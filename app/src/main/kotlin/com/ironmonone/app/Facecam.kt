package com.ironmonone.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Where the camera is drawn while CAM is on (rc32 audit P2 #59). DOCKED is the top of the tracker's column, wherever
 * the tracker is drawn: beside the game, in the floating window, over the DS's black box, or on a second display.
 * Anywhere else it is the BUBBLE over the game. Never both: each preview takes the camera from the other, so one
 * went black, and a game with no tracker (or one set to Hidden) got no camera at all in landscape.
 */
enum class FacecamPlace {
    NONE, BUBBLE, DOCKED;

    companion object {
        /**
         * [setting], [open] and [peek] are the landscape tracker's (TrackerOptions.landscapeTracker, Play's trackerOpen,
         * PlayUiState.trackerPeek); [onSecond] is the tracker showing on a second display.
         */
        fun of(on: Boolean, clean: Boolean, landscape: Boolean, tracked: Boolean, onSecond: Boolean,
               setting: LandscapeTracker, open: Boolean, peek: Boolean): FacecamPlace = when {
            !on -> NONE
            // The tracker, and the camera at the top of it, are on the other display: none on the phone.
            onSecond -> DOCKED
            clean -> NONE
            landscape && tracked && (setting == LandscapeTracker.FLOATING || (open && (setting == LandscapeTracker.DOCKED || peek))) -> DOCKED
            else -> BUBBLE
        }
    }
}

/**
 * What CAM says when the camera is refused (rc32 audit P2 #21). After a second no (Android 11 and newer) or "Don't
 * ask again", Android answers no at once with no dialog, so every CAM tap only said the permission was denied and
 * nothing in KaizoCore led back to it. A denial for good now says where to turn the camera on, and the status line
 * carries a Settings button that opens KaizoCore's own page there.
 */
internal object CameraDenied {
    const val DENIED = "Camera permission denied."
    const val OFF = "The camera is off for KaizoCore. Turn it on in Settings, Permissions."
    const val SETTINGS = "Settings"

    /**
     * Whether a denial just answered is for good. Android says it will explain no more only once it has stopped asking;
     * before the first ask the same flag is false too, so this is read in the denial's callback only.
     */
    fun permanent(activity: android.app.Activity?): Boolean =
        activity != null && runCatching { !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) }.getOrDefault(false)

    /** Play's status line for a denial; one for good also puts the Settings button on it ([PlayUiState.toastAction]). */
    fun note(context: android.content.Context, ui: PlayUiState, permanent: Boolean): String = note(ui, permanent) { openSettings(context) }

    internal fun note(ui: PlayUiState, permanent: Boolean, open: () -> Unit): String {
        if (!permanent) return DENIED
        ui.toastAction = Triple(OFF, SETTINGS, open)
        return OFF
    }

    /** KaizoCore's page in the system settings, where its permissions are. */
    fun openSettings(context: android.content.Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** The activity behind a Compose context, or null. */
private fun android.content.Context.activity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c != null && c !is android.app.Activity) c = (c as? android.content.ContextWrapper)?.baseContext
    return c as? android.app.Activity
}

/**
 * The camera DOCKED into the tracker column, the streaming layout: game on the
 * left, and a right column of camera over tracker. Fills the width it is given
 * at 16:9 - no dragging, no floating over the game, because in this layout it
 * has a slot of its own.
 */
@Composable
fun FacecamDocked(onDenied: (permanent: Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    // Whether Android will ask again, read in the denial's own callback (CameraDenied, rc32 audit P2 #21).
    val activity = remember(context) { context.activity() }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok; if (!ok) onDenied(CameraDenied.permanent(activity)) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) return

    var front by remember { mutableStateOf(true) }
    val g = com.ironmonone.app.gen3.Gen3
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .background(g.FrameDark).padding(1.dp)
            // Tap to flip, same as the floating bubble.
            .pointerInput(Unit) { detectTapGestures { front = !front } },
    ) {
        CameraPreview(front, lifecycleOwner)
    }
}

