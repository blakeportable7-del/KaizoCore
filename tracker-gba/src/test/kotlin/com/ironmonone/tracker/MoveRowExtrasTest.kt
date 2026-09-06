package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Priority and contact are read out of gBattleMoves alongside power and type,
 * so a move popup can show them the way the reference InfoScreen does - and
 * from the live ROM rather than a table that would not know a randomized move.
 *
 * Layout pinned against the shipped Nat. Dex ROM on 2026-09-05: priority is
 * the signed byte at +7, contact is bit 0 of the flags byte at +8.
 */
class MoveRowExtrasTest {

    private val map = GameMap.EMERALD_U

    private open class Fake : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        fun put(addr: Long, data: ByteArray) { data.forEachIndexed { i, b -> bytes[addr + i] = b } }
        fun put(addr: Long, v: Long, len: Int) {
            for (i in 0 until len) bytes[addr + i] = ((v shr (8 * i)) and 0xFF).toByte()
        }
        open override fun read(address: Long, length: Int): ByteArray =
            ByteArray(length) { bytes[address + it] ?: 0 }
    }

    /** A gBattleMoves row: power, type, acc, pp, priority, flags. */
    private fun move(m: Fake, id: Int, power: Int, type: Int, priority: Int, contact: Boolean) {
        val e = ByteArray(12)
        e[1] = power.toByte(); e[2] = type.toByte(); e[3] = 100; e[4] = 20
        e[7] = priority.toByte(); e[8] = (if (contact) 1 else 0).toByte()
        m.put(map.battleMoves + id * 12L, e)
    }

    /** One party mon knowing [moves], vanilla layout, pid%24==0 (growth first). */
    private fun party(m: Fake, moves: IntArray) {
        val plain = ByteArray(48)
        plain[0] = 25            // species
        moves.forEachIndexed { i, mv -> plain[12 + i * 2] = mv.toByte(); plain[13 + i * 2] = (mv shr 8).toByte() }
        for (i in moves.indices) plain[20 + i] = 20
        val mon = ByteArray(100)
        mon.putU32(0, 0x18); mon.putU32(4, 0x18)
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4))
        mon[0x54] = 10; mon[0x56] = 30; mon[0x58] = 30
        m.put(map.partyCount, 1, 1)
        m.put(map.party, mon)
    }

    @Test
    fun `priority and contact come off the move table`() {
        val m = Fake()
        move(m, 98, power = 40, type = 0, priority = 1, contact = true)     // Quick Attack
        move(m, 86, power = 0, type = 13, priority = 0, contact = false)    // Thunder Wave
        move(m, 233, power = 70, type = 1, priority = -1, contact = true)   // Vital Throw
        party(m, intArrayOf(98, 86, 233))
        val rows = GbaTracker(m, map).read().party.firstOrNull()?.moveRows
        val r = assertNotNull(rows)
        assertEquals(listOf(1, 0, -1), r.map { it.priority })
        assertEquals(listOf(true, false, true), r.map { it.contact })
        assertEquals(listOf(0, 13, 1), r.map { it.type })
    }

    @Test
    fun `an unreadable table leaves them null rather than zero`() {
        // A reader that cannot see the move table at all (short read), which
        // is different from a table that happens to hold zeros.
        val m = object : Fake() {
            override fun read(address: Long, length: Int): ByteArray =
                if (address >= map.battleMoves && address < map.battleMoves + 400 * 12)
                    ByteArray(0)
                else super.read(address, length)
        }
        party(m, intArrayOf(98))
        val r = GbaTracker(m, map).read().party.first().moveRows.first()
        assertEquals(null, r.priority)
        assertEquals(null, r.contact)
        assertEquals(null, r.power)
    }
}
