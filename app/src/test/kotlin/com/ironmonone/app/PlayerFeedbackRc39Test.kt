package com.ironmonone.app

import android.view.KeyEvent
import com.ironmonone.app.stream.StreamHub
import com.ironmonone.app.stream.StreamSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The players' feedback built for rc39 that a JVM test can hold: the d-pad slide, the stream's nature and log. */
class PlayerFeedbackRc39Test {
    private val cell = 60f

    @Test
    fun `a thumb slid from one arrow onto another presses that one, and the middle changes nothing`() {
        val up = KeyEvent.KEYCODE_DPAD_UP
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, DpadSlide.direction(-cell, 0f, cell, up))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, DpadSlide.direction(cell, 5f, cell, up))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, DpadSlide.direction(3f, cell, cell, up))
        assertEquals(up, DpadSlide.direction(0f, -cell, cell, KeyEvent.KEYCODE_DPAD_DOWN))
        // Within a third of a cell of the centre the arrow held stays held.
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, DpadSlide.direction(5f, 5f, cell, KeyEvent.KEYCODE_DPAD_LEFT))
        // Past the arrows' boxes it still steers: far left is left.
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, DpadSlide.direction(-4 * cell, cell, cell, up))
        // Each arrow knows where it sits on the plus.
        assertEquals(1 to 0, DpadSlide.grid(up))
        assertEquals(0 to 1, DpadSlide.grid(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun `the streamed card carries the nature's plus and minus`() {
        // Modest: +SpA -Atk (nature 15: raised stat 15 / 5 = SPA, lowered 15 % 5 = ATK).
        assertEquals(mapOf("atk" to "-", "spa" to "+"), StreamSnapshot.natureMarks(15))
        // Calm: +SpD -Atk; Hardy is neutral and marks nothing; no nature (Gen 1 and 2) marks nothing.
        assertEquals(mapOf("atk" to "-", "spd" to "+"), StreamSnapshot.natureMarks(20))
        assertEquals(emptyMap(), StreamSnapshot.natureMarks(0))
        assertEquals(emptyMap(), StreamSnapshot.natureMarks(null))
        val page = java.io.File("src/main/assets/stream/tracker.html").readText()
        assertTrue("statsOwn(p.stats,p.nature)" in page, "the page draws it on your card")
    }

    @Test
    fun `opening the log on the phone sends the state again with logOpen`() {
        StreamHub.publish("{\"title\":\"x\",\"logOpen\":false}", 1)
        val v = StreamHub.version
        StreamHub.logOpen = true
        assertTrue("\"logOpen\":true" in StreamHub.state)
        assertEquals(v + 1, StreamHub.version)
        StreamHub.logOpen = false
        assertTrue("\"logOpen\":false" in StreamHub.state)
        val page = java.io.File("src/main/assets/stream/tracker.html").readText()
        assertTrue("S.logOpen" in page, "the run-over view follows it")
    }
}
