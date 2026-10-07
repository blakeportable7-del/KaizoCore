package com.ironmonone.app.engine

import com.ironmonone.app.BstRule
import com.ironmonone.app.engine.hns.HnsGame
import com.ironmonone.app.engine.hns.HnsLayout
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.app.engine.hns.HnsRandomizer
import com.ironmonone.app.engine.hns.HnsRom
import com.ironmonone.app.engine.hns.HnsSpeciesFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32

/**
 * The Heart & Soul randomizer (docs/HNS-KAIZO.md, step 4). UPR cannot open Heart & Soul: it is pokeemerald-expansion,
 * with 1,500 species, bit-packed tables and a 32 MB cartridge. So KaizoCore does UPR's work itself, driven by the same
 * settings files every other game uses (a mode's .rnqs, read by the Nat. Dex fork's or ZX's Settings), on the tables
 * our build's own symbols locate (assets/hns/layout-*.json, made by tools/hns/layout.py). The output and its log are a
 * function of the ROM, the settings, the seed and the pool alone, as New Run requires.
 *
 * Wired as Engine.HNS (RomKind.HEARTSOUL_KAIZO_206): Randomizers hands it every Heart & Soul run, with the pool the
 * player chose (Randomizers.hnsPoolNow) and the app's assets ([appAssets]).
 */
object HnsEngine {
    const val ID = "hns-1.0"
    const val DISPLAY_NAME = "KaizoCore Heart & Soul randomizer 1.0 (UPR rules)"
    const val GAME_NAME = "Pokemon Heart & Soul v2.0.6"

    /** VANILLA is Heart & Soul's own Gen 1-3 scope (src/randomizer.c BuildGenScopeMask); NATDEX is every species. */
    enum class Pool { VANILLA, NATDEX }

    /** A build this engine has a layout for: its CRC-32 and its two assets. */
    class Build(val crc: Long, val name: String, val layoutAsset: String, val speciesAsset: String)

    /**
     * The comfort build players run (official 2.0.6 patched with tools/hns' BPS), then our plain build of the same tag,
     * which has the same structures and is kept for the tests.
     */
    val BUILDS = listOf(
        Build(0x949DBE42L, "KaizoCore comfort build", "hns/layout-kaizo.json", "hns/species-kaizo.json"),
        Build(0x45D07ED4L, "plain build", "hns/layout-plain.json", "hns/species-plain.json"),
    )

    class Result(val rom: ByteArray, val logText: String, val seed: Long)

