package com.ironmonone.tracker

/**
 * Heart & Soul's chance to catch, the game's own arithmetic: pokehns-expansion's src/battle_script_commands.c as the
 * KaizoCore build compiles it (branch kaizocore-rc37, CRC C218FD9E), ComputeBallData, ComputeCaptureOdds,
 * ComputeBallShakeOdds and Cmd_handleballthrow, with include/config/battle.h's values for that build. Every step is
 * the C's integer step in the C's order, so the odds come out to the unit the game gets.
 *
 * The config, as built (GEN_LATEST is GEN_9):
 *  - B_INCAPACITATED_CATCH_BONUS Gen 9: sleep or freeze x2.5; poison, burn, paralysis, toxic or frostbite x1.5.
 *  - B_LOW_LEVEL_CATCH_BONUS Gen 9: a wild Pokemon of level 13 or less, x(36 - 2 x level) / 10.
 *  - B_MISSING_BADGE_CATCH_MALUS Gen 9: with fewer than eight badges, x4/5 for each badge level (sBadgeLevel: 25, 30,
 *    35 ... 60, 100) from the badges held upward that the wild Pokemon's level is above. Heart & Soul has sixteen
 *    badge flags; the count is capped at eight (NUM_BADGES_CAPPED).
 *  - B_CRITICAL_CAPTURE FALSE: no critical captures, so a throw is always four shake checks. (B_CRITICAL_CAPTURE_IF_OWNED
 *    only changes the animation of a catch that already succeeded.)
 *  - Balls: Net x3.5 (Gen 7), Dive x3.5 underwater or when surfing or fishing (Gen 4), Nest Gen 3's (400 - 10 x level)/100
 *    below level 30, Repeat x3.5 (Gen 7), Timer Gen 3's (100 + 10 x turn)/100 up to x4, Dusk x3.5 (Gen 3), Quick x4 on
 *    the first turn (Gen 3), Lure x3 when fishing (Gen 3), Heavy Gen 3's weight brackets, Dream x4 asleep (Gen 8),
 *    Safari and Sport x1.5 (Gen 3). Heart & Soul's own (IS_HNS): Friend x4 for Bug or Grass, and Level, Lure, Moon,
 *    Love and Fast at least x4 for their types, Heavy x4 for Rock, Ground or Steel; the GS Ball x25.5, Celebi caught.
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
        when (ball) {
            HnsLayout.BALL_FRIEND -> if (t.isType(HnsLayout.TYPE_BUG, HnsLayout.TYPE_GRASS)) mul = 400
            HnsLayout.BALL_GREAT -> mul = 150
            HnsLayout.BALL_ULTRA -> mul = 200
            HnsLayout.BALL_MASTER -> sure = true
            HnsLayout.BALL_NET -> if (t.isType(HnsLayout.TYPE_WATER, HnsLayout.TYPE_BUG)) mul = 350
            // B_NEST_BALL_MODIFIER is GEN_3: the else-if branch.
            HnsLayout.BALL_NEST -> if (t.level < 30) mul = 400 - t.level * 10
            HnsLayout.BALL_DIVE -> if (f.mapType == HnsLayout.MAP_TYPE_UNDERWATER || f.fishing || f.surfing) mul = 350
            HnsLayout.BALL_DUSK -> if (f.timeOfDay == HnsLayout.TIME_EVENING || f.timeOfDay == HnsLayout.TIME_NIGHT || f.cave ||
                f.mapType == HnsLayout.MAP_TYPE_UNDERGROUND) mul = 350
            HnsLayout.BALL_TIMER -> { mul = 100 + f.turn * 10; if (mul > 4 * div) mul = 4 * div }
            HnsLayout.BALL_QUICK -> if (f.turn == 0) mul = 400
            HnsLayout.BALL_REPEAT -> if (f.caughtBefore) mul = 350
            HnsLayout.BALL_LEVEL -> {
                when {
                    f.playerLevel >= 4 * t.level -> mul = 800
                    f.playerLevel > 2 * t.level -> mul = 400
                    f.playerLevel > t.level -> mul = 200
                }
                if (mul < 400 && t.isType(HnsLayout.TYPE_NORMAL, HnsLayout.TYPE_FLYING, HnsLayout.TYPE_ICE)) mul = 400
            }
            HnsLayout.BALL_LURE -> {
                if (f.fishing) mul = 300
                if (mul < 400 && t.isType(HnsLayout.TYPE_WATER, HnsLayout.TYPE_DRAGON)) mul = 400
            }
            HnsLayout.BALL_MOON -> {
                if (t.evolvesByMoonStone) mul = 400
                if (mul < 400 && t.isType(HnsLayout.TYPE_DARK, HnsLayout.TYPE_GHOST, HnsLayout.TYPE_POISON)) mul = 400
            }
            HnsLayout.BALL_LOVE -> {
                if (t.species == f.playerSpecies && t.gender != f.playerGender &&
                    t.gender != HnsLayout.MON_GENDERLESS && f.playerGender != HnsLayout.MON_GENDERLESS) mul = 800
                if (mul < 400 && t.isType(HnsLayout.TYPE_FAIRY, HnsLayout.TYPE_PSYCHIC)) mul = 400
            }
            HnsLayout.BALL_FAST -> {
                if (t.baseSpeed >= 100) mul = 400
                if (mul < 400 && t.isType(HnsLayout.TYPE_ELECTRIC, HnsLayout.TYPE_FIGHTING, HnsLayout.TYPE_FIRE)) mul = 400
            }
            HnsLayout.BALL_HEAVY -> {
                // B_HEAVY_BALL_MODIFIER is GEN_3: the last branch's brackets.
                flat = when { t.weight < 1024 -> -20; t.weight < 2048 -> 0; t.weight < 3072 -> 20; t.weight < 4096 -> 30; else -> 40 }
                if (t.isType(HnsLayout.TYPE_ROCK, HnsLayout.TYPE_GROUND, HnsLayout.TYPE_STEEL)) { mul = 400; if (flat < 0) flat = 0 }
            }
            HnsLayout.BALL_DREAM -> if (t.status1 and HnsLayout.STATUS1_SLEEP.toLong() != 0L || t.ability == HnsLayout.ABILITY_COMATOSE) mul = 400
            HnsLayout.BALL_SAFARI -> mul = 150
            HnsLayout.BALL_SPORT -> mul = 150
            HnsLayout.BALL_BEAST -> { mul = 410; div = 4096 }
            HnsLayout.BALL_GS -> if (t.species == HnsLayout.SPECIES_CELEBI) sure = true else mul = 2550
        }
        return BallData(mul, div, flat, sure)
    }

    private val BADGE_LEVEL = intArrayOf(25, 30, 35, 40, 45, 50, 55, 60, 100)
    private const val NUM_BADGES_CAPPED = 8
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
        // Heart & Soul: the Safari Zone's flattened factor is for Safari Balls only.
        var catchRate = if (f.safari && ball == HnsLayout.BALL_SAFARI) f.safariCatchFactor * 1275 / 100 else t.catchRate
        catchRate += b.flatBonus
        if (catchRate <= 0) catchRate = 1
        odds = (odds * catchRate and U32) / (t.maxHp * 3L)
        odds = (odds * b.multiplier and U32) / b.divider
        val badges = minOf(f.badges, NUM_BADGES_CAPPED)
        if (badges < NUM_BADGES_CAPPED) {
            var i = badges
            while (i < BADGE_LEVEL.size && t.level > BADGE_LEVEL[i]) { odds = odds * 4 / 5; i++ }
        }
        if (t.level <= 13) odds = odds * (36 - t.level * 2) / 10
        if (t.status1 and INCAPACITATED != 0L) odds = odds * 25 / 10
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
