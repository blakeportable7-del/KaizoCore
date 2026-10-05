package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.GbaTracker
import kotlin.math.floor

/** RandomizerLog.EncounterTypes, in LogTabRouteDetails' tab order, with the reference's slot rates. */
enum class LogEncType(val label: String, val rates: List<Double>) {
    TRAINERS("Trainers", emptyList()),
    GRASS("Grass / Cave", listOf(0.20, 0.20, 0.10, 0.10, 0.10, 0.10, 0.05, 0.05, 0.04, 0.04, 0.01, 0.01)),
    SURFING("Surfing", listOf(0.60, 0.30, 0.05, 0.04, 0.01)),
    OLDROD("Old Rod", listOf(0.70, 0.30)),
    GOODROD("Good Rod", listOf(0.60, 0.20, 0.20)),
    SUPERROD("Super Rod", listOf(0.40, 0.40, 0.15, 0.04, 0.01)),
    ROCKSMASH("Rock Smash", listOf(0.60, 0.30, 0.05, 0.04, 0.01)),
}

/** One species in one encounter area, its slots combined: lowest level, highest level, summed rate. */
class LogWild(val name: String, val pokemon: RandomizerLog.Pokemon?, val levelMin: Int, val levelMax: Int, val rate: Double, val index: Int)

/** RandomizerLog.Data.Routes[mapId], as LogTabRoutes and LogTabRouteDetails read it. */
class LogRoute(
    val mapId: Int,
    val name: String,
    /** The route's trainers that are in the log and in play (DataHelper.buildRouteLogDisplay). */
    val trainers: List<RandomizerLog.Trainer>,
    val numTrainers: Int,
    val minTrainerLv: Int?,
    val maxTrainerLv: Int?,
    val avgTrainerLv: Double?,
    val areas: Map<LogEncType, List<LogWild>>,
    val numWilds: Int,
    val minWildLv: Int?,
    val maxWildLv: Int?,
)

/**
 * RandomizerLog.parseRoutes for the app. The log only places wild encounters,
 * so trainers come from the tracker's route tables first (RouteData.Info),
 * then each "Set #N" is put on its map through RouteSetNumToIdMap, its type
 * read from the set's name. Fishing is one table in the log that the rods
 * split: slots 1-2 Old Rod, 3-5 Good Rod, the rest Super Rod. The route's own
 * wild summary is taken from the first area parsed for it, as the reference's
 * is, and a route with neither trainers nor wilds is left out.
 */
object LogRoutes {
    private val keys = listOf(
        "grass/cave" to LogEncType.GRASS, "surfing" to LogEncType.SURFING,
        "rock smash" to LogEncType.ROCKSMASH, "fishing" to LogEncType.OLDROD,
    )
    private val fishingRates = LogEncType.OLDROD.rates + LogEncType.GOODROD.rates + LogEncType.SUPERROD.rates

    private class MutWild(val name: String, val index: Int) { var lo = 100; var hi = 0; var rate = 0.0 }
    private class Acc(val name: String) {
        var trainerIds: List<Int> = emptyList()
        var numTrainers = 0; var minT: Int? = null; var maxT: Int? = null; var avgT: Double? = null
        val areas = LinkedHashMap<LogEncType, LinkedHashMap<String, MutWild>>()
        var numWilds = 0; var minW: Int? = null; var maxW: Int? = null
    }

