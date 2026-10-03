package com.ironmonone.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dabomstew.pkrandom.Settings as NdSettings
import com.dabomstew.pkrandomzx.Settings as ZxSettings
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.editor.Option
import com.ironmonone.editor.Section
import com.ironmonone.editor.SettingsReflector
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The randomizer settings editor.
 *
 * ## What this is for
 *
 * Nobody authors an IronMON settings file from nothing: the published presets
 * are the base and your file is a DEVIATION from one. So the editor's job is
 * to make deviations easy to make, easy to see, and easy to undo - which is
 * why the load snapshots the original and every row can say whether it has
 * moved and put itself back.
 *
 * ## Order comes from the desktop randomizer, not from reflection
 *
 * Reflection returns fields in an arbitrary order, which used to render
 * dependent controls ABOVE the master enum that decides whether they do
 * anything. `SettingsReflector` now sorts by the desktop UPR's own control
 * order (see tools/extract_upr_order.py), so a section reads master-first the
 * way the reference does.
 */
@Composable
fun EditorScreen(
    file: File,
    /** e.g. "GEN3" - lets the editor hide settings the game cannot use. */
    generation: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }

    // A preset belongs to ONE engine: vanilla files are unreadable by the Nat.
    // Dex fork and vice versa (that is the "settings file is too old / newer
    // version" pair). Try both and keep whichever class read it.
    //
    // Loaded TWICE on purpose: `working` is edited, `original` stays as the
    // file was so every row can be compared against it. Without a baseline the
    // only change indicator possible is a counter, which tells you that
    // something moved but never what.
    //
    // The edited copy is kept with the activity as its settings string (EditorCopies): Android ending the app while it
    // was in the background reopened the editor on the file with every unsaved edit gone (RC35-NOTICED N #12).
    val loaded: Triple<Any, Any, Class<*>>? =
        rememberSaveable(file, stateSaver = EditorCopies.saver(file)) { mutableStateOf(EditorCopies.load(file)) }.value

    if (loaded == null) {
        androidx.activity.compose.BackHandler { onClose() }
        ScreenBackground(null) {
            Column(modifier.fillMaxSize().padding(16.dp)) {
                EmptyState(
                    "Could not read that preset.",
                    // MaxDex's file is read by its own randomizer only, which the editor does not edit in this first version.
                    if (RnqsInfo.of(file).maxDex) "\"${file.name}\" is a MaxDex settings file. The editor does not change MaxDex files yet; " +
                        "the file runs as Trip made it."
                    else "\"${file.name}\" is not a settings file either engine " +
                        "recognizes. It may be from a newer randomizer.",
                )
                Spacer(Modifier.height(12.dp))
                Gen3Button("BACK") { onClose() }
            }
        }
        return
    }
    val (settings, original, settingsClass) = loaded

    // Drop what this generation cannot use at all. The editor was offering
    // "Abilities follow mega evolutions" while editing a FireRed preset - a
    // Gen 3 game with no mega evolutions - so an option that does nothing
    // looked exactly like a choice.
    val sections = remember(settingsClass, generation) {
        SettingsReflector.bySection(settingsClass)
            .mapValues { (_, v) ->
                v.filterNot { SettingsReflector.unsupportedIn(it.id, generation) }
            }
    }
    val allOptions = remember(sections) { sections.values.flatten() }
    var openSection by remember { mutableStateOf<Section?>(null) }
    var edits by remember { mutableIntStateOf(0) }   // bump to recompose values
    var saveDialog by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    // A pasted string that passed validation, waiting for its name: the text
    // and whether the Nat. Dex fork owns it.
    var pendingPaste by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val fileInfo = remember(file) { RnqsInfo.of(file) }
    val natDexFile = PresetStrings.isNatDexClass(settingsClass)
    var status by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    /** Current value of an option, as a comparable string. */
    fun valueOf(opt: Option, on: Any): String = when (opt) {
        is Option.Bool -> opt.get(on).toString()
        is Option.IntValue -> opt.get(on).toString()
        is Option.Choice -> opt.get(on)
    }

    fun changed(opt: Option): Boolean {
        @Suppress("UNUSED_EXPRESSION") edits      // recompose on every edit
        return valueOf(opt, settings) != valueOf(opt, original)
    }

    fun revert(opt: Option) {
        when (opt) {
            is Option.Bool -> opt.set(settings, opt.get(original))
            is Option.IntValue -> opt.set(settings, opt.get(original))
            is Option.Choice -> opt.set(settings, opt.get(original))
        }
        edits++
    }

    /**
     * Why a row is dead, or null if it is live.
     *
     * The desktop greys a dependent control out when its master makes it
     * meaningless - "Ban bad abilities" does nothing while "Abilities mod" is
     * Unchanged. This editor used to accept that edit happily and write a
     * setting the engine then ignored.
     */
    fun gateReason(opt: Option): String? {
        @Suppress("UNUSED_EXPRESSION") edits
        // Every gate a field has, not just one: balanceShakingGrass is dead
        // under Area AND Global mapping (2026-09-27, audit).
        for (gate in SettingsReflector.gatesFor(opt.id)) {
            val master = allOptions.firstOrNull { it.id == gate.whenField } ?: continue
            if (valueOf(master, settings) != gate.equalsValue) continue
            return "No effect while " + master.label + " is " +
                SettingsReflector.valueLabel(master.id, gate.equalsValue)
        }
        return null
    }

    val changedCount = allOptions.count { changed(it) }
    // Leaving with changes that were never saved asks first. The phone's Back
    // used to leave the editor (and the app) with no word, losing every edit
    // (audit, 2026-09-27). What was last saved is kept as its settings string.
    var savedString by rememberSaveable { mutableStateOf<String?>(null) }
    val dirty = changedCount > 0 && runCatching { settings.toString() }.getOrNull() != savedString
    var confirmLeave by remember { mutableStateOf(false) }
    fun leave() { if (dirty) confirmLeave = true else onClose() }
    androidx.activity.compose.BackHandler { leave() }
    // Declared AFTER the leave handler so it wins while there is a search:
    // Back clears the search first, as every search field does (2026-09-27, audit).
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) { query = "" }
    if (confirmLeave) {
        ShellDialog("Leave without saving?", onDismiss = { confirmLeave = false }) {
            Column {
                Text("You changed $changedCount setting(s). They are lost if you leave now.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(10.dp))
                // Two rows: three buttons in one clipped the last at phone width.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button("SAVE AS", accent = true) { confirmLeave = false; saveDialog = true }
                    Gen3Button("STAY") { confirmLeave = false }
                }
                Spacer(Modifier.height(8.dp))
                // The destructive choice reads as one: danger text, no fill.
                DangerTextButton("Leave without saving") { confirmLeave = false; onClose() }
            }
        }
    }

    ScreenBackground(null) {
        Column(modifier.fillMaxSize()) {
            // ---- scrolling content ------------------------------------------
            LazyColumn(
                Modifier.weight(1f).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "head") {
                    Gen3Box(Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                file.name.removeSuffix(".rnqs"),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Shell.inkOnPaper,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // Name the engine that ACTUALLY read this file.
                                "Engine: " + (
                                    if (settingsClass == NdSettings::class.java)
                                        NatDexEngine.DISPLAY_NAME
                                    else ZxEngine.DISPLAY_NAME
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Shell.hintOnPaper,
                            )
                            Spacer(Modifier.height(10.dp))
                            // 141 options behind eight collapsed sections is
                            // not browsable. Search is how anyone finds one
                            // setting without opening all of them.
                            Row(
                                Modifier.fillMaxWidth().background(Shell.frame)
                                    .padding(2.dp).background(Shell.paper)
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search settings",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Shell.hintOnPaper,
                                        )
                                    }
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Shell.inkOnPaper, fontSize = 15.sp,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                // A way out of a search without deleting it
                                // letter by letter.
                                if (query.isNotEmpty()) {
                                    Box(
                                        Modifier.size(Shell.touchTarget).clickable { query = "" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Icon(
                                            androidx.compose.material.icons.Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = Shell.hintOnPaper,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (query.isNotBlank()) {
                    // Matches the label OR the description, so searching for
                    // "trapping" finds the option whose help text mentions it
                    // even when its label does not.
                    val q = query.trim().lowercase()
                    val hits = allOptions.filter { o ->
                        o.label.lowercase().contains(q) ||
                            SettingsReflector.helpFor(o.id)
                                ?.lowercase()?.contains(q) == true
                    }
                    if (hits.isEmpty()) {
                        item(key = "nohits") {
                            EmptyState("No match.", "Nothing matches that search.")
                        }
                    }
                    items(hits, key = { "q_" + it.id }) { opt ->
                        Gen3Box(Modifier.fillMaxWidth()) {
                            OptionRow(
                                opt = opt, settings = settings,
                                changed = changed(opt),
                                gateReason = gateReason(opt),
                                generation = generation,
                                onEdit = { edits++ }, onRevert = { revert(opt) },
                            )
                        }
                    }
                    return@LazyColumn
                }

                Section.entries.forEach { section ->
                    val opts = sections[section].orEmpty()
                    if (opts.isEmpty()) return@forEach
                    val sectionChanged = opts.count { changed(it) }
                    item(key = section.name) {
                        Gen3Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    openSection =
                                        if (openSection == section) null else section
                                }.heightIn(min = Shell.touchTarget),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    if (openSection == section) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp
                                    else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (openSection == section) "Close" else "Open",
                                    tint = Shell.hintOnPaper,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    section.title,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Shell.inkOnPaper,
                                )
                                // A section says how many of ITS rows differ, so
                                // a change is findable without opening all eight.
                                if (sectionChanged > 0) {
                                    ChangedDot()
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "$sectionChanged",
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = Shell.inkOnPaper,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    "${opts.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Shell.hintOnPaper,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                    }
                    if (openSection == section) {
                        items(opts, key = { it.id }) { opt ->
                            Gen3Box(Modifier.fillMaxWidth()) {
                                OptionRow(
                                    opt = opt,
                                    settings = settings,
                                    changed = changed(opt),
                                    gateReason = gateReason(opt),
                                    generation = generation,
                                    onEdit = { edits++ },
                                    onRevert = { revert(opt) },
                                )
                            }
                        }
                    }
                }
            }

            // ---- sticky footer ----------------------------------------------
            //
            // SAVE AS and the change count are always reachable. The old row
            // put four buttons in a horizontal scroller, so the fourth was
            // invisible unless you knew to swipe it.
            Column(Modifier.fillMaxWidth().background(Shell.night).padding(10.dp)) {
                status?.let {
                    StatusBanner(it, statusError)
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gen3Button("SAVE AS", enabled = changedCount > 0, accent = true) {
                        saveDialog = true
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (changedCount == 0) "no changes"
                        else "$changedCount changed",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Shell.hintOnNight,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Gen3Button("COPY") {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        // With the version prefix, as the desktop copies it:
                        // the bare body was refused by our own PASTE (2026-09-27, audit).
                        clip.setPrimaryClip(
                            ClipData.newPlainText("settings", PresetStrings.copyString(settings, settingsClass)),
                        )
                        status = "Settings string copied."; statusError = false
                    }
                    Spacer(Modifier.width(6.dp))
                    Gen3Button("PASTE") { pasteDialog = true }
                    Spacer(Modifier.width(6.dp))
                    Gen3Button("BACK") { leave() }
                }
            }
        }
    }

    if (saveDialog) {
        NameDialog(
            title = "Save preset as",
            // The game tag, NatDex and ruleset stay in the suggested name: the
            // Run tab pairs a preset with a game by them (2026-09-27, audit).
            initial = PresetStrings.suggestedName(fileInfo, natDexFile, "my edit"),
            onCancel = { saveDialog = false },
        ) { name ->
            saveDialog = false
            runCatching {
                // Both engines declare write(FileOutputStream), not the
                // OutputStream supertype, so the lookup must use the exact
                // type. Write to a temp file first: a failed save used to
                // leave an empty .rnqs behind that showed up as a broken
                // preset.
                val tmp = File.createTempFile("preset", ".rnqs", store.cacheDirFor())
                try {
                    FileOutputStream(tmp).use { out ->
                        settingsClass.getMethod("write", FileOutputStream::class.java)
                            .invoke(settings, out)
                    }
                    // Read the file BACK and compare before trusting the save.
                    // A pretty editor that emits a preset the engine cannot
                    // read is worse than an ugly one that emits a good file.
                    //
                    // BOTH lookups must use the CONCRETE stream type. The
                    // engines declare write(FileOutputStream) and
                    // read(FileInputStream), not the supertypes - asking for
                    // read(InputStream) throws NoSuchMethodException, and this
                    // guard then failed every save it was meant to protect.
                    val reread = FileInputStream(tmp).use { input ->
                        settingsClass.getMethod("read", FileInputStream::class.java)
                            .invoke(null, input)
                    }
                    val lost = SettingsReflector.options(settingsClass).filter { o ->
                        valueOf(o, reread) != valueOf(o, settings)
                    }
                    check(lost.isEmpty()) {
                        "the saved file did not read back the same: " +
                            lost.take(3).joinToString { it.label }
                    }
                    // Never over another file: importSettings replaced any
                    // same-named preset, bundled ones included, without a word.
                    // addSettingsFile makes "Name (2)" instead (2026-09-27, audit).
                    val saved = store.addSettingsFile("${name.removeSuffix(".rnqs")}.rnqs", tmp.readBytes()).getOrThrow()
                    // What the preset is for, beside it, in case the name lost
                    // the tokens the Run tab pairs on.
                    RnqsInfo.writeMeta(saved, fileInfo.gameTag ?: PresetStrings.targetFamily, natDexFile, fileInfo.ruleset)
                    saved
                } finally {
                    // Always: a failed save used to leave the temp file behind.
                    tmp.delete()
                }
            }.onSuccess { saved ->
                savedString = runCatching { settings.toString() }.getOrNull()
                status = "Saved as ${saved.name.removeSuffix(".rnqs")}. Pick it in Kaizo IronMON."; statusError = false
            }.onFailure {
                status = PresetStrings.plain(it, "Could not save the preset"); statusError = true
            }
        }
    }

    if (pasteDialog) {
        NameDialog(
            title = "Paste settings string",
            initial = "",
            singleLine = false,
            onCancel = { pasteDialog = false },
        ) { text ->
            pasteDialog = false
            val natDexErr = NatDexEngine.validateSettingsString(text)
            val zxErr = ZxEngine.validateSettingsString(text)
            if (natDexErr != null && zxErr != null) {
                status = if ("newer randomizer" in natDexErr + zxErr)
                    "That settings string is from a newer randomizer than this app carries."
                else "That is not a settings string either randomizer can read."
                statusError = true
                return@NameDialog
            }
            // Both can accept an older string; then the engine of the file
            // being edited decides.
            val natDex = when {
                natDexErr == null && zxErr == null -> natDexFile
                else -> natDexErr == null
            }
            // Name it next, instead of saving every paste over "Pasted
            // preset.rnqs" with no tags the Run tab could pair on (2026-09-27, audit).
            pendingPaste = text to natDex
        }
    }

    pendingPaste?.let { (text, natDex) ->
        NameDialog(
            title = "Name the pasted preset",
            initial = PresetStrings.suggestedName(null, natDex, "pasted", family = fileInfo.gameTag),
            onCancel = { pendingPaste = null },
        ) { name ->
            pendingPaste = null
            runCatching {
                val tmp = File.createTempFile("paste", ".rnqs", store.cacheDirFor())
                try {
                    if (natDex) NatDexEngine.writeSettingsString(text, tmp)
                    else ZxEngine.writeSettingsString(text, tmp)
                    val saved = store.addSettingsFile("${name.removeSuffix(".rnqs")}.rnqs", tmp.readBytes()).getOrThrow()
                    RnqsInfo.writeMeta(saved, RnqsInfo.parse(saved.name).gameTag ?: fileInfo.gameTag ?: PresetStrings.targetFamily, natDex, null)
                    saved
                } finally {
                    tmp.delete()
                }
            }.onSuccess { saved ->
                status = "Saved as ${saved.name.removeSuffix(".rnqs")}. Pick it in Kaizo IronMON."; statusError = false
            }.onFailure {
                status = PresetStrings.plain(it, "Could not save the pasted preset"); statusError = true
            }
        }
    }
}

/**
 * The editor's two copies of a preset: the one edited, the file as it was, and the engine class that read it (a preset
 * belongs to one engine). The edited copy is kept in the activity's saved state as its settings string, which the
 * engines read back with Settings.fromString, so a process death keeps the unsaved edits (RC35-NOTICED N #12).
 */
internal object EditorCopies {
    fun load(file: File): Triple<Any, Any, Class<*>>? {
        fun read(): Pair<Any, Class<*>>? {
            runCatching { FileInputStream(file).use { NdSettings.read(it) } }
                .getOrNull()?.let { return it to NdSettings::class.java }
            runCatching { FileInputStream(file).use { ZxSettings.read(it) } }
                .getOrNull()?.let { return it to ZxSettings::class.java }
            return null
        }
        val a = read() ?: return null
        val b = read() ?: return null
        return Triple(a.first, b.first, a.second)
    }

    /**
     * The edited copy as text: its engine's class name, then its settings string, or the name alone when the copy will
     * not write one (the file is then read as it is). Null only when there is no copy.
     */
    fun save(loaded: Triple<Any, Any, Class<*>>?): String? =
        loaded?.let { (working, _, cls) -> cls.name + "\n" + (runCatching { working.toString() }.getOrNull() ?: "") }

    /**
     * [file] read again, with the edited copy put back from [saved]. The file as it is now when the saved string is not
     * for the engine that reads the file, or will not read.
     */
    fun restore(file: File, saved: String): Triple<Any, Any, Class<*>>? {
        val fresh = load(file) ?: return null
        val cls = saved.substringBefore('\n')
        val body = saved.substringAfter('\n', "")
        if (cls != fresh.third.name || body.isEmpty()) return fresh
        val working = runCatching { fresh.third.getMethod("fromString", String::class.java).invoke(null, body) }.getOrNull() ?: return fresh
        return Triple(working, fresh.second, fresh.third)
    }

    fun saver(file: File) = androidx.compose.runtime.saveable.Saver<Triple<Any, Any, Class<*>>?, String>(
        save = { save(it) }, restore = { restore(file, it) },
    )
}

/** A text-only button for a destructive choice, in the danger colour. */
@Composable
private fun DangerTextButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Shell.touchTarget)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Shell.dangerOnPaper)
    }
}

