package com.ironmonone.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Run tab's long jobs (randomize, import, export), owned by the app rather
 * than by the screen.
 *
 * They used to run in the screen's rememberCoroutineScope(), so switching tabs
 * cancelled a randomize mid-way while the panel said "Leaving this screen is
 * fine". runCatching then swallowed the CancellationException: the new ROM
 * could be on disk with no attempt counted, no seed or last run saved and the
 * old run's notes still there, and the button came back idle, so a second
 * randomize could overlap the first (2026-09-27, audit). This scope outlives
 * the screen; the screen only observes [busy], [phase] and the status.
 */
object RunJob {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var busy by mutableStateOf(false)
        private set
    var phase by mutableStateOf(RunPhase.ROTATING)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var statusIsError by mutableStateOf(false)
        private set
    /** Bumped when a job changes files on disk, so RUN re-reads its lists. */
    var generation by mutableStateOf(0)
        private set

    /**
     * Called on the main thread when a new run is ready. RUN sets it while it
     * is on screen and clears it when it leaves, so a run that finishes after
     * the player went elsewhere does not pull them to Play.
     */
    @Volatile var onRunReady: (() -> Unit)? = null

    fun say(text: String?, isError: Boolean = false) { status = text; statusIsError = isError }

    private suspend fun main(block: () -> Unit) = withContext(Dispatchers.Main.immediate) { block() }

    /**
     * Randomize [rom] with [settings] into the current run. Returns false when
     * a job is already running: one randomize at a time, never two writing
     * the same files.
     */
    fun randomize(context: Context, rom: Pair<RomKind, File>, settings: File, seed: Long): Boolean {
        if (busy) return false
        val app = context.applicationContext
        busy = true; status = null; phase = RunPhase.ROTATING
        scope.launch {
            val store = PrepStore(app)
            try {
                val outcome = withContext(Dispatchers.IO) {
                    store.rotateRuns(rom.first)
                    // The engine has no progress callback, so these three are
                    // the only honest phases available: the steps the caller
                    // actually performs. Nothing pretends to know how far
                    // through randomize() we are.
                    main { phase = RunPhase.RANDOMIZING }
                    // Engine is chosen by the ROM, never by the user: NatDex ROMs get
                    // the fork, vanilla ROMs get ZX 4.6.1 (which also handles NDS
                    // Gen 4). Cross-wiring is impossible.
                    val dest = store.currentRunFor(rom.first)
                    Randomizers.randomize(rom.first, rom.second, settings, dest, seed, secondPass = store.secondPassSettings(rom.first))
                }
                main { phase = RunPhase.FINISHING }
                withContext(Dispatchers.IO) {
                    store.saveLastRun(rom.first.id, settings.name)
                    store.saveLastSeed(outcome.seed)
                    // A fresh seed is what the player wants to play next, even
                    // if a library ROM was open before.
                    store.library.selectRun()
                    // Name the game explicitly rather than leaning on saveLastRun
                    // having already run: this counter is per game and must not
                    // depend on the order of the two lines above it.
                    store.bumpAttempt(rom.first.id)
                    // Marks, notes and route sightings describe the OLD seed's
                    // randomization; carrying them into the new run is actively
                    // misleading. The Play screen's own NEW RUN already clears
                    // them; this path forgot to.
                    store.clearRunNotes()
                }
                main {
                    say("Your new game is ready (seed ${seedText(outcome.seed)}).")
                    generation++
                    busy = false
                    onRunReady?.invoke()
                }
            } catch (e: CancellationException) {
                // Never swallowed: only the app's own scope can cancel this.
                throw e
            } catch (e: Throwable) {
                main { say(randomizeFailure(e), true); busy = false; generation++ }
            }
        }
        return true
    }

    /**
     * Any other file job (import, export) under the same busy flag and a phase
     * that names it. [block] returns the status line and whether it is an error.
     */
    fun run(phase: RunPhase, whatFailed: String, block: suspend () -> Pair<String, Boolean>): Boolean {
        if (busy) return false
        busy = true; status = null; this.phase = phase
        scope.launch {
            try {
                val (text, err) = withContext(Dispatchers.IO) { block() }
                main { say(text, err) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                main { say(PresetStrings.plain(e, whatFailed), true) }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) { main { busy = false; generation++ } }
            }
        }
        return true
    }

    /** The seed as RUN writes it everywhere: the stored 16 hex digits, grouped for reading. */
    fun seedText(seed: Long): String = "%016x".format(seed)

    /**
     * An engine failure as a sentence. The engine's own refusals ("is a
     * vanilla-era settings file", "is not a GBA Pokémon ROM") already read as
     * sentences and are kept; a wrapped exception ("Randomization failed:
     * null", "Could not read x: Malformed input") is replaced, and the
     * detail goes to the log (2026-09-27, audit).
     */
    private fun randomizeFailure(e: Throwable): String {
        runCatching { android.util.Log.w("IronMonOne", "randomize failed", e) }
        val m = e.message.orEmpty()
        return when {
            e is NatDexEngine.EngineException && m.startsWith("Randomization failed") ->
                "The randomizer stopped partway through. Try again, or pick another settings file."
            e is NatDexEngine.EngineException && m.startsWith("Could not read") ->
                "The randomizer could not read that settings file. It may be damaged or from a newer randomizer."
            e is NatDexEngine.EngineException && m.isNotBlank() -> m
            else -> PresetStrings.plain(e, "Randomizing failed")
        }
    }
}
