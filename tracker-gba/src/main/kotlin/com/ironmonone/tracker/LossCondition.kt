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
    ENTIRE_PARTY("EntirePartyFaints", "Entire party faints"),
    /**
     * Kaizo Doubles (the official settings gist, "Kaizo Doubles" rule 1): if either of your
     * two Pokemon faints, the run is over. No PC tracker has it; they end a Doubles run only
     * with the lead (Blake, 2026-09-29: the most recent rules).
     */
    EITHER_OF_FIRST_TWO("EitherOfFirstTwoFaints", "Either of your first two faints");

    fun lost(party: List<Pair<Int, Int>>): Boolean = lostMons(party.map { LossMon(it.first, it.second) })

    /**
     * The reference's conditions over the party in slot order, eggs ignored
     * (`pokemon.isEgg ~= 1`): the lead is the first Pokemon that is not an egg,
     * as TrackerAPI.getPlayerPokemon(1) finds it (Tracker.getPokemon skips eggs,
     * Tracker.lua:105-140); highest level checks every tie; entire party needs a
     * real Pokemon and every real one at 0 HP. The lead was slot 1 itself, so an
     * egg there meant the lead fainting never ended the run (rc32 audit P2 #136).
     */
    fun lostMons(party: List<LossMon>): Boolean {
        val real = party.filter { it.level > 0 && !it.isEgg }.map { it.level to it.curHp }
        if (real.isEmpty()) return false
        return when (this) {
            LEAD -> real.first().second == 0
            EITHER_OF_FIRST_TWO -> real.take(2).any { it.second == 0 }
            HIGHEST_LEVEL -> { val top = real.maxOf { it.first }; real.any { it.first == top && it.second == 0 } }
            ENTIRE_PARTY -> real.all { it.second == 0 }
        }
    }

    companion object {
        fun byKey(key: String?): LossCondition = entries.firstOrNull { it.key == key } ?: LEAD

        /**
         * QuickloadScreen.SettingsKeywordToGameOverMap: a new profile's condition from its
         * settings file's name, case-insensitive; no keyword means the lead.
         */
        fun forSettingsName(name: String): LossCondition = when {
            // Before "Kaizo": a Kaizo Doubles file is the doubles rule, not the lead's.
            name.contains("Doubles", true) -> EITHER_OF_FIRST_TWO
            // Red, Blue and Yellow Survival's "HM 01, Buddy Has Fun" puts the HM friend in the lead slot in a gym to
            // faint on purpose, so the lead's faint is no loss there (2026-09-30, IronMON rules check R9).
            name.contains("RBY", true) && name.contains("Survival", true) -> HIGHEST_LEVEL
            name.contains("Standard", true) || name.contains("Ultimate", true) -> ENTIRE_PARTY
            name.contains("Kaizo", true) || name.contains("Survival", true) -> LEAD
            else -> LEAD
        }
    }
}

/** One party slot for a loss check: a level of 0 is a slot that has not decoded yet. */
data class LossMon(val level: Int, val curHp: Int, val isEgg: Boolean = false)
