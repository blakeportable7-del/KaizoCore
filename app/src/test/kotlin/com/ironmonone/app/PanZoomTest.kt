package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The picture viewer's pinch and drag maths (2026-09-29), on the two shapes it draws: a 240 px screenshot and a 1000 px map. */
class PanZoomTest {
    private val phoneW = 1080f
    private val phoneH = 1700f

    @Test
    fun `a screenshot and a map fit the phone at their own scales`() {
        assertEquals(4.5f, PanZoom.fitScale(240f, 160f, phoneW, phoneH), 0.001f)
        assertEquals(1.08f, PanZoom.fitScale(1000f, 1000f, phoneW, phoneH), 0.001f)
        assertEquals(0.36f, PanZoom.fitScale(1000f, 1000f, 360f, 640f), 0.001f)
        // The taller edge decides: a wide picture in a tall view fits its width, a tall one its height.
        assertEquals(0.5f, PanZoom.fitScale(2000f, 500f, 1000f, 1000f), 0.001f)
        assertEquals(0.5f, PanZoom.fitScale(500f, 2000f, 1000f, 1000f), 0.001f)
        assertEquals(1f, PanZoom.fitScale(240f, 160f, 0f, 0f), "no view yet")
    }

    @Test
    fun `the picture starts fitted and centred`() {
        val v = PanZoom.fitted(1000f, 1000f, 360f, 640f)
        assertEquals(0.36f, v.scale, 0.001f)
        assertEquals(0f, v.x, 0.01f)
        assertEquals(140f, v.y, 0.01f)
        val s = PanZoom.fitted(240f, 160f, phoneW, phoneH)
        assertEquals(0f, s.x, 0.01f)
        assertEquals((phoneH - 160f * 4.5f) / 2f, s.y, 0.01f)
    }

    @Test
    fun `how far each shape may be enlarged`() {
        // A 240 px screenshot: 12 screen pixels for each of its pixels, not eight times a 4.5 fit.
        assertEquals(12f, PanZoom.maxScale(4.5f), 0.001f)
        // A 1000 px map on a phone: eight times its fit, about 2.9 screen pixels per pixel, so its small labels can be read.
        assertEquals(2.88f, PanZoom.maxScale(0.36f), 0.001f)
        // Never less than twice the fit, so a big screen can still zoom.
        assertEquals(16f, PanZoom.maxScale(8f), 0.001f)
    }

    @Test
    fun `a pinch keeps the point under the fingers where it was`() {
        val start = PanZoom.fitted(1000f, 1000f, 360f, 640f)
        val cx = 250f
        val cy = 300f
        val pictureX = (cx - start.x) / start.scale
        val pictureY = (cy - start.y) / start.scale
        val zoomed = PanZoom.transform(start, 1000f, 1000f, 360f, 640f, cx, cy, 0f, 0f, 3f)
        assertEquals(1.08f, zoomed.scale, 0.001f)
        assertEquals(pictureX, (cx - zoomed.x) / zoomed.scale, 0.05f)
        assertEquals(pictureY, (cy - zoomed.y) / zoomed.scale, 0.05f)
    }

    @Test
    fun `the scale stays between the fitted size and the most it may be enlarged`() {
        val start = PanZoom.fitted(1000f, 1000f, 360f, 640f)
        assertEquals(0.36f, PanZoom.transform(start, 1000f, 1000f, 360f, 640f, 100f, 100f, 0f, 0f, 0.2f).scale, 0.0001f)
        var v = start
        repeat(10) { v = PanZoom.transform(v, 1000f, 1000f, 360f, 640f, 180f, 320f, 0f, 0f, 2f) }
        assertEquals(PanZoom.maxScale(0.36f), v.scale, 0.0001f)
    }

    @Test
    fun `dragging never leaves a gap at an edge`() {
        val w = 360f; val h = 640f
        var v = PanZoom.transform(PanZoom.fitted(1000f, 1000f, w, h), 1000f, 1000f, w, h, 180f, 320f, 0f, 0f, 3f)
        for ((px, py) in listOf(5000f to 5000f, -5000f to -5000f, 5000f to -5000f, -30f to 40f)) {
            v = PanZoom.transform(v, 1000f, 1000f, w, h, 180f, 320f, px, py, 1f)
            assertTrue(v.x <= 0.001f && v.x + 1000f * v.scale >= w - 0.001f, "horizontal gap: $v")
            // 1000 px at 1.08 is 1080 tall, more than the view, so it has to cover it too.
            assertTrue(v.y <= 0.001f && v.y + 1000f * v.scale >= h - 0.001f, "vertical gap: $v")
        }
    }

    @Test
    fun `a picture smaller than the view stays centred and cannot be dragged off`() {
        // The 240 by 160 screenshot at its fit is smaller than the tall phone view in height.
        val start = PanZoom.fitted(240f, 160f, phoneW, phoneH)
        val moved = PanZoom.transform(start, 240f, 160f, phoneW, phoneH, 500f, 800f, 300f, 700f, 1f)
        assertEquals(start.x, moved.x, 0.01f)
        assertEquals(start.y, moved.y, 0.01f)
    }

    @Test
    fun `a double tap zooms in on the point and a second one goes back`() {
        val start = PanZoom.fitted(1000f, 1000f, 360f, 640f)
        val inn = PanZoom.doubleTap(start, 1000f, 1000f, 360f, 640f, 90f, 200f)
        assertEquals(0.36f * PanZoom.TAP_ZOOM, inn.scale, 0.001f)
        val pictureX = (90f - start.x) / start.scale
        assertEquals(pictureX, (90f - inn.x) / inn.scale, 0.05f)
        assertEquals(start, PanZoom.doubleTap(inn, 1000f, 1000f, 360f, 640f, 90f, 200f))
        // A screenshot that already fits at 4.5 zooms to three times that, but no further than 12.
        val shot = PanZoom.fitted(240f, 160f, phoneW, phoneH)
        assertEquals(12f, PanZoom.doubleTap(shot, 240f, 160f, phoneW, phoneH, 500f, 800f).scale, 0.001f)
    }

    @Test
    fun `pixel art is drawn with the nearest pixel once enlarged and smoothed when shrunk`() {
        assertTrue(PanZoom.nearestPixel(1f))
        assertTrue(PanZoom.nearestPixel(4.5f))
        assertFalse(PanZoom.nearestPixel(0.36f))
    }
}
