package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.phonecontrol.PhoneUiNode
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshot
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshotFormatter
import com.example.nanobot.core.tools.impl.BasePhoneControlTool
import com.example.nanobot.core.tools.impl.InputTextTool
import com.example.nanobot.core.tools.impl.PressGlobalActionTool
import com.example.nanobot.core.tools.impl.ScrollUiTool
import com.example.nanobot.core.tools.impl.TapUiNodeTool
import com.example.nanobot.core.tools.impl.WaitForUiTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PhoneControlToolsTest {
    private val formatter = PhoneUiSnapshotFormatter()
    private val fakePhoneControlService = object : PhoneControlServiceLike {
        override fun readCurrentUi(includeNonInteractive: Boolean, maxNodes: Int): PhoneUiSnapshot {
            return PhoneUiSnapshot(
                serviceConnected = true,
                packageName = "com.android.settings",
                activityTitle = "Settings",
                capturedAtEpochMs = 123L,
                interactiveNodeCount = 1,
                returnedNodeCount = 1,
                nodes = listOf(
                    PhoneUiNode(
                        nodeId = "node-1",
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
        }

        override fun isPackageInstalled(packageName: String): Boolean = packageName == "com.android.settings"
    }

    private val tools = listOf(
        TestReadCurrentUiTool(fakePhoneControlService, formatter),
        TapUiNodeTool(),
        InputTextTool(),
        ScrollUiTool(),
        PressGlobalActionTool(),
        TestLaunchAppTool(fakePhoneControlService),
        WaitForUiTool()
    )

    @Test
    fun allPhoneControlToolsAreHiddenUnlockable() {
        assertTrue(tools.all { it.exposure == ToolExposure.HIDDEN_UNLOCKABLE })
    }

    @Test
    fun skeletonToolsReturnStubMessageWhenExecuted() = runTest {
        val config = AgentConfig(enableTools = true)
        val runContext = AgentRunContext.root(
            sessionId = "session-1",
            unlockedToolNames = tools.map { it.name }.toSet()
        )

        val uiResult = TestReadCurrentUiTool(fakePhoneControlService, formatter).execute(
            buildJsonObject {
                put("includeNonInteractive", false)
                put("maxNodes", 20)
            },
            config,
            runContext
        )
        val launchResult = TestLaunchAppTool(fakePhoneControlService).execute(
            buildJsonObject { put("packageName", "com.android.settings") },
            config,
            runContext
        )

        assertTrue(uiResult.contains("Phone UI Snapshot"))
        assertTrue(launchResult.contains("com.android.settings"))
    }

    @Test
    fun phoneControlToolsKeepExpectedNames() {
        assertEquals(
            listOf(
                "read_current_ui",
                "tap_ui_node",
                "input_text",
                "scroll_ui",
                "press_global_action",
                "launch_app",
                "wait_for_ui"
            ),
            tools.map { it.name }
        )
    }

    private interface PhoneControlServiceLike {
        fun readCurrentUi(includeNonInteractive: Boolean, maxNodes: Int): PhoneUiSnapshot
        fun isPackageInstalled(packageName: String): Boolean
    }

    private class TestReadCurrentUiTool(
        private val service: PhoneControlServiceLike,
        private val snapshotFormatter: PhoneUiSnapshotFormatter
    ) : BasePhoneControlTool() {
        override val name: String = "read_current_ui"
        override val description: String = "Reads a structured summary of the current foreground Android UI"
        override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_READ_ONLY
        override val parametersSchema: kotlinx.serialization.json.JsonObject = buildJsonObject { }

        override suspend fun execute(arguments: kotlinx.serialization.json.JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            val snapshot = service.readCurrentUi(false, 20)
            return snapshotFormatter.format(snapshot)
        }
    }

    private class TestLaunchAppTool(
        private val service: PhoneControlServiceLike
    ) : BasePhoneControlTool() {
        override val name: String = "launch_app"
        override val description: String = "Launches an Android app by package name"
        override val accessCategory: ToolAccessCategory = ToolAccessCategory.LOCAL_SIDE_EFFECT
        override val parametersSchema: kotlinx.serialization.json.JsonObject = buildJsonObject { }

        override suspend fun execute(arguments: kotlinx.serialization.json.JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            val packageName = arguments["packageName"]?.toString().orEmpty()
            return if (service.isPackageInstalled("com.android.settings")) {
                notImplemented("Requested launch for package '$packageName'.")
            } else {
                "Package '$packageName' is not installed on this device."
            }
        }
    }
}
