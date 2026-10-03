package cn.xiangyu.museum

import android.content.Intent
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.io.File
import java.util.UUID
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class StorySliceTest {
    @Test fun storyboardObservationAndJudgementSaveSeparatelyFromOldTasks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val endpoint = "https://example.invalid"
        val username = "TEST_STORY_${UUID.randomUUID()}"
        val key = MessageDigest.getInstance("SHA-256")
            .digest("$endpoint\u0000$username\u0000child".toByteArray())
            .joinToString("") { "%02x".format(it) } + ":story-slice-v1"
        fun progress() = StoryChapterStore(context).use { it.load(key.removeSuffix(":story-slice-v1")) }
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        val stoppedUntil = SystemClock.uptimeMillis() + 10000
        while (manager.runningAppProcesses.orEmpty().any { it.processName == context.packageName + ":puzzle" } &&
            SystemClock.uptimeMillis() < stoppedUntil) SystemClock.sleep(100)
        assertFalse("Previous engine should have exited", manager.runningAppProcesses.orEmpty()
            .any { it.processName == context.packageName + ":puzzle" })
        context.startActivity(Intent(context, PuzzleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("endpoint", endpoint).putExtra("username", username)
            .putExtra("story_slice", true))
        try {
            val until = SystemClock.uptimeMillis() + 25000
            var width = 0
            var height = 0
            while (SystemClock.uptimeMillis() < until) {
                instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                    width = screenshot.width; height = screenshot.height; screenshot.recycle()
                }
                if (width > height && height > 0) break
                SystemClock.sleep(250)
            }
            assertTrue("Game should be landscape", width > height)
            val readyUntil = SystemClock.uptimeMillis() + 20000
            var ready = false
            while (SystemClock.uptimeMillis() < readyUntil) {
                ready = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("文字")?.isNotEmpty() == true
                if (ready) break
                SystemClock.sleep(150)
            }
            assertTrue("Story controls should be ready", ready)
            fun shot(name: String) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.getExternalFilesDir(null), name).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            shot("story-slice-start.png")
            fun tap(px: Float, py: Float) {
                val scale = min(width / 1280f, height / 720f)
                val x = (width - 1280 * scale) / 2 + px * scale
                val y = (height - 720 * scale) / 2 + py * scale
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).let {
                        assertTrue(instrumentation.uiAutomation.injectInputEvent(it, true)); it.recycle()
                    }
                }
                SystemClock.sleep(350)
            }
            tap(990f, 620f) // storyboard -> light
            tap(990f, 620f) // separate cloth-entry video slot
            tap(350f, 390f)
            tap(850f, 390f)
            tap(990f, 640f)
            shot("story-slice-after-light.png")
            assertEquals(1, progress().mask)
            tap(640f, 390f)
            assertEquals(3, progress().mask)
            tap(850f, 440f) // unsupported claim must not pass
            assertEquals(3, progress().mask)
            tap(350f, 440f)
            assertEquals(7, progress().mask)
            assertFalse(context.getSharedPreferences("puzzle_checkpoints", Context.MODE_PRIVATE)
                .getBoolean(key.removeSuffix(":story-slice-v1") + ":stamp", false))
        } finally {
            // Exit button closes the isolated engine; Android back first returns to the scroll.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            SystemClock.sleep(1200)
            context.openOrCreateDatabase("museum_story.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("DELETE FROM chapter WHERE scope=?", arrayOf(key.removeSuffix(":story-slice-v1")))
            }
            context.openOrCreateDatabase("museum_progress.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("DELETE FROM tasks WHERE endpoint=? AND username=?", arrayOf(endpoint, username))
            }
        }
    }
}
