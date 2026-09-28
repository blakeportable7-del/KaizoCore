package com.ironmonone.app

import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.RomKind
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COPY, Save As and PASTE in the settings editor (2026-09-27, audit).
 *
 * COPY put the bare base64 body on the clipboard, which our own PASTE and
 * the desktop randomizer both refuse; a preset saved as "My run" lost the
 * tokens the Run tab pairs on.
 */
class PresetStringsTest {

    private val presets = File("../app/src/main/assets/presets")

    @Test
    fun `a copied ZX string pastes back into the same settings`() {
        val f = File(presets, "FRLG Kaizo.rnqs")
        val s = FileInputStream(f).use { com.dabomstew.pkrandomzx.Settings.read(it) }
        val copied = PresetStrings.copyString(s, com.dabomstew.pkrandomzx.Settings::class.java)
        assertTrue(copied.startsWith("322"), copied.take(8))
        assertNull(ZxEngine.validateSettingsString(copied))
        assertEquals(s.toString(), ZxEngine.parseSettingsString(copied).toString())
    }

    @Test
    fun `a copied Nat Dex string pastes back into the same settings`() {
        val f = File(presets, "FRLG NatDex v1.2 Kaizo.rnqs")
        val s = FileInputStream(f).use { com.dabomstew.pkrandom.Settings.read(it) }
        val copied = PresetStrings.copyString(s, com.dabomstew.pkrandom.Settings::class.java)
        assertTrue(copied.startsWith("908"), copied.take(8))
        assertNull(NatDexEngine.validateSettingsString(copied))
        assertEquals(s.toString(), NatDexEngine.parseSettingsString(copied).toString())
    }

    @Test
    fun `suggested names keep the tokens the Run tab pairs on`() {
        val natdex = RnqsInfo.parse("FRLG NatDex v1.2 Kaizo.rnqs")
        assertEquals("FRLG NatDex v1.2 Kaizo (my edit)", PresetStrings.suggestedName(natdex, true, "my edit"))
        val suggested = RnqsInfo.parse(PresetStrings.suggestedName(natdex, true, "my edit") + ".rnqs")
        assertEquals("FRLG", suggested.gameTag); assertTrue(suggested.natDex); assertEquals("kaizo", suggested.ruleset)
        // A paste has no file of its own: the game and engine name it.
        assertEquals("RSE NatDex (pasted)", PresetStrings.suggestedName(null, true, "pasted", family = "RSE"))
    }

    @Test
    fun `a preset renamed without tokens still pairs through its sidecar`() {
        val dir = kotlin.io.path.createTempDirectory("rnqs").toFile()
        try {
            val f = File(dir, "My run.rnqs")
            File(presets, "FRLG NatDex v1.2 Kaizo.rnqs").copyTo(f)
            // Before: the name alone said "no game, vanilla".
            assertNull(RnqsInfo.parse(f.name).gameTag)
            RnqsInfo.writeMeta(f, "FRLG", true, "kaizo")
            val i = RnqsInfo.of(f)
            assertEquals("FRLG", i.gameTag); assertTrue(i.natDex); assertEquals("kaizo", i.ruleset)
            assertTrue(RulesetCatalog.isCompatible(RomKind.allNatDex.first { it.family == "FRLG" }, f))
            assertFalse(RulesetCatalog.isCompatible(RomKind.FIRERED_U_V11, f))
            assertEquals("kaizo", RulesetCatalog.forRom(RomKind.allNatDex.first { it.family == "FRLG" }, listOf(f)).single().key)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an untagged file with no sidecar is judged by the engine that reads it`() {
        val dir = kotlin.io.path.createTempDirectory("rnqs").toFile()
        try {
            val nd = File(dir, "custom a.rnqs").also { File(presets, "RSE NatDex v1.2 Kaizo.rnqs").copyTo(it) }
            val zx = File(dir, "custom b.rnqs").also { File(presets, "RSE Kaizo.rnqs").copyTo(it) }
            assertTrue(RnqsInfo.of(nd).natDex)
            assertFalse(RnqsInfo.of(zx).natDex)
        } finally {
            dir.deleteRecursively()
        }
    }
}
