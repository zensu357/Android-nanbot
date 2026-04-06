package com.example.nanobot.core.model

enum class VoiceEngineType(val wireValue: String) {
    ANDROID("ANDROID"),
    WHISPER("WHISPER");

    companion object {
        fun from(value: String?): VoiceEngineType {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ANDROID
        }
    }
}
