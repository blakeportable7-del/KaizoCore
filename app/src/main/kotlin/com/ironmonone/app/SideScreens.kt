package com.ironmonone.app

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
) {
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
