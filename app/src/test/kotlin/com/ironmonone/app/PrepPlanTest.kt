package com.ironmonone.app

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PREP describes what it will do per game, and offers Nat. Dex only where it exists. */
class PrepPlanTest {
    @Test
    fun `each family gets its own plan`() {
        // Standard comes first in every line, as it does in the list of choices (2026-09-30, UX audit P0-10).
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).first().startsWith("Standard:"))
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).first().contains("Nat. Dex:"))
        assertTrue(PrepPlan.lines(RomKind.FIRERED_U_V11).first().contains("Faster FireRed"), "FireRed 1.1 has three choices, and the screen must name the third")
        assertTrue(!PrepPlan.lines(RomKind.EMERALD_U).first().contains("Faster FireRed"), "Emerald names its own patch, not FireRed's")
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).first().contains("Faster Emerald"))
        assertTrue(PrepPlan.lines(RomKind.WHITE2_U).first().contains("Faster B2W2"))
        assertTrue(PrepPlan.lines(RomKind.LEAFGREEN_U).first().contains("Smart AI"), "Super Kaizo is offered where its patch exists")
        assertTrue(PrepPlan.lines(RomKind.RUBY_U).first().contains("No patch applies"))
        assertTrue(PrepPlan.lines(RomKind.RED_U).first().startsWith("Standard:"))
        assertTrue(PrepPlan.lines(RomKind.RED_U).first().contains("Pseudo-fluctuating patch:"))
        // Either official way, never both: the patch with the first pass, or both passes without it.
        assertTrue(PrepPlan.lines(RomKind.RED_U).any { it.contains("either way") && it.contains("first pass only") && it.contains("two passes") })
        assertTrue(PrepPlan.lines(RomKind.RED_U).none { it.contains("as the IronMON rules require") || it.startsWith("Kaizo:") })
        assertTrue(PrepPlan.lines(RomKind.CRYSTAL_U).first().contains("require it for Crystal"))
        assertTrue(PrepPlan.lines(RomKind.HEARTGOLD_U).first().contains("Super Kaizo"))
        assertTrue(PrepPlan.lines(RomKind.HEARTGOLD_U).first().contains("IronMON HGSS"), "HeartGold has three choices, and the screen names each")
        assertEquals("Already carries the IronMON HGSS 0.2.2a patch. Stored as is, ready to randomize.", PrepPlan.lines(RomKind.HEARTGOLD_IRONMON).single())
        assertTrue(PrepPlan.lines(RomKind.PLATINUM_U).first().contains("Super Kaizo") && PrepPlan.lines(RomKind.PLATINUM_U).first().contains("Platinum 1.0"))
        assertTrue(PrepPlan.lines(RomKind.DIAMOND_U).first().contains("needs no patch"), "Diamond keeps the plain line: no Super Kaizo patch exists for it")
        assertTrue(PrepPlan.lines(RomKind.RED_PF).first().startsWith("Already carries"))
        assertTrue(PrepPlan.lines(RomKind.BLACK2_U).any { it.contains("needs no patch") })
        assertTrue(PrepPlan.lines(RomKind.RUBY_U).none { it.startsWith("Settings files") }, "no developer jargon on the screen")
        assertTrue(PrepPlan.lines(RomKind.EMERALD_NATDEX_121).first().startsWith("Already a Nat. Dex"))
    }

    /** FireRed 1.0 has a Super Kaizo patch, so it is not "Standard only"; and where v1.1 comes from is said (2026-09-30, UX audit P0-10). */
    @Test
    fun `FireRed 1_0 says Standard or the Super Kaizo patch, and what Nat Dex needs`() {
        assertTrue(PrepOptions.forKind(RomKind.FIRERED_U_V10).any { it.out?.patchTag == "smartai" }, "the patch is offered, which is what makes 'Standard only' wrong")
        val lines = PrepPlan.lines(RomKind.FIRERED_U_V10)
        assertEquals("Standard, or the Super Kaizo patch. Nat. Dex needs v1.1.", lines.first())
        assertEquals("FireRed v1.1 is the one whose file name may say Rev 1.", lines[1])
        assertTrue(lines.none { it.startsWith("Standard only") })
    }

    /** The plan sits beside the radio list, so it must not name a way the page does not offer or use the words the labels retired. */
    @Test
    fun `no plan talks about a clean dump or a vanilla game`() {
        for (k in RomKind.all) for (line in PrepPlan.lines(k)) {
            assertTrue("clean dump" !in line && "vanilla" !in line && "Vanilla" !in line, "${k.id}: $line")
            assertTrue('—' !in line && '–' !in line, "${k.id}: no dash in $line")
        }
    }
}
