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
 * The user's whole theme (2026-09-29): the eight colours the editor names, and what a
 * preset or an auto theme adds to them, which are the lower box's own text, border and
 * background, the header's text and its ground, the DS tracker's alternate positive and
 * negative pair, the move-type bar and the category icons. The user's theme was only the
 * eight until now, so an auto theme letting go could only clear the rest to nothing, and
 * a preset with a lower box of its own would have been lost with it. [keys] is in
 * [ThemeStore.KEYS] order. A null extra falls back the way [Pc]'s getters do.
 */
data class WholeTheme(
    val page: Color, val ground: Color, val border: Color, val text: Color,
    val positive: Color, val negative: Color, val gold: Color, val dim: Color,
    val headerX: Color? = null, val headerGroundX: Color? = null, val lowerTextX: Color? = null,
    val lowerBorderX: Color? = null, val lowerGroundX: Color? = null,
    val altPositiveX: Color? = null, val altNegativeX: Color? = null,
    val moveTypeBar: Boolean = false, val categoryIconsX: Boolean? = null,
) {
    val keys: List<Color> get() = listOf(page, ground, border, text, positive, negative, gold, dim)
    val header: Color get() = headerX ?: text
    val lowerText: Color get() = lowerTextX ?: text
    val lowerBorder: Color get() = lowerBorderX ?: border
    val lowerGround: Color get() = lowerGroundX ?: ground

    /** True when nothing rides on the eight colours. */
    val plain: Boolean
        get() = headerX == null && headerGroundX == null && lowerTextX == null && lowerBorderX == null &&
            lowerGroundX == null && altPositiveX == null && altNegativeX == null && !moveTypeBar && categoryIconsX == null

    companion object {
        /** The eight colours in [ThemeStore.KEYS] order, and nothing else. */
        fun ofKeys(keys: List<Color>): WholeTheme {
            require(keys.size == 8) { "eight colors, not ${keys.size}" }
            return WholeTheme(keys[0], keys[1], keys[2], keys[3], keys[4], keys[5], keys[6], keys[7])
        }
    }
}

