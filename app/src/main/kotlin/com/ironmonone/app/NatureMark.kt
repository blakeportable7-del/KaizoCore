package com.ironmonone.app

/**
 * The reference's nature symbol (TrackerScreen.lua, drawStatsArea): on your own
 * Pokemon the stat its nature raises is drawn in Positive text with a "+", the
 * one it lowers in Negative text with a "-". Natures are numbered as the games
 * number them: raised stat is nature / 5, lowered is nature % 5, over
 * ATK, DEF, SPE, SPA, SPD; a neutral nature raises and lowers the same one and
 * marks nothing. Gen 1 and 2 have no natures and pass null.
 */
fun natureMark(nature: Int?, stat: String): Char? {
    if (nature == null || nature !in 0..24) return null
    val up = NATURE_STATS[nature / 5]; val down = NATURE_STATS[nature % 5]
    if (up == down) return null
    return when (stat) { up -> '+'; down -> '-'; else -> null }
}
internal val NATURE_STATS = listOf("ATK", "DEF", "SPE", "SPA", "SPD")
