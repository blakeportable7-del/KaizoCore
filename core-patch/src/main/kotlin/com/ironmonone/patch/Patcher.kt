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
            else -> throw CorruptPatch("it is not an IPS, BPS or UPS patch")
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
