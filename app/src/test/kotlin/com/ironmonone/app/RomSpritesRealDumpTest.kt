package com.ironmonone.app

import com.dabomstew.pkrandomzx.newnds.NARCArchive
import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.core.RomKind
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The decoder against a REAL dump, when one is present: KAIZOCORE_B2_ROM or KAIZOCORE_HG_ROM if set, else Blake's own
 * dumps in IRONMON_ROMS (C:/Users/bepor/IronMonOne/.vendor/roms by default), where the other real-ROM tests find them.
 * Without one the test passes trivially and says so. Writes a sample of species and a montage to a temporary folder,
 * never beside the dumps, so the result can be looked at, not just counted. Android unit tests have no
 * AWT, so the PNGs are written by the small encoder at the bottom.
 */
class RomSpritesRealDumpTest {
    @Test
    fun `Black 2 decodes every species out of the dump`() {
        val rom = dump("KAIZOCORE_B2_ROM", "black2-u.nds") ?: run { println("no Black 2 dump; skipped"); return }
        val narc = NARCArchive(NDSRom(rom.absolutePath).getFile(RomSprites.narcPath(RomKind.BLACK2_U)!!))
        val out = kotlin.io.path.createTempDirectory("sprites-b2").toFile()
        var decoded = 0; var blank = 0
        val sample = listOf(1, 4, 7, 25, 150, 151, 152, 386, 493, 494, 495, 498, 501, 570, 635, 649)
        val tiles = HashMap<Int, RomSprites.Decoded>()
        for (species in 1..649) {
            val img = runCatching { RomSprites.decodeGen5(narc.files[species * 20], narc.files[species * 20 + 18]) }.getOrNull()
            if (img == null || img.argb.count { it != 0 } < 200) { blank++; continue }
            decoded++
            if (species in sample) { File(out, "$species.png").writeBytes(png(img.width, img.height, img.argb)); tiles[species] = img }
        }
        val cols = 8; val rows = (sample.size + cols - 1) / cols; val cell = 96
        val m = IntArray(cols * cell * rows * cell) { 0xFF1E1E24.toInt() }
        sample.forEachIndexed { i, sp -> tiles[sp]?.let { t -> for (y in 0 until 96) for (x in 0 until 96) { val c = t.argb[y * 96 + x]; if (c != 0) m[((i / cols) * cell + y) * cols * cell + (i % cols) * cell + x] = c } } }
        File(out, "montage.png").writeBytes(png(cols * cell, rows * cell, m))
        println("decoded=$decoded blank=$blank montage=${File(out, "montage.png").absolutePath}")
        assertTrue(decoded >= 640, "expected nearly all 649 species to decode, got $decoded (blank $blank)")
    }

    @Test
    fun `HeartGold decodes every species out of the dump`() {
        val rom = dump("KAIZOCORE_HG_ROM", "heartgold-u.nds") ?: run { println("no HeartGold dump; skipped"); return }
        val narc = NARCArchive(NDSRom(rom.absolutePath).getFile(RomSprites.narcPath(RomKind.HEARTGOLD_U)!!))
        val out = kotlin.io.path.createTempDirectory("sprites-hg").toFile()
        var decoded = 0; var blank = 0
        val sample = listOf(1, 4, 7, 25, 150, 152, 155, 158, 249, 250, 386, 448, 483, 487, 491, 493)
        val tiles = HashMap<Int, RomSprites.Decoded>()
        for (species in 1..493) {
            val img = runCatching {
                var raw = narc.files[species * 6 + 3]; if (raw.isEmpty()) raw = narc.files[species * 6 + 2]
                RomSprites.decodeGen4(raw, narc.files[species * 6 + 4], backwards = false)
            }.getOrNull()
            if (img == null || img.argb.count { it != 0 } < 200) { blank++; continue }
            decoded++
            if (species in sample) { File(out, "$species.png").writeBytes(png(img.width, img.height, img.argb)); tiles[species] = img }
        }
        val cols = 8; val rows = (sample.size + cols - 1) / cols; val cell = 80
        val m = IntArray(cols * cell * rows * cell) { 0xFF1E1E24.toInt() }
        sample.forEachIndexed { i, sp -> tiles[sp]?.let { t -> for (y in 0 until 80) for (x in 0 until 80) { val c = t.argb[y * 80 + x]; if (c != 0) m[((i / cols) * cell + y) * cols * cell + (i % cols) * cell + x] = c } } }
        File(out, "montage.png").writeBytes(png(cols * cell, rows * cell, m))
        println("decoded=$decoded blank=$blank montage=${File(out, "montage.png").absolutePath}")
        assertTrue(decoded >= 485, "expected nearly all 493 species to decode, got $decoded (blank $blank)")
    }

    /** [env]'s path when set (it must exist), else [name] in IRONMON_ROMS or the vendored folder, else null. */
    private fun dump(env: String, name: String): File? {
        System.getenv(env)?.let { p -> return File(p).also { assertTrue(it.isFile, p) } }
        val dir = System.getenv("IRONMON_ROMS")?.let(::File) ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")
        return File(dir, name).takeIf { it.isFile }
    }

    /** Minimal PNG: 8-bit RGBA, no filtering, one IDAT. */
    private fun png(w: Int, h: Int, argb: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) { raw.write(0); for (x in 0 until w) { val c = argb[y * w + x]; raw.write((c shr 16) and 0xFF); raw.write((c shr 8) and 0xFF); raw.write(c and 0xFF); raw.write((c ushr 24) and 0xFF) } }
        val def = Deflater(); def.setInput(raw.toByteArray()); def.finish()
        val comp = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); comp.write(buf, 0, n) }
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        o.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            o.writeInt(data.size); val t = type.toByteArray(Charsets.US_ASCII); o.write(t); o.write(data)
            val c = CRC32(); c.update(t); c.update(data); o.writeInt(c.value.toInt())
        }
        val ihdr = ByteArrayOutputStream(); DataOutputStream(ihdr).apply { writeInt(w); writeInt(h); write(8); write(6); write(0); write(0); write(0) }
        chunk("IHDR", ihdr.toByteArray()); chunk("IDAT", comp.toByteArray()); chunk("IEND", ByteArray(0))
        return bo.toByteArray()
    }
}
