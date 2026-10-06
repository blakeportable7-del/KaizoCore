package com.ironmonone.app.stream

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The stream server: what OBS on the PC reads, over the phone's Wi-Fi or a USB cable.
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
 *   /favorite/1.png  each favorite's picture, 1 to 9, a see-through PNG (StreamFavorites)
 *   /favorite/1      a page showing it, for a browser source, that follows the favorites as they change
 *   /gameover        the game over card: see-through while the run goes on, a card when it ends (StreamOverlays)
 *   /gameover.json   that card's data, "null" while the run goes on
 *   /timer           the run timer and its splits, one a badge (StreamOverlays, StreamTimer)
 *   /timer.json      the timer's data
 *   /mon/25.png      a species' picture for the game over card, a see-through PNG (the empty one where there is none)
 *   /history         the run history for viewers: past attempts on this game and mode, the best, what ends runs (StreamHistory)
 *   /history.json    the same facts as JSON
 *
 * /tracker, /attempts.html, /gameover and /timer take `&theme=clean` or `&theme=hud` for another look (StreamThemes).
 * /events also sends `gameover` and `timer` events, beside `state`.
 *
 * 2026-10-05 (streamer list items 1, 3 and 6): /gameover, /timer, their JSON, /mon and the looks were added.
 * 2026-10-05 (streamer list items 2 and 4): /history and /history.json were added.
 *
 * Every route needs `?k=<token>`, /game.ws included (a browser cannot add a header to
 * a WebSocket, so it is the query there too). The token is in the URL the app shows,
 * so the only thing it stops is another device on the same network guessing the port.
 *
 * Two ways in, equal (2026-10-06, Blake: "do both wifi and wired"): the phone's Wi-Fi address, and the USB cable, where
 * the PC runs `adb forward tcp:8642 tcp:8642` (StreamHub.ADB_FORWARD) and opens 127.0.0.1:8642. adbd opens the PC's
 * connection on the phone's own loopback, which [local] admits and the bind on every interface ([start]) serves, so
 * nothing here differs by the way in except the guide's word for a loopback visitor (StreamPages.onPhone): the phone's
 * own browser, or a PC on the cable.
 *
 * The providers are read on the server thread; they must be cheap and thread-safe
 * (the hub keeps prebuilt strings). [feed] is where the game's picture and sound come
 * from; without one (the tests that only look at the tracker routes) /game.ws answers
 * 503. [address] is this phone's Wi-Fi address, used in the OBS scene only when a
 * request carries no usable Host header.
 *
 * 2026-09-29: /game, /game.ws, /attempts.html and /obs-scene.json were added (the stream
 * kit), and / became the setup guide it needs.
 *
 * rc33 audit P1 #58, #59: before the token is checked, a connection costs a thread. So a connection is served only
 * from this network ([local]) and only while fewer than [maxOpen] are open; the request line and headers have
 * [requestMs] in all, however slowly they trickle in, and every header line counts; a thread that cannot start refuses
 * that connection and nothing else; and an accept() that fails (a Wi-Fi blip, no descriptors left) is waited out
 * rather than ending the server while the app still says STREAM ON.
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
    private val maxOpen: Int = MAX_OPEN,
    private val requestMs: Long = REQUEST_MS,
    /** The run is over (StreamHub.ended): a [dex] still null is being built, not refused. */
    private val runOver: () -> Boolean = { false },
    /** Favorite [n]'s picture, 1 to StreamFavorites.SLOTS, as a PNG; null for a box with none (StreamHub.favorites). */
    private val favorite: (Int) -> ByteArray? = { null },
    /** The game over card's JSON, "null" while the run goes on (StreamHub.gameOver). */
    private val gameOver: () -> String = { "null" },
    /** The run timer's JSON (StreamHub.timerJson). */
    private val timer: () -> String = { StreamTimer.NO_RUN },
    /** The looks' CSS, put into every page that takes `?theme=` (StreamThemes). */
    private val themes: () -> String = { "" },
    /** A species' picture as a PNG, for the game over card (StreamHub.monPicture); null for none. */
    private val mon: (Int) -> ByteArray? = { null },
    /** The run history's facts (StreamHistory.facts), read on each request for /history and /history.json. */
    private val history: () -> Map<String, Any?> = { StreamHistory.facts(null, null, emptyList(), null) },
) {
    private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val tokenBytes = token.toByteArray(Charsets.UTF_8)
    private val game: GameStream? = feed?.let { GameStream(it) }
    private val sessions = ConcurrentHashMap.newKeySet<GameSocket>()
    private val open = AtomicInteger(0)
    val port: Int get() = socket?.localPort ?: -1

    /** How many pages are watching the game right now. */
    val viewers: Int get() = game?.viewerCount ?: 0

    fun start(port: Int = 0): Int {
        val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
        socket = s
        running.set(true)
        Thread({
            while (running.get()) {
                val c = try { s.accept() } catch (e: Throwable) {
                    if (!running.get() || s.isClosed) break
                    runCatching { Thread.sleep(250) }
                    continue
                }
                if (!admit(c)) { runCatching { c.close() }; continue }
                try {
                    Thread({
                        try { runCatching { serve(c) } } finally { runCatching { c.close() }; open.decrementAndGet() }
                    }, "stream-conn").apply { isDaemon = true }.start()
                } catch (e: Throwable) {
                    runCatching { c.close() }; open.decrementAndGet()
                }
            }
        }, "stream-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    /** Taken before anything is spent on [c]: from this network, and room for it under [maxOpen]. */
    private fun admit(c: Socket): Boolean {
        if (!local(c.inetAddress)) return false
        if (open.incrementAndGet() > maxOpen) { open.decrementAndGet(); return false }
        return true
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
        val input = BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        val deadline = System.currentTimeMillis() + requestMs
        val req = try { readRequest(input, c, deadline) } catch (e: IllegalArgumentException) { return respond(out, 400, "text/plain", "Bad request.") } ?: return
        c.soTimeout = 15_000
        if (req.method != "GET") return respond(out, 405, "text/plain", "GET only")
        if (!authorized(req.query["k"])) return respond(out, 403, "text/plain", "Missing or wrong token. Use the URL shown in the app.")
        val base = baseUrl(req)
        when (req.path) {
            "/", "/index.html" -> respond(out, 200, HTML, StreamPages.setup(base, token, req.headers))
            "/tracker", "/tracker.html" -> respond(out, 200, HTML, themed(page(), req))
            "/game", "/game.html" -> respond(out, 200, HTML, StreamPages.game())
            "/game.ws" -> gameSocket(c, input, out, req)
            "/attempts.html" -> respond(out, 200, HTML, themed(StreamPages.attempts(), req))
            "/gameover", "/gameover.html" -> respond(out, 200, HTML, themed(StreamOverlays.gameOver(), req))
            "/gameover.json" -> respond(out, 200, "application/json", gameOver())
            "/timer", "/timer.html" -> respond(out, 200, HTML, themed(StreamOverlays.timer(), req))
            "/timer.json" -> respond(out, 200, "application/json", timer())
            "/obs-scene.json" -> {
                val top = req.query["top"] == "1"
                val file = if (top) ObsScene.FILE_TOP else ObsScene.FILE
                respond(out, 200, "application/json", ObsScene.json(base, token, top),
                    "Content-Disposition: attachment; filename=\"$file\"\r\n")
            }
            "/state.json" -> respond(out, 200, "application/json", state())
            "/attempts" -> respond(out, 200, "text/plain; charset=utf-8", attempts())
            // Over but not built yet (StreamFeed builds it once the run has ended) is a wait, which the page asks again
            // after: it was the same refusal as a run still going, and the page said it could not load the data
            // (RC35-NOTICED N #7).
            "/dex.json" -> dex()?.let { respond(out, 200, "application/json", it) }
                ?: if (runOver()) respond(out, 503, "text/plain; charset=utf-8", DEX_BUILDING)
                else respond(out, 403, "text/plain; charset=utf-8", "The randomized data opens when the run is over.")
            "/events" -> events(c, out)
            "/history", "/history.html" -> respond(out, 200, HTML, StreamHistory.page(historyFacts(), req.query))
            "/history.json" -> respond(out, 200, "application/json", StreamHistory.json(historyFacts()))
            else -> {
                val fav = StreamFavorites.route(req.path)
                val species = StreamOverlays.monRoute(req.path)
                when {
                    species != null -> picture(out, runCatching { mon(species) }.getOrNull(), req)
                    fav == null -> respond(out, 404, "text/plain", "No such page.")
                    fav.second -> favoritePicture(out, fav.first, req)
                    else -> respond(out, 200, HTML, StreamFavorites.page())
                }
            }
        }
    }

    /** The history's facts, or a page with no runs when reading them failed: a bad file never costs the server a thread. */
    private fun historyFacts(): Map<String, Any?> =
        runCatching { history() }.getOrNull() ?: StreamHistory.facts(null, null, emptyList(), null)

    /**
     * Favorite [n]'s picture, or the empty one (StreamFavorites.EMPTY) where there is none: always a 200 and a PNG, so
     * OBS shows nothing rather than an error. Its ETag lets the page ask "still this one?" and get a 304 with no body.
     */
    private fun favoritePicture(out: OutputStream, n: Int, req: Request) = picture(out, runCatching { favorite(n) }.getOrNull(), req)

    /** [png], or the empty picture where there is none, with the ETag the favorites' pages ask with. */
    private fun picture(out: OutputStream, png: ByteArray?, req: Request) {
        val body = png ?: StreamFavorites.EMPTY
        val tag = StreamFavorites.etag(body)
        if (req.headers["if-none-match"] == tag) return respondBytes(out, 304, null, ByteArray(0), "ETag: $tag\r\n")
        respondBytes(out, 200, "image/png", body, "ETag: $tag\r\n")
    }

    /** [html] in the look its address asks for (`?theme=`), the looks' CSS put in before its head ends (StreamThemes). */
    private fun themed(html: String, req: Request): String = StreamThemes.apply(html, req.query["theme"], themes())

    /**
     * Reads the request line and headers, by [deadline] at the latest. Null when the client sent nothing usable
     * (closed early, a line over 8 KB, more than 100 header lines, too slow). Throws IllegalArgumentException for a
     * query string that does not decode.
     */
    private fun readRequest(input: InputStream, c: Socket, deadline: Long): Request? {
        val line = readLine(input, c, deadline) ?: return null
        val headers = LinkedHashMap<String, String>()
        var lines = 0
        while (true) {
            val h = readLine(input, c, deadline) ?: return null
            if (h.isEmpty()) break
            // Every line counts: a repeated name or a line with no colon used not to, so the read never ended.
            if (++lines > MAX_HEADERS) return null
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

    /** One line of ISO-8859-1 without its CR LF; null at end of stream, past [MAX_LINE] characters or past [deadline]. */
    private fun readLine(input: InputStream, c: Socket, deadline: Long): String? {
        val sb = StringBuilder()
        val left = deadline - System.currentTimeMillis()
        if (left <= 0) return null
        // A read waits no longer than the request has left, and the clock is read after every byte: a byte now and then
        // no longer keeps the thread past the deadline (by one wait at most).
        c.soTimeout = left.toInt().coerceAtLeast(1)
        while (true) {
            val b = try { input.read() } catch (e: java.net.SocketTimeoutException) { return null }
            if (System.currentTimeMillis() > deadline) return null
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
            // A request still arriving when the stream was turned off: stop() clears [running] before it walks the
            // sessions, so a session added after that walk sees it here and is dropped, and one added before is aborted
            // there (rc32 audit P3 #82). It was served, and switched the emulator's taps back on.
            if (!running.get()) return
            if (!session.run(key)) respond(out, 503, "text/plain", "Too many pages are watching the game. Close one and try again.")
        } finally {
            sessions.remove(session)
        }
    }

    /**
     * Server-Sent Events: one `state` event per snapshot change, a `gameover` event whenever the game over card's data
     * changes (looked at four times a second), a `timer` event whenever the timer's changes (looked at once a second,
     * which while it counts is every second), and a comment every 10s of quiet as keepalive. Each is sent once as the
     * page connects, so a page opened mid-run starts right. A page ignores the events it does not listen for.
     */
    private fun events(c: Socket, out: OutputStream) {
        c.soTimeout = 0
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
            "Access-Control-Allow-Origin: *\r\nConnection: keep-alive\r\n\r\n").toByteArray())
        out.flush()
        var last = -1L
        var lastOver: String? = null
        var lastTimer: String? = null
        var idle = 0
        var tick = 0
        fun send(event: String, data: String) {
            out.write(("event: $event\ndata: " + data.replace("\n", " ") + "\n\n").toByteArray())
            idle = 0
        }
        while (running.get() && !c.isClosed) {
            val v = version()
            if (v != last) { last = v; send("state", state()) }
            val over = runCatching { gameOver() }.getOrDefault("null")
            if (over != lastOver) { lastOver = over; send("gameover", over) }
            if (tick++ % 4 == 0) {
                val t = runCatching { timer() }.getOrDefault(StreamTimer.NO_RUN)
                if (t != lastTimer) { lastTimer = t; send("timer", t) }
            }
            if (idle == 0) out.flush()
            if (++idle >= 40) {
                idle = 0
                out.write(": keepalive\n\n".toByteArray()); out.flush()
            }
            Thread.sleep(250)
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String, extra: String = "") =
        respondBytes(out, code, type, body.toByteArray(Charsets.UTF_8), extra)

    /** A whole answer with [b] as its body. A [type] of null sends no body headers: a 304, which has no body. */
    private fun respondBytes(out: OutputStream, code: Int, type: String?, b: ByteArray, extra: String = "") {
        val reason = when (code) {
            200 -> "OK"; 304 -> "Not Modified"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            426 -> "Upgrade Required"; 503 -> "Service Unavailable"; else -> "Error"
        }
        val head = StringBuilder("HTTP/1.1 $code $reason\r\n")
        if (type != null) head.append("Content-Type: $type\r\nContent-Length: ${b.size}\r\n")
        head.append("Cache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\n").append(extra).append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (type != null) out.write(b)
        out.flush()
    }

    companion object {
        private const val HTML = "text/html; charset=utf-8"
        private const val MAX_LINE = 8192
        private const val MAX_HEADERS = 100
        /** Connections open at once: OBS needs a handful (the tracker's events, six game pages at most, a page load). */
        const val MAX_OPEN = 32
        /** The request line and headers, all of them, before the token is read. */
        const val REQUEST_MS = 5_000L
        /** /dex.json once the run is over, while the data is still being put together. */
        const val DEX_BUILDING = "The randomized data is being put together. Try again in a moment."

        /**
         * A peer on this network: loopback (the phone's own browser, and a PC on the USB cable through adbd), the private ranges, link-local, IPv6 unique-local, and the carrier-grade range
         * (100.64.0.0/10) a VPN such as Tailscale hands out. Anything else, a public address reaching the phone through
         * an unfiltered IPv6 route say, is closed before it costs a thread.
         */
        fun local(a: InetAddress?): Boolean {
            if (a == null) return false
            if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress) return true
            val b = a.address
            return when (a) {
                is Inet4Address -> (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 0x40
                is Inet6Address -> (b[0].toInt() and 0xFE) == 0xFC
                else -> false
            }
        }
        /** A host name, an IPv4 address or a bracketed IPv6 one, then optionally a port. */
        private val HOST = Regex("(?:[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}\\])(?::[0-9]{1,5})?")
    }
}
