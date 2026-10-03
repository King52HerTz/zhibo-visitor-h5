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

    @Test fun stickerNavigationSwitchesTabsAndRestoresSelection() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").performClick()
        compose.onNode(hasText("探索") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("问阿帛") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("比较两件衣服").assertExists()
        compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("我的手帐") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("我的旅行手帐").assertExists()
        compose.onNode(hasText("探索") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("今天，寄一封信给汉代").assertExists()
    }

    @Test fun childCanReadOfflineChapterAndSeeHonestAiState() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("今天，寄一封信给汉代").assertExists()
        compose.onNodeWithText("文物与观察任务").performScrollTo().performClick()
        compose.onNodeWithText("文物与来源").performScrollTo().performClick()
        compose.onNodeWithText("直裾素纱单衣").assertExists()
        compose.onNodeWithText("问阿帛 · 本章").performScrollTo().performClick()
        compose.onNodeWithText("比较两件衣服").assertExists()
    }

    @Test fun adultModeUsesDifferentStoryAndCanSwitch() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNodeWithText("从一件衣服，走进汉代生活").assertExists()
        compose.onNode(hasText("文物") and hasClickAction()).performClick()
        compose.onNodeWithText("文物档案").assertIsDisplayed()
        compose.onNodeWithText("我的手帐").assertDoesNotExist()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "experience-adult-relics.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
        compose.onNode(hasText("故事") and hasClickAction()).performClick()
        compose.onNodeWithText("开始阅读").performScrollTo().performClick()
        compose.onNodeWithText("成人模式 · 切换模式").performScrollTo().performClick()
        compose.onNodeWithText("今天，怎样出发？").assertExists()
    }

    @Test fun loginFormGatesModeSelectionAndUsesExistingTheme() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.authBusy }
        compose.onNodeWithText("儿童模式").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("账号")).performScrollTo().performTextInput("TEST_UI_USER")
        compose.onNode(hasSetTextAction() and hasText("密码")).performScrollTo().performTextInput("TEST_ONLY_PASSWORD")
        compose.onNodeWithText("登录并开始旅行").performScrollTo().performClick()
        compose.waitUntil(5000) { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.username == "TEST_UI_USER" }
        compose.onNodeWithText("儿童模式").assertExists()
        compose.onNodeWithText("已登录 · TEST_UI_USER").assertExists()
    }

    @Test fun registrationTabsClearPasswordAndConnectionSettingsExpand() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.authBusy }
        compose.onNodeWithText("登录并开始旅行").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("密码")).performScrollTo().performTextInput("TEST_ONLY_PASSWORD")
        compose.onNodeWithText("显示").performClick()
        compose.onNodeWithText("隐藏").assertExists()
        compose.onNodeWithText("注册", substring = false).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("注册并开始旅行").assertIsNotEnabled()
        compose.onNodeWithText("连接设置", substring = true).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertExists()
        compose.onNodeWithText("收起连接设置").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertDoesNotExist()
        compose.onNodeWithText("登录", substring = false).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("登录并开始旅行").assertIsNotEnabled()
    }

    @Test fun chatShowsStreamReferencesAndClarificationWithoutResendingOnRecreate() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").performClick()
        compose.onNode(hasText("问阿帛") and hasClickAction()).performClick()
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
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("请选一个测试方向").assertExists()
        org.junit.Assert.assertEquals(1, server.requests.count { it.first == "/mobile/chat/stream" })
    }

    @Test fun chatInputRemainsVisibleAboveTheRealKeyboard() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").performClick()
        compose.onNode(hasText("问阿帛") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performClick().performTextInput("这件衣服有什么线索？")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasText("问阿帛") and hasClickAction()).assertDoesNotExist()
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "chat-real-keyboard.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @Test fun landscapeChatKeepsInputVisibleWithTheKeyboard() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNode(hasText("讲解") and hasClickAction()).performClick()
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
        File(instrumentation.targetContext.getExternalFilesDir(null), "chat-landscape-keyboard.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("发送问题").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
