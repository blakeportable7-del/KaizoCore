package com.ironmonone.tracker.nds

/**
 * The DS tracker's experience bar fraction, MiscUtils.calculateExperiencePercent
 * (MiscUtils.lua:355-375): every Pokemon on the Fluctuating curve, as the
 * reference computes it, in floating point with the floor at the end (so its
 * under-15 branch divides (level + 1) by 3 without flooring, unlike the game).
 *
 * The reference only reads IronMON ROMs, whose Ultimate rules put every Pokemon but the Legendaries on Fluctuating.
 * Here the bar shows in every mode, and Standard, IronMON Journey, Kaizo Doubles and a plain game keep other curves:
 * a Medium Fast Pokemon at level 30 with 27,000 EXP read a full bar. So the bar follows the species' own growth rate
 * from the run's sidecar, Fluctuating by the reference's formula (rc32 audit P3 #118).
 */
object NdsExperience {
    /** The growth rates as the ROM's personal data numbers them (Gen4Constants.bsGrowthCurveOffset, the randomizer's ExpCurve). */
    const val MEDIUM_FAST = 0
    const val ERRATIC = 1
    const val FLUCTUATING = 2
    const val MEDIUM_SLOW = 3
    const val FAST = 4
    const val SLOW = 5

    /**
     * The experience a Pokemon of [growthRate] has at [level], or null for a rate the games do not have. The games'
     * integer formulas: Medium Fast, Medium Slow, Fast and Slow are pokecrystal data/growth_rates.asm:15, :18-20, Erratic is
     * the Gen 3 one (the table at Complete-Fire-Red-Upgrade src/Tables/experience_tables.c:1297), and Fluctuating is
     * the reference's own [fluctuatingAt]. Medium Slow at level 1 would be -54; it is 0.
     */
    fun totalAt(growthRate: Int, level: Int): Long? {
        val n = level.toLong()
        val cube = n * n * n
        return when (growthRate) {
            MEDIUM_FAST -> cube
            ERRATIC -> when {
                n <= 50 -> (100 - n) * cube / 50
                n <= 68 -> (150 - n) * cube / 100
                n <= 98 -> ((1911 - 10 * n) / 3) * cube / 500
                else -> (160 - n) * cube / 100
            }
            FLUCTUATING -> fluctuatingAt(level)
            MEDIUM_SLOW -> maxOf(0L, 6 * cube / 5 - 15 * n * n + 100 * n - 140)
            FAST -> 4 * cube / 5
            SLOW -> 5 * cube / 4
            else -> null
        }
    }

    /**
     * How far through [level] a Pokemon of [growthRate] with [experience] is. Fluctuating is the reference's [fraction];
     * another rate is full from level 100, as the reference's is. Null for a rate the games do not have.
     */
    fun fraction(level: Int, experience: Long, growthRate: Int): Double? {
        if (growthRate == FLUCTUATING) return fraction(level, experience)
        if (experience == 0L) return 0.0
        if (level >= 100) return 1.0
        val here = totalAt(growthRate, level) ?: return null
        val next = totalAt(growthRate, level + 1) ?: return null
        return (experience - here).toDouble() / (next - here).toDouble()
    }
    /** calculateFluctuatingAtLevel: 0 from level 100 on. */
    fun fluctuatingAt(level: Int): Long {
        val cube = Math.pow(level.toDouble(), 3.0)
        return when {
            level < 15 -> Math.floor((cube * (((level + 1) / 3.0) + 24)) / 50).toLong()
            level < 36 -> Math.floor((cube * (level + 14)) / 50).toLong()
            level < 100 -> Math.floor((cube * ((level / 2.0) + 32)) / 50).toLong()
            else -> 0L
        }
    }

    /**
     * How far through [level] a Pokemon with [experience] is: 0 with none, and
     * whatever the reference's formula gives otherwise (below 0 at level 99,
     * where the next level's total reads 0; unbounded at 100). The bar clamps it.
     */
    fun fraction(level: Int, experience: Long): Double {
        if (experience == 0L) return 0.0
        val here = fluctuatingAt(level)
        val next = fluctuatingAt(level + 1)
        return (experience - here).toDouble() / (next - here).toDouble()
    }

    /**
     * DrawingUtils.drawExperienceBar's filled width (lua:522-536): floor(57 x
     * min(1, fraction)), nothing drawn at 0 or below.
     */
    fun barWidth(fraction: Double): Int = Math.floor(57 * minOf(1.0, fraction)).toInt().coerceAtLeast(0)
}
