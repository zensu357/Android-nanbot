package com.example.nanobot.core.tools.impl

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.phonecontrol.NodeAction
import com.example.nanobot.core.phonecontrol.PhoneGlobalAction
import com.example.nanobot.core.phonecontrol.PhoneUiNodeSelector
import com.example.nanobot.core.phonecontrol.PhoneControlService
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshotFormatter
import com.example.nanobot.core.phonecontrol.ScrollDirection
import com.example.nanobot.core.phonecontrol.SelectorMatchMode
import com.example.nanobot.core.tools.AgentTool
import com.example.nanobot.core.tools.ToolAccessCategory
import com.example.nanobot.core.tools.ToolExposure
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val AUTO_SNAPSHOT_DELAY_MS = 350L
private const val AUTO_SNAPSHOT_MAX_NODES = 30

abstract class BasePhoneControlTool : AgentTool {
    override val exposure: ToolExposure = ToolExposure.HIDDEN_UNLOCKABLE

    protected suspend fun withAutoSnapshot(
        phoneControlService: PhoneControlService,
        formatter: PhoneUiSnapshotFormatter,
        actionResult: String
    ): String {
        kotlinx.coroutines.delay(AUTO_SNAPSHOT_DELAY_MS)
        val snapshot = phoneControlService.readCurrentUi(
            includeNonInteractive = false,
            maxNodes = AUTO_SNAPSHOT_MAX_NODES
        )
        val snapshotText = formatter.format(snapshot)
        return "$actionResult\n\n--- UI After Action ---\n$snapshotText"
    }

    protected fun hasAnySelector(selector: PhoneUiNodeSelector): Boolean {
        return !selector.nodeId.isNullOrBlank() || !selector.text.isNullOrBlank() ||
            !selector.contentDescription.isNullOrBlank() || !selector.className.isNullOrBlank() ||
            !selector.viewIdResourceName.isNullOrBlank()
    }

    protected fun parseMatchMode(arguments: JsonObject): SelectorMatchMode {
        val raw = arguments["matchMode"]?.jsonPrimitive?.contentOrNull ?: "exact"
        return SelectorMatchMode.from(raw) ?: SelectorMatchMode.EXACT
    }

    protected fun parseSelector(arguments: JsonObject): PhoneUiNodeSelector {
        return PhoneUiNodeSelector(
            nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull,
            text = arguments["text"]?.jsonPrimitive?.contentOrNull,
            contentDescription = arguments["contentDescription"]?.jsonPrimitive?.contentOrNull,
            className = arguments["className"]?.jsonPrimitive?.contentOrNull,
            viewIdResourceName = arguments["viewIdResourceName"]?.jsonPrimitive?.contentOrNull,
            matchMode = parseMatchMode(arguments)
        )
    }

    protected fun JsonObjectBuilder.putSelectorProperties() {
        putJsonObject("nodeId") {
            put("type", "string")
            put("description", "Preferred stable node identifier from read_current_ui")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Visible text selector")
        }
        putJsonObject("contentDescription") {
            put("type", "string")
            put("description", "ContentDescription selector")
        }
        putJsonObject("className") {
            put("type", "string")
            put("description", "Android view class name selector (e.g. 'android.widget.Button')")
        }
        putJsonObject("viewIdResourceName") {
            put("type", "string")
            put("description", "View resource ID selector (e.g. 'com.example:id/btn_ok')")
        }
        putJsonObject("matchMode") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("exact"))
                add(JsonPrimitive("contains"))
                add(JsonPrimitive("regex"))
            })
            put("description", "How text/contentDescription/className/viewId selectors match: exact (default), contains (substring), or regex")
        }
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
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "tap_ui_node"
    override val description: String = "Taps a foreground UI node selected by nodeId, text, contentDescription, className, or viewIdResourceName. Supports exact, contains, and regex match modes. Returns an updated UI snapshot after the action."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putSelectorProperties()
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val selector = parseSelector(arguments)
        if (!hasAnySelector(selector)) {
            return "Provide at least one selector for tap_ui_node: 'nodeId', 'text', 'contentDescription', 'className', or 'viewIdResourceName'."
        }
        val result = phoneControlService.tapUiNode(selector)
        return if (result.success) {
            withAutoSnapshot(phoneControlService, formatter, result.message)
        } else {
            result.message
        }
    }
}

class InputTextTool @Inject constructor(
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "input_text"
    override val description: String = "Inputs text into an editable foreground UI field. Supports exact, contains, and regex match modes for locating the target field. Returns an updated UI snapshot after the action."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putSelectorProperties()
            putJsonObject("inputText") {
                put("type", "string")
                put("description", "Text to enter into the editable field")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("inputText"))
        })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val inputText = arguments["inputText"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val selector = parseSelector(arguments)
        if (!hasAnySelector(selector)) {
            return "Provide at least one selector for input_text: 'nodeId', 'text', 'contentDescription', 'className', or 'viewIdResourceName'."
        }
        val result = phoneControlService.inputText(selector, inputText)
        return if (result.success) {
            withAutoSnapshot(phoneControlService, formatter, result.message)
        } else {
            result.message
        }
    }
}

