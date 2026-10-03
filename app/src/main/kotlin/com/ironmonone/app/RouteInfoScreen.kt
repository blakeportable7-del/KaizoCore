package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.ORDERED_ENCOUNTERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One icon on the route info screen: a species (or null for the "?" slot) and what is known of it. */
data class RouteIcon(val species: Int?, val rate: Double? = null, val minLv: Int? = null, val maxLv: Int? = null)

/** What the route info screen needs to draw one map. */
class RouteInfoSource(
    val mapId: Int,
    val name: String,
    /** RouteData.Info[mapId][area] for each area the map has. */
    val vanilla: Map<String, List<GbaTracker.RouteMon>>,
    /** Tracker.getRouteEncounters(mapId, area): species met there, in order of appearance. */
    val tracked: (String) -> List<Int>,
    /**
     * RandomizerLog.Data.Routes[mapId] for the area, in Open Book; null when there is no log. It reads the run's log, so
     * the screen calls it off the main thread (rc32 audit P2 #27).
     */
    val logged: ((String) -> List<RouteIcon>?)? = null,
    /**
     * A Safari Zone map's record (Tracker.getSafariEncounters): each Pokemon met there with the
     * highest level it was met at. The reference shows it only in a chat command (!pivots);
     * Blake, 2026-09-29: show it somewhere, so it sits under the map's icons.
     */
    val safari: List<Pair<Int, Int>> = emptyList(),
)

