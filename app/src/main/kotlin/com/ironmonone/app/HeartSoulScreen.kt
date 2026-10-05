package com.ironmonone.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.patch.RomIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Pokemon Heart & Soul screen, opened by its own button on Home (HomeMode.HEARTSOUL). The steps and every word are
 * HnsSetup's; this only draws them and moves files: a picked file is copied into the cache first, so neither the
 * player's file nor a Library game is ever changed. The patch is the player's own download, opened in their browser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeartSoulScreen(
    modifier: Modifier = Modifier,
    /** Play the official 2.0.6 as it is, once it is in the Library. */
    onPlay: () -> Unit = {},
    onKaizo: () -> Unit = {},
    onNuzlocke: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PrepStore(context) }
    val progress = remember { FileProgress() }
    val work = remember { File(context.cacheDir, "hns-setup").apply { mkdirs() } }
    var game by remember { mutableStateOf<HnsSetup.Game?>(null) }
    var patch by remember { mutableStateOf<HnsSetup.Patch?>(null) }
    var gameLine by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var patchLine by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var added by remember { mutableStateOf<HnsSetup.Added?>(null) }
    var busy by remember { mutableStateOf(false) }
    val fromLibrary = remember { mutableStateListOf<Pair<LibraryStore.Entry, HnsSetup.Start>>() }
    var listed by remember { mutableStateOf(0) }

    LaunchedEffect(listed) {
        val l = withContext(Dispatchers.IO) { runCatching { HnsSetup.libraryGames(store.library.list()) }.getOrDefault(emptyList()) }
        fromLibrary.clear(); fromLibrary.addAll(l)
    }

    /** Copies [uri] into the work folder, unpacking a zip that holds one file; the copy and the name it was picked under. */
    fun copyIn(uri: Uri): Pair<File, String> {
        val name = runCatching { context.displayNameOf(uri) }.getOrDefault("file")
        val lower = name.lowercase()
        if (lower.endsWith(".7z")) throw HnsSetup.Problem(LibraryImport.archiveLine("7z"))
        if (lower.endsWith(".rar")) throw HnsSetup.Problem(LibraryImport.archiveLine("rar"))
        val size = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
        progress.start("Copying $name", size)
        val tmp = File(work, "pick-${System.nanoTime()}")
        context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
        val head = com.ironmonone.patch.Patcher.head(tmp, 8)
        if (ZipImport.isZip(name, head)) {
            val inside = try {
                tmp.inputStream().use { ZipImport.extractToFiles(it, File(work, "zip-${System.nanoTime()}")) }
            } finally { tmp.delete() }
            val one = inside.singleOrNull() ?: run {
                inside.forEach { it.second.delete() }
                throw HnsSetup.Problem("That zip holds ${if (inside.isEmpty()) "no game or patch" else "more than one file"}. Unpack it on your phone first, then pick the file inside.")
            }
            return one.second to one.first
        }
        return tmp to name
    }

    /** [file] as step 1's game, identified off the main thread; the file goes when it will not do. */
    fun identify(file: File, name: String): HnsSetup.Game {
        val id = RomIdentity.identify(file)
        val start = try {
            HnsSetup.startOf(id.crc, id.header)
        } catch (p: HnsSetup.Problem) {
            file.delete(); throw p
        }
        return HnsSetup.Game(file, name, id.crc, start)
    }

    fun takeGame(g: HnsSetup.Game) {
        game?.file?.takeIf { it != g.file }?.delete()
        game = g
        gameLine = HnsSetup.gameLine(g.start) to false
        result = null; added = null
    }

    val pickGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { val (f, n) = copyIn(uri); identify(f, n) } }
            r.onSuccess { takeGame(it) }
            r.onFailure { gameLine = (it as? HnsSetup.Problem)?.message.orEmpty().ifEmpty { patchFailure(it) } to true }
            progress.clear(); busy = false
        }
    }
    val pickPatch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val (f, n) = copyIn(uri)
                    val format = try { HnsSetup.patchFormatOf(f) } catch (p: HnsSetup.Problem) { f.delete(); throw p }
                    HnsSetup.Patch(f, n, format)
                }
            }
            r.onSuccess { p ->
                patch?.file?.takeIf { it != p.file }?.delete()
                patch = p
                patchLine = HnsSetup.patchLine(p.format) to false; result = null
            }
            r.onFailure { patchLine = (it as? HnsSetup.Problem)?.message.orEmpty().ifEmpty { patchFailure(it) } to true }
            progress.clear(); busy = false
        }
    }

    fun useLibrary(e: LibraryStore.Entry) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    progress.start("Copying ${stripKnownExt(e.name)}", e.sizeBytes)
                    val tmp = File(work, "pick-${System.nanoTime()}")
                    e.file.inputStream().use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
                    identify(tmp, e.name)
                }
            }
            r.onSuccess { takeGame(it) }
            r.onFailure { gameLine = (it as? HnsSetup.Problem)?.message.orEmpty().ifEmpty { patchFailure(it) } to true }
            progress.clear(); busy = false
        }
    }

    fun patchIt() {
        val g = game ?: return
        busy = true
        result = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val comfort = store.bundledPatch(context, HnsSetup.COMFORT_PATCH)
                    val made = HnsSetup.make(g, patch, comfort, work, onStep = { progress.start(it, 0L) }) { d, t -> progress.at(d); if (t > 0) progress.total = t }
                    progress.start("Adding to your library", 0L)
                    HnsSetup.addToLibrary(store.library, made, g.name, patch?.name)
                }
            }
            r.onSuccess {
                added = it
                result = HnsSetup.doneLine(it) to false
                game = null; patch?.file?.delete(); patch = null; gameLine = null; patchLine = null
                listed++
            }
            r.onFailure { result = (it as? HnsSetup.Problem)?.message.orEmpty().ifEmpty { patchFailure(it) } to true }
            progress.clear(); busy = false
        }
    }

    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(HnsSetup.TITLE, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Gen3.Ink)
                Spacer(Modifier.height(6.dp))
                Text(HnsSetup.INTRO, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            }
        }
        if (busy) { if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ShellBusy() }

        // ---- 1. your game ----
        StepHeader("1", HnsSetup.STEP1, "PICK A FILE", enabled = !busy) { pickGame.launch(arrayOf("*/*")) }
        Hint(HnsSetup.STEP1_LINE)
        // Once both games are made, step 1 has nothing left to offer: the list would only invite doing it again.
        if (fromLibrary.isNotEmpty() && game == null && added == null) {
            Hint(HnsSetup.STEP1_LIBRARY)
            fromLibrary.forEach { (e, start) ->
                ChoiceRow(stripKnownExt(e.name), HnsSetup.gameLine(start), selected = false, enabled = !busy) { useLibrary(e) }
            }
        }
        game?.let { g -> ChoiceRow(stripKnownExt(g.name), gameLine?.first.orEmpty(), selected = true, enabled = !busy) {} }
        gameLine?.takeIf { it.second }?.let { (t, _) -> Text(t, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper) }

        // ---- 2. the patch ----
        StepHeader("2", HnsSetup.STEP2, "PICK THE PATCH", enabled = !busy && HnsSetup.needsPatch(game?.start ?: HnsSetup.Start.EMERALD)) {
            pickPatch.launch(arrayOf("*/*"))
        }
        if (game != null && !HnsSetup.needsPatch(game?.start)) Hint(HnsSetup.STEP2_NOT_NEEDED)
        else {
            Hint(HnsSetup.STEP2_LINE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button("Open GitHub", enabled = !busy, raw = true) { open(HnsSetup.GITHUB_URL) }
                Gen3Button("Open Hackdex", enabled = !busy, raw = true) { open(HnsSetup.HACKDEX_URL) }
            }
            patch?.let { p -> ChoiceRow(stripKnownExt(p.name), patchLine?.first.orEmpty(), selected = true, enabled = !busy) {} }
            patchLine?.takeIf { it.second }?.let { (t, _) -> Text(t, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper) }
        }

        // ---- 3. patch ----
        StepTitle("3", HnsSetup.STEP3)
        Hint(HnsSetup.STEP3_LINE)
        HnsSetup.waitingFor(game, patch)?.takeIf { added == null }?.let { Hint(it) }
        Gen3Button("PATCH IT", accent = true, enabled = !busy && HnsSetup.canPatch(game, patch)) { patchIt() }
        result?.let { (t, bad) -> Text(t, style = MaterialTheme.typography.bodyMedium, color = if (bad) Shell.dangerOnPaper else Shell.goodOnPaper) }

        // ---- 4. choose how to play ----
        StepTitle("4", HnsSetup.STEP4)
        Hint(HnsSetup.STEP4_LINE)
        val ready = added != null || fromLibrary.any { it.second == HnsSetup.Start.KAIZO }
        val official = added?.official ?: fromLibrary.firstOrNull { it.second == HnsSetup.Start.OFFICIAL }?.first
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("Kaizo IronMON", accent = ready, enabled = !busy && ready, raw = true) { onKaizo() }
            Gen3Button("NUZLOCKE", enabled = !busy && ready) { onNuzlocke() }
            Gen3Button("Play Heart & Soul", enabled = !busy && official != null, raw = true) {
                official?.let { store.library.selectLibrary(it); onPlay() }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(HnsSetup.CREDIT, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
        Gen3Button("Heart & Soul on GitHub", enabled = true, raw = true) { open(HnsSetup.REPO_URL) }
        Spacer(Modifier.height(16.dp))
    }
}
