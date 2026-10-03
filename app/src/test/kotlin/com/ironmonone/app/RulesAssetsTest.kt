package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every bundled preset has rules text for its game and mode, and every rules
 * file names its sources. Reads the source tree, not the APK, so a missing
 * file fails here rather than as an empty RULES box on a phone. A Nat. Dex
 * preset's text is its own (rulesets/<family>-NatDex/): the Nat. Dex ruleset
 * changes on top of the mode.
 */
class RulesAssetsTest {
    private val assets = File("src/main/assets")

    @Test
    fun `every preset's game and mode has a rules file that cites its sources and carries no em dash`() {
        val presets = File(assets, "presets").listFiles { f -> f.extension == "rnqs" }!!
        assertTrue(presets.size > 30, "presets: ${presets.size}")
        val missing = mutableListOf<String>()
        for (p in presets) {
            val info = RnqsInfo.parse(p.name)
            val tag = info.gameTag ?: continue; val mode = info.ruleset ?: continue
            if (info.appliedByApp) continue
            // MaxDex's file reads MaxDex's own book (the Nat. Dex rules with MaxDex's section).
            val dir = if (info.maxDex) "$tag-MaxDex" else if (info.natDex) "$tag-NatDex" else tag
            val f = File(assets, "rulesets/$dir/$mode.md")
            if (!f.isFile) { missing += "$dir/$mode (for ${p.name})"; continue }
            val text = f.readText()
            assertTrue("## Sources" in text && "gist.github.com" in text, "${f.name}: sources missing")
            assertTrue("\u2014" !in text, "${f.name}: em dash")
            assertTrue(text.lineSequence().any { it.startsWith("## ${expectedHeading(mode)}") }, "$dir/${f.name}: no section for its own mode")
            if (info.natDex) assertTrue("## Nat. Dex ruleset changes" in text, "$dir/${f.name}: no Nat. Dex changes")
            if (info.maxDex) assertTrue("## MaxDex" in text && "Tripc423/Maxdex.wiki.git" in text, "$dir/${f.name}: no MaxDex section")
        }
        assertTrue(missing.isEmpty(), "no rules for: $missing")
    }

    /**
     * What build_rules.py drew wrong until 2026-09-29, checked in every file: HTML
     * the box shows as text ("</br>", "<li>"), markdown links left raw, list items
     * run into the next sentence ("Any Building with TrainersForests"), and the
     * rules gist's activity stamp given as the rules' date.
     */
    @Test
    fun `no rules file shows markup or the rules gist's activity stamp as its date`() {
        val files = File(assets, "rulesets").walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
        assertTrue(files.size >= 71, "${files.size} rules files")
        for (f in files) {
            val text = f.readText()
            for (bad in listOf("</br>", "<br", "<li>", "</ul>", "<b>", "](http", "TrainersForests", "gist updated"))
                assertTrue(bad !in text, "${f.parentFile.name}/${f.name}: \"$bad\"")
            assertTrue("rules last changed 2025-02-23" in text, "${f.parentFile.name}/${f.name}: the rules' own date")
        }
    }

    /**
     * What RULES opens: each game's own folder (a Nat. Dex build its Nat. Dex
     * text) holds the text of every mode the Mode row offers that game, and a
     * run whose mode has no text there is told so instead of shown Standard.
     */
    @Test
    fun `RULES reads each game's own folder, which has every mode it offers, and says so when the run's mode has none`() {
        val presets = File(assets, "presets").listFiles { f -> f.extension == "rnqs" }!!.toList()
        for (k in com.ironmonone.core.RomKind.all) {
            val dir = File(assets, "rulesets/" + Rules.dirFor(k.family, k.isNatDex, k))
            val have = dir.list()?.map { it.removeSuffix(".md") }?.toSet() ?: emptySet()
            for (m in RulesetCatalog.forRom(k, presets)) assertTrue(m.key in have, "${k.id}: ${m.key} is offered but ${dir.name} has no text for it")
        }
        assertTrue(File(assets, "rulesets/RSE-NatDex/kaizo.md").readText().contains("## Nat. Dex ruleset changes"))
        val modes = Rules.order(listOf("kaizo", "evokaizo", "standard", "survivalrevival"))
        assertEquals(listOf("standard", "kaizo", "survivalrevival", "evokaizo"), modes)
        assertEquals("kaizo" to null, Rules.opening(modes, "kaizo"))
        assertEquals("standard" to null, Rules.opening(modes, null), "no run mode: the first tab, as before")
        val (tab, said) = Rules.opening(listOf("standard", "kaizo"), "chaoskaizo")
        assertEquals(null, tab, "never Standard's text for a Chaos Kaizo run")
        assertTrue(said!!.startsWith("There is no rules text for Chaos Kaizo on this game") && '\u2014' !in said)
    }

    private fun expectedHeading(mode: String) = when (mode) {
        "standard" -> "Standard IronMON"; "ultimate" -> "Ultimate IronMON"; "kaizo" -> "Kaizo IronMON"
        "superkaizo" -> "Super Kaizo IronMON"; "survival" -> "Survival IronMON"; "kaizodoubles" -> "Kaizo Doubles"
        "survivalrevival" -> "Survival Revival IronMON"; "ironmonjourney" -> "IronMON Journey"
        "chaoskaizo" -> "Chaos Kaizo IronMON"; "evokaizo" -> "Evo Kaizo IronMON"
        else -> mode
    }
}
