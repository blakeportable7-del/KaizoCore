package com.ironmonone.pc

import com.ironmonone.app.DiskWriter
import com.ironmonone.pcui.Samples
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A run's marks, notes and encounters in its own folder, kept across a restart (PLAN row 5). */
class RunDataTest {
    @Test
    fun `marks and notes are written to the run's folder and come back with it`() {
        val d = TestFiles.tempDir("rundata")
        try {
            val a = RunData(d)
            a.cycleMark(245, 0)
            a.cycleMark(245, 0)
            a.cycleMark(245, 5)
            a.setNote(245, "Surf, Mind Reader\nfast")
            assertTrue(DiskWriter.drain(), "the marks were written")
            // KaizoCore PC started again, the same run's game loaded: the folder is found by the ROM's hash (SessionTest).
            val b = RunData(d)
            assertEquals(listOf(2, 0, 0, 0, 0, 1), b.marks.of(245).toList())
            assertEquals("Surf, Mind Reader fast", b.note(245), "a note is one line, as on the phone")
            assertTrue(File(d, "marks.txt").isFile)
        } finally { d.deleteRecursively() }
    }

    @Test
    fun `a battle counts the opponent once and records what it used`() {
        val d = TestFiles.tempDir("rundata2")
        try {
            val r = RunData(d)
            val battle = Samples.enemy
            repeat(5) { r.observe(battle, null) }
            r.observe(Samples.party, null)
            assertEquals(1, r.marks.encounters(245, wild = false), "one trainer battle, read five times")
            assertEquals(42, r.marks.lastLevelSeen(245))
            assertTrue(r.version > 0)
        } finally { d.deleteRecursively() }
    }

    @Test
    fun `a note survives panel_txt's one line`() {
        val s = "line one\nline two \\ and a backslash\r\n"
        assertEquals(s, PanelOut.unescape(PanelOut.escape(s)))
        assertTrue('\n' !in PanelOut.escape(s))
    }
}
