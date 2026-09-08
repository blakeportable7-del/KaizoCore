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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

private enum class Tab(val label: String, val scene: String? = null) {
    // `scene` names a file in assets/backgrounds. Null = plain black, which is
    // also the fallback when the named file is missing, so a tab can be
    // assigned a scene before its art exists without breaking anything.
    PREPARE("PREP", "battle-water"),
    RUN("RUN", "battle-water"),
    PLAY("PLAY"),
    LIBRARY("ROMS", "battle-water"),
    CONTROLS("KEYS", "battle-water"),
    ABOUT("INFO", "battle-water")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Demo.mode = intent?.getStringExtra("demo")
        // PREP and ADD FILES stream picks into the cache; a pick that was never
        // finished (a crash, a second tap) is worthless after a restart and, at
        // 512 MB a DS dump, fills a phone. Swept every launch.
        runCatching { cacheDir.listFiles()?.filter { it.name.startsWith("prep-") || it.name.startsWith("import-") }?.forEach { it.deleteRecursively() } }
        TrackerOptions.load(java.io.File(filesDir, "prep/tracker-options.txt"))
        ThemeStore.load(java.io.File(filesDir, "prep/theme.txt"))
        (getSystemService(INPUT_SERVICE) as InputManager)
            .registerInputDeviceListener(deviceListener, null)
        Controllers.refresh()
        setContent { MaterialTheme(colorScheme = Gen3.Scheme) { App() } }
    }

    override fun onDestroy() {
        (getSystemService(INPUT_SERVICE) as InputManager)
            .unregisterInputDeviceListener(deviceListener)
        super.onDestroy()
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
            LibretroDroid.onKeyEvent(0, event.action, event.keyCode)
            NewRunCombo.track(event.action, event.keyCode)
            FleeOnB.handle(event.action, event.keyCode)
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
                LibretroDroid.onKeyEvent(0, event.action, mapped)
                NewRunCombo.track(event.action, mapped)
                FleeOnB.handle(event.action, mapped)
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
private fun App() {
    var tab by remember { mutableStateOf(Tab.PREPARE) }
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

    Column(
        Modifier.fillMaxSize().background(Pc.Page)
            .let { if (fullscreen) it else it.statusBarsPadding().navigationBarsPadding() }
    ) {
        // The bar carries the screen's own actions on the right and nothing
        // else. The app's name was taking a full strip to tell you which app
        // you had already opened.
        val barActions = AppBarActions.content
        if (!fullscreen && (barActions != null || editing != null)) {
            Row(
                Modifier.fillMaxWidth().background(Pc.Ground)
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
                targetState = editing to tab,
                animationSpec = androidx.compose.animation.core.tween(
                    if (reduceMotion) 0 else 120,
                ),
                label = "tab",
            ) { (editingNow, tabNow) ->
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
                Tab.PREPARE -> ScreenBackground(Tab.PREPARE.scene) {
                    PrepareScreen(Modifier.fillMaxSize())
                }
                Tab.RUN -> ScreenBackground(Tab.RUN.scene) {
                    RunScreen(Modifier.fillMaxSize(), onEdit = { f, g -> editing = f to g })
                }
                Tab.PLAY -> PlayScreen(
                    Modifier.fillMaxSize(),
                    fullscreen = fullscreen,
                    landscape = landscape,
                    onExitFullscreen = { showChrome = true },
                    clean = clean,
                    onClean = { clean = it },
                )
                Tab.LIBRARY -> ScreenBackground(Tab.LIBRARY.scene) {
                    RomLibraryScreen(Modifier.fillMaxSize(), onPlay = { tab = Tab.PLAY })
                }
                Tab.CONTROLS -> ScreenBackground(Tab.CONTROLS.scene) {
                    ControlsScreen(Modifier.fillMaxSize())
                }
                Tab.ABOUT -> ScreenBackground(Tab.ABOUT.scene) {
                    AboutScreen(Modifier.fillMaxSize())
                }
            }
            }
        }

        // Tab bar as a Gen 3 menu row: paper strip, selector triangle marks the tab.
        if (editing == null && !fullscreen) {
            Row(
                // Tab bar in the tracker's own palette, not the green chrome.
                Modifier.fillMaxWidth().background(Pc.Border).padding(1.dp)
                    .background(Pc.Ground),
            ) {
                // Tab labels stop growing at a 1.3 system font scale.
                //
                // Six weight(1f) slots are 179px on a 1080px screen, which is
                // exactly four glyph cells at a 2.0 font scale - and the
                // longest label needs five (selector + four letters). So at
                // 2.0 every label wrapped: PREP became "▶PR" over "EP",
                // INFO became "INF" over "O", and the bar swelled to about a
                // quarter of the screen. Measured at 1.3 the same five cells
                // fit with 13px to spare, so 1.3 is the largest scale that
                // provably works and the cap is set there rather than guessed.
                //
                // This CAPS growth, it does not reverse it: below 1.3 nothing
                // changes at all, and above it the label still renders at
                // 11.7sp, larger than the 9sp everyone sees by default.
                //
                // Deliberately NOT maxLines = 1. If a future label is longer
                // than these, wrapping is the safe failure and clipping is
                // not - forcing one line here turned the 2.0 wrap into
                // genuinely lost text.
                val fontScale = LocalDensity.current.fontScale
                val labelSp = (9f * minOf(fontScale, 1.3f) / fontScale).sp
                Tab.entries.forEach { t ->
                    val active = tab == t
                    Text(
                        text = (if (active) "▶" else " ") + t.label,
                        modifier = Modifier.weight(1f)
                            .clickable { tab = t; showChrome = false }
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        fontFamily = Gen3.PixelFont,
                        fontSize = labelSp,
                        color = if (active) Pc.Gold else Pc.Text,
                    )
                }
            }
        }
    }
}
