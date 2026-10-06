package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The FILE bar's live state, one per app: Play is the only screen that shows it. Kept off PlayScreen, whose composable
 * sits at ART's verifier limit, and read by the three views (the dock pads under it, the window steps below it and
 * locks, the HUD trims its panels' tops).
 */
object FileBar {
    /** The bar's height, in dp: chips a thumb can hit (44dp) and a little air. */
    const val BAR_DP = 52
    /** The gap under the bar where a window or a panel steps down to. */
    const val UNDER_DP = BAR_DP + 6

    var open by mutableStateOf(false)
        private set
    var sheet by mutableStateOf<BarGroup?>(null)

    /** The floating window's lock. Not a setting: it is locked whenever Play opens (Blake, 2026-10-05). */
    var floatLocked by mutableStateOf(true)
    /** The last touch on an unlocked window, for its idle lock. */
    var floatTouchedAt by mutableLongStateOf(0L)

    /** The toast's line, set by Play. */
    var say: (String) -> Unit = {}

    /** What a tap on the game is weighed against (TapZone); set by the host every frame it is composed. */
    @Volatile var tap: TapInputs? = null

    fun set(v: Boolean) { open = v; if (!v) sheet = null }
    fun toggle() = set(!open)
    fun close() = set(false)

    /** Play left: nothing open, the window locked for the next game. */
    fun reset() { open = false; sheet = null; floatLocked = true; tap = null }

    /** What the tap rules need about the game on screen, in the game view's terms. */
    class TapInputs(
        val allowed: Boolean,
        val console: TapZone.Console,
        /** The view's viewport in fractions (DsDock); null for the whole view. */
        val viewport: TapZone.Rect?,
        /** The pad as drawn over the game (landscape), or null where it is not over the game. */
        val pad: PadLayout?,
        val skin: PadSkin,
        val density: Float,
        /** The docked DS tracker's width over the right of the view, in pixels; the pad keeps left of it. */
        val dockCoverPx: Float,
    )

    /** A tap that reached the game view: the bar opens or closes when it was in the zone. True when it did. */
    fun onScreenTap(x: Float, y: Float, viewW: Float, viewH: Float): Boolean {
        val t = tap ?: return false
        if (!t.allowed) return false
        val picture = TapZone.picture(t.console, viewW, viewH, t.viewport)
        val topOnly = (t.console as? TapZone.Console.Ds)?.topOnly == true
        val controls = t.pad?.let { p ->
            val d = t.density
            PadGeometry.rects(p, (viewW - t.dockCoverPx) / d, viewH / d, landscape = true, skin = t.skin).values.flatten()
                .map { b -> TapZone.Rect(b.l * d, b.t * d, b.r * d, b.b * d) }
                // "1 screen" draws the frame twice the size about the top centre, and the touch arrives in the view's
                // own pixels: a control on screen is at half its distance from that point there.
                .map { r -> if (topOnly) TapZone.Rect((r.l + viewW / 2) / 2, r.t / 2, (r.r + viewW / 2) / 2, r.b / 2) else r }
        } ?: emptyList()
        val dead = TapZone.DEAD_DP * t.density * (if (topOnly) 0.5f else 1f)
        val since = android.os.SystemClock.uptimeMillis() - lastPadReleaseAt
        if (!TapZone.opens(x, y, picture, controls, dead, padKeysHeld(), since)) return false
        toggle()
        return true
    }
}

/** The views VIEW offers, from the two settings that hold them (the HUD rides on the floating window's setting). */
internal fun currentBarView(): BarView = when {
    TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING && TrackerHud.ENABLED && TrackerOptions.trackerHud -> BarView.HUD
    TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING -> BarView.FLOATING
    TrackerOptions.landscapeTracker == LandscapeTracker.HIDDEN -> BarView.HIDDEN
    else -> BarView.DOCKED
}

/** [v] into the settings, saved: the same two settings Tracker Setup's "Landscape tracker" sets. */
internal fun chooseBarView(v: BarView) {
    TrackerOptions.landscapeTracker = when (v) {
        BarView.DOCKED -> LandscapeTracker.DOCKED
        BarView.FLOATING, BarView.HUD -> LandscapeTracker.FLOATING
        BarView.HIDDEN -> LandscapeTracker.HIDDEN
    }
    TrackerOptions.trackerHud = TrackerHud.ENABLED && v == BarView.HUD
    TrackerOptions.save()
}

