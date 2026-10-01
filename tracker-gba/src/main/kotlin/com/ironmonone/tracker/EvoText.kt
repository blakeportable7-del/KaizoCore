package com.ironmonone.tracker

import kotlin.math.floor

/**
 * The evolution text after a Pokemon's level: "Lv.5 (30)". TrackerScreen.lua
 * drawPokemonInfoArea, with the words from the reference's English language
 * file (Ironmon-Tracker v9.3.1, the version this app clones).
 *
 * Your own Pokemon: the level and brackets in the default colour, the method in
 * the intermediate colour, or green once it is ready - the level is one short of
 * the target (Utils.isReadyToEvolveByLevel is (level + 1) >= target), a stone it
 * uses is in the bag, or its friendship has reached the requirement, at which
 * point the method reads READY. Short of that, a friendship evolution fills
 * green one letter at a time as friendship climbs from the species' base value.
 * That is "Determine friendship readiness", which the reference turns ON by
 * default (Options.lua).
 *
 * The opponent: the same text, all in the default colour, no readiness.
 *
 * Nothing is drawn when the Pokemon does not evolve.
 */
object EvoText {
    /** Default text, Intermediate text, Positive text. */
    enum class Tone { PLAIN, WAITING, READY }

    /**
     * What goes in the brackets and how it is coloured. The first [highlighted]
     * letters draw green over the rest, which take [tone].
     */
    data class Label(
        val text: String, val tone: Tone, val highlighted: Int = 0,
        /** The DS tracker's friendship bar (IconDrawer.drawFriendshipProgress): the word
         *  drawn green column by column up to this fraction, not whole letters. */
        val fill: Float? = null,
    )

    /** English.lua PokemonData.Evolutions abbreviations. A level evolution is just its number. */
    private val ABBREVIATION = mapOf(
        "FRIEND" to "FRIEND", "FRIEND_READY" to "READY",
        "THUNDER" to "THUNDER", "FIRE" to "FIRE", "WATER" to "WATER", "MOON" to "MOON",
        "LEAF" to "LEAF", "SUN" to "SUN", "LEAF_SUN" to "LF/SN",
        "WATER30" to "30/WTR", "WATER37" to "37/WTR", "WATER37_REV" to "WTR/37",
        "EEVEE_STONES" to "STONE",
    )

    /** PokemonData.Evolutions evoItemIds: the stones that make each method ready. */
    private val STONES = mapOf(
        "EEVEE_STONES" to setOf(93, 94, 95, 96, 97),
        "THUNDER" to setOf(96), "FIRE" to setOf(95), "WATER" to setOf(97),
        "MOON" to setOf(94), "LEAF" to setOf(98), "SUN" to setOf(93),
        "LEAF_SUN" to setOf(93, 98),
        "WATER30" to setOf(97), "WATER37" to setOf(97), "WATER37_REV" to setOf(97),
    )

    /** Program.lua's friendshipRequired before the ROM answers, and PokemonData.Values.DefaultBaseFriendship. */
    const val DEFAULT_REQUIRED = 220
    const val DEFAULT_BASE = 70

