package com.ironmonone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Blake's flow, verbatim: "i upload the vanilla rom, then select nationaldex or
 * standard dex, and it patches for me."
 *
 * The Nat. Dex .bps is imported ONCE and remembered; after that the choice is just a
 * radio button. An already-patched Nat. Dex ROM is accepted too and skips the patch.
 *
 * Library's second page, "Patched versions" (2026-09-30, UX audit P0-10): most games are ready as soon as they
 * are added under My games, so this page is only for making a patched version, and it opens on Standard. It works
 * on an exact copy of a game the tracker reads and on nothing else (RomIdentity.Result.exact): a file it cannot
 * set up is told what it is and sent to My games through [onMyGames].
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrepareScreen(modifier: Modifier = Modifier, onMyGames: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }
    val scope = rememberCoroutineScope()

    var romName by remember { mutableStateOf<String?>(null) }
    // The picked ROM as a FILE, never as bytes: a DS dump is 128 to 512 MB and
    // reading one into the heap was what killed PREP on Blake's phone with
    // Black 2 (2026-09-07). Only the GBA patch path reads bytes, and those
    // dumps are 16 to 32 MB.
    var romFile by remember { mutableStateOf<java.io.File?>(null) }
    var romId by remember { mutableStateOf<RomIdentity.Result?>(null) }
    /** The PREP option chosen for this dump; null means the game's first (default) option. */
    var chosenOption by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var needPatchImport by remember { mutableStateOf(false) }
    val progress = remember { FileProgress() }

    fun say(text: String, error: Boolean = false) { message = text; messageIsError = error }

    val pickRom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true; message = null; needPatchImport = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // Stream to a temp file; unzip on disk; identify from the file
                    // (RomIdentity.identify(File) reads the header and streams the CRC).
                    val tmp = java.io.File(context.cacheDir, "prep-" + System.nanoTime())
                    var name = context.displayNameOf(uri)
                    val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                    progress.start("Copying $name", if (size > 0) size else 0L)
                    context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
                    var file = tmp
                    val head = tmp.inputStream().use { i -> val h = ByteArray(8); val n = i.read(h); if (n > 0) h.copyOf(n) else ByteArray(0) }
                    // A .7z or .rar is not opened, and it is said so instead of "not a game" (2026-09-30, UX audit P0-11).
                    LibraryImport.archiveKind(name, head)?.let { throw ArchiveNotOpened(it) }
                    if (ZipImport.isZip(name, head)) {
                        progress.start("Unpacking $name", tmp.length())
                        val inside = tmp.inputStream().use { ZipImport.extractToFiles(it, java.io.File(tmp.parentFile, tmp.name + ".d")) { progress.at(it) } }
                        tmp.delete()
                        val rom = inside.firstOrNull { (n, _) -> n.substringAfterLast('.').lowercase() in setOf("gba", "gbc", "gb", "nds") }
                            ?: inside.firstOrNull() ?: throw NoRomInZip()
                        name = rom.first; file = rom.second
                    }
                    progress.start("Checking $name", file.length())
                    Triple(name, file, RomIdentity.identify(file) { d, _ -> progress.at(d) })
                }
            }.onSuccess { (n, f, id) ->
                // A file that is not a game is explained once, under the pick,
                // with where to go next; it used to repeat here in red (audit, 2026-09-27).
                romFile?.delete(); romName = n; romFile = f; romId = id
                // A new file opens on Standard again: a choice made for the last one must not carry over to this game.
                chosenOption = null
            }.onFailure { say(readFailure(it), error = true); runCatching { context.cacheDir.listFiles()?.filter { f -> f.name.startsWith("prep-") }?.forEach { f -> f.deleteRecursively() } } }
            progress.clear(); busy = false
        }
    }

    val pickPatch = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val b = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    store.importPatch(b).getOrThrow()
                }
            }.onSuccess {
                needPatchImport = false
                say("Patch saved. It will be found automatically from now on.")
            }.onFailure {
                // PrepStore words its own refusals; anything else is not for the player to read.
                say((it as? IllegalArgumentException)?.message ?: "Could not read that patch file. Pick it again.", error = true)
            }
            busy = false
        }
    }

    // The games already in My games that KaizoCore can make a patched version of (Blake, 2026-09-30: the page would
    // only take a file from the phone's storage, so a game added and then cleared from Downloads could not be patched).
    val yourGames by produceState(initialValue = emptyList<LibraryStore.Entry>(), busy) {
        value = withContext(Dispatchers.IO) { store.library.list().filter { PrepRun.builtIns(it).isNotEmpty() } }
    }

    fun pickFromLibrary(e: LibraryStore.Entry) {
        busy = true; message = null; needPatchImport = false
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { PrepRun.copyFromLibrary(context, e, progress) } }
                .onSuccess { (f, id) ->
                    romFile?.delete(); romName = e.name; romFile = f; romId = id
                    // Picked to be patched: open on its first patched version, not on the game as it is.
                    chosenOption = e.kind?.let { k -> PrepRun.builtIns(k).firstOrNull()?.id }
                }
                .onFailure { say(readFailure(it), error = true) }
            progress.clear(); busy = false
        }
    }

    fun prepare() {
        val file = romFile ?: return
        val id = romId ?: return
        val kind = id.kind ?: return
        // Only an exact copy of a known game is stored: the list of prepared games (PrepStore.listPrepared) and the
        // Kaizo screen read a stored file by its checksum, so anything else was stored, called "Ready for Kaizo
        // IronMON" and never listed. A game recognised by its title alone (Pokemon Black, today) or whose checksum
        // is another (a trimmed copy) used to get through here (audit, 2026-09-27; UX audit P0-11, 2026-09-30).
        if (!id.exact) {
            say(
                if (kind.expectedCrc == com.ironmonone.core.RomKind.CRC_UNKNOWN)
                    "This copy of ${kind.displayName} has not been checked by KaizoCore yet, so it cannot be set up here. " +
                        "It plays from My games, without a tracker. Nothing was changed."
                else "This file is not an exact copy of ${kind.displayName}, so it cannot be set up here. " +
                    "It plays from My games, without a tracker. Nothing was changed.",
                error = true,
            )
            return
        }
        busy = true; message = null
        // A stale phase from the pick used to label this job; each mode names its own (audit, 2026-09-27).
        progress.clear()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { PrepRun.run(context, store, file, kind, chosenOption, progress) }
            }.onSuccess { msg -> say("$msg Ready for Kaizo IronMON."); romFile = null; romId = null; romName = null }
                .onFailure {
                    if (it is NeedPatch) {
                        // Not an error: a one-time step, with where to get the file (audit, 2026-09-27).
                        needPatchImport = true
                        say(NEED_NATDEX_PATCH)
                    } else say(prepFailure(it), error = true)
                }
            progress.clear(); busy = false
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
        // Why this page exists, before anything else (audit, 2026-09-27), and that most games never need it (UX audit P0-10, 2026-09-30).
        Text(
            "Most games are ready as soon as you add them under My games. Use this page only to make a patched version, " +
                "such as Faster FireRed, Nat. Dex or Super Kaizo, from a game you already added.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gen3.Ink,
        )
        Spacer(Modifier.height(14.dp))

        // The file name gets its own line: through the button label it was
        // re-cased and clipped (audit, 2026-09-27).
        if (yourGames.isNotEmpty() && romName == null) {
            Text("From your games", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Gen3.Ink)
            Spacer(Modifier.height(4.dp))
            yourGames.forEach { e ->
                LibraryPickRow(stripKnownExt(e.name), e.kind?.displayName ?: "", onClick = { if (!busy) pickFromLibrary(e) })
            }
            Spacer(Modifier.height(10.dp))
            Text("Or from this phone's files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Gen3.Ink)
            Spacer(Modifier.height(4.dp))
        }
        Gen3Button(if (romName == null) "CHOOSE A GAME FILE" else "CHOOSE ANOTHER", enabled = !busy) {
            pickRom.launch(arrayOf("*/*"))
        }
        romName?.let { n ->
            Spacer(Modifier.height(6.dp))
            Text(n, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }

        // Only an exact copy of a game the tracker reads can be set up. Anything else is said as soon as it is
        // read, with what it is and where it plays, and Prepare stays off (audit, 2026-09-27; UX audit P0-11, 2026-09-30).
        val exact = romId?.exact == true
        romId?.let { id ->
            Spacer(Modifier.height(8.dp))
            Text(
                id.summary,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    id.verdict == RomIdentity.Verdict.DAMAGED || id.verdict == RomIdentity.Verdict.NOT_A_GAME -> Shell.dangerOnPaper
                    exact -> Shell.goodOnPaper
                    else -> Shell.hintOnPaper
                },
            )
            notSetUpHereLine(id.verdict)?.let { line ->
                Spacer(Modifier.height(4.dp))
                Text(line, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            }
            // The button for the place it names (UX audit P0-13): a file that plays goes in under My games.
            if (playsWithoutSetup(id.verdict)) {
                Spacer(Modifier.height(8.dp))
                Gen3Button("OPEN MY GAMES", enabled = !busy) { onMyGames() }
            }
            // Blake, 2026-09-07: "the prepare screen will be different and unique
            // to each rom." What this game gets, in its own words.
            id.kind?.takeIf { exact }?.let { k ->
                Spacer(Modifier.height(6.dp))
                PrepPlan.lines(k).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                }
            }
        }

        romId?.kind?.takeIf { exact }?.let { k ->
            val options = PrepOptions.forKind(k)
            if (options.size > 1) {
                Spacer(Modifier.height(14.dp))
                val current = options.firstOrNull { it.id == chosenOption } ?: PrepOptions.default(k)
                // The whole row is the touch target, not just the radio circle. A label
                // that ignores taps is the bug the emulator test caught on 2026-08-30.
                Column {
                    options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosenOption = o.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShellRadio(current.id == o.id)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(o.label, fontWeight = if (current.id == o.id) FontWeight.SemiBold else FontWeight.Normal)
                                Text(PrepOptions.describe(o, k), style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                            }
                        }
                    }
                }
            }
        }

        // Picking Nat. Dex says what it adds before anything is made (Blake, 2026-09-30).
        romId?.kind?.takeIf { exact }?.let { k ->
            val picked = PrepOptions.forKind(k).firstOrNull { it.id == chosenOption } ?: PrepOptions.default(k)
            if (picked.mode == PrepOptions.Mode.NATDEX) {
                Spacer(Modifier.height(10.dp))
                Text(NatDexInfo.WHAT, style = MaterialTheme.typography.titleSmall, color = Gen3.Ink)
                NatDexInfo.lines(k).forEach { line ->
                    Text("\u2022 $line", style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper, modifier = Modifier.padding(vertical = 1.dp))
                }
                Text(NatDexInfo.CREDIT, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            }
        }

        Spacer(Modifier.height(14.dp))
        if (busy) {
            // The pick shows its bytes; PREPARE shows the patch phase.
            if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ProgressPanel(RunPhase.PATCHING)
            Spacer(Modifier.height(14.dp))
        }

        Gen3Button("PREPARE", enabled = !busy && exact, accent = true) {
            prepare()
        }
        // A disabled main button says why (UX audit P0-10, 2026-09-30).
        if (!busy && !exact) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (romId == null) "Choose a game file first." else "This file cannot be set up here. See above.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
        }

        if (needPatchImport) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Gen3Button("Import the Nat. Dex patch", enabled = !busy) {
                    pickPatch.launch(arrayOf("*/*"))
                }
                Gen3Button("Open the release page", enabled = !busy) {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(NATDEX_RELEASES))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "No browser found to open the page.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
            }
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            StatusBanner(it, isError = messageIsError)
        }

        // What is already prepared. A ROM is patched and stored ONCE, but the
        // screen showed no sign of that, so it read as though every session had
        // to go and find the file again. These are the ones the Run tab will
        // offer; nothing here needs re-picking. Read off the main thread: it
        // hashes a newly stored file, seconds on a DS game (audit, 2026-09-27).
        val prepared by produceState<List<Pair<RomKind, java.io.File>>?>(null, message, busy) {
            if (!busy) value = withContext(Dispatchers.IO) { runCatching { store.listPrepared() }.getOrDefault(emptyList()) }
        }
        Spacer(Modifier.height(14.dp))
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Already prepared",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (prepared == null) {
                    ShellBusy()
                } else if (prepared!!.isEmpty()) {
                    Text(
                        "Nothing yet. A game you make here is kept for good.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                } else {
                    prepared!!.forEach { (kind, file) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                kind.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "%.0f MB".format(file.length() / 1048576.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = Shell.hintOnPaper,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick one in Kaizo IronMON to randomize it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                }
            }
        }
    }
}

