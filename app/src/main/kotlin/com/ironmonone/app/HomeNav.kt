package com.ironmonone.app

import java.io.File

/**
 * The welcome screen and the main menu, without the screens (2026-09-29). Blake: "definitely a
 * welcome screen and main menu, where you can play any rom normally because this is an emulator,
 * a nuzlock button, a rom hack button, and a kaizo button".
 *
 * Where each button leads, where Back goes, when the welcome shows and what the Continue card
 * says are decided here, as plain data, so that all of it is tested on the JVM: a Compose screen
 * cannot be run there. HomeMenu.kt only draws what this file says, and MainActivity's App()
 * only holds the state.
 */

/**
 * The buttons on Home, in the order they are drawn, each with its one plain line (2026-09-29). Heart & Soul joined them
 * on 2026-10-05 (Blake: "Pokemon Heart and Soul will get its own button on the main menu").
 */
internal enum class HomeMode(val title: String, val line: String) {
    PLAY_ANY("Play any game", "Pick a game from your library and play it. No rules, no randomizer."),
    KAIZO("Kaizo IronMON", "A randomized game with the IronMON tracker. A loss means a new game."),
    NUZLOCKE("Nuzlocke", "Catch the first Pok\u00e9mon in each area. One that faints is gone."),
    HACKS("ROM Hacks", "Patch a fan-made hack onto a game you own, then play it."),
    HEARTSOUL("Pok\u00e9mon Heart & Soul", "Turn your Emerald into Heart & Soul, then play it as it is, in Kaizo IronMON or as a Nuzlocke."),
    ;

    /** What a screen reader says for the button: the title, then its line. */
    val spoken: String get() = "$title. $line"
}

/**
 * Where the app is (2026-09-29): a tab, the screen open on Home, and the page of Library (0 My games,
 * 1 Patched versions) and of More. Kaizo IronMON, Nuzlocke and ROM Hacks are screens on Home, not tabs, so [mode] is only
 * ever set while [tab] is HOME. PLAY_ANY is never held: it is a button that opens Library.
 *
 * [anchor] is Home or Play, whichever the player was last on, and is where Back from Library or
 * More goes. That was always Play (audit, 2026-09-27: Back on another tab used to close the app,
 * even with a game running), and Play is still where it goes for anyone who came from Play. From
 * Home it goes to Home, because the app opens there now: Back from Library must not start a game.
 */
