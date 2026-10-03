package com.ironmonone.app

import com.dabomstew.pkrandomzx.newnds.NARCArchive
import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Engine
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import com.ironmonone.tracker.nds.NdsGameMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IronMON HGSS 0.2.2a (PyroMikeGit), bundled 2026-10-03 (Blake, 2026-10-02). Runs when the pinned HeartGold dump is
 * to hand (heartgold-u.nds in IRONMON_ROMS, else in .vendor/roms), as PlatinumSuperKaizoTest does for Platinum: the
 * bundled patch applies with every xdelta window's checksum passing and makes the pinned kind; the header the DS
 * tracker detects the game by still says HeartGold; HeartGold's sprite archive, the one RomSprites decodes, is still
 * where HeartGold keeps it; and the randomizer (ZX, the engine this kind goes to) randomizes it with HGSS Kaizo.
 */
class IronmonHgssTest {
    private val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    @Test
    fun `the patch builds the pinned kind, the tracker and the sprites see HeartGold, and ZX randomizes it with HGSS Kaizo`() {
        val rom = File(roms, "heartgold-u.nds").takeIf { it.isFile }
            ?: return println("IronmonHgssTest skipped: no heartgold-u.nds in $roms")
        assertTrue(RomIdentity.identify(rom).let { it.exact && it.kind == RomKind.HEARTGOLD_U }, "the dump is the pinned HeartGold")
        val opt = PrepOptions.forKind(RomKind.HEARTGOLD_U).first { it.out?.id == RomKind.HEARTGOLD_IRONMON.id }
        val built = File.createTempFile("ironmonhgss", ".nds")
        val run = File.createTempFile("ironmonhgss-run", ".nds")
        try {
            val crc = Patcher.applyFiles(File("src/main/assets/patches/" + opt.asset), rom, built)
            println("IronmonHgssTest: built %d bytes, CRC-32 %08x".format(built.length(), crc))
            assertEquals("%08x".format(RomKind.HEARTGOLD_IRONMON.expectedCrc), "%08x".format(crc), "the build the bundled patch makes of the pinned dump")
            val id = RomIdentity.identify(built)
            assertEquals(RomKind.HEARTGOLD_IRONMON.id, id.kind?.id, "identified as ${id.summary}")
            assertTrue(id.exact && id.dsHeader!!.checksumValid, "an exact copy, with a header melonDS accepts")
            assertEquals("HGSS", id.kind!!.family, "it takes the HGSS settings files")

            // The DS tracker picks its map by the game code in the cartridge header (NdsGameMap.detect).
            val head = built.inputStream().use { it.readNBytes(0x20) }
            assertEquals("POKEMON HG", String(head, 0, 10, Charsets.US_ASCII))
            assertEquals("IPKE", String(head, 0x0C, 4, Charsets.US_ASCII), "HeartGold's US game code")
            val code = (0 until 4).fold(0L) { acc, i -> acc or ((head[0x0C + i].toLong() and 0xFF) shl (8 * i)) }
            assertTrue(code in NdsGameMap.HGSS.gameCodes, "the game code the DS tracker detects HeartGold by: ${String(head, 0x0C, 4, Charsets.US_ASCII)}")

            // The tracker card's sprites come out of the game's own archive (RomSprites), and the patched game keeps
            // HeartGold's where HeartGold has it, so the archive of the game it was made from is the one to read.
            val nds = NDSRom(built.absolutePath)
            val decoded = try {
                val narc = NARCArchive(nds.getFile(RomSprites.narcPath(RomKind.HEARTGOLD_U)!!))
                listOf(1, 4, 7, 25, 152, 155, 158, 249, 250, 493).count { species ->
                    runCatching {
                        var raw = narc.files[species * 6 + 3]; if (raw.isEmpty()) raw = narc.files[species * 6 + 2]
                        RomSprites.decodeGen4(raw, narc.files[species * 6 + 4], backwards = false).argb.count { it != 0 } >= 200
                    }.getOrDefault(false)
                }
            } finally { nds.closeROM() }
            assertEquals(10, decoded, "sprites decoded out of the patched game")

            assertEquals(Engine.ZX, RomKind.HEARTGOLD_IRONMON.engine)
            val out = Randomizers.randomize(RomKind.HEARTGOLD_IRONMON, built, File("src/main/assets/presets/HGSS Kaizo.rnqs"), run, 20261003L)
            assertTrue(run.length() > 100_000_000, "no game written")
            assertTrue("--Trainers Pokemon--" in out.logText, "the log has the trainers")
            assertTrue(Randomizers.sidecarFor(run).length() > 0, "the species file the DS tracker reads types and abilities from")
        } finally {
            built.delete(); run.delete(); Randomizers.logFor(run).delete(); Randomizers.sidecarFor(run).delete()
        }
    }
}