    /**
     * A value from our species table. The table was converted from
     * PokemonData.lua and caught the Lua comment on twelve rows along with the
     * value - Kadabra's reads `37", -- Level 37 replaces trade evolution` - so
     * the info screen's "Evolves" line read that too.
     */
    fun clean(raw: String?): String? =
        raw?.substringBefore('"')?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() && it != "NONE" }

    private fun isLevel(evo: String) = evo.isNotEmpty() && evo.all { it.isDigit() }

    /** Utils.getEvoAbbreviation, or null when it does not evolve. */
    fun abbreviation(evo: String?): String? {
        val e = clean(evo) ?: return null
        return if (isLevel(e)) e else ABBREVIATION[e]
    }

    fun forEnemy(evo: String?): Label? = abbreviation(evo)?.let { Label(it, Tone.PLAIN) }

    /**
     * Utils.getDetailedEvolutionsInfo (Utils.lua:631-652) in English.lua's "detailed" words
     * (PokemonData.updateResources, PokemonData.lua:186-230): the Pokemon info screen's lines,
     * "Level 16", "220 Friendship" (the ROM's requirement), "Fire Stone", "5 Diff. Stones", two
     * lines for a method with two ways, and "---" when it does not evolve. The Game Boy trackers'
     * table ([generation] 1 or 2, their PokemonData.lua:53-107) is the same but for "Thunder Stone".
     */
    fun detailed(evo: String?, friendshipRequired: Int = DEFAULT_REQUIRED, generation: Int = 3): List<String> {
        val e = clean(evo) ?: return listOf("---")
        if (isLevel(e)) return listOf("Level $e")
        val thunder = if (generation < 3) "Thunder Stone" else "Thunderstone"
        return when (e) {
            "FRIEND" -> listOf("${if (friendshipRequired > 1) friendshipRequired else DEFAULT_REQUIRED} Friendship")
            "EEVEE_STONES" -> listOf("5 Diff. Stones")
            "THUNDER" -> listOf(thunder)
            "FIRE" -> listOf("Fire Stone")
            "WATER" -> listOf("Water Stone")
            "MOON" -> listOf("Moon Stone")
            "LEAF" -> listOf("Leaf Stone")
            "SUN" -> listOf("Sun Stone")
            "LEAF_SUN" -> listOf("Leaf Stone", "Sun Stone")
            "WATER30" -> listOf("Level 30", "Water Stone")
            "WATER37" -> listOf("Level 37", "Water Stone")
            "WATER37_REV" -> listOf("Water Stone", "Level 37")
            else -> listOf(e)
        }
    }

    /**
     * Utils.getShortenedEvolutionsInfo (Utils.lua:655-665) in English.lua's "short" words: the
     * log viewer's label under each evolution, one per evolution in order ("Lv.16", "Friend",
     * "Fire"; Eevee's five stones "Thunder", "Water", "Fire", "Sun", "Moon"), "---" for none.
     */
    fun short(evo: String?): List<String> {
        val e = clean(evo) ?: return listOf("---")
        if (isLevel(e)) return listOf("Lv.$e")
        return when (e) {
            "FRIEND" -> listOf("Friend")
            "EEVEE_STONES" -> listOf("Thunder", "Water", "Fire", "Sun", "Moon")
            "THUNDER" -> listOf("Thunder")
            "FIRE" -> listOf("Fire")
            "WATER" -> listOf("Water")
            "MOON" -> listOf("Moon")
            "LEAF" -> listOf("Leaf")
            "SUN" -> listOf("Sun")
            "LEAF_SUN" -> listOf("Leaf", "Sun")
            "WATER30" -> listOf("Lv.30", "Water")
            "WATER37" -> listOf("Lv.37", "Water")
            "WATER37_REV" -> listOf("Water", "Lv.37")
            else -> listOf(e)
        }
    }

    /** [bagIds] is only called for a stone evolution, so the bag is read only when it matters. */
    fun forOwn(
        evo: String?, level: Int, bagIds: () -> Set<Int>,
        friendship: Int, friendshipBase: Int, friendshipRequired: Int,
        determineFriendship: Boolean = true,
    ): Label? {
        val e = clean(evo) ?: return null
        // With "Determine friendship readiness" off the reference leaves the
        // method as FRIEND, drawn like any other method still waiting.
        if (e == "FRIEND" && !determineFriendship) return Label("FRIEND", Tone.WAITING)
        if (e == "FRIEND") {
            // DataHelper.lua:211: at the requirement the method becomes READY, drawn green.
            if (friendship >= friendshipRequired) return Label("READY", Tone.READY)
            val span = friendshipRequired - friendshipBase
            val fill = if (span <= 0) 0.0 else (friendship - friendshipBase).toDouble() / span
            val text = "FRIEND"
            return Label(text, Tone.PLAIN, floor(text.length * fill).toInt().coerceIn(0, text.length))
        }
        val text = abbreviation(e) ?: return null
        val byLevel = isLevel(e) && level + 1 >= e.toInt()
        val byStone = STONES[e]?.let { stones -> bagIds().any { it in stones } } == true
        return Label(text, if (byLevel || byStone) Tone.READY else Tone.WAITING)
    }

    /** The five stones of the Game Boy references' MiscData.EvolutionStones. */
    enum class GbStone { MOON, FIRE, THUNDER, WATER, LEAF }

    /**
     * MiscData.EvolutionStones in both Game Boy references (Gen 1 reference
     * MiscData.lua:173-205, Gen 2 reference MiscData.lua:486-517): the methods
     * each stone makes ready, in our table's keys (STONES is EEVEE_STONES). The
     * references key it by Gen 1's item ids; each tracker names its own game's
     * stones instead ([Gen1Tracker.STONES], [GbcTracker.STONES]).
     */
    private val GB_STONE_METHODS = mapOf(
        GbStone.MOON to setOf("MOON", "EEVEE_STONES"),
        GbStone.FIRE to setOf("FIRE", "EEVEE_STONES"),
        GbStone.THUNDER to setOf("THUNDER", "EEVEE_STONES"),
        GbStone.WATER to setOf("WATER", "EEVEE_STONES"),
        GbStone.LEAF to setOf("LEAF", "LEAF_SUN"),
    )

    /**
     * Your own Pokemon on a Game Boy game, as the Game Boy references draw it
     * (Gen 1 reference TrackerScreen.lua:722-760 and DataHelper.lua:166-170; the
     * Gen 2 reference's TrackerScreen.lua:691-729 and DataHelper.lua:162-167 are
     * the same lines). Where it differs from [forOwn]:
     * - Every colour sits inside `if Options["Determine friendship readiness"]`:
     *   with the option off the method is in the default colour, level and
     *   stone readiness included.
     * - The level is the first number in the method, "Lv.%s (" .. evo ..
     *   ")" being drawn from the abbreviation (Utils.isReadyToEvolveByLevel,
     *   `evoMethod:match("%d+")`), so 30/WTR and 37/WTR are ready by level too.
     *   Their Evolutions are strings there; in the Gen 3 reference they are
     *   tables, which that check refuses.
     * - FRIEND reads READY at 220 (Program.lua:23, friendshipRequired), with no
     *   green fill short of it.
     * - A stone is one of [bagStones]: Utils.isReadyToEvolveByStone over the
     *   table above, plus its Water Stone rule for any WTR method, which the
     *   references write with Gen 3's Water Stone id (97) and so never meet on
     *   a Game Boy game.
     */
    fun forOwnGb(evo: String?, level: Int, bagStones: Set<GbStone>, friendship: Int, determineFriendship: Boolean): Label? {
        val e = clean(evo) ?: return null
        if (e == "FRIEND" && determineFriendship && friendship >= DEFAULT_REQUIRED) return Label("READY", Tone.READY)
        val text = abbreviation(e) ?: return null
        if (!determineFriendship) return Label(text, Tone.PLAIN)
        val byLevel = Regex("\\d+").find(text)?.value?.toInt()?.let { level + 1 >= it } == true
        val byStone = bagStones.any { e in GB_STONE_METHODS.getValue(it) || (it == GbStone.WATER && "WTR" in text) }
        return Label(text, if (byLevel || byStone) Tone.READY else Tone.WAITING)
    }
}
