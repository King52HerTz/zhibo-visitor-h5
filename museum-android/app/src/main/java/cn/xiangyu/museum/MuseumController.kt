package cn.xiangyu.museum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MuseumState(
    val restoring: Boolean = true, val username: String? = null, val endpoint: String = "",
    val authBusy: Boolean = false, val error: String? = null, val audience: Audience? = null,
    val sessions: List<ChatSessionInfo> = emptyList(), val session: ChatSessionInfo? = null,
    val exhibitId: String? = null, val messages: List<ChatLine> = emptyList(),
    val chatBusy: Boolean = false, val status: String = "", val draft: String = "",
    val needsRefresh: Boolean = false
)

class MuseumController(private val api: MuseumApi, private val vault: TokenVault,
                       private val scope: CoroutineScope, private val debug: Boolean) {
    private val mutable = MutableStateFlow(MuseumState(endpoint = if (debug) "http://10.0.2.2:8000" else ""))
    val state = mutable.asStateFlow()
    private var login: LoginSession? = null
    private var job: Job? = null
    @Volatile private var generation = 0

    init {
        val ticket = generation
        job = scope.launch {
            try {
                val saved = vault.load()?.let { saved ->
                    serviceEndpoint(saved.endpoint, debug)
                    saved
                }
                if (ticket == generation) {
                    login = saved
                    mutable.update { it.copy(restoring = false, username = saved?.username,
                        endpoint = saved?.endpoint ?: it.endpoint) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (ticket == generation) {
                    login = null
                    mutable.update { it.copy(restoring = false, error = "登录记录无法恢复，请重新登录") }
                }
            }
        }
    }

    private fun replace(action: suspend (Int) -> Unit) {
        val previous = job
        val ticket = ++generation
        previous?.cancel()
        job = scope.launch {
            previous?.join()
            try { action(ticket) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (ticket == generation) {
                    if (error is ApiFailure && error.status == 401 && login != null) {
                        login = null
                        mutable.value = MuseumState(restoring = false, endpoint = state.value.endpoint,
                            error = "登录已失效，请重新登录")
                        vault.clear()
                    } else mutable.update { it.copy(authBusy = false, chatBusy = false, status = "",
                        error = if (error is ApiFailure) error.message else "操作失败，请重试") }
                }
            }
        }
    }

    fun authenticate(endpoint: String, username: String, password: String, register: Boolean) {
        if (state.value.authBusy || state.value.restoring) return
        mutable.update { it.copy(authBusy = true, error = null) }
        replace { ticket ->
            val address = serviceEndpoint(endpoint, debug)
            if (username.isBlank() || password.isBlank() || (register && password.trim().length < 8))
                throw ApiFailure(message = "请填写账号和密码；注册密码至少8位")
            val authenticated = api.authenticate(address, username.trim(), password.trim(), register)
            vault.save(authenticated)
            if (ticket == generation) {
                login = authenticated
                mutable.value = MuseumState(restoring = false, username = authenticated.username, endpoint = address)
            }
        }
    }

    fun logout() {
        login = null
        val endpoint = state.value.endpoint
        mutable.value = MuseumState(restoring = false, endpoint = endpoint)
        replace { vault.clear() }
    }

    fun chooseMode() {
        mutable.update { it.copy(audience = null, session = null, sessions = emptyList(),
            messages = emptyList(), draft = "", chatBusy = false, error = null, needsRefresh = false) }
        replace { }
    }

    fun selectAudience(audience: Audience) {
        mutable.update { it.copy(audience = audience, exhibitId = null, session = null,
            sessions = emptyList(), messages = emptyList(), draft = "", error = null, needsRefresh = false, chatBusy = false) }
        loadConversation()
    }

    fun selectExhibit(exhibitId: String?) {
        if (exhibitId != null && FirstChapter.relics.none { it.id == exhibitId }) return
        mutable.update { it.copy(exhibitId = exhibitId, session = null, messages = emptyList(), draft = "", chatBusy = false) }
        loadConversation()
    }

    fun loadConversation(selected: ChatSessionInfo? = null, new: Boolean = false) {
        val identity = login ?: return
        val audience = state.value.audience ?: return
        val exhibit = state.value.exhibitId
        val current = selected ?: state.value.session
        mutable.update { it.copy(chatBusy = true, error = null, status = "正在读取会话") }
        replace { ticket ->
            val sessions = api.sessions(identity, audience)
            val session = if (new) null else sessions.firstOrNull { it.id == current?.id }
                ?: sessions.firstOrNull { it.exhibitId == exhibit }
            val messages = if (session == null) emptyList() else api.messages(identity, audience, session)
            if (ticket == generation) mutable.update { it.copy(sessions = sessions, session = session,
                exhibitId = if (session != null) session.exhibitId else exhibit, messages = messages,
                chatBusy = false, status = "", needsRefresh = false) }
        }
    }

    fun draft(text: String) { mutable.update { it.copy(draft = text.take(8000)) } }

    fun send(option: String? = null) {
        val identity = login ?: return
        val before = state.value
        val audience = before.audience ?: return
        val text = (option ?: before.draft).trim()
        if (before.chatBusy || before.needsRefresh || text.isEmpty()) return
        mutable.update { it.copy(chatBusy = true, draft = "", error = null, status = "正在连接阿帛") }
        replace { ticket ->
            var sent = false
            try {
                val session = before.session ?: api.createSession(identity, audience, before.exhibitId)
                if (ticket != generation) return@replace
                mutable.update { it.copy(session = session, sessions = listOf(session) + it.sessions.filter { saved -> saved.id != session.id },
                    messages = it.messages + listOf(ChatLine(true, text), ChatLine(false, "", incomplete = true))) }
                sent = true
                api.stream(identity, audience, session, text) { event ->
                    mutable.update { current ->
                        if (ticket != generation || !current.chatBusy) current else {
                            val last = current.messages.lastOrNull() ?: return@update current
                            when (event) {
                                is ChatEvent.Content -> current.copy(messages = current.messages.dropLast(1) + last.copy(text = last.text + event.text))
                                is ChatEvent.Trace -> current.copy(messages = current.messages.dropLast(1) + last.copy(evidence = event.evidence))
                                is ChatEvent.Clarify -> current.copy(messages = current.messages.dropLast(1) + last.copy(text = event.prompt, options = event.options))
                                is ChatEvent.Status -> current.copy(status = event.text)
                            }
                        }
                    }
                }
                if (ticket == generation) mutable.update { it.copy(chatBusy = false, status = "",
                    messages = it.messages.dropLast(1) + it.messages.last().copy(incomplete = false)) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (ticket == generation) mutable.update { it.copy(draft = text, needsRefresh = sent) }
                throw error
            }
        }
    }

    fun stop() {
        if (!state.value.chatBusy) return
        mutable.update { it.copy(chatBusy = false, needsRefresh = true,
            status = "已停止显示；服务器可能仍在处理，请刷新会话再继续") }
        replace { }
    }
}

class MuseumViewModel(application: Application) : AndroidViewModel(application) {
    private val api = HttpMuseumApi()
    val controller = MuseumController(api, SecureTokenStore(application), viewModelScope, BuildConfig.DEBUG)
    override fun onCleared() { api.close() }
}
