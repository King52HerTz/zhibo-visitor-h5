package cn.xiangyu.museum

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class StoryChapterTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = "https://example.invalid"

    private fun journey(user: String): ActivityScenario<PuzzleActivity> = ActivityScenario.launch(
        Intent(context, PuzzleActivity::class.java).putExtra("endpoint", endpoint)
            .putExtra("username", user).putExtra("story_slice", true))

    private fun awaitLabel(label: String) = compose.waitUntil(8000) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun nativeJourneyPersistsPageAndKeepsAccountsSeparate() {
        val visitor = "TEST_${UUID.randomUUID()}"
        val other = "TEST_${UUID.randomUUID()}"
        journey(visitor).use {
            awaitLabel("手帐 0/3")
            compose.onNode(hasText("帛画寻宝") and hasClickAction()).assertIsNotEnabled()
            compose.onNodeWithText("观察素纱单衣").performScrollTo().performClick()
            for (i in 1..3) {
                compose.onNodeWithContentDescription("标记 $i").performScrollTo().performClick()
            }
            compose.onNodeWithText("返回旅程").performScrollTo().performClick()
            awaitLabel("手帐 1/3")
            compose.onNode(hasText("帛画寻宝") and hasClickAction()).assertIsEnabled()
        }
        journey(visitor).use { awaitLabel("手帐 1/3") }
        journey(other).use { awaitLabel("手帐 0/3") }
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
            context.openOrCreateDatabase("museum_story.db", Context.MODE_PRIVATE, null).use { db ->
                listOf(original, other).forEach { db.execSQL("DELETE FROM chapter WHERE scope=?", arrayOf(it)) }
            }
        }
    }
}
