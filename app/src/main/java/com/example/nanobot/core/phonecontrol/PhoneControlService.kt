package com.example.nanobot.core.phonecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PhoneControlService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun readCurrentUi(includeNonInteractive: Boolean, maxNodes: Int): PhoneUiSnapshot {
        val service = PhoneControlAccessibilityService.getInstance()
        if (service == null) {
            return PhoneUiSnapshot(
                serviceConnected = false,
                packageName = null,
                activityTitle = null,
                capturedAtEpochMs = System.currentTimeMillis(),
                interactiveNodeCount = 0,
                returnedNodeCount = 0,
                nodes = emptyList(),
                warning = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        }
        return service.captureSnapshot(
            includeNonInteractive = includeNonInteractive,
            maxNodes = maxNodes.coerceIn(1, 200)
        )
    }

    open fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    open fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            true
        }.getOrDefault(false)
    }
}
