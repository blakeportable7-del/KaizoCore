package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Gen 4 ability reveals, the DS reference's BattleHandlerGen4 rule: a battle
 * message names the abilities that could have produced it, and it is tracked
 * only when exactly one mon on the field has one of them.
 */
class NdsAbilityRevealTest {
    private fun mon(species: Int, ability: Int, name: String) = NdsTrackedMon(
        mon = Gen4.Mon(
            pid = 1, species = species, heldItem = 0, abilityId = ability, level = 20, curHp = 50, maxHp = 60,
            atk = 1, def = 1, spe = 1, spAtk = 1, spDef = 1, moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0),
            ppUps = listOf(0, 0, 0, 0), ivs = List(6) { 0 }, shiny = false, nature = 0, isEgg = false,
        ),
        speciesName = "#$species", info = null, abilityName = name, itemName = "-", moves = emptyList(),
    )

    private val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) })

    @Test
    fun `Static's message reveals the one mon with Static`() {
        val player = mon(25, 9, "Static")
        val enemy = mon(52, 7, "Limber")
        assertEquals(25 to "Static", t.resolveAbilityMsg(31, player, enemy))
    }

    @Test
    fun `a message both sides could have produced reveals nothing`() {
        assertNull(t.resolveAbilityMsg(31, mon(25, 9, "Static"), mon(125, 9, "Static")))
    }

    @Test
    fun `a message that names no ability reveals nothing`() {
        assertNull(t.resolveAbilityMsg(9999, mon(25, 9, "Static"), mon(52, 7, "Limber")))
    }
}
