package com.ironmonone.app

/**
 * The Pokemon info screen's two empty states (InfoScreen.lua): a species that learns no moves
 * says so under its learn levels (:772-774, "Does not learn any moves"), and one with nothing 2x
 * or 4x against it says so under its weaknesses (:818-820, "Has no weaknesses"). The screen used
 * to leave the learn levels out and print no weakness rows, saying nothing in either case.
 */
internal object InfoScreenLines {
    const val NO_MOVES = "Does not learn any moves"
    const val NO_WEAKNESSES = "Has no weaknesses"

    /** The learn levels, those still ahead in brackets, or [NO_MOVES]. */
    fun learnLevels(moveLevels: List<Int>, level: Int): String =
        if (moveLevels.isEmpty()) NO_MOVES else moveLevels.joinToString(", ") { if (it <= level) "$it" else "[$it]" }

    /** InfoScreen.lua:818: `#data.e[2] == 0 and #data.e[4] == 0`. */
    fun hasNoWeaknesses(effectiveness: Map<Double, List<String>>): Boolean =
        effectiveness[2.0].isNullOrEmpty() && effectiveness[4.0].isNullOrEmpty()
}