internal data class AppNav(
    val tab: Tab = Tab.HOME,
    val mode: HomeMode? = null,
    val libraryPage: Int = MY_GAMES_PAGE,
    val morePage: Int = 0,
    val anchor: Tab = Tab.HOME,
    /**
     * Your stats is open (2026-09-30): a screen on Home under the modes' top bar, so only ever set with [tab] HOME and no
     * [mode]. Home's link and More's card both open it ([openStats]); Back, the top bar's arrow, the bar and the four
     * buttons all close it.
     */
    val stats: Boolean = false,
) {
    /**
     * A tap on the bottom bar: that tab's own first screen, never a mode screen left open behind it. Library is
     * entered on My games, the games list, from anywhere but Library itself (2026-09-30, UX audit P0-10): it used to
     * come back on whichever page was open last, so one visit to Patched versions made it the way in from then on.
     * A tap on Library while in it leaves the page as it is.
     */
    fun pick(t: Tab): AppNav = copy(
        tab = t, mode = null, stats = false,
        libraryPage = if (t == Tab.LIBRARY && tab != Tab.LIBRARY) MY_GAMES_PAGE else libraryPage,
        anchor = if (t == Tab.HOME || t == Tab.PLAY) t else anchor,
    )

    fun home(): AppNav = pick(Tab.HOME)

    /** To the game: Continue, a new run, a Library game's Play, a hack just made. */
    fun play(): AppNav = pick(Tab.PLAY)

    /** One of the buttons on Home. */
    fun open(m: HomeMode): AppNav = when (m) {
        HomeMode.PLAY_ANY -> copy(tab = Tab.LIBRARY, mode = null, stats = false, libraryPage = MY_GAMES_PAGE, anchor = Tab.HOME)
        HomeMode.KAIZO, HomeMode.NUZLOCKE, HomeMode.HACKS, HomeMode.HEARTSOUL -> copy(tab = Tab.HOME, mode = m, stats = false, anchor = Tab.HOME)
    }

    /** Home's "Your stats" link, and More's card: the stats screen on Home, from wherever the player is. */
    fun openStats(): AppNav = pick(Tab.HOME).copy(stats = true)

    /** "Add your games" on the welcome: Library's My games page, where Add files is. */
    fun addGames(): AppNav = open(HomeMode.PLAY_ANY)

    /**
     * Where a screen that says "add a game" sends the player, with a button (2026-09-30, UX audit P0-13): Library on
     * My games, the page Play any game opens. Unlike Play any game it leaves Back where it was, so a player who came
     * from Play goes back to Play.
     */
    fun openMyGames(): AppNav = pick(Tab.LIBRARY).copy(libraryPage = MY_GAMES_PAGE)

    /**
     * Where a screen that says "make a patched version" sends the player: Library on Patched versions (2026-09-30,
     * UX audit P0-10). Nothing sends anyone there yet but the page's own selector; a button that names it calls this.
     */
    fun openPatchedVersions(): AppNav = pick(Tab.LIBRARY).copy(libraryPage = PATCHED_PAGE)

    fun withLibraryPage(page: Int): AppNav = copy(libraryPage = page)

    fun withMorePage(page: Int): AppNav = copy(morePage = page)

    /**
     * The system Back where this class has a say, or null where the system's own applies
     * (leaving the app from Home or Play). A mode screen goes to Home; Library and More go to
     * [anchor].
     */
    fun back(): AppNav? = when {
        stats -> copy(stats = false)
        mode != null -> copy(mode = null)
        tab == Tab.LIBRARY || tab == Tab.MORE -> copy(tab = anchor)
        else -> null
    }

    companion object {
        /**
         * Library's first page, "My games": the games list, where the Add files button is, and the page every way in
         * lands on unless it says otherwise (MainActivity's TabPages list). Until 2026-09-30 (UX audit P0-10) it was the
         * second page, and the first was the one that makes a patched game.
         */
        const val MY_GAMES_PAGE = 0

        /** Library's second page, "Patched versions": make a patched game, such as Nat. Dex, from one already added. */
        const val PATCHED_PAGE = 1

        /** The state a launch starts in, from PrepStore.startingPoint(). */
        fun opening(startingPoint: String): AppNav =
            if (startingPoint == "PLAY") AppNav(tab = Tab.PLAY, anchor = Tab.PLAY) else AppNav()

        /**
         * Where the app was, from [saved] ([AppNav.saved]), when Android brings it back after ending it (rc32 audit P2 #32):
         * the same screen is drawn again, so a file picker's answer reaches the screen that asked for it, which it
         * cannot when the app comes back on Home. A game to reopen still wins: it is reopened in Play, where CrashResume
         * finds it. Anything that does not read as a place is the opening one.
         */
        fun restore(saved: String?, startingPoint: String): AppNav {
            if (startingPoint == "PLAY") return opening(startingPoint)
            return saved?.let { fromSaved(it) } ?: opening(startingPoint)
        }

        /** [AppNav.saved] read back, or null. */
        fun fromSaved(s: String): AppNav? = runCatching {
            val f = s.split('|')
            if (f.size != 6) return null
            AppNav(
                tab = Tab.valueOf(f[0]),
                mode = f[1].takeIf { it.isNotEmpty() }?.let { HomeMode.valueOf(it) },
                libraryPage = f[2].toInt().coerceIn(MY_GAMES_PAGE, PATCHED_PAGE),
                morePage = f[3].toInt().coerceIn(0, 1),
                anchor = Tab.valueOf(f[4]),
                stats = f[5] == "1",
            )
        }.getOrNull()
    }

    /** This place as one line of text, by name, for the saved state Android keeps (see [restore]). */
    fun saved(): String = listOf(tab.name, mode?.name.orEmpty(), libraryPage, morePage, anchor.name, if (stats) 1 else 0).joinToString("|")
}

/**
 * The welcome screen (2026-09-29): shown once, on the first launch that opens on Home.
 *
 * Its flag is a file directly in filesDir, not under prep/: prep/ is what Backup carries and what
 * BackupCoverageTest accounts for, and a phone that restores a backup still has none of the
 * player's dumps (the library and the prepared ROMs are left out of it), so the welcome is still
 * true there. The flag is written when the player answers it, with either button or Back, not
 * when it is drawn: a launch that ended before an answer shows it again. A phone that already
 * has games gets it once too, on the first launch of the build that has it.
 */
