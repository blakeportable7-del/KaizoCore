package com.ironmonone.tracker

import kotlin.math.floor

/**
 * Calc Atk: UTDZac's "Attacking Damage Calc." extension for the Gen 3 tracker (v1.2, MIT,
 * github.com/UTDZac/CalcAtk-IronmonExtension). After an opponent hits you, its attacking
 * stat is estimated backwards from the damage it did, with the Gen 3 damage formula
 * (pokefirered CalculateBaseDamage), so the player learns only from what they saw.
 *
 * [estimate] is the extension's calcLowHighStat() line for line, including its floating
 * point and floor() order; CalcAtkTest holds it to the extension's own Lua run over 4000
 * inputs (tools/parity/calcatk_fixture.py).
 */
object CalcAtk {
    /** The extension's bounds; an estimate of (MAX_STAT, MIN_STAT) means no stat fits. */
    const val MIN_STAT = 0
    const val MAX_STAT = 999

    /** Weather states of the extension's checkbox: none, boosted (x1.5), halved (x0.5). */
    const val WEATHER_NONE = 0
    const val WEATHER_BOOSTED = 1
    const val WEATHER_HALVED = 2

    data class Inputs(
        /** The enemy Pokemon's level. */
        val level: Int,
        /** The damage it dealt. */
        val damage: Int,
        /** Your DEF for a physical move, SPD for a special one. */
        val defense: Int,
        val power: Int,
        /** 0.25, 0.5, 1, 2 or 4. */
        val effectiveness: Double = 1.0,
        /** Anything else, such as Flash Fire or Helping Hand. */
        val other: Double = 1.0,
        val stab: Boolean = false,
        val crit: Boolean = false,
        val weather: Int = WEATHER_NONE,
        /** The enemy is burned and the move is physical. */
        val burned: Boolean = false,
        /** Your Reflect or Light Screen is up against this move's category. */
        val screen: Boolean = false,
    )

    /** The damage roll range, 85% to 100%, for [attack] with [i] (the extension's formuoli). */
    fun damageRange(i: Inputs, attack: Int): Pair<Int, Int> {
        val defense = if (i.defense == 0) 1 else i.defense
        val power = if (i.power == 0) 1 else i.power
        val mType = if (i.effectiveness == 0.0) 1.0 else i.effectiveness
        val mOther = if (i.other == 0.0) 1.0 else i.other
        val mStab = if (i.stab) 1.5 else 1.0
        val mCritical = if (i.crit) 2.0 else 1.0
        val mWeather = when (i.weather) { WEATHER_BOOSTED -> 1.5; WEATHER_HALVED -> 0.5; else -> 1.0 }
        val mBurn = if (i.burned) 0.5 else 1.0
        val mScreen = if (i.screen) 0.5 else 1.0
        // "anytime division occurs, it needs to be integer division": floor where the Lua floors.
        val part1 = floor(floor((2.0 * i.level / 5 + 2) * power * (attack.toDouble() / defense)) / 50)
        val part2 = part1 * mBurn * mScreen * mWeather + 2
        val part3 = part2 * mCritical * mStab * mType * mOther
        return floor(part3 * 85 / 100).toInt() to floor(part3 * 100 / 100).toInt()
    }

    /**
     * The lowest and highest attacking stat, trying 1 to 255 as the extension does, whose roll
     * range includes [Inputs.damage]. (MAX_STAT, MIN_STAT) when none does.
     */
    fun estimate(i: Inputs): Pair<Int, Int> {
        var low = MAX_STAT
        var high = MIN_STAT
        for (attack in 1..255) {
            val (lo, hi) = damageRange(i, attack)
            if (i.damage in lo..hi) {
                if (attack < low) low = attack
                if (attack > high) high = attack
            }
        }
        return low to high
    }

    /** True when [estimate]'s result names a stat at all. */
    fun found(result: Pair<Int, Int>): Boolean = result.first <= result.second

    /** What [autoFill] fills in, and whether the power had to be guessed (the extension marks it). */
    data class Fill(val inputs: Inputs, val guessed: Boolean, val physical: Boolean)

    private const val ROCK = 5
    private const val FIRE = 10
    private const val WATER = 11
    private const val ICE = 15
    private const val TRIPLE_KICK = 167
    /** Double Kick, Bonemerang: always two hits (CalcAtk.lua doubleHitMoves). */
    private val DOUBLE_HIT = setOf(24, 155)
    /** Two to five hits, so one hit's damage cannot be told apart (CalcAtk.lua multiHitMoves). */
    private val MULTI_HIT = setOf(292, 140, 198, 331, 4, 3, 31, 154, 333, 42, 350, 131)

