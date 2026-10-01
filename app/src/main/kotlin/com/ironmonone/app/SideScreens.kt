@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ironmonone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.text.font.FontWeight
import com.ironmonone.app.gen3.Gen3Button
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTracker

/**
 * Which of the tracker's side screens is open. Kept out of PlayScreen on
 * purpose: that composable holds so much state that adding these ten to it
 * pushed its bytecode past what ART's verifier accepts (a VerifyError on
 * "copy1 v7<-v304", 2026-09-08), which killed the play screen outright.
 */
class SideScreenState {
    var trainersDialog by mutableStateOf(false)
    var trainerInfo by mutableStateOf<GbaTracker.TrainerInfo?>(null)
    var battleDetailsDialog by mutableStateOf(false)
    var catchRatesDialog by mutableStateOf(false)
    var catchHpAdjust by mutableStateOf(0)
    var notebookDialog by mutableStateOf(false)
    var randomEvos by mutableStateOf<Int?>(null)
    var healsDialog by mutableStateOf(false)
    /** Move History for a card: (species, name, level). */
    var moveHistory by mutableStateOf<Triple<Int, String, Int>?>(null)
    var statsDialog by mutableStateOf(false)
    var timeMachineDialog by mutableStateOf(false)
    var scoreSheet by mutableStateOf(false)
    var pastRuns by mutableStateOf(false)
    var statistics by mutableStateOf(false)
    var evoData by mutableStateOf<Int?>(null)
    var trackedPokemon by mutableStateOf(false)
    var tourney by mutableStateOf(false)
    var colorTheme by mutableStateOf(false)
    /** Calc Atk, open with what it filled in from the last hit (null fill: opened empty). */
    var calcAtkOpen by mutableStateOf(false)
    var calcAtkFill by mutableStateOf<com.ironmonone.tracker.CalcAtk.Fill?>(null)

    /** Calc Atk's configureOptions in battle: fill from the last hit, then open. */
    fun openCalcAtk(t: GbaTracker?, s: TrackerState?) {
        calcAtkFill = if (t != null && s != null) calcAtkFill(t, s) else null
        calcAtkOpen = true
    }
}

