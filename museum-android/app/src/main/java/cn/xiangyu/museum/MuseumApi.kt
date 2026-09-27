package cn.xiangyu.museum

import java.io.BufferedReader
import java.net.URI

// Credentials deliberately do not have a data-class toString containing the token.
class LoginSession(val username: String, val token: String, val endpoint: String)
data class ChatSessionInfo(val id: String, val title: String, val exhibitId: String?)
data class Evidence(val filename: String, val text: String, val url: String?)
data class ChatLine(val human: Boolean, val text: String, val evidence: List<Evidence> = emptyList(),
                    val options: List<String> = emptyList(), val incomplete: Boolean = false)
sealed interface ChatEvent {
    data class Content(val text: String) : ChatEvent
    data class Trace(val evidence: List<Evidence>) : ChatEvent
    data class Clarify(val prompt: String, val options: List<String>) : ChatEvent
    data class Status(val text: String) : ChatEvent
}
class ApiFailure(val status: Int = 0, message: String) : Exception(message)

interface TokenVault {
    suspend fun load(): LoginSession?
    suspend fun save(session: LoginSession)
    suspend fun clear()
}

interface MuseumApi {
    suspend fun authenticate(endpoint: String, username: String, password: String, register: Boolean): LoginSession
    suspend fun sessions(login: LoginSession, audience: Audience): List<ChatSessionInfo>
    suspend fun createSession(login: LoginSession, audience: Audience, exhibitId: String?): ChatSessionInfo
    suspend fun messages(login: LoginSession, audience: Audience, session: ChatSessionInfo): List<ChatLine>
    suspend fun stream(login: LoginSession, audience: Audience, session: ChatSessionInfo,
                       message: String, onEvent: (ChatEvent) -> Unit)
}

fun serviceEndpoint(value: String, debug: Boolean): String {
    val uri = try { URI(value.trim()) } catch (_: Exception) { throw ApiFailure(message = "服务地址格式不正确") }
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null ||
        (uri.scheme != "https" && !(debug && uri.scheme == "http"))) {
        throw ApiFailure(message = "请填写无账号、查询参数的 HTTPS 服务地址；调试版才允许 HTTP")
    }
    return uri.toString().trimEnd('/')
}

fun readSse(reader: BufferedReader, onData: (String) -> Unit) {
    val data = mutableListOf<String>()
    while (true) {
        val line = reader.readLine() ?: throw ApiFailure(message = "回复连接已中断，请刷新会话后重试")
        if (line.isEmpty()) {
            if (data.isNotEmpty()) {
                val payload = data.joinToString("\n")
                data.clear()
                if (payload == "[DONE]") return
                onData(payload)
            }
        } else if (line.startsWith("data:")) {
            data += line.removePrefix("data:").removePrefix(" ")
        }
    }
}
