package com.ironmonone.app.stream

import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The routes of the game over card, the run timer and the looks (streamer list items 1, 3 and 6, 2026-10-05): every
 * one behind the key like the rest, each with its content type, the looks put into the pages that take them, and the
 * two new kinds of event on /events.
 */
class StreamSourcesTest {

    private val css = File("src/main/assets/stream/themes.css").readText()

    private class Answer(val code: Int, val type: String?, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    private fun get(port: Int, path: String): Answer {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).readBytes()
        return Answer(code, c.contentType, body)
    }

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    private fun server(over: () -> String = { """{"key":"a"}""" }, timer: () -> String = { """{"run":true,"ms":1000}""" }): StreamServer =
        StreamServer("abcd", { "<!doctype html>\n<html><head><style>#box{}</style></head><body><div id=\"box\" data-panel></div></body></html>" },
            { "{}" }, { 1L }, { "1" }, { null },
            gameOver = over, timer = timer, themes = { css }, mon = { n -> if (n == 25) png else null })

    @Test
    fun `every new route needs the key and answers with its own content type`() {
        val s = server()
        val port = s.start(0)
        try {
            for (path in listOf("/gameover", "/gameover.html", "/gameover.json", "/timer", "/timer.html", "/timer.json", "/mon/25.png")) {
                assertEquals(403, get(port, path).code, "$path without the key")
                assertEquals(403, get(port, "$path?k=nope").code, "$path with a wrong key")
            }
            val html = "text/html; charset=utf-8"
            assertEquals(html, get(port, "/gameover?k=abcd").type)
            assertEquals(StreamOverlays.gameOver(), get(port, "/gameover?k=abcd").text, "with no look asked, the page as it is")
            assertEquals(StreamOverlays.timer(), get(port, "/timer.html?k=abcd").text)
            assertEquals(html, get(port, "/timer?k=abcd").type)
            get(port, "/gameover.json?k=abcd").let { assertEquals(200, it.code); assertEquals("application/json", it.type); assertEquals("""{"key":"a"}""", it.text) }
            get(port, "/timer.json?k=abcd").let { assertEquals("application/json", it.type); assertEquals("""{"run":true,"ms":1000}""", it.text) }
            get(port, "/mon/25.png?k=abcd").let { assertEquals(200, it.code); assertEquals("image/png", it.type); assertTrue(png.contentEquals(it.body)) }
            // A species with no picture is the empty one, never an error box in OBS.
            get(port, "/mon/26.png?k=abcd").let { assertEquals("image/png", it.type); assertTrue(StreamFavorites.EMPTY.contentEquals(it.body)) }
            assertEquals(404, get(port, "/mon/abc.png?k=abcd").code)
            assertEquals(404, get(port, "/mon/0.png?k=abcd").code)
        } finally { s.stop() }
    }

    @Test
    fun `the looks go into the tracker, the attempt counter, the game over card and the timer, and nothing else`() {
        val s = server()
        val port = s.start(0)
        try {
            for (path in listOf("/tracker", "/attempts.html", "/gameover", "/timer")) {
                for (look in StreamThemes.NAMES) {
                    val page = get(port, "$path?k=abcd&theme=$look").text
                    assertContains(page, "<html data-theme=\"$look\"", message = "$path $look")
                    assertContains(page, "<style id=\"kc-themes\">", message = "$path $look")
                    assertTrue(page.indexOf("kc-themes") < page.indexOf("</head>"), "$path: the looks' CSS is in the head")
                    assertContains(page, "data-panel", message = "$path marks its box for the looks")
                }
                // No look, or one there is not: the page as it was.
                assertFalse(get(port, "$path?k=abcd").text.contains("kc-themes"), path)
                assertFalse(get(port, "$path?k=abcd&theme=%22%3E%3Cscript%3E").text.contains("data-theme"), "$path: only a look on the list")
            }
            assertEquals("<html data-theme=\"hud\" lang=\"en\">", Regex("<html[^>]*>").find(get(port, "/timer?k=abcd&theme=HUD").text)?.value)
            // The game page and the guide take none.
            assertFalse(get(port, "/game?k=abcd&theme=hud").text.contains("data-theme"))
            assertFalse(get(port, "/?k=abcd&theme=hud").text.contains("data-theme"))
        } finally { s.stop() }
    }

    @Test
    fun `events carry the game over card and the timer, each when it changes`() {
        var over = "null"
        var timer = """{"run":true,"ms":1000}"""
        val s = server({ over }, { timer })
        val port = s.start(0)
        try {
            Socket("127.0.0.1", port).use { sock ->
                sock.soTimeout = 5000
                sock.getOutputStream().write("GET /events?k=abcd HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                val input = sock.getInputStream().bufferedReader()
                val seen = StringBuilder()
                /** Reads until [text] has come in after position [from] of what was read; returns where it starts. */
                fun until(text: String, from: Int = 0): Int {
                    val end = System.currentTimeMillis() + 5000
                    while (seen.indexOf(text, from) < 0 && System.currentTimeMillis() < end) seen.append(input.readLine() ?: break).append('\n')
                    val at = seen.indexOf(text, from)
                    assertTrue(at >= 0, "expected after $from: $text\nin: $seen")
                    return at
                }
                // On connect, all three.
                until("event: state\ndata: {}")
                until("event: gameover\ndata: null")
                until("event: timer\ndata: {\"run\":true,\"ms\":1000}")
                over = """{"key":"x","cause":"Lv.21 Sandile"}"""
                val shown = until("event: gameover\ndata: {\"key\":\"x\",\"cause\":\"Lv.21 Sandile\"}")
                timer = """{"run":true,"ms":2000}"""
                until("event: timer\ndata: {\"run\":true,\"ms\":2000}")
                // The card's clearing (a new run, Retry) goes out too.
                over = "null"
                until("event: gameover\ndata: null", shown)
                assertEquals(1, Regex("event: timer\ndata: \\{\"run\":true,\"ms\":1000\\}").findAll(seen).count(), "an unchanged timer is sent once")
            }
        } finally { s.stop() }
    }

    @Test
    fun `the looks' CSS fetches nothing and names both looks`() {
        // The phone serves everything: no web font, no import, no address of any kind.
        for (bad in listOf("http", "//", "@import", "url(", "@font-face")) assertFalse(css.replace(Regex("(?s)/\\*.*?\\*/"), "").contains(bad), bad)
        for (look in StreamThemes.NAMES) assertContains(css, ":root[data-theme=\"$look\"]")
        assertEquals(listOf("clean", "hud"), StreamThemes.NAMES)
        assertEquals(null, StreamThemes.name("default")); assertEquals("hud", StreamThemes.name(" Hud "))
        assertEquals("<html>x", StreamThemes.apply("<html>x", null, css), "no look: untouched")
        assertEquals("<html>x", StreamThemes.apply("<html>x", "nope", css))
    }

    @Test
    fun `the hub hands the server the card, the timer, the looks and the pictures`() {
        val hub = File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText()
        // Then the run history (StreamHistory), merged in after them (feat/stream, 2026-10-05).
        assertContains(hub, "gameOver = { gameOver }, timer = { timerJson() }, themes = { themes() }, mon = { n -> monPicture?.invoke(n) },")
        assertContains(hub, "history = StreamHistory.source(filesDir))")
        assertContains(hub, "assets?.open(\"stream/themes.css\")")
        assertContains(File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText(), "StreamHub.monPicture = StreamFavoritePictures.monSource(applicationContext)")
    }
}
