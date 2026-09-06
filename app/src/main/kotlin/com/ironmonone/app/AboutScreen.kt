package com.ironmonone.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    fun open(url: String) =
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    // Collected once per visit, from Android's own process-exit history.
    // A native crash leaves nothing behind in the app itself, so this is the
    // only way to see one without plugging the phone into a computer.
    var crash by remember { mutableStateOf(CrashLog.collect(context) ?: CrashLog.existing(context)) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
    ) {
        crash?.let { text ->
            com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "LAST SESSION ENDED UNEXPECTEDLY",
                        fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 11.sp,
                        color = Shell.dangerOnPaper,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap SEND REPORT and share it however is easiest. It names " +
                            "the signal and the code that faulted, which is what " +
                            "makes a crash fixable rather than guessed at.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.inkOnPaper,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text.lineSequence().take(14)
                            .joinToString(Char(10).toString()),
                        fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 8.sp,
                        color = Shell.hintOnPaper,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        com.ironmonone.app.gen3.Gen3Button("SEND REPORT", accent = true, onClick = {
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
                        Spacer(Modifier.width(8.dp))
                        com.ironmonone.app.gen3.Gen3Button("DISMISS", onClick = {
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
        val store = remember { PrepStore(context) }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("BETA FEEDBACK", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 11.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text("Something in your way? Say what happened and where. The report carries your device, Android and app " +
                    "versions, the game family and the app's own log lines. Never a ROM, a save or a file name.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    feedbackWords, { feedbackWords = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("What happened, what you expected, and the steps") },
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("SEND FEEDBACK", accent = true) {
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
                        }.onFailure { feedbackStatus = "No app on this phone can receive it." }
                    }
                    if (Feedback.Links.BUG_FORM.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("BUG FORM") { open(Feedback.Links.BUG_FORM) }
                    if (Feedback.Links.RELEASES.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("LATEST BUILD") { open(Feedback.Links.RELEASES) }
                    if (Feedback.Links.SUPPORT.isNotBlank()) com.ironmonone.app.gen3.Gen3Button("SUPPORT THE PROJECT") { open(Feedback.Links.SUPPORT) }
                }
                feedbackStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.goodOnPaper) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // BACKUP. One zip of saves, states, notes, runs and settings - never
        // ROMs. Both directions go through the system file picker, so the
        // file lands wherever the player chooses (Drive, Downloads, a mail).
        var backupStatus by remember { mutableStateOf<String?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context.filesDir, it) } }.getOrNull()
                }
                backupStatus = if (n != null) "Backed up $n files." else "Could not write the backup."
            }
        }
        val importer = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.read(context.filesDir, it) } }.getOrNull()
                }
                backupStatus = when {
                    n == null -> "Could not read that file."
                    n < 0 -> "That is not a KaizoCore backup."
                    else -> "Restored $n files. Your saves, states and settings are back."
                }
            }
        }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("BACKUP", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 11.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                Text("One zip of your save states and screenshots, battery saves, the current run and its notes, " +
                    "attempts, presets, key bindings, layouts, cheats and settings. ROMs are never included; re-add " +
                    "those on the ROMs tab. Restoring overwrites what is here.",
                    style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("BACK UP", accent = true) { exporter.launch(Backup.suggestedName()) }
                    com.ironmonone.app.gen3.Gen3Button("RESTORE") { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                }
                backupStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.goodOnPaper) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // CLOUD SYNC. The same zip, kept up to date in one document the
        // player picked on Drive (or any provider) - see CloudSync.
        var cloudLink by remember { mutableStateOf(CloudSync.load(context.filesDir)) }
        var cloudStatus by remember { mutableStateOf<String?>(null) }
        var cloudBusy by remember { mutableStateOf(false) }
        var confirmCloudRestore by remember { mutableStateOf(false) }
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
            }
        }
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text("CLOUD SYNC", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 11.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                val l = cloudLink
                if (l == null) {
                    Text("Keep the backup in Google Drive on its own. LINK creates one zip where you choose (pick Drive in the " +
                        "picker); after that it is rewritten every time you leave a game, and the Drive app carries it up. " +
                        "On another phone, link the same file and RESTORE.",
                        style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(10.dp))
                    com.ironmonone.app.gen3.Gen3Button("LINK A CLOUD FILE", accent = true) { linker.launch(CloudSync.SUGGESTED_NAME) }
                } else {
                    Text("Linked to ${l.provider}. Last synced: ${CloudSync.whenLabel(l.lastSync)}. Syncs when you leave a game; ROMs never go up.",
                        style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        com.ironmonone.app.gen3.Gen3Button("SYNC NOW", accent = true, enabled = !cloudBusy) {
                            cloudBusy = true
                            scope.launch {
                                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CloudSync.sync(context, force = true) }
                                cloudLink = CloudSync.load(context.filesDir); cloudBusy = false; cloudStatus = describe(r)
                            }
                        }
                        com.ironmonone.app.gen3.Gen3Button("RESTORE FROM CLOUD", enabled = !cloudBusy) { confirmCloudRestore = true }
                        com.ironmonone.app.gen3.Gen3Button("UNLINK", enabled = !cloudBusy) {
                            CloudSync.unlink(context); cloudLink = null; cloudStatus = "Unlinked. The file stays where it is."
                        }
                    }
                }
                cloudStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.goodOnPaper) }
            }
        }
        if (confirmCloudRestore) {
            ShellDialog("Restore from cloud?", onDismiss = { confirmCloudRestore = false }) {
                Column {
                    Text("Overwrites the saves, states, runs and settings on this phone with the synced copy. ROMs are untouched.",
                        style = MaterialTheme.typography.bodyMedium, color = com.ironmonone.app.gen3.Gen3.Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        com.ironmonone.app.gen3.Gen3Button("RESTORE", accent = true) {
                            confirmCloudRestore = false; cloudBusy = true
                            scope.launch {
                                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CloudSync.restore(context) }
                                cloudBusy = false
                                cloudStatus = when { n == null -> "Could not read the synced file."; n < 0 -> "The synced file is not a KaizoCore backup."; else -> "Restored $n files from the cloud." }
                            }
                        }
                        com.ironmonone.app.gen3.Gen3Button("CANCEL") { confirmCloudRestore = false }
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
        Text(
            NATDEX_URL,
            style = MaterialTheme.typography.bodySmall,
            // Yellow on paper measured 1.01:1 - luminance-invisible, surviving
            // on hue alone. Links on a card are ink plus the underline that is
            // already here, which also works without colour vision.
            color = Shell.linkOnPaper,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 4.dp).clickable { open(NATDEX_URL) },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Tracker behaviour derives from the IronMON Tracker by besteon and " +
                "contributors, used under the MIT licence.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            TRACKER_URL,
            style = MaterialTheme.typography.bodySmall,
            color = Shell.linkOnPaper,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 4.dp).clickable { open(TRACKER_URL) },
        )

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
