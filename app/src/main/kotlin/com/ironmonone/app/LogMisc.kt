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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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

/**
 * LogTabMisc.openRandomizerShareWindow's text: each line "Label: value", the settings string after a blank line. A run
 * made in more than one pass gives each pass its seed and settings string, in the order they are applied (the 60%
 * levels' pre-pass first, PART 2 after): it handed out the logged pass alone, which rebuilds an easier game than the
 * one played (rc32 audit P2 #69).
 */
internal fun logShareText(log: RandomizerLog): String {
    if (log.passes.isEmpty()) return listOf(
        "Pokémon Game: ${log.game}",
        "Randomizer Version: ${log.version}",
        "Random Seed: ${log.seed}",
        "\nSettings String: ${log.settingsString}",
    ).joinToString(" \n")
    val steps = log.passes.filter { it.before }.map { "${it.label}, ${it.file}" to it } +
        listOf((if (log.passes.any { !it.before }) "PART 1" else "The run's own settings") to null) +
        log.passes.filter { !it.before }.map { "${it.label}, ${it.file}" to it }
    return (listOf(
        "Pokémon Game: ${log.game}",
        "Randomizer Version: ${log.version}",
        "This run took ${steps.size} passes. Load them in this order, each on the ROM the one before made.",
    ) + steps.flatMapIndexed { i, (label, pass) ->
        listOf(
            "\n${i + 1}. $label",
            "Random Seed: ${pass?.seed ?: log.seed}",
            "Settings String: " + if (pass == null) log.settingsString
            else pass.settingsString.ifEmpty { "not kept in this log. It is KaizoCore's own ${pass.file}." },
        )
    }).joinToString(" \n")
}

/**
 * LogTabMisc: the three checkboxes (Show unlearnable Gym TMs, Show Pre
 * Evolutions, Custom Trainer Names) and Share Seed, then the game, the
 * randomizer's version, the seed and the settings string in 39-character
 * pieces. Share Seed shows the reference's text to copy; the copy button puts
 * it on the phone's clipboard. Below a rule are the app's own extras from the
 * log, which the reference's Misc tab does not show.
 */
@Composable
internal fun LogMiscTab(log: RandomizerLog, names: LogNames = LogNames.PLAIN) {
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
            LogButton("Share Seed", Pc.Text, 12) { share = true }
        }
        Spacer(Modifier.height(10.dp))
        LogInfo("Pokémon Game:", log.game)
        LogInfo("Randomizer Version:", log.version)
        LogInfo("Random Seed:", log.seed)
        DialogText("Settings String:", 13, Pc.Text, Modifier.padding(top = 4.dp))
        log.settingsString.chunked(39).forEach { DialogText(it, 12, Pc.Text, Modifier.padding(start = 8.dp)) }
        // The run's other passes, each with what rebuilds it (rc32 audit P2 #69), and what the app noted (P3 #90).
        for (p in log.passes) {
            Spacer(Modifier.height(8.dp))
            DialogText((if (p.before) "Before it, " else "After it, ") + p.label + ": " + p.file, 13, Pc.Gold)
            LogInfo("Random Seed:", p.seed)
            DialogText("Settings String:", 13, Pc.Text, Modifier.padding(top = 4.dp))
            if (p.settingsString.isEmpty()) DialogText("Not kept in this log.", 12, Pc.Dim, Modifier.padding(start = 8.dp))
            else p.settingsString.chunked(39).forEach { DialogText(it, 12, Pc.Text, Modifier.padding(start = 8.dp)) }
        }
        for (n in log.notes) {
            Spacer(Modifier.height(6.dp))
            DialogText(n, 12, Pc.Gold)
        }

        Spacer(Modifier.height(12.dp))
        DialogText("From the log (not on the PC tracker's Misc tab)", 12, Pc.Dim)
        Spacer(Modifier.height(4.dp))
        // As the log writes them, or as the tracker names them where the game's own name is cut short (LogNames).
        DialogText("Starters", 13, Pc.Gold)
        log.starters.forEach { DialogText(names.speciesAsWritten(it), 12, Pc.Text) }
        Spacer(Modifier.height(6.dp))
        DialogText("Static encounters", 13, Pc.Gold)
        log.statics.forEach { (a, b) -> DialogText("${names.speciesAsWritten(a)}  ->  ${names.speciesAsWritten(b)}", 12, Pc.Text) }
        if (log.pickup.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            DialogText("Pickup items", 13, Pc.Gold)
            log.pickup.forEach { DialogText(it, 12, Pc.Text) }
        }
    }
    if (share) ShareSeedDialog(log) { share = false }
}

@Composable
private fun LogCheck(label: String, on: Boolean, onToggle: () -> Unit) {
    // A 48dp row read out as a checkbox (rc32 audit P2 #26); it was a 25dp line.
    Row(Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Checkbox) { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
        DialogText(if (on) "[X]" else "[ ]", 13, Pc.Gold, Modifier.width(26.dp))
        DialogText(label, 13, Pc.Text)
    }
}

@Composable
private fun LogInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        DialogText(label, 13, Pc.Text, Modifier.width(150.dp))
        DialogText(value.ifBlank { "---" }, 13, Pc.Text)
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
            DialogText("Share Randomizer Seed", 15, Pc.Gold)
            Spacer(Modifier.height(6.dp))
            DialogText("Copy/paste everything below to share. Load it through Randomizer --> Premade Seed.", 12, Pc.Text)
            if (log.passes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                DialogText("This run took more than one pass: each one is below, in the order to load them.", 12, Pc.Gold)
            }
            Spacer(Modifier.height(8.dp))
            DialogText(text, 12, Pc.Text, Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp))
            Spacer(Modifier.height(8.dp))
            Row {
                LogButton(if (copied) "COPIED" else "COPY", if (copied) Pc.Positive else Pc.Gold) {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Randomizer seed", text))
                    copied = true
                }
                Spacer(Modifier.width(10.dp))
                LogButton("CLOSE", Pc.Text) { onClose() }
            }
        }
    }
}
