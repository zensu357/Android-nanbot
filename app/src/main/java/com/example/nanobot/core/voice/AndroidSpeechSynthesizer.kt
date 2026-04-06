package com.example.nanobot.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class AndroidSpeechSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechSynthesizer {
    private val lock = Any()
    private var textToSpeech: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<TextToSpeech>? = null
    private var activeContinuation: CancellableContinuation<Unit>? = null
    private var activeUtteranceId: String? = null

    override suspend fun speak(text: String, languageTag: String, speed: Float) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main.immediate) {
            stop()
            val tts = awaitTextToSpeech()
            configure(tts, languageTag, speed)
            suspendCancellableCoroutine { continuation ->
                val utteranceId = UUID.randomUUID().toString()
                synchronized(lock) {
                    activeContinuation = continuation
                    activeUtteranceId = utteranceId
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        finishContinuation(continuation, utteranceId) { continuation.resume(Unit) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishContinuation(continuation, utteranceId) {
                            continuation.resumeWithException(IllegalStateException("Text-to-speech playback failed."))
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finishContinuation(continuation, utteranceId) {
                            continuation.resumeWithException(
                                IllegalStateException("Text-to-speech playback failed with error code $errorCode.")
                            )
                        }
                    }
                })
                val speakResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult == TextToSpeech.ERROR) {
                    finishContinuation(continuation, utteranceId) {
                        continuation.resumeWithException(IllegalStateException("Text-to-speech failed to start."))
                    }
                }
                continuation.invokeOnCancellation { stop() }
            }
        }
    }

    override fun stop() {
        val tts: TextToSpeech?
        val continuation: CancellableContinuation<Unit>?
        synchronized(lock) {
            tts = textToSpeech
            continuation = activeContinuation
            activeUtteranceId = null
            activeContinuation = null
        }
        runCatching { tts?.stop() }
        continuation?.cancel(CancellationException("Speech playback stopped."))
    }

    override fun release() {
        val tts: TextToSpeech?
        synchronized(lock) {
            tts = textToSpeech
            textToSpeech = null
            initDeferred = null
            activeUtteranceId = null
            activeContinuation?.cancel(CancellationException("Speech synthesizer released."))
            activeContinuation = null
        }
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
    }

    private suspend fun awaitTextToSpeech(): TextToSpeech = withContext(Dispatchers.Main.immediate) {
        val existingTts = synchronized(lock) { textToSpeech }
        if (existingTts != null) {
            return@withContext existingTts
        }

        val existingDeferred = synchronized(lock) { initDeferred }
        if (existingDeferred != null) {
            return@withContext existingDeferred.await()
        }

        val deferred = CompletableDeferred<TextToSpeech>()
        val shouldInitialize = synchronized(lock) {
            when {
                textToSpeech != null -> false
                initDeferred != null -> false
                else -> {
                    initDeferred = deferred
                    true
                }
            }
        }
        if (!shouldInitialize) {
            return@withContext synchronized(lock) { textToSpeech } ?: synchronized(lock) { initDeferred }!!.await()
        }
        var createdTts: TextToSpeech? = null
        var pendingStatus: Int? = null
        createdTts = TextToSpeech(context) { status ->
            val readyTts = synchronized(lock) {
                when {
                    createdTts != null -> createdTts
                    textToSpeech != null -> textToSpeech
                    else -> {
                        pendingStatus = status
                        null
                    }
                }
            }
            readyTts?.let { completeInitialization(it, status, deferred) }
        }
        synchronized(lock) {
            if (initDeferred === deferred && textToSpeech == null) {
                textToSpeech = createdTts
            }
        }
        pendingStatus?.let { status ->
            createdTts?.let { completeInitialization(it, status, deferred) }
        }
        deferred.await()
    }

    private fun configure(tts: TextToSpeech, languageTag: String, speed: Float) {
        val requestedLocale = Locale.forLanguageTag(languageTag.ifBlank { "zh-CN" })
        val availability = tts.setLanguage(requestedLocale)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackLocale = Locale.getDefault()
            Log.w(TAG, "TTS language '$languageTag' unavailable, falling back to '${fallbackLocale.toLanguageTag()}'.")
            val fallbackAvailability = tts.setLanguage(fallbackLocale)
            if (fallbackAvailability == TextToSpeech.LANG_MISSING_DATA || fallbackAvailability == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Fallback TTS language '${fallbackLocale.toLanguageTag()}' is also unavailable.")
            }
        }
        tts.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
    }

    private fun finishContinuation(
        continuation: CancellableContinuation<Unit>,
        utteranceId: String?,
        resumeBlock: () -> Unit
    ) {
        var shouldResume = false
        synchronized(lock) {
            if (activeContinuation === continuation && activeUtteranceId == utteranceId) {
                activeContinuation = null
                activeUtteranceId = null
                shouldResume = true
            }
        }
        if (shouldResume) {
            resumeBlock()
        }
    }

    private fun completeInitialization(
        tts: TextToSpeech,
        status: Int,
        deferred: CompletableDeferred<TextToSpeech>
    ) {
        val shouldComplete = synchronized(lock) {
            if (initDeferred !== deferred && textToSpeech !== tts) {
                false
            } else {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech = tts
                } else if (textToSpeech === tts) {
                    textToSpeech = null
                }
                initDeferred = null
                true
            }
        }
        if (!shouldComplete) {
            if (status != TextToSpeech.SUCCESS) {
                runCatching { tts.shutdown() }
            }
            return
        }

        if (status == TextToSpeech.SUCCESS) {
            deferred.complete(tts)
        } else {
            deferred.completeExceptionally(IllegalStateException("Text-to-speech initialization failed."))
            runCatching { tts.shutdown() }
        }
    }

    private companion object {
        const val TAG = "AndroidSpeechSynth"
    }
}