/**
 * Tracker Setup and the screens it opens, for this game: the gear's callbacks, kept in one place so Tracker Setup and
 * the bar's TRACKER sheet open the same screens. Each is the screen's own opening; whoever calls it closes itself first.
 */
internal class TrackerLinks(
    val speciesName: (Int) -> String,
    val marks: StatMarks,
    val onCleared: () -> Unit,
    val onSetup: () -> Unit,
    val onRules: () -> Unit,
    val onCoverage: () -> Unit,
    val onStats: (() -> Unit)?,
    val onTrainers: (() -> Unit)?,
    val onBattleDetails: (() -> Unit)?,
    val onCatchRates: (() -> Unit)?,
    val onNotebook: (() -> Unit)?,
    val onHeals: (() -> Unit)?,
    val onTimeMachine: (() -> Unit)?,
    val onPastRuns: (() -> Unit)?,
    val onStatistics: (() -> Unit)?,
    val onEvoData: (() -> Unit)?,
    val onTrackedPokemon: (() -> Unit)?,
    val onTourney: (() -> Unit)?,
    val onColorTheme: (() -> Unit)?,
    val showTimerToggle: Boolean,
    val showBadgeOptions: Boolean,
    val gameBoy: Boolean,
    val ds: Boolean,
    val ivPotential: (() -> String)?,
) {
    fun reach() = TrackerReach(
        notebook = onNotebook != null, stats = onStats != null, trainers = onTrainers != null, battle = onBattleDetails != null,
        catchRates = onCatchRates != null, heals = onHeals != null, timeMachine = onTimeMachine != null, pastRuns = onPastRuns != null,
        statistics = onStatistics != null, evo = onEvoData != null, tracked = onTrackedPokemon != null, tourney = onTourney != null,
        theme = onColorTheme != null,
    )
}

/** Tracker Setup from [TrackerLinks]: every screen it opens closes it first, as it always did. */
@Composable
internal fun TrackerGearDialog(links: TrackerLinks, onDismiss: () -> Unit) {
    fun close(f: (() -> Unit)?): (() -> Unit)? = f?.let { { onDismiss(); it() } }
    val filesDir = LocalContext.current.applicationContext.filesDir
    val runSettingsName = remember {
        runCatching { PrepStore(filesDir).let { s -> if (s.session().isRun) s.loadLastRun()?.second else null } }.getOrNull()
    }
    TrackerGearDialog(
        speciesName = links.speciesName, marks = links.marks, onCleared = links.onCleared,
        onRules = close(links.onRules)!!, onCoverage = close(links.onCoverage)!!,
        onStats = close(links.onStats), onTrainers = close(links.onTrainers), onBattleDetails = close(links.onBattleDetails),
        onCatchRates = close(links.onCatchRates), onNotebook = close(links.onNotebook), onHeals = close(links.onHeals),
        onTimeMachine = close(links.onTimeMachine), onPastRuns = close(links.onPastRuns), onStatistics = close(links.onStatistics),
        onEvoData = close(links.onEvoData), onTrackedPokemon = close(links.onTrackedPokemon), onTourney = close(links.onTourney),
        showTimerToggle = links.showTimerToggle, onColorTheme = close(links.onColorTheme), showAutoThemes = true,
        runSettingsName = runSettingsName, showBadgeOptions = links.showBadgeOptions, gameBoy = links.gameBoy,
        ivPotential = links.ivPotential, ds = links.ds, onDismiss = onDismiss,
    )
}

