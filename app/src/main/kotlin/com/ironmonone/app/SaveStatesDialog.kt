package com.ironmonone.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Button

/**
 * The save-state picker. All eight slots are on screen at once as a 4x2
 * grid of screenshots (a scrolling list showed four and read as "only
 * four"); the picked slot gets a gold frame and ONE action row underneath
 * acts on it: SAVE, LOAD, LOCK/UNLOCK, UNDO. The auto-save sits above the
 * grid with RESUME.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaveStatesDialog(
    auto: StateSlots.Slot,
    slots: List<StateSlots.Slot>,
    current: Int,
    version: Int,
    onPick: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onLock: (StateSlots.Slot, Boolean) -> Unit,
    onUndo: (StateSlots.Slot) -> Unit,
    onDismiss: () -> Unit,
) {
    val picked = slots.firstOrNull { it.n == current } ?: slots.first()
    ShellDialog("Save states · ${slots.size} slots", onDismiss = onDismiss) {
        // Auto-save row.
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(auto, version, Modifier.width(64.dp).height(43.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(auto.title(), style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                Text(if (auto.exists) auto.savedLabel() + " · written when you leave" else "none yet",
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Shell.inkOnPaper)
            }
            Gen3Button("RESUME", accent = auto.exists, enabled = auto.exists) { onLoad(StateSlots.AUTO) }
        }
        ShellDivider()
        Spacer(Modifier.height(6.dp))
        // The grid: two rows of four.
        slots.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { s ->
                    Column(
                        Modifier.weight(1f)
                            .then(if (s.n == current) Modifier.border(2.dp, Pc.Gold) else Modifier.border(1.dp, Color(0x33000000)))
                            .clickable { onPick(s.n) }.padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Thumb(s, version, Modifier.fillMaxWidth().aspectRatio(3f / 2f))
                        Text("${s.n}${if (s.locked) " 🔒" else ""}", fontFamily = Gen3.PixelFont, fontSize = 9.sp, color = Gen3.Ink)
                        Text(if (s.exists) s.savedLabel() else "empty", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Shell.inkOnPaper, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // The picked slot's own line and actions.
        Text(
            "${picked.title()}${if (picked.locked) " · LOCKED" else ""}" +
                if (picked.exists) " · %s · %,d KB".format(picked.savedLabel(), picked.sizeBytes / 1024) else " · empty",
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Shell.inkOnPaper,
        )
        Spacer(Modifier.height(4.dp))
        // Four buttons do not fit one row on a phone; the fourth (UNDO) was clipped.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Gen3Button("SAVE", accent = !picked.exists, enabled = !picked.locked) { onSave(picked.n) }
            Gen3Button("LOAD", enabled = picked.exists) { onLoad(picked.n) }
            Gen3Button(if (picked.locked) "UNLOCK" else "LOCK", enabled = picked.exists || picked.locked) { onLock(picked, !picked.locked) }
            if (picked.hasBackup) Gen3Button("UNDO") { onUndo(picked) }
        }
        Text(
            when {
                picked.locked -> "Locked: SAVE is refused until you unlock it."
                picked.hasBackup -> "UNDO brings back the state this one overwrote."
                else -> "Tap a slot to pick it. Overwrites keep the old state for UNDO."
            },
            fontFamily = Gen3.PixelFont, fontSize = 7.sp, color = Shell.inkOnPaper,
        )
        Spacer(Modifier.height(8.dp))
        Gen3Button("CLOSE", onClick = onDismiss)
    }
}

@Composable
private fun Thumb(s: StateSlots.Slot, version: Int, modifier: Modifier) {
    val thumb = remember(s.n, version, s.savedAt) {
        runCatching { BitmapFactory.decodeFile(s.thumb.path)?.asImageBitmap() }.getOrNull()
    }
    Box(modifier.background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        if (thumb != null) Image(thumb, contentDescription = "${s.title()} screenshot",
            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        else Text("-", color = Color.Gray)
    }
}
