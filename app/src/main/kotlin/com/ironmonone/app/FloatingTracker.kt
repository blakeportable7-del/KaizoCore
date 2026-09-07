package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The floating window's frame, in dp. Persisted per game with the other play settings. */
data class FloatFrame(val x: Float, val y: Float, val w: Float, val h: Float) {
    companion object {
        const val MIN_W = 200f
        const val MIN_H = 160f
        /** Top right, a third of the width, most of the height: where the dock would have been. */
        fun default(windowW: Float, windowH: Float): FloatFrame {
            val w = (windowW * 0.32f).coerceIn(MIN_W, 360f)
            val h = (windowH - 24f).coerceAtLeast(MIN_H)
            return FloatFrame(windowW - w - 8f, 12f, w, h)
        }
    }
    fun clamped(windowW: Float, windowH: Float): FloatFrame {
        val w = w.coerceIn(MIN_W, windowW); val h = h.coerceIn(MIN_H, windowH)
        return FloatFrame(x.coerceIn(0f, (windowW - w).coerceAtLeast(0f)), y.coerceIn(0f, (windowH - h).coerceAtLeast(0f)), w, h)
    }
}

/**
 * 2.2: the tracker as a window over the game in landscape. One draggable Box:
 * the title strip moves it, the grip at the bottom-right corner resizes it,
 * a double tap on the strip puts it back where the dock would be (the same
 * reset the dock's drag handle has). It never leaves the window.
 */
@Composable
fun FloatingTracker(
    frame: FloatFrame,
    windowW: Float,
    windowH: Float,
    onFrame: (FloatFrame) -> Unit,
    onDock: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset { IntOffset((frame.x * density).roundToInt(), (frame.y * density).roundToInt()) }
            .size(frame.w.dp, frame.h.dp)
            .background(Pc.Page)
            .border(1.dp, Pc.Border),
    ) {
        Column(Modifier.size(frame.w.dp, frame.h.dp)) {
            // Title strip: drag to move, double tap to reset.
            Row(
                Modifier.fillMaxWidth().height(22.dp).background(Pc.Ground)
                    .pointerInput(windowW, windowH) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onFrame(frame.copy(x = frame.x + drag.x / density, y = frame.y + drag.y / density).clamped(windowW, windowH))
                        }
                    }
                    .pointerInput(windowW, windowH) {
                        detectTapGestures(onDoubleTap = { onFrame(FloatFrame.default(windowW, windowH)) })
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixText("TRACKER", 7, Pc.Dim, Modifier.weight(1f))
                PixText("DOCK", 7, Pc.Text, Modifier.padding(start = 6.dp).clickable { onDock() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
        }
        // Resize grip
        Box(
            Modifier.align(Alignment.BottomEnd).size(24.dp)
                .pointerInput(windowW, windowH) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onFrame(frame.copy(w = frame.w + drag.x / density, h = frame.h + drag.y / density).clamped(windowW, windowH))
                    }
                },
            contentAlignment = Alignment.BottomEnd,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(14.dp).padding(2.dp)) {
                val s = size.minDimension
                for (i in 0 until 3) {
                    val o = i * s / 3f
                    drawLine(Pc.Dim, androidx.compose.ui.geometry.Offset(s, o), androidx.compose.ui.geometry.Offset(o, s), strokeWidth = 2f)
                }
            }
        }
    }
}
