package com.ironmonone.tracker

import com.ironmonone.tracker.OverworldMath.Fade
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The per-frame maths of "Play as your Pokemon", worked out by hand and asserted as plain numbers: the game's own shift
 * and blend, the OAM size table, the player's box from the fields UpdateOamCoords reads, which OAM entries are the
 * trainer's, each pixel format's colours, and what drawing does at the edges. No ROM, no emulator, no C++: this is the
 * JVM's answer, and SpriteCoreWasmTest and SpriteCoreParityTest (in :app) hold the native code to it. Every expected
 * value below is arithmetic done on paper first (the working is in the comment beside it), not read back from the code.
 */
class OverworldMathTest {
    private val M = OverworldMath

    // ------------------------------------------------------------------ the game's shift

    @Test
    fun `the game's shift by 16 rounds toward minus infinity, as a C int shift does`() {
        assertEquals(0, M.floorDiv16(0)); assertEquals(0, M.floorDiv16(15)); assertEquals(1, M.floorDiv16(16)); assertEquals(1, M.floorDiv16(31))
        assertEquals(-1, M.floorDiv16(-1)); assertEquals(-1, M.floorDiv16(-15)); assertEquals(-1, M.floorDiv16(-16))
        assertEquals(-2, M.floorDiv16(-17))
        assertEquals(-16, M.floorDiv16(-248), "-248 / 16 is -15.5, and the shift takes it to -16, not -15")
        assertEquals(15, M.floorDiv16(248))
    }

    // ------------------------------------------------------------------ OAM sizes

    @Test
    fun `tile counts are the GBA's shape and size table, and the prohibited shape has none`() {
        // shape 0 is square: 8x8 = 1 tile, 16x16 = 4, 32x32 = 16, 64x64 = 64
        assertEquals(listOf(1, 4, 16, 64), (0..3).map { M.tileCount(0, it) })
        // shape 1 is wide: 16x8 = 2, 32x8 = 4, 32x16 = 8, 64x32 = 32
        assertEquals(listOf(2, 4, 8, 32), (0..3).map { M.tileCount(1, it) })
        // shape 2 is tall: 8x16 = 2, 8x32 = 4, 16x32 = 8, 32x64 = 32 (the player is shape 2, size 2: 16x32, 8 tiles)
        assertEquals(listOf(2, 4, 8, 32), (0..3).map { M.tileCount(2, it) })
        assertEquals(listOf(0, 0, 0, 0), (0..3).map { M.tileCount(3, it) })
        // only the low two bits of each are looked at
        assertEquals(8, M.tileCount(2 + 4, 2 + 8))
    }

    // ------------------------------------------------------------------ BlendPalette

    /** channel 0 is the low five bits (red on the GBA), then green, then blue. */
    private fun rgb(r: Int, g: Int, b: Int) = r or (g shl 5) or (b shl 10)

