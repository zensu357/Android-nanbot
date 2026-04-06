package com.example.nanobot.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class AndroidSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechRecognizer {
    private val lock = Any()
    private var activeRecognizer: android.speech.SpeechRecognizer? = null
    private var activeContinuation: CancellableContinuation<String>? = null

    override suspend fun recognize(languageTag: String): String = withContext(Dispatchers.Main.immediate) {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition is unavailable on this device.")
        }
        suspendCancellableCoroutine { continuation ->
            val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            synchronized(lock) {
                cancelLocked()
                activeRecognizer = recognizer
                activeContinuation = continuation
            }
            val finished = AtomicBoolean(false)
            fun finish(block: () -> Unit) {
                if (!finished.compareAndSet(false, true)) return
                synchronized(lock) {
                    if (activeRecognizer === recognizer) activeRecognizer = null
                    if (activeContinuation === continuation) activeContinuation = null
                }
                block()
                runCatching { recognizer.destroy() }
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    finish { continuation.resume(text) }
                }

                override fun onError(error: Int) {
                    finish {
                        continuation.resumeWithException(IllegalStateException(errorMessage(error)))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag.ifBlank { "zh-CN" })
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    synchronized(lock) {
                        if (activeRecognizer === recognizer) activeRecognizer = null
                        if (activeContinuation === continuation) activeContinuation = null
                    }
                    runCatching { recognizer.cancel() }
                    runCatching { recognizer.destroy() }
                }
            }

            recognizer.startListening(intent)
        }
    }

    override fun cancel() {
        synchronized(lock) {
            cancelLocked()
        }
    }

    override fun stop() {
        val recognizer = synchronized(lock) { activeRecognizer }
        runCatching { recognizer?.stopListening() }
    }

    private fun cancelLocked() {
        val recognizer = activeRecognizer
        val continuation = activeContinuation
        activeRecognizer = null
        activeContinuation = null
        continuation?.cancel(CancellationException("Voice input cancelled."))
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
    }

    private fun errorMessage(code: Int): String {
        return when (code) {
            android.speech.SpeechRecognizer.ERROR_AUDIO -> "Speech recognition failed because audio capture was interrupted."
            android.speech.SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was cancelled."
            android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing for speech recognition."
            android.speech.SpeechRecognizer.ERROR_NETWORK,
            android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition failed because the network was unavailable."
            android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand the recording. Please try again."
            android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy right now. Please try again in a moment."
            android.speech.SpeechRecognizer.ERROR_SERVER -> "The speech recognition service returned an error."
            android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected before the timeout."
            else -> "Speech recognition failed with error code $code."
        }
    }
}
