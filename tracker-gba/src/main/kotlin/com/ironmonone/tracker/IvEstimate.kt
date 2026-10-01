package com.ironmonone.tracker

/**
 * "Estimate Pokemon IV Potential" on the Gen 2 reference's Tracker Extras
 * screen: ExtrasScreen.displayJudgeMessage (screens/ExtrasScreen.lua:116-142)
 * over Utils.estimateIVs (Utils.lua:651-675), for your lead.
 *
 * estimateIVs sums the six stats, each times its nature multiplier, less the
 * level and 35; that times 50 over the level, less the species' BST, over 96
 * is the fraction, held between 0 and 1. A Gen 2 Pokemon has no nature (the
 * reference sets it to 0, whose multiplier is 1 for every stat). The judge
 * takes fraction * 186 through Bulbapedia's Stats judge ranges as the
 * reference writes them, integer ranges on a fractional number, so a value
 * between 150 and 151, or 120 and 121, reads "Decent.".
 *
 * The Gen 1 reference has the same screen, but its Pokemon carry no "spd"
 * stat and estimateIVs fails on it, so there is none on Red, Blue and Yellow.
 */
object IvEstimate {
    const val UNAVAILABLE = "Estimate is unavailable."

    /** Utils.estimateIVs, 0 to 1. */
    fun fraction(maxHp: Int, atk: Int, def: Int, spAtk: Int, spDef: Int, spe: Int, level: Int, bst: Int): Double {
        if (level <= 0) return 0.0
        val sum = (maxHp + atk + def + spAtk + spDef + spe - level - 35).toDouble()
        val guess = sum * 50 / level - bst
        return (guess / 96).coerceIn(0.0, 1.0)
    }

    /** displayJudgeMessage's four words for [fraction]. */
    fun verdict(fraction: Double): String = judge(fraction * 186)

    /** The ranges on `ivEstimate = Utils.estimateIVs(leadPokemon) * 186`, as written there. */
    internal fun judge(e: Double): String = when {
        e >= 151 -> "Outstanding!!!"
        e >= 121 && e <= 150 -> "Quite impressive!!"
        e >= 91 && e <= 120 -> "Above average!"
        else -> "Decent."
    }
}
