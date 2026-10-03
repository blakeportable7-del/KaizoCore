package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings picker must never show two rows with the same title.
 *
 * It did: "FRLG Kaizo.rnqs" and "FRLG Kaizo (edited).rnqs" both parse to the
 * label "FRLG Kaizo", so the picker listed the same words twice and the only
 * distinguishing text was the filename in the subtitle.
 */
class RnqsLabelTest {

    @Test
    fun `colliding labels fall back to the file stem`() {
        val out = RnqsInfo.displayLabels(
            listOf("FRLG Kaizo.rnqs", "FRLG Kaizo (edited).rnqs"),
        )
        assertEquals("FRLG Kaizo" to "FRLG Kaizo", out[0])
        assertEquals("FRLG Kaizo (edited)" to "FRLG Kaizo", out[1])
        // The point of the exercise: the two TITLES differ.
        assertTrue(out[0].first != out[1].first)
    }

    @Test
    fun `a unique label is left alone`() {
        val out = RnqsInfo.displayLabels(
            listOf("FRLG Kaizo.rnqs", "RSE NatDex v1.2 Kaizo.rnqs"),
        )
        assertEquals("FRLG Kaizo", out[0].first)
        assertEquals("RSE Nat. Dex Kaizo", out[1].first)
    }

    @Test
    fun `every title in a real picker list is unique`() {
        val files = listOf(
            "DPPt Kaizo.rnqs", "FRLG Kaizo.rnqs", "FRLG Kaizo (edited).rnqs",
            "FRLG NatDex v1.2 Kaizo.rnqs", "RSE Kaizo.rnqs",
            "RSE NatDex v1.2 Kaizo.rnqs",
        )
        val titles = RnqsInfo.displayLabels(files).map { it.first }
        assertEquals(titles.size, titles.toSet().size, "duplicate titles: $titles")
    }

    @Test
    fun `words typed in a build's name never decide its game or mode`() {
        // rc33 audit P1 #48: the whole name was searched, so a bracket of the player's words picked the mode and even the game.
        val cases = mapOf(
            "FRLG Kaizo (standard starters).rnqs" to Triple("FRLG", "kaizo", false),
            "FRLG Kaizo (doubles practice).rnqs" to Triple("FRLG", "kaizo", false),
            "FRLG Kaizo (horse).rnqs" to Triple("FRLG", "kaizo", false),
            "FRLG Kaizo (nurse joy).rnqs" to Triple("FRLG", "kaizo", false),
            "FRLG Kaizo (natdex test).rnqs" to Triple("FRLG", "kaizo", false),
            "RSE NatDex Survival (my run).rnqs" to Triple("RSE", "survival", true),
        )
        for ((name, want) in cases) {
            val i = RnqsInfo.parse(name)
            assertEquals(want, Triple(i.gameTag, i.ruleset, i.natDex), name)
        }
        kotlin.test.assertFalse(RnqsInfo.parse("FRLG Kaizo (part 2).rnqs").secondPass, "the build is not the Gen 1 second pass")
        kotlin.test.assertFalse(RnqsInfo.parse("RSE Kaizo (prepass).rnqs").prePass, "nor the pre-pass")
        assertTrue(RnqsInfo.parse("RBY PART 2.rnqs").secondPass && RnqsInfo.parse("RSE PRE-PASS.rnqs").prePass, "the real ones still are")
        // No bundled preset name has a bracket, so every one reads as before.
        val presets = java.io.File("src/main/assets/presets").list { _, n -> n.endsWith(".rnqs") }!!
        assertTrue(presets.isNotEmpty() && presets.none { '(' in it })
        // A file from elsewhere with no sidecar and its game only in brackets is still read, from the whole name.
        val dir = java.nio.file.Files.createTempDirectory("rnqs").toFile()
        val imported = java.io.File(dir, "My run (FRLG Kaizo).rnqs").apply { writeBytes(ByteArray(8)) }
        val info = RnqsInfo.of(imported)
        assertEquals("FRLG" to "kaizo", info.gameTag to info.ruleset)
    }
}
