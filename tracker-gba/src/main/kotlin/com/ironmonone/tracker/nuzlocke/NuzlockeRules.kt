package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.Gen3Types

private const val PKMN = "Pokémon"

/**
 * The presets a Nuzlocke run can start from (2026-09-29). Only two rules are shared by every community that
 * plays one (first wild Pokemon per area, a faint is a death), so a preset is nothing more than a set of
 * defaults for the switches in [NuzlockeRules]; the player can flip any of them before the run starts
 * (docs/research/nuzlocke-variants.md, "Design notes": presets that set toggles, not hard-coded rules).
 */
enum class NuzlockePreset(val key: String, val label: String, val blurb: String) {
    STANDARD(
        "standard", "Standard",
        "The two core rules and the common clauses: one catch per area, a faint is a death, a whiteout ends the run.",
    ),
    HARDCORE(
        "hardcore", "Hardcore",
        "Standard, plus level caps at every boss, no items in battle and the Set battle style.",
    ),
    RANDOMIZER(
        "randomizer", "Randomizer",
        "Standard on a randomized game. It starts a new randomized run, and boss levels are read from that game (on a DS game the standard table is used).",
    ),
    MONOTYPE(
        "monotype", "Monotype",
        "Standard, but only $PKMN of one type you pick count as encounters, and only they belong on the team.",
    ),
    WEDLOCKE(
        "wedlocke", "Wedlocke",
        "Standard with pairs: one male and one female. You skip the gender you already have more of, and genderless $PKMN cannot be caught.",
    ),
    GENLOCKE(
        "genlocke", "Genlocke",
        "Standard, and the survivors at the Champion are saved so the next game can carry them on.",
    );

    companion object {
        fun byKey(key: String?): NuzlockePreset? = entries.firstOrNull { it.key == key }
    }
}

/** How the Safari Zone is split into areas. */
enum class SafariRule(val key: String, val label: String) {
    PER_ZONE("zone", "Each zone is its own area"),
    ONE_AREA("one", "The whole Safari Zone is one area");

    companion object {
        fun byKey(key: String?): SafariRule = entries.firstOrNull { it.key == key } ?: PER_ZONE
    }
}

enum class ClauseGroup(val title: String) {
    CORE("The rules"),
    OPTIONS("Clauses"),
    HARDCORE("Hardcore"),
    VARIANT("Variants"),
}

/**
 * One on/off switch, with the words the screens show. Kept beside the rules so the start screen and the
 * ledger's rules page read the same list and a new switch cannot be added to one and forgotten in the other.
 */
class Clause(
    val key: String,
    val group: ClauseGroup,
    val title: String,
    val detail: String,
    val get: (NuzlockeRules) -> Boolean,
    val set: (NuzlockeRules, Boolean) -> NuzlockeRules,
)

/**
 * Every clause of a run, each a switch with the preset's default (see [forPreset]). The defaults are the
 * research's suggested defaults (docs/research/nuzlocke-gen1-3.md 1.3 and nuzlocke-variants.md 2.4):
 * gifts, fossils, eggs, trades and the starter free, statics free, a whiteout ends the run, the Safari Zone
 * split by zone, cave floors merged into one area, fishing shared with the area.
 *
 * Nothing here blocks the game. The rules only decide what the ledger records and what it warns about.
 */
