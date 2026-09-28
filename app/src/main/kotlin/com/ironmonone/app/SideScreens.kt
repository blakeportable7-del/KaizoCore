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
}

/** The side screens themselves: Move History, Random Evos, Heals In Bag, Notebook, Catch Rates, Battle Details, Trainers On Route, Trainer Info, Stats. */
@Composable
fun SideScreenDialogs(
    s: SideScreenState,
    trackerRef: GbaTracker?,
    ndsTrackerRef: NdsTracker?,
    trackerState: TrackerState?,
    statMarks: StatMarks,
    encounters: Map<Int, Int>,
    lastSeenLevel: Map<Int, Int>,
    enemySpecies: Int,
    gbNames: ((Int) -> String)?,
    spriteFor: (Int) -> ImageBitmap?,
    attempt: Int,
    timeMachine: TimeMachine? = null,
    snapshot: () -> ByteArray? = { null },
    onRestore: (ByteArray) -> Unit = {},
    pastRunStore: PastRunStore? = null,
    dsSpriteOf: @Composable (Int) -> ImageBitmap? = { null },
    tourney: TourneyTracker? = null,
    currentSeed: String = "",
) {
    if (s.colorTheme) ColorThemeDialog { s.colorTheme = false }
    if (s.trackedPokemon) TrackedPokemonDialog(statMarks, encounters.keys, ndsTrackerRef, dsSpriteOf) { s.trackedPokemon = false }
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
                onRestore(rp.bytes)
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
            encountersOf = { encounters[it] ?: 0 }, seenSpecies = encounters.keys.toSet(),
            lastLevelOf = { lastSeenLevel[it] }, lastSeenSpecies = enemySpecies.takeIf { it > 0 },
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
            TrainersOnRouteDialog(st.routeName ?: "This map", list, onTrainer = { s.trainerInfo = it; s.trainersDialog = false }, onClose = { s.trainersDialog = false })
        } else s.trainersDialog = false
    }
    s.trainerInfo?.let { t ->
        val gba = trackerRef
        TrainerInfoDialog(
            t, routeName = trackerState?.mapId?.let { m -> gba?.routeInfo(m)?.first },
            leadLevel = trackerState?.party?.firstOrNull()?.mon?.level,
            speciesName = { gba?.speciesName(it) ?: "#$it" }, itemName = { gba?.itemName(it) ?: "#$it" }, moveName = { gba?.moveName(it) ?: "#$it" },
            onClose = { s.trainerInfo = null },
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

private const val NEW_RUN_FAILED = "Could not start a new run. Try again from the Run tab."

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

/** Start a new run, in the shell's look (it was 8sp pixel font on the tracker's palette). */
@Composable
fun NewRunConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ShellDialog("Start a new run?", onDismiss) {
        Text("The current run ends and a new seed is rolled.", style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Spacer(Modifier.height(8.dp))
        // What survives and what does not, as the old dialog said.
        Text("Your in-game save is kept, so Continue starts straight into the new seed. Stat notes for this run are cleared.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        Spacer(Modifier.height(16.dp))
        ConfirmButtons("YES, NEW RUN", onConfirm, "CANCEL", onDismiss)
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
    if (ui.confirmReset) ShellDialog("Restart the game?", { ui.confirmReset = false }) {
        DialogBody("Anything not saved is lost.")
        ConfirmButtons("RESTART", { ui.confirmReset = false; onRestart() }, "CANCEL", { ui.confirmReset = false })
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
