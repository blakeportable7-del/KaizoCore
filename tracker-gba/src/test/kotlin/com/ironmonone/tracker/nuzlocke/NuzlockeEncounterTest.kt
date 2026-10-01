package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first-encounter clause, driven by sequences of game states: what counts as an area's encounter, how it
 * ended, and what the dupes, shiny, gift, static and slow start clauses leave out (2026-09-29).
 */
class NuzlockeEncounterTest {

    @Test
    fun `the first wild Pokemon in an area is its encounter, and catching it is recorded`() {
        val s = Sim().starter()
        s.wildBattle(foe(100, Sp.PIDGEY, "PIDGEY", 3))
        val enc = assertNotNull(s.enc("Route 3"))
        assertEquals(Outcome.IN_PROGRESS, enc.outcome)
        assertEquals("Pidgey", enc.speciesName)
        s.party = s.party + mon(100, Sp.PIDGEY, "PIDGEY", 3, nickname = "Peck")
        s.poll()
        s.finish(BattleEnd.CAUGHT)
        assertEquals(Outcome.CAUGHT, enc.outcome)
        assertEquals(100L, enc.monId)
        val caught = assertNotNull(s.ledger.roster[100L])
        assertEquals(Origin.CAUGHT, caught.origin)
        assertTrue(caught.alive && caught.inParty && !caught.violation)
        assertEquals("Route 3", caught.areaName)
        assertTrue(s.ledger.openWarnings.isEmpty(), s.ledger.openWarnings.joinToString { it.text })
    }

    @Test
    fun `the starter is taken in as the starter and never uses an area`() {
        val s = Sim(rules { it.copy(giftsCount = true) }).starter()
        val starter = assertNotNull(s.ledger.roster[1L])
        assertEquals(Origin.STARTER, starter.origin)
        assertTrue(s.ledger.meta.started)
        // Gifts count in these rules, and the starter still does not.
        assertTrue(s.ledger.areas.isEmpty())
    }

    @Test
    fun `a party already there when the ledger first looks is taken in as had already`() {
        val s = Sim()
        s.party = listOf(mon(1, level = 30), mon(2, Sp.RATICATE, "RATICATE", 25))
        s.idle()
        assertEquals(setOf(Origin.EXISTING), s.ledger.roster.values.map { it.origin }.toSet())
        assertTrue(s.ledger.areas.isEmpty())
    }

