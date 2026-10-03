package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.EnemyPartyMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Encounter counts and last-seen levels, by the reference's rules (EncounterBook).
 * They used to live in the Play screen's memory as one combined count per species,
 * counted whenever the species on screen changed, and were lost on a tab switch.
 */
class EncounterBookTest {
    private val dir: File = Files.createTempDirectory("encbook").toFile()
    private fun marks() = StatMarks(File(dir, "marks.txt"))

    private fun enemy(species: Int, level: Int, pid: Long = 0x1234, ghost: Boolean = false) = EnemyInfo(
        species = species, speciesName = "#$species", level = level, curHp = 10, maxHp = 10, type1 = 0, type2 = 0,
        base = null, movesSeen = emptyList(), pid = pid, isGhost = ghost,
    )

    private fun battle(
        on: EnemyInfo?, wild: Boolean, party: List<EnemyPartyMon>, field: List<Int>,
    ) = TrackerState(
        partyCount = 1, party = emptyList(), inBattle = true, isWildBattle = wild, enemy = on,
        enemyParty = party, enemyOnField = field, isGhostBattle = on?.isGhost == true,
    )

    private val overworld = TrackerState(partyCount = 1, party = emptyList(), inBattle = false, isWildBattle = false)

    private fun feed(book: EncounterBook, m: StatMarks, vararg states: TrackerState, save: Boolean = true) =
        states.forEach { book.onGba(m, EncounterBook.gbaUpdate(it), save) }

    @Test fun `a trainer's two of one species are two encounters`() {
        // Gen 3 trainer Pokemon of one species share a personality value, so only the
        // party slot (gBattlerPartyIndexes) tells them apart, as seenAlready does.
        val m = marks(); val book = EncounterBook()
        val party = listOf(EnemyPartyMon(0, 41, 10, true), EnemyPartyMon(1, 41, 12, true))
        feed(book, m,
            battle(enemy(41, 10, pid = 0x9900), false, party, listOf(0)),
            battle(enemy(41, 12, pid = 0x9900), false, party, listOf(1)),
            overworld)
        assertEquals(2, m.encounters(41, wild = false))
        assertEquals(0, m.encounters(41, wild = true))
    }

    @Test fun `a Pokemon switched out and back in is counted once per battle`() {
        val m = marks(); val book = EncounterBook()
        val party = listOf(EnemyPartyMon(0, 74, 12, true), EnemyPartyMon(1, 95, 14, true))
        feed(book, m,
            battle(enemy(74, 12, 1), false, party, listOf(0)),
            battle(enemy(95, 14, 2), false, party, listOf(1)),
            battle(enemy(74, 12, 1), false, party, listOf(0)),
            overworld)
        assertEquals(1, m.encounters(74, false))
        assertEquals(1, m.encounters(95, false))
        // A second battle counts again.
        feed(book, m, battle(enemy(74, 13, 1), false, party, listOf(0)), overworld)
        assertEquals(2, m.encounters(74, false))
    }

    @Test fun `wild and trainer counts are kept apart`() {
        val m = marks(); val book = EncounterBook()
        feed(book, m,
            battle(enemy(16, 3), true, listOf(EnemyPartyMon(0, 16, 3, true)), listOf(0)), overworld,
            battle(enemy(16, 5), true, listOf(EnemyPartyMon(0, 16, 5, true)), listOf(0)), overworld,
            battle(enemy(16, 9), false, listOf(EnemyPartyMon(0, 16, 9, true)), listOf(0)), overworld)
        assertEquals(2, m.encounters(16, wild = true))
        assertEquals(1, m.encounters(16, wild = false))
        assertEquals(3, m.totalEncounters(16))
    }

