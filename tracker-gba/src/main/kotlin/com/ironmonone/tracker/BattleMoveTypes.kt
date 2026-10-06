package com.ironmonone.tracker

/**
 * The type a move takes in battle where the game changes it (2026-10-06, Blake: "what about moves that change the type
 * of the pokemon? how is that accounted for in the tracker and the moves?"), for the two Game Boy Advance builds that
 * change any: Heart & Soul (pokeemerald-expansion) and MaxDex 1.0. The five games and the Nat. Dex builds have none past
 * Weather Ball and Hidden Power, which MoveRules handles: Nat. Dex 1.2.1's newer moves and abilities are placeholders
 * that do nothing (NatDexExtension wiki, Changelog v1.2.0: "All future-gen moves and Abilities are defined in the
 * game's code ... Currently they are placeholders that do nothing"; Judgment, Natural Gift and the rest share one
 * placeholder row in its move table).
 *
 * What the tracker may not know stays out: an opponent's ability counts only once the game has shown it (GbaTracker
 * keeps what it has seen), and an opponent's held item never counts, as the tracker never learns it. Your own Pokemon's
 * ability and item are always known. A form shows on screen, so a move that follows the user's form follows it on
 * either side.
 */
internal object BattleMoveTypes {
    /** pokeemerald-expansion's 1.2x for -ate and Normalize (B_ATE_MULTIPLIER, GEN_LATEST here), uq4_12_multiply_by_int_half_down. */
    fun ateBoosted(power: Int): Int = (power * 4915 + 2047) shr 12

    /** A name as the comparisons here take it: letters only, upper case ("Multi-Attack" and "MULTI ATTACK" alike). */
    fun norm(s: String?): String = s.orEmpty().uppercase().filter { it in 'A'..'Z' }
}

/**
 * Heart & Soul: pokeemerald-expansion's GetDynamicMoveType (src/battle_main.c) for a battler in battle, then
 * SetTypeBeforeUsingMove's Ion Deluge and Electrify, with GetBattlerTypes' Roost (src/battle_util.c) and
 * IsBattlerGrounded for Terrain Pulse. Which effect is which is read from the ROM by the moves' own names, so nothing
 * here is a number from the build.
 *
 * Left as the row has them: Hidden Power (the type the player sets, as on PC), Weather Ball (MoveRules.adjust, by the
 * weather), Natural Gift (the game's berry table, gNaturalGiftTable, is not in the layout export), Struggle and Nature
 * Power, and Terastallization (off in this build).
 */
internal class HnsMoveTypes(private val data: HnsData, private val mem: MemoryReader) {
    private enum class Kind { ITEM, REVELATION_DANCE, RAGING_BULL, IVY_CUDGEL, TERRAIN_PULSE, AURA_WHEEL, HIDDEN_POWER, WEATHER_BALL, NATURAL_GIFT, OWN_RULE }

    private val MI = HnsLayout.MoveInfo
    private val II = HnsLayout.ItemInfo
    private val SI = HnsLayout.SpeciesInfo
    private val BM = HnsLayout.BattlePokemon
    private val V = HnsLayout.Volatiles

    /** Effect id to what it does to a type, from the moves carrying it (Judgment, Techno Blast and Multi-Attack share one). */
    private val kinds: Map<Int, Kind> by lazy {
        val byName = mapOf(
            "JUDGMENT" to Kind.ITEM, "TECHNOBLAST" to Kind.ITEM, "MULTIATTACK" to Kind.ITEM,
            "REVELATIONDANCE" to Kind.REVELATION_DANCE, "RAGINGBULL" to Kind.RAGING_BULL, "IVYCUDGEL" to Kind.IVY_CUDGEL,
            "TERRAINPULSE" to Kind.TERRAIN_PULSE, "AURAWHEEL" to Kind.AURA_WHEEL, "HIDDENPOWER" to Kind.HIDDEN_POWER,
            "WEATHERBALL" to Kind.WEATHER_BALL, "NATURALGIFT" to Kind.NATURAL_GIFT,
            "STRUGGLE" to Kind.OWN_RULE, "NATUREPOWER" to Kind.OWN_RULE,
        )
        val out = HashMap<Int, Kind>()
        for (id in 1 until HnsLayout.MOVES_COUNT) {
            val k = byName[BattleMoveTypes.norm(data.moveName(id))] ?: continue
            val m = data.move(id) ?: continue
            out[MI.effect.at(m)] = k
        }
        out
    }

