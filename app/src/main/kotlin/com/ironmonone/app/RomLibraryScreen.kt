package com.ironmonone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.gen3.Gen3Header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ROMs tab: the player's files, shelved by what they are.
 *
 * Four shelves for ROMs (clean, patched, hacks, other games) and one for
 * patches. Everything is matched by identity: a patch card names the game
 * it is for, a ROM's Patch button lists only patches that fit its CRC, and
 * a patch's Apply lists only ROMs it fits. Names are the player's to change;
 * Rename offers names built from what the file is.
 *
 * Identification, copying and patching run off the main thread: a 128MB DS
 * ROM read through SAF and hashed on the composition thread is an ANR.
 */
@Composable
fun RomLibraryScreen(modifier: Modifier = Modifier, onPlay: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PrepStore(context) }
    val roms = remember { mutableStateListOf<LibraryStore.Entry>() }
    val patches = remember { mutableStateListOf<LibraryStore.PatchEntry>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val progress = remember { FileProgress() }
    var selectedName by remember { mutableStateOf(store.library.selectedLibraryName()) }

    // Dialogs. One at a time; each is a small, named state.
    var renameRom by remember { mutableStateOf<LibraryStore.Entry?>(null) }
    var renamePatch by remember { mutableStateOf<LibraryStore.PatchEntry?>(null) }
    var patchFor by remember { mutableStateOf<LibraryStore.Entry?>(null) }      // PATCH on a ROM
    var applyTo by remember { mutableStateOf<LibraryStore.PatchEntry?>(null) }  // APPLY on a patch
    // One way in for files, shared with the HACK tab.
    val importer = remember { LibraryImport(context, store, progress) }

    fun reload() {
        scope.launch {
            val (r, p) = withContext(Dispatchers.IO) { store.library.list() to store.library.listPatches() }
            roms.clear(); roms.addAll(r); patches.clear(); patches.addAll(p)
        }
    }
    LaunchedEffect(Unit) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = withContext(Dispatchers.IO) { importer.importUris(uris) }
            reload(); progress.clear(); busy = false
        }
    }

    fun runPatch(base: LibraryStore.Entry, p: LibraryStore.PatchEntry) {
        busy = true
        scope.launch {
            progress.start("Patching ${base.name}", base.sizeBytes)
            val r = withContext(Dispatchers.IO) { runCatching { store.library.apply(base, p) { d, t -> progress.at(d); if (t > 0) progress.total = t } } }
            status = r.fold({ "Made ${it.name}: ${it.subtitle}" }, ::patchFailure)
            reload(); progress.clear(); busy = false
        }
    }

    Column(modifier.fillMaxSize().padding(10.dp)) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Your own dumps, hacks and patches. Nothing is downloaded and nothing " +
                        "leaves this phone. Files are sorted by what they are, and a patch is " +
                        "only ever offered for the game it fits.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button(if (busy) "WORKING…" else "ADD FILES", enabled = !busy, accent = true) {
                        picker.launch(arrayOf("*/*"))
                    }
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) { if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ShellBusy() }

        if (roms.isEmpty() && patches.isEmpty() && !busy) {
            EmptyState(
                "Nothing here yet.",
                "Add files takes ROMs (.gba, .gbc, .nds), patches (.bps, .ips, .ups) and zips of either. " +
                    "KaizoCore tracks Red, Blue, Yellow, Gold, Silver, Crystal, FireRed, Emerald, Diamond, Pearl, " +
                    "Platinum, HeartGold, SoulSilver, Black, White, Black 2 and White 2 (U). Anything else plays without a tracker.",
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cat in LibraryStore.Category.entries) {
                val here = roms.filter { it.category == cat }
                if (here.isEmpty()) continue
                item(key = "h-" + cat.name) { Shelf(cat.title, cat.blurb, here.size) }
                items(here, key = { it.name }) { e ->
                    RomCard(
                        e, playing = e.name == selectedName, busy = busy,
                        patchCount = patches.count { it.matches(e) },
                        onPlay = { store.library.selectLibrary(e); selectedName = e.name; onPlay() },
                        onPatch = { patchFor = e },
                        onRename = { renameRom = e },
                        onDelete = {
                            scope.launch(Dispatchers.IO) { store.library.delete(e) }
                            roms.remove(e); if (selectedName == e.name) selectedName = null
                            status = "Deleted ${stripKnownExt(e.name)}. Its save states stay until you add it again."
                        },
                    )
                }
            }
            if (patches.isNotEmpty()) {
                item(key = "h-patches") { Shelf("Patches", "Hack and Nat. Dex patches, each matched to the game it is for.", patches.size) }
                items(patches, key = { "p-" + it.name }) { p ->
                    PatchCard(p, romCount = roms.count { p.matches(it) }, busy = busy,
                        onApply = { applyTo = p }, onRename = { renamePatch = p },
                        onDelete = {
                            scope.launch(Dispatchers.IO) { store.library.deletePatch(p) }
                            patches.remove(p); status = "Deleted ${stripKnownExt(p.name)}."
                        })
                }
            }
        }
    }

    // ---- dialogs ----
    renameRom?.let { e ->
        // suggestions() reads only what the sidecar holds, so it is safe here;
        // it used to hash the whole file on this thread (audit, 2026-09-27).
        RenameDialog("Rename ROM", stripKnownExt(e.name),
            suggestions = remember(e.name) { store.library.suggestions(e) },
            onDismiss = { renameRom = null }) { new ->
            val r = store.library.rename(e, new)
            if (selectedName == e.name) selectedName = r.name
            // A rename that did not happen said nothing before (audit, 2026-09-27).
            if (r.file == e.file) status = "Could not rename ${stripKnownExt(e.name)}. Try a different name."
            renameRom = null; reload()
        }
    }
    renamePatch?.let { p ->
        RenameDialog("Rename patch", stripKnownExt(p.name),
            suggestions = listOfNotNull(p.forName?.let { "${stripKnownExt(p.name)} for ${stripKnownExt(it)}" }),
            onDismiss = { renamePatch = null }) { new ->
            val r = store.library.renamePatch(p, new)
            if (r.file == p.file) status = "Could not rename ${stripKnownExt(p.name)}. Try a different name."
            renamePatch = null; reload()
        }
    }
    patchFor?.let { e ->
        val fits = patches.filter { it.matches(e) }
        ShellDialog("Patch ${stripKnownExt(e.name)}", onDismiss = { patchFor = null }) {
            if (fits.isEmpty()) {
                Text("No patch in the library fits this file. A patch is matched by the exact " +
                    "ROM it was made for; add one with Add files and it will appear here if it fits.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            } else fits.forEach { p ->
                LibraryPickRow(stripKnownExt(p.name), patchFormatLabel(p.format), onClick = { patchFor = null; runPatch(e, p) })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("CLOSE") { patchFor = null }
        }
    }
    applyTo?.let { p ->
        val fits = roms.filter { p.matches(it) }
        ShellDialog("Apply ${stripKnownExt(p.name)}", onDismiss = { applyTo = null }) {
            Text("For: " + patchForLabel(p),
                style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            if (fits.isEmpty()) {
                Text("None of your ROMs is the one this patch was made for. Add that dump first.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            } else fits.forEach { e ->
                LibraryPickRow(stripKnownExt(e.name), e.category.title, onClick = { applyTo = null; runPatch(e, p) })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("CLOSE") { applyTo = null }
        }
    }
    importer.pendingName?.let { name ->
        // An IPS cannot say what it is for, so the player does, once, from
        // the clean ROMs on hand. It is then offered for that file only.
        ShellDialog("Which game is $name for?", onDismiss = { importer.cancelPending() }) {
            Text("This patch does not say which game it was made for. Pick the ROM it was made for; it will " +
                "only ever be offered for that file.", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            Spacer(Modifier.height(6.dp))
            val candidates = roms.filter { it.category == LibraryStore.Category.CLEAN || it.category == LibraryStore.Category.OTHER }
            if (candidates.isEmpty()) Text("The game it is for is not in your library yet. Add it, and it will appear here.",
                style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            candidates.forEach { e ->
                LibraryPickRow(stripKnownExt(e.name), e.kind?.displayName ?: e.platform?.name ?: "", onClick = {
                    status = importer.declarePending(e)
                    reload()
                })
            }
            Spacer(Modifier.height(8.dp))
            // "Not now" was the only way out with no game on hand, and it threw
            // the patch away. The patch waits while the game is added (audit, 2026-09-27).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button(if (candidates.isEmpty()) "ADD THE GAME" else "ADD ANOTHER GAME", accent = candidates.isEmpty(), enabled = !busy) {
                    picker.launch(arrayOf("*/*"))
                }
                Gen3Button("NOT NOW") { importer.cancelPending() }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, blurb: String, count: Int) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gen3Header(title)
            Spacer(Modifier.width(8.dp))
            Text("$count", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.sp, color = Shell.hintOnNight)
        }
        Text(blurb, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RomCard(
    entry: LibraryStore.Entry,
    playing: Boolean,
    busy: Boolean,
    patchCount: Int,
    onPlay: () -> Unit,
    onPatch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val tracked = GameSession.trackerKind(entry.kind, entry.crc) != null
    val playable = entry.platform != null
    val accent = when {
        tracked -> Shell.goodOnPaper
        playable -> Shell.hintOnPaper
        else -> Shell.dangerOnPaper
    }
    var armed by remember(entry.name) { mutableStateOf(false) }
    // "Sure?" used to stay armed for good, so a stray tap minutes later deleted (audit, 2026-09-27).
    LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(DISARM_MS); armed = false } }

    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(entry.platform)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stripKnownExt(entry.name) + if (playing) "  (playing)" else "",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(entry.subtitle + if (tracked) " · tracked" else if (playable) " · plays untracked" else "",
                        style = MaterialTheme.typography.bodySmall, color = accent)
                    // No checksum on the card: it means nothing to a player (audit, 2026-09-27).
                    Text("%s · %s".format((knownExtOf(entry.name) ?: entry.platform?.name ?: "?").uppercase(), sizeLabel(entry.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Gen3Button("PLAY", accent = true, enabled = playable, onClick = onPlay)
                Gen3Button(if (patchCount > 0) "PATCH ($patchCount)" else "PATCH", enabled = playable, onClick = onPatch)
                Gen3Button("RENAME", onClick = onRename)
                Gen3Button(if (armed) "SURE?" else "DELETE", accent = armed, enabled = !busy,
                    onClick = { if (armed) { armed = false; onDelete() } else armed = true })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PatchCard(
    p: LibraryStore.PatchEntry,
    romCount: Int,
    busy: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var armed by remember(p.name) { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(DISARM_MS); armed = false } }
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(stripKnownExt(p.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("For: " + patchForLabel(p),
                style = MaterialTheme.typography.bodySmall,
                color = if (romCount > 0) Shell.goodOnPaper else Shell.hintOnPaper)
            Text("%s · %s".format(patchFormatLabel(p.format), sizeLabel(p.sizeBytes)),
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Gen3Button(if (romCount > 0) "APPLY ($romCount)" else "APPLY", accent = romCount > 0, onClick = onApply)
                Gen3Button("RENAME", onClick = onRename)
                Gen3Button(if (armed) "SURE?" else "DELETE", accent = armed, enabled = !busy,
                    onClick = { if (armed) { armed = false; onDelete() } else armed = true })
            }
        }
    }
}

@Composable
private fun RenameDialog(
    title: String,
    current: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    // The whole name starts selected, so typing replaces it, and Done renames (audit, 2026-09-27).
    var draft by remember(current) { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val canRename = draft.text.isNotBlank() && draft.text != current
    ShellDialog(title, onDismiss = onDismiss) {
        OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canRename) onRename(draft.text) }),
            modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Suggestions", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 13.sp, color = Shell.inkOnPaper)
            suggestions.forEach { s ->
                // A full touch target, shaped like the other controls (audit, 2026-09-27).
                Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(Shell.controlRadius)).background(Shell.raised)
                    .clickable { draft = TextFieldValue(s, TextRange(s.length)) }
                    .heightIn(min = Shell.touchTarget).padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("RENAME", accent = true, enabled = canRename) { onRename(draft.text) }
            Gen3Button("CANCEL", onClick = onDismiss)
        }
    }
}

/**
 * What a failed patch says to the player. Plain words only: the patcher's own
 * messages carry checksums and file paths (audit, 2026-09-27).
 */
internal fun patchFailure(t: Throwable): String = when (t) {
    is com.ironmonone.patch.WrongSourceRom, is com.ironmonone.patch.WrongSourceSize ->
        "This patch was made for a different version of the game. Nothing was changed."
    is com.ironmonone.patch.OutputMismatch ->
        "The patch applied, but the result came out wrong, so it was not kept. The game is probably a different version."
    is com.ironmonone.patch.PatchException -> t.message ?: "The patch did not apply."
    is OutOfMemoryError -> "This phone ran out of memory patching a game this size. Close other apps and try again."
    is java.io.IOException -> if ((t.message ?: "").contains("ENOSPC") || (t.message ?: "").contains("No space"))
        "Not enough free space on this phone to save the patched game." else "Could not save the patched game. Try again."
    else -> "The patch did not apply. It was probably made for a different version of this game."
}

/** How long a Delete stays armed as "Sure?" before it lets go. */
internal const val DISARM_MS = 3000L

/** "16 MB", or "512 KB" for a small file such as a patch. */
internal fun sizeLabel(bytes: Long): String =
    if (bytes >= 1L shl 20) "%,d MB".format((bytes + (1L shl 19)) shr 20) else "%,d KB".format(maxOf(1L, bytes / 1024))

/** The game a patch is for, in words: never a checksum (audit, 2026-09-27). */
internal fun patchForLabel(p: LibraryStore.PatchEntry): String =
    p.forName?.let(::stripKnownExt) ?: if (p.forCrc != null) "a game you have not added" else "a game it does not name"

/**
 * A choice in a library dialog: the name on its own line, the detail under it.
 * ShellListRow puts both on one line, and a long game name crushed the value
 * beside it (audit, 2026-09-27).
 */
@Composable
internal fun LibraryPickRow(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(Shell.controlRadius)).background(Shell.raised)
            .clickable(onClick = onClick)
            .heightIn(min = Shell.touchTarget).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
    }
}
