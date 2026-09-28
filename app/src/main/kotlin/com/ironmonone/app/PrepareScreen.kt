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
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrepareScreen(modifier: Modifier = Modifier) {
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

    fun prepare() {
        val file = romFile ?: return
        val id = romId ?: return
        val kind = id.kind ?: return
        // A game recognised by its title but whose exact copy the app has not
        // checked yet (Pokemon Black, today) cannot be listed on RUN: the
        // tracker's addresses are per build and RUN only offers checked ones.
        // PREP used to store it anyway and say "Ready on the RUN tab", which
        // was never true (audit, 2026-09-27).
        if (kind.expectedCrc == com.ironmonone.core.RomKind.CRC_UNKNOWN) {
            say("This copy of ${kind.displayName} has not been checked by the app yet, so it cannot be randomized here. " +
                "It plays from Library, All files, without the tracker. Nothing was changed.", error = true)
            return
        }
        busy = true; message = null
        // A stale phase from the pick used to label this job; each mode names its own (audit, 2026-09-27).
        progress.clear()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val options = PrepOptions.forKind(kind)
                    val opt = options.firstOrNull { it.id == chosenOption } ?: options.first()
                    when (opt.mode) {
                        PrepOptions.Mode.STANDARD -> {
                            progress.start("Saving", 0L)
                            (if (kind.isNatDex || kind.patchTag != null) "Already patched. Stored as is." else "Stored as a standard (vanilla) base.") to
                                store.savePrepared(kind, file)
                        }

                        PrepOptions.Mode.PATCH -> {
                            // Every failure below is worded for the player; none of them passes on an exception's own text (audit, 2026-09-27).
                            val outKind = opt.out ?: throw PrepFailure(NOT_IN_THIS_BUILD)
                            val patchFile = store.bundledPatch(context, opt.asset ?: throw PrepFailure(NOT_IN_THIS_BUILD))
                                ?: throw PrepFailure(NOT_IN_THIS_BUILD)
                            val tmp = java.io.File(context.cacheDir, "prep-patched-${outKind.id}.${outKind.fileExtension}")
                            progress.start("Patching", 0L)
                            val crc = Patcher.applyFiles(patchFile, file, tmp, kind.displayName) { done, total -> progress.done = done; progress.total = total }
                            if (outKind.expectedCrc != RomKind.CRC_UNKNOWN && crc != outKind.expectedCrc) {
                                tmp.delete()
                                throw PrepFailure("The patch applied, but the result is not a version this app knows. " +
                                    "Your dump is probably a different version of the game. Nothing was changed.")
                            }
                            file.delete()
                            "Patched to ${outKind.displayName}." to store.savePrepared(outKind, tmp)
                        }

                        PrepOptions.Mode.NATDEX -> {
                            // Bundled patch is used unless the user imported one.
                            val patchFile = store.patchFileOrBundled(context, kind)
                                ?: throw NeedPatch()
                            progress.start("Patching", 0L)
                            val out = Patcher.apply(patchFile.readBytes(), file.readBytes(), kind.displayName)
                            val outKind = RomKind.allNatDex.firstOrNull {
                                it.expectedCrc == com.ironmonone.patch.Crc32.of(out)
                            } ?: throw PrepFailure(
                                "The patch applied, but the result is not a Nat. Dex version this app knows. " +
                                    "A newer Nat. Dex release needs an update of this app first. Nothing was changed.")
                            "Patched to ${outKind.displayName}." to
                                store.savePrepared(outKind, out)
                        }
                    }
                }
            }.onSuccess { (msg, _) -> say("$msg Ready on the Run tab."); romFile = null; romId = null }
                .onFailure {
                    if (it is NeedPatch) {
                        // Not an error: a one-time step, with where to get the file (audit, 2026-09-27).
                        needPatchImport = true
                        say("KaizoCore needs the National Dex patch for this game once. " +
                            "It comes from the Nat. Dex Extension release page. Download the .bps for your game there, then import it here.")
                    } else say(prepFailure(it), error = true)
                }
            progress.clear(); busy = false
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
        // Why this page exists, before anything else (audit, 2026-09-27).
        Text(
            "Makes a game ready for the Run tab: checks your dump and, if you pick a ruleset patch, applies it. " +
                "A clean dump added in All files is already ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gen3.Ink,
        )
        Spacer(Modifier.height(14.dp))

        // The file name gets its own line: through the button label it was
        // re-cased and clipped (audit, 2026-09-27).
        Gen3Button(if (romName == null) "CHOOSE ROM" else "CHOOSE ANOTHER", enabled = !busy) {
            pickRom.launch(arrayOf("*/*"))
        }
        romName?.let { n ->
            Spacer(Modifier.height(6.dp))
            Text(n, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }

        // A game named by its header but whose exact copy is unchecked: said
        // as soon as it is read, with Prepare off, not after a tap (audit, 2026-09-27).
        val unchecked = romId?.kind?.expectedCrc == RomKind.CRC_UNKNOWN
        romId?.let { id ->
            Spacer(Modifier.height(8.dp))
            Text(
                id.summary,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !id.recognised -> Shell.dangerOnPaper
                    unchecked -> Shell.hintOnPaper
                    else -> Shell.goodOnPaper
                },
            )
            if (!id.recognised) {
                Spacer(Modifier.height(4.dp))
                Text("To play it anyway, add it in Library, All files. To make a hack, use the Hacks tab.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            } else if (unchecked) {
                Spacer(Modifier.height(4.dp))
                Text("This copy cannot be randomized here yet. It plays from Library, All files, without the tracker.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
            }
            // Blake, 2026-09-07: "the prepare screen will be different and unique
            // to each rom." What this game gets, in its own words.
            id.kind?.takeIf { !unchecked }?.let { k ->
                Spacer(Modifier.height(6.dp))
                PrepPlan.lines(k).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                }
            }
        }

        romId?.kind?.takeIf { !unchecked }?.let { k ->
            val options = PrepOptions.forKind(k)
            if (options.size > 1) {
                Spacer(Modifier.height(14.dp))
                val current = options.firstOrNull { it.id == chosenOption } ?: options.first()
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
                                Text(PrepOptions.describe(o), style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (busy) {
            // The pick shows its bytes; PREPARE shows the patch phase.
            if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ProgressPanel(RunPhase.PATCHING)
            Spacer(Modifier.height(14.dp))
        }

        Gen3Button("PREPARE", enabled = !busy && romId?.recognised == true && !unchecked, accent = true) {
            prepare()
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
                        "Nothing yet. Add a ROM above and it is kept for good.",
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
                        "Pick one on the Run tab to randomize it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                }
            }
        }
    }
}

private class NeedPatch : Exception()

/** A failure already worded for the player. */
private class PrepFailure(message: String) : Exception(message)

/** A zip with no game inside. */
private class NoRomInZip : Exception()

private const val NOT_IN_THIS_BUILD = "This option is not part of this version of the app. Pick another one."

/** Where the Nat. Dex patches are published. */
private const val NATDEX_RELEASES = "https://github.com/CyanSMP64/NatDexExtension"

private fun isNoSpace(t: Throwable): Boolean =
    generateSequence(t) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

/** What a failed pick says to the player: never an exception's own text (audit, 2026-09-27). */
private fun readFailure(t: Throwable): String = when {
    t is NoRomInZip -> "That zip has no game inside. Pick the .gba, .gbc, .gb or .nds file, or a zip holding one."
    isNoSpace(t) -> "Not enough free space on this phone to read that file. Free some space and try again."
    t is OutOfMemoryError -> "This phone ran out of memory reading that file. Close other apps and try again."
    else -> "Could not read that file. Pick it again, or copy it onto this phone first."
}

/** What a failed Prepare says to the player. */
private fun prepFailure(t: Throwable): String = when {
    t is PrepFailure -> t.message ?: "Setting up the game did not work. Nothing was changed."
    isNoSpace(t) -> "Not enough free space on this phone to save the game. Free some space and try again."
    else -> patchFailure(t)
}
