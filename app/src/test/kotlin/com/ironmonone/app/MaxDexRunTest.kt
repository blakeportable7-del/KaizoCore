package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A MaxDex run pairs MaxDex 1.0 with MaxDex's own Kaizo file and with nothing else, both ways. MaxDex is a Nat. Dex
 * build, so the Nat. Dex flag alone let Nat. Dex 1.2's files reach it: their settings are version 908, MaxDex's
 * randomizer reads up to 904, and Nat. Dex 1.2's fork refuses MaxDex's 902 file as too old.
 */
class MaxDexRunTest {
    private val presets = File("src/main/assets/presets").listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }
    private val maxDexPreset = File("src/main/assets/presets/FRLG MaxDex Kaizo.rnqs")

    private fun assertPlain(text: String) {
        assertTrue(text.all { it.code < 0x80 || it == 'é' }, "not plain: $text")
        assertFalse(Regex("\\bAI\\b").containsMatchIn(text), text)
        assertFalse("Pokemon" in text, text)
    }

    @Test
    fun `MaxDex's file is read as FRLG, Kaizo, MaxDex and Nat Dex`() {
        val i = RnqsInfo.parse("FRLG MaxDex Kaizo.rnqs")
        assertEquals("FRLG", i.gameTag)
        assertEquals("kaizo", i.ruleset)
        assertTrue(i.maxDex)
        assertTrue(i.natDex, "MaxDex is a Nat. Dex build, so its file is a Nat. Dex file too")
        assertEquals("FRLG MaxDex Kaizo", i.label)
        // Nat. Dex 1.2's and the standard file are not MaxDex's.
        assertFalse(RnqsInfo.parse("FRLG NatDex v1.2 Kaizo.rnqs").maxDex)
        assertFalse(RnqsInfo.parse("FRLG Kaizo.rnqs").maxDex)
        assertEquals("FRLG Nat. Dex Kaizo", RnqsInfo.parse("FRLG NatDex v1.2 Kaizo.rnqs").label)
    }

    @Test
    fun `a renamed MaxDex file is still MaxDex's, by its sidecar or by which engine reads it`() {
        val dir = Files.createTempDirectory("maxdex-rnqs").toFile()
        try {
            // No name to go by and no sidecar: only the MaxDex randomizer reads a 902 file.
            val plain = File(dir, "My run.rnqs").apply { writeBytes(maxDexPreset.readBytes()) }
            assertTrue(RnqsInfo.of(plain).maxDex, "judged by the engines")
            assertTrue(RnqsInfo.of(plain).natDex)
            // A sidecar says so too, and a sidecar written for another game says otherwise.
            val saved = File(dir, "Saved.rnqs").apply { writeBytes(maxDexPreset.readBytes()) }
            RnqsInfo.writeMeta(saved, "FRLG", natDex = true, ruleset = "kaizo", maxDex = true)
            assertTrue(RnqsInfo.of(saved).maxDex)
            assertEquals("kaizo", RnqsInfo.of(saved).ruleset)
            val natDex = File(dir, "Other.rnqs").apply { writeBytes(File("src/main/assets/presets/FRLG NatDex v1.2 Kaizo.rnqs").readBytes()) }
            assertFalse(RnqsInfo.of(natDex).maxDex, "a Nat. Dex 1.2 file read by its own engine")
            assertTrue(RnqsInfo.of(natDex).natDex)
            val zx = File(dir, "Zx.rnqs").apply { writeBytes(File("src/main/assets/presets/FRLG Kaizo.rnqs").readBytes()) }
            assertFalse(RnqsInfo.of(zx).maxDex, "a standard file, which ZX reads first")
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `MaxDex is offered its own Kaizo only, and no other game is offered it`() {
        val maxDex = RulesetCatalog.forRom(RomKind.FIRERED_MAXDEX_10, presets)
        assertEquals(listOf("kaizo"), maxDex.map { it.key })
        assertEquals("FRLG MaxDex Kaizo.rnqs", maxDex.single().preset.name)
        assertTrue(maxDex.single().alternatives.isEmpty(), "Nat. Dex 1.2's Kaizo is no alternative: ${maxDex.single().alternatives}")
        for (k in listOf(RomKind.FIRERED_NATDEX_121, RomKind.FIRERED_U_V11, RomKind.FIRERED_U_V10, RomKind.LEAFGREEN_U)) {
            assertFalse(RulesetCatalog.isCompatible(k, maxDexPreset), k.id)
        }
        assertFalse(RulesetCatalog.isCompatible(RomKind.FIRERED_MAXDEX_10, File("src/main/assets/presets/FRLG NatDex v1.2 Kaizo.rnqs")))
        assertEquals("FRLG MaxDex Kaizo.rnqs", RulesetCatalog.openingFile(RomKind.FIRERED_MAXDEX_10, presets, null, null)?.name)
    }

    @Test
    fun `the guard refuses MaxDex with another file, and another game with MaxDex's file`() {
        val maxDex = RomKind.FIRERED_MAXDEX_10 to File("maxdex.gba")
        val natDex = RomKind.FIRERED_NATDEX_121 to File("natdex.gba")
        val fireRed = RomKind.FIRERED_U_V11 to File("firered.gba")
        assertNull(RunPairing.problem(maxDex, maxDexPreset))
        assertEquals(RunPairing.MAXDEX_GAME, RunPairing.problem(maxDex, File("FRLG NatDex v1.2 Kaizo.rnqs")))
        assertEquals(RunPairing.MAXDEX_GAME, RunPairing.problem(maxDex, File("FRLG Kaizo.rnqs")))
        assertEquals(RunPairing.NOT_MAXDEX_GAME, RunPairing.problem(natDex, maxDexPreset))
        assertEquals(RunPairing.NOT_MAXDEX_GAME, RunPairing.problem(fireRed, maxDexPreset))
        // What it was before MaxDex: Nat. Dex 1.2 and the standard game keep their own words.
        assertNull(RunPairing.problem(natDex, File("FRLG NatDex v1.2 Kaizo.rnqs")))
        assertEquals(RunPairing.STANDARD_GAME, RunPairing.problem(fireRed, File("FRLG NatDex v1.2 Kaizo.rnqs")))
        for (s in listOf(RunPairing.MAXDEX_GAME, RunPairing.NOT_MAXDEX_GAME)) assertPlain(s)
    }

    /** The Kaizo IronMON screen names MaxDex's own engine, calls the card a MaxDex build, and offers no builder for it. */
    @Test
    fun `the Kaizo IronMON screen names MaxDex's engine and offers no builder for it`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("selectedRom?.takeUnless { it.first.isMaxDex }?.let { rom -> BuildYourGameEntry" in run)
        assertTrue("Randomizers.engineName(it)" in run)
        assertTrue("kind.isMaxDex -> \"MaxDex build\"" in run)
    }
}
