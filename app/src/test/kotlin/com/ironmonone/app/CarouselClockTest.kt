package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #45: the tracker kept redrawing while nothing on it changed. The carousel recomposed ten times a
 * second all session on a 100 ms ticker, and every Walking Pals icon redrew each display frame though a sheet frame
 * lasts several. The carousel now wakes only when something is due, and an icon redraws only when its frame moves.
 */
class CarouselClockTest {
    @Test
    fun `the clock sleeps until the rotation is due`() {
        // Shown at 1000 for 3500 ms: the carousel rotates once more than 3500 ms have passed, at 4501.
        assertEquals(3501L, CarouselClock.wait(now = 1000, shownSince = 1000, ms = 3500, trainersUntil = 0, rotation = true))
        assertEquals(1L, CarouselClock.wait(now = 4500, shownSince = 1000, ms = 3500, trainersUntil = 0, rotation = true))
    }

    @Test
    fun `TRAINERS ending first wakes it sooner`() {
        assertEquals(2000L, CarouselClock.wait(now = 3000, shownSince = 1000, ms = 7000, trainersUntil = 5000, rotation = true))
        assertEquals(2000L, CarouselClock.wait(now = 3000, shownSince = 1000, ms = 7000, trainersUntil = 5000, rotation = false), "with rotation off, TRAINERS still ends")
    }

    @Test
    fun `nothing due is no wake at all`() {
        assertNull(CarouselClock.wait(now = 3000, shownSince = 1000, ms = 3500, trainersUntil = 0, rotation = false), "rotation off, no trainers")
        assertNull(CarouselClock.wait(now = 9000, shownSince = 1000, ms = 3500, trainersUntil = 5000, rotation = true), "both passed: the draw that follows handles them")
        assertNull(CarouselClock.wait(now = 3000, shownSince = 0, ms = 3500, trainersUntil = 0, rotation = true), "not shown yet")
    }

    @Test
    fun `the carousel has no ticker, and the clock is keyed on what it waits for`() {
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText().replace("\r\n", "\n")
        val carousel = pc.substringAfter("fun PcCarousel(").substringBefore("\n}\n")
        assertFalse("delay(100)" in carousel, "the 100 ms ticker is gone")
        assertTrue("LaunchedEffect(index, shownSince, ms, trainersUntil, TrackerOptions.allowCarouselRotation)" in carousel)
        assertTrue("CarouselClock.wait(now.value, shownSince, ms, trainersUntil, TrackerOptions.allowCarouselRotation) ?: break" in carousel)
    }

    @Test
    fun `an icon's animation follows SpriteData's rules`() {
        assertEquals(WalkingPals.Anim.SLEEP, palAnim(afk = true, status = "", walk = true), "idle 55 seconds: asleep, walking or not")
        assertEquals(WalkingPals.Anim.FAINT, palAnim(afk = false, status = "FNT", walk = true))
        assertEquals(WalkingPals.Anim.SLEEP, palAnim(afk = false, status = "SLP", walk = false))
        assertEquals(WalkingPals.Anim.WALK, palAnim(afk = false, status = "", walk = true))
        assertEquals(WalkingPals.Anim.IDLE, palAnim(afk = false, status = "PSN", walk = false))
    }

    @Test
    fun `an icon writes its frame only when it changes, and draws only what it wrote`() {
        val src = File("src/main/kotlin/com/ironmonone/app/WalkingPals.kt").readText().replace("\r\n", "\n")
        val icon = src.substringAfter("fun WalkingPalsIcon(").substringBefore("\n}\n")
        assertFalse("tick = (" in icon, "no state written every display frame")
        assertTrue("if (f != shown) shown = f" in icon, "the frame is written when it moves")
        val draw = icon.substringAfter("Canvas(Modifier.size(boxDp)) {")
        assertTrue("val (anim, index, row) = shown ?: return@Canvas" in draw, "the drawing reads the frame written")
        assertFalse("SpriteMotion." in draw, "and works nothing out itself, so it has no reason to run again")
        // The idle and walking checks are part of the frame written, or a sprite would wake or walk a frame late.
        val loop = icon.substringAfter("LaunchedEffect(pal) {").substringBefore("Canvas(")
        assertTrue("SpriteMotion.lastInputNanos" in loop && "SpriteMotion.walking()" in loop && "SpriteMotion.facingRow()" in loop)
    }
}
