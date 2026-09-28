package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

/**
 * ColorSchemeScreen.lua: the eight colours of the palette by the DS
 * tracker's names, edited as AARRGGBB hex and applied live, saved to
 * prep/theme.txt, with the reference's import and export of a theme string
 * (eight hex values, comma separated) and a reset to the default scheme.
 */
object ThemeStore {
    class Key(val name: String, val get: () -> Color, val set: (Color) -> Unit, val default: Color)

    val KEYS: List<Key> = listOf(
        Key("Main background color", { Pc.Page }, { Pc.Page = it }, Color(0xFF000000)),
        Key("Top box background color", { Pc.Ground }, { Pc.Ground = it }, Color(0xFF222222)),
        Key("Top box border color", { Pc.Border }, { Pc.Border = it }, Color(0xFFAAAAAA)),
        Key("Top box text color", { Pc.Text }, { Pc.Text = it }, Color(0xFFFFFFFF)),
        Key("Positive text color", { Pc.Positive }, { Pc.Positive = it }, Color(0xFF00FF00)),
        Key("Negative text color", { Pc.Negative }, { Pc.Negative = it }, Color(0xFFFF0000)),
        Key("Intermediate text color", { Pc.Gold }, { Pc.Gold = it }, Color(0xFFFFFF00)),
        Key("Bottom box text color", { Pc.Dim }, { Pc.Dim = it }, Color(0xFFAAAAAA)),
    )
    private var file: File? = null

    fun hex(c: Color): String = String.format("%08X", (c.value shr 32).toLong() and 0xFFFFFFFFL)
    fun parse(s: String): Color? = s.trim().removePrefix("#").takeIf { it.length == 8 || it.length == 6 }?.let { h ->
        h.toLongOrNull(16)?.let { v -> Color(if (h.length == 6) (0xFF000000L or v) else v) }
    }

    fun export(): String = KEYS.joinToString(",") { hex(it.get()) }
    fun import(s: String): Boolean {
        val parts = s.split(',').map { parse(it) ?: return false }
        if (parts.size != KEYS.size) return false
        KEYS.forEachIndexed { i, k -> k.set(parts[i]) }
        save(); return true
    }
    fun reset() { KEYS.forEach { it.set(it.default) }; save() }

    fun load(f: File) {
        file = f
        if (!f.isFile) return
        runCatching { import(f.readText().trim()) }
    }
    fun save() { file?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(export()) } } }
}

/**
 * The tracker colour editor. Shell-styled since 2026-09-27 (audit): pixel text
 * at 7 to 10 px, a 15dp "X" and 21dp hex fields were the old look. Only the
 * swatches use the tracker's colours, because they ARE those colours.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ColorThemeDialog(onClose: () -> Unit) {
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var resetArmed by remember { mutableStateOf(false) }
    var edits by remember { mutableStateOf(ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }) }
    // Swatches read the live Pc colours; this bumps them when one is applied.
    var applied by remember { mutableIntStateOf(0) }
    fun refreshEdits() { edits = ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }; applied++ }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    // Two taps, disarmed after 3 s: one tap used to wipe a custom theme.
    LaunchedEffect(resetArmed) { if (resetArmed) { delay(3000); resetArmed = false } }
    LaunchedEffect(copied) { if (copied) { delay(2500); copied = false } }

    Dialog(onDismissRequest = onClose) {
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tracker colours", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium, color = Shell.inkOnPaper, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(Shell.touchTarget)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Shell.inkOnPaper)
                    }
                }
                Text("Hex, 6 or 8 digits (RRGGBB or AARRGGBB). A colour applies once it is valid.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                Spacer(Modifier.height(8.dp))
                ThemeStore.KEYS.forEachIndexed { idx, k ->
                    if (idx > 0) ShellDivider()
                    val v = edits[k.name] ?: ""
                    val valid = ThemeStore.parse(v) != null
                    fun apply(text: String) {
                        ThemeStore.parse(text)?.let { c -> k.set(c); ThemeStore.save(); applied++ }
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // Reading `applied` redraws the swatch after a change.
                            val swatch = applied.let { k.get() }
                            val sw = RoundedCornerShape(6.dp)
                            Box(Modifier.size(28.dp).clip(sw).background(swatch).border(1.dp, Shell.hairline, sw))
                            Text(k.name, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                            val field = RoundedCornerShape(Shell.controlRadius)
                            BasicTextField(
                                value = v,
                                onValueChange = { t ->
                                    val clean = t.filter { it.isLetterOrDigit() || it == '#' }.take(9).uppercase()
                                    edits = edits + (k.name to clean)
                                    // Only a whole value applies; a half-typed one used to repaint the tracker.
                                    val digits = clean.removePrefix("#")
                                    if (digits.length == 6 || digits.length == 8) apply(clean)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Shell.inkOnPaper),
                                cursorBrush = SolidColor(Shell.accent),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { apply(v); focus.clearFocus() }),
                                modifier = Modifier.width(112.dp).heightIn(min = Shell.touchTarget)
                                    .clip(field)
                                    .background(Shell.night)
                                    .border(1.dp, if (valid) Shell.hairline else Shell.dangerOnPaper, field)
                                    .semantics { contentDescription = "${k.name}, hex" },
                                decorationBox = { inner ->
                                    Box(Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart) { inner() }
                                },
                            )
                        }
                        if (!valid) Text("Not a colour. Use 6 or 8 hex digits, like FF8800.",
                            style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button(if (resetArmed) "Sure? Reset colours" else "Reset colours", accent = resetArmed) {
                        if (resetArmed) { ThemeStore.reset(); refreshEdits(); resetArmed = false } else resetArmed = true
                    }
                    Gen3Button(if (showExport) "Hide theme string" else "Export theme") { showExport = !showExport }
                }
                if (showExport) {
                    Spacer(Modifier.height(8.dp))
                    // Selectable and copyable: the pixel text was neither.
                    SelectionContainer {
                        Text(ThemeStore.export(), style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper)
                    }
                    Spacer(Modifier.height(6.dp))
                    Gen3Button(if (copied) "Copied" else "Copy") {
                        clipboard.setText(AnnotatedString(ThemeStore.export())); copied = true
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Import a theme", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it; importError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Theme string") },
                    isError = importError != null,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper),
                )
                // The error is its own line; it used to overwrite what was pasted.
                importError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button("Paste") {
                        clipboard.getText()?.text?.let { importText = it.trim(); importError = null }
                    }
                    Gen3Button("Apply", accent = true, enabled = importText.isNotBlank()) {
                        if (ThemeStore.import(importText.trim())) { refreshEdits(); importText = ""; importError = null }
                        else importError = "That is not a theme string. It should be eight hex colours, separated by commas."
                    }
                }
            }
        }
    }
}
