package com.ironmonone.app

import android.view.KeyEvent
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
        Column(modifier.fillMaxSize().padding(16.dp)) {
            if (!session.isRun) {
                EmptyState("That ROM is gone.",
                    "${session.title} is no longer in the library. Pick another on the ROMs tab.")
            } else if (failure != null) {
                EmptyState("New run failed.", failure)
            } else {
                EmptyState(
                    "No run yet.",
                    "Randomize one on the Run tab, then come back here.",
                )
            }
        }
        return
    }

    var retro by remember { mutableStateOf<GLRetroView?>(null) }
    // Speed and mute are the player's desk setup, not part of a run, so they
    // are restored rather than reset. NEW RUN is the most-pressed button in
    // the app and a seed can die in two minutes; going back to 1x-and-audible
    // on every attempt meant re-muting and re-pressing turbo each time.
    // Per-game settings (GameSettings): speed, mute, DS screen mode, pane width.
    val prefs0 = remember(session.id) { store.gameSettings(session) }
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
    var gameActive by remember { mutableStateOf(true) }

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
    var gearDialog by remember { mutableStateOf(false) }
    var rulesDialog by remember { mutableStateOf(false) }
    // The run, through one interface, whichever tracker is producing it.
    // Only one of the two states is ever non-null on a given platform.
    val view: com.ironmonone.tracker.RunView? = ndsState ?: trackerState

    /** The moment the last battle ended; flee refuses for 400 ms after it. */
    var battleEndedAt by remember { mutableStateOf(0L) }

    /** Guards against overlapping flee sequences while B is held. */
    var fleeing by remember { mutableStateOf(false) }

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
                    trackerRef?.isChoosingActionInWild()
                        ?: (view?.let { it.inBattle && it.isWildBattle } ?: false)

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

    var favoriteHit by remember { mutableStateOf<String?>(null) }
    // The ball call wins when there is one; otherwise the three favorites, the way the PC tracker's new-game screen lists them.
    val favoriteLine = favoriteHit ?: remember(session.id) { Favorites.line(store) }
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
    var cheats by remember(session.id) { mutableStateOf(if (cheatsAllowed) store.cheats.load(session.id) else emptyList()) }
    var cheatsDialog by remember { mutableStateOf(false) }
    // LAYOUT EDITOR. One layout per orientation and console; the pad renders
    // from it (FreePad) and the editor changes it in place, saved on DONE.
    val layoutKey = PadLayout.key(landscape, platform)
    var padLayout by remember(layoutKey) { mutableStateOf(store.layouts.load(layoutKey, landscape)) }
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
        coreValues.filterKeys { k ->
            k !in CoreOptions.APP_KEYS &&
                // DSi file paths only when DSi mode is on; a plain DS must not be told about files it lacks.
                (DsiMode.isOn(coreValues) || CoreOptions.forPlatform(platform).firstOrNull { it.key == k }?.group != CoreOptions.DSI_GROUP)
        }.map { (k, v) -> com.swordfish.libretrodroid.Variable(k, v) }
    var importSystemFileName by remember { mutableStateOf<String?>(null) }
    val systemFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        val name = importSystemFileName ?: return@rememberLauncherForActivityResult
        importSystemFileName = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    // systemDirectory is filesDir: the cores look for these names there.
                    File(context.filesDir, name).writeBytes(bytes); bytes.size
                }.getOrNull()
            }
            status = if (ok != null) "$name imported (%,d bytes). Takes effect on the next boot.".format(ok) else "Could not read that file."
        }
    }
    androidx.activity.compose.BackHandler(enabled = editingLayout) {
        store.layouts.save(layoutKey, padLayout); editingLayout = false
    }
    val layoutToolbar: @Composable (Modifier) -> Unit = { m ->
        LayoutToolbar(
            layout = padLayout, selected = selectedElement, landscape = landscape,
            isDs = platform == com.ironmonone.core.Platform.NDS,
            onEdit = { padLayout = it },
            onReset = { store.layouts.reset(layoutKey); padLayout = PadLayout.default(landscape, nds = platform == com.ironmonone.core.Platform.NDS); selectedElement = null },
            onDone = { store.layouts.save(layoutKey, padLayout); editingLayout = false; selectedElement = null },
            skin = padSkin, onSkin = { padSkin = it; store.setPadSkin(it) },
            modifier = m,
        )
    }
    fun applyCheats() {
        val r = retro ?: return
        runCatching {
            r.resetCheat()
            if (!cheatsAllowed) return
            var i = 0
            for (c in cheats) {
                val code = CheatStore.normalise(c.code, platform) ?: continue
                r.setCheat(i++, c.enabled, code)
            }
        }
    }
    var saveSlot by remember { mutableStateOf(1) }
    var slotsVersion by remember { mutableStateOf(0) }
    var statesDialog by remember { mutableStateOf(false) }

    // Per-species stat notes, the tracker's core mechanic. Reset per run, since a
    // new seed re-randomizes every base stat and old notes would mislead.
    val statMarks = remember(session.id) { StatMarks(store.marksFile(session)) }
    var marksVersion by remember { mutableStateOf(0) }   // bump to redraw cells
    // Encounter counts, so a species you keep running into is obvious.
    val encounters = remember { HashMap<Int, Int>() }
    // The level a species was at the PREVIOUS time it was met. The reference's
    // enemy card shows "Last seen Lv.N" in place of the HP line, so it needs
    // the level from before this encounter, not the current one.
    val lastSeenLevel = remember { HashMap<Int, Int>() }
    var enemyLastSeen by remember { mutableStateOf<Int?>(null) }
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
    // Tracker pane width, dragged by the divider. Narrower pane = bigger game.
    val windowWidthDp = androidx.compose.ui.platform.LocalConfiguration
        .current.screenWidthDp.toFloat()
    val windowHeightDp = androidx.compose.ui.platform.LocalConfiguration
        .current.screenHeightDp.toFloat()

    // The column starts as a FRACTION of the window, matching the reference
    // streaming layout, rather than a fixed 340dp that ate 40% of a phone in
    // landscape and letterboxed the game. Still draggable from there; keyed on
    // the window so a rotation re-derives a sensible default.
    // 2.2: the floating window's frame, per game, defaulting to where the dock would be.
    var floatFrame by remember(windowWidthDp, windowHeightDp, session.id) {
        mutableStateOf(prefs0.floatFrame?.let { FloatFrame(it[0], it[1], it[2], it[3]).clamped(windowWidthDp, windowHeightDp) } ?: FloatFrame.default(windowWidthDp, windowHeightDp))
    }
    var trackerWidth by remember(windowWidthDp, session.id) {
        mutableStateOf(windowWidthDp * (prefs0.trackerFraction ?: TRACKER_FRACTION))
    }
    // One writer for all four, so no toggle can forget to persist. Debounced
    // because the pane width changes on every drag event.
    LaunchedEffect(speed, muted, dsTopOnly, trackerWidth, floatFrame, session.id) {
        kotlinx.coroutines.delay(300)
        val fraction = (trackerWidth / windowWidthDp).takeIf { windowWidthDp > 0 && it in 0.1f..0.9f }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.saveGameSettings(session, GameSettings.Values(speed, muted, dsTopOnly, fraction, listOf(floatFrame.x, floatFrame.y, floatFrame.w, floatFrame.h)))
        }
    }

    // The notes for whoever is on screen right now. Keyed on both the species and
    // a version counter so a tap redraws the cell immediately.
    // Every change the panel would redraw for is a new snapshot for the
    // stream page. Built off the main thread; the hub dedupes identical
    // JSON so SSE only fires on a real change.
    LaunchedEffect(trackerState, ndsState, marksVersion, session.id) {
        val notes = com.ironmonone.app.stream.StreamSnapshot.Notes(
            marksOf = { statMarks.of(it) }, noteOf = { statMarks.noteFor(it) },
            movesSeenOf = { statMarks.movesSeenFor(it).map { m -> m.name } }, abilityOf = { statMarks.abilityFor(it) },
            encountersOf = { encounters[it] ?: 0 }, lastSeenLevelOf = { lastSeenLevel[it] },
            routeSeenOf = { statMarks.seenOnRoute(it).size },
        )
        val run = com.ironmonone.app.stream.StreamSnapshot.Run(
            session.title, platform.name, store.attempt(), session.tracked,
            if (session.isRun) store.lastSeed() else null)
        val gba = trackerState; val nds = ndsState; val ref = trackerRef
        val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.ironmonone.app.stream.Json.write(
                com.ironmonone.app.stream.StreamSnapshot.build(run, gba, nds, notes, ref))
        }
        com.ironmonone.app.stream.StreamHub.publish(json, run.attempt)
    }
    // The post-game browser's data: every species as randomized. Once per
    // tracker, since it reads ROM tables for the whole dex.
    LaunchedEffect(trackerRef, ndsTrackerRef) {
        val ref = trackerRef; val nref = ndsTrackerRef
        if (ref == null && nref == null) return@LaunchedEffect
        com.ironmonone.app.stream.StreamHub.dex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                com.ironmonone.app.stream.Json.write(com.ironmonone.app.stream.StreamSnapshot.dex(
                    ref, nref, if (ref != null) (if (ref.expandedSpeciesIds) 1284 else 412) else 650))
            }.getOrDefault("[]")
        }
    }
    val enemySpecies = view?.enemySpeciesId ?: -1
    val enemyMarks = remember(enemySpecies, marksVersion) {
        if (enemySpecies > 0) statMarks.of(enemySpecies) else IntArray(StatMarks.COUNT)
    }
    val enemyEncounters = encounters[enemySpecies] ?: 0
    val enemyNote = remember(enemySpecies, marksVersion) {
        if (enemySpecies > 0) statMarks.noteFor(enemySpecies) else ""
    }
    var noteDialog by remember { mutableStateOf(false) }

    // Hardware keys route to the game ONLY while this screen exists and no
    // text dialog is open. Without the gate a Bluetooth keyboard could never
    // type into any field in the app: dispatchKeyEvent consumed Z/X/arrows/
    // Enter app-wide and fed them to a core that might not even be on screen.
    // Keyed on whether the dialog is ACTUALLY on screen, not on the intent to
    // open it. The dialog renders only when an enemy is decoded, so keying on
    // `noteDialog` alone left the flag false with no dialog to raise it again
    // - and from that moment every hardware key was dead with no way back
    // except leaving the tab, which reboots the core.
    val noteDialogVisible = noteDialog && enemySpecies > 0
    // Any dialog with a text field takes the keyboard back from the game:
    // the note, the cheats (code entry) and the settings (link address).
    val textDialogOpen = noteDialogVisible || cheatsDialog || settingsDialog || raDialog
    DisposableEffect(textDialogOpen) {
        KeyBindings.routeToGame = !textDialogOpen
        onDispose { KeyBindings.routeToGame = false }
    }
    // If the enemy disappears mid-edit the dialog unmounts; drop the intent
    // too so a later tap can open it again.
    LaunchedEffect(enemySpecies) { if (enemySpecies <= 0) noteDialog = false }
    // Fleeing is wild-only on BOTH consoles; a trainer battle never offers it.
    val inBattleNow = view?.inBattle == true
    // The reference snapshots the core when a battle begins
    // (Battle.beginNewBattle -> GameOverScreen.createTempSaveState) so the
    // game-over screen can offer "Retry the battle". Held in memory only, for
    // the current battle; a new battle replaces it, a new run drops it.
    var battleStartState by remember(session.id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(inBattleNow) {
        if (!inBattleNow) battleEndedAt = android.os.SystemClock.uptimeMillis()
        else battleStartState = runCatching { retro?.serializeState() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
    val wildBattleNow = view?.let { it.inBattle && it.isWildBattle } == true

    // Count an encounter once per arrival, not once per poll tick.
    // Persist what this enemy uses as it uses it, so the next encounter with
    // the species starts informed - the reference's Tracker.TrackMove.
    // The DS enemy's moves are already used-only (NdsTracker.usedOnly), so every one is a sighting.
    LaunchedEffect(ndsState?.enemy?.moves) {
        val e = ndsState?.enemy
        if (e != null && statMarks.addMovesSeen(e.mon.species, e.moves.map { it.id to it.name }, e.mon.level)) marksVersion++
    }
    LaunchedEffect(trackerState?.enemy?.movesSeen) {
        val e = trackerState?.enemy
        if (e != null && statMarks.addMovesSeen(e.species, e.moveRows.map { it.id to it.name }, e.level)) marksVersion++
    }

    // A battle script revealing an ability is the ONLY thing that unlocks
    // the enemy's ability line - the reference's TrackAbility rule.
    LaunchedEffect(trackerState?.abilityRevealed) {
        trackerState?.abilityRevealed?.let { (sp, name) ->
            if (statMarks.revealAbility(sp, name)) marksVersion++
        }
    }
    LaunchedEffect(ndsState?.abilityRevealed) {
        ndsState?.abilityRevealed?.let { (sp, name) ->
            if (statMarks.revealAbility(sp, name)) marksVersion++
        }
    }

    LaunchedEffect(enemySpecies) {
        if (enemySpecies > 0 && enemySpecies != lastCountedSpecies) {
            // Read the previous sighting BEFORE recording this one.
            enemyLastSeen = lastSeenLevel[enemySpecies]
            trackerState?.enemy?.level?.let { lastSeenLevel[enemySpecies] = it }
            encounters[enemySpecies] = (encounters[enemySpecies] ?: 0) + 1
            // Also record it against the map it was met on, which is the other
            // half of the reference's route info: what this route actually held.
            trackerState?.mapId?.let { statMarks.seeOnRoute(it, enemySpecies) }
            lastCountedSpecies = enemySpecies
        } else if (enemySpecies <= 0) {
            lastCountedSpecies = -1
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

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
                PcAssets.gbaSprite(context, sp) else null
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
            kotlinx.coroutines.delay(700)
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
                t.lossCondition = TrackerOptions.lossCondition
                ndsState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { t.read() }.getOrNull()
                } ?: ndsState
                Demo.mode?.takeIf { it.startsWith("nds") }?.let { m -> ndsState = runCatching { Demo.nds(t, m) }.getOrNull() ?: ndsState }
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
            // ROM file itself. Produces the same TrackerState as Gen 3, so
            // the panel below needs nothing; trackerRef stays null, and the
            // GBA-only lookups it powers (descriptions, learnsets) are off.
            val romBytes = runCatching { rom.readBytes() }.getOrNull() ?: return@LaunchedEffect
            // Gen 2 (Crystal, Gold, Silver) or Gen 1 (Red, Blue, Yellow), picked from the header.
            val gbc = if (com.ironmonone.tracker.Gen2Map.forRom(romBytes) != null) com.ironmonone.tracker.GbcTracker(reader, romBytes) else null
            val gb1 = if (gbc == null) com.ironmonone.tracker.Gen1Tracker(reader, romBytes) else null
            val read: () -> com.ironmonone.tracker.TrackerState = { gbc?.read() ?: gb1!!.read() }
            gbLookup = { id -> gbc?.moveRowFor(id) ?: gb1?.moveRowFor(id) }
            gbNames = { id -> gbc?.speciesName(id) ?: gb1?.speciesName(id) ?: "#$id" }
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
            }
        }
        var tracker: com.ironmonone.tracker.GbaTracker? = null
        while (true) {
            val visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!visible) { kotlinx.coroutines.delay(1000); continue }
            kotlinx.coroutines.delay(
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
                // Favorites vs the three starter balls, once per load. Only a MATCH
                // is revealed - never the full ball contents. IronMON honesty.
                tracker?.let { t ->
                    val favs = store.loadFavorites()
                    if (favs.isNotEmpty()) {
                        favoriteHit = runCatching {
                            t.starters().firstOrNull { it.name.lowercase() in favs }
                                ?.let { "FAVORITE! ${it.name} IN ${it.ball}" }
                        }.getOrNull()
                    }
                }
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
    val dsLayoutName = padLayout.dsLayout ?: if (landscape) "hybrid-top" else "top-bottom"
    LaunchedEffect(dsLayoutName, retro, platform) {
        if (platform != com.ironmonone.core.Platform.NDS) return@LaunchedEffect
        retro?.updateVariables(
            com.swordfish.libretrodroid.Variable(
                "melonds_number_of_screen_layouts", "1"),
            com.swordfish.libretrodroid.Variable("melonds_screen_layout1", dsLayoutName),
        )
    }

    var confirmNewRun by remember { mutableStateOf(false) }
    /** The randomizer log open full screen (the game-over screen's Inspect the log), or null. */
    var logViewerFile by remember { mutableStateOf<java.io.File?>(null) }

    /**
     * The GBA battery save, persisted per game.
     *
     * mGBA exposes save data ONLY through the frontend SRAM interface -
     * unlike melonDS, which writes its own .sav - and nothing here ever
     * called it, so every FireRed/Emerald in-game save lived purely in core
     * memory and died with the process. The NEW RUN dialog promised the
     * in-game save was kept; on GBA that was false every time.
     *
     * Written on pause, on leaving the tab, and before a reboot; loaded into
     * the core at view creation via saveRAMState. Kept across NEW RUN on
     * purpose: save in-game, re-roll, press Continue is the intended fast
     * restart, skipping the intro and clock.
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
            // Never clobber a good save with an empty buffer from a core that
            // is mid-teardown or not yet running.
            if (bytes.isEmpty()) return
            val f = sramFile()
            val tmp = File(f.parentFile, f.name + ".tmp")
            f.parentFile?.mkdirs()
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    fun newRun() {
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
                    val (romId, settingsName) = store.loadLastRun()
                        ?: error("Randomize once on the Run tab first.")
                    val prepared = store.listPrepared()
                        .firstOrNull { it.first.id == romId }
                        ?: error("Prepared ROM missing - use the Run tab.")
                    val settings = store.listSettings()
                        .firstOrNull { it.name == settingsName }
                        ?: error("Settings \"$settingsName\" missing.")
                    store.rotateRuns(prepared.first)
                    val dest = store.currentRunFor(prepared.first)
                    val seed = java.security.SecureRandom().nextLong()
                    com.ironmonone.app.engine.Randomizers
                        .randomize(prepared.first, prepared.second, settings, dest, seed, secondPass = store.secondPassSettings(prepared.first))
                    seed
                }
            }
            ok.onSuccess { seed ->
                status = "New run (seed %016x). Rebooting…".format(seed)
                trackerState = null
                ndsState = null
                ndsTrackerRef = null
                favoriteHit = null
                battleStartState = null   // a state from another randomization must never be restored
                statMarks.clear()
                encounters.clear()
            lastSeenLevel.clear()
            enemyLastSeen = null
                lastCountedSpecies = -1
                marksVersion++
                store.setLastRunError(null)
                store.bumpAttempt()
                store.saveLastSeed(seed)
                gameKeyForRom++
                gameKey++
                gameActive = true
                status = "New run (seed %016x). New ball call incoming.".format(seed)
            }.onFailure {
                status = it.message
                // Survives the composition being torn down by the empty state.
                store.setLastRunError(it.message ?: it.toString())
                // The core was already stopped; bring the OLD run back up
                // rather than leaving a dead screen.
                gameActive = true
            }
        }
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
            status = "Save failed - game not running."
            return
        }
        captureFrame { frame ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val f = slotFile(slot).apply { parentFile?.mkdirs() }
                // The state being overwritten survives as the slot's backup (UNDO).
                if (slot != StateSlots.AUTO) target.keepBackup()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeBytes(st)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
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
     * exit). Silent, no thumbnail capture (the surface may be gone), and
     * skipped when the core cannot answer. What RESUME in STATES loads.
     */
    fun autoSave() {
        val v = retro ?: return
        // useEmulationThread = false, the same reason as persistSram: this runs
        // from ON_PAUSE and onDispose, when the emulation thread is already
        // paused, and the default hops to it and waits on a latch that never
        // fires. That was a confirmed ANR (input dispatch timed out, 5s).
        val st = runCatching { v.serializeState(useEmulationThread = false) }.getOrNull()
        if (st == null || st.isEmpty()) return
        val f = slotFile(StateSlots.AUTO)
        val stamp = store.stateStamp(session)
        val stampFile = slotStamp(StateSlots.AUTO)
        Thread {
            runCatching {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeBytes(st)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                stampFile.writeText(stamp)
            }
        }.start()
    }

    fun loadState(which: Int = saveSlot) {
        val f = slotFile(which)
        if (!f.exists()) { status = "Slot $which is empty."; return }
        if (raHardcore) { status = "Loading a state is off in RetroAchievements hardcore."; return }
        // A save state restores the whole of RAM. Loaded against a DIFFERENT
        // randomization it puts one game's memory under another game's data
        // tables, and the tracker then reads a party that cannot exist -
        // species past the end of the dex, impossible levels, ids that change
        // between reads. It looks like a tracker bug and is not one.
        val want = store.stateStamp(session)
        val got = runCatching { slotStamp(which).readText().trim() }.getOrNull()
        if (got != null && got != want) {
            status = "Slot $which belongs to a different run - not loaded."
            return
        }
        if (got == null) {
            status = "Slot $which predates run stamping - not loaded."
            return
        }
        val slot = which
        if (which != StateSlots.AUTO) saveSlot = which
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // DS states run tens of MB; reading them on the main thread froze
            // the UI for seconds per tap.
            val bytes = f.readBytes()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // The core reports whether it accepted the state; claiming
                // "Loaded" when it refused left the player mid-game with no
                // idea the rewind never happened.
                val ok = retro?.unserializeState(bytes) == true
                status = if (ok) (if (slot == StateSlots.AUTO) "Resumed from the auto-save." else "Loaded slot $slot.")
                else "Load failed - the core refused that state."
            }
        }
    }

    /** Audio is on only when the user has not muted AND we are not in turbo. */
    fun applyAudio() {
        retro?.audioEnabled = !muted && speed == 1 && slow == 1 && !rewinding
    }

    fun cycleSpeed() {
        // GBA goes to 16x. The DS core is capped at 4x: raising melonDS past it
        // killed the GL thread outright (SIGSEGV in GLThread the instant the
        // multiplier changed, reproduced 2026-08-30), and a crash that eats the
        // run is worse than a slower fast-forward. Revisit if the core is fixed.
        // ... then past the top turbo come the two slow-motion steps, 1/2 and
        // 1/4, before wrapping to 1x. Slow motion is a session thing, never
        // persisted: a game that opened at quarter speed would read as broken.
        val max = platform.maxTurbo
        when {
            slow == 4 -> { slow = 1; speed = 1 }
            slow == 2 -> { slow = 4 }
            speed >= max -> { speed = 1; slow = if (raHardcore) 1 else 2 }
            else -> speed *= 2
        }
        retro?.frameSpeed = speed
        retro?.slowMotion = slow
        // IronMON default: silence during turbo, full audio at 1x. Never chipmunk.
        applyAudio()
    }
    val speedLabel = when (slow) { 2 -> "\u00BDX"; 4 -> "\u00BCX"; else -> "${speed}X" }

    // RUMBLE. The core's rumble state changes arrive as events; the phone's
    // motor follows: any strength above zero buzzes at that amplitude until
    // the core sets zero. Off with the SETTINGS row.
    LaunchedEffect(retro, coreValues[CoreOptions.RUMBLE_KEY]) {
        val r = retro ?: return@LaunchedEffect
        if (coreValues[CoreOptions.RUMBLE_KEY] == "off") return@LaunchedEffect
        val vib = PhoneHardware.vibrator(context) ?: return@LaunchedEffect
        r.getRumbleEvents().collect { ev -> PhoneHardware.rumble(vib, ev.strengthWeak, ev.strengthStrong) }
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
    // Direct serialize (no emulation-thread hop) for the same reason as
    // persistSram: this runs on the main thread on a timer.
    fun startRewind() {
        if (!rewindAllowed) { status = "Rewind is off on a tracked game."; return }
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
    LaunchedEffect(retro, rewindAllowed, session.id) {
        val r = retro ?: return@LaunchedEffect
        if (!rewindAllowed) return@LaunchedEffect
        val (_, interval) = RewindBuffer.policy(platform)
        while (true) {
            kotlinx.coroutines.delay(interval)
            if (rewinding || speed != 1 || slow != 1 || !gameActive) continue
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
            val st = runCatching { r.serializeState(useEmulationThread = false) }.getOrNull() ?: continue
            rewind.push(st)
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
        // melonDS dies above 4x (see cycleSpeed). Persistence made it possible
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
        StateSlots.auto(context.filesDir, session).takeIf { it.exists }?.let {
            status = "Auto-save from ${it.savedLabel()}: FILE > STATES > RESUME."
        }
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
        if (n > 0) status = "Sprites read from your ROM: $n."
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
        onDispose { FleeOnB.onFlee = null }
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
            Text("Stream: " + com.ironmonone.app.stream.StreamHub.url() + "  (Back leaves CLEAN VIEW)",
                fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 8.sp,
                color = Pc.Gold, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Row(
            // Excluded from the system back gesture: a fast fling on this strip
            // starting near the screen edge used to leave the game (2026-09-06).
            Modifier.fillMaxWidth().systemGestureExclusion().padding(8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.ironmonone.app.gen3.Gen3Button(speedLabel, accent = speed > 1 || slow > 1,
                onClick = { cycleSpeed() })
            // Slot picker: shows a dot when that slot holds a state.
            val slotHas = remember(saveSlot, slotsVersion) {
                (1..StateSlots.COUNT).map { slotFile(it).exists() }
            }
            com.ironmonone.app.gen3.Gen3Button(
                "SLOT $saveSlot" + if (slotHas[saveSlot - 1]) "*" else "",
                onClick = { saveSlot = if (saveSlot >= StateSlots.COUNT) 1 else saveSlot + 1 })
            com.ironmonone.app.gen3.Gen3Button("STATES", onClick = { statesDialog = true })
            com.ironmonone.app.gen3.Gen3Button("SAVE", onClick = { saveState() })
            com.ironmonone.app.gen3.Gen3Button("LOAD", onClick = { loadState() })
            com.ironmonone.app.gen3.Gen3Button("RESET", onClick = { retro?.reset() })
            // 2.4: the rules for this game and mode, readable mid-run.
            if (session.tracked) com.ironmonone.app.gen3.Gen3Button("RULES", onClick = { rulesDialog = true })
            if (rewindAllowed) HoldChip("REWIND", onDown = { startRewind() }, onUp = { stopRewind() }, big = true)
            // DS: collapse the touch screen so the top screen fills the frame.
            if (dsScreens) {
                com.ironmonone.app.gen3.Gen3Button(
                    if (dsTopOnly) "2 SCREEN" else "1 SCREEN",
                    accent = dsTopOnly,
                    onClick = { dsTopOnly = !dsTopOnly })
            }
            if (session.isRun) com.ironmonone.app.gen3.Gen3Button("NEW RUN", accent = true,
                onClick = { confirmNewRun = true })
            com.ironmonone.app.gen3.Gen3Button(if (muted) "UNMUTE" else "MUTE",
                accent = muted, onClick = { muted = !muted; applyAudio() })
            com.ironmonone.app.gen3.Gen3Button("CAM", accent = facecam,
                onClick = { facecam = !facecam })
            // Streaming: the web tracker for OBS, and the capture layout.
            com.ironmonone.app.gen3.Gen3Button(if (streamOn) "STREAM ON" else "STREAM",
                accent = streamOn, onClick = {
                    if (streamOn) { com.ironmonone.app.stream.StreamHub.stop(); streamOn = false; status = "Stream server stopped." }
                    else {
                        val url = com.ironmonone.app.stream.StreamHub.start()
                        streamOn = url != null
                        status = if (url != null) "OBS browser source: $url" else "Port ${com.ironmonone.app.stream.StreamHub.PORT} is busy."
                    }
                })
            com.ironmonone.app.gen3.Gen3Button("CLEAN VIEW", onClick = { onClean(true); menuOpen = false })
            com.ironmonone.app.gen3.Gen3Button("EDIT LAYOUT", onClick = { editingLayout = true; menuOpen = false })
            com.ironmonone.app.gen3.Gen3Button("SETTINGS", onClick = { settingsDialog = true })
            com.ironmonone.app.gen3.Gen3Button(
                if (!raSummary.loggedIn) "ACHIEVEMENTS" else if (raSummary.gameLoaded) "ACHIEVEMENTS ${raSummary.unlocked}/${raSummary.total}" else "ACHIEVEMENTS ON",
                accent = raSummary.loggedIn && raSummary.gameLoaded,
                onClick = { raRefresh(); raDialog = true })
            com.ironmonone.app.gen3.Gen3Button(
                if (!cheatsAllowed) "CHEATS OFF" else if (cheats.any { it.enabled }) "CHEATS (${cheats.count { it.enabled }})" else "CHEATS",
                accent = cheatsAllowed && cheats.any { it.enabled },
                onClick = {
                    if (cheatsAllowed) cheatsDialog = true
                    else status = "Cheats are off on a tracked game. Play it from the library untracked, or a hack, to use them."
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
            // shareable without a computer, same as the crash report.
            com.ironmonone.app.gen3.Gen3Button("ROUTE LOG", onClick = {
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
        status?.let {
            Text(it, Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Shell.accentOnNight)
        }
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
    val leadMoveTypes = trackerState?.party?.firstOrNull()?.moveRows
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
                      FacecamDocked(onDenied = {
                          facecam = false
                          status = "Camera permission denied."
                      })
                  }
                  Row(
                      Modifier.fillMaxWidth()
                          .padding(horizontal = 4.dp, vertical = 1.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      // The only attempt counter in the app, and no seed: the
                      // seed is not something you act on mid-run.
                      Text(
                          "ATTEMPT ${store.attempt()}",
                          fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont,
                          fontSize = 8.sp, color = Pc.Text,
                      )
                      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          if (dsScreens) {
                              OverlayChip(if (dsTopOnly) "2 SCR" else "1 SCR") {
                                  dsTopOnly = !dsTopOnly
                              }
                          }
                          OverlayChip("▶") { trackerOpen = false }
                      }
                  }
                  if (dsScreens) NdsTrackerPanel(
                      ndsState, onFlee = { flee() }, onGear = { gearDialog = true },
                      enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                      onCycleMark = { i ->
                          ndsState?.enemy?.let { statMarks.cycle(it.mon.species, i) }
                          marksVersion++
                      },
                      enemyNote = enemyNote,
                      onEditNote = { noteDialog = true },
                      attempt = store.attempt(),
                      coverage = ndsCoverage,
                        revealedEnemyAbility = ndsState?.enemy
                            ?.let { statMarks.abilityFor(it.mon.species) },
                        movesSeenRunWide = ndsState?.enemy?.let { statMarks.movesSeenFor(it.mon.species) } ?: emptyList(),
                        moveInfoFor = { id -> ndsTrackerRef?.moveInfoFor(id) },
                  )
                  else TrackerPanel(
                      trackerState, onFlee = { flee() }, ballCall = ballCall, onGear = { gearDialog = true },
                onRerollBall = { ballReroll++ },
                movesSeenRunWide = trackerState?.enemy
                    ?.let { statMarks.movesSeenFor(it.species) } ?: emptyList(),
                moveRowFor = { id -> trackerRef?.moveRowFor(id) ?: gbLookup?.invoke(id) },
                revealedEnemyAbility = trackerState?.enemy
                    ?.let { statMarks.abilityFor(it.species) },
                routeName = trackerState?.routeName,
                routeSeen = trackerState?.mapId?.let { statMarks.seenOnRoute(it).size } ?: 0,
                routeTotal = trackerState?.routeSpecies?.size ?: 0,
                routeTrainers = trackerState?.routeTrainers?.size ?: 0,
                routeBosses = trackerState?.routeBosses ?: 0,
                steps = trackerState?.steps ?: 0,
                onMoveDescription = { id -> trackerRef?.moveDescription(id) },
                onAbilityDescription = { name ->
                    trackerRef?.let { t -> t.abilityIdOf(name)?.let(t::abilityDescription) }
                },
                onWeight = { sp -> trackerRef?.weight(sp) },
                onEvolution = { sp -> trackerRef?.evolution(sp) },
                onEffectiveness = { sp ->
                    trackerRef?.effectivenessAgainst(sp) ?: emptyMap()
                },
                onMoveLevels = { sp ->
                    trackerRef?.learnset(sp)?.map { it.first } ?: emptyList()
                },
                onSpeciesNote = { sp -> statMarks.noteFor(sp) },
                onRouteAreas = {
                    trackerState?.mapId?.let { trackerRef?.routeEncounterAreas(it) }
                        ?: emptyMap()
                },
                onRouteSeenSet = {
                    trackerState?.mapId?.let { statMarks.seenOnRoute(it) } ?: emptySet()
                },
                onSpeciesName = { sp -> trackerRef?.speciesName(sp) ?: "#$sp" },
                      favoriteLine = favoriteLine, spriteFor = spriteFor,
                      enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                      enemyLastSeenLevel = enemyLastSeen,
                      onCycleMark = { i ->
                          trackerState?.enemy?.let { statMarks.cycle(it.species, i) }
                          marksVersion++
                      },
                      enemyNote = enemyNote,
                      onEditNote = { noteDialog = true },
                      attempt = store.attempt(),
                      coverage = coverage,
                      // Landscape: your lead and the enemy together.
                      stackBoth = true,
                  )
                  }

    // The tracker pane, hoisted so it can be laid out two ways: as a
    // full-height column beside the game, or - for DS landscape - as a
    // TOP-anchored overlay, so the bottom screen can sit BELOW it.
    val trackerPane: @Composable () -> Unit = {
        Row {
          if (!session.tracked) {
              // Nothing to track: the game takes the whole width.
          } else if (TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING) {
              // 2.2: the window floats over the game; nothing sits in this row.
          } else if (trackerOpen && TrackerOptions.landscapeTracker == LandscapeTracker.DOCKED) {
              // Drag handle: pulling it right shrinks the tracker, and the game
              // column is weighted so it takes back every pixel given up.
              // 24dp of grab, not 10, with a visible ridge. At 10dp wide
              // and marked with a single 9sp glyph this was neither findable
              // nor comfortably draggable - the standard grab strip is 24dp
              // and the ridge is what says "pull me".
              Box(
                  Modifier.width(24.dp)
                      .fillMaxHeight()
                      .semantics {
                          contentDescription =
                              "Resize tracker. Drag left or right. " +
                              "Double tap to reset the split."
                      }
                      .background(Pc.Ground)
                      // Double tap restores the default split.
                      //
                      // The drag was one-way in practice: trackerWidth only
                      // changed by dragging and only re-derived on a rotation,
                      // so a stray pull left the game letterboxed with no way
                      // back short of rotating the phone twice. A resize
                      // control with no reset is a trap.
                      .pointerInput(windowWidthDp) {
                          detectTapGestures(onDoubleTap = {
                              trackerWidth = windowWidthDp * TRACKER_FRACTION
                          })
                      }
                      .pointerInput(Unit) {
                          detectDragGestures { change, drag ->
                              change.consume()
                              // Clamped against the ACTUAL window, keeping a
                              // 160dp minimum for the game. The old fixed
                              // 150..520 range left 110dp of game on a 640dp
                              // window and zero on anything under 530dp,
                              // because the game column is weight(1f) with no
                              // minimum of its own.
                              val maxTracker =
                                  (windowWidthDp - 170f).coerceAtLeast(150f)
                              trackerWidth = (trackerWidth - drag.x / density)
                                  .coerceIn(150f, maxTracker)
                          }
                      },
                  contentAlignment = Alignment.Center,
              ) {
                  // Three short bars: a grip, readable at a glance, rather
                  // than a text glyph that looked like a stray character.
                  Column(
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                      repeat(3) {
                          Box(
                              Modifier.width(10.dp).height(2.dp)
                                  .background(Color.White.copy(alpha = 0.75f)),
                          )
                      }
                  }
              }
              Column(
                  // A fraction of the window, not a dragged pixel width, so
                  // the split matches the reference layout on any screen.
                  Modifier.width(trackerWidth.dp)
                      // DS landscape anchors this to the top and lets it wrap,
                      // so its opaque background stops where the content stops
                      // and the core's bottom screen shows BELOW it. Filling
                      // the height painted over exactly the area meant to
                      // display it.
                      .fillMaxHeight()
                      .background(Pc.Page)
                      .verticalScroll(rememberScrollState())
              ) {
                  trackerContent()
              }
          } else {
              // Collapsed: a thin tab on the right edge, tap to bring it back.
              Box(
                  Modifier.width(26.dp).fillMaxHeight()
                      .background(Pc.Ground)
                      .clickable { trackerOpen = true; TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED; TrackerOptions.save() },
                  contentAlignment = Alignment.Center,
              ) {
                  Text("◀", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont,
                      fontSize = 12.sp, color = Color.White)
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
                fullscreen -> Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                dsScreens -> Modifier.fillMaxWidth().weight(1f)
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
                landscape -> Modifier.fillMaxSize()
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
                else -> Modifier.fillMaxWidth(budget.gameFraction)
                    .align(Alignment.CenterHorizontally).aspectRatio(platform.aspect)
                    .background(com.ironmonone.app.gen3.Gen3.FrameDark).padding(3.dp)
            },
        ) {
            if (gameActive) androidx.compose.runtime.key(gameKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize()
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
                                com.swordfish.libretrodroid.Variable("melonds_number_of_screen_layouts", "1"),
                                com.swordfish.libretrodroid.Variable("melonds_screen_layout1", dsLayoutName),
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
                            view.cheevosListener = RetroAchievements.listener(
                                mainPost = { r -> view.post(r) },
                                onEvent = { type, title, desc, points, badge, result ->
                                    view.post {
                                        when (type) {
                                            RetroAchievements.EV_LOGIN_DONE -> {
                                                raBusy = null
                                                if (result == 0) { if (badge.isNotBlank()) raStore.save(title, badge); status = "RetroAchievements: signed in as $title." }
                                                else { raStore.clear(); raError = desc.ifBlank { "Sign-in failed (error $result)." }; status = "RetroAchievements: $raError" }
                                                raRefresh()
                                                if (result == 0 && !session.isRun) { raBusy = "Looking up this game..."; com.swordfish.libretrodroid.LibretroDroid.cheevosLoadGame(rom.absolutePath, RetroAchievements.consoleId(platform)) }
                                            }
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

            // The floating bubble is only for layouts with no tracker column
            // to dock into - portrait, or landscape with the tracker collapsed.
            // With the column open the camera lives in it (see below).
            if (facecam && !streamClean && !(landscape && trackerOpen)) {
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    FacecamBubble(onDenied = {
                        facecam = false
                        status = "Camera permission denied."
                    })
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
                            .then(Modifier.alpha(if (editingLayout) 1f else controlAlpha)),
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

                // The FILE button is in the top bar in landscape too, so its
                // menu renders here. Offset below the chip strip so the two
                // do not sit on top of each other.
                if (menuOpen) {
                    Box(Modifier.align(Alignment.TopStart).padding(top = 54.dp)) {
                        FileMenu()
                    }
                }

                // Slim utility strip, top-left: speed and states stay reachable by
                // touch even with a controller (no controller button maps to them).
                // Bounded and scrollable: the strip is as wide as the game
                // column, and adding MENU pushed it past that edge.
                if (editingLayout) layoutToolbar(Modifier.align(Alignment.TopStart))
                if (!streamClean && !editingLayout) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().systemGestureExclusion().padding(6.dp)
                        .horizontalScroll(rememberScrollState())
                        .alpha(chipAlpha),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Two groups, separated by a rule: the ones that act on
                    // the RUN first, then the ones that act on the APP. Nine
                    // undifferentiated chips meant hunting for LOAD every
                    // time, and NEW (which ends your run) sat between two
                    // harmless ones.
                    OverlayChip("SLOT $saveSlot") {
                        saveSlot = if (saveSlot >= StateSlots.COUNT) 1 else saveSlot + 1
                    }
                    OverlayChip("STATES") { statesDialog = true }
                    OverlayChip("SAVE") { saveState() }
                    OverlayChip("LOAD") { loadState() }
                    OverlayChip("NEW") { confirmNewRun = true }
                    ChipRule()
                    OverlayChip(speedLabel) { cycleSpeed() }
                    if (rewindAllowed) HoldChip("REWIND", onDown = { startRewind() }, onUp = { stopRewind() })
                    OverlayChip(if (muted) "MUTED" else "SOUND") {
                        muted = !muted; applyAudio()
                    }
                    OverlayChip("CAM") { facecam = !facecam }
                    OverlayChip("CLEAN") { onClean(true) }
                    OverlayChip("LAYOUT") { editingLayout = true }
                    // Reachable in landscape too, where the portrait notice
                    // and its button are not rendered at all.
                    if (controllerOn) {
                        OverlayChip(if (padForced) "PAD ON" else "PAD OFF") {
                            padForced = !padForced
                            store.setPadForced(padForced)
                        }
                    }
                    // The status line lives inside the FILE row, which is
                    // portrait-only - so in landscape every message ("Save
                    // failed", "Slot belongs to a different run", "Camera
                    // permission denied") was unreachable and those buttons
                    // looked like they did nothing. Show it in the strip.
                    status?.let { msg ->
                        OverlayChip(msg.take(38)) { status = null }
                    }
                    // The tab bar is gone in landscape, so without this the
                    // only way back to the rest of the app was to rotate the
                    // phone - impossible with rotation locked.
                    OverlayChip("MENU") { onExitFullscreen() }
                }
            }

        }

        if (!landscape) {
            // The FILE button itself lives in the top bar; this is only what it
            // drops down when opened, so nothing sits between game and tracker.
            if (menuOpen) { FileMenu() }

            if (streamClean || !session.tracked) {
                // Untracked, or the capture layout: nothing but the game.
                Spacer(Modifier.weight(1f))
            } else if (dsScreens) {
                // Bounded: a full party of six is taller than the screen, and the
                // pad must stay reachable without scrolling past it.
                Box(
                    Modifier.heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    NdsTrackerPanel(
                        ndsState, onFlee = { flee() }, onGear = { gearDialog = true },
                        enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                        onCycleMark = { i ->
                            ndsState?.enemy?.let { statMarks.cycle(it.mon.species, i) }
                            marksVersion++
                        },
                        enemyNote = enemyNote,
                        onEditNote = { noteDialog = true },
                        attempt = store.attempt(),
                        coverage = ndsCoverage,
                        revealedEnemyAbility = ndsState?.enemy
                            ?.let { statMarks.abilityFor(it.mon.species) },
                        movesSeenRunWide = ndsState?.enemy?.let { statMarks.movesSeenFor(it.mon.species) } ?: emptyList(),
                        moveInfoFor = { id -> ndsTrackerRef?.moveInfoFor(id) },
                    )
                }
            } else Box(
                // Bounded and scrollable: a full card is taller than a phone
                // screen, which was silently cutting off the fourth move.
                // weight(1f), not a fixed cap: the tracker is the flexible
                // one now. Safe because the parent column no longer scrolls -
                // a weighted child inside a scrolling column is a crash, which
                // is why `scrollable` had to go first.
                Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) { TrackerPanel(
                trackerState, onFlee = { flee() }, ballCall = ballCall, onGear = { gearDialog = true },
                onRerollBall = { ballReroll++ },
                movesSeenRunWide = trackerState?.enemy
                    ?.let { statMarks.movesSeenFor(it.species) } ?: emptyList(),
                moveRowFor = { id -> trackerRef?.moveRowFor(id) ?: gbLookup?.invoke(id) },
                revealedEnemyAbility = trackerState?.enemy
                    ?.let { statMarks.abilityFor(it.species) },
                routeName = trackerState?.routeName,
                routeSeen = trackerState?.mapId?.let { statMarks.seenOnRoute(it).size } ?: 0,
                routeTotal = trackerState?.routeSpecies?.size ?: 0,
                routeTrainers = trackerState?.routeTrainers?.size ?: 0,
                routeBosses = trackerState?.routeBosses ?: 0,
                steps = trackerState?.steps ?: 0,
                onMoveDescription = { id -> trackerRef?.moveDescription(id) },
                onAbilityDescription = { name ->
                    trackerRef?.let { t -> t.abilityIdOf(name)?.let(t::abilityDescription) }
                },
                onWeight = { sp -> trackerRef?.weight(sp) },
                onEvolution = { sp -> trackerRef?.evolution(sp) },
                onEffectiveness = { sp ->
                    trackerRef?.effectivenessAgainst(sp) ?: emptyMap()
                },
                onMoveLevels = { sp ->
                    trackerRef?.learnset(sp)?.map { it.first } ?: emptyList()
                },
                onSpeciesNote = { sp -> statMarks.noteFor(sp) },
                onRouteAreas = {
                    trackerState?.mapId?.let { trackerRef?.routeEncounterAreas(it) }
                        ?: emptyMap()
                },
                onRouteSeenSet = {
                    trackerState?.mapId?.let { statMarks.seenOnRoute(it) } ?: emptySet()
                },
                onSpeciesName = { sp -> trackerRef?.speciesName(sp) ?: "#$sp" },
                favoriteLine = favoriteLine, spriteFor = spriteFor,
                enemyMarks = enemyMarks, enemyEncounters = enemyEncounters,
                enemyLastSeenLevel = enemyLastSeen,
                onCycleMark = { i ->
                    trackerState?.enemy?.let { statMarks.cycle(it.species, i) }
                    marksVersion++
                },
                enemyNote = enemyNote,
                onEditNote = { noteDialog = true },
                attempt = store.attempt(),
                coverage = coverage,
            ) }

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
                        "CONTROLLER CONNECTED — PAD HIDDEN",
                        fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont,
                        fontSize = 9.sp,
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
        }
      }

      // Landscape: the tracker sits BESIDE the game, the way BizHawk and the PC
      // tracker sit side by side, and collapses to an arrow tab so the game can
      // have the whole screen back.
      if (landscape && !streamClean) trackerPane()
    }
    if (landscape && !streamClean && session.tracked && TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING) {
        FloatingTracker(
            frame = floatFrame, windowW = windowWidthDp, windowH = windowHeightDp,
            onFrame = { floatFrame = it },
            onDock = { TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED; TrackerOptions.save(); trackerOpen = true },
        ) { trackerContent() }
    }
    }

    androidx.compose.runtime.SideEffect {
        QuickActions.onQuickSave = { saveState() }
        QuickActions.onQuickLoad = { loadState() }
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
            systemFilePresent = { File(context.filesDir, it).let { f -> f.exists() && f.length() > 0 } },
            onImportSystemFile = { name -> importSystemFileName = name; systemFilePicker.launch(arrayOf("*/*")) },
            onDismiss = { settingsDialog = false },
        )
    }
    if (statesDialog) {
        val slots = remember(session.id, slotsVersion) { StateSlots.list(context.filesDir, session) }
        val auto = remember(session.id, slotsVersion) { StateSlots.auto(context.filesDir, session) }
        SaveStatesDialog(
            auto = auto, slots = slots, current = saveSlot, version = slotsVersion,
            onPick = { saveSlot = it },
            onSave = { saveState(it) },
            onLoad = { loadState(it); statesDialog = false },
            onLock = { s, on -> s.setLocked(on); slotsVersion++ },
            onUndo = { s -> status = if (s.restoreBackup()) "Slot ${s.n}: previous state restored." else "Nothing to undo."; slotsVersion++ },
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
                status = if (on) "Hardcore on: rcheevos reset the game's achievements; cheats, rewind, slow motion and state loads are off." else "Hardcore off."
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
    // 2.3: the reference's GameOverScreen, once per outcome. "Continue playing"
    // leaves the run as it is (the tracker keeps its game-over card); NEW RUN
    // goes through the usual confirmation. Shown again only after the outcome
    // has cleared and returned, the way isDisplayed works in the reference.
    val runOutcome = view?.outcome
    var gameOverShownFor by remember(session.id) { mutableStateOf<com.ironmonone.tracker.RunOutcome?>(null) }
    LaunchedEffect(runOutcome) { if (runOutcome == null) gameOverShownFor = null }
    if (runOutcome != null && gameOverShownFor != runOutcome && !streamClean) {
        val team: List<GameOverMon> = ndsState?.party?.map { GameOverMon(it.mon.species, it.speciesName, it.mon.level, it.mon.curHp == 0, it.mon.shiny) }
            ?: trackerState?.party?.map { GameOverMon(it.mon.species, it.speciesName, it.mon.level, it.mon.curHp == 0, it.mon.shiny) }
            ?: emptyList()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val family = when (platform) {
            com.ironmonone.core.Platform.NDS -> GameOverFamily.DS
            com.ironmonone.core.Platform.GBC -> GameOverFamily.GEN12
            else -> GameOverFamily.GEN3
        }
        // The staged screenshot modes have no run and no battle: they read a log
        // from the app's external files dir and offer Retry, so the popup shows
        // every action it can carry. Never taken outside Demo.mode.
        val logFile = (if (session.isRun) session.kind?.let { store.currentRunLogFor(it) } else null)
            ?: Demo.mode?.let { ctx.getExternalFilesDir(null)?.let { d -> java.io.File(d, "demo.log") }?.takeIf { it.isFile } }
        GameOverDialog(
            family = family,
            won = runOutcome == com.ironmonone.tracker.RunOutcome.WON,
            attempt = store.attempt(),
            team = team,
            spriteOf = { m -> if (ndsState != null) remember(m.species, m.shiny) { PcAssets.dsSprite(ctx, m.species, m.shiny) } else spriteFor(m.species) },
            dsCause = ndsState?.runOver,
            canRetry = (battleStartState != null || Demo.mode != null) && !raHardcore,
            onInspectLog = logFile?.let { f -> { logViewerFile = f } },
            onContinue = { gameOverShownFor = runOutcome },
            onRetry = {
                // The reference's loadTempSaveState: back to the moment the battle began.
                val st = battleStartState
                gameOverShownFor = runOutcome
                if (st != null) {
                    val ok = retro?.unserializeState(st) == true
                    status = if (ok) "Back to the start of the battle." else "Could not restore the battle."
                }
            },
            onSaveAttempt = {
                val kind = session.kind
                if (kind == null || !session.isRun) false
                else store.saveAttempt(kind, store.attempt(), store.lastSeedText(), runCatching { retro?.serializeState() }.getOrNull())
            },
            onNewGame = { gameOverShownFor = runOutcome; confirmNewRun = true },
        )
    }
    logViewerFile?.let { f -> LogViewer(f, onClose = { logViewerFile = null }) }

    if (rulesDialog) {
        val fam = session.kind?.family ?: ""
        val runMode = if (session.isRun) store.loadLastRun()?.second?.let { RnqsInfo.parse(it).ruleset } else null
        RulesDialog(family = fam, mode = runMode, onDismiss = { rulesDialog = false })
    }

    if (gearDialog) {
        TrackerGearDialog(
            speciesName = { id -> trackerRef?.speciesName(id) ?: ndsTrackerRef?.speciesName(id) ?: gbNames?.invoke(id) ?: "#$id" },
            marks = statMarks,
            onCleared = { marksVersion++ },
            onRules = { gearDialog = false; rulesDialog = true },
            onDismiss = { gearDialog = false },
        )
    }

    if (confirmNewRun) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmNewRun = false }) {
            Column(
                Modifier.background(Pc.Ground).border(1.dp, Pc.Border).padding(14.dp)
            ) {
                PixText("START A NEW RUN?", 10, Pc.Gold)
                Spacer(Modifier.height(8.dp))
                PixText("Rolls a fresh seed and re-randomizes the ROM.", 8, Pc.Text)
                Spacer(Modifier.height(4.dp))
                PixText("Your in-game save is KEPT, so if you saved", 8, Pc.Dim)
                PixText("after the intro you can pick Continue and", 8, Pc.Dim)
                PixText("start straight into the new randomization.", 8, Pc.Dim)
                Spacer(Modifier.height(4.dp))
                PixText("Stat notes for this run are cleared.", 8, Pc.Negative)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("YES, NEW RUN", accent = true) {
                        confirmNewRun = false
                        newRun()
                    }
                    com.ironmonone.app.gen3.Gen3Button("CANCEL") {
                        confirmNewRun = false
                    }
                }
            }
        }
    }

    // Note editor for the species on screen. Notes are per species and per run,
    // sitting beside the stat marks.
    if (noteDialog && enemySpecies > 0) {
        var draft by remember(enemySpecies) { mutableStateOf(enemyNote) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { noteDialog = false }) {
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
                    PcSmallButton("SAVE") {
                        statMarks.setNote(enemySpecies, draft)
                        marksVersion++
                        noteDialog = false
                    }
                    PcSmallButton("CANCEL") { noteDialog = false }
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
            persistSram()
            autoSave()
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
 * How much of the window the tracker column takes in landscape, by default.
 *
 * From the reference streaming layout: about a quarter of the width for the
 * camera / tracker stack, leaving roughly three quarters for the game. The old
 * fixed 340dp took ~40% of a landscape phone, which both squeezed the game and
 * letterboxed it.
 */
private const val TRACKER_FRACTION = 0.26f

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
}
private fun coreRelease(keyCode: Int) {
    heldCoreKeys.remove(keyCode)
    com.swordfish.libretrodroid.LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, keyCode)
    NewRunCombo.track(KeyEvent.ACTION_UP, keyCode)
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
        outline && keyCode in DPAD_KEYS -> androidx.compose.ui.graphics.RectangleShape
        outline -> androidx.compose.foundation.shape.CircleShape
        !modern -> androidx.compose.ui.graphics.RectangleShape
        mini || small || wide -> androidx.compose.foundation.shape.RoundedCornerShape(50)
        else -> androidx.compose.foundation.shape.CircleShape
    }
    val modernFill = Color.White.copy(alpha = if (pressed) 0.50f else if (glyphOnly) 0f else 0.22f)
    val modernEdge = Color.White.copy(alpha = if (glyphOnly) 0f else 0.40f)
    Box(
        Modifier
            .padding(2.dp)
            .let {
                when {
                    mini -> if (modern || outline) it.width((48 * scale).dp).height((30 * scale).dp) else it.size((36 * scale).dp)
                    small -> it.width((64 * scale).dp).height(((if (modern) 26 else 32) * scale).dp)
                    wide -> it.width((96 * scale).dp).height((44 * scale).dp)
                    else -> it.size((56 * scale).dp)
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
            }
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
                    corePress(keyCode)
                },
                { pressed = false
                  coreRelease(keyCode)
                  if (keyCode == KeyEvent.KEYCODE_BUTTON_B) currentOnB() },
            ),
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

/** A chip that acts while HELD: down starts, up stops. Rewind's control. */
@Composable
private fun HoldChip(label: String, onDown: () -> Unit, onUp: () -> Unit, big: Boolean = false) {
    val g = com.ironmonone.app.gen3.Gen3
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(if (held) Pc.Gold.copy(alpha = 0.6f) else g.FrameDark.copy(alpha = if (big) 1f else 0.45f))
            .padding(1.dp)
            .background(g.Paper.copy(alpha = if (big) 1f else 0.25f))
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp)
            .pressHold({ held = true; onDown() }, { held = false; onUp() }),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u25C0\u25C0 " + label, fontFamily = g.PixelFont, fontSize = 9.sp,
            color = if (big) g.Ink else Color.White)
    }
}

@Composable
private fun OverlayChip(label: String, onClick: () -> Unit) {
    val g = com.ironmonone.app.gen3.Gen3
    Box(
        Modifier
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
        Text(
            label, fontFamily = g.PixelFont, fontSize = 8.sp, color = Color.White,
            maxLines = 1, softWrap = false,
        )
    }
}

/** Press-and-hold semantics for a game button: down on touch, up on release. */
private fun Modifier.pressHold(onDown: () -> Unit, onUp: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            var held = false
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Press -> { held = true; onDown() }
                    PointerEventType.Release -> { if (held) onUp(); held = false }
                    else -> {
                        // A consumed or cancelled gesture never delivers
                        // Release; without this the core key stayed latched
                        // DOWN and the character walked forever.
                        if (held && event.changes.all { it.isConsumed }) {
                            held = false; onUp()
                        }
                    }
                }
            }
        }
    }

/** The four direction keys: the OUTLINE skin draws these square, the My Boy cross. */
private val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
