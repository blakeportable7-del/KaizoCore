package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the Game Boy trackers report reaches the panel. The panel's file class
 * loads an Android Typeface, so, as in GbMoveTableWiringTest, the path is
 * checked in the source; the rules themselves are tested in the tracker module.
 */
class GbPanelWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    /** A Game Boy battle opens on your own card unless the player picked auto swap (TrackerOptions.autoSwapToEnemy). */
    @Test
    fun `the panel and the gear ask auto swap for the game being played`() {
        assertTrue("mutableStateOf(!(state.inBattle && TrackerOptions.autoSwapToEnemy(gameBoy = generation < 3)))" in src("TrackerPanel.kt"))
        // Two toggles of that name: the DS tracker's own setting, else the Gen 1 to 3 one.
        val gear = src("TrackerGearDialog.kt").lines().filter { "\"Auto swap to enemy\"" in it }
        assertEquals(2, gear.size, gear.joinToString("\n"))
        val gen3 = gear.single { "} else GearToggle(" in it }
        assertTrue("TrackerOptions.autoSwapToEnemy(gameBoy)" in gen3 && "TrackerOptions.chooseAutoSwapToEnemy(it)" in gen3, gen3)
        assertTrue("TrackerOptions.dsAutoSwapToEnemy" in gear.single { it != gen3 })
    }

    /** A Game Boy trainer battle's team row reads "Team:" before its balls, as the Game Boy references write it. */
    @Test
    fun `the Game Boy team row is labelled Team`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("teamLabel = \"Team:\".takeIf { generation < 3 }," in panel)
        assertTrue("else if (teamLabel != null) Row(verticalAlignment = Alignment.CenterVertically) {" in panel)
        assertTrue("PixText(teamLabel, PcRef.FONT, Pc.Text, Modifier.width(29.rp)); PcTrainerTeam(team)" in panel)
    }

    /** Gold, Silver and Crystal get the IV estimate in the gear; Red, Blue and Yellow do not (their reference's fails). */
    @Test
    fun `the gear offers the IV estimate on a Gen 2 game`() {
        assertTrue("ivPotential = (gbRef as? com.ironmonone.tracker.GbcTracker)?.let { g -> { g.ivPotential(trackerState?.party?.firstOrNull()) } }," in src("PlayScreen.kt"))
        val gear = src("TrackerGearDialog.kt")
        assertTrue("GearButton(\"ESTIMATE POK\\u00c9MON IV POTENTIAL\") { ivText = judge() }" in gear)
        // The result line is a dialog label now: in sp, 13, wrapping (2026-09-30, UX audit P0-15), not 7dp of pixel text.
        assertTrue("if (ivText.isNotEmpty()) DialogText(ivText, 13, Pc.Text)" in gear)
    }

    /** The carousel's line is built by LastAttack, so "Last move: X" reaches it when no damage was counted. */
    @Test
    fun `the carousel's last attack line and its colour come from LastAttack`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("com.ironmonone.tracker.LastAttack.text(mv, state.lastAttackDamage, state.lastAttackTeams)" in panel)
        assertTrue("lastAttackLethal = com.ironmonone.tracker.LastAttack.lethal(state.lastAttackDamage, state.party.firstOrNull()?.mon?.curHp)," in panel)
    }
}
