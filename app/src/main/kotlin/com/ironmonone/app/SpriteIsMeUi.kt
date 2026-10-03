package com.ironmonone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Play as your Pokemon" in the two places a player looks for settings while playing: the tracker's gear, and (for a
 * game with no tracker, where the gear does not exist) the emulator settings. Both draw the same controls with the
 * same words (SpriteIsMeCopy); only the look differs, the gear's pixel font and the shell's cards.
 *
 * The switch and the choices are the phone's, not a run's (SpriteIsMeSettings), and this never asks what kind of
 * game is running: it only asks whether the game it is watching is one the emulator side can work on
 * (SpriteIsMeSupport), and if not says so in one line.
 */
@Composable
fun SpriteIsMeGearSection() = SpriteIsMeControls(pix = true)

@Composable
fun SpriteIsMeSettingsSection() = SpriteIsMeControls(pix = false)

@Composable
private fun Header(pix: Boolean, text: String, section: Boolean = false) {
    // In Tracker Setup, the section's head is the dialog's own (GearHead) and the rest is text at the rows' scale: the
    // reference-pixel text was a third style there, and too small to read (Blake, 2026-09-30).
    if (pix) { if (section) GearHead(text) else DialogText(text, 13, Pc.Text, Modifier.padding(top = 8.dp, bottom = 2.dp), heading = true) }
    else Text(text, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun Note(pix: Boolean, text: String, modifier: Modifier = Modifier) {
    if (pix) DialogText(text, 12, Pc.Dim, modifier)
    else Text(text, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = modifier)
}

/** A button: Tracker Setup's own in the gear, the shell's in Settings. */
@Composable
private fun Btn(pix: Boolean, text: String, raw: Boolean = false, onClick: () -> Unit) {
    if (pix) GearButton(text, Modifier, raw = raw, onClick = onClick) else Gen3Button(text, raw = raw, onClick = onClick)
}

@Composable
private fun Check(pix: Boolean, label: String, on: Boolean, radio: Boolean = false, onChange: (Boolean) -> Unit) {
    when {
        pix -> GearToggle(label, on, radio = radio, onChange = onChange)
        radio -> Row(
            Modifier.fillMaxWidth().selectable(selected = on, role = Role.RadioButton, onClick = { onChange(true) })
                .heightIn(min = Shell.touchTarget).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellRadio(on)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        }
        else -> ShellSwitchRow(label, on, onChange = onChange)
    }
}

private fun speciesName(id: Int): String = Favorites.namesInOrder.firstOrNull { it.first == id }?.second ?: "#$id"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpriteIsMeControls(pix: Boolean) {
    val ctx = LocalContext.current
    val filesDir = ctx.filesDir
    SpriteIsMeSettings.ensureLoaded(filesDir)
    val s = SpriteIsMeSettings
    val support = SpriteIsMeSupport.state
    var picking by remember { mutableStateOf(false) }      // the "Always use" picker
    var sheetDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                SpriteIsMeImport.picture(ctx, uri)?.let { SpriteIsMeStore.savePicture(filesDir, it) } == true
            }
            if (ok) s.who = SpriteIsMeSettings.Who.OWN
            if (ok) s.save()
            message = if (ok) SpriteIsMeCopy.OWN_PICTURE else SpriteIsMeCopy.PICTURE_FAILED
        }
    }
    val pickSheets = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val (kept, big) = withContext(Dispatchers.IO) {
                val picked = SpriteIsMeImport.sheets(ctx, uris)
                SpriteIsMeStore.saveSheets(filesDir, picked) to SpriteIsMeStore.tooBig(picked)
            }
            if (kept > 0) { s.who = SpriteIsMeSettings.Who.OWN; s.save() }
            message = if (kept == SpriteIsMeStore.NOT_SAVED) SpriteIsMeCopy.SHEETS_NOT_SAVED else SpriteIsMeCopy.sheetsSaved(kept, big)
        }
    }

    Header(pix, SpriteIsMeCopy.TITLE, section = true)
    val refused: String? = when (support) {
        SpriteIsMeSupport.State.NotGba -> SpriteIsMeCopy.NOT_GBA
        is SpriteIsMeSupport.State.Unsupported -> support.why
        else -> null
    }
    if (refused != null) {
        Note(pix, refused)
        return
    }
    if (support == SpriteIsMeSupport.State.Unknown) Note(pix, SpriteIsMeCopy.LOOKING)
    Note(pix, SpriteIsMeCopy.WHAT)
    Check(pix, SpriteIsMeCopy.TITLE, s.on) { s.on = it; s.save() }
    Note(pix, SpriteIsMeCopy.GENS)   // beside the switch, on or off (Blake, 2026-09-30)
    if (!s.on) return

    // Each choice shows only what it needs (Blake, 2026-09-30: "the game should know your lead pokemon, so there is
    // actually no point in choosing it from the list"): the lead needs nothing, Always use its Pokemon, your own
    // sprite its files.
    Header(pix, SpriteIsMeCopy.WHO)
    // One of the three, and a second tap on Always use or Your own sprite goes back to the lead, the default (Blake,
    // 2026-09-30: "you can't uncheck always use").
    Check(pix, SpriteIsMeCopy.LEAD, s.who == SpriteIsMeSettings.Who.LEAD, radio = true) { s.who = SpriteIsMeSettings.Who.LEAD; s.save() }
    Check(pix, SpriteIsMeCopy.ALWAYS, s.who == SpriteIsMeSettings.Who.ALWAYS, radio = true) {
        if (s.who == SpriteIsMeSettings.Who.ALWAYS) s.who = SpriteIsMeSettings.Who.LEAD
        else {
            s.who = SpriteIsMeSettings.Who.ALWAYS
            if (s.always == 0) picking = true
        }
        s.save()
    }
    if (s.who == SpriteIsMeSettings.Who.ALWAYS) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(pix, if (s.always > 0) speciesName(s.always) else SpriteIsMeCopy.CHOOSE, raw = s.always > 0) { picking = true }
        }
        // The picked Pokemon's shiny (Blake, 2026-10-03), a switch like the section's own; one with no shiny walks plain.
        Check(pix, SpriteIsMeCopy.SHINY, s.alwaysShiny) { s.alwaysShiny = it; s.save() }
    }
    Check(pix, SpriteIsMeCopy.OWN, s.who == SpriteIsMeSettings.Who.OWN, radio = true) {
        s.who = if (s.who == SpriteIsMeSettings.Who.OWN) SpriteIsMeSettings.Who.LEAD else SpriteIsMeSettings.Who.OWN; s.save()
    }
    if (s.who == SpriteIsMeSettings.Who.OWN) OwnSpriteFiles(pix, s, pickPicture, pickSheets, onSheetSettings = { sheetDialog = true }, onRemoved = { message = null })
    message?.let { Note(pix, it) }
    // Imported art the game cannot draw, said where the choice is made (rc32 audit P2 #88, P3 #66).
    SpriteIsMeSupport.ownNote?.takeIf { s.who == SpriteIsMeSettings.Who.OWN && it != message }?.let { Note(pix, it) }

    if (picking) SpeciesPickerDialog(SpriteIsMeCopy.ALWAYS, s.always, onPick = {
        s.always = it; s.who = SpriteIsMeSettings.Who.ALWAYS; s.artVersion++; s.save(); picking = false
    }, onDismiss = { picking = false })
    if (sheetDialog) SheetSettingsDialog(onDismiss = { sheetDialog = false })
}

