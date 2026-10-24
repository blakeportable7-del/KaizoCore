package com.ironmonone.pc.fx

import androidx.compose.ui.graphics.toAwtImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Easing, as Lua tween libraries (rxi/flux, kikito/tween.lua) name them: t goes 0 to 1 over an effect's time, and the
 * curve shapes how a value travels. outBack overshoots and settles (a pop), outCubic eases in to rest, inOutSine
 * breathes, outElastic wobbles.
 */
object Ease {
    fun linear(t: Double) = t
    fun outCubic(t: Double) = 1 - (1 - t).pow(3)
    fun inCubic(t: Double) = t * t * t
    fun inOutSine(t: Double) = -(cos(PI * t) - 1) / 2
    fun outBack(t: Double): Double { val c1 = 1.70158; val c3 = c1 + 1; return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2) }
    fun outElastic(t: Double): Double =
        if (t <= 0) 0.0 else if (t >= 1) 1.0 else 2.0.pow(-10 * t) * sin((t * 10 - 0.75) * (2 * PI / 3)) + 1
    /** A value that rises to 1 by [a] of the way and falls back to 0 from [b] (a fade in and out). */
    fun window(t: Double, a: Double = 0.15, b: Double = 0.75) = when { t < a -> t / a; t > b -> max(0.0, (1 - t) / (1 - b)); else -> 1.0 }
}

/** Where things are on the card, in card pixels (PcBesteon's layout: the sprite slot, the HP line, the stat rows). */
object CardSpots {
    /**
     * The Pokemon's sprite slot, inside the card's frame: the frame's border runs at x 5 and y 5 (measured on the drawn
     * card), the type badge starts at y 33, the words at x 36. Effects that used to start at y 0 drew over the border.
     */
    val sprite = Rect(5.0, 5.0, 32.0, 28.0)
    /** Inside the card's frame: every effect but the walking sprite stays in here. */
    val frame = Rect(5.67, 5.67, 138.66, 148.66)
    /** "HP: 22/29" (OwnView, x 36, y 15). */
    val hp = Rect(36.0, 15.0, 54.0, 9.0)
    /** A stat's row (y 7 + 10 * row, the stat box from x 101 to 145): HP 0, ATK 1, DEF 2, SPA 3, SPD 4, SPE 5. */
    fun statRow(stat: String): Rect? {
        // Accuracy and evasion share the bottom line in battle (BstRow: "Acc" from x 101, "Eva" from x 129, y 67).
        if (stat == "ACC") return Rect(101.0, 69.0, 22.0, 9.0)
        if (stat == "EVA") return Rect(123.0, 69.0, 22.0, 9.0)
        val i = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE").indexOf(stat).takeIf { it >= 0 } ?: return null
        return Rect(102.0, 7.0 + 10 * i + 2, 42.0, 9.0)
    }
    /** The number at the end of a stat's row (right of x 125); for accuracy and evasion, their chevrons. */
    fun statNumber(stat: String): Rect? = when (stat) {
        "ACC" -> Rect(116.0, 69.0, 7.0, 9.0)
        "EVA" -> Rect(123.0, 69.0, 7.0, 9.0)
        else -> statRow(stat)?.let { Rect(127.0, it.y, 17.0, it.h) }
    }
    val card = Rect(0.0, 0.0, 150.0, 160.0)
}

data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

/**
 * One animation over the card, started at [start] (nanoseconds) and lasting [ms]; [loops] ones run until removed (a
 * status that stays on). Drawn in card pixels on a Graphics2D already scaled to the window ([s] window pixels a card
 * pixel), so every particle is a crisp square of the card's own grid. [card] is the card as composed (for an effect that
 * lifts a piece of it: the number that pops, the sprite that shakes).
 */
abstract class Fx(val start: Long, val ms: Long, val loops: Boolean = false) {
    /** It draws in the sprite slot (a label, an icon, a burst): such effects take turns, one at a time (FxLayer's queue). */
    open val usesSlot: Boolean get() = false
    fun t(now: Long): Double = ((now - start) / 1e6 / ms).let { if (loops) it - kotlin.math.floor(it) else min(1.0, it) }
    fun seconds(now: Long): Double = (now - start) / 1e9
    fun done(now: Long): Boolean = !loops && (now - start) / 1e6 >= ms
    abstract fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?)
    /** How far this moves the whole card this moment (a hard hit's shake), in card pixels. */
    open fun cardOffset(now: Long): Pair<Double, Double> = 0.0 to 0.0
    /**
     * How this moves the walking sprite the layer draws (dx, dy in card pixels, and its opacity), or null. Set when the
     * layer walks the sprite itself ([liveSprite]); the effect then leaves the card's picture alone.
     */
    open fun spriteMotion(now: Long): Triple<Double, Double, Double>? = null
    /** The layer draws the walking sprite (FxLayer.walker): move it through [spriteMotion], do not lift it off the card. */
    var liveSprite = false
    /** Queued: it waits its turn for the sprite slot (FxLayer's queue). Drawn before its start, if it has a waiting look. */
    open fun drawWaiting(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {}
}

