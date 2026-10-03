package com.ironmonone.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.engine.GameFacts
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.gen3.Gen3Header
import com.ironmonone.core.RomKind
import com.ironmonone.editor.SettingsReflector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The entry on RUN, under the Mode row (2026-09-29): opens the builder. One line in RunScreen.
 */
@Composable
fun BuildYourGameEntry(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(Shell.cardRadius)).background(Shell.paper)
            .clickable(onClick = onOpen)
            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Build your own", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(
                "Pick your starters and what the randomizer changes, in plain words.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
        }
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
            contentDescription = "Open", tint = Shell.hintOnPaper,
        )
    }
}

/** The file a plan was last saved as, and what it was saved for: any change to either makes it stale. */
private data class SavedBuild(val plan: GameBuild.Plan, val name: String, val file: File)

/**
 * A plan as text for the saved state (RC35-NOTICED N #13): the starting point's file, the starters, then one pick a line.
 * A starting point gone since is no starting point, and a line that will not read is left out.
 */
internal object PlanText {
    fun of(p: GameBuild.Plan): String = buildString {
        append("base=").append(p.base?.path.orEmpty()).append('\n')
        append("starters=").append(
            when (val s = p.starters) {
                GameBuild.Starters.Keep -> "keep"
                GameBuild.Starters.Own -> "own"
                GameBuild.Starters.Random -> "random"
                is GameBuild.Starters.Pick -> "pick:" + slotsText(s.slots)
            },
        ).append('\n')
        for ((k, v) in p.picks) append("pick=").append(k).append('=').append(v).append('\n')
    }

    fun planOf(text: String): GameBuild.Plan {
        var base: File? = null
        var starters: GameBuild.Starters = GameBuild.Starters.Keep
        val picks = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            when (key) {
                "base" -> base = File(value).takeIf { value.isNotEmpty() && it.isFile }
                "starters" -> starters = when {
                    value == "own" -> GameBuild.Starters.Own
                    value == "random" -> GameBuild.Starters.Random
                    value.startsWith("pick:") -> slotsOf(value.removePrefix("pick:"))?.let { GameBuild.Starters.Pick(it) } ?: GameBuild.Starters.Keep
                    else -> GameBuild.Starters.Keep
                }
                "pick" -> value.indexOf('=').takeIf { it > 0 }?.let { picks[value.substring(0, it)] = value.substring(it + 1) }
            }
        }
        return GameBuild.Plan(base, starters, picks)
    }

    /** Starter slots: a species number, or r for a random one. */
    fun slotsText(slots: List<Int?>): String = slots.joinToString(",") { it?.toString() ?: "r" }

    fun slotsOf(text: String): List<Int?>? =
        text.split(',').map { t -> if (t == "r") null else t.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

    val Saver = androidx.compose.runtime.saveable.Saver<GameBuild.Plan, String>(save = { of(it) }, restore = { planOf(it) })

    val SlotsSaver = androidx.compose.runtime.saveable.Saver<List<Int?>?, String>(
        save = { it?.let { s -> slotsText(s) } }, restore = { slotsOf(it) },
    )
}

private const val KEEP = "keep"
private val STEPS = listOf("Start from", "Starters", "The world", "The Pokémon", "Save and play")

/**
 * "Build your own" (2026-09-29, Blake: "a full customization game where you can choose any starter
 * you want and other changes"): five short pages that end in a settings file, saved beside the
 * others and played through the same RunJob the Run tab's button uses.
 *
 * It takes the whole Run tab while it is open (RunScreen swaps it in), so the bottom bar stays and
 * RunScreen's own listeners stay on: a finished run still opens Play. What the pages do to the
 * settings, and everything about the file, is GameBuild. The starter list comes from the game's own
 * ROM through the engine (GameFacts), so it is exactly the Pokémon that game's randomizer knows.
 * "All settings" saves first and opens the file in the editor, which is the editor's job from there.
 */