/** The side screens themselves: Move History, Random Evos, Heals In Bag, Notebook, Catch Rates, Battle Details, Trainers On Route, Trainer Info, Stats. */
@Composable
fun SideScreenDialogs(
    s: SideScreenState,
    trackerRef: GbaTracker?,
    ndsTrackerRef: NdsTracker?,
    trackerState: TrackerState?,
    statMarks: StatMarks,
    enemySpecies: Int,
    gbNames: ((Int) -> String)?,
    spriteFor: (Int) -> ImageBitmap?,
    attempt: Int,
    timeMachine: TimeMachine? = null,
    snapshot: () -> ByteArray? = { null },
    /** The restore point itself, not only its bytes: the run's events name which one (RunEvents). */
    onRestore: (TimeMachine.RestorePoint) -> Unit = {},
    pastRunStore: PastRunStore? = null,
    dsSpriteOf: @Composable (Int) -> ImageBitmap? = { null },
    tourney: TourneyTracker? = null,
    currentSeed: String = "",
) {
    // Tracker.getAbilities for the Gen 3 tracker, whose Battle Details shows Loafing only once
    // Truant is tracked (BattleDetailsScreen.lua:1574-1588). Set here rather than in PlayScreen,
    // which is near the verifier's limit (see SideScreenState).
    androidx.compose.runtime.SideEffect { trackerRef?.trackedAbilities = statMarks::abilitiesFor }
    // A DS game gets the DS tracker's preset themes, any other game the PC tracker's (ThemePresets).
    if (s.colorTheme) ColorThemeDialog(ds = ndsTrackerRef != null) { s.colorTheme = false }
    // A Nuzlocke's own rules, asked for by Rules in the File menu or Tracker Setup (2026-09-30, UX audit P0-6).
    NuzlockeLedgerRequested()
    if (s.calcAtkOpen) CalcAtkDialog(s.calcAtkFill, enemyStats = null) { s.calcAtkOpen = false }
    if (s.trackedPokemon) TrackedPokemonDialog(statMarks, statMarks.encounteredSpecies(), ndsTrackerRef, dsSpriteOf) { s.trackedPokemon = false }
    if (s.tourney && tourney != null) TourneyDialog(tourney, currentSeed) { s.tourney = false }
    if (s.pastRuns && pastRunStore != null) PastRunsDialog(pastRunStore, dsSpriteOf) { s.pastRuns = false }
    if (s.statistics && pastRunStore != null) StatisticsDialog(pastRunStore) { s.statistics = false }
    s.evoData?.let { sp -> EvoDataDialog(sp, ndsTrackerRef, dsSpriteOf) { s.evoData = null } }
    if (s.scoreSheet) {
        val result = remember(statMarks, trackerRef) {
            ScoreSheet.build(statMarks, { trackerRef?.baseStats(it) }, { id -> trackerRef?.speciesName(id) ?: gbNames?.invoke(id) ?: "#$id" })
        }
        ScoreSheetDialog(result, spriteFor) { s.scoreSheet = false }
    }
    if (s.timeMachineDialog && timeMachine != null) {
        timeMachine.viewing = true
        TimeMachineDialog(
            timeMachine, enabled = TrackerOptions.restorePoints,
            onEnable = { TrackerOptions.restorePoints = it; TrackerOptions.save() },
            onCreate = { timeMachine.create(null, trackerState?.routeName, System.currentTimeMillis(), snapshot) },
            onRestore = { rp ->
                timeMachine.backupCurrent(System.currentTimeMillis(), snapshot)
                onRestore(rp)
                s.timeMachineDialog = false
            },
        ) { s.timeMachineDialog = false; timeMachine.viewing = false; timeMachine.cleanup() }
    }
    s.moveHistory?.let { (species, n, lv) ->
        MoveHistoryDialog(
            name = n, level = lv, seen = statMarks.movesSeenFor(species),
            learnLevels = ndsTrackerRef?.moveLevelsOf(species) ?: trackerRef?.learnset(species)?.map { it.first } ?: emptyList(),
            onClose = { s.moveHistory = null },
        )
    }
    s.randomEvos?.let { sp ->
        RandomEvosDialog(sp, trackerRef, speciesName = { id -> trackerRef?.speciesName(id) ?: "#$id" }, spriteFor = spriteFor,
            onPick = { id -> if (trackerRef?.hasRandomEvos(id) == true) s.randomEvos = id }) { s.randomEvos = null }
    }
    if (s.healsDialog) {
        val gba = trackerRef
        val rows = remember(trackerState, gba) { runCatching { gba?.healsInBag(trackerState?.party?.firstOrNull()) }.getOrNull() ?: emptyList() }
        HealsInBagDialog(rows) { s.healsDialog = false }
    }
    if (s.notebookDialog) {
        NotebookDialog(
            tracker = trackerRef, marks = statMarks,
            encountersOf = { statMarks.totalEncounters(it) }, seenSpecies = statMarks.encounteredSpecies(),
            lastLevelOf = { statMarks.lastLevelSeen(it) }, lastSeenSpecies = enemySpecies.takeIf { it > 0 },
            speciesName = { id -> trackerRef?.speciesName(id) ?: gbNames?.invoke(id) ?: "#$id" },
            spriteFor = spriteFor,
        ) { s.notebookDialog = false }
    }
    if (s.catchRatesDialog) {
        val gba = trackerRef
        val rates = remember(trackerState, gba, s.catchHpAdjust) { runCatching { gba?.catchRates(s.catchHpAdjust) }.getOrNull() }
        CatchRatesDialog(rates, s.catchHpAdjust, onAdjust = { s.catchHpAdjust = it }) { s.catchRatesDialog = false }
    }
    if (s.battleDetailsDialog) {
        val gba = trackerRef
        // Re-read on every poll so counters move while the screen is open.
        val details = remember(trackerState, gba) { runCatching { gba?.battleDetails() }.getOrNull() }
        BattleDetailsDialog(details) { s.battleDetailsDialog = false }
    }
    if (s.trainersDialog) {
        val gba = trackerRef; val st = trackerState
        val mapId = st?.mapId
        if (gba != null && mapId != null) {
            val list = remember(mapId, st.routeTrainers) { gba.trainersForRoute(mapId).mapNotNull { gba.trainer(it) } }
            TrainersOnRouteDialog(
                st.routeName ?: "This map", list, onTrainer = { s.trainerInfo = it; s.trainersDialog = false }, onClose = { s.trainersDialog = false },
                // FireRed and LeafGreen only, and only for a place with pictures (FrlgPictures.placeFor, 2026-09-29).
                pictures = FrlgPictures.placeFor(st.badgeSet, mapId),
            )
        } else s.trainersDialog = false
    }
    s.trainerInfo?.let { t ->
        val gba = trackerRef
        val st = trackerState
        TrainerInfoDialog(
            t, routeName = st?.mapId?.let { m -> gba?.routeInfo(m)?.first },
            leadLevel = st?.party?.firstOrNull()?.mon?.level,
            canShowTeams = InfoRules.canShowTrainerTeams(gba?.trainerTeamsRandomized()),
            // TrainerInfoScreen.lua:325: in the battle against this trainer, a fainted Pokemon shows.
            faintedSlots = if (st != null && st.inBattle && st.opponentTrainerId == t.id) st.enemyParty.filter { !it.alive }.map { it.slot }.toSet() else emptySet(),
            giovanni = TrainerInfoView.isGiovanni(gba?.isRse == false, t.id),
            // Resources.Game.ItemNames has no entry for 0 (TrainerInfoScreen.lua:293).
            itemName = { id -> gba?.itemName(id)?.takeIf { id != 0 && it.isNotBlank() && !it.startsWith("#") } },
            spriteFor = spriteFor,
            onClose = { s.trainerInfo = null },
            routePictures = FrlgPictures.placeFor(st?.badgeSet, st?.mapId),
        )
    }
    if (s.statsDialog) {
        val gba = trackerRef
        StatsDialog(StatsRows.build(attempt, gba?.let { t -> { i: Int -> t.readGameStat(i) } }), onClose = { s.statsDialog = false })
    }
}