    fun build(log: RandomizerLog, rules: LogTrainerRules, tracker: GbaTracker): List<LogRoute> {
        val byNumber = log.trainers.associateBy { it.number }
        val acc = LinkedHashMap<Int, Acc>()
        // First the maps the tracker's tables know, with their trainers.
        for (mapId in tracker.routeMapIds().sorted()) {
            val a = Acc(refUpperEachWord(tracker.routeInfo(mapId)?.first ?: "Unknown Area"))
            acc[mapId] = a
            val ids = tracker.trainersOnRoute(mapId).filter { rules.use(it) }
            if (ids.isEmpty()) continue
            a.trainerIds = ids
            var avgSum = 0.0
            for (id in ids) {
                val t = byNumber[id] ?: continue   // counted, as the reference counts it, but it has no levels
                if (t.party.isEmpty()) continue
                a.minT = minOf(a.minT ?: 999, t.party.minOf { it.level })
                a.maxT = maxOf(a.maxT ?: 0, t.maxLevel)
                avgSum += t.party.map { it.level }.average()
            }
            if (avgSum > 0) { a.numTrainers = ids.size; a.avgT = avgSum / ids.size }
        }
        // Then the log's wild sets, by the same map ids as the trainers above (logRouteSets turns
        // Ruby/Sapphire's raw ids into the tracker's).
        val sets = tracker.logRouteSets()
        for (set in log.routes) {
            val mapId = sets[set.number] ?: continue
            val low = set.name.lowercase()
            val (key, type) = keys.firstOrNull { low.contains(it.first) } ?: continue
            val a = acc.getOrPut(mapId) { Acc(logTitle(set.name.substring(0, low.indexOf(key)).trim()).ifEmpty { "Unknown Area" }) }
            val fishing = key == "fishing"
            if (fishing) listOf(LogEncType.OLDROD, LogEncType.GOODROD, LogEncType.SUPERROD).forEach { a.areas[it] = LinkedHashMap() }
            else a.areas[type] = LinkedHashMap()
            var area = a.areas.getValue(if (fishing) LogEncType.OLDROD else type)
            set.encounters.forEachIndexed { i0, e ->
                val slot = i0 + 1
                if (fishing) area = a.areas.getValue(when { slot <= 2 -> LogEncType.OLDROD; slot <= 5 -> LogEncType.GOODROD; else -> LogEncType.SUPERROD })
                // The reference skips names it cannot map. A log that lists no Pokemon (nothing about them was randomized)
                // keeps them by name, or every wild area of such a log vanished (rc32 audit P2 #71).
                if (log.pokemon.isNotEmpty() && log.pokemonNamed(e.name) == null) return@forEachIndexed
                val w = area.getOrPut(e.name.uppercase()) { MutWild(e.name, slot) }
                w.lo = minOf(w.lo, e.minLevel)
                w.hi = maxOf(w.hi, e.maxLevel)
                w.rate += (if (fishing) fishingRates else type.rates).getOrElse(slot - 1) { 0.0 }
            }
            // "If the levels for the route's wild encounters haven't been noted yet, do that" - from the area last filled.
            if (a.minW == null || a.maxW == null) {
                for (w in area.values) {
                    a.numWilds++
                    a.minW = minOf(a.minW ?: 999, w.lo)
                    a.maxW = maxOf(a.maxW ?: 0, w.hi)
                }
            }
        }
        return acc.map { (mapId, a) ->
            LogRoute(
                mapId, a.name, a.trainerIds.mapNotNull { byNumber[it] },
                a.numTrainers, a.minT, a.maxT, a.avgT,
                a.areas.mapValues { (_, m) -> m.values.map { LogWild(it.name, log.pokemonNamed(it.name), it.lo, it.hi, it.rate, it.index) } },
                a.numWilds, a.minW, a.maxW,
            )
        }
            .filter { (it.avgTrainerLv ?: 0.0) != 0.0 || (it.maxWildLv ?: 0) != 0 }
            // LogTabRoutes.defaultSortKey, "WildPokemonLevel": the highest wild level, lowest first; no wilds last.
            .sortedWith(compareBy<LogRoute>({ it.maxWildLv ?: 999 }, { it.mapId }))
    }
}

/**
 * LogTabRoutes: a bar per location with its wild count and level range and
 * its trainer count and level range. The reference heads the two count
 * columns with a Nidoran and a trainer head; the app has no such art, so they
 * are labelled. A search looks for the route's name (its defaultFilterKey).
 */
@Composable
internal fun LogRoutesTab(routes: List<LogRoute>, query: String, onRoute: (LogRoute) -> Unit) {
    val q = query.trim()
    val rows = if (q.isEmpty()) routes else routes.filter { it.name.contains(q, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            DialogText("LOCATION", 12, Pc.Gold, Modifier.weight(1f))
            DialogText("WILD", 12, Pc.Gold, Modifier.width(44.dp), TextAlign.Center)
            DialogText("LV.", 12, Pc.Gold, Modifier.width(68.dp), TextAlign.Center)
            DialogText("TRAINERS", 12, Pc.Gold, Modifier.width(76.dp), TextAlign.Center)
            DialogText("LV.", 12, Pc.Gold, Modifier.width(68.dp), TextAlign.Center)
        }
        if (rows.isEmpty()) {
            DialogText("(No results)", 13, Pc.Dim)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows, key = { it.mapId }) { r ->
                Row(
                    Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable { onRoute(r) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogText(r.name, 13, Pc.Text, Modifier.weight(1f))
                    val wild = r.numWilds > 0
                    val trn = r.numTrainers > 0
                    DialogText(if (wild) "${r.numWilds}" else "", 12, Pc.Text, Modifier.width(44.dp), TextAlign.Center)
                    DialogText(if (wild) "${r.minWildLv}-${r.maxWildLv}" else "", 12, Pc.Text, Modifier.width(68.dp), TextAlign.Center)
                    DialogText(if (trn) "${r.numTrainers}" else "", 12, Pc.Text, Modifier.width(76.dp), TextAlign.Center)
                    DialogText(if (trn) "${r.minTrainerLv}-${r.maxTrainerLv}" else "", 12, Pc.Text, Modifier.width(68.dp), TextAlign.Center)
                }
            }
        }
    }
}