/** "Your own sprite": what is imported, and the buttons to import, set up or remove it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnSpriteFiles(
    pix: Boolean,
    s: SpriteIsMeSettings,
    pickPicture: androidx.activity.compose.ManagedActivityResultLauncher<PickVisualMediaRequest, android.net.Uri?>,
    pickSheets: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, List<android.net.Uri>>,
    onSheetSettings: () -> Unit,
    onRemoved: () -> Unit,
) {
    val filesDir = LocalContext.current.filesDir
    Note(pix, when (s.own) {
        SpriteIsMeSettings.Own.NONE -> SpriteIsMeCopy.NO_OWN_YET
        SpriteIsMeSettings.Own.PICTURE -> SpriteIsMeCopy.OWN_PICTURE
        SpriteIsMeSettings.Own.SHEET -> SpriteIsMeCopy.OWN_SHEETS
    })
    Note(pix, SpriteIsMeCopy.IN_BACKUPS)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Btn(pix, SpriteIsMeCopy.CHOOSE_PICTURE) {
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        Btn(pix, SpriteIsMeCopy.CHOOSE_SHEETS) {
            pickSheets.launch(arrayOf("image/png", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        if (s.own == SpriteIsMeSettings.Own.SHEET) Btn(pix, SpriteIsMeCopy.SHEET_SETTINGS) { onSheetSettings() }
        if (s.own != SpriteIsMeSettings.Own.NONE) Btn(pix, SpriteIsMeCopy.REMOVE) {
            SpriteIsMeStore.remove(filesDir)
            onRemoved()
        }
    }
}

/**
 * A Pokemon by name, out of every one with a walking sprite (Gen 1 to 9 and the forms, numbered as the Nat. Dex table
 * numbers them, whatever the game), narrowed as the player types.
 */
