package com.ironmonone.pc.fx

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An edge round every effect, so it shows on any theme (Blake, 2026-10-10: "Just get an edge or shadow"). The effects
 * keep their own colours (paralysis yellow, poison purple) whatever the card's auto theme is; on a card of the same
 * colour they melted into it. Each card pixel next to an effect's solid stroke, and not itself one, gets an edge: black
 * round a light stroke, white round a dark one, so stroke and edge differ by at least 3:1 (WCAG's line for shapes) on
 * every theme.
 *
 * A solid stroke is what the effects drew on their own layer at half opacity or more. A translucent tint (a stat row's
 * flash) is meant to be soft and gets none, and as an effect fades its edge fades with it. Pieces of the card itself
 * that an effect moves or covers (a stat number lifting off, the card's own fill laid over it) are drawn straight onto
 * the card ([onCard]), not on the layer, so they get no box round them. A near-black stroke is an outline already (the
 * words' own) and makes no edge, so words get no halo.
 */
internal object Edges {
    /** A layer pixel at least this opaque (of 255) is a solid stroke. */
    private const val SOLID = 128
    /** A stroke darker than this is an outline already, and makes no edge. */
    private const val OUTLINE_LUMA = 0.06

    private var buffer: BufferedImage? = null
    /** The card's own graphics while the effects draw on their layer: lifts and covers go there ([onCard]). */
    private var cardTarget: Graphics2D? = null

    /** The solid strokes of the last frame that make an edge, card pixels row by row (FxEdgeContrastTest measures round them). */
    var lastStrokes: BooleanArray? = null
        private set
    /** Every solid pixel of the last frame, the words' dark outlines included. */
    var lastSolid: BooleanArray? = null
        private set

    /** Runs [draw] on the card itself when the effects are drawing on their layer, else on [g]; [g]'s clip and opacity carry over. */
    fun onCard(g: Graphics2D, draw: (Graphics2D) -> Unit) {
        val t = cardTarget ?: return draw(g)
        val clip = t.clip; val comp = t.composite
        t.clip = g.clip; t.composite = g.composite
        try { draw(t) } finally { t.clip = clip; t.composite = comp }
    }

    class Layer internal constructor(val image: BufferedImage, val graphics: Graphics2D) {
        /**
         * Puts the effects on [g], then the edges (when [edges]) round their solid strokes, over [card] drawn at [offset] card
         * pixels: an edge is chosen against a stroke's colour as it shows over the card (a half-faded wash is darker).
         */
        fun finish(g: Graphics2D, s: Double, edges: Boolean, card: BufferedImage, offset: Pair<Double, Double>) {
            graphics.dispose()
            cardTarget = null
            val cw = CardSpots.card.w.toInt(); val ch = CardSpots.card.h.toInt()
            val cs = card.width / CardSpots.card.w
            val stroke = BooleanArray(cw * ch)
            val spawns = BooleanArray(cw * ch)
            val luma = DoubleArray(cw * ch)
            for (y in 0 until ch) for (x in 0 until cw) {
                val px = ((x + 0.5) * s).toInt(); val py = ((y + 0.5) * s).toInt()
                if (px >= image.width || py >= image.height) continue
                val p = image.getRGB(px, py); val a = p ushr 24
                if (a < SOLID) continue
                val i = y * cw + x
                stroke[i] = true
                val c = card.getRGB(((x - offset.first + 0.5) * cs).toInt().coerceIn(0, card.width - 1), ((y - offset.second + 0.5) * cs).toInt().coerceIn(0, card.height - 1))
                val k = a / 255.0
                fun mix(sh: Int) = (((p shr sh) and 255) * k + ((c shr sh) and 255) * (1 - k)).roundToInt()
                luma[i] = 0.2126 * lin(mix(16)) + 0.7152 * lin(mix(8)) + 0.0722 * lin(mix(0))
                spawns[i] = luma[i] >= OUTLINE_LUMA
            }
            lastStrokes = spawns.copyOf(); lastSolid = stroke.copyOf()
            // The effects first, then the edges: an edge cell is never a solid stroke, and drawn after the effects it covers
            // the faint glow round a stroke instead of being washed out by it.
            g.drawImage(image, 0, 0, null)
            if (edges) for (y in 0 until ch) for (x in 0 until cw) {
                if (stroke[y * cw + x]) continue
                // Black or white, whichever keeps the most contrast against the darkest and lightest strokes it touches (a
                // two-tone ring can touch both).
                var lo = 2.0; var hi = -1.0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until cw || ny !in 0 until ch) continue
                    val j = ny * cw + nx
                    if (!spawns[j]) continue
                    lo = minOf(lo, luma[j]); hi = maxOf(hi, luma[j])
                }
                if (hi < 0) continue
                val againstBlack = (lo + 0.05) / 0.05; val againstWhite = 1.05 / (hi + 0.05)
                // Opaque: a stroke counts once it is half opaque, and a half-faded edge would fall under 3:1 against it.
                g.color = if (againstBlack >= againstWhite) Color.BLACK else Color.WHITE
                g.fillRect((x * s).roundToInt(), (y * s).roundToInt(), max(1, ((x + 1) * s).roundToInt() - (x * s).roundToInt()),
                    max(1, ((y + 1) * s).roundToInt() - (y * s).roundToInt()))
            }
        }
    }

    /** A clear layer the size of the card at [s], drawn on as [g] would be (its clip and hints); lifts and covers go to [g]. */
    fun layer(g: Graphics2D, s: Double): Layer {
        val w = ceil(CardSpots.card.w * s).toInt(); val h = ceil(CardSpots.card.h * s).toInt()
        val img = buffer?.takeIf { it.width == w && it.height == h } ?: BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { buffer = it }
        val lg = img.createGraphics()
        lg.composite = AlphaComposite.Clear
        lg.fillRect(0, 0, w, h)
        lg.composite = AlphaComposite.SrcOver
        lg.setRenderingHints(g.renderingHints)
        lg.clip = g.clip
        cardTarget = g
        return Layer(img, lg)
    }

    /** sRGB to linear light, for WCAG's relative luminance. */
    private fun lin(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
}