/**
 * ColorSchemeScreen.lua: the eight colours of the palette by the DS
 * tracker's names, edited as AARRGGBB hex and applied live, saved to
 * prep/theme.txt, with the reference's import and export of a theme string
 * (eight hex values, comma separated) and a reset to the default scheme.
 *
 * What is saved is the whole theme (2026-09-29, [WholeTheme]). The file is still one
 * line that starts with the eight AARRGGBB values, so a file from before presets loads
 * as it always did. A theme with more than those eight (a preset with a lower box of its
 * own) continues after a bar, nine comma separated tokens: the header text, header ground,
 * lower text, lower border, lower ground, alternate positive and alternate negative as a
 * colour or a dash for none, then the move-type bar as 1 or 0 and the category icons as
 * 1, 0 or a dash. A plain theme is written exactly as it was, so its export is unchanged.
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

    /** What a fresh install shows and what Reset colours returns to: the eight defaults, nothing extra. */
    val FACTORY: WholeTheme by lazy { WholeTheme.ofKeys(KEYS.map { it.default }) }

    private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    fun hex(c: Color): String = String.format("%08X", (c.value shr 32).toLong() and 0xFFFFFFFFL)
    // Digits only: toLongOrNull(16) also takes a sign, and "-12345" is six characters.
    fun parse(s: String): Color? = s.trim().removePrefix("#")
        .takeIf { d -> (d.length == 8 || d.length == 6) && d.all { c -> isHexDigit(c) } }?.let { h ->
            h.toLongOrNull(16)?.let { v -> Color(if (h.length == 6) (0xFF000000L or v) else v) }
        }

    /** The whole theme as it shows now, whichever of the user's or an auto theme's it is. */
    fun snapshot(): WholeTheme = WholeTheme(
        page = Pc.Page, ground = Pc.Ground, border = Pc.Border, text = Pc.Text,
        positive = Pc.Positive, negative = Pc.Negative, gold = Pc.Gold, dim = Pc.Dim,
        headerX = Pc.HeaderX, headerGroundX = Pc.HeaderGroundX, lowerTextX = Pc.LowerTextX,
        lowerBorderX = Pc.LowerBorderX, lowerGroundX = Pc.LowerGroundX,
        altPositiveX = Pc.AltPositiveX, altNegativeX = Pc.AltNegativeX,
        moveTypeBar = Pc.moveTypeBar, categoryIconsX = Pc.categoryIconsX,
    )

    /** Puts every colour and override of [w] on the palette, the extras that are null included. Nothing is saved. */
    fun show(w: WholeTheme) {
        Pc.Page = w.page; Pc.Ground = w.ground; Pc.Border = w.border; Pc.Text = w.text
        Pc.Positive = w.positive; Pc.Negative = w.negative; Pc.Gold = w.gold; Pc.Dim = w.dim
        Pc.HeaderX = w.headerX; Pc.HeaderGroundX = w.headerGroundX; Pc.LowerTextX = w.lowerTextX
        Pc.LowerBorderX = w.lowerBorderX; Pc.LowerGroundX = w.lowerGroundX
        Pc.AltPositiveX = w.altPositiveX; Pc.AltNegativeX = w.altNegativeX
        Pc.moveTypeBar = w.moveTypeBar; Pc.categoryIconsX = w.categoryIconsX
    }

    /** [w] becomes the user's own theme: shown and saved. */
    fun adopt(w: WholeTheme) { show(w); save() }

    /**
     * One colour edited in the editor, then saved. Two extras follow their key while they
     * still hold its old value, because the editor has no row of its own for them: a
     * preset's header ground is the main background (the reference draws the header on it),
     * and its lower text is the bottom box text. Without this, editing either after picking
     * a preset changed the quiet lines and left the moves table and the header as they were.
     */
    fun edit(k: Key, c: Color) {
        val before = k.get()
        k.set(c)
        if (k === KEYS[0] && Pc.HeaderGroundX == before) Pc.HeaderGroundX = c
        if (k === KEYS[7] && Pc.LowerTextX == before) Pc.LowerTextX = c
        save()
    }

    /** The theme as one line, see the class comment. A plain theme is just its eight colours. */
    fun encode(w: WholeTheme): String {
        val keys = w.keys.joinToString(",") { hex(it) }
        if (w.plain) return keys
        val colours = listOf(w.headerX, w.headerGroundX, w.lowerTextX, w.lowerBorderX, w.lowerGroundX, w.altPositiveX, w.altNegativeX)
            .map { c -> c?.let { hex(it) } ?: "-" }
        val flags = listOf(if (w.moveTypeBar) "1" else "0", w.categoryIconsX?.let { if (it) "1" else "0" } ?: "-")
        return keys + "|" + (colours + flags).joinToString(",")
    }

    /** The theme [s] holds, or null when it is not one. A bar with the wrong tail after it is not one either. */
    fun decode(s: String): WholeTheme? {
        val text = s.trim()
        val bar = text.indexOf('|')
        val keys = (if (bar < 0) text else text.substring(0, bar)).split(',').map { parse(it) ?: return null }
        if (keys.size != KEYS.size) return null
        if (bar < 0) return WholeTheme.ofKeys(keys)
        val tail = text.substring(bar + 1).split(',').map { it.trim() }
        if (tail.size != 9) return null
        // A colour, or a dash for none: a plain loop, so a bad token is told apart from a dash.
        val colours = ArrayList<Color?>()
        for (t in tail.take(7)) colours += if (t == "-") null else (parse(t) ?: return null)
        val typeBar = when (tail[7]) { "1" -> true; "0" -> false; else -> return null }
        val icons: Boolean? = when (tail[8]) { "1" -> true; "0" -> false; "-" -> null; else -> return null }
        return WholeTheme.ofKeys(keys).copy(
            headerX = colours[0], headerGroundX = colours[1], lowerTextX = colours[2], lowerBorderX = colours[3],
            lowerGroundX = colours[4], altPositiveX = colours[5], altNegativeX = colours[6],
            moveTypeBar = typeBar, categoryIconsX = icons,
        )
    }

    fun export(): String = encode(snapshot())
    fun import(s: String): Boolean {
        val w = decode(s) ?: return false
        adopt(w); return true
    }
    fun reset() { adopt(FACTORY) }

    fun load(f: File) {
        file = f
        runCatching {
            val text = (DiskWriter.read(f) ?: return).trim()
            // The colours are what matter: a damaged tail must not cost the player the eight.
            (decode(text) ?: decode(text.substringBefore('|')))?.let { show(it) }
        }
    }
    /**
     * Whole (SafeWrite), on the writer's thread (DiskWriter): each valid hex typed and each preset tap saves, and the
     * sync ran on the main thread while a new run could be writing hundreds of MB (rc32 audit P3 #69).
     */
    fun save() { file?.let { DiskWriter.write(it, export()) } }

    /** Tests only: stop writing to the file [load] was given. */
    internal fun detach() { file = null }
}

