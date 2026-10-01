package com.ironmonone.app

import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The words on the crash screens, in one place so a test can hold them to the copy rules
 * (plain, dry, no em dash) and to the promise they make.
 */
internal object CrashReportText {
    const val TITLE = "KaizoCore closed unexpectedly"
    const val ASK = "It happened last time you played. Your saves are kept. Send a report so it gets fixed? " +
        "It says where the app failed, with your phone model, the Android and app versions and the game family. " +
        "Never a ROM, a save or a file name."
    const val SENDING = "Sending..."
    const val SENT = "Sent. Thank you."
    const val FAILED = "Could not send it. Share it instead?"
    const val SENT_BEFORE = "Sent to Blake."
    const val INFO_HINT = "Tap Send report and it goes to Blake. It says where the app failed, " +
        "which is what makes a crash fixable rather than guessed at."
    const val CARD_TITLE = "Crash reports"
    const val SWITCH = "Send crash reports automatically"
    const val CARD_NOTE = "If KaizoCore closes unexpectedly, a report of where it failed goes to Blake at willowcreek.group: " +
        "your phone model, the Android and app versions, the game family and the lines that name the failure. " +
        "Never a ROM, a save or a file name. With this off, the app asks you before it sends a report."
}

/** Where one send stands, for the launch dialog and for the INFO card. */
private enum class Going { IDLE, SENDING, SENT, FAILED }

/**
 * One send, off the main thread; a failure is a result and never an exception. In demo mode
 * (`--es demo crash`) the sample report goes nowhere: it waits a moment and says it went.
 */
private suspend fun sendReport(context: Context, report: String): CrashReport.SendResult {
    if (Demo.mode == "crash") {
        delay(600)
        return CrashReport.SendResult.Sent
    }
    val result = withContext(Dispatchers.IO) {
        runCatching { CrashReport.submit(context, report) }
            .getOrElse { CrashReport.SendResult.Failed(it.javaClass.simpleName) }
    }
    // Tagged KaizoCore so Feedback.logTail carries the reason into a bug report (see CloudSync).
    if (result !is CrashReport.SendResult.Sent) android.util.Log.i("KaizoCore", "crash report not sent: $result")
    return result
}

/**
 * The share sheet with the scrubbed report: the way out when a send does not go. False when
 * nothing on the phone can take it.
 */
internal fun shareReport(context: Context, report: String): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "KaizoCore crash report")
        putExtra(Intent.EXTRA_TEXT, CrashReport.scrub(report))
    }
    context.startActivity(Intent.createChooser(send, "Send crash report"))
}.isSuccess

/**
 * A report for `--es demo crash`, made by the real renderer so it has the real shape. A native
 * crash in the mGBA core, the kind a phone actually sends.
 */
