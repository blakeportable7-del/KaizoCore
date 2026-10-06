package com.ironmonone.app.stream.twitch

import com.ironmonone.app.stream.WebSocket
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One request the fake HTTP server saw. */
class SeenRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
    /** The form body as a map (id.twitch.tv calls). */
    val form: Map<String, String> get() = body.split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
    val json: Map<String, Any?> get() = JsonIn.obj(body) ?: emptyMap()
}

/**
 * A real HTTP/1.1 server on 127.0.0.1, one request per connection, answered by [handler]. UrlHttp talks to it exactly as
 * it talks to Twitch, so the tests cover the app's HTTP code and not a stand-in for it.
 */
class FakeHttp(private val handler: (SeenRequest) -> Pair<Int, String>) : Closeable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val seen = CopyOnWriteArrayList<SeenRequest>()
    val base: String get() = "http://127.0.0.1:${server.localPort}"

    init {
        Thread({
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                Thread({ runCatching { serve(s) }; runCatching { s.close() } }, "fake-http-conn").apply { isDaemon = true }.start()
            }
        }, "fake-http").apply { isDaemon = true }.start()
    }

    fun paths(): List<String> = seen.map { it.path }

    private fun serve(s: Socket) {
        val input = BufferedInputStream(s.getInputStream())
        val head = readLine(input) ?: return
        val (method, path) = head.split(' ').let { it[0] to it[1] }
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: return
            if (l.isEmpty()) break
            headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(len).also { var got = 0; while (got < len) { val r = input.read(it, got, len - got); if (r < 0) break; got += r } }
        val req = SeenRequest(method, path, headers, String(body, Charsets.UTF_8))
        seen += req
        val (code, answer) = handler(req)
        val bytes = answer.toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    override fun close() { runCatching { server.close() } }
}

/**
 * A fake EventSub: a real WebSocket server (the stream kit's own WebSocket.kt, server side) on 127.0.0.1. Each accepted
 * connection is a [Conn] the test drives: send a message, read what the client sent back, close.
 */
class FakeEventSub : Closeable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val conns = LinkedBlockingQueue<Conn>()
    val accepted = CopyOnWriteArrayList<Conn>()
    val url: String get() = "ws://127.0.0.1:${server.localPort}/ws"
    val paths = CopyOnWriteArrayList<String>()

    inner class Conn(val socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        private val out = socket.getOutputStream()
        /** Frames the client sent (pongs, closes), as they arrive. */
        val fromClient = LinkedBlockingQueue<WebSocket.Frame>()

        fun send(json: String) = synchronized(this) { out.write(WebSocket.text(json)); out.flush() }
        fun ping(payload: String) = synchronized(this) { out.write(WebSocket.encode(WebSocket.OP_PING, payload.toByteArray())); out.flush() }
        fun closeWith(code: Int) = runCatching { synchronized(this) { out.write(WebSocket.close(code)); out.flush() } }
        fun drop() = runCatching { socket.close() }
        val closed: Boolean get() = socket.isClosed

        fun welcome(id: String, keepalive: Int = 10) =
            send("""{"metadata":{"message_id":"w-$id","message_type":"session_welcome","message_timestamp":"2026-10-05T00:00:00Z"},"payload":{"session":{"id":"$id","status":"connected","keepalive_timeout_seconds":$keepalive,"reconnect_url":null}}}""")

        fun keepalive(n: Int) = send("""{"metadata":{"message_id":"k-$n","message_type":"session_keepalive"},"payload":{}}""")

        fun chat(id: String, text: String, broadcaster: String = "141981764", chatter: String = "99", source: String? = null, meta: String = "m-$id") =
            send(chatJson(id, text, broadcaster, chatter, source, meta))

        fun reconnect(url: String) =
            send("""{"metadata":{"message_id":"r-1","message_type":"session_reconnect"},"payload":{"session":{"id":"s1","status":"reconnecting","keepalive_timeout_seconds":null,"reconnect_url":"$url"}}}""")

        fun revoke(status: String) =
            send("""{"metadata":{"message_id":"v-1","message_type":"revocation","subscription_type":"channel.chat.message"},"payload":{"subscription":{"id":"sub","status":"$status","type":"channel.chat.message","version":"1"}}}""")
    }

    init {
        Thread({
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    val c = Conn(s)
                    val headers = ArrayList<String>()
                    while (true) {
                        val sb = StringBuilder()
                        while (true) { val b = c.input.read(); if (b < 0 || b == '\n'.code) break; sb.append(b.toChar()) }
                        val l = sb.toString().trimEnd('\r')
                        if (l.isEmpty()) break
                        headers += l
                    }
                    paths += headers.first().split(' ')[1]
                    val key = headers.first { it.lowercase().startsWith("sec-websocket-key:") }.substringAfter(':').trim()
                    s.getOutputStream().write(WebSocket.handshakeResponse(key)); s.getOutputStream().flush()
                    Thread({
                        while (!s.isClosed) {
                            val f = runCatching { WebSocket.readFrame(c.input, 1 shl 16) }.getOrNull() ?: break
                            c.fromClient += f
                        }
                    }, "fake-eventsub-read").apply { isDaemon = true }.start()
                    accepted += c
                    conns += c
                }
            }
        }, "fake-eventsub").apply { isDaemon = true }.start()
    }

    fun next(seconds: Long = 5): Conn = conns.poll(seconds, TimeUnit.SECONDS) ?: error("no connection within $seconds s")

    override fun close() { runCatching { server.close() }; accepted.forEach { it.drop() } }

    companion object {
        fun chatJson(id: String, text: String, broadcaster: String = "141981764", chatter: String = "99", source: String? = null, meta: String = "m-$id"): String {
            val src = if (source == null) "null" else "\"$source\""
            return """{"metadata":{"message_id":"$meta","message_type":"notification","subscription_type":"channel.chat.message","subscription_version":"1"},"payload":{"subscription":{"id":"sub","type":"channel.chat.message"},"event":{"broadcaster_user_id":"$broadcaster","broadcaster_user_login":"streamer","broadcaster_user_name":"Streamer","chatter_user_id":"$chatter","chatter_user_login":"viewer","chatter_user_name":"Viewer","message_id":"$id","message":{"text":${com.ironmonone.app.stream.Json.write(text)},"fragments":[]},"message_type":"text","badges":[],"source_broadcaster_user_id":$src}}}"""
        }
    }
}

/** Waits up to [ms] for [cond]. */
fun waitFor(ms: Long = 5000, what: String = "condition", cond: () -> Boolean) {
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(10) }
    if (!cond()) throw AssertionError("timed out waiting for $what")
}
