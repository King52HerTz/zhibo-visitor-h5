package cn.xiangyu.museum

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
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.restoring }
        compose.runOnUiThread {
            ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.authenticate(
                server.endpoint, "TEST_ONLY_USER", "TEST_ONLY_PASSWORD", false)
        }
        compose.waitUntil(5000) { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.username != null }
    }

    @After fun logoutAndCloseFixture() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
        kotlinx.coroutines.runBlocking { SecureTokenStore(compose.activity).clear() }
        server.close()
    }

    @Test fun stickerNavigationSwitchesTabsAndRestoresSelection() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNode(hasText("探索") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("问阿帛") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("AI 需要联网核验资料；阅读首章不需要联网。").assertExists()
        compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("我的手帐") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("我的旅行手帐").assertExists()
        compose.onNode(hasText("探索") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("今天，寄一封信给汉代").assertExists()
    }

    @Test fun childCanReadOfflineChapterAndSeeHonestAiState() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("今天，寄一封信给汉代").assertExists()
        compose.onNodeWithText("开始探索").performScrollTo().performClick()
        compose.onNodeWithText("直裾素纱单衣").assertExists()
        compose.onNodeWithText("问阿帛 · 本章").performScrollTo().performClick()
        compose.onNodeWithText("AI 需要联网核验资料；阅读首章不需要联网。").assertExists()
    }

    @Test fun adultModeUsesDifferentStoryAndCanSwitch() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNodeWithText("从一件衣服，走进汉代生活").assertExists()
        compose.onNodeWithText("开始阅读").performScrollTo().performClick()
        compose.onNodeWithText("成人模式 · 切换模式").performScrollTo().performClick()
        compose.onNodeWithText("你想怎样开始这段旅行？").assertExists()
    }

    @Test fun loginFormGatesModeSelectionAndUsesExistingTheme() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.logout() }
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
        compose.onNodeWithText("登录并开始旅行").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("密码")).performScrollTo().performTextInput("TEST_ONLY_PASSWORD")
        compose.onNodeWithText("显示").performClick()
        compose.onNodeWithText("隐藏").assertExists()
        compose.onNodeWithText("注册", substring = false).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("注册并开始旅行").assertIsNotEnabled()
        compose.onNodeWithText("连接设置").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertExists()
        compose.onNodeWithText("收起连接设置").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("服务地址")).assertDoesNotExist()
        compose.onNodeWithText("登录", substring = false).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("登录并开始旅行").assertIsNotEnabled()
    }

    @Test fun chatShowsStreamReferencesAndClarificationWithoutResendingOnRecreate() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNode(hasText("问阿帛") and hasClickAction()).performClick()
        compose.waitUntil(5000) { !ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller.state.value.chatBusy }
        compose.onNode(hasSetTextAction() and hasText("想问阿帛什么？")).performScrollTo().performTextInput("介绍这件衣服")
        compose.onNodeWithText("发送问题").performScrollTo().performClick()
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
}
