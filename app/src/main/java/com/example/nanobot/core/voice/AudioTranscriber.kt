package com.example.nanobot.core.voice

import com.example.nanobot.core.model.AgentConfig
import java.io.File

interface AudioTranscriber {
    suspend fun transcribe(audioFile: File, languageTag: String, config: AgentConfig): String
}
