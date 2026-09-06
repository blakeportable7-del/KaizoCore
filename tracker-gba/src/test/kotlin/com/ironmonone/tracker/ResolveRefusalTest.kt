package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Identifying the loaded ROM must REFUSE what it does not recognise.
 *
 * The bug this pins: the vanilla branch read the 4-byte game code and did
 * `"BPR" -> FireRed else Emerald`. A core that has not finished loading its
 * ROM answers that read with zeros, so a FireRed game resolved to the
 * EMERALD map - and because the tracker is built once and cached, that wrong
 * map stuck for the whole session. Every party decode then came back garbage
 * and the panel said "TRACKER CANNOT READ THIS ROM" on a perfectly good ROM.
 */
class ResolveRefusalTest {

    /** A reader whose ROM region returns [code] at the header, [ver] as
     *  the revision byte, and zeros elsewhere. */
    private fun readerWith(code: String?, ver: Int = 0) =
        MemoryReader { address, length ->
            when {
                code != null && address == 0x080000ACL && length == 4 ->
                    code.toByteArray(Charsets.US_ASCII).copyOf(4)
                code != null && address == 0x080000BCL ->
                    byteArrayOf(ver.toByte())
                else -> ByteArray(length)   // zeros: nothing loaded yet
            }
        }

    @Test
    fun `a half-loaded ROM is refused, not called Emerald`() {
        // All-zero header: the core is still loading. Guessing here is what
        // produced a permanently wrong map.
        assertNull(GameMap.resolveOrNull(readerWith(null)))
    }

    @Test
    fun `an unknown game code is refused`() {
        assertNull(GameMap.resolveOrNull(readerWith("XYZ")))
    }

    @Test
    fun `FireRed and Emerald are each identified by their own code`() {
        assertEquals("FireRed (U) v1.0", GameMap.resolveOrNull(readerWith("BPR"))?.name)
        assertEquals("Emerald (U)", GameMap.resolveOrNull(readerWith("BPE"))?.name)
    }

    @Test
    fun `the FireRed revision comes from the version byte, not the code`() {
        // "BPRE" is the header code for BOTH revisions. v1.1 shifts three
        // ROM tables by +0x70, and since every RAM address is identical the
        // wrong map decodes fine and shows wrong stats with confidence -
        // it never trips the sanity check that catches a wrong map.
        assertEquals("FireRed (U) v1.0",
            GameMap.resolveOrNull(readerWith("BPR", 0))?.name)
        assertEquals("FireRed (U) v1.1",
            GameMap.resolveOrNull(readerWith("BPR", 1))?.name)
        assertEquals(0x08254784L,
            GameMap.resolveOrNull(readerWith("BPR", 0))?.baseStats)
        assertEquals(0x082547F4L,
            GameMap.resolveOrNull(readerWith("BPR", 1))?.baseStats)
    }

    @Test
    fun `Emerald is never the answer for a FireRed header`() {
        // The specific wrong outcome, stated as its own assertion.
        val m = GameMap.resolveOrNull(readerWith("BPR"))
        assertEquals(GameMap.FIRERED_U_V10.party, m?.party)
    }
}
