package com.ironmonone.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One NEW RUN at a time (rc33 audit P0-4). Play's newRun stops the core, makes the next run on a background thread and
 * boots it; a second confirm while that was under way (the File menu, the NEW chip, A+B+Start and the game over's New
 * game all open the same confirm) ran a second install: the attempt counted twice, the first new run filed as ended,
 * and a view booted over a core that was never torn down. newRun claims this first and gives it back when it ends,
 * however it ends.
 */
object NewRunGuard {
    private val busy = AtomicBoolean(false)

    const val BUSY = "A new run is already being made. It boots by itself in a moment."

    /** True when the caller may start a new run; false while one is being made. */
    fun claim(): Boolean = busy.compareAndSet(false, true)

    fun release() { busy.set(false) }

    val inProgress: Boolean get() = busy.get()

    /** Returns once no new run is being made. */
    suspend fun awaitDone() { while (inProgress) kotlinx.coroutines.delay(300) }
}