/**
 * Landscape, a game with no tracker: the only FILE button lives in the tracker's
 * header, so saves, speed and the way back to the app were out of reach
 * (audit, 2026-09-27). This chip opens the same menu row. It sits top-centre,
 * clear of the L and R buttons in every pad preset, and is 48dp tall.
 */
@Composable
internal fun LandscapeMenuChip(modifier: androidx.compose.ui.Modifier, onClick: () -> Unit) {
    val g = com.ironmonone.app.gen3.Gen3
    androidx.compose.foundation.layout.Box(
        modifier
            .padding(6.dp)
            .heightIn(min = 48.dp)
            .background(g.FrameDark.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text("Menu", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.sp,
            color = androidx.compose.ui.graphics.Color.White)
    }
}

/**
 * The Play screen's own small bits of UI state (confirmations, pickers, the
 * status toast's action). Held here for the same reason as [SideScreenState]:
 * every `remember` added to PlayScreen itself costs registers in a method that
 * is already near the verifier's limit (2026-09-27, audit).
 */
class PlayUiState {
    var confirmReset by mutableStateOf(false)
    /** The slot waiting on "Load slot N?", or null. */
    var confirmLoad by mutableStateOf<Int?>(null)
    var speedPicker by mutableStateOf(false)
    /** Landscape: FILE shows the chip strip; More swaps it for the full menu. Never both at once. */
    var moreOpen by mutableStateOf(false)
    /** The layout and skin as they were when the editor opened, for Cancel. */
    var layoutBefore: PadLayout? = null
    var skinBefore: PadSkin? = null
    var confirmKeepLayout by mutableStateOf(false)
    /** Landscape: the editor bar at the bottom edge instead of the top, when a control sits under it. */
    var layoutBarBottom by mutableStateOf(false)
    /** Landscape with the tracker set to Hidden: the tab shows it for now without changing that choice. */
    var trackerPeek by mutableStateOf(false)
    /** An action offered with one status message: (that message, the button, what it does). */
    var toastAction by mutableStateOf<Triple<String, String, () -> Unit>?>(null)
    /** The game view whose core has drawn a frame; until it is the current one, the core is still loading. */
    var coreUp by mutableStateOf<Any?>(null)
    /** The game view's size while its core loads (see [holdSizeWhileLoading]). */
    var heldSize: androidx.compose.ui.unit.IntSize? = null
}

/**
 * Keeps the game view at its first size until the core has drawn a frame.
 *
 * GLRetroView loads the game inside onSurfaceCreated on the GL thread, and a
 * DS game is up to 512 MB. A resize meanwhile (the tracker filling in, a
 * rotation) makes GLSurfaceView.surfaceChanged wait on the main thread for
 * that thread, and a load past 5 s is an ANR. White 2 hit it on the emulator
 * (2026-09-28). The view is measured at the held size and clipped to its slot;
 * once the core is up it takes its real size, and a resize then is instant.
 */
fun Modifier.holdSizeWhileLoading(ui: PlayUiState, loading: Boolean): Modifier =
    this.clipToBounds().layout { measurable, constraints ->
        val held = if (loading) (ui.heldSize ?: androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
            .takeIf { constraints.hasBoundedWidth && constraints.hasBoundedHeight }?.also { ui.heldSize = it })
        else { ui.heldSize = null; null }
        val p = measurable.measure(held?.let { androidx.compose.ui.unit.Constraints.fixed(it.width, it.height) } ?: constraints)
        layout(constraints.constrainWidth(p.width), constraints.constrainHeight(p.height)) { p.place(0, 0) }
    }

/** A new-run failure we raised ourselves, whose message is already plain copy. */
class RunSetupProblem(message: String) : Exception(message)

private const val NEW_RUN_FAILED = "Could not start a new run. Try again from Kaizo IronMON on Home."

/**
 * What the player reads when NEW RUN fails. Our own setup problems say what to
 * do; anything else ("java.lang.IllegalStateException: ...", or no message at
 * all) is logged by the caller and shown as one plain line (2026-09-27, audit).
 */
fun newRunFailureCopy(t: Throwable): String = (t as? RunSetupProblem)?.message ?: NEW_RUN_FAILED

/** The same, for a failure text an older build saved to disk. */
fun newRunFailureCopy(saved: String): String =
    if (saved.startsWith("java.") || saved.startsWith("kotlin.") || "Exception" in saved) NEW_RUN_FAILED else saved

@Composable
private fun ConfirmButtons(yes: String, onYes: () -> Unit, no: String, onNo: () -> Unit, middle: Pair<String, () -> Unit>? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Gen3Button(yes, accent = true, onClick = onYes)
        middle?.let { (l, f) -> Gen3Button(l, onClick = f) }
        Gen3Button(no, onClick = onNo)
    }
}

