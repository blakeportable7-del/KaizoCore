package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
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

    /** Room kept beyond the new game itself: its log, the DS species file, and the engine's own working files. */
    private const val SPACE_MARGIN = 16L shl 20

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
        /**
         * Heart & Soul's pool: the run's own for Play's NEW RUN (HnsPool.ofRun), so the next game keeps the run's
         * Pokemon; null for the one chosen now on the Kaizo IronMON and Nuzlocke screens. Ignored for other games.
         */
        pool: HnsEngine.Pool? = null,
        randomize: (dest: File, seed: Long) -> Unit = { dest, s ->
            Randomizers.randomize(kind, prepared, settings, dest, s, secondPass = secondPass, prePass = prePass, pool = pool)
        },
        /** False for a randomized Nuzlocke, which counts no IronMON attempt (PrepStore.installRun). */
        countAttempt: Boolean = true,
        /**
         * Puts the Nuzlocke mode's challenge preset into a Heart & Soul Nuzlocke run's game (HnsEngine.writePreset): the
         * randomizer writes Kaizo IronMON's. Here, so every way a run starts does it: Play's NEW RUN did not, and a
         * Nuzlocke's next game came up with the Kaizo challenge settings (rc36.1 known issue). The test's to replace.
         */
        hnsNuzlockePreset: (File) -> Unit = { f -> HnsEngine.writePreset(f, HnsEngine.Preset.NUZLOCKE, HnsEngine.appAssets()) },
        /** The same run made again on this app's build of its game (RunMove): PrepStore.installRun's moved. */
        moved: Boolean = false,
        /** Built from a run code (RunCodeUi): its record says so (R7). */
        fromCode: Boolean = false,
        /** The free bytes where the run is made; the test's to replace. */
        freeBytes: (File) -> Long = { it.usableSpace },
    ): Started {
        val recipe = NextRun.recipe(kind, prepared, settings, secondPass, prePass, app, pool)
        val ahead = if (seed == null) take(recipe) else { stop(); null }
        // Room for the new game (twice over with a second pass, whose first output waits beside it) before it is made:
        // only the run made ahead looked, and a randomize that ran out of space partway could leave a cut-short game
        // (rc32 audit P2 #119).
        if (ahead == null) {
            val need = prepared.length() * (if (prePass != null || secondPass != null) 2 else 1) + SPACE_MARGIN
            if (freeBytes(store.nextRun.dir.apply { mkdirs() }) < need) throw RunSetupProblem(Randomizers.NO_ROOM_BEFORE)
        }
        val staged = ahead ?: store.nextRun.makeAndClaim(recipe, seed ?: java.security.SecureRandom().nextLong(), randomize)
            ?: throw java.io.IOException("The new run could not be moved into place.")
        // What the official file runs without, for the record (R4): decided on the file's bytes and the passes taken.
        val variant = CustomRuns.bundled?.invoke()?.takeIf { it.isNotEmpty() }?.let { b ->
            ExtraPasses.variant(kind, settings.name, runCatching { settings.readBytes() }.getOrNull(), b, prePass != null, secondPass != null)
        }
        store.installRun(kind, settings.name, staged, countAttempt = countAttempt, variant = variant, fromCode = fromCode, moved = moved)
        // A Heart & Soul Nuzlocke (no IronMON attempt counted) carries the Nuzlocke mode's preset, not Kaizo's.
        if (kind.isHns && !countAttempt) hnsNuzlockePreset(store.currentRunFor(kind))
        return Started(staged.seed, staged === ahead)
    }

    /**
     * Why the game [romId]'s runs are made from is not there to make another, in words the player can act on: an older
     * KaizoCore's build of it in [library] (RomKind.supersededCrcs) is named as that (2026-10-06), not as "gone".
     */
    internal fun goneLine(romId: String, library: List<LibraryStore.Entry>): String {
        val kind = RomKind.byId(romId)
        if (kind != null && library.any { kind.isOlderBuild(it.crc) })
            return "Your ${kind.displayName} was made by an older KaizoCore. Make it again in ${RunBuild.whereToMake(kind)}, then start the run."
        return "The game this run was made from is gone. Add it again in Library, My games, or for a patched build make it again in Library, Patched versions."
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
            ?: throw RunSetupProblem(goneLine(romId, runCatching { store.library.list() }.getOrDefault(emptyList())))
        val settings = store.listSettings()
            .firstOrNull { it.name == settingsName }
            ?: throw RunSetupProblem("The settings \"$settingsName\" are missing. Pick settings in Kaizo IronMON.")
        return Triple(prepared.first, prepared.second, settings)
    }
}
