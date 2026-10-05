package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * How much height the tracker has where it is drawn: the docked column, the floating window, the space above the
 * DS bottom screen. Null where nobody measured it, which keeps each caller's own default.
 */
val LocalTrackerRoom = compositionLocalOf<Dp?> { null }

/** True inside the floating window, whose title bar reads the attempt: the panels then leave it out (2026-10-02). */
val LocalAttemptInTitle = compositionLocalOf { false }

/**
 * Both cards at once, or one with SEE FOE and SEE MINE (Blake, 2026-10-02: where the tracker is short, "a small
 * button, see foe and click that and you can see the opponent", "or you can have it set to auto switch to foe when
 * in battle and then after the battle it switches back"). The swap and its auto swap are the panels' own, as in
 * portrait; this only decides when there is room to skip them.
 */
object TrackerRoom {
    /** Your card, the opponent's and the strip under them: Black 2's docked column held all three at 393dp. */
    const val BOTH_DP = 340

    fun stackBoth(room: Dp?): Boolean = room == null || room >= BOTH_DP.dp
}

/**
 * The landscape File band: the chips over the game, centred, with an arrow at each end while there is more to
 * scroll to, and an X at the far right that closes it (Blake, 2026-10-02: "needs to be centered, needs to inform
 * with arrows that it scrolls and then needs a close x at the far right of the band").
 *
 * The chips go round (LoopingRow, Blake, 2026-10-03: "They both should have infinite scroll"), and the arrows move
 * through the loop. The X stays put outside it. The band ends at a docked DS tracker's left edge: it ran on under
 * the tracker, and the last chips, MENU among them, could not be reached even scrolled to the end ("So I can't get
 * to the menu button at all").
 */
@Composable
internal fun LandscapeMenuBand(modifier: Modifier, onClose: () -> Unit, chips: @Composable RowScope.() -> Unit) {
    val loop = rememberLoopRowState()
    val scope = rememberCoroutineScope()
    Row(
        modifier.padding(end = dsDockClearance()).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val overflows = loop.overflows
        if (overflows) BandArrow("◀", "Scroll the menu left", enabled = loop.canBack) {
            scope.launch { loop.page(forward = false) }
        }
        // A short row sits in the middle instead of at the left.
        LoopingRow(loop, Modifier.weight(1f).padding(vertical = 6.dp), gap = 6.dp, center = true, content = chips)
        if (overflows) BandArrow("▶", "Scroll the menu right", enabled = loop.canForward) {
            scope.launch { loop.page(forward = true) }
        }
        Box(
            Modifier.size(Shell.touchTarget).clickable(role = Role.Button) { onClose() }
                .semantics { contentDescription = "Close the menu" },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun BandArrow(glyph: String, spoken: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 36.dp, height = Shell.touchTarget)
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White.copy(alpha = if (enabled) 0.95f else 0.3f), fontSize = 14.sp) }
}

/**
 * The tracker's top right corner in landscape: a menu button of three lines, and under it the attempt, FILE, the DS's
 * one or two screens and hiding the tracker (Blake, 2026-10-02: "the top right should just be a little arrow you can
 * click that will reveal the file button and that it's 2 screen mode", then "maybe 3 horizontal line menu icon instead
 * of file"). It sits at the end of the tracker's own first row, the
 * battle banner or the bar with the route, so it costs the tracker no height; the row of chips it replaces did, and on a
 * narrow column it ran off the edge.
 */
@Composable
internal fun TrackerCornerMenu(
    attempt: Int,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    /** The DS's "1 screen" and "2 screens", or null on any other console. */
    dsTopOnly: Boolean?,
    onScreens: () -> Unit,
    /** Null where the tracker cannot be hidden this way (the floating window). */
    onHide: (() -> Unit)?,
    /** The floating window's way back beside the game; null when docked. */
    onDock: (() -> Unit)? = null,
    /** Docked: make it a window over the game instead; null when it already is one. */
    onFloat: (() -> Unit)? = null,
    /** Off screen (ScreenTapMenu): bring it back beside the game. */
    onShow: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    // A Kaizo IronMON run's count only, as on the tracker (ironmonRunInPlay): a library game read the last run's (2026-10-02).
    val run = ironmonRunInPlay(attempt)
    Box {
        MenuLinesButton(if (run) "Attempt $attempt, file menu and screens" else "File menu and screens") { open = !open }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (run) Text(
                "ATTEMPT $attempt", color = Pc.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // The floating window's own lines when its row is narrow: Tracker Setup and the text's tap (WindowBarFit).
            for (item in LocalWindowMenuItems.current) DropdownMenuItem(text = { Text(item.label) }, onClick = { open = false; item.action() })
            // Red, as Blake asked (2026-10-03: "I want the file button to be red"): the colour that reads on the dark menu.
            DropdownMenuItem(text = { Text(if (menuOpen) "Hide the file menu" else "File", color = Shell.dangerOnNight) }, onClick = { open = false; onMenu() })
            if (dsTopOnly != null) DropdownMenuItem(
                text = { Text(if (dsTopOnly) "2 screens" else "1 screen (top only)") },
                onClick = { open = false; onScreens() },
            )
            if (onShow != null) DropdownMenuItem(text = { Text("Show the tracker") }, onClick = { open = false; onShow() })
            if (onFloat != null) DropdownMenuItem(text = { Text("Float the tracker (move and resize it)") }, onClick = { open = false; TrackerOptions.trackerHud = false; onFloat() })
            // The Tracker HUD beside Dock and Float (TrackerHud.kt): from the dock or a hidden tracker it floats, as a HUD.
            if (TrackerHud.ENABLED && onFloat != null) DropdownMenuItem(text = { Text(TrackerHud.MENU_HUD) }, onClick = { open = false; TrackerOptions.trackerHud = true; onFloat(); TrackerOptions.save() })
            // From the window, the HUD; from the HUD, the window.
            LocalHudMenu.current?.let { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { open = false; item.action() }) }
            if (onDock != null) DropdownMenuItem(text = { Text("Dock the tracker beside the game") }, onClick = { open = false; onDock() })
            if (onHide != null) DropdownMenuItem(text = { Text("Hide the tracker") }, onClick = { open = false; onHide() })
        }
    }
}

