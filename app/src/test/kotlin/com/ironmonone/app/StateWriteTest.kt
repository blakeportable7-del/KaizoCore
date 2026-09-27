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

    @Test
    fun `a normal write replaces the save`() {
        val dir = Files.createTempDirectory("slots").toFile()
        val f = File(dir, "state2.bin").apply { writeBytes(byteArrayOf(1)) }
        assertNull(StateSlots.writeAtomic(f, byteArrayOf(4, 5)))
        assertContentEquals(byteArrayOf(4, 5), f.readBytes())
    }
}
