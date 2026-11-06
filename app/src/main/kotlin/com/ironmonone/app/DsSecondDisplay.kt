package com.ironmonone.app

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.StylusTouch

/**
 * A DS's bottom screen on a second display (el borracho, 2026-10-20, on an AYN Thor: "only one screen could be set up";
 * Blake, 2026-10-23: build it as an option). The core still draws both screens into one frame, stacked (Top/Bottom, no
 * gap): the phone's own display shows the top half (the "1 screen" view) and libretrodroid draws the bottom half on a
 * SurfaceView in an Android Presentation on the other display (GLRetroView.setSecondDisplay). A touch on that display
 * is the stylus on the touch screen.
 *
 * Off unless chosen (TrackerOptions.dsBottomOnSecondScreen), and only while a second display is there; it takes the
 * second display before the tracker would (SecondScreenHost).
 *
 * The AYN Thor (public specs, 2026-11-06): the top screen, 6 inches, 1920 by 1080 at 120 Hz, is the main display and
 * the bottom one, 3.92 inches, 1240 by 1080 at 60 Hz, both touch, is the second; DraStic's and Azahar's second-screen
 * modes (both Presentation windows) draw on it, and DraStic's touch did not reach the game there until a fork read the
 * touches its own window gets, which is what this does. 1240 by 1080 is wider than 4:3, so the bottom screen fills its
 * height (1240 by 930 drawn at 4:3, a black band above and below); the letterbox is worked out from whatever size the
 * display has, so another device or a portrait panel still gets a 4:3 picture.
 */
internal object DsSecondDisplay {
    /** The bottom screen's rows of the stacked frame. */
    const val TOP = 0.5f
    const val BOTTOM = 1f

    /** The DS touch screen, in its own pixels. */
    const val DS_WIDTH = 256
    const val DS_HEIGHT = 192

    /** What libretrodroid takes for "no stylus" (GLRetroView's TOUCH_EVENT_OUTSIDE, already relative). */
    const val OUTSIDE = -10f

    /** The 4:3 picture in a [w] by [h] view: left, top, width, height, as libretrodroid's SecondDisplay draws it. */
    fun band(w: Float, h: Float): FloatArray {
        val bw = if (w > h * 4f / 3f) h * 4f / 3f else w
        val bh = if (w > h * 4f / 3f) h else w * 3f / 4f
        return floatArrayOf((w - bw) / 2f, (h - bh) / 2f, bw, bh)
    }

    /**
     * Where a touch at ([x], [y]) on a [w] by [h] view of the bottom screen lands in the whole stacked frame, 0 to 1
     * each way, or null when it is on the black band around the picture (no stylus there, as off a DS's screen).
     */
    fun touchToFrame(x: Float, y: Float, w: Float, h: Float): Pair<Float, Float>? {
        if (w <= 0f || h <= 0f) return null
        val (left, top, bw, bh) = band(w, h)
        val fx = (x - left) / bw
        val fy = (y - top) / bh
        if (fx < 0f || fx > 1f || fy < 0f || fy > 1f) return null
        return fx to (TOP + (BOTTOM - TOP) * fy)
    }

    /** The DS touch screen pixel a frame point from [touchToFrame] is, 0 to 255 across and 0 to 191 down. */
    fun dsPixel(frame: Pair<Float, Float>): Pair<Int, Int> {
        val fy = (frame.second - TOP) / (BOTTOM - TOP)
        return (frame.first * DS_WIDTH).toInt().coerceIn(0, DS_WIDTH - 1) to (fy * DS_HEIGHT).toInt().coerceIn(0, DS_HEIGHT - 1)
    }

    /** One line about a display, for the log and the touch info: id, name, size, refresh, density, rotation, kind. */
    fun describe(d: Display): String {
        @Suppress("DEPRECATION") val size = android.graphics.Point().also { d.getRealSize(it) }
        val metrics = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        val presentation = d.flags and Display.FLAG_PRESENTATION != 0
        return "display ${d.displayId} \"${d.name}\" ${size.x}x${size.y} ${"%.0f".format(d.refreshRate)} Hz ${metrics.densityDpi} dpi" +
            " rotation ${d.rotation * 90}" + (if (presentation) " presentation" else "") + (if (d.flags and Display.FLAG_PRIVATE != 0) " private" else "")
    }

    /** The tester's lines (TrackerOptions.dsSecondScreenTouchInfo): the display, frames drawn there, the last touch. */
    var displayLine by mutableStateOf("")
    var framesLine by mutableStateOf("")
    var touchLine by mutableStateOf("No touch on the second screen yet")
    val info: String get() = listOf(displayLine, framesLine, touchLine).filter { it.isNotBlank() }.joinToString("\n")

    /** At most this many re-shows in [RESHOW_WINDOW_MS] after the system dismisses the presentation on its own. */
    const val RESHOWS = 3
    const val RESHOW_WINDOW_MS = 10_000L

