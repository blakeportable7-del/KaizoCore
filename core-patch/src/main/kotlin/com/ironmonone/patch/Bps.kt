package com.ironmonone.patch

/**
 * BPS patches, as used by the Nat. Dex releases.
 *
 * A BPS carries CRC-32 of its source, its target and itself, so applying one IS the
 * verification step. [info] reads those without applying anything, which is how the UI
 * can say "this patch wants Emerald (U)" before the user has picked a file.
 */
object Bps {

    data class Info(
        val sourceSize: Long,
        val targetSize: Long,
        val sourceCrc: Long,
        val targetCrc: Long,
        val patchCrc: Long,
        val patchIntact: Boolean,
        val metadataBytes: Long,
    )

    fun info(patch: ByteArray): Info {
        requireHeader(patch)
        val c = Cursor(patch, 4)
        val sourceSize = c.varint()
        val targetSize = c.varint()
        val metadata = c.varint()
        val declared = patch.u32le(patch.size - 4)
        return Info(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sourceCrc = patch.u32le(patch.size - 12),
            targetCrc = patch.u32le(patch.size - 8),
            patchCrc = declared,
            patchIntact = Crc32.of(patch, 0, patch.size - 4) == declared,
            metadataBytes = metadata,
        )
    }

    fun apply(patch: ByteArray, source: ByteArray, romName: String = "that ROM"): ByteArray {
        requireHeader(patch)
        val c = Cursor(patch, 4)
        val sourceSize = c.varint()
        val targetSize = c.varint()

        // Read the metadata length into a local FIRST. Writing `c.i += c.varint().toInt()`
        // reads c.i before evaluating the right-hand side, so the varint's own advance is
        // thrown away and the cursor rewinds onto the length byte. The decoder then reads
        // that byte as an action and every offset after it is wrong.
        val metadataBytes = c.varint().toInt()
        c.i += metadataBytes

        val wantSourceCrc = patch.u32le(patch.size - 12)
        if (source.size.toLong() != sourceSize) {
            throw WrongSourceSize(sourceSize, source.size.toLong())
        }
        val gotSourceCrc = Crc32.of(source)
        if (gotSourceCrc != wantSourceCrc) {
            throw WrongSourceRom(wantSourceCrc, gotSourceCrc, romName)
        }

        val target = ByteArray(targetSize.toInt())
        var out = 0
        var sourceRel = 0
        var targetRel = 0
        val end = patch.size - 12

        while (c.i < end) {
            val action = c.varint()
            val length = ((action shr 2) + 1).toInt()
            when ((action and 3L).toInt()) {
                0 -> repeat(length) { target[out] = source[out]; out++ }
                1 -> repeat(length) { target[out++] = patch[c.i++] }
                2 -> {
                    sourceRel += signed(c.varint())
                    repeat(length) { target[out++] = source[sourceRel++] }
                }
                3 -> {
                    targetRel += signed(c.varint())
                    repeat(length) { target[out++] = target[targetRel++] }
                }
            }
        }

        val wantTargetCrc = patch.u32le(patch.size - 8)
        val gotTargetCrc = Crc32.of(target)
        if (gotTargetCrc != wantTargetCrc) throw OutputMismatch(wantTargetCrc, gotTargetCrc)
        return target
    }

    private fun requireHeader(patch: ByteArray) {
        if (patch.size < 16 || patch.u8(0) != 'B'.code || patch.u8(1) != 'P'.code ||
            patch.u8(2) != 'S'.code || patch.u8(3) != '1'.code
        ) throw CorruptPatch("it does not start with a BPS1 header")
    }

    private fun signed(v: Long): Int =
        ((if (v and 1L != 0L) -1 else 1) * (v shr 1)).toInt()

    /** BPS variable-width integer. */
    private class Cursor(val d: ByteArray, var i: Int) {
        fun varint(): Long {
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