internal fun demoReport(context: Context): String {
    val device = Feedback.device(context)
    val lib = "/data/app/~~demo==/${context.packageName}-demo==/lib/arm64/libmgba_libretro_android.so"
    return CrashLog.render(
        BuildConfigish.version(context), "${device.model}, ${device.android}",
        listOf(
            CrashLog.Exit(
                time = System.currentTimeMillis() - 90 * 60_000L,
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE, status = 11, importance = 100,
                process = context.packageName, description = "crash", rss = 412_384L,
                tombstone = CrashLog.Tombstone(
                    "crash-trace-demo.bin", 21_480L,
                    listOf(
                        "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0000000000000018",
                        "Cause: null pointer dereference",
                        "#00 pc 000000000006e2f4  $lib (retro_run+412)",
                        "#01 pc 0000000000012a90  $lib (Java_com_swordfish_libretrodroid_LibretroDroid_step+96)",
                    ),
                ),
            ),
        ),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ButtonRow(content: @Composable () -> Unit) {
    // FlowRow: at a large font two buttons in a Row ran off the dialog.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)

/**
 * The dialog at launch after a crash or a freeze. Three ways it goes:
 *
 *  - The player has "Always send" on and the report may go: it goes with nothing on screen, and
 *    only if it fails does the dialog open, at "Could not send it".
 *  - Otherwise the dialog asks: Send report, Always send (turns the switch on, then sends), Not now.
 *  - A send in the dialog ends at "Sent. Thank you." or at "Could not send it. Share it instead?",
 *    whose Share instead is the old share sheet, with the report scrubbed.
 *
 * [onClose] clears the crash at the caller, whichever way it ended. In demo mode nothing is sent and
 * nothing is written, so it can be staged on a phone.
 */
@Composable
internal fun CrashReportLaunch(report: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val demo = Demo.mode == "crash"
    // Decided once, when the dialog is made: what the player chose before this launch decides it.
    var quiet by remember { mutableStateOf(!demo && CrashReport.mayAutoSend(context.filesDir, report)) }
    var going by remember { mutableStateOf(Going.IDLE) }

    fun send() {
        going = Going.SENDING
        scope.launch {
            going = if (sendReport(context, report) is CrashReport.SendResult.Sent) Going.SENT else Going.FAILED
        }
    }

    LaunchedEffect(Unit) {
        if (!quiet) return@LaunchedEffect
        if (sendReport(context, report) is CrashReport.SendResult.Sent) onClose()
        else {
            going = Going.FAILED
            quiet = false
        }
    }

    if (!quiet) {
        // A send under way is not dismissed by a tap outside: it would finish with nobody told.
        ShellDialog(CrashReportText.TITLE, onDismiss = { if (going != Going.SENDING) onClose() }) {
            when (going) {
                Going.IDLE -> {
                    Body(CrashReportText.ASK)
                    Spacer(Modifier.height(10.dp))
                    ButtonRow {
                        Gen3Button("Send report", accent = true) { send() }
                        Gen3Button("Always send") {
                            if (!demo) CrashReport.setAuto(context.filesDir, true)
                            send()
                        }
                        Gen3Button("Not now") { onClose() }
                    }
                }
                Going.SENDING -> {
                    Body(CrashReportText.SENDING)
                    ShellBusy()
                }
                Going.SENT -> {
                    Body(CrashReportText.SENT)
                    Spacer(Modifier.height(10.dp))
                    ButtonRow { Gen3Button("Close", accent = true) { onClose() } }
                }
                Going.FAILED -> {
                    Body(CrashReportText.FAILED)
                    Spacer(Modifier.height(10.dp))
                    ButtonRow {
                        Gen3Button("Share instead", accent = true) {
                            shareReport(context, report)
                            onClose()
                        }
                        Gen3Button("Close") { onClose() }
                    }
                }
            }
        }
    }
}

/**
 * The buttons of INFO's "Last session ended unexpectedly" card. Send report goes through the
 * site; a report already delivered shows "Sent to Blake." where the button was; Share instead is
 * the share sheet; Dismiss is the caller's.
 */
@Composable
internal fun CrashCardActions(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stamp = remember(report) { CrashLog.newestStamp(report) }
    val before = remember(report) { stamp != null && CrashReport.load(context.filesDir).lastSent == stamp }
    var going by remember(report) { mutableStateOf(Going.IDLE) }
    ButtonRow {
        when {
            going == Going.SENT -> CardNote(CrashReportText.SENT)
            before -> CardNote(CrashReportText.SENT_BEFORE)
            else -> Gen3Button("Send report", accent = true, enabled = going != Going.SENDING) {
                going = Going.SENDING
                scope.launch {
                    going = if (sendReport(context, report) is CrashReport.SendResult.Sent) Going.SENT else Going.FAILED
                }
            }
        }
        Gen3Button("Share instead") { shareReport(context, report) }
        Gen3Button("Dismiss") { onDismiss() }
    }
    when (going) {
        Going.SENDING -> CardStatus(CrashReportText.SENDING, Shell.hintOnPaper)
        Going.FAILED -> CardStatus(CrashReportText.FAILED, Shell.dangerOnPaper)
        else -> Unit
    }
}

/** A fact in a button's place, as tall as the button so the row does not jump. */
@Composable
private fun CardNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Shell.goodOnPaper,
        modifier = Modifier.heightIn(min = Shell.touchTarget).wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun CardStatus(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/**
 * INFO's always-visible card: the one switch behind "Always send". Off is the default, and off means
 * the app asks first (CrashReportLaunch). The row is ShellSwitchRow, drawn for paper cards; the
 * write is synced to disk (SafeWrite), so it happens off the main thread.
 */
@Composable
internal fun CrashReportsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(CrashReport.load(context.filesDir).auto) }
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(CrashReportText.CARD_TITLE, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            ShellSwitchRow(CrashReportText.SWITCH, auto, CrashReportText.CARD_NOTE) { on ->
                auto = on
                scope.launch(Dispatchers.IO) { CrashReport.setAuto(context.filesDir, on) }
            }
        }
    }
}
