package com.ironmonone.patch

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Identification by header, for the kinds whose CRC is not pinned yet.
 *
 * Before this, identify() was CRC-only, so every HGSS, BW, B2W2 and Crystal
 * dump would have been rejected at Prepare as "Not a GBA ROM" - the kinds
 * existed, but no file could ever become one.
 */
class RomIdentityTest {

    private fun dsRom(title: String, code: String): ByteArray {
        val r = ByteArray(0x1000)
        title.forEachIndexed { i, c -> r[i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        return r
    }

    private fun gbRom(title: String, cgb: Int = 0xC0): ByteArray {
        val r = ByteArray(0x8000)
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = cgb.toByte()
        return r
    }

    @Test
    fun `a DS dump is identified by its header title`() {
        val hg = RomIdentity.identify(dsRom("POKEMON HG", "IPKE"))
        assertEquals(RomKind.HEARTGOLD_U, hg.kind)
        assertEquals("POKEMON HG IPKE", hg.headerLine)
        assertTrue("by header" in hg.summary, hg.summary)
        assertEquals(RomKind.SOULSILVER_U, RomIdentity.identify(dsRom("POKEMON SS", "IPGE")).kind)
        assertEquals(RomKind.BLACK2_U, RomIdentity.identify(dsRom("POKEMON B2", "IREO")).kind)
    }

    @Test
    fun `Black is not mistaken for Black 2 or the reverse`() {
        assertEquals(RomKind.BLACK_U, RomIdentity.identify(dsRom("POKEMON B", "IRBO")).kind)
        assertEquals(RomKind.BLACK2_U, RomIdentity.identify(dsRom("POKEMON B2", "IREO")).kind)
    }

    @Test
    fun `a DS file with a damaged header is refused, not handed to the core`() {
        val r = dsRom("POKEMON HG", "IPKE")
        r[0x15E] = (r[0x15E].toInt() xor 0x55).toByte()
        val id = RomIdentity.identify(r)
        assertNull(id.kind, "no header match on a damaged header")
        assertTrue(id.dsHeader != null && !id.dsHeader!!.checksumValid)
        assertTrue("damaged header" in id.summary, id.summary)
    }

    @Test
    fun `Crystal is identified by its Game Boy header`() {
        val r = RomIdentity.identify(gbRom("PM_CRYSTAL"))
        assertEquals(RomKind.CRYSTAL_U, r.kind)
        assertEquals("PM_CRYSTAL", r.headerLine)
        assertNotNull(r.gbHeader); assertTrue(r.gbHeader!!.colorOnly)
    }

    @Test
    fun `Gold and Silver are identified by their Game Boy headers, and their CRCs are the randomizer's`() {
        val g = RomIdentity.identify(gbRom("POKEMON_GLDAAUE", cgb = 0x80))
        assertEquals(RomKind.GOLD_U, g.kind)
        assertEquals("POKEMON_GLDAAUE", g.headerLine)
        assertEquals(RomKind.SILVER_U, RomIdentity.identify(gbRom("POKEMON_SLVAAXE", cgb = 0x80)).kind)
        assertEquals(0x6BDE3C3EL, RomKind.GOLD_U.expectedCrc)      // gen2_offsets.ini [Gold (U)]
        assertEquals(0x8AD48636L, RomKind.SILVER_U.expectedCrc)    // gen2_offsets.ini [Silver (U)]
        assertEquals("GSC", RomKind.GOLD_U.family)
    }

    @Test
    fun `Red, Blue and Yellow are identified by their headers, with the randomizer's CRCs`() {
        assertEquals(RomKind.RED_U, RomIdentity.identify(gbRom("POKEMON RED", cgb = 0x00)).kind)
        assertEquals(RomKind.BLUE_U, RomIdentity.identify(gbRom("POKEMON BLUE", cgb = 0x00)).kind)
        val y = RomIdentity.identify(gbRom("POKEMON YELLOW", cgb = 0x80))
        assertEquals(RomKind.YELLOW_U, y.kind)
        assertEquals("POKEMON YELLOW", y.headerLine)
        assertEquals(0x9F7FDD53L, RomKind.RED_U.expectedCrc)      // gen1_offsets.ini [Red (U)]
        assertEquals(0xD6DA8A1AL, RomKind.BLUE_U.expectedCrc)     // [Blue (U)]
        assertEquals(0x7D527D62L, RomKind.YELLOW_U.expectedCrc)   // [Yellow (U)]
        assertEquals("gbc", RomKind.RED_U.fileExtension, "the GBC platform's extension, whatever the dump was called")
    }

    @Test
    fun `an unknown game on a known console is named as such, not as not-a-ROM`() {
        val ds = RomIdentity.identify(dsRom("MARIO KART", "AMCE"))
        assertNull(ds.kind); assertTrue("DS ROM" in ds.summary, ds.summary)
        val gb = RomIdentity.identify(gbRom("TETRIS", cgb = 0))
        assertNull(gb.kind); assertTrue("Game Boy ROM" in gb.summary, gb.summary)
        val junk = RomIdentity.identify(ByteArray(0x2000))
        assertNull(junk.kind); assertTrue(junk.summary.startsWith("Not a"), junk.summary)
    }
}
