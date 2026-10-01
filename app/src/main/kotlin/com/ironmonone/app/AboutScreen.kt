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
/** The Game Boy trackers' references (NOTICE): Red, Blue and Yellow; Crystal. */
private const val GEN1_TRACKER_URL = "https://github.com/mollo010/Ironmon-gen-tracker"
private const val GEN2_TRACKER_URL = "https://github.com/seadogstingray/Ironmon-gen-2-tracker"
/** IronMon Emu, where the FireRed and LeafGreen pictures come from (NOTICE). */
private const val IRONMON_EMU_URL = "https://github.com/billgreenwald/ironmon_emu"

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
fun AboutScreen(modifier: Modifier = Modifier, onStats: () -> Unit = {}) {
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
                    "2" to "Home, Kaizo IronMON: pick a mode and start a new run. Every run is a new game.",
                    "3" to "Play: play it. The tracker fills in as you go. When a run ends, start the next one in Kaizo IronMON.",
                )) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(n, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.sp, color = Shell.inkOnPaper,
                            modifier = Modifier.width(22.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Library also keeps all your files. ROM Hacks on Home turns a game you own into a ROM hack. " +
                    "More, Controls sets up a controller or keyboard.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Your stats (2026-09-30): the way to it from More. It opens on Home, where Back returns (AppNav.openStats).
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(HomeCopy.STATS_LINK, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text(HomeCopy.STATS_CARD_LINE, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(10.dp))
                com.ironmonone.app.gen3.Gen3Button(HomeCopy.STATS_OPEN, Modifier.fillMaxWidth()) { onStats() }
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
                    // Send report goes through the site now, and Share instead is the old
                    // share sheet (CrashReportUi, 2026-09-29).
                    Text(
                        CrashReportText.INFO_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                    Spacer(Modifier.height(10.dp))
                    CrashCardActions(text, onDismiss = { CrashLog.clear(context); crash = null })
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // CRASH REPORTS. The one switch behind "Always send"; off until the player turns it on.
        CrashReportsCard()
        Spacer(Modifier.height(10.dp))

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

        UpdatesCard()
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
                Text("One zip of your save states and screenshots, battery saves, the current run and its notes " +
                    "(its randomizer log once the run is over), attempts, presets, key bindings, layouts, cheats and settings. " +
                    "Your library games are never in it; add them again in Library, My games. The current run's " +
                    "randomized game is, so a restored run comes back whole. Restoring overwrites what is here.",
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
                    Text("Linked to ${l.provider}. Last synced: ${CloudSync.whenLabel(l.lastSync)}. Syncs when you leave a game; your library games never go up.",
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
            "Nat. Dex support uses the Pok\u00e9mon and move data, sprites and settings files from the " +
                "Nat. Dex Extension by CyanSixFour (CyanSMP64), used with permission.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(NATDEX_URL) { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "The quality-of-life patches are Faster FireRed and Faster Emerald by DrMaple, " +
                "and Faster Black 2 / White 2 by SilverstarStream. " +
                "They are bundled so a run can be prepared without hunting for the files.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(FASTER_URL) { openOrSay(it) }
        CreditLink("https://github.com/DrMaple/Faster-Emerald") { openOrSay(it) }
        CreditLink("https://github.com/SilverstarStream/faster_black2_white2") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        // Every mode the app offers comes from these, and NOTICE lists each file.
        Text(
            "The IronMON rules are iateyourpie's, from the rules gist kept by valiant-code, and the official " +
                "settings are UTDZac's. The other modes are Super Kaizo (iateyourpie, kept by PyroMikeGit), " +
                "Survival Revival (SaltyDolphin and Reimi), IronMON Journey (PappyQC, settings by UTDZac), " +
                "Chaos Kaizo (UTDZac) and Evo Kaizo (a pastebin that names no author). Their rules text and " +
                "settings files are bundled from these sources.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://gist.github.com/valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1") { openOrSay(it) }
        CreditLink("https://gist.github.com/UTDZac/a147c497424dfbd537d8c4b0c22b5621") { openOrSay(it) }
        CreditLink("https://github.com/PyroMikeGit/SuperKaizoIronMON") { openOrSay(it) }
        CreditLink("https://github.com/Reimittv/SurvivalRevivalIronMON") { openOrSay(it) }
        CreditLink("https://gist.github.com/PappyQC/b9e28068ba4abbbdf191dd33625b737d") { openOrSay(it) }
        CreditLink("https://gist.github.com/UTDZac/7c51734eed353779f1653217413dd05e") { openOrSay(it) }
        CreditLink("https://gist.github.com/UTDZac/c8c3a84553840f8eabb063be80a33ee7") { openOrSay(it) }
        CreditLink("https://pastebin.com/puyAqPyt") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "The mode patches are the pseudo-fluctuating growth patches from the Kaizo run site, Smart AI " +
                "by tom-overton (FireRed, LeafGreen) and by CyanSixFour (Emerald), HeartGold Super Kaizo by " +
                "PyroMikeGit, and Platinum Super Kaizo by SentorG.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://www.stealmylyrics.com/kaizo/patch/") { openOrSay(it) }
        CreditLink("https://github.com/tom-overton/pokefirered/releases/tag/smart-ai-v2") { openOrSay(it) }
        CreditLink("https://github.com/CyanSMP64/Emerald_Smart_AI") { openOrSay(it) }
        CreditLink("https://github.com/SentorG/PlatinumSuperKaizo") { openOrSay(it) }

        Spacer(Modifier.height(16.dp))

        Text(
            "Tracker behaviour derives from the IronMON Tracker by besteon and " +
                "contributors, used under the MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(TRACKER_URL) { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "The Game Boy trackers follow the Gen 1 IronMON Tracker by Sannji (mollo010) and " +
                "the Gen 2 IronMON Tracker by seadogstingray, forks of besteon's, used under " +
                "the MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(GEN1_TRACKER_URL) { openOrSay(it) }
        CreditLink(GEN2_TRACKER_URL) { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        // Added 2026-09-29 with the FireRed and LeafGreen pictures (NOTICE): permission for the
        // pictures only, from IronMon Emu's author. None of its code is used. Seven maps ship since 2026-09-30.
        Text(
            "The FireRed and LeafGreen dungeon maps are by Bill Greenwald (doctrDNA), " +
                "from IronMon Emu, used with permission.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink(IRONMON_EMU_URL) { openOrSay(it) }

        Spacer(Modifier.height(16.dp))

        Text(
            "The animated Pok\u00e9mon (the Walking Pals icon set) are sprites from the PMD Sprite " +
                "Collab, by its artists, used under the Creative Commons BY-NC 4.0 licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://sprites.pmdcollab.org") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        // Play as your Pokemon (2026-09-29): UTDZac's Sprite Is Me, drawing the sprites above.
        Text(
            "Play as your Pok\u00e9mon follows Sprite Is Me, the Ironmon Tracker extension by UTDZac, used under " +
                "the MIT licence, and draws the Walking Pals sprites above.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://github.com/UTDZac/SpriteIsMe-IronmonExtension") { openOrSay(it) }

        Spacer(Modifier.height(16.dp))

        // Added 2026-09-29 (the rc30 credits check): the DS tracker, the two Gen 3 extensions
        // ported, the randomizer, the cores and the font, which were in NOTICE or nowhere.
        Text(
            "The DS trackers follow the NDS IronMON Tracker by Brian0255, used under the GPL-3.0 " +
                "licence. The damage calculator is Calc Atk by UTDZac and the Auto Pokémon Themes " +
                "are Fellshadow's, both used under the MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://github.com/Brian0255/NDS-Ironmon-Tracker") { openOrSay(it) }
        CreditLink("https://github.com/UTDZac/CalcAtk-IronmonExtension") { openOrSay(it) }
        CreditLink("https://github.com/Fellshadow/Ironmon-Tracker-AutoPokemonThemes") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "Runs are randomized with the Universal Pokémon Randomizer ZX by Ajarmar, built on " +
                "Dabomstew's Universal Pokémon Randomizer, and Nat. Dex runs with CyanSixFour's fork " +
                "of it, all used under the GPL-3.0 licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://github.com/Ajarmar/universal-pokemon-randomizer-zx") { openOrSay(it) }
        CreditLink("https://github.com/CyanSMP64/universal-pokemon-randomizer-zx") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        Text(
            "Games run on the mGBA, melonDS and Gambatte cores through LibretroDroid, and the " +
                "on-screen controls are RadialGamePad, both by Swordfish90. RetroAchievements " +
                "uses rcheevos by RetroAchievements.org, under the MIT licence. The pixel font is " +
                "Press Start 2P by CodeMan38, under the SIL Open Font Licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://github.com/Swordfish90/LibretroDroid") { openOrSay(it) }
        CreditLink("https://github.com/Swordfish90/RadialGamePad") { openOrSay(it) }
        CreditLink("https://github.com/RetroAchievements/rcheevos") { openOrSay(it) }

        Spacer(Modifier.height(12.dp))

        // Added 2026-09-30 with the game over lines (NOTICE): the idea is the Death Quotes extension's.
        Text(
            "Your own game over lines follow the Death Quotes extension by UTDZac, used under the " +
                "MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CreditLink("https://github.com/UTDZac/DeathQuotes-IronmonExtension") { openOrSay(it) }

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
