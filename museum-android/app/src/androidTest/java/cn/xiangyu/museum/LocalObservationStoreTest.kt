package cn.xiangyu.museum

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class LocalObservationStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ProgressKey("https://example.invalid", "TEST_LOCAL_OBSERVATION", Audience.CHILD)
    private val relic = FirstChapter.relics.first().id
    private lateinit var root: File
    private lateinit var isolated: Context
    private lateinit var prefix: String

    @Before fun createIsolatedFilesAndDatabases() {
        prefix = "observation_test_${UUID.randomUUID()}"
        root = File(context.filesDir, prefix).apply { check(mkdirs()) }
        isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
            override fun getCacheDir(): File = File(root, "cache").apply { check(mkdirs() || isDirectory) }
            override fun getDatabasePath(name: String): File = File(root, "databases/$name").also {
                check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory)
            }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                openOrCreateDatabase(name, mode, factory, null)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                                              errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
                val file = getDatabasePath(name)
                check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                return SQLiteDatabase.openOrCreateDatabase(file.path, factory, errorHandler)
            }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("${prefix}_$name", mode)
        }
    }

    @After fun deleteOnlyThisTestDirectory() {
        check(root.canonicalFile.parentFile == context.filesDir.canonicalFile && root.name == prefix)
        check(root.deleteRecursively())
        context.deleteSharedPreferences("${prefix}_puzzle_checkpoints")
    }

    private fun id() = UUID.randomUUID().toString()
    private fun capture(name: String = "${id()}.jpg", directory: File = File(isolated.cacheDir, "journal-capture")): File {
        check(directory.mkdirs() || directory.isDirectory)
        val file = File(directory, name)
        val bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.CYAN)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally { bitmap.recycle() }
        return file
    }

    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Invalid input must be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun recordsRecoverAfterReopeningAndDuplicateSaveRetainsTheOriginal() = runBlocking {
        val recordId = id()
        val original = SqliteLocalObservationStore(isolated).use { store ->
            val saved = store.save(key, recordId, relic, "  我发现纱很薄  ")
            assertEquals("我发现纱很薄", saved.note)
            assertNull(saved.photoName)
            assertTrue(saved.createdAt > 0)
            assertEquals(saved, store.save(key, recordId, relic, "第二次保存不改写"))
            assertEquals(listOf(saved), store.load(key))
            saved
        }
        SqliteLocalObservationStore(isolated).use { store ->
            assertEquals(listOf(original), store.load(key))
            assertNull(store.photo(key, original))
        }
    }

    @Test fun concurrentDuplicateSavesProduceOneRecord() = runBlocking {
        SqliteLocalObservationStore(isolated).use { store ->
            SqliteLocalObservationStore(isolated).use { reopened ->
                val recordId = id()
                val saved = listOf(store, reopened, store, reopened).map {
                    async { it.save(key, recordId, relic, "观察到轻薄的纱") }
                }.awaitAll()
                assertTrue(saved.all { it == saved.first() })
                assertEquals(1, store.load(key).size)
            }
        }
    }

    @Test fun accountsModesAndEndpointsAreIsolatedAndForeignDeletionDoesNothing() = runBlocking {
        val others = listOf(key.copy(username = "TEST_OTHER"), key.copy(audience = Audience.ADULT),
            key.copy(endpoint = "https://other.example.invalid"))
        assertEquals(4, (others + key).map(::localObservationScope).toSet().size)
        assertTrue(localObservationScope(key).matches(Regex("[0-9a-f]{64}")))
        SqliteLocalObservationStore(isolated).use { store ->
            val saved = store.save(key, id(), relic, "我的观察", capture())
            val photo = store.photo(key, saved)!!
            for (other in others) {
                assertTrue(store.load(other).isEmpty())
                assertFalse(store.delete(other, saved.id))
                assertNull(store.photo(other, saved))
            }
            assertTrue(photo.isFile)
            assertEquals(listOf(saved), store.load(key))
        }
    }

    @Test fun metadataAndInvalidOrOutOfDirectoryPhotosAreRejectedWithoutSaving() = runBlocking {
        val valid = capture()
        val invalid = File(valid.parentFile, "${id()}.jpg").apply { writeText("not an image") }
        val outside = capture(directory = File(isolated.cacheDir, "outside-capture"))
        val nested = capture(directory = File(valid.parentFile, "nested"))
        val wrongExtension = capture(name = "${id()}.png")
        val png = File(valid.parentFile, "${id()}.jpg")
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).let { bitmap ->
            try { png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        SqliteLocalObservationStore(isolated).use { store ->
            rejected { store.save(key, "../outside", relic, "观察") }
            rejected { store.save(key, "1-1-1-1-1", relic, "观察") }
            rejected { store.save(key, id(), "unknown-relic", "观察") }
            rejected { store.save(key, id(), relic, "  ") }
            rejected { store.save(key, id(), relic, "字".repeat(601)) }
            for (file in listOf(invalid, outside, nested, wrongExtension, png, valid.parentFile!!)) {
                rejected { store.save(key, id(), relic, "观察", file) }
                assertTrue("Rejected source is not deleted", file.exists())
            }
            assertTrue(store.load(key).isEmpty())
            assertFalse(File(isolated.filesDir, "journal-photos").exists())
        }
    }

    @Test fun validPhotoIsCopiedPrivatelyAndDuplicateSaveDoesNotReplaceOrDeleteIt() = runBlocking {
        val source = capture()
        SqliteLocalObservationStore(isolated).use { store ->
            val saved = store.save(key, id(), relic, "我看到了花叶纹样", source)
            val photo = store.photo(key, saved)!!
            assertEquals("${saved.id}.jpg", saved.photoName)
            assertEquals(File(isolated.filesDir, "journal-photos/${localObservationScope(key)}").canonicalFile, photo.parentFile)
            assertArrayEquals(source.readBytes(), photo.readBytes())
            assertTrue(source.isFile)
            assertEquals(saved, store.save(key, saved.id, relic, "重复点击", File(source.parentFile, "missing.jpg")))
            assertTrue(photo.isFile)
            assertTrue(store.delete(key, saved.id))
            assertFalse(photo.exists())
            assertTrue(source.exists())
            assertTrue(store.load(key).isEmpty())
            assertFalse(store.delete(key, saved.id))
            assertNull(store.photo(key, saved.copy(photoName = "../../outside.jpg")))
        }
    }

    @Test fun failedInsertRemovesOnlyItsNewCopyAndPreservesExistingPhotos() = runBlocking {
        SqliteLocalObservationStore(isolated).use { store ->
            val saved = store.save(key, id(), relic, "已经保存的观察", capture())
            val oldPhoto = store.photo(key, saved)!!
            isolated.openOrCreateDatabase("museum_observations.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER reject_new_observation BEFORE INSERT ON observations BEGIN SELECT RAISE(ABORT,'test rejection'); END")
            }
            val nextId = id()
            try {
                store.save(key, nextId, relic, "这次插入会失败", capture())
                fail("Failed insert must not be reported as saved")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertFalse(File(oldPhoto.parentFile, "$nextId.jpg").exists())
            assertTrue(oldPhoto.isFile)
            assertEquals(listOf(saved), store.load(key))
        }
    }

    @Test fun savingAndDeletingObservationNeverChangesTasksOrStoryProgress() = runBlocking {
        SqliteProgressStore(isolated).use { tasks ->
            StoryChapterStore(isolated).use { story ->
                val scope = storyChapterScope(key.endpoint, key.username)
                val beforeTasks = tasks.complete(key, FirstChapter.tasks.first().id)
                assertTrue(story.save(scope, 1))
                assertTrue(story.watched(scope, 2))
                val beforeStory = story.load(scope)
                SqliteLocalObservationStore(isolated).use { observations ->
                    val saved = observations.save(key, id(), relic, "照片只是我的观察记录", capture())
                    assertEquals(beforeTasks, tasks.load(key))
                    assertEquals(beforeStory, story.load(scope))
                    assertTrue(observations.delete(key, saved.id))
                    assertEquals(beforeTasks, tasks.load(key))
                    assertEquals(beforeStory, story.load(scope))
                    assertFalse(story.load(scope).stamped)
                }
            }
        }
    }
}