    /** The ability names this reads, as [BattleMoveTypes.norm] has them. */
    private val ATE = mapOf("PIXILATE" to HnsLayout.TYPE_FAIRY, "REFRIGERATE" to HnsLayout.TYPE_ICE,
        "AERILATE" to HnsLayout.TYPE_FLYING, "GALVANIZE" to HnsLayout.TYPE_ELECTRIC)

    /**
     * GetBattlerTypes for the battle struct [b]: its three types, Roost's Flying gone for the turn (a pure Flying type
     * Normal, B_ROOST_PURE_FLYING being Gen 5 and later), as the expansion numbers types. The third is TYPE_MYSTERY when
     * nothing added one.
     */
    fun battlerTypes(b: ByteArray): List<Int> {
        val t = MutableList(3) { BM.types.at(b, 0, it) }
        if (V.roostActive.at(b, BM.volatiles.offset) != 0) {
            if (t[0] == HnsLayout.TYPE_FLYING && t[1] == HnsLayout.TYPE_FLYING) { t[0] = HnsLayout.TYPE_NORMAL; t[1] = HnsLayout.TYPE_NORMAL }
            else if (t[0] == HnsLayout.TYPE_FLYING) t[0] = HnsLayout.TYPE_MYSTERY
            else if (t[1] == HnsLayout.TYPE_FLYING) t[1] = HnsLayout.TYPE_MYSTERY
        }
        return t
    }

    /** [species]' place in its forms table (SpeciesInfo.formSpeciesIdTable) and that table's first species; (0, species) with none. */
    private fun formOf(species: Int): Pair<Int, Int> {
        val s = data.species(species) ?: return 0 to species
        val p = SI.formSpeciesIdTable.u32(s)
        if (p !in 0x08000000L..0x09FFFFFFL) return 0 to species
        val raw = mem.read(p, 64)
        val ids = (0 until raw.size / 2).map { raw.u16(it * 2) }.takeWhile { it != HnsLayout.FORM_SPECIES_END }
        val i = ids.indexOf(species)
        return if (i < 0 || ids.isEmpty()) 0 to species else i to ids[0]
    }

    private fun family(species: Int): Pair<Int, String> = formOf(species).let { (i, first) -> i to BattleMoveTypes.norm(data.speciesName(first)) }

    private fun speciesType2(species: Int): Int? = data.species(species)?.let { SI.types.at(it, 0, 1) }
        ?.takeIf { it != HnsLayout.TYPE_NONE && it != HnsLayout.TYPE_MYSTERY }

