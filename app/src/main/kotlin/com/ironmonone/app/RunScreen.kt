package com.ironmonone.app

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import java.io.File

/**
 * The loop: pick a game, pick a mode (Kaizo opens, 2026-09-30), Start attempt N, play.
 * A new attempt = tap again; the previous run is kept.
 *
 * Engine name is always visible (brief section 6), and a vanilla settings file on a
 * Nat. Dex ROM is refused BEFORE the engine sees it.
 */
@Composable
fun RunScreen(
    modifier: Modifier = Modifier,
    /** The preset to edit, and the generation of the ROM it will be run on. */
    onEdit: (File, String?) -> Unit = { _, _ -> },
    /** Go to the game. Called when a new run is ready: that is always the next step. */
    onPlay: () -> Unit = {},
    /**
     * Open Library where a game is added (2026-09-30, UX audit P0-13): the empty game list named that place and had
     * no way to it. The default does nothing, so a caller with no Library to open leaves the button inert.
     */
    onAddGame: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }

    // First run: put the bundled presets on disk before anything reads the list,
    // so the picker and the editor are usable with no import step.
    remember { store.seedBundledPresets(context) }

    var refresh by remember { mutableIntStateOf(0) }
    // A job that finished while this tab was away re-reads the lists too.
    val jobGeneration = RunJob.generation
    LaunchedEffect(jobGeneration) { if (jobGeneration > 0) refresh++ }
    val preparedList = remember(refresh) { store.listPrepared() }
    val settingsList = remember(refresh) { store.listSettings() }

    // The selection follows the game you are actually playing. It used to
    // default to "first Nat. Dex ROM + Nat. Dex Kaizo" on EVERY visit to this
    // tab, so tabbing to Play and back silently re-pointed NEW RUN at a
    // different game - caught in the audit when a FireRed re-roll rotated the
    // Emerald Nat. Dex run instead.
    val lastRun = remember(refresh) { store.loadLastRun() }
    // The mode follows the game (2026-09-30, UX audit P0-12): the file the player last picked for it, else the file
    // of the run last started on it, else Kaizo (RulesetCatalog.openingFile). It used to be the first mode in the
    // row, which was Standard, on a screen named Kaizo IronMON.
    val modeMemory = remember { RunModeMemory.of(context.filesDir) }
    fun openingFor(kind: RomKind): File? = RulesetCatalog.openingFile(
        kind, settingsList, modeMemory.get(kind.id), lastRun?.takeIf { it.first == kind.id }?.second)
    val firstRom = preparedList.firstOrNull { it.first.id == lastRun?.first }
        ?: preparedList.firstOrNull { it.first.isNatDex }
        ?: preparedList.firstOrNull()
    var selectedRom by remember(refresh) { mutableStateOf(firstRom) }
    var selectedSettings by remember(refresh) {
        mutableStateOf(
            firstRom?.let { openingFor(it.first) }
                ?: settingsList.firstOrNull { it.name == lastRun?.second }
                ?: settingsList.firstOrNull {
                    val i = RnqsInfo.of(it); i.ruleset == "kaizo" && i.natDex
                } ?: settingsList.firstOrNull())
    }
    // Every pick is kept for its game, so switching games and back brings each one's mode back.
    fun pickSettings(f: File, game: RomKind? = selectedRom?.first) {
        selectedSettings = f
        game?.let { modeMemory.set(it.id, f.name) }
    }
    // MODE. The modes that exist for the selected ROM, derived from the preset files themselves
    // (RulesetCatalog), so importing a preset adds a mode with no code change.
    val modes = remember(selectedRom, settingsList) {
        selectedRom?.let { RulesetCatalog.forRom(it.first, settingsList) } ?: emptyList()
    }
    var confirmNewRun by remember { mutableStateOf(false) }
    // Build your own (2026-09-29): the guided builder (BuildYourGame.kt) takes this tab while it is open, for
    // the game that was picked when it opened (a save re-reads the lists, which re-derives the pick above).
    // builtName is the file it just saved: game and file are picked here once the lists are read again.
    var buildGame by remember { mutableStateOf<Pair<RomKind, File>?>(null) }
    var builtName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) {
        val n = builtName ?: return@LaunchedEffect
        builtName = null
        val (rom, file) = GameBuild.selectionAfterSave(preparedList, settingsList, buildGame?.first, n)
        rom?.let { selectedRom = it }
        file?.let { pickSettings(it, (rom ?: selectedRom)?.first) }
    }

    // Gold, Silver and Crystal without the growth patch (GrowthPatch): once the patched copy is made, it is picked,
    // with the mode that was picked, when the lists are read again.
    var pickAfterPatch by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(refresh) {
        val (id, mode) = pickAfterPatch ?: return@LaunchedEffect
        val made = preparedList.firstOrNull { it.first.id == id } ?: return@LaunchedEffect
        pickAfterPatch = null
        selectedRom = made
        (settingsList.firstOrNull { it.name == mode } ?: openingFor(made.first))?.let { pickSettings(it, made.first) }
    }

    // The job, its phase and its outcome live in RunJob, not here: leaving
    // the tab used to cancel a randomize half way (2026-09-27, audit).
    val busy = RunJob.busy
    val status = RunJob.status
    val statusIsError = RunJob.statusIsError
    val phase = RunJob.phase
    fun say(t: String, err: Boolean = false) = RunJob.say(t, err)
    // Go to Play when a run lands, but only while this tab is showing.
    val playNow by androidx.compose.runtime.rememberUpdatedState(onPlay)
    androidx.compose.runtime.DisposableEffect(Unit) {
        RunJob.onRunReady = { playNow() }
        onDispose { RunJob.onRunReady = null }
    }

    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // The phase is set before the panel shows. It showed whatever the last
        // randomize left ("Saving run") while importing (2026-09-27, audit).
        RunJob.run(RunPhase.IMPORTING, "Could not add those files") {
            val lines = ArrayList<String>()
            var failed = false
            uris.forEach { uri ->
                val name = runCatching { context.displayNameOf(uri) }.getOrDefault("One file")
                val r = runCatching {
                    val b = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    store.addSettingsFile(name, b).getOrThrow()
                }
                r.onSuccess { lines += "Added ${it.name.removeSuffix(".rnqs")}." }
                r.onFailure {
                    failed = true
                    lines += (it as? IllegalArgumentException)?.message ?: "Could not read $name."
                    runCatching { android.util.Log.w("IronMonOne", "import failed", it) }
                }
            }
            lines.joinToString("\n") to failed
        }
    }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        RunJob.run(RunPhase.EXPORTING, "Could not export the run") {
            context.contentResolver.openOutputStream(uri)!!
                .use { it.write(store.currentRun.readBytes()) }
            "Exported. Open it in another emulator to play it there." to false
        }
    }

    /** The one guard that stops a crashed intro, and what it says (RunPairing). */
    fun pairingProblem(): String? = RunPairing.problem(selectedRom, selectedSettings)

    fun newRun() {
        // The guard first: with no mode picked, RANDOMIZE used to
        // return before it and do nothing at all (2026-09-27, audit).
        pairingProblem()?.let { say(it, true); return }
        val rom = selectedRom ?: return
        val s = selectedSettings ?: return
        // What ran is what this game opens on next time (2026-09-30, UX audit P0-12).
        modeMemory.set(rom.first.id, s.name)
        // No seed given: a new one, or the run made ahead for this game and
        // mode when there is one (NextRun), which is seconds instead of 40.
        RunJob.randomize(context, rom, s, seed = null)
    }

    /**
     * The start button. A pairing the run would be refused on is said now, not after the question (2026-09-30), and a run
     * in play is asked about first, the same gate the Play screen's NEW chip has.
     */
    fun startTapped() {
        val problem = pairingProblem()
        if (problem != null) { say(problem, true); return }
        if (store.currentRun.exists()) confirmNewRun = true else newRun()
    }

    val building = buildGame
    if (building != null) {
        BuildYourGame(
            store, building, settingsList, hasRun = store.currentRun.exists(),
            onClose = { buildGame = null }, onSaved = { f -> builtName = f.name; refresh++ }, onEdit = onEdit,
            modifier = modifier,
        )
        return
    }

    // The run in play (RunInPlay), what the next attempt will be numbered, the mode as the confirm names it, the game
    // list with the selected game first, and how many library files there are for an empty list to say something about.
    val inPlay = runInPlay(store, refresh + jobGeneration)
    // Counted per game and settings file (PrepStore): the file picked is the one the next attempt is of.
    val nextAttempt = (selectedRom?.first?.id?.let { store.attemptOf(it, selectedSettings?.name) } ?: 0) + 1
    // A file that is not one KaizoCore comes with is a custom game, said wherever the mode is named (CustomRuns, R2).
    val customPicked = remember(selectedSettings) { selectedSettings?.let { CustomRuns.isCustom(context, it) } == true }
    val modeName = selectedSettings?.let { CustomRuns.label(RunCopy.modeName(RulesetCatalog.modeOf(modes, it)?.label, it), customPicked) }
    val gamesShown = remember(refresh) { RunGames.selectedFirst(preparedList, firstRom?.first?.id) }
    val libraryFiles = remember(refresh) {
        if (preparedList.isEmpty()) runCatching { store.library.list().size }.getOrDefault(0) else 0
    }

    // Scrolling content ABOVE, fixed footer BELOW. The whole screen used to be
    // one scrolling column, which put NEW RUN under the nav bar until you
    // swiped and put its result off-screen entirely.
    Column(modifier.fillMaxSize()) {
      Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        // The engine follows the game (Randomizers.randomize): the Nat. Dex fork for a
        // Nat. Dex build, ZX for everything else. With no game picked there is no engine to name.
        val engineName = when (selectedRom?.first?.isNatDex) {
            null -> "chosen by the game you pick"
            true -> NatDexEngine.DISPLAY_NAME
            false -> ZxEngine.DISPLAY_NAME
        }
        Box(Modifier.fillMaxWidth()) {
            Column {
        // The attempt count is the emotional core of IronMON and lived only
        // inside the tracker, mid-battle. It belongs on the screen where you
        // start the next one.
        selectedRom?.first?.let { rom ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Run", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
                    Text(
                        "Attempt ${store.attemptOf(rom.id, selectedSettings?.name)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Shell.textOnNight,
                    )
                }
                // The STORED seed, in the same 16 digits as the status line.
                // The chip showed 8 digits against the status line's 16 and
                // vanished on a tab change although the seed is saved (2026-09-27, audit).
                val seedShown = remember(refresh, jobGeneration, rom.id) {
                    store.lastSeed()?.takeIf { store.loadLastRun()?.first == rom.id }
                }
                seedShown?.let {
                    Text(
                        "seed $it",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Shell.accentOnNight,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Shell.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Play opens the Library game last picked there, and until 2026-09-29 nothing led back
        // to the IronMON run but a new one, which ends it (found in the rc30 QA pass). While a
        // run exists it is one tap away here, and this is the one filled button on the screen
        // (2026-09-30, UX audit P2: two red primaries); starting another attempt is the quiet one
        // below. When Play is on a Library game instead, the line under the button says so.
        inPlay?.let { run ->
            Gen3Button(if (run.nuzlocke) RunCopy.CONTINUE_NUZLOCKE else RunCopy.continueRun(run.attempt), accent = true) {
                store.library.selectRun()
                onPlay()
            }
            run.libraryGame?.let { libraryGame ->
                Text(
                    "Play is on $libraryGame from the Library. Your run is where you left it.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        com.ironmonone.app.gen3.Gen3Header("Game")
        Spacer(Modifier.height(4.dp))
        if (preparedList.isEmpty()) {
            // Which case it is, and the button is the next step (2026-09-30, UX audit P0-13).
            NoGamesCard(libraryFiles, onAddGame)
        }
        // The game selected when the screen opens comes first: with seven games it was the last card, below the fold (P1).
        gamesShown.forEach { pair ->
            GameCard(pair.first, selectedRom?.first?.id == pair.first.id) {
                if (selectedRom?.first?.id != pair.first.id) {
                    selectedRom = pair
                    // A game brings back its own last mode, else Kaizo (2026-09-30, UX audit P0-12).
                    openingFor(pair.first)?.let { selectedSettings = it }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        // Picking a mode selects its preset; the Settings list below stays for choosing a specific file.
        // A ROM change can leave a preset from another family selected; the
        // pairing guard would refuse it at RUN. Snap to the game's own mode instead: its last,
        // else Kaizo (openingFor), not the first in the row, which was Standard.
        LaunchedEffect(selectedRom) {
            val rom = selectedRom?.first ?: return@LaunchedEffect
            val cur = selectedSettings
            if (cur == null || !RulesetCatalog.isCompatible(rom, cur)) {
                openingFor(rom)?.let { selectedSettings = it }
            }
        }
        if (modes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            com.ironmonone.app.gen3.Gen3Header("Mode")
            Text(RulesetCatalog.MODE_HEADER, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
            Spacer(Modifier.height(6.dp))
            ShellSegmented(
                values = modes.map { it.key },
                selected = RulesetCatalog.modeOf(modes, selectedSettings)?.key ?: "",
                label = { k -> modes.first { it.key == k }.label },
                onSelect = { k -> pickSettings(modes.first { it.key == k }.preset) },
            )
            // One plain line for the mode picked, and where its rules are (2026-09-30, UX audit P0-12).
            selectedRom?.first?.let { rom -> RulesetCatalog.modeOf(modes, selectedSettings)?.let { mode ->
                ModeBlurb(rom, mode)
                if (customPicked) Text(CustomRuns.line(mode.label), style = MaterialTheme.typography.bodySmall,
                    color = Shell.dangerOnPaper, modifier = Modifier.padding(vertical = 4.dp))
            } }
            Spacer(Modifier.height(4.dp))
        }
        // What the rules add around the mode (ExtraPasses), shown and the
        // player's to switch, never forced (Blake, 2026-09-29).
        selectedRom?.first?.let { rom -> selectedSettings?.let { s -> ExtraPassRows(rom, s) } }
        // Super Kaizo on a build without smart AI: said beside the mode, with
        // where to make the build, and never a refusal.
        selectedRom?.first?.let { rom ->
            val w = remember(rom.id, selectedSettings) { RulesetCatalog.superKaizoWarning(rom, selectedSettings?.let { RnqsInfo.of(it).ruleset }) }
            if (w != null) {
                Text(w, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        // Gold, Silver and Crystal without the growth patch: said, with the button that makes the patched copy.
        selectedRom?.let { (rom, base) ->
            GrowthPatch.warning(rom)?.let { w ->
                GrowthPatchNotice(w, busy) {
                    val out = GrowthPatch.buildFor(rom) ?: return@GrowthPatchNotice
                    pickAfterPatch = out.id to selectedSettings?.name
                    RunJob.run(RunPhase.PATCHING, GrowthPatch.FAILED) {
                        GrowthPatch.made(GrowthPatch.make(context, store, rom, base)) to false
                    }
                }
            }
        }
        // FireRed 1.1 and Emerald with no Nat. Dex version yet: said here with the button that makes it from this copy, which
        // stays as it is (Blake, 2026-09-30: "make it easier to see the natl dex options for all games that have it").
        selectedRom?.let { (rom, base) ->
            val natDex = NatDexInfo.buildOf(rom)
            if (natDex != null && preparedList.none { it.first.id == natDex.id }) {
                NatDexNotice(busy) {
                    pickAfterPatch = natDex.id to null
                    RunJob.run(RunPhase.PATCHING, "Could not make the Nat. Dex version.") {
                        val tmp = java.io.File(context.cacheDir, "prep-" + System.nanoTime())
                        try {
                            base.copyTo(tmp, overwrite = true)
                            PrepRun.run(context, store, tmp, rom, PrepOptions.Mode.NATDEX.name, FileProgress()) + " It is listed above; pick it for a Nat. Dex mode." to false
                        } finally {
                            tmp.delete()
                        }
                    }
                }
            }
        }
        // Build your own (2026-09-29): starters and plain-word choices, saved as a settings file (BuildYourGame.kt).
        selectedRom?.let { rom -> BuildYourGameEntry { buildGame = rom } }
        // RUN used to open on three empty favourite boxes, the engine's name and
        // a list of raw settings file names, before the game and mode: the
        // choices every run needs came last (audit, 2026-09-27). The optional
        // parts now sit folded below, each saying what is inside.
        Spacer(Modifier.height(10.dp))
        var showFavorites by remember { mutableStateOf(false) }
        val favFilled = Favorites.slots(store, selectedRom?.first?.id, Favorites.slotCount(selectedRom?.first)).count { it.isNotBlank() }
        RunDisclosure("Startup favorites (optional)",
            if (favFilled == 0) RunCopy.FAVORITES_LINE else "$favFilled set.",
            showFavorites) { showFavorites = !showFavorites }
        if (showFavorites) {
            // The PC tracker's startup favorites: three Pokémon it shows on the
            // new-game screen. Typed by name here; the tracker's no-party card
            // repeats them before a party exists, as the PC trackers' startup and title screens do.
            Text("Startup favorites", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // As many boxes as the game's PC tracker keeps, and only that game's dex in the list.
            val favCount = Favorites.slotCount(selectedRom?.first)
            val favMax = Favorites.maxDex(selectedRom?.first)
            val favRomId = selectedRom?.first?.id
            var favSlots by remember(favCount, favRomId) { mutableStateOf(Favorites.slots(store, favRomId, favCount)) }
            // Which box is being typed in: its suggestions show under the row.
            var favActive by remember { mutableStateOf(-1) }
            // One full-width box per slot, stacked. Four or five boxes in one
            // row left about 27dp of text each on a DS game (2026-09-27, audit).
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                favSlots.forEachIndexed { i, v ->
                    // Known FOR THIS GAME: a name past its dex (a Gen 5 species on a standard Emerald) is as wrong as a typo.
                    val known = v.isBlank() || Favorites.inGame(v, favMax)
                    androidx.compose.material3.OutlinedTextField(
                        value = v,
                        onValueChange = { t ->
                            favSlots = favSlots.toMutableList().also { it[i] = t }
                            favActive = i
                            Favorites.save(store, favRomId, favSlots)
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) favActive = i },
                        singleLine = true,
                        isError = !known,
                        placeholder = { Text("Favorite ${i + 1}") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // The names that start with what is typed in the active box, narrowing
            // with every letter; a tap fills the box. Dex order, eight at most.
            val favHints = if (favActive in favSlots.indices) Favorites.suggest(favSlots[favActive], maxId = favMax) else emptyList()
            if (favHints.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    favHints.forEach { name ->
                        // As the name is written: upper-casing it and passing it
                        // through Shell.label mangled "Mr. Mime" and "Ho-Oh".
                        com.ironmonone.app.gen3.Gen3Button(name, raw = true) {
                            favSlots = favSlots.toMutableList().also { it[favActive] = name }
                            Favorites.save(store, favRomId, favSlots)
                            favActive = -1
                        }
                    }
                }
            }
            val favDs = selectedRom?.first?.platform == com.ironmonone.core.Platform.NDS
            // The mode picked: its rules for favorites below, and whether the tracker names a favorite's ball (FavoriteBall).
            val favMode = RulesetCatalog.modeOf(modes, selectedSettings)?.key
            val favBall = selectedRom?.first?.platform == com.ironmonone.core.Platform.GBA && favMode != null && favMode != FavoriteBall.JOURNEY
            Text(
                // The NDS tracker's title screen shows four: a Gen 5 game's five take turns, a Gen 4 game's four stand still.
                if (favSlots.all { it.isBlank() || Favorites.inGame(it, favMax) }) (when {
                    favDs && favCount > 4 -> "The DS tracker keeps $favCount and shows four at a time on its title screen, in turn."
                    favDs -> "The DS tracker keeps four and shows them on its title screen."
                    favCount > 3 -> "A Nat. Dex game allows $favCount. The tracker shows them all before your first Pokémon."
                    else -> "Shown on the tracker before your first Pokémon, as the PC tracker's startup screen shows them."
                } + if (favBall) " " + RunCopy.FAVORITE_BALL else "")
                else "A name in red is not a Pokémon this game has.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
            // The run's own rules for favorites, from the book for this game and mode (Blake, 2026-10-01: "base it on
            // whatever game is doing a run because different kaizo's have different rules").
            val favBook = remember(selectedRom?.first?.id, favMode) {
                val k = selectedRom?.first
                if (k == null || favMode == null) null else Rules.text(context, Rules.dirFor(k.family, k.isNatDex), favMode)
            }
            FavoriteRulesBlock(favBook, favMode, favSlots)
            Spacer(Modifier.height(12.dp))


        }
        var showAdvanced by remember { mutableStateOf(false) }
        RunDisclosure("Randomizer settings (advanced)",
            selectedSettings?.let { "Using " + it.name.substringBeforeLast('.') + ". Import, edit or pick a file." } ?: "Import, edit or pick a settings file.",
            showAdvanced) { showAdvanced = !showAdvanced }
        if (showAdvanced) {
            Text(
                "Engine: $engineName",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Shell.hintOnPaper,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Gen3Button("IMPORT", enabled = !busy) { importSettings.launch(arrayOf("*/*")) }
                Spacer(Modifier.width(8.dp))
                Gen3Button("EDIT", enabled = !busy && selectedSettings != null) {
                    selectedSettings?.let {
                        // The editor names a saved copy after this game when the
                        // file's own name has no game tag (PresetStrings).
                        PresetStrings.targetFamily = selectedRom?.first?.family
                        PresetStrings.targetNatDex = selectedRom?.first?.isNatDex
                        onEdit(it, selectedRom?.first?.generation?.name)
                    }
                }
            }
            val labels = remember(settingsList) {
                RnqsInfo.displayLabelsFor(settingsList)
            }
            // With every game's presets bundled the full list is 39 rows. Show the
            // selected ROM's family plus anything untagged (an imported custom
            // file); the Mode row above is the normal way to pick.
            val visibleSettings = remember(selectedRom, settingsList) {
                val rom = selectedRom?.first
                // Blake, 2026-09-07: only the loaded game's files, never all of them.
                if (rom == null) emptyList()
                else settingsList.filter { f ->
                    val i = RnqsInfo.of(f)
                    // Untagged files show only for a game of their own engine.
                    (i.gameTag == null && !i.appliedByApp && i.natDex == rom.isNatDex) || RulesetCatalog.isCompatible(rom, f)
                }
            }
            if (selectedRom == null) Text("Pick a game above to see its settings files.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            visibleSettings.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { pickSettings(f) }
                        // A full touch target per file; the rows were about 40dp.
                        .heightIn(min = Shell.touchTarget).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(selectedSettings?.name == f.name)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        // Two files can parse to the SAME label - "FRLG Kaizo.rnqs"
                        // and "FRLG Kaizo (edited).rnqs" both read "FRLG Kaizo",
                        // so the picker showed two identical rows and the only way
                        // to tell them apart was the filename underneath. When a
                        // label collides, lead with the file stem, which is unique
                        // by definition, and keep the parsed ruleset beneath it.
                        val (title, subtitle) = labels[settingsList.indexOf(f)]
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Shell.hintOnPaper,
                        )
                    }
                }
            }


        }
        Spacer(Modifier.height(16.dp))
        // The primary action and its outcome now live in the sticky footer at
        // the bottom of this screen, where they cannot scroll out of reach.

        if (store.currentRun.exists()) {
            Spacer(Modifier.height(8.dp))
            Gen3Button("EXPORT CURRENT RUN", enabled = !busy) {
                export.launch("KaizoCore_CurrentRun." + store.currentRun.extension)
            }
        }
            }
        }

        // Run codes (roadmap item 7): share the run in play, or build a friend's (RunCodeUi.kt).
        Spacer(Modifier.height(10.dp))
        var showCodes by remember { mutableStateOf(false) }
        RunDisclosure("Run codes", "Share this run, or play the same game as a friend.", showCodes) { showCodes = !showCodes }
        if (showCodes) RunCodeSection(store, preparedList, settingsList, refresh + jobGeneration, busy)
      }

      // ---- Sticky footer: the action, its progress, and its outcome -------
      Column(Modifier.fillMaxWidth().background(Shell.night).padding(horizontal = 16.dp, vertical = 10.dp)) {
          if (busy) {
              ProgressPanel(phase)
              Spacer(Modifier.height(8.dp))
          }
          // The confirmation belongs WITH the button that raises it. It used
          // to sit in the scrolling content while its trigger sat down here,
          // so a longer settings list would have pushed the question
          // off-screen - the same defect the sticky footer just fixed.
          if (confirmNewRun) {
              Gen3Box(Modifier.fillMaxWidth()) {
                  Column {
                      Text(
                          RunCopy.confirmNewAttempt(
                              inPlay?.let { RunCopy.EndingRun(it.attempt, it.game, it.nuzlocke) }, nextAttempt,
                              selectedRom?.first?.displayName ?: "?", modeName),
                          style = MaterialTheme.typography.bodyMedium,
                          color = Shell.inkOnPaper,
                      )
                      Spacer(Modifier.height(8.dp))
                      Row {
                          // Named for what it starts, and quiet while Continue is the red one (2026-09-30, UX audit P1, P2).
                          Gen3Button(RunCopy.start(nextAttempt), accent = inPlay == null) {
                              confirmNewRun = false; newRun()
                          }
                          Spacer(Modifier.width(8.dp))
                          Gen3Button(RunCopy.CANCEL) { confirmNewRun = false }
                      }
                  }
              }
              Spacer(Modifier.height(8.dp))
          }
          status?.let {
              StatusBanner(it, statusIsError)
              Spacer(Modifier.height(8.dp))
          }
          Gen3Button(
              RunCopy.start(nextAttempt),
              modifier = Modifier.fillMaxWidth(),
              enabled = !busy && preparedList.isNotEmpty(),
              // Red only when there is no run to continue: the button that continues is the red one (2026-09-30, UX audit P2).
              accent = inPlay == null,
              // Rolling a new seed ends the current run; startTapped asks first.
          ) { startTapped() }
      }
    }
}

/**
 * The run in play, when one is on disk (2026-09-30): which attempt it is and which game, and the Library game Play is on
 * instead when it is on one. [key] re-reads it when a job finishes or the tab refreshes. Until 2026-09-30 this was only the
 * second half, the run to go back to while Play is on a Library game, so the screen had no way to continue a run that
 * Play already had up.
 */
private class RunInPlay(val attempt: Int, val game: String, val libraryGame: String?, val nuzlocke: Boolean = false)

@Composable
private fun runInPlay(store: PrepStore, key: Int): RunInPlay? = remember(key) {
    val kind = store.loadLastRun()?.first?.let { RomKind.byId(it) } ?: return@remember null
    if (!store.currentRunFor(kind).isFile) return@remember null
    val library = store.library.selectedLibraryName()
    val shown = library?.let { name ->
        store.library.find(name)?.takeIf { it.verified }?.kind?.displayName ?: name.substringBeforeLast('.')
    }
    RunInPlay(store.attempt(kind.id), kind.displayName, shown, store.lastRunNuzlocke())
}

/**
 * The passes the rules add, under the Mode row (ExtraPasses): the 60% levels
 * where the game takes the pre-pass (one line where the build already carries
 * it), and Red, Blue and Yellow's PART 2. Each is on by default only where an
 * official preset calls for it, and the player's to switch.
 */
@Composable
private fun ExtraPassRows(rom: RomKind, settings: File) {
    val context = LocalContext.current
    if (ExtraPasses.takesPart2(rom)) {
        var version by remember { mutableIntStateOf(0) }
        val on = remember(rom.id, settings.absolutePath, settings.lastModified(), version) { ExtraPasses.part2On(context, rom, settings) }
        val official = remember(settings.absolutePath, settings.lastModified()) { ExtraPasses.isOfficial(context, settings) }
        PassSwitch(
            "Second pass (PART 2)", on,
            (if (ExtraPasses.patched(rom)) "This build has the pseudo-fluctuating patch, the other official way, so it needs only the first pass. " +
                "On, PART 2 sets every Pok\u00e9mon to Slow growth and the patch no longer counts."
            else "Sets every Pok\u00e9mon to Slow growth so nothing evolves into a legendary: the official way to play without the pseudo-fluctuating patch.") +
                if (official) "" else " This settings file is custom or edited, so it runs exactly as saved unless you switch this on.",
        ) { ExtraPasses.choosePart2(context, rom, settings, !on); version++ }
        return
    }
    if (ExtraPasses.carries60(rom)) {
        Text(
            "Faster Emerald 1.3.2 already raises trainer and wild levels 6%, so Kaizo and every mode built on it " +
                "come out at the official 60% with no extra pass. Standard and Ultimate use Faster Emerald 1.2.1.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
        )
        return
    }
    if (ExtraPasses.prePassName(rom) == null) return
    var version by remember { mutableIntStateOf(0) }
    val on = remember(rom.id, settings.absolutePath, settings.lastModified(), version) { ExtraPasses.prePassOn(context, rom, settings) }
    val official = remember(settings.absolutePath, settings.lastModified()) { ExtraPasses.isOfficial(context, settings) }
    val game = if (rom.family == "GSC") "Gold, Silver and Crystal Kaizo and Survival" else "Emerald Kaizo and every mode built on it"
    PassSwitch(
        "Official 60% levels", on,
        "$game: trainer and wild levels +6% first, then the mode's +50%, as the official settings do." +
            if (official) "" else " This settings file is custom or edited, so it runs exactly as saved unless you switch this on.",
    ) { ExtraPasses.choosePrePass(context, rom, settings, !on); version++ }
}

/** One switch row: a check box, a title and one plain line, the whole row the target. */
@Composable
private fun PassSwitch(title: String, on: Boolean, detail: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }
            .heightIn(min = Shell.touchTarget).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellCheck(on)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        }
    }
}

/** A folded section on RUN: a card row with a title, one line saying what is inside, and a chevron. */
@Composable
private fun RunDisclosure(title: String, detail: String, open: Boolean, onToggle: () -> Unit) {
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
        androidx.compose.material3.Icon(
            if (open) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp
            else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "Close" else "Open",
            tint = Shell.hintOnPaper,
        )
    }
}

/**
 * One game on RUN: a card with the console badge, the name and what kind of
 * file it is. The picked one carries the accent outline.
 */
@Composable
private fun GameCard(kind: com.ironmonone.core.RomKind, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Shell.cardRadius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Shell.paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) Shell.accent else Shell.hairline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(kind.platform)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(
                when {
                    kind in com.ironmonone.core.RomKind.allPatched -> "Patched game"
                    kind.isNatDex -> "Nat. Dex build"
                    else -> "Original game"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) Shell.goodOnPaper else Shell.hintOnPaper,
            )
        }
        if (selected) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = Shell.accentOnNight,
            )
        }
    }
}

/** A small rounded tile naming the console: GBA, DS, GBC. */
@Composable
internal fun PlatformBadge(platform: com.ironmonone.core.Platform?) {
    val (label, tint) = when (platform) {
        com.ironmonone.core.Platform.NDS -> "DS" to Color(0xFF3DD6C3)
        com.ironmonone.core.Platform.GBA -> "GBA" to Color(0xFFFF8589)
        null -> "?" to Shell.hintOnPaper
        else -> platform.name to Color(0xFFF5C26B)
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

/**
 * The mode picked, in words: one plain line (RulesetCatalog.modeLine) and the link to its rules (2026-09-30, UX audit
 * P0-12). The link opens the same box the game's menu does, on this mode's tab.
 */
@Composable
private fun ModeBlurb(rom: RomKind, mode: Ruleset) {
    var showRules by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            RulesetCatalog.modeLine(mode.key, rom.isNatDex, rom.family),
            style = MaterialTheme.typography.bodyMedium,
            color = Shell.textOnNight,
        )
        RulesLink(mode.label) { showRules = true }
    }
    if (showRules) RulesDialog(family = rom.family, mode = mode.key, natDex = rom.isNatDex, kind = rom, onDismiss = { showRules = false })
}

/** A small underlined link under a line of text, with a 48dp target: Home's link (HomeMenu.HomeLink), left-aligned. */
@Composable
private fun RulesLink(modeLabel: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Shell.touchTarget)
            .clip(RoundedCornerShape(Shell.controlRadius))
            .clickable(role = Role.Button, onClickLabel = "Read the rules for $modeLabel", onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            RunCopy.READ_THE_RULES,
            style = MaterialTheme.typography.bodyMedium,
            color = Shell.accentOnNight,
            textDecoration = Shell.linkDecoration,
        )
    }
}

