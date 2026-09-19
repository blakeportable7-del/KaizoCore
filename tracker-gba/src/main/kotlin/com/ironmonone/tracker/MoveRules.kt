package com.ironmonone.tracker

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The PC tracker's rules for what each row of the move table shows
 * (Ironmon-Tracker v9.3.1: TrackerScreen.lua drawMovesArea, DataHelper.lua
 * 255-370, MoveData.lua and Utils.lua).
 *
 * - Variable-power moves show a label instead of the ROM's number: the ROM
 *   holds a placeholder for them (the randomizer sets it to 1), which this app
 *   used to print as if it were the move's power.
 * - In battle, STAB draws the power green, and effectiveness against the
 *   target is marked beside it.
 * - "Calculate variable damage" (on by default) turns some labels into
 *   numbers when the tracker can know them: Weather Ball and Low Kick and the
 *   one-hit KO moves in battle; Flail, Reversal, Eruption, Water Spout, Return
 *   and Frustration only for your own Pokemon, since they would reveal the
 *   opponent's HP or friendship.
 *
 * Type ids are Gen 3's: 0 Normal ... 8 Steel, 10 Fire, 11 Water, 12 Grass ...
 */
object MoveRules {
    const val GUILLOTINE = 12
    const val HORN_DRILL = 32
    const val LOW_KICK = 67
    const val FISSURE = 90
    const val FLAIL = 175
    const val REVERSAL = 179
    const val RETURN = 216
    const val FRUSTRATION = 218
    const val HIDDEN_POWER = 237
    const val ERUPTION = 284
    const val WEATHER_BALL = 311
    const val WATER_SPOUT = 323
    const val SHEER_COLD = 329

    /**
     * MoveData.lua's `variablepower` moves and the power each shows. "0" is
     * drawn as a dash, like any move with no power.
     */
    val VARIABLE_POWER: Map<Int, String> = mapOf(
        12 to "0", 32 to "0", 49 to "0", 67 to "WT", 68 to "0", 69 to "0", 82 to "0",
        90 to "0", 101 to "0", 117 to "0", 149 to "0", 162 to "0", 175 to "<HP",
        179 to "<HP", 216 to ">FR", 217 to "RNG", 218 to "<FR", 222 to "RNG",
        237 to "VAR", 243 to "0", 255 to "100x", 283 to "0", 284 to ">HP",
        323 to ">HP", 329 to "0",
    )

    /** MoveData.IsTypelessMove: Future Sight, Beat Up, Doom Desire. */
    private val TYPELESS = setOf(248, 251, 353)

    /** MoveData.StatusMovesWillFail: the status moves a type is immune to. */
    private val STATUS_WILL_FAIL = mapOf(
        73 to setOf(12),        // Leech Seed: Grass
        77 to setOf(8, 3),      // PoisonPowder: Steel, Poison
        86 to setOf(4),         // Thunder Wave: Ground
        92 to setOf(8, 3),      // Toxic
        137 to setOf(7),        // Glare: Ghost
        139 to setOf(8, 3),     // Poison Gas
        261 to setOf(10),       // Will-O-Wisp: Fire
    )

    private val OHKO = setOf(GUILLOTINE, HORN_DRILL, FISSURE, SHEER_COLD)

    /**
     * Hidden Power's type is not the ROM's Normal: the reference leaves it
     * unknown until the player sets it, so it is neither STAB nor effective.
     */
    fun shownType(id: Int, romType: Int?): Int? = if (id == HIDDEN_POWER) null else romType

    /** The power column before any adjustment: the variable-power label, else the ROM number. "0" = none. */
    fun basePower(id: Int, romPower: Int?): String = VARIABLE_POWER[id] ?: (romPower ?: 0).toString()

    /** Utils.isSTAB. [category] is "PHY", "SPE" or "STA". */
    fun isStab(id: Int, type: Int?, category: String?, power: String, attackerTypes: List<Int>): Boolean {
        if (type == null || attackerTypes.isEmpty()) return false
        if (id in TYPELESS || category == "STA" || power == "0") return false
        if (type == 0 && id == HIDDEN_POWER) return false
        return type in attackerTypes
    }

