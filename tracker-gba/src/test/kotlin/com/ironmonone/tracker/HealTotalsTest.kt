package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Heals line's numbers, as Program.recalcLeadPokemonHealingInfo works them out (Program.lua:1654-1683) and
 * TrackerScreen.lua:1267-1269 prints them, rounded (rc32 audit P2 #99). The panel truncated the percent and rebuilt the
 * HP from it, so each of these read lower than the PC tracker: 66% and 19 HP, 6% and 17 HP, 59 HP.
 */
class HealTotalsTest {
    private fun of(items: Map<Int, Int>, maxHp: Int) = HealTotals.of(items, maxHp) { HEAL_ITEMS[it] }

    @Test
    fun `the percent rounds, and the HP is added up per item`() {
        assertEquals(HealTotals(67, 20, 1), of(mapOf(13 to 1), 30), "one Potion on 30 HP")
        assertEquals(HealTotals(7, 20, 1), of(mapOf(13 to 1), 299), "one Potion on 299 HP")
        assertEquals(HealTotals(133, 60, 3), of(mapOf(13 to 3), 45), "three Potions on 45 HP")
    }

    @Test
    fun `a flat heal counts a full heal at most, a percentage heal its share, and nothing else counts`() {
        assertEquals(HealTotals(200, 60, 2), of(mapOf(21 to 2), 30), "two Hyper Potions on 30 HP: a full heal each")
        assertEquals(HealTotals(100, 30, 1), of(mapOf(20 to 1), 30), "a Max Potion")
        assertEquals(HealTotals(25, 8, 2), of(mapOf(143 to 2), 30), "two 12.5% berries on 30 HP: 7.5 HP, rounded")
        assertEquals(HealTotals(0, 0, 0), of(mapOf(4 to 5), 30), "Poke Balls heal nothing")
        assertEquals(HealTotals.NONE, of(mapOf(13 to 1), 0), "no Pokemon to heal")
        // The Game Boy trackers' tables carry whole amounts; the same rule applies (Ironmon-gen-tracker Program.lua:1291-1302).
        assertEquals(HealTotals(150, 60, 3), HealTotals.of(mapOf(18 to 3), 40) { id -> GbcTracker.HEALS[id]?.let { it.first.toDouble() to it.second } })
    }

    /**
     * RC35-NOTICED N #33: DataHelper.lua:378-380 caps what the Heals line prints at 9999%, 99999 HP and 99 items, and the
     * Game Boy trackers cap the percent and the count the same way (DataHelper.lua:331-332). The count was printed whole.
     */
    @Test
    fun `the Heals line holds at 99 items, 9999 percent and 99999 HP`() {
        val many = of(mapOf(13 to 99, 21 to 99), 30)     // 198 Potions and Hyper Potions on 30 HP: 16500%, 4950 HP
        assertEquals(99, many.count)
        assertEquals(9999, many.percent)
        assertEquals(HealTotals(9999, 4950, 99), many, "the HP is under its own cap")
        assertEquals(42, of(mapOf(13 to 42), 30).count, "below the cap the count is the count")
    }
}