internal object Welcome {
    const val FILE = "welcome-seen.txt"

    fun flag(filesDir: File): File = File(filesDir, FILE)

    /** The flag is not there yet. */
    fun due(filesDir: File): Boolean = !flag(filesDir).exists()

    /** Written whole or not at all (SafeWrite); returns false when the disk refused it. */
    fun markSeen(filesDir: File): Boolean = SafeWrite.text(flag(filesDir), "seen\n")

    /**
     * Whether this launch opens on the welcome. Never over a resume: a player the app is
     * bringing back into a game (startingPoint "PLAY") is not new, and a staged demo (Demo.mode)
     * must land on what it stages.
     */
    fun showAtLaunch(filesDir: File, startingPoint: String, demo: String?): Boolean =
        due(filesDir) && startingPoint != "PLAY" && demo == null
}

/** What the Continue card on Home says: the game, and the mode it is in. */
internal data class ContinueInfo(val game: String, val detail: String)

/** The Continue card's content (2026-09-29), from what PrepStore already knows about the game Play would open. */
internal object ContinueCard {
    /**
     * What Play would open, when it has a file to open: the run's game with its mode and attempt,
     * or the Library game last picked. Null when nothing is in progress. Uses PrepStore.session(),
     * the same answer Play gets, so the card never names a game other than the one Continue opens.
     * It may identify a Library file that has no sidecar yet: call it off the main thread.
     */
    fun read(store: PrepStore, nuzlocke: NuzlockeStore? = null): ContinueInfo? {
        val session = store.session()
        if (!session.file.isFile) return null
        // A game with a Nuzlocke run says so, whether it is a Library game or a randomized run (2026-09-29), and a run
        // that has ended still names its mode, the one the player was last playing (Blake, 2026-09-30).
        val nuz = nuzlocke?.let { nz ->
            runCatching {
                NuzlockeStore.bindOf(session, store.runIdentity())?.let { nz.current(it) }?.header
                    ?.let { HomeCopy.nuzlockeDetail(it.preset.label, it.status, it.endReason) }
            }.getOrNull()
        }
        if (!session.isRun) return ContinueInfo(stripKnownExt(session.title), nuz ?: HomeCopy.LIBRARY_DETAIL)
        val kind = session.kind ?: return null
        if (nuz != null) return ContinueInfo(kind.displayName, nuz)
        val ruleset = store.loadLastRun()?.second
            ?.let { RnqsInfo.of(store.settingsFile(it)).ruleset }
            ?.let { CustomRuns.label(HomeCopy.ironmonDetail(RnqsInfo.rulesetLabel(it)), store.lastRunCustom()) }
            ?: HomeCopy.RUN_DETAIL
        val attempt = store.attempt(kind.id)
        return ContinueInfo(kind.displayName, if (attempt > 0) "$ruleset, attempt $attempt" else ruleset)
    }
}

/**
 * Every line the welcome, the menu and the Nuzlocke placeholder show, in one place (2026-09-29):
 * what a player reads is tested for the copy rules here (no em dash, nothing about how the work
 * is made, plain and dry), and HomeMenu.kt has no wording of its own.
 */
internal object HomeCopy {
    const val PICK_A_MODE = "Pick a mode"
    const val LEFT_OFF = "Where you left off"
    const val CONTINUE = "Continue"
    fun continueSpoken(game: String) = "$CONTINUE $game"
    const val LIBRARY_DETAIL = "Played from your library"
    const val RUN_DETAIL = "IronMON run"
    /** The mode as players name it, "Standard Nuzlocke", and how that run ended if it has (Blake, 2026-09-30). */
    fun nuzlockeDetail(preset: String, status: com.ironmonone.tracker.nuzlocke.RunStatus = com.ironmonone.tracker.nuzlocke.RunStatus.ACTIVE, endReason: String = "") =
        "$preset Nuzlocke" + when (status) {
            com.ironmonone.tracker.nuzlocke.RunStatus.OVER -> if (endReason.isBlank()) ", run over" else ", run over: $endReason"
            com.ironmonone.tracker.nuzlocke.RunStatus.COMPLETE -> ", champion beaten"
            else -> ""
        }
    /** "Kaizo IronMON", as players name the mode; IronMON Journey already says it. */
    fun ironmonDetail(ruleset: String) = if (ruleset.contains("IronMON", ignoreCase = true)) ruleset else "$ruleset IronMON"

