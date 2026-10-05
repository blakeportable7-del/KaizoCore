package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): run codes carry the pool; the Library's names.
 */
class HnsRunCodeLibraryTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `a Heart and Soul run code names its pool, and the other pool's run is never taken for it`() {
        fun recipe(engine: String) = NextRun.Recipe(hns.id, "gba", engine, "app", "rom", 1L, 1L, "e08dd128", "RSE NatDex v1.2 Kaizo.rnqs", "0123456789abcdef", "", "")
        val nat = RunCodes.shareCodeFor(recipe("hns-1.0 natdex"), 42L, 0x1234L)
        val van = RunCodes.shareCodeFor(recipe("hns-1.0 vanilla"), 42L, 0x1234L)
        assertTrue(nat.hnsNatDexPool && !van.hnsNatDexPool)
        assertEquals(com.ironmonone.app.engine.HnsEngine.Pool.NATDEX, RunCodes.hnsPoolOf(RunCode.parse(nat.text())!!))
        assertEquals(com.ironmonone.app.engine.HnsEngine.Pool.VANILLA, RunCodes.hnsPoolOf(RunCode.parse(van.text())!!))
        assertFalse(RunCode.parse(nat.text())!!.unknownPasses)
        val emerald = RunCodes.shareCodeFor(recipe("natdex").copy(kind = RomKind.EMERALD_NATDEX_121.id), 42L, 0L)
        assertFalse(emerald.hnsNatDexPool)
        // Without the build, the code says where it is made: the Heart & Soul button, not Patched versions.
        val refused = RunCodes.plan(nat.text(), emptyList(), emptyList()) { "" }
        assertTrue(refused is RunCodes.Plan.Refused && "Heart & Soul on Home" in refused.why, "$refused")
    }

    @Test
    fun `the Library offers the KaizoCore build's own name, never a clean one`() {
        val f = File.createTempFile("hns", ".gba").apply { deleteOnExit() }
        val e = LibraryStore.Entry(f, "x.gba", hns.expectedCrc, hns, com.ironmonone.core.Platform.GBA, "s")
        val names = LibraryStore(java.nio.file.Files.createTempDirectory("hns-lib").toFile()).suggestions(e)
        assertEquals(hns.displayName, names.first())
        assertTrue(names.none { it.endsWith(" clean") }, "$names")
    }
}
