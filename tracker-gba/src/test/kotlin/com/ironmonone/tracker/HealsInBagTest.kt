package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HealsInBagScreen over a Ruby bag (unencrypted, fixed save block): three
 * Potions, two Antidotes, one Ultra Ball and a Leppa Berry. A lead missing
 * 30 HP makes the Potion helpful (20 * 2/3 <= 30); poisoned makes the
 * Antidote helpful; a move on its last point makes the Leppa Berry helpful.
 */
class HealsInBagTest {
    private val m = GameMap.RUBY_U
    private fun tracker(): GbaTracker {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> ram[a + i] = v.toByte() } }
        put(m.saveBlock1Fixed + m.bagItemsOffset, 13, 0, 3, 0, 14, 0, 2, 0)
        put(m.saveBlock1Fixed + m.bagBerriesOffset, 138, 0, 1, 0)
        put(m.saveBlock1Fixed + m.bagBallsOffset, 2, 0, 1, 0)
        return GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, m)
    }
    private fun lead(curHp: Int, status: String, pp: List<Int>) = TrackedMon(
        mon = PokemonDecoder.Mon(1, 10, "", 1, 0, 0, listOf(33, 45, 0, 0), pp, List(6) { 0 }, List(6) { 0 }, List(4) { 0 }, 0, 0, false, 0, curHp, 60, 0, 0, 0, 0, 0),
        speciesName = "BULBASAUR", moveNames = emptyList(), base = null, statusCondition = status,
    )

    @Test
    fun `categories, counts and the helpful rules follow the reference`() {
        val t = tracker()
        val rows = t.healsInBag(lead(30, "PSN", listOf(1, 20)))
        assertEquals(listOf("HP", "Status", "PP", "Balls"), rows.map { it.category })
        assertEquals(listOf(3, 2, 1, 1), rows.map { it.quantity })
        assertTrue(rows.all { it.helpful || it.category == "Balls" })
        val healthy = t.healsInBag(lead(60, "", listOf(20, 20)))
        assertFalse(healthy.any { it.helpful })
        assertEquals(mapOf(13 to 3, 14 to 2, 138 to 1, 2 to 1), t.readBag())
    }

    @Test
    fun `balls, stones and other items sort as the reference sorts them, by id after the heals`() {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> ram[a + i] = v.toByte() } }
        // Items: Nugget 110, Fire Stone 95, Escape Rope 85, Potion 13; Poke Balls: Great Ball 3.
        put(m.saveBlock1Fixed + m.bagItemsOffset, 110, 0, 1, 0, 95, 0, 1, 0, 85, 0, 1, 0, 13, 0, 1, 0)
        put(m.saveBlock1Fixed + m.bagBallsOffset, 3, 0, 1, 0)
        val t = GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, m)
        // calcSortValue gives all but the Potion 0, and Pager.defaultSort breaks the tie by id.
        assertEquals(listOf(13, 3, 85, 95, 110), t.healsInBag(null).map { it.id })
    }
}