    @Test
    fun `a second wild Pokemon in the same area is not written down, and catching it is flagged`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        val before = s.ledger.events.size
        s.wildEncounter(foe(101, Sp.RATTATA, "RATTATA", 3), BattleEnd.WON)
        assertEquals("Pidgey", s.enc("Route 3")!!.speciesName)
        assertEquals(before, s.ledger.events.size, "an ordinary battle in a used area is not an event")
        assertTrue(s.ledger.areas.getValue("Route 3").extras.isEmpty())
        assertTrue(s.ledger.warnings.isEmpty())
        // The third one is caught anyway.
        s.catchIt(foe(102, Sp.RATTATA, "RATTATA", 4), mon(102, Sp.RATTATA, "RATTATA", 4))
        val extra = s.ledger.areas.getValue("Route 3").extras.single()
        assertEquals(ExtraKind.SECOND, extra.kind)
        assertTrue(s.ledger.roster.getValue(102L).violation)
        assertEquals(1, s.warnings(WarnKind.SECOND_CATCH).size)
    }

    @Test
    fun `how an encounter ended is read from the game's outcome`() {
        val cases = listOf(
            BattleEnd.WON to Outcome.FAINTED, BattleEnd.RAN to Outcome.RAN, BattleEnd.MON_FLED to Outcome.FLED,
            BattleEnd.LOST to Outcome.LOST, BattleEnd.DREW to Outcome.LOST, BattleEnd.UNKNOWN to Outcome.UNKNOWN,
        )
        for ((i, c) in cases.withIndex()) {
            val s = Sim().starter()
            s.moveTo("Route ${10 + i}")
            s.wildEncounter(foe(200L + i), c.first)
            assertEquals(c.second, s.enc("Route ${10 + i}")!!.outcome, "game says ${c.first}")
        }
    }

    @Test
    fun `with no outcome from the game, an enemy seen at 0 HP fainted`() {
        val s = Sim().starter()
        s.wildBattle(foe(100, hp = 10))
        s.enemy = foe(100, hp = 0)
        s.poll()
        s.finish(BattleEnd.UNKNOWN)
        assertEquals(Outcome.FAINTED, s.enc("Route 3")!!.outcome)
    }

    @Test
    fun `a Pokemon that fled uses the area, unless the escape clause is on`() {
        val plain = Sim().starter()
        plain.wildEncounter(foe(100), BattleEnd.MON_FLED)
        assertEquals(Outcome.FLED, plain.enc("Route 3")!!.outcome)
        plain.wildEncounter(foe(101, Sp.SPEAROW, "SPEAROW"), BattleEnd.WON)
        assertEquals("Rattata", plain.enc("Route 3")!!.speciesName, "the flee already used the area")

        val escape = Sim(rules { it.copy(escapeClause = true) }).starter()
        escape.wildEncounter(foe(100), BattleEnd.MON_FLED)
        assertNull(escape.enc("Route 3"), "the escape clause opens the area again")
        escape.wildEncounter(foe(101, Sp.SPEAROW, "SPEAROW"), BattleEnd.WON)
        assertEquals("Spearow", escape.enc("Route 3")!!.speciesName)
    }

    @Test
    fun `a dupe does not use the area, across the whole evolution line`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        s.moveTo("Route 4")
        s.wildEncounter(foe(110, Sp.PIDGEOTTO, "PIDGEOTTO", 18), BattleEnd.WON)
        assertNull(s.enc("Route 4"), "Pidgeotto is Pidgey's line")
        assertEquals(ExtraKind.DUPE, s.ledger.areas.getValue("Route 4").extras.single().kind)
        // The area is still open for the next Pokemon.
        s.wildEncounter(foe(111, Sp.SPEAROW, "SPEAROW", 5), BattleEnd.WON)
        assertEquals("Spearow", s.enc("Route 4")!!.speciesName)
        // And a dupe caught anyway is kept and flagged.
        s.moveTo("Route 5")
        s.catchIt(foe(112, Sp.PIDGEOT, "PIDGEOT", 40), mon(112, Sp.PIDGEOT, "PIDGEOT", 40))
        assertTrue(s.ledger.roster.getValue(112L).violation)
        assertEquals(1, s.warnings(WarnKind.DUPE_CATCH).size)
    }

    @Test
    fun `with the dupes clause off the same encounter counts`() {
        val s = Sim(rules { it.copy(dupes = false) }).starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        s.moveTo("Route 4")
        s.wildEncounter(foe(110, Sp.PIDGEOTTO, "PIDGEOTTO", 18), BattleEnd.WON)
        assertEquals("Pidgeotto", s.enc("Route 4")!!.speciesName)
    }

    @Test
    fun `a line whose Pokemon all died stays a dupe unless the rules count only lines still owned`() {
        fun run(owned: Boolean): Sim {
            val s = Sim(rules { it.copy(dupesOwnedOnly = owned) }).starter()
            val pidgey = mon(100, Sp.PIDGEY, "PIDGEY", 3)
            s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), pidgey)
            // It faints in a fight, and the player boxes the body.
            s.party = listOf(s.party[0], pidgey.copy(hp = 0))
            s.poll()
            s.party = listOf(s.party[0])
            s.poll()
            assertFalse(s.ledger.roster.getValue(100L).alive)
            s.moveTo("Route 4")
            s.wildEncounter(foe(110, Sp.PIDGEY, "PIDGEY", 4), BattleEnd.WON)
            return s
        }
        assertNull(run(owned = false).enc("Route 4"), "caught this run: still a dupe")
        assertEquals("Pidgey", run(owned = true).enc("Route 4")!!.speciesName, "no Pidgey left: open again")
    }

    @Test
    fun `a shiny is a free extra under the shiny clause and leaves the area open`() {
        val s = Sim().starter()
        s.wildBattle(foe(100, Sp.PIDGEY, "PIDGEY", 3, shiny = true))
        assertNull(s.enc("Route 3"))
        assertEquals(ExtraKind.SHINY, s.ledger.areas.getValue("Route 3").extras.single().kind)
        s.party = s.party + mon(100, Sp.PIDGEY, "PIDGEY", 3, shiny = true)
        s.poll()
        s.finish(BattleEnd.CAUGHT)
        assertEquals(Origin.EXTRA, s.ledger.roster.getValue(100L).origin)
        assertFalse(s.ledger.roster.getValue(100L).violation)
        // The next Pokemon is still the area's first encounter.
        s.wildEncounter(foe(101, Sp.RATTATA, "RATTATA", 4), BattleEnd.WON)
        assertEquals("Rattata", s.enc("Route 3")!!.speciesName)
    }

    @Test
    fun `with the shiny clause off a shiny is the first encounter like any other`() {
        val s = Sim(rules { it.copy(shinyClause = false) }).starter()
        s.wildBattle(foe(100, Sp.PIDGEY, "PIDGEY", 3, shiny = true))
        assertEquals("Pidgey", s.enc("Route 3")!!.speciesName)
    }

    @Test
    fun `a set battle is free by default and counts when the rules say so`() {
        val free = Sim().starter()
        free.wildBattle(foe(100, 143, "SNORLAX", 30), Method.STATIC)
        assertNull(free.enc("Route 3"))
        assertEquals(ExtraKind.STATIC, free.ledger.areas.getValue("Route 3").extras.single().kind)
        free.party = free.party + mon(100, 143, "SNORLAX", 30)
        free.poll()
        free.finish(BattleEnd.CAUGHT)
        assertEquals(Origin.STATIC, free.ledger.roster.getValue(100L).origin)
        free.wildEncounter(foe(101, Sp.RATTATA), BattleEnd.WON)
        assertEquals("Rattata", free.enc("Route 3")!!.speciesName, "the static did not use the area")

        val counts = Sim(rules { it.copy(staticsCount = true) }).starter()
        counts.wildBattle(foe(100, 143, "SNORLAX", 30), Method.STATIC)
        assertEquals("Snorlax", counts.enc("Route 3")!!.speciesName)
    }

    @Test
    fun `a gift is free by default and uses the area where it was given when gifts count`() {
        val free = Sim().starter()
        free.moveTo("Celadon City")
        free.party = free.party + mon(50, Sp.EEVEE, "EEVEE", 25)
        free.idle()
        assertEquals(Origin.GIFT, free.ledger.roster.getValue(50L).origin)
        assertNull(free.enc("Celadon City"))

        val counts = Sim(rules { it.copy(giftsCount = true) }).starter()
        counts.moveTo("Celadon City")
        counts.party = counts.party + mon(50, Sp.EEVEE, "EEVEE", 25)
        counts.idle()
        val enc = assertNotNull(counts.enc("Celadon City"))
        assertEquals(50L, enc.monId)
        assertEquals(Outcome.CAUGHT, enc.outcome)
        // A second gift in the same place finds the area used and is simply free.
        counts.party = counts.party + mon(51, Sp.TREECKO, "TREECKO", 5)
        counts.idle()
        assertEquals(50L, counts.enc("Celadon City")!!.monId)
    }

    @Test
    fun `before the first Poke Ball nothing counts, and the same area is open once the rules begin`() {
        val s = Sim()
        s.balls = 0
        s.starter()
        assertFalse(s.ledger.meta.started)
        s.moveTo("Route 1")
        s.wildEncounter(foe(100, Sp.PIDGEY, "PIDGEY", 2), BattleEnd.WON)
        assertTrue(s.ledger.areas.isEmpty(), "Route 1 before any ball is free")
        s.balls = 5
        s.idle()
        assertTrue(s.ledger.meta.started)
        s.wildEncounter(foe(101, Sp.RATTATA, "RATTATA", 2), BattleEnd.WON)
        assertEquals("Rattata", s.enc("Route 1")!!.speciesName)
    }

    @Test
    fun `with slow start off the rules begin with the first Pokemon`() {
        val s = Sim(rules { it.copy(slowStart = false) })
        s.balls = 0
        s.starter()
        assertTrue(s.ledger.meta.started)
    }

    @Test
    fun `a Pokemon Tower ghost is never an encounter`() {
        val s = Sim().starter()
        s.inBattle = true; s.wild = true; s.ghost = true; s.enemy = null
        s.poll()
        s.finish(BattleEnd.RAN)
        assertTrue(s.ledger.areas.isEmpty())
    }

    @Test
    fun `fishing and surfing share the area unless water is its own area`() {
        val shared = Sim().starter()
        shared.wildEncounter(foe(100), BattleEnd.WON)
        shared.wildEncounter(foe(101, Sp.MAGIKARP, "MAGIKARP", 5, types = listOf(WATER)), BattleEnd.WON, Method.ROD)
        assertNull(shared.enc("Route 3|water"))
        assertEquals("Rattata", shared.enc("Route 3")!!.speciesName)

        val split = Sim(rules { it.copy(waterSeparate = true) }).starter()
        split.wildEncounter(foe(100), BattleEnd.WON)
        split.wildEncounter(foe(101, Sp.MAGIKARP, "MAGIKARP", 5, types = listOf(WATER)), BattleEnd.WON, Method.ROD)
        assertEquals("Rattata", split.enc("Route 3")!!.speciesName)
        assertEquals("Magikarp", split.enc("Route 3|water")!!.speciesName)
        assertEquals("Route 3 (water)", split.ledger.areas.getValue("Route 3|water").name)
    }

    @Test
    fun `a cave with several floors is one area`() {
        val s = Sim().starter()
        s.moveTo("Mt. Moon 1F", 114)
        s.wildEncounter(foe(100, 41, "ZUBAT", 8), BattleEnd.WON)
        s.moveTo("Mt. Moon B1F", 115)
        s.wildEncounter(foe(101, 74, "GEODUDE", 8), BattleEnd.WON)
        assertEquals(listOf("Mt. Moon"), s.ledger.areas.keys.toList())
        assertEquals("Zubat", s.enc("Mt. Moon")!!.speciesName)
    }

    @Test
    fun `Monotype skips encounters of the wrong type and the first one that fits counts`() {
        val s = Sim(rules(NuzlockePreset.MONOTYPE) { it.copy(monotypeType = WATER) }).starter()
        s.wildEncounter(foe(100, Sp.RATTATA, "RATTATA", 3, types = listOf(NORMAL)), BattleEnd.WON)
        assertNull(s.enc("Route 3"))
        assertEquals(ExtraKind.TYPE, s.ledger.areas.getValue("Route 3").extras.single().kind)
        s.wildEncounter(foe(101, Sp.MAGIKARP, "MAGIKARP", 5, types = listOf(WATER)), BattleEnd.WON, Method.ROD)
        assertEquals("Magikarp", s.enc("Route 3")!!.speciesName)
        // A wrong-type catch is kept and flagged.
        s.moveTo("Route 4")
        s.catchIt(foe(102, Sp.PIDGEY, "PIDGEY", 4), mon(102, Sp.PIDGEY, "PIDGEY", 4))
        assertTrue(s.ledger.roster.getValue(102L).violation)
        assertEquals(1, s.warnings(WarnKind.TYPE).size)
    }

    @Test
    fun `Wedlocke skips the gender the party has more of, and genderless Pokemon`() {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE)).starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, gender = Gender.MALE))
        // One male in the party: another male is skipped, a female counts.
        s.moveTo("Route 4")
        s.wildEncounter(foe(100, gender = Gender.MALE), BattleEnd.WON)
        assertNull(s.enc("Route 4"))
        assertEquals(ExtraKind.GENDER, s.ledger.areas.getValue("Route 4").extras.single().kind)
        s.wildEncounter(foe(101, Sp.SPEAROW, "SPEAROW", gender = Gender.FEMALE), BattleEnd.WON)
        assertEquals("Spearow", s.enc("Route 4")!!.speciesName)
        // Genderless can never be caught.
        s.moveTo("Route 5")
        s.wildEncounter(foe(102, 81, "MAGNEMITE", gender = null), BattleEnd.WON)
        assertNull(s.enc("Route 5"))
    }

    @Test
    fun `Wedlocke pairs a catch with a Pokemon of the other gender, and a death leaves the partner a widow`() {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE)).starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, gender = Gender.MALE))
        s.moveTo("Route 4")
        s.catchIt(foe(100, Sp.SPEAROW, "SPEAROW", gender = Gender.FEMALE), mon(100, Sp.SPEAROW, "SPEAROW", 3, gender = Gender.FEMALE))
        assertEquals(100L, s.ledger.roster.getValue(1L).partner)
        assertEquals(1L, s.ledger.roster.getValue(100L).partner)
        // The starter faints; the other Pokemon lives.
        s.party = listOf(s.party[0].copy(hp = 0), s.party[1])
        s.poll()
        assertFalse(s.ledger.roster.getValue(1L).alive)
        assertNull(s.ledger.roster.getValue(100L).partner)
        assertTrue(s.ledger.events.any { it.kind == "widow" })
    }

    @Test
    fun `an engine picked up from the saved ledger in the middle of a battle carries on with that battle`() {
        val s = Sim().starter()
        s.wildBattle(foe(100, Sp.PIDGEY, "PIDGEY", 3))
        val reloaded = assertNotNull(NuzlockeText.parse(NuzlockeText.format(s.ledger)))
        val s2 = Sim()
        s2.engine = NuzlockeEngine(reloaded)
        s2.party = s.party
        s2.inBattle = true; s2.enemy = foe(100, Sp.PIDGEY, "PIDGEY", 3)
        s2.poll()
        s2.finish(BattleEnd.WON)
        assertEquals(Outcome.FAINTED, reloaded.areas.getValue("Route 3").encounter!!.outcome)
        assertEquals("Pidgey", reloaded.areas.getValue("Route 3").encounter!!.speciesName)
        assertTrue(reloaded.areas.getValue("Route 3").extras.isEmpty(), "the same Pokemon must not register twice")
    }

    @Test
    fun `polls that change nothing leave the ledger alone`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        val rev = s.ledger.revision
        repeat(6) { assertFalse(s.poll()) }
        assertEquals(rev, s.ledger.revision)
    }

    @Test
    fun `a poll the tracker could not read is ignored`() {
        val s = Sim().starter()
        s.readable = false
        s.party = emptyList()
        assertFalse(s.poll())
        assertTrue(s.ledger.roster.getValue(1L).inParty, "an unreadable poll must not empty the party")
    }

    @Test
    fun `a party read that came up short does not box anybody or count as a whiteout`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3))
        // The game has two Pokemon; one slot failed to decode, and the one that did is at 0 HP.
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, hp = 0, nickname = "Shell"))
        s.partyCount = 2
        repeat(4) { s.poll() }
        assertTrue(s.ledger.roster.getValue(100L).inParty, "a slot that failed to read is not a box")
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        assertFalse(s.ledger.meta.whiteoutLatched)
    }

    private fun rules(tweak: (NuzlockeRules) -> NuzlockeRules) = com.ironmonone.tracker.nuzlocke.rules(NuzlockePreset.STANDARD, tweak)
}
