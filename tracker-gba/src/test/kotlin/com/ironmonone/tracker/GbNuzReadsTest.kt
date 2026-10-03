package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.Origin
import com.ironmonone.tracker.nuzlocke.Outcome
import com.ironmonone.tracker.nuzlocke.RunMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Game Boy trackers' Nuzlocke reads through a fake WRAM and a fake ROM (2026-09-30): each byte the rules read is
 * put at the address the disassembly gives it (the same offsets the trackers use), and the whole path is driven, from
 * memory to the ledger, the way the Play screen drives it. Nothing here proves the addresses are right on a device:
 * that needs the game running. What it proves is that the tracker reads what it says it reads, and that the reads
 * make the engine do the right thing.
 */
class GbNuzReadsTest {

    /** 8 KB, a Red or Blue work RAM on the core, unless told: a bigger fake hid raw addresses (rc32 audit P3 #117). */
    private class Ram(private val base: Long, size: Int = 0x2000) : MemoryReader {
        val bytes = ByteArray(size)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        fun be16(off: Long, v: Int) { put(off, v shr 8); put(off + 1, v and 0xFF) }
        /** A name in the games' text, padded with the terminator to [width]. */
        fun name(off: Long, s: String, width: Int = 11) {
            for (i in 0 until width) put(off + i, 0x50)
            s.forEachIndexed { i, c -> put(off + i, when (c) { in 'A'..'Z' -> 0x80 + (c - 'A'); in 'a'..'z' -> 0xA0 + (c - 'a'); ' ' -> 0x7F; else -> 0xE6 }) }
        }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - base).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    private fun header(r: ByteArray, title: String, cgb: Int) {
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = cgb.toByte()
    }

