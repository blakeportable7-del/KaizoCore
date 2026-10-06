package com.ironmonone.app.stream

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * OBS's WebSocket server as obs-websocket 5 behaves, for the tests: the opening handshake with the obswebsocket.json
 * subprotocol, Hello (with a challenge when [password] is set), Identify checked against its own working of the
 * answer, Identified, then requests and their responses. There is no OBS on the build PC; this is what the client is
 * held to until one is tried for real.
 *
 * It reads the client's frames with WebSocket.readFrame, the server side's own reader, which throws on a frame that
 * is not masked: a client that forgot to mask would not get past Identify here, as it would not with OBS.
 */
internal class FakeObs(
    @Volatile var password: String? = null,
    port: Int = 0,
    val scenes: List<String> = listOf("Game", "Battle", "Game over", "Be right back"),
) : AutoCloseable {
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port)) }
    val port: Int get() = server.localPort

    /** Every request after Identify, in order: "Type" or "Type:sceneName". */
    val requests = CopyOnWriteArrayList<String>()
    /** Every Identify's "d", as sent. */
    val identifies = CopyOnWriteArrayList<Map<String, Any?>>()
    /** Connections that reached the handshake. */
    val connections = AtomicInteger(0)
    private val open = CopyOnWriteArrayList<Socket>()

    @Volatile var scene = "Game"
    @Volatile var replayActive = true

    val salt = "lM1GncleQOaCu9lT1yeUZhFYnqhsLLP1G5lAGo3ixaI="
    val challenge = "+IxH4CnCiqpX1rM9scsNynZzbOe4KhDeYcTNS3PDaeY="

    init {
        Thread({
            while (!server.isClosed) {
                val c = try { server.accept() } catch (e: Exception) { break }
                open += c
                Thread({ runCatching { serve(c) }; runCatching { c.close() }; open.remove(c) }, "fake-obs-conn").apply { isDaemon = true }.start()
            }
        }, "fake-obs").apply { isDaemon = true }.start()
    }

    /** The answer obs-websocket expects, worked out here on its own (its docs, "Creating an authentication string"). */
    private fun expected(pw: String): String {
        fun sha(s: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))
        return sha(sha(pw + salt) + challenge)
    }

    private fun serve(c: Socket) {
        c.soTimeout = 10_000
        val input = BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        val request = line(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val h = line(input) ?: return
            if (h.isEmpty()) break
            headers[h.substringBefore(':').trim().lowercase()] = h.substringAfter(':').trim()
        }
        connections.incrementAndGet()
        check(request.startsWith("GET / ")) { request }
        check(headers["upgrade"].equals("websocket", true))
        check(headers["sec-websocket-protocol"]?.split(',')?.map { it.trim() }?.contains("obswebsocket.json") == true)
        val key = headers["sec-websocket-key"]!!
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${WebSocket.acceptKey(key)}\r\nSec-WebSocket-Protocol: obswebsocket.json\r\n\r\n").toByteArray())
        val pw = password
        val hello = linkedMapOf<String, Any?>("obsWebSocketVersion" to "5.5.2", "rpcVersion" to 1)
        if (pw != null) hello["authentication"] = linkedMapOf("challenge" to challenge, "salt" to salt)
        send(out, mapOf("op" to 0, "d" to hello))

        val identify = readJson(input) ?: return
        check(identify["op"] == 1L) { "Identify first: $identify" }
        @Suppress("UNCHECKED_CAST") val d = identify["d"] as Map<String, Any?>
        identifies += d
        if (pw != null && d["authentication"] != expected(pw)) {
            out.write(WebSocket.close(4009, "Authentication failed.")); out.flush()
            return
        }
        send(out, mapOf("op" to 2, "d" to mapOf("negotiatedRpcVersion" to 1)))

        while (true) {
            val msg = readJson(input) ?: return
            if (msg["op"] != 6L) continue
            @Suppress("UNCHECKED_CAST") val r = msg["d"] as Map<String, Any?>
            val type = r["requestType"] as String
            @Suppress("UNCHECKED_CAST") val data = r["requestData"] as Map<String, Any?>?
            requests += if (data?.get("sceneName") != null) "$type:${data["sceneName"]}" else type
            var ok = true; var code = 100; var comment: String? = null
            val response: Map<String, Any?>? = when (type) {
                "GetVersion" -> mapOf("obsVersion" to "31.0.0", "obsWebSocketVersion" to "5.5.2", "rpcVersion" to 1)
                // obs-websocket lists the scenes bottom first, with sceneIndex 0 for the bottom one.
                "GetSceneList" -> mapOf("currentProgramSceneName" to scene,
                    "scenes" to scenes.mapIndexed { i, n -> mapOf("sceneIndex" to scenes.size - 1 - i, "sceneName" to n) }.reversed())
                "GetCurrentProgramScene" -> mapOf("currentProgramSceneName" to scene, "sceneName" to scene)
                "SetCurrentProgramScene" -> {
                    val n = data?.get("sceneName") as? String
                    if (n != null && n in scenes) scene = n else { ok = false; code = 600; comment = "No source was found by the name of `$n`." }
                    null
                }
                "SaveReplayBuffer" -> { if (!replayActive) { ok = false; code = 501; comment = "Replay buffer is not active." }; null }
                "GetReplayBufferStatus" -> mapOf("outputActive" to replayActive)
                else -> { ok = false; code = 204; null }
            }
            val status = linkedMapOf<String, Any?>("result" to ok, "code" to code)
            if (comment != null) status["comment"] = comment
            val d2 = linkedMapOf("requestType" to type, "requestId" to r["requestId"], "requestStatus" to status)
            if (response != null) d2["responseData"] = response
            send(out, mapOf("op" to 7, "d" to d2))
        }
    }

    private fun send(out: OutputStream, m: Map<String, Any?>) { out.write(WebSocket.text(Json.write(m))); out.flush() }

    private fun readJson(input: InputStream): Map<String, Any?>? {
        while (true) {
            val f = try { WebSocket.readFrame(input, 1 shl 20) } catch (e: Exception) { return null } ?: return null
            when (f.opcode) {
                WebSocket.OP_CLOSE -> return null
                WebSocket.OP_TEXT -> {
                    @Suppress("UNCHECKED_CAST")
                    return MiniJson(String(f.payload, Charsets.UTF_8)).parse() as Map<String, Any?>
                }
            }
        }
    }

    private fun line(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    /** Drops every open connection, as OBS closing or the Wi-Fi going would. The server keeps listening. */
    fun dropAll() { open.forEach { runCatching { it.close() } } }

    override fun close() { runCatching { server.close() }; dropAll() }
}

/** A port nothing listens on right now. */
internal fun freePort(): Int = ServerSocket(0).use { it.localPort }
