package com.ironmonone.app

/**
 * Which melonDS screen arrangement shows the most game in a landscape column.
 * Both screens are 256x192; "left-right" is 512x192 of content, "top-bottom"
 * is 256x384. The core letterboxes inside the column, so the arrangement
 * whose content scales larger is the one with bigger screens. A phone's
 * column (about 550x360dp beside the tracker) scales side by side larger;
 * a tablet's near-square column scales stacked larger. Hybrid layouts are
 * left to the editor: they grow the top screen by shrinking the bottom one.
 */
object NdsScreens {
    fun autoLayout(columnW: Int, columnH: Int): String {
        if (columnW <= 0 || columnH <= 0) return "left-right"
        val sideBySide = minOf(columnW / 512f, columnH / 192f)
        val stacked = minOf(columnW / 256f, columnH / 384f)
        return if (stacked > sideBySide) "top-bottom" else "left-right"
    }
}