/** The empty game list (2026-09-30, UX audit P0-13): which case it is, and the button for the next step. */
@Composable
private fun NoGamesCard(libraryFiles: Int, onAddGame: () -> Unit) {
    val (headline, detail) = RunCopy.noGames(libraryFiles)
    EmptyState(headline, detail, actionLabel = RunCopy.ADD_A_GAME, onAction = onAddGame)
}

/**
 * What the Kaizo IronMON screen says about attempts and empty games, in one place so the copy rules and the numbers
 * are tested (2026-09-30, UX audit P0-13, P1). An attempt is numbered the way PrepStore counts: the run in play is
 * attempt N, and the one started next is N + 1 (PrepStore.bumpAttempt).
 */
internal object RunCopy {
    const val READ_THE_RULES = "Read all the rules"

    /**
     * Under "Startup favorites": the Favorites Clause lets a player take a favorite offered as a starter, within the
     * mode's own limits (Kaizo's legendary favorites under 600 BST, Survival's none and none at 580 or more, Super
     * Kaizo's none, Standard's one legendary). The line said only the first half (IronMON rules check R17, 2026-09-30).
     */
    const val FAVORITES_LINE = "If one is offered as a starter, the rules let you take it, within your mode's legendary and BST limits."
    /** After the favorites hint on a Game Boy Advance game, in any mode but Journey (FavoriteBall). */
    const val FAVORITE_BALL = "When the starters are offered, the tracker names the ball one is in, if your mode allows that favorite."
    const val ADD_A_GAME = "Add a game"
    const val CANCEL = "Cancel"

