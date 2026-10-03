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
        main { controller.newRecord(); controller.note("新的草稿") }
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