    const val LIBRARY_LINK = "Library"
    const val LIBRARY_LINK_SPOKEN = "Open the library"
    const val MORE_LINK = "More"
    const val MORE_LINK_SPOKEN = "Open more: controls, backup and info"
    const val BACK_HOME = "Back to Home"

    const val STATS_LINK = "Your stats"
    const val STATS_LINK_SPOKEN = "Open your stats: runs started, wins and your best runs"
    const val STATS_CARD_LINE = "Runs started, wins, your best run in each game and mode, and the time you have played, across every game."
    const val STATS_OPEN = "Open your stats"

    const val WELCOME_TITLE = "Welcome to KaizoCore"
    const val WELCOME_WHAT = "KaizoCore is an emulator for every Game Boy, Game Boy Advance and DS game you own, and Pok\u00e9mon games get extra tools on top."
    const val WELCOME_DOES = "Play your games, take on Kaizo IronMON with a tracker, play a Nuzlocke, or try a ROM hack."
    const val WELCOME_NO_GAMES = "KaizoCore contains no games. Add game files you own. They stay on this phone."
    const val ADD_GAMES = "Add your games"
    const val LOOK_AROUND = "Look around"
    // On the first screen of a first launch, with the crash report switch under it (Blake, 2026-09-30: "explain this is
    // in beta, beg them to send me bugs, and or edits", "only during the first time they open the app").
    const val BETA_TITLE = "This is a beta"
    const val BETA_ASK = "KaizoCore is new, and one person makes it. It will have bugs, and I can only fix the ones I hear about. " +
        "Please send me every bug you find and anything you would change, big or small. Report a bug is under More, then " +
        "Backup and info, and it comes straight to me. Thank you. Blake"


    /** Everything above, the buttons' words and More's How it works, for the copy-rule test. */
    val all: List<String> = HowItWorksCopy.all + listOf(
        PICK_A_MODE, LEFT_OFF, CONTINUE, continueSpoken("Pokemon Emerald"), LIBRARY_DETAIL, RUN_DETAIL, nuzlockeDetail("Standard"),
        nuzlockeDetail("Standard", com.ironmonone.tracker.nuzlocke.RunStatus.OVER), nuzlockeDetail("Standard", com.ironmonone.tracker.nuzlocke.RunStatus.OVER, "Whiteout"),
        nuzlockeDetail("Standard", com.ironmonone.tracker.nuzlocke.RunStatus.COMPLETE), ironmonDetail("Kaizo"),
        LIBRARY_LINK, LIBRARY_LINK_SPOKEN, MORE_LINK, MORE_LINK_SPOKEN, BACK_HOME,
        STATS_LINK, STATS_LINK_SPOKEN, STATS_CARD_LINE, STATS_OPEN,
        WELCOME_TITLE, WELCOME_WHAT, WELCOME_DOES, WELCOME_NO_GAMES, ADD_GAMES, LOOK_AROUND, BETA_TITLE, BETA_ASK,
    ) + HomeMode.entries.flatMap { listOf(it.title, it.line, it.spoken) }
}

/**
 * More's "How it works" card (rc32 audit P3 #15). It opened "KaizoCore plays IronMON on your phone", against the
 * welcome's ruling (Blake, 2026-09-30) that KaizoCore is an emulator for every game with Pokémon tools on top. It
 * opens with the welcome's own sentence now, so the two cannot drift, then Home's four modes in their own words, then
 * where games come from and where they are played.
 */
internal object HowItWorksCopy {
    const val INTRO = HomeCopy.WELCOME_WHAT
    const val MODES_HEAD = "Home has five ways to play:"
    val STEPS: List<Pair<String, String>> = listOf(
        "1" to "Library: add your own game files. KaizoCore never downloads games.",
        "2" to "Home: pick a way to play, then a game.",
        "3" to "Play: play it. In a game the tracker reads, it fills in as you go.",
    )
    const val MORE = "Library also keeps your patches. More, Controls sets up a controller or keyboard."

    /** Every line the card shows, for the copy-rule test (HomeCopy.all). */
    val all: List<String> = listOf(INTRO, MODES_HEAD, MORE) + STEPS.map { it.second } + HomeMode.entries.map { "${it.title}: ${it.line}" }
}
