package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WorkshopTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    @Test fun sceneRoutesToBothGamesAndUsesExistingCompletionIds() {
        val completed = mutableStateOf(emptySet<String>())
        compose.setContent {
            var task by remember { mutableStateOf<ObservationTask?>(null) }
            MuseumTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                    if (task == null) WorkshopContent(TaskProgress(completed.value), { task = it })
                    else WorkshopTaskContent(task!!, ProgressState(TaskProgress(completed.value)), {
                        completed.value = completed.value + it
                    })
                }
            }
        }
        shot("workshop-scene.png")
        compose.onNodeWithText("衣服", substring = false).performClick()
        compose.onNodeWithText("把发现收进手帐").assertIsNotEnabled()
        compose.onNodeWithText("掀开衣料").performScrollTo().performClick()
        compose.onNodeWithText("把发现收进手帐").performScrollTo().performClick()
        compose.onNodeWithText("已收进手帐").assertIsNotEnabled()
        assertEquals(setOf("clothes-light-v1"), completed.value)
        shot("workshop-light.png")
    }

    @Test fun flowerDragOutsideDoesNotCompleteAndInsideDoes() {
        var completed = false
        compose.setContent {
            MuseumTheme {
                WorkshopTaskContent(FirstChapter.tasks[1], ProgressState(), { completed = true },
                    Modifier.fillMaxSize().padding(Space.medium))
            }
        }
        val piece = compose.onNodeWithTag("flower-piece")
        val source = piece.fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("flower-target").fetchSemanticsNode().boundsInRoot.center
        piece.performTouchInput { swipe(center, center + Offset(0f, 20f), 400) }
        compose.onNodeWithText("把发现收进手帐").assertIsNotEnabled()
        piece.performTouchInput { swipe(center, center + (target - source), 600) }
        compose.onNodeWithText("花叶已放入").assertExists()
        compose.onNodeWithText("把发现收进手帐").performClick()
        assertTrue(completed)
        shot("workshop-flower.png")
    }

    @Test fun animationCanPauseRestartAndDoesNotGateTasks() {
        compose.setContent {
            MuseumTheme {
                WorkshopContent(TaskProgress(), {}, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.medium))
            }
        }
        compose.onNodeWithText("丝线", substring = false).performClick()
        compose.onNodeWithText("播放动画").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("1 / 12 秒").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("暂停动画").performScrollTo().performClick()
        compose.onNodeWithText("继续播放").assertExists()
        compose.onNodeWithText("从头播放").performClick()
        compose.onNodeWithText("暂停动画").performClick()
        compose.onNodeWithText("去发现 · 发现轻与薄").assertExists()
    }

    @Test fun flowerTapAlternativeWorksWithoutDrag() {
        var completed = false
        compose.setContent {
            MuseumTheme { WorkshopTaskContent(FirstChapter.tasks[1], ProgressState(), { completed = true }) }
        }
        compose.onNodeWithText("点一下放入花叶").performClick()
        compose.onNodeWithText("把发现收进手帐").performClick()
        assertTrue(completed)
    }
}
