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
            k.generation == Generation.GB1 -> listOf("Stored as a standard base. Gen 1 has no exp-curve option, so every run is randomized in two passes (PART 1, then PART 2), as the official settings page requires.", family)
            k.generation == Generation.GBC2 -> listOf("Stored as a standard base. No patch applies to Gen 2.", family)
            k.generation == Generation.GBA3 -> listOf("Stored as a standard base. No Nat. Dex patch exists for this game.", family)
            k.generation == Generation.NDS4 || k.generation == Generation.NDS5 -> listOf("Stored as a standard base. DS games are never patched; the randomizer works on the dump directly.", family, "A DS game wants a phone with at least 3 GB of RAM.")
            else -> listOf(family)
        }
    }
}