    /** Whether one more re-show fits, given the earlier ones' times ([past], oldest first) and [now]. */
    fun mayReshow(past: List<Long>, now: Long): Boolean = past.count { now - it < RESHOW_WINDOW_MS } < RESHOWS

    const val TAG = "KaizoCore"
}

/** The id of the display that would take the bottom screen, other than the app's own; null when there is none. */
@Composable
internal fun secondDisplayId(): Int? {
    val context = LocalContext.current
    val dm = remember(context) { context.getSystemService(DisplayManager::class.java) }
    fun look() = dm?.let { presentationDisplay(it, ownDisplayId(context))?.displayId }
    var id by remember { mutableStateOf(look()) }
    // The app moved to the other display (the Thor's bottom screen can hold it): looked at again.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    LaunchedEffect(configuration) { id = look() }
    DisposableEffect(dm) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { id = look() }
            override fun onDisplayRemoved(displayId: Int) { id = look() }
            override fun onDisplayChanged(displayId: Int) { id = look() }
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }
    return id
}

/** Whether a display other than the app's own is there to take the bottom screen. */
@Composable
internal fun secondDisplayPresent(): Boolean = secondDisplayId() != null

/**
 * Shows the bottom screen on the second display while [enabled]; true while it is up. Follows the display: shown again
 * when it comes back, or when the system dismissed the window over a change of the display's size or density (Android
 * dismisses a presentation then); false at once when it goes away, so the play screen puts both screens on the phone.
 *
 * Taken down while the app is in the background and put up again when it returns (a new surface each time, which
 * libretrodroid draws on from the next frame): since Android 11 a presentation is not hidden with its activity, so the
 * paused game's last frame would sit on the other screen over whatever the player opened there. While it is down for
 * that, the result stays true, so the phone's layout does not jump to two screens and back.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun DsBottomOnSecondDisplay(enabled: Boolean, retro: GLRetroView?): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val dm = remember(context) { context.getSystemService(DisplayManager::class.java) }
    var showing by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val reshows = remember { mutableListOf<Long>() }
    val displayId = secondDisplayId()
    val touchInfo = TrackerOptions.dsSecondScreenTouchInfo
    // Every display Android lists, once a play screen, for a bug report from a device whose second screen does not take
    // the bottom screen (the option shows only for one Android offers to apps as a presentation display).
    LaunchedEffect(dm) {
        dm?.displays?.forEach { d -> runCatching { Log.i(DsSecondDisplay.TAG, "display seen: " + DsSecondDisplay.describe(d)) } }
    }
    val lifecycle = activity?.lifecycle
    fun isStarted() = lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ?: true
    var started by remember { mutableStateOf(isStarted()) }
    var awayWhileShowing by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> started = isStarted() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    DisposableEffect(enabled && started, displayId, retro, activity, attempt, touchInfo) {
        val display = displayId?.let { dm?.getDisplay(it) }?.takeIf { it.isValid }
        if (started) awayWhileShowing = false
        if (!enabled || !started || retro == null || display == null || activity == null) {
            showing = false
            return@DisposableEffect onDispose { }
        }
        var disposed = false
        val presentation = Presentation(activity, display)
        // Never focusable: a tap on the bottom screen must not take the buttons from the game. On one focused window for
        // every display (Android's default), a touch on a focusable window moves the focus, and the game's keys with it.
        presentation.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val surface = SurfaceView(presentation.context)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = retro.setSecondDisplay(holder.surface, DsSecondDisplay.TOP, DsSecondDisplay.BOTTOM)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                retro.setSecondDisplay(holder.surface, DsSecondDisplay.TOP, DsSecondDisplay.BOTTOM)
            // Waited for: the GL thread must be done with the surface before this returns and the surface goes.
            override fun surfaceDestroyed(holder: SurfaceHolder) = retro.setSecondDisplay(null, 0f, 0f, waitMs = 500)
        })
        val label = if (touchInfo) TextView(presentation.context).apply {
            setTextColor(AndroidColor.WHITE)
            setBackgroundColor(0xB0000000.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(12, 8, 12, 8)
        } else null
        // The stylus: the pointer of the first finger down, pressed while it is down and on the picture, lifted when it
        // leaves the picture, comes up, or the system takes the gesture (StylusTouch's rule, as on the phone's screen).
        var pointer = -1
        var touches = 0
        surface.setOnTouchListener { v, e ->
            val action = e.actionMasked
            if (action == MotionEvent.ACTION_DOWN) pointer = e.getPointerId(0)
            val trackedUp = action == MotionEvent.ACTION_POINTER_UP && e.getPointerId(e.actionIndex) == pointer
            val w = v.width.toFloat(); val h = v.height.toFloat()
            var at: Pair<Float, Float>? = null
            var raw: Pair<Float, Float>? = null
            when (StylusTouch.act(action, trackedUp)) {
                StylusTouch.Act.PRESS -> {
                    val i = e.findPointerIndex(pointer)
                    if (i >= 0) {
                        raw = e.getX(i) to e.getY(i)
                        at = DsSecondDisplay.touchToFrame(e.getX(i), e.getY(i), w, h)
                        if (at != null) retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_POINTER, at.first, at.second)
                        else retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_POINTER, DsSecondDisplay.OUTSIDE, DsSecondDisplay.OUTSIDE)
                    }
                }
                StylusTouch.Act.LIFT -> {
                    pointer = -1
                    retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_POINTER, DsSecondDisplay.OUTSIDE, DsSecondDisplay.OUTSIDE)
                }
                StylusTouch.Act.NONE -> Unit
            }
            if (touchInfo) {
                if (action == MotionEvent.ACTION_DOWN) touches++
                val name = when (action) {
                    MotionEvent.ACTION_DOWN -> "down"; MotionEvent.ACTION_MOVE -> "move"; MotionEvent.ACTION_UP -> "up"
                    MotionEvent.ACTION_CANCEL -> "cancel"; MotionEvent.ACTION_POINTER_DOWN -> "2nd finger down"
                    MotionEvent.ACTION_POINTER_UP -> "finger up"; else -> "action $action"
                }
                val shown = raw ?: (e.x to e.y)
                val where = at?.let { DsSecondDisplay.dsPixel(it) }?.let { (x, y) -> "DS $x, $y" }
                    ?: if (raw != null) "outside the picture" else "stylus up"
                val from = buildString {
                    v.display?.let { append(" on display ${it.displayId}") }
                    e.device?.name?.let { append(", from \"$it\"") }
                }
                DsSecondDisplay.touchLine = "Touch $touches: $name at ${shown.first.toInt()}, ${shown.second.toInt()} of ${w.toInt()}x${h.toInt()} -> $where$from"
                label?.text = DsSecondDisplay.info
                if (action != MotionEvent.ACTION_MOVE) Log.i(DsSecondDisplay.TAG, "second display touch: ${DsSecondDisplay.touchLine}")
            }
            true
        }
        val root = FrameLayout(presentation.context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            label?.let { addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)) }
        }
        presentation.setContentView(root)
        presentation.setOnDismissListener {
            showing = false
            // Dismissed by the system, not by this effect (the display's size or density changed, or it went away): shown
            // again while it is still wanted, a few times at most, so a display that keeps changing cannot loop.
            if (!disposed) {
                val now = SystemClock.elapsedRealtime()
                if (DsSecondDisplay.mayReshow(reshows, now)) { reshows += now; attempt++ }
                else Log.w(DsSecondDisplay.TAG, "second display: dismissed again, left on the phone")
            }
        }
        DsSecondDisplay.displayLine = "Second screen: " + DsSecondDisplay.describe(display)
        DsSecondDisplay.framesLine = ""
        DsSecondDisplay.touchLine = "No touch on the second screen yet"
        // The system can refuse the window (the display went away between the listener and here, or it does not take
        // presentations): the bottom screen stays on the phone. An optional screen never takes the game down.
        showing = runCatching { presentation.show(); true }
            .onFailure { Log.w(DsSecondDisplay.TAG, "second display: not shown on ${DsSecondDisplay.describe(display)}: $it") }
            .getOrDefault(false)
        if (showing) Log.i(DsSecondDisplay.TAG, "second display: bottom screen on ${DsSecondDisplay.describe(display)}")
        // The tester's frame count, twice a second while the info is on.
        val ticker = if (touchInfo && showing) object : Runnable {
            override fun run() {
                val n = retro.secondDisplayFrames()
                DsSecondDisplay.framesLine = if (n < 0) "Frames drawn there: none, the picture was refused (see the log)" else "Frames drawn there: $n"
                label?.text = DsSecondDisplay.info
                root.postDelayed(this, 500)
            }
        } else null
        ticker?.let { root.post(it) }
        onDispose {
            disposed = true
            // Down because the app went to the background: the phone keeps the one-screen layout until it returns.
            awayWhileShowing = showing && !isStarted()
            showing = false
            ticker?.let { root.removeCallbacks(it) }
            runCatching { presentation.dismiss() }
            retro.setSecondDisplay(null, 0f, 0f)
        }
    }
    return enabled && (showing || awayWhileShowing)
}

/** The tester's touch info on the phone's screen too, where a screenshot catches it (TrackerOptions.dsSecondScreenTouchInfo). */
@Composable
internal fun DsSecondTouchInfo(modifier: Modifier = Modifier) {
    Text(
        DsSecondDisplay.info,
        modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 3.dp),
        color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace,
    )
}
