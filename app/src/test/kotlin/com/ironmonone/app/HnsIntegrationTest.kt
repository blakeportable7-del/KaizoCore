package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.HnsEngineTest
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Engine
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Heart & Soul as a KaizoCore game (2026-10-05): its two kinds and how the Library shelves them, HnsEngine as its
 * engine, the RSE NatDex v1.2 modes, the pool choice reaching the engine, and the screens that number its species
 * (the log's names, GachaMon's listed BST). The engine runs read the real comfort build where it lies
 * (HnsEngineTest.romFile) and return early without it.
 */
class HnsIntegrationTest {
    private val presets = File("src/main/assets/presets").listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }
    private fun tmp(): File = Files.createTempDirectory("hns-int").toFile().apply { deleteOnExit() }

    // ------------------------------------------------------------------ the kinds

    @Test
    fun `the three checksums are Emerald, the official 2_0_6 and the KaizoCore build, each one kind`() {
        fun byCrc(c: Long) = RomKind.all.filter { it.expectedCrc == c }
        assertEquals(listOf(RomKind.EMERALD_U), byCrc(0x1F1C08FBL))
        assertEquals(listOf(RomKind.HEARTSOUL_206), byCrc(0x01713508L))
        assertEquals(listOf(RomKind.HEARTSOUL_KAIZO_206), byCrc(0xC993EB6EL))
        assertEquals(RomKind.HEARTSOUL_206, RomKind.byId("heartsoul-206"))
        for (k in RomKind.allHns) {
            assertEquals("POKEMON HNS", k.titleDetect)
            assertEquals(Platform.GBA, k.platform)
            assertTrue(k.isHns && k.fairyTypes && !k.isNatDex && !k.natDexCapable)
        }
        assertTrue(RomKind.HEARTSOUL_206.playOnly)
        assertFalse(RomKind.HEARTSOUL_KAIZO_206.playOnly)
        assertFalse(RomKind.EMERALD_U.fairyTypes)
        assertTrue(RomKind.EMERALD_NATDEX_121.fairyTypes)
    }

    @Test
    fun `the official game plays with no tracker, the KaizoCore build is tracked and offered for runs`() {
        val d = tmp()
        val f1 = File(d, "a.gba").apply { writeBytes(ByteArray(16)) }
        val official = LibraryStore.Entry(f1, "Pokémon Heart & Soul 2.0.6.gba", 0x01713508L, RomKind.HEARTSOUL_206, Platform.GBA, "s")
        val kaizo = LibraryStore.Entry(f1, "Pokémon Heart & Soul (KaizoCore).gba", 0xC993EB6EL, RomKind.HEARTSOUL_KAIZO_206, Platform.GBA, "s")
        assertTrue(official.verified && official.playOnly && !official.tracked)
        assertEquals(LibraryStore.Category.HACK, official.category)
        assertEquals("Pokémon Heart & Soul 2.0.6 · No tracker", official.subtitle)
        assertTrue(kaizo.tracked && !kaizo.playOnly)
        assertEquals(LibraryStore.Category.PATCHED, kaizo.category)
        assertEquals("Pokémon Heart & Soul (KaizoCore) · Tracker works", kaizo.subtitle)
        assertNull(GameSession.trackerKind(RomKind.HEARTSOUL_206, 0x01713508L))
        assertEquals(RomKind.HEARTSOUL_KAIZO_206, GameSession.trackerKind(RomKind.HEARTSOUL_KAIZO_206, 0xC993EB6EL))
        // Nuzlocke's plain games: the KaizoCore build, never the official one.
        assertEquals(listOf(kaizo), NuzlockeStarts.plainGames(listOf(official, kaizo)))
        assertTrue(LibraryImport.addedLine(official).contains("Pokémon Heart & Soul on Home"))
    }

    @Test
    fun `Kaizo IronMON lists the KaizoCore build from the Library and never the official game`() {
        val d = tmp()
        val store = PrepStore(d)
        // Bytes that identify as neither: listPrepared reads the sidecar the import wrote, so give each its kind there.
        val official = store.library.import("Pokémon Heart & Soul 2.0.6.gba", ByteArray(64) { 1 })
        val kaizo = store.library.import("Pokémon Heart & Soul (KaizoCore).gba", ByteArray(64) { 2 })
        fun pin(e: LibraryStore.Entry, k: RomKind, crc: Long) {
            val meta = File(e.file.parentFile, e.file.name + ".meta")
            val lines = meta.readLines().toMutableList()
            lines[1] = "%08x".format(crc); lines[2] = k.id
            meta.writeText(lines.joinToString("\n"))
        }
        pin(official, RomKind.HEARTSOUL_206, 0x01713508L)
        pin(kaizo, RomKind.HEARTSOUL_KAIZO_206, 0xC993EB6EL)
        assertEquals(listOf(RomKind.HEARTSOUL_KAIZO_206.id), store.listPrepared().map { it.first.id })
    }

    // ------------------------------------------------------------------ the engine and the modes

    @Test
    fun `Heart and Soul's engine is HnsEngine, named and versioned with the pool`() {
        assertEquals(Engine.HNS, RomKind.HEARTSOUL_KAIZO_206.engine)
        assertEquals(HnsEngine.DISPLAY_NAME, Randomizers.engineName(RomKind.HEARTSOUL_KAIZO_206))
        assertEquals("hns-1.0 natdex", Randomizers.engineId(RomKind.HEARTSOUL_KAIZO_206, HnsEngine.Pool.NATDEX))
        assertEquals("hns-1.0 vanilla", Randomizers.engineId(RomKind.HEARTSOUL_KAIZO_206, HnsEngine.Pool.VANILLA))
        assertEquals(HnsEngine.Pool.VANILLA, Randomizers.hnsPoolOf("hns-1.0 vanilla"))
        assertEquals(HnsEngine.Pool.NATDEX, Randomizers.hnsPoolOf(Randomizers.engineId(RomKind.HEARTSOUL_KAIZO_206, HnsEngine.Pool.NATDEX)))
        assertNull(Randomizers.hnsPoolOf(Randomizers.engineId(RomKind.EMERALD_NATDEX_121)))
        assertNull(Randomizers.hnsPoolOf(null))
        // Every other game's id is what it was.
        assertEquals(com.ironmonone.app.engine.NatDexEngine.ID, Randomizers.engineId(RomKind.EMERALD_NATDEX_121))
        // Build your own is not offered on it.
        assertFailsWith<IllegalArgumentException> { com.ironmonone.app.engine.GameFacts.read(RomKind.HEARTSOUL_KAIZO_206, File("none.gba")) }
    }

    @Test
    fun `a run made ahead with one pool is never taken for the other`() {
        val d = tmp()
        val rom = File(d, "hns.gba").apply { writeBytes(ByteArray(32)) }
        val settings = File(presets.first { it.name == "RSE NatDex v1.2 Kaizo.rnqs" }.path)
        val saved = Randomizers.hnsPoolChosen
        try {
            Randomizers.hnsPoolChosen = { HnsEngine.Pool.NATDEX }
            val natDex = NextRun.recipe(RomKind.HEARTSOUL_KAIZO_206, rom, settings, null, null, "app")
            Randomizers.hnsPoolChosen = { HnsEngine.Pool.VANILLA }
            val vanilla = NextRun.recipe(RomKind.HEARTSOUL_KAIZO_206, rom, settings, null, null, "app")
            assertNotEquals(natDex, vanilla)
            assertEquals(HnsEngine.Pool.VANILLA, Randomizers.hnsPoolOf(vanilla.engine))
            assertEquals(HnsEngine.Pool.NATDEX, Randomizers.hnsPoolOf(natDex.engine))
        } finally {
            Randomizers.hnsPoolChosen = saved
        }
    }

    @Test
    fun `the pool is the player's choice, kept, and Nat Dex until chosen`() {
        val d = tmp()
        assertEquals(HnsEngine.Pool.NATDEX, HnsPool.chosen(d))
        HnsPool.choose(d, HnsEngine.Pool.VANILLA)
        assertEquals(HnsEngine.Pool.VANILLA, HnsPool.chosen(d))
        HnsPool.choose(d, HnsEngine.Pool.NATDEX)
        assertEquals(HnsEngine.Pool.NATDEX, HnsPool.chosen(d))
        assertEquals(listOf("Vanilla (Gen 1-3)", "Nat. Dex (Gen 1-9)"), HnsPool.LABELS.map { it.second })
    }

    @Test
    fun `Heart and Soul plays every Emerald Nat Dex v1_2 mode and no other game's files`() {
        val k = RomKind.HEARTSOUL_KAIZO_206
        val modes = RulesetCatalog.forRom(k, presets)
        assertEquals(listOf("standard", "ultimate", "kaizo", "superkaizo", "survival", "survivalrevival", "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney"),
            modes.map { it.key })
        assertTrue(modes.all { it.preset.name.startsWith("RSE NatDex v1.2 ") }, modes.map { it.preset.name }.toString())
        assertEquals("RSE NatDex v1.2 Kaizo.rnqs", RulesetCatalog.openingFile(k, presets, null, null)!!.name)
        assertNull(RulesetCatalog.superKaizoWarning(k, "superkaizo"), "HnsEngine applies the Super Kaizo file's smart AI")
        for (f in presets) {
            val ok = RunPairing.problem(k to File("x.gba"), f)
            if (f.name.startsWith("FRLG MaxDex")) assertEquals(RunPairing.HNS_GAME, ok, f.name) else assertNull(ok, f.name)
        }
        // A Nat. Dex game still refuses a vanilla file, and nothing else's pairing moved.
        assertEquals(RunPairing.NAT_DEX_GAME, RunPairing.problem(RomKind.EMERALD_NATDEX_121 to File("x.gba"), presets.first { it.name == "RSE Kaizo.rnqs" }))
        // The official game is played as it is: it has no runs and no modes of its own to start one.
        assertTrue(RomKind.HEARTSOUL_206.playOnly)
        // Nuzlocke fair writes the Nat. Dex fork's settings for it, which HnsEngine reads.
        assertEquals(com.dabomstew.pkrandom.Settings::class.java, GameBuild.settingsClass(k))
        assertEquals(60, GameBuild.maxLevelBoost(k))
    }

    @Test
    fun `the pool the run asks for reaches HnsEngine, and the default is the chosen one`() {
        val romFile = HnsEngineTest.romFile ?: return println("HnsIntegrationTest skipped: no hns-kaizo.gba")
        val d = tmp()
        val settings = presets.first { it.name == "RSE NatDex v1.2 Kaizo.rnqs" }
        val assets = HnsEngine.folderAssets(File("src/main/assets"))
        val savedAssets = HnsEngine.assetText
        val savedPool = Randomizers.hnsPoolChosen
        try {
            HnsEngine.assetText = assets
            val seed = 4242L
            val vanilla = File(d, "v.gba")
            Randomizers.randomize(RomKind.HEARTSOUL_KAIZO_206, romFile, settings, vanilla, seed, pool = HnsEngine.Pool.VANILLA)
            val direct = HnsEngine.randomize(romFile.readBytes(), HnsEngine.readSettings(settings), seed, HnsEngine.Pool.VANILLA, assets)
            assertContentEquals(direct.rom, vanilla.readBytes(), "Randomizers hands the pool to HnsEngine")
            assertEquals(direct.logText, Randomizers.logFor(vanilla).readText(), "and keeps its log beside the ROM")
            // No pool given: the one chosen (HnsPool through MainActivity's provider) is taken.
            Randomizers.hnsPoolChosen = { HnsEngine.Pool.NATDEX }
            val chosen = File(d, "n.gba")
            Randomizers.randomize(RomKind.HEARTSOUL_KAIZO_206, romFile, settings, chosen, seed)
            val natDex = HnsEngine.randomize(romFile.readBytes(), HnsEngine.readSettings(settings), seed, HnsEngine.Pool.NATDEX, assets)
            assertContentEquals(natDex.rom, chosen.readBytes())
            assertFalse(natDex.rom.contentEquals(direct.rom), "the two pools make different games")
            // The log viewer reads its log.
            val log = RandomizerLog.parse(Randomizers.logFor(chosen))
            assertNotNull(log)
            assertTrue(log.pokemon.isNotEmpty())
        } finally {
            HnsEngine.assetText = savedAssets
            Randomizers.hnsPoolChosen = savedPool
            d.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ the screens that number species

    private class RomMemory(val rom: ByteArray) : MemoryReader {
        override fun read(address: Long, length: Int): ByteArray {
            if (address in 0x08000000L..0x09FFFFFFL) {
                val o = (address - 0x08000000L).toInt()
                if (o >= rom.size) return ByteArray(0)
                return rom.copyOfRange(o, minOf(rom.size, o + length))
            }
            return ByteArray(length)
        }
    }

    @Test
    fun `the log names every Heart and Soul species by its own id, and GachaMon lists the BST of the same Pokemon`() {
        val romFile = HnsEngineTest.romFile ?: return println("HnsIntegrationTest skipped: no hns-kaizo.gba")
        val mem = RomMemory(romFile.readBytes())
        val t = GbaTracker(mem, GameMap.resolve(mem))
        assertTrue(t.heartSoul)
        assertEquals(1572, t.speciesIdCount)
        val names = LogNames.of(t)
        // Past the Nat. Dex build's 1300: forms such as Oinkologne's female (1300) and the starter Pikachu (1487).
        assertEquals(252, names.speciesId("TREECKO"))
        assertEquals(387, names.speciesId("TURTWIG"))
        assertNotNull(names.speciesId("SPRIGATITO"))
        // GachaMon: Treecko's listed BST is Treecko's (310), not Gen 3's empty slot 252.
        val game = GbaGachaMonGame(t)
        assertTrue(game.heartSoul)
        assertEquals(310, GachaMon.listedBst(252, true, game))
        assertEquals(318, GachaMon.listedBst(1, true, game), "Bulbasaur")
        // Walking Pals number it its own way.
        assertEquals(WalkingPals.Dex.HNS, WalkingPals.trackerDex(3, 1572, hns = true))
        assertEquals(WalkingPals.Dex.HNS, SpriteLead.dexOf(GameMap.resolve(mem)))
    }
}
