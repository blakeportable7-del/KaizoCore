package com.ironmonone.app

/**
 * The pictures behind "Play as your Pokemon", as plain pixels, with no Android in them so the
 * rules are tested on the JVM: how one picture is fitted into the player's box, and how a set of
 * sheets is read (Sprite Is Me's own sheet format, which is the Walking Pals one).
 *
 * The box is what the extension draws against: 32x32 game pixels, centred on the player and
 * resting on its feet. Art is placed against it by an offset from the box's top-left corner.
 */
class ArtPixels(val w: Int, val h: Int, val argb: IntArray) {
    init { require(w >= 0 && h >= 0 && argb.size == w * h) { "a ${w}x$h picture needs ${w * h} pixels, not ${argb.size}" } }

    val isEmpty: Boolean get() = w == 0 || h == 0

    operator fun get(x: Int, y: Int): Int = argb[y * w + x]

    override fun equals(other: Any?) = other is ArtPixels && other.w == w && other.h == h && other.argb.contentEquals(argb)
    override fun hashCode() = 31 * (31 * w + h) + argb.contentHashCode()
}

object SpriteArt {
    /** The player's box, in game pixels. */
    const val BOX = 32
    /** The biggest frame the emulator side draws. */
    const val MAX_FRAME = 128

    /** A picture ready for the game: its pixels and where its top-left sits against the box's. */
    class Fitted(val pixels: ArtPixels, val ox: Int, val oy: Int)

    private fun alpha(c: Int) = (c ushr 24) and 255

    /** The box around every pixel that is not fully transparent, as left, top, right, bottom (inclusive); null when nothing shows. */
    private fun shown(p: ArtPixels): IntArray? {
        var top = p.h; var bottom = -1; var left = p.w; var right = -1
        for (y in 0 until p.h) for (x in 0 until p.w) if (alpha(p[x, y]) != 0) {
            if (y < top) top = y
            if (y > bottom) bottom = y
            if (x < left) left = x
            if (x > right) right = x
        }
        return if (bottom < 0) null else intArrayOf(left, top, right, bottom)
    }

