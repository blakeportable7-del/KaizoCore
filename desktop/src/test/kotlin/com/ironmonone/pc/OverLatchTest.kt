package com.ironmonone.pc

import com.ironmonone.tracker.GameOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The game over card's latch (PLAN row 6): the reference's GameOverScreen.isDisplayed rules, as the app keeps them. */
class OverLatchTest {
    @Test
    fun `fires once, stays through the whiteout's heal, and goes with Continue`() {
        val l = OverLatch()
        assertFalse(l.onRead(null, false) { emptyList() })
        assertFalse(l.onRead(null, true) { emptyList() })
        assertTrue(l.onRead(GameOver.LOST, true) { emptyList() })
        assertFalse(l.onRead(GameOver.LOST, true) { emptyList() }, "the same loss fires once")
        assertNotNull(l.ending)
        assertFalse(l.onRead(null, false) { emptyList() }, "the heal clears the live read, not the card")
        assertNotNull(l.ending)
        l.dismiss()
        assertNull(l.ending)
        // The same battle's read cannot bring it back; the next battle can.
        assertFalse(l.onRead(GameOver.LOST, false) { emptyList() })
        assertTrue(l.onRead(GameOver.LOST, true) { emptyList() })
    }

    @Test
    fun `Retry waits for the first read without a loss, then arms again`() {
        val l = OverLatch()
        assertTrue(l.onRead(GameOver.LOST, true) { emptyList() })
        l.retried()
        assertNull(l.ending)
        assertFalse(l.onRead(GameOver.LOST, true) { emptyList() }, "the stale lost read after the click")
        assertFalse(l.onRead(null, true) { emptyList() }, "the restored battle: armed again, nothing fired")
        assertTrue(l.onRead(GameOver.LOST, true) { emptyList() }, "the replayed battle can end the run again")
        assertEquals(GameOver.LOST, l.ending?.outcome)
    }

    @Test
    fun `a win fires the win card`() {
        val l = OverLatch()
        assertTrue(l.onRead(GameOver.WON, false) { emptyList() })
        assertTrue(l.ending!!.won)
        l.reset()
        assertNull(l.ending)
    }
}