    @Test fun `last level is recorded for the whole enemy party when the battle ends`() {
        val m = marks(); val book = EncounterBook()
        m.recordLastLevels(listOf(74 to 8))
        val party = listOf(EnemyPartyMon(0, 74, 12, true), EnemyPartyMon(1, 95, 14, true), EnemyPartyMon(2, 66, 200, true))
        feed(book, m, battle(enemy(74, 12), false, party, listOf(0)))
        // Mid-battle "Last seen" is still the level from before this battle.
        assertEquals(8, m.lastLevelSeen(74))
        feed(book, m, overworld)
        assertEquals(12, m.lastLevelSeen(74))
        // Onix never came out; the reference records the whole team (Program.GameData.EnemyTeam).
        assertEquals(14, m.lastLevelSeen(95))
        // Outside 1-100 is not a level (Tracker.recordLastLevelsSeen).
        assertNull(m.lastLevelSeen(66))
    }

    @Test fun `a ghost is not counted, but its real party gets its level`() {
        val m = marks(); val book = EncounterBook()
        feed(book, m,
            battle(enemy(413, 30, ghost = true), true, listOf(EnemyPartyMon(0, 105, 30, true)), listOf(0)),
            overworld)
        assertEquals(0, m.totalEncounters(413))
        assertEquals(0, m.totalEncounters(105))
        assertEquals(30, m.lastLevelSeen(105))
    }

    @Test fun `a stale slot at battle start is not counted twice`() {
        val m = marks(); val book = EncounterBook()
        val party = listOf(EnemyPartyMon(0, 19, 4, true), EnemyPartyMon(1, 16, 4, true))
        feed(book, m,
            // gBattlerPartyIndexes still says slot 1 (last battle's) while slot 0 is out.
            battle(enemy(19, 4), false, party, listOf(1)),
            battle(enemy(19, 4), false, party, listOf(0)),
            battle(enemy(19, 4), false, party, listOf(0)),
            overworld)
        assertEquals(1, m.encounters(19, false))
        assertEquals(0, m.encounters(16, false))
    }

    @Test fun `counts and levels outlast the screen and die with the run`() {
        val book = EncounterBook()
        val first = marks()
        feed(book, first, battle(enemy(27, 7), true, listOf(EnemyPartyMon(0, 27, 7, true)), listOf(0)))
        // The Play screen goes away mid-battle and comes back: a new StatMarks, the same book.
        val second = marks()
        assertEquals(1, second.encounters(27, true))
        feed(book, second, battle(enemy(27, 7), true, listOf(EnemyPartyMon(0, 27, 7, true)), listOf(0)), overworld)
        assertEquals(1, second.encounters(27, true))
        assertEquals(7, marks().lastLevelSeen(27))
        second.clear()
        assertEquals(0, marks().totalEncounters(27))
        assertNull(marks().lastLevelSeen(27))
    }

    @Test fun `a staged Demo battle writes nothing to disk`() {
        val m = marks(); val book = EncounterBook()
        feed(book, m, battle(enemy(74, 20), true, listOf(EnemyPartyMon(0, 74, 20, true)), listOf(0)), overworld, save = false)
        assertEquals(1, m.encounters(74, true))
        assertEquals(0, marks().encounters(74, true))
        assertNull(marks().lastLevelSeen(74))
    }

    @Test fun `Game Boy games count the Pokemon on screen and record what was seen`() {
        val m = marks(); val book = EncounterBook()
        feed(book, m,
            battle(enemy(19, 3, pid = 0), true, emptyList(), emptyList()),
            battle(enemy(19, 3, pid = 0), true, emptyList(), emptyList()),
            overworld)
        assertEquals(1, m.encounters(19, true))
        assertEquals(3, m.lastLevelSeen(19))
    }

    @Test fun `DS counts every new active enemy and records the one it replaced`() {
        val m = marks(); val book = EncounterBook()
        fun ds(pid: Long, sp: Int, lv: Int) = book.onDs(m, EncounterBook.Ds(true, false, pid, sp, lv), true)
        ds(1, 74, 12); ds(1, 74, 12)
        assertEquals(1, m.totalEncounters(74))
        assertNull(m.lastLevelSeen(74))
        ds(2, 95, 14)
        // BattleHandlerGen4.lua:190: the replaced one's level is recorded as it leaves.
        assertEquals(12, m.lastLevelSeen(74))
        ds(1, 74, 12)
        // Back in: counted again (logNewEnemyPokemonInBattle on every new active PID).
        assertEquals(2, m.totalEncounters(74))
        assertEquals(14, m.lastLevelSeen(95))
        // A tick with no enemy read is not the end of the battle.
        book.onDs(m, EncounterBook.Ds(true, false, null, 0, 0), true)
        book.onDs(m, EncounterBook.Ds(false, false, null, 0, 0), true)
        assertEquals(2, m.totalEncounters(74))
        assertEquals(12, m.lastLevelSeen(74))
    }

