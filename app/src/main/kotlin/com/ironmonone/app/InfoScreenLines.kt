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

    /**
     * The learn levels, those still ahead in brackets, or [NO_MOVES]. With no level (0: a species opened from a route
     * icon or the lookup), every level plain, as InfoScreen.lua:785-790 draws them; bracketed, they read as all still
     * ahead, even for an opponent in battle that knows them already (rc32 audit P2 #100).
     */
    fun learnLevels(moveLevels: List<Int>, level: Int): String =
        if (moveLevels.isEmpty()) NO_MOVES
        else moveLevels.joinToString(", ") { if (level <= 0 || it <= level) "$it" else "[$it]" }

    /**
     * The level the info screen measures [species]'s learn levels against (DataHelper.lua:452-465): in a battle, the level
     * of the Pokemon on the field of that species, in TrackerAPI.getActiveBattlePokemon's order (your left one, the
     * opponent's, then a double battle's right-hand pair); otherwise your lead's when it is that species; else 0, no level.
     */
    fun viewedLevel(species: Int, state: com.ironmonone.tracker.TrackerState?): Int {
        val s = state ?: return 0
        if (s.inBattle) {
            s.onField?.mon?.takeIf { it.species == species }?.let { return it.level }
            s.enemy?.takeIf { !it.isGhost && it.species == species }?.let { return it.level }
            s.ownRight?.mon?.takeIf { it.species == species }?.let { return it.level }
            s.enemyRight?.takeIf { it.species == species }?.let { return it.level }
            return 0
        }
        return s.onField?.mon?.takeIf { it.species == species }?.level ?: 0
    }

    /** InfoScreen.lua:818: `#data.e[2] == 0 and #data.e[4] == 0`. */
    fun hasNoWeaknesses(effectiveness: Map<Double, List<String>>): Boolean =
        effectiveness[2.0].isNullOrEmpty() && effectiveness[4.0].isNullOrEmpty()
}
