package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/** BattleHandlerBase.lua:265: an enemy move is tracked once its PP is below base. */
class NdsUsedMovesTest {
    private fun mon(moves: List<Int>, pp: List<Int>) = Gen4.Mon(
        pid = 1, species = 542, heldItem = 0, abilityId = 0, level = 24, curHp = 50, maxHp = 72,
        atk = 1, def = 1, spe = 1, spAtk = 1, spDef = 1, moves = moves, pp = pp, ppUps = listOf(0, 1, 0, 0),
        ivs = List(6) { 0 }, shiny = false, nature = 0, isEgg = false,
    )
    private val base = mapOf(75 to 25, 522 to 20, 15 to 30, 182 to 10)   // Razor Leaf, Struggle Bug, Cut, Protect

    @Test
    fun `only moves with PP below base survive, and slots stay aligned with PP and PP Ups`() {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) })
        val e = t.usedOnly(mon(listOf(75, 522, 15, 182), listOf(25, 18, 30, 9))) { base[it] ?: 0 }
        assertEquals(listOf(522, 182, 0, 0), e.moves, "Struggle Bug and Protect were used; Razor Leaf and Cut were not")
        assertEquals(listOf(18, 9, 0, 0), e.pp)
        assertEquals(listOf(1, 0, 0, 0), e.ppUps, "Struggle Bug's PP Up travels with it")
    }

    @Test
    fun `an unknown move is kept, a fresh opponent shows nothing`() {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) })
        assertEquals(listOf(999, 0, 0, 0), t.usedOnly(mon(listOf(999, 75, 0, 0), listOf(5, 25, 0, 0))) { base[it] ?: 0 }.moves)
        assertEquals(listOf(0, 0, 0, 0), t.usedOnly(mon(listOf(75, 522, 0, 0), listOf(25, 20, 0, 0))) { base[it] ?: 0 }.moves)
    }
}
