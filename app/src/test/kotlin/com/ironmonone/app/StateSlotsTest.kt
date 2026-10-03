package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    /** rc33 audit P1: UNDO brought back the old state but kept the stamp of the save that overwrote it. */
    @Test
    fun `undo brings back the old state's own run stamp`() {
        val dir = java.nio.file.Files.createTempDirectory("undo").toFile()
        val slot = StateSlots.Slot(1, java.io.File(dir, "state1.bin"), java.io.File(dir, "state1.id"), java.io.File(dir, "state1.png"))
        slot.file.writeText("seed A state"); slot.stamp.writeText("emerald-u/aaaa")
        slot.keepBackup()
        slot.file.writeText("seed B state"); slot.stamp.writeText("emerald-u/bbbb")
        kotlin.test.assertTrue(slot.restoreBackup())
        kotlin.test.assertEquals("seed A state", slot.file.readText())
        kotlin.test.assertEquals("emerald-u/aaaa", slot.stamp.readText(), "the old state with its own stamp")
        kotlin.test.assertEquals("emerald-u/bbbb", slot.backupStamp.readText(), "and the swapped-out one keeps its")
        // A backup kept before rc33 has no stamp: the restored state is unstamped, which a load refuses.
        slot.backupStamp.delete()
        kotlin.test.assertTrue(slot.restoreBackup())
        kotlin.test.assertFalse(slot.stamp.exists())
    }

    /** rc32 audit P2 #62: UNDO copied a state three times on the main thread, and a kill part way tore the slot. */
    @Test
    fun `undo swaps by renames off the main thread, and a swap a kill cut short is put back`() {
        val filesDir = Files.createTempDirectory("undo2").toFile()
        val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)
        val slot = StateSlots.list(filesDir, run)[1]
        slot.file.parentFile.mkdirs()
        slot.file.writeText("old"); slot.thumb.writeText("old png"); slot.stamp.writeText("emerald-u/aa")
        slot.keepBackup()
        slot.file.writeText("new"); slot.thumb.writeText("new png"); slot.stamp.writeText("emerald-u/bb")
        assertEquals("Slot 2: previous state restored.", kotlinx.coroutines.runBlocking { StateSlots.undo(slot) })
        assertEquals("old", slot.file.readText()); assertEquals("new", slot.backup.readText())
        assertEquals("old png", slot.thumb.readText()); assertEquals("new png", slot.backupThumb.readText())
        assertEquals("emerald-u/aa", slot.stamp.readText()); assertEquals("emerald-u/bb", slot.backupStamp.readText())
        assertTrue(slot.file.parentFile.listFiles()!!.none { it.name.endsWith(".swap") })
        // A kill after the slot's file went aside: the next listing puts it back.
        java.nio.file.Files.move(slot.file.toPath(), File(slot.file.parentFile, slot.file.name + ".swap").toPath())
        assertFalse(slot.exists)
        assertEquals("old", StateSlots.list(filesDir, run)[1].file.readText())
        // A kill after the backup came in: the listing finishes the swap.
        java.nio.file.Files.move(slot.file.toPath(), File(slot.file.parentFile, slot.file.name + ".swap").toPath())
        java.nio.file.Files.move(slot.backup.toPath(), slot.file.toPath())
        StateSlots.list(filesDir, run)
        assertEquals("new", slot.file.readText()); assertEquals("old", slot.backup.readText())
        assertEquals("Nothing to undo.", kotlinx.coroutines.runBlocking { StateSlots.undo(StateSlots.list(filesDir, run)[4]) })
        val src = File("src/main/kotlin/com/ironmonone/app/StateSlots.kt").readText().replace("\r\n", "\n")
        val swap = src.substring(src.indexOf("fun restoreBackup()"), src.indexOf("fun recoverSwap()"))
            .lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/") }.joinToString("\n")
        assertFalse("copyTo" in swap, "a swap by renames")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("onUndo = { s -> scope.launch { status = StateSlots.undo(s); slotsVersion++ } }," in play)
    }

    /** rc32 audit P3 #54: loadState's run check, untested, refused another run's state with no test behind it. */
    @Test
    fun `a load is refused for another run, an unknown run or no stamp, in that order`() {
        val here = "emerald-u/00000000000000aa"
        assertNull(StateSlots.loadRefusal(1, here, here))
        assertEquals("Slot 1 is from a different run, so it was not loaded.", StateSlots.loadRefusal(1, "emerald-u/00000000000000bb", here))
        assertEquals("Slot 1 is from a different run, so it was not loaded.", StateSlots.loadRefusal(1, "emerald-u/?", "emerald-u/?"), "two unknowns never match")
        assertEquals("Slot 1 is from an older version, so it was not loaded.", StateSlots.loadRefusal(1, null, here))
        assertEquals("Slot 1 is from a different run, so it was not loaded.", StateSlots.loadRefusal(1, "x", "?/?"), "a mismatch is said before an unknown run")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("StateSlots.loadRefusal(which, runCatching { slotStamp(which).readText().trim() }.getOrNull(), store.stateStamp(session))?.let { status = it; return }" in play)
    }

    /** rc32 audit P3 #54 and P2 #51: the battery save's one writer. */
    @Test
    fun `the battery writer keeps a good save from an empty buffer and from an older read`() {
        val f = File(Files.createTempDirectory("srm").toFile(), "emerald-u.srm")
        f.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(StateSlots.writeSram(f, ByteArray(0), readAt = 10))
        assertContentEquals(byteArrayOf(1, 2, 3), f.readBytes(), "an empty buffer writes nothing")
        assertNull(StateSlots.writeSram(f, byteArrayOf(4), readAt = 20))
        assertNull(StateSlots.writeSram(f, byteArrayOf(5), readAt = 15))
        assertContentEquals(byteArrayOf(4), f.readBytes(), "a flush queued behind a pause never puts the older save back")
        assertNull(StateSlots.writeSram(f, byteArrayOf(6), readAt = 30))
        assertContentEquals(byteArrayOf(6), f.readBytes())
    }

    /** rc32 audit P3 #54: the run's record of loads, undos, restores and retries had no test reaching its writers. */
    @Test
    fun `every load, undo, restore and retry goes on the run's record`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("if (ok) store.runEvents(session)?.add(RunEvents.Kind.LOAD, slotName, \"saved \${f.lastModified()}\")" in play)
        assertTrue("if (undone) store.runEvents(session)?.add(RunEvents.Kind.UNDO, slotName)" in play)
        assertTrue("if (ok) store.runEvents(session)?.add(RunEvents.Kind.RESTORE, rp.label, \"made \${rp.timestamp}\")" in play)
        val host = File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertTrue("if (ok) { latch.retried(); store.runEvents(session)?.add(RunEvents.Kind.RETRY, \"battle start\"); RunHistoryHook.retried(store, session) }" in host)
        assertTrue("StateSlots.writeSram(sramFile(), bytes)?.let { SaveTrouble.report(SaveTrouble.BATTERY, it) }" in play)
    }

    /** rc32 audit P3 #63: RESUME was lit for the last run's auto-save and then refused it as "Slot 0". */
    @Test
    fun `RESUME is offered only for this run's own auto-save`() {
        val dialog = File("src/main/kotlin/com/ironmonone/app/SaveStatesDialog.kt").readText()
        assertTrue("val autoUsable = remember(session.id, version) { CrashResume.usable(auto, runStamp()) }" in dialog)
        assertTrue("Gen3Button(\"RESUME\", accent = autoUsable, enabled = autoUsable) { onLoad(StateSlots.AUTO) }" in dialog)
        assertTrue("\" · from another run\"" in dialog)
        val filesDir = Files.createTempDirectory("resume").toFile()
        val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)
        val auto = StateSlots.auto(filesDir, run)
        auto.file.parentFile.mkdirs(); auto.file.writeBytes(byteArrayOf(1)); auto.stamp.writeText("emerald-u/00000000000000aa")
        assertTrue(CrashResume.usable(auto, "emerald-u/00000000000000aa"))
        assertFalse(CrashResume.usable(auto, "emerald-u/00000000000000bb"), "the last run's slot after a new run")
    }
}
