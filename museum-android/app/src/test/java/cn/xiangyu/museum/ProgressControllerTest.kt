package cn.xiangyu.museum

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class MemoryProgressStore : ProgressStore {
    val records = mutableMapOf<ProgressKey, TaskProgress>()
    override suspend fun load(key: ProgressKey) = records[key] ?: TaskProgress()
    override suspend fun complete(key: ProgressKey, taskId: String): TaskProgress {
        val old = load(key)
        return old.copy(completed = old.completed + taskId, pending = old.pending + taskId).also { records[key] = it }
    }
    override suspend fun merge(key: ProgressKey, remote: Set<String>): TaskProgress {
        val old = load(key)
        return TaskProgress(old.completed + remote, old.pending - remote).also { records[key] = it }
    }
}

class ProgressService : MuseumProgressApi {
    var offline = false
    var gate: CompletableDeferred<Unit>? = null
    val records = mutableMapOf<String, Set<String>>()
    override suspend fun syncProgress(login: LoginSession, audience: Audience, completed: Set<String>): Set<String> {
        if (offline) throw ApiFailure(message = "test offline")
        val key = login.username + audience.name
        val result = records[key].orEmpty() + completed
        gate?.await()
        records[key] = result
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressControllerTest {
    private val a = LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid")
    private val b = LoginSession("b", "TEST_ONLY_TOKEN", "https://example.invalid")
    private val light = "clothes-light-v1"
    private val pattern = "clothes-pattern-v1"

    @Test fun offlineCompletionSurvivesRestartAndSyncDoesNotLoseCloudProgress() = runTest {
        val api = ProgressService().apply { offline = true }
        val store = MemoryProgressStore()
        val first = ProgressController(api, store, backgroundScope)
        first.bind(a, Audience.CHILD); runCurrent()
        first.complete(light); runCurrent()
        assertEquals(setOf(light), first.state.value.progress.pending)
        first.bind(null, null); runCurrent()
        val restored = ProgressController(api, store, backgroundScope)
        restored.bind(a, Audience.CHILD); runCurrent()
        assertEquals(setOf(light), restored.state.value.progress.completed)
        api.records["aCHILD"] = setOf(pattern)
        api.offline = false
        restored.sync(); runCurrent()
        assertEquals(setOf(light, pattern), restored.state.value.progress.completed)
        assertTrue(restored.state.value.progress.pending.isEmpty())
        restored.complete(light); runCurrent()
        assertTrue(restored.state.value.progress.pending.isEmpty())
    }

    @Test fun taskAddedDuringSyncRemainsPendingUntilAcknowledged() = runTest {
        val api = ProgressService()
        val controller = ProgressController(api, MemoryProgressStore(), backgroundScope)
        controller.bind(a, Audience.CHILD); runCurrent()
        api.gate = CompletableDeferred()
        controller.complete(light); runCurrent()
        controller.complete(pattern); runCurrent()
        assertEquals(setOf(light, pattern), controller.state.value.progress.pending)
        api.gate!!.complete(Unit); runCurrent()
        assertEquals(setOf(light, pattern), api.records["aCHILD"])
        assertTrue(controller.state.value.progress.pending.isEmpty())
    }

    @Test fun switchingAccountsAndModesDoesNotSendPreviousOfflineTasks() = runTest {
        val api = ProgressService().apply { offline = true }
        val controller = ProgressController(api, MemoryProgressStore(), backgroundScope)
        controller.bind(a, Audience.CHILD); runCurrent()
        controller.complete(light); runCurrent()
        api.offline = false
        controller.bind(b, Audience.CHILD); runCurrent()
        assertTrue(controller.state.value.progress.completed.isEmpty())
        assertTrue(api.records["bCHILD"].orEmpty().isEmpty())
        controller.bind(a, Audience.ADULT); runCurrent()
        assertTrue(controller.state.value.progress.completed.isEmpty())
        controller.bind(a, Audience.CHILD); runCurrent()
        assertEquals(setOf(light), controller.state.value.progress.completed)
        assertTrue(controller.state.value.progress.pending.isEmpty())
    }
}