    /** The picture without its fully transparent border; the same picture when it has none, empty when nothing shows. */
    fun trim(p: ArtPixels): ArtPixels {
        val (left, top, right, bottom) = shown(p) ?: return ArtPixels(0, 0, IntArray(0))
        if (top == 0 && left == 0 && bottom == p.h - 1 && right == p.w - 1) return p
        return crop(p, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * One frame of a Walking Pals sheet as the emulator side takes it, no side past [MAX_FRAME], drawn at [ox], [oy]
     * from the box's corner. A frame that fits is left as it is. A bigger one (a few later sheets have them, Mega
     * Rayquaza's 144 x 136 or Eternamax's 128 x 192, mostly clear) loses its clear border and its offset moves by as
     * much, so every pixel lands where it would have; what still does not fit keeps its middle columns and its bottom
     * rows, where the feet are.
     */
    fun placeFrame(p: ArtPixels, ox: Int, oy: Int): Fitted {
        if (p.w <= MAX_FRAME && p.h <= MAX_FRAME) return Fitted(p, ox, oy)
        val (left, top, right, bottom) = shown(p) ?: return Fitted(ArtPixels(1, 1, intArrayOf(0)), ox, oy)
        var x = left; var y = top; var w = right - left + 1; var h = bottom - top + 1
        if (w > MAX_FRAME) { x += (w - MAX_FRAME) / 2; w = MAX_FRAME }
        if (h > MAX_FRAME) { y += h - MAX_FRAME; h = MAX_FRAME }
        return Fitted(crop(p, x, y, w, h), ox + x, oy + y)
    }

    /** [w] x [h] pixels of [p] from ([x], [y]); anything outside the picture is transparent. */
    fun crop(p: ArtPixels, x: Int, y: Int, w: Int, h: Int): ArtPixels {
        val out = IntArray(maxOf(0, w) * maxOf(0, h))
        for (yy in 0 until h) for (xx in 0 until w) {
            val sx = x + xx; val sy = y + yy
            if (sx in 0 until p.w && sy in 0 until p.h) out[yy * w + xx] = p[sx, sy]
        }
        return ArtPixels(maxOf(0, w), maxOf(0, h), out)
    }

    fun flipH(p: ArtPixels): ArtPixels {
        val out = IntArray(p.argb.size)
        for (y in 0 until p.h) for (x in 0 until p.w) out[y * p.w + (p.w - 1 - x)] = p[x, y]
        return ArtPixels(p.w, p.h, out)
    }

    /**
     * Fit [src] inside [box] x [box] game pixels, keeping its shape, with its feet (the bottom of
     * what shows) on the box's bottom edge and its middle on the box's middle:
     *  - a picture that already fits is enlarged by a whole number of times, never blurred, so
     *    pixel art stays crisp (a 16x16 sprite becomes 32x32);
     *  - a small picture too big for the box (up to twice its size, so pixel art as big as 64x64)
     *    is shrunk by nearest neighbour, crisp;
     *  - a big picture is shrunk by averaging, so a photo does not turn to noise.
     * A fully transparent border is trimmed first, so a sprite with a wide margin is not drawn small
     * and floating; a picture with nothing in it gives an empty result.
     */
    fun fit(src: ArtPixels, box: Int = BOX): Fitted {
        require(box in 8..MAX_FRAME) { "box $box" }
        val p = trim(src)
        if (p.isEmpty) return Fitted(ArtPixels(1, 1, intArrayOf(0)), BOX / 2, BOX - 1)
        val scale = minOf(box.toDouble() / p.w, box.toDouble() / p.h)
        val out: ArtPixels = when {
            scale >= 1.0 -> scaleNearest(p, p.w * scale.toInt(), p.h * scale.toInt())
            maxOf(p.w, p.h) <= 2 * box -> scaleNearest(p, maxOf(1, Math.round(p.w * scale).toInt()), maxOf(1, Math.round(p.h * scale).toInt()))
            else -> scaleArea(p, maxOf(1, Math.round(p.w * scale).toInt()), maxOf(1, Math.round(p.h * scale).toInt()))
        }
        return Fitted(out, (BOX - out.w) / 2, BOX - out.h)
    }

    /** Nearest neighbour to [w] x [h]. */
    fun scaleNearest(p: ArtPixels, w: Int, h: Int): ArtPixels {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy = minOf(p.h - 1, ((y + 0.5) * p.h / h).toInt())
            for (x in 0 until w) {
                val sx = minOf(p.w - 1, ((x + 0.5) * p.w / w).toInt())
                out[y * w + x] = p[sx, sy]
            }
        }
        return ArtPixels(w, h, out)
    }

