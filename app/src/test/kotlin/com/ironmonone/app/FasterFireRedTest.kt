package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.RomIdentity
import com.ironmonone.tracker.GameMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Faster FireRed 1.3.2, the quality-of-life patch IronMON players use on
 * FireRed Rev 1 (marked hidden items, instant PC, shortened errands).
 *
 * The patch ships in the app (assets/patches/faster-firered-u-v11.ips, the file
 * PrepOptions applies), so this runs when the v1.1 dump is to hand: IRONMON_ROMS
 * holding firered-u-v11.gba. It used to want its own copy in IRONMON_PATCHES,
 * which nothing set, so the release gate never ran it (rc32 audit P3 #105).
 *
 * What it pins is what the app has to get right: the CRC that identifies the
 * patched build, that the tracker's ROM tables did not move, and that the
 * randomizer still loads it.
 */
class FasterFireRedTest {

    private fun applyIps(base: ByteArray, patch: ByteArray): ByteArray {
        assertTrue(patch.size > 5 && String(patch, 0, 5) == "PATCH", "not an IPS file")
        val out = base.copyOf()
        var o = 5
        while (o + 2 < patch.size) {
            val at = ((patch[o].toInt() and 255) shl 16) or ((patch[o + 1].toInt() and 255) shl 8) or (patch[o + 2].toInt() and 255)
            if (at == 0x454F46) break                      // "EOF"
            o += 3
            val size = ((patch[o].toInt() and 255) shl 8) or (patch[o + 1].toInt() and 255)
            o += 2
            val data: ByteArray
            if (size == 0) {                                // run-length record
                val run = ((patch[o].toInt() and 255) shl 8) or (patch[o + 1].toInt() and 255)
                o += 2
                data = ByteArray(run) { patch[o] }
                o += 1
            } else {
                data = patch.copyOfRange(o, o + size)
                o += size
            }
            data.copyInto(out, at)
        }
        return out
    }

    private fun files(): Triple<File, File, ByteArray>? {
        val rom = Dumps.rom("firered-u-v11.gba")
        val ips = File("src/main/assets/patches/faster-firered-u-v11.ips")
        if (rom == null) {
            println("FasterFireRedTest skipped: set IRONMON_ROMS (firered-u-v11.gba)")
            return null
        }
        val base = rom.readBytes()
        return Triple(rom, ips, applyIps(base, ips.readBytes()))
    }

    @Test
    fun `the patched build is identified as the pinned kind, not as a modified ROM`() {
        val (_, _, patched) = files() ?: return
        val id = RomIdentity.identify(patched)
        assertEquals(RomKind.FIRERED_V11_FASTER.id, id.kind?.id, "identified as ${id.summary}")
        assertEquals(RomKind.FIRERED_V11_FASTER.expectedCrc, id.crc, "the pinned CRC is stale")
        assertEquals("FRLG", id.kind!!.family, "it must take the FRLG settings files")
        assertEquals(RomKind.FIRERED_U_V11.id, id.kind!!.baseId)
    }

    @Test
    fun `every ROM table the tracker reads is where vanilla keeps it`() {
        val (rom, _, patched) = files() ?: return
        val vanilla = rom.readBytes()
        val m = GameMap.FIRERED_U_V11
        // Species count and trainer count for v1.1, enough of each table to
        // catch a shift rather than a single edited entry.
        val regions = listOf(
            "base stats" to (m.baseStats to 412 * 28),
            "level-up learnsets" to (m.levelUpLearnsets to 412 * 4),
            "trainers" to (m.gTrainers to 743 * 40),
            "trainer class names" to (m.gTrainerClassNames to 107 * 13),
            "experience tables" to (m.expTables to 6 * 101 * 4),
        )
        for ((name, r) in regions) {
            val (addr, len) = r
            if (addr == 0L) continue
            val off = (addr - 0x08000000L).toInt()
            val same = (0 until len).count { vanilla[off + it] == patched[off + it] }
            assertEquals(len, same, "$name moved: the tracker would read the wrong bytes")
        }
    }

    @Test
    fun `the randomizer still loads it and writes a run`() {
        val (_, _, patched) = files() ?: return
        val src = File.createTempFile("faster", ".gba").apply { writeBytes(patched) }
        val out = File.createTempFile("run", ".gba")
        try {
            ZxEngine.randomize(src, File("src/main/assets/presets/FRLG Standard.rnqs"), out, 20260923L, Generation.GBA3)
            val after = out.readBytes()
            assertEquals(patched.size, after.size)
            assertTrue(patched.indices.count { patched[it] != after[it] } > 10_000, "nothing was randomized")
            // The patch's own code sits past the vanilla data; randomizing must not touch it.
            val tail = (0xF00000 until 0x1000000).count { patched[it] == after[it] }
            assertEquals(0x100000, tail, "randomizing overwrote the patch's added code")
        } finally { src.delete(); out.delete() }
    }
}
