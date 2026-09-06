package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every bundled preset must name a game the app knows and a mode the Mode
 * row can label. A file that parses to "? Kaizo" or "HGSS ?" is a preset the
 * player can only reach by reading file names, which is what the Mode row
 * exists to end.
 */
class PresetNamesTest {

    private val presets = File("../app/src/main/assets/presets")
    private val knownTags = setOf("RBY", "GSC", "FRLG", "RSE", "HGSS", "DPPt", "BW", "B2W2")

    @Test
    fun `every bundled preset parses to a known game and a labelled mode`() {
        assertTrue(presets.isDirectory, presets.absolutePath)
        val files = presets.listFiles { f: File -> f.name.endsWith(".rnqs") }!!.sortedBy { it.name }
        assertTrue(files.size >= 5, "expected the bundled presets, found ${files.size}")
        val bad = files.mapNotNull { f ->
            val i = RnqsInfo.parse(f.name)
            when {
                i.gameTag == null -> "${f.name}: no game tag"
                i.gameTag !in knownTags -> "${f.name}: unknown tag ${i.gameTag}"
                i.secondPass -> null                       // the Gen 1 PART 2 file: applied, never picked, so no ruleset by design
                i.ruleset == null -> "${f.name}: no ruleset"
                RnqsInfo.rulesetLabel(i.ruleset) == i.ruleset -> "${f.name}: ruleset ${i.ruleset} has no label"
                else -> null
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    @Test
    fun `the new Game Boy tags parse`() {
        assertTrue(RnqsInfo.parse("GSC Survival.rnqs").let { it.gameTag == "GSC" && it.ruleset == "survival" })
        assertTrue(RnqsInfo.parse("RBY Kaizo.rnqs").let { it.gameTag == "RBY" && it.ruleset == "kaizo" })
        assertTrue(RnqsInfo.parse("HGSS Kaizo Doubles.rnqs").let { it.gameTag == "HGSS" && it.ruleset == "kaizodoubles" })
    }
}
