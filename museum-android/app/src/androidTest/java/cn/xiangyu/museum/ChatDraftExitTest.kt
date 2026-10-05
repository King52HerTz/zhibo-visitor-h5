package cn.xiangyu.museum

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatDraftExitTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: TestMuseumServer
    private val username = "P10_DRAFT_${System.nanoTime()}"
    private val question = "测试问题：这件衣服的丝纱为什么这么轻？"
    private val observation = "测试观察：记下丝纱和纹样的线索。"
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun key(audience: Audience) = ProgressKey(server.endpoint, username, audience)
    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun chatTab(audience: Audience) = tab(if (audience == Audience.CHILD) "问阿帛" else "讲解")
    private fun journalTab(audience: Audience) = tab(if (audience == Audience.CHILD) "我的手帐" else "我的")
    private fun switchMode() = compose.onNode(hasText("切换模式", substring = true) and hasClickAction())
    private fun input(): SemanticsNodeInteraction {
        val state = model().controller.state.value
        val text = if (state.draft.isEmpty()) hasText(
            if (state.audience == Audience.ADULT) "想了解什么？" else "想问阿帛什么？")
        else SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(state.draft))
        return compose.onNode(hasSetTextAction() and text)
    }

    @Before fun login() {
        server = TestMuseumServer()
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy && !model().controller.state.value.restoring }
        compose.runOnUiThread { model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
        compose.waitUntil(5000) { model().controller.state.value.username == username }
        compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun enter(audience: Audience) {
        compose.onNodeWithText(audience.label).performScrollTo().performClick()
        if (audience == Audience.CHILD) compose.onNodeWithText("跳过引入").performClick()
        compose.waitUntil(5000) { !model().progress.state.value.loading && !model().observations.state.value.loading &&
            !model().controller.state.value.chatBusy }
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(5000) { Build.VERSION.SDK_INT < 30 ||
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true }
        compose.waitForIdle()
    }

    private fun assertNoSends() {
        assertEquals(0, server.requests.count { it.first == "/mobile/chat/stream" })
    }

    private fun assertCompleteText(matcher: SemanticsMatcher) {
        val node = compose.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
        val layout = layouts.single()
        assertFalse("确认说明不能省略文字", layout.multiParagraph.didExceedMaxLines)
        assertTrue("确认文字应在可见宽度内", layout.size.width <= bounds.width + 1f)
        assertTrue("确认文字应完整可见", layout.size.height <= bounds.height + 1f)
        repeat(layout.lineCount) { line ->
            assertFalse("确认文字不能使用省略号", layout.isLineEllipsized(line))
            assertTrue("每一行应在文字宽度内", layout.getLineLeft(line) >= -1f &&
                layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("每一行应在文字高度内", layout.getLineTop(line) >= -1f &&
                layout.getLineBottom(line) <= layout.size.height + 1f)
        }
    }

    private fun assertQuestionDialog(confirm: String) {
        assertCompleteText(hasText("问题还没发送"))
        assertCompleteText(hasText("未发送的问题", substring = true))
        assertCompleteText(hasText("继续提问"))
        assertCompleteText(hasText(confirm))
        compose.onNodeWithText("放下草稿？").assertDoesNotExist()
    }

    private fun screenshot(name: String, visibleText: String) {
        hideKeyboard()
        compose.onNodeWithText(visibleText).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun containsVisibleText(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isVisibleToUser && (node.text?.contains(visibleText) == true ||
                    node.contentDescription?.contains(visibleText) == true)) return true
            return (0 until node.childCount).any { containsVisibleText(node.getChild(it)) }
        }
        compose.waitUntil(5000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.packageName?.toString() == compose.activity.packageName && containsVisibleText(root)
        }
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val context = instrumentation.targetContext
        File(context.getExternalFilesDir(null), "$name-${context.resources.configuration.fontScale}.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy }
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

    @Test fun childModeSwitchKeepsTheQuestionUntilExplicitConfirmation() {
        enter(Audience.CHILD)
        chatTab(Audience.CHILD).performClick()
        input().performTextInput(question)
        hideKeyboard()
        switchMode().performClick()
        assertEquals("切换前必须保留未发送的问题", question, model().controller.state.value.draft)
        assertEquals(Audience.CHILD, model().controller.state.value.audience)
        assertQuestionDialog("放下问题并切换")
        screenshot("p10-chat-mode-confirm", "问题还没发送")
        compose.onNodeWithText("继续提问").performClick()
        chatTab(Audience.CHILD).assertIsSelected()
        input().assertTextContains(question)
        screenshot("p10-chat-return", question)
        assertNoSends()
        switchMode().performClick()
        compose.onNodeWithText("放下问题并切换").performClick()
        assertNull(model().controller.state.value.audience)
        assertEquals("", model().controller.state.value.draft)
        compose.onNodeWithText("今天，怎样出发？").assertIsDisplayed()
        assertNoSends()
    }

    @Test fun hiddenAdultQuestionSurvivesLogoutConfirmationRecreationAndCancel() {
        enter(Audience.ADULT)
        chatTab(Audience.ADULT).performClick()
        input().performTextInput(question)
        hideKeyboard()
        journalTab(Audience.ADULT).performClick().assertIsSelected()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(username, model().controller.state.value.username)
        assertQuestionDialog("放下问题并退出")
        compose.activityRule.scenario.recreate()
        assertQuestionDialog("放下问题并退出")
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(Audience.ADULT, model().controller.state.value.audience)
        assertEquals(username, model().controller.state.value.username)
        screenshot("p10-chat-logout-confirm", "问题还没发送")
        compose.onNodeWithText("继续提问").performClick()
        chatTab(Audience.ADULT).assertIsSelected()
        input().assertTextContains(question)
        assertNoSends()
        journalTab(Audience.ADULT).performClick()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithText("放下问题并退出").performClick()
        compose.waitUntil(5000) { model().controller.state.value.username == null && !model().controller.state.value.authBusy }
        assertNull(model().controller.state.value.audience)
        assertEquals("", model().controller.state.value.draft)
        compose.onNodeWithText("登录并开始旅行").assertExists()
        assertNoSends()
    }

    @Test fun combinedDraftsUseOneConfirmationAndCancelKeepsBothInTheJournal() {
        enter(Audience.ADULT)
        journalTab(Audience.ADULT).performClick()
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo().performTextInput(observation)
        hideKeyboard()
        val draftId = model().observations.state.value.draftId
        val progress = model().progress.state.value.progress
        chatTab(Audience.ADULT).performClick()
        input().performTextInput(question)
        hideKeyboard()
        switchMode().performClick()
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(observation, model().observations.state.value.note)
        compose.onAllNodesWithText("放下草稿？").assertCountEquals(1)
        compose.onNodeWithText("问题还没发送").assertDoesNotExist()
        assertCompleteText(hasText("放下草稿？"))
        assertCompleteText(hasText("未发送的问题", substring = true))
        assertCompleteText(hasText("继续记录"))
        assertCompleteText(hasText("放下草稿并切换"))
        screenshot("p10-combined-confirm", "放下草稿？")
        compose.onNodeWithText("继续记录").performClick()
        journalTab(Audience.ADULT).assertIsSelected()
        assertEquals(draftId, model().observations.state.value.draftId)
        assertEquals(observation, model().observations.state.value.note)
        assertEquals(question, model().controller.state.value.draft)
        assertEquals(progress, model().progress.state.value.progress)
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo().assertTextContains(observation)
        assertFalse(model().observations.state.value.confirmDiscard)
        assertTrue(runBlocking { SqliteLocalObservationStore(InstrumentationRegistry.getInstrumentation().targetContext).use {
            it.load(key(Audience.ADULT)).isEmpty()
        } })
        chatTab(Audience.ADULT).performClick()
        input().assertTextContains(question)
        switchMode().performClick()
        compose.onNodeWithText("放下草稿并切换").performClick()
        assertNull(model().controller.state.value.audience)
        assertEquals("", model().controller.state.value.draft)
        assertFalse(model().observations.state.value.editing)
        assertEquals("", model().observations.state.value.note)
        assertNoSends()
    }

    @Test fun regularTabsKeepTheQuestionAndBlankIdentityExitsNeedNoConfirmation() {
        enter(Audience.ADULT)
        chatTab(Audience.ADULT).performClick()
        input().performTextInput(question)
        hideKeyboard()
        journalTab(Audience.ADULT).performClick().assertIsSelected()
        compose.onNodeWithText("问题还没发送").assertDoesNotExist()
        compose.onNodeWithText("放下草稿？").assertDoesNotExist()
        assertEquals(question, model().controller.state.value.draft)
        tab("故事").performClick().assertIsSelected()
        assertEquals(question, model().controller.state.value.draft)
        chatTab(Audience.ADULT).performClick().assertIsSelected()
        input().assertTextContains(question)
        compose.runOnUiThread { model().controller.draft(" \n  ") }
        switchMode().performClick()
        assertNull(model().controller.state.value.audience)
        compose.onNodeWithText("问题还没发送").assertDoesNotExist()
        compose.onNodeWithText("放下草稿？").assertDoesNotExist()
        compose.onNodeWithText("今天，怎样出发？").assertIsDisplayed()
        enter(Audience.ADULT)
        assertEquals("", model().controller.state.value.draft)
        journalTab(Audience.ADULT).performClick()
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.waitUntil(5000) { model().controller.state.value.username == null && !model().controller.state.value.authBusy }
        compose.onNodeWithText("问题还没发送").assertDoesNotExist()
        compose.onNodeWithText("放下草稿？").assertDoesNotExist()
        assertNoSends()
    }
}
