package com.ironmonone.app

import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sentor's GIGA patch 3.1 for Platinum (SentorG/NewPlatPatch, 2025-02-12), the Platinum IronMON quality-of-life patch
 * (Blake, 2026-11-06: a GIGA game had no Kaizo run). A game patched with it used to be an unknown hack, playable without
 * the tracker or the randomizer. Its four builds are bundled since 2026-11-06 with SentorG's permission, given to Blake
 * on the KaizoCore Discord (NOTICE), offered on Library, Patched versions, for Platinum, with the link to his page kept.
 *
 * What runs everywhere: the bundled files are the release's by SHA-256, the kinds take the DPPt settings files and read
 * as Platinum, and the link stays. With IRONMON_ROMS (platinum-u.nds): the dump is Platinum 1.0 by checksum, each file
 * builds its pinned CRC and is identified as its build, keeps the header and the main program (arm9) the DS tracker
 * reads Platinum's memory by, keeps the picture archives the card reads, and ZX randomizes it with DPPt Kaizo.
 */
class PlatinumGigaTest {

    /** Each build, its bundled file, and the release file's SHA-256 (NOTICE). */
    private val builds = listOf(
        Triple(RomKind.PLATINUM_GIGA, "giga-platinum-u.xdelta", "f79ae1cac4913da1a0d035b0e62a9b03495a5201eb274a705037f28802c4fd46"),
        Triple(RomKind.PLATINUM_GIGA_FLX, "gigaflx-platinum-u.xdelta", "c6c68cefd08080375d4698fffcde7d497d0923cbef2937210a2df9a5fe2eb679"),
        Triple(RomKind.PLATINUM_GIGA_SNOWY, "gigasnowy-platinum-u.xdelta", "506eda9354ef05fee042d2f7c201ab9606844cd670083eb337bab0a77aaa3357"),
        Triple(RomKind.PLATINUM_GIGA_SNOWY_FLX, "gigasnowyflx-platinum-u.xdelta", "cfae1809c635c69cc65a2d6006376413d8dd9a80b8be6c36c15d25c0ae193992"),
    )

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `the four builds are bundled as released, offered for Platinum, read as Platinum, and still linked`() {
        val options = PrepOptions.forKind(RomKind.PLATINUM_U)
        for ((k, asset, sha) in builds) {
            assertEquals(sha, sha256(File("src/main/assets/patches/$asset")), "$asset is the release file, unmodified")
            assertEquals(asset, options.single { it.out?.id == k.id }.asset)
            assertEquals(k, RomKind.byId(k.id))
            assertEquals("DPPt", k.family)
            assertEquals(RomKind.PLATINUM_U.id, k.baseId)
            assertTrue(k in RomKind.allPatched)
            assertEquals(listOf("STANDARD"), PrepOptions.forKind(k).map { it.id }, "already patched: stored as is")
            assertTrue(RomFormSprites.supports(k), "${k.id}: its picture archives are Platinum's")
            assertEquals("poketool/pokegra/pl_pokegra.narc", RomSprites.narcPath(k))
            // GIGA has no smart AI: Super Kaizo points at its own patch.
            assertTrue("Super Kaizo 1.0 patch" in assertNotNull(RulesetCatalog.superKaizoWarning(k, "superkaizo")))
            assertNull(RulesetCatalog.superKaizoWarning(k, "kaizo"))
        }
        val link = HackLinks.all.single { it.name == "Platinum GIGA patch" }
        assertEquals("SentorG" to "https://github.com/SentorG/NewPlatPatch/releases", link.author to link.url)
        assertEquals(setOf(RomKind.PLATINUM_U.id), link.ids)
        assertTrue(link in HackLinks.forFamily("platinum"))
        assertTrue("KaizoCore already includes it" in link.blurb)
    }