/**
 * LogTabRouteDetails: the route's name, "Encounters:" and a tab for each kind
 * of encounter it has with its count (trainers, grass or cave, surfing, the
 * three rods, rock smash), opening on trainers if any, else grass, else
 * surfing. Trainers are drawn as on the Trainers tab; each wild Pokemon with
 * its level range and its rate, the likeliest first.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun LogRouteDetail(
    route: LogRoute,
    rules: LogTrainerRules,
    custom: Boolean,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    onTrainer: (RandomizerLog.Trainer) -> Unit,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    onBack: () -> Unit,
    names: LogNames = LogNames.PLAIN,
    /** Each trainer's portrait from the ROM, as on the Trainers tab. */
    portraitOf: (RandomizerLog.Trainer) -> ImageBitmap? = { null },
    /** The wild Pokemon's Walking Pals (LogTabRouteDetails.lua:367-412 draws each one's icon). */
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? = null,
    /** The player's head from the ROM, on the Trainers choice (LogTabRouteDetails.lua:136-140, getPlayerIconHead). */
    playerHead: ImageBitmap? = null,
) {
    fun count(t: LogEncType) = if (t == LogEncType.TRAINERS) route.trainers.size else route.areas[t]?.size ?: 0
    val tabs = LogEncType.entries.filter { count(it) > 0 }
    val first = when {
        route.trainers.isNotEmpty() -> LogEncType.TRAINERS
        count(LogEncType.GRASS) > 0 -> LogEncType.GRASS
        else -> LogEncType.SURFING
    }
    var tab by remember(route.mapId) { mutableStateOf(first) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LogBack(onBack)
            DialogText(route.name.uppercase(), 13, Pc.Gold, Modifier.weight(1f))
        }
        // 48dp choices that flow onto a second line on a narrow phone (rc32 audit P2 #26).
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DialogText("Encounters:", 12, Pc.Dim, Modifier.align(Alignment.CenterVertically))
            // Trainers is the first choice when the route has any: the head sits right before it.
            if (playerHead != null && LogEncType.TRAINERS in tabs)
                Image(playerHead, null, Modifier.size(18.dp).align(Alignment.CenterVertically), filterQuality = FilterQuality.None)
            tabs.forEach { t -> LogChoice("${t.label} ${count(t)}", t == tab) { tab = t } }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(92.dp), Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tab == LogEncType.TRAINERS) {
                // realignTrainerGrid sorts by SortBy.TrainerLevel; a trainer button has no average, so that is
                // (999 + maxlevel): the highest party level, lowest first, then id.
                val trainers = route.trainers.sortedWith(compareBy<RandomizerLog.Trainer>({ it.maxLevel }, { it.number }))
                items(trainers, key = { it.number }) { t -> LogTrainerTile(t, rules, custom, portraitOf(t)) { onTrainer(t) } }
            } else {
                // LogTabRouteDetails.realignPokemonGrid: by rate, highest first, then by id.
                val wilds = (route.areas[tab] ?: emptyList()).sortedWith(compareBy<LogWild>({ -it.rate }, { it.pokemon?.id ?: 9999 }))
                items(wilds, key = { it.name }) { w ->
                    Column(
                        Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable(enabled = w.pokemon != null) { w.pokemon?.let(onPokemon) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val shown = names.species(w.name)
                        DialogText(shown, 12, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
                        LogMonIcon(w.pokemon?.let { spriteOf?.invoke(it) }, w.pokemon?.let { palOf?.invoke(it) }, 36.dp, shown)
                        DialogText(if (w.levelMin == w.levelMax) "Lv ${w.levelMin}" else "Lv ${w.levelMin} -- ${w.levelMax}", 12, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
                        DialogText("(${floor(w.rate * 100).toInt()}%)", 12, Pc.Dim, Modifier.fillMaxWidth(), TextAlign.Center)
                    }
                }
            }
        }
    }
}
