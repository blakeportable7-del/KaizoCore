package com.ironmonone.tracker

/**
 * Heart & Soul's chance to catch, the game's own arithmetic: pokehns-expansion's src/battle_script_commands.c as the
 * KaizoCore build compiles it (Nat. Dex Emerald's Gen 3 rules, patch 0047 on, build AA092306), ComputeBallData,
 * ComputeCaptureOdds, ComputeBallShakeOdds and Cmd_handleballthrow, with include/config/battle.h's values for that
 * build. Every step is the C's integer step in the C's order, so the odds come out to the unit the game gets.
 *
 * Until 2026-10-11 this was the rc37 build's arithmetic (Gen 9 settings): its low-level bonus made a level 5 Magikarp in
 * a Poke Ball read 99% while the game, on Gen 3's rules, gives it about a third (Blake: four balls).
 *
 * The config, as built (every catch setting GEN_3, KAIZOCORE_GEN3_BATTLE on):
 *  - B_INCAPACITATED_CATCH_BONUS Gen 3: sleep or freeze x2; poison, burn, paralysis, toxic or frostbite x1.5.
 *  - B_LOW_LEVEL_CATCH_BONUS Gen 3 and B_MISSING_BADGE_CATCH_MALUS Gen 3: neither applies.
 *  - B_CRITICAL_CAPTURE FALSE: no critical captures, so a throw is always four shake checks.
 *  - In the Safari Zone every ball is a Safari Ball (x1.5) and every ball uses the Safari catch factor.
 *  - Kurt's Apricorn Balls (Level, Lure, Moon, Friend, Love, Fast, Heavy) and the GS Ball catch as a Poke Ball (Blake,
 *    2026-10-10: Emerald has none of them).
 *  - Balls: Net x3 for Water or Bug, Dive x3.5 underwater only, Nest Gen 3's (400 - 10 x level)/100 below level 30,
 *    Repeat x3, Timer Gen 3's (100 + 10 x turn)/100 up to x4, Dusk x3.5 in a cave or underground only (Emerald has no
 *    night for it), Quick x4 on the first turn, Dream x1, Safari and Sport x1.5.
 */
internal object HnsCatch {
    /** What ComputeCaptureOdds returns for the Master Ball (and the GS Ball on Celebi): CAPTURE_GUARANTEED. */
    const val GUARANTEED = -1L

    /** The wild Pokemon being thrown at (gBattleMons[GetCatchingBattler()]), with what its species says. */
    data class Target(
        val species: Int,
        val catchRate: Int,
        val hp: Int,
        val maxHp: Int,
        val level: Int,
        val status1: Long = 0,
        /** GetBattlerTypes: the battle's three type slots (HnS TYPE_ ids), Roost already applied. */
        val types: List<Int> = emptyList(),
        val ability: Int = 0,
        val gender: Int = HnsLayout.MON_GENDERLESS,
        val isUltraBeast: Boolean = false,
        val baseSpeed: Int = 0,
        /** SpeciesInfo.weight, in hectograms. */
        val weight: Int = 0,
        /** One of its evolutions is EVO_ITEM with the Moon Stone (the Moon Ball's own rule). */
        val evolvesByMoonStone: Boolean = false,
    ) {
        fun isType(vararg t: Int) = types.any { it in t }
    }

    /** The thrower's side and the place. */
    data class Field(
        val playerSpecies: Int = 0,
        val playerLevel: Int = 0,
        val playerGender: Int = HnsLayout.MON_GENDERLESS,
        /** gBattleResults.battleTurnCounter. */
        val turn: Int = 0,
        /** The Pokedex's caught flag for the species (the Repeat Ball). */
        val caughtBefore: Boolean = false,
        val timeOfDay: Int = 0,
        /** gMapHeader.cave and gMapHeader.mapType. */
        val cave: Boolean = false,
        val mapType: Int = 0,
        val fishing: Boolean = false,
        val surfing: Boolean = false,
        val safari: Boolean = false,
        val safariCatchFactor: Int = 0,
        /** Badge flags set, all sixteen counted (capped at eight here, as the game does). */
        val badges: Int = 0,
    )

    class BallData(val multiplier: Int, val divider: Int, val flatBonus: Int, val guaranteed: Boolean)

