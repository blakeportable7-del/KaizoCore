package com.ironmonone.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The FireRed and LeafGreen picture viewer and the map mark that opens it (2026-09-29). The pictures and
 * which place each one goes with are FrlgPictures; the pinch and drag numbers are PanZoom.
 *
 * The mark sits after a place's name wherever the Gen 3 tracker shows the current place (the wild battle
 * card, the route info screen, Trainers on Route, Trainer Info), and only for a place with pictures in a
 * FireRed or LeafGreen game: the callers ask FrlgPictures.placeFor, which answers null for any other game.
 * All of its state lives here, not in PlayScreen, which is at ART's method size limit.
 */

/** A location pin, 9 by 10 pixels, drawn in the tracker's own pixel style. */
private val PIN = listOf(
    "001111100",
    "011111110",
    "111000111",
    "111000111",
    "111000111",
    "011111110",
    "011111110",
    "001111100",
    "000111000",
    "000010000",
)

/**
 * The map mark: a pin announced as a button that says what it shows. Draws nothing for a null [place] (any
 * other game, or a place with no pictures), so a caller can pass the lookup as is.
 *
 * [compact] is for the tracker card, which is laid out as the PC tracker's: there the pin is the height of a
 * line of tracker text and takes no more room than that, so the card keeps its shape (a 48dp box made the
 * wild-battle name line about 35dp taller, review 2026-09-29). Compose still widens a small clickable's touch
 * area toward its 48dp minimum without growing the layout, so it stays easy to hit. Elsewhere (the Route info
 * and trainer screens, which are lists) the mark is a full 48dp box.
 */
@Composable
fun FrlgMapMark(place: FrlgPictures.Place?, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (place == null) return
    var open by remember(place) { mutableStateOf(false) }
    val spoken = place.spoken()
    Box(
        (if (compact) modifier.padding(start = 2.rp) else modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
            .clickable(onClickLabel = spoken, role = Role.Button) { open = true }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { MapPin(Pc.Gold, if (compact) Modifier.size(9.rp, 10.rp) else Modifier.size(18.dp, 20.dp)) }
    if (open) FrlgPictureViewer(place) { open = false }
}

@Composable
private fun MapPin(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 9f
        // One path for the whole pin: separate squares at a fractional size leave hairlines between them.
        val path = Path()
        PIN.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c == '1') path.addRect(Rect(x * u, y * u, (x + 1) * u, (y + 1) * u)) }
        }
        drawPath(path, color)
    }
}

/** What is on screen: not read yet ([Shot] is null), read, or unreadable (its image is null). */
private class Shot(val image: ImageBitmap?)

/**
 * Reads one picture at a time, off the main thread. Nothing here holds a picture after it is returned:
 * the viewer keeps only the one on screen, so 118 pictures are never in memory together.
 */
object FrlgPictureDecoder {
    private val oneAtATime = Mutex()

    /** The picture at [path] under assets/, or null when it cannot be read. */
    suspend fun load(context: Context, path: String): ImageBitmap? = oneAtATime.withLock {
        withContext(Dispatchers.IO) {
            // A picture the reader already moved on from (Next tapped twice) is not worth decoding.
            ensureActive()
            runCatching {
                val options = BitmapFactory.Options().apply { inScaled = false }
                context.applicationContext.assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
}

/**
 * Full screen: the place's name and a Close button, the map with pinch to zoom and drag to move, and Previous
 * and Next with "2 of 5" when a place has more than one. System Back closes it too. The hidden item tab went
 * with its pictures on 2026-09-30 (FrlgPictures).
 */
@Composable
fun FrlgPictureViewer(place: FrlgPictures.Place, onClose: () -> Unit) {
    var index by remember(place) { mutableStateOf(0) }
    val files = place.maps
    val count = files.size
    val at = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val path = files.getOrNull(at)?.let { FrlgPictures.mapAsset(it) }

    // The reading restarts for each picture, and the one before is let go with its state.
    val context = LocalContext.current
    var shot by remember(path) { mutableStateOf<Shot?>(null) }
    LaunchedEffect(path) {
        if (path != null) shot = Shot(FrlgPictureDecoder.load(context, path))
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The mark can be opened from inside the tracker's scaled canvas; this window is not scaled.
        CompositionLocalProvider(LocalRpx provides 1.dp) {
            Column(Modifier.fillMaxSize().background(Pc.Page).semantics { paneTitle = "Pictures for ${place.name}" }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PixText(place.name, 12, Pc.Gold, Modifier.weight(1f))
                    ViewerButton("Close", spoken = "Close the pictures") { onClose() }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val s = shot
                    when {
                        s == null -> PixText("Loading", 12, Pc.Dim, Modifier.align(Alignment.Center))
                        s.image == null -> PixText("This picture could not be opened.", 12, Pc.Negative, Modifier.align(Alignment.Center))
                        else -> ZoomablePicture(
                            s.image,
                            if (count > 1) "${place.name}, map ${at + 1} of $count" else "${place.name}, map",
                            Modifier.fillMaxSize(),
                        )
                    }
                }
                if (count > 1) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerButton("Previous", enabled = at > 0) { index = at - 1 }
                    PixText(
                        "${at + 1} of $count", 12, Pc.Text,
                        Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        align = TextAlign.Center,
                    )
                    ViewerButton("Next", enabled = at < count - 1) { index = at + 1 }
                }
            }
        }
    }
}

/** A bordered text button, at least 48dp each way. */
@Composable
private fun ViewerButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    spoken: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(Pc.Ground)
            .border(1.dp, if (enabled) Pc.Border else Pc.Border.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClickLabel = spoken, role = Role.Button) { onClick() }
            .semantics { if (spoken != null) contentDescription = spoken }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { PixText(label, 12, if (!enabled) Pc.Dim.copy(alpha = 0.5f) else Pc.Text) }
}

/**
 * The picture, fitted to the view to start with, that pinch and drag zoom and move (PanZoom). It is drawn
 * straight from the bitmap with the nearest pixel once enlarged, so pixel art stays crisp.
 */
@Composable
private fun ZoomablePicture(image: ImageBitmap, description: String, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(IntSize.Zero) }
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    // Starts fitted, and starts again for a new picture or a view that changed shape (a rotation).
    var xf by remember(image, view) { mutableStateOf(PanZoom.fitted(iw, ih, vw, vh)) }
    Canvas(
        modifier.clipToBounds()
            .onSizeChanged { view = it }
            .pointerInput(image, view) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    xf = PanZoom.transform(xf, iw, ih, vw, vh, centroid.x, centroid.y, pan.x, pan.y, zoom)
                }
            }
            .pointerInput(image, view) {
                detectTapGestures(onDoubleTap = { p -> xf = PanZoom.doubleTap(xf, iw, ih, vw, vh, p.x, p.y) })
            }
            .semantics { contentDescription = description; role = Role.Image },
    ) {
        if (vw > 0f && vh > 0f) {
            val s = xf.scale
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(xf.x.roundToInt(), xf.y.roundToInt()),
                dstSize = IntSize(max(1, (iw * s).roundToInt()), max(1, (ih * s).roundToInt())),
                filterQuality = if (PanZoom.nearestPixel(s)) FilterQuality.None else FilterQuality.Medium,
            )
        }
    }
}
