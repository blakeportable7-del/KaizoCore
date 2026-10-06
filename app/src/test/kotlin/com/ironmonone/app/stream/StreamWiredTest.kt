package com.ironmonone.app.stream

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wi-Fi and the USB cable, side by side (2026-10-06, Blake: "do both wifi and wired").
 *
 * On the cable the PC runs `adb forward tcp:8642 tcp:8642` and opens 127.0.0.1:8642; adbd opens that connection on the
 * phone's own loopback. So the server sees a loopback peer and a loopback Host whether the guide was opened in the
 * phone's own browser or on the PC, and the guide used to tell every loopback visitor "Open this page on your PC, not on
 * the phone" and keep the scene from them. These tests drive the real server over a socket, the way adbd does (a
 * loopback connection, the Host the PC's browser wrote), with the User-Agent and client hints real browsers send.
 */
class StreamWiredTest {

    private val windowsChrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    private val obsBrowser = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 OBS/31.1.0"
    private val macFirefox = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:131.0) Gecko/20100101 Firefox/131.0"
    private val phoneChrome = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    private val phoneFirefox = "Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0"

    private class Answer(val code: Int, val headers: Map<String, String>, val body: String)

    /** A GET over a loopback socket, as adbd hands the PC's request on, with the headers the PC's browser wrote. */
    private fun raw(port: Int, path: String, headers: Map<String, String>): Answer {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5000
            val req = StringBuilder("GET $path HTTP/1.1\r\n")
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

    private fun withServer(body: (Int) -> Unit) {
        val s = StreamServer("abcd", { "<h1>page</h1>" }, { "{}" }, { 1L }, { "42" }, { "[]" }, FakeGameFeed(), { "192.168.1.20" })
        val port = s.start(0)
        try { body(port) } finally { s.stop() }
    }

    /** Every http:// address in [text], up to the first character an address cannot hold. */
    private fun addresses(text: String): List<String> =
        Regex("http://[^\"'<>\\s&]+").findAll(text.replace("\\/", "/")).map { it.value }.toList()

    // ------------------------------------------------------------------ the PC on the cable

    @Test
    fun `a PC on the USB cable gets the scene downloads, and every address in the guide is 127_0_0_1`() = withServer { port ->
        for (ua in listOf(windowsChrome, obsBrowser, macFirefox)) {
            val page = raw(port, "/?k=abcd", mapOf("Host" to "127.0.0.1:$port", "User-Agent" to ua))
            assertEquals(200, page.code)
            assertFalse(page.body.contains("Open this page on your PC"), "a PC on the cable is not the phone: $ua")
            assertContains(page.body, "download=\"${ObsScene.FILE}\"", message = ua)
            assertContains(page.body, "download=\"${ObsScene.FILE_TOP}\"", message = ua)
            assertContains(page.body, "You are on the USB cable.", message = ua)
            assertContains(page.body, StreamHub.ADB_FORWARD, message = ua)
            val urls = addresses(page.body).filter { "?k=" in it || "/game" in it }
            assertTrue(urls.size >= 10, "the table's addresses are there: $urls")
            for (u in urls) assertTrue(u.startsWith("http://127.0.0.1:$port/"), "an address the PC can open over the cable: $u")
        }
    }

    @Test
    fun `curl on the PC, with no browser words at all, is a PC too`() = withServer { port ->
        val page = raw(port, "/?k=abcd", mapOf("Host" to "127.0.0.1:$port"))
        assertContains(page.body, "download=\"${ObsScene.FILE}\"")
        assertFalse(page.body.contains("Open this page on your PC"))
    }

    @Test
    fun `the scene downloaded over the cable points OBS at 127_0_0_1, both versions`() = withServer { port ->
        for (path in listOf("/obs-scene.json?k=abcd", "/obs-scene.json?k=abcd&top=1")) {
            val scene = raw(port, path, mapOf("Host" to "127.0.0.1:$port", "User-Agent" to windowsChrome))
            assertEquals(200, scene.code, path)
            assertContains(scene.headers["content-disposition"].orEmpty(), "attachment", message = path)
            val urls = addresses(scene.body)
            assertTrue(urls.isNotEmpty(), path)
            for (u in urls) assertTrue(u.startsWith("http://127.0.0.1:$port/"), "$path: $u")
            assertFalse(scene.body.contains("192.168.1.20"), "the phone's Wi-Fi address is not in a cable scene")
        }
    }

    @Test
    fun `a PC that forwarded another port of its own gets that port in the guide and the scene`() = withServer { port ->
        // adb forward tcp:9000 tcp:8642: the PC opens 127.0.0.1:9000, and that is the address OBS on it has to use.
        val page = raw(port, "/?k=abcd", mapOf("Host" to "127.0.0.1:9000", "User-Agent" to windowsChrome))
        for (u in addresses(page.body).filter { "?k=" in it }) assertTrue(u.startsWith("http://127.0.0.1:9000/"), u)
        val scene = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to "localhost:9000", "User-Agent" to windowsChrome))
        for (u in addresses(scene.body)) assertTrue(u.startsWith("http://localhost:9000/"), u)
    }

    @Test
    fun `the game page and its socket answer a loopback peer, which is how the cable arrives`() = withServer { port ->
        assertEquals(200, raw(port, "/game?k=abcd", mapOf("Host" to "127.0.0.1:$port")).code)
        assertEquals(200, raw(port, "/tracker?k=abcd", mapOf("Host" to "127.0.0.1:$port")).code)
        assertEquals(403, raw(port, "/game?k=wrong", mapOf("Host" to "127.0.0.1:$port")).code, "the password still counts on the cable")
        // The socket stays open after the upgrade, so only its status line is read.
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write(("GET /game.ws?k=abcd HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
            val status = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1)).readLine()
            assertEquals("101", status.split(' ')[1], "the game's socket opens over loopback: $status")
        }
        assertTrue(StreamServer.local(java.net.InetAddress.getByName("127.0.0.1")), "adbd's loopback connection is admitted")
    }

    // ------------------------------------------------------------------ the phone's own browser

    @Test
    fun `the phone's own browser still gets the warning and no scene`() = withServer { port ->
        val phones = listOf(
            mapOf("User-Agent" to phoneChrome, "Sec-CH-UA-Mobile" to "?1", "Sec-CH-UA-Platform" to "\"Android\""),
            mapOf("User-Agent" to phoneChrome),
            mapOf("User-Agent" to phoneFirefox),
            // Chrome set to Desktop site with its client hints still saying Android.
            mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
                "Sec-CH-UA-Platform" to "\"Android\""),
            mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64)", "Sec-CH-UA-Mobile" to "?1"),
        )
        for (host in listOf("127.0.0.1:$port", "localhost:$port", "[::1]:$port")) for (h in phones) {
            val page = raw(port, "/?k=abcd", h + ("Host" to host)).body
            assertContains(page, "Open this page on your PC, not on the phone.", message = "$host $h")
            assertFalse(page.contains("download="), "$host $h")
            assertFalse(page.contains("You are on the USB cable."), "$host $h")
        }
    }

    // ------------------------------------------------------------------ Wi-Fi, unchanged and equal

    @Test
    fun `a PC on Wi-Fi gets the downloads with the Wi-Fi address, and no cable note`() = withServer { port ->
        val page = raw(port, "/?k=abcd", mapOf("Host" to "192.168.1.20:$port", "User-Agent" to windowsChrome)).body
        assertContains(page, "download=\"${ObsScene.FILE}\"")
        assertFalse(page.contains("You are on the USB cable."))
        assertFalse(page.contains("Open this page on your PC"))
        for (u in addresses(page).filter { "?k=" in it }) assertTrue(u.startsWith("http://192.168.1.20:$port/"), u)
        val scene = raw(port, "/obs-scene.json?k=abcd", mapOf("Host" to "192.168.1.20:$port", "User-Agent" to windowsChrome)).body
        for (u in addresses(scene)) assertTrue(u.startsWith("http://192.168.1.20:$port/"), u)
        // Another phone or tablet on the Wi-Fi is not this phone: the scene it would make names the right address.
        val tablet = raw(port, "/?k=abcd", mapOf("Host" to "192.168.1.20:$port", "User-Agent" to phoneChrome)).body
        assertFalse(tablet.contains("Open this page on your PC"))
    }

    @Test
    fun `the guide names both ways`() {
        val text = StreamPages.setup("http://192.168.1.20:8642", "abcd").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ")
        assertContains(text, "over a USB cable or your Wi-Fi")
        assertContains(text, "run ${StreamHub.ADB_FORWARD} on the PC again")
        assertContains(text, "every address starts http://127.0.0.1:${StreamHub.PORT}")
    }

    // ------------------------------------------------------------------ the app's lines and Copy

    @Test
    fun `both links are offered when there is Wi-Fi, and the cable's alone when there is none`() {
        val both = StreamHub.linkLines("192.168.1.20", "0a1b2c3d")
        assertEquals(listOf(StreamHub.Way.WIFI, StreamHub.Way.USB), both.map { it.first })
        assertEquals("Wi-Fi: http://192.168.1.20:8642/?k=0a1b2c3d", both[0].second)
        assertEquals("USB cable: run adb forward tcp:8642 tcp:8642 on your PC, then open http://127.0.0.1:8642/?k=0a1b2c3d", both[1].second)
        val none = StreamHub.linkLines(null, "0a1b2c3d")
        assertEquals(both[1], none[1], "the cable's line does not depend on Wi-Fi")
        assertContains(none[0].second, StreamHub.NO_WIFI)
        assertFalse(none.any { "<phone-ip>" in it.second })
        assertTrue(StreamHub.canCopy(StreamHub.Way.USB, null))
        assertTrue(StreamHub.canCopy(StreamHub.Way.WIFI, "192.168.1.20"))
        assertFalse(StreamHub.canCopy(StreamHub.Way.WIFI, null))
    }

    @Test
    fun `Copy hands over the link the player picked`() {
        var copied: String? = null
        assertEquals("USB cable link copied. On your PC, run adb forward tcp:8642 tcp:8642 first.",
            StreamHub.copyLink(StreamHub.Way.USB, null, { copied = it }, "0a1b2c3d"))
        assertEquals("http://127.0.0.1:8642/?k=0a1b2c3d", copied)
        assertEquals("Wi-Fi link copied.", StreamHub.copyLink(StreamHub.Way.WIFI, "10.0.0.7", { copied = it }, "0a1b2c3d"))
        assertEquals("http://10.0.0.7:8642/?k=0a1b2c3d", copied)
        copied = null
        assertEquals(StreamHub.NO_WIFI, StreamHub.copyLink(StreamHub.Way.WIFI, null, { copied = it }, "0a1b2c3d"))
        assertNull(copied)
    }

    @Test
    fun `the status line after STREAM names both, or the cable alone with no Wi-Fi`() {
        val url = "http://192.168.1.20:8642/?k=${StreamHub.token}"
        val wifi = StreamHub.startedLine(url, "192.168.1.20")
        assertContains(wifi, url)
        assertContains(wifi, "http://127.0.0.1:8642/")
        assertContains(wifi, StreamHub.ADB_FORWARD)
        val cable = StreamHub.startedLine(url, null)
        assertContains(cable, "http://127.0.0.1:8642/")
        assertContains(cable, StreamHub.ADB_FORWARD)
        assertContains(cable, StreamHub.NO_WIFI)
    }

    @Test
    fun `the Stream page's links carry the saved token while the stream is off`() {
        val dir = kotlin.io.path.createTempDirectory("wired").toFile()
        try {
            val k = StreamHub.linkToken(dir)
            assertTrue(k.matches(Regex("[0-9a-f]{8}")), k)
            assertEquals(k, File(dir, "prep/stream-token.txt").readText().trim(), "saved, so STREAM reuses it")
            assertEquals(k, StreamHub.linkToken(dir), "the same one next time")
        } finally { dir.deleteRecursively() }
    }

    // ------------------------------------------------------------------ OBS over the cable

    @Test
    fun `OBS at 127_0_0_1 that does not answer says to run adb reverse, with the port`() {
        val closed = ServerSocket(0).use { it.localPort }
        try {
            ObsSocket.open("127.0.0.1", closed, "", connectMs = 1000, ioMs = 1000).close()
            fail("nothing listens on $closed")
        } catch (e: ObsError) {
            assertEquals(ObsError.Kind.UNREACHABLE, e.kind)
            assertEquals("OBS did not answer at 127.0.0.1:$closed. On a USB cable, run adb reverse tcp:$closed tcp:$closed on the PC first, " +
                "and again after you plug the phone back in.", e.message)
        }
        assertEquals("OBS did not answer at 192.168.1.20:4455.", ObsSocket.unreachable("192.168.1.20", 4455))
        assertContains(ObsSocket.unreachable("localhost", 4455), "adb reverse tcp:4455 tcp:4455")
        assertEquals("adb reverse tcp:4455 tcp:4455", StreamHub.ADB_REVERSE_OBS)
    }

    @Test
    fun `OBS through adb reverse is an ordinary loopback connection the link already makes`() {
        // adb reverse makes 127.0.0.1:4455 on the phone reach OBS on the PC; to the link that is a server at 127.0.0.1.
        val obs = FakeObs()
        try {
            val p = ObsLink.probe(ObsSettings(enabled = true, host = "127.0.0.1", port = obs.port, overworldScene = "Game"))
            assertTrue(p.ok, p.line)
        } finally { obs.close() }
    }

    // ------------------------------------------------------------------ where it is wired in, and the words

    @Test
    fun `Play's FILE menu and the Stream page show the two links`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("StreamLinkRows({ status = it })" in play)
        // Handed to the FILE bar, whose TOOLS sheet draws them under the stream's head line while it is on.
        assertTrue("streamExtra = { StreamLinkRows({ status = it }) }" in play && "a.streamExtra?.invoke()" in File("src/main/kotlin/com/ironmonone/app/FileBar.kt").readText())
        val page = File("src/main/kotlin/com/ironmonone/app/StreamSettingsScreen.kt").readText()
        assertTrue("linkToken?.let { StreamLinksCard(it) }" in page)
        val rows = File("src/main/kotlin/com/ironmonone/app/StreamLinks.kt").readText()
        assertTrue("StreamHub.linkLines(address, k)" in rows && "StreamHub.copyLink(way, address" in rows)
    }

    @Test
    fun `every new line is plain, with no dashes and no AI`() {
        val lines = com.ironmonone.app.StreamLinkCopy.ALL + com.ironmonone.app.ObsCopy.HOW +
            StreamHub.linkLines("192.168.1.20", "0a1b2c3d").map { it.second } + StreamHub.linkLines(null, "0a1b2c3d").map { it.second } +
            StreamHub.startedLine("http://x", null) + StreamHub.startedLine("http://x", "192.168.1.20") +
            StreamHub.copyLink(StreamHub.Way.USB, null, {}, "0a1b2c3d") + ObsSocket.unreachable("127.0.0.1", 4455) +
            StreamPages.setup("http://127.0.0.1:8642", "abcd")
        for (s in lines) {
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()), s.take(80))
            assertFalse(Regex("\\bAI\\b|artificial intelligence", RegexOption.IGNORE_CASE).containsMatchIn(s), s.take(80))
        }
    }
}