    private fun newRun(system: NuzlockeSystem, rules: NuzlockeRules = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)): Pair<NuzlockeLedger, NuzlockeEngine> {
        val ledger = NuzlockeLedger(RunMeta("nz-gb", "lib-gb", "Test game", rules, 1_000L).also { it.system = system })
        return ledger to NuzlockeEngine(ledger)
    }

    // =============================================================== Generation 1

    private val rb = Gen1Map.RED_BLUE
    private val yellow = Gen1Map.YELLOW

    /** Internal 0xB1 is Charmander (dex 4) and 0x03 Pikachu (dex 25) in this ROM, on purpose, as in Gen1TrackerTest. */
    private fun rom1(map: Gen1Map, title: String, brockLevel: Int? = null): ByteArray {
        val r = ByteArray(0x50000)
        header(r, title, 0)
        r[map.dexOrder + 0xB1 - 1] = 4; r[map.dexOrder + 0x03 - 1] = 25
        fun base(dex: Int, t1: Int) {
            val b = map.baseStats + (dex - 1) * Gen1Tracker.BASE_STRIDE
            r[b] = dex.toByte(); r[b + 1] = 39; r[b + 2] = 52; r[b + 3] = 43; r[b + 4] = 65; r[b + 5] = 50
            r[b + 6] = t1.toByte(); r[b + 7] = t1.toByte()
        }
        base(4, 20); base(25, 23)
        if (brockLevel != null) {
            // Class 34 (Brock) has one team, in the bank of the trainer table: FF, then level and species pairs, and a 0 to end it.
            val bank = (map.trainerTable / 0x4000) * 0x4000
            val at = bank + 0x2100
            val p = 0x4000 + (at - bank)
            val entry = map.trainerTable + (34 - 1) * 2
            r[entry] = (p and 0xFF).toByte(); r[entry + 1] = (p shr 8).toByte()
            intArrayOf(0xFF, brockLevel - 2, 0x4E, brockLevel, 0x4F, 0).forEachIndexed { i, b -> r[at + i] = b.toByte() }
        }
        return r
    }

    private fun mon1(w: Ram, map: Gen1Map, slot: Int, internal: Int, level: Int, hp: Int, maxHp: Int, ot: Int = 0x2B0E, dvs: Int = 0xA5B6) {
        val b = map.partyMons + slot * Gen1Tracker.PARTY_STRIDE
        w.put(map.partySpecies + slot, internal)
        w.put(b, internal); w.be16(b + 1, hp)
        w.be16(b + 12, ot); w.be16(b + 27, dvs)
        w.put(b + 33, level); w.be16(b + 34, maxHp)
        w.be16(b + 36, 30); w.be16(b + 38, 31); w.be16(b + 40, 32); w.be16(b + 42, 33)
    }

    /** A game with Charmander (a starter, Lv 5), a bag with Potions, Poke Balls and a Silph Scope, on Route 1 (map 12). */
    private fun overworld1(map: Gen1Map = rb, mapId: Int = 12): Ram {
        val w = Ram(Gen1Tracker.RAM)
        w.put(map.partyCount, 1)
        mon1(w, map, 0, 0xB1, 5, 20, 20, dvs = 0x7777)
        w.put(map.partySpecies + 1, 0xFF)
        w.name(map.nicks, "EMBER")
        w.be16(map.playerId, 0x2B0E)
        w.put(map.curMap, mapId)
        w.put(map.numItems, 4)
        intArrayOf(0x14, 3, 0x04, 5, 0x03, 2, 0x48, 1, 0xFF).forEachIndexed { i, v -> w.put(map.items + i, v) }
        return w
    }

    private fun wild1(w: Ram, map: Gen1Map = rb, hp: Int = 12, level: Int = 4, dvs: Int = 0xA5B6) {
        w.put(map.inBattle, 1)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, hp); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, level); w.be16(e + 15, 18)
        w.be16(map.enemyDvs, dvs)
    }

    private fun endBattle1(w: Ram, map: Gen1Map = rb, result: Int) { w.put(map.inBattle, 0); w.put(map.battleResult, result) }

    @Test
    fun `Red reads the trainer id, the place, the balls, the bag, the style and the nicknames`() {
        val w = overworld1()
        w.put(rb.options, 0x40)
        val s = Gen1Tracker(w, rom1(rb, "POKEMON RED")).read()
        val g = assertNotNull(s.nuz?.gb)
        assertEquals(1, g.generation); assertEquals("rb", g.game); assertEquals(listOf("rb"), g.gameKeys)
        assertEquals(0x2B0E, g.playerId)
        assertEquals("Route 1", g.place); assertEquals("Route 1", g.detail); assertEquals(12, s.mapId)
        assertEquals(7, g.ballCount, "Poke Balls and Great Balls added up")
        assertEquals(listOf(20, 72), g.bag!!.keys.sorted(), "the balls are not in the bag map")
        assertEquals("POTION", g.bag!!.getValue(20).name); assertEquals(3, g.bag!!.getValue(20).qty)
        assertEquals("SILPH SCOPE", g.bag!!.getValue(72).name)
        assertEquals(true, g.battleStyleSet, "wOptions bit 6")
        assertEquals(listOf("EMBER"), g.nicknames)
        assertEquals(-1, g.turn, "no battle: no turn count"); assertEquals(-1, g.enemyDvs)
        assertNull(g.opponent)
        assertFalse(g.surfing); assertFalse(g.ghost)
        w.put(rb.options, 0x00)
        assertEquals(false, Gen1Tracker(w, rom1(rb, "POKEMON RED")).read().nuz!!.gb!!.battleStyleSet)
    }

    @Test
    fun `a map the table does not name keeps the last place that it did`() {
        val w = overworld1()
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        assertEquals("Route 1", t.read().nuz!!.gb!!.place)
        w.put(rb.curMap, 11)     // an unused map id
        val g = t.read().nuz!!.gb!!
        assertEquals("Route 1", g.place, "the last named place stands")
        w.put(rb.curMap, 0)
        assertEquals("Pallet Town", t.read().nuz!!.gb!!.place)
    }

    @Test
    fun `Yellow's addresses and rows are its own`() {
        val w = overworld1(yellow)
        w.put(yellow.options, 0x40)
        val t = Gen1Tracker(w, rom1(yellow, "POKEMON YELLOW"))
        val s = t.read()
        val g = assertNotNull(s.nuz?.gb)
        assertEquals("y", g.game); assertEquals(0x2B0E, g.playerId); assertEquals("Route 1", g.place)
        assertEquals(true, g.battleStyleSet); assertEquals(listOf("EMBER"), g.nicknames)
        assertEquals(7, g.ballCount)
    }

    @Test
    fun `a wild battle gives the enemy's DVs and its turn count, and a trainer battle its class and party number`() {
        val w = overworld1()
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED", brockLevel = 22))
        wild1(w); w.put(rb.aiTurns, 3)
        val g = t.read().nuz!!.gb!!
        assertEquals(0xA5B6, g.enemyDvs); assertEquals(3, g.turn); assertEquals(12, g.enemyHpLast); assertTrue(g.lastWild)
        assertNull(g.opponent)
        // A trainer battle: Brock's party 1. The DVs of a trainer's Pokemon are not read.
        w.put(rb.inBattle, 2); w.put(rb.trainerClass, 34); w.put(rb.trainerNo, 1)
        val s = t.read()
        val gt = s.nuz!!.gb!!
        assertEquals(-1, gt.enemyDvs); assertFalse(gt.lastWild)
        val o = assertNotNull(gt.opponent)
        assertEquals("Leader Brock", o.label); assertEquals("Gym", o.group); assertEquals("gym1", o.bossKey)
        assertEquals(LevelCapTable.pack(34, 1), o.trainerId)
        assertEquals(22, o.maxLevel, "the team's own top level, read out of the ROM")
        // The cap table has that level for Brock too, and only for the boss whose team could be read.
        val caps = assertNotNull(gt.caps)
        assertEquals(22, caps.byKey("gym1")!!.cap); assertTrue(caps.byKey("gym1")!!.fromRom)
        assertEquals(21, caps.byKey("gym2")!!.cap); assertFalse(caps.byKey("gym2")!!.fromRom)
        assertTrue(caps.mixed)
        // A party number with no row of its own reads as its class.
        w.put(rb.trainerClass, 1); w.put(rb.trainerNo, 9)
        assertEquals("Youngster", t.read().nuz!!.gb!!.opponent!!.label)
    }

    /** Brock's team of Red and Blue as the game writes it, into a ROM that had none: class 34's pointer and one team of [level]. */
    private fun putBrock(rom: ByteArray, level: Int) {
        val bank = (rb.trainerTable / 0x4000) * 0x4000
        val at = bank + 0x2100
        val p = 0x4000 + (at - bank)
        val entry = rb.trainerTable + (34 - 1) * 2
        rom[entry] = (p and 0xFF).toByte(); rom[entry + 1] = (p shr 8).toByte()
        intArrayOf(0xFF, level - 2, 0x4E, level, 0x4F, 0).forEachIndexed { i, b -> rom[at + i] = b.toByte() }
    }

    @Test
    fun `a trainer table that will not read is tried again a few times, and then the standard table stands`() {
        val rom = rom1(rb, "POKEMON RED")
        val t = Gen1Tracker(overworld1(), rom)
        repeat(3) { assertFalse(assertNotNull(t.read().nuz!!.gb!!.caps).let { it.fromRom || it.mixed }, "nothing to read yet") }
        putBrock(rom, 22)
        val later = assertNotNull(t.read().nuz!!.gb!!.caps)
        assertEquals(22, later.byKey("gym1")!!.cap, "the next look finds it")
        assertTrue(later.byKey("gym1")!!.fromRom)
        // A ROM that never gives anything up is asked about twenty times and then left alone.
        val stubborn = rom1(rb, "POKEMON RED")
        val u = Gen1Tracker(overworld1(), stubborn)
        repeat(25) { u.read() }
        putBrock(stubborn, 22)
        assertEquals(14, assertNotNull(u.read().nuz!!.gb!!.caps).byKey("gym1")!!.cap, "after twenty tries the table stands")
    }

    @Test
    fun `the standard caps stand when the ROM's trainer data will not read`() {
        val w = overworld1()
        val caps = Gen1Tracker(w, rom1(rb, "POKEMON RED")).read().nuz!!.gb!!.caps
        assertNotNull(caps)
        assertFalse(caps.fromRom); assertFalse(caps.mixed)
        assertEquals(14, caps.byKey("gym1")!!.cap)
        assertEquals(65, caps.byKey("champion")!!.cap)
    }

    @Test
    fun `a ghost is a wild battle on a Pokemon Tower floor without the Silph Scope`() {
        val w = overworld1(mapId = 145)
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        wild1(w)
        assertFalse(t.read().nuz!!.gb!!.ghost, "the scope is in the bag")
        w.put(rb.numItems, 3); w.put(rb.items + 6, 0xFF)          // the scope is gone
        val g = t.read().nuz!!.gb!!
        assertTrue(g.ghost); assertEquals("Pokemon Tower", g.place)
        w.put(rb.curMap, 12)
        assertFalse(t.read().nuz!!.gb!!.ghost, "not on a Tower floor")
        w.put(rb.curMap, 145); w.put(rb.inBattle, 2)
        assertFalse(t.read().nuz!!.gb!!.ghost, "a trainer is not a ghost")
    }

    @Test
    fun `the surf state, the battle type and the ball pocket are read as the game holds them`() {
        val w = overworld1()
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        assertFalse(t.read().nuz!!.gb!!.surfing)
        w.put(rb.surfState, 2)
        assertTrue(t.read().nuz!!.gb!!.surfing)
        w.put(rb.surfState, 1)
        assertFalse(t.read().nuz!!.gb!!.surfing, "1 is the bike")
        wild1(w); w.put(rb.battleType, 1)
        assertEquals(1, t.read().nuz!!.gb!!.battleType)
    }

    @Test
    fun `the flags of a battle are held until the next one starts, and its last look at the enemy stays`() {
        val w = overworld1()
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        wild1(w, hp = 12)
        t.read()
        w.put(rb.escaped, 1)
        t.read()
        w.put(rb.escaped, 0)                       // the game clears it; the tracker does not
        wild1(w, hp = 5)
        t.read()
        endBattle1(w, result = 0)
        val over = t.read().nuz!!.gb!!
        assertTrue(over.escaped); assertFalse(over.captured)
        assertEquals(5, over.enemyHpLast, "the last HP seen in the battle")
        assertEquals(0, over.battleResult); assertTrue(over.lastWild)
        assertEquals(BattleEnd.RAN, Gen12Nuzlocke.battleEnd(over))
        // The next battle clears them.
        wild1(w, hp = 12)
        val next = t.read().nuz!!.gb!!
        assertFalse(next.escaped); assertFalse(next.captured); assertEquals(12, next.enemyHpLast)
    }

    @Test
    fun `a Generation 1 catch is recorded, the caught Pokemon has the id its enemy had, and a run is a run`() {
        val w = overworld1()
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN1)
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()                                              // the starter, on Route 1, holding balls: the rules begin
        assertTrue(ledger.meta.started)
        assertEquals(NuzlockeSystem.GEN1, ledger.meta.system)
        wild1(w); step(); step()
        assertEquals(Outcome.IN_PROGRESS, ledger.areas.getValue("Route 1").encounter!!.outcome)
        // The ball: the game sets the caught species, the party grows, the battle ends with result 2 (ran or caught).
        w.put(rb.captured, 0x03)
        w.put(rb.partyCount, 2); mon1(w, rb, 1, 0x03, 4, 12, 18, ot = 0x2B0E, dvs = 0xA5B6); w.put(rb.partySpecies + 2, 0xFF)
        w.name(rb.nicks + 11, "SPARKY")
        step()
        endBattle1(w, result = 2); step()
        val enc = assertNotNull(ledger.areas.getValue("Route 1").encounter)
        assertEquals(Outcome.CAUGHT, enc.outcome)
        assertEquals(Gen12Nuzlocke.id(0x2B0E, 0xA5B6), enc.pid, "the enemy's id, before the catch")
        assertEquals(enc.pid, enc.monId, "and the id the caught Pokemon has in the party")
        val mon = ledger.roster.getValue(enc.pid)
        assertEquals("pikachu", mon.speciesName.lowercase())
        assertEquals(Origin.CAUGHT, mon.origin); assertNull(mon.gender); assertTrue(mon.inParty)
        assertEquals("SPARKY", mon.nickname)
    }

    @Test
    fun `a Generation 1 catch into a full party's box is written from the battle, and the Pokemon keeps its id when it is taken out`() {
        val w = overworld1()
        for (slot in 1 until 6) mon1(w, rb, slot, 0xB1, 5, 20, 20, ot = 0x2B0E, dvs = 0x1111 * slot)
        w.put(rb.partyCount, 6); w.put(rb.partySpecies + 6, 0xFF)
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN1)
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()
        wild1(w); step()
        w.put(rb.captured, 0x03)
        step()
        endBattle1(w, result = 2); step()                   // the party did not grow: it went to a box
        val id = Gen12Nuzlocke.id(0x2B0E, 0xA5B6)
        val boxed = assertNotNull(ledger.roster[id], "written from what the battle showed of the enemy")
        assertFalse(boxed.inParty)
        assertEquals(Outcome.CAUGHT, ledger.areas.getValue("Route 1").encounter!!.outcome)
        // Later it is withdrawn into the party: the same id, so it is the same roster member and not a second catch.
        mon1(w, rb, 5, 0x03, 6, 20, 20, ot = 0x2B0E, dvs = 0xA5B6)
        step()
        assertTrue(ledger.roster.getValue(id).inParty)
        assertEquals(6, ledger.roster.count { it.value.inParty }, "no new member")
    }

    @Test
    fun `a Generation 1 wild Pokemon that was knocked out, one that ran off, and one the player ran from read differently`() {
        // The game's own escape: wEscapedFromBattle set while the battle is still on screen, by whoever's move it was.
        fun escape(w: Ram, enemyMove: Int, playerMove: Int) { w.put(rb.escaped, 1); w.put(rb.enemyMove, enemyMove); w.put(rb.playerMove, playerMove) }
        val teleport = 0x64; val tackle = 0x21
        val cases = listOf(
            Triple("knocked out", { w: Ram -> }, Outcome.FAINTED),
            Triple("the player ran", { w: Ram -> }, Outcome.RAN),
            // rc33 audit P1 #75: the wild Abra's Teleport sets the flag as the player's does.
            Triple("it teleported away", { w: Ram -> escape(w, enemyMove = teleport, playerMove = tackle) }, Outcome.FLED),
            Triple("the player teleported", { w: Ram -> escape(w, enemyMove = tackle, playerMove = teleport) }, Outcome.RAN),
        )
        for ((why, during, outcome) in cases) {
            val w = overworld1()
            val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
            val (ledger, engine) = newRun(NuzlockeSystem.GEN1)
            var at = 10_000L
            fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
            step(); wild1(w, hp = if (why == "knocked out") 0 else 9); step()
            during(w); step()
            w.put(rb.inBattle, 0); w.put(rb.battleResult, if (why == "the player ran") 2 else 0); step()
            assertEquals(outcome, ledger.areas.getValue("Route 1").encounter!!.outcome, why)
        }
    }

    @Test
    fun `the escape clause reopens Route 1 after a wild Pokemon teleported away, and not after the player did`() {
        for ((enemyMove, playerMove, reopened) in listOf(Triple(0x64, 0x21, true), Triple(0x21, 0x64, false))) {
            val w = overworld1()
            val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
            val (ledger, engine) = newRun(NuzlockeSystem.GEN1, NuzlockeRules.forPreset(NuzlockePreset.STANDARD).copy(escapeClause = true))
            var at = 10_000L
            fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
            step(); wild1(w, hp = 9); step()
            w.put(rb.escaped, 1); w.put(rb.enemyMove, enemyMove); w.put(rb.playerMove, playerMove); step()
            w.put(rb.inBattle, 0); w.put(rb.battleResult, 0); step()
            val area = ledger.areas.getValue("Route 1")
            assertEquals(reopened, area.encounter == null, "enemy $enemyMove, you $playerMove: ${area.encounter?.outcome}")
        }
    }

    @Test
    fun `Brock's battle in Red starts under the cap of the team the ROM holds, and beating him is remembered`() {
        val w = overworld1()
        // The starter is level 5; a cap of 22 from the ROM is well above it, so no warning, and the win is recorded.
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED", brockLevel = 22))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN1, NuzlockeRules.forPreset(NuzlockePreset.HARDCORE))
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()
        mon1(w, rb, 0, 0xB1, 25, 60, 60)
        step()
        w.put(rb.inBattle, 2); w.put(rb.trainerClass, 34); w.put(rb.trainerNo, 1)
        val e = rb.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 40); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 22); w.be16(e + 15, 40)
        step()
        assertEquals(1, ledger.warnings.count { it.kind == com.ironmonone.tracker.nuzlocke.WarnKind.CAP }, "Lv 25 against Brock's 22")
        w.put(rb.inBattle, 0); w.put(rb.battleResult, 0); w.put(rb.badges, 1)
        step()
        assertEquals(setOf("gym1"), ledger.meta.beatenBosses)
    }

    /** rc32 audit P3 #106: NIDORAN and the male sign (pokered data/pokemon/names.asm:5) reads NIDORAN; species.tsv says NIDORAN M. */
    @Test
    fun `a male Nidoran caught and left with its default name is reminded to be named`() {
        val w = overworld1()
        val rom = rom1(rb, "POKEMON RED").also { it[rb.dexOrder + 0x03 - 1] = 32 }     // internal 0x03, NIDORAN_M in pokered too
        val t = Gen1Tracker(w, rom)
        val (ledger, engine) = newRun(NuzlockeSystem.GEN1)
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()
        wild1(w); step(); step()
        w.put(rb.captured, 0x03)
        w.put(rb.partyCount, 2); mon1(w, rb, 1, 0x03, 4, 12, 18, ot = 0x2B0E, dvs = 0xA5B6); w.put(rb.partySpecies + 2, 0xFF)
        intArrayOf(0x8D, 0x88, 0x83, 0x8E, 0x91, 0x80, 0x8D, 0xEF, 0x50, 0x50, 0x50).forEachIndexed { i, b -> w.put(rb.nicks + 11 + i, b) }
        step()
        endBattle1(w, result = 2); step()
        val mon = ledger.roster.getValue(Gen12Nuzlocke.id(0x2B0E, 0xA5B6))
        assertEquals("NIDORAN M", mon.speciesName.uppercase())
        assertFalse(mon.hasNickname, "its own name is no nickname")
        assertTrue(ledger.warnings.any { it.kind == com.ironmonone.tracker.nuzlocke.WarnKind.NICKNAME && it.id == "nickname:${mon.id}" },
            "the reminder to name it")
    }

    /**
     * rc32 audit P3 #111: a slot that fails to decode for one read (caught mid-copy) ends the party list there. The snapshot
     * takes the game's own count, so the engine does not take the short list for the whole party: nobody goes to a box, and
     * a fainted lead beside it is no whiteout.
     */
    @Test
    fun `a party slot that fails to decode for one read boxes nobody and is no whiteout`() {
        val w = overworld1()
        w.put(rb.partyCount, 3)
        for (slot in 0 until 3) mon1(w, rb, slot, 0xB1, 5 + slot, 20, 20, ot = 0x2B0E, dvs = 0x1111 * (slot + 1))
        w.put(rb.partySpecies + 3, 0xFF)
        val t = Gen1Tracker(w, rom1(rb, "POKEMON RED"))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN1)
        engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), 10_000L)
        assertTrue(ledger.meta.started)
        assertEquals(3, ledger.roster.count { it.value.inParty })
        // The lead and the third at 0 HP, the second read with level 0, which partyMon refuses.
        mon1(w, rb, 0, 0xB1, 5, 0, 20, ot = 0x2B0E, dvs = 0x1111)
        mon1(w, rb, 2, 0xB1, 7, 0, 20, ot = 0x2B0E, dvs = 0x3333)
        w.put(rb.partyMons + Gen1Tracker.PARTY_STRIDE + 33, 0)
        val s = t.read()
        assertEquals(1, s.party.size)
        val snap = assertNotNull(NuzlockeAdapters.snapshot(s))
        assertEquals(3, snap.partyCount, "the game's count, not the length of the list")
        engine.update(snap, 10_700L)
        assertEquals(3, ledger.roster.count { it.value.inParty }, "nobody went to a box")
        assertTrue(ledger.events.none { it.kind == "whiteout" })
    }

    // =============================================================== Generation 2

    private val crystal = Gen2Map.CRYSTAL
    private val gs = Gen2Map.GS

    private fun rom2(title: String, cgb: Int, map: Gen2Map, falknerLevel: Int? = null): ByteArray {
        val r = ByteArray(0x60000)
        header(r, title, cgb)
        fun base(species: Int, ratio: Int, t1: Int) {
            val b = GbcTracker.BASE_STATS + (species - 1) * GbcTracker.BASE_STRIDE
            r[b] = species.toByte(); r[b + 1] = 39; r[b + 2] = 52; r[b + 3] = 43; r[b + 4] = 65; r[b + 5] = 60; r[b + 6] = 50
            r[b + 7] = t1.toByte(); r[b + 8] = t1.toByte(); r[b + 13] = ratio.toByte()
        }
        base(155, 31, 20)        // Cyndaquil: one female in eight
        base(161, 127, 0)        // Sentret: even odds
        base(100, 255, 23)       // Voltorb: no gender
        if (falknerLevel != null) {
            // Class 1 (Falkner): one team. The team's name, 0x50, its type (0: level and species), the pairs, then 0xFF.
            val bank = (map.trainerTable / 0x4000) * 0x4000
            val at = bank + 0x2800
            val p = 0x4000 + (at - bank)
            r[map.trainerTable] = (p and 0xFF).toByte(); r[map.trainerTable + 1] = (p shr 8).toByte()
            intArrayOf(0x85, 0x80, 0x8B, 0x82, 0x8E, 0x8D, 0x50, 0, falknerLevel - 2, 16, falknerLevel, 17, 0xFF).forEachIndexed { i, b -> r[at + i] = b.toByte() }
        }
        return r
    }

    private fun mon2(w: Ram, slot: Int, species: Int, level: Int, hp: Int, maxHp: Int, ot: Int = 0x2B0E, dvs: Int = 0xA5B6) {
        val b = GbcTracker.PARTY_MONS + slot * GbcTracker.PARTY_STRIDE
        w.put(GbcTracker.PARTY_SPECIES + slot, species)
        w.put(b, species)
        w.be16(b + 6, ot); w.be16(b + 21, dvs)
        w.put(b + 31, level)
        w.be16(b + 34, hp); w.be16(b + 36, maxHp)
        w.be16(b + 38, 30); w.be16(b + 40, 31); w.be16(b + 42, 32); w.be16(b + 44, 33); w.be16(b + 46, 34)
    }

    /** A Crystal game with a starter on Route 29 (landmark 2), a ball pocket and a bag. */
    private fun overworld2(): Ram {
        val w = Ram(GbcTracker.RAM, 0x8000)
        w.put(GbcTracker.PARTY_COUNT, 1)
        mon2(w, 0, 155, 5, 20, 20, dvs = 0xF7F7)
        w.put(GbcTracker.PARTY_SPECIES + 1, 0xFF)
        w.name(crystal.nicks, "EMBER")
        w.be16(crystal.playerId, 0x2B0E)
        w.put(GbcTracker.CUR_LANDMARK, 2)
        w.put(GbcTracker.NUM_ITEMS, 1); w.put(GbcTracker.ITEMS, 18); w.put(GbcTracker.ITEMS + 1, 3); w.put(GbcTracker.ITEMS + 2, 0xFF)
        w.put(crystal.numBalls, 2)
        intArrayOf(5, 4, 4, 2, 0xFF).forEachIndexed { i, v -> w.put(crystal.balls + i, v) }
        return w
    }

    /** The ball catches: wWildMon holds the species from that throw until the battle is over. */
    private fun ball2(w: Ram, species: Int = 161) = w.put(crystal.wildMon, species)

    private fun wild2(w: Ram, species: Int = 161, hp: Int = 12, level: Int = 4, dvs: Int = 0xA5B6, type: Int = 0) {
        w.put(GbcTracker.BATTLE_MODE, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, species); w.put(e + 13, level); w.be16(e + 16, hp); w.be16(e + 18, 18)
        w.be16(crystal.enemyDvs, dvs)
        w.put(crystal.battleType, type)
    }

    @Test
    fun `Crystal reads the trainer id, the place, the balls, the bag, the style and the nicknames`() {
        val w = overworld2()
        w.put(crystal.options, 0x40)
        val s = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read()
        val g = assertNotNull(s.nuz?.gb)
        assertEquals(2, g.generation); assertEquals("c", g.game); assertEquals(listOf("c"), g.gameKeys)
        assertEquals(0x2B0E, g.playerId)
        assertEquals("Route 29", g.place); assertEquals(2, s.mapId)
        assertEquals(6, g.ballCount, "the ball pocket's quantities added up")
        assertEquals(listOf(18), g.bag!!.keys.toList()); assertEquals("POTION", g.bag!!.getValue(18).name)
        assertEquals(true, g.battleStyleSet)
        assertEquals(listOf("EMBER"), g.nicknames)
        assertEquals(-1, g.turn)
        assertFalse(g.surfing)
        w.put(crystal.playerState, 4)
        assertTrue(GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read().nuz!!.gb!!.surfing, "PLAYER_SURF")
        w.put(crystal.playerState, 8)
        assertTrue(GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read().nuz!!.gb!!.surfing, "PLAYER_SURF_PIKA")
        w.put(crystal.playerState, 1)
        assertFalse(GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read().nuz!!.gb!!.surfing, "the bike")
    }

    @Test
    fun `Crystal's catch flag is held after the game clears it, and the next battle starts clean`() {
        val w = overworld2()
        val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal))
        wild2(w, hp = 12); t.read()
        ball2(w); t.read()                                // the throw that catches
        w.put(crystal.wildMon, 0)                         // the battle ends and the game lets go of it
        w.put(GbcTracker.BATTLE_MODE, 0); w.put(crystal.battleResult, 0)
        val over = t.read().nuz!!.gb!!
        assertTrue(over.captured)
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(over))
        wild2(w, hp = 12)
        assertFalse(t.read().nuz!!.gb!!.captured, "a new battle has caught nothing yet")
    }

    @Test
    fun `a look that could not read the enemy keeps what the last one saw`() {
        val t = GbNuzTracker(1, "rb", listOf("rb"), ByteArray(0x50000), 0)
        t.look(inBattle = true, wild = true, enemyHp = 9, escapedNow = false, capturedNow = false)
        t.look(inBattle = true, wild = true, enemyHp = null, escapedNow = false, capturedNow = false)
        assertEquals(9, t.lastEnemyHp)
        t.look(inBattle = false, wild = true, enemyHp = null, escapedNow = false, capturedNow = false)
        assertEquals(9, t.lastEnemyHp, "and once the battle is over")
    }

    @Test
    fun `an empty ball pocket is no balls, and one that cannot be read is not counted`() {
        val w = overworld2()
        w.put(crystal.numBalls, 0)
        assertEquals(0, GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read().nuz!!.gb!!.ballCount)
        // The reader stops at 12 entries, which is more than the pocket can hold.
        w.put(crystal.numBalls, 200)
        assertTrue(GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read().nuz!!.gb!!.ballCount >= 0)
    }

    @Test
    fun `Gold and Silver are told apart by their header, and both use the rows the two share`() {
        for ((title, first) in listOf("POKEMON_GLD" to "g", "POKEMON_SLV" to "s")) {
            val w = Ram(GbcTracker.RAM, 0x8000)
            w.put(GbcTracker.PARTY_COUNT, 0)
            w.be16(gs.mapGroup, 0x0102)                       // group 1, map 2 (Gold and Silver: no landmark)
            val s = GbcTracker(w, rom2(title, 0x00, gs), gs).read()
            val g = assertNotNull(s.nuz?.gb, title)
            assertEquals(first, g.game, title); assertEquals(listOf(first, "gs"), g.gameKeys, title)
            assertEquals(0x0102, s.mapId, title)
        }
    }

    @Test
    fun `Generation 2 gender comes from the ROM's ratio and the DVs, and shininess from the DVs`() {
        val w = overworld2()
        w.put(GbcTracker.PARTY_COUNT, 3)
        mon2(w, 1, 161, 5, 18, 18, dvs = 0x0707)              // Sentret, attack 0 and speed 0: female
        mon2(w, 2, 155, 5, 18, 18, dvs = (2 shl 12) or (10 shl 8) or (10 shl 4) or 10)
        w.put(GbcTracker.PARTY_SPECIES + 3, 0xFF)
        val s = assertNotNull(NuzlockeAdapters.snapshot(GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read()))
        assertEquals(listOf(Gender.MALE, Gender.FEMALE, Gender.MALE), s.party.map { it.gender }, "0xF7F7: b = 255 against 31; 0x0707: b = 0 against 127; 0x2AAA: b = 42 against 31")
        assertEquals(listOf(false, false, true), s.party.map { it.shiny })
    }

    @Test
    fun `an egg keeps its slot and its nickname is not given to the Pokemon after it`() {
        val w = overworld2()
        w.put(GbcTracker.PARTY_COUNT, 3)
        mon2(w, 1, 175, 5, 20, 20)
        w.put(GbcTracker.PARTY_SPECIES + 1, 0xFD)              // an egg, as the species list shows one
        mon2(w, 2, 161, 5, 18, 18, dvs = 0x1234)
        w.put(GbcTracker.PARTY_SPECIES + 3, 0xFF)
        w.name(crystal.nicks + 11, "EGG"); w.name(crystal.nicks + 22, "SENTY")
        val s = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal)).read()
        assertEquals(listOf(155, 161), s.party.map { it.mon.species })
        assertEquals(listOf("EMBER", "SENTY"), s.nuz!!.gb!!.nicknames, "each Pokemon has the nickname of its own slot")
    }

    /**
     * rc32 audit P2 #140, its Game Boy Color part: the tracker's party leaves eggs out, so a Crystal egg was first seen as
     * it hatched, and with 'Gifts count' on the Pokemon used up the route it hatched on. The eggs reach the engine now,
     * each with the id its Pokemon will have, since Crystal's Odd Egg carries another trainer's id until it hatches.
     */
    @Test
    fun `a Crystal egg uses up the place it was received, not the route it hatches on`() {
        val w = overworld2()                                       // Cyndaquil on Route 29, trainer id 0x2B0E
        val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN2, NuzlockeRules.forPreset(NuzlockePreset.STANDARD).copy(giftsCount = true))
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()
        assertTrue(ledger.meta.started)
        // The Day-Care man on Route 34 gives the Odd Egg: a level 5 Pichu with trainer id 2048 and no HP, as the game's
        // own table has it (pokecrystal data/events/odd_eggs.asm).
        w.put(GbcTracker.CUR_LANDMARK, 15)
        w.put(GbcTracker.PARTY_COUNT, 2)
        mon2(w, 1, 172, 5, 0, 17, ot = 2048, dvs = 0x1234)
        w.put(GbcTracker.PARTY_SPECIES + 1, 0xFD); w.put(GbcTracker.PARTY_SPECIES + 2, 0xFF)
        val snap = assertNotNull(NuzlockeAdapters.snapshot(t.read()))
        assertEquals(listOf(false, true), snap.party.map { it.isEgg })
        assertEquals(2, snap.partyCount, "the egg's slot is in the count")
        step()
        val id = Gen12Nuzlocke.id(0x2B0E, 0x1234)
        assertEquals("Route 34", ledger.meta.eggs[id]?.areaName, "the egg is followed from where it joined")
        assertNull(ledger.roster[id], "an egg is nobody on the roster")
        // It hatches on Route 35: the species list names it, and the game writes the player's id into it.
        w.put(GbcTracker.CUR_LANDMARK, 18)
        step(); step()
        mon2(w, 1, 172, 5, 17, 17, ot = 0x2B0E, dvs = 0x1234)
        step()
        val pichu = ledger.roster.getValue(id)
        assertEquals(Origin.GIFT, pichu.origin)
        assertEquals("Route 34", pichu.areaName)
        assertEquals(id, assertNotNull(ledger.areas["Route 34"]?.encounter).monId, "the place it was received is used")
        assertNull(ledger.areas["Route 35"]?.encounter, "the route it hatched on stays open")
        assertEquals(2, ledger.roster.size, "the starter and the Pichu, one Pokemon each")
    }

    @Test
    fun `Generation 2 fishing, a headbutt tree and a roamer are told from walking by the battle type`() {
        val w = overworld2()
        val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal))
        fun method(type: Int): Method {
            wild2(w, type = type)
            return assertNotNull(NuzlockeAdapters.snapshot(t.read())).method
        }
        assertEquals(Method.WALK, method(0))
        assertEquals(Method.ROD, method(4))
        assertEquals(Method.HEADBUTT, method(8))
        assertEquals(Method.STATIC, method(5))
        assertEquals(Method.STATIC, method(6))
        assertEquals(Method.STATIC, method(12), "Suicune")
        wild2(w, type = 3)
        assertTrue(assertNotNull(NuzlockeAdapters.snapshot(t.read())).ghost, "the tutorial is not an encounter")
    }

    @Test
    fun `a Generation 2 catch is a win with the wild Pokemon standing, a knockout is one with it down`() {
        for (caught in listOf(true, false)) {
            val w = overworld2()
            val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal))
            val (ledger, engine) = newRun(NuzlockeSystem.GEN2)
            var at = 10_000L
            fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
            step()
            assertTrue(ledger.meta.started)
            wild2(w); step(); step()
            if (caught) {
                ball2(w)
                w.put(GbcTracker.PARTY_COUNT, 2); mon2(w, 1, 161, 4, 12, 18); w.put(GbcTracker.PARTY_SPECIES + 2, 0xFF)
                w.name(crystal.nicks + 11, "SENTY")
                step()
            } else wild2(w, hp = 0).also { step() }
            w.put(GbcTracker.BATTLE_MODE, 0); w.put(crystal.wildMon, 0); w.put(crystal.battleResult, 0); step()
            val enc = assertNotNull(ledger.areas.getValue("Route 29").encounter)
            assertEquals(if (caught) Outcome.CAUGHT else Outcome.FAINTED, enc.outcome)
            if (caught) {
                assertEquals(Gen12Nuzlocke.id(0x2B0E, 0xA5B6), enc.monId, "the id of the enemy is the id of the catch")
                assertEquals(Gender.MALE, ledger.roster.getValue(enc.monId!!).gender, "0xA5B6: b = 0xA6 against 127")
            }
        }
    }

    @Test
    fun `a Generation 2 draw in the wild is read as a run, and in a trainer battle as a draw`() {
        val w = overworld2()
        val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal))
        wild2(w); t.read()
        w.put(GbcTracker.BATTLE_MODE, 0); w.put(crystal.battleResult, 2)
        assertEquals(BattleEnd.RAN, assertNotNull(NuzlockeAdapters.snapshot(t.read())).end)
        w.put(GbcTracker.BATTLE_MODE, 2)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 13, 9); w.be16(e + 16, 20); w.be16(e + 18, 20)
        t.read()
        w.put(GbcTracker.BATTLE_MODE, 0); w.put(crystal.battleResult, 2)
        assertEquals(BattleEnd.DREW, assertNotNull(NuzlockeAdapters.snapshot(t.read())).end)
    }

    @Test
    fun `Falkner's battle in Crystal is identified, capped by the ROM's own team, and remembered when it is won`() {
        val w = overworld2()
        val t = GbcTracker(w, rom2("PM_CRYSTAL", 0xC0, crystal, falknerLevel = 14))
        val (ledger, engine) = newRun(NuzlockeSystem.GEN2, NuzlockeRules.forPreset(NuzlockePreset.HARDCORE))
        var at = 10_000L
        fun step() { engine.update(assertNotNull(NuzlockeAdapters.snapshot(t.read())), at); at += 700 }
        step()
        mon2(w, 0, 155, 18, 60, 60, dvs = 0xF7F7)
        step()
        w.put(GbcTracker.BATTLE_MODE, 2); w.put(crystal.trainerClass, 1); w.put(crystal.trainerNo, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 13, 14); w.be16(e + 16, 40); w.be16(e + 18, 40)
        val s = t.read()
        val o = assertNotNull(s.nuz!!.gb!!.opponent)
        assertEquals("Leader Falkner", o.label); assertEquals("Gym", o.group); assertEquals("gym1", o.bossKey); assertEquals(14, o.maxLevel)
        val caps = assertNotNull(s.nuz!!.gb!!.caps)
        assertEquals(14, caps.byKey("gym1")!!.cap); assertTrue(caps.byKey("gym1")!!.fromRom, "the standard table says 9")
        step()
        assertEquals(1, ledger.warnings.count { it.kind == com.ironmonone.tracker.nuzlocke.WarnKind.CAP }, "Lv 18 against 14")
        w.put(GbcTracker.BATTLE_MODE, 0); w.put(crystal.battleResult, 0); w.put(GbcTracker.JOHTO_BADGES, 1)
        step()
        assertEquals(setOf("gym1"), ledger.meta.beatenBosses)
    }

    @Test
    fun `Gold reads its trainer table from Gold's offset`() {
        val w = Ram(GbcTracker.RAM, 0x8000)
        w.put(GbcTracker.PARTY_COUNT, 0)
        w.put(gs.trainerClass, 1); w.put(gs.trainerNo, 1); w.put(gs.battleMode, 2)
        // A Gold ROM with Falkner's team written where Gold's table is, and nothing at Crystal's.
        val rom = rom2("POKEMON_GLD", 0x00, gs, falknerLevel = 13)
        val caps = GbcTracker(w, rom, gs).read().nuz!!.gb!!.caps
        assertEquals(13, assertNotNull(caps).byKey("gym1")!!.cap)
        assertTrue(caps.byKey("gym1")!!.fromRom)
    }
}
