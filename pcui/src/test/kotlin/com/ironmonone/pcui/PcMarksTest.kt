package com.ironmonone.pcui

import androidx.compose.ui.graphics.toArgb
import com.ironmonone.app.Pc
import com.ironmonone.app.StatMarks
import com.ironmonone.app.ThemeStore
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stat marks the way besteon colours them (Constants.STAT_STATES), in every theme the panel can show: "+" Positive, the
 * minus sign Negative, "=" Default text. The minus is one proper minus sign, centred as "+" is (Blake, 2026-10-07).
 */
class PcMarksTest {
    private val dir = kotlin.io.path.createTempDirectory("kc-marks").toFile()

    @Test
    fun `the negative mark is one minus sign, and the marks file keeps the state number`() {
        assertEquals("−", StatMarks.symbol(2))
        assertEquals(listOf("+", "−", "="), (1..3).map { StatMarks.symbol(it) })
        val m = StatMarks(File(dir, "marks.txt"))
        m.cycle(245, 0); m.cycle(245, 1); m.cycle(245, 1)
        val saved = File(dir, "marks.txt").takeIf { it.isFile }?.readText().orEmpty()
        assertTrue("−" !in saved && "--" !in saved, "the file holds numbers: $saved")
        dir.deleteRecursively()
    }

    @Test
    fun `plus is Positive, minus Negative, equals Default text, on the opponent's card in every theme`() {
        val before = ThemeStore.snapshot()
        try {
            val themes = com.ironmonone.app.ThemePresets.builtIns(false).take(6).map { it.theme } +
                listOfNotNull(com.ironmonone.app.ThemeCodes.gen3(com.ironmonone.app.AutoTheme.gbaTheme(25) ?: ""))
            assertTrue(themes.size >= 6)
            for (t in themes) {
                ThemeStore.show(t)
                val marks = StatMarks(File(dir, "marks-${System.nanoTime()}.txt"))
                // Suicune's HP "+", ATK the minus, DEF "=".
                marks.cycle(245, 0); marks.cycle(245, 1); marks.cycle(245, 1); marks.cycle(245, 2); marks.cycle(245, 2); marks.cycle(245, 2)
                PcPanel.Renderer(File(dir, "files"), PcPanel.Input(Samples.enemy, 3), 3f).use { r ->
                    val img = ImageIO.read(r.render(PcPanel.Input(Samples.enemy, 3, marks, version = 1)).inputStream())
                    fun box(i: Int): Set<Int> {
                        val out = HashSet<Int>()
                        for (y in (9 + 10 * i) * 3 + 3 until (9 + 10 * i + 8) * 3) for (x in 129 * 3 + 3 until (129 + 8) * 3) out += img.getRGB(x, y)
                        return out
                    }
                    // Each in the theme's colour as the card keeps it readable on the upper box (readableInk, 4.5 to 1):
                    // a theme whose Positive is too pale for its own box has it drawn deeper, the same hue.
                    fun ink(c: androidx.compose.ui.graphics.Color) = com.ironmonone.app.readableInk(c, Pc.Ground).toArgb()
                    assertTrue(ink(Pc.Positive) in box(0), "+ in Positive")
                    assertTrue(ink(Pc.Negative) in box(1), "the minus in Negative")
                    assertTrue(ink(Pc.Text) in box(2), "= in Default text")
                    assertTrue(ink(Pc.Negative) !in box(0) && ink(Pc.Positive) !in box(1), "no colour crosses over")
                }
            }
        } finally { ThemeStore.show(before); dir.deleteRecursively() }
    }
}