/** Small drawing helpers in card pixels. */
internal object Px {
    fun dot(g: Graphics2D, s: Double, x: Double, y: Double, c: Color, size: Double = 1.0) {
        g.color = c
        g.fillRect((x * s).roundToInt(), (y * s).roundToInt(), max(1, (size * s).roundToInt()), max(1, (size * s).roundToInt()))
    }
    fun alpha(c: Color, a: Double) = Color(c.red, c.green, c.blue, (c.alpha * a.coerceIn(0.0, 1.0)).roundToInt())
    fun mix(a: Color, b: Color, t: Double) = Color(
        (a.red + (b.red - a.red) * t).roundToInt(), (a.green + (b.green - a.green) * t).roundToInt(),
        (a.blue + (b.blue - a.blue) * t).roundToInt(), (a.alpha + (b.alpha - a.alpha) * t).roundToInt())
    /**
     * Words centred on (cx, cy) in card pixels, [size] card pixels tall, with a one-pixel dark outline; shrunk to fit
     * [maxW] card pixels when wider, so a label stays in its slot instead of crossing the card's words.
     */
    fun text(g: Graphics2D, s: Double, font: Font, text: String, cx: Double, cy: Double, size: Double, c: Color, outline: Color = Color(0, 0, 0, c.alpha), maxW: Double = Double.MAX_VALUE) {
        g.font = font.deriveFont((size * s).toFloat())
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        if (g.fontMetrics.stringWidth(text) > maxW * s) g.font = font.deriveFont((size * s * maxW * s / g.fontMetrics.stringWidth(text)).toFloat())
        val fm = g.fontMetrics
        val x = (cx * s - fm.stringWidth(text) / 2.0).toFloat(); val y = (cy * s + fm.ascent / 2.0 - fm.descent / 2.0).toFloat()
        val o = max(1f, (s / 2).toFloat())
        g.color = outline
        for ((dx, dy) in listOf(-o to 0f, o to 0f, 0f to -o, 0f to o, -o to -o, o to o, -o to o, o to -o)) g.drawString(text, x + dx, y + dy)
        g.color = c
        g.drawString(text, x, y)
    }
    /** A piece of the card image ([r], card pixels) drawn at [r] moved by (dx, dy) and scaled by [k] about its centre. */
    fun lift(g0: Graphics2D, s: Double, card: BufferedImage, r: Rect, dx: Double = 0.0, dy: Double = 0.0, k: Double = 1.0) = Edges.onCard(g0) { g ->
        val cs = card.width / CardSpots.card.w
        val sx0 = (r.x * cs).roundToInt().coerceIn(0, card.width - 1); val sy0 = (r.y * cs).roundToInt().coerceIn(0, card.height - 1)
        val sx1 = ((r.x + r.w) * cs).roundToInt().coerceIn(sx0 + 1, card.width); val sy1 = ((r.y + r.h) * cs).roundToInt().coerceIn(sy0 + 1, card.height)
        val w = r.w * k; val h = r.h * k
        val x0 = ((r.cx - w / 2 + dx) * s).roundToInt(); val y0 = ((r.cy - h / 2 + dy) * s).roundToInt()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(card, x0, y0, x0 + (w * s).roundToInt(), y0 + (h * s).roundToInt(), sx0, sy0, sx1, sy1, null)
    }
    /** [r] covered with the card's own fill read at (sx, sy), on the card itself (a piece about to be drawn again moved). */
    fun cover(g0: Graphics2D, s: Double, card: BufferedImage, r: Rect, sx: Double, sy: Double) = Edges.onCard(g0) { g ->
        g.color = fillAt(card, sx, sy)
        g.fillRect((r.x * s).roundToInt(), (r.y * s).roundToInt(), (r.w * s).roundToInt(), (r.h * s).roundToInt())
    }
    /**
     * A picture of the game's (a battle animation frame, an item icon) at card pixel (x, y), [k] card pixels per picture
     * pixel (the game's sprites are drawn for a 64-pixel Pokemon; the card's is 32, so about half), nearest neighbour.
     */
    fun image(g: Graphics2D, s: Double, img: BufferedImage, x: Double, y: Double, k: Double, alpha: Double = 1.0) {
        if (alpha <= 0.01) return
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        val old = g.composite
        if (alpha < 1.0) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.toFloat().coerceIn(0f, 1f))
        g.drawImage(img, (x * s).roundToInt(), (y * s).roundToInt(), (img.width * k * s).roundToInt(), (img.height * k * s).roundToInt(), null)
        g.composite = old
    }

    /** The card's fill at a card pixel, read off the card image (to cover a piece before it is drawn again moved). */
    /**
     * A ring on the card's pixel grid: whole card pixels round the ellipse (rx, ry), each once, no smoothing. The smooth
     * rings it replaces blurred against the ROM's pixel sprites (design audit 2026-10-10).
     */
    fun ring(g: Graphics2D, s: Double, cx: Double, cy: Double, rx: Double, ry: Double, c: Color) {
        val n = max(12, (2 * PI * max(rx, ry) * 1.5).roundToInt())
        val seen = HashSet<Long>()
        for (i in 0 until n) {
            val a = i * 2 * PI / n
            val x = kotlin.math.floor(cx + cos(a) * rx); val y = kotlin.math.floor(cy + sin(a) * ry)
            if (seen.add(x.toLong() * 100000 + y.toLong())) dot(g, s, x, y, c)
        }
    }

    /** A soft disc on the grid: card pixels inside (rx, ry), brightest at the centre, the outer third dithered. */
    fun disc(g: Graphics2D, s: Double, cx: Double, cy: Double, rx: Double, ry: Double, c: Color) {
        for (y in kotlin.math.floor(cy - ry).toInt()..kotlin.math.ceil(cy + ry).toInt())
            for (x in kotlin.math.floor(cx - rx).toInt()..kotlin.math.ceil(cx + rx).toInt()) {
                val dx = (x + 0.5 - cx) / rx; val dy = (y + 0.5 - cy) / ry
                val d = dx * dx + dy * dy
                if (d > 1 || (d > 0.55 && (x + y) % 2 != 0)) continue
                dot(g, s, x.toDouble(), y.toDouble(), alpha(c, 1 - d * 0.7))
            }
    }

    /** A rectangle's outline, one card pixel wide, on the grid. */
    fun box(g: Graphics2D, s: Double, r: Rect, c: Color) {
        val x0 = kotlin.math.floor(r.x); val y0 = kotlin.math.floor(r.y); val x1 = kotlin.math.ceil(r.x + r.w) - 1; val y1 = kotlin.math.ceil(r.y + r.h) - 1
        g.color = c
        g.fillRect((x0 * s).roundToInt(), (y0 * s).roundToInt(), ((x1 - x0 + 1) * s).roundToInt(), s.roundToInt())
        g.fillRect((x0 * s).roundToInt(), (y1 * s).roundToInt(), ((x1 - x0 + 1) * s).roundToInt(), s.roundToInt())
        g.fillRect((x0 * s).roundToInt(), (y0 * s).roundToInt(), s.roundToInt(), ((y1 - y0 + 1) * s).roundToInt())
        g.fillRect((x1 * s).roundToInt(), (y0 * s).roundToInt(), s.roundToInt(), ((y1 - y0 + 1) * s).roundToInt())
    }

    /**
     * A glint crossing [r]: one band three card pixels wide, moved in whole card pixels, its middle brightest. It was five
     * fractional columns, which read as a barcode (design audit).
     */
    fun band(g: Graphics2D, s: Double, r: Rect, x: Double, c: Color) {
        val cx = kotlin.math.floor(x)
        for (k in -1..1) {
            val col = cx + k
            if (col < r.x || col >= r.x + r.w) continue
            g.color = alpha(c, if (k == 0) 1.0 else 0.45)
            g.fillRect((col * s).roundToInt(), (r.y * s).roundToInt(), s.roundToInt(), (r.h * s).roundToInt())
        }
    }

    private val tints = java.util.WeakHashMap<BufferedImage, HashMap<Int, BufferedImage>>()

    /** [img] recoloured to [c] by its own lightness (the game tints its orbs the same way, by palette). */
    fun tinted(img: BufferedImage, c: Color): BufferedImage = synchronized(tints) {
        tints.getOrPut(img) { HashMap() }.getOrPut(c.rgb) {
            val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val p = img.getRGB(x, y); val a = p ushr 24
                if (a == 0) continue
                val l = (((p shr 16) and 255) * 0.3 + ((p shr 8) and 255) * 0.59 + (p and 255) * 0.11) / 255.0
                val k = 0.35 + 0.9 * l
                out.setRGB(x, y, (a shl 24) or ((c.red * k).toInt().coerceIn(0, 255) shl 16) or ((c.green * k).toInt().coerceIn(0, 255) shl 8) or (c.blue * k).toInt().coerceIn(0, 255))
            }
            out
        }
    }

    fun fillAt(card: BufferedImage, x: Double, y: Double): Color {
        val cs = card.width / CardSpots.card.w
        return Color(card.getRGB((x * cs).roundToInt().coerceIn(0, card.width - 1), (y * cs).roundToInt().coerceIn(0, card.height - 1)))
    }
}

/**
 * Particles with no state of their own: each is a pure function of its index and the time, from a seeded random number,
 * so a frame can be drawn for any moment (the preview, a dropped frame) and nothing is stored per particle.
 */
internal class Seeds(private val seed: Int) {
    fun r(i: Int, k: Int): Double {
        var x = seed * 374761393 + i * 668265263 + k * 2147483647
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0x7FFFFFFF) / 2147483647.0
    }
}

/**
 * A status that stays on, around the sprite, for as long as it lasts: one look each (Blake, 2026-10-09). With the
 * game's own art ([art], read from the ROM) it uses the sprites the game's status animations use: embers, poison
 * bubbles, the shock, ice crystals, the Z, the ducks of confusion, the red heart of Attract. Without it, the looks below
 * drawn in code.
 */
