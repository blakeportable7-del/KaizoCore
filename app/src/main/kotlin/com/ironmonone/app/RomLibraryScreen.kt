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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
 * it is for, a ROM's PATCH button lists only patches that fit its CRC, and
 * a patch's APPLY lists only ROMs it fits. Names are the player's to change;
 * RENAME offers names built from what the file is.
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
    // An IPS being imported needs the player to say which game it is for.
    var pendingIps by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    fun reload() {
        scope.launch {
            val (r, p) = withContext(Dispatchers.IO) { store.library.list() to store.library.listPatches() }
            roms.clear(); roms.addAll(r); patches.clear(); patches.addAll(p)
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun importOne(name: String, bytes: ByteArray): String = when {
        // A zip is opened and every ROM or patch inside is imported on its own.
        ZipImport.isZip(name, bytes) -> {
            val inside = ZipImport.extract(bytes)
            if (inside.isEmpty()) "$name holds no ROM or patch this app reads."
            else inside.joinToString(Char(10).toString()) { (n, b) -> importOne(n, b) }.ifBlank { "" }
        }
        LibraryStore.looksLikePatch(name) || store.library.peekPatch(bytes) != null -> {
            val peek = store.library.peekPatch(bytes)
            when {
                peek == null -> "$name is not a patch this app reads."
                peek.forCrc == null -> { pendingIps = name to bytes; "" }
                else -> { val p = store.library.importPatch(name, bytes); "Added patch ${p.name} for ${p.forName ?: "an unknown game (%08x)".format(p.forCrc)}." }
            }
        }
        else -> { val e = store.library.import(name, bytes); "Added ${e.name}: ${e.subtitle}" }
    }

    /**
     * Import one picked file that has been streamed to [f]. ROMs move into
     * the library as files (a DS dump is 128 to 512 MB and does not fit in
     * the heap; Black 2 proved it); zips are unpacked to files the same
     * way; only patches, which are small, are read into memory.
     */
    fun importFile(name: String, f: java.io.File): String {
        val head = f.inputStream().use { i -> val b = ByteArray(8); val n = i.read(b); if (n > 0) b.copyOf(n) else ByteArray(0) }
        return when {
            ZipImport.isZip(name, head) -> {
                progress.start("Unpacking $name", f.length())
                val inside = f.inputStream().use { ZipImport.extractToFiles(it, java.io.File(f.parentFile, f.name + ".d")) { progress.at(it) } }
                f.delete()
                if (inside.isEmpty()) "$name holds no ROM or patch this app reads."
                else inside.joinToString(Char(10).toString()) { (n, file) -> importFile(n, file) }.ifBlank { "" }
            }
            LibraryStore.looksLikePatch(name) || f.length() < 32L * 1024 * 1024 && store.library.peekPatch(f.readBytes()) != null ->
                importOne(name, f.readBytes()).also { f.delete() }
            else -> {
                progress.start("Checking $name", f.length())
                val e = store.library.importFile(name, f) { d, t -> progress.at(d); if (t > 0) progress.total = t }
                "Added ${e.name}: ${e.subtitle}" + (if (e.verified) ". Ready on the RUN tab." else "")
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val lines = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val tmp = java.io.File(context.cacheDir, "import-" + System.nanoTime())
                        val name = context.displayNameOf(uri)
                        val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                        progress.start("Copying $name", if (size > 0) size else 0L)
                        context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
                        importFile(name, tmp)
                    }.getOrElse { (it as? com.ironmonone.patch.PatchException)?.message ?: "Could not read one file." }
                }
            }
            status = lines.filter { it.isNotEmpty() }.joinToString("\n").ifBlank { null }
            reload(); progress.clear(); busy = false
        }
    }

    fun runPatch(base: LibraryStore.Entry, p: LibraryStore.PatchEntry) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { store.library.apply(base, p) } }
            status = r.fold({ "Made ${it.name}: ${it.subtitle}" },
                { (it as? com.ironmonone.patch.PatchException)?.message ?: "Patching failed." })
            reload(); busy = false
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
                "ADD FILES takes ROMs (.gba, .gbc, .nds), patches (.bps, .ips, .ups) and zips of either. " +
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
                        e, playing = e.name == selectedName,
                        patchCount = patches.count { it.matches(e) },
                        onPlay = { store.library.selectLibrary(e); selectedName = e.name; onPlay() },
                        onPatch = { patchFor = e },
                        onRename = { renameRom = e },
                        onDelete = {
                            scope.launch(Dispatchers.IO) { store.library.delete(e) }
                            roms.remove(e); if (selectedName == e.name) selectedName = null
                            status = "Deleted ${e.name}. Its save states stay until you add it again."
                        },
                    )
                }
            }
            if (patches.isNotEmpty()) {
                item(key = "h-patches") { Shelf("Patches", "Hack and Nat. Dex patches, each matched to the game it is for.", patches.size) }
                items(patches, key = { "p-" + it.name }) { p ->
                    PatchCard(p, romCount = roms.count { p.matches(it) },
                        onApply = { applyTo = p }, onRename = { renamePatch = p },
                        onDelete = {
                            scope.launch(Dispatchers.IO) { store.library.deletePatch(p) }
                            patches.remove(p); status = "Deleted ${p.name}."
                        })
                }
            }
        }
    }

    // ---- dialogs ----
    renameRom?.let { e ->
        RenameDialog("Rename ROM", e.name.substringBeforeLast('.'),
            suggestions = remember(e.name) { store.library.suggestions(e) },
            onDismiss = { renameRom = null }) { new ->
            val r = store.library.rename(e, new)
            if (selectedName == e.name) selectedName = r.name
            renameRom = null; reload()
        }
    }
    renamePatch?.let { p ->
        RenameDialog("Rename patch", p.name.substringBeforeLast('.'),
            suggestions = listOfNotNull(p.forName?.let { "${p.name.substringBeforeLast('.')} for ${it.substringBeforeLast('.')}" }),
            onDismiss = { renamePatch = null }) { new ->
            store.library.renamePatch(p, new); renamePatch = null; reload()
        }
    }
    patchFor?.let { e ->
        val fits = patches.filter { it.matches(e) }
        ShellDialog("Patch ${e.name}", onDismiss = { patchFor = null }) {
            if (fits.isEmpty()) {
                Text("No patch in the library fits this file. A patch is matched by the exact " +
                    "ROM it was made for; add one with ADD FILES and it will appear here if it fits.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            } else fits.forEach { p ->
                ShellListRow(p.name, p.format.name, onClick = { patchFor = null; runPatch(e, p) })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("CLOSE") { patchFor = null }
        }
    }
    applyTo?.let { p ->
        val fits = roms.filter { p.matches(it) }
        ShellDialog("Apply ${p.name}", onDismiss = { applyTo = null }) {
            Text("For: " + (p.forName ?: p.forCrc?.let { "a ROM with CRC %08x".format(it) } ?: "unknown"),
                style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            if (fits.isEmpty()) {
                Text("None of your ROMs is the one this patch was made for. Add that dump first.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            } else fits.forEach { e ->
                ShellListRow(e.name, e.category.title, onClick = { applyTo = null; runPatch(e, p) })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("CLOSE") { applyTo = null }
        }
    }
    pendingIps?.let { (name, bytes) ->
        // An IPS cannot say what it is for, so the player does, once, from
        // the clean ROMs on hand. It is then offered for that file only.
        ShellDialog("Which game is $name for?", onDismiss = { pendingIps = null }) {
            Text("An .ips patch does not name its game. Pick the ROM it was made for; it will " +
                "only ever be offered for that file.", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            Spacer(Modifier.height(6.dp))
            val candidates = roms.filter { it.category == LibraryStore.Category.CLEAN || it.category == LibraryStore.Category.OTHER }
            if (candidates.isEmpty()) Text("Add the clean ROM first.", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            candidates.forEach { e ->
                ShellListRow(e.name, e.kind?.displayName ?: e.platform?.name ?: "", onClick = {
                    val p = store.library.importPatch(name, bytes, declaredFor = e)
                    status = "Added patch ${p.name} for ${e.name}."
                    pendingIps = null; reload()
                })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("NOT NOW") { pendingIps = null }
        }
    }
}

@Composable
private fun Shelf(title: String, blurb: String, count: Int) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gen3Header(title)
            Spacer(Modifier.width(8.dp))
            Text("$count", fontFamily = Gen3.PixelFont, fontSize = 10.sp, color = Gen3.Paper)
        }
        Text(blurb, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RomCard(
    entry: LibraryStore.Entry,
    playing: Boolean,
    patchCount: Int,
    onPlay: () -> Unit,
    onPatch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val tracked = GameSession.trackerKind(entry.kind, entry.crc) != null
    val playable = entry.platform != null
    val accent = when {
        tracked -> MaterialTheme.colorScheme.primary
        playable -> Shell.inkOnPaper
        else -> MaterialTheme.colorScheme.error
    }
    var armed by remember(entry.name) { mutableStateOf(false) }

    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(10.dp).clip(RoundedCornerShape(1.dp)), color = accent, content = {})
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name.substringBeforeLast('.') + if (playing) "  (playing)" else "",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(entry.subtitle + if (tracked) " · tracked" else if (playable) " · plays untracked" else "",
                        style = MaterialTheme.typography.bodySmall, color = accent)
                    Text("%s · %,d KB · crc %08x".format(entry.name.substringAfterLast('.', "?").uppercase(),
                        entry.sizeBytes / 1024, entry.crc),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Shell.inkOnPaper)
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Gen3Button("PLAY", accent = true, enabled = playable, onClick = onPlay)
                Gen3Button(if (patchCount > 0) "PATCH ($patchCount)" else "PATCH", enabled = playable, onClick = onPatch)
                Gen3Button("RENAME", onClick = onRename)
                Gen3Button(if (armed) "SURE?" else "DELETE", accent = armed,
                    onClick = { if (armed) onDelete() else armed = true })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PatchCard(
    p: LibraryStore.PatchEntry,
    romCount: Int,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var armed by remember(p.name) { mutableStateOf(false) }
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(p.name.substringBeforeLast('.'), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("For: " + (p.forName ?: p.forCrc?.let { "CRC %08x (not in your library)".format(it) } ?: "unknown"),
                style = MaterialTheme.typography.bodySmall,
                color = if (romCount > 0) MaterialTheme.colorScheme.primary else Shell.inkOnPaper)
            Text("%s · %,d KB".format(p.format.name, p.sizeBytes / 1024),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Shell.inkOnPaper)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Gen3Button(if (romCount > 0) "APPLY ($romCount)" else "APPLY", accent = romCount > 0, onClick = onApply)
                Gen3Button("RENAME", onClick = onRename)
                Gen3Button(if (armed) "SURE?" else "DELETE", accent = armed,
                    onClick = { if (armed) onDelete() else armed = true })
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
    var draft by remember(current) { mutableStateOf(current) }
    ShellDialog(title, onDismiss = onDismiss) {
        OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("SUGGESTIONS", fontFamily = Gen3.PixelFont, fontSize = 9.sp, color = Shell.inkOnPaper)
            suggestions.forEach { s ->
                Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .background(Color(0x14000000)).clickable { draft = s }.padding(8.dp)) {
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("RENAME", accent = true, enabled = draft.isNotBlank() && draft != current) { onRename(draft) }
            Gen3Button("CANCEL", onClick = onDismiss)
        }
    }
}
