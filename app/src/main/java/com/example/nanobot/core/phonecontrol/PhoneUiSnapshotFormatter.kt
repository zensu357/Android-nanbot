package com.example.nanobot.core.phonecontrol

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneUiSnapshotFormatter @Inject constructor() {
    fun format(snapshot: PhoneUiSnapshot): String {
        return buildString {
            appendLine("Phone UI Snapshot")
            appendLine("Service Connected: ${snapshot.serviceConnected}")
            appendLine("Package: ${snapshot.packageName ?: "(unknown)"}")
            appendLine("Activity Title: ${snapshot.activityTitle ?: "(unknown)"}")
            appendLine("Captured At: ${snapshot.capturedAtEpochMs}")
            appendLine("Interactive Nodes: ${snapshot.interactiveNodeCount}")
            appendLine("Returned Nodes: ${snapshot.returnedNodeCount}")
            snapshot.warning?.takeIf { it.isNotBlank() }?.let { warning ->
                appendLine("Warning: $warning")
            }
            if (snapshot.nodes.isNotEmpty()) {
                appendLine("Nodes:")
                snapshot.nodes.forEach { node ->
                    append("- id=${node.nodeId}")
                    append(", depth=${node.depth}")
                    append(", class=${node.className ?: "(unknown)"}")
                    append(", text=${node.text ?: "(none)"}")
                    append(", contentDescription=${node.contentDescription ?: "(none)"}")
                    append(", viewId=${node.viewIdResourceName ?: "(none)"}")
                    append(", bounds=${node.boundsInScreen}")
                    append(", clickable=${node.clickable}")
                    append(", editable=${node.editable}")
                    append(", scrollable=${node.scrollable}")
                    append(", enabled=${node.enabled}")
                    append(", visibleToUser=${node.visibleToUser}")
                    appendLine()
                }
            }
        }.trim()
    }
}
