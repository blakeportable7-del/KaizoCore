package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P1: in Red, Blue and Yellow a blackout never ended the run. pokered sets wIsInBattle back to 0 and heals
 * the party in one routine, so "battle over, lead at 0 HP" was never there to read. The lost-battle mark (0xFF) is.
 */
class GbGameOverTest {
    private fun mon(hp: Int, max: Int = 40) = TrackedMon(
        PokemonDecoder.Mon(
            pid = 1, level = 12, nickname = "", species = 4, heldItem = 0, friendship = 70,
            moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 },
            ppUps = listOf(0, 0, 0, 0), abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = hp, maxHp = max,
            atk = 1, def = 1, spe = 1, spAtk = 1, spDef = 1,
        ), "CHARMANDER", emptyList(), null,
    )

    @Test
    fun `a Gen 1 blackout ends the run while the lost-battle mark is up`() {
        val wiped = listOf(mon(0), mon(0))
        assertTrue(GbGameOver.lost(GbGameOver.LOST_BATTLE, wiped))
        assertTrue(GbGameOver.lost(GbGameOver.LOST_BATTLE, wiped, LossCondition.ENTIRE_PARTY))
        // After HealParty the byte is 0 and everyone is healed: no second loss, and none before this fix.
        assertFalse(GbGameOver.lost(0, listOf(mon(40), mon(40))))
    }

    @Test
    fun `the mark alone is not a loss`() {
        assertFalse(GbGameOver.lost(GbGameOver.LOST_BATTLE, listOf(mon(0), mon(12))), "someone still standing")
        assertFalse(GbGameOver.lost(GbGameOver.LOST_BATTLE, emptyList()), "power-on: no party yet")
        // The references' own rule, unchanged: the lead at 0 HP once the battle byte reads 0; never mid-battle.
        assertTrue(GbGameOver.lost(0, listOf(mon(0), mon(30))))
        assertFalse(GbGameOver.lost(1, listOf(mon(0), mon(30))))
        assertFalse(GbGameOver.lost(2, listOf(mon(0), mon(0))))
    }
}