    /**
     * The rows of the battler in struct [b], each move with the type (Gen 3 ids) and power the game gives it now.
     * [abilityKnown]: the battler's ability may count (always for yours, once shown for an opponent). [itemKnown]: its
     * held item may count (yours only). [fieldStatuses]: gFieldStatuses (terrains, Gravity, Ion Deluge).
     */
    fun rows(rows: List<MoveRow>, b: ByteArray, abilityKnown: Boolean, itemKnown: Boolean, fieldStatuses: Long): List<MoveRow> {
        if (b.size < BM.SIZE) return rows
        val vo = BM.volatiles.offset
        val species = BM.species.at(b)
        // GetBattlerAbility: none while Gastro Acid holds it.
        val ability = if (!abilityKnown || V.gastroAcid.at(b, vo) != 0) "" else BattleMoveTypes.norm(data.abilityName(BM.ability.at(b)))
        val itemId = if (itemKnown) BM.item.at(b) else 0
        val item = if (itemId != 0) data.item(itemId) else null
        val itemName = if (itemId != 0) BattleMoveTypes.norm(data.itemName(itemId)) else ""
        val types = battlerTypes(b)
        val roost = V.roostActive.at(b, vo) != 0
        val electrified = V.electrified.at(b, vo) != 0
        // IsBattlerGrounded (src/battle_util.c IsBattlerGroundedInverseCheck), for Terrain Pulse.
        val grounded = when {
            itemName == "IRONBALL" -> true
            fieldStatuses and HnsLayout.STATUS_FIELD_GRAVITY.toLong() != 0L -> true
            V.root.at(b, vo) != 0 || V.smackDown.at(b, vo) != 0 -> true
            V.telekinesis.at(b, vo) != 0 || V.magnetRise.at(b, vo) != 0 || itemName == "AIRBALLOON" || ability == "LEVITATE" -> false
            HnsLayout.TYPE_FLYING in types -> false
            else -> true
        }
        val terrainAffected = grounded && V.semiInvulnerable.at(b, vo) == 0
        return rows.map { r ->
            val m = data.move(r.id) ?: return@map r
            val kind = kinds[MI.effect.at(m)]
            if (kind == Kind.HIDDEN_POWER || kind == Kind.WEATHER_BALL || kind == Kind.NATURAL_GIFT || kind == Kind.OWN_RULE) return@map r
            val romType = MI.type.at(m)
            var type: Int? = null
            var boost = false
            when (kind) {
                Kind.ITEM -> if (item != null && II.holdEffect.at(item) == MI.argument.at(m)) type = II.secondaryId.at(item)
                Kind.REVELATION_DANCE -> type = when {
                    types[0] != HnsLayout.TYPE_MYSTERY && !(roost && types[0] == HnsLayout.TYPE_FLYING) -> types[0]
                    types[1] != HnsLayout.TYPE_MYSTERY && !(roost && types[1] == HnsLayout.TYPE_FLYING) -> types[1]
                    roost -> HnsLayout.TYPE_NORMAL
                    else -> types[2]
                }
                Kind.RAGING_BULL -> family(species).let { (i, name) -> if (i > 0 && name == "TAUROS") type = speciesType2(species) }
                Kind.IVY_CUDGEL -> family(species).let { (i, name) -> if (i > 0 && name == "OGERPON") type = speciesType2(species) }
                Kind.TERRAIN_PULSE -> if (terrainAffected) type = when {
                    fieldStatuses and HnsLayout.STATUS_FIELD_ELECTRIC_TERRAIN.toLong() != 0L -> HnsLayout.TYPE_ELECTRIC
                    fieldStatuses and HnsLayout.STATUS_FIELD_GRASSY_TERRAIN.toLong() != 0L -> HnsLayout.TYPE_GRASS
                    fieldStatuses and HnsLayout.STATUS_FIELD_MISTY_TERRAIN.toLong() != 0L -> HnsLayout.TYPE_FAIRY
                    fieldStatuses and HnsLayout.STATUS_FIELD_PSYCHIC_TERRAIN.toLong() != 0L -> HnsLayout.TYPE_PSYCHIC
                    else -> null
                }
                else -> {}
            }
            // The switch's own answer stands; otherwise the abilities and Aura Wheel, in GetDynamicMoveType's order.
            if (type == null) {
                when {
                    MI.soundMove.at(m) != 0 && ability == "LIQUIDVOICE" -> type = HnsLayout.TYPE_WATER
                    kind == Kind.AURA_WHEEL && ability != "NORMALIZE" && family(species).let { (i, n) -> i == 1 && n == "MORPEKO" } -> type = HnsLayout.TYPE_DARK
                    romType == HnsLayout.TYPE_NORMAL && ability != "NORMALIZE" -> {
                        // TrySetAteType leaves alone the moves that set their own type.
                        val own = kind == Kind.ITEM || kind == Kind.REVELATION_DANCE || kind == Kind.TERRAIN_PULSE
                        ATE[ability]?.takeIf { !own }?.let { type = it; boost = true }
                    }
                    kind != Kind.ITEM && kind != Kind.TERRAIN_PULSE && ability == "NORMALIZE" -> { type = HnsLayout.TYPE_NORMAL; boost = true }
                }
            }
            // SetTypeBeforeUsingMove: Ion Deluge turns Normal moves Electric, Electrify every move.
            val final = type ?: romType
            if ((fieldStatuses and HnsLayout.STATUS_FIELD_ION_DELUGE.toLong() != 0L && final == HnsLayout.TYPE_NORMAL) || electrified) type = HnsLayout.TYPE_ELECTRIC
            if (type == null && !boost) return@map r
            r.copy(
                type = type?.let(HnsMon::gen3Type) ?: r.type,
                power = r.power?.let { if (boost && it > 1) BattleMoveTypes.ateBoosted(it) else it },
            )
        }
    }
}

