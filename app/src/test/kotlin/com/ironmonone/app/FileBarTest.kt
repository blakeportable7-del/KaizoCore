package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The FILE bar (Blake, 2026-10-05/06): "completely removing the gear and the hamburger menu buttons off the tracker and
 * putting it on a file bar", "one file bar, semi transparent, the docked tracker, floating tracker, and hud tracker",
 * "an x close on the file bar right end", and "Every item that lives in the app today must still be reachable".
 * The menu map and the sheets' rules run for real; where the bar is drawn is held to the source.
 */
class FileBarTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun code(name: String) = read(name).replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    /** Every game and mode the sheets meet. */
    private val contexts: List<BarContext> = buildList {
        val reaches = listOf(TrackerReach(), TrackerReach(true, true, true, true, true, true, true, true, true, true, true, true, true))
        for (tracked in listOf(true, false)) for (isRun in listOf(true, false)) for (ds in listOf(true, false)) for (gb in listOf(true, false))
            for (landscape in listOf(true, false)) for (view in BarView.entries) for (reach in reaches) for (ironmon in listOf(true, false)) {
                if (ds && gb) continue
                add(BarContext(
                    tracked = tracked, isRun = isRun, ds = ds, gameBoy = gb, landscape = landscape, fullscreen = landscape,
                    controller = isRun, view = view, rewindAllowed = !isRun, cheatsAllowed = !isRun, routeLog = !ds && !gb,
                    inject = ds && !isRun, streamSettings = isRun, ironmon = ironmon && isRun, plain = !isRun, gen3 = !ds && !gb, reach = reach,
                ))
            }
    }

    @Test
    fun `the bar's groups, in order, each with its line`() {
        assertEquals(listOf("FILE", "NEW", "TRACKER", "VIEW", "TOOLS", "SETTINGS", "HOME"), BarGroup.entries.map { it.label })
        val run = BarContext(isRun = true, ironmon = true, plain = false, gen3 = true)
        assertEquals(BarGroup.entries, FileBarMap.groups(run), "a Kaizo IronMON run has all seven")
        val library = BarContext(isRun = false)
        assertFalse(BarGroup.NEW in FileBarMap.groups(library), "NEW only where there is a run to end")
        assertFalse(BarGroup.TRACKER in FileBarMap.groups(BarContext(tracked = false)), "TRACKER only where there is a tracker")
        for (c in contexts) for (g in FileBarMap.groups(c)) assertTrue(FileBarMap.items(g, c).isNotEmpty(), "$g has lines for $c")
        for (g in BarGroup.entries) assertTrue(g.line.isNotBlank())
    }

    @Test
    fun `everything the old menus held is on the bar, and every line is reachable somewhere`() {
        // Each entry of the File band, its MORE menu, the gear, the three-line menu, the window's bar and the tabs.
        val gestures = setOf(FileBarMap.THE_BAR, FileBarMap.TAP_TOP, FileBarMap.HOLD, FileBarMap.OWN_ROW)
        for (l in FileBarMap.LEGACY) assertTrue(l.now != null || l.how in gestures, "${l.was} (${l.where}) has nowhere to go")
        val mapped = FileBarMap.LEGACY.mapNotNull { it.now }.toSet()
        // Tracker Setup's screens, the File menu's tools and the tabs all mapped.
        for (i in listOf(BarItem.SETUP, BarItem.RULES, BarItem.NOTEBOOK, BarItem.COVERAGE, BarItem.TRAINERS, BarItem.HEALS, BarItem.BATTLE,
            BarItem.CATCH, BarItem.EVO, BarItem.STATS, BarItem.TIME, BarItem.TRACKED, BarItem.FAVORITES, BarItem.PAST, BarItem.STATISTICS,
            BarItem.TOURNEY, BarItem.GACHA, BarItem.THEME, BarItem.QUOTES, BarItem.CLEAR, BarItem.SAVE, BarItem.LOAD, BarItem.STATES,
            BarItem.SPEED, BarItem.SOUND, BarItem.RESTART, BarItem.REWIND, BarItem.NEW_RUN, BarItem.SCREENS, BarItem.CAM, BarItem.STREAM,
            BarItem.CLEAN, BarItem.LAYOUT, BarItem.EMULATOR, BarItem.ACHIEVEMENTS, BarItem.CHEATS, BarItem.PAD, BarItem.ROUTE_LOG,
            BarItem.INJECT, BarItem.LEAVE, BarItem.VIEWS)) assertTrue(i in mapped, "$i stands for an old entry")
        // Every line shows for some game, so none is dead.
        for (i in BarItem.entries) assertTrue(contexts.any { FileBarMap.shows(i, it) && i in FileBarMap.items(i.group, it) }, "$i never shows")
        // And every line is drawn: the sheet's when has a branch for each.
        val bar = code("FileBar.kt")
        for (i in BarItem.entries) assertTrue("BarItem.${i.name} ->" in bar, "FileBar.kt draws $i")
    }

    @Test
    fun `the TRACKER sheet offers Tracker Setup's screens under Tracker Setup's own conditions`() {
        val all = TrackerReach(true, true, true, true, true, true, true, true, true, true, true, true, true)
        val gen3Run = BarContext(isRun = true, ironmon = true, plain = false, gen3 = true, reach = all)
        assertEquals(BarItem.SETUP, FileBarMap.items(BarGroup.TRACKER, gen3Run).first(), "Tracker Setup leads")
        assertEquals(BarItem.entries.filter { it.group == BarGroup.TRACKER }, FileBarMap.items(BarGroup.TRACKER, gen3Run), "a Gen 3 run with every screen")
        val gb = BarContext(gameBoy = true, isRun = true, ironmon = true, plain = false, reach = all)
        val gbItems = FileBarMap.items(BarGroup.TRACKER, gb)
        assertFalse(BarItem.COVERAGE in gbItems || BarItem.STATS in gbItems || BarItem.GACHA in gbItems, "the gear offers none of these on a Game Boy game")
        val library = BarContext(isRun = false, plain = true, gen3 = true, reach = all)
        val libItems = FileBarMap.items(BarGroup.TRACKER, library)
        for (i in listOf(BarItem.RULES, BarItem.FAVORITES, BarItem.PAST, BarItem.STATISTICS, BarItem.TOURNEY, BarItem.QUOTES))
            assertFalse(i in libItems, "$i is a run's, not a library game's")
        val none = FileBarMap.items(BarGroup.TRACKER, BarContext(isRun = true, ironmon = true, plain = false, gen3 = true))
        for (i in listOf(BarItem.NOTEBOOK, BarItem.TRAINERS, BarItem.BATTLE, BarItem.CATCH, BarItem.HEALS, BarItem.EVO, BarItem.TRACKED, BarItem.TIME))
            assertFalse(i in none, "$i shows only where its screen exists")
        // The same conditions as the gear dialog itself.
        val gear = code("TrackerGearDialog.kt")
        for (cond in listOf("if (!scope.plain) GearButton(\"RULES FOR THIS RUN\")", "if (FavoritesInPlay.offered(scope)) EditFavoritesRow()",
            "if (!gameBoy) GearButton(\"COVERAGE CALC\")", "if (scope.ironmon) onPastRuns?.let", "if (scope.gen3) GearButton(\"GachaMon Collection\"",
            "if (scope.ironmon) GearButton(DeathQuotesCopy.TITLE)")) assertTrue(cond in gear, cond)
    }

    @Test
    fun `VIEW offers the four views, the HUD only while it is on, and the window and HUD lines only for them`() {
        assertTrue(TrackerHud.ENABLED, "the HUD is one of the bar's views (Blake, 2026-10-06)")
        assertEquals(listOf("Docked", "Floating", "HUD", "Hidden"), FileBarMap.views(BarContext()).map { it.label })
        assertEquals(listOf("Docked", "Floating", "Hidden"), FileBarMap.views(BarContext(hudEnabled = false)).map { it.label })
        assertTrue(BarItem.SEE_THROUGH in FileBarMap.items(BarGroup.VIEW, BarContext(view = BarView.FLOATING)))
        assertTrue(BarItem.HUD_MINE in FileBarMap.items(BarGroup.VIEW, BarContext(view = BarView.HUD)))
        assertFalse(BarItem.SEE_THROUGH in FileBarMap.items(BarGroup.VIEW, BarContext(view = BarView.DOCKED)))
        assertFalse(BarItem.SCREENS in FileBarMap.items(BarGroup.VIEW, BarContext(ds = false)))
        assertTrue(BarItem.SCREENS in FileBarMap.items(BarGroup.VIEW, BarContext(ds = true)))
        assertFalse(BarItem.PAD in FileBarMap.items(BarGroup.VIEW, BarContext(controller = false)), "the pad switch only with a controller")
        assertFalse(BarItem.LEAVE in FileBarMap.items(BarGroup.HOME, BarContext(landscape = false, fullscreen = false)), "portrait has its tab bar")
        // The views write the same two settings Tracker Setup's Landscape tracker does, and read back.
        try {
            for (v in BarView.entries) { chooseBarView(v); assertEquals(v, currentBarView()) }
        } finally {
            TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED; TrackerOptions.trackerHud = false
        }
    }

    @Test
    fun `rewind and cheats stay on the sheet when off, and say why`() {
        assertNotNull(FileBarMap.offWhy(BarItem.REWIND, BarContext(rewindAllowed = false)))
        assertNotNull(FileBarMap.offWhy(BarItem.REWIND, BarContext(rewindAllowed = true, raHardcore = true)))
        assertNull(FileBarMap.offWhy(BarItem.REWIND, BarContext(rewindAllowed = true)))
        assertNotNull(FileBarMap.offWhy(BarItem.CHEATS, BarContext(cheatsAllowed = false)))
        assertTrue(BarItem.REWIND in FileBarMap.items(BarGroup.FILE, BarContext(rewindAllowed = false)))
    }

    @Test
    fun `the words are plain, no em dash, no AI, every label short enough for its button`() {
        val words = BarGroup.entries.flatMap { listOf(it.label, it.line) } + BarItem.entries.map { it.label } + BarView.entries.map { it.label } +
            listOf(FileBarCopy.OPEN_HINT, FileBarCopy.CLOSE_HINT, FileBarCopy.PORTRAIT_VIEWS, FloatLockRules.TIP, FloatLockRules.LOCKED,
                FloatLockRules.UNLOCKED, FloatLockRules.LOCKED_IDLE, FloatLockRules.LOCKED_FOR_BAR) +
            FileBarMap.LEGACY.map { it.how }
        for (w in words) {
            assertFalse(Char(0x2014) in w || Char(0x2013) in w, "no dash: $w")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(w), "never AI: $w")
        }
        for (i in BarItem.entries) assertTrue(i.label.length <= 22, "${i.label} fits a half-width button")
        for (s in listOf(read("FileBar.kt"), read("FileBarModel.kt"), read("FloatHold.kt"), read("TapZone.kt"))) assertFalse(Char(0x2014) in s)
    }

    @Test
    fun `one see-through bar over every view, with the X fixed at its right end and 44 dp targets`() {
        val bar = code("FileBar.kt")
        assertTrue("private val BAR_FILL = Color.Black.copy(alpha = 0.6f)" in bar, "semi transparent")
        assertEquals(52, FileBar.BAR_DP)
        val row = bar.substringAfter("private fun Bar(").substringBefore("\n}\n")
        assertTrue(row.indexOf("Modifier.weight(1f).horizontalScroll(scroll)") < row.indexOf("FileBarCopy.CLOSE"), "the X after the scrolling chips, outside them")
        assertTrue("if (scroll.canScrollBackward) Arrow(" in row && "if (scroll.canScrollForward) Arrow(" in row, "arrows while it scrolls")
        assertTrue("Modifier.size(FileBar.BAR_DP.dp).clickable" in row, "the X is a full target")
        // Every target at least 44 dp.
        assertTrue("modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)" in bar, "the chips")
        assertEquals(4, Regex("""heightIn\(min = Shell\.touchTarget\)""").findAll(bar.substringAfter("private fun Btn(")).count(), "buttons, switches, pickers, hold")
        // Drawn once, over the game, the pad, the dock, the window and the HUD: after them in Play's root box.
        val play = code("PlayScreen.kt")
        val root = play.substringAfter("    Box(modifier.fillMaxSize()) {\n    Row(Modifier.fillMaxSize()) {")
        assertTrue(root.indexOf("FloatingTracker(") in 0 until root.indexOf("    fileBar()"), "the bar after the window and the HUD")
        assertTrue(root.indexOf("    fileBar()") < root.indexOf("StatusToast("), "the toast still above it")
        assertEquals(1, Regex("FileBarHost\\(").findAll(play).count())
        // The views make room for it: the dock pads, the window steps down and locks, the HUD trims.
        assertTrue("padding(top = if (FileBar.open) (FileBar.BAR_DP + 4).dp else 0.dp)" in code("TrackerEdge.kt"))
        assertTrue("if (FileBar.open) HudLayout.Plan(placed.mine?.underBar(barTop), placed.rest?.underBar(barTop)) else placed" in code("TrackerHud.kt"))
        // Portrait's FILE opens the same bar.
        assertTrue("\"FILE\", accent = FileBar.open, onClick = { FileBar.toggle() })" in play)
    }

    @Test
    fun `the gear, the three-line menu and the File band are gone from the tracker and the play screen`() {
        val play = code("PlayScreen.kt")
        for (gone in listOf("TrackerCornerMenu(", "LandscapeMenuBand(", "ScreenTapMenu(", "val FileMenu", "OverlayChip(", "LandscapeMenuChip(",
            "onGear = { gearDialog = true }", "headerTrailing = corner", "menuOpen", "ui.moreOpen", "ui.speedPicker"))
            assertFalse(gone in play, "$gone is still in Play")
        val chrome = code("LandscapeChrome.kt")
        assertFalse("DropdownMenu" in chrome || "MenuLinesButton" in chrome)
        assertFalse("HudMenuItem" in code("TrackerHud.kt") || "drawCircle(Color.Black.copy(alpha = 0.55f))" in code("TrackerHud.kt"), "the HUD's menu disc is gone")
        // The overworld row stays without the gear: it shows whenever no battle banner does.
        for (f in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) assertTrue("if (!bannerShows) run {" in code(f), f)
        // Tracker Setup keeps every option and is opened from the bar.
        assertTrue("onSetup = { gearDialog = true }" in play)
        // Read raw: PlayScreen's "*/*" file filter reads as a comment opener to the comment stripper.
        assertTrue("if (gearDialog) TrackerGearDialog(trackerLinks(), onDismiss = { gearDialog = false })" in read("PlayScreen.kt"))
    }

    @Test
    fun `the stored settings carry over, nothing new is written, and the views read the old keys`() {
        val opts = read("TrackerOptions.kt")
        for (key in listOf("landscapeTracker=\${landscapeTracker.name}", "floatingLocked=\$floatingLocked", "floatingSolid=\$floatingSolid",
            "trackerHud=\$trackerHud", "hudShowMine=\$hudShowMine", "hudShowRest=\$hudShowRest")) assertTrue(key in opts, key)
        // A file from rc36.1 with the HUD chosen loads as the HUD now that it is on.
        val dir = java.nio.file.Files.createTempDirectory("filebar").toFile()
        try {
            val f = File(dir, "tracker-options.txt")
            f.writeText("landscapeTracker=FLOATING" + Char(10) + "trackerHud=true" + Char(10) + "floatingSolid=70" + Char(10))
            TrackerOptions.load(f)
            assertEquals(BarView.HUD, currentBarView())
            assertEquals(70, TrackerOptions.floatingSolid)
        } finally {
            dir.deleteRecursively()
            TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED; TrackerOptions.trackerHud = false; TrackerOptions.floatingSolid = FloatingSeeThrough.SOLID
        }
    }
}
