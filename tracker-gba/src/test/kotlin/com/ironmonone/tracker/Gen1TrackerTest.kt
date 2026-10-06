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

    /** The core's 8 KB work RAM: a 64 KB fake hid raw addresses (rc32 audit P3 #117). */
    private class Wram : MemoryReader {
        val bytes = ByteArray(0x2000)
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

    /**
     * RC35-NOTICED N #22: Gen1Map.forRom cut the header title at a NUL that was written into the source as the raw byte,
     * so git kept Gen1Tracker.kt as a binary file, with no text diffs and no line end conversion. It is the escape now,
     * and the title still stops at its padding.
     */
    @Test
    fun `no tracker source holds a raw NUL byte, and the header title still stops at its padding`() {
        val sources = listOf(java.io.File("src/main/kotlin"), java.io.File("src/test/kotlin"))
            .flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue(sources.size > 50, "the sources were found")
        assertEquals(emptyList(), sources.filter { f -> f.readBytes().any { it == 0.toByte() } }.map { it.name })
        assertSame(Gen1Map.YELLOW, Gen1Map.forRom(rom(Gen1Map.YELLOW, "POKEMON YELLOW", 0x80)))
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
        w.put(map.enemyMove, 84); w.put(0x0CD5L, 1)     // it has moved: wAILayer2Encouragement
        assertEquals(listOf("thundershock"), t.read().enemy!!.movesSeen.map { it.lowercase() })
        w.put(map.enemyMove, 52)                       // a move the opponent does not know is not recorded
        assertEquals(1, t.read().enemy!!.movesSeen.size)
        w.put(map.inBattle, 2)
        s = t.read(); assertTrue(s.inBattle && !s.isWildBattle)
        w.put(map.inBattle, 0)
        s = t.read(); assertTrue(!s.inBattle); assertNull(s.enemy)
    }

    /**
     * The Gen 1 reference records the move byte only once its turn counter
     * (wAILayer2Encouragement, 0xCCD5) has left 0, and only when it is one of
     * the moves the opponent had while the counter was 0 (Battle.lua:791-835).
     */
    @Test
    fun `an opponent's move counts only after it has moved, and only from its moves at the start`() {
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)
        w.put(map.inBattle, 1)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 8, 84); w.put(e + 9, 102)   // Thunder Shock, Mimic
        w.put(e + 14, 4); w.be16(e + 15, 18)
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        fun seen() = t.read().enemy!!.movesSeen.map { it.lowercase() }
        w.put(map.enemyMove, 84); w.put(0x0CD5L, 0)
        assertEquals(emptyList(), seen(), "Thunder Shock left in the byte by the last battle: this Pikachu has not moved")
        w.put(0x0CD5L, 1)
        assertEquals(listOf("thundershock"), seen())
        w.put(e + 9, 55); w.put(map.enemyMove, 55); w.put(0x0CD5L, 2)   // Mimic took Water Gun
        assertEquals(listOf("thundershock"), seen(), "a move Mimic copied is not Pikachu's own")
    }

    /**
     * Battle.updateBattleStatus (Gen 1 reference Battle.lua:589-624): party slot
     * 1 at 0 HP is a loss once wIsInBattle reads 0. Mid-battle it is not, nor
     * while the byte holds pokered's LOST_BATTLE (0xFF) before the blackout,
     * and no other slot counts.
     */
    @Test
    fun `a dead lead is a loss only once wIsInBattle reads 0, and only the lead counts`() {
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)
        w.put(map.inBattle, 2)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 4); w.be16(e + 15, 18)
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        assertTrue(t.read().inBattle)
        w.be16(map.partyMons + 1, 0)                          // the lead faints
        assertNull(t.read().gameOver, "mid-battle")
        w.put(map.inBattle, 0xFF)
        assertNull(t.read().gameOver, "LOST_BATTLE is not 0")
        w.put(map.inBattle, 0)
        assertEquals(GameOver.LOST, t.read().gameOver)
        w.be16(map.partyMons + 1, 30)                         // healed lead, second slot fainted
        w.be16(map.partyMons + Gen1Tracker.PARTY_STRIDE + 1, 0)
        assertNull(t.read().gameOver, "only slot 1 counts")
    }

    @Test
    fun `the player's condition is used, at the references' time`() {
        // Blake, 2026-09-29: full control. A Standard run ends on the entire party, still only
        // once wIsInBattle reads 0.
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        t.lossCondition = LossCondition.ENTIRE_PARTY
        w.be16(map.partyMons + 1, 0)                          // the lead faints, others alive
        assertNull(t.read().gameOver, "entire party: the lead alone is not a loss")
        val count = t.read().party.size
        for (k in 0 until count) w.be16(map.partyMons + k * Gen1Tracker.PARTY_STRIDE + 1, 0)
        w.put(map.inBattle, 2)
        assertNull(t.read().gameOver, "never mid-battle")
        w.put(map.inBattle, 0)
        assertEquals(GameOver.LOST, t.read().gameOver)
    }

    /**
     * "Lv.12 (16)" on the cards (Gen 1 reference TrackerScreen.lua:722-765): the
     * reference's evolution, ready a level early, and a stone evolution ready
     * while that stone, by Gen 1's own item id, is in the bag. The opponent's
     * is the same text in the default colour.
     */
    @Test
    fun `the cards carry the evolution text, and a Thunder Stone in the bag readies Pikachu`() {
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)                 // Charmander Lv.12 (16), Pikachu Lv.5 (THUNDER)
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        var s = t.read()
        assertEquals(EvoText.Label("16", EvoText.Tone.WAITING), s.party[0].evo)
        assertEquals(EvoText.Label("THUNDER", EvoText.Tone.WAITING), s.party[1].evo)
        w.put(map.numItems, 3); w.put(map.items + 4, 0x21); w.put(map.items + 5, 1); w.put(map.items + 6, 0xFF)
        s = t.read()
        assertEquals(EvoText.Label("THUNDER", EvoText.Tone.READY), s.party[1].evo, "THUNDER_STONE, \$21")
        w.put(map.items + 4, 96)
        assertEquals(EvoText.Tone.WAITING, t.read().party[1].evo?.tone, "96 is Gen 3's Thunder Stone, not Gen 1's")
        w.put(map.partyMons + 33, 15)
        assertEquals(EvoText.Label("16", EvoText.Tone.READY), t.read().party[0].evo, "Lv.15, one short of 16")
        w.put(map.inBattle, 1)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 4); w.be16(e + 15, 18)
        assertEquals(EvoText.Label("THUNDER", EvoText.Tone.PLAIN), t.read().enemy?.evo)
    }

    /**
     * In a battle (Gen 1 reference Battle.lua:695-761, DataHelper.lua:277-288):
     * the stat mods at wPlayerMonStatMods and 0x14 on, 7 neutral; the enemy's
     * live PP with "Count enemy PP usage"; and "Last move: X" once the trainer's
     * next Pokemon is out, when pokered clears wAILayer2Encouragement and
     * wEnemyMoveNum together.
     */
    @Test
    fun `a battle shows the stat stages, the enemy's live PP and the last move line`() {
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)
        w.put(map.inBattle, 2)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 8, 84); w.put(e + 25, 30)
        w.put(e + 14, 4); w.be16(e + 15, 18)
        for (i in 0 until 6) { w.put(map.statMods + i, 7); w.put(map.statMods + 0x14 + i, 7) }
        w.put(map.statMods, 9)                                // your Attack +2
        w.put(map.statMods + 0x14 + 2, 6)                     // its Speed -1
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        var s = t.read()
        assertEquals(8, s.party[0].statStages["ATK"]); assertEquals(6, s.party[0].statStages["SPA"])
        assertEquals(5, s.enemy!!.statStages["SPE"]); assertNull(s.enemy!!.statStages["SPD"], "one Special in Gen 1")
        assertTrue(s.party[1].statStages.isEmpty(), "the stages are slot 1's, as the reference views it")
        w.put(map.enemyMove, 84); w.put(map.aiTurns, 1); w.put(e + 25, 29)   // Thunder Shock used
        s = t.read()
        assertEquals(29, s.enemy!!.moveRows.single().pp, "live PP")
        assertNull(s.lastAttackMove, "it has attacked since its counter moved")
        TrackerPrefs.countEnemyPp = false
        try { assertEquals(30, t.read().enemy!!.moveRows.single().pp, "base PP with the option off") } finally { TrackerPrefs.countEnemyPp = true }
        w.put(map.enemyMove, 0); w.put(map.aiTurns, 0)
        assertEquals("thundershock", t.read().lastAttackMove?.lowercase())
        w.put(map.inBattle, 0)
        s = t.read()
        assertTrue(s.party[0].statStages.isEmpty()); assertNull(s.lastAttackMove)
    }

    /** "Team:" (TrackerScreen.lua:825-828): the one ball the reference knows, in a trainer battle only. */
    @Test
    fun `a trainer battle's team row is the opponent on the field`() {
        val map = Gen1Map.RED_BLUE
        val w = overworld(map)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 4); w.be16(e + 15, 18)
        val t = Gen1Tracker(w, rom(map, "POKEMON RED", 0x00))
        w.put(map.inBattle, 2)
        assertEquals(listOf(true), t.read().enemyTeam)
        w.be16(e + 1, 0)
        assertEquals(listOf(false), t.read().enemyTeam, "fainted: the grey ball")
        w.be16(e + 1, 12); w.put(map.inBattle, 1)
        assertEquals(emptyList(), t.read().enemyTeam, "a wild battle")
        w.put(map.inBattle, 0)
        assertEquals(emptyList(), t.read().enemyTeam)
    }

    /**
     * Program.updateMapLocation (Gen 1 reference Program.lua:1114-1129): the
     * map is wCurMap, which makes the Time Machine's points possible. It has no
     * name: the reference's Gen 1 names are RSE's.
     */
    @Test
    fun `the map is wCurMap, Red's and Yellow's own, with no name`() {
        val red = Gen1Map.RED_BLUE
        val w = overworld(red); w.put(0x135EL, 12)
        val s = Gen1Tracker(w, rom(red, "POKEMON RED", 0x00)).read()
        assertEquals(12, s.mapId); assertNull(s.routeName)
        val y = Gen1Map.YELLOW
        val wy = overworld(y); wy.put(0x135DL, 33)
        assertEquals(33, Gen1Tracker(wy, rom(y, "POKEMON YELLOW", 0x80)).read().mapId, "pokeyellow wCurMap 0xD35D")
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

    /** rc33 audit P1: in a battle the Pokemon on the field (wPlayerMonNumber) gets the stages and the heals, not slot 1. */
    @Test
    fun `in a battle the Pokemon on the field is the one wPlayerMonNumber names`() {
        val map = Gen1Map.RED_BLUE
        fun battle(onField: Int): TrackerState {
            val w = overworld(map)
            w.put(map.inBattle, 1)
            val e = map.enemyMon
            w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 14, 4); w.be16(e + 15, 18)
            w.put(Gen1Tracker.PLAYER_MON_NUMBER, onField)
            intArrayOf(9, 7, 7, 7, 7, 7).forEachIndexed { i, v -> w.put(map.statMods + i, v) }   // ATTACK +2
            return Gen1Tracker(w, rom(map, "POKEMON RED", 0x00)).read()
        }
        val s = battle(1)
        assertEquals(1, s.ownOnField)
        assertEquals(18, s.onField!!.mon.maxHp)
        assertTrue(s.party[0].statStages.isEmpty(), "slot 1 is not on the field")
        assertTrue(s.party[1].statStages.isNotEmpty())
        assertTrue(s.healPercent != battle(0).healPercent, "heals are counted against the Pokemon on the field")
    }

    /**
     * rc32 audit P2 #135: beating the Champion ends a Red, Blue or Yellow run as a win. A trainer battle against RIVAL3 (0x2B)
     * that ends with wBattleResult 0, the Champion's Pokemon at 0 HP in wEnemyMon and the player in CHAMPIONS_ROOM (0x78)
     * reads WON until the next battle begins. A loss does not, nor the blackout after it, which zeroes wBattleResult as it
     * heals the party; nor a win over anyone else; nor a won game's save at the main menu, which sits on HALL_OF_FAME.
     */
    @Test
    fun `beating the Champion reads as a win until the next battle, and losing to him does not`() {
        val map = Gen1Map.RED_BLUE
        val rom = rom(map, "POKEMON RED", 0x00)
        fun battle(w: Wram, trainerClass: Int, hp: Int) {
            w.put(map.curMap, 0x78); w.put(map.trainerClass, trainerClass); w.put(map.inBattle, 2)
            val e = map.enemyMon
            w.put(e, 0x03); w.be16(e + 1, hp); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 60); w.be16(e + 15, 180)
        }
        fun won(w: Wram) { w.be16(map.enemyMon + 1, 0); w.put(map.battleResult, 0); w.put(map.inBattle, 0) }

        var w = overworld(map); var t = Gen1Tracker(w, rom)
        battle(w, 0x2B, 90)
        assertNull(t.read().gameOver, "not while the battle is on")
        won(w)
        assertEquals(GameOver.WON, t.read().gameOver)
        assertEquals(GameOver.WON, t.read().gameOver, "held, as the GBA's battle outcome byte holds it")
        w.put(map.inBattle, 1)
        assertNull(t.read().gameOver, "until the next battle begins")
        w.put(map.inBattle, 0)
        assertNull(t.read().gameOver, "and it is not given again")

        // Lost: the battle ends with wBattleResult 1 and every Pokemon down. The blackout then zeroes the result, heals the
        // party and warps the player, in one routine: whatever read sees it, it is no win.
        w = overworld(map); t = Gen1Tracker(w, rom)
        battle(w, 0x2B, 90)
        t.read()
        w.put(map.battleResult, 1); w.put(map.inBattle, 0)
        party(w, map, 0, 0xB1, 12, 0, 40, listOf(52, 10, 0, 0)); party(w, map, 1, 0x03, 5, 0, 18, listOf(84, 0, 0, 0))
        assertEquals(GameOver.LOST, t.read().gameOver)
        w = overworld(map); t = Gen1Tracker(w, rom)
        battle(w, 0x2B, 90)
        t.read()
        w.put(map.battleResult, 0); w.put(map.inBattle, 0); w.put(map.curMap, 0xAE)      // the blackout, read only after it
        assertNull(t.read().gameOver, "the Champion still stands, and the player is out of his room")

        // Beating anyone else is no win, and a won save loaded at the main menu (wCurMap on HALL_OF_FAME) is none either.
        w = overworld(map); t = Gen1Tracker(w, rom)
        battle(w, 0x2A, 90); t.read(); won(w)
        assertNull(t.read().gameOver, "RIVAL2")
        w = overworld(map); t = Gen1Tracker(w, rom)
        w.put(map.curMap, 0x76)
        assertNull(t.read().gameOver, "no battle with the Champion was seen")
    }

    /** Gen 1 Conversion copies the target's types into wBattleMon (pokered ConversionEffect): your card shows them next read. */
    @Test
    fun `your Conversion shows on your card on the next read`() {
        val map = Gen1Map.RED_BLUE
        val rom = rom(map, "POKEMON RED", 0x00)
        val w = overworld(map)
        w.put(map.inBattle, 1)
        val e = map.enemyMon
        w.put(e, 0x03); w.be16(e + 1, 12); w.put(e + 5, 23); w.put(e + 6, 23); w.put(e + 14, 4); w.be16(e + 15, 18)
        w.put(map.battleMon, 0xB1); w.put(map.battleMon + 5, 20); w.put(map.battleMon + 6, 20)   // Charmander, Fire
        val t = Gen1Tracker(w, rom)
        assertEquals(listOf(10, 10), t.read().party[0].battleTypes)
        w.put(map.battleMon + 5, 23); w.put(map.battleMon + 6, 23)                               // Conversion: the foe's Electric
        assertEquals(listOf(13, 13), t.read().party[0].battleTypes)
        assertEquals(13, t.read().party[0].base?.type1)
    }
}
