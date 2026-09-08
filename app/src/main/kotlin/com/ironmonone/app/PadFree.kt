package com.ironmonone.app

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import kotlin.math.roundToInt

/**
 * The on-screen pad, placed from a [PadLayout] instead of a fixed
 * arrangement. Fills its parent; that parent IS the pad area the layout's
 * fractions refer to (the whole screen in landscape, the band under the
 * game in portrait).
 *
 * Edit mode puts ONE transparent sheet over the whole area, on top of every
 * button, and hit-tests drags and taps against the elements' last placed
 * rectangles. The sheet is what takes the pointer, so no key is sent while
 * editing, and it never moves, so drag deltas are in a fixed frame. (A
 * handle on each element was tried first: the handle moved with the
 * element under the finger and the deltas fed back on themselves, so a
 * 150px drag threw the button across the screen.)
 *
 * Nothing here writes to disk; every change goes out through [onEdit] and
 * the caller saves on DONE.
 */
@Composable
fun FreePad(
    layout: PadLayout,
    onB: () -> Unit,
    translucent: Boolean,
    skin: PadSkin = PadSkin.CLASSIC,
    /** Portrait's budget shrink; 1 in landscape. Multiplies every element's own scale. */
    baseScale: Float = 1f,
    editing: Boolean = false,
    selected: PadLayout.Element? = null,
    onSelect: (PadLayout.Element) -> Unit = {},
    onEdit: (PadLayout) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(layout)
    val rects = remember { HashMap<PadLayout.Element, Rect>() }
    Box(modifier.fillMaxSize().onSizeChanged { area = it }) {
        if (area.width == 0) return@Box
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        PadLayout.Element.entries.filter { it in layout.places }.forEach { e ->
            val s = PadGeometry.scaleOf(layout, e, baseScale)
            Box(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        // One geometry for the pad and for PadGeometryTest: centres in dp, the
                        // diamond one button from A, everything clamped inside the area.
                        val (cxDp, cyDp) = PadGeometry.centre(layout, e, area.width / density, area.height / density, translucent, skin, baseScale)
                        val cx = (cxDp * density).roundToInt() - placeable.width / 2
                        val cy = (cyDp * density).roundToInt() - placeable.height / 2
                        placeable.place(IntOffset(cx.coerceIn(0, (area.width - placeable.width).coerceAtLeast(0)),
                            cy.coerceIn(0, (area.height - placeable.height).coerceAtLeast(0))))
                    }
                }.onGloballyPositioned { c ->
                    val o = c.positionInParent()
                    rects[e] = Rect(o, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
                }.systemGestureExclusion() // a thumb sliding off a control at the screen edge is not a back gesture
                    .then(if (editing && selected == e) Modifier.border(2.dp, Pc.Gold) else Modifier)
            ) {
                when (e) {
                    PadLayout.Element.DPAD -> Box(contentAlignment = Alignment.Center) {
                        val cell = ((PadGeometry.BUTTON + 2 * PadGeometry.PAD) * s).dp
                        val modern = skin == PadSkin.MODERN
                        if (modern) {
                            // One disc with a cross, the store-emulator d-pad. The four
                            // arrows keep their own hit areas over it, drawn as glyphs only.
                            androidx.compose.foundation.Canvas(Modifier.size(cell * 3)) {
                                drawCircle(Color.White.copy(alpha = 0.16f))
                                drawCircle(Color.White.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                                val arm = size.width * 0.30f; val thick = size.width * 0.26f
                                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                                val r = androidx.compose.ui.geometry.CornerRadius(thick * 0.25f)
                                drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(c.x - thick / 2f, c.y - arm - thick / 2f),
                                    size = androidx.compose.ui.geometry.Size(thick, arm * 2f + thick), cornerRadius = r)
                                drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(c.x - arm - thick / 2f, c.y - thick / 2f),
                                    size = androidx.compose.ui.geometry.Size(arm * 2f + thick, thick), cornerRadius = r)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row { Spacer(Modifier.size(cell)); PadButton("^", KeyEvent.KEYCODE_DPAD_UP, translucent = translucent, scale = s, skin = skin, glyphOnly = modern); Spacer(Modifier.size(cell)) }
                            Row { PadButton("<", KeyEvent.KEYCODE_DPAD_LEFT, translucent = translucent, scale = s, skin = skin, glyphOnly = modern); Spacer(Modifier.size(cell)); PadButton(">", KeyEvent.KEYCODE_DPAD_RIGHT, translucent = translucent, scale = s, skin = skin, glyphOnly = modern) }
                            Row { Spacer(Modifier.size(cell)); PadButton("v", KeyEvent.KEYCODE_DPAD_DOWN, translucent = translucent, scale = s, skin = skin, glyphOnly = modern); Spacer(Modifier.size(cell)) }
                        }
                    }
                    PadLayout.Element.A -> PadButton("A", KeyEvent.KEYCODE_BUTTON_A, translucent = translucent, scale = s, skin = skin)
                    PadLayout.Element.B -> PadButton("B", KeyEvent.KEYCODE_BUTTON_B, translucent = translucent, scale = s, onB = onB, skin = skin)
                    PadLayout.Element.X -> PadButton("X", KeyEvent.KEYCODE_BUTTON_X, translucent = translucent, scale = s, skin = skin)
                    PadLayout.Element.Y -> PadButton("Y", KeyEvent.KEYCODE_BUTTON_Y, translucent = translucent, scale = s, skin = skin)
                    PadLayout.Element.L -> PadButton("L", KeyEvent.KEYCODE_BUTTON_L1, translucent = translucent, scale = s, mini = true, skin = skin)
                    PadLayout.Element.R -> PadButton("R", KeyEvent.KEYCODE_BUTTON_R1, translucent = translucent, scale = s, mini = true, skin = skin)
                    PadLayout.Element.SELECT -> PadButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, translucent = translucent, scale = s, small = !translucent, wide = translucent, skin = skin)
                    PadLayout.Element.START -> PadButton("START", KeyEvent.KEYCODE_BUTTON_START, translucent = translucent, scale = s, small = !translucent, wide = translucent, skin = skin)
                }
            }
        }
        if (editing) {
            fun hit(pos: Offset): PadLayout.Element? =
                PadLayout.Element.entries.filter { it in current.places }.lastOrNull { rects[it]?.contains(pos) == true }
            var dragging by remember { mutableStateOf<PadLayout.Element?>(null) }
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0x14FFD54A))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { pos -> hit(pos)?.let(onSelect) })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos -> dragging = hit(pos); dragging?.let(onSelect) },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                        ) { change, drag ->
                            change.consume()
                            val d = dragging ?: return@detectDragGestures
                            val l = current
                            // A diamond member drags the whole diamond: its A place is the centre.
                            val e = if (l.inDiamond(d)) PadLayout.Element.A else d
                            onEdit(l.with(e, l[e].moved(drag.x / size.width, drag.y / size.height)))
                        }
                    }
            )
        }
    }
}

