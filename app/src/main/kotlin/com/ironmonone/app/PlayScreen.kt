@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ironmonone.app

import android.view.KeyEvent
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File

/**
 * The game, in our app, on the mGBA libretro core via LibretroDroid.
 *
 * Portrait: game on top, tracker + pad below (the classic phone layout).
 * Landscape (Blake's fold-open/rotate gesture): the game owns the whole screen,
 * translucent controls float over it, the tracker is an overlay card with its own
 * HIDE, and a connected Bluetooth controller sweeps every on-screen button away.
 */
@Composable
fun PlayScreen(
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    /**
     * Landscape drives the LAYOUT - game beside the tracker, controls floating
     * translucently over the game. fullscreen only decides whether the app's
     * own chrome is hidden. They were the same flag, so pressing MENU or Back
     * dropped landscape back into the portrait stack: a game frame sized
     * fillMaxWidth().aspectRatio(3:2), which on a wide tablet computes TALLER
     * than the screen and clips to a thin strip, with the solid portrait pad
     * below it.
     */
    landscape: Boolean = false,
    onExitFullscreen: () -> Unit = {},
    /** CLEAN VIEW: the capture layout. Owned by the activity, which hides its own chrome for it. */
    clean: Boolean = false,
    onClean: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { PrepStore(context) }

    // Re-reads only when a new run lands, not on every recomposition: the
    // getter chains through lastrun.txt, and this composable recomposes on
    // every tracker poll.
    var gameKeyForRom by remember { mutableStateOf(0) }
    // The session: the run, or a library ROM the player chose. Everything
    // below reads the game through it; nothing here knows which it is
    // except the few places gated on isRun (NEW RUN, attempts) and on
    // tracked (the pollers and the panel).
    val session = remember(gameKeyForRom) { store.session() }
    val rom = session.file

    // The core follows the ROM, never a hardcoded console (brief 15.5). Both are
    // Lemuroid's pinned builds, not buildbot nightlies: the nightly mGBA SIGSEGVed
    // inside LibretroDroid 0.13.2's loadGameFromPath (null core, fault addr 0x28).
    // The game descriptor. The kind is whatever the Run tab last randomized;
    // the file extension is only a fallback for a run with no record, and
    // GBA is the last resort because that is what a bare .gba is.
    val kind = session.kind
    val platform = session.platform
    // The DS has a second screen and a touch layer, and the GBA does not.
    // That is the ONLY thing this name may gate: which panel, which pad,
    // the screen-layout chip. Every decision about the RUN reads `view`,
    // every decision about the MACHINE reads `platform`. If you find
    // yourself writing `if (dsScreens)` for anything else, it belongs on
    // one of those two instead.
    val dsScreens = platform == com.ironmonone.core.Platform.NDS
    val corePath = remember(platform) {
        File(context.applicationInfo.nativeLibraryDir, platform.core)
    }

    if (!corePath.exists()) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            Text("Emulator core missing from this build.",
                color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (!rom.exists()) {
        // A failed NEW RUN lands here, so say WHY rather than implying the
        // user simply has not randomized yet.
        val failure = remember(gameKeyForRom) { store.lastRunError() }
        // Each case says what happened and has a button to the next step (PlayNothing, 2026-09-30).
        PlayNothing(modifier, session.isRun, session.title, failure)
        return
    }

    var retro by remember { mutableStateOf<GLRetroView?>(null) }
    // Speed and mute are the player's desk setup, not part of a run, so they
    // are restored rather than reset. NEW RUN is the most-pressed button in
    // the app and a seed can die in two minutes; going back to 1x-and-audible
    // on every attempt meant re-muting and re-pressing turbo each time.
    // Per-game settings (GameSettings): speed, mute, DS screen mode, pane width.
    val prefs0 = remember(session.id) { store.gameSettings(session) }
    // The run's attempt and seed, read once per run, never in composition (RunIds, rc32 audit P2 #56, P3 #55).
    val runNow = rememberRunIds(store, gameKeyForRom)
    var speed by remember(session.id) { mutableStateOf(prefs0.speed) }
    // Slow motion divisor (1, 2, 4) and the rewind history. Both are session
    // state; rewind is refused on a tracked game (RewindBuffer.allowed).
    var slow by remember(session.id) { mutableStateOf(1) }
    val rewindAllowed = RewindBuffer.allowed(session)
    val rewind = remember(session.id) { RewindBuffer.forPlatform(platform) }
    var rewinding by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    // Bumping this recreates the game view, which reboots the core into the current
    // ROM file - the reload mechanism behind NEW RUN.
    var gameKey by remember { mutableStateOf(0) }
    // False while a NEW RUN reboot is tearing the old core down. The old view MUST
    // be destroyed and given time to stop its GL thread before a new view calls
    // native create: skipping that is the SIGSEGV family this app was born with
    // (loadGameFromPath fault 0x28, step() fault 0x48, and the release-build crash
    // on 2026-08-30 - all the same race).
    // Off, too, for a Play screen opened while a new run is still being made (Play's own NEW RUN goes on after the screen
    // that started it is left): it waits for the run instead of booting the one being replaced (rc33 audit P1 #22).
    var gameActive by remember { mutableStateOf(!NewRunGuard.inProgress) }
    LaunchedEffect(Unit) {
        if (!gameActive && NewRunGuard.inProgress) { status = NewRunGuard.BUSY; NewRunGuard.awaitDone(); gameKeyForRom++; gameActive = true }
    }

    // The status line is a NOTIFICATION, not a label: it clears itself.
    //
    // Nothing ever set it back to null, so the last thing that happened -
    // usually "New run (seed ...)" - sat over the game for the rest of the
    // session.
    //
    // Gated on gameActive so it does NOT clear mid-operation: randomizing
    // takes ~40 seconds with the core down, and "Rolling a new seed..." is the
    // only sign the app is doing anything. gameActive is false for exactly
    // that stretch, so the timer starts once the run is back up.
    //
    // Keyed on both, so flipping gameActive re-arms it for the message that
    // was set while busy.
    LaunchedEffect(status, gameActive) {
        if (status != null && gameActive) {
            // Long enough to read a 16-digit seed, short enough to not linger.
            kotlinx.coroutines.delay(8000)
            status = null
        }
    }
    var trackerState by remember { mutableStateOf<com.ironmonone.tracker.TrackerState?>(null) }
    var ndsState by remember {
        mutableStateOf<com.ironmonone.tracker.nds.NdsTrackerState?>(null)
    }
    var ndsTrackerRef by remember {
        mutableStateOf<com.ironmonone.tracker.nds.NdsTracker?>(null)
    }
    val scope = rememberCoroutineScope()
    val controllerOn by Controllers.connected
    // The pad is hidden whenever a controller is detected; this forces it back.
    var padForced by remember { mutableStateOf(store.padForced()) }
    val streamClean = clean
    var streamOn by remember { mutableStateOf(com.ironmonone.app.stream.StreamHub.running) }
    val showPad = (!controllerOn || padForced) && !streamClean

    // Immersive while fullscreen: system bars leave with the chrome, and come back
    // the moment the phone rotates upright again.
    val hostView = LocalView.current
    LaunchedEffect(fullscreen) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(window, hostView)
        if (fullscreen) {
            ctl.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * B-to-Run, Instant (brief section 9): the wild-battle action menu is a 2x2 grid
     * whose cursor clamps at the edges, so RIGHT then DOWN lands on RUN from any
     * position, then A confirms. Input-only - no memory writes, so it works
     * identically on vanilla and every Nat. Dex release. The game's own flee formula
     * still runs. Trainer battles never trigger this (the tracker gates on wild).
     */
    // Declared here, above flee(), which reads it for the live battle gate.
    var trackerRef by remember { mutableStateOf<com.ironmonone.tracker.GbaTracker?>(null) }
    // The Game Boy trackers' move table, for the enemy card's run-wide moves (trackerRef is Gen 3 only).
    var gbLookup by remember(session.id) { mutableStateOf<((Int) -> com.ironmonone.tracker.MoveRow?)?>(null) }
    var gbNames by remember(session.id) { mutableStateOf<((Int) -> String)?>(null) }
    // The Game Boy tracker's answers to the panel's lookups (GbLookups), which trackerRef gives on Gen 3.
    var gbRef by remember(session.id) { mutableStateOf<com.ironmonone.tracker.GbLookups?>(null) }
    val panelLookups = remember(session.id) { PanelLookups({ trackerRef }, { gbRef }) }
    var gearDialog by remember { mutableStateOf(false) }
    var rulesDialog by remember { mutableStateOf(false) }
    // The run, through one interface, whichever tracker is producing it.
    // Only one of the two states is ever non-null on a given platform.
    val view: com.ironmonone.tracker.RunView? = ndsState ?: trackerState

    /** The moment the last battle ended; flee refuses for 400 ms after it. */
    var battleEndedAt by remember { mutableStateOf(0L) }

    /** Guards against overlapping flee sequences while B is held. */
    var fleeing by remember { mutableStateOf(false) }

    /** The live action-menu read (ActionMenuGate) of Gen 3 or the Game Boy tracker; null where the tracker has none. */
    fun actionMenuUp(): Boolean? =
        trackerRef?.isChoosingActionInWild() ?: (gbRef as? com.ironmonone.tracker.ActionMenuGate)?.isChoosingActionInWild()

    fun flee() {
        // Already running: holding B used to start a fresh sequence on every
        // repeat, so several overlapping RIGHT/DOWN/A bursts queued up and
        // walked the player several steps once the battle was over.
        if (fleeing) return
        if (android.os.SystemClock.uptimeMillis() - battleEndedAt < 400) return
        scope.launch {
            fleeing = true
            try {
                // Re-checked before EVERY tap, not once at the start.
                //
                // This types RIGHT, DOWN, A into the battle menu to reach RUN.
                // The sequence used to fire blind on fixed delays, so if the
                // battle ended early - a successful flee is often quicker than
                // the remaining taps - the leftovers landed in the OVERWORLD,
                // where DOWN walks the player and A talks to whatever is in
                // front of them. Synthetic input must never outlive the screen
                // it was aimed at.
                // GBA: the LIVE action-menu state, not the last poll. The poll
                // lags the game by up to 700 ms, and that lag is exactly the
                // window a mashed B used to slip through and walk the player.
                // The GBA tracker has a LIVE action-menu gate; where there is
                // no live gate (DS) the last polled view is the best available.
                fun stillWild(): Boolean =
                    actionMenuUp() ?: (view?.let { it.inBattle && it.isWildBattle } ?: false)

                suspend fun tap(key: Int): Boolean {
                    if (!stillWild()) return false
                    com.swordfish.libretrodroid.LibretroDroid
                        .onKeyEvent(0, KeyEvent.ACTION_DOWN, key)
                    kotlinx.coroutines.delay(80)
                    com.swordfish.libretrodroid.LibretroDroid
                        .onKeyEvent(0, KeyEvent.ACTION_UP, key)
                    kotlinx.coroutines.delay(120)
                    return true
                }

                if (!tap(KeyEvent.KEYCODE_DPAD_RIGHT)) return@launch
                if (!tap(KeyEvent.KEYCODE_DPAD_DOWN)) return@launch
                tap(KeyEvent.KEYCODE_BUTTON_A)
            } finally {
                fleeing = false
            }
        }
    }

    // The favorites, the way the PC tracker's new-game screen lists them: as many as this game's PC tracker keeps (three on
    // Gen 1 to 3, four or five on DS, nine on a Nat. Dex build), in the order typed. In a Kaizo IronMON run's lab, also the
    // ball holding one the mode lets you take (FavoriteBall), worked out again once the tracker is up.
    val favoriteLine = remember(session.id, trackerRef) { FavoriteBall.shown(store, session, trackerRef, context.filesDir) }
    var facecam by remember { mutableStateOf(false) }
    var muted by remember(session.id) { mutableStateOf(prefs0.muted) }
    // CHEATS. Per game, never on a tracked game (CheatStore.allowed). Sent
    // to the core whole - reset then every enabled code - on every change
    // and once the core is up, so the list on disk and the list in the
    // core never disagree.
    // RETROACHIEVEMENTS. The client is native (rcheevos); this holds what
    // the screen shows. Never loaded on an IronMON run (isRun): a randomized
    // ROM has no set anyway, and the run must stay unquestionable. Hardcore is the player's choice and gates the app's own
    // helpers below (raHardcore).
    val raStore = remember { RetroAchievements.Store(File(context.filesDir, "ra/session.txt")) }
    var raSummary by remember { mutableStateOf(RetroAchievements.Summary()) }
    var raList by remember { mutableStateOf<List<RetroAchievements.Achievement>>(emptyList()) }
    var raBusy by remember { mutableStateOf<String?>(null) }
    var raError by remember { mutableStateOf<String?>(null) }
    var raDialog by remember { mutableStateOf(false) }
    val raHardcore = RetroAchievements.hardcoreOn(raSummary) && !session.isRun
    fun raRefresh() {
        raSummary = runCatching { RetroAchievements.parseSummary(com.swordfish.libretrodroid.LibretroDroid.cheevosSummary()) }.getOrDefault(RetroAchievements.Summary())
        raList = runCatching { RetroAchievements.parseAchievements(com.swordfish.libretrodroid.LibretroDroid.cheevosAchievements()) }.getOrDefault(emptyList())
    }
    val cheatsAllowed = CheatStore.allowed(session) && !raHardcore
    // The game's cheats are always read; only applying them waits on cheatsAllowed. Read only when allowed, a stale
    // Nuzlocke flag hid them, and adding one then saved the short list over them (rc33 audit P1).
    var cheats by remember(session.id) { mutableStateOf(store.cheats.load(session.id)) }
    var cheatsDialog by remember { mutableStateOf(false) }
    // LAYOUT EDITOR. One layout per orientation and console; the pad renders
    // from it (FreePad) and the editor changes it in place, saved on DONE.
    val layoutKey = PadLayout.key(landscape, platform)
    // Each orientation's layout, kept through a rotation, and the editor's edit over them (PadLayouts, rc32 audit P3 #48).
    val padLayouts = remember { PadLayouts(store.layouts) }
    var padLayout by padLayouts.at(layoutKey, landscape)
    var editingLayout by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<PadLayout.Element?>(null) }
    var padSkin by remember { mutableStateOf(store.padSkin()) }
    // EMULATOR SETTINGS: the console's core options. Read once per platform
    // for the view's creation; changes go to the core live and to disk.
    var coreValues by remember(platform) { mutableStateOf(store.coreOptions.effective(platform)) }
    var settingsDialog by remember { mutableStateOf(false) }
    fun shaderFor(name: String?): com.swordfish.libretrodroid.ShaderConfig = when (name) {
        "Sharp" -> com.swordfish.libretrodroid.ShaderConfig.Sharp
        "LCD" -> com.swordfish.libretrodroid.ShaderConfig.LCD
        "CRT" -> com.swordfish.libretrodroid.ShaderConfig.CRT
        "Upscale" -> com.swordfish.libretrodroid.ShaderConfig.CUT()
        else -> com.swordfish.libretrodroid.ShaderConfig.Default
    }
    // Core variables from the settings: every option except the filter, which is the view's.
    fun coreVariables(): List<com.swordfish.libretrodroid.Variable> =
        CoreOptions.coreVariables(platform, coreValues).map { (k, v) -> com.swordfish.libretrodroid.Variable(k, v) }
    // Saved with the activity: a DSi system file picked while Android ended the app behind the picker was dropped (N #11).
    var importSystemFileName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val systemFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        val name = importSystemFileName ?: return@rememberLauncherForActivityResult
        importSystemFileName = null
        if (uri == null) return@rememberLauncherForActivityResult
        // systemDirectory is filesDir: the cores look for these names there. Streamed and checked (SystemFiles).
        scope.launch { status = SystemFiles.import(context.filesDir, name) { context.contentResolver.openInputStream(uri) } }
    }
    var menuOpen by remember { mutableStateOf(false) }
    // Confirmations, pickers and the toast's action live in one holder, off this method's registers.
    val ui = remember { PlayUiState() }
    // The layout editor edits a copy. It used to save on DONE and on Back alike
    // and RESET deleted the saved file on the spot, so there was no way to
    // walk away from a bad edit (2026-09-27, audit). Now DONE keeps, CANCEL
    // restores what was there on entry, Back asks when anything changed, and
    // nothing reaches disk before DONE.
    fun startLayoutEdit() {
        padLayouts.startEdit(layoutKey, landscape); ui.skinBefore = padSkin
        selectedElement = null; menuOpen = false; editingLayout = true
    }
    fun finishLayoutEdit(keep: Boolean) {
        // Every orientation the edit reached: saved (a default as no file), or put back as it was.
        padLayouts.finishEdit(keep)
        if (!keep) ui.skinBefore?.let { if (it != padSkin) { padSkin = it; store.setPadSkin(it) } }
        ui.confirmKeepLayout = false; editingLayout = false; selectedElement = null
    }
    // Back closes the FILE menu before it leaves anything (2026-09-27, audit).
    // Declared before the editor's handler, so the editor's wins when both are on.
    androidx.activity.compose.BackHandler(enabled = menuOpen) { menuOpen = false }
    LaunchedEffect(menuOpen) { if (!menuOpen) ui.moreOpen = false }
    androidx.activity.compose.BackHandler(enabled = editingLayout) {
        if (padLayouts.changed() || padSkin != ui.skinBefore) ui.confirmKeepLayout = true
        else finishLayoutEdit(false)
    }
    val layoutToolbar: @Composable (Modifier) -> Unit = { m ->
        LayoutToolbar(
            layout = padLayout, selected = selectedElement, landscape = landscape,
            isDs = platform == com.ironmonone.core.Platform.NDS,
            onEdit = { padLayout = it },
            onReset = { padLayout = PadLayout.default(landscape, nds = platform == com.ironmonone.core.Platform.NDS, gb = platform == com.ironmonone.core.Platform.GBC); selectedElement = null },
            onDone = { finishLayoutEdit(true) }, gb = platform == com.ironmonone.core.Platform.GBC,
            skin = padSkin, onSkin = { padSkin = it; store.setPadSkin(it) },
            onCancel = { finishLayoutEdit(false) },
            barAtBottom = ui.layoutBarBottom,
            onMoveBar = if (landscape) ({ ui.layoutBarBottom = !ui.layoutBarBottom }) else null,
            modifier = m,
        )
    }
    // Reset, then every code in order, in one emulation-thread job (rc32 audit P3 #49); none unless allowed.
    fun applyCheats() { retro?.let { r -> runCatching { r.setCheats(CheatStore.forCore(cheats, platform, cheatsAllowed)) } } }
    // Again whenever they become allowed or not: hardcore switched on mid-game re-applied them with the allowance of
    // the moment before, so cheats ran on under hardcore while the button said CHEATS OFF (rc33 audit P1).
    // Only once the core is up (its first frame): the core-up block below makes the first application itself.
    LaunchedEffect(cheatsAllowed) { if (retro != null && ui.coreUp === retro) applyCheats() }
    var saveSlot by remember { mutableStateOf(1) }
    var slotsVersion by remember { mutableStateOf(0) }
    var statesDialog by remember { mutableStateOf(false) }

    // Per-species stat notes, the tracker's core mechanic. Reset per run, since a
    // new seed re-randomizes every base stat and old notes would mislead. Read off the main thread (PlayMarks).
    val statMarks = rememberStatMarks(store, session) ?: return
    // Auto Pokemon Themes (AutoThemes.lua afterProgramDataUpdate): Gen 3 and Game Boy leads.
    // A DS game follows the DS tracker's own (PokemonThemeManager.lua): its playerPokemon.
    val autoThemeParty = trackerState?.party?.map { it.mon.species to it.mon.isEgg }
    val autoThemeDs = ndsState?.let(AutoTheme::dsPokemon)
    LaunchedEffect(autoThemeParty, autoThemeDs, TrackerOptions.autoPokemonThemes) {
        if (autoThemeDs != null) AutoTheme.onDs(autoThemeDs, TrackerOptions.autoPokemonThemes)
        else AutoTheme.onGba(autoThemeParty ?: emptyList(), TrackerOptions.autoPokemonThemes)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { AutoTheme.release(); dsView.clear(); gbaView.clear() } }
    SpriteIsMeHost(retro, platform)   // Play as your Pokemon: the one line the Play screen knows of it (SpriteIsMe.kt)

    // ---- Route info (InfoScreen ROUTE_INFO) and the carousel's route line ----
    // Open Book: RandomizerLog.Data.Routes from this run's log, read off the main thread and kept for the run (OpenBookRoutes).
    val openBook = remember(session.id, gameKeyForRom) { OpenBookRoutes() }
    /** The route info screen's data: [raw] is a RouteData key (the look-up lists those), or null for where the player is. */
    fun routeSource(raw: Int?): RouteInfoSource? {
        val t = trackerRef ?: return null
        val here = trackerState?.mapId
        val rawId = raw ?: here?.let { t.routeKey(it) } ?: return null
        val mapId = t.mapIdFromRouteKey(rawId)
        val vanilla = t.routeEncountersRaw(rawId)
        val name = t.routeNameRaw(rawId) ?: trackerState?.routeName ?: ""
        if (vanilla.isEmpty() && name.isBlank()) return null
        val set = trackerState?.badgeSet
        return RouteInfoSource(
            mapId = mapId, name = name, vanilla = vanilla,
            safari = if (t.isSafariMap(mapId)) statMarks.safariSeen(mapId) else emptyList(),
            tracked = { area -> statMarks.seenOnRouteArea(mapId, area) },
            logged = { area -> openBook.icons(t, store, session.kind, set, mapId, area) },
        )
    }
    // TrackerScreen CarouselItems ROUTE_INFO: in a wild battle the battle's area when RouteData has
    // it; otherwise Walking (showEarlyRouteEncounters). (area, seen, total)
    val routeCarousel: Triple<String?, Int, Int> = run {
        val t = trackerRef
        val m = trackerState?.mapId
        if (t == null || m == null) return@run Triple(null, 0, 0)
        val areas = t.routeEncounters(m)
        val battleArea = trackerState?.encounterArea?.takeIf { trackerState?.isWildBattle == true && it in areas }
        val area = battleArea ?: "Walking".takeIf { it in areas }
        if (area == null) Triple(null, 0, 0)
        else Triple(area, statMarks.seenOnRouteArea(m, area).size, areas[area].orEmpty().size)
    }
    var marksVersion by remember { mutableStateOf(0) }   // bump to redraw cells
    // Encounter counts and last-seen levels are the run's (StatMarks), written by the
    // reference's rules (EncounterBook). They used to live here and died with the screen.
    val encounterBook = remember(session.id, gameKeyForRom) { EncounterBook.of(if (session.isRun) "run/" + store.lastSeedText() else session.id) }
    LaunchedEffect(trackerState) {
        if (encounterBook.onGba(statMarks, EncounterBook.gbaUpdate(trackerState), save = Demo.mode == null)) marksVersion++
    }
    LaunchedEffect(ndsState) {
        if (encounterBook.onDs(statMarks, EncounterBook.dsUpdate(ndsState), save = Demo.mode == null)) marksVersion++
        if (session.isRun) ndsState?.let { s -> if (PcHeals.observeDsSurvival(statMarks, Integer.bitCount(s.badges), PcHeals.limitForLastRunCached(), s.leagueBeaten)) marksVersion++ }
    }
    // The DS main screen's pause on move effectiveness after each new opponent.
    val dsFxReady = rememberDsEffectivenessReady(ndsState)
    var lastCountedSpecies by remember { mutableStateOf(-1) }
    var trackerOpen by remember { mutableStateOf(true) }
    // The last touch anywhere on the play area, for the landscape chip strip's fade.
    // Watched at the ROOT: a watcher inside the pad overlay was never hit under the
    // DS touch surface, so in DS landscape no tap ever woke the strip (2026-09-07).
    var lastTouch by remember { mutableStateOf(android.os.SystemClock.uptimeMillis()) }
    // STREAMING. Clean view is the capture layout: nothing on screen but
    // the game (both DS screens in the core's own layout), so scrcpy's
    // window is a clean crop. Back leaves it. The tracker for the stream
    // is the web page the hub serves, not this screen.
    LaunchedEffect(Unit) {
        com.ironmonone.app.stream.StreamHub.page = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.assets.open("stream/tracker.html").bufferedReader().readText() }
                .getOrDefault("<p>tracker.html missing from the build</p>")
        }
    }
    var dsTopOnly by remember(session.id) { mutableStateOf(prefs0.dsTopOnly) }
    // Tracker pane width, dragged by the tracker's left edge. Narrower pane = bigger game.
    val windowWidthDp = androidx.compose.ui.platform.LocalConfiguration
        .current.screenWidthDp.toFloat()
    val windowHeightDp = androidx.compose.ui.platform.LocalConfiguration
        .current.screenHeightDp.toFloat()

    // The tracker column's share of the window and the floating window's frame: kept through a rotation, and saved
    // with no read of them here, so a drag recomposes the pane alone (PaneSizes, rc32 audit P2 #46, #56). One writer
    // for every per-game setting, so no toggle can forget to persist.
    val panes = remember(session.id) { PaneSizes(prefs0.trackerFraction, prefs0.floatFrame) }
    LaunchedEffect(speed, muted, dsTopOnly, session.id) { panes.saveWith(speed, muted, dsTopOnly) { store.saveGameSettings(session, it) } }
    val enemySpecies = view?.enemySpeciesId ?: -1
    // The opponent's notebook: a locked DS opponent's while one is locked (DsViewState); counting stays live.
    val notebookSpecies = viewedFoeSpecies(ndsState, trackerState, enemySpecies)
    val enemyMarks = remember(notebookSpecies, marksVersion) {
        if (notebookSpecies > 0) statMarks.of(notebookSpecies) else IntArray(StatMarks.COUNT)
    }
    // DataHelper.lua:398: this battle kind's count, wild or trainer, at most 999; the DS
    // tracker's "Total seen" counts both. "Last seen" is from before this battle.
    val enemyEncounters = remember(notebookSpecies, marksVersion, trackerState?.isWildBattle, ndsState != null) {
        when {
            notebookSpecies <= 0 -> 0
            ndsState != null -> statMarks.totalEncounters(notebookSpecies)
            else -> statMarks.encounters(notebookSpecies, trackerState?.isWildBattle == true).coerceAtMost(999)
        }
    }
    val enemyLastSeen = remember(notebookSpecies, marksVersion) { if (notebookSpecies > 0) statMarks.lastLevelSeen(notebookSpecies) else null }
    val enemyNote = remember(notebookSpecies, marksVersion) {
        GhostCard.note(trackerState?.enemy, if (notebookSpecies > 0) statMarks.noteFor(notebookSpecies) else "")
    }
    var noteDialog by remember { mutableStateOf(false) }
    val side = remember { SideScreenState() }
    /** The randomizer log open full screen (the game-over screen's Inspect the log), or null. */
    var logViewerFile by remember { mutableStateOf<java.io.File?>(null) }

    // Hardware keys route to the game ONLY while this screen exists and no
    // text dialog is open. Without the gate a Bluetooth keyboard could never
    // type into any field in the app: dispatchKeyEvent consumed Z/X/arrows/
    // Enter app-wide and fed them to a core that might not even be on screen.
    // Keyed on whether the dialog is ACTUALLY on screen, not on the intent to
    // open it. The dialog renders only when an enemy is decoded, so keying on
    // `noteDialog` alone left the flag false with no dialog to raise it again
    // - and from that moment every hardware key was dead with no way back
    // except leaving the tab, which reboots the core.
    // The note editor is for the enemy on screen, or for the species an info screen asked about.
    var noteForSpecies by remember { mutableStateOf<Int?>(null) }
    val noteSpecies = noteForSpecies ?: notebookSpecies
    val noteDialogVisible = noteDialog && noteSpecies > 0
    // Any dialog with a text field takes the keyboard back from the game:
    // the note, the cheats (code entry) and the settings (link address).
    // Also the tracked-Pokemon and log searches, and the layout editor's typeable
    // steppers: a Bluetooth keyboard typed into the game there (2026-09-27, audit).
    val textDialogOpen = noteDialogVisible || cheatsDialog || settingsDialog || raDialog ||
        side.trackedPokemon || logViewerFile != null || editingLayout
    DisposableEffect(textDialogOpen) {
        KeyBindings.routeToGame = !textDialogOpen
        onDispose { KeyBindings.routeToGame = false }
    }
    // If the enemy disappears mid-edit the dialog unmounts; drop the intent
    // too so a later tap can open it again.
    LaunchedEffect(enemySpecies) { if (enemySpecies <= 0 && noteForSpecies == null) noteDialog = false }
    // Fleeing is wild-only on BOTH consoles; a trainer battle never offers it.
    val inBattleNow = view?.inBattle == true
    // The reference snapshots the core when a battle begins
    // (Battle.beginNewBattle -> GameOverScreen.createTempSaveState) so the
    // game-over screen can offer "Retry the battle". Held in memory only, for
    // the current battle; a new battle replaces it, a new run drops it.
    var battleStartState by remember(session.id) { mutableStateOf<ByteArray?>(null) }
    // A DS run's latch starts fired when its end is already on record (GameOverLatch.forPlay, rc32 audit P2 #41).
    val gameOverLatch = remember(session.id) { GameOverLatch.forPlay(platform, store, session) }
    // TimeMachineScreen: a restore point every four minutes on a map, out of battle; five on a Game Boy game.
    val timeMachine = remember(session.id) { TimeMachine.forPlatform(platform) }
    // SeedLogger: the DS tracker's past runs, per game (SeedLogger.lua:233).
    val pastRunStore = remember(ndsState?.gameName, ndsState?.badgeSet) { ndsState?.let { pastRunStoreFor(it, store::pastRunsFile) } }
    // The run's, not the session id's: that is "run" for every run, so NEW RUN kept the last seed's (rc33 audit P1 #29).
    // From the time the run has been played (RunTimer.forRun, rc32 audit P2 #48).
    val runTimer = remember(session.id, gameKeyForRom) { RunTimer.forRun(session, runNow.attempt) }
    // TourneyTracker, HeartGold / SoulSilver only, keyed on the seed as the reference keys on the ROM hash.
    val tourney = remember { TourneyTracker(store.tourneyFile()) }
    LaunchedEffect(ndsState?.inBattle, ndsState?.badgeSet, ndsState?.mapId) {
        tourney.onRead(ndsState, ndsTrackerRef?.defeatedTrainers, gameOverLatch, runNow.seed.ifEmpty { session.id })?.let { status = it }
    }
    // What the stream page shows, only while the stream is on (StreamFeed, rc32 audit P3 #50, #51).
    com.ironmonone.app.stream.StreamFeed(streamOn, session, platform, runNow, statMarks, marksVersion, trackerState, ndsState, trackerRef, ndsTrackerRef, gameOverLatch)
    LaunchedEffect(session.id) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            if (Demo.mode != null) continue
            val inBattle = trackerState?.inBattle == true || ndsState?.inBattle == true
            val mapKnown = trackerState?.mapId != null || ndsState != null
            // The state is taken off the main thread (rc32 audit P2 #52).
            timeMachine.tick(System.currentTimeMillis(), TrackerOptions.restorePoints, inBattle, mapKnown, trackerState?.routeName, retro)
        }
    }
    LaunchedEffect(inBattleNow) {
        if (!inBattleNow) battleEndedAt = android.os.SystemClock.uptimeMillis()
        else {
            battleStartState = null   // never the last battle's while this one's is taken
            gameOverLatch.onBattleBegan()   // Battle.beginNewBattle: GameOverScreen.isDisplayed = false
            battleStartState = AutoSave.battleStart(retro)   // off the main thread (rc32 audit P2 #52)
            // The same snapshot keeps the auto slot fresh (AutoSave), so a crash mid-battle resumes at its start.
            val now = System.currentTimeMillis()
            battleStartState?.let { s -> AutoSave.of(context.filesDir, session).takeIf { Demo.mode == null && it.battleDue(now) }?.save(s, now, store.stateStamp(session)) }
        }
    }
    val wildBattleNow = view?.let { it.inBattle && it.isWildBattle } == true

    // Count an encounter once per arrival, not once per poll tick.
    // Persist what this enemy uses as it uses it, so the next encounter with
    // the species starts informed - the reference's Tracker.TrackMove.
    // The DS enemy's moves are already used-only (NdsTracker.usedOnly), so every one is a sighting; every opponent's on
    // the field, a double or triple battle's others too (DsFoeMoves).
    LaunchedEffect(DsFoeMoves.key(ndsState)) {
        if (DsFoeMoves.record(statMarks, ndsState)) marksVersion++
    }
    // Every opposing Pokemon's, the doubles partner's too (rc33 audit P1 #72).
    LaunchedEffect(trackerState?.enemyMovesThisBattle) {
        if (statMarks.addBattleMoves(trackerState?.enemyMovesThisBattle)) marksVersion++
    }

    // A battle script revealing an ability is the ONLY thing that unlocks
    // the enemy's ability line - the reference's TrackAbility rule.
    LaunchedEffect(trackerState?.abilitiesRevealed) {
        var changed = false
        trackerState?.abilitiesRevealed?.forEach { (sp, name) -> if (statMarks.revealAbility(sp, name)) changed = true }
        if (changed) marksVersion++
    }
    // Your own battlers' abilities, every battle, as the reference does.
    LaunchedEffect(trackerState?.ownAbilities) {
        var changed = false
        trackerState?.ownAbilities?.forEach { (sp, name) -> if (statMarks.revealAbility(sp, name)) changed = true }
        if (changed) marksVersion++
    }
    // The DS reference (Tracker.trackAbilityNote) also writes a newly tracked
    // ability into that species' note, unless the note already names it.
    LaunchedEffect(ndsState?.abilitiesRevealed) {
        var changed = false
        ndsState?.abilitiesRevealed?.forEach { (sp, name) ->
            if (statMarks.revealAbility(sp, name, max = 3)) {
                val note = statMarks.noteFor(sp)
                if (!note.contains(name, ignoreCase = true))
                    statMarks.setNote(sp, if (note.isEmpty()) name else "$note, $name")
                changed = true
            }
        }
        if (changed) marksVersion++
    }

    LaunchedEffect(enemySpecies) {
        if (enemySpecies > 0 && enemySpecies != lastCountedSpecies) {
            // Battle.lua:505: a Pokemon Tower ghost records nothing (GhostCard.recordsEncounter).
            // Its count and last level are EncounterBook's, above.
            val records = GhostCard.recordsEncounter(trackerState)
            // Battle.incrementEnemyEncounter: a WILD Pokemon is recorded against the
            // map and the encounter area it was met in (Tracker.TrackRouteEncounter),
            // only where RouteData has that area. Trainer Pokemon used to be recorded
            // too, which the reference never does (2026-09-28).
            // DS: Tracker.updateEncounterData is EncounterBook.onDs's, with the encounter count (rc33 audit P1 #80).
            // Never from a staged screenshot battle (Demo), which would write into this save's records.
            // Gen 3 only: the Game Boy references record no route encounters (Gen 2 reference
            // Battle.lua:522-525 has TrackRouteEncounter commented out), though their map is read now.
            trackerState?.takeIf { it.isWildBattle && records && Demo.mode == null && trackerRef != null }?.let { st ->
                st.mapId?.let { m ->
                    statMarks.seeOnRoute(m, enemySpecies)
                    val area = st.encounterArea
                    if (area != null && trackerRef?.routeEncounters(m)?.containsKey(area) == true)
                        statMarks.seeOnRouteArea(m, area, enemySpecies)
                    // Tracker.TrackSafariEncounter's record, kept with the run and shown on the route screen.
                    trackerRef?.takeIf { it.isSafariMap(m) }?.let { t ->
                        t.safariEncounters(t.rawMapId(m)).forEach { (sp, lv) -> if (statMarks.seeSafari(m, sp, lv)) marksVersion++ }
                    }
                }
            }
            lastCountedSpecies = enemySpecies
        } else if (enemySpecies <= 0) {
            lastCountedSpecies = -1
        }
    }

    // Card art, cached per run (a new ROM = new art).
    //
    // The bundled pack comes FIRST, which is what the reference does: it draws
    // PNGs indexed by pokemonID and never reads art out of the ROM. That is
    // the whole reason the Nat. Dex builds showed blank cards - no front-pic
    // table address is published for them, so the ROM decoder had nothing to
    // read. Vanilla keeps the ROM decoder because those ROMs DO publish a
    // front-pic table, so the card shows art from the ROM actually being
    // played. The pack would index correctly there too - vanilla shares this
    // numbering up to 411 - so that is a preference, not a constraint.
    val spriteCache = remember(gameKey) {
        HashMap<Int, androidx.compose.ui.graphics.ImageBitmap?>()
    }
    val spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap? = { sp ->
        spriteCache.getOrPut(sp) {
            // Gen 2's 251 ids are Gen 3's first 251, so Crystal draws from
            // the same pack with nothing added.
            val packed = if (platform == com.ironmonone.core.Platform.GBC ||
                trackerRef?.expandedSpeciesIds == true)
                PcAssets.gbaSprite(context, sp, trackerRef?.nameSet) else null
            packed ?: trackerRef?.sprite(sp)?.let { px ->
                android.graphics.Bitmap.createBitmap(
                    px, 64, 64, android.graphics.Bitmap.Config.ARGB_8888
                ).asImageBitmap()
            }
        }
    }

    // The tracker poller. Adaptive cadence for battery: 250ms only while a battle
    // needs live HP; 700ms in the overworld; fully paused when the app is not
    // visible (the emulator core itself also pauses with the lifecycle).
    // Gen 4 tracker (Platinum). Separate poller because the structures, the
    // address chain and the name sources are all different from GBA.
    LaunchedEffect(retro, platform, gameKey) {
        val r = retro ?: return@LaunchedEffect
        if (!session.tracked) return@LaunchedEffect
        if (platform != com.ironmonone.core.Platform.NDS) return@LaunchedEffect
        val reader = com.ironmonone.tracker.nds.NdsMemoryReader { addr, len ->
            r.readMemory(addr, len)
        }
        // Types, BST and abilities are randomized, so they come from the sidecar
        // the engine writes beside the ROM rather than from a bundled list.
        val sidecar = java.io.File(
            rom.parentFile, rom.nameWithoutExtension + ".species.tsv")
        var tracker: com.ironmonone.tracker.nds.NdsTracker? = null
        while (true) {
            val visible =
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!visible) { kotlinx.coroutines.delay(1000); continue }
            if (ndsState?.inBattle == true && tracker != null) {
                // In battle: full read every 250 ms, and the Gen 4 battle
                // message checked about every 2 frames in between.
                val t = tracker
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    repeat(8) { kotlinx.coroutines.delay(31); t?.pollAbilityTrigger() }
                }
            } else kotlinx.coroutines.delay(700)
            if (tracker == null) {
                tracker = kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.Default
                ) {
                    runCatching {
                        if (r.readMemory(0x02000000L, 4).isEmpty()) null
                        // Which DS game, from the cartridge header in RAM. A
                        // game with no map gets no tracker (and keeps probing)
                        // rather than Platinum's offsets read against it.
                        else com.ironmonone.tracker.nds.NdsGameMap.detect(reader)?.let { map ->
                            com.ironmonone.tracker.nds.NdsTracker(reader, sidecar, map)
                        }
                    }.getOrNull()
                }
                ndsTrackerRef = tracker
            }
            tracker?.let { t ->
                // The DS tracker's own FAINT_DETECTION, never the Gen 3 per-settings-file condition.
                t.lossCondition = TrackerOptions.dsLossCondition
                // tracker.hasRunEnded(): the latch has fired this run (and no Retry is pending).
                t.runEnded = !gameOverLatch.armed
                ndsState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { t.read() }.getOrNull()
                } ?: ndsState
                Demo.mode?.takeIf { it.startsWith("nds") }?.let { m -> ndsState = runCatching { Demo.nds(t, m) }.getOrNull() ?: ndsState }
                // The Nuzlocke ledger follows every poll here too, not only the ones a tracker panel draws (2026-09-30).
                NuzlockeTracking.observeNds(context.applicationContext.filesDir, ndsState)
                ndsState?.let { statMarks.noteDsProgress(it.progress) }
                KeptSave.observe(store, session, ndsState, runNow.attempt)
                if (ndsState != null) RunClock.tick(session.kind?.id?.takeIf { session.isRun }, { runNow.attempt }, gameActive && Demo.mode == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }
    }

    LaunchedEffect(retro, platform) {
        val r = retro ?: return@LaunchedEffect
        if (!session.tracked) return@LaunchedEffect
        if (platform == com.ironmonone.core.Platform.NDS) return@LaunchedEffect
        val reader = com.ironmonone.tracker.MemoryReader { addr, len -> r.readMemory(addr, len) }
        if (platform == com.ironmonone.core.Platform.GBC) {
            // Crystal: WRAM through the core's SYSTEM_RAM, tables from the
            // ROM file itself. Produces the same TrackerState as Gen 3;
            // trackerRef stays null, and the panel's lookups (move summaries,
            // learn levels, weight, evolution, weaknesses) come from gbRef.
            val romBytes = runCatching { rom.readBytes() }.getOrNull() ?: return@LaunchedEffect
            // Gen 2 (Crystal, Gold, Silver) or Gen 1 (Red, Blue, Yellow), picked from the header.
            val gbc = if (com.ironmonone.tracker.Gen2Map.forRom(romBytes) != null) com.ironmonone.tracker.GbcTracker(reader, romBytes) else null
            val gb1 = if (gbc == null) com.ironmonone.tracker.Gen1Tracker(reader, romBytes) else null
            val read: () -> com.ironmonone.tracker.TrackerState = { gbc?.read() ?: gb1!!.read() }
            gbLookup = { id -> gbc?.moveRowFor(id) ?: gb1?.moveRowFor(id) }
            gbNames = { id -> gbc?.speciesName(id) ?: gb1?.speciesName(id) ?: "#$id" }
            gbRef = gbc ?: gb1
            while (true) {
                val visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!visible) { kotlinx.coroutines.delay(1000); continue }
                kotlinx.coroutines.delay(if (trackerState?.inBattle == true) 250 else 700)
                gbc?.lossCondition = TrackerOptions.lossCondition; gb1?.lossCondition = TrackerOptions.lossCondition
                trackerState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { read() }.getOrNull()
                } ?: trackerState
                Demo.mode?.takeIf { it.startsWith("gb-") }?.let { m ->
                    trackerState = runCatching { if (gbc != null) Demo.gb2(gbc, m) else Demo.gb1(gb1!!, m) }.getOrNull() ?: trackerState
                }
                NuzlockeTracking.observe(context.applicationContext.filesDir, trackerState)
                KeptSave.observe(store, session, trackerState, runNow.attempt)
                if (trackerState != null) RunClock.tick(session.kind?.id?.takeIf { session.isRun }, { runNow.attempt }, gameActive && Demo.mode == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }
        var tracker: com.ironmonone.tracker.GbaTracker? = null
        while (true) {
            val visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!visible) { kotlinx.coroutines.delay(1000); continue }
            if (trackerState?.inBattle == true && tracker != null) {
                // In battle, look for ability messages every ~2 frames at 1x
                // between the 250 ms full reads (see pollAbilityTrigger).
                val t = tracker
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    repeat(8) { kotlinx.coroutines.delay(31); t?.pollAbilityTrigger() }
                }
            } else kotlinx.coroutines.delay(
                if (trackerState?.inBattle == true) 250 else 700
            )
            if (tracker == null) {
                tracker = kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.Default
                ) {
                    runCatching {
                        val probe = r.readMemory(0x08000000L, 4)
                        if (probe.isEmpty()) null
                        else com.ironmonone.tracker.GbaTracker(
                            reader, com.ironmonone.tracker.GameMap.resolve(reader)
                        )
                    }.getOrNull()
                }
                trackerRef = tracker
                // The favorite-in-ball line reads this tracker's starter balls
                // (FavoriteBall, through favoriteLine's remember on trackerRef).
                // No PC tracker has it, which is why it was cut on 2026-09-07; it
                // is back on Blake's word (2026-10-01), in Kaizo IronMON runs only.
            }
            tracker?.let { t ->
                t.lossCondition = TrackerOptions.lossCondition
                val fresh = kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.Default
                ) { runCatching { t.read() }.getOrNull() }
                // Self-heal: an unreadable party means this map is wrong for
                // the ROM actually loaded, so drop the tracker and resolve
                // again next tick rather than showing the error all session.
                if (fresh != null && fresh.unreadable) {
                    tracker = null
                    trackerRef = null
                }
                trackerState = fresh ?: trackerState
                Demo.mode?.takeIf { it.startsWith("gba") }?.let { m -> trackerState = runCatching { Demo.gba(t, m) }.getOrNull() ?: trackerState }
                // The Nuzlocke ledger follows every poll, not only the ones a tracker panel draws (hidden, clean view).
                NuzlockeTracking.observe(context.applicationContext.filesDir, trackerState)
                KeptSave.observe(store, session, trackerState, runNow.attempt)
                if (trackerState != null) RunClock.tick(session.kind?.id?.takeIf { session.isRun }, { runNow.attempt }, gameActive && Demo.mode == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }
    }

    // Window width in dp, for clamping the tracker so the game keeps a usable
    // minimum on narrow landscape windows.
    // The DS frame is aligned by MEASURING where the tracker actually is,
    // not by computing it from assumed dp values. Every arithmetic attempt was
    // off by ~180px because the numbers it used (window width, tracker width,
    // letterboxing) do not add up to the real on-screen position.
    val dsDensity = androidx.compose.ui.platform.LocalDensity.current.density
    var dsFrameWidthPx by remember { mutableStateOf(0f) }


    // The pad unmounts when the orientation flips or a controller connects.
    // Neither destroys the screen, so the dispose above does not fire - but a
    // button held across either one would stay latched.
    DisposableEffect(landscape, showPad) {
        onDispose { releaseAllCoreKeys() }
    }

    // The screen layout follows the orientation WITHOUT rebooting the core:
    // the view is keyed on gameKey, so a rotation alone would otherwise leave
    // the layout chosen at load time, and rebuilding the view on every
    // rotation would restart the game.
    // No layout chosen: in landscape the screens arrange themselves to the game column's
    // shape (side by side on a phone, stacked on a tablet-shaped column), see NdsScreens.
    var gameColumn by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val dsLayoutName = padLayout.dsLayout ?: if (landscape) NdsScreens.autoLayout(gameColumn.width, gameColumn.height) else "top-bottom"
    LaunchedEffect(dsLayoutName, retro, platform) {
        if (platform != com.ironmonone.core.Platform.NDS) return@LaunchedEffect
        retro?.updateVariables(
            com.swordfish.libretrodroid.Variable("melonds_screen_layout", NdsScreens.classicName(dsLayoutName)),
            com.swordfish.libretrodroid.Variable("melonds_screen_gap", NdsScreens.classicGap(padLayout.dsGap)),
        )
    }

    var confirmNewRun by remember { mutableStateOf(false) }
    /** The Type Defenses screen for one Pokemon: its name and its buckets, or null. */
    var typeDefenses by remember { mutableStateOf<Pair<String, Map<Double, List<String>>>?>(null) }
    var coverageCalc by remember { mutableStateOf(false) }
    /** Move History for one Pokemon: species, name, level. */
    // Where the game picture sits on screen, so the game-over popup sits over it (GameOverHost).
    var gameFrame by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    /**
     * The GBA battery save, persisted per game.
     *
     * mGBA exposes save data ONLY through the frontend SRAM interface -
     * unlike melonDS, which writes its own .sav - and nothing here ever
     * called it, so every FireRed/Emerald in-game save lived purely in core
     * memory and died with the process. The NEW RUN dialog promised the
     * in-game save was kept; on GBA that was false every time.
     *
     * Written on pause, on leaving the tab, before a reboot, and while the game
     * runs once it has saved (AutoSave.keepFresh, rc32 audit P2 #51); loaded into
     * the core at view creation via saveRAMState. A new seed keeps it, in every
     * game: Continue on its title screen opens it (RunSaves, 2026-09-30).
     */
    fun sramFile() = store.sramFile(session)

    fun persistSram() {
        if (platform.coreOwnsSaves || retro == null) return
        runCatching {
            // useEmulationThread = false: the default hops to the emulation
            // thread and BLOCKS the caller on a latch until it answers. This
            // runs from onDispose and from ON_PAUSE, both on the main thread,
            // so every tab switch and every rotation could hang the UI - a
            // confirmed ANR (input dispatch timed out after 5s in
            // GLRetroView.serializeSRAM). Reading the SRAM buffer directly
            // trades a theoretical torn read for never freezing the app.
            val bytes = retro?.serializeSRAM(useEmulationThread = false) ?: return
            // The battery save's one writer (rc32 audit P3 #54): it writes nothing for the empty buffer of a core
            // mid-teardown or not yet running, and never lets an older read win (the flush while playing, P2 #51).
            // Flushed, an atomic replace that never deletes the save first, the .tmp gone on a failure, and the
            // failure said (rc33 audit P0-8: a full phone lost every in-game save in silence).
            StateSlots.writeSram(sramFile(), bytes)?.let { SaveTrouble.report(SaveTrouble.BATTERY, it) }
        }
    }

    fun newRun() {
        // A randomized Nuzlocke's next game keeps its rules in a ledger of its own (2026-09-30, UX audit P0-8).
        val nuzlocke = if (session.isRun) NuzlockeTracking.current(context.applicationContext.filesDir)?.ledger else null
        // One at a time: a second confirm while one is being made is refused, not run (rc33 audit P0-4, NewRunGuard).
        // Claimed right before the launch, whose completion gives it back however the job ends.
        if (!NewRunGuard.claim()) { status = NewRunGuard.BUSY; return }
        scope.launch {
            status = "Rolling a new seed…"
            // Stop the core BEFORE the randomizer overwrites current.gba/.nds:
            // mGBA holds the ROM in memory, but melonDS keeps the file handle,
            // and writing under it worked only because teardown usually came
            // soon enough. Order it explicitly instead of by luck.
            persistSram()
            gameActive = false
            kotlinx.coroutines.delay(200)
            retro?.let { v ->
                // Silence FIRST. Randomizing takes ~40s with the core already
                // destroyed, and that is the window the crash lands in: the
                // audio stream is still closing while nothing is driving it.
                // Muting stops the emulator side writing into a buffer that
                // teardown is freeing, which is one of the two racy paths
                // (the other is guarded in audio.cpp).
                runCatching { v.audioEnabled = false }
                lifecycleOwner.lifecycle.removeObserver(v)
                runCatching { v.onDestroy() }
            }
            retro = null
            trackerRef = null
            kotlinx.coroutines.delay(500)
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    // The last run's game and settings again. A run made ahead for exactly
                    // those (NextRun) is moved in within seconds; otherwise it is randomized
                    // now. The rotate, seed, attempt and notes are PrepStore.installRun's,
                    // the same code the Run tab goes through.
                    val (k, prepared, settings) = RunStart.lastInputs(store)
                    // The passes as RUN has them switched now (ExtraPasses): a stage made the other way is not taken.
                    val started = RunStart.start(store, k, prepared, settings, seed = null, app = NextRunJob.appStamp(context),
                        prePass = ExtraPasses.prePassFor(context, store, k, settings),
                        secondPass = ExtraPasses.secondPassFor(context, store, k, settings), countAttempt = nuzlocke == null)
                    nuzlocke?.let { NuzlockeStore(context.applicationContext.filesDir).startNextRandomized(it, k, started.seed, System.currentTimeMillis()) }
                    // Here, not after the reboot: if the player left Play meanwhile, the rest of this job never runs.
                    TrackerOptions.startRunWith(settings.name)
                    started.seed to settings.name
                }
            }
            ok.onSuccess { (seed, settingsName) ->
                status = "New run (seed %016x). Rebooting…".format(seed)
                trackerState = null
                ndsState = null
                ndsTrackerRef = null
                battleStartState = null   // a state from another randomization must never be restored
                gameOverLatch.reset()
                timeMachine.clear()
                statMarks.clear()
                encounterBook.reset()
                lastCountedSpecies = -1
                marksVersion++
                gameKeyForRom++
                gameKey++
                gameActive = true
                status = ("New run (seed %016x)." + if (TrackerOptions.ballPickerShows()) " New ball call incoming." else "").format(seed)
            }.onFailure {
                // Plain copy, never a null (which showed nothing) or an exception's
                // class name; the exception itself goes to the log (2026-09-27, audit).
                android.util.Log.w("KaizoCore", "New run failed", it)
                status = newRunFailureCopy(it)
                // Survives the composition being torn down by the empty state.
                store.setLastRunError(newRunFailureCopy(it))
                // The core was already stopped; bring the OLD run back up
                // rather than leaving a dead screen.
                gameActive = true
            }
        }.invokeOnCompletion { NewRunGuard.release() }   // done, failed, or cancelled before it started
    }

    // Three save slots. One slot meant every checkpoint overwrote the last,
    // which is the wrong shape for a run where you want a pre-gym state kept
    // while you experiment past it.
    fun slotFile(n: Int) = store.slotFile(session, n)

    // Slots arrived after single-slot builds, whose state lived in state0.bin.
    // Without this an update silently orphans the player's only save state.
    LaunchedEffect(Unit) {
        val legacy = File(context.filesDir, "saves/state0.bin")
        val slot1 = slotFile(1)
        if (session.isRun && legacy.exists() && !slot1.exists()) {
            runCatching {
                slot1.parentFile?.mkdirs()
                legacy.copyTo(slot1, overwrite = false)
                // Stamp it as the current run, or loadState's stamp gate
                // refuses the very file this migration exists to preserve
                // (and the original was already deleted).
                runCatching {
                    store.slotStamp(session, 1).writeText(store.stateStamp(session))
                }
                legacy.delete()
                slotsVersion++
            }
        }
    }

    /** Which ROM and seed a slot was taken from. */
    fun slotStamp(n: Int) = store.slotStamp(session, n)

    /**
     * The frame on screen, for the slot's thumbnail. PixelCopy reads the
     * SurfaceView's buffer, which is the only way to get pixels out of a
     * GLSurfaceView without touching the GL thread. Asynchronous; the
     * callback lands on the main thread.
     */
    fun captureFrame(onDone: (android.graphics.Bitmap?) -> Unit) {
        val v = retro ?: return onDone(null)
        if (android.os.Build.VERSION.SDK_INT < 24 || v.width <= 0 || v.height <= 0) return onDone(null)
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        runCatching {
            android.view.PixelCopy.request(v as android.view.SurfaceView, bmp, { rc ->
                onDone(if (rc == android.view.PixelCopy.SUCCESS) bmp else null)
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }.onFailure { onDone(null) }
    }

    fun saveState(slot: Int = saveSlot) {
        val target = StateSlots.slot(context.filesDir, session, slot)
        if (target.locked) { status = "Slot $slot is locked. Unlock it in STATES to overwrite."; return }
        val st = retro?.serializeState()
        if (st == null || st.isEmpty()) {
            // It used to overwrite the slot with whatever came back and say
            // "Saved" - or say nothing at all on null - so a bad serialize
            // could destroy the previous good state while claiming success.
            status = "Could not save: the game is not running."
            return
        }
        captureFrame { frame ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val f = slotFile(slot).apply { parentFile?.mkdirs() }
                // The state being overwritten survives as the slot's backup (UNDO).
                if (slot != StateSlots.AUTO) target.keepBackup()
                StateSlots.writeAtomic(f, st)?.let { err ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { status = err }
                    return@launch
                }
                runCatching { slotStamp(slot).writeText(store.stateStamp(session)) }
                // Thumbnail: 240 wide, aspect kept. Missing is fine; the row shows a dash.
                val thumb = StateSlots.thumbFile(f)
                if (frame != null) runCatching {
                    val w = 240; val h = (frame.height.toLong() * w / frame.width).toInt().coerceAtLeast(1)
                    val small = android.graphics.Bitmap.createScaledBitmap(frame, w, h, true)
                    thumb.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                } else thumb.delete()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    slotsVersion++
                    saveSlot = slot
                    status = "Saved to slot $slot."
                }
            }
        }
    }

    /**
     * Auto-save: slot 0, written when the game is left (pause, tab switch,
     * exit), and while it is played (AutoSave). Silent, no thumbnail capture
     * (the surface may be gone), and skipped when the core cannot answer.
     * What RESUME in STATES loads.
     */
    fun autoSave() {
        val v = retro ?: return
        // useEmulationThread = false, the same reason as persistSram: this runs
        // from ON_PAUSE and onDispose, when the emulation thread is already
        // paused, and the default hops to it and waits on a latch that never
        // fires. That was a confirmed ANR (input dispatch timed out, 5s).
        val st = runCatching { v.serializeState(useEmulationThread = false) }.getOrNull()
        if (st == null || st.isEmpty()) return
        // Every write of the slot goes through its one writer: in order, flushed, the stamp after the state.
        // leaving: this is the moment the game is left or paused, which is what lets it open back here (CrashResume).
        AutoSave.of(context.filesDir, session).save(st, System.currentTimeMillis(), store.stateStamp(session), leaving = true)
    }

    fun loadState(which: Int = saveSlot, keepUndo: Boolean = false) {
        val f = slotFile(which)
        if (!f.exists()) { status = StateSlots.emptyLine(which); return }
        if (raHardcore) { status = "Loading a state is off in RetroAchievements hardcore."; return }
        // Another randomization's state, or one with no stamp, is refused (StateSlots.loadRefusal, rc32 audit P3 #54):
        // a save state restores the whole of RAM, and the tracker would read a party that cannot exist.
        StateSlots.loadRefusal(which, runCatching { slotStamp(which).readText().trim() }.getOrNull(), store.stateStamp(session))?.let { status = it; return }
        val slot = which
        if (which != StateSlots.AUTO) saveSlot = which
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // DS states run tens of MB; reading them on the main thread froze
            // the UI for seconds per tap.
            val bytes = StateSlots.readOrNull(f)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (bytes == null) { status = "Could not read ${StateSlots.named(slot, capital = false)}."; return@withContext }
                // A controller's quick load asks nothing, so it keeps the moment
                // it replaced and the toast offers it back (2026-09-27, audit).
                val before = if (keepUndo) runCatching { retro?.serializeState() }.getOrNull()?.takeIf { it.isNotEmpty() } else null
                // The core reports whether it accepted the state; claiming
                // "Loaded" when it refused left the player mid-game with no
                // idea the rewind never happened.
                val ok = retro?.unserializeState(bytes) == true
                val msg = if (ok) (if (slot == StateSlots.AUTO) "Resumed from the auto-save." else "Loaded slot $slot.")
                else "Could not load ${StateSlots.named(slot, capital = false)}. The save may be damaged."
                status = msg
                // In the run's events (RunEvents): which slot, and when that state was saved.
                val slotName = if (slot == StateSlots.AUTO) "auto" else "$slot"
                if (ok) store.runEvents(session)?.add(RunEvents.Kind.LOAD, slotName, "saved ${f.lastModified()}")
                if (ok && before != null) ui.toastAction = Triple(msg, "Undo") {
                    val undone = retro?.unserializeState(before) == true
                    if (undone) store.runEvents(session)?.add(RunEvents.Kind.UNDO, slotName)
                    status = if (undone) "Load undone." else "Could not undo the load."
                }
            }
        }
    }

    // LOAD from a menu asks first: it replaced live progress on one tap. An
    // empty slot has nothing to lose, so it goes straight to "Slot N is empty."
    fun askLoad(n: Int = saveSlot) { if (slotFile(n).exists()) ui.confirmLoad = n else loadState(n) }

    /** Audio is on only when the user has not muted AND we are not in turbo. */
    fun applyAudio() {
        retro?.audioEnabled = !muted && speed == 1 && slow == 1 && !rewinding
    }

    // Speed is PICKED, not cycled: a forward cycle through 1x..16x, 1/2x,
    // 1/4x took six taps to get from 2x back to 1x (2026-09-27, audit).
    // GBA goes to 16x. The DS core is capped at 4x: raising melonDS past it
    // killed the GL thread outright (SIGSEGV in GLThread the instant the
    // multiplier changed, reproduced 2026-08-30), and a crash that eats the
    // run is worse than a slower fast-forward. Revisit if the core is fixed.
    // Then the two slow-motion steps, 1/2 and 1/4, never in hardcore. Slow
    // motion is a session thing, never persisted: a game that opened at
    // quarter speed would read as broken.
    fun speedOptions(): List<String> =
        generateSequence(1) { it * 2 }.takeWhile { it <= platform.maxTurbo }.map { "${it}x" }.toList() +
            (if (raHardcore) emptyList() else listOf("\u00BDx", "\u00BCx"))
    fun pickSpeed(label: String) {
        when (label) {
            "\u00BDx" -> { speed = 1; slow = 2 }
            "\u00BCx" -> { speed = 1; slow = 4 }
            else -> { speed = label.removeSuffix("x").toIntOrNull()?.coerceIn(1, platform.maxTurbo) ?: 1; slow = 1 }
        }
        retro?.frameSpeed = speed
        retro?.slowMotion = slow
        // IronMON default: silence during turbo, full audio at 1x. Never chipmunk.
        applyAudio()
    }
    val speedLabel = when (slow) { 2 -> "\u00BDx"; 4 -> "\u00BCx"; else -> "${speed}x" }

    // RUMBLE. The core's rumble state changes arrive as events; the phone's
    // motor follows: any strength above zero buzzes at that amplitude until
    // the core sets zero, and stops when Play pauses or leaves (PhoneHardware.follow,
    // rc32 audit P3 #47). Off with the SETTINGS row.
    LaunchedEffect(retro, coreValues[CoreOptions.RUMBLE_KEY]) {
        val r = retro ?: return@LaunchedEffect
        if (coreValues[CoreOptions.RUMBLE_KEY] == "off") return@LaunchedEffect
        val vib = PhoneHardware.vibrator(context) ?: return@LaunchedEffect
        PhoneHardware.follow(vib, r.getRumbleEvents(), lifecycleOwner.lifecycle)
    }
    // SENSORS. Listeners exist only while the core has asked for that sensor,
    // read from its mask every half second; values are fed in libretro's
    // units (g, rad/s, lux). Off with the SETTINGS row.
    LaunchedEffect(retro, coreValues[CoreOptions.SENSORS_KEY]) {
        val r = retro ?: return@LaunchedEffect
        if (coreValues[CoreOptions.SENSORS_KEY] == "off") return@LaunchedEffect
        val feed = PhoneHardware.SensorFeed(context) { ax, ay, az, gx, gy, gz, lux -> r.setSensorValues(ax, ay, az, gx, gy, gz, lux) }
        try {
            while (true) {
                feed.want(runCatching { r.sensorMask() }.getOrDefault(0))
                kotlinx.coroutines.delay(500)
            }
        } finally { feed.stop() }
    }

    // REWIND. Record a state every interval while the game runs at 1x and
    // nothing is rewinding; hold REWIND to pop them back, newest first.
    // Recorded between frames from a background thread (RewindBuffer.record).
    fun startRewind() {
        if (!rewindAllowed) { status = "Rewind is off in a Kaizo IronMON run and in a Nuzlocke."; return }
        if (raHardcore) { status = "Rewind is off in RetroAchievements hardcore."; return }
        if (rewinding) return
        rewinding = true; applyAudio()
        scope.launch {
            while (rewinding) {
                val st = rewind.pop()
                if (st == null) { status = "Nothing further back."; break }
                retro?.unserializeState(st, useEmulationThread = false)
                kotlinx.coroutines.delay(RewindBuffer.policy(platform).second / 4)
            }
            rewinding = false; applyAudio()
        }
    }
    fun stopRewind() { rewinding = false }
    // Crash insurance: the auto slot every three minutes of play (AutoSave),
    // snapshotted on the emulation thread and written off the main one.
    // The battery save too, once the game has saved, on a console whose core leaves saves to the app (rc32 audit P2 #51).
    LaunchedEffect(retro, session.id) {
        val r = retro ?: return@LaunchedEffect
        AutoSave.of(context.filesDir, session).keepFresh(r, stamp = { store.stateStamp(session) }, playing = {
            gameActive && ui.coreUp === r && Demo.mode == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }, sram = if (platform.coreOwnsSaves) null else sramFile())
    }
    LaunchedEffect(retro, rewindAllowed, session.id) {
        val r = retro ?: return@LaunchedEffect
        if (!rewindAllowed) return@LaunchedEffect
        rewind.record(r, RewindBuffer.policy(platform).second) {
            !rewinding && speed == 1 && slow == 1 && gameActive && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }

    // Apply the RESTORED speed and mute once the core exists.
    //
    // Restoring the state variables alone only changes the chip captions: the
    // core boots at 1x and audible regardless, so the pad would read "4X MUTED"
    // over a game running at normal speed with sound - worse than not
    // persisting at all, because the display would be lying.
    /**
     * Apply the RESTORED speed and mute, once the core is actually running.
     *
     * Restoring the state variables alone only changes the chip captions: the
     * core boots at 1x and audible regardless, so the pad would read "2X
     * MUTED" over a game running at full speed with sound.
     *
     * The wait for FrameRendered is not politeness. Writing audioEnabled as
     * soon as the view existed reproduced a hard SIGSEGV on NEW RUN every
     * time - a null dereference inside oboe::LatencyTuner::tune() on the
     * AudioTrack thread, because the toggle raced the audio stream that the
     * reloading core was still building. FrameRendered is the first proof the
     * core is up rather than mid-setup.
     */
    LaunchedEffect(retro, platform) {
        val r = retro ?: return@LaunchedEffect
        r.getGLRetroEvents()
            .filter { it is GLRetroView.GLRetroEvents.FrameRendered }
            .first()
        ui.coreUp = r   // the view may take its real size now (holdSizeWhileLoading)
        SramGuard.check(r, store.sramFile(session))
        // melonDS dies above 4x (see speedOptions). Persistence made it possible
        // to carry a GBA run's 16x into a DS core, which is that same crash.
        val capped = speed.coerceAtMost(platform.maxTurbo)
        if (capped != speed) speed = capped
        r.frameSpeed = capped
        applyAudio()
        applyCheats()
        // RetroAchievements: a saved session signs in silently; the game is
        // identified once the login lands (EV_LOGIN_DONE above). A game
        // already loaded (view rebuilt on NEW RUN) is reset, not reloaded.
        com.swordfish.libretrodroid.LibretroDroid.cheevosSetHardcore(raStore.hardcore)
        raRefresh()
        if (raSummary.loggedIn) {
            if (!session.isRun) { raBusy = "Looking up this game..."; com.swordfish.libretrodroid.LibretroDroid.cheevosLoadGame(rom.absolutePath, RetroAchievements.consoleId(platform)) }
        } else raStore.load()?.let { (u, t) -> raBusy = "Signing in..."; com.swordfish.libretrodroid.LibretroDroid.cheevosLogin(u, t, true) }
        // The run after this one, made in the background while this one is played.
        if (session.isRun) NextRunJob.prepare(context)
        // The app closed with this game open (a crash, a call, a kill): back where it was, from
        // its own auto slot only (CrashResume). Otherwise the slot is offered, as it always was.
        CrashResume.atCoreUp(store.playMarker, session, StateSlots.auto(context.filesDir, session), store.stateStamp(session),
            loadsAllowed = !(raStore.hardcoreSignedIn() && !session.isRun), events = store.runEvents(session),
            why = { CrashResume.lastExit(context) }, load = { bytes -> r.unserializeState(bytes) })?.let { status = it }
    }

    // DS sprites come out of the player's own ROM (RomSprites): decode once per
    // kind into the cache, off the main thread, and point the card at it. A
    // kind the decoder does not know, or a ROM it cannot open, leaves the
    // bundled fallback in place and says nothing.
    LaunchedEffect(session.id) {
        val k = kind
        if (platform != com.ironmonone.core.Platform.NDS || k == null || RomSprites.narcPath(k) == null) { RomSprites.activeKind = null; return@LaunchedEffect }
        RomSprites.activeKind = k
        val max = if (k.generation == com.ironmonone.core.Generation.NDS5) 649 else 493
        val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { RomSprites.decodeAll(context.filesDir, rom, k, max) }
                .onFailure { android.util.Log.w("KaizoCore", "RomSprites: ${it}", it) }.getOrDefault(-1)
        }
        if (n > 0) status = "Sprites read from your game: $n."
    }

    // The bar shows FILE only while the Play screen is up, and stops showing it
    // the moment the screen leaves - otherwise a stale button would sit over the
    // other tabs, opening a menu for a game that is no longer loaded.
    // A+B+Start held is the reset gesture every IronMON player already has in
    // their hands. It replaces the NEW RUN button rather than duplicating it.
    androidx.compose.runtime.DisposableEffect(Unit) {
        // Ask first. A+B+Start is easy to hit by accident with a controller in
        // your hands, and the thing on the other side of it is the whole run.
        NewRunCombo.onFire = { confirmNewRun = true }
        onDispose { NewRunCombo.onFire = null; NewRunCombo.reset() }
    }

    // Publish the flee hook ONLY while a wild battle is up, so a controller's
    // B does nothing special in a trainer battle or in the overworld.
    androidx.compose.runtime.DisposableEffect(wildBattleNow) {
        FleeOnB.onFlee = if (wildBattleNow) ({ flee() }) else null
        // Gen 1, 2 and 3 read the battle menu live (ActionMenuGate). DS cannot: B there is only B, and RUN stays.
        FleeOnB.menuUp = { actionMenuUp() ?: false }
        onDispose { FleeOnB.onFlee = null; FleeOnB.menuUp = null }
    }

    androidx.compose.runtime.DisposableEffect(menuOpen, fullscreen) {
        AppBarActions.content = if (fullscreen) null else {
            { com.ironmonone.app.gen3.Gen3Button(
                "FILE", accent = menuOpen, onClick = { menuOpen = !menuOpen }) }
        }
        onDispose { AppBarActions.content = null }
    }

    // The FILE menu, defined ONCE and rendered in both orientations.

    // It used to live inside the portrait-only branch while the FILE button
    // that opens it is published in BOTH orientations, so in landscape the
    // button toggled a menu nothing drew and read as a dead control.
    //
    // The landscape overlay chips do cover most of it (SLOT/SAVE/LOAD/NEW/
    // speed/mute/CAM); RESET is the one action they lack. That is a reason to
    // keep the menu reachable, not a reason to keep two half-overlapping
    // control surfaces - but the button says FILE, so FILE opens the menu.
    val FileMenu: @Composable () -> Unit = {
        Column {
        if (streamOn) {
            // The address OBS needs, with a Copy button: it had to be typed by
            // hand off this line (2026-09-27, audit).
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(com.ironmonone.app.stream.StreamHub.menuLine(),
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
                    modifier = Modifier.weight(1f))
                com.ironmonone.app.gen3.Gen3Button("Copy link", onClick = {
                    status = com.ironmonone.app.stream.StreamHub.copyLink { clipboard.setText(androidx.compose.ui.text.AnnotatedString(it)) }
                })
            }
        }
        // Wrapped rows in three groups (save states, the game, tools), not one
        // row of twenty buttons with most of them off screen and nothing to say
        // so (2026-09-27, audit). Capped and scrolled vertically, so it stays
        // near the height the portrait budget gives it; the half-shown row is
        // the cue that there is more. 164dp ended exactly on a row edge, so the
        // menu looked complete with four rows hidden; 190dp shows half a row, and
        // a fade marks the bottom while there is more (2026-09-27, walk-through).
        val menuScroll = rememberScrollState()
        FlowRow(
            // Excluded from the system back gesture: a fast fling on this strip
            // starting near the screen edge used to leave the game (2026-09-06).
            Modifier.fillMaxWidth().systemGestureExclusion()
                .heightIn(max = if (landscape) 210.dp else 190.dp)
                .drawWithContent {
                    drawContent()
                    if (menuScroll.canScrollForward) drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Shell.paper), startY = size.height - 36.dp.toPx(), endY = size.height,
                        ),
                    )
                }
                .verticalScroll(menuScroll).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (landscape) {
                // Landscape shows this OR the chip strip, never both.
                com.ironmonone.app.gen3.Gen3Button("Fewer", onClick = { ui.moreOpen = false })
                com.ironmonone.app.gen3.Gen3Button("Close", onClick = { menuOpen = false })
                MenuRule()
            }
            // Save states. The slot is picked in STATES; this button used to
            // step through eight slots one tap at a time (2026-09-27, audit).
            val slotHas = remember(saveSlot, slotsVersion) { slotFile(saveSlot).exists() }
            com.ironmonone.app.gen3.Gen3Button("States, slot $saveSlot" + if (slotHas) "" else " (empty)",
                onClick = { statesDialog = true })
            com.ironmonone.app.gen3.Gen3Button("SAVE STATE", onClick = { saveState() })
            com.ironmonone.app.gen3.Gen3Button("LOAD STATE", onClick = { askLoad() })
            if (rewindAllowed) HoldChip("REWIND", onDown = { startRewind() }, onUp = { stopRewind() }, big = true)
            MenuRule()
            // The game.
            if (session.isRun) com.ironmonone.app.gen3.Gen3Button(if (NuzlockeTracking.inPlay()) "NEW NUZLOCKE" else "NEW RUN", accent = true,
                onClick = { confirmNewRun = true })
            // Speed opens a picker (see speedOptions).
            com.ironmonone.app.gen3.Gen3Button("Speed $speedLabel", accent = speed > 1 || slow > 1,
                onClick = { ui.speedPicker = true })
            com.ironmonone.app.gen3.Gen3Button(if (muted) "UNMUTE" else "MUTE",
                accent = muted, onClick = { muted = !muted; applyAudio() })
            // Restart asks first: it threw away everything since the last save on one tap.
            com.ironmonone.app.gen3.Gen3Button("RESTART", onClick = { ui.confirmReset = true })
            // DS: collapse the touch screen so the top screen fills the frame.
            if (dsScreens) {
                com.ironmonone.app.gen3.Gen3Button(
                    if (dsTopOnly) "Both screens" else "Top screen only",
                    accent = dsTopOnly,
                    onClick = { dsTopOnly = !dsTopOnly })
            }
            // 2.4: the rules for this game and mode, readable mid-run.
            // A Nuzlocke's own rules in a Nuzlocke game, not the IronMON rulebook (2026-09-30, UX audit P0-6).
            if (session.tracked) com.ironmonone.app.gen3.Gen3Button("RULES", onClick = { if (NuzlockeTracking.inPlay()) NuzlockeLedgerRequest.openRules() else rulesDialog = true })
            // The second screen is view only, so the tracker's SETUP there cannot be tapped: this is the way in from the phone.
            if (session.tracked && TrackerOptions.trackerOnSecondScreen) com.ironmonone.app.gen3.Gen3Button("TRACKER SETUP", onClick = { menuOpen = false; gearDialog = true })
            MenuRule()
            // Tools.
            com.ironmonone.app.gen3.Gen3Button("CAM", accent = facecam,
                onClick = { facecam = !facecam })
            // Streaming: the web tracker for OBS, and the capture layout.
            com.ironmonone.app.gen3.Gen3Button(if (streamOn) "STREAM ON" else "STREAM",
                accent = streamOn, onClick = {
                    if (streamOn) { com.ironmonone.app.stream.StreamHub.stop(); streamOn = false; status = "Stream server stopped." }
                    else {
                        val url = com.ironmonone.app.stream.StreamHub.start(context.filesDir)
                        streamOn = url != null
                        status = com.ironmonone.app.stream.StreamHub.startedLine(url)
                    }
                })
            com.ironmonone.app.gen3.Gen3Button("CLEAN VIEW", onClick = { onClean(true); menuOpen = false })
            com.ironmonone.app.gen3.Gen3Button("EDIT LAYOUT", onClick = { startLayoutEdit() })
            com.ironmonone.app.gen3.Gen3Button("SETTINGS", onClick = { settingsDialog = true })
            com.ironmonone.app.gen3.Gen3Button(
                if (!raSummary.loggedIn) "ACHIEVEMENTS" else if (raSummary.gameLoaded) "Achievements ${raSummary.unlocked}/${raSummary.total}" else "ACHIEVEMENTS ON",
                accent = raSummary.loggedIn && raSummary.gameLoaded,
                onClick = { raRefresh(); raDialog = true })
            com.ironmonone.app.gen3.Gen3Button(
                if (!cheatsAllowed) "CHEATS OFF" else if (cheats.any { it.enabled }) "Cheats (${cheats.count { it.enabled }})" else "CHEATS",
                accent = cheatsAllowed && cheats.any { it.enabled },
                onClick = {
                    if (cheatsAllowed) cheatsDialog = true
                    else status = "Cheats are off in a Kaizo IronMON run, a Nuzlocke and RetroAchievements hardcore."
                })
            // With a controller attached the on-screen pad is optional; the
            // switch lives here rather than as a row under the pad, which
            // cost the tracker a button's height for a control used once.
            if (controllerOn && padForced) {
                com.ironmonone.app.gen3.Gen3Button("HIDE PAD") {
                    padForced = false; store.setPadForced(false)
                }
            }
            // Diagnostic for the route panel: the adopted map-id transitions,
            // shareable without a computer, same as the crash report. Only the
            // Gen 3 tracker records them (trackerRef is GBA only), so on GB/GBC
            // and DS it could only ever send an empty log and is not shown
            // there (2026-09-27, audit).
            if (trackerRef != null) com.ironmonone.app.gen3.Gen3Button("ROUTE LOG", onClick = {
                val lines = trackerRef?.routeLogSnapshot() ?: emptyList()
                val text = if (lines.isEmpty()) "No map transitions recorded yet."
                    else lines.joinToString(Char(10).toString())
                runCatching {
                    File(context.filesDir, "route-log.txt").writeText(text)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "KaizoCore route log")
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(send, "Send route log"))
                }
                status = "Route log: " + lines.size + " transitions."
            })

            // Debug builds only: writes a known Pokemon into the DS party
            // slot to prove the tracker's read/decode/render chain without
            // playing to the first starter. Never present in a release APK.
            val debuggable = (context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable && dsScreens) {
                com.ironmonone.app.gen3.Gen3Button("INJECT", onClick = {
                    val t = ndsTrackerRef
                    status = if (t == null) "DS tracker not ready yet."
                    else t.injectTestMon { addr, data ->
                        retro?.writeMemory(addr, data) ?: 0
                    }
                })
            }
        }
        // Status messages are the toast's now (StatusToast), shown whether or not this menu is open.
        }
    }


    // DS coverage runs off type NAMES, because the Gen 4 sidecar stores types
    // as names rather than ids.
    val ndsLeadMoveTypes = ndsState?.party?.firstOrNull()?.moves
        ?.filter { it.category != "STATUS" }?.map { it.type }?.distinct()?.sorted()
        ?: emptyList()
    val ndsCoverage = remember(ndsLeadMoveTypes, gameKey) {
        ndsTrackerRef?.coverage(ndsLeadMoveTypes) ?: emptyMap()
    }

    // The lead's damaging move types, which is all the coverage walk needs.
    // Keyed on that list so it does not re-walk the whole dex every poll.
    val leadMoveTypes = trackerState?.lead?.moveRows
        ?.mapNotNull { r -> r.type.takeIf { r.category != "STA" } }?.distinct()?.sorted()
        ?: emptyList()
    val coverage = remember(leadMoveTypes, gameKey) {
        trackerRef?.coverage(leadMoveTypes) ?: emptyMap()
    }

    // The ball call is a RANDOM pick, the way the PC tracker's
    // randomlyChooseBall() is - math.random(3), nothing more.
    //
    // It used to be derived from a CRC of the ROM's first 4096 bytes. That
    // region is header and boot code, which randomizing barely touches, so the
    // CRC came out the same on nearly every seed and the call was "RIGHT" over
    // and over. A derived value was the wrong idea entirely: this is meant to
    // be a coin toss, not a function of the ROM.
    //
    // Keyed to the run so it stays put while you walk to the lab instead of
    // re-rolling on every tracker poll, and REROLL re-picks on demand, which is
    // the reference's dice button.
    var ballReroll by remember { mutableStateOf(0) }
    val ballCall = remember(gameKey, ballReroll) {
        when (java.security.SecureRandom().nextInt(3)) {
            0 -> "LEFT"; 1 -> "MIDDLE"; else -> "RIGHT"
        }
    }

    // DS landscape used to overlay the tracker on the right third of a full-width
    // frame, with the bottom screen meant to show below it. With a real team the
    // tracker is taller than the screen, so the bottom screen was always hidden
    // (Blake, 2026-09-06). DS landscape is now side by side, like GBA landscape.

    // The tracker column's children: the attempt row and the panel. One
    // definition, drawn docked beside the game or inside the floating window.
    val trackerContent: @Composable () -> Unit = {
                  // Streaming layout: camera sits at the TOP of the right
                  // column, with the attempt counter and the tracker beneath
                  // it. Docked here it needs no dragging and cannot cover the
                  // game or its own controls, which the floating bubble could.
                  if (facecam) {
                      FacecamDocked(onDenied = { permanent -> facecam = false; status = CameraDenied.note(context, ui, permanent) })
                  }
                  // The attempt (the only counter in the app), FILE, the DS's screens and hiding the tracker sit
                  // under one small arrow at the end of the tracker's first row (TrackerCornerMenu). They were a row
                  // of chips of their own, which ran off a narrow column (Blake, 2026-10-02). The second display is
                  // view only: the panel's own ATTEMPT line, a Kaizo IronMON run's only, shows there (rc32 audit P2 #55).
                  val onSecond = LocalOnSecondScreen.current
                  val corner: (@Composable () -> Unit)? = if (onSecond || TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING) null else { {
                      TrackerCornerMenu(
                          attempt = runNow.attempt, menuOpen = menuOpen, onMenu = { menuOpen = !menuOpen; lastTouch = android.os.SystemClock.uptimeMillis() },
                          dsTopOnly = dsTopOnly.takeIf { dsScreens }, onScreens = { dsTopOnly = !dsTopOnly },
                          onHide = { trackerOpen = false; ui.trackerPeek = false },
                          onFloat = { TrackerOptions.landscapeTracker = LandscapeTracker.FLOATING; TrackerOptions.save() },
                      )
                  } }
                  if (dsScreens) NdsTrackerPanel(
                      state = ndsState, onFlee = { flee() }, onGear = { gearDialog = true }, headerTrailing = corner, timer = if (TrackerOptions.showTimer) runTimer else null,
                      favoriteLine = favoriteLine,
                      randomBall = ndsTrackerRef?.randomBall?.takeIf { TrackerOptions.ballPickerShows() },
                      onTypeDefenses = { n, a, b -> typeDefenses = n to com.ironmonone.tracker.Gen3Types.defenses(com.ironmonone.tracker.nds.Gen4Types.idOf(a) ?: -1, com.ironmonone.tracker.nds.Gen4Types.idOf(b) ?: (com.ironmonone.tracker.nds.Gen4Types.idOf(a) ?: -1)) },
                      enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                      onCycleMark = { i ->
                          if (notebookSpecies > 0) statMarks.cycle(notebookSpecies, i)
                          marksVersion++
                      },
                      enemyNote = enemyNote,
                      onEditNote = { noteDialog = true },
                      attempt = runNow.attempt,
                      coverage = ndsCoverage,
                        enemyLastLevel = enemyLastSeen,
                        movesSeenRunWide = if (notebookSpecies > 0) statMarks.movesSeenFor(notebookSpecies) else emptyList(),
                        moveInfoFor = { id -> ndsTrackerRef?.moveInfoFor(id) },
                        onMoveHistory = { sp, n, lv -> side.moveHistory = Triple(sp, n, lv) },
                        encounterArea = ndsState?.let { com.ironmonone.tracker.nds.NdsEncounterTables.area(it.badgeSet, it.areaName) },
                        encountersSeen = ndsState?.let { statMarks.dsEncountersIn(it.areaName) } ?: emptyMap(),
                        speciesNameOf = { sp -> ndsTrackerRef?.speciesName(sp) ?: "#$sp" },
                        hiddenPowerType = statMarks.dsHiddenPowerType(), effectivenessReady = dsFxReady,
                        onStepHiddenPower = { f -> statMarks.stepDsHiddenPower(f); marksVersion++ },
                        pokecenterCount = statMarks.dsPokecenterCount(), onPokecenter = { up -> statMarks.bumpDsPokecenter(up); marksVersion++ },
                        stackBoth = TrackerRoom.stackBoth(LocalTrackerRoom.current),
                  )
                  else TrackerPanel(
                      onTrainerInfo = { trackerState?.opponentTrainerId?.let { id -> trackerRef?.trainer(id)?.let { side.trainerInfo = it } } },
                      onGradeNotes = { side.scoreSheet = true },
                      onRandomEvos = { sp -> side.randomEvos = sp }, hasRandomEvos = { sp -> trackerRef?.hasRandomEvos(sp) == true },
                      onMoveHistory = { sp, n, lv -> side.moveHistory = Triple(sp, n, lv) },
                      onTypeDefenses = { n, a, b -> typeDefenses = n to com.ironmonone.tracker.Gen3Types.defenses(a, b, gen1 = session.kind?.generation == com.ironmonone.core.Generation.GB1, natDex = session.kind?.isNatDex == true) },
                      trackerState, onFlee = { flee() }, ballCall = ballCall, onGear = { gearDialog = true }, headerTrailing = corner,
                onRerollBall = { ballReroll++ },
                movesSeenRunWide = gbaView.foe(trackerState)
                    ?.let { statMarks.movesSeenFor(it.species) } ?: emptyList(),
                moveRowFor = { id -> trackerRef?.moveRowFor(id) ?: gbLookup?.invoke(id) },
                revealedEnemyAbility = gbaView.foe(trackerState)
                    ?.let { statMarks.abilityFor(it.species) },
                revealedEnemyAbility2 = gbaView.foe(trackerState)
                    ?.let { statMarks.secondAbilityFor(it.species) },
                routeName = trackerState?.routeName,
                routeSeen = routeCarousel.second,
                routeTotal = routeCarousel.third,
                routeTrainers = trackerState?.routeTrainers?.size ?: 0,
                routeBosses = trackerState?.routeBosses ?: 0,
                steps = trackerState?.steps ?: 0,
                onMoveDescription = panelLookups::moveDescription,
                onAbilityDescription = { name ->
                    trackerRef?.let { t -> t.abilityIdOf(name)?.let(t::abilityDescription) }
                },
                onWeight = panelLookups::weight,
                onEvolution = panelLookups::evolutionDetails,
                onEffectiveness = panelLookups::effectiveness,
                onMoveLevels = panelLookups::moveLevels,
                onSpeciesNote = { sp -> statMarks.noteFor(sp) },
                onRouteSource = { raw -> routeSource(raw) },
                onRouteLookup = { trackerRef?.routeLookupList() ?: emptyList() },
                routeArea = routeCarousel.first,
                onSpeciesBase = panelLookups::speciesBase,
                // Gen 3 ids run to 411 (1283 with the Nat. Dex); Red/Blue/Yellow have 151, Gold/Silver/Crystal 251.
                speciesTotal = when {
                    trackerRef?.expandedSpeciesIds == true -> 1283
                    gbRef != null -> if (session.kind?.generation?.number == 1) 151 else 251
                    else -> 411
                },
                onEditNoteFor = { sp -> noteForSpecies = sp; noteDialog = true },
                onHealsInBag = if (trackerRef?.hasCatchRates == true) { { side.healsDialog = true } } else null,
                onTrainersOnRoute = if (trackerRef?.hasTrainerData == true) { { side.trainersDialog = true } } else null,
                onBattleDetails = if (trackerRef?.hasBattleDetails == true) { { side.battleDetailsDialog = true } } else null,
                onCalcAtk = { side.openCalcAtk(trackerRef, trackerState) },
                pcHealsLimit = remember(session.id, runNow.attempt) { if (session.isRun) PcHeals.limitForLastRun() else null },
                onSpeciesName = panelLookups::speciesName,
                      favoriteLine = favoriteLine, spriteFor = spriteFor,
                      enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                      enemyLastSeenLevel = enemyLastSeen,
                      onCycleMark = { i ->
                          gbaView.foe(trackerState)?.let { statMarks.cycle(it.species, i) }
                          marksVersion++
                      },
                      enemyNote = enemyNote,
                      onEditNote = { noteDialog = true },
                      attempt = runNow.attempt,
                      coverage = coverage, runScoped = session.isRun,
                      // Landscape: your lead and the enemy together.
                      stackBoth = TrackerRoom.stackBoth(LocalTrackerRoom.current),
                      onCatchRates = { side.catchHpAdjust = 0; side.catchRatesDialog = true },
                      generation = session.kind?.generation?.number ?: 3,
                  )
                  }
    // The tracker on a second display when there is one (SecondScreen.kt); the phone keeps the game.
    val trackerOnSecond = SecondScreenHost(session.tracked && !streamClean && trackerOpen && TrackerOptions.trackerOnSecondScreen) { trackerContent() }
    // A DS on Hybrid Top with the tracker docked: the tracker fills the black box above the touch screen (DsDock).
    val dsDock = dsDockIn(retro, ui, landscape && fullscreen && dsScreens && !dsTopOnly && dsLayoutName == "hybrid-top" && session.tracked && !streamClean && !trackerOnSecond,
        trackerOpen, gameColumn, coreValues)

    // The tracker pane, hoisted so it can be laid out two ways: as a
    // full-height column beside the game, or - for DS landscape - as a
    // TOP-anchored overlay, so the bottom screen can sit BELOW it.
    val trackerPane: @Composable () -> Unit = {
        Row {
          if (!session.tracked) {
              // Nothing to track: the game takes the whole width.
          } else if (TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING) {
              // 2.2: the window floats over the game; nothing sits in this row.
          } else if (dsDock != null) {
              // Over the game's black box above the touch screen instead (DsDockTracker); nothing sits in this row.
          } else if (trackerOpen && (TrackerOptions.landscapeTracker == LandscapeTracker.DOCKED || ui.trackerPeek)) {
              // No bar: the game meets the tracker, and the tracker's left edge resizes it (TrackerEdge.kt, rc34).
              DockedTracker(panes, windowWidthDp, trackerContent)
          } else {
              // Collapsed: a tab on the right edge, tap to bring it back.
              // 48dp wide and named for a screen reader; it was 26dp with a
              // bare arrow. A tap shows the tracker for now: it used to write
              // Docked over a Hidden choice made in the tracker's settings
              // (2026-09-27, audit).
              Box(
                  Modifier.width(com.ironmonone.app.Shell.touchTarget).fillMaxHeight()
                      .semantics { contentDescription = "Show tracker" }
                      .background(Pc.Ground)
                      .clickable {
                          trackerOpen = true
                          if (TrackerOptions.landscapeTracker != LandscapeTracker.DOCKED) ui.trackerPeek = true
                      },
                  contentAlignment = Alignment.Center,
              ) {
                  Text("◀", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                      fontSize = 17.sp, color = Color.White)
              }
          }
        }
    }

    // DS landscape is the streaming layout: the game surface spans the
    // WHOLE window so the core's hybrid screen layout drops the small
    // bottom screen into the right-hand side, and the tracker anchors to
    // the TOP of that side so the bottom screen sits BELOW it.
    //
    // One core means one framebuffer means one surface: the bottom screen
    // cannot be a second view, so the layout is arranged around where the
    // core actually draws it rather than trying to move it.
    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    lastTouch = android.os.SystemClock.uptimeMillis()
                }
            }
        },
    ) {
    Row(Modifier.fillMaxSize()) {
      // GBA portrait is the one layout whose height is NOT elastic: the game
      // frame is a fixed 3:2, so on a shorter screen - or one with a system nav
      // bar - the bottom of the column simply fell off. That cost the DOWN
      // button and the whole L/SELECT/START/R row, with no scrollbar and no
      // clipping cue to say anything was missing.
      //
      // Only this case may scroll. Fullscreen and DS size the game with
      // weight(1f), and a weighted child inside a scrolling column is a crash,
      // not a layout bug.
      // LANDSCAPE MUST NEVER SCROLL. It used to be `!fullscreen`, but
      // landscape-with-chrome (reachable from MENU and from Back) is also
      // !fullscreen - and in that state the game Box takes the
      // `landscape -> fillMaxSize()` branch inside a verticalScroll. Under an
      // unbounded height fillMaxSize passes minHeight 0 through, the
      // SurfaceView measures to 0 px, and the game vanishes while the core
      // keeps running and keeps taking input.
      // NOTHING scrolls the whole column any more.
      //
      // Portrait GBA used to scroll game + tracker + pad as one strip, so a
      // loaded tracker card (736px) pushed the d-pad about 260px below the
      // fold: you could have the game or the controls, not both. The fix is
      // the same shape as the Run screen's - pin the two things that must
      // always be visible and let the variable-height one absorb the
      // difference. The game keeps its 3:2 box at the top, the pad stays at
      // the bottom, and the TRACKER takes the leftover and scrolls inside
      // itself. On a shorter screen the tracker simply gets less room, which
      // is the correct thing to give up.
      // Measured size of the play area (game + tracker + pad), so the
      // portrait height budget below is exact instead of a guess at the
      // chrome around it.
      var playArea by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
      // Portrait: how much the pad and, failing that, the game must yield
      // so the tracker keeps a readable minimum. See PortraitBudget.
      val budget = PortraitBudget.plan(
          playArea.width, playArea.height,
          androidx.compose.ui.platform.LocalDensity.current.density, menuOpen,
      )
      val scrollable = false
      Column(
          Modifier.weight(1f).fillMaxHeight()
              .onSizeChanged { playArea = it }
              .then(
                  if (scrollable) Modifier.verticalScroll(rememberScrollState())
                  else Modifier
              )
      ) {
        // The game surface. Same composition slot in both orientations, so rotating
        // never recreates the view (which would reboot the core mid-run).
        Box(
            when {
                // FIRST, deliberately. `fullscreen` is true for any
                // landscape view without chrome - which includes every DS
                // streaming layout - so it matched first and this branch
                // was dead code. Several rounds of geometry edits changed
                // nothing on screen for exactly that reason.
                // DS is two 256x192 screens stacked, so a fixed aspect ratio makes
                // the frame 1.5x the screen width TALL and shoves the tracker and
                // the pad off the bottom. Take the leftover space instead and let
                // the core letterbox inside it.
                // Landscape gives the frame the whole column and lets the
                // core letterbox inside it. A 3:2 box gets width-constrained
                // by the column and ends up smaller than the space available.
                // DS landscape, positioned from the core's ACTUAL hybrid
                // geometry rather than guessed: hybrid-top draws the top
                // screen at 2x (512x384) with the bottom screen at 1x (256
                // wide) beside it, so the frame is 768x384 - aspect 2.0 - and
                // the bottom screen starts at exactly 512/768 = 2/3 across.
                //
                // Giving the surface that aspect makes the content fill it
                // exactly, so the fractions are meaningful. Earlier attempts
                // only changed the surface WIDTH, which did nothing: the
                // content was letterboxed and centred inside it, so it never
                // moved and the bottom screen stayed inset ~180px from the
                // tracker instead of flush with it.
                //
                // The offset then slides the frame so the 2/3 mark lands on
                // the tracker's left edge, putting the bottom screen and the
                // tracker in one straight column.
                fullscreen -> Modifier.fillMaxWidth().weight(1f).background(Color.Black).onSizeChanged { gameColumn = it }
                dsScreens -> Modifier.fillMaxWidth().weight(1f)
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
                landscape -> Modifier.fillMaxSize().onSizeChanged { gameColumn = it }
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
                else -> Modifier.fillMaxWidth(budget.gameFraction)
                    .align(Alignment.CenterHorizontally).aspectRatio(platform.aspect)
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
            },
        ) {
            if (gameActive) androidx.compose.runtime.key(gameKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize()
                        .holdSizeWhileLoading(ui, loading = retro == null || ui.coreUp !== retro)
                        .onGloballyPositioned { gameFrame = it.boundsInWindow() }
                        // DS bottom-screen collapse: scale 2x about the top edge
                        // and clip, so the top screen fills the frame. The core
                        // still renders both; this only changes what is shown,
                        // so nothing about emulation or save states changes.
                        .then(
                            if (dsScreens && dsTopOnly)
                                Modifier.graphicsLayer {
                                    scaleX = 2f; scaleY = 2f
                                    transformOrigin = androidx.compose.ui.graphics
                                        .TransformOrigin(0.5f, 0f)
                                }
                            else Modifier
                        ),
                    factory = { ctx ->
                        val data = GLRetroViewData(ctx).apply {
                            coreFilePath = corePath.absolutePath
                            gameFilePath = rom.absolutePath
                            systemDirectory = ctx.filesDir.absolutePath
                            savesDirectory = File(ctx.filesDir, "saves")
                                .apply { mkdirs() }.absolutePath
                            // DS SCREEN LAYOUT.
                            //
                            // melonDS stacks the two screens, which is a tall
                            // 256x384 shape. Letterboxed into a short, wide
                            // landscape column it can only be as wide as its
                            // height allows, so it filled just over half the
                            // width with black pillars either side - measured
                            // at 52% of the column, 396px wasted left and
                            // 272px right. Side-by-side turns it into a wide
                            // shape that fills the space, keeps BOTH screens
                            // visible, and keeps the stylus working.
                            //
                            // Portrait keeps the stacked layout, which is the
                            // right shape for a tall window.
                            variables = (coreVariables() + if (dsScreens) listOf(
                                com.swordfish.libretrodroid.Variable("melonds_screen_layout", NdsScreens.classicName(dsLayoutName)),
                                com.swordfish.libretrodroid.Variable("melonds_screen_gap", NdsScreens.classicGap(padLayout.dsGap)),
                            ) else emptyList()).toTypedArray()
                            shader = shaderFor(coreValues[CoreOptions.FILTER_KEY])
                            // Restore the battery save. melonDS manages its
                            // own .sav in savesDirectory; only GBA needs this.
                            if (!platform.coreOwnsSaves) {
                                saveRAMState = sramFile()
                                    .takeIf { it.exists() && it.length() > 0 }
                                    ?.readBytes()
                            }
                        }
                        GLRetroView(ctx, data).also { view ->
                            retro = view
                            view.setOnTouchListener(ScreenTap.listener(ui, ctx)) // a tap on bare screen, for ScreenTapMenu
                            view.stateLoadListener = SaveGuard.listener(platform, File(ctx.filesDir, "saves"), rom)   // loading a state keeps the in-game save
                            view.cheevosListener = RetroAchievements.listener(
                                onEvent = { type, title, desc, points, badge, result ->
                                    view.post {
                                        when (type) {
                                            RetroAchievements.EV_LOGIN_DONE -> {
                                                if (result != RetroAchievements.SIGN_IN_IN_FLIGHT) raBusy = null
                                                if (result == 0) { if (badge.isNotBlank()) raStore.save(title, badge); status = "RetroAchievements: signed in as $title." }
                                                else if (result != RetroAchievements.SIGN_IN_IN_FLIGHT) { if (RetroAchievements.tokenRefused(result)) raStore.clear(); raError = desc.ifBlank { "Sign-in failed (error $result)." }; status = "RetroAchievements: $raError" }
                                                raRefresh()
                                                if (result == 0 && !session.isRun) { raBusy = "Looking up this game..."; com.swordfish.libretrodroid.LibretroDroid.cheevosLoadGame(rom.absolutePath, RetroAchievements.consoleId(platform)) }
                                            }
                                            RetroAchievements.EV_RESET -> { RetroAchievements.restartGame(view); raRefresh(); status = "RetroAchievements: hardcore starts the game over, from its last in-game save." }
                                            RetroAchievements.EV_GAME_LOADED -> {
                                                raBusy = null; raRefresh()
                                                status = if (result == 0) "RetroAchievements: $title, ${raSummary.unlocked}/${raSummary.total} unlocked." else "RetroAchievements: " + desc.ifBlank { "no set for this game" }
                                            }
                                            RetroAchievements.EV_ACHIEVEMENT -> { status = "Achievement unlocked: $title ($points)"; raRefresh() }
                                            RetroAchievements.EV_GAME_COMPLETED -> { status = "RetroAchievements: game mastered!"; raRefresh() }
                                            RetroAchievements.EV_SERVER_ERROR -> status = "RetroAchievements: $desc"
                                            RetroAchievements.EV_DISCONNECTED -> status = "RetroAchievements: offline, unlocks will be sent when it is back."
                                            RetroAchievements.EV_RECONNECTED -> status = "RetroAchievements: back online."
                                        }
                                    }
                                },
                            )
                            // GLRetroView IS a LifecycleObserver: its own lifecycle
                            // onCreate performs the native create. Driving onResume by
                            // hand skips that and segfaults on the first load.
                            lifecycleOwner.lifecycle.addObserver(view)
                        }
                    },
                )
            }

            // The floating bubble is only for layouts with no tracker drawn to
            // dock into: portrait, or landscape with no column, window or dock.
            // Where the tracker is drawn, a second display included, the camera
            // lives in it, and never in both (FacecamPlace, rc32 audit P2 #59).
            if (FacecamPlace.of(facecam, streamClean, landscape, session.tracked, trackerOnSecond, TrackerOptions.landscapeTracker, trackerOpen, ui.trackerPeek) == FacecamPlace.BUBBLE) {
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    FacecamBubble(onDenied = { permanent -> facecam = false; status = CameraDenied.note(context, ui, permanent) })
                }
            }

            // Stream layout (brief 16.1): controls fade after idle so a screen
            // share reads as game + tracker; any touch brings them back.
            var dimmed by remember { mutableStateOf(false) }
            // DS stylus: the game view's own touch handling. LibretroDroid's
            // GLRetroView.onTouchEvent normalises the touch over the view and
            // the native side maps it through the core's letterbox
            // (video->getLayout().getRelativePosition), so every screen layout
            // the core can draw, stacked or side by side or hybrid, is handled
            // where the layout is known. A Compose layer used to sit here doing
            // the same maths itself; on 2026-09-07 it was found to receive no
            // pointer events at all in either orientation, so no DS game could
            // pass a "touch the screen" prompt. The free pad's buttons are the
            // only Compose targets over the view, so a touch on bare screen
            // reaches the view, and a touch on a button is the button's.

            if (landscape) {
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(500)
                        dimmed = android.os.SystemClock.uptimeMillis() - lastTouch > 3000
                    }
                }
            }
            // The game now fills its pane, so these sit ON the picture rather
            // than on a black margin. They rest SEMI-TRANSPARENT so the game
            // reads through them, and fade to nothing when untouched instead of
            // lingering at a ghostly 12% that was neither visible nor gone.
            // TWO fades, because these are not the same kind of control.
            //
            // The chip strip (SAVE / LOAD / NEW / MENU) is occasional, so it
            // may disappear entirely. The PAD is how you play - hiding it left
            // no way to press a button, which is worse than it covering some
            // scenery. It stays put, just see-through.
            val chipAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = when {
                    landscape && dimmed -> 0f
                    landscape -> 0.55f
                    else -> 1f
                },
                label = "chipFade",
            )
            val controlAlpha = if (landscape) padLayout.opacity else 1f
            // Modifier.alpha does not affect hit testing, so a faded control is
            // still live: the tap meant to WAKE the controls also pressed
            // whichever one it landed on - and LOAD and SAVE sit in that strip
            // with no confirmation. While faded, the first touch only wakes.
            // Only the CHIPS need the wake-first guard, since only they can
            // be invisible. The pad is always visible and always live.
            val controlsAwake = !(landscape && dimmed)
            if (landscape) {
                // No full-size watcher Box here any more. It had a pointer modifier and
                // sat above the game view, and Compose hands a touch to the topmost
                // sibling only, so in landscape the DS touch screen never received a
                // press (2026-09-07). The root Box's watcher already sees every touch
                // at the Initial pass without consuming it.

                if (showPad || editingLayout) {
                    FreePad(
                        layout = padLayout, onB = { if (wildBattleNow) flee() },
                        translucent = true, skin = padSkin, editing = editingLayout, selected = selectedElement,
                        onSelect = { selectedElement = it }, onEdit = { padLayout = it },
                        modifier = Modifier.fillMaxSize()
                            .then(Modifier.alpha(if (editingLayout) 1f else controlAlpha)).clearOfDsDock(dsDock),
                    )
                }
                // Swallow the wake-up touch while the strip is faded.
                if (!controlsAwake) {
                    Box(
                        Modifier.align(Alignment.TopStart).fillMaxWidth()
                            .height(56.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        e.changes.forEach { it.consume() }
                                        // This guard is the topmost hit under the strip, so the
                                        // watcher below never sees a tap here: a tap on the faded
                                        // strip did nothing at all. It wakes the chips itself now.
                                        lastTouch = android.os.SystemClock.uptimeMillis()
                                    }
                                }
                            }
                    )
                }

                // FILE in landscape shows ONE thing: the chip strip, or (after
                // More) the full menu. It drew both at once, about 170dp over
                // the game, and kept drawing the menu over clean view and the
                // layout editor (2026-09-27, audit).
                if (menuOpen && ui.moreOpen && !streamClean && !editingLayout) {
                    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.75f))) {
                        FileMenu()
                    }
                }

                // Slim utility strip, top-left: speed and states stay reachable by
                // touch even with a controller (no controller button maps to them).
                // Bounded and scrollable: the strip is as wide as the game
                // column, and adding MENU pushed it past that edge.
                // The editor bar is narrower than the column, so the corners
                // (L and R in every preset) stay free, and it moves to the
                // bottom edge when a control sits under it (2026-09-27, audit).
                if (editingLayout) layoutToolbar(Modifier.align(if (ui.layoutBarBottom) Alignment.BottomCenter else Alignment.TopCenter))
                if (!session.tracked && !menuOpen && !streamClean && !editingLayout)
                    LandscapeMenuChip(Modifier.align(Alignment.TopCenter)) { menuOpen = true }
                // Centred, with arrows while it scrolls and an X that closes it (LandscapeMenuBand, 2026-10-02).
                if (menuOpen && !ui.moreOpen && !streamClean && !editingLayout) LandscapeMenuBand(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().systemGestureExclusion().padding(6.dp)
                        .alpha(chipAlpha),
                    onClose = { menuOpen = false },
                ) {
                    // Two groups, separated by a rule: the ones that act on
                    // the RUN first, then the ones that act on the APP. Nine
                    // undifferentiated chips meant hunting for LOAD every
                    // time, and NEW (which ends your run) sat between two
                    // harmless ones.
                    // One chip for the states list, where slots are picked; SLOT
                    // used to step through eight of them (2026-09-27, audit).
                    OverlayChip("States, slot $saveSlot") { statesDialog = true }
                    OverlayChip("SAVE") { saveState() }
                    OverlayChip("LOAD") { askLoad() }
                    // Runs only, as in portrait: on a library game or hack NEW
                    // re-randomized the last IronMON run (2026-09-27, audit).
                    if (session.isRun) OverlayChip("NEW") { confirmNewRun = true }
                    ChipRule()
                    OverlayChip("Speed $speedLabel") { ui.speedPicker = true }
                    if (rewindAllowed) HoldChip("REWIND", onDown = { startRewind() }, onUp = { stopRewind() })
                    OverlayChip(if (muted) "MUTED" else "SOUND") {
                        muted = !muted; applyAudio()
                    }
                    OverlayChip(if (facecam) "CAM ON" else "CAM") { facecam = !facecam }
                    // Both close the menu: it stayed open over the capture and the editor.
                    OverlayChip("CLEAN") { menuOpen = false; onClean(true) }
                    OverlayChip("LAYOUT") { startLayoutEdit() }
                    // Reachable in landscape too, where the portrait notice
                    // and its button are not rendered at all.
                    if (controllerOn) {
                        OverlayChip(if (padForced) "PAD ON" else "PAD OFF") {
                            padForced = !padForced
                            store.setPadForced(padForced)
                        }
                    }
                    // Everything else (restart, settings, cheats, stream...) in place of the strip.
                    OverlayChip("MORE") { ui.moreOpen = true }
                    // Status messages are the toast's now (StatusToast); here
                    // they were cut at 38 characters and only showed with the strip open.
                    // The tab bar is gone in landscape, so without this the
                    // only way back to the rest of the app was to rotate the
                    // phone - impossible with rotation locked.
                    OverlayChip("MENU") { onExitFullscreen() }
                }
                // Over the faded strip's guard, so a first tap on the docked DS tracker reaches it.
                dsDock?.let { DsDockTracker(it, Modifier.align(Alignment.TopEnd)) { trackerContent() } }
                // Last, so it sits over the faded strip's guard: the tracker's menu while the tracker is off screen.
                ScreenTapMenu(
                    ui, pad = padLayout.takeIf { showPad }, padSkin = padSkin,
                    allowed = session.tracked && !menuOpen && !streamClean && !editingLayout,
                    trackerOpen = trackerOpen, trackerOnSecond = trackerOnSecond,
                    dsLayout = if (dsScreens && !dsTopOnly) ScreenTap.DsLayout(dsLayoutName, padLayout.dsGap, coreValues["melonds_hybrid_ratio"]?.toIntOrNull() ?: 2) else null,
                    attempt = runNow.attempt, dsTopOnly = dsTopOnly.takeIf { dsScreens }, onScreens = { dsTopOnly = !dsTopOnly },
                    // A pick in the dropdown (its own window) never reaches the idle clock, so the band came up faded.
                    onFile = { menuOpen = true; lastTouch = android.os.SystemClock.uptimeMillis() },
                    onShow = { trackerOpen = true; if (TrackerOptions.landscapeTracker != LandscapeTracker.DOCKED) ui.trackerPeek = true },
                )
            }

        }

        if (!landscape) {
            // The FILE button itself lives in the top bar; this is only what it
            // drops down when opened, so nothing sits between game and tracker.
            if (menuOpen) { FileMenu() }

            // Blake, 2026-09-15: "the game is unplayable in portrait, buttons
            // are too small etc, the tracker should be below the buttons". So
            // the order is game, pad, tracker: the buttons sit under the game
            // where the thumbs already are, and the TRACKER is the piece that
            // takes whatever height is left over.
            if (streamClean || !session.tracked || trackerOnSecond) Spacer(Modifier.weight(1f))

            if (streamClean) {
                // Capture layout: no pad, no notice.
            } else if (!showPad && !editingLayout) {
                // Tappable, not just a notice: detection counts any paired
                // keyboard as a controller, so this was a dead end with no way
                // back to touch controls.
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Controller connected. Pad hidden.",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Shell.hintOnNight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    com.ironmonone.app.gen3.Gen3Button("SHOW PAD ANYWAY") {
                        padForced = true
                        store.setPadForced(true)
                    }
                }
            } else {
                if (editingLayout) layoutToolbar(Modifier)
                // The band is the pad area the layout's fractions refer to:
                // the designed 192dp, shrunk by the portrait budget.
                Box(Modifier.fillMaxWidth().height((PortraitBudget.PAD_NATURAL_DP * budget.padScale).dp)) {
                    FreePad(
                        layout = padLayout, onB = { if (wildBattleNow) flee() },
                        translucent = false, skin = padSkin, baseScale = budget.padScale,
                        editing = editingLayout, selected = selectedElement,
                        onSelect = { selectedElement = it }, onEdit = { padLayout = it },
                    )
                }
            }
            if (streamClean || !session.tracked || trackerOnSecond) {
                // Nothing here: the weighted spacer that holds the pad at the
                // bottom is now ABOVE the pad, because the pad comes first.
            } else if (dsScreens) {
                // Whatever height is left under the pad, scrolled: a full
                // party of six is taller than any phone screen.
                TrackerScroll(Modifier.weight(1f), background = null) {
                    NdsTrackerPanel(
                        state = ndsState, onFlee = { flee() }, onGear = { gearDialog = true }, timer = if (TrackerOptions.showTimer) runTimer else null,
                        favoriteLine = favoriteLine,
                        randomBall = ndsTrackerRef?.randomBall?.takeIf { TrackerOptions.ballPickerShows() },
                        onTypeDefenses = { n, a, b -> typeDefenses = n to com.ironmonone.tracker.Gen3Types.defenses(com.ironmonone.tracker.nds.Gen4Types.idOf(a) ?: -1, com.ironmonone.tracker.nds.Gen4Types.idOf(b) ?: (com.ironmonone.tracker.nds.Gen4Types.idOf(a) ?: -1)) },
                        enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                        onCycleMark = { i ->
                            if (notebookSpecies > 0) statMarks.cycle(notebookSpecies, i)
                            marksVersion++
                        },
                        enemyNote = enemyNote,
                        onEditNote = { noteDialog = true },
                        attempt = runNow.attempt,
                        coverage = ndsCoverage,
                        enemyLastLevel = enemyLastSeen,
                        movesSeenRunWide = if (notebookSpecies > 0) statMarks.movesSeenFor(notebookSpecies) else emptyList(),
                        moveInfoFor = { id -> ndsTrackerRef?.moveInfoFor(id) },
                        onMoveHistory = { sp, n, lv -> side.moveHistory = Triple(sp, n, lv) },
                        encounterArea = ndsState?.let { com.ironmonone.tracker.nds.NdsEncounterTables.area(it.badgeSet, it.areaName) },
                        encountersSeen = ndsState?.let { statMarks.dsEncountersIn(it.areaName) } ?: emptyMap(),
                        speciesNameOf = { sp -> ndsTrackerRef?.speciesName(sp) ?: "#$sp" },
                        hiddenPowerType = statMarks.dsHiddenPowerType(), effectivenessReady = dsFxReady,
                        onStepHiddenPower = { f -> statMarks.stepDsHiddenPower(f); marksVersion++ },
                        pokecenterCount = statMarks.dsPokecenterCount(), onPokecenter = { up -> statMarks.bumpDsPokecenter(up); marksVersion++ },
                    )
                }
            } else TrackerScroll(
                // Bounded and scrollable: a full card is taller than a phone
                // screen, which was silently cutting off the fourth move.
                // weight(1f), not a fixed cap: the tracker is the flexible
                // one now. Safe because the parent column no longer scrolls -
                // a weighted child inside a scrolling column is a crash, which
                // is why `scrollable` had to go first.
                Modifier.weight(1f), background = null,
            ) { TrackerPanel(
                onTrainerInfo = { trackerState?.opponentTrainerId?.let { id -> trackerRef?.trainer(id)?.let { side.trainerInfo = it } } },
                      onGradeNotes = { side.scoreSheet = true },
                      onRandomEvos = { sp -> side.randomEvos = sp }, hasRandomEvos = { sp -> trackerRef?.hasRandomEvos(sp) == true },
                onMoveHistory = { sp, n, lv -> side.moveHistory = Triple(sp, n, lv) },
                onTypeDefenses = { n, a, b -> typeDefenses = n to com.ironmonone.tracker.Gen3Types.defenses(a, b, gen1 = session.kind?.generation == com.ironmonone.core.Generation.GB1, natDex = session.kind?.isNatDex == true) },
                trackerState, onFlee = { flee() }, ballCall = ballCall, onGear = { gearDialog = true },
                onRerollBall = { ballReroll++ },
                movesSeenRunWide = gbaView.foe(trackerState)
                    ?.let { statMarks.movesSeenFor(it.species) } ?: emptyList(),
                moveRowFor = { id -> trackerRef?.moveRowFor(id) ?: gbLookup?.invoke(id) },
                revealedEnemyAbility = gbaView.foe(trackerState)
                    ?.let { statMarks.abilityFor(it.species) },
                revealedEnemyAbility2 = gbaView.foe(trackerState)
                    ?.let { statMarks.secondAbilityFor(it.species) },
                routeName = trackerState?.routeName,
                routeSeen = routeCarousel.second,
                routeTotal = routeCarousel.third,
                routeTrainers = trackerState?.routeTrainers?.size ?: 0,
                routeBosses = trackerState?.routeBosses ?: 0,
                steps = trackerState?.steps ?: 0,
                onMoveDescription = panelLookups::moveDescription,
                onAbilityDescription = { name ->
                    trackerRef?.let { t -> t.abilityIdOf(name)?.let(t::abilityDescription) }
                },
                onWeight = panelLookups::weight,
                onEvolution = panelLookups::evolutionDetails,
                onEffectiveness = panelLookups::effectiveness,
                onMoveLevels = panelLookups::moveLevels,
                onSpeciesNote = { sp -> statMarks.noteFor(sp) },
                onRouteSource = { raw -> routeSource(raw) },
                onRouteLookup = { trackerRef?.routeLookupList() ?: emptyList() },
                routeArea = routeCarousel.first,
                onSpeciesBase = panelLookups::speciesBase,
                // Gen 3 ids run to 411 (1283 with the Nat. Dex); Red/Blue/Yellow have 151, Gold/Silver/Crystal 251.
                speciesTotal = when {
                    trackerRef?.expandedSpeciesIds == true -> 1283
                    gbRef != null -> if (session.kind?.generation?.number == 1) 151 else 251
                    else -> 411
                },
                onEditNoteFor = { sp -> noteForSpecies = sp; noteDialog = true },
                onHealsInBag = if (trackerRef?.hasCatchRates == true) { { side.healsDialog = true } } else null,
                onTrainersOnRoute = if (trackerRef?.hasTrainerData == true) { { side.trainersDialog = true } } else null,
                onBattleDetails = if (trackerRef?.hasBattleDetails == true) { { side.battleDetailsDialog = true } } else null,
                onCalcAtk = { side.openCalcAtk(trackerRef, trackerState) },
                pcHealsLimit = remember(session.id, runNow.attempt) { if (session.isRun) PcHeals.limitForLastRun() else null },
                onSpeciesName = panelLookups::speciesName,
                favoriteLine = favoriteLine, spriteFor = spriteFor,
                enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                enemyLastSeenLevel = enemyLastSeen,
                onCycleMark = { i ->
                    gbaView.foe(trackerState)?.let { statMarks.cycle(it.species, i) }
                    marksVersion++
                },
                enemyNote = enemyNote,
                onEditNote = { noteDialog = true },
                attempt = runNow.attempt,
                coverage = coverage, runScoped = session.isRun,
                onCatchRates = { side.catchHpAdjust = 0; side.catchRatesDialog = true },
                generation = session.kind?.generation?.number ?: 3,
            ) }

        }
      }

      // Landscape: the tracker sits BESIDE the game, the way BizHawk and the PC
      // tracker sit side by side, and collapses to an arrow tab so the game can
      // have the whole screen back.
      if (landscape && !streamClean && !trackerOnSecond) trackerPane()
    }
    if (landscape && !streamClean && session.tracked && !trackerOnSecond && TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING) {
        FloatingTracker(
            panes = panes, windowW = windowWidthDp, windowH = windowHeightDp,
            onDock = { TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED; TrackerOptions.save(); trackerOpen = true },
            menu = { dock ->
                TrackerCornerMenu(
                    attempt = runNow.attempt, menuOpen = menuOpen, onMenu = { menuOpen = !menuOpen; lastTouch = android.os.SystemClock.uptimeMillis() },
                    dsTopOnly = dsTopOnly.takeIf { dsScreens }, onScreens = { dsTopOnly = !dsTopOnly },
                    onHide = null, onDock = dock,
                )
            },
            attempt = runNow.attempt,
        ) { trackerContent() }
    }
    // One place for status messages in both orientations, menu open or not
    // (2026-09-27, audit). Not over the capture: clean view stays clean.
    if (!streamClean) StatusToast(
        status, hold = !gameActive, action = ui.toastAction,
        onGone = { status = null; ui.toastAction = null },
        modifier = Modifier.align(if (landscape) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(top = if (landscape) 56.dp else 0.dp),
    )
    if (streamClean) CleanViewExit(onExit = { onClean(false) })
    // A game the core refuses says so, and stays said, over a black screen (CoreLoadErrors, rc32 audit P2 #53).
    CoreLoadErrors(retro, session.isRun, Modifier.align(Alignment.Center))
    }

    androidx.compose.runtime.SideEffect {
        QuickActions.onQuickSave = { saveState() }
        // Instant, as a mapped key should be, with Undo on the toast instead of a question.
        QuickActions.onQuickLoad = { loadState(keepUndo = true) }
        QuickActions.onOpenStates = { statesDialog = true }
        QuickActions.onRewind = { down -> if (down) startRewind() else stopRewind() }
        QuickActions.onFastForward = { down ->
            val target = if (down) platform.maxTurbo else 1
            if (speed != target) { speed = target; retro?.frameSpeed = target; applyAudio() }
        }
    }
    if (settingsDialog) {
        EmulatorSettingsDialog(
            platform = platform, values = coreValues,
            onChange = { o, v ->
                store.coreOptions.set(platform, o.key, v)
                coreValues = coreValues + (o.key to v)
                if (o.key == CoreOptions.FILTER_KEY) retro?.shader = shaderFor(v)
                else if (o.key !in CoreOptions.APP_KEYS) retro?.updateVariables(com.swordfish.libretrodroid.Variable(o.key, v))
                if (o.restart) status = "${o.label}: applies on the next boot of this game."
            },
            onReset = {
                store.coreOptions.reset(platform)
                coreValues = store.coreOptions.effective(platform)
                retro?.let { r -> r.shader = com.swordfish.libretrodroid.ShaderConfig.Default; r.updateVariables(*coreVariables().toTypedArray()) }
                status = "Settings reset; restart-marked ones apply on the next boot."
            },
            systemFilePresent = { SystemFiles.present(context.filesDir, it) },
            onImportSystemFile = { name -> importSystemFileName = name; systemFilePicker.launch(arrayOf("*/*")) },
            onDismiss = { settingsDialog = false },
        )
    }
    if (statesDialog) {
        SaveStatesDialog(
            context.filesDir, session, runStamp = { store.stateStamp(session) }, current = saveSlot, version = slotsVersion,
            onPick = { saveSlot = it },
            onSave = { saveState(it) },
            // Asks first, like the menu's LOAD (2026-09-27, audit).
            onLoad = { askLoad(it); statesDialog = false },
            onLock = { s, on -> s.setLocked(on); slotsVersion++ },
            // The swap is off the main thread (StateSlots.undo, rc32 audit P2 #62).
            onUndo = { s -> scope.launch { status = StateSlots.undo(s); slotsVersion++ } },
            onDismiss = { statesDialog = false },
        )
    }
    if (raDialog) {
        RetroAchievementsDialog(
            summary = raSummary, achievements = raList, busy = raBusy, error = raError, tracked = session.isRun,
            onLogin = { u, p -> raBusy = "Signing in..."; raError = null; com.swordfish.libretrodroid.LibretroDroid.cheevosLogin(u, p, false) },
            onLogout = {
                com.swordfish.libretrodroid.LibretroDroid.cheevosUnloadGame(); com.swordfish.libretrodroid.LibretroDroid.cheevosLogout()
                raStore.clear(); raBusy = null; raRefresh(); status = "RetroAchievements: signed out."
            },
            onHardcore = { on ->
                raStore.hardcore = on
                com.swordfish.libretrodroid.LibretroDroid.cheevosSetHardcore(on)
                if (on) { if (slow != 1) { slow = 1; retro?.slowMotion = 1 }; if (rewinding) stopRewind() }
                raRefresh(); applyCheats()
                status = if (on) "Hardcore on: the game restarts from its last in-game save; cheats, rewind, slow motion and state loads are off." else "Hardcore off."
            },
            onDismiss = { raDialog = false },
        )
    }
    if (cheatsDialog) {
        CheatsDialog(
            title = "Cheats: " + session.title.substringBeforeLast('.'),
            platform = platform, cheats = cheats,
            onChange = { list ->
                cheats = list
                store.cheats.save(session.id, list)
                applyCheats()
            },
            onDismiss = { cheatsDialog = false },
        )
    }

    // Confirming a new run. This wipes the seed, the randomization and every
    // stat note, so it asks - and it says what survives, because the whole
    // point of saving in-game first is that the save file is NOT wiped.
    // 2.3: the game-over popup, latched the way each reference latches it
    // (GameOverLatch.kt). Blake, 2026-09-10: it used to follow the live outcome,
    // so the heal after a whiteout closed it. The dialog is in GameOverHost.kt.
    // Filed and logged once per run, the timer following the latch (GameOverLatch.read, rc32 audit P2 #41, #48).
    LaunchedEffect(view?.outcome, ndsState?.runOver, gameOverLatch.armed) {
        gameOverLatch.read(view?.outcome, ndsState?.runOver, runTimer, store, session, trackerRef, trackerState, ndsState, pastRunStore) { statMarks.dsProgress() }
    }
    GameOverHost(
        gameOverLatch, gameOverFamily(platform), hidden = streamClean,
        ndsState = ndsState, trackerState = trackerState, store = store, session = session, retro = retro,
        battleStartState = battleStartState, raHardcore = raHardcore, spriteFor = spriteFor,
        onStatus = { status = it }, onInspectLog = { logViewerFile = it },
        onNewGame = { confirmNewRun = true },
        onGrade = if (ndsState == null) { { side.scoreSheet = true } } else null,
        gameFrame = gameFrame,
    )
    logViewerFile?.let { f -> LogViewer(f, onClose = { logViewerFile = null }, tracker = trackerRef, badgeSet = trackerState?.badgeSet, spriteFor = spriteFor, party = trackerState?.party, nds = ndsTrackerRef, ndsParty = ndsState?.party) }

    typeDefenses?.let { (n, b) -> TypeDefensesDialog(n, b, onClose = { typeDefenses = null }) }

    SideScreenDialogs(side, trackerRef, ndsTrackerRef, trackerState, statMarks, enemySpecies, gbNames, spriteFor, runNow.attempt,
        timeMachine = timeMachine, snapshot = { runCatching { retro?.serializeState() }.getOrNull() },
        onRestore = { rp ->
            if (raHardcore) status = "Loading a state is off in RetroAchievements hardcore."
            else {
                val ok = retro?.unserializeState(rp.bytes) == true
                if (ok) store.runEvents(session)?.add(RunEvents.Kind.RESTORE, rp.label, "made ${rp.timestamp}")
                status = if (ok) "Restored." else "Could not restore. The save may be damaged."
            }
        },
        pastRunStore = pastRunStore,
        tourney = tourney, currentSeed = runNow.seed.ifEmpty { session.id },
        dsSpriteOf = { sp -> val c = androidx.compose.ui.platform.LocalContext.current; remember(sp) { PcAssets.dsSprite(c, sp, false) } })
    if (coverageCalc) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val nds = ndsTrackerRef
        val gba = trackerRef
        if (nds != null) {
            CoverageCalcDialog(
                seed = ndsCoverageSeed(ndsState?.party?.firstOrNull()?.moves ?: emptyList(), statMarks.dsHiddenPowerType()),
                allTypes = com.ironmonone.tracker.Gen3Types.ALL.map { com.ironmonone.tracker.Gen3Types.name(it) },
                compute = { types, _ -> nds.coverage(types) },
                name = { nds.speciesName(it) }, bst = { nds.speciesBst(it) },
                sprite = { id -> remember(id) { PcAssets.dsSprite(ctx, id, false) } },
                fullyEvolvedSupported = false, sortByBst = true,
                noDataNote = if (nds.hasSpeciesData()) null else "No species data for this game yet. Randomize it in Kaizo IronMON and the buckets fill in.",
                onClose = { coverageCalc = false },
            )
        } else if (gba != null) {
            CoverageCalcDialog(
                seed = CoverageCalc.gen3Seed(trackerState?.lead),
                allTypes = gba.typeNames,
                compute = { types, fe -> gba.coverage(types.mapNotNull { com.ironmonone.tracker.Gen3Types.idOf(it) }, fe) },
                name = { gba.speciesName(it) }, bst = { gba.baseStats(it)?.bst ?: 0 },
                sprite = { id -> spriteFor(id) },
                fullyEvolvedSupported = true, sortByBst = true,
                onClose = { coverageCalc = false },
            )
        } else coverageCalc = false
    }

    if (rulesDialog) {
        val fam = session.kind?.family ?: ""
        val runMode = if (session.isRun) store.loadLastRun()?.second?.let { RnqsInfo.of(store.settingsFile(it)).ruleset } else null
        RulesDialog(family = fam, mode = runMode, natDex = session.kind?.isNatDex == true, kind = session.kind, onDismiss = { rulesDialog = false })
    }

    if (gearDialog) {
        TrackerGearDialog(
            speciesName = { id -> trackerRef?.speciesName(id) ?: ndsTrackerRef?.speciesName(id) ?: gbNames?.invoke(id) ?: "#$id" },
            marks = statMarks,
            onCleared = { marksVersion++ },
            onRules = { gearDialog = false; if (NuzlockeTracking.inPlay()) NuzlockeLedgerRequest.openRules() else rulesDialog = true },
            onCoverage = { gearDialog = false; coverageCalc = true },
            onStats = if (platform == com.ironmonone.core.Platform.NDS) null else { { gearDialog = false; side.statsDialog = true } },
            onTrainers = if (trackerRef?.hasTrainerData == true) { { gearDialog = false; side.trainersDialog = true } } else null,
            onBattleDetails = if (trackerRef?.hasBattleDetails == true) { { gearDialog = false; side.battleDetailsDialog = true } } else null,
            onCatchRates = if (trackerRef?.hasCatchRates == true) { { gearDialog = false; side.catchHpAdjust = 0; side.catchRatesDialog = true } } else null,
            onNotebook = if (platform == com.ironmonone.core.Platform.NDS) null else { { gearDialog = false; side.notebookDialog = true } },
            onHeals = if (trackerRef?.hasCatchRates == true) { { gearDialog = false; side.healsDialog = true } } else null,
            onTimeMachine = { gearDialog = false; side.timeMachineDialog = true },
            onPastRuns = if (pastRunStore != null) { { gearDialog = false; side.pastRuns = true } } else null,
            onStatistics = if (pastRunStore != null) { { gearDialog = false; side.statistics = true } } else null,
            onEvoData = ndsState?.party?.firstOrNull()?.mon?.species?.let { sp -> { gearDialog = false; side.evoData = sp } },
            onTrackedPokemon = if (ndsState != null) { { gearDialog = false; side.trackedPokemon = true } } else null,
            onTourney = if (ndsState?.badgeSet == "HGSS") { { gearDialog = false; side.tourney = true } } else null,
            showTimerToggle = ndsState != null,
            onColorTheme = { gearDialog = false; side.colorTheme = true },
            showAutoThemes = true,
            runSettingsName = remember(session.id) { if (session.isRun) runCatching { store.loadLastRun()?.second }.getOrNull() else null },
            showBadgeOptions = ndsState?.badgeSet == "HGSS",
            gameBoy = platform == com.ironmonone.core.Platform.GBC,
            // The Gen 2 reference's IV estimate, on the lead; the Gen 1 one fails (IvEstimate).
            ivPotential = (gbRef as? com.ironmonone.tracker.GbcTracker)?.let { g -> { g.ivPotential(trackerState?.party?.firstOrNull()) } },
            ds = platform == com.ironmonone.core.Platform.NDS,
            onDismiss = { gearDialog = false },
        )
    }

    if (confirmNewRun) {
        // Shell look since 2026-09-27 (audit); it was the tracker's pixel font and palette.
        NewRunConfirmDialog(beforeRead = { persistSram() }, onConfirm = { confirmNewRun = false; newRun() }, onDismiss = { confirmNewRun = false })
    }
    PlayDialogs(
        ui,
        onRestart = { RetroAchievements.restartGame(retro) },
        onLoad = { loadState(it) },
        speeds = speedOptions(),
        speedNow = speedLabel,
        onSpeed = { pickSpeed(it) },
        onLayoutDone = { finishLayoutEdit(it) },
    )

    // Note editor for the species on screen. Notes are per species and per run,
    // sitting beside the stat marks.
    if (noteDialog && noteSpecies > 0) {
        var draft by remember(noteSpecies) { mutableStateOf(statMarks.noteFor(noteSpecies)) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { noteDialog = false; noteForSpecies = null }) {
            Column(
                Modifier.background(Pc.Ground)
                    .border(1.dp, Pc.Border).padding(12.dp)
            ) {
                PixText("NOTE", 10, Pc.Text)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = false,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PcTarget("SAVE") {
                        statMarks.setNote(noteSpecies, draft)
                        marksVersion++
                        noteDialog = false; noteForSpecies = null
                    }
                    PcTarget("CANCEL") { noteDialog = false; noteForSpecies = null }
                }
            }
        }
    }

    // The battery save is flushed whenever the app pauses - HOME, screen off,
    // app switch - which is when Android may kill the process without asking.
    DisposableEffect(Unit) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                persistSram(); autoSave()
                // The backup zip goes to the linked cloud file after the
                // saves are on disk; off the main thread, skipped when unchanged.
                CloudSync.syncInBackground(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            PlayLoading.clear()
            persistSram()
            autoSave()
            // Closed the normal way: nothing to resume at the next launch (CrashResume).
            CrashResume.left(store.playMarker, (context as? android.app.Activity)?.isFinishing != false,
                lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED)
            CloudSync.syncInBackground(context)
            releaseAllCoreKeys()
            QuickActions.clear()
            runCatching { com.swordfish.libretrodroid.LibretroDroid.cheevosUnloadGame() }
            retro?.let { v ->
                v.cheevosListener = null
                lifecycleOwner.lifecycle.removeObserver(v)
                // Leaving the Play tab must stop the core, not orphan its GL
                // thread against the singleton the next visit re-creates.
                runCatching { v.onDestroy() }
            }
        }
    }
}