    /** What a button that starts the next attempt says: the number the new run will get. */
    fun start(next: Int) = "Start attempt $next"

    /** What the way back into the run in play says. */
    fun continueRun(attempt: Int) = "Continue attempt $attempt"

    /** The same for a randomized Nuzlocke, which counts no attempt (PrepStore.installRun). */
    const val CONTINUE_NUZLOCKE = "Continue the Nuzlocke"

    /** The run a new attempt ends: its number and its game, and whether it is a Nuzlocke, which has no number. */
    class EndingRun(val attempt: Int, val game: String, val nuzlocke: Boolean = false)

    /**
     * The question before a new attempt ends the run in play: "End attempt 14 and start attempt 15 on <game>, <mode>?".
     * The game is named once when the run and the new attempt are the same game, and twice when a run of another game
     * is the one that ends. [ending] is null when a run file is there and the last run says nothing about it.
     */
    fun confirmNewAttempt(ending: EndingRun?, next: Int, game: String, mode: String?): String {
        val target = if (mode == null) game else "$game, $mode"
        return when {
            ending == null -> "End the current run and start attempt $next on $target?"
            ending.nuzlocke -> "End the Nuzlocke on ${ending.game} and start attempt $next on $target?"
            ending.game == game -> "End attempt ${ending.attempt} and start attempt $next on $target?"
            else -> "End attempt ${ending.attempt} on ${ending.game} and start attempt $next on $target?"
        }
    }

