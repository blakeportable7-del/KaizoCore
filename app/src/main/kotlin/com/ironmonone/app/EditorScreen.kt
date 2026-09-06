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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ironmonone.app.gen3.Gen3
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
    val loaded: Triple<Any, Any, Class<*>>? = remember(file) {
        fun read(): Pair<Any, Class<*>>? {
            runCatching { FileInputStream(file).use { NdSettings.read(it) } }
                .getOrNull()?.let { return it to NdSettings::class.java }
            runCatching { FileInputStream(file).use { ZxSettings.read(it) } }
                .getOrNull()?.let { return it to ZxSettings::class.java }
            return null
        }
        val a = read() ?: return@remember null
        val b = read() ?: return@remember null
        Triple(a.first, b.first, a.second)
    }

    if (loaded == null) {
        ScreenBackground(null) {
            Column(modifier.fillMaxSize().padding(16.dp)) {
                EmptyState(
                    "Could not read that preset.",
                    "\"${file.name}\" is not a settings file either engine " +
                        "recognises. It may be from a newer randomizer.",
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
        val gate = SettingsReflector.gateFor(opt.id) ?: return null
        val master = allOptions.firstOrNull { it.id == gate.whenField } ?: return null
        if (valueOf(master, settings) != gate.equalsValue) return null
        return "No effect while " + master.label + " is " +
            SettingsReflector.prettifyEnum(gate.equalsValue)
    }

    val changedCount = allOptions.count { changed(it) }

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
                                fontFamily = Gen3.PixelFont,
                                fontSize = 11.sp,
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
                            Box(
                                Modifier.fillMaxWidth().background(Shell.frame)
                                    .padding(2.dp).background(Shell.paper)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            ) {
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
                                Text(
                                    if (openSection == section) "v" else ">",
                                    fontFamily = Gen3.PixelFont,
                                    fontSize = 9.sp,
                                    color = Shell.hintOnPaper,
                                    modifier = Modifier.width(20.dp),
                                )
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
                                        fontFamily = Gen3.PixelFont,
                                        fontSize = 9.sp,
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
                        fontFamily = Gen3.PixelFont,
                        fontSize = 9.sp,
                        color = Shell.hintOnNight,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Gen3Button("COPY") {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clip.setPrimaryClip(
                            ClipData.newPlainText("settings", settings.toString()),
                        )
                        status = "Settings string copied."; statusError = false
                    }
                    Spacer(Modifier.width(6.dp))
                    Gen3Button("PASTE") { pasteDialog = true }
                    Spacer(Modifier.width(6.dp))
                    Gen3Button("BACK") { onClose() }
                }
            }
        }
    }

    if (saveDialog) {
        NameDialog(
            title = "Save preset as",
            initial = file.name.removeSuffix(".rnqs") + " (edited)",
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
                    store.importSettings("$name.rnqs", tmp.readBytes())
                } finally {
                    // Always: a failed save used to leave the temp file behind.
                    tmp.delete()
                }
            }.onSuccess {
                status = "Saved. Pick it on the Run tab."; statusError = false
            }.onFailure {
                status = "Save failed: ${it.message}"; statusError = true
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
            val natDexOk = NatDexEngine.validateSettingsString(text) == null
            val zxOk = ZxEngine.validateSettingsString(text) == null
            if (!natDexOk && !zxOk) {
                status = "That settings string is not valid for either engine."
                statusError = true
                return@NameDialog
            }
            runCatching {
                val dest = store.importSettings("Pasted preset.rnqs", ByteArray(0))
                if (natDexOk) NatDexEngine.writeSettingsString(text, dest)
                else ZxEngine.writeSettingsString(text, dest)
            }.onSuccess {
                status = "Pasted preset saved. Pick it on the Run tab."; statusError = false
            }.onFailure {
                status = "Could not save: ${it.message}"; statusError = true
            }
        }
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
                    if (opt is Option.Bool && live)
                        Modifier.clickable { opt.set(settings, !opt.get(settings)); onEdit() }
                    else Modifier,
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
                    Text(
                        opt.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                    )
                    // The randomizer's own explanation, on demand. 122 of the
                    // 138 fields have one; the rest show no marker at all
                    // rather than an empty panel.
                    if (help != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(28.dp).clickable { showHelp = !showHelp },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "?",
                                fontFamily = Gen3.PixelFont,
                                fontSize = 10.sp,
                                color = Shell.hintOnPaper,
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
                // The row owns the click; this is just the indicator.
                is Option.Bool -> Box(Modifier.padding(8.dp)) {
                    ShellCheck(opt.get(settings))
                }

                is Option.IntValue -> ShellStepper(
                    value = opt.get(settings),
                    // The engine clamps anyway; this keeps the stepper sane.
                    range = 0..255,
                    onChange = { opt.set(settings, it); onEdit() },
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
                    Text(
                        "<",
                        fontFamily = Gen3.PixelFont,
                        fontSize = 11.sp,
                        color = Shell.inkOnPaper,
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
            // Segmented only when the labels actually FIT. Counting values was
            // not enough - three long names do not fit a phone width.
            val inline = opt.values.size <= 3 &&
                opt.values.sumOf { SettingsReflector.prettifyEnum(it).length } <= 26
            Spacer(Modifier.height(2.dp))
            if (inline) {
                ShellSegmented(
                    values = opt.values,
                    selected = current,
                    label = { SettingsReflector.prettifyEnum(it) },
                    onSelect = { opt.set(settings, it); onEdit() },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else {
                var picking by remember(opt.id) { mutableStateOf(false) }
                Box(
                    Modifier.fillMaxWidth()
                        .clickable { picking = true }
                        .background(Shell.frame).padding(2.dp)
                        .background(Shell.paper)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        SettingsReflector.prettifyEnum(current),
                        fontFamily = Gen3.PixelFont,
                        fontSize = 9.sp,
                        color = Shell.inkOnPaper,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (picking) {
                    ShellDialog(opt.label, onDismiss = { picking = false }) {
                        opt.values.forEach { v ->
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
                                    SettingsReflector.prettifyEnum(v),
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
