package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Your own sprite": how a picture is fitted into the player's 32x32 box, and how a sheet set is read.
 * All plain pixels and bytes; the emulator never comes into it.
 */
class SpriteArtTest {
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val CLEAR = 0

    private fun solid(w: Int, h: Int, c: Int) = ArtPixels(w, h, IntArray(w * h) { c })

    /** A picture with a transparent border of [m] on every side around a solid [w] x [h] block. */
    private fun padded(w: Int, h: Int, m: Int, c: Int): ArtPixels {
        val ww = w + 2 * m; val hh = h + 2 * m
        return ArtPixels(ww, hh, IntArray(ww * hh) { i -> val x = i % ww; val y = i / ww; if (x in m until m + w && y in m until m + h) c else CLEAR })
    }

    // ------------------------------------------------------------------ fitting a picture

    @Test
    fun `a picture that fits is enlarged by whole numbers, crisp, and stands on the feet line`() {
        val f = SpriteArt.fit(solid(16, 16, RED))
        assertEquals(32, f.pixels.w); assertEquals(32, f.pixels.h)
        assertEquals(0, f.ox); assertEquals(0, f.oy)
        assertTrue(f.pixels.argb.all { it == RED }, "no blur, no new colours")
        // 8 wide, 16 tall: twice as big is 16x32 (the height fills the box first), centred on the box, on its feet.
        val g = SpriteArt.fit(solid(8, 16, RED))
        assertEquals(16, g.pixels.w); assertEquals(32, g.pixels.h)
        assertEquals(8, g.ox, "centred: (32 - 16) / 2"); assertEquals(0, g.oy)
        // A small one: 10x10 is 3x = 30x30, kept crisp, lower edge on the box's lower edge.
        val h = SpriteArt.fit(solid(10, 10, RED))
        assertEquals(30, h.pixels.w); assertEquals(1, h.ox); assertEquals(2, h.oy)
        // A checkerboard stays a checkerboard when doubled.
        val check = ArtPixels(2, 2, intArrayOf(RED, BLUE, BLUE, RED))
        val c = SpriteArt.fit(check)
        assertEquals(32, c.pixels.w)
        assertEquals(RED, c.pixels[0, 0]); assertEquals(RED, c.pixels[15, 15]); assertEquals(BLUE, c.pixels[16, 0]); assertEquals(BLUE, c.pixels[0, 16])
        assertEquals(setOf(RED, BLUE), c.pixels.argb.toSet())
    }

    @Test
    fun `a picture nearly the box's size is not blurred to reach it`() {
        val f = SpriteArt.fit(solid(24, 30, RED))     // 1.06 times: a whole number of times is once
        assertEquals(24, f.pixels.w); assertEquals(30, f.pixels.h)
        assertEquals(4, f.ox); assertEquals(2, f.oy)
    }

