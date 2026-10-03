package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Rage, and counts Hidden Power only once the player has set its type); up to six types; tap one to drop it, ADD TYPE to
 * pick another, CLEAR; a "fully evolved only" switch; then every species in
 * the game bucketed by the BEST multiplier any chosen type gets against it,
 * 0x, 1/4x, 1/2x, 1x, 2x, 4x, with Shedinja counted as 0x unless something
 * is super effective. Tap a bucket to see its Pokemon, highest BST first on
 * both (the Gen 3 screen's Pager.defaultSort, CoverageCalcScreen.lua:132-134,
 * breaks ties by id; it was listed in dex order here until 2026-09-29).
 */
object CoverageCalc {
    /** CoverageCalcScreen.getPartyPokemonEffectiveMoveTypes: damage-dealing moves with no type-based multiplier. */
    val GEN3_EXCLUDED_MOVES = setOf(12, 32, 49, 68, 69, 82, 90, 101, 117, 149, 162, 165, 237, 243, 248, 251, 283, 329, 353)

    /** The seed types from a lead's move rows: (moveId, category, type). Status and fixed-damage moves are out. */
    fun seedTypes(moves: List<Triple<Int, String, String>>, excluded: Set<Int> = GEN3_EXCLUDED_MOVES): List<String> =
        moves.filter { (id, cat, type) -> !cat.startsWith("STA", ignoreCase = true) && id !in excluded && type.isNotBlank() }
            .map { it.third }.distinct().take(6)

    /**
     * The Gen 3 seed from [lead]'s move rows (rc32 audit P2 #17). Hidden Power counts with the type the player set on
     * the info screen (HiddenPowerTypes), in its place in move order, and is left out while that type is unknown:
     * CoverageCalcScreen.lua:462 "Allowed later, but only if it's type is tracked", :483-487. The ROM's own type for
     * it is Normal, which says nothing about this Pokemon's.
     */
    fun gen3Seed(lead: com.ironmonone.tracker.TrackedMon?): List<String> {
        val hiddenPower = lead?.let { HiddenPowerTypes.of(it.mon.pid) }
        return seedTypes(
            lead?.moveRows.orEmpty().map { r ->
                // Its category follows the type it was given, as on the card (MoveDecor).
                if (r.id == com.ironmonone.tracker.MoveRules.HIDDEN_POWER)
                    Triple(r.id, hiddenPower?.let { if (it <= 8) "PHY" else "SPE" } ?: "", hiddenPower?.let(com.ironmonone.tracker.Gen3Types::name) ?: "")
                else Triple(r.id, r.category ?: "", r.type?.let(com.ironmonone.tracker.Gen3Types::name) ?: "")
            },
            GEN3_EXCLUDED_MOVES - com.ironmonone.tracker.MoveRules.HIDDEN_POWER,
        )
    }

    /** CoverageCalcScreen.lua:132: BST, highest first, then id. */
    fun byBst(ids: List<Int>, bst: (Int) -> Int): List<Int> = ids.sortedWith(compareByDescending<Int> { bst(it) }.thenBy { it })
}

private val BUCKETS = listOf(0.0 to "0x", 0.25 to "1/4x", 0.5 to "1/2x", 1.0 to "1x", 2.0 to "2x", 4.0 to "4x")

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // Its words in DialogText, which follows the phone's font size, its close a 48dp X that says Close, and every
    // chip, switch and tab a 48dp target (rc32 audit P2 #19, #102, P3 #74): fixed 7 to 10dp text and an X of 25 by
    // 21dp that a screen reader read as "X". The chips flow onto a second line rather than past the screen's edge.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("COVERAGE CALC", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Text, "Close", Modifier.background(Pc.Page).border(1.dp, Pc.Border)) { onClose() }
            }
            noDataNote?.let { DialogText(it, 13, Pc.Negative, Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(6.dp))
            // The types under test: tap one to drop it.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                DialogText("Move types (tap to remove)", 12, Pc.Dim)
                Spacer(Modifier.height(3.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (types.isEmpty()) DialogText("none", 12, Pc.Dim)
                    types.forEach { t -> CoverageTypeChip(t, "Remove ${t.lowercase().replaceFirstChar { it.uppercase() }}") { types = types - t } }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                    if (types.size < 6) GearButton("ADD TYPE", Modifier) { picking = !picking }
                    GearButton("CLEAR", Modifier) { types = emptyList(); picking = false }
                    if (fullyEvolvedSupported) {
                        Row(
                            Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).padding(start = 4.dp)
                                .toggleable(value = fullyEvolved, role = Role.Checkbox) { fullyEvolved = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogText(if (fullyEvolved) "[x]" else "[ ]", 13, Pc.Text)
                            Spacer(Modifier.width(4.dp))
                            DialogText("Fully evolved only", 12, Pc.Text)
                        }
                    }
                }
                if (picking) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        allTypes.filter { it !in types }.forEach { t ->
                            CoverageTypeChip(t, "Add ${t.lowercase().replaceFirstChar { it.uppercase() }}") { if (types.size < 6) types = types + t; picking = false }
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
                        Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).background(if (on) Pc.Page else Pc.Ground)
                            .border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                            .selectable(selected = on, role = Role.Tab) { tab = mult }.padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DialogText("$n", 13, when { mult == 0.0 && n > 0 -> Pc.Negative; mult >= 2.0 -> Pc.Positive; else -> Pc.Text })
                        DialogText(label, 12, if (on) Pc.Gold else Pc.Dim, underline = on)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val ids = (data[tab] ?: emptyList()).let { if (sortByBst) CoverageCalc.byBst(it, bst) else it }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ids, key = { it }) { id ->
                    Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = sprite(id)
                        if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(28.dp), contentScale = ContentScale.Fit) else Spacer(Modifier.size(28.dp))
                        Spacer(Modifier.width(6.dp))
                        DialogText(name(id), 13, Pc.Text, Modifier.weight(1f))
                        DialogText("BST ${bst(id)}", 12, Pc.Dim)
                    }
                }
            }
        }
    }
}

/** A move type as a chip, drawn as before inside a 48dp touch area, and [spoken] for a screen reader. */
@Composable
private fun CoverageTypeChip(type: String, spoken: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(onClickLabel = spoken) { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.border(1.dp, Pc.Border).background(Pc.Ground).padding(horizontal = 5.dp, vertical = 3.dp)) {
            DialogText(type.uppercase(), 12, pcTypeColorByName(type))
        }
    }
}
