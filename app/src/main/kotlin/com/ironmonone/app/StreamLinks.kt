package com.ironmonone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.stream.StreamHub

/**
 * The stream's links for the PC, Wi-Fi and USB cable side by side, each with its own Copy (2026-10-06, Blake: "do both
 * wifi and wired"). Out of PlayScreen on purpose: it sits at ART's verifier limit, so Play's FILE menu makes one call
 * here ([StreamLinkRows], handed to the FILE bar's TOOLS sheet as its streamExtra) where it used to build one line and
 * one Copy itself. The Stream page (More, Stream) shows the
 * same rows in a card ([StreamLinksCard]), with the adb lines to copy.
 *
 * Copy: plain, no dashes. The words are StreamHub's (linkLines, copyLink), which StreamLinksTest reads.
 */
internal object StreamLinkCopy {
    const val COPY_WIFI = "Copy Wi-Fi link"
    const val COPY_USB = "Copy USB link"
    const val COPY_ADB = "Copy adb line"
    const val COPY_ADB_OBS = "Copy OBS adb line"
    const val TITLE = "Links for your PC"
    val INTRO = "Open one of these in a browser on the PC that runs OBS: it is the setup guide, with the OBS scene to " +
        "download and every address. They work while Stream is on under TOOLS on a game's File bar. The USB cable needs USB " +
        "debugging on, as for scrcpy; Wi-Fi needs the phone and the PC on the same network."
    val ADB_NOTE = "The adb line ends when the phone is unplugged or adb restarts: run it again then. With scrcpy open, " +
        "a line copied here can be pasted on the PC."
    val ADB_OBS_NOTE = "For OBS switching scenes over the cable, also run " + StreamHub.ADB_REVERSE_OBS + " on the PC, and " +
        "enter " + StreamHub.WIRED_HOST + " as the PC's address below."
    const val ADB_COPIED = "adb line copied."

    fun copyLabel(way: StreamHub.Way) = if (way == StreamHub.Way.WIFI) COPY_WIFI else COPY_USB

    val ALL: List<String> get() = listOf(COPY_WIFI, COPY_USB, COPY_ADB, COPY_ADB_OBS, TITLE, INTRO, ADB_NOTE, ADB_OBS_NOTE, ADB_COPIED)
}

/**
 * The two links, one row each: its line (StreamHub.linkLines) and its Copy, which hands the clipboard that link and
 * [onStatus] what was done. Wi-Fi with no address shows what to do and no Copy; the cable always has one. [k] is the
 * token the links carry: the running stream's in Play, the saved one on the Stream page.
 */
@Composable
internal fun StreamLinkRows(onStatus: (String) -> Unit, k: String = StreamHub.token, text: Color = Shell.hintOnNight) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val address = StreamHub.wifiAddress()
    Column(Modifier.fillMaxWidth()) {
        for ((way, line) in StreamHub.linkLines(address, k)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = text, modifier = Modifier.weight(1f))
                if (StreamHub.canCopy(way, address)) {
                    Spacer(Modifier.width(6.dp))
                    Gen3Button(StreamLinkCopy.copyLabel(way), raw = true, onClick = {
                        onStatus(StreamHub.copyLink(way, address, { clipboard.setText(AnnotatedString(it)) }, k))
                    })
                }
            }
        }
    }
}

/** The Stream page's first card: the two links, and the adb lines to copy for the cable. */
@Composable
internal fun StreamLinksCard(k: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var note by remember { mutableStateOf<String?>(null) }
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(StreamLinkCopy.TITLE, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            Text(StreamLinkCopy.INTRO, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Spacer(Modifier.height(8.dp))
            StreamLinkRows({ note = it }, k, text = Shell.inkOnPaper)
            Spacer(Modifier.height(8.dp))
            Text(StreamLinkCopy.ADB_NOTE, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(6.dp))
            Row {
                Gen3Button(StreamLinkCopy.COPY_ADB, raw = true) {
                    clipboard.setText(AnnotatedString(StreamHub.ADB_FORWARD)); note = StreamLinkCopy.ADB_COPIED
                }
                Spacer(Modifier.width(8.dp))
                Gen3Button(StreamLinkCopy.COPY_ADB_OBS, raw = true) {
                    clipboard.setText(AnnotatedString(StreamHub.ADB_REVERSE_OBS)); note = StreamLinkCopy.ADB_COPIED
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(StreamLinkCopy.ADB_OBS_NOTE, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.goodOnPaper)
            }
        }
    }
}