    fun crc(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value

    /** Why [rom] cannot be randomized here, or null when it is a Heart & Soul build this engine knows. */
    fun refusal(rom: ByteArray): String? {
        val c = crc(rom)
        return if (BUILDS.any { it.crc == c }) null
        else "This file is not a Heart & Soul build KaizoCore knows (CRC %08X), so the Heart & Soul randomizer will not touch it.".format(c)
    }

    /** The settings in a .rnqs: the Nat. Dex fork's first, then ZX's (the vanilla RSE presets are ZX files). */
    fun readSettings(file: File): HnsOptions {
        val mode = file.nameWithoutExtension
        runCatching { FileInputStream(file).use { com.dabomstew.pkrandom.Settings.read(it) } }.getOrNull()?.let { return HnsOptions.from(it, mode) }
        val zx = runCatching { FileInputStream(file).use { com.dabomstew.pkrandomzx.Settings.read(it) } }
        return HnsOptions.fromZx(zx.getOrElse { throw NatDexEngine.EngineException("Could not read \"${file.name}\": ${it.message}", it) }, mode)
    }

    /** The BstRule mode key of a preset's name: "RSE NatDex v1.2 Super Kaizo" is "superkaizo". */
    fun modeKey(name: String): String? {
        val n = name.lowercase()
        return when {
            "super kaizo" in n -> "superkaizo"
            "kaizo doubles" in n -> "kaizodoubles"
            "survival revival" in n -> "survivalrevival"
            "survival" in n -> "survival"
            "evo kaizo" in n -> "evokaizo"
            "chaos kaizo" in n -> "chaoskaizo"
            "kaizo" in n -> "kaizo"
            "ultimate" in n -> "ultimate"
            "standard" in n -> "standard"
            "journey" in n -> "journey"
            else -> null
        }
    }

    /**
     * The BST from which KaizoCore's rule sheet bans a Pokemon of the mode (BstRule): starters and wild ones stay under
     * it. The randomizer draws one line for both, so it takes the wild one, except Evo Kaizo's lab line (600 and lower
     * legal there). That is the line it has drawn since it shipped, so a seed still makes the same game: BstRule's own
     * line for a Nat. Dex Kaizo run moved to 601 on 2026-10-06 (600 BST starters are legal), the wild one stayed at 600.
     */
    fun bstLine(options: HnsOptions, pool: Pool): Int? {
        val mode = modeKey(options.modeName)
        val lines = BstRule.lines(mode, natDex = pool == Pool.NATDEX) ?: return null
        return if (mode == "evokaizo") lines.own else lines.wild
    }

    /** The BST from which the rule sheet bans a wild Pokemon of the mode (BstRule): Evo Kaizo's is Kaizo's, not its own 601. */
    fun wildBstLine(options: HnsOptions, pool: Pool): Int? = BstRule.lines(modeKey(options.modeName), natDex = pool == Pool.NATDEX)?.wild

    /**
     * Randomizes [rom] (not changed) by [options] and [seed] over [pool]. [assets] reads an asset by its path under
     * app/src/main/assets ("hns/layout-kaizo.json").
     */
    fun randomize(rom: ByteArray, options: HnsOptions, seed: Long, pool: Pool, assets: (String) -> String): Result {
        refusal(rom)?.let { throw NatDexEngine.EngineException(it) }
        val build = BUILDS.first { it.crc == crc(rom) }
        val layout = HnsLayout.parse(assets(build.layoutAsset))
        val species = HnsSpeciesFile.parse(assets(build.speciesAsset))
        require(layout.buildCrc == build.crc && species.buildCrc == build.crc) { "The ${build.name} layout does not match its CRC." }
        val out = rom.copyOf()
        val game = HnsGame(HnsRom(out, layout), species)
        val text = Randomizers.inEngineLocale {
            HnsRandomizer(game, options, seed, pool, bstLine(options, pool), poolItemKeys(pool, assets), poolNonBadKeys(pool, assets),
                wildBstLine(options, pool), poolAllowedKeys(pool, assets)).run()
        }
        // Every randomized run is a Kaizo IronMON run until the Nuzlocke path says otherwise (writePreset).
        writePreset(out, layout, Preset.KAIZO, kaizoDoubles = options.doubleBattleMode)
        return Result(out, text, seed)
    }

    /**
     * The challenge menu's preset a run's ROM carries (gHnsChallengePreset, kaizocore_tables.h, Blake 2026-10-05): the
     * build holds the Nuzlocke mode's; a Kaizo IronMON run skips the menu, with the Nuzlocke rows OFF and locked too.
     */
    enum class Preset { KAIZO, NUZLOCKE }

    /** Rows of the Nuzlocke tab (tab 3 of 20 rows): the NUZLOCKE switch, then its clauses. */
    private const val NUZLOCKE_ROW = 3 * 20

    /**
     * Writes [preset] into [rom] (a run of the comfort build, or the build itself) at the layout's gHnsChallengePreset.
     * [kaizoDoubles]: the Kaizo Doubles mode (its trainers battle in doubles), which keeps every trainer double battle a
     * double; any other Kaizo run plays them as singles, one Pokemon at a time (Blake, 2026-10-06). The Nuzlocke mode
     * keeps the game's own doubles either way.
     */
    fun writePreset(rom: ByteArray, layout: HnsLayout, preset: Preset, kaizoDoubles: Boolean = false) {
        if (!layout.hasSym("gHnsChallengePreset")) return
        val base = layout.sym("gHnsChallengePreset") - layout.romBase
        val values = base + layout.const("HNS_PRESET_VALUES")
        val locks = base + layout.const("HNS_PRESET_LOCKS")
        val kaizo = preset == Preset.KAIZO
        rom[base + layout.const("HNS_PRESET_MODE")] = layout.const(if (kaizo) "HNS_PRESET_MODE_KAIZO" else "HNS_PRESET_MODE_NUZLOCKE").toByte()
        rom[base + layout.const("HNS_PRESET_SKIP_MENU")] = (if (kaizo) 1 else 0).toByte()
        if (layout.constants.containsKey("HNS_PRESET_KAIZO_DOUBLES"))
            rom[base + layout.const("HNS_PRESET_KAIZO_DOUBLES")] = (if (kaizo && kaizoDoubles) 1 else 0).toByte()
        for (k in 0 until 6) {
            // The NUZLOCKE switch OFF (selection 0, stored plus one) and every clause locked; in a Nuzlocke run all free.
            rom[values + NUZLOCKE_ROW + k] = (if (kaizo && k == 0) 1 else 0).toByte()
            rom[locks + NUZLOCKE_ROW + k] = (if (kaizo) 1 else 0).toByte()
        }
        // The rules rows Kaizo sets apart from the build's Nuzlocke preset (Blake, 2026-10-05), locked: GAME MODE CUSTOM
        // (the two below differ from RECOMMENDED), REUSABLE TMS OFF, NATURE MINTS OFF (the mint shop closes with it),
        // SHINY CHANCE 1/8192 and ITEM DROP OFF. The Nuzlocke path puts the build's own back: RECOMMENDED, both ON and
        // locked; the two FEATURES rows free.
        for ((row, k, n) in KAIZO_RULE_ROWS) {
            rom[values + row] = (if (kaizo) k else n).toByte()
            rom[locks + row] = (if (kaizo || row < 20) 1 else 0).toByte()
        }
    }

    /** (row, Kaizo value, Nuzlocke value): a value is the selection plus one, 0 leaves the row as it is. */
    private val KAIZO_RULE_ROWS = listOf(Triple(0, 2, 1), Triple(4, 1, 2), Triple(5, 1, 2), Triple(20 + 1, 1, 0), Triple(20 + 3, 1, 0))

    /** [writePreset] on a run's ROM file, read through the comfort build's layout. */
    fun writePreset(romFile: File, preset: Preset, assets: (String) -> String) {
        val layout = HnsLayout.parse(assets(BUILDS.first().layoutAsset))
        val bytes = romFile.readBytes()
        writePreset(bytes, layout, preset)
        romFile.writeBytes(bytes)
    }

    /** The asset naming the items of the game [pool] models (tools/hns/item_names.py reads them off the real ROMs). */
    fun poolItemsAsset(pool: Pool): String = if (pool == Pool.VANILLA) "hns/items-emerald.tsv" else "hns/items-natdex-121.tsv"

    /**
     * The items a [pool] run may roll, as HnsRandomizer.itemKey names (Blake, 2026-10-05: "standard kaizo, not natl dex,
     * the item pool would be smaller"): VANILLA the items of vanilla Emerald, NATDEX those of Nat. Dex Emerald 1.2.1.
     */
    fun poolItemKeys(pool: Pool, assets: (String) -> String): Set<String> =
        assets(poolItemsAsset(pool)).lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
            .map { HnsRandomizer.itemKey(it.substringAfter('\t')) }.toSet()

    /**
     * The pool's "bad items banned" list, as the source game's own UPR fork has it for Emerald (Gen3Constants
     * .getNonBadItems): engine-zx's for VANILLA, engine-natdex's for NATDEX, by name. Heart & Soul had banned more on
     * its own (valuables, the status berries, the Deep Sea items), which left TMs 31% of the list against Emerald's 27%
     * (Blake, 2026-10-05: the starter's item seemed to favor TMs). Every item roll with bad items banned draws from it.
     */
    fun poolNonBadKeys(pool: Pool, assets: (String) -> String): Set<String> {
        val list = if (pool == Pool.VANILLA) com.dabomstew.pkrandomzx.constants.Gen3Constants.getNonBadItems(com.dabomstew.pkrandomzx.constants.Gen3Constants.RomType_Em)::isAllowed
                   else com.dabomstew.pkrandom.constants.Gen3Constants.getNonBadItems(com.dabomstew.pkrandom.constants.Gen3Constants.RomType_Em)::isAllowed
        return assets(poolItemsAsset(pool)).lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
            .filter { list(it.substringBefore('\t').toInt()) }.map { HnsRandomizer.itemKey(it.substringAfter('\t')) }.toSet()
    }

    /**
     * The pool's allowed items, as the source game's own UPR fork has it for Emerald (Gen3Constants.allowedItems: no key
     * item, HM or unused slot): engine-zx's for VANILLA, engine-natdex's for NATDEX, by name. A roll that may land on a
     * bad item (an in-game trade's item, or a mode that keeps bad items) draws from it (rc38).
     */
    fun poolAllowedKeys(pool: Pool, assets: (String) -> String): Set<String> {
        val list = if (pool == Pool.VANILLA) com.dabomstew.pkrandomzx.constants.Gen3Constants.allowedItems::isAllowed
                   else com.dabomstew.pkrandom.constants.Gen3Constants.allowedItems::isAllowed
        return assets(poolItemsAsset(pool)).lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
            .filter { list(it.substringBefore('\t').toInt()) }.map { HnsRandomizer.itemKey(it.substringAfter('\t')) }.toSet()
    }

    fun randomize(rom: ByteArray, settings: com.dabomstew.pkrandom.Settings, modeName: String, seed: Long, pool: Pool, assets: (String) -> String): Result =
        randomize(rom, HnsOptions.from(settings, modeName), seed, pool, assets)

    /** The file form New Run uses for the other engines: [sourceRom] and [settingsFile] in, [dest] and its log out. */
    fun randomize(sourceRom: File, settingsFile: File, dest: File, seed: Long, pool: Pool, assets: (String) -> String): NatDexEngine.Outcome {
        val result = randomize(sourceRom.readBytes(), readSettings(settingsFile), seed, pool, assets)
        dest.writeBytes(result.rom)
        return NatDexEngine.Outcome(seed, result.logText)
    }

    /** The app's own assets, by path; MainActivity sets it. A test or a tool hands [folderAssets] instead. */
    @Volatile var assetText: ((String) -> String)? = null

    /** [assetText], or the reason a run cannot be made without it. */
    fun appAssets(): (String) -> String = assetText ?: throw NatDexEngine.EngineException("Heart & Soul's tables are not loaded. Restart KaizoCore and try again.")

    /** An asset reader over a folder laid out as app/src/main/assets, for tests and tools. */
    fun folderAssets(dir: File): (String) -> String = { path -> File(dir, path).readText(Charsets.UTF_8) }
}
