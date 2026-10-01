package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The death card (roadmap item 6): which end of an attempt is its record, and what the
 * game-over popup's lines say about it. The first loss is the run's end; playing on cannot
 * raise a best; Retry undoes the loss and is counted.
 */
class DeathCardTest {
    private val dir: File = Files.createTempDirectory("deathcard").toFile()
    private fun history() = RunHistory(File(dir, "runhistory-emerald-u.tsv"))
    private val seed = "5261db990e333467"

    private fun rec(attempt: Int, badges: Int, outcome: RunRecord.Outcome = RunRecord.Outcome.LOST, killer: RunRecord.Mon? = RunRecord.Mon(82, "Magneton", 22),
                    trainer: String = "LEADER WATTSON", place: String = "Mauville City", secs: Int = 6500, restores: Int = 0, ruleset: String = "Kaizo.rnqs") =
        RunRecord(attempt, if (attempt == 37) seed else "%016x".format(attempt), ruleset, 0L, 0L, secs, outcome, badges, null, killer, trainer, place, restores)

    @Test fun `the first loss of an attempt is its record, playing on changes nothing`() {
        val h = history()
        val (first, earlier1) = fileRunEnd(h, 37, seed, reopened = false) { rec(37, 2, restores = it) }
        assertFalse(earlier1); assertEquals(2, first.badges)
        // Continue playing, two more badges, another loss: the run already ended at the first.
        val (second, earlier2) = fileRunEnd(h, 37, seed, reopened = false) { rec(37, 4, killer = RunRecord.Mon(330, "Flygon", 40), restores = it) }
        assertTrue(earlier2); assertEquals(first, second)
        // Even a win: a loss the player did not retry is still a lost run.
        val (third, _) = fileRunEnd(h, 37, seed, reopened = false) { rec(37, 8, RunRecord.Outcome.WON, restores = it) }
        assertEquals(RunRecord.Outcome.LOST, third.outcome)
        assertEquals(listOf(2), history().all().map { it.badges })
        assertEquals("FIRST LOSS", DeathCard("Pokemon Emerald", second, null, earlier2).headline()?.first)
    }

    @Test fun `Retry undoes the loss and is counted, and the next end replaces it`() {
        val h = history()
        assertFalse(fileRetry(h, 37, seed), "nothing filed yet")
        fileRunEnd(h, 37, seed, reopened = false) { rec(37, 2, restores = it) }
        assertTrue(fileRetry(h, 37, seed))
        assertEquals(1, history().find(37, seed)?.restores)
        val (r, earlier) = fileRunEnd(h, 37, seed, reopened = true) { rec(37, 3, killer = RunRecord.Mon(335, "Zangoose", 27), trainer = "", place = "Route 117", restores = it) }
        assertFalse(earlier)
        assertEquals(3, r.badges); assertEquals(1, r.restores, "the retry stays on the record")
        assertEquals(listOf(3), history().all().map { it.badges })
    }

    @Test fun `the lines say what ended the run`() {
        val trainerLoss = DeathCard("Pokemon Emerald", rec(37, 2), null, earlier = false)
        assertEquals("LOST TO" to "Lv.22 Magneton (LEADER WATTSON)", trainerLoss.headline())
        assertEquals("2 badges, 1:48:20 played", trainerLoss.statsText())
        val wild = DeathCard("Pokemon Emerald", rec(37, 1, killer = RunRecord.Mon(335, "Zangoose", 27), trainer = "", place = "Route 117", secs = 0), null, earlier = false)
        assertEquals("LOST TO" to "Lv.27 Zangoose, Route 117", wild.headline())
        assertEquals("1 badge", wild.statsText())
        assertEquals("ENDED ON" to "Route 29", DeathCard("Pokemon HeartGold", rec(37, 0, killer = null, trainer = "", place = "Route 29"), null, false).headline())
        assertNull(DeathCard("Pokemon HeartGold", rec(37, 0, killer = null, trainer = "", place = ""), null, false).headline())
        val won = DeathCard("Pokemon Emerald", rec(37, 8, RunRecord.Outcome.WON, killer = null, trainer = ""), null, false)
        assertNull(won.headline(), "the title says it was won")
        assertEquals("8 badges, 1:48:20 played", won.statsText())
    }

    @Test fun `a new best is past an earlier run on the same settings, never the first`() {
        assertFalse(DeathCard("E", rec(37, 2), null, false).newBest, "the first run on these settings")
        assertNull(DeathCard("E", rec(37, 2), null, false).bestText())
        val behind = DeathCard("E", rec(37, 2), rec(31, 3, secs = 9120), false)
        assertFalse(behind.newBest); assertEquals("Best: 3 badges, attempt 31", behind.bestText())
        val tie = DeathCard("E", rec(37, 3), rec(31, 3), false)
        assertFalse(tie.newBest, "a tie goes to the run that got there first")
        val past = DeathCard("E", rec(37, 4), rec(31, 3), false)
        assertTrue(past.newBest); assertNull(past.bestText())
        assertTrue(past.shareText().endsWith(" New best."), past.shareText())
        assertTrue(DeathCard("E", rec(37, 0, RunRecord.Outcome.WON), rec(31, 8), false).newBest, "a win beats any loss")
        assertEquals("Best: won, attempt 31", DeathCard("E", rec(37, 8), rec(31, 5, RunRecord.Outcome.WON), false).bestText())
        assertEquals("Best: 1 badge, attempt 31", DeathCard("E", rec(37, 0), rec(31, 1), false).bestText())
    }

