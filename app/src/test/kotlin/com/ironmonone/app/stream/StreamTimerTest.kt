package com.ironmonone.app.stream

import com.ironmonone.app.RunClock
import com.ironmonone.app.RunRecord
import com.ironmonone.tracker.RunOutcome
import org.junit.Assume
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The run timer and splits source (/timer, streamer list item 3, 2026-10-05): the time is RunClock's, the splits are
 * the run's own (RunProgress) against the best earlier run with splits on the same settings file.
 */
class StreamTimerTest {
    private val dir: File = Files.createTempDirectory("timer").toFile()
    private var clock = 0L

    @BeforeTest fun setUp() {
        RunClock.load(File(dir, "run-clock.txt"))
        StreamHub.now = { clock }
        StreamHub.newRun()
    }

    @AfterTest fun tearDown() {
        StreamHub.newRun(); StreamHub.timerRun = null; StreamHub.splitBest = null
        StreamHub.now = { android.os.SystemClock.elapsedRealtime() }
    }

    private fun rec(attempt: Int, badges: Int, splits: Map<Int, Int>, outcome: RunRecord.Outcome = RunRecord.Outcome.LOST, ruleset: String = "Kaizo.rnqs") =
        RunRecord(attempt, "%016x".format(attempt), ruleset, 0L, 0L, 4000, outcome, badges, null, null, "", "", splits = splits)

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String) = MiniJson(json).parse() as Map<String, Any?>

    // ------------------------------------------------------------------ the arithmetic

    @Test
    fun `each badge's split is compared with the best run's, ahead negative and behind positive`() {
        val rows = StreamTimer.rows(mapOf(0 to 754, 1 to 1890, 2 to 2655), mapOf(0 to 799, 1 to 1818, 2 to 2655, 3 to 3410))
        assertEquals(listOf(1, 2, 3, 4), rows.map { it["badge"] }, "badge numbers, in badge order")
        assertEquals(listOf(-45, 72, 0, null), rows.map { it["delta"] })
        assertEquals(listOf(754, 1890, 2655, null), rows.map { it["at"] }, "badge 4 is not earned yet")
        assertEquals(3410, rows[3]["best"], "but the best run's time for it is there")
        assertEquals(listOf(null, null), StreamTimer.rows(mapOf(0 to 600, 1 to 900), emptyMap()).map { it["delta"] }, "nothing to compare with")
        assertEquals("-0:45", StreamTimer.delta(-45)); assertEquals("+1:12", StreamTimer.delta(72)); assertEquals("0:00", StreamTimer.delta(0))
        assertEquals("1:02:03", StreamTimer.clock(3723)); assertEquals("+1:02:03", StreamTimer.delta(3723))
    }

    @Test
    fun `the run compared with is the best earlier one with splits on the same settings, never the run itself`() {
        val runs = listOf(
            rec(1, 2, mapOf(0 to 500, 1 to 1500)),
            rec(2, 4, emptyMap()),                                    // the most badges, but filed before splits
            rec(3, 3, mapOf(0 to 600, 1 to 1400, 2 to 2400)),
            rec(4, 3, mapOf(0 to 610, 1 to 1300, 2 to 2200)),          // as far, and faster to its last badge
            rec(5, 6, mapOf(0 to 400), ruleset = "Other.rnqs"),       // another settings file
            rec(9, 7, mapOf(0 to 300)),                               // the run in play itself, once filed
        )
        assertEquals(4, StreamTimer.bestForSplits(runs, "Kaizo.rnqs", 9, "%016x".format(9))?.attempt)
        val won = runs + rec(6, 2, mapOf(0 to 900, 1 to 2000), RunRecord.Outcome.WON)
        assertEquals(6, StreamTimer.bestForSplits(won, "Kaizo.rnqs", 9, "%016x".format(9))?.attempt, "a win first")
        assertNull(StreamTimer.bestForSplits(runs, "New.rnqs", 9, "x"))
        assertEquals(mapOf("attempt" to 4, "badges" to 3, "won" to false, "seconds" to 4000), StreamTimer.bestInfo(runs[3]))
    }

    // ------------------------------------------------------------------ the hub's timer: counting, paused, held at the end

    @Test
    fun `the timer counts while the run is played, says paused when it is not, and holds its time when the run ends`() {
        assertEquals(StreamTimer.NO_RUN, StreamHub.timerJson(), "no run in Play")
        val key = RunClock.key("emerald-u", 812)
        StreamHub.timerRun = StreamHub.TimerRun(key, 812)
        RunClock.observe(key, 0)
        for (t in 500L..10_500L step 500) RunClock.observe(key, t)
        clock = 10_600
        var m = parse(StreamHub.timerJson())
        assertEquals(true, m["run"]); assertEquals(812L, m["attempt"])
        assertEquals(10_500L, m["ms"]); assertEquals(true, m["running"]); assertNull(m["ended"])
        // No reads for longer than RunClock counts as play: the app is in the background, or the screen is off.
        clock = 10_500 + RunClock.MAX_STEP_MS + 1
        m = parse(StreamHub.timerJson())
        assertEquals(false, m["running"]); assertEquals(10_500L, m["ms"])
        // The run ends: the time it ended at is held, though the clock goes on as the player plays on after the loss.
        clock = 20_000
        RunClock.observe(key, 20_000)
        StreamHub.ended = RunOutcome.LOST
        for (t in 20_500L..30_000L step 500) RunClock.observe(key, t)
        m = parse(StreamHub.timerJson())
        assertEquals(10_500L, m["ms"], "held at the end"); assertEquals(false, m["running"]); assertEquals("LOST", m["ended"])
        // Retry undoes the loss: it counts on.
        StreamHub.ended = null
        m = parse(StreamHub.timerJson())
        assertEquals(20_500L, m["ms"]); assertNull(m["ended"])
    }

    @Test
    fun `a Nuzlocke's timer prints no attempt, and the splits ride along`() {
        StreamHub.timerRun = StreamHub.TimerRun(RunClock.key("firered-u", 3), null)
        StreamHub.splitRows = StreamTimer.rows(mapOf(0 to 600), mapOf(0 to 700))
        StreamHub.splitBest = StreamTimer.bestInfo(rec(2, 1, mapOf(0 to 700)))
        val m = parse(StreamHub.timerJson())
        assertNull(m["attempt"])
        @Suppress("UNCHECKED_CAST") val rows = m["splits"] as List<Map<String, Any?>>
        assertEquals(-100L, rows.single()["delta"])
        assertEquals(2L, (m["best"] as Map<*, *>)["attempt"])
    }

    @Test
    fun `RunClock says how long and whether it is counting`() {
        val key = RunClock.key("emerald-u", 1)
        assertFalse(RunClock.ticking(key, 0))
        RunClock.observe(key, 1_000)
        assertTrue(RunClock.ticking(key, 1_000), "the first read: the next will count")
        RunClock.observe(key, 2_700)
        assertEquals(1_700L, RunClock.millis(key)); assertEquals(1, RunClock.of(key))
        assertTrue(RunClock.ticking(key, 2_700 + RunClock.MAX_STEP_MS))
        assertFalse(RunClock.ticking(key, 2_701 + RunClock.MAX_STEP_MS))
        assertFalse(RunClock.ticking(RunClock.key("emerald-u", 2), 2_700), "another run is not counting")
    }

    // ------------------------------------------------------------------ the page

    private val ids = listOf("box", "t", "state", "label", "splits", "bestline")

    private fun run(steps: List<Map<String, Any?>>, search: String = "?k=abcd"): PageRunner.Result {
        Assume.assumeTrue("node is not on the PATH, so the pages' own scripts are not run", PageRunner.available)
        return PageRunner.run("timer", StreamOverlays.timer(), search, steps, mapOf("ids" to ids))
    }

    private fun timer(ms: Long, running: Boolean, ended: String? = null, attempt: Int? = 812): Map<String, Any?> = MiniJson(StreamTimer.json(
        attempt, ms, running, ended, StreamTimer.rows(mapOf(0 to 754, 1 to 1890), mapOf(0 to 799, 1 to 1818, 2 to 2655)),
        StreamTimer.bestInfo(rec(640, 5, mapOf(0 to 799))),
    )).parse() as Map<String, Any?>

    private fun event(d: Any?) = mapOf<String, Any?>("event" to mapOf("type" to "timer", "data" to d))

    @Test
    fun `the page shows the time, counts on between updates, and lists the splits against the best run`() {
        val r = run(listOf(event(timer(2_712_000, true)), mapOf("advance" to 1_000), mapOf("advance" to 5_000),
            event(timer(2_713_000, false)), event(timer(2_713_000, false, "LOST")), event(mapOf("run" to false))))
        assertEquals("", r.initial.str("box.cls"), "see-through before any data")
        val first = r.steps[0]
        assertEquals("on", first.str("box.cls"))
        assertEquals("45:12", first.str("t.text"))
        assertEquals("ATTEMPT 812", first.str("label.text"))
        assertEquals("", first.str("state.text"))
        val html = first.str("splits.html")
        assertTrue("Badge 1</td><td class=\"n\">12:34</td><td class=\"n ahead\">-0:45" in html, html)
        assertTrue("Badge 2</td><td class=\"n\">31:30</td><td class=\"n behind\">+1:12" in html, html)
        assertTrue("<tr class=\"todo\"><td>Badge 3</td><td class=\"n\">-</td><td class=\"n next\">best 44:15" in html, html)
        assertEquals("Against attempt 640, 5 badges", first.str("bestline.text"))
        assertEquals("45:13", r.steps[1].str("t.text"), "it counts on between the phone's updates")
        assertEquals("45:14", r.steps[2].str("t.text"), "but never more than 2.5 s past the last one")
        assertEquals("PAUSED", r.steps[3].str("state.text")); assertEquals("45:13", r.steps[3].str("t.text"))
        assertEquals("RUN OVER", r.steps[4].str("state.text"))
        assertEquals("", r.steps[5].str("box.cls"), "no run: see-through again")
    }

    @Test
    fun `splits=0 leaves the splits off and a Nuzlocke prints no attempt`() {
        val r = run(listOf(event(timer(5_000, true, attempt = null))), "?k=abcd&splits=0")
        assertEquals("", r.steps[0].str("splits.html"))
        assertEquals("", r.steps[0].str("bestline.text"))
        assertEquals("RUN TIME", r.steps[0].str("label.text"))
        assertEquals("0:05", r.steps[0].str("t.text"))
    }

    @Test
    fun `the timer demo shows splits with no phone`() {
        val r = run(emptyList(), "?k=abcd&demo=1")
        assertEquals("on", r.initial.str("box.cls"))
        assertEquals(0L, r.initial.long("sources"))
        assertTrue("ahead" in r.initial.str("splits.html") && "behind" in r.initial.str("splits.html"))
    }
}