    @Test
    fun `BlendPalette, by hand`() {
        assertEquals(0x7FFF, M.gameBlend(0x7FFF, 0, 0), "coefficient 0 is no fade")
        assertEquals(0x7FFF, M.gameBlend(0x7FFF, 0, 0x1234))
        assertEquals(0, M.gameBlend(0x7FFF, 16, 0), "31 + ((0 - 31) * 16 >> 4) = 31 - 31 = 0")
        assertEquals(0x7FFF, M.gameBlend(0, 16, 0x7FFF), "0 + (31 * 16 >> 4) = 31")
        assertEquals(rgb(0x1B, 0x03, 0x11), M.gameBlend(rgb(0x1B, 0x03, 0x11), 16, rgb(0x1B, 0x03, 0x11)), "fading to itself changes nothing")
        // half way from white to black: 31 + ((0 - 31) * 8 >> 4) = 31 + (-248 >> 4) = 31 - 16 = 15 in each channel
        assertEquals(rgb(15, 15, 15), M.gameBlend(0x7FFF, 8, 0))
        assertEquals(0x3DEF, M.gameBlend(0x7FFF, 8, 0))
        // half way from black to white: 0 + (31 * 8 >> 4) = 15
        assertEquals(0x3DEF, M.gameBlend(0, 8, 0x7FFF))
        // The shift is taken on a negative number for a channel above the target and on a positive one for below, so the
        // fade is not symmetric: 1 -> 0 at half strength (1 + (-8 >> 4) = 1 - 1), 0 -> 1 stays 0 (0 + (8 >> 4) = 0).
        assertEquals(0, M.gameBlend(rgb(1, 0, 0), 8, 0))
        assertEquals(0, M.gameBlend(0, 8, rgb(1, 0, 0)))
        assertEquals(1, M.gameBlend(rgb(2, 0, 0), 8, 0), "2 + (-16 >> 4) = 1")
        // Each channel on its own: (10, 20, 30) a quarter of the way to black, coefficient 4:
        //   red   10 + (-40  >> 4) = 10 - 3 = 7    (-2.5 goes down to -3)
        //   green 20 + (-80  >> 4) = 20 - 5 = 15
        //   blue  30 + (-120 >> 4) = 30 - 8 = 22   (-7.5 goes down to -8)
        assertEquals(rgb(7, 15, 22), M.gameBlend(rgb(10, 20, 30), 4, 0))
        assertEquals(23015, M.gameBlend(rgb(10, 20, 30), 4, 0))
        // Towards a colour with its own channels: (0, 0, 0) to (31, 16, 0) at 8: 15, 8, 0
        assertEquals(rgb(15, 8, 0), M.gameBlend(0, 8, rgb(31, 16, 0)))
    }

    // ------------------------------------------------------------------ reading a fade back

    /** Fifteen colours that between them cover the range in every channel, the way an OBJ palette does. */
    private val palette = IntArray(15) { i -> rgb((i * 2) % 32, (i * 5 + 3) % 32, 31 - (i * 2) % 32) }

    private fun fadeAll(p: IntArray, f: Fade) = IntArray(p.size) { M.gameBlend(p[it], f.coeff, f.color) }

    @Test
    fun `a fade is read back from the two palettes, for every coefficient and several colours`() {
        for (coeff in 1..16) for (color in listOf(0, 0x7FFF, rgb(31, 0, 0), rgb(5, 17, 28), rgb(31, 31, 0))) {
            val shown = fadeAll(palette, Fade(coeff, color))
            val got = assertNotNull(M.estimateFade(palette, shown), "coeff $coeff colour %04x".format(color))
            // What is read back must reproduce what was shown, exactly: that is all the drawing needs of it.
            val again = if (got == Fade.NONE) palette else fadeAll(palette, got)
            assertContentEquals(shown, again, "coeff $coeff colour %04x read as $got".format(color))
        }
    }

    @Test
    fun `the usual fades are read back as themselves when the palette pins them down`() {
        // The game fades to black or white. A palette with a colour whose channels are all 1 and one whose channels are all
        // 31 leaves no other colour that fits: at 1 a fade to black gives 0 and any other blend gives at least 1, and at 31
        // a fade to white gives 31 and any other blend gives less. (Without them a fade is only known as far as the palette
        // shows it, which is as far as it matters: the property test above.)
        val pinned = palette.copyOf().also { it[0] = rgb(1, 1, 1); it[1] = rgb(31, 31, 31) }
        for (coeff in 1..16) for (color in listOf(0, 0x7FFF)) {
            val shown = fadeAll(pinned, Fade(coeff, color))
            assertEquals(Fade(coeff, color), M.estimateFade(pinned, shown), "coefficient $coeff towards %04x".format(color))
        }
    }

    @Test
    fun `identical palettes are no fade, and a palette that changed some other way is not read as one`() {
        assertEquals(Fade.NONE, M.estimateFade(palette, palette.copyOf()))
        assertEquals(Fade.NONE, M.estimateFade(IntArray(0), IntArray(0)))
        assertEquals(Fade(0, 0), Fade.NONE)
        // Every colour replaced by another one entirely: no coefficient and colour explain it.
        val scrambled = IntArray(palette.size) { palette[(it + 7) % palette.size] }
        assertNull(M.estimateFade(palette, scrambled))
        // One channel reversed is not a fade either.
        val flipped = IntArray(palette.size) { rgb(palette[it] and 31, ((palette[it] shr 5) and 31), 31 - ((palette[it] shr 10) and 31)) }
        assertNull(M.estimateFade(palette, flipped))
    }

