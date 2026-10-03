package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gold, Silver and Crystal before a run (2026-09-30, IronMON rules check R3): the rules want the pseudo-fluctuating
 * growth patch, Crystal must have it, and an unpatched copy from My games ran with nothing said.
 */
class GrowthPatchTest {
    @Test
    fun `only an unpatched Gold, Silver or Crystal is warned`() {
        assertEquals(RomKind.CRYSTAL_PF, GrowthPatch.buildFor(RomKind.CRYSTAL_U))
        assertEquals(RomKind.GOLD_PF, GrowthPatch.buildFor(RomKind.GOLD_U))
        assertEquals(RomKind.SILVER_PF, GrowthPatch.buildFor(RomKind.SILVER_U))
        val warned = RomKind.all.filter { GrowthPatch.warning(it) != null }.map { it.id }.toSet()
        assertEquals(setOf(RomKind.GOLD_U.id, RomKind.SILVER_U.id, RomKind.CRYSTAL_U.id), warned, "the patched builds and every other game: nothing")
        // Gen 1 has the rules' other way (PART 2), so it is never warned.
        for (k in listOf(RomKind.RED_U, RomKind.BLUE_U, RomKind.YELLOW_U, RomKind.CRYSTAL_PF)) assertNull(GrowthPatch.warning(k), k.id)
    }

    @Test
    fun `the words say what the rules say, plainly`() {
        val crystal = assertNotNull(GrowthPatch.warning(RomKind.CRYSTAL_U))
        assertTrue("need the pseudo-fluctuating growth patch on Crystal" in crystal)
        assertTrue("legendaries" in crystal)
        val gold = assertNotNull(GrowthPatch.warning(RomKind.GOLD_U))
        assertTrue("may not work completely on Gold" in gold, gold)
        for (s in listOf(crystal, gold, GrowthPatch.BUTTON, GrowthPatch.FAILED, GrowthPatch.made(RomKind.CRYSTAL_PF)))
            assertTrue('\u2014' !in s && '\u2013' !in s, s)
    }

    @Test
    fun `the screen shows it under the mode, and the button makes the copy and picks it`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("GrowthPatch.warning(rom)?.let { w ->" in run)
        assertTrue("GrowthPatch.made(GrowthPatch.make(context, store, rom, base)) to false" in run)
        assertTrue("pickAfterPatch = out.id to selectedSettings?.name" in run, "the mode picked stays picked")
    }

    /** The bundled patch on a real dump gives the pinned build. Needs IRONMON_ROMS with crystal-u.gbc (or gold, silver). */
    @Test
    fun `the patch made from the stored copy is the pinned build`() {
        val dir = Dumps.romsDir() ?: return
        var checked = 0
        for (k in listOf(RomKind.GOLD_U, RomKind.SILVER_U, RomKind.CRYSTAL_U)) {
            val rom = Dumps.file(dir, k.id + "." + k.fileExtension) ?: continue
            val out = GrowthPatch.buildFor(k)!!
            val asset = PrepOptions.forKind(k).first { it.out?.id == out.id }.asset!!
            val tmp = File.createTempFile("growth", "." + out.fileExtension).apply { deleteOnExit() }
            val crc = com.ironmonone.patch.Patcher.applyFiles(File("src/main/assets/patches/$asset"), rom, tmp)
            assertEquals("%08x".format(out.expectedCrc), "%08x".format(crc), k.id)
            checked++
        }
        println("GROWTH_CHECKED=$checked")
    }
}
