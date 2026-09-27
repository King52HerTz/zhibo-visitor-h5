package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ProgressJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun shot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
            }
        }
    }

    @Test fun sqlitePersistsPendingTasksAndSeparatesAccountModeAndService() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-progress-${UUID.randomUUID()}.db"
        val key = ProgressKey("https://example.invalid", "TEST_USER_A", Audience.CHILD)
        try {
            SqliteProgressStore(context, name).use { store ->
                store.complete(key, "clothes-light-v1")
                store.complete(key, "clothes-light-v1")
            }
            SqliteProgressStore(context, name).use { store ->
                assertEquals(setOf("clothes-light-v1"), store.load(key).pending)
                assertTrue(store.load(key.copy(username = "TEST_USER_B")).completed.isEmpty())
                assertTrue(store.load(key.copy(audience = Audience.ADULT)).completed.isEmpty())
                assertTrue(store.load(key.copy(endpoint = "https://other.invalid")).completed.isEmpty())
                store.merge(key, setOf("clothes-pattern-v1"))
                assertEquals(2, store.load(key).completed.size)
                assertEquals(1, store.load(key).pending.size)
                store.merge(key, setOf("clothes-light-v1"))
                assertTrue(store.load(key).pending.isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun offlineObservationJourneyRestoresAndSynchronizesWithVisualCapture() {
        TestMuseumServer().use { server ->
            server.progressOffline = true
            compose.runOnUiThread { model().controller.logout() }
            compose.waitUntil(5000) { !model().controller.state.value.authBusy }
            val username = "TEST_P3_" + UUID.randomUUID().toString()
            try {
                compose.runOnUiThread { model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
                compose.waitUntil(5000) { model().controller.state.value.username != null }
                compose.onNodeWithText("成人模式").performScrollTo().performClick()
                compose.waitUntil(5000) { !model().progress.state.value.loading && !model().progress.state.value.syncing }
                compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
                shot("p3-journal-locked.png")
                compose.onNodeWithText("继续探索衣服里的秘密").performScrollTo().performClick()
                compose.onNodeWithText("观察任务 · 发现轻与薄").performScrollTo().performClick()
                shot("p3-observation.png")
                compose.onNodeWithText("厚重的金属").performScrollTo().performClick()
                compose.onNodeWithText("再看看上面的故事，线索就在衣料和纹样里。可以重新选择。").assertExists()
                compose.onNodeWithText("轻薄的丝纱").performScrollTo().performClick()
                compose.onNodeWithText("把发现收进手帐").performScrollTo().performClick()
                compose.waitUntil(5000) { model().progress.state.value.progress.completed.size == 1 }
                compose.onNodeWithText("返回故事").performScrollTo().performClick()
                compose.onNodeWithText("观察任务 · 寻找花叶线索").performScrollTo().performClick()
                compose.onNodeWithText("植物的枝叶和花").performScrollTo().performClick()
                compose.onNodeWithText("把发现收进手帐").performScrollTo().performClick()
                compose.waitUntil(5000) { model().progress.state.value.progress.completed.size == 2 }
                compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
                compose.onNodeWithText("首章纪念页 · 已获得").assertExists()
                shot("p3-journal-earned.png")
                compose.activityRule.scenario.recreate()
                compose.onNodeWithText("首章纪念页 · 已获得").assertExists()
                assertEquals(2, model().progress.state.value.progress.pending.size)
                server.progressOffline = false
                compose.runOnUiThread { model().progress.sync() }
                compose.waitUntil(5000) { model().progress.state.value.progress.pending.isEmpty() && !model().progress.state.value.syncing }
                compose.runOnUiThread { model().controller.selectAudience(Audience.CHILD) }
                compose.waitUntil(5000) { !model().progress.state.value.loading && !model().progress.state.value.syncing }
                assertTrue(model().progress.state.value.progress.completed.isEmpty())
                shot("p3-journal-adult.png")
            } finally {
                compose.runOnUiThread { model().controller.logout() }
                compose.waitUntil(5000) { !model().controller.state.value.authBusy }
                // Only this fixture user's local progress; not the user's real app records.
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("museum_progress.db").path,
                    null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                    it.delete("tasks", "endpoint=? AND username=?", arrayOf(server.endpoint, username))
                }
            }
        }
    }
}
