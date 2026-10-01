package com.ironmonone.app.stream

import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The game page is a source in the middle of somebody's live stream, so once a picture has been on it, it must never
 * write words over the game (2026-09-30, UX audit). Before the first picture the help text is what a streamer setting
 * up needs, and it stays as it was. The page's own script runs under node (PageRunner) and the tests read what it
 * left in the message box, the mark and the click-for-sound note.
 */
class GamePageWordsTest {

    private val game = StreamPages.game()

    private fun needNode() = Assume.assumeTrue("node is not on the PATH, so the pages' own scripts are not run", PageRunner.available)

    private val open = mapOf("open" to true)
    private val picture = mapOf("picture" to true)
    private val drop = mapOf("close" to true)
    private fun wait(ms: Int) = mapOf("advance" to ms)

    private fun words(s: PageRunner.Seen) = s.str("msg.display") == "block" && s.str("msg.text").isNotEmpty()

    @Test
    fun `before the first picture the help text is what it always was`() {
        needNode()
        // Connects, hears nothing, drops, and keeps trying: four failures in a row is the "cannot reach" text.
        val steps = listOf(open, drop, wait(5000), drop, wait(5000), drop, wait(5000), drop)
        val seen = PageRunner.run("game", game, "?k=abcd", steps).steps

        assertEquals("Connected. Waiting for the game on the phone.", seen[0].str("msg.text"))
        assertEquals("block", seen[0].str("msg.display"))
        assertEquals("Lost the phone. Trying again.", seen[1].str("msg.text"))
        assertEquals("Lost the phone. Trying again.", seen[3].str("msg.text"))
        assertEquals("Lost the phone. Trying again.", seen[5].str("msg.text"))
        assertEquals("Cannot reach the phone. Is the stream on, and is this the address the app shows?", seen[7].str("msg.text"))
        assertTrue(seen.none { it.str("mark.display") == "block" }, "the mark belongs to a page that has shown a picture")
    }

    @Test
    fun `after a picture has been on the source a lost connection writes no words and shows only the mark`() {
        needNode()
        val steps = listOf(
            open, picture,                                                            // 0, 1: the game is on the source
            drop,                                                                     // 2: Wi-Fi drops
            wait(5000), open,                                                         // 3, 4: it comes back, but there is no picture yet
            drop, wait(5000), drop, wait(5000), drop, wait(5000), drop, wait(5000),   // 5..12: it goes on failing, more than four times
            open, picture,                                                            // 13, 14: and the game is back
        )
        val seen = PageRunner.run("game", game, "?k=abcd", steps).steps

        for ((i, s) in seen.withIndex().drop(1)) assertTrue(!words(s), "step $i writes '${s.str("msg.text")}' over a picture that has been on air")
        assertNotEquals("block", seen[1].str("mark.display"), "no mark while the picture is coming in")
        assertEquals("block", seen[2].str("mark.display"), "the drop shows as a mark")
        assertEquals("block", seen[4].str("mark.display"), "connected again is not a picture: the mark stays")
        assertEquals("block", seen[12].str("mark.display"), "and stays through any number of failures")
        assertEquals("none", seen[14].str("mark.display"), "the next picture takes it away")
        assertTrue(seen[12].long("sockets") >= 5, "meanwhile it keeps trying to reconnect")
    }

    @Test
    fun `the click for sound note shows in a browser tab and never inside OBS`() {
        needNode()
        // A browser holds the sound back until a click, so the test tab asks for one. OBS does not, so there the note
        // could only ever be words on the stream.
        val steps = listOf(open, picture, mapOf("sound" to true))
        val browser = PageRunner.run("game", game, "?k=abcd", steps, mapOf("audioState" to "suspended")).steps
        assertEquals("block", browser.last().str("tap.display"))

        val obs = PageRunner.run("game", game, "?k=abcd", steps, mapOf("audioState" to "suspended", "obs" to true)).steps
        assertEquals("none", obs.last().str("tap.display"))
    }

    @Test
    fun `the help text and the mark come in after a pause, so a quick reconnect flashes nothing`() {
        // OBS loads the page again each time its scene comes up (restart_when_active), and a page that connects quickly
        // must not flash words over the stream. CSS does the waiting, so the text is set at once and the test above
        // can read it; the animation starts afresh each time the box is shown.
        assertTrue(Regex("#msg\\{[^}]*animation:wait-in [^}]*1\\.2s").containsMatchIn(game), "the help text waits")
        assertTrue(Regex("#mark\\{[^}]*display:none[^}]*animation:wait-in [^}]*2s").containsMatchIn(game), "and so does the mark, longer")
        assertContains(game, "@keyframes wait-in")
        assertContains(game, "<div id=\"mark\"></div>")
    }
}
