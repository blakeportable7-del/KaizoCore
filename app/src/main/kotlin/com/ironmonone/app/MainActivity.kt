package com.ironmonone.app

import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Header
import com.swordfish.libretrodroid.LibretroDroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal enum class Tab(val label: String) {
    // Tabs with icons (Blake, 2026-09-27). Seven pixel-font labels did not
    // fit a phone bar. PREP and ROMS are the two pages of LIBRARY; KEYS and
    // INFO are the two pages of MORE. Four since 2026-09-29: HOME, the main
    // menu, is first and is where the app opens. RUN and HACKS stopped being
    // tabs and are screens opened from Home's buttons (HomeMode, AppNav).
    HOME("Home"),
    PLAY("Play"),
    LIBRARY("Library"),
    MORE("More"),
}

/** The bar's icon for a tab. Not in the enum, so the tab list is plain data that a test reads (2026-09-29). */
private fun Tab.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    Tab.HOME -> androidx.compose.material.icons.Icons.Filled.Home
    Tab.PLAY -> androidx.compose.material.icons.Icons.Filled.PlayArrow
    Tab.LIBRARY -> androidx.compose.material.icons.Icons.Filled.List
    Tab.MORE -> androidx.compose.material.icons.Icons.Filled.Menu
}

/** The two pages inside a tab, switched by a segmented control at its top. */
@Composable
private fun TabPages(
    pages: List<String>,
    page: Int,
    onPage: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Shell.night)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius))
                .background(Shell.paper).padding(4.dp)
                .selectableGroup(),
        ) {
            // A tab group to TalkBack, 48dp tall (audit, 2026-09-27).
            pages.forEachIndexed { i, name ->
                val on = i == page
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = Shell.touchTarget)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(if (on) Shell.raised else Shell.paper)
                        .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onPage(i) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = if (on) Shell.inkOnPaper else Shell.hintOnPaper)
                }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Physical (Bluetooth/USB) controller presence, observable from Compose. When a
 * pad is connected the on-screen buttons get out of the way (Blake's rule), and
 * they come back the moment it disconnects.
 */
object Controllers {
    val connected = mutableStateOf(false)

    fun refresh() {
        connected.value = InputDevice.getDeviceIds().any { id ->
            val d = InputDevice.getDevice(id) ?: return@any false
            val s = d.sources
            (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                // A full keyboard also replaces the on-screen pad. Virtual
                // keyboards report SOURCE_KEYBOARD too, so require real keys.
                (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && !d.isVirtual)
        }
    }
}

/** Keys a controller may send that the core understands, passed straight through. */
private val PAD_KEYS = KeyBindings.PAD_PASSTHROUGH

class MainActivity : ComponentActivity() {

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Controllers.refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = Controllers.refresh()
        override fun onInputDeviceChanged(deviceId: Int) = Controllers.refresh()
    }

    // Analog stick / d-pad hat state, so an axis crossing only sends one key event.
    private var hatX = 0
    private var hatY = 0

    /**
     * Set by the remap screen while it waits for a key. Returning true consumes
     * the press so binding a key never also fires it into the game.
     */
    var keyCapture: ((Int) -> Boolean)? = null

    /**
     * True while [keyCapture] runs for a key from a gamepad. The remap screen
     * refuses standard pad buttons for console buttons, because those always
     * reach the core as themselves below; a keyboard's arrow keys share the
     * same key codes and must stay bindable (audit, 2026-09-27).
     */
    var captureFromPad: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Demo.mode = intent?.getStringExtra("demo")
        CrashLog.installHandler(this)
        SaveTrouble.init(this)
        // The player's saved key bindings and controller actions, published now (rc33 audit P1): only More > Controls
        // ever loaded them, so after a restart a remapped pad played on the defaults until that screen was opened.
        runCatching { KeyBindings(java.io.File(filesDir, KeyBindings.FILE)) }
        // PREP and ADD FILES stream picks into the cache; a pick that was never
        // finished (a crash, a second tap) is worthless after a restart and, at
        // 512 MB a DS dump, fills a phone. Swept every launch.
        runCatching { cacheDir.listFiles()?.filter { it.name.startsWith("prep-") || it.name.startsWith("import-") }?.forEach { it.deleteRecursively() } }
        TrackerOptions.load(java.io.File(filesDir, "prep/tracker-options.txt"))
        // A new run knows whether its settings file is one KaizoCore comes with (CustomRuns, IronMON rules check R2).
        val appContext = applicationContext
        CustomRuns.bundled = { ExtraPasses.bundled(appContext) }
        HiddenPowerTypes.load(java.io.File(filesDir, "prep/hidden-power.txt"))
        PcHeals.load(java.io.File(filesDir, "prep/pc-heals.txt"))
        RunClock.load(java.io.File(filesDir, "prep/run-clock.txt"))
        DeathQuotes.load(java.io.File(filesDir, DeathQuotes.FILE))
        SummaryChecks.load(java.io.File(filesDir, "prep/summary-checked.txt"))
        ThemeStore.load(java.io.File(filesDir, "prep/theme.txt"))
        ThemePresets.load(java.io.File(filesDir, ThemePresets.FILE))
        TrackerBackground.load(filesDir)
        NextRunJob.load(PrepStore(this))
        (getSystemService(INPUT_SERVICE) as InputManager)
            .registerInputDeviceListener(deviceListener, null)
        Controllers.refresh()
        // The stream reminder (StreamReminder): from Android 13 the phone asks once, as the stream is turned on.
        com.ironmonone.app.stream.StreamHub.onStarted = {
            if (com.ironmonone.app.stream.StreamReminder.shouldAsk(this)) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 71)
        }
        // Text with no colour of its own takes the light ink: on the dark
        // shell the default (black) was invisible.
        setContent {
            MaterialTheme(colorScheme = Gen3.Scheme) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Shell.inkOnPaper,
                ) { App() }
            }
        }
    }

    override fun onDestroy() {
        (getSystemService(INPUT_SERVICE) as InputManager)
            .unregisterInputDeviceListener(deviceListener)
        com.ironmonone.app.stream.StreamHub.onStarted = null
        super.onDestroy()
    }

    // While the stream is on, leaving KaizoCore pauses the game: a notification brings the player back (UX audit P0-19).
    override fun onStop() {
        super.onStop()
        com.ironmonone.app.stream.StreamReminder.left(this)
    }

    override fun onStart() {
        super.onStart()
        com.ironmonone.app.stream.StreamReminder.back(this)
    }

    /** Route controller AND keyboard keys into the core, same path as the pad. */
    // Overriding dispatchKeyEvent on ComponentActivity trips androidx's
    // RestrictedApi lint even though it is the documented way to intercept
    // hardware keys before the view tree. Runtime behavior is fine; the
    // suppression is for the lint gate only.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.repeatCount != 0) return super.dispatchKeyEvent(event)

        // Remap capture comes FIRST, before the game gate. It used to sit
        // behind it - and on the KEYS tab the Play screen is not composed, so
        // routeToGame is false and capture could never fire. The entire remap
        // screen was dead: every key press bound nothing.
        keyCapture?.let { capture ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                captureFromPad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                if (capture(event.keyCode)) return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                return true      // swallow the matching up, or the game sees it
            }
        }

        // Past the capture window, keys belong to the game only while the Play
        // screen says so.
        if (!KeyBindings.routeToGame) return super.dispatchKeyEvent(event)

        // A key bound to an emulator action (quick save, fast forward...)
        // is the action's, pad or keyboard, and never reaches the core.
        if (QuickActions.handle(event.action, event.keyCode)) return true

        val fromPad = (event.source and InputDevice.SOURCE_GAMEPAD) ==
            InputDevice.SOURCE_GAMEPAD
        if (fromPad && event.keyCode in PAD_KEYS) {
            // Before the core gets the press: B-to-Run reads the battle menu the game is on as B goes down.
            FleeOnB.handle(event.action, event.keyCode)
            LibretroDroid.onKeyEvent(0, event.action, event.keyCode)
            NewRunCombo.track(event.action, event.keyCode)
            SpriteMotion.key(event.action, event.keyCode)
            return true
        }

        // A keyboard is a real input device here, not a second-class one: map its
        // keys and feed the same path. Without this branch a Bluetooth keyboard
        // was completely inert, because it never reports SOURCE_GAMEPAD.
        val fromKeyboard = (event.source and InputDevice.SOURCE_KEYBOARD) ==
            InputDevice.SOURCE_KEYBOARD
        if (fromKeyboard) {
            // The player's own bindings, falling back to the defaults baked into
            // KeyBindings so a fresh install is playable with no setup.
            KeyBindings.active[event.keyCode]?.let { mapped ->
                FleeOnB.handle(event.action, mapped)
                LibretroDroid.onKeyEvent(0, event.action, mapped)
                NewRunCombo.track(event.action, mapped)
                SpriteMotion.key(event.action, mapped)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Sticks and d-pad hats arrive as motion; fold them into d-pad presses. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joy = (event.source and InputDevice.SOURCE_JOYSTICK) ==
            InputDevice.SOURCE_JOYSTICK
        if (joy && event.action == MotionEvent.ACTION_MOVE) {
            fun axis(a: Int, alt: Int): Int {
                val v = event.getAxisValue(a).takeIf { kotlin.math.abs(it) > 0.5f }
                    ?: event.getAxisValue(alt).takeIf { kotlin.math.abs(it) > 0.5f } ?: 0f
                return if (v > 0.5f) 1 else if (v < -0.5f) -1 else 0
            }
            val nx = axis(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_X)
            val ny = axis(MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_Y)
            fun swap(old: Int, new: Int, neg: Int, pos: Int) {
                if (old == new) return
                if (old != 0) LibretroDroid.onKeyEvent(
                    0, KeyEvent.ACTION_UP, if (old < 0) neg else pos)
                if (new != 0) LibretroDroid.onKeyEvent(
                    0, KeyEvent.ACTION_DOWN, if (new < 0) neg else pos)
                if (old != 0) SpriteMotion.key(KeyEvent.ACTION_UP, if (old < 0) neg else pos)
                if (new != 0) SpriteMotion.key(KeyEvent.ACTION_DOWN, if (new < 0) neg else pos)
            }
            swap(hatX, nx, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            swap(hatY, ny, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
            hatX = nx; hatY = ny
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun App() {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // Where the app opens: Home, or Play when it is being reopened after closing in the
    // middle of a game, so CrashResume finds that game up (PrepStore.startingPoint, 2026-09-29).
    val start = remember { runCatching { PrepStore(appContext).startingPoint(Demo.mode) }.getOrDefault("HOME") }
    // The tab, the screen open on Home (Kaizo IronMON, Nuzlocke, ROM Hacks) and which page of
    // LIBRARY (0 set up a game, 1 all files) and MORE (0 controls, 1 backup and info) is showing.
    // Plain data in AppNav, so where every button and Back leads is tested (2026-09-29).
    var nav by remember { mutableStateOf(AppNav.opening(start)) }
    val tab = nav.tab
    // The welcome, on the first launch that opens on Home (Welcome.showAtLaunch).
    var welcome by remember { mutableStateOf(runCatching { Welcome.showAtLaunch(appContext.filesDir, start, Demo.mode) }.getOrDefault(false)) }
    // A crash or freeze last session is offered at launch, not only on INFO,
    // where hardly anyone would find it (audit, 2026-09-27). Android ending the
    // app in the background to free memory is normal and is not announced.
    // The dialog sends the report to Blake when the player says so, or offers the share sheet
    // (CrashReportLaunch, 2026-09-29). `--es demo crash` stages it with a sample report.
    var launchCrash by remember {
        mutableStateOf(
            if (Demo.mode == "crash") demoReport(appContext)
            else runCatching { CrashLog.collect(appContext) }.getOrNull()
                ?.takeIf { "CRASH" in it || "ANR" in it }
        )
    }
    launchCrash?.let { text -> CrashReportLaunch(text, onClose = { launchCrash = null }) }
    UpdatePrompt(show = tab != Tab.PLAY && launchCrash == null)
    // The preset being edited, with the generation of the ROM it targets.
    var editing by remember { mutableStateOf<Pair<java.io.File, String?>?>(null) }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Rotating on the Play tab IS the fullscreen gesture: no title, no tabs, no
    // insets padding - the game owns the screen and the overlay floats on it.
    // Landscape Play hides the tab bar so the game and tracker get the screen.
    // That left no way back into the app: rotating was the only exit, which is
    // no exit at all on a phone with rotation locked. MENU and the system Back
    // button both bring the chrome back.
    var showChrome by remember { mutableStateOf(false) }
    // Rotating is the gesture that asks for the game to own the screen, so a
    // MENU/Back press does not survive a rotation. Without this, one Back tap
    // left every future landscape session stuck in the portrait stack.
    LaunchedEffect(landscape) { showChrome = false }
    // CLEAN VIEW (streaming capture): the game alone, in either orientation.
    // Back leaves it, and it is dropped on leaving the Play tab.
    var clean by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != Tab.PLAY) clean = false }
    val fullscreen = editing == null && (clean || (landscape && tab == Tab.PLAY && !showChrome))
    androidx.activity.compose.BackHandler(enabled = clean) { clean = false }
    androidx.activity.compose.BackHandler(enabled = fullscreen && !clean) { showChrome = true }
    // Back from Library or More returns to where the player came from: Play, as it always
    // did (it used to close the app, even with a game running: audit, 2026-09-27), or Home.
    // A mode screen's Back is Home, and on Home and Play the system handles it (AppNav.back,
    // 2026-09-29). Screens and editors register their own handlers later, so theirs win.
    androidx.activity.compose.BackHandler(enabled = editing == null && nav.back() != null) { nav.back()?.let { nav = it } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Answering the welcome, with either button or Back, writes its flag and it is never shown
    // again. The write is off the main thread because SafeWrite syncs to disk. "Add your games"
    // goes on to Library's All files page, where Add files is.
    fun closeWelcome(addGames: Boolean) {
        scope.launch(Dispatchers.IO) { Welcome.markSeen(appContext.filesDir) }
        welcome = false
        if (addGames) nav = nav.addGames()
    }
    // Back on the welcome is "Look around": it is skippable, and a first screen that closed
    // the app would only show itself again at the next launch.
    androidx.activity.compose.BackHandler(enabled = welcome) { closeWelcome(addGames = false) }
    if (welcome) {
        WelcomeScreen(onAddGames = { closeWelcome(addGames = true) }, onLookAround = { closeWelcome(addGames = false) })
        return
    }

    Column(
        Modifier.fillMaxSize().background(Shell.night)
            .let { if (fullscreen) it else it.statusBarsPadding().navigationBarsPadding() }
    ) {
        // The bar carries the screen's own actions on the right and nothing
        // else. The app's name was taking a full strip to tell you which app
        // you had already opened.
        val barActions = AppBarActions.content
        if (!fullscreen && (barActions != null || editing != null)) {
            Row(
                Modifier.fillMaxWidth().background(Shell.paper)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing != null) Gen3Header("SETTINGS")
                Spacer(Modifier.weight(1f))
                barActions?.invoke()
            }
        }

        Box(Modifier.weight(1f)) {
            // 120ms crossfade between tabs, and ZERO when the system asks for
            // reduced motion. Crossfade only ever animates ALPHA of content
            // that is already composed - it cannot leave a screen blank if the
            // animation clock never runs, which is the failure mode that has
            // bitten this project before.
            val reduceMotion = android.provider.Settings.Global.getFloat(
                androidx.compose.ui.platform.LocalContext.current.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            androidx.compose.animation.Crossfade(
                targetState = Triple(editing, tab, nav.mode),
                animationSpec = androidx.compose.animation.core.tween(
                    if (reduceMotion) 0 else 120,
                ),
                label = "tab",
            ) { (editingNow, tabNow, modeNow) ->
            editingNow?.let { file ->
                EditorScreen(
                    file.first, file.second,
                    onClose = { editing = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: when (tabNow) {
                // A scene per tab, EXCEPT Play - the game surface is the
                // picture there and anything behind it is either invisible or
                // a distraction. ScreenBackground owns the scrim, so the
                // contrast guarantees from M1 hold over any artwork.
                // Home is the main menu; its four buttons open Library's All files page or one of
                // three screens under a top bar with a back control to Home (2026-09-29). PLAY_ANY is
                // never held (AppNav.open sends it to Library), so it reads as Home here.
                Tab.HOME -> when (modeNow) {
                    // Your stats is a screen on Home like a mode's, under the same top bar, from the link under the buttons (AppNav.stats).
                    null, HomeMode.PLAY_ANY -> if (nav.stats) ModeScreen(StatsCopy.TITLE, onBack = { nav = nav.home() }) {
                        CareerStatsScreen(Modifier.fillMaxSize())
                    } else HomeScreen(
                        Modifier.fillMaxSize(),
                        onContinue = { nav = nav.play() },
                        onMode = { nav = nav.open(it) },
                        onLibrary = { nav = nav.pick(Tab.LIBRARY) },
                        onMore = { nav = nav.pick(Tab.MORE) },
                        onStats = { nav = nav.openStats() },
                    )
                    HomeMode.KAIZO -> ModeScreen(HomeMode.KAIZO.title, onBack = { nav = nav.home() }) {
                        ScreenBackground(null) {
                            RunScreen(Modifier.fillMaxSize(), onEdit = { f, g -> editing = f to g }, onPlay = { nav = nav.play() },
                                // The empty game list's Add a game button: Library's games page (2026-09-30, UX audit P0-13).
                                onAddGame = { nav = nav.openMyGames() })
                        }
                    }
                    HomeMode.NUZLOCKE -> ModeScreen(HomeMode.NUZLOCKE.title, onBack = { nav = nav.home() }) {
                        ScreenBackground(null) {
                            NuzlockeScreen(Modifier.fillMaxSize(), onPlay = { nav = nav.play() }, onAddGame = { nav = nav.openMyGames() })
                        }
                    }
                    HomeMode.HACKS -> ModeScreen(HomeMode.HACKS.title, onBack = { nav = nav.home() }) {
                        ScreenBackground(null) {
                            HacksScreen(Modifier.fillMaxSize(), onPlay = { nav = nav.play() })
                        }
                    }
                }
                // Where Play's empty screen sends the player (PlayNothing): Play takes no new parameters (2026-09-30).
                // While a new run is being made, Play waits on it instead of booting the run it replaces (rc33 P0-5).
                Tab.PLAY -> if (RunJob.installing) PlayRunBeingMade(Modifier.fillMaxSize()) else androidx.compose.runtime.CompositionLocalProvider(
                    LocalShellNav provides ShellNav(openMyGames = { nav = nav.openMyGames() }, openKaizo = { nav = nav.open(HomeMode.KAIZO) }),
                ) { PlayScreen(
                    Modifier.fillMaxSize(),
                    fullscreen = fullscreen,
                    landscape = landscape,
                    onExitFullscreen = { showChrome = true },
                    clean = clean,
                    onClean = { clean = it },
                ) }
                // My games first, the games list; Patched versions second (2026-09-30, UX audit P0-10). AppNav.MY_GAMES_PAGE is 0.
                Tab.LIBRARY -> TabPages(listOf("My games", "Patched versions"), nav.libraryPage, { nav = nav.withLibraryPage(it) }) {
                    if (nav.libraryPage == AppNav.MY_GAMES_PAGE) RomLibraryScreen(Modifier.fillMaxSize(), onPlay = { nav = nav.play() })
                    else PrepareScreen(Modifier.fillMaxSize(), onMyGames = { nav = nav.withLibraryPage(AppNav.MY_GAMES_PAGE) })
                }
                Tab.MORE -> TabPages(listOf("Controls", "Backup and info"), nav.morePage, { nav = nav.withMorePage(it) }) {
                    if (nav.morePage == 0) ControlsScreen(Modifier.fillMaxSize())
                    else AboutScreen(Modifier.fillMaxSize(), onStats = { nav = nav.openStats() })
                }
            }
            }
        }

        // Bottom navigation: icon over label, the selected tab in a pill.
        if (editing == null && !fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(Shell.paper)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .selectableGroup(),
            ) {
                // Labels stop growing at a 1.3 font scale so the tabs fit one row.
                val fontScale = LocalDensity.current.fontScale
                val labelSp = (12f * minOf(fontScale, 1.3f) / fontScale).sp
                val tabContext = androidx.compose.ui.platform.LocalContext.current
                Tab.entries.forEach { t ->
                    val active = tab == t
                    Column(
                        Modifier.weight(1f)
                            .heightIn(min = Shell.touchTarget)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab) {
                                // Not away from Play while its game is still loading: the teardown would wait on the
                                // GL thread and freeze the app (PlayLoading, rc33 audit P0-11).
                                if (tab == Tab.PLAY && t != Tab.PLAY && PlayLoading.holds()) {
                                    android.widget.Toast.makeText(tabContext, PlayLoading.WAIT, android.widget.Toast.LENGTH_SHORT).show()
                                } else { nav = nav.pick(t); showChrome = false }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .background(if (active) Shell.accent.copy(alpha = 0.22f) else Shell.paper)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                t.icon(), contentDescription = null,
                                tint = if (active) Shell.accentOnNight else Shell.hintOnNight,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            t.label,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = labelSp,
                            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (active) Shell.inkOnPaper else Shell.hintOnNight,
                        )
                    }
                }
            }
        }
    }
}