/** Everything the bar does, handed over by Play in one piece. */
internal class FileBarActions(
    /** Not over clean view or the layout editor. */
    val allowed: Boolean,
    val ctx: BarContext,
    val attempt: Int,
    val tap: FileBar.TapInputs,
    // FILE
    val slot: Int,
    val onSlot: (Int) -> Unit,
    val slotHas: (Int) -> Boolean,
    val onSave: () -> Unit,
    val onLoad: () -> Unit,
    val onStates: () -> Unit,
    val speeds: List<String>,
    val speedNow: String,
    val onSpeed: (String) -> Unit,
    val muted: Boolean,
    val onMute: () -> Unit,
    val onRestart: () -> Unit,
    val onRewind: (Boolean) -> Unit,
    // NEW
    val beforeNewRun: () -> Unit,
    val onNewRun: () -> Unit,
    // TRACKER
    val links: TrackerLinks?,
    // VIEW
    val dsTopOnly: Boolean,
    val onScreens: (Boolean) -> Unit,
    val onClean: () -> Unit,
    val onLayout: () -> Unit,
    val padForced: Boolean,
    val onPad: (Boolean) -> Unit,
    // TOOLS
    val facecam: Boolean,
    val onCam: () -> Unit,
    val streamOn: Boolean,
    val onStream: () -> Unit,
    val raLabel: String,
    val raOn: Boolean,
    val onAchievements: () -> Unit,
    val cheatsLabel: String,
    val onCheats: () -> Unit,
    val onRouteLog: () -> Unit,
    val onInject: () -> Unit,
    // SETTINGS, HOME
    val onEmulator: () -> Unit,
    val onLeave: () -> Unit,
    /**
     * More of the stream's own lines, under its address on the TOOLS sheet while it is on: the place the streamer pack
     * (feat/stream) adds its wired USB option. Null draws nothing.
     */
    val streamExtra: (@Composable () -> Unit)? = null,
)

private val CYAN = Color(0xFF3FE0FF)
private val BAR_FILL = Color.Black.copy(alpha = 0.6f)
private val SHEET_FILL = Color(0xFF0E1216).copy(alpha = 0.94f)
private val BTN_FILL = Color.White.copy(alpha = 0.07f)
private val BTN_EDGE = Color.White.copy(alpha = 0.24f)
private val TEXT = Color(0xFFECEDEE)
private val HINT = Color(0xFFB4B8C2)
private val RED_TEXT = Color(0xFFFF7A7A)

