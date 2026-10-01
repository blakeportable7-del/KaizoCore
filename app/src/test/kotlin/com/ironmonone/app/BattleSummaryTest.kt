package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The carousel's battle line is the viewed battler's BattleDetailsScreen summary, shown only
 * while there is one (TrackerScreen.lua:769-787). It read the weather and a seen count until
 * 2026-09-29.
 */
class BattleSummaryTest {
    @Test
    fun `the viewed side's first detail, and nothing when it has none`() {
        val s = listOf("Confused (1- 4 Turns)", "Encore (TACKLE)", "", "")
        assertEquals("Confused (1- 4 Turns)", BattleSummary.line(s, viewingOwn = true))
        assertEquals("Encore (TACKLE)", BattleSummary.line(s, viewingOwn = false))
        assertNull(BattleSummary.line(listOf("", ""), viewingOwn = false))
        assertNull(BattleSummary.line(emptyList(), viewingOwn = true), "outside a battle, or no battle addresses")
    }

    @Test
    fun `the carousel shows the summary, and the panel hands it the viewed battler's`() {
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText().replace("\r\n", "\n")
        assertTrue("TrackerOptions.carouselShows(\"BattleDetails\") && inBattle && battleDetailsSummary != null" in pc)
        assertTrue("\"battleDetails\" -> PcBattleSummaryLine(battleDetailsSummary ?: \"\", onBattleDetailsTap)" in pc)
        assertFalse("encounters > 1" in pc, "the seen count is not a battle detail")
        val panel = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertTrue("battleDetailsSummary = BattleSummary.line(state.battleSummaries, viewingOwn = !state.inBattle || viewingOwn)" in panel)
    }
}
