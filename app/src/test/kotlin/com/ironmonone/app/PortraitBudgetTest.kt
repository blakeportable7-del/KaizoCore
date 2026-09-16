package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three screen shapes that matter, plus the invariants that must hold on
 * every one of them: everything fits, the pad never goes below its floor, and
 * the tracker gets its minimum unless the game is already at ITS floor.
 *
 * Since 2026-09-15 the GAME is what yields to a short screen, not the pad, so
 * the two cases below that used to read "the pad shrank" now read "the buttons
 * stayed and the game gave up the pixels".
 *
 * Heights are the PLAY AREA (between the top bar and the tab bar), which is
 * what the screen measures, not the display.
 */
class PortraitBudgetTest {

    private val d = 2.75f   // 440 dpi, the emulator and most flagships

    private fun check(w: Int, h: Int, menu: Boolean = false): PortraitBudget.Plan {
        val p = PortraitBudget.plan(w, h, d, menu)
        val game = PortraitBudget.gamePx(w, p.gameFraction, d)
        val pad = PortraitBudget.PAD_NATURAL_DP * d * p.padScale
        val menuPx = if (menu) PortraitBudget.MENU_DP * d else 0f
        val tracker = h - game - pad - menuPx
        assertTrue(p.padScale >= PortraitBudget.PAD_FLOOR - 1e-4f, "pad below floor: $p")
        assertTrue(p.padScale <= 1f && p.gameFraction <= 1f, "over natural size: $p")
        val trackerMin = PortraitBudget.TRACKER_MIN_DP * d
        assertTrue(
            tracker >= trackerMin - 1f || p.gameFraction <= PortraitBudget.GAME_MIN_FRACTION + 1e-4f,
            "${w}x$h: tracker got ${tracker.toInt()}px, min ${trackerMin.toInt()}, plan $p",
        )
        return p
    }

    @Test
    fun `a normal phone shrinks nothing at all`() {
        val p = check(1080, 1788)          // 1080x2340 minus chrome
        assertEquals(PortraitBudget.Plan(1f, 1f), p)
    }

    @Test
    fun `a tall narrow cover screen keeps the game and scales the pad only to its width`() {
        val p = check(904, 1760)           // Fold cover, minus chrome
        assertEquals(1f, p.gameFraction)
        // 904px / (366dp * 2.75) = 0.90: the band fits the width instead of
        // clipping the middle column, and it is nowhere near the floor.
        assertTrue(p.padScale > 0.85f && p.padScale < 1f, "$p")
    }

    @Test
    fun `Blake's phone keeps buttons at full size`() {
        // Play area estimated from his portrait screenshot: ~1540px at 2.625.
        // Under the old rule this came out at padScale 0.87, a 48dp button,
        // which is the screenshot he called unplayable.
        val p = PortraitBudget.plan(1080, 1540, 2.625f, false)
        assertEquals(1f, p.padScale, "buttons must not shrink on his phone: $p")
        // The game pays for it, and barely: about a tenth of its width.
        assertTrue(p.gameFraction > 0.85f, "game gave up too much: $p")
    }

    @Test
    fun `a short phone keeps the buttons and shrinks the game to its floor`() {
        val p = check(1080, 1280)          // 1080x1700 minus chrome
        assertEquals(1f, p.padScale, "the pad no longer yields to height: $p")
        assertTrue(p.gameFraction < 1f, "game did not yield: $p")
        assertEquals(PortraitBudget.GAME_MIN_FRACTION, p.gameFraction)
    }

    @Test
    fun `the open FILE menu is charged to the budget`() {
        // It comes off the GAME now, not the pad, so assert on the piece that
        // actually pays: a test on padScale here would pass with 1f == 1f and
        // prove nothing at all.
        val closed = check(1080, 1500)
        val open = check(1080, 1500, menu = true)
        assertTrue(open.gameFraction < closed.gameFraction, "menu was free: $open vs $closed")
    }

    @Test
    fun `unmeasured is the designed layout, never a shrunken first frame`() {
        assertEquals(PortraitBudget.Plan(1f, 1f), PortraitBudget.plan(0, 0, d, false))
    }
}
