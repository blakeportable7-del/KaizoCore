package com.ironmonone.tracker.gachamon

/**
 * The move and ability facts GachaMon's rating reads besides the rating file: the reference's MoveData and AbilityData
 * tables (Ironmon-Tracker data/MoveData.lua, data/AbilityData.lua, Utils.isSTAB), with the reference's ids.
 */
object GachaMonMoves {
    // AbilityData.Values
    const val DRIZZLE = 2
    const val COMPOUNDEYES = 14
    const val IMMUNITY = 17
    const val LEVITATE = 26
    const val MAGMA_ARMOR = 40
    const val WATER_VEIL = 41
    const val SAND_STREAM = 45
    const val HUSTLE = 55
    const val ROCK_HEAD = 69
    const val DROUGHT = 70

    // MoveData.Values
    const val LOW_KICK = 67
    const val TRIPLE_KICK = 167
    const val FLAIL = 175
    const val REVERSAL = 179
    const val RETURN = 216
    const val FRUSTRATION = 218
    const val HIDDEN_POWER = 237
    const val ERUPTION = 284
    const val WEATHER_BALL = 311
    const val WATER_SPOUT = 323

    /** Psycho Boost: the five games' last move. Ids past it are the Nat. Dex build's own (MoveData.getNatDexCompatible). */
    const val VANILLA_LAST_MOVE = 354

    /**
     * AbilityData.getTypeDefensiveAbilities: the attacking types each ability takes the sting out of, by Gen 3 type id
     * (10 Fire, 11 Water, 13 Electric, 4 Ground, 15 Ice).
     */
    val TYPE_DEFENSIVE_ABILITIES: Map<Int, Set<Int>> = mapOf(
        DRIZZLE to setOf(10),        // Drizzle: Fire
        10 to setOf(13),             // Volt Absorb: Electric
        11 to setOf(11),             // Water Absorb: Water
        18 to setOf(10),             // Flash Fire: Fire
        LEVITATE to setOf(4),        // Levitate: Ground
        47 to setOf(10, 15),         // Thick Fat: Fire, Ice
        DROUGHT to setOf(11),        // Drought: Water
    )

    /** MoveData.IsTypelessMove: Future Sight, Beat Up and Doom Desire, never same-type. */
    private val TYPELESS = setOf(248, 251, 353)
    /** MoveData.IsOHKOMove: Guillotine, Horn Drill, Fissure, Sheer Cold. */
    private val OHKO = setOf(12, 32, 90, 329)
    /** MoveData.IsRecoilMove: Take Down, Double-Edge, Submission, Volt Tackle (not Struggle). */
    private val RECOIL = setOf(36, 38, 66, 344)
    /** MoveData.IsNoMissDamagingMove: Swift, Faint Attack, Shadow Punch, Aerial Ace, Magical Leaf, Shock Wave. */
    private val NO_MISS = setOf(129, 185, 325, 332, 345, 351)
    /** getExpectedPower: the moves that strike 2-5 times, counted as three hits. */
    private val MULTI_HIT = setOf(292, 140, 198, 331, 4, 3, 31, 154, 333, 42, 350, 131)
    /** getExpectedPower: the moves that strike exactly twice. */
    private val DOUBLE_HIT = setOf(155, 24, 41)

    fun isOhko(id: Int): Boolean = id in OHKO
    fun isRecoil(id: Int): Boolean = id in RECOIL
    fun isNoMissDamaging(id: Int): Boolean = id in NO_MISS

    /**
     * MoveData.lua's variablepower moves and the power text the reference keeps for them (variable-power.tsv): buildData
     * leaves it alone ("randomizer sets them to 1"), so Low Kick reads "WT" and Seismic Toss "0" on PC whatever the ROM
     * holds. Only the five games' ids: the Nat. Dex build's own moves have no such entries and take the ROM's power.
     */
    val VARIABLE_POWER: Map<Int, String> by lazy {
        val out = LinkedHashMap<Int, String>()
        GachaMonMoves::class.java.getResourceAsStream("/gachamon/variable-power.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { !it.startsWith("#") && it.isNotBlank() }.forEach { line ->
                val p = line.split('\t')
                p.getOrNull(0)?.trim()?.toIntOrNull()?.let { out[it] = p.getOrElse(1) { "" } }
            }
        }
        out
    }

    /** The power text MoveData holds for [id] when the ROM says [romPower]: the reference's own text for a variable move. */
    fun powerText(id: Int, romPower: Int): String = if (id <= VANILLA_LAST_MOVE) VARIABLE_POWER[id] ?: romPower.toString() else romPower.toString()

    /** MoveData.getExpectedPower: a guess at the move's real power (multi-hit moves as three hits, HP moves at full). */
    fun expectedPower(m: MoveFacts): Int {
        when (m.id) {
            LOW_KICK -> return 80
            ERUPTION, WATER_SPOUT -> return 150
            FLAIL, REVERSAL -> return 80
            RETURN -> return 102
            FRUSTRATION -> return 50
            TRIPLE_KICK -> return 60
        }
        val power = m.power.trim().toIntOrNull() ?: 0
        return when (m.id) {
            in DOUBLE_HIT -> power * 2
            in MULTI_HIT -> power * 3
            else -> power
        }
    }

    /** Utils.isSTAB: the move's type is one of [types], and it is a damaging move that can be same-type. */
    fun isStab(m: MoveFacts, types: List<Int>): Boolean {
        if (m.type == MYSTERY) return false
        if (m.id in TYPELESS || m.category == MoveCategory.STATUS || m.power == "0" || m.power == "---") return false
        // The default Hidden Power type (Normal) can't happen, so it is never same-type
        if (m.type == NORMAL && m.id == HIDDEN_POWER) return false
        return m.type in types
    }

    private const val NORMAL = 0
    private const val MYSTERY = 9
}

/** PokemonData's static base stat totals (bst.tsv): the five games' list, and the Nat. Dex extension's. */
object GachaMonBst {
    private val table: Map<Int, Pair<Int?, Int?>> by lazy {
        val out = HashMap<Int, Pair<Int?, Int?>>()
        GachaMonBst::class.java.getResourceAsStream("/gachamon/bst.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { !it.startsWith("#") && it.isNotBlank() }.forEach { line ->
                val p = line.split('\t')
                p.getOrNull(0)?.trim()?.toIntOrNull()?.let { out[it] = p.getOrNull(1)?.trim()?.toIntOrNull() to p.getOrNull(2)?.trim()?.toIntOrNull() }
            }
        }
        out
    }

    /** The listed total for [species] in the five games ([natDex] false) or a Nat. Dex build; null where there is none. */
    fun listed(species: Int, natDex: Boolean): Int? = table[species]?.let { if (natDex) it.second else it.first }
}
