package com.ironmonone.app

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepOptionsTest {
    @Test
    fun `Gen 1 opens on Standard, with the pseudo-fluctuating patch second`() {
        for (k in listOf(RomKind.RED_U, RomKind.BLUE_U, RomKind.YELLOW_U)) {
            val o = PrepOptions.forKind(k)
            // Standard first (2026-09-30, UX audit P0-10): the rules give Gen 1 two ways, and the other is PART 2.
            assertEquals(PrepOptions.Mode.STANDARD, o.first().mode, k.id)
            assertEquals("Standard (the game as it is)", o.first().label)
            assertEquals(PrepOptions.Mode.PATCH, o[1].mode, k.id)
            assertEquals("pseudofluct", o[1].out!!.patchTag)
            assertEquals("pseudofluct-${k.id}.bps", o[1].asset)
            // Named for what it is: the patch applies to every mode, not only Kaizo.
            assertEquals("Pseudo-fluctuating growth patch", o[1].label)
        }
    }

    /** IronMON rules check R3 (2026-09-30): "you must first apply the pseudo-fluctuating growth patch" on Crystal. */
    @Test
    fun `Gold, Silver and Crystal open on the growth patch, Standard second`() {
        for (k in listOf(RomKind.GOLD_U, RomKind.SILVER_U, RomKind.CRYSTAL_U)) {
            val o = PrepOptions.forKind(k)
            assertEquals(PrepOptions.Mode.PATCH, o.first().mode, k.id)
            assertEquals("pseudofluct-${k.id}.bps", o.first().asset)
            assertEquals(o.first(), PrepOptions.default(k), "${k.id}: and the one the page opens on")
            assertEquals(PrepOptions.Mode.STANDARD, o[1].mode, k.id)
            assertTrue("Pick this if you are not sure." in PrepOptions.describe(o.first(), k), k.id)
            assertEquals("The game as it is. For Kaizo IronMON, pick the growth patch.", PrepOptions.describe(o[1], k))
        }
    }

    /** The one rule of the page (2026-09-30, UX audit P0-10): Standard is first, preselected and the words are the ones given. */
    @Test
    fun `Standard is first and preselected for every game but Gold, Silver and Crystal, and the two labels say what they are`() {
        for (k in RomKind.all.filter { GrowthPatch.buildFor(it) == null }) {
            val o = PrepOptions.forKind(k)
            assertEquals(PrepOptions.Mode.STANDARD, o.first().mode, "${k.id}: Standard is the first option")
            assertEquals(o.first(), PrepOptions.default(k), "${k.id}: and the one the page opens on")
            assertEquals(1, o.count { it.mode == PrepOptions.Mode.STANDARD }, k.id)
        }
        val emerald = PrepOptions.forKind(RomKind.EMERALD_U)
        assertEquals("Standard (the game as it is)", emerald[0].label)
        assertEquals("Pick this if you are not sure.", PrepOptions.describe(emerald[0]))
        assertEquals("Nat. Dex (adds Pokémon from later games)", emerald[1].label)
        assertEquals(NatDexInfo.SHORT, PrepOptions.describe(emerald[1]))
        // The same words for a game with no Nat. Dex, and for Gen 1 and 2, which used to say "Vanilla".
        for (k in listOf(RomKind.HEARTGOLD_U, RomKind.RED_U, RomKind.FIRERED_U_V10)) {
            assertEquals("Standard (the game as it is)", PrepOptions.default(k).label, k.id)
            assertEquals("Pick this if you are not sure.", PrepOptions.describe(PrepOptions.default(k)), k.id)
        }
        // Nat. Dex is offered only where its patch exists.
        assertEquals(setOf(RomKind.EMERALD_U.id, RomKind.FIRERED_U_V11.id), RomKind.all.filter { k -> PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.NATDEX } }.map { it.id }.toSet())
        // No retired word survives on the page's labels.
        for (k in RomKind.all) for (o in PrepOptions.forKind(k)) assertTrue("Vanilla" !in o.label && "vanilla" !in PrepOptions.describe(o), "${k.id}: ${o.label}")
    }

    @Test
    fun `Gen 3 offers Smart AI beside the existing choices, never on Nat Dex`() {
        assertEquals(listOf("STANDARD", "NATDEX", RomKind.EMERALD_FASTER.id, RomKind.EMERALD_FASTER121.id, RomKind.EMERALD_SMARTAI.id), PrepOptions.forKind(RomKind.EMERALD_U).map { it.id })
        assertEquals("faster-${RomKind.EMERALD_U.id}.ups", PrepOptions.forKind(RomKind.EMERALD_U)[2].asset)
        assertEquals("faster121-${RomKind.EMERALD_U.id}.ips", PrepOptions.forKind(RomKind.EMERALD_U)[3].asset)
        assertEquals(listOf("STANDARD", RomKind.FIRERED_V10_SMARTAI.id), PrepOptions.forKind(RomKind.FIRERED_U_V10).map { it.id })
        assertEquals(listOf("STANDARD", "NATDEX", RomKind.FIRERED_V11_FASTER.id), PrepOptions.forKind(RomKind.FIRERED_U_V11).map { it.id })
        assertEquals("faster-${RomKind.FIRERED_U_V11.id}.ips", PrepOptions.forKind(RomKind.FIRERED_U_V11)[2].asset)
        assertEquals(1, PrepOptions.forKind(RomKind.EMERALD_NATDEX_121).size)
        assertEquals(1, PrepOptions.forKind(RomKind.EMERALD_SMARTAI).size)
    }

    @Test
    fun `HeartGold and Platinum offer Super Kaizo, Black 2 and White 2 offer Faster B2W2`() {
        assertEquals(listOf("STANDARD", RomKind.HEARTGOLD_SUPERKAIZO.id), PrepOptions.forKind(RomKind.HEARTGOLD_U).map { it.id })
        assertEquals("superkaizo-${RomKind.HEARTGOLD_U.id}.xdelta", PrepOptions.forKind(RomKind.HEARTGOLD_U)[1].asset)
        assertEquals(listOf("STANDARD", RomKind.PLATINUM_SUPERKAIZO.id), PrepOptions.forKind(RomKind.PLATINUM_U).map { it.id })
        assertEquals("superkaizo-${RomKind.PLATINUM_U.id}.xdelta", PrepOptions.forKind(RomKind.PLATINUM_U)[1].asset)
        assertEquals("Super Kaizo 1.0 patch", PrepOptions.forKind(RomKind.PLATINUM_U)[1].label)
        assertEquals("Super Kaizo 0.0.3 patch", PrepOptions.forKind(RomKind.HEARTGOLD_U)[1].label)
        // Diamond and Pearl have no Super Kaizo patch (the README's Platinum patch is for Platinum 1.0 only).
        assertEquals(listOf("STANDARD"), PrepOptions.forKind(RomKind.DIAMOND_U).map { it.id })
        assertEquals(listOf("STANDARD", RomKind.BLACK2_FASTER.id, RomKind.BLACK2_FASTERPWT.id), PrepOptions.forKind(RomKind.BLACK2_U).map { it.id })
        assertEquals(listOf("STANDARD", RomKind.WHITE2_FASTER.id, RomKind.WHITE2_FASTERPWT.id), PrepOptions.forKind(RomKind.WHITE2_U).map { it.id })
        assertEquals("fasterpwt-${RomKind.WHITE2_U.id}.xdelta", PrepOptions.forKind(RomKind.WHITE2_U)[2].asset)
        assertEquals(listOf("STANDARD"), PrepOptions.forKind(RomKind.BLACK_U).map { it.id })
        // Every bundled patch an option names exists in the build.
        for (k in RomKind.allV1) for (o in PrepOptions.forKind(k)) o.asset?.let {
            assertTrue(java.io.File("src/main/assets/patches/$it").isFile, it)
        }
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
