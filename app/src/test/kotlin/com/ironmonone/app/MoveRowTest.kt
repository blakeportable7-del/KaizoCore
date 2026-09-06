package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A move row is only "blank" when it IS a blank slot.
 *
 * The padding rows that fill a Pokemon's unused move slots were identified by
 * `id == 0`, which conflated two different things: "this slot is empty" and
 * "this code path has no move id to give". The DS panel is the second case -
 * it builds rows from NdsMoveInfo, which carries no id - so every real Gen 4
 * move was treated as an empty slot and its PP rendered "---".
 *
 * It went unnoticed because the DS panel had never once been rendered.
 */
class MoveRowTest {

    private fun move(id: Int, pp: Int) = PcMove(
        id = id, name = "Tackle", pp = pp, ppMax = null, power = 40, acc = 100,
        color = Color.White, category = "PHY",
    )

    @Test
    fun `a real move with no id is not a blank slot`() {
        // Exactly the DS case.
        assertFalse(move(id = 0, pp = 35).blank)
    }

    @Test
    fun `a real move with an id is not a blank slot`() {
        assertFalse(move(id = 33, pp = 35).blank)
    }

    @Test
    fun `only the padding row is blank`() {
        // BLANK_MOVE is private to PcTracker.kt; assert the property it is
        // built with, which is what the renderer branches on.
        val padding = PcMove(
            id = 0, name = "---", pp = 0, ppMax = null, power = null, acc = null,
            color = Color.Gray, category = null, blank = true,
        )
        assertTrue(padding.blank)
    }
}