/** The marker for a row that differs from the loaded preset. */
@Composable
private fun ChangedDot() {
    Box(Modifier.size(8.dp).background(Shell.inkOnPaper))
}

@Composable
private fun OptionRow(
    opt: Option,
    settings: Any,
    changed: Boolean,
    gateReason: String?,
    generation: String?,
    onEdit: () -> Unit,
    onRevert: () -> Unit,
) {
    val live = gateReason == null
    // Disabled, not hidden. A control that vanishes when its master moves is
    // disorienting; one that greys out and says why teaches what the master
    // does.
    val ink = if (live) Shell.inkOnPaper else Shell.hintOnPaper
    var showHelp by remember(opt.id) { mutableStateOf(false) }
    val help = SettingsReflector.helpFor(opt.id)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                // The WHOLE row toggles a boolean, not just the 18dp box.
                // Tapping the label did nothing, which is the most natural
                // thing to tap and the largest part of the row.
                .then(
                    when {
                        opt is Option.Bool && live ->
                            Modifier.clickable { opt.set(settings, !opt.get(settings)); onEdit() }
                        // A number or choice row has no toggle: its label opens
                        // the help instead, a bigger target than the icon.
                        opt !is Option.Bool && help != null -> Modifier.clickable { showHelp = !showHelp }
                        else -> Modifier
                    },
                )
                .heightIn(min = Shell.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (changed) {
                ChangedDot()
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Weighted without fill: a long label wraps instead of
                    // squeezing the help button to zero width (2026-09-27, audit).
                    Text(
                        opt.label,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                    )
                    // The randomizer's own explanation, on demand. 122 of the
                    // 138 fields have one; the rest show no marker at all
                    // rather than an empty panel.
                    if (help != null) {
                        // A 48dp target with an icon, not a 28dp "?" glyph.
                        Box(
                            Modifier.size(Shell.touchTarget).clickable { showHelp = !showHelp },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Info,
                                contentDescription = "Help",
                                tint = Shell.hintOnPaper,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                gateReason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                }
            }
            when (opt) {
                // The row owns the click; this is just the indicator. Dimmed
                // while dead, so a set box does not look like a live choice.
                is Option.Bool -> Box(Modifier.padding(8.dp).alpha(if (live) 1f else 0.38f)) {
                    ShellCheck(opt.get(settings))
                }

                // A dead number is shown, not offered: the stepper took edits
                // the engine then ignored (2026-09-27, audit).
                is Option.IntValue -> if (live) ShellStepper(
                    value = opt.get(settings),
                    // The desktop randomizer's own limits for this setting,
                    // widened to its "off" value where it has one.
                    range = SettingsReflector.stepperRange(opt.id, generation),
                    onChange = { v ->
                        opt.set(settings, SettingsReflector.snapValue(opt.id, opt.get(settings), v)); onEdit()
                    },
                ) else Text(
                    opt.get(settings).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Shell.hintOnPaper,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // Choice rows are handled BELOW, on their own line.
                is Option.Choice -> Unit
            }
            if (changed) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(Shell.touchTarget).clickable { onRevert() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Refresh,
                        contentDescription = "Put back the file's value",
                        tint = Shell.hintOnPaper,
                    )
                }
            }
        }

        if (showHelp && help != null) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = Shell.hintOnPaper,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // An enum's control goes on its OWN line under the label.
        //
        // Sharing a row with a weighted label crushed both: "Base statistics
        // mod" wrapped to "Base / statistic / s mod", and the longer Exp curve
        // enum pushed its label out of existence entirely while its third
        // option was squeezed to a sliver. A full-width line below the label
        // fits every enum in the file.
        if (opt is Option.Choice) {
            val current = opt.get(settings)
            fun name(v: String) = SettingsReflector.valueLabel(opt.id, v)
            // A value the editor does not offer (Custom starters) still shows
            // when the file already uses it.
            val values = if (current in opt.values) opt.values else opt.values + current
            // Segmented only when the labels actually FIT. Counting values was
            // not enough - three long names do not fit a phone width.
            val inline = values.size <= 3 && values.sumOf { name(it).length } <= 26
            Spacer(Modifier.height(2.dp))
            if (inline) {
                ShellSegmented(
                    values = values,
                    selected = current,
                    label = { name(it) },
                    // Dead: the chips stay readable but take no taps.
                    onSelect = { if (live) { opt.set(settings, it); onEdit() } },
                    modifier = Modifier.padding(bottom = 6.dp).alpha(if (live) 1f else 0.38f),
                )
            } else {
                var picking by remember(opt.id) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (live) Modifier.clickable { picking = true } else Modifier)
                        .background(Shell.frame).padding(2.dp)
                        .background(Shell.paper)
                        .heightIn(min = Shell.touchTarget)
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name(current),
                        Modifier.weight(1f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 13.sp,
                        color = ink,
                    )
                    // Says "this opens a list", which a bare box did not.
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = Shell.hintOnPaper,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (picking && live) {
                    ShellDialog(opt.label, onDismiss = { picking = false }) {
                        values.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        opt.set(settings, v); onEdit(); picking = false
                                    }
                                    .heightIn(min = Shell.touchTarget),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShellRadio(v == current)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    name(v),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Shell.inkOnPaper,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A text prompt in shell language. */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    singleLine: Boolean = true,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ShellDialog(title, onDismiss = onCancel) {
        Box(
            Modifier.fillMaxWidth().background(Shell.frame).padding(2.dp)
                .background(Shell.paper).padding(8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                textStyle = TextStyle(color = Shell.inkOnPaper, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Gen3Button("CANCEL") { onCancel() }
            Spacer(Modifier.width(8.dp))
            Gen3Button("OK", accent = true) {
                if (text.isNotBlank()) onConfirm(text.trim())
            }
        }
    }
}
