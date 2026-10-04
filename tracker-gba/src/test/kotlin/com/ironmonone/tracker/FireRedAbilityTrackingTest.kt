package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ability tracking on FireRed 1.1 (and so Faster FireRed, which leaves the
 * battle scripts and the header untouched), against synthetic memory.
 * Blake, 2026-09-27: abilities were not being tracked on FireRed. The PC
 * tracker records an ability two ways: the enemy's when a battle script shows
 * it, and the player's own battlers' on every battle update.
 */
class FireRedAbilityTrackingTest {
    private class FakeMem {
        val bytes = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { bytes[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun reader() = MemoryReader { address, length -> ByteArray(length) { bytes[address + it] ?: 0 } }
    }

    private val m = GameMap.FIRERED_U_V11

    private fun mon(mem: FakeMem, battler: Int, species: Int, liveAbility: Int, slot: Int = 0) {
        val base = m.battleMons + battler * m.battleMonSize.toLong()
        mem.put16(base, species)
        mem.put32(base + 0x14, if (slot == 1) 0x80000000L else 0L)
        mem.put8(base + 0x20, liveAbility)
    }

    private fun baseAbilities(mem: FakeMem, species: Int, a1: Int, a2: Int) {
        val at = m.baseStats + species.toLong() * m.baseStatsStride
        for (i in 0 until 6) mem.put8(at + i, 50)   // a real stat line, or baseStats() refuses it
        mem.put8(at + 22, a1); mem.put8(at + 23, a2)
    }

    @Test
    fun `a FireRed 1_1 ability script reveals the battler's ability`() {
        val mem = FakeMem()
        mem.put8(m.battlersCount, 2)
        mem.put32(m.scriptCurrInstr, 0x081D9317)   // BATTLER trigger for ability 36 in FR 1.1's table
        mem.put8(m.scriptingBattler, 1)
        mon(mem, 0, species = 25, liveAbility = 9)
        // A Trace Kadabra whose live byte already holds the traced Static, as the game leaves it (rc32 audit P2 #132).
        baseAbilities(mem, 64, a1 = 36, a2 = 0)
        mon(mem, 1, species = 64, liveAbility = 9)
        mem.put8(m.battleTextBuff1 + 2, 0)
        val t = GbaTracker(mem.reader(), m)
        // Trace in a single battle reveals the other side, as in the reference.
        assertEquals(25, t.readAbilityTrigger()?.first)
    }

    @Test
    fun `a message caught between reads is not lost`() {
        val mem = FakeMem()
        mem.put8(m.battlersCount, 2)
        mem.put8(m.scriptingBattler, 1)
        mon(mem, 0, species = 25, liveAbility = 9)
        mon(mem, 1, species = 64, liveAbility = 43)
        val t = GbaTracker(mem.reader(), m)
        mem.put32(m.scriptCurrInstr, 0x081D78FF)   // BATTLER trigger, ability 43, on screen now
        t.pollAbilityTrigger(); t.pollAbilityTrigger()
        mem.put32(m.scriptCurrInstr, 0x08123456)   // and gone before the next full read
        // live = true: drained by a read during an active battle.
        val drained = t.javaClass.getDeclaredMethod("drainReveals", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(t, true) as List<*>
        assertEquals(1, drained.size, "seen once, recorded once")
        assertEquals(64, (drained[0] as Pair<*, *>).first)
    }

    @Test
    fun `your own battler's ability comes from its slot, not the live byte`() {
        val mem = FakeMem()
        mem.put8(m.battlersCount, 2)
        baseAbilities(mem, 25, a1 = 9, a2 = 31)
        // Slot 1 (the second ability), while the live byte says something else
        // (as after Trace or Skill Swap).
        mon(mem, 0, species = 25, liveAbility = 36, slot = 1)
        val own = GbaTracker(mem.reader(), m).readOwnAbilities()
        assertEquals(listOf(25), own.map { it.first })
        assertTrue(own[0].second.contains("31"), "the name of ability 31 (no name table in fake memory): ${own[0].second}")
    }
}
