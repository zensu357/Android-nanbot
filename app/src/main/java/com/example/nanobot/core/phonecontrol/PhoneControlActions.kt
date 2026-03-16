package com.example.nanobot.core.phonecontrol

enum class PhoneGlobalAction(val wireName: String) {
    BACK("back"),
    HOME("home"),
    RECENTS("recents");

    companion object {
        fun from(value: String): PhoneGlobalAction? = entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
    }
}

data class PhoneControlActionResult(
    val success: Boolean,
    val message: String
)

data class PhoneUiNodeSelector(
    val nodeId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null
)
