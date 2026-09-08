package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind

/**
 * What PREPARE will do for a given game, stated per game (2026-09-07). The
 * screen used to promise "choose Nat. Dex or Standard" to every dump; only
 * Emerald and FireRed v1.1 have a Nat. Dex patch, Gen 1 is randomized in two
 * passes, and a DS game is never patched at all.
 */
object PrepPlan {
    fun lines(k: RomKind): List<String> {
        val family = "Settings files: ${k.family}."
        return when {
            k.isNatDex -> listOf("Already a Nat. Dex build. Stored as is, ready to randomize.", "Settings files: ${k.family} Nat. Dex.")
            k.natDexCapable -> listOf("Nat. Dex: the 1.2.1 patch is applied and the result stored. Standard: the clean dump is stored.", family)
            k.id == "firered-u-v10" -> listOf("Standard only: Nat. Dex needs FireRed v1.1, and this is v1.0.", family)
            k.patchTag != null -> listOf("Already carries the ${k.displayName.substringAfter(" + ")} patch. Stored as is, ready to randomize.", family)
            k.generation == Generation.GB1 -> listOf("Kaizo: the pseudo-fluctuating growth patch is applied first, as the official page requires. Vanilla: the clean dump is stored.", "Every Gen 1 run is randomized in two passes (PART 1, then PART 2), as the page requires.", family)
            k.generation == Generation.GBC2 -> listOf("Kaizo: the pseudo-fluctuating growth patch is applied first. The official page requires it for Crystal; Gold and Silver strings may not work completely without it. Vanilla: the clean dump is stored.", family)
            k.generation == Generation.GBA3 && PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.PATCH } -> listOf("Standard: the clean dump is stored. Super Kaizo: the Smart AI patch is applied instead (every trainer has Smart AI, as that ruleset requires).", family)
            k.generation == Generation.GBA3 -> listOf("Stored as a standard base. No patch applies to this game.", family)
            k.id == RomKind.HEARTGOLD_U.id -> listOf("Standard: the clean dump is stored. Super Kaizo: the 0.0.3 patch is applied instead.", family, "A DS game wants a phone with at least 3 GB of RAM.")
            k.generation == Generation.NDS4 || k.generation == Generation.NDS5 -> listOf("Stored as a standard base. Kaizo on this game needs no patch; the randomizer works on the dump directly.", family, "A DS game wants a phone with at least 3 GB of RAM.")
            else -> listOf(family)
        }
    }
}