/** The bar, its sheets, its first-time hints, and the dialogs it opens that outlive it. Drawn over everything in Play. */
@Composable
internal fun FileBarHost(a: FileBarActions) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("file_bar", android.content.Context.MODE_PRIVATE) }
    val density = LocalDensity.current.density
    SideEffect {
        FileBar.tap = a.tap
        // The pad as drawn, for the Tracker HUD to keep clear of (TrackerHud.kt).
        TrackerHud.pad = a.tap.pad; TrackerHud.padSkin = a.tap.skin
        TrackerHud.ds = (a.tap.console as? TapZone.Console.Ds)?.topOnly == false
    }
    DisposableEffect(Unit) { onDispose { FileBar.reset() } }
    // Clean view and the layout editor take the screen: the bar goes with them.
    LaunchedEffect(a.allowed) { if (!a.allowed) FileBar.close() }
    androidx.activity.compose.BackHandler(enabled = FileBar.open) { if (FileBar.sheet != null) FileBar.sheet = null else FileBar.close() }
    // The dialogs the TRACKER sheet opens that Tracker Setup used to hold for itself: the sheet is gone by the time they show.
    var favorites by remember { mutableStateOf(false) }
    var quotes by remember { mutableStateOf(false) }
    var gacha by remember { mutableStateOf(false) }
    if (favorites) FavoritesEditorDialog { favorites = false }
    if (quotes) DeathQuotesDialog { quotes = false }
    if (gacha) GachaMonScreen(GachaMonStart()) { gacha = false }

    // The first-time hints: where the bar is, once, and how it closes, once.
    var openHint by remember { mutableStateOf(!prefs.getBoolean("hint_open", false)) }
    var closeHint by remember { mutableStateOf(false) }
    LaunchedEffect(openHint) { if (openHint) { kotlinx.coroutines.delay(7000); openHint = false } }
    LaunchedEffect(FileBar.open) {
        if (!FileBar.open) { closeHint = false; return@LaunchedEffect }
        if (openHint || !prefs.getBoolean("hint_open", false)) { openHint = false; prefs.edit().putBoolean("hint_open", true).apply() }
        if (!prefs.getBoolean("hint_close", false)) {
            prefs.edit().putBoolean("hint_close", true).apply()
            closeHint = true
            kotlinx.coroutines.delay(5000)
            closeHint = false
        }
    }
    if (!a.allowed) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        var origin by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.size(0.dp).onGloballyPositioned { origin = it.boundsInRoot().topLeft })
        if (openHint && !FileBar.open) {
            // Over the top of the game, where the tap goes.
            val g = TrackerHud.game
            val left = g?.let { (it.left - origin.x) / density } ?: 0f
            val gw = g?.let { it.width / density } ?: w
            val top = g?.let { (it.top - origin.y) / density } ?: 0f
            Box(Modifier.offset { IntOffset((left * density).roundToInt(), ((top + 12f) * density).roundToInt()) }.width(gw.dp),
                contentAlignment = Alignment.TopCenter) {
                BarHint(FileBarCopy.OPEN_HINT)
            }
        }
        if (!FileBar.open) return@BoxWithConstraints
        val chipX = remember { mutableStateMapOf<BarGroup, Float>() }
        val groups = FileBarMap.groups(a.ctx)
        Bar(groups, chipX, origin, density)
        if (closeHint && FileBar.sheet == null) {
            Box(Modifier.fillMaxWidth().padding(top = (FileBar.BAR_DP + 8).dp, end = 8.dp), contentAlignment = Alignment.TopEnd) {
                BarHint(FileBarCopy.CLOSE_HINT)
            }
        }
        FileBar.sheet?.takeIf { it in groups }?.let { g ->
            val width = minOf(if (g == BarGroup.TRACKER) 400f else 330f, w - 16f)
            val x = (chipX[g] ?: 8f).coerceIn(8f, maxOf(8f, w - width - 8f))
            Column(
                Modifier.offset { IntOffset((x * density).roundToInt(), ((FileBar.BAR_DP + 4) * density).roundToInt()) }
                    .width(width.dp).heightIn(max = (h - FileBar.BAR_DP - 12f).coerceAtLeast(120f).dp)
                    .background(SHEET_FILL).border(1.dp, if (g == BarGroup.NEW) Shell.accent else BTN_EDGE)
                    .systemGestureExclusion()
                    .verticalScroll(rememberScrollState()).padding(12.dp)
                    .semantics { contentDescription = g.label },
            ) {
                Text(g.line, color = HINT, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Sheet(g, a) { which -> when (which) { "favorites" -> favorites = true; "quotes" -> quotes = true; "gacha" -> gacha = true } }
            }
        }
    }
}

/** The words of the bar that are not item labels, kept apart so the tests can read them. */
internal object FileBarCopy {
    const val OPEN_HINT = "Tap the top of the game for the menu"
    const val CLOSE_HINT = "Tap the top of the game again, or ✕, to close."
    const val CLOSE = "Close the file bar"
    const val BACK = "Scroll the bar left"
    const val FORWARD = "Scroll the bar right"
    const val PORTRAIT_VIEWS = "Where it sits in landscape. In portrait it is under the pad."
    const val STREAM_HEAD = "The stream is on. Open a link in a browser on the PC that runs OBS."
    const val OFF = "Off"
    const val ON = "On"
    fun newRunLine(attempt: Int) = if (attempt > 0) "Attempt ${attempt + 1} starts." else ""
}

