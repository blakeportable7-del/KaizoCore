package com.ironmonone.core

/**
 * The console a ROM runs on, and everything that follows from that.
 *
 * Brief 15.5 already said nothing above core-api may branch on a file
 * extension or a console name. PlayScreen did anyway: `rom.extension == "nds"`
 * decided the core, the turbo cap, which tracker to build and which panel to
 * draw, in twenty-eight places. Every one of those was a place a third console
 * would have needed a third branch.
 *
 * This is the single lookup that replaces them. Adding Game Boy support is one
 * more line here, not twenty-eight `if`s.
 *
 * [maxTurbo]: melonDS dies above 4x - SIGSEGV in GLThread the instant the
 * multiplier changes, reproduced 2026-08-30 - so the cap is a property of the
 * platform, not a magic number in the speed button.
 */
enum class Platform(
    val core: String,
    val romExtension: String,
    val maxTurbo: Int,
    /**
     * Whether the core keeps the battery save itself. melonDS manages its own
     * .sav in the saves directory; mGBA hands SRAM to the app, which persists
     * it on every pause. The wrong answer either double-writes a save or never
     * writes one.
     */
    val coreOwnsSaves: Boolean,
    /**
     * The screen's width over its height, for the portrait game box. GBA is
     * 240x160; the Game Boy is 160x144, noticeably squarer. The DS draws two
     * screens and sizes its box by weight instead, so its value is unused.
     */
    val aspect: Float,
) {
    GBA("libmgba_libretro_android.so", "gba", 16, coreOwnsSaves = false, aspect = 240f / 160f),
    NDS("libmelonds_libretro_android.so", "nds", 4, coreOwnsSaves = true, aspect = 1f),
    /**
     * Game Boy Color, via Gambatte (LemuroidCores' pinned build, the same
     * family as the mGBA core). Like mGBA it hands SRAM to the frontend, so
     * the app persists the battery save. Gen 2 lives here; Gen 1 too once
     * its two-pass randomization exists.
     */
    GBC("libgambatte_libretro_android.so", "gbc", 16, coreOwnsSaves = false, aspect = 160f / 144f);

    companion object {
        /** Only for a file with no known [RomKind]; a kind's platform is authoritative. */
        fun fromExtension(ext: String): Platform? =
            entries.firstOrNull { it.romExtension.equals(ext, ignoreCase = true) }
    }
}

/**
 * Which randomizer fork owns a ROM. Never mixed: a vanilla ROM goes to ZX,
 * a Nat. Dex ROM to the Nat. Dex fork. The pairing guard in RunScreen exists
 * because crossing them crashes the intro.
 */
enum class Engine { ZX, NATDEX }
