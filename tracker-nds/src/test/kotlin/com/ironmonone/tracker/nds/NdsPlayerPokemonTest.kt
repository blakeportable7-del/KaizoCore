package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reference's playerPokemon on DS (Program.lua:521-535): in a fetched battle
 * the Pokemon on the field, whose battle stat stages are yours
 * (BattleHandlerGen4._updateStatStages); otherwise the first party member standing.
 * Synthetic Platinum memory at MemoryAddresses[PLATINUM], as NdsRunOverTest lays it out.
 */
class NdsPlayerPokemonTest {
    private val ram = ByteArray(0x400000)
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val m = NdsGameMap.PLATINUM

    private fun putU32(off: Long, v: Long) { val i = off.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
    private fun putU16(off: Long, v: Int) { val i = off.toInt(); ram[i] = v.toByte(); ram[i + 1] = (v ushr 8).toByte() }
    private fun put(off: Long, bytes: ByteArray) = bytes.copyInto(ram, off.toInt())

    private fun mon(pid: Long, species: Int, hp: Int) =
        Gen4.encodeParty(pid, species = species, level = 20, curHp = hp, maxHp = 40, moves = listOf(33, 0, 0, 0))

    private fun tracker(onField: Long, battle: Boolean): NdsTracker {
        putU32(m.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        // Your party and the battle's copy of it: Squirtle (0x100), then Pidgey (0x101).
        listOf(mon(0x100L, 7, 40), mon(0x101L, 16, 40)).forEachIndexed { i, b ->
            put(versionRel + m.playerBase + i * 236L, b)
            put(versionRel + m.playerBattleBase + i * 236L, b)
        }
        put(versionRel + m.enemyBase, Gen4.encodeParty(0x9000L, 25, 20, 40, 40, listOf(84, 0, 0, 0)))
        putU32(versionRel + m.enemyBattleMonPid, 0x9000L)
        putU32(versionRel + m.playerBattleMonPid, onField)
        // Your side's stages, HP ATK DEF SPE SPA SPD ACC EVA: +2 attack, -1 accuracy.
        byteArrayOf(6, 8, 6, 6, 6, 6, 5, 6).copyInto(ram, (versionRel + m.statStagesPlayer).toInt())
        putU16(m.battleStatus, if (battle) 0x2100 else 0x2800)
        val reader = NdsMemoryReader { addr, len ->
            val off = (addr - 0x02000000L).toInt()
            if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
        }
        return NdsTracker(reader, null, m)
    }

    @Test
    fun `your stages go on the Pokemon on the field, not the lead`() {
        val s = tracker(onField = 0x101L, battle = true).read()
        assertEquals(0x101L, s.playerActive?.mon?.pid, "Pidgey was sent out")
        assertEquals(8, s.party[1].statStages["ATK"])
        assertEquals(5, s.party[1].statStages["ACC"])
        assertTrue(s.party[0].statStages.isEmpty(), "the lead is on the bench")
        assertEquals(s.party[1], s.playerPokemon)
    }

    @Test
    fun `with the lead out, the lead carries them`() {
        val s = tracker(onField = 0x100L, battle = true).read()
        assertEquals(8, s.party[0].statStages["ATK"])
        assertEquals(s.party[0], s.playerPokemon)
    }

    @Test
    fun `outside a battle it is the first party member standing`() {
        val t = tracker(onField = 0x100L, battle = false)
        put(versionRel + m.playerBase, mon(0x100L, 7, 0))
        val s = t.read()
        assertEquals(16, s.playerPokemon?.mon?.species, "the fainted lead gives way")
        assertTrue(s.party.all { it.statStages.isEmpty() })
    }
}
