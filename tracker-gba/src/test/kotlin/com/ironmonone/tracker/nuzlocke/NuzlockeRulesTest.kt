package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The presets, their switches, and the words the screens show for them (2026-09-29). */
class NuzlockeRulesTest {

    @Test
    fun `Standard is the two core rules plus the common clauses, with gifts and statics free`() {
        val r = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)
        assertTrue(r.firstEncounter && r.faintIsDeath && r.whiteoutEndsRun)
        assertTrue(r.dupes && r.shinyClause && r.slowStart && r.nicknames)
        assertFalse(r.giftsCount); assertFalse(r.staticsCount)
        assertTrue(r.floorsMerged); assertFalse(r.waterSeparate); assertFalse(r.escapeClause)
        assertEquals(SafariRule.PER_ZONE, r.safari)
        assertFalse(r.levelCaps || r.noItemsInBattle || r.setStyle || r.capExtraBosses)
        assertFalse(r.wedlocke || r.genlocke)
        assertEquals(null, r.monotypeType)
    }

    @Test
    fun `Hardcore adds level caps, no items in battle and the Set style, and nothing else`() {
        val h = NuzlockeRules.forPreset(NuzlockePreset.HARDCORE)
        assertTrue(h.levelCaps && h.noItemsInBattle && h.setStyle)
        assertEquals(NuzlockeRules.forPreset(NuzlockePreset.STANDARD).copy(preset = NuzlockePreset.HARDCORE, levelCaps = true, noItemsInBattle = true, setStyle = true), h)
    }

    @Test
    fun `the variants each turn on their own switch`() {
        assertEquals(NuzlockePreset.RANDOMIZER, NuzlockeRules.forPreset(NuzlockePreset.RANDOMIZER).preset)
        assertEquals(WATER, NuzlockeRules.forPreset(NuzlockePreset.MONOTYPE, WATER).monotypeType)
        assertEquals("Water", NuzlockeRules.forPreset(NuzlockePreset.MONOTYPE, WATER).monotypeLabel)
        assertTrue(NuzlockeRules.forPreset(NuzlockePreset.WEDLOCKE).wedlocke)
        assertTrue(NuzlockeRules.forPreset(NuzlockePreset.GENLOCKE).genlocke)
        assertFalse(NuzlockeRules.forPreset(NuzlockePreset.STANDARD).wedlocke)
    }

    @Test
    fun `every switch of the rules is in the clause list, so no screen can forget one`() {
        val declared = NuzlockeRules::class.java.declaredFields
            .filter { it.type == java.lang.Boolean.TYPE && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }.toSet()
        assertEquals(declared, NuzlockeRules.CLAUSES.map { it.key }.toSet())
        assertEquals(NuzlockeRules.CLAUSES.size, NuzlockeRules.CLAUSES.map { it.key }.toSet().size, "a key appears twice")
    }

    @Test
    fun `each clause reads and sets its own field`() {
        val base = NuzlockeRules()
        for (c in NuzlockeRules.CLAUSES) {
            val flipped = c.set(base, !c.get(base))
            assertNotEquals(base, flipped, "${c.key} did not change the rules")
            assertEquals(!c.get(base), c.get(flipped), c.key)
            // Every other clause is unchanged.
            for (o in NuzlockeRules.CLAUSES) if (o.key != c.key) assertEquals(o.get(base), o.get(flipped), "${c.key} moved ${o.key}")
        }
    }

    @Test
    fun `the rules survive the ledger file, every switch flipped`() {
        var r = NuzlockeRules.forPreset(NuzlockePreset.MONOTYPE, WATER).copy(safari = SafariRule.ONE_AREA)
        for (c in NuzlockeRules.CLAUSES) r = c.set(r, !c.get(r))
        val back = NuzlockeRules.fromEntries(r.toEntries().toMap())
        assertEquals(r, back)
    }

    @Test
    fun `a missing, garbled or unknown entry keeps the default`() {
        val r = NuzlockeRules.fromEntries(mapOf("preset" to "nonsense", "dupes" to "maybe", "levelCaps" to "true", "safari" to "?", "monotypeType" to "99"))
        assertEquals(NuzlockePreset.STANDARD, r.preset)
        assertTrue(r.dupes, "a value that is not true or false is ignored")
        assertTrue(r.levelCaps)
        assertEquals(SafariRule.PER_ZONE, r.safari)
        assertEquals(null, r.monotypeType, "a type id no game has is dropped")
        assertEquals(NuzlockeRules(), NuzlockeRules.fromEntries(emptyMap()))
    }

    @Test
    fun `a preset says what was changed from it`() {
        assertTrue(NuzlockeRules.forPreset(NuzlockePreset.HARDCORE).changesFromPreset().isEmpty())
        val edited = NuzlockeRules.forPreset(NuzlockePreset.HARDCORE).copy(dupes = false, levelCaps = false)
        assertEquals(setOf("dupes", "levelCaps"), edited.changesFromPreset().map { it.key }.toSet())
    }

    @Test
    fun `the words the screens show follow the copy rules, no em dashes and no mention of how the work is done`() {
        val words = NuzlockeRules.CLAUSES.flatMap { listOf(it.title, it.detail) } +
            NuzlockePreset.entries.flatMap { listOf(it.label, it.blurb) } + SafariRule.entries.map { it.label } +
            ClauseGroup.entries.map { it.title }
        for (w in words) {
            assertFalse('—' in w || '–' in w, "dash in: $w")
            assertFalse(Regex("\\bAI\\b|artificial|automat", RegexOption.IGNORE_CASE).containsMatchIn(w), "the copy names how it is made: $w")
        }
        assertTrue(NuzlockePreset.entries.all { it.blurb.length in 20..200 })
    }

    @Test
    fun `presets and safari rules are found by their file key`() {
        for (p in NuzlockePreset.entries) assertEquals(p, NuzlockePreset.byKey(p.key))
        assertEquals(null, NuzlockePreset.byKey("nope"))
        assertEquals(SafariRule.PER_ZONE, SafariRule.byKey(null))
        assertEquals(SafariRule.ONE_AREA, SafariRule.byKey("one"))
    }
}
