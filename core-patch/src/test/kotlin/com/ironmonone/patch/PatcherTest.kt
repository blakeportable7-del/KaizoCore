package com.ironmonone.patch

import com.ironmonone.core.PatchFormat
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatcherTest {

    // ------------------------------------------------------------------ CRC-32

    @Test
    fun `crc32 matches the standard check vector`() {
        // zlib crc32("123456789") == 0xCBF43926. If this is wrong the whole gate is.
        assertEquals(0xCBF43926L, Crc32.of("123456789".toByteArray()))
    }

    // --------------------------------------------------------------------- BPS

    @Test
    fun `applies all four BPS opcodes`() {
        val source = pattern(256, 7, 13)
        val expected = expectedTarget(source)
        assertTrue(Bps.apply(encodeBps(source, expected), source).contentEquals(expected))
    }

    @Test
    fun `refuses a source ROM with the wrong checksum, in plain English`() {
        val source = pattern(256, 7, 13)
        val patch = encodeBps(source, expectedTarget(source))
        val wrong = pattern(256, 7, 13).also { it[100] = (it[100].toInt() xor 0xFF).toByte() }

        val e = assertFailsWith<WrongSourceRom> { Bps.apply(patch, wrong, "Emerald") }
        assertContains(e.message!!, "different copy of Emerald")
    }

    @Test
    fun `refuses a source ROM of the wrong size`() {
        val source = pattern(256, 7, 13)
        val patch = encodeBps(source, expectedTarget(source))
        val e = assertFailsWith<WrongSourceSize> { Bps.apply(patch, pattern(128, 7, 13)) }
        assertContains(e.message!!, "already patched")
    }

    @Test
    fun `refuses output that misses its declared checksum`() {
        val source = pattern(256, 7, 13)
        val patch = encodeBps(source, expectedTarget(source))
        patch[patch.size - 8] = (patch[patch.size - 8].toInt() xor 1).toByte()
        writeU32le(patch, patch.size - 4, Crc32.of(patch, 0, patch.size - 4))

        assertFailsWith<OutputMismatch> { Bps.apply(patch, source) }
    }

    @Test
    fun `rejects a file that is not a patch`() {
        assertNull(Patcher.detect(ByteArray(64)))
        assertFailsWith<CorruptPatch> { Patcher.apply(ByteArray(64), ByteArray(16)) }
    }

    // --------------------------------------------------------------------- IPS

    @Test
    fun `applies a plain IPS record without disturbing neighbours`() {
        val base = pattern(64, 3, 1)
        val patch = ips {
            record(10, byteArrayOf(1, 2, 3, 4, 5))
        }
        val out = Ips.apply(patch, base)
        assertEquals(64, out.size)
        assertTrue((0 until 5).all { out[10 + it].toInt() == it + 1 })
        assertEquals(base[9], out[9])
        assertEquals(base[15], out[15])
    }

    @Test
    fun `applies an IPS RLE run`() {
        val base = pattern(64, 3, 1)
        val patch = ips { rle(20, 10, 0xAB.toByte()) }
        val out = Ips.apply(patch, base)
        assertTrue((20 until 30).all { out[it] == 0xAB.toByte() })
        assertEquals(base[19], out[19])
        assertEquals(base[30], out[30])
    }

    @Test
    fun `honours the optional IPS truncation field`() {
        val patch = ips { truncateTo = 32 }
        assertEquals(32, Ips.apply(patch, pattern(64, 3, 1)).size)
    }

    // ------------------------------------- external validation on the real patch

    /**
     * The strongest test here: the actual 26MB Nat. Dex 1.2.1 patch. Its footer must
     * report the Emerald (U) source CRC and the patched-ROM target CRC that
     * `RomKind.EMERALD_NATDEX_121` hardcodes. Skips if the file is not on this machine.
     */
    @Test
    fun `reads the real Nat Dex patch and reports the known checksums`() {
        val f = File(
            "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\extensions" +
                "\\natdex\\rom_patches\\pokeemerald_natdex_1.2.1.bps"
        )
        if (!f.exists()) {
            println("SKIP: real Nat. Dex patch not present on this machine")
            return
        }
        val info = Bps.info(f.readBytes())
        assertTrue(info.patchIntact, "the patch file itself is damaged")
        assertEquals(0x1f1c08fbL, info.sourceCrc, "should require Emerald (U)")
        assertEquals(0xebfdce4bL, info.targetCrc, "should produce Nat. Dex 1.2.1 Emerald")
        assertEquals(16_777_216L, info.sourceSize)
        assertEquals(33_554_432L, info.targetSize)
        assertEquals(PatchFormat.BPS, Patcher.detect(f.readBytes()))
    }

    // ---------------------------------------------------------------- fixtures

    private fun pattern(len: Int, mul: Int, add: Int) =
        ByteArray(len) { ((it * mul + add) and 0xFF).toByte() }

    /** Laid out so every opcode is required: SourceRead, TargetRead, SourceCopy, TargetCopy. */
    private fun expectedTarget(source: ByteArray) = ByteArray(160).also { t ->
        source.copyInto(t, 0, 0, 64)
        for (i in 0 until 32) t[64 + i] = ((i * 31 + 5) and 0xFF).toByte()
        source.copyInto(t, 96, 200, 232)
        t.copyInto(t, 128, 0, 32)
    }

    private fun encodeBps(source: ByteArray, target: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        "BPS1".forEach { b.write(it.code) }
        varint(b, source.size.toLong())
        varint(b, target.size.toLong())
        varint(b, 0)

        varint(b, ((64 - 1).toLong() shl 2) or 0)          // SourceRead 64
        varint(b, ((32 - 1).toLong() shl 2) or 1)          // TargetRead 32
        for (i in 0 until 32) b.write(target[64 + i].toInt())
        varint(b, ((32 - 1).toLong() shl 2) or 2)          // SourceCopy 32 from 200
        varint(b, 200L shl 1)
        varint(b, ((32 - 1).toLong() shl 2) or 3)          // TargetCopy 32 from 0
        varint(b, 0)

        val head = b.toByteArray()
        val out = head.copyOf(head.size + 12)
        writeU32le(out, head.size, Crc32.of(source))
        writeU32le(out, head.size + 4, Crc32.of(target))
        writeU32le(out, out.size - 4, Crc32.of(out, 0, out.size - 4))
        return out
    }

    /** Inverse of the BPS varint decoder. */
    private fun varint(b: ByteArrayOutputStream, value: Long) {
        var n = value
        while (true) {
            val x = n and 0x7f
            n = n shr 7
            if (n == 0L) { b.write((0x80L or x).toInt()); return }
            b.write(x.toInt())
            n--
        }
    }

    private fun writeU32le(d: ByteArray, o: Int, v: Long) {
        d[o] = v.toByte()
        d[o + 1] = (v ushr 8).toByte()
        d[o + 2] = (v ushr 16).toByte()
        d[o + 3] = (v ushr 24).toByte()
    }

    private class IpsBuilder {
        val body = ByteArrayOutputStream()
        var truncateTo: Int? = null
        fun record(offset: Int, data: ByteArray) {
            be24(offset); be16(data.size); data.forEach { body.write(it.toInt()) }
        }
        fun rle(offset: Int, run: Int, value: Byte) {
            be24(offset); be16(0); be16(run); body.write(value.toInt())
        }
        private fun be24(v: Int) { body.write(v shr 16); body.write(v shr 8); body.write(v) }
        private fun be16(v: Int) { body.write(v shr 8); body.write(v) }
    }

    private fun ips(build: IpsBuilder.() -> Unit): ByteArray {
        val b = IpsBuilder().apply(build)
        val out = ByteArrayOutputStream()
        "PATCH".forEach { out.write(it.code) }
        out.write(b.body.toByteArray())
        "EOF".forEach { out.write(it.code) }
        b.truncateTo?.let { out.write(it shr 16); out.write(it shr 8); out.write(it) }
        return out.toByteArray()
    }
}