    private fun header(f: File): ByteArray = f.inputStream().use { it.readNBytes(0x200) }
    private fun u32(b: ByteArray, o: Int) = (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
        ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
    private fun region(f: File, off: Long, len: Int): ByteArray = RandomAccessFile(f, "r").use { r -> ByteArray(len).also { r.seek(off); r.readFully(it) } }

    @Test
    fun `the bundled files build the pinned kinds from Platinum 1_0, keep what the tracker reads, and randomize`() {
        val rom = Dumps.rom("platinum-u.nds") ?: return println("PlatinumGigaTest skipped: set IRONMON_ROMS (platinum-u.nds)")
        val crc0 = CRC32().also { c -> rom.inputStream().use { i -> val b = ByteArray(1 shl 20); while (true) { val n = i.read(b); if (n < 0) break; c.update(b, 0, n) } } }.value
        assertEquals(RomKind.PLATINUM_U.expectedCrc, crc0, "the base is Platinum 1.0 by checksum")
        val vh = header(rom)
        val wrong = ArrayList<String>()
        for ((k, asset, _) in builds) {
            val patch = File("src/main/assets/patches/$asset")
            val built = File.createTempFile(k.id, ".nds")
            val run = File.createTempFile(k.id + "-run", ".nds")
            try {
                val crc = Patcher.applyFiles(patch, rom, built)
                println("${k.id}: %08X, %d bytes".format(crc, built.length()))
                if (crc != k.expectedCrc) { wrong += "${k.id} %08X".format(crc); continue }
                assertEquals(k.id, RomIdentity.identify(built).kind?.id)
                val h = header(built)
                assertEquals("CPUE", String(h, 0x0C, 4, Charsets.US_ASCII), "the game code the DS tracker detects Platinum by")
                assertEquals(0, h[0x1E].toInt(), "still version 1.0")
                // The main program (arm9): where it loads and how long it is, so every address in RAM is Platinum's; the DS
                // tracker finds the party, battle and bag through the pointers at RAM +0x20, +0xB70 and +0xBA8. GIGA changes
                // three bytes of code (0x02027A40, 0x020791DE, 0x020E8448) and FLX two more (0x02000DF8, the frame
                // limiter) and Snowy one (0x020E86B8, Twinleaf's weather), none of them there; Super Kaizo, made from GIGA 3 and tracked since rc33, changes 1,095.
                for (o in listOf(0x24, 0x28, 0x2C)) assertEquals(u32(vh, o), u32(h, o), "${k.id}: arm9 header word %02X".format(o))
                val arm9 = u32(vh, 0x2C).toInt()
                val a = region(rom, u32(vh, 0x20), arm9); val b = region(built, u32(h, 0x20), arm9)
                val changed = (0 until arm9).filter { a[it] != b[it] }
                val flx = if ("flx" in k.id) listOf(0xDF8, 0xDF9) else emptyList()
                val snowy = if ("snowy" in k.id) listOf(0xE86B8) else emptyList()
                assertEquals(flx + listOf(0x27A40, 0x791DE, 0xE8448) + snowy, changed, "${k.id}: arm9 bytes changed")
                for (slot in listOf(0x20, 0xB70, 0xBA8)) assertTrue(changed.none { it in slot until slot + 4 }, "${k.id}: pointer slot %X".format(slot))
                val base = NDSRom(rom.absolutePath); val patched = NDSRom(built.absolutePath)
                try {
                    for (a in listOf(RomSprites.narcPath(RomKind.PLATINUM_U)!!, RomFormSprites.PL_OTHERPOKE)) {
                        assertTrue(base.getFile(a).contentEquals(patched.getFile(a)), "${k.id} $a")
                    }
                    assertEquals(RomFormSprites.forms(base, RomKind.PLATINUM_U), RomFormSprites.forms(patched, k))
                } finally { base.closeROM(); patched.closeROM() }

                val out = ZxEngine.randomize(built, File("src/main/assets/presets/DPPt Kaizo.rnqs"), run, 20261106L, Generation.NDS4)
                assertTrue(run.length() > 100_000_000, "no ROM written")
                assertTrue("--Trainers Pokemon--" in out.logText, "the log has the trainers")
            } finally {
                built.delete(); run.delete(); File(run.parentFile, run.nameWithoutExtension + ".species.tsv").delete()
            }
        }
        assertEquals(emptyList(), wrong, "the pinned CRCs")
    }
}
