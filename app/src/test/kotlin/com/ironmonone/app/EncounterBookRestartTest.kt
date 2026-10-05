package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.EnemyPartyMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An app start in the middle of a battle (rc35.2 QA, attempt 791): the floating tracker's Seen (Trainer) for Lorelei's
 * Leafeon read 17, then 18, then 19 across three app starts with no new battle. EncounterBook kept the battle's
 * "already counted" marks only in memory, so every new process counted the battle it resumed into again. Each restart
 * here is what the app does: the run's files written out (DiskWriter), a new StatMarks read from them and a new book,
 * then the same battle fed again. The PC tracker counts once per battle per opposing Pokemon (Battle.lua:522-535,
 * incrementEnemyEncounter); so must we, however often the app is closed.
 */
class EncounterBookRestartTest {
    private val dir: File = Files.createTempDirectory("encrestart").toFile()

    /** The run's notes as a new process reads them: everything handed to the writer is on disk first. */
    private fun restart(): StatMarks {
        assertTrue(DiskWriter.drain(), "the run's files were written")
        return StatMarks(File(dir, "marks.txt"))
    }

    private fun enemy(species: Int, level: Int, pid: Long) = EnemyInfo(
        species = species, speciesName = "#$species", level = level, curHp = 10, maxHp = 10, type1 = 0, type2 = 0,
        base = null, movesSeen = emptyList(), pid = pid,
    )

    private fun battle(on: EnemyInfo, wild: Boolean, party: List<EnemyPartyMon>, field: List<Int>) = TrackerState(
        partyCount = 1, party = emptyList(), inBattle = true, isWildBattle = wild, enemy = on,
        enemyParty = party, enemyOnField = field,
    )

    private val overworld = TrackerState(partyCount = 1, party = emptyList(), inBattle = false, isWildBattle = false)

    private fun feed(book: EncounterBook, m: StatMarks, vararg states: TrackerState) =
        states.forEach { book.onGba(m, EncounterBook.gbaUpdate(it), save = true) }

    // Lorelei, as a randomized run has her: five Pokemon, the fourth a Leafeon (470).
    private val lorelei = listOf(
        EnemyPartyMon(0, 87, 52, true, pid = 0x51A0), EnemyPartyMon(1, 91, 51, true, pid = 0x51A1),
        EnemyPartyMon(2, 80, 52, true, pid = 0x51A2), EnemyPartyMon(3, 470, 54, true, pid = 0x51A3),
        EnemyPartyMon(4, 131, 54, true, pid = 0x51A4),
    )
    private fun loreleiOut(slot: Int) = battle(enemy(lorelei[slot].species, lorelei[slot].level, lorelei[slot].pid), false, lorelei, listOf(slot))

    @Test fun `an app start resumed into the same trainer battle does not count it again`() {
        var m = restart()
        // Leafeon met in 16 earlier battles of this run.
        repeat(16) { m.trackEncounter(470, wild = false) }
        feed(EncounterBook(), m, loreleiOut(0), loreleiOut(3))
        assertEquals(17, m.encounters(470, wild = false))
        assertEquals(1, m.encounters(87, wild = false))
        // Three app starts, each resumed into the same battle with Leafeon out (the auto slot, CrashResume).
        repeat(3) {
            m = restart()
            feed(EncounterBook(), m, loreleiOut(3), loreleiOut(3))
            assertEquals(17, m.encounters(470, wild = false), "Seen (Trainer) after restart ${it + 1}")
            assertEquals(1, m.encounters(87, wild = false))
        }
        // A start from an auto slot written before Dewgong came out, then Dewgong: counted once all battle.
        m = restart()
        val book = EncounterBook()
        feed(book, m, loreleiOut(0), loreleiOut(1))
        assertEquals(1, m.encounters(87, wild = false))
        assertEquals(1, m.encounters(91, wild = false), "a Pokemon this battle had not shown yet is counted")
        // The battle ends: the whole party's levels, then the next battle counts as before.
        feed(book, m, overworld)
        assertEquals(54, restart().lastLevelSeen(470))
        feed(book, m, loreleiOut(3))
        assertEquals(18, m.encounters(470, wild = false))
    }

