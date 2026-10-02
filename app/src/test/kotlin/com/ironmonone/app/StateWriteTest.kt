package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A failed save must leave the last good save and say so, never throw (audit, 2026-09-27). */
class StateWriteTest {
    @Test
    fun `a write that fails keeps the previous save and returns a sentence`() {
        val dir = Files.createTempDirectory("slots").toFile()
        val f = File(dir, "state1.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        // A directory where the .tmp should go makes the write fail, as a full disk does.
        File(dir, "state1.bin.tmp").mkdir()
        val err = StateSlots.writeAtomic(f, byteArrayOf(9, 9, 9, 9))
        assertNotNull(err)
        assertContentEquals(byteArrayOf(1, 2, 3), f.readBytes(), "the last good save is untouched")
        assertFalse(File(dir, "state1.bin.tmp").exists(), "the half-written .tmp is gone")
    }

    /**
     * rc33 audit P0-2: two saves to one slot at once (two quick taps, big DS states) shared the .tmp; the loser's
     * rename failed, its fallback deleted the slot the winner had just written, and it still reported success.
     */
    @Test
    fun `saves racing to one slot never lose it and never claim a save that did not happen`() {
        val dir = Files.createTempDirectory("slots").toFile()
        val f = File(dir, "state1.bin").apply { writeBytes(ByteArray(4) { 7 }) }
        val payloads = (0 until 8).map { t -> ByteArray(256 * 1024) { (t + 1).toByte() } }
        val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()
        repeat(10) {
            val start = java.util.concurrent.CountDownLatch(1)
            val threads = payloads.map { p ->
                Thread { start.await(); StateSlots.writeAtomic(f, p)?.let(errors::add) }.apply { start() }
            }
            start.countDown()
            threads.forEach { it.join() }
            kotlin.test.assertTrue(f.exists(), "the slot is still there")
            val now = f.readBytes()
            kotlin.test.assertTrue(payloads.any { it.contentEquals(now) }, "the slot holds one whole save")
        }
        kotlin.test.assertTrue(errors.isEmpty(), "every write said what happened: $errors")
        assertFalse(File(dir, "state1.bin.tmp").exists())
    }

    @Test
    fun `a replace that fails says so and leaves what was there`() {
        val dir = Files.createTempDirectory("slots").toFile()
        // The slot's name taken by a non-empty folder: the rename cannot replace it. The old code deleted the
        // target (which failed here), tried the rename again and returned null, "Saved".
        val f = File(dir, "state3.bin").apply { mkdir(); File(this, "keep").writeText("x") }
        assertNotNull(StateSlots.writeAtomic(f, byteArrayOf(1, 2)))
        kotlin.test.assertTrue(File(f, "keep").exists())
        assertFalse(File(dir, "state3.bin.tmp").exists())
    }

    @Test
    fun `a normal write replaces the save`() {
        val dir = Files.createTempDirectory("slots").toFile()
        val f = File(dir, "state2.bin").apply { writeBytes(byteArrayOf(1)) }
        assertNull(StateSlots.writeAtomic(f, byteArrayOf(4, 5)))
        assertContentEquals(byteArrayOf(4, 5), f.readBytes())
    }
}
