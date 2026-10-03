package cn.xiangyu.museum

import org.junit.Assert.*
import org.junit.Test

class StoryChapterProgressTest {
    @Test fun onlyOrderedCheckpointsUnlockPages() {
        assertEquals(listOf(0, 1, 3, 7, 15, 31, 63, 127, 255, 511), (0..1024).filter(::validStoryMask))
        assertEquals(0, StoryChapterProgress(3).pages)
        assertEquals(1, StoryChapterProgress(7).pages)
        assertEquals(1, StoryChapterProgress(31).pages)
        assertEquals(2, StoryChapterProgress(63).pages)
        assertEquals(2, StoryChapterProgress(255).pages)
        assertEquals(3, StoryChapterProgress(511).pages)
        assertFalse(StoryChapterProgress(255).complete)
        assertTrue(StoryChapterProgress(511).complete)
    }

    @Test fun scopeSeparatesAccountsAndServers() {
        val original = storyChapterScope("https://example.invalid", "TEST_USER")
        assertNotEquals(original, storyChapterScope("https://example.invalid", "TEST_OTHER"))
        assertNotEquals(original, storyChapterScope("https://other.invalid", "TEST_USER"))
        assertEquals(original, storyChapterScope("https://example.invalid", "TEST_USER"))
    }

    @Test fun cloudTasksRestoreOnlyCompletedPagesAndKeepLegacyTasksSeparate() {
        assertEquals(0, storyMaskFromTasks(setOf("clothes-light-v1", "clothes-pattern-v1")))
        assertEquals(0, storyMaskFromTasks(setOf("story-daoyin-v1")))
        assertEquals(7, storyMaskFromTasks(setOf("story-gauze-v1")))
        assertEquals(63, storyMaskFromTasks(storyTaskIds.take(2).toSet()))
        assertEquals(511, storyMaskFromTasks(storyTaskIds.toSet()))
    }
}
