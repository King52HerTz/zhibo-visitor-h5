package cn.xiangyu.museum

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

// Opt-in: creates temporary ordinary users and calls the configured real model.
class LiveMuseumServiceTest {
    @Test fun realProgressSynchronizesAndRestoresWithoutCallingModel() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("liveEndpoint")
        val prefix = args.getString("liveUserPrefix")
        assumeTrue("live service arguments required", !endpoint.isNullOrBlank() && !prefix.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "live-progress-${UUID.randomUUID()}.db"
        val api = HttpMuseumApi()
        try {
            val password = UUID.randomUUID().toString()
            val address = serviceEndpoint(endpoint!!, true)
            val a = api.authenticate(address, prefix + "a", password, true)
            val b = api.authenticate(address, prefix + "b", password, true)
            val key = ProgressKey(address, a.username, Audience.CHILD)
            SqliteProgressStore(context, database).use { store ->
                store.complete(key, "clothes-light-v1")
                store.complete(key, "clothes-pattern-v1")
                storyTaskIds.forEach { store.complete(key, it) }
            }
            SqliteProgressStore(context, database).use { store ->
                val local = store.load(key)
                assertEquals(5, local.pending.size)
                val responses = coroutineScope {
                    List(4) { async { api.syncProgress(a, Audience.CHILD, local.completed) } }.awaitAll()
                }
                responses.forEach { assertEquals(local.completed, it) }
                assertTrue(store.merge(key, responses.first()).pending.isEmpty())
                assertEquals(local.completed, api.syncProgress(a, Audience.CHILD, emptySet()))
                assertTrue(api.syncProgress(a, Audience.ADULT, emptySet()).isEmpty())
                assertTrue(api.syncProgress(b, Audience.CHILD, emptySet()).isEmpty())
                assertEquals(511, storyMaskFromTasks(api.syncProgress(a, Audience.CHILD, emptySet())))
            }
            SqliteProgressStore(context, database).use { store ->
                assertEquals(5, store.load(key).completed.size)
                assertTrue(store.load(key).pending.isEmpty())
            }
        } finally {
            api.close()
            context.deleteDatabase(database)
        }
    }

    @Test fun realServiceRestoresHistoryAndSeparatesAccountsAndModes() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("liveEndpoint")
        val prefix = args.getString("liveUserPrefix")
        assumeTrue("live service arguments required", !endpoint.isNullOrBlank() && !prefix.isNullOrBlank())
        val address = serviceEndpoint(endpoint!!, true)
        val api = HttpMuseumApi()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vault = object : TokenVault {
            var saved: LoginSession? = null
            override suspend fun load() = saved
            override suspend fun save(session: LoginSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        try {
            val password = UUID.randomUUID().toString()
            val a = api.authenticate(address, prefix + "a", password, true)
            val b = api.authenticate(address, prefix + "b", password, true)
            assertEquals(a.username, api.authenticate(address, a.username, password, false).username)
            val child = api.createSession(a, Audience.CHILD, null)
            val adult = api.createSession(a, Audience.ADULT, null)
            try {
                api.messages(b, Audience.CHILD, child)
                fail("other account must not read history")
            } catch (error: ApiFailure) { assertEquals(404, error.status) }
            try {
                api.messages(a, Audience.ADULT, child)
                fail("other mode must not read history")
            } catch (error: ApiFailure) { assertEquals(409, error.status) }
            val events = mutableListOf<ChatEvent>()
            api.stream(a, Audience.CHILD, child, "请简单介绍一下素纱单衣") { events += it }
            assertTrue(events.filterIsInstance<ChatEvent.Content>().any { it.text.isNotBlank() })
            assertTrue(events.filterIsInstance<ChatEvent.Trace>().any { it.evidence.isNotEmpty() })
            assertTrue(api.messages(a, Audience.CHILD, child).any { !it.human && it.text.isNotBlank() })
            vault.save(a)
            val controller = MuseumController(api, vault, scope, true)
            awaitIdle(controller)
            controller.selectAudience(Audience.CHILD)
            awaitIdle(controller)
            assertEquals(child.id, controller.state.value.session?.id)
            assertTrue(controller.state.value.messages.isNotEmpty())
            controller.selectAudience(Audience.ADULT)
            awaitIdle(controller)
            assertEquals(adult.id, controller.state.value.session?.id)
            assertTrue(controller.state.value.messages.isEmpty())
            controller.logout()
            awaitIdle(controller)
            assertNull(vault.load())
            controller.authenticate(address, b.username, password, false)
            awaitIdle(controller)
            controller.selectAudience(Audience.CHILD)
            awaitIdle(controller)
            assertEquals(b.username, controller.state.value.username)
            assertTrue(controller.state.value.sessions.isEmpty())
            assertTrue(controller.state.value.messages.isEmpty())
        } finally {
            scope.cancel()
            api.close()
        }
    }

    private suspend fun awaitIdle(controller: MuseumController) = withTimeout(30000) {
        while (controller.state.value.let { it.restoring || it.authBusy || it.chatBusy }) delay(50)
        assertNull(controller.state.value.error)
    }
}