/** Lines the floating window adds to its menu when its one row has no room for them (WindowBarFit.top). */
val LocalWindowMenuItems = androidx.compose.runtime.compositionLocalOf<List<HudMenuItem>> { emptyList() }

/**
 * The tracker's menu when the tracker is off screen: hidden, or on a second display (Blake, 2026-10-02: "a screen tap
 * displays another menu button on the top left, tapping away from the button and on the screen makes it disappear").
 * A tap on the game shows it and the next one hides it (ScreenTap, PlayUiState.onScreenTap); a dark disc keeps the
 * three lines readable over a white title screen.
 */
@Composable
internal fun ScreenTapMenu(
    ui: PlayUiState,
    /** The pad as drawn over the game, so the button steps below a control in the corner; null with no pad. */
    pad: PadLayout?,
    padSkin: PadSkin,
    /** The game has a tracker and nothing else holds the top of the screen (the File band, clean view, the layout editor). */
    allowed: Boolean,
    trackerOpen: Boolean,
    trackerOnSecond: Boolean,
    /** The DS screens as drawn (ScreenTap.DsLayout); null on other consoles or with the top screen alone. */
    dsLayout: ScreenTap.DsLayout?,
    attempt: Int,
    dsTopOnly: Boolean?,
    onScreens: () -> Unit,
    onFile: () -> Unit,
    onShow: () -> Unit,
) {
    // Off screen: on the other display, or neither floating nor open beside the game (PlayScreen's tracker pane).
    val off = trackerOnSecond || (TrackerOptions.landscapeTracker != LandscapeTracker.FLOATING &&
        !(trackerOpen && (TrackerOptions.landscapeTracker == LandscapeTracker.DOCKED || ui.trackerPeek)))
    val enabled = allowed && off
    androidx.compose.runtime.SideEffect {
        // The pad as drawn, for the Tracker HUD to keep clear of (TrackerHud.kt).
        TrackerHud.pad = pad; TrackerHud.padSkin = padSkin; TrackerHud.ds = dsLayout != null
        ui.tapMenuEnabled = enabled
        ui.tapDsLayout = dsLayout
        if (!enabled && ui.tapMenuShown) ui.tapMenuShown = false
    }
    if (!enabled || !ui.tapMenuShown) return
    // No pointer modifier on this full-size box, so a touch anywhere but the button still reaches the game view.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = ScreenTap.menuTop(
            pad?.let { PadGeometry.rects(it, maxWidth.value, maxHeight.value, landscape = true, skin = padSkin).values.flatten() } ?: emptyList(),
            margin = 6f, size = PcMin.TOUCH_DP.toFloat(),
        )
        Box(Modifier.offset(x = 6.dp, y = top.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.6f))) {
            TrackerCornerMenu(
                attempt = attempt, menuOpen = false, onMenu = { ui.tapMenuShown = false; onFile() },
                dsTopOnly = dsTopOnly, onScreens = onScreens, onHide = null,
                // On the other display it is already showing, and floats there with it.
                onShow = if (trackerOnSecond) null else { { ui.tapMenuShown = false; onShow() } },
                onFloat = if (trackerOnSecond) null else { {
                    ui.tapMenuShown = false
                    TrackerOptions.landscapeTracker = LandscapeTracker.FLOATING; TrackerOptions.save()
                    // A hidden tracker floated is open again (rc32 audit P2 #22): the window drew while Play still held it
                    // hidden, so the second screen stayed empty when turned on and CAM put a camera in both places. No
                    // peek is left behind to override a later choice of Hidden.
                    onShow(); ui.trackerPeek = false
                } },
            )
        }
    }
}

/**
 * NEW in the landscape File strip, filled red like portrait's NEW RUN (Gen3Button with accent): it ends the run (Blake,
 * 2026-10-03: "the new button should be red as well"). In size and shape it is the strip's other chips (PlayScreen's
 * OverlayChip), whose call it replaces one for one, so Play's method does not grow.
 */
@Composable
internal fun NewRunChip(onClick: () -> Unit) {
    val g = com.ironmonone.app.gen3.Gen3
    Box(
        Modifier
            .background(g.FrameDark.copy(alpha = 0.45f))
            .padding(1.dp)
            .background(Shell.accent)
            .clickable(role = Role.Button) { onClick() }
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Shell.label("NEW"), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Shell.onAccent, maxLines = 1, softWrap = false)
    }
}

/** Three lines in the tracker's pill, in a touch box of [PcMin.TOUCH_DP] like the tracker's other buttons. */
@Composable
private fun MenuLinesButton(spoken: String, onClick: () -> Unit) {
    val color = Pc.Text
    Box(
        Modifier.size(PcMin.TOUCH_DP.dp).clickable(role = Role.Button) { onClick() }.semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(TrackerLook.inset).padding(horizontal = 8.dp, vertical = 7.dp)) {
            androidx.compose.foundation.Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
                val stroke = 2.dp.toPx()
                for (i in 0..2) {
                    val y = stroke / 2 + i * (size.height - stroke) / 2
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
    }
}
