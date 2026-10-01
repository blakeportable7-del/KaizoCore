package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsSpeciesInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Effectiveness on the DS move rows (MainScreen.setUpMoveEffectiveness): only in
 * battle, only against the opposing Pokemon, with the setting on and the pause
 * after a new opponent over.
 */
class NdsMoveEffectTest {
    private fun tm(pid: Long, species: Int, t1: String, t2: String, item: Int = 0, moves: List<NdsMoveInfo> = emptyList()) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 20, 40, 40, listOf(33, 0, 0, 0), heldItem = item))!!,
        speciesName = "#$species", info = NdsSpeciesInfo("#$species", t1, t2, 300, "", ""),
        abilityName = "-", itemName = "-", moves = moves,
    )

    private val water = NdsMoveInfo("Water Gun", 40, 100, "WATER", 25, "SPECIAL", 55)
    private val lead = tm(0x11, 7, "WATER", "WATER", moves = listOf(water))
    private val second = tm(0x22, 25, "ELECTRIC", "ELECTRIC")
    private val foe = tm(0x99, 74, "ROCK", "GROUND", moves = listOf(water))

    private fun battle(active: NdsTrackedMon?) = NdsTrackerState(
        2, listOf(lead, second), located = true, inBattle = true, enemy = foe, playerActive = active,
    )

    @Test
    fun `the Pokemon on the field is aimed at the opponent, and the opponent at it`() {
        val s = battle(active = lead)
        val mine = assertNotNullCtx(ndsMoveContext(s, lead, enemyCard = false, "BUG", show = true))
        assertEquals(listOf("ROCK", "GROUND"), mine.targetTypes)
        assertEquals(4.0, ndsMoveEffect(water, mine))
        assertNull(ndsMoveContext(s, second, enemyCard = false, "BUG", show = true), "a Pokemon not in battle has no target")
        val theirs = assertNotNullCtx(ndsMoveContext(s, foe, enemyCard = true, "BUG", show = true))
        assertEquals(listOf("WATER", "WATER"), theirs.targetTypes)
        assertEquals(0.5, ndsMoveEffect(water, theirs))
        // After a switch the new Pokemon on the field is the one aimed.
        val switched = battle(active = second)
        assertNull(ndsMoveContext(switched, lead, enemyCard = false, "BUG", show = true))
        assertEquals(listOf("ELECTRIC", "ELECTRIC"), ndsMoveContext(switched, foe, enemyCard = true, "BUG", show = true)?.targetTypes)
    }

    @Test
    fun `nothing outside battle, with the setting off or during the pause, and nothing for neutral`() {
        val out = NdsTrackerState(2, listOf(lead, second), located = true)
        assertNull(ndsMoveContext(out, lead, enemyCard = false, "BUG", show = true))
        assertNull(ndsMoveEffect(water, ndsMoveContext(battle(lead), lead, enemyCard = false, "BUG", show = false)), "the setting off, or the pause")
        val neutral = NdsMoveContext(listOf("NORMAL"), 0, "BUG")
        assertNull(ndsMoveEffect(water, neutral), "1x draws nothing")
    }

    @Test
    fun `the pause is the reference's frames at 60 a second`() {
        assertEquals(2500L, ndsEffectivenessDelayMs("DPPT", firstOfBattle = true))
        assertEquals(4000L, ndsEffectivenessDelayMs("BW2", firstOfBattle = true))
        assertEquals(1500L, ndsEffectivenessDelayMs("BW", firstOfBattle = false))
    }

    @Test
    fun `both cards and both panels are wired`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertTrue("moveCtx = ndsMoveContext(state, it, enemyCard = true" in panel, "the enemy card lost its effectiveness")
        assertTrue("moveCtx = ndsMoveContext(state, p, enemyCard = false" in panel, "the party cards lost their effectiveness")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex("effectivenessReady = dsFxReady").findAll(play).count(), "both DS panels take the pause")
    }

    private fun assertNotNullCtx(c: NdsMoveContext?): NdsMoveContext = c ?: throw AssertionError("no move context")
}
