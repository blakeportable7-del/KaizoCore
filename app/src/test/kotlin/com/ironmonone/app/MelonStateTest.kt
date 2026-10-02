package com.ironmonone.app

import com.swordfish.libretrodroid.MelonState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * rc33: a melonDS state taken while a DMA was copying out of main RAM crashed a core that had just booted (the burst
 * table pointer is not in the state), and the auto slot loads as soon as a core is up, so it crashed every launch
 * (Black 2, 2026-10-01: DMA3 into the 3D FIFO, burst count 0x6b, SIGSEGV at 0x6b).
 */
class MelonStateTest {
    private val ndsg = 16 + 40

    /** A melonDS 9.0 state: its header, NDSG, DMA0 to DMA7 (72 bytes each, as the core writes them), GPUG. */
    private fun state(running: IntArray, count: IntArray = IntArray(8), major: Int = 9): ByteArray {
        fun section(tag: String, data: ByteArray): ByteArray {
            val b = ByteBuffer.allocate(16 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            b.put(tag.toByteArray(Charsets.US_ASCII)); b.putInt(16 + data.size); b.putLong(0); b.put(data)
            return b.array()
        }
        var body = section("NDSG", ByteArray(40) { 7 })
        for (n in 0 until 8) {
            val d = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
            // SrcAddr, DstAddr, Cnt, StartMode, CurSrcAddr, CurDstAddr, RemCount, IterCount, SrcAddrInc, DstAddrInc,
            // Running, InProgress, IsGXFIFODMA, MRAMBurstCount: the Black 2 state's DMA3.
            d.putInt(0x0229df94); d.putInt(0x04000400); d.putInt(0x84400076L.toInt()); d.putInt(0)
            d.putInt(0x0229e140); d.putInt(0x04000400); d.putInt(11); d.putInt(11); d.putInt(1); d.putInt(0)
            d.putInt(running[n]); d.putInt(if (running[n] != 0) 1 else 0); d.putInt(1); d.putInt(count[n])
            body += section("DMA$n", d.array())
        }
        body += section("GPUG", ByteArray(24) { 3 })
        val h = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        h.put("MELN".toByteArray(Charsets.US_ASCII)); h.putShort(major.toShort()); h.putShort(0); h.putInt(16 + body.size); h.putInt(0)
        return h.array() + body
    }

    private fun runningAt(dma: Int) = 16 + ndsg + dma * 72 + 16 + 40

    private fun runningOf(s: ByteArray, dma: Int): Int = ByteBuffer.wrap(s, runningAt(dma), 4).order(ByteOrder.LITTLE_ENDIAN).int

    @Test
    fun `a DMA caught mid-burst starts its burst again, and nothing else changes`() {
        val s = state(IntArray(8).also { it[3] = 1 }, IntArray(8).also { it[3] = 0x6b; it[6] = 0x43 })
        val fixed = MelonState.safeToLoad(s)
        assertNotSame(s, fixed)
        assertEquals(2, runningOf(fixed, 3))
        assertEquals(1, runningOf(s, 3), "the caller's bytes are left alone")
        assertEquals(listOf(runningAt(3)), s.indices.filter { s[it] != fixed[it] })
    }

    @Test
    fun `every DMA mid-burst is caught, on either CPU`() {
        val fixed = MelonState.safeToLoad(state(IntArray(8) { if (it == 0 || it == 7) 1 else 0 }))
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0, 2), (0 until 8).map { runningOf(fixed, it) })
    }

    @Test
    fun `a state with no DMA mid-burst is handed back as it came`() {
        for (r in listOf(0, 2)) {
            val s = state(IntArray(8) { r })
            assertSame(s, MelonState.safeToLoad(s))
        }
    }

    @Test
    fun `only this core's own layout is touched`() {
        val running = IntArray(8).also { it[3] = 1 }
        val newer = state(running, major = 10)
        assertSame(newer, MelonState.safeToLoad(newer))
        val gba = ByteArray(4096) { 1 }
        assertSame(gba, MelonState.safeToLoad(gba))
        val tiny = byteArrayOf(0x4D, 0x45, 0x4C)
        assertSame(tiny, MelonState.safeToLoad(tiny))
        // Cut short, or a section with a length that cannot be: the walk stops without throwing.
        val cut = state(running).copyOf(runningAt(3) - 20)
        assertSame(cut, MelonState.safeToLoad(cut))
        val bad = state(running).also { it[16 + 4] = 3 }
        assertSame(bad, MelonState.safeToLoad(bad))
    }

    @Test
    fun `every state load goes through it`() {
        val view = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText()
        assertTrue("LibretroDroid.unserializeState(MelonState.safeToLoad(data))" in view)
    }
}
