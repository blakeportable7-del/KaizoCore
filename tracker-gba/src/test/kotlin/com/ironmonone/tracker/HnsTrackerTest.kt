package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Heart & Soul tracker profile (HnsMaps, HnsData, HnsMon, HnsLayout) against the real comfort build,
 * hns-kaizo.gba (CRC E35A0E40), read where it lies: IRONMON_HNS, else this checkout's .vendor/hns, else the main
 * checkout's (a worktree can carry a newer build than the main checkout). Without it
 * every test here returns; under IRONMON_REQUIRE_DUMPS a missing ROM fails instead. The RAM is synthetic: parties,
 * save blocks and battles are written into it with the game's own algorithm and offsets, then read back.
 */
class HnsTrackerTest {
    companion object {
        /** IRONMON_HNS, else the first .vendor/hns holding hns-kaizo.gba from the working folder up, else the main checkout's. */
        fun romDir(): File? {
            System.getenv("IRONMON_HNS")?.let(::File)?.takeIf { it.isDirectory }?.let { return it }
            var d: File? = File("").absoluteFile
            while (d != null) {
                File(d, ".vendor/hns").takeIf { File(it, "hns-kaizo.gba").isFile }?.let { return it }
                d = d.parentFile
            }
            return File("C:/Users/bepor/IronMonOne/.vendor/hns").takeIf { it.isDirectory }
        }
    }


    private val rom: ByteArray? by lazy {
        Dumps.file(HnsTrackerTest.romDir(), "hns-kaizo.gba")?.readBytes()
    }

    /** The ROM plus writable EWRAM and IWRAM. */
    private class Mem(val rom: ByteArray) : MemoryReader {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        private fun region(a: Long): Pair<ByteArray, Int>? = when (a) {
            in 0x08000000L..0x09FFFFFFL -> rom to (a - 0x08000000L).toInt()
            in 0x02000000L..0x0203FFFFL -> ewram to (a - 0x02000000L).toInt()
            in 0x03000000L..0x03007FFFL -> iwram to (a - 0x03000000L).toInt()
            else -> null
        }
        override fun read(address: Long, length: Int): ByteArray {
            val (b, o) = region(address) ?: return ByteArray(0)
            if (o < 0 || o >= b.size) return ByteArray(0)
            return b.copyOfRange(o, minOf(b.size, o + length))
        }
        fun write(address: Long, bytes: ByteArray) { val (b, o) = region(address)!!; bytes.copyInto(b, o) }
        fun w8(a: Long, v: Int) = write(a, byteArrayOf(v.toByte()))
        fun w16(a: Long, v: Int) = write(a, byteArrayOf(v.toByte(), (v shr 8).toByte()))
        fun w32(a: Long, v: Long) = write(a, ByteArray(4) { (v ushr (8 * it)).toByte() })
    }

    /** Writes [v] into [f] of the struct at [base] in [b], bitfields included: the inverse of HnsField.at. */
    private fun put(f: HnsField, b: ByteArray, base: Int, v: Int, index: Int = 0) {
        val unit = if (f.count > 1) f.size / f.count else f.size
        val o = base + f.offset + index * unit
        var cur = 0L
        for (i in 0 until unit) cur = cur or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        val (sh, w) = if (f.count > 1) 0 to unit * 8 else f.shift to f.width
        val mask = (if (w >= 64) -1L else (1L shl w) - 1) shl sh
        cur = (cur and mask.inv()) or ((v.toLong() shl sh) and mask)
        for (i in 0 until unit) b[o + i] = (cur ushr (8 * i)).toByte()
    }

    private val charOf: Map<Char, Int> by lazy { Gen3Charmap.MAP.entries.filter { it.value.length == 1 }.associate { it.value[0] to it.key } }
    private fun encode(s: String, len: Int) = ByteArray(len) { i -> (if (i < s.length) charOf.getValue(s[i]) else 0xFF).toByte() }

