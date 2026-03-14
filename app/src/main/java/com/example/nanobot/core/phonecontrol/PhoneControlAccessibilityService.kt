package com.example.nanobot.core.phonecontrol

import android.accessibilityservice.AccessibilityService
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

    companion object {
        private val instance = AtomicReference<PhoneControlAccessibilityService?>(null)

        fun getInstance(): PhoneControlAccessibilityService? = instance.get()
    }
}
