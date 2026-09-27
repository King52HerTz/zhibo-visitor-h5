package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkshopIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @Test fun childGameUnlocksJournalAndRestoresAfterRecreation() {
        TestMuseumServer().use { server ->
            server.progressOffline = true
            val username = "TEST_GAME_" + UUID.randomUUID().toString()
            compose.runOnUiThread { model().controller.logout() }
            compose.waitUntil(5000) { !model().controller.state.value.authBusy }
            try {
                compose.runOnUiThread { model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
                compose.waitUntil(5000) { model().controller.state.value.username != null }
                compose.onNodeWithText("儿童模式").performScrollTo().performClick()
                compose.waitUntil(5000) { !model().progress.state.value.loading }
                compose.onNodeWithText("开始探索").performScrollTo().performClick()
                compose.onNodeWithText("阿帛的汉代衣坊").performScrollTo().assertIsDisplayed()
                shot("workshop-app-scene.png")
                compose.onNodeWithText("衣服", substring = false).performScrollTo().performClick()
                compose.onNodeWithText("掀开衣料").performScrollTo().performClick()
                compose.onNodeWithText("把发现收进手帐").performScrollTo().performClick()
                compose.waitUntil(5000) { model().progress.state.value.progress.completed.size == 1 }
                compose.onNodeWithText("返回故事").performScrollTo().performClick()
                compose.onNodeWithText("花叶", substring = false).performScrollTo().performClick()
                compose.onNodeWithText("点一下放入花叶").performScrollTo().performClick()
                compose.onNodeWithText("把发现收进手帐").performScrollTo().performClick()
                compose.waitUntil(5000) { model().progress.state.value.progress.completed.size == 2 }
                shot("workshop-app-flower.png")
                compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
                compose.onNodeWithText("首章纪念页 · 已获得").assertExists()
                compose.activityRule.scenario.recreate()
                compose.onNodeWithText("首章纪念页 · 已获得").assertExists()
                assertEquals(2, model().progress.state.value.progress.pending.size)
                compose.runOnUiThread { model().controller.selectAudience(Audience.ADULT) }
                compose.waitUntil(5000) { !model().progress.state.value.loading }
                assertTrue(model().progress.state.value.progress.completed.isEmpty())
            } finally {
                compose.runOnUiThread { model().controller.logout() }
                compose.waitUntil(5000) { !model().controller.state.value.authBusy }
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("museum_progress.db").path,
                    null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                    it.delete("tasks", "endpoint=? AND username=?", arrayOf(server.endpoint, username))
                }
            }
        }
    }
}
