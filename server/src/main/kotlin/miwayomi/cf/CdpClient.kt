package miwayomi.cf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CdpClient(private val debugPort: Int) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newHttpClient()
    private var socket: WebSocket? = null
    private var debuggerUrl: String? = null
    private var msgId = 0
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private val sendLock = Any()
    private val textBuffer = StringBuilder()

    private fun nextId() = ++msgId

    private fun handleMessage(text: String) {
        runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (id != null) pending.remove(id)?.complete(obj)
        }
    }

    private fun onTextData(data: CharSequence, last: Boolean) {
        textBuffer.append(data)
        if (last) {
            val complete = textBuffer.toString()
            textBuffer.setLength(0)
            handleMessage(complete)
        }
    }

    private fun connect(): WebSocket {
        socket?.let { return it }

        val resp = http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$debugPort/json/new?about:blank"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val wsUrl = json.parseToJsonElement(resp.body()).jsonObject["webSocketDebuggerUrl"]
            ?.jsonPrimitive?.contentOrNull
            ?: error("Chrome no devolvió webSocketDebuggerUrl: ${resp.body().take(200)}")
        debuggerUrl = wsUrl

        val open = CompletableFuture<WebSocket>()
        val ws = http.newWebSocketBuilder()
            .buildAsync(URI(wsUrl), object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) { open.complete(webSocket) }
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    onTextData(data, last)
                    webSocket.request(1)
                    return null
                }
                override fun onError(webSocket: WebSocket, error: Throwable) {
                    open.completeExceptionally(error)
                }
            })
            .get(15, TimeUnit.SECONDS)
        ws.request(1)
        socket = ws

        runCatching { open.get(10, TimeUnit.SECONDS) }
        command("Page.enable")
        command("Network.enable")
        return ws
    }

    fun command(method: String, params: JsonObject = buildJsonObject {}): JsonObject {
        val ws = connect()
        val id = nextId()
        val fut = CompletableFuture<JsonObject>()
        pending[id] = fut
        val msg = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }
        synchronized(sendLock) {
            ws.sendText(msg.toString(), true)
        }
        return try {
            fut.get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            pending.remove(id)
            throw RuntimeException("CDP $method falló: ${e.message}", e)
        }
    }

    fun close() {
        socket?.let { runCatching { it.sendClose(WebSocket.NORMAL_CLOSURE, "bye") } }
        socket = null
        debuggerUrl = null
        pending.clear()
    }
}
