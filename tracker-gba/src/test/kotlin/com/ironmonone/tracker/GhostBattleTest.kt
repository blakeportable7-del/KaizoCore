package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pokemon Tower ghosts (Battle.lua:371): BATTLE_TYPE_GHOST (bit 15) with
 * BATTLE_TYPE_GHOST_UNVEILED (bit 13) clear, FireRed and LeafGreen only. The
 * reference shows its ghost stand-in (Tracker.getGhostPokemon, "Ghost", types
 * unknown, the real level), records no move, ability or encounter for it
 * (Battle.lua:389, 505) and hides every move's effectiveness (DataHelper.lua:337).
 * We used to show the real species, record its moves and reveal its ability.
 *
 * The real ROMs supply names, base stats and ability scripts; the battle is
 * staged in RAM.
 */
class GhostBattleTest {
    private val frV10 = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-u-v10.gba")
    private val frNatDex = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba")

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

    private val GHOST = 1L shl 15
    private val UNVEILED = 1L shl 13
    private val LICK = 122

    /**
     * A wild battle in progress on [m]: Pikachu out for the player, a Gastly
     * (92) at level 18 for the opponent that has just used Lick, and a Speed
     * Boost battle script on screen for the opponent (FireRed v1.0's
     * BATTLER row at 0x081D929A, ability 3, read from the live byte there).
     */
    private fun stage(mem: Mem, m: GameMap, flags: Long) {
        mem.put8(m.battlersCount, 2)
        mem.put8(m.battleOutcome, 0)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
        mem.put32(m.battleTypeFlags, flags)
        mem.put16(m.battleMons, 25)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e, 92)
        mem.put16(e + 0x0C, LICK)
        mem.put8(e + 0x20, 3)
        mem.put16(e + 0x28, 30); mem.put8(e + 0x2A, 18); mem.put16(e + 0x2C, 30)
        mem.put16(m.battleResults + 0x24, LICK)
        mem.put32(m.scriptCurrInstr, 0x081D929AL)
        mem.put8(m.scriptingBattler, 1)
    }

    /** Two polls: the first enters the battle, the second makes its data ready. */
    private fun settle(t: GbaTracker): TrackerState { t.read(); return t.read() }

    @Test
    fun `a Pokemon Tower ghost is the reference's stand-in and records nothing`() {
        if (!frV10.exists()) { println("SKIP: FireRed v1.0 ROM missing"); return }
        val mem = Mem(frV10.readBytes())
        val m = GameMap.FIRERED_U_V10
        stage(mem, m, GHOST)
        val t = GbaTracker(mem.reader(), m)
        val s = settle(t)
        assertTrue(s.inBattle && s.isWildBattle)
        assertTrue(s.isGhostBattle, "bit 15 without bit 13 is a ghost battle")
        val e = assertNotNull(s.enemy)
        assertTrue(e.isGhost)
        assertEquals(413, e.species, "PokemonData.Values.GhostId")
        assertEquals("Ghost", e.speciesName)
        assertEquals(18, e.level, "the stand-in keeps the real level")
        assertEquals(9, e.type1); assertEquals(9, e.type2)
        assertNull(e.base); assertNull(e.evo)
        assertTrue(e.movesSeen.isEmpty() && e.moveRows.isEmpty() && e.moves.isEmpty(), "no move is tracked for a ghost")
        assertEquals("---", e.abilityGuess)
        assertTrue(e.statStages.isEmpty())
        assertNull(s.abilityRevealed, "no ability is revealed in a ghost battle")
        assertTrue(s.abilitiesRevealed.isEmpty())
        assertNull(s.encounterArea, "incrementEnemyEncounter is never reached for a ghost")
        assertEquals(0, s.catchPercent, "calcCatchRate refuses the GhostId")
        assertNull(t.catchRates(), "CatchRatesScreen.buildScreen refuses the GhostId")
        // Your own battler's ability is still tracked (Battle.lua:482, outside the isGhost check).
        assertEquals(listOf(25), s.ownAbilities.map { it.first })
        // The GhostId reads past the species tables; the tracker must not.
        assertEquals("Ghost", t.speciesName(413))
        assertTrue(t.learnset(413).isEmpty())
    }

    @Test
    fun `with the Silph Scope the same battle is an ordinary one`() {
        if (!frV10.exists()) { println("SKIP: FireRed v1.0 ROM missing"); return }
        val mem = Mem(frV10.readBytes())
        val m = GameMap.FIRERED_U_V10
        stage(mem, m, GHOST or UNVEILED)
        val t = GbaTracker(mem.reader(), m)
        val s = settle(t)
        assertFalse(s.isGhostBattle)
        val e = assertNotNull(s.enemy)
        assertFalse(e.isGhost)
        assertEquals(92, e.species)
        assertEquals("GASTLY", e.speciesName)
        assertEquals(listOf("LICK"), e.movesSeen)
        assertEquals(92, s.abilityRevealed?.first, "Speed Boost's script reveals the battler's ability")
        assertTrue(s.encounterArea != null)
        assertTrue((s.catchPercent ?: -1) > 0)
    }

    @Test
    fun `Emerald has no ghost battles`() {
        // GameSettings.game == 3 only: bit 15 means something else outside FireRed and LeafGreen.
        val mem = Mem(ByteArray(0))
        val m = GameMap.EMERALD_U
        stage(mem, m, GHOST)
        val s = settle(GbaTracker(mem.reader(), m))
        assertTrue(s.inBattle)
        assertFalse(s.isGhostBattle)
        assertEquals(92, s.enemy?.species)
    }

    @Test
    fun `Nat Dex FireRed uses the extension's GhostId`() {
        if (!frNatDex.exists()) { println("SKIP: Nat. Dex FireRed ROM missing"); return }
        val mem = Mem(frNatDex.readBytes())
        val m = GameMap.resolve(mem.reader())
        mem.put8(m.battlersCount, 2)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
        mem.put32(m.battleTypeFlags, GHOST)
        mem.put16(m.battleMons, 25)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e, 92); mem.put8(e + 0x2A, 21); mem.put16(e + 0x28, 40); mem.put16(e + 0x2C, 40)
        val t = GbaTracker(mem.reader(), m)
        val s = settle(t)
        assertTrue(s.isGhostBattle)
        assertEquals(1285, s.enemy?.species, "NatDexExtension.lua:16912 sets GhostId = 1285")
        assertEquals("Ghost", s.enemy?.speciesName)
        assertEquals(21, s.enemy?.level)
        assertTrue(t.learnset(1285).isEmpty())
    }
}
