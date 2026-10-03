package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind

/**
 * The choices PREPARE offers for a dump, per game, from the official settings
 * page (tools/upr-settings/official-settings.md, read 2026-09-08) and the
 * Super Kaizo repo:
 *
 * - Red, Blue, Yellow, Gold, Silver, Crystal: the pseudo-fluctuating growth
 *   patch. Required for Crystal ("you must first apply"); Gold and Silver "may
 *   not work completely" without one; for Gen 1 it is one of the page's two
 *   ways (the other is a second settings pass, Randomizers.gen1). Named for
 *   what it is: it applies to every mode, not only Kaizo. It was the default here until 2026-09-30.
 * - FireRed 1.0, LeafGreen, Emerald: the Super Kaizo Smart AI patch, an option
 *   beside Standard (and Nat. Dex where it exists). Never stacked on Nat. Dex.
 * - HeartGold: IronMON HGSS (xdelta), its quality-of-life patch, and the Super Kaizo 0.0.3 patch (xdelta), two
 *   options beside Standard.
 * - Platinum: the Platinum Super Kaizo 1.0 patch (xdelta), the same way.
 * - Everything else: stored as is. Black 2 / White 2 Kaizo needs no patch.
 *
 * Standard is the first option and the one the page opens on (2026-09-30, UX audit P0-10): Nat. Dex came first for
 * FireRed 1.1 and Emerald, and the Gen 1 growth patch for those, so a player who was not sure and tapped Prepare got a
 * patched game. Gold, Silver and Crystal are the exception: the rules want the growth patch there (Crystal must have
 * it), so it is their first option and default (IronMON rules check R3, the same day). See [default]. A bundled patch
 * is named by the base kind id so the asset and the option can never drift apart.
 */
object PrepOptions {
    /**
     * MAXDEX: Trip's MaxDex patch applied to FireRed 1.1 (PrepRun): built in since rc34, or the same file where a player
     * added it to the library before then (MaxDexInfo.patchFile).
     */
    enum class Mode { STANDARD, NATDEX, PATCH, MAXDEX }

    const val STANDARD_LABEL = "Standard (the game as it is)"
    const val NATDEX_LABEL = "Nat. Dex (adds Pokémon from later games)"

    /**
     * One plain line under each choice. The labels alone ("Pseudo-fluctuating
     * growth patch", "Nat. Dex") meant nothing to a newcomer and
     * nothing said which was the normal pick (audit, 2026-09-27).
     */
    fun describe(o: Option, k: RomKind? = null): String = when (o.mode) {
        Mode.STANDARD -> if (k != null && GrowthPatch.buildFor(k) != null) "The game as it is. For Kaizo IronMON, pick the growth patch."
            else "Pick this if you are not sure."
        // What it adds, not just when to pick it (Blake, 2026-09-30); the full list is NatDexInfo.lines.
        Mode.NATDEX -> NatDexInfo.SHORT
        Mode.MAXDEX -> MaxDexInfo.SHORT
        Mode.PATCH -> when (o.out?.patchTag) {
            "pseudofluct" -> if (o.out.generation == Generation.GB1)
                "Adds the experience curve this game lacks, so a run needs only the first official settings pass. Without it, runs take the second pass instead."
            else "Adds the experience curve this game lacks. The IronMON rules need it on Crystal so nothing evolves into a legendary. Pick this if you are not sure."
            "smartai" -> "Trainers pick their moves more cleverly. Needed for Super Kaizo."
            "superkaizo" -> "The Super Kaizo patch for this game. Needed for Super Kaizo."
            "faster" -> when (o.out.baseId) {
                RomKind.EMERALD_U.id -> "Shorter intro, instant healing, hidden items marked, and the 6% trainer level increase Kaizo, Survival and Super Kaizo use."
                RomKind.BLACK2_U.id, RomKind.WHITE2_U.id -> "Skips most cutscenes. Every battle stays."
                else -> "Hidden items marked, instant healing at the PC, shorter errands."
            }
            "faster121" -> "The same quality of life without the level increase. The one for Standard and Ultimate."
            "fasterpwt" -> "Also skips the Driftveil tournament and its three battles."
            "ironmon" -> "Skips the intro, shortens the talk until Goldenrod, and speeds up walking and battle animations."
            else -> "Applies a patch before randomizing."
        }
    }

    data class Option(val mode: Mode, val label: String, val asset: String? = null, val out: RomKind? = null) {
        val id: String get() = out?.id ?: mode.name
    }

    /** The option Prepare opens on and runs unless the player picks another: the first, Standard except on Gold, Silver and Crystal. */
    fun default(k: RomKind): Option = forKind(k).first()

    fun forKind(k: RomKind): List<Option> {
        if (k.isNatDex || k.patchTag != null) return listOf(Option(Mode.STANDARD, "Already patched. Store as is."))
        val standard = Option(Mode.STANDARD, STANDARD_LABEL)
        fun patch(label: String, out: RomKind, ext: String) = Option(Mode.PATCH, label, "${out.patchTag}-${k.id}.$ext", out)
        val pf = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "pseudofluct" }
        val smart = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "smartai" }
        val sk = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "superkaizo" }
        fun tagged(tag: String) = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == tag }
        val faster = tagged("faster")
        // Each quality-of-life patch in the format its author ships.
        val fasterName = when (k.id) {
            RomKind.EMERALD_U.id -> "Faster Emerald 1.3.2"
            RomKind.BLACK2_U.id, RomKind.WHITE2_U.id -> "Faster B2W2"
            else -> "Faster FireRed"
        }
        val fasterExt = when (k.id) { RomKind.EMERALD_U.id -> "ups"; RomKind.BLACK2_U.id, RomKind.WHITE2_U.id -> "xdelta"; else -> "ips" }
        // Gold, Silver and Crystal: the growth patch first, as the rules want it (GrowthPatch).
        val growthFirst = pf != null && GrowthPatch.buildFor(k) != null
        return buildList {
            if (growthFirst) add(patch("Pseudo-fluctuating growth patch", pf!!, "bps"))
            add(standard)
            if (k.natDexCapable) add(Option(Mode.NATDEX, NATDEX_LABEL))
            if (pf != null && !growthFirst) add(patch("Pseudo-fluctuating growth patch", pf, "bps"))
            if (faster != null) add(patch("$fasterName: quality-of-life patch", faster, fasterExt))
            tagged("faster121")?.let { add(patch("Faster Emerald 1.2.1: for Standard and Ultimate", it, "ips")) }
            tagged("fasterpwt")?.let { add(patch("Faster B2W2, tournament skipped", it, "xdelta")) }
            // HeartGold's quality-of-life patch goes by its own name, IronMON HGSS (PyroMikeGit, 2026-10-02).
            tagged("ironmon")?.let { add(patch("IronMON HGSS: quality-of-life patch", it, "xdelta")) }
            if (smart != null) add(patch("Super Kaizo: Smart AI patch", smart, "ips"))
            // Each Super Kaizo patch under its own release number: HeartGold 0.0.3 (PyroMikeGit), Platinum 1.0 (SentorG).
            if (sk != null) add(patch(if (k.id == RomKind.PLATINUM_U.id) "Super Kaizo 1.0 patch" else "Super Kaizo 0.0.3 patch", sk, "xdelta"))
            // MaxDex 1.0 (Tripc423/Maxdex), last: Trip's patch, bundled since rc34 and named as every bundled patch is.
            MaxDexInfo.buildOf(k)?.let { add(Option(Mode.MAXDEX, MaxDexInfo.LABEL, "maxdex-${k.id}.bps", it)) }
        }
    }
}
