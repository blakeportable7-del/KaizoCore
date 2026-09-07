package com.ironmonone.tracker

/**
 * The reference's GameOverScreen.LossConditions: which faint ends the run.
 * "Game is considered over when" on its Gameplay options tab, default
 * LeadPokemonFaints. Every tracker asks this with its party as (level, curHp)
 * pairs, eggs left out; a level of 0 is a slot that has not decoded yet, not a
 * dead Pokemon, and never counts.
 */
enum class LossCondition(val key: String, val label: String) {
    LEAD("LeadPokemonFaints", "Lead Pokemon faints"),
    HIGHEST_LEVEL("HighestLevelFaints", "Highest level faints"),
    ENTIRE_PARTY("EntirePartyFaints", "Entire party faints");

    fun lost(party: List<Pair<Int, Int>>): Boolean {
        val real = party.filter { it.first > 0 }
        if (real.isEmpty()) return false
        return when (this) {
            LEAD -> real.first().second == 0
            HIGHEST_LEVEL -> { val top = real.maxOf { it.first }; real.any { it.first == top && it.second == 0 } }
            ENTIRE_PARTY -> real.all { it.second == 0 }
        }
    }

    companion object {
        fun byKey(key: String?): LossCondition = entries.firstOrNull { it.key == key } ?: LEAD
    }
}
