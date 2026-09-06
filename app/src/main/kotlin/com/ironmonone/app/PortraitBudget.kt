package com.ironmonone.app

/**
 * How the portrait Play screen divides its height between the game, the
 * tracker and the pad.
 *
 * Three things must be on screen at once and their natural sizes do not fit
 * every phone: the game is a 3:2 box as wide as the screen, the pad is 262dp
 * of buttons, and the tracker used to take "whatever was left" - which on a
 * phone shorter than the emulator was nothing, and the pad's own bottom row
 * fell off the screen with it (Blake's phone, 2026-09-05; reproduced at
 * 1080x1700). Something has to yield, in a fixed order:
 *
 *  1. The PAD scales down, to a floor of [PAD_FLOOR]. Buttons at 55% are
 *     still 31dp; below that they stop being buttons.
 *  2. If the tracker still cannot have [TRACKER_MIN_DP], the GAME shrinks,
 *     centred, to a floor of [GAME_MIN_FRACTION] of the width.
 *
 * A Fold's cover screen is the opposite shape, tall and narrow, and the same
 * arithmetic gives it a small game box, a large tracker and a full-size pad.
 *
 * Pure, so it is unit-tested at the three shapes above instead of trusted.
 */
object PortraitBudget {
    /** The compact pad: one band three buttons tall (see Pad). Was 262 as four rows. */
    const val PAD_NATURAL_DP = 192f
    /**
     * The band's natural WIDTH: D-pad (3 cells, 180), the middle column (two
     * 36dp shoulders side by side, 80), the A/B diagonal (90), plus margins
     * (16). A screen narrower than this - a Fold's cover screen - scales the
     * pad down by width instead of clipping the middle column.
     */
    const val PAD_WIDTH_DP = 366f
    const val PAD_FLOOR = 0.55f
    const val TRACKER_MIN_DP = 140f
    const val MENU_DP = 96f
    const val FRAME_DP = 6f
    const val GAME_MIN_FRACTION = 0.5f

    data class Plan(val padScale: Float, val gameFraction: Float)

    /** Height of the game box, in px, at a given width fraction. */
    fun gamePx(widthPx: Int, fraction: Float, density: Float): Float =
        widthPx * fraction * 2f / 3f + FRAME_DP * density

    fun plan(widthPx: Int, heightPx: Int, density: Float, menuOpen: Boolean): Plan {
        // Not measured yet: the designed sizes, so the first frame is not a
        // shrunken flash.
        if (widthPx <= 0 || heightPx <= 0) return Plan(1f, 1f)
        val menu = if (menuOpen) MENU_DP * density else 0f
        val trackerMin = TRACKER_MIN_DP * density
        val padNatural = PAD_NATURAL_DP * density

        // 1. The pad yields first - to the height, and to a narrow screen.
        val byHeight = (heightPx - gamePx(widthPx, 1f, density) - menu - trackerMin) / padNatural
        val byWidth = widthPx / (PAD_WIDTH_DP * density)
        val padScale = minOf(byHeight, byWidth).coerceIn(PAD_FLOOR, 1f)

        // 2. Then, only if the tracker still cannot have its minimum, the game.
        val roomForGame = heightPx - menu - trackerMin - padNatural * padScale
        val gameFraction =
            if (roomForGame >= gamePx(widthPx, 1f, density)) 1f
            else (((roomForGame - FRAME_DP * density) * 1.5f) / widthPx)
                .coerceIn(GAME_MIN_FRACTION, 1f)
        return Plan(padScale, gameFraction)
    }
}
