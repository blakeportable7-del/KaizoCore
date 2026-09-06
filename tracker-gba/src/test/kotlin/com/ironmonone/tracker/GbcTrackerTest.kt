package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Crystal through a fake WRAM and a fake ROM, laid out from pokecrystal's
 * wram.asm and the ROM tables the game reads. Nothing here is a guess the
 * tracker could agree with by accident: the ROM's base stats and move table
 * are written with distinctive numbers and read back through the same
 * offsets the game uses.
 */
class GbcTrackerTest {

    private class Wram : MemoryReader {
        val bytes = ByteArray(0x8000)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        fun be16(off: Long, v: Int) { put(off, v shr 8); put(off + 1, v and 0xFF) }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - GbcTracker.RAM).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    /** A ROM big enough to hold both tables, with Cyndaquil, Sentret, Ember, Tackle. */
    private fun rom(): ByteArray {
        val r = ByteArray(0x60000)
        header(r, "PM_CRYSTAL", 0xC0)
        fun base(species: Int, hp: Int, atk: Int, t1: Int, t2: Int) {
            val b = GbcTracker.BASE_STATS + (species - 1) * GbcTracker.BASE_STRIDE
            r[b] = species.toByte(); r[b + 1] = hp.toByte(); r[b + 2] = atk.toByte()
            r[b + 3] = 43; r[b + 4] = 65; r[b + 5] = 60; r[b + 6] = 50
            r[b + 7] = t1.toByte(); r[b + 8] = t2.toByte()
        }
        base(155, 39, 52, 20, 20)     // Cyndaquil, Fire/Fire
        base(161, 35, 46, 0, 0)       // Sentret, Normal
        fun move(id: Int, power: Int, type: Int, acc: Int, pp: Int) {
            val m = GbcTracker.MOVES + (id - 1) * GbcTracker.MOVE_STRIDE
            r[m + 2] = power.toByte(); r[m + 3] = type.toByte(); r[m + 4] = acc.toByte(); r[m + 5] = pp.toByte()
        }
        move(52, 40, 20, 255, 25)     // Ember: Fire, 100%
        move(33, 35, 0, 242, 35)      // Tackle: Normal, 95%
        return r
    }

    /** The Game Boy header the map is picked from: logo, title at 0x134, CGB flag. */
    private fun header(r: ByteArray, title: String, cgb: Int) {
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = cgb.toByte()
    }

    private fun party(w: Wram, slot: Int, species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, status: Int = 0) {
        val b = GbcTracker.PARTY_MONS + slot * GbcTracker.PARTY_STRIDE
        w.put(GbcTracker.PARTY_SPECIES + slot, species)
        w.put(b, species); w.put(b + 1, 0)
        moves.forEachIndexed { i, m -> w.put(b + 2 + i, m) }
        w.put(b + 21, 0xAA); w.put(b + 22, 0xAA)         // DVs
        moves.forEachIndexed { i, _ -> w.put(b + 23 + i, 20) }   // PP
        w.put(b + 31, level); w.put(b + 32, status)
        w.be16(b + 34, hp); w.be16(b + 36, maxHp)
        w.be16(b + 38, 30); w.be16(b + 40, 31); w.be16(b + 42, 32); w.be16(b + 44, 33); w.be16(b + 46, 34)
    }

    private fun overworld(): Wram {
        val w = Wram()
        w.put(GbcTracker.PARTY_COUNT, 2)
        party(w, 0, 155, 12, 30, 40, listOf(52, 33, 0, 0))
        party(w, 1, 161, 5, 18, 18, listOf(33, 0, 0, 0), status = 0x08)   // poisoned
        w.put(GbcTracker.PARTY_SPECIES + 2, 0xFF)
        w.put(GbcTracker.JOHTO_BADGES, 0b11); w.put(GbcTracker.KANTO_BADGES, 0)
        w.put(GbcTracker.NUM_ITEMS, 2)
        w.put(GbcTracker.ITEMS, 18); w.put(GbcTracker.ITEMS + 1, 3)          // 3 Potions
        w.put(GbcTracker.ITEMS + 2, 1); w.put(GbcTracker.ITEMS + 3, 5)       // 5 Master Balls: not heals
        w.put(GbcTracker.ITEMS + 4, 0xFF)
        return w
    }

    @Test
    fun `the party is read from WRAM and typed from the ROM`() {
        val s = GbcTracker(overworld(), rom()).read()
        assertEquals(2, s.partyCount)
        val lead = s.party[0]
        assertEquals("CYNDAQUIL", lead.speciesName)
        assertEquals(12, lead.mon.level); assertEquals(30 to 40, lead.mon.curHp to lead.mon.maxHp)
        assertEquals(10, lead.base?.type1, "Fire is Gen 2 type 20, panel type 10")
        assertEquals(listOf("EMBER", "TACKLE").map { it.lowercase() }, lead.moveNames.map { it.lowercase() })
        val ember = lead.moveRows[0]
        assertEquals(40, ember.power); assertEquals(10, ember.type); assertEquals(100, ember.acc); assertEquals(25, ember.ppMax)
        assertEquals("SPE", ember.category, "Fire is special in the Gen 2/3 type split")
        assertEquals("PSN", s.party[1].statusCondition)
        assertTrue(!s.inBattle)
        assertEquals(0b11, s.badges)
        // 3 Potions x 20 HP on a 40 HP lead: 150%, 3 items. Master Balls ignored.
        assertEquals(150 to 3, s.healPercent to s.healCount)
    }

