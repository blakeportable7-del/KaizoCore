package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker

/**
 * RandomEvosScreen.lua: what a species can randomly evolve into and how
 * likely each is, from the reference's PokemonRevoData table. A species
 * with more than one regular evolution gets the reference's "Evo N: NAME"
 * header with arrows to switch between them. Under 0.1% is drawn red as
 * "< 0.1%". Tapping an evolution shows its own table when it has one. Its
 * words are DialogText, which follows the phone's font size, and its arrows
 * and close are 48dp with a spoken name (rc32 audit P2 #19, #102): fixed 6 to
 * 10dp text and arrows and an X about 17dp across that read "<", ">" and "X".
 */
@Composable
fun RandomEvosDialog(species: Int, tracker: GbaTracker?, speciesName: (Int) -> String, spriteFor: (Int) -> ImageBitmap?, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val options = remember(species) { tracker?.randomEvoOptions(species) ?: emptyList() }
    var optionIndex by remember(species) { mutableStateOf(0) }
    // Sorted by Name, BST or Percent, as the DS screen (EvoDataDialog) and besteon's EvoDataScreen do.
    var sort by remember { mutableStateOf("PERCENT") }
    val bst: (Int) -> Int = { id -> tracker?.baseStats(id)?.let { it.hp + it.atk + it.def + it.spe + it.spAtk + it.spDef } ?: 0 }
    val table = remember(species, optionIndex, sort) {
        val list = tracker?.randomEvos(species, options.getOrNull(optionIndex)) ?: emptyList()
        when (sort) {
            "NAME" -> list.sortedBy { speciesName(it.first) }
            "BST" -> list.sortedByDescending { bst(it.first) }
            else -> list.sortedByDescending { it.second }
        }
    }
    // Heart & Soul's odds are KaizoCore's own randomizer's over many seeds, and the screen says how many.
    val seeds = remember(tracker) { tracker?.hnsEvoSeeds() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (options.isNotEmpty()) {
                    PcTap("<", 10, Pc.Text, "Previous evolution") { optionIndex = (optionIndex - 1 + options.size) % options.size }
                    DialogText("Evo ${optionIndex + 1}: ${speciesName(options[optionIndex])}", 14, Pc.Text, Modifier.weight(1f), TextAlign.Center, heading = true)
                    PcTap(">", 10, Pc.Text, "Next evolution") { optionIndex = (optionIndex + 1) % options.size }
                } else DialogText("Random Evos (${speciesName(species)})", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            seeds?.let { DialogText(RandomEvosCopy.seeds(it), 12, Pc.Dim) }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("NAME" to "Name", "BST" to "BST", "PERCENT" to "Percent").forEach { (k, label) ->
                    val on = sort == k
                    Box(Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                        .selectable(selected = on, role = Role.RadioButton) { sort = k }, contentAlignment = Alignment.Center) {
                        DialogText(label, 13, if (on) Pc.Gold else Pc.Dim, align = TextAlign.Center, underline = on)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (table.isEmpty()) DialogText("No random evolution data for this Pokemon.", 13, Pc.Dim)
            table.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (id, perc) ->
                        Column(Modifier.weight(1f).clickable { onPick(id) }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PcSprite(spriteFor(id))
                            DialogText(if (perc < 0.1) "< 0.1%" else evoShare(perc), 12, if (perc < 0.1) Pc.Negative else Pc.Text, align = TextAlign.Center)
                            DialogText(speciesName(id), 12, Pc.Dim, align = TextAlign.Center)
                            DialogText("BST ${bst(id)}", 12, Pc.Dim, align = TextAlign.Center)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The words of the Random Evos screen. */
internal object RandomEvosCopy {
    fun seeds(n: Int) = "Odds from $n seeds of KaizoCore's own randomizer."
}

/**
 * A share as the evolution screens print it, "12.50%", with a point whatever the phone's language: String.format took
 * the phone's own, so a German phone printed 12,50% among English words (rc35 follow-up N #28).
 */
internal fun evoShare(perc: Double): String = String.format(java.util.Locale.US, "%.2f%%", perc)