    // ------------------------------------------------------------------ the player's box

    private fun box(x: Int = 120, y: Int = 72, x2: Int = 0, y2: Int = 0, vecY: Int = -16, on: Boolean = false, offX: Int = 0, offY: Int = 0) =
        M.box(x, y, x2, y2, vecY, on, offX, offY).toList()

    @Test
    fun `a standing player's box is (104, 56), from a 16x32 sprite at (112, 56), and it rests on the sprite's feet`() {
        // centre x = 120, the sprite's top is y - 16 = 56 and its feet 32 lower, at 88; the 32x32 box: 120 - 16, 88 - 32.
        assertEquals(listOf(104, 56), box())
        assertEquals(88 - 32, box()[1])
    }

    @Test
    fun `a step moves the box by the sprite's own x2 and y2`() {
        assertEquals(listOf(96, 56), box(x2 = -8), "half a tile into a step east: 120 - 8 - 16")
        assertEquals(listOf(104 + 5, 56), box(x2 = 5))
        assertEquals(listOf(104, 62), box(y2 = 6), "a step south, six pixels in: feet 72 + 6 + 16 = 94, 94 - 32")
        assertEquals(listOf(104, 50), box(y2 = -6))
    }

    @Test
    fun `a ledge jump lifts the box with the sprite`() {
        // y2 = -10 in the air: feet = 72 - 10 + 16 = 78, box top 46. The shadow the game draws stays on the ground; the box does not.
        assertEquals(listOf(104, 46), box(y2 = -10))
        assertEquals(listOf(104, 56 - 12), box(y2 = -12))
    }

    @Test
    fun `surf bob moves the box a pixel at a time`() {
        assertEquals(listOf(104, 55), box(y2 = -1))
        assertEquals(listOf(104, 57), box(y2 = 1))
    }

    @Test
    fun `a 32x32 bike sprite has the same box, and a taller object rests on its own feet`() {
        // The bike is 32x32 with centerToCornerVecY -16, like the 16x32 player: same centre, same feet.
        assertEquals(listOf(104, 56), box(vecY = -16))
        // A 32x64 sprite has vecY -32: feet = 72 + 32 = 104, top = 72.
        assertEquals(listOf(104, 72), box(vecY = -32))
        // A 16x16 sprite has vecY -8: feet = 72 + 8 = 80, top = 48.
        assertEquals(listOf(104, 48), box(vecY = -8))
    }

    @Test
    fun `the camera offset counts only when the sprite has coordOffsetEnabled`() {
        // On: x = 120 - 8 = 112, feet = 72 + 4 + 16 = 92.  Off: the offsets are not looked at.
        assertEquals(listOf(96, 60), box(on = true, offX = -8, offY = 4))
        assertEquals(listOf(104, 56), box(on = false, offX = -8, offY = 4))
        assertEquals(listOf(104, 56), box(on = true, offX = 0, offY = 0))
        assertNotEquals(box(on = true, offX = 3, offY = 3), box(on = false, offX = 3, offY = 3))
    }

    @Test
    fun `a box partly off the screen keeps its true position, and the drawing clips`() {
        assertEquals(listOf(-8, 56), box(x = 8), "centre 8: the left quarter of the box is off the screen")
        assertEquals(listOf(216, 56), box(x = 232), "the right quarter is off, and the box is not pulled back")
        assertEquals(listOf(104, -8), box(y = 8), "feet at 8 + 16 = 24, top at -8")
        assertEquals(listOf(104, 144), box(y = 160), "feet at 176, top at 144, and the box goes 16 below the screen")
    }

    // ------------------------------------------------------------------ hiding the trainer

