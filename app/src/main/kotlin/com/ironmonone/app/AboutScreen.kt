package com.ironmonone.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val NATDEX_URL = "https://github.com/CyanSMP64/NatDexExtension"
private const val FASTER_URL = "https://github.com/DrMaple/Faster-FireRed"
private const val TRACKER_URL = "https://github.com/besteon/Ironmon-Tracker"

/**
 * Credits and disclaimer.
 *
 * Two lines here are BINDING, not decoration. CyanSMP64 granted permission to use the
 * Nat. Dex data and sprites on condition that he is credited with a link in the app's
 * About screen, and that nothing implies this is an official release. See NOTICE.
 * Do not remove either without re-reading that grant.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // A link with no browser to open it crashed the app (audit, 2026-09-27).
    // False when nothing could open it; the caller says so where it was tapped.
    fun open(url: String): Boolean =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    var linkStatus by remember { mutableStateOf<String?>(null) }
    fun openOrSay(url: String) { linkStatus = if (open(url)) null else "No browser on this phone." }

    // Collected once per visit, from Android's own process-exit history.
    // A native crash leaves nothing behind in the app itself, so this is the
    // only way to see one without plugging the phone into a computer. Only
    // crashes and freezes from the last 7 days (CrashLog), not Android freeing
    // memory, which kept this card up for good (audit, 2026-09-27).
    var crash by remember { mutableStateOf(CrashLog.collect(context) ?: CrashLog.existing(context)) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
    ) {
        // What the app is and how to use it, first. INFO had no help at all:
        // a newcomer met jargon on PREP and nothing to explain it (audit,
        // 2026-09-27). The steps name the real tabs.
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("How it works", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text("KaizoCore plays IronMON on your phone: a randomized Pokemon game with a tracker beside it. " +
                    "Lose the run and you start again with a brand new game.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(8.dp))
                for ((n, line) in listOf(
                    "1" to "Library: add your own game file. KaizoCore never downloads games.",
                    "2" to "Run: pick a mode and start a new run. Every run is a new game.",
                    "3" to "Play: play it. The tracker fills in as you go. When a run ends, start the next one on Run.",
                )) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(n, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.sp, color = Shell.inkOnPaper,
                            modifier = Modifier.width(22.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Library also keeps all your files. Hacks turns a game you own into a ROM hack. " +
                    "More, Controls sets up a controller or keyboard.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            }
        }
        Spacer(Modifier.height(10.dp))
        crash?.let { text ->
            com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Last session ended unexpectedly",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp,
                        color = Shell.dangerOnPaper,
                    )
                    Spacer(Modifier.height(6.dp))
                    // One plain sentence; the raw report lines go only into
                    // the shared report (audit, 2026-09-27).
                    Text(
                        CrashLog.whenLabel(text)?.let { "KaizoCore closed unexpectedly on $it." }
                            ?: "KaizoCore closed unexpectedly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Shell.inkOnPaper,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap Send report and share it however is easiest. It says " +
                            "where the app failed, which is what makes a crash " +
                            "fixable rather than guessed at.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        com.ironmonone.app.gen3.Gen3Button("Send report", accent = true, onClick = {
                            runCatching {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "KaizoCore crash report")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, "Send crash report"))
                            }
                        })
                        com.ironmonone.app.gen3.Gen3Button("Dismiss", onClick = {
                            CrashLog.clear(context); crash = null
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // BETA FEEDBACK. Composed here, sent through the share sheet: no
        // account, no server, no ROM. The outward buttons appear only once
        // their links exist (Feedback.Links), so nothing invented ships.
        var feedbackWords by remember { mutableStateOf("") }
        var feedbackStatus by remember { mutableStateOf<String?>(null) }
        // Failures show in the danger colour; every status was green like a
        // success (audit, 2026-09-27). Same for backup and cloud below.
        var feedbackError by remember { mutableStateOf(false) }
        val store = remember { PrepStore(context) }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("Report a bug", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text("Something in your way? Say what happened and where, then tap Email Blake. The report carries your device, Android and app " +
                    "versions, the game family, the last crash if there was one, and the app's own log lines. Never a ROM, a save or a file name.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    feedbackWords, { feedbackWords = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text("What happened") },
                    placeholder = { Text("What happened, what you expected, and the steps") },
                    // The unfocused border was near invisible on the card.
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper),
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("Email Blake", accent = true) {
                        val family = store.loadLastRun()?.first?.let { com.ironmonone.core.RomKind.byId(it)?.family }
                        val device = Feedback.device(context)
                        val text = Feedback.compose(device, family, feedbackWords, Feedback.logTail(), CrashLog.existing(context))
                        runCatching {
                            context.startActivity(Feedback.emailIntent("KaizoCore bug (${device.appVersion})", text))
                            feedbackStatus = "Mail composed to ${Feedback.Links.EMAIL} (${text.lines().size} lines). Send it from your mail app."
                            feedbackError = false
                        }.onFailure { feedbackStatus = "No mail app on this phone. Use Share instead and pick anything that reaches ${Feedback.Links.EMAIL}."; feedbackError = true }
                    }
                    com.ironmonone.app.gen3.Gen3Button("Share instead") {
                        val family = store.loadLastRun()?.first?.let { com.ironmonone.core.RomKind.byId(it)?.family }
                        val text = Feedback.compose(Feedback.device(context), family, feedbackWords, Feedback.logTail())
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "KaizoCore beta feedback")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Send feedback"))
                            feedbackStatus = "Report composed (${text.lines().size} lines). Pick where to send it."
                            feedbackError = false
                        }.onFailure { feedbackStatus = "No app on this phone can receive it."; feedbackError = true }
                    }
                    fun link(url: String) { if (!open(url)) { feedbackStatus = "No browser on this phone."; feedbackError = true } }
                    if (Feedback.Links.BUG_FORM.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("Bug form") { link(Feedback.Links.BUG_FORM) }
                    if (Feedback.Links.RELEASES.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("Latest build") { link(Feedback.Links.RELEASES) }
                    if (Feedback.Links.SUPPORT.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("Support the project") { link(Feedback.Links.SUPPORT) }
                }
                feedbackStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = if (feedbackError) Shell.dangerOnPaper else Shell.goodOnPaper) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // BACKUP. One zip of saves, states, notes, runs and settings - never
        // ROMs. Both directions go through the system file picker, so the
        // file lands wherever the player chooses (Drive, Downloads, a mail).
        var backupStatus by remember { mutableStateOf<String?>(null) }
        var backupError by remember { mutableStateOf(false) }
        // Back up and restore ran with nothing on screen and stayed tappable,
        // so a second tap started a second restore (audit, 2026-09-27).
        var backupBusy by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            backupBusy = true
            scope.launch {
                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context.filesDir, it) } }.getOrNull()
                }
                backupBusy = false
                backupStatus = if (n != null) "Backed up $n files." else "Could not write the backup."
                backupError = n == null
            }
        }
        // Settings are read when the app starts, so a restore is finished by a restart.
        var needsRestart by remember { mutableStateOf(false) }
        var restoreFrom by remember { mutableStateOf<android.net.Uri?>(null) }
        val importer = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) restoreFrom = uri }
        restoreFrom?.let { uri ->
            ShellDialog("Restore from this file?", onDismiss = { restoreFrom = null }) {
                Column {
                    Text("Your save states, battery saves, runs, notes and settings on this phone are replaced with the ones in the file. ROMs are untouched.",
                        style = MaterialTheme.typography.bodyMedium, color = com.ironmonone.app.gen3.Gen3.Ink)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        com.ironmonone.app.gen3.Gen3Button("Restore", accent = true, enabled = !backupBusy) {
                            restoreFrom = null
                            backupBusy = true
                            scope.launch {
                                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.read(context.filesDir, it) } }.getOrNull()
                                }
                                backupBusy = false
                                backupError = n == null || n < 0
                                backupStatus = when {
                                    n == null -> "Could not read that file."
                                    n < 0 -> "That is not a KaizoCore backup. Nothing was changed."
                                    else -> "Restored $n files. Restart KaizoCore to finish."
                                }
                                if (n != null && n >= 0) needsRestart = true
                            }
                        }
                        com.ironmonone.app.gen3.Gen3Button("Cancel") { restoreFrom = null }
                    }
                }
            }
        }
        // Once, above both cards: after a restore it showed on each (audit, 2026-09-27).
        if (needsRestart) {
            com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
                Column {
                    Text("Restore finished. Restart KaizoCore to use it.", style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(8.dp))
                    com.ironmonone.app.gen3.Gen3Button("Restart now", accent = true) { restartApp(context) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("Backup", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text("One zip of your save states and screenshots, battery saves, the current run and its notes, " +
                    "attempts, presets, key bindings, layouts, cheats and settings. ROMs are never included; re-add " +
                    "those in Library, All files. Restoring overwrites what is here.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    com.ironmonone.app.gen3.Gen3Button("Back up", accent = true, enabled = !backupBusy) { exporter.launch(Backup.suggestedName()) }
                    com.ironmonone.app.gen3.Gen3Button("Restore", enabled = !backupBusy) { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                }
                if (backupBusy) ShellBusy()
                backupStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = if (backupError) Shell.dangerOnPaper else Shell.goodOnPaper) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // CLOUD SYNC. The same zip, kept up to date in one document the
        // player picked on Drive (or any provider) - see CloudSync.
        var cloudLink by remember { mutableStateOf(CloudSync.load(context.filesDir)) }
        var cloudStatus by remember { mutableStateOf<String?>(null) }
        var cloudError by remember { mutableStateOf(false) }
        // Unlink is two taps, disarmed after 3 s (audit, 2026-09-27).
        var unlinkArmed by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(unlinkArmed) { if (unlinkArmed) { kotlinx.coroutines.delay(3000); unlinkArmed = false } }
        var cloudBusy by remember { mutableStateOf(false) }
        var confirmCloudRestore by remember { mutableStateOf(false) }
        // Linked to an EXISTING file to restore from it. Until that restore
        // happens nothing may be written to it, or the empty new phone would
        // overwrite the good copy; cancelling unlinks.
        var linkedToRestore by remember { mutableStateOf(false) }
        // A sync started by leaving a game finishes after this screen is up,
        // so the label re-reads the (tiny) link file while it is showing.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) { kotlinx.coroutines.delay(1500); val l = CloudSync.load(context.filesDir); if (l != cloudLink) cloudLink = l }
        }
        fun describe(r: CloudSync.Result): String = when (r) {
            CloudSync.Result.NotLinked -> "Not linked."
            CloudSync.Result.Unchanged -> "Nothing changed since the last sync."
            is CloudSync.Result.Written -> "Synced ${r.files} files."
            is CloudSync.Result.Failed -> r.reason
        }
        val linker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            cloudLink = CloudSync.link(context, uri)
            cloudBusy = true
            scope.launch {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CloudSync.sync(context, force = true) }
                cloudLink = CloudSync.load(context.filesDir); cloudBusy = false
                cloudStatus = "Linked to ${cloudLink?.provider}. " + describe(r)
                cloudError = r is CloudSync.Result.Failed
            }
        }
        val existingLinker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            cloudLink = CloudSync.link(context, uri)
            linkedToRestore = true
            confirmCloudRestore = true
        }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("Cloud sync", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                val l = cloudLink
                if (l == null) {
                    Text("Keep your backup in Google Drive by itself. Start a sync file where you choose (pick Drive in the " +
                        "picker); after that it is updated every time you leave a game. Moving to a new phone? Use the file " +
                        "you already have instead, and your saves come back.",
                        style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        com.ironmonone.app.gen3.Gen3Button("Start a new sync file", accent = true, enabled = !cloudBusy) { linker.launch(CloudSync.SUGGESTED_NAME) }
                        com.ironmonone.app.gen3.Gen3Button("Use my file from another phone", enabled = !cloudBusy) {
                            existingLinker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    }
                } else {
                    Text("Linked to ${l.provider}. Last synced: ${CloudSync.whenLabel(l.lastSync)}. Syncs when you leave a game; ROMs never go up.",
                        style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        com.ironmonone.app.gen3.Gen3Button("Sync now", accent = true, enabled = !cloudBusy) {
                            cloudBusy = true
                            scope.launch {
                                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CloudSync.sync(context, force = true) }
                                cloudLink = CloudSync.load(context.filesDir); cloudBusy = false; cloudStatus = describe(r)
                                cloudError = r is CloudSync.Result.Failed
                            }
                        }
                        com.ironmonone.app.gen3.Gen3Button("Restore from cloud", enabled = !cloudBusy) { confirmCloudRestore = true }
                        com.ironmonone.app.gen3.Gen3Button(if (unlinkArmed) "Sure? Unlink" else "Unlink", accent = unlinkArmed, enabled = !cloudBusy) {
                            if (unlinkArmed) {
                                CloudSync.unlink(context); cloudLink = null; unlinkArmed = false
                                cloudStatus = "Unlinked. The file stays where it is."; cloudError = false
                            } else unlinkArmed = true
                        }
                    }
                }
                if (cloudBusy) ShellBusy()
                cloudStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = if (cloudError) Shell.dangerOnPaper else Shell.goodOnPaper) }
            }
        }
        fun cancelCloudRestore() {
            confirmCloudRestore = false
            if (linkedToRestore) {
                CloudSync.unlink(context); cloudLink = null; linkedToRestore = false
                cloudStatus = "Not linked. Nothing was changed."; cloudError = false
            }
        }
        if (confirmCloudRestore) {
            ShellDialog("Restore from cloud?", onDismiss = { cancelCloudRestore() }) {
                Column {
                    Text("Overwrites the saves, states, runs and settings on this phone with the synced copy. ROMs are untouched.",
                        style = MaterialTheme.typography.bodyMedium, color = com.ironmonone.app.gen3.Gen3.Ink)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        com.ironmonone.app.gen3.Gen3Button("Restore", accent = true, enabled = !cloudBusy) {
                            confirmCloudRestore = false; cloudBusy = true
                            scope.launch {
                                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CloudSync.restore(context) }
                                cloudBusy = false
                                cloudError = n == null || n < 0
                                cloudStatus = when { n == null -> "Could not read the synced file."; n < 0 -> "The synced file is not a KaizoCore backup."; else -> "Restored $n files from the cloud. Restart KaizoCore to finish." }
                                if (n != null && n >= 0) { needsRestart = true; linkedToRestore = false }
                                else if (linkedToRestore) { CloudSync.unlink(context); cloudLink = null; linkedToRestore = false }
                            }
                        }
                        com.ironmonone.app.gen3.Gen3Button("Cancel") { cancelCloudRestore() }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
        Text("KaizoCore", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        // The version the package manager installed, never a string typed here:
        // "0.1.0" sat on this screen while the APK said 1.0.0-rc15.
        val versionName = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "?"
        }
        Text("Version $versionName", style = MaterialTheme.typography.bodySmall,
            color = Shell.inkOnPaper)

        Spacer(Modifier.height(20.dp))
        ShellDivider()
        Spacer(Modifier.height(20.dp))

        Text("Credits", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Text(
            "Nat. Dex support uses the Pokémon and move data and sprites from the " +
                "Nat. Dex Extension by CyanSixFour (CyanSMP64), used with permission.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(NATDEX_URL) { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "The FireRed quality-of-life patch is Faster FireRed by DrMaple. " +
                "It is bundled so a run can be prepared without hunting for the file.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(FASTER_URL) { openOrSay(it) }

        Spacer(Modifier.height(16.dp))

        Text(
            "Tracker behaviour derives from the IronMON Tracker by besteon and " +
                "contributors, used under the MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(TRACKER_URL) { openOrSay(it) }

        Spacer(Modifier.height(16.dp))

        Text(
            "The animated Pok\u00e9mon (the Walking Pals icon set) are sprites from the PMD Sprite " +
                "Collab, by its artists, used under the Creative Commons BY-NC 4.0 licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://sprites.pmdcollab.org") { openOrSay(it) }

        linkStatus?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
        }

        Spacer(Modifier.height(20.dp))
        ShellDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            "This is not an official IronMON or Nat. Dex release. It is an " +
                "unaffiliated, unendorsed personal project, and is not associated with " +
                "Nintendo, Game Freak or The Pokémon Company.",
            style = MaterialTheme.typography.bodyMedium,
            color = Shell.inkOnPaper,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "No game ROM is bundled, hosted or downloaded by this app. You supply your " +
                "own legally obtained dump.",
            style = MaterialTheme.typography.bodyMedium,
            color = Shell.inkOnPaper,
        )
            }
        }
    }
}

/**
 * A credit link: the URL, underlined, 48dp tall and announced as a button.
 * They were about 20dp tall with no role (audit, 2026-09-27).
 */
@Composable
private fun CreditLink(url: String, onOpen: (String) -> Unit) {
    Text(
        url,
        style = MaterialTheme.typography.bodySmall,
        // Yellow on paper measured 1.01:1 - luminance-invisible, surviving
        // on hue alone. Links on a card are ink plus the underline, which
        // also works without colour vision.
        color = Shell.linkOnPaper,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .heightIn(min = Shell.touchTarget)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Open link") { onOpen(url) }
            .wrapContentHeight(androidx.compose.ui.Alignment.CenterVertically),
    )
}

/** Relaunch the app so settings read at startup take the restored values. */
internal fun restartApp(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(android.content.Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
