package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.phonecontrol.PhoneControlActionResult
import com.example.nanobot.core.phonecontrol.PhoneControlService
import com.example.nanobot.core.phonecontrol.PhoneGlobalAction
import com.example.nanobot.core.phonecontrol.PhoneUiNode
import com.example.nanobot.core.phonecontrol.PhoneUiNodeSelector
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshot
import com.example.nanobot.core.phonecontrol.PhoneUiSnapshotFormatter
import com.example.nanobot.core.phonecontrol.ScrollDirection
import com.example.nanobot.core.phonecontrol.SelectorMatchMode
import com.example.nanobot.core.phonecontrol.ScreenshotResult
import com.example.nanobot.core.tools.impl.InputTextTool
import com.example.nanobot.core.tools.impl.LaunchAppTool
import com.example.nanobot.core.tools.impl.PressGlobalActionTool
import com.example.nanobot.core.tools.impl.ReadCurrentUiTool
import com.example.nanobot.core.tools.impl.ScrollUiTool
import com.example.nanobot.core.tools.impl.TapUiNodeTool
import com.example.nanobot.core.tools.impl.WaitForUiTool
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneControlToolsTest {
    private val formatter = PhoneUiSnapshotFormatter()
    private val fakePhoneControlService = FakePhoneControlService()

    private val tools = listOf(
        ReadCurrentUiTool(fakePhoneControlService, formatter),
        TapUiNodeTool(fakePhoneControlService, formatter),
        InputTextTool(fakePhoneControlService, formatter),
        ScrollUiTool(fakePhoneControlService, formatter),
        PressGlobalActionTool(fakePhoneControlService, formatter),
        LaunchAppTool(fakePhoneControlService, formatter),
        WaitForUiTool(fakePhoneControlService, formatter)
    )

    @Test
    fun allPhoneControlToolsAreHiddenUnlockable() {
        assertTrue(tools.all { it.exposure == ToolExposure.HIDDEN_UNLOCKABLE })
    }

    @Test
    fun readCurrentUiFormatsSnapshot() = runTest {
        val result = ReadCurrentUiTool(fakePhoneControlService, formatter).execute(
            buildJsonObject {
                put("includeNonInteractive", false)
                put("maxNodes", 20)
            },
            AgentConfig(enableTools = true),
            unlockedRunContext()
        )

        assertTrue(result.contains("Phone UI Snapshot"))
        assertTrue(result.contains("Package: com.android.settings"))
    }

    @Test
    fun pressGlobalActionDelegatesToPhoneControlService() = runTest {
        val result = PressGlobalActionTool(fakePhoneControlService, formatter).execute(
            buildJsonObject { put("action", "home") },
            AgentConfig(enableTools = true),
            unlockedRunContext()
        )

        assertTrue(result.contains("Performed global action 'home'."))
        assertTrue(result.contains("--- UI After Action ---"))
        assertEquals(PhoneGlobalAction.HOME, fakePhoneControlService.lastGlobalAction)
    }

    @Test
    fun tapUiNodeDelegatesToPhoneControlService() = runTest {
        val result = TapUiNodeTool(fakePhoneControlService, formatter).execute(
            buildJsonObject {
                put("text", "Wi-Fi")
            },
            AgentConfig(enableTools = true),
            unlockedRunContext()
        )
        assertTrue(result.contains("Tapped UI node 'android:id/title'."))
        assertEquals(
            PhoneUiNodeSelector(text = "Wi-Fi", matchMode = SelectorMatchMode.EXACT),
            fakePhoneControlService.lastNodeSelector
        )
    }

    @Test
    fun launchAppInvokesPhoneControlService() = runTest {
        val result = LaunchAppTool(fakePhoneControlService, formatter).execute(
            buildJsonObject { put("packageName", "com.android.settings") },
            AgentConfig(enableTools = true),
            unlockedRunContext()
        )

        assertTrue(result.contains("Launched package 'com.android.settings'."))
        assertEquals("com.android.settings", fakePhoneControlService.lastLaunchedPackage)
    }

    @Test
    fun inputTextDelegatesToPhoneControlService() = runTest {
        val inputResult = InputTextTool(fakePhoneControlService, formatter).execute(
            buildJsonObject {
                put("nodeId", "input-1")
                put("inputText", "hello")
            },
            AgentConfig(enableTools = true),
            unlockedRunContext()
        )

        assertTrue(inputResult.contains("Entered text into 'input-1'."))
    }

    @Test
    fun takeScreenshotStructuredReturnsMultimodalResult() = runTest {
        val result = com.example.nanobot.core.tools.impl.TakeScreenshotTool(fakePhoneControlService)
            .executeStructured(
                buildJsonObject {},
                AgentConfig(enableTools = true),
                unlockedRunContext().copy(supportsVision = true)
            )

        val multimodal = assertIs<ToolResult.Multimodal>(result)
        assertEquals("Screenshot captured (3 bytes, JPEG q=60).", multimodal.text)
        assertEquals("data:image/jpeg;base64,AAA", multimodal.images.single().dataUrl)
    }

    private fun unlockedRunContext(): AgentRunContext {
        return AgentRunContext.root(
            sessionId = "session-1",
            unlockedToolNames = tools.map { it.name }.toSet()
        )
    }

    private class FakePhoneControlService : PhoneControlService(RuntimeEnvironment.getApplication()) {
        var lastGlobalAction: PhoneGlobalAction? = null
        var lastNodeSelector: PhoneUiNodeSelector? = null
        var lastLaunchedPackage: String? = null
        var lastInputText: String? = null

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

        override fun performGlobalAction(action: PhoneGlobalAction): PhoneControlActionResult {
            lastGlobalAction = action
            return PhoneControlActionResult(true, "Performed global action '${action.wireName}'.")
        }

        override fun tapUiNode(selector: PhoneUiNodeSelector): PhoneControlActionResult {
            lastNodeSelector = selector
            return PhoneControlActionResult(true, "Tapped UI node 'android:id/title'.")
        }

        override fun inputText(selector: PhoneUiNodeSelector, text: String): PhoneControlActionResult {
            lastNodeSelector = selector
            lastInputText = text
            return PhoneControlActionResult(true, "Entered text into '${selector.nodeId}'.")
        }

        override fun scrollNode(selector: PhoneUiNodeSelector?, direction: ScrollDirection): PhoneControlActionResult {
            return PhoneControlActionResult(true, "Scrolled ${direction.wireName}.")
        }

        override suspend fun waitForCondition(
            text: String?,
            contentDescription: String?,
            timeoutMs: Long
        ): PhoneControlActionResult {
            return PhoneControlActionResult(true, "Condition met.")
        }

        override suspend fun takeScreenshot(quality: Int, maxWidth: Int): ScreenshotResult {
            return ScreenshotResult(true, "Screenshot captured (3 bytes, JPEG q=$quality).", "AAA")
        }

        override fun launchApp(packageName: String): Boolean {
            lastLaunchedPackage = packageName
            return true
        }

        override fun isPackageInstalled(packageName: String): Boolean = packageName == "com.android.settings"
    }
}
