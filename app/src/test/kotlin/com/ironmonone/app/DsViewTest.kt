package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS tracker's one-Pokemon view by touch (Program.lua: switchPokemonView,
 * switchToEnemy, lockEnemy and unlockEnemy; BattleHandlerBase.isAllowedToSwap).
 */
class DsViewTest {
    private fun tm(pid: Long, species: Int) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 20, 40, 40, listOf(33, 0, 0, 0)))!!,
        speciesName = "#$species", info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    private val mine = tm(0x10, 7)
    private val foe = tm(0x90, 16)
    private val nextFoe = tm(0x91, 19)
    private fun battle(enemy: NdsTrackedMon? = foe) =
        NdsTrackerState(1, listOf(mine), located = true, inBattle = true, enemy = enemy, healsPid = 0x10)
    private val overworld = NdsTrackerState(1, listOf(mine), located = true, healsPid = 0x10)

    @Test
    fun `the swap goes between yours and the opponent, never during a new opponent's pause`() {
        val v = DsViewState()
        assertFalse(v.viewingEnemy, "a battle opens on your Pokemon")
        v.swap(battle(), allowed = false)
        assertFalse(v.viewingEnemy, "isAllowedToSwap is false during the pause")
        assertFalse(v.canSwap(battle(), allowed = false))
        v.swap(battle(), allowed = true)
        assertTrue(v.viewingEnemy)
        v.swap(battle(), allowed = true)
        assertFalse(v.viewingEnemy)
        assertFalse(v.canSwap(battle(enemy = null), allowed = true), "no opponent read yet")
        assertFalse(v.canSwap(overworld, allowed = true), "nothing to see after a battle unless it is locked")
    }

    @Test
    fun `the battle ending brings your Pokemon back`() {
        val v = DsViewState()
        v.swap(battle(), allowed = true)
        v.onRead(battle())
        assertTrue(v.viewingEnemy)
        v.onRead(overworld)
        assertFalse(v.viewingEnemy)
    }

    @Test
    fun `auto swap happens as a new opponent's pause ends, and only with the setting`() {
        val v = DsViewState()
        v.onPause(false, battle(), autoSwap = true)
        assertFalse(v.viewingEnemy, "not while the pause runs")
        v.onPause(true, battle(), autoSwap = true)
        assertTrue(v.viewingEnemy)
        val off = DsViewState()
        off.onPause(false, battle(), autoSwap = false)
        off.onPause(true, battle(), autoSwap = false)
        assertFalse(off.viewingEnemy, "AUTO_SWAP_TO_ENEMY is off by default on DS")
        val turned = DsViewState()
        turned.onPause(true, battle(), autoSwap = true)
        assertFalse(turned.viewingEnemy, "no pause ended: a panel composed mid-battle does not swap")
    }

    @Test
    fun `the lock needs its setting and an opponent, keeps it, and outlasts the battle`() {
        val v = DsViewState()
        v.toggleLock(battle(), enabled = false)
        assertNull(v.locked, "ENABLE_ENEMY_LOCKING is off by default")
        v.toggleLock(overworld, enabled = true)
        assertNull(v.locked, "lockEnemy needs a battle")
        v.toggleLock(battle(), enabled = true)
        assertEquals(foe, v.locked)
        // The opponent switches: the enemy view still shows the locked one, and yours aims at it.
        assertEquals(foe, v.shownEnemy(battle(enemy = nextFoe)))
        // After the battle the locked one can still be seen, and readMemory leaves the view on it.
        v.swap(overworld, allowed = false)
        assertTrue(v.viewingEnemy)
        v.onRead(overworld)
        assertTrue(v.viewingEnemy)
        assertEquals(foe, v.shownEnemy(overworld))
        // Let go after the battle: your Pokemon again.
        v.toggleLock(overworld, enabled = true)
        assertNull(v.locked)
        assertFalse(v.viewingEnemy)
    }

    @Test
    fun `unlocking in a battle stays on the opponent, now the one on the field`() {
        val v = DsViewState()
        v.toggleLock(battle(), enabled = true)
        v.swap(battle(enemy = nextFoe), allowed = true)
        v.toggleLock(battle(enemy = nextFoe), enabled = true)
        assertNull(v.locked)
        assertTrue(v.viewingEnemy)
        assertEquals(nextFoe, v.shownEnemy(battle(enemy = nextFoe)))
    }

    @Test
    fun `a new run starts on your Pokemon with nothing locked`() {
        val v = DsViewState()
        v.forAttempt(3)
        v.toggleLock(battle(), enabled = true)
        v.swap(battle(), allowed = true)
        v.forAttempt(3)
        assertEquals(foe, v.locked, "the same run keeps both")
        v.forAttempt(4)
        assertNull(v.locked)
        assertFalse(v.viewingEnemy)
        // Leaving the play screen clears it too, so a lock never reaches the next game's notebook.
        v.toggleLock(battle(), enabled = true)
        v.swap(battle(), allowed = true)
        v.clear()
        assertNull(v.locked)
        assertFalse(v.viewingEnemy)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("onDispose { AutoTheme.release(); dsView.clear() }" in play)
    }

    @Test
    fun `the panels show one Pokemon, swap by touch, and bind no game button`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertTrue("if (stackBoth || !showEnemy) state.playerPokemon?.let { p ->" in panel, "your one card")
        assertTrue("if (showEnemy) shownEnemy?.let {" in panel, "the opponent's card")
        // SETUP rides in the banner during a battle (2026-10-02): it had a row of its own.
        assertTrue("PcBattleBanner(state.isWildBattle, onFlee, viewingOwn = !showEnemy, onSwapView = onSwap, onGear = onGear)" in panel)
        assertTrue("onLock = { view.toggleLock(state, TrackerOptions.dsEnemyLocking) }" in panel)
        assertTrue("view.onPause(effectivenessReady, state, TrackerOptions.dsAutoSwapToEnemy)" in panel)
        assertFalse("state.party.forEachIndexed" in panel, "the six cards are back")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex("stackBoth = true,").findAll(play).count(), "landscape stacks both on GBA and on DS")
        assertTrue("val notebookSpecies = dsView.locked?.mon?.species ?: enemySpecies" in play, "the locked opponent's notebook")
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue("GearToggle(\"Enable enemy locking\", TrackerOptions.dsEnemyLocking)" in gear)
        assertTrue("GearToggle(\"Auto swap to enemy\", TrackerOptions.dsAutoSwapToEnemy)" in gear)
        // Touch only (Blake, 2026-09-29): nothing here reads a DS button for the tracker.
        val view = File("src/main/kotlin/com/ironmonone/app/DsView.kt").readText()
        assertTrue(listOf("KeyEvent", "KeyBindings", "Button.START", "Button.SELECT").none { it in view || it in panel })
    }
}
