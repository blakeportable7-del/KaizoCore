package com.ironmonone.tracker.gachamon

import kotlin.random.Random

/**
 * GachaMonData.convertPokemonToGachaMon (data/GachaMonData.lua:171-248): a Pokemon as the game holds it, made into a
 * card. The caller reads the game; this does what the reference does with what it read.
 */
object GachaMonMaker {
    /** GachaMonData.SHINY_ODDS: a card's own one-in-213 chance of being shiny, as Pokemon Go and Pokemon Sleep do. */
    const val SHINY_ODDS = 0.004695

    /** One Pokemon, as convertPokemonToGachaMon reads it. */
    data class Mon(
        val personality: Long,
        val species: Int,
        val level: Int,
        /** PokemonData.getAbilityId: the species' ability in the Pokemon's slot, 0 where that slot is empty. */
        val abilityId: Int,
        val stats: SixStats,
        /** The moves it knows that the game has (MoveData.getNatDexCompatible is not BlankMove), in slot order. */
        val moves: List<MoveFacts>,
        val nature: Int,
        /** 1 male, 2 female, 0 neither (MiscData.Gender). */
        val gender: Int,
        val shiny: Boolean,
        /** The species' types in this game, one entry for a single type. */
        val types: List<Int>,
        val baseStats: SixStats,
        val listedBst: Int,
        val evolves: Boolean,
    )

    /** The rating's view of [m]. */
    fun ratingInput(m: Mon, natDex: Boolean): RatingInput = RatingInput(
        abilityId = m.abilityId, types = m.types, baseStats = m.baseStats, listedBst = m.listedBst,
        evolves = m.evolves, natDex = natDex, moves = m.moves, stats = m.stats, nature = m.nature,
    )

    /**
     * The card for [m]: rated under [rulesetKey], dated [year]/[month]/[day], on attempt [seed] of game [gameVersion].
     * [random] decides the card's own shiny chance; a shiny card, or one of five stars or more (always made shiny), is
     * kept in the collection.
     */
    fun make(
        m: Mon,
        natDex: Boolean,
        gameVersion: Int,
        seed: Int,
        year: Int, month: Int, day: Int,
        rulesetKey: String?,
        rs: GachaMonRatingSystem = GachaMonRatingSystem.default,
        random: Random = Random.Default,
    ): GachaMonCard {
        var shiny = m.shiny
        // Reroll shininess chance
        if (!shiny && random.nextDouble() <= SHINY_ODDS) shiny = true
        // Always keep shinies in collection
        var keep = if (shiny) 1 else 0
        val input = ratingInput(m, natDex)
        val rating = rs.ratingScore(input, rulesetKey)
        val version = GachaMonCodec.CURRENT_VERSION
        val stars = rs.stars(rating, version)
        val power = rs.battlePower(input, stars)
        // Always make 5-star or higher GachaMon's shiny
        if (!shiny && stars >= 5) { shiny = true; keep = 1 }
        val type1 = m.types.firstOrNull() ?: UNKNOWN_TYPE
        return GachaMonCard(
            version = version,
            personality = m.personality,
            pokemonId = m.species,
            level = m.level,
            abilityId = m.abilityId,
            ratingScore = rating,
            battlePower = power,
            seedNumber = seed,
            type1 = type1,
            type2 = m.types.getOrNull(1) ?: type1,
            stats = m.stats,
            moveIds = List(4) { m.moves.getOrNull(it)?.id ?: 0 },
            gameVersion = gameVersion,
            keep = keep,
            isShiny = if (shiny) 1 else 0,
            gender = m.gender,
            nature = m.nature,
            year = year, month = month, day = day,
        )
    }

    /** PokemonData.Types.UNKNOWN's index: the Mystery slot. */
    const val UNKNOWN_TYPE = 9
}
