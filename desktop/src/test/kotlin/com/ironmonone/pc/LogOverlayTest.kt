package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.pcui.PcLogData
import com.ironmonone.pcui.PcLogOverlay
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The log viewer (PLAN row 7) on a real Heart & Soul run's log: made here from the comfort build (seed 1234, the pinned
 * one), read by PcLogData with the run's own tracker, then driven as the script drives it: opened on a lead's page,
 * the tab icons, a grid cell, the back arrow, the wheel, the panel's search screen and keyboard, the X. With
 * -Dkaizocore.logShots=<dir> it writes the pictures.
 */
class LogOverlayTest {
    @Test
    fun `the viewer opens on the log, turns its pages, opens a Pokemon and a trainer, searches and closes`() {
        val comfort = TestFiles.comfortBuild ?: return println("LogOverlayTest skipped: no .vendor/hns/hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        val settings = PcHns.settingsFile(PcHns.DEFAULT_PRESET)
        val r = PcHns.randomize(build, PcHns.options(settings), 1234L, HnsEngine.Pool.NATDEX)
        val dir = TestFiles.tempDir("log")
        try {
            val logFile = File(dir, "run.gba.log").apply { writeText(r.logText) }
            val reader = BridgeReader(r.rom)
            val tracker = GbaTracker(reader, GameMap.resolve(reader))
            val t0 = System.nanoTime()
            val d = assertNotNull(PcLogData.load(logFile, tracker), "the log reads")
            println("log read in %.0f ms: ${d.log.pokemon.size} Pokemon, ${d.log.trainers.size} trainers, ${d.routes.size} routes, ${d.gymTms.size} gym TMs".format((System.nanoTime() - t0) / 1e6))
            assertTrue(d.log.pokemon.size > 300 && d.log.trainers.size > 100 && d.routes.size > 20 && d.gymTms.size == 16)

            val closed = ArrayList<Int>()
            val shots = System.getProperty("kaizocore.logShots")?.let { File(it).apply { mkdirs() } }
            PcLogOverlay.Renderer(3f).use { o ->
                o.onClose = { closed += 1 }
                fun shot(name: String, png: ByteArray) { shots?.let { File(it, "$name.png").writeBytes(png) } }
                // Opened on the lead's page: the first Pokemon of the log stands in for the party's lead.
                val lead = d.log.pokemon.first()
                o.open(d, d.speciesId(lead))
                assertEquals(lead, o.view.pokemon)
                val png = o.render()
                shot("log-pokemon-page", png)
                assertEquals(720 to 480, ImageIO.read(png.inputStream()).let { it.width to it.height })
                // The panel beside it: the log's Pokemon card, then the search screen with the grid.
                com.ironmonone.pcui.PcPanel.Renderer(File(dir, "panel"), com.ironmonone.pcui.PcPanel.Input(null, 0), 3f).use { panel ->
                    val input = com.ironmonone.pcui.PcPanel.Input(null, 3, log = o.shared)
                    panel.render(input); shot("panel-log-card", panel.render(input))
                    // The back arrow (LogOverlay's XIcon at x 230 inside a page): the grid, sixteen to a page.
                    o.click(233, 5); shot("log-grid", o.render())
                    assertNull(o.view.pokemon); assertEquals(PcLogOverlay.Tab.POKEMON, o.view.tab)
                    assertEquals((d.pokemonRows("").size + 15) / 16, PcLogOverlay.totalPages(d, o.view))
                    panel.render(input); shot("panel-log-search", panel.render(input))
                    // The keyboard: C, H, then backspace and clear (LogSearchScreen's keys, 11 by 11, rows 13 apart from y 100).
                    panel.click(53, 144); panel.render(input)
                    panel.click(87, 131); panel.render(input)
                    assertEquals("CH", o.view.query)
                    assertTrue(PcLogOverlay.pokemonRows(d, o.view).all { d.shownName(it).contains("ch", ignoreCase = true) })
                    shot("log-grid-search-ch", o.render()); shot("panel-log-search-ch", panel.render(input))
                    panel.click(120, 85); panel.render(input); assertEquals("C", o.view.query)
                    panel.click(135, 85); panel.render(input); assertEquals("", o.view.query)
                    // Sort by: the drop-down, then Alphabetical, the second choice.
                    panel.click(80, 22); panel.render(input)
                    assertTrue(o.shared.sortOpen)
                    shot("panel-log-sort-open", panel.render(input))
                    panel.click(80, 18 + 13 + 13 + 5); panel.render(input)
                    assertEquals(com.ironmonone.app.LogSort.ALPHA, o.view.sort)
                    assertEquals(d.pokemonRows("", com.ironmonone.app.LogFilter.NAME, com.ironmonone.app.LogSort.ALPHA).first(), PcLogOverlay.pokemonRows(d, o.view).first())
                    o.set(o.view.copy(sort = null))
                }
                // The wheel down: page 2; up: page 1 again; the header's arrows the same.
                o.wheel(-120); assertEquals(2, o.view.page)
                o.wheel(120); assertEquals(1, o.view.page)
                o.render(); o.click(209, 5); o.render(); assertEquals(2, o.view.page)
                o.click(146, 5); o.render(); assertEquals(1, o.view.page)
                // The first grid cell (32 by 32 at (21, 14)): its Pokemon's page, with the grid under it.
                o.click(37, 30); o.render()
                val first = d.pokemonRows("").first()
                assertEquals(first, o.view.pokemon)
                assertEquals(1, o.view.history.size)
                // TM Moves on the page (the moves column at x 120, under the evolutions when it has some).
                val my = 12 + if (first.evolutions.isNotEmpty()) 42 else 0
                o.click(120 + 70 + 20, my + 5); o.render()
                assertEquals(PcLogOverlay.MovesTab.TM, o.view.movesTab)
                // The tab icons: Trainers, the second. besteon's default filter is the gym leaders; then All, the first tile.
                val tabs = PcLogOverlay.tabBoxes()
                fun tab(i: Int) = tabs[i].first + tabs[i].second / 2
                o.click(tab(1), 5); o.render()
                assertEquals(PcLogOverlay.Tab.TRAINERS, o.view.tab); assertNull(o.view.pokemon)
                assertEquals(com.ironmonone.app.LogTrainerFilter.GYM, o.view.trainerFilter)
                shot("log-trainers", o.render())
                val gyms = d.trainerRows(com.ironmonone.app.LogTrainerFilter.GYM, "")
                assertTrue(gyms.size in 16..40 && gyms.size < d.trainerRows(com.ironmonone.app.LogTrainerFilter.ALL, "").size, "the gym leaders: ${gyms.size}")
                val filters = PcLogOverlay.filterBoxes(com.ironmonone.app.LogTrainerFilter.entries.map { it.label })
                o.click(filters[0].first + 3, 17); o.render()
                assertEquals(com.ironmonone.app.LogTrainerFilter.ALL, o.view.trainerFilter)
                o.click(17 + 16, 42 + 16); o.render()
                val t = assertNotNull(o.view.trainer, "the first trainer tile opens its page")
                assertEquals(d.trainerRows(com.ironmonone.app.LogTrainerFilter.ALL, "").first(), t)
                assertTrue(o.shared.side is PcLogOverlay.Side.Trainer)
                shot("log-trainer-page", o.render())
                // A party member's name on the trainer's page ("1. Name" at (3, 94)) opens its Pokemon.
                o.click(10, 12 + 82 + 4); o.render()
                assertNotNull(o.view.pokemon, "the first party member's page")
                assertEquals(2, o.view.history.size)
                o.click(233, 5); o.render(); assertEquals(t, o.view.trainer)
                o.click(233, 5); o.render(); assertNull(o.view.trainer)
                // Routes: the rows 21 tall from y 29, the first route's page and its route info.
                o.click(tab(2), 5); o.render()
                assertEquals(PcLogOverlay.Tab.ROUTES, o.view.tab)
                shot("log-routes", o.render())
                o.click(60, 35); o.render()
                val route = assertNotNull(o.view.route)
                assertEquals(d.routeRows("").first(), route)
                assertTrue(o.shared.side is PcLogOverlay.Side.Route)
                shot("log-route-page", o.render())
                // TMs: by gym (the default), then by number; a TM opens its move's info on the panel.
                o.click(tab(3), 5); o.render()
                assertEquals(PcLogOverlay.Tab.TMS, o.view.tab)
                shot("log-tms-gym", o.render())
                val tmFilters = PcLogOverlay.filterBoxes(com.ironmonone.app.LogTmFilter.entries.map { it.label })
                o.click(tmFilters[0].first + 3, 17); o.render()
                assertEquals(com.ironmonone.app.LogTmFilter.TM_NUMBER, o.view.tmFilter)
                assertTrue(PcLogOverlay.totalPages(d, o.view) >= 2)
                o.click(30, 30); o.render()
                assertTrue(o.shared.side is PcLogOverlay.Side.Move, "a TM opens its move: ${o.shared.side}")
                shot("log-tms", o.render())
                com.ironmonone.pcui.PcPanel.Renderer(File(dir, "panel2"), com.ironmonone.pcui.PcPanel.Input(null, 0), 3f).use { panel ->
                    val input = com.ironmonone.pcui.PcPanel.Input(null, 3, log = o.shared)
                    panel.render(input); shot("panel-log-move-info", panel.render(input))
                    // The Pokemon card's History and Show resistances screens, each with its back arrow.
                    o.set(o.view.into(PcLogOverlay.View(pokemon = first)))
                    panel.render(input); panel.click(120, 68); panel.render(input)
                    assertTrue(o.shared.side is PcLogOverlay.Side.History, "History: ${o.shared.side}")
                    shot("panel-log-history", panel.render(input))
                    panel.click(135, 146); panel.render(input)
                    assertNull(o.shared.side)
                    val levels = first.moves.map { it.first }.filter { it > 1 }
                    val weakY = 75 + 28 - (if (levels.size <= (if (levels.size <= 16) 8 else 9)) 10 else 0)
                    panel.click(120, weakY + 4); panel.render(input)
                    assertTrue(o.shared.side is PcLogOverlay.Side.Defenses, "resistances: ${o.shared.side}")
                    shot("panel-log-resistances", panel.render(input))
                    panel.click(135, 146); panel.render(input)
                    // A route's info, a trainer's info.
                    o.shared.set(o.view, PcLogOverlay.Side.Route(route)); panel.render(input); shot("panel-log-route-info", panel.render(input))
                    o.shared.set(o.view, PcLogOverlay.Side.Trainer(t)); panel.render(input); shot("panel-log-trainer-info", panel.render(input))
                    o.set(o.view.back() ?: o.view)
                }
                // Misc.: the log's seed and settings.
                o.click(tab(4), 5); o.render(); shot("log-misc", o.render())
                assertEquals(PcLogOverlay.Tab.MISC, o.view.tab)
                // A typed search narrows the Pokemon tab.
                o.search("char")
                val rows = d.pokemonRows("char")
                assertTrue(rows.isNotEmpty() && rows.all { d.shownName(it).contains("char", ignoreCase = true) }, rows.map { d.shownName(it) }.toString())
                assertEquals("char", o.view.query)
                o.render()
                o.search("")
                assertEquals("", o.view.query)
                // The X at the top level closes it.
                o.click(233, 5); o.render()
                assertEquals(listOf(1), closed)
            }
        } finally { dir.deleteRecursively() }
    }
}
