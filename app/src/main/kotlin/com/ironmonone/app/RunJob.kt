package com.ironmonone.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.app.engine.NatDexEngine
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
     * A new run is being made and installed: the randomize's own phases, never an import, a patch or an export.
     * Play does not start while this is true (MainActivity). A game booted then was the run about to be replaced:
     * its battery save and auto-save landed on the new run's files under the new run's stamp, and on a DS game the
     * install overwrote the ROM the core held open (rc33 audit P0-5). Play shows the progress and starts the new run
     * when it is in.
     */
    val installing: Boolean get() = isInstalling(busy, phase)

    fun isInstalling(busy: Boolean, phase: RunPhase): Boolean =
        busy && (phase == RunPhase.ROTATING || phase == RunPhase.RANDOMIZING || phase == RunPhase.FINISHING)

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
     * the same files. [seed] is one the player chose, kept as it is; null
     * asks for a new one, and a run made ahead for exactly this game and
     * these settings (NextRun) is taken instead of randomizing.
     */
    fun randomize(
        context: Context, rom: Pair<RomKind, File>, settings: File, seed: Long? = null, expect: RunCode? = null, nuzlocke: Boolean = false,
        /** A run code's own passes, for this one build: the player's switches are left as they are (R4). */
        prePassOn: Boolean? = null, part2On: Boolean? = null,
    ): Boolean {
        // One new run at a time across the app: Play's NEW RUN holds the same guard, and two RunStarts at once deleted or
        // swapped each other's stage (rc33 audit P1 #22).
        if (busy || !NewRunGuard.claim()) return false
        val app = context.applicationContext
        // The engine has no progress callback, so the phases are the steps the
        // caller performs. RANDOMIZING covers taking a run made ahead too:
        // that is seconds, and saying more would be pretending.
        busy = true; status = null; phase = RunPhase.RANDOMIZING
        scope.launch {
            val store = PrepStore(app)
            try {
                // Engine is chosen by the ROM, never by the user: NatDex ROMs get
                // the fork, vanilla ROMs get ZX 4.6.1 (which also handles NDS
                // Gen 4). Cross-wiring is impossible. The rotate, the seed, the
                // attempt and the cleared notes are PrepStore.installRun's, the
                // same code Play's NEW RUN goes through.
                val outcome = withContext(Dispatchers.IO) {
                    RunStart.start(store, rom.first, rom.second, settings, seed, NextRunJob.appStamp(app),
                        prePass = ExtraPasses.prePassFor(app, store, rom.first, settings, prePassOn),
                        secondPass = ExtraPasses.secondPassFor(app, store, rom.first, settings, part2On),
                        countAttempt = !nuzlocke, fromCode = expect != null)
                }
                // Heart & Soul: a Nuzlocke run's ROM carries the Nuzlocke mode's challenge preset, not Kaizo IronMON's.
                // RunStart.start writes it now, for every way a run starts (countAttempt false is a Nuzlocke).
                main { phase = RunPhase.FINISHING }
                // The run's Game Over condition comes from its settings file (the reference's
                // profile default by keyword, or what the player last chose for that file).
                main { TrackerOptions.startRunWith(settings.name) }
                // A run built from a code (RunCodes): the same game as the sharer's, or not.
                val built = expect?.let { runCatching { withContext(Dispatchers.IO) { RunCode.crc32(store.currentRunFor(rom.first)) } }.getOrDefault(0L) }
                // What the app wrote into the run's log (ZxEngine.withNote): a setting the engine did not apply (rc32 audit P3 #90).
                val notes = withContext(Dispatchers.IO) {
                    RandomizerLog.notesOf(com.ironmonone.app.engine.Randomizers.logFor(store.currentRunFor(rom.first)))
                }.joinToString("") { " $it" }
                main {
                    val verdict = if (expect != null && built != null) " " + RunCodes.verdict(expect, built) else ""
                    say("Your new game is ready (seed ${seedText(outcome.seed)}).$verdict$notes",
                        isError = expect != null && expect.romCrc != 0L && expect.romCrc != built)
                    generation++
                    busy = false
                    if (expect == null) onRunReady?.invoke()
                }
            } catch (e: CancellationException) {
                // Never swallowed: only the app's own scope can cancel this.
                throw e
            } catch (e: Throwable) {
                main { say(randomizeFailure(e), true); busy = false; generation++ }
            } finally {
                NewRunGuard.release()
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
     * standard settings file", "is not a GBA Pokémon ROM") already read as
     * sentences and are kept; a wrapped exception ("Randomization failed:
     * null", "Could not read x: Malformed input") is replaced, and the
     * detail goes to the log (2026-09-27, audit). A full phone says so, first:
     * it read as "The engine finished but wrote no output." (rc32 audit P2 #119).
     */
    internal fun randomizeFailure(e: Throwable): String {
        runCatching { android.util.Log.w("IronMonOne", "randomize failed", e) }
        val m = e.message.orEmpty()
        return when {
            // Already plain copy: the new run's name or seed could not be saved (PrepStore.installRun, rc32 audit P2 #65).
            e is RunSetupProblem && m.isNotBlank() -> m
            isNoSpace(e) -> com.ironmonone.app.engine.Randomizers.NO_ROOM
            e is NatDexEngine.EngineException && m.startsWith("Randomization failed") ->
                "The randomizer stopped partway through. Try again, or pick another settings file."
            e is NatDexEngine.EngineException && m.startsWith("Could not read") ->
                "The randomizer could not read that settings file. It may be damaged or from a newer randomizer."
            e is NatDexEngine.EngineException && m.isNotBlank() -> m
            else -> PresetStrings.plain(e, "Randomizing failed")
        }
    }
}
