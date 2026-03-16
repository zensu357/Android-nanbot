package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.phonecontrol.PhoneGlobalAction
import com.example.nanobot.core.phonecontrol.PhoneUiNodeSelector
import com.example.nanobot.core.phonecontrol.PhoneControlService
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshotFormatter
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolExposure
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

private const val PHONE_CONTROL_STUB_MESSAGE =
    "This hidden phone-control tool has been unlocked, but the Android automation backend is not wired in yet. " +
        "Phase 3 only registers the host-side tool skeleton."

abstract class BasePhoneControlTool : AgentTool {
    override val exposure: ToolExposure = ToolExposure.HIDDEN_UNLOCKABLE

    protected fun notImplemented(detail: String): String {
        return "$detail $PHONE_CONTROL_STUB_MESSAGE"
    }
}

class ReadCurrentUiTool @Inject constructor(
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "read_current_ui"
    override val description: String = "Reads a structured summary of the current foreground Android UI"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("includeNonInteractive") {
                put("type", "boolean")
                put("description", "Whether to include non-interactive nodes in the summary")
            }
            putJsonObject("maxNodes") {
                put("type", "integer")
                put("description", "Maximum number of nodes to summarize")
                put("minimum", 1)
                put("maximum", 200)
            }
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val includeNonInteractive = arguments["includeNonInteractive"]?.jsonPrimitive?.contentOrNull == "true"
        val maxNodes = arguments["maxNodes"]?.jsonPrimitive?.intOrNull ?: 50
        val snapshot = phoneControlService.readCurrentUi(
            includeNonInteractive = includeNonInteractive,
            maxNodes = maxNodes
        )
        return formatter.format(snapshot)
    }
}

class TapUiNodeTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "tap_ui_node"
    override val description: String = "Taps a foreground UI node selected by semantic fields such as nodeId, text, or contentDescription"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("nodeId") {
                put("type", "string")
                put("description", "Preferred stable node identifier from read_current_ui")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Fallback visible text selector")
            }
            putJsonObject("contentDescription") {
                put("type", "string")
                put("description", "Fallback contentDescription selector")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull
        val text = arguments["text"]?.jsonPrimitive?.contentOrNull
        val contentDescription = arguments["contentDescription"]?.jsonPrimitive?.contentOrNull
        if (nodeId.isNullOrBlank() && text.isNullOrBlank() && contentDescription.isNullOrBlank()) {
            return "Provide at least one selector for tap_ui_node: 'nodeId', 'text', or 'contentDescription'."
        }
        val result = phoneControlService.tapUiNode(
            PhoneUiNodeSelector(
                nodeId = nodeId,
                text = text,
                contentDescription = contentDescription
            )
        )
        return result.message
    }
}

class InputTextTool @Inject constructor() : BasePhoneControlTool() {
    override val name: String = "input_text"
    override val description: String = "Inputs text into an editable foreground UI field"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("nodeId") {
                put("type", "string")
                put("description", "Editable node identifier from read_current_ui")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to enter")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("nodeId"))
            add(JsonPrimitive("text"))
        })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val text = arguments["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return notImplemented(
            "Requested text input for nodeId=$nodeId with ${text.length} characters."
        )
    }
}

class ScrollUiTool @Inject constructor() : BasePhoneControlTool() {
    override val name: String = "scroll_ui"
    override val description: String = "Scrolls a scrollable foreground container forward or backward"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("nodeId") {
                put("type", "string")
                put("description", "Scrollable node identifier")
            }
            putJsonObject("direction") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("forward"))
                    add(JsonPrimitive("backward"))
                })
                put("description", "Scroll direction")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("direction")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull
        val direction = arguments["direction"]?.jsonPrimitive?.contentOrNull ?: "forward"
        return notImplemented(
            "Requested scroll direction=$direction on nodeId=${nodeId ?: "(none)"}."
        )
    }
}

class PressGlobalActionTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "press_global_action"
    override val description: String = "Invokes a global Android action such as back, home, or recents"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("back"))
                    add(JsonPrimitive("home"))
                    add(JsonPrimitive("recents"))
                })
                put("description", "Global action to invoke")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull ?: "back"
        val parsed = PhoneGlobalAction.from(action)
            ?: return "Unsupported global action '$action'. Supported values: back, home, recents."
        return phoneControlService.performGlobalAction(parsed).message
    }
}

class LaunchAppTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "launch_app"
    override val description: String = "Launches an Android app by package name"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("packageName") {
                put("type", "string")
                put("description", "Android package name to launch")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("packageName")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val packageName = arguments["packageName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (packageName.isBlank()) {
            return "The 'packageName' field is required for launch_app."
        }
        return if (!phoneControlService.isPackageInstalled(packageName)) {
            "Package '$packageName' is not installed on this device."
        } else if (phoneControlService.launchApp(packageName)) {
            "Launched package '$packageName'."
        } else {
            "Failed to launch package '$packageName'."
        }
    }
}

class WaitForUiTool @Inject constructor() : BasePhoneControlTool() {
    override val name: String = "wait_for_ui"
    override val description: String = "Waits for a target UI condition such as text or contentDescription to appear"
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Visible text to wait for")
            }
            putJsonObject("contentDescription") {
                put("type", "string")
                put("description", "Content description to wait for")
            }
            putJsonObject("timeoutMs") {
                put("type", "integer")
                put("description", "Maximum wait time in milliseconds")
                put("minimum", 100)
                put("maximum", 30000)
            }
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val text = arguments["text"]?.jsonPrimitive?.contentOrNull
        val contentDescription = arguments["contentDescription"]?.jsonPrimitive?.contentOrNull
        val timeoutMs = arguments["timeoutMs"]?.jsonPrimitive?.intOrNull ?: 3000
        return notImplemented(
            "Requested wait for text=${text ?: "(none)"}, contentDescription=${contentDescription ?: "(none)"}, timeoutMs=$timeoutMs."
        )
    }
}
