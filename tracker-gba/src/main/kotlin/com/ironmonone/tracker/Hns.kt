package com.ironmonone.tracker

/**
 * One field of a struct as the Heart & Soul build compiled it (HnsLayout, generated from tools/hns/layout.py's export):
 * the byte offset, the size of the unit it lives in, and for a bitfield its shift and width inside that unit
 * (layout.py's `bits`). [count] > 1 is an array of [size] / [count]-byte elements.
 */
internal class HnsField(val offset: Int, val size: Int, val shift: Int, val width: Int, val signed: Boolean, val count: Int) {
    /** Element [index] of the field in [b], whose struct starts at [base]. */
    fun at(b: ByteArray, base: Int = 0, index: Int = 0): Int {
        val unit = if (count > 1) size / count else size
        val o = base + offset + index * unit
        if (o < 0 || o + unit > b.size) return 0
        var v = 0L
        for (i in 0 until minOf(unit, 8)) v = v or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        val bits = if (count > 1) unit * 8 else width
        val sh = if (count > 1) 0 else shift
        if (bits < 64) v = (v ushr sh) and ((1L shl bits) - 1)
        if (signed && bits < 64 && (v shr (bits - 1)) and 1L == 1L) v -= 1L shl bits
        return v.toInt()
    }

    /** The same as an unsigned 32-bit value (pointers, personality). */
    fun u32(b: ByteArray, base: Int = 0): Long = at(b, base).toLong() and 0xFFFFFFFFL
}

/**
 * The Heart & Soul party Pokemon (pokeemerald-expansion's BoxPokemon): Gen 3's 100/80-byte shape, key and substructure
 * order, but every substructure bit-packed (species 11 bits, held item 10, experience 21, moves 11, PP 7), the
 * nickname's last two letters in substructure 0, the ability as a 2-bit slot (2 is the hidden ability), a hidden
 * nature modifier and a shiny override bit. Every offset is the build's own (HnsLayout).
 */
internal object HnsMon {
    private val B = HnsLayout.BoxPokemon
    private val P = HnsLayout.Pokemon
    private val S0 = HnsLayout.PokemonSubstruct0
    private val S1 = HnsLayout.PokemonSubstruct1
    private val S2 = HnsLayout.PokemonSubstruct2
    private val S3 = HnsLayout.PokemonSubstruct3

    /** The 48 bytes of substructures, decrypted, and where each of the four sits (growth, attacks, EVs, misc). */
    fun plain(mon: ByteArray): Pair<ByteArray, IntArray> {
        val pid = mon.u32(B.personality.offset)
        val key = pid xor mon.u32(B.otId.offset)
        val sec = B.secure.offset
        val plain = ByteArray(48)
        for (w in 0 until 12) plain.putU32(w * 4, mon.u32(sec + w * 4) xor key)
        val slot = PokemonDecoder.SLOT_OF[(pid % 24).toInt()]
        return plain to IntArray(4) { slot[it] * 12 }
    }

    fun decode(mon: ByteArray): PokemonDecoder.Mon {
        val pid = mon.u32(B.personality.offset)
        val otId = mon.u32(B.otId.offset)
        val (plain, at) = plain(mon)
        val g = at[0]; val a = at[1]; val e = at[2]; val m = at[3]
        val nick = ByteArray(12) { 0xFF.toByte() }
        for (i in 0 until B.nickname.count) nick[i] = mon[B.nickname.offset + i]
        nick[10] = S0.nickname11.at(plain, g).toByte()
        nick[11] = S0.nickname12.at(plain, g).toByte()
        val natureByPid = (pid % 25).toInt()
        // MON_DATA_HIDDEN_NATURE: the personality's nature XOR the modifier, the nature the stats are worked out with.
        val hidden = natureByPid xor B.hiddenNatureModifier.at(mon)
        val shinyValue = ((otId ushr 16) xor (otId and 0xFFFF) xor (pid ushr 16) xor (pid and 0xFFFF))
        return PokemonDecoder.Mon(
            pid = pid,
            level = P.level.at(mon),
            nickname = Gen3Text.decode(nick),
            species = S0.species.at(plain, g),
            heldItem = S0.heldItem.at(plain, g),
            friendship = S0.friendship.at(plain, g),
            moves = listOf(S1.move1, S1.move2, S1.move3, S1.move4).map { it.at(plain, a) },
            pp = listOf(S1.pp1, S1.pp2, S1.pp3, S1.pp4).map { it.at(plain, a) },
            ppUps = List(4) { (S0.ppBonuses.at(plain, g) shr (it * 2)) and 3 },
            ivs = listOf(S3.hpIV, S3.attackIV, S3.defenseIV, S3.speedIV, S3.spAttackIV, S3.spDefenseIV).map { it.at(plain, m) },
            evs = listOf(S2.hpEV, S2.attackEV, S2.defenseEV, S2.speedEV, S2.spAttackEV, S2.spDefenseEV).map { it.at(plain, e) },
            abilitySlot = S3.abilityNum.at(plain, m),
            nature = if (hidden in 0..24) hidden else natureByPid,
            shiny = B.shinyModifier.at(mon) == 1 || shinyValue < HnsLayout.SHINY_ODDS,
            status = P.status.u32(mon),
            curHp = P.hp.at(mon), maxHp = P.maxHP.at(mon),
            atk = P.attack.at(mon), def = P.defense.at(mon), spe = P.speed.at(mon),
            spAtk = P.spAttack.at(mon), spDef = P.spDefense.at(mon),
            exp = S0.experience.at(plain, g).toLong(),
            isEgg = S3.isEgg.at(plain, m) == 1,
        )
    }

    /** Gen 3's type id for the expansion's: TYPE_NONE is 0 there, so each type is one higher; Stellar and None read as the unknown slot (9). */
    fun gen3Type(t: Int): Int = if (t in 1..19) t - 1 else 9
}

/**
 * The three Heart & Soul battle weathers the five games do not have, which change what a move does (Blake, 2026-10-05:
 * "weather is only relevant if it effects the battle mechanics"), by the names the weather line shows. From the
 * expansion's src/battle_util.c: strong winds (Delta Stream) make a super-effective hit on a Flying type neutral, per
 * Flying type; heavy rain (Primordial Sea) makes a damaging Fire move fail, extreme sun (Desolate Land) a damaging Water
 * move. Fog is never a battle weather in this build (B_OVERWORLD_FOG is GEN_3), so it stays unnamed.
 */
object HnsWeather {
    const val STRONG_WINDS = "STRONG WINDS"
    const val HEAVY_RAIN = "HEAVY RAIN"
    const val EXTREME_SUN = "EXTREME SUN"

