package com.ironmonone.app

/**
 * The FILE bar's contents, free of Compose so the tests hold every rule (Blake, 2026-10-05 and 10-06: "completely
 * removing the gear and the hamburger menu buttons off the tracker and putting it on a file bar", "one file bar, semi
 * transparent, the docked tracker, floating tracker, and hud tracker", "an x close on the file bar right end", "File,
 * New....etc", and "ship the new file menu with the updates, and tracker options").
 *
 * One see-through bar across the top of the play screen, the same in every view and both orientations: seven groups,
 * each a short sheet, and the X at the right end. It holds everything the File band, its MORE menu, the tracker's gear
 * (Tracker Setup) and its three-line menu held, and the way to the app's tabs that full screen hides. [FileBarMap.LEGACY]
 * says where each of those went; nothing was dropped.
 */
enum class BarGroup(val label: String, val line: String) {
    FILE("FILE", "Save states and the game itself."),
    NEW("NEW", "Ends this run and starts the next seed."),
    TRACKER("TRACKER", "Tracker Setup holds every option. Its screens are here too."),
    VIEW("VIEW", "Where the tracker sits, and the screen around the game."),
    TOOLS("TOOLS", "Camera, streaming and the extras."),
    SETTINGS("SETTINGS", "The emulator and the app."),
    HOME("HOME", "The rest of the app. The game stays where you left it."),
}

/** How an item is drawn: a button, a switch, a question first, or one of the pickers. */
enum class BarKind { ACTION, TOGGLE, CONFIRM, SLOTS, SPEED, VIEWS, SCREENS, SLIDER, HOLD }

/** Every line on the bar's sheets, in each sheet's order. [wide] lines take the sheet's whole width. */
enum class BarItem(val group: BarGroup, val label: String, val kind: BarKind = BarKind.ACTION, val wide: Boolean = false) {
    SLOT(BarGroup.FILE, "Save slot", BarKind.SLOTS, wide = true),
    SAVE(BarGroup.FILE, "Save state"),
    LOAD(BarGroup.FILE, "Load state"),
    STATES(BarGroup.FILE, "All save states", wide = true),
    SPEED(BarGroup.FILE, "Speed", BarKind.SPEED, wide = true),
    SOUND(BarGroup.FILE, "Sound", BarKind.TOGGLE),
    RESTART(BarGroup.FILE, "Restart"),
    REWIND(BarGroup.FILE, "Rewind", BarKind.HOLD),

    NEW_RUN(BarGroup.NEW, "New run", BarKind.CONFIRM, wide = true),

    SETUP(BarGroup.TRACKER, "Tracker Setup", wide = true),
    RULES(BarGroup.TRACKER, "Rules for this run"),
    FAVORITES(BarGroup.TRACKER, "Edit favorites"),
    NOTEBOOK(BarGroup.TRACKER, "Notebook"),
    COVERAGE(BarGroup.TRACKER, "Coverage calc"),
    TRAINERS(BarGroup.TRACKER, "Trainers on route"),
    HEALS(BarGroup.TRACKER, "Heals in bag"),
    BATTLE(BarGroup.TRACKER, "Battle details"),
    CATCH(BarGroup.TRACKER, "Catch rates"),
    EVO(BarGroup.TRACKER, "Evo data"),
    STATS(BarGroup.TRACKER, "Stats"),
    TIME(BarGroup.TRACKER, "Time machine"),
    TRACKED(BarGroup.TRACKER, "Tracked Pokémon"),
    PAST(BarGroup.TRACKER, "Past runs"),
    STATISTICS(BarGroup.TRACKER, "Statistics"),
    TOURNEY(BarGroup.TRACKER, "Tourney tracker"),
    GACHA(BarGroup.TRACKER, "GachaMon collection"),
    THEME(BarGroup.TRACKER, "Edit color theme"),
    QUOTES(BarGroup.TRACKER, "Game over lines"),
    CLEAR(BarGroup.TRACKER, "Clear tracked data", BarKind.CONFIRM, wide = true),

