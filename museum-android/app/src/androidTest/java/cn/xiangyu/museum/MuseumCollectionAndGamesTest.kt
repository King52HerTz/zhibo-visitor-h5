package cn.xiangyu.museum

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MuseumCollectionAndGamesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: TestMuseumServer
    private fun controller() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java].controller
    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Before fun login() {
        server = TestMuseumServer()
        compose.runOnUiThread { controller().logout() }
        compose.waitUntil(6000) { !controller().state.value.authBusy && !controller().state.value.restoring }
        compose.runOnUiThread { controller().authenticate(server.endpoint, "PREVIEW_TEST", "TEST_ONLY_PASSWORD", false) }
        compose.waitUntil(6000) { controller().state.value.username == "PREVIEW_TEST" }
        compose.waitUntil(6000) { compose.onAllNodesWithText("选择导览模式").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun logout() {
        compose.runOnUiThread { controller().logout() }
        compose.waitUntil(6000) { !controller().state.value.authBusy }
        server.close()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val test = InstrumentationRegistry.getInstrumentation()
        val shot = test.uiAutomation.takeScreenshot() ?: return
        File(test.targetContext.getExternalFilesDir(null), name).outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shot.recycle()
    }

    @Test fun collectionHasElevenRealRecordsAndSearchOpensCitedArchive() {
        assertEquals(11, museumCollection.size)
        assertEquals(11, museumCollection.map { it.id }.distinct().size)
        assertEquals(11, museumCollection.map { it.art }.distinct().size)
        assertTrue(museumCollection.all { !it.origin.isNullOrBlank() && it.period.isNotBlank() && it.collection != null })
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("馆藏展品").fetchSemanticsNodes().isNotEmpty() }
        capture("catalog-adult-home.png")
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("11 件"))
        list.performScrollToNode(hasText("长沙窑茶具"))
        compose.onNodeWithText("长沙窑茶具").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNode(hasSetTextAction() and hasText("搜索文物、年代或类别")).performTextInput("方鼎")
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            (compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
        compose.onNode(hasText("青铜器") and isSelectable()).performClick().assertIsSelected()
        list.performScrollToNode(hasText("大禾人面纹方鼎"))
        compose.onNodeWithText("大禾人面纹方鼎").performClick()
        compose.onNodeWithText("问问这件文物").assertIsDisplayed()
        compose.onNodeWithText("收藏展品").performClick()
        capture("catalog-adult-detail.png")
        compose.onNodeWithText("馆藏档案").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("图像原始出处").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("看大图").performScrollTo().performClick()
        compose.onNodeWithText("放大").performClick()
        compose.onNodeWithText("复位").assertIsEnabled()
        compose.onNodeWithText("关闭大图").performClick()
        compose.onNodeWithText("取消收藏").assertIsDisplayed()
        compose.onNodeWithText("问问这件文物").performClick()
        compose.waitUntil(5000) { controller().state.value.exhibitContextName == "大禾人面纹方鼎" }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("取消收藏").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("大禾人面纹方鼎").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNode(hasSetTextAction() and hasText("方鼎")).assertExists()
        compose.onNode(hasText("青铜器") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("清除").performClick()
        compose.onNode(hasText("全部") and isSelectable()).performClick()
        list.performScrollToNode(hasText("馆藏目录"))
        compose.onNodeWithText("馆藏目录").assertIsDisplayed()
    }

    @Test fun bannerTreasureRespondsToWrongAndCorrectTapsAndSaves() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        tab("游戏").performClick()
        compose.onNodeWithText("开始找").performScrollTo().performClick()
        compose.onNodeWithContentDescription("标记 2").performScrollTo().performClick()
        compose.onNodeWithText("还没找到", substring = true).performScrollTo().assertIsDisplayed()
        for (i in 1..3) compose.onNodeWithContentDescription("标记 $i").performScrollTo().performClick()
        compose.onNodeWithText("寻宝完成", substring = true).performScrollTo().assertIsDisplayed()
        capture("v2-banner-game.png")
        tab("我的").performClick()
        compose.onNodeWithText("游戏进度").performScrollTo().performClick()
        compose.onNode(hasText("帛画寻宝") and hasText("已完成")).performScrollTo().assertIsDisplayed()
    }

    @Test fun restorationPuzzleAndPictureMatchingHaveCompleteLoops() {
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        tab("游戏").performClick()
        compose.onNodeWithText("拼合漆耳杯").performScrollTo().performClick()
        for (position in listOf(1,5,3,5,4,5,5,6))
            compose.onNode(hasContentDescription("第 $position 格", substring = true)).performScrollTo().performClick()
        compose.onNodeWithText("拼合完成", substring = true).performScrollTo().assertIsDisplayed()
        capture("v2-lacquer-game.png")
        compose.onNodeWithText("返回游戏").performScrollTo().performClick()
        compose.onNodeWithText("导引图配对").performScrollTo().performClick()
        for (position in listOf(1,6,2,5,3,4))
            compose.onNode(hasContentDescription("第 $position 张图卡", substring = true)).performScrollTo().performClick()
        compose.onNodeWithText("配对完成", substring = true).performScrollTo().assertIsDisplayed()
        capture("v2-daoyin-game.png")
        compose.onNodeWithText("再玩一次").performClick()
        compose.onNodeWithText("已配对 0/3").assertExists()
    }
}
