package com.ironmonone.app

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much height the tracker has where it is drawn: the docked column, the floating window, the space above the
 * DS bottom screen. Null where nobody measured it, which keeps each caller's own default.
 */
val LocalTrackerRoom = compositionLocalOf<Dp?> { null }

/** True inside the floating window, whose title bar reads the attempt: the panels then leave it out (2026-10-02). */
val LocalAttemptInTitle = compositionLocalOf { false }

/**
 * Both cards at once, or one with SEE FOE and SEE MINE (Blake, 2026-10-02: where the tracker is short, "a small
 * button, see foe and click that and you can see the opponent", "or you can have it set to auto switch to foe when
 * in battle and then after the battle it switches back"). The swap and its auto swap are the panels' own, as in
 * portrait; this only decides when there is room to skip them.
 */
object TrackerRoom {
    /** Your card, the opponent's and the strip under them: Black 2's docked column held all three at 393dp. */
    const val BOTH_DP = 340

    fun stackBoth(room: Dp?): Boolean = room == null || room >= BOTH_DP.dp
}
