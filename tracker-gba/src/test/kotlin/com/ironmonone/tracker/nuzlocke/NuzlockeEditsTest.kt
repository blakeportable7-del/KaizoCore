package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Everything the tracker decided can be corrected by hand, and a correction is not undone by the next poll
 * (2026-09-29).
 */
class NuzlockeEditsTest {

    private fun edits(s: Sim) = NuzlockeEdits(s.ledger)
    private val celadon = AreaKey("Celadon City", "Celadon City")
    private val route3 = AreaKey("Route 3", "Route 3")

    @Test
    fun `an encounter that should not count is cleared and the area opens again`() {
        val s = Sim().starter()
        s.wildEncounter(foe(100), BattleEnd.WON)
        assertEquals("Rattata", s.enc("Route 3")!!.speciesName)
        assertTrue(edits(s).clearEncounter("Route 3", s.now))
        assertNull(s.enc("Route 3"))
        s.wildEncounter(foe(101, Sp.SPEAROW, "SPEAROW"), BattleEnd.WON)
        assertEquals("Spearow", s.enc("Route 3")!!.speciesName)
        assertTrue(s.ledger.events.any { it.manual && it.kind == "edit" })
        assertFalse(edits(s).clearEncounter("Route 99", s.now), "an area with no encounter has nothing to clear")
    }

    @Test
    fun `a gift the player wants counted becomes its area's encounter`() {
        val s = Sim().starter()
        s.moveTo("Celadon City")
        s.party = s.party + mon(50, Sp.EEVEE, "EEVEE", 25)
        s.idle()
        assertNull(s.enc("Celadon City"))
        assertTrue(edits(s).countAsEncounter(50L, celadon, s.now))
        val enc = assertNotNull(s.enc("Celadon City"))
        assertEquals(50L, enc.monId)
        assertEquals(Outcome.CAUGHT, enc.outcome)
        assertTrue(enc.manual)
        // A wild Pokemon there is now a second encounter.
        s.wildEncounter(foe(101), BattleEnd.WON)
        assertEquals("Eevee", s.enc("Celadon City")!!.speciesName)
    }

    @Test
    fun `an encounter set by hand as caught puts a Pokemon on the roster and feeds the dupes clause`() {
        val s = Sim().starter()
        edits(s).setEncounter(route3, Sp.PIDGEY, "PIDGEY", 4, Outcome.CAUGHT, s.now)
        val enc = assertNotNull(s.enc("Route 3"))
        assertEquals(Outcome.CAUGHT, enc.outcome)
        val mon = s.ledger.roster.getValue(assertNotNull(enc.monId))
        assertEquals("Pidgey", mon.speciesName)
        assertFalse(mon.inParty)
        assertTrue(mon.id < 0, "a Pokemon made by hand has a negative id")
        // Pidgeotto is Pidgey's line, so it is a dupe in the next area.
        s.moveTo("Route 4")
        s.wildEncounter(foe(110, Sp.PIDGEOTTO, "PIDGEOTTO", 18), BattleEnd.WON)
        assertNull(s.enc("Route 4"))
    }

    @Test
    fun `a manual encounter is left alone by the tracker's next poll`() {
        val s = Sim().starter()
        edits(s).setEncounter(route3, Sp.PIDGEY, "PIDGEY", 4, Outcome.FLED, s.now)
        s.wildEncounter(foe(100), BattleEnd.WON)
        val enc = s.enc("Route 3")!!
        assertEquals("Pidgey", enc.speciesName)
        assertEquals(Outcome.FLED, enc.outcome)
        assertTrue(enc.manual)
    }

