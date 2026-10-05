package com.ironmonone.app

import android.view.MotionEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Swipe to scroll a locked, see-through floating window; a tap still reaches the game (Blake, 2026-10-04, on rc35.1's
 * arrows: "I wish I could just scroll up or down to reveal the enemy and my pokemon"). The filter runs here as it runs in
 * the activity, with a fake clock; what the activity passes on is what Compose then sends to a button or the game.
 */
class WindowSwipeTest {
    private val slop = 16f

    /** A filter wired to lists: what went on, what was thrown away, how far the window scrolled, and a clock. */
    private inner class Rig {
        val sent = ArrayList<String>()
        val dropped = ArrayList<String>()
        var scrolled = 0f
        var clock = 0L
        val timers = ArrayList<Pair<Long, () -> Unit>>()
        val target = WindowSwipe.Target(100f, 100f, 400f, 600f) { dy -> scrolled += dy }
        val filter = WindowSwipe.Filter<String>(slop, deliver = { sent += it }, drop = { dropped += it },
            later = { ms, run -> timers += (clock + ms) to run }, now = { clock })

        /** The activity's call: null = it goes on as usual (and so is "sent" here too). */
        fun touch(name: String, action: Int, x: Float, y: Float, t: WindowSwipe.Target? = target): Boolean? {
            val r = filter.onTouch({ name }, action, x, y, t)
            if (r == null) sent += name
            return r
        }

        fun advance(ms: Long) {
            clock += ms
            val due = timers.filter { it.first <= clock }
            timers.removeAll(due)
            due.forEach { it.second() }
        }
    }

    @Test
    fun `a move past the slop is a swipe, anything less that lifts is a tap`() {
        val c = WindowSwipe.Classifier(slop)
        c.down(10f, 10f)
        assertEquals(WindowSwipe.Kind.PENDING, c.move(10f, 20f))
        assertEquals(WindowSwipe.Kind.PENDING, c.move(18f, 20f), "a wobble inside the slop")
        assertEquals(WindowSwipe.Kind.TAP, c.up())
        c.down(10f, 10f)
        assertEquals(WindowSwipe.Kind.SWIPE, c.move(10f, 30f), "down the window")
        assertEquals(WindowSwipe.Kind.SWIPE, c.move(10f, 10f), "a swipe stays a swipe, even back where it began")
        assertEquals(WindowSwipe.Kind.SWIPE, c.up())
        c.down(0f, 0f)
        assertEquals(WindowSwipe.Kind.SWIPE, c.move(0f, -17f), "up the window")
    }