    /** The mode as the confirm names it: its label, else the settings file's own name. */
    fun modeName(label: String?, file: File): String = label ?: file.name.removeSuffix(".rnqs").removeSuffix(".RNQS")

    /** The empty game list: nothing added yet, or files added that the tracker cannot read (headline to detail). */
    fun noGames(libraryFiles: Int): Pair<String, String> =
        if (libraryFiles <= 0) "No games yet." to "Add a Pok\u00e9mon game you own. It shows here once the tracker can read it."
        else "Nothing here is ready for Kaizo IronMON." to
            "Your library has $libraryFiles ${if (libraryFiles == 1) "file" else "files"} the tracker cannot read."
}

/**
 * The guard that stops a crashed intro (2026-09-27, audit), and what it says: a game and a mode must be picked, and a
 * mode for the standard Pokedex must not meet the Nat. Dex version of a game, or the reverse. The words were
 * rewritten 2026-09-30 (UX audit): they named "a settings file" and "a ROM", and pointed nowhere.
 */
internal object RunPairing {
    const val NO_GAME = "Add a game first."
    const val NO_MODE = "Pick a mode first."
    const val NAT_DEX_GAME = "That mode is for the standard Pok\u00e9dex, and this game is the Nat. Dex version. Pick a Nat. Dex mode."
    const val STANDARD_GAME = "That mode needs the Nat. Dex version of this game. Pick a standard mode, or make the Nat. Dex version in Library, Patched versions."

