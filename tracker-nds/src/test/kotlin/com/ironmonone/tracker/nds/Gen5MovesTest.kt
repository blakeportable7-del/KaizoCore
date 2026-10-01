package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * gen5/moves.tsv against the reference's Gen 5 values. GameConfigurator.initMoveData
 * keeps moveAttribute[gameInfo.GEN] of every per-generation table in MoveData.lua
 * (GameConfigurator.lua:87), so Tackle's {"35", "35", "35", "35", "50"} is 50 on
 * Black and White. The table used to come from a regex that read neither a
 * per-generation table nor an entry whose type spans several lines: 70 moves were
 * wrong (Tackle and Thrash power 0, Bite, Gust and Curse with no type, 21 moves at
 * PP 0), and a PP-0 move showed on the enemy card before the opponent used it
 * (parity audit, 2026-09-28). tools/extract_gen5_data.py now runs the reference's
 * own initMoveData and compares every row; these are the spot checks on this side.
 */
class Gen5MovesTest {
    private val none = NdsMemoryReader { _, _ -> ByteArray(0) }
    private val bw = NdsTracker(none, null, NdsGameMap.BW)

    private fun move(id: Int) = assertNotNull(bw.moveInfoFor(id), "move $id")
    private fun row(m: NdsMoveInfo) = listOf(m.name, m.power, m.accuracy, m.type, m.pp, m.category)

    @Test
    fun `moves that differ by generation carry their Gen 5 value`() {
        assertEquals(listOf("Tackle", 50, 100, "NORMAL", 35, "PHYSICAL"), row(move(33)))
        assertEquals(listOf("Thrash", 120, 100, "NORMAL", 10, "PHYSICAL"), row(move(37)))
        assertEquals(listOf("Bite", 60, 100, "DARK", 25, "PHYSICAL"), row(move(44)))
        assertEquals(listOf("Gust", 40, 100, "FLYING", 35, "SPECIAL"), row(move(16)))
        assertEquals(listOf("Karate Chop", 50, 100, "FIGHTING", 25, "PHYSICAL"), row(move(2)))
        assertEquals(listOf("Curse", 0, 0, "GHOST", 10, "STATUS"), row(move(174)), "Curse is Ghost from Gen 5")
        assertEquals(listOf("Vine Whip", 35, 100, "GRASS", 15, "PHYSICAL"), row(move(22)))
        assertEquals(listOf("Blizzard", 120, 70, "ICE", 5, "SPECIAL"), row(move(59)))
        assertEquals(1, move(165).pp, "Struggle")
    }

    @Test
    fun `a power the reference prints as text keeps the text`() {
        assertEquals("WT", move(67).powerText, "Low Kick")
        assertEquals(">HP", move(284).powerText, "Eruption")
        assertEquals("VAR", move(237).powerText, "Hidden Power")
        assertEquals(0, move(67).power)
        assertEquals("", move(33).powerText, "a numeric power has no text")
        assertEquals("", move(14).powerText, "a status move's --- is not text")
    }

    @Test
    fun `every Gen 5 move has a type, a category and a base PP, and every Gen 4 move a base PP`() {
        val gen5 = bw.moveTable()
        assertEquals((1..559).toList(), gen5.map { it.id })
        assertEquals(emptyList(), gen5.filter { it.pp <= 0 }.map { it.name }, "Gen 5 moves at PP 0")
        assertEquals(emptyList(), gen5.filter { it.type.isBlank() || it.category.isBlank() }.map { it.name })
        // NdsTracker.usedOnly keeps a move whose base PP it does not know; with a base PP
        // on every row that can only be an id outside the table.
        val gen4 = NdsTracker(none, null, NdsGameMap.PLATINUM).moveTable()
        assertEquals((1..467).toList(), gen4.map { it.id })
        assertEquals(emptyList(), gen4.filter { it.pp <= 0 }.map { it.name }, "Gen 4 moves at PP 0")
    }

    @Test
    fun `status moves and never-miss moves read 0, which the card draws as ---`() {
        assertTrue(move(14).power == 0 && move(14).accuracy == 0, "Swords Dance")
        assertEquals(100, move(18).accuracy, "Whirlwind hits 100 in Gen 5, it is not a never-miss move")
    }
}
