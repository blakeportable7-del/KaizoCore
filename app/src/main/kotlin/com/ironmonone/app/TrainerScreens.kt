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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker

/**
 * TrainersOnRouteScreen.lua: the trainers stationed on the current map, one
 * row each with class and name, party size and a check when defeated, and the
 * "N of M defeated" line; tapping a row opens Trainer Info. Both screens draw
 * their words in DialogText, which follows the phone's font size, and close
 * with a 48dp X that says Close (rc32 audit P2 #19, #102, P3 #74): they were
 * fixed 7 to 10dp text and a 17 by 13dp X that a screen reader read as "X".
 */
@Composable
fun TrainersOnRouteDialog(
    routeName: String,
    trainers: List<GbaTracker.TrainerInfo>,
    onTrainer: (GbaTracker.TrainerInfo) -> Unit,
    onClose: () -> Unit,
    /** FireRed and LeafGreen (2026-09-29): the pictures for this map, shown as the map mark after its name (FrlgPictures.placeFor). */
    pictures: FrlgPictures.Place? = null,
) {
    val defeated = trainers.count { it.defeated }
    val monsDefeated = trainers.filter { it.defeated }.sumOf { it.party.size }
    val monsTotal = trainers.sumOf { it.party.size }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText(routeName.uppercase(), 16, Pc.Text, Modifier.weight(1f), heading = true)
                FrlgMapMark(pictures)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            DialogText("Trainers: $defeated / ${trainers.size} defeated, Pokemon: $monsDefeated / $monsTotal", 12, Pc.Dim)
            Spacer(Modifier.height(6.dp))
            if (trainers.isEmpty()) DialogText("No trainers here.", 13, Pc.Text)
            trainers.forEach { t ->
                Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable { onTrainer(t) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    DialogText(if (t.defeated) "\u2713" else " ", 13, Pc.Positive, Modifier.widthIn(min = 20.dp))
                    Column(Modifier.weight(1f)) {
                        DialogText((t.className + " " + t.name).trim(), 13, Pc.Text)
                        DialogText("${t.party.size} Pokemon, Lv.${t.minLevel}" + (if (t.maxLevel != t.minLevel) "-${t.maxLevel}" else ""), 12, Pc.Dim)
                    }
                }
            }
        }
    }
}

/**
 * What Trainer Info shows of a trainer before and after the fight (TrainerInfoScreen.lua).
 *
 * The party is one box per Pokemon with its level and, when it holds one, the held-item mark
 * (never the item itself). The species shows only once the trainer is beaten, where teams may
 * be shown unseen (TrainerData.canShowUnknownTrainerTeams: Open Book, or unrandomized teams
 * with "Show data for vanilla game" on), or, in the battle against this trainer, for a Pokemon
 * that has fainted (TrainerInfoScreen.lua:321-331). Otherwise a Poke Ball stands in for it,
 * Master Balls for Giovanni (:370-373). Moves are never shown. The screen used to list every
 * species, level, held item and move before the fight.
 */
internal object TrainerInfoView {
    /** One party box: [species] null while it is still a Poke Ball. */
    data class Slot(val species: Int?, val level: Int, val holdsItem: Boolean)

    fun party(t: GbaTracker.TrainerInfo, canShowTeams: Boolean, faintedSlots: Set<Int>): List<Slot> =
        t.party.mapIndexed { i, m ->
            Slot(m.species.takeIf { t.defeated || canShowTeams || i in faintedSlots }, m.level, m.heldItem != 0)
        }

    /**
     * TrainerInfoScreen.lua:290-311, "Usable Items": the trainer's items that have a name, each
     * once, "2 Hyper Potion" for two of one, sorted as text and joined with commas; null for none.
     */
    fun usableItems(items: List<Int>, itemName: (Int) -> String?): String? {
        val counts = LinkedHashMap<Int, Int>()
        for (id in items) if (itemName(id) != null) counts[id] = (counts[id] ?: 0) + 1
        return counts.map { (id, n) -> if (n == 1) "${itemName(id)}" else "$n ${itemName(id)}" }
            .sorted().joinToString(", ").ifEmpty { null }
    }

    /** TrainerData.isGiovanni (TrainerData.lua:357-360): FireRed and LeafGreen trainers 348 to 350. */
    fun isGiovanni(frlg: Boolean, trainerId: Int): Boolean = frlg && trainerId in 348..350
}

/** Constants.PixelImages.POKEBALL, 12x12, in TrackerScreen.PokeBalls.ColorList. */
private val POKEBALL = listOf(
    "000011110000", "001122221100", "012223222210", "012232222210", "122222222221", "122221122221",
    "112213312211", "131113311131", "013331133310", "013333333310", "001133331100", "000011110000",
)
private val POKEBALL_COLORS = mapOf('1' to Color(0xFF000000), '2' to Color(0xFFF04037), '3' to Color(0xFFFFFFFF))

