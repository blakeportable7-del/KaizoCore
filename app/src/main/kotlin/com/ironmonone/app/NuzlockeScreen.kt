package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.gen3.Gen3Header
import com.ironmonone.core.Generation
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.nuzlocke.ClauseGroup
import com.ironmonone.tracker.nuzlocke.Heir
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.RunStatus
import com.ironmonone.tracker.nuzlocke.SafariRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Nuzlocke tab (2026-09-29): pick a preset, change any switch, pick a game, start. The ledger the run keeps is
 * in NuzlockeStore and the rules are in tracker-gba (nuzlocke/); this screen only starts runs and lists them.
 *
 * Randomizer starts a new randomized game through RunJob, the same job the Run tab's button uses, with a seed this
 * screen picks itself so the run's ledger can be tied to that game before the game exists. Every other preset
 * plays a game from the Library as it is, with its own save. Nothing here touches the game or the tracker: the
 * ledger is fed from the tracker panel in Play (NuzlockePanel).
 */
@Composable
fun NuzlockeScreen(
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    /** Library's games page, for the empty game list's button (2026-09-30, UX audit P0-13). */
    onAddGame: () -> Unit = {},
) {
    val context = LocalContext.current
    val filesDir = remember { context.applicationContext.filesDir }
    val store = remember { PrepStore(context) }
    val nz = remember { NuzlockeStore(filesDir) }
    // The randomizer's modes are the settings files on disk. The Run tab puts the bundled ones there the first
    // time it opens, and this tab can be opened first.
    remember { store.seedBundledPresets(context) }

    var refresh by remember { mutableIntStateOf(0) }
    // A randomize that finished while this tab was away re-reads the lists too.
    val jobGeneration = RunJob.generation
    LaunchedEffect(jobGeneration) { if (jobGeneration > 0) refresh++ }

    val plainGames = remember(refresh) { NuzlockeStarts.plainGames(runCatching { store.library.list() }.getOrDefault(emptyList())) }
    val preparedGames = remember(refresh) { NuzlockeStarts.randomGames(runCatching { store.listPrepared() }.getOrDefault(emptyList())) }
    val settingsList = remember(refresh) { runCatching { store.listSettings() }.getOrDefault(emptyList()) }
    val runs = remember(refresh) { nz.list() }
    val playingId = remember(refresh) { runCatching { NuzlockeTracking.current(filesDir)?.ledger?.meta?.id }.getOrNull() }

    var preset by remember { mutableStateOf(NuzlockePreset.STANDARD) }
    var rules by remember { mutableStateOf(NuzlockeRules.forPreset(NuzlockePreset.STANDARD)) }
    // The type picked for Monotype, kept while another preset is looked at.
    var typePick by remember { mutableStateOf<Int?>(null) }
    var gameKey by remember { mutableStateOf<String?>(null) }
    var modeKey by remember { mutableStateOf<String?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<StartConfirm?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // True once this screen has started a randomize, so its outcome is shown here and no other job's is.
    var waiting by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var nextLeg by remember { mutableStateOf<NextLeg?>(null) }

    // A new run ready goes to Play, but only while this tab is showing (the same rule as the Run tab).
    val playNow by rememberUpdatedState(onPlay)
    DisposableEffect(Unit) {
        val mine: () -> Unit = { playNow() }
        RunJob.onRunReady = mine
        onDispose { if (RunJob.onRunReady === mine) RunJob.onRunReady = null }
    }

    // A randomize that failed leaves the ledger made for it behind, and the next one of the same game makes the
    // last one's stale. Once the randomizer is idle they are put in order (NuzlockeStore.settleRandomized).
    val busy = RunJob.busy
    LaunchedEffect(busy, jobGeneration) {
        if (!busy) {
            val n = withContext(Dispatchers.IO) {
                nz.settleRandomized(store.loadLastRun()?.first, store.lastSeed(), System.currentTimeMillis())
            }
            if (n > 0) refresh++
        }
    }

    val randomized = preset == NuzlockePreset.RANDOMIZER
    val pickedPlain = if (randomized) null else plainGames.firstOrNull { it.name == gameKey } ?: plainGames.singleOrNull()
    val pickedPrepared = if (!randomized) null else preparedGames.firstOrNull { it.first.id == gameKey } ?: preparedGames.singleOrNull()
    val chosenKind: RomKind? = if (randomized) pickedPrepared?.first else pickedPlain?.kind
    val modes = remember(pickedPrepared?.first?.id, settingsList) {
        pickedPrepared?.let { RulesetCatalog.forRom(it.first, settingsList) }.orEmpty()
    }
    // Nuzlocke fair unless the player picks an IronMON mode (2026-09-30, UX audit P0-7): Kaizo was the default here.
    val fairPicked = modeKey == null || modeKey == NuzlockeFair.KEY
    val mode = if (fairPicked) null else modes.firstOrNull { it.key == modeKey }
    // A game that has been played already: the ledger starts from the team it finds there. Judged by what is in the
    // save (SaveCheck): an auto-save is written whenever a game is left, title screen included (QA 2026-09-29).
    val hasSave = remember(pickedPlain?.name, refresh) {
        pickedPlain?.let { e -> GameSession.forLibrary(e)?.let { s -> SaveCheck.hasProgress(store.sramFile(s), s.platform) } } == true
    }

    fun say(text: String, isError: Boolean = false) { message = text to isError }

    fun pickPreset(p: NuzlockePreset) {
        preset = p
        rules = NuzlockeRules.forPreset(p, if (p == NuzlockePreset.MONOTYPE) typePick else null)
        gameKey = null; modeKey = null; message = null; confirm = null
    }

    fun startNow() {
        val at = System.currentTimeMillis()
        val leg = nextLeg
        if (randomized) {
            val prepared = pickedPrepared ?: return
            val settingsFile = if (fairPicked) NuzlockeFair.file(store, prepared.first).getOrElse {
                say("Could not make the Nuzlocke fair settings for this game. Pick an IronMON mode instead, or try again.", true); return
            } else (mode ?: return).preset
            if (RunJob.busy) { say("The randomizer is busy. Try again in a moment.", true); return }
            // The seed is chosen here, so the ledger can be tied to the game before the game exists.
            val seed = SecureRandom().nextLong()
            val ledger = nz.start(
                NuzlockeStore.bindOfRun(prepared.first.id, seed), NuzlockeStarts.gameLabel(prepared.first, true), rules, at,
                system = NuzlockeStarts.systemOf(prepared.first), gameKey = NuzlockeStarts.gameKeyOf(prepared.first),
            )
            if (!RunJob.randomize(context, prepared, settingsFile, seed, nuzlocke = true)) {
                nz.delete(ledger.meta.id)
                say("The randomizer is busy. Try again in a moment.", true)
                return
            }
            waiting = true
            refresh++
        } else {
            val entry = pickedPlain ?: return
            val kind = entry.kind ?: return
            val session = GameSession.forLibrary(entry)
            if (session == null) { say("That game cannot be played here.", true); return }
            store.library.selectLibrary(entry)
            nz.start(
                session.id, NuzlockeStarts.gameLabel(kind, false), rules, at,
                genlockeId = leg?.genlockeId.orEmpty(), leg = leg?.leg ?: 0, carriedFrom = leg?.fromId.orEmpty(), carry = leg?.carry.orEmpty(),
                system = NuzlockeStarts.systemOf(kind), gameKey = NuzlockeStarts.gameKeyOf(kind),
            )
            nextLeg = null
            refresh++
            onPlay()
        }
    }

    fun tryStart() {
        message = null
        if (randomized) {
            val kind = chosenKind ?: return
            val inPlay = store.loadLastRun()?.first == kind.id && store.currentRunFor(kind).isFile
            if (inPlay) {
                confirm = StartConfirm("Randomize a new ${kind.displayName}? The run in play ends, and so does its ledger.", "Yes, randomize") { startNow() }
            } else startNow()
        } else {
            val entry = pickedPlain ?: return
            val running = GameSession.forLibrary(entry)?.let { nz.current(it.id) }?.takeIf { it.header.status == RunStatus.ACTIVE }
            if (running != null) {
                confirm = StartConfirm(
                    "This game has a Nuzlocke in progress: ${running.header.preset.label}, started ${dayOf(running.header.startedAt)}. " +
                        "Starting a new one replaces it. The old ledger stays in your runs.",
                    "Yes, start a new one",
                ) { startNow() }
            } else startNow()
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Gen3Header("Nuzlocke")
            Text(
                "One wild Pokémon per area, and a Pokémon that faints stays dead. Pick the rules and a game. " +
                    "The tracker keeps the ledger while you play, warns you when a rule is broken and never stops the game.",
                style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight,
            )
            Spacer(Modifier.height(16.dp))

            // ---- The next game of a Genlocke -------------------------------------------------------------
            nextLeg?.let { leg ->
                Gen3Box(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Genlocke, game ${leg.leg}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                        Text(
                            "Carrying on from ${leg.fromGame}: " +
                                (if (leg.carry.isEmpty()) "nobody was saved." else leg.carry.joinToString(", ") { it.speciesName } + "."),
                            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
                        )
                        Spacer(Modifier.height(8.dp))
                        Gen3Button("Cancel") { nextLeg = null; pickPreset(NuzlockePreset.STANDARD) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Preset ----------------------------------------------------------------------------------
            Gen3Header("Preset")
            Spacer(Modifier.height(4.dp))
            if (nextLeg == null) {
                ShellSegmented(
                    values = NuzlockePreset.entries.map { it.key },
                    selected = preset.key,
                    label = { k -> NuzlockePreset.byKey(k)?.label ?: k },
                    onSelect = { k -> NuzlockePreset.byKey(k)?.let { pickPreset(it) } },
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(preset.blurb, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
            if (preset == NuzlockePreset.MONOTYPE) {
                Spacer(Modifier.height(12.dp))
                Gen3Header("Type")
                Spacer(Modifier.height(4.dp))
                ShellSegmented(
                    values = NuzlockeStarts.types(chosenKind?.isNatDex == true, NuzlockeStarts.systemOf(chosenKind)).map { it.toString() },
                    selected = rules.monotypeType?.toString() ?: "",
                    label = { k -> Gen3Types.name(k.toInt()) },
                    onSelect = { k -> typePick = k.toInt(); rules = rules.copy(monotypeType = typePick) },
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- Game ------------------------------------------------------------------------------------
            Gen3Header("Game")
            Spacer(Modifier.height(4.dp))
            val choices = if (randomized) preparedGames.map { (kind, _) ->
                GameChoice(
                    kind.id, kind.displayName,
                    when {
                        kind in RomKind.allPatched -> "Patched game"
                        kind.isNatDex -> "Nat. Dex build"
                        else -> "Original game"
                    },
                    kind.platform,
                )
            } else plainGames.map { e -> GameChoice(e.name, stripKnownExt(e.name), e.subtitle, e.kind?.platform ?: e.platform) }
            val chosenKey = if (randomized) pickedPrepared?.first?.id else pickedPlain?.name
            if (choices.isEmpty()) {
                // Each names where to go and has the button to get there (2026-09-30, UX audit P0-13).
                if (randomized) EmptyState("No game to randomize yet", "Add a game in Library, My games. It shows here once the tracker can read it.",
                    actionLabel = "Add a game", onAction = onAddGame)
                else EmptyState("No game to play yet", "Add a Game Boy, Game Boy Advance or DS game in Library, My games. The tracker has to be able to read it.",
                    actionLabel = "Add a game", onAction = onAddGame)
            }
            for (c in choices) {
                NzGameCard(c, c.key == chosenKey) { gameKey = c.key; message = null }
                Spacer(Modifier.height(8.dp))
            }
            if (randomized) {
                val ds = NuzlockeStarts.systemOf(chosenKind).let { it == NuzlockeSystem.GEN4 || it == NuzlockeSystem.GEN5 }
                Text(
                    "Makes a new randomized game and opens it in Play. The run of that game you have now ends. " +
                        if (ds) "The level caps are the standard table for the game, because a DS game's boss levels cannot be read here."
                        else "Boss levels come from the new game, so the level caps follow it.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
                )
            } else {
                Text(
                    "Plays the game as it is, with its own save.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
                )
                if (hasSave) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This game already has a save. The ledger starts from the team it finds there and cannot know what came before. For a whole run, start a new game from the title screen.",
                        style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper,
                    )
                }
            }

            // ---- Randomizer mode -------------------------------------------------------------------------
            if (randomized && pickedPrepared != null) {
                Spacer(Modifier.height(16.dp))
                Gen3Header("How random")
                Spacer(Modifier.height(4.dp))
                // Nuzlocke fair first and picked; the IronMON modes after it, each saying it is one (2026-09-30).
                ShellSegmented(
                    values = listOf(NuzlockeFair.KEY) + modes.map { it.key },
                    selected = if (fairPicked) NuzlockeFair.KEY else mode?.key ?: "",
                    label = { k -> if (k == NuzlockeFair.KEY) NuzlockeFair.LABEL else modes.first { it.key == k }.label },
                    onSelect = { k -> modeKey = k },
                )
                Spacer(Modifier.height(4.dp))
                Text(if (fairPicked) NuzlockeFair.LINE else NuzlockeFair.IRONMON_LINE,
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
            }
            Spacer(Modifier.height(16.dp))

            // ---- The switches ----------------------------------------------------------------------------
            val changed = remember(rules) { rules.changesFromPreset() }
            NzDisclosure(
                "Rules in detail",
                if (changed.isEmpty()) "Every switch is on its preset's default."
                else "${changed.size} changed from ${preset.label}: " + changed.joinToString(", ") { it.title },
                showRules,
            ) { showRules = !showRules }
            if (showRules) {
                for (g in ClauseGroup.entries) {
                    val clauses = NuzlockeRules.CLAUSES.filter { it.group == g }
                    if (clauses.isEmpty()) continue
                    Spacer(Modifier.height(8.dp))
                    Gen3Header(g.title)
                    Gen3Box(Modifier.fillMaxWidth()) {
                        Column {
                            for (c in clauses) {
                                ShellSwitchRow(c.title, c.get(rules), c.detail) { on -> rules = c.set(rules, on) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Gen3Header("Safari Zone")
                Spacer(Modifier.height(4.dp))
                ShellSegmented(
                    values = SafariRule.entries.map { it.key },
                    selected = rules.safari.key,
                    label = { k -> SafariRule.byKey(k).label },
                    onSelect = { k -> rules = rules.copy(safari = SafariRule.byKey(k)) },
                )
                Spacer(Modifier.height(8.dp))
                Gen3Button("Back to the preset's defaults") { rules = NuzlockeRules.forPreset(preset, rules.monotypeType) }
            }

            // ---- Runs ------------------------------------------------------------------------------------
            if (runs.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Gen3Header("Your runs")
                Spacer(Modifier.height(4.dp))
                for (e in runs) {
                    val h = e.header
                    // What the panel is changing now is ahead of what the file holds, which is saved a moment later.
                    val meta = NuzlockeTracking.loaded(h.id)?.meta
                    val status = meta?.status ?: h.status
                    val reason = meta?.endReason ?: h.endReason
                    val canContinue = h.preset == NuzlockePreset.GENLOCKE && status == RunStatus.COMPLETE
                    RunCard(
                        title = h.preset.label + " on " + h.game,
                        line = NuzlockeStarts.statusLine(status, reason, h.startedAt),
                        playing = h.id == playingId && status == RunStatus.ACTIVE,
                        onOpen = { openId = h.id },
                        onDelete = { deleteId = h.id },
                        onNext = if (!canContinue) null else ({
                            val ledger = NuzlockeTracking.loaded(h.id) ?: nz.load(h.id)
                            if (ledger != null) {
                                nextLeg = NextLeg(h.id, ledger.meta.genlockeId, ledger.meta.leg + 1, nz.heirsOf(h.id), ledger.meta.game)
                                preset = NuzlockePreset.GENLOCKE
                                rules = ledger.meta.rules
                                gameKey = null; modeKey = null; message = null; confirm = null
                            }
                        }),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ---- Footer: the action, its question and its outcome ----------------------------------------
        Column(Modifier.fillMaxWidth().background(Shell.night).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (busy && waiting) {
                ProgressPanel(RunJob.phase)
                Spacer(Modifier.height(8.dp))
            }
            confirm?.let { q ->
                Gen3Box(Modifier.fillMaxWidth()) {
                    Column {
                        Text(q.text, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Gen3Button(q.yes, accent = true) { confirm = null; q.action() }
                            Spacer(Modifier.width(8.dp))
                            Gen3Button("Cancel") { confirm = null }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val shown = message ?: if (waiting) RunJob.status?.let { it to RunJob.statusIsError } else null
            shown?.let { (text, isError) ->
                StatusBanner(text, isError)
                Spacer(Modifier.height(8.dp))
            }
            val problem = NuzlockeStarts.problem(
                preset, rules, game = chosenKind != null, mode = fairPicked || mode != null, busy = busy, natDex = chosenKind?.isNatDex == true,
            )
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight, modifier = Modifier.padding(bottom = 6.dp))
            }
            Gen3Button(
                when {
                    nextLeg != null -> "Start the next game"
                    randomized -> "Randomize and start"
                    else -> "Start Nuzlocke"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = problem == null && confirm == null,
                accent = true,
            ) { tryStart() }
        }
    }

    // ---- The ledger of a run in the list ---------------------------------------------------------------
    val opened = openId?.let { id -> remember(id) { NuzlockeTracking.liveById(nz, id) } }
    // A run whose file went missing or cannot be read has nothing to open: the row is simply not opened.
    LaunchedEffect(openId, opened) { if (openId != null && opened == null) openId = null }
    if (opened != null) NuzlockeLedgerDialog(opened, null, onClose = { openId = null; refresh++ }, onChanged = { refresh++ })

    deleteId?.let { id ->
        val e = runs.firstOrNull { it.header.id == id }
        ShellDialog("Delete this run?", onDismiss = { deleteId = null }) {
            Text(
                (e?.let { it.header.preset.label + " on " + it.header.game } ?: "This run") +
                    " and its whole ledger are removed from this phone. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Gen3Button("Delete", accent = true) {
                    nz.delete(id)
                    if (openId == id) openId = null
                    deleteId = null
                    refresh++
                }
                Spacer(Modifier.width(8.dp))
                Gen3Button("Cancel") { deleteId = null }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// What the screen decides, apart from the screen, so a test can drive it
// ---------------------------------------------------------------------------------------------------------------

internal object NuzlockeStarts {

    /** The family of games a kind belongs to: it picks the data the ledger reads and the rules that make sense. */
    fun systemOf(kind: RomKind?): NuzlockeSystem = when (kind?.generation) {
        Generation.GB1 -> NuzlockeSystem.GEN1
        Generation.GBC2 -> NuzlockeSystem.GEN2
        Generation.NDS4 -> NuzlockeSystem.GEN4
        Generation.NDS5 -> NuzlockeSystem.GEN5
        else -> NuzlockeSystem.GEN3
    }

    /**
     * The game inside its family, for the rules page: "red", "crystal", "platinum". A patched game (Pseudo-fluctuating,
     * Super Kaizo, Faster) is the game it was patched from. Gen 3 runs carry none: their page has no per-game notes.
     */
    fun gameKeyOf(kind: RomKind?): String {
        if (kind == null || systemOf(kind) == NuzlockeSystem.GEN3) return ""
        return (kind.baseId ?: kind.id).substringBefore("-u")
    }

    /** Library games a plain run can start on: a copy the app has checked, which is what lets the tracker read it. Every console the app tracks. */
    fun plainGames(entries: List<LibraryStore.Entry>): List<LibraryStore.Entry> =
        entries.filter { it.verified && it.kind != null }

    /** Prepared games a randomized run can start on. */
    fun randomGames(prepared: List<Pair<RomKind, File>>): List<Pair<RomKind, File>> = prepared

    /** The name a run carries: the game as the app knows it, and how it was made. */
    fun gameLabel(kind: RomKind, randomized: Boolean): String = if (randomized) kind.displayName + ", randomized" else kind.displayName

    /** The types a Monotype run can pick: Fairy exists only in the Nat. Dex builds, and Red, Blue and Yellow have no Steel or Dark Pokemon. */
    fun types(natDex: Boolean, system: NuzlockeSystem = NuzlockeSystem.GEN3): List<Int> = system.types + if (natDex) listOf(FAIRY) else emptyList()

    /** Why Start is off, in words for the player, or null when it can run. */
    fun problem(preset: NuzlockePreset, rules: NuzlockeRules, game: Boolean, mode: Boolean, busy: Boolean, natDex: Boolean): String? = when {
        !game -> "Pick a game."
        preset == NuzlockePreset.MONOTYPE && rules.monotypeType == null -> "Pick the type."
        preset == NuzlockePreset.MONOTYPE && rules.monotypeType == FAIRY && !natDex -> "Fairy only exists in the Nat. Dex builds."
        preset == NuzlockePreset.RANDOMIZER && !mode -> "This game has no randomizer mode to pick."
        preset == NuzlockePreset.RANDOMIZER && busy -> "The randomizer is busy. Try again in a moment."
        else -> null
    }

    /** A run's line in the list: where it stands, in a few plain words. */
    fun statusLine(status: RunStatus, endReason: String, startedAt: Long): String = when (status) {
        RunStatus.ACTIVE -> "In progress since ${dayOf(startedAt)}"
        RunStatus.OVER -> if (endReason.isBlank()) "Over" else "Over: $endReason"
        RunStatus.COMPLETE -> "Finished. The Champion is beaten."
        RunStatus.ABANDONED -> "Replaced by a newer run"
    }

    /** Gen 3 type id of Fairy, which the Nat. Dex builds add after Dark. */
    const val FAIRY = 18
}

private fun dayOf(at: Long): String = if (at <= 0) "an unknown day" else SimpleDateFormat("MMM d", Locale.US).format(Date(at))

/** The next game of a Genlocke: which run it carries on from and who comes along. */
private class NextLeg(val fromId: String, val genlockeId: String, val leg: Int, val carry: List<Heir>, val fromGame: String)

/** A question before a start that replaces something. */
private class StartConfirm(val text: String, val yes: String, val action: () -> Unit)

private class GameChoice(val key: String, val title: String, val subtitle: String, val platform: Platform?)

// ---------------------------------------------------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------------------------------------------------

/** One game to pick: a card with the console badge, the name and what kind of game it is. The picked one carries the accent outline. */
@Composable
private fun NzGameCard(g: GameChoice, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Shell.cardRadius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Shell.paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) Shell.accent else Shell.hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(g.platform)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) Shell.goodOnPaper else Shell.hintOnPaper)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Shell.accentOnNight)
    }
}

/** A folded section: a card row with a title, one line saying what is inside, and a chevron. */
@Composable
private fun NzDisclosure(title: String, detail: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(Shell.cardRadius)).background(Shell.paper)
            .clickable(onClick = onToggle)
            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        }
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "Close" else "Open",
            tint = Shell.hintOnPaper,
        )
    }
}

/** One run in the list: what it is, where it stands, and what can be done with it. */
@Composable
private fun RunCard(title: String, line: String, playing: Boolean, onOpen: () -> Unit, onDelete: () -> Unit, onNext: (() -> Unit)?) {
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(line, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            if (playing) Text("Tracking it now in Play.", style = MaterialTheme.typography.bodySmall, color = Shell.goodOnPaper)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button("Open the ledger", onClick = onOpen)
                Gen3Button("Delete", onClick = onDelete)
            }
            if (onNext != null) {
                Spacer(Modifier.height(8.dp))
                Gen3Button("Next game of this Genlocke", accent = true, onClick = onNext)
            }
        }
    }
}
