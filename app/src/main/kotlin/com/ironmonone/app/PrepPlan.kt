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
 *
 * Standard comes first in every line, as it does in the radio list (PrepOptions, 2026-09-30, UX audit P0-10), and
 * is said as "the game is stored as it is", the words of its label.
 */
object PrepPlan {
    private const val DS_RAM = "A DS game wants a phone with at least 3 GB of RAM."

    fun lines(k: RomKind): List<String> = when {
        k.isNatDex -> listOf("Already a Nat. Dex build. Stored as is, ready to randomize.")
        // FireRed 1.1 is the one dump with three: Nat. Dex, the game as it is, and the Faster FireRed
        // quality-of-life patch.
        k.natDexCapable -> listOf(
            "Standard: the game is stored as it is. Nat. Dex: the Nat. Dex patch is applied and the result stored, for the Nat. Dex modes." +
                (if (PrepOptions.forKind(k).any { it.out?.patchTag == "faster" })
                    " ${if (k.id == RomKind.EMERALD_U.id) "Faster Emerald" else "Faster FireRed"}: the quality-of-life patch is applied instead (marked hidden items, instant healing)." else ""),
        )
        // The one FireRed that cannot take Nat. Dex says why, and which file does (2026-09-30, UX audit P0-10): dumps of v1.1 are named "Rev 1".
        k.id == "firered-u-v10" -> listOf(
            if (PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.PATCH }) "Standard, or the Super Kaizo patch. Nat. Dex needs v1.1."
            else "Standard only. Nat. Dex needs v1.1.",
            "FireRed v1.1 is the one whose file name may say Rev 1.",
        )
        k.patchTag != null -> listOf("Already carries the ${k.displayName.substringAfter(" + ")} patch. Stored as is, ready to randomize.")
        // The settings page gives Gen 1 two ways: the patch with part 1, or part 1
        // then part 2. This said the patch was required and every run took both,
        // which made the patch do nothing (part 2 sets every curve to Slow).
        k.generation == Generation.GB1 -> listOf(
            "Standard: the game is stored as it is. Pseudo-fluctuating patch: the growth patch is applied first.",
            "The IronMON rules allow either way: the patch with the first settings pass, or no patch and two passes (part 1, then part 2). " +
                "A run on the patched game takes the first pass only, on the standard one both; the second pass is a switch in Kaizo IronMON.",
        )
        k.generation == Generation.GBC2 -> listOf("Standard: the game is stored as it is. Pseudo-fluctuating patch: the growth patch is applied first. The IronMON rules require it for Crystal; Gold and Silver may not work completely without it.")
        k.generation == Generation.GBA3 && PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.PATCH } -> listOf("Standard: the game is stored as it is. Super Kaizo: the Smart AI patch is applied instead, so every trainer picks its best move and switches like a player would, as that ruleset requires.")
        k.generation == Generation.GBA3 -> listOf("Stored as a standard base. No patch applies to this game.")
        k.id == RomKind.BLACK2_U.id || k.id == RomKind.WHITE2_U.id -> listOf("Standard: the game is stored as it is; Kaizo on this game needs no patch. Faster B2W2: the cutscene-skip patch is applied instead, with or without the Driftveil tournament.", DS_RAM)
        k.id == RomKind.HEARTGOLD_U.id -> listOf("Standard: the game is stored as it is. Super Kaizo: its patch is applied instead.", DS_RAM)
        k.id == RomKind.PLATINUM_U.id -> listOf("Standard: the game is stored as it is. Super Kaizo: its patch is applied instead, which gives every trainer smart AI. It is made for Platinum 1.0, the version this game is.", DS_RAM)
        k.generation == Generation.NDS4 || k.generation == Generation.NDS5 -> listOf("Stored as a standard base. Kaizo on this game needs no patch; the randomizer works on the game directly.", DS_RAM)
        else -> listOf("Stored as is, ready to randomize.")
    }
}
