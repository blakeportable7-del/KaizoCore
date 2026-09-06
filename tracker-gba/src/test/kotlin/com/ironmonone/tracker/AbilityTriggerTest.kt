package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ability-activation engine, driven against synthetic battle memory.
 *
 * The mechanism is the reference's Battle.checkAbilitiesToTrack: the current
 * battle-script instruction pointer is matched against the per-game ability
 * script table, and the trigger kind names which battler's ability was just
 * shown on screen. These tests build a fake Emerald battle and step through
 * the cases that matter.
 */
class AbilityTriggerTest {

    /** Synthetic memory: a map of address ranges served byte-wise. */
    private class FakeMem {
        val bytes = HashMap<Long, Byte>()
        fun put8(addr: Long, v: Int) { bytes[addr] = v.toByte() }
        fun put16(addr: Long, v: Int) { put8(addr, v and 0xFF); put8(addr + 1, (v shr 8) and 0xFF) }
        fun put32(addr: Long, v: Long) {
            for (i in 0 until 4) put8(addr + i, ((v shr (i * 8)) and 0xFF).toInt())
        }
        fun reader() = MemoryReader { address, length ->
            ByteArray(length) { bytes[address + it] ?: 0 }
        }
    }

    private val m = GameMap.EMERALD_U

    private fun battle(mem: FakeMem, scriptAddr: Long, battler: Int, attacker: Int, target: Int) {
        mem.put8(m.battlersCount, 2)
        mem.put32(m.scriptCurrInstr, scriptAddr)
        mem.put8(m.scriptingBattler, battler)
        mem.put8(m.battlerAttacker, attacker)
        mem.put8(m.battlerTarget, target)
    }

    private fun mon(mem: FakeMem, battler: Int, species: Int, ability: Int) {
        val base = m.battleMons + battler * 0x58L
        mem.put16(base, species)
        mem.put8(base + 0x20, ability)
    }

    @Test
    fun `Intimidate on the enemy reveals the enemy's ability`() {
        val mem = FakeMem()
        // 82DB50A = Emerald's IntimidateActivationAnimLoop, a BATTLER trigger.
        battle(mem, 0x082DB50A, battler = 1, attacker = 0, target = 0)
        mon(mem, 0, species = 25, ability = 9)    // player: Pikachu/Static
        mon(mem, 1, species = 59, ability = 22)   // enemy: Arcanine/Intimidate
        val t = GbaTracker(mem.reader(), m)
        // The fake memory has no ROM name table, so the name falls
        // back to the id form - the mechanism is what is under test.
        assertEquals(59 to "#22", t.readAbilityTrigger())
    }

    @Test
    fun `Trace reveals the OTHER side's ability, not Trace itself`() {
        val mem = FakeMem()
        // 82DB458 = TraceActivates, BATTLER trigger, ability 36.
        battle(mem, 0x082DB458, battler = 0, attacker = 0, target = 1)
        mon(mem, 0, species = 65, ability = 36)   // player: Alakazam/Trace
        mon(mem, 1, species = 130, ability = 22)  // enemy: Gyarados/Intimidate
        val t = GbaTracker(mem.reader(), m)
        // The information Trace shows on screen is the TRACED ability.
        assertEquals(130 to "#22", t.readAbilityTrigger())
    }

    @Test
    fun `a script address that is not an ability reveal returns nothing`() {
        val mem = FakeMem()
        battle(mem, 0x08123456, battler = 1, attacker = 0, target = 0)
        mon(mem, 0, 25, 9); mon(mem, 1, 59, 22)
        val t = GbaTracker(mem.reader(), m)
        assertEquals(null, t.readAbilityTrigger())
    }

    @Test
    fun `an ability script whose battler lacks the ability reveals nothing`() {
        val mem = FakeMem()
        // Intimidate script, but the scripting battler has Static: the message
        // on screen cannot belong to this battler's ability.
        battle(mem, 0x082DB50A, battler = 0, attacker = 0, target = 1)
        mon(mem, 0, 25, 9); mon(mem, 1, 59, 22)
        val t = GbaTracker(mem.reader(), m)
        assertEquals(null, t.readAbilityTrigger())
    }

    @Test
    fun `stat stages decode in HP ATK DEF SPE SPA SPD order`() {
        val mem = FakeMem()
        val base = m.battleMons + 0x58L  // enemy battler 1
        // HP 6, ATK 8 (+2), DEF 5 (-1), SPE 6, SPA 6, SPD 6, ACC 6, EVA 6
        for ((i, v) in listOf(6, 8, 5, 6, 6, 6, 6, 6).withIndex()) {
            mem.put8(base + 0x18 + i, v)
        }
        val t = GbaTracker(mem.reader(), m)
        val st = t.readStatStages(1)
        assertEquals(8, st["ATK"]); assertEquals(5, st["DEF"]); assertEquals(6, st["SPE"])
    }

    @Test
    fun `an HP stage of zero means the block is not live and reads empty`() {
        val mem = FakeMem()
        val t = GbaTracker(mem.reader(), m)
        assertTrue(t.readStatStages(0).isEmpty())
    }

    @Test
    fun `status words name the right condition`() {
        val mem = FakeMem()
        val t = GbaTracker(mem.reader(), m)
        assertEquals("SLP", t.statusName(0x3))
        assertEquals("PSN", t.statusName(0x8))
        assertEquals("BRN", t.statusName(0x10))
        assertEquals("FRZ", t.statusName(0x20))
        assertEquals("PAR", t.statusName(0x40))
        assertEquals("PSN", t.statusName(0x80))
        assertEquals("", t.statusName(0))
    }
}
