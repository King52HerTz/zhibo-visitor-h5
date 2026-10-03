package cn.xiangyu.museum

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
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
import kotlin.math.min

class LandscapePuzzleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun waitFor(label: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(150)
        }
        fail(label)
    }
    private fun shot(name: String): Pair<Int, Int> {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val size = bitmap.width to bitmap.height
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        return size
    }
    private fun touch(x: Float, y: Float, action: Int, down: Long) {
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).let {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(it, true))
            it.recycle()
        }
    }
    private fun contact(point: Pair<Float, Float>, size: Pair<Int, Int>) {
        val scale = min(size.first / 1280f, size.second / 720f)
        val x = (size.first - 1280 * scale) / 2 + point.first * scale
        val y = (size.second - 720 * scale) / 2 + point.second * scale
        val down = SystemClock.uptimeMillis()
        touch(x, y, MotionEvent.ACTION_DOWN, down)
        SystemClock.sleep(120)
        touch(x, y, MotionEvent.ACTION_UP, down)
        SystemClock.sleep(250)
    }
    private fun drag(from: Pair<Float, Float>, to: Pair<Float, Float>, size: Pair<Int, Int>) {
        val scale = min(size.first / 1280f, size.second / 720f)
        fun x(value: Float) = (size.first - 1280 * scale) / 2 + value * scale
        fun y(value: Float) = (size.second - 720 * scale) / 2 + value * scale
        val down = SystemClock.uptimeMillis()
        touch(x(from.first), y(from.second), MotionEvent.ACTION_DOWN, down)
        for (step in 1..12) {
            SystemClock.sleep(25)
            val ratio = step / 12f
            touch(x(from.first + (to.first - from.first) * ratio), y(from.second + (to.second - from.second) * ratio), MotionEvent.ACTION_MOVE, down)
        }
        touch(x(to.first), y(to.second), MotionEvent.ACTION_UP, down)
        SystemClock.sleep(250)
    }
    private fun skipClip() {
        SystemClock.sleep(800)
        instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Got it")?.firstOrNull()
            ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        SystemClock.sleep(300)
        val clicked = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("跳过")?.firstOrNull()
            ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!clicked) {
            val size = shot("puzzle-cutscene.png")
            val down = SystemClock.uptimeMillis()
            touch(size.first - 60f, 40f, MotionEvent.ACTION_DOWN, down)
            touch(size.first - 60f, 40f, MotionEvent.ACTION_UP, down)
        }
        SystemClock.sleep(500)
    }

    @Test fun landscapeDiscoveryPersistsAcrossExitAndBackgroundAndKeepsAccountIsolation() {
        TestMuseumServer().use { server ->
            server.progressOffline = true
            val user = "TEST_LANDSCAPE_${UUID.randomUUID()}"
            val key = ProgressKey(server.endpoint, user, Audience.CHILD)
            fun stored() = runBlocking {
                SqliteProgressStore(instrumentation.targetContext).use { it.load(key) }
            }
            compose.runOnUiThread { model().controller.logout() }
            compose.waitUntil(5000) { !model().controller.state.value.authBusy }
            try {
                compose.runOnUiThread { model().controller.authenticate(server.endpoint, user, "TEST_ONLY_PASSWORD", false) }
                compose.waitUntil(5000) { model().controller.state.value.username == user }
                compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("儿童模式").performScrollTo().performClick()
                compose.onNodeWithText("跳过引入").performClick()
                compose.waitUntil(5000) { !model().progress.state.value.loading }
                compose.onNodeWithText("文物与观察任务").performScrollTo().performClick()
                compose.onNodeWithText("进入横屏衣坊").performScrollTo().performClick()
                waitFor("Landscape must open") {
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        (bitmap.width > bitmap.height).also { bitmap.recycle() }
                    }
                }
                SystemClock.sleep(10000)
                skipClip()
                val size = shot("puzzle-room-before.png")
                contact(615f to 305f, size)
                assertTrue(stored().completed.isEmpty())
                contact(80f to 52f, size)
                waitFor("Return to main shell") { compose.activity.hasWindowFocus() }
                compose.onNodeWithText("进入横屏衣坊").performScrollTo().performClick()
                SystemClock.sleep(10000)
                val resumedSize = shot("puzzle-room-partial.png")
                contact(755f to 365f, resumedSize)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                SystemClock.sleep(400)
                // Reorder the existing activity, rather than guessing which
                // recent-task card the launcher happens to put at the center.
                instrumentation.targetContext.startActivity(android.content.Intent(
                    instrumentation.targetContext, PuzzleActivity::class.java
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("endpoint", server.endpoint).putExtra("username", user))
                SystemClock.sleep(1500)
                contact(650f to 455f, resumedSize)
                waitFor("All observations must persist") { "clothes-light-v1" in stored().completed }
                assertEquals(setOf("clothes-light-v1"), stored().completed)
                shot("puzzle-room-complete.png")
                SystemClock.sleep(3000)
                // Wrong matches must not complete the second task.
                drag(1030f to 250f, 755f to 365f, resumedSize)
                assertEquals(setOf("clothes-light-v1"), stored().completed)
                drag(1030f to 250f, 615f to 305f, resumedSize)
                contact(1100f to 325f, resumedSize)
                contact(755f to 365f, resumedSize)
                drag(1020f to 390f, 650f to 455f, resumedSize)
                waitFor("Pattern discovery must persist") { stored().completed.size == 2 }
                shot("puzzle-stamp-before.png")
                drag(990f to 375f, 660f to 365f, resumedSize)
                skipClip()
                shot("puzzle-stamp-complete.png")
                contact(80f to 52f, resumedSize)
                waitFor("Exit must preserve account shell") { compose.activity.hasWindowFocus() }
                compose.waitUntil(5000) { "clothes-light-v1" in model().progress.state.value.progress.completed }
                assertEquals(2, model().progress.state.value.progress.completed.size)
                compose.onNode(hasText("我的手帐") and hasClickAction()).performClick()
                compose.onNodeWithText("首章纪念页 · 已获得").assertExists()
                runBlocking {
                    SqliteProgressStore(instrumentation.targetContext).use {
                        assertTrue(it.load(key.copy(audience = Audience.ADULT)).completed.isEmpty())
                        assertTrue(it.load(key.copy(username = "OTHER_TEST_ACCOUNT")).completed.isEmpty())
                    }
                }
            } finally {
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                compose.runOnUiThread { model().controller.logout() }
                compose.waitUntil(5000) { !model().controller.state.value.authBusy }
                android.database.sqlite.SQLiteDatabase.openDatabase(instrumentation.targetContext.getDatabasePath("museum_progress.db").path,
                    null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                    it.delete("tasks", "endpoint=? AND username=?", arrayOf(server.endpoint, user))
                }
            }
        }
    }
}
