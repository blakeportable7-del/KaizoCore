package com.ironmonone.pcui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The contrast check's measure, shared by pcui's TextContrastTest (the card) and desktop's LogContrastTest (the log
 * viewer). A picture is drawn, drawn again with its words invisible (app's PixTextCheck), and drawn once more: a pixel the
 * same in both full drawings and different without the words is a word's pixel, and the drawing without words is what is
 * really behind it. A word reads by its strongest pixel against the fill most of it stands on.
 */
object PcInkCheck {
    /** The least a word may read at: WCAG AA for small text, the rule PixText keeps (app's INK_MIN). */
    const val LIMIT = com.ironmonone.app.INK_MIN

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun lum(rgb: Int) = 0.2126 * lin(rgb shr 16 and 255) + 0.7152 * lin(rgb shr 8 and 255) + 0.0722 * lin(rgb and 255)
    fun contrast(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }

    class Word(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val ink: Int, val ground: Int, val ratio: Double, val pixels: Int)

    /** The words in [a] (with words, drawn twice: [a], [a2]) over [h] (without), each a connected run of word pixels. */
    fun words(a: BufferedImage, a2: BufferedImage, h: BufferedImage): List<Word> {
        val w = a.width; val ht = a.height
        val mask = BooleanArray(w * ht)
        for (y in 0 until ht) for (x in 0 until w) {
            val p = a.getRGB(x, y)
            if (p == a2.getRGB(x, y) && p != h.getRGB(x, y)) mask[y * w + x] = true
        }
        val seen = BooleanArray(w * ht)
        val found = ArrayList<Word>()
        val stack = IntArray(w * ht)
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var sp = 0; stack[sp++] = start; seen[start] = true
            var x0 = w; var y0 = ht; var x1 = 0; var y1 = 0; var n = 0
            val pixels = ArrayList<Int>()
            while (sp > 0) {
                val i = stack[--sp]; val x = i % w; val y = i / w
                n++; x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
                pixels += i
                // Letters a pixel or two apart are one word: look 2 pixels around.
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= ht) continue
                    val j = ny * w + nx
                    if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (n < 6) continue
            // The word's ground is what most of its box is (the drawing without words): a sprite allowed to overflow a
            // little (Blake, 2026-10-09) may touch a letter's edge, and a word is read against the fill it stands on. A
            // word drawn over a picture still has that picture as its ground.
            val counts = HashMap<Int, Int>()
            for (y in y0..y1) for (x in x0..x1) { val p = h.getRGB(x, y); counts[p] = (counts[p] ?: 0) + 1 }
            val ground = counts.maxByOrNull { it.value }!!.key
            var best = 0.0; var ink = 0
            for (i in pixels) { val p = a.getRGB(i % w, i / w); val c = contrast(p, ground); if (c > best) { best = c; ink = p } }
            found += Word(x0, y0, x1, y1, ink and 0xFFFFFF, ground and 0xFFFFFF, best, n)
        }
        return found
    }

    /** Every auto theme: the 386-row Gen 3 table, then the themes painted from walking sheets for the rest (every 9th). */
    fun themes(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream("/gen3/autothemes.tsv")!!.bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") && '\t' in it }.forEach { l -> list += "gen3-${l.substringBefore('\t').trim()}" to l.substringAfter('\t').trim() }
        }
        val painted = com.ironmonone.app.AutoTheme::class.java.getDeclaredMethod("painted", Int::class.javaPrimitiveType).apply { isAccessible = true }
        for (id in (412..1283 step 9)) (painted.invoke(com.ironmonone.app.AutoTheme, id) as String?)?.let { list += "painted-$id" to it }
        return list
    }

    /** One place on one picture where words fail, over every theme that fails there. */
    class Spot(val themes: MutableSet<String> = HashSet(), var worst: Double = 99.0, var example: String = "", var crop: BufferedImage? = null)

    /** The words of many pictures, the failing ones gathered by picture and line ([scale] picture pixels a card pixel). */
    class Tally(private val scale: Int) {
        var words = 0; var fails = 0
        val spots = java.util.TreeMap<String, Spot>()

        fun add(theme: String, what: String, a: BufferedImage, found: List<Word>) {
            for (wd in found) {
                words++
                if (wd.ratio >= LIMIT) continue
                fails++
                val sp = spots.getOrPut("$what y${(wd.y0 / scale) / 10 * 10}") { Spot() }
                sp.themes += theme
                if (wd.ratio < sp.worst) {
                    sp.worst = wd.ratio
                    sp.example = "%s %06X on %06X at %d,%d".format(theme, wd.ink, wd.ground, wd.x0 / scale, wd.y0 / scale)
                    sp.crop = a.getSubimage(0, max(0, wd.y0 - 9), a.width, min(a.height - max(0, wd.y0 - 9), wd.y1 - wd.y0 + 19))
                }
            }
        }

        /** spots.tsv and one crop per spot in [out]; the lines, most themes first. */
        fun report(out: File): List<String> {
            out.listFiles()?.filter { it.name.startsWith("spot-") }?.forEach { it.delete() }
            val lines = spots.entries.sortedByDescending { it.value.themes.size }.mapIndexed { i, (k, v) ->
                v.crop?.let { ImageIO.write(it, "png", File(out, "spot-%02d.png".format(i + 1))) }
                "%02d\t%s\t%d themes\tworst %.2f\t%s\t%s".format(i + 1, k, v.themes.size, v.worst, v.example, v.themes.sorted().take(6).joinToString(","))
            }
            File(out, "spots.tsv").writeText(lines.joinToString("\n", postfix = "\n"))
            return lines
        }
    }
}
