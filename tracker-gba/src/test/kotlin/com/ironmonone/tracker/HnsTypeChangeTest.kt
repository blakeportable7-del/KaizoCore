package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Heart & Soul's type changes in battle against the real comfort build (hns-kaizo.gba, found as HnsTrackerTest finds
 * it; without it every test returns): GetBattlerTypes' third type and Roost, and GetDynamicMoveType's -ate abilities,
 * Liquid Voice, held-item types, Terrain Pulse and Electrify (HnsMoveTypes), with the hidden information fence on an
 * opponent's ability. The harness is HnsTrackerTest's; ids come from the build's own layout export.
 */
class HnsTypeChangeTest {
    private val enums: String by lazy { File("../app/src/main/assets/hns/layout-kaizo.json").readText(Charsets.UTF_8) }
    private fun id(name: String): Int = Regex("\"$name\":\\s*(\\d+)").find(enums)!!.groupValues[1].toInt()



    private val rom: ByteArray? by lazy {
        // This layout's own build by its CRC where the folder keeps one beside a newer hns-kaizo.gba, else hns-kaizo.gba.
        val dir = HnsTrackerTest.romDir()
        (dir?.let { File(it, "hns-kaizo-%08X.gba".format(HnsLayout.BUILD_CRC)) }?.takeIf { it.isFile } ?: Dumps.file(dir, "hns-kaizo.gba"))?.readBytes()
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

    private val BM = HnsLayout.BattlePokemon
    private val V = HnsLayout.Volatiles

    /** Rewrites battler [i]'s struct in place with [f]. */
    private fun struct(mem: Mem, i: Int, f: (ByteArray) -> Unit) {
        val b = mem.read(HnsLayout.gBattleMons + i.toLong() * BM.SIZE, BM.SIZE)
        f(b); mem.write(HnsLayout.gBattleMons + i.toLong() * BM.SIZE, b)
    }

    /** Your Pikachu holding a Flame Plate, with Pound, Judgment, Hyper Voice and Terrain Pulse, and [ability] in battle. */
    private fun ownGame(ability: Int): Pair<Mem, GbaTracker>? {
        val (mem, t) = game() ?: return null
        val plate = id("ITEM_FLAME_PLATE")
        val moves = listOf(id("MOVE_POUND"), id("MOVE_JUDGMENT"), id("MOVE_HYPER_VOICE"), id("MOVE_TERRAIN_PULSE"))
        mem.write(HnsLayout.gPlayerParty, partyMon(pid = 0x2F15A7C3L, otId = 0x0BAD1DEAL, species = 25, level = 12, nickname = "PIKA",
            moves = moves, pp = listOf(35, 10, 10, 10), ivs = List(6) { 10 }, evs = List(6) { 0 }, abilityNum = 0, heldItem = plate))
        mem.w8(HnsLayout.gPlayerPartyCount, 1)
        battle(mem, enemySpecies = 39, trainer = null)
        struct(mem, 0) { b ->
            put(BM.ability, b, 0, ability); put(BM.item, b, 0, plate)
            put(BM.types, b, 0, HnsLayout.TYPE_ELECTRIC, 0); put(BM.types, b, 0, HnsLayout.TYPE_ELECTRIC, 1); put(BM.types, b, 0, HnsLayout.TYPE_MYSTERY, 2)
        }
        return mem to t
    }

    private fun GbaTracker.settle(): TrackerState { read(); return read() }
    private fun TrackerState.rows() = party[0].moveRows.map { it.type to it.power }

    @Test
    fun `your Pixilate turns your Normal moves Fairy at 1-2x, the plate sets Judgment, Terrain Pulse follows the terrain`() {
        val (mem, t) = ownGame(id("ABILITY_PIXILATE")) ?: return
        // Pound 40 -> Fairy 48; Judgment with a Flame Plate Fire (its own rule, before any ability); Hyper Voice 90 -> Fairy
        // 108; Terrain Pulse stays Normal 50 with no terrain (TrySetAteType leaves it alone).
        assertEquals(listOf(18 to 48, 10 to 100, 18 to 108, 0 to 50), t.settle().rows())
        // Electric Terrain on a grounded Pikachu: Terrain Pulse is Electric.
        mem.w32(HnsLayout.gFieldStatuses, HnsLayout.STATUS_FIELD_ELECTRIC_TERRAIN.toLong())
        assertEquals(13 to 50, t.read().rows()[3])
        // Electrify: every move Electric for the turn (SetTypeBeforeUsingMove).
        struct(mem, 0) { b -> put(V.electrified, b, BM.volatiles.offset, 1) }
        assertEquals(List(4) { 13 }, t.read().rows().map { it.first })
    }

    @Test
    fun `your Liquid Voice turns your sound moves Water`() {
        val (_, t) = ownGame(id("ABILITY_LIQUID_VOICE")) ?: return
        assertEquals(listOf(0 to 40, 10 to 100, 11 to 90, 0 to 50), t.settle().rows())
    }

    @Test
    fun `a third type and Roost are in the battle types the matchups read`() {
        val (mem, t) = ownGame(0) ?: return
        t.settle()
        // Forest's Curse made a Flying/Flying foe Flying/Flying/Grass, and it used Roost this turn.
        struct(mem, 1) { b ->
            put(BM.types, b, 0, HnsLayout.TYPE_FLYING, 0); put(BM.types, b, 0, HnsLayout.TYPE_FLYING, 1); put(BM.types, b, 0, HnsLayout.TYPE_GRASS, 2)
        }
        val cursed = t.read().enemy!!
        assertEquals(listOf(2, 2, 12), cursed.battleTypes)
        assertEquals(4.0, MoveRules.effectiveness(58, 15, "SPE", cursed.battleTypes!!, power = "90", natDex = true), "Ice: 2x Flying, 2x Grass")
        struct(mem, 1) { b -> put(V.roostActive, b, BM.volatiles.offset, 1) }
        val roosted = t.read().enemy!!
        assertEquals(listOf(0, 0, 12), roosted.battleTypes, "pure Flying turns Normal for the turn (Gen 5 on)")
        assertEquals(2.0, MoveRules.effectiveness(58, 15, "SPE", roosted.battleTypes!!, power = "90", natDex = true))
        assertEquals(listOf(2, 2), roosted.type1.let { listOf(it, roosted.type2) }, "the card keeps the types the struct holds")
    }

    /** The fence (hard rule): an opponent's Pixilate changes nothing shown until its pop-up has shown it. */
    @Test
    fun `an unrevealed opponent Pixilate does not change anything shown`() {
        val (mem, t) = game() ?: return
        mem.write(HnsLayout.gPlayerParty, pikachu); mem.w8(HnsLayout.gPlayerPartyCount, 1)
        val sylveon = id("SPECIES_SYLVEON")
        battle(mem, enemySpecies = sylveon, trainer = null)
        // Its hidden ability, Pixilate, in its party slot and its battle struct.
        mem.write(HnsLayout.gEnemyParty, partyMon(pid = 0x00C0FFEEL, otId = 0x11112222L, species = sylveon, level = 10, nickname = "FOE",
            moves = listOf(33, 45, 0, 0), pp = listOf(35, 40, 0, 0), ivs = List(6) { 10 }, evs = List(6) { 0 }, abilityNum = 2, hp = 20, maxHp = 25))
        struct(mem, 1) { b -> put(BM.ability, b, 0, id("ABILITY_PIXILATE")); put(BM.abilityNum, b, 0, 2) }
        t.read(); t.read()
        // It uses Tackle.
        mem.w8(HnsLayout.gCurrentTurnActionNumber, 1); mem.w8(HnsLayout.gActionsByTurnOrder + 1, HnsLayout.B_ACTION_USE_MOVE)
        mem.w8(HnsLayout.gBattlerAttacker, 1)
        mem.w16(HnsLayout.gBattleResults + HnsLayout.BattleResults.lastUsedMoveOpponent.offset, 33)
        t.read()
        val hidden = t.read().enemy!!
        assertEquals(listOf(0 to 40), hidden.moveRows.map { it.type to it.power }, "Tackle as the table has it: nothing reveals the ability")
        // The game shows it: the pop-up for battler 1.
        mem.w8(HnsLayout.gBattlerAbility, 1)
        mem.w32(HnsLayout.gBattlescriptCurrInstr, HnsLayout.BattleScript_AbilityPopUp.first + 5)
        val shown = t.read()
        assertEquals(listOf(sylveon to t.abilityName(id("ABILITY_PIXILATE"))), shown.abilitiesRevealed)
        assertEquals(listOf(18 to 48), shown.enemy!!.moveRows.map { it.type to it.power }, "shown: Fairy, 1.2x")
    }
}
