package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The PC tracker's move header counts only moves LEARNED, never the ones a
 * Pokemon starts with. Levels below are read out of a real FireRed ROM.
 */
class LearnedMovesTest {
    @Test
    fun `Blake's randomized Lv5 Flaaffy starter reads 0 of 5, next at 9`() {
        // The randomizer guaranteed four starting moves, so four Lv.1 entries;
        // the learn levels themselves are vanilla. His tracker read "4/9 (9)".
        val rom = listOf(1, 1, 1, 1, 9, 18, 27, 36, 45)
        assertEquals(LearnedMoves.Header(0, 5, 9), LearnedMoves.of(rom, 5))
    }

    @Test
    fun `vanilla Flaaffy matches the reference movelvls`() {
        // ROM [1,1,1,9,18,27,36,45]; the reference lists {9,18,27,36,45}.
        assertEquals(LearnedMoves.Header(0, 5, 9), LearnedMoves.of(listOf(1, 1, 1, 9, 18, 27, 36, 45), 5))
    }

    @Test
    fun `a starter is not always 0 - Bulbasaur learns Growl at Lv4`() {
        // ROM [1,4,7,...]; the reference lists {4,7,...} and shows "Moves 1/10 (7)".
        val rom = listOf(1, 4, 7, 10, 15, 15, 20, 25, 32, 39, 46)
        assertEquals(LearnedMoves.Header(1, 10, 7), LearnedMoves.of(rom, 5))
    }

    @Test
    fun `every move learned leaves no next level`() {
        assertEquals(LearnedMoves.Header(5, 5, null), LearnedMoves.of(listOf(1, 1, 9, 18, 27, 36, 45), 50))
    }

    @Test
    fun `a Pokemon with only starting moves learns nothing`() {
        assertEquals(LearnedMoves.Header(0, 0, null), LearnedMoves.of(listOf(1, 1), 30))
    }
}
