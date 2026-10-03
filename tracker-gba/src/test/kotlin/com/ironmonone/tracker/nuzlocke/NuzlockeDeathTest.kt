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

    /**
     * rc32 audit P2 #141: Emerald's Battle Tents and the Battle Frontier lend a party for their battles (three of yours, or
     * Slateport's rentals) and give the real one back afterwards. Their faints are no deaths, losing is no whiteout, and a
     * rental is nobody's gift; when the real party comes back nothing has changed, so no revive warning follows either.
     */
    @Test
    fun `nothing a facility's lent party does is the run's`() {
        val s = team()
        val real = s.party
        val events = s.ledger.events.size
        // Verdanturf: two of yours enter, and lose.
        s.facility = true
        s.party = real.take(2)
        s.poll()
        s.trainerBattle(NzOpponent(950, "TENT TRAINER", "Other", null, 30))
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        s.finish(BattleEnd.LOST)
        // Slateport: three rentals, new to the ledger.
        s.party = listOf(mon(700, Sp.EEVEE, "EEVEE"), mon(701, Sp.MAGIKARP, "MAGIKARP"), mon(702, Sp.RATTATA, "RATTATA"))
        s.poll()
        s.trainerBattle(NzOpponent(951, "TENT TRAINER", "Other", null, 30))
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        s.finish(BattleEnd.LOST)
        // The real party back, as it was.
        s.facility = false
        s.party = real
        s.idle()
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status, "no whiteout ended the run")
        assertTrue(s.ledger.graveyard.isEmpty(), "no deaths")
        assertTrue(s.ledger.roster.keys.none { it >= 700L }, "the rentals are nobody's")
        assertTrue(s.warnings(WarnKind.REVIVED).isEmpty())
        assertEquals(events, s.ledger.events.size, "the ledger did not move")
        assertTrue(s.ledger.roster.getValue(1L).inParty && s.ledger.roster.getValue(100L).inParty)
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

    @Test
    fun `Shedinja shares Ninjask's game id and is still a Pokemon of its own`() {
        // rc33 audit P1 #78: Shedinja is a copy of the Nincada that made it, personality value and all.
        val nincada = 301; val ninjask = 302; val shedinja = 303
        val s = Sim().starter()
        s.catchIt(foe(500, nincada, "NINCADA", 15), mon(500, nincada, "NINCADA", 15, nickname = "Bug"))
        s.idle()
        // It evolves at 20 with a free slot: Ninjask where Nincada was, Shedinja at the end, the same id.
        s.party = listOf(s.party[0], mon(500, ninjask, "NINJASK", 20, nickname = "Bug"), mon(500, shedinja, "SHEDINJA", 20, hp = 1, maxHp = 1, nickname = "SHEDINJA", gender = null))
        s.poll()
        val jask = s.ledger.roster.getValue(500L)
        assertEquals(ninjask, jask.species)
        val twin = s.ledger.roster.values.single { it.species == shedinja }
        assertTrue(twin.id != 500L, "a record of its own")
        assertEquals(Origin.EXTRA, twin.origin, "no encounter, no catch, no gift: a free extra")
        assertEquals(3, s.ledger.alive.size)
        assertFalse(s.poll(), "and nothing changes on the next poll: the two no longer take turns at one record")
        // Reordered, each keeps its own record.
        s.party = listOf(s.party[2], s.party[1], s.party[0])
        s.poll()
        assertEquals(ninjask, s.ledger.roster.getValue(500L).species)
        assertEquals(shedinja, s.ledger.roster.getValue(twin.id).species)
        // Shedinja faints: it dies, and Ninjask is not called revived.
        s.party = listOf(s.party[0].copy(hp = 0), s.party[1], s.party[2])
        s.poll(); s.poll()
        assertFalse(s.ledger.roster.getValue(twin.id).alive)
        assertTrue(s.ledger.roster.getValue(500L).alive)
        assertTrue(s.warnings(WarnKind.REVIVED).isEmpty(), "nobody was revived")
        // Then Ninjask faints, and that is recorded too.
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0), s.party[2])
        s.poll()
        assertFalse(s.ledger.roster.getValue(500L).alive)
    }
}