    private const val FLYING = 2
    private const val FIRE = 10
    private const val WATER = 11

    /**
     * [raw] gBattleWeather as the five games' bits, which Calc Atk and Weather Ball read: rain 1, sandstorm 8, sun 0x20,
     * hail and snow 0x80. Heavy rain is rain and extreme sun is sun here; fog and strong winds have no Gen 3 bit.
     */
    fun word(raw: Int): Int = when {
        raw and HnsLayout.B_WEATHER_RAIN != 0 -> 0x01
        raw and HnsLayout.B_WEATHER_SANDSTORM != 0 -> 0x08
        raw and HnsLayout.B_WEATHER_SUN != 0 -> 0x20
        raw and (HnsLayout.B_WEATHER_HAIL or HnsLayout.B_WEATHER_SNOW) != 0 -> 0x80
        else -> 0
    }

    /** The weather line for [raw]: one of the three by name, else the Gen 3 name of [word] ("RAIN", "SUN"...), else null. */
    fun line(raw: Int): String? = name(raw) ?: gen3WeatherName(word(raw))

    /** The name of [raw] gBattleWeather when it is one of the three; null for any other weather (read as before). */
    fun name(raw: Int): String? = when {
        raw and HnsLayout.B_WEATHER_STRONG_WINDS != 0 -> STRONG_WINDS
        raw and HnsLayout.B_WEATHER_RAIN_PRIMAL != 0 -> HEAVY_RAIN
        raw and HnsLayout.B_WEATHER_SUN_PRIMAL != 0 -> EXTREME_SUN
        else -> null
    }

    /**
     * [base], the chart's multiplier of a [type] move of [category] on [targetTypes], under [weather]: unchanged for
     * any weather but the three, so no other game's numbers move. [chart] is the single-type multiplier the caller used.
     */
    fun effectiveness(weather: String?, type: Int, category: String?, targetTypes: List<Int>, base: Double, chart: (Int) -> Double): Double {
        val damaging = category != "STA"
        return when (weather) {
            HEAVY_RAIN -> if (damaging && type == FIRE) 0.0 else base
            EXTREME_SUN -> if (damaging && type == WATER) 0.0 else base
            // battle_util.c: "B_WEATHER_STRONG_WINDS weakens Super Effective moves against Flying-type Pokemon", type by type.
            STRONG_WINDS -> if (FLYING in targetTypes.distinct() && chart(FLYING) >= 2.0) base / chart(FLYING) else base
            else -> base
        }
    }
}

/**
 * The power column of the Heart & Soul moves whose power the game works out in battle, where its move table holds the
 * expansion's placeholder 1 (the Nat. Dex sweep, 2026-10-06: Grass Knot and fifteen more printed "1"). The words are
 * the DS tracker's for the same moves (tracker-nds gen5/moves.tsv: WT, >WT, <SP, >SP, BRY, ITM, <PP, VAR, HP), the PC
 * tracker's for the rest: a fixed amount of damage prints as no power ("0", as Super Fang and Counter do there), a
 * friendship one as Return's ">FR". By the move's name, so a build with other ids never takes a label it should not.
 */
object HnsMovePower {
    private val LABELS = mapOf(
        "GRASSKNOT" to "WT", "HEAVYSLAM" to ">WT", "HEATCRASH" to ">WT", "GYROBALL" to "<SP", "ELECTROBALL" to ">SP",
        "NATURALGIFT" to "BRY", "FLING" to "ITM", "TRUMPCARD" to "<PP", "BEATUP" to "VAR", "FINALGAMBIT" to "HP",
        "METALBURST" to "0", "COMEUPPANCE" to "0", "NATURESMADNESS" to "0", "RUINATION" to "0",
        "PIKAPAPOW" to ">FR", "VEEVEEVOLLEY" to ">FR",
    )

    /** The label for the move named [name] when its table power is the placeholder 1, else null (the number stands). */
    fun label(name: String, romPower: Int?): String? = if (romPower == 1) LABELS[BattleMoveTypes.norm(name)] else null
}

/**
 * Heart & Soul's species numbering for the app's screens (2026-10-05): its ids run 1 to [TOTAL] in the expansion's own
 * order (National Dex order to 1025, then the forms), which is neither Gen 3's nor the Nat. Dex build's, so a screen that
 * counts or pictures species asks here.
 */
object HnsSpecies {
    /** The last species id: NUM_SPECIES less one. */
    const val TOTAL = HnsLayout.NUM_SPECIES - 1
    /** The egg's id (SPECIES_EGG), one past the last species. */
    const val EGG = HnsLayout.SPECIES_EGG
    /** The Nat. Dex sprite pack's egg (GbaTracker's Nat. Dex builds draw theirs from it). */
    const val PACK_EGG = 1284

    /** The Nat. Dex build's id of the same Pokemon (the sprite pack, Walking Pals, the listed BST), or null. */
    fun natDexId(species: Int): Int? = if (species == EGG) PACK_EGG else HnsSprites.packId(species)
}

/**
 * The bundled sprite pack's picture for a Heart & Soul species (hns/packsprites.tsv, tools/hns/gen_tracker.py): the
 * pack is numbered the Nat. Dex build's way, so a Heart & Soul species is looked up by its National Dex number and form.
 */
object HnsSprites {
    private val ids: Map<Int, Int> by lazy {
        val out = HashMap<Int, Int>()
        HnsSprites::class.java.getResourceAsStream("/hns/packsprites.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l ->
                if (l.startsWith("#")) return@forEach
                val t = l.indexOf('\t')
                if (t > 0) l.substring(0, t).toIntOrNull()?.let { k -> l.substring(t + 1).trim().toIntOrNull()?.let { out[k] = it } }
            }
        }
        out
    }

    /** The pack's id for [species], or null where the pack has no picture of it. */
    fun packId(species: Int): Int? = ids[species]
}

/**
 * The ROM read in 16 KB pages, each page once (the last [maxPages] kept): Heart & Soul's tables are read field by field,
 * thousands of small reads where a log viewer names every species and move, and on the device each read of the running
 * core waits its turn with the frame for the core's lock, so the Heart & Soul log took 30 to 40 seconds to open
 * (2026-10-05). A cartridge cannot change while it is loaded; anything outside the ROM is read straight through.
 */
