package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Pokemon info screen says "Does not learn any moves" and "Has no weaknesses" where the
 * reference does (InfoScreen.lua:772-774, 818-820), and Team View draws an egg as "EGG" with
 * nothing of the Pokemon inside it (TeamViewArea.lua:62-229). Before 2026-09-29 the one said
 * nothing and the other showed the egg's species and stats.
 */
class InfoAndTeamViewTest {
    private fun mon(egg: Boolean) = TrackedMon(
        mon = PokemonDecoder.Mon(1, 5, "TOGEPI", 175, 0, 120, listOf(33, 0, 0, 0), listOf(35, 0, 0, 0), List(6) { 0 }, List(6) { 0 }, List(4) { 0 },
            0, 0, false, 0, 20, 20, 5, 5, 5, 5, 5, isEgg = egg),
        speciesName = "TOGEPI", moveNames = emptyList(), base = BaseStats(35, 20, 65, 20, 40, 65, 0, 0, 55, 0),
        abilityName = "HUSTLE", itemName = "-", expNow = 10, expTotal = 100,
    )

    @Test
    fun `the info screen names the two empty states`() {
        assertEquals("Does not learn any moves", InfoScreenLines.learnLevels(emptyList(), 10))
        assertEquals("1, 7, [13]", InfoScreenLines.learnLevels(listOf(1, 7, 13), 10))
        assertTrue(InfoScreenLines.hasNoWeaknesses(mapOf(0.5 to listOf("Normal"), 0.0 to listOf("Fighting"))), "Sableye")
        assertFalse(InfoScreenLines.hasNoWeaknesses(mapOf(2.0 to listOf("Fire"))))
        assertFalse(InfoScreenLines.hasNoWeaknesses(mapOf(4.0 to listOf("Ice"))))
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText().replace("\r\n", "\n")
        assertTrue("if (InfoScreenLines.hasNoWeaknesses(effectiveness)) PcInfoRow(\"Weak to\", InfoScreenLines.NO_WEAKNESSES)" in pc)
        // In sp since rc34 (rc32 audit P2 #19, TrackerWindowsRestTest).
        assertTrue("DialogText(InfoScreenLines.learnLevels(moveLevels, level), 13, Pc.Dim)" in pc)
        assertFalse("if (moveLevels.isNotEmpty()) {" in pc, "the learn levels always have their place")
    }

    @Test
    fun `Team View draws an egg as EGG and nothing else of it`() {
        val egg = TeamBox.of(mon(egg = true), eggSpecies = 412)
        assertEquals(TeamBox("EGG", 412, "", listOf("Unknown" to 9), "Lv.?", null, null, "---", "---", tappable = false), egg)
        val togepi = TeamBox.of(mon(egg = false), eggSpecies = 412)
        assertEquals("TOGEPI", togepi.name); assertEquals(175, togepi.iconSpecies); assertEquals("Lv.5", togepi.level)
        assertEquals(listOf("Normal" to 0), togepi.types); assertEquals("HUSTLE", togepi.ability); assertTrue(togepi.tappable)
        val src = File("src/main/kotlin/com/ironmonone/app/TeamView.kt").readText().replace("\r\n", "\n")
        assertTrue("val box = TeamBox.of(p, eggSpecies)" in src)
    }
}
