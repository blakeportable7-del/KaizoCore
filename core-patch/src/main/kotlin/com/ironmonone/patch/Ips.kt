package com.ironmonone.patch

/**
 * IPS patches, the format the small QoL hacks ship as.
 *
 * IPS carries no checksums at all, so unlike BPS it cannot tell you the source was
 * wrong. Verify the source by CRC before calling this, never after.
 */
object Ips {

    fun apply(patch: ByteArray, source: ByteArray): ByteArray {
        if (patch.size < 8 || !patch.matches(0, "PATCH")) {
            throw CorruptPatch("it does not start with a PATCH header")
        }

        var target = source.copyOf()
        var i = 5

        while (i + 3 <= patch.size) {
            if (patch.matches(i, "EOF")) {
                i += 3
                if (i + 3 <= patch.size) {                        // optional truncation
                    val cut = patch.u24be(i)
                    if (cut < target.size) target = target.copyOf(cut)
                }
                return target
            }

            val offset = patch.u24be(i); i += 3
            val length = patch.u16be(i); i += 2

            if (length == 0) {                                    // RLE run
                if (i + 3 > patch.size) throw CorruptPatch("an RLE record is truncated")
                val run = patch.u16be(i); i += 2
                val value = patch[i++]
                if (offset + run > target.size) target = target.copyOf(offset + run)
                java.util.Arrays.fill(target, offset, offset + run, value)
            } else {
                if (i + length > patch.size) throw CorruptPatch("a record runs past the end")
                if (offset + length > target.size) target = target.copyOf(offset + length)
                patch.copyInto(target, offset, i, i + length)
                i += length
            }
        }
        throw CorruptPatch("it has no EOF marker")
    }

    private fun ByteArray.matches(at: Int, magic: String): Boolean =
        at + magic.length <= size && magic.indices.all { this[at + it].toInt() == magic[it].code }
}
