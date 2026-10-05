package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Banned held items and abilities, per mode, marked on your own Pokemon's card with the rules' X (Blake, 2026-10-04:
 * "depending on the mode you are running, should there be an X next to the item on the tracker if your pokemon is
 * holding a banned item? ... different modes have different banned items", then "and then banned abilities and
 * exceptions"). Banned moves are [MoveRule]'s; this adds the moves Huge Power and Pure Power ban ([physicalBan]).
 *
 * The mark states the rule and nothing more (the IronMON dev team, 2026-09-30): never what to use instead.
 * Every rule below is from the rulesets the app ships (app/src/main/assets/rulesets, built by tools/rules/build_rules.py
 * from valiant-code's IronMON rules gist, last changed 2025-02-23, and UTDZac's game-specific gist, 2026-07-05), whose
 * text is the same for every game family that has the mode. Where a mode's text says nothing, nothing is marked.
 * The PC tracker's GachaMon ratings (GachaMonRatingSystem.json, Rulesets) carry banned abilities too; where they and a
 * ruleset's own words differ, the ruleset wins, as for [MoveRule]: Survival bans its list from 400 BST with no evolving
 * exception, and Survival Revival keeps Kaizo's abilities only ("ALL PREVIOUS Standard, Ultimate, and Kaizo rules").
 */
object RuleMarks {
    /** One run: its mode ("kaizo", "survival", ...), and a Nat. Dex or MaxDex game. */
    data class Run(
        val mode: String, val natDex: Boolean, val family: String? = null,
        /**
         * This seed's evolutions by species name, from the run's randomizer log: true or false where the log says,
         * null where it does not know the name. Null itself when the log has no "Randomized Evolutions" (the game's own
         * evolutions stand, which the tracker already reads) or is not read yet.
         */
        val evolves: ((String) -> Boolean?)? = null,
    ) {
        /** Whether [species] will evolve further in this seed: the log's answer, else the tracker's ([tracker]). */
        fun canEvolve(species: String, tracker: Boolean): Boolean = evolves?.invoke(species) ?: tracker
    }

    /** [log]'s evolutions as [Run.evolves] wants them; null when the seed kept the game's own. */
    fun seedEvolutions(log: RandomizerLog): ((String) -> Boolean?)? {
        if (log.pokemon.none { it.evolutions.isNotEmpty() }) return null
        return { name -> log.pokemonNamed(name)?.evolutions?.isNotEmpty() }
    }

    private fun key(name: String) = MoveRule.key(name)

    // ---- Held items ----

    /** Standard: "Banned Items: Banned items: Lucky Egg, Sacred Ash." Sacred Ash does nothing held, so only Lucky Egg is marked. */
    private val STANDARD_ITEMS = listOf("Lucky Egg")
    /** Ultimate: "Ultimate Banned Items: Additionally banned items: Leftovers, Soul Dew, Everstone." */
    private val ULTIMATE_ITEMS = STANDARD_ITEMS + listOf("Leftovers", "Soul Dew", "Everstone")
    /** Chaos Kaizo: "Banned Items: Leftovers, Shell Bell, Lucky Egg, Everstone, Soul Dew, Sacred Ash" ("All items are allowed to be held, except..."). */
    private val CHAOS_ITEMS = listOf("Leftovers", "Shell Bell", "Lucky Egg", "Everstone", "Soul Dew")
    /** Survival Revival, The Tools of Survival: "The following Held items are still banned: Lucky egg, Leftovers, Shell Bell, focus band and brightpowder." */
    private val REVIVAL_ITEMS = listOf("Lucky Egg", "Leftovers", "Shell Bell", "Focus Band", "BrightPowder")
    /** Survival, Cut a move, Hold any Item: "any previously banned held item (except for Lucky Egg and Everstone) if you teach Cut". */
    private val SURVIVAL_CUT_ITEMS = listOf("Lucky Egg", "Everstone")

    /**
     * Kaizo's Held Items Restriction: "The only Held Items allowed are: Items that are consumed on use like berries or a
     * white herb, with the exception of Focus Sash which is banned. Evolution items. a Smoke Ball". The rule names a kind
     * of item, so this list is the held items that work while held and are never used up, by what each does in the game.
     * Not here, so never marked: berries, herbs and every other item used up when it works; evolution items (the rule
     * allows one held to evolve, which the tracker cannot tell from holding it for battle: King's Rock, Metal Coat,
     * Dragon Scale, Up-Grade, Deep Sea Tooth and Scale, Razor Claw and Fang, and the rest); a Smoke Ball; Mega Stones and
     * anything else not listed. Gen 2's names (Pink Bow, Polkadot Bow) and Nat. Dex's later items are here by their names.
     */
    internal val KEPT_WHILE_HELD = listOf(
        // Generations 2 and 3
        "Leftovers", "Shell Bell", "BrightPowder", "Quick Claw", "Choice Band", "Focus Band", "Scope Lens", "Lax Incense",
        "Sea Incense", "Exp. Share", "Amulet Coin", "Cleanse Tag", "Soothe Bell", "Macho Brace", "Lucky Egg", "Everstone",
        "Soul Dew", "Lucky Punch", "Metal Powder", "Thick Club", "Stick", "Leek", "Light Ball", "Silk Scarf", "Charcoal",
        "Mystic Water", "Magnet", "Miracle Seed", "NeverMeltIce", "Black Belt", "Poison Barb", "Soft Sand", "Sharp Beak",
        "TwistedSpoon", "SilverPowder", "Hard Stone", "Spell Tag", "Dragon Fang", "BlackGlasses", "Pink Bow", "Polkadot Bow",
        // Generation 4 on (Nat. Dex, MaxDex and the DS games)
        "Focus Sash", "Life Orb", "Choice Specs", "Choice Scarf", "Expert Belt", "Muscle Band", "Wise Glasses", "Wide Lens",
        "Zoom Lens", "Black Sludge", "Big Root", "Shed Shell", "Toxic Orb", "Flame Orb", "Iron Ball", "Lagging Tail",
        "Sticky Barb", "Grip Claw", "Binding Band", "Light Clay", "Damp Rock", "Heat Rock", "Smooth Rock", "Icy Rock",
        "Destiny Knot", "Metronome", "Assault Vest", "Rocky Helmet", "Eviolite", "Safety Goggles", "Protective Pads",
        "Heavy-Duty Boots", "Utility Umbrella", "Terrain Extender", "Float Stone", "Ring Target", "Quick Powder",
        "Power Weight", "Power Bracer", "Power Belt", "Power Lens", "Power Band", "Power Anklet", "Loaded Dice",
        "Covert Cloak", "Clear Amulet", "Punching Glove", "Ability Shield", "Adamant Orb", "Lustrous Orb", "Griseous Orb",
        "Odd Incense", "Rock Incense", "Rose Incense", "Wave Incense", "Full Incense", "Luck Incense", "Pure Incense",
        "Fairy Feather", "Flame Plate", "Splash Plate", "Zap Plate", "Meadow Plate", "Icicle Plate", "Fist Plate",
        "Toxic Plate", "Earth Plate", "Sky Plate", "Mind Plate", "Insect Plate", "Stone Plate", "Spooky Plate", "Draco Plate",
        "Dread Plate", "Iron Plate", "Pixie Plate",
    )
    private val KEPT_KEYS = KEPT_WHILE_HELD.map(::key).toSet()

    /** The modes that keep Kaizo's held item restriction ("All Kaizo Ironmon Rules are still in play" in Evo Kaizo). */
    private val KAIZO_HELD = setOf("kaizo", "kaizodoubles", "ironmonjourney", "superkaizo", "evokaizo", "survival")

    /**
     * The card's line for your Pokemon holding [item] in [run], null when the run allows it. [moves]: its move names, for
     * Survival's Cut rule. A Nat. Dex Survival run also needs the HM01 item before Cut counts, which the tracker does not
     * read, so there a Pokemon with Cut is not marked for the items Cut allows.
     */
    fun itemLine(run: Run?, item: String?, moves: List<String> = emptyList()): String? {
        if (run == null || item == null) return null
        val k = key(item)
        if (k.isEmpty() || k == "none") return null
        fun named(list: List<String>) = list.any { key(it) == k }
        val banned = when (run.mode) {
            "standard" -> named(STANDARD_ITEMS)
            "ultimate" -> named(ULTIMATE_ITEMS)
            "chaoskaizo" -> named(CHAOS_ITEMS)
            "survivalrevival" -> named(REVIVAL_ITEMS)
            "survival" -> if (moves.any { key(it) == "cut" }) named(SURVIVAL_CUT_ITEMS) else k in KEPT_KEYS
            in KAIZO_HELD -> k in KEPT_KEYS
            else -> false
        }
        if (!banned) return null
        return "Banned in this run: holding $item. ${labNote(run, "hold it")}"
    }

    // ---- Abilities ----

    /** Kaizo: "Using any physical move is banned while having Huge Power or Pure Power". */
    private val PHYSICAL = listOf("Huge Power", "Pure Power")
    /** Super Kaizo, "Additional Banned Abilities": Battle Armor / Shell Armor, Magic Guard (Pickup and Poison Heal are "okay"). */
    private val SUPER_KAIZO = listOf("Battle Armor", "Shell Armor", "Magic Guard")
    /** Survival: "If your pokemon has BST of 400 or more, the following abilities are banned: Huge Power/Pure Power, Battle Armor/Shell Armor, Parental Bond, Protean, Fur Coat, Magic Guard". */
    private val SURVIVAL = listOf("Huge Power", "Pure Power", "Battle Armor", "Shell Armor", "Parental Bond", "Protean", "Fur Coat", "Magic Guard")
    /** Kaizo and the modes that keep its Banned Abilities rule. Evo Kaizo: "All Abilities are LEGAL". */
    private val KAIZO_ABILITIES = setOf("kaizo", "kaizodoubles", "ironmonjourney", "superkaizo", "survivalrevival")

    /**
     * Kaizo's exceptions, which its rule gives every banned ability: "Banned abilities don't apply to: Pokémon with BST
     * 399 or lower; Pokémon with BST 400-410 (inclusive) that will eventually evolve". Nat. Dex (and MaxDex): "The BST
     * limit for banned Abilities such as Huge Power is increased to 420 inclusive." [bst] is the species as this seed
     * has it, the number the card shows; [canEvolve] is from this seed's evolutions ([Run.canEvolve]). An unknown BST is
     * not marked.
     */
    private fun kaizoExempt(run: Run, bst: Int?, canEvolve: Boolean): Boolean {
        val b = bst ?: return true
        if (run.natDex) return b <= 420
        return b <= 399 || (b <= 410 && canEvolve)
    }

    /** Kaizo's exception in words, for the line a tap shows. */
    private fun kaizoExceptionLine(run: Run): String =
        if (run.natDex) "Banned abilities don't apply to a Pokemon with BST 420 or lower."
        else "Banned abilities don't apply to a Pokemon with BST 399 or lower, or BST 400 to 410 that will evolve."

    /** Survival's own line: banned "If your pokemon has BST of 400 or more", 420 inclusive on Nat. Dex. */
    private fun survivalExempt(run: Run, bst: Int?): Boolean {
        val b = bst ?: return true
        return if (run.natDex) b <= 420 else b < 400
    }

    private fun survivalExceptionLine(run: Run): String =
        if (run.natDex) "This run bans it for a Pokemon with BST 421 or more." else "This run bans it for a Pokemon with BST 400 or more."

    /**
     * The card's line for your Pokemon's [ability] in [run], null when the run allows it or when the ban falls on its
     * moves instead: Huge Power and Pure Power are never marked themselves in Kaizo and the modes that keep its rule,
     * only the physical moves they ban ([physicalBan], Blake, 2026-10-04). [canEvolve]: it will evolve further in this
     * seed. Chaos Kaizo allows a banned ability while the Pokemon is not fully evolved or evolved into it, which the
     * tracker cannot tell, so Chaos Kaizo marks none.
     */
    fun abilityLine(run: Run?, ability: String?, bst: Int?, canEvolve: Boolean): String? {
        if (run == null || ability == null) return null
        val k = key(ability)
        if (k.isEmpty()) return null
        fun named(list: List<String>) = list.any { key(it) == k }
        val line = when {
            run.mode == "survival" ->
                if (named(SURVIVAL) && !survivalExempt(run, bst)) "Banned in this run: $ability. ${survivalExceptionLine(run)}" else null
            run.mode == "superkaizo" && named(SUPER_KAIZO) && !kaizoExempt(run, bst, canEvolve) ->
                "Banned in this run: $ability. ${kaizoExceptionLine(run)}"
            else -> null
        } ?: return null
        return "$line ${labNote(run, "use it")}"
    }

    /**
     * Kaizo's "Using any physical move is banned while having Huge Power or Pure Power (special moves are okay)", with
     * its exceptions: the reason a physical move of your Pokemon is banned, or null. Physical is the category the card
     * shows (by type in a Gen 3 game, by move where the build splits them: Nat. Dex, MaxDex, the DS games).
     */
    fun physicalBan(run: Run?, ability: String?, bst: Int?, canEvolve: Boolean): String? {
        if (run == null || ability == null || run.mode !in KAIZO_ABILITIES) return null
        if (PHYSICAL.none { key(it) == key(ability) } || kaizoExempt(run, bst, canEvolve)) return null
        return "a physical move with $ability. ${kaizoExceptionLine(run).removeSuffix(".")}"
    }

    /**
     * The exception every ruleset with these bans gives: "During the lab fight(s) only, you may hold banned items" and
     * "You may use banned abilities in the lab fight"; Black and White: "Abilities/Moves that are banned are usable for
     * the first three battles (including N)".
     */
    private fun labNote(run: Run, what: String): String =
        if (run.family == "BW") "You may $what in the first three battles, N's included."
        else "You may $what in the lab fight."
}

/**
 * The run in Play for [RuleMarks]: a Kaizo IronMON run on any console, with its mode; null otherwise, as for the BST X.
 * Its evolutions are the seed's, from the run's randomizer log once read (off the main thread).
 */
@Composable
fun ruleRunInPlay(attempt: Int): RuleMarks.Run? {
    val context = LocalContext.current
    val found = remember(attempt) {
        runCatching {
            val filesDir = context.applicationContext.filesDir
            val store = PrepStore(filesDir)
            val session = store.session()
            val kind = session.kind
            val mode = FavoriteBall.modeOf(store)
            if (PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON || kind == null || mode == null) null
            else Triple(RuleMarks.Run(mode, HnsPool.rulesNatDex(kind, filesDir), kind.family), store, kind)
        }.getOrNull()
    }
    val evolves = androidx.compose.runtime.produceState<((String) -> Boolean?)?>(null, found) {
        val (_, store, kind) = found ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { store.currentRunLogFor(kind)?.let { RandomizerLog.parse(it) }?.let { RuleMarks.seedEvolutions(it) } }.getOrNull()
        }
    }.value
    return found?.first?.copy(evolves = evolves)
}