/** The strip of edit controls shown while the layout is being edited. DONE first: it must never scroll away. */
@Composable
fun LayoutToolbar(
    layout: PadLayout,
    selected: PadLayout.Element?,
    landscape: Boolean,
    isDs: Boolean,
    onEdit: (PadLayout) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    skin: PadSkin = PadSkin.CLASSIC,
    onSkin: (PadSkin) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Gen3.FrameDark.copy(alpha = 0.85f)).padding(6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutChip("DONE", accent = true, onClick = onDone)
        if (selected != null) {
            LayoutChip("${selected.label.uppercase()} -") { onEdit(layout.with(selected, layout[selected].scaled(1 / 1.1f))) }
            LayoutChip("${selected.label.uppercase()} +") { onEdit(layout.with(selected, layout[selected].scaled(1.1f))) }
        }
        if (landscape) {
            LayoutChip("FADE ${(layout.opacity * 100).roundToInt()}%") {
                val next = if (layout.opacity >= 0.99f) 0.25f else (layout.opacity + 0.15f).coerceAtMost(1f)
                onEdit(layout.copy(opacity = next))
            }
        }
        if (isDs) {
            LayoutChip("SCREENS: ${layout.dsLayout ?: "auto"}") {
                // auto (fit the column) first, then every melonDS arrangement.
                val list = listOf<String?>(null) + PadLayout.DS_LAYOUTS
                val i = list.indexOf(layout.dsLayout)
                onEdit(layout.copy(dsLayout = list[(i + 1) % list.size]))
            }
        }
        LayoutChip("SKIN: ${skin.label.uppercase()}") { onSkin(skin.next()) }
        // 2.1: the most-downloaded store emulator's layout, one tap. DS keeps its own until its reference is measured.
        if (!isDs) LayoutChip("MY BOY LAYOUT") { onEdit(PadLayout.myBoy(landscape)); onSkin(PadSkin.OUTLINE) }
        if (isDs) LayoutChip("SUPERNDS LAYOUT") { onEdit(PadLayout.default(landscape, nds = true)); onSkin(PadSkin.OUTLINE) }
        if (!isDs) LayoutChip("ORIGINAL PAD") { onEdit(PadLayout.legacy(landscape)); onSkin(PadSkin.CLASSIC) }
        LayoutChip("RESET", onClick = onReset)
        Text(
            if (selected == null) "DRAG A BUTTON · TAP TO PICK" else "DRAG TO MOVE",
            fontFamily = Gen3.PixelFont, fontSize = 8.sp, color = Pc.Gold, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun LayoutChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    com.ironmonone.app.gen3.Gen3Button(label, accent = accent, onClick = onClick)
}