class StatusAura(start: Long, val kind: String, val art: FxRomArt? = null, val area: Rect = CardSpots.sprite) : Fx(start, 2400, loops = true) {
    private val seeds = Seeds(kind.hashCode())
    override fun spriteMotion(now: Long): Triple<Double, Double, Double>? {
        if (kind != "PAR") return null
        val sec = seconds(now)
        return if (sec % 0.9 <= 0.25) Triple(if ((sec * 30).toInt() % 2 == 0) 0.8 else -0.8, 0.0, 1.0) else null
    }
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val sec = seconds(now)
        if (art != null && art.ok && romArt(g, s, sec, card)) return
        when (kind) {
            "BRN" -> embers(g, s, sec)
            "PSN", "TOX" -> bubbles(g, s, sec)
            "PAR" -> sparks(g, s, sec, card)
            "FRZ" -> frost(g, s, sec)
            "SLP" -> zees(g, s, sec)
            "CONFUSE" -> stars(g, s, sec)
            "ATTRACT" -> hearts(g, s, sec)
        }
    }

    /** The status in the game's own sprites; false when this ROM has none for it (the look drawn in code then). */
    private fun romArt(g: Graphics2D, s: Double, sec: Double, card: BufferedImage?): Boolean {
        val a = art ?: return false
        when (kind) {
            "BRN" -> {
                val f = a.frames("ember", 32).ifEmpty { return false }
                for (i in 0 until 4) {
                    val life = 1.1 + seeds.r(i, 1) * 0.5
                    val age = ((sec + seeds.r(i, 2) * life) % life) / life
                    val x = area.x + 2 + seeds.r(i, 3) * (area.w - 14); val y = area.y + area.h - 14 - age * 16
                    Px.image(g, s, f[((age * f.size).toInt()).coerceIn(0, f.size - 1)], x, y, 0.45, Ease.window(age, 0.1, 0.6))
                }
            }
            "PSN", "TOX" -> {
                val f = a.frames(if (kind == "TOX") "toxic_bubble" else "poison_bubble", 16).ifEmpty { return false }
                for (i in 0 until 6) {
                    val life = 1.5 + seeds.r(i, 1)
                    val age = ((sec + seeds.r(i, 2) * life) % life) / life
                    val x = area.x + 3 + seeds.r(i, 3) * (area.w - 12) + sin(sec * 2.5 + i * 1.7) * 1.5
                    val y = area.y + area.h - 8 - age * area.h * 0.8
                    Px.image(g, s, f[if (age > 0.8) f.size - 1 else ((age / 0.8) * (f.size - 1)).toInt()], x, y, 0.5, Ease.window(age, 0.1, 0.85))
                }
            }
            "PAR" -> {
                val f = a.frames("shock", 32).ifEmpty { return false }
                val beat = sec % 0.9
                if (beat > 0.3) return true
                if (card != null && !liveSprite) Px.lift(g, s, card, area, dx = if ((sec * 30).toInt() % 2 == 0) 0.8 else -0.8)
                Px.image(g, s, f[((beat / 0.3) * f.size).toInt().coerceIn(0, f.size - 1)], area.cx - 8, area.cy - 8, 0.5, 1.0)
            }
            "FRZ" -> {
                val f = a.parts("ice_crystals").orEmpty().ifEmpty { return false }
                g.color = Color(150, 210, 255, 70)
                g.fillRect((area.x * s).roundToInt(), (area.y * s).roundToInt(), (area.w * s).roundToInt(), (area.h * s).roundToInt())
                for (i in 0 until 6) {
                    val x = area.x + 2 + seeds.r(i, 1) * (area.w - 10); val y = area.y + 2 + seeds.r(i, 2) * (area.h - 10)
                    val glint = ((sin(sec * 2.5 + i * 1.3) + 1) / 2)
                    Px.image(g, s, f[i % f.size], x, y, 0.5, 0.45 + 0.55 * glint)
                }
            }
            "SLP" -> {
                val z = a.parts("letter_z")?.firstOrNull() ?: return false
                for (i in 0 until 3) {
                    val age = ((sec / 2.1 + i / 3.0) % 1.0)
                    val x = area.x + area.w * 0.55 + age * 9; val y = area.y + area.h * 0.3 - age * 14
                    Px.image(g, s, z, x, y, 0.2 + age * 0.25, Ease.window(age, 0.15, 0.7))
                }
            }
            "CONFUSE" -> {
                val f = a.frames("duck", 16).ifEmpty { return false }
                for (i in 0 until 3) {
                    val ang = sec * 3.5 + i * 2 * PI / 3
                    Px.image(g, s, f[((sec * 6).toInt() + i) % f.size], area.cx + cos(ang) * 10 - 4, area.y + 4 + sin(ang) * 2.5, 0.5, 1.0)
                }
            }
            "ATTRACT" -> {
                val h = a.parts("red_heart")?.firstOrNull() ?: return false
                // Three small hearts, at the scale of the other statuses' sprites: at half size they were bigger than the
                // Pokemon and the loudest thing on the card (design audit).
                for (i in 0 until 3) {
                    val age = ((sec / 1.8 + seeds.r(i, 1)) % 1.0)
                    val x = area.x + 3 + seeds.r(i, 2) * (area.w - 10) + sin(age * 9 + i) * 2
                    val y = area.y + area.h - 6 - age * (area.h - 6)
                    val beat = 0.3 + 0.06 * abs(sin(age * 14))
                    Px.image(g, s, h, x, y, beat, Ease.window(age, 0.1, 0.7))
                }
            }
            else -> return false
        }
        return true
    }

    /** Burn: embers rising off the sprite, yellow to orange to red, flickering, gone by the top. */
    private fun embers(g: Graphics2D, s: Double, sec: Double) {
        for (i in 0 until 14) {
            val life = 0.9 + seeds.r(i, 1) * 0.7
            val age = ((sec + seeds.r(i, 2) * life) % life) / life
            val x = area.x + 3 + seeds.r(i, 3) * (area.w - 6) + sin(sec * 6 + i) * 1.2
            val y = area.y + area.h - 2 - age * (area.h + 2)
            val c = if (age < 0.3) Color(255, 236, 120) else if (age < 0.6) Color(255, 150, 40) else Color(220, 60, 30)
            val flick = if (((sec * 20).toInt() + i) % 5 == 0) 0.4 else 1.0
            Px.dot(g, s, x, y, Px.alpha(c, (1 - age) * flick), if (age < 0.4) 1.5 else 1.0)
        }
    }

    /** Poison: purple bubbles swaying up, each popping into a ring before it fades. */
    private fun bubbles(g: Graphics2D, s: Double, sec: Double) {
        for (i in 0 until 9) {
            val life = 1.4 + seeds.r(i, 1)
            val age = ((sec + seeds.r(i, 2) * life) % life) / life
            val x = area.x + 4 + seeds.r(i, 3) * (area.w - 8) + sin(sec * 2.5 + i * 1.7) * 2
            val y = area.y + area.h - 1 - age * area.h * 0.9
            val purple = Color(176, 80, 220)
            if (age < 0.85) {
                val r = 1.0 + age * 1.6
                Px.disc(g, s, x, y, r, r, Px.alpha(purple, 0.85))
                Px.dot(g, s, x - r / 2, y - r / 2, Px.alpha(Color(235, 200, 255), 0.9), 0.7)
            } else {
                val p = (age - 0.85) / 0.15; val r = 2.5 + p * 2
                Px.ring(g, s, x, y, r, r, Px.alpha(purple, 1 - p))
            }
        }
    }

    /** Paralysis: a yellow zigzag flashes across the sprite now and then, and the sprite twitches with it. */
    private fun sparks(g: Graphics2D, s: Double, sec: Double, card: BufferedImage?) {
        val beat = sec % 0.9
        if (beat > 0.22) return
        val k = (sec / 0.9).toInt()
        if (card != null && !liveSprite) Px.lift(g, s, card, area, dx = if ((sec * 30).toInt() % 2 == 0) 0.8 else -0.8)
        g.stroke = java.awt.BasicStroke((s * 0.9).toFloat())
        for (b in 0 until 2) {
            var x = area.x + 4 + seeds.r(k, b * 7) * (area.w - 8); var y = area.y + 2.0
            val path = java.awt.geom.Path2D.Double(); path.moveTo(x * s, y * s)
            while (y < area.y + area.h - 4) { x += (seeds.r(k, (y * 3).toInt() + b) - 0.5) * 9; y += 4.0; path.lineTo(x * s, y * s) }
            g.color = Color(255, 255, 160, 220); g.draw(path)
        }
    }

    /** Freeze: an icy wash over the sprite and crystals that glint in turn. */
    private fun frost(g: Graphics2D, s: Double, sec: Double) {
        val breathe = 0.28 + 0.06 * sin(sec * 2)
        g.color = Color(150, 210, 255, (breathe * 255).roundToInt())
        g.fillRect((area.x * s).roundToInt(), (area.y * s).roundToInt(), (area.w * s).roundToInt(), (area.h * s).roundToInt())
        for (i in 0 until 7) {
            val x = area.x + 3 + seeds.r(i, 1) * (area.w - 6); val y = area.y + 3 + seeds.r(i, 2) * (area.h - 6)
            val glint = ((sin(sec * 3 + i * 1.3) + 1) / 2).pow(4)
            val c = Px.alpha(Color.WHITE, 0.4 + 0.6 * glint)
            Px.dot(g, s, x, y, c); Px.dot(g, s, x - 1, y, Px.alpha(c, 0.6)); Px.dot(g, s, x + 1, y, Px.alpha(c, 0.6))
            Px.dot(g, s, x, y - 1, Px.alpha(c, 0.6)); Px.dot(g, s, x, y + 1, Px.alpha(c, 0.6))
        }
    }

    /** Sleep: Z after Z drifting up and right from the head, growing and fading. */
    private fun zees(g: Graphics2D, s: Double, sec: Double) {
        val font = Font(Font.SANS_SERIF, Font.BOLD, 10)
        for (i in 0 until 3) {
            val age = ((sec / 2.1 + i / 3.0) % 1.0)
            val x = area.x + area.w * 0.62 + age * 9 + sin(age * 6) * 1.5
            val y = area.y + area.h * 0.35 - age * 14
            Px.text(g, s, font, "Z", x, y, 4 + age * 5, Px.alpha(Color(220, 230, 255), Ease.window(age, 0.15, 0.7)), Color(30, 40, 90, (Ease.window(age, 0.15, 0.7) * 200).roundToInt()))
        }
    }

    /** Confusion: little stars circling over the head. */
    private fun stars(g: Graphics2D, s: Double, sec: Double) {
        for (i in 0 until 3) {
            val a = sec * 4 + i * 2 * PI / 3
            val x = area.cx + cos(a) * 9; val y = area.y + 3 + sin(a) * 2.5
            val c = if (sin(a) > 0) Color(255, 240, 120) else Color(200, 180, 80)
            Px.dot(g, s, x, y, c); Px.dot(g, s, x - 1, y, Px.alpha(c, 0.5)); Px.dot(g, s, x + 1, y, Px.alpha(c, 0.5))
            Px.dot(g, s, x, y - 1, Px.alpha(c, 0.5)); Px.dot(g, s, x, y + 1, Px.alpha(c, 0.5))
        }
    }

    /** Infatuation (Attract): pink hearts floating up and swaying, beating as they go. */
    private fun hearts(g: Graphics2D, s: Double, sec: Double) {
        val heart = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
        for (i in 0 until 4) {
            val age = ((sec / 1.8 + seeds.r(i, 1)) % 1.0)
            val x = area.x + 3 + seeds.r(i, 2) * (area.w - 10) + sin(age * 9 + i) * 2
            val y = area.y + area.h - 4 - age * (area.h - 2)
            val beat = 0.6 + 0.4 * abs(sin(age * 14))
            val c = Px.alpha(Color(255, 110, 170), Ease.window(age, 0.1, 0.7))
            heart.forEachIndexed { row, line -> line.forEachIndexed { col, ch -> if (ch == '1') Px.dot(g, s, x + col * 0.6 * beat, y + row * 0.6 * beat, c, 0.6 * beat + 0.05) } }
        }
    }
}

