package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.core.RomKind
import java.io.File

/**
 * Which build of its game the run in play was made from, and what to do when KaizoCore has moved on (2026-10-06).
 *
 * A run's game is randomized once, from the build of its game the app had then, and stays that file (prep/runs/current.*)
 * until the next run. A game KaizoCore makes from a patch it ships (Heart & Soul's comfort build, RomKind.supersededCrcs)
 * gets a new CRC and new addresses when an update changes that patch, and the tracker knows only the new ones. Blake's
 * rc36 Heart & Soul run, opened in rc36.1, came back where he left it and the tracker sat on "waiting for the game"
 * for good: GameMap.resolve refused the old build and PlayScreen swallowed the refusal. Patching again did not help,
 * because a run never takes a new library copy by itself.
 *
 * So: the run's recipe records its build (NextRun.Recipe.romCrc); a run of an older build is [State.OLDER]; the tracker
 * card says so in plain words ([trackerNote]); and the run can be moved ([move]): the same seed, settings, pool and mode
 * randomized again on this app's build, no attempt counted, notes kept, the in-game save carried over as every new seed
 * does (RunSaves), and the save states from the old build never loaded into the new one (StateStamp). The world is this
 * release's randomization of that seed, not the old one: the engine and the build both changed ([MOVE_LINE]).
 */
internal object RunBuild {

    enum class State {
        /** Made from the build this app has (or no recipe to say otherwise). */
        CURRENT,
        /** Made from an older KaizoCore build of its game: the tracker cannot read it. */
        OLDER,
    }

    /** [kind]'s run, made from [madeFrom] (PrepStore.runBuild): OLDER when that is not the build this app makes. */
    fun state(kind: RomKind?, madeFrom: Long?): State =
        if (kind != null && madeFrom != null && kind.expectedCrc != RomKind.CRC_UNKNOWN && madeFrom != kind.expectedCrc) State.OLDER
        else State.CURRENT

    fun state(store: PrepStore): State = state(RomKind.byId(store.loadLastRun()?.first), store.runBuild())

    /** Where the player makes [kind] again: Heart & Soul has its own button on Home; the rest are made in Library. */
    fun whereToMake(kind: RomKind): String =
        if (kind.isHns) "Home > Pokémon Heart & Soul" else "Library > Patched versions"

    /**
     * What the tracker card says when the tracker will not read the game in play ([refused]: GameMap.resolve's
     * UnreadableBuild), or null when it reads it. [state] is the run's; [haveNew] says whether this app's build of the
     * game is in the library to move the run onto.
     */
    fun trackerNote(kind: RomKind?, isRun: Boolean, state: State, haveNew: Boolean, refused: Boolean): String? {
        // Only when the tracker has refused the game: a run of an older build that it still reads plays on as it is.
        if (!refused) return null
        val name = kind?.displayName ?: "this game"
        if (isRun && state == State.OLDER && kind != null) {
            return "This run was made with an older KaizoCore's $name, and the tracker reads only this version's. " +
                if (haveNew) MOVE_LINE else "Make it again in ${whereToMake(kind)}, then come back here to move the run."
        }
        return "The tracker cannot read this copy of $name: it was made by another KaizoCore version. " +
            (kind?.let { "Make it again in ${whereToMake(it)}. " } ?: "") + "The game plays without the tracker."
    }

    /**
     * Measured 2026-10-06 (rc36's engine on C993EB6E against rc36.1's on E35A0E40, six seeds, both pools): the same seed
     * gives other evolutions, base stats, starters, trainers and wild Pokemon on nearly every seed. So the words say the
     * world is made again, and that what carries is the in-game save.
     */
    const val MOVE_LINE = "Move it to this version: the same seed and settings are randomized again, so the Pokémon, " +
        "trainers and items come out differently, and your in-game save brings your team and progress."
    const val MOVE_BUTTON = "Move this run"
    const val REMAKE_BUTTON = "Make it again"
    const val MOVING = "Moving this run to this version of the game…"
    const val MOVED = "Moved. Pick Continue on the title screen: your in-game save carries the run. Save states from the older build stay behind."

    /** What a move is made from: the run's own game, settings, seed, pool, mode and passes. */
    class Inputs(
        val kind: RomKind, val prepared: File, val settings: File, val seed: Long, val pool: HnsEngine.Pool?,
        val nuzlocke: Boolean, val prePass: Boolean, val secondPass: Boolean,
    )

    /**
     * The run in play's inputs for [move], or a [RunSetupProblem] saying in plain words why it cannot be moved: the
     * run is not of an older build, this app's build of the game is not in the library (made again where [whereToMake]
     * says), or its settings file or seed are gone.
     */
    fun inputs(store: PrepStore): Inputs {
        val (romId, settingsName) = store.loadLastRun() ?: throw RunSetupProblem("There is no run to move.")
        val kind = RomKind.byId(romId) ?: throw RunSetupProblem("There is no run to move.")
        if (state(kind, store.runBuild()) != State.OLDER) throw RunSetupProblem("This run is already on this version of the game.")
        val prepared = store.listPrepared().firstOrNull { it.first.id == kind.id }?.second
            ?: throw RunSetupProblem("Make ${kind.displayName} again in ${whereToMake(kind)} first.")
        val settings = store.listSettings().firstOrNull { it.name == settingsName }
            ?: throw RunSetupProblem("The settings \"$settingsName\" this run was made with are missing, so it cannot be made again.")
        val seed = store.lastSeedText().toULongOrNull(16)?.toLong() ?: throw RunSetupProblem("This run's seed is missing, so it cannot be made again.")
        val recipe = NextRun.currentRecipe(store)?.first
        return Inputs(
            kind, prepared, settings, seed,
            pool = if (kind.isHns) com.ironmonone.app.engine.Randomizers.hnsPoolOf(recipe?.engine) else null,
            nuzlocke = store.lastRunNuzlocke(),
            prePass = recipe?.prePass?.isNotBlank() == true, secondPass = recipe?.secondPass?.isNotBlank() == true,
        )
    }

    /**
     * Makes the run in play again from [i] on this app's build: the same seed, settings, pool and mode, no attempt
     * counted and nothing filed as ended (PrepStore.installRun's moved), the Nuzlocke preset for a Heart & Soul Nuzlocke
     * (RunStart). The save states of the old build are pinned to it on the way (StateStamp.pin). Blocking: a randomize.
     */
    fun move(
        store: PrepStore, i: Inputs, app: String, prePass: File?, secondPass: File?,
        randomize: ((File, Long) -> Unit)? = null,
        hnsNuzlockePreset: ((File) -> Unit)? = null,
    ): RunStart.Started {
        val from = store.runBuild()
        val started = if (randomize != null) RunStart.start(store, i.kind, i.prepared, i.settings, i.seed, app, prePass, secondPass,
            pool = i.pool, randomize = randomize, countAttempt = !i.nuzlocke, moved = true,
            hnsNuzlockePreset = hnsNuzlockePreset ?: { })
        else RunStart.start(store, i.kind, i.prepared, i.settings, i.seed, app, prePass, secondPass,
            pool = i.pool, countAttempt = !i.nuzlocke, moved = true)
        store.runEvents(GameSession.forRun(store.currentRun, i.kind))?.add(RunEvents.Kind.MOVE, "run",
            "made again on build %08X from %s, same seed".format(i.kind.expectedCrc, from?.let { "%08X".format(it) } ?: "an older build"))
        return started
    }
}
