package com.ironmonone.app

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepOptionsTest {
    @Test
    fun `Gen 1 and 2 default to the pseudo-fluctuating patch, with vanilla second`() {
        for (k in listOf(RomKind.RED_U, RomKind.BLUE_U, RomKind.YELLOW_U, RomKind.GOLD_U, RomKind.SILVER_U, RomKind.CRYSTAL_U)) {
            val o = PrepOptions.forKind(k)
            assertEquals(PrepOptions.Mode.PATCH, o.first().mode, k.id)
            assertEquals("pseudofluct", o.first().out!!.patchTag)
            assertEquals("pseudofluct-${k.id}.bps", o.first().asset)
            assertEquals("Vanilla", o[1].label)
        }
    }

    @Test
    fun `Gen 3 offers Smart AI beside the existing choices, never on Nat Dex`() {
        assertEquals(listOf("NATDEX", "STANDARD", RomKind.EMERALD_SMARTAI.id), PrepOptions.forKind(RomKind.EMERALD_U).map { it.id })
        assertEquals(listOf("STANDARD", RomKind.FIRERED_V10_SMARTAI.id), PrepOptions.forKind(RomKind.FIRERED_U_V10).map { it.id })
        assertEquals(listOf("NATDEX", "STANDARD"), PrepOptions.forKind(RomKind.FIRERED_U_V11).map { it.id }, "1.1 waits on a dump to pin")
        assertEquals(1, PrepOptions.forKind(RomKind.EMERALD_NATDEX_121).size)
        assertEquals(1, PrepOptions.forKind(RomKind.EMERALD_SMARTAI).size)
    }

    @Test
    fun `HeartGold offers Super Kaizo, Black 2 offers nothing to patch`() {
        assertEquals(listOf("STANDARD", RomKind.HEARTGOLD_SUPERKAIZO.id), PrepOptions.forKind(RomKind.HEARTGOLD_U).map { it.id })
        assertEquals("superkaizo-${RomKind.HEARTGOLD_U.id}.xdelta", PrepOptions.forKind(RomKind.HEARTGOLD_U)[1].asset)
        assertEquals(listOf("STANDARD"), PrepOptions.forKind(RomKind.BLACK2_U).map { it.id })
    }

    @Test
    fun `patched kinds are distinct, pinned, and point at their base`() {
        val ids = RomKind.allPatched.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (k in RomKind.allPatched) {
            assertTrue(k.expectedCrc != RomKind.CRC_UNKNOWN, k.id)
            assertTrue(RomKind.byId(k.baseId) != null, k.id)
            assertEquals(RomKind.byId(k.baseId)!!.family, k.family)
            assertTrue(RomKind.all.count { it.expectedCrc == k.expectedCrc } == 1, "one kind per CRC: " + k.id)
        }
    }

    /** The bundled patches applied to the pinned dumps give the pinned CRCs. Needs IRONMON_ROMS=<dir with <kindid>.<ext>>. */
    @Test
    fun `bundled patches reproduce the pinned builds from real dumps`() {
        val dir = System.getenv("IRONMON_ROMS")?.let { java.io.File(it) }?.takeIf { it.isDirectory } ?: return
        var checked = 0
        for (k in RomKind.allPatched) {
            val base = RomKind.byId(k.baseId)!!
            val rom = java.io.File(dir, base.id + "." + base.fileExtension).takeIf { it.isFile } ?: continue
            val opt = PrepOptions.forKind(base).first { it.out?.id == k.id }
            val patch = java.io.File("src/main/assets/patches/" + opt.asset)
            assertTrue(patch.isFile, opt.asset!!)
            val out = java.io.File(System.getProperty("java.io.tmpdir"), "prep-" + k.id + "." + k.fileExtension)
            val crc = com.ironmonone.patch.Patcher.applyFiles(patch, rom, out)
            assertEquals("%08x".format(k.expectedCrc), "%08x".format(crc), k.id)
            checked++
        }
        println("PATCH_CHECKED=$checked")
    }
}
