package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ledger as a file: everything comes back, and a file that is old, newer or damaged loads safely (2026-09-29). */
class NuzlockeLedgerTextTest {

    /** A run with every kind of record in it. */
    private fun richRun(): Sim {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE) { it.copy(monotypeType = WATER, safari = SafariRule.ONE_AREA, dupesOwnedOnly = true) })
        s.ledger.meta.genlockeId = "gl-1"; s.ledger.meta.leg = 2; s.ledger.meta.carriedFrom = "nz-old"
        s.ledger.meta.heirsIn += Heir(Sp.TREECKO, "Treecko", "Sprout", 50, Gender.FEMALE, true)
        s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, gender = Gender.MALE, nickname = "Shell"))
        s.catchIt(foe(100, Sp.MAGIKARP, "MAGIKARP", 5, gender = Gender.FEMALE, types = listOf(WATER)), mon(100, Sp.MAGIKARP, "MAGIKARP", 5, gender = Gender.FEMALE, nickname = "Splash", types = listOf(WATER)))
        s.moveTo("Route 4")
        s.wildEncounter(foe(101, Sp.RATTATA, "RATTATA", 3), BattleEnd.WON)            // a wrong-type skip
        s.wildBattle(foe(102, Sp.MAGIKARP, "MAGIKARP", 6, types = listOf(WATER), gender = Gender.MALE, shiny = true), Method.ROD)
        s.party = s.party + mon(102, Sp.MAGIKARP, "MAGIKARP", 6, gender = Gender.MALE, nickname = "Glint", shiny = true, types = listOf(WATER))
        s.poll(); s.finish(BattleEnd.CAUGHT)
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0), s.party[2])
        s.badges = 1
        s.poll()
        s.ledger.meta.heirsOut += Heir(Sp.SQUIRTLE, "Squirtle", "Shell", 5, Gender.MALE, false)
        val e = NuzlockeEdits(s.ledger)
        e.addNote("a note", s.now)
        e.setEncounter(AreaKey("Route 5", "Route 5"), Sp.PIDGEY, "PIDGEY", 4, Outcome.FLED, s.now)
        s.ledger.warn("w-open", s.now, WarnKind.OTHER, "still open")
        s.ledger.warn("w-done", s.now, WarnKind.CAP, "already dismissed")
        s.ledger.warnings.last().dismissed = true
        return s
    }

    @Test
    fun `everything in a run comes back out of its file`() {
        val s = richRun()
        assertTrue(s.ledger.areas.isNotEmpty() && s.ledger.roster.isNotEmpty() && s.ledger.events.isNotEmpty())
        assertTrue(s.ledger.graveyard.isNotEmpty() && s.ledger.warnings.isNotEmpty() && s.ledger.notes.isNotEmpty())
        val text = NuzlockeText.format(s.ledger)
        val back = assertNotNull(NuzlockeText.parse(text))
        assertEquals(text, NuzlockeText.format(back), "format(parse(format(x))) differs from format(x)")
        assertEquals(s.ledger.meta.rules, back.meta.rules)
        assertEquals(s.ledger.areas.keys.toList(), back.areas.keys.toList(), "areas keep their order")
        assertEquals(s.ledger.roster.keys.toList(), back.roster.keys.toList())
        val dead = back.graveyard.single()
        assertEquals(s.ledger.graveyard.single().death!!.cause, dead.death!!.cause)
        assertEquals("gl-1", back.meta.genlockeId); assertEquals(2, back.meta.leg); assertEquals("nz-old", back.meta.carriedFrom)
        assertEquals(listOf("Treecko"), back.meta.heirsIn.map { it.speciesName })
        assertEquals(listOf("Squirtle"), back.meta.heirsOut.map { it.speciesName })
        assertEquals(s.ledger.warnings.map { it.dismissed }, back.warnings.map { it.dismissed })
        assertTrue(back.meta.started && back.meta.partySeen)
        val enc = back.areas.getValue("Route 3").encounter!!
        assertEquals(Outcome.CAUGHT, enc.outcome); assertEquals(100L, enc.monId)
        assertTrue(back.areas.getValue("Route 5").encounter!!.manual)
        assertEquals(listOf(ExtraKind.TYPE), back.areas.getValue("Route 4").extras.map { it.kind }.filter { it == ExtraKind.TYPE })
    }

    @Test
    fun `text with tabs and line breaks in it cannot break the file`() {
        val s = Sim().starter()
        s.ledger.warn("x", 5, WarnKind.OTHER, "one\ttwo\nthree\r\nfour")
        s.ledger.event(6, "note", "a\tb", manual = true)
        val back = assertNotNull(NuzlockeText.parse(NuzlockeText.format(s.ledger)))
        assertEquals("one two three  four", back.warnings.single { it.id == "x" }.text)
        assertEquals("a b", back.events.last().text)
    }

    @Test
    fun `a file that is not a ledger is refused, not turned into an empty run`() {
        assertNull(NuzlockeText.parse(""))
        assertNull(NuzlockeText.parse("hello\nworld\n"))
        assertNull(NuzlockeText.parse("KAIZOCORE-NUZLOCKE\t1\n"), "the magic line with no run in it")
        assertNull(NuzlockeText.parse("KAIZOCORE-NUZLOCKE\tone\nrun\tid\tb\tg\t1\tactive\t0\t\n"), "a version that is not a number")
        assertNull(NuzlockeText.parse("run\tid\tb\tg\t1\tactive\t0\t\n"), "no magic line")
        assertNull(NuzlockeText.parse("KAIZOCORE-NUZLOCKE\t1\nrun\t\tb\tg\t1\tactive\t0\t\n"), "a run with no id")
        assertNull(NuzlockeText.header(emptySequence()))
    }

    @Test
    fun `lines that are cut off, garbled or unknown are skipped and the rest loads`() {
        val good = NuzlockeText.format(richRun().ledger).lines().filter { it.isNotEmpty() }
        val damaged = buildList {
            addAll(good)
            add("mon\t7\tnotanumber")                       // cut off
            add("enc\tRoute 9\tx\tx\tx\tx\tx\tx\tx\tx\tx\t\t0")  // fields that are not numbers
            add("death\t99999\t1\t2\ta\tb\t0\t0")           // a death for nobody
            add("evt\tabc\tk\t0\t\ttext")                   // a time that is not a number
            add("somethingnew\t1\t2\t3")                    // a line from a later build
            add("\t\t\t")
            add("warn")
        }.joinToString("\n")
        val back = assertNotNull(NuzlockeText.parse(damaged))
        assertEquals(NuzlockeText.format(NuzlockeText.parse(good.joinToString("\n"))!!), NuzlockeText.format(back))
    }

    @Test
    fun `a file cut off part way through loads what was written`() {
        val text = NuzlockeText.format(richRun().ledger)
        // Cut in the middle of the first event line: the areas and the roster before it are whole.
        val cut = text.indexOf("\nevt\t") + 12
        val back = assertNotNull(NuzlockeText.parse(text.substring(0, cut)))
        assertEquals("nz-test", back.meta.id)
        assertEquals(richRun().ledger.roster.keys.toList(), back.roster.keys.toList())
        assertEquals(richRun().ledger.areas.keys.toList(), back.areas.keys.toList())
        // The cut line is read as far as it goes and its text is short, never a wrong record.
        assertTrue(back.events.size <= 1)
        for (m in back.roster.values) assertTrue(m.speciesName.isNotEmpty() && m.level > 0)
    }

    @Test
    fun `a file from a newer build with extra lines and a higher version still opens`() {
        val text = NuzlockeText.format(richRun().ledger).replaceFirst("KAIZOCORE-NUZLOCKE\t1", "KAIZOCORE-NUZLOCKE\t7") + "future\tthing\n"
        val back = assertNotNull(NuzlockeText.parse(text))
        assertEquals(7, NuzlockeText.header(text.lineSequence())!!.version)
        assertTrue(back.roster.isNotEmpty())
    }

    @Test
    fun `a Windows file with CRLF line ends loads the same`() {
        val text = NuzlockeText.format(richRun().ledger)
        val crlf = assertNotNull(NuzlockeText.parse(text.replace("\n", "\r\n")))
        assertEquals(text, NuzlockeText.format(crlf))
    }

    @Test
    fun `a Pokemon with a death line is dead even if its own flag says alive`() {
        val s = richRun()
        val text = NuzlockeText.format(s.ledger)
        val deadId = s.ledger.graveyard.single().id
        val tweaked = text.lines().joinToString("\n") { l ->
            if (l.startsWith("mon\t$deadId\t")) l.split('\t').toMutableList().also { it[12] = "1" }.joinToString("\t") else l
        }
        assertFalse(NuzlockeText.parse(tweaked)!!.roster.getValue(deadId).alive)
    }

    @Test
    fun `the header reads from the first lines alone`() {
        val s = richRun()
        val h = assertNotNull(NuzlockeText.header(NuzlockeText.format(s.ledger).lineSequence().take(40)))
        assertEquals("nz-test", h.id)
        assertEquals("lib-test", h.bind)
        assertEquals(NuzlockePreset.WEDLOCKE, h.preset)
        assertEquals(RunStatus.ACTIVE, h.status)
        assertEquals(1_000L, h.startedAt)
        assertEquals(NuzlockeText.VERSION, h.version)
    }

    @Test
    fun `a run that ended keeps how it ended`() {
        val s = Sim().starter()
        NuzlockeEdits(s.ledger).endRun("Gave up", 55)
        val back = NuzlockeText.parse(NuzlockeText.format(s.ledger))!!
        assertEquals(RunStatus.OVER, back.meta.status)
        assertEquals("Gave up", back.meta.endReason)
        assertEquals(55L, back.meta.endedAt)
    }

    @Test
    fun `an engine carries on from a reloaded ledger without taking the party in twice`() {
        val s = Sim().starter()
        s.catchIt(foe(100), mon(100))
        val reloaded = NuzlockeText.parse(NuzlockeText.format(s.ledger))!!
        val s2 = Sim()
        s2.engine = NuzlockeEngine(reloaded)
        s2.party = s.party
        s2.idle(3)
        assertEquals(s.ledger.roster.size, reloaded.roster.size)
        assertEquals(1, reloaded.events.count { it.kind == "starter" })
    }
}