    VIEWS(BarGroup.VIEW, "Tracker", BarKind.VIEWS, wide = true),
    SCREENS(BarGroup.VIEW, "DS screens", BarKind.SCREENS, wide = true),
    HUD_MINE(BarGroup.VIEW, "HUD: your panel", BarKind.TOGGLE),
    HUD_REST(BarGroup.VIEW, "HUD: battle panel", BarKind.TOGGLE),
    SEE_THROUGH(BarGroup.VIEW, "See-through", BarKind.SLIDER, wide = true),
    CLEAN(BarGroup.VIEW, "Clean view"),
    LAYOUT(BarGroup.VIEW, "Edit layout"),
    PAD(BarGroup.VIEW, "On-screen pad", BarKind.TOGGLE),

    CAM(BarGroup.TOOLS, "Camera", BarKind.TOGGLE),
    STREAM(BarGroup.TOOLS, "Stream", BarKind.TOGGLE),
    ACHIEVEMENTS(BarGroup.TOOLS, "Achievements"),
    CHEATS(BarGroup.TOOLS, "Cheats"),
    ROUTE_LOG(BarGroup.TOOLS, "Route log"),
    INJECT(BarGroup.TOOLS, "Test Pokémon"),

    EMULATOR(BarGroup.SETTINGS, "Emulator settings", wide = true),
    CONTROLS(BarGroup.SETTINGS, "Controls"),
    BACKUP(BarGroup.SETTINGS, "Backup and info"),
    /** More, Stream (OBS and Twitch, feat/stream): shown once the shell gives a way there (ShellNav.openStreamSettings). */
    STREAM_SETTINGS(BarGroup.SETTINGS, "Stream settings"),

    HOME(BarGroup.HOME, "Home", wide = true),
    MY_GAMES(BarGroup.HOME, "My games"),
    PATCHED(BarGroup.HOME, "Patched versions"),
    YOUR_STATS(BarGroup.HOME, "Your stats"),
    LEAVE(BarGroup.HOME, "Leave full screen"),
}

/** The tracker views on VIEW's first line. HUD is offered only while [TrackerHud.ENABLED]. */
enum class BarView(val label: String) { DOCKED("Docked"), FLOATING("Floating"), HUD("HUD"), HIDDEN("Hidden") }

/** Which of Tracker Setup's screens this game and mode have: the gear dialog's own conditions (TrackerGearDialog.kt). */
data class TrackerReach(
    val notebook: Boolean = false,
    val stats: Boolean = false,
    val trainers: Boolean = false,
    val battle: Boolean = false,
    val catchRates: Boolean = false,
    val heals: Boolean = false,
    val timeMachine: Boolean = false,
    val pastRuns: Boolean = false,
    val statistics: Boolean = false,
    val evo: Boolean = false,
    val tracked: Boolean = false,
    val tourney: Boolean = false,
    val theme: Boolean = false,
)

/** What the game in Play is and has, for the sheets' rules. */
data class BarContext(
    val tracked: Boolean = true,
    val isRun: Boolean = false,
    val ds: Boolean = false,
    val gameBoy: Boolean = false,
    val landscape: Boolean = true,
    val fullscreen: Boolean = true,
    val controller: Boolean = false,
    val view: BarView = BarView.DOCKED,
    val hudEnabled: Boolean = TrackerHud.ENABLED,
    val rewindAllowed: Boolean = false,
    val raHardcore: Boolean = false,
    val cheatsAllowed: Boolean = false,
    /** The Gen 3 tracker is up: it alone records the route log. */
    val routeLog: Boolean = false,
    /** A debug build on a DS game: the tracker's test Pokemon. */
    val inject: Boolean = false,
    /** The shell has a Stream settings page to open (feat/stream's More, Stream). */
    val streamSettings: Boolean = false,
    // Tracker Setup's scope (GearScope).
    val ironmon: Boolean = false,
    val plain: Boolean = true,
    val gen3: Boolean = false,
    val reach: TrackerReach = TrackerReach(),
)