    @Test
    fun `small pixel art too big for the box shrinks by nearest neighbour, a big picture by averaging`() {
        // 64x64 is small pixel art (up to twice the box): nearest, so a 2x2 block pattern keeps its two colours.
        val art = ArtPixels(64, 64, IntArray(64 * 64) { i -> if ((i % 64) < 32) RED else BLUE })
        val n = SpriteArt.fit(art)
        assertEquals(32, n.pixels.w)
        assertEquals(setOf(RED, BLUE), n.pixels.argb.toSet(), "only the two colours it had")
        // 256x256 is a picture: averaged, so a fine checkerboard turns to grey and not to noise or one colour.
        val fine = ArtPixels(256, 256, IntArray(256 * 256) { i -> if (((i % 256) + (i / 256)) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() })
        val a = SpriteArt.fit(fine)
        assertEquals(32, a.pixels.w); assertEquals(32, a.pixels.h)
        val mid = a.pixels[10, 10]
        val r = (mid shr 16) and 255
        assertTrue(r in 120..135, "a black and white checkerboard averages to grey: $r")
        assertEquals(255, (mid ushr 24), "opaque stays opaque")
        // Aspect kept: 300 wide, 150 tall becomes 32 x 16, standing on the feet line.
        val wide = SpriteArt.fit(solid(300, 150, RED))
        assertEquals(32, wide.pixels.w); assertEquals(16, wide.pixels.h)
        assertEquals(0, wide.ox); assertEquals(16, wide.oy)
    }

    @Test
    fun `an averaged edge keeps its colour and turns transparent, it does not darken`() {
        // A red disc block on a transparent ground: edge pixels are partly transparent RED, never dark red or black.
        val big = padded(200, 200, 0, RED)
        val half = ArtPixels(256, 256, IntArray(256 * 256) { i -> if ((i % 256) < 100) RED else CLEAR })
        val f = SpriteArt.fit(half)          // trimmed to the 100 red columns first
        assertEquals(RED, f.pixels[f.pixels.w / 2, f.pixels.h / 2])
        // Straddling pixels: half red, half clear, no trimming possible.
        val straddle = ArtPixels(65, 65, IntArray(65 * 65) { i -> if ((i % 65) <= 32) RED else CLEAR })
        val s = SpriteArt.scaleArea(straddle, 13, 13)
        val edge = s[6, 6]
        assertTrue(edge != 0 && (edge ushr 24) in 1..254, "a partly transparent edge pixel: %08X".format(edge))
        assertEquals(255, (edge shr 16) and 255, "its colour is still full red")
        assertEquals(0, (edge shr 8) and 255)
        assertEquals(RED, big[10, 10])
    }

    @Test
    fun `transparent margins are trimmed so the sprite fills the box and its feet reach the ground`() {
        val f = SpriteArt.fit(padded(16, 16, 20, RED))     // 56x56 with a 16x16 sprite in the middle
        assertEquals(32, f.pixels.w); assertEquals(32, f.pixels.h)
        assertTrue(f.pixels.argb.all { it == RED }, "the margin was cut, not scaled")
        assertEquals(0, f.oy)
        // Trimming a picture that has no border changes nothing; an empty one is nothing.
        val solidOne = solid(5, 7, RED)
        assertEquals(solidOne, SpriteArt.trim(solidOne))
        assertTrue(SpriteArt.trim(solid(5, 5, CLEAR)).isEmpty)
        val nothing = SpriteArt.fit(solid(5, 5, CLEAR))
        assertTrue(nothing.pixels.argb.all { it == 0 }, "nothing shows")
    }

    @Test
    fun `flip mirrors the picture and crop takes a piece, anything outside is clear`() {
        val p = ArtPixels(3, 2, intArrayOf(1, 2, 3, 4, 5, 6))
        assertContentEquals(intArrayOf(3, 2, 1, 6, 5, 4), SpriteArt.flipH(p).argb)
        assertEquals(p, SpriteArt.flipH(SpriteArt.flipH(p)))
        val c = SpriteArt.crop(p, 1, 0, 2, 2)
        assertContentEquals(intArrayOf(2, 3, 5, 6), c.argb)
        val out = SpriteArt.crop(p, 2, 1, 3, 2)
        assertContentEquals(intArrayOf(6, 0, 0, 0, 0, 0), out.argb)
    }

    /** A [w] x [h] frame, clear but for a block whose every pixel names its own place in the frame. */
    private fun frame(w: Int, h: Int, bx: Int, by: Int, bw: Int, bh: Int) = ArtPixels(w, h, IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        if (x in bx until bx + bw && y in by until by + bh) (0xFF shl 24) or (x shl 12) or y else CLEAR
    })