internal class RomPages(private val memory: MemoryReader, private val maxPages: Int = 256) : MemoryReader {
    private val pages = object : LinkedHashMap<Int, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?) = size > maxPages
    }

    /** How many reads reached [memory]: for the tests. */
    @Volatile var reads = 0L
        private set

    private fun through(address: Long, length: Int): ByteArray { reads++; return memory.read(address, length) }

    override fun read(address: Long, length: Int): ByteArray {
        if (length <= 0 || address !in ROM_START..ROM_END || address + length - 1 > ROM_END) return through(address, length)
        val out = ByteArray(length)
        var done = 0
        synchronized(pages) {
            while (done < length) {
                val rel = address + done - ROM_START
                val page = (rel shr PAGE_BITS).toInt()
                val off = (rel and (PAGE - 1).toLong()).toInt()
                val p = pages[page] ?: through(ROM_START + (page.toLong() shl PAGE_BITS), PAGE).also { if (it.size == PAGE) pages[page] = it }
                if (p.size <= off) return if (done == 0) through(address, length) else out.copyOf(done)
                val n = minOf(length - done, p.size - off)
                System.arraycopy(p, off, out, done, n)
                done += n
                if (p.size < PAGE && done < length) return out.copyOf(done)
            }
        }
        return out
    }

    private companion object {
        const val ROM_START = 0x08000000L
        const val ROM_END = 0x09FFFFFFL
        const val PAGE_BITS = 14
        const val PAGE = 1 shl PAGE_BITS
    }
}

/**
 * Everything the tracker reads out of the Heart & Soul ROM, through its own tables (HnsLayout): names, base stats,
 * moves, learnsets, evolutions, the type chart, items and what they heal, the map sections' names, the wild encounter
 * tables and the trainers. Cached per id; a ROM that cannot change while loaded.
 */
internal class HnsData(private val memory: MemoryReader) {
    private val SI = HnsLayout.SpeciesInfo
    private val MI = HnsLayout.MoveInfo
    private val AI = HnsLayout.AbilityInfo
    private val II = HnsLayout.ItemInfo

    private fun rec(table: Long, size: Int, id: Int): ByteArray = memory.read(table + id.toLong() * size, size)
    private fun u32(addr: Long): Long { val b = memory.read(addr, 4); return if (b.size == 4) b.u32(0) else 0L }
    private fun rom(p: Long) = p in 0x08000000L..0x09FFFFFFL

    /** Text at a ROM pointer, up to [max] bytes, the 0xFE line breaks as spaces. */
    fun text(ptr: Long, max: Int): String {
        if (!rom(ptr)) return ""
        val b = memory.read(ptr, max)
        val end = b.indexOfFirst { it == 0xFF.toByte() }.let { if (it < 0) b.size else it }
        val cut = b.copyOfRange(0, end)
        for (i in cut.indices) if (cut[i] == 0xFE.toByte()) cut[i] = 0x00
        return Gen3Text.decode(cut + 0xFF.toByte()).replace(Regex("\\s+"), " ").trim()
    }

    fun species(id: Int): ByteArray? = if (id in 0..HnsLayout.NUM_SPECIES) rec(HnsLayout.gSpeciesInfo, SI.SIZE, id).takeIf { it.size == SI.SIZE } else null
    fun move(id: Int): ByteArray? = if (id in 0 until HnsLayout.MOVES_COUNT_ALL) rec(HnsLayout.gMovesInfo, MI.SIZE, id).takeIf { it.size == MI.SIZE } else null
    fun item(id: Int): ByteArray? = if (id in 0 until HnsLayout.ITEMS_COUNT) rec(HnsLayout.gItemsInfo, II.SIZE, id).takeIf { it.size == II.SIZE } else null

    fun speciesName(id: Int): String? = species(id)?.let { b ->
        Gen3Text.decode(b.copyOfRange(SI.speciesName.offset, SI.speciesName.offset + SI.speciesName.size))
    }?.takeIf { it.isNotBlank() }

    fun moveName(id: Int): String? = move(id)?.let { text(MI.name.u32(it), 17) }?.takeIf { it.isNotBlank() }

    fun abilityName(id: Int): String? {
        if (id !in 0 until HnsLayout.ABILITIES_COUNT) return null
        val b = rec(HnsLayout.gAbilitiesInfo, AI.SIZE, id)
        if (b.size < AI.SIZE) return null
        return Gen3Text.decode(b.copyOfRange(AI.name.offset, AI.name.offset + AI.name.size)).takeIf { it.isNotBlank() }
    }

    fun itemName(id: Int): String? = item(id)?.let { text(II.name.u32(it), 21) }?.takeIf { it.isNotBlank() }

    fun moveDescription(id: Int): String? = move(id)?.let { text(MI.description.u32(it), 200) }?.takeIf { it.isNotBlank() }

    fun abilityDescription(id: Int): String? {
        if (id !in 1 until HnsLayout.ABILITIES_COUNT) return null
        val b = rec(HnsLayout.gAbilitiesInfo, AI.SIZE, id)
        return if (b.size < AI.SIZE) null else text(AI.description.u32(b), 200).takeIf { it.isNotBlank() }
    }

    /** An ability's id by its name, as [abilityName] gives it. */
    fun abilityId(name: String): Int? = (1 until HnsLayout.ABILITIES_COUNT).firstOrNull { abilityName(it)?.equals(name.trim(), true) == true }

    fun baseStats(id: Int): BaseStats? {
        val b = species(id) ?: return null
        val ab = List(3) { SI.abilities.at(b, 0, it) }
        return BaseStats(
            hp = SI.baseHP.at(b), atk = SI.baseAttack.at(b), def = SI.baseDefense.at(b), spe = SI.baseSpeed.at(b),
            spAtk = SI.baseSpAttack.at(b), spDef = SI.baseSpDefense.at(b),
            type1 = HnsMon.gen3Type(SI.types.at(b, 0, 0)), type2 = HnsMon.gen3Type(SI.types.at(b, 0, 1)),
            ability1 = ab[0], ability2 = ab[1], ability3 = ab[2],
            growthRate = SI.growthRate.at(b), catchRate = SI.catchRate.at(b),
            baseFriendship = SI.friendship.at(b), genderRatio = SI.genderRatio.at(b),
        )
    }

    fun natDexNum(id: Int): Int = species(id)?.let { SI.natDexNum.at(it) } ?: 0

    /** Weight in kg as the reference writes it ("6.9"), from SpeciesInfo.weight in hectograms. */
    fun weight(id: Int): String? = species(id)?.let { SI.weight.at(it) }?.takeIf { it > 0 }?.let { "%d.%d".format(it / 10, it % 10) }

