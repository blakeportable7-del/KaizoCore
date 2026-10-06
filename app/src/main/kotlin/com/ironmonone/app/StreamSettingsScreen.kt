package com.ironmonone.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.stream.ObsLink
import com.ironmonone.app.stream.ObsSettings
import com.ironmonone.app.stream.twitch.ChatCommand
import com.ironmonone.app.stream.twitch.TwitchChat
import com.ironmonone.app.stream.twitch.TwitchLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The words of the OBS card that name the two ways to the PC (StreamLinksTest). */
internal object ObsCopy {
    val HOW = "In OBS 28 or newer, open Tools, WebSocket Server Settings and tick Enable WebSocket server. Show Connect " +
        "Info there gives the address, port and password. On Wi-Fi, the phone and the PC need the same network: enter the " +
        "PC's address. On a USB cable, run " + com.ironmonone.app.stream.StreamHub.ADB_REVERSE_OBS + " on the PC, again " +
        "each time you plug the phone in, and enter " + com.ironmonone.app.stream.StreamHub.WIRED_HOST + " as the address."
}

/** The words of the Twitch card, in one place for the copy test (TwitchStorageTest): plain, no dashes, nothing about money. */
internal object TwitchCopy {
    const val TITLE = "Twitch chat commands"
    const val INTRO = "Your viewers type !pokemon, !moves, !attempts or !gachamon in your Twitch chat and this phone " +
        "answers, like the PC tracker's Stream Connect. It only says what your tracker shows: nothing about the " +
        "opponent, the seed or the log."
    const val CONNECT = "Connect Twitch"
    const val WAITING = "On your phone or PC, go to twitch.tv/activate, sign in, and enter this code:"
    const val OPEN = "Open twitch.tv/activate"
    const val CANCEL = "Cancel"
    const val ANSWER = "Answer chat commands"
    const val ANSWER_NOTE = "Off keeps you signed in and quiet."
    const val COMMANDS = "Commands"
    const val SIGN_OUT = "Sign out"
    const val PRIVACY = "Signing in gives KaizoCore two permissions on your account: read your channel's chat and send " +
        "chat messages as you. Your sign-in stays on this phone, locked by the phone's own key store, and is left out of " +
        "backups. Sign out removes it here and at Twitch."
    const val NO_BROWSER = "No browser on this phone. Go to twitch.tv/activate on any device."

    fun connectedAs(name: String) = "Connected as $name."
    fun phaseLine(s: TwitchLink.Status): String = when (s.phase) {
        TwitchLink.Phase.SIGNED_OUT -> s.note
        TwitchLink.Phase.GETTING_CODE -> s.note
        TwitchLink.Phase.WAITING_FOR_CODE -> "Waiting for Twitch. This page moves on by itself."
        TwitchLink.Phase.CONNECTING -> "Connecting to your chat..."
        TwitchLink.Phase.LISTENING -> "Answering your chat."
        TwitchLink.Phase.RECONNECTING -> s.note.ifBlank { "Reconnecting..." }
        TwitchLink.Phase.PAUSED -> s.note.ifBlank { "Not answering chat." }
    }

    /** Every line above, for the copy test. */
    val ALL: List<String> get() = listOf(TITLE, INTRO, CONNECT, WAITING, OPEN, CANCEL, ANSWER, ANSWER_NOTE, COMMANDS, SIGN_OUT,
        PRIVACY, NO_BROWSER) + ChatCommand.entries.map { it.help }
}

/**
 * More > Stream (2026-10-05): the stream's settings that are not the Play screen's, one page with two cards.
 * Its own page, not Play's menu: PlayScreen sits at ART's verifier limit, so nothing here is reached from it, and these
 * are set once, not mid-game.
 *
 *  - OBS (ObsLink): OBS on the PC reacting by itself through its own WebSocket server. The player enters the PC's address,
 *    the port and the password from OBS's WebSocket Server Settings, tests the connection, which reads OBS's scene list,
 *    then picks a game scene, a battle scene and a game over scene from it, and whether to save the replay buffer when a
 *    run ends. The link's state is shown live, from ObsLink.status.
 *  - Twitch (TwitchLink): Stream Connect, chat commands answered by the phone.
 *
 * The browser sources themselves (tracker, game over card, timer, history...) need nothing here: their addresses are on
 * the stream's setup page, which Play's Stream menu opens. Since 2026-10-06 the page starts with the links to that guide,
 * Wi-Fi and USB cable side by side (StreamLinksCard). Merged from feat/stream-obs and feat/stream-twitch 2026-10-05.
 */
