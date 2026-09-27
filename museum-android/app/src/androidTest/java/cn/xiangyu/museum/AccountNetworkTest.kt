package cn.xiangyu.museum

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountNetworkTest {
    @Test fun keystoreStoresOnlyCiphertextAndSurvivesNewStoreInstance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureTokenStore(context)
        store.clear()
        try {
            store.save(LoginSession("TEST_ONLY_USER", "TEST_ONLY_TOKEN", "https://example.invalid"))
            val raw = context.getSharedPreferences("museum_login", 0).getString("sealed", "")!!
            assertFalse(raw.contains("TEST_ONLY_USER"))
            assertFalse(raw.contains("TEST_ONLY_TOKEN"))
            assertFalse(raw.contains("example.invalid"))
            val loaded = SecureTokenStore(context).load()!!
            assertTrue(loaded.username == "TEST_ONLY_USER" && loaded.token == "TEST_ONLY_TOKEN")
        } finally { store.clear() }
        assertNull(store.load())
    }

    @Test fun corruptedLoginIsClearedWithoutCrashing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("museum_login", 0).edit().putString("sealed", "invalid-test-blob").commit()
        assertNull(SecureTokenStore(context).load())
        assertFalse(context.getSharedPreferences("museum_login", 0).contains("sealed"))
    }

    @Test fun httpClientSendsRestrictedRegistrationAndReadsUtf8Stream() = runBlocking {
        TestMuseumServer().use { server ->
            val api = HttpMuseumApi()
            val login = api.authenticate(server.endpoint, "TEST_ONLY_USER", "TEST_ONLY_PASSWORD", true)
            val payload = server.requests.first().second
            assertEquals(setOf("username", "password"), payload.keys().asSequence().toSet())
            val session = api.createSession(login, Audience.CHILD, "silk-plain-straight")
            val events = mutableListOf<ChatEvent>()
            api.stream(login, Audience.CHILD, session, "TEST_QUESTION") { events += it }
            assertEquals("这是测试回复。", (events[0] as ChatEvent.Content).text)
            assertEquals("测试依据", (events[1] as ChatEvent.Trace).evidence.single().text)
            assertEquals(listOf("测试选项"), (events[2] as ChatEvent.Clarify).options)
            assertEquals("child", server.requests.last().second.getString("audience"))
            assertEquals("test-session", server.requests.last().second.getString("session_id"))
        }
    }

    @Test fun serverErrorsAndExpiredLoginAreNotPresentedAsAnswers() = runBlocking {
        TestMuseumServer().use { server ->
            val api = HttpMuseumApi()
            val login = LoginSession("TEST_ONLY_USER", "TEST_ONLY_TOKEN", server.endpoint)
            val session = ChatSessionInfo("test-session", "测试", null)
            server.streamError = true
            try { api.stream(login, Audience.ADULT, session, "TEST_QUESTION") { }; fail("must report SSE error") }
            catch (error: ApiFailure) { assertFalse(error.message.orEmpty().contains("TEST_PROVIDER_ERROR")) }
            server.unauthorized = true
            try { api.sessions(login, Audience.ADULT); fail("must require reauthentication") }
            catch (error: ApiFailure) { assertEquals(401, error.status) }
        }
    }
}
