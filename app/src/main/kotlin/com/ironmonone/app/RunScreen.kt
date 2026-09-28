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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import java.io.File
import java.security.SecureRandom

/**
 * The loop: pick prepared ROM, pick settings (Kaizo default), Randomize, play.
 * New Run = tap again; the previous run is kept.
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
) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }
    val rng = remember { SecureRandom() }

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
    var selectedRom by remember(refresh) {
        mutableStateOf(
            preparedList.firstOrNull { it.first.id == lastRun?.first }
                ?: preparedList.firstOrNull { it.first.isNatDex }
                ?: preparedList.firstOrNull())
    }
    var selectedSettings by remember(refresh) {
        mutableStateOf(
            settingsList.firstOrNull { it.name == lastRun?.second }
                ?: settingsList.firstOrNull {
                    val i = RnqsInfo.of(it); i.ruleset == "kaizo" && i.natDex
                } ?: settingsList.firstOrNull())
    }
    var confirmNewRun by remember { mutableStateOf(false) }

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

    /** The one guard that stops a crashed intro. */
    fun pairingProblem(): String? {
        val rom = selectedRom ?: return "Set up a game first on the Library tab."
        val s = selectedSettings ?: return "Pick a settings file first, or import one."
        // The file's sidecar counts too: a preset saved under a plain name
        // keeps its Nat. Dex flag there (2026-09-27, audit).
        val info = RnqsInfo.of(s)
        return when {
            info.natDex != rom.first.isNatDex ->
                if (rom.first.isNatDex)
                    "\"${s.name}\" is a vanilla settings file and this is a Nat. Dex ROM. " +
                        "That combination crashes the intro."
                else
                    "\"${s.name}\" is a Nat. Dex settings file and this is a vanilla ROM. " +
                        "Use the standard (non NatDex) settings."
            else -> null
        }
    }

    fun newRun() {
        // The guard first: with no settings file picked, RANDOMIZE used to
        // return before it and do nothing at all (2026-09-27, audit).
        pairingProblem()?.let { say(it, true); return }
        val rom = selectedRom ?: return
        val s = selectedSettings ?: return
        RunJob.randomize(context, rom, s, rng.nextLong())
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
                        "Attempt ${store.attempt(rom.id)}",
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
        com.ironmonone.app.gen3.Gen3Header("Game")
        Spacer(Modifier.height(4.dp))
        if (preparedList.isEmpty()) {
            Text(
                "No games yet. Add one on the Library tab.",
                style = MaterialTheme.typography.bodySmall,
                color = Shell.inkOnPaper,
            )
        }
        preparedList.forEach { pair ->
            GameCard(pair.first, selectedRom?.first?.id == pair.first.id) { selectedRom = pair }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        // MODE. The rulesets that exist for the selected ROM, derived from the
        // preset files themselves (RulesetCatalog), so importing a preset adds
        // a mode with no code change. Picking one selects its preset; the
        // Settings list below stays for choosing a specific file.
        val modes = remember(selectedRom, settingsList) {
            selectedRom?.let { RulesetCatalog.forRom(it.first, settingsList) } ?: emptyList()
        }
        // A ROM change can leave a preset from another family selected; the
        // pairing guard would refuse it at RUN. Snap to the first mode instead.
        LaunchedEffect(selectedRom) {
            val rom = selectedRom?.first ?: return@LaunchedEffect
            val cur = selectedSettings
            if (cur == null || !RulesetCatalog.isCompatible(rom, cur)) {
                modes.firstOrNull()?.let { selectedSettings = it.preset }
            }
        }
        if (modes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            com.ironmonone.app.gen3.Gen3Header("Mode")
            Spacer(Modifier.height(4.dp))
            ShellSegmented(
                values = modes.map { it.key },
                selected = RulesetCatalog.modeOf(modes, selectedSettings)?.key ?: "",
                label = { k -> modes.first { it.key == k }.label },
                onSelect = { k -> selectedSettings = modes.first { it.key == k }.preset },
            )
            Spacer(Modifier.height(4.dp))
        }
        // RUN used to open on three empty favourite boxes, the engine's name and
        // a list of raw settings file names, before the game and mode: the
        // choices every run needs came last (audit, 2026-09-27). The optional
        // parts now sit folded below, each saying what is inside.
        Spacer(Modifier.height(10.dp))
        var showFavorites by remember { mutableStateOf(false) }
        val favFilled = Favorites.slots(store, selectedRom?.first?.id, Favorites.slotCount(selectedRom?.first)).count { it.isNotBlank() }
        RunDisclosure("Startup favorites (optional)",
            if (favFilled == 0) "If one is offered as a starter, the rules let you take it." else "$favFilled set.",
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
                    val known = v.isBlank() || (Favorites.idOf(v)?.let { it <= favMax } == true)
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
            Text(
                if (favSlots.all { it.isBlank() || (Favorites.idOf(it)?.let { id -> id <= favMax } == true) }) (if (favCount > 3) "The DS tracker keeps $favCount and rotates them on its title screen." else "Shown on the tracker before your first Pokémon, as the PC tracker's startup screen shows them.")
                else "A name in red is not a Pokémon this game has.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
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
                    (i.gameTag == null && !i.secondPass && i.natDex == rom.isNatDex) || RulesetCatalog.isCompatible(rom, f)
                }
            }
            if (selectedRom == null) Text("Pick a game above to see its settings files.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            visibleSettings.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedSettings = f }
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
                          "End the current run and roll a new seed for " +
                              (selectedRom?.first?.displayName ?: "?") + "?",
                          style = MaterialTheme.typography.bodyMedium,
                          color = Shell.inkOnPaper,
                      )
                      Spacer(Modifier.height(8.dp))
                      Row {
                          Gen3Button("YES, NEW RUN", accent = true) {
                              confirmNewRun = false; newRun()
                          }
                          Spacer(Modifier.width(8.dp))
                          Gen3Button("CANCEL") { confirmNewRun = false }
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
              if (store.currentRun.exists()) "Start new run" else "Randomize",
              modifier = Modifier.fillMaxWidth(),
              enabled = !busy && preparedList.isNotEmpty(),
              accent = true,
              // Rolling a new seed ends the current run; ask first, the same
              // gate the Play screen's NEW chip has.
          ) { if (store.currentRun.exists()) confirmNewRun = true else newRun() }
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