    @Test fun `no state yet is not the end of a battle`() {
        // rc33 audit P1 #15, #30: Play starts every visit with no state; read as "not in battle" it ended the battle the
        // player came back to, filed its levels, and counted it again.
        val m = marks(); val book = EncounterBook()
        val wild = battle(enemy(27, 7), true, listOf(EnemyPartyMon(0, 27, 7, true)), listOf(0))
        feed(book, m, wild)
        kotlin.test.assertNull(EncounterBook.gbaUpdate(null))
        kotlin.test.assertFalse(book.onGba(m, EncounterBook.gbaUpdate(null), true))
        assertNull(marks().lastLevelSeen(27), "no levels filed by a missing read")
        feed(book, m, wild, overworld)
        assertEquals(1, m.encounters(27, true), "the same battle, counted once")
        assertEquals(7, marks().lastLevelSeen(27))
        kotlin.test.assertNull(EncounterBook.dsUpdate(null))
        kotlin.test.assertFalse(book.onDs(m, EncounterBook.dsUpdate(null), true))
    }

    @Test fun `Play keeps one book per run, so a new run starts clean`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertTrue("EncounterBook.of(if (session.isRun) \"run/\" + store.lastSeedText() else session.id)" in play)
        kotlin.test.assertTrue("remember(session.id, gameKeyForRom) { EncounterBook.of(" in play)
    }

    @Test fun `a DS battle not fetched is counted nowhere, and a fetched wild one goes on its area's frame`() {
        // rc33 audit P1 #80: Platinum's catching demonstration is never fetched; it was counted in Total seen and put on
        // Route 202's encounter frame.
        val m = marks(); val book = EncounterBook()
        fun state(fetched: Boolean, trainer: Int = 0, pid: Long = 0x202L) = com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 1, party = emptyList(), located = true, inBattle = true, isWildBattle = trainer == 0, areaName = "Route 202",
            enemyTrainerId = trainer, battleFetched = fetched,
            enemy = com.ironmonone.tracker.nds.NdsTrackedMon(com.ironmonone.tracker.nds.Gen4.Mon(
                pid = pid, species = 399, heldItem = 0, abilityId = 0, level = 2, curHp = 10, maxHp = 10, atk = 5, def = 5, spe = 5,
                spAtk = 5, spDef = 5, moves = List(4) { 0 }, pp = List(4) { 0 }, ppUps = List(4) { 0 }, ivs = List(6) { 0 }, shiny = false,
                nature = 0, isEgg = false, otId = 0, otSid = 0), "BIDOOF", null, "-", "-", emptyList()),
        )
        book.onDs(m, EncounterBook.dsUpdate(state(fetched = false)), true)
        assertEquals(0, m.totalEncounters(399))
        assertEquals(emptyMap(), m.dsEncountersIn("Route 202"))
        book.onDs(m, EncounterBook.dsUpdate(state(fetched = true, pid = 0x303L)), true)
        assertEquals(1, m.totalEncounters(399))
        assertEquals(mapOf(399 to listOf(2)), m.dsEncountersIn("Route 202"))
        // A trainer's Pokemon is counted, never put on the route's frame.
        val other = EncounterBook()
        other.onDs(m, EncounterBook.dsUpdate(state(fetched = true, trainer = 247, pid = 0x404L).copy(areaName = "Route 203")), true)
        assertEquals(emptyMap(), m.dsEncountersIn("Route 203"))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertFalse("statMarks.seeDsEncounter(" in play, "the frame is EncounterBook's now, behind the fetch")
    }
}