    /** [power, type (Gen 3's id), accuracy, PP, priority, flags (bit 0 contact), category 0 physical 1 special 2 status]. */
    fun moveData(id: Int): IntArray? {
        if (id <= 0) return null
        val b = move(id) ?: return null
        return intArrayOf(MI.power.at(b), HnsMon.gen3Type(MI.type.at(b)), MI.accuracy.at(b), MI.pp.at(b),
            MI.priority.at(b), MI.makesContact.at(b), MI.category.at(b))
    }

    /** Level-up learnset as (level, move), from SpeciesInfo.levelUpLearnset, ended by LEVEL_UP_MOVE_END. */
    fun learnset(id: Int): List<Pair<Int, Int>> {
        val b = species(id) ?: return emptyList()
        val p = SI.levelUpLearnset.u32(b)
        if (!rom(p)) return emptyList()
        val lm = HnsLayout.LevelUpMove
        val out = ArrayList<Pair<Int, Int>>(24)
        val raw = memory.read(p, lm.SIZE * 120)
        for (i in 0 until raw.size / lm.SIZE) {
            val move = lm.move.at(raw, i * lm.SIZE)
            if (move == HnsLayout.LEVEL_UP_MOVE_END || move == 0) break
            val level = lm.level.at(raw, i * lm.SIZE)
            if (level > 100 || move >= HnsLayout.MOVES_COUNT_ALL) break
            out += level to move
        }
        return out
    }

    /** SpeciesInfo.evolutions as (method, param, target, the conditions' (condition, arg1)). */
    fun evolutions(id: Int): List<Evo> {
        val b = species(id) ?: return emptyList()
        val p = SI.evolutions.u32(b)
        if (!rom(p)) return emptyList()
        val ev = HnsLayout.Evolution
        val prm = HnsLayout.EvolutionParam
        val out = ArrayList<Evo>()
        for (i in 0 until 128) {   // to EVOLUTIONS_END: Milcery has 72 (every sweet and cream), not 16
            val e = memory.read(p + i * ev.SIZE.toLong(), ev.SIZE)
            if (e.size < ev.SIZE) break
            val method = ev.method.at(e)
            if (method == HnsLayout.EVOLUTIONS_END || method == 0) break
            val conds = ArrayList<Pair<Int, Int>>()
            val cp = ev.params.u32(e)
            if (rom(cp)) for (k in 0 until 8) {
                val c = memory.read(cp + k * prm.SIZE.toLong(), prm.SIZE)
                if (c.size < prm.SIZE) break
                val cond = prm.condition.at(c)
                if (cond == HnsLayout.CONDITIONS_END || cond == 0xFFFF) break
                conds += cond to prm.arg1.at(c)
            }
            out += Evo(method, ev.param.at(e), ev.targetSpecies.at(e), conds)
        }
        return out
    }

    data class Evo(val method: Int, val param: Int, val target: Int, val conditions: List<Pair<Int, Int>>)

    /**
     * The evolution as the tracker's EvoText reads it: the level ("16"), FRIEND, a stone's or an item's method key, or
     * null when it does not evolve. Where a species has more than one way, the first that EvoText can name; three
     * stones or more, as Eevee's, is EEVEE_STONES_NATDEX.
     */
    fun evolutionText(id: Int): String? {
        val evos = evolutions(id).filter { it.method != 0 }
        if (evos.isEmpty()) return null
        val stones = evos.filter { it.method == HnsLayout.EVO_ITEM }
        if (stones.size >= 3) return "EEVEE_STONES_NATDEX"
        for (e in evos) {
            when {
                e.conditions.any { it.first == HnsLayout.IF_MIN_FRIENDSHIP } -> return "FRIEND"
                e.method == HnsLayout.EVO_LEVEL && e.param > 0 -> return e.param.toString()
                e.method == HnsLayout.EVO_ITEM -> HnsLayout.EVO_ITEM_METHOD[e.param]?.let { return it }
                e.method == HnsLayout.EVO_TRADE -> e.conditions.firstOrNull { it.first == HnsLayout.IF_HOLD_ITEM }?.let { c ->
                    HnsLayout.EVO_ITEM_METHOD[c.second]?.let { return it }
                }
            }
        }
        // A way none of those names (2026-10-06, the Nat. Dex sweep: 32 species of the build, about 40 a Kaizo seed, read
        // as not evolving at all): a trade, an item the table has no word for (Protector, Galarica Cuff, Oval Stone), a
        // level-up with a condition and no level (knowing a move, Remoraid in the party, 1,000 steps), or another way.
        val e = evos.first()
        return when {
            evos.any { it.method == HnsLayout.EVO_ITEM } || e.conditions.any { it.first == HnsLayout.IF_HOLD_ITEM } -> EvoText.HNS_ITEM
            e.method == HnsLayout.EVO_TRADE -> EvoText.HNS_TRADE
            e.method == HnsLayout.EVO_LEVEL -> EvoText.HNS_LEVEL_UP
            else -> EvoText.HNS_OTHER
        }
    }

    /** The friendship an evolution asks for (IF_MIN_FRIENDSHIP's argument): 160 on this build's generation, 220 before. */
    val friendshipThreshold: Int by lazy {
        (1 until HnsLayout.NUM_SPECIES).asSequence().flatMap { evolutions(it).asSequence() }
            .flatMap { it.conditions.asSequence() }.firstOrNull { it.first == HnsLayout.IF_MIN_FRIENDSHIP }?.second
            ?.takeIf { it in 1..255 } ?: EvoText.DEFAULT_REQUIRED
    }

    /** gTypeEffectivenessTable, uq4.12: the multiplier of an attacking type on a defending one, both Gen 3 ids. */
    fun typeEffect(attack: Int, defend: Int): Double {
        fun hns(t: Int) = if (t == 9) HnsLayout.TYPE_MYSTERY else if (t in 0..18) t + 1 else -1
        val a = hns(attack); val d = hns(defend)
        if (a < 0 || d < 0) return 1.0
        return u32(HnsLayout.gTypeEffectivenessTable + 4L * (a * HnsLayout.NUMBER_OF_MON_TYPES + d)) / 4096.0
    }

    // ---- Items: what each one does, from its own effect bytes (src/data/pokemon/item_effects.h) ----

    private fun effect(id: Int): ByteArray? {
        val b = item(id) ?: return null
        val p = II.effect.u32(b)
        return if (rom(p)) memory.read(p, 7).takeIf { it.size == 7 } else null
    }

    private fun pocket(id: Int): Int = item(id)?.let { II.pocket.at(it) } ?: -1

