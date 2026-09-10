package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** LogTabMisc.openRandomizerShareWindow's text: each line "Label: value", the settings string after a blank line. */
internal fun logShareText(log: RandomizerLog): String = listOf(
    "Pokémon Game: ${log.game}",
    "Randomizer Version: ${log.version}",
    "Random Seed: ${log.seed}",
    "\nSettings String: ${log.settingsString}",
).joinToString(" \n")

/**
 * LogTabMisc: the three checkboxes (Show unlearnable Gym TMs, Show Pre
 * Evolutions, Custom Trainer Names) and Share Seed, then the game, the
 * randomizer's version, the seed and the settings string in 39-character
 * pieces. Share Seed shows the reference's text to copy; the copy button puts
 * it on the phone's clipboard. Below a rule are the app's own extras from the
 * log, which the reference's Misc tab does not show.
 */
@Composable
internal fun LogMiscTab(log: RandomizerLog) {
    var share by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                LogCheck("Show unlearnable Gym TMs", TrackerOptions.logShowUnlearnableGymTms) {
                    TrackerOptions.logShowUnlearnableGymTms = !TrackerOptions.logShowUnlearnableGymTms; TrackerOptions.save()
                }
                LogCheck("Show Pre Evolutions", TrackerOptions.logShowPreEvolutions) {
                    TrackerOptions.logShowPreEvolutions = !TrackerOptions.logShowPreEvolutions; TrackerOptions.save()
                }
                LogCheck("Custom Trainer Names", TrackerOptions.logCustomTrainerNames) {
                    TrackerOptions.logCustomTrainerNames = !TrackerOptions.logCustomTrainerNames; TrackerOptions.save()
                }
            }
            PixText("Share Seed", 7, Pc.Text, Modifier.border(1.dp, Pc.Border).clickable { share = true }.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        Spacer(Modifier.height(10.dp))
        LogInfo("Pokémon Game:", log.game)
        LogInfo("Randomizer Version:", log.version)
        LogInfo("Random Seed:", log.seed)
        PixText("Settings String:", 8, Pc.Text, Modifier.padding(top = 4.dp))
        log.settingsString.chunked(39).forEach { PixText(it, 7, Pc.Text, Modifier.padding(start = 8.dp)) }

        Spacer(Modifier.height(12.dp))
        PixText("From the log (not on the PC tracker's Misc tab)", 7, Pc.Dim)
        Spacer(Modifier.height(4.dp))
        PixText("Starters", 8, Pc.Gold)
        log.starters.forEach { PixText(it, 7, Pc.Text) }
        Spacer(Modifier.height(6.dp))
        PixText("Static encounters", 8, Pc.Gold)
        log.statics.forEach { (a, b) -> PixText("$a  ->  $b", 7, Pc.Text) }
        if (log.pickup.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PixText("Pickup items", 8, Pc.Gold)
            log.pickup.forEach { PixText(it, 7, Pc.Text, wrap = true) }
        }
    }
    if (share) ShareSeedDialog(log) { share = false }
}

@Composable
private fun LogCheck(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.clickable { onToggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PixText(if (on) "[X]" else "[ ]", 8, Pc.Gold, Modifier.width(26.dp))
        PixText(label, 8, Pc.Text)
    }
}

@Composable
private fun LogInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PixText(label, 8, Pc.Text, Modifier.width(150.dp))
        PixText(value.ifBlank { "---" }, 8, Pc.Text, wrap = true)
    }
}

/** "Share Randomizer Seed": the reference's instructions, the text, and a button that copies it. */
@Composable
private fun ShareSeedDialog(log: RandomizerLog, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(log) { logShareText(log) }
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(10.dp)) {
            PixText("Share Randomizer Seed", 9, Pc.Gold)
            Spacer(Modifier.height(6.dp))
            PixText("Copy/paste everything below to share. Load it through Randomizer --> Premade Seed.", 7, Pc.Text, wrap = true)
            Spacer(Modifier.height(8.dp))
            PixText(text, 7, Pc.Text, Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp), wrap = true)
            Spacer(Modifier.height(8.dp))
            Row {
                PixText(if (copied) "COPIED" else "COPY", 8, if (copied) Pc.Positive else Pc.Gold,
                    Modifier.border(1.dp, Pc.Border).clickable {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Randomizer seed", text))
                        copied = true
                    }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(10.dp))
                PixText("CLOSE", 8, Pc.Text, Modifier.border(1.dp, Pc.Border).clickable { onClose() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
