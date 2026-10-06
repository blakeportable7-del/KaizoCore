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
        val wifiLine = StreamHub.linkLines(null, "0a1b2c3d").single { it.first == StreamHub.Way.WIFI }.second
        for (line in listOf(StreamHub.startedLine(url, null), wifiLine, StreamHub.copyLink(StreamHub.Way.WIFI, null, { error("nothing to copy: $it") }, "0a1b2c3d"))) {
            assertTrue(StreamHub.NO_WIFI in line, line)
            assertFalse("<phone-ip>" in line, line)
        }
        assertFalse(StreamHub.canCopy(StreamHub.Way.WIFI, null), "no Wi-Fi Copy with nothing to copy")
        assertEquals("Connect the phone to the same Wi-Fi as your PC, or turn on its hotspot.", StreamHub.NO_WIFI)
    }

    @Test
    fun `with one they give the link as before, and a busy port says so`() {
        assertTrue(StreamHub.startedLine(url, "192.168.1.20").startsWith("On your PC, open $url over Wi-Fi, or http://127.0.0.1:${StreamHub.PORT}/?k="))
        assertEquals("Port ${StreamHub.PORT} is busy.", StreamHub.startedLine(null, "192.168.1.20"))
        assertEquals("Wi-Fi: http://192.168.1.20:${StreamHub.PORT}/?k=0a1b2c3d",
            StreamHub.linkLines("192.168.1.20", "0a1b2c3d").single { it.first == StreamHub.Way.WIFI }.second)
        var copied: String? = null
        assertEquals("Wi-Fi link copied.", StreamHub.copyLink(StreamHub.Way.WIFI, "192.168.1.20", { copied = it }, "0a1b2c3d"))
        assertEquals("http://192.168.1.20:${StreamHub.PORT}/?k=0a1b2c3d", copied)
        val noWifi = StreamHub.linkLines(null, "0a1b2c3d").single { it.first == StreamHub.Way.WIFI }.second
        for (s in listOf(StreamHub.NO_WIFI, StreamHub.startedLine(url, null), noWifi))
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()) || " - " in s, s)
    }

    @Test
    fun `Play's status line, menu line and Copy link go through them`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("status = com.ironmonone.app.stream.StreamHub.startedLine(url)" in play)
        // The FILE bar's TOOLS sheet: its head line, then StreamLinks.kt's rows, handed over as streamExtra.
        assertTrue("Text(FileBarCopy.STREAM_HEAD," in File("src/main/kotlin/com/ironmonone/app/FileBar.kt").readText())
        assertTrue("streamExtra = { StreamLinkRows({ status = it }) }" in play, "the FILE bar's links are StreamLinks.kt's rows")
        assertFalse("On your PC, open" in play || "StreamHub.url()" in play, "no line of Play's builds the link itself")
    }
}
