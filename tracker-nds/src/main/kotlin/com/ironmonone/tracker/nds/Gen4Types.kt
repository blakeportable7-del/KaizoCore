package com.ironmonone.tracker.nds

import com.ironmonone.tracker.Gen3Types

/**
 * Type lookups for the DS tracker.
 *
 * The Gen 4 sidecar stores types as NAMES ("FIRE", "Water"), not ids, so this
 * maps a name onto the internal id and hands the matchup to the shared chart.
 *
 * That chart is Gen3Types': the type effectiveness table did not change between
 * Gen 3 and Gen 4, so there is one table for both rather than a second copy of
 * 110 cells that could drift out of agreement with the first.
 */
object Gen4Types {

    private val byName: Map<String, Int> = mapOf(
        "NORMAL" to 0, "FIGHTING" to 1, "FLYING" to 2, "POISON" to 3,
        "GROUND" to 4, "ROCK" to 5, "BUG" to 6, "GHOST" to 7, "STEEL" to 8,
        "FIRE" to 10, "WATER" to 11, "GRASS" to 12, "ELECTRIC" to 13,
        "PSYCHIC" to 14, "ICE" to 15, "DRAGON" to 16, "DARK" to 17,
    )

    /** Internal id for a type name, or null when the name is blank/unknown. */
    fun idOf(name: String?): Int? =
        name?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { byName[it] }

    fun effect(attack: Int, t1: Int, t2: Int): Double =
        Gen3Types.effect(attack, t1, t2)
}