/** The moment a status starts: a ring bursts off the sprite in its colour, and its word pops over it. */
class StatusOnset(start: Long, val kind: String, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1100) {
    override val usesSlot get() = true
    private val color = when (kind) { "BRN" -> Color(255, 120, 40); "PSN", "TOX" -> Color(180, 90, 230); "PAR" -> Color(255, 230, 60)
        "FRZ" -> Color(140, 210, 255); "SLP" -> Color(170, 180, 220); "ATTRACT" -> Color(255, 110, 170); else -> Color(255, 240, 120) }
    private val word = when (kind) { "BRN" -> "BURNED!"; "PSN" -> "POISONED!"; "TOX" -> "BADLY POISONED!"; "PAR" -> "PARALYZED!"
        "FRZ" -> "FROZEN!"; "SLP" -> "ASLEEP!"; "ATTRACT" -> "IN LOVE!"; "CONFUSE" -> "CONFUSED!"; else -> kind }
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        // The ring and the word stay in the sprite's slot: out over the card they crossed its name and its type badge.
        val before = g.clip
        g.clipRect((6 * s).roundToInt(), (6 * s).roundToInt(), (30 * s).roundToInt(), (27 * s).roundToInt())
        val r = 4 + Ease.outCubic(t) * 14
        Px.ring(g, s, area.cx, area.cy, r, r * 0.9, Px.alpha(color, 1 - t))
        g.clip = before
        val k = Ease.outBack(min(1.0, t * 2.2))
        Px.text(g, s, font, word, area.cx, area.y + 23, 6.5 * k, Px.alpha(color, Ease.window(t, 0.05, 0.75)), maxW = area.w - 7)
    }
}

/**
 * A stat stage moved: the number lifts off the card and pops (bigger on a raise, a squash on a drop), the row flashes in
 * the theme's sense of good or bad, and the change floats up beside it ("+2", "-1") with its chevrons.
 */
class StatPop(start: Long, val stat: String, val delta: Int, val font: Font, val up: Color = Color(80, 230, 110), val down: Color = Color(255, 90, 90),
              /** The opponent's card: its stat rows hold your marking boxes, not numbers; the stage's arrow pops instead. */
              val foe: Boolean = false) : Fx(start, 1300) {
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val row = CardSpots.statRow(stat) ?: return
        // The label and its stage arrow (x 102 to 125) on the opponent's card; the number on yours.
        val num = (if (foe && stat != "ACC" && stat != "EVA") Rect(102.0, row.y, 23.0, row.h) else CardSpots.statNumber(stat)) ?: return
        val t = t(now)
        val c = if (delta > 0) up else down
        g.color = Px.alpha(c, 0.35 * (1 - t))
        g.fillRect((row.x * s).roundToInt(), (row.y * s).roundToInt(), (row.w * s).roundToInt(), (row.h * s).roundToInt())
        if (card != null) {
            // A number pops big; the opponent's label and arrow only a little, from their left edge, so they stay off
            // the HP row and the marking box beside them.
            val amp = if (foe) 0.2 else 0.7
            val k = if (delta > 0) 1 + amp * sin(PI * Ease.outCubic(t)) * (1 - t) else 1 - (if (foe) 0.12 else 0.3) * sin(PI * min(1.0, t * 1.6))
            Px.cover(g, s, card, num, num.x - 2, num.cy)
            Px.lift(g, s, card, num, dx = if (foe) (k - 1) * num.w / 2 else 0.0, dy = if (delta < 0) Ease.outBack(min(1.0, t * 2)) * 1.5 * (1 - t) else 0.0, k = k)
        }
        val rise = Ease.outCubic(t) * 10
        // Accuracy and evasion share one line with no room beside it: their change is said by the chevrons popping (and,
        // for evasion going up, by the afterimages' own label), not by a number floating over the stats.
        if (stat != "ACC" && stat != "EVA" && !foe) Px.text(g, s, font, (if (delta > 0) "+" else "") + delta, row.x - 7, row.cy - rise, 6.0 + abs(delta), Px.alpha(c, Ease.window(t, 0.05, 0.7)), maxW = 9.0)
    }
}

/** A hit: the sprite shakes, damped (a spring settling), flashes white, and the damage floats up off the HP line. */
class Hit(start: Long, val amount: Int, val big: Boolean, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, if (big) 900 else 650) {
    override val usesSlot get() = true
    private fun shake(t: Double) = exp(-6 * t) * sin(t * 2 * PI * 7) * (if (big) 3.0 else 1.6)
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        if (liveSprite) {
            if (t < 0.12) { g.color = Color(255, 255, 255, 110); g.fillRect((area.x * s).roundToInt(), (area.y * s).roundToInt(), (area.w * s).roundToInt(), (area.h * s).roundToInt()) }
        } else if (card != null) {
            Px.cover(g, s, card, area, area.x + 1, area.y + 1)
            Px.lift(g, s, card, area, dx = shake(t))
            if (t < 0.12) { g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f); Px.lift(g, s, card, area, dx = shake(t)); g.composite = AlphaComposite.SrcOver
                g.color = Color(255, 255, 255, 120); g.fillRect((area.x * s).roundToInt(), (area.y * s).roundToInt(), (area.w * s).roundToInt(), (area.h * s).roundToInt()) }
        }
        val hp = CardSpots.hp
        // The damage rises in the sprite slot, never out over the name or the HP line.
        Px.text(g, s, font, "-$amount", area.cx, area.y + area.h - 6 - Ease.outCubic(t) * 14, if (big) 9.0 else 7.0, Px.alpha(Color(255, 80, 70), Ease.window(t, 0.05, 0.6)), maxW = area.w - 4)
    }
    override fun cardOffset(now: Long): Pair<Double, Double> = if (big) (shake(t(now)) * 0.5) to 0.0 else 0.0 to 0.0
    override fun spriteMotion(now: Long): Triple<Double, Double, Double> = Triple(shake(t(now)), 0.0, 1.0)
}

/**
 * Evasion up (Double Team, Minimize, a Bright Powder boost): afterimages split off the sprite to both sides, flicker,
 * and fold back, as the games' Double Team does, and "EVASION UP" floats off. The ghosts are the walking sprite itself,
 * drawn by the layer from [ghosts].
 */
class Afterimages(start: Long, val delta: Int, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1500) {
    override val usesSlot get() = true
    /** The ghosts this moment: (dx, dy, opacity), drawn by FxLayer.walk under the sprite. */
    fun ghosts(now: Long): List<Triple<Double, Double, Double>> {
        val t = t(now)
        val spread = Ease.outBack(min(1.0, t * 2.2)) * (1 - Ease.inCubic(max(0.0, (t - 0.6) / 0.4))) * (6.0 + 3 * delta)
        val flicker = if (((now - start) / 40_000_000L) % 2 == 0L) 0.55 else 0.3
        return (1..(1 + delta).coerceAtMost(3)).flatMap { k -> listOf(Triple(-spread * k / 2, 0.0, flicker / k), Triple(spread * k / 2, 0.0, flicker / k)) }
    }
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        // In the sprite's own slot, the one place on the card with no words: "EVASION" over "UP +n".
        val k = Ease.outBack(min(1.0, t * 2.5)); val a = Ease.window(t, 0.05, 0.75)
        val ink = Px.alpha(Color(170, 220, 255), a); val edge = Color(10, 30, 60, (230 * a).roundToInt())
        Px.text(g, s, font, "EVASION", area.cx, area.y + 16 - Ease.outCubic(t) * 4, 6.5 * k, ink, edge, area.w - 7)
        Px.text(g, s, font, "UP +$delta", area.cx, area.y + 23 - Ease.outCubic(t) * 4, 6.5 * k, ink, edge, area.w - 7)
    }
}

/** A label in the sprite's slot, the one place on the card with no words: [top] over [bottom], popping in. */
internal fun slotLabel(g: Graphics2D, s: Double, font: Font, area: Rect, t: Double, top: String, bottom: String, ink: Color, edge: Color) {
    val k = Ease.outBack(min(1.0, t * 2.5)); val a = Ease.window(t, 0.05, 0.75)
    val e = Color(edge.red, edge.green, edge.blue, (230 * a).roundToInt())
    Px.text(g, s, font, top, area.cx, area.y + 16 - Ease.outCubic(t) * 4, 6.5 * k, Px.alpha(ink, a), e, area.w - 7)
    Px.text(g, s, font, bottom, area.cx, area.y + 23 - Ease.outCubic(t) * 4, 6.5 * k, Px.alpha(ink, a), e, area.w - 7)
}

/**
 * Evasion down (Sweet Scent, Defog): the game's own lock-on reticle (art "reticle": green squares that close in, then
 * red corner brackets) shrinks onto the sprite and snaps, blinking twice: it is easier to hit now.
 */
class EvasionDown(start: Long, val delta: Int, val art: FxRomArt?, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1500) {
    override val usesSlot get() = true
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        val f = art?.frames("reticle", 16).orEmpty()
        if (f.size >= 5) {
            if (t < 0.55) {
                // The green squares, closing in from wide to the sprite's size.
                val p = t / 0.55
                val i = 1 + (p * 3).toInt().coerceIn(0, 2)
                val k = 2.6 - 1.4 * Ease.outCubic(p)
                Px.image(g, s, f[i], area.cx - 8 * k, area.cy - 8 * k, k, 0.9)
            } else {
                // The red brackets snap on and blink twice.
                val blink = ((t - 0.55) / 0.1).toInt() % 2 == 0 || t > 0.95
                if (blink) Px.image(g, s, f[4], area.cx - 20, area.cy - 20, 2.5, 1.0 - 0.3 * max(0.0, (t - 0.85) / 0.15))
            }
        } else {
            val r = 20 - 12 * Ease.outCubic(min(1.0, t / 0.55))
            g.color = Px.alpha(Color(255, 90, 80), Ease.window(t, 0.05, 0.85)); g.stroke = java.awt.BasicStroke((s * 0.8).toFloat())
            g.drawRect(((area.cx - r) * s).roundToInt(), ((area.cy - r) * s).roundToInt(), (2 * r * s).roundToInt(), (2 * r * s).roundToInt())
        }
        slotLabel(g, s, font, area, t, "EVASION", "DOWN $delta", Color(255, 150, 140), Color(60, 10, 10))
    }
}