    @Test fun `reads from before the saved state loads do not end the battle`() {
        var m = restart()
        feed(EncounterBook(), m, loreleiOut(3))
        assertEquals(1, m.encounters(470, false))
        // The new process reads the game before the auto slot is loaded (title screen, overworld), then the battle.
        m = restart()
        val book = EncounterBook()
        feed(book, m, overworld, overworld, loreleiOut(3))
        assertEquals(1, m.encounters(470, false))
        assertNull(m.lastLevelSeen(470), "the battle has not ended")
    }

    @Test fun `a different battle after a start ends the one that was in progress`() {
        var m = restart()
        feed(EncounterBook(), m, loreleiOut(3))
        // The app was closed mid-battle and opened on a state from another battle: a wild Leafeon.
        m = restart()
        val book = EncounterBook()
        val wild = battle(enemy(470, 30, 0xBEEF), true, listOf(EnemyPartyMon(0, 470, 30, true, pid = 0xBEEF)), listOf(0))
        feed(book, m, wild)
        assertEquals(1, m.encounters(470, wild = true), "the new battle is counted")
        assertEquals(1, m.encounters(470, wild = false))
        // Lorelei's battle ended unseen: its levels are recorded as at any battle's end.
        assertEquals(52, m.lastLevelSeen(87))
        feed(book, m, overworld)
        assertEquals(30, restart().lastLevelSeen(470))
    }

    @Test fun `a battle that ended before the app closed is not remembered`() {
        var m = restart()
        feed(EncounterBook(), m, loreleiOut(3), overworld)
        // A new start, and the same trainer again (a state from before that battle, loaded on purpose): counted again,
        // as the PC tracker does when a state from before a battle is loaded.
        m = restart()
        feed(EncounterBook(), m, loreleiOut(3))
        assertEquals(2, m.encounters(470, false))
    }

    @Test fun `a wild battle resumed after a start is counted once`() {
        var m = restart()
        val wild = battle(enemy(16, 5, 0x7777), true, listOf(EnemyPartyMon(0, 16, 5, true, pid = 0x7777)), listOf(0))
        feed(EncounterBook(), m, wild)
        m = restart()
        feed(EncounterBook(), m, wild, wild)
        assertEquals(1, m.encounters(16, wild = true))
    }

    @Test fun `a Game Boy battle resumed after a start is counted once`() {
        var m = restart()
        // No party read on a Game Boy game: the Pokemon on screen, by its PID.
        val gb = battle(enemy(19, 3, 0x3131), true, emptyList(), emptyList())
        feed(EncounterBook(), m, gb)
        m = restart()
        feed(EncounterBook(), m, overworld, gb)
        assertEquals(1, m.encounters(19, wild = true))
        val other = battle(enemy(19, 4, 0x4141), true, emptyList(), emptyList())
        feed(EncounterBook(), restart().also { m = it }, other)
        assertEquals(2, m.encounters(19, wild = true), "another Rattata is another battle")
        assertEquals(3, m.lastLevelSeen(19), "the first battle's end, recorded when the next one showed")
    }

    @Test fun `a DS battle resumed after a start is counted once, and another one ends it`() {
        var m = restart()
        fun ds(book: EncounterBook, marks: StatMarks, pid: Long?, sp: Int, lv: Int, vararg others: EncounterBook.DsFoe?) =
            book.onDs(marks, EncounterBook.Ds(pid != null || sp != 0, false, pid, sp, lv, others = others.toList()), true)
        val first = EncounterBook()
        ds(first, m, 1, 74, 12, EncounterBook.DsFoe(2, 95, 14))
        ds(first, m, 3, 66, 13, EncounterBook.DsFoe(2, 95, 14))
        assertEquals(1, m.totalEncounters(74)); assertEquals(1, m.totalEncounters(95)); assertEquals(1, m.totalEncounters(66))
        repeat(2) {
            m = restart()
            val book = EncounterBook()
            // A tick with no enemy read, then the battle as the state left it.
            book.onDs(m, EncounterBook.Ds(true, false, null, 0, 0), true)
            ds(book, m, 3, 66, 13, EncounterBook.DsFoe(2, 95, 14))
            assertEquals(1, m.totalEncounters(66), "after restart ${it + 1}")
            assertEquals(1, m.totalEncounters(95))
        }
        // Opened on another battle: counted, and the one in progress has its levels recorded.
        m = restart()
        ds(EncounterBook(), m, 9, 41, 20)
        assertEquals(1, m.totalEncounters(41))
        assertEquals(13, m.lastLevelSeen(66))
        assertEquals(14, m.lastLevelSeen(95))
    }
}
