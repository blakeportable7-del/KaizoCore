package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ironmonone.app.TrackerBackground as Bg

/**
 * The image behind the tracker (2026-09-29): its settings, the arithmetic that shrinks, turns and places it, the
 * plan a draw follows (colour, then picture, then black over the picture), the backup, and the wiring the JVM
 * cannot run: the photo picker, the backdrop in every host, and PlayScreen.kt left alone.
 */
class TrackerBackgroundTest {
    private val dir: File = Files.createTempDirectory("trackerbg").toFile()
    private val decoderBefore = Bg.decoder
    private val runAsyncBefore = Bg.runAsync

    @AfterTest fun cleanup() {
        Bg.decoder = decoderBefore; Bg.runAsync = runAsyncBefore
        Bg.detach()
        dir.deleteRecursively()
    }

    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    private class FakeBitmap(override val width: Int, override val height: Int) : ImageBitmap {
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override val hasAlpha: Boolean get() = false
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) {}
        override fun prepareToDraw() {}
    }

    // ---- Settings ------------------------------------------------------------------------------------------

    @Test
    fun `the settings default to a dim of 40 and Fill, and a missing file leaves them there`() {
        assertEquals(40, Bg.DEFAULT_DIM)
        Bg.load(dir)
        assertEquals(40, Bg.dim); assertEquals(Bg.Fit.FILL, Bg.fit); assertNull(Bg.image)
        assertEquals("dim=40\nfit=fill\nsee=35\n", Bg.settingsText())
    }

    @Test
    fun `the settings round trip through the file`() {
        val settings = File(dir, Bg.SETTINGS_FILE)
        Bg.load(dir)
        Bg.changeDim(65); Bg.changeFit(Bg.Fit.FIT); Bg.save()
        DiskWriter.drain()   // written on the writer's thread (rc32 audit P3 #69)
        assertEquals("dim=65\nfit=fit\nsee=35\n", settings.readText())
        Bg.applySettings("")
        assertEquals(40, Bg.dim)
        Bg.load(dir)
        assertEquals(65, Bg.dim); assertEquals(Bg.Fit.FIT, Bg.fit)
    }

    @Test
    fun `a dim outside 0 to 90 is clamped, and what cannot be read is the default`() {
        Bg.applySettings("dim=250\nfit=fit"); assertEquals(90, Bg.dim); assertEquals(Bg.Fit.FIT, Bg.fit)
        Bg.applySettings("dim=-5"); assertEquals(0, Bg.dim); assertEquals(Bg.Fit.FILL, Bg.fit, "no fit line is Fill")
        Bg.applySettings("dim=abc\nfit=stretch"); assertEquals(40, Bg.dim); assertEquals(Bg.Fit.FILL, Bg.fit)
        Bg.applySettings("dim = 55 \n fit = Fit \njunk\n=5"); assertEquals(55, Bg.dim); assertEquals(Bg.Fit.FIT, Bg.fit)
        Bg.applySettings(""); assertEquals(40, Bg.dim)
        assertEquals(90, Bg.clampDim(1000)); assertEquals(0, Bg.clampDim(-1)); assertEquals(37, Bg.clampDim(37))
        Bg.changeDim(1000); assertEquals(90, Bg.dim)
    }

    @Test
    fun `a settings file that cannot be read gives the defaults, not a crash`() {
        val settings = File(dir, Bg.SETTINGS_FILE)
        settings.parentFile!!.mkdirs(); settings.mkdirs()   // a directory where the file should be
        Bg.load(dir)
        assertEquals(40, Bg.dim); assertEquals(Bg.Fit.FILL, Bg.fit)
    }

    // ---- The image is decoded once, and never in the way -------------------------------------------------------

    @Test
    fun `the image is decoded once at load and remembered`() {
        val jpg = File(dir, Bg.IMAGE_FILE).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        var decodes = 0
        val fake = FakeBitmap(1280, 720)
        Bg.decoder = { f -> assertEquals(jpg, f); decodes++; fake }
        Bg.runAsync = { it() }
        Bg.load(dir)
        assertEquals(1, decodes)
        assertEquals(fake, Bg.image)
        // Reading it, as every draw does, decodes nothing more.
        repeat(50) { assertNotNull(Bg.image) }
        assertEquals(1, decodes)
    }

    @Test
    fun `the decode is handed to another thread, not run on the caller's`() {
        File(dir, Bg.IMAGE_FILE).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        var queued: (() -> Unit)? = null
        var decodes = 0
        Bg.runAsync = { queued = it }
        Bg.decoder = { decodes++; FakeBitmap(10, 10) }
        Bg.load(dir)
        assertEquals(0, decodes, "load itself does not decode")
        assertNull(Bg.image)
        queued!!.invoke()
        assertEquals(1, decodes)
        assertNotNull(Bg.image)
    }

    @Test
    fun `a missing or unreadable image leaves the colour`() {
        Bg.runAsync = { it() }
        var decodes = 0
        Bg.decoder = { decodes++; null }
        Bg.load(dir)                                       // no file: nothing to decode
        assertEquals(0, decodes); assertNull(Bg.image)
        File(dir, Bg.IMAGE_FILE).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(9, 9)) }
        Bg.load(dir)                                       // a file the decoder gives up on
        assertEquals(1, decodes); assertNull(Bg.image)
        Bg.decoder = { throw IllegalStateException("corrupt") }
        Bg.load(dir)                                       // a decoder that throws
        assertNull(Bg.image)
        assertEquals(listOf<Bg.Op>(Bg.Op.Colour(Color.Red)), Bg.plan(Color.Red, 0, 0, 100, 100, Bg.Fit.FILL, 40),
            "with no image a draw is the colour as before")
    }

    @Test
    fun `removing the image deletes the copy and the colour shows again`() {
        val jpg = File(dir, Bg.IMAGE_FILE).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        Bg.runAsync = { it() }; Bg.decoder = { FakeBitmap(10, 10) }
        Bg.load(dir)
        assertNotNull(Bg.image)
        Bg.clear()
        assertNull(Bg.image); assertFalse(jpg.exists())
        Bg.load(dir)
        assertNull(Bg.image)
    }

    // ---- The arithmetic --------------------------------------------------------------------------------------

    @Test
    fun `the long side is at most 1280 and the aspect is kept`() {
        assertEquals(1280, Bg.MAX_LONG_SIDE); assertEquals(85, Bg.JPEG_QUALITY)
        assertEquals(1280 to 960, Bg.scaledSize(4000, 3000))
        assertEquals(960 to 1280, Bg.scaledSize(3000, 4000))
        assertEquals(1280 to 720, Bg.scaledSize(1920, 1080))
        assertEquals(1280 to 1280, Bg.scaledSize(5000, 5000))
        assertEquals(1280 to 1, Bg.scaledSize(9000, 1), "a sliver keeps a pixel")
        // Not enlarged, and exactly 1280 is left alone.
        assertEquals(1000 to 500, Bg.scaledSize(1000, 500))
        assertEquals(1280 to 720, Bg.scaledSize(1280, 720))
        assertEquals(640 to 1280, Bg.scaledSize(640, 1280))
        for ((w, h) in listOf(4032 to 3024, 3024 to 4032, 2000 to 1999, 12000 to 800, 1281 to 1000)) {
            val (sw, sh) = Bg.scaledSize(w, h)
            assertEquals(1280, maxOf(sw, sh), "$w x $h")
            assertTrue(kotlin.math.abs(sw.toDouble() / sh - w.toDouble() / h) / (w.toDouble() / h) < 0.01, "$w x $h keeps its aspect: $sw x $sh")
        }
    }

    @Test
    fun `a huge image is decoded at a power of two that still has 1280 on its long side`() {
        assertEquals(1, Bg.sampleSize(1000, 800)); assertEquals(1, Bg.sampleSize(1280, 720)); assertEquals(1, Bg.sampleSize(2559, 100))
        assertEquals(2, Bg.sampleSize(2560, 1440)); assertEquals(2, Bg.sampleSize(4000, 3000))
        assertEquals(4, Bg.sampleSize(5120, 100)); assertEquals(8, Bg.sampleSize(12000, 9000))
        for (long in listOf(1280, 1500, 2559, 2560, 4032, 6000, 10000, 20000)) {
            val s = Bg.sampleSize(long, 10)
            assertTrue(s and (s - 1) == 0, "$long: a power of two")
            assertTrue(long / s >= minOf(long, 1280), "$long / $s still reaches 1280")
            assertTrue(long / (s * 2) < 1280 || long < 2560, "$long: no larger a sample would do")
        }
    }

    /** An image turned upright by an orientation, done on a grid of cells: mirror left to right first, then turn clockwise. */
    private fun <T> upright(grid: List<List<T>>, o: Bg.Orientation): List<List<T>> {
        var g = if (o.flip) grid.map { it.reversed() } else grid
        repeat(o.degrees / 90) { g = (0 until g[0].size).map { col -> g.indices.reversed().map { row -> g[row][col] } } }
        return g
    }

    @Test
    fun `each EXIF orientation turns the picture upright as the standard describes`() {
        val stored = listOf(listOf('a', 'b', 'c'), listOf('d', 'e', 'f'))
        // EXIF tag values 1 to 8 as the standard pictures them, for a stored 3 wide, 2 high image.
        val shown = mapOf(
            1 to listOf("abc", "def"),
            2 to listOf("cba", "fed"),      // mirrored left to right
            3 to listOf("fed", "cba"),      // turned half way
            4 to listOf("def", "abc"),      // mirrored top to bottom
            5 to listOf("ad", "be", "cf"),  // transposed
            6 to listOf("da", "eb", "fc"),  // turned a quarter clockwise
            7 to listOf("fc", "eb", "da"),  // transversed
            8 to listOf("cf", "be", "ad"),  // turned three quarters clockwise
        )
        for ((tag, rows) in shown) {
            val o = Bg.orientationOf(tag)
            assertEquals(rows, upright(stored, o).map { it.joinToString("") }, "tag $tag")
            assertEquals(rows.size == 3, o.swapsAxes, "tag $tag swaps the axes when it lies on its side")
        }
        for (tag in listOf(0, 9, -1, 100)) {
            val o = Bg.orientationOf(tag)
            assertEquals(0, o.degrees); assertFalse(o.flip); assertFalse(o.swapsAxes)
        }
    }

    @Test
    fun `Fill covers the box and keeps the middle, Fit shows all of the image centred`() {
        // A wide photo in the tracker's tall, narrow box.
        val fill = Bg.place(1280, 720, 150, 400, Bg.Fit.FILL)!!
        assertEquals(Bg.Placement(505, 0, 270, 720, 0, 0, 150, 400), fill)
        val fit = Bg.place(1280, 720, 150, 400, Bg.Fit.FIT)!!
        assertEquals(Bg.Placement(0, 0, 1280, 720, 0, 158, 150, 84), fit)
        // Fill's slice has the box's aspect and is centred; Fit's picture has the image's aspect and is centred.
        for ((iw, ih, bw, bh) in listOf(listOf(1280, 720, 300, 300), listOf(720, 1280, 1080, 600), listOf(1000, 1000, 333, 777), listOf(1280, 960, 1600, 900))) {
            val f = Bg.place(iw, ih, bw, bh, Bg.Fit.FILL)!!
            assertEquals(Triple(0, 0, bw), Triple(f.dstX, f.dstY, f.dstW)); assertEquals(bh, f.dstH)
            assertTrue(f.srcX >= 0 && f.srcY >= 0 && f.srcX + f.srcW <= iw && f.srcY + f.srcH <= ih, "fill stays inside the image $iw x $ih")
            assertTrue(kotlin.math.abs(f.srcW.toDouble() / f.srcH - bw.toDouble() / bh) < 0.02 * bw.toDouble() / bh, "fill $iw x $ih into $bw x $bh")
            assertTrue(kotlin.math.abs(2 * f.srcX + f.srcW - iw) <= 1 && kotlin.math.abs(2 * f.srcY + f.srcH - ih) <= 1, "fill is centred")
            val t = Bg.place(iw, ih, bw, bh, Bg.Fit.FIT)!!
            assertTrue(t.dstX >= 0 && t.dstY >= 0 && t.dstX + t.dstW <= bw && t.dstY + t.dstH <= bh, "fit stays inside the box")
            assertEquals(Triple(iw, ih, 0), Triple(t.srcW, t.srcH, t.srcX))
            assertTrue(t.dstW == bw || t.dstH == bh, "fit touches two sides")
            assertTrue(kotlin.math.abs(2 * t.dstX + t.dstW - bw) <= 1 && kotlin.math.abs(2 * t.dstY + t.dstH - bh) <= 1, "fit is centred")
        }
        for (bad in listOf(listOf(0, 720, 100, 100), listOf(1280, 0, 100, 100), listOf(1280, 720, 0, 100), listOf(1280, 720, 100, -1)))
            assertNull(Bg.place(bad[0], bad[1], bad[2], bad[3], Bg.Fit.FILL), bad.toString())
    }

    // ---- What a draw does -----------------------------------------------------------------------------------

    @Test
    fun `a draw is the colour, then the picture, then black over the picture at the dim level`() {
        val page = Color(0xFF123456)
        val ops = Bg.plan(page, 1280, 720, 150, 400, Bg.Fit.FILL, 40)
        assertEquals(3, ops.size)
        assertEquals(Bg.Op.Colour(page), ops[0])
        assertEquals(Bg.Op.Picture(Bg.place(1280, 720, 150, 400, Bg.Fit.FILL)!!), ops[1])
        assertEquals(Bg.Op.Dim(0.4f, 0, 0, 150, 400), ops[2])
    }

    @Test
    fun `the black layer covers the picture and not the colour around a Fit`() {
        val ops = Bg.plan(Color.Black, 1280, 720, 150, 400, Bg.Fit.FIT, 60)
        assertEquals(Bg.Op.Dim(0.6f, 0, 158, 150, 84), ops[2], "only where the picture is")
    }

    @Test
    fun `no black layer at a dim of 0, and never more than 90 percent`() {
        assertEquals(2, Bg.plan(Color.Black, 1280, 720, 150, 400, Bg.Fit.FILL, 0).size)
        assertEquals(0.9f, (Bg.plan(Color.Black, 1280, 720, 150, 400, Bg.Fit.FILL, 300)[2] as Bg.Op.Dim).alpha)
        assertEquals(2, Bg.plan(Color.Black, 1280, 720, 150, 400, Bg.Fit.FILL, -20).size)
    }

    @Test
    fun `without an image, or a box, a draw is only the colour`() {
        assertEquals(listOf<Bg.Op>(Bg.Op.Colour(Color.Blue)), Bg.plan(Color.Blue, 0, 0, 150, 400, Bg.Fit.FILL, 40))
        assertEquals(listOf<Bg.Op>(Bg.Op.Colour(Color.Blue)), Bg.plan(Color.Blue, 1280, 720, 0, 400, Bg.Fit.FILL, 40))
    }

    // ---- The backup ----------------------------------------------------------------------------------------

    @Test
    fun `the backup takes the presets, the image settings and the image`() {
        for (p in listOf("prep/theme-presets.txt", "prep/tracker-bg.txt", "prep/tracker-bg.jpg", "prep/theme.txt"))
            assertTrue(Backup.admits(p), p)
        assertEquals(ThemePresets.FILE, "prep/theme-presets.txt"); assertEquals(Bg.SETTINGS_FILE, "prep/tracker-bg.txt"); assertEquals(Bg.IMAGE_FILE, "prep/tracker-bg.jpg")
        assertFalse(Backup.admits("prep/tracker-bg.jpg.tmp"), "the half written copy is not a backup")
        val files = Files.createTempDirectory("filesdir").toFile()
        try {
            for (rel in listOf(ThemePresets.FILE, Bg.SETTINGS_FILE, Bg.IMAGE_FILE)) File(files, rel).apply { parentFile!!.mkdirs(); writeText("x") }
            val got = Backup.collect(files)
            assertTrue(listOf("prep/theme-presets.txt", "prep/tracker-bg.jpg", "prep/tracker-bg.txt").all { it in got }, got.toString())
        } finally { files.deleteRecursively() }
    }

    // ---- Wiring the JVM cannot run ---------------------------------------------------------------------------

    @Test
    fun `the image comes from the system photo picker, images only, with no storage permission`() {
        val ui = src("ThemePicker.kt")
        assertTrue("rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())" in ui)
        assertTrue("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)" in ui)
        assertTrue("Dispatchers.IO) { TrackerBackground.importFrom(" in ui, "decoding and saving are off the main thread")
        assertFalse("OpenDocument" in ui || "GetContent" in ui, "no file picker that wants a storage grant")
        assertFalse("requestpermission" in ui.lowercase(), "no permission is asked for")
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("READ_EXTERNAL_STORAGE" in manifest || "READ_MEDIA_IMAGES" in manifest || "MANAGE_EXTERNAL_STORAGE" in manifest)
    }

    @Test
    fun `the image is turned, shrunk and kept as a JPEG in the app's own folder`() {
        val bg = src("TrackerBackground.kt")
        assertTrue("android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)" in bg)
        assertTrue("TrackerBackground.orientationOf(tag)" in bg && "TrackerBackground.scaledSize(upW, upH)" in bg)
        assertTrue("Bitmap.CompressFormat.JPEG, TrackerBackground.JPEG_QUALITY" in bg)
        assertTrue("SafeWrite.bytes(out, jpeg.toByteArray())" in bg, "whole or not at all")
        assertTrue("BitmapFactory.Options().apply { inSampleSize = TrackerBackground.sampleSize(" in bg, "a big photo is not decoded whole")
        assertTrue("const val IMAGE_FILE = \"prep/tracker-bg.jpg\"" in bg)
        assertTrue("if (o.flip) m.postScale(-1f, 1f)" in bg && "m.postRotate(o.degrees.toFloat())" in bg)
    }

    @Test
    fun `nothing decodes while the tracker draws, and the black goes under nothing but the picture`() {
        val draw = src("TrackerBackdrop.kt")
        for (never in listOf("BitmapFactory", "decodeFile", "decodeStream", "decoder(", "importFrom", "asImageBitmap"))
            assertFalse(never in draw, "a draw must not call $never")
        assertTrue("val img = TrackerBackground.image" in draw)
        assertTrue("TrackerBackground.plan(" in draw)
        assertTrue("Color.Black.copy(alpha = op.alpha)" in draw, "the dim layer is black at the plan's alpha")
        assertTrue("filterQuality = FilterQuality.Medium" in draw)
    }

    @Test
    fun `the backdrop is painted in the panels, the floating window and the second display, and the hosts paint it once`() {
        for (panel in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            val s = src(panel)
            assertTrue("Column(Modifier.fillMaxWidth().then(trackerBackdrop()).padding(PcRef.MARGIN.rp))" in s, panel)
            assertFalse("Column(Modifier.fillMaxWidth().background(Pc.Page).padding(PcRef.MARGIN.rp))" in s, "$panel still paints only the colour")
        }
        val floating = src("FloatingTracker.kt")
        assertTrue(".hostBackdrop()" in floating && "LocalBackdropHosted provides true" in floating)
        assertFalse(".background(Pc.Page)" in floating)
        val second = src("SecondScreen.kt")
        assertTrue(".hostBackdrop()" in second && "LocalBackdropHosted provides true" in second)
        assertFalse(".background(Pc.Page)" in second)
        val backdrop = src("TrackerBackdrop.kt")
        assertTrue("if (LocalBackdropHosted.current) Modifier else Modifier.drawBehind(paintBackdropBehind)" in backdrop, "a panel in a host draws no second copy")
    }

    @Test
    fun `PlayScreen is not touched by any of it`() {
        val play = src("PlayScreen.kt")
        for (name in listOf("TrackerBackground", "trackerBackdrop", "hostBackdrop", "LocalBackdropHosted", "paintBackdrop", "ThemePresets", "ThemePreset", "WholeTheme", "ThemeCodes"))
            assertFalse(name in play, "PlayScreen.kt must not mention $name: it is at the verifier's limit")
    }

    @Test
    fun `the stores load at launch and the editor is given the console`() {
        val main = src("MainActivity.kt")
        assertTrue("ThemePresets.load(java.io.File(filesDir, ThemePresets.FILE))" in main)
        assertTrue("TrackerBackground.load(filesDir)" in main)
        assertTrue("ThemeStore.load(java.io.File(filesDir, \"prep/theme.txt\"))" in main)
        assertTrue("ColorThemeDialog(ds = ndsTrackerRef != null)" in src("SideScreens.kt"), "a DS game gets the DS tracker's themes")
        val theme = src("Theme.kt")
        assertTrue("ThemePresetSection(ds) { refreshEdits() }" in theme && "TrackerImageSection()" in theme)
        assertTrue("ThemeStore.edit(k, c)" in theme, "the hex editor edits through the store, so a preset's tied colours follow")
    }

    @Test
    fun `the dialog says the stream page keeps its own colours, and what an auto theme does to a preset`() {
        val ui = src("ThemePicker.kt")
        assertTrue("The OBS stream page has its own colors and does not show the image." in ui)
        assertTrue("if (TrackerOptions.autoPokemonThemes)" in ui)
        assertTrue("Turn it off in the tracker's setup to keep the preset you pick." in ui)
        assertFalse(ui.contains(0x2014.toChar()), "no em dash in what the player reads")
        // The setting the line is about is a real one.
        assertTrue("var autoPokemonThemes by mutableStateOf(false)" in src("TrackerOptions.kt"))
    }

    @Test
    fun `see-through boxes is kept in the file, clamped, and off without an image`() {
        Bg.applySettings("dim=40\nfit=fill\nsee=60")
        assertEquals(60, Bg.seeThrough)
        Bg.applySettings("see=250"); assertEquals(Bg.MAX_SEE_THROUGH, Bg.seeThrough, "clamped, not refused")
        Bg.applySettings("see=-5"); assertEquals(0, Bg.seeThrough)
        Bg.applySettings("dim=40"); assertEquals(Bg.DEFAULT_SEE_THROUGH, Bg.seeThrough, "a file from before the setting reads as the default")
        val c = androidx.compose.ui.graphics.Color(0xFF333333)
        assertEquals(c, Bg.boxFill(c, hasImage = false, seeThrough = 60), "no image: the box stays solid")
        assertEquals(c, Bg.boxFill(c, hasImage = true, seeThrough = 0), "0 percent: solid")
        assertEquals(0.4f, Bg.boxFill(c, hasImage = true, seeThrough = 60).alpha, 0.01f, "60 percent off a solid box")
        assertEquals(0.2f, Bg.boxFill(c.copy(alpha = 0.5f), hasImage = true, seeThrough = 60).alpha, 0.01f, "and off a box that was already see-through")
        assertEquals(c.red, Bg.boxFill(c, hasImage = true, seeThrough = 60).red, 0.001f, "the colour itself is kept")
    }

    @Test
    fun `see-through reaches the tracker's boxes and not its dialogs`() {
        val pc = src("PcTracker.kt")
        assertEquals(6, Regex(Regex.escape("TrackerBackground.boxFill(")).findAll(pc).count(),
            "card, battle banner, carousel line, badge row, move header, move list")
        val dialog = pc.substringAfter("fun PcInfoDialog(").substringBefore("\n}\n")
        assertTrue(dialog.length < 4000, "the dialog body was cut out, not the rest of the file")
        assertFalse("boxFill" in dialog, "an info dialog stays solid over the game")
        val lookup = pc.substringAfter("fun PcNameLookup(").substringBefore("\n}\n")
        assertTrue(lookup.length < 4000)
        assertFalse("boxFill" in lookup, "and so does the name lookup")
        assertTrue("TrackerBackground.changeSeeThrough(" in src("ThemePicker.kt"), "the picker has the slider")
    }

    /**
     * rc32 audit P3 #21: the Team View, the step counter, the battle summary line and the last attack line drew their
     * own solid fill, so with a picture behind the tracker those four stayed opaque while every other box let it through,
     * and the picker still said the boxes keep their own colours.
     */
    @Test
    fun `the carousel's own lines and the Team View let the picture through too`() {
        val summary = src("BattleSummary.kt").substringAfter("fun PcBattleSummaryLine(").substringBefore("\n}\n")
        assertTrue("background(TrackerBackground.boxFill(Pc.LowerGroundX ?: Pc.Ground))" in summary)
        val steps = src("Pedometer.kt").substringAfter("fun PcPedometerLine(").substringBefore("\n}\n")
        assertTrue("background(TrackerBackground.boxFill(Pc.Ground))" in steps)
        val team = src("TeamView.kt")
        assertTrue("Modifier.width(boxW.rp).background(TrackerBackground.boxFill(Pc.Ground))" in team)
        val attack = src("MoveDecor.kt").substringAfter("fun PcLastAttackLine(").substringBefore("\n}\n")
        assertTrue("background(TrackerBackground.boxFill(Pc.Ground))" in attack)
        for ((name, body) in listOf("summary" to summary, "steps" to steps, "attack" to attack)) {
            assertFalse(Regex("\\.background\\(Pc\\.(Ground|LowerGroundX)").containsMatchIn(body), "$name draws no solid fill")
        }
        val ui = src("ThemePicker.kt")
        assertFalse("keep their own colors" in ui, "the picker's help line is the slider's now")
        assertTrue("See-through boxes sets how much of it shows through the tracker's boxes." in ui)
    }
}
