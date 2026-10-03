package com.ironmonone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The two screens of the update check (2026-09-29): the prompt at launch and the card on INFO.
 * The logic, the copy and every network call are in [UpdateCheck] and [UpdateInstall]; this file
 * only draws them and runs the one-tap update between them.
 */

/**
 * A link in the browser. False when nothing on the phone could open it, which the caller says
 * where it was tapped: a link with no browser crashed the app once (audit, 2026-09-27).
 */
private fun openLink(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess

/**
 * What the launch check found, kept until the player answers. An activity Android rebuilds would
 * drop a dialog that is showing, and the check will not ask again for 20 hours: the answer has to
 * outlive the composition, not the process.
 */
private var heldForProcess: UpdateCheck.Manifest? = null

/**
 * Android's answers to an update, for the whole process (rc32 audit P2 #105): a receiver registered once, on the
 * application, so no screen closing can drop one, and Android's confirmation put in front of the activity that is up,
 * or of the next one to come up. The rules are [UpdateAnswers]'.
 */
internal object UpdateStatus {
    /** An update is under way, from Update to Android's last answer: Check now waits for it. */
    var busy by mutableStateOf(false)

    val answers = UpdateAnswers<Intent>(onEnded = { busy = false })

    private var registered = false
    private var front: java.lang.ref.WeakReference<android.app.Activity>? = null

    /** From MainActivity.onCreate; once a process. */
    fun register(context: Context) {
        if (registered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val confirm = UpdateInstall.confirmIntent(intent)
                answers.onStatus(UpdateInstall.session(intent), UpdateInstall.answer(UpdateInstall.status(intent), confirm != null),
                    confirm, UpdateInstall.message(intent), ::show)
            }
        }
        // Not exported: only this app's own PendingIntent can reach it.
        registered = runCatching {
            ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(UpdateInstall.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
    }

    /** From MainActivity.onResume: a confirmation that came while no activity was up is shown now. */
    fun resumed(a: android.app.Activity) {
        front = java.lang.ref.WeakReference(a)
        answers.resumed(::show)
    }

    fun paused(a: android.app.Activity) { if (front?.get() === a) front = null }

    /** Android's confirmation over the activity that is up: true when it is shown, false when it could not be, null with none up. */
    private fun show(confirm: Intent): Boolean? {
        val a = front?.get() ?: return null
        return runCatching { a.startActivity(confirm) }.isSuccess
    }
}

/** Where one update stands. */
private sealed class Step {
    object Idle : Step()
    /** Android's "Install unknown apps" switch is off for KaizoCore. */
    object NeedsAllow : Step()
    data class Downloading(val done: Long, val total: Long) : Step()
    /** The build is being copied into Android's installer: nothing to press until it is in. */
    object Handing : Step()
    /** Handed to Android's installer; its confirmation is up, or coming. */
    object Installing : Step()
    object Declined : Step()
    data class Failed(val text: String) : Step()
}

/**
 * One update, from the tap to Android's installer: the permission, the download (checked
 * against latest.json's size and SHA-256), the installer session, and Android's answer. The
 * update demo (Demo.mode == "update") copies the installed APK instead of downloading, and the
 * install after it is real: a reinstall of this same build (UpdateInstall.demoDownload).
 * [onReplaced] is told of a newer build when the site no longer has [m].
 */
private class Updater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val m: UpdateCheck.Manifest,
    private val demo: Boolean,
    private val onReplaced: (UpdateCheck.Manifest) -> Unit,
) {
    var step by mutableStateOf<Step>(Step.Idle)
    @Volatile private var cancel = false

    val busy: Boolean get() = step is Step.Downloading || step == Step.Handing || step == Step.Installing

    fun start() {
        if (busy) return
        if (!UpdateInstall.canInstall(context)) {
            step = Step.NeedsAllow
            return
        }
        cancel = false
        UpdateStatus.busy = true
        step = Step.Downloading(0, m.bytes)
        scope.launch {
            try {
                val dir = UpdateInstall.dir(context.cacheDir)
                val progress = { done: Long, total: Long -> step = Step.Downloading(done, total) }
                // The download is blocking code: it stops for Cancel, and for the screen going away.
                val alive = coroutineContext.job
                val stop = { cancel || !alive.isActive }
                val got = withContext(Dispatchers.IO) {
                    // A build Android did not install is tried again from the copy on the phone (rc32 audit P2 #103).
                    UpdateInstall.downloaded(dir, m.bytes, m.sha256)?.let { UpdateInstall.Download.Done(it) }
                        ?: if (demo) UpdateInstall.demoDownload(context, dir, progress, stop)
                        else UpdateInstall.download(m.apk, dir, m.bytes, m.sha256, progress, stop)
                }
                when (got) {
                    is UpdateInstall.Download.Failed -> {
                        step = if (got.why == UpdateInstall.Why.CANCELLED) Step.Idle else Step.Failed(UpdateCheck.failText(got.why, m.bytes))
                        // The site has this build no more: a newer one replaced it, so offer that (rc32 audit P3 #76).
                        if (got.why == UpdateInstall.Why.GONE) {
                            withContext(Dispatchers.IO) { UpdateCheck.fetch() }?.let { UpdateCheck.replacement(m, it) }?.let(onReplaced)
                        }
                    }
                    is UpdateInstall.Download.Done -> {
                        // No Close while the copy runs: Android's question comes once the session is committed (rc32 audit P2 #105).
                        step = Step.Handing
                        val handed = withContext(Dispatchers.IO) { UpdateInstall.install(context, got.file) { id -> UpdateStatus.answers.handed(id) } }
                        when (handed) {
                            // Unless Android has answered already.
                            is UpdateInstall.Handed.Committed -> if (step == Step.Handing) step = Step.Installing
                            is UpdateInstall.Handed.Failed -> step = Step.Failed(
                                if (handed.noSpace) UpdateCheck.failText(UpdateInstall.Why.NO_SPACE, m.bytes) else UpdateCheck.installFailed(""))
                        }
                    }
                }
            } finally {
                // Handed over, Android's answer ends it (UpdateStatus); anything else ends here, the screen going away too.
                if (step != Step.Installing) UpdateStatus.busy = false
            }
        }
    }

    fun cancel() { cancel = true }

    fun allow() { runCatching { context.startActivity(UpdateInstall.allowIntent(context)) } }

    /** Back from Android's settings with the switch on: carry on without a second tap. */
    fun resumed() { if (step == Step.NeedsAllow && UpdateInstall.canInstall(context)) start() }

    /**
     * How Android's installer ended the session (UpdateStatus); its confirmation is shown there, not here. Only the
     * updater that handed over a build acts on it, from the commit on: an answer can come before the copy's return does.
     */
    fun answered(answer: UpdateInstall.Answer, message: String) {
        if (step != Step.Installing && step != Step.Handing) return
        when (answer) {
            // KaizoCore is replaced a moment later; nothing to change on screen.
            UpdateInstall.Answer.CONFIRM, UpdateInstall.Answer.INSTALLED -> Unit
            UpdateInstall.Answer.DECLINED -> step = Step.Declined
            UpdateInstall.Answer.REFUSED -> step = Step.Failed(UpdateCheck.installFailed(message))
        }
    }
}

/** An [Updater] for [m], told Android's answer and the return from its settings while it is on screen. */
@Composable
private fun rememberUpdater(m: UpdateCheck.Manifest, demo: Boolean, onReplaced: (UpdateCheck.Manifest) -> Unit): Updater {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = remember(m) { Updater(context, scope, m, demo, onReplaced) }
    DisposableEffect(u) {
        val listener: (UpdateInstall.Answer, String) -> Unit = { a, message -> u.answered(a, message) }
        UpdateStatus.answers.attach(listener)
        onDispose { UpdateStatus.answers.detach(listener) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, u) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) u.resumed() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return u
}

/** The line under the buttons for where the update stands, spoken when it changes. Nothing while idle. */
@Composable
private fun UpdaterStatus(u: Updater) {
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (val s = u.step) {
        Step.Idle -> Unit
        Step.NeedsAllow -> Text(UpdateCheck.NEEDS_ALLOW, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        is Step.Downloading -> Column {
            Text(UpdateCheck.downloading(s.done, s.total), modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            val fraction = if (s.total > 0) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        Step.Handing -> Text(UpdateCheck.HANDING, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Step.Installing -> Text(UpdateCheck.INSTALLING, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Step.Declined -> Text(UpdateCheck.DECLINED, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
        is Step.Failed -> Text(s.text, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.dangerOnPaper)
    }
}

/**
 * The prompt for a newer build. Asks the site once per process start, on the IO dispatcher, and
 * keeps the answer; the dialog shows only while [show] is true, so a game in progress or the
 * crash dialog is never covered, and it appears as soon as [show] turns true.
 *
 * Update downloads the build and hands it to Android's installer (one tap, then Android's own
 * question). Later snoozes this build for three days, and leaving the dialog any other way counts
 * as Later, except while a download or an install is under way. What's new opens the notes and
 * keeps the dialog. If the update fails, Download in the browser is the old way, and snoozes the
 * build for a day. The update demo never writes a snooze, so a staged prompt cannot hide a real one.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun UpdatePrompt(show: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { UpdateCheck.installed(context) }
    val demo = Demo.mode == "update"
    var found by remember { mutableStateOf(if (demo) installed?.let { UpdateCheck.demo(it.code) } else heldForProcess) }
    var noBrowser by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (demo || found != null) return@LaunchedEffect
        val m = UpdateCheck.checkIfDue(context)
        heldForProcess = m
        found = m
    }
    val m = found
    if (show && m != null && installed != null) {
        // A newer build than the one held, found when the site no longer had it: offered in its place (rc32 audit P3 #76).
        val u = rememberUpdater(m, demo, onReplaced = { newer -> found = newer; heldForProcess = newer })
        fun close(snoozeMs: Long) {
            u.cancel()
            found = null
            heldForProcess = null
            noBrowser = false
            if (!demo) {
                val now = System.currentTimeMillis()
                scope.launch(Dispatchers.IO) { UpdateCheck.snooze(context.filesDir, m.versionCode, snoozeMs, now) }
            }
        }
        ShellDialog(UpdateCheck.promptTitle(m), onDismiss = { if (!u.busy) close(UpdateCheck.LATER_SNOOZE_MS) }) {
            Text(
                UpdateCheck.promptBody(installed.name, m),
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
            )
            if (u.step != Step.Idle) {
                Spacer(Modifier.height(10.dp))
                UpdaterStatus(u)
            }
            Spacer(Modifier.height(10.dp))
            // FlowRow: at a large font two buttons in a Row ran off the dialog.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (u.step) {
                    is Step.Downloading -> Gen3Button(UpdateCheck.CANCEL) { u.cancel() }
                    // Nothing to press while the build goes into Android's installer; Close once Android has it, and its
                    // question comes up whether this stays open or not (rc32 audit P2 #105).
                    Step.Handing -> Unit
                    Step.Installing -> Gen3Button(UpdateCheck.CLOSE) { close(UpdateCheck.DOWNLOAD_SNOOZE_MS) }
                    Step.NeedsAllow -> {
                        Gen3Button(UpdateCheck.ALLOW, accent = true) { u.allow() }
                        Gen3Button(UpdateCheck.LATER) { close(UpdateCheck.LATER_SNOOZE_MS) }
                    }
                    is Step.Failed -> {
                        Gen3Button(UpdateCheck.TRY_AGAIN, accent = true) { u.start() }
                        Gen3Button(UpdateCheck.IN_BROWSER) {
                            // Only a link that opened counts as downloading; otherwise stay and say why.
                            if (openLink(context, m.apk)) close(UpdateCheck.DOWNLOAD_SNOOZE_MS) else noBrowser = true
                        }
                        Gen3Button(UpdateCheck.LATER) { close(UpdateCheck.LATER_SNOOZE_MS) }
                    }
                    Step.Idle, Step.Declined -> {
                        Gen3Button(UpdateCheck.UPDATE, accent = true) { u.start() }
                        m.notes?.let { url -> Gen3Button(UpdateCheck.WHATS_NEW) { noBrowser = !openLink(context, url) } }
                        Gen3Button(UpdateCheck.LATER) { close(UpdateCheck.LATER_SNOOZE_MS) }
                    }
                }
            }
            if (noBrowser) {
                Spacer(Modifier.height(8.dp))
                Text(UpdateCheck.NO_BROWSER, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            }
        }
    }
}

/**
 * The card on INFO: the installed version, the once-a-day switch, what the check sends, and
 * Check now. Check now ignores the cadence and any snooze, and says what it found where the
 * button is; a newer build gets its own Update and What's new.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun UpdatesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { UpdateCheck.installed(context) }
    var on by remember { mutableStateOf(UpdateCheck.loadState(context.filesDir).enabled) }
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<UpdateCheck.Outcome?>(null) }
    var noBrowser by remember { mutableStateOf(false) }
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(UpdateCheck.CARD_TITLE, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            Text(
                UpdateCheck.cardVersion(installed?.name ?: "?"),
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
            )
            Spacer(Modifier.height(4.dp))
            ShellSwitchRow(UpdateCheck.TOGGLE_LABEL, on, UpdateCheck.CARD_HINT) { v ->
                on = v
                scope.launch(Dispatchers.IO) { UpdateCheck.setEnabled(context.filesDir, v) }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Not while an update is under way: a new check took its screen, and with it Android's answer (rc32 audit P2 #105).
                Gen3Button("Check now", enabled = !checking && installed != null && !UpdateStatus.busy) {
                    checking = true
                    noBrowser = false
                    outcome = null
                    scope.launch {
                        // checkNow never throws, but the flag must come back whatever happens.
                        outcome = try { withContext(Dispatchers.IO) { UpdateCheck.checkNow(context) } } finally { checking = false }
                    }
                }
            }
            val found = outcome
            if (checking || found != null) {
                Spacer(Modifier.height(8.dp))
                // Spoken when it changes: the status line is the only feedback the button gives.
                val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                when {
                    checking -> Text(UpdateCheck.CHECKING, modifier = live, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                    found is UpdateCheck.Outcome.Current ->
                        Text(UpdateCheck.CURRENT, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.goodOnPaper)
                    found is UpdateCheck.Outcome.Unreachable ->
                        Text(UpdateCheck.UNREACHABLE, modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.dangerOnPaper)
                    found is UpdateCheck.Outcome.Newer -> {
                        val m = found.manifest
                        val u = rememberUpdater(m, Demo.mode == "update", onReplaced = { newer -> outcome = UpdateCheck.Outcome.Newer(newer) })
                        Text(UpdateCheck.cardFound(m), modifier = live, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                        if (u.step != Step.Idle) {
                            Spacer(Modifier.height(8.dp))
                            UpdaterStatus(u)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (u.step) {
                                is Step.Downloading -> Gen3Button(UpdateCheck.CANCEL) { u.cancel() }
                                Step.Handing, Step.Installing -> Unit
                                Step.NeedsAllow -> Gen3Button(UpdateCheck.ALLOW, accent = true) { u.allow() }
                                is Step.Failed -> {
                                    Gen3Button(UpdateCheck.TRY_AGAIN, accent = true) { u.start() }
                                    Gen3Button(UpdateCheck.IN_BROWSER) { noBrowser = !openLink(context, m.apk) }
                                }
                                Step.Idle, Step.Declined -> {
                                    Gen3Button(UpdateCheck.UPDATE, accent = true) { u.start() }
                                    m.notes?.let { url -> Gen3Button(UpdateCheck.WHATS_NEW) { noBrowser = !openLink(context, url) } }
                                }
                            }
                        }
                        if (noBrowser) {
                            Spacer(Modifier.height(8.dp))
                            Text(UpdateCheck.NO_BROWSER, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
                        }
                    }
                }
            }
        }
    }
}
