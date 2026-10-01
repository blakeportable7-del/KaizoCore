package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Accuracy and evasion stages are drawn as the reference draws them (TrackerScreen.lua:1435-1448):
 * in battle, once either moves, "Acc" and "Eva" with a column of chevrons each take the BST row,
 * the chevrons as Drawing.drawChevronsVerticalIntensity makes them. The stages were read and
 * never drawn until 2026-09-29.
 */
class StageChevronsTest {
    @Test
    fun `a chevron a stage up to three, coloured past three`() {
        assertEquals(emptyList(), StageChevrons.of(0))
        assertEquals(listOf(false), StageChevrons.of(-1))
        assertEquals(listOf(false, false, false), StageChevrons.of(3))
        assertEquals(listOf(true, true, false), StageChevrons.of(5))
        assertEquals(listOf(true, true, true), StageChevrons.of(-6))
    }

    @Test
    fun `in battle, a moved accuracy or evasion takes the BST row`() {
        assertTrue(StageChevrons.accEvaReplacesBst(inBattle = true, acc = 5, eva = 6))
        assertTrue(StageChevrons.accEvaReplacesBst(inBattle = true, acc = 6, eva = 8))
        assertFalse(StageChevrons.accEvaReplacesBst(inBattle = true, acc = 6, eva = 6))
        assertFalse(StageChevrons.accEvaReplacesBst(inBattle = false, acc = 4, eva = 6))
    }

    @Test
    fun `both cards draw the row, and a hidden card's stages are the stand-in's`() {
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertEquals(2, Regex("if \\(StageChevrons\\.accEvaReplacesBst\\((inBattle|true), acc, eva\\)\\) PcAccEvaRow\\(acc, eva\\)").findAll(src).count())
        assertTrue("val stages = if (hidden) emptyMap<String, Int>() else p.statStages" in src)
        assertFalse("p.statStages[" in src, "your card's rows read the stages through the hidden rule")
        assertTrue("inBattle = state.inBattle)" in src)
    }
}
