package com.ironmonone.app.stream

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The stream server: what OBS on the PC reads, over the phone's Wi-Fi.
 *
 * Hand-written HTTP/1.1, GET only, because the surface is five routes and a
 * dependency here is a supply-chain and a build-step for nothing. Live data
 * goes out as Server-Sent Events, which OBS's browser source (Chromium)
 * handles natively and which is one long response on a plain socket.
 *
 *   /              index with the links
 *   /tracker       the one-box tracker page (HTML from assets)
 *   /state.json    the current snapshot
 *   /events        SSE: a `state` event whenever the snapshot changes
 *   /attempts      plain text, for an OBS text source
 *   /dex.json      every species' randomized data, for the post-game browser
 *
 * Every route needs `?k=<token>`. The token is in the URL the app shows, so
 * the only thing it stops is another device on the same network guessing the
 * port. Wi-Fi only by intent: the URL the app prints is the Wi-Fi address.
 *
 * The providers are read on the server thread; they must be cheap and
 * thread-safe (the hub keeps prebuilt strings).
 */
class StreamServer(
    private val token: String,
    private val page: () -> String,
    private val state: () -> String,
    private val version: () -> Long,
    private val attempts: () -> String,
    private val dex: () -> String,
) {
    private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    val port: Int get() = socket?.localPort ?: -1

    fun start(port: Int = 0): Int {
        val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
        socket = s
        running.set(true)
        Thread({
            while (running.get()) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                Thread({ runCatching { serve(c) }; runCatching { c.close() } }, "stream-conn").start()
            }
        }, "stream-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
    }

    private fun serve(c: Socket) {
        c.soTimeout = 15_000
        val r = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.ISO_8859_1))
        val line = r.readLine() ?: return
        while (true) { val h = r.readLine() ?: break; if (h.isEmpty()) break }
        val parts = line.split(' ')
        if (parts.size < 2 || parts[0] != "GET") return respond(c.getOutputStream(), 405, "text/plain", "GET only")
        val (path, query) = parts[1].split('?', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val q = query.split('&').filter { it.isNotEmpty() }.associate { kv ->
            val (k, v) = kv.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        val out = c.getOutputStream()
        if (q["k"] != token) return respond(out, 403, "text/plain", "Missing or wrong token. Use the URL shown in the app.")
        when (path) {
            "/", "/index.html" -> respond(out, 200, "text/html; charset=utf-8", index())
            "/tracker", "/tracker.html" -> respond(out, 200, "text/html; charset=utf-8", page())
            "/state.json" -> respond(out, 200, "application/json", state())
            "/attempts" -> respond(out, 200, "text/plain; charset=utf-8", attempts())
            "/dex.json" -> respond(out, 200, "application/json", dex())
            "/events" -> events(c, out)
            else -> respond(out, 404, "text/plain", "No such page.")
        }
    }

    private fun index(): String {
        val k = "?k=$token"
        return """<!doctype html><meta charset=utf-8><title>KaizoCore stream</title>
<body style="font-family:monospace;background:#111;color:#eee;padding:20px">
<h2>KaizoCore stream sources</h2>
<p>Add each as an OBS <b>Browser</b> source (or Text for attempts).</p>
<ul>
<li><a style="color:#8cf" href="/tracker$k">/tracker$k</a> - the tracker, one box</li>
<li><a style="color:#8cf" href="/attempts$k">/attempts$k</a> - attempt counter (OBS Text source can't read a URL; use the tracker's own counter or a browser source)</li>
<li><a style="color:#8cf" href="/state.json$k">/state.json$k</a> - raw snapshot</li>
<li><a style="color:#8cf" href="/dex.json$k">/dex.json$k</a> - every species, randomized</li>
</ul>
<p>Game video and audio: capture the phone with scrcpy over USB and use the app's CLEAN view.</p>"""
    }

    /** Server-Sent Events: one `state` event per snapshot change, a comment every 10s as keepalive. */
    private fun events(c: Socket, out: OutputStream) {
        c.soTimeout = 0
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
            "Access-Control-Allow-Origin: *\r\nConnection: keep-alive\r\n\r\n").toByteArray())
        out.flush()
        var last = -1L
        var idle = 0
        while (running.get() && !c.isClosed) {
            val v = version()
            if (v != last) {
                last = v; idle = 0
                out.write(("event: state\ndata: " + state().replace("\n", " ") + "\n\n").toByteArray())
                out.flush()
            } else if (++idle >= 40) {
                idle = 0
                out.write(": keepalive\n\n".toByteArray()); out.flush()
            }
            Thread.sleep(250)
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        out.write(("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${b.size}\r\n" +
            "Cache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(b); out.flush()
    }
}