// Direct native call, not view.sendKeyEvent: the view's queueEvent lambdas were
// never draining (multiple GLRetroView instances across tab visits), so key events
// queued forever. LibretroDroid.onKeyEvent is a static native on a singleton;
// Input::onKeyEvent just mutates a key set, safe to call off the GL thread.
/**
 * Every core key this app is currently holding down.
 *
 * A pad button that leaves composition mid-press - a tab switch, a rotation,
 * a controller connecting, the flee macro being cancelled - takes its gesture
 * coroutine with it and the matching UP is never sent. The key stays latched
 * in the core and the character walks, or mashes A, by itself. Nothing else
 * in the app ever cleared it.
 */
private val heldCoreKeys = java.util.Collections.synchronizedSet(HashSet<Int>())

/** Releases anything still held. Safe to call when nothing is. */
internal fun releaseAllCoreKeys() {
    val stuck = synchronized(heldCoreKeys) { heldCoreKeys.toList() }
    // Compose disposes effects in reverse declaration order, so this can run
    // after the core has been destroyed; the native singleton is gone by then
    // and the set is moot anyway. A stuck key is not worth a crash.
    stuck.forEach { runCatching { coreRelease(it) } }
    heldCoreKeys.clear()
}

private fun corePress(keyCode: Int) {
    heldCoreKeys.add(keyCode)
    com.swordfish.libretrodroid.LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_DOWN, keyCode)
    NewRunCombo.track(KeyEvent.ACTION_DOWN, keyCode)
    SpriteMotion.key(KeyEvent.ACTION_DOWN, keyCode)
}
private fun coreRelease(keyCode: Int) {
    heldCoreKeys.remove(keyCode)
    com.swordfish.libretrodroid.LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, keyCode)
    NewRunCombo.track(KeyEvent.ACTION_UP, keyCode)
    SpriteMotion.key(KeyEvent.ACTION_UP, keyCode)
}

