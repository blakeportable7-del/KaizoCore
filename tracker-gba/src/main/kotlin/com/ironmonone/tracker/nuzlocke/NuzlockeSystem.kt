package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.Gen3Types

/**
 * Which family of games a run is on (2026-09-30). The engine is the same for all of them; a system picks the data
 * files it reads (evolution lines, level caps), how a map's name becomes an area, and the few rules that only make
 * sense on some games: Generation 1 has no genders, Generations 4 and 5 do not tell the tracker how a battle ended.
 *
 * A run made before the Game Boy and DS games were supported has no system in its file and is Generation 3.
 */
enum class NuzlockeSystem(
    val key: String,
    val label: String,
    /** The game has genders. Generation 1 has none (only the two Nidorans are told apart by species). */
    val hasGender: Boolean,
    /** Shiny Pokemon exist (Generation 2 on). */
    val hasShiny: Boolean,
    /** The game leaves a value saying how the last battle ended, so the tracker can read it (Generations 1 to 3). */
    val readsOutcome: Boolean,
    /** The types a Monotype run can pick in these games, as Gen 3 type ids. */
    val types: List<Int>,
) {
    GEN1("gen1", "Game Boy, Red, Blue and Yellow", hasGender = false, hasShiny = false, readsOutcome = true,
        types = Gen3Types.ALL.filter { it != 8 && it != 17 }),
    GEN2("gen2", "Game Boy, Gold, Silver and Crystal", hasGender = true, hasShiny = true, readsOutcome = true, types = Gen3Types.ALL),
    GEN3("gen3", "Game Boy Advance", hasGender = true, hasShiny = true, readsOutcome = true, types = Gen3Types.ALL),
    GEN4("gen4", "DS, Diamond, Pearl, Platinum, HeartGold and SoulSilver", hasGender = true, hasShiny = true, readsOutcome = false, types = Gen3Types.ALL),
    GEN5("gen5", "DS, Black, White, Black 2 and White 2", hasGender = true, hasShiny = true, readsOutcome = false, types = Gen3Types.ALL);

    /** The tracker can read the Poke Ball count, so the rules can begin when the bag first holds balls (Generations 1 to 3). The DS games begin with the first Pokemon. */
    val readsBalls: Boolean get() = this != GEN4 && this != GEN5

    /** The data files of this system, under nuzlocke/ in the resources. */
    val capsResource: String get() = "/nuzlocke/levelcaps-$key.tsv"
    val familiesResource: String get() = "/nuzlocke/families-$key.tsv"

    companion object {
        /** The system a file names; anything unknown, or nothing, is Generation 3, which is what every older ledger is. */
        fun byKey(key: String?): NuzlockeSystem = entries.firstOrNull { it.key == key } ?: GEN3
    }
}
