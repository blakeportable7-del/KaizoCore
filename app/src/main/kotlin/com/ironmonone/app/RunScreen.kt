package com.ironmonone.app

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

/**
 * The loop: pick prepared ROM, pick settings (Kaizo default), Randomize, export,
 * play in ironmon_emu. New Run = tap again; the previous run is kept.
 *
 * Engine name is always visible (brief section 6), and a vanilla settings file on a
 * Nat. Dex ROM is refused BEFORE the engine sees it.
 */
@Composable
fun RunScreen(
    modifier: Modifier = Modifier,
    /** The preset to edit, and the generation of the ROM it will be run on. */
    onEdit: (File, String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }
    val scope = rememberCoroutineScope()
    val rng = remember { SecureRandom() }

    // First run: put the bundled presets on disk before anything reads the list,
    // so the picker and the editor are usable with no import step.
    remember { store.seedBundledPresets(context) }

    var refresh by remember { mutableIntStateOf(0) }
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
                    val i = RnqsInfo.parse(it.name); i.ruleset == "kaizo" && i.natDex
                } ?: settingsList.firstOrNull())
    }
    var confirmNewRun by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var lastSeed by remember { mutableStateOf<Long?>(null) }

    fun say(t: String, err: Boolean = false) { status = t; statusIsError = err }

    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var count = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        val b = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                        store.importSettings(context.displayNameOf(uri), b)
                        count++
                    }
                }
            }
            refresh++
            say("$count settings file(s) imported.")
            busy = false
        }
    }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!
                        .use { it.write(store.currentRun.readBytes()) }
                }
            }.onSuccess { say("Exported. Open it in ironmon_emu and play.") }
                .onFailure { say("Export failed: ${it.message}", true) }
            busy = false
        }
    }

    /** The one guard that stops a crashed intro. */
    fun pairingProblem(): String? {
        val rom = selectedRom ?: return "Prepare a ROM first (Prepare tab)."
        val s = selectedSettings ?: return "Import a settings file first."
        val info = RnqsInfo.parse(s.name)
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

    var phase by remember { mutableStateOf(RunPhase.ROTATING) }

    fun newRun() {
        val rom = selectedRom ?: return
        val s = selectedSettings ?: return
        pairingProblem()?.let { say(it, true); return }
        busy = true; status = null
        phase = RunPhase.ROTATING
        scope.launch {
            val seed = rng.nextLong()
            runCatching {
                withContext(Dispatchers.IO) {
                    store.rotateRuns(rom.first)
                    // The engine has no progress callback, so these three are
                    // the only honest phases available: the steps the caller
                    // actually performs. Nothing pretends to know how far
                    // through randomize() we are.
                    phase = RunPhase.RANDOMIZING
                    val dest = store.currentRunFor(rom.first)
                    // Engine is chosen by the ROM, never by the user: NatDex ROMs get
                    // the fork, vanilla ROMs get ZX 4.6.1 (which also handles NDS
                    // Gen 4). Cross-wiring is impossible.
                    Randomizers.randomize(rom.first, rom.second, s, dest, seed, secondPass = store.secondPassSettings(rom.first))
                }
            }.onSuccess {
                phase = RunPhase.FINISHING
                lastSeed = it.seed
                store.saveLastRun(rom.first.id, s.name)
                store.saveLastSeed(it.seed)
                // A fresh seed is what the player wants to play next, even
                // if a library ROM was open before.
                store.library.selectRun()
                // Name the game explicitly rather than leaning on saveLastRun
                // having already run: this counter is per game and must not
                // depend on the order of the two lines above it.
                store.bumpAttempt(rom.first.id)
                // Marks, notes and route sightings describe the OLD seed's
                // randomization; carrying them into the new run is actively
                // misleading. The Play screen's own NEW RUN already clears
                // them; this path forgot to.
                store.clearRunNotes()
                say(
                    "New run ready (seed %016x). Play tab, or NEW RUN there for the next."
                        .format(it.seed)
                )
            }.onFailure { say(it.message ?: "Randomization failed.", true) }
            busy = false
        }
    }

    // Scrolling content ABOVE, fixed footer BELOW. The whole screen used to be
    // one scrolling column, which put NEW RUN under the nav bar until you
    // swiped and put its result off-screen entirely.
    Column(modifier.fillMaxSize()) {
      Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
      ) {
        // The engine follows the game (Randomizers.randomize): the Nat. Dex fork for a
        // Nat. Dex build, ZX for everything else. With no game picked there is no engine to name.
        val engineName = when (selectedRom?.first?.isNatDex) {
            null -> "chosen by the game you pick"
            true -> NatDexEngine.DISPLAY_NAME
            false -> ZxEngine.DISPLAY_NAME
        }
        Gen3Box(Modifier.fillMaxWidth()) {
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
                Text(
                    "ATTEMPT ${store.attempt(rom.id)}",
                    fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont,
                    fontSize = 14.sp,
                    color = Shell.inkOnPaper,
                )
                lastSeed?.let {
                    Text(
                        "seed %016x".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ShellDivider()
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Engine: $engineName",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Gen3.MenuBlue,
        )
        Spacer(Modifier.height(12.dp))

        // The PC tracker's startup favorites: three Pokemon it shows on the
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) favActive = i },
                    singleLine = true,
                    isError = !known,
                    placeholder = { Text("Favorite ${i + 1}") },
                    textStyle = MaterialTheme.typography.bodySmall,
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
                    com.ironmonone.app.gen3.Gen3Button(name.uppercase()) {
                        favSlots = favSlots.toMutableList().also { it[favActive] = name }
                        Favorites.save(store, favRomId, favSlots)
                        favActive = -1
                    }
                }
            }
        }
        Text(
            if (favSlots.all { it.isBlank() || (Favorites.idOf(it)?.let { id -> id <= favMax } == true) }) (if (favCount > 3) "The DS tracker keeps $favCount and rotates them on its title screen." else "Shown on the tracker before your first Pokemon, as the PC tracker's startup screen shows them.")
            else "A name in red is not a Pokemon this game has.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
        )
        Spacer(Modifier.height(12.dp))

        Text("ROM", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (preparedList.isEmpty()) {
            Text(
                "Nothing prepared yet. Start on the Prepare tab.",
                style = MaterialTheme.typography.bodySmall,
                color = Shell.inkOnPaper,
            )
        }
        preparedList.forEach { pair ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedRom = pair },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShellRadio(selectedRom?.first?.id == pair.first.id)
                Spacer(Modifier.width(10.dp))
                Text(pair.first.displayName, style = MaterialTheme.typography.bodyMedium)
            }
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
            Spacer(Modifier.height(12.dp))
            Text("Mode", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ShellSegmented(
                values = modes.map { it.key },
                selected = RulesetCatalog.modeOf(modes, selectedSettings)?.key ?: "",
                label = { k -> modes.first { it.key == k }.label },
                onSelect = { k -> selectedSettings = modes.first { it.key == k }.preset },
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Gen3Button("IMPORT", enabled = !busy) { importSettings.launch(arrayOf("*/*")) }
            Spacer(Modifier.width(8.dp))
            Gen3Button("EDIT", enabled = !busy && selectedSettings != null) {
                selectedSettings?.let {
                    onEdit(it, selectedRom?.first?.generation?.name)
                }
            }
        }
        val labels = remember(settingsList) {
            RnqsInfo.displayLabels(settingsList.map { it.name })
        }
        // With every game's presets bundled the full list is 39 rows. Show the
        // selected ROM's family plus anything untagged (an imported custom
        // file); the Mode row above is the normal way to pick.
        val visibleSettings = remember(selectedRom, settingsList) {
            val rom = selectedRom?.first
            // Blake, 2026-09-07: only the loaded game's files, never all of them.
            if (rom == null) emptyList()
            else settingsList.filter { f ->
                RnqsInfo.parse(f.name).gameTag == null || RulesetCatalog.isCompatible(rom, f)
            }
        }
        if (selectedRom == null) Text("Pick a ROM above to see its settings files.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        visibleSettings.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedSettings = f },
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

        Spacer(Modifier.height(16.dp))
        // The primary action and its outcome now live in the sticky footer at
        // the bottom of this screen, where they cannot scroll out of reach.

        if (store.currentRun.exists()) {
            Spacer(Modifier.height(8.dp))
            Gen3Button("EXPORT CURRENT RUN", enabled = !busy) {
                export.launch("IronMonOne_CurrentRun." + store.currentRun.extension)
            }
        }
            }
        }

      }

      // ---- Sticky footer: the action, its progress, and its outcome -------
      Column(Modifier.fillMaxWidth().background(Shell.night).padding(10.dp)) {
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
              if (store.currentRun.exists()) "NEW RUN" else "RANDOMIZE",
              enabled = !busy && preparedList.isNotEmpty(),
              accent = true,
              // Rolling a new seed ends the current run; ask first, the same
              // gate the Play screen's NEW chip has.
          ) { if (store.currentRun.exists()) confirmNewRun = true else newRun() }
      }
    }
}