/** Constants.PixelImages.MASTERBALL, 12x12, in TrackerScreen.PokeBalls.ColorListMasterBall. */
private val MASTERBALL = listOf(
    "000011110000", "001142241100", "014442244410", "014422224410", "144222222441", "122221122221",
    "112213312211", "131113311131", "013331133310", "013333333310", "001133331100", "000011110000",
)
private val MASTERBALL_COLORS = POKEBALL_COLORS + mapOf('2' to Color(0xFFA040B8), '4' to Color(0xFFF86088))

/** Constants.PixelImages.HELD_ITEM, 6x6, in its own colours. */
private val HELD_ITEM = listOf("211112", "211112", "211112", "433334", "433334", "211112")
private val HELD_ITEM_COLORS = mapOf('1' to Color(0xFFFFF79C), '2' to Color(0xFFDEDE73), '3' to Color(0xFFF75229), '4' to Color(0xFFA55A52))

/**
 * TrainerInfoScreen.lua: class and name, route, team size and level range (red when the lead
 * is below the lowest), average IVs, the AI label, the trainer's usable items, and the party as
 * [TrainerInfoView] allows.
 */
@Composable
fun TrainerInfoDialog(
    t: GbaTracker.TrainerInfo,
    routeName: String?,
    leadLevel: Int?,
    /** TrainerData.canShowUnknownTrainerTeams (InfoRules.canShowTrainerTeams). */
    canShowTeams: Boolean,
    /** In a battle against this trainer, its party slots that have fainted. */
    faintedSlots: Set<Int>,
    giovanni: Boolean,
    /** An item's name, or null for an id with none (0 included). */
    itemName: (Int) -> String?,
    spriteFor: (Int) -> ImageBitmap?,
    onClose: () -> Unit,
    /** FireRed and LeafGreen (2026-09-29): the pictures for the map the fight is on, shown as the map mark after the Route line. */
    routePictures: FrlgPictures.Place? = null,
) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText((t.className + " " + t.name).trim().uppercase(), 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            @Composable fun row(label: String, value: String, color: androidx.compose.ui.graphics.Color = Pc.Text) {
                Row(Modifier.fillMaxWidth()) { DialogText(label, 12, Pc.Dim, Modifier.width(96.dp)); DialogText(value, 13, color, Modifier.weight(1f)) }
            }
            if (routePictures == null) row("Route", routeName ?: "???")
            else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("Route", 12, Pc.Dim, Modifier.width(96.dp))
                DialogText(routeName ?: "???", 13, Pc.Text, Modifier.weight(1f, fill = false))
                FrlgMapMark(routePictures)
            }
            val lvRange = if (t.minLevel == t.maxLevel) "${t.minLevel}" else "${t.minLevel}-${t.maxLevel}"
            row("Team", "${t.party.size} Pokemon, Lv.$lvRange", if (leadLevel != null && leadLevel < t.minLevel) Pc.Negative else Pc.Gold)
            row("Avg IVs", "${t.avgIvs}", if (t.avgIvs > 0) Pc.Gold else Pc.Text)
            row("AI", t.aiLabel + (if (t.doubleBattle) ", doubles" else ""))
            if (t.defeated) row("Status", "Defeated", Pc.Positive)
            TrainerInfoView.usableItems(t.items, itemName)?.let {
                Spacer(Modifier.height(2.dp))
                DialogText("Usable Items:", 12, Pc.Dim)
                DialogText(it, 13, Pc.Positive, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            TrainerInfoView.party(t, canShowTeams, faintedSlots).chunked(6).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    line.forEach { slot -> TrainerPartyBox(slot, giovanni, spriteFor) }
                }
            }
        }
    }
}

/**
 * One party box (TrainerInfoScreen.lua:346-366): the icon or a Poke Ball, the level, the held-item mark. The level
 * sits under the picture in the dialog's text size, so a big font makes the box taller instead of running the words
 * over the picture.
 */
@Composable
private fun TrainerPartyBox(slot: TrainerInfoView.Slot, giovanni: Boolean, spriteFor: (Int) -> ImageBitmap?) {
    Column(Modifier.width(44.dp).heightIn(min = 44.dp).border(1.dp, Pc.Border), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(32.dp)) {
            val art = slot.species?.let(spriteFor)
            if (slot.species != null && art != null)
                Image(art, null, Modifier.align(Alignment.TopCenter).size(32.dp), filterQuality = FilterQuality.None)
            else if (slot.species == null)
                PcPixelImageColors(if (giovanni) MASTERBALL else POKEBALL, if (giovanni) MASTERBALL_COLORS else POKEBALL_COLORS,
                    Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(20.dp))
            if (slot.holdsItem) PcPixelImageColors(HELD_ITEM, HELD_ITEM_COLORS, Modifier.align(Alignment.TopEnd).padding(2.dp).size(9.dp))
        }
        DialogText("Lv.${slot.level}", 12, Pc.Text, align = TextAlign.Center)
    }
}
