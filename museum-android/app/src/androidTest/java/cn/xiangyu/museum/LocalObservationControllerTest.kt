package cn.xiangyu.museum

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class LocalObservationControllerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val key = ProgressKey("https://example.invalid", "TEST_CAMERA_OBSERVATION", Audience.CHILD)
    private lateinit var databaseName: String
    private lateinit var preferencesName: String
    private lateinit var photoRoot: File
    private lateinit var store: SqliteLocalObservationStore
    private lateinit var camera: LocalJournalCamera
    private val jobs = mutableListOf<Job>()
    private val ownedFiles = mutableSetOf<File>()
    private val identities = mutableSetOf<ProgressKey>()

    @Before fun createIndependentDatabasePhotosAndCameraTicket() {
        val id = UUID.randomUUID().toString()
        databaseName = "test-camera-observations-$id.db"
        preferencesName = "test-camera-ticket-$id"
        photoRoot = File(context.filesDir, "test-camera-photos-$id")
        store = SqliteLocalObservationStore(context, databaseName, photoRoot)
        camera = LocalJournalCamera(context, preferencesName)
    }

    @After fun removeOnlyOwnedTicketsFilesDatabaseAndPreferences() = runBlocking {
        jobs.forEach { it.cancel() }
        withTimeout(5000) { jobs.joinAll() }
        camera.pending()?.let { camera.discard(it.id) }
        for (identity in identities) {
            store.load(identity).forEach { record -> store.photo(identity, record)?.let { ownedFiles += it } }
        }
        store.close()
        for (file in ownedFiles) assertTrue("Only this test's registered files are removed", !file.exists() || file.delete())
        for (identity in identities) {
            val directory = File(photoRoot, localObservationScope(identity))
            assertTrue(!directory.exists() || directory.delete())
        }
        assertEquals(context.filesDir.canonicalFile, photoRoot.canonicalFile.parentFile)
        assertTrue(!photoRoot.exists() || photoRoot.delete())
        context.deleteDatabase(databaseName)
        context.deleteSharedPreferences(preferencesName)
        Unit
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private suspend fun await(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(20)
    }

    private suspend fun settle() = withTimeout(5000) {
        jobs.flatMap { it.children.toList() }.joinAll()
    }

    private suspend fun controller(identity: ProgressKey? = key, bind: Boolean = true,
                                   ticketCamera: LocalJournalCamera = camera): LocalObservationController {
        val job = SupervisorJob().also { jobs += it }
        val controller = LocalObservationController(context, store,
            CoroutineScope(job + Dispatchers.Main.immediate), ticketCamera)
        identity?.let { identities += it }
        if (bind) {
            main { controller.bind(identity) }
            await { !controller.state.value.loading }
        }
        return controller
    }

    private fun startCapture(controller: LocalObservationController): JournalCapture {
        var ticket: JournalCapture? = null
        main {
            controller.takePhoto { id, uri ->
                ticket = requireNotNull(camera.pending())
                assertEquals(ticket!!.id, id)
                assertEquals("content", uri.scheme)
                assertEquals("${context.packageName}.journal-camera", uri.authority)
            }
        }
        return requireNotNull(ticket).also { ownedFiles += requireNotNull(camera.file(it)) }
    }

    private fun jpeg(file: File, width: Int = 24, height: Int = 16) {
        ownedFiles += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.RED)
            canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
    }

    @Test fun cancellingOrMissingCameraKeepsTheTextAlternativeWithoutSaving() = runBlocking {
        val controller = controller()
        main { controller.newRecord(); controller.note("我看到衣料很薄。") }
        val cancelled = startCapture(controller)
        jpeg(requireNotNull(camera.file(cancelled)))
        main { controller.cameraResult(cancelled.id, false) }
        settle()
        assertEquals("cancelled", camera.pending()?.result)
        assertNull(controller.state.value.photo)
        assertTrue(controller.state.value.editing)
        assertEquals("我看到衣料很薄。", controller.state.value.note)
        assertTrue(store.load(key).isEmpty())
        assertFalse(requireNotNull(camera.file(cancelled)).exists())

        main { controller.takePhoto { _, _ -> throw ActivityNotFoundException("TEST_ONLY_NO_CAMERA") } }
        assertNull(camera.pending())
        assertTrue(controller.state.value.editing)
        assertEquals("我看到衣料很薄。", controller.state.value.note)
        assertNull(controller.state.value.photo)
        assertTrue(store.load(key).isEmpty())
        main { controller.save() }
        await { !controller.state.value.busy && !controller.state.value.editing }
        assertEquals(1, store.load(key).size)
        assertNull(store.load(key).single().photoName)
    }

    @Test fun successfulCaptureOnlyPreviewsUntilExplicitSaveAndRepeatedSaveIsGuarded() = runBlocking {
        val controller = controller()
        main { controller.newRecord(); controller.note("纹样让我想到花和叶。") }
        val capture = startCapture(controller)
        jpeg(requireNotNull(camera.file(capture)))
        main { controller.cameraResult(capture.id, true) }
        settle()
        assertEquals("ready", camera.pending()?.result)
        assertEquals(camera.file(capture), controller.state.value.photo)
        assertTrue(controller.state.value.editing)
        assertTrue(store.load(key).isEmpty())
        main { controller.relic(controller.state.value.relicId) }
        assertEquals(camera.file(capture), controller.state.value.photo)
        assertTrue(requireNotNull(camera.file(capture)).isFile)
        main { controller.cameraResult(capture.id, false) }
        settle()
        assertEquals("ready", camera.pending()?.result)
        assertEquals(camera.file(capture), controller.state.value.photo)

        main {
            controller.save()
            assertTrue(controller.state.value.busy)
            controller.save()
            controller.note("保存期间不应改写")
            controller.newRecord()
            controller.cancelDraft()
            assertEquals(capture.draftId, controller.state.value.draftId)
            assertEquals(capture.note, controller.state.value.note)
        }
        await { !controller.state.value.busy && !controller.state.value.editing }
        val saved = store.load(key).single()
        assertEquals(capture.draftId, saved.id)
        assertEquals(capture.note, saved.note)
        val photo = requireNotNull(store.photo(key, saved))
        ownedFiles += photo
        assertTrue(photo.isFile)
        assertEquals(File(photoRoot, localObservationScope(key)).canonicalFile, photo.canonicalFile.parentFile)
        assertNull(camera.pending())
        assertFalse(requireNotNull(camera.file(capture)).exists())
        main { controller.save() }
        assertEquals(saved, store.save(key, saved.id, saved.relicId, "重复提交不会改写"))
        assertEquals(listOf(saved), store.load(key))
    }

    @Test fun independentCameraAndControllerInstancesRestoreWaitingAndReadyTickets() = runBlocking {
        val original = controller()
        main { original.newRecord(); original.note("重建后还保留这点发现。") }
        val capture = startCapture(original)
        jobs.first().cancelAndJoin()

        val reopenedCamera = LocalJournalCamera(context, preferencesName)
        val waiting = controller(bind = false, ticketCamera = reopenedCamera)
        main { waiting.bind(null, restoring = true) }
        assertEquals(capture, reopenedCamera.pending())
        main { waiting.bind(null, waitingForMode = true) }
        assertEquals(capture, reopenedCamera.pending())
        assertFalse(waiting.state.value.editing)
        main { waiting.bind(key) }
        await { !waiting.state.value.loading }
        assertEquals(capture, reopenedCamera.pending())
        assertEquals(capture.draftId, waiting.state.value.draftId)
        assertEquals(capture.note, waiting.state.value.note)
        assertTrue(waiting.state.value.editing)
        assertNull(waiting.state.value.photo)
        jobs.last().cancelAndJoin()

        jpeg(requireNotNull(reopenedCamera.file(capture)))
        val callbackCamera = LocalJournalCamera(context, preferencesName)
        val beforeIdentityRestoration = controller(bind = false, ticketCamera = callbackCamera)
        main { beforeIdentityRestoration.cameraResult(capture.id, true) }
        settle()
        assertEquals("ready", callbackCamera.pending()?.result)
        assertFalse(beforeIdentityRestoration.state.value.editing)
        main { beforeIdentityRestoration.bind(null, restoring = true) }
        assertEquals("ready", callbackCamera.pending()?.result)
        main { beforeIdentityRestoration.bind(null, waitingForMode = true) }
        assertEquals("ready", callbackCamera.pending()?.result)
        assertFalse(beforeIdentityRestoration.state.value.editing)
        main { beforeIdentityRestoration.bind(key) }
        await { !beforeIdentityRestoration.state.value.loading }
        assertEquals(capture.note, beforeIdentityRestoration.state.value.note)
        assertEquals(camera.file(capture), beforeIdentityRestoration.state.value.photo)
        jobs.last().cancelAndJoin()

        val ready = controller(ticketCamera = LocalJournalCamera(context, preferencesName))
        assertEquals(capture.draftId, ready.state.value.draftId)
        assertEquals(capture.note, ready.state.value.note)
        assertTrue(requireNotNull(ready.state.value.photo).isFile)
        assertTrue(store.load(key).isEmpty())
    }

    @Test fun missingOrDamagedReadyPhotosRecoverAsExplicitTextOnlyDrafts() = runBlocking {
        for (damaged in listOf(false, true)) {
            val original = controller()
            main { original.newRecord(); original.note("测试观察：照片失效时保留这点发现。") }
            val capture = startCapture(original)
            val file = requireNotNull(camera.file(capture))
            jpeg(file)
            main { original.cameraResult(capture.id, true) }
            settle()
            assertEquals("ready", camera.pending()?.result)
            jobs.last().cancelAndJoin()
            if (damaged) file.writeText("TEST_ONLY_DAMAGED_PHOTO") else assertTrue(file.delete())

            val restored = controller(ticketCamera = LocalJournalCamera(context, preferencesName))
            assertTrue(restored.state.value.editing)
            assertEquals(capture.draftId, restored.state.value.draftId)
            assertEquals(capture.relicId, restored.state.value.relicId)
            assertEquals(capture.note, restored.state.value.note)
            assertNull("An unavailable cache photo must not be offered for saving", restored.state.value.photo)
            assertEquals(capture.copy(result = "unavailable"), camera.pending())
            assertFalse(file.exists())
            assertEquals("照片暂存已不可用，观察文字还在。可以重新拍照，或直接收进手帐。", restored.state.value.message)
            assertTrue(store.load(key).none { it.id == capture.draftId })
            assertTrue(restored.hasUnsavedDraft())
            jobs.last().cancelAndJoin()
            val reopened = controller(ticketCamera = LocalJournalCamera(context, preferencesName))
            assertEquals(capture.note, reopened.state.value.note)
            assertNull(reopened.state.value.photo)
            assertEquals(restored.state.value.message, reopened.state.value.message)
            main { reopened.cameraResult(capture.id, true) }
            settle()
            assertNull(reopened.state.value.photo)
            assertEquals(capture.copy(result = "unavailable"), camera.pending())
            main { reopened.save() }
            await { !reopened.state.value.busy && !reopened.state.value.editing }
            val saved = store.load(key).single { it.id == capture.draftId }
            assertEquals(capture.note, saved.note)
            assertNull(saved.photoName)
            assertNull(camera.pending())
        }
    }

    @Test fun lateResultsCannotCrossIdentityBoundariesOrReplaceANewerDraftCapture() = runBlocking {
        val controller = controller()
        val others = listOf(key.copy(username = "TEST_CAMERA_OTHER"), key.copy(audience = Audience.ADULT),
            key.copy(endpoint = "https://other.example.invalid"))
        for (other in others) {
            identities += other
            main { controller.bind(key) }
            await { !controller.state.value.loading }
            main { controller.newRecord(); controller.note("旧身份的发现") }
            val old = startCapture(controller)
            jpeg(requireNotNull(camera.file(old)))
            main { controller.bind(other) }
            await { !controller.state.value.loading }
            main { controller.newRecord(); controller.note("新身份的发现"); controller.cameraResult(old.id, true) }
            settle()
            assertEquals("新身份的发现", controller.state.value.note)
            assertNull(controller.state.value.photo)
            assertNull(camera.pending())
            assertTrue(store.load(other).isEmpty())
            assertFalse(requireNotNull(camera.file(old)).exists())
        }

        main { controller.bind(key) }
        await { !controller.state.value.loading }
        main { controller.newRecord(); controller.note("第一张草稿") }
        val old = startCapture(controller)
        jpeg(requireNotNull(camera.file(old)))
        main { controller.cancelDraft(); controller.newRecord(); controller.note("新的草稿") }
        val current = startCapture(controller)
        jpeg(requireNotNull(camera.file(current)))
        main { controller.cameraResult(old.id, true) }
        settle()
        assertEquals(current.id, camera.pending()?.id)
        assertEquals("waiting", camera.pending()?.result)
        assertEquals(current.draftId, controller.state.value.draftId)
        assertNull(controller.state.value.photo)
        main { controller.cameraResult(current.id, true) }
        settle()
        main { controller.cameraResult(old.id, false) }
        settle()
        assertEquals(current.id, camera.pending()?.id)
        assertEquals("ready", camera.pending()?.result)
        assertEquals(camera.file(current), controller.state.value.photo)
        assertEquals("新的草稿", controller.state.value.note)
        assertTrue(store.load(key).isEmpty())
        main { controller.bind(null, waitingForMode = true) }
        assertNull(camera.pending())
        assertFalse(controller.state.value.editing)
        assertFalse(requireNotNull(camera.file(current)).exists())
    }

    @Test fun providerSharesOnlyCaptureCacheAndRejectsSavedPhotosAndOtherFiles() = runBlocking {
        identities += key
        val capture = camera.begin(key, UUID.randomUUID().toString(), FirstChapter.relics.first().id, "测试照片")
        val source = requireNotNull(camera.file(capture))
        jpeg(source)
        val uri = camera.uri(capture)
        context.contentResolver.openFileDescriptor(uri, "r").use { assertTrue(requireNotNull(it).statSize > 0) }
        val provider = requireNotNull(context.packageManager.resolveContentProvider(uri.authority!!, 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val saved = store.save(key, UUID.randomUUID().toString(), capture.relicId, capture.note, source)
        val savedPhoto = requireNotNull(store.photo(key, saved)).also { ownedFiles += it }
        val outside = File(context.cacheDir, "test-camera-outside-${UUID.randomUUID()}.jpg")
        val privateFile = File(context.filesDir, "test-camera-private-${UUID.randomUUID()}.jpg")
        jpeg(outside)
        jpeg(privateFile)
        for (file in listOf(savedPhoto, outside, privateFile)) {
            try {
                FileProvider.getUriForFile(context, uri.authority!!, file)
                fail("Provider must reject files outside journal-capture")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun specifiedRelicEntryDoesNotOverwriteAnExistingDraft() = runBlocking {
        val controller = controller()
        val first = FirstChapter.relics.first().id
        val second = FirstChapter.relics.last().id
        main { controller.newRecord("TEST_UNKNOWN_RELIC") }
        assertFalse(controller.state.value.editing)
        main { controller.newRecord(second); controller.note("我看到花叶纹样。") }
        val original = controller.state.value
        assertEquals(second, original.relicId)
        assertTrue(controller.hasUnsavedDraft())
        main { controller.newRecord(second) }
        assertEquals(original, controller.state.value)
        main { controller.newRecord(first) }
        assertEquals(original.draftId, controller.state.value.draftId)
        assertEquals(second, controller.state.value.relicId)
        assertEquals(original.note, controller.state.value.note)
        assertNotNull(controller.state.value.message)
        main { controller.newRecord("TEST_UNKNOWN_RELIC") }
        assertEquals(original.draftId, controller.state.value.draftId)
        assertTrue(store.load(key).isEmpty())
        main { controller.cancelDraft(); controller.newRecord(first) }
        assertEquals(first, controller.state.value.relicId)
        assertNotEquals(original.draftId, controller.state.value.draftId)
        assertEquals("", controller.state.value.note)
        assertFalse(controller.hasUnsavedDraft())
        assertNull(controller.state.value.pendingRelicId)
    }

    @Test fun changingAPhotoRelicNeedsConfirmationAndPreservesTextAndTaskProgress() = runBlocking {
        val taskDatabase = "test-camera-tasks-${UUID.randomUUID()}.db"
        val tasks = SqliteProgressStore(context, taskDatabase)
        try {
            val previousTasks = tasks.complete(key, FirstChapter.tasks.first().id)
            val controller = controller()
            val first = FirstChapter.relics.first().id
            val second = FirstChapter.relics.last().id
            main { controller.newRecord(first); controller.note("照片和文字来自这次观察。") }
            val capture = startCapture(controller)
            val capturedFile = requireNotNull(camera.file(capture))
            jpeg(capturedFile)
            main { controller.cameraResult(capture.id, true) }
            settle()
            main { controller.note("") }
            assertTrue(controller.hasUnsavedDraft())
            main { controller.note(capture.note) }
            main { controller.relic(first) }
            assertNull(controller.state.value.pendingRelicId)
            main { controller.relic("TEST_UNKNOWN_RELIC") }
            assertEquals(first, controller.state.value.relicId)
            main { controller.relic(second) }
            assertEquals(first, controller.state.value.relicId)
            assertEquals(second, controller.state.value.pendingRelicId)
            assertEquals(capturedFile, controller.state.value.photo)
            assertTrue(capturedFile.isFile)
            assertEquals(capture.id, camera.pending()?.id)
            main { controller.keepRelic() }
            assertNull(controller.state.value.pendingRelicId)
            assertEquals(first, controller.state.value.relicId)
            assertEquals(capture.note, controller.state.value.note)
            assertEquals(capturedFile, controller.state.value.photo)
            assertTrue(capturedFile.isFile)
            main { controller.newRecord(second) }
            assertEquals(capture.draftId, controller.state.value.draftId)
            assertEquals(first, controller.state.value.relicId)
            assertEquals(capturedFile, controller.state.value.photo)
            main { controller.relic(second); controller.confirmRelicChange() }
            assertNull(controller.state.value.pendingRelicId)
            assertEquals(second, controller.state.value.relicId)
            assertEquals(capture.note, controller.state.value.note)
            assertNull(controller.state.value.photo)
            assertNull(camera.pending())
            assertFalse(capturedFile.exists())
            assertTrue(controller.hasUnsavedDraft())
            main {
                controller.save()
                assertTrue(controller.state.value.busy)
                controller.relic(first)
                controller.newRecord(first)
                controller.confirmRelicChange()
                assertEquals(second, controller.state.value.relicId)
            }
            await { !controller.state.value.busy && !controller.state.value.editing }
            val saved = store.load(key).single()
            assertEquals(second, saved.relicId)
            assertEquals(capture.note, saved.note)
            assertNull(saved.photoName)
            assertFalse(controller.hasUnsavedDraft())
            assertEquals(previousTasks, tasks.load(key))
        } finally {
            tasks.close()
            context.deleteDatabase(taskDatabase)
        }
    }

    @Test fun changingRelicWithAWaitingCaptureRequiresConfirmationAndRejectsItsLateResult() = runBlocking {
        val controller = controller()
        val first = FirstChapter.relics.first().id
        val second = FirstChapter.relics.last().id
        main { controller.newRecord(first) }
        assertFalse(controller.hasUnsavedDraft())
        val capture = startCapture(controller)
        val capturedFile = requireNotNull(camera.file(capture))
        assertTrue(controller.hasUnsavedDraft())
        main { controller.relic(second) }
        assertEquals(first, controller.state.value.relicId)
        assertEquals(second, controller.state.value.pendingRelicId)
        assertEquals(capture, camera.pending())
        assertTrue(capturedFile.isFile)
        main { controller.keepRelic() }
        assertNull(controller.state.value.pendingRelicId)
        assertEquals(capture, camera.pending())
        main { controller.relic(second); controller.confirmRelicChange() }
        assertEquals(second, controller.state.value.relicId)
        assertNull(camera.pending())
        assertFalse(capturedFile.exists())
        assertFalse(controller.hasUnsavedDraft())
        main { controller.cameraResult(capture.id, true) }
        settle()
        assertEquals(second, controller.state.value.relicId)
        assertNull(controller.state.value.photo)
        assertNull(controller.state.value.pendingRelicId)
        assertTrue(store.load(key).isEmpty())
    }

    @Test fun discardGuardUsesCurrentDraftContentAndClearsConfirmationsOnCancelAndUnbind() = runBlocking {
        val controller = controller()
        main { controller.newRecord(); controller.note("   ") }
        assertFalse(controller.hasUnsavedDraft())
        main { controller.requestCancelDraft() }
        assertFalse(controller.state.value.editing)
        assertFalse(controller.state.value.confirmDiscard)

        main { controller.newRecord() }
        val unrelated = camera.begin(key, UUID.randomUUID().toString(), FirstChapter.relics.first().id, "测试无关票据")
        val unrelatedFile = requireNotNull(camera.file(unrelated)).also { ownedFiles += it }
        assertFalse(controller.hasUnsavedDraft())
        main { controller.requestCancelDraft() }
        assertFalse(controller.state.value.editing)
        assertFalse(controller.state.value.confirmDiscard)
        assertNull(camera.pending())
        assertFalse(unrelatedFile.exists())

        main { controller.newRecord() }
        val cancelled = startCapture(controller)
        assertTrue(controller.hasUnsavedDraft())
        main { controller.cameraResult(cancelled.id, false) }
        settle()
        assertEquals("cancelled", camera.pending()?.result)
        assertFalse(controller.hasUnsavedDraft())
        main { controller.relic(FirstChapter.relics.last().id) }
        assertEquals(FirstChapter.relics.last().id, controller.state.value.relicId)
        assertNull(controller.state.value.pendingRelicId)
        assertNull(camera.pending())
        main { controller.requestCancelDraft() }
        assertFalse(controller.state.value.editing)
        assertFalse(controller.state.value.confirmDiscard)
        assertNull(camera.pending())

        main { controller.newRecord(); controller.note("这次观察还没有收好。") }
        val capture = startCapture(controller)
        main { controller.relic(FirstChapter.relics.last().id); controller.requestCancelDraft() }
        assertTrue(controller.state.value.confirmDiscard)
        assertNull(controller.state.value.pendingRelicId)
        assertTrue(controller.hasUnsavedDraft())
        main { controller.keepDraft() }
        assertFalse(controller.state.value.confirmDiscard)
        assertEquals(capture.draftId, controller.state.value.draftId)
        assertEquals(capture.note, controller.state.value.note)
        main { controller.relic(FirstChapter.relics.last().id) }
        assertNotNull(controller.state.value.pendingRelicId)
        main { controller.cancelDraft() }
        assertFalse(controller.hasUnsavedDraft())
        assertNull(controller.state.value.pendingRelicId)
        assertNull(camera.pending())
        main { controller.newRecord(); controller.note("切换身份前的观察。") }
        val beforeUnbind = startCapture(controller)
        main { controller.relic(FirstChapter.relics.last().id); controller.bind(null) }
        assertFalse(controller.hasUnsavedDraft())
        assertFalse(controller.state.value.editing)
        assertNull(controller.state.value.pendingRelicId)
        assertFalse(controller.state.value.confirmDiscard)
        assertNull(camera.pending())
        assertFalse(requireNotNull(camera.file(beforeUnbind)).exists())
    }

    @Test fun unboundCaptureGuardFindsOwnModeForWaitingAndReadableReadyPhotosWithoutChangingTickets() = runBlocking {
        for (audience in Audience.entries) {
            val identity = key.copy(audience = audience)
            val capture = camera.begin(identity, UUID.randomUUID().toString(), FirstChapter.relics.first().id, "")
            val photo = requireNotNull(camera.file(capture)).also { ownedFiles += it }
            val controller = controller(bind = false)
            main { controller.bind(null, restoring = true); controller.bind(null, waitingForMode = true) }
            assertFalse(controller.state.value.editing)
            assertFalse(controller.hasUnsavedDraft())
            assertEquals(audience, controller.pendingDraftAudience(key.endpoint, key.username))
            assertNull(controller.pendingDraftAudience(key.endpoint, "TEST_OTHER_CAPTURE_ACCOUNT"))
            assertNull(controller.pendingDraftAudience("https://other.example.invalid", key.username))
            assertEquals(capture, camera.pending())
            assertTrue(photo.isFile)

            jpeg(photo)
            main { controller.cameraResult(capture.id, true) }
            settle()
            val ready = requireNotNull(camera.pending())
            assertEquals("ready", ready.result)
            main { controller.bind(null, waitingForMode = true) }
            assertFalse(controller.state.value.editing)
            assertEquals(audience, controller.pendingDraftAudience(key.endpoint, key.username))
            assertNull(controller.pendingDraftAudience(key.endpoint, "TEST_OTHER_CAPTURE_ACCOUNT"))
            assertNull(controller.pendingDraftAudience("https://other.example.invalid", key.username))
            assertEquals(ready, camera.pending())
            assertTrue(photo.isFile)
            photo.writeText("TEST_ONLY_UNREADABLE_CAPTURE")
            assertNull(controller.pendingDraftAudience(key.endpoint, key.username))
            assertEquals(ready, camera.pending())
            assertTrue(photo.isFile)
            jpeg(photo)
            assertEquals(audience, controller.pendingDraftAudience(key.endpoint, key.username))
            camera.discard(capture.id)
        }
    }

    @Test fun unboundCancelledCaptureOnlyProtectsNonBlankTextWithinItsOwnAccountAndAddress() = runBlocking {
        val identity = key.copy(audience = Audience.ADULT)
        val controller = controller(bind = false)
        for (note in listOf("", "   ", "拍照取消后，这段文字还没有收好。")) {
            val capture = camera.begin(identity, UUID.randomUUID().toString(), FirstChapter.relics.last().id, note)
            val photo = requireNotNull(camera.file(capture)).also { ownedFiles += it }
            main { controller.cameraResult(capture.id, false) }
            settle()
            main { controller.bind(null, waitingForMode = true) }
            val cancelled = requireNotNull(camera.pending())
            assertEquals("cancelled", cancelled.result)
            assertFalse(photo.exists())
            assertFalse(controller.state.value.editing)
            assertFalse(controller.hasUnsavedDraft())
            assertEquals(if (note.isBlank()) null else Audience.ADULT,
                controller.pendingDraftAudience(key.endpoint, key.username))
            assertNull(controller.pendingDraftAudience(key.endpoint, "TEST_OTHER_CAPTURE_ACCOUNT"))
            assertNull(controller.pendingDraftAudience("https://other.example.invalid", key.username))
            assertEquals(cancelled, camera.pending())
            camera.discard(capture.id)
        }
        assertNull(controller.pendingDraftAudience(key.endpoint, key.username))
    }

    @Test fun previewSamplingBoundsMemoryAndAppliesExifRotationAndMirror() {
        val source = File(context.cacheDir, "test-camera-preview-${UUID.randomUUID()}.jpg")
        jpeg(source, 2400, 800)
        assertTrue(journalPhotoReadable(source))
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        requireNotNull(readJournalPhoto(source)).let { bitmap ->
            try {
                assertEquals(400, bitmap.width)
                assertEquals(1200, bitmap.height)
                assertTrue(Color.red(bitmap.getPixel(200, 100)) > 200)
                assertTrue(Color.blue(bitmap.getPixel(200, 1100)) > 200)
            } finally { bitmap.recycle() }
        }
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString())
            saveAttributes()
        }
        requireNotNull(readJournalPhoto(source)).let { bitmap ->
            try {
                assertEquals(1200, bitmap.width)
                assertEquals(400, bitmap.height)
                assertTrue(Color.blue(bitmap.getPixel(100, 200)) > 200)
                assertTrue(Color.red(bitmap.getPixel(1100, 200)) > 200)
            } finally { bitmap.recycle() }
        }
        source.writeText("TEST_ONLY_INVALID_IMAGE")
        assertFalse(journalPhotoReadable(source))
        assertNull(readJournalPhoto(source))
    }
}
