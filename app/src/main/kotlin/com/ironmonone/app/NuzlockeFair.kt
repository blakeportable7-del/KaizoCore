package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File

/**
 * "Nuzlocke fair" (2026-09-30, UX audit P0-7): what a randomized Nuzlocke starts from unless the player picks an
 * IronMON mode instead.
 *
 * The Randomizer preset used to start the official Kaizo IronMON settings, with every level raised (about 60
 * percent on Emerald, with the official level pass), and the screen called it "Standard on a randomized game". A
 * Nuzlocke player asking for a randomized game wants the game mixed up, not an IronMON challenge. This is the
 * common Nuzlocke choice: wild Pokémon and trainers' teams random, each close in strength to the one it replaces,
 * levels as the game has them, and nothing else touched, so the starters, evolutions and items are the game's own
 * and the dupes clause keeps working.
 *
 * It is made through GameBuild, the same code as Build your own, and kept in the settings folder under a name in
 * parentheses ("RSE (Nuzlocke fair).rnqs"), which RulesetCatalog ranks behind every official file: it is never an
 * IronMON mode, and nothing shows it as one.
 */
internal object NuzlockeFair {
    const val KEY = "fair"
    const val LABEL = "Nuzlocke fair"
    const val LINE = "Wild Pokémon and trainers' teams are random, each close in strength to the one it replaces. " +
        "Levels stay as the game has them, and nothing else is changed."
    /** What an IronMON mode says when it is picked here instead. */
    const val IRONMON_LINE = "An IronMON mode's settings, which are harder than most Nuzlockes. Read the mode's rules for what they change."

    /** The answers GameBuild gives the plan: its choice ids and answer ids. */
    val PICKS: Map<String, String> = mapOf("wild" to "similar", "trainers" to "similar")

    private const val NAME = "Nuzlocke fair"

    /** The file's name for [kind]: GameBuild's naming, so a Nat. Dex build gets its own. */
    fun fileName(kind: RomKind): String = GameBuild.fileName(kind, null, NAME)!!

    /**
     * The settings file for [kind], made the first time it is asked for and kept. A file the player renamed or
     * deleted is simply made again.
     */
    fun file(store: PrepStore, kind: RomKind): Result<File> = runCatching {
        val name = fileName(kind)
        store.listSettings().firstOrNull { it.name == name }
            ?: GameBuild.save(store, kind, GameBuild.Plan(picks = PICKS), facts = null, typedName = NAME).getOrThrow()
    }
}