    private fun oamOf(vararg entries: Triple<Int, Int, Int>): ByteArray {
        val oam = ByteArray(M.OAM_ENTRIES * 8)
        for (i in 0 until M.OAM_ENTRIES) put(oam, i, 0x00A0, 0, 0)   // the game's dummy entry, y = 160
        entries.forEachIndexed { i, (a0, a1, a2) -> put(oam, i, a0, a1, a2) }
        return oam
    }

    private fun put(oam: ByteArray, i: Int, a0: Int, a1: Int, a2: Int) {
        for ((k, v) in listOf(a0, a1, a2).withIndex()) { oam[i * 8 + k * 2] = v.toByte(); oam[i * 8 + k * 2 + 1] = (v shr 8).toByte() }
    }

    private fun attr0(oam: ByteArray, i: Int) = (oam[i * 8].toInt() and 255) or ((oam[i * 8 + 1].toInt() and 255) shl 8)

    @Test
    fun `only the trainer's tiles are hidden, the reflection included, and every other byte is left as it was`() {
        val oam = oamOf(
            Triple(0x8010, 0x4078, 0x0020),   // 0: the body, tile 0x20: the first of the range
            Triple(0x8310, 0x4078, 0x1022),   // 1: the reflection, an affine entry (mode 0b11), palette 1, tile 0x22
            Triple(0x8010, 0x4050, 0x0028),   // 2: a passer-by, tile 0x28: one past the range
            Triple(0x8010, 0x4050, 0x001F),   // 3: a passer-by, tile 0x1F: one before it
            Triple(0x8010, 0x4078, 0x0027),   // 4: tile 0x27, the last of the range
            Triple(0x8210, 0x4078, 0x0021),   // 5: already switched off, in range: left alone and not counted
            Triple(0x8010, 0x4078, 0x0C24),   // 6: priority 3, tile 0x24: hidden, and its attr2 is not touched
            Triple(0x0138, 0x0060, 0x0020 or 0x2000),   // 7: an affine entry (mode 0b01) with palette 2, tile 0x20
        )
        val before = oam.copyOf()
        val n = M.hideTileRange(oam, 0x20, 8)
        assertEquals(5, n, "entries 0, 1, 4, 6 and 7")
        // Bits 8 and 9 of attr0 are 0b10 (OBJ disabled) on the hidden ones, with the rest of attr0 as it was.
        assertEquals(0x8210, attr0(oam, 0)); assertEquals(0x8210, attr0(oam, 1)); assertEquals(0x8210, attr0(oam, 4))
        assertEquals(0x8210, attr0(oam, 6)); assertEquals(0x0238, attr0(oam, 7), "0x0138 with bit 8 cleared and bit 9 set")
        // Every other byte of the buffer: identical.
        for (b in before.indices) {
            val entry = b / 8; val inAttr0 = b % 8 < 2
            if (inAttr0 && entry in listOf(0, 1, 4, 6, 7)) continue
            assertEquals(before[b], oam[b], "byte $b (entry $entry) changed")
        }
        // The passers-by are exactly as they were.
        assertEquals(0x8010, attr0(oam, 2)); assertEquals(0x8010, attr0(oam, 3)); assertEquals(0x8210, attr0(oam, 5))
    }

    @Test
    fun `the game's dummy entries are never touched, even when the range includes tile 0`() {
        val oam = oamOf(Triple(0x8010, 0x4078, 0x0003))
        val before = oam.copyOf()
        assertEquals(1, M.hideTileRange(oam, 0, 8))
        assertEquals(0x8210, attr0(oam, 0))
        for (i in 1 until M.OAM_ENTRIES) assertEquals(0x00A0, attr0(oam, i), "dummy $i")
        for (b in 8 until before.size) assertEquals(before[b], oam[b], "byte $b")
        // The same buffer with nothing in range changes nothing and says so.
        val quiet = oamOf(Triple(0x8010, 0x4078, 0x0100))
        val was = quiet.copyOf()
        assertEquals(0, M.hideTileRange(quiet, 0x20, 8))
        assertContentEquals(was, quiet)
    }