/**
 * MaxDex 1.0: its attack canceler sets the move's type for the turn (gBattleStruct + 0x13, the type with bit 7 set)
 * in this order, the last write winning (0x081AF2C8 to 0x081AF544, read with arm-none-eabi-objdump on the patched ROM,
 * CRC 28C12926):
 * - Revelation Dance (648): the user's first type in its battle struct.
 * - Judgment (455), Multi-Attack (671), Techno Blast (552): the type of the user's type-boosting held item (0x081AEE5C:
 *   the item's hold effect through a jump table; Silk Scarf and anything else, Normal).
 * - Aura Wheel (709): Dark for Morpeko-H (species 1218).
 * - Normalize (ability 95): Normal for any move whose own type is not.
 * - Liquid Voice (ability 227): Water for a sound move ([SOUND], 0x081C80BC).
 * - Terrain Pulse (731): the terrain's type ([GameMap.terrain]).
 * - Pixilate, Refrigerate, Aerilate, Galvanize (192 to 195): Fairy, Ice, Flying, Electric for a Normal move with power.
 * Protean and Libero (210, 247) write the user's types into its battle struct (0x081AF54E), which the cards read.
 * Raging Bull, Ivy Cudgel and Natural Gift have no type code here. No power boost was found, so the power stays.
 */
internal object MaxDexMoveTypes {
    const val REVELATION_DANCE = 648
    const val JUDGMENT = 455
    const val MULTI_ATTACK = 671
    const val TECHNO_BLAST = 552
    const val AURA_WHEEL = 709
    const val TERRAIN_PULSE = 731
    const val MORPEKO_HANGRY = 1218
    const val NORMALIZE = 95
    const val LIQUID_VOICE = 227
    private val ATE = mapOf(192 to 18, 193 to 15, 194 to 2, 195 to 13)

    /** 0x081AEE5C's jump table: an item's hold effect to the type it gives Judgment, Multi-Attack and Techno Blast. */
    val ITEM_TYPES: Map<Int, Int> = mapOf(
        31 to 6, 42 to 8, 46 to 4, 47 to 5, 48 to 12, 49 to 17, 50 to 1, 51 to 13, 52 to 11, 53 to 2, 54 to 3, 55 to 15,
        56 to 7, 57 to 14, 58 to 10, 59 to 16, 67 to 18,
    )

    /** The moves 0x081C80BC calls sound moves, found by running it on every move id (tools/maxdex/sound_moves.py). */
    val SOUND: Set<Int> = setOf(45, 46, 47, 48, 103, 173, 195, 215, 253, 304, 319, 320, 355, 411, 454, 502, 503, 553, 561, 573,
        579, 586, 590, 626, 653, 701, 712, 752, 795, 836, 839)

    /** [type1]: the user's first battle type. [ability] null when not known; [itemHoldEffect] null when not known. [terrain] the terrain byte. */
    fun type(id: Int, romType: Int?, power: Int?, species: Int, type1: Int, ability: Int?, itemHoldEffect: Int?, terrain: Int): Int? {
        var t: Int? = null
        if (id == REVELATION_DANCE) t = type1 and 0x3F
        if ((id == JUDGMENT || id == MULTI_ATTACK || id == TECHNO_BLAST) && itemHoldEffect != null) t = ITEM_TYPES[itemHoldEffect] ?: 0
        if (id == AURA_WHEEL && species == MORPEKO_HANGRY) t = 17
        if (ability == NORMALIZE && romType != 0) t = 0
        if (ability == LIQUID_VOICE && id in SOUND) t = 11
        if (id == TERRAIN_PULSE) when (terrain) { 1 -> t = 12; 2 -> t = 18; 3 -> t = 13; 4 -> t = 14 }
        if (romType == 0 && (power ?: 0) != 0) ability?.let { ATE[it] }?.let { t = it }
        return t
    }
}
