package com.example.nanobot.core.tools

sealed class ToolResult {
    data class Text(val content: String) : ToolResult()

    data class Multimodal(
        val text: String,
        val images: List<ImagePart>
    ) : ToolResult()

    companion object {
        private val screenshotPattern = Regex("""\[screenshot:(data:image/[^\]]+)]""")

        fun fromLegacyOutput(content: String): ToolResult {
            val images = screenshotPattern.findAll(content).map { match ->
                ImagePart(
                    dataUrl = match.groupValues[1],
                    altText = "Current screen screenshot"
                )
            }.toList()
            if (images.isEmpty()) {
                return Text(content)
            }

            val text = screenshotPattern.replace(content, "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()

            return Multimodal(
                text = text.ifBlank { "Screenshot captured." },
                images = images
            )
        }
    }
}

data class ImagePart(
    val dataUrl: String,
    val altText: String = ""
)
