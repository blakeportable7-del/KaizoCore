package com.ironmonone.app

/**
 * Where a tap on the game opens the FILE bar (Blake, 2026-10-05: "screen tap, tapped at the upper 33% of the play
 * screen opens the semi transparent bar"). Only the upper third of the picture the player looks at does: the lower two
 * thirds are the game's, a tap on the DS touch screen is always play, the black bars beside the picture are nothing,
 * and a tap within [DEAD_DP] of an on-screen control, while one is held or just after one is let go, is a missed button
 * press, never the menu. Plain arithmetic in the game view's own pixels, so the tests run every console and layout.
 *
 * The game view draws the core's frame fitted and centred in it (LibretroDroid), or in its viewport where the DS dock
 * set one (DsDock); the DS's two screens sit in that frame as melonDS lays them out (ScreenTap.frame).
 */
object TapZone {
    /** Room round a control where a tap is a missed button press, never the menu (the prototype's 16 dp). */
    const val DEAD_DP = 16f
    /** A tap this soon after a button is let go is a fast thumb, not the menu. */
    const val AFTER_PAD_MS = 250L
    /** The share of the picture, from its top, that opens the bar. */
    const val SHARE = 1f / 3f

    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        fun contains(x: Float, y: Float) = x >= l && x < r && y >= t && y < b
        fun grown(by: Float) = Rect(l - by, t - by, r + by, b + by)
        fun meets(o: Rect) = l < o.r && o.l < r && t < o.b && o.t < b
    }

    /** The core's frame: Game Boy 160 by 144, Game Boy Advance 240 by 160, a DS layout as ScreenTap lays it out. */
    sealed interface Console {
        data class Flat(val w: Float, val h: Float) : Console
        data class Ds(val layout: ScreenTap.DsLayout, val topOnly: Boolean) : Console
    }

    val GBA = Console.Flat(240f, 160f)
    val GB = Console.Flat(160f, 144f)

    /** The frame fitted and centred in [area], as LibretroDroid draws it. */
    fun fit(fw: Float, fh: Float, area: Rect): Rect {
        if (fw <= 0f || fh <= 0f || area.w <= 0f || area.h <= 0f) return Rect(area.l, area.t, area.l, area.t)
        val s = minOf(area.w / fw, area.h / fh)
        val w = fw * s
        val h = fh * s
        val l = area.l + (area.w - w) / 2f
        val t = area.t + (area.h - h) / 2f
        return Rect(l, t, l + w, t + h)
    }

    /**
     * The DS's non-touch screen in the frame of [l], in the frame's pixels; null where the layout draws only the touch
     * screen. melonDS's hybrid frame is the big screen at the ratio, then a column holding one small screen.
     */
    fun dsTopScreen(l: ScreenTap.DsLayout): Rect? {
        val w = 256f; val h = 192f
        val g = l.gap.coerceIn(0, 126).toFloat()
        val ratio = l.hybridRatio.coerceIn(2, 3)
        val r = ratio.toFloat()
        return when (l.name) {
            "bottom-top" -> Rect(0f, h + g, w, h * 2 + g)
            "left-right" -> Rect(0f, 0f, w, h)
            "right-left" -> Rect(w, 0f, w * 2, h)
            "hybrid-top" -> Rect(0f, 0f, w * r, h * r)
            // The big screen is the touch screen; the column's small one at its top is the top screen.
            "hybrid-bottom" -> { val x = w * r + (ratio / 2); Rect(x, 0f, x + w, h) }
            "top" -> Rect(0f, 0f, w, h)
            "bottom" -> null
            else -> Rect(0f, 0f, w, h)   // "top-bottom" and the core's fallback
        }
    }

    /**
     * The picture the player looks at, in the view's pixels: the whole frame, or on a DS its top screen. [viewport] is
     * the view's viewport in fractions (DsDock); null is the whole view. With [Console.Ds.topOnly] Play shows the frame
     * at twice the size about the view's top centre (the "1 screen" view); a touch on the view arrives in the view's own
     * untransformed pixels, so the zone is worked out there, where the visible part is the middle half across and the
     * top half down.
     */
    fun picture(console: Console, viewW: Float, viewH: Float, viewport: Rect? = null): Rect? {
        val area = viewport?.let { Rect(it.l * viewW, it.t * viewH, it.r * viewW, it.b * viewH) } ?: Rect(0f, 0f, viewW, viewH)
        return when (console) {
            is Console.Flat -> fit(console.w, console.h, area)
            is Console.Ds -> {
                val f = ScreenTap.frame(console.layout)
                val p = fit(f.width, f.height, area)
                val s = if (f.width > 0f) p.w / f.width else 0f
                val top = dsTopScreen(console.layout) ?: return null
                var r = Rect(p.l + top.l * s, p.t + top.t * s, p.l + top.r * s, p.t + top.b * s)
                if (console.topOnly) {
                    val seen = Rect(viewW / 4f, 0f, viewW * 3f / 4f, viewH / 2f)
                    r = Rect(maxOf(r.l, seen.l), maxOf(r.t, seen.t), minOf(r.r, seen.r), minOf(r.b, seen.b))
                    if (r.w <= 0f || r.h <= 0f) return null
                }
                r
            }
        }
    }

    /** The upper third of [picture]: a tap here opens and closes the bar. */
    fun zone(picture: Rect): Rect = Rect(picture.l, picture.t, picture.r, picture.t + picture.h * SHARE)

    /**
     * Whether a tap at ([x], [y]) on the view opens (or closes) the bar. [controls] are the on-screen controls over the
     * game, in the view's pixels; [deadPx] is [DEAD_DP] in pixels; [padHeld] and [sincePadUpMs] say whether a thumb is
     * on the pad or has just left it.
     */
    fun opens(x: Float, y: Float, picture: Rect?, controls: List<Rect>, deadPx: Float, padHeld: Boolean, sincePadUpMs: Long): Boolean {
        val p = picture ?: return false
        if (!zone(p).contains(x, y)) return false
        if (controls.any { it.grown(deadPx).contains(x, y) }) return false
        if (padHeld || sincePadUpMs in 0 until AFTER_PAD_MS) return false
        return true
    }
}
