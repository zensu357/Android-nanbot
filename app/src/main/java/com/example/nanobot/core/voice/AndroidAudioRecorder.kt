package com.example.nanobot.core.voice

import android.os.Build
import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {
    private val lock = Any()
    private var mediaRecorder: MediaRecorder? = null
    private var continuation: CancellableContinuation<File>? = null
    private var outputFile: File? = null

    override suspend fun recordUntilStopped(): File = withContext(Dispatchers.IO) {
        try {
            withTimeout(MAX_RECORDING_DURATION_MS) {
                suspendCancellableCoroutine { cont ->
                    val file = File.createTempFile("voice-input-", ".m4a", context.cacheDir)
                    val recorder = createRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(16_000)
                        setAudioEncodingBitRate(64_000)
                        setOutputFile(file.absolutePath)
                    }
                    try {
                        recorder.prepare()
                        recorder.start()
                    } catch (throwable: Throwable) {
                        runCatching { recorder.reset() }
                        runCatching { recorder.release() }
                        file.delete()
                        cont.resumeWithException(IllegalStateException("Failed to start microphone recording.", throwable))
                        return@suspendCancellableCoroutine
                    }

                    synchronized(lock) {
                        cancelLocked(deleteFile = true)
                        mediaRecorder = recorder
                        continuation = cont
                        outputFile = file
                    }

                    cont.invokeOnCancellation {
                        synchronized(lock) {
                            cancelLocked(deleteFile = true)
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            cancel()
            throw IllegalStateException("Voice input timed out after ${MAX_RECORDING_DURATION_MS / 1000} seconds.")
        }
    }

    override fun stop() {
        val recorder: MediaRecorder?
        val cont: CancellableContinuation<File>?
        val file: File?
        synchronized(lock) {
            recorder = mediaRecorder
            cont = continuation
            file = outputFile
            mediaRecorder = null
            continuation = null
            outputFile = null
        }
        if (recorder == null || cont == null || file == null) return
        try {
            recorder.stop()
            recorder.reset()
            recorder.release()
            cont.resume(file)
        } catch (throwable: Throwable) {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            file.delete()
            cont.resumeWithException(IllegalStateException("Microphone recording was too short or failed to stop cleanly.", throwable))
        }
    }

    override fun cancel() {
        synchronized(lock) {
            cancelLocked(deleteFile = true)
        }
    }

    private fun cancelLocked(deleteFile: Boolean) {
        val recorder = mediaRecorder
        val cont = continuation
        val file = outputFile
        mediaRecorder = null
        continuation = null
        outputFile = null
        if (recorder != null) {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        if (deleteFile) {
            runCatching { file?.delete() }
        }
        cont?.cancel(CancellationException("Audio recording cancelled."))
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private companion object {
        const val MAX_RECORDING_DURATION_MS = 60_000L
    }
}