/** GBA-hardware-flavoured key: framed square/pill, pixel label, hold semantics. */
@Composable
internal fun PadButton(
    label: String,
    keyCode: Int,
    wide: Boolean = false,
    translucent: Boolean = false,
    onB: () -> Unit = {},
    /** 1 is the designed size; portrait shrinks the pad on short screens. */
    scale: Float = 1f,
    /** The compact pad's SELECT/START: a short bar, not the 96dp landscape one. */
    small: Boolean = false,
    /** The compact pad's L/R: a 36dp square. Rarely pressed in these games. */
    mini: Boolean = false,
    skin: PadSkin = PadSkin.CLASSIC,
    /** Modern d-pad arrows: no shape of their own, drawn over the disc. */
    glyphOnly: Boolean = false,
    /** Touch reach past the drawn box on each side (PadGeometry.hitPadding); the drawing does not move. */
    hitX: androidx.compose.ui.unit.Dp = 0.dp,
    hitY: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // What a screen reader says. The visible labels are arrows and single
    // letters - "^", "<", "v" - which are meaningless read aloud, and the
    // d-pad is the app's primary control.
    val spoken = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_BUTTON_A -> "A button"
        KeyEvent.KEYCODE_BUTTON_B -> "B button"
        KeyEvent.KEYCODE_BUTTON_X -> "X button"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y button"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L shoulder"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R shoulder"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        else -> label
    }
    // pressHold uses pointerInput(Unit), which captures its lambdas from the
    // FIRST composition and never updates them. onB is `{ if (wildBattleNow)
    // flee() }`, so it froze wildBattleNow = false at load and the on-screen
    // B button's B-to-Run never fired once, in either orientation.
    val currentOnB by androidx.compose.runtime.rememberUpdatedState(onB)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val g = com.ironmonone.app.gen3.Gen3
    val frame = if (translucent) g.FrameDark.copy(alpha = 0.35f) else g.FrameDark
    val bevel = if (translucent) g.FrameBevel.copy(alpha = 0.35f) else g.FrameBevel
    val paper = if (translucent) g.Paper.copy(alpha = 0.30f) else g.Paper
    // Pressed is a visual state for the modern skin's glow; the key itself is
    // sent from the press handlers below regardless of skin.
    var pressed by remember { mutableStateOf(false) }
    val modern = skin == PadSkin.MODERN
    val outline = skin == PadSkin.OUTLINE
    val shape = when {
        outline && (mini || small || wide) -> androidx.compose.foundation.shape.RoundedCornerShape(50)
        outline && keyCode in DPAD_KEYS -> androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        outline -> androidx.compose.foundation.shape.CircleShape
        !modern -> androidx.compose.ui.graphics.RectangleShape
        mini || small || wide -> androidx.compose.foundation.shape.RoundedCornerShape(50)
        else -> androidx.compose.foundation.shape.CircleShape
    }
    val modernFill = Color.White.copy(alpha = if (pressed) 0.50f else if (glyphOnly) 0f else 0.22f)
    val modernEdge = Color.White.copy(alpha = if (glyphOnly) 0f else 0.40f)
    // The press is taken on the OUTER box, which reaches hitX/hitY past the
    // drawn one: Select/Start and L/R draw under 48dp (2026-09-27, audit).
    Box(
        Modifier
            .semantics { contentDescription = spoken }
            .pressHold(
                {
                    pressed = true
                    // A tick on press only, never on release: a d-pad held for
                    // a walk cycle must not buzz continuously, and a double
                    // buzz per tap reads as a stutter rather than a button.
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                    )
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_B) FleeOnB.pressed()
                    corePress(keyCode)
                },
                { pressed = false
                  coreRelease(keyCode)
                  if (keyCode == KeyEvent.KEYCODE_BUTTON_B) FleeOnB.released(currentOnB) },
            )
            .padding(horizontal = hitX, vertical = hitY),
    ) {
    Box(
        Modifier
            .padding(2.dp)
            .let {
                when {
                    mini -> PadGeometry.shoulder(skin).let { (w, h) -> it.width((w * scale).dp).height((h * scale).dp) }
                    small -> PadGeometry.selectStart(false, skin).let { (w, h) -> it.width((w * scale).dp).height((h * scale).dp) }
                    wide -> PadGeometry.selectStart(true, skin).let { (w, h) -> it.width((w * scale).dp).height((h * scale).dp) }
                    else -> it.size((PadGeometry.BUTTON * scale).dp)
                }
            }
            .let {
                when {
                    // Outline: a clear ground with a light edge; a press fills it.
                    outline -> it.background(Color.White.copy(alpha = if (pressed) 0.35f else 0.06f), shape)
                        .border(2.dp, Color.White.copy(alpha = 0.75f), shape)
                    modern -> it.background(modernFill, shape).border(1.dp, modernEdge, shape)
                    else -> it.background(frame).padding(2.dp).background(bevel).padding(2.dp).background(paper)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (modern || outline) {
            val glyph = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> "\u25B2"; KeyEvent.KEYCODE_DPAD_DOWN -> "\u25BC"
                KeyEvent.KEYCODE_DPAD_LEFT -> "\u25C0"; KeyEvent.KEYCODE_DPAD_RIGHT -> "\u25B6"
                else -> label
            }
            Text(
                glyph,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = ((if (wide || small) 11f else if (mini) 12f else if (glyphOnly) 14f else 20f) * scale).sp,
                color = Color.White.copy(alpha = if (glyphOnly) 0.75f else 0.95f),
            )
        } else Text(
            label,
            fontFamily = g.PixelFont,
            // Scales with the button, or SELECT reads SELEC at the floor.
            fontSize = ((if (wide || small) 8f else if (mini) 9f else 12f) * scale).sp,
            color = if (translucent) Color.White else g.Ink,
        )
    }
    }
}


