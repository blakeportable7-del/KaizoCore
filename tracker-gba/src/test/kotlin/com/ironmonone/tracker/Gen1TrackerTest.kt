package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Red and Yellow through a fake WRAM and a fake ROM laid out from pokered's
 * ram/wram.asm and the randomizer's gen1_offsets.ini. The distinctive part
 * of Gen 1 is that RAM holds INTERNAL species ids and the ROM's own order
 * table turns them into dex numbers; the fixtures use a deliberately odd
 * mapping so a tracker that skipped the table would name the wrong Pokemon.
 */
class Gen1TrackerTest {

    private class Wram : MemoryReader {
        val bytes = ByteArray(0x10000)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        fun be16(off: Long, v: Int) { put(off, v shr 8); put(off + 1, v and 0xFF) }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - Gen1Tracker.RAM).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    // Internal ids in the fixture: 0xB1 -> dex 155? No: Gen 1 has 151, so
    // internal 0xB1 (Bulbasaur's real internal id is 0x99) maps to dex 4
    // (Charmander) here, internal 0x03 to dex 25 (Pikachu), on purpose.
    private fun rom(map: Gen1Map, title: String, cgb: Int): ByteArray {
        val r = ByteArray(0x50000)
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }; r[0x143] = cgb.toByte()
        r[map.dexOrder + 0xB1 - 1] = 4; r[map.dexOrder + 0x03 - 1] = 25
        fun base(dex: Int, hp: Int, atk: Int, spc: Int, t1: Int, t2: Int) {
            val b = map.baseStats + (dex - 1) * Gen1Tracker.BASE_STRIDE
            r[b] = dex.toByte(); r[b + 1] = hp.toByte(); r[b + 2] = atk.toByte(); r[b + 3] = 43; r[b + 4] = 65; r[b + 5] = spc.toByte()
            r[b + 6] = t1.toByte(); r[b + 7] = t2.toByte()
        }
        base(4, 39, 52, 50, 20, 20)     // Charmander: Fire
        base(25, 35, 55, 50, 23, 23)    // Pikachu: Electric
        fun move(id: Int, power: Int, type: Int, acc: Int, pp: Int) {
            val m = map.moves + (id - 1) * Gen1Tracker.MOVE_STRIDE
            r[m] = id.toByte(); r[m + 2] = power.toByte(); r[m + 3] = type.toByte(); r[m + 4] = acc.toByte(); r[m + 5] = pp.toByte()
        }
        move(52, 40, 20, 255, 25)       // Ember
        move(84, 40, 23, 255, 30)       // Thunder Shock
        move(10, 40, 0, 255, 35)        // Scratch
        return r
    }

    private fun party(w: Wram, map: Gen1Map, slot: Int, internal: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, status: Int = 0) {
        val b = map.partyMons + slot * Gen1Tracker.PARTY_STRIDE
        w.put(map.partySpecies + slot, internal)
        w.put(b, internal); w.be16(b + 1, hp); w.put(b + 4, status)
        moves.forEachIndexed { i, m -> w.put(b + 8 + i, m) }
        w.be16(b + 27, 0xAAAA)
        moves.forEachIndexed { i, _ -> w.put(b + 29 + i, 20) }
        w.put(b + 33, level); w.be16(b + 34, maxHp)
        w.be16(b + 36, 30); w.be16(b + 38, 31); w.be16(b + 40, 32); w.be16(b + 42, 33)
    }

    private fun overworld(map: Gen1Map): Wram {
        val w = Wram()
        w.put(map.partyCount, 2)
        party(w, map, 0, 0xB1, 12, 30, 40, listOf(52, 10, 0, 0))
        party(w, map, 1, 0x03, 5, 18, 18, listOf(84, 0, 0, 0), status = 0x08)
        w.put(map.partySpecies + 2, 0xFF)
        w.put(map.badges, 0b101)
        w.put(map.numItems, 2)
        w.put(map.items, 0x14); w.put(map.items + 1, 3)        // 3 Potions
        w.put(map.items + 2, 0x04); w.put(map.items + 3, 5)    // 5 Poke Balls: not heals
        w.put(map.items + 4, 0xFF)
        return w
    }

    @Test
    fun `Red is picked from its header, species go through the dex order table, and the ROM types the party`() {
        val rom = rom(Gen1Map.RED_BLUE, "POKEMON RED", 0x00)
        assertSame(Gen1Map.RED_BLUE, Gen1Map.forRom(rom))
        val s = Gen1Tracker(overworld(Gen1Map.RED_BLUE), rom).read()
        assertEquals(2, s.partyCount)
        val lead = s.party[0]
        assertEquals("CHARMANDER", lead.speciesName.uppercase(), "internal 0xB1 -> dex 4 through the ROM's table")
        assertEquals(4, lead.mon.species)
        assertEquals(12, lead.mon.level); assertEquals(30 to 40, lead.mon.curHp to lead.mon.maxHp)
        assertEquals(10, lead.base?.type1, "Fire is Gen 1 type 20, panel type 10")
        // One Special stat: shown once, counted once. 39+52+43+65+50, not with 50 twice.
        assertEquals(true, lead.base?.singleSpecial); assertEquals(39 + 52 + 43 + 65 + 50, lead.base?.bst)
        assertEquals(listOf("ember", "scratch"), lead.moveNames.map { it.lowercase() })
        val ember = lead.moveRows[0]
        assertEquals(40, ember.power); assertEquals(100, ember.acc); assertEquals(25, ember.ppMax)
        assertEquals("PIKACHU", s.party[1].speciesName.uppercase()); assertEquals("PSN", s.party[1].statusCondition)
        assertEquals(0b101, s.badges); assertEquals("RBY", s.badgeSet)
        assertEquals(150 to 3, s.healPercent to s.healCount)
        assertTrue(!s.inBattle); assertTrue(!s.unreadable)
        assertEquals("-", lead.itemName); assertEquals("-", lead.abilityName)
    }

    @Test
    fun `a wild battle shows the opponent and only the moves it has used, and a trainer battle is not wild`() {
        val map = Gen1Map.RED_BLUE
        val rom = rom(map, "POKEMON BLUE", 0x00)
        val w = overworld(map)
        w.put(map.inBattle, 1)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 8, 84); w.put(e + 9, 10)
        w.put(e + 14, 4); w.be16(e + 15, 18)
        val t = Gen1Tracker(w, rom)
        var s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        val foe = assertNotNull(s.enemy)
        assertEquals("PIKACHU", foe.speciesName.uppercase()); assertEquals(4, foe.level); assertEquals(12, foe.curHp)
        assertEquals(13, foe.type1, "Electric is Gen 1 type 23, panel type 13")
        assertEquals(emptyList(), foe.movesSeen)
        w.put(map.enemyMove, 84)
        assertEquals(listOf("thundershock"), t.read().enemy!!.movesSeen.map { it.lowercase() })
        w.put(map.enemyMove, 52)                       // a move the opponent does not know is not recorded
        assertEquals(1, t.read().enemy!!.movesSeen.size)
        w.put(map.inBattle, 2)
        s = t.read(); assertTrue(s.inBattle && !s.isWildBattle)
        w.put(map.inBattle, 0)
        s = t.read(); assertTrue(!s.inBattle); assertNull(s.enemy)
    }

    @Test
    fun `Yellow is Red minus one in the D block, and reads its own layout`() {
        val y = Gen1Map.YELLOW; val r = Gen1Map.RED_BLUE
        for ((a, b) in listOf(y.partyCount to r.partyCount, y.partyMons to r.partyMons, y.enemyMon to r.enemyMon, y.inBattle to r.inBattle, y.enemyMove to r.enemyMove, y.badges to r.badges, y.numItems to r.numItems, y.items to r.items)) {
            assertEquals(b - 1, a)
        }
        assertEquals(0, y.mewStats, "Yellow keeps Mew in the main table (gen1_offsets.ini MewStatsOffset=0)")
        val rom = rom(y, "POKEMON YELLOW", 0x80)
        assertSame(y, Gen1Map.forRom(rom))
        val s = Gen1Tracker(overworld(y), rom).read()
        assertEquals("CHARMANDER", s.party[0].speciesName.uppercase())
        // The same RAM through Red's map: the count byte is one address over, so no party.
        assertEquals(0, Gen1Tracker(overworld(y), rom, r).read().partyCount)
    }

    @Test
    fun `a wrong count byte cannot invent party members, zero is no party yet, and an unknown title has no map`() {
        val map = Gen1Map.RED_BLUE; val rom = rom(map, "POKEMON RED", 0x00)
        val w = overworld(map); w.put(map.partyCount, 6)
        assertEquals(2, Gen1Tracker(w, rom).read().partyCount)
        val w0 = overworld(map); w0.put(map.partyCount, 0); w0.put(map.partySpecies, 0)
        val s0 = Gen1Tracker(w0, rom).read(); assertEquals(0, s0.partyCount); assertTrue(!s0.unreadable)
        assertNull(Gen1Map.forRom(rom(map, "TETRIS", 0x00)))
        assertTrue(Gen1Tracker(w, rom(map, "TETRIS", 0x00)).read().unreadable)
    }
}