    @Test
    fun `a death that does not count is ignored until the Pokemon has been seen healthy`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        assertFalse(s.ledger.roster.getValue(100L).alive)
        assertTrue(edits(s).markAlive(100L, s.now))
        assertTrue(s.ledger.roster.getValue(100L).alive)
        assertNull(s.ledger.roster.getValue(100L).death)
        // Still at 0 HP on the next polls: not killed again.
        repeat(3) { s.poll() }
        assertTrue(s.ledger.roster.getValue(100L).alive)
        // Healed, then it faints for real.
        s.party = listOf(s.party[0], s.party[1].copy(hp = 20))
        s.poll()
        assertFalse(s.ledger.roster.getValue(100L).forgiven)
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        assertFalse(s.ledger.roster.getValue(100L).alive)
    }

    @Test
    fun `what killed a Pokemon can be corrected, and the rest of the death stays as it was`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        val seen = s.ledger.roster.getValue(100L).death!!
        assertFalse(seen.manual)
        assertTrue(edits(s).setCause(100L, "  burn  from\tthe\nlast\rbattle ", s.now))
        val fixed = s.ledger.roster.getValue(100L).death!!
        assertEquals("burn from the last battle", fixed.cause)
        assertEquals(seen.at, fixed.at)
        assertEquals(seen.level, fixed.level)
        assertEquals(seen.areaName, fixed.areaName)
        assertEquals(seen.badges, fixed.badges)
        assertFalse(fixed.manual, "a death the tracker saw is still one it saw")
        assertTrue(s.ledger.events.any { it.manual && it.text.contains("burn from the last battle") })
        // Nothing to change, nothing to change it on: no edit is logged.
        val events = s.ledger.events.size
        assertFalse(edits(s).setCause(100L, "burn from the last battle", s.now))
        assertFalse(edits(s).setCause(100L, "   ", s.now))
        assertFalse(edits(s).setCause(1L, "alive and well", s.now), "the starter has not died")
        assertFalse(edits(s).setCause(12345L, "nobody", s.now))
        assertEquals(events, s.ledger.events.size)
    }

    @Test
    fun `a rule changed by hand applies from the next poll and is written in the log`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        s.moveTo("Route 4")
        s.wildEncounter(foe(110, Sp.PIDGEOTTO, "PIDGEOTTO", 18), BattleEnd.WON)
        assertNull(s.enc("Route 4"), "a dupe while the clause is on")
        assertTrue(edits(s).setClause("dupes", false, s.now))
        assertFalse(s.ledger.meta.rules.dupes)
        s.wildEncounter(foe(111, Sp.PIDGEOTTO, "PIDGEOTTO", 19), BattleEnd.WON)
        assertEquals("Pidgeotto", s.enc("Route 4")!!.speciesName, "the same Pokemon counts once the clause is off")
        val edit = s.ledger.events.single { it.manual && it.text.startsWith("Rule changed by hand") }
        assertTrue("Dupes clause" in edit.text && "off" in edit.text, edit.text)
        // And back on: the next area's dupe is skipped again.
        assertTrue(edits(s).setClause("dupes", true, s.now))
        s.moveTo("Route 5")
        s.wildEncounter(foe(112, Sp.PIDGEOT, "PIDGEOT", 40), BattleEnd.WON)
        assertNull(s.enc("Route 5"))
    }

    @Test
    fun `a rule changed by hand is kept in the ledger's file, and nothing else about the rules moves`() {
        val s = Sim(rules(NuzlockePreset.HARDCORE)).starter()
        val before = s.ledger.meta.rules
        assertTrue(edits(s).setClause("levelCaps", false, s.now))
        assertTrue(edits(s).setSafari(SafariRule.ONE_AREA, s.now))
        val back = NuzlockeText.parse(NuzlockeText.format(s.ledger))!!.meta.rules
        assertFalse(back.levelCaps)
        assertEquals(SafariRule.ONE_AREA, back.safari)
        assertEquals(before.copy(levelCaps = false, safari = SafariRule.ONE_AREA), back)
        assertEquals(NuzlockePreset.HARDCORE, back.preset, "the preset a run began as is not rewritten")
    }

    @Test
    fun `changing a rule to what it already is, or one that does not exist, changes nothing`() {
        val s = Sim().starter()
        val events = s.ledger.events.size
        val revision = s.ledger.revision
        assertFalse(edits(s).setClause("dupes", true, s.now), "already on")
        assertFalse(edits(s).setClause("no such rule", true, s.now))
        assertFalse(edits(s).setSafari(SafariRule.PER_ZONE, s.now), "already per zone")
        assertEquals(events, s.ledger.events.size)
        assertEquals(revision, s.ledger.revision)
        // Every switch the start screen offers can be changed here, and changed back.
        for (c in NuzlockeRules.CLAUSES) {
            val was = c.get(s.ledger.meta.rules)
            assertTrue(edits(s).setClause(c.key, !was, s.now), c.key)
            assertEquals(!was, c.get(s.ledger.meta.rules), c.key)
            assertTrue(edits(s).setClause(c.key, was, s.now), c.key)
        }
        assertEquals(Sim().ledger.meta.rules, s.ledger.meta.rules)
    }

    @Test
    fun `a death marked by hand is not answered with a revived warning`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        assertTrue(edits(s).markDead(100L, "poison on Route 3", s.now))
        val death = s.ledger.roster.getValue(100L).death!!
        assertTrue(death.manual)
        assertEquals("poison on Route 3", death.cause)
        repeat(2) { s.poll() }
        assertTrue(s.warnings(WarnKind.REVIVED).isEmpty())
        assertFalse(edits(s).markDead(100L, "again", s.now), "already dead")
    }

    @Test
    fun `a Pokemon the tracker never saw can be added to the roster`() {
        val s = Sim().starter()
        val id = edits(s).addMon("ODDISH", 43, 7, Origin.CAUGHT, AreaKey("Route 5", "Route 5"), s.now)
        val mon = s.ledger.roster.getValue(id)
        assertEquals("Oddish", mon.speciesName)
        assertEquals(Origin.CAUGHT, mon.origin)
        assertTrue(mon.alive)
    }

    @Test
    fun `a gift marked as a catch changes what it is`() {
        val s = Sim().starter()
        s.party = s.party + mon(50, Sp.EEVEE, "EEVEE", 25)
        s.idle()
        assertEquals(Origin.GIFT, s.ledger.roster.getValue(50L).origin)
        assertTrue(edits(s).setOrigin(50L, Origin.STATIC, s.now))
        assertEquals(Origin.STATIC, s.ledger.roster.getValue(50L).origin)
        assertFalse(edits(s).setOrigin(50L, Origin.STATIC, s.now), "no change, no event")
    }

    @Test
    fun `a run ended by mistake can be opened again, and a whiteout does not end it twice`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        s.moveTo("Route 4")
        s.catchIt(foe(101, Sp.SPEAROW, "SPEAROW", 4), mon(101, Sp.SPEAROW, "SPEAROW", 4))
        s.party = s.party.filter { it.id != 101L }
        s.idle()
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertTrue(edits(s).reopen(s.now))
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        // Still fainted: the whiteout is the same one and does not end the run again.
        repeat(3) { s.poll() }
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        // A team that is well again, then down again, is a new whiteout.
        s.party = s.party.map { it.copy(hp = 20) }
        s.poll()
        s.party = s.party.map { it.copy(hp = 0) }
        s.poll()
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertTrue(edits(s).reopen(s.now), "it can be opened again")
    }

    @Test
    fun `a run can be ended by hand, given a note, and a warning can be dismissed`() {
        val s = Sim().starter()
        s.wildEncounter(foe(100), BattleEnd.WON)
        s.catchIt(foe(101, Sp.SPEAROW, "SPEAROW", 4), mon(101, Sp.SPEAROW, "SPEAROW", 4))
        val warning = s.warnings(WarnKind.SECOND_CATCH).single()
        assertTrue(edits(s).dismiss(warning.id))
        assertTrue(s.ledger.openWarnings.isEmpty())
        assertFalse(edits(s).dismiss(warning.id))
        assertTrue(edits(s).addNote("Blackout at the Pokemon Center door\tdoes not count", s.now))
        assertEquals("Blackout at the Pokemon Center door does not count", s.ledger.notes.single())
        assertFalse(edits(s).addNote("   ", s.now))
        assertTrue(edits(s).endRun("Gave up", s.now))
        assertEquals(RunStatus.OVER, s.ledger.meta.status)
        assertEquals("Gave up", s.ledger.meta.endReason)
        assertFalse(edits(s).endRun("again", s.now))
    }
}
