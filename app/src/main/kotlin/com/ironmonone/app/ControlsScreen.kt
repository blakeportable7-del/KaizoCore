package com.ironmonone.app

import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Button remap screen (brief section 14).
 *
 * Tap a button, then press the key you want on the keyboard or controller. The
 * screen listens for the next physical key rather than asking you to pick from
 * a list, so what you press is exactly what gets bound.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ControlsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bindings = remember { KeyBindings(File(context.filesDir, "prep/keys.txt")) }
    var version by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf<KeyBindings.Button?>(null) }
    var listeningAction by remember { mutableStateOf<KeyBindings.Action?>(null) }
    var lastCaptured by remember { mutableStateOf<String?>(null) }

    // While listening, swallow the next physical key and bind it. Registered on
    // the Activity so it sees keys even though no Compose node has focus.
    DisposableEffect(listening, listeningAction) {
        val activity = context as? MainActivity
        val target = listening
        val targetAction = listeningAction
        if (activity != null && (target != null || targetAction != null)) {
            activity.keyCapture = capture@{ code ->
                // Back cancels; the phone's own keys pass through to the phone.
                if (code == android.view.KeyEvent.KEYCODE_BACK) {
                    listening = null; listeningAction = null
                    lastCaptured = null
                    return@capture true
                }
                if (KeyBindings.isSystemKey(code)) {
                    lastCaptured = "That key belongs to the phone. Press another, or Back to cancel."
                    return@capture false
                }
                if (target != null) {
                    bindings.bind(target, code)
                    lastCaptured = "${target.label} = ${KeyBindings.keyName(code)}"
                } else if (targetAction != null) {
                    bindings.bindAction(targetAction, code)
                    lastCaptured = "${targetAction.label} = ${KeyBindings.keyName(code)}"
                }
                listening = null; listeningAction = null
                version++
                true
            }
        }
        onDispose { activity?.keyCapture = null }
    }

    // Shell language, not the tracker's.
    //
    // This screen was drawing #222 rows with white/yellow pixel text on black -
    // the PC TRACKER's palette - on a settings screen. It is the only place in
    // the shell that did, which is why KEYS read as a third design next to the
    // paper cards on PREP / RUN / ROMS / INFO. Same information, same
    // interaction, now in the same language as its neighbours.
    Column(
        modifier.fillMaxSize().background(Shell.night).padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Keys and buttons",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Shell.inkOnPaper,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap a button, then press the key you want on your keyboard " +
                        "or controller.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Shell.inkOnPaper,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Controller buttons always work as themselves; these bindings " +
                        "are for keyboards and odd pads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Shell.hintOnPaper,
                )
                Spacer(Modifier.height(12.dp))

                val map = remember(version) { bindings.all() }
                KeyBindings.Button.entries.forEachIndexed { idx, button ->
                    val isListening = listening == button
                    if (idx > 0) ShellDivider()
                    ShellListRow(
                        label = button.label,
                        value = if (isListening) "press a key (Back cancels)"
                            else map[button]?.takeIf { it != 0 }
                                ?.let { KeyBindings.keyName(it) } ?: "unbound",
                        valueColor = when {
                            isListening -> Shell.goodOnPaper
                            map[button] == 0 -> Shell.dangerOnPaper
                            else -> Shell.inkOnPaper
                        },
                        onClick = { listening = if (isListening) null else button },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Emulator actions", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.sp, color = Shell.inkOnPaper)
                Spacer(Modifier.height(4.dp))
                Text("Give a pad button or key to an action. It stops being a game button until unbound.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                Spacer(Modifier.height(6.dp))
                KeyBindings.Action.entries.forEachIndexed { idx, a ->
                    val isListening = listeningAction == a
                    val bound = remember(version) { bindings.keyForAction(a) }
                    if (idx > 0) ShellDivider()
                    ShellListRow(
                        label = a.label,
                        value = if (isListening) "press a key (Back cancels)" else bound?.let { KeyBindings.keyName(it) } ?: "unbound",
                        valueColor = if (isListening) Shell.goodOnPaper else Shell.inkOnPaper,
                        onClick = { listening = null; listeningAction = if (isListening) null else a },
                    )
                }
                Spacer(Modifier.height(12.dp))
                lastCaptured?.let {
                    Text(
                        if (it.startsWith("That key")) it else "Bound: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.goodOnPaper,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                var resetArmed by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Two taps: this throws away every binding the player made.
                    Gen3Button(if (resetArmed) "SURE? RESET ALL" else "RESET TO DEFAULTS", accent = resetArmed) {
                        if (resetArmed) { bindings.resetToDefaults(); version++; lastCaptured = "Every key is back to its default."; resetArmed = false }
                        else resetArmed = true
                    }
                    listeningAction?.let { a ->
                        if (bindings.keyForAction(a) != null) Gen3Button("CLEAR ${a.label.uppercase()}") {
                            bindings.unbindAction(a); listeningAction = null; version++; lastCaptured = "${a.label} is unbound."
                        }
                    }
                    if (listening != null || listeningAction != null) Gen3Button("CANCEL") { listening = null; listeningAction = null }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Defaults: arrows move, X = A, Z = B, Enter = Start, " +
                        "R-Shift = Select, A = L, S = R.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Shell.hintOnPaper,
                )
            }
        }
    }
}
