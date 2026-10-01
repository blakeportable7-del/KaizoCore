package com.ironmonone.app

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The auto slot kept fresh while the game is played, so a crash, an OS kill
 * or a dead battery costs minutes, not the run.
 *
 * The auto slot used to be written only when the game was left (a pause,
 * leaving the tab). A native crash or a kill in the foreground lost all play
 * since the last pause, and a whole evening played without pausing had no
 * auto-save at all. Now it is also written every [EVERY_MS] of play, and when
 * a battle begins, from the snapshot "Retry the battle" takes then anyway, at
 * most every [BATTLE_GAP_MS].
 *
 * Every write of a slot goes through its one [AutoSave]: on one background
 * thread, one at a time. A snapshot older than the one already written is
 * dropped, so a pause's snapshot is never replaced by a periodic one queued
 * before it. The state is flushed to disk before it replaces the old one
 * (StateSlots.writeAtomic), and its stamp is written after it, never before,
 * so a state never carries another run's stamp; a state from a run whose
 * identity is not known (half way through NEW RUN) is not written at all.
 *
 * Since 2026-09-30 (UX audit P0-4): a snapshot taken as the
 * game is left or paused marks the slot (Slot.leftMark), and any later one
 * takes the mark away, so "the slot is exactly where the player stopped" can
 * be read off the disk: that is what lets the game open back there by itself
 * (CrashResume).
 */
class AutoSave(private val state: File, private val stampFile: File) {

    companion object {
        const val EVERY_MS = 3 * 60 * 1000L
        const val BATTLE_GAP_MS = 30 * 1000L

        private val writer = Executors.newSingleThreadExecutor { r ->
            Thread(r, "autosave").apply { isDaemon = true }
        }
        private val slots = HashMap<String, AutoSave>()

        /** Waits, [timeoutMs] at most, for the writes queued before it: a resume then reads the newest state. */
        fun drain(timeoutMs: Long = 3000) {
            runCatching { writer.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) }
        }

        /** The one writer for [session]'s auto slot. */
        fun of(filesDir: File, session: GameSession): AutoSave {
            val f = SessionPaths.slot(filesDir, session, StateSlots.AUTO)
            return synchronized(slots) {
                slots.getOrPut(f.absolutePath) { AutoSave(f, SessionPaths.slotStamp(filesDir, session, StateSlots.AUTO)) }
            }
        }

        /**
         * The core's state, taken between two frames on its own thread the way
         * a save state is, never in the middle of one. Waits [timeoutMs] at
         * most and then gives up: a core being torn down never answers, and
         * nothing may hang on it. Never on the main thread.
         */
        fun snapshot(view: com.swordfish.libretrodroid.GLRetroView, timeoutMs: Long = 2000): ByteArray? {
            val done = CountDownLatch(1)
            val out = AtomicReference<ByteArray?>()
            var took = 0L
            view.queueEvent {
                val t0 = System.nanoTime()
                out.set(runCatching { view.serializeState(useEmulationThread = false) }.getOrNull())
                took = System.nanoTime() - t0
                done.countDown()
            }
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
            val bytes = out.get()?.takeIf { it.isNotEmpty() } ?: return null
            // What the emulation thread gave up for it: one frame's worth or less, or it would stutter.
            android.util.Log.i("KaizoCore", "auto-save: ${bytes.size shr 10} KB taken in ${took / 1_000_000} ms on the emulation thread")
            return bytes
        }
    }

    /** When the newest snapshot handed in was taken. */
    @Volatile var lastCapture = 0L
        private set
    /** When the snapshot on disk was taken; the writer thread's own. */
    private var lastWritten = 0L

    private val leftMark = File(state.parentFile, state.nameWithoutExtension + ".left")

    /** Due for its every-three-minutes write. */
    fun due(now: Long): Boolean = now - lastCapture >= EVERY_MS

    /** A battle began: worth a write unless one was made moments ago. */
    fun battleDue(now: Long): Boolean = now - lastCapture >= BATTLE_GAP_MS

    /** Counts the core coming up as the last write, so the first periodic one is [EVERY_MS] into play. */
    fun started(now: Long) {
        if (now > lastCapture) lastCapture = now
    }

    /**
     * Queues [bytes], taken at [capturedAt] from the run stamped [stamp]
     * (PrepStore.stateStamp at that moment), for the slot. Returns at once.
     */
    fun save(bytes: ByteArray, capturedAt: Long, stamp: String, leaving: Boolean = false) {
        if (bytes.isEmpty() || !PrepStore.stampKnown(stamp)) return
        if (capturedAt > lastCapture) lastCapture = capturedAt
        writer.execute { write(bytes, capturedAt, stamp, leaving) }
    }

    /**
     * The write itself, on the writer thread. Returns why it failed, or null. [leaving] is a snapshot taken as the
     * game was left or paused: the slot is then exactly where the player stopped, and says so.
     */
    internal fun write(bytes: ByteArray, capturedAt: Long, stamp: String, leaving: Boolean = false): String? {
        if (capturedAt < lastWritten) return null
        // Before the state: a mark left standing over a newer state would call it the moment the game was left.
        runCatching { leftMark.delete() }
        StateSlots.writeAtomic(state, bytes)?.let { return it }
        // After the state: a state never carries another run's stamp. A kill
        // between the two leaves the new state with the old stamp, which the
        // load refuses unless it is this run's anyway.
        runCatching { stampFile.writeText(stamp) }
        if (leaving) runCatching { leftMark.writeText(capturedAt.toString()) }
        lastWritten = capturedAt
        return null
    }

    /**
     * Keeps the slot fresh while [playing]: every [EVERY_MS] of play a
     * snapshot from [view] (off the main thread), written unless the run
     * changed under it. Runs until cancelled.
     */
    suspend fun keepFresh(view: com.swordfish.libretrodroid.GLRetroView, playing: () -> Boolean, stamp: () -> String) {
        started(System.currentTimeMillis())
        while (true) {
            kotlinx.coroutines.delay(10_000)
            val now = System.currentTimeMillis()
            if (!due(now) || !playing()) continue
            val before = stamp()
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { snapshot(view) } ?: continue
            if (!playing() || stamp() != before) continue
            save(bytes, now, before)
        }
    }
}