@Composable
private fun DialogBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
    Spacer(Modifier.height(16.dp))
}

/**
 * Start a new run, in the shell's look (it was 8sp pixel font on the tracker's palette).
 *
 * It works out for itself what is in Play (2026-09-30, UX audit P0-1, P0-2, P0-8), so PlayScreen, at the verifier's
 * limit, only says yes or no: a Kaizo IronMON run, a randomized Nuzlocke (the next game keeps its rules), or a
 * library game, where A+B+Start has no run to start and says so. And it says what the new seed opens with: the
 * in-game save stays, in every game, and New Game on the title screen is the clean start (RunSaves). [beforeRead]
 * writes the running game's battery save to disk first: the app writes it only when it pauses, so this read an older
 * save or none, said "no save" and, in rc32's first builds, offered no way to keep it (Blake, 2026-09-30).
 */
@Composable
fun NewRunConfirmDialog(beforeRead: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filesDir = context.applicationContext.filesDir
    val store = remember { PrepStore(filesDir) }
    val session = remember { runCatching { store.session() }.getOrNull() }
    if (session == null || !session.isRun) {
        ShellDialog("No run to start", onDismiss) {
            DialogBody(NewRunCopy.NOT_A_RUN)
            Gen3Button("OK", accent = true, onClick = onDismiss)
        }
        return
    }
    val nuzlocke = remember { PlayRules.kind(session, filesDir) == PlayRules.Kind.NUZLOCKE }
    val plan = remember {
        runCatching { beforeRead() }
        runCatching {
            val kind = store.loadLastRun()?.first?.let { id -> com.ironmonone.core.RomKind.byId(id) } ?: return@runCatching null
            RunSaves.planOnNewSeed(RunSaves.file(filesDir, kind, store.currentRunFor(kind)), kind)
        }.getOrNull()
    }
    ShellDialog(if (nuzlocke) "Start the next Nuzlocke?" else "Start a new run?", onDismiss) {
        Text(if (nuzlocke) NewRunCopy.NUZLOCKE else NewRunCopy.IRONMON, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Spacer(Modifier.height(8.dp))
        Text(NewRunCopy.save(plan), style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        Spacer(Modifier.height(16.dp))
        ConfirmButtons(if (nuzlocke) "YES, NEW NUZLOCKE" else "YES, NEW RUN", onConfirm, "CANCEL", onDismiss)
    }
}

/** The new-run dialog's words (2026-09-30), kept apart so a test can hold them. */
internal object NewRunCopy {
    const val IRONMON = "The current run ends and a new game is randomized. Stat notes for this run are cleared."
    const val NUZLOCKE = "The current run ends and a new game is randomized with the same rules. " +
        "This run's ledger stays in your runs on the Nuzlocke screen."
    const val NOT_A_RUN = "A+B+Start starts a new run only in a Kaizo IronMON game or a randomized Nuzlocke. " +
        "This game is from your library, so there is no run to start."
    fun save(plan: RunSaves.Plan?): String = when (plan) {
        null, RunSaves.Plan.NONE -> "The new game starts from the title screen with no save."
        RunSaves.Plan.NO_TEAM -> "Your in-game save stays. It has no Pokémon in it yet, so Continue on the title screen skips the intro."
        RunSaves.Plan.HOLDS_TEAM -> "Your in-game save stays, and it holds this run's team. Continue brings that team along, and New Game starts fresh."
        RunSaves.Plan.UNREAD -> "Your in-game save stays. Continue on the title screen opens it, and New Game starts fresh."
    }
}

/**
 * The Play screen's confirmations and its speed picker. Restart, Load and the
 * layout editor's Back used to act on one tap with no way back, and speed was
 * a seven-step forward cycle (2026-09-27, audit).
 */
@Composable
fun PlayDialogs(
    ui: PlayUiState,
    onRestart: () -> Unit,
    onLoad: (Int) -> Unit,
    speeds: List<String>,
    speedNow: String,
    onSpeed: (String) -> Unit,
    onLayoutDone: (keep: Boolean) -> Unit,
) {
    val restartFiles = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    if (ui.confirmReset) ShellDialog("Restart the game?", { ui.confirmReset = false }) {
        DialogBody("Anything not saved is lost.")
        ConfirmButtons("RESTART", { ui.confirmReset = false; onRestart(); RunRestarts.log(restartFiles) }, "CANCEL", { ui.confirmReset = false })
    }
    ui.confirmLoad?.let { n ->
        ShellDialog(if (n == StateSlots.AUTO) "Resume from the auto-save?" else "Load slot $n?", { ui.confirmLoad = null }) {
            DialogBody("Progress since then is lost.")
            ConfirmButtons("LOAD", { ui.confirmLoad = null; onLoad(n) }, "CANCEL", { ui.confirmLoad = null })
        }
    }
    if (ui.speedPicker) ShellDialog("Game speed", { ui.speedPicker = false }) {
        ShellSegmented(speeds, speedNow, label = { it }, onSelect = { onSpeed(it); ui.speedPicker = false })
        Spacer(Modifier.height(12.dp))
        Text("Sound is off at any speed but 1x.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
    }
    if (ui.confirmKeepLayout) ShellDialog("Keep changes?", { ui.confirmKeepLayout = false }) {
        DialogBody("Keep saves the new layout. Discard puts it back the way it was.")
        ConfirmButtons("KEEP", { onLayoutDone(true) }, "KEEP EDITING", { ui.confirmKeepLayout = false },
            middle = "DISCARD" to { onLayoutDone(false) })
    }
}

/**
 * Status messages as a toast over the game, whatever the menu is doing. They
 * lived inside the FILE menu, so in portrait "Saved", "Could not load" or a
 * denied camera said nothing unless the menu happened to be open, and in
 * landscape they were a chip cut at 38 characters (2026-09-27, audit).
 * Gone after 3 s (5 with an action) or on a tap; [hold] keeps it up while a
 * long job runs with nothing else on screen to say so.
 */
@Composable
fun StatusToast(
    text: String?,
    hold: Boolean,
    action: Triple<String, String, () -> Unit>?,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text == null) return
    val gone by rememberUpdatedState(onGone)
    val act = action?.takeIf { it.first == text }
    LaunchedEffect(text, hold) {
        if (!hold) { kotlinx.coroutines.delay(if (act != null) 5000 else 3000); gone() }
    }
    val shape = RoundedCornerShape(Shell.controlRadius)
    Row(
        modifier
            .padding(12.dp)
            .widthIn(max = 520.dp)
            .background(Shell.raised, shape)
            .clickable { gone() }
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
            modifier = Modifier.weight(1f, fill = false))
        act?.let { (_, label, run) ->
            Box(
                Modifier.padding(start = 8.dp).heightIn(min = Shell.touchTarget)
                    .clickable { run(); gone() }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Shell.accentOnNight)
            }
        }
    }
}

