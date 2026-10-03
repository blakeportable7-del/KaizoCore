package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the Gen 3 tracker reads for the Nuzlocke rules engine (2026-09-29): the battle's outcome and turn, the bag
 * and the Poke Balls in it, the battle style, whether the wild Pokemon is shiny, who the trainer is, and the level
 * cap table. Synthetic memory, laid out at Emerald's real addresses.
 */
class GbaTrackerNuzlockeTest {

    private val map = GameMap.EMERALD_U

    /** Memory as separate blocks; a read inside one block is served from it, any other is unmapped. */
    private class Mem {
        val blocks = TreeMap<Long, ByteArray>()
        fun poke(addr: Long, bytes: ByteArray) { blocks[addr] = bytes }
        fun u8(addr: Long, v: Int) = poke(addr, byteArrayOf(v.toByte()))
        fun u16(addr: Long, v: Int) = poke(addr, byteArrayOf(v.toByte(), (v shr 8).toByte()))
        fun u32(addr: Long, v: Long) = poke(addr, ByteArray(4).also { it.putU32(0, v) })
        fun reader() = MemoryReader { a, n ->
            val e = blocks.floorEntry(a)
            if (e != null && a + n <= e.key + e.value.size) e.value.copyOfRange((a - e.key).toInt(), (a - e.key).toInt() + n) else ByteArray(0)
        }
    }

    private val sb1 = 0x02005000L
    private val sb2 = 0x02006000L
    private val key = 0x1234

    /** A wild or trainer battle on the field, a bag with a Potion and five Poke Balls, Set battle style, three badges. */
    private fun world(trainerId: Int? = null, shinyEnemy: Boolean = false, style: Int = 1, map: GameMap = this.map): Mem {
        val m = Mem()
        m.u8(map.partyCount, 0)
        m.u8(map.battlersCount, 2)
        // Battler 0 (the player's) and battler 1 (the enemy): Pikachu at level 7, 18 of 22 HP, Electric, personality 24.
        val mons = ByteArray(0x58 * 2)
        mons[0] = 25
        mons[0x58 + 0x00] = 25; mons[0x58 + 0x2A] = 7; mons[0x58 + 0x28] = 18; mons[0x58 + 0x2C] = 22
        mons[0x58 + 0x21] = 13; mons[0x58 + 0x22] = 13
        ByteArray(4).also { it.putU32(0, 24) }.copyInto(mons, 0x58 + 0x48)
        m.poke(map.battleMons, mons)
        m.u32(map.battleTypeFlags, if (trainerId != null) 0x8L else 0L)
        m.u8(map.battleOutcome, 0)
        if (trainerId != null) m.u16(map.trainerOpponent, trainerId)
        // gBattleResults: the turn counter at +0x13.
        m.poke(map.battleResults, ByteArray(0x60).also { it[map.battleResultsTurnOffset] = 3 })
        // The enemy party's first slot, Pikachu with personality 24 and trainer id 24 (shiny) or 280 (not: 280 xor 24 is 256).
        val party = ByteArray(6 * 100)
        encodeMon(24, if (shinyEnemy) 24 else 280, 25, 7, 18, 22).copyInto(party, 0)
        m.poke(map.enemyParty, party)
        // Save blocks: the pointers, the security key, the options word, the badges, the bag.
        m.u32(map.saveBlock1Ptr, sb1); m.u32(map.saveBlock2Ptr, sb2)
        m.u32(sb2 + map.encryptionKeyOffset, key.toLong())
        m.u16(sb2 + 0x14, style shl 9)
        m.u16(sb1 + map.badgeOffset, 0b111 shl 7)
        val items = ByteArray(map.bagItemsSlots * 4)
        putItem(items, 0, 13, 3); putItem(items, 1, 14, 1)
        m.poke(sb1 + map.bagItemsOffset, items)
        m.poke(sb1 + map.bagBerriesOffset, ByteArray(map.bagBerriesSlots * 4).also { putItem(it, 0, 139, 2) })
        m.poke(sb1 + map.bagBallsOffset, ByteArray(map.bagBallsSlots * 4).also { putItem(it, 0, 4, 5); putItem(it, 1, 3, 2) })
        return m
    }

    private fun putItem(pocket: ByteArray, slot: Int, id: Int, qty: Int) {
        pocket[slot * 4] = id.toByte(); pocket[slot * 4 + 1] = (id shr 8).toByte()
        val q = qty xor key
        pocket[slot * 4 + 2] = q.toByte(); pocket[slot * 4 + 3] = (q shr 8).toByte()
    }