    /** Item id to (amount, isPercentage) for every item that heals HP and is not a revive, as HEAL_ITEMS has the vanilla ones. */
    val healItems: Map<Int, Pair<Double, Boolean>> by lazy {
        val out = HashMap<Int, Pair<Double, Boolean>>()
        for (id in 1 until HnsLayout.ITEMS_COUNT) {
            val e = effect(id) ?: continue
            val f4 = e.u8(4)
            if (f4 and HnsLayout.ITEM4_HEAL_HP == 0 || f4 and HnsLayout.ITEM4_REVIVE != 0) continue
            when (val amt = e.u8(6)) {
                HnsLayout.ITEM6_HEAL_HP_FULL -> out[id] = 100.0 to true
                HnsLayout.ITEM6_HEAL_HP_HALF -> out[id] = 50.0 to true
                HnsLayout.ITEM6_HEAL_HP_QUARTER -> out[id] = 25.0 to true
                HnsLayout.ITEM6_HEAL_HP_LVL_UP -> {}
                else -> if (amt > 0) out[id] = amt.toDouble() to false
            }
        }
        out
    }

    /** Item id to the status it cures ("Poison", "Burn", ..., "All"), as STATUS_ITEMS has the vanilla ones. */
    val statusItems: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        for (id in 1 until HnsLayout.ITEMS_COUNT) {
            val e = effect(id) ?: continue
            val s = e.u8(3) and HnsLayout.ITEM3_STATUS_ALL
            if (s == 0) continue
            out[id] = when (s) {
                HnsLayout.ITEM3_STATUS_ALL -> "All"
                HnsLayout.ITEM3_POISON -> "Poison"
                HnsLayout.ITEM3_BURN -> "Burn"
                HnsLayout.ITEM3_FREEZE -> "Freeze"
                HnsLayout.ITEM3_SLEEP -> "Sleep"
                HnsLayout.ITEM3_PARALYSIS -> "Paralyze"
                HnsLayout.ITEM3_CONFUSION -> "Confusion"
                else -> "All"
            }
        }
        out
    }

    /** Items that restore PP. */
    val ppItems: Set<Int> by lazy {
        (1 until HnsLayout.ITEMS_COUNT).filterTo(HashSet()) { id ->
            effect(id)?.let { (it.u8(4) and (HnsLayout.ITEM4_HEAL_PP or HnsLayout.ITEM4_HEAL_PP_ONE)) != 0 } == true
        }
    }

    /**
     * Every item an evolution of the build uses or asks to be held (EVO_ITEM's item, IF_HOLD_ITEM's): Heals in Bag's
     * Evo tab, which knew only the stones and trade items of EvoText's table (Protector, Galarica Cuff and 27 more
     * read as Other, the Nat. Dex sweep 2026-10-06).
     */
    val evoItems: Set<Int> by lazy {
        val out = HashSet<Int>()
        for (s in 1 until HnsLayout.NUM_SPECIES) for (e in evolutions(s)) {
            if (e.method == HnsLayout.EVO_ITEM && e.param > 0) out += e.param
            e.conditions.filter { it.first == HnsLayout.IF_HOLD_ITEM && it.second > 0 }.forEach { out += it.second }
        }
        out
    }

    /** The X items, Guard Spec. and Dire Hit (MiscData.BattleItems). */
    val battleItems: Set<Int> = HnsLayout.X_ITEMS + setOf(HnsLayout.ITEM_GUARD_SPEC, HnsLayout.ITEM_DIRE_HIT)

    /** Every Poke Ball (the Poke Balls pocket's items). */
    val balls: Set<Int> by lazy { (1 until HnsLayout.ITEMS_COUNT).filterTo(HashSet()) { pocket(it) == POCKET_POKE_BALLS } }

    // ---- Maps: the section names and the wild encounters ----

    /** gRegionMapEntries[section].name. */
    fun mapSectionName(section: Int): String? {
        if (section < 0 || section >= HnsLayout.MAPSEC_NONE) return null
        val r = HnsLayout.RegionMapLocation
        val b = memory.read(HnsLayout.gRegionMapEntries + section.toLong() * r.SIZE, r.SIZE)
        if (b.size < r.SIZE) return null
        return text(r.name.u32(b), 24).takeIf { it.isNotBlank() }
    }

    /** gMapGroups[group][num]'s header bytes, or null. */
    private fun mapHeader(mapId: Int): ByteArray? {
        val group = mapId shr 8; val num = mapId and 0xFF
        if (group !in 0 until HnsLayout.MAP_GROUPS_COUNT) return null
        val g = u32(HnsLayout.gMapGroups + group * 4L)
        if (!rom(g)) return null
        val h = u32(g + num * 4L)
        if (!rom(h)) return null
        return memory.read(h, HnsLayout.MapHeader.SIZE).takeIf { it.size == HnsLayout.MapHeader.SIZE }
    }

    /** The region map section a map (mapGroup << 8 | mapNum) belongs to, or -1. */
    fun mapSection(mapId: Int): Int = mapHeader(mapId)?.let { HnsLayout.MapHeader.regionMapSectionId.at(it) } ?: -1

    /** The map's name: its region map section's, as the game's own map popup and Pokedex area show it. */
    fun mapName(mapId: Int): String? = mapSectionName(mapSection(mapId))

    /**
     * gWildMonHeaders, by map: each area's species with the chance of its slots (summed per species) and its levels.
     * Areas as RouteData names them: Walking, Surfing, RockSmash, Old Rod, Good Rod, Super Rod.
     *
     * What the list holds follows what the game will hand out (Blake, 2026-10-06, rc37):
     * - A Kaizo IronMON run (the ROM's challenge preset is KAIZO, [kaizoCycle]): the game cycles through every species the
     *   area has at ANY time of day (src/wild_encounter.c KaizoCore_CycleSlot), and the list is exactly that set in that
     *   order: the area's tables of that kind in time-of-day order, each table once, its slots in order, each species
     *   once. Each species shows its best chance and its levels over all its tables.
     * - Otherwise (a Nuzlocke run, the plain build): the game rolls only the current time's table (gTimeOfDay; a time with
     *   no table falls back to OW_TIME_OF_DAY_FALLBACK's, as GetTimeOfDayForEncounters does), so the list is that
     *   table's, by chance.
     */
    val wild: Map<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>>
        get() = if (kaizoCycle) wildCycle else wildAt(timeOfDay())

    /** The ROM's challenge preset mode is KAIZO: a Kaizo IronMON run, whose wild encounters cycle (HnsEngine.writePreset). */
    val kaizoCycle: Boolean by lazy {
        val b = memory.read(HnsLayout.gHnsChallengePreset + HnsLayout.HNS_PRESET_MODE, 1)
        b.size == 1 && (b[0].toInt() and 0xFF) == HnsLayout.HNS_PRESET_MODE_KAIZO
    }

    /** gTimeOfDay (the overworld updates it every minute), or the fallback time when it reads out of range. */
    fun timeOfDay(): Int {
        val b = memory.read(HnsLayout.gTimeOfDay, 1)
        val t = if (b.size == 1) b[0].toInt() and 0xFF else -1
        return if (t in 0 until HnsLayout.TIMES_OF_DAY_COUNT) t else HnsLayout.OW_TIME_OF_DAY_FALLBACK
    }

    /** One wild table of an area kind: its slots' species (slot order, 0 for an empty slot), levels and chances. */
    private class WildTable(val ptr: Long, val species: IntArray, val minLv: IntArray, val maxLv: IntArray, val rates: IntArray)

    /** A wild header's map id and, per area name, its table at each time of day (null = none). */
    private class WildHeader(val mapId: Int, val tables: Map<String, Array<WildTable?>>)

    private val wildHeaders: List<WildHeader> by lazy {
        val out = ArrayList<WildHeader>()
        val H = HnsLayout.WildPokemonHeader
        val T = HnsLayout.WildEncounterTypes
        val I = HnsLayout.WildPokemonInfo
        val W = HnsLayout.WildPokemon
        fun table(info: Long, slots: IntRange, rates: IntArray): WildTable? {
            if (!rom(info)) return null
            val ib = memory.read(info, I.SIZE)
            if (ib.size < I.SIZE) return null
            val mons = I.wildPokemon.u32(ib)
            if (!rom(mons)) return null
            val n = slots.count()
            val sp = IntArray(n); val lo = IntArray(n); val hi = IntArray(n)
            for ((k, s) in slots.withIndex()) {
                val w = memory.read(mons + s.toLong() * W.SIZE, W.SIZE)
                if (w.size < W.SIZE) continue
                sp[k] = W.species.at(w).coerceAtLeast(0); lo[k] = W.minLevel.at(w); hi[k] = W.maxLevel.at(w)
            }
            return WildTable(info, sp, lo, hi, rates)
        }
        for (i in 0 until HnsLayout.gWildMonHeaders_COUNT) {
            val h = memory.read(HnsLayout.gWildMonHeaders + i.toLong() * H.SIZE, H.SIZE)
            if (h.size < H.SIZE) break
            val group = H.mapGroup.at(h); val num = H.mapNum.at(h)
            if (group == 0xFF) break
            val tables = LinkedHashMap<String, Array<WildTable?>>()
            for (name in WILD_AREAS) tables[name] = arrayOfNulls(HnsLayout.TIMES_OF_DAY_COUNT)
            for (t in 0 until HnsLayout.TIMES_OF_DAY_COUNT) {
                val base = H.encounterTypes.offset + t * T.SIZE
                tables.getValue("Walking")[t] = table(T.landMonsInfo.u32(h, base), 0 until HnsLayout.LAND_WILD_COUNT, LAND_RATES)
                tables.getValue("Surfing")[t] = table(T.waterMonsInfo.u32(h, base), 0 until HnsLayout.WATER_WILD_COUNT, WATER_RATES)
                tables.getValue("RockSmash")[t] = table(T.rockSmashMonsInfo.u32(h, base), 0 until HnsLayout.ROCK_WILD_COUNT, WATER_RATES)
                val fish = T.fishingMonsInfo.u32(h, base)
                tables.getValue("Old Rod")[t] = table(fish, 0..1, OLD_ROD_RATES)
                tables.getValue("Good Rod")[t] = table(fish, 2..4, GOOD_ROD_RATES)
                tables.getValue("Super Rod")[t] = table(fish, 5..9, SUPER_ROD_RATES)
            }
            out += WildHeader((group shl 8) or num, tables)
        }
        out
    }

    /** One table's species in slot order, each once, with its summed chance and its level range. */
    private fun perSpecies(t: WildTable): LinkedHashMap<Int, GbaTracker.RouteMon> {
        val per = LinkedHashMap<Int, GbaTracker.RouteMon>()
        for (k in t.species.indices) {
            val sp = t.species[k]
            if (sp <= 0) continue
            val old = per[sp]
            per[sp] = if (old == null) GbaTracker.RouteMon(sp, t.rates[k].toDouble(), t.minLv[k], t.maxLv[k])
            else GbaTracker.RouteMon(sp, old.rate + t.rates[k], minOf(old.minLv, t.minLv[k]), maxOf(old.maxLv, t.maxLv[k]))
        }
        return per
    }

    private fun byMap(build: (Array<WildTable?>) -> List<GbaTracker.RouteMon>): Map<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>> {
        val out = LinkedHashMap<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>>()
        for (h in wildHeaders) {
            val areas = LinkedHashMap<String, List<GbaTracker.RouteMon>>()
            for (a in ORDERED_ENCOUNTERS) h.tables[a]?.let(build)?.takeIf { it.isNotEmpty() }?.let { areas[a] = it }
            // The game takes a map's first header (GetCurrentMapWildMonHeaderId), so a later one for the same map is not it.
            if (areas.isNotEmpty() && h.mapId !in out) out[h.mapId] = areas
        }
        return out
    }

    /** The Kaizo cycle's list per map and area: KaizoCore_CycleSlot's set, in its order. */
    val wildCycle: Map<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>> by lazy {
        byMap { times ->
            val seen = ArrayList<WildTable>()
            for (t in times) if (t != null && seen.none { it.ptr == t.ptr }) seen += t
            val out = LinkedHashMap<Int, GbaTracker.RouteMon>()
            for (t in seen) for ((sp, m) in perSpecies(t)) {
                val o = out[sp]
                out[sp] = if (o == null) m else GbaTracker.RouteMon(sp, maxOf(o.rate, m.rate), minOf(o.minLv, m.minLv), maxOf(o.maxLv, m.maxLv))
            }
            out.values.toList()
        }
    }

    private val wildByTime = arrayOfNulls<Map<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>>>(HnsLayout.TIMES_OF_DAY_COUNT)

    /** What the game rolls from at time of day [time]: that time's table, or the fallback time's when it has none. */
    fun wildAt(time: Int): Map<Int, LinkedHashMap<String, List<GbaTracker.RouteMon>>> {
        val t = time.coerceIn(0, HnsLayout.TIMES_OF_DAY_COUNT - 1)
        wildByTime[t]?.let { return it }
        val m = byMap { times ->
            val table = times[t] ?: if (HnsLayout.OW_TIME_OF_DAY_DISABLE_FALLBACK == 0) times[HnsLayout.OW_TIME_OF_DAY_FALLBACK] else null
            table?.let { perSpecies(it).values.sortedByDescending { m -> m.rate } } ?: emptyList()
        }
        wildByTime[t] = m
        return m
    }

    /** The trainers each map's scripts battle (hns/maptrainers.tsv, from the build's map scripts). */
    val mapTrainers: Map<Int, List<Int>> by lazy {
        val out = HashMap<Int, List<Int>>()
        HnsData::class.java.getResourceAsStream("/hns/maptrainers.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l ->
                if (l.startsWith("#")) return@forEach
                val p = l.split('\t')
                val id = p.getOrNull(0)?.toIntOrNull() ?: return@forEach
                out[id] = p.getOrNull(1).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
            }
        }
        out
    }

    // ---- Trainers: gTrainers[DIFFICULTY_NORMAL][id], the only difficulty Heart & Soul fills ----

    private fun trainerRec(id: Int): ByteArray? {
        if (id !in 1 until HnsLayout.TRAINERS_COUNT) return null
        val t = HnsLayout.Trainer
        return memory.read(HnsLayout.gTrainers + (HnsLayout.DIFFICULTY_NORMAL.toLong() * HnsLayout.TRAINERS_COUNT + id) * t.SIZE, t.SIZE).takeIf { it.size == t.SIZE }
    }

    fun trainerClass(id: Int): Int = trainerRec(id)?.let { HnsLayout.Trainer.trainerClass.at(it) } ?: -1

    fun trainerClassName(cls: Int): String? {
        if (cls < 0) return null
        val c = HnsLayout.TrainerClass
        val b = memory.read(HnsLayout.gTrainerClasses + cls.toLong() * c.SIZE, c.SIZE)
        if (b.size < c.SIZE) return null
        return Gen3Text.decode(b.copyOfRange(c.name.offset, c.name.offset + c.name.size)).takeIf { it.isNotBlank() }
    }

    /** gTrainers' entry, as Trainer Info shows it: IVs averaged from the packed iv word, the AI flags' low word. */
    fun trainer(id: Int, defeated: Boolean): GbaTracker.TrainerInfo? {
        val t = HnsLayout.Trainer
        val m = HnsLayout.TrainerMon
        val b = trainerRec(id) ?: return null
        val size = t.partySize.at(b)
        val ptr = t.party.u32(b)
        val party = ArrayList<GbaTracker.TrainerMon>()
        if (size in 1..6 && rom(ptr)) {
            val pb = memory.read(ptr, m.SIZE * size)
            if (pb.size == m.SIZE * size) for (i in 0 until size) {
                val o = i * m.SIZE
                val iv = m.iv.u32(pb, o)
                val ivs = List(6) { ((iv shr (it * 5)) and 0x1F).toInt() }
                party += GbaTracker.TrainerMon(m.species.at(pb, o), m.lvl.at(pb, o), ivs.sum() / 6, m.heldItem.at(pb, o),
                    List(4) { m.moves.at(pb, o, it) })
            }
        }
        val name = Gen3Text.decode(b.copyOfRange(t.trainerName.offset, t.trainerName.offset + t.trainerName.size))
        return GbaTracker.TrainerInfo(
            id = id, className = trainerClassName(t.trainerClass.at(b)) ?: "", name = name, party = party,
            aiFlags = (t.aiFlags.at(b).toLong() and 0xFFFFFFFFL).toInt(), doubleBattle = t.battleType.at(b) != 0,
            defeated = defeated, items = List(4) { t.items.at(b, 0, it) },
        )
    }

    companion object {
        /** Gen 3's encounter slot chances: land, and the water / Rock Smash tables. */
        val LAND_RATES = intArrayOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1)
        val WATER_RATES = intArrayOf(60, 30, 5, 4, 1)
        val OLD_ROD_RATES = intArrayOf(70, 30)
        val GOOD_ROD_RATES = intArrayOf(60, 20, 20)
        val SUPER_ROD_RATES = intArrayOf(40, 40, 15, 4, 1)
        /** The area kinds KaizoCore_CycleSlot cycles (land, water, rock smash, each rod), by RouteData name. */
        val WILD_AREAS = listOf("Walking", "Surfing", "RockSmash", "Old Rod", "Good Rod", "Super Rod")
        const val POCKET_POKE_BALLS = 2
    }
}

