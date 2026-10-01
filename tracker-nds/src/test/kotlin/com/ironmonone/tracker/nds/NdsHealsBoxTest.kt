package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The DS heals box against Program.lua and MainScreen.lua: "Heals:" rounded as
 * calculateHealPercent rounds, raw HP with "Bag heals show HP instead",
 * "Status items:", and the lists a tap shows.
 */
class NdsHealsBoxTest {
    @Test
    fun `the heals total is rounded, not cut off`() {
        // Two Oran Berries on 30 HP: 66.67%, math.floor(x + 0.5) = 67 (the old read cut it to 66).
        assertEquals(67 to 2, NdsHeals.totals(mapOf(155 to 2), 30, showHp = false))
        assertEquals("Heals: 67% (2)", NdsHeals.healsLine(mapOf(155 to 2), 30, showHp = false))
        assertEquals(0 to 0, NdsHeals.totals(mapOf(155 to 2), 0, showHp = false), "no Pokemon, nothing to measure")
    }

    @Test
    fun `with HP instead a flat heal counts whole and a percentage heal is its share`() {
        // Three Potions on a 10 HP Pokemon: 300% (each capped at a full bar), or 60 HP uncapped.
        assertEquals(300 to 3, NdsHeals.totals(mapOf(17 to 3), 10, showHp = false))
        assertEquals("Heals: 60 HP (3)", NdsHeals.healsLine(mapOf(17 to 3), 10, showHp = true))
        // Two Sitrus Berries on 80 HP: 25% each, 40 HP.
        assertEquals(50 to 2, NdsHeals.totals(mapOf(158 to 2), 80, showHp = false))
        assertEquals(40 to 2, NdsHeals.totals(mapOf(158 to 2), 80, showHp = true))
    }

    @Test
    fun `status items are counted and listed, Full Restore on both lists`() {
        val status = mapOf(18 to 2, 27 to 1, 23 to 1)
        assertEquals("Status items: 4", NdsHeals.statusLine(status))
        assertEquals(listOf("1 Full Restore (All)", "1 Full Heal (All)", "2 Antidotes (Poison)"), NdsHeals.statusList(status))
        assertEquals("Status items: 0", NdsHeals.statusLine(emptyMap()))
    }

    @Test
    fun `the healing list in the reference's order and plurals`() {
        val bag = mapOf(155 to 2, 17 to 3, 158 to 1, 43 to 2)
        assertEquals(listOf("1 Sitrus Berry (25%)", "2 Berry Juices (20 HP)", "3 Potions (20 HP)", "2 Oran Berries (10 HP)"), NdsHeals.healingList(bag))
        assertEquals("You currently do not have any healing items.", NdsHeals.emptyText("Healing"))
        assertEquals("You currently do not have any status items.", NdsHeals.emptyText("Status"))
    }

    @Test
    fun `the bag walk keeps healing and status items apart`() {
        val ram = ByteArray(0x400000)
        fun putU32(rel: Long, v: Long) { val i = rel.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
        val globalRel = 0x100000L; val versionRel = 0x180000L
        putU32(0xBA8L, globalRel); putU32(globalRel + 0x20, versionRel)
        var a = versionRel + NdsGameMap.PLATINUM.itemStartNoBattle
        for ((id, q) in listOf(17 to 3, 18 to 2, 23 to 1, 1 to 5)) { putU32(a, id.toLong() or (q.toLong() shl 16)); a += 4 }
        a = versionRel + NdsGameMap.PLATINUM.berryBagStart
        for ((id, q) in listOf(155 to 2, 151 to 1)) { putU32(a, id.toLong() or (q.toLong() shl 16)); a += 4 }
        val t = NdsTracker({ addr, len ->
            val off = (addr - 0x02000000L).toInt()
            if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
        })
        val (healing, status) = t.readBag(inBattle = false)
        assertEquals(mapOf(17 to 3, 23 to 1, 155 to 2), healing)
        assertEquals(mapOf(18 to 2, 23 to 1, 151 to 1), status, "the Master Ball is neither")
    }
}
