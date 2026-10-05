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
        Build(0xC993EB6EL, "KaizoCore comfort build", "hns/layout-kaizo.json", "hns/species-kaizo.json"),
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

    /** The BST from which KaizoCore's rule sheet bans a Pokemon of the mode (BstRule): starters and wild ones stay under it. */
    fun bstLine(options: HnsOptions, pool: Pool): Int? = BstRule.lines(modeKey(options.modeName), natDex = pool == Pool.NATDEX)?.own

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
            HnsRandomizer(game, options, seed, pool, bstLine(options, pool), poolItemKeys(pool, assets)).run()
        }
        // Every randomized run is a Kaizo IronMON run until the Nuzlocke path says otherwise (writePreset).
        writePreset(out, layout, Preset.KAIZO)
        return Result(out, text, seed)
    }

    /**
     * The challenge menu's preset a run's ROM carries (gHnsChallengePreset, kaizocore_tables.h, Blake 2026-10-05): the
     * build holds the Nuzlocke mode's; a Kaizo IronMON run skips the menu, with the Nuzlocke rows OFF and locked too.
     */
    enum class Preset { KAIZO, NUZLOCKE }

    /** Rows of the Nuzlocke tab (tab 3 of 20 rows): the NUZLOCKE switch, then its clauses. */
    private const val NUZLOCKE_ROW = 3 * 20

    /** Writes [preset] into [rom] (a run of the comfort build, or the build itself) at the layout's gHnsChallengePreset. */
    fun writePreset(rom: ByteArray, layout: HnsLayout, preset: Preset) {
        if (!layout.hasSym("gHnsChallengePreset")) return
        val base = layout.sym("gHnsChallengePreset") - layout.romBase
        val values = base + layout.const("HNS_PRESET_VALUES")
        val locks = base + layout.const("HNS_PRESET_LOCKS")
        val kaizo = preset == Preset.KAIZO
        rom[base + layout.const("HNS_PRESET_MODE")] = layout.const(if (kaizo) "HNS_PRESET_MODE_KAIZO" else "HNS_PRESET_MODE_NUZLOCKE").toByte()
        rom[base + layout.const("HNS_PRESET_SKIP_MENU")] = (if (kaizo) 1 else 0).toByte()
        for (k in 0 until 6) {
            // The NUZLOCKE switch OFF (selection 0, stored plus one) and every clause locked; in a Nuzlocke run all free.
            rom[values + NUZLOCKE_ROW + k] = (if (kaizo && k == 0) 1 else 0).toByte()
            rom[locks + NUZLOCKE_ROW + k] = (if (kaizo) 1 else 0).toByte()
        }
    }

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