    /** A party Pokemon, built and encrypted the way the game does (BoxPokemon key, substructure order, checksum). */
    private fun partyMon(
        pid: Long, otId: Long, species: Int, level: Int, nickname: String, moves: List<Int>, pp: List<Int>,
        ivs: List<Int>, evs: List<Int>, abilityNum: Int, heldItem: Int = 0, exp: Int = 0, friendship: Int = 70,
        ppBonuses: Int = 0, status: Int = 0, hp: Int = 30, maxHp: Int = 40, stats: List<Int> = listOf(20, 21, 22, 23, 24),
        isEgg: Boolean = false,
    ): ByteArray {
        val B = HnsLayout.BoxPokemon; val P = HnsLayout.Pokemon
        val mon = ByteArray(P.SIZE)
        put(B.personality, mon, 0, pid.toInt()); put(B.otId, mon, 0, otId.toInt())
        val nick = encode(nickname, 12)
        for (i in 0 until 10) mon[B.nickname.offset + i] = nick[i]
        val plain = ByteArray(48)
        val slot = PokemonDecoder.SLOT_OF[(pid % 24).toInt()]
        val g = slot[0] * 12; val a = slot[1] * 12; val e = slot[2] * 12; val m = slot[3] * 12
        val S0 = HnsLayout.PokemonSubstruct0; val S1 = HnsLayout.PokemonSubstruct1; val S2 = HnsLayout.PokemonSubstruct2; val S3 = HnsLayout.PokemonSubstruct3
        put(S0.species, plain, g, species); put(S0.heldItem, plain, g, heldItem); put(S0.experience, plain, g, exp)
        put(S0.friendship, plain, g, friendship); put(S0.ppBonuses, plain, g, ppBonuses)
        put(S0.nickname11, plain, g, nick[10].toInt() and 0xFF); put(S0.nickname12, plain, g, nick[11].toInt() and 0xFF)
        listOf(S1.move1, S1.move2, S1.move3, S1.move4).forEachIndexed { i, f -> put(f, plain, a, moves[i]) }
        listOf(S1.pp1, S1.pp2, S1.pp3, S1.pp4).forEachIndexed { i, f -> put(f, plain, a, pp[i]) }
        listOf(S2.hpEV, S2.attackEV, S2.defenseEV, S2.speedEV, S2.spAttackEV, S2.spDefenseEV).forEachIndexed { i, f -> put(f, plain, e, evs[i]) }
        listOf(S3.hpIV, S3.attackIV, S3.defenseIV, S3.speedIV, S3.spAttackIV, S3.spDefenseIV).forEachIndexed { i, f -> put(f, plain, m, ivs[i]) }
        put(S3.abilityNum, plain, m, abilityNum); put(S3.isEgg, plain, m, if (isEgg) 1 else 0)
        var sum = 0
        for (i in 0 until 24) sum += plain.u16(i * 2)
        put(B.checksum, mon, 0, sum and 0xFFFF)
        val key = pid xor otId
        for (w in 0 until 12) mon.putU32(B.secure.offset + w * 4, plain.u32(w * 4) xor key)
        put(P.status, mon, 0, status); put(P.level, mon, 0, level); put(P.hp, mon, 0, hp); put(P.maxHP, mon, 0, maxHp)
        listOf(P.attack, P.defense, P.speed, P.spAttack, P.spDefense).forEachIndexed { i, f -> put(f, mon, 0, stats[i]) }
        return mon
    }

    private val sb1 = 0x02020000L
    private val sb2 = 0x02030000L
    private val key = 0x1234ABCDL

    private fun game(): Pair<Mem, GbaTracker>? {
        val r = rom ?: return null
        val mem = Mem(r)
        mem.w32(HnsLayout.gSaveBlock1Ptr, sb1); mem.w32(HnsLayout.gSaveBlock2Ptr, sb2)
        mem.w32(sb2 + HnsLayout.SaveBlock2.encryptionKey.offset, key)
        val map = GameMap.resolve(mem)
        return mem to GbaTracker(mem, map)
    }

    private val pikachu by lazy {
        partyMon(pid = 0x2F15A7C3L, otId = 0x0BAD1DEAL, species = 25, level = 12, nickname = "SPARKYTHEBIG",
            moves = listOf(84, 98, 1, 86), pp = listOf(30, 25, 35, 20), ivs = listOf(31, 0, 15, 7, 20, 25),
            evs = listOf(10, 20, 30, 40, 50, 60), abilityNum = 2, heldItem = 520, exp = 1900, friendship = 99,
            ppBonuses = 3, status = 0x40)
    }

    @Test
    fun `the generated layout is this layout export's`() {
        val json = File("../app/src/main/assets/hns/layout-kaizo.json").readText()
        assertEquals("%08X".format(HnsLayout.BUILD_CRC), Regex("\"buildCrc\":\\s*\"(\\w+)\"").find(json)!!.groupValues[1])
        val syms = Regex("\"(\\w+)\":\\s*\\{\"addr\":\\s*\"0x([0-9A-F]{8})\",\\s*\"size\"").findAll(json.substringAfter("\"symbols\"").substringBefore("\"labels\""))
            .associate { it.groupValues[1] to it.groupValues[2].toLong(16) }
        assertTrue(syms.size >= 70, "symbols read: ${syms.size}")
        for ((n, a) in syms) assertEquals(a, HnsLayout::class.java.getField(n).getLong(null), n)
        // The party is the 100-byte Gen 3 shape with every substructure packed; the battle struct grew to 136 bytes.
        assertEquals(100, HnsLayout.Pokemon.SIZE); assertEquals(11, HnsLayout.PokemonSubstruct0.species.width)
        assertEquals(136, HnsLayout.BattlePokemon.SIZE)
        assertEquals(HnsLayout.BattlePokemon.personality.offset, HnsLayout.BattlePokemon.volatiles.offset - 8, "the personality is read 8 below status2Offset")
    }

