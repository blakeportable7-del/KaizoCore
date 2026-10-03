package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Hide stats until summary shown": Tracker.Data.hasCheckedSummary, per attempt. */
class SummaryChecksTest {
    private val dir = Files.createTempDirectory("sum").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun `an attempt stays checked once a summary is seen, and a new attempt starts hidden`() {
        val f = java.io.File(dir, "summary-checked.txt")
        SummaryChecks.load(f)
        assertFalse(SummaryChecks.checked(12))
        SummaryChecks.mark(12)
        assertTrue(SummaryChecks.checked(12))
        SummaryChecks.load(f)                       // a restart
        assertTrue(SummaryChecks.checked(12))
        assertFalse(SummaryChecks.checked(13), "a new run hides again")
    }

    /**
     * The Game Boy references never read a summary screen (no sMonSummaryScreen
     * address there), so on Red to Crystal the option would hide the card for
     * the whole run. It does not apply there; Gen 3 hides until a summary is seen.
     */
    @Test
    fun `a Game Boy card is never hidden, a Gen 3 card is until a summary is seen`() {
        SummaryChecks.load(File(dir, "summary-checked.txt"))
        for (gen in 1..2) assertFalse(SummaryChecks.hidesStats(optionOn = true, gameDataRandomized = true, attempt = 7, generation = gen), "generation $gen")
        assertTrue(SummaryChecks.hidesStats(optionOn = true, gameDataRandomized = true, attempt = 7, generation = 3))
        assertFalse(SummaryChecks.hidesStats(optionOn = false, gameDataRandomized = true, attempt = 7, generation = 3), "option off")
        assertFalse(SummaryChecks.hidesStats(optionOn = true, gameDataRandomized = false, attempt = 7, generation = 3), "an unrandomized game")
        SummaryChecks.mark(7)
        assertFalse(SummaryChecks.hidesStats(optionOn = true, gameDataRandomized = true, attempt = 7, generation = 3), "summary seen")
    }

    /** The panel asks the rule with its generation, and the gear offers no switch on a Game Boy game. */
    @Test
    fun `the panel hands the rule its generation and the Game Boy gear has no switch`() {
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("val hideStats = if (runScoped) SummaryChecks.hides(attempt, state.gameDataRandomized, generation)" in src("TrackerPanel.kt"))
        assertTrue("hidden = hideStats," in src("TrackerPanel.kt"))
        assertTrue("hidesStats(TrackerOptions.hideStatsUntilSummary, gameDataRandomized, attempt, generation)" in src("SummaryChecks.kt"))
        val gear = src("TrackerGearDialog.kt").lines().single { "\"Hide stats until summary shown\"" in it }
        // DS games have no summary screen this reads either (SetupShowsWhatWorksTest, 2026-09-30).
        assertTrue(gear.trim().startsWith("if (!gameBoy && !ds && scope.randomized) GearToggle("), gear)
        assertTrue("gameBoy = platform == com.ironmonone.core.Platform.GBC," in src("PlayScreen.kt"))
    }

    /**
     * rc33 audit P1 #57: PlayScreen passed the run's attempt for every game, so a library game or a Nuzlocke counted its
     * Pokemon Center visits on the run and marked the run's summary as seen.
     */
    @Test
    fun `only a run's game reads and writes the run's heal count and summary check`() {
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        val play = src("PlayScreen.kt")
        assertEquals(2, Regex(Regex.escape("coverage = coverage, runScoped = session.isRun,")).findAll(play).count(), "landscape and portrait")
        val panel = src("TrackerPanel.kt")
        val effect = panel.substringAfter("var sessionSummary by remember { mutableStateOf(false) }").substringBefore("val hideStats")
        assertTrue("if (runScoped) {" in effect && "PcHeals.observe(attempt, state.centerHealsStat)" in effect && "SummaryChecks.mark(attempt)" in effect)
        assertTrue("} else if (state.summaryOpen) sessionSummary = true" in effect, "outside a run the check is the session's")
        assertTrue("pcHealsAttempt = attempt.takeIf { runScoped && TrackerOptions.trackPcHeals }" in panel, "and no heal counter")
        assertTrue("runScoped = runScoped," in panel)
    }
}
