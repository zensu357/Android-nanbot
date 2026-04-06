package com.example.nanobot.core.phonecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import androidx.annotation.RequiresApi
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class PhoneControlAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance.set(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance.compareAndSet(this, null)
        super.onDestroy()
    }

    fun captureSnapshot(includeNonInteractive: Boolean, maxNodes: Int): PhoneUiSnapshot {
        val root = rootInActiveWindow
        if (root == null) {
            return PhoneUiSnapshot(
                serviceConnected = true,
                packageName = null,
                activityTitle = null,
                capturedAtEpochMs = System.currentTimeMillis(),
                interactiveNodeCount = 0,
                returnedNodeCount = 0,
                nodes = emptyList(),
                warning = "Accessibility service is enabled, but there is no active window to inspect."
            )
        }

        val allNodes = mutableListOf<PhoneUiNode>()
        val interactiveCount = countInteractiveNodes(root)
        traverse(
            node = root,
            depth = 0,
            includeNonInteractive = includeNonInteractive,
            maxNodes = maxNodes,
            into = allNodes
        )

        return PhoneUiSnapshot(
            serviceConnected = true,
            packageName = root.packageName?.toString(),
            activityTitle = root.contentDescription?.toString(),
            capturedAtEpochMs = System.currentTimeMillis(),
            interactiveNodeCount = interactiveCount,
            returnedNodeCount = allNodes.size,
            nodes = allNodes
        )
    }

    fun performGlobalAction(action: PhoneGlobalAction): PhoneControlActionResult {
        val success = when (action) {
            PhoneGlobalAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            PhoneGlobalAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            PhoneGlobalAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            PhoneGlobalAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            PhoneGlobalAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            PhoneGlobalAction.POWER_DIALOG -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            PhoneGlobalAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            PhoneGlobalAction.TAKE_SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
        return if (success) {
            PhoneControlActionResult(true, "Performed global action '${action.wireName}'.")
        } else {
            PhoneControlActionResult(false, "Failed to perform global action '${action.wireName}'.")
        }
    }

    fun tapNode(selector: PhoneUiNodeSelector): PhoneControlActionResult {
        val root = rootInActiveWindow
            ?: return PhoneControlActionResult(false, "Accessibility service is enabled, but there is no active window to inspect.")
        val target = findNode(root, selector)
            ?: return PhoneControlActionResult(false, buildNotFoundMessage(selector))
        val clickableTarget = firstClickableAncestor(target) ?: target
        val success = clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (success) {
            PhoneControlActionResult(true, "Tapped UI node '${describeNode(target)}'.")
        } else {
            PhoneControlActionResult(false, "Matched UI node '${describeNode(target)}' but the click action was rejected.")
        }
    }

    fun inputText(selector: PhoneUiNodeSelector, text: String): PhoneControlActionResult {
        val root = rootInActiveWindow
            ?: return PhoneControlActionResult(false, "Accessibility service is enabled, but there is no active window to inspect.")
        val target = findNode(root, selector)
            ?: return PhoneControlActionResult(false, buildNotFoundMessage(selector))
        if (!target.isEditable) {
            return PhoneControlActionResult(false, "Matched UI node '${describeNode(target)}' but it is not editable.")
        }
        val focusResult = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!focusResult) {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (success) {
            PhoneControlActionResult(true, "Set text on '${describeNode(target)}' (${text.length} chars).")
        } else {
            PhoneControlActionResult(false, "Matched editable node '${describeNode(target)}' but ACTION_SET_TEXT was rejected.")
        }
    }

    fun scrollNode(selector: PhoneUiNodeSelector?, direction: ScrollDirection): PhoneControlActionResult {
        val root = rootInActiveWindow
            ?: return PhoneControlActionResult(false, "Accessibility service is enabled, but there is no active window to inspect.")
        val target = if (selector != null) {
            findNode(root, selector)
                ?: return PhoneControlActionResult(false, buildNotFoundMessage(selector))
        } else {
            findFirstScrollable(root)
                ?: return PhoneControlActionResult(false, "No scrollable node found in the current UI.")
        }
        if (!target.isScrollable) {
            return PhoneControlActionResult(false, "Matched UI node '${describeNode(target)}' but it is not scrollable.")
        }
        val action = when (direction) {
            ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val success = target.performAction(action)
        return if (success) {
            PhoneControlActionResult(true, "Scrolled ${direction.wireName} on '${describeNode(target)}'.")
        } else {
            PhoneControlActionResult(false, "Matched scrollable node '${describeNode(target)}' but scroll action was rejected.")
        }
    }

    suspend fun waitForCondition(
        text: String?,
        contentDescription: String?,
        timeoutMs: Long
    ): PhoneControlActionResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        val pollIntervalMs = 300L
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null && matchesCondition(root, text, contentDescription)) {
                return PhoneControlActionResult(true, "Condition met: found ${describeCondition(text, contentDescription)}.")
            }
            delay(pollIntervalMs)
        }
        return PhoneControlActionResult(false, "Timed out after ${timeoutMs}ms waiting for ${describeCondition(text, contentDescription)}.")
    }

    fun performNodeAction(selector: PhoneUiNodeSelector, action: NodeAction): PhoneControlActionResult {
        val root = rootInActiveWindow
            ?: return PhoneControlActionResult(false, "Accessibility service is enabled, but there is no active window to inspect.")
        val target = findNode(root, selector)
            ?: return PhoneControlActionResult(false, buildNotFoundMessage(selector))
        val actionId = when (action) {
            NodeAction.CLICK -> AccessibilityNodeInfo.ACTION_CLICK
            NodeAction.LONG_CLICK -> AccessibilityNodeInfo.ACTION_LONG_CLICK
            NodeAction.FOCUS -> AccessibilityNodeInfo.ACTION_FOCUS
            NodeAction.CLEAR_FOCUS -> AccessibilityNodeInfo.ACTION_CLEAR_FOCUS
            NodeAction.SELECT -> AccessibilityNodeInfo.ACTION_SELECT
            NodeAction.CLEAR_SELECTION -> AccessibilityNodeInfo.ACTION_CLEAR_SELECTION
            NodeAction.COPY -> AccessibilityNodeInfo.ACTION_COPY
            NodeAction.PASTE -> AccessibilityNodeInfo.ACTION_PASTE
            NodeAction.CUT -> AccessibilityNodeInfo.ACTION_CUT
        }
        val success = target.performAction(actionId)
        return if (success) {
            PhoneControlActionResult(true, "Performed '${action.wireName}' on '${describeNode(target)}'.")
        } else {
            PhoneControlActionResult(false, "Matched node '${describeNode(target)}' but '${action.wireName}' was rejected.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotBase64(quality: Int, maxWidth: Int): com.example.nanobot.core.phonecontrol.ScreenshotResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return com.example.nanobot.core.phonecontrol.ScreenshotResult(
                success = false,
                message = "Screenshot requires Android 11 (API 30) or higher. Current: API ${Build.VERSION.SDK_INT}."
            )
        }
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        if (bitmap == null) {
                            continuation.resume(
                                com.example.nanobot.core.phonecontrol.ScreenshotResult(false, "Failed to decode screenshot hardware buffer.")
                            )
                            return
                        }
                        val scaled = if (bitmap.width > maxWidth) {
                            val ratio = maxWidth.toFloat() / bitmap.width
                            val newHeight = (bitmap.height * ratio).toInt()
                            Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true).also {
                                if (it !== bitmap) bitmap.recycle()
                            }
                        } else {
                            bitmap
                        }
                        val stream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 95), stream)
                        scaled.recycle()
                        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        continuation.resume(
                            com.example.nanobot.core.phonecontrol.ScreenshotResult(
                                success = true,
                                message = "Screenshot captured (${stream.size()} bytes, JPEG q=$quality).",
                                base64Jpeg = base64
                            )
                        )
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(
                            com.example.nanobot.core.phonecontrol.ScreenshotResult(false, "Screenshot failed with error code $errorCode.")
                        )
                    }
                }
            )
        }
    }

    private fun matchesCondition(node: AccessibilityNodeInfo, text: String?, contentDescription: String?): Boolean {
        val textMatch = text == null || node.text?.toString()?.contains(text, ignoreCase = true) == true
        val cdMatch = contentDescription == null || node.contentDescription?.toString()?.contains(contentDescription, ignoreCase = true) == true
        if (textMatch && cdMatch && (text != null || contentDescription != null)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (matchesCondition(child, text, contentDescription)) return true
        }
        return false
    }

    private fun describeCondition(text: String?, contentDescription: String?): String {
        val parts = listOfNotNull(
            text?.let { "text='$it'" },
            contentDescription?.let { "contentDescription='$it'" }
        )
        return if (parts.isNotEmpty()) parts.joinToString(" and ") else "(any)"
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findFirstScrollable(child)?.let { return it }
        }
        return null
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        depth: Int,
        includeNonInteractive: Boolean,
        maxNodes: Int,
        into: MutableList<PhoneUiNode>
    ) {
        if (into.size >= maxNodes) return
        val isInteractive = isInteractive(node)
        if (includeNonInteractive || isInteractive) {
            into += node.toModel(depth)
        }
        for (index in 0 until node.childCount) {
            if (into.size >= maxNodes) break
            val child = node.getChild(index) ?: continue
            traverse(
                node = child,
                depth = depth + 1,
                includeNonInteractive = includeNonInteractive,
                maxNodes = maxNodes,
                into = into
            )
        }
    }

    private fun countInteractiveNodes(node: AccessibilityNodeInfo): Int {
        var count = if (isInteractive(node)) 1 else 0
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            count += countInteractiveNodes(child)
        }
        return count
    }

    private fun isInteractive(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isEditable || node.isScrollable || node.isLongClickable
    }

    private fun AccessibilityNodeInfo.toModel(depth: Int): PhoneUiNode {
        val rect = Rect()
        getBoundsInScreen(rect)
        val nodeId = buildNodeId(this, rect, depth)
        return PhoneUiNode(
            nodeId = nodeId,
            text = text?.toString()?.takeIf { it.isNotBlank() },
            contentDescription = contentDescription?.toString()?.takeIf { it.isNotBlank() },
            viewIdResourceName = viewIdResourceName?.takeIf { it.isNotBlank() },
            className = className?.toString()?.takeIf { it.isNotBlank() },
            packageName = packageName?.toString()?.takeIf { it.isNotBlank() },
            boundsInScreen = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            clickable = isClickable,
            editable = isEditable,
            scrollable = isScrollable,
            enabled = isEnabled,
            visibleToUser = isVisibleToUser,
            depth = depth
        )
    }

    private fun buildNodeId(node: AccessibilityNodeInfo, rect: Rect, depth: Int): String {
        val parts = listOfNotNull(
            node.viewIdResourceName?.takeIf { it.isNotBlank() },
            node.className?.toString()?.takeIf { it.isNotBlank() },
            node.text?.toString()?.takeIf { it.isNotBlank() }?.take(24),
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.take(24),
            "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            "d$depth"
        )
        return parts.joinToString(separator = "|")
    }

    private fun findNode(node: AccessibilityNodeInfo, selector: PhoneUiNodeSelector): AccessibilityNodeInfo? {
        if (matches(node, selector)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findNode(child, selector)?.let { return it }
        }
        return null
    }

    private fun matches(node: AccessibilityNodeInfo, selector: PhoneUiNodeSelector): Boolean {
        val rect = Rect().also(node::getBoundsInScreen)
        val nodeId = buildNodeId(node, rect, depth = 0)
        val checks = listOfNotNull(
            selector.nodeId?.let { expected -> nodeId.equals(expected, ignoreCase = true) },
            selector.text?.let { expected -> matchField(node.text?.toString(), expected, selector.matchMode) },
            selector.contentDescription?.let { expected ->
                matchField(node.contentDescription?.toString(), expected, selector.matchMode)
            },
            selector.className?.let { expected ->
                matchField(node.className?.toString(), expected, selector.matchMode)
            },
            selector.viewIdResourceName?.let { expected ->
                matchField(node.viewIdResourceName, expected, selector.matchMode)
            }
        )
        return checks.isNotEmpty() && checks.all { it }
    }

    private fun matchField(actual: String?, expected: String, mode: SelectorMatchMode): Boolean {
        if (actual == null) return false
        return when (mode) {
            SelectorMatchMode.EXACT -> actual.equals(expected, ignoreCase = true)
            SelectorMatchMode.CONTAINS -> actual.contains(expected, ignoreCase = true)
            SelectorMatchMode.REGEX -> runCatching {
                Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(actual)
            }.getOrDefault(false)
        }
    }

    private fun firstClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        return node.viewIdResourceName
            ?: node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: node.className?.toString()
            ?: "unknown-node"
    }

    private fun buildNotFoundMessage(selector: PhoneUiNodeSelector): String {
        return buildString {
            append("Could not find a foreground UI node")
            val filters = listOfNotNull(
                selector.nodeId?.let { "nodeId='$it'" },
                selector.text?.let { "text='$it'" },
                selector.contentDescription?.let { "contentDescription='$it'" },
                selector.className?.let { "className='$it'" },
                selector.viewIdResourceName?.let { "viewId='$it'" }
            )
            if (filters.isNotEmpty()) {
                append(" matching ")
                append(filters.joinToString())
                if (selector.matchMode != SelectorMatchMode.EXACT) {
                    append(" (${selector.matchMode.wireName})")
                }
            }
            append('.')
        }
    }

    companion object {
        private val instance = AtomicReference<PhoneControlAccessibilityService?>(null)

        fun getInstance(): PhoneControlAccessibilityService? = instance.get()
    }
}
