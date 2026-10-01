package com.ironmonone.app

import com.ironmonone.tracker.RandomizedFlags

/**
 * What the opponent's card may reveal: PokemonData.canShowUnknown* and the
 * "Reveal info if randomized" rule (DataHelper.lua 186-250, 340).
 *
 * Open Book Play Mode shows everything. Otherwise a part is shown only when it
 * was NOT randomized and "Show data for vanilla game" is on (its default). A
 * game whose randomization cannot be told counts as randomized, so nothing is
 * revealed by mistake.
 */
internal object InfoRules {
    private fun canShow(changed: Boolean?): Boolean =
        TrackerOptions.openBookPlayMode || (TrackerOptions.showDataForVanillaGame && changed == false)

    /** Both possible abilities, instead of only those revealed in battle. */
    fun canShowAbilities(r: RandomizedFlags?) = canShow(r?.abilities)

    /** The opponent's base stats, instead of the marking boxes. */
    fun canShowStats(r: RandomizedFlags?) = canShow(r?.stats)

    /** The opponent's actual moves, instead of those seen. */
    fun canShowMoves(r: RandomizedFlags?) = canShow(r?.moveLearnSet)

    /**
     * TrainerData.canShowUnknownTrainerTeams (TrainerData.lua:269-274): a trainer's Pokemon on
     * Trainer Info before the trainer is beaten. [teamsRandomized] is GbaTracker.trainerTeamsRandomized.
     */
    fun canShowTrainerTeams(teamsRandomized: Boolean?) = canShow(teamsRandomized)

    /**
     * "Reveal info if randomized" off: which randomized move facts to hide on
     * the opponent's moves. Null when nothing is hidden. Unknown randomization
     * hides everything.
     */
    fun hiddenMoveInfo(r: RandomizedFlags?): RandomizedFlags? {
        if (TrackerOptions.revealInfoIfRandomized || TrackerOptions.openBookPlayMode) return null
        return r ?: RandomizedFlags(true, true, true, true, true, true, true, true, true, true)
    }

    /** PokemonData.Types.UNKNOWN as a type icon (name, Gen 3 type id): the "?" icon, drawn once. */
    val UNKNOWN_TYPE = "Unknown" to 9

    /**
     * "Reveal info if randomized" off, with the types randomized: the viewed opponent's types on
     * the tracker (TrackerScreen.lua:1135) and a species' types in the Notebook
     * (NotebookPokemonNoteView.lua:260) are the one unknown icon. Neither place checks Open Book.
     */
    fun hidesRandomizedTypes(r: RandomizedFlags?): Boolean =
        !TrackerOptions.revealInfoIfRandomized && (r?.types ?: true)

    /**
     * DataHelper.buildPokemonInfoDisplay (DataHelper.lua:432), the Pokemon info screen: the types
     * show where PokemonData.canShowUnknownTypes allows, with the option on, or for your lead's
     * own species; otherwise the unknown icon.
     */
    fun infoScreenHidesTypes(r: RandomizedFlags?, ownLead: Boolean): Boolean =
        !(canShow(r?.types) || TrackerOptions.revealInfoIfRandomized || ownLead)

    /** [types] as (name, type id), or the unknown icon alone when [hidden]. */
    fun typeIcons(types: List<Pair<String, Int>>, hidden: Boolean): List<Pair<String, Int>> =
        if (hidden) listOf(UNKNOWN_TYPE) else types

    /** With it off, even your own moves show no effectiveness while the types are randomized. */
    fun hideOwnEffectiveness(r: RandomizedFlags?): Boolean =
        !TrackerOptions.revealInfoIfRandomized && (r?.types ?: true)

    /**
     * DataHelper.lua:336-344, move.showeffective: a ghost battle (Battle.isGhost)
     * hides every move's effectiveness, your own included; otherwise only your
     * own moves lose it, under [hideOwnEffectiveness].
     */
    fun hideEffectiveness(r: RandomizedFlags?, ghost: Boolean, own: Boolean): Boolean =
        ghost || (own && hideOwnEffectiveness(r))
}
