package cn.xiangyu.museum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalObservationPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyJournalInvitesANewObservationWithoutAwardingTasks() {
        var creates = 0
        compose.setContent { MuseumTheme {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                LocalObservationList(Audience.CHILD, emptyList(), false, null, { creates++ }, {}, {})
            }
        } }
        compose.onNodeWithText("我的观察记录").assertExists()
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        assertEquals(1, creates)
        compose.onNodeWithText("这一页，等你的发现").assertExists()
        compose.onNodeWithText("记录参与，不是到馆证明，也不会自动完成探索任务。").performScrollTo().assertIsDisplayed()
    }

    @Test fun writingOrChoosingAHintOnlySavesAfterConfirmation() {
        var note by mutableStateOf("")
        var saves = 0
        var observations = 0
        compose.setContent { MuseumTheme {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                LocalObservationEditor(Audience.CHILD, FirstChapter.relics.first().id, note, false, false, null,
                    {}, { note = it }, {}, { observations++ }, { saves++ }, {}, {})
            }
        } }
        compose.onNodeWithText("确认收进手帐").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("   ")
        compose.onNodeWithText("确认收进手帐").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("只做观察").performScrollTo().performClick()
        assertEquals(1, observations)
        assertEquals(0, saves)
        compose.onNodeWithText("我注意到衣料很轻薄。").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("我注意到衣料很轻薄。")
        assertEquals(0, saves)
        compose.onNodeWithText("确认收进手帐").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test fun replacingAPhotoAndBusySavingGuardActions() {
        var photo by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var cameras = 0
        var saves = 0
        compose.setContent { MuseumTheme {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                LocalObservationEditor(Audience.ADULT, FirstChapter.relics.last().id, "我看到植物纹样。", photo, busy, null,
                    {}, {}, { cameras++ }, { photo = false }, { saves++ }, {}, { Text("测试照片预览") })
            }
        } }
        compose.onNodeWithText("测试照片预览").assertExists()
        compose.onNodeWithText("改用文字观察").performScrollTo().performClick()
        compose.onNodeWithText("测试照片预览").assertDoesNotExist()
        assertEquals(0, saves)
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("允许拍照时拍一张").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("只做观察").assertIsNotEnabled()
        compose.onNodeWithText("正在收好观察…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("返回观察记录").assertIsNotEnabled()
        assertEquals(0, cameras)
        assertEquals(0, saves)
    }

    @Test fun deletingARecordRequiresConfirmationAndCancelKeepsIt() {
        val record = LocalObservation("TEST_ONLY_RECORD", FirstChapter.relics.first().id, "测试观察内容", null, 0L)
        var deleted: LocalObservation? = null
        compose.setContent { MuseumTheme {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                LocalObservationList(Audience.CHILD, listOf(record), false, null, {}, { deleted = it }, {})
            }
        } }
        compose.onNodeWithText("删除记录").performScrollTo().performClick()
        compose.onNodeWithText("删除这条观察？").assertIsDisplayed()
        assertEquals(null, deleted)
        compose.onNodeWithText("保留记录").performClick()
        compose.onNodeWithText("删除这条观察？").assertDoesNotExist()
        assertEquals(null, deleted)
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithText("确认删除").performClick()
        assertEquals(record, deleted)
    }

    @Test fun observationEditorRemainsReachableWithLargeText() {
        var note by mutableStateOf("")
        var relicId by mutableStateOf(FirstChapter.relics.first().id)
        var saves = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MuseumTheme {
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.medium)) {
                        LocalObservationEditor(Audience.CHILD, relicId, note, false, false, null,
                            { relicId = it }, { note = it }, {}, {}, { saves++ }, {}, {})
                    }
                }
            }
        }
        compose.onNode(hasText(FirstChapter.relics.last().title) and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("纹样让我想到植物的枝叶和花。").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("确认收进手帐").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("返回观察记录").performScrollTo().assertIsDisplayed()
        assertEquals(1, saves)
    }
}