@Composable
private fun BarHint(text: String) {
    Text(
        text, color = TEXT, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp).background(Color.Black.copy(alpha = 0.72f)).border(1.dp, CYAN.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** The bar itself: the group chips, scrolling with an arrow at each end while there is more, and the X fixed at the right. */
@Composable
private fun Bar(groups: List<BarGroup>, chipX: MutableMap<BarGroup, Float>, origin: Offset, density: Float) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().padding(end = dsDockClearance()).height(FileBar.BAR_DP.dp).background(BAR_FILL)
            .systemGestureExclusion().semantics { contentDescription = "File bar" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scroll.canScrollBackward) Arrow("◀", FileBarCopy.BACK) { scope.launch { scroll.animateScrollTo((scroll.value - scroll.viewportSize * 7 / 10).coerceAtLeast(0)) } }
        else Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (g in groups) Chip(g, Modifier.onGloballyPositioned { chipX[g] = (it.boundsInRoot().left - origin.x) / density })
        }
        if (scroll.canScrollForward) Arrow("▶", FileBarCopy.FORWARD) { scope.launch { scroll.animateScrollTo(scroll.value + scroll.viewportSize * 7 / 10) } }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(BTN_EDGE))
        Box(
            Modifier.size(FileBar.BAR_DP.dp).clickable(role = Role.Button, onClickLabel = FileBarCopy.CLOSE) { FileBar.close() }
                .semantics { contentDescription = FileBarCopy.CLOSE },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = TEXT, fontSize = 20.sp) }
    }
}

@Composable
private fun Arrow(glyph: String, spoken: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 36.dp, height = Shell.touchTarget).clickable(role = Role.Button) { onClick() }.semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = TEXT, fontSize = 14.sp) }
}

/** One group's chip: FILE outlined red, NEW filled red (Blake, 2026-10-03: the file and new buttons red), the open one lit. */
@Composable
private fun Chip(g: BarGroup, modifier: Modifier) {
    val on = FileBar.sheet == g
    val fill = when { g == BarGroup.NEW -> Shell.accent; on -> Color.White.copy(alpha = 0.16f); else -> BTN_FILL }
    val edge = when { g == BarGroup.FILE || g == BarGroup.NEW -> Shell.accent; on -> CYAN; else -> BTN_EDGE }
    Box(
        modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).background(fill).border(1.dp, edge)
            .clickable(role = Role.Button) { FileBar.sheet = if (on) null else g }
            .semantics { stateDescription = if (on) "Open" else "Closed" }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            g.label, color = if (g == BarGroup.FILE) RED_TEXT else TEXT, fontSize = 14.sp, letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
        )
        if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(CYAN))
    }
}

/** A sheet's lines, two to a row unless a line is wide. */
@Composable
private fun Sheet(g: BarGroup, a: FileBarActions, open: (String) -> Unit) {
    if (g == BarGroup.NEW) { NewRunSheet(a); return }
    if (g == BarGroup.TOOLS && a.streamOn) StreamLine(a)
    if (g == BarGroup.VIEW && !a.ctx.landscape && a.ctx.tracked) Text(FileBarCopy.PORTRAIT_VIEWS, color = HINT, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
    val items = FileBarMap.items(g, a.ctx)
    var i = 0
    while (i < items.size) {
        val item = items[i]
        val next = items.getOrNull(i + 1)
        if (item.wide || next == null || next.wide) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Line(item, a, open, Modifier.weight(1f)) }
            i++
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Line(item, a, open, Modifier.weight(1f)); Line(next, a, open, Modifier.weight(1f))
            }
            i += 2
        }
    }
}

/** Opens something over the game: the bar closes first, so the screen it opens is not under a sheet. */
private fun go(f: (() -> Unit)?) { FileBar.close(); f?.invoke() }