    /** Utils.calculateWeatherBall's gBattleWeather values and the type each gives. */
    private fun weatherBallType(word: Int?): Int? = when (word) {
        1, 5 -> WATER; 8, 24 -> ROCK; 32, 96 -> FIRE; 128 -> ICE; else -> null
    }

    /** The extension's getMovePowerAndType guesstimate: round to the nearest ten. */
    private fun roundTen(v: Double): Int = (floor(v / 10 + 0.5) * 10).toInt()

    /**
     * The extension's autoApplyValues and getMovePowerAndType: the formula filled in from the
     * move the enemy just used ([moveId], [power], [type] and [category] as the tracker has
     * them), the damage, your Pokemon and the enemy. Crit, screens and other multipliers stay
     * for the player to set, as there.
     *
     * One deliberate difference: Eruption and Water Spout. The extension rounds the enemy's
     * HP ratio (0 to 1) to the nearest ten, which is always 0, so their power always read 1;
     * here the power (150 by HP) is what gets rounded to the nearest ten.
     */
    fun autoFill(
        moveId: Int, power: String, type: Int?, category: String?,
        damage: Int,
        ownTypes: List<Int>, ownDef: Int, ownSpd: Int, ownWeightKg: Double?,
        enemyTypes: List<Int>, enemyLevel: Int, enemyCurHp: Int, enemyMaxHp: Int, enemyBurned: Boolean,
        enemyBaseFriendship: Int?, wild: Boolean, weatherWord: Int?,
    ): Fill {
        var guessed = false
        var moveType = type
        var movePower = power.toIntOrNull() ?: 0
        when (moveId) {
            // Utils.calculateWeatherBall: typed by the weather and doubled.
            MoveRules.WEATHER_BALL -> weatherBallType(weatherWord)?.let { moveType = it; movePower *= 2 }
            MoveRules.LOW_KICK -> if (ownWeightKg != null) movePower = MoveRules.weightBased(ownWeightKg).toIntOrNull() ?: 0
            MoveRules.ERUPTION, MoveRules.WATER_SPOUT -> if (enemyMaxHp > 0) {
                guessed = true
                movePower = maxOf(roundTen(150.0 * enemyCurHp / enemyMaxHp), 1)
            }
            // "Fudge the numbers a bit to guesstimate the ratio"; 200 is never shown.
            MoveRules.FLAIL, MoveRules.REVERSAL -> if (enemyMaxHp > 0) {
                guessed = true
                val ratio = enemyCurHp * 48.0 / enemyMaxHp
                movePower = when { ratio <= 5 -> 150; ratio <= 10 -> 100; ratio <= 18 -> 80; ratio <= 34 -> 40; else -> 20 }
            }
            // A wild Pokemon's friendship is its species' base (70 when unknown).
            MoveRules.RETURN -> if (wild) {
                guessed = true
                movePower = maxOf(roundTen((enemyBaseFriendship ?: 70) / 2.5), 1)
            }
            MoveRules.FRUSTRATION -> if (wild) {
                guessed = true
                movePower = maxOf(roundTen((255 - (enemyBaseFriendship ?: 70)) / 2.5), 1)
            }
            TRIPLE_KICK -> { guessed = true; movePower = movePower + movePower * 2 + movePower * 3 }
            in DOUBLE_HIT -> movePower *= 2
            in MULTI_HIT -> guessed = true
        }
        // MoveData.TypeToCategory: Gen 3's split is by type, types 0-8 physical.
        val physical = moveType != null && moveType in 0..8
        // getWeatherBoostState reads the move's own type, not Weather Ball's new one.
        val weather = when (weatherWord) {
            1, 5 -> when (type) { WATER -> WEATHER_BOOSTED; FIRE -> WEATHER_HALVED; else -> WEATHER_NONE }
            32, 96 -> when (type) { FIRE -> WEATHER_BOOSTED; WATER -> WEATHER_HALVED; else -> WEATHER_NONE }
            else -> WEATHER_NONE
        }
        val inputs = Inputs(
            level = enemyLevel, damage = damage,
            defense = if (physical) ownDef else ownSpd,
            power = movePower,
            effectiveness = MoveRules.effectiveness(moveId, moveType, category, ownTypes),
            // Utils.isSTAB(move, move.type, enemyTypes): the move's own type and power.
            stab = MoveRules.isStab(moveId, type, category, power, enemyTypes),
            weather = weather,
            burned = physical && enemyBurned,
        )
        return Fill(inputs, guessed, physical)
    }
}
