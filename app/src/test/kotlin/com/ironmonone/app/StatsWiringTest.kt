package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where Your stats can be reached from and what it is drawn with (2026-09-30). The way there is AppNav's, as every
 * other move is, and is run for real here; the screens that draw it cannot run on the JVM, so the rest reads the
 * source, for exactly the lines a phone would otherwise be needed to see. What the numbers are is CareerStatsTest's.
 */
class StatsWiringTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    /** [text] with its comments dropped, so a note that names something is not counted as using it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    // ---------------------------------------------------------------- AppNav

    @Test
    fun `Your stats is a screen on Home, opened from Home's link or from More, and Back is Home`() {
        val n = AppNav().openStats()
        assertEquals(Tab.HOME, n.tab)
        assertTrue(n.stats)
        assertNull(n.mode)
        // From wherever the player is: the Home tab, with nothing else held open behind it.
        for (from in listOf(AppNav(tab = Tab.MORE, morePage = 1), AppNav().pick(Tab.LIBRARY), AppNav().play(), AppNav().open(HomeMode.KAIZO), AppNav().open(HomeMode.PLAY_ANY))) {
            val s = from.openStats()
            assertEquals(Tab.HOME, s.tab, "from $from")
            assertTrue(s.stats, "from $from")
            assertNull(s.mode, "from $from")
            assertEquals(Tab.HOME, s.anchor, "from $from")
        }
        // Back closes it, and is one step: the menu, and then the system's own Back on the menu.
        assertEquals(AppNav(), n.back())
        assertNull(assertNotNull(n.back()).back())
        // A tap on the bar, one of the four buttons, and the back arrow all leave it.
        for (t in Tab.entries) assertFalse(n.pick(t).stats, "the $t tab")
        for (m in HomeMode.entries) assertFalse(n.open(m).stats, "the $m button")
        assertFalse(n.home().stats)
        assertFalse(n.play().stats)
    }

    @Test
    fun `nothing else opens Your stats, and it changes nothing else in AppNav`() {
        assertFalse(AppNav().stats)
        assertFalse(AppNav.opening("HOME").stats)
        assertFalse(AppNav.opening("PLAY").stats)
        // The page a tab was on is kept while stats is open and after it.
        assertEquals(1, AppNav(morePage = 1).openStats().morePage)
        assertEquals(1, AppNav(libraryPage = 1).openStats().libraryPage)
    }

    // ----------------------------------------------------------- the screens

    @Test
    fun `Home has the link with the other two, and it goes through AppNav`() {
        val menu = code(read("HomeMenu.kt"))
        assertTrue("onStats: () -> Unit," in menu, "HomeScreen takes it")
        assertTrue("HomeLink(HomeCopy.STATS_LINK, HomeCopy.STATS_LINK_SPOKEN, onStats)" in menu)
        val links = menu.substringAfter("FlowRow(horizontalArrangement").substringBefore("\n                    }\n")
        assertEquals(3, Regex("HomeLink\\(").findAll(links).count(), "Library, More and Your stats sit in the one wrapping row: $links")
        assertTrue(links.indexOf("HomeCopy.MORE_LINK") < links.indexOf("HomeCopy.STATS_LINK"), "under the buttons, after the two that were there")
        val main = code(read("MainActivity.kt"))
        assertTrue("onStats = { nav = nav.openStats() }," in main, "the link opens it")
        assertEquals(0, Regex("\\bnav\\s*=\\s*AppNav\\(").findAll(main).count(), "no navigation is written outside AppNav")
    }

    @Test
    fun `the screen sits under the same top bar as the modes, with a back control to Home`() {
        val main = code(read("MainActivity.kt"))
        assertTrue(
            "null, HomeMode.PLAY_ANY -> if (nav.stats) ModeScreen(StatsCopy.TITLE, onBack = { nav = nav.home() }) {\n" +
                "                        CareerStatsScreen(Modifier.fillMaxSize())\n" +
                "                    } else HomeScreen(" in main,
            "the stats screen is the menu's other face, and Back from it is Home",
        )
        // It replaces the menu and never a mode screen: stats and a mode are never open together.
        assertEquals(1, Regex("nav\\.stats").findAll(main).count())
        assertEquals(1, Regex("CareerStatsScreen\\(").findAll(main).count())
    }

    @Test
    fun `More has a card for it on the Backup and info page`() {
        val main = code(read("MainActivity.kt"))
        assertTrue("AboutScreen(Modifier.fillMaxSize(), onStats = { nav = nav.openStats() })" in main)
        val about = code(read("AboutScreen.kt"))
        assertTrue("fun AboutScreen(modifier: Modifier = Modifier, onStats: () -> Unit = {}) {" in about)
        assertTrue("Text(HomeCopy.STATS_LINK," in about && "Text(HomeCopy.STATS_CARD_LINE," in about)
        assertTrue("Gen3Button(HomeCopy.STATS_OPEN, Modifier.fillMaxWidth()) { onStats() }" in about)
        // After the help card and before the crash card: help stays the first thing a newcomer reads.
        assertTrue(about.indexOf("How it works") < about.indexOf("HomeCopy.STATS_LINK") && about.indexOf("HomeCopy.STATS_LINK") < about.indexOf("crash?.let"))
    }

    @Test
    fun `the words are HomeCopy's and StatsCopy's, and the screens have none of their own`() {
        // HomeCopy holds the link and the card, so the menu's copy rules (HomeNavTest) cover them.
        for (s in listOf(HomeCopy.STATS_LINK, HomeCopy.STATS_LINK_SPOKEN, HomeCopy.STATS_CARD_LINE, HomeCopy.STATS_OPEN)) assertTrue(s in HomeCopy.all, s)
        // An empty string is not a word.
        fun literals(file: String) = Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code(read(file))).map { it.value }.filter { it != "\"\"" }.toList()
        assertEquals(emptyList(), literals("CareerStatsScreen.kt"), "CareerStatsScreen.kt says things StatsCopy does not")
        assertEquals(emptyList(), literals("HomeMenu.kt"), "HomeMenu.kt says things HomeCopy does not")
        for (name in listOf("CareerStats.kt", "CareerStatsScreen.kt")) {
            val text = read(name)
            assertFalse(text.contains(0x2014.toChar()), "$name has an em dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
    }

    @Test
    fun `the numbers are read off the main thread, after which the rows are drawn`() {
        val screen = code(read("CareerStatsScreen.kt"))
        assertTrue("LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { CareerStats.readOrEmpty(context.filesDir) } }" in screen)
        assertTrue("if (s != null) {" in screen, "nothing is drawn until they are in")
        assertTrue("if (s.isEmpty) {" in screen, "and a phone with nothing played says so")
        // Every number the page shows is a field of CareerStats, through StatsCopy.
        for (f in listOf("s.runsStarted", "s.wins", "s.playSeconds", "s.topCause", "s.longestWinStreak", "s.bests", "s.nuzlockeStarted", "s.nuzlockeFinished", "s.nuzlockeLost"))
            assertTrue(f in screen, "the page shows $f")
    }

    @Test
    fun `PlayScreen carries nothing for it`() {
        // PlayScreen is at ART's verifier limit (playscreen-verifier-limit).
        val play = read("PlayScreen.kt")
        assertFalse("CareerStats" in play || "openStats" in play || "StatsCopy" in play)
    }

    @Test
    fun `it is built only from what the app already keeps, and adds no file of its own`() {
        val stats = code(read("CareerStats.kt"))
        // Read from: run history, attempt counters, run clock, DS past runs, Nuzlocke ledgers. Nothing is written.
        for (reads in listOf("RunHistory(f)", "\"runhistory-\"", "File(prep, \"attempts\")", "RunClock.readSeconds(", "PastRunStore(f)", "NuzlockeStore(filesDir)"))
            assertTrue(reads in stats, "reads $reads")
        for (writes in listOf("writeText", "SafeWrite", "writeBytes", "appendText", "delete(", ".save("))
            assertFalse(writes in stats, "CareerStats.kt must not write: $writes")
        assertFalse(Regex("\"prep/[A-Za-z0-9_./-]+\"").containsMatchIn(stats), "no new file under prep/ for BackupCoverageTest to account for")
    }

    /**
     * rc32 audit P2 #15: a long Runs won value ("3, 1 after state loads, retries or restarts") took the row and left
     * "Runs won" a sliver or nothing at a large font. A value takes at most half the row, and the wins that went back
     * in time are a line of their own under it.
     */
    @Test
    fun `a value never takes the label's room, and the rewound wins are a line of their own`() {
        val row = code(read("CareerStatsScreen.kt")).substringAfter("private fun StatRow(").substringBefore("\n}\n")
        assertTrue("val half = maxWidth / 2" in row && "Text(value, Modifier.widthIn(max = half)" in row, "the value wraps inside its half")
        assertTrue("Text(label, Modifier.weight(1f)" in row, "the label takes what is left")
        assertTrue("note?.let {" in row, "the note under them")
        assertTrue("StatRow(StatsCopy.RUNS_WON, StatsCopy.wins(s.wins), StatsCopy.winsNote(s.winsAfterRewinds))" in read("CareerStatsScreen.kt"))
        assertTrue(StatsCopy.winsNote(1)!! in StatsCopy.all, "the copy rules read the note too")
    }
}