    @Test
    fun `the tile number is ten bits, so palette and priority bits never put an entry in or out of the range`() {
        // attr2 = 0xF420: palette 15, priority 1, tile 0x20. It IS the trainer's.
        val oam = oamOf(Triple(0x8010, 0x4078, 0xF420), Triple(0x8010, 0x4078, 0xFC28))
        assertEquals(1, M.hideTileRange(oam, 0x20, 8))
        assertEquals(0x8210, attr0(oam, 0)); assertEquals(0x8010, attr0(oam, 1))
        // At the top of the tile space (0x3FF) an entry is still found.
        val top = oamOf(Triple(0x8010, 0x4078, 0x03FF))
        assertEquals(1, M.hideTileRange(top, 0x3F8, 8))
    }

    // ------------------------------------------------------------------ pixel formats

    @Test
    fun `each pixel format's white, red, green, blue and grey`() {
        // 8888: a 5-bit channel v becomes (v << 3) | (v >> 2): 31 -> 255, 16 -> 132 (0x84).
        assertEquals(0xFFFFFF, M.packPixel(M.FORMAT_XRGB8888, 31, 31, 31))
        assertEquals(0xFF0000, M.packPixel(M.FORMAT_XRGB8888, 31, 0, 0))
        assertEquals(0x00FF00, M.packPixel(M.FORMAT_XRGB8888, 0, 31, 0))
        assertEquals(0x0000FF, M.packPixel(M.FORMAT_XRGB8888, 0, 0, 31))
        assertEquals(0x848484, M.packPixel(M.FORMAT_XRGB8888, 16, 16, 16))
        // 1555: five bits each, red at bit 10.
        assertEquals(0x7FFF, M.packPixel(M.FORMAT_0RGB1555, 31, 31, 31))
        assertEquals(0x7C00, M.packPixel(M.FORMAT_0RGB1555, 31, 0, 0))
        assertEquals(0x03E0, M.packPixel(M.FORMAT_0RGB1555, 0, 31, 0))
        assertEquals(0x001F, M.packPixel(M.FORMAT_0RGB1555, 0, 0, 31))
        assertEquals(0x4210, M.packPixel(M.FORMAT_0RGB1555, 16, 16, 16))
        // 565: green has six bits, the top bit copied into the extra bottom one: 31 -> 63, 16 -> (32 | 1) = 33.
        assertEquals(0xFFFF, M.packPixel(M.FORMAT_RGB565, 31, 31, 31))
        assertEquals(0xF800, M.packPixel(M.FORMAT_RGB565, 31, 0, 0))
        assertEquals(0x07E0, M.packPixel(M.FORMAT_RGB565, 0, 31, 0))
        assertEquals(0x001F, M.packPixel(M.FORMAT_RGB565, 0, 0, 31))
        assertEquals(0x8430, M.packPixel(M.FORMAT_RGB565, 16, 16, 16), "0x8000 | (33 << 5 = 0x420) | 0x10")
        assertEquals(listOf(2, 2, 4), listOf(M.FORMAT_0RGB1555, M.FORMAT_RGB565, M.FORMAT_XRGB8888).map { M.bytesPerPixel(it) })
    }

    // ------------------------------------------------------------------ drawing

    private class Frame(val format: Int, val w: Int, val h: Int, val pad: Int = 0, fill: Int = 0) {
        val bpp = OverworldMath.bytesPerPixel(format)
        val pitch = w * bpp + pad
        val bytes = ByteArray(pitch * h) { fill.toByte() }
        fun at(x: Int, y: Int): Int {
            val o = y * pitch + x * bpp
            var v = (bytes[o].toInt() and 255) or ((bytes[o + 1].toInt() and 255) shl 8)
            if (bpp == 4) v = v or ((bytes[o + 2].toInt() and 255) shl 16) or ((bytes[o + 3].toInt() and 255) shl 24)
            return v
        }
        fun set(x: Int, y: Int, v: Int) {
            val o = y * pitch + x * bpp
            bytes[o] = v.toByte(); bytes[o + 1] = (v shr 8).toByte()
            if (bpp == 4) { bytes[o + 2] = (v shr 16).toByte(); bytes[o + 3] = (v shr 24).toByte() }
        }
    }

