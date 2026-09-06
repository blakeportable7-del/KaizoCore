package com.ironmonone.patch

/**
 * UPS patches (byuu, 2008): the format between IPS and BPS, and still what
 * a good share of hacks ship as.
 *
 * Layout: "UPS1", varint source size, varint target size, then records of
 * (varint gap, XOR bytes ending in a 0x00 that is itself consumed), then
 * source CRC-32, target CRC-32 and patch CRC-32, little-endian, as the last
 * twelve bytes. The varint is BPS's, which is why Cursor is shared.
 *
 * UPS is symmetric: XOR-ing the target with the same patch gives the source
 * back. The size and CRC gates here use the SOURCE side only; a user asking
 * to un-patch gets the source-mismatch message, which is honest ("this
 * expects a different file") if not specific.
 */
object Ups {

    fun apply(patch: ByteArray, source: ByteArray, romName: String = "that ROM"): ByteArray {
        if (patch.size < 4 + 12 || patch.u8(0) != 'U'.code || patch.u8(1) != 'P'.code ||
            patch.u8(2) != 'S'.code || patch.u8(3) != '1'.code
        ) throw CorruptPatch("it does not start with a UPS1 header")

        val wantPatchCrc = patch.u32le(patch.size - 4)
        val gotPatchCrc = Crc32.of(patch, 0, patch.size - 4)
        if (gotPatchCrc != wantPatchCrc) throw CorruptPatch("its own checksum does not match")

        val c = Varint(patch, 4)
        val sourceSize = c.next()
        val targetSize = c.next()

        if (source.size.toLong() != sourceSize) {
            throw WrongSourceSize(sourceSize, source.size.toLong())
        }
        val wantSourceCrc = patch.u32le(patch.size - 12)
        val gotSourceCrc = Crc32.of(source)
        if (gotSourceCrc != wantSourceCrc) throw WrongSourceRom(wantSourceCrc, gotSourceCrc, romName)

        // The target starts as the source, zero-extended or truncated to its
        // declared size; every record XORs on top of that.
        val target = source.copyOf(targetSize.toInt())
        var out = 0L
        val end = patch.size - 12
        while (c.i < end) {
            out += c.next()
            while (c.i < end) {
                val x = patch.u8(c.i++)
                if (x == 0) { out++; break }
                if (out < target.size) target[out.toInt()] = (target[out.toInt()].toInt() xor x).toByte()
                out++
            }
        }

        val wantTargetCrc = patch.u32le(patch.size - 8)
        val gotTargetCrc = Crc32.of(target)
        if (gotTargetCrc != wantTargetCrc) throw OutputMismatch(wantTargetCrc, gotTargetCrc)
        return target
    }

    /** The BPS/UPS variable-width integer, same encoding in both formats. */
    internal class Varint(val d: ByteArray, var i: Int) {
        fun next(): Long {
            var data = 0L
            var shift = 1L
            while (true) {
                if (i >= d.size) throw CorruptPatch("it ends in the middle of a number")
                val x = d.u8(i++)
                data += (x and 0x7f) * shift
                if (x and 0x80 != 0) break
                shift = shift shl 7
                data += shift
            }
            return data
        }
    }
}
