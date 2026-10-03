package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Game Boy names as the Nuzlocke ledger reads them. rc32 audit P3 #107: the naming screens offer the multiply sign ($F1)
 * in both generations and Gen 1's period as <DOT> ($F2) (pokered data/text/alphabets.asm:5-6, pokecrystal
 * data/text/name_input_chars.asm:7), and a name with either was cut short at it.
 */
class GbTextTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `a name runs to its terminator, the naming screens' marks included`() {
        assertEquals("DR.X", GbText.decode(bytes(0x83, 0x91, 0xF2, 0x97, 0x50)), "Gen 1's period is <DOT>")
        assertEquals("xA", GbText.decode(bytes(0xF1, 0x80, 0x50)), "a name that begins with the multiply sign")
        assertEquals("MR.MIME", GbText.decode(bytes(0x8C, 0x91, 0xE8, 0x8C, 0x88, 0x8C, 0x84, 0x50)), "Gen 2's period")
        assertEquals("NIDORAN", GbText.decode(bytes(0x8D, 0x88, 0x83, 0x8E, 0x91, 0x80, 0x8D, 0xEF, 0x50)), "the gender signs are dropped")
    }

    @Test
    fun `a name stops at eleven bytes and at a byte that is not text`() {
        assertEquals("AAAAAAAAAAA", GbText.decode(ByteArray(20) { 0x80.toByte() }))
        assertEquals("AB", GbText.decode(bytes(0x80, 0x81, 0x00, 0x82, 0x50)))
    }
}