    /** Utils.netEffectiveness: 1.0 wherever the check does not apply. */
    fun effectiveness(id: Int, type: Int?, category: String?, targetTypes: List<Int>): Double {
        if (type == null || targetTypes.isEmpty() || id in TYPELESS) return 1.0
        if (category == "STA") {
            val immune = STATUS_WILL_FAIL[id] ?: return 1.0
            return if (targetTypes.any { it in immune }) 0.0 else 1.0
        }
        var total = Gen3Types.effect(type, targetTypes[0])
        if (targetTypes.size > 1 && targetTypes[1] != targetTypes[0]) total *= Gen3Types.effect(type, targetTypes[1])
        return total
    }

    /** One side of the battle, as the adjustments need it. */
    data class Side(
        val level: Int,
        val curHp: Int = 0,
        val maxHp: Int = 0,
        val friendship: Int = 0,
        /** Kilograms, from the species table; null when unknown. */
        val weightKg: Double? = null,
    )

    data class Shown(val type: Int?, val power: String, val acc: String)

    /**
     * MoveData.adjustVariableMoveValues. [weather] is the tracker's RAIN,
     * SANDSTORM, SUN or HAIL. [viewingOwn] is whether [source] is the player's.
     */
    fun adjust(
        id: Int, type: Int?, power: String, acc: String,
        source: Side, target: Side?, inBattle: Boolean, viewingOwn: Boolean,
        weather: String?, determineFriendship: Boolean,
    ): Shown {
        var t = type; var p = power; var a = acc
        when (id) {
            WEATHER_BALL -> if (inBattle) {
                val w = when (weather) { "RAIN" -> 11; "SANDSTORM" -> 5; "SUN" -> 10; "HAIL" -> 15; else -> null }
                if (w != null) { t = w; p.toIntOrNull()?.let { p = (it * 2).toString() } }
            }
            LOW_KICK -> if (inBattle && target?.weightKg != null) p = weightBased(target.weightKg)
            FLAIL, REVERSAL -> if (viewingOwn && source.maxHp > 0) p = lowHpBased(source.curHp, source.maxHp)
            ERUPTION, WATER_SPOUT -> if (viewingOwn && source.maxHp > 0) p = highHpBased(p, source.curHp, source.maxHp)
            RETURN, FRUSTRATION -> if (viewingOwn) p = friendshipBased(p, source.friendship, determineFriendship)
            in OHKO -> if (inBattle && target != null) {
                val diff = source.level - target.level
                if (diff > 0) a = min((a.toIntOrNull() ?: 30) + diff, 100).toString()
                else if (diff < 0) a = "X"
            }
        }
        return Shown(t, p, a)
    }

    /** Utils.calculateWeightBasedDamage. */
    fun weightBased(kg: Double): String = when {
        kg == 0.0 -> "0"
        kg < 10.0 -> "20"
        kg < 25.0 -> "40"
        kg < 50.0 -> "60"
        kg < 100.0 -> "80"
        kg < 200.0 -> "100"
        else -> "120"
    }

    /** Utils.calculateLowHPBasedDamage: the game's own s32 fraction of 48. */
    fun lowHpBased(cur: Int, maxHp: Int): String {
        val f = cur * 48 / max(maxHp, 1)
        return when {
            f <= 1 -> "200"
            f <= 4 -> "150"
            f <= 9 -> "100"
            f <= 16 -> "80"
            f <= 32 -> "40"
            else -> "20"
        }
    }

    /** Utils.calculateHighHPBasedDamage: 150 scaled by HP, at least 1, rounded half up. */
    fun highHpBased(power: String, cur: Int, maxHp: Int): String {
        val base = if (power == ">HP") 150.0 else power.toDoubleOrNull() ?: return power
        return floor(max(base * cur / max(maxHp, 1), 1.0) + 0.5).toInt().toString()
    }

    /**
     * Utils.calculateFriendshipBasedDamage: revealed only once it would reach
     * 100 or more, and only with "Determine friendship readiness" on.
     */
    fun friendshipBased(power: String, friendship: Int, determineFriendship: Boolean): String {
        if (!determineFriendship || (power != ">FR" && power != "<FR")) return power
        var f = friendship.coerceIn(0, 255)
        if (power == "<FR") f = 255 - f
        val bp = max(f / 2.5, 1.0)
        return if (bp < 100) power else floor(bp).toInt().toString()
    }
}
