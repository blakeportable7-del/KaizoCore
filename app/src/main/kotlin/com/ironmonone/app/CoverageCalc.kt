package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The Coverage Calc screen, read from both references on 2026-09-08:
 * Ironmon-Tracker screens/CoverageCalcScreen.lua (Gen 1 to 3 share it) and
 * NDS-Ironmon-Tracker ui/CoverageCalcScreen.lua.
 *
 * Both: the move types under test start as the lead's damaging move types
 * (the Gen 3 file skips fixed-damage moves such as Seismic Toss and Dragon
 * Rage, and Hidden Power); up to six types; tap one to drop it, ADD TYPE to
 * pick another, CLEAR; a "fully evolved only" switch; then every species in
 * the game bucketed by the BEST multiplier any chosen type gets against it,
 * 0x, 1/4x, 1/2x, 1x, 2x, 4x, with Shedinja counted as 0x unless something
 * is super effective. Tap a bucket to see its Pokemon. The Gen 3 screen lists
 * them in dex order; the DS screen sorts them by BST, highest first.
 */
object CoverageCalc {
    /** CoverageCalcScreen.getPartyPokemonEffectiveMoveTypes: damage-dealing moves with no type-based multiplier. */
    val GEN3_EXCLUDED_MOVES = setOf(12, 32, 49, 68, 69, 82, 90, 101, 117, 149, 162, 165, 237, 243, 248, 251, 283, 329, 353)

    /** The seed types from a lead's move rows: (moveId, category, type). Status and fixed-damage moves are out. */
    fun seedTypes(moves: List<Triple<Int, String, String>>, excluded: Set<Int> = GEN3_EXCLUDED_MOVES): List<String> =
        moves.filter { (id, cat, type) -> !cat.startsWith("STA", ignoreCase = true) && id !in excluded && type.isNotBlank() }
            .map { it.third }.distinct().take(6)
}

private val BUCKETS = listOf(0.0 to "0x", 0.25 to "1/4x", 0.5 to "1/2x", 1.0 to "1x", 2.0 to "2x", 4.0 to "4x")

@Composable
fun CoverageCalcDialog(
    seed: List<String>,
    allTypes: List<String>,
    /** The species per bucket for these type names, honouring the switch where the tracker can. */
    compute: (List<String>, Boolean) -> Map<Double, List<Int>>,
    name: (Int) -> String,
    bst: (Int) -> Int,
    sprite: @Composable (Int) -> ImageBitmap?,
    /** False where the tracker has no evolution data (the DS sidecar carries none). */
    fullyEvolvedSupported: Boolean,
    sortByBst: Boolean,
    /** Shown instead of six empty buckets when the tracker has no species table (a DS ROM that was never randomized here). */
    noDataNote: String? = null,
    onClose: () -> Unit,
) {
    var types by remember { mutableStateOf(seed) }
    var fullyEvolved by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(2.0) }
    val data = remember(types, fullyEvolved) { compute(types, fullyEvolved) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("COVERAGE CALC", 10, Pc.Text, Modifier.weight(1f))
                Box(Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onClose() }.padding(horizontal = 10.dp, vertical = 6.dp)) { PixText("X", 9, Pc.Text) }
            }
            noDataNote?.let { PixText(it, 8, Pc.Negative, Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(6.dp))
            // The types under test: tap one to drop it.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                PixText("Move types (tap to remove)", 7, Pc.Dim)
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (types.isEmpty()) PixText("none", 7, Pc.Dim)
                    types.forEach { t ->
                        Box(Modifier.border(1.dp, Pc.Border).background(Pc.Ground).clickable { types = types - t }.padding(horizontal = 5.dp, vertical = 3.dp)) {
                            PixText(t.uppercase(), 7, pcTypeColorByName(t))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (types.size < 6) PcSmallButton("ADD TYPE") { picking = !picking }
                    PcSmallButton("CLEAR") { types = emptyList(); picking = false }
                    if (fullyEvolvedSupported) {
                        Spacer(Modifier.width(4.dp))
                        Row(Modifier.clickable { fullyEvolved = !fullyEvolved }, verticalAlignment = Alignment.CenterVertically) {
                            PixText(if (fullyEvolved) "[x]" else "[ ]", 8, Pc.Text)
                            Spacer(Modifier.width(4.dp))
                            PixText("Fully evolved only", 7, Pc.Text)
                        }
                    }
                }
                if (picking) {
                    Spacer(Modifier.height(6.dp))
                    allTypes.filter { it !in types }.chunked(5).forEach { line ->
                        Row(Modifier.padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            line.forEach { t ->
                                Box(Modifier.border(1.dp, Pc.Border).background(Pc.Ground).clickable { if (types.size < 6) types = types + t; picking = false }.padding(horizontal = 5.dp, vertical = 3.dp)) {
                                    PixText(t.uppercase(), 7, pcTypeColorByName(t))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // The buckets as tabs, each with its count.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                BUCKETS.forEach { (mult, label) ->
                    val n = data[mult]?.size ?: 0
                    val on = tab == mult
                    Column(
                        Modifier.weight(1f).background(if (on) Pc.Page else Pc.Ground).border(1.dp, if (on) Pc.Gold else Pc.Border).clickable { tab = mult }.padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixText("$n", 9, when { mult == 0.0 && n > 0 -> Pc.Negative; mult >= 2.0 -> Pc.Positive; else -> Pc.Text })
                        PixText(label, 7, if (on) Pc.Gold else Pc.Dim)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val ids = (data[tab] ?: emptyList()).let { if (sortByBst) it.sortedByDescending(bst) else it }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ids, key = { it }) { id ->
                    Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = sprite(id)
                        if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(28.dp), contentScale = ContentScale.Fit) else Spacer(Modifier.size(28.dp))
                        Spacer(Modifier.width(6.dp))
                        PixText(name(id), 8, Pc.Text, Modifier.weight(1f))
                        PixText("BST ${bst(id)}", 7, Pc.Dim)
                    }
                }
            }
        }
    }
}
