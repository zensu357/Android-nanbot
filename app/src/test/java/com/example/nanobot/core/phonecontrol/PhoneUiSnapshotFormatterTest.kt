package com.example.nanobot.core.phonecontrol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneUiSnapshotFormatterTest {
    private val formatter = PhoneUiSnapshotFormatter()

    @Test
    fun formatsSnapshotWithWarningAndNodeSummary() {
        val formatted = formatter.format(
            PhoneUiSnapshot(
                serviceConnected = true,
                packageName = "com.android.settings",
                activityTitle = "Settings",
                capturedAtEpochMs = 123456789L,
                interactiveNodeCount = 3,
                returnedNodeCount = 2,
                warning = "Accessibility root is partial.",
                nodes = listOf(
                    PhoneUiNode(
                        nodeId = "settings|TextView|Wi-Fi|0,0,100,40|d1",
                        text = "Wi-Fi",
                        contentDescription = null,
                        viewIdResourceName = "android:id/title",
                        className = "android.widget.TextView",
                        packageName = "com.android.settings",
                        boundsInScreen = "0,0,100,40",
                        clickable = true,
                        editable = false,
                        scrollable = false,
                        enabled = true,
                        visibleToUser = true,
                        depth = 1
                    )
                )
            )
        )

        assertTrue(formatted.contains("Phone UI Snapshot"))
        assertTrue(formatted.contains("Package: com.android.settings"))
        assertTrue(formatted.contains("Warning: Accessibility root is partial."))
        assertTrue(formatted.contains("text=Wi-Fi"))
        assertTrue(formatted.contains("viewId=android:id/title"))
    }

    @Test
    fun formatsEmptySnapshotWithoutNodesSection() {
        val formatted = formatter.format(
            PhoneUiSnapshot(
                serviceConnected = false,
                packageName = null,
                activityTitle = null,
                capturedAtEpochMs = 1L,
                interactiveNodeCount = 0,
                returnedNodeCount = 0,
                nodes = emptyList(),
                warning = "Service disabled"
            )
        )

        assertTrue(formatted.contains("Service Connected: false"))
        assertTrue(formatted.contains("Warning: Service disabled"))
        assertFalse(formatted.lineSequence().any { it.trim() == "Nodes:" })
    }
}
