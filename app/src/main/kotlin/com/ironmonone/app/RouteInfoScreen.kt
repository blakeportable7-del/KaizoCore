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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.ORDERED_ENCOUNTERS

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
    /** RandomizerLog.Data.Routes[mapId] for the area, in Open Book; null when there is no log. */
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

    val logged = if (TrackerOptions.openBookPlayMode) src.logged?.invoke(area)?.takeIf { it.isNotEmpty() } else null
    val icons: List<RouteIcon> = when {
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
                    PixText(src.name.ifBlank { "---" }, 10, Pc.Text, Modifier.weight(1f))
                    picturesFor?.let { FrlgMapMark(it(src.mapId)) }
                    Box(Modifier.clickable { lookupOpen = true }.padding(horizontal = 6.dp)) { PixText("SEARCH", 8, Pc.Gold) }
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
                Box(Modifier.clickable { step(-1) }.padding(6.dp)) { PixText("<", 10, Pc.Text) }
                PixText(
                    if (area == "Static") "Static Pokémon encounters" else "Pokémon seen by $area",
                    9, Pc.Text, Modifier.weight(1f), align = TextAlign.Center,
                )
                Box(Modifier.clickable { step(1) }.padding(6.dp)) { PixText(">", 10, Pc.Text) }
            }
            // BOTTOM BOX: the icons.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                if (logged == null && !showPercents && !showLevels) PixText("In order of appearance:", 8, Pc.Dim)
                if (icons.isEmpty()) PixText("Nothing wild appears here.", 8, Pc.Dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    icons.forEach { ic ->
                        val label = when {
                            showPercents && ic.rate != null -> "${kotlin.math.floor(ic.rate * 100).toInt()}%"
                            showLevels && ic.minLv != null && ic.maxLv != null ->
                                if (ic.minLv == 0 && ic.maxLv == 0) "?" else "${ic.minLv} - ${ic.maxLv}"
                            else -> null
                        }
                        Column(
                            Modifier.width(48.dp).then(
                                if (ic.species != null && onPokemon != null) Modifier.clickable { onPokemon(ic.species) } else Modifier
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PixText(label ?: " ", 8, Pc.Text, align = TextAlign.Center)
                            if (ic.species != null) PcSprite(spriteFor(ic.species))
                            else Box(Modifier.size(PcRef.ICON.rp), contentAlignment = Alignment.Center) { PixText("?", 14, Pc.Dim) }
                        }
                    }
                }
            }
            if (src.safari.isNotEmpty()) {
                Spacer(Modifier.height4())
                Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                    PixText("Safari Zone, highest level met:", 8, Pc.Dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        src.safari.forEach { (sp, lv) ->
                            Column(
                                Modifier.width(48.dp).then(if (onPokemon != null) Modifier.clickable { onPokemon(sp) } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PixText("Lv.$lv", 8, Pc.Text, align = TextAlign.Center)
                                PcSprite(spriteFor(sp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height4())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PcSmallButton("BACK") { onDismiss() } }
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
                PixText("Look up a route:", 10, Pc.Gold)
                Spacer(Modifier.height4())
                lookupRoutes().forEach { (id, name) ->
                    Box(Modifier.fillMaxWidth().clickable {
                        sourceFor(id)?.let { s ->
                            src = s; area = firstArea(s, "Walking")
                            showPercents = false; showLevels = levelsDefault
                        }
                        lookupOpen = false
                    }.padding(vertical = 5.dp)) { PixText(name, 9, Pc.Text) }
                }
            }
        }
    }
}

@Composable
private fun RouteCheckbox(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.clickable { onToggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).border(1.dp, Pc.Border), contentAlignment = Alignment.Center) {
            if (on) PixText("X", 8, Pc.Text)
        }
        Spacer(Modifier.width(4.dp))
        PixText(label, 8, Pc.Text)
    }
}

private fun Modifier.height4(): Modifier = this.then(Modifier.padding(top = 4.dp))
