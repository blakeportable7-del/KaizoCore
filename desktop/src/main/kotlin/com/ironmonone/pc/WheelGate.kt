package com.ironmonone.pc

import kotlin.math.abs
import kotlin.math.sign

/**
 * The mouse wheel, one page a notch and at most one page every [gapMs] (Blake, 2026-10-11: "all the scrolls in the
 * tracker make the scroll go superfast and it looks glitchy and the text flashes"). A smooth-scrolling mouse or a
 * touchpad sends many small events for one notch, and some with no rotation at all; each used to turn a page, and a
 * zero counted as up, so the pages raced and flipped back and forth. Rotation is gathered until it makes a notch, a
 * change of direction starts over, and a notch inside [gapMs] of the last page is dropped.
 */
class WheelGate(private val gapMs: Long = 150) {
    private var gathered = 0.0
    private var lastStep = Long.MIN_VALUE / 2

    /** [rotation] as Java gives it (positive is down, 1.0 a notch). 1 a page down, -1 a page up, null for nothing. */
    fun feed(rotation: Double, nowMs: Long): Int? {
        if (rotation == 0.0 || rotation.isNaN()) return null
        if (gathered != 0.0 && sign(gathered) != sign(rotation)) gathered = 0.0
        gathered += rotation
        if (abs(gathered) < 1.0 - 1e-6) return null
        val dir = if (gathered > 0) 1 else -1
        gathered = 0.0
        if (nowMs - lastStep < gapMs) return null
        lastStep = nowMs
        return dir
    }
}
