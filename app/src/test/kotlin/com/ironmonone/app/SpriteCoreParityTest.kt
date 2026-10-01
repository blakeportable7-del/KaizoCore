package com.ironmonone.app

import com.ironmonone.tracker.OverworldMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin statement of the per-frame maths (tracker-gba's OverworldMath) against the C++ that
 * actually runs in the emulator (sprite_core.h, built for WebAssembly by WasmSpriteCore), over
 * thousands of seeded random inputs. The two are written twice on purpose, one to read and one to
 * run; this is what keeps them the same thing. Where a hand-checked constant matters it is in
 * SpriteCoreWasmTest; here nothing is trusted but agreement.
 */
class SpriteCoreParityTest {
    private val core: WasmSpriteCore? = WasmSpriteCore.shared()
    private fun run(body: (WasmSpriteCore) -> Unit) { core?.let(body) }

    private fun le16(vararg v: Int): ByteArray = ByteArray(v.size * 2).also { b -> v.forEachIndexed { i, w -> b[i * 2] = w.toByte(); b[i * 2 + 1] = (w shr 8).toByte() } }

    @Test
    fun `the arithmetic shift, the blend and the tile table agree`() = run { c ->
        for (v in -700..700) assertEquals(OverworldMath.floorDiv16(v), c.call("h_floorDiv16", v).toInt(), "floorDiv16($v)")
        val rnd = Random(20260929)
        repeat(3000) {
            val col = rnd.nextInt(0x8000); val coeff = rnd.nextInt(0, 17); val blend = rnd.nextInt(0x8000)
            assertEquals(OverworldMath.gameBlend(col, coeff, blend), c.call("h_blend", col, coeff, blend).toInt(), "blend %04X %d %04X".format(col, coeff, blend))
        }
        for (shape in 0..3) for (size in 0..3) assertEquals(OverworldMath.tileCount(shape, size).toLong(), c.call("h_tiles", shape, size))
        // And the game's own numbers: BlendPalette to black at 16 is black, at 0 is the colour, halfway is the floor.
        assertEquals(0, OverworldMath.gameBlend(0x7FFF, 16, 0))
        assertEquals(0x7FFF, OverworldMath.gameBlend(0x7FFF, 0, 0))
        assertEquals(0x7FFF, OverworldMath.gameBlend(0x7FFF, 16, 0x7FFF))
        assertEquals(15, OverworldMath.gameBlend(31, 8, 0) and 31, "31 - ceil(31 * 8 / 16) = 15")
    }

    @Test
    fun `the box maths agree`() = run { c ->
        val rnd = Random(7)
        repeat(2000) {
            val x = rnd.nextInt(-300, 500); val y = rnd.nextInt(-300, 500); val x2 = rnd.nextInt(-40, 40); val y2 = rnd.nextInt(-40, 40)
            val vecY = -rnd.nextInt(1, 64); val en = rnd.nextBoolean(); val ox = rnd.nextInt(-400, 400); val oy = rnd.nextInt(-400, 400)
            c.call("h_box", x, y, x2, y2, vecY, if (en) 1 else 0, ox, oy, "@5:0")
            val out = c.read(WasmSpriteCore.KIND_SCRATCH, 0, 8)
            val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            val mirror = OverworldMath.box(x, y, x2, y2, vecY, en, ox, oy)
            assertEquals(mirror[0], bb.getInt(0)); assertEquals(mirror[1], bb.getInt(4))
        }
    }

    @Test
    fun `the fade fit agrees, and recovers what the game applied`() = run { c ->
        val rnd = Random(99)
        var fitted = 0
        fun estimate(u: IntArray, s: IntArray): Triple<Boolean, Int, Int> {
            c.write(WasmSpriteCore.KIND_SCRATCH, 0, le16(*u)); c.write(WasmSpriteCore.KIND_SCRATCH, 64, le16(*s))
            val ok = c.call("h_estimate", "@5:0", "@5:64", u.size, "@5:128", "@5:132") == 1L
            val b = ByteBuffer.wrap(c.read(WasmSpriteCore.KIND_SCRATCH, 128, 8)).order(ByteOrder.LITTLE_ENDIAN)
            return Triple(ok, b.getInt(0), b.getInt(4))
        }
        repeat(150) { round ->
            val pal = IntArray(15) { rnd.nextInt(0x8000) }
            val coeff = rnd.nextInt(1, 17)
            val blend = when (round % 4) { 0 -> 0; 1 -> 0x7FFF; else -> rnd.nextInt(0x8000) }
            val shown = IntArray(15) { OverworldMath.gameBlend(pal[it], coeff, blend) }
            val (ok, gotCoeff, gotColor) = estimate(pal, shown)
            val mirror = OverworldMath.estimateFade(pal, shown)
            assertNotNull(mirror); assertTrue(ok)
            assertEquals(mirror.coeff, gotCoeff, "coeff, round $round"); assertEquals(mirror.color, gotColor, "colour, round $round")
            // A fit reproduces the shown palette exactly, which is all a fit has to do.
            for (i in 0 until 15) assertEquals(shown[i], OverworldMath.gameBlend(pal[i], mirror.coeff, mirror.color), "entry $i, round $round")
            fitted++
            // The same palette with noise: both say the same thing, fit or no fit.
            val noisy = IntArray(15) { (shown[it] xor rnd.nextInt(0x8000)) and 0x7FFF }
            val (ok2, c2, col2) = estimate(pal, noisy)
            val m2 = OverworldMath.estimateFade(pal, noisy)
            assertEquals(m2 != null, ok2, "noise, round $round")
            if (m2 != null) { assertEquals(m2.coeff, c2); assertEquals(m2.color, col2) }
        }
        assertEquals(150, fitted)
        // Identical palettes are no fade, at once.
        val same = IntArray(15) { it * 991 and 0x7FFF }
        val (ok, cc, col) = estimate(same, same)
        assertTrue(ok); assertEquals(0, cc); assertEquals(0, col)
        assertEquals(OverworldMath.Fade.NONE, OverworldMath.estimateFade(same, same))
        // A palette with nothing in common with the original is not a fade.
        assertNull(OverworldMath.estimateFade(IntArray(15) { 0x7FFF }, IntArray(15) { it * 2113 and 0x7FFF }))
    }

    @Test
    fun `the OAM hide agrees on random buffers`() = run { c ->
        val rnd = Random(4242)
        repeat(300) { round ->
            val oam = ByteArray(1024)
            for (i in 0 until 128) {
                val e = ByteBuffer.wrap(oam, i * 8, 8).order(ByteOrder.LITTLE_ENDIAN)
                when (rnd.nextInt(6)) {
                    0 -> { e.putShort(0, 0x00A0.toShort()); e.putShort(2, 0); e.putShort(4, 0); e.putShort(6, 0) }   // the dummy
                    1 -> { e.putShort(0, ((rnd.nextInt(256)) or (2 shl 8)).toShort()); e.putShort(2, rnd.nextInt(65536).toShort()); e.putShort(4, rnd.nextInt(65536).toShort()) }   // disabled
                    else -> { e.putShort(0, rnd.nextInt(65536).toShort()); e.putShort(2, rnd.nextInt(65536).toShort()); e.putShort(4, rnd.nextInt(65536).toShort()); e.putShort(6, rnd.nextInt(65536).toShort()) }
                }
            }
            val base = rnd.nextInt(0, 1000); val count = listOf(1, 2, 4, 8, 16, 32, 64)[rnd.nextInt(7)]
            val theirs = oam.copyOf()
            val n = OverworldMath.hideTileRange(theirs, base, count)
            c.write(WasmSpriteCore.KIND_SCRATCH, 0, oam)
            val nn = c.call("h_hide", "@5:0", base, count).toInt()
            assertEquals(n, nn, "changed, round $round")
            assertContentEquals(theirs, c.read(WasmSpriteCore.KIND_SCRATCH, 0, 1024), "bytes, round $round")
        }
    }

    @Test
    fun `the compositor agrees byte for byte in every format`() = run { c ->
        val rnd = Random(1234567)
        repeat(120) { round ->
            val format = round % 3          // 0 = 0RGB1555, 1 = XRGB8888, 2 = RGB565
            val bpp = if (format == 1) 4 else 2
            val pitch = 240 * bpp + rnd.nextInt(0, 3) * 8
            val sw = rnd.nextInt(1, 70); val sh = rnd.nextInt(1, 70)
            val sprite = IntArray(sw * sh) {
                val a = when (rnd.nextInt(5)) { 0 -> 0; 1 -> rnd.nextInt(1, 255); else -> 255 }
                (a shl 24) or (rnd.nextInt(0x1000000))
            }
            val dx = rnd.nextInt(-90, 300); val dy = rnd.nextInt(-90, 220)
            val fade = when (round % 4) { 0 -> OverworldMath.Fade.NONE; 1 -> OverworldMath.Fade(rnd.nextInt(1, 17), 0); 2 -> OverworldMath.Fade(rnd.nextInt(1, 17), 0x7FFF); else -> OverworldMath.Fade(rnd.nextInt(1, 17), rnd.nextInt(0x8000)) }
            val frame = ByteArray(pitch * 160).also { rnd.nextBytes(it) }
            // A frame in a real format has clean top bits: 0RGB1555's high bit and XRGB8888's high byte are zero.
            if (format == 0) for (i in 0 until frame.size / 2) frame[i * 2 + 1] = (frame[i * 2 + 1].toInt() and 0x7F).toByte()
            if (format == 1) for (i in 0 until frame.size / 4) frame[i * 4 + 3] = 0
            val mine = frame.copyOf()
            OverworldMath.draw(mine, 240, 160, pitch, format, sprite, sw, sh, dx, dy, fade)

            val rgba = ByteArray(sw * sh * 4)
            for (i in sprite.indices) { rgba[i * 4] = (sprite[i] shr 16).toByte(); rgba[i * 4 + 1] = (sprite[i] shr 8).toByte(); rgba[i * 4 + 2] = sprite[i].toByte(); rgba[i * 4 + 3] = (sprite[i] ushr 24).toByte() }
            c.write(WasmSpriteCore.KIND_SPRITE, 0, rgba)
            c.call("h_sprite", sw, sh, 0, 0)
            c.write(WasmSpriteCore.KIND_FRAME, 0, frame)
            c.call("h_draw", format, 240, 160, pitch, dx, dy, fade.coeff, fade.color)
            val theirs = c.read(WasmSpriteCore.KIND_FRAME, 0, frame.size)
            assertContentEquals(mine, theirs, "format $format round $round sprite ${sw}x$sh at ($dx,$dy) fade ${fade.coeff}/%04X".format(fade.color))
        }
    }

    @Test
    fun `packing a pixel agrees`() = run { c ->
        for (fmt in 0..2) for (r in 0..31 step 3) for (g in 0..31 step 3) for (b in 0..31 step 5)
            assertEquals(OverworldMath.packPixel(fmt, r, g, b), c.call("h_pack", fmt, r, g, b).toInt(), "fmt $fmt $r $g $b")
    }
}
