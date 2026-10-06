package com.ironmonone.app

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Platform
import java.io.File

/**
 * Makes the next run (NextRun) in the background while this one is played.
 *
 * Asked once the run's core is up ([prepare]). It lets the boot, the DS
 * sprite read and the tracker's first reads have the phone for [SETTLE_MS],
 * then randomizes one run ahead for the game and settings in play, on its own
 * thread at background priority, so the emulator's threads win the CPU
 * whenever they want it. One at a time, never while the Run tab's job is
 * busy, and not at all when:
 * - "Get the next run ready in the background" is off in the tracker's setup.
 *   It is on by default and has a switch because it spends battery and, for
 *   a DS game, a few hundred MB of storage for as long as the run lasts;
 * - space is short ([roomFor]): the stage is the size of a run again;
 * - memory is short: the randomizer holds the game's tables in the Java heap
 *   while a DS core already holds the whole ROM in native memory.
 * No skip says anything: NEW RUN randomizes then, as it always did.
 *
 * NEW RUN asks [take]. A stage still being made for the same run is waited
 * for, at normal priority from then on, as it is closer to done than a new
 * randomize would be.
 */
object NextRunJob {
    private const val TAG = "KaizoCore"

    /** After the core is up, before a stage starts. */
    const val SETTLE_MS = 20_000L

    /**
     * Java heap a stage needs beyond what the app already uses: White 2, the
     * largest, peaked near 80 MB above the app's own on the emulator, and a
     * GBA game far below that. Twice that, so a stage never pushes the app
     * to its heap limit. A GBA or Game Boy stage holds its ROM twice before
     * any table (the engine's rom and originalRom), and a Nat. Dex build is
     * 32 MB: the flat 64 MB was those two copies with nothing to spare, so the
     * need grows with the ROM ([heapNeed], rc32 audit P3 #33).
     */
    private const val HEAP_DS = 160L shl 20
    private const val HEAP_OTHER = 64L shl 20
    /** Beyond a GBA ROM's two copies: the tables, the log and the rest of a stage. */
    private const val HEAP_MARGIN = 32L shl 20

    /** The heap a stage of a [platform] game needs, from the size of the ROM it randomizes. */
    internal fun heapNeed(platform: Platform, romBytes: Long): Long =
        if (platform == Platform.NDS) HEAP_DS else maxOf(HEAP_OTHER, 2 * romBytes + HEAP_MARGIN)

    /** Free space left after a stage: at least this much, and at least two runs' worth. */
    private const val SPACE_FLOOR = 512L shl 20

    /** The setup switch. Loaded at start ([load]); off deletes a staged run. */
    var ahead by mutableStateOf(true)
        private set

    private val lock = Any()
    private var worker: Thread? = null
    @Volatile private var workerTid = 0
    /** The recipe the worker is making a stage for, while it is. */
    @Volatile private var making: NextRun.Recipe? = null
    /** Bumped by [take] and [stop], so a worker not yet making a stage knows a new run overtook it. */
    @Volatile private var generation = 0

    fun load(store: PrepStore) {
        ahead = store.nextRunAhead()
    }

    fun setAhead(context: Context, on: Boolean) {
        val app = context.applicationContext
        ahead = on
        if (!on) stop()
        Thread {
            val store = PrepStore(app)
            store.setNextRunAhead(on)
            // After the stage in progress has stopped: frees its space.
            if (!on) runCatching { store.nextRun.clear() }
        }.start()
        if (on) prepare(app, settleMs = 0)
    }

    /** Stage the run after this one, unless one is waiting for it already. Returns at once. */
    fun prepare(context: Context, settleMs: Long = SETTLE_MS) {
        if (!ahead || Demo.mode != null) return
        val app = context.applicationContext
        synchronized(lock) {
            if (worker?.isAlive == true) return
            val gen = generation
            worker = Thread({ work(app, settleMs, gen) }, "next-run").apply { isDaemon = true; start() }
        }
    }

    /**
     * The staged run for [recipe], claimed, or null. A stage still being made
     * for it is waited for, raised to normal priority; any other work stops.
     * Blocking; never on the main thread.
     */
    fun take(store: PrepStore, recipe: NextRun.Recipe): NextRun.Staged? {
        generation++
        release(keep = recipe)
        val t0 = SystemClock.elapsedRealtime()
        val staged = store.nextRun.claim(recipe)
        Log.i(TAG, "next run: " + (if (staged != null) "taken, seed %016x".format(staged.seed) else "none waiting") +
            " (waited ${SystemClock.elapsedRealtime() - t0} ms)")
        return staged
    }

    /** A run is being made from a seed the player chose, so no stage will be taken: stop making one. */
    fun stop() {
        generation++
        release(keep = null)
    }

    /**
     * The worker told what is wanted of it, raised to normal priority either
     * way ([released]). One making a stage for [keep] finishes sooner. Any
     * other is interrupted: asleep it wakes and leaves, and randomizing it
     * stops at its next random draw (RandomSource), so a stage nobody will
     * take is never waited for longer than it must be.
     */
    private fun release(keep: NextRun.Recipe?) {
        val w = synchronized(lock) { worker } ?: return
        if (!w.isAlive) return
        released(keep, making, workerTid, raise = { tid -> runCatching { Process.setThreadPriority(tid, Process.THREAD_PRIORITY_DEFAULT) } }, interrupt = { w.interrupt() })
    }