    @Test
    fun `a sheet frame bigger than the emulator side takes is cut to what shows, every pixel where it was`() {
        // One that fits is left alone, whatever clear border it has.
        val fits = frame(128, 128, 10, 10, 20, 20)
        val same = SpriteArt.placeFrame(fits, -40, -30)
        assertTrue(same.pixels === fits); assertEquals(-40, same.ox); assertEquals(-30, same.oy)
        // Mega Rayquaza's frame size with a 100 x 80 block 20 in and 30 down: the block, moved by as much.
        val big = SpriteArt.placeFrame(frame(144, 136, 20, 30, 100, 80), -55, -25)
        assertEquals(100 to 80, big.pixels.w to big.pixels.h)
        assertEquals(-55 + 20, big.ox); assertEquals(-25 + 30, big.oy)
        for (y in 0 until 80) for (x in 0 until 100) {
            val c = big.pixels[x, y]
            assertEquals(x + 20, (c shr 12) and 0xFFF, "column"); assertEquals(y + 30, c and 0xFFF, "row")
        }
        // Still too big once cut (no sheet shipped is): the middle columns and the bottom rows, where the feet are.
        val huge = SpriteArt.placeFrame(frame(200, 200, 10, 20, 150, 160), 0, 0)
        assertEquals(SpriteArt.MAX_FRAME to SpriteArt.MAX_FRAME, huge.pixels.w to huge.pixels.h)
        assertEquals(10 + 11, huge.ox, "(150 - 128) / 2 columns off the left"); assertEquals(20 + 32, huge.oy, "the top 32 rows off")
        assertEquals(10 + 11, (huge.pixels[0, 0] shr 12) and 0xFFF)
        assertEquals(20 + 160 - 1, huge.pixels[0, SpriteArt.MAX_FRAME - 1] and 0xFFF, "the bottom row is the block's last")
        // Nothing shows: one clear pixel, still something the emulator side takes.
        val clear = SpriteArt.placeFrame(ArtPixels(150, 150, IntArray(150 * 150)), 3, 4)
        assertEquals(1 to 1, clear.pixels.w to clear.pixels.h); assertEquals(0, clear.pixels[0, 0])
    }

    @Test
    fun `a single picture lifts a pixel on every other step while walking and never standing`() {
        assertEquals(0, SpriteArt.bobOffset(walking = false, step = 1))
        assertEquals(0, SpriteArt.bobOffset(walking = true, step = 0))
        assertEquals(-1, SpriteArt.bobOffset(walking = true, step = 1))
        assertEquals(0, SpriteArt.bobOffset(walking = true, step = 2))
        assertEquals(-1, SpriteArt.bobOffset(walking = true, step = 3))
    }

    // ------------------------------------------------------------------ a sheet set