@Composable
private fun Line(item: BarItem, a: FileBarActions, open: (String) -> Unit, modifier: Modifier) {
    val c = a.ctx
    val off = FileBarMap.offWhy(item, c)
    if (off != null) { Btn(item.label, modifier, trailing = FileBarCopy.OFF, dim = true) { a.ctxSay(off) }; return }
    val nav = LocalShellNav.current
    val links = a.links
    when (item) {
        BarItem.SLOT -> Column(modifier) {
            Label(if (a.slotHas(a.slot)) "Save slot" else "Save slot (slot ${a.slot} is empty)")
            for (row in (1..StateSlots.COUNT).chunked(4)) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in row) Seg("$n", a.slot == n, Modifier.weight(1f)) { a.onSlot(n) }
            }
        }
        BarItem.SAVE -> Btn(item.label, modifier) { a.onSave() }
        BarItem.LOAD -> Btn(item.label, modifier) { go(a.onLoad) }
        BarItem.STATES -> Btn(item.label, modifier) { go(a.onStates) }
        BarItem.SPEED -> Column(modifier) {
            Label(item.label)
            for (row in a.speeds.chunked(5)) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in row) Seg(s, a.speedNow == s, Modifier.weight(1f)) { a.onSpeed(s) }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        BarItem.SOUND -> Toggle(item.label, !a.muted, modifier) { a.onMute() }
        BarItem.RESTART -> Btn(item.label, modifier) { go(a.onRestart) }
        BarItem.REWIND -> HoldButton(item.label, a.onRewind, modifier)
        BarItem.SETUP -> Btn(item.label, modifier, lead = true) { go(links?.onSetup) }
        BarItem.RULES -> Btn(item.label, modifier) { go(links?.onRules) }
        BarItem.FAVORITES -> Btn(item.label, modifier) { FileBar.close(); open("favorites") }
        BarItem.NOTEBOOK -> Btn(item.label, modifier) { go(links?.onNotebook) }
        BarItem.COVERAGE -> Btn(item.label, modifier) { go(links?.onCoverage) }
        BarItem.TRAINERS -> Btn(item.label, modifier) { go(links?.onTrainers) }
        BarItem.HEALS -> Btn(item.label, modifier) { go(links?.onHeals) }
        BarItem.BATTLE -> Btn(item.label, modifier) { go(links?.onBattleDetails) }
        BarItem.CATCH -> Btn(item.label, modifier) { go(links?.onCatchRates) }
        BarItem.EVO -> Btn(item.label, modifier) { go(links?.onEvoData) }
        BarItem.STATS -> Btn(item.label, modifier) { go(links?.onStats) }
        BarItem.TIME -> Btn(item.label, modifier) { go(links?.onTimeMachine) }
        BarItem.TRACKED -> Btn(item.label, modifier) { go(links?.onTrackedPokemon) }
        BarItem.PAST -> Btn(item.label, modifier) { go(links?.onPastRuns) }
        BarItem.STATISTICS -> Btn(item.label, modifier) { go(links?.onStatistics) }
        BarItem.TOURNEY -> Btn(item.label, modifier) { go(links?.onTourney) }
        BarItem.GACHA -> Btn(item.label, modifier) { FileBar.close(); open("gacha") }
        BarItem.THEME -> Btn(item.label, modifier) { go(links?.onColorTheme) }
        BarItem.QUOTES -> Btn(item.label, modifier) { FileBar.close(); open("quotes") }
        BarItem.CLEAR -> Ask(item.label, "Marks, notes, routes, moves and abilities for this run. Clear them?", "Yes, clear", modifier) {
            // The run's own counters stay (StatMarks.clear), as in Tracker Setup.
            links?.let { l -> l.marks.clear(keepRunCounters = true); l.onCleared(); a.ctxSay("Tracked data cleared.") }
        }
        BarItem.VIEWS -> Column(modifier) {
            Label(item.label)
            val now = currentBarView()
            val views = FileBarMap.views(c)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in views) Seg(v.label, now == v, Modifier.weight(1f)) { chooseBarView(v); a.ctxSay("Tracker: ${v.label}.") }
            }
        }
        BarItem.SCREENS -> Column(modifier) {
            Label(item.label)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Seg("1 screen", a.dsTopOnly, Modifier.weight(1f)) { a.onScreens(true) }
                Seg("2 screens", !a.dsTopOnly, Modifier.weight(1f)) { a.onScreens(false) }
            }
        }
        BarItem.HUD_MINE -> Toggle(item.label, TrackerOptions.hudShowMine, modifier) { TrackerOptions.hudShowMine = !TrackerOptions.hudShowMine; TrackerOptions.save() }
        BarItem.HUD_REST -> Toggle(item.label, TrackerOptions.hudShowRest, modifier) { TrackerOptions.hudShowRest = !TrackerOptions.hudShowRest; TrackerOptions.save() }
        BarItem.SEE_THROUGH -> Column(modifier) {
            val solid = TrackerOptions.floatingSolid
            Label("See-through: $solid% solid")
            androidx.compose.material3.Slider(
                value = solid.toFloat(),
                onValueChange = { TrackerOptions.floatingSolid = FloatingSeeThrough.clamp(kotlin.math.round(it).toInt()) },
                onValueChangeFinished = { TrackerOptions.save() },
                valueRange = FloatingSeeThrough.MIN.toFloat()..FloatingSeeThrough.SOLID.toFloat(),
                steps = (FloatingSeeThrough.SOLID - FloatingSeeThrough.MIN) / FloatingSeeThrough.STEP - 1,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = CYAN, activeTrackColor = CYAN, inactiveTrackColor = BTN_EDGE,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "See-through, percent solid" },
            )
        }
        BarItem.CLEAN -> Btn(item.label, modifier) { go(a.onClean) }
        BarItem.LAYOUT -> Btn(item.label, modifier) { go(a.onLayout) }
        BarItem.PAD -> Toggle(item.label, a.padForced, modifier) { a.onPad(!a.padForced) }
        BarItem.CAM -> Toggle(item.label, a.facecam, modifier) { a.onCam() }
        BarItem.STREAM -> Toggle(item.label, a.streamOn, modifier) { a.onStream() }
        BarItem.ACHIEVEMENTS -> Btn(a.raLabel, modifier, trailing = if (a.raOn) FileBarCopy.ON else null) { go(a.onAchievements) }
        BarItem.CHEATS -> Btn(a.cheatsLabel, modifier) { go(a.onCheats) }
        BarItem.ROUTE_LOG -> Btn(item.label, modifier) { go(a.onRouteLog) }
        BarItem.INJECT -> Btn(item.label, modifier) { a.onInject() }
        BarItem.EMULATOR -> Btn(item.label, modifier) { go(a.onEmulator) }
        BarItem.CONTROLS -> Btn(item.label, modifier) { leave(a, nav?.openControls) }
        BarItem.BACKUP -> Btn(item.label, modifier) { leave(a, nav?.openBackup) }
        BarItem.STREAM_SETTINGS -> Btn(item.label, modifier) { leave(a, nav?.openStreamSettings) }
        BarItem.HOME -> Btn(item.label, modifier, lead = true) { leave(a, nav?.openHome) }
        BarItem.MY_GAMES -> Btn(item.label, modifier) { leave(a, nav?.openMyGames) }
        BarItem.PATCHED -> Btn(item.label, modifier) { leave(a, nav?.openPatched) }
        BarItem.YOUR_STATS -> Btn(item.label, modifier) { leave(a, nav?.openStats) }
        BarItem.LEAVE -> Btn(item.label, modifier) { go(a.onLeave) }
        BarItem.NEW_RUN -> Unit   // the NEW sheet is its own (NewRunSheet)
    }
}

