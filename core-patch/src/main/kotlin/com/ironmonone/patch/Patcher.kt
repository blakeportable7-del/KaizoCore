package com.ironmonone.patch

import com.ironmonone.core.PatchFormat
import java.util.zip.CRC32

/**
 * Patch application, ported from the verified reference in `tools/RomTool.java`.
 *
 * Nothing on the JVM does this: Maven Central has no IPS/BPS artifact, and the real
 * implementations (Flips, Hips, libpatch) are C or C++ while RomPatcher.js is
 * JavaScript. Surveyed 2026-08-30.
 *
 * Every failure carries a message written for the user, not the log. The CRC gate's
 * entire job is to refuse a wrong dump with a reason instead of silently producing a
 * corrupt ROM.
 */
sealed class PatchException(message: String) : Exception(message)

class WrongSourceRom(val expectedCrc: Long, val actualCrc: Long, romName: String) :
    PatchException(
        "This patch needs a different copy of $romName. " +
            "It expects checksum %08x and yours is %08x.".format(expectedCrc, actualCrc)
    )

class WrongSourceSize(val expectedBytes: Long, val actualBytes: Long) :
    PatchException(
        "This patch needs a %,d byte ROM and yours is %,d bytes. ".format(expectedBytes, actualBytes) +
            "That usually means the file is a different game, or already patched."
    )

class CorruptPatch(detail: String) : PatchException("The patch file is damaged: $detail")

class OutputMismatch(val expectedCrc: Long, val actualCrc: Long) :
    PatchException(
        "The patch applied but produced the wrong result " +
            "(expected %08x, got %08x). Do not use the output.".format(expectedCrc, actualCrc)
    )

object Crc32 {
    fun of(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Long =
        CRC32().apply { update(data, offset, length) }.value
}

object Patcher {

    fun detect(patch: ByteArray): PatchFormat? = when {
        patch.size > 4 && patch.startsWith("BPS1") -> PatchFormat.BPS
        patch.size > 5 && patch.startsWith("PATCH") -> PatchFormat.IPS
        patch.size > 4 && patch.startsWith("UPS1") -> PatchFormat.UPS
        Xdelta.isXdelta(patch) -> PatchFormat.XDELTA
        else -> null
    }

    /**
     * Applies [patch] to [source]. [romName] appears in the failure message a user reads.
     */
    fun apply(patch: ByteArray, source: ByteArray, romName: String = "that ROM"): ByteArray =
        when (detect(patch)) {
            PatchFormat.BPS -> Bps.apply(patch, source, romName)
            PatchFormat.IPS -> Ips.apply(patch, source)
            PatchFormat.UPS -> Ups.apply(patch, source, romName)
            PatchFormat.XDELTA -> throw CorruptPatch("an xdelta patch is applied file to file (applyFiles), not in memory")
            else -> throw CorruptPatch("it is not an IPS, BPS, UPS or xdelta patch")
        }

    /**
     * Applies a patch file to a ROM file, writing [target]. xdelta streams (a
     * 128 MB DS dump never sits in the heap); the small formats go through
     * memory. Returns the target's CRC32.
     */
    fun applyFiles(patch: java.io.File, source: java.io.File, target: java.io.File, romName: String = "that ROM", onProgress: ((Long, Long) -> Unit)? = null): Long {
        val head = patch.inputStream().use { it.readNBytes(8) }
        if (Xdelta.isXdelta(head)) {
            Xdelta.apply(patch, source, target, onProgress)
        } else {
            val out = apply(patch.readBytes(), source.readBytes(), romName)
            target.parentFile?.mkdirs(); target.writeBytes(out)
        }
        val crc = java.util.zip.CRC32()
        target.inputStream().buffered(1 shl 20).use { input -> val b = ByteArray(1 shl 20); while (true) { val n = input.read(b); if (n < 0) break; crc.update(b, 0, n) } }
        return crc.value
    }

    private fun ByteArray.startsWith(magic: String): Boolean =
        magic.indices.all { this[it].toInt() == magic[it].code }
}

/** Little-endian helpers shared by both formats. */
internal fun ByteArray.u8(o: Int): Int = this[o].toInt() and 0xFF
internal fun ByteArray.u16be(o: Int): Int = (u8(o) shl 8) or u8(o + 1)
internal fun ByteArray.u24be(o: Int): Int = (u8(o) shl 16) or (u8(o + 1) shl 8) or u8(o + 2)
internal fun ByteArray.u32le(o: Int): Long =
    u8(o).toLong() or (u8(o + 1).toLong() shl 8) or
        (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)