/** A zip with no game inside. */
private class NoRomInZip : Exception()

/** A .7z or .rar, which KaizoCore does not open ([kind] is ".7z" or ".rar"). */
private class ArchiveNotOpened(val kind: String) : Exception()

/**
 * What to do with a file this page cannot set up, in one line under what it is; null where what it is already says
 * (a damaged DS file has its own next step). "Patched versions" only works on an exact copy of a game the tracker
 * reads, and a game that is not that still plays (UX audit P0-11, 2026-09-30).
 */
internal fun notSetUpHereLine(v: RomIdentity.Verdict): String? = when (v) {
    RomIdentity.Verdict.EXACT, RomIdentity.Verdict.DAMAGED -> null
    RomIdentity.Verdict.NOT_A_GAME -> "Choose a Game Boy, Game Boy Advance or DS game file, or a .zip holding one."
    else -> "This page only works on an exact copy of a game the tracker reads. To play this one, add it under My games. To make a ROM hack, use ROM Hacks on Home."
}

/** A file that is not set up here but plays, so it gets the button to My games. */
internal fun playsWithoutSetup(v: RomIdentity.Verdict): Boolean = when (v) {
    RomIdentity.Verdict.EXACT, RomIdentity.Verdict.DAMAGED, RomIdentity.Verdict.NOT_A_GAME -> false
    else -> true
}

/** Where the Nat. Dex patches are published. */
private const val NATDEX_RELEASES = "https://github.com/CyanSMP64/NatDexExtension"

/** What a failed pick says to the player: never an exception's own text (audit, 2026-09-27). */
private fun readFailure(t: Throwable): String = when {
    t is ArchiveNotOpened -> LibraryImport.archiveLine(t.kind, verb = "choose")
    t is NoRomInZip -> "That zip has no game inside. Pick the .gba, .gbc, .gb or .nds file, or a zip holding one."
    isNoSpace(t) -> "Not enough free space on this phone to read that file. Free some space and try again."
    t is OutOfMemoryError -> "This phone ran out of memory reading that file. Close other apps and try again."
    else -> "Could not read that file. Pick it again, or copy it onto this phone first."
}

