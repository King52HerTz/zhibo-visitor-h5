package cn.xiangyu.museum

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpMuseumApi : MuseumApi, AutoCloseable {
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "museum-network").apply { isDaemon = true }
    }
    override fun close() { workers.shutdownNow() }

    private suspend fun <T> call(endpoint: String, path: String, token: String? = null,
                                 body: JSONObject? = null, read: (HttpURLConnection) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val task = workers.submit {
                var http: HttpURLConnection? = null
                try {
                    if (!continuation.isActive) return@submit
                    http = URL(endpoint + path).openConnection() as HttpURLConnection
                    connection.set(http)
                    if (!continuation.isActive) return@submit
                    http.connectTimeout = 10000
                    http.readTimeout = 90000
                    http.instanceFollowRedirects = false
                    http.setRequestProperty("Accept", "application/json, text/event-stream")
                    if (token != null) http.setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        http.requestMethod = "POST"
                        http.doOutput = true
                        http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        http.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    val status = http.responseCode
                    if (status !in 200..299) {
                        val message = when (status) {
                            401 -> "登录失效或账号密码不正确，请重新登录"
                            400 -> "输入内容不符合要求，请检查后重试"
                            404 -> "接口或会话不存在，请确认服务已更新"
                            409 -> "账号已存在或会话模式不匹配"
                            422 -> "输入格式不符合要求"
                            429 -> "请求过于频繁，请稍后重试"
                            else -> "服务暂时不可用，请稍后重试"
                        }
                        throw ApiFailure(status, message)
                    }
                    val result = read(http)
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (error is ApiFailure) error else ApiFailure(message = "无法连接服务，请检查网络和服务地址")
                    )
                } finally { http?.disconnect() }
            }
            continuation.invokeOnCancellation { connection.get()?.disconnect(); task.cancel(true) }
        }

    private suspend fun json(login: LoginSession, path: String, body: JSONObject? = null) =
        call(login.endpoint, path, login.token, body) { http ->
            http.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        }

    override suspend fun authenticate(endpoint: String, username: String, password: String, register: Boolean): LoginSession {
        val body = JSONObject().put("username", username).put("password", password)
        return call(endpoint, if (register) "/mobile/auth/register" else "/auth/login", body = body) { http ->
            val result = http.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            LoginSession(result.getString("username"), result.getString("access_token"), endpoint)
        }
    }

    override suspend fun sessions(login: LoginSession, audience: Audience): List<ChatSessionInfo> =
        json(login, "/mobile/sessions?audience=${audience.apiValue}").getJSONArray("sessions").objects().map(::sessionInfo)

    override suspend fun createSession(login: LoginSession, audience: Audience, exhibitId: String?): ChatSessionInfo =
        sessionInfo(json(login, "/mobile/sessions", JSONObject().put("audience", audience.apiValue)
            .put("exhibit_id", exhibitId ?: JSONObject.NULL)))

    override suspend fun messages(login: LoginSession, audience: Audience, session: ChatSessionInfo): List<ChatLine> =
        json(login, "/mobile/sessions/${session.id}?audience=${audience.apiValue}").getJSONArray("messages")
            .objects().filter { it.optString("type") in listOf("human", "ai") }.map { item ->
                val trace = item.optJSONObject("rag_trace")
                ChatLine(item.getString("type") == "human", item.getString("content"), evidence(trace),
                    trace?.optJSONArray("hitl_options")?.strings() ?: emptyList())
            }

    override suspend fun stream(login: LoginSession, audience: Audience, session: ChatSessionInfo,
                                 message: String, onEvent: (ChatEvent) -> Unit) {
        val body = JSONObject().put("message", message).put("session_id", session.id).put("audience", audience.apiValue)
        call(login.endpoint, "/mobile/chat/stream", login.token, body) { http ->
            if (!http.contentType.orEmpty().startsWith("text/event-stream"))
                throw ApiFailure(message = "服务没有返回流式回复，请确认接口版本")
            http.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> readSse(reader) { payload ->
                val event = JSONObject(payload)
                when (event.optString("type")) {
                    "content" -> onEvent(ChatEvent.Content(event.optString("content")))
                    "trace" -> onEvent(ChatEvent.Trace(evidence(event.optJSONObject("rag_trace"))))
                    "hitl_request" -> event.getJSONObject("hitl").let {
                        onEvent(ChatEvent.Clarify(it.getString("prompt"), it.optJSONArray("options")?.strings() ?: emptyList()))
                    }
                    "rag_step" -> onEvent(ChatEvent.Status(event.optJSONObject("step")?.optString("label") ?: "正在核验资料"))
                    "error" -> throw ApiFailure(message = "AI 服务暂时无法完成回复，请刷新会话后重试")
                }
            } }
        }
    }

    private fun sessionInfo(item: JSONObject) = ChatSessionInfo(item.getString("session_id"),
        item.optString("title").takeUnless { it.isBlank() || it == "null" } ?: "新的对话",
        item.optString("exhibit_id").takeUnless { it.isBlank() || it == "null" })

    private fun evidence(trace: JSONObject?): List<Evidence> =
        trace?.optJSONArray("retrieved_chunks")?.objects()?.map {
            Evidence(it.optString("filename"), it.optString("text"),
                it.optString("source_url").takeIf { url -> url.startsWith("https://") })
        } ?: emptyList()

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}

val Audience.apiValue: String get() = if (this == Audience.CHILD) "child" else "adult"