/**
 * Clean view's way out. Back was the only exit and nothing on screen said so
 * (2026-09-27, audit). A hint for the first 3 s; after that a tap in the
 * top-right corner shows a Leave chip for 3 s. The corner zone is one touch
 * target and no more, so the rest of the screen still reaches the game (a DS
 * touch screen keeps working) and nothing shows on the capture unasked.
 */
@Composable
fun CleanViewExit(onExit: () -> Unit, modifier: Modifier = Modifier) {
    var hint by remember { mutableStateOf(true) }
    var chip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); hint = false }
    LaunchedEffect(chip) { if (chip) { kotlinx.coroutines.delay(3000); chip = false } }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(hint, Modifier.align(Alignment.TopCenter).padding(top = 16.dp), enter = fadeIn(), exit = fadeOut()) {
            Text("Back or tap the corner to leave clean view",
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                modifier = Modifier.background(Shell.raised, RoundedCornerShape(Shell.controlRadius))
                    .padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(Shell.touchTarget)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (chip) onExit() else chip = true
                },
        )
        AnimatedVisibility(chip, Modifier.align(Alignment.TopEnd).padding(4.dp), enter = fadeIn(), exit = fadeOut()) {
            Gen3Button("Leave clean view", onClick = onExit)
        }
    }
}

