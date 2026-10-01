package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The DS panel shows what the DS tracker's main screen shows (IronMON rules check, 2026-09-30): no coverage counts
 * there. The DS tracker has them on its Coverage Calc screen only, which is COVERAGE CALC in Tracker Setup here.
 */
class NdsCoverageParityTest {
    @Test
    fun `coverage lives on the Coverage Calc screen, not the DS main panel`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertFalse("PcCoverage(" in panel, "the DS main panel draws no coverage")
        assertTrue("GearButton(\"COVERAGE CALC\") { onCoverage() }" in File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText())
    }
}
