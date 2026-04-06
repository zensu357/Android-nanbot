package com.example.nanobot.core.voice

import java.io.File

interface AudioRecorder {
    suspend fun recordUntilStopped(): File

    fun stop()

    fun cancel()
}