/**
 * Portrait pad below the game. Tap = press; hold = the key stays down (walking).
 *
 * ONE band, three buttons tall, that uses the WIDTH: a plain D-pad on the
 * left, a middle column with SELECT/START over a row of L and R, and A/B on
 * the GBA's own diagonal on the right.
 *
 * L and R were first put in the D-pad's empty corners. Blake vetoed it:
 * a shoulder button on the movement cluster gets bumped while walking.
 * They sit level with Down and B now, where nothing passes over them.
 *
 * It replaced a four-row stack - D-pad, a gap, a shoulder/SELECT/START row,
 * and (with a controller) a HIDE PAD row - that stood 262dp tall with an
 * empty middle. On a phone shorter than the emulator the only way to fit
 * that was to shrink every button to 31dp, which is what Blake saw and
 * rightly refused. This band is 192dp, so the same phone keeps buttons near
 * 50dp, and on a normal screen nothing shrinks at all.
 *
 * [scale] comes from PortraitBudget. Every dimension here multiplies by it,
 * including the cell the spacers reserve, so the grid stays a grid.
 */
@Composable
private fun Pad(onB: () -> Unit = {}, scale: Float = 1f) {
    // A button is 56dp plus 2dp of padding each side: that is the grid cell.
    val cell = (60 * scale).dp
    Row(
        // The pad sits at the screen edges; a thumb sliding off the D-pad must not be a back gesture.
        Modifier.fillMaxWidth().systemGestureExclusion().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A plain plus. Nothing else lives on the movement cluster.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Spacer(Modifier.size(cell))
                PadButton("^", KeyEvent.KEYCODE_DPAD_UP, scale = scale)
                Spacer(Modifier.size(cell))
            }
            Row {
                PadButton("<", KeyEvent.KEYCODE_DPAD_LEFT, scale = scale)
                Spacer(Modifier.size(cell))
                PadButton(">", KeyEvent.KEYCODE_DPAD_RIGHT, scale = scale)
            }
            Row {
                Spacer(Modifier.size(cell))
                PadButton("v", KeyEvent.KEYCODE_DPAD_DOWN, scale = scale)
                Spacer(Modifier.size(cell))
            }
        }
        // Middle column, as tall as the D-pad so the bottom row lines up
        // with Down and B: SELECT over START, then L and R on that bottom row.
        Column(
            Modifier.weight(1f).height(cell * 3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            PadButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, small = true, scale = scale)
            PadButton("START", KeyEvent.KEYCODE_BUTTON_START, small = true, scale = scale)
            Row {
                PadButton("L", KeyEvent.KEYCODE_BUTTON_L1, mini = true, scale = scale)
                PadButton("R", KeyEvent.KEYCODE_BUTTON_R1, mini = true, scale = scale)
            }
        }
        // A up and right of B: the console's own diagonal, under the thumb.
        Column(horizontalAlignment = Alignment.End) {
            Row {
                Spacer(Modifier.width(cell / 2))
                PadButton("A", KeyEvent.KEYCODE_BUTTON_A, scale = scale)
            }
            Row {
                PadButton("B", KeyEvent.KEYCODE_BUTTON_B, onB = onB, scale = scale)
                Spacer(Modifier.width(cell / 2))
            }
        }
    }
}