data class NuzlockeRules(
    val preset: NuzlockePreset = NuzlockePreset.STANDARD,
    // The two core rules, and the two things every player has to settle.
    val firstEncounter: Boolean = true,
    val faintIsDeath: Boolean = true,
    val whiteoutEndsRun: Boolean = true,
    /** The rules begin once the bag holds Poke Balls (Bulbapedia "Slow Start"), so Route 1 before the parcel is free. */
    val slowStart: Boolean = true,
    // Common clauses.
    val dupes: Boolean = true,
    /** Off: a line stays a dupe after all of its Pokemon died. On: only a line you still own counts as one. */
    val dupesOwnedOnly: Boolean = false,
    val shinyClause: Boolean = true,
    val giftsCount: Boolean = false,
    val staticsCount: Boolean = false,
    val floorsMerged: Boolean = true,
    val waterSeparate: Boolean = false,
    val escapeClause: Boolean = false,
    val nicknames: Boolean = true,
    val safari: SafariRule = SafariRule.PER_ZONE,
    // Hardcore.
    val levelCaps: Boolean = false,
    val capExtraBosses: Boolean = false,
    val noItemsInBattle: Boolean = false,
    /** A reminder only: the game's option is read, never written. */
    val setStyle: Boolean = false,
    // Variants.
    /** Gen 3 type id of the one type a Monotype run may catch, or null. */
    val monotypeType: Int? = null,
    val wedlocke: Boolean = false,
    val genlocke: Boolean = false,
) {
    /** The switches as text lines for the ledger file: one "key value" per switch, in a fixed order. */
    fun toEntries(): List<Pair<String, String>> = buildList {
        add("preset" to preset.key)
        CLAUSES.forEach { add(it.key to it.get(this@NuzlockeRules).toString()) }
        add("safari" to safari.key)
        add("monotypeType" to (monotypeType?.toString() ?: ""))
    }

    /** What the player is asked at the start of a run, in a sentence: "Standard, changed: dupes clause off". */
    fun changesFromPreset(): List<Clause> {
        val base = forPreset(preset, monotypeType)
        return CLAUSES.filter { it.get(this) != it.get(base) }
    }

    val monotypeLabel: String? get() = monotypeType?.let { Gen3Types.name(it) }

    companion object {
        /** Each boolean switch, in the order the screens list them. */
        val CLAUSES: List<Clause> = listOf(
            Clause("firstEncounter", ClauseGroup.CORE, "First encounter per area",
                "Only the first wild $PKMN you meet in each area can be caught.",
                { it.firstEncounter }, { r, v -> r.copy(firstEncounter = v) }),
            Clause("faintIsDeath", ClauseGroup.CORE, "A fainted $PKMN is dead",
                "It goes to the graveyard the moment it faints. No revives.",
                { it.faintIsDeath }, { r, v -> r.copy(faintIsDeath = v) }),
            Clause("whiteoutEndsRun", ClauseGroup.CORE, "A whiteout ends the run",
                "Your whole party fainted. Off: the run goes on with the $PKMN you have boxed.",
                { it.whiteoutEndsRun }, { r, v -> r.copy(whiteoutEndsRun = v) }),
            Clause("slowStart", ClauseGroup.CORE, "Slow start",
                "The rules begin once you hold Poke Balls, so the first routes are free.",
                { it.slowStart }, { r, v -> r.copy(slowStart = v) }),
            Clause("dupes", ClauseGroup.OPTIONS, "Dupes clause",
                "A first encounter from an evolution line you already caught does not count, and the area stays open.",
                { it.dupes }, { r, v -> r.copy(dupes = v) }),
            Clause("dupesOwnedOnly", ClauseGroup.OPTIONS, "Dupes: only lines you still own",
                "A line whose $PKMN all died is open again. Off: a caught line stays a dupe for the whole run.",
                { it.dupesOwnedOnly }, { r, v -> r.copy(dupesOwnedOnly = v) }),
            Clause("shinyClause", ClauseGroup.OPTIONS, "Shiny clause",
                "A shiny is a free extra. It does not use up the area.",
                { it.shinyClause }, { r, v -> r.copy(shinyClause = v) }),
            Clause("giftsCount", ClauseGroup.OPTIONS, "Gifts count",
                "A gift $PKMN uses up the area where you got it. Off: gifts are free. Your starter never counts.",
                { it.giftsCount }, { r, v -> r.copy(giftsCount = v) }),
            Clause("staticsCount", ClauseGroup.OPTIONS, "Static $PKMN count",
                "A set battle (Snorlax, a legendary, a roamer) uses up its area. Off: they are free.",
                { it.staticsCount }, { r, v -> r.copy(staticsCount = v) }),
            Clause("floorsMerged", ClauseGroup.OPTIONS, "Cave floors are one area",
                "Every floor of a cave or tower shares one encounter. Off: each floor is its own area.",
                { it.floorsMerged }, { r, v -> r.copy(floorsMerged = v) }),
            Clause("waterSeparate", ClauseGroup.OPTIONS, "Water is its own area",
                "Surfing and fishing get an encounter of their own, apart from the grass.",
                { it.waterSeparate }, { r, v -> r.copy(waterSeparate = v) }),
            Clause("escapeClause", ClauseGroup.OPTIONS, "Escape clause",
                "If the wild $PKMN flees, the area stays open.",
                { it.escapeClause }, { r, v -> r.copy(escapeClause = v) }),
            Clause("nicknames", ClauseGroup.OPTIONS, "Nickname reminder",
                "A warning when a caught $PKMN in your party still has its species name.",
                { it.nicknames }, { r, v -> r.copy(nicknames = v) }),
            Clause("levelCaps", ClauseGroup.HARDCORE, "Level caps",
                "No $PKMN above the highest level on the next boss's team. Checked when a boss battle starts; passing it inside the battle is allowed.",
                { it.levelCaps }, { r, v -> r.copy(levelCaps = v) }),
            Clause("capExtraBosses", ClauseGroup.HARDCORE, "Rivals and team leaders are bosses",
                "The cap also applies before rival and villain team battles.",
                { it.capExtraBosses }, { r, v -> r.copy(capExtraBosses = v) }),
            Clause("noItemsInBattle", ClauseGroup.HARDCORE, "No items in battle",
                "A warning when the bag loses an item during a battle. Poke Balls are fine.",
                { it.noItemsInBattle }, { r, v -> r.copy(noItemsInBattle = v) }),
            Clause("setStyle", ClauseGroup.HARDCORE, "Set battle style",
                "A warning while the game's battle style option is on Shift.",
                { it.setStyle }, { r, v -> r.copy(setStyle = v) }),
            Clause("wedlocke", ClauseGroup.VARIANT, "Wedlocke pairs",
                "One male and one female. You skip encounters of the gender you have more of, and genderless $PKMN cannot be caught.",
                { it.wedlocke }, { r, v -> r.copy(wedlocke = v) }),
            Clause("genlocke", ClauseGroup.VARIANT, "Genlocke",
                "The survivors at the Champion are saved so the next game can carry them on.",
                { it.genlocke }, { r, v -> r.copy(genlocke = v) }),
        )

        /** The defaults of [preset]. Monotype needs the type the player picked. */
        fun forPreset(preset: NuzlockePreset, monotypeType: Int? = null): NuzlockeRules {
            val base = NuzlockeRules(preset = preset)
            return when (preset) {
                NuzlockePreset.STANDARD, NuzlockePreset.RANDOMIZER -> base
                NuzlockePreset.HARDCORE -> base.copy(levelCaps = true, noItemsInBattle = true, setStyle = true)
                NuzlockePreset.MONOTYPE -> base.copy(monotypeType = monotypeType)
                NuzlockePreset.WEDLOCKE -> base.copy(wedlocke = true)
                NuzlockePreset.GENLOCKE -> base.copy(genlocke = true)
            }
        }

        /** Rules from a file's "key value" pairs. A missing or unreadable switch keeps the Standard default. */
        fun fromEntries(entries: Map<String, String>): NuzlockeRules {
            val preset = NuzlockePreset.byKey(entries["preset"]) ?: NuzlockePreset.STANDARD
            var r = NuzlockeRules(preset = preset)
            for (c in CLAUSES) {
                val v = entries[c.key] ?: continue
                if (v == "true" || v == "false") r = c.set(r, v == "true")
            }
            return r.copy(
                safari = SafariRule.byKey(entries["safari"]),
                monotypeType = entries["monotypeType"]?.toIntOrNull()?.takeIf { it in 0..30 },
            )
        }
    }
}
