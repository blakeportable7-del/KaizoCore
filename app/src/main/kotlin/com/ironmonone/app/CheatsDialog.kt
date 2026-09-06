package com.ironmonone.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.Platform

/**
 * The cheat list for one game. Toggle, add, remove. Every change is handed
 * back through [onChange] with the full list, so the caller both saves it
 * and re-sends it to the core; there is no second copy of the state here.
 */
@Composable
fun CheatsDialog(
    title: String,
    platform: Platform,
    cheats: List<CheatStore.Cheat>,
    onChange: (List<CheatStore.Cheat>) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val formats = when (platform) {
        Platform.GBA -> "GameShark, CodeBreaker or Action Replay codes, one per line."
        Platform.GBC -> "GameShark (01XXYYZZ) or Game Genie (XXX-YYY-ZZZ) codes, one per line."
        Platform.NDS -> "Action Replay codes: pairs of 8 hex digits, one pair per line."
    }

    ShellDialog(title, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (cheats.isEmpty() && !adding) {
                Text("No cheats for this game yet.", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            }
            cheats.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onChange(cheats.mapIndexed { j, x -> if (j == i) x.copy(enabled = !x.enabled) else x })
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellCheck(c.enabled)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                        Text(c.code.replace("\n", " · "), style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = Shell.inkOnPaper)
                    }
                    Gen3Button("X") { onChange(cheats.filterIndexed { j, _ -> j != i }) }
                }
            }
            if (adding) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = false,
                    label = { Text("Code") }, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                Text(formats, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button("SAVE", accent = true) {
                        val clean = CheatStore.normalise(code, platform)
                        when {
                            clean == null -> error = "The code is empty."
                            name.isBlank() -> error = "Give it a name."
                            else -> {
                                onChange(cheats + CheatStore.Cheat(name.trim(), code.trim(), true))
                                name = ""; code = ""; error = null; adding = false
                            }
                        }
                    }
                    Gen3Button("CANCEL") { adding = false; error = null }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!adding) Gen3Button("ADD A CODE", accent = true) { adding = true }
            Gen3Button("CLOSE", onClick = onDismiss)
        }
    }
}
