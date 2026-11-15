package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

class MuseumNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: TestMuseumServer

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @Before fun loginUsingTestService() {
        server = TestMuseumServer()
        compose.runOnUiThread {
            ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout()
        }
        compose.waitUntil(5000) {
            val state = ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value
            !state.restoring && !state.authBusy
        }
        compose.runOnUiThread {
            ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.authenticate(
                server.endpoint, "TEST_ONLY_USER", "TEST_ONLY_PASSWORD", false)
        }
        compose.waitUntil(5000) { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.username != null }
        compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun logoutAndCloseFixture() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.authBusy }
        server.close()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test fun childModeOpensExhibitsAndRestoresSelectedTab() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        listOf("展品", "故事", "对话", "游戏", "我的").forEach { tab(it).assertIsDisplayed() }
        tab("展品").assertIsSelected()
        screenshot("zhibo-child-exhibits.png")
        tab("故事").performClick().assertIsSelected()
        compose.onNodeWithText("汉代的一天").assertIsDisplayed()
        screenshot("zhibo-child-stories.png")
        tab("对话").performClick().assertIsSelected()
        compose.onNodeWithText("问文物").assertIsDisplayed()
        tab("游戏").performClick().assertIsSelected()
        compose.onNodeWithText("帛画寻宝").assertIsDisplayed()
        screenshot("zhibo-child-games.png")
        tab("我的").performClick()
        compose.activityRule.scenario.recreate()
        tab("我的").assertIsSelected()
        compose.onNodeWithText("小小观展员").assertIsDisplayed()
        screenshot("zhibo-child-mine.png")
    }

    @Test fun childGameAndCollectionPersistWithinTheAccount() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("看看细节").performScrollTo().performClick()
        compose.onNodeWithText("收藏展品").performScrollTo().performClick()
        tab("游戏").performClick()
        compose.onNodeWithText("开始找").performScrollTo().performClick()
        compose.onNodeWithText("画面右上").performScrollTo().performClick()
        compose.onNodeWithText("这里用圆形画出太阳。", substring = true).assertExists()
        tab("我的").performClick()
        compose.onNodeWithText("收藏文物").performScrollTo().performClick()
        compose.onNodeWithText("直裾素纱单衣").assertExists()
        compose.onNodeWithText("游戏进度").performScrollTo().performClick()
        compose.onNodeWithText("帛画寻宝 · 已完成").assertExists()
    }

    @Test fun adultModeOpensRelationshipTimelineAndSwitchesModes() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        listOf("展品", "图解", "对话", "年表", "我的").forEach { tab(it).assertIsDisplayed() }
        tab("展品").assertIsSelected()
        screenshot("zhibo-adult-exhibits.png")
        tab("图解").performClick().assertIsSelected()
        compose.onNodeWithText("历史图解").assertIsDisplayed()
        screenshot("zhibo-adult-graph.png")
        compose.onNodeWithText("问这件文物").performScrollTo().performClick()
        tab("对话").assertIsSelected()
        compose.onNodeWithText("T形帛画").assertIsDisplayed()
        screenshot("zhibo-adult-chat.png")
        tab("年表").performClick().assertIsSelected()
        compose.onNodeWithText("历史年表").assertIsDisplayed()
        screenshot("zhibo-adult-timeline.png")
        tab("我的").performClick()
        screenshot("zhibo-adult-mine.png")
        compose.onNodeWithText("切换导览模式", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("选择导览模式").assertExists()
        screenshot("zhibo-mode.png")
    }

    @Test fun loginFormAuthenticatesWithConfiguredKnowledgeService() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.authBusy }
        compose.onNodeWithText("儿童模式").assertDoesNotExist()
        screenshot("zhibo-login.png")
        compose.onNode(hasSetTextAction() and hasText("账号")).performScrollTo().performTextInput("TEST_UI_USER")
        compose.onNode(hasSetTextAction() and hasText("密码")).performScrollTo().performTextInput("TEST_ONLY_PASSWORD")
        compose.onNode(hasText("登录") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.username == "TEST_UI_USER" }
        compose.onNodeWithText("选择导览模式").assertExists()
    }

    @Test fun registerActionClearsPasswordAndConnectionSettingsExpand() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.authBusy }
        compose.onNode(hasText("登录") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("知识库服务地址")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("密码")).performScrollTo().performTextInput("TEST_ONLY_PASSWORD")
        compose.onNodeWithText("显示").performClick()
        compose.onNodeWithText("隐藏").assertExists()
        compose.onNode(hasText("注册账号") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("注册") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("连接设置") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("知识库服务地址")).assertExists()
        compose.onNodeWithText("收起连接设置").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("知识库服务地址")).assertDoesNotExist()
        compose.onNodeWithText("返回登录").performScrollTo().performClick()
        compose.onNode(hasText("登录") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun chatShowsStreamReferencesAndClarificationWithoutResendingOnRecreate() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNode(hasText("对话") and hasClickAction()).performClick()
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.chatBusy }
        compose.onNode(hasSetTextAction() and hasText("想问阿帛什么？")).assertIsDisplayed().performTextInput("介绍这件衣服")
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            val state = ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value
            !state.chatBusy && state.messages.any { it.options.isNotEmpty() }
        }
        compose.onNodeWithText("请选一个测试方向").assertExists()
        compose.onNodeWithText("展开引用 · 1 条").performScrollTo().performClick()
        compose.onNodeWithText("测试依据").assertExists()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true) {
            org.junit.Assert.assertTrue(instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(5000) {
                androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == false
            }
        }
        compose.onNodeWithText("测试依据").performScrollTo().assertIsDisplayed()
        screenshot("chat-main-references.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("请选一个测试方向").assertExists()
        org.junit.Assert.assertEquals(1, server.requests.count { it.first == "/mobile/chat/stream" })
    }

    @Test fun keyboardSendProducesOneStreamRequestAndBlankDraftDoesNotResend() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNode(hasText("对话") and hasClickAction()).performClick()
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.chatBusy }
        compose.onNode(hasSetTextAction()).performTextInput("键盘发送的界面测试问题")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) {
            val state = ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value
            !state.chatBusy && state.messages.any { it.options.isNotEmpty() }
        }
        compose.onNodeWithText("键盘发送的界面测试问题").assertExists()
        compose.onNodeWithContentDescription("发送问题").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(1, server.requests.count { it.first == "/mobile/chat/stream" })
    }

    @Test fun chatInputRemainsVisibleAboveTheRealKeyboard() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNode(hasText("对话") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        val prompt = compose.onNodeWithText("比较两件衣服", useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        prompt.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
            org.junit.Assert.assertTrue(action(layouts))
        }
        org.junit.Assert.assertFalse("第一个示例问题不能溢出", layouts.single().hasVisualOverflow)
        org.junit.Assert.assertTrue("第一个示例问题应完整出现在消息视口内",
            layouts.single().size.height <= prompt.fetchSemanticsNode().boundsInRoot.height + 1f)
        screenshot("chat-main-empty.png")
        compose.onNode(hasSetTextAction()).performClick().performTextInput("这件衣服有什么线索？")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasText("对话") and hasClickAction()).assertDoesNotExist()
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        screenshot("chat-real-keyboard.png")
    }

    @Test fun landscapeChatKeepsInputVisibleWithTheKeyboard() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNode(hasText("对话") and hasClickAction()).performClick()
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.onNode(hasSetTextAction()).performClick().performTextInput("材料有哪些线索？")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        screenshot("chat-landscape-keyboard.png")
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        org.junit.Assert.assertTrue(instrumentation.uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == false
        }
        try {
            compose.waitUntil(5000) { runCatching {
                compose.onNodeWithText("历史会话").assertIsDisplayed()
                compose.onNodeWithText("新对话").assertIsDisplayed()
                compose.onNode(hasText("对话") and hasClickAction()).assertIsDisplayed()
                true
            }.getOrDefault(false) }
        } finally {
            screenshot("chat-landscape-closed.png")
        }
    }
}
