package com.example.nanobot.core.phonecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

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
        return listOfNotNull(
            selector.nodeId?.let { expected -> nodeId.equals(expected, ignoreCase = true) },
            selector.text?.let { expected -> node.text?.toString()?.equals(expected, ignoreCase = true) == true },
            selector.contentDescription?.let { expected ->
                node.contentDescription?.toString()?.equals(expected, ignoreCase = true) == true
            }
        ).all { it }
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
                selector.contentDescription?.let { "contentDescription='$it'" }
            )
            if (filters.isNotEmpty()) {
                append(" matching ")
                append(filters.joinToString())
            }
            append('.')
        }
    }

    companion object {
        private val instance = AtomicReference<PhoneControlAccessibilityService?>(null)

        fun getInstance(): PhoneControlAccessibilityService? = instance.get()
    }
}