/** The Heart & Soul map, its addresses and offsets all HnsLayout's (the build's own). */
internal object HnsMaps {
    const val NAME = "Heart & Soul 2.0.6 (KaizoCore)"
    /** The game, as UnreadableBuild names it. */
    const val GAME = "Heart & Soul"
    private val sb1 = HnsLayout.SaveBlock1
    private val bag = HnsLayout.Bag

    /** Whether [memory] holds this build: the header title, and the species and move tables where its layout puts them. */
    fun isHns(memory: MemoryReader): Boolean {
        val title = memory.read(0x080000A0L, 12)
        return title.size == 12 && String(title, Charsets.ISO_8859_1).startsWith("POKEMON HNS")
    }

    fun isKaizoBuild(memory: MemoryReader): Boolean {
        val d = HnsData(memory)
        // Names only: every one sits where this build's layout puts its table, and no randomizer setting renames a
        // species or a move. Pikachu's base stats were part of the check, and a run with random base stats (every Kaizo
        // mode) was refused by its own tracker (2026-10-05).
        return d.speciesName(1)?.uppercase() == "BULBASAUR" && d.speciesName(25)?.uppercase() == "PIKACHU" &&
            d.moveName(1)?.uppercase() == "POUND" && d.moveName(33)?.uppercase() == "TACKLE"
    }

