package com.ironmonone.app

import kotlin.math.max
import kotlin.math.min

/**
 * Pinch and drag maths for the FireRed and LeafGreen picture viewer (2026-09-29).
 *
 * Pure numbers, no Compose, so the JVM tests can drive it: the gestures in FrlgPictureViewer only hand
 * it a pinch centre, a pan and a zoom factor. A picture's place is [View]: how many screen pixels each
 * picture pixel takes, and where the picture's top left corner sits on the screen.
 */
object PanZoom {
    data class View(val scale: Float, val x: Float, val y: Float)

    /** The most a picture is enlarged: this many screen pixels for each of its pixels. */
    const val MAX_PIXEL = 12f
    /** ... and never more than this many times the size it started at, so a big map stays a map. */
    const val MAX_ZOOM = 8f
    /** A double tap on a picture zooms to this many times its starting size. */
    const val TAP_ZOOM = 3f

    /** The scale at which the whole picture fits the view. */
    fun fitScale(imageW: Float, imageH: Float, viewW: Float, viewH: Float): Float =
        if (imageW <= 0f || imageH <= 0f || viewW <= 0f || viewH <= 0f) 1f
        else min(viewW / imageW, viewH / imageH)

    /**
     * The largest scale for a picture that fits at [fit]. A 240 px screenshot fits a phone at about 4.5, so
     * it may go to 12 (nearly 3 times); a 1000 px map fits at about 0.4, so it may go 8 times, to about 3.
     */
    fun maxScale(fit: Float): Float = max(fit * 2f, min(fit * MAX_ZOOM, MAX_PIXEL))

    /** The picture fitted to the view and centred: where it starts, and where a double tap sends it back to. */
    fun fitted(imageW: Float, imageH: Float, viewW: Float, viewH: Float): View {
        val s = fitScale(imageW, imageH, viewW, viewH)
        return View(s, (viewW - imageW * s) / 2f, (viewH - imageH * s) / 2f)
    }

    /** One axis of the picture's position: centred while it is smaller than the view, else never showing a gap. */
    fun clampAxis(pos: Float, size: Float, view: Float): Float =
        if (size <= view) (view - size) / 2f else pos.coerceIn(view - size, 0f)

    /**
     * One step of a pinch and drag. The point of the picture under ([cx], [cy]) stays under it while
     * [zoom] changes the scale, then [panX] and [panY] move the picture; the scale stays between the
     * fitted size and [maxScale], and the picture never leaves a gap at an edge.
     */
    fun transform(
        v: View, imageW: Float, imageH: Float, viewW: Float, viewH: Float,
        cx: Float, cy: Float, panX: Float, panY: Float, zoom: Float,
    ): View {
        val fit = fitScale(imageW, imageH, viewW, viewH)
        val s = (v.scale * zoom).coerceIn(fit, maxScale(fit))
        val k = s / v.scale
        val nx = cx - (cx - v.x) * k + panX
        val ny = cy - (cy - v.y) * k + panY
        return View(s, clampAxis(nx, imageW * s, viewW), clampAxis(ny, imageH * s, viewH))
    }

    /** A double tap: back to the fitted picture when it is enlarged, else in on the tapped point. */
    fun doubleTap(v: View, imageW: Float, imageH: Float, viewW: Float, viewH: Float, tapX: Float, tapY: Float): View {
        val fit = fitScale(imageW, imageH, viewW, viewH)
        if (v.scale > fit * 1.05f) return fitted(imageW, imageH, viewW, viewH)
        val target = min(fit * TAP_ZOOM, maxScale(fit))
        return transform(v, imageW, imageH, viewW, viewH, tapX, tapY, 0f, 0f, target / v.scale)
    }

    /**
     * Pixel art is drawn with the nearest pixel while it is enlarged, so a zoomed screenshot stays crisp.
     * Shrunk (a 1000 px map on a phone) nearest-pixel drops most of the picture, so it is smoothed then.
     */
    fun nearestPixel(scale: Float): Boolean = scale >= 1f
}
