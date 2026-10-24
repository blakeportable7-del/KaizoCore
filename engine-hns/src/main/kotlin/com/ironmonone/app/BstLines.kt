package com.ironmonone.app

/*
 * The rule sheet's BST lines (BstRule), pure Kotlin in engine-hns so the Heart & Soul randomizer (HnsEngine.bstLine)
 * draws the same line in the app and in KaizoCore PC. What needs the app (your own Pokemon's X with its JoinedForms, the
 * legendary list, the Game Boy key, the sheet) stays in the app's BstRule.kt as extensions called the same way.
 */

/**
 * Kaizo's "No Uber Strong Mons": "No using 599+ BST Pokémon. If your Pokémon evolves into something with 600 BST or
 * higher that is ok" (the IronMON rules gist, read 2026-10-02). Blake, the same day, playing Black 2 Kaizo with a
 * Zekrom of 679 from the lab: "there is no indication that this is against the rules", then "Or BSTX red color base
 * stat total number". The tracker states the rule and nothing more (the IronMON dev team, 2026-09-30): an X after BST
 * and the number in red, on your Pokemon while it is still the species it joined the run as, and on a wild one, which
 * would join as it is. A trainer's Pokemon cannot join, so it gets none.
 *
 * Which modes, from ironmon-ref/IRONMON-MODES-REPORT.md: Kaizo and the modes built on it (Survival, Super Kaizo,
 * Kaizo Doubles, Survival Revival) from 599; their Nat. Dex versions, which "also ban 600+ BST", from 600; Evo Kaizo,
 * whose lab Pokemon are legal "up to 600 BST", from 601 on your own and Kaizo's line on a wild one. Standard, Ultimate,
 * Journey and Chaos Kaizo (a ban list of its own) draw no line. Red, Blue and Yellow ban "BST over 480" by a per-game
 * rule (Dragonite, the three birds, Mew, Mewtwo), so their line is 481; Gold, Silver and Crystal take Kaizo's. A Game Boy
 * Pokemon has no personality value, so it is followed by its trainer id and DVs instead, which an evolution keeps
 * (Gen12Nuzlocke.partyId, as the Nuzlocke ledger does).
 *
 * MaxDex is the one build held to the Nat. Dex page's older section, "v1.0.0 to v1.1.3 only", the version it is built on
 * (Blake, 2026-10-03: "Max dex is allowed a bst 600 pokemon"); see [maxDexLines]. KaizoCore's Nat. Dex games are 1.2.1
 * and keep the v1.2.0+ lines above.
 */
object BstRule {
    private val FROM_KAIZO = setOf("kaizo", "survival", "superkaizo", "kaizodoubles", "survivalrevival")

    /**
     * The BST from which a Pokemon is banned: [own] for yours, [wild] for one you could catch, and [legendary] for a
     * legendary of yours however it came to its form, evolving included (MaxDex's line; null everywhere else, where an
     * evolution into any form is fine).
     */
    data class Lines(val own: Int, val wild: Int, val legendary: Int? = null)

    /**
     * The run's lines, by its [mode] ("kaizo", "evokaizo"); null where it draws none. [gen1]: Red, Blue or Yellow.
     * [maxDex]: MaxDex 1.0, which follows the Nat. Dex 1.1.3 rules ([maxDexLines]) though it is a Nat. Dex build too.
     * [hns]: Heart & Soul, whose Nat. Dex runs draw the line at 601.
     */
    fun lines(mode: String?, natDex: Boolean, gen1: Boolean = false, maxDex: Boolean = false, hns: Boolean = false): Lines? {
        if (maxDex) return maxDexLines(mode)
        // Heart & Soul with the Nat. Dex pool (Blake, 2026-10-07): 600 BST and below allowed, 601 and up banned. Emerald
        // and FireRed Nat. Dex keep their books' 600.
        val kaizo = when { gen1 -> 481; natDex && hns -> 601; natDex -> 600; else -> 599 }
        return when {
            mode == "evokaizo" -> Lines(own = 601, wild = kaizo)
            mode in FROM_KAIZO -> Lines(kaizo, kaizo)
            else -> null
        }
    }

    /**
     * MaxDex's lines, from the Nat. Dex Ruleset Changes page's "v1.0.0 to v1.1.3 only" section (tools/upr-settings/
     * community/Nat.-Dex-Ruleset-Changes.md): "BST limit for Kaizo, Survival and Super Kaizo is 599, or 600 for starter
     * Pokemon, including legendaries. 600+ BST mons may be obtained through evolution, except for 601+ BST legendaries."
     * So a starter of 600 keeps no X and one of 601 has it, a wild one is legal under 600, and evolving past 600 is fine
     * unless the Pokemon becomes a legendary of 601 or more. The tracker cannot tell your starter from a Pokemon you were
     * given, so every Pokemon of yours takes the starter's line, as Evo Kaizo's lab line is drawn above; MaxDex's own
     * randomizer keeps every Pokemon of 600 or more out of the wild. Standard and Ultimate: "the BST limit is 640
     * inclusive, and the Legendary evolution exception does not apply" ("acquire a legal Arceus by evolving a
     * Vigoroth"). The section names no Evo Kaizo, which keeps the Nat. Dex line. MaxDex ships Kaizo only.
     */
    private fun maxDexLines(mode: String?): Lines? = when {
        mode in FROM_KAIZO -> Lines(own = 601, wild = 600, legendary = 601)
        mode == "evokaizo" -> Lines(own = 601, wild = 600)
        mode == "standard" || mode == "ultimate" -> Lines(own = 641, wild = 641)
        else -> null
    }

    /** At or past [line], and still the species it joined as (a [joinedAs] of null: a wild one, joining as it is). */
    fun breaks(bst: Int?, line: Int?, joinedAs: Int?, species: Int): Boolean =
        bst != null && bst > 0 && line != null && bst >= line && (joinedAs == null || joinedAs == species)

    /** The X on a wild opponent. */
    fun wildBreaks(bst: Int?, lines: Lines?): Boolean = breaks(bst, lines?.wild, null, 0)

    /** What the rule sheet adds where a run has a [Lines.legendary] line. */
    fun legendarySays(line: Int): String = "A legendary at BST $line or above is not allowed even then."
}
