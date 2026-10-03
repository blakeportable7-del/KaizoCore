package com.ironmonone.app.stream

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamServerTest {

    private fun get(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to body
    }

    /** What a raw GET answered: status code, lower-cased headers and body. */
    private class Answer(val code: Int, val headers: Map<String, String>, val body: String)

    /** A GET over a raw socket, for the headers HttpURLConnection will not let a test set (Host, Upgrade). */
    private fun raw(port: Int, path: String, headers: Map<String, String> = emptyMap()): Answer {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5000
            val req = StringBuilder("GET $path HTTP/1.1\r\n")
            if (headers.keys.none { it.equals("Host", true) }) req.append("Host: 127.0.0.1:$port\r\n")
            headers.forEach { (k, v) -> req.append("$k: $v\r\n") }
            req.append("\r\n")
            s.getOutputStream().write(req.toString().toByteArray()); s.getOutputStream().flush()
            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val status = r.readLine()
            val h = LinkedHashMap<String, String>()
            while (true) {
                val l = r.readLine() ?: break
                if (l.isEmpty()) break
                h[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
            }
            return Answer(status.split(' ')[1].toInt(), h, r.readText())
        }
    }

    private fun server(feed: GameFeed? = FakeGameFeed(), token: String = "abcd", state: () -> String = { "{}" }, address: () -> String? = { null }): StreamServer =
        StreamServer(token, { "<h1>page</h1>" }, state, { 1L }, { "42" }, { "[]" }, feed, address)

    // ------------------------------------------------------------------ the routes that were here first

    @Test
    fun `routes answer, the token gates everything, unknown paths are 404`() {
        var v = 1L
        val s = StreamServer("abcd", { "<h1>page</h1>" }, { """{"a":1}""" }, { v }, { "42" }, { "[]" })
        val port = s.start(0)
        try {
            assertEquals(200 to """{"a":1}""", get(port, "/state.json?k=abcd"))
            assertEquals(200 to "42", get(port, "/attempts?k=abcd"))
            assertEquals(200 to "<h1>page</h1>", get(port, "/tracker?k=abcd"))
            assertEquals(200 to "[]", get(port, "/dex.json?k=abcd"))
            assertEquals(403, get(port, "/state.json").first)
            assertEquals(403, get(port, "/state.json?k=nope").first)
            assertEquals(404, get(port, "/other?k=abcd").first)
            assertContains(get(port, "/?k=abcd").second, "/tracker?k=abcd")
        } finally { s.stop() }
    }

    @Test
    fun `the randomized data is refused until the run is over`() {
        var dex: String? = null
        val s = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "0" }, { dex })
        val port = s.start(0)
        try {
            val refused = get(port, "/dex.json?k=abcd")
            assertEquals(403, refused.first)
            assertEquals("The randomized data opens when the run is over.", refused.second)
            dex = "[{\"species\":1}]"
            assertEquals(200 to "[{\"species\":1}]", get(port, "/dex.json?k=abcd"))
            assertEquals(403, get(port, "/dex.json").first, "and never without the key")
        } finally { s.stop() }
    }

    /**
     * RC35-NOTICED N #7: once the run was over the data was still being built for a moment, and /dex.json refused it as it
     * does during a run, so the page said it could not load the data. Over and not built yet is a wait now.
     */
    @Test
    fun `the randomized data still being built after the run is a wait, not a refusal`() {
        var dex: String? = null
        var over = false
        val s = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "0" }, { dex }, runOver = { over })
        val port = s.start(0)
        try {
            assertEquals(403 to "The randomized data opens when the run is over.", get(port, "/dex.json?k=abcd"), "the run is going")
            over = true
            assertEquals(503 to StreamServer.DEX_BUILDING, get(port, "/dex.json?k=abcd"), "over, and the data is being built")
            dex = "[]"
            assertEquals(200 to "[]", get(port, "/dex.json?k=abcd"))
            assertEquals(403, get(port, "/dex.json").first, "and never without the key")
        } finally { s.stop() }
        assertContains(java.io.File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText(), "runOver = { ended != null }")
    }

    @Test
    fun `the hub serves the data only once the run has latched its end`() {
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText()
        assertContains(src, "{ if (ended != null) dex else null }")
        val host = java.io.File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertContains(host, "val ended = if (latch.applies) latch.outcome else null")
        assertContains(host, "SideEffect { com.ironmonone.app.stream.StreamHub.ended = ended }")
    }

    @Test
    fun `events is a server-sent stream that emits on version change`() {
        var v = 1L
        var state = """{"n":1}"""
        val s = StreamServer("t", { "" }, { state }, { v }, { "0" }, { "[]" })
        val port = s.start(0)
        try {
            Socket("127.0.0.1", port).use { sock ->
                sock.soTimeout = 5000
                sock.getOutputStream().write("GET /events?k=t HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                sock.getOutputStream().flush()
                val r = BufferedReader(InputStreamReader(sock.getInputStream()))
                val head = generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                assertTrue(head.first().startsWith("HTTP/1.1 200"), head.first())
                assertTrue(head.any { it.contains("text/event-stream") })
                assertEquals("event: state", r.readLine())
                assertEquals("data: {\"n\":1}", r.readLine())
                state = """{"n":2}"""; v = 2
                // skip the blank separator, then the next event
                val lines = generateSequence { r.readLine() }.filter { it.isNotEmpty() }.take(2).toList()
                assertEquals(listOf("event: state", "data: {\"n\":2}"), lines)
            }
        } finally { s.stop() }
    }

    @Test
    fun `json writer escapes and shapes`() {
        assertEquals("""{"a":"x\"y\n","b":[1,2.5,true,null],"c":{}}""",
            Json.write(linkedMapOf("a" to "x\"y\n", "b" to listOf(1, 2.5, true, null), "c" to emptyMap<String, Any>())))
    }

    // ------------------------------------------------------------------ the token gates the new routes too

    @Test
    fun `the game page, the socket, the scene and the counter all refuse a missing or wrong token`() {
        val s = server()
        val port = s.start(0)
        try {
            for (path in listOf("/game", "/game.ws", "/obs-scene.json", "/attempts.html", "/obs-scene.json?top=1")) {
                assertEquals(403, raw(port, path).code, "$path with no key")
                assertEquals(403, raw(port, "$path${if ('?' in path) '&' else '?'}k=nope").code, "$path with a wrong key")
                assertEquals(403, raw(port, "$path${if ('?' in path) '&' else '?'}k=abcde").code, "$path with a key that only starts right")
                assertEquals(403, raw(port, "$path${if ('?' in path) '&' else '?'}k=").code, "$path with an empty key")
            }
            // A real upgrade request with the wrong key must not get a 101.
            val upgrade = mapOf("Upgrade" to "websocket", "Connection" to "Upgrade", "Sec-WebSocket-Key" to WsTestClient.KEY, "Sec-WebSocket-Version" to "13")
            assertEquals(403, raw(port, "/game.ws?k=wrong", upgrade).code)
            assertEquals(403, raw(port, "/game.ws", upgrade).code)
            WsTestClient(port, "/game.ws?k=wrong").use { c -> assertTrue(c.status.contains(" 403 "), c.status) }
            assertFalse(s.viewers > 0, "a refused socket is not a viewer")
        } finally { s.stop() }
    }

    @Test
    fun `a refused socket never turns the capture switch on`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=wrong").use { }
            WsTestClient(port, "/game.ws").use { }
            // A socket with the key as a barrier, not a sleep (rc32 audit P3 #83): once its capture is on, the log holds
            // that one switch and nothing the refused sockets did before it.
            WsTestClient(port, "/game.ws?k=abcd").use {
                assertTrue(waitFor { feed.capturing }, "the socket with the key turns the capture on")
                assertEquals(listOf(true), feed.captureLog.toList())
            }
        } finally { s.stop() }
    }

    // ------------------------------------------------------------------ the pages

    @Test
    fun `with the key the game page, the counter and the scene answer`() {
        val s = server()
        val port = s.start(0)
        try {
            val game = raw(port, "/game?k=abcd")
            assertEquals(200, game.code)
            assertContains(game.headers.getValue("content-type"), "text/html")
            assertContains(game.body, "/game.ws?k=")
            assertContains(game.body, "<canvas")

            val attempts = raw(port, "/attempts.html?k=abcd")
            assertEquals(200, attempts.code)
            assertContains(attempts.body, "/events?k=")

            val scene = raw(port, "/obs-scene.json?k=abcd")
            assertEquals(200, scene.code)
            assertEquals("application/json", scene.headers.getValue("content-type"))
            assertContains(scene.headers.getValue("content-disposition"), "attachment")
            assertContains(scene.headers.getValue("content-disposition"), "KaizoCore-stream.json")
            assertContains(MiniJson(scene.body).parse().toString(), "KaizoCore game")

            val top = raw(port, "/obs-scene.json?k=abcd&top=1")
            assertContains(top.headers.getValue("content-disposition"), "top-screen")
            assertContains(top.body, "&top=1")
            assertFalse(scene.body.contains("top=1"))
        } finally { s.stop() }
    }

    @Test
    fun `the scene points at the address the request came to`() {
        val s = server(address = { "10.0.0.7" })
        val port = s.start(0)
        try {
            val a = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to "192.168.1.50:8642"))
            val urls = Regex("\"url\":\"([^\"]+)\"").findAll(a.body).map { it.groupValues[1] }.toList()
            assertEquals(3 + StreamFavorites.SLOTS, urls.size, "the game, the tracker, the attempts and each favorite")
            assertTrue(urls.all { it.startsWith("http://192.168.1.50:8642/") && it.contains("k=abcd") }, urls.toString())

            val named = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to "pixel-7.local:8642"))
            assertContains(named.body, "http://pixel-7.local:8642/game?k=abcd")

            // A Host that is not a plain host name is not trusted: it lands in a file and in HTML.
            for (bad in listOf("evil\"><script>alert(1)</script>", "a b", "x/y", "h:99999999", "")) {
                val r = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to bad))
                assertContains(r.body, "http://10.0.0.7:$port/game?k=abcd", message = "a Host of '$bad' falls back to the phone's own address")
                assertFalse(r.body.contains("script"))
            }
        } finally { s.stop() }
    }

    @Test
    fun `with no usable address anywhere the scene says so plainly rather than inventing one`() {
        val s = server(address = { null })
        val port = s.start(0)
        try {
            val r = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to "bad host"))
            assertContains(r.body, "<phone-ip>:$port")
            val page = raw(port, "/?k=abcd", mapOf("Host" to "bad host"))
            assertFalse(page.body.contains("<phone-ip>"), "in the page the placeholder is escaped")
            assertContains(page.body, "&lt;phone-ip&gt;")
        } finally { s.stop() }
    }

    @Test
    fun `the setup page is three steps, advice for while streaming, what to do when nothing shows, then the links`() {
        val s = server(address = { "10.0.0.7" })
        val port = s.start(0)
        try {
            val page = raw(port, "/?k=abcd", mapOf("Host" to "192.168.1.50:8642")).body
            // The three steps, then the rest, in order (StreamGuideTest holds the sentences).
            val i1 = page.indexOf("Download the OBS scene")
            val i2 = page.indexOf("Add it to OBS")
            val i3 = page.indexOf("What you should see</h2>")
            val nothing = page.indexOf("If nothing shows")
            val links = page.indexOf("The links, one at a time")
            assertTrue(i1 in 0 until i2 && i2 < i3 && i3 < nothing && nothing < links, "sections in order: $i1 $i2 $i3 $nothing $links")
            assertContains(page, "Import Scene Collection")
            assertContains(page, "KaizoCore stream")
            assertContains(page, "/obs-scene.json?k=abcd")
            assertContains(page, "/obs-scene.json?k=abcd&top=1")
            // The individual links, with this address and the key.
            assertContains(page, "http://192.168.1.50:8642/game?k=abcd")
            assertContains(page, "http://192.168.1.50:8642/tracker?k=abcd")
            assertContains(page, "http://192.168.1.50:8642/attempts.html?k=abcd")
            assertContains(page, "/state.json?k=abcd")
            assertFalse(page.contains("/dex.json"), "the randomized data is not linked: it opens when the run is over")
            // What to do if nothing shows.
            assertContains(page, "If nothing shows")
            assertContains(page, "same Wi-Fi", ignoreCase = true)
            assertContains(page, "address changed", ignoreCase = true)
            assertContains(page, "router", ignoreCase = true)
        } finally { s.stop() }
    }

    @Test
    fun `the setup page says it works for any game and never asks for a run`() {
        val s = server()
        val port = s.start(0)
        try {
            val page = raw(port, "/?k=abcd").body
            assertContains(page, "any game")
            for (mode in listOf("Kaizo IronMON", "Nuzlocke", "ROM hacks", "library")) assertContains(page, mode, ignoreCase = true)
            assertFalse(Regex("(only|needs?|requires?) (an? )?(IronMON )?run", RegexOption.IGNORE_CASE).containsMatchIn(page))
            assertContains(page, "attempt counter", ignoreCase = true)
            assertContains(page, "any other game", ignoreCase = true)
        } finally { s.stop() }
    }

    @Test
    fun `the copy on every page follows the house rules`() {
        val s = server()
        val port = s.start(0)
        try {
            // The guide is asked for from a PC and from the phone itself: they differ (the downloads), so both are read.
            val pages = listOf("/?k=abcd" to "127.0.0.1:$port", "/?k=abcd" to "192.168.1.50:$port", "/game?k=abcd" to "127.0.0.1:$port",
                "/attempts.html?k=abcd" to "127.0.0.1:$port", "/obs-scene.json?k=abcd" to "127.0.0.1:$port", "/favorite/1?k=abcd" to "127.0.0.1:$port")
            for ((path, host) in pages) {
                val body = raw(port, path, mapOf("Host" to host)).body
                assertFalse(body.contains(0x2014.toChar()), "no em dash on $path at $host")
                assertFalse(body.contains(0x2013.toChar()), "no en dash on $path at $host")
                assertFalse(Regex("\\bAI\\b|artificial intelligence|machine learning", RegexOption.IGNORE_CASE).containsMatchIn(body), "no mention of AI on $path")
            }
        } finally { s.stop() }
    }

    @Test
    fun `the guide opened at the phone's own address says so, and opened from the PC it does not`() {
        val s = server(address = { "10.0.0.7" })
        val port = s.start(0)
        try {
            for (host in listOf("localhost:$port", "127.0.0.1:$port", "[::1]:$port")) {
                val page = raw(port, "/?k=abcd", mapOf("Host" to host)).body
                assertContains(page, "Open this page on your PC, not on the phone.", message = host)
                assertFalse(page.contains("download="), "a scene made at $host would point at localhost, so none is offered")
            }
            val pc = raw(port, "/?k=abcd", mapOf("Host" to "192.168.1.50:$port")).body
            assertFalse(pc.contains("Open this page on your PC"))
            assertContains(pc, "download=\"KaizoCore-stream.json\"")
        } finally { s.stop() }
    }

    @Test
    fun `the files the guide says it saves are the files the server sends`() {
        val s = server()
        val port = s.start(0)
        try {
            val page = raw(port, "/?k=abcd", mapOf("Host" to "192.168.1.50:$port")).body
            val links = Regex("href=\"(/obs-scene\\.json[^\"]*)\" download=\"([^\"]+)\"").findAll(page).map { it.groupValues[1] to it.groupValues[2] }.toList()
            assertEquals(2, links.size, "the scene and the top screen scene")
            for ((href, name) in links) {
                val sent = raw(port, href)
                assertEquals(200, sent.code, href)
                assertContains(sent.headers.getValue("content-disposition"), "filename=\"$name\"", message = "the file the guide names for $href")
            }
        } finally { s.stop() }
    }

    // ------------------------------------------------------------------ the socket

    private fun rgb(w: Int, h: Int, seed: Long) = TestPics.structured(w, h, seed)

    @Test
    fun `the socket handshakes, says hello, then carries pictures and sound`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c ->
                assertContains(c.status, " 101 ")
                assertEquals("websocket", c.headers["upgrade"]?.lowercase())
                assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", c.headers["sec-websocket-accept"], "the RFC's own example")

                val hello = assertNotNull(c.nextData())
                assertEquals(WebSocket.OP_TEXT, hello.opcode)
                assertEquals("{\"type\":\"hello\",\"protocol\":1}", String(hello.payload, Charsets.UTF_8))

                val pic = rgb(32, 20, 4)
                feed.push(32, 20, pic)
                val v = assertNotNull(c.nextData())
                assertEquals(WebSocket.OP_BINARY, v.opcode)
                assertEquals(GameWire.VIDEO, v.payload[0].toInt())
                assertContentEquals(pic, TestPics.decode(v.payload.copyOfRange(GameWire.HEADER, v.payload.size)).third)

                val pcm = ByteArray(16 * 4) { (it * 5).toByte() }
                feed.pushSound(pcm, 65536)
                val a = assertNotNull(c.nextData())
                assertEquals(GameWire.AUDIO, a.payload[0].toInt())
                assertContentEquals(pcm, a.payload.copyOfRange(GameWire.HEADER, a.payload.size))
            }
        } finally { s.stop() }
    }

    @Test
    fun `the game is served whatever the snapshot says, run or not, tracker or not, nothing at all`() {
        val snapshots = listOf(
            "{}",                                                                                          // no game announced yet
            """{"app":"KaizoCore","title":"Some ROM hack","platform":"GBA","attempt":812,"tracked":false,"run":false,"seed":null}""",
            """{"app":"KaizoCore","title":"Library game","platform":"NDS","attempt":3,"tracked":true,"run":false,"seed":null}""",
            """{"app":"KaizoCore","title":"Kaizo run","platform":"GBA","attempt":5,"tracked":true,"run":true,"seed":"abc"}""",
        )
        for (snap in snapshots) {
            val feed = FakeGameFeed()
            val s = server(feed, state = { snap })
            val port = s.start(0)
            try {
                WsTestClient(port, "/game.ws?k=abcd").use { c ->
                    assertContains(c.status, " 101 ", message = snap)
                    feed.push(16, 10, rgb(16, 10, 6))
                    val got = generateSequence { assertNotNull(c.nextData(), "the socket stays up for $snap") }
                        .first { it.opcode == WebSocket.OP_BINARY }
                    assertEquals(GameWire.VIDEO, got.payload[0].toInt(), snap)
                }
            } finally { s.stop() }
        }
    }

    @Test
    fun `a ping is answered with a pong that carries the same bytes`() {
        val s = server()
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c ->
                assertNotNull(c.nextData())                         // the hello
                c.send(WebSocket.OP_PING, "are you there".toByteArray())
                var f: ServerFrame
                do { f = assertNotNull(c.next()) } while (f.opcode == WebSocket.OP_PING || f.opcode == WebSocket.OP_BINARY)
                assertEquals(WebSocket.OP_PONG, f.opcode)
                assertEquals("are you there", String(f.payload))
            }
        } finally { s.stop() }
    }

    @Test
    fun `a close from the page is echoed and the socket ends`() {
        val s = server()
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c ->
                assertNotNull(c.nextData())
                c.send(WebSocket.OP_CLOSE, byteArrayOf(0x03, 0xE8.toByte(), 'b'.code.toByte(), 'y'.code.toByte()))
                var f: ServerFrame? = c.next()
                while (f != null && f.opcode != WebSocket.OP_CLOSE) f = c.next()
                val close = assertNotNull(f, "a close frame back")
                assertEquals(1000, ((close.payload[0].toInt() and 0xFF) shl 8) or (close.payload[1].toInt() and 0xFF))
                assertNull(c.next(), "then the connection ends")
            }
        } finally { s.stop() }
    }

    @Test
    fun `a frame that is not masked ends the session with a protocol error`() {
        val s = server()
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c ->
                assertNotNull(c.nextData())
                c.send(WebSocket.OP_TEXT, "hi".toByteArray(), masked = false)
                var f: ServerFrame? = c.next()
                while (f != null && f.opcode != WebSocket.OP_CLOSE) f = c.next()
                val close = assertNotNull(f)
                assertEquals(1002, ((close.payload[0].toInt() and 0xFF) shl 8) or (close.payload[1].toInt() and 0xFF))
                assertNull(c.next())
            }
        } finally { s.stop() }
    }

    @Test
    fun `text and binary from the page are ignored and the stream goes on`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c ->
                assertNotNull(c.nextData())
                c.send(WebSocket.OP_TEXT, "hello there".toByteArray())
                c.send(WebSocket.OP_BINARY, ByteArray(300))
                c.send(WebSocket.OP_TEXT, "Hel".toByteArray(), fin = false)
                c.send(WebSocket.OP_CONTINUATION, "lo".toByteArray())
                feed.push(16, 10, rgb(16, 10, 8))
                var f = assertNotNull(c.nextData())
                assertEquals(WebSocket.OP_BINARY, f.opcode)
            }
        } finally { s.stop() }
    }

    @Test
    fun `bad upgrade requests get plain answers, not a socket`() {
        val s = server()
        val port = s.start(0)
        try {
            val plain = raw(port, "/game.ws?k=abcd")
            assertEquals(400, plain.code)
            assertContains(plain.body, "/game")

            WsTestClient(port, "/game.ws?k=abcd", version = "8").use { c ->
                assertContains(c.status, " 426 ")
                assertEquals("13", c.headers["sec-websocket-version"])
            }
            WsTestClient(port, "/game.ws?k=abcd", key = "bm90IGEga2V5").use { c -> assertContains(c.status, " 400 ") }
            WsTestClient(port, "/game.ws?k=abcd", upgrade = false).use { c -> assertContains(c.status, " 400 ") }
            assertEquals(0, s.viewers)
        } finally { s.stop() }
    }

    @Test
    fun `without a game feed the socket answers 503 and the tracker routes still work`() {
        val s = server(feed = null)
        val port = s.start(0)
        try {
            WsTestClient(port, "/game.ws?k=abcd").use { c -> assertContains(c.status, " 503 ") }
            assertEquals(200, raw(port, "/game?k=abcd").code)
            assertEquals(200 to "42", get(port, "/attempts?k=abcd"))
        } finally { s.stop() }
    }

    @Test
    fun `a request line that is nonsense, or a query that will not decode, does not hang or crash the server`() {
        val s = server()
        val port = s.start(0)
        try {
            assertEquals(400, raw(port, "/game?k=%zz").code)
            Socket("127.0.0.1", port).use { sock ->
                sock.soTimeout = 5000
                sock.getOutputStream().write("BREW /pot HTTP/1.1\r\n\r\n".toByteArray())
                val r = sock.getInputStream().bufferedReader().readLine()
                assertContains(r, "405")
            }
            assertEquals(200 to "42", get(port, "/attempts?k=abcd"), "and it is still serving")
        } finally { s.stop() }
    }

    // ------------------------------------------------------------------ the capture switch, end to end

    @Test
    fun `the taps are off with nobody connected, on while a page is, and off when it leaves`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        try {
            assertFalse(feed.capturing)
            assertEquals(emptyList(), feed.captureLog, "starting the server touches nothing")
            get(port, "/game?k=abcd")
            raw(port, "/obs-scene.json?k=abcd")
            assertEquals(emptyList(), feed.captureLog, "the pages alone do not start the game feed")

            val c = WsTestClient(port, "/game.ws?k=abcd")
            assertTrue(waitFor { feed.capturing }, "a connected page turns the taps on")
            assertEquals(1, s.viewers)
            c.close()
            assertTrue(waitFor { !feed.capturing }, "and its leaving turns them off")
            assertEquals(0, s.viewers)
            assertEquals(listOf(true, false), feed.captureLog)
        } finally { s.stop() }
    }

    @Test
    fun `two pages share one feed and it stays on until the second leaves`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        try {
            val a = WsTestClient(port, "/game.ws?k=abcd")
            val b = WsTestClient(port, "/game.ws?k=abcd")
            assertTrue(waitFor { s.viewers == 2 })
            a.close()
            assertTrue(waitFor { s.viewers == 1 })
            assertTrue(feed.capturing, "one page is still watching")
            b.close()
            assertTrue(waitFor { !feed.capturing })
            assertEquals(listOf(true, false), feed.captureLog)
        } finally { s.stop() }
    }

    @Test
    fun `stopping the server ends the pages and turns the taps off`() {
        val feed = FakeGameFeed()
        val s = server(feed)
        val port = s.start(0)
        val c = WsTestClient(port, "/game.ws?k=abcd")
        try {
            assertTrue(waitFor { feed.capturing })
            s.stop()
            assertTrue(waitFor { !feed.capturing }, "capture is off after stop")
            assertTrue(waitFor { c.next() == null }, "the page's socket is closed, so it reconnects by itself")
        } finally { c.close(); s.stop() }
    }

    @Test
    fun `the seventh page at once is refused with a 503`() {
        val s = server()
        val port = s.start(0)
        val open = ArrayList<WsTestClient>()
        try {
            repeat(GameStream.MAX_VIEWERS) { open += WsTestClient(port, "/game.ws?k=abcd") }
            assertTrue(waitFor { s.viewers == GameStream.MAX_VIEWERS })
            WsTestClient(port, "/game.ws?k=abcd").use { c -> assertContains(c.status, " 503 ") }
            open.removeAt(0).close()
            assertTrue(waitFor { s.viewers == GameStream.MAX_VIEWERS - 1 })
            WsTestClient(port, "/game.ws?k=abcd").use { c -> assertContains(c.status, " 101 ") }
        } finally { open.forEach { it.close() }; s.stop() }
    }

    @Test
    fun `a page that stops answering pings is dropped and the taps go off`() {
        val feed = FakeGameFeed()
        val gs = GameStream(feed)
        val sock = java.net.ServerSocket(0)
        val client = Socket("127.0.0.1", sock.localPort)
        val serverSide = sock.accept()
        // A session with a quick ping and a short read timeout stands in for 5 and 20 seconds.
        val session = GameSocket(serverSide, java.io.BufferedInputStream(serverSide.getInputStream()), gs, pingEveryMs = 50, readTimeoutMs = 300)
        val runner = Thread { session.run(WsTestClient.KEY) }.also { it.start() }
        try {
            assertTrue(waitFor { feed.capturing }, "the session began")
            // The peer says nothing at all: no pong, no data.
            assertTrue(waitFor(4000) { !feed.capturing }, "a silent page is dropped after the read timeout")
            runner.join(2000)
            assertFalse(runner.isAlive)
        } finally { client.close(); serverSide.close(); sock.close(); gs.shutdown() }
    }

    @Test
    fun `the server pings a quiet page`() {
        val feed = FakeGameFeed()
        val gs = GameStream(feed)
        val sock = java.net.ServerSocket(0)
        val client = Socket("127.0.0.1", sock.localPort).apply { soTimeout = 5000 }
        val serverSide = sock.accept()
        val session = GameSocket(serverSide, java.io.BufferedInputStream(serverSide.getInputStream()), gs, pingEveryMs = 40, readTimeoutMs = 5000)
        val runner = Thread { session.run(WsTestClient.KEY) }.also { it.start() }
        try {
            val input = java.io.BufferedInputStream(client.getInputStream())
            // Skip the 101 head.
            var last = 0; var n = 0
            while (true) { val b = input.read(); n++; last = (last shl 8) or b; if (last == 0x0D0A0D0A) break; check(n < 400) }
            val opcodes = HashSet<Int>()
            repeat(6) { opcodes += ServerFrame.read(input)!!.opcode }
            assertTrue(WebSocket.OP_PING in opcodes, "pings on a timer: $opcodes")
        } finally { client.close(); serverSide.close(); sock.close(); runner.join(2000); gs.shutdown() }
    }

    // ------------------------------------------------------------------ a thread is spent only on this network, and not for long (rc33 audit P1 #58, #59)

    @Test
    fun `only a peer on this network is served`() {
        fun a(s: String) = java.net.InetAddress.getByName(s)
        for (ok in listOf("127.0.0.1", "192.168.1.5", "10.0.0.2", "172.16.4.1", "169.254.3.3", "100.101.102.103", "::1", "fe80::1", "fd12:3456::1"))
            assertTrue(StreamServer.local(a(ok)), ok)
        for (no in listOf("8.8.8.8", "100.128.0.1", "2001:4860::8888", "203.0.113.9"))
            assertFalse(StreamServer.local(a(no)), no)
        assertFalse(StreamServer.local(null))
    }

    /** A connection the server has given up on: its read ends (-1) or is reset, well before [ms]. */
    private fun closedWithin(s: Socket, ms: Int): Boolean {
        s.soTimeout = ms
        return try { s.getInputStream().read() == -1 } catch (e: java.net.SocketException) { true } catch (e: java.net.SocketTimeoutException) { false }
    }

    @Test
    fun `a request that trickles in is dropped at its deadline, however steady the trickle`() {
        val srv = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "0" }, { "[]" }, requestMs = 600)
        val port = srv.start(0)
        try {
            Socket("127.0.0.1", port).use { c ->
                val out = c.getOutputStream()
                out.write("GET /state.json?k=abcd HTTP/1.1\r\n".toByteArray()); out.flush()
                val began = System.currentTimeMillis()
                // A header byte every 100 ms for 8 s: each read is answered long before any per-read timeout. The bounds
                // sit well above the 600 ms deadline, for a busy machine, and well below the trickle (rc32 audit P3 #83).
                val t = Thread { runCatching { repeat(80) { out.write('x'.code); out.flush(); Thread.sleep(100) } } }.apply { isDaemon = true; start() }
                assertTrue(closedWithin(c, 6000), "the server let go")
                assertTrue(System.currentTimeMillis() - began < 5000, "at the deadline, not after the trickle")
                t.interrupt()
            }
        } finally { srv.stop() }
    }

    @Test
    fun `every header line counts toward the hundred, repeated names and all`() {
        val srv = server()
        val port = srv.start(0)
        try {
            Socket("127.0.0.1", port).use { c ->
                val req = StringBuilder("GET /state.json?k=abcd HTTP/1.1\r\n")
                repeat(150) { req.append("X: 1\r\n") }
                req.append("\r\n")
                c.getOutputStream().write(req.toString().toByteArray()); c.getOutputStream().flush()
                c.soTimeout = 3000
                val first = BufferedReader(InputStreamReader(c.getInputStream())).readLine()
                assertNull(first, "no answer: the request was dropped")
            }
        } finally { srv.stop() }
    }

    /** The connections the server counts as open now (its private count, read for the test). */
    private fun openNow(srv: StreamServer): Int =
        (StreamServer::class.java.getDeclaredField("open").apply { isAccessible = true }.get(srv) as java.util.concurrent.atomic.AtomicInteger).get()

    /**
     * rc32 audit P3 #82: a page's request still arriving as STREAM is turned off is in no list stop() walks. It was upgraded
     * after the stop, switched the taps back on and ran a pump on the stopped stream; it is dropped now.
     */
    @Test
    fun `a game socket whose request finishes after the stream stopped is dropped, and the taps stay off`() {
        val feed = FakeGameFeed()
        val srv = server(feed)
        val port = srv.start(0)
        Socket("127.0.0.1", port).use { c ->
            val out = c.getOutputStream()
            out.write(("GET /game.ws?k=abcd HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: ${WsTestClient.KEY}\r\nSec-WebSocket-Version: 13\r\n").toByteArray()); out.flush()
            Thread.sleep(300)
            srv.stop()
            out.write("\r\n".toByteArray()); out.flush()
            assertTrue(closedWithin(c, 3000), "dropped, not upgraded")
        }
        Thread.sleep(200)
        assertFalse(feed.capturing, "the taps stay off")
        assertTrue(feed.captureLog.none { it }, "and were never switched back on: ${feed.captureLog}")
    }

    @Test
    fun `past the cap a connection is closed at once, and the server still answers once one goes`() {
        val srv = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "0" }, { "[]" }, maxOpen = 2, requestMs = 5_000)
        val port = srv.start(0)
        try {
            val idle = List(2) { Socket("127.0.0.1", port) }
            // Waited for, not slept on (rc32 audit P3 #83): the server has counted both before the third comes.
            assertTrue(waitFor { openNow(srv) == 2 })
            Socket("127.0.0.1", port).use { extra -> assertTrue(closedWithin(extra, 2000), "the third is refused") }
            idle.forEach { it.close() }
            assertTrue(waitFor { openNow(srv) < 2 })
            assertEquals(200, get(port, "/state.json?k=abcd").first, "room again")
        } finally { srv.stop() }
    }
}
