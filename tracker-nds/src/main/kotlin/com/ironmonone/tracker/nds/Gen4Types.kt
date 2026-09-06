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
/**
 * Gen 4 healing items, from the DS tracker's ItemData.HEALING_ITEMS.
 *
 * The ids are NOT the Gen 3 ones - Gen 4 renumbered the item table - and the
 * values differ too: a Sitrus Berry restores a flat 30 HP in Gen 3 but 25% of
 * max HP in Gen 4. Reusing the Gen 3 table here would have read plausibly and
 * been wrong on every berry.
 *
 * Pair is (amount, isPercentage).
 */
internal val GEN4_HEAL_ITEMS: Map<Int, Pair<Double, Boolean>> = mapOf(
    23 to (100.0 to true),      // Full Restore
    24 to (100.0 to true),      // Max Potion
    158 to (25.0 to true),      // Sitrus Berry
    159 to (12.5 to true),      // Figy Berry
    160 to (12.5 to true),      // Wiki Berry
    161 to (12.5 to true),      // Mago Berry
    162 to (12.5 to true),      // Aguav Berry
    163 to (12.5 to true),      // Iapapa Berry
    208 to (12.5 to true),      // Enigma Berry
    17 to (20.0 to false),      // Potion
    25 to (200.0 to false),     // Hyper Potion
    26 to (50.0 to false),      // Super Potion
    30 to (50.0 to false),      // Fresh Water
    31 to (60.0 to false),      // Soda Pop
    32 to (80.0 to false),      // Lemonade
    33 to (100.0 to false),     // Moomoo Milk
    34 to (50.0 to false),      // EnergyPowder
    35 to (200.0 to false),     // Energy Root
    43 to (20.0 to false),      // Berry Juice
    134 to (20.0 to false),     // Sweet Heart
    155 to (10.0 to false),     // Oran Berry
    504 to (20.0 to false),     // RageCandyBar
)

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
