package com.example.nanobot.domain.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow

data class SystemAccessState(
    val notificationPermissionRequired: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val accessibilityEnabled: Boolean
)

interface SystemAccessRepository {
    fun observeSystemAccessState(): Flow<SystemAccessState>
    suspend fun refresh()
    fun buildOpenNotificationSettingsIntent(): Intent
    fun buildOpenAccessibilitySettingsIntent(): Intent
}
