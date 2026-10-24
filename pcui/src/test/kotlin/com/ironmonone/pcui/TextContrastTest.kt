package com.ironmonone.pcui

import com.ironmonone.app.PixTextCheck
import com.ironmonone.app.ThemeCodes
import com.ironmonone.app.ThemeStore
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every word on the card readable under every auto theme (Blake, 2026-10-09: "the text is always visible"), measured on
 * the picture (PcInkCheck): 22 cards and screens, each word against what is really behind it. The log viewer is
 * desktop's LogContrastTest, which needs a real run's log.
 */
class TextContrastTest {
    private val out = File(System.getProperty("contrast.out") ?: "build/contrast").apply { mkdirs() }

    /** One card to check: what is drawn, and what is opened over it first (a screen, the settings, a description). */
    class Case(val name: String, val input: PcPanel.Input, val open: (PcPanel.Renderer) -> Unit = {})

    private fun cases(): List<Case> {
        val party = PcPanel.Input(Samples.party, 740)
        val enemy = PcPanel.Input(Samples.enemy, 740, routeName = "Route 24", moveLevels = Samples.levels)
        val list = mutableListOf(
            Case("start", PcPanel.Input(null, 740, title = "Heart & Soul (KaizoCore)")),
            Case("lab", PcPanel.Input(Samples.lab, 740)),
            Case("party", party),
            Case("enemy", enemy),
            Case("wild", PcPanel.Input(Samples.wild, 740, routeName = "Petalburg Woods", moveLevels = Samples.levels)),
            Case("revealed", PcPanel.Input(Samples.revealed, 740, routeName = "Petalburg Woods")),
            Case("own", PcPanel.Input(Samples.enemy, 740, routeName = "Route 24", showOwn = true)),
            Case("over", PcPanel.Input(Samples.over, 740)),
            Case("describe", party) { it.inspect.show("MUDDY WATER", "An attack with muddy water. It may lower the target's accuracy.") },
            Case("chart", party) { it.chart.open(listOf(11, 12), true) },
        )
        for (tab in PcSettingsScreen.Tab.entries) list += Case("settings-${tab.name.lowercase()}", party) { it.settings.open = true; it.settings.tab = tab }
        val screens = listOf(
            "info" to PcScreens.Screen.PokemonInfo(25), "learned" to PcScreens.Screen.MovesLearned(25, 20),
            "history" to PcScreens.Screen.MoveHistory(25, "PIKACHU", listOf(6, 8, 11, 15, 20, 26, 33, 41, 50)),
            "move" to PcScreens.Screen.MoveInfo(57), "bag" to PcScreens.Screen.HealsInBag, "catch" to PcScreens.Screen.CatchRates,
            "battle" to PcScreens.Screen.BattleDetails, "note" to PcScreens.Screen.Note("NOTE", "Watch out for its Thunder Wave."),
            "type" to PcScreens.Screen.TypeInfo(13, true),
        )
        for ((n, s) in screens) list += Case("screen-$n", enemy) { it.screens.push(s) }
        return list
    }

    /** Back to the plain card after a case. */
    private fun reset(r: PcPanel.Renderer) {
        r.settings.open = false
        r.inspect.open = false
        r.chart.close()
        r.screens.close()
    }

    private fun draw(r: PcPanel.Renderer, input: PcPanel.Input): BufferedImage = ImageIO.read(r.render(input).inputStream())

    @Test
    fun `every word on the card reads on what is behind it, under every auto theme`() {
        val before = ThemeStore.snapshot()
        val tally = PcInkCheck.Tally(3)
        val undrawable = LinkedHashSet<String>()
        try {
            val all = PcInkCheck.themes()
            assertTrue(all.size >= 386, "${all.size} themes")
            // CONTRAST_THEMES=n checks the first n only, CONTRAST_ONLY=gen3-190,... those named (a quick look); unset checks them all.
            val only = System.getenv("CONTRAST_ONLY")?.split(',')?.map { it.trim() }?.toSet()
            val themes = (if (only != null) all.filter { it.first in only } else all).let { t -> System.getenv("CONTRAST_THEMES")?.toIntOrNull()?.let { t.take(it) } ?: t }
            PcPanel.Renderer(File(out, "files").apply { mkdirs() }, PcPanel.Input(null, 0), 3f).use { r ->
                for ((name, code) in themes) {
                    ThemeStore.show(ThemeCodes.gen3(code) ?: error("$name does not parse"))
                    for (case in cases()) {
                        reset(r)
                        val drawn = runCatching {
                            case.open(r)
                            r.render(case.input)
                            val a = draw(r, case.input)
                            PixTextCheck.hide = true
                            val h = try { draw(r, case.input) } finally { PixTextCheck.hide = false }
                            Triple(a, h, draw(r, case.input))
                        }
                        reset(r)
                        val got = drawn.getOrNull()
                        if (got == null) { undrawable += "${case.name}: ${drawn.exceptionOrNull()}"; continue }
                        val (a, h, a2) = got
                        tally.add(name, case.name, a, PcInkCheck.words(a, a2, h))
                    }
                }
            }
        } finally { ThemeStore.show(before); PixTextCheck.hide = false }
        val lines = tally.report(out)
        println("CONTRAST words=${tally.words} under${PcInkCheck.LIMIT}=${tally.fails} spots=${tally.spots.size}")
        lines.forEach { println("CONTRAST SPOT $it") }
        undrawable.forEach { println("CONTRAST UNDRAWN $it") }
        assertTrue(tally.words > 1000, "${tally.words} words found: the check is not seeing the card")
        assertTrue(undrawable.isEmpty(), "cards that could not be drawn: $undrawable")
        assertTrue(tally.fails == 0, "${tally.fails} words under ${PcInkCheck.LIMIT}:1 in ${tally.spots.size} spots; see ${File(out, "spots.tsv")}")
    }
}
