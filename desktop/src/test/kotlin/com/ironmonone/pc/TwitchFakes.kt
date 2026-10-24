package com.ironmonone.pc

import com.ironmonone.app.stream.WebSocket
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
 * The app's own Twitch fakes (app/src/test/.../TwitchFakes.kt), here for the PC's chat: a real HTTP server for
 * id.twitch.tv and Helix, and a real WebSocket server standing in for EventSub, both on 127.0.0.1. Nothing here talks
 * to Twitch.
 */

class SeenRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
    /** The body's string fields (Send Chat Message's body is flat): "key":"value" pairs, the escapes undone. */
    val json: Map<String, String> get() = Regex("\"([A-Za-z_]+)\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body)
        .associate { m -> m.groupValues[1] to m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n") }
}

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

class FakeEventSub : Closeable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val conns = LinkedBlockingQueue<Conn>()
    val accepted = CopyOnWriteArrayList<Conn>()
    val url: String get() = "ws://127.0.0.1:${server.localPort}/ws"

    inner class Conn(val socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        private val out = socket.getOutputStream()

        fun send(json: String) = synchronized(this) { out.write(WebSocket.text(json)); out.flush() }
        fun drop() = runCatching { socket.close() }

        fun welcome(id: String, keepalive: Int = 10) =
            send("""{"metadata":{"message_id":"w-$id","message_type":"session_welcome","message_timestamp":"2026-10-07T00:00:00Z"},"payload":{"session":{"id":"$id","status":"connected","keepalive_timeout_seconds":$keepalive,"reconnect_url":null}}}""")

        /** A chat line with [badges] as Twitch lists them (set_id each). */
        fun chat(id: String, text: String, broadcaster: String, chatter: String = "99", badges: List<String> = emptyList()) =
            send(chatJson(id, text, broadcaster, chatter, badges))
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
                    val key = headers.first { it.lowercase().startsWith("sec-websocket-key:") }.substringAfter(':').trim()
                    s.getOutputStream().write(WebSocket.handshakeResponse(key)); s.getOutputStream().flush()
                    Thread({
                        while (!s.isClosed) { runCatching { WebSocket.readFrame(c.input, 1 shl 16) }.getOrNull() ?: break }
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
        fun chatJson(id: String, text: String, broadcaster: String, chatter: String, badges: List<String>): String {
            val b = badges.joinToString(",") { """{"set_id":"$it","id":"1","info":""}""" }
            return """{"metadata":{"message_id":"m-$id","message_type":"notification","subscription_type":"channel.chat.message","subscription_version":"1"},"payload":{"subscription":{"id":"sub","type":"channel.chat.message"},"event":{"broadcaster_user_id":"$broadcaster","broadcaster_user_login":"streamer","broadcaster_user_name":"Streamer","chatter_user_id":"$chatter","chatter_user_login":"viewer","chatter_user_name":"Viewer","message_id":"$id","message":{"text":${com.ironmonone.app.stream.Json.write(text)},"fragments":[]},"message_type":"text","badges":[$b],"source_broadcaster_user_id":null}}}"""
        }
    }
}

fun waitFor(ms: Long = 5000, what: String = "condition", cond: () -> Boolean) {
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(10) }
    if (!cond()) throw AssertionError("timed out waiting for $what")
}
