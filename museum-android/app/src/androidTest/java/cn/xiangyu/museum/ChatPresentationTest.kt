package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.Assert.*

class ChatPresentationTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun childChatEmptyStateRemainsUsable() {
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD))
        compose.setContent {
            MuseumTheme {
                ChatContent(state, { state = state.copy(draft = it) }, {}, {}, {}, {}, {}, {},
                    Modifier.fillMaxSize().systemBarsPadding().imePadding())
            }
        }
        screenshot("chat-after-empty.png")
        compose.onNodeWithText("比较两件衣服").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("这两件衣服的材料和工艺有什么不同？")
    }

    @Test fun composerStaysVisibleAndHistoryLivesInItsOwnDialog() {
        var selected: ChatSessionInfo? = null
        var refreshes = 0
        val first = ChatSessionInfo("test-first", "衣服里的秘密", null)
        val second = ChatSessionInfo("test-second", "上一次的文物旅行", null)
        val state = MuseumState(restoring = false, audience = Audience.ADULT, draft = "还有什么线索？",
            session = first, sessions = listOf(first, second), messages = List(12) { index ->
                ChatLine(index % 2 == 0, "第 ${index + 1} 条界面测试记录。" + "这段示例用于检查长对话的阅读排版与固定输入区。".repeat(4))
            })
        compose.setContent { MuseumTheme {
            ChatContent(state, {}, {}, {}, { refreshes++ }, {}, { selected = it }, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed()
        screenshot("chat-long-history.png")
        compose.onNodeWithText("历史会话").performClick()
        compose.onNodeWithText(second.title).performClick()
        assertEquals(second, selected)
        compose.onNodeWithText("历史会话").performClick()
        compose.onNodeWithText("刷新会话").performClick()
        assertEquals(1, refreshes)
    }

    @Test fun referencesRemainReadableAndRefreshRequiredPreventsDuplicateSend() {
        var sends = 0
        var refreshes = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            messages = listOf(ChatLine(true, "这件衣服有什么线索？"),
                ChatLine(false, "我们可以先观察材料，再看看工艺。\n这是一段排版测试文本。",
                    evidence = listOf(Evidence("演示资料", "这段来源摘录仅用于界面测试。", null)), options = listOf("继续看材料")))))
        compose.setContent { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, { refreshes++ }, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        compose.onNodeWithText("展开引用 · 1 条").performScrollTo().performClick()
        compose.onNodeWithText("这段来源摘录仅用于界面测试。").assertExists()
        compose.onNodeWithText("问阿帛").assertIsDisplayed()
        screenshot("chat-references.png")
        compose.runOnIdle { state = state.copy(needsRefresh = true, draft = "保留的问题", status = "已停止显示；服务器可能仍在处理，请刷新会话再继续") }
        compose.onNodeWithContentDescription("发送问题").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("继续看材料").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText("刷新会话").assertIsDisplayed().performClick()
        assertEquals(0, sends)
        assertEquals(1, refreshes)
        screenshot("chat-needs-refresh.png")
    }

    @Test fun incomingReplyDoesNotMoveTheReaderAwayFromEarlierMessages() {
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            messages = List(15) { index -> ChatLine(false, "第 $index 条旅行记录。" + "这是检查阅读位置的示例。".repeat(4)) }))
        compose.setContent { MuseumTheme {
            ChatContent(state, {}, {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("回到最新回复").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(messages = state.messages + ChatLine(false, "这是一条新到达的界面测试回复。")) }
        compose.onNodeWithText("回到最新回复").assertIsDisplayed().performClick()
        compose.onNodeWithText("这是一条新到达的界面测试回复。").assertIsDisplayed()
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed()
    }
}
