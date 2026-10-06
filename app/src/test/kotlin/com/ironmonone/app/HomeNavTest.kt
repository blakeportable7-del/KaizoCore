package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The main menu's logic (2026-09-29): the four tabs, where each of the four buttons leads, where Back goes
 * and what the menu says. All of it is plain data in HomeNav.kt so it runs here; the screens that draw it
 * are checked in HomeWiringTest.
 */
class HomeNavTest {

    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    @Test
    fun `the tab list is Home, Play, Library, More`() {
        assertEquals(listOf("Home", "Play", "Library", "More"), Tab.entries.map { it.label })
        assertEquals(listOf("HOME", "PLAY", "LIBRARY", "MORE"), Tab.entries.map { it.name })
        // Run and Hacks are screens reached from Home now, not tabs.
        assertNull(Tab.entries.firstOrNull { it.name == "RUN" || it.name == "HACKS" })
    }

    @Test
    fun `the four buttons are the four modes, in order, with the words Blake was given`() {
        assertEquals(
            listOf(
                "Play any game" to "Pick a game from your library and play it. No rules, no randomizer.",
                "Kaizo IronMON" to "A randomized game with the IronMON tracker. A loss means a new game.",
                "Nuzlocke" to "Catch the first Pok\u00e9mon in each area. One that faints is gone.",
                "ROM Hacks" to "Patch a fan-made hack onto a game you own, then play it.",
                "Pok\u00e9mon Heart & Soul" to "Turn your Emerald into Heart & Soul, then play it as it is, in Kaizo IronMON or as a Nuzlocke.",
            ),
            HomeMode.entries.map { it.title to it.line },
        )
    }

    @Test
    fun `every word the menu shows follows the copy rules`() {
        val em = 0x2014.toChar()
        assertTrue(HomeCopy.all.size >= HomeMode.entries.size * 3, "the list of what the menu says is not empty: ${HomeCopy.all.size}")
        for (s in HomeCopy.all) {
            assertTrue(s.isNotBlank() && s == s.trim(), "\"$s\" is plain text with no stray space")
            assertFalse(em in s, "\"$s\" has an em dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s) || Regex("(?i)artificial intelligence|machine learning|\\bGPT\\b|\\bLLM\\b").containsMatchIn(s), "\"$s\" says how the work is made")
            assertFalse('!' in s, "\"$s\" shouts: the voice is dry")
        }
    }

