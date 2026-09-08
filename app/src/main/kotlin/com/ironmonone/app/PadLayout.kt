package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.util.Properties

/**
 * Where the on-screen controls sit, per orientation and console, and how
 * the DS screens are arranged. The layout editor (FILE > EDIT LAYOUT) edits
 * this; the pad renders from it; the defaults reproduce the layouts the app
 * shipped with, so an untouched install looks exactly as before.
 *
 * Positions are the element's CENTRE as a fraction of the pad area (0..1
 * each way), so the same layout survives a different screen size. Scale is
 * per element, opacity is for the whole pad and only matters in landscape,
 * where the pad floats over the game.
 */
data class PadLayout(
    val places: Map<Element, Place>,
    val opacity: Float = 0.55f,
    /** melonDS screen layout name for this orientation, e.g. "hybrid-top". */
    val dsLayout: String? = null,
    /** melonDS screen gap in pixels: one of DS_GAPS. */
    val dsGap: Int = 0,
    /**
     * DS: X, Y, A and B are one diamond, spaced in dp, not in fractions of the
     * area. Blake, 2026-09-08, on a tablet: "x y a b are too spread out on ds".
     * Fractions grow with the screen; a diamond does not. With this on, the A
     * place is the diamond's CENTRE and X, Y and B sit one button up, left and
     * down from it (their own places are ignored); A's scale sizes all four.
     * The editor drags the four as one.
     */
    val abxyDiamond: Boolean = false,
) {
    enum class Element(val label: String) {
        DPAD("D-pad"), A("A"), B("B"), L("L"), R("R"), SELECT("Select"), START("Start"),
        /** DS only. A layout that lacks them does not draw them. */
        X("X"), Y("Y");
    }

    data class Place(val x: Float, val y: Float, val scale: Float = 1f) {
        fun moved(dx: Float, dy: Float) = copy(x = (x + dx).coerceIn(0.04f, 0.96f), y = (y + dy).coerceIn(0.04f, 0.96f))
        fun scaled(by: Float) = copy(scale = (scale * by).coerceIn(0.6f, 1.8f))
    }

    operator fun get(e: Element): Place = places[e] ?: DEFAULT_PLACE

    /** The diamond's members, which the editor moves together. */
    fun inDiamond(e: Element) = abxyDiamond && e in DIAMOND

    fun with(e: Element, p: Place) = copy(places = places + (e to p))

    companion object {
        val DEFAULT_PLACE = Place(0.5f, 0.5f)
        val DIAMOND = setOf(Element.X, Element.Y, Element.A, Element.B)
        val DS_LAYOUTS = listOf("top-bottom", "bottom-top", "left-right", "right-left",
            "hybrid-top", "hybrid-bottom", "top", "bottom", "rotate-left", "rotate-right")
        val DS_GAPS = listOf(0, 16, 32, 64, 128)

        /** The shipped landscape layout: the D-pad at the left thumb, A/B and Select/Start at the right. */
        // The landscape pad area is the GAME COLUMN beside the tracker, about
        // 550dp on a phone, not the whole window. Spacings are chosen for that
        // width: B to A 0.14 (77dp, circles are 56), Select to Start 0.20
        // (110dp, pills are 96). On a wider column they spread, never collide.
        val LANDSCAPE = PadLayout(mapOf(
            Element.DPAD to Place(0.18f, 0.68f),
            Element.B to Place(0.78f, 0.60f), Element.A to Place(0.92f, 0.60f),
            Element.SELECT to Place(0.70f, 0.88f), Element.START to Place(0.92f, 0.88f), // SELECT was 0.72: the clamp pushed START onto it on a 500dp column
            Element.L to Place(0.08f, 0.14f), Element.R to Place(0.94f, 0.14f),
        ), opacity = 0.55f, dsLayout = "hybrid-top")

        /** The shipped portrait band: D-pad left, Select/Start over L/R in the middle, A/B diagonal right. */
        // Measured against a 411dp-wide phone: the 180dp D-pad cluster centred
        // at 0.23 spans 5..185dp, the middle column at 0.60 starts at 213dp,
        // so nothing overlaps the right arrow. Fractions scale on wider phones.
        val PORTRAIT = PadLayout(mapOf(
            Element.DPAD to Place(0.23f, 0.50f),
            Element.SELECT to Place(0.60f, 0.34f), Element.START to Place(0.60f, 0.56f),
            Element.L to Place(0.53f, 0.82f), Element.R to Place(0.68f, 0.82f),
            Element.A to Place(0.91f, 0.30f), Element.B to Place(0.85f, 0.66f), // B was 0.80 and touched R on a 360dp phone
        ), opacity = 1f, dsLayout = "top-bottom")

        /** The app's original GBA pads, kept for old layout files; no longer the default. */
        fun legacy(landscape: Boolean) = if (landscape) LANDSCAPE else PORTRAIT

        /**
         * The shipped defaults since 2026-09-08: the emulator layouts (My Boy for
         * GBA and GB, SuperNDS for DS), not the app's original pads. They used to be
         * chips in EDIT LAYOUT that nobody would find, so every phone still showed
         * the old controls with X sitting on A in DS portrait. The original pads
         * stay as presets for a layout file that predates this.
         */
        fun default(landscape: Boolean, nds: Boolean) =
            if (nds) (if (landscape) SUPERNDS_LANDSCAPE else SUPERNDS_PORTRAIT) else myBoy(landscape)

        /**
         * 2.1, DS: the layout of the most-downloaded DS emulator on Google Play,
         * SuperNDS (com.supernds.free, 5M+ on 2026-09-07), measured off Blake's
         * landscape screenshot of it on 2026-09-07: the two screens side by side, the
         * cross low at the left, X Y A B in a diamond at the right, L and R pills at
         * the top corners, START and SELECT along the bottom (its TOUCH toggle has no
         * counterpart here: the bottom screen is always touchable). Drawn with this
         * app's own shapes. Portrait waits on a portrait screenshot.
         */
        val SUPERNDS_LANDSCAPE = PadLayout(mapOf(
            Element.DPAD to Place(0.11f, 0.79f),
            // The diamond is a button wide and a button tall each way; tighter than that and
            // the hit areas sit on each other on a 500x300 column, whatever the circles look like.
            // A is the diamond's centre (abxyDiamond); X, Y and B are placed from it in dp.
            Element.A to Place(0.87f, 0.76f, 0.75f),
            Element.X to Place(0.87f, 0.60f, 0.75f), Element.Y to Place(0.78f, 0.76f, 0.75f), Element.B to Place(0.87f, 0.92f, 0.75f),
            Element.L to Place(0.07f, 0.08f), Element.R to Place(0.94f, 0.08f),
            Element.START to Place(0.40f, 0.94f), Element.SELECT to Place(0.64f, 0.94f),
        // dsLayout null: the screens fit themselves to the column (NdsScreens.autoLayout). On a
        // tablet-shaped column "left-right" drew two small screens in a black field (Blake's
        // screenshot, 2026-09-08); stacked they are a third bigger.
        ), opacity = 0.55f, dsLayout = null, abxyDiamond = true)

        /**
         * SuperNDS in portrait, laid into this app's 192dp control band under the
         * stacked screens: the cross at the left, X Y A B in a diamond at the right,
         * L and R pills in the top corners (L in the cross's empty corner cell),
         * START and SELECT as two short pills along the bottom between them.
         * Blake's portrait screenshot has not arrived; the element set and their
         * corners are the landscape shot's, only the band is ours. Every rectangle
         * is checked apart by PadLayoutTest over 360..480dp phones.
         */
        val SUPERNDS_PORTRAIT = PadLayout(mapOf(
            Element.DPAD to Place(0.23f, 0.50f),
            Element.A to Place(0.86f, 0.55f, 0.7f), // the diamond's centre
            Element.X to Place(0.86f, 0.31f, 0.7f), Element.Y to Place(0.75f, 0.55f, 0.7f), Element.B to Place(0.86f, 0.79f, 0.7f),
            Element.L to Place(0.08f, 0.12f, 0.9f), Element.R to Place(0.95f, 0.10f, 0.9f),
            Element.SELECT to Place(0.43f, 0.88f), Element.START to Place(0.63f, 0.88f),
        ), opacity = 1f, dsLayout = "top-bottom", abxyDiamond = true)

        /**
         * 2.1: the layout of the most-downloaded GBA emulator on Google Play, My Boy!
         * (com.fastemulator.gbafree, 50M+ installs on 2026-09-07; the paid listing
         * com.fastemulator.gba is the same layout at 1M+). Positions were measured off
         * its store screenshots, as fractions of the control area, and drawn with this
         * app's own shapes (the OUTLINE skin): none of its artwork is used.
         *
         * Landscape: D-pad low left, A low right with B up and to its left on the
         * console's diagonal, L and R as pills at mid height on each edge, SELECT and
         * START as two small pills at the bottom centre.
         * Portrait: the controls sit in a band under the game; L and R at the top
         * corners, the D-pad at the left, A above B stacked at the right, SELECT and
         * START at the bottom centre.
         */
        val MYBOY_LANDSCAPE = PadLayout(mapOf(
            Element.DPAD to Place(0.13f, 0.78f),
            // B a full button up and left of A, the pills a full pill apart: on a 500dp game
            // column 0.10 of width is 50dp, less than a 60dp button (PadGeometryTest).
            Element.B to Place(0.80f, 0.78f), Element.A to Place(0.94f, 0.90f),
            Element.L to Place(0.06f, 0.45f), Element.R to Place(0.94f, 0.45f),
            Element.SELECT to Place(0.40f, 0.94f), Element.START to Place(0.60f, 0.94f),
        ), opacity = 0.55f, dsLayout = "hybrid-top")
        val MYBOY_PORTRAIT = PadLayout(mapOf(
            Element.DPAD to Place(0.23f, 0.56f),
            // L in the cross's empty top-left cell, SELECT and START right of its down arrow: on a
            // 360dp phone the cross reaches x 180, so 0.14 and 0.40 sat on the arrows (PadGeometryTest).
            Element.L to Place(0.08f, 0.12f, 0.9f), Element.R to Place(0.86f, 0.12f, 0.9f),
            Element.A to Place(0.86f, 0.40f), Element.B to Place(0.86f, 0.72f),
            Element.SELECT to Place(0.43f, 0.88f), Element.START to Place(0.63f, 0.88f),
        ), opacity = 1f, dsLayout = "top-bottom")
        fun myBoy(landscape: Boolean) = if (landscape) MYBOY_LANDSCAPE else MYBOY_PORTRAIT

        /** One file per orientation and console. */
        fun key(landscape: Boolean, platform: Platform) =
            (if (landscape) "landscape" else "portrait") + "-" + platform.name.lowercase()
    }
}

