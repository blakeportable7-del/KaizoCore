package com.ironmonone.pcui

import androidx.compose.ui.graphics.Color
import com.ironmonone.app.Pc
import com.ironmonone.app.StatMarks

/**
 * besteon's Constants.STAT_STATES colours for a stat mark, everywhere the PC shows one (the opponent's card, Grade my
 * notes): "+" in the theme's Positive text, the minus sign in Negative text, "=" and blank in Default text. Read from
 * the theme showing, so a preset and the auto theme from slot one colour them the same way.
 */
object PcMarks {
    fun color(state: Int): Color = when (state) { 1 -> Pc.Positive; 2 -> Pc.Negative; else -> Pc.Text }

    /** The colour of a mark's glyph ([StatMarks.symbol]); [plain] for anything else. */
    fun colorOf(glyph: String, plain: Color = Pc.Text): Color = when (glyph.trim()) {
        StatMarks.symbol(1) -> color(1)
        StatMarks.symbol(2), "--" -> color(2)
        StatMarks.symbol(3) -> color(3)
        else -> plain
    }
}