/**
 * Accuracy down (Sand Attack, Smokescreen, Mud-Slap, Flash, Kinesis): the game's own dust clouds (art "gray_smoke") roll
 * over the sprite's eyes, sand grains spray in, and the cloud thins away.
 */
class AccuracyDown(start: Long, val delta: Int, val art: FxRomArt?, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1500) {
    override val usesSlot get() = true
    private val seeds = Seeds(delta * 31 + 7)
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        val clouds = art?.frames("gray_smoke", 32).orEmpty()
        val grains = art?.parts("mud_sand").orEmpty()
        if (clouds.isNotEmpty()) {
            val i = (t * clouds.size * 1.1).toInt().coerceIn(0, clouds.size - 1)
            Px.image(g, s, clouds[i], area.x - 2, area.y - 4, 1.0, Ease.window(t, 0.05, 0.7))
        } else {
            g.color = Px.alpha(Color(200, 190, 150), 0.6 * Ease.window(t, 0.05, 0.7))
            Px.disc(g, s, area.cx, area.y + 10, area.w / 2 - 2, 8.0, g.color)
        }
        // Grains thrown at it from the right, the way Sand Attack comes in.
        for (i in 0 until 10) {
            val age = (t * 1.6 - seeds.r(i, 1) * 0.5).coerceIn(0.0, 1.0)
            if (age <= 0 || age >= 1) continue
            val x = area.x + area.w + 6 - age * (area.w + 4); val y = area.y + 6 + seeds.r(i, 2) * 14 + age * 4
            val grain = grains.firstOrNull()
            if (grain != null) Px.image(g, s, grain, x, y, 0.5, 1 - age) else Px.dot(g, s, x, y, Px.alpha(Color(210, 190, 130), 1 - age), 1.0)
        }
        slotLabel(g, s, font, area, t, "ACCURACY", "DOWN $delta", Color(230, 210, 150), Color(50, 35, 10))
    }
}

/** Level up: a gold column of sparkles over the sprite and LV UP popping in. */
class LevelUp(start: Long, val to: Int, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1500) {
    override val usesSlot get() = true
    private val seeds = Seeds(to)
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        for (i in 0 until 16) {
            val age = (t * 1.4 - seeds.r(i, 1) * 0.4).coerceIn(0.0, 1.0)
            if (age <= 0 || age >= 1) continue
            // The column rises inside the sprite slot and stops at the card's top edge.
            val x = area.x + 2 + seeds.r(i, 2) * (area.w - 4); val y = area.y + area.h - age * (area.h - 1)
            Px.dot(g, s, x, y, Px.alpha(Color(255, 220, 80), 1 - age), if (i % 3 == 0) 1.5 else 1.0)
        }
        // The words in the slot too, as every other label: over the card's border they crossed its frame and its name.
        slotLabel(g, s, font, area, t, "LV UP!", "Lv.$to", Color(255, 220, 80), Color(70, 40, 0))
    }
}

/** Fainted: the sprite sinks and greys out, the way the games drop a fainted Pokemon off its platform. */
class Faint(start: Long, val area: Rect = CardSpots.sprite) : Fx(start, 1000) {
    override val usesSlot get() = true
    override fun spriteMotion(now: Long): Triple<Double, Double, Double> { val t = Ease.inCubic(t(now)); return Triple(0.0, t * area.h, 1 - t) }
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        if (liveSprite) return
        card ?: return
        val t = Ease.inCubic(t(now))
        Px.cover(g, s, card, area, area.x + 1, area.y + 1)
        val clip = g.clip
        g.clipRect((area.x * s).roundToInt(), (area.y * s).roundToInt(), (area.w * s).roundToInt(), (area.h * s).roundToInt())
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1 - t).toFloat())
        Px.lift(g, s, card, area, dy = t * area.h)
        g.composite = AlphaComposite.SrcOver
        g.clip = clip
    }
}

/** A substitute stands in the sprite's slot (the game's own doll, [art]); it pops in, bobs, and stays until it breaks. */
class SubstituteDoll(start: Long, val art: FxRomArt?, val area: Rect = CardSpots.sprite) : Fx(start, 2000, loops = true) {
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val doll = art?.parts("substitute")?.firstOrNull()
        val sec = seconds(now)
        val k = 0.5 * Ease.outBack(min(1.0, sec * 3))
        if (card != null) Px.cover(g, s, card, area, area.x + 1, area.y + 1)
        val bob = sin(sec * 3) * 0.6
        if (doll != null) Px.image(g, s, doll, area.cx - doll.width * k / 2, area.y + area.h - doll.height * k + bob, k)
        else { g.color = Color(200, 190, 150); g.fillRect(((area.cx - 6) * s).roundToInt(), ((area.y + 10 + bob) * s).roundToInt(), (12 * s).roundToInt(), (18 * s).roundToInt()) }
    }
}

/**
 * An item used on a Pokemon: the game's item bag opens, the item's own icon (read from the ROM) rises out of it and pops
 * over the sprite with its name, then the effect its use names plays: HP back (green sparkles), a status cured (the
 * aura bursts away), a stat raised (gold arrows; the stage change itself pops in StatPop), Mist, Focus Energy (the
 * game's focus sprite), a revival (gold stars), PP back.
 */
class ItemUse(start: Long, val icon: BufferedImage?, val usage: Int, val name: String, val art: FxRomArt?, val font: Font, val area: Rect = CardSpots.sprite) : Fx(start, 1900) {
    override val usesSlot get() = true
    private val seeds = Seeds(name.hashCode())
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        // The icon drops into the slot's top-right corner and pops: over the middle it hid the Pokemon's face, and the
        // grey bag under it read as dirt (design audit). 0 to 0.35 the icon, 0.35 to 1 the effect its use names.
        val drop = Ease.outBack(min(1.0, t / 0.3))
        val pop = if (t < 0.35) 1.0 else 1 + 0.25 * sin(PI * min(1.0, (t - 0.35) / 0.25))
        val k = 0.55 * pop
        if (icon != null) Px.image(g, s, icon, area.x + area.w - 1 - 24 * k, area.y + 1 + (drop - 1) * 6 - 6 * (pop - 1), k, Ease.window(t, 0.05, 0.85))
        // The item's name in the sprite slot, under the rising icon: never out over the card's words.
        if (t > 0.3) Px.text(g, s, font, name, area.cx, area.y + area.h - 4, 6.0, Px.alpha(Color.WHITE, Ease.window((t - 0.3) / 0.7, 0.1, 0.8)), maxW = area.w - 7)
        if (t < 0.35) return
        val e = (t - 0.35) / 0.65
        when (usage) {
            1, 3 -> sparkles(g, s, e, art?.frames("green_sparkle", 16).orEmpty(), Color(120, 255, 140))
            2 -> burst(g, s, e, Color(255, 255, 255))
            4, 11 -> arrows(g, s, e)
            6 -> art?.frames("focus_energy", 16)?.takeIf { it.isNotEmpty() }?.let { f -> Px.image(g, s, f[(e * f.size).toInt().coerceIn(0, f.size - 1)], area.cx - 8, area.cy - 8, 1.0, 1 - e) } ?: arrows(g, s, e)
            9 -> sparkles(g, s, e, art?.frames("gold_stars", 16, 8).orEmpty(), Color(255, 220, 80))
            10 -> sparkles(g, s, e, art?.frames("sparkle", 32).orEmpty(), Color(150, 200, 255))
            5 -> burst(g, s, e, Color(200, 220, 255))
        }
    }
    private fun sparkles(g: Graphics2D, s: Double, e: Double, f: List<BufferedImage>, c: Color) {
        for (i in 0 until 8) {
            val age = (e * 1.3 - seeds.r(i, 1) * 0.3).coerceIn(0.0, 1.0)
            if (age <= 0 || age >= 1) continue
            val x = area.x + 2 + seeds.r(i, 2) * (area.w - 8); val y = area.y + area.h - 4 - age * (area.h + 2)
            if (f.isNotEmpty()) Px.image(g, s, f[(age * f.size).toInt().coerceIn(0, f.size - 1)], x, y, 0.5, 1 - age)
            else Px.dot(g, s, x, y, Px.alpha(c, 1 - age), 1.5)
        }
    }
    private fun burst(g: Graphics2D, s: Double, e: Double, c: Color) {
        val r = 4 + Ease.outCubic(e) * 13
        Px.ring(g, s, area.cx, area.cy, r, r * 0.9, Px.alpha(c, 1 - e))
    }
    private fun arrows(g: Graphics2D, s: Double, e: Double) {
        for (i in 0 until 3) {
            val x = area.x + 6 + i * 9.0; val y = area.y + area.h - e * 24 - i * 3
            val c = Px.alpha(Color(255, 220, 80), 1 - e)
            for (k in 0 until 3) { Px.dot(g, s, x + k, y + 2 - k, c); Px.dot(g, s, x + 4 - k, y + 2 - k, c) }
        }
    }
}

