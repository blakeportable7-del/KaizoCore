package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File

/**
 * One IronMON mode a ROM can be played in, backed by a preset that exists.
 *
 * [preset] is the file the mode selects; [alternatives] are other presets for
 * the same mode and ROM (an edited copy, an older version) that the Settings
 * list still offers individually.
 */
data class Ruleset(
    val key: String,
    val label: String,
    val preset: File,
    val alternatives: List<File> = emptyList(),
)

/**
 * The modes available for a ROM, derived from the preset files themselves.
 *
 * Rulesets are data, not code (OVERHAUL-PLAN 0.3) - and the data already
 * exists: every preset name carries its game tag, Nat. Dex flag and ruleset,
 * which RnqsInfo reads and which was validated against 72 real files. So a
 * mode is "a ruleset for which a preset matching this ROM's family and
 * Nat. Dex-ness exists". Import a Survival preset and Survival appears; no
 * catalogue to keep in sync, and no mode is ever offered that cannot run.
 *
 * The pairing guard still applies underneath: a preset is only eligible when
 * its family and Nat. Dex flag both match the ROM, which is the same rule
 * RunScreen.pairingProblem enforces.
 */
object RulesetCatalog {

    /**
     * The order the rules build (2026-09-30, UX audit P0-12). Standard is the base, Ultimate adds rules to it and
     * Kaizo adds to both: every file in assets/rulesets opens "Every ruleset builds on the ones before it" and holds
     * those sections in that order. Super Kaizo adds to Kaizo. Survival, Survival Revival, Kaizo Doubles, Chaos
     * Kaizo, Evo Kaizo and IronMON Journey each start from Kaizo, not from Super Kaizo (their files hold Standard,
     * Ultimate and Kaizo, then their own section), so they follow it. The row was in "difficulty order", which put
     * Ultimate after Super Kaizo, and the in-run rules box (Rules.order) already listed the first four this way.
     * Anything unlisted goes last, alphabetical.
     */
    private val ORDER = listOf(
        "standard", "ultimate", "kaizo", "superkaizo", "survival", "survivalrevival",
        "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney",
    )

    /** Every mode the row can show, in the order it shows them. */
    val keys: List<String> get() = ORDER

    /** The place [key] has in the row: its index in the order, after every listed mode when it is not listed. */
    fun rank(key: String): Int = ORDER.indexOf(key).let { if (it < 0) ORDER.size else it }

    // Reads the file's sidecar too (RnqsInfo.of), not just its name: a preset
    // saved under a plain name kept its family and Nat. Dex flag there
    // (2026-09-27, audit).
    fun isCompatible(kind: RomKind, preset: File): Boolean {
        val i = RnqsInfo.of(preset)
        if (i.appliedByApp) return false   // applied by Randomizers, not picked
        return i.gameTag == kind.family && i.natDex == kind.isNatDex
    }

    /**
     * Games a mode's own rules leave out, though they share its settings
     * family. Super Kaizo's README writes rules for Emerald (not Ruby or
     * Sapphire) and Platinum (not Diamond or Pearl), and no Smart AI build
     * exists for those four, so the Mode row does not offer it there. A
     * player can still pick the file from the settings list.
     */
    private val UNDEFINED = mapOf(
        "superkaizo" to setOf(RomKind.RUBY_U.id, RomKind.SAPPHIRE_U.id, RomKind.DIAMOND_U.id, RomKind.PEARL_U.id),
    )

    /** Whether [key]'s rules cover [kind]'s game. */
    fun defined(kind: RomKind, key: String): Boolean = (kind.baseId ?: kind.id) !in UNDEFINED[key].orEmpty()

