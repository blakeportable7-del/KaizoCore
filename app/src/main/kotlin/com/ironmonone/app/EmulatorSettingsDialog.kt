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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.Platform

/**
 * The emulator settings page for one console. Rows are grouped. A number
 * scale is a stepper, a short choice is inline chips, a long one opens a
 * picker; the caller applies a change to the running core at once (options
 * marked restart land on the next boot). System files are listed with
 * whether they are present and an IMPORT button each.
 *
 * Every row used to be a forward-only cycle: the GB internal palette was up
 * to 46 taps from where you were, the low-pass range 18 (2026-09-27, audit).
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
    var picking by remember { mutableStateOf<CoreOptions.Option?>(null) }
    // Reset all wipes every changed option for the console, so it takes two taps;
    // the second has to come within 3 s (2026-09-27, audit).
    var resetArmed by remember { mutableStateOf(false) }
    LaunchedEffect(resetArmed) { if (resetArmed) { kotlinx.coroutines.delay(3000); resetArmed = false } }
    ShellDialog("${if (platform.name == "NDS") "DS" else platform.name} settings", onDismiss = onDismiss) {
        // The buttons are inside the content, which ShellDialog scrolls as one: the
        // list used to be capped at 520dp with the buttons under it, which put them
        // off-screen in landscape (2026-09-27, audit).
        Column {
            // Play as your Pokemon: a game with no tracker has no tracker gear, and this is reachable in every mode.
            if (platform == Platform.GBA) { SpriteIsMeSettingsSection(); ShellDivider() }
            opts.groupBy { it.group }.forEach { (group, rows) ->
                if (group == CoreOptions.LINK_IP_GROUP) {
                    LinkAddressField(values, rows, onChange)
                    return@forEach
                }
                if (group == CoreOptions.DSI_GROUP) {
                    DsiSection(values, opts, systemFilePresent, onChange)
                    return@forEach
                }
                SectionTitle(group)
                if (group == CoreOptions.LINK_GROUP) {
                    Text("Both phones on the same Wi-Fi, same game. One hosts (Network server), the other joins with the " +
                        "host's address. This phone: " + (com.ironmonone.app.stream.StreamHub.wifiAddress() ?: "no Wi-Fi address"),
                        style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                }
                rows.forEach { o ->
                    OptionRow(o, values[o.key] ?: o.default, onChange) { picking = o }
                    ShellDivider()
                }
            }
            val files = CoreOptions.systemFiles(platform)
            if (files.isNotEmpty()) {
                SectionTitle("System files")
                Text("Your own dumps. Nothing is downloaded; a missing file leaves the built-in replacement in use.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                files.forEach { (name, what) ->
                    val present = systemFilePresent(name)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name + if (present) " · present" else " · missing", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium, color = if (present) Shell.goodOnPaper else Gen3.Ink)
                            Text(what, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                        }
                        Gen3Button(if (present) "REPLACE" else "IMPORT") { onImportSystemFile(name) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button("Close", accent = true, onClick = onDismiss)
                Gen3Button(if (resetArmed) "Sure? Reset all" else "Reset all") {
                    if (resetArmed) { resetArmed = false; onReset() } else resetArmed = true
                }
            }
            if (resetArmed) {
                Text("Tap again to put every ${platform.name} setting back to its default.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
            }
        }
    }
    picking?.let { o ->
        OptionPicker(o, values[o.key] ?: o.default, onPick = { onChange(o, it); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 13.sp, color = Shell.hintOnPaper,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** One option: a stepper for a number scale, chips for four choices or fewer, else a row that opens the picker. */