class ScrollUiTool @Inject constructor(
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "scroll_ui"
    override val description: String = "Scrolls a scrollable foreground container forward or backward. If no selector is given, scrolls the first scrollable container found. Supports exact, contains, and regex match modes. Returns an updated UI snapshot after the action."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putSelectorProperties()
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
        val directionStr = arguments["direction"]?.jsonPrimitive?.contentOrNull ?: "forward"
        val direction = ScrollDirection.from(directionStr)
            ?: return "Unsupported scroll direction '$directionStr'. Supported values: forward, backward."
        val selector = parseSelector(arguments)
        val result = phoneControlService.scrollNode(if (hasAnySelector(selector)) selector else null, direction)
        return if (result.success) {
            withAutoSnapshot(phoneControlService, formatter, result.message)
        } else {
            result.message
        }
    }
}

class PressGlobalActionTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "press_global_action"
    override val description: String = "Invokes a global Android action such as back, home, recents, notifications, quick_settings, power_dialog, lock_screen, or take_screenshot"
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
                    add(JsonPrimitive("notifications"))
                    add(JsonPrimitive("quick_settings"))
                    add(JsonPrimitive("power_dialog"))
                    add(JsonPrimitive("lock_screen"))
                    add(JsonPrimitive("take_screenshot"))
                })
                put("description", "Global action to invoke")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull ?: "back"
        val parsed = PhoneGlobalAction.from(action)
            ?: return "Unsupported global action '$action'. Supported values: ${PhoneGlobalAction.entries.joinToString { it.wireName }}."
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

class WaitForUiTool @Inject constructor(
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "wait_for_ui"
    override val description: String = "Waits for a target UI condition such as text or contentDescription to appear in the foreground UI tree. Provide at least one of 'text' or 'contentDescription'. Returns an updated UI snapshot when condition is met."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Visible text to wait for (substring match, case-insensitive)")
            }
            putJsonObject("contentDescription") {
                put("type", "string")
                put("description", "Content description to wait for (substring match, case-insensitive)")
            }
            putJsonObject("timeoutMs") {
                put("type", "integer")
                put("description", "Maximum wait time in milliseconds (default 5000, max 30000)")
                put("minimum", 100)
                put("maximum", 30000)
            }
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val text = arguments["text"]?.jsonPrimitive?.contentOrNull
        val contentDescription = arguments["contentDescription"]?.jsonPrimitive?.contentOrNull
        if (text.isNullOrBlank() && contentDescription.isNullOrBlank()) {
            return "Provide at least one condition for wait_for_ui: 'text' or 'contentDescription'."
        }
        val timeoutMs = (arguments["timeoutMs"]?.jsonPrimitive?.intOrNull ?: 5000).coerceIn(100, 30000).toLong()
        val result = phoneControlService.waitForCondition(text, contentDescription, timeoutMs)
        return if (result.success) {
            withAutoSnapshot(phoneControlService, formatter, result.message)
        } else {
            result.message
        }
    }
}

class PerformUiActionTool @Inject constructor(
    private val phoneControlService: PhoneControlService,
    private val formatter: PhoneUiSnapshotFormatter
) : BasePhoneControlTool() {
    override val name: String = "perform_ui_action"
    override val description: String = "Performs an advanced action on a UI node: long_click, focus, clear_focus, select, clear_selection, copy, paste, or cut. Returns an updated UI snapshot after the action."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putSelectorProperties()
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    NodeAction.entries.forEach { add(JsonPrimitive(it.wireName)) }
                })
                put("description", "Action to perform on the matched node")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val actionStr = arguments["action"]?.jsonPrimitive?.contentOrNull
            ?: return "The 'action' field is required for perform_ui_action."
        val action = NodeAction.from(actionStr)
            ?: return "Unsupported action '$actionStr'. Supported: ${NodeAction.entries.joinToString { it.wireName }}."
        val selector = parseSelector(arguments)
        if (!hasAnySelector(selector)) {
            return "Provide at least one selector for perform_ui_action: 'nodeId', 'text', 'contentDescription', 'className', or 'viewIdResourceName'."
        }
        val result = phoneControlService.performNodeAction(selector, action)
        return if (result.success) {
            withAutoSnapshot(phoneControlService, formatter, result.message)
        } else {
            result.message
        }
    }
}

class TakeScreenshotTool @Inject constructor(
    private val phoneControlService: PhoneControlService
) : BasePhoneControlTool() {
    override val name: String = "take_screenshot"
    override val description: String = "Captures a screenshot of the current screen and returns it as a base64-encoded JPEG image. Requires Android 11+."
    override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("quality") {
                put("type", "integer")
                put("description", "JPEG quality (10-95, default 60)")
                put("minimum", 10)
                put("maximum", 95)
            }
            putJsonObject("maxWidth") {
                put("type", "integer")
                put("description", "Maximum image width in pixels (default 720). Larger screenshots are downscaled to save tokens.")
                put("minimum", 240)
                put("maximum", 1440)
            }
        }
    }

    override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
        val quality = arguments["quality"]?.jsonPrimitive?.intOrNull ?: 60
        val maxWidth = arguments["maxWidth"]?.jsonPrimitive?.intOrNull ?: 720
        val result = phoneControlService.takeScreenshot(quality, maxWidth)
        return if (result.success && result.base64Jpeg != null) {
            "${result.message}\n[screenshot:data:image/jpeg;base64,${result.base64Jpeg}]"
        } else {
            result.message
        }
    }
}
