package com.ironmonone.app

import com.ironmonone.tracker.TrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The GBA tracker reads again soon while it is settling (TrackerPoll; rc35, the slow route line and weather). */
class TrackerPollTest {
    private fun st(settling: Boolean = false, inBattle: Boolean = false) =
        TrackerState(partyCount = 0, party = emptyList(), inBattle = inBattle, isWildBattle = false, settling = settling)

    @Test
    fun `a settling tracker is read again within six frames, otherwise at the old pace`() {
        assertEquals(100L, TrackerPoll.gbaWait(st(settling = true)))
        assertEquals(100L, TrackerPoll.gbaWait(st(settling = true, inBattle = true)))
        assertEquals(250L, TrackerPoll.gbaWait(st(inBattle = true)))
        assertEquals(700L, TrackerPoll.gbaWait(st()))
        assertEquals(700L, TrackerPoll.gbaWait(null))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("} else kotlinx.coroutines.delay(TrackerPoll.gbaWait(trackerState))" in play, "the GBA loop asks TrackerPoll")
    }
}