    @Test
    fun `a party built with the game's own encryption decodes back`() {
        val (mem, t) = game() ?: return
        assertEquals(HnsMaps.NAME, t.map.name)
        val m = PokemonDecoder.decode(pikachu, t.map.monLayout)
        assertEquals(25, m.species); assertEquals(12, m.level); assertEquals("SPARKYTHEBIG", m.nickname)
        assertEquals(listOf(84, 98, 1, 86), m.moves); assertEquals(listOf(30, 25, 35, 20), m.pp)
        assertEquals(listOf(31, 0, 15, 7, 20, 25), m.ivs); assertEquals(listOf(10, 20, 30, 40, 50, 60), m.evs)
        assertEquals(2, m.abilitySlot); assertEquals(520, m.heldItem); assertEquals(1900L, m.exp); assertEquals(99, m.friendship)
        assertEquals(listOf(3, 0, 0, 0), m.ppUps); assertEquals((0x2F15A7C3L % 25).toInt(), m.nature)

        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        val s = t.read()
        assertFalse(s.unreadable, s.diagnostics)
        val p = s.party.single()
        assertEquals("PIKACHU", p.speciesName.uppercase())
        // Ability slot 2 is the hidden ability: Pikachu's abilities are Static, none, Lightning Rod.
        assertEquals("LIGHTNING ROD", p.abilityName.uppercase())
        assertEquals("ORAN BERRY", p.itemName.uppercase())
        assertEquals(listOf("THUNDER SHOCK", "QUICK ATTACK", "POUND", "THUNDER WAVE"), p.moveNames.map { it.uppercase() })
        // Thunder Shock's 30 PP with three PP Ups is 48; its type is Electric, its category special.
        val ts = p.moveRows.first()
        assertEquals(48, ts.ppMax); assertEquals(13, ts.type); assertEquals("SPE", ts.category); assertEquals(40, ts.power)
        assertEquals("PAR", p.statusCondition)
        // Medium Fast: 12^3 = 1728 to 13^3 = 2197.
        assertEquals(1900 - 1728, p.expNow); assertEquals(2197 - 1728, p.expTotal)
        assertEquals(listOf(13, 13), listOf(p.base!!.type1, p.base!!.type2))
        assertTrue(p.movesTotal > 0 && p.nextMoveLevel != null, "Pikachu's level-up moves are read: ${p.movesLearned}/${p.movesTotal}")
    }

    @Test
    fun `species, moves, learnsets and the type chart come out of the ROM`() {
        val (_, t) = game() ?: return
        assertEquals("BULBASAUR", t.speciesName(1).uppercase())
        val b = t.baseStats(1)!!
        assertEquals(listOf(45, 49, 49, 45, 65, 65), listOf(b.hp, b.atk, b.def, b.spe, b.spAtk, b.spDef))
        assertEquals(12 to 3, b.type1 to b.type2, "Grass/Poison in Gen 3's numbering")
        assertEquals(listOf("OVERGROW", "CHLOROPHYLL"), t.possibleAbilities(1).map { it.uppercase() })
        assertEquals(320, t.baseStats(25)!!.bst)
        // Species past Gen 3's 411 and the Hoenn block that Gen 3 numbers 277 on: Treecko is 252 here.
        assertEquals("TREECKO", t.speciesName(252).uppercase())
        assertTrue(t.speciesExists(252))
        val pound = t.moveRowFor(1)!!
        assertEquals("POUND", pound.name.uppercase())
        assertEquals(listOf(40, 0, 100, 35), listOf(pound.power, pound.type, pound.acc, pound.pp))
        assertEquals("PHY", pound.category); assertEquals(true, pound.contact); assertEquals(0, pound.priority)
        assertEquals(1, t.moveRowFor(98)!!.priority, "Quick Attack")
        assertEquals(18, t.moveRowFor(584)!!.type, "Fairy Wind is Fairy")
        assertTrue(!t.moveDescription(1).isNullOrBlank())
        val learn = t.learnset(1)
        assertEquals("TACKLE", t.moveName(learn.first().second).uppercase())
        assertTrue(learn.all { it.first in 0..100 })
        // Evolutions from the ROM: a level, friendship, Pikachu's stone, Eevee's many stones.
        assertEquals("16", t.evolution(1)); assertEquals("FRIEND", t.evolution(172))
        assertEquals("THUNDER", t.evolution(25)); assertEquals("EEVEE_STONES_NATDEX", t.evolution(133))
        assertNull(t.evolution(26))
        assertTrue(t.friendshipRequired() in 100..220)
        assertTrue(t.fairyChart)
        assertEquals(listOf("Normal", "Fighting"), t.typeNames.take(2)); assertEquals("Fairy", t.typeNames.last())
    }

