package com.swordfish.libretrodroid

/**
 * KaizoCore patch (rc33): a melonDS state taken while a DMA was copying out of main RAM crashed the core when it was
 * loaded into a game that had only just started.
 *
 * melonDS 0.9 saves where a DMA is in its main RAM burst (MRAMBurstCount) but not the timing table that count indexes
 * (MRAMBurstTable, a pointer the core sets only when a burst starts). In a core that has run that DMA channel before,
 * the old pointer is still a good table. In one that has just booted it is null, and the next frame reads table[count]
 * through it: SIGSEGV at 0x6b in DMA::UnitTimings9_16, DMA3 feeding the 3D FIFO on Black 2's title screen
 * (2026-10-01). The app loads the auto slot as soon as a core is up, so that state crashed every launch.
 *
 * Every DMA the state has running is marked as a burst just started (Running = 2, what DMA::Start sets), so the core
 * picks its table again and counts from the first entry. The cost is one burst-start delay on a transfer already under
 * way. Only the layout this core writes is touched: "MELN" version 9.0, DMA sections of 72 bytes, Running at byte 40
 * of the section's data. Anything else is handed back as it came.
 */
object MelonState {
    private const val HEADER = 0x10
    private const val SECTION_HEADER = 16
    private const val DMA_SECTION = 72
    private const val RUNNING = SECTION_HEADER + 10 * 4

    /** [data] ready for melonDS to load: the same array when nothing needs changing, otherwise a changed copy. */
    fun safeToLoad(data: ByteArray): ByteArray {
        if (data.size < HEADER || !tag(data, 0, "MELN")) return data
        if (u16(data, 4) != 9 || u16(data, 6) != 0) return data
        var out: ByteArray? = null
        var i = HEADER
        while (i + SECTION_HEADER <= data.size) {
            val len = u32(data, i + 4)
            if (len < SECTION_HEADER || len > data.size - i) break
            if (len == DMA_SECTION.toLong() && tag(data, i, "DMA") && u32(data, i + RUNNING) == 1L) {
                val o = out ?: data.copyOf().also { out = it }
                o[i + RUNNING] = 2
            }
            i += len.toInt()
        }
        return out ?: data
    }

    private fun tag(b: ByteArray, at: Int, s: String): Boolean = s.indices.all { b[at + it] == s[it].code.toByte() }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        (u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16))
}
