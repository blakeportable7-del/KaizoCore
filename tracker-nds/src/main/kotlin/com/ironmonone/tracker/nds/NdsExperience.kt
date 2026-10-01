package com.ironmonone.tracker.nds

/**
 * The DS tracker's experience bar fraction, MiscUtils.calculateExperiencePercent
 * (MiscUtils.lua:355-375): every Pokemon on the Fluctuating curve, as the
 * reference computes it, in floating point with the floor at the end (so its
 * under-15 branch divides (level + 1) by 3 without flooring, unlike the game).
 */
object NdsExperience {
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
