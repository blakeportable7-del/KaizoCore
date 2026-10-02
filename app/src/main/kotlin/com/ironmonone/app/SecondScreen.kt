package com.ironmonone.app

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The tracker on a second display (roadmap item 5, Blake, 2026-09-29): a dual-screen handheld,
 * or a phone with an external screen, shows the game on the main screen and the tracker on the
 * other, the way a PC player keeps the tracker beside the game. Android's Presentation API: the
 * second display gets a Presentation window whose content is a ComposeView bound to the
 * activity, so it recomposes from the same state as the play screen.
 *
 * Returns whether [content] is showing on a second display; the caller then leaves the tracker
 * out of its own layout. With no second display, or [enabled] false, nothing is shown and the
 * result is false. A display added or removed while playing is followed.
 */
@Composable
fun SecondScreenHost(enabled: Boolean, content: @Composable () -> Unit): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val dm = remember(context) { context.getSystemService(DisplayManager::class.java) }
    var display by remember { mutableStateOf(dm?.let { presentationDisplay(it) }) }
    var showing by remember { mutableStateOf(false) }
    val latest = rememberUpdatedState(content)

    DisposableEffect(dm) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { display = dm?.let { presentationDisplay(it) } }
            override fun onDisplayRemoved(id: Int) { display = dm?.let { presentationDisplay(it) } }
            override fun onDisplayChanged(id: Int) {}
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }

    DisposableEffect(enabled, display, activity) {
        val d = display
        if (!enabled || d == null || activity == null) {
            showing = false
            return@DisposableEffect onDispose { }
        }
        val presentation = Presentation(activity, d)
        val view = ComposeView(presentation.context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            // The Presentation's context has no activity in it, so what the tracker's content
            // looks up through the activity is provided here: the camera (Facecam) asks for the
            // activity-result registry, and threw without it (review, 2026-09-29).
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides activity,
                    androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides activity,
                ) { SecondScreenFrame { latest.value() } }
            }
        }
        presentation.setContentView(view)
        presentation.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
        }
        presentation.setOnDismissListener { showing = false }
        // The display went away between the listener and here, or the system refused the
        // window: the tracker stays on the phone. An optional screen never takes the game down.
        showing = runCatching { presentation.show(); true }.getOrDefault(false)
        onDispose {
            showing = false
            runCatching { presentation.dismiss() }
        }
    }
    return enabled && showing
}

/**
 * What the second display draws around the tracker. A Presentation is its own composition, so
 * the activity's theme and content colour do not reach it: they are set again here. The tracker
 * keeps its proportions as one column, as wide as fits the display's height, instead of the
 * phone's side pane (LocalCanvasMax), and scrolls when a long team runs past the bottom.
 */
@Composable
internal fun SecondScreenFrame(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(colorScheme = com.ironmonone.app.gen3.Gen3.Scheme) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Shell.inkOnPaper) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                // The Main background colour and the player's image across the whole display; the panel
                // in the column paints nothing of its own (TrackerBackdrop.kt).
                androidx.compose.ui.Modifier.fillMaxSize().hostBackdrop(),
                contentAlignment = androidx.compose.ui.Alignment.TopCenter,
            ) {
                val w = secondScreenColumnWidth(maxWidth, maxHeight)
                androidx.compose.runtime.CompositionLocalProvider(LocalCanvasMax provides w, LocalBackdropHosted provides true, LocalOnSecondScreen provides true) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.width(w).fillMaxHeight()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) { content() }
                }
                // View only, for now: every touch on this display stops here. A dialog the tracker
                // opens from a presentation window has no window token on Android 12 and later,
                // which would crash the app (review, 2026-09-29); the phone's own controls stay.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
                )
            }
        }
    }
}

/** The tracker column on a second display: its full width, or three quarters of its height if narrower. */
internal fun secondScreenColumnWidth(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf(width, height * 0.75f)

/** The first display Android offers for presentations, other than the one the app is on. */
internal fun presentationDisplay(dm: DisplayManager): Display? =
    dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

private fun Context.findComponentActivity(): ComponentActivity? {
    var c: Context? = this
    while (c != null) {
        if (c is ComponentActivity) return c
        if (c is Activity) return null
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}