    fun problem(rom: Pair<RomKind, File>?, settings: File?): String? {
        val game = rom?.first ?: return NO_GAME
        val file = settings ?: return NO_MODE
        // The file's sidecar counts too: a preset saved under a plain name
        // keeps its Nat. Dex flag there (2026-09-27, audit).
        return when {
            RnqsInfo.of(file).natDex == game.isNatDex -> null
            game.isNatDex -> NAT_DEX_GAME
            else -> STANDARD_GAME
        }
    }
}

/** The game list as the screen draws it (2026-09-30, UX audit P1). */
internal object RunGames {
    /**
     * [games] with the one named [id] first and the rest as they were, so the screen opens with the selected game in
     * view: with seven games it was the last card, below the fold. Kotlin's sort is stable.
     */
    fun selectedFirst(games: List<Pair<RomKind, File>>, id: String?): List<Pair<RomKind, File>> =
        if (id == null) games else games.sortedBy { if (it.first.id == id) 0 else 1 }
}

/**
 * Which settings file each game last had picked on the Kaizo IronMON screen (2026-09-30, UX audit P0-12), so that
 * switching games brings back that game's own mode, and Kaizo where it has none (RulesetCatalog.openingFile). It is
 * kept here and not in PrepStore: it is a habit of this screen, not part of a run. One line per game, the game's id,
 * a tab, the file's name, in a file directly in the files folder like the welcome's flag: not under prep/, so the
 * backup leaves it out, and a phone restored from a backup opens each game on Kaizo again, which does no harm. A
 * line that does not read is skipped, and a write that fails leaves the old file as it was.
 */
internal class RunModeMemory(private val file: File) {

    private fun read(): Map<String, String> {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) out[parts[0]] = parts[1]
        }
        return out
    }

    /** The file name last picked for [romId], or null. */
    fun get(romId: String): String? = read()[romId]

    /** Keeps [settingsName] for [romId]. False when it cannot be kept (a tab or a line break in either) or the disk refused. */
    fun set(romId: String, settingsName: String): Boolean {
        fun keepable(s: String) = s.isNotBlank() && s.none { it == '\t' || it == '\n' || it == '\r' }
        if (!keepable(romId) || !keepable(settingsName)) return false
        val all = LinkedHashMap(read())
        if (all[romId] == settingsName) return true
        all[romId] = settingsName
        return SafeWrite.text(file, all.entries.joinToString("") { (k, v) -> k + "\t" + v + "\n" })
    }

    companion object {
        const val FILE = "run-modes.txt"

        fun of(filesDir: File) = RunModeMemory(File(filesDir, FILE))
    }
}
