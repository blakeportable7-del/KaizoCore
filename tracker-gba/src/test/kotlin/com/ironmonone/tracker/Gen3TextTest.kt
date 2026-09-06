package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The charmap covers the accented glyphs, not just ASCII.
 *
 * "Pok? Doll" reached a real screen: the hand-written table had no entry for
 * byte 0x1B, so the e-acute in the item name fell through to the '?' fallback
 * and the tracker confidently displayed a typo as if it were the item's name.
 */
class Gen3TextTest {

    /** Encodes an ASCII letter to its Gen III byte. */
    private fun up(c: Char) = (0xBB + (c - 'A')).toByte()
    private fun lo(c: Char) = (0xD5 + (c - 'a')).toByte()

    @Test
    fun `e-acute decodes instead of a question mark`() {
        // P o k [e-acute] _ D o l l
        val bytes = byteArrayOf(
            up('P'), lo('o'), lo('k'), 0x1B, 0x00,
            up('D'), lo('o'), lo('l'), lo('l'), 0xFF.toByte(),
        )
        val out = Gen3Text.decode(bytes)
        assertEquals("Pok" + Char(0xE9) + " Doll", out)
        assertEquals(false, out.contains('?'), "the accent fell back to '?'")
    }

    @Test
    fun `plain ascii still decodes`() {
        assertEquals("Abc", Gen3Text.decode(
            byteArrayOf(up('A'), lo('b'), lo('c'), 0xFF.toByte())))
    }

    @Test
    fun `an unmapped byte is still a question mark`() {
        // 0x7F has no entry in the reference table either.
        assertEquals("?", Gen3Text.decode(byteArrayOf(0x7F, 0xFF.toByte())))
    }
}