    /**
     * Why a Super Kaizo run on [kind] is not what the rules describe, or null
     * when it is. The rules give every trainer smart AI through a ROM patch;
     * a build made with one (patchTag "smartai" or "superkaizo") or a Nat. Dex
     * build (its Super Kaizo file turns on the fork's Smart AI Mode) has it.
     * A warning, never a refusal: the player's choice stands.
     */
    fun superKaizoWarning(kind: RomKind, key: String?): String? {
        if (key != "superkaizo" || kind.isNatDex || kind.patchTag == "smartai" || kind.patchTag == "superkaizo") return null
        val base = RomKind.byId(kind.baseId) ?: kind
        if (!defined(kind, "superkaizo")) return when (base.family) {
            "RSE" -> "Super Kaizo's rules are written for Emerald, and there is no Smart AI patch for Ruby or Sapphire."
            else -> "Super Kaizo's rules are written for Platinum, and there is no Smart AI patch for Diamond or Pearl."
        }
        val option = PrepOptions.forKind(base).firstOrNull { it.out?.patchTag == "smartai" || it.out?.patchTag == "superkaizo" }
            ?: return when (base.id) {
                RomKind.SOULSILVER_U.id -> "Super Kaizo gives every trainer smart AI through a patch, and the Super Kaizo patch is made for HeartGold only. On SoulSilver the trainers keep their normal AI."
                else -> "Super Kaizo gives every trainer smart AI through a patch, and this app has none for ${base.displayName.removePrefix("Pok\u00e9mon ")}. " +
                    "FireRed 1.0 and LeafGreen have one, and Nat. Dex's Super Kaizo file has smart AI built in. On this build the trainers keep their normal AI."
            }
        return "Super Kaizo gives every trainer smart AI through a patch, and this build does not have it. " +
            "Make that build on Library, Patched versions: \"${option.label}\". On this one the trainers keep their normal AI."
    }

    fun forRom(kind: RomKind, settings: List<File>): List<Ruleset> {
        val eligible = settings.mapNotNull { f ->
            val i = RnqsInfo.of(f)
            if (i.ruleset != null && isCompatible(kind, f) && defined(kind, i.ruleset)) i.ruleset to f else null
        }
        return eligible.groupBy({ it.first }, { it.second }).map { (key, files) ->
            // An untouched preset beats an edited copy; then the shortest,
            // then alphabetical - deterministic, and the plain file wins.
            val ranked = files.sortedWith(
                compareBy<File>({ '(' in it.name }, { it.name.length }, { it.name })
            )
            Ruleset(key, RnqsInfo.rulesetLabel(key), ranked.first(), ranked.drop(1))
        }.sortedWith(compareBy({ rank(it.key) }, { it.key }))
    }

    /** The mode a given preset belongs to, if it is one of [modes]. */
    fun modeOf(modes: List<Ruleset>, preset: File?): Ruleset? = preset?.let { p ->
        modes.firstOrNull { m -> m.preset.name == p.name || m.alternatives.any { it.name == p.name } }
    }

    /** Kaizo is what the Kaizo IronMON screen is named for, so it is the mode a game opens on. */
    const val OPENING_MODE = "kaizo"

    /**
     * The settings file a game opens on (2026-09-30, UX audit P0-12). It was the first mode in the row, which was
     * Standard, on a screen named Kaizo IronMON. Now: the file the player last picked for this game ([remembered]),
     * else the file of the run last started on it ([lastRun], for a phone with runs from before that was kept), else
     * the game's Kaizo mode, else the first mode in the row. A Nat. Dex game is only ever offered its own Nat. Dex
     * files ([forRom]), so its Kaizo is its Nat. Dex Kaizo. A file that is gone, or that is not this game's family
     * and Nat. Dex flag, is skipped, never kept.
     */
    fun openingFile(kind: RomKind, settings: List<File>, remembered: String?, lastRun: String?): File? {
        fun usable(name: String?): File? = name?.let { n -> settings.firstOrNull { it.name == n && isCompatible(kind, it) } }
        return usable(remembered) ?: usable(lastRun) ?: forRom(kind, settings).let { modes ->
            (modes.firstOrNull { it.key == OPENING_MODE } ?: modes.firstOrNull())?.preset
        }
    }

    /**
     * The line under "Mode" (2026-09-30, UX audit P0-12), true of every game's row: see [ORDER]. Each rules file
     * holds those sections, and RunScreenKaizoTest reads them back.
     */
    const val MODE_HEADER = "Standard is the base. Ultimate adds rules to it and Kaizo adds more. Every other mode starts from Kaizo."

