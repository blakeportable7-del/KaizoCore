package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind

/**
 * What PREPARE will do for a given game, stated per game (2026-09-07). The
 * screen used to promise "choose Nat. Dex or Standard" to every dump; only
 * Emerald and FireRed v1.1 have a Nat. Dex patch, Gen 1 is randomized in two
 * passes, and a DS game is never patched at all.
 *
 * Plain words only (audit, 2026-09-27): the "Settings files: RSE." line, patch
 * version numbers and "the official page" meant nothing to a player.
 */
object PrepPlan {
    private const val DS_RAM = "A DS game wants a phone with at least 3 GB of RAM."

    fun lines(k: RomKind): List<String> = when {
        k.isNatDex -> listOf("Already a Nat. Dex build. Stored as is, ready to randomize.")
        // FireRed 1.1 is the one dump with three: Nat. Dex, clean, and the
        // Faster FireRed quality-of-life patch.
        k.natDexCapable -> listOf(
            "Nat. Dex: the National Dex patch is applied and the result stored. Standard: the clean dump is stored." +
                (if (PrepOptions.forKind(k).any { it.out?.patchTag == "faster" })
                    " Faster FireRed: the quality-of-life patch is applied instead (marked hidden items, instant PC)." else ""),
        )
        k.id == "firered-u-v10" -> listOf("Standard only: Nat. Dex needs FireRed v1.1, and this is v1.0.")
        k.patchTag != null -> listOf("Already carries the ${k.displayName.substringAfter(" + ")} patch. Stored as is, ready to randomize.")
        k.generation == Generation.GB1 -> listOf(
            "Kaizo: the pseudo-fluctuating growth patch is applied first, as the IronMON rules require. Vanilla: the clean dump is stored.",
            "Every Gen 1 run is randomized in two passes (part 1, then part 2), as the rules require.",
        )
        k.generation == Generation.GBC2 -> listOf("Kaizo: the pseudo-fluctuating growth patch is applied first. The IronMON rules require it for Crystal; Gold and Silver may not work completely without it. Vanilla: the clean dump is stored.")
        k.generation == Generation.GBA3 && PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.PATCH } -> listOf("Standard: the clean dump is stored. Super Kaizo: the Smart AI patch is applied instead, so every trainer picks its best move and switches like a player would, as that ruleset requires.")
        k.generation == Generation.GBA3 -> listOf("Stored as a standard base. No patch applies to this game.")
        k.id == RomKind.HEARTGOLD_U.id -> listOf("Standard: the clean dump is stored. Super Kaizo: its patch is applied instead.", DS_RAM)
        k.generation == Generation.NDS4 || k.generation == Generation.NDS5 -> listOf("Stored as a standard base. Kaizo on this game needs no patch; the randomizer works on the dump directly.", DS_RAM)
        else -> listOf("Stored as is, ready to randomize.")
    }
}
