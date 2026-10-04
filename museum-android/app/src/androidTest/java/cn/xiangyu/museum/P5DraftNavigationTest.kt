package cn.xiangyu.museum

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class P5DraftNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: TestMuseumServer
    private val username = "P5_NAV_${System.nanoTime()}"
    private val draftText = "测试观察：保留我写下的丝纱线索。"
    private fun model() = ViewModelProvider(compose.activity)[MuseumViewModel::class.java]
    private fun key(audience: Audience) = ProgressKey(server.endpoint, username, audience)

    @Before fun login() {
        server = TestMuseumServer()
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy && !model().controller.state.value.restoring }
        compose.runOnUiThread { model().controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
        compose.waitUntil(5000) { model().controller.state.value.username == username }
        compose.waitUntil(5000) { compose.onAllNodesWithText("儿童模式").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun enter(audience: Audience) {
        compose.onNodeWithText(audience.label).performScrollTo().performClick()
        if (audience == Audience.CHILD) compose.onNodeWithText("跳过引入").performClick()
        compose.waitUntil(5000) { !model().progress.state.value.loading && !model().observations.state.value.loading }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun switchMode() = compose.onNode(hasText("切换模式", substring = true) and hasClickAction())

    private fun hideKeyboard() {
        compose.runOnUiThread {
            (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(5000) { Build.VERSION.SDK_INT < 30 ||
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true }
        compose.waitForIdle()
    }

    private fun draft(audience: Audience) {
        tab(if (audience == Audience.CHILD) "我的手帐" else "我的").performClick()
        compose.onNodeWithText("新建观察记录").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo().performTextInput(draftText)
        hideKeyboard()
    }

    private fun back() {
        hideKeyboard()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun screenshot(name: String, visibleText: String) {
        hideKeyboard()
        compose.onNodeWithText(visibleText).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun containsVisibleText(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isVisibleToUser && (node.text?.contains(visibleText) == true ||
                    node.contentDescription?.contains(visibleText) == true)) return true
            return (0 until node.childCount).any { containsVisibleText(node.getChild(it)) }
        }
        compose.waitUntil(5000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.packageName?.toString() == compose.activity.packageName && containsVisibleText(root)
        }
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val context = instrumentation.targetContext
        val fontScale = context.resources.configuration.fontScale
        File(context.getExternalFilesDir(null), "$name-$fontScale.png").outputStream().use {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }
    }

    private fun savedRecords(audience: Audience) = runBlocking {
        SqliteLocalObservationStore(InstrumentationRegistry.getInstrumentation().targetContext).use { it.load(key(audience)) }
    }

    @After fun cleanup() {
        compose.runOnUiThread { model().controller.logout() }
        compose.waitUntil(5000) { !model().controller.state.value.authBusy }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val camera = LocalJournalCamera(context)
        camera.pending()?.takeIf { capture -> Audience.entries.any { capture.scope == localObservationScope(key(it)) } }
            ?.let { camera.discard(it.id) }
        runBlocking { SqliteLocalObservationStore(context).use { store ->
            Audience.entries.forEach { audience -> store.load(key(audience)).forEach { store.delete(key(audience), it.id) } }
        } }
        if (context.getDatabasePath("museum_progress.db").exists()) {
            context.openOrCreateDatabase("museum_progress.db", Context.MODE_PRIVATE, null).use {
                it.delete("tasks", "endpoint=? AND username=?", arrayOf(server.endpoint, username))
            }
        }
        if (context.getDatabasePath("museum_story.db").exists()) {
            context.openOrCreateDatabase("museum_story.db", Context.MODE_PRIVATE, null).use {
                it.delete("chapter", "scope=?", arrayOf(storyChapterScope(server.endpoint, username)))
            }
        }
        server.close()
    }

    @Test fun modeSwitchKeepsDraftUntilConfirmationWithoutChangingSavedRecordsOrTasks() {
        enter(Audience.CHILD)
        draft(Audience.CHILD)
        compose.onNodeWithText("确认收进手帐").performScrollTo().performClick()
        compose.waitUntil(5000) { !model().observations.state.value.editing && model().observations.state.value.records.size == 1 }
        compose.runOnUiThread { model().progress.complete(FirstChapter.tasks.first().id) }
        compose.waitUntil(5000) { FirstChapter.tasks.first().id in model().progress.state.value.progress.completed &&
            !model().progress.state.value.syncing && model().progress.state.value.progress.pending.isEmpty() }
        val saved = savedRecords(Audience.CHILD)
        val progress = model().progress.state.value.progress
        draft(Audience.CHILD)
        val draftId = model().observations.state.value.draftId
        switchMode().performScrollTo().performClick()
        compose.onNodeWithText("放下草稿？").assertIsDisplayed()
        assertEquals(Audience.CHILD, model().controller.state.value.audience)
        screenshot("p5-mode-confirm", "放下草稿？")
        compose.onNodeWithText("继续记录").performClick()
        tab("我的手帐").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
        assertEquals(draftId, model().observations.state.value.draftId)
        assertEquals(saved, savedRecords(Audience.CHILD))
        assertEquals(progress, model().progress.state.value.progress)
        compose.onNodeWithText("写一句自己的发现").performScrollTo()
        screenshot("p5-mode-return-draft", "写一句自己的发现")
        switchMode().performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并切换").performClick()
        compose.onNodeWithText("今天，怎样出发？").assertIsDisplayed()
        assertNull(model().controller.state.value.audience)
        assertFalse(model().observations.state.value.editing)
        enter(Audience.ADULT)
        assertTrue(model().observations.state.value.records.isEmpty())
        assertEquals(saved, savedRecords(Audience.CHILD))
        runBlocking { SqliteProgressStore(InstrumentationRegistry.getInstrumentation().targetContext).use {
            assertEquals(progress, it.load(key(Audience.CHILD)))
        } }
    }

    @Test fun logoutConfirmationSurvivesRecreationAndCancelReturnsToTheDraft() {
        enter(Audience.ADULT)
        draft(Audience.ADULT)
        val draftId = model().observations.state.value.draftId
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并退出").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("放下草稿并退出").assertIsDisplayed()
        assertEquals(username, model().controller.state.value.username)
        screenshot("p5-logout-confirm-restored", "放下草稿？")
        compose.onNodeWithText("继续记录").performClick()
        tab("我的").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
        assertEquals(draftId, model().observations.state.value.draftId)
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并退出").performClick()
        compose.waitUntil(5000) { model().controller.state.value.username == null && !model().controller.state.value.authBusy }
        assertFalse(model().observations.state.value.editing)
        assertTrue(savedRecords(Audience.ADULT).isEmpty())
    }

    @Test fun chatModeSwitchProtectsAHiddenDraftAndCancelOpensItsJournal() {
        enter(Audience.ADULT)
        draft(Audience.ADULT)
        val draftId = model().observations.state.value.draftId
        tab("讲解").performClick()
        compose.waitUntil(5000) { !model().controller.state.value.chatBusy }
        switchMode().performClick()
        compose.onNodeWithText("放下草稿？").assertIsDisplayed()
        assertEquals(Audience.ADULT, model().controller.state.value.audience)
        screenshot("p5-hidden-chat-confirm", "放下草稿？")
        compose.onNodeWithText("继续记录").performClick()
        tab("我的").assertIsSelected()
        assertEquals(draftId, model().observations.state.value.draftId)
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
    }

    @Test fun backToModeSelectionProtectsTheHiddenChildDraft() {
        enter(Audience.CHILD)
        draft(Audience.CHILD)
        tab("问阿帛").performClick()
        back()
        tab("探索").assertIsSelected()
        assertEquals(Audience.CHILD, model().controller.state.value.audience)
        back()
        compose.onNodeWithText("放下草稿？").assertIsDisplayed()
        compose.onNodeWithText("继续记录").performClick()
        tab("我的手帐").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
        tab("探索").performClick()
        back()
        compose.onNodeWithText("放下草稿并切换").performClick()
        assertNull(model().controller.state.value.audience)
        assertFalse(model().observations.state.value.editing)
    }

    @Test fun storyInvitationBackUsesTheSameDraftProtection() {
        enter(Audience.CHILD)
        draft(Audience.CHILD)
        tab("探索").performClick()
        compose.onNodeWithText("重看故事引入").performScrollTo().performClick()
        compose.onNodeWithText("跳过引入").assertIsDisplayed()
        back()
        compose.onNodeWithText("放下草稿？").assertIsDisplayed()
        compose.onNodeWithText("继续记录").performClick()
        tab("我的手帐").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
    }

    @Test fun relicEntrypointsUseStableContextAndPreserveAnExistingDraft() {
        enter(Audience.ADULT)
        val progress = model().progress.state.value.progress
        tab("文物").performClick()
        compose.onNodeWithContentDescription("记下${FirstChapter.relics.last().title}的观察").performScrollTo().performClick()
        tab("我的").assertIsSelected()
        assertEquals(FirstChapter.relics.last().id, model().observations.state.value.relicId)
        compose.onNode(hasSetTextAction() and hasText("我的发现")).performScrollTo().performTextInput(draftText)
        hideKeyboard()
        compose.onNodeWithText("写一句自己的发现").performScrollTo()
        screenshot("p5-relic-observation", "写一句自己的发现")
        val draftId = model().observations.state.value.draftId
        tab("文物").performClick()
        compose.onNodeWithContentDescription("记下${FirstChapter.relics.first().title}的观察").performScrollTo().performClick()
        tab("我的").assertIsSelected()
        assertEquals(draftId, model().observations.state.value.draftId)
        assertEquals(FirstChapter.relics.last().id, model().observations.state.value.relicId)
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
        assertEquals(progress, model().progress.state.value.progress)
        assertTrue(savedRecords(Audience.ADULT).isEmpty())
    }

    @Test fun childSourceEntryStartsAnObservationForItsRelic() {
        enter(Audience.CHILD)
        val progress = model().progress.state.value.progress
        compose.onNodeWithText("文物与观察任务").performScrollTo().performClick()
        compose.onNodeWithText("文物与来源").performScrollTo().performClick()
        compose.onNodeWithContentDescription("记下${FirstChapter.relics.last().title}的观察").performScrollTo().performClick()
        tab("我的手帐").assertIsSelected()
        assertEquals(FirstChapter.relics.last().id, model().observations.state.value.relicId)
        assertEquals(progress, model().progress.state.value.progress)
        assertTrue(savedRecords(Audience.CHILD).isEmpty())
    }

    @Test fun busySavingPreventsGlobalConfirmationAndHiddenChatIdentitySwitches() {
        enter(Audience.ADULT)
        draft(Audience.ADULT)
        switchMode().performScrollTo().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = context.openOrCreateDatabase("museum_observations.db", Context.MODE_PRIVATE, null)
        database.beginTransaction()
        try {
            compose.runOnUiThread { model().observations.save() }
            compose.waitUntil(5000) { model().observations.state.value.busy }
            compose.onNodeWithText("放下草稿并切换").assertIsNotEnabled()
            assertEquals(Audience.ADULT, model().controller.state.value.audience)
            compose.onNodeWithText("继续记录").performClick()
            switchMode().performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("退出登录").performScrollTo().assertIsNotEnabled()
            tab("讲解").performClick()
            compose.waitUntil(5000) { !model().controller.state.value.chatBusy }
            switchMode().performClick()
            compose.onNodeWithText("放下草稿？").assertDoesNotExist()
            assertEquals(Audience.ADULT, model().controller.state.value.audience)
            assertEquals(username, model().controller.state.value.username)
        } finally {
            database.endTransaction()
            database.close()
        }
        compose.waitUntil(10000) { !model().observations.state.value.busy && model().observations.state.value.records.size == 1 }
    }

    @Test fun photoRelicChangeRequiresConfirmationAndKeepsTheWrittenObservation() {
        enter(Audience.ADULT)
        draft(Audience.ADULT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            var captureId: String? = null
            model().observations.takePhoto { id, uri ->
                captureId = id
                val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(20, 108, 114))
                context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                bitmap.recycle()
            }
            model().observations.cameraResult(requireNotNull(captureId), true)
        }
        compose.waitUntil(5000) { model().observations.state.value.photo != null }
        val photo = model().observations.state.value.photo
        val originalRelic = model().observations.state.value.relicId
        compose.onNode(hasText(FirstChapter.relics.last().title) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("更换观察的文物？").assertIsDisplayed()
        screenshot("p5-photo-relic-confirm", "更换观察的文物？")
        compose.onNodeWithText("保留原文物").performClick()
        assertEquals(originalRelic, model().observations.state.value.relicId)
        assertEquals(photo, model().observations.state.value.photo)
        assertEquals(draftText, model().observations.state.value.note)
        assertTrue(photo!!.isFile)
        compose.onNode(hasText(FirstChapter.relics.last().title) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("更换并移除照片").performClick()
        assertEquals(FirstChapter.relics.last().id, model().observations.state.value.relicId)
        assertNull(model().observations.state.value.photo)
        assertEquals(draftText, model().observations.state.value.note)
        assertTrue(savedRecords(Audience.ADULT).isEmpty())
        compose.onNodeWithText("记录一次观察").performScrollTo()
        screenshot("p5-photo-relic-changed", "记录一次观察")
    }

    @Test fun waitingModePhotoDraftRequiresExplicitDiscardAndMatchingModeRestoresIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val camera = LocalJournalCamera(context)
        fun fixtureTicket(): Pair<JournalCapture, File> {
            val capture = camera.begin(key(Audience.CHILD), UUID.randomUUID().toString(), FirstChapter.relics.first().id, draftText)
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(20, 108, 114))
            context.contentResolver.openOutputStream(camera.uri(capture))!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            val ready = requireNotNull(camera.finish(capture.id, true))
            assertEquals("ready", ready.result)
            return ready to requireNotNull(camera.file(ready))
        }
        val (ticket, photo) = fixtureTicket()
        Audience.entries.forEach { audience ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(audience.label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
            val layout = layouts.single()
            assertEquals(audience.label, layout.layoutInput.text.text)
            assertEquals("Mode choices should keep complete labels on one line", 1, layout.lineCount)
            assertTrue(layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
            assertFalse(layout.isLineEllipsized(0))
        }
        compose.onNodeWithText("成人模式").performScrollTo()
        screenshot("p5-waiting-mode-choice", "成人模式")
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并退出").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("放下草稿并退出").assertIsDisplayed()
        assertEquals(ticket, camera.pending())
        assertTrue(photo.isFile)
        screenshot("p5-waiting-mode-confirm", "放下草稿？")
        compose.onNodeWithText("返回选择模式").performClick()
        assertNull(model().controller.state.value.audience)
        assertEquals(username, model().controller.state.value.username)
        assertEquals(ticket, camera.pending())
        assertTrue(photo.isFile)
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并切换").assertIsDisplayed()
        assertNull(model().controller.state.value.audience)
        compose.onNodeWithText("返回选择模式").performClick()
        assertEquals(ticket, camera.pending())
        assertTrue(photo.isFile)
        compose.onNodeWithText("成人模式").performScrollTo().performClick()
        compose.onNodeWithText("放下草稿并切换").performClick()
        compose.waitUntil(5000) { model().controller.state.value.audience == Audience.ADULT && !model().observations.state.value.loading }
        assertNull(camera.pending())
        assertFalse(photo.exists())
        assertFalse(model().observations.state.value.editing)
        switchMode().performScrollTo().performClick()
        assertNull(model().controller.state.value.audience)
        val (matchingTicket, matchingPhoto) = fixtureTicket()
        compose.onNodeWithText("儿童模式").performScrollTo().performClick()
        compose.waitUntil(5000) { model().controller.state.value.audience == Audience.CHILD && model().observations.state.value.editing }
        tab("我的手帐").assertIsSelected()
        compose.onNodeWithText("跳过引入").assertDoesNotExist()
        assertEquals(matchingTicket, camera.pending())
        assertEquals(matchingPhoto, model().observations.state.value.photo)
        assertEquals(draftText, model().observations.state.value.note)
        assertTrue(matchingPhoto.isFile)
        compose.onNode(hasSetTextAction() and hasText("我的发现")).assertTextContains(draftText)
    }
}
