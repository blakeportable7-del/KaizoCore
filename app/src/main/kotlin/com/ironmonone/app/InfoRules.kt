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
     * "Reveal info if randomized" off: which randomized move facts to hide on
     * the opponent's moves. Null when nothing is hidden. Unknown randomization
     * hides everything.
     */
    fun hiddenMoveInfo(r: RandomizedFlags?): RandomizedFlags? {
        if (TrackerOptions.revealInfoIfRandomized || TrackerOptions.openBookPlayMode) return null
        return r ?: RandomizedFlags(true, true, true, true, true, true, true, true, true, true)
    }

    /** With it off, even your own moves show no effectiveness while the types are randomized. */
    fun hideOwnEffectiveness(r: RandomizedFlags?): Boolean =
        !TrackerOptions.revealInfoIfRandomized && (r?.types ?: true)
}
