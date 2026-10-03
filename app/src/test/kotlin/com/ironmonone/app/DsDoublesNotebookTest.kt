package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A DS double or triple battle's other opponents go in the notebook as the first does (rc34; Blake: "fix 1 ... don't
 * stop"): the DS tracker counts each new Pokemon in each enemy slot, records the level of the one it replaced and, at
 * the battle's end, each one still out (BattleHandlerGen4.lua:190, BattleHandlerGen5.lua:277, BattleHandlerBase.lua:364),
 * and records the moves of every slot (updateAllPokemonInBattle with checkEnemyPP). Until rc34 only the first
 * opponent's were.
 */
class DsDoublesNotebookTest {
    private val dir: File = Files.createTempDirectory("dsdoubles").toFile()
    private fun marks() = StatMarks(File(dir, "marks-${System.nanoTime()}.txt"))

    private fun tm(pid: Long, species: Int, level: Int, moves: List<NdsMoveInfo> = emptyList()) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, level, 40, 40, listOf(33, 0, 0, 0)))!!,
        speciesName = "#$species", info = null, abilityName = "-", itemName = "-", moves = moves,
    )

    private fun move(id: Int, name: String) = NdsMoveInfo(name, 40, 100, "NORMAL", 35, "PHY", id = id)

    private val starly = tm(0x90, 396, 10, listOf(move(33, "Tackle")))
    private val bidoof = tm(0x91, 399, 9, listOf(move(45, "Growl")))
    private val shinx = tm(0x92, 403, 11, listOf(move(43, "Leer")))
    private val partner = tm(0x70, 25, 30, listOf(move(84, "Thunder Shock")))

    private fun battle(enemies: List<NdsTrackedMon?>, allies: Int = 0, wild: Boolean = false, enemy: NdsTrackedMon? = enemies.getOrNull(allies)) =
        NdsTrackerState(1, emptyList(), located = true, inBattle = true, isWildBattle = wild, enemy = enemy,
            enemyTrainerId = if (wild) 0 else 7, battleFetched = true, areaName = "Route 202",
            enemyBattlers = enemies, enemyAllies = allies)

    @Test
    fun `every opponent slot is counted once a Pokemon, and a replaced one's level is recorded`() {
        val m = marks(); val book = EncounterBook()
        fun ds(vararg others: EncounterBook.DsFoe?) = book.onDs(m, EncounterBook.Ds(true, false, 1, 74, 12, others = others.toList()), true)
        ds(EncounterBook.DsFoe(2, 95, 14))
        assertEquals(1, m.totalEncounters(74))
        assertEquals(1, m.totalEncounters(95), "the second opponent, counted as the first is")
        ds(EncounterBook.DsFoe(2, 95, 14))
        assertEquals(1, m.totalEncounters(95), "the same Pokemon in its slot is counted once")
        ds(null)
        assertEquals(1, m.totalEncounters(95), "a tick its slot read nothing is not a new one")
        assertNull(m.lastLevelSeen(95))
        ds(EncounterBook.DsFoe(3, 41, 15))
        assertEquals(1, m.totalEncounters(41))
        assertEquals(14, m.lastLevelSeen(95), "the one it replaced, recorded as it left")
        ds(EncounterBook.DsFoe(3, 41, 15), EncounterBook.DsFoe(4, 19, 9))
        assertEquals(1, m.totalEncounters(19), "a triple battle's third")
        book.onDs(m, EncounterBook.Ds(false, false, null, 0, 0), true)
        assertEquals(12, m.lastLevelSeen(74))
        assertEquals(15, m.lastLevelSeen(41), "every one still out when the battle ends")
        assertEquals(9, m.lastLevelSeen(19))
    }

    @Test
    fun `the update names every opponent slot, never your partner's, and wild ones go on the area's frame`() {
        val doubles = EncounterBook.dsUpdate(battle(listOf(starly, bidoof)))!!
        assertEquals(396, doubles.species)
        assertEquals(listOf(EncounterBook.DsFoe(0x91, 399, 9)), doubles.others)
        val gap = EncounterBook.dsUpdate(battle(listOf(starly, null, shinx)))!!
        assertEquals(listOf(null, EncounterBook.DsFoe(0x92, 403, 11)), gap.others, "a slot that read nothing keeps its place")
        // A Black 2 multi battle reads your partner's Pokemon first among the opponent's (NdsTrackerState.enemyAllies).
        val multi = EncounterBook.dsUpdate(battle(listOf(partner, starly, bidoof), allies = 1))!!
        assertEquals(396, multi.species)
        assertEquals(listOf(EncounterBook.DsFoe(0x91, 399, 9)), multi.others)
        val m = marks()
        EncounterBook().onDs(m, EncounterBook.dsUpdate(battle(listOf(starly, bidoof), wild = true)), true)
        assertEquals(mapOf(396 to listOf(10), 399 to listOf(9)), m.dsEncountersIn("Route 202"))
        EncounterBook().onDs(m, EncounterBook.dsUpdate(battle(listOf(partner, starly, bidoof), allies = 1)), true)
        assertEquals(0, m.totalEncounters(25), "your partner's Pokemon is not an opponent")
    }

    @Test
    fun `every opponent's moves go in the notebook, your partner's do not`() {
        val m = marks()
        assertTrue(DsFoeMoves.record(m, battle(listOf(starly, bidoof))))
        assertEquals(listOf("Tackle"), m.movesSeenFor(396).map { it.name })
        assertEquals(listOf("Growl"), m.movesSeenFor(399).map { it.name }, "the second opponent's too")
        DsFoeMoves.record(m, battle(listOf(partner, starly, bidoof), allies = 1))
        assertTrue(m.movesSeenFor(25).isEmpty())
        // Play records again when any opponent's moves change, the second's included.
        val before = DsFoeMoves.key(battle(listOf(starly, bidoof)))
        assertTrue(before != DsFoeMoves.key(battle(listOf(starly, bidoof.copy(moves = listOf(move(45, "Growl"), move(33, "Tackle")))))))
        // A battle not fetched yet still records the one opponent read, as before.
        val unfetched = NdsTrackerState(1, emptyList(), located = true, inBattle = true, enemy = shinx)
        assertEquals(listOf(shinx), unfetched.opponents)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("LaunchedEffect(DsFoeMoves.key(ndsState)) {" in play)
        assertTrue("if (DsFoeMoves.record(statMarks, ndsState)) marksVersion++" in play)
    }
}
