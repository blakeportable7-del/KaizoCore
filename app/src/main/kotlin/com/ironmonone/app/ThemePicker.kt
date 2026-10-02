package com.ironmonone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The two sections the colour editor opens on (2026-09-29): the presets, and the image behind the tracker.
 * ColorThemeDialog (Theme.kt) puts them above the hex editor, which is unchanged.
 */

/**
 * The presets: a tile for each, drawn in its own colours, the one in use marked, and Save as new and Remove for
 * the player's own, as the PC tracker's theme screen has them. A tap applies at once and saves as the player's own
 * theme ([ThemePresets.apply]); [onApplied] lets the editor below refresh its hex fields.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ThemePresetSection(ds: Boolean, onApplied: () -> Unit) {
    // Both read state, so the tiles follow a tap, an edit below and a save.
    val now = ThemeStore.snapshot()
    val yours = ThemePresets.yours
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var removeArmed by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // Two taps, disarmed after 3 s, as Reset colours does.
    LaunchedEffect(removeArmed) { if (removeArmed) { delay(3000); removeArmed = false } }

    Text("Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
    Text(if (ds) "The DS tracker's own themes. Tap one to use it now." else "The PC tracker's themes. Tap one to use it now.",
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
    if (TrackerOptions.autoPokemonThemes) {
        // The setting is TrackerOptions.autoPokemonThemes, "Auto Pokemon Themes" in the tracker's setup.
        Text("Auto Pok\u00e9mon Themes is on, so the lead Pok\u00e9mon's colours show while you play. " +
            "Turn it off in the tracker's setup to keep the preset you pick.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemePresets.builtIns(ds).forEach { p ->
            PresetTile(p, current = p.theme == now) { ThemePresets.apply(p); note = null; removeArmed = false; onApplied() }
        }
    }
    if (yours.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Yours", style = MaterialTheme.typography.labelLarge, color = Shell.inkOnPaper)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            yours.forEach { p ->
                PresetTile(p, current = p.theme == now) { ThemePresets.apply(p); note = null; removeArmed = false; onApplied() }
            }
        }
    }
    // The saved preset in use is the one Remove takes out, as the PC tracker removes the one it is showing.
    val inUse = yours.firstOrNull { it.theme == now }
    if (inUse != null) {
        Spacer(Modifier.height(8.dp))
        Gen3Button(if (removeArmed) "Sure? Remove ${inUse.name}" else "Remove ${inUse.name}", accent = removeArmed, raw = true) {
            if (removeArmed) { ThemePresets.removeYours(inUse.name); removeArmed = false; note = null } else removeArmed = true
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(ThemePresets.MAX_NAME); note = null },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Name for these colours") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper),
    )
    Spacer(Modifier.height(6.dp))
    Gen3Button("Save these colours", accent = true, enabled = name.isNotBlank()) {
        val kept = ThemePresets.cleanName(name)
        when (ThemePresets.saveYours(name)) {
            ThemePresets.SaveResult.SAVED -> { note = "Saved as $kept." to false; name = "" }
            ThemePresets.SaveResult.REPLACED -> { note = "Replaced $kept." to false; name = "" }
            ThemePresets.SaveResult.EMPTY -> note = "Type a name first." to true
            ThemePresets.SaveResult.RESERVED -> note = "$kept is the name of a built-in preset. Pick another." to true
            ThemePresets.SaveResult.FAILED -> note = "Could not save. The phone refused the write." to true
        }
        removeArmed = false
        focus.clearFocus()
    }
    note?.let { (text, isError) ->
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) Shell.dangerOnPaper else Shell.hintOnPaper,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** One preset: its preview and its name, the one in use ringed in the accent. Read as a radio button. */
@Composable
private fun PresetTile(p: ThemePresets.Preset, current: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Shell.controlRadius)
    Column(
        Modifier.width(88.dp).clip(shape).background(Shell.raised)
            .border(if (current) 2.dp else 1.dp, if (current) Shell.accent else Shell.hairline, shape)
            .selectable(selected = current, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (current) "${p.name}, in use" else p.name }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemePreview(p.theme, Modifier.fillMaxWidth().height(46.dp))
        Spacer(Modifier.height(4.dp))
        // minLines = 2: a one-line name made a shorter tile than its two-line neighbours (emulator, 2026-09-29).
        Text(p.name, style = MaterialTheme.typography.labelSmall, color = Shell.inkOnPaper, minLines = 2, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * A small tracker in a theme's colours, after the PC tracker's own preview (Drawing.drawTrackerThemePreview): the
 * main background, the top box with lines in the top text, intermediate, positive and negative colours, the header,
 * and the lower box with three moves, their names in their type colours (Fire, Water, Grass) as the tracker draws them
 * on every theme (TrackerLook.moveName). [page] false draws only the boxes, to lay them over the image.
 */
@Composable
internal fun ThemePreview(t: WholeTheme, modifier: Modifier = Modifier, page: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (page) drawRect(t.page)
        val edge = 1.dp.toPx()
        val m = w * 0.06f
        val bw = w - 2 * m
        val bar = h * 0.045f
        fun line(x: Float, y: Float, wide: Float, c: Color) = drawRect(c, Offset(x, y), Size(wide, bar))

        val topY = h * 0.07f
        val topH = h * 0.42f
        drawRect(t.ground, Offset(m, topY), Size(bw, topH))
        drawRect(t.border, Offset(m, topY), Size(bw, topH), style = Stroke(edge))
        val left = m + bw * 0.07f
        val right = m + bw * 0.60f
        for (i in 0..2) {
            val y = topY + topH * (0.18f + i * 0.22f)
            line(left, y, bw * listOf(0.40f, 0.30f, 0.34f)[i], if (i == 1) t.gold else t.text)
            line(right, y, bw * listOf(0.28f, 0.28f, 0.22f)[i], when (i) { 0 -> t.positive; 1 -> t.negative; else -> t.text })
        }

        line(m + bw * 0.05f, topY + topH + h * 0.045f, bw * 0.5f, t.header)

        val lowY = topY + topH + h * 0.13f
        val lowH = h * 0.36f
        drawRect(t.lowerGround, Offset(m, lowY), Size(bw, lowH))
        drawRect(t.lowerBorder, Offset(m, lowY), Size(bw, lowH), style = Stroke(edge))
        val types = listOf(pcTypeColor(10), pcTypeColor(11), pcTypeColor(12))
        for (i in 0..2) {
            val y = lowY + lowH * (0.16f + i * 0.27f)
            val x = m + bw * 0.06f
            line(x, y, bw * 0.44f, TrackerLook.readable(types[i], t.lowerGround, t.lowerText))
            line(m + bw * 0.66f, y, bw * 0.24f, t.lowerText)
        }
    }
}

/**
 * The image behind the tracker: the system photo picker (images only, so no storage permission), the copy kept in
 * prep/tracker-bg.jpg, how much black goes over it and whether it fills or fits. A small live preview shows the
 * image as the tracker will, with its dim and its fit, under the theme's boxes.
 */
@Composable
internal fun TrackerImageSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true; error = null
            scope.launch {
                // Decoding, turning, scaling and saving a photo take a moment: never on the main thread.
                val ok = withContext(Dispatchers.IO) { TrackerBackground.importFrom(context.applicationContext, uri) }
                busy = false
                if (!ok) error = "That image could not be read. Try another one."
            }
        }
    }
    val hasImage = TrackerBackground.image != null

    Text("Image behind the tracker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
    Text("Your own photo, kept inside KaizoCore and scaled to at most ${TrackerBackground.MAX_LONG_SIDE} px on its long side. " +
        "The boxes keep their own colours: give a box colour 8 hex digits (AARRGGBB) to let the image show through it.",
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
    Text("The OBS stream page has its own colours and does not show the image.",
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasImage) {
            val shape = RoundedCornerShape(Shell.controlRadius)
            Box(Modifier.width(96.dp).height(144.dp).clip(shape).border(1.dp, Shell.hairline, shape).hostBackdrop()) {
                ThemePreview(ThemeStore.snapshot(), Modifier.matchParentSize(), page = false)
            }
        }
        Column(Modifier.weight(1f)) {
            Gen3Button(if (hasImage) "Choose another image" else "Choose image", enabled = !busy) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (hasImage) {
                Spacer(Modifier.height(6.dp))
                Gen3Button("Remove image", enabled = !busy) { TrackerBackground.clear() }
                Spacer(Modifier.height(10.dp))
                Text("Dim ${TrackerBackground.dim}%", style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Slider(
                    value = TrackerBackground.dim.toFloat(),
                    onValueChange = { TrackerBackground.changeDim(it.roundToInt()) },
                    onValueChangeFinished = { TrackerBackground.save() },
                    valueRange = 0f..TrackerBackground.MAX_DIM.toFloat(),
                    steps = TrackerBackground.MAX_DIM / 5 - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = Shell.accent, activeTrackColor = Shell.accent, inactiveTrackColor = Shell.raised,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Dim, percent" },
                )
                Text("See-through boxes ${TrackerBackground.seeThrough}%", style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                Slider(
                    value = TrackerBackground.seeThrough.toFloat(),
                    onValueChange = { TrackerBackground.changeSeeThrough(it.roundToInt()) },
                    onValueChangeFinished = { TrackerBackground.save() },
                    valueRange = 0f..TrackerBackground.MAX_SEE_THROUGH.toFloat(),
                    steps = TrackerBackground.MAX_SEE_THROUGH / 5 - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = Shell.accent, activeTrackColor = Shell.accent, inactiveTrackColor = Shell.raised,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.semantics { contentDescription = "See-through boxes, percent" },
                )
                Spacer(Modifier.height(4.dp))
                ShellSegmented(
                    values = TrackerBackground.Fit.entries.map { it.name },
                    selected = TrackerBackground.fit.name,
                    label = { TrackerBackground.Fit.valueOf(it).label },
                    onSelect = { TrackerBackground.changeFit(TrackerBackground.Fit.valueOf(it)) },
                )
                Text("Fill crops the image to cover the tracker. Fit shows all of it.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (busy) ShellBusy()
    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper, modifier = Modifier.padding(top = 4.dp))
    }
}
