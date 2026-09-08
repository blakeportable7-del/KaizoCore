package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertTrue

/** RandomBallScreen.lua rolls 1..3 once per tracker start; every roll is one of the three. */
class RandomBallTest {
    @Test
    fun `the roll is left, middle or right and fixed for the tracker's life`() {
        val r = NdsMemoryReader { _, _ -> ByteArray(0) }
        val seen = HashSet<Int>()
        repeat(60) {
            val t = NdsTracker(r, null, NdsGameMap.B2W2)
            assertTrue(t.randomBall in 1..3)
            assertTrue(t.randomBall == t.randomBall)
            seen += t.randomBall
        }
        assertTrue(seen.size > 1, "sixty rolls landed on more than one ball")
    }
}
