package cn.xiangyu.museum

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class MemoryVault(var value: LoginSession? = null) : TokenVault {
    override suspend fun load() = value
    override suspend fun save(session: LoginSession) { value = session }
    override suspend fun clear() { value = null }
}

class FakeMuseumApi : MuseumApi {
    val histories = mutableMapOf<String, List<ChatLine>>()
    val owned = mutableMapOf<String, MutableList<ChatSessionInfo>>()
    var fail: ApiFailure? = null
    var hold = false
    var lateEvent: ((ChatEvent) -> Unit)? = null
    var sends = 0
    override suspend fun authenticate(endpoint: String, username: String, password: String, register: Boolean) =
        LoginSession(username, "TEST_ONLY_TOKEN", endpoint)
    override suspend fun sessions(login: LoginSession, audience: Audience): List<ChatSessionInfo> {
        fail?.let { throw it }
        return owned["${login.username}-${audience.name}"].orEmpty()
    }
    override suspend fun createSession(login: LoginSession, audience: Audience, exhibitId: String?): ChatSessionInfo {
        val key = "${login.username}-${audience.name}"
        val session = ChatSessionInfo("session-${owned.values.sumOf { it.size }}", "测试对话", exhibitId)
        owned.getOrPut(key) { mutableListOf() }.add(0, session)
        return session
    }
    override suspend fun messages(login: LoginSession, audience: Audience, session: ChatSessionInfo) = histories[session.id].orEmpty()
    override suspend fun stream(login: LoginSession, audience: Audience, session: ChatSessionInfo,
                                 message: String, onEvent: (ChatEvent) -> Unit) {
        sends++
        lateEvent = onEvent
        onEvent(ChatEvent.Content("部分"))
        if (hold) awaitCancellation()
        fail?.let { throw it }
        onEvent(ChatEvent.Content("回答"))
        onEvent(ChatEvent.Trace(listOf(Evidence("测试资料", "依据", null))))
        histories[session.id] = histories[session.id].orEmpty() + listOf(ChatLine(true, message), ChatLine(false, "部分回答"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MuseumControllerTest {
    @Test fun endpointOnlyAllowsSafeConfiguredTransport() {
        assertEquals("https://example.invalid", serviceEndpoint("https://example.invalid/", false))
        assertEquals("http://10.0.2.2:8000", serviceEndpoint("http://10.0.2.2:8000", true))
        for (url in listOf("http://example.invalid", "https://user:password@example.invalid", "https://example.invalid?token=x", "bad")) {
            try { serviceEndpoint(url, false); fail("must reject unsafe endpoint") } catch (_: ApiFailure) { }
        }
    }

    @Test fun sseHandlesCommentsMultilineAndDone() {
        val events = mutableListOf<String>()
        readSse(StringReader(": keepalive\r\ndata: first\r\ndata: second\r\n\r\ndata: [DONE]\r\n\r\n").buffered()) { events += it }
        assertEquals(listOf("first\nsecond"), events)
    }

    @Test fun sseNeverTreatsUnexpectedEofAsCompletedAnswer() {
        try { readSse(StringReader("data: partial\n\n").buffered()) { }; fail("must signal interruption") }
        catch (_: ApiFailure) { }
    }

    @Test fun restoreAndModeSwitchDoNotReuseOtherModesHistory() = runTest {
        val api = FakeMuseumApi()
        val vault = MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid"))
        val controller = MuseumController(api, vault, backgroundScope, false)
        runCurrent()
        assertEquals("a", controller.state.value.username)
        controller.selectAudience(Audience.CHILD); runCurrent()
        controller.draft("儿童问题"); controller.send(); runCurrent()
        val childSession = controller.state.value.session?.id
        assertEquals("部分回答", controller.state.value.messages.last().text)
        assertEquals("依据", controller.state.value.messages.last().evidence.single().text)
        controller.selectAudience(Audience.ADULT); runCurrent()
        assertTrue(controller.state.value.messages.isEmpty())
        controller.draft("成人问题"); controller.send(); runCurrent()
        assertNotEquals(childSession, controller.state.value.session?.id)
        controller.selectAudience(Audience.CHILD); runCurrent()
        assertEquals(childSession, controller.state.value.session?.id)
        assertEquals("儿童问题", controller.state.value.messages.first().text)
    }

    @Test fun logoutCancelsAndRejectsLateEventsBeforeNextAccount() = runTest {
        val api = FakeMuseumApi().apply { hold = true }
        val vault = MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid"))
        val controller = MuseumController(api, vault, backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.CHILD); runCurrent()
        controller.draft("问题"); controller.send(); runCurrent()
        val late = api.lateEvent!!
        controller.logout(); runCurrent()
        late(ChatEvent.Content("旧账号迟到回复"))
        assertNull(controller.state.value.username)
        assertTrue(controller.state.value.messages.isEmpty())
        assertNull(vault.value)
        controller.authenticate("https://example.invalid", "b", "TEST_ONLY_PASSWORD", false); runCurrent()
        controller.selectAudience(Audience.CHILD); runCurrent()
        late(ChatEvent.Content("仍然迟到"))
        assertEquals("b", controller.state.value.username)
        assertTrue(controller.state.value.messages.isEmpty())
    }

    @Test fun modeSwitchRejectsLateReplyAndClearsDraft() = runTest {
        val api = FakeMuseumApi().apply { hold = true }
        val controller = MuseumController(api, MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid")), backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.CHILD); runCurrent()
        controller.draft("问题"); controller.send(); runCurrent()
        val late = api.lateEvent!!
        controller.selectAudience(Audience.ADULT); runCurrent()
        late(ChatEvent.Content("儿童迟到回复"))
        assertEquals(Audience.ADULT, controller.state.value.audience)
        assertTrue(controller.state.value.messages.isEmpty())
        assertEquals("", controller.state.value.draft)
    }

    @Test fun interruptedSendPreservesDraftWithoutAutomaticRetry() = runTest {
        val api = FakeMuseumApi()
        val controller = MuseumController(api, MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid")), backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.CHILD); runCurrent()
        api.fail = ApiFailure(message = "测试断网")
        controller.draft("保留的问题"); controller.send(); runCurrent()
        assertEquals("保留的问题", controller.state.value.draft)
        assertTrue(controller.state.value.needsRefresh)
        assertTrue(controller.state.value.messages.last().incomplete)
        controller.send(); runCurrent()
        assertEquals(1, api.sends)
        api.fail = null
        controller.loadConversation(); runCurrent()
        controller.send(); runCurrent()
        assertEquals(2, api.sends)
    }

    @Test fun expiredLoginClearsStoredIdentityAndConversation() = runTest {
        val vault = MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid"))
        val api = FakeMuseumApi().apply { fail = ApiFailure(401, "测试过期") }
        val controller = MuseumController(api, vault, backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.CHILD); runCurrent()
        assertNull(controller.state.value.username)
        assertNull(vault.value)
        assertTrue(controller.state.value.messages.isEmpty())
    }

    @Test fun duplicateTapDoesNotSendTwiceAndStopInvalidatesCallback() = runTest {
        val api = FakeMuseumApi().apply { hold = true }
        val controller = MuseumController(api, MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid")), backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.CHILD); runCurrent()
        controller.draft("问题"); controller.send(); controller.send(); runCurrent()
        assertEquals(1, api.sends)
        controller.stop(); runCurrent()
        api.lateEvent!!(ChatEvent.Content("停止后内容"))
        assertEquals("部分", controller.state.value.messages.last().text)
        assertFalse(controller.state.value.chatBusy)
        assertTrue(controller.state.value.needsRefresh)
    }

    @Test fun newControllerRestoresLoginThenLoadsServerHistoryWithoutResending() = runTest {
        val api = FakeMuseumApi()
        val vault = MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid"))
        val first = MuseumController(api, vault, backgroundScope, false)
        runCurrent(); first.selectAudience(Audience.CHILD); runCurrent()
        first.draft("保存的问题"); first.send(); runCurrent()
        val restored = MuseumController(api, vault, backgroundScope, false)
        runCurrent(); restored.selectAudience(Audience.CHILD); runCurrent()
        assertEquals("保存的问题", restored.state.value.messages.first().text)
        assertEquals(1, api.sends)
    }

    @Test fun selectingHistoricalSessionUpdatesArtifactAndRefreshKeepsSelection() = runTest {
        val api = FakeMuseumApi()
        val controller = MuseumController(api, MemoryVault(LoginSession("a", "TEST_ONLY_TOKEN", "https://example.invalid")), backgroundScope, false)
        runCurrent(); controller.selectAudience(Audience.ADULT); runCurrent()
        controller.draft("主题问题"); controller.send(); runCurrent()
        val topic = controller.state.value.session!!
        controller.selectExhibit("silk-plain-straight"); runCurrent()
        controller.draft("文物问题"); controller.send(); runCurrent()
        controller.loadConversation(topic); runCurrent()
        assertNull(controller.state.value.exhibitId)
        assertEquals(topic.id, controller.state.value.session?.id)
        controller.loadConversation(); runCurrent()
        assertEquals(topic.id, controller.state.value.session?.id)
    }
}
