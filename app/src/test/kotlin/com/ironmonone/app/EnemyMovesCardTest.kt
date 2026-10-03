package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.EnemyMovesSeen
import com.ironmonone.tracker.MoveRow
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The opponent card's moves, and what the run keeps of them (rc33 audit P1 #56 and #72).
 */
class EnemyMovesCardTest {
    private val dir = Files.createTempDirectory("enemymoves").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun row(id: Int) = MoveRow(id, "M$id", 10, null, 40, 100, 0, "PHY")
    private val leechLife = 141; private val supersonic = 48; private val bite = 44; private val wingAttack = 17; private val astonish = 310

    private fun zubat(usedThisBattle: List<Int>) = EnemyInfo(
        species = 41, speciesName = "ZUBAT", level = 20, curHp = 40, maxHp = 50, type1 = 3, type2 = 2,
        base = null, movesSeen = usedThisBattle.map { "M$it" }, moveRows = usedThisBattle.map(::row),
    )

    /** The audit's run: Leech Life then Supersonic seen earlier, then this battle's four, front-inserted (Tracker.TrackMove). */
    private val runWide = listOf(astonish, wingAttack, bite, supersonic, leechLife).map { StatMarks.SeenMove(it, "M$it", 20, 20) }

    @Test
    fun `an opponent that has used all four of its moves shows those four`() {
        // The run-wide order put Supersonic, which this Zubat does not have, in the first four and Leech Life, which it
        // used, out of them. The PC tracker shows the four seen this battle (Tracker.BattleNotes.FourMovesIfAllKnown).
        val e = zubat(listOf(leechLife, bite, wingAttack, astonish))
        val (rows, seen) = EnemyView.moveRows(e, runWide, ::row, actual = false, hidden = false)
        assertEquals(listOf(leechLife, bite, wingAttack, astonish), rows.map { it.id })
        assertEquals(5, seen, "the header still says more have been seen")
        assertTrue(EnemyView.fourKnown(e))
        assertEquals(listOf(leechLife, bite, wingAttack, astonish), EnemyView.moveRows(e, runWide, ::row, actual = false, hidden = true).first.map { it.id })
    }

    @Test
    fun `with fewer than four used this battle the run's sightings fill the card`() {
        val e = zubat(listOf(leechLife, bite, wingAttack))
        assertFalse(EnemyView.fourKnown(e))
        assertEquals(listOf(astonish, wingAttack, bite, supersonic), EnemyView.moveRows(e, runWide, ::row, actual = false, hidden = false).first.map { it.id })
    }

    @Test
    fun `none of the four is starred, and the card says so where it is drawn`() {
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText()
        assertTrue("val starred = if (actual || EnemyView.fourKnown(e)) emptySet() else" in src)
        assertTrue("fourKnown(e) -> thisBattle.take(4)" in src)
    }

    @Test
    fun `every opposing Pokemon's moves this battle are kept for the run`() {
        // rc33 audit P1 #72: the doubles partner's too, each under its own species.
        val marks = StatMarks(File(dir, "marks.txt"))
        val battle = listOf(EnemyMovesSeen(19, 10, listOf(33 to "TACKLE")), EnemyMovesSeen(37, 11, listOf(52 to "EMBER")))
        assertTrue(marks.addBattleMoves(battle))
        assertEquals(listOf(33), marks.movesSeenFor(19).map { it.id })
        assertEquals(listOf(52), marks.movesSeenFor(37).map { it.id })
        assertEquals(11, marks.movesSeenFor(37).single().lastLv)
        assertFalse(marks.addBattleMoves(battle), "the same moves again change nothing")
        assertFalse(marks.addBattleMoves(null))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("LaunchedEffect(trackerState?.enemyMovesThisBattle) {" in play)
        assertTrue("statMarks.addBattleMoves(trackerState?.enemyMovesThisBattle)" in play)
        assertFalse("LaunchedEffect(trackerState?.enemy?.movesSeen)" in play, "the left foe's card alone missed the partner")
    }
}