/** Fullscreen overlay pad: translucent GBA controls at the screen's thumbs. */
@Composable
private fun OverlayPad(onB: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.systemGestureExclusion()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PadButton("^", KeyEvent.KEYCODE_DPAD_UP, translucent = true)
            Row {
                PadButton("<", KeyEvent.KEYCODE_DPAD_LEFT, translucent = true)
                Spacer(Modifier.size(56.dp))
                PadButton(">", KeyEvent.KEYCODE_DPAD_RIGHT, translucent = true)
            }
            PadButton("v", KeyEvent.KEYCODE_DPAD_DOWN, translucent = true)
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                PadButton("B", KeyEvent.KEYCODE_BUTTON_B, translucent = true, onB = onB)
                Spacer(Modifier.width(10.dp))
                PadButton("A", KeyEvent.KEYCODE_BUTTON_A, translucent = true)
            }
            Row(Modifier.padding(top = 8.dp)) {
                PadButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, wide = true,
                    translucent = true)
                PadButton("START", KeyEvent.KEYCODE_BUTTON_START, wide = true,
                    translucent = true)
            }
        }
    }
}

/** Tiny translucent utility button for the fullscreen strip. */
@Composable
private fun ChipRule() {
    Box(
        Modifier.width(1.dp).heightIn(min = Shell.touchTarget)
            .background(Color.White.copy(alpha = 0.35f)),
    )
}

