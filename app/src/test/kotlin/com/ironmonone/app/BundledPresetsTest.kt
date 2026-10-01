package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Engine
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every mode the app bundles is choosable where it is defined and nowhere
 * else, and loads in the engine that will run it. Since 2026-09-29 the
 * presets are the official page's (and its 60% pre-passes and Gen 1's PART
 * 2), the eighteen Nat. Dex v1.2 files not bundled before, and the community
 * rulesets' published strings: IronMON Journey, Chaos Kaizo, Evo Kaizo and
 * Survival Revival.
 */
class BundledPresetsTest {
    private val presets = File("src/main/assets/presets")
    private val files = presets.listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }

    private fun readsWith(engine: Engine, f: File): Boolean = runCatching {
        FileInputStream(f).use {
            when (engine) {
                Engine.ZX -> com.dabomstew.pkrandomzx.Settings.read(it)
                Engine.NATDEX -> com.dabomstew.pkrandom.Settings.read(it)
            }
        }
    }.isSuccess

    @Test
    fun `every bundled preset loads in the engine of each game that offers it, and the other engine refuses it`() {
        assertTrue(files.size >= 74, "${files.size} presets")
        for (f in files) {
            val info = RnqsInfo.of(f)
            if (info.appliedByApp) { assertTrue(readsWith(Engine.ZX, f), f.name); continue }
            val kinds = RomKind.all.filter { k -> RulesetCatalog.forRom(k, files).any { it.preset == f || f in it.alternatives } }
            assertTrue(kinds.isNotEmpty(), "${f.name} is offered for no game")
            val engines = kinds.map { it.engine }.toSet()
            assertEquals(1, engines.size, "${f.name} is offered to both engines' games: ${kinds.map { it.id }}")
            assertTrue(readsWith(engines.single(), f), "${f.name} does not load in ${engines.single()}")
            val other = if (engines.single() == Engine.ZX) Engine.NATDEX else Engine.ZX
            assertFalse(readsWith(other, f), "${f.name} also loads in $other")
        }
    }

    // The order the rules build (2026-09-30, UX audit P0-12): Standard, Ultimate, Kaizo, Super Kaizo, then the variants.
    private val gen3 = listOf("standard", "ultimate", "kaizo", "superkaizo", "survival")
    private fun expected(k: RomKind): List<String> = when {
        k.isNatDex -> gen3 + listOf("survivalrevival", "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney")
        k.family == "FRLG" -> gen3 + listOf("survivalrevival", "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney")
        k.family == "RSE" && (k.baseId ?: k.id) in setOf(RomKind.RUBY_U.id, RomKind.SAPPHIRE_U.id) ->
            (gen3 - "superkaizo") + listOf("kaizodoubles", "chaoskaizo", "ironmonjourney")
        k.family == "RSE" -> gen3 + listOf("kaizodoubles", "chaoskaizo", "ironmonjourney")
        k.family == "DPPt" && (k.baseId ?: k.id) != RomKind.PLATINUM_U.id -> (gen3 - "superkaizo") + listOf("kaizodoubles", "ironmonjourney")
        k.family == "DPPt" || k.family == "HGSS" -> gen3 + listOf("kaizodoubles", "ironmonjourney")
        k.family == "B2W2" -> listOf("standard", "ultimate", "kaizo", "survival", "kaizodoubles", "ironmonjourney")
        k.family == "BW" -> listOf("standard", "ultimate", "kaizo", "survival", "ironmonjourney")
        else -> listOf("standard", "ultimate", "kaizo", "survival")   // RBY, GSC
    }

    @Test
    fun `each game offers exactly the modes defined for it, from its own family's files`() {
        for (k in RomKind.all) {
            val modes = RulesetCatalog.forRom(k, files)
            assertEquals(expected(k), modes.map { it.key }, k.id)
            for (m in modes) {
                val i = RnqsInfo.of(m.preset)
                assertTrue(i.gameTag == k.family && i.natDex == k.isNatDex, "${k.id} ${m.key}: ${m.preset.name}")
            }
        }
        // Spot checks on the new files: the Nat. Dex ones only on the Nat. Dex builds, Evo Kaizo only on FireRed and LeafGreen.
        assertEquals("FRLG NatDex v1.2 Evo Kaizo.rnqs", RulesetCatalog.forRom(RomKind.FIRERED_NATDEX_121, files).single { it.key == "evokaizo" }.preset.name)
        assertEquals("RSE NatDex v1.2 Survival Revival.rnqs", RulesetCatalog.forRom(RomKind.EMERALD_NATDEX_121, files).single { it.key == "survivalrevival" }.preset.name)
        assertEquals("FRLG Survival Revival.rnqs", RulesetCatalog.forRom(RomKind.LEAFGREEN_U, files).single { it.key == "survivalrevival" }.preset.name)
        assertTrue(RulesetCatalog.forRom(RomKind.EMERALD_U, files).none { it.key == "evokaizo" || it.key == "survivalrevival" })
    }

    @Test
    fun `Survival Revival is the README's current string, the 2026-02-21 one, read as its authors' tool wrote it`() {
        val s = FileInputStream(File(presets, "FRLG Survival Revival.rnqs")).use { com.dabomstew.pkrandomzx.Settings.read(it) }
        // "Bigger & Badder" (2026-02-20): boss and important trainers hold consumable, sensible items.
        assertTrue(s.isRandomizeHeldItemsForBossTrainerPokemon && s.isRandomizeHeldItemsForImportantTrainerPokemon && !s.isRandomizeHeldItemsForRegularTrainerPokemon)
        assertTrue(s.isConsumableItemsOnlyForTrainers && s.isSensibleItemsOnlyForTrainers, "the older tracker file had sensible only")
        assertEquals(listOf(50, 50, 3), listOf(s.trainersLevelModifier, s.wildLevelModifier, s.additionalBossTrainerPokemon))
        // "BALLS of Knowledge": TMs 50% learnable; "Trials of Knowledge": tutors.
        assertEquals("COMPLETELY_RANDOM", s.tmsHmsCompatibilityMod.name)
        assertEquals("RANDOM" to "FULL", s.moveTutorMovesMod.name to s.moveTutorsCompatibilityMod.name)
        assertEquals(334008, s.currentMiscTweaks, "the same tweaks as the official Survival")
        assertEquals("Fire Red (U) 1.1", s.romName, "the name ZX 4.6.1 misses in the README string, where its authors' tool keeps it")
    }

    /**
     * The new presets run: each randomizes a real dump of a game that offers it,
     * when IRONMON_ROMS has one (the GBA dumps and Platinum; the 512 MB Black 2
     * and White 2 are left out of the test heap).
     */
    @Test
    fun `the new modes randomize a real dump of each game they are offered for`() {
        val dir = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory }
            ?: return println("BundledPresetsTest skipped: set IRONMON_ROMS")
        val new = files.filter { f -> RnqsInfo.of(f).let { it.natDex || it.ruleset in setOf("ironmonjourney", "chaoskaizo", "evokaizo", "survivalrevival") } }
        var ran = 0
        for (f in new) {
            val kind = RomKind.all.filter { k -> k.patchTag == null && (k.platform == Platform.GBA || k.id == RomKind.PLATINUM_U.id) }
                .firstOrNull { k -> File(dir, k.id + "." + k.fileExtension).isFile && RulesetCatalog.forRom(k, files).any { it.preset == f } } ?: continue
            val src = File(dir, kind.id + "." + kind.fileExtension)
            val out = File.createTempFile("mode", "." + kind.fileExtension)
            try {
                val o = Randomizers.randomize(kind, src, f, out, 20260929L)
                assertTrue(out.length() > 0 && "--Trainers Pokemon--" in o.logText, "${f.name} on ${kind.id}")
                ran++
            } finally {
                out.delete(); Randomizers.logFor(out).delete(); File(out.parentFile, out.nameWithoutExtension + ".species.tsv").delete()
            }
        }
        println("BUNDLED_PRESETS_RAN=$ran of ${new.size}")
        assertTrue(ran > 0)
    }
}