/** Layouts on disk: prep/layouts/<key>.properties. Missing or corrupt = the default. */
class LayoutStore(private val dir: File) {
    init { dir.mkdirs() }

    private fun file(key: String) = File(dir, "$key.properties")

    fun load(key: String, landscape: Boolean): PadLayout {
        val d = PadLayout.default(landscape, nds = key.endsWith("-nds"))
        val p = runCatching { Properties().apply { file(key).inputStream().use { load(it) } } }.getOrNull() ?: return d
        val places = PadLayout.Element.entries.filter { it in d.places }.associateWith { e ->
            val x = p.getProperty("${e.name}.x")?.toFloatOrNull()
            val y = p.getProperty("${e.name}.y")?.toFloatOrNull()
            val s = p.getProperty("${e.name}.scale")?.toFloatOrNull() ?: d[e].scale
            if (x == null || y == null || x !in 0f..1f || y !in 0f..1f) d[e]
            else PadLayout.Place(x, y, s.coerceIn(0.6f, 1.8f))
        }
        return PadLayout(
            places,
            opacity = p.getProperty("opacity")?.toFloatOrNull()?.coerceIn(0.15f, 1f) ?: d.opacity,
            dsLayout = p.getProperty("dsLayout")?.takeIf { it in PadLayout.DS_LAYOUTS } ?: d.dsLayout,
            dsGap = p.getProperty("dsGap")?.toIntOrNull()?.takeIf { it in PadLayout.DS_GAPS } ?: d.dsGap,
            abxyDiamond = p.getProperty("abxyDiamond")?.toBooleanStrictOrNull() ?: d.abxyDiamond,
        )
    }

