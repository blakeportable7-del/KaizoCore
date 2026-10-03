package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RC35-NOTICED N #34: with an Egg in slot 1, three of the panel's readers still took slot 1. The PC tracker's are
 * Battle.getViewedPokemon(true) for the info screen's types (DataHelper.lua:423) and Tracker.getPokemon(1, true) for the
 * carousel's early route encounters and Trainer Info's level colour (TrackerScreen.lua:664, TrainerInfoScreen.lua:251):
 * never an Egg. TrackerState.onField and TrackerState.lead are those two (Gen3BattleReadsTest drives them); this pins the
 * panel to them, as the readers are composable arguments.
 */
class TrackerLeadTest {
    private fun read(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()

    @Test
    fun `the info screen's own lead, the carousel and Trainer Info read past an Egg in slot 1`() {
        val panel = read("TrackerPanel.kt")
        val side = read("SideScreens.kt")
        // GbaViewState.own: TrackerState.onField, or in a double battle the right-hand one while it is viewed (rc34).
        assertTrue("ownLead = species == gbaView.own(state)?.mon?.species" in panel, "the info screen's types")
        assertTrue("if (ownBattler(it) == 2) it.ownRight else it.onField" in read("DoublesView.kt"))
        assertTrue("leadLevel = state.lead?.mon?.level ?: 0" in panel, "the carousel")
        assertTrue("leadLevel = st?.lead?.mon?.level" in side, "Trainer Info")
        assertFalse("ownLead = species == state?.party?.firstOrNull()" in panel)
        assertFalse("leadLevel = state.party.firstOrNull()" in panel)
        assertFalse("leadLevel = st?.party?.firstOrNull()" in side)
    }
}
