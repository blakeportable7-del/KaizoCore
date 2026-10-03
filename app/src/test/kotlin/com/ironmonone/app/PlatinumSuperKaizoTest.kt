package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Platinum Super Kaizo 1.0 (SentorG), bundled 2026-09-29. Runs when the pinned
 * Platinum dump is to hand (IRONMON_ROMS holding platinum-u.nds, Platinum 1.0),
 * as FasterEmeraldTest does for Emerald: the bundled patch applies with every
 * xdelta window's checksum passing, the result is the pinned kind, the header
 * the DS tracker reads the game from is untouched, and the randomizer's Gen 4
 * handler loads it and randomizes it with the DPPt Super Kaizo preset.
 */
class PlatinumSuperKaizoTest {

    @Test
    fun `the patch builds the pinned kind, and ZX randomizes it with DPPt Super Kaizo`() {
        val rom = Dumps.rom("platinum-u.nds")
            ?: return println("PlatinumSuperKaizoTest skipped: set IRONMON_ROMS (platinum-u.nds)")
        val opt = PrepOptions.forKind(RomKind.PLATINUM_U).first { it.out?.id == RomKind.PLATINUM_SUPERKAIZO.id }
        val built = File.createTempFile("platsk", ".nds")
        val run = File.createTempFile("platsk-run", ".nds")
        try {
            val crc = Patcher.applyFiles(File("src/main/assets/patches/" + opt.asset), rom, built)
            assertEquals("%08x".format(RomKind.PLATINUM_SUPERKAIZO.expectedCrc), "%08x".format(crc))
            val id = RomIdentity.identify(built)
            assertEquals(RomKind.PLATINUM_SUPERKAIZO.id, id.kind?.id, "identified as ${id.summary}")
            assertEquals("DPPt", id.kind!!.family, "it takes the DPPt settings files")
            val head = built.inputStream().use { it.readNBytes(0x20) }
            assertEquals("CPUE", String(head, 0x0C, 4, Charsets.US_ASCII), "the game code the DS tracker detects Platinum by")
            assertEquals(0, head[0x1E].toInt(), "still version 1.0")

            val out = ZxEngine.randomize(built, File("src/main/assets/presets/DPPt Super Kaizo.rnqs"), run, 20260929L, Generation.NDS4)
            assertTrue(run.length() > 100_000_000, "no ROM written")
            assertTrue("--Trainers Pokemon--" in out.logText, "the log has the trainers")
            // Super Kaizo gives every trainer a held item: the log writes them as SPECIES@Item.
            val trainers = out.logText.substringAfter("--Trainers Pokemon--").substringBefore("\n--").lines().filter { it.startsWith("#") }
            assertTrue(trainers.size > 500 && trainers.count { "@" in it } > trainers.size * 9 / 10, "${trainers.count { "@" in it }} of ${trainers.size} trainers hold items")
        } finally {
            built.delete(); run.delete(); File(run.parentFile, run.nameWithoutExtension + ".species.tsv").delete()
        }
    }
}
