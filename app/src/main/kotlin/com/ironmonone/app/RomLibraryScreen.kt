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
 * My games, the first page of Library: the player's files, shelved by what they are.
 *
 * Five shelves for ROMs (clean, patched, other versions, hacks, other games) and one for
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
    // True once the library has been read. Until then the page is not empty, it is not read yet: the empty state
    // flashed on every visit, and after an update that re-reads the games it stayed for seconds (2026-09-30).
    var loaded by remember { mutableStateOf(false) }
    val progress = remember { FileProgress() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var selectedName by remember { mutableStateOf(store.library.selectedLibraryName()) }

    // Dialogs. One at a time; each is a small, named state.
    var renameRom by remember { mutableStateOf<LibraryStore.Entry?>(null) }
    var renamePatch by remember { mutableStateOf<LibraryStore.PatchEntry?>(null) }
    var patchFor by remember { mutableStateOf<LibraryStore.Entry?>(null) }      // PATCH on a ROM
    var applyTo by remember { mutableStateOf<LibraryStore.PatchEntry?>(null) }  // APPLY on a patch
    var natDexFor by remember { mutableStateOf<LibraryStore.Entry?>(null) }     // NAT. DEX on a game that has one
    // One way in for files, shared with the ROM Hacks screen.
    val importer = remember { LibraryImport(context, store, progress) }

    fun reload() {
        scope.launch {
            val (r, p) = withContext(Dispatchers.IO) { store.library.list() to store.library.listPatches() }
            roms.clear(); roms.addAll(r); patches.clear(); patches.addAll(p)
            loaded = true
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

    /**
     * A patched version KaizoCore makes by itself (Nat. Dex, the growth patch, Faster, Smart AI, Super Kaizo), made from
     * a copy of the library's own file, which stays where it is (PrepRun). Blake, 2026-09-30: PATCH said no patch fits
     * his FireRed 1.1 while the app carries Nat. Dex for it; "this needs applied to all games".
     */
    fun runBuiltIn(base: LibraryStore.Entry, o: PrepOptions.Option) {
        // One job at a time: a second run of the same patch wrote the same file and both failed, and the first to end
        // turned busy off under the other (rc32 audit P2 #74). Every row of the Patch and Apply windows comes here.
        if (busy) return
        val kind = base.kind ?: return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val (tmp, id) = PrepRun.copyFromLibrary(context, base, progress)
                    try {
                        if (!id.exact) throw PrepFailure("This copy of ${kind.displayName} is not an exact one, so KaizoCore cannot patch it. Nothing was changed.")
                        PrepRun.run(context, store, tmp, kind, o.id, progress)
                    } finally {
                        tmp.delete()   // spent by a run that worked; left behind by one that did not
                    }
                }
            }
            status = r.fold({ "$it Ready for Kaizo IronMON: pick it on Home, Kaizo IronMON." }, { if (it is NeedPatch) NEED_NATDEX_PATCH else prepFailure(it) })
            reload(); progress.clear(); busy = false
        }
    }

    fun runPatch(base: LibraryStore.Entry, p: LibraryStore.PatchEntry) {
        if (busy) return
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
                    "Your own games, hacks and patches. Nothing is downloaded and nothing " +
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
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) { if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ShellBusy() }
        else if (!loaded) ShellBusy()

        if (loaded && roms.isEmpty() && patches.isEmpty() && !busy) {
            EmptyState("Nothing here yet.", emptyLibraryLine())
        }

        // The result of the last action is the list's first item, as on ROM Hacks, and the list takes the height left.
        // In the fixed card at the top, the result of adding a zip of ten games (a line per file) pushed the list
        // off the screen until the tab was left (rc32 audit P2 #73). A new result scrolls into view.
        LaunchedEffect(status) { if (status != null) listState.animateScrollToItem(0) }
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            status?.let { s ->
                item(key = "status") {
                    Gen3Box(Modifier.fillMaxWidth()) {
                        Text(s, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                    }
                }
            }
            for (cat in LibraryStore.Category.entries) {
                val here = roms.filter { it.category == cat }
                if (here.isEmpty()) continue
                item(key = "h-" + cat.name) { Shelf(cat.title, cat.blurb, here.size) }
                items(here, key = { it.name }) { e ->
                    RomCard(
                        e, playing = e.name == selectedName, busy = busy,
                        patchCount = MaxDexInfo.fitting(patches, e).size + PrepRun.builtIns(e).size,
                        onPlay = { store.library.selectLibrary(e); selectedName = e.name; onPlay() },
                        onPatch = { patchFor = e },
                        // The games the Nat. Dex Extension has a patch for (FireRed 1.1 and Emerald), said on the card itself.
                        onNatDex = if (PrepRun.builtIns(e).any { it.mode == PrepOptions.Mode.NATDEX }) ({ natDexFor = e }) else null,
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
        // Trip's MaxDex patch is offered once, as the MaxDex built-in, where the game has one (MaxDexInfo.fitting).
        val fits = MaxDexInfo.fitting(patches, e)
        val builtIns = PrepRun.builtIns(e)
        ShellDialog("Patch ${stripKnownExt(e.name)}", onDismiss = { patchFor = null }) {
            if (builtIns.isNotEmpty()) {
                Text("Made by KaizoCore from this game", style = MaterialTheme.typography.titleSmall, color = Gen3.Ink)
                builtIns.forEach { o ->
                    LibraryPickRow(o.label, PrepOptions.describe(o, e.kind), onClick = {
                        patchFor = null
                        if (o.mode == PrepOptions.Mode.NATDEX) natDexFor = e else runBuiltIn(e, o)
                    })
                }
            }
            if (fits.isNotEmpty()) {
                if (builtIns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Your patches", style = MaterialTheme.typography.titleSmall, color = Gen3.Ink)
                }
                fits.forEach { p ->
                    LibraryPickRow(stripKnownExt(p.name), patchFormatLabel(p.format), onClick = { patchFor = null; runPatch(e, p) })
                }
            }
            if (fits.isEmpty() && builtIns.isEmpty()) {
                Text("No patch in the library fits this file yet. A patch is matched by the exact ROM it was made for.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            }
            Spacer(Modifier.height(8.dp))
            // Add a patch from here (Blake, 2026-09-30: "a button to add patch file to library"). The window stays open,
            // so a patch that fits this game is listed under Your patches as soon as it is in.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button(if (busy) "WORKING…" else "ADD A PATCH FILE", accent = fits.isEmpty() && builtIns.isEmpty(), enabled = !busy) {
                    picker.launch(arrayOf("*/*"))
                }
                Gen3Button("CLOSE") { patchFor = null }
            }
        }
    }
    natDexFor?.let { e ->
        val k = e.kind
        val natDex = PrepRun.builtIns(e).firstOrNull { it.mode == PrepOptions.Mode.NATDEX }
        if (k != null && natDex != null) ShellDialog(NatDexInfo.TITLE, onDismiss = { natDexFor = null }) {
            Text(NatDexInfo.WHAT, style = MaterialTheme.typography.titleSmall, color = Gen3.Ink)
            Spacer(Modifier.height(4.dp))
            NatDexInfo.lines(k).forEach { line ->
                Text("\u2022 $line", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Your ${stripKnownExt(e.name)} stays as it is; the Nat. Dex version is made from a copy and listed on Kaizo IronMON. " +
                NatDexInfo.CREDIT, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            Spacer(Modifier.height(10.dp))
            Gen3Button("MAKE IT", accent = true, enabled = !busy) { natDexFor = null; runBuiltIn(e, natDex) }
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
            val candidates = roms.filter {
                it.category == LibraryStore.Category.CLEAN || it.category == LibraryStore.Category.OTHER_VERSIONS || it.category == LibraryStore.Category.OTHER
            }
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
    /** Set for a game the Nat. Dex Extension has a patch for: the card says so with its own button. */
    onNatDex: (() -> Unit)? = null,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val tracked = entry.tracked
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
                    // The subtitle says whether the tracker works ("Tracker works", "No tracker") or, for a file
                    // it cannot read, what it is and that it plays (UX audit Words table, 2026-09-30).
                    Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = accent)
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
                Gen3Button(if (patchCount > 0) "PATCH ($patchCount)" else "PATCH", enabled = playable && !busy, onClick = onPatch)
                onNatDex?.let { Gen3Button("NAT. DEX", enabled = !busy, onClick = it) }
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
                Gen3Button(if (romCount > 0) "APPLY ($romCount)" else "APPLY", accent = romCount > 0, enabled = !busy, onClick = onApply)
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
    // An xdelta on the wrong copy of its game is SourceMismatch; it read "The patch file is damaged" (rc32 audit P2 #115).
    is com.ironmonone.patch.WrongSourceRom, is com.ironmonone.patch.WrongSourceSize, is com.ironmonone.patch.SourceMismatch ->
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

/**
 * What the empty page says is accepted and what the tracker reads (2026-09-30, UX audit P0-11). The file types are
 * the ones LibraryImport and ZipImport take (.gba, .gbc, .gb and .nds games, the four patch kinds, a .zip of either),
 * and the games are the ones with a pinned checksum, so it cannot promise a game whose tracker is not there.
 */
internal fun emptyLibraryLine(): String =
    "Add files takes games (.gba, .gbc, .gb, .nds), patches (.bps, .ips, .ups, .xdelta) and .zip files holding either. " +
        "The tracker reads the US English ${listWithAnd(LibraryStore.trackedGames())}. Any other game still plays, without a tracker."

/** "Red, Blue and Yellow". */
internal fun listWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

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