/**
 * Streaming facecam: a draggable camera bubble over the game. Screen-capture
 * streaming apps (Twitch, YouTube, Streamlabs) then broadcast game + tracker +
 * face in one take. Preview only - nothing is recorded or stored by this app.
 * Tap the bubble to flip front/back.
 */
@Composable
fun FacecamBubble(onDenied: (permanent: Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val activity = remember(context) { context.activity() }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok; if (!ok) onDenied(CameraDenied.permanent(activity)) }

    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) return

    var front by remember { mutableStateOf(true) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    // Resizable: the bubble was a fixed 132dp square, which is too small to be
    // worth streaming on a tablet and too big on a phone. Pinch to size it.
    var sizeDp by remember { mutableFloatStateOf(132f) }
    val g = com.ironmonone.app.gen3.Gen3

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    // Drag used to be unbounded, so the bubble could be flung off the edge and
    // was then unreachable for the rest of the session. Keep it on screen.
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    Box(
        Modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    sizeDp = (sizeDp * zoom).coerceIn(90f, 320f)
                    val sidePx = with(density) { sizeDp.dp.toPx() }
                    // Bottom-start anchored, so X runs right and Y runs up.
                    // The top strip is reserved: parking the bubble over it
                    // covered the only CAM button in landscape, and the bubble
                    // eats every touch inside itself - so the only way to turn
                    // it off was to rotate the phone.
                    val reservedTop = with(density) { 64.dp.toPx() }
                    offX = (offX + pan.x).coerceIn(0f, (screenW - sidePx).coerceAtLeast(0f))
                    offY = (offY + pan.y).coerceIn(
                        -(screenH - sidePx - reservedTop).coerceAtLeast(0f), 0f)
                }
            }
            .pointerInput(Unit) {
                // Flip lives here, not on the PreviewView's click listener: the
                // gesture detector above consumes the events before the View
                // ever sees them, so tapping to flip did nothing.
                detectTapGestures { front = !front }
            }
            .background(g.FrameDark)
            .padding(2.dp)
            .background(g.FrameBevel)
            .padding(2.dp),
    ) {
        // The preview binds ONLY when `front` changes. This used to live in an
        // AndroidView update block, which runs on every recomposition and tore
        // the camera down and rebuilt it on unrelated state changes.
        CameraPreview(front, lifecycleOwner)
    }
}

/**
 * Binds the camera exactly once per [front] change.
 *
 * Kept separate from the AndroidView so the bind is driven by a keyed effect
 * rather than by recomposition.
 */
@Composable
private fun BoxScope.CameraPreview(front: Boolean, lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val view = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    AndroidView(factory = { view }, modifier = Modifier.matchParentSize())
    // The camera is let go when the preview leaves, the docked one and the bubble alike (rc33 audit P1: only the
    // bubble did, so the docked camera stayed in use after CAM went off or Play was left). A bind still on its way
    // when the preview goes is dropped. Both run on the main executor, so the order is the order they were asked.
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            // Never block the main thread on the provider future here.
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ runCatching { future.get().unbindAll() } }, ContextCompat.getMainExecutor(context))
        }
    }
    LaunchedEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!alive.get()) return@addListener
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(view.surfaceProvider) }
                provider.unbindAll()
                // Try the requested lens, then the other one. A device with
                // only one camera - or one that underreports, which is what
                // an emulator does - failed to bind and then rendered NOTHING,
                // with no error: a camera button that silently does nothing.
                val wanted = if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                val other = if (front) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
                val bound = runCatching {
                    provider.bindToLifecycle(lifecycleOwner, wanted, preview)
                }.isSuccess
                if (!bound) {
                    runCatching {
                        provider.bindToLifecycle(lifecycleOwner, other, preview)
                    }
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