    @Test
    fun `the ROM's type chart is the one the tracker computes with`() {
        val r = rom ?: return
        val d = HnsData(Mem(r))
        assertEquals(2.0, d.typeEffect(11, 10)); assertEquals(0.5, d.typeEffect(10, 11)); assertEquals(0.0, d.typeEffect(0, 7))
        assertEquals(2.0, d.typeEffect(18, 16), "Fairy on Dragon")
        val bad = ArrayList<String>()
        for (a in Gen3Types.typesFor(true)) for (df in Gen3Types.typesFor(true)) {
            val rom = d.typeEffect(a, df); val ours = Gen3Types.effect(a, df, gen1 = false, natDex = true)
            if (rom != ours) bad += "${Gen3Types.name(a)}>${Gen3Types.name(df)} ROM $rom, tracker $ours"
        }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun `items, maps, trainers and wild encounters`() {
        val (_, t) = game() ?: return
        assertEquals("POTION", t.itemName(28).uppercase())
        val r = rom!!
        val d = HnsData(Mem(r))
        assertEquals(20.0 to false, d.healItems[28]); assertEquals(100.0 to true, d.healItems[31])
        assertEquals(25.0 to true, d.healItems[523], "Sitrus Berry, a quarter")
        assertEquals("Poison", d.statusItems[43]); assertEquals("All", d.statusItems[48])
        assertTrue(49 in d.ppItems); assertTrue(1 in d.balls && 2 in d.balls); assertTrue(121 in d.battleItems)
        // Route 29 (MAP_ROUTE29_HNS = group 0, num 11).
        val route29 = 11
        assertEquals("ROUTE 29", t.routeInfo(route29)!!.first.uppercase())
        val enc = t.routeEncounters(route29)
        assertTrue("Walking" in enc && enc.getValue("Walking").isNotEmpty(), "Route 29 walking: $enc")
        assertTrue(enc.getValue("Walking").all { t.speciesExists(it.id) && it.minLv in 1..100 })
        // Sawyer (trainer 1) and Falkner (402): names, a party, the gym group, where they stand.
        val sawyer = t.trainer(1)!!
        assertEquals("SAWYER", sawyer.name.uppercase())
        assertEquals("GEODUDE", t.speciesName(sawyer.party.first().species).uppercase())
        val falkner = t.trainer(402)!!
        assertEquals("FALKNER", falkner.name.uppercase()); assertTrue(falkner.party.isNotEmpty())
        assertEquals("Gym", t.trainerGroup(402))
        assertTrue(402 in t.trainersOnRoute(773), "Falkner stands in the Violet City gym: ${t.trainersOnRoute(773)}")
    }

    @Test
    fun `badges, the bag and the map are read from the save blocks`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        // Badges 1, 2 (Zephyr, Hive) and 9 (Boulder): FLAG_BADGE01_GET, 02 and 09.
        val flags = sb1 + HnsLayout.SaveBlock1.flags.offset
        for (f in listOf(HnsLayout.FLAG_BADGE01_GET, HnsLayout.FLAG_BADGE02_GET, HnsLayout.FLAG_BADGE09_GET)) {
            val a = flags + f / 8
            mem.w8(a, (mem.read(a, 1)[0].toInt() or (1 shl (f % 8))))
        }
        val bag = sb1 + HnsLayout.SaveBlock1.bag.offset
        val k = (key and 0xFFFF).toInt()
        fun slot(pocket: HnsField, i: Int, item: Int, qty: Int) { mem.w16(bag + pocket.offset + i * 4L, item); mem.w16(bag + pocket.offset + i * 4L + 2, qty xor k) }
        slot(HnsLayout.Bag.medicine, 0, 28, 3)    // Potion x3: 60 HP
        slot(HnsLayout.Bag.medicine, 1, 43, 1)    // Antidote
        slot(HnsLayout.Bag.berries, 0, 523, 2)    // Sitrus Berry x2: 50%
        slot(HnsLayout.Bag.pokeBalls, 0, 1, 5)    // Poke Ball x5
        slot(HnsLayout.Bag.items, 0, 213, 1)      // Thunder Stone
        // On Route 29.
        mem.w8(sb1 + HnsLayout.SaveBlock1.location.offset, 0); mem.w8(sb1 + HnsLayout.SaveBlock1.location.offset + 1, 11)
        val s0 = t.read(); val s = t.read()   // a map id is adopted on its second read
        assertEquals(0b1_0000_0011, s.badges); assertEquals("GSC", s.badgeSet)
        // Pikachu has 40 max HP: 3 Potions are 150% of it, 2 Sitrus Berries 50%.
        assertEquals(5, s.healCount); assertEquals(200, s.healPercent)
        assertEquals(mapOf(4 to 5), t.bagBalls())
        val rows = t.healsInBag(s.party.first())
        assertEquals("HP", rows.first { it.id == 28 }.category); assertEquals("Status", rows.first { it.id == 43 }.category)
        assertEquals("Evo", rows.first { it.id == 213 }.category)
        // Pikachu evolves by Thunder Stone: with one in the bag it is ready.
        assertEquals(EvoText.Tone.READY, s.party.first().evo!!.tone)
        assertEquals(11, s.mapId); assertEquals("ROUTE 29", s.routeName?.uppercase())
        assertTrue(s.routeSpecies.isNotEmpty())
        assertTrue(s0.mapId == null)
    }

    private fun battle(mem: Mem, enemySpecies: Int, trainer: Int?) {
        val bm = HnsLayout.BattlePokemon
        mem.w8(HnsLayout.gBattlersCount, 2)
        val mons = ByteArray(bm.SIZE * 4)
        put(bm.species, mons, 0, 25); put(bm.hp, mons, 0, 30); put(bm.level, mons, 0, 12); put(bm.maxHP, mons, 0, 40)
        put(bm.species, mons, bm.SIZE, enemySpecies); put(bm.hp, mons, bm.SIZE, 20); put(bm.level, mons, bm.SIZE, 10); put(bm.maxHP, mons, bm.SIZE, 25)
        // Its battle types as the expansion numbers them: Normal is 1, Fairy 19.
        put(bm.types, mons, bm.SIZE, HnsLayout.TYPE_NORMAL, 0); put(bm.types, mons, bm.SIZE, HnsLayout.TYPE_FAIRY, 1)
        for (i in 0 until 8) { put(bm.statStages, mons, 0, 6, i); put(bm.statStages, mons, bm.SIZE, 6, i) }
        put(bm.statStages, mons, bm.SIZE, 7, 1)
        mem.write(HnsLayout.gBattleMons, mons)
        mem.w16(HnsLayout.gBattlerPartyIndexes, 0); mem.w16(HnsLayout.gBattlerPartyIndexes + 2, 0)
        mem.w8(HnsLayout.gBattleOutcome, 0)
        mem.w32(HnsLayout.gBattleTypeFlags, if (trainer != null) 8L else 0L)
        trainer?.let { mem.w16(HnsLayout.gTrainerBattleParameter + HnsLayout.TrainerBattleParameter.opponentA.offset, it) }
        mem.w32(HnsLayout.gBattleMainFunc, HnsLayout.HandleTurnActionSelectionState)
        val foe = partyMon(pid = 0x00C0FFEEL, otId = 0x11112222L, species = enemySpecies, level = 10, nickname = "FOE",
            moves = listOf(33, 45, 0, 0), pp = listOf(35, 40, 0, 0), ivs = List(6) { 10 }, evs = List(6) { 0 }, abilityNum = 0,
            hp = 20, maxHp = 25)
        mem.write(HnsLayout.gEnemyParty, foe)
    }

