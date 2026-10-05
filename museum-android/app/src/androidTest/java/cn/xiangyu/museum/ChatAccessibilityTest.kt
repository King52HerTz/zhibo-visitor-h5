package cn.xiangyu.museum

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var automation: UiAutomation
    private lateinit var server: TestMuseumServer
    private var accessibilityReady = false
    private val username = "P11_ACCESSIBILITY_${System.nanoTime()}"
    private val question = "测试问题：丝纱和纹样分别有什么线索？"
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun key(audience: Audience) = ProgressKey(server.endpoint, username, audience)

    private fun talkBackEnabled(): Boolean {
        val manager = compose.activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback"
        }
    }

    private fun assertTalkBackEnabled() = assertTrue("平台检查期间 TalkBack 应保持启用", talkBackEnabled())

    @Before fun prepareAccessibility() {
        assumeTrue("原生无障碍专项需要 API 28 及以上，未执行验收", Build.VERSION.SDK_INT >= 28)
        automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        // This platform-service suite is not executed when its external prerequisite is absent.
        assumeTrue("TalkBack 专项缺少已启用的服务，未执行验收", talkBackEnabled())
        accessibilityReady = true
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy && !model().controller.state.value.restoring }
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && node.packageName?.toString() == compose.activity.packageName && predicate(node)) return node
            repeat(node.childCount) { index ->
                node.getChild(index)?.let { child ->
                    find(child)?.let { match -> node.recycle(); return match }
                }
            }
            node.recycle()
            return null
        }
        val root = automation.rootInActiveWindow ?: return null
        if (root.packageName?.toString() != compose.activity.packageName) { root.recycle(); return null }
        return find(root)
    }

    private fun withNode(predicate: (AccessibilityNodeInfo) -> Boolean, check: (AccessibilityNodeInfo) -> Unit) {
        var node: AccessibilityNodeInfo? = null
        compose.waitUntil(5000) { findNode(predicate).also { node = it } != null }
        try { check(requireNotNull(node)) } finally { node?.recycle() }
    }

    private fun focusNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean) {
        withNode(predicate) { node ->
            if (node.isAccessibilityFocused) {
                assertTrue("$label 应能清除旧的无障碍焦点", node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS))
            }
            assertTrue("$label 应能获得无障碍焦点", node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            try { compose.waitUntil(5000) {
                val root = automation.rootInActiveWindow
                val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                try { focused == node } finally { focused?.recycle(); root?.recycle() }
            } } catch (timeout: ComposeTimeoutException) {
                throw AssertionError("$label 的无障碍焦点未停留在目标节点", timeout)
            }
        }
    }

    private fun login(audience: Audience) {
        server = TestMuseumServer()
        compose.runOnUiThread { model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
        compose.waitUntil(5000) { model().controller.state.value.username == username }
        compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(audience.label).performScrollTo().performClick()
        if (audience == Audience.CHILD) compose.onNodeWithText("跳过引入").performClick()
        compose.waitUntil(5000) { !model().progress.state.value.loading && !model().observations.state.value.loading &&
            !model().controller.state.value.chatBusy }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun hideKeyboard() {
        compose.runOnUiThread {
            (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(5000) { Build.VERSION.SDK_INT < 30 ||
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true }
        compose.waitForIdle()
    }

    private fun hasDialogLabel(node: AccessibilityNodeInfo, label: String): Boolean {
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                try { if (hasDialogLabel(child, label)) return true } finally { child.recycle() }
            }
        }
        return false
    }

    private fun dialogButton(node: AccessibilityNodeInfo, label: String) =
        node.isClickable && node.isScreenReaderFocusable && hasDialogLabel(node, label)

    private fun assertQuestionDialog(action: String, confirm: String) {
        val title = "问题还没发送"
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title),
            useUnmergedTree = true).assertCountEquals(1)
        withNode({ it.paneTitle?.toString() == title }) { assertEquals(title, it.paneTitle?.toString()) }
        withNode({ it.text?.toString() == title }) { assertTrue("确认标题应标记为标题", it.isHeading) }
        withNode({ it.text?.toString() == "${action}会清空当前未发送的问题。" }) {
            assertTrue("确认说明应完整暴露给无障碍服务", it.isVisibleToUser)
        }
        // Synchronize platform accessibility events before explicitly moving focus.
        automation.waitForIdle(500, 5000)
        focusNode("确认标题") { it.text?.toString() == title }
        listOf("继续提问", confirm).forEach { label ->
            withNode({ dialogButton(it, label) }) { node ->
                assertTrue("确认按钮应可用", node.isEnabled)
                assertTrue("确认按钮应暴露点击操作", node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
            }
            focusNode(label) { dialogButton(it, label) }
        }
        assertTalkBackEnabled()
    }

    private fun cancelAndCheckChat(audience: Audience) {
        withNode({ dialogButton(it, "继续提问") }) {
            assertTrue("保留问题应能通过无障碍点击完成", it.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("问题还没发送").fetchSemanticsNodes().isEmpty() }
        tab(if (audience == Audience.CHILD) "问阿帛" else "讲解").assertIsSelected()
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(audience, model().controller.state.value.audience)
        assertEquals(username, model().controller.state.value.username)
        compose.onNode(hasSetTextAction() and SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString(question))).assertIsDisplayed()
        focusNode("保留问题的输入框") { it.isEditable && it.text?.toString() == question }
        focusNode("发送问题") { it.contentDescription?.toString() == "发送问题" && it.isEnabled }
        assertEquals(0, server.requests.count { it.first == "/mobile/chat/stream" })
        assertTalkBackEnabled()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = requireNotNull(automation.takeScreenshot())
        try { File(context.getExternalFilesDir(null), "$name-${context.resources.configuration.fontScale}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @After fun cleanup() {
        if (!accessibilityReady) return
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy }
        if (::server.isInitialized) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val camera = LocalJournalCamera(context)
            camera.pending()?.takeIf { capture -> Audience.entries.any { capture.scope == localObservationScope(key(it)) } }
                ?.let { camera.discard(it.id) }
            runBlocking { SqliteLocalObservationStore(context).use { store ->
                Audience.entries.forEach { audience -> store.load(key(audience)).forEach { store.delete(key(audience), it.id) } }
            } }
            if (context.getDatabasePath("museum_progress.db").exists()) {
                context.openOrCreateDatabase("museum_progress.db", Context.MODE_PRIVATE, null).use {
                    it.delete("tasks", "endpoint=? AND username=?", arrayOf(server.endpoint, username))
                }
            }
            if (context.getDatabasePath("museum_story.db").exists()) {
                context.openOrCreateDatabase("museum_story.db", Context.MODE_PRIVATE, null).use {
                    it.delete("chapter", "scope=?", arrayOf(storyChapterScope(server.endpoint, username)))
                }
            }
            server.close()
        }
        assertTalkBackEnabled()
    }

    @Test fun compactNonemptyQuestionsKeepTheirPurposeAndEditableContent() {
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD, draft = question))
        compose.runOnUiThread { compose.activity.setContent { MuseumTheme {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                ChatContent(state, { state = state.copy(draft = it) }, {}, {}, {}, {}, {}, {},
                    Modifier.width(240.dp).fillMaxHeight().align(Alignment.Center).imePadding())
            }
        } } }
        listOf(Audience.CHILD to "想问阿帛什么？", Audience.ADULT to "想了解什么？").forEach { (audience, purpose) ->
            compose.runOnIdle { state = state.copy(audience = audience) }
            compose.onNode(hasSetTextAction() and SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, AnnotatedString(question))).assertIsDisplayed()
            withNode({ it.isEditable && it.text?.toString() == question }) { node ->
                assertEquals("用途标签不能替代已输入的问题", question, node.text?.toString())
                assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT })
                assertEquals("非空紧凑输入仍应提供提问用途", purpose, node.contentDescription?.toString())
            }
            compose.onNodeWithContentDescription(purpose).assertIsDisplayed()
        }
        assertTalkBackEnabled()
    }

    @Test fun childQuestionConfirmationExposesHeadingPaneAndAccessibleActions() {
        login(Audience.CHILD)
        tab("问阿帛").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(question)
        hideKeyboard()
        compose.onNode(hasText("切换模式", substring = true) and hasClickAction()).performClick()
        assertEquals(question, model().controller.state.value.draft)
        assertQuestionDialog("切换模式", "放下问题并切换")
        screenshot("p11-child-accessible-confirm")
        cancelAndCheckChat(Audience.CHILD)
        screenshot("p11-child-accessible-return")
    }

    @Test fun hiddenAdultQuestionKeepsAccessibleConfirmationAcrossRecreation() {
        login(Audience.ADULT)
        tab("讲解").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(question)
        hideKeyboard()
        tab("我的").performClick().assertIsSelected()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        assertQuestionDialog("退出登录", "放下问题并退出")
        compose.activityRule.scenario.recreate()
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(Audience.ADULT, model().controller.state.value.audience)
        assertEquals(username, model().controller.state.value.username)
        assertQuestionDialog("退出登录", "放下问题并退出")
        screenshot("p11-adult-accessible-recreated-confirm")
        cancelAndCheckChat(Audience.ADULT)
        screenshot("p11-adult-accessible-return")
    }
}
