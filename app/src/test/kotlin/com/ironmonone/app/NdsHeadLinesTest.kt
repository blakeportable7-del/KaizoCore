package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The DS card's head lines against MainScreen.lua: your own Pokemon shows
 * "HP: cur/max" (lua:883); an opponent's HP row is hidden (lua:872,
 * pokemonHP.setVisibility(not isEnemy)), its item line is "Total seen" and its
 * ability line "Last level" (lua:600-601).
 */
class NdsHeadLinesTest {
    private fun mon(curHp: Int, maxHp: Int, item: Int = 0) =
        Gen4.decodeParty(Gen4.encodeParty(0x2345L, 25, 12, curHp, maxHp, listOf(84, 0, 0, 0), heldItem = item))!!

    @Test
    fun `an opponent's card has no HP row`() {
        val lines = ndsHeadLines(mon(7, 31), enemy = true, encounters = 3, lastLevel = null)
        assertNull(lines.hp, "the reference hides an opponent's HP")
        assertEquals("Total seen: 3", lines.item)
        assertEquals("Last level: ---", lines.ability)
        assertEquals("Last level: 11", ndsHeadLines(mon(7, 31), enemy = true, lastLevel = 11).ability)
    }

    @Test
    fun `your own card keeps HP, item and ability`() {
        val own = ndsHeadLines(mon(7, 31, item = 155), enemy = false, itemName = "Oran Berry", abilityName = "Static")
        assertEquals("7/31", own.hp)
        assertEquals("Oran Berry", own.item)
        assertEquals("Static", own.ability)
        assertEquals("---", ndsHeadLines(mon(7, 31), enemy = false, itemName = "-", abilityName = "Static").item)
    }
}
