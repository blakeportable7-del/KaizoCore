package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * PokemonData.calcCatchRate worked by hand for a catch rate of 45 at full
 * HP, no status: raw 15 for a Poke Ball gives 5%, 30 for an Ultra Ball 12%,
 * and the Master Ball's 382 is a sure catch. Then the screen over a Ruby
 * battle (no bag encryption, fixed save blocks): the enemy is species 1 at
 * 20/20, the bag holds five Poke Balls, so that row leads and the rest
 * follow by rate.
 */
class CatchRatesTest {
    private val m = GameMap.RUBY_U

    @Test
    fun `the formula matches the reference's arithmetic`() {
        val t = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, m)
        assertEquals(5, t.calcCatchRate(45, 20, 20, 5, 0, 4, false, 0, false, 0))
        assertEquals(12, t.calcCatchRate(45, 20, 20, 5, 0, 2, false, 0, false, 0))
        assertEquals(100, t.calcCatchRate(45, 20, 20, 5, 0, 1, false, 0, false, 0))
        assertEquals(0, t.calcCatchRate(45, 20, 0, 5, 0, 4, false, 0, false, 0))
        // Sleep doubles the raw rate: 30 -> 12%.
        assertEquals(12, t.calcCatchRate(45, 20, 20, 5, 0x3, 4, false, 0, false, 0))
        // Toxic gives nothing on Ruby.
        assertEquals(5, t.calcCatchRate(45, 20, 20, 5, 0x80, 4, false, 0, false, 0))
    }

    @Test
    fun `the screen reads the enemy, the bag and sorts owned balls first`() {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> ram[a + i] = v.toByte() } }
        put(m.baseStats + 1 * 28, 45, 49, 49, 45, 65, 65, 12, 3, 45)   // species 1: Bulbasaur stats, Grass/Poison, catch rate 45
        put(m.battlersCount, 2); put(m.battleMainFunc, 0x25, 0x23, 0x01, 0x08)   // gBattleMainFunc = HandleTurnActionSelectionState
        put(m.battleMons, 1, 0)                          // allied species 1, so the battle is real
        val e = m.battleMons + m.battleMonSize
        put(e, 1, 0); put(e + 0x28, 20, 0); put(e + 0x2A, 5); put(e + 0x2C, 20, 0)
        put(m.saveBlock1Fixed + m.bagBallsOffset, 4, 0, 5, 0)   // five Poke Balls
        val t = GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, m)
        repeat(2) { t.read() }
        val d = assertNotNull(t.catchRates())
        assertEquals(100, d.hpPercent); assertEquals("", d.status)
        assertEquals(4, d.rows[0].ballId); assertEquals(5, d.rows[0].quantity); assertEquals(5, d.rows[0].rate)
        assertEquals(1, d.rows[1].ballId); assertEquals(100, d.rows[1].rate)
        assertEquals(12, d.rows.size)
    }

    @Test
    fun `Toxic adds nothing on Ruby, Sapphire and Emerald, half again on FireRed and LeafGreen`() {
        // PokemonData.lua:672-676: statusBonus is 1 when GameSettings.game is 1 or 2, else Toxic's 1.5.
        // Catch rate 45 at full HP in a Poke Ball is raw 15 (5%); 1.5x makes it 22 (9%).
        fun toxic(map: GameMap) = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, map)
            .calcCatchRate(45, 20, 20, 5, 0x80, 4, false, 0, false, 0)
        assertEquals(5, toxic(GameMap.RUBY_U)); assertEquals(5, toxic(GameMap.SAPPHIRE_U))
        assertEquals(5, toxic(GameMap.EMERALD_U))
        assertEquals(9, toxic(GameMap.FIRERED_U_V10)); assertEquals(9, toxic(GameMap.LEAFGREEN_U))
        // The Nat. Dex builds count as their base game (IRONMON_ROMS; skipped without the dumps).
        for ((file, want) in listOf("emerald-natdex-121.gba" to 5, "firered-natdex-121.gba" to 9)) {
            val f = System.getenv("IRONMON_ROMS")?.let { java.io.File(it, file) }?.takeIf { it.isFile } ?: continue
            val rom = f.readBytes()
            val mem = MemoryReader { a, n ->
                val o = a - 0x08000000L
                ByteArray(n) { i -> if (o >= 0 && o + i < rom.size) rom[(o + i).toInt()] else 0 }
            }
            assertEquals(want, GbaTracker(mem, GameMap.resolve(mem)).calcCatchRate(45, 20, 20, 5, 0x80, 4, false, 0, false, 0), file)
        }
    }
}
