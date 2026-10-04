package com.ironmonone.app

import com.ironmonone.tracker.TrackerState

/**
 * How long the Game Boy Advance tracker waits before its next read (Blake, 2026-10-03: "the bottom of tracker bar that
 * says walking and weather, it's very slow to start displaying data").
 *
 * Out of battle it read every 700 ms. Two things wait on a read, and at that pace each waited too long:
 * - A map id is adopted on its second read (GbaTracker's debounce), so the route line ("Walking: 2/5 Seen Pokemon")
 *   came up to 1.4 s after a map change or a load.
 * - A battle's data counts as ready, as the reference counts it (Battle.lua:154-175), only when a read lands on
 *   gBattleMainFunc at the intro's party summary (a wild battle) or the opponent's send-out (a trainer's), else at the
 *   action menu. The reference looks every 10 frames (Program.lua:16, Battle.lua:122); a read every 700 ms mostly
 *   missed the intro, so the battle's lines (the area's Seen Pokemon, the weather) waited for the action menu, after
 *   every intro message, a weather ability's included, and longer the faster the game ran.
 * While the tracker says it is [TrackerState.settling], it reads again after [SETTLING_MS], six frames at normal speed.
 */
internal object TrackerPoll {
    const val SETTLING_MS = 100L
    const val BATTLE_MS = 250L
    const val IDLE_MS = 700L

    fun gbaWait(state: TrackerState?): Long = when {
        state?.settling == true -> SETTLING_MS
        state?.inBattle == true -> BATTLE_MS
        else -> IDLE_MS
    }
}
