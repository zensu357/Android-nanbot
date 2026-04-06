package com.example.nanobot.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneControlUnlockProfileRegistry @Inject constructor() {
    fun resolveTools(profiles: Collection<String>): Set<String> {
        return profiles
            .flatMap { profile -> PROFILE_TO_TOOLS[profile].orEmpty() }
            .toSet()
    }

    fun isKnownProfile(profile: String): Boolean = profile in PROFILE_TO_TOOLS

    private companion object {
        val PROFILE_TO_TOOLS: Map<String, Set<String>> = mapOf(
            "phone_control_basic_v1" to setOf(
                "read_current_ui",
                "tap_ui_node",
                "input_text",
                "scroll_ui",
                "press_global_action",
                "launch_app",
                "wait_for_ui",
                "perform_ui_action",
                "take_screenshot",
                "analyze_screenshot",
                "visual_verify"
            )
        )
    }
}