/** DS screen arrangements in words; the melonDS keys ("hybrid-top") meant nothing to a player. Null is automatic. */
fun dsLayoutName(key: String?): String = when (key) {
    null -> "Automatic"
    "top-bottom" -> "Top above bottom"
    "bottom-top" -> "Bottom above top"
    "left-right" -> "Side by side"
    "right-left" -> "Side by side, bottom first"
    "hybrid-top" -> "Hybrid, top large"
    "hybrid-bottom" -> "Hybrid, bottom large"
    "top" -> "Top screen only"
    "bottom" -> "Bottom screen only"
    "rotate-left" -> "Rotated left"
    "rotate-right" -> "Rotated right"
    else -> key
}

/** The DS screen picker: every arrangement at once, in words, instead of an eleven-step cycle. */
@Composable
fun DsScreensDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ShellDialog("DS screens", onDismiss) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            (listOf<String?>(null) + PadLayout.DS_LAYOUTS).forEach { k ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(k) }.heightIn(min = Shell.touchTarget).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(k == current)
                    Text(dsLayoutName(k), style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                        modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

/** A yes-or-cancel question for the layout editor's destructive chips. */
@Composable
fun LayoutConfirmDialog(title: String, body: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    ShellDialog(title, onDismiss) {
        DialogBody(body)
        ConfirmButtons(yes, onYes, "CANCEL", onDismiss)
    }
}
