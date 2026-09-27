package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExperienceContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun introductionRequiresThreeFramesOrExplicitSkip() {
        var ready = 0
        compose.setContent { MuseumTheme { StoryInvitation { ready++ } } }
        compose.onNodeWithText("开始旅程").assertDoesNotExist()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        File(instrumentation.targetContext.getExternalFilesDir(null), "experience-intro.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
        compose.onNodeWithText("翻开下一幕").performClick()
        compose.onNodeWithText("翻开下一幕").performClick()
        compose.onNodeWithText("开始旅程").performClick()
        assertEquals(1, ready)
    }

    @Test fun introductionCanBeSkippedWithoutCompletingTasks() {
        var ready = 0
        compose.setContent { MuseumTheme { StoryInvitation { ready++ } } }
        compose.onNodeWithText("跳过引入").performClick()
        assertEquals(1, ready)
    }
}
