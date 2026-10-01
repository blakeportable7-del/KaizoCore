package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File

/**
 * Starting a new run: what Play's NEW RUN (and the game-over screen's new
 * game, and A+B+Start) and the Run tab's Start new run both do.
 *
 * With no seed asked for, a run made ahead for exactly these inputs, passes
 * included, is taken (NextRun, NextRunJob): seconds instead of a randomize.
 * Otherwise the run is randomized there and then, into the same staging
 * folder, and moved in the same way. Either way PrepStore.installRun does the
 * rest, one path for both buttons. A seed the player chose is never swapped
 * for a staged one.
 *
 * Blocking: a randomize is tens of seconds, and waiting for a stage still
 * being made can be too. Never on the main thread.
 */
object RunStart {

    /** The run now in place: its seed, and whether it was made ahead. */
    class Started(val seed: Long, val wasStaged: Boolean)

    fun start(
        store: PrepStore,
        kind: RomKind,
        prepared: File,
        settings: File,
        /** The seed the player chose, or null for a new one, which may be a run made ahead. */
        seed: Long?,
        /** The app build (NextRunJob.appStamp), part of what a run made ahead must match. */
        app: String,
        /**
         * The passes this run takes around [settings], as RUN's switches are now
         * (ExtraPasses.prePassFor, secondPassFor), null for one it does not take.
         * Part of the recipe: a stage made with the switches the other way is not taken.
         */
        prePass: File?,
        secondPass: File?,
        take: (NextRun.Recipe) -> NextRun.Staged? = { NextRunJob.take(store, it) },
        /** Stops a stage being made, which nothing will take once a seed is chosen. */
        stop: () -> Unit = { NextRunJob.stop() },
        randomize: (dest: File, seed: Long) -> Unit = { dest, s ->
            Randomizers.randomize(kind, prepared, settings, dest, s, secondPass = secondPass, prePass = prePass)
        },
        /** False for a randomized Nuzlocke, which counts no IronMON attempt (PrepStore.installRun). */
        countAttempt: Boolean = true,
        /** Built from a run code (RunCodeUi): its record says so (R7). */
        fromCode: Boolean = false,
    ): Started {
        val recipe = NextRun.recipe(kind, prepared, settings, secondPass, prePass, app)
        val ahead = if (seed == null) take(recipe) else { stop(); null }
        val staged = ahead ?: run {
            store.nextRun.make(recipe, seed ?: java.security.SecureRandom().nextLong(), randomize)
            store.nextRun.claim(recipe) ?: throw java.io.IOException("The new run could not be moved into place.")
        }
        // What the official file runs without, for the record (R4): decided on the file's bytes and the passes taken.
        val variant = CustomRuns.bundled?.invoke()?.takeIf { it.isNotEmpty() }?.let { b ->
            ExtraPasses.variant(kind, settings.name, runCatching { settings.readBytes() }.getOrNull(), b, prePass != null, secondPass != null)
        }
        store.installRun(kind, settings.name, staged, countAttempt = countAttempt, variant = variant, fromCode = fromCode)
        return Started(staged.seed, staged === ahead)
    }

    /**
     * What Play's NEW RUN repeats: the last run's game and settings, or the
     * reason they cannot be found, said the way the player can act on it.
     */
    fun lastInputs(store: PrepStore): Triple<RomKind, File, File> {
        val (romId, settingsName) = store.loadLastRun()
            ?: throw RunSetupProblem("Randomize once in Kaizo IronMON first.")
        val prepared = store.listPrepared()
            .firstOrNull { it.first.id == romId }
            ?: throw RunSetupProblem("The game this run was made from is gone. Add it again in Library, My games, or for a patched build make it again in Library, Patched versions.")
        val settings = store.listSettings()
            .firstOrNull { it.name == settingsName }
            ?: throw RunSetupProblem("The settings \"$settingsName\" are missing. Pick settings in Kaizo IronMON.")
        return Triple(prepared.first, prepared.second, settings)
    }
}
