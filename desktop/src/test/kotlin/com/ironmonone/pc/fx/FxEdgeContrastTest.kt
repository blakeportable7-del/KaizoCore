package com.ironmonone.pc.fx

import com.ironmonone.app.ThemeCodes
import com.ironmonone.app.ThemeStore
import com.ironmonone.pc.PcHns
import com.ironmonone.pc.TestFiles
import com.ironmonone.pcui.PcInkCheck
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every effect shows on every auto theme (Blake, 2026-10-10: "animations on auto themes, certain theme colors would alter
 * the animations?", then "Just get an edge or shadow"). The effects keep their own colours, so on a theme of the same
 * colour they melted into the card; Edges puts a black or white edge round them.
 *
 * Measured on what the viewer sees: each frame of a scenario that plays most of the effects (statuses, lasting looks,
 * stat changes, an item, a level-up) over the real card, under each auto theme, drawn with no edge and with the edge.
 * An effect's pixels are its solid strokes, the edge pass's own mask (Edges.lastStrokes), so a stroke the colour of
 * the card counts like any other. On its rim, each pixel is set against its neighbour outside the stroke: a frame
 * "melts" when fewer than half of those pairs reach 3:1
 * (WCAG's line for shapes). With the edge no frame may melt; with none, some must, or the test proves nothing.
 */
class FxEdgeContrastTest {
    private val out = File(System.getProperty("contrast.out") ?: "build/contrast-fx").apply { mkdirs() }

    private val scenario = """
        lead Quilava lv 24 hp 70/71 ability Blaze item Charcoal moves Ember, Quick Attack, Smokescreen, Swift
        foe Miltank lv 25 moves Rollout, Milk Drink, Attract, Stomp
        wait 0.4
        foe stage ATK +1
        foe status PAR
        foe leech seed
        wait 0.8
        foe status PSN
        foe spikes 2
        wait 0.8
        view own
        own stage DEF -1
        own status BRN
        own reflect 5
        own use Full Restore
        wait 0.6
        own status SLP
        own confuse
        wait 0.6
        own status FRZ
        levelup
        wait 0.6
    """.trimIndent()

    private fun luma(p: Int): Double {
        fun lin(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((p shr 16) and 255) + 0.7152 * lin((p shr 8) and 255) + 0.0722 * lin(p and 255)
    }
    private fun contrast(a: Int, b: Int): Double { val x = luma(a); val y = luma(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }
    private fun differs(a: Int, b: Int) =
        max(abs(((a shr 16) and 255) - ((b shr 16) and 255)), max(abs(((a shr 8) and 255) - ((b shr 8) and 255)), abs((a and 255) - (b and 255)))) > 40

    /**
     * Frames of [shown] where the effects melt into the card: round each frame's solid strokes ([strokes], the edge pass's
     * own mask, so a stroke the colour of the card counts like any other), fewer than half of the rim pairs reach 3:1.
     */
    private fun melts(strokes: List<BooleanArray?>, solid: List<BooleanArray?>, shown: List<BufferedImage>, s: Int, dump: String? = null): Int {
        var n = 0
        val cw = CardSpots.card.w.toInt(); val ch = CardSpots.card.h.toInt(); val frame = CardSpots.frame
        for (f in shown.indices) {
            val fx = strokes.getOrNull(f) ?: continue
            val all = solid.getOrNull(f) ?: fx
            val v = shown[f]
            fun at(img: BufferedImage, x: Int, y: Int) = img.getRGB(min(img.width - 1, x * s + s / 2), min(img.height - 1, y * s + s / 2))
            if (fx.count { it } < 4) continue
            var pairs = 0; var good = 0
            for (y in 0 until ch) for (x in 0 until cw) {
                if (!fx[y * cw + x]) continue
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until cw || ny !in 0 until ch || all[ny * cw + nx]) continue
                    // Past the card's frame no edge can go (the effects stay inside it): the frame itself is not the ground.
                    if (nx + 0.5 < frame.x || nx + 0.5 > frame.x + frame.w || ny + 0.5 < frame.y || ny + 0.5 > frame.y + frame.h) continue
                    pairs++
                    if (contrast(at(v, x, y), at(v, nx, ny)) >= 3.0) good++
                }
            }
            if (pairs > 0 && good * 2 < pairs) {
                n++
                if (dump != null && n <= 8) javax.imageio.ImageIO.write(v, "png", File(out, "$dump-f$f.png"))
            }
        }
        return n
    }

    @Test
    fun `every effect shows on every auto theme with its edge, and some melt without it`() {
        val official = TestFiles.official206 ?: return println("FxEdgeContrastTest skipped: no .vendor/roms/hns-206.gba")
        val sb = Sandbox(PcHns.build(official.readBytes()))
        val steps = sb.steps(scenario)
        val all = PcInkCheck.themes()
        // FX_THEMES=all checks every auto theme, =n the first n, =name,name those; the default takes every 5th (about 90).
        val themes = when (val t = System.getenv("FX_THEMES")) {
            "all" -> all; null -> all.filterIndexed { i, _ -> i % 5 == 0 }
            else -> t.toIntOrNull()?.let { all.take(it) } ?: t.split(",").toSet().let { names -> all.filter { it.first in names } }
        }
        val before = ThemeStore.snapshot()
        val report = StringBuilder("theme\tframes\tmelting with the edge\tmelting without\n")
        var withEdge = 0; var without = 0; var frames = 0
        try {
            for ((name, code) in themes) {
                ThemeStore.show(ThemeCodes.gen3(code) ?: error("$name does not parse"))
                val strokes = ArrayList<BooleanArray?>(); val solid = ArrayList<BooleanArray?>()
                val plain = sb.frames(steps, captions = false, edges = false, strokes = strokes, solid = solid)
                val edged = sb.frames(steps, captions = false)
                val dump = if (System.getenv("FX_DUMP") != null) name else null
                val on = melts(strokes, solid, edged, 3, dump?.let { "$it-edge" }); val off = melts(strokes, solid, plain, 3, dump?.let { "$it-plain" })
                withEdge += on; without += off; frames += plain.size
                report.append("$name\t${plain.size}\t$on\t$off\n")
            }
        } finally { ThemeStore.show(before) }
        File(out, "fx-edges.tsv").writeText(report.toString())
        println("FX EDGES themes=${themes.size} frames=$frames melting with=$withEdge without=$without (see ${File(out, "fx-edges.tsv")})")
        assertTrue(frames > 0, "no frames: the scenario did not play")
        assertTrue(without > 0, "no frame melts even with no edge: the check cannot fail, so it proves nothing")
        assertTrue(withEdge == 0, "$withEdge frames melt into the card with the edge on; see ${File(out, "fx-edges.tsv")}")
    }
}
