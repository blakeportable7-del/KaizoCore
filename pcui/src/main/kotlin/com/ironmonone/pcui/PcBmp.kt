package com.ironmonone.pcui

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * A drawn picture as a plain 24-bit BMP, the hand-off to kaizocore-hns.lua (2026-10-07): BizHawk loads a BMP by copying
 * it, where a PNG must be decoded, and writing one takes a couple of milliseconds where encoding a PNG took 13 to 85 ms
 * (measured on the panel at 1x and 4x). The panel is opaque, so the alpha is dropped.
 */
object PcBmp {
    /**
     * How many times smaller than the window the panel is drawn, for a window [scale]: no more than 4 picture pixels a
     * game pixel, so a maximized window (7.4 a game pixel on a 2880-wide screen, Blake's BizHawk) draws a 555 by 591
     * picture, not a 1108 by 1182 one: 60 to 125 ms a click became 8 to 20. BizHawk stretches the picture to the window
     * pixel for pixel (nearest neighbour: a grab of it stretched there and one stretched here differed in 0 of 1,746,528
     * pixels, 2026-10-07), so text stays a hard pixel edge; at 4x and below nothing changes.
     */
    fun upscale(scale: Float): Int = kotlin.math.ceil(scale / 4f - 0.001f).toInt().coerceAtLeast(1)

    fun of(img: Image, k: Int = 1): ByteArray {
        val w = img.width; val h = img.height
        val bmp = Bitmap()
        bmp.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        img.readPixels(bmp, 0, 0)
        val px = bmp.readPixels(bmp.imageInfo, w * 4, 0, 0) ?: ByteArray(w * h * 4)
        bmp.close()
        return encode(px, w, h, k)
    }

    /** [bgra] rows top to bottom, 4 bytes a pixel, as a bottom-up 24-bit BMP, each pixel repeated [k] by [k] times. */
    fun encode(bgra: ByteArray, sw: Int, sh: Int, k: Int = 1): ByteArray {
        val w = sw * k; val h = sh * k
        val row = (w * 3 + 3) and 3.inv()
        val size = 54 + row * h
        val out = ByteArray(size)
        fun le32(o: Int, v: Int) { out[o] = v.toByte(); out[o + 1] = (v shr 8).toByte(); out[o + 2] = (v shr 16).toByte(); out[o + 3] = (v shr 24).toByte() }
        out[0] = 'B'.code.toByte(); out[1] = 'M'.code.toByte()
        le32(2, size); le32(10, 54); le32(14, 40); le32(18, w); le32(22, h)
        out[26] = 1; out[28] = 24
        le32(34, row * h); le32(38, 2835); le32(42, 2835)
        for (sy in 0 until sh) {
            val first = 54 + (h - 1 - sy * k) * row
            var o = first
            var i = sy * sw * 4
            for (x in 0 until sw) {
                val b = bgra[i]; val g = bgra[i + 1]; val r = bgra[i + 2]
                for (j in 0 until k) { out[o] = b; out[o + 1] = g; out[o + 2] = r; o += 3 }
                i += 4
            }
            // The other k - 1 rows of this one are copies of it (bottom-up: the rows above it in the file).
            for (j in 1 until k) System.arraycopy(out, first, out, first - j * row, row)
        }
        return out
    }
}