    @Test
    fun `a trainer battle, the opponent with its types, its moves seen and its ability revealed`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        battle(mem, enemySpecies = 39, trainer = 1)   // Jigglypuff, against Sawyer
        t.read()
        val s = t.read()
        assertTrue(s.inBattle); assertFalse(s.isWildBattle)
        assertEquals(1, s.opponentTrainerId)
        val e = s.enemy!!
        assertEquals("JIGGLYPUFF", e.speciesName.uppercase())
        assertEquals(0 to 18, e.type1 to e.type2, "Normal/Fairy in Gen 3's numbering")
        assertEquals(7, e.statStages["ATK"])
        assertTrue(e.abilityGuess.uppercase().contains("CUTE CHARM"), e.abilityGuess)
        assertEquals(listOf(true), s.enemyTeam)

        // The foe uses Tackle: its action under way (gCurrentTurnActionNumber 1, B_ACTION_USE_MOVE), Tackle the
        // opposing side's last move. Recorded one poll after the action began.
        mem.w8(HnsLayout.gCurrentTurnActionNumber, 1); mem.w8(HnsLayout.gActionsByTurnOrder + 1, HnsLayout.B_ACTION_USE_MOVE)
        mem.w8(HnsLayout.gBattlerAttacker, 1)
        mem.w16(HnsLayout.gBattleResults + HnsLayout.BattleResults.lastUsedMoveOpponent.offset, 33)
        t.read()
        val after = t.read()
        assertEquals(listOf("TACKLE"), after.enemy!!.movesSeen.map { it.uppercase() })