    private fun png(w: Int, h: Int): ByteArray {
        val b = ByteArray(33)
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()).copyInto(b)
        for (i in 0 until 4) { b[16 + i] = (w shr (24 - 8 * i)).toByte(); b[20 + i] = (h shr (24 - 8 * i)).toByte() }
        return b
    }

    @Test
    fun `a file or folder name says which animation a sheet is`() {
        val idle = WalkingPals.Anim.IDLE; val walk = WalkingPals.Anim.WALK; val sleep = WalkingPals.Anim.SLEEP; val faint = WalkingPals.Anim.FAINT
        assertEquals(idle, SheetSet.classify("idle.png"))
        assertEquals(walk, SheetSet.classify("Walk.PNG"))
        assertEquals(sleep, SheetSet.classify("SpriteIsMeImages/sleep/Me.png"), "the folder, as the extension lays them out")
        assertEquals(faint, SheetSet.classify("images\\faint\\Me.png"))
        assertEquals(walk, SheetSet.classify("Me_walk.png"))
        assertEquals(idle, SheetSet.classify("Me-Idle-sheet.png"))
        assertEquals(walk, SheetSet.classify("walking.png"))
        assertEquals(walk, SheetSet.classify("idle/Me-walk.png"), "the file name wins over the folder")
        assertNull(SheetSet.classify("Me.png"))
        assertNull(SheetSet.classify("readme.txt"))
        assertNull(SheetSet.classify(""))
    }

    @Test
    fun `only PNGs are kept, one sheet per animation, the first of each kind`() {
        val a = png(96, 320); val b = png(200, 200)
        val kept = SheetSet.choose(listOf(
            "idle.png" to a, "walk.png" to b, "walk2.png" to png(1, 1), "notes.txt" to "hello".toByteArray(),
            "Me.png" to png(8, 8), "sleep.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(40),
        ))
        assertEquals(setOf(WalkingPals.Anim.IDLE, WalkingPals.Anim.WALK), kept.keys)
        assertContentEquals(a, kept[WalkingPals.Anim.IDLE])
        assertContentEquals(b, kept[WalkingPals.Anim.WALK], "the first walk wins")
        assertEquals(96 to 320, SheetSet.pngSize(a))
        assertNull(SheetSet.pngSize(ByteArray(40)))
        assertNull(SheetSet.pngSize(png(0, 5)), "a zero-sized image is not a sheet")
        assertNull(SheetSet.pngSize(png(9000, 5)))
    }

    @Test
    fun `frame lengths are the whole numbers typed, or nothing`() {
        assertContentEquals(intArrayOf(40, 6, 6), SheetSet.parseLengths("40,6,6"))
        assertContentEquals(intArrayOf(40, 6, 6), SheetSet.parseLengths(" 40, 6 6 "))
        assertContentEquals(intArrayOf(8), SheetSet.parseLengths("x8y"))
        assertNull(SheetSet.parseLengths(""))
        assertNull(SheetSet.parseLengths("fast"))
        assertNull(SheetSet.parseLengths("0,0"), "a frame of no length is not a length")
        assertNull(SheetSet.parseLengths("9999"))
    }

    @Test
    fun `the frame size is worked out from the sheets when it is not typed`() {
        val sizes = mapOf(
            WalkingPals.Anim.IDLE to (96 to 320),     // 3 frames of 32 by 8 rows of 40
            WalkingPals.Anim.WALK to (128 to 320),
            WalkingPals.Anim.SLEEP to (64 to 40),     // one row: the frame is 40 tall
        )
        assertEquals(40, SheetSet.frameHeight(sizes, SheetSet.Spec()))
        // No sleep or faint sheet: an idle or walk sheet's height over its eight rows.
        assertEquals(40, SheetSet.frameHeight(mapOf(WalkingPals.Anim.IDLE to (96 to 320)), SheetSet.Spec()))
        // A height that does not divide by eight is one row.
        assertEquals(50, SheetSet.frameHeight(mapOf(WalkingPals.Anim.IDLE to (150 to 50)), SheetSet.Spec()))
        // Typed sizes win.
        assertEquals(20, SheetSet.frameHeight(sizes, SheetSet.Spec(height = 20)))
        val g = SheetSet.geometry(96 to 320, SheetSet.Spec(), 40)
        assertNotNull(g)
        assertEquals(40, g.frameW, "square by default"); assertEquals(2, g.cols); assertEquals(8, g.rows)
        val typed = SheetSet.geometry(96 to 320, SheetSet.Spec(width = 32), 40)
        assertEquals(3, typed!!.cols)
        assertNull(SheetSet.geometry(10 to 10, SheetSet.Spec(), 40), "a sheet smaller than one frame")
    }

    @Test
    fun `a sheet set becomes sheets with lengths, defaults, and frames resting on the feet line`() {
        val sizes = mapOf(
            WalkingPals.Anim.IDLE to (96 to 256),
            WalkingPals.Anim.WALK to (128 to 256),
            WalkingPals.Anim.SLEEP to (64 to 32),
        )
        val sheets = assertNotNull(SheetSet.sheets(sizes, SheetSet.Spec()))
        val idle = sheets.getValue(WalkingPals.Anim.IDLE)
        assertEquals(32, idle.w); assertEquals(32, idle.h)
        assertContentEquals(intArrayOf(24, 24, 24), idle.durations, "three frames of the default idle length")
        assertContentEquals(intArrayOf(6, 6, 6, 6), sheets.getValue(WalkingPals.Anim.WALK).durations)
        assertContentEquals(intArrayOf(30, 30), sheets.getValue(WalkingPals.Anim.SLEEP).durations)
        assertEquals(0, idle.x); assertEquals(0, idle.y, "a 32x32 frame is exactly the box")
        assertFalse(WalkingPals.Anim.FAINT in sheets)
        // Typed lengths: fewer than frames repeat the last, more are cut.
        val typed = SheetSet.sheets(sizes, SheetSet.Spec(idle = "40, 6", walk = "5,5,5,5,5,5,5", sleep = ""))!!
        assertContentEquals(intArrayOf(40, 6, 6), typed.getValue(WalkingPals.Anim.IDLE).durations)
        assertContentEquals(intArrayOf(5, 5, 5, 5), typed.getValue(WalkingPals.Anim.WALK).durations)
        // A smaller frame sits centred and low; a bigger one centred and rising above the box.
        val small = SheetSet.sheets(mapOf(WalkingPals.Anim.IDLE to (48 to 128)), SheetSet.Spec(width = 16, height = 16))!!.getValue(WalkingPals.Anim.IDLE)
        assertEquals(8, small.x); assertEquals(16, small.y)
        val big = SheetSet.sheets(mapOf(WalkingPals.Anim.IDLE to (96 to 384)), SheetSet.Spec(width = 48, height = 48))!!.getValue(WalkingPals.Anim.IDLE)
        assertEquals(-8, big.x); assertEquals(-16, big.y)
        assertNull(SheetSet.sheets(emptyMap(), SheetSet.Spec()))
    }

    @Test
    fun `a set missing an animation borrows the nearest one, and a sheet with fewer than eight rows shows its first`() {
        val have = setOf(WalkingPals.Anim.IDLE)
        assertEquals(WalkingPals.Anim.IDLE, SheetSet.substitute(WalkingPals.Anim.WALK, have))
        assertEquals(WalkingPals.Anim.IDLE, SheetSet.substitute(WalkingPals.Anim.FAINT, have))
        assertEquals(WalkingPals.Anim.WALK, SheetSet.substitute(WalkingPals.Anim.IDLE, setOf(WalkingPals.Anim.WALK)))
        assertEquals(WalkingPals.Anim.SLEEP, SheetSet.substitute(WalkingPals.Anim.SLEEP, setOf(WalkingPals.Anim.SLEEP, WalkingPals.Anim.IDLE)))
        assertNull(SheetSet.substitute(WalkingPals.Anim.IDLE, setOf(WalkingPals.Anim.FAINT)))
        // The extension's rows: down 0, up 4, left 6, right 2 (1 down, 2 up, 3 left, 4 right as the game numbers them).
        assertEquals(0, SheetSet.rowFor(1, 8)); assertEquals(4, SheetSet.rowFor(2, 8)); assertEquals(6, SheetSet.rowFor(3, 8)); assertEquals(2, SheetSet.rowFor(4, 8))
        assertEquals(0, SheetSet.rowFor(4, 4), "four rows is not the extension's eight")
        assertEquals(0, SheetSet.rowFor(3, 1))
    }

    // ------------------------------------------------------------------ what the store keeps

    private fun tmp() = java.nio.file.Files.createTempDirectory("sim").toFile()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test
    fun `a zip's sheets come out by name whatever folders they sit in`() {
        val z = zip("MySprite/idle/Me.png" to png(96, 320), "MySprite/walk/Me.png" to png(128, 320),
            "MySprite/sleep/Me.png" to png(64, 40), "MySprite/__MACOSX/._x.png" to png(2, 2), "MySprite/notes.txt" to "x".toByteArray())
        val files = SpriteIsMeStore.unpack("MySprite.zip", z)
        assertTrue(files.size >= 4)
        val kept = SheetSet.choose(files)
        assertEquals(setOf(WalkingPals.Anim.IDLE, WalkingPals.Anim.WALK, WalkingPals.Anim.SLEEP), kept.keys)
        // A plain PNG is itself.
        val one = SpriteIsMeStore.unpack("walk.png", png(9, 9))
        assertEquals(listOf("walk.png"), one.map { it.first })
    }

    @Test
    fun `a zip bomb stops at the caps`() {
        val big = ByteArray(SpriteIsMeStore.MAX_FILE + 10)
        val z = zip("a.bin" to ByteArray(10), "b.bin" to big, "c.bin" to ByteArray(10))
        val files = SpriteIsMeStore.unpack("x.zip", z)
        assertEquals(listOf("a.bin"), files.map { it.first }, "the oversized entry and everything after it are dropped")
        val many = zip(*Array(SpriteIsMeStore.MAX_ENTRIES + 20) { "f$it.png" to png(2, 2) })
        assertEquals(SpriteIsMeStore.MAX_ENTRIES, SpriteIsMeStore.unpack("many.zip", many).size)
    }

    @Test
    fun `importing a picture then sheets then removing leaves nothing behind, and each replaces the last`() {
        val dir = tmp()
        SpriteIsMeSettings.load(java.io.File(dir, "prep/sprite-is-me.txt"))
        try {
            assertFalse(SpriteIsMeStore.ready(dir, SpriteIsMeSettings.Own.PICTURE))
            assertTrue(SpriteIsMeStore.savePicture(dir, png(40, 40)))
            assertEquals(SpriteIsMeSettings.Own.PICTURE, SpriteIsMeSettings.own)
            assertTrue(SpriteIsMeStore.pictureFile(dir).isFile)
            assertTrue(SpriteIsMeStore.ready(dir, SpriteIsMeSettings.Own.PICTURE))
            // Sheets replace it.
            assertEquals(2, SpriteIsMeStore.saveSheets(dir, mapOf(WalkingPals.Anim.IDLE to png(96, 320), WalkingPals.Anim.WALK to png(128, 320))))
            assertEquals(SpriteIsMeSettings.Own.SHEET, SpriteIsMeSettings.own)
            assertFalse(SpriteIsMeStore.pictureFile(dir).exists(), "the picture went")
            assertEquals(setOf(WalkingPals.Anim.IDLE, WalkingPals.Anim.WALK), SpriteIsMeStore.sheetSizes(dir).keys)
            assertEquals(96 to 320, SpriteIsMeStore.sheetSizes(dir)[WalkingPals.Anim.IDLE])
            // A new set replaces the old one whole: no stale walk sheet.
            assertEquals(1, SpriteIsMeStore.saveSheets(dir, mapOf(WalkingPals.Anim.SLEEP to png(64, 40))))
            assertEquals(setOf(WalkingPals.Anim.SLEEP), SpriteIsMeStore.sheetSizes(dir).keys)
            // And it is on disk for the settings file to name.
            assertTrue(java.io.File(dir, "prep/sprite-is-me.txt").readText().contains("own=sheet"))
            SpriteIsMeSettings.who = SpriteIsMeSettings.Who.OWN
            SpriteIsMeStore.remove(dir)
            assertFalse(SpriteIsMeStore.dir(dir).resolve("sheet").exists())
            assertEquals(SpriteIsMeSettings.Own.NONE, SpriteIsMeSettings.own)
            assertEquals(SpriteIsMeSettings.Who.LEAD, SpriteIsMeSettings.who, "nothing left to be, so back to the lead")
            assertFalse(SpriteIsMeStore.ready(dir, SpriteIsMeSettings.Own.SHEET))
        } finally { dir.deleteRecursively(); SpriteIsMeSettings.reset() }
    }

    @Test
    fun `things that are not sprites are refused and change nothing`() {
        val dir = tmp()
        SpriteIsMeSettings.load(java.io.File(dir, "prep/sprite-is-me.txt"))
        try {
            assertFalse(SpriteIsMeStore.savePicture(dir, "not a picture".toByteArray()))
            assertFalse(SpriteIsMeStore.savePicture(dir, ByteArray(SpriteIsMeStore.MAX_FILE + 1) { 1 }))
            assertEquals(0, SpriteIsMeStore.saveSheets(dir, emptyMap()))
            assertEquals(0, SpriteIsMeStore.saveSheets(dir, mapOf(WalkingPals.Anim.IDLE to ByteArray(50))))
            assertEquals(SpriteIsMeSettings.Own.NONE, SpriteIsMeSettings.own)
            assertFalse(SpriteIsMeStore.dir(dir).exists() && SpriteIsMeStore.dir(dir).listFiles().orEmpty().isNotEmpty())
        } finally { dir.deleteRecursively(); SpriteIsMeSettings.reset() }
    }
}
