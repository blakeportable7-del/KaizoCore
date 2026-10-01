package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the tracker's Nuzlocke panel says (2026-09-29). */
class NuzlockeViewTest {

    private fun lines(s: Sim) = NuzlockeView.panel(s.ledger, s.snapshot()).lines.map { it.text }

    @Test
    fun `an area with no encounter yet is open`() {
        val s = Sim().starter()
        assertEquals(listOf("Route 3: first encounter open"), lines(s))
    }

    @Test
    fun `an area says what its first encounter was and how it ended`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.SPEAROW, "SPEAROW", 4), mon(100, Sp.SPEAROW, "SPEAROW", 4))
        assertEquals("Route 3: Spearow caught", lines(s).single())
        assertEquals(NuzlockeView.Tone.GOOD, NuzlockeView.areaLine(s.ledger, s.snapshot()).tone)
        val t = Sim().starter()
        t.wildEncounter(foe(100), BattleEnd.WON)
        assertEquals("Route 3: Rattata fainted", lines(t).single())
        assertEquals(NuzlockeView.Tone.BAD, NuzlockeView.areaLine(t.ledger, t.snapshot()).tone)
        val u = Sim().starter()
        u.wildBattle(foe(100))
        assertEquals("Route 3: Rattata in battle", lines(u).single())
    }

    @Test
    fun `an area that skipped a dupe says so and stays open`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        s.moveTo("Route 4")
        s.wildEncounter(foe(110, Sp.PIDGEOTTO, "PIDGEOTTO", 18), BattleEnd.WON)
        assertEquals("Route 4: first encounter open (skipped Pidgeotto, dupe)", lines(s).single())
    }

    @Test
    fun `the panel follows the player from area to area`() {
        val s = Sim().starter()
        s.wildEncounter(foe(100), BattleEnd.WON)
        s.moveTo("Route 4")
        assertEquals("Route 4: first encounter open", lines(s).single())
        s.moveTo("Route 3")
        assertEquals("Route 3: Rattata fainted", lines(s).single())
    }

    @Test
    fun `a place with no name has no area line, so the title screen never says Map 0`() {
        val s = Sim().starter()
        s.area = NzArea(null, 0)
        assertTrue(lines(s).none { it.startsWith("Map ") || "first encounter" in it }, lines(s).toString())
        // A map the tracker cannot name still shows once something happened there.
        val t = Sim().starter()
        t.area = NzArea(null, 57)
        t.wildEncounter(foe(100), BattleEnd.WON)
        assertEquals("Map 57: Rattata fainted", lines(t).single())
    }

    @Test
    fun `before the rules begin the panel says it is waiting for Poke Balls`() {
        val s = Sim()
        s.balls = 0
        s.starter()
        assertTrue(lines(s).first().startsWith("Waiting for Poke Balls"), lines(s).toString())
    }

    @Test
    fun `an ended run heads the panel and says why`() {
        val s = Sim().starter()
        NuzlockeEdits(s.ledger).endRun("Whiteout", 5)
        assertEquals("Run over: Whiteout", lines(s).first())
        val done = Sim().also { it.caps = LevelCapTable.standard("frlg") }.starter()
        done.trainerBattle(NzOpponent(438, "RIVAL BLUE", "Elite4", "champion", 63)); done.finish(BattleEnd.WON)
        assertEquals("Champion beaten. The run is complete.", lines(done).first())
    }

    @Test
    fun `the counts and the title come from the ledger`() {
        val s = Sim(rules(NuzlockePreset.MONOTYPE) { it.copy(monotypeType = WATER) }).starter()
        s.catchIt(foe(100, Sp.MAGIKARP, "MAGIKARP", 5, types = listOf(WATER)), mon(100, Sp.MAGIKARP, "MAGIKARP", 5, types = listOf(WATER)))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        s.ledger.warn("w", s.now, WarnKind.OTHER, "x")
        val p = NuzlockeView.panel(s.ledger, s.snapshot())
        assertEquals("NUZLOCKE  Monotype (Water)", p.title)
        assertEquals(1, p.alive); assertEquals(1, p.graveyard); assertEquals(1, p.warnings)
    }

    @Test
    fun `no snapshot yet reads as waiting for the game`() {
        val s = Sim().starter()
        assertEquals("Waiting for the game", NuzlockeView.areaLine(s.ledger, null).text)
    }

    @Test
    fun `the Shift reminder shows while the style is Shift`() {
        val s = Sim(rules(NuzlockePreset.HARDCORE)).starter()
        s.style = false
        s.poll()
        assertTrue(lines(s).any { it.startsWith("Battle style is Shift") }, lines(s).toString())
        s.style = true
        s.poll()
        assertTrue(lines(s).none { it.startsWith("Battle style is Shift") })
    }
}