/**
 * InfoScreen's ROUTE_INFO view (InfoScreen.lua:1046 drawRouteInfoScreen,
 * :528 getPokemonButtonsForEncounterArea), which had never been ported: the
 * app showed every area's vanilla table as one text list (2026-09-28, Blake:
 * "i asked to clone the pc version").
 *
 * Top box: the route's name, the lookup magnifier, and the Percentages and
 * Levels checkboxes (one clears the other). Under it "Pokemon seen by
 * <area>" between the arrows that step through the route's areas in
 * RouteData.OrderedEncounters order. The box: in Open Book, the log's
 * encounters for the area; with a checkbox on, the vanilla table with its
 * rates or levels; otherwise the species met there in order of appearance,
 * then a "?" for each one of the area's vanilla count not yet met. Tapping a
 * Pokemon opens its info. Levels starts on when the game data is not
 * randomized (or in Open Book), as InfoScreen.changeScreenView sets it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PcRouteInfoScreen(
    start: RouteInfoSource,
    startArea: String?,
    gameDataRandomized: Boolean,
    spriteFor: (Int) -> ImageBitmap?,
    /** RouteData.AvailableRoutes for the lookup: maps with any encounter area, in map id order. */
    lookupRoutes: () -> List<Pair<Int, String>>,
    sourceFor: (Int) -> RouteInfoSource?,
    onPokemon: ((Int) -> Unit)?,
    /**
     * FireRed and LeafGreen (2026-09-29): the pictures for a map, so the name of whichever map the screen shows
     * (the look-up can switch it) is followed by the map mark. Null in any other game (FrlgPictures.lookupFor).
     */
    picturesFor: ((Int) -> FrlgPictures.Place?)? = null,
    onDismiss: () -> Unit,
) {
    var src by remember { mutableStateOf(start) }
    fun firstArea(s: RouteInfoSource, want: String?) =
        want?.takeIf { it in s.vanilla } ?: ORDERED_ENCOUNTERS.firstOrNull { it in s.vanilla } ?: "Walking"
    var area by remember { mutableStateOf(firstArea(start, startArea)) }
    val levelsDefault = !gameDataRandomized || TrackerOptions.openBookPlayMode
    var showPercents by remember { mutableStateOf(false) }
    var showLevels by remember { mutableStateOf(levelsDefault) }
    var lookupOpen by remember { mutableStateOf(false) }

    // RouteData.getNext/PreviousAvailableEncounterArea: wrap around the ordered list, skipping areas the map lacks.
    fun step(dir: Int) {
        val n = ORDERED_ENCOUNTERS.size
        var i = ORDERED_ENCOUNTERS.indexOf(area)
        repeat(n) {
            i = ((i + dir) % n + n) % n
            if (ORDERED_ENCOUNTERS[i] in src.vanilla) { area = ORDERED_ENCOUNTERS[i]; return }
        }
    }

    // Open Book's table comes from the run's log, read on Dispatchers.Default (rc32 audit P2 #27): the first look parsed
    // the whole log on the main thread. Each answer is kept with the map and area it is for, so another's never shows.
    val openBook = TrackerOptions.openBookPlayMode
    val want = src to area
    val loggedFor by produceState<Pair<Pair<RouteInfoSource, String>, List<RouteIcon>?>?>(null, want, openBook) {
        val read = want.first.logged
        value = if (!openBook || read == null) null
        else want to withContext(Dispatchers.Default) { runCatching { read(want.second) }.getOrNull() }?.takeIf { it.isNotEmpty() }
    }
    val readingLog = openBook && src.logged != null && loggedFor?.first != want
    val logged = loggedFor?.takeIf { it.first == want }?.second
    val icons: List<RouteIcon> = when {
        readingLog -> emptyList()
        logged != null -> logged
        showPercents || showLevels -> src.vanilla[area].orEmpty().map { RouteIcon(it.id, it.rate, it.minLv, it.maxLv) }
        else -> {
            val seen = src.tracked(area)
            val total = maxOf(seen.size, src.vanilla[area].orEmpty().size)
            List(total) { i -> RouteIcon(seen.getOrNull(i)) }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp)
                .background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            // TOP BOX: route name, lookup, the two checkboxes.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // In sp, as every tracker window's words are (rc32 audit P2 #19).
                    DialogText(src.name.ifBlank { "---" }, 14, Pc.Text, Modifier.weight(1f), heading = true)
                    picturesFor?.let { FrlgMapMark(it(src.mapId)) }
                    // SEARCH and the two arrows below are 48dp targets with a spoken name (rc32 audit P2 #102): they
                    // were 8 to 22dp words a screen reader read as "SEARCH", "<" and ">". SEARCH looks up a route.
                    PcTap("SEARCH", 8, Pc.Gold, "Look up a route") { lookupOpen = true }
                }
                Spacer(Modifier.height4())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RouteCheckbox("Percentages", showPercents) { showPercents = !showPercents; showLevels = false }
                    Spacer(Modifier.width(14.dp))
                    RouteCheckbox("Levels", showLevels) { showLevels = !showLevels; showPercents = false }
                }
            }
            Spacer(Modifier.height4())
            // "Pokemon seen by <area>" between the arrows; a static area is "Static Pokemon encounters".
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PcTap("<", 10, Pc.Text, "Previous encounter area") { step(-1) }
                DialogText(
                    if (area == "Static") "Static Pokémon encounters" else "Pokémon seen by $area",
                    12, Pc.Text, Modifier.weight(1f), align = TextAlign.Center,
                )
                PcTap(">", 10, Pc.Text, "Next encounter area") { step(1) }
            }
            // BOTTOM BOX: the icons.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                if (readingLog) DialogText("Reading the log.", 12, Pc.Dim)
                else if (logged == null && !showPercents && !showLevels) DialogText("In order of appearance:", 12, Pc.Dim)
                if (icons.isEmpty() && !readingLog) DialogText("Nothing wild appears here.", 12, Pc.Dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    icons.forEach { ic ->
                        val label = when {
                            showPercents && ic.rate != null -> "${kotlin.math.floor(ic.rate * 100).toInt()}%"
                            // "to", not a spaced hyphen, between the levels (rc35 follow-up N #31).
                            showLevels && ic.minLv != null && ic.maxLv != null ->
                                if (ic.minLv == 0 && ic.maxLv == 0) "?" else "${ic.minLv} to ${ic.maxLv}"
                            else -> null
                        }
                        Column(
                            Modifier.width(48.dp).then(
                                if (ic.species != null && onPokemon != null) Modifier.clickable { onPokemon(ic.species) } else Modifier
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            DialogText(label ?: " ", 12, Pc.Text, align = TextAlign.Center)
                            if (ic.species != null) PcSprite(spriteFor(ic.species))
                            else Box(Modifier.size(PcRef.ICON.rp), contentAlignment = Alignment.Center) { DialogText("?", 14, Pc.Dim) }
                        }
                    }
                }
            }
            if (src.safari.isNotEmpty()) {
                Spacer(Modifier.height4())
                Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                    DialogText("Safari Zone, highest level met:", 12, Pc.Dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        src.safari.forEach { (sp, lv) ->
                            Column(
                                Modifier.width(48.dp).then(if (onPokemon != null) Modifier.clickable { onPokemon(sp) } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DialogText("Lv.$lv", 12, Pc.Text, align = TextAlign.Center)
                                PcSprite(spriteFor(sp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height4())
            GearButton("Back") { onDismiss() }
        }
    }

    // InfoScreen.openRouteInfoWindow: pick any route that has encounters; it opens on
    // Walking (or the first area it has) with Percentages off and Levels at its default.
    if (lookupOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { lookupOpen = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).background(Pc.Ground).border(1.dp, Pc.Border)
                    .verticalScroll(rememberScrollState()).padding(10.dp)
            ) {
                DialogText("Look up a route:", 14, Pc.Gold, heading = true)
                Spacer(Modifier.height4())
                lookupRoutes().forEach { (id, name) ->
                    Box(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) {
                        sourceFor(id)?.let { s ->
                            src = s; area = firstArea(s, "Walking")
                            showPercents = false; showLevels = levelsDefault
                        }
                        lookupOpen = false
                    }, contentAlignment = Alignment.CenterStart) { DialogText(name, 13, Pc.Text) }
                }
            }
        }
    }
}

/** Percentages or Levels: a 48dp switch row that says whether it is checked, its label in sp (rc32 audit P2 #19). */
@Composable
private fun RouteCheckbox(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Checkbox) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).border(1.dp, Pc.Border), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(8.dp).background(Pc.Gold))
        }
        Spacer(Modifier.width(8.dp))
        DialogText(label, 12, Pc.Text)
    }
}

private fun Modifier.height4(): Modifier = this.then(Modifier.padding(top = 4.dp))
