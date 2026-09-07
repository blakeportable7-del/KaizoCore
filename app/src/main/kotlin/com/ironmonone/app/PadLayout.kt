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
) {
    enum class Element(val label: String) {
        DPAD("D-pad"), A("A"), B("B"), L("L"), R("R"), SELECT("Select"), START("Start");
    }

    data class Place(val x: Float, val y: Float, val scale: Float = 1f) {
        fun moved(dx: Float, dy: Float) = copy(x = (x + dx).coerceIn(0.04f, 0.96f), y = (y + dy).coerceIn(0.04f, 0.96f))
        fun scaled(by: Float) = copy(scale = (scale * by).coerceIn(0.6f, 1.8f))
    }

    operator fun get(e: Element): Place = places[e] ?: DEFAULT_PLACE

    fun with(e: Element, p: Place) = copy(places = places + (e to p))

    companion object {
        val DEFAULT_PLACE = Place(0.5f, 0.5f)
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
            Element.SELECT to Place(0.72f, 0.88f), Element.START to Place(0.92f, 0.88f),
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
            Element.A to Place(0.91f, 0.30f), Element.B to Place(0.80f, 0.66f),
        ), opacity = 1f, dsLayout = "top-bottom")

        fun default(landscape: Boolean) = if (landscape) LANDSCAPE else PORTRAIT

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
            Element.B to Place(0.84f, 0.82f), Element.A to Place(0.94f, 0.90f),
            Element.L to Place(0.07f, 0.45f), Element.R to Place(0.94f, 0.45f),
            Element.SELECT to Place(0.55f, 0.94f), Element.START to Place(0.64f, 0.94f),
        ), opacity = 0.55f, dsLayout = "hybrid-top")
        val MYBOY_PORTRAIT = PadLayout(mapOf(
            Element.DPAD to Place(0.23f, 0.56f),
            Element.L to Place(0.14f, 0.12f, 0.9f), Element.R to Place(0.86f, 0.12f, 0.9f),
            Element.A to Place(0.86f, 0.40f), Element.B to Place(0.86f, 0.72f),
            Element.SELECT to Place(0.40f, 0.88f), Element.START to Place(0.62f, 0.88f),
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
        val d = PadLayout.default(landscape)
        val p = runCatching { Properties().apply { file(key).inputStream().use { load(it) } } }.getOrNull() ?: return d
        val places = PadLayout.Element.entries.associateWith { e ->
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
        runCatching {
            val f = file(key); val tmp = File(dir, f.name + ".tmp")
            tmp.outputStream().use { p.store(it, null) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    fun reset(key: String) { file(key).delete() }
}