        // Its ability pop-up: gBattlescriptCurrInstr inside BattleScript_AbilityPopUp, gBattlerAbility the foe.
        mem.w8(HnsLayout.gBattlerAbility, 1)
        mem.w32(HnsLayout.gBattlescriptCurrInstr, HnsLayout.BattleScript_AbilityPopUp.first + 5)
        val reveals = t.readAbilityTriggers()
        assertEquals(listOf(39 to t.abilityName(t.baseStats(39)!!.ability1)), reveals)
    }

    @Test
    fun `a move the foe could not use is not revealed`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        battle(mem, enemySpecies = 39, trainer = null)
        t.read(); t.read()
        // DoAttackCanceler marked it: gBattleStruct->unableToUseMove (fully paralysed, asleep...).
        val bs = 0x02010000L
        mem.w32(HnsLayout.gBattleStruct, bs)
        val f = HnsLayout.BattleStruct.unableToUseMove
        mem.w8(bs + f.offset, 1 shl f.shift)
        mem.w8(HnsLayout.gCurrentTurnActionNumber, 1); mem.w8(HnsLayout.gActionsByTurnOrder + 1, HnsLayout.B_ACTION_USE_MOVE)
        mem.w8(HnsLayout.gBattlerAttacker, 1)
        mem.w16(HnsLayout.gBattleResults + HnsLayout.BattleResults.lastUsedMoveOpponent.offset, 33)
        t.read()
        val s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        assertEquals(emptyList(), s.enemy!!.movesSeen)
    }

    @Test
    fun `Battle Details reads the build's volatiles, side and field statuses, weather and BattleStruct`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        battle(mem, enemySpecies = 39, trainer = null)
        assertTrue(t.hasBattleDetails, "the Battle Details button is offered for Heart & Soul")
        t.read(); t.read()
        val BM = HnsLayout.BattlePokemon; val V = HnsLayout.Volatiles
        val mons = mem.read(HnsLayout.gBattleMons, BM.SIZE * 2)
        // Pikachu: confused, seeded by the foe (LEECHSEEDED_BY(1) = 2), Substitute up. The foe: taunted for 2 turns.
        put(V.confusionTurns, mons, BM.volatiles.offset, 3)
        put(V.leechSeed, mons, BM.volatiles.offset, 2)
        put(V.substitute, mons, BM.volatiles.offset, 1)
        put(V.tauntTimer, mons, BM.SIZE + BM.volatiles.offset, 2)
        mem.write(HnsLayout.gBattleMons, mons)
        // The player's side: Reflect with 4 turns and two layers of Spikes; Tailwind on the foe's side.
        mem.w32(HnsLayout.gSideStatuses, HnsLayout.SIDE_STATUS_REFLECT.toLong())
        mem.w16(HnsLayout.gSideTimers + HnsLayout.SideTimer.reflectTimer.offset, 4)
        mem.w8(HnsLayout.gSideTimers + HnsLayout.SideTimer.spikesAmount.offset, 2)
        mem.w32(HnsLayout.gSideStatuses + 4, HnsLayout.SIDE_STATUS_TAILWIND.toLong())
        mem.w16(HnsLayout.gSideTimers + HnsLayout.SideTimer.SIZE + HnsLayout.SideTimer.tailwindTimer.offset, 3)
        // The field: Trick Room for 5 turns, Electric Terrain for 2. Heavy rain. Turn 3.
        mem.w32(HnsLayout.gFieldStatuses, (HnsLayout.STATUS_FIELD_TRICK_ROOM or HnsLayout.STATUS_FIELD_ELECTRIC_TERRAIN).toLong())
        mem.w16(HnsLayout.gFieldTimers + HnsLayout.FieldTimer.trickRoomTimer.offset, 5)
        mem.w16(HnsLayout.gFieldTimers + HnsLayout.FieldTimer.terrainTimer.offset, 2)
        mem.w32(HnsLayout.gBattleWeather, HnsLayout.B_WEATHER_RAIN_PRIMAL.toLong())
        mem.w8(HnsLayout.gBattleEnvironment, HnsLayout.BATTLE_ENVIRONMENT_CAVE)
        mem.w8(HnsLayout.gBattleResults + HnsLayout.BattleResults.battleTurnCounter.offset, 2)
        // gBattleStruct: Future Sight on the foe from Pikachu (battler 0), 2 turns.
        val bs = 0x02010000L
        mem.w32(HnsLayout.gBattleStruct, bs)
        mem.w16(bs + HnsLayout.BattleStruct.futureSight.offset + 4 + 2, 2 or (0 shl 10))
        val d = assertNotNull(t.battleDetails())
        assertEquals("Cave", d.terrain); assertEquals("Heavy Rain", d.weather); assertEquals(3, d.turn)
        val field = d.field.map { it.text }
        assertTrue("Weather: Heavy Rain" in field, "$field")
        assertTrue("Trick Room: 5 Turns Left" in field && "Electric Terrain: 2 Turns Left" in field, "$field")
        val mine = d.sides[0].map { it.text }; val theirs = d.sides[1].map { it.text }
        assertTrue(mine.any { it.uppercase() == "REFLECT: 4 TURNS LEFT" } && mine.any { it.uppercase() == "SPIKES: 2" }, "$mine")
        assertEquals(listOf("Tailwind: 3 Turns Left"), theirs)
        val me = d.mons[0].map { it.text.uppercase() }
        assertTrue("CONFUSED (1- 4 TURNS)" in me && "SUBSTITUTE" in me && "LEECH SEED (JIGGLYPUFF)" in me, "$me")
        val foe = d.mons[1].map { it.text.uppercase() }
        assertTrue("TAUNT: 2 TURNS LEFT" in foe && "FUTURE: 2 TURNS LEFT (PIKACHU)" in foe, "$foe")
    }

    @Test
    fun `the last-attack line counts what the foe's move took off the player's Pokemon`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        battle(mem, enemySpecies = 39, trainer = null)
        val BM = HnsLayout.BattlePokemon
        t.read(); t.read()
        // The foe's Tackle lands: attacker 1, its last move Tackle, Pikachu 30 -> 21 HP.
        mem.w8(HnsLayout.gBattlerAttacker, 1)
        mem.w16(HnsLayout.gBattleResults + HnsLayout.BattleResults.lastUsedMoveOpponent.offset, 33)
        t.read()
        mem.w16(HnsLayout.gBattleMons + BM.hp.offset, 21)
        t.read()
        // The next turn comes round: the line is ready, 9 damage from Tackle.
        mem.w8(HnsLayout.gBattlerAttacker, 0)
        mem.w8(HnsLayout.gBattleResults + HnsLayout.BattleResults.battleTurnCounter.offset, 1)
        val s = t.read()
        assertEquals(33, s.lastAttackMoveId); assertEquals(9, s.lastAttackDamage)
        assertEquals("TACKLE", s.lastAttackMove?.uppercase())
        // Pikachu's own recoil while it is the attacker is not the foe's damage.
        mem.w16(HnsLayout.gBattleMons + BM.hp.offset, 18)
        assertEquals(9, t.read().lastAttackDamage)
    }

    @Test
    fun `the sprite pack has a picture for every species with a National Dex number`() {
        val r = rom ?: return
        val d = HnsData(Mem(r))
        val missing = (1 until HnsLayout.NUM_SPECIES).filter { d.natDexNum(it) > 0 && HnsSprites.packId(it) == null }
        assertEquals(emptyList(), missing)
        assertEquals(1, HnsSprites.packId(1)); assertEquals(277, HnsSprites.packId(252), "Treecko is the pack's 277")
        assertEquals(412, HnsSprites.packId(387), "Turtwig is the pack's 412")
    }

    @Test
    fun `another Heart & Soul build is refused, not read with these addresses`() {
        val r = rom ?: return
        val other = r.copyOf()
        // Move Bulbasaur's name: the title still says Heart & Soul, the tables are not this build's.
        val at = (HnsLayout.gSpeciesInfo - 0x08000000L + HnsLayout.SpeciesInfo.SIZE + HnsLayout.SpeciesInfo.speciesName.offset).toInt()
        other[at] = 0
        assertNull(GameMap.resolveOrNull(Mem(other)))
        assertNotNull(GameMap.resolveOrNull(Mem(r)))
    }

    @Test
    fun `a run with random base stats is still this build to the tracker`() {
        val r = rom?.copyOf() ?: return
        // What every Kaizo mode does to Pikachu: other base stats. The tracker must still take the run.
        val pika = (HnsLayout.gSpeciesInfo - 0x08000000L).toInt() + 25 * HnsLayout.SpeciesInfo.SIZE
        for (i in 0 until 6) r[pika + i] = (77 + i).toByte()
        assertEquals(77, HnsData(Mem(r)).baseStats(25)!!.hp)
        assertTrue(HnsMaps.isKaizoBuild(Mem(r)))
        assertNotNull(GameMap.resolveOrNull(Mem(r)))
    }

    @Test
    fun `naming every species and move, as the log viewer does, reads the ROM a page at a time`() {
        val r = rom ?: return
        var calls = 0
        val mem = Mem(r)
        val counting = MemoryReader { a, n -> calls++; mem.read(a, n) }
        val t = GbaTracker(counting, GameMap.resolve(mem))
        // LogNames.of and logMoveTypes: every species' name, every move's name and row.
        val names = (1..t.speciesIdCount).map { t.speciesName(it) }
        val moves = (1..t.lastMoveId).map { t.moveName(it) to t.moveRowFor(it)?.type }
        assertEquals("BULBASAUR", names[0].uppercase())
        val plain = HnsData(mem); val paged = HnsData(RomPages(mem))
        assertEquals((1..t.speciesIdCount).map { plain.speciesName(it) }, (1..t.speciesIdCount).map { paged.speciesName(it) },
            "the pages give what the plain reads give")
        assertTrue(moves.count { it.second != null } > 800)
        // About 4,100 single reads before; now each 16 KB page once.
        assertTrue(calls < 250, "$calls reads of the running core")
        assertTrue(t.hnsRom!!.reads < 250)
    }

    @Test
    fun `the run is won by beating Red on Mt Silver, and Lance is an ordinary Champion fight`() {
        val json = File("../app/src/main/assets/hns/layout-kaizo.json").readText()
        fun trainer(n: String) = Regex("\"$n\":\\s*(\\d+)").find(json)!!.groupValues[1].toInt()
        for ((id, won) in listOf(trainer("TRAINER_RED_HNS") to true, trainer("TRAINER_RED_POSTOBC_HNS") to true,
                trainer("TRAINER_LANCE_1_HNS") to false, trainer("TRAINER_LANCE_2_HNS") to false)) {
            val (mem, t) = game() ?: return
            mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
            battle(mem, enemySpecies = 25, trainer = id)
            mem.w8(HnsLayout.gBattleOutcome, 1)
            t.read()
            assertEquals(if (won) GameOver.WON else null, t.read().gameOver, "trainer $id")
        }
    }

    @Test
    fun `Elm's three balls show their Pokemon, and the one being offered, as in the other Gen 3 labs`() {
        val (mem, t) = game() ?: return
        pokeBalls(mem, 5)
        onMap(mem, 1, 0); t.read()
        val s = t.read()
        assertTrue(s.inLab)
        val balls = t.starters()
        assertEquals(listOf("LEFT", "MIDDLE", "RIGHT"), balls.map { it.ball })
        assertEquals(listOf("CHIKORITA", "CYNDAQUIL", "TOTODILE"), balls.map { it.name.uppercase() })
        assertNull(s.starterOffered)
        // The middle ball's yes/no is open: PLAYER_STARTER_SPECIES (VAR_TEMP_2) holds it, VAR_RESULT is 255.
        val vars = sb1 + HnsLayout.SaveBlock1.vars.offset
        mem.w16(vars + 2L * (HnsLayout.VAR_TEMP_2 - HnsLayout.VARS_START), balls[1].species)
        mem.w16(HnsLayout.gSpecialVar_Result, 255)
        assertEquals(balls[1].species, t.read().starterOffered)
        // Answered NO: nothing is offered any more.
        mem.w16(HnsLayout.gSpecialVar_Result, 0)
        assertNull(t.read().starterOffered)
        // A randomized ROM's balls are what its scripts set.
        mem.write(HnsLayout.STARTER_BALL_SPECIES[0], byteArrayOf(25, 0))
        assertEquals("PIKACHU", t.starters()[0].name.uppercase())
    }

    @Test
    fun `the Safari Zone is known by its flag and its maps`() {
        val (mem, t) = game() ?: return
        assertFalse(t.isInSafariZone())
        val f = HnsLayout.FLAG_SYS_SAFARI_MODE
        val a = sb1 + HnsLayout.SaveBlock1.flags.offset + f / 8
        mem.w8(a, mem.read(a, 1)[0].toInt() or (1 shl (f % 8)))
        assertTrue(t.isInSafariZone())
        val json = File("../app/src/main/assets/hns/layout-kaizo.json").readText()
        fun map(n: String) = Regex("\"$n\":\\s*(\\d+)").find(json)!!.groupValues[1].toInt()
        assertTrue(t.isSafariMap(map("MAP_SAFARI_ZONE_TOP_MID_HNS")) && t.isSafariMap(map("MAP_FUCHSIA_CITY_SAFARI_ZONE_BEACH_HNS")))
        assertFalse(t.isSafariMap(map("MAP_SAFARI_ZONE_GATE_HNS")) || t.isSafariMap(map("MAP_ROUTE29_HNS")))
    }

    // ---------------------------------------------------------------- the Nuzlocke's gifts and statics

    /** On map ([group], [num]): SaveBlock1.location and gMapHeader, the RAM copy the game keeps of the map's ROM header. */
    private fun onMap(mem: Mem, group: Int, num: Int) {
        mem.w8(sb1 + HnsLayout.SaveBlock1.location.offset, group); mem.w8(sb1 + HnsLayout.SaveBlock1.location.offset + 1, num)
        val r = mem.rom
        fun u32(a: Long) = r.u32((a - 0x08000000L).toInt())
        val header = u32(u32(HnsLayout.gMapGroups + 4L * group) + 4L * num)
        val o = (header - 0x08000000L).toInt()
        mem.write(HnsLayout.gMapHeader, r.copyOfRange(o, o + HnsLayout.MapHeader.SIZE))
    }

    private fun pokeBalls(mem: Mem, n: Int) {
        val bag = sb1 + HnsLayout.SaveBlock1.bag.offset + HnsLayout.Bag.pokeBalls.offset
        mem.w16(bag, 1); mem.w16(bag + 2, n xor (key and 0xFFFF).toInt())
    }

    @Test
    fun `a Pokemon given in Elm's lab is a gift of New Bark Town to the Nuzlocke`() {
        val (mem, t) = game() ?: return
        pokeBalls(mem, 5)
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        val rules = com.ironmonone.tracker.nuzlocke.NuzlockeRules.forPreset(com.ironmonone.tracker.nuzlocke.NuzlockePreset.STANDARD)
        val ledger = com.ironmonone.tracker.nuzlocke.NuzlockeLedger(com.ironmonone.tracker.nuzlocke.RunMeta("hns-nz", "lib-hns", "Heart & Soul", rules, 1_000L))
        val engine = com.ironmonone.tracker.nuzlocke.NuzlockeEngine(ledger)
        var clock = 5_000L
        fun poll(): TrackerState = t.read().also { s -> Gen3Nuzlocke.snapshot(s)?.let { engine.update(it, clock) }; clock += 700 }
        // New Bark Town outdoors, where the town's map section is learned; then Elm's lab, a building in it.
        onMap(mem, 0, 0); poll(); val town = poll()
        assertEquals("NEW BARK TOWN", town.routeName?.uppercase())
        assertEquals(com.ironmonone.tracker.nuzlocke.Origin.STARTER, ledger.roster.values.single().origin)
        // Heart & Soul names a map by its region map section, so the lab already reads as the town (its map type is
        // MAP_TYPE_NONE in the game's data, not INDOOR, so the section rule has nothing to add here).
        onMap(mem, 1, 0); poll(); val lab = poll()
        assertEquals("NEW BARK TOWN", lab.routeName?.uppercase())
        assertEquals(town.nuz!!.mapSection, lab.nuz!!.mapSection)
        // A second Pokemon joins the party outside a battle.
        val togepi = partyMon(pid = 0x51515151L, otId = 0x0BAD1DEAL, species = 175, level = 5, nickname = "TOGEPI",
            moves = listOf(45, 0, 0, 0), pp = listOf(40, 0, 0, 0), ivs = List(6) { 5 }, evs = List(6) { 0 }, abilityNum = 0)
        mem.write(HnsLayout.gPlayerParty + HnsLayout.Pokemon.SIZE, togepi); mem.w8(HnsLayout.gPlayerPartyCount, 2)
        poll(); poll()
        val gift = ledger.roster.getValue(0x51515151L)
        assertEquals(com.ironmonone.tracker.nuzlocke.Origin.GIFT, gift.origin)
        val ev = ledger.events.singleOrNull { it.kind == "gift" } ?: error("events: " + ledger.events.map { it.kind + ": " + it.text })
        assertTrue("NEW BARK TOWN" in ev.text.uppercase(), ev.text)
        assertEquals("NEW BARK TOWN", gift.areaName.uppercase())
        // Togepi is the Nat. Dex build's 175 too, so the dupes clause sees its line.
        assertEquals(175, gift.species)
    }

    @Test
    fun `a battle a script started is a static to the Nuzlocke, a walking one the area's encounter`() {
        for ((cb, area, method) in listOf(
            Triple(HnsLayout.CB2_EndScriptedWildBattle, "Static", com.ironmonone.tracker.nuzlocke.Method.STATIC),
            Triple(HnsLayout.CB2_EndWildBattle, "Walking", com.ironmonone.tracker.nuzlocke.Method.WALK),
        )) {
            val (mem, t) = game() ?: return
            pokeBalls(mem, 5)
            mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
            onMap(mem, 0, 11); t.read(); t.read()
            // BattleSetup_StartScriptedWildBattle (dowildbattle) and BattleSetup_StartLegendaryBattle set this way back.
            mem.w32(HnsLayout.gMain + HnsLayout.Main.savedCallback.offset, cb)
            battle(mem, enemySpecies = 39, trainer = null)
            t.read()
            val s = t.read()
            assertTrue(s.inBattle && s.isWildBattle)
            assertEquals(area, s.encounterArea)
            assertEquals(method, Gen3Nuzlocke.snapshot(s)!!.method)
        }
    }
}
