package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trainer Info shows what TrainerInfoScreen.lua shows: Poke Balls with levels until the trainer
 * is beaten (species only after, where Open Book or unrandomized teams allow, or for a Pokemon
 * that fainted in the battle against it), the held-item mark and never the item, no moves, and
 * the trainer's usable items. Until 2026-09-29 it listed the whole party with items and moves
 * before the fight.
 */
class TrainerInfoViewTest {
    @AfterTest fun defaults() {
        TrackerOptions.showDataForVanillaGame = true
        TrackerOptions.openBookPlayMode = false
    }

    private fun trainer(defeated: Boolean) = GbaTracker.TrainerInfo(
        id = 414, className = "LEADER", name = "BROCK",
        party = listOf(GbaTracker.TrainerMon(74, 12, 15, 0, listOf(33, 111, 0, 0)), GbaTracker.TrainerMon(95, 14, 15, 139, listOf(33, 103, 0, 0))),
        aiFlags = 7, doubleBattle = false, defeated = defeated, items = listOf(13, 0, 13, 19),
    )

    @Test
    fun `before the fight only balls, levels and the held-item mark, after it the species`() {
        val before = TrainerInfoView.party(trainer(false), canShowTeams = false, faintedSlots = emptySet())
        assertEquals(listOf(TrainerInfoView.Slot(null, 12, false), TrainerInfoView.Slot(null, 14, true)), before)
        val after = TrainerInfoView.party(trainer(true), canShowTeams = false, faintedSlots = emptySet())
        assertEquals(listOf(74, 95), after.map { it.species })
        // In the battle against it, a fainted Pokemon shows and the rest stay balls.
        assertEquals(listOf(74, null), TrainerInfoView.party(trainer(false), false, setOf(0)).map { it.species })
    }

    @Test
    fun `Open Book, or unrandomized teams with the vanilla option, show the team before the fight`() {
        assertFalse(InfoRules.canShowTrainerTeams(true))
        assertFalse(InfoRules.canShowTrainerTeams(null), "unknown counts as randomized")
        assertTrue(InfoRules.canShowTrainerTeams(false))
        assertEquals(listOf(74, 95), TrainerInfoView.party(trainer(false), InfoRules.canShowTrainerTeams(false), emptySet()).map { it.species })
        TrackerOptions.showDataForVanillaGame = false
        assertFalse(InfoRules.canShowTrainerTeams(false))
        TrackerOptions.openBookPlayMode = true
        assertTrue(InfoRules.canShowTrainerTeams(true))
    }

    @Test
    fun `usable items are counted, named, sorted and joined, and none is null`() {
        val names = mapOf(13 to "POTION", 19 to "FULL RESTORE")
        assertEquals("2 POTION, FULL RESTORE", TrainerInfoView.usableItems(listOf(13, 0, 13, 19)) { names[it] })
        assertNull(TrainerInfoView.usableItems(listOf(0, 0, 0, 0)) { names[it] })
        assertTrue(TrainerInfoView.isGiovanni(true, 349)); assertFalse(TrainerInfoView.isGiovanni(false, 349))
    }

    @Test
    fun `the screen draws the party and items through those rules and never a move`() {
        val src = File("src/main/kotlin/com/ironmonone/app/TrainerScreens.kt").readText().replace("\r\n", "\n")
        val dialog = src.substring(src.indexOf("fun TrainerInfoDialog("))
        assertTrue("TrainerInfoView.party(t, canShowTeams, faintedSlots)" in dialog)
        assertTrue("TrainerInfoView.usableItems(t.items, itemName)" in dialog)
        assertFalse(".moves" in dialog, "Trainer Info never lists moves")
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText().replace("\r\n", "\n")
        assertTrue("canShowTeams = InfoRules.canShowTrainerTeams(gba?.trainerTeamsRandomized())" in side)
    }
}
