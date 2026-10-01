package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deaths, the whiteout and the end of a run (2026-09-29). A faint is recorded on the poll that sees the
 * Pokemon at 0 HP, and nothing the game does afterwards, a whiteout's heal included, takes it back.
 */
class NuzlockeDeathTest {

    /** Two Pokemon in the party and a third alive in a box, all in Route 3 with a wild Rattata on the field. */
    private fun team(): Sim {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        s.moveTo("Route 4")
        s.catchIt(foe(101, Sp.SPEAROW, "SPEAROW", 4), mon(101, Sp.SPEAROW, "SPEAROW", 4))
        // The third is put in a box.
        s.party = s.party.filter { it.id != 101L }
        s.idle()
        assertFalse(s.ledger.roster.getValue(101L).inParty)
        return s
    }

    @Test
    fun `a Pokemon at 0 HP is dead on the poll that sees it, with where and what killed it`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 7))
        s.badges = 2
        s.wildBattle(foe(150, Sp.RATTATA, "RATTATA", 6))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        val dead = s.ledger.roster.getValue(100L)
        assertFalse(dead.alive)
        val death = assertNotNull(dead.death)
        assertEquals("wild Rattata Lv 6", death.cause)
        assertEquals("Route 3", death.areaName)
        assertEquals(2, death.badges)
        assertFalse(death.manual)
        assertEquals(listOf(100L), s.ledger.graveyard.map { it.id })
        assertTrue(s.ledger.roster.getValue(1L).alive)
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
    }

    @Test
    fun `a death in a trainer battle names the trainer`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.trainerBattle(NzOpponent(300, "YOUNGSTER JOEY", "Other", null, 9), foe(901, Sp.RATTATA, "RATTATA", 9))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        assertEquals("YOUNGSTER JOEY (Rattata Lv 9)", s.ledger.roster.getValue(100L).death!!.cause)
    }

    @Test
    fun `a faint outside a battle, poison for one, is a death too`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.idle()
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        assertEquals("outside a battle", s.ledger.roster.getValue(100L).death!!.cause)
    }

    @Test
    fun `a heal on the very next poll cannot bring a dead Pokemon back`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        // One poll saw it fainted. Now the party is at full HP again.
        s.party = listOf(s.party[0], s.party[1].copy(hp = 20))
        s.poll()
        assertFalse(s.ledger.roster.getValue(100L).alive, "the death stays")
        assertEquals(1, s.warnings(WarnKind.REVIVED).size, "and the ledger says it has HP again")
    }

    @Test
    fun `a death is recorded even when a whiteout heals everything a poll later`() {
        val s = Sim().starter()
        // Only Shell is in the game. It faints, and the whiteout heals it before a second poll.
        s.wildBattle(foe(150))
        s.party = listOf(s.party[0].copy(hp = 0))
        s.poll()
        assertFalse(s.ledger.roster.getValue(1L).alive)
        s.finish(BattleEnd.LOST)
        s.party = listOf(s.party[0].copy(hp = 20))
        s.poll()
        assertFalse(s.ledger.roster.getValue(1L).alive)
        // The whole party was down on the first poll that saw it, so the run ended then.
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertEquals("Whiteout", s.ledger.meta.endReason)
        // The battle it died in can no longer be watched to its end, so its encounter is settled as lost.
        assertEquals(Outcome.LOST, s.enc("Route 3")!!.outcome)
    }

    @Test
    fun `a whiteout ends the run on the first poll that shows the whole party down, even with Pokemon alive in a box`() {
        val s = team()
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertEquals("Whiteout", s.ledger.meta.endReason)
        assertTrue(s.ledger.events.any { it.kind == "whiteout" })
        assertEquals(2, s.ledger.graveyard.size)
        assertTrue(s.ledger.roster.getValue(101L).alive, "the boxed one is alive")
    }

    @Test
    fun `with the whiteout rule off the run goes on and the whiteout is logged once`() {
        val s = Sim(rules { it.copy(whiteoutEndsRun = false) }).starter()
        s.catchIt(foe(100), mon(100))
        s.moveTo("Route 4")
        s.catchIt(foe(101, Sp.SPEAROW, "SPEAROW", 4), mon(101, Sp.SPEAROW, "SPEAROW", 4))
        s.party = s.party.filter { it.id != 101L }
        s.idle()
        s.party = s.party.map { it.copy(hp = 0) }
        repeat(4) { s.poll() }
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        assertEquals(1, s.ledger.events.count { it.kind == "whiteout" })
        // Healed, then down again: a second whiteout.
        s.party = s.party.map { it.copy(hp = 20) }
        s.poll()
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        assertEquals(2, s.ledger.events.count { it.kind == "whiteout" })
    }

    @Test
    fun `with the whiteout rule off the last Pokemon to die still ends the run`() {
        val s = Sim(rules { it.copy(whiteoutEndsRun = false) }).starter()
        s.party = listOf(s.party[0].copy(hp = 0))
        s.poll()
        assertEquals(1, s.ledger.events.count { it.kind == "whiteout" })
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertEquals("No living Pokemon left", s.ledger.meta.endReason)
    }

    @Test
    fun `a party that is down is one whiteout however many polls show it`() {
        val s = team()
        s.party = s.party.map { it.copy(hp = 0) }
        repeat(5) { s.poll() }
        assertEquals(1, s.ledger.events.count { it.kind == "whiteout" })
    }

    @Test
    fun `with faints not counted as deaths nobody dies`() {
        val s = Sim(rules { it.copy(faintIsDeath = false) }).starter()
        s.catchIt(foe(100), mon(100))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        assertTrue(s.ledger.roster.getValue(100L).alive)
        assertTrue(s.ledger.graveyard.isEmpty())
    }

    @Test
    fun `nothing is recorded once the run is over`() {
        val s = team()
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        val rev = s.ledger.revision
        s.party = s.party.map { it.copy(hp = 20) }
        s.moveTo("Route 9")
        s.wildEncounter(foe(300), BattleEnd.WON)
        assertEquals(rev, s.ledger.revision)
        assertFalse(s.ledger.areas.containsKey("Route 9"))
    }

    @Test
    fun `an egg at 0 HP is not a casualty and does not make a whiteout`() {
        val s = Sim().starter()
        s.party = s.party + mon(60, Sp.PIDGEY, "PIDGEY", 1, hp = 0, egg = true)
        repeat(3) { s.poll() }
        assertTrue(s.ledger.roster.getValue(1L).alive)
        assertFalse(s.ledger.roster.containsKey(60L), "an egg is not on the roster")
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
    }

    @Test
    fun `before the rules begin a faint is not a death`() {
        val s = Sim()
        s.balls = 0
        s.starter()
        s.party = listOf(s.party[0].copy(hp = 0))
        repeat(3) { s.poll() }
        assertTrue(s.ledger.roster.getValue(1L).alive)
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
    }

    private fun rules(tweak: (NuzlockeRules) -> NuzlockeRules) = com.ironmonone.tracker.nuzlocke.rules(NuzlockePreset.STANDARD, tweak)
}