    @Test fun `the best other run is on the same settings and is not this run`() {
        val h = history()
        h.record(rec(31, 3)); h.record(rec(32, 5, ruleset = "Survival.rnqs")); h.record(rec(37, 4))
        assertEquals(31, h.bestOther(rec(37, 4))?.attempt)
        assertNull(h.bestOther(rec(32, 5, ruleset = "Survival.rnqs")))
    }

    @Test fun `the integrity line counts what went back in time`() {
        val t0 = 1_000L
        val events = listOf(
            RunEvents.Entry(t0, RunEvents.Kind.LOAD, "slot 1", ""), RunEvents.Entry(t0, RunEvents.Kind.UNDO, "slot 1", ""),
            RunEvents.Entry(t0, RunEvents.Kind.RESTORE, "3 min", ""), RunEvents.Entry(t0, RunEvents.Kind.RETRY, "battle start", ""),
            RunEvents.Entry(t0, RunEvents.Kind.RESUME, "auto", ""),
        )
        assertEquals(3, rewinds(events), "a load, a restore and a retry; the undo of a load is not another")
        assertEquals("No state loads or retries", integrityText(0, 0))
        assertEquals("1 state load or retry, 1 resume after the app closed", integrityText(1, 1))
        assertEquals("3 state loads or retries, 2 resumes after the app closed", integrityText(3, 2))
        // IronMON rules check R5 and R11 (2026-09-30): File > Restart and a kept save are said, never counted as loads.
        assertEquals("No state loads or retries, 1 restart", integrityText(0, 0, resets = 1))
        assertEquals("1 state load or retry, 2 restarts, started from the last run's in-game save", integrityText(1, 0, resets = 2, keptSave = true))
        val clean = DeathCard("E", rec(37, 2), null, false)
        assertEquals("2 badges, 1:48:20 played", clean.statsText(), "nothing to say on a clean run's card")
        val back = DeathCard("E", rec(37, 2).copy(restores = 2, resumes = 1), null, false)
        assertEquals("2 badges, 1:48:20 played, 2 state loads or retries, 1 resume after the app closed", back.statsText())
        assertTrue("2 state loads or retries, 1 resume after the app closed." in back.shareText())
        assertTrue("No state loads or retries." in clean.shareText(), "the shared line says a clean run is clean")
    }

    @Test fun `the counts survive the history file`() {
        val h = history()
        h.record(rec(37, 2).copy(restores = 4, resumes = 1))
        val back = history().find(37, seed)!!
        assertEquals(4, back.restores); assertEquals(1, back.resumes)
        // A line written before resumes were counted reads as none (the columns after restores cut off).
        val old = back.encode().split('\t').take(13).joinToString("\t")
        assertEquals(0, RunRecord.decode(old)!!.resumes)
    }

    private fun own(species: Int, name: String, level: Int, hp: Int) = TrackedMon(
        mon = PokemonDecoder.Mon(pid = 1L, level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = hp, maxHp = 50, atk = 5, def = 5, spe = 5, spAtk = 5, spDef = 5),
        speciesName = name, moveNames = emptyList(), base = null,
    )

    @Test fun `a run's record is read from the state it ended on`() {
        val enemy = EnemyInfo(species = 82, speciesName = "Magneton", level = 22, curHp = 30, maxHp = 63, type1 = 13, type2 = 8, base = null,
            movesSeen = emptyList(), moveRows = emptyList())
        val state = TrackerState(partyCount = 2, party = listOf(own(123, "Scyther", 24, 12), own(41, "Zubat", 10, 0)),
            inBattle = true, isWildBattle = false, enemy = enemy, badges = 0b11, badgeSet = "RSE", routeName = "Mauville City")
        val r = runRecordAtEnd(attempt = 37, seed = seed, ruleset = "Kaizo.rnqs", started = 5L, playSeconds = 6500, won = false,
            gba = state, nds = null, trainerName = "LEADER WATTSON")
        assertEquals(RunRecord.Mon(41, "Zubat", 10), r.lead, "the Pokemon that fainted, not the first slot")
        assertEquals(RunRecord.Mon(82, "Magneton", 22), r.killer)
        assertEquals(2, r.badges); assertEquals("Mauville City", r.location); assertEquals("LEADER WATTSON", r.trainer)
        val won = runRecordAtEnd(37, seed, "Kaizo.rnqs", 5L, 6500, true, state, null, "LEADER WATTSON")
        assertNull(won.killer); assertEquals("", won.trainer); assertEquals(RunRecord.Outcome.WON, won.outcome)
    }
}
