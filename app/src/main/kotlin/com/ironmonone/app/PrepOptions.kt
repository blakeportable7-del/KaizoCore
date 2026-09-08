package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind

/**
 * The choices PREPARE offers for a dump, per game, from the official settings
 * page (tools/upr-settings/official-settings.md, read 2026-09-08) and the
 * Super Kaizo repo:
 *
 * - Red, Blue, Yellow, Gold, Silver, Crystal: the pseudo-fluctuating growth
 *   patch. Required for Crystal ("you must first apply"), and the page's Gen 1
 *   strings are written for it; Gold and Silver "may not work completely"
 *   without one. Default on.
 * - FireRed 1.0, LeafGreen, Emerald: the Super Kaizo Smart AI patch, an option
 *   beside Standard (and Nat. Dex where it exists). Never stacked on Nat. Dex.
 * - HeartGold: the Super Kaizo 0.0.3 patch (xdelta), an option beside Standard.
 * - Everything else: stored as is. Black 2 / White 2 Kaizo needs no patch.
 *
 * The first option is the default. A bundled patch is named by the base kind
 * id so the asset and the option can never drift apart.
 */
object PrepOptions {
    enum class Mode { STANDARD, NATDEX, PATCH }

    data class Option(val mode: Mode, val label: String, val asset: String? = null, val out: RomKind? = null) {
        val id: String get() = out?.id ?: mode.name
    }

    fun forKind(k: RomKind): List<Option> {
        if (k.isNatDex || k.patchTag != null) return listOf(Option(Mode.STANDARD, "Already patched. Store as is."))
        val standard = Option(Mode.STANDARD, if (k.generation == Generation.GB1 || k.generation == Generation.GBC2) "Vanilla" else "Standard")
        fun patch(label: String, out: RomKind, ext: String) = Option(Mode.PATCH, label, "${out.patchTag}-${k.id}.$ext", out)
        val pf = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "pseudofluct" }
        val smart = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "smartai" }
        val sk = RomKind.allPatched.firstOrNull { it.baseId == k.id && it.patchTag == "superkaizo" }
        return buildList {
            if (pf != null) add(patch("Kaizo: pseudo-fluctuating patch", pf, "bps"))
            if (k.natDexCapable) add(Option(Mode.NATDEX, "Nat. Dex"))
            add(standard)
            if (smart != null) add(patch("Super Kaizo: Smart AI patch", smart, "ips"))
            if (sk != null) add(patch("Super Kaizo 0.0.3 patch", sk, "xdelta"))
        }
    }
}
