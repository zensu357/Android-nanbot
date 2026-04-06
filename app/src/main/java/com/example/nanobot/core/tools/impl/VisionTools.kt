package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.phonecontrol.PhoneControlService
import com.example.nanobot.core.tools.ImagePart
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolResult
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val DEFAULT_VISION_SCREENSHOT_QUALITY = 60
private const val DEFAULT_VISION_SCREENSHOT_MAX_WIDTH = 720

private fun PhoneControlService.hasPhoneControlAccess(): Boolean {
    return readCurrentUi(includeNonInteractive = false, maxNodes = 1).serviceConnected
}

class AnalyzeScreenshotTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "analyze_screenshot"
    override val description: String = "Captures a screenshot and asks the vision model to analyze it for a specific question or goal."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val availabilityHint: String = "Requires a vision-capable provider and unlocked phone-control tools"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("question") {
                put("type", "string")
                put("description", "What to analyze in the screenshot.")
            }
            putJsonObject("maxWidth") {
                put("type", "integer")
                put("description", "Maximum screenshot width in pixels (default 720).")
                put("minimum", 240)
                put("maximum", 1440)
            }
            putJsonObject("quality") {
                put("type", "integer")
                put("description", "JPEG quality (10-95, default 60).")
                put("minimum", 10)
                put("maximum", 95)
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("question")) })
    }

    override fun isAvailable(config: AgentConfig, runContext: AgentRunContext): Boolean {
        return runContext.supportsVision && phoneControlService.hasPhoneControlAccess()
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val question = arguments["question"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (question.isBlank()) {
            return "The 'question' field is required for analyze_screenshot."
        }
        return if (runContext.supportsVision) {
            executeStructured(arguments, config, runContext).let { result ->
                when (result) {
                    is ToolResult.Text -> result.content
                    is ToolResult.Multimodal -> result.text
                }
            }
        } else {
            "analyze_screenshot requires a vision-capable provider."
        }
    }

    override suspend fun executeStructured(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): ToolResult {
        if (!runContext.supportsVision) {
            return ToolResult.Text("analyze_screenshot requires a vision-capable provider.")
        }
        if (!phoneControlService.hasPhoneControlAccess()) {
            return ToolResult.Text("analyze_screenshot requires the phone-control accessibility service to be enabled.")
        }
        val question = arguments["question"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (question.isBlank()) {
            return ToolResult.Text("The 'question' field is required for analyze_screenshot.")
        }
        return capturePromptedScreenshot(
            prompt = buildString {
                append("Answer the screenshot question first. Keep the answer grounded in visible evidence. ")
                append("If the screenshot is insufficient, say exactly what is unclear.\n\n")
                append("Question: $question")
            },
            altText = question,
            arguments = arguments
        )
    }

    private suspend fun capturePromptedScreenshot(
        prompt: String,
        altText: String,
        arguments: JsonObject
    ): ToolResult {
        val quality = arguments["quality"]?.jsonPrimitive?.intOrNull ?: DEFAULT_VISION_SCREENSHOT_QUALITY
        val maxWidth = arguments["maxWidth"]?.jsonPrimitive?.intOrNull ?: DEFAULT_VISION_SCREENSHOT_MAX_WIDTH
        val result = phoneControlService.takeScreenshot(quality, maxWidth)
        return if (result.success && result.base64Jpeg != null) {
            ToolResult.Multimodal(
                text = prompt,
                images = listOf(
                    ImagePart(
                        dataUrl = "data:image/jpeg;base64,${result.base64Jpeg}",
                        altText = altText
                    )
                )
            )
        } else {
            ToolResult.Text(result.message)
        }
    }
}

class VisualVerifyTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "visual_verify"
    override val description: String = "Captures a screenshot so the vision model can verify whether a previous action achieved the expected result."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val availabilityHint: String = "Requires a vision-capable provider and unlocked phone-control tools"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expected") {
                put("type", "string")
                put("description", "Description of the expected screen state.")
            }
            putJsonObject("maxWidth") {
                put("type", "integer")
                put("description", "Maximum screenshot width in pixels (default 720).")
                put("minimum", 240)
                put("maximum", 1440)
            }
            putJsonObject("quality") {
                put("type", "integer")
                put("description", "JPEG quality (10-95, default 60).")
                put("minimum", 10)
                put("maximum", 95)
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("expected")) })
    }

    override fun isAvailable(config: AgentConfig, runContext: AgentRunContext): Boolean {
        return runContext.supportsVision && phoneControlService.hasPhoneControlAccess()
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val expected = arguments["expected"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (expected.isBlank()) {
            return "The 'expected' field is required for visual_verify."
        }
        return if (runContext.supportsVision) {
            executeStructured(arguments, config, runContext).let { result ->
                when (result) {
                    is ToolResult.Text -> result.content
                    is ToolResult.Multimodal -> result.text
                }
            }
        } else {
            "visual_verify requires a vision-capable provider."
        }
    }

    override suspend fun executeStructured(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): ToolResult {
        if (!runContext.supportsVision) {
            return ToolResult.Text("visual_verify requires a vision-capable provider.")
        }
        if (!phoneControlService.hasPhoneControlAccess()) {
            return ToolResult.Text("visual_verify requires the phone-control accessibility service to be enabled.")
        }
        val expected = arguments["expected"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (expected.isBlank()) {
            return ToolResult.Text("The 'expected' field is required for visual_verify.")
        }
        val quality = arguments["quality"]?.jsonPrimitive?.intOrNull ?: DEFAULT_VISION_SCREENSHOT_QUALITY
        val maxWidth = arguments["maxWidth"]?.jsonPrimitive?.intOrNull ?: DEFAULT_VISION_SCREENSHOT_MAX_WIDTH
        val result = phoneControlService.takeScreenshot(quality, maxWidth)
        return if (result.success && result.base64Jpeg != null) {
            ToolResult.Multimodal(
                text = buildString {
                    append("Decide whether the expected outcome is visible. Start with one of: VERIFIED, NOT VERIFIED, or UNCERTAIN. ")
                    append("Then give a brief evidence-based explanation.\n\n")
                    append("Expected outcome: $expected")
                },
                images = listOf(
                    ImagePart(
                        dataUrl = "data:image/jpeg;base64,${result.base64Jpeg}",
                        altText = expected
                    )
                )
            )
        } else {
            ToolResult.Text(result.message)
        }
    }
}
