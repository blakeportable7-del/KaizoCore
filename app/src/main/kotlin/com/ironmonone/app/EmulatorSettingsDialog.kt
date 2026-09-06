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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.Platform

/**
 * The emulator settings page for one console. Rows are grouped; tapping a
 * row cycles its value and the caller applies it to the running core at
 * once (options marked restart land on the next boot). System files are
 * listed with whether they are present and an IMPORT button each.
 */
@Composable
fun EmulatorSettingsDialog(
    platform: Platform,
    values: Map<String, String>,
    onChange: (CoreOptions.Option, String) -> Unit,
    onReset: () -> Unit,
    systemFilePresent: (String) -> Boolean,
    onImportSystemFile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val opts = CoreOptions.forPlatform(platform)
    ShellDialog("${platform.name} settings", onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            opts.groupBy { it.group }.forEach { (group, rows) ->
                if (group == CoreOptions.LINK_IP_GROUP) {
                    LinkAddressField(values, rows, onChange)
                    return@forEach
                }
                if (group == CoreOptions.DSI_GROUP) {
                    DsiSection(values, opts, systemFilePresent, onChange)
                    return@forEach
                }
                Text(group.uppercase(), fontFamily = Gen3.PixelFont, fontSize = 9.sp, color = Shell.inkOnPaper,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                if (group == CoreOptions.LINK_GROUP) {
                    Text("Both phones on the same Wi-Fi, same game. One hosts (Network Server), the other joins with the " +
                        "host's address. This phone: " + (com.ironmonone.app.stream.StreamHub.wifiAddress() ?: "no Wi-Fi address"),
                        style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                }
                rows.forEach { o ->
                    val v = values[o.key] ?: o.default
                    Column(Modifier.fillMaxWidth().clickable { onChange(o, o.next(v)) }.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(o.label + if (o.restart) " (restart)" else "", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                            Text(v, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                                color = if (v != o.default) Pc.Gold.copy(alpha = 0.9f).let { MaterialTheme.colorScheme.primary } else Shell.inkOnPaper)
                        }
                        o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper) }
                    }
                    ShellDivider()
                }
            }
            val files = CoreOptions.systemFiles(platform)
            if (files.isNotEmpty()) {
                Text("SYSTEM FILES", fontFamily = Gen3.PixelFont, fontSize = 9.sp, color = Shell.inkOnPaper,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                Text("Your own dumps. Nothing is downloaded; a missing file leaves the built-in replacement in use.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                files.forEach { (name, what) ->
                    val present = systemFilePresent(name)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name + if (present) " · present" else " · missing", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium, color = if (present) MaterialTheme.colorScheme.primary else Gen3.Ink)
                            Text(what, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                        }
                        Gen3Button(if (present) "REPLACE" else "IMPORT") { onImportSystemFile(name) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("CLOSE", accent = true, onClick = onDismiss)
            Gen3Button("RESET ALL", onClick = onReset)
        }
    }
}

/** Gambatte's twelve address digits, drawn as one field. */
@Composable
private fun LinkAddressField(
    values: Map<String, String>,
    rows: List<CoreOptions.Option>,
    onChange: (CoreOptions.Option, String) -> Unit,
) {
    var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LinkIp.join(values)) }
    androidx.compose.material3.OutlinedTextField(
        value = draft, onValueChange = { draft = it }, singleLine = true,
        label = { Text("Server address (the hosting phone)") }, modifier = Modifier.fillMaxWidth(),
        isError = LinkIp.digits(draft) == null,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Gen3Button("SET ADDRESS", enabled = LinkIp.digits(draft) != null) {
            LinkIp.digits(draft)?.forEachIndexed { i, d ->
                rows.firstOrNull { it.key == LinkIp.key(i + 1) }?.let { onChange(it, d) }
            }
        }
        Text("stored: " + LinkIp.join(values), style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
    }
}

/** DSi mode as one switch, with the file checklist that gates it. */
@Composable
private fun DsiSection(
    values: Map<String, String>,
    opts: List<CoreOptions.Option>,
    present: (String) -> Boolean,
    onChange: (CoreOptions.Option, String) -> Unit,
) {
    val on = DsiMode.isOn(values)
    val missing = DsiMode.missing(present)
    Text("DSI MODE", fontFamily = Gen3.PixelFont, fontSize = 9.sp, color = Shell.inkOnPaper,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    Text(
        "Runs the game as a DSi, with your own DSi dumps from the SYSTEM FILES list below. " +
            "All seven are needed. The core cannot make save states in DSi mode, so the auto-save, " +
            "rewind and the slots are off there. Takes effect on the next boot of the game.",
        style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper,
    )
    Text(
        if (missing.isEmpty()) "All files present." else "Missing: " + missing.joinToString(", "),
        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
        color = if (missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(6.dp))
    fun apply(map: Map<String, String>) {
        map.forEach { (k, v) -> opts.firstOrNull { it.key == k }?.let { onChange(it, v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gen3Button(if (on) "DSI MODE ON" else "ENABLE DSI MODE", accent = on, enabled = !on && missing.isEmpty()) { apply(DsiMode.ON) }
        if (on) Gen3Button("BACK TO DS") { apply(DsiMode.OFF) }
    }
    ShellDivider()
}
