package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateSlotsTest {

    @Test
    fun `eight slots, the first three on the pre-existing paths, thumbnails beside them`() {
        val filesDir = Files.createTempDirectory("files").toFile()
        val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)
        val slots = StateSlots.list(filesDir, run)
        assertEquals(StateSlots.COUNT, slots.size)
        assertEquals(File(filesDir, "saves/state1.bin"), slots[0].file)
        assertEquals(File(filesDir, "saves/state3.bin"), slots[2].file)
        assertEquals(File(filesDir, "saves/state8.png"), slots[7].thumb)
        assertTrue(slots.none { it.exists })
        assertNull(StateSlots.latest(slots))
        assertEquals("empty", slots[0].savedLabel())
    }

    @Test
    fun `latest is the most recently written slot, and an empty file does not count`() {
        val filesDir = Files.createTempDirectory("files").toFile()
        val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)
        val slots = StateSlots.list(filesDir, run)
        slots[1].file.parentFile.mkdirs()
        slots[1].file.writeBytes(ByteArray(10)); slots[1].file.setLastModified(1_000_000L)
        slots[4].file.writeBytes(ByteArray(10)); slots[4].file.setLastModified(2_000_000L)
        slots[6].file.writeBytes(ByteArray(0))
        assertFalse(slots[6].exists)
        assertEquals(5, StateSlots.latest(slots)?.n)
        assertTrue(slots[1].savedLabel() != "empty")
    }
}
