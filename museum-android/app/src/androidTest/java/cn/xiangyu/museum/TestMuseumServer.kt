package cn.xiangyu.museum

import org.json.JSONObject
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

// Instrumentation-only protocol fixture, never packaged in the application.
class TestMuseumServer : AutoCloseable {
    private val server = ServerSocket(0)
    val endpoint = "http://127.0.0.1:${server.localPort}"
    val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    @Volatile var streamError = false
    @Volatile var unauthorized = false
    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().buffered()
                    fun line(): String {
                        val bytes = mutableListOf<Byte>()
                        while (true) {
                            val byte = input.read()
                            if (byte < 0 || byte == 10) break
                            if (byte != 13) bytes += byte.toByte()
                        }
                        return String(bytes.toByteArray(), Charsets.US_ASCII)
                    }
                    val request = line().split(" ")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val header = line()
                        if (header.isEmpty()) break
                        headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                    }
                    val length = headers["content-length"]?.toInt() ?: 0
                    val bodyBytes = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val count = input.read(bodyBytes, read, length - read)
                        if (count < 0) break
                        read += count
                    }
                    val body = if (read == 0) JSONObject() else JSONObject(String(bodyBytes, 0, read, Charsets.UTF_8))
                    val path = request[1]
                    requests += path to body
                    val stream = path == "/mobile/chat/stream"
                    val response = when {
                        unauthorized -> "{}"
                        path == "/auth/login" || path == "/mobile/auth/register" ->
                            JSONObject().put("username", body.getString("username")).put("access_token", "TEST_ONLY_TOKEN").toString()
                        request[0] == "POST" && path == "/mobile/sessions" -> "{\"session_id\":\"test-session\",\"title\":\"测试会话\",\"exhibit_id\":null}"
                        path.startsWith("/mobile/sessions?") -> "{\"sessions\":[]}"
                        path.startsWith("/mobile/sessions/") -> "{\"messages\":[]}"
                        stream && streamError -> "data: {\"type\":\"error\",\"content\":\"TEST_PROVIDER_ERROR\"}\n\n"
                        stream -> "data: {\"type\":\"content\",\"content\":\"这是测试回复。\"}\n\n" +
                            "data: {\"type\":\"trace\",\"rag_trace\":{\"retrieved_chunks\":[{\"filename\":\"测试资料\",\"text\":\"测试依据\"}]}}\n\n" +
                            "data: {\"type\":\"hitl_request\",\"hitl\":{\"prompt\":\"请选一个测试方向\",\"options\":[\"测试选项\"]}}\n\n" +
                            "data: [DONE]\n\n"
                        else -> "{}"
                    }
                    val bytes = response.toByteArray(Charsets.UTF_8)
                    val status = if (unauthorized) "401 Unauthorized" else "200 OK"
                    val type = if (stream) "text/event-stream" else "application/json"
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    bytes.asList().chunked(3).forEach { packet -> output.write(packet.toByteArray()); output.flush() }
                }
            } catch (_: Exception) { /* A cancelled client can close its socket. */ }
        }
    }
    override fun close() { server.close(); worker.join(1000) }
}
