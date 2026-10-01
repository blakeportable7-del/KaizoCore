package com.ironmonone.app.stream

import com.ironmonone.tracker.GameOver
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The attempt counter belongs to a run. In Play any game and ROM Hacks the attempt
 * number in the store is the LAST run's, which is a wrong number to put on a stream,
 * so it must say nothing there (Blake, 2026-09-29: the stream kit works in every mode).
 *
 * 2026-09-30 (UX audit P0-17): the tracker page said "attempt N" in its footer and its run over banner in every
 * game. The tests further down run the pages' own scripts under node (PageRunner) and read what they printed.
 */
class StreamAttemptsTest {

    private fun snapshot(run: StreamSnapshot.Run) = Json.write(StreamSnapshot.build(run, null, null, StreamSnapshot.Notes()))

    @Test
    fun `a run is a run by the flag the Play screen passes`() {
        assertTrue(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Kaizo", "GBA", 5, true, seed = "abc", isRun = true))))
        assertFalse(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Library game", "GBA", 812, true, seed = null, isRun = false))))
    }

    @Test
    fun `the flag wins over the seed, and with no flag the seed decides`() {
        // A run whose seed file is missing is still a run, and a game with a stale seed is still not.
        assertTrue(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Nuzlocke", "GBA", 1, true, seed = null, isRun = true))))
        assertFalse(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Hack", "GBA", 9, false, seed = "stale", isRun = false))))
        assertTrue(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Old call", "GBA", 2, true, seed = "abc"))))
        assertFalse(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("Old call", "GBA", 2, false))))
    }

    @Test
    fun `the snapshot says run true or false as a plain field, and only a run counts`() {
        val run = snapshot(StreamSnapshot.Run("Kaizo", "GBA", 5, true, "abc", true))
        val other = snapshot(StreamSnapshot.Run("Hack", "GBA", 5, false, null, false))
        assertContains(run, "\"run\":true")
        assertContains(other, "\"run\":false")
        assertFalse(other.contains("\"run\":true"))
        assertFalse(StreamSnapshot.isRun("{}"))
        assertFalse(StreamSnapshot.isRun(""))
        // A title that spells the flag out is a string, and strings are escaped.
        assertFalse(StreamSnapshot.isRun(snapshot(StreamSnapshot.Run("\"run\":true", "GBA", 1, false, null, false))))
    }

    @Test
    fun `the hub publishes a run's attempt number and nothing for any other game`() {
        val kaizo = snapshot(StreamSnapshot.Run("Kaizo", "GBA", 7, true, "abc", true))
        val library = snapshot(StreamSnapshot.Run("Library game", "GBA", 7, true, null, false))
        val hack = snapshot(StreamSnapshot.Run("Some hack", "NDS", 7, false, null, false))

        StreamHub.publish(kaizo, 7)
        assertEquals("7", StreamHub.attempts)
        StreamHub.publish(kaizo, 8)
        assertEquals("8", StreamHub.attempts)

        // The store still holds the run's count while another game is played; it must not show.
        StreamHub.publish(library, 8)
        assertEquals("", StreamHub.attempts, "Play any game")
        StreamHub.publish(hack, 8)
        assertEquals("", StreamHub.attempts, "ROM Hacks")

        // And back to a run: the number returns.
        StreamHub.publish(kaizo, 9)
        assertEquals("9", StreamHub.attempts)
    }

    @Test
    fun `the plain-text attempts route says nothing outside a run`() {
        StreamHub.publish(snapshot(StreamSnapshot.Run("Hack", "GBA", 44, false, null, false)), 44)
        val s = StreamServer("k", { "" }, { StreamHub.state }, { StreamHub.version }, { StreamHub.attempts }, { "[]" })
        val port = s.start(0)
        try {
            val c = java.net.URL("http://127.0.0.1:$port/attempts?k=k").openConnection() as java.net.HttpURLConnection
            assertEquals(200, c.responseCode)
            assertEquals("", c.inputStream.bufferedReader().readText())
        } finally { s.stop() }
    }

    @Test
    fun `the state version moves only when the snapshot changes`() {
        val a = snapshot(StreamSnapshot.Run("A", "GBA", 1, true, "x", true))
        StreamHub.publish(a, 1)
        val v = StreamHub.version
        StreamHub.publish(a, 1)
        assertEquals(v, StreamHub.version)
        StreamHub.publish(snapshot(StreamSnapshot.Run("B", "GBA", 1, true, "x", true)), 1)
        assertEquals(v + 1, StreamHub.version)
    }

    @Test
    fun `the address to open on the PC is the setup guide, not one source`() {
        val url = StreamHub.url()
        assertContains(url, ":${StreamHub.PORT}/?k=")
        assertFalse(url.contains("/tracker"))
        assertTrue(url.startsWith("http://"))
    }

    @Test
    fun `the tracker page says so in one line when the game has no tracker`() {
        val page = java.io.File("src/main/assets/stream/tracker.html").readText()
        assertContains(page, "no tracker for this game")
        assertFalse(page.contains("Untracked game."), "the old wording is gone")
        // It names the game when the snapshot does, and is escaped like every other field.
        assertContains(page, "esc(s.title)")
    }

    // ------------------------------------------------------------------ the pages themselves (UX audit P0-17)

    private val tracker = java.io.File("src/main/assets/stream/tracker.html").readText()

    private fun needNode() = Assume.assumeTrue("node is not on the PATH, so the pages' own scripts are not run", PageRunner.available)

    private fun trackerShowing(state: Map<String, Any?>) =
        PageRunner.run("tracker", tracker, "?k=abcd", listOf(mapOf("state" to state))).steps.single()

    @Test
    fun `the tracker page reads the run flag wherever it prints the attempt`() {
        // Source level, so it holds even where node is missing: every place the page prints the attempt is behind the flag.
        val lines = tracker.replace("\r\n", "\n").lines().filter { it.contains("s.attempt") }
        assertEquals(2, lines.size, "the footer and the run over banner are the only two places: $lines")
        for (l in lines) assertContains(l, "s.run===true", message = "the attempt is printed only for a run: $l")
    }

    @Test
    fun `the tracker shows the attempt in a run and in no other game`() {
        needNode()
        val inRun = trackerShowing(PageSnapshots.of(run = true)).str("own")
        assertContains(inRun, "Attempt <b>812</b>")

        // The same game state from a library game, a ROM hack or a standard Nuzlocke: the store still holds 812.
        val elsewhere = trackerShowing(PageSnapshots.of(run = false)).str("own")
        assertFalse(elsewhere.contains("Attempt"), "no attempt outside a run")
        assertFalse(elsewhere.contains("812"), "and none of the last run's number gets through some other way")
        assertContains(elsewhere, "Badges <b>2</b>", message = "the rest of the footer is still there")
    }

    @Test
    fun `the run over banner names the attempt in a run and only says run over elsewhere`() {
        needNode()
        val lost = trackerShowing(PageSnapshots.of(run = true, outcome = GameOver.LOST)).str("over")
        assertContains(lost, "RUN OVER")
        assertContains(lost, "attempt 812")
        val won = trackerShowing(PageSnapshots.of(run = true, outcome = GameOver.WON)).str("over")
        assertContains(won, "RUN WON")
        assertContains(won, "attempt 812")

        val elsewhere = trackerShowing(PageSnapshots.of(run = false, outcome = GameOver.LOST)).str("over")
        assertContains(elsewhere, "RUN OVER")
        assertFalse(elsewhere.contains("attempt"), "the banner keeps its words and drops the number")
        assertFalse(elsewhere.contains("812"))
    }

    @Test
    fun `the tracker demo is a run, so the boxes can be placed with the attempt in place`() {
        needNode()
        val battle = PageRunner.run("tracker", tracker, "?k=abcd&demo=battle").initial
        assertContains(battle.str("own"), "Attempt <b>812</b>")
        assertContains(battle.str("enemy"), "Sandile")
        assertEquals(0L, battle.long("sources"), "a demo asks the phone for nothing")

        val over = PageRunner.run("tracker", tracker, "?k=abcd&demo=over").initial
        assertContains(over.str("over"), "RUN OVER")
        assertContains(over.str("over"), "attempt 812")
    }

    @Test
    fun `the attempt counter has a demo that shows a number and asks the phone for nothing`() {
        needNode()
        val demo = PageRunner.run("attempts", StreamPages.attempts(), "?k=abcd&demo=1&label=TRIES").initial
        assertEquals("812", demo.str("n"), "the tracker's own demo number, so the two boxes match")
        assertEquals("on", demo.str("box"), "the box is visible")
        assertEquals("TRIES", demo.str("label"), "the label option still works in a demo")
        assertEquals(0L, demo.long("fetches"), "no poll")
        assertEquals(0L, demo.long("sources"), "no event stream")

        // Without it, and with no run on, the box stays hidden: that is the whole reason a demo is needed.
        val live = PageRunner.run("attempts", StreamPages.attempts(), "?k=abcd").initial
        assertEquals("", live.str("box"))
        assertEquals(1L, live.long("sources"), "and a page that is not a demo does listen to the phone")
    }

    @Test
    fun `without a demo the attempt counter follows the run flag the phone sends`() {
        needNode()
        val steps = listOf(
            mapOf("state" to PageSnapshots.of(run = true, attempt = 7)),
            mapOf("state" to PageSnapshots.of(run = false, attempt = 7)),
            mapOf("state" to PageSnapshots.of(run = true, attempt = 8)),
        )
        val seen = PageRunner.run("attempts", StreamPages.attempts(), "?k=abcd", steps).steps
        assertEquals("on" to "7", seen[0].str("box") to seen[0].str("n"), "a run shows its attempt")
        assertEquals("", seen[1].str("box"), "a game that is not a run hides the box, whatever number the store holds")
        assertEquals("on" to "8", seen[2].str("box") to seen[2].str("n"), "and the next run's number comes back")
    }
}
