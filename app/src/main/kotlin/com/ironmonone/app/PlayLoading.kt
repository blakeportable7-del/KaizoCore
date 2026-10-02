package com.ironmonone.app

/**
 * Whether Play's game view is still loading its game (rc33 audit P0-11). GLRetroView loads inside onSurfaceCreated
 * on the GL thread, and a DS game takes five seconds and more (White 2 measured past 5 s, 2026-09-28). Leaving Play
 * then tears the view down, and GLSurfaceView's teardown waits on the main thread for the GL thread: past 5 s with a
 * touch pending, an ANR. The tabs hold Play until the load ends, for at most [MAX_HOLD_MS], so a flag that somehow
 * never cleared can never trap the player there.
 */
object PlayLoading {
    const val MAX_HOLD_MS = 20_000L
    const val WAIT = "The game is still starting. One moment, then switch."

    /** When the game view began loading (monotonic ms), or 0 while none is loading. */
    @Volatile private var since = 0L

    private fun nowMs() = System.nanoTime() / 1_000_000

    /** Play's game view, each time it is composed: still loading, or up. */
    fun note(loading: Boolean, now: Long = nowMs()) {
        since = when {
            !loading -> 0L
            since == 0L -> now
            else -> since
        }
    }

    /** Play is gone: nothing of it is loading. */
    fun clear() { since = 0L }

    /** True while leaving Play would tear down a view whose game is still loading. */
    fun holds(now: Long = nowMs()): Boolean = since != 0L && now - since < MAX_HOLD_MS
}