object FileBarMap {
    /** The groups the bar shows for [c]: NEW only in a run, TRACKER only where there is a tracker. */
    fun groups(c: BarContext): List<BarGroup> = BarGroup.entries.filter { g ->
        when (g) {
            BarGroup.NEW -> c.isRun
            BarGroup.TRACKER -> c.tracked
            else -> true
        } && items(g, c).isNotEmpty()
    }

    /** The lines of [g]'s sheet for [c], in order. */
    fun items(g: BarGroup, c: BarContext): List<BarItem> = BarItem.entries.filter { it.group == g && shows(it, c) }

    /** The views VIEW offers: the HUD only while it is switched on. */
    fun views(c: BarContext): List<BarView> = BarView.entries.filter { it != BarView.HUD || c.hudEnabled }

    fun shows(i: BarItem, c: BarContext): Boolean = when (i) {
        BarItem.NEW_RUN -> c.isRun
        // Tracker Setup's own rows, under its own conditions (TrackerGearDialog): a row that does nothing is worse than none.
        BarItem.SETUP, BarItem.CLEAR -> c.tracked
        BarItem.RULES -> c.tracked && !c.plain
        BarItem.FAVORITES, BarItem.QUOTES -> c.tracked && c.ironmon
        BarItem.COVERAGE -> c.tracked && !c.gameBoy
        BarItem.STATS -> c.tracked && !c.gameBoy && c.reach.stats
        BarItem.NOTEBOOK -> c.tracked && c.reach.notebook
        BarItem.TRAINERS -> c.tracked && c.reach.trainers
        BarItem.BATTLE -> c.tracked && c.reach.battle
        BarItem.CATCH -> c.tracked && c.reach.catchRates
        BarItem.HEALS -> c.tracked && c.reach.heals
        BarItem.TIME -> c.tracked && c.reach.timeMachine
        BarItem.PAST -> c.tracked && c.ironmon && c.reach.pastRuns
        BarItem.STATISTICS -> c.tracked && c.ironmon && c.reach.statistics
        BarItem.TOURNEY -> c.tracked && c.ironmon && c.reach.tourney
        BarItem.GACHA -> c.tracked && c.gen3
        BarItem.EVO -> c.tracked && c.reach.evo
        BarItem.TRACKED -> c.tracked && c.reach.tracked
        BarItem.THEME -> c.tracked && c.reach.theme
        // Where the tracker sits is a landscape choice; in portrait it sits under the pad, and the line says so.
        BarItem.VIEWS -> c.tracked
        BarItem.SCREENS -> c.ds
        BarItem.HUD_MINE, BarItem.HUD_REST -> c.tracked && c.hudEnabled && c.view == BarView.HUD
        BarItem.SEE_THROUGH -> c.tracked && (c.view == BarView.FLOATING || (c.hudEnabled && c.view == BarView.HUD))
        BarItem.PAD -> c.controller
        BarItem.ROUTE_LOG -> c.routeLog
        BarItem.INJECT -> c.inject
        BarItem.STREAM_SETTINGS -> c.streamSettings
        // Only full screen hides the tab bar: elsewhere it is on screen already.
        BarItem.LEAVE -> c.landscape && c.fullscreen
        else -> true
    }

    /** Why a line is off, said when it is tapped; null where it works. Off lines stay on the sheet, so nothing hides. */
    fun offWhy(i: BarItem, c: BarContext): String? = when (i) {
        BarItem.REWIND -> when {
            !c.rewindAllowed -> "Rewind is off in a Kaizo IronMON run and in a Nuzlocke."
            c.raHardcore -> "Rewind is off in RetroAchievements hardcore."
            else -> null
        }
        BarItem.CHEATS -> if (c.cheatsAllowed) null else "Cheats are off in a Kaizo IronMON run, a Nuzlocke and RetroAchievements hardcore."
        else -> null
    }

