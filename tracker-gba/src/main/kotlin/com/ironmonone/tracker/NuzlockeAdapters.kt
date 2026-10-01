package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.Snapshot

/**
 * Picks the adapter that turns a tracker state into the rules engine's snapshot (2026-09-30): the Game Boy trackers put
 * their reads in [NuzlockeReads.gb], the Gen 3 tracker in the reads themselves. The DS tracker has its own state and its
 * own adapter (NdsNuzlocke in tracker-nds).
 */
object NuzlockeAdapters {

    /** The engine's view of [state], or null when the state has no Nuzlocke reads or could not be read. */
    fun snapshot(state: TrackerState?): Snapshot? {
        val reads = state?.nuz ?: return null
        return if (reads.gb != null) Gen12Nuzlocke.snapshot(state) else Gen3Nuzlocke.snapshot(state)
    }
}
