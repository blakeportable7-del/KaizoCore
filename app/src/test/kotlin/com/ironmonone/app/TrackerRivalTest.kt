package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #130: the reference keeps Tracker.Data.whichRival with the run's tracked data, and the Gen 3 tracker
 * here is made again on every visit to Play, so after a restart all three rivals were listed and counted again. The run
 * keeps it with its notes (StatMarks), and TrackerRival hands it between the two.
 */
class TrackerRivalTest {
    private val dir = Files.createTempDirectory("rival").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private val fr = GameMap.FIRERED_U_V10

    /** FireRed memory with the lab battle against [trainer] on the field: two polls make its data readable. */
    private fun labBattle(trainer: Int): GbaTracker {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, v: Long, n: Int) { for (i in 0 until n) ram[a + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        put(fr.battlersCount, 2, 1); put(fr.battleOutcome, 0, 1)
        put(fr.battleMainFunc, fr.handleTurnAction, 4); put(fr.battleTypeFlags, 0x8, 4)
        put(fr.battleMons, 4, 2); put(fr.battleMons + fr.battleMonSize, 7, 2)
        put(fr.trainerOpponent, trainer.toLong(), 2)
        val t = GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, fr)
        t.read(); t.read()
        return t
    }

    @Test
    fun `the rival is kept with the run's notes, and goes with them on New Run`() {
        val f = File(dir, "marks.txt")
        val marks = StatMarks(f)
        assertNull(marks.gbaRival())
        marks.noteGbaRival("Left")
        assertEquals("Left", StatMarks(f).gbaRival(), "the next visit to Play reads it back")
        StatMarks(f).clear()
        assertNull(StatMarks(f).gbaRival(), "New Run drops it, as the reference's resetData does")
    }

    @Test
    fun `a rival learned in battle is kept, and a new tracker is given it back`() {
        val marks = StatMarks(File(dir, "marks.txt"))
        val battled = labBattle(328)
        assertEquals("Right", battled.rivalChoice)
        TrackerRival.sync(battled, marks)
        assertEquals("Right", marks.gbaRival())
        // The next visit's tracker, before any rival battle.
        val next = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, fr)
        assertNull(next.rivalChoice)
        TrackerRival.sync(next, marks)
        assertEquals("Right", next.rivalChoice)
        assertEquals(listOf(328), next.trainersForRoute(5), "Oak's Lab lists the run's rival only")
    }

    @Test
    fun `a tracker still up when New Run clears the notes does not carry its rival into the next run`() {
        val marks = StatMarks(File(dir, "marks.txt"))
        val t = labBattle(327)
        TrackerRival.sync(t, marks)
        assertEquals("Left", marks.gbaRival())
        marks.clear()
        TrackerRival.sync(t, marks)
        assertNull(marks.gbaRival(), "only a rival a battle just taught it is written")
    }

    @Test
    fun `Play's side screens keep it in step, and Heals in Bag works on the card's Pokemon`() {
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertTrue("androidx.compose.runtime.SideEffect { TrackerRival.sync(trackerRef, statMarks) }" in side)
        // rc32 audit P2 #136: not an Egg in slot 1 (TrackerState.onField, through GbaViewState.own, which follows a double
        // battle's view, rc34).
        assertTrue("gba?.healsInBag(gbaView.own(trackerState))" in side)
        assertTrue("healsInBag(trackerState?.party?.firstOrNull())" !in side)
    }
}
