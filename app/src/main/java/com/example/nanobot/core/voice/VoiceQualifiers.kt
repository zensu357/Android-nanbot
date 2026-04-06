package com.example.nanobot.core.voice

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AndroidVoiceRecognizer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WhisperVoiceRecognizer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AndroidVoiceSynthesizer