private fun FileBarActions.ctxSay(s: String) = FileBar.say(s)

/** To another tab: not while the game is still loading, which would freeze the app (PlayLoading, rc33 audit P0-11). */
private fun leave(a: FileBarActions, to: (() -> Unit)?) {
    if (PlayLoading.holds()) { a.ctxSay(PlayLoading.WAIT); return }
    FileBar.close()
    to?.invoke()
}

/** NEW: the new-run question itself, with the words the dialog has always used (NewRunQuestion), and the attempt to come. */
@Composable
private fun NewRunSheet(a: FileBarActions) {
    val words = rememberNewRunWords(a.beforeNewRun)
    if (words == null) { Text(NewRunCopy.NOT_A_RUN, color = TEXT, fontSize = 15.sp); return }
    Text(words.title, color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    Text(words.body + FileBarCopy.newRunLine(a.attempt).let { if (it.isEmpty()) "" else " $it" }, color = TEXT, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Text(words.save, color = HINT, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn(words.yes, Modifier.weight(1f), danger = true) { FileBar.close(); a.onNewRun() }
        Btn("Keep playing", Modifier.weight(1f)) { FileBar.close() }
    }
}

/**
 * The stream while it is on, at the head of TOOLS (the File menu's stream rows before 2026-10-06): a head line, the
 * stream's own rows ([FileBarActions.streamExtra]: StreamLinks.kt's Wi-Fi and USB links, each with its Copy), and Clean
 * view for the capture.
 */
@Composable
private fun StreamLine(a: FileBarActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(FileBarCopy.STREAM_HEAD, color = HINT, fontSize = 13.sp)
        a.streamExtra?.invoke()
        Btn("Clean view", Modifier.fillMaxWidth().padding(top = 6.dp)) { go(a.onClean) }
    }
}

@Composable
private fun Label(text: String) { Text(text, color = TEXT, fontSize = 15.sp) }

/** A sheet button: 48dp tall at least, its label wrapping rather than cut. */
@Composable
private fun Btn(
    label: String, modifier: Modifier = Modifier, trailing: String? = null, lead: Boolean = false, danger: Boolean = false,
    dim: Boolean = false, onClick: () -> Unit,
) {
    Row(
        modifier.heightIn(min = Shell.touchTarget).background(if (danger) Shell.accent else BTN_FILL)
            .border(1.dp, when { danger -> Shell.accent; lead -> CYAN; else -> BTN_EDGE })
            .clickable(role = Role.Button) { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (dim) HINT else TEXT, fontSize = 15.sp, fontWeight = if (lead || danger) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f, fill = false))
        trailing?.let { Text(it, color = if (it == FileBarCopy.ON) CYAN else HINT, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp)) }
    }
}

