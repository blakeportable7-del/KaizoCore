package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game's generation has to reach the move rows for the Game Boy
 * references' rules to apply there: Low Kick's ROM power in place of Gen 3's
 * "WT" (MoveRules.variablePower), and the Gen 1 tracker's chart on Red,
 * Blue and Yellow's effectiveness marks and move info. The rules themselves
 * are tested in MoveRulesTest and MoveMatchupTest; the rows are drawn
 * through PcTracker.kt, whose file class
 * loads an Android Typeface, so the path from PlayScreen to MoveRules is
 * checked here in the source, the way BackupCoverageTest reads it.
 */
class GbMoveTableWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    /** The argument list of every call to [fn] in [text], by balanced parentheses. */
    private fun calls(text: String, fn: String): List<String> =
        Regex("\\b$fn\\(").findAll(text).map { m ->
            var depth = 0; var i = m.range.last
            do { when (text[i]) { '(' -> depth++; ')' -> depth-- }; i++ } while (depth > 0 && i < text.length)
            text.substring(m.range.last, i)
        }.toList()

    @Test
    fun `PlayScreen tells both panels the game's generation, and the panel hands it to both cards' moves`() {
        val panels = calls(src("PlayScreen.kt"), "TrackerPanel")
        assertEquals(2, panels.size, "the portrait and the landscape panel")
        panels.forEach { assertTrue("generation = session.kind?.generation?.number ?: 3" in it, "a TrackerPanel call without the generation") }
        val panel = src("TrackerPanel.kt")
        for (ctx in listOf("ownMoveContext", "enemyMoveContext")) {
            val use = Regex("$ctx\\([^\\n]*\\)\\n\\s*\\.copy\\(([^\\n]*)\\)").find(panel)?.groupValues?.get(1)
            assertTrue(use != null && "generation = generation" in use, "$ctx is copied without the generation: $use")
        }
        assertTrue("MoveRules.basePower(id, power, ctx?.generation ?: 3)" in src("MoveDecor.kt"))
    }

    @Test
    fun `Red, Blue and Yellow's effectiveness marks and move info use the Gen 1 chart`() {
        val decor = src("MoveDecor.kt")
        val marks = calls(decor, "MoveRules.effectiveness")
        assertEquals(2, marks.size, "the hidden-info row and the ordinary row")
        marks.forEach { assertTrue("gen1 = ctx.generation == 1" in it, "an effectiveness mark on Gen 3's chart: $it") }
        val panel = src("TrackerPanel.kt")
        val infos = calls(panel, "detailOf").filter { "mv," in it }
        assertEquals(2, infos.size, "your moves' info and the opponent's")
        infos.forEach { assertTrue("gen1 = generation == 1" in it, "a move info on Gen 3's chart: $it") }
        assertTrue("MoveMatchup.general(mv.type, gen1)" in panel)
    }
}
