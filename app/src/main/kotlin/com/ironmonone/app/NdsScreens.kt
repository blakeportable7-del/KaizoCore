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
    /**
     * The classic core's value for one of PadLayout.DS_LAYOUTS. The app keeps
     * the lowercase names it always stored; the core wants "Top/Bottom" and
     * friends. It has no rotated layouts, so those fall back to stacked.
     */
    fun classicName(layout: String): String = when (layout) {
        "top-bottom" -> "Top/Bottom"
        "bottom-top" -> "Bottom/Top"
        "left-right" -> "Left/Right"
        "right-left" -> "Right/Left"
        "hybrid-top" -> "Hybrid Top"
        "hybrid-bottom" -> "Hybrid Bottom"
        "top" -> "Top Only"
        "bottom" -> "Bottom Only"
        else -> "Top/Bottom"
    }

    /** The core takes 0..126 px between the screens. */
    fun classicGap(gap: Int): String = gap.coerceIn(0, 126).toString()

    fun autoLayout(columnW: Int, columnH: Int): String {
        if (columnW <= 0 || columnH <= 0) return "left-right"
        val sideBySide = minOf(columnW / 512f, columnH / 192f)
        val stacked = minOf(columnW / 256f, columnH / 384f)
        return if (stacked > sideBySide) "top-bottom" else "left-right"
    }
}
