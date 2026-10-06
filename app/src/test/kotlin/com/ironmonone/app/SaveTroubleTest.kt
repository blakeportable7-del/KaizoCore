package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P0-8: on a full phone the battery save, the auto-save and the Nuzlocke ledger stopped reaching the disk
 * in silence. Each now says so (SaveTrouble), the battery save goes through the slot writer, and SafeWrite cleans up
 * after itself and never deletes the file it was replacing.
 */
class SaveTroubleTest {
    @AfterTest fun reset() = SaveTrouble.forget()

    @Test
    fun `a failure is shown once a minute per kind, not on every pause`() {
        assertTrue(SaveTrouble.shouldShow(SaveTrouble.BATTERY, 1_000))
        assertFalse(SaveTrouble.shouldShow(SaveTrouble.BATTERY, 30_000))
        assertTrue(SaveTrouble.shouldShow(SaveTrouble.AUTO, 30_000), "another save's failure is its own")
        assertTrue(SaveTrouble.shouldShow(SaveTrouble.BATTERY, 1_000 + SaveTrouble.QUIET_MS))
        assertFalse('\u2014' in SaveTrouble.LEDGER_FAILED)
    }

    @Test
    fun `SafeWrite that cannot write keeps the old file`() {
        val dir = Files.createTempDirectory("sw").toFile()
        val f = File(dir, "attempts.txt").apply { writeText("7") }
        // The write cannot happen, as on a full disk. A folder in the .tmp's place is not ours and stays.
        val blocked = File(dir, "attempts.txt.tmp").apply { mkdir() }
        assertFalse(SafeWrite.text(f, "8"))
        assertEquals("7", f.readText())
        assertTrue(blocked.isDirectory)
    }

    @Test
    fun `SafeWrite whose replace fails says so and deletes nothing`() {
        val dir = Files.createTempDirectory("sw").toFile()
        // The file's name taken by a folder with something in it: the old code deleted (failed), renamed (failed) and
        // left the .tmp; the replace now fails cleanly.
        val f = File(dir, "history.tsv").apply { mkdir(); File(this, "keep").writeText("x") }
        assertFalse(SafeWrite.text(f, "new"))
        assertTrue(File(f, "keep").exists())
        assertFalse(File(dir, "history.tsv.tmp").exists())
        val ok = File(dir, "ok.txt").apply { writeText("a") }
        assertTrue(SafeWrite.text(ok, "b")); assertEquals("b", ok.readText())
    }

    @Test
    fun `the three silent writers now report`() {
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        val play = src("PlayScreen.kt")
        val persist = play.substring(play.indexOf("    fun persistSram() {"), play.indexOf("    fun newRun(move: Boolean = false) {"))
        assertTrue("StateSlots.writeSram(sramFile(), bytes)?.let { SaveTrouble.report(SaveTrouble.BATTERY, it) }" in persist)
        assertFalse("renameTo" in persist, "the battery save no longer renames by hand")
        assertTrue("write(bytes, capturedAt, stamp, leaving)?.let { SaveTrouble.report(SaveTrouble.AUTO, it) }" in src("AutoSave.kt"))
        assertTrue("SaveTrouble.report(SaveTrouble.LEDGER, SaveTrouble.LEDGER_FAILED)" in src("NuzlockeStore.kt"))
        assertTrue("SaveTrouble.init(this)" in src("MainActivity.kt"))
    }
}
