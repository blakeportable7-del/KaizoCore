package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsSpeciesInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The DS move rows (MainScreen.readMovesIntoUI): STAB in battle, the opponent's stars, the description on a tap. */
class NdsMoveRowsTest {
    private val tackle = NdsMoveInfo("Tackle", 35, 95, "NORMAL", 35, "PHYSICAL", 33)
    private val ember = NdsMoveInfo("Ember", 40, 100, "FIRE", 25, "SPECIAL", 52)
    private val table = mapOf(33 to tackle, 52 to ember)

    private fun tm(level: Int, moveLevels: List<Int> = emptyList(), moves: List<Int> = listOf(52, 0, 0, 0), pp: List<Int> = listOf(24, 0, 0, 0)) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(0x55L, 4, level, 30, 30, moves, pp = pp))!!,
        speciesName = "Charmander", info = NdsSpeciesInfo("Charmander", "FIRE", "FIRE", 309, "", ""),
        abilityName = "-", itemName = "-", moves = moves.filter { it != 0 }.map { table.getValue(it) }, moveLevels = moveLevels,
    )

    @Test
    fun `STAB colours the power in battle only`() {
        val me = tm(10)
        assertTrue(ndsMoveRow(ember, me, 25, 25, inBattle = true, ctx = null).stab)
        assertEquals(false, ndsMoveRow(ember, me, 25, 25, inBattle = false, ctx = null).stab, "Program.isInBattle")
        assertEquals(false, ndsMoveRow(tackle, me, 35, 35, inBattle = true, ctx = null).stab)
    }

    @Test
    fun `an opponent's move it may have forgotten carries a star`() {
        // Tackle seen at level 12, Ember in use now at 26; it learns at 15, 20 and 25.
        val foe = tm(26, moveLevels = listOf(5, 15, 20, 25))
        val seen = listOf(StatMarks.SeenMove(33, "Tackle", 12, 12, 12))
        val rows = enemyMovesOf(foe, seen, { table[it] }, inBattle = true)
        assertEquals(listOf("Tackle*", "Ember"), rows.map { it.name })
        assertTrue(rows.all { r -> r.name != "Ember" || r.stab }, "the opponent's STAB too")
    }

    /** rc32 audit P2 #35: MoveUtils.getMoveHeader on both cards (MoveUtils.lua:134-149, MainScreen.lua:524-526). */
    @Test
    fun `both cards head their moves the DS tracker's way, learned of total and the next level`() {
        val foe = tm(20).copy(movesLearned = 3, movesTotal = 12, nextMoveLevel = 14)
        assertEquals("Moves: 3/12 (14)", ndsMovesHeader(foe))
        assertEquals("Moves: 12/12", ndsMovesHeader(foe.copy(movesLearned = 12, nextMoveLevel = null)), "every one learned")
        assertEquals("Moves", ndsMovesHeader(tm(20)), "a species the table lacks")
    }

    @Test
    fun `your card and the opponent's both take that header`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertEquals(2, Regex("header = ndsMovesHeader\\(").findAll(panel).count(), "your card and the opponent's")
        assertTrue("header = \"Moves\"," !in panel, "the opponent's bare header")
    }

    @Test
    fun `a tap shows the move's description, and an empty row shows nothing`() {
        val row = ndsMoveRow(tackle, tm(10), 35, 35, inBattle = false, ctx = null)
        assertEquals("Inflicts regular damage and makes contact.", ndsMoveDescription(row, gen = 4))
        assertNull(ndsMoveDescription(row.copy(id = 0), gen = 4))
    }

    private fun holder(item: Int = 0, friendship: Int = 0, hp: Int = 30) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(0x66L, 493, 50, hp, 30, listOf(237, 449, 67, 216), heldItem = item, friendship = friendship))!!,
        speciesName = "Arceus", info = NdsSpeciesInfo("Arceus", "NORMAL", "NORMAL", 720, "", ""),
        abilityName = "-", itemName = "-", moves = emptyList(),
    )
    private val hiddenPower = NdsMoveInfo("Hidden Power", 0, 100, "UNKNOWN", 15, "SPECIAL", 237, "VAR")
    private val judgment = NdsMoveInfo("Judgment", 100, 100, "NORMAL", 10, "SPECIAL", 449)
    private val lowKick = NdsMoveInfo("Low Kick", 0, 100, "FIGHTING", 20, "PHYSICAL", 67, "WT")
    private val returnMove = NdsMoveInfo("Return", 0, 100, "NORMAL", 20, "PHYSICAL", 216, ">FR")

    @Test
    fun `your Hidden Power takes the run's type and carries the arrows, the opponent's does not`() {
        val own = ndsMoveRow(hiddenPower, holder(), 15, 15, inBattle = false, ctx = null, hiddenPowerType = "FIRE")
        assertEquals(pcTypeColorByName("FIRE"), own.color)
        assertEquals("Hidden Power", own.name)
        assertTrue(own.hiddenPowerArrows)
        assertEquals("Fire", ndsMoveRow(hiddenPower, holder(), 15, 15, false, null, hiddenPowerType = "FIRE", hiddenPowerJustChanged = true).name,
            "the type's name for a moment after the arrows")
        val theirs = ndsMoveRow(hiddenPower, holder(), 15, 15, inBattle = false, ctx = null, own = false, hiddenPowerType = "FIRE")
        assertEquals(pcTypeColorByName("UNKNOWN"), theirs.color)
        assertEquals(false, theirs.hiddenPowerArrows)
    }

    @Test
    fun `your Judgment is coloured by the plate it holds`() {
        assertEquals(pcTypeColorByName("DRAGON"), ndsMoveRow(judgment, holder(item = 311), 10, 10, false, null).color, "Draco Plate")
        assertEquals(pcTypeColorByName("NORMAL"), ndsMoveRow(judgment, holder(), 10, 10, false, null).color)
        assertEquals(pcTypeColorByName("NORMAL"), ndsMoveRow(judgment, holder(item = 311), 10, 10, false, null, own = false).color)
    }

    @Test
    fun `variable powers are worked out with the opponent, Return by friendship`() {
        val gastly = NdsMoveContext(listOf("GHOST", "POISON"), 0, "BUG", targetSpecies = 92)
        assertEquals("20", ndsMoveRow(lowKick, holder(), 20, 20, inBattle = true, ctx = gastly, calcVariable = true).powerText, "Gastly weighs 0.1 kg")
        assertEquals("WT", ndsMoveRow(lowKick, holder(), 20, 20, inBattle = false, ctx = null, calcVariable = true).powerText, "no opponent: the table's text")
        assertEquals("WT", ndsMoveRow(lowKick, holder(), 20, 20, inBattle = true, ctx = gastly, calcVariable = false).powerText, "the setting off")
        assertEquals("102", ndsMoveRow(returnMove, holder(friendship = 255), 20, 20, false, null, calcVariable = false).powerText, "Return is outside the setting")
        assertEquals(">FR", ndsMoveRow(returnMove, holder(friendship = 200), 20, 20, false, null).powerText)
    }

    @Test
    fun `the arrows step the run's one type, wrapping`() {
        val dir = java.nio.file.Files.createTempDirectory("dshp").toFile()
        try {
            val marks = StatMarks(File(dir, "marks.txt"))
            assertEquals("BUG", marks.dsHiddenPowerType())
            assertEquals("WATER", marks.stepDsHiddenPower(forward = false))
            assertEquals("BUG", marks.stepDsHiddenPower(forward = true))
            assertEquals("DARK", marks.stepDsHiddenPower(forward = true))
            assertEquals("DARK", StatMarks(File(dir, "marks.txt")).dsHiddenPowerType(), "kept with the run")
            marks.clear()
            assertEquals("BUG", StatMarks(File(dir, "marks.txt")).dsHiddenPowerType(), "a new run starts at Bug")
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `both cards open the description`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertEquals(2, Regex("ndsMoveDescription\\(r, gen\\)").findAll(panel).count(), "each card's move tap shows the description")
    }

    @Test
    fun `the Coverage Calc starts from the run's Hidden Power type and leaves out moves with no power`() {
        val seismicToss = NdsMoveInfo("Seismic Toss", 0, 100, "FIGHTING", 20, "PHYSICAL", 69)
        val growl = NdsMoveInfo("Growl", 0, 100, "NORMAL", 40, "STATUS", 45)
        assertEquals(listOf("ICE", "FIGHTING"), ndsCoverageSeed(listOf(hiddenPower, seismicToss, growl, lowKick), "ICE"))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("seed = ndsCoverageSeed(" in play, "the DS Coverage Calc no longer seeds as the reference does")
    }

    @Test
    fun `both DS panels step the Hidden Power type`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex("onStepHiddenPower = \\{ f -> statMarks\\.stepDsHiddenPower\\(f\\)").findAll(play).count())
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertTrue("onHiddenPower = onHiddenPower," in panel, "the party card no longer hands the arrows to its moves")
    }
}
