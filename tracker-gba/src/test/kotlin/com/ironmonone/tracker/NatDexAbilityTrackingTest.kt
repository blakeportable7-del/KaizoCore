package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Blake, 2026-09-28: "at least on fire red natl dex, after an enemy pokemon's
 * ability is known, the tracker never picks that information up". The real
 * FireRed Nat. Dex 1.2.1 ROM, with the battle staged in RAM.
 */
class NatDexAbilityTrackingTest {
    private val romFile = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba")

    private class Mem(val rom: ByteArray) {
        val ram = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { ram[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun reader() = MemoryReader { address, length ->
            if (address >= 0x08000000L) {
                val off = (address - 0x08000000L).toInt()
                if (off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
            } else ByteArray(length) { ram[address + it] ?: 0 }
        }
    }

    /** A party record with PID = OTID = 24: key 0 and substructure order G, A, E, M. */
    private fun stageMon(mem: Mem, at: Long, layout: PokemonDecoder.Layout, species: Int, abilitySlot: Int) {
        mem.put32(at, 24); mem.put32(at + 4, 24)
        val enc = at + layout.enc
        mem.put16(enc, species)                                   // G: species
        mem.put32(enc + 36 + 4, if (abilitySlot == 1) 0x80000000L else 0L)   // M: IV word, bit 31
        mem.put8(at + layout.level, 30)
    }

    private fun u32(rom: ByteArray, off: Int) =
        (rom[off].toLong() and 0xFF) or ((rom[off + 1].toLong() and 0xFF) shl 8) or
            ((rom[off + 2].toLong() and 0xFF) shl 16) or ((rom[off + 3].toLong() and 0xFF) shl 24)

    @Test
    fun `an ability script on screen reveals the enemy's ability`() {
        if (!romFile.exists()) { println("SKIP: Nat. Dex FireRed ROM missing"); return }
        val rom = romFile.readBytes()
        val mem = Mem(rom)
        val t = GbaTracker(mem.reader(), GameMap.resolve(mem.reader()))
        val m = t.map
        println("map=${m.name} battleMons=%08X size=${m.battleMonSize} count=%08X script=%08X scriptingBattler=%08X attacker=%08X target=%08X table=${m.abilityScriptTable}"
            .format(m.battleMons, m.battlersCount, m.scriptCurrInstr, m.scriptingBattler, m.battlerAttacker, m.battlerTarget))
        mem.put8(m.battlersCount, 2)
        mem.put8(m.battlerAttacker, 0)
        mem.put8(m.battlerTarget, 1)
        mem.put8(m.scriptingBattler, 1)
        // Enemy (battler 1): a Hypno in its first ability slot; base data says Insomnia (15).
        // Byte 0x20 of the battle struct holds garbage on purpose: in Nat. Dex it is not
        // the ability (types start at 0x21 and abilities are u16), and reading it is what
        // made every reveal fail. The old code reads it and must fail this test.
        mem.put16(m.battleMons, 25); mem.put8(m.battleMons + 0x20, 0x77)
        val enemy = m.battleMons + m.battleMonSize
        mem.put16(enemy, 97); mem.put8(enemy + 0x20, 0x77)
        // gBattlerPartyIndexes: battler 1 is enemy party slot 0, which holds that Hypno.
        mem.put16(m.battlerPartyIndexes + 2, 0)
        stageMon(mem, m.enemyParty, m.monLayout, species = 97, abilitySlot = 0)
        assertEquals(15, t.baseStats(97)?.ability1, "Nat. Dex Hypno's first ability")
        // BattleScript_PrintAbilityMadeIneffective + 8: ATTACKER trigger for 15/72 (the target's ability).
        mem.put32(m.scriptCurrInstr, u32(rom, 0x35C) + 0x8)
        val r = t.readAbilityTrigger()
        assertNotNull(r, "no reveal")
        assertEquals(97, r.first)
        println("revealed: $r")
    }
}
