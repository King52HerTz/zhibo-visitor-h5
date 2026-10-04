package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatWelcomeTest {
    @get:Rule val compose = createComposeRule()

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

    @Test fun changingExhibitsOffersRelevantQuestionsWithoutSendingThem() {
        var sends = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD))
        val silk = FirstChapter.relics.first()
        val robe = FirstChapter.relics.last()
        compose.setContent { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, {}, {}, {}, {
                state = state.copy(exhibitId = it, session = null, messages = emptyList(), draft = "")
            }, Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNode(hasText(silk.title) and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.onNode(hasText("主题问答") and hasClickAction()).assertIsNotSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("纹样里藏着什么？").assertDoesNotExist()
        compose.onNodeWithText("纱衣为什么这么轻？").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("直裾素纱单衣为什么这么轻？")
        compose.runOnIdle { assertEquals(0, sends); assertTrue(state.messages.isEmpty()) }
        screenshot("p7-welcome-silk")

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNode(hasText(robe.title) and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("", state.draft) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("纱衣为什么这么轻？").assertDoesNotExist()
        compose.onNodeWithText("纹样里藏着什么？").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("印花敷彩纱丝绵袍的纹样有哪些特点？")
        compose.runOnIdle { assertEquals(0, sends); assertTrue(state.messages.isEmpty()) }
        screenshot("p7-welcome-robe")

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNode(hasText("主题问答") and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("", state.draft) }
        compose.onNodeWithText("比较两件衣服").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("这两件衣服的材料和工艺有什么不同？")
        compose.runOnIdle { assertEquals(0, sends) }
        screenshot("p7-welcome-theme")
    }

    @Test fun busyAndRefreshRequiredKeepQuickQuestionsFromChangingTheDraft() {
        var sends = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            exhibitId = FirstChapter.relics.first().id, draft = "保留原来的问题"))
        compose.setContent { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, {}, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        listOf(true to false, false to true).forEach { (busy, refresh) ->
            compose.runOnIdle { state = state.copy(chatBusy = busy, needsRefresh = refresh) }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText("纱衣为什么这么轻？").performScrollTo().assertIsDisplayed()
                .assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle {
                assertEquals("保留原来的问题", state.draft)
                assertEquals(0, sends)
            }
        }
    }

    @Test fun longExhibitNameFitsItsTouchableChoiceWithoutEllipsis() {
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD))
        val title = FirstChapter.relics.last().title
        compose.setContent { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, {}, {}, {}, {}, {}, {
                state = state.copy(exhibitId = it, session = null, messages = emptyList(), draft = "")
            }, Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        val choice = compose.onNode(hasText(title) and hasClickAction()).performScrollTo().assertIsDisplayed()
        val item = choice.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
        val layout = layouts.single()
        assertEquals(title, layout.layoutInput.text.text)
        assertFalse("The relic name must not omit text lines", layout.multiParagraph.didExceedMaxLines)
        assertTrue("The relic name must fit its visible choice width", layout.size.width <= item.boundsInRoot.width + 1f)
        assertTrue("The relic name must fit its visible choice height", layout.size.height <= item.boundsInRoot.height + 1f)
        repeat(layout.lineCount) { line ->
            assertFalse("The relic name must remain complete", layout.isLineEllipsized(line))
            assertTrue("The relic name must stay within the text width",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("The relic name must stay within the text height",
                layout.getLineTop(line) >= -1f && layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        choice.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick().assertIsSelected()
        screenshot("p7-welcome-long-choice")
    }
}