    @Test
    fun `the two files carry no em dash and never say how the work is made`() {
        for (name in listOf("HomeNav.kt", "HomeMenu.kt")) {
            val text = read(name)
            assertFalse(text.contains(0x2014.toChar()), "$name has an em dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
    }

    @Test
    fun `the screens have no wording of their own, so the copy rules above cover everything the player reads`() {
        // Comments dropped, then any string literal left in HomeMenu.kt would be a line HomeCopy does not hold.
        val code = read("HomeMenu.kt").replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")
        val literals = Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code).map { it.value }.toList()
        assertTrue(literals.isEmpty(), "HomeMenu.kt says things HomeCopy does not: $literals")
    }

    @Test
    fun `Play any game opens Library on My games`() {
        val n = AppNav().open(HomeMode.PLAY_ANY)
        assertEquals(Tab.LIBRARY, n.tab)
        assertEquals(AppNav.MY_GAMES_PAGE, n.libraryPage)
        assertNull(n.mode, "nothing is held open on Home")
        // From wherever the player was, including Library already open on the other page.
        assertEquals(AppNav.MY_GAMES_PAGE, AppNav(tab = Tab.LIBRARY, libraryPage = AppNav.PATCHED_PAGE).open(HomeMode.PLAY_ANY).libraryPage)
    }

    /** Library opens on the games list (2026-09-30, UX audit P0-10): every way in lands where it meant to. */
    @Test
    fun `every way into Library lands on My games, except the one that says Patched versions`() {
        assertEquals(0, AppNav.MY_GAMES_PAGE)
        assertEquals(1, AppNav.PATCHED_PAGE)
        assertEquals(AppNav.MY_GAMES_PAGE, AppNav().libraryPage, "the default page")
        // The last visit was to Patched versions, on Play, on Home, on a mode screen, on More: the bar and Home's link still land on My games.
        val afterPatched = AppNav().pick(Tab.LIBRARY).withLibraryPage(AppNav.PATCHED_PAGE)
        assertEquals(AppNav.PATCHED_PAGE, afterPatched.libraryPage)
        for (from in listOf(afterPatched.play(), afterPatched.home(), afterPatched.open(HomeMode.KAIZO), afterPatched.pick(Tab.MORE), afterPatched.openStats())) {
            val n = from.pick(Tab.LIBRARY)
            assertEquals(Tab.LIBRARY, n.tab)
            assertEquals(AppNav.MY_GAMES_PAGE, n.libraryPage, "the bar from ${from.tab} lands on My games")
            assertNull(n.mode)
        }
        // Welcome's "Add your games" and Home's "Play any game".
        assertEquals(AppNav.MY_GAMES_PAGE, afterPatched.home().addGames().libraryPage)
        assertEquals(AppNav.MY_GAMES_PAGE, afterPatched.home().open(HomeMode.PLAY_ANY).libraryPage)
        // A screen that says "add a game" with a button, and one that says "make a patched version".
        assertEquals(AppNav.MY_GAMES_PAGE, afterPatched.open(HomeMode.NUZLOCKE).openMyGames().libraryPage)
        assertEquals(Tab.LIBRARY, AppNav().openMyGames().tab)
        assertEquals(AppNav.PATCHED_PAGE, AppNav().openPatchedVersions().libraryPage)
        assertEquals(Tab.LIBRARY, AppNav().open(HomeMode.KAIZO).openPatchedVersions().tab)
        assertNull(AppNav().open(HomeMode.KAIZO).openPatchedVersions().mode)
        // The selector at the top of Library moves between the two, and a tap on Library while in it leaves the page.
        assertEquals(AppNav.PATCHED_PAGE, AppNav().pick(Tab.LIBRARY).withLibraryPage(AppNav.PATCHED_PAGE).pick(Tab.LIBRARY).libraryPage)
    }

    @Test
    fun `the buttons that open Library leave Back where the player came from`() {
        assertEquals(Tab.PLAY, AppNav().play().openMyGames().back()?.tab, "from Play, Back is Play")
        assertEquals(Tab.PLAY, AppNav().play().openPatchedVersions().back()?.tab)
        assertEquals(Tab.HOME, AppNav().open(HomeMode.KAIZO).openMyGames().back()?.tab, "from a mode screen, Back is Home")
        assertEquals(Tab.HOME, AppNav().open(HomeMode.KAIZO).openPatchedVersions().back()?.tab)
    }

    @Test
    fun `Kaizo IronMON, Nuzlocke and ROM Hacks each open their own screen on Home`() {
        for (m in listOf(HomeMode.KAIZO, HomeMode.NUZLOCKE, HomeMode.HACKS, HomeMode.HEARTSOUL)) {
            val n = AppNav().open(m)
            assertEquals(Tab.HOME, n.tab, "$m stays on the Home tab")
            assertEquals(m, n.mode, "$m is the screen open")
        }
        // Four different screens, not one screen four times.
        assertEquals(4, listOf(HomeMode.KAIZO, HomeMode.NUZLOCKE, HomeMode.HACKS, HomeMode.HEARTSOUL).map { AppNav().open(it).mode }.toSet().size)
    }

    @Test
    fun `everything that plays lands on Play and leaves no mode screen behind`() {
        for (from in listOf(AppNav(), AppNav().open(HomeMode.KAIZO), AppNav().open(HomeMode.HACKS), AppNav().open(HomeMode.PLAY_ANY), AppNav(tab = Tab.MORE))) {
            val n = from.play()
            assertEquals(Tab.PLAY, n.tab)
            assertNull(n.mode)
        }
    }

    @Test
    fun `a tap on the bar goes to that tab's own first screen`() {
        val onRun = AppNav().open(HomeMode.KAIZO)
        assertNull(onRun.pick(Tab.HOME).mode, "Home again is the menu, not the Run screen")
        assertEquals(Tab.HOME, onRun.pick(Tab.HOME).tab)
        assertEquals(Tab.LIBRARY, onRun.pick(Tab.LIBRARY).tab)
        assertNull(onRun.pick(Tab.LIBRARY).mode)
        // More keeps the page it was on, as it always did. Library does not: it opens on My games (see above).
        assertEquals(1, AppNav(morePage = 1).pick(Tab.MORE).morePage)
        assertEquals(AppNav.MY_GAMES_PAGE, AppNav(libraryPage = AppNav.PATCHED_PAGE).pick(Tab.PLAY).pick(Tab.LIBRARY).libraryPage)
    }

    @Test
    fun `Back from a mode screen is Home`() {
        for (m in listOf(HomeMode.KAIZO, HomeMode.NUZLOCKE, HomeMode.HACKS, HomeMode.HEARTSOUL)) {
            val back = AppNav().open(m).back()
            assertNotNull(back)
            assertEquals(AppNav(), back, "$m goes back to the menu itself")
        }
    }

    @Test
    fun `Back from Library or More returns to where the player came from`() {
        // From Play, as it always did: a game that was running is where Back leads (audit, 2026-09-27).
        assertEquals(Tab.PLAY, AppNav.opening("PLAY").pick(Tab.LIBRARY).back()?.tab)
        assertEquals(Tab.PLAY, AppNav.opening("PLAY").pick(Tab.MORE).back()?.tab)
        assertEquals(Tab.PLAY, AppNav().play().pick(Tab.LIBRARY).back()?.tab)
        // From Home, to Home: Back out of Library must not start a game.
        assertEquals(Tab.HOME, AppNav().pick(Tab.LIBRARY).back()?.tab)
        assertEquals(Tab.HOME, AppNav().pick(Tab.MORE).back()?.tab)
        assertEquals(Tab.HOME, AppNav().open(HomeMode.PLAY_ANY).back()?.tab, "Play any game, then Back, is the menu")
        assertEquals(Tab.HOME, AppNav().addGames().back()?.tab, "Add your games, then Back, is the menu")
        // More from Library does not make More the place to go back to.
        assertEquals(Tab.HOME, AppNav().pick(Tab.LIBRARY).pick(Tab.MORE).back()?.tab)
        assertEquals(Tab.HOME, AppNav().open(HomeMode.KAIZO).pick(Tab.LIBRARY).back()?.tab)
    }

    @Test
    fun `Back on Home and on Play is the system's, which leaves the app`() {
        assertNull(AppNav().back(), "Home")
        assertNull(AppNav().play().back(), "Play")
        assertNull(AppNav.opening("PLAY").back(), "Play after a resume")
    }

    @Test
    fun `Add your games opens the same page as Play any game`() {
        assertEquals(AppNav().open(HomeMode.PLAY_ANY), AppNav().addGames())
    }

    /**
     * rc32 audit P2 #32: Android ending the app behind a file picker (or rebuilding it) brought it back on Home, so the
     * pick never reached the screen that asked for it and an editor closed. The place is saved by name and read back.
     */
    @Test
    fun `every place in the app comes back as it was saved`() {
        val home = AppNav()
        val places = listOf(
            home, home.openStats(), home.play(), home.pick(Tab.LIBRARY), home.openPatchedVersions(), home.pick(Tab.MORE),
            home.pick(Tab.MORE).withMorePage(1), home.pick(Tab.MORE).withMorePage(AppNav.STREAM_PAGE), home.open(HomeMode.KAIZO), home.open(HomeMode.NUZLOCKE), home.open(HomeMode.HACKS), home.open(HomeMode.HEARTSOUL),
            home.open(HomeMode.PLAY_ANY), home.play().pick(Tab.LIBRARY).withLibraryPage(AppNav.PATCHED_PAGE), home.play().pick(Tab.MORE).openStats(),
        )
        for (p in places) {
            assertEquals(p, AppNav.fromSaved(p.saved()), p.saved())
            assertEquals(p, AppNav.restore(p.saved(), "HOME"), "a launch with no game to reopen")
        }
        // Back from a restored Library still goes where it went before: the anchor came back with it.
        assertEquals(Tab.PLAY, AppNav.restore(home.play().pick(Tab.LIBRARY).saved(), "HOME").back()!!.tab)
    }

    @Test
    fun `a game to reopen wins over the saved place, and nothing readable is the opening place`() {
        val library = AppNav().pick(Tab.LIBRARY).saved()
        assertEquals(AppNav.opening("PLAY"), AppNav.restore(library, "PLAY"), "CrashResume finds the game in Play")
        for (junk in listOf(null, "", "LIBRARY", "NOPE|||0|HOME|0", "LIBRARY||x|0|HOME|0", "LIBRARY||0|0|HOME|0|extra")) {
            assertEquals(AppNav.opening("HOME"), AppNav.restore(junk, "HOME"), "$junk")
        }
        // A page past the two there are is the nearest one.
        assertEquals(AppNav.PATCHED_PAGE, AppNav.fromSaved("LIBRARY||9|0|HOME|0")!!.libraryPage)
        // More has three pages since Stream (2026-10-05): one past them is Stream, the last.
        assertEquals(AppNav.STREAM_PAGE, AppNav.fromSaved("MORE||0|9|HOME|0")!!.morePage)
    }

    @Test
    fun `the place and the editor are saved with the activity, and the screens keep their keys across a process`() {
        val main = read("MainActivity.kt")
        assertTrue("var nav by androidx.compose.runtime.saveable.rememberSaveable(" in main)
        assertTrue("save = { it.saved() }, restore = { AppNav.restore(it, start) }" in main)
        assertTrue("var editing by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = EditingSaver)" in main)
        // The Crossfade's key for each screen is made of names: an enum's own hash is new in every process, and a new key
        // is a new screen whose saved state (a file picker's pending answer) is not given to it.
        assertTrue("contentKey = { (e, t, m) -> listOf(e?.first?.path, e?.second, t.name, m?.name) }" in main)
        assertFalse("androidx.compose.animation.Crossfade(" in main, "the plain Crossfade keys by the Triple's hash")
        // And the font size, bold text and language no longer rebuild the activity at all.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val changes = Regex("""android:configChanges="([^"]+)"""").find(manifest)!!.groupValues[1].split('|')
        for (c in listOf("fontScale", "fontWeightAdjustment", "locale", "layoutDirection")) assertTrue(c in changes, c)
    }

    @Test
    fun `a launch starts on Play only for a resume`() {
        assertEquals(Tab.PLAY, AppNav.opening("PLAY").tab)
        assertEquals(Tab.PLAY, AppNav.opening("PLAY").anchor)
        assertEquals(Tab.HOME, AppNav.opening("HOME").tab)
        // The old starting points no longer exist; an old value is Home, never a tab that is not there.
        assertEquals(Tab.HOME, AppNav.opening("RUN").tab)
        assertEquals(Tab.HOME, AppNav.opening("PREP").tab)
    }

    @Test
    fun `the page number Add files sits on is the My games page of the Library tab`() {
        val main = read("MainActivity.kt")
        val pages = Regex("Tab\\.LIBRARY -> TabPages\\(listOf\\(([^)]*)\\)").find(main)!!.groupValues[1]
            .split(",").map { it.trim().trim('"') }
        assertEquals(listOf("My games", "Patched versions"), pages)
        assertEquals("My games", pages[AppNav.MY_GAMES_PAGE])
        assertEquals("Patched versions", pages[AppNav.PATCHED_PAGE])
        // The old names are gone from what decides where Library opens: HomeNav.kt, and the Library block of MainActivity.kt.
        val block = main.substringAfter("Tab.LIBRARY -> TabPages(").substringBefore("Tab.MORE ->")
        for ((where, text) in listOf("HomeNav.kt" to read("HomeNav.kt"), "MainActivity's Library block" to block)) {
            assertFalse("Set up a game" in text || "All files" in text || "ALL_FILES_PAGE" in text, "$where still names the old pages")
        }
    }

    /**
     * rc32 audit P3 #15: More's How it works opened "KaizoCore plays IronMON on your phone", against the welcome's ruling
     * that it is an emulator for every game. It opens with the welcome's own sentence and names Home's four modes.
     */
    @Test
    fun `How it works says what the welcome says, and names every mode`() {
        assertEquals(HomeCopy.WELCOME_WHAT, HowItWorksCopy.INTRO)
        for (m in HomeMode.entries) assertTrue(HowItWorksCopy.all.any { m.title in it && m.line in it }, "${m.title} is named with its line")
        for (s in HowItWorksCopy.all) assertTrue(s in HomeCopy.all, "\"$s\" is in the copy-rule list")
        val about = read("AboutScreen.kt")
        assertFalse("KaizoCore plays IronMON on your phone" in about, "the old framing line is gone")
        assertTrue("Text(HowItWorksCopy.INTRO" in about && "for (mode in HomeMode.entries)" in about && "HowItWorksCopy.STEPS" in about)
    }
}
