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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
    // Warnings show in the danger colour; they were green like a success
    // (audit, 2026-09-27).
    var lastIsWarning by remember { mutableStateOf(false) }
    val connected by Controllers.connected
    fun say(text: String?, warning: Boolean = false) { lastCaptured = text; lastIsWarning = warning }

    // A row tapped with no keyboard or controller attached used to wait
    // forever for a key that could not come (audit, 2026-09-27).
    fun canListen(): Boolean {
        Controllers.refresh()
        if (!Controllers.connected.value) {
            say("Connect a keyboard or controller to change these.", warning = true)
            return false
        }
        return true
    }

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
                    say(null)
                    return@capture true
                }
                if (KeyBindings.isSystemKey(code)) {
                    say("That key belongs to the phone. Press another, or Back to cancel.", warning = true)
                    return@capture false
                }
                // A standard pad button always reaches the game as itself (see
                // MainActivity.dispatchKeyEvent), so binding it to a console
                // button showed "B = Pad Y" and did nothing. Refused, and the
                // screen keeps listening. Actions still take pad buttons: they
                // are handled before the passthrough (audit, 2026-09-27).
                if (target != null && activity.captureFromPad && code in KeyBindings.PAD_PASSTHROUGH) {
                    say("Controller buttons always work as themselves; bind keyboard keys here.", warning = true)
                    return@capture true
                }
                val (lost, what) = when {
                    target != null ->
                        bindings.bind(target, code) to "${target.label} = ${KeyBindings.keyName(code)}."
                    targetAction != null ->
                        bindings.bindAction(targetAction, code) to "${targetAction.label} = ${KeyBindings.keyName(code)}."
                    else -> null to ""
                }
                // Say who lost the key; it used to be unbound silently.
                say("Bound: $what" + (lost?.let { " $it lost its key; tap $it to bind it again." } ?: ""),
                    warning = lost != null)
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
                if (!connected) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No keyboard or controller is connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Shell.hintOnPaper,
                    )
                }
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
                        onClick = {
                            listeningAction = null
                            listening = if (isListening) null else if (canListen()) button else null
                        },
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ShellListRow(
                            label = a.label,
                            value = if (isListening) "press a key (Back cancels)" else bound?.let { KeyBindings.keyName(it) } ?: "unbound",
                            valueColor = if (isListening) Shell.goodOnPaper else Shell.inkOnPaper,
                            onClick = {
                                listening = null
                                listeningAction = if (isListening) null else if (canListen()) a else null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        // Clear is on the row itself; it only appeared while
                        // that row was listening (audit, 2026-09-27).
                        if (bound != null && !isListening) {
                            IconButton(
                                onClick = { bindings.unbindAction(a); version++; say("${a.label} is unbound.") },
                                modifier = Modifier.size(Shell.touchTarget),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear ${a.label}", tint = Shell.hintOnPaper)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                lastCaptured?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lastIsWarning) Shell.dangerOnPaper else Shell.goodOnPaper,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                var resetArmed by remember { mutableStateOf(false) }
                // Disarm after 4 s: "Sure?" used to stay armed forever (audit, 2026-09-27).
                LaunchedEffect(resetArmed) { if (resetArmed) { kotlinx.coroutines.delay(4000); resetArmed = false } }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Two taps: this throws away every binding the player made.
                    Gen3Button(if (resetArmed) "Sure? Reset all" else "Reset to defaults", accent = resetArmed) {
                        if (resetArmed) { bindings.resetToDefaults(); version++; say("Every key is back to its default."); resetArmed = false }
                        else resetArmed = true
                    }
                    if (listening != null || listeningAction != null) Gen3Button("Cancel") { listening = null; listeningAction = null }
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
