package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.TrackerState

/**
 * A Pokemon Tower ghost fought without the Silph Scope (Battle.isGhost,
 * Battle.lua:371), as the GBA panel and the play screen treat it. The tracker
 * hands the panel the reference's stand-in (Tracker.getGhostPokemon); these
 * are the parts of the reference's screen that key off it.
 */
internal object GhostCard {
    /** Tracker.getNote(GhostId) returns this whatever was saved (Tracker.lua:490). */
    const val NOTE = "Spoooky!"

    /**
     * The bundled pack's ghost icon: 1285, the Nat. Dex GhostId, is the same art
     * as the reference's images/pokemonUpdated/413.png. Vanilla's GhostId 413
     * would draw Grotle from this pack, and the ROM has no picture for it.
     */
    const val SPRITE = 1285

    /**
     * Battle.lua:505: incrementEnemyEncounter is skipped for a ghost, so the
     * encounter count, the last-seen level and the route sighting all stay
     * as they were.
     */
    fun recordsEncounter(state: TrackerState?): Boolean = state?.isGhostBattle != true

    /** The note the carousel shows for the opponent: the stored one, or the ghost's. */
    fun note(enemy: EnemyInfo?, stored: String): String = if (enemy?.isGhost == true) NOTE else stored

    /**
     * The enemy's type icons as (icon name, type id). The stand-in's two types
     * are both PokemonData.Types.UNKNOWN, drawn once as the "unknown" icon;
     * Gen3Types calls that slot Mystery, which has no icon of its own.
     */
    fun types(e: EnemyInfo): List<Pair<String, Int>> =
        if (e.isGhost) listOf("Unknown" to e.type1)
        else listOf(Gen3Types.name(e.type1) to e.type1) +
            (if (e.type2 != e.type1) listOf(Gen3Types.name(e.type2) to e.type2) else emptyList())

    /** The BST line: PokemonData.BlankPokemon.bst for the stand-in, Constants.BLANKLINE. */
    fun bst(e: EnemyInfo): String = if (e.isGhost) "---" else e.base?.bst?.toString() ?: "?"
}
