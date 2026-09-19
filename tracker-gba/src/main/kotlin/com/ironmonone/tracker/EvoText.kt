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
    data class Label(val text: String, val tone: Tone, val highlighted: Int = 0)

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

    /** [bagIds] is only called for a stone evolution, so the bag is read only when it matters. */
    fun forOwn(
        evo: String?, level: Int, bagIds: () -> Set<Int>,
        friendship: Int, friendshipBase: Int, friendshipRequired: Int,
    ): Label? {
        val e = clean(evo) ?: return null
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
}
