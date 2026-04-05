package com.example.nanobot.core.phonecontrol

enum class PhoneGlobalAction(val wireName: String) {
    BACK("back"),
    HOME("home"),
    RECENTS("recents"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick_settings"),
    POWER_DIALOG("power_dialog"),
    LOCK_SCREEN("lock_screen"),
    TAKE_SCREENSHOT("take_screenshot");

    companion object {
        fun from(value: String): PhoneGlobalAction? = entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}

data class PhoneControlActionResult(
    val success: Boolean,
    val message: String
)

enum class SelectorMatchMode(val wireName: String) {
    EXACT("exact"),
    CONTAINS("contains"),
    REGEX("regex");

    companion object {
        fun from(value: String): SelectorMatchMode? = entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}

data class PhoneUiNodeSelector(
    val nodeId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val viewIdResourceName: String? = null,
    val matchMode: SelectorMatchMode = SelectorMatchMode.EXACT
)

enum class NodeAction(val wireName: String) {
    CLICK("click"),
    LONG_CLICK("long_click"),
    FOCUS("focus"),
    CLEAR_FOCUS("clear_focus"),
    SELECT("select"),
    CLEAR_SELECTION("clear_selection"),
    COPY("copy"),
    PASTE("paste"),
    CUT("cut");

    companion object {
        fun from(value: String): NodeAction? = entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}

data class ScreenshotResult(
    val success: Boolean,
    val message: String,
    val base64Jpeg: String? = null
)

enum class ScrollDirection(val wireName: String) {
    FORWARD("forward"),
    BACKWARD("backward");

    companion object {
        fun from(value: String): ScrollDirection? = entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}