@Composable
private fun SpeciesPickerDialog(title: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // The tables are read off the main thread (RC35-NOTICED N #10): an empty list for the moment that takes.
    val ix = WalkingPals.ready(ctx)
    val all = remember(ix) { SpriteIsMeLogic.choices(Favorites.namesInOrder) { id, dex -> ix?.find(id, dex) } }
    var query by remember { mutableStateOf("") }
    val shown = remember(all, query) { SpriteIsMeSearch.matches(all, query) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column {
                DialogTitle(title, onDismiss)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, label = { Text(SpriteIsMeCopy.SEARCH) },
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(shown, key = { it.first }) { (id, name) ->
                        Text(name, color = if (id == current) Shell.accentOnNight else Shell.inkOnPaper,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.heightIn(min = Shell.touchTarget).padding(vertical = 12.dp))
                    }
                    if (shown.isEmpty() && ix != null) item { Note(false, SpriteIsMeCopy.NO_MATCH) }
                }
                Spacer(Modifier.height(8.dp))
                Gen3Button(SpriteIsMeCopy.CLOSE, accent = true, onClick = onDismiss)
            }
        }
    }
}

/** The sheet set's frame size and frame lengths, the numbers the extension's own options window asked for. */
@Composable
private fun SheetSettingsDialog(onDismiss: () -> Unit) {
    val s = SpriteIsMeSettings
    var width by remember { mutableStateOf(if (s.sheetWidth > 0) s.sheetWidth.toString() else "") }
    var height by remember { mutableStateOf(if (s.sheetHeight > 0) s.sheetHeight.toString() else "") }
    var idle by remember { mutableStateOf(s.idleLengths) }
    var walk by remember { mutableStateOf(s.walkLengths) }
    var sleep by remember { mutableStateOf(s.sleepLengths) }
    var faint by remember { mutableStateOf(s.faintLengths) }
    val colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogTitle(SpriteIsMeCopy.SHEET_SETTINGS, onDismiss)
                Spacer(Modifier.height(4.dp))
                Note(false, SpriteIsMeCopy.SHEET_HELP)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width, { width = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text(SpriteIsMeCopy.FRAME_WIDTH) }, modifier = Modifier.weight(1f), colors = colors)
                    OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text(SpriteIsMeCopy.FRAME_HEIGHT) }, modifier = Modifier.weight(1f), colors = colors)
                }
                Spacer(Modifier.height(4.dp))
                Note(false, SpriteIsMeCopy.LENGTHS_HINT)
                for ((label, value, set) in listOf(
                    Triple(SpriteIsMeCopy.IDLE, idle) { v: String -> idle = v },
                    Triple(SpriteIsMeCopy.WALK, walk) { v: String -> walk = v },
                    Triple(SpriteIsMeCopy.SLEEP, sleep) { v: String -> sleep = v },
                    Triple(SpriteIsMeCopy.FAINT, faint) { v: String -> faint = v },
                )) {
                    OutlinedTextField(value, { set(SpriteIsMeSettings.lengths(it)) }, singleLine = true, label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = colors)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button(SpriteIsMeCopy.SAVE, accent = true) {
                        s.sheetWidth = width.toIntOrNull()?.coerceIn(0, SpriteArt.MAX_FRAME) ?: 0
                        s.sheetHeight = height.toIntOrNull()?.coerceIn(0, SpriteArt.MAX_FRAME) ?: 0
                        s.idleLengths = idle.trim(); s.walkLengths = walk.trim(); s.sleepLengths = sleep.trim(); s.faintLengths = faint.trim()
                        s.artVersion++
                        s.save()
                        onDismiss()
                    }
                    Gen3Button(SpriteIsMeCopy.CLOSE, onClick = onDismiss)
                }
            }
        }
    }
}
