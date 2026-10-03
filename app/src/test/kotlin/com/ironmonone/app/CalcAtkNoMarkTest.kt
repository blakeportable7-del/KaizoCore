package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Calc Atk shows no low-confidence mark (Blake, 2026-10-02, rc32 audit P3 #22). The extension's (LabelConfidence)
 * compares the estimate with the opponent's real ATK and SPA, which the tracker otherwise keeps hidden. The dialog took
 * those stats as a parameter that was never filled, so the mark could not draw; the parameter and the check are gone,
 * and the dialog is given no opponent stats to compare.
 */
class CalcAtkNoMarkTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()

    @Test
    fun `the dialog takes no opponent stats and draws no mark`() {
        val calc = src("CalcAtkScreen.kt")
        assertTrue("fun CalcAtkDialog(fill: CalcAtk.Fill?, onClose: () -> Unit)" in calc)
        assertFalse("enemyStats" in calc)
        assertFalse("Something in the formula is missing" in calc)
        assertFalse(Regex("\" \\*\"").containsMatchIn(calc), "no star beside the estimate")
        assertTrue("CalcAtkDialog(s.calcAtkFill) { s.calcAtkOpen = false }" in src("SideScreens.kt"))
    }
}
