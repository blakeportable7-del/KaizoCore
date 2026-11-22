package com.ironmonone.app

import com.ironmonone.app.engine.NatDex122Engine
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Engine
import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nat. Dex 1.2.2 beside 1.2.1 (Blake, 2026-11-20: "Don't hurt the players who are on the old system"). A 1.2.1 game
 * keeps everything it had: its id, its checksum, its randomizer. A 1.2.2 game is a game of its own, made by the patches
 * the app carries now and randomized by its own copy of the randomizer.
 */
class NatDexSideBySideTest {
    @Test
    fun `the 1_2_1 games are exactly what they were`() {
        for ((k, id, crc) in listOf(Triple(RomKind.EMERALD_NATDEX_121, "emerald-natdex-121", 0xEBFDCE4BL),
                Triple(RomKind.FIRERED_NATDEX_121, "firered-natdex-121", 0x33779943L))) {
            assertEquals(id, k.id); assertEquals(crc, k.expectedCrc)
            assertTrue(k.displayName.endsWith("Nat. Dex 1.2.1"))
            assertEquals(Engine.NATDEX, k.engine)
            assertEquals(NatDexEngine.ID, Randomizers.engineId(k), "a run made ahead on 1.2.1 still matches")
            assertTrue(k.supersededCrcs.isEmpty(), "not an older build of anything: it plays and randomizes as before")
        }
    }

    @Test
    fun `the 1_2_2 games are games of their own, Nat_ Dex in every rule`() {
        data class Case(val k: RomKind, val old: RomKind, val id: String, val crc: Long)
        for ((k, old, id, crc) in listOf(
                Case(RomKind.EMERALD_NATDEX_122, RomKind.EMERALD_NATDEX_121, "emerald-natdex-122", 0x7B4291C4L),
                Case(RomKind.FIRERED_NATDEX_122, RomKind.FIRERED_NATDEX_121, "firered-natdex-122", 0x34AAFED7L))) {
            assertEquals(id, k.id); assertEquals(crc, k.expectedCrc)
            assertEquals(Engine.NATDEX122, k.engine)
            assertEquals(NatDex122Engine.ID, Randomizers.engineId(k))
            assertTrue(k.isNatDex && !k.isMaxDex && k.fairyTypes)
            assertEquals(old.family, k.family); assertEquals(old.generation, k.generation); assertFalse(k.natDexCapable)
            assertTrue(k in RomKind.allNatDex && k in RomKind.natDexMade && old in RomKind.allNatDex && old !in RomKind.natDexMade)
            assertEquals(k, RomKind.byId(id))
        }
        assertEquals(RomKind.EMERALD_NATDEX_122, NatDexInfo.buildOf(RomKind.EMERALD_U), "making gives 1.2.2")
        assertEquals(RomKind.FIRERED_NATDEX_122, NatDexInfo.buildOf(RomKind.FIRERED_U_V11))
        assertEquals(RomKind.EMERALD_NATDEX_121, NatDexInfo.olderOf(RomKind.EMERALD_U))
        assertTrue("stay" in NatDexInfo.NEW_RELEASE && "1.2.1" in NatDexInfo.NEW_RELEASE)
        assertFalse('—' in NatDexInfo.NEW_RELEASE)
    }

    @Test
    fun `each randomizer takes only its own release`() {
        val roms = Dumps.romsDir() ?: return println("NatDexSideBySideTest: no dumps, the engine gate skipped")
        val old = Dumps.file(roms, "emerald-natdex-121.gba") ?: return
        val new = Dumps.file(roms, "emerald-natdex-122.gba") ?: return
        assertNotNull(NatDex122Engine.refusal(old), "1.2.2's offsets are never written into a 1.2.1 game")
        assertNull(NatDex122Engine.refusal(new))
        assertNull(NatDex122Engine.refusal(Dumps.file(roms, "firered-natdex-122.gba") ?: return))
    }

    @Test
    fun `a patch's footer says which game it makes`() {
        val dir = java.nio.file.Files.createTempDirectory("footer").toFile()
        val body = "BPS1".toByteArray() + byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte()) +
            byteArrayOf(0xFB.toByte(), 0x08, 0x1C, 0x1F) + byteArrayOf(0xC4.toByte(), 0x91.toByte(), 0x42, 0x7B)
        val f = File(dir, "p.bps").apply { writeBytes(body + ByteArray(4)) }
        assertEquals(0x7B4291C4L, BundledCopy.bpsTarget(f))
        assertNull(BundledCopy.bpsTarget(File(dir, "short.bps").apply { writeBytes(ByteArray(8)) }))
        dir.deleteRecursively()
    }

    @Test
    fun `the patches the app carries make 1_2_2`() {
        for (name in listOf("natdex-emerald.bps", "natdex-firered.bps")) {
            val t = BundledCopy.bpsTarget(File("src/main/assets/patches/$name"))
            assertTrue(RomKind.natDexMade.any { it.expectedCrc == t }, "$name makes ${"%08X".format(t)}")
        }
    }
}
