package com.ironmonone.app.stream

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The stream server: what OBS on the PC reads, over the phone's Wi-Fi.
 *
 * Hand-written HTTP/1.1, GET only, because the surface is a handful of routes and a
 * dependency here is a supply-chain and a build-step for nothing. Live data goes out
 * as Server-Sent Events (the tracker) and as one WebSocket (the game), both of which
 * OBS's browser source (Chromium) handles natively.
 *
 *   /                setup guide for the PC: a test of the picture, the OBS scene, the three addresses
 *   /tracker         the one-box tracker page (HTML from assets)
 *   /game            the game's picture and sound, a page for a browser source
 *   /game.ws         the WebSocket that page reads (PNG frames and PCM sound)
 *   /attempts.html   the attempt counter, styled, for a browser source
 *   /obs-scene.json  an OBS scene collection with all three, for Import Scene Collection
 *   /state.json      the current snapshot
 *   /events          SSE: a `state` event whenever the snapshot changes
 *   /attempts        plain text
 *   /dex.json        every species' randomized data, for the post-game browser
 *
 * Every route needs `?k=<token>`, /game.ws included (a browser cannot add a header to
 * a WebSocket, so it is the query there too). The token is in the URL the app shows,
 * so the only thing it stops is another device on the same network guessing the port.
 * Wi-Fi only by intent: the URL the app prints is the Wi-Fi address.
 *
 * The providers are read on the server thread; they must be cheap and thread-safe
 * (the hub keeps prebuilt strings). [feed] is where the game's picture and sound come
 * from; without one (the tests that only look at the tracker routes) /game.ws answers
 * 503. [address] is this phone's Wi-Fi address, used in the OBS scene only when a
 * request carries no usable Host header.
 *
 * 2026-09-29: /game, /game.ws, /attempts.html and /obs-scene.json were added (the stream
 * kit), and / became the setup guide it needs.
 */
