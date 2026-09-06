package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `vanilla FireRed offers its own family's modes, in difficulty order`() {
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
}
