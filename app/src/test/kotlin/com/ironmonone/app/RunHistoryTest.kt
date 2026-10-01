package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The every-platform run history behind personal bests and the death card (RunHistory). */
class RunHistoryTest {
    private val dir: File = Files.createTempDirectory("runhist").toFile()
    private fun history() = RunHistory(File(dir, "runhistory-emerald-u.tsv"))

    private fun run(attempt: Int, badges: Int, outcome: RunRecord.Outcome = RunRecord.Outcome.LOST, killer: String = "Mawile", seconds: Int = 600) =
        RunRecord(
            attempt = attempt, seed = "%016x".format(attempt.toLong() * 7919), ruleset = "RSE Kaizo.rnqs",
            started = 1_000L * attempt, ended = 1_000L * attempt + seconds * 1000L, playSeconds = seconds,
            outcome = outcome, badges = badges, lead = RunRecord.Mon(252, "Treecko", 24),
            killer = RunRecord.Mon(303, killer, 24), trainer = if (attempt % 2 == 0) "Trainer Kylie" else "",
            location = "Route 116",
        )

    @Test fun `runs survive a reload, names with tabs included`() {
        val h = history()
        h.record(run(1, 0))
        h.record(run(2, 1).copy(location = "Route\t116", trainer = "Leader\nRoxanne"))
        val back = history().all()
        assertEquals(2, back.size)
        assertEquals("Route 116", back[1].location)
        assertEquals("Leader Roxanne", back[1].trainer)
        assertEquals(RunRecord.Mon(303, "Mawile", 24), back[0].killer)
        // The same attempt and seed recorded again replaces the first line.
        h.record(run(2, 2))
        assertEquals(listOf(1, 2), history().all().map { it.attempt })
        assertEquals(2, history().all().last().badges)
        // The restarts and a kept save survive the reload (IronMON rules check, 2026-09-30); a line from before they
        // existed reads as none.
        h.record(run(3, 0).copy(resets = 2, keptSave = true))
        assertEquals(2, history().all().last().resets); assertTrue(history().all().last().keptSave)
        assertEquals(0, history().all().first().resets); assertFalse(history().all().first().keptSave)
        val old = RunRecord.decode(run(4, 0).encode().split('\t').take(14).joinToString("\t"))!!
        assertEquals(0, old.resets); assertFalse(old.keptSave)
    }

    @Test fun `the best run is a win, else the most badges, first reached`() {
        val h = history()
        h.record(run(1, 1)); h.record(run(2, 3)); h.record(run(3, 3)); h.record(run(4, 2))
        assertEquals(2, h.best()?.attempt, "3 badges first on attempt 2")
        assertTrue(h.isBest(run(2, 3))); assertFalse(h.isBest(run(3, 3)))
        h.record(run(5, 0, RunRecord.Outcome.WON))
        assertEquals(5, h.best()?.attempt)
        assertEquals(null, h.best("FRLG Kaizo.rnqs"))
    }

    @Test fun `common killers and total time`() {
        val h = history()
        h.record(run(1, 0, killer = "Poochyena")); h.record(run(2, 0, killer = "Mawile")); h.record(run(3, 0, killer = "Poochyena"))
        h.record(run(4, 0, RunRecord.Outcome.ENDED, killer = "Wingull"))
        assertEquals(listOf("Poochyena" to 2, "Mawile" to 1), h.commonKillers())
        assertEquals(2400L, h.totalPlaySeconds())
    }

    @Test fun `the death card line says what ended the run`() {
        assertEquals(
            "Attempt 2, Pokemon Emerald (RSE Kaizo): lost to Lv.24 Mawile (Trainer Kylie) on Route 116. 1 badge, 10:00. No state loads or retries. Seed ${"%016x".format(2L * 7919)}.",
            runSummaryLine(run(2, 1), "Pokemon Emerald"),
        )
        assertEquals("1:01:01", playTimeText(3661))
        val won = runSummaryLine(run(3, 8, RunRecord.Outcome.WON, seconds = 0), "Pokemon Emerald")
        assertTrue(won.contains(": won. 8 badges."), won)
    }
}
