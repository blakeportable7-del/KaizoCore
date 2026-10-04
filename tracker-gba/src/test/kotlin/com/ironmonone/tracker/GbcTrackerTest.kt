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
    fun `an egg in the party does not hide the Pokemon after it`() {
        val w = overworld()
        w.put(GbcTracker.PARTY_COUNT, 3)
        party(w, 0, 155, 12, 0, 40, listOf(52, 33, 0, 0))                 // the lead has fainted
        party(w, 1, 175, 5, 20, 20, listOf(33, 0, 0, 0))
        w.put(GbcTracker.PARTY_SPECIES + 1, 0xFD)                         // an egg, as the list shows one
        party(w, 2, 161, 5, 18, 18, listOf(33, 0, 0, 0))
        w.put(GbcTracker.PARTY_SPECIES + 3, 0xFF)
        val s = GbcTracker(w, rom()).read()
        assertEquals(listOf(155, 161), s.party.map { it.mon.species }, "the egg is skipped, the Pokemon after it is read")
        assertTrue(!LossCondition.ENTIRE_PARTY.lost(s.party.map { it.mon.level to it.mon.curHp }), "the Sentret still stands")
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
        assertEquals(60, s.healHp, "the whole HP, for Show heals as whole number (HealTotals)")
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
        w.put(GbcTracker.ENEMY_LAST_MOVE, 33); w.put(GbcTracker.ENEMY_TURNS, 1)
        s = t.read()
        assertEquals(listOf("tackle"), s.enemy!!.movesSeen.map { it.lowercase() })
        // Battle ends: the opponent leaves and the seen list resets.
        w.put(GbcTracker.BATTLE_MODE, 0)
        s = t.read()
        assertTrue(!s.inBattle); assertNull(s.enemy)
    }

    /**
     * Jasmine's Mineral Badge is wJohtoBadges bit 4 and Chuck's Storm Badge
     * bit 5, but the art has Storm as badge 5: the Gen 2 reference reads
     * badge 5 from bit 5 and badge 6 from bit 4 (Program.lua:1103-1108).
     * Kanto's eight ride above Johto's for the badge row's second line.
     */
    @Test
    fun `the Hall of Fame flag says the Johto League is beaten, for Survival's Kanto heals`() {
        fun beaten(flags: Int, kanto: Int = 0): Boolean {
            val w = overworld(); w.put(GbcTracker.JOHTO_BADGES - 11, flags); w.put(GbcTracker.JOHTO_BADGES, 0xFF); w.put(GbcTracker.KANTO_BADGES, kanto)
            return GbcTracker(w, rom()).read().leagueBeaten
        }
        assertEquals(false, beaten(0))
        assertEquals(false, beaten(0xBF), "every other status bit: not the Hall of Fame")
        assertEquals(true, beaten(0x40), "wStatusFlags bit 6, STATUSFLAGS_HALL_OF_FAME_F")
        assertEquals(true, beaten(0, kanto = 1), "a Kanto badge: the League was beaten")
        assertEquals(Gen2Map.CRYSTAL.statusFlags, 0x184CL)
        assertEquals(Gen2Map.GS.statusFlags, 0x1571L)
    }

    @Test
    fun `Johto badges come out in the art's order, Chuck's and Jasmine's swapped, Kanto above`() {
        fun badges(johto: Int, kanto: Int): Int {
            val w = overworld(); w.put(GbcTracker.JOHTO_BADGES, johto); w.put(GbcTracker.KANTO_BADGES, kanto)
            return GbcTracker(w, rom()).read().badges
        }
        assertEquals(1 shl 5, badges(1 shl 4, 0), "Mineral (bit 4) lights badge 6, Jasmine's art")
        assertEquals(1 shl 4, badges(1 shl 5, 0), "Storm (bit 5) lights badge 5, Chuck's art")
        assertEquals(0b1100_1111, badges(0b1100_1111, 0), "the other six stay where they are")
        assertEquals(0xFF or (0b101 shl 8), badges(0xFF, 0b101), "Boulder and Thunder: Kanto badges 1 and 3")
    }

    /**
     * Battle.updateTrackedInfoGen2 (Gen 2 reference Battle.lua:429-455): the
     * move byte in wEnemyMoveStruct is recorded only when it is one of the
     * opponent's four, and once per enemy turn (wEnemyTurnsTaken). The byte
     * keeps the last battle's move, and the AI loads every move it weighs
     * into it, so recording any byte put moves on the card that were never used.
     */
    @Test
    fun `an opponent's move is recorded only when it knows it, once per turn it takes`() {
        val w = overworld()
        w.put(GbcTracker.BATTLE_MODE, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 2, 33); w.put(e + 3, 45); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        val t = GbcTracker(w, rom())
        fun seen() = t.read().enemy!!.movesSeen.map { it.lowercase() }
        val turns = 0x06DCL   // the reference's oppTurn, 0x020006dc: wEnemyTurnsTaken 0xC6DC
        w.put(GbcTracker.ENEMY_LAST_MOVE, 33); w.put(turns, 0)
        assertEquals(emptyList(), seen(), "the byte left over from the last battle, before the opponent has moved")
        w.put(GbcTracker.ENEMY_LAST_MOVE, 52); w.put(turns, 1)
        assertEquals(emptyList(), seen(), "Ember: not one of Sentret's moves")
        w.put(GbcTracker.ENEMY_LAST_MOVE, 33)
        assertEquals(listOf("tackle"), seen(), "Tackle, on its first turn")
        w.put(GbcTracker.ENEMY_LAST_MOVE, 45)
        assertEquals(listOf("tackle"), seen(), "Growl weighed by the AI on the same turn: not used, not recorded")
        w.put(turns, 2)
        assertEquals(listOf("tackle", "growl"), seen(), "Growl, used on its second turn")
        // A new opponent: its counter starts over at 0, and so does the rule.
        w.put(e, 155); w.put(e + 2, 52); w.put(e + 3, 0); w.put(turns, 0)
        assertEquals(emptyList(), seen())
        w.put(GbcTracker.ENEMY_LAST_MOVE, 52); w.put(turns, 1)
        assertEquals(listOf("ember"), seen())
    }

    /**
     * Battle.updateBattleStatusGen2 (Gen 2 reference Battle.lua:132-162): party
     * slot 1 at 0 HP is a loss once wBattleMode reads 0, never mid-battle, and
     * no other slot counts. It used to fire the moment the lead fainted.
     */
    @Test
    fun `a trainer battle is not wild, and a dead lead is a loss only once the battle is over`() {
        val w = overworld()
        w.put(GbcTracker.BATTLE_MODE, 2)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 155); w.put(e + 13, 10); w.be16(e + 16, 5); w.be16(e + 18, 30)
        val t = GbcTracker(w, rom())
        val s = t.read()
        assertTrue(s.inBattle && !s.isWildBattle)
        w.be16(GbcTracker.PARTY_MONS + 34, 0)                 // the lead faints
        assertNull(t.read().gameOver, "mid-battle: the reference waits for wBattleMode 0")
        w.put(GbcTracker.BATTLE_MODE, 0)
        assertEquals(GameOver.LOST, t.read().gameOver)
        w.be16(GbcTracker.PARTY_MONS + 34, 30)                // healed lead, second slot fainted
        w.be16(GbcTracker.PARTY_MONS + GbcTracker.PARTY_STRIDE + 34, 0)
        assertNull(t.read().gameOver, "only slot 1 counts")
    }

    /**
     * The evolution text on the cards (Gen 2 reference TrackerScreen.lua:691-734):
     * a stone evolution is ready while that stone is in the bag by Crystal's own
     * item id, not the Gen 1 id the reference keys it by (0x21 is X Accuracy
     * here), and friendship (party_struct +27) reads READY at 220.
     */
    @Test
    fun `the cards carry the evolution text, with Crystal's own stone ids and friendship`() {
        val w = overworld()
        party(w, 2, 25, 10, 20, 30, listOf(33, 0, 0, 0))              // Pikachu: THUNDER
        party(w, 3, 172, 5, 15, 15, listOf(33, 0, 0, 0))             // Pichu: FRIEND
        w.put(GbcTracker.PARTY_COUNT, 4); w.put(GbcTracker.PARTY_SPECIES + 4, 0xFF)
        val pichuFriendship = GbcTracker.PARTY_MONS + 3 * GbcTracker.PARTY_STRIDE + 27
        w.put(pichuFriendship, 219)
        val t = GbcTracker(w, rom())
        var s = t.read()
        assertEquals(EvoText.Label("14", EvoText.Tone.WAITING), s.party[0].evo, "Cyndaquil Lv.12")
        assertEquals(EvoText.Label("THUNDER", EvoText.Tone.WAITING), s.party[2].evo)
        assertEquals(EvoText.Label("FRIEND", EvoText.Tone.WAITING), s.party[3].evo)
        w.put(GbcTracker.NUM_ITEMS, 3); w.put(GbcTracker.ITEMS + 4, 0x21); w.put(GbcTracker.ITEMS + 5, 1); w.put(GbcTracker.ITEMS + 6, 0xFF)
        assertEquals(EvoText.Tone.WAITING, t.read().party[2].evo?.tone, "0x21: X Accuracy in Crystal")
        w.put(GbcTracker.ITEMS + 4, 0x17)                            // THUNDERSTONE
        w.put(pichuFriendship, 220)
        s = t.read()
        assertEquals(EvoText.Label("THUNDER", EvoText.Tone.READY), s.party[2].evo)
        assertEquals(EvoText.Label("READY", EvoText.Tone.READY), s.party[3].evo)
        w.put(GbcTracker.BATTLE_MODE, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        assertEquals(EvoText.Label("15", EvoText.Tone.PLAIN), t.read().enemy?.evo)
    }

    /**
     * In a battle (Gen 2 reference Battle.lua:379-408 and 699-722,
     * DataHelper.lua:275-286): the seven stage bytes at wPlayerStatLevels and 8
     * on, the enemy's live PP, and "Last move: X" once wPlayerTurnsTaken moves
     * while the move byte is 0.
     */
    @Test
    fun `a battle shows the stat stages, the enemy's live PP and the last move line`() {
        val w = overworld()
        w.put(GbcTracker.BATTLE_MODE, 2)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 2, 33); w.put(e + 8, 35); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        for (i in 0 until 7) { w.put(GbcTracker.STAT_LEVELS + i, 7); w.put(GbcTracker.STAT_LEVELS + 8 + i, 7) }
        w.put(GbcTracker.STAT_LEVELS + 4, 5)                  // your Sp. Def -2
        w.put(GbcTracker.STAT_LEVELS + 8 + 5, 8)              // its accuracy +1
        val t = GbcTracker(w, rom())
        var s = t.read()
        assertEquals(4, s.party[0].statStages["SPD"]); assertEquals(6, s.party[0].statStages["SPA"])
        assertEquals(7, s.enemy!!.statStages["ACC"])
        w.put(GbcTracker.ENEMY_LAST_MOVE, 33); w.put(GbcTracker.ENEMY_TURNS, 1); w.put(GbcTracker.PLAYER_TURNS, 1); w.put(e + 8, 34)
        s = t.read()
        assertEquals(34, s.enemy!!.moveRows.single().pp, "Tackle at its live PP")
        assertNull(s.lastAttackMove)
        TrackerPrefs.countEnemyPp = false
        try { assertEquals(35, t.read().enemy!!.moveRows.single().pp, "base PP with the option off") } finally { TrackerPrefs.countEnemyPp = true }
        w.put(GbcTracker.ENEMY_LAST_MOVE, 0); w.put(GbcTracker.PLAYER_TURNS, 2)
        assertEquals("tackle", t.read().lastAttackMove?.lowercase())
        w.put(GbcTracker.BATTLE_MODE, 0)
        s = t.read()
        assertTrue(s.party[0].statStages.isEmpty()); assertNull(s.lastAttackMove)
    }

    /**
     * Tracker Extras' "Estimate Pokemon IV Potential" on the lead, with the ROM's
     * BST (PokemonData.UpdateBST) and the reference's name for the species.
     */
    @Test
    fun `the IV estimate judges the lead with the ROM's BST`() {
        val w = overworld()
        // Cyndaquil at Lv.50, stats summing to 461 against this ROM's BST of 309: 129.8, "Quite impressive!!".
        val b = GbcTracker.PARTY_MONS
        w.put(b + 31, 50); w.be16(b + 34, 80); w.be16(b + 36, 80)
        w.be16(b + 38, 76); w.be16(b + 40, 75); w.be16(b + 42, 75); w.be16(b + 44, 77); w.be16(b + 46, 78)
        val t = GbcTracker(w, rom())
        assertEquals(309, t.baseStats(155)!!.bst)
        assertEquals("Cyndaquil is: Quite impressive!!", t.ivPotential(t.read().party.first()))
        assertEquals(IvEstimate.UNAVAILABLE, t.ivPotential(null), "no Pokemon yet")
    }

    /** "Team:" (TrackerScreen.lua:794-797): the one ball the reference knows, in a trainer battle only. */
    @Test
    fun `a trainer battle's team row is the opponent on the field`() {
        val w = overworld()
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        val t = GbcTracker(w, rom())
        w.put(GbcTracker.BATTLE_MODE, 2)
        assertEquals(listOf(true), t.read().enemyTeam)
        w.be16(e + 16, 0)
        assertEquals(listOf(false), t.read().enemyTeam, "fainted: the grey ball")
        w.be16(e + 16, 12); w.put(GbcTracker.BATTLE_MODE, 1)
        assertEquals(emptyList(), t.read().enemyTeam, "a wild battle")
    }

    /**
     * A held item by its name (Gen 2 reference DataHelper.lua:173-174:
     * MiscData.Items[id + 1]), on the card and in the team view, which both
     * read TrackedMon.itemName; it read "#146". Each Pokemon's own item.
     */
    @Test
    fun `held items read by their names, each Pokemon its own`() {
        val w = overworld()
        w.put(GbcTracker.PARTY_MONS + 1, 146)                              // LEFTOVERS $92
        w.put(GbcTracker.PARTY_MONS + GbcTracker.PARTY_STRIDE + 1, 109)    // MIRACLEBERRY $6D
        val s = GbcTracker(w, rom()).read()
        assertEquals("LEFTOVERS", s.party[0].itemName)
        assertEquals("MIRACLEBERRY", s.party[1].itemName)
        w.put(GbcTracker.PARTY_MONS + 1, 0)
        assertEquals("-", GbcTracker(w, rom()).read().party[0].itemName, "no item")
    }

    /** The names table and the heals table are the same reference's ids: an off-by-one between them would show here. */
    @Test
    fun `every healing item id names the item the heals table means`() {
        val names = javaClass.getResourceAsStream("/gen2/items.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .associate { it.substringBefore('\t').toInt() to it.substringAfter('\t') }
        assertEquals(255, names.size)
        val expected = mapOf(18 to "POTION", 17 to "SUPER POTION", 16 to "HYPER POTION", 15 to "MAX POTION", 14 to "FULL RESTORE",
            46 to "FRESH WATER", 47 to "SODA POP", 48 to "LEMONADE", 72 to "MOOMOO MILK", 114 to "RAGE CANDY BAR",
            121 to "ENERGY POWDER", 122 to "ENERGY ROOT", 139 to "BERRY JUICE", 173 to "BERRY", 174 to "GOLD BERRY")
        assertEquals(GbcTracker.HEALS.keys, expected.keys)
        expected.forEach { (id, name) -> assertEquals(name, names[id], "item $id") }
        assertEquals(mapOf(8 to "MOON STONE", 22 to "FIRE STONE", 23 to "THUNDER STONE", 24 to "WATER STONE", 34 to "LEAF STONE"),
            GbcTracker.STONES.keys.associateWith { names[it] })
    }

    /**
     * Program.updateMapLocation (Gen 2 reference Program.lua:1121-1134): the map
     * is wCurLandmark (0xC2D9), named from RouteData.setupRouteInfoAsGSC; a gate
     * (0xFF) has no name. The names are Crystal's landmark constants.
     */
    @Test
    fun `the map is Crystal's landmark, named as the reference names it`() {
        val w = overworld()
        w.put(0x02D9L, 2)
        val t = GbcTracker(w, rom())
        var s = t.read()
        assertEquals(2, s.mapId); assertEquals("Route 29", s.routeName)
        w.put(0x02D9L, 0xFF)
        s = t.read()
        assertEquals(0xFF, s.mapId); assertNull(s.routeName, "a gate")
        val names = javaClass.getResourceAsStream("/gen2/landmarks.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .associate { it.substringBefore('\t').toInt() to it.substringAfter('\t') }
        // pokecrystal constants/landmark_constants.asm: NEW_BARK_TOWN 01, BATTLE_TOWER 1d, PALLET_TOWN 2f, FAST_SHIP 5f.
        assertEquals(listOf("New Bark Town", "Battle Tower", "Pallet Town", "S.S. Aqua"), listOf(1, 0x1D, 0x2F, 0x5F).map { names[it] })
        assertEquals((1..95).toList(), names.keys.sorted())
    }

    /**
     * The Crystal reads added for the battle and the map are the Gen 2
     * reference's own (GameSettings.setGen2Addresses: StatChange 0x020006cc,
     * gTurn 0x020006dd, gMapHeader 0x020002d9), and every Gen 2 read lies in
     * the 8 KB work RAM, the enemy's stage bytes included.
     */
    @Test
    fun `the battle and map reads are the reference's, inside work RAM`() {
        val c = Gen2Map.CRYSTAL
        assertEquals(0x06CCL, c.statLevels); assertEquals(0x06DDL, c.playerTurns); assertEquals(0x02D9L, c.curLandmark)
        for (m in listOf(Gen2Map.CRYSTAL, Gen2Map.GS)) {
            val reads = listOf(m.partyCount, m.partyMons, m.enemyMon, m.battleMode, m.enemyMove, m.enemyTurns, m.playerTurns,
                m.statLevels, m.statLevels + 8 + 6, m.johtoBadges, m.numItems, m.items) +
                listOf(m.curLandmark, m.mapGroup + 1).filter { it > 1L }
            reads.forEach { assertTrue(it in 1L until 0x2000L, "${m.name}: 0x%X".format(it)) }
        }
    }

    /**
     * Every Long field of Gen2Map, by reflection, so the Nuzlocke reads (2026-09-30), the 2D menu and wCurBattleMon are
     * range checked too: the list above names none of them (rc32 audit P3 #117). The ends of the blocks read in one go.
     */
    @Test
    fun `every Gen 2 work RAM read of the map is inside work RAM`() {
        for (m in listOf(Gen2Map.CRYSTAL, Gen2Map.GS)) {
            val fields = Gen2Map::class.java.declaredFields.filter { it.type == java.lang.Long.TYPE }
                .map { f -> f.isAccessible = true; f.getLong(m) }.filter { it != 0L }
            assertTrue(fields.size >= 25, "${m.name}: only ${fields.size} reads found; the reflection lost the map's fields")
            val reads = fields + listOf(m.partyMons + 6 * GbcTracker.PARTY_STRIDE - 1, m.nicks + 6 * 11 - 1,
                m.balls + 12 * 2, m.menu2D + 12, m.statLevels + 8 + 6)
            reads.forEach { assertTrue(it in 1L until 0x2000L, "${m.name}: 0x%X is outside work RAM".format(it)) }
        }
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
        // The proof the map is consulted: Crystal's addresses find no party in this RAM
        // (a zero count there reads as 'no party yet', not as a broken map).
        assertEquals(0, GbcTracker(goldWram(), goldRom(), Gen2Map.CRYSTAL).read().partyCount)
    }

    @Test
    fun `a Gold battle reads the opponent at pokegold's wEnemyMon and its used move at wEnemyMoveStruct`() {
        val gs = Gen2Map.GS
        val w = goldWram()
        w.put(gs.battleMode, 1)
        val e = gs.enemyMon
        w.put(e, 155); w.put(e + 2, 52); w.put(e + 13, 7); w.be16(e + 16, 9); w.be16(e + 18, 20); w.put(e + 30, 20); w.put(e + 31, 20)
        val t = GbcTracker(w, goldRom())
        var s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        assertEquals("CYNDAQUIL", s.enemy!!.speciesName); assertEquals(emptyList(), s.enemy!!.movesSeen)
        w.put(gs.enemyMove, 52); w.put(0x0BBAL, 1)     // pokegold wEnemyTurnsTaken 0xCBBA (tools/wram_layout.py)
        s = t.read()
        assertEquals(listOf("ember"), s.enemy!!.movesSeen.map { it.lowercase() })
    }

    /** Gold and Silver keep no wCurLandmark (Crystal's map name sign sets it): the map is wMapGroup and wMapNumber, unnamed. */
    @Test
    fun `Gold keeps no landmark, so its map is the group and number, with no name`() {
        val w = goldWram(); w.put(0x1A00L, 24); w.put(0x1A01L, 4)     // pokegold wMapGroup 0xDA00, wMapNumber 0xDA01
        val s = GbcTracker(w, goldRom()).read()
        assertEquals((24 shl 8) or 4, s.mapId); assertNull(s.routeName)
    }

    @Test
    fun `a Game Boy ROM with an unknown title gets no map and reads nothing`() {
        val r = ByteArray(0x60000)
        header(r, "TETRIS", 0x00)
        assertNull(Gen2Map.forRom(r))
        assertTrue(GbcTracker(overworld(), r).read().unreadable)
        // And a count the game could never have (Gen 1's 0xFF terminator sitting on the count byte) is unreadable.
        val w = overworld(); w.put(GbcTracker.PARTY_COUNT, 0xFF); w.put(GbcTracker.PARTY_SPECIES, 0xFF)
        assertTrue(GbcTracker(w, rom()).read().unreadable)
        // While a zero count is simply no party yet.
        val w0 = overworld(); w0.put(GbcTracker.PARTY_COUNT, 0); w0.put(GbcTracker.PARTY_SPECIES, 0)
        assertTrue(!GbcTracker(w0, rom()).read().unreadable)
    }

    @Test
    fun `a wrong count byte cannot invent party members`() {
        val w = overworld()
        w.put(GbcTracker.PARTY_COUNT, 6)          // lies: only two structs and a terminator
        assertEquals(2, GbcTracker(w, rom()).read().partyCount)
    }

    /** rc33 audit P1: wCurBattleMon is a party slot; with an egg before it, the list index is one less. */
    @Test
    fun `in a battle the Pokemon on the field is the one wCurBattleMon names, eggs skipped`() {
        val w = overworld()
        w.put(GbcTracker.PARTY_COUNT, 3)
        party(w, 0, 155, 12, 0, 40, listOf(52, 33, 0, 0))                 // the lead has fainted
        party(w, 1, 175, 5, 20, 20, listOf(33, 0, 0, 0))
        w.put(GbcTracker.PARTY_SPECIES + 1, 0xFD)                         // an egg
        party(w, 2, 161, 5, 18, 18, listOf(33, 0, 0, 0))
        w.put(GbcTracker.PARTY_SPECIES + 3, 0xFF)
        w.put(GbcTracker.BATTLE_MODE, 1)
        val e = GbcTracker.ENEMY_MON
        w.put(e, 161); w.put(e + 2, 33); w.put(e + 13, 4); w.be16(e + 16, 12); w.be16(e + 18, 18)
        w.put(Gen2Map.CRYSTAL.curBattleMon, 2)                             // slot 3 is out
        intArrayOf(9, 7, 7, 7, 7, 7, 7).forEachIndexed { i, v -> w.put(Gen2Map.CRYSTAL.statLevels + i, v) }
        val s = GbcTracker(w, rom()).read()
        assertEquals(1, s.ownOnField, "slot 3 is list entry 2: the egg in slot 2 is not listed")
        assertEquals(161, s.onField!!.mon.species)
        assertTrue(s.party[0].statStages.isEmpty(), "slot 1 is not on the field")
        assertTrue(s.party[1].statStages.isNotEmpty())
    }

    /** rc32 audit P2 #134: CheckShininess needs bit 1 of the Attack DV (pokecrystal engine/gfx/color.asm:3-36), not Attack 2. */
    @Test
    fun `a party Pokemon is shiny by the game's own rule, the Red Gyarados included`() {
        fun shinyWith(atkDef: Int): Boolean {
            val w = overworld()
            w.put(GbcTracker.PARTY_MONS + 21, atkDef); w.put(GbcTracker.PARTY_MONS + 22, 0xAA)
            return GbcTracker(w, rom()).read().party[0].mon.shiny
        }
        assertTrue(shinyWith(0xEA), "Attack 14: ATKDEFDV_SHINY, the Lake of Rage Gyarados")
        assertTrue(shinyWith(0x3A), "Attack 3")
        assertTrue(shinyWith(0x2A), "Attack 2")
        assertTrue(!shinyWith(0x1A), "Attack 1: bit 1 clear")
        assertTrue(!shinyWith(0xE9), "Defense 9")
    }

    /** rc32 audit P3 #110: Curse's type byte is CURSE_TYPE, 19 (pokecrystal constants/type_constants.asm:22-23, moves.asm:190). */
    @Test
    fun `Curse is the ??? type, not Normal`() {
        assertEquals(9, GbcTracker.gen3Type(19))
        val r = rom()
        val m = GbcTracker.MOVES + (174 - 1) * GbcTracker.MOVE_STRIDE
        r[m + 2] = 0; r[m + 3] = 19; r[m + 4] = 255.toByte(); r[m + 5] = 10
        val t = GbcTracker(overworld(), r)
        assertEquals(9, t.moveData(174)!![1])
        assertEquals(9, t.moveRowOf(174, 10, 10).type)
        assertEquals(0, GbcTracker.gen3Type(0), "Normal stays Normal")
    }

    /**
     * rc32 audit P2 #135: Red beaten on Mt. Silver is the GSC rulebook's win. A trainer battle against class RED (0x3F) that
     * ends with wBattleResult's low bits at WIN reads WON until the next battle begins. CleanUpBattleRAM clears the class
     * with the battle byte, so it is the class seen during the battle that counts. A loss to Red, or a win over anyone
     * else, is no win.
     */
    @Test
    fun `beating Red reads as a win until the next battle, and losing to him or beating anyone else does not`() {
        val c = Gen2Map.CRYSTAL
        fun fight(w: Wram, trainerClass: Int) { w.put(GbcTracker.BATTLE_MODE, 2); w.put(c.trainerClass, trainerClass) }
        fun end(w: Wram, result: Int) { w.put(GbcTracker.BATTLE_MODE, 0); w.put(c.trainerClass, 0); w.put(c.battleResult, result) }

        var w = overworld(); var t = GbcTracker(w, rom())
        fight(w, 0x3F)
        assertNull(t.read().gameOver, "not while the battle is on")
        end(w, 0x80)                                       // WIN, with the box-full bit set
        assertEquals(GameOver.WON, t.read().gameOver)
        assertEquals(GameOver.WON, t.read().gameOver, "held, as the GBA's battle outcome byte holds it")
        w.put(GbcTracker.BATTLE_MODE, 1)
        assertNull(t.read().gameOver, "until the next battle begins")
        w.put(GbcTracker.BATTLE_MODE, 0)
        assertNull(t.read().gameOver, "and it is not given again")

        w = overworld(); t = GbcTracker(w, rom())
        fight(w, 0x3F); t.read(); end(w, 1)
        assertNull(t.read().gameOver, "lost to Red, the lead still standing")
        w = overworld(); t = GbcTracker(w, rom())
        fight(w, 0x01); t.read(); end(w, 0)
        assertNull(t.read().gameOver, "Falkner")
    }
}
