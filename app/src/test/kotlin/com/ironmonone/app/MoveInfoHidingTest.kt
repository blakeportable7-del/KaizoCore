package com.ironmonone.app

import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.MoveRules
import com.ironmonone.tracker.RandomizedFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The move info card hides what the move's row hides (2026-09-30, IronMON rules check). With "Reveal info if
 * randomized" off, an opponent's randomized PP, power, accuracy and type read "?" on its row, and the card opened from
 * that row printed the ROM's numbers. DataHelper.buildMoveInfoDisplay hides them on the card as well.
 */
class MoveInfoHidingTest {
    private val flamethrower = MoveRow(53, "FLAMETHROWER", 15, null, 95, 100, 10, "SPE")

    private fun opponent(hide: RandomizedFlags?) = MoveContext(
        inBattle = true, viewingOwn = false, attackerTypes = listOf(10, 10), targetTypes = listOf(0, 0),
        source = MoveRules.Side(30, 50, 90), target = null, weather = null, hide = hide,
    )

    private val everything = RandomizedFlags(true, true, true, true, true, true, true, true, true, true)

    @Test
    fun `hidden on the row, hidden on the card`() {
        val d = detailOf(flamethrower.toPcMove(opponent(everything)), "A powerful fire attack.")
        assertEquals("?", MoveInfoText.pp(d))
        assertEquals("?", MoveInfoText.power(d))
        assertEquals("?", MoveInfoText.accuracy(d))
        assertNull(d.typeName, "the type too")
        assertNull(d.typeChart, "and so no chart for it")
    }

    @Test
    fun `only what was randomized is hidden`() {
        val powerOnly = RandomizedFlags(false, false, false, false, false, false, false, true, false, false)
        val d = detailOf(flamethrower.toPcMove(opponent(powerOnly)), null)
        assertEquals("15", MoveInfoText.pp(d))
        assertEquals("?", MoveInfoText.power(d))
        assertEquals("100%", MoveInfoText.accuracy(d))
        assertEquals("Fire", d.typeName)
    }

    /** Blake, 2026-09-30: the type matchups are a switch, off by default, since the PC tracker's move screen has none. */
    @Test
    fun `the type matchups show only with their switch on`() {
        val saved = TrackerOptions.showTypeMatchups
        try {
            TrackerOptions.showTypeMatchups = false
            assertNull(detailOf(flamethrower.toPcMove(opponent(null)), null).typeChart, "off by default")
            TrackerOptions.showTypeMatchups = true
            val chart = detailOf(flamethrower.toPcMove(opponent(null)), null).typeChart
            assertEquals(setOf("Grass", "Ice", "Bug", "Steel"), chart?.strongAgainst?.toSet(), "Fire's chart, the opponent's nowhere in it")
            assertNull(detailOf(flamethrower.toPcMove(opponent(everything)), null).typeChart, "never for a hidden type")
        } finally { TrackerOptions.showTypeMatchups = saved }
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/TrackerOptions.kt").readText()
        assertTrue("var showTypeMatchups by mutableStateOf(false)" in src)
        val gear = java.io.File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue("GearToggle(\"Type matchups in move info\", TrackerOptions.showTypeMatchups)" in gear, "the player turns it on in Tracker Setup")
    }

    @Test
    fun `nothing hidden shows the numbers as before`() {
        val d = detailOf(flamethrower.toPcMove(opponent(null)), null)
        assertEquals("15", MoveInfoText.pp(d))
        assertEquals("95", MoveInfoText.power(d))
        assertEquals("100%", MoveInfoText.accuracy(d))
        val own = detailOf(MoveRow(53, "FLAMETHROWER", 12, 15, 95, 100, 10, "SPE").toPcMove(null), null)
        assertEquals("12/15", MoveInfoText.pp(own), "your own move keeps its PP out of its max")
        val swift = detailOf(MoveRow(129, "SWIFT", 20, 20, 60, 0, 0, "PHY").toPcMove(null), null)
        assertEquals("-", MoveInfoText.accuracy(swift), "a move that never misses reads a dash, not 0%")
    }
}
