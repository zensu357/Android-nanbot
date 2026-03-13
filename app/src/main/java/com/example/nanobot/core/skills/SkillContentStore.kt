package com.example.nanobot.core.skills

import android.content.Context
import android.net.Uri
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SkillContentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun readText(documentUri: String, maxChars: Int = Int.MAX_VALUE): SkillTextContent {
        val uri = Uri.parse(documentUri)
        val text = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).readText()
        } else {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }
        val truncated = text.length > maxChars
        val content = if (truncated) text.take(maxChars.coerceAtLeast(0)) else text
        return SkillTextContent(
            content = content,
            totalBytes = text.toByteArray().size,
            truncated = truncated
        )
    }
}

data class SkillTextContent(
    val content: String,
    val totalBytes: Int,
    val truncated: Boolean
)
