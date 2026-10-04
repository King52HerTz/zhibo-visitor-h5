package cn.xiangyu.museum

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/**
 * Explicit staging only: instrument this method with -e p5Stage prepare, force-stop the
 * target application externally, then run verify and finally cleanup. The ordinary suite
 * skips this fixture. Activity recreation is not used as evidence of a new process.
 * Only fixture username/endpoint/record ID/PIDs are saved in private test preferences;
 * credentials remain in the application's normal encrypted login store.
 */
class P5OfflineJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val marker get() = context.getSharedPreferences("p5_offline_journey_test", Context.MODE_PRIVATE)
    private val note = "TEST_P5_观察：衣料轻薄。"
    private val taskId = FirstChapter.tasks.first().id

    private data class Fixture(val username: String, val endpoint: String, val pid: Int, val recordId: String?) {
        fun key(audience: Audience) = ProgressKey(endpoint, username, audience)
    }

    private fun fixture(): Fixture {
        val username = requireNotNull(marker.getString("username", null))
        val endpoint = requireNotNull(marker.getString("endpoint", null))
        require(username.startsWith("TEST_P5_OFFLINE_"))
        UUID.fromString(username.removePrefix("TEST_P5_OFFLINE_"))
        require(endpoint.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}")))
        require(java.net.URI(endpoint).port in 1..65535)
        return Fixture(username, endpoint, marker.getInt("prepare_pid", 0), marker.getString("record_id", null))
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private suspend fun await(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) delay(25)
    }

    private suspend fun withActivity(action: suspend (MuseumViewModel) -> Unit) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            lateinit var model: MuseumViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MuseumViewModel::class.java] }
            await { !model.controller.state.value.restoring }
            action(model)
        } finally { scenario.close() }
    }

    private suspend fun settled(model: MuseumViewModel) {
        await {
            !model.controller.state.value.chatBusy && !model.controller.state.value.authBusy &&
                !model.progress.state.value.loading && !model.progress.state.value.syncing &&
                !model.observations.state.value.loading && !model.observations.state.value.busy
        }
    }

    @Test fun stagedForceStopOfflineJourney() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("p5Stage")
        assumeTrue("Only explicit prepare/verify/cleanup commands may run this fixture",
            stage in setOf("prepare", "verify", "cleanup"))
        when (stage) {
            "prepare" -> prepare()
            "verify" -> verify()
            "cleanup" -> cleanup()
            else -> error("Unsupported staging command")
        }
    }

    private suspend fun prepare() {
        assertFalse("Clean the prior owned staging fixture before preparing another", marker.contains("username"))
        withActivity { model ->
            assertNull("Prepare requires a logged-out app; never replace another user's login", model.controller.state.value.username)
            TestMuseumServer().use { server ->
                server.progressOffline = true
                val username = "TEST_P5_OFFLINE_${UUID.randomUUID()}"
                assertTrue(marker.edit().putString("username", username).putString("endpoint", server.endpoint)
                    .putInt("prepare_pid", Process.myPid()).putString("stage", "preparing").commit())
                main { model.controller.authenticate(server.endpoint, username, "TEST_ONLY_PASSWORD", false) }
                await { model.controller.state.value.username == username }
                main { model.controller.selectAudience(Audience.CHILD) }
                settled(model)
                main { model.progress.complete(taskId) }
                await { taskId in model.progress.state.value.progress.pending && !model.progress.state.value.syncing }
                main { model.observations.newRecord(); model.observations.note(note); model.observations.save() }
                await { !model.observations.state.value.editing && model.observations.state.value.records.size == 1 }
                val record = model.observations.state.value.records.single()
                assertEquals(note, record.note)
                assertNull(record.photoName)
                assertTrue(marker.edit().putString("record_id", record.id).commit())
                StoryChapterStore(context).use { story ->
                    val scope = storyChapterScope(server.endpoint, username)
                    assertEquals(StoryChapterProgress(), story.load(scope))
                    assertTrue(story.save(scope, 1))
                    assertEquals(StoryChapterProgress(mask = 1), story.load(scope))
                }
                assertFalse(server.requests.any { it.first == "/mobile/chat/stream" || it.first == "/mobile/sessions" })
            }
            // The fixture server is now closed. Login remains saved for external force-stop.
            assertTrue(marker.edit().putString("stage", "prepared").commit())
        }
    }

    private suspend fun verify() {
        val fixture = fixture()
        assertEquals("prepared", marker.getString("stage", null))
        assertTrue("Prepare and verify must execute in different real processes", fixture.pid > 0 && fixture.pid != Process.myPid())
        requireNotNull(fixture.recordId)
        withActivity { model ->
            assertEquals(fixture.username, model.controller.state.value.username)
            assertEquals(fixture.endpoint, model.controller.state.value.endpoint)
            assertNull(model.controller.state.value.audience)
            assertTrue(model.controller.state.value.messages.isEmpty())
            assertFalse(model.controller.state.value.chatBusy)
            assertFalse(model.observations.state.value.editing)
            main { model.controller.selectAudience(Audience.CHILD) }
            await { model.observations.state.value.records.any { it.id == fixture.recordId } }
            settled(model)
            assertChildRestored(model, fixture)
            assertNotNull("Closed server produces an ordinary connection error without logout", model.controller.state.value.error)
            main { model.controller.selectAudience(Audience.ADULT) }
            await { model.observations.state.value.records.isEmpty() }
            settled(model)
            assertEquals(fixture.username, model.controller.state.value.username)
            assertEquals(Audience.ADULT, model.controller.state.value.audience)
            assertTrue(model.progress.state.value.progress.completed.isEmpty())
            assertTrue(model.observations.state.value.records.isEmpty())
            assertTrue(model.controller.state.value.messages.isEmpty())
            assertChildFilesUnchanged(fixture)
            main { model.controller.selectAudience(Audience.CHILD) }
            await { model.observations.state.value.records.any { it.id == fixture.recordId } }
            settled(model)
            assertChildRestored(model, fixture)
            assertTrue(marker.edit().putInt("verify_pid", Process.myPid()).putString("stage", "verified").commit())
        }
    }

    private fun assertChildRestored(model: MuseumViewModel, fixture: Fixture) {
        assertEquals(fixture.username, model.controller.state.value.username)
        assertEquals(Audience.CHILD, model.controller.state.value.audience)
        assertEquals(setOf(taskId), model.progress.state.value.progress.completed)
        assertEquals(setOf(taskId), model.progress.state.value.progress.pending)
        val record = model.observations.state.value.records.single()
        assertEquals(fixture.recordId, record.id)
        assertEquals(note, record.note)
        assertNull(record.photoName)
        assertFalse(model.observations.state.value.editing)
        StoryChapterStore(context).use {
            assertEquals(StoryChapterProgress(mask = 1), it.load(storyChapterScope(fixture.endpoint, fixture.username)))
        }
    }

    private suspend fun assertChildFilesUnchanged(fixture: Fixture) {
        SqliteLocalObservationStore(context).use {
            val record = it.load(fixture.key(Audience.CHILD)).single()
            assertEquals(fixture.recordId, record.id)
            assertEquals(note, record.note)
            assertNull(record.photoName)
        }
        SqliteProgressStore(context).use {
            assertEquals(TaskProgress(setOf(taskId), setOf(taskId)), it.load(fixture.key(Audience.CHILD)))
        }
        StoryChapterStore(context).use {
            assertEquals(StoryChapterProgress(mask = 1), it.load(storyChapterScope(fixture.endpoint, fixture.username)))
        }
    }

    private suspend fun cleanup() {
        if (!marker.contains("username")) {
            context.deleteSharedPreferences("p5_offline_journey_test")
            return
        }
        val fixture = fixture()
        withActivity { model ->
            if (model.controller.state.value.username == fixture.username && model.controller.state.value.endpoint == fixture.endpoint) {
                main { model.controller.logout() }
                await { model.controller.state.value.username == null && !model.controller.state.value.authBusy }
            }
            if (context.getDatabasePath("museum_observations.db").isFile) SqliteLocalObservationStore(context).use { store ->
                for (audience in Audience.entries) {
                    val key = fixture.key(audience)
                    for (record in store.load(key)) {
                        assertNull("This fixture creates only text observations", record.photoName)
                        assertTrue(store.delete(key, record.id))
                    }
                    assertTrue(store.load(key).isEmpty())
                }
            }
            val progressFile = context.getDatabasePath("museum_progress.db")
            if (progressFile.isFile) SQLiteDatabase.openDatabase(progressFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.delete("tasks", "endpoint=? AND username=?", arrayOf(fixture.endpoint, fixture.username))
                it.rawQuery("SELECT COUNT(*) FROM tasks WHERE endpoint=? AND username=?", arrayOf(fixture.endpoint, fixture.username)).use { rows ->
                    assertTrue(rows.moveToFirst()); assertEquals(0, rows.getInt(0))
                }
            }
            val storyFile = context.getDatabasePath("museum_story.db")
            if (storyFile.isFile) SQLiteDatabase.openDatabase(storyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                val scope = storyChapterScope(fixture.endpoint, fixture.username)
                it.delete("chapter", "scope=?", arrayOf(scope))
                it.rawQuery("SELECT COUNT(*) FROM chapter WHERE scope=?", arrayOf(scope)).use { rows ->
                    assertTrue(rows.moveToFirst()); assertEquals(0, rows.getInt(0))
                }
            }
            context.deleteSharedPreferences("p5_offline_journey_test")
        }
    }
}