    /**
     * What [release] does to a live worker: raised to normal priority whatever it makes, and interrupted unless it makes
     * the stage wanted. A stage nobody will take stops only at its next random draw, and a DS game's load and final
     * write have none: left at background priority it ran on in the background while NEW RUN waited on its lock
     * (rc32 audit P3 #34).
     */
    internal fun <R> released(keep: R?, making: R?, tid: Int, raise: (Int) -> Unit, interrupt: () -> Unit) {
        if (tid != 0) raise(tid)
        if (keep == null || making != keep) interrupt()
    }

    /** The app build, part of every recipe: a reinstall can randomize differently with the same engine. */
    fun appStamp(context: Context): String = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        "${p.versionName} ${UpdateCheck.versionCode(p)} ${p.lastUpdateTime}"
    }.getOrDefault("unknown")

    /**
     * Whether [usable] bytes free leave room for a stage of about [estimate]
     * bytes: after it is written, at least [SPACE_FLOOR] and two more runs'
     * worth must still be free, so a stage never takes the space a save or
     * the next NEW RUN needs.
     */
    fun roomFor(usable: Long, estimate: Long): Boolean = usable - estimate >= maxOf(SPACE_FLOOR, 2 * estimate)

    private fun work(app: Context, settleMs: Long, gen: Int) {
        workerTid = Process.myTid()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            if (settleMs > 0) Thread.sleep(settleMs)
            if (gen != generation || !ahead || RunJob.busy) return
            val store = PrepStore(app)
            val (kind, prepared, settings) = runCatching { RunStart.lastInputs(store) }.getOrNull() ?: return
            // The passes as RUN has them switched now (ExtraPasses); NEW RUN asks again, and a
            // switch flipped in between makes a different recipe, so this stage is not taken.
            val secondPass = ExtraPasses.secondPassFor(app, store, kind, settings)
            val prePass = ExtraPasses.prePassFor(app, store, kind, settings)
            // Heart & Soul: the run in play's pool, as Play's NEW RUN asks for it (HnsPool.ofRun), so the stage is taken.
            val recipe = NextRun.recipe(kind, prepared, settings, secondPass, prePass, appStamp(app), if (kind.isHns) HnsPool.ofRun(store) else null)
            if (store.nextRun.ready(recipe) != null) { Log.i(TAG, "next run: one is waiting for ${kind.id}"); return }

            // The output is about the size of the run in play; the prepared ROM when there is none yet.
            val estimate = store.currentRunFor(kind).takeIf { it.isFile }?.length() ?: prepared.length()
            val usable = store.nextRun.dir.apply { mkdirs() }.usableSpace
            if (!roomFor(usable, estimate)) {
                Log.i(TAG, "next run: skipped, ${usable shr 20} MB free for a ${estimate shr 20} MB run")
                return
            }
            val rt = Runtime.getRuntime()
            val used = rt.totalMemory() - rt.freeMemory()
            val needHeap = heapNeed(kind.platform, prepared.length())
            val mem = ActivityManager.MemoryInfo()
            runCatching { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem) }
            if (rt.maxMemory() - used < needHeap || mem.lowMemory || mem.availMem < mem.threshold + needHeap) {
                Log.i(TAG, "next run: skipped, heap ${used shr 20}/${rt.maxMemory() shr 20} MB, " +
                    "system ${mem.availMem shr 20} MB free of which ${mem.threshold shr 20} MB is its low mark")
                return
            }
            if (gen != generation) return

            val peak = java.util.concurrent.atomic.AtomicLong(used)
            val sampler = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                try {
                    while (true) {
                        peak.accumulateAndGet(rt.totalMemory() - rt.freeMemory()) { a, b -> maxOf(a, b) }
                        Thread.sleep(100)
                    }
                } catch (e: InterruptedException) {
                    // stopped with the stage
                }
            }, "next-run-heap").apply { isDaemon = true; start() }
            val t0 = SystemClock.elapsedRealtime()
            making = recipe
            try {
                // Asked again under the stage's lock: a NEW RUN that came in since
                // has its own stage to take, and this one must not start over it.
                val staged = store.nextRun.make(recipe, java.security.SecureRandom().nextLong(), { dest, s ->
                    Randomizers.randomize(kind, prepared, settings, dest, s, secondPass = secondPass, prePass = prePass,
                        pool = Randomizers.hnsPoolOf(recipe.engine))
                    // Stopped after its last draw, or by an engine that caught the stop and
                    // carried on: either way not a whole run, and never kept (release).
                    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("stage stopped")
                }, proceed = { gen == generation }) ?: return
                Log.i(TAG, "next run: ${kind.id} staged in ${SystemClock.elapsedRealtime() - t0} ms, " +
                    "${staged.rom.length() shr 20} MB, heap ${used shr 20} MB before and ${peak.get() shr 20} MB at peak")
            } finally {
                making = null
                sampler.interrupt()
            }
        } catch (e: InterruptedException) {
            Log.i(TAG, "next run: not started, a new run came first")
        } catch (t: Throwable) {
            // OutOfMemoryError included: the stage is deleted (NextRun.make) and NEW RUN randomizes as before.
            if (Thread.currentThread().isInterrupted) Log.i(TAG, "next run: stopped, another run was asked for")
            else Log.w(TAG, "next run: not staged", t)
        } finally {
            workerTid = 0
            synchronized(lock) { if (worker === Thread.currentThread()) worker = null }
        }
    }
}
