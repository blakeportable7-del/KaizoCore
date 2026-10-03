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
 * The DS tracker's swap in a double, triple, rotation or multi battle, as NDS-Ironmon-Tracker steps it
 * (Program.switchPokemonView, Program.lua:808-831, with BattleHandlerBase.updatePlayerSlotIndex and
 * updateEnemySlotIndex, BattleHandlerBase.lua:272-302): each of your Pokemon on the field in turn, then each of the
 * opponent's, then yours again. Until rc34 the DS view held one of each side.
 */
class DsDoublesViewTest {
    private fun tm(pid: Long, species: Int) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 20, 40, 40, listOf(33, 0, 0, 0)))!!,
        speciesName = "#$species", info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    private val p1 = tm(0x10, 7); private val p2 = tm(0x11, 25); private val p3 = tm(0x12, 1)
    private val e1 = tm(0x90, 16); private val e2 = tm(0x91, 19); private val e3 = tm(0x92, 21)

    private fun battle(players: List<NdsTrackedMon?>, enemies: List<NdsTrackedMon?>, enemy: NdsTrackedMon? = e1, rotation: Boolean = false, allies: Int = 0) =
        NdsTrackerState(3, listOf(p1, p2, p3), located = true, inBattle = true, enemy = enemy, healsPid = p1.mon.pid,
            playerBattlers = players, enemyBattlers = enemies, rotation = rotation, enemyAllies = allies)

    private val overworld = NdsTrackerState(3, listOf(p1, p2, p3), located = true, healsPid = p1.mon.pid)

    /** What the view shows after each swap: yours ("P" and its species) or the opponent's ("E"). */
    private fun walk(v: DsViewState, s: NdsTrackerState, steps: Int, stacked: Boolean = false): List<String> = List(steps) {
        v.swap(s, allowed = true, stacked = stacked)
        if (v.viewingEnemy) "E${v.shownEnemy(s)!!.mon.species}" else "P${v.shownPlayer(s)!!.mon.species}"
    }

    private fun viewIn(s: NdsTrackerState) = DsViewState().also { it.onRead(s) }

    @Test
    fun `a double battle walks each of yours, then each of the opponent's`() {
        val s = battle(listOf(p1, p2), listOf(e1, e2))
        val v = viewIn(s)
        assertEquals(7, v.shownPlayer(s)?.mon?.species)
        assertEquals(listOf("P25", "E16", "E19", "P7", "P25", "E16"), walk(v, s, 6))
    }

    @Test
    fun `a triple battle walks all three of each side`() {
        val s = battle(listOf(p1, p2, p3), listOf(e1, e2, e3))
        assertEquals(listOf("P25", "P1", "E16", "E19", "E21", "P7"), walk(viewIn(s), s, 6))
    }

    @Test
    fun `a single battle swaps yours and the opponent's as it always has`() {
        val s = battle(listOf(p1), listOf(e1))
        val v = viewIn(s)
        assertEquals(listOf("E16", "P7", "E16", "P7"), walk(v, s, 4))
        assertNull(v.sideWords(s, stacked = false))
        assertFalse(v.canSwap(s, allowed = true, stacked = true), "both on screen: nothing to swap")
        assertTrue(v.canSwap(s, allowed = true))
    }

    @Test
    fun `with both cards on screen each step changes one of them`() {
        val s = battle(listOf(p1, p2), listOf(e1, e2))
        val v = viewIn(s)
        val cards = List(3) { v.swap(s, allowed = true, stacked = true); v.shownPlayer(s)!!.mon.species to v.shownEnemy(s)!!.mon.species }
        assertEquals(listOf(25 to 16, 7 to 19, 7 to 16), cards)
    }

    @Test
    fun `the swap waits out a new opponent's pause, and auto swap keeps the opponent's slot`() {
        val s = battle(listOf(p1, p2), listOf(e1, e2))
        val v = viewIn(s)
        v.swap(s, allowed = false)
        assertFalse(v.viewingEnemy)
        assertEquals(7, v.shownPlayer(s)?.mon?.species, "isAllowedToSwap is false during the pause")
        v.swap(s, allowed = true)                                  // your right-hand one
        v.onPause(false, s, autoSwap = true); v.onPause(true, s, autoSwap = true)
        // Program.switchToEnemy only turns the view: the opponent's slot is where it was, and yours stays for the matchups.
        assertTrue(v.viewingEnemy)
        assertEquals(16, v.shownEnemy(s)?.mon?.species)
        assertEquals(25, v.shownPlayer(s)?.mon?.species)
    }

    @Test
    fun `a battle starts and ends on each side's first slot, and a slot that read nothing is passed over`() {
        val s = battle(listOf(p1, p2), listOf(e1, null))
        val v = viewIn(s)
        assertEquals(listOf("P25", "E16", "P7"), walk(v, s, 3))
        v.swap(s, allowed = true)
        assertEquals(1, v.playerSlot)
        v.onRead(overworld)
        assertEquals(0, v.playerSlot)
        assertFalse(v.viewingEnemy)
        assertEquals(p1, v.shownPlayer(overworld))
    }

    @Test
    fun `a locked opponent is a side of one`() {
        val s = battle(listOf(p1, p2), listOf(e1, e2))
        val v = viewIn(s)
        v.toggleLock(s, enabled = true)
        assertEquals(listOf("P25", "E16", "P7", "P25"), walk(v, s, 4))
        assertNull(v.sideWords(s, stacked = false), "the locked one has no slot")
    }

    @Test
    fun `the banner says where each stands`() {
        val d = battle(listOf(p1, p2), listOf(e1, e2))
        val v = viewIn(d)
        assertEquals(BannerSide("MINE ON THE LEFT", "MINE LEFT"), v.sideWords(d, stacked = false))
        assertEquals("Show my Pokémon on the right", v.swapSpoken(d, stacked = false))
        v.swap(d, allowed = true)
        assertEquals("MINE ON THE RIGHT", v.sideWords(d, stacked = false)?.full)
        assertTrue(v.offersFoe(d, stacked = false))
        v.swap(d, allowed = true)
        // The opponent's first stands on the right of your screen (BattleSideWords).
        assertEquals("FOE ON THE RIGHT", v.sideWords(d, stacked = false)?.full)
        v.swap(d, allowed = true)
        assertEquals("FOE ON THE LEFT", v.sideWords(d, stacked = false)?.full)
        assertEquals("MINE LEFT, FOE LEFT", v.sideWords(d, stacked = true)?.full)
        val t = battle(listOf(p1, p2, p3), listOf(e1, e2, e3))
        val tv = viewIn(t)
        tv.swap(t, allowed = true)
        assertEquals("MINE IN THE MIDDLE", tv.sideWords(t, stacked = false)?.full)
        assertEquals("Show my Pokémon on the right", tv.swapSpoken(t, stacked = false))
        tv.swap(t, allowed = true); tv.swap(t, allowed = true); tv.swap(t, allowed = true)
        assertEquals("FOE IN THE MIDDLE", tv.sideWords(t, stacked = false)?.full)
        val r = battle(listOf(p1, p2, p3), listOf(e1, e2, e3), rotation = true)
        val rv = viewIn(r)
        assertEquals(BannerSide("MINE 1 OF 3", "MINE 1 OF 3"), rv.sideWords(r, stacked = false), "a rotation battle's slots stand in no row")
        // A multi battle (Black and White, NdsTrackerState.enemyAllies): your one slot, your partner's read first among the opponent's.
        val m = battle(listOf(p1), listOf(p2, e1, e2), allies = 1)
        val mv = viewIn(m)
        assertEquals("MINE ON THE LEFT", mv.sideWords(m, stacked = false)?.full)
        assertEquals(listOf("E25", "E16", "E19", "P7"), walk(mv, m, 4))
        mv.swap(m, allowed = true)
        assertEquals("PARTNER ON THE RIGHT", mv.sideWords(m, stacked = false)?.full)
        mv.swap(m, allowed = true)
        assertEquals("FOE ON THE RIGHT", mv.sideWords(m, stacked = false)?.full)
        assertNull(viewIn(battle(listOf(p1), listOf(e1))).sideWords(battle(listOf(p1), listOf(e1)), stacked = false))
    }

    @Test
    fun `your right-hand Pokemon's moves are matched against the opponent, and the opponent's against it`() {
        val s = battle(listOf(p1, p2), listOf(e1, e2))
        assertNull(ndsMoveContext(s, p2, enemyCard = false, "Fighting", show = true, opponent = e1), "matched against nothing as the second")
        assertEquals(16, ndsMoveContext(s, p2, enemyCard = false, "Fighting", show = true, opponent = e1, shownActive = p2)?.targetSpecies)
        assertEquals(25, ndsMoveContext(s, e2, enemyCard = true, "Fighting", show = true, opponent = e2, shownActive = p2)?.targetSpecies)
        // Its heals are a share of its own max HP.
        assertTrue(ndsHealsView(s, p2, false, false, 10, false, false) == null, "the first one carried them alone")
        assertTrue(ndsHealsView(s, p2, false, false, 10, false, false, shownCarrier = p2) != null)
    }

    @Test
    fun `the DS panel draws the slot the view is on`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText().replace("\r\n", "\n")
        assertTrue("val shownPlayer = view.shownPlayer(state)" in panel)
        assertTrue("if (stackBoth || !showEnemy) shownPlayer?.let { p ->" in panel, "your card")
        assertTrue("val onSwap = if (view.canSwap(state, effectivenessReady, stackBoth)) { { view.swap(state, effectivenessReady, stackBoth) } } else null" in panel)
        assertTrue("side = side, swapSpoken = view.swapSpoken(state, stackBoth))" in panel, "the banner's words")
        assertTrue("pokecenterCount, TrackerOptions.tourneyTracker, TrackerOptions.dsAccEva, shownCarrier = p)," in panel, "its heals")
        assertTrue("opponent = shownEnemy, shownActive = p.takeIf { state.playerBattlers.size > 1 })," in panel, "your matchups")
        assertTrue("opponent = it, shownActive = shownPlayer.takeIf { state.playerBattlers.size > 1 })," in panel, "the opponent's matchups")
        // Each new opponent in another slot starts its own pause, as _logNewEnemy runs per slot.
        assertTrue("LaunchedEffect(state?.enemy?.mon?.pid, state?.enemyBattlers?.drop(1)?.map { it?.mon?.pid })" in panel)
    }
}
