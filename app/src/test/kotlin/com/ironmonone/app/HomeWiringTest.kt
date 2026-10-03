package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The main menu and the welcome as they are wired into App() and drawn (2026-09-29). A Compose screen cannot
 * run on the JVM, so this reads the source, for exactly the lines a phone would otherwise be needed to see.
 * What the buttons lead to, where Back goes, when the welcome shows and what the card says are run for real in
 * HomeNavTest and HomeStartTest.
 */
class HomeWiringTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")
    private val main = read("MainActivity.kt")
    private val menu = read("HomeMenu.kt")

    private fun count(text: String, needle: String) = Regex(Regex.escape(needle)).findAll(text).count()

    /** [text] with its comments dropped, so a note that names a function is not counted as a call to it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    /** The branches of App()'s `when (modeNow)`, each from its head to the next. */
    private fun homeBranches(): Map<String, String> {
        // Comments dropped first: a note above one branch would otherwise be read as part of the branch before it.
        val block = code(main.substringAfter("Tab.HOME -> when (modeNow) {").substringBefore("Tab.PLAY -> PlayScreen("))
        val heads = Regex("(?m)^\\s*(null, HomeMode\\.PLAY_ANY|HomeMode\\.[A-Z_]+) ->").findAll(block).toList()
        return heads.mapIndexed { i, m -> m.groupValues[1] to block.substring(m.range.last, heads.getOrNull(i + 1)?.range?.first ?: block.length) }.toMap()
    }

    /** A function's text, from its `fun` line to its closing brace: at column 0 for a top-level one, [close] for a local one. */
    private fun body(text: String, signature: String, close: String = "\n}\n"): String =
        signature + text.substringAfter(signature).substringBefore(close)

    @Test
    fun `the menu has the welcome title on top, above the Continue card and the buttons`() {
        val screen = code(body(menu, "internal fun HomeScreen("))
        val title = screen.indexOf("HomeCopy.WELCOME_TITLE")
        assertTrue(title >= 0, "the title is drawn on Home")
        assertTrue(title < screen.indexOf("ContinueBlock(") && title < screen.indexOf("HomeMode.entries.forEach"), "and it comes first")
        assertTrue("semantics { heading() }" in screen.substring(title, screen.indexOf("ContinueBlock(")), "as a heading")
    }

    // ---------------------------------------------------------------- App()

    @Test
    fun `the app opens where PrepStore says, on the state AppNav makes from it`() {
        assertTrue("val start = remember { runCatching { PrepStore(appContext).startingPoint(Demo.mode) }.getOrDefault(\"HOME\") }" in main)
        assertTrue(") { mutableStateOf(AppNav.opening(start)) }\n    val tab = nav.tab" in main)
        assertTrue("restore = { AppNav.restore(it, start) }" in main, "a saved place comes back, and a game to reopen wins over it (rc32 audit P2 #32)")
        // No tab is set by hand any more: every move goes through AppNav.
        assertEquals(0, Regex("\\btab\\s*=\\s*Tab\\.").findAll(main).count(), "a tab assigned outside AppNav")
        assertFalse(Regex("\\b(libraryPage|morePage)\\b\\s*=").containsMatchIn(main.replace("nav.libraryPage", "").replace("nav.morePage", "")), "a page kept outside AppNav")
    }

    @Test
    fun `the four buttons lead where AppNav says, and each mode screen is the right one`() {
        val branches = homeBranches()
        assertEquals(setOf("null, HomeMode.PLAY_ANY", "HomeMode.KAIZO", "HomeMode.NUZLOCKE", "HomeMode.HACKS"), branches.keys)
        val home = branches.getValue("null, HomeMode.PLAY_ANY")
        assertTrue("HomeScreen(" in home && "onMode = { nav = nav.open(it) }" in home, "every button goes through AppNav.open")
        assertTrue("onContinue = { nav = nav.play() }" in home)
        assertTrue("onLibrary = { nav = nav.pick(Tab.LIBRARY) }" in home && "onMore = { nav = nav.pick(Tab.MORE) }" in home)
        // Each of the three screens under a top bar whose back control goes Home.
        for (mode in listOf("KAIZO", "NUZLOCKE", "HACKS")) {
            assertTrue("ModeScreen(HomeMode.$mode.title, onBack = { nav = nav.home() })" in branches.getValue("HomeMode.$mode"), "$mode has the back control to Home")
        }
        // The two screens that already existed are the ones that open, each only from its own button.
        for ((mode, screen) in listOf("KAIZO" to "RunScreen(", "HACKS" to "HacksScreen(")) {
            val b = branches.getValue("HomeMode.$mode")
            assertTrue(screen in b, "$mode opens $screen")
            for (other in listOf("RunScreen(", "HacksScreen(") - screen) assertFalse(other in b, "$mode does not open $other")
        }
        for (existing in listOf("RunScreen(", "HacksScreen(")) assertFalse(existing in branches.getValue("HomeMode.NUZLOCKE"), "Nuzlocke does not open $existing")
        // The Run screen keeps its editor: the preset editor takes the screen, and closing it comes back.
        assertTrue("RunScreen(Modifier.fillMaxSize(), onEdit = { f, g -> editing = f to g }, onPlay = { nav = nav.play() }," in branches.getValue("HomeMode.KAIZO"))
        // The empty game list's button opens Library on its games page (2026-09-30, UX audit P0-13).
        assertTrue("onAddGame = { nav = nav.openMyGames() })" in branches.getValue("HomeMode.KAIZO"))
    }

    /** The Nuzlocke button opens the real screen (2026-09-29), and the placeholder that stood in for it is gone. */
    @Test
    fun `Nuzlocke opens the Nuzlocke screen, and the placeholder is gone`() {
        assertTrue("NuzlockeScreen(Modifier.fillMaxSize(), onPlay = { nav = nav.play() }, onAddGame = { nav = nav.openMyGames() })" in homeBranches().getValue("HomeMode.NUZLOCKE"),
            "under the back control to Home, and a started run goes to Play")
        assertEquals(1, count(code(main), "HomeMode.NUZLOCKE ->"))
        val everywhere = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.sumOf { count(it.readText(), "NuzlockePlaceholder") + count(it.readText(), "NUZLOCKE_SOON") }
        assertEquals(0, everywhere, "no placeholder, no coming-soon line")
    }

    /** The ledger is fed from Play's polling, so a hidden tracker (landscape, clean view) does not leave gaps in it. */
    @Test
    fun `the Nuzlocke ledger is fed on every tracker poll`() {
        val play = read("PlayScreen.kt")
        val poll = play.substringAfter("trackerState = fresh ?: trackerState").substringBefore("\n    }\n")
        assertTrue("NuzlockeTracking.observe(context.applicationContext.filesDir, trackerState)" in poll, "after the poll and the staged demo")
    }

    @Test
    fun `Run and Hacks are drawn once each, from Home`() {
        for ((screen, file) in listOf("RunScreen(" to "RunScreen.kt", "HacksScreen(" to "HacksScreen.kt")) {
            val calls = src.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != file }.sumOf { count(code(it.readText()), screen) }
            assertEquals(1, calls, "$screen is called once, in App()")
        }
    }

    @Test
    fun `everything that used to jump to the Play tab still lands on Play`() {
        // Run's onPlay (a new run, and Back to attempt N), Library's PLAY, Hacks' PLAY, Nuzlocke's started run, and Continue.
        assertEquals(4, count(main, "onPlay = { nav = nav.play() }"))
        assertEquals(1, count(main, "onContinue = { nav = nav.play() }"))
        // Play sits inside the provider of its empty screen's navigation (PlayNothing, 2026-09-30).
        // A run being made holds Play in its wait first (rc33 audit P0-5, PlayWaitsForNewRunTest).
        assertTrue("Tab.PLAY -> if (RunJob.installing) PlayRunBeingMade(Modifier.fillMaxSize()) else androidx.compose.runtime.CompositionLocalProvider(" in main && "{ PlayScreen(" in main)
        // The Library's page is AppNav's, so Play any game and Add your games can set it.
        // Library opens on My games, with Patched versions second (2026-09-30, UX audit P0-10); the page that makes a patched game can send the player to My games.
        assertTrue("TabPages(listOf(\"My games\", \"Patched versions\"), nav.libraryPage, { nav = nav.withLibraryPage(it) })" in main)
        assertTrue("if (nav.libraryPage == AppNav.MY_GAMES_PAGE) RomLibraryScreen(Modifier.fillMaxSize(), onPlay = { nav = nav.play() })" in main)
        assertTrue("else PrepareScreen(Modifier.fillMaxSize(), onMyGames = { nav = nav.withLibraryPage(AppNav.MY_GAMES_PAGE) })" in main)
        assertTrue("TabPages(listOf(\"Controls\", \"Backup and info\"), nav.morePage, { nav = nav.withMorePage(it) })" in main)
    }

    @Test
    fun `the bar draws the tab list and each tap goes through AppNav`() {
        assertTrue("Tab.entries.forEach { t ->" in main)
        assertTrue("{ nav = nav.pick(t); showChrome = false }" in main, "a tap also brings the chrome back, as it did")
        assertTrue("t.icon()" in main)
        // The tab list has no second copy to drift from it.
        assertEquals(0, count(main, "listOf(Tab."))
    }

    @Test
    fun `Back, fullscreen, clean view and the top bar are as they were before the menu`() {
        assertEquals(1, count(main, "androidx.activity.compose.BackHandler(enabled = clean) { clean = false }"))
        assertEquals(1, count(main, "androidx.activity.compose.BackHandler(enabled = fullscreen && !clean) { showChrome = true }"))
        assertTrue("val fullscreen = editing == null && (clean || (landscape && tab == Tab.PLAY && !showChrome))" in main)
        assertTrue("LaunchedEffect(tab) { if (tab != Tab.PLAY) clean = false }" in main)
        assertTrue("LaunchedEffect(landscape) { showChrome = false }" in main)
        assertTrue("val barActions = AppBarActions.content" in main)
        assertTrue("if (!fullscreen && (barActions != null || editing != null)) {" in main)
        // The Library and More Back, the mode screens' Back: AppNav's, and never over an open editor, whose own handler wins.
        assertEquals(1, count(main, "androidx.activity.compose.BackHandler(enabled = editing == null && nav.back() != null) { nav.back()?.let { nav = it } }"))
        // The order matters: a handler registered later wins, so the ones for clean view and fullscreen come before the general one.
        val order = listOf("BackHandler(enabled = clean)", "BackHandler(enabled = fullscreen && !clean)", "BackHandler(enabled = editing == null && nav.back() != null)", "BackHandler(enabled = welcome)")
            .map { main.indexOf(it) }
        assertEquals(order.sorted(), order)
        assertTrue(order.all { it > 0 })
    }

    // ---------------------------------------------------------------- the welcome

    @Test
    fun `the welcome is answered with either button or Back, once, off the main thread`() {
        assertEquals(1, count(main, "Welcome.markSeen("), "the flag is written in one place")
        val close = body(main, "fun closeWelcome(addGames: Boolean) {", close = "\n    }\n")
        assertTrue("scope.launch(Dispatchers.IO) { Welcome.markSeen(appContext.filesDir) }" in close, "SafeWrite syncs to disk")
        assertTrue("welcome = false" in close && "if (addGames) nav = nav.addGames()" in close)
        assertTrue("WelcomeScreen(onAddGames = { closeWelcome(addGames = true) }, onLookAround = { closeWelcome(addGames = false) })" in main)
        assertTrue("androidx.activity.compose.BackHandler(enabled = welcome) { closeWelcome(addGames = false) }" in main, "Back is Look around")
        assertTrue("var welcome by remember { mutableStateOf(runCatching { Welcome.showAtLaunch(appContext.filesDir, start, Demo.mode) }.getOrDefault(false)) }" in main)
    }

    @Test
    fun `the welcome takes the whole screen and gives it back`() {
        val at = main.indexOf("if (welcome) {")
        assertTrue(at > 0)
        val block = main.substring(at, main.indexOf("Column(", at))
        assertTrue("WelcomeScreen(" in block && "return" in block, "it replaces the screen, and the rest of App() waits")
        // It is drawn after the crash dialog and the update prompt, so neither is ever hidden by it.
        assertTrue(main.indexOf("UpdatePrompt(show = tab != Tab.PLAY && crashChecked && launchCrash == null)") in 1 until at)
        assertTrue(main.indexOf("CrashReportLaunch(text") in 1 until at)
    }

    @Test
    fun `the welcome offers exactly two buttons, as the shell's buttons, and scrolls`() {
        val w = body(menu, "internal fun WelcomeScreen(onAddGames: () -> Unit, onLookAround: () -> Unit) {")
        assertEquals(2, count(w, "Gen3Button("))
        assertTrue("Gen3Button(HomeCopy.ADD_GAMES, Modifier.fillMaxWidth(), accent = true) { onAddGames() }" in w)
        assertTrue("Gen3Button(HomeCopy.LOOK_AROUND, Modifier.fillMaxWidth()) { onLookAround() }" in w)
        assertTrue("verticalScroll(rememberScrollState())" in w, "at a large font both buttons stay reachable")
        assertTrue("statusBarsPadding()" in w && "navigationBarsPadding()" in w, "it replaces the whole screen, so it pads its own bars")
        assertEquals(1, count(w, "HomeCopy.WELCOME_NO_GAMES"), "it says there are no games")
        // Blake, 2026-09-30: say it is a beta, ask for bugs and changes, and offer the crash report switch, first launch only.
        assertEquals(1, count(w, "HomeCopy.BETA_ASK"))
        assertEquals(1, count(w, "CrashReportsCard()"), "the same switch as More's, so the two never disagree")
        assertTrue("Report a bug" in HomeCopy.BETA_ASK && "beta" in HomeCopy.BETA_TITLE)
    }

    // ---------------------------------------------------------------- the menu, drawn

    @Test
    fun `the mode buttons are real buttons, tall enough, and never cut a word`() {
        val b = body(menu, "private fun ModeButton(mode: HomeMode, onClick: () -> Unit) {")
        assertTrue("role = Role.Button" in b, "a button to a screen reader")
        assertTrue("contentDescription = mode.spoken" in b, "with a description")
        assertTrue("heightIn(min = MODE_BUTTON_MIN_HEIGHT)" in b, "it grows with the font, it is not a fixed height")
        val min = Regex("MODE_BUTTON_MIN_HEIGHT = (\\d+)\\.dp").find(menu)!!.groupValues[1].toInt()
        assertTrue(min >= 48, "at least the 48dp touch target: $min")
        for (cut in listOf("maxLines", "softWrap", "TextOverflow", "Ellipsis")) assertFalse(cut in b, "the button text is cut by $cut")
        // A spacer may have a height; the button may not.
        assertFalse(Regex("(?<!Spacer\\(Modifier)\\.height\\(").containsMatchIn(b), "a fixed height would cut the text at a large font")
        assertTrue("mode.title" in b && "mode.line" in b, "the mode's own words")
        assertTrue("Gen3Box(" in b, "a card of the shell, not a new kind of container")
    }

    @Test
    fun `the modes stack in one column and the links wrap`() {
        val home = body(menu, "internal fun HomeScreen(")
        assertTrue("HomeMode.entries.forEach { m -> ModeButton(m) { onMode(m) } }" in home, "all four, from the enum, none by hand")
        val column = home.substringBefore("HomeMode.entries.forEach").substringAfterLast("Column(")
        assertTrue(column.startsWith("verticalArrangement = Arrangement.spacedBy("), "the buttons sit in a Column: $column")
        assertEquals(1, count(home, "FlowRow("), "the two links wrap instead of running off a narrow screen")
        assertFalse(Regex("(?<![A-Za-z])Row[(]").containsMatchIn(home), "no Row that could clip a button at a large font")
        assertTrue("HomeLink(HomeCopy.LIBRARY_LINK, HomeCopy.LIBRARY_LINK_SPOKEN, onLibrary)" in home)
        assertTrue("HomeLink(HomeCopy.MORE_LINK, HomeCopy.MORE_LINK_SPOKEN, onMore)" in home)
        val link = body(menu, "private fun HomeLink(")
        assertTrue("heightIn(min = Shell.touchTarget)" in link && "role = Role.Button" in link && "contentDescription = spoken" in link)
    }

    @Test
    fun `the Continue card is the shell's card and accent button, and is read off the main thread`() {
        val home = body(menu, "internal fun HomeScreen(")
        assertTrue("resume = withContext(Dispatchers.IO) { runCatching { ContinueCard.read(store, NuzlockeStore(context.applicationContext.filesDir)) }.getOrNull() }" in home,
            "off the main thread, and told about Nuzlocke runs so the card can say one is going")
        assertTrue("resume?.let {" in home && "ContinueBlock(it, onContinue)" in home, "shown only when there is something to continue")
        val c = body(menu, "private fun ContinueBlock(info: ContinueInfo, onContinue: () -> Unit) {")
        assertTrue("Gen3Box(" in c && "accent = true" in c && "onClick = onContinue" in c)
        assertTrue("contentDescription = HomeCopy.continueSpoken(info.game)" in c, "the button says which game")
        // Nothing is drawn until the card has answered, so the four buttons never move under a finger.
        assertTrue("if (loaded) {" in home)
    }

    @Test
    fun `a mode screen has a top bar with a back control to Home`() {
        val m = body(menu, "internal fun ModeScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {")
        assertTrue("clickable(role = Role.Button, onClickLabel = HomeCopy.BACK_HOME, onClick = onBack)" in m)
        assertTrue("contentDescription = HomeCopy.BACK_HOME" in m)
        assertTrue("heightIn(min = Shell.touchTarget)" in m && "widthIn(min = Shell.touchTarget)" in m, "a 48dp control")
        assertTrue("Box(Modifier.weight(1f)) { content() }" in m, "the screen keeps the rest of the height")
    }

    @Test
    fun `the menu is built from the shell's own parts and tokens`() {
        for (part in listOf("Gen3Box(", "Gen3Button(", "Gen3Header(", "Shell.night", "Shell.paper", "Shell.inkOnPaper", "Shell.hintOnPaper", "Shell.touchTarget"))
            assertTrue(part in menu, "HomeMenu.kt uses $part")
        // The welcome's CrashReportsCard() is the app's own Gen3Box card (CrashReportUi.kt), not Material's Card(.
        val own = menu.replace("CrashReportsCard(", "")
        for (foreign in listOf("Color(0x", "androidx.compose.material3.Button(", "OutlinedButton(", "Card(", "AlertDialog(", "Snackbar"))
            assertFalse(foreign in own, "HomeMenu.kt brings in its own $foreign")
    }
}
