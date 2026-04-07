package com.example.nanobot.feature.settings.sections

import androidx.compose.runtime.Composable
import com.example.nanobot.feature.settings.SettingsUiState
import com.example.nanobot.ui.components.SettingsGroupCard

@Composable
fun PermissionsSection(
    state: SettingsUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    SettingsGroupCard(title = "Permissions & Access") {
        AccessStatusCard(
            title = "Notification Permission",
            status = when {
                !state.systemAccess.notificationPermissionRequired -> "Not required on this Android version"
                state.systemAccess.notificationPermissionGranted -> "Granted"
                else -> "Not granted"
            },
            supportingText = if (state.systemAccess.notificationPermissionRequired) {
                "Required on Android 13+ before the app can post notifications."
            } else {
                "This Android version does not require the POST_NOTIFICATIONS runtime permission."
            },
            actionLabel = "Request notification permission",
            onActionClick = onRequestNotificationPermission,
            actionEnabled = state.systemAccess.notificationPermissionRequired && !state.systemAccess.notificationPermissionGranted
        )
        AccessStatusCard(
            title = "App Notifications",
            status = if (state.systemAccess.notificationsEnabled) "Enabled" else "Disabled in system settings",
            supportingText = "Controls whether reminder and heartbeat notifications can appear for this app.",
            actionLabel = "Open notification settings",
            onActionClick = onOpenNotificationSettings
        )
        AccessStatusCard(
            title = "Phone Control Accessibility",
            status = if (state.systemAccess.accessibilityEnabled) "Enabled" else "Not enabled",
            supportingText = "Required for phone control tools to inspect and interact with the device UI.",
            actionLabel = "Open accessibility settings",
            onActionClick = onOpenAccessibilitySettings
        )
    }
}