    /** Shrink to [w] x [h] by averaging every source pixel a destination pixel covers, weighted by alpha so edges do not darken. */
    fun scaleArea(p: ArtPixels, w: Int, h: Int): ArtPixels {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = y * p.h.toDouble() / h; val y1 = (y + 1) * p.h.toDouble() / h
            for (x in 0 until w) {
                val x0 = x * p.w.toDouble() / w; val x1 = (x + 1) * p.w.toDouble() / w
                var sumA = 0.0; var sumR = 0.0; var sumG = 0.0; var sumB = 0.0; var weight = 0.0
                for (sy in y0.toInt() until minOf(p.h, Math.ceil(y1).toInt())) {
                    val wy = minOf(y1, sy + 1.0) - maxOf(y0, sy.toDouble())
                    for (sx in x0.toInt() until minOf(p.w, Math.ceil(x1).toInt())) {
                        val wx = minOf(x1, sx + 1.0) - maxOf(x0, sx.toDouble())
                        val c = p[sx, sy]
                        val a = alpha(c) * wx * wy
                        sumA += a
                        sumR += ((c shr 16) and 255) * a; sumG += ((c shr 8) and 255) * a; sumB += (c and 255) * a
                        weight += wx * wy
                    }
                }
                out[y * w + x] = if (sumA <= 0.0 || weight <= 0.0) 0 else {
                    val a = Math.round(sumA / weight).toInt().coerceIn(0, 255)
                    val r = Math.round(sumR / sumA).toInt().coerceIn(0, 255)
                    val g = Math.round(sumG / sumA).toInt().coerceIn(0, 255)
                    val b = Math.round(sumB / sumA).toInt().coerceIn(0, 255)
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return ArtPixels(w, h, out)
    }

    /** How far a single picture is lifted, in pixels: nothing standing, and one pixel on every other step while walking ([step] counts steps). */
    fun bobOffset(walking: Boolean, step: Int): Int = if (walking && step % 2 == 1) -1 else 0
}

/**
 * Sprite Is Me's custom sprite format: a sheet per animation (idle, walk, sleep, faint) with the frames
 * side by side. Idle and walk carry eight rows, one per facing (down, down-right, right, up-right, up,
 * up-left, left, down-left, as the extension's Input.getSpriteFacingDirection numbers them); sleep and
 * faint carry one. The extension takes one frame width and height for all four, and a list of frame
 * lengths (in game frames) for each. Blank means "work it out".
 */
object SheetSet {
    val ANIMS: List<WalkingPals.Anim> = WalkingPals.Anim.entries

    /** What the player typed: a frame size (0 = automatic) and each animation's frame lengths (blank = the default). */
    data class Spec(
        val width: Int = 0, val height: Int = 0,
        val idle: String = "", val walk: String = "", val sleep: String = "", val faint: String = "",
    ) {
        fun lengthsText(a: WalkingPals.Anim): String = when (a) {
            WalkingPals.Anim.IDLE -> idle; WalkingPals.Anim.WALK -> walk; WalkingPals.Anim.SLEEP -> sleep; WalkingPals.Anim.FAINT -> faint
        }
    }

    /** Game frames a frame lasts when nothing says: a slow idle, a brisk walk, a slow sleep, a quick faint. */
    val DEFAULT_LENGTH = mapOf(WalkingPals.Anim.IDLE to 24, WalkingPals.Anim.WALK to 6, WalkingPals.Anim.SLEEP to 30, WalkingPals.Anim.FAINT to 12)

    /** Which animation a file or folder name belongs to: "idle/Me.png", "Me_walk.png", "SLEEP.PNG". Null for neither. */
    fun classify(path: String): WalkingPals.Anim? {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        fun anim(name: String): WalkingPals.Anim? {
            val tokens = name.substringBeforeLast('.').lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            for (t in tokens.asReversed()) for (a in ANIMS) if (t.startsWith(a.key)) return a
            return null
        }
        return anim(parts.last()) ?: parts.dropLast(1).asReversed().firstNotNullOfOrNull { anim(it) }
    }

    /**
     * From the files a player picked (name and bytes, zip entries already unpacked) to the sheets to keep,
     * one per animation: the first file of each kind wins. Only PNGs, since a sheet needs its transparency.
     */
    fun choose(files: List<Pair<String, ByteArray>>): Map<WalkingPals.Anim, ByteArray> {
        val out = LinkedHashMap<WalkingPals.Anim, ByteArray>()
        for ((name, bytes) in files) {
            val a = classify(name) ?: continue
            if (!isPng(bytes) || a in out) continue
            out[a] = bytes
        }
        return out
    }

    fun isPng(b: ByteArray): Boolean = b.size > 24 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    /**
     * The biggest sheet that is kept and read: eight rows of the biggest frame (all [rowFor] reads), sixteen frames of it
     * across, 8 MB once decoded. Every Walking Pals sheet of Gen 1 to 3 fits. A PNG may say 8192 x 8192, a 256 MB
     * decode, which froze the game for seconds or got the app closed (rc32 audit P2 #88, P3 #66).
     */
    const val MAX_SHEET_W = 16 * SpriteArt.MAX_FRAME
    const val MAX_SHEET_H = 8 * SpriteArt.MAX_FRAME

    /** A sheet of [size] (width, height) is within the bound. */
    fun fits(size: Pair<Int, Int>): Boolean = size.first <= MAX_SHEET_W && size.second <= MAX_SHEET_H

    /** A PNG's width and height from its header, or null when it is not one. */
    fun pngSize(b: ByteArray): Pair<Int, Int>? {
        if (!isPng(b)) return null
        fun be(o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)
        val w = be(16); val h = be(20)
        return if (w in 1..8192 && h in 1..8192) w to h else null
    }

    /** Frame lengths from "40, 6 6": every whole number in it, or null when there is none. */
    fun parseLengths(text: String): IntArray? {
        val nums = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.filter { it in 1..600 }.toList()
        return nums.takeIf { it.isNotEmpty() }?.toIntArray()
    }

    /** How one sheet is laid out. */
    class Geometry(val frameW: Int, val frameH: Int, val cols: Int, val rows: Int)

    /**
     * The frame height for every sheet: what was typed, else the height of a sleep or faint sheet (one row, so
     * the frame's height exactly), else an idle or walk sheet's height over its eight rows.
     */
    fun frameHeight(sizes: Map<WalkingPals.Anim, Pair<Int, Int>>, spec: Spec): Int {
        if (spec.height > 0) return spec.height.coerceIn(1, SpriteArt.MAX_FRAME)
        (sizes[WalkingPals.Anim.SLEEP] ?: sizes[WalkingPals.Anim.FAINT])?.let { return it.second.coerceIn(1, SpriteArt.MAX_FRAME) }
        val tall = sizes[WalkingPals.Anim.IDLE] ?: sizes[WalkingPals.Anim.WALK] ?: return SpriteArt.BOX
        return if (tall.second % 8 == 0 && tall.second / 8 >= 1) (tall.second / 8).coerceIn(1, SpriteArt.MAX_FRAME) else tall.second.coerceIn(1, SpriteArt.MAX_FRAME)
    }

    fun geometry(size: Pair<Int, Int>, spec: Spec, frameH: Int): Geometry? {
        val h = frameH
        val w = (if (spec.width > 0) spec.width else h).coerceIn(1, SpriteArt.MAX_FRAME)
        val cols = size.first / w
        val rows = size.second / h
        return if (cols >= 1 && rows >= 1) Geometry(w, h, cols, rows) else null
    }

    /**
     * The sheets as the animator wants them (the Walking Pals table's own type), from each sheet's pixel size.
     * Frames sit against the box with their middle on the player's and their bottom on its feet. An animation the
     * set lacks is filled from another (walk from idle and back, sleep and faint from idle) so a set with only
     * one of them still moves; null when the set has nothing usable.
     */
    fun sheets(sizes: Map<WalkingPals.Anim, Pair<Int, Int>>, spec: Spec): Map<WalkingPals.Anim, WalkingPals.Sheet>? {
        if (sizes.isEmpty()) return null
        val frameH = frameHeight(sizes, spec)
        val out = LinkedHashMap<WalkingPals.Anim, WalkingPals.Sheet>()
        for ((a, size) in sizes) {
            val g = geometry(size, spec, frameH) ?: continue
            val typed = parseLengths(spec.lengthsText(a))
            val lengths = IntArray(g.cols) { i -> typed?.let { it[minOf(i, it.lastIndex)] } ?: DEFAULT_LENGTH.getValue(a) }
            out[a] = WalkingPals.Sheet(g.frameW, g.frameH, (SpriteArt.BOX - g.frameW) / 2, SpriteArt.BOX - g.frameH, lengths)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** The animation to show when [want] is missing: the nearest one the set has, or null. */
    fun substitute(want: WalkingPals.Anim, have: Set<WalkingPals.Anim>): WalkingPals.Anim? = when {
        want in have -> want
        want == WalkingPals.Anim.WALK && WalkingPals.Anim.IDLE in have -> WalkingPals.Anim.IDLE
        want == WalkingPals.Anim.IDLE && WalkingPals.Anim.WALK in have -> WalkingPals.Anim.WALK
        WalkingPals.Anim.IDLE in have -> WalkingPals.Anim.IDLE
        WalkingPals.Anim.WALK in have -> WalkingPals.Anim.WALK
        else -> null
    }

    /**
     * The sheet row for a facing (1 down, 2 up, 3 left, 4 right as the game numbers them): the extension's row when
     * the sheet has its eight, the first row when it has fewer.
     */
    fun rowFor(facing: Int, rows: Int): Int = if (rows < 8) 0 else when (facing) {
        2 -> 4; 3 -> 6; 4 -> 2; else -> 0
    }
}
