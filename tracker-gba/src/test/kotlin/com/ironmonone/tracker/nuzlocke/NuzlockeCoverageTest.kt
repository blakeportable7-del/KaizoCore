package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Team types (2026-10-06): weak, resist and immune counts per attacking type, and the types no move hits hard. */
class NuzlockeCoverageTest {

    // Gen 3 type ids: 0 Normal, 2 Flying, 4 Ground, 5 Rock, 7 Ghost, 10 Fire, 11 Water, 12 Grass, 13 Electric.
    private val team = listOf(
        NuzlockeCoverage.Member("Charizard", listOf(10, 2), listOf(10, 2)),
        NuzlockeCoverage.Member("Gyarados", listOf(11, 2), listOf(11)),
        NuzlockeCoverage.Member("Gengar", listOf(7, 3), listOf(7)),
    )

    @Test
    fun `counts per attacking type`() {
        val r = NuzlockeCoverage.report(team, NuzlockeSystem.GEN3)
        val electric = r.rows.single { it.type == 13 }
        assertEquals(listOf("Charizard", "Gyarados"), electric.weak)
        assertTrue(electric.danger)
        val ground = r.rows.single { it.type == 4 }
        assertEquals(listOf("Charizard", "Gyarados"), ground.immune)
        assertEquals(listOf("Gengar"), ground.weak)
        val normal = r.rows.single { it.type == 0 }
        assertEquals(listOf("Gengar"), normal.immune)
        assertFalse(normal.danger)
    }

    @Test
    fun `types no move hits hard`() {
        val r = NuzlockeCoverage.report(team, NuzlockeSystem.GEN3)
        assertTrue(r.movesKnown)
        // Fire, Flying, Water and Ghost moves: nothing of theirs hits Water, Dragon or Normal hard.
        assertTrue(11 in r.notCovered && 16 in r.notCovered && 0 in r.notCovered)
        assertFalse(12 in r.notCovered)
        assertFalse(NuzlockeCoverage.report(team.map { it.copy(moveTypes = emptyList()) }, NuzlockeSystem.GEN3).movesKnown)
    }

    @Test
    fun `Red, Blue and Yellow have no Dark or Steel and Ghost does nothing to Psychic`() {
        val r = NuzlockeCoverage.report(listOf(NuzlockeCoverage.Member("Alakazam", listOf(14))), NuzlockeSystem.GEN1)
        assertFalse(r.rows.any { it.type == 8 || it.type == 17 })
        assertEquals(listOf("Alakazam"), r.rows.single { it.type == 7 }.immune)
    }
}