    private fun draw(f: Frame, sprite: IntArray, sw: Int, sh: Int, dx: Int, dy: Int, fade: Fade = Fade.NONE) =
        M.draw(f.bytes, f.w, f.h, f.pitch, f.format, sprite, sw, sh, dx, dy, fade)

    private val formats = listOf(M.FORMAT_0RGB1555, M.FORMAT_RGB565, M.FORMAT_XRGB8888)

    @Test
    fun `an opaque pixel is packed in the frame's format, and a clear one leaves the frame as it was`() {
        for (fmt in formats) {
            val f = Frame(fmt, 3, 1, fill = 0x11)
            val was = f.bytes.copyOf()
            // red opaque, then a transparent pixel with junk colour, then blue opaque
            draw(f, intArrayOf(0xFFFF0000.toInt(), 0x00123456, 0xFF0000FF.toInt()), 3, 1, 0, 0)
            assertEquals(M.packPixel(fmt, 31, 0, 0), f.at(0, 0), "format $fmt red")
            assertEquals(M.packPixel(fmt, 0, 0, 31), f.at(2, 0), "format $fmt blue")
            val o = 1 * f.bpp
            for (k in 0 until f.bpp) assertEquals(was[o + k], f.bytes[o + k], "format $fmt: a clear pixel wrote byte $k")
        }
        // 8888 leaves the top byte (unused) at 0 for a pixel it writes.
        val f = Frame(M.FORMAT_XRGB8888, 1, 1, fill = 0x7F)
        draw(f, intArrayOf(0xFFFFFFFF.toInt()), 1, 1, 0, 0)
        assertEquals(0x00FFFFFF, f.at(0, 0))
    }

    @Test
    fun `a half clear pixel is mixed with what is behind it, in every format`() {
        // White at alpha 128 over black: (255 * 128 + 0 * 127 + 127) / 255 = 32767 / 255 = 128 in each channel.
        val over = mapOf(M.FORMAT_XRGB8888 to 0x808080, M.FORMAT_0RGB1555 to 0x4210, M.FORMAT_RGB565 to 0x8410)
        for (fmt in formats) {
            val f = Frame(fmt, 1, 1)                                     // black
            draw(f, intArrayOf(0x80FFFFFF.toInt()), 1, 1, 0, 0)
            assertEquals(over.getValue(fmt), f.at(0, 0), "format $fmt: white at 128 over black")
        }
        // Black at alpha 128 over white: (0 * 128 + 255 * 127 + 127) / 255 = 32512 / 255 = 127 (127.5 truncated).
        val under = mapOf(M.FORMAT_XRGB8888 to 0x7F7F7F, M.FORMAT_0RGB1555 to 0x3DEF, M.FORMAT_RGB565 to 0x7BEF)
        for (fmt in formats) {
            val f = Frame(fmt, 1, 1)
            f.set(0, 0, M.packPixel(fmt, 31, 31, 31))
            draw(f, intArrayOf(0x80000000.toInt()), 1, 1, 0, 0)
            assertEquals(under.getValue(fmt), f.at(0, 0), "format $fmt: black at 128 over white")
        }
        // An arbitrary background, in 8888: 0x11 in each channel. (255 * 128 + 17 * 127 + 127) / 255 = 34926 / 255 = 136.
        val f = Frame(M.FORMAT_XRGB8888, 1, 1, fill = 0x11)
        draw(f, intArrayOf(0x80FFFFFF.toInt()), 1, 1, 0, 0)
        assertEquals(0x888888, f.at(0, 0))
        // Fully opaque replaces, fully clear leaves: the two ends of the same rule.
        val g = Frame(M.FORMAT_XRGB8888, 2, 1, fill = 0x11)
        draw(g, intArrayOf(0xFF102030.toInt(), 0x00102030), 2, 1, 0, 0)
        assertEquals(M.packPixel(M.FORMAT_XRGB8888, 0x10 shr 3, 0x20 shr 3, 0x30 shr 3), g.at(0, 0))
        assertEquals(0x11111111, g.at(1, 0))
    }