    fun save(key: String, l: PadLayout) {
        val p = Properties()
        l.places.forEach { (e, pl) ->
            p.setProperty("${e.name}.x", pl.x.toString()); p.setProperty("${e.name}.y", pl.y.toString())
            p.setProperty("${e.name}.scale", pl.scale.toString())
        }
        p.setProperty("opacity", l.opacity.toString())
        l.dsLayout?.let { p.setProperty("dsLayout", it) }
        p.setProperty("dsGap", l.dsGap.toString())
        p.setProperty("abxyDiamond", l.abxyDiamond.toString())
        runCatching {
            val f = file(key); val tmp = File(dir, f.name + ".tmp")
            tmp.outputStream().use { p.store(it, null) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    fun reset(key: String) { file(key).delete() }
}

/**
 * The pad's real geometry in dp, the one source for FreePad's sizes and for the
 * test that keeps the controls apart. Blake, 2026-09-08: "the buttons for the
 * controls should never overlap each other." Positions are fractions of the
 * area and sizes are dp, so two controls that read as apart in fractions can
 * still sit on each other on a 192dp band; only rectangles tell.
 */
object PadGeometry {
    /** A, B, X, Y: a 56dp disc; the d-pad's arrows are the same disc in a 60dp cell (the 2dp padding each side). */
    const val BUTTON = 56f
    const val PAD = 2f
    /** L and R: a pill on the drawn skins, the classic 36dp square. */
    fun shoulder(skin: PadSkin): Pair<Float, Float> = if (skin == PadSkin.CLASSIC) 36f to 36f else 48f to 30f
    /** SELECT and START: the 96x44 landscape pill, or the portrait band's short bar. */
    fun selectStart(landscape: Boolean, skin: PadSkin): Pair<Float, Float> =
        if (landscape) 96f to 44f else 64f to (if (skin == PadSkin.MODERN) 26f else 32f)

    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun meets(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
    }

    /**
     * Every rectangle the pad places for [layout] on an area of [areaW] x [areaH] dp,
     * clamped inside the area exactly as FreePad clamps them. The d-pad is its two
     * bars (the column and the row of the cross), so its empty corners are free
     * for a shoulder pill.
     */
    /** An element's size in dp for its scale. */
    fun size(e: PadLayout.Element, s: Float, landscape: Boolean, skin: PadSkin): Pair<Float, Float> = when (e) {
        PadLayout.Element.DPAD -> (BUTTON + 2 * PAD) * 3 * s to (BUTTON + 2 * PAD) * 3 * s
        PadLayout.Element.L, PadLayout.Element.R -> shoulder(skin).let { (it.first + 2 * PAD) * s to (it.second + 2 * PAD) * s }
        PadLayout.Element.SELECT, PadLayout.Element.START -> selectStart(landscape, skin).let { (it.first + 2 * PAD) * s to (it.second + 2 * PAD) * s }
        else -> (BUTTON + 2 * PAD) * s to (BUTTON + 2 * PAD) * s
    }

    /** The scale an element draws at: a diamond member takes A's. */
    fun scaleOf(layout: PadLayout, e: PadLayout.Element, baseScale: Float): Float =
        (if (layout.inDiamond(e)) layout[PadLayout.Element.A].scale else layout[e].scale) * baseScale

    /**
     * Where an element's centre lands in dp, clamped inside the area the way
     * FreePad clamps it. A diamond member is one button from A's centre, and the
     * diamond is clamped as a unit so it keeps its shape at the edge.
     */
    fun centre(layout: PadLayout, e: PadLayout.Element, areaW: Float, areaH: Float, landscape: Boolean, skin: PadSkin, baseScale: Float = 1f): Pair<Float, Float> {
        val s = scaleOf(layout, e, baseScale)
        val (w, h) = size(e, s, landscape, skin)
        if (layout.inDiamond(e)) {
            val a = layout[PadLayout.Element.A]
            val d = (BUTTON + 2 * PAD) * s
            val half = w / 2
            val cx = (a.x * areaW).coerceIn(d + half, (areaW - d - half).coerceAtLeast(d + half))
            val cy = (a.y * areaH).coerceIn(d + half, (areaH - d - half).coerceAtLeast(d + half))
            return when (e) {
                PadLayout.Element.X -> cx to cy - d
                PadLayout.Element.Y -> cx - d to cy
                PadLayout.Element.A -> cx + d to cy
                else -> cx to cy + d
            }
        }
        val p = layout[e]
        val l = (p.x * areaW - w / 2).coerceIn(0f, (areaW - w).coerceAtLeast(0f))
        val t = (p.y * areaH - h / 2).coerceIn(0f, (areaH - h).coerceAtLeast(0f))
        return l + w / 2 to t + h / 2
    }

    fun rects(layout: PadLayout, areaW: Float, areaH: Float, landscape: Boolean, skin: PadSkin, baseScale: Float = 1f): Map<PadLayout.Element, List<Box>> {
        val out = LinkedHashMap<PadLayout.Element, List<Box>>()
        for (e in PadLayout.Element.entries) {
            if (e !in layout.places) continue
            val s = scaleOf(layout, e, baseScale)
            val (w, h) = size(e, s, landscape, skin)
            val (cx, cy) = centre(layout, e, areaW, areaH, landscape, skin, baseScale)
            val l = cx - w / 2
            val t = cy - h / 2
            out[e] = if (e == PadLayout.Element.DPAD) {
                val c = w / 3
                listOf(Box(l + c, t, l + 2 * c, t + h), Box(l, t + c, l + w, t + 2 * c))
            } else listOf(Box(l, t, l + w, t + h))
        }
        return out
    }

    /** The pairs of controls whose rectangles touch, empty when the layout is clean. */
    fun overlaps(layout: PadLayout, areaW: Float, areaH: Float, landscape: Boolean, skin: PadSkin, baseScale: Float = 1f): List<Pair<PadLayout.Element, PadLayout.Element>> {
        val r = rects(layout, areaW, areaH, landscape, skin, baseScale).entries.toList()
        val out = ArrayList<Pair<PadLayout.Element, PadLayout.Element>>()
        for (i in r.indices) for (j in i + 1 until r.size)
            if (r[i].value.any { a -> r[j].value.any { b -> a.meets(b) } }) out += r[i].key to r[j].key
        return out
    }
}