/** A switch line: the label, and On or Off at its end. */
@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = Shell.touchTarget).background(if (on) CYAN.copy(alpha = 0.12f) else BTN_FILL).border(1.dp, if (on) CYAN else BTN_EDGE)
            .clickable(role = Role.Switch) { onClick() }
            .semantics { stateDescription = if (on) FileBarCopy.ON else FileBarCopy.OFF }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TEXT, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (on) FileBarCopy.ON else FileBarCopy.OFF, color = if (on) CYAN else HINT, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** One choice of a picker: lit while chosen. */
@Composable
private fun Seg(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = Shell.touchTarget).background(if (on) CYAN.copy(alpha = 0.14f) else BTN_FILL).border(1.dp, if (on) CYAN else BTN_EDGE)
            .clickable(role = Role.RadioButton) { onClick() }
            .semantics { stateDescription = if (on) "Chosen" else "Not chosen" }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = TEXT, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2) }
}

/** A line that asks before it acts. */
@Composable
private fun Ask(label: String, question: String, yes: String, modifier: Modifier, onYes: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    if (!asking) { Btn(label, modifier) { asking = true }; return }
    Column(modifier) {
        Text(question, color = TEXT, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn(yes, Modifier.weight(1f), danger = true) { asking = false; onYes() }
            Btn("Cancel", Modifier.weight(1f)) { asking = false }
        }
    }
}

/**
 * Rewind's line: it acts while HELD, down starts and up stops. It waits 150 ms before acting, and a finger that moves past
 * the touch slop in that time is the sheet's scroll, not a press (2026-09-27, audit). The press handler outlives a
 * recomposition, so it reads the callback of the composition it is in: hardcore turned on with the menu open left it
 * rewinding with the check of the moment the menu opened (rc32 audit P3 #56).
 */
@Composable
internal fun HoldButton(label: String, onHold: (Boolean) -> Unit, modifier: Modifier) {
    var held by remember { mutableStateOf(false) }
    val hold by rememberUpdatedState(onHold)
    Box(
        modifier.heightIn(min = Shell.touchTarget).background(if (held) CYAN.copy(alpha = 0.3f) else BTN_FILL).border(1.dp, BTN_EDGE)
            .semantics { contentDescription = "$label, hold" }
            .holdUnlessScrolled({ held = true; hold(true) }, { held = false; hold(false) })
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text("◀◀ $label (hold)", color = TEXT, fontSize = 15.sp) }
}

private fun Modifier.holdUnlessScrolled(onDown: () -> Unit, onUp: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            // null = still held and still, so it is a press; false = lifted or moved.
            val gaveUp = withTimeoutOrNull(150L) {
                while (true) {
                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed || c.isConsumed || (c.position - down.position).getDistance() > slop) break
                }
                false
            }
            if (gaveUp != null) return@awaitEachGesture
            onDown()
            try { waitForUpOrCancellation() } finally { onUp() }
        }
    }

