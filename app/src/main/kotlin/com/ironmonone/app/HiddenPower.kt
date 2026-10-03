package com.ironmonone.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Hidden Power's type, which the player sets (Tracker.TrackHiddenPowerType).
 *
 * The game never shows it and the tracker cannot read it without the IVs being
 * known, so the reference leaves it unknown until you pick it in the move's
 * info screen, and remembers the pick against the Pokemon's personality value.
 * Until then the move is typeless on the card: no colour, no STAB, no
 * effectiveness.
 */
object HiddenPowerTypes {
    /**
     * MoveData.HiddenPowerTypeList in order: unknown, then Fighting to Dark as
     * Gen 3 type ids. Normal is not a Hidden Power type.
     */
    val CYCLE: List<Int?> = listOf(null, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17)

    private val types = mutableStateMapOf<Long, Int>()
    private var file: File? = null

    fun of(pid: Long): Int? = types[pid]

    /** InfoScreen's right arrow: the next type, wrapping from Dark back to unknown. */
    fun next(pid: Long) = step(pid, +1)

    /** The left arrow: the previous one, unknown wrapping to Dark. */
    fun prev(pid: Long) = step(pid, -1)

    private fun step(pid: Long, by: Int) {
        if (pid == 0L) return
        val i = CYCLE.indexOf(types[pid]).coerceAtLeast(0)
        val t = CYCLE[((i + by) % CYCLE.size + CYCLE.size) % CYCLE.size]
        if (t == null) types.remove(pid) else types[pid] = t
        save()
    }

    fun load(f: File) {
        file = f
        types.clear()
        runCatching {
            DiskWriter.read(f)?.lineSequence()?.forEach { line ->
                val (k, v) = line.split('=').takeIf { it.size == 2 } ?: return@forEach
                val pid = k.trim().toLongOrNull() ?: return@forEach
                val t = v.trim().toIntOrNull() ?: return@forEach
                if (t in CYCLE) types[pid] = t
            }
        }
    }

    /** Whole or not at all, off the main thread (DiskWriter): it was rewritten in place on each tap (rc32 audit P2 #65). */
    private fun save() {
        val f = file ?: return
        DiskWriter.write(f, types.entries.joinToString("") { "${it.key}=${it.value}\n" })
    }
}

private val LEFT_ARROW = listOf(
    "0000000000", "0001100000", "0011000000", "0110000000", "1111111110",
    "1111111110", "0110000000", "0011000000", "0001100000", "0000000000",
)
private val RIGHT_ARROW = listOf(
    "0000000000", "0000011000", "0000001100", "0000000110", "0111111111",
    "0111111111", "0000000110", "0000001100", "0000011000", "0000000000",
)

/**
 * The type tag with InfoScreen's HiddenPowerPrev and HiddenPowerNext arrows
 * (Constants.PixelImages LEFT_ARROW and RIGHT_ARROW) either side, for your own
 * Pokemon's Hidden Power. "???" until a type is set.
 */
@Composable
internal fun HiddenPowerPicker(pid: Long) {
    val t = HiddenPowerTypes.of(pid)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Each arrow a 48dp button with a spoken name (rc32 audit P2 #19): they were the arrow's own pixels, unnamed.
        HiddenPowerStep(HiddenPowerCopy.PREVIOUS) { HiddenPowerTypes.prev(pid) }
        if (t != null) InfoTypeTag(com.ironmonone.tracker.Gen3Types.name(t), pcTypeColor(t))
        else InfoTag("???", Pc.Dim)
        HiddenPowerStep(HiddenPowerCopy.NEXT) { HiddenPowerTypes.next(pid) }
    }
}

@Composable
private fun HiddenPowerStep(spoken: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.sizeIn(minWidth = PcMin.DIALOG_TOUCH_DP.dp, minHeight = PcMin.DIALOG_TOUCH_DP.dp)
            .clickable(onClickLabel = spoken, role = androidx.compose.ui.semantics.Role.Button) { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { PcPixelImage(if (spoken == HiddenPowerCopy.NEXT) RIGHT_ARROW else LEFT_ARROW, Pc.Text) }
}
