package com.ironmonone.core

/**
 * Console generation. v1 implements GBA3 only, but nothing above [core-api] may
 * branch on file extension or console name. See brief section 15.5.
 */
enum class Generation(val platform: Platform, val number: Int) {
    GBA3(Platform.GBA, 3),
    NDS4(Platform.NDS, 4),
    NDS5(Platform.NDS, 5),
    GBC2(Platform.GBC, 2),
    /** Gen 1 on Gambatte. Randomized in two passes (Randomizers.twoPass), the official page's PART 1 then PART 2. */
    GB1(Platform.GBC, 1),
}

/**
 * A specific game + revision the app knows how to verify and patch.
 *
 * [expectedCrc] is zlib CRC-32 of the clean dump, the same function RomPatcher.js
 * uses, so the green/red gate matches what the user saw on PC.
 */
data class RomKind(
    val id: String,
    val displayName: String,
    /**
     * The settings-file family this ROM belongs to: "FRLG", "RSE", "DPPt",
     * "HGSS", "BW", "B2W2" - the same tags RnqsInfo reads out of preset names.
     * A preset is offered for a ROM only when the two agree, so a ruleset
     * built for Emerald is never handed to FireRed. Explicit per kind: never
     * sniffed out of the id.
     */
    val family: String,
    val generation: Generation,
    val fileExtension: String,
    val expectedCrc: Long,
    val titleDetect: String,
    val natDexCapable: Boolean,
    /**
     * Whether the NatDex patch has already been applied.
     *
     * Explicit, and never inferred from [titleDetect]: the patch does not alter the GBA
     * header, so a patched Emerald still reports "POKEMON EMER". An earlier revision
     * derived this from the title and silently made every ROM read as vanilla, which
     * would have disabled the guard that stops a vanilla .rnqs reaching a NatDex ROM.
     */
    val isNatDex: Boolean = false,
) {
    /** The console, and with it the core and the turbo cap. Never the extension. */
    val platform: Platform get() = generation.platform

    /** The randomizer fork this ROM belongs to. The one place that decision is made. */
    val engine: Engine get() = if (isNatDex) Engine.NATDEX else Engine.ZX

    companion object {
        /** Every kind the app knows, so a stored id can be turned back into one. */
        val all: List<RomKind> get() = allV1 + allNatDex

        fun byId(id: String?): RomKind? = id?.let { k -> all.firstOrNull { it.id == k } }

        val FIRERED_U_V11 = RomKind(
            id = "firered-u-v11",
            family = "FRLG",
            displayName = "Pokémon FireRed (U) v1.1",
            generation = Generation.GBA3,
            fileExtension = "gba",
            expectedCrc = 0x84ee4776L,
            titleDetect = "POKEMON FIRE",
            natDexCapable = true,
        )

        val EMERALD_U = RomKind(
            id = "emerald-u",
            family = "RSE",
            displayName = "Pokémon Emerald (U)",
            generation = Generation.GBA3,
            fileExtension = "gba",
            expectedCrc = 0x1f1c08fbL,
            titleDetect = "POKEMON EMER",
            natDexCapable = true,
        )

        /**
         * NatDex 1.2.1 output. These CRCs are read directly out of the BPS footers
         * (measured 2026-08-30 with tools/RomTool.java), so a NatDex ROM is verifiable
         * by CRC alone without re-running the patch.
         *
         * NOTE: the NatDex patch does NOT change the GBA header. A patched Emerald
         * still reports title "POKEMON EMER" / code "BPEE", and the string "Nat. Dex"
         * is not ASCII-findable in the body because the games use a custom text
         * encoding. The title SCREEN says Nat. Dex; the header does not.
         * **CRC is the only reliable gate. Do not detect NatDex from the header.**
         */
        val EMERALD_NATDEX_121 = EMERALD_U.copy(
            id = "emerald-natdex-121",
            family = "RSE",
            displayName = "Pokémon Emerald + Nat. Dex 1.2.1",
            expectedCrc = 0xebfdce4bL,
            titleDetect = "POKEMON EMER", // unchanged by the patch; see note above
            natDexCapable = false,        // already applied; cannot stack
            isNatDex = true,
        )

        val FIRERED_NATDEX_121 = FIRERED_U_V11.copy(
            id = "firered-natdex-121",
            family = "FRLG",
            displayName = "Pokémon FireRed + Nat. Dex 1.2.1",
            expectedCrc = 0x33779943L,
            titleDetect = "POKEMON FIRE",
            natDexCapable = false,
            isNatDex = true,
        )

        /**
         * FireRed v1.0 — vanilla runs only. Nat. Dex genuinely requires v1.1 (the BPS
         * refuses anything else), but ZX randomizes v1.0 fine and vanilla IronMON on
         * it is legitimate. natDexCapable=false is the enforcement.
         */
        val FIRERED_U_V10 = RomKind(
            id = "firered-u-v10",
            family = "FRLG",
            displayName = "Pokémon FireRed (U) v1.0",
            generation = Generation.GBA3,
            fileExtension = "gba",
            expectedCrc = 0xdd88761cL,
            titleDetect = "POKEMON FIRE",
            natDexCapable = false,
        )

        /**
         * Ruby, Sapphire and LeafGreen (USA) v1.0, from Blake's dumps 2026-09-07:
         * titles "POKEMON RUBY" / "POKEMON SAPP" / "POKEMON LEAF", codes AXVE /
         * AXPE / BPGE, version byte 0. Ruby and Sapphire join Emerald's RSE
         * family, LeafGreen joins FRLG, so the presets and the rules are shared.
         * The tracker has a map for each (GameMap.RUBY_U, SAPPHIRE_U,
         * LEAFGREEN_U). Nat. Dex is built for Emerald and FireRed v1.1 only.
         */
        val RUBY_U = RomKind(
            id = "ruby-u", family = "RSE", displayName = "Pokémon Ruby (U)",
            generation = Generation.GBA3, fileExtension = "gba",
            expectedCrc = 0xF0815EE7L, titleDetect = "POKEMON RUBY", natDexCapable = false,
        )
        val SAPPHIRE_U = RUBY_U.copy(
            id = "sapphire-u", displayName = "Pokémon Sapphire (U)",
            expectedCrc = 0x554DEDC4L, titleDetect = "POKEMON SAPP",
        )
        val LEAFGREEN_U = FIRERED_U_V10.copy(
            id = "leafgreen-u", displayName = "Pokémon LeafGreen (U)",
            expectedCrc = 0xD69C96CCL, titleDetect = "POKEMON LEAF",
        )

        /**
         * Pokémon Platinum (USA). Header read from Blake's own dump on
         * 2026-08-30: title "POKEMON PL", game code CPUE, version byte 0,
         * 128MB, CRC-32 9253921d (matched again on his 2026-09-07 dump). The DS header carries the code at 0x0C,
         * which is why titleDetect is the 12-byte title and the code is
         * checked separately by the picker.
         *
         * Nat. Dex is a GBA hack: natDexCapable is false and the Emerald or
         * FireRed .bps must never reach this ROM (brief 15.5).
         */
        val PLATINUM_U = RomKind(
            id = "platinum-u",
            family = "DPPt",
            displayName = "Pokémon Platinum (U)",
            generation = Generation.NDS4,
            fileExtension = "nds",
            expectedCrc = 0x9253921dL,
            titleDetect = "POKEMON PL",
            natDexCapable = false,
        )

        /**
         * Pokémon Diamond and Pearl (USA), Rev 5, read off Blake's own dumps
         * 2026-09-07: titles "POKEMON D" / "POKEMON P" (the DS header title is
         * matched whole, so "POKEMON P" never claims Platinum's "POKEMON PL"),
         * codes ADAE / APAE, version byte 5, 64 MB. Same DPPt family as
         * Platinum: the settings presets and the rules are shared, the tracker
         * map is NdsGameMap.DP. Nat. Dex is a GBA hack; never applicable.
         */
        val DIAMOND_U = PLATINUM_U.copy(
            id = "diamond-u", displayName = "Pokémon Diamond (U)",
            expectedCrc = 0x84427823L, titleDetect = "POKEMON D",
        )
        val PEARL_U = PLATINUM_U.copy(
            id = "pearl-u", displayName = "Pokémon Pearl (U)",
            expectedCrc = 0xE2D87EBFL, titleDetect = "POKEMON P",
        )

        const val CRC_UNKNOWN = -1L

        /**
         * HeartGold / SoulSilver (USA). Header titles "POKEMON HG" / "POKEMON SS",
         * game codes IPKE / IPGE (the reference's GameInfo.VERSION_NUMBER). The
         * CRCs are CRC_UNKNOWN until read off Blake's own dumps: the Clean step
         * then accepts the file on its header, and the tracker still refuses a
         * game it has no map for. Nat. Dex is a GBA hack; never applicable.
         */
        val HEARTGOLD_U = RomKind(
            id = "heartgold-u",
            family = "HGSS",
            displayName = "Pokémon HeartGold (U)",
            generation = Generation.NDS4,
            fileExtension = "nds",
            expectedCrc = 0xC180A0E9L,   // Blake's dump 2026-09-06: POKEMON HG IPKE rev 0, 128 MB
            titleDetect = "POKEMON HG",
            natDexCapable = false,
        )

        val SOULSILVER_U = HEARTGOLD_U.copy(
            id = "soulsilver-u",
            displayName = "Pokémon SoulSilver (U)",
            titleDetect = "POKEMON SS",
            expectedCrc = 0xD8EA6090L,   // Blake's dump 2026-09-06: POKEMON SS IPGE, 128 MB
        )

        /**
         * Gen 5 (USA). Header titles and codes from the DS reference's
         * GameInfo.VERSION_NUMBER: Black IRBO, White IRAO, Black 2 IREO,
         * White 2 IRDO. Black's CRC is unknown until a dump is read. Two
         * families, because the settings page and the reference both treat
         * BW and B2W2 as separate groups.
         */
        val BLACK_U = RomKind(
            id = "black-u",
            family = "BW",
            displayName = "Pokémon Black (U)",
            generation = Generation.NDS5,
            fileExtension = "nds",
            expectedCrc = CRC_UNKNOWN,
            titleDetect = "POKEMON B",
            natDexCapable = false,
        )
        val WHITE_U = BLACK_U.copy(
            id = "white-u", displayName = "Pokémon White (U)", titleDetect = "POKEMON W",
            expectedCrc = 0xB552501CL,   // Blake's dump 2026-09-06: POKEMON W IRAO, 256 MB
        )
        val BLACK2_U = BLACK_U.copy(
            id = "black2-u", family = "B2W2", displayName = "Pokémon Black 2 (U)",
            titleDetect = "POKEMON B2",
            // CRC-32 read off Blake's own dump on 2026-09-06: "POKEMON B2" IREO rev 0, 512 MB.
            expectedCrc = 0xD4427FD1L,
        )
        val WHITE2_U = BLACK_U.copy(
            id = "white2-u", family = "B2W2", displayName = "Pokémon White 2 (U)",
            titleDetect = "POKEMON W2",
            expectedCrc = 0x777EB04FL,   // Blake's dump 2026-09-06: POKEMON W2 IRDO, 512 MB
        )

        /**
         * Pokémon Crystal (USA). Game Boy header title "PM_CRYSTAL" at 0x134;
         * the CGB flag at 0x143 is 0xC0 (Color only). CRC_UNKNOWN until read
         * off Blake's dump (two US revisions exist: ZX lists EE6F5188 for 1.0
         * and 3358E30A for 1.1). Gold and Silver are [GOLD_U] and [SILVER_U].
         */
        val CRYSTAL_U = RomKind(
            id = "crystal-u",
            family = "GSC",
            displayName = "Pokémon Crystal (U)",
            generation = Generation.GBC2,
            fileExtension = "gbc",
            expectedCrc = 0xEE6F5188L,   // Blake's dump 2026-09-06: PM_CRYSTAL, CGB 0xC0, 2 MB; the 1.0 revision (ZX lists 3358E30A for 1.1)
            titleDetect = "PM_CRYSTAL",
            natDexCapable = false,
        )

        /**
         * Pokémon Gold and Silver (USA). Header title "POKEMON_GLD" / "POKEMON_SLV"
         * with the manufacturer code "AAUE" / "AAXE" right behind it (pokegold's
         * rgbfix flags), which is why the 15-byte title reads as one string.
         * CRC-32 from the randomizer's own gen2_offsets.ini ([Gold (U)] and
         * [Silver (U)]): the same whole-file CRC ZX checks before it will
         * randomize one, so a dump ZX accepts is a dump this pins.
         */
        val GOLD_U = RomKind(
            id = "gold-u", family = "GSC", displayName = "Pokémon Gold (U)",
            generation = Generation.GBC2, fileExtension = "gbc",
            expectedCrc = 0x6BDE3C3EL, titleDetect = "POKEMON_GLDAAUE", natDexCapable = false,
        )
        val SILVER_U = GOLD_U.copy(
            id = "silver-u", displayName = "Pokémon Silver (U)",
            expectedCrc = 0x8AD48636L, titleDetect = "POKEMON_SLVAAXE",   // matched Blake's own dump 2026-09-07: POKEMON_SLVAAXE, CGB 0x80, 2 MB
        )

        /**
         * Pokémon Red, Blue and Yellow (USA). Header titles from the randomizer's
         * gen1_offsets.ini `Game=` lines (the same `-t` rgbfix stamps in pokered),
         * CRC-32s from its [Red (U)], [Blue (U)] and [Yellow (U)] entries. All
         * three land as .gbc, the GBC platform's extension: Gambatte reads the
         * header, and the app's file names follow the console, not the dump.
         * Gen 1 has no exp-curve option, so a run is randomized twice; see
         * Randomizers.twoPass and the bundled "RBY PART 2.rnqs".
         */
        val RED_U = RomKind(
            id = "red-u", family = "RBY", displayName = "Pokémon Red (U)",
            generation = Generation.GB1, fileExtension = "gbc",
            expectedCrc = 0x9F7FDD53L, titleDetect = "POKEMON RED", natDexCapable = false,
        )
        val BLUE_U = RED_U.copy(id = "blue-u", displayName = "Pokémon Blue (U)", expectedCrc = 0xD6DA8A1AL, titleDetect = "POKEMON BLUE")
        val YELLOW_U = RED_U.copy(id = "yellow-u", displayName = "Pokémon Yellow (U)", expectedCrc = 0x7D527D62L, titleDetect = "POKEMON YELLOW")

        val allV1 = listOf(
            FIRERED_U_V11, FIRERED_U_V10, LEAFGREEN_U, EMERALD_U, RUBY_U, SAPPHIRE_U, DIAMOND_U, PEARL_U, PLATINUM_U, HEARTGOLD_U, SOULSILVER_U,
            BLACK_U, WHITE_U, BLACK2_U, WHITE2_U, CRYSTAL_U, GOLD_U, SILVER_U, RED_U, BLUE_U, YELLOW_U,
        )
        val allNatDex = listOf(EMERALD_NATDEX_121, FIRERED_NATDEX_121)
    }
}

/** A ROM on disk plus what we believe it to be. */
data class RomFile(
    val uri: String,
    val kind: RomKind,
    val crc: Long,
    val sizeBytes: Long,
) {
    val crcVerified: Boolean
        get() = kind.expectedCrc != RomKind.CRC_UNKNOWN && crc == kind.expectedCrc
}
