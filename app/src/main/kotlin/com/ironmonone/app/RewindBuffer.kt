package com.ironmonone.app

import com.ironmonone.core.Platform

/**
 * The rewind history: the last N save states, newest last. The Play screen
 * pushes one every [intervalMs] while the game runs at 1x, and pops them
 * back into the core while REWIND is held.
 *
 * Sized per console from what a state costs: a GBA or Game Boy state is
 * about half a megabyte, so sixty of them (thirty seconds) is ~30 MB. A DS
 * state is tens of megabytes, so the DS gets a short history at a long
 * interval rather than none at all.
 *
 * Never in a Kaizo IronMON run or a Nuzlocke: rewinding one is the thing a
 * viewer would call out, the same rule as cheats (CheatStore.allowed). Until
 * 2026-09-30 it was every game the tracker reads, so a plain Emerald from the
 * library, with no run and no rules, had no rewind at all.
 */
class RewindBuffer(val capacity: Int, val maxBytes: Long = Long.MAX_VALUE) {
    private val states = ArrayDeque<ByteArray>()
    val size: Int get() = states.size
    val bytes: Long get() = states.sumOf { it.size.toLong() }

    fun push(state: ByteArray) {
        if (state.isEmpty()) return
        states.addLast(state)
        while (states.size > capacity) states.removeFirst()
        // Capped by size as well (rc32 audit P2 #52): six DS states sat on the Java heap beside the restore points.
        while (states.size > 1 && bytes > maxBytes) states.removeFirst()
    }

    /**
     * Records a state every interval while [canRecord] (the game at 1x, nothing rewinding, Play in front), until
     * cancelled. Each is taken between frames on the core's own thread from a background one, and kept here on the
     * caller's (rc32 audit P2 #52): it was serialized on the main thread, a DS state every two seconds, and each one
     * held up the controls and the tracker.
     */
    suspend fun record(view: com.swordfish.libretrodroid.GLRetroView, intervalMs: Long, canRecord: () -> Boolean) {
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            if (!canRecord()) continue
            val st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AutoSave.snapshot(view, quiet = true) } ?: continue
            if (canRecord()) push(st)
        }
    }

    /** The newest state, removed. Null when there is nothing to go back to. */
    fun pop(): ByteArray? = states.removeLastOrNull()

    fun clear() = states.clear()

    companion object {
        fun allowed(session: GameSession, nuzlocke: Boolean = NuzlockeTracking.inPlay()): Boolean = !session.isRun && !nuzlocke

        /** (capacity, interval ms) per console. */
        fun policy(p: Platform): Pair<Int, Long> = when (p) {
            Platform.GBA, Platform.GBC -> 60 to 500L
            Platform.NDS -> 6 to 2000L
        }

        /** The most a console's history may hold: a DS state runs to megabytes, a GBA one is about half of one. */
        fun maxBytes(p: Platform): Long = if (p == Platform.NDS) 32L * 1024 * 1024 else 64L * 1024 * 1024

        fun forPlatform(p: Platform) = RewindBuffer(policy(p).first, maxBytes(p))
    }
}
