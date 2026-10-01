package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Auto swap to enemy" turns the view to the opponent each time it sends out a new Pokemon
 * (Battle.lua:309-316), not only when the battle starts. Until 2026-09-29 it swapped once.
 */
class AutoSwapTest {
    @Test
    fun `a party slot on the field that changes Pokemon is a new opponent`() {
        assertTrue(opponentSentOut(listOf(0), listOf(1)), "the first fainted, the second came out")
        assertTrue(opponentSentOut(listOf(0, 1), listOf(0, 2)), "the right-hand battler of a double battle")
        assertFalse(opponentSentOut(listOf(0), listOf(0)))
        assertFalse(opponentSentOut(emptyList(), listOf(0)), "the battle's start: its own swap covers it")
        assertFalse(opponentSentOut(listOf(0), emptyList()), "the slot unreadable for a poll")
    }

    @Test
    fun `the panel swaps to the opponent when it sends one out`() {
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertTrue(Regex("LaunchedEffect\\(state\\.enemyOnField\\) \\{\\s*if \\(TrackerOptions\\.autoSwapToEnemy\\(gameBoy = generation < 3\\) && opponentSentOut\\(enemySlots, state\\.enemyOnField\\)\\) viewingOwn = false").containsMatchIn(src))
    }
}
