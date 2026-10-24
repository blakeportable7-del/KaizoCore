package com.ironmonone.pcui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.ironmonone.app.Pc
import com.ironmonone.app.PcCanvas
import com.ironmonone.app.PcCrispText
import com.ironmonone.app.PixText
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcPanelTest {
    private val files = File(System.getProperty("java.io.tmpdir"), "pcui-test-${System.nanoTime()}")

    private fun colors(png: ByteArray, x0: Int = 0, y0: Int = 0, w: Int = -1, h: Int = -1): Set<Int> {
        val img = ImageIO.read(png.inputStream())
        val ww = if (w < 0) img.width else w
        val hh = if (h < 0) img.height else h
        return (y0 until y0 + hh).flatMap { y -> (x0 until x0 + ww).map { x -> img.getRGB(x, y) } }.toSet()
    }

    @Test
    fun `the panel is 150 by 160 at 1x and 450 by 480 at a 3x window, and draws each card`() {
        try {
            for (scale in listOf(1f, 3f)) PcPanel.Renderer(files, PcPanel.Input(null, 0), scale).use { r ->
                for (input in listOf(PcPanel.Input(null, 3), PcPanel.Input(Samples.party, 3), PcPanel.Input(Samples.enemy, 3))) {
                    val png = r.render(input)
                    val img = ImageIO.read(png.inputStream())
                    assertEquals((150 * scale).toInt() to (160 * scale).toInt(), img.width to img.height)
                    assertTrue(colors(png).size >= 3, "something is drawn")
                }
            }
        } finally { files.deleteRecursively() }
    }

    /** The reference's rows (besteon's TrackerScreen.lua): the boxes from y 5, the moves box to 136 and the carousel strip under it, inside 160. */
    @Test
    fun `the whole card and the carousel strip fit the 160 rows at the reference's positions`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(null, 0)).use { r ->
                for (input in listOf(PcPanel.Input(Samples.party, 3), PcPanel.Input(Samples.enemy, 3, routeName = "Route 24"))) {
                    r.render(input)   // the first frame of a new input lays out; the second is what the panel shows
                    val img = ImageIO.read(r.render(input).inputStream())
                    val page = Pc.Page.toArgb()
                    fun drawn(y: Int) = (0 until img.width).any { x -> img.getRGB(x, y) != page }
                    // A box's border: one colour, not the page's, across most of the row (the icon pokes into row 5).
                    fun borderRow(y: Int) = (0 until img.width).map { x -> img.getRGB(x, y) }.filter { it != page }
                        .groupingBy { it }.eachCount().values.maxOrNull()?.let { it > 60 } == true   // the corners are rounded and the icon crosses row 5
                    val bottom = (0 until img.height).last { drawn(it) }
                    fun rowColours(y: Int) = (0 until img.width).map { x -> img.getRGB(x, y) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
                        .joinToString { "%08x x%d".format(it.key, it.value) }
                    assertTrue(borderRow(5), "the card's top border at the reference's margin, y 5: rows 3 to 7 are ${(3..7).map { rowColours(it) }}")
                    assertTrue(borderRow(135), "the card's bottom border at the reference's y 135 (the moves box ends at 136)")
                    assertTrue(bottom in 150..157, "the carousel's bottom row: $bottom")
                }
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `at a 3x window the text is drawn at 3x with hard edges, not scaled up`() {
        val scene = ImageComposeScene(450, 60, Density(3f)) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(platformStyle = PcCrispText)) {
                Box(Modifier.fillMaxSize().background(Pc.Ground)) { PcCanvas { PixText("Heals: 110% HP (3) Golisopod", 9, Pc.Text) } }
            }
        }
        val png = scene.render().encodeToData()!!.bytes
        scene.close()
        val seen = colors(png)
        assertEquals(setOf(Pc.Ground.toArgb(), Pc.Text.toArgb()), seen, "colours: ${seen.map { Integer.toHexString(it) }}")
        // Drawn at 3x: the glyphs are about 27 rows tall, not 9 rows stretched (a stretched picture has 3-row stair steps everywhere).
        val img = ImageIO.read(png.inputStream())
        val rows = (0 until img.height).count { y -> (0 until img.width).any { x -> img.getRGB(x, y) == Pc.Text.toArgb() } }
        assertTrue(rows in 18..32, "text rows at 3x: $rows")
    }

    @Test
    fun `the tracker's text is drawn without blended edges`() {
        // A line of the tracker's own text on its own box colour: aliased, only those two colours, so BizHawk's
        // nearest-pixel scaling keeps it sharp.
        val scene = ImageComposeScene(150, 20, Density(1f)) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(platformStyle = PcCrispText)) {
                Box(Modifier.fillMaxSize().background(Pc.Ground)) { PcCanvas { PixText("Heals: 110% HP (3) Golisopod", 9, Pc.Text) } }
            }
        }
        val png = scene.render().encodeToData()!!.bytes
        scene.close()
        val seen = colors(png)
        assertEquals(setOf(Pc.Ground.toArgb(), Pc.Text.toArgb()), seen, "colours: ${seen.map { Integer.toHexString(it) }}")
    }

    @Test
    fun `a click on a stat box marks that stat of the opponent`() {
        try {
            val got = ArrayList<Pair<Int, Int>>()
            PcPanel.Renderer(files, PcPanel.Input(Samples.enemy, 3)).use { r ->
                r.onCycleMark = { sp, i -> got += sp to i }
                r.render(PcPanel.Input(Samples.enemy, 3))
                // The HP box, then the SPE box: the column of marks at the card's right edge, rows 10 apart from y 8
                // (the reference's statOffsetY 7 in a box at 5, one more for the border).
                r.click(135, 12)
                r.render(PcPanel.Input(Samples.enemy, 3))
                r.click(135, 62)
                r.render(PcPanel.Input(Samples.enemy, 3))
            }
            assertEquals(listOf(245 to 0, 245 to 5), got)
        } finally { files.deleteRecursively() }
    }

    /**
     * A click leaves no mark of its own on the picture (2026-10-07, Blake: "most clicks flash"): Compose's default press
     * and hover look greyed the clicked control in the next picture, and its animation asked for a second drawing. A click
     * on a control whose action changes nothing must give the same picture, byte for byte, as before it.
     */
    @Test
    fun `a click that changes nothing leaves the picture exactly as it was`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.enemy, 3), 3f).use { r ->
                r.onNote = {}
                val before = r.render(PcPanel.Input(Samples.enemy, 3))
                r.click(60, 145)   // "(Leave a note)": its action is the caller's, nothing here
                val after = r.render(PcPanel.Input(Samples.enemy, 3))
                assertTrue(before.contentEquals(after), "the clicked strip changed the picture")
                assertTrue(before[0] == 'B'.code.toByte() && before[1] == 'M'.code.toByte(), "the hand-off is a BMP")
            }
        } finally { files.deleteRecursively() }
    }

    /** besteon's gear (TrackerScreen.Buttons.SettingsGear at x 92, y 7) opens the settings; a checkbox flips its option; the back arrow closes. */
    @Test
    fun `the gear opens the settings, a checkbox flips its option and the back arrow closes them`() {
        try {
            val before = com.ironmonone.app.TrackerOptions.showCatchRate
            var changed = 0
            PcPanel.Renderer(files, PcPanel.Input(Samples.wild, 3)).use { r ->
                r.onSettingsChanged = { changed++ }
                r.render(PcPanel.Input(Samples.wild, 3))
                r.click(95, 10); r.render(PcPanel.Input(Samples.wild, 3))
                assertTrue(r.settings.open, "the gear opened the settings")
                assertEquals(PcSettingsScreen.Tab.BATTLE, r.settings.tab)
                // The second Battle row, "Show Poke Ball catch rate", at y 43.
                r.click(12, PcSettingsScreen.rowY(1) + 3); r.render(PcPanel.Input(Samples.wild, 3))
                assertEquals(!before, com.ironmonone.app.TrackerOptions.showCatchRate)
                assertEquals(1, changed)
                // The Theme tab, then back.
                val (tx, _) = PcSettingsScreen.tabBoxes()[2]
                r.click(tx + 3, 20); r.render(PcPanel.Input(Samples.wild, 3))
                assertEquals(PcSettingsScreen.Tab.THEME, r.settings.tab)
                r.click(135, 146); r.render(PcPanel.Input(Samples.wild, 3))
                assertTrue(!r.settings.open, "the back arrow closed the settings")
            }
            com.ironmonone.app.TrackerOptions.showCatchRate = before
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the shared tracker files call Android only through the platform's names`() {
        val repo = File(System.getProperty("kaizocore.repo") ?: "..")
        val list = File(repo, "pcui/app-files.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(list.size > 25)
        for (name in list) {
            val code = File(repo, "app/src/main/kotlin/com/ironmonone/app/$name").readLines()
                .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            val bad = code.filter { Regex("""\bandroid\.(content|graphics|view|os|util|provider|hardware|app|widget|media|net)\b""").containsMatchIn(it) ||
                "ui.platform.LocalContext" in it || "R.font." in it || "R.drawable." in it }
            assertTrue(bad.isEmpty(), "$name reaches Android itself (put it behind PcPlatformAndroid.kt's names): ${bad.take(3)}")
        }
    }
}