    @Test
    fun `drawing is clipped at every edge, respects the pitch, and never writes outside the frame`() {
        for (fmt in formats) {
            val white = IntArray(9) { 0xFFFFFFFF.toInt() }                // a 3x3 sprite
            val w = M.packPixel(fmt, 31, 31, 31)
            fun lit(f: Frame) = (0 until f.h).flatMap { y -> (0 until f.w).map { x -> x to y } }.filter { (x, y) -> f.at(x, y) == w }.toSet()

            // Over the top-left corner: only the bottom right 2x2 of the sprite is inside.
            val a = Frame(fmt, 6, 4, pad = 8, fill = 0x55)
            draw(a, white, 3, 3, -1, -1)
            assertEquals(setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), lit(a), "format $fmt top left")
            // Over the bottom-right corner: the top left 2x2.
            val b = Frame(fmt, 6, 4, pad = 8, fill = 0x55)
            draw(b, white, 3, 3, 4, 2)
            assertEquals(setOf(4 to 2, 5 to 2, 4 to 3, 5 to 3), lit(b), "format $fmt bottom right")
            // Wholly outside, on each side: nothing written.
            for ((dx, dy) in listOf(-3 to 0, 0 to -3, 6 to 0, 0 to 4, -100 to -100, 1000 to 1000)) {
                val c = Frame(fmt, 6, 4, pad = 8, fill = 0x55)
                val was = c.bytes.copyOf()
                draw(c, white, 3, 3, dx, dy)
                assertContentEquals(was, c.bytes, "format $fmt at ($dx, $dy)")
            }
            // The padding at the end of each row (the pitch is wider than the picture) is never touched.
            val rowBytes = 6 * b.bpp
            for (y in 0 until 4) for (k in rowBytes until b.pitch) assertEquals(0x55.toByte(), b.bytes[y * b.pitch + k], "format $fmt padding row $y byte $k")
        }
    }

    @Test
    fun `a fade is applied to each colour that is drawn, channel by channel`() {
        val f = Frame(M.FORMAT_XRGB8888, 1, 1)
        // White at half towards black: 31 -> 15 in each channel, which the 8888 frame shows as (15 << 3) | (15 >> 2) = 123 = 0x7B.
        draw(f, intArrayOf(0xFFFFFFFF.toInt()), 1, 1, 0, 0, Fade(8, 0))
        assertEquals(0x7B7B7B, f.at(0, 0))
        // Full strength towards red: every colour becomes red, whatever it was.
        draw(f, intArrayOf(0xFF2040C0.toInt()), 1, 1, 0, 0, Fade(16, rgb(31, 0, 0)))
        assertEquals(0xFF0000, f.at(0, 0))
        // Black fades up to white at full strength, in a 565 frame too.
        val g = Frame(M.FORMAT_RGB565, 1, 1)
        draw(g, intArrayOf(0xFF000000.toInt()), 1, 1, 0, 0, Fade(16, 0x7FFF))
        assertEquals(0xFFFF, g.at(0, 0))
        // No fade is the identity: the colour is cut to five bits and no more.
        val h = Frame(M.FORMAT_0RGB1555, 1, 1)
        draw(h, intArrayOf(0xFF8040F8.toInt()), 1, 1, 0, 0, Fade.NONE)
        assertEquals(M.packPixel(M.FORMAT_0RGB1555, 0x80 shr 3, 0x40 shr 3, 0xF8 shr 3), h.at(0, 0))
        // The fade comes before the mix with what is behind: a half-clear white pixel, faded to black, over mid grey.
        //   faded first:  black at 128 over 0x80 is (0 * 128 + 128 * 127 + 127) / 255 = 16383 / 255 = 64  (0x40)
        //   mixed first:  white at 128 over 0x80 is 192 (0xC0), and fading that afterwards would not give 0x40.
        val k = Frame(M.FORMAT_XRGB8888, 1, 1)
        k.set(0, 0, 0x808080)
        draw(k, intArrayOf(0x80FFFFFF.toInt()), 1, 1, 0, 0, Fade(16, 0))
        assertEquals(0x404040, k.at(0, 0))
    }
}