    @Test
    fun `a drag scrolls the window and the game never sees any of it`() {
        val r = Rig()
        assertEquals(true, r.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f))
        assertEquals(true, r.touch("m1", MotionEvent.ACTION_MOVE, 200f, 390f))
        assertEquals(0f, r.scrolled, "inside the slop it waits")
        assertEquals(true, r.touch("m2", MotionEvent.ACTION_MOVE, 200f, 370f))
        assertEquals(-30f, r.scrolled, "past the slop the window moves with the finger, from where it went down")
        r.touch("m3", MotionEvent.ACTION_MOVE, 200f, 250f)
        assertEquals(-150f, r.scrolled)
        r.touch("m4", MotionEvent.ACTION_MOVE, 200f, 300f)
        assertEquals(-100f, r.scrolled, "and back")
        assertEquals(true, r.touch("up", MotionEvent.ACTION_UP, 200f, 300f))
        r.advance(1000)
        assertTrue(r.sent.isEmpty(), "nothing went on: ${r.sent}")
        assertEquals(listOf("down"), r.dropped, "the held press is thrown away")
        // The next touch outside the window is the game's at once.
        assertNull(r.touch("down2", MotionEvent.ACTION_DOWN, 20f, 20f))
        assertNull(r.touch("up2", MotionEvent.ACTION_UP, 20f, 20f))
        assertEquals(listOf("down2", "up2"), r.sent)
    }

    @Test
    fun `a tap on the window goes on as a press and a lift, held long enough for the game to read`() {
        val r = Rig()
        r.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f)
        r.clock += 20
        r.touch("m", MotionEvent.ACTION_MOVE, 205f, 404f)
        assertTrue(r.sent.isEmpty(), "held until it is known")
        r.clock += 10
        r.touch("up", MotionEvent.ACTION_UP, 205f, 404f)
        assertEquals(listOf("down"), r.sent, "the press goes on at the lift")
        r.advance(WindowSwipe.MIN_PRESS_MS - 30 - 1)
        assertEquals(listOf("down"), r.sent, "the lift waits for the shortest press")
        r.advance(1)
        assertEquals(listOf("down", "up"), r.sent)
        assertEquals(0f, r.scrolled)
        // A slow tap's lift goes on at once.
        val s = Rig()
        s.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f)
        s.clock += 120
        s.touch("up", MotionEvent.ACTION_UP, 200f, 400f)
        assertEquals(listOf("down", "up"), s.sent)
    }

    @Test
    fun `a button keeps its tap and its held press`() {
        // The activity passes the tap on whole; Compose sends it to the button under it, as before (no column takes it).
        val r = Rig()
        r.touch("down", MotionEvent.ACTION_DOWN, 380f, 120f)
        r.clock += 60
        r.touch("up", MotionEvent.ACTION_UP, 380f, 120f)
        assertEquals(listOf("down", "up"), r.sent)
        // Held still (the swap's long press, a d-pad held under the window): the press goes on at HOLD_MS, the rest after it.
        val h = Rig()
        h.touch("down", MotionEvent.ACTION_DOWN, 380f, 120f)
        h.advance(WindowSwipe.HOLD_MS - 1)
        assertTrue(h.sent.isEmpty())
        h.advance(1)
        assertEquals(listOf("down"), h.sent)
        assertNull(h.touch("m", MotionEvent.ACTION_MOVE, 380f, 160f), "a held press that then moves is the game's or the button's")
        assertNull(h.touch("up", MotionEvent.ACTION_UP, 380f, 160f))
        assertEquals(listOf("down", "m", "up"), h.sent)
        assertEquals(0f, h.scrolled)
    }

    @Test
    fun `nothing registered, outside the window, or a second finger, and the touch goes on as before`() {
        val r = Rig()
        assertNull(r.touch("a", MotionEvent.ACTION_DOWN, 200f, 400f, t = null))
        assertNull(r.touch("b", MotionEvent.ACTION_MOVE, 200f, 300f, t = null))
        assertEquals(0f, r.scrolled)
        assertNull(r.touch("c", MotionEvent.ACTION_DOWN, 50f, 400f), "beside the window")
        assertNull(r.touch("d", MotionEvent.ACTION_UP, 50f, 400f))
        val two = Rig()
        two.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f)
        assertNull(two.touch("p", MotionEvent.ACTION_POINTER_DOWN, 200f, 400f))
        assertEquals(listOf("down", "p"), two.sent, "what was held goes on first")
        // A cancel throws the held press away.
        val c = Rig()
        c.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f)
        assertEquals(true, c.touch("x", MotionEvent.ACTION_CANCEL, 200f, 400f))
        assertTrue(c.sent.isEmpty()); assertEquals(listOf("down"), c.dropped)
        // A new touch before a tap's lift went on sends the lift first, so no press is ever left down.
        val q = Rig()
        q.touch("down", MotionEvent.ACTION_DOWN, 200f, 400f)
        q.touch("up", MotionEvent.ACTION_UP, 200f, 400f)
        q.touch("down2", MotionEvent.ACTION_DOWN, 20f, 20f)
        assertEquals(listOf("down", "up", "down2"), q.sent)
        q.advance(1000)
        assertEquals(listOf("down", "up", "down2"), q.sent, "and only once")
    }

    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun code(name: String) = strip(File(src, name).readText())
    private fun strip(text: String) = text.replace("\r\n", "\n")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @Test
    fun `the activity runs every touch through it, and the window registers only while taps pass through`() {
        assertTrue("override fun dispatchTouchEvent(ev: MotionEvent): Boolean = WindowSwipe.dispatch(this, ev) { super.dispatchTouchEvent(it) }" in code("MainActivity.kt"))
        val all = src.listFiles { f -> f.extension == "kt" }!!
        assertEquals(listOf("TrackerScroll.kt"), all.filter { "WindowSwipe.target =" in strip(it.readText()) }.map { it.name }.sorted())
        val scroll = code("TrackerScroll.kt")
        assertTrue("if (swipe) TrackerScroll(modifier, background, content) else SwipeColumn(modifier, background, content)" in scroll)
        assertTrue("if (WindowSwipe.target === target[0]) WindowSwipe.target = null" in scroll, "it lets go when it leaves")
        assertTrue("swipe = FloatingSeeThrough.swipeScrolls(locked, solid)" in code("FloatingTracker.kt"))
    }

    @Test
    fun `no up or down arrow buttons anywhere`() {
        // Blake: "The see foe and see mine is still great, just not those 2 up and down arrows".
        val all = src.walkTopDown().filter { it.extension == "kt" }.toList()
        for (f in all) {
            val t = strip(f.readText())
            assertFalse("ArrowScroll" in t || "More above" in t || "Scroll up\"" in t, "${f.name} draws a scroll arrow")
        }
        val scroll = code("TrackerScroll.kt")
        assertFalse("up = true" in scroll || "rotationZ" in scroll, "no arrow turned over")
        // The docked column keeps its one sign that there is more below (rc35); the window's pass-through column has none.
        assertEquals(1, Regex("""\bMoreBelow\(Modifier""").findAll(scroll).count())
        assertTrue("if (scroll.canScrollForward) MoreBelow(" in scroll)
        assertFalse("MoreBelow" in scroll.substringAfter("private fun SwipeColumn").substringBefore("private fun MoreBelow"))
        // The swap stays exactly as it was.
        assertTrue("parts?.swap?.let { Box(Modifier.overhang()) { SwapIconButton(it) } }" in code("FloatingTracker.kt"))
    }
}