/** The line between the FILE menu's groups. */
@Composable
private fun MenuRule() {
    Box(Modifier.width(1.dp).height(com.ironmonone.app.Shell.touchTarget).background(com.ironmonone.app.Shell.hairline))
}

/** A chip that acts while HELD: down starts, up stops. Rewind's control. */
@Composable
private fun HoldChip(label: String, onDown: () -> Unit, onUp: () -> Unit, big: Boolean = false) {
    val g = com.ironmonone.app.gen3.Gen3
    var held by remember { mutableStateOf(false) }
    // The press handler outlives a recomposition (pointerInput(Unit)), so it reads the callbacks of the composition
    // now, as PadButton does: it kept the first ones, and hardcore turned on with the FILE menu open left REWIND
    // rewinding with the check of the moment the menu opened (rc32 audit P3 #56).
    val down by androidx.compose.runtime.rememberUpdatedState(onDown)
    val up by androidx.compose.runtime.rememberUpdatedState(onUp)
    Box(
        Modifier
            .background(if (held) Pc.Gold.copy(alpha = 0.6f) else g.FrameDark.copy(alpha = if (big) 1f else 0.45f))
            .padding(1.dp)
            .background(g.Paper.copy(alpha = if (big) 1f else 0.25f))
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp)
            .holdUnlessScrolled({ held = true; down() }, { held = false; up() }),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u25C0\u25C0 " + com.ironmonone.app.Shell.label(label), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 13.sp,
            color = if (big) g.Ink else Color.White)
    }
}

