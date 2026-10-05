package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatEvidenceTest {
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

    @Test fun failedSourceOpeningPreservesTheExcerptAndCanRetryWithoutSending() {
        val url = "https://evidence.example.invalid/source-one"
        val excerpt = "这段来源摘录仅用于验证网页打开失败后仍可阅读。"
        val opened = mutableListOf<String>()
        var unavailable = true
        var sends = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            draft = "保留的问题", messages = listOf(ChatLine(false, "先阅读来源摘录，再继续观察。",
                evidence = listOf(Evidence("演示来源.pdf", excerpt, url))))))
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
                if (unavailable) throw IllegalArgumentException("TEST_ONLY_UNAVAILABLE_BROWSER")
            }
        }
        compose.setContent { CompositionLocalProvider(LocalUriHandler provides handler) { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, {}, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("展开引用 · 1 条").performScrollTo().performClick()
        compose.onNodeWithText("打开来源网页").performScrollTo().performClick()
        compose.onNodeWithText("网页暂时无法打开，引用摘录仍可阅读。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起引用").assertExists()
        compose.onNodeWithText(excerpt).performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("保留的问题")
        compose.runOnIdle { assertEquals(listOf(url), opened); assertEquals("保留的问题", state.draft); assertEquals(0, sends) }
        val notice = compose.onNodeWithText("网页暂时无法打开，引用摘录仍可阅读。", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        val noticeLayouts = mutableListOf<TextLayoutResult>()
        notice.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(noticeLayouts)) }
        val noticeLayout = noticeLayouts.single()
        assertFalse("The source failure notice must not omit lines", noticeLayout.multiParagraph.didExceedMaxLines)
        assertTrue("The full source failure notice must be visible after scrolling",
            noticeLayout.size.height <= notice.fetchSemanticsNode().boundsInRoot.height + 1f)
        screenshot("p8-source-open-failed")

        compose.runOnIdle { unavailable = false }
        compose.onNodeWithText("重试打开来源网页").performScrollTo().performClick()
        compose.onNodeWithText("网页暂时无法打开，引用摘录仍可阅读。").assertDoesNotExist()
        compose.onNodeWithText("重试打开来源网页").assertDoesNotExist()
        compose.onNodeWithText(excerpt).performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("保留的问题")
        compose.runOnIdle { assertEquals("保留的问题", state.draft) }
        compose.onNode(hasSetTextAction()).performTextReplacement("这条观察还想继续问")
        compose.runOnIdle {
            assertEquals(listOf(url, url), opened)
            assertEquals("这条观察还想继续问", state.draft)
            assertEquals(0, sends)
        }
    }

    @Test fun unusableLinksKeepExcerptsAndDoNotOfferOrOpenAWebAction() {
        val invalidUrls = listOf("http://evidence.example.invalid/source", "javascript:alert('TEST_ONLY')",
            "https://", "https://evidence.example.invalid/a b")
        val opened = mutableListOf<String>()
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.ADULT,
            messages = listOf(ChatLine(false, "这些来源只用于链接校验测试。", evidence = invalidUrls.mapIndexed { index, url ->
                Evidence("演示无效来源${index + 1}.pdf", "第 ${index + 1} 段演示摘录仍可阅读。", url)
            }))))
        val handler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        compose.setContent { CompositionLocalProvider(LocalUriHandler provides handler) { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, {}, {}, {}, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("展开引用 · 4 条").performScrollTo().performClick()
        compose.onAllNodesWithText("网页链接不可用，引用摘录仍可阅读。").assertCountEquals(4)
        invalidUrls.indices.forEach { index ->
            compose.onNodeWithText("第 ${index + 1} 段演示摘录仍可阅读。").performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodesWithText("打开来源网页").assertCountEquals(0)
        compose.onAllNodesWithText("重试打开来源网页").assertCountEquals(0)
        compose.runOnIdle { assertTrue(opened.isEmpty()) }

        compose.runOnIdle { state = state.copy(messages = listOf(ChatLine(false, "没有网页的来源保持纯摘录。",
            evidence = listOf(Evidence("演示纯摘录.pdf", "没有网页链接，也可以阅读这段演示摘录。", null))))) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("展开引用 · 1 条").performScrollTo().performClick()
        compose.onNodeWithText("没有网页链接，也可以阅读这段演示摘录。").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("网页链接不可用，引用摘录仍可阅读。").assertCountEquals(0)
        compose.onAllNodesWithText("网页暂时无法打开，引用摘录仍可阅读。").assertCountEquals(0)
        compose.onAllNodesWithText("打开来源网页").assertCountEquals(0)
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        screenshot("p8-source-excerpt-only")
    }

    @Test fun multipleLongSourceTitlesRemainCompleteAndExpansionDoesNotBlockInput() {
        val sources = listOf(
            Evidence("演示资料_汉代衣物材料与织造工艺观察记录_第一份.pdf", "第一份演示摘录，不表示真实馆藏结论。",
                "https://evidence.example.invalid/source-one"),
            Evidence("演示资料_印花与敷彩纹样观察和来源阅读记录_第二份.pdf", "第二份演示摘录，不表示真实馆藏结论。",
                "https://evidence.example.invalid/source-two"))
        val opened = mutableListOf<String>()
        var sends = 0
        var state by mutableStateOf(MuseumState(restoring = false, audience = Audience.CHILD,
            draft = "保留输入内容", messages = listOf(ChatLine(false, "两份演示来源可以分别展开阅读。", evidence = sources))))
        val handler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        compose.setContent { CompositionLocalProvider(LocalUriHandler provides handler) { MuseumTheme {
            ChatContent(state, { state = state.copy(draft = it) }, { sends++ }, {}, {}, {}, {}, {},
                Modifier.fillMaxSize().systemBarsPadding().imePadding())
        } } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("展开引用 · 2 条").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起")).performClick()
        compose.onNodeWithText("收起引用")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithText("资料来源").performScrollTo().assertIsDisplayed()
        sources.forEachIndexed { index, source ->
            val title = "[${index + 1}] ${source.filename}"
            val titleNode = compose.onNodeWithText(title, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val item = titleNode.fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
            val layout = layouts.single()
            assertEquals(title, layout.layoutInput.text.text)
            assertFalse("A source title must not omit text lines", layout.multiParagraph.didExceedMaxLines)
            assertTrue("A source title must fit its visible width", layout.size.width <= item.boundsInRoot.width + 1f)
            assertTrue("A source title must fit its visible height", layout.size.height <= item.boundsInRoot.height + 1f)
            repeat(layout.lineCount) { line ->
                assertFalse("A source title must remain complete", layout.isLineEllipsized(line))
                assertTrue("A source title must stay within the text width",
                    layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("A source title must stay within the text height",
                    layout.getLineTop(line) >= -1f && layout.getLineBottom(line) <= layout.size.height + 1f)
            }
            screenshot("p8-source-long-title-${index + 1}")
            compose.onNodeWithText(source.text).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("打开来源网页")[index].performScrollTo().performClick()
        }
        compose.onNodeWithText("收起引用").performScrollTo().performClick()
        compose.onNodeWithText("展开引用 · 2 条")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        compose.onNodeWithText("资料来源").assertDoesNotExist()
        sources.forEachIndexed { index, source ->
            compose.onNodeWithText("[${index + 1}] ${source.filename}").assertDoesNotExist()
            compose.onNodeWithText(source.text).assertDoesNotExist()
        }
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled().performTextReplacement("展开阅读后继续提问")
        compose.runOnIdle {
            assertEquals(sources.map { it.url }, opened)
            assertEquals("展开阅读后继续提问", state.draft)
            assertEquals(0, sends)
        }
    }
}
