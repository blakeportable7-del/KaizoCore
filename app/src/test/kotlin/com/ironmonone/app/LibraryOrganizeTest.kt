package com.ironmonone.app

import com.ironmonone.core.PatchFormat
import com.ironmonone.core.RomKind
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library's shelving and its patch matching (2026-09-05).
 *
 * The rule under test: a patch is offered for, and applied to, exactly the
 * file whose CRC it names, so a wrong patch on the wrong game is not an
 * outcome the UI can reach, and the store refuses it even if the UI did.
 */
class LibraryOrganizeTest {

    private fun dsRom(title: String, code: String, salt: Int = 0): ByteArray {
        val r = ByteArray(0x1000)
        title.forEachIndexed { i, c -> r[i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        r[0x800] = salt.toByte()
        return r
    }

    private fun ips(offset: Int, payload: String) = ByteArrayOutputStream().apply {
        write("PATCH".toByteArray()); write(offset shr 16); write((offset shr 8) and 0xFF); write(offset and 0xFF)
        write(0); write(payload.length); write(payload.toByteArray()); write("EOF".toByteArray())
    }.toByteArray()

    @Test
    fun `shelving follows identity, not names`() {
        val lib = LibraryStore(Files.createTempDirectory("lib").toFile())
        val hg = lib.import("whatever.nds", dsRom("POKEMON HG", "IPKE"))
        assertEquals(LibraryStore.Category.CLEAN, hg.category, "header match, CRC unpinned: clean but unverified")
        assertTrue(hg.unverified)
        assertTrue(hg.subtitle.contains("unverified"))
        val mk = lib.import("mk.nds", dsRom("MARIO KART", "AMCE"))
        assertEquals(LibraryStore.Category.OTHER, mk.category)
        val junk = lib.import("notes.txt", ByteArray(0x2000))
        assertEquals(LibraryStore.Category.OTHER, junk.category)
        assertNull(junk.platform)
    }

    @Test
    fun `a sidecar from an older rule is re-identified, not trusted`() {
        val dir = Files.createTempDirectory("lib").toFile()
        val lib = LibraryStore(dir)
        val bad = dsRom("POKEMON HG", "IPKE"); bad[0x15E] = (bad[0x15E].toInt() xor 0x55).toByte()
        val e = lib.import("stale.nds", bad)
        assertNull(e.platform, "a damaged DS file is not playable")
        // Forge the pre-v2 verdict an old build would have left behind.
        java.io.File(dir, "stale.nds.meta").writeText(listOf("%08x".format(e.crc), "heartgold-u", "NDS", "-", "-", "old verdict").joinToString("\n"))
        val again = lib.list().single()
        assertNull(again.platform, "the old sidecar was ignored and the file re-identified")
        assertTrue(again.summary.contains("damaged header"), again.summary)
    }

    @Test
    fun `an IPS is stored against the game the player picked and offered only there`() {
        val lib = LibraryStore(Files.createTempDirectory("lib").toFile())
        val hg = lib.import("POKEMON HG.nds", dsRom("POKEMON HG", "IPKE"))
        val ss = lib.import("POKEMON SS.nds", dsRom("POKEMON SS", "IPGE"))
        val p = lib.importPatch("Renegade.ips", ips(0x200, "HACK"), declaredFor = hg)
        assertEquals(PatchFormat.IPS, p.format)
        assertEquals(hg.crc, p.forCrc)
        assertEquals("POKEMON HG.nds", p.forName)
        assertEquals(listOf(p.name), lib.patchesFor(hg).map { it.name })
        assertTrue(lib.patchesFor(ss).isEmpty(), "never offered for SoulSilver")
        assertEquals(listOf(hg.name), lib.romsFor(p).map { it.name })
        // Even a direct call refuses the mismatch.
        assertFailsWith<com.ironmonone.patch.WrongSourceRom> { lib.apply(ss, p) }
        val out = lib.apply(hg, p)
        assertEquals("Renegade.nds", out.name)
        assertEquals(LibraryStore.Category.HACK, out.category)
        assertEquals("Renegade.ips on POKEMON HG.nds", out.subtitle)
        assertEquals(hg.name, out.baseName)
        assertTrue(lib.listPatches().single().name == p.name)
    }

    @Test
    fun `a BPS names its own source and ignores what the player picked`() {
        val lib = LibraryStore(Files.createTempDirectory("lib").toFile())
        val a = lib.import("a.nds", dsRom("POKEMON HG", "IPKE", 1))
        val b = lib.import("b.nds", dsRom("POKEMON HG", "IPKE", 2))
        // A BPS header with b's CRC as source and no actions: source size = target size = 0x1000.
        val bps = ByteArrayOutputStream().apply {
            write("BPS1".toByteArray())
            fun v(x: Long) { var n = x; while (true) { val c = (n and 0x7f).toInt(); n = n shr 7; if (n == 0L) { write(0x80 or c); break }; write(c); n-- } }
            v(0x1000); v(0x1000); v(0)
            // one source-read action covering 0x1000 bytes: action 0, length-1 = 0xFFF -> (0xFFF shl 2)
            v((0xFFFL shl 2) or 0)
            fun le(x: Long) { for (k in 0 until 4) write(((x shr (8 * k)) and 0xFF).toInt()) }
            le(b.crc); le(b.crc)
            val body = toByteArray(); le(com.ironmonone.patch.Crc32.of(body))
        }.toByteArray()
        val p = lib.importPatch("identity.bps", bps, declaredFor = a)
        assertEquals(b.crc, p.forCrc, "the BPS's own CRC wins over the pick")
        assertEquals(listOf(b.name), lib.romsFor(p).map { it.name })
        assertTrue(lib.patchesFor(a).isEmpty())
    }

    @Test
    fun `rename keeps the extension, moves the sidecar and follows the selection`() {
        val lib = LibraryStore(Files.createTempDirectory("lib").toFile())
        val hg = lib.import("POKEMON HG.nds", dsRom("POKEMON HG", "IPKE"))
        lib.selectLibrary(hg)
        val r = lib.rename(hg, "HeartGold clean")
        assertEquals("HeartGold clean.nds", r.name)
        assertEquals("HeartGold clean.nds", lib.selectedLibraryName())
        assertEquals(RomKind.HEARTGOLD_U, lib.list().single().kind, "identity survived without re-hashing")
        assertEquals(hg.crc, r.crc, "same file, same CRC, same saves")
        // A second rename onto a taken name gets a suffix, never an overwrite.
        val other = lib.import("x.nds", dsRom("POKEMON SS", "IPGE"))
        assertEquals("HeartGold clean (2).nds", lib.rename(other, "HeartGold clean").name)
    }

    @Test
    fun `suggestions come from what the file is`() {
        val lib = LibraryStore(Files.createTempDirectory("lib").toFile())
        val hg = lib.import("random name.nds", dsRom("POKEMON HG", "IPKE"))
        val s = lib.suggestions(hg)
        assertTrue(s.first().startsWith("Pokémon HeartGold"), s.toString())
        assertTrue(s.any { it.contains("POKEMON HG") }, "the header line is offered too: $s")
        assertTrue(LibraryStore.looksLikePatch("thing.UPS")); assertTrue(!LibraryStore.looksLikePatch("thing.gba"))
    }
}