/**
 * [pressHold] for a control that sits in a scrolling row or menu: it waits
 * 150 ms before acting, and a finger that moves past touch slop in that time
 * is a scroll, not a press. REWIND used plain pressHold, so a swipe that began
 * on it rewound the game (2026-09-27, audit). The pad keeps pressHold: a
 * button there must act on the instant.
 */
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

@Composable
private fun OverlayChip(label: String, description: String? = null, onClick: () -> Unit) {
    val g = com.ironmonone.app.gen3.Gen3
    Box(
        Modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .background(g.FrameDark.copy(alpha = 0.45f))
            .padding(1.dp)
            .background(g.Paper.copy(alpha = 0.25f))
            // clickable, not raw Release detection: the chip row scrolls
            // horizontally, and firing on ANY Release meant a scroll flick
            // that happened to end over LOAD instantly rewound the game.
            // clickable cancels properly when the scroll consumes the gesture.
            .clickable { onClick() }
            // 48dp is the floor for a touch target; these were ~29dp tall,
            // which is a hard thing to hit with a thumb while the other hand
            // is on the d-pad. Width still hugs the label so nine of them
            // still fit the strip.
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // No wrapping: when the chip row ran out of width the last chip broke
        // its label into a vertical column of letters ("M E N U") instead of
        // staying on one line.
        // Shell.label: the chips were written in capitals ("SLOT 1", "MUTED")
        // and the rest of the chrome is sentence case (2026-09-27, audit).
        Text(
            com.ironmonone.app.Shell.label(label), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 12.sp, color = Color.White,
            maxLines = 1, softWrap = false,
        )
    }
}