    /** ComputeBallData for [ball] (a BALL_ id: ItemIdToBallId, the item's secondaryId). */
    fun ballData(ball: Int, t: Target, f: Field): BallData {
        var mul = 100; var div = 100; var flat = 0; var sure = false
        if (t.isUltraBeast) {
            if (ball == HnsLayout.BALL_BEAST) mul = 500 else { mul = 410; div = 4096 }
            return BallData(mul, div, flat, false)
        }
        // KaizoCore: every ball in the Safari Zone is a Safari Ball.
        if (f.safari) return BallData(150, 100, 0, false)
        // KaizoCore: Kurt's Apricorn Balls and the GS Ball catch as a Poke Ball.
        if (ball in APRICORN_AND_GS) return BallData(100, 100, 0, false)
        when (ball) {
            HnsLayout.BALL_GREAT -> mul = 150
            HnsLayout.BALL_ULTRA -> mul = 200
            HnsLayout.BALL_MASTER -> sure = true
            HnsLayout.BALL_NET -> if (t.isType(HnsLayout.TYPE_WATER, HnsLayout.TYPE_BUG)) mul = 300
            // B_NEST_BALL_MODIFIER is GEN_3: the else-if branch.
            HnsLayout.BALL_NEST -> if (t.level < 30) mul = 400 - t.level * 10
            HnsLayout.BALL_DIVE -> if (f.mapType == HnsLayout.MAP_TYPE_UNDERWATER) mul = 350
            HnsLayout.BALL_DUSK -> if (f.cave || f.mapType == HnsLayout.MAP_TYPE_UNDERGROUND) mul = 350
            HnsLayout.BALL_TIMER -> { mul = 100 + f.turn * 10; if (mul > 4 * div) mul = 4 * div }
            HnsLayout.BALL_QUICK -> if (f.turn == 0) mul = 400
            HnsLayout.BALL_REPEAT -> if (f.caughtBefore) mul = 300
            HnsLayout.BALL_SAFARI -> mul = 150
            HnsLayout.BALL_SPORT -> mul = 150
            HnsLayout.BALL_BEAST -> { mul = 410; div = 4096 }
        }
        return BallData(mul, div, flat, sure)
    }

    private val APRICORN_AND_GS = setOf(HnsLayout.BALL_LEVEL, HnsLayout.BALL_LURE, HnsLayout.BALL_MOON, HnsLayout.BALL_FRIEND,
        HnsLayout.BALL_LOVE, HnsLayout.BALL_FAST, HnsLayout.BALL_HEAVY, HnsLayout.BALL_GS)
    private const val U32 = 0xFFFFFFFFL
    private val INCAPACITATED = (HnsLayout.STATUS1_SLEEP or HnsLayout.STATUS1_FREEZE).toLong()
    private val CAN_MOVE = (HnsLayout.STATUS1_POISON or HnsLayout.STATUS1_BURN or HnsLayout.STATUS1_PARALYSIS or
        HnsLayout.STATUS1_TOXIC_POISON or HnsLayout.STATUS1_FROSTBITE).toLong()

    /** ComputeCaptureOdds: the "a" value, or [GUARANTEED]. Unsigned 32-bit as in the C. */
    fun odds(ball: Int, t: Target, f: Field): Long {
        val b = ballData(ball, t, f)
        if (b.guaranteed) return GUARANTEED
        if (t.maxHp <= 0) return 0
        var odds = (t.maxHp * 3L - t.hp * 2L) and U32
        // KaizoCore: in the Safari Zone every ball uses the Safari catch factor (Emerald's).
        var catchRate = if (f.safari) f.safariCatchFactor * 1275 / 100 else t.catchRate
        catchRate += b.flatBonus
        if (catchRate <= 0) catchRate = 1
        odds = (odds * catchRate and U32) / (t.maxHp * 3L)
        odds = (odds * b.multiplier and U32) / b.divider
        // Gen 3: no missing-badge malus and no low-level bonus; sleep and freeze double the odds.
        if (t.status1 and INCAPACITATED != 0L) odds *= 2
        if (t.status1 and CAN_MOVE != 0L) odds = odds * 15 / 10
        return odds and U32
    }

    /** ComputeBallShakeOdds: each of the four checks passes when RandomUniform(0, 65535) is below this. */
    fun shakeOdds(odds: Long): Long {
        if (odds == 0L) return 0
        return 1048560L / isqrt(isqrt(16711680L / odds))
    }

    /**
     * The chance a throw catches, 0 to 1: certain for [GUARANTEED] and for odds over 254, else four shake checks
     * each passing with shakeOdds / 65536 (no critical captures in this build).
     */
    fun chance(odds: Long): Double {
        if (odds == GUARANTEED || odds > 254) return 1.0
        val p = minOf(shakeOdds(odds), 65536L) / 65536.0
        return p * p * p * p
    }

    /** [chance] as the screen's whole percent: 100 only when certain, otherwise rounded down, so never overstated. */
    fun percent(chance: Double): Int = if (chance >= 1.0) 100 else Math.floor(chance * 100).toInt().coerceIn(0, 99)

    /** The BIOS Sqrt (swi 8): the integer square root, rounded down. */
    private fun isqrt(n: Long): Long {
        if (n <= 0) return 0
        var r = Math.sqrt(n.toDouble()).toLong()
        while (r * r > n) r--
        while ((r + 1) * (r + 1) <= n) r++
        return r
    }
}
