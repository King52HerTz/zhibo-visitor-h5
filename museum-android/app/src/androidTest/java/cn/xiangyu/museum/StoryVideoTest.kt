package cn.xiangyu.museum

import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class StoryVideoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var restoration: StateRestorationTester

    private fun setup() {
        assumeTrue("Local story videos required", storyVideoResources(compose.activity).all { it != 0 })
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            var done by remember { mutableStateOf(false) }
            MuseumTheme { if (done) Text("已进入探索") else StoryInvitation { done = true } }
        }
    }

    private fun findVideo(view: View): VideoView? {
        if (view is VideoView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findVideo(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun player() = findVideo(compose.activity.window.decorView)
    private fun waitPlaying() = compose.waitUntil(10000) {
        var playing = false
        compose.runOnUiThread { playing = player()?.let { it.isPlaying && it.currentPosition > 700 } == true }
        playing
    }

    @Test fun invitationOnlyPlaysOpeningWithoutPrematureEnding() {
        setup()
        waitPlaying()
        compose.onNodeWithText("开始探索").assertDoesNotExist()
        compose.waitUntil(15000) { compose.onAllNodesWithText("跟随纸蝶").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("跟随纸蝶").performClick()
        compose.onNodeWithText("已进入探索").assertExists()
        compose.runOnIdle { assertNull(player()) }
    }

    @Test fun pauseReplayAndBackgroundDoNotKeepAudioPlaying() {
        setup()
        waitPlaying()
        compose.onNodeWithText("暂停播放").performClick()
        compose.runOnIdle { assertFalse(player()!!.isPlaying) }
        compose.onNodeWithText("重播本幕").performClick()
        waitPlaying()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("继续播放").assertExists()
        compose.runOnIdle { assertFalse(player()!!.isPlaying) }
        compose.onNodeWithText("继续播放").performClick()
        waitPlaying()
        compose.onNodeWithText("暂停播放").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("继续播放").assertExists()
        compose.onNodeWithText("继续播放").performClick()
        waitPlaying()
    }

    @Test fun skipReleasesPlayingVideo() {
        setup()
        waitPlaying()
        lateinit var previous: VideoView
        compose.runOnIdle { previous = player()!! }
        compose.onNodeWithText("跳过引入").performClick()
        compose.onNodeWithText("已进入探索").assertExists()
        compose.runOnIdle { assertFalse(previous.isPlaying); assertNull(player()) }
    }
}
