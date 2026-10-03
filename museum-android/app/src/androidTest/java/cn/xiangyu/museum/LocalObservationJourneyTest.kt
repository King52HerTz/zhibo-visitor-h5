package cn.xiangyu.museum

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocalObservationJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: TestMuseumServer
    private val username = "P4_UI_${System.nanoTime()}"
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun key(audience: Audience) = ProgressKey(server.endpoint, username, audience)

    @Before fun login() {
        server = TestMuseumServer()
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy && !model().controller.state.value.restoring }
        compose.runOnUiThread {
            model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false)
        }
        compose.waitUntil(5000) { model().controller.state.value.username == username }
        compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        hideKeyboard()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val suffix = instrumentation.targetContext.resources.configuration.fontScale
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name-$suffix.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            SqliteLocalObservationStore(context).use { store ->
                Audience.entries.forEach { audience -> store.load(key(audience)).forEach { store.delete(key(audience), it.id) } }
            }
        }
        server.close()
    }

    @Test fun textObservationPersistsAndModesStaySeparateWithoutUnlockingTasks() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").performClick()
        compose.waitUntil(5000) { !model().progress.state.value.loading && !model().observations.state.value.loading }
        val before = model().progress.state.value.progress
        compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
        compose.onNodeWithText("我的观察记录").assertExists()
        shot("p4-main-empty")
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        compose.onNodeWithText("确认收进手帐").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("只做观察").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo()
            .performTextInput("测试观察：我注意到轻薄的丝纱。")
        hideKeyboard()
        compose.onNodeWithText("返回观察记录").performScrollTo().performClick()
        compose.onNodeWithText("放下这次观察？").assertIsDisplayed()
        compose.onNodeWithText("继续记录").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains("测试观察：我注意到轻薄的丝纱。")
        shot("p4-main-editor")
        compose.onNodeWithText("确认收进手帐").performScrollTo().performClick()
        compose.waitUntil(5000) { !model().observations.state.value.editing && model().observations.state.value.records.size == 1 }
        compose.onNodeWithText("测试观察：我注意到轻薄的丝纱。").performScrollTo().assertIsDisplayed()
        shot("p4-main-saved")
        assertEquals(before, model().progress.state.value.progress)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("测试观察：我注意到轻薄的丝纱。").assertExists()
        compose.onNodeWithText("儿童模式 · 切换模式").performScrollTo().performClick()
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        compose.waitUntil(5000) { !model().observations.state.value.loading }
        compose.onNodeWithText("这一页，等你的发现").assertExists()
        compose.onNodeWithText("测试观察：我注意到轻薄的丝纱。").assertDoesNotExist()
        shot("p4-main-adult-empty")
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        compose.onNodeWithText("纹样让我想到植物的枝叶和花。").assertDoesNotExist()
        compose.onNodeWithText(FirstChapter.relics.last().title).performScrollTo().performClick()
        compose.onNodeWithText("纹样让我想到植物的枝叶和花。").performScrollTo().performClick()
        compose.onNodeWithText("确认收进手帐").performScrollTo().performClick()
        compose.waitUntil(5000) { !model().observations.state.value.editing && model().observations.state.value.records.size == 1 }
        assertEquals(FirstChapter.relics.last().id, model().observations.state.value.records.single().relicId)
        assertTrue(model().progress.state.value.progress.completed.isEmpty())
        compose.onNodeWithText("删除记录").performScrollTo().performClick()
        compose.onNodeWithText("保留记录").performClick()
        assertEquals(1, model().observations.state.value.records.size)
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5000) { !model().observations.state.value.busy && model().observations.state.value.records.isEmpty() }
        runBlocking {
            SqliteLocalObservationStore(InstrumentationRegistry.getInstrumentation().targetContext).use {
                assertEquals(1, it.load(key(Audience.CHILD)).size)
                assertTrue(it.load(key(Audience.ADULT)).isEmpty())
            }
        }
    }

    @Test fun systemCameraCancellationReturnsToTheSameTextDraft() {
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        compose.waitUntil(5000) { !model().observations.state.value.loading }
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo()
            .performTextInput("测试观察：取消拍照后继续记录。")
        hideKeyboard()
        compose.onNodeWithText("允许拍照时拍一张").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(5000) {
            automation.rootInActiveWindow?.packageName?.toString() in setOf("com.android.camera2", "com.android.permissioncontroller")
        }
        assertNotNull(model().observations.pendingCaptureId())
        repeat(3) {
            if (automation.rootInActiveWindow?.packageName?.toString() != "cn.xiangyu.museum") {
                automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
                android.os.SystemClock.sleep(300)
            }
        }
        compose.waitUntil(5000) { model().observations.pendingCaptureId() == null }
        compose.onNode(hasSetTextAction() and hasText("我的发现"))
            .assertTextContains("测试观察：取消拍照后继续记录。")
        assertNull(model().observations.state.value.photo)
        assertTrue(model().observations.state.value.records.isEmpty())
        compose.onNodeWithText("确认收进手帐").performScrollTo().performClick()
        compose.waitUntil(5000) { model().observations.state.value.records.size == 1 }
        assertNull(model().observations.state.value.records.single().photoName)
    }
}
