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
 * Never on a tracked game: rewinding an IronMON run is the one thing a
 * viewer would call out, the same rule as cheats (CheatStore.allowed).
 */
class RewindBuffer(val capacity: Int) {
    private val states = ArrayDeque<ByteArray>()
    val size: Int get() = states.size
    val bytes: Long get() = states.sumOf { it.size.toLong() }

    fun push(state: ByteArray) {
        if (state.isEmpty()) return
        states.addLast(state)
        while (states.size > capacity) states.removeFirst()
    }

    /** The newest state, removed. Null when there is nothing to go back to. */
    fun pop(): ByteArray? = states.removeLastOrNull()

    fun clear() = states.clear()

    companion object {
        fun allowed(session: GameSession): Boolean = !session.tracked

        /** (capacity, interval ms) per console. */
        fun policy(p: Platform): Pair<Int, Long> = when (p) {
            Platform.GBA, Platform.GBC -> 60 to 500L
            Platform.NDS -> 6 to 2000L
        }

        fun forPlatform(p: Platform) = RewindBuffer(policy(p).first)
    }
}
