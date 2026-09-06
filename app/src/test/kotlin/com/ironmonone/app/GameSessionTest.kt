package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session model that untied Play from "the current run" (2026-09-05).
 *
 * Two things must hold or an existing install loses something: the run
 * session's save paths are byte-for-byte the paths the app used before
 * sessions existed, and a library file is tracked only on a CRC match, so a
 * hack never gets a tracker reading garbage against its memory.
 */
class GameSessionTest {

    private val filesDir = File("/data/app/files")
    private val prep = File(filesDir, "prep")

    @Test
    fun `the run session's save paths are exactly the pre-session paths`() {
        val run = GameSession.forRun(File(prep, "runs/current.gba"), RomKind.FIRERED_U_V11)
        assertTrue(run.isRun); assertTrue(run.tracked)
        assertEquals(File(filesDir, "saves/state2.bin"), SessionPaths.slot(filesDir, run, 2))
        assertEquals(File(filesDir, "saves/state2.id"), SessionPaths.slotStamp(filesDir, run, 2))
        assertEquals(File(filesDir, "saves/firered-u-v11.srm".replace("firered-u-v11", RomKind.FIRERED_U_V11.id)),
            SessionPaths.sram(filesDir, run, RomKind.FIRERED_U_V11.id))
        assertEquals(File(prep, "marks.txt"), SessionPaths.marks(prep, run))
    }

    @Test
    fun `a library session's saves never share a path with the run's`() {
        val dir = Files.createTempDirectory("lib").toFile()
        val lib = LibraryStore(dir)
        val e = lib.import("Mario Kart DS.nds", dsRom("MARIO KART", "AMCE"))
        val s = GameSession.forLibrary(e)
        assertNotNull(s)
        assertFalse(s.isRun); assertFalse(s.tracked)
        assertEquals(Platform.NDS, s.platform)
        val run = GameSession.forRun(File(prep, "runs/current.nds"), RomKind.PLATINUM_U)
        for (n in 1..3) {
            assertTrue(SessionPaths.slot(filesDir, s, n) != SessionPaths.slot(filesDir, run, n))
            assertTrue(SessionPaths.slot(filesDir, s, n).path.contains("saves${File.separator}lib${File.separator}"))
        }
        assertTrue(SessionPaths.marks(prep, s) != SessionPaths.marks(prep, run))
    }

    @Test
    fun `tracked only on a CRC match, never on a header`() {
        val fr = RomKind.FIRERED_U_V11
        assertEquals(fr, GameSession.trackerKind(fr, fr.expectedCrc))
        assertNull(GameSession.trackerKind(fr, fr.expectedCrc xor 1L), "a modified FireRed is a hack")
        // A kind whose CRC is not pinned identifies by header and must not track.
        assertEquals(RomKind.CRC_UNKNOWN, RomKind.HEARTGOLD_U.expectedCrc)
        assertNull(GameSession.trackerKind(RomKind.HEARTGOLD_U, 0x12345678L))
        assertNull(GameSession.trackerKind(null, 0L))
    }

    @Test
    fun `the library copies, lists, selects and deletes`() {
        val dir = Files.createTempDirectory("lib").toFile()
        val lib = LibraryStore(dir)
        assertTrue(lib.list().isEmpty())
        val a = lib.import("POKEMON HG.nds", dsRom("POKEMON HG", "IPKE"))
        val b = lib.import("POKEMON HG.nds", dsRom("POKEMON HG", "IPKE"))
        assertEquals("POKEMON HG.nds", a.name)
        assertEquals("POKEMON HG (2).nds", b.name, "a second import never overwrites")
        assertEquals(RomKind.HEARTGOLD_U, a.kind, "identified by header")
        assertNull(GameSession.forLibrary(a)?.kind, "but not tracked: CRC not pinned")
        assertEquals(setOf(a.name, b.name), lib.list().map { it.name }.toSet())

        assertNull(lib.selectedLibraryName())
        lib.selectLibrary(b)
        assertEquals(b.name, lib.selectedLibraryName())
        lib.delete(b)
        assertNull(lib.selectedLibraryName(), "deleting the selected entry falls back to the run")
        assertEquals(listOf(a.name), lib.list().map { it.name })

        // A lost sidecar is rebuilt from the file, not reported as an error.
        File(dir, a.name + ".meta").delete()
        assertEquals(RomKind.HEARTGOLD_U, lib.list().single().kind)
    }

    @Test
    fun `a file with no console is not playable`() {
        val dir = Files.createTempDirectory("lib").toFile()
        val e = LibraryStore(dir).import("notes.txt", ByteArray(0x2000))
        assertNull(e.platform)
        assertNull(GameSession.forLibrary(e))
    }

    @Test
    fun `patching a library entry stores the result as a new entry, base untouched`() {
        val dir = Files.createTempDirectory("lib").toFile()
        val lib = LibraryStore(dir)
        val base = lib.import("POKEMON HG.nds", dsRom("POKEMON HG", "IPKE"))
        // A tiny IPS: write "HACK" at 0x200 (well past the header), no truncation.
        val ips = java.io.ByteArrayOutputStream().apply {
            write("PATCH".toByteArray()); write(0); write(0x02); write(0x00)
            write(0); write(4); write("HACK".toByteArray()); write("EOF".toByteArray())
        }.toByteArray()
        val out = lib.patch(base, "My Hack v2.ips", ips)
        assertEquals("My Hack v2.nds", out.name, "named after the patch, base's extension")
        assertEquals("HACK", String(out.file.readBytes(), 0x200, 4))
        assertEquals(RomKind.HEARTGOLD_U, out.kind, "header still says HeartGold")
        assertTrue(base.file.readBytes()[0x200] == 0.toByte(), "the base is untouched")
        assertEquals(2, lib.list().size)
        // A patch for a different file says so in words, and stores nothing.
        val bad = assertFailsWith<com.ironmonone.patch.CorruptPatch> {
            lib.patch(base, "junk.ups", ByteArray(20))
        }
        assertTrue(bad.message!!.startsWith("The patch file is damaged"))
        assertEquals(2, lib.list().size)
    }

    private fun dsRom(title: String, code: String): ByteArray {
        val r = ByteArray(0x1000)
        title.forEachIndexed { i, c -> r[i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        return r
    }
}
