package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** StatMarkingScoreSheet's rules: ranges with the 25 margin, the ability exceptions, scoring and the letter. */
class ScoreSheetTest {
    @Test
    fun `ranges, margin and ability exceptions`() {
        assertTrue(ScoreSheet.accurate("+", 90, 0, 0))     // 115 - 25
        assertFalse(ScoreSheet.accurate("+", 89, 0, 0))
        assertTrue(ScoreSheet.accurate("--", 95, 0, 0))    // 70 + 25
        assertTrue(ScoreSheet.accurate("=", 45, 0, 0))
        assertFalse(ScoreSheet.accurate("=", 44, 0, 0))
        assertTrue(ScoreSheet.accurate("+", 50, 1, 37))    // Huge Power doubles Attack
        assertFalse(ScoreSheet.accurate("+", 50, 1, 0))
        assertTrue(ScoreSheet.accurate("+", 60, 1, 55))    // Hustle x1.5 = 90
        assertTrue(ScoreSheet.accurate("+", 50, 4, 47))    // Thick Fat: doubled Sp. Def counts too
        assertTrue(ScoreSheet.accurate("--", 50, 4, 47))   // and so does the plain one
        assertEquals('A', ScoreSheet.letter(90.0)); assertEquals('B', ScoreSheet.letter(85.0)); assertEquals('C', ScoreSheet.letter(70.0)); assertEquals('D', ScoreSheet.letter(69.9))
    }

    @Test
    fun `scoring, order and percentage`() {
        val f = File.createTempFile("marks", ".txt"); f.deleteOnExit()
        val marks = StatMarks(f)
        marks.setAll(1, intArrayOf(1, 0, 0, 0, 0, 1))   // HP + on 45: wrong; Speed ignored
        marks.setAll(4, intArrayOf(0, 3, 0, 0, 0, 0))   // ATK = on 52: right
        val base = mapOf(1 to BaseStats(45, 49, 49, 45, 65, 65, 12, 3, 65, 0), 4 to BaseStats(39, 52, 43, 65, 60, 50, 10, 10, 66, 0))
        val r = ScoreSheet.build(marks, { base[it] }, { "#$it" })
        assertEquals(2, r.total); assertEquals(1, r.great); assertEquals(1, r.poor)
        assertEquals("50.0", r.percentage); assertEquals('D', r.letter)
        assertEquals(listOf(1, 4), r.rows.map { it.species })   // worst first
        assertEquals(-10, r.rows[0].score); assertEquals(1, r.rows[1].score)
        assertEquals(5, r.rows[0].cells.size)
    }
}