/**
 * The tracker's small pixel button with a 48dp press area around it: the
 * note editor's SAVE and CANCEL were a few dp tall (2026-09-27, audit). The
 * look is the tracker's and stays; only the target grows.
 */
@Composable
private fun PcTarget(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = com.ironmonone.app.Shell.touchTarget)
            .widthIn(min = com.ironmonone.app.Shell.touchTarget)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { PcSmallButton(label, onClick) }
}

/**
 * Press-and-hold semantics for a game button: down on touch, up on release.
 *
 * One gesture is ONE finger on THIS button, and that is the whole point.
 * PointerEvent.type is the type of the WHOLE event, not of this node's own
 * pointer, so the old loop lifted this button whenever any other finger was
 * released: B could not be held while a direction was, and holding B is how
 * the player brakes the bike in Gen 3 (Blake, 2026-09-15). awaitEachGesture
 * follows only the pointer that went down here, and waitForUpOrCancellation
 * returns on a consumed or cancelled gesture as well as on the release, so a
 * core key is still never left latched DOWN with the character walking on.
 */
private fun Modifier.pressHold(onDown: () -> Unit, onUp: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onDown()
            try {
                waitForUpOrCancellation()
            } finally {
                onUp()
            }
        }
    }

/** The four direction keys: the OUTLINE skin draws these square, the My Boy cross. */
private val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