@Composable
fun BuildYourGame(
    store: PrepStore,
    rom: Pair<RomKind, File>,
    settingsList: List<File>,
    /** A run is in play: starting this game ends it, so it asks first, as the Run tab's button does. */
    hasRun: Boolean,
    onClose: () -> Unit,
    /** A file was saved: RUN re-reads its settings list and selects it. */
    onSaved: (File) -> Unit,
    onEdit: (File, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val kind = rom.first
    val cls = remember(kind.id) { GameBuild.settingsClass(kind) }
    val modes = remember(kind.id, settingsList) { RulesetCatalog.forRom(kind, settingsList) }

    // The page, the plan, the open starter slot and the name are kept with the activity (RC35-NOTICED N #13, the rest of
    // rc32 audit P2 #33): a process death while the app was in the background lost every choice.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var plan by rememberSaveable(stateSaver = PlanText.Saver) { mutableStateOf(GameBuild.Plan()) }
    var slot by rememberSaveable { mutableStateOf<Int?>(null) }
    var typedName by rememberSaveable { mutableStateOf(GameBuild.DEFAULT_NAME) }
    var saved by remember { mutableStateOf<SavedBuild?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteIsError by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var startedHere by remember { mutableStateOf(false) }
    // The picks last made, so switching to "Random" and back does not lose them.
    var pickMemory by rememberSaveable(stateSaver = PlanText.SlotsSaver) { mutableStateOf<List<Int?>?>(null) }

    // The game's own Pokémon list, read once off the phone's ROM on the IO thread.
    var facts by remember(kind.id, rom.second.path) { mutableStateOf<GameFacts.Facts?>(null) }
    var factsFailed by remember(kind.id, rom.second.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(kind.id, rom.second.path) {
        val r = withContext(Dispatchers.IO) { runCatching { GameFacts.read(kind, rom.second) } }
        facts = r.getOrNull()
        factsFailed = r.exceptionOrNull()?.let { PresetStrings.plain(it, "Could not read this game's Pokémon list") }
    }
    val f = facts

    val choices = remember(kind.id, f) { GameBuild.choices(kind, f) }
    val rows = remember(f) { f?.let { GameBuild.entries(it) } }
    val baseSettings = remember(plan.base) { plan.base?.let { b -> runCatching { GameBuild.read(cls, b) }.getOrNull() } }
    val baseLabel: String? = plan.base?.let { b ->
        modes.firstOrNull { it.preset.name == b.name }?.label ?: RnqsInfo.rulesetLabel(RnqsInfo.of(b).ruleset)
    }
    val baseRuleset = plan.base?.let { RnqsInfo.of(it).ruleset }
    val upToDate: File? = saved?.takeIf { it.plan == plan && it.name == typedName }?.file
    val busy = RunJob.busy

    fun saveNow(): File? = GameBuild.save(store, kind, plan, f, typedName).fold(
        onSuccess = { file ->
            saved = SavedBuild(plan, typedName, file)
            note = "Saved as ${file.name.removeSuffix(".rnqs")}. It is in the settings list on the Kaizo IronMON screen."; noteIsError = false
            onSaved(file)
            file
        },
        onFailure = { e ->
            note = PresetStrings.plain(e, "Could not save your game"); noteIsError = true
            null
        },
    )

    fun startNow() {
        val file = upToDate ?: saveNow() ?: return
        startedHere = true
        if (!RunJob.randomize(context, rom, file, seed = null)) { startedHere = false; RunJob.say(NewRunGuard.BUSY, true) }
    }

    fun leave() {
        if (plan.touched && saved?.plan != plan) confirmLeave = true else onClose()
    }

    BackHandler {
        when {
            slot != null -> slot = null
            step > 0 -> step -= 1
            else -> leave()
        }
    }

    if (confirmLeave) {
        ShellDialog("Leave without saving?", onDismiss = { confirmLeave = false }) {
            Text(
                "Your choices are lost if you leave now.",
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button("Stay", accent = true) { confirmLeave = false }
                Gen3Button("Leave") { confirmLeave = false; onClose() }
            }
        }
    }

    // What the last save or refusal said goes when the plan or the name changes: it was about the old one.
    LaunchedEffect(plan, typedName) { note = null }

    val openSlot = slot
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Column(modifier.fillMaxSize()) {
        if (openSlot != null && f != null && rows != null) {
            val slots = (plan.starters as? GameBuild.Starters.Pick)?.slots?.takeIf { it.size == f.starterCount } ?: f.ownStarters
            SpeciesPicker(
                title = "Starter ${openSlot + 1}",
                rows = rows,
                current = slots.getOrNull(openSlot),
                ownName = f.ownStarters.getOrNull(openSlot)?.let { n -> rows.getOrNull(n - 1)?.label },
                onPick = { n ->
                    val next = slots.toMutableList().also { if (openSlot in it.indices) it[openSlot] = n }
                    pickMemory = next
                    plan = plan.copy(starters = GameBuild.Starters.Pick(next))
                    slot = null
                },
                onBack = { slot = null },
            )
        } else {
            Header(kind, step)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp)) {
                when (step) {
                    0 -> StartPage(kind, modes, plan.base) { plan = plan.copy(base = it) }
                    1 -> StartersPage(
                        plan = plan, facts = f, failed = factsFailed, rows = rows, baseLabel = baseLabel, natDex = kind.isNatDex,
                        baseText = baseSettings?.let { SettingsReflector.valueLabel("startersMod", GameBuild.get(cls, it, "startersMod")) },
                        onMode = { id ->
                            plan = plan.copy(
                                starters = when (id) {
                                    "own" -> GameBuild.Starters.Own
                                    "random" -> GameBuild.Starters.Random
                                    // Not before the game's list has been read: without it there are no slots to fill.
                                    "pick" -> if (f == null) plan.starters else GameBuild.Starters.Pick(pickMemory?.takeIf { it.size == f.starterCount } ?: f.ownStarters)
                                    else -> GameBuild.Starters.Keep
                                },
                            )
                        },
                        onSlot = { slot = it },
                    )
                    2, 3 -> {
                        val page = if (step == 2) GameBuild.Page.WORLD else GameBuild.Page.POKEMON
                        Text(
                            if (step == 2) "What you meet, what trainers send out, and what lies on the ground."
                            else "What each Pokémon is, and what it can do.",
                            style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight,
                        )
                        Spacer(Modifier.height(10.dp))
                        choices.filter { it.page == page }.forEach { c ->
                            ChoiceCard(
                                choice = c,
                                selected = plan.picks[c.id] ?: if (plan.base != null) KEEP else c.picks.first().id,
                                baseLabel = baseLabel,
                                baseText = baseSettings?.let { GameBuild.currentText(cls, it, c) },
                                onPick = { id -> plan = plan.copy(picks = if (id == KEEP) plan.picks - c.id else plan.picks + (c.id to id)) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    else -> SavePage(
                        kind = kind, baseRuleset = baseRuleset, name = typedName, onName = { typedName = it },
                        lines = GameBuild.summary(kind, plan, f, baseLabel), saved = upToDate,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- footer: what just happened, and the way on ------------------------------------
            Column(Modifier.fillMaxWidth().background(Shell.night).padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (startedHere && busy) {
                    ProgressPanel(RunJob.phase)
                    Spacer(Modifier.height(8.dp))
                }
                if (confirmStart) {
                    Gen3Box(Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                "End the current run and start this game on ${kind.displayName}?",
                                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Gen3Button("YES, START IT", accent = true) { confirmStart = false; startNow() }
                                Spacer(Modifier.width(8.dp))
                                Gen3Button("CANCEL") { confirmStart = false }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (startedHere) RunJob.status?.let { StatusBanner(it, RunJob.statusIsError); Spacer(Modifier.height(8.dp)) }
                if (step == STEPS.lastIndex) note?.let { StatusBanner(it, noteIsError); Spacer(Modifier.height(8.dp)) }

                if (step < STEPS.lastIndex) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gen3Button(if (step == 0) "Cancel" else "Back") { if (step == 0) leave() else step -= 1 }
                        Spacer(Modifier.weight(1f))
                        Gen3Button("Next", accent = true) { step += 1 }
                    }
                } else {
                    Gen3Button(
                        "Start this game", modifier = Modifier.fillMaxWidth(), accent = true,
                        enabled = !busy,
                    ) { if (hasRun) confirmStart = true else startNow() }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gen3Button("Back") { step -= 1 }
                        Spacer(Modifier.width(8.dp))
                        Gen3Button("Save", enabled = !busy && upToDate == null) { saveNow() }
                        Spacer(Modifier.width(8.dp))
                        Gen3Button("All settings", enabled = !busy) {
                            val file = upToDate ?: saveNow()
                            if (file != null) {
                                // The editor names a saved copy after this game when a name has no tag (PresetStrings).
                                PresetStrings.targetFamily = kind.family
                                PresetStrings.targetNatDex = kind.isNatDex
                                onEdit(file, kind.generation.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- pieces

@Composable
private fun Header(kind: RomKind, step: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Build your own", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
                Text(STEPS[step], fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Shell.textOnNight)
            }
            Text("Step ${step + 1} of ${STEPS.size}", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            STEPS.indices.forEach { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) Shell.accent else Shell.frame))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(kind.displayName, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
    }
}

/** A row with a radio, a title and one line, the whole row the target. */
@Composable
private fun RadioRow(title: String, detail: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = Shell.touchTarget).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellRadio(on)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        }
    }
}

@Composable
private fun StartPage(kind: RomKind, modes: List<Ruleset>, base: File?, onBase: (File?) -> Unit) {
    Text(
        "Begin with the game as it is, or with one of its modes, and change what you like.",
        style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight,
    )
    Spacer(Modifier.height(10.dp))
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            RadioRow("The game as it is", "Nothing is randomized until you say so on the next pages.", base == null) { onBase(null) }
            modes.forEach { m ->
                // "Official" was said of every mode, Super Kaizo and the community modes too (IronMON rules check R18).
                RadioRow(m.label, "The ${m.label} settings that come with KaizoCore. Whatever you leave alone stays as they have it.", base?.name == m.preset.name) { onBase(m.preset) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Engine: " + if (kind.isNatDex) NatDexEngine.DISPLAY_NAME else ZxEngine.DISPLAY_NAME,
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
    )
}

@Composable
private fun StartersPage(
    plan: GameBuild.Plan,
    facts: GameFacts.Facts?,
    failed: String?,
    rows: List<GameBuild.Entry>?,
    baseLabel: String?,
    natDex: Boolean,
    baseText: String?,
    onMode: (String) -> Unit,
    onSlot: (Int) -> Unit,
) {
    val mode = when (plan.starters) {
        GameBuild.Starters.Keep -> if (baseLabel != null) KEEP else "own"
        GameBuild.Starters.Own -> "own"
        GameBuild.Starters.Random -> "random"
        is GameBuild.Starters.Pick -> "pick"
    }
    val values = (if (baseLabel != null) listOf(KEEP) else emptyList()) + listOf("own", "random", "pick")
    fun nameOf(n: Int?) = if (n == null) "Random" else rows?.getOrNull(n - 1)?.label ?: "#$n"
    val ownText = facts?.ownStarters?.joinToString(", ") { nameOf(it) }

    Text(
        "Choose the Pokémon you start with, or leave it to the randomizer.",
        style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight,
    )
    Spacer(Modifier.height(10.dp))
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            ShellSegmented(
                values = values, selected = mode,
                label = { when (it) { KEEP -> "As $baseLabel"; "own" -> "The game's own"; "random" -> "Random"; else -> "My picks" } },
                onSelect = onMode,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    KEEP -> "$baseLabel's starters: ${baseText?.lowercase() ?: "as it has them"}."
                    "own" -> "The game's own starters" + (ownText?.let { ": $it" } ?: "") + "."
                    "random" -> when (facts?.starterCount) { 1 -> "One random Pokémon."; 2 -> "Two random Pokémon, different from each other."; else -> "Three random Pokémon, different from each other." }
                    else -> "Tap a slot to pick any Pokémon for it, or Random. A slot you leave alone keeps the game's own."
                },
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
        }
    }
    if (facts == null) {
        Spacer(Modifier.height(10.dp))
        if (failed == null) {
            Text("Reading this game's Pokémon list.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
            ShellBusy()
        } else StatusBanner("$failed Picking starters is not possible, but you can build the rest.", true)
    }
    if (mode == "pick" && facts != null) {
        Spacer(Modifier.height(10.dp))
        val slots = (plan.starters as? GameBuild.Starters.Pick)?.slots?.takeIf { it.size == facts.starterCount } ?: facts.ownStarters
        slots.forEachIndexed { i, n ->
            Gen3Box(Modifier.fillMaxWidth().clickable { onSlot(i) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeciesIcon(rows?.getOrNull((n ?: 0) - 1)?.iconId)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Starter ${i + 1}", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                        Text(nameOf(n), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                        facts.ownStarters.getOrNull(i)?.let {
                            Text("The game's own: ${nameOf(it)}", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                        }
                    }
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Pick", tint = Shell.hintOnPaper,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "The last Pokémon in the game's list, ${nameOf(facts.lastNumber)}, cannot be a starter: the randomizer swaps it for the game's own.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
        )
        if (natDex) {
            Spacer(Modifier.height(4.dp))
            Text(
                "This Nat. Dex build lists ${facts.species.size} entries, alternate forms included, and takes custom starters like any other game.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    choice: GameBuild.Choice,
    selected: String,
    baseLabel: String?,
    baseText: String?,
    onPick: (String) -> Unit,
) {
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(choice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text(choice.blurb, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(8.dp))
            ShellSegmented(
                values = (if (baseLabel != null) listOf(KEEP) else emptyList()) + choice.picks.map { it.id },
                selected = selected,
                label = { if (it == KEEP) "As $baseLabel" else choice.pick(it).label },
                onSelect = onPick,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (selected == KEEP) "$baseLabel has: ${baseText?.lowercase() ?: "its own"}." else choice.pick(selected).detail,
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
        }
    }
}

@Composable
private fun SavePage(
    kind: RomKind,
    baseRuleset: String?,
    name: String,
    onName: (String) -> Unit,
    lines: List<String>,
    saved: File?,
) {
    val focus = LocalFocusManager.current
    Text(
        "Name it, then save it or play it. A saved game shows in the settings list on the Kaizo IronMON screen.",
        style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight,
    )
    Spacer(Modifier.height(10.dp))
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text("Name", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().background(Shell.frame).padding(2.dp).background(Shell.paper).padding(horizontal = 10.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = name, onValueChange = onName, singleLine = true,
                    textStyle = TextStyle(color = Shell.inkOnPaper, fontSize = 15.sp),
                    cursorBrush = SolidColor(Shell.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            val file = GameBuild.fileName(kind, baseRuleset, name)
            Text(
                when {
                    file == null -> "Give your game a name."
                    saved != null -> "Saved as ${file.removeSuffix(".rnqs")}."
                    else -> "File: ${file.removeSuffix(".rnqs")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (file == null) Shell.dangerOnPaper else Shell.hintOnPaper,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Gen3Header("What it does")
    Gen3Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper) }
        }
    }
    // The passes the official rules add (ExtraPasses) are off for a file that is not an official preset.
    val pass = when {
        ExtraPasses.takesPart2(kind) -> "second pass (PART 2)"
        ExtraPasses.prePassName(kind) != null -> "official 60% levels"
        else -> null
    }
    if (pass != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            "The Kaizo IronMON screen has a switch for this game's $pass. It stays off for your own settings until you switch it on.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
        )
    }
}

/** One 36dp sprite from the bundled pack, or an empty square where there is none. */
@Composable
private fun SpeciesIcon(iconId: Int?) {
    val context = LocalContext.current
    val bmp = remember(iconId) { iconId?.let { PcAssets.gbaSprite(context, it) } }
    if (bmp != null) Image(bmp, null, Modifier.size(36.dp), filterQuality = FilterQuality.None)
    else Spacer(Modifier.size(36.dp))
}

/**
 * Every Pokémon the game has, searchable by name or number, with "Random" first. A species the
 * engine cannot take as a starter is listed and greyed with the reason, not hidden.
 */
@Composable
private fun SpeciesPicker(
    title: String,
    rows: List<GameBuild.Entry>,
    current: Int?,
    ownName: String?,
    onPick: (Int?) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(rows, query) { GameBuild.search(rows, query) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Gen3Button("Back") { onBack() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Pick for", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Shell.textOnNight)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Shell.frame).padding(2.dp).background(Shell.paper).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (query.isEmpty()) Text("Search by name or number", style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Shell.inkOnPaper, fontSize = 15.sp),
                    cursorBrush = SolidColor(Shell.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(Shell.touchTarget).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Clear search", tint = Shell.hintOnPaper,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "random") {
                PickRow(
                    icon = null, label = "Random",
                    detail = "A different Pokémon each time" + (ownName?.let { ". The game's own is $it." } ?: "."),
                    number = null, on = current == null, blocked = null,
                ) { onPick(null) }
            }
            if (shown.isEmpty()) item(key = "none") { Text("No Pokémon matches that.", style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnNight, modifier = Modifier.padding(vertical = 12.dp)) }
            items(shown, key = { it.number }) { e ->
                PickRow(icon = e.iconId, label = e.label, detail = e.blocked, number = e.number, on = current == e.number, blocked = e.blocked) { onPick(e.number) }
            }
        }
    }
}

@Composable
private fun PickRow(icon: Int?, label: String, detail: String?, number: Int?, on: Boolean, blocked: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Shell.controlRadius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) Shell.raised else Shell.paper)
            .then(if (blocked == null) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (blocked == null) 1f else 0.5f)
            .heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesIcon(icon)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = Shell.inkOnPaper)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        }
        if (number != null) Text("#$number", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        if (on) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.CheckCircle,
                contentDescription = "Selected", tint = Shell.accentOnNight,
            )
        }
    }
}
