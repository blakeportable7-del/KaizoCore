package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the app SHELL — everything that is not the tracker panel.
 *
 * ## Why these are named by ground
 *
 * The shell paints on TWO grounds: the black page and the paper cards. The
 * Material scheme has one `onSurfaceVariant` and one `secondary`, so a call
 * site picked a colour without stating which ground it was on, and two of them
 * were measured wrong on 2026-08-31:
 *
 *   - About screen links: `secondary` (#FFFF00) on paper  ->  1.01:1
 *   - ROMs empty state:   `onSurfaceVariant` (#404040) on black -> 2.03:1
 *
 * 1.01:1 is luminance-invisible - the text survives on hue alone. Neither
 * colour is wrong in itself; #404040 is a fine ink ON PAPER (8.6:1) and yellow
 * is a fine accent ON BLACK (19.6:1). They were simply used on the wrong
 * ground, which no single-scheme token can prevent.
 *
 * So every token below says its ground in its name. If you cannot name the
 * ground, you do not yet know which colour you want.
 *
 * Contrast figures are WCAG ratios against that ground, measured, not assumed.
 * The tracker panel is EXEMPT: it reproduces the PC tracker's own palette and
 * is verified against the reference, not against this file.
 */
object Shell {

    // ---- Grounds -----------------------------------------------------------
    /** The app page. Everything not on a card sits on this. */
    val night = Color(0xFF000000)
    /** Card ground. */
    val paper = Color(0xFFF8F8F8)

    // ---- On paper ----------------------------------------------------------
    /** Body text on a card. 8.6:1 */
    val inkOnPaper = Color(0xFF404040)
    /** De-emphasised text on a card. 4.8:1 */
    val hintOnPaper = Color(0xFF6B6B6B)
    /**
     * Links on a card: INK PLUS AN UNDERLINE, never the yellow accent.
     * Yellow on paper measured 1.01:1 - see the class doc. Use with
     * [linkDecoration] so the link is distinguishable without relying on hue,
     * which also covers colour-blind readers.
     */
    val linkOnPaper = Color(0xFF404040)
    val linkDecoration = TextDecoration.Underline

    // ---- On night ----------------------------------------------------------
    /** Body text on the page. 21:1 */
    val textOnNight = Color(0xFFFFFFFF)
    /**
     * De-emphasised text on the page. 4.6:1 - the darkest grey that still
     * clears AA on black. Empty-state copy is guidance, not disabled UI, and
     * at 2:1 it read as the latter.
     */
    val hintOnNight = Color(0xFF9E9E9E)
    /** The accent. ONLY on dark grounds - 19.6:1 here, 1.01:1 on paper. */
    val accentOnNight = Color(0xFFFFEB00)

    // ---- Structure ---------------------------------------------------------
    val frame = Color(0xFF333333)
    val hairline = Color(0xFFAAAAAA)

    // ---- Status ------------------------------------------------------------
    val dangerOnPaper = Color(0xFFC62828)   // 5.9:1 on paper
    val dangerOnNight = Color(0xFFFF6B60)   // 6.6:1 on black
    val goodOnPaper = Color(0xFF1B6B2E)     // 6.3:1 on paper
    val goodOnNight = Color(0xFF5FD07A)     // 10.4:1 on black

    // ---- Metrics -----------------------------------------------------------
    /** Minimum interactive target. */
    val touchTarget = 48.dp
    /** Card inner padding. */
    val cardPadding = 16.dp
    /** Base spacing grid. */
    val gap = 4.dp
    /** Frame stroke on cards and buttons. */
    val stroke = 2.dp
}