    /** A vanilla party struct: growth substructure first (personality a multiple of 24), the trainer id as given. */
    private fun encodeMon(pid: Long, otId: Long, species: Int, level: Int, hp: Int, maxHp: Int): ByteArray {
        require(pid % 24 == 0L)
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        val mon = ByteArray(100)
        mon.putU32(0, pid); mon.putU32(4, otId)
        val k = pid xor otId
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4) xor k)
        mon[0x54] = level.toByte()
        mon[0x56] = hp.toByte(); mon[0x57] = (hp shr 8).toByte()
        mon[0x58] = maxHp.toByte(); mon[0x59] = (maxHp shr 8).toByte()
        return mon
    }

    /** Two polls: the battle screen latches, then its data is readable. */
    private fun inBattle(m: Mem, map: GameMap = this.map): Pair<GbaTracker, TrackerState> {
        val t = GbaTracker(m.reader(), map)
        t.read()
        return t to t.read()
    }

    // ------------------------------------------------------------------ the catching tutorial (2026-09-30, UX audit P0-9)

    @Test
    fun `nothing is read while the catching tutorial runs, as the reference does, and only the first time`() {
        val m = world()
        m.u8(map.specialFlags, 0)
        m.u8(map.battleOutcome, 1)               // no battle yet: the player is walking to Route 102
        val t = GbaTracker(m.reader(), map)
        val first = t.read()
        // One special flag alone (the map name popup hidden, say) is not the tutorial: reads go on.
        m.u8(map.specialFlags, 1)
        val before = t.read()
        assertNotSame(first, before)
        assertFalse(before.inBattle)
        // The tutorial: Wally's Zigzagoon is in the party and his Ralts battle is on the field.
        m.u8(map.specialFlags, 3)
        m.u8(map.battleOutcome, 0)
        m.u32(map.battleTypeFlags, 0x200L)
        assertSame(before, t.read(), "the state from before the tutorial stands")
        assertSame(before, t.read())
        assertFalse(t.read().inBattle, "the lesson is not a battle the tracker saw")
        // It ends: reads go on, and the lesson's catch is a stale outcome with no battle behind it.
        m.u8(map.specialFlags, 0)
        m.u8(map.battleOutcome, 7)
        val after = t.read()
        assertNotSame(before, after)
        assertFalse(after.inBattle)
        // The same value later in the game is two unrelated flags, not the tutorial (Program.hasCompletedTutorial).
        m.u8(map.specialFlags, 3)
        assertNotSame(after, t.read())
    }

    @Test
    fun `every Gen 3 game's map knows where the tutorial flag is`() {
        assertEquals(0x020375FCL, GameMap.EMERALD_U.specialFlags)
        for (g in listOf(GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U)) assertEquals(0x020370E0L, g.specialFlags, g.name)
        for (g in listOf(GameMap.RUBY_U, GameMap.SAPPHIRE_U)) assertEquals(0x0202E8E2L, g.specialFlags, g.name)
    }

    @Test
    fun `a lesson battle is nobody's encounter, even when it is seen`() {
        // Wally's tutorial, or the Old Man's: bit 9 of the battle type, in every Gen 3 game.
        val wally = world().also { it.u32(map.battleTypeFlags, 0x200L) }
        val (_, s) = inBattle(wally)
        assertTrue(s.inBattle && s.isWildBattle, "it reads as a wild battle, which is the trap")
        assertTrue(assertNotNull(s.nuz).lesson)
        assertTrue(assertNotNull(Gen3Nuzlocke.snapshot(s)).ghost, "so the engine takes no encounter and no catch from it")
        // An ordinary wild battle is not one.
        val (_, plain) = inBattle(world())
        assertFalse(assertNotNull(plain.nuz).lesson)
        assertFalse(assertNotNull(Gen3Nuzlocke.snapshot(plain)).ghost)
        // Bit 16 is the Teachy TV's battle on FireRed and LeafGreen only; Emerald uses that bit for the Battle Dome.
        val (_, dome) = inBattle(world().also { it.u32(map.battleTypeFlags, 0x10000L) })
        assertFalse(assertNotNull(dome.nuz).lesson)
        val fr = GameMap.FIRERED_U_V11
        val (_, teachy) = inBattle(world(map = fr).also { it.u32(fr.battleTypeFlags, 0x10000L) }, fr)
        assertTrue(teachy.inBattle)
        assertTrue(assertNotNull(teachy.nuz).lesson)
    }

    @Test
    fun `a battle in progress reports its turn, the bag without Poke Balls, the ball count and the battle style`() {
        val (_, s) = inBattle(world())
        assertTrue(s.inBattle && s.isWildBattle)
        val n = assertNotNull(s.nuz)
        assertEquals(3, n.turn)
        assertEquals(0, n.battleOutcome, "an outcome of 0 is a battle that is still going")
        assertEquals(7, n.ballCount, "five Poke Balls and two Great Balls")
        assertEquals(true, n.battleStyleSet)
        val bag = assertNotNull(n.bag)
        assertEquals(setOf(13, 14, 139), bag.keys, "Items and Berries pockets, and no balls")
        assertEquals(3, bag.getValue(13).qty)
        assertEquals(2, bag.getValue(139).qty)
    }

    @Test
    fun `the battle style bit is read as Shift when it is clear`() {
        assertEquals(false, inBattle(world(style = 0)).second.nuz!!.battleStyleSet)
    }

    @Test
    fun `Ruby and Sapphire read the battle style from their fixed save block, Nat Dex from its published pointer`() {
        // Ruby and Sapphire: SaveBlock2 at its fixed address, the same options bit (pokeruby's SaveBlock2 header).
        for (ruby in listOf(GameMap.RUBY_U, GameMap.SAPPHIRE_U)) {
            for (style in listOf(1, 0)) {
                val m = world(style = 1 - style)   // the pointer-based block says the opposite, so reading it would fail
                m.u16(ruby.saveBlock2Fixed + 0x14, style shl 9)
                val t = GbaTracker(m.reader(), ruby)
                t.read()
                assertEquals(style == 1, t.read().nuz?.battleStyleSet, "${ruby.routeVersion}, style $style")
            }
        }
        // Nat. Dex keeps the header, and saveBlock2() follows the pointer.
        val natdex = GbaTracker(world(style = 1).reader(), map.copy(expandedSpeciesIds = true))
        natdex.read()
        assertEquals(true, natdex.read().nuz?.battleStyleSet)
        val shift = GbaTracker(world(style = 0).reader(), map.copy(expandedSpeciesIds = true))
        shift.read()
        assertEquals(false, shift.read().nuz?.battleStyleSet)
    }

    @Test
    fun `the outcome is read once the battle is over, and the bag once more with it`() {
        val m = world()
        val (t, during) = inBattle(m)
        assertNotNull(during.nuz!!.bag)
        // The game says caught. The tracker leaves the battle and reports it.
        m.u8(map.battleOutcome, 7)
        val after = t.read()
        assertFalse(after.inBattle)
        assertEquals(7, after.nuz!!.battleOutcome)
        assertEquals(-1, after.nuz!!.turn)
        assertNotNull(after.nuz!!.bag, "read once more, for the item check")
        assertNull(t.read().nuz!!.bag, "and not again")
        assertEquals(BattleEnd.CAUGHT, Gen3Nuzlocke.snapshot(after)!!.end)
    }

    @Test
    fun `a shiny wild Pokemon is told from its personality value and the player's ids`() {
        assertTrue(inBattle(world(shinyEnemy = true)).second.nuz!!.enemyShiny)
        assertFalse(inBattle(world(shinyEnemy = false)).second.nuz!!.enemyShiny)
    }

    @Test
    fun `a trainer battle names the trainer, their boss slot and their team's top level`() {
        val m = world(trainerId = 265)
        // Roxanne's entry in gTrainers: two Pokemon, the party at 0x08400000, levels 12 and 22.
        val entry = ByteArray(map.trainerLayout.size)
        entry[map.trainerLayout.partySizeOffset] = 2
        entry.putU32(map.trainerLayout.partyPtrOffset, 0x08400000L)
        m.poke(map.gTrainers + 265L * map.trainerLayout.size, entry)
        val party = ByteArray(16)
        party[2] = 12; party[4] = 74
        party[8 + 2] = 22; party[8 + 4] = 75
        m.poke(0x08400000L, party)
        val (t, s) = inBattle(m)
        assertTrue(s.inBattle && !s.isWildBattle)
        val opp = assertNotNull(s.nuz!!.opponent)
        assertEquals(265, opp.trainerId)
        assertEquals("gym1", opp.bossKey)
        assertEquals("Gym", opp.group)
        assertEquals(22, opp.maxLevel)
        assertEquals("GymLeader1", opp.label, "with no class name or name in the ROM the tracker's own class stands in")
        // The same trainer again is answered from the cache.
        assertEquals(opp, t.read().nuz!!.opponent)
    }

    @Test
    fun `beaten bosses come from the badges, and the League from trainer flags once all eight are held`() {
        val m = world()
        val (t, s) = inBattle(m)
        assertEquals(setOf("gym1", "gym2", "gym3"), s.nuz!!.beaten)
        // All eight badges and Sidney's flag (trainer 261 at flag 0x500 + 261 in the flag block).
        m.u16(sb1 + map.badgeOffset, 0xFF shl 7)
        val flagBit = map.trainerFlagStart + 261
        val flags = ByteArray(0x300)
        flags[flagBit / 8] = (1 shl (flagBit % 8)).toByte()
        m.poke(sb1 + map.gameFlagsOffset, flags)
        val all = t.read().nuz!!.beaten
        assertEquals((1..8).map { "gym$it" } + "e4-1", all.toList())
    }

    @Test
    fun `with the trainer data unreadable the caps table is the standard one and says so`() {
        val t = GbaTracker(MemoryReader { _, _ -> ByteArray(0) }, map)
        val caps = assertNotNull(t.levelCaps())
        assertFalse(caps.fromRom || caps.mixed)
        assertEquals(15, caps.byKey("gym1")!!.cap)
        assertEquals(46, caps.byKey("gym8")!!.cap)
    }

    @Test
    fun `a boss whose team can be read takes its cap from the game, the rest keep the table`() {
        val m = Mem()
        val entry = ByteArray(map.trainerLayout.size)
        entry[map.trainerLayout.partySizeOffset] = 2
        entry.putU32(map.trainerLayout.partyPtrOffset, 0x08400000L)
        m.poke(map.gTrainers + 265L * map.trainerLayout.size, entry)
        val party = ByteArray(16); party[2] = 12; party[4] = 74; party[8 + 2] = 22; party[8 + 4] = 75
        m.poke(0x08400000L, party)
        val caps = GbaTracker(m.reader(), map).levelCaps()!!
        assertEquals(22, caps.byKey("gym1")!!.cap)
        assertTrue(caps.byKey("gym1")!!.fromRom)
        assertEquals(19, caps.byKey("gym2")!!.cap)
        assertFalse(caps.byKey("gym2")!!.fromRom)
        assertTrue(caps.mixed)
    }

    @Test
    fun `a game with no trainer table gets the standard caps and no read is tried`() {
        val t = GbaTracker(MemoryReader { _, _ -> ByteArray(0) }, map.copy(gTrainers = 0))
        val caps = assertNotNull(t.levelCaps())
        assertFalse(caps.fromRom)
        assertEquals(58, caps.byKey("champion")!!.cap)
    }

    @Test
    fun `a game the table does not cover has no caps, and a Game Boy state has no reads at all`() {
        assertNull(GbaTracker(MemoryReader { _, _ -> ByteArray(0) }, map.copy(routeVersion = "")).levelCaps())
        assertNull(TrackerState(partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false).nuz)
        assertNull(Gen3Nuzlocke.snapshot(TrackerState(partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false)))
    }

    @Test
    fun `the same game read again is the same state, so the panel is not redrawn for nothing`() {
        // The Compose side only hears about a state that differs from the last one. A field that changed on
        // every poll (a table rebuilt each time, a counter) would redraw the whole tracker five times a second.
        val (t, first) = inBattle(world(trainerId = 265))
        assertEquals(first, t.read())
        assertEquals(first, t.read())
        assertEquals(first.nuz!!.caps, t.read().nuz!!.caps)
        // And with no battle on the field: the poll after a battle carries the bag once, the ones after it do not.
        val m = world()
        m.u8(map.battlersCount, 0)
        val idle = GbaTracker(m.reader(), map)
        idle.read(); idle.read()
        val settled = idle.read()
        assertFalse(settled.inBattle)
        assertEquals(settled, idle.read())
        assertEquals(settled, idle.read())
    }

    @Test
    fun `an unreadable bag gives no ball count instead of none`() {
        val m = world()
        m.blocks.remove(map.saveBlock1Ptr)
        assertEquals(-1, inBattle(m).second.nuz!!.ballCount)
    }

    @Test
    fun `the map section and type are read with the map's layout id, and never paired with another map's`() {
        // rc32 audit P2 #140: gMapHeader +0x12 layout id, +0x14 regionMapSectionId, +0x17 mapType.
        val m = world()
        m.poke(map.mapHeader + 0x12, byteArrayOf(8, 0, 0x66, 0, 0, 8))
        val t = GbaTracker(m.reader(), map)
        t.read()
        val n = assertNotNull(t.read().nuz)
        assertEquals(0x66, n.mapSection)
        assertEquals(8, n.mapType)
        // The next map's header, read once: its layout id is not adopted yet, so neither is its section.
        m.poke(map.mapHeader + 0x12, byteArrayOf(9, 0, 0x5B, 0, 0, 2))
        val moving = assertNotNull(t.read().nuz)
        assertEquals(-1, moving.mapSection)
        assertEquals(-1, moving.mapType)
        val there = assertNotNull(t.read().nuz)
        assertEquals(0x5B, there.mapSection)
        assertEquals(2, there.mapType)
    }
}