@Composable
fun StreamSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var obsLoaded by remember { mutableStateOf(false) }
    var linkToken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ObsLink.app.load(context.applicationContext.filesDir)
            linkToken = com.ironmonone.app.stream.StreamHub.linkToken(context.applicationContext.filesDir)
        }
        obsLoaded = true
    }
    Column(modifier.fillMaxSize().background(Shell.night).verticalScroll(rememberScrollState()).padding(10.dp)) {
        linkToken?.let { StreamLinksCard(it) }
        Spacer(Modifier.height(12.dp))
        if (obsLoaded) ObsSettingsCard()
        Spacer(Modifier.height(12.dp))
        TwitchCard()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ObsSettingsCard() {
    val link = ObsLink.app
    var draft by remember { mutableStateOf(link.settings()) }
    var portText by remember { mutableStateOf(draft.port.toString()) }
    var scenes by remember { mutableStateOf<List<String>>(emptyList()) }
    var testing by remember { mutableStateOf(false) }
    var testLine by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var replayRunning by remember { mutableStateOf<Boolean?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    val status by link.status.collectAsState()
    val scope = rememberCoroutineScope()

    /** The draft with the address and port as typed, cleaned: a port typed into the address is taken when the box is at its default. */
    fun typed(): ObsSettings {
        val (host, inAddress) = ObsSettings.cleanHost(draft.host)
        val box = portText.trim().toIntOrNull() ?: -1
        val port = if (inAddress != null && box == com.ironmonone.app.stream.ObsProtocol.DEFAULT_PORT) inAddress else box
        return draft.copy(host = host, port = port)
    }

    fun save(s: ObsSettings) {
        draft = s
        if (s.port in 1..65535) portText = s.port.toString()
        scope.launch(Dispatchers.IO) { link.update(s) }
    }

    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text("OBS switches scenes by itself", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            Text("KaizoCore can tell OBS on your PC what is happening in the game: go to your battle scene when a battle " +
                "starts and back to your game scene after, go to a game over scene when a Kaizo IronMON run is lost, and " +
                "save OBS's replay buffer when a run ends. Leaving the game goes back to your game scene.",
                style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            Text(ObsCopy.HOW, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(10.dp))
            ShellSwitchRow("Use OBS", draft.enabled, "Off: KaizoCore never contacts OBS.") { on -> save(typed().copy(enabled = on)) }
            Spacer(Modifier.height(8.dp))
            Text(status.line, style = MaterialTheme.typography.bodyMedium, color = when (status.state) {
                ObsLink.State.CONNECTED -> Shell.goodOnPaper
                ObsLink.State.STOPPED -> Shell.dangerOnPaper
                else -> Shell.hintOnPaper
            })
            if (status.note.isNotEmpty()) Text(status.note, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            Spacer(Modifier.height(12.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper)
            OutlinedTextField(draft.host, { draft = draft.copy(host = it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("PC address") }, placeholder = { Text("192.168.1.20") }, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Port") }, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(draft.password, { draft = draft.copy(password = it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Password") }, colors = fieldColors, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("Leave it empty if OBS has no password. It stays on this phone, locked by the phone's own key store, and is " +
                "left out of backups.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button(if (testing) "Testing..." else "Test connection", enabled = !testing, accent = true) {
                    val s = typed()
                    testing = true
                    testLine = null
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { ObsLink.probe(s) }
                        testing = false
                        testOk = p.ok
                        testLine = p.line
                        if (p.ok) { scenes = p.scenes; replayRunning = p.replayRunning }
                    }
                }
                Gen3Button("Save") { save(typed()) }
            }
            testLine?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (testOk) Shell.goodOnPaper else Shell.dangerOnPaper)
            }

            Spacer(Modifier.height(14.dp))
            Text("Scenes", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Shell.inkOnPaper)
            Text(if (scenes.isEmpty()) "Test the connection to choose from OBS's scenes." else "Tap one to choose. None means it stays where it is.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(4.dp))
            SceneRow("Game scene", draft.overworldScene, scenes.isNotEmpty()) { picking = "overworld" }
            ShellDivider()
            SceneRow("Battle scene", draft.battleScene, scenes.isNotEmpty()) { picking = "battle" }
            ShellDivider()
            SceneRow("Game over scene", draft.gameOverScene, scenes.isNotEmpty()) { picking = "gameover" }
            Text("The game scene is the one you play on. The app only switches while OBS shows one of these three, so your " +
                "Starting soon or Be right back scene is left alone. A battle has to last a moment before OBS switches, so a " +
                "flicker at its start or end does not jump scenes.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(10.dp))
            ShellSwitchRow("Save the replay buffer when a run ends", draft.saveReplay,
                "OBS saves its last seconds, the loss included. Start the replay buffer in OBS first: Controls, Start Replay Buffer. " +
                    "Set its length in Settings, Output, Replay Buffer.") { on -> save(typed().copy(saveReplay = on)) }
            if (replayRunning == false && draft.saveReplay) {
                Text("OBS's replay buffer is not running right now.", style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            }
            if (draft.enabled && !typed().usable) {
                Spacer(Modifier.height(6.dp))
                Text(ObsLink.whatIsMissing(typed()), style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            }
        }
    }

    picking?.let { which ->
        val current = when (which) { "battle" -> draft.battleScene; "gameover" -> draft.gameOverScene; else -> draft.overworldScene }
        ShellDialog(title = when (which) { "battle" -> "Battle scene"; "gameover" -> "Game over scene"; else -> "Game scene" }, onDismiss = { picking = null }) {
            (listOf("") + scenes).forEach { name ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Shell.touchTarget).clickable {
                        val base = typed()
                        save(when (which) {
                            "battle" -> base.copy(battleScene = name)
                            "gameover" -> base.copy(gameOverScene = name)
                            else -> base.copy(overworldScene = name)
                        })
                        picking = null
                    }.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(name == current)
                    Spacer(Modifier.width(10.dp))
                    Text(name.ifEmpty { "None" }, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                }
            }
        }
    }
}

@Composable
private fun SceneRow(label: String, scene: String, canPick: Boolean, onPick: () -> Unit) {
    ShellListRow(label, scene.ifEmpty { "None" }, onClick = if (canPick) onPick else null,
        valueColor = if (scene.isEmpty()) Shell.hintOnPaper else Shell.inkOnPaper)
}

@Composable
private fun TwitchCard() {
    val context = LocalContext.current
    val link = remember { TwitchChat.init(context); TwitchChat.link }
    if (link == null) return
    val s by link.status.collectAsState()
    // The switches are plain fields in ChatSettings; this bumps a redraw when one is flipped.
    var flips by remember { mutableIntStateOf(0) }
    var browserNote by remember { mutableStateOf<String?>(null) }

    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(TwitchCopy.TITLE, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Shell.inkOnPaper)
            Spacer(Modifier.height(6.dp))
            Text(TwitchCopy.INTRO, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Spacer(Modifier.height(10.dp))
            when (s.phase) {
                TwitchLink.Phase.SIGNED_OUT, TwitchLink.Phase.GETTING_CODE -> {
                    Gen3Button(TwitchCopy.CONNECT, Modifier.fillMaxWidth(), enabled = s.phase == TwitchLink.Phase.SIGNED_OUT, accent = true) {
                        browserNote = null; link.signIn()
                    }
                }
                TwitchLink.Phase.WAITING_FOR_CODE -> {
                    Text(TwitchCopy.WAITING, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(s.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                            color = Shell.inkOnPaper, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Gen3Button(TwitchCopy.OPEN, accent = true) {
                            val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.link))) }.isSuccess
                            browserNote = if (ok) null else TwitchCopy.NO_BROWSER
                        }
                        Spacer(Modifier.width(8.dp))
                        Gen3Button(TwitchCopy.CANCEL) { link.cancelSignIn() }
                    }
                }
                else -> {
                    Text(TwitchCopy.connectedAs(s.name), style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(4.dp))
                    ShellSwitchRow(TwitchCopy.ANSWER, s.phase != TwitchLink.Phase.PAUSED, TwitchCopy.ANSWER_NOTE) { on ->
                        link.setAnswering(on)
                    }
                    ShellDivider()
                    Text(TwitchCopy.COMMANDS, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Shell.hintOnPaper,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    androidx.compose.runtime.key(flips) {
                        for (c in ChatCommand.entries) {
                            ShellSwitchRow(c.label, link.settings.enabled(c), c.help.substringAfter("> ")) { on ->
                                link.settings.setEnabled(c, on); flips++
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Gen3Button(TwitchCopy.SIGN_OUT) { link.signOut() }
                }
            }
            val line = TwitchCopy.phaseLine(s)
            if (line.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = if (s.phase == TwitchLink.Phase.RECONNECTING) Shell.dangerOnPaper else Shell.hintOnPaper)
            }
            browserNote?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            }
            Spacer(Modifier.height(10.dp))
            Text(TwitchCopy.PRIVACY, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
        }
    }
}
