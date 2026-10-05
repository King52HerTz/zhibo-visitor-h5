package cn.xiangyu.museum

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private var view: View? = null

    @Composable private fun CaptureView() {
        val currentView = LocalView.current
        SideEffect { view = currentView }
    }

    private fun keyboardVisible() = view?.let {
        ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
    } == true

    private fun hideKeyboard() {
        if (keyboardVisible()) {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(5000) { !keyboardVisible() }
        }
    }

    private fun assertDisabledInput(draft: String) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
            .assertIsDisplayed().assertIsNotEnabled().assertTextContains(draft)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), "$name-${context.resources.configuration.fontScale}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun assertCompleteText(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
        val layout = layouts.single()
        assertEquals(text, layout.layoutInput.text.text)
        assertFalse("Feedback text must not omit lines", layout.multiParagraph.didExceedMaxLines)
        assertTrue("Feedback text must fit its visible width", layout.size.width <= bounds.width + 1f)
        assertTrue("Feedback text must be fully readable after scrolling", layout.size.height <= bounds.height + 1f)
        repeat(layout.lineCount) { line ->
            assertFalse("Feedback text must remain complete", layout.isLineEllipsized(line))
            assertTrue("Feedback lines must stay within their width",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("Feedback lines must stay within their height",
                layout.getLineTop(line) >= -1f && layout.getLineBottom(line) <= layout.size.height + 1f)
        }
    }

    @Test fun processingKeepsTheStatusReadableAndStopsWithoutSending() {
        val status = "正在连接阿帛"
        val question = "这件衣服为什么这么轻？"
        var sends = 0
        var stops = 0
        val state = MuseumState(restoring = false, audience = Audience.CHILD, draft = "保留的测试草稿",
            chatBusy = true, status = status, messages = listOf(ChatLine(true, question)))
        compose.setContent { MuseumTheme {
            CaptureView()
            ChatContent(state, {}, { sends++ }, { stops++ }, {}, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }

        hideKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        assertCompleteText(status)
        compose.onNodeWithText(status, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        screenshot("p9-processing")
        assertDisabledInput(state.draft)
        compose.onNodeWithContentDescription("停止回复").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("停止回复").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(question).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, stops); assertEquals(0, sends) }
    }

    @Test fun reviewRequiredPreservesMessagesAndDraftAndReloadsOnceWithoutSending() {
        val status = "已停止显示；服务器可能仍在处理，请刷新会话再继续"
        val question = "请介绍这件衣服的材料。"
        val partial = "我们可以先观察这件衣服的材料。"
        val draft = "保留的原问题"
        var sends = 0
        var reloads = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            draft = draft, status = status, needsRefresh = true,
            messages = listOf(ChatLine(true, question), ChatLine(false, partial,
                options = listOf("继续看材料"), incomplete = true))))
        compose.setContent { MuseumTheme {
            CaptureView()
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, {
                reloads++
                state = state.copy(chatBusy = true, status = "正在读取会话", error = null)
            }, {}, {}, {}, Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }

        compose.onNodeWithContentDescription("发送问题").assertIsNotEnabled().performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled().assertTextContains(draft).performImeAction()
        compose.waitUntil(5000) { keyboardVisible() }
        hideKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(question).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText(partial).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("回复未完成").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("继续看材料").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        assertCompleteText(status)
        assertCompleteText("问题已保留，请先刷新核对记录。")
        assertCompleteText("刷新会话")
        compose.onNodeWithText("刷新会话").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("p9-review-needed")
        compose.onNodeWithText("刷新会话").performClick()

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        assertCompleteText("正在读取会话")
        compose.onNodeWithText("正在读取会话", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("刷新会话").performScrollTo().assertIsNotEnabled().performClick()
        screenshot("p9-review-loading")
        assertDisabledInput(draft)
        compose.runOnIdle {
            assertEquals(1, reloads)
            assertEquals(0, sends)
            state = state.copy(chatBusy = false, status = "", needsRefresh = false)
        }
        compose.onNodeWithText(status).assertDoesNotExist()
        compose.onNodeWithText("正在读取会话").assertDoesNotExist()
        compose.onNodeWithText("刷新会话").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled().assertTextContains(draft)
        compose.onNodeWithContentDescription("发送问题").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(draft, state.draft)
            assertEquals(listOf(question, partial), state.messages.map { it.text })
            assertEquals(1, reloads)
            assertEquals(0, sends)
        }
    }

    @Test fun ordinaryErrorKeepsTheDraftEditableAndDoesNotRequireReviewOrSend() {
        val error = "暂时无法读取会话。当前已显示的消息可以继续阅读，请稍后再试。"
        val reply = "这是一段保留的演示回复。"
        var sends = 0
        var reloads = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.ADULT,
            error = error, draft = "准备继续观察", messages = listOf(ChatLine(false, reply))))
        compose.setContent { MuseumTheme {
            CaptureView()
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, { reloads++ }, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        assertCompleteText(error)
        compose.onNodeWithText("刷新会话").assertDoesNotExist()
        compose.onNodeWithText("问题已保留，请先刷新核对记录。").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled().assertTextContains("准备继续观察")
        compose.onNodeWithContentDescription("发送问题").assertIsEnabled()
        screenshot("p9-ordinary-error")
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement("修改后的观察问题")
        compose.waitUntil(5000) { keyboardVisible() }
        hideKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(reply).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("修改后的观察问题", state.draft)
            assertEquals(error, state.error)
            assertEquals(listOf(reply), state.messages.map { it.text })
            assertEquals(0, sends)
            assertEquals(0, reloads)
        }
    }

    @Test fun reviewActionRemainsReachableAboveTheRealKeyboardWithoutSending() {
        var sends = 0
        var reloads = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            status = "请先核对会话记录", needsRefresh = true,
            messages = listOf(ChatLine(false, "当前显示的演示回复仍可阅读。", incomplete = true))))
        compose.setContent { MuseumTheme {
            CaptureView()
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, { reloads++ }, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        try {
            compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled().performClick().performTextInput("保留输入")
            compose.waitUntil(5000) { keyboardVisible() }
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.onNodeWithContentDescription("发送问题").assertIsNotEnabled()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
            val button = compose.onNode(hasText("刷新会话") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled()
            val buttonNode = button.fetchSemanticsNode()
            assertTrue("The review action must fit above the keyboard", buttonNode.size.height <= buttonNode.boundsInRoot.height + 1f)
            compose.onNode(hasSetTextAction()).assertIsDisplayed().assertTextContains("保留输入")
            assertTrue("The screenshot must show a real open keyboard", keyboardVisible())
            screenshot("p9-review-keyboard")
            button.performClick()
            compose.runOnIdle {
                assertEquals("保留输入", state.draft)
                assertEquals(1, reloads)
                assertEquals(0, sends)
            }
        } finally {
            hideKeyboard()
        }
    }
}
