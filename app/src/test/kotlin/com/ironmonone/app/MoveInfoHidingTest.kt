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

    /**
     * rc32 audit P3 #32: the reference marks a same-type move before it hides anything (DataHelper.lua:306-309) and clears
     * the green only where the move types are hidden (TrackerScreen.lua:1469, :1535-1541). With "Reveal info if
     * randomized" off and the types not randomized, the opponent's Fire move from a Fire attacker stays green.
     */
    @Test
    fun `an opponent's same-type move keeps its green while its type is shown`() {
        val powerOnly = RandomizedFlags(false, false, false, false, false, false, false, true, false, false)
        assertTrue(flamethrower.toPcMove(opponent(powerOnly)).stab, "move types shown: green, on a power that reads ?")
        assertTrue(!flamethrower.toPcMove(opponent(everything)).stab, "move types hidden: no green")
        assertTrue(flamethrower.toPcMove(opponent(null)).stab, "nothing hidden, as before")
    }

    /**
     * rc32 audit P3 #73: your own Hidden Power's chart is its set type's, read when it is drawn, as the tag and the
     * category beside the arrows are. It was worked out once, from the type the card opened with.
     */
    @Test
    fun `Hidden Power's matchups follow its type arrows`() {
        val saved = TrackerOptions.showTypeMatchups
        val dir = java.nio.file.Files.createTempDirectory("hp").toFile()
        try {
            TrackerOptions.showTypeMatchups = true
            HiddenPowerTypes.load(java.io.File(dir, "hp.txt"))
            val pid = 0x1234L
            val hp = MoveRow(MoveRules.HIDDEN_POWER, "HIDDEN POWER", 15, 15, 1, 100, 0, "PHY")
            val d = detailOf(hp.toPcMove(null), null).copy(hiddenPowerPid = pid)
            assertNull(MoveInfoText.chart(d), "no type set: no chart")
            repeat(9) { HiddenPowerTypes.next(pid) }                      // Fighting ... Fire
            assertEquals(10, HiddenPowerTypes.of(pid))
            assertEquals(setOf("Grass", "Ice", "Bug", "Steel"), MoveInfoText.chart(d)?.strongAgainst?.toSet(), "Fire's")
            HiddenPowerTypes.next(pid)                                     // Water
            assertEquals(setOf("Fire", "Ground", "Rock"), MoveInfoText.chart(d)?.strongAgainst?.toSet(), "Water's, on the same card")
            TrackerOptions.showTypeMatchups = false
            assertNull(MoveInfoText.chart(detailOf(hp.toPcMove(null), null).copy(hiddenPowerPid = pid)), "the switch still rules")
            // Any other move keeps the chart it opened with.
            TrackerOptions.showTypeMatchups = true
            val f = detailOf(flamethrower.toPcMove(opponent(null)), null)
            assertEquals(f.typeChart, MoveInfoText.chart(f))
        } finally {
            TrackerOptions.showTypeMatchups = saved
            dir.deleteRecursively()
        }
        val card = java.io.File("src/main/kotlin/com/ironmonone/app/PcMoveInfo.kt").readText()
        assertTrue("MoveInfoText.chart(d)?.let { g ->" in card, "the card draws the live chart")
        assertTrue("d.typeChart?.let" !in card)
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
