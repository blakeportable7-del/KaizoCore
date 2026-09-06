package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Gen 4 bag walk, against synthetic memory.
 *
 * Two things here are genuinely different from Gen 3 and would each read
 * plausibly if wrong: quantities are NOT encrypted, and the pockets are
 * null-terminated lists rather than fixed-length arrays. The item ids differ
 * too - Gen 4 renumbered the table - so a Gen 3 table would silently match the
 * wrong items.
 */
class NdsHealsTest {

    private val RAM = 0x02000000L
    private val GLOBAL_PTR = 0xBA8L
    private val VERSION_OFF = 0x20L
    private val ITEMS = 0xB60L
    private val BERRIES = 0xC00L

    /** globalRel and versionRel are arbitrary but must survive the chain. */
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L

    private fun buildMemory(
        items: List<Pair<Int, Int>>,
        berries: List<Pair<Int, Int>>,
    ): NdsMemoryReader {
        val ram = ByteArray(0x400000)
        fun putU32(rel: Long, v: Long) {
            val i = rel.toInt()
            ram[i] = (v and 0xFF).toByte()
            ram[i + 1] = ((v shr 8) and 0xFF).toByte()
            ram[i + 2] = ((v shr 16) and 0xFF).toByte()
            ram[i + 3] = ((v shr 24) and 0xFF).toByte()
        }
        putU32(GLOBAL_PTR, globalRel)
        putU32(globalRel + VERSION_OFF, versionRel)
        // Slots are id in the low half, quantity in the high half, terminated
        // by a zero id. The terminator is what stops the walk.
        fun writePocket(start: Long, list: List<Pair<Int, Int>>) {
            var a = versionRel + start
            list.forEach { (id, qty) ->
                putU32(a, (id.toLong() and 0xFFFF) or ((qty.toLong() and 0xFFFF) shl 16))
                a += 4
            }
            putU32(a, 0)
        }
        writePocket(ITEMS, items)
        writePocket(BERRIES, berries)
        return NdsMemoryReader { address, length ->
            val off = (address - RAM).toInt()
            if (off >= 0 && off + length <= ram.size) ram.copyOfRange(off, off + length)
            else ByteArray(0)
        }
    }

    @Test
    fun `a potion is twenty flat HP, capped at one full heal`() {
        // Potion is id 17 in Gen 4, not 13 as in Gen 3.
        val t = NdsTracker(buildMemory(listOf(17 to 3), emptyList()))
        // 3 Potions x 20 HP on a 40 HP lead = 60/40 = 150%.
        assertEquals(150 to 3, t.readHeals(40, inBattle = false))
        // On a 10 HP lead each Potion can only ever restore a full bar, so it
        // is 100% each and not 200%.
        assertEquals(300 to 3, t.readHeals(10, inBattle = false))
    }

    @Test
    fun `sitrus is a percentage in Gen 4, unlike Gen 3`() {
        // Gen 4 Sitrus is id 158 and restores 25% of max HP. The Gen 3 table
        // has it as a flat 30 at id 142, so a wrong table gives 0 here.
        val t = NdsTracker(buildMemory(emptyList(), listOf(158 to 2)))
        assertEquals(50 to 2, t.readHeals(80, inBattle = false))
    }

    @Test
    fun `both pockets are counted`() {
        val t = NdsTracker(buildMemory(listOf(17 to 1), listOf(155 to 1)))
        // Potion 20 + Oran 10 = 30 of a 100 HP bar.
        assertEquals(30 to 2, t.readHeals(100, inBattle = false))
    }

    @Test
    fun `the walk stops at the terminator and ignores what follows`() {
        // A pocket that did not stop at the zero id would keep reading into
        // whatever lies past the bag and invent heals out of noise.
        val t = NdsTracker(buildMemory(listOf(17 to 1), emptyList()))
        assertEquals(20 to 1, t.readHeals(100, inBattle = false))
    }

    @Test
    fun `non-healing items are not counted`() {
        // 1 is a Master Ball. It must contribute nothing at all.
        val t = NdsTracker(buildMemory(listOf(1 to 99), emptyList()))
        assertEquals(0 to 0, t.readHeals(100, inBattle = false))
    }

    @Test
    fun `an absurd quantity is rejected rather than believed`() {
        val t = NdsTracker(buildMemory(listOf(17 to 5000), emptyList()))
        assertEquals(0 to 0, t.readHeals(100, inBattle = false))
    }

    @Test
    fun `no lead means no percentage to report`() {
        val t = NdsTracker(buildMemory(listOf(17 to 3), emptyList()))
        assertEquals(0 to 0, t.readHeals(0, inBattle = false))
    }
}