/** Where the card's move rows are (MovesArea: y 94 + 10 a row, the box from x 5 to 145). */
internal fun moveRow(i: Int) = Rect(6.0, 94.0 + 10 * i, 138.0, 9.0)

/**
 * A badge won: the card dims, rays turn behind the badge as it springs in, confetti bursts, and the banner says which
 * badge and congratulations, with the leader beaten when the run knows them. Long enough for chat to see it (3.6 s).
 */
class BadgeWin(start: Long, val badge: BufferedImage?, val title: String, val leader: String?, val font: Font, val art: FxRomArt? = null) : Fx(start, 3600) {
    private val seeds = Seeds(title.hashCode())
    private val colors = listOf(Color(255, 90, 90), Color(255, 210, 60), Color(90, 200, 255), Color(120, 230, 120), Color(230, 120, 255))
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        val fade = Ease.window(t, 0.06, 0.88)
        val c = CardSpots.card
        g.color = Color(0, 0, 0, (200 * fade).roundToInt())
        g.fillRect(0, 0, (c.w * s).roundToInt(), (c.h * s).roundToInt())
        val cx = c.cx; val cy = 62.0
        // Rays.
        val rays = 12
        for (i in 0 until rays) {
            val a = i * 2 * PI / rays + t * 1.4
            val p = java.awt.geom.Path2D.Double()
            p.moveTo(cx * s, cy * s)
            p.lineTo((cx + cos(a - 0.12) * 90) * s, (cy + sin(a - 0.12) * 90) * s)
            p.lineTo((cx + cos(a + 0.12) * 90) * s, (cy + sin(a + 0.12) * 90) * s)
            p.closePath()
            g.color = Color(255, 230, 140, (40 * fade).roundToInt()); g.fill(p)
        }
        // The badge springs in.
        val k = Ease.outElastic(min(1.0, t * 1.8)) * (1 - 0.1 * max(0.0, t - 0.85) / 0.15)
        val size = 34 * k
        if (badge != null && size > 0.5) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade.toFloat())
            g.drawImage(badge, ((cx - size / 2) * s).roundToInt(), ((cy - size / 2) * s).roundToInt(), (size * s).roundToInt(), (size * s).roundToInt(), null)
            g.composite = AlphaComposite.SrcOver
        }
        // Confetti: one burst at the spring, falling with a flutter.
        for (i in 0 until 60) {
            val vx = (seeds.r(i, 1) - 0.5) * 120; val vy = -40 - seeds.r(i, 2) * 70
            val tt = t * 3.6
            val x = cx + vx * tt * 0.55 + sin(tt * 8 + i) * 2
            val y = cy + vy * tt * 0.55 + 55 * tt * tt * 0.55
            if (y > c.h + 4 || tt > 3.2) continue
            val flip = abs(sin(tt * 10 + i))
            val pieces = art?.frames("confetti", 8).orEmpty()
            if (pieces.isNotEmpty()) Px.image(g, s, pieces[i % pieces.size], x, y, 0.5 + flip * 0.4, fade)
            else Px.dot(g, s, x, y, Px.alpha(colors[i % colors.size], fade), 0.8 + flip * 0.8)
        }
        // The banner.
        val bk = Ease.outBack(((t - 0.18) * 3).coerceIn(0.0, 1.0))
        if (bk > 0) {
            Px.text(g, s, font, "BADGE ACQUIRED", cx, 18.0, 11 * bk, Px.alpha(Color(255, 225, 90), fade), Color(70, 30, 0, (230 * fade).roundToInt()), maxW = 130.0)
            Px.text(g, s, font, title, cx, 98.0, 8 * bk, Px.alpha(Color.WHITE, fade))
            Px.text(g, s, font, "Congratulations!", cx, 110.0, 7 * bk, Px.alpha(Color(150, 230, 255), fade))
            if (leader != null) Px.text(g, s, font, "$leader defeated", cx, 121.0, 6 * bk, Px.alpha(Color(220, 220, 220), fade))
        }
    }
}

/**
 * The effects playing over the card. The window asks it [active] before running its timer at all, so a card with
 * nothing happening costs nothing; a status aura, a substitute or a lasting effect keeps it ticking while it lasts.
 */
class FxLayer(private val font: Font, private val art: FxRomArt? = null) {
    private val fx = ArrayList<Fx>()
    private var aura: StatusAura? = null
    /** When the sprite slot is next free: effects that use it take turns (one label, one icon, one burst at a time). */
    private var slotFreeAt = 0L
    /** The card last drawn: the card as it was before this read's change (the new move's old row). */
    private var lastCard: BufferedImage? = null
    /** Each side's lasting effects, kept while the card shows the other side (FxLasting: the game's own list decides). */
    private val held = mapOf(FxEvent.Side.OWN to LinkedHashMap<String, Lasting>(), FxEvent.Side.FOE to LinkedHashMap<String, Lasting>())
    private val subs = HashMap<FxEvent.Side, SubstituteDoll>()
    private var shownSide = FxEvent.Side.OWN
    /** The badge picture for a badge index and set (PcAssets.badge as an AWT image); null draws the banner alone. */
    var badgeArt: (Int, String) -> BufferedImage? = { _, _ -> null }
    /** The gym leader of a badge (the run's log knows them); null leaves that line off. */
    var leaderOf: (Int) -> String? = { null }
    /** A badge's name ("Zephyr Badge"); "Badge n" when the game's names are not known. */
    var badgeName: (Int) -> String = { "Badge ${it + 1}" }
    /** An item's name as the game spells it. */
    var itemName: (Int) -> String = { "Item $it" }
    /** The shown Pokemon's moves, each with whether it is a status move (for Disable, Taunt, Encore). */
    var rows: () -> List<Pair<String, Boolean>> = { emptyList() }
    /**
     * Where the card prints a side's ability [name] (PcBesteon: your card's ability line at y 45, the opponent's first
     * ability line at y 35); null when the card does not print it.
     */
    var abilityRow: (FxEvent.Side, String) -> Rect? = { side, _ -> if (side == FxEvent.Side.OWN) Rect(36.0, 45.0, 60.0, 9.0) else Rect(36.0, 35.0, 60.0, 9.0) }

    /**
     * The shown moves' accuracy as the card prints it ([base], 0 for a move that never misses) and the game's chance to
     * hit for each (HitChance: stages, abilities, items, weather and field), [sure] where it cannot miss.
     */
    data class AccInfo(val base: List<Int?>, val chance: List<Int?>, val sure: List<Boolean> = emptyList())
    var accuracy: () -> AccInfo? = { null }
    private var accShown: List<Int?> = emptyList()
    private var accFrom: List<Int?> = emptyList()
    private var accSince = 0L

    private fun accColumn(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val info = accuracy() ?: run { accShown = emptyList(); return }
        val target = info.base.indices.map { i -> if (info.sure.getOrElse(i) { false }) SURE else info.chance.getOrNull(i) }
        if (target != accShown) { accFrom = if (accShown.size == target.size) accShown else info.base; accShown = target; accSince = now }
        if (target.indices.all { i -> target[i] == info.base[i] || (target[i] == SURE && info.base[i] == 0) }) return
        // With battle animations off the new odds show at once, without the count or the pop.
        val t = if (com.ironmonone.app.TrackerOptions.battleAnimations) min(1.0, (now - accSince) / 6e8) else 1.0
        for ((i, b) in info.base.withIndex()) {
            val to = target.getOrNull(i) ?: continue
            if (b == null || b <= 0 || to == b) continue
            val sure = to == SURE
            val from = (accFrom.getOrNull(i) ?: b).let { if (it == SURE) 100 else it }
            val v = (from + ((if (sure) 100 else to) - from) * Ease.outCubic(t)).roundToInt()
            // A sure hit reads as the card's own "--" (a move that never misses) once the count reaches it.
            val shown = if (sure && t >= 1.0) "--" else "${v.coerceAtMost(100)}"
            val r = moveRow(i)
            val cell = Rect(127.0, r.y, 18.0, 9.0)
            if (card != null) { g.color = Px.fillAt(card, 144.0, r.y + 1); g.fillRect((cell.x * s).roundToInt(), (cell.y * s).roundToInt(), (cell.w * s).roundToInt(), (cell.h * s).roundToInt()) }
            val down = !sure && to < b
            // Amber and cyan: the Pow column beside it already says resisted and super effective in red and green.
            val c = if (down) Color(255, 175, 60) else Color(110, 210, 255)
            val pop = 1 + 0.35 * kotlin.math.sin(PI * t) * (1 - t)
            Px.text(g, s, font, shown, 134.5, r.cy + 0.5, 8.0 * pop, c, Color(0, 0, 0, 0))
            // A small arrow: down for a drop, up when the odds rose past what the card prints (a sure hit at 100).
            val ax = 143.5; val ay = r.y + 3
            for (k in 0 until 3) {
                if (down) { Px.dot(g, s, ax - 1 + k * 0.5, ay + k * 0.6, c, 0.6); Px.dot(g, s, ax + 1 - k * 0.5, ay + k * 0.6, c, 0.6) }
                else { Px.dot(g, s, ax - 1 + k * 0.5, ay + 2 - k * 0.6, c, 0.6); Px.dot(g, s, ax + 1 - k * 0.5, ay + 2 - k * 0.6, c, 0.6) }
            }
        }
    }

    private val SURE = -1

