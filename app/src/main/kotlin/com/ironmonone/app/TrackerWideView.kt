package com.ironmonone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The floating window's wide view (Blake, 2026-10-04): a window about two and a half times wider than tall lays the
 * tracker out in columns instead of one stack. Your Pokemon's card is the wide card (PcMonCard: the info, the stats and
 * the heals, then the moves beside them), and to its right the opponent's card in a battle, stacked and compact, then
 * the carousel. It is the same cards, drawn by the same code, so every Tracker Setup switch shows or hides the same
 * things here as in the stack. A taller window goes back to the stack by itself.
 *
 * It is never smaller than the wide card's least unit ([WIDE_MIN_RPX]): a window too short or too narrow for that keeps
 * the stack, so the words stay readable.
 */
object TrackerWideView {
    /** Width over height, the tracker's own room under the title bar, from which the window goes wide. */
    const val RATIO = 2.5f
    /** The units across: the wide card, then a stacked card's width for the column at the right. */
    const val UNITS = PcRef.WIDE_WIDTH + PcRef.WIDTH
    /** The most a unit is drawn at, in dp, so a very wide window does not grow the words past the window's height. */
    const val MAX_RPX = 2.2f

    /** Whether a window [w] by [h] dp of tracker room is drawn wide. */
    fun applies(w: Float, h: Float): Boolean = h > 0f && w / h >= RATIO && w / UNITS >= WIDE_MIN_RPX.value

    /** The unit the wide view draws at, for a canvas [width] wide. */
    fun unit(width: Dp): Dp = (width.value / UNITS).coerceIn(WIDE_MIN_RPX.value, MAX_RPX).dp
}

/** True inside a floating window drawn wide (TrackerWideView); everywhere else, the stack. */
val LocalTrackerWideView = compositionLocalOf { false }

/**
 * Your Pokemon's card ([left]) and what follows it ([right]: the opponent's card and the carousel). In the stack they
 * are emitted one after the other, exactly as before; in the wide view they sit side by side, the left as the wide card.
 */
@Composable
fun WideCards(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    // The Tracker HUD (TrackerHud.kt): your card goes to its own panel, the rest stays here.
    LocalHudPortal.current?.let { portal ->
        androidx.compose.runtime.SideEffect { portal.left = left }
        right()
        return
    }
    if (!LocalTrackerWideView.current) { left(); right(); return }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(PcRef.WIDE_WIDTH.rp)) {
            CompositionLocalProvider(LocalTrackerWide provides true) { left() }
        }
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalTrackerWide provides false) { right() }
        }
    }
}