    /** What a mode with no line of its own says (a key this catalogue does not know). */
    const val OTHER_LINE = "A variant of Kaizo. Read its rules first."

    /**
     * The line under the Mode row for [key] (2026-09-30, UX audit P0-12): ten chips carried no words. Each is
     * written from that mode's rules file in assets/rulesets, not from memory, and every claim in it is a rule the
     * files hold; RunScreenKaizoTest reads them back, so a rules file that changes fails there.
     *
     * Two things are not what they might be. Ultimate differs for a Nat. Dex build: its rules file says HM moves
     * may be used in battle there, as long as the moves were not taught with the HM items, so [natDex] gets its own
     * line. And Super Kaizo's line does not say it needs the patched game: a Nat. Dex build has smart AI in its own
     * Super Kaizo file, and [superKaizoWarning] says so, right under the line, for the builds that lack it.
     */
    fun modeLine(key: String, natDex: Boolean = false, family: String? = null): String =
        if (key == "ultimate" && natDex) ULTIMATE_NAT_DEX else FAMILY_LINES[key to family] ?: LINES[key] ?: OTHER_LINE

    /**
     * Where a game's own rules file changes a mode's line (2026-10-01, rules check): Black and White count Survival's
     * heals once you beat the trainer after N, and the Johto games add seven for Kanto after the Elite Four.
     */
    private val FAMILY_LINES = mapOf(
        ("survival" to "BW") to "Harder than Kaizo. Ten Pok\u00e9mon Center heals, counted once you beat the trainer after N, " +
            "and an eleventh after your eighth badge.",
        ("survival" to "GSC") to "Harder than Kaizo. Ten Pok\u00e9mon Center heals from your first trainer battle after the rival, " +
            "an eleventh at your eighth badge and seven more for Kanto.",
        ("survival" to "HGSS") to "Harder than Kaizo. Ten Pok\u00e9mon Center heals from your first trainer battle after the rival, " +
            "an eleventh at your eighth badge and seven more for Kanto.",
    )

    private const val ULTIMATE_NAT_DEX = "Standard, plus no moves taught by HM items in battle, no leaving a gym until you " +
        "beat every trainer in it, one visit per dungeon, and six Pok\u00e9mon in all."

    private val LINES = mapOf(
        "standard" to "The base rules. Catch or kill one Pok\u00e9mon per route. A Pok\u00e9mon that faints is gone for good. " +
            "No shops, except for Pok\u00e9 Balls and Repels.",
        "ultimate" to "Standard, plus no HM moves in battle, no leaving a gym until you beat every trainer in it, " +
            "one visit per dungeon, and six Pok\u00e9mon in all.",
        "kaizo" to "Standard and Ultimate, plus one Pok\u00e9mon at a time, no fighting wild Pok\u00e9mon and no healing items " +
            "outside of battle. Built to be very hard.",
        "superkaizo" to "Kaizo, plus trainers that pick their moves more cleverly, gym leaders with six Pok\u00e9mon, " +
            "and a switch to a new Pok\u00e9mon about halfway through.",
        "survival" to "Harder than Kaizo. Ten Pok\u00e9mon Center heals, counted from your first trainer battle after the rival, " +
            "and an eleventh after your eighth badge.",
        "survivalrevival" to "Kaizo with a tight healing budget: five Pok\u00e9mon Center heals after your first badge, " +
            "and one more after your eighth.",
        "kaizodoubles" to "Kaizo, but every trainer battle is a double battle. The run ends if either of your two " +
            "Pok\u00e9mon faints.",
        "chaoskaizo" to "Kaizo with random Pok\u00e9mon types, random move power, accuracy and PP, and looser item rules. " +
            "Its rules are marked as a work in progress.",
        "evokaizo" to "Kaizo, but your Pok\u00e9mon evolves on every level up and you cannot cancel it.",
        "ironmonjourney" to "Kaizo with one Pok\u00e9mon: any starter you like is your whole team, apart from emergency swaps. " +
            "No fighting wild Pok\u00e9mon, and every TM can be learned.",
    )
}
