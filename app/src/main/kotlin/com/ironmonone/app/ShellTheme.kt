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
    // The modern dark look (Blake, 2026-09-27: "A, modern dark"). Cards are a
    // step lighter than the page, so every "OnPaper" colour below is a LIGHT
    // ink now. The names still say the ground, which is the rule of this file.
    // Ratios measured against that ground.

    // ---- Grounds -----------------------------------------------------------
    /** The app page. */
    val night = Color(0xFF121316)
    /** Card ground. */
    val paper = Color(0xFF1C1D22)
    /** A control on a card: buttons, chips, steppers. */
    val raised = Color(0xFF2A2C33)
    /** The same, pressed. */
    val raisedPressed = Color(0xFF363841)

    // ---- On paper ----------------------------------------------------------
    /** Body text on a card. 14.4:1 */
    val inkOnPaper = Color(0xFFECEDEE)
    /** De-emphasised text on a card. 6.6:1 */
    val hintOnPaper = Color(0xFFA0A3AD)
    /** Links on a card: ink plus an underline, so hue is never the only cue. */
    val linkOnPaper = Color(0xFFECEDEE)
    val linkDecoration = TextDecoration.Underline

    // ---- On night ----------------------------------------------------------
    /** Body text on the page. 15.6:1 */
    val textOnNight = Color(0xFFECEDEE)
    /** De-emphasised text on the page. 7.1:1 */
    val hintOnNight = Color(0xFFA0A3AD)
    /** The accent as TEXT on either dark ground. 7.9:1 */
    val accentOnNight = Color(0xFFFF8589)

    // ---- Accent fill -------------------------------------------------------
    /** The one filled colour: the main action on a screen, and selection. */
    val accent = Color(0xFFCF3A3F)
    val accentPressed = Color(0xFFB23035)
    /** Text on [accent]. 5.8:1 */
    val onAccent = Color(0xFFFFFFFF)

    // ---- Structure ---------------------------------------------------------
    val frame = Color(0xFF2E3036)
    val hairline = Color(0xFF34363C)

    // ---- Status ------------------------------------------------------------
    val dangerOnPaper = Color(0xFFFF6B6B)   // 5.8:1 on a card
    val dangerOnNight = Color(0xFFFF6B6B)
    val goodOnPaper = Color(0xFF4CC38A)     // 7.5:1 on a card
    val goodOnNight = Color(0xFF4CC38A)

    // ---- Metrics -----------------------------------------------------------
    /** Minimum interactive target. */
    val touchTarget = 48.dp
    /** Card inner padding. */
    val cardPadding = 16.dp
    /** Base spacing grid. */
    val gap = 4.dp
    /** Frame stroke on cards and buttons. */
    val stroke = 1.dp
    val cardRadius = 16.dp
    val controlRadius = 12.dp

    /**
     * Button labels are written in capitals all over the app ("PLAY IT NOW"),
     * from the pixel-font days. In the sans face capitals shout, so a label
     * with no lower case is shown in sentence case here, once, instead of
     * rewriting ninety call sites. Short codes stay as they are.
     */
    fun label(s: String): String {
        if (Regex("[0-9.]+X").matches(s)) return s.lowercase()   // speed: 1x, 2x
        if (s.any { it.isLowerCase() } || s.length <= 2) return s
        val words = s.split(' ')
        return words.mapIndexed { i, w ->
            val key = w.trimEnd(',', '.', '!', '?', ':')
            val tail = w.substring(key.length)
            (KEEP[key] ?: when {
                key in CODES -> key
                i == 0 -> key.lowercase().replaceFirstChar { it.uppercase() }
                else -> key.lowercase()
            }) + tail
        }.joinToString(" ")
    }

    private val CODES = setOf(
        "ROM", "ROMS", "IPS", "BPS", "UPS", "DS", "NDS", "GBA", "GBC", "GB", "HP", "PP", "PC",
        "OK", "AI", "RA", "UPR", "ZX", "IV", "IVS", "EV", "EVS", "TM", "HM", "ID", "USB", "X",
    )
    private val KEEP = mapOf(
        "BLAKE" to "Blake", "POKEMON" to "Pokémon", "NAT.DEX" to "Nat. Dex", "SUPERNDS" to "SuperNDS",
        "DRASTIC" to "DraStic", "MELONDS" to "melonDS",
    )
}
