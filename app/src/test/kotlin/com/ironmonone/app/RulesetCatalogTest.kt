package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Modes are derived from preset files, and the derivation must be as strict
 * as the pairing guard: never a mode from another game's family, never a
 * Nat. Dex preset for a vanilla ROM or the reverse.
 */
class RulesetCatalogTest {

    private val files = listOf(
        "FRLG Kaizo.rnqs",
        "FRLG Kaizo (edited).rnqs",
        "FRLG Super Kaizo.rnqs",
        "FRLG NatDex v1.2 Kaizo.rnqs",
        "RSE Kaizo.rnqs",
        "DPPt Kaizo.rnqs",
        "my custom.rnqs",              // no game tag: not a mode
    ).map { File("/presets/$it") }

    @Test
    fun `vanilla FireRed offers its own family's modes, in the order the rules build`() {
        val modes = RulesetCatalog.forRom(RomKind.FIRERED_U_V11, files)
        assertEquals(listOf("kaizo", "superkaizo"), modes.map { it.key })
        assertEquals(listOf("Kaizo", "Super Kaizo"), modes.map { it.label })
    }

    @Test
    fun `the untouched preset wins over an edited copy, which stays reachable`() {
        val kaizo = RulesetCatalog.forRom(RomKind.FIRERED_U_V11, files).first()
        assertEquals("FRLG Kaizo.rnqs", kaizo.preset.name)
        assertEquals(listOf("FRLG Kaizo (edited).rnqs"), kaizo.alternatives.map { it.name })
    }

    @Test
    fun `a Nat Dex ROM only sees Nat Dex presets`() {
        val modes = RulesetCatalog.forRom(RomKind.FIRERED_NATDEX_121, files)
        assertEquals(listOf("FRLG NatDex v1.2 Kaizo.rnqs"), modes.map { it.preset.name })
    }

    @Test
    fun `another family's presets are never offered`() {
        val modes = RulesetCatalog.forRom(RomKind.PLATINUM_U, files)
        assertEquals(listOf("DPPt Kaizo.rnqs"), modes.map { it.preset.name })
        val emerald = RulesetCatalog.forRom(RomKind.EMERALD_U, files)
        assertEquals(listOf("RSE Kaizo.rnqs"), emerald.map { it.preset.name })
    }

    @Test
    fun `modeOf finds the mode through an alternative too`() {
        val modes = RulesetCatalog.forRom(RomKind.FIRERED_U_V11, files)
        assertEquals("kaizo", RulesetCatalog.modeOf(modes, File("/x/FRLG Kaizo (edited).rnqs"))?.key)
        assertNull(RulesetCatalog.modeOf(modes, File("/x/RSE Kaizo.rnqs")))
        assertNull(RulesetCatalog.modeOf(modes, null))
    }

    @Test
    fun `a ROM with no matching preset has no modes rather than a wrong one`() {
        assertEquals(emptyList(), RulesetCatalog.forRom(RomKind.EMERALD_NATDEX_121, files))
    }

    @Test
    fun `Super Kaizo is offered only where its rules are written`() {
        val sk = listOf("RSE Super Kaizo.rnqs", "DPPt Super Kaizo.rnqs", "HGSS Super Kaizo.rnqs", "FRLG Super Kaizo.rnqs", "RSE Kaizo.rnqs", "DPPt Kaizo.rnqs").map { File("/presets/$it") }
        for (k in listOf(RomKind.RUBY_U, RomKind.SAPPHIRE_U, RomKind.DIAMOND_U, RomKind.PEARL_U)) {
            assertEquals(listOf("kaizo"), RulesetCatalog.forRom(k, sk).map { it.key }, k.id)
            assertTrue(RulesetCatalog.isCompatible(k, File("/presets/" + k.family + " Super Kaizo.rnqs")), "the file itself stays pickable")
        }
        for (k in listOf(RomKind.EMERALD_U, RomKind.EMERALD_SMARTAI, RomKind.PLATINUM_U, RomKind.PLATINUM_SUPERKAIZO,
                RomKind.HEARTGOLD_U, RomKind.SOULSILVER_U, RomKind.FIRERED_U_V10, RomKind.LEAFGREEN_SMARTAI))
            assertTrue("superkaizo" in RulesetCatalog.forRom(k, sk).map { it.key }, k.id)
    }

    @Test
    fun `Super Kaizo on a build without smart AI is a warning that says where the build is made`() {
        // Builds that carry smart AI, and every other mode, say nothing.
        for (k in listOf(RomKind.EMERALD_SMARTAI, RomKind.FIRERED_V10_SMARTAI, RomKind.LEAFGREEN_SMARTAI,
                RomKind.HEARTGOLD_SUPERKAIZO, RomKind.PLATINUM_SUPERKAIZO, RomKind.EMERALD_NATDEX_121, RomKind.FIRERED_NATDEX_121))
            assertNull(RulesetCatalog.superKaizoWarning(k, "superkaizo"), k.id)
        for (m in listOf("kaizo", "survival", "standard", null)) assertNull(RulesetCatalog.superKaizoWarning(RomKind.EMERALD_U, m))
        // A clean build names the PREPARE option that makes the right one.
        val named = mapOf(RomKind.EMERALD_U to "Super Kaizo: Smart AI patch", RomKind.EMERALD_FASTER to "Super Kaizo: Smart AI patch",
            RomKind.FIRERED_U_V10 to "Super Kaizo: Smart AI patch", RomKind.LEAFGREEN_U to "Super Kaizo: Smart AI patch",
            RomKind.HEARTGOLD_U to "Super Kaizo 0.0.3 patch", RomKind.PLATINUM_U to "Super Kaizo 1.0 patch")
        for ((k, option) in named) {
            val w = RulesetCatalog.superKaizoWarning(k, "superkaizo")!!
            assertTrue("\"$option\"" in w && "Patched versions" in w, "${k.id}: $w")
            assertTrue(PrepOptions.forKind(RomKind.byId(k.baseId) ?: k).any { it.label == option }, "${k.id}: the option exists")
        }
        // Where no build exists, it says so instead.
        assertTrue("HeartGold only" in RulesetCatalog.superKaizoWarning(RomKind.SOULSILVER_U, "superkaizo")!!)
        assertTrue("FireRed (U) v1.1" in RulesetCatalog.superKaizoWarning(RomKind.FIRERED_U_V11, "superkaizo")!!)
        assertTrue("FireRed (U) v1.1" in RulesetCatalog.superKaizoWarning(RomKind.FIRERED_V11_FASTER, "superkaizo")!!)
        assertTrue("written for Emerald" in RulesetCatalog.superKaizoWarning(RomKind.RUBY_U, "superkaizo")!!)
        assertTrue("written for Platinum" in RulesetCatalog.superKaizoWarning(RomKind.PEARL_U, "superkaizo")!!)
        for (k in RomKind.all) RulesetCatalog.superKaizoWarning(k, "superkaizo")?.let { assertTrue('\u2014' !in it, "${k.id}: em dash") }
    }
}
