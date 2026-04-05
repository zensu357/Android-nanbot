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

    open fun performGlobalAction(action: PhoneGlobalAction): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.performGlobalAction(action)
    }

    open fun tapUiNode(selector: PhoneUiNodeSelector): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.tapNode(selector)
    }

    open fun inputText(selector: PhoneUiNodeSelector, text: String): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.inputText(selector, text)
    }

    open fun scrollNode(selector: PhoneUiNodeSelector?, direction: ScrollDirection): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.scrollNode(selector, direction)
    }

    open suspend fun waitForCondition(
        text: String?,
        contentDescription: String?,
        timeoutMs: Long
    ): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.waitForCondition(text, contentDescription, timeoutMs)
    }

    open fun performNodeAction(selector: PhoneUiNodeSelector, action: NodeAction): PhoneControlActionResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return PhoneControlActionResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.performNodeAction(selector, action)
    }

    open suspend fun takeScreenshot(quality: Int, maxWidth: Int): ScreenshotResult {
        val service = PhoneControlAccessibilityService.getInstance()
            ?: return ScreenshotResult(
                success = false,
                message = "Phone-control accessibility service is not enabled. Enable the Nanobot accessibility service in Android accessibility settings first."
            )
        return service.takeScreenshotBase64(quality, maxWidth)
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