    /** The Pokemon the card shows, walked by the layer: its species (the game's ids when [hns]) and status (FNT, SLP). */
    data class Walker(val species: Int, val hns: Boolean, val status: String, val evasion: Int = 0, val accuracy: Int = 0)
    /** Null: the card draws its own (still) sprite. Set with PcPictures.cardDrawsWalking off. */
    var walker: () -> Walker? = { null }
    private val awt = java.util.WeakHashMap<androidx.compose.ui.graphics.ImageBitmap, BufferedImage>()

    /** The walking sprite at [now], moved by whatever is shaking or sinking it, and the status icon back over it. */
    private fun walk(g: Graphics2D, s: Double, now: Long, card: BufferedImage?, w: Walker) {
        val p = com.ironmonone.pcui.PcPictures.posed(w.species, w.hns, faint = w.status == "FNT", asleep = w.status == "SLP",
            maxRight = 36f - 5f - 0.5f, nanos = now) ?: return
        val img = awt.getOrPut(p.image) { p.image.toAwtImage() }
        val motions = (fx.filter { now >= it.start } + listOfNotNull(aura) + held.getValue(shownSide).values).mapNotNull { it.spriteMotion(now) }
        val dx = motions.sumOf { it.first }; val dy = motions.sumOf { it.second }; val alpha = motions.minOfOrNull { it.third } ?: 1.0
        val clip = g.clip
        if (dy > 0) g.clipRect(0, 0, (CardSpots.card.w * s).roundToInt(), (CardSpots.sprite.h * s).roundToInt())
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        // Evasion: the afterimages of a rise, then, while the stage stays up, a faint mirage swaying either side.
        val ghosts = fx.filterIsInstance<Afterimages>().flatMap { it.ghosts(now) }.ifEmpty {
            if (w.evasion > 0) { val sway = (1.2 + w.evasion * 0.6) * kotlin.math.sin(now / 1e9 * 2.4); listOf(Triple(-sway, 0.0, 0.16), Triple(sway, 0.0, 0.16)) } else emptyList()
        }
        if (ghosts.isNotEmpty()) {
            // The ghosts stop at the column of words (x 36), as the sprite itself does.
            val before = g.clip
            g.clipRect(0, 0, (36 * s).roundToInt(), (CardSpots.card.h * s).roundToInt())
            for ((gx, gy, ga) in ghosts) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (ga * alpha).toFloat().coerceIn(0f, 1f))
                g.drawImage(img, ((5 + p.x + dx + gx) * s).roundToInt(), ((-1 + p.y + dy + gy) * s).roundToInt(), (p.w * s).roundToInt(), (p.h * s).roundToInt(), null)
            }
            g.clip = before
        }
        g.composite = if (alpha < 1.0) AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.toFloat().coerceIn(0f, 1f)) else AlphaComposite.SrcOver
        g.drawImage(img, ((5 + p.x + dx) * s).roundToInt(), ((-1 + p.y + dy) * s).roundToInt(), (p.w * s).roundToInt(), (p.h * s).roundToInt(), null)
        g.composite = AlphaComposite.SrcOver
        g.clip = clip
        val area = CardSpots.sprite
        if (w.evasion < 0 && fx.none { it is EvasionDown }) {
            // Still in the sights: the red brackets, faint, breathing.
            val f = art?.frames("reticle", 16).orEmpty()
            val a = 0.6 + 0.2 * kotlin.math.sin(now / 1e9 * 3)
            if (f.size >= 5) Px.image(g, s, f[4], area.cx - 20, area.cy - 20, 2.5, a)
        }
        if (w.accuracy < 0 && fx.none { it is AccuracyDown }) {
            // Dust still in the eyes: a thin haze over the top of the sprite, more for each stage down.
            val clouds = art?.frames("gray_smoke", 32).orEmpty()
            val a = (0.12 + 0.06 * -w.accuracy).coerceAtMost(0.4)
            if (clouds.isNotEmpty()) Px.image(g, s, clouds.last(), area.x - 2 + kotlin.math.sin(now / 1e9 * 1.3) * 1.5, area.y - 6, 1.0, a)
        }
        if (card != null && w.status.isNotEmpty() && w.status != "FNT") Px.lift(g, s, card, Rect(20.0, 6.0, 16.0, 8.0))
    }

    val active: Boolean get() = synchronized(fx) { fx.isNotEmpty() || aura != null || held.values.any { it.isNotEmpty() } || walker() != null || accuracy() != null }

    /**
     * The lasting effects the game lists now, per side (FxLasting.from). New ones start, ones that stay keep running
     * with their count updated, ones the game dropped stop: cured, worn off, switched out, or the battle over.
     */
    fun sync(active: Map<FxEvent.Side, List<LastingSpec>>, now: Long) = synchronized(fx) {
        for ((side, map) in held) {
            val specs = active[side].orEmpty()
            val names = specs.map { it.name }.toSet()
            map.keys.retainAll(names)
            if ("Substitute" !in names) subs.remove(side)
            for (sp in specs) {
                val cur = map[sp.name]
                if (cur != null) { cur.spec = sp; continue }
                map[sp.name] = Lasting(now, sp, art, font) { rows() }
                if (sp.name == "Substitute") subs[side] = SubstituteDoll(now, art)
            }
        }
    }

    /** Plays [events] for the side the card shows ([shown]); lasting things (a status, a substitute, Taunt) stay until they end. */
    /**
     * A moment that uses the sprite slot, queued: [priority] orders a read's moments the way the game tells them (the hit,
     * a faint, the ability it set off, the status, the stat labels, an item, a candy, the level, the new move, effort).
     * Each starts when the slot is free; it is free again at 80% of the last one, so the tails overlap a little. A
     * backlog over 3 s (a sped-up game) drops the least important rather than running the card behind the game.
     */
    private class Moment(val priority: Int, val ms: Long, val make: (Long) -> Fx)

    private fun runMoments(list: List<Moment>, now: Long) {
        var free = maxOf(now, slotFreeAt)
        for (m in list.sortedBy { it.priority }) {
            if (free - now > 3_000_000_000L && m.priority >= 4) continue
            fx += m.make(free)
            free += m.ms * 800_000L
        }
        slotFreeAt = free
    }

    fun play(events: List<FxEvent>, shown: FxEvent.Side, now: Long) = synchronized(fx) {
        shownSide = shown
        moments.clear()
        // The two switches in Settings: battle animations, and level-up and item animations (in battle or out).
        val battle = com.ironmonone.app.TrackerOptions.battleAnimations
        val progress = com.ironmonone.app.TrackerOptions.progressAnimations
        // A candy plays first; the level-up it brings follows it.
        val candy = events.any { it is FxEvent.Candy }
        levelled = events.any { it is FxEvent.LevelUp }
        for (e in events) when (e) {
            is FxEvent.LevelUp, is FxEvent.Badge, FxEvent.League, is FxEvent.MoveLearned, is FxEvent.Effort, is FxEvent.Candy ->
                if (progress) progressEvent(e, shown, now, candy)
            is FxEvent.ItemUsed -> if ((art?.itemUsage(e.item) ?: 0) == 0) { if (progress && e.side == shown) moments += Moment(5, 1900) { t -> ItemUse(t, art?.itemIcon(e.item), 0, itemName(e.item), art, font) } }
                else if (battle && e.side == shown) moments += Moment(5, 1900) { t -> ItemUse(t, art?.itemIcon(e.item), art?.itemUsage(e.item) ?: 0, itemName(e.item), art, font) }
            else -> if (battle) battleEvent(e, shown, now)
        }
        runMoments(moments, now)
    }

    private val moments = ArrayList<Moment>()

    private var levelled = false

    /** The item an effort change came from, for its icon: a vitamin (+10), a feather (+1 to +9) or a berry (minus). */
    var effortItem: (String, Int) -> Int? = { _, _ -> null }
    /** The candy a level outside battle came from, for its icon (a Rare Candy for one level). */
    var candyItem: (Int) -> Int? = { null }

    private fun progressEvent(e: FxEvent, shown: FxEvent.Side, now: Long, candy: Boolean) {
        when (e) {
            is FxEvent.LevelUp -> if (shown == FxEvent.Side.OWN) moments += Moment(7, 1500) { t -> LevelUp(t, e.to, font) }
            is FxEvent.Badge -> fx += BadgeWin(now, badgeArt(e.index, e.set), badgeName(e.index), leaderOf(e.index), font, art)
            // After the level-up, if one came with it (the queue's order); the old row shows until its turn.
            is FxEvent.MoveLearned -> if (shown == FxEvent.Side.OWN) { val old = lastCard; moments += Moment(8, 1900) { t -> NewMove(t, e.slot, e.levelUp, font, art, oldCard = old) } }
            is FxEvent.Effort -> if (shown == FxEvent.Side.OWN) {
                val item = effortItem(e.stat, e.delta)
                moments += Moment(9, 1900) { t -> EffortChange(t, e.stat, e.delta, item?.let { art?.itemIcon(it) }, item?.let(itemName) ?: "", font, art) }
            }
            is FxEvent.Candy -> if (shown == FxEvent.Side.OWN) {
                val item = candyItem(e.levels)
                moments += Moment(6, 900) { t -> CandyPop(t, item?.let { art?.itemIcon(it) }, item?.let(itemName) ?: "", font, art) }
            }
            else -> Unit
        }
    }

    private fun battleEvent(e: FxEvent, shown: FxEvent.Side, now: Long) {
        when (e) {
            is FxEvent.Status -> if (e.side == shown) {
                aura = if (e.kind.isEmpty()) null else StatusAura(now, e.kind, art)
                if (e.kind.isNotEmpty()) moments += Moment(3, 1100) { t -> StatusOnset(t, e.kind, font) }
            }
            is FxEvent.Stage -> if (e.side == shown) {
                // Accuracy and evasion share a line the card shows only while one of them is off 0; back at 0 it shows
                // BST there, and a pop would light up BST (design audit).
                val w = walker()
                val accLine = e.stat != "ACC" && e.stat != "EVA" || w == null || w.accuracy != 0 || w.evasion != 0
                if (accLine) fx += StatPop(now, e.stat, e.delta, font, foe = shown == FxEvent.Side.FOE)
                if (e.stat == "EVA" && e.delta > 0) moments += Moment(4, 1500) { t -> Afterimages(t, e.delta, font) }
                if (e.stat == "EVA" && e.delta < 0) moments += Moment(4, 1500) { t -> EvasionDown(t, e.delta, art, font) }
                if (e.stat == "ACC" && e.delta < 0) moments += Moment(4, 1500) { t -> AccuracyDown(t, e.delta, art, font) }
            }
            is FxEvent.Damage -> if (e.side == shown) moments += Moment(0, if (e.big) 900 else 650) { t -> Hit(t, e.amount, e.big, font) }
            is FxEvent.Heal -> Unit
            is FxEvent.Faint -> if (e.side == shown) { aura = null; moments += Moment(1, 1000) { t -> Faint(t) } }
            is FxEvent.Ability -> {
                val look = Abilities.of(e.name)
                // On the holder's card the reveal; Intimidate also plays on the card it lowers.
                if (e.side == shown) moments += Moment(2, 2000) { t -> AbilityReveal(t, e.name, look, abilityRow(e.side, e.name), art, font) }
                else if (look.kind == AbilityKind.INTIMIDATE) moments += Moment(2, 1400) { t -> Intimidated(t, art) }
            }
            else -> Unit
        }
    }

    /**
     * The card shown changed (your lead to the opponent): the aura is the new side's, set by [status] (empty: none); its
     * lasting effects were kept and show again.
     */
    fun showing(side: FxEvent.Side, status: String, now: Long) = synchronized(fx) {
        shownSide = side
        aura = if (status.isEmpty()) null else StatusAura(now, status, art)
    }

    /**
     * Whether the card is on its main view (the Pokemon's card, not Settings, the log or an info screen). The layer
     * draws over that view only: its spots (the sprite slot, the stat rows, the move rows) are that view's.
     */
    var onCard: () -> Boolean = { true }

    /** Draws everything playing at [now] over the card (already drawn), dropping what has finished. */
    fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) = synchronized(fx) {
        fx.removeAll { it.done(now) }
        if (!onCard()) return@synchronized
        val battleOn = com.ironmonone.app.TrackerOptions.battleAnimations
        lastCard = card
        val lasting = held.getValue(shownSide)
        val sub = subs[shownSide]
        // A takeover (the badge) plays alone: nothing else draws under its dimmed card (design audit).
        val takeover = fx.firstOrNull { it is BadgeWin && now >= it.start }
        val w = if ((sub == null || !battleOn) && takeover == null) walker() else null
        for (f in fx) f.liveSprite = w != null
        aura?.liveSprite = w != null
        if (w != null) walk(g, s, now, card, w)
        val unclipped = g.clip
        val f = CardSpots.frame
        g.clipRect((f.x * s).roundToInt(), (f.y * s).roundToInt(), (f.w * s).roundToInt(), (f.h * s).roundToInt())
        accColumn(g, s, now, card)
        if (!effects) { g.clip = unclipped; return@synchronized }
        if (takeover != null) { takeover.draw(g, s, now, card); g.clip = unclipped; return@synchronized }
        // While a moment has the slot, the lasting looks in it go quiet; over the head, one at a time, turning every 2 s.
        val busy = fx.any { it.usesSlot && now >= it.start && !it.done(now) }
        val heads = lasting.values.filter { it.name in Lasting.HEAD }
        val headTurn = if (heads.isEmpty()) null else heads[((now / 2_000_000_000L) % heads.size).toInt()]
        if (battleOn) drawLocks(g, s, now, lasting)
        // The effects go on a layer of their own, then onto the card with an edge round them (Edges): their colours are
        // their own, so on a theme of the same colour they would melt into the card.
        val layer = if (card != null) Edges.layer(g, s) else null
        val e2 = layer?.graphics ?: g
        if (battleOn) {
            sub?.draw(e2, s, now, card)
            aura?.draw(e2, s, now, card)
            for (l in lasting.values) { l.liveSprite = w != null; l.quiet = busy; l.headTurn = l === headTurn; l.draw(e2, s, now, card) }
        }
        // A queued effect waits for its start; one with a waiting look (the new move's old row) shows it meanwhile.
        for (e in fx.toList()) if (now >= e.start) e.draw(e2, s, now, card) else e.drawWaiting(e2, s, now, card)
        if (layer != null && card != null) layer.finish(g, s, edges, card, cardOffset(now))
        g.clip = unclipped
    }

    /** False draws the effects straight onto the card, with no edge (for the tests that show what the edge buys). */
    var edges = true
    /** False draws the walking sprite and the accuracy column but none of the effects (the tests' bare card). */
    var effects = true

    private class Lock(val color: Color, val tag: String)

    /**
     * Moves the game will not let the shown Pokemon use, on their rows: each blocked row dims under a padlock in its
     * lock's colour (Disable red, Encore magenta, Taunt orange, recharging white), Encore's own move glows, and the locks'
     * names take turns in the gap of the moves header. Torment and a rampage (Outrage, Thrash, Petal Dance) show their
     * name only: which move they hold needs the battler's last move, not read yet. Imprison is not read at all yet.
     */
    private fun drawLocks(g: Graphics2D, s: Double, now: Long, set: Map<String, Lasting>) {
        if (set.isEmpty()) return
        val names = rows()
        val blocked = List(names.size) { ArrayList<Lock>() }
        val tags = ArrayList<Pair<String, Color>>()
        val sec = now / 1e9
        for (l in set.values) {
            val n = l.spec.count
            fun tag(t: String, c: Color) = tags.add((t + (n?.let { " $it" } ?: "")) to c)
            fun block(c: Color, t: String, which: (Int) -> Boolean) { tag(t, c); names.indices.filter(which).forEach { blocked[it] += Lock(c, t) } }
            when (l.name) {
                "Disable" -> block(Color(255, 120, 110), "DISABLE") { names[it].first.equals(l.spec.move, true) }
                "Encore" -> {
                    block(Color(240, 110, 230), "ENCORE") { !names[it].first.equals(l.spec.move, true) }
                    names.indexOfFirst { it.first.equals(l.spec.move, true) }.takeIf { it >= 0 }?.let { i ->
                        Px.box(g, s, moveRow(i), Px.alpha(Color(240, 110, 230), 0.45 + 0.35 * sin(sec * 5)))
                    }
                }
                "Taunt" -> block(Color(255, 140, 70), "TAUNT") { names[it].second }
                "Recharging" -> block(Color(230, 230, 230), "RECHARGE") { true }
                "Torment" -> tag("TORMENT", Color(200, 130, 255))
                "Must Attack" -> tag("LOCKED IN", Color(90, 220, 200))
            }
        }
        for ((i, locks) in blocked.withIndex()) {
            if (locks.isEmpty()) continue
            val r = moveRow(i)
            g.color = Color(0, 0, 0, 120); g.fillRect((r.x * s).roundToInt(), (r.y * s).roundToInt(), (r.w * s).roundToInt(), (r.h * s).roundToInt())
            // The padlock over the row's first icon: the row is out of play, the lock says why by its colour.
            padlock(g, s, r.x + 1.5, r.y + 1, locks.first().color)
        }
        if (tags.isNotEmpty()) {
            // One name at a time in the header's gap (between "Moves n/n" and "PP"), turning every 1.6 s.
            val (t, c) = tags[((sec / 1.6).toInt()) % tags.size]
            Px.text(g, s, font, t, 68.0, 86.0, 6 * (1 + 0.06 * sin(sec * 6)), c, maxW = 34.0)
        }
    }

    private fun padlock(g: Graphics2D, s: Double, x: Double, y: Double, c: Color) {
        val shape = listOf("01110", "10001", "10001", "11111", "11011", "11111")
        g.color = Color(0, 0, 0, 160); g.fillRect(((x - 0.5) * s).roundToInt(), ((y - 0.5) * s).roundToInt(), (6 * s).roundToInt(), (8.2 * s).roundToInt())
        shape.forEachIndexed { r, line -> line.forEachIndexed { k, ch -> if (ch == '1') Px.dot(g, s, x + k, y + r * 1.2, c) } }
    }

    /** The whole card's shake this moment (the hardest hit playing). */
    fun cardOffset(now: Long): Pair<Double, Double> = synchronized(fx) {
        fx.map { it.cardOffset(now) }.maxByOrNull { abs(it.first) + abs(it.second) } ?: (0.0 to 0.0)
    }
}
