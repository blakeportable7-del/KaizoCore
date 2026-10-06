package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The floating window's lock with no lock button (Blake, 2026-10-05: "remove the lock, a long hold would lock in place,
 * and a long hold would show the floating tracker expand corner button"): locked by default, a half-second hold flips
 * it, opening the FILE bar and six seconds untouched lock it. The hold's watch and the lock's rules run for real; where
 * the window draws them is held to the source.
 */
class FloatHoldTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private val window = TapZone.Rect(100f, 50f, 400f, 350f)

    @Test
    fun `half a second, still, on the window fires the hold, and nothing else does`() {
        assertEquals(500L, FloatHold.HOLD_MS)
        assertEquals(6000L, FloatHold.IDLE_MS)
        val w = FloatHold.Watch(slop = 8f)
        // Still for the hold: it fires, and the rest of the touch is swallowed, the lift too.
        var g = w.down(200f, 200f, window)
        assertTrue(w.armed)
        w.move(203f, 204f)
        assertTrue(w.fire(g), "a wobble inside the slop is still a hold")
        assertTrue(w.swallows)
        assertTrue(w.end(), "the lift after a hold is swallowed")
        assertFalse(w.swallows)
        // A tap: lifted before the hold.
        g = w.down(200f, 200f, window); assertFalse(w.end()); assertFalse(w.fire(g), "a tap is not a hold")
        // A drag or a scroll: moved past the slop first.
        g = w.down(200f, 200f, window); w.move(220f, 200f); assertFalse(w.fire(g), "a drag is not a hold"); w.end()
        // Two fingers.
        g = w.down(200f, 200f, window); w.secondFinger(); assertFalse(w.fire(g)); w.end()
        // Off the window, or with no window.
        g = w.down(20f, 20f, window); assertFalse(w.armed); assertFalse(w.fire(g)); w.end()
        g = w.down(200f, 200f, null); assertFalse(w.fire(g)); w.end()
        // The swap button's own long press.
        g = w.down(200f, 60f, window, skip = TapZone.Rect(150f, 50f, 250f, 90f)); assertFalse(w.fire(g)); w.end()
        // A timer from an older touch never fires on a newer one.
        val old = w.down(200f, 200f, window); w.end()
        w.down(200f, 200f, window)
        assertFalse(w.fire(old), "the first touch's timer")
    }

    @Test
    fun `a hold flips the lock, the bar and six seconds lock it, and locked stays locked`() {
        assertFalse(FloatLockRules.afterHold(true), "locked: a hold unlocks")
        assertTrue(FloatLockRules.afterHold(false), "unlocked: a hold locks")
        assertTrue(FloatLockRules.locksForBar(barOpen = true, locked = false))
        assertFalse(FloatLockRules.locksForBar(barOpen = true, locked = true))
        assertFalse(FloatLockRules.locksForBar(barOpen = false, locked = false))
        assertFalse(FloatLockRules.idleLocks(false, touchedAt = 1000L, now = 1000L + 5999L))
        assertTrue(FloatLockRules.idleLocks(false, touchedAt = 1000L, now = 1000L + 6000L))
        assertFalse(FloatLockRules.idleLocks(true, touchedAt = 0L, now = 1_000_000L))
        FileBar.reset()
        assertTrue(FileBar.floatLocked, "locked whenever Play opens")
    }

    @Test
    fun `the window has no lock, gear or menu, and its hold, drag, corner and idle lock are wired`() {
        val ft = read("FloatingTracker.kt")
        for (gone in listOf("LockButton(", "GripDots(", "TrackerGearButton(", "menu(dock)", "TrackerCornerMenu(", "EdgeHandle(", "floatingLocked"))
            assertFalse(gone in ft, "$gone is gone from the window")
        assertTrue("val locked = FileBar.floatLocked" in ft)
        assertTrue("DisposableEffect(Unit) {\n            FileBar.floatLocked = true" in ft, "locked whenever it is shown")
        assertTrue("FloatHold.onHold = {\n                FileBar.floatLocked = FloatLockRules.afterHold(FileBar.floatLocked)" in ft)
        assertTrue("haptics.performHapticFeedback(HapticFeedbackType.LongPress)" in ft, "the buzz")
        assertTrue("LaunchedEffect(FileBar.open) {\n            if (FloatLockRules.locksForBar(FileBar.open, FileBar.floatLocked))" in ft, "the bar locks it")
        assertTrue("FloatLockRules.idleLocks(FileBar.floatLocked, FileBar.floatTouchedAt" in ft, "six seconds untouched lock it")
        // Unlocked: the drag and the corner; locked: neither.
        val unlocked = ft.substringAfter("            if (!locked) {\n                // Unlocked, the window is a thing to move")
        assertTrue("move(live.copy(x = live.x + d.x / density, y = live.y + d.y / density))" in unlocked)
        assertTrue("if (!locked) {\n            // What the hold did" in ft && "FloatGrabs.corner(shown, fitH, areaW, areaH)" in ft)
        assertTrue("move(live.copy(w = (live.w + drag.x / density).coerceAtMost(areaW - FloatGrabs.OUT - live.x)," in ft, "the corner resizes, up to the edge")
        assertEquals(44f, FloatGrabs.CORNER, "a thumb's target")
        // The ring, and the window stepping below the open bar.
        assertTrue("FloatHold.RING_AFTER_MS" in ft && "drawArc(TrackerHud.CYAN" in ft)
        assertTrue("if (FileBar.open) live.copy(y = maxOf(live.y, minOf(FileBar.UNDER_DP.toFloat(), areaH - fitH))) else live" in ft)
        // The activity watches the hold before Compose sees the touch, and before the see-through swipe.
        assertTrue("FloatHold.dispatch(this, ev) { e -> WindowSwipe.dispatch(this, e) { super.dispatchTouchEvent(it) } }" in read("MainActivity.kt"))
    }

    @Test
    fun `the corner button stays on screen`() {
        for ((aw, ah) in listOf(851f to 393f, 900f to 405f, 1280f to 800f)) {
            for (f in listOf(FloatFrame(0f, 0f, 300f, 200f), FloatFrame(aw - 300f - FloatGrabs.OUT, ah - 200f - FloatGrabs.OUT, 300f, 200f), FloatFrame.default(aw, ah))) {
                val (x, y) = FloatGrabs.corner(f.clamped(aw, ah), f.h, aw, ah)
                assertTrue(x >= 0f && y >= 0f && x + FloatGrabs.CORNER <= aw + 0.01f && y + FloatGrabs.CORNER <= ah + 0.01f, "$aw x $ah $f: ($x, $y)")
            }
        }
    }
}
