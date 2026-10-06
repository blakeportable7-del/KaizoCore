package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Gen 3 tracker's own reads through read(), on synthetic memory laid out at each game's real addresses (rc34 fixes
 * of the rc32 audit): the game over (P3 #108), the first fishing battle (P3 #109), the rival kept with the run
 * (P2 #130), Transform (P2 #131), an Egg in slot 1 (P2 #136), a Battle Tent or Frontier battle (P2 #141) and the heals'
 * whole HP (P2 #99). Nothing here needs a ROM: the base stats and the trainer data a test needs are put in its memory.
 */
class Gen3BattleReadsTest {

    private class Mem {
        val bytes = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { bytes[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun put(a: Long, b: ByteArray) { b.forEachIndexed { i, v -> bytes[a + i] = v } }
        fun reader() = MemoryReader { address, length -> ByteArray(length) { bytes[address + it] ?: 0 } }
    }

    /** A vanilla party record: personality a multiple of 24, so the substructures run growth, attacks, EVs, misc. */
    private fun encodeMon(
        pid: Long, species: Int, level: Int, hp: Int, maxHp: Int, abilitySlot: Int = 0, egg: Boolean = false,
        moves: List<Int> = emptyList(), pps: List<Int> = emptyList(),
    ): ByteArray {
        require(pid % 24 == 0L)
        val otId = 0x1234L
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        // The attacks substructure, second at this personality: four move ids, then their PP.
        moves.forEachIndexed { i, id -> plain[12 + i * 2] = id.toByte(); plain[13 + i * 2] = (id shr 8).toByte() }
        pps.forEachIndexed { i, pp -> plain[20 + i] = pp.toByte() }
        plain.putU32(40, (if (abilitySlot == 1) 0x80000000L else 0L) or (if (egg) 0x40000000L else 0L))
        val mon = ByteArray(100)
        mon.putU32(0, pid); mon.putU32(4, otId)
        val key = pid xor otId
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4) xor key)
        mon[0x54] = level.toByte()
        mon[0x56] = hp.toByte(); mon[0x57] = (hp shr 8).toByte()
        mon[0x58] = maxHp.toByte(); mon[0x59] = (maxHp shr 8).toByte()
        return mon
    }

    private fun party(mem: Mem, m: GameMap, vararg mons: ByteArray) {
        mem.put8(m.partyCount, mons.size)
        mons.forEachIndexed { i, b -> mem.put(m.party + i * 100L, b) }
    }

    /** A base stats line the tracker accepts, with its two abilities. */
    private fun base(mem: Mem, m: GameMap, species: Int, a1: Int, a2: Int = 0) {
        val at = m.baseStats + species.toLong() * m.baseStatsStride
        for (i in 0 until 6) mem.put8(at + i, 50)
        mem.put8(at + 22, a1); mem.put8(at + 23, a2)
    }

    /** A battle on the field: battler 0 is [own], battler 1 [foe] at [level] with [hp] of [maxHp]. */
    private fun battle(mem: Mem, m: GameMap, flags: Long, own: Int, foe: Int, level: Int = 20, hp: Int = 30, maxHp: Int = 30) {
        mem.put8(m.battlersCount, 2)
        mem.put8(m.battleOutcome, 0)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
        mem.put32(m.battleTypeFlags, flags)
        mem.put16(m.battleMons, own)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e, foe)
        mem.put16(e + 0x28, hp); mem.put8(e + 0x2A, level); mem.put16(e + 0x2C, maxHp)
    }

    /** The battle is over: the outcome is set and gBattleMainFunc is back to nothing. */
    private fun endBattle(mem: Mem, m: GameMap, outcome: Int = 1) {
        mem.put8(m.battleOutcome, outcome)
        mem.put32(m.battleMainFunc, 0)
    }

    /** Two polls: the first enters the battle, the second makes its data readable. */
    private fun settle(t: GbaTracker): TrackerState { t.read(); return t.read() }

    // ------------------------------------------------------------------ your Pokemon's types in battle (2026-10-06)

    /**
     * Castform keeps its species in battle in the five games and only its battle struct's types change with the weather;
     * the opponent's card read those, your own card read the species' row. Now the Pokemon on the field shows the
     * struct's types too, and only when the struct holds that Pokemon.
     */
    @Test
    fun `your Castform in the sun shows the Fire type its battle struct holds, and its row outside the battle`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        party(mem, m, encodeMon(24, 385, 20, 40, 40))
        base(mem, m, 385, 59)                       // Castform's row: Normal/Normal (types 0), Forecast
        mem.put(m.enemyParty, encodeMon(48, 25, 20, 30, 30))
        battle(mem, m, flags = 0, own = 385, foe = 25)
        mem.put8(m.battleMons + m.battleMonTypes, 10); mem.put8(m.battleMons + m.battleMonTypes + 1, 10)
        val s = settle(GbaTracker(mem.reader(), m))
        assertEquals(10 to 10, s.party[0].base?.let { it.type1 to it.type2 }, "Fire, as the battle has it")
        // A struct holding another species (the last battle's, or not yet filled) is not this Pokemon's.
        mem.put16(m.battleMons, 25)
        assertEquals(0 to 0, settle(GbaTracker(mem.reader(), m)).party[0].base?.let { it.type1 to it.type2 })
        // Outside the battle, the row.
        mem.put16(m.battleMons, 385)
        endBattle(mem, m)
        val t = GbaTracker(mem.reader(), m)
        assertEquals(0 to 0, settle(t).party[0].base?.let { it.type1 to it.type2 })
    }

    // ------------------------------------------------------------------ Transform (P2 #131)

    @Test
    fun `a wild Ditto that transformed into your Pokemon is still the Ditto in its party slot`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        party(mem, m, encodeMon(24, 25, 20, 40, 40))
        // The opposing party's slot 0 is a Ditto; gBattlerPartyIndexes[1] reads 0 (unset).
        mem.put(m.enemyParty, encodeMon(48, 132, 20, 30, 30))
        battle(mem, m, flags = 0, own = 25, foe = 25)     // the battle struct holds the copied Pikachu
        mem.put32(m.battleMons + m.battleMonSize + m.status2Offset, GbaTracker.STATUS2_TRANSFORMED)
        val s = settle(GbaTracker(mem.reader(), m))
        assertEquals(132, s.enemy?.species, "the card and the route sighting follow the party Pokemon, not the copy")
        assertEquals(132, s.enemySpeciesId)
        assertEquals(20, s.enemy?.level, "its level stays the battle struct's")
        // Without Transform the battle struct stands, whatever the party slot holds.
        mem.put32(m.battleMons + m.battleMonSize + m.status2Offset, 0)
        assertEquals(25, settle(GbaTracker(mem.reader(), m)).enemy?.species)
    }

    /**
     * RC35-NOTICED N #35: after Transform the battle struct holds the copied moves at 5 PP each, and the card showed them
     * as the opponent's own wherever its moves are shown (learnsets not randomized). The reference's card is the party
     * Pokemon (Tracker.getPokemon, gEnemyParty: Program.lua:855-871), whose moves and PP Transform leaves alone.
     */
    @Test
    fun `a transformed opponent's moves and PP are its own, not the copied ones`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        party(mem, m, encodeMon(24, 25, 20, 40, 40, moves = listOf(84, 98), pps = listOf(28, 30)))
        mem.put(m.enemyParty, encodeMon(48, 132, 20, 30, 30, moves = listOf(144), pps = listOf(9)))
        battle(mem, m, flags = 0, own = 25, foe = 25)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e + 0x0C, 84); mem.put16(e + 0x0E, 98)   // Thunder Shock and Quick Attack, copied
        mem.put8(e + 0x24, 5); mem.put8(e + 0x25, 5)
        mem.put32(e + m.status2Offset, GbaTracker.STATUS2_TRANSFORMED)
        val s = settle(GbaTracker(mem.reader(), m))
        assertEquals(listOf(144, 0, 0, 0), s.enemy?.moves, "Ditto's Transform, from its party slot")
        assertEquals(listOf(9, 0, 0, 0), s.enemy?.movePps)
        // Untransformed, the battle struct's moves are its own and stand.
        mem.put32(e + m.status2Offset, 0)
        val plain = settle(GbaTracker(mem.reader(), m))
        assertEquals(listOf(84, 98, 0, 0), plain.enemy?.moves)
        assertEquals(listOf(5, 5, 0, 0), plain.enemy?.movePps)
    }

    @Test
    fun `your own Transform does not record the foe's ability as your Pokemon's`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        base(mem, m, 65, a1 = 28, a2 = 39)                 // Alakazam: Synchronize / Inner Focus
        base(mem, m, 130, a1 = 22, a2 = 99)                // Gyarados, as the copy holds it
        party(mem, m, encodeMon(24, 65, 30, 60, 60, abilitySlot = 0))
        battle(mem, m, flags = 0, own = 130, foe = 130)
        // Battler 0 used Transform: Gyarados's species and its IV word, ability bit set.
        mem.put32(m.battleMons + 0x14, 0x80000000L)
        mem.put32(m.battleMons + m.status2Offset, GbaTracker.STATUS2_TRANSFORMED)
        val t = GbaTracker(mem.reader(), m)
        settle(t)
        assertEquals(listOf(65 to "#28"), t.readOwnAbilities(), "Alakazam's own first ability (Battle.lua:485-490)")
    }

    @Test
    fun `a partner's Pokemon in a multi battle is not recorded as yours`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        base(mem, m, 65, a1 = 28)
        base(mem, m, 376, a1 = 29)                         // Steven's Metagross
        party(mem, m, encodeMon(24, 65, 30, 60, 60), encodeMon(48, 65, 30, 60, 60), encodeMon(72, 65, 30, 60, 60))
        battle(mem, m, flags = 0x8L or 0x40L, own = 65, foe = 130)
        mem.put8(m.battlersCount, 4)
        mem.put16(m.battleMons + 2L * m.battleMonSize, 376)
        mem.put16(m.battlerPartyIndexes + 4, 3)             // battler 2 is party slot 3, past the three the battle began with
        mem.put(m.party + 300L, encodeMon(96, 376, 45, 120, 120))
        val t = GbaTracker(mem.reader(), m)
        settle(t)
        assertEquals(listOf(65), t.readOwnAbilities().map { it.first }, "Battle.lua:486 and :496 skip slots past Battle.partySize")
    }

    // ------------------------------------------------------------------ an Egg in slot 1 (P2 #136)

    @Test
    fun `an Egg in slot 1 is not the lead, so the card, the heals and the loss rule take the first Pokemon after it`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        party(mem, m, encodeMon(24, 360, 5, 20, 20, egg = true), encodeMon(48, 258, 12, 0, 35))
        val t = GbaTracker(mem.reader(), m)
        val out = t.read()
        assertEquals(258, out.onField?.mon?.species, "Tracker.getPokemon skips eggs (Tracker.lua:105-140)")
        // In battle the lead fainting ends a Kaizo run, egg or no egg in front of it.
        battle(mem, m, flags = 0, own = 258, foe = 263)
        t.lossCondition = LossCondition.LEAD
        assertEquals(GameOver.LOST, settle(t).gameOver)
        // The plain list: the card's Pokemon is never the Egg, even read at slot 0.
        val egg = TrackedMon(PokemonDecoder.decode(encodeMon(24, 360, 5, 20, 20, egg = true)), "EGG", emptyList(), null)
        val mudkip = TrackedMon(PokemonDecoder.decode(encodeMon(48, 258, 12, 30, 35)), "MUDKIP", emptyList(), null)
        assertEquals(258, TrackerState(2, listOf(egg, mudkip), inBattle = false, isWildBattle = false).onField?.mon?.species)
        assertEquals(258, TrackerState(2, listOf(egg, mudkip), inBattle = true, isWildBattle = true, ownOnField = 0).onField?.mon?.species)
        assertEquals(360, TrackerState(1, listOf(egg), inBattle = false, isWildBattle = false).onField?.mon?.species, "only an egg: nothing else to show")
    }

    /**
     * RC35-NOTICED N #34: the carousel's early route encounters and Trainer Info's level colour take Tracker.getPokemon(1,
     * true), the lead past any Egg, in a battle too (TrackerScreen.lua:664, TrainerInfoScreen.lua:251). They read slot 1
     * as it was, the Egg's level and all. The panel's own readers are pinned in the app's TrackerLeadTest.
     */
    @Test
    fun `the lead is the first Pokemon that is not an Egg, whichever is on the field`() {
        val egg = TrackedMon(PokemonDecoder.decode(encodeMon(24, 360, 5, 20, 20, egg = true)), "EGG", emptyList(), null)
        val mudkip = TrackedMon(PokemonDecoder.decode(encodeMon(48, 258, 12, 30, 35)), "MUDKIP", emptyList(), null)
        val zigzagoon = TrackedMon(PokemonDecoder.decode(encodeMon(72, 263, 9, 30, 30)), "ZIGZAGOON", emptyList(), null)
        val party = listOf(egg, mudkip, zigzagoon)
        assertEquals(12, TrackerState(3, party, inBattle = false, isWildBattle = false).lead?.mon?.level)
        val switched = TrackerState(3, party, inBattle = true, isWildBattle = true, ownOnField = 2)
        assertEquals(12, switched.lead?.mon?.level, "the lead, not the Pokemon sent out")
        assertEquals(263, switched.onField?.mon?.species, "which is the card's")
        assertEquals(5, TrackerState(1, listOf(egg), inBattle = false, isWildBattle = false).lead?.mon?.level, "an Egg alone is all there is")
    }

    // ------------------------------------------------------------------ the heals' whole HP (P2 #99)

    @Test
    fun `the heals carry the HP they add up to, rounded per item as the PC tracker does`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        val sb1 = 0x02010000L; val sb2 = 0x02018000L
        mem.put32(m.saveBlock1Ptr, sb1); mem.put32(m.saveBlock2Ptr, sb2)
        mem.put16(sb1 + m.bagItemsOffset, 13); mem.put16(sb1 + m.bagItemsOffset + 2, 1)   // one Potion, key 0
        party(mem, m, encodeMon(24, 258, 12, 30, 30))
        val s = GbaTracker(mem.reader(), m).read()
        assertEquals(Triple(67, 20, 1), Triple(s.healPercent, s.healHp, s.healCount), "66% and 19 HP before")
    }

    // ------------------------------------------------------------------ the game over (P3 #108)

    @Test
    fun `Gen 3's game over follows the loss condition, in battle only, and the Champion's win`() {
        for ((m, champions) in listOf(GameMap.EMERALD_U to listOf(804), GameMap.FIRERED_U_V10 to listOf(438, 439, 440))) {
            val mem = Mem()
            // A fainted lead, a fainted second, a higher-level third still standing.
            party(mem, m, encodeMon(24, 258, 20, 0, 50), encodeMon(48, 258, 15, 0, 40), encodeMon(72, 258, 30, 60, 60))
            val t = GbaTracker(mem.reader(), m)
            t.lossCondition = LossCondition.LEAD
            assertNull(t.read().gameOver, "${m.name}: a faint on the overworld ends nothing (Battle.lua:190)")
            battle(mem, m, flags = 0, own = 258, foe = 263)
            assertEquals(GameOver.LOST, settle(t).gameOver, "${m.name}: the lead")
            t.lossCondition = LossCondition.EITHER_OF_FIRST_TWO
            assertEquals(GameOver.LOST, t.read().gameOver, "${m.name}: either of the first two")
            t.lossCondition = LossCondition.HIGHEST_LEVEL
            assertNull(t.read().gameOver, "${m.name}: the highest level is standing")
            t.lossCondition = LossCondition.ENTIRE_PARTY
            assertNull(t.read().gameOver, "${m.name}: one is standing")
            mem.put16(m.party + 200L + 0x56, 0)
            assertEquals(GameOver.LOST, t.read().gameOver, "${m.name}: the whole party is down")

            // The Champion beaten: gBattleOutcome 1 against a final trainer; anyone else, or a loss to them, is not a win.
            for (champ in champions) {
                val w = Mem()
                party(w, m, encodeMon(24, 258, 60, 90, 90))
                w.put8(m.battleOutcome, 1); w.put16(m.trainerOpponent, champ)
                assertEquals(GameOver.WON, GbaTracker(w.reader(), m).read().gameOver, "${m.name}: $champ")
                w.put8(m.battleOutcome, 2)
                assertNull(GbaTracker(w.reader(), m).read().gameOver, "${m.name}: lost to $champ")
            }
            val other = Mem()
            party(other, m, encodeMon(24, 258, 60, 90, 90))
            other.put8(m.battleOutcome, 1); other.put16(m.trainerOpponent, champions.max() + 1)
            assertNull(GbaTracker(other.reader(), m).read().gameOver, "${m.name}: not the Champion")
        }
    }

    // ------------------------------------------------------------------ the first fishing battle (P3 #109)

    private val sb1 = 0x02010000L
    private val sb2 = 0x02018000L
    private val key = 0x5A5A1234L

    private fun stat(mem: Mem, m: GameMap, index: Int, value: Int) =
        mem.put32(sb1 + m.gameStatsOffset + index * 4L, value.toLong() xor key)

    private fun saveIn(mem: Mem, m: GameMap) {
        mem.put32(m.saveBlock1Ptr, sb1); mem.put32(m.saveBlock2Ptr, sb2)
        mem.put32(sb2 + m.encryptionKeyOffset, key)
        party(mem, m, encodeMon(24, 258, 12, 30, 30))
    }

    @Test
    fun `the first rod battle after the tracker starts is the rod's, not Surfing`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        saveIn(mem, m)
        stat(mem, m, 12, 5)                                  // FISHING_CAPTURES in the save
        val t = GbaTracker(mem.reader(), m)
        t.read()                                             // the overworld: Tracker.resetData's moment
        stat(mem, m, 12, 6)                                  // a bite, counted before the battle starts
        mem.put16(m.specialVarItemId, 262)                   // the Old Rod
        mem.put16(m.battleTerrain, 4)                        // water
        battle(mem, m, flags = 0, own = 258, foe = 129)
        assertEquals("Old Rod", settle(t).encounterArea)
    }

    @Test
    fun `the first Rock Smash battle after the tracker starts is Rock Smash, not Walking`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        saveIn(mem, m)
        stat(mem, m, 19, 5)                                  // USED_ROCK_SMASH
        val t = GbaTracker(mem.reader(), m)
        t.read()
        stat(mem, m, 19, 6)
        mem.put16(m.specialVarResultAny, 1)
        battle(mem, m, flags = 0, own = 258, foe = 74)
        assertEquals("RockSmash", settle(t).encounterArea)
    }

    @Test
    fun `a tracker that starts in the middle of a battle still takes the counts there`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        saveIn(mem, m)
        stat(mem, m, 12, 6)
        mem.put16(m.specialVarItemId, 262); mem.put16(m.battleTerrain, 4)
        battle(mem, m, flags = 0, own = 258, foe = 129)
        assertEquals("Surfing", settle(GbaTracker(mem.reader(), m)).encounterArea, "no read outside a battle to compare with")
    }

    // ------------------------------------------------------------------ the rival, kept with the run (P2 #130)

    /**
     * Every boss of FireRed's cap table in gTrainers, so the table is all read from the game and kept, as on a real ROM:
     * the three Champion teams at different levels, Left (439) the lowest, and everyone else at 50.
     */
    private fun champions(mem: Mem, m: GameMap) {
        val champion = mapOf(438 to 61, 439 to 59, 440 to 63)
        val ids = com.ironmonone.tracker.nuzlocke.LevelCapTable.standard("frlg").bosses.flatMap { it.trainerIds }
        for (id in ids) {
            val at = m.gTrainers + id * 0x28L
            val partyAt = 0x08F00000L + id * 0x10L
            mem.put8(at + 0x20, 1); mem.put32(at + 0x24, partyAt)
            mem.put8(partyAt + 2, champion[id] ?: 50); mem.put16(partyAt + 4, 9)
        }
    }

    @Test
    fun `a rival learned in battle is handed over once, and given back to a new tracker it filters and caps`() {
        val m = GameMap.FIRERED_U_V10
        val mem = Mem()
        champions(mem, m)
        party(mem, m, encodeMon(24, 4, 6, 20, 20))
        val t = GbaTracker(mem.reader(), m)
        assertTrue(t.levelCaps()?.fromRom == true, "every boss read from the game, so the table is kept")
        assertEquals(63, t.levelCaps()?.byKey("champion")?.cap, "the rival unknown: the strongest of the three")
        assertEquals(listOf(326, 327, 328), t.trainersForRoute(5))
        // The lab battle against trainer 327, the Left rival.
        battle(mem, m, flags = 0x8L, own = 4, foe = 7)
        mem.put16(m.trainerOpponent, 327)
        settle(t)
        assertEquals("Left", t.rivalChoice)
        assertEquals(59, t.levelCaps()?.byKey("champion")?.cap, "worked out again once the rival is known")
        assertEquals("Left", t.takeLearnedRival())
        assertNull(t.takeLearnedRival(), "handed over once")

        // The next visit to Play makes a new tracker: given the rival back, it lists and caps as the first did.
        val next = GbaTracker(Mem().also { champions(it, m) }.reader(), m)
        assertEquals(listOf(326, 327, 328), next.trainersForRoute(5))
        next.restoreRival("Nonsense")
        assertNull(next.rivalChoice, "only a rival this game's table has")
        assertEquals(63, next.levelCaps()?.byKey("champion")?.cap)
        next.restoreRival("Left")
        assertEquals("Left", next.rivalChoice)
        assertEquals(listOf(327), next.trainersForRoute(5))
        assertEquals(59, next.levelCaps()?.byKey("champion")?.cap)
        assertNull(next.takeLearnedRival(), "a rival given back is not handed over again")
    }

    // ------------------------------------------------------------------ Battle Tent and Frontier battles (P2 #141)

    @Test
    fun `a facility battle is read as one, and held until the party is healthy again`() {
        val m = GameMap.EMERALD_U
        val mem = Mem()
        party(mem, m, encodeMon(24, 258, 30, 50, 50), encodeMon(48, 258, 30, 50, 50))
        val t = GbaTracker(mem.reader(), m)
        assertEquals(false, t.read().nuz?.facility)
        // A Verdanturf Battle Tent battle: a trainer battle with BATTLE_TYPE_PALACE (bit 17).
        battle(mem, m, flags = 0x8L or 0x20000L, own = 258, foe = 263)
        assertEquals(true, settle(t).nuz?.facility)
        // Lost: the entrants down after the battle, until the game hands the real party back.
        mem.put16(m.party + 0x56, 0); mem.put16(m.party + 100L + 0x56, 0)
        endBattle(mem, m, outcome = 2)
        val after = t.read()
        assertFalse(after.inBattle)
        assertEquals(true, after.nuz?.facility, "the entrants at 0 HP are the lent party's")
        mem.put16(m.party + 0x56, 50); mem.put16(m.party + 100L + 0x56, 50)
        assertEquals(false, t.read().nuz?.facility, "the real party is back and healthy")
        // The real party back with someone at 0 HP (a Pokemon that fainted before the challenge): the hold ends with the
        // lent party, so a faint after it is the run's again.
        battle(mem, m, flags = 0x8L or 0x20000L, own = 258, foe = 263)
        settle(t)
        endBattle(mem, m, outcome = 2)
        mem.put16(m.party + 0x56, 0)
        assertEquals(true, t.read().nuz?.facility)
        party(mem, m, encodeMon(24, 258, 30, 0, 50), encodeMon(48, 258, 30, 50, 50), encodeMon(72, 258, 30, 0, 50))
        assertEquals(false, t.read().nuz?.facility, "another party than the battle's is the real one")
        // An ordinary trainer battle is not one, and ends the hold at once.
        mem.put16(m.party + 0x56, 0)
        battle(mem, m, flags = 0x8L, own = 258, foe = 263)
        assertEquals(false, settle(t).nuz?.facility)
    }

    @Test
    fun `the facility bits are each game's own`() {
        val pal = 0x8L or 0x20000L
        assertTrue(GbaTracker(Mem().reader(), GameMap.EMERALD_U).isFacilityBattle(pal), "Emerald's Palace")
        assertTrue(GbaTracker(Mem().reader(), GameMap.EMERALD_U).isFacilityBattle(0x8L or 0x100L), "Emerald's Battle Tower")
        for (bit in 16..21) assertTrue(GbaTracker(Mem().reader(), GameMap.EMERALD_U).isFacilityBattle(0x8L or (1L shl bit)), "Emerald bit $bit")
        assertFalse(GbaTracker(Mem().reader(), GameMap.EMERALD_U).isFacilityBattle(0x8L or 0x40L or 0x400000L), "Steven's multi battle")
        assertFalse(GbaTracker(Mem().reader(), GameMap.FIRERED_U_V10).isFacilityBattle(pal), "FireRed's bit 17 is a scripted wild battle")
        assertTrue(GbaTracker(Mem().reader(), GameMap.RUBY_U).isFacilityBattle(0x8L or 0x100L), "Ruby's Battle Tower")
        assertFalse(GbaTracker(Mem().reader(), GameMap.RUBY_U).isFacilityBattle(pal))
        assertFalse(GbaTracker(Mem().reader(), GameMap.EMERALD_U.copy(expandedSpeciesIds = true)).isFacilityBattle(pal), "not on Nat. Dex")
    }
}
