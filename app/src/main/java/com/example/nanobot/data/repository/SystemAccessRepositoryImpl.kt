package com.example.nanobot.data.repository

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.nanobot.core.phonecontrol.PhoneControlAccessibilityService
import com.example.nanobot.domain.repository.SystemAccessRepository
import com.example.nanobot.domain.repository.SystemAccessState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SystemAccessRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemAccessRepository {
    private val state = MutableStateFlow(readCurrentState())

    override fun observeSystemAccessState(): Flow<SystemAccessState> = state.asStateFlow()

    override suspend fun refresh() {
        state.value = readCurrentState()
    }

    override fun buildOpenNotificationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun buildOpenAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun readCurrentState(): SystemAccessState {
        val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationPermissionGranted = !notificationPermissionRequired ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val accessibilityEnabled = isAccessibilityEnabled()
        return SystemAccessState(
            notificationPermissionRequired = notificationPermissionRequired,
            notificationPermissionGranted = notificationPermissionGranted,
            notificationsEnabled = notificationsEnabled,
            accessibilityEnabled = accessibilityEnabled
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val target = ComponentName(context, PhoneControlAccessibilityService::class.java).flattenToString()
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.resolveInfo?.serviceInfo?.let { serviceInfo ->
                    ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString() == target
                } == true
            }
    }
}
