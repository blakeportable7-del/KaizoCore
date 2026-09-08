package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * BattleDetailsScreen.updateData over a hand-built battle: a cave, temporary
 * rain with 3 turns left, turn 5, the allied mon confused and under Leech
 * Seed from battler 1, the enemy side behind Reflect for 4 turns, the enemy
 * mon Encored into move 33, and Pay Day money on the field.
 */
class BattleDetailsTest {
    private val m = GameMap.EMERALD_U
    private fun mem(inBattle: Boolean): MemoryReader {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> ram[a + i] = v.toByte() } }
        // party of one so the in-battle test has a mon; flags: in battle (bit 2 of gBattleTypeFlags per read())
        put(m.battleTerrain, 7)
        put(m.weather, 0x01)
        put(m.wishFutureKnock + 0x28, 3)
        put(m.battleResults + 0x13, 4)
        put(m.paydayMoney, 0x2C, 0x01)                      // 300
        put(m.battleMons + 0x50, 0x01)                      // allied: confusion
        put(m.battleMons + 0x00, 1, 0)                      // allied species 1
        put(m.battleMons + m.battleMonSize, 4, 0)           // enemy species 4
        put(m.statuses3, 0x05)                              // leech seed, source bits = 1
        put(m.sideStatuses + 2, 0x01)                       // enemy side: reflect
        put(m.sideTimers + 0xC, 4)                          // reflect timer 4
        put(m.disableStructs + 0x1C + 6, 33, 0)             // enemy encored move 33
        if (inBattle) { put(m.battlersCount, 2); put(m.battleMainFunc, 0x75, 0xBE, 0x03, 0x08) }   // gBattleMainFunc = HandleTurnActionSelectionState
        return MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }
    }

    @Test
    fun `the field, the sides and the battlers read as the reference reads them`() {
        val t = GbaTracker(mem(true), m)
        repeat(2) { t.read() }   // the status machine needs a poll to open and one to say the data is ready
        val d = assertNotNull(t.battleDetails())
        assertEquals("Cave", d.terrain); assertEquals("Rain", d.weather); assertEquals(5, d.turn)
        assertEquals(listOf("Weather turns Left: 3", "${t.moveName(6)} (300)"), d.field.map { it.text })
        assertEquals(listOf("Confused (1- 4 Turns)", "${t.moveName(73)} (${t.speciesName(4)})"), d.mons[0].map { it.text })
        assertEquals(listOf("${t.moveName(115)}: 4 Turns Left"), d.sides[1].map { it.text })
        assertEquals(listOf("${t.moveName(227)} (${t.moveName(33)})"), d.mons[1].map { it.text })
        assertEquals("Confused (1- 4 Turns)", d.summary(0))
        assertEquals("${t.moveName(227)} (${t.moveName(33)})", d.summary(1))
    }

    @Test
    fun `outside a battle there is nothing to read`() {
        val t = GbaTracker(mem(false), m); repeat(2) { t.read() }
        assertNull(t.battleDetails())
    }
}
