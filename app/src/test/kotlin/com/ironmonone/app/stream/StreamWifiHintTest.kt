package com.ironmonone.app.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rest of rc32 audit P3 #79 (rc35 follow-up N #24): with no address a PC can open, Play's status line and its FILE
 * menu printed a link with the <phone-ip> placeholder in it, and nothing said that Wi-Fi was the trouble. They say what
 * to do now, and Copy link copies nothing it cannot fill in.
 */
class StreamWifiHintTest {
    private val url = "http://192.168.1.20:${StreamHub.PORT}/?k=0a1b2c3d"

    @Test
    fun `with no Wi-Fi address the lines say what to do, never a placeholder link`() {
        for (line in listOf(StreamHub.startedLine(url, null), StreamHub.menuLine(null), StreamHub.copyLink(null) { error("nothing to copy: $it") })) {
            assertTrue(StreamHub.NO_WIFI in line, line)
            assertFalse("<phone-ip>" in line, line)
        }
        assertEquals("Connect the phone to the same Wi-Fi as your PC, or turn on its hotspot.", StreamHub.NO_WIFI)
    }

    @Test
    fun `with one they give the link as before, and a busy port says so`() {
        assertEquals("On your PC, open $url", StreamHub.startedLine(url, "192.168.1.20"))
        assertEquals("Port ${StreamHub.PORT} is busy.", StreamHub.startedLine(null, "192.168.1.20"))
        assertTrue(StreamHub.menuLine("192.168.1.20").startsWith("Stream: http://192.168.1.20:${StreamHub.PORT}/?k="))
        var copied: String? = null
        assertEquals("Stream link copied.", StreamHub.copyLink("192.168.1.20") { copied = it })
        assertTrue(copied!!.startsWith("http://192.168.1.20:${StreamHub.PORT}/?k="))
        for (s in listOf(StreamHub.NO_WIFI, StreamHub.startedLine(url, null), StreamHub.menuLine(null)))
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()) || " - " in s, s)
    }

    @Test
    fun `Play's status line, menu line and Copy link go through them`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("status = com.ironmonone.app.stream.StreamHub.startedLine(url)" in play)
        assertTrue("Text(com.ironmonone.app.stream.StreamHub.menuLine()," in play)
        assertTrue("status = com.ironmonone.app.stream.StreamHub.copyLink {" in play)
        assertFalse("On your PC, open" in play || "StreamHub.url()" in play, "no line of Play's builds the link itself")
    }
}