    @Test
    fun `a wild battle shows the opponent, its types, and only moves it has used`() {
        val w = overworld()
        w.put(GbcTracker.BATTLE_MODE, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 2, 33); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        w.put(e + 30, 0); w.put(e + 31, 0)
        val t = GbcTracker(w, rom())
        var s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        val foe = assertNotNull(s.enemy)
        assertEquals("SENTRET", foe.speciesName); assertEquals(4, foe.level); assertEquals(12, foe.curHp)
        assertEquals(emptyList(), foe.movesSeen, "no move used yet, none shown")
        w.put(GbcTracker.ENEMY_LAST_MOVE, 33)
        s = t.read()
        assertEquals(listOf("tackle"), s.enemy!!.movesSeen.map { it.lowercase() })
        // Battle ends: the opponent leaves and the seen list resets.
        w.put(GbcTracker.BATTLE_MODE, 0)
        s = t.read()
        assertTrue(!s.inBattle); assertNull(s.enemy)
    }

    @Test
    fun `a trainer battle is not wild, and a dead lead is a loss`() {
        val w = overworld()
        w.put(GbcTracker.BATTLE_MODE, 2)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 155); w.put(e + 13, 10); w.be16(e + 16, 5); w.be16(e + 18, 30)
        val s = GbcTracker(w, rom()).read()
        assertTrue(s.inBattle && !s.isWildBattle)
        w.be16(GbcTracker.PARTY_MONS + 34, 0)
        assertEquals(GameOver.LOST, GbcTracker(w, rom()).read().gameOver)
    }

    // ------------------------------------------------------------ Gold / Silver

    /** Gold: pokegold's header, Cyndaquil and Ember at gen2_offsets.ini's Gold (U) tables. */
    private fun goldRom(): ByteArray {
        val r = ByteArray(0x60000)
        header(r, "POKEMON_GLDAAUE", 0x80)
        val gs = Gen2Map.GS
        val b = gs.baseStats + (155 - 1) * GbcTracker.BASE_STRIDE
        r[b] = 155.toByte(); r[b + 1] = 39; r[b + 2] = 52; r[b + 7] = 20; r[b + 8] = 20
        val m = gs.moves + (52 - 1) * GbcTracker.MOVE_STRIDE
        r[m + 2] = 40; r[m + 3] = 20; r[m + 4] = 255.toByte(); r[m + 5] = 25
        return r
    }

    /** A one-Cyndaquil party laid out at pokegold's WRAM addresses (Gen2Map.GS). */
    private fun goldWram(): Wram {
        val w = Wram(); val gs = Gen2Map.GS
        w.put(gs.partyCount, 1); w.put(gs.partySpecies, 155); w.put(gs.partySpecies + 1, 0xFF)
        val b = gs.partyMons
        w.put(b, 155); w.put(b + 2, 52); w.put(b + 21, 0xAA); w.put(b + 22, 0xAA); w.put(b + 23, 20)
        w.put(b + 31, 12); w.be16(b + 34, 30); w.be16(b + 36, 40)
        w.put(gs.johtoBadges, 0b101); w.put(gs.kantoBadges, 0)
        w.put(gs.numItems, 1); w.put(gs.items, 18); w.put(gs.items + 1, 2); w.put(gs.items + 2, 0xFF)   // 2 Potions
        return w
    }

    @Test
    fun `Gold is picked from its header and read at pokegold's addresses`() {
        assertSame(Gen2Map.GS, Gen2Map.forRom(goldRom()))
        assertSame(Gen2Map.CRYSTAL, Gen2Map.forRom(rom()))
        val s = GbcTracker(goldWram(), goldRom()).read()
        assertEquals(1, s.partyCount)
        assertEquals("CYNDAQUIL", s.party[0].speciesName)
        assertEquals(10, s.party[0].base?.type1, "typed from the Gold table, not Crystal's")
        assertEquals(40, s.party[0].moveRows[0].power)
        assertEquals(0b101, s.badges)
        assertEquals(100 to 2, s.healPercent to s.healCount, "2 Potions x 20 on a 40 HP lead")
        assertTrue(s.diagnostics.startsWith("Gold/Silver"), s.diagnostics)
        // The proof the map is consulted: Crystal's addresses find nothing in this RAM.
        assertTrue(GbcTracker(goldWram(), goldRom(), Gen2Map.CRYSTAL).read().unreadable)
    }

    @Test
    fun `a Gold battle reads the opponent at pokegold's wEnemyMon and its used move at wEnemyMoveStruct`() {
        val gs = Gen2Map.GS
        val w = goldWram()
        w.put(gs.battleMode, 1)
        val e = gs.enemyMon
        w.put(e, 155); w.put(e + 13, 7); w.be16(e + 16, 9); w.be16(e + 18, 20); w.put(e + 30, 20); w.put(e + 31, 20)
        val t = GbcTracker(w, goldRom())
        var s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        assertEquals("CYNDAQUIL", s.enemy!!.speciesName); assertEquals(emptyList(), s.enemy!!.movesSeen)
        w.put(gs.enemyMove, 52)
        s = t.read()
        assertEquals(listOf("ember"), s.enemy!!.movesSeen.map { it.lowercase() })
    }

    @Test
    fun `a Game Boy ROM with an unknown title gets no map and reads nothing`() {
        val r = ByteArray(0x60000)
        header(r, "TETRIS", 0x00)
        assertNull(Gen2Map.forRom(r))
        assertTrue(GbcTracker(overworld(), r).read().unreadable)
    }

    @Test
    fun `a wrong count byte cannot invent party members`() {
        val w = overworld()
        w.put(GbcTracker.PARTY_COUNT, 6)          // lies: only two structs and a terminator
        assertEquals(2, GbcTracker(w, rom()).read().partyCount)
    }
}
