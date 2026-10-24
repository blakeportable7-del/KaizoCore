package com.ironmonone.pc

import com.ironmonone.app.PixTextCheck
import com.ironmonone.app.ThemeCodes
import com.ironmonone.app.ThemeStore
import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.pcui.PcInkCheck
import com.ironmonone.pcui.PcLogData
import com.ironmonone.pcui.PcLogOverlay
import com.ironmonone.pcui.PcPanel
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every word in the log viewer readable under every auto theme (Blake, 2026-10-09: "add the contrast rule to the log
 * viewer too"), measured as the card is (PcInkCheck), on a real run's log (the comfort build, seed 1234, as
 * LogOverlayTest makes it): each tab, a Pokemon's page, a trainer's, a route's, and the card's side of the log.
 */
class LogContrastTest {
    private val out = File(System.getProperty("contrast.out") ?: "build/contrast-log").apply { mkdirs() }

    private fun read(png: ByteArray): BufferedImage = ImageIO.read(png.inputStream())

    @Test
    fun `every word in the log viewer reads on what is behind it, under every auto theme`() {
        val comfort = TestFiles.comfortBuild ?: return println("LogContrastTest skipped: no .vendor/hns/hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        val r = PcHns.randomize(build, PcHns.options(PcHns.settingsFile(PcHns.DEFAULT_PRESET)), 1234L, HnsEngine.Pool.NATDEX)
        val dir = TestFiles.tempDir("logink")
        // A session test before this one can leave an Auto Pokemon Theme showing; with it the themes below were not the
        // ones drawn (Porygon's failed only in the full suite, 2026-10-11). Off first, so each theme is the one checked.
        com.ironmonone.app.AutoTheme.onGba(emptyList(), false)
        val before = ThemeStore.snapshot()
        val tally = PcInkCheck.Tally(3)
        try {
            val logFile = File(dir, "run.gba.log").apply { writeText(r.logText) }
            val reader = BridgeReader(r.rom)
            val d = PcLogData.load(logFile, GbaTracker(reader, GameMap.resolve(reader))) ?: error("the log does not read")
            val lead = d.log.pokemon.first()
            val trainer = d.log.trainers.first()
            val route = d.routes.first()
            val views = PcLogOverlay.Tab.entries.map { "tab-${it.name.lowercase()}" to PcLogOverlay.View(tab = it) } + listOf(
                "pokemon" to PcLogOverlay.View(pokemon = lead),
                "trainer" to PcLogOverlay.View(tab = PcLogOverlay.Tab.TRAINERS, trainer = trainer),
                "route" to PcLogOverlay.View(tab = PcLogOverlay.Tab.ROUTES, route = route),
            )
            val all = PcInkCheck.themes()
            // CONTRAST_THEMES=n checks the first n only, CONTRAST_ONLY=gen3-190,... those named (a quick look); unset checks them all.
            val only = System.getenv("CONTRAST_ONLY")?.split(',')?.map { it.trim() }?.toSet()
            val themes = (if (only != null) all.filter { it.first in only } else all).let { t -> System.getenv("CONTRAST_THEMES")?.toIntOrNull()?.let { t.take(it) } ?: t }
            PcLogOverlay.Renderer(3f).use { o ->
                o.open(d, d.speciesId(lead))
                PcPanel.Renderer(File(dir, "panel"), PcPanel.Input(null, 0), 3f).use { panel ->
                    val side = PcPanel.Input(null, 3, log = o.shared)
                    for ((name, code) in themes) {
                        ThemeStore.show(ThemeCodes.gen3(code) ?: error("$name does not parse"))
                        for ((what, v) in views) {
                            o.set(v)
                            o.render()
                            val a = read(o.render())
                            PixTextCheck.hide = true
                            val h = try { read(o.render()) } finally { PixTextCheck.hide = false }
                            tally.add(name, "log-$what", a, PcInkCheck.words(a, read(o.render()), h))
                            // The card beside it shows this view's side (the Pokemon's card, the search screen).
                            panel.render(side)
                            val pa = read(panel.render(side))
                            PixTextCheck.hide = true
                            val ph = try { read(panel.render(side)) } finally { PixTextCheck.hide = false }
                            tally.add(name, "side-$what", pa, PcInkCheck.words(pa, read(panel.render(side)), ph))
                        }
                    }
                }
            }
        } finally { ThemeStore.show(before); PixTextCheck.hide = false; dir.deleteRecursively() }
        val lines = tally.report(out)
        println("CONTRAST LOG words=${tally.words} under${PcInkCheck.LIMIT}=${tally.fails} spots=${tally.spots.size}")
        lines.forEach { println("CONTRAST LOG SPOT $it") }
        assertTrue(tally.words > 1000, "${tally.words} words found: the check is not seeing the log")
        assertTrue(tally.fails == 0, "${tally.fails} words under ${PcInkCheck.LIMIT}:1 in ${tally.spots.size} spots; see ${File(out, "spots.tsv")}")
    }
}