    /** Where each entry of the old menus went (Blake, 2026-10-06: "Nothing may be lost"). */
    data class Legacy(val was: String, val where: String, val now: BarItem?, val how: String = "")

    /** Said for an entry with no line of its own: the bar, a gesture, or the tracker's own row. */
    const val THE_BAR = "the bar itself"
    const val TAP_TOP = "a tap on the top third of the game"
    const val HOLD = "a half-second hold on the floating window"
    const val OWN_ROW = "the tracker's own first row"

    val LEGACY: List<Legacy> = listOf(
        // The FILE menu (portrait's FILE, landscape's MORE), PlayScreen.kt before 2026-10-06.
        Legacy("Copy link", "FILE menu", BarItem.STREAM),
        Legacy("States, slot N", "FILE menu and File band", BarItem.STATES),
        Legacy("SAVE STATE", "FILE menu", BarItem.SAVE),
        Legacy("LOAD STATE", "FILE menu", BarItem.LOAD),
        Legacy("REWIND", "FILE menu and File band", BarItem.REWIND),
        Legacy("NEW RUN", "FILE menu", BarItem.NEW_RUN),
        Legacy("NEW NUZLOCKE", "FILE menu", BarItem.NEW_RUN),
        Legacy("Speed Nx", "FILE menu and File band", BarItem.SPEED),
        Legacy("MUTE", "FILE menu", BarItem.SOUND),
        Legacy("RESTART", "FILE menu", BarItem.RESTART),
        Legacy("Top screen only", "FILE menu", BarItem.SCREENS),
        Legacy("RULES", "FILE menu", BarItem.RULES),
        Legacy("TRACKER SETUP", "FILE menu (second display)", BarItem.SETUP),
        Legacy("CAM", "FILE menu and File band", BarItem.CAM),
        Legacy("STREAM", "FILE menu", BarItem.STREAM),
        Legacy("CLEAN VIEW", "FILE menu and File band", BarItem.CLEAN),
        Legacy("EDIT LAYOUT", "FILE menu and File band", BarItem.LAYOUT),
        Legacy("SETTINGS", "FILE menu", BarItem.EMULATOR),
        Legacy("ACHIEVEMENTS", "FILE menu", BarItem.ACHIEVEMENTS),
        Legacy("CHEATS", "FILE menu", BarItem.CHEATS),
        Legacy("HIDE PAD", "FILE menu", BarItem.PAD),
        Legacy("ROUTE LOG", "FILE menu", BarItem.ROUTE_LOG),
        Legacy("INJECT", "FILE menu (debug builds)", BarItem.INJECT),
        Legacy("Fewer", "FILE menu (landscape)", null, THE_BAR),
        Legacy("Close", "FILE menu (landscape)", null, THE_BAR),
        // The landscape File band (LandscapeMenuBand).
        Legacy("SAVE", "File band", BarItem.SAVE),
        Legacy("LOAD", "File band", BarItem.LOAD),
        Legacy("NEW", "File band", BarItem.NEW_RUN),
        Legacy("SOUND", "File band", BarItem.SOUND),
        Legacy("PAD ON", "File band", BarItem.PAD),
        Legacy("MORE", "File band", null, THE_BAR),
        Legacy("MENU", "File band", BarItem.LEAVE),
        Legacy("the band's X", "File band", null, THE_BAR),
        // The tracker's three-line menu (TrackerCornerMenu) and the screen-tap menu (ScreenTapMenu).
        Legacy("ATTEMPT N", "three-line menu", null, OWN_ROW),
        Legacy("File", "three-line menu", null, THE_BAR),
        Legacy("1 screen (top only)", "three-line menu", BarItem.SCREENS),
        Legacy("2 screens", "three-line menu", BarItem.SCREENS),
        Legacy("Show the tracker", "screen-tap menu", BarItem.VIEWS),
        Legacy("Float the tracker", "three-line menu", BarItem.VIEWS),
        Legacy("Tracker HUD", "three-line menu", BarItem.VIEWS),
        Legacy("Dock the tracker beside the game", "three-line menu", BarItem.VIEWS),
        Legacy("Hide the tracker", "three-line menu", BarItem.VIEWS),
        Legacy("Tracker Setup", "floating window's menu", BarItem.SETUP),
        Legacy("Trainer info", "floating window's menu", null, OWN_ROW),
        Legacy("the menu button on the game", "screen-tap menu", null, TAP_TOP),
        Legacy("Menu", "a game with no tracker (LandscapeMenuChip)", null, TAP_TOP),
        Legacy("Show tracker tab", "the hidden tracker's tab", BarItem.VIEWS),
        // The gear (TrackerGearDialog), whose dialog is still Tracker Setup.
        Legacy("the gear", "tracker's rows and battle banner", BarItem.SETUP),
        Legacy("RULES FOR THIS RUN", "Tracker Setup", BarItem.RULES),
        Legacy("EDIT FAVORITES", "Tracker Setup", BarItem.FAVORITES),
        Legacy("COVERAGE CALC", "Tracker Setup", BarItem.COVERAGE),
        Legacy("STATS", "Tracker Setup", BarItem.STATS),
        Legacy("TRAINERS ON ROUTE", "Tracker Setup", BarItem.TRAINERS),
        Legacy("BATTLE DETAILS", "Tracker Setup", BarItem.BATTLE),
        Legacy("CATCH RATES", "Tracker Setup", BarItem.CATCH),
        Legacy("HEALS IN BAG", "Tracker Setup", BarItem.HEALS),
        Legacy("TIME MACHINE", "Tracker Setup", BarItem.TIME),
        Legacy("PAST RUNS", "Tracker Setup", BarItem.PAST),
        Legacy("STATISTICS", "Tracker Setup", BarItem.STATISTICS),
        Legacy("GachaMon Collection", "Tracker Setup", BarItem.GACHA),
        Legacy("EVO DATA", "Tracker Setup", BarItem.EVO),
        Legacy("TRACKED POKEMON", "Tracker Setup", BarItem.TRACKED),
        Legacy("TOURNEY TRACKER", "Tracker Setup", BarItem.TOURNEY),
        Legacy("EDIT COLOR THEME", "Tracker Setup", BarItem.THEME),
        Legacy("GAME OVER LINES", "Tracker Setup", BarItem.QUOTES),
        Legacy("OPEN NOTEBOOK", "Tracker Setup", BarItem.NOTEBOOK),
        Legacy("CLEAR TRACKED DATA", "Tracker Setup", BarItem.CLEAR),
        Legacy("Landscape tracker", "Tracker Setup", BarItem.VIEWS),
        Legacy("HUD: show your Pokemon's panel", "Tracker Setup", BarItem.HUD_MINE),
        Legacy("HUD: show the battle and route panel", "Tracker Setup", BarItem.HUD_REST),
        Legacy("Window see-through", "Tracker Setup", BarItem.SEE_THROUGH),
        // The floating window's title bar.
        Legacy("the lock", "floating window", null, HOLD),
        Legacy("the grip and the title bar's drag", "floating window", null, HOLD),
        Legacy("the resize grabs", "floating window", null, HOLD),
        // The app's tabs, out of reach in full screen.
        Legacy("Home", "tab bar", BarItem.HOME),
        Legacy("Library, My games", "tab bar", BarItem.MY_GAMES),
        Legacy("Library, Patched versions", "tab bar", BarItem.PATCHED),
        Legacy("Your stats", "Home", BarItem.YOUR_STATS),
        Legacy("More, Controls", "tab bar", BarItem.CONTROLS),
        Legacy("More, Backup and info", "tab bar", BarItem.BACKUP),
        Legacy("FILE in the top bar", "portrait top bar", null, THE_BAR),
    )
}