    val KAIZO: GameMap = GameMap(
        name = NAME,
        hns = true,
        nameSet = "hns",
        expandedSpeciesIds = true,
        partyCount = HnsLayout.gPlayerPartyCount,
        party = HnsLayout.gPlayerParty,
        enemyParty = HnsLayout.gEnemyParty,
        battleTypeFlags = HnsLayout.gBattleTypeFlags,
        battleMons = HnsLayout.gBattleMons,
        battlersCount = HnsLayout.gBattlersCount,
        scriptCurrInstr = HnsLayout.gBattlescriptCurrInstr,
        scriptingBattler = HnsLayout.gBattleScripting + HnsLayout.BattleScripting.battler.offset,
        battlerAttacker = HnsLayout.gBattlerAttacker,
        battlerTarget = HnsLayout.gBattlerTarget,
        battleTextBuff1 = HnsLayout.gBattleTextBuff1,
        battlerAbility = HnsLayout.gBattlerAbility,
        abilityScriptTable = "hns",
        baseStats = HnsLayout.gSpeciesInfo,
        baseStatsStride = HnsLayout.SpeciesInfo.SIZE,
        speciesNames = 0L,
        moveNames = 0L,
        abilitiesAreU16 = true,
        expTables = HnsLayout.gExperienceTables,
        battleResultsTurnOffset = HnsLayout.BattleResults.battleTurnCounter.offset,
        battleResultsLastMovesOffset = HnsLayout.BattleResults.lastUsedMovePlayer.offset,
        // The personality sits 8 below this (BattlePokemon.personality); status2's bits are not read here (transformedOffset).
        status2Offset = HnsLayout.BattlePokemon.volatiles.offset,
        transformedOffset = HnsLayout.BattlePokemon.volatiles.offset + HnsLayout.Volatiles.transformed.offset,
        transformedMask = 1L shl HnsLayout.Volatiles.transformed.shift,
        battlerPartyIndexes = HnsLayout.gBattlerPartyIndexes,
        battleResults = HnsLayout.gBattleResults,
        currentTurnActionNumber = HnsLayout.gCurrentTurnActionNumber,
        actionsByTurnOrder = HnsLayout.gActionsByTurnOrder,
        hitMarker = HnsLayout.gHitMarker,
        moveScripts = MoveScripts(
            focusPunchSetUp = HnsLayout.BattleScript_FocusPunchSetUp.first, snatchedMove = HnsLayout.BattleScript_SnatchedMove.first,
            isConfused = 0, isConfused2 = 0, isConfusedNoMore = 0, wokeUp = 0, isInLove = 0, isInLove2 = 0,
            isFrozen = 0, isFrozen2 = 0, isFrozen3 = 0, unfroze = 0, unfroze2 = 0,
            delayedRanges = listOf(HnsLayout.BattleScript_MoveUsedIsConfused, HnsLayout.BattleScript_MoveUsedIsConfusedNoMore,
                HnsLayout.BattleScript_MoveUsedWokeUp, HnsLayout.BattleScript_MoveUsedIsInLove, HnsLayout.BattleScript_MoveUsedIsFrozen,
                HnsLayout.BattleScript_BattlerDefrosted),
        ),
        saveBlock1Ptr = HnsLayout.gSaveBlock1Ptr,
        saveBlock2Ptr = HnsLayout.gSaveBlock2Ptr,
        specialVarItemId = HnsLayout.gSpecialVar_ItemId,
        specialVarResultAny = HnsLayout.gSpecialVar_Result,
        battleMoves = HnsLayout.gMovesInfo,
        moveCategoryByte = true,
        weather = HnsLayout.gBattleWeather,
        monSummaryScreen = HnsLayout.sMonSummaryScreen,
        badgeOffset = sb1.flags.offset.toLong(),
        badgeFlagStart = HnsLayout.FLAG_BADGE01_GET,
        badgeCount = HnsLayout.NUM_BADGES,
        badgeSet = "GSC",
        repelStepsOffset = sb1.vars.offset + (HnsLayout.VAR_REPEL_STEP_COUNT - HnsLayout.VARS_START) * 2L,
        bagItemsOffset = sb1.bag.offset + bag.items.offset.toLong(), bagItemsSlots = bag.items.count,
        bagMedicineOffset = sb1.bag.offset + bag.medicine.offset.toLong(), bagMedicineSlots = bag.medicine.count,
        bagBerriesOffset = sb1.bag.offset + bag.berries.offset.toLong(), bagBerriesSlots = bag.berries.count,
        bagBallsOffset = sb1.bag.offset + bag.pokeBalls.offset.toLong(), bagBallsSlots = bag.pokeBalls.count,
        mapHeader = HnsLayout.gMapHeader,
        mapLocationOffset = sb1.location.offset.toLong(),
        monLayout = PokemonDecoder.Layout(
            size = HnsLayout.Pokemon.SIZE, enc = HnsLayout.BoxPokemon.secure.offset, status = HnsLayout.Pokemon.status.offset,
            level = HnsLayout.Pokemon.level.offset, curHp = HnsLayout.Pokemon.hp.offset, maxHp = HnsLayout.Pokemon.maxHP.offset,
            nickLen = HnsLayout.POKEMON_NAME_LENGTH, packed = true,
        ),
        battleMonSize = HnsLayout.BattlePokemon.SIZE,
        battleMonTypes = HnsLayout.BattlePokemon.types.offset,
        battleMonPp = HnsLayout.BattlePokemon.pp.offset,
        battleMonHp = HnsLayout.BattlePokemon.hp.offset,
        gameStatsOffset = sb1.gameStats.offset.toLong(),
        battleOutcome = HnsLayout.gBattleOutcome,
        battleEnvironment = HnsLayout.gBattleEnvironment,
        battleMainFunc = HnsLayout.gBattleMainFunc,
        // Data is ready once the intro has sent everyone out: TryDoEventsBeforeFirstTurn runs the switch-in abilities.
        introDrawPartySummary = HnsLayout.TryDoEventsBeforeFirstTurn,
        introOpponentSendsOut = HnsLayout.TryDoEventsBeforeFirstTurn,
        handleTurnAction = HnsLayout.HandleTurnActionSelectionState,
        returnToOverworld = HnsLayout.ReturnFromBattleToOverworld,
        battleCommunication = HnsLayout.gBattleCommunication,
        // STATE_WAIT_ACTION_CHOSEN in battle_main.c's enum (TURN_START_RECORD, BEFORE_ACTION_CHOSEN, WAIT_ACTION_CHOSEN), as Emerald.
        actionMenuState = 2,
        trainerOpponent = HnsLayout.gTrainerBattleParameter + HnsLayout.TrainerBattleParameter.opponentA.offset,
        gTrainers = HnsLayout.gTrainers,
        gTrainerClassNames = HnsLayout.gTrainerClasses,
        gameFlagsOffset = sb1.flags.offset.toLong(),
        trainerFlagStart = HnsLayout.TRAINER_FLAGS_START,
        finalTrainers = HnsLayout.FINAL_TRAINERS,
        labMapIds = setOf(HnsLayout.LAB_MAP_ID),
        safariModeFlag = HnsLayout.FLAG_SYS_SAFARI_MODE,
        leagueFlag = HnsLayout.FLAG_END_NUZLOCKE,
        safariMapIds = HnsLayout.SAFARI_MAP_IDS,
        encryptionKeyOffset = HnsLayout.SaveBlock2.encryptionKey.offset.toLong(),
        spriteCount = HnsLayout.NUM_SPECIES,
    )
}
