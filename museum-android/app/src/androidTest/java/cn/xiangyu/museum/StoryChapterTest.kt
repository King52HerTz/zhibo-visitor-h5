package cn.xiangyu.museum

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.min

class StoryChapterTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val endpoint = "https://example.invalid"
    private var width = 0
    private var height = 0
    private var scope = ""

    private fun progress() = StoryChapterStore(context).use { it.load(scope) }
    private fun launch(user: String) {
        scope = storyChapterScope(endpoint, user)
        context.startActivity(Intent(context, PuzzleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("endpoint", endpoint).putExtra("username", user).putExtra("story_slice", true))
        val until = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < until) {
            instrumentation.uiAutomation.takeScreenshot()?.let {
                width = it.width; height = it.height; it.recycle()
            }
            if (width > height && height > 0) break
            SystemClock.sleep(200)
        }
        assertTrue("Game is landscape", width > height)
        // Wait for the native control bridge, not just the landscape boot splash.
        val readyUntil = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < readyUntil) {
            if (instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("文字")?.isNotEmpty() == true) return
            SystemClock.sleep(150)
        }
        fail("Story controls did not become ready")
    }
    private fun tap(x: Float, y: Float) {
        val scale = min(width / 1280f, height / 720f)
        val screenX = (width - 1280 * scale) / 2 + x * scale
        val screenY = (height - 720 * scale) / 2 + y * scale
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, screenX, screenY, 0).let {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(it, true)); it.recycle()
            }
        }
        SystemClock.sleep(200)
    }
    private fun next() = tap(1000f, 650f)
    private fun exit() { tap(1180f, 60f); SystemClock.sleep(1200) }
    private fun shot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun assertMask(mask: Int) {
        val until = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < until && progress().mask != mask) SystemClock.sleep(100)
        assertEquals(mask, progress().mask)
    }
    private fun deleteTestScopes(vararg scopes: String) {
        context.openOrCreateDatabase("museum_story.db", Context.MODE_PRIVATE, null).use { db ->
            scopes.forEach { db.execSQL("DELETE FROM chapter WHERE scope=?", arrayOf(it)) }
        }
    }

    private fun clickText(text: String) {
        val until = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < until) {
            val node = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text && it.isEnabled && it.isClickable }
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(350)
                return
            }
            SystemClock.sleep(100)
        }
        fail("Missing actionable native control: $text")
    }

    @Test fun nativeTextControlsCompleteTheSameChapterWithoutCanvasCoordinates() {
        val user = "TEST_CHAPTER_TEXT_${UUID.randomUUID()}"
        try {
            launch(user)
            clickText("文字")
            clickText("继续探索"); clickText("继续探索")
            val next = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("我看出不同了")?.first()
            assertNotNull(next)
            assertFalse(next!!.isEnabled)
            clickText("点亮厚布示意"); clickText("点亮纱衣示意")
            shot("chapter-text-cloth.png")
            clickText("我看出不同了"); clickText("观察衣领轮廓")
            clickText("辛追亲手穿过它"); assertMask(3)
            clickText("两块布透光不同"); assertMask(7)
            repeat(4) { clickText("继续探索") }
            for (name in listOf("上部", "中部", "下部")) {
                clickText("观察${name}线索"); clickText("送到画的$name")
            }
            assertMask(63)
            repeat(3) { clickText("继续探索") }
            clickText("选择 B：手臂朝上"); clickText("选择 B")
            clickText("三号墓"); assertMask(511)
            clickText("继续探索"); clickText("继续探索")
            clickText("盖上三件发现章")
            assertTrue(progress().stamped)
            shot("chapter-text-scroll.png")
            clickText("退出探索")
        } finally {
            context.openOrCreateDatabase("museum_progress.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("DELETE FROM tasks WHERE endpoint=? AND username=?", arrayOf(endpoint, user))
            }
            deleteTestScopes(storyChapterScope(endpoint, user))
        }
    }

    @Test fun allThreeRelicsPersistAcrossReentryAndAwardOnlyTheNewStamp() {
        val user = "TEST_CHAPTER_${UUID.randomUUID()}"
        val original = storyChapterScope(endpoint, user)
        val otherUser = "TEST_CHAPTER_OTHER_${UUID.randomUUID()}"
        val other = storyChapterScope(endpoint, otherUser)
        try {
            launch(user)
            shot("chapter-video-slot.png")
            tap(1000f, 60f) // scroll: painting and daoyin locked
            tap(650f, 390f)
            assertMask(0)
            tap(300f, 390f) // cloth card
            next()
            tap(350f, 400f); tap(850f, 400f); next()
            assertMask(1)
            exit()
            launch(user)
            tap(640f, 400f)
            assertMask(3)
            tap(850f, 440f)
            assertMask(3)
            tap(350f, 440f)
            assertMask(7)
            repeat(4) { next() }
            shot("chapter-painting.png")
            tap(850f, 320f) // no selected card
            assertMask(7)
            tap(300f, 320f); tap(850f, 420f) // wrong destination
            assertMask(7)
            tap(850f, 320f)
            assertMask(15)
            exit()
            launch(user)
            tap(300f, 424f); tap(850f, 424f)
            assertMask(31)
            tap(300f, 528f); tap(850f, 528f)
            assertMask(63)
            repeat(3) { next() }
            shot("chapter-daoyin.png")
            tap(600f, 400f)
            assertMask(63)
            tap(950f, 400f)
            assertMask(127)
            // Explicitly reorder the existing activity after backgrounding; no engine recreation.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            SystemClock.sleep(800)
            context.startActivity(Intent(context, PuzzleActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("endpoint", endpoint).putExtra("username", user).putExtra("story_slice", true))
            SystemClock.sleep(1200)
            tap(300f, 400f)
            assertMask(127)
            tap(650f, 400f)
            assertMask(255)
            tap(350f, 440f)
            assertMask(255)
            assertFalse(progress().stamped)
            tap(850f, 440f)
            assertMask(511)
            assertFalse(progress().stamped)
            next(); next(); next()
            assertTrue(progress().stamped)
            assertEquals(0, progress().watched) // missing video never counts as watched
            shot("chapter-scroll-complete.png")
            assertFalse(context.getSharedPreferences("puzzle_checkpoints", Context.MODE_PRIVATE).getBoolean(original + ":stamp", false))
            val tasks = kotlinx.coroutines.runBlocking {
                SqliteProgressStore(context).use { it.load(ProgressKey(endpoint, user, Audience.CHILD)) }
            }
            assertEquals(storyTaskIds.toSet(), tasks.completed)
            assertEquals(storyTaskIds.toSet(), tasks.pending)
            tap(650f, 390f); next() // replay painting retains all earned progress
            assertMask(511)
            assertTrue(progress().stamped)
            exit()
            launch(otherUser)
            assertMask(0)
            assertFalse(progress().stamped)
            exit()
            launch(user)
            assertMask(511)
            assertTrue(progress().stamped)
            shot("chapter-scroll-restored.png")
        } finally {
            exit()
            deleteTestScopes(original, other)
            context.openOrCreateDatabase("museum_progress.db", Context.MODE_PRIVATE, null).use { db ->
                for (name in listOf(user, otherUser)) {
                    db.execSQL("DELETE FROM tasks WHERE endpoint=? AND username=?", arrayOf(endpoint, name))
                }
            }
        }
    }

    @Test fun storeMigratesOldSliceAndRejectsSkippedActionsAndPrematureStamp() {
        val original = "TEST_SCOPE_${UUID.randomUUID()}"
        val other = "TEST_SCOPE_${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences("puzzle_checkpoints", Context.MODE_PRIVATE)
        prefs.edit().putInt(original + ":story-slice-v1", 3).commit()
        try {
            StoryChapterStore(context).use { store ->
                assertEquals(3, store.load(original).mask)
                assertFalse(store.save(original, 511))
                assertFalse(store.save(original, 5))
                assertFalse(store.stamp(original))
                assertTrue(store.watched(original, 0))
                assertEquals(3, store.load(original).mask)
                assertFalse(store.load(original).stamped)
                assertEquals(0, store.load(other).mask)
                for (mask in listOf(7, 15, 31, 63, 127, 255, 511)) assertTrue(store.save(original, mask))
                assertTrue(store.stamp(original))
                assertTrue(store.stamp(original))
                assertTrue(store.save(original, 7))
            }
            StoryChapterStore(context).use {
                assertEquals(StoryChapterProgress(511, true, 1), it.load(original))
                assertEquals(StoryChapterProgress(), it.load(other))
            }
        } finally {
            prefs.edit().remove(original + ":story-slice-v1").commit()
            deleteTestScopes(original, other)
        }
    }
}