@Composable
private fun OptionRow(o: CoreOptions.Option, v: String, onChange: (CoreOptions.Option, String) -> Unit, onPick: () -> Unit) {
    val title = o.label + if (o.restart) " (restart)" else ""
    val ns = o.numbers
    when {
        ns != null -> Column(Modifier.fillMaxWidth().heightIn(min = Shell.touchTarget).padding(vertical = 6.dp)) {
            val asNumber = v.toIntOrNull()
            val from = asNumber ?: ns.first()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink, modifier = Modifier.weight(1f))
                // A typed value that snaps back to the current one changes nothing, so the
                // stepper would keep showing the typed text; bumping the key resets it.
                var nonce by remember { mutableStateOf(0) }
                key(nonce) {
                    ShellStepper(from, ns.first()..ns.last(), onChange = { n ->
                        val snapped = o.snap(n, from)
                        if (snapped == v) nonce++ else onChange(o, snapped)
                    })
                }
            }
            if (o.extras.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ShellSegmented(listOf(NUMBER) + o.extras, if (asNumber != null) NUMBER else v,
                    label = { if (it == NUMBER) "Set level" else CoreOptions.display(it) },
                    onSelect = { pick ->
                        if (pick != NUMBER) { if (pick != v) onChange(o, pick) }
                        else if (asNumber == null) onChange(o, o.default.takeIf { it.toIntOrNull() != null } ?: ns.first().toString())
                    })
            }
            o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper, modifier = Modifier.padding(top = 4.dp)) }
        }
        o.values.size <= 4 -> Column(Modifier.fillMaxWidth().heightIn(min = Shell.touchTarget).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            Spacer(Modifier.height(6.dp))
            ShellSegmented(o.values, v, label = CoreOptions::display, onSelect = { if (it != v) onChange(o, it) })
            o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper, modifier = Modifier.padding(top = 4.dp)) }
        }
        else -> Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Choose") { onPick() }
                .heightIn(min = Shell.touchTarget).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink, modifier = Modifier.weight(1f))
                Text(CoreOptions.display(v) + "  ›", style = MaterialTheme.typography.bodyMedium,
                    color = if (v != o.default) Shell.accentOnNight else Shell.inkOnPaper)
            }
            o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper) }
        }
    }
}

/** The chip that stands for "a number from the stepper" beside a number option's extras. */
private const val NUMBER = "\u0000number"

/** A long option list: a filter and every value, the current one marked; a tap chooses. */
@Composable
private fun OptionPicker(o: CoreOptions.Option, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val q = filter.trim()
    val shown = o.values.filter { q.isEmpty() || CoreOptions.display(it).contains(q, ignoreCase = true) || it.contains(q, ignoreCase = true) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = o.values.indexOf(current).coerceAtLeast(0))
    val listMax = (LocalConfiguration.current.screenHeightDp - 300).coerceAtLeast(120).dp
    ShellDialog(o.label, onDismiss = onDismiss) {
        if (o.values.size > 8) {
            androidx.compose.material3.OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true,
                label = { Text("Filter") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        if (shown.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
        // Bounded height: the list sits inside ShellDialog's scroll, which cannot hold an unbounded lazy list.
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax), state = listState) {
            items(shown, key = { it }) { v ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Shell.touchTarget).clickable { onPick(v) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(v == current)
                    Spacer(Modifier.width(12.dp))
                    Text(CoreOptions.display(v), style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Gen3Button("Cancel", onClick = onDismiss)
    }
}

/** Gambatte's twelve address digits, drawn as one field. */
@Composable
private fun LinkAddressField(
    values: Map<String, String>,
    rows: List<CoreOptions.Option>,
    onChange: (CoreOptions.Option, String) -> Unit,
) {
    var draft by remember { mutableStateOf(LinkIp.join(values)) }
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
    SectionTitle("DSi mode")
    Text(
        "Runs the game as a DSi, with your own DSi dumps from the system files list below. " +
            "All seven are needed. The core cannot make save states in DSi mode, so the auto-save, " +
            "rewind and the slots are off there. Takes effect on the next boot of the game.",
        style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper,
    )
    Text(
        if (missing.isEmpty()) "All files present." else "Missing: " + missing.joinToString(", "),
        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
        color = if (missing.isEmpty()) Shell.goodOnPaper else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(6.dp))
    fun apply(map: Map<String, String>) {
        map.forEach { (k, v) -> opts.firstOrNull { it.key == k }?.let { onChange(it, v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Written in sentence case here rather than left to Shell.label, which would read "Dsi".
        Gen3Button(if (on) "DSi mode on" else "Turn on DSi mode", accent = on, enabled = !on && missing.isEmpty()) { apply(DsiMode.ON) }
        if (on) Gen3Button("Back to DS") { apply(DsiMode.OFF) }
    }
    ShellDivider()
}
