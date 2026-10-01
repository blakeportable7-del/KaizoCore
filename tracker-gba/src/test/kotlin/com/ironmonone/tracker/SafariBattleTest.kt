package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Safari Zone battles (Battle.lua:176-185). The player sends nothing out, so
 * gBattleMons[0] holds no species and the battle looks "fake"; the reference
 * starts it anyway when Program.isInSafariZone says the SYS_SAFARI_MODE flag
 * is set and the opponent's lead is in gEnemyParty. We had no Safari branch,
 * so no Safari battle ever began. Then Battle.lua:607-612 keeps each Safari
 * map's wild Pokemon with their highest level (Tracker.TrackSafariEncounter).
 */
class SafariBattleTest {
    private val frV10 = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-u-v10.gba")
    private val natDex = listOf(
        File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba") to (0x800 + 0x0),
        File("C:/Users/bepor/IronMonOne/.vendor/roms/emerald-natdex-121.gba") to (0x860 + 0x2C),
    )

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

    private val SB1 = 0x02025000L
    private val SAFARI = 0x84L     // BATTLE_TYPE_SAFARI (bit 7) with the usual bit 2

    /** Standing on [mapId] with the Safari flag [inZone], outside any battle. */
    private fun overworld(mem: Mem, m: GameMap, mapId: Int, inZone: Boolean) {
        mem.put32(m.saveBlock1Ptr, SB1)
        mem.put16(m.mapHeader + 0x12, mapId)
        val flag = m.safariModeFlag
        mem.put8(SB1 + m.gameFlagsOffset + flag / 8, if (inZone) 1 shl (flag % 8) else 0)
        mem.put8(m.battleOutcome, 7)      // the last battle ended with a catch
        mem.put32(m.battleMainFunc, m.returnToOverworld)
    }

    /**
     * A Safari encounter with [species] at [level]: the opponent in gEnemyParty
     * (personality = trainer id = 24, so key 0 and substructures G, A, E, M) and
     * in gBattleMons[1]; gBattleMons[0] empty, as the Safari controller leaves it.
     */
    private fun encounter(mem: Mem, m: GameMap, species: Int, level: Int, lead: Boolean = true) {
        val l = m.monLayout
        if (lead) {
            mem.put32(m.enemyParty, 24); mem.put32(m.enemyParty + 4, 24)
            mem.put16(m.enemyParty + l.enc, species)
            mem.put8(m.enemyParty + l.level, level)
            mem.put16(m.enemyParty + l.curHp, 50); mem.put16(m.enemyParty + l.maxHp, 50)
        }
        mem.put8(m.battlersCount, 2)
        mem.put16(m.battleMons, 0)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e, species); mem.put8(e + 0x2A, level); mem.put16(e + 0x28, 50); mem.put16(e + 0x2C, 50)
        mem.put32(m.battleTypeFlags, SAFARI)
        mem.put8(m.battleOutcome, 0)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
    }

    private fun end(mem: Mem, m: GameMap) {
        mem.put8(m.battleOutcome, 7)
        mem.put32(m.battleMainFunc, m.returnToOverworld)
    }

    @Test
    fun `a FireRed Safari Zone battle begins, shows the opponent and keeps it`() {
        if (!frV10.exists()) { println("SKIP: FireRed v1.0 ROM missing"); return }
        val mem = Mem(frV10.readBytes())
        val m = GameMap.FIRERED_U_V10
        overworld(mem, m, 147, inZone = true)
        val t = GbaTracker(mem.reader(), m)
        t.read(); t.read()                           // the map id settles over two polls
        assertTrue(t.isInSafariZone())
        encounter(mem, m, species = 32, level = 22)  // Nidoran M, Safari Zone Center
        t.read(); val s = t.read()
        assertTrue(s.inBattle, "a Safari battle looks fake and must still begin")
        assertTrue(s.isWildBattle)
        assertEquals(32, s.enemy?.species)
        assertEquals("Walking", s.encounterArea, "the Safari bit sends the area to the terrain")
        assertEquals(listOf(32 to 22), t.safariEncounters(147))

        // Caught: the outcome alone ends it, although gBattleMons[0] never held a species.
        end(mem, m)
        assertFalse(t.read().inBattle)

        // Met again higher: the level rises. Met lower: it stays.
        encounter(mem, m, species = 32, level = 25)
        t.read(); assertTrue(t.read().inBattle)
        end(mem, m); t.read()
        encounter(mem, m, species = 32, level = 20)
        t.read(); t.read()
        end(mem, m); t.read()
        assertEquals(listOf(32 to 25), t.safariEncounters(147))
    }

    @Test
    fun `the Safari Zone maps are known in the tracker's own numbering`() {
        val m = object : MemoryReader { override fun read(address: Long, length: Int) = ByteArray(length) }
        assertTrue(GbaTracker(m, GameMap.FIRERED_U_V10).isSafariMap(147))
        assertTrue(!GbaTracker(m, GameMap.FIRERED_U_V10).isSafariMap(146))
        // Ruby reports 239-242; the tracker numbers them one lower (rsMapShift).
        assertTrue(GbaTracker(m, GameMap.RUBY_U).isSafariMap(238))
        assertTrue(GbaTracker(m, GameMap.EMERALD_U).isSafariMap(394))
    }

    @Test
    fun `outside the Safari Zone the same memory is a fake battle`() {
        if (!frV10.exists()) { println("SKIP: FireRed v1.0 ROM missing"); return }
        val mem = Mem(frV10.readBytes())
        val m = GameMap.FIRERED_U_V10
        overworld(mem, m, 147, inZone = false)
        val t = GbaTracker(mem.reader(), m)
        t.read(); t.read()
        encounter(mem, m, species = 32, level = 22)
        t.read()
        assertFalse(t.read().inBattle)
        assertTrue(t.safariEncounters(147).isEmpty())
    }

    @Test
    fun `no opponent in the party means no battle, even in the zone`() {
        // After loading a save inside the Safari Zone every byte reads 0,
        // gBattleOutcome included; the reference's opposingPokemon check is
        // what keeps that from opening a battle.
        if (!frV10.exists()) { println("SKIP: FireRed v1.0 ROM missing"); return }
        val mem = Mem(frV10.readBytes())
        val m = GameMap.FIRERED_U_V10
        overworld(mem, m, 147, inZone = true)
        val t = GbaTracker(mem.reader(), m)
        t.read(); t.read()
        encounter(mem, m, species = 32, level = 22, lead = false)
        t.read()
        assertFalse(t.read().inBattle)
    }

    @Test
    fun `Emerald reads its own Safari flag and maps`() {
        val mem = Mem(ByteArray(0))
        val m = GameMap.EMERALD_U
        assertEquals(0x860 + 0x2C, m.safariModeFlag)
        overworld(mem, m, 394, inZone = true)        // Safari Zone N-Ext., Emerald only
        val t = GbaTracker(mem.reader(), m)
        t.read(); t.read()
        encounter(mem, m, species = 190, level = 34) // Aipom
        t.read(); val s = t.read()
        assertTrue(s.inBattle)
        assertEquals(listOf(190 to 34), t.safariEncounters(394))
    }

    @Test
    fun `Nat Dex publishes the Safari flag in its slot table`() {
        for ((rom, flag) in natDex) {
            if (!rom.exists()) { println("SKIP: ${rom.name} missing"); continue }
            val m = GameMap.resolve(Mem(rom.readBytes()).reader())
            assertEquals(flag, m.safariModeFlag, rom.name)
        }
    }
}
