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

    /** Community difficulty order; anything unlisted goes last, alphabetical. */
    private val ORDER = listOf(
        "standard", "kaizo", "superkaizo", "ultimate", "survival", "survivalrevival",
        "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney",
    )

    fun isCompatible(kind: RomKind, preset: File): Boolean {
        val i = RnqsInfo.parse(preset.name)
        if (i.secondPass) return false   // applied by Randomizers, not picked
        return i.gameTag == kind.family && i.natDex == kind.isNatDex
    }

    fun forRom(kind: RomKind, settings: List<File>): List<Ruleset> {
        val eligible = settings.mapNotNull { f ->
            val i = RnqsInfo.parse(f.name)
            if (i.ruleset != null && isCompatible(kind, f)) i.ruleset to f else null
        }
        return eligible.groupBy({ it.first }, { it.second }).map { (key, files) ->
            // An untouched preset beats an edited copy; then the shortest,
            // then alphabetical - deterministic, and the plain file wins.
            val ranked = files.sortedWith(
                compareBy<File>({ '(' in it.name }, { it.name.length }, { it.name })
            )
            Ruleset(key, RnqsInfo.rulesetLabel(key), ranked.first(), ranked.drop(1))
        }.sortedWith(compareBy({ ORDER.indexOf(it.key).let { if (it < 0) ORDER.size else it } },
                               { it.key }))
    }

    /** The mode a given preset belongs to, if it is one of [modes]. */
    fun modeOf(modes: List<Ruleset>, preset: File?): Ruleset? = preset?.let { p ->
        modes.firstOrNull { m -> m.preset.name == p.name || m.alternatives.any { it.name == p.name } }
    }
}
