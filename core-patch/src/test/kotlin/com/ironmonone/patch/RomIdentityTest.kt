package com.ironmonone.patch

import com.ironmonone.core.RomKind
import com.ironmonone.patch.RomIdentity.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Identification by header, for the kinds whose CRC is not pinned yet.
 *
 * Before this, identify() was CRC-only, so every HGSS, BW, B2W2 and Crystal
 * dump would have been rejected at Prepare as "Not a GBA ROM" - the kinds
 * existed, but no file could ever become one.
 *
 * And what a file the tracker cannot read is told (2026-09-30, UX audit P0-11): which case it is, what does
 * work, and that it still plays.
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

    /** A GBA file with a header that checks out, and a salt so two of them never share a checksum. */
    private fun gbaRom(title: String, code: String, version: Int = 0, size: Int = 0x400): ByteArray {
        val r = ByteArray(size)
        title.forEachIndexed { i, c -> r[GbaHeader.TITLE + i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[GbaHeader.CODE + i] = c.code.toByte() }
        "01".forEachIndexed { i, c -> r[GbaHeader.MAKER + i] = c.code.toByte() }
        r[GbaHeader.VERSION] = version.toByte()
        r[GbaHeader.CHECK] = GbaHeader.checksum(r).toByte()
        r[0x300] = (version + 1).toByte()
        return r
    }

    private val playsWithout = "It plays here without a tracker."

    @Test
    fun `a DS dump is identified by its header title`() {
        val hg = RomIdentity.identify(dsRom("POKEMON HG", "IPKE"))
        assertEquals(RomKind.HEARTGOLD_U, hg.kind)
        assertEquals("POKEMON HG IPKE", hg.headerLine)
        // HeartGold and SoulSilver are pinned from dumps now; Black still identifies by header.
        assertEquals(RomKind.SOULSILVER_U, RomIdentity.identify(dsRom("POKEMON SS", "IPGE")).kind)
        val bk = RomIdentity.identify(dsRom("POKEMON B", "IRBO"))
        assertEquals(RomKind.BLACK_U, bk.kind)
        assertEquals(Verdict.UNCHECKED, bk.verdict)
        assertEquals("This is Pokémon Black (U), a copy KaizoCore has not checked yet. $playsWithout", bk.summary)
        assertTrue("%08x".format(bk.crc) !in bk.summary, bk.summary)
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
        assertEquals(Verdict.DAMAGED, id.verdict)
        assertEquals("This DS file looks damaged (POKEMON HG), so it will not play. Copy the file to the phone again, then add it again.", id.summary)
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
        assertNull(ds.kind)
        assertEquals(Verdict.OTHER_GAME, ds.verdict)
        assertEquals("A DS game (MARIO KART), but not one the tracker reads. It still plays, without a tracker.", ds.summary)
        val gb = RomIdentity.identify(gbRom("TETRIS", cgb = 0))
        assertNull(gb.kind)
        assertEquals("A Game Boy game (TETRIS), but not one the tracker reads. It still plays, without a tracker.", gb.summary)
        val gba = RomIdentity.identify(gbaRom("MARIOKART", "AMKE"))
        assertEquals("A GBA game (MARIOKART), but not one the tracker reads. It still plays, without a tracker.", gba.summary)
        val junk = RomIdentity.identify(ByteArray(0x2000))
        assertNull(junk.kind)
        assertEquals(Verdict.NOT_A_GAME, junk.verdict)
        assertEquals("Not a Game Boy, Game Boy Advance or DS game file.", junk.summary)
    }

    // ---------------------------------------------------------------- P0-11: which case a file the tracker cannot read is

    @Test
    fun `a German FireRed is another language, and the words say what does work`() {
        val id = RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRD"))
        assertNull(id.kind)
        assertFalse(id.exact)
        assertEquals(Verdict.OTHER_LANGUAGE, id.verdict)
        assertEquals(
            "This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout",
            id.summary,
        )
    }

    @Test
    fun `the other languages are named from the letter the code ends in, and never guessed`() {
        fun say(code: String) = RomIdentity.identify(gbaRom("POKEMON EMER", code)).summary
        assertEquals("This is the Japanese Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPEJ"))
        assertEquals("This is the French Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPEF"))
        assertEquals("This is the Italian Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPEI"))
        assertEquals("This is the Spanish Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPES"))
        assertEquals("This is the Korean Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPEK"))
        // P is Europe: a release, not a language, so it is said as one.
        assertEquals("This is the European Emerald. The tracker reads the US English Emerald. $playsWithout", say("BPEP"))
        // A letter that names no single release is not turned into a language.
        assertEquals("This is Emerald from another release. The tracker reads the US English Emerald. $playsWithout", say("BPEC"))
    }

    @Test
    fun `a FireRed of the right language that the tracker does not read says which revision it is`() {
        // v1.1 is Rev 1 in a file name. Both revisions are pinned, so the file is the wrong copy of a known one.
        val v11 = RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRE", version = 1))
        assertEquals(Verdict.OTHER_VERSION, v11.verdict)
        assertEquals(
            "This is FireRed v1.1 (the file name may say Rev 1), but this file is not an exact copy. It may be trimmed or changed. $playsWithout",
            v11.summary,
        )
        val v10 = RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRE", version = 0))
        assertEquals("This is FireRed v1.0, but this file is not an exact copy. It may be trimmed or changed. $playsWithout", v10.summary)
        // A revision the tracker does not have: the revision is the reason, and what is read is said.
        val lg = RomIdentity.identify(gbaRom("POKEMON LEAF", "BPGE", version = 1))
        assertEquals(
            "This is LeafGreen v1.1 (the file name may say Rev 1). The tracker reads the US English LeafGreen, v1.0. $playsWithout",
            lg.summary,
        )
        // A version byte no release has: nothing is claimed about the revision.
        val odd = RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRE", version = 9))
        assertEquals(
            "This is FireRed, but not v1.0 or v1.1. It may be trimmed, changed or another revision. $playsWithout",
            odd.summary,
        )
    }

    @Test
    fun `a GBA Pokemon game bigger than any release is a changed game, whatever its language`() {
        val big = 16 * 1024 * 1024 + 1
        val hack = RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRE", size = big))
        assertEquals(Verdict.CHANGED, hack.verdict)
        assertEquals("A changed Pokémon FireRed (a ROM hack). It plays without the tracker.", hack.summary)
        assertEquals(Verdict.CHANGED, RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRD", size = big)).verdict, "expanded wins over another language")
        // Exactly a release's size is not evidence of a change.
        assertEquals(Verdict.OTHER_VERSION, RomIdentity.identify(gbaRom("POKEMON FIRE", "BPRE", size = 16 * 1024 * 1024)).verdict)
    }

    @Test
    fun `a DS game of another release keeps its title and is still not the US one`() {
        val de = RomIdentity.identify(dsRom("POKEMON HG", "IPKD"))
        assertNull(de.kind, "the title alone must not make a German cartridge the US game")
        assertEquals(Verdict.OTHER_LANGUAGE, de.verdict)
        assertEquals(
            "This is the German Pokémon HeartGold. The tracker reads the US English Pokémon HeartGold. $playsWithout",
            de.summary,
        )
        // Black has no pinned copy, so nothing is claimed about what the tracker reads.
        assertEquals(
            "This is the Japanese Pokémon Black. The tracker does not read Pokémon Black yet. $playsWithout",
            RomIdentity.identify(dsRom("POKEMON B", "IRBJ")).summary,
        )
    }

    @Test
    fun `a DS game of the tracker's release with another checksum is another version, not another language`() {
        val id = RomIdentity.identify(dsRom("POKEMON HG", "IPKE"))
        assertEquals(RomKind.HEARTGOLD_U, id.kind, "the header still says which game it is")
        assertFalse(id.exact, "a header is not a checksum")
        assertEquals(Verdict.OTHER_VERSION, id.verdict)
        assertEquals(
            "This is Pokémon HeartGold, but not the copy the tracker reads. It may be trimmed, changed or another revision. $playsWithout",
            id.summary,
        )
    }

    @Test
    fun `a Game Boy game the tracker does not read says a language is one of the things it may be`() {
        val red = RomIdentity.identify(gbRom("POKEMON RED", cgb = 0x00))
        assertEquals(Verdict.OTHER_VERSION, red.verdict)
        assertEquals(
            "This is Pokémon Red, but not the US English copy the tracker reads. It may be another language or revision, trimmed or changed. $playsWithout",
            red.summary,
        )
        // Gold and Silver do carry their release in the title.
        val gold = RomIdentity.identify(gbRom("POKEMON_GLDAAUD", cgb = 0x80))
        assertNull(gold.kind)
        assertEquals(Verdict.OTHER_LANGUAGE, gold.verdict)
        assertEquals(
            "This is the German Pokémon Gold. The tracker reads the US English Pokémon Gold. $playsWithout",
            gold.summary,
        )
    }

    @Test
    fun `exact means the pinned checksum and nothing else`() {
        val gba = GbaHeader("POKEMON EMER", "BPEE", "01", 0, true)
        val id = RomIdentity.Result(RomKind.EMERALD_U.expectedCrc, 16L * 1024 * 1024, gba, RomKind.EMERALD_U)
        assertTrue(id.exact)
        assertEquals(Verdict.EXACT, id.verdict)
        assertEquals("Pokémon Emerald (U)", id.summary)
        // The same kind reached by header only: no.
        assertFalse(id.copy(crc = 1L).exact)
        // A kind with no pinned checksum is never exact, not even by a checksum equal to the sentinel.
        assertFalse(RomIdentity.Result(RomKind.CRC_UNKNOWN, 1L, null, RomKind.BLACK_U).exact)
    }

    @Test
    fun `every sentence follows the copy rules`() {
        val big = 16 * 1024 * 1024 + 1
        val files = listOf(
            gbaRom("POKEMON FIRE", "BPRD"), gbaRom("POKEMON FIRE", "BPRE", version = 1), gbaRom("POKEMON FIRE", "BPRE", version = 9),
            gbaRom("POKEMON LEAF", "BPGE", version = 2), gbaRom("POKEMON FIRE", "BPRE", size = big), gbaRom("MARIOKART", "AMKE"),
            dsRom("POKEMON HG", "IPKD"), dsRom("POKEMON HG", "IPKE"), dsRom("POKEMON B", "IRBO"), dsRom("MARIO KART", "AMCE"),
            gbRom("POKEMON RED", cgb = 0), gbRom("POKEMON_SLVAAXS", cgb = 0x80), gbRom("TETRIS", cgb = 0), ByteArray(0x2000),
            dsRom("POKEMON HG", "IPKE").also { it[0x15E] = (it[0x15E].toInt() xor 0x55).toByte() },
        )
        for (f in files) {
            val s = RomIdentity.identify(f).summary
            assertTrue(s.isNotBlank() && s == s.trim() && s.endsWith("."), "a whole sentence: $s")
            assertFalse('—' in s || '–' in s, "no dash: $s")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s), s)
            assertFalse(Regex("(?i)\\bcrc\\b|checksum|dump|verified|[0-9a-f]{8}").containsMatchIn(s), "words a player cannot act on: $s")
        }
    }

    @Test
    fun `a known checksum skips the hashing and reads the same identity`() {
        val f = java.io.File.createTempFile("ident", ".gba")
        try {
            val bytes = gbaRom("POKEMON FIRE", "BPRD")
            f.writeBytes(bytes)
            val hashed = RomIdentity.identify(f)
            val told = RomIdentity.identifyWithCrc(f, hashed.crc)
            assertEquals(hashed, told)
            // The checksum it was told is the one it reports: nothing was read to find another.
            assertEquals(42L, RomIdentity.identifyWithCrc(f, 42L).crc)
        } finally { f.delete() }
    }

    /**
     * MaxDex 1.0 is a known build: Blake's FireRed 1.1 patched with Trip's MaxDex.bps is identified by its checksum,
     * exactly, though its header is still FireRed 1.1's. Read from IRONMON_ROMS (firered-maxdex.gba) or the vendor
     * folder; skipped without it.
     */
    @Test
    fun `the MaxDex build is identified exactly, by its checksum`() {
        val dir = System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms"
        val f = java.io.File(dir, "firered-maxdex.gba")
        if (!f.isFile) return println("skipped: firered-maxdex.gba not to hand")
        val id = RomIdentity.identify(f)
        assertEquals(RomKind.FIRERED_MAXDEX_10, id.kind)
        assertEquals(0x28C12926L, id.crc)
        assertTrue(id.exact)
    }
}
