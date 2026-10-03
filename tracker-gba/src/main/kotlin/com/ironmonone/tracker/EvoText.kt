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

    /** One of the Nat. Dex extension's evolution methods: its abbreviation, short and detailed words, and evoItemIds. */
    private class Method(val abbreviation: String, val short: List<String>, val detailed: List<String>, val items: Set<Int> = emptySet())

    /**
     * NatDexExtension.lua natDexEvoDetails (lines 4795-4969), the methods its species table names
     * (gen3/species-extra-natdex.tsv): the new stones, the held items that replace trades, the female-only levels and
     * Eevee's eight stones. Its item ids are the Nat. Dex ROM's (rc33 audit P1 #67).
     */
    private val NAT_DEX = mapOf(
        "SHINY" to Method("SHINY", listOf("Shiny"), listOf("Shiny Stone"), setOf(99)),
        "DUSK" to Method("DUSK", listOf("Dusk"), listOf("Dusk Stone"), setOf(100)),
        "DAWN" to Method("DAWN", listOf("Dawn"), listOf("Dawn Stone"), setOf(101)),
        "ICE" to Method("ICE", listOf("Ice"), listOf("Ice Stone"), setOf(102)),
        "METAL_COAT" to Method("MTL CT", listOf("Mtl.Coat"), listOf("Metal Coat"), setOf(199)),
        "KINGS_ROCK" to Method("KNG RCK", listOf("K.Rock"), listOf("King's Rock"), setOf(187)),
        "DRAGON_SCALE" to Method("DSCALE", listOf("D.Scale"), listOf("Dragon Scale"), setOf(201)),
        "UPGRADE" to Method("UPGRADE", listOf("Up-Grade"), listOf("Up-Grade"), setOf(218)),
        "DUBIOUS_DISC" to Method("D.DISC", listOf("Dub.Disc"), listOf("Dubious Disc"), setOf(89)),
        "RAZOR_CLAW" to Method("R.CLAW", listOf("Rzr.Claw"), listOf("Razor Claw"), setOf(90)),
        "RAZOR_FANG" to Method("R.FANG", listOf("Rzr.Fang"), listOf("Razor Fang"), setOf(91)),
        "LINKING_CORD" to Method("L.CORD", listOf("Link Crd."), listOf("Linking Cord"), setOf(92)),
        "WATER_DUSK" to Method("WTR/DSK", listOf("Water", "Dusk"), listOf("Water Stone", "Dusk Stone"), setOf(97, 100)),
        "MOON_SUN" to Method("MN/SUN", listOf("Moon", "Sun"), listOf("Moon Stone", "Sun Stone"), setOf(94, 93)),
        "SUN_LEAF_DAWN" to Method("SN/LF/DW", listOf("Sun", "Leaf", "Dawn"), listOf("Sun Stone", "Leaf Stone", "Dawn Stone"), setOf(93, 98, 101)),
        "SUN_MOON_DUSK" to Method("SN/MN/DS", listOf("Sun", "Moon", "Dusk"), listOf("Sun Stone", "Moon Stone", "Dusk Stone"), setOf(93, 94, 100)),
        "COAT_ROCK" to Method("MCT/KRK", listOf("Mtl.Coat", "K.Rock"), listOf("Metal Coat", "King's Rock"), setOf(199, 187)),
        "DAWN42" to Method("42/DWN", listOf("Lv.42", "Dawn(F)"), listOf("Level 42", "Dawn Stone (Female)"), setOf(101)),
        "DAWN30" to Method("30/DWN", listOf("Lv.30", "Dawn(M)"), listOf("Level 30", "Dawn Stone (Male)"), setOf(101)),
        "FEMALE21" to Method("21F", listOf("Lv.21(F)"), listOf("Level 21 (Female)")),
        "FEMALE33" to Method("33F", listOf("Lv.33(F)"), listOf("Level 33 (Female)")),
        "WATER_ROCK" to Method("WTR/KRK", listOf("Water", "K.Rock"), listOf("Water Stone", "King's Rock"), setOf(97, 187)),
        "ROCK37" to Method("37/KRK", listOf("Lv. 37", "K.Rock"), listOf("Level 37", "King's Rock"), setOf(187)),
        "DEEPSEA" to Method("DEEPSEA", listOf("D.S.Tooth", "D.S.Scale"), listOf("Deep Sea Tooth", "Deep Sea Scale"), setOf(192, 193)),
        "EEVEE_STONES_NATDEX" to Method("STONE", listOf("Thunder", "Water", "Fire", "Sun", "Moon", "Leaf", "Ice", "Dawn"),
            listOf("8 Diff. Stones"), setOf(93, 94, 95, 96, 97, 98, 102, 101)),
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
        return if (isLevel(e)) e else ABBREVIATION[e] ?: NAT_DEX[e]?.abbreviation
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
        NAT_DEX[e]?.let { return it.detailed }
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
        NAT_DEX[e]?.let { return it.short }
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
        val byStone = (STONES[e] ?: NAT_DEX[e]?.items)?.let { stones -> bagIds().any { it in stones } } == true
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