/** The colour editor's hex fields as it opens. */
internal object ThemeEditor {
    /**
     * The user's own eight colours, not an auto theme's: the fields were read during the first draw, before the editor
     * held the auto theme off, so they showed the auto colours, and Done on a field saved one over the user's theme
     * (rc32 audit P2 #93). AutoTheme.userTheme is the theme the hold puts back.
     */
    fun openingHex(): Map<String, String> {
        val user = AutoTheme.userTheme().keys
        return ThemeStore.KEYS.mapIndexed { i, k -> k.name to ThemeStore.hex(user[i]) }.toMap()
    }
}

/**
 * The tracker colour editor. Shell-styled since 2026-09-27 (audit): pixel text
 * at 7 to 10 px, a 15dp "X" and 21dp hex fields were the old look. Only the
 * swatches use the tracker's colours, because they ARE those colours.
 *
 * Since 2026-09-29 it opens on the presets (the PC tracker's, or the DS tracker's on a DS
 * game [ds]) and the image behind the tracker, ThemePicker.kt, and the hex editor follows.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ColorThemeDialog(ds: Boolean = false, onClose: () -> Unit) {
    // What is edited and saved is the user's own theme, never an auto theme showing.
    androidx.compose.runtime.DisposableEffect(Unit) { AutoTheme.suspend(); onDispose { AutoTheme.resume() } }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var resetArmed by remember { mutableStateOf(false) }
    var edits by remember { mutableStateOf(ThemeEditor.openingHex()) }
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
                    Text("Tracker colors", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium, color = Shell.inkOnPaper, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(Shell.touchTarget)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Shell.inkOnPaper)
                    }
                }
                ThemePresetSection(ds) { refreshEdits() }
                Spacer(Modifier.height(16.dp))
                TrackerImageSection()
                Spacer(Modifier.height(16.dp))
                Text("Edit colors", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                Text("Hex, 6 or 8 digits (RRGGBB or AARRGGBB). A color applies once it is valid.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                Spacer(Modifier.height(8.dp))
                ThemeStore.KEYS.forEachIndexed { idx, k ->
                    if (idx > 0) ShellDivider()
                    val v = edits[k.name] ?: ""
                    val valid = ThemeStore.parse(v) != null
                    fun apply(text: String) {
                        ThemeStore.parse(text)?.let { c -> ThemeStore.edit(k, c); applied++ }
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
                        if (!valid) Text("Not a color. Use 6 or 8 hex digits, like FF8800.",
                            style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button(if (resetArmed) "Sure? Reset colors" else "Reset colors", accent = resetArmed) {
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
                        else importError = "That is not a theme string. It should be eight hex colors, separated by commas."
                    }
                }
            }
        }
    }
}
