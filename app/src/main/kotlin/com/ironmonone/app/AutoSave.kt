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
 *
 * Snapshots are ordered and paced on [clock], a clock that only goes forward (rc32 audit P2 #10). They were ordered
 * on the phone's wall clock, which the game's own clock follows, so players move it: set back, it stopped every
 * periodic and battle write for as long as it moved, dropped the snapshot taken as the game was left as "older than
 * the one on disk", and left the older moment's mark, which the next open took for where the player stopped.
 */
class AutoSave(
    private val state: File,
    private val stampFile: File,
    /** Milliseconds that only go forward: elapsedRealtime on the phone, a test's own clock in the tests. */
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {

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
        fun snapshot(view: com.swordfish.libretrodroid.GLRetroView, timeoutMs: Long = 2000, quiet: Boolean = false): ByteArray? {
            var took = 0L
            val bytes = betweenFrames(view, timeoutMs) {
                val t0 = System.nanoTime()
                runCatching { view.serializeState(useEmulationThread = false) }.getOrNull().also { took = System.nanoTime() - t0 }
            } ?: return null
            // What the emulation thread gave up for it: one frame's worth or less, or it would stutter.
            if (!quiet) android.util.Log.i("KaizoCore", "auto-save: ${bytes.size shr 10} KB taken in ${took / 1_000_000} ms on the emulation thread")
            return bytes
        }

        /**
         * The state as a battle begins, for "Retry the battle" and the auto slot, taken from a background thread
         * (rc32 audit P2 #52): the main thread used to wait on the core for it, a whole DS state at every battle.
         */
        suspend fun battleStart(view: com.swordfish.libretrodroid.GLRetroView?): ByteArray? =
            view?.let { v -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { snapshot(v) } }

        /**
         * [read] run on the core's own thread between two frames; waits [timeoutMs] at most, and null when it does not
         * answer or gives nothing. Never on the main thread.
         */
        private fun betweenFrames(view: com.swordfish.libretrodroid.GLRetroView, timeoutMs: Long, read: () -> ByteArray?): ByteArray? {
            val done = CountDownLatch(1)
            val out = AtomicReference<ByteArray?>()
            view.queueEvent {
                out.set(runCatching(read).getOrNull())
                done.countDown()
            }
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
            return out.get()?.takeIf { it.isNotEmpty() }
        }

        /** The battery save as it is in the core, between frames, and when it was read (System.nanoTime). */
        private fun sramNow(view: com.swordfish.libretrodroid.GLRetroView): Pair<ByteArray, Long>? {
            var at = 0L
            val bytes = betweenFrames(view, 2000) { view.serializeSRAM(useEmulationThread = false).also { at = System.nanoTime() } }
            return bytes?.let { it to at }
        }
    }

    /** When the newest snapshot handed in was taken, on [clock]. */
    @Volatile var lastCapture = 0L
        private set
    /** When the snapshot on disk was taken, on [clock]; the writer thread's own. */
    private var lastWritten = 0L

    private val leftMark = File(state.parentFile, state.nameWithoutExtension + ".left")

    /** Due for its every-three-minutes write. */
    fun due(): Boolean = clock() - lastCapture >= EVERY_MS

    /**
     * A battle began: worth a write unless one was made moments ago. [wallNow] is the Play screen's wall-clock time,
     * which pacing no longer reads (rc32 audit P2 #10); the call keeps its shape.
     */
    fun battleDue(@Suppress("UNUSED_PARAMETER") wallNow: Long = 0L): Boolean = clock() - lastCapture >= BATTLE_GAP_MS

    /** Counts the core coming up as the last write, so the first periodic one is [EVERY_MS] into play. */
    fun started() {
        val now = clock()
        if (now > lastCapture) lastCapture = now
    }

    /**
     * Queues [bytes], taken just now from the run stamped [stamp] (PrepStore.stateStamp at that moment), for the
     * slot. Returns at once. [wallTime] is when, on the phone's clock, which nothing orders by any more: the snapshot
     * is placed on [clock] as it is handed in (rc32 audit P2 #10).
     */
    fun save(bytes: ByteArray, @Suppress("UNUSED_PARAMETER") wallTime: Long, stamp: String, leaving: Boolean = false) =
        queue(bytes, clock(), stamp, leaving)

    /** [save] for a snapshot taken at [capturedAt] on [clock]: keepFresh's, asked for before its snapshot is taken. */
    private fun queue(bytes: ByteArray, capturedAt: Long, stamp: String, leaving: Boolean) {
        if (bytes.isEmpty() || !PrepStore.stampKnown(stamp)) return
        if (capturedAt > lastCapture) lastCapture = capturedAt
        // The writer's answer was dropped here, so a full phone stopped auto-saving in silence (rc33 audit P0-8).
        writer.execute { write(bytes, capturedAt, stamp, leaving)?.let { SaveTrouble.report(SaveTrouble.AUTO, it) } }
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
     *
     * [sram] is the battery save's file on a console whose core leaves saves to the app (Game Boy, GBA), else null.
     * Every tick of play it is read from the core and, when the game has saved since, written (rc32 audit P2 #51):
     * it reached the disk only on a pause, leaving Play and NEW RUN, so a crash or a dead battery lost every in-game
     * save since the last pause, and the crash resume then played on with the older one.
     */
    suspend fun keepFresh(view: com.swordfish.libretrodroid.GLRetroView, playing: () -> Boolean, stamp: () -> String, sram: File? = null) {
        started()
        val watch = sram?.let { f -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SramWatch(runCatching { f.takeIf { it.isFile }?.readBytes() }.getOrNull()) } }
        while (true) {
            kotlinx.coroutines.delay(10_000)
            if (!playing()) continue
            if (sram != null && watch != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { flushSram(view, sram, watch) }
            if (!due()) continue
            // On [clock], before the snapshot is asked for: a battle's snapshot handed in meanwhile is newer.
            val now = clock()
            val before = stamp()
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { snapshot(view) } ?: continue
            if (!playing() || stamp() != before) continue
            queue(bytes, now, before, leaving = false)
        }
    }

    /** One tick's battery save: read between frames, written when [watch] says the game has saved since. Off the main thread. */
    private fun flushSram(view: com.swordfish.libretrodroid.GLRetroView, f: File, watch: SramWatch) {
        val (bytes, at) = sramNow(view) ?: return
        if (!watch.offer(bytes)) return
        val failed = StateSlots.writeSram(f, bytes, at)
        if (failed == null) watch.wrote(bytes) else SaveTrouble.report(SaveTrouble.BATTERY, failed)
    }
}

/**
 * Whether a read of the battery save goes to disk (rc32 audit P2 #51): when it differs from what was last written and
 * has held still since the read before. A save the game is part way through writing is never caught half done (a
 * Gen 1 save has no second copy to fall back on); a tick later it is written whole.
 */
internal class SramWatch(onDisk: ByteArray?) {
    private var written: Long? = onDisk?.let(::crc)
    private var seen: Long? = null

    /** A read; true when it should be written now. */
    fun offer(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val c = crc(bytes)
        val steady = c == seen
        seen = c
        return steady && c != written
    }

    /** [bytes] reached the disk. */
    fun wrote(bytes: ByteArray) { written = crc(bytes) }

    private fun crc(b: ByteArray): Long = java.util.zip.CRC32().apply { update(b) }.value
}
