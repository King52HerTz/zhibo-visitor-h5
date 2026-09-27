package cn.xiangyu.museum

import org.junit.Assert.*
import org.junit.Test

class FirstChapterTest {
    @Test fun stableIdsAreUnique() {
        assertEquals(2, FirstChapter.relics.size)
        assertEquals(2, FirstChapter.relics.map { it.id }.toSet().size)
    }
    @Test fun modesHaveDifferentContent() {
        FirstChapter.relics.forEach {
            assertTrue(FirstChapter.text(it, Audience.CHILD).isNotBlank())
            assertTrue(FirstChapter.text(it, Audience.ADULT).isNotBlank())
            assertNotEquals(FirstChapter.text(it, Audience.CHILD), FirstChapter.text(it, Audience.ADULT))
        }
    }
    @Test fun sourceIsHttpsAndUnverifiedWeightIsAbsent() {
        assertTrue(FirstChapter.sourceUrl.startsWith("https://"))
        FirstChapter.relics.forEach {
            assertFalse(it.childText.contains("49"))
            assertFalse(it.adultText.contains("48"))
        }
    }
}
