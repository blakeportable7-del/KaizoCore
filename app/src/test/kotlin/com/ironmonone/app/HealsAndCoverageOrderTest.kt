package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heals in Bag opens on All, the reference's first tab, where nothing is coloured for being
 * helpful (HealsInBagScreen.lua:8-34, :105, :266-279, :303); Coverage Calc lists each bucket by BST,
 * highest first, ties by id (CoverageCalcScreen.lua:132). Until 2026-09-29 the one opened on HP
 * and the other listed the Gen 3 buckets in dex order.
 */
class HealsAndCoverageOrderTest {
    private val potion = GbaTracker.BagRow(13, "POTION", 3, "HP", helpful = true, sortValue = 50020)
    private val ball = GbaTracker.BagRow(4, "POKE BALL", 69, "Balls", helpful = false, sortValue = 0)

    @Test
    fun `Heals in Bag opens on All, with no helpful colouring there`() {
        assertEquals(listOf("All", "HP", "PP", "Status", "Battle"), HealsTabs.ORDER)
        assertEquals(listOf(potion, ball), HealsTabs.rows(listOf(potion, ball), "All"))
        assertEquals(listOf(potion), HealsTabs.rows(listOf(potion, ball), "HP"))
        assertFalse(HealsTabs.green(potion, "All"))
        assertTrue(HealsTabs.green(potion, "HP"))
        assertTrue(HealsTabs.green(ball, "All"), "69 of anything")
        val src = File("src/main/kotlin/com/ironmonone/app/HealsInBag.kt").readText().replace("\r\n", "\n")
        assertTrue("var tab by remember { mutableStateOf(HealsTabs.ORDER.first()) }" in src)
        assertTrue("val c = if (HealsTabs.green(r, tab)) Pc.Positive else Pc.Text" in src)
    }

    @Test
    fun `Coverage Calc lists by BST, highest first, then id, on Gen 3 as on DS`() {
        val bst = mapOf(1 to 318, 4 to 309, 7 to 314, 152 to 318, 150 to 680)
        assertEquals(listOf(150, 1, 152, 7, 4), CoverageCalc.byBst(listOf(1, 4, 7, 150, 152)) { bst.getValue(it) })
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val calls = Regex("CoverageCalcDialog\\(").findAll(play).map { m -> play.substring(m.range.first, (m.range.first + 1500).coerceAtMost(play.length)) }.toList()
        assertEquals(2, calls.size, "the DS and the Gen 3 calculators")
        assertTrue(calls.all { "sortByBst = true" in it })
    }
}