class StreamServer(
    private val token: String,
    private val page: () -> String,
    private val state: () -> String,
    private val version: () -> Long,
    private val attempts: () -> String,
    /** Every species as randomized, or null while the run is going: then the page is refused (StreamHub.ended). */
    private val dex: () -> String?,
    feed: GameFeed? = null,
    private val address: () -> String? = { null },
) {
    private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val tokenBytes = token.toByteArray(Charsets.UTF_8)
    private val game: GameStream? = feed?.let { GameStream(it) }
    private val sessions = ConcurrentHashMap.newKeySet<GameSocket>()
    val port: Int get() = socket?.localPort ?: -1

    /** How many pages are watching the game right now. */
    val viewers: Int get() = game?.viewerCount ?: 0

    fun start(port: Int = 0): Int {
        val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
        socket = s
        running.set(true)
        Thread({
            while (running.get()) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                Thread({ runCatching { serve(c) }; runCatching { c.close() } }, "stream-conn").apply { isDaemon = true }.start()
            }
        }, "stream-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        // Pages watching the game are told nothing: the socket drops and they reconnect
        // by themselves when the server is back.
        sessions.forEach { it.abort() }
        game?.shutdown()
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    private fun serve(c: Socket) {
        c.soTimeout = 15_000
        val input = BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        val req = try { readRequest(input) } catch (e: IllegalArgumentException) { return respond(out, 400, "text/plain", "Bad request.") } ?: return
        if (req.method != "GET") return respond(out, 405, "text/plain", "GET only")
        if (!authorized(req.query["k"])) return respond(out, 403, "text/plain", "Missing or wrong token. Use the URL shown in the app.")
        val base = baseUrl(req)
        when (req.path) {
            "/", "/index.html" -> respond(out, 200, HTML, StreamPages.setup(base, token))
            "/tracker", "/tracker.html" -> respond(out, 200, HTML, page())
            "/game", "/game.html" -> respond(out, 200, HTML, StreamPages.game())
            "/game.ws" -> gameSocket(c, input, out, req)
            "/attempts.html" -> respond(out, 200, HTML, StreamPages.attempts())
            "/obs-scene.json" -> {
                val top = req.query["top"] == "1"
                val file = if (top) ObsScene.FILE_TOP else ObsScene.FILE
                respond(out, 200, "application/json", ObsScene.json(base, token, top),
                    "Content-Disposition: attachment; filename=\"$file\"\r\n")
            }
            "/state.json" -> respond(out, 200, "application/json", state())
            "/attempts" -> respond(out, 200, "text/plain; charset=utf-8", attempts())
            "/dex.json" -> dex()?.let { respond(out, 200, "application/json", it) }
                ?: respond(out, 403, "text/plain; charset=utf-8", "The randomized data opens when the run is over.")
            "/events" -> events(c, out)
            else -> respond(out, 404, "text/plain", "No such page.")
        }
    }

    /**
     * Reads the request line and headers. Null when the client sent nothing usable
     * (closed early, a line over 8 KB, more than 100 headers). Throws
     * IllegalArgumentException for a query string that does not decode.
     */
    private fun readRequest(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: return null
            if (h.isEmpty()) break
            if (headers.size >= MAX_HEADERS) return null
            val colon = h.indexOf(':')
            if (colon > 0) headers[h.substring(0, colon).trim().lowercase()] = h.substring(colon + 1).trim()
        }
        val parts = line.split(' ')
        if (parts.size < 2) return Request("", "", emptyMap(), headers)
        val (path, query) = parts[1].split('?', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val q = query.split('&').filter { it.isNotEmpty() }.associate { kv ->
            val (k, v) = kv.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        return Request(parts[0], path, q, headers)
    }

    /** One line of ISO-8859-1 without its CR LF; null at end of stream or past [MAX_LINE] characters. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) {
                if (sb.length >= MAX_LINE) return null
                sb.append(b.toChar())
            }
        }
    }

    /** Compared in constant time: the token is short, and the check costs nothing. */
    private fun authorized(k: String?): Boolean =
        k != null && MessageDigest.isEqual(k.toByteArray(Charsets.UTF_8), tokenBytes)

    /**
     * The address the visitor used to reach this server, for the URLs written into the
     * setup page and the OBS scene: what worked for the PC that asked is what will work
     * for OBS on it. A Host header that is not a plain host name or address (it ends up
     * in HTML and in a file) falls back to the phone's own Wi-Fi address.
     */
    private fun baseUrl(req: Request): String {
        val host = req.headers["host"]?.takeIf { HOST.matches(it) }
        return "http://" + (host ?: "${address() ?: "<phone-ip>"}:$port")
    }

    /** Upgrades the request to the game's WebSocket and serves it until either side ends it. */
    private fun gameSocket(c: Socket, input: InputStream, out: OutputStream, req: Request) {
        val stream = game ?: return respond(out, 503, "text/plain", "This build has no game feed.")
        val connection = req.headers["connection"].orEmpty().lowercase().split(',').map { it.trim() }
        val key = req.headers["sec-websocket-key"]
        if (req.headers["upgrade"]?.lowercase() != "websocket" || "upgrade" !in connection || key == null || !WebSocket.validKey(key)) {
            return respond(out, 400, "text/plain", "This address is the game's socket. Open /game for the page.")
        }
        if (req.headers["sec-websocket-version"] != "13") {
            return respond(out, 426, "text/plain", "WebSocket version 13 only.", "Sec-WebSocket-Version: 13\r\n")
        }
        val session = GameSocket(c, input, stream)
        sessions.add(session)
        try {
            if (!session.run(key)) respond(out, 503, "text/plain", "Too many pages are watching the game. Close one and try again.")
        } finally {
            sessions.remove(session)
        }
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

    private fun respond(out: OutputStream, code: Int, type: String, body: String, extra: String = "") {
        val b = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            426 -> "Upgrade Required"; 503 -> "Service Unavailable"; else -> "Error"
        }
        out.write(("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${b.size}\r\n" +
            "Cache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\n" + extra + "Connection: close\r\n\r\n").toByteArray())
        out.write(b); out.flush()
    }

    private companion object {
        const val HTML = "text/html; charset=utf-8"
        const val MAX_LINE = 8192
        const val MAX_HEADERS = 100
        /** A host name, an IPv4 address or a bracketed IPv6 one, then optionally a port. */
        val HOST = Regex("(?:[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}\\])(?::[0-9]{1,5})?")
    }
}
