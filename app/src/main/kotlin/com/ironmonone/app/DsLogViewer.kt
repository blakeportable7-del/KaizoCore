package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironmonone.tracker.nds.NdsLogData
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTracker
import java.io.File
import androidx.compose.foundation.lazy.grid.items as gridItems

/** NDS-Ironmon-Tracker's LogViewerScreen: its six tabs, in its order. */
private enum class DsTab(val label: String) {
    POKEMON("Pokémon"), TRAINERS("Trainers"), PIVOTS("Pivots"), GYM_TMS("Gym TMs"), INFO("Info"), SEARCH("Search"),
}

/** Where the viewer is. The back arrow's history (LogViewerScreen.goBackFunctionList) is a stack of these. */
private data class DsView(
    val tab: DsTab = DsTab.POKEMON,
    /** Pokemon tab: the log's number of the Pokemon whose page is open. */
    val pokemon: Int? = null,
    /** Pokemon tab: the Stats screen. */
    val stats: Boolean = false,
    /** Trainers tab: the group, and the battle whose team is open (its 1-based position in the group). */
    val group: Int = 0,
    val team: Int? = null,
    val member: Int = 0,
)

/** What each tab keeps while you look at a Pokemon from it; a tab click starts them over (resetTabs). */
private class DsTabState {
    var overviewQuery by mutableStateOf("")
    var searchTrainers by mutableStateOf(false)
    var searchAbility by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchMatch by mutableStateOf<String?>(null)
    var pivotArea by mutableStateOf(0)
    var pivotType by mutableStateOf<String?>(null)
    var statistic by mutableStateOf(0)
    var statPos by mutableStateOf(0)
}

/**
 * NDS-Ironmon-Tracker's LogViewer, for a DS run: Pokemon (search, each
 * species' page with its stats, evolutions, moves, gym TMs and abilities, and
 * the Stats rankings), Trainers (rivals by location, gym leaders and the Elite
 * 4, each team with its stats estimated as TeamInfoScreen does), Pivots (the
 * early areas' encounters with their odds), Gym TMs, Info and Search (Pokemon
 * or trainers with a move or an ability). The arrow in the top right goes
 * back; with nothing to go back to it is the X that closes the log.
 *
 * Not drawn: the trainers' portraits and card art, the type-matchup hover on
 * a Pokemon's picture, and the bookmark filter for marked Pokemon; the phone's
 * keyboard stands in for the on-screen one.
 */
@Composable
internal fun DsLogViewer(file: File, tracker: NdsTracker, party: List<NdsTrackedMon>?, onClose: () -> Unit) {
    val log = remember(file) { RandomizerLog.parse(file) }
    val game = remember(log) { log?.let { NdsLogData.gameFor(tracker.map, DsLog.logGameName(it)) } }
    val ds = remember(log, game) {
        if (log == null || game == null || DsLog.logGameName(log) != game.name) null
        else DsLog(
            log, game,
            DsLog.starterNumber(log, listOfNotNull(tracker.firstPokemonId.takeIf { it > 0 }) + (party?.map { it.mon.species } ?: emptyList())),
            speciesName = { tracker.speciesName(it) },
        )
    }
    val ctx = LocalContext.current
    val sprites = remember { HashMap<Int, ImageBitmap?>() }
    val spriteOf: (Int) -> ImageBitmap? = { id ->
        if (sprites.containsKey(id)) sprites[id] else PcAssets.dsSprite(ctx, id, false).also { sprites[id] = it }
    }
    val moves = remember(tracker) { tracker.moveTable().filter { it.name.isNotBlank() && it.name != "-" }.associateBy { DsLog.norm(it.name) } }
    // Program.openLogFromPath: with a Pokemon in the party the viewer opens on its page.
    val lead = party?.firstOrNull()?.mon?.species
    var view by remember(ds) { mutableStateOf(if (ds != null && lead != null && ds.byId.containsKey(lead)) DsView(pokemon = lead) else DsView()) }
    val history = remember(ds) { mutableStateListOf<DsView>().also { if (view.pokemon != null) it.add(DsView()) } }
    var st by remember { mutableStateOf(DsTabState()) }
    fun go(next: DsView) { history.add(view); view = next }
    fun back() { if (history.isEmpty()) onClose() else view = history.removeAt(history.lastIndex) }
    fun openTab(t: DsTab) { history.clear(); st = DsTabState(); view = DsView(tab = t) }

    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    DsTab.entries.forEach { t ->
                        val on = t == view.tab
                        PixText(t.label, 7, if (on) Pc.Gold else Pc.Text,
                            Modifier.background(if (on) Pc.Page else Pc.Ground).border(1.dp, if (on) Pc.Gold else Pc.Border)
                                .clickable { openTab(t) }.padding(horizontal = 6.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
                PixText(if (history.isEmpty()) "X" else "<", 9, Pc.Text,
                    Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { back() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            if (log == null) { PixText("The log could not be read.", 8, Pc.Negative, wrap = true); return@Column }
            if (ds == null) {
                PixText("Game does not match. Only load logs with the same game as the one you're playing.", 8, Pc.Negative, wrap = true)
                return@Column
            }
            val v = view
            val toPokemon: (Int) -> Unit = { go(DsView(tab = DsTab.POKEMON, pokemon = it)) }
            when (v.tab) {
                DsTab.POKEMON -> when {
                    v.stats -> DsStatsScreen(ds, st, spriteOf, onOpen = toPokemon)
                    v.pokemon != null -> ds.byId[v.pokemon]?.let { p ->
                        DsPokemonPage(ds, p, moves, spriteOf, onSwap = { view = v.copy(pokemon = it) }, onOpen = { go(v.copy(pokemon = it)) })
                    }
                    else -> DsPokemonOverview(ds, st, spriteOf, onPokemon = { go(v.copy(pokemon = it)) }, onStats = { go(v.copy(stats = true)) })
                }
                DsTab.TRAINERS -> {
                    val g = ds.groups.getOrNull(v.group)
                    if (g == null) PixText("---", 8, Pc.Text)
                    else if (v.team != null) DsTeamPage(
                        ds, g, v.team, v.member, moves, spriteOf,
                        onPosition = { view = v.copy(team = it, member = 0) }, onMember = { view = v.copy(member = it) }, onPokemon = toPokemon,
                    )
                    else DsTrainerGroups(ds, v.group, onGroup = { view = v.copy(group = it) }, onBattle = { go(v.copy(team = it, member = 0)) })
                }
                DsTab.PIVOTS -> DsPivots(ds, st, onPokemon = toPokemon)
                DsTab.GYM_TMS -> DsGymTms(ds, moves, onLeader = { gi, pos -> go(DsView(tab = DsTab.TRAINERS, group = gi, team = pos)) })
                DsTab.INFO -> DsInfo(ds)
                DsTab.SEARCH -> DsSearch(ds, tracker, st, spriteOf, onPokemon = toPokemon,
                    onTrainer = { gi, pos, member -> go(DsView(tab = DsTab.TRAINERS, group = gi, team = pos, member = member)) })
            }
        }
    }
}

// ---- widgets --------------------------------------------------------------

@Composable
private fun DsBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), content = content)
}

@Composable
private fun DsArrow(text: String, visible: Boolean = true, onClick: () -> Unit) {
    val m = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    if (visible) PixText(text, 9, Pc.Text, Modifier.clickable { onClick() }.then(m)) else PixText(" ", 9, Pc.Text, m)
}

/** A Pokemon's picture; a form with no picture shows its name instead. */
@Composable
private fun DsMonIcon(art: ImageBitmap?, name: String, size: Int = 32, modifier: Modifier = Modifier) {
    if (art != null) Image(art, name, modifier.size(size.dp), filterQuality = FilterQuality.None)
    else Box(modifier.size(size.dp), contentAlignment = Alignment.Center) { PixText(name, 6, Pc.Dim, align = TextAlign.Center, wrap = true) }
}

@Composable
private fun DsField(value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = TextStyle(color = Pc.Text, fontSize = 12.sp), cursorBrush = SolidColor(Pc.Gold),
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) PixText("X", 7, Pc.Dim, Modifier.clickable { onValue("") }.padding(4.dp))
    }
}

/** The reference's BarGraph: "Base Stats (N total)" over six bars scaled to 255. */
@Composable
private fun DsBarGraph(heading: String, values: List<Int>) {
    DsBox(Modifier.fillMaxWidth()) {
        PixText(heading, 7, Pc.Text)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(74.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            values.forEach { v ->
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    PixText("$v", 6, Pc.Text)
                    Box(Modifier.width(14.dp).fillMaxHeight((v / 255f).coerceIn(0.02f, 1f)).background(Pc.Text))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { DsLog.STAT_KEYS.forEach { PixText(it, 6, Pc.Text) } }
    }
}

/** pokeball_large (the member shown) and pokeball_large_off. */
@Composable
private fun DsBall(size: Int, lit: Boolean = true, onClick: (() -> Unit)? = null) {
    Canvas(Modifier.size(size.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        drawCircle(Color(0xFFE8E8E8), r, c)
        drawArc(if (lit) Color(0xFFE03C3C) else Color(0xFF7A7A7A), 180f, 180f, true, Offset.Zero, this.size)
        drawLine(Color.Black, Offset(0f, r), Offset(this.size.width, r), 1f)
        drawCircle(Color.Black, r * 0.36f, c)
        drawCircle(Color.White, r * 0.2f, c)
    }
}

/** The dot where a team has no Pokemon. */
@Composable
private fun DsDot(size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(3.dp).background(Pc.Text)) }
}

private fun Modifier.underline(): Modifier = drawWithContent {
    drawContent()
    drawLine(Pc.Text, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
}

private fun Modifier.strike(): Modifier = drawWithContent {
    drawContent()
    val y = size.height / 2f
    drawLine(Pc.Text, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

private fun stab(p: RandomizerLog.Pokemon?, m: NdsMoveInfo?): Boolean =
    m != null && m.power > 0 && p?.types?.any { it.equals(m.type, ignoreCase = true) } == true

/** The move's details, where the reference shows them on hover. */
@Composable
private fun DsMoveDialog(m: NdsMoveInfo, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        DsBox(Modifier.clickable { onClose() }) {
            PixText(m.name, 9, Pc.Gold)
            Spacer(Modifier.height(4.dp))
            PixText("Type: ${logTitle(m.type)}", 7, Pc.Text)
            if (m.category.isNotBlank()) PixText("Category: ${m.category}", 7, Pc.Text)
            PixText("Power: ${if (m.power > 0) m.power.toString() else "---"}", 7, Pc.Text)
            PixText("Accuracy: ${if (m.accuracy > 0) m.accuracy.toString() else "---"}", 7, Pc.Text)
            PixText("PP: ${m.pp}", 7, Pc.Text)
        }
    }
}

// ---- Pokemon ---------------------------------------------------------------

/** PokemonOverviewScreen: search by the start of a name, four to a row, and the Stats button. */
@Composable
private fun DsPokemonOverview(ds: DsLog, st: DsTabState, spriteOf: (Int) -> ImageBitmap?, onPokemon: (Int) -> Unit, onStats: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixText("Type below to search any pokemon:", 7, Pc.Text, Modifier.weight(1f), wrap = true)
            Row(Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onStats() }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom) {
                listOf(15, 27, 21).forEach { h -> Box(Modifier.padding(end = 2.dp).width(6.dp).height((h * 0.6f).dp).background(Pc.Text)) }
                Spacer(Modifier.width(4.dp))
                PixText("Stats", 7, Pc.Text)
            }
        }
        Spacer(Modifier.height(6.dp))
        DsField(st.overviewQuery) { st.overviewQuery = it }
        Spacer(Modifier.height(6.dp))
        val q = st.overviewQuery.trim()
        val matches = remember(ds, q) { if (q.isEmpty()) emptyList() else ds.sortedPokemon.filter { ds.nameOf(it).startsWith(q, ignoreCase = true) } }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            gridItems(matches, key = { it.id }) { p ->
                Column(Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onPokemon(p.id) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    DsMonIcon(spriteOf(p.id), ds.nameOf(p))
                    PixText(ds.nameOf(p), 6, Pc.Text, align = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * PokemonStatScreen: the arrows walk the Pokemon in name order; base stats;
 * "Evos:" with each evolution and how, a picture that opens it; "Moves" (the
 * level each is learned, same-type moves in green) or, with the arrows,
 * "Gym TMs" (struck through where it cannot learn one); and its abilities.
 */
@Composable
private fun DsPokemonPage(
    ds: DsLog, p: RandomizerLog.Pokemon, moves: Map<String, NdsMoveInfo>, spriteOf: (Int) -> ImageBitmap?,
    onSwap: (Int) -> Unit, onOpen: (Int) -> Unit,
) {
    val list = ds.sortedPokemon
    val at = list.indexOfFirst { it.id == p.id }.coerceAtLeast(0)
    var evo by remember(p.id) { mutableStateOf(0) }
    var tms by remember(p.id) { mutableStateOf(false) }
    var info by remember { mutableStateOf<NdsMoveInfo?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsArrow("<") { onSwap(list[(at - 1 + list.size) % list.size].id) }
                DsMonIcon(spriteOf(p.id), ds.nameOf(p), 40)
                Spacer(Modifier.width(6.dp))
                PixText(ds.nameOf(p), 9, Pc.Text, Modifier.weight(1f))
                DsArrow(">") { onSwap(list[(at + 1) % list.size].id) }
            }
        }
        Spacer(Modifier.height(4.dp))
        DsBarGraph("Base Stats (${p.bst} total)", DsLog.STAT_KEYS.map { LogSearch.statOf(p, it) })
        Spacer(Modifier.height(4.dp))
        val evos = p.evolutions.mapNotNull { ds.log.pokemonNamed(it) }
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixText("Evos:", 8, Pc.Text, Modifier.width(52.dp))
                if (evos.isEmpty()) PixText("None", 7, Pc.Text)
                else {
                    val i = evo.coerceIn(0, evos.lastIndex)
                    val e = evos[i]
                    DsArrow("<", evos.size > 1) { evo = (i - 1 + evos.size) % evos.size }
                    DsMonIcon(spriteOf(e.id), ds.nameOf(e), 32, Modifier.clickable { onOpen(e.id) })
                    DsArrow(">", evos.size > 1) { evo = (i + 1) % evos.size }
                    PixText(ds.evoText(p, i), 7, Pc.Text, Modifier.weight(1f), wrap = true)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsArrow("<") { tms = !tms }
                PixText(if (tms) "Gym TMs" else "Moves", 8, Pc.Text, Modifier.weight(1f), align = TextAlign.Center)
                DsArrow(">") { tms = !tms }
            }
            if (!tms) p.moves.forEach { (lv, mv) ->
                val m = moves[DsLog.norm(mv)]
                val level = lv.toString().let { if (it.length == 1) "  $it" else it }
                PixText("$level ${m?.name ?: logTitle(mv)}", 7, if (stab(p, m)) Pc.Positive else Pc.Text,
                    Modifier.fillMaxWidth().clickable(enabled = m != null) { info = m }.padding(vertical = 2.dp))
            } else ds.game.gymTms.forEach { tm ->
                if (tm == -1) Spacer(Modifier.height(14.dp))
                else {
                    val mv = ds.tmMove(tm)
                    val m = moves[DsLog.norm(mv)]
                    val can = tm in p.tmsLearnable
                    PixText("TM %02d %s".format(tm, m?.name ?: logTitle(mv)), 7, if (can && stab(p, m)) Pc.Positive else Pc.Text,
                        Modifier.clickable(enabled = m != null) { info = m }.padding(vertical = 2.dp).then(if (can) Modifier else Modifier.strike()))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        DsBox(Modifier.fillMaxWidth()) {
            PixText("Abilities", 8, Pc.Text)
            Spacer(Modifier.height(2.dp))
            ds.abilityLines(p).forEach { PixText(it, 7, Pc.Text, Modifier.padding(vertical = 1.dp)) }
        }
    }
    info?.let { DsMoveDialog(it) { info = null } }
}

/** StatsScreen: six rankings, each Pokemon of the ten with its stats; its picture opens its page. */
@Composable
private fun DsStatsScreen(ds: DsLog, st: DsTabState, spriteOf: (Int) -> ImageBitmap?, onOpen: (Int) -> Unit) {
    val n = ds.statistics.size
    val si = st.statistic.coerceIn(0, n - 1)
    val s = ds.statistics[si]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsArrow("<") { st.statistic = (si - 1 + n) % n; st.statPos = 0 }
                PixText(s.name, 8, Pc.Text, Modifier.weight(1f), align = TextAlign.Center)
                DsArrow(">") { st.statistic = (si + 1) % n; st.statPos = 0 }
            }
            PixText(s.description, 7, Pc.Text, Modifier.fillMaxWidth(), align = TextAlign.Center, wrap = true)
        }
        if (s.top.isEmpty()) return@Column
        val pos = st.statPos.coerceIn(0, s.top.lastIndex)
        val p = s.top[pos]
        Spacer(Modifier.height(4.dp))
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsArrow("<") { st.statPos = (pos - 1 + s.top.size) % s.top.size }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DsMonIcon(spriteOf(p.id), ds.nameOf(p), 40, Modifier.clickable { onOpen(p.id) })
                    PixText("#${pos + 1}. ${ds.nameOf(p)}", 8, Pc.Text)
                }
                DsArrow(">") { st.statPos = (pos + 1) % s.top.size }
            }
        }
        Spacer(Modifier.height(4.dp))
        DsBarGraph("Base Stats (${p.bst} total)", DsLog.STAT_KEYS.map { LogSearch.statOf(p, it) })
    }
}

// ---- Trainers --------------------------------------------------------------

/**
 * TrainerOverviewScreen: a tab per group. A rival's battles are cards by
 * location, three to a row, with a ball per Pokemon (RivalOverviewScreen);
 * gym leaders and the rest are cards with their badge and name, four to a row
 * (TrainerGroupOverviewScreen). A card opens the team.
 */
@Composable
private fun DsTrainerGroups(ds: DsLog, group: Int, onGroup: (Int) -> Unit, onBattle: (Int) -> Unit) {
    val ctx = LocalContext.current
    val g = ds.groups[group]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ds.groups.forEachIndexed { i, gr ->
                if (i > 0) PixText(" | ", 8, Pc.Text)
                PixText(gr.name, 7, Pc.Text, Modifier.clickable { onGroup(i) }.then(if (i == group) Modifier.underline() else Modifier).padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        val perRow = if (g.isRival) 3 else 4
        g.battles.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { b ->
                    DsBox(Modifier.weight(1f).clickable { onBattle(b.position) }) {
                        if (g.isRival) {
                            PixText(b.location, 7, Pc.Text, wrap = true)
                            Spacer(Modifier.height(4.dp))
                            val size = ds.team(b).size
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { for (i in 0 until 6) if (i < size) DsBall(8) else DsDot(8) }
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            b.badge?.let { n ->
                                PcAssets.badge(ctx, ds.badgeSetFor(g), n, true)?.let { Image(it, null, Modifier.size(16.dp), filterQuality = FilterQuality.None) }
                                Spacer(Modifier.width(2.dp))
                            }
                            PixText(b.name, 7, Pc.Text, wrap = true)
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * TeamInfoScreen: the arrows by the name walk the group's battles, the balls
 * (or their arrows) pick the Pokemon, shown as the tracker shows one: stats
 * estimated from the trainer's IVs, its first ability, its item, and the four
 * moves it knows at its level. Its picture opens its page.
 */
@Composable
private fun DsTeamPage(
    ds: DsLog, g: DsLog.Group, position: Int, member: Int, moves: Map<String, NdsMoveInfo>, spriteOf: (Int) -> ImageBitmap?,
    onPosition: (Int) -> Unit, onMember: (Int) -> Unit, onPokemon: (Int) -> Unit,
) {
    val n = g.battles.size
    val at = g.battles.indexOfFirst { it.position == position }.coerceAtLeast(0)
    val b = g.battles[at]
    val team = ds.team(b)
    val mi = member.coerceIn(0, (team.size - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DsBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsArrow("<") { onPosition(g.battles[(at - 1 + n) % n].position) }
                PixText(g.battleName(b), 9, Pc.Text, Modifier.weight(1f), align = TextAlign.Center)
                DsArrow(">") { onPosition(g.battles[(at + 1) % n].position) }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                DsArrow("<", team.size > 1) { onMember((mi - 1 + team.size) % team.size) }
                for (i in 0 until 6) {
                    if (i < team.size) DsBall(16, lit = i == mi) { onMember(i) } else DsDot(16)
                    Spacer(Modifier.width(3.dp))
                }
                DsArrow(">", team.size > 1) { onMember((mi + 1) % team.size) }
            }
        }
        Spacer(Modifier.height(4.dp))
        team.getOrNull(mi)?.let { m -> DsTeamMon(ds.teamMon(b, m), moves, spriteOf, onPokemon) }
    }
}

@Composable
private fun DsTeamMon(t: DsLog.TeamMon, moves: Map<String, NdsMoveInfo>, spriteOf: (Int) -> ImageBitmap?, onPokemon: (Int) -> Unit) {
    val p = t.pokemon
    val open = Modifier.clickable(enabled = p != null) { p?.let { onPokemon(it.id) } }
    DsBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            DsMonIcon(p?.let { spriteOf(it.id) }, t.name, 48, open)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                PixText(t.name, 9, Pc.Text, open)
                PixText("Lv. ${t.level}", 7, Pc.Text)
                p?.let { PixText(it.types.joinToString(" / ") { ty -> logTitle(ty) }, 7, Pc.Text) }
                PixText(t.ability, 7, Pc.Gold)
                t.item?.let { PixText(it, 7, Pc.Gold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DsLog.STAT_KEYS.forEachIndexed { i, k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixText(k, 6, Pc.Text)
                    PixText("${t.stats[i]}", 8, Pc.Text)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            PixText("Moves", 7, Pc.Text, Modifier.weight(1f))
            PixText("PP", 7, Pc.Text, Modifier.width(28.dp))
            PixText("Pow", 7, Pc.Text, Modifier.width(34.dp))
            PixText("Acc", 7, Pc.Text, Modifier.width(34.dp))
        }
        t.moves.forEach { mv ->
            val m = moves[DsLog.norm(mv)]
            Row(Modifier.padding(vertical = 2.dp)) {
                PixText(m?.name ?: logTitle(mv), 7, if (stab(p, m)) Pc.Positive else Pc.Text, Modifier.weight(1f))
                PixText(m?.pp?.toString() ?: "---", 7, Pc.Text, Modifier.width(28.dp))
                PixText(m?.power?.takeIf { it > 0 }?.toString() ?: "---", 7, Pc.Text, Modifier.width(34.dp))
                PixText(m?.accuracy?.takeIf { it > 0 }?.toString() ?: "---", 7, Pc.Text, Modifier.width(34.dp))
            }
        }
    }
}

// ---- Pivots, Gym TMs, Info ---------------------------------------------------

/** PivotsScreen: the areas down the left; each area's encounter types, and its Pokemon with levels and odds. */
@Composable
private fun DsPivots(ds: DsLog, st: DsTabState, onPokemon: (Int) -> Unit) {
    val area = ds.pivotAreas.getOrNull(st.pivotArea)
    val data = area?.let { ds.pivots[it] }
    val types = DsLog.ENCOUNTER_TYPES.filter { data?.containsKey(it) == true }
    val cur = st.pivotType?.takeIf { it in types } ?: types.firstOrNull()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        DsBox(Modifier.width(118.dp).fillMaxHeight()) {
            PixText("Areas", 9, Pc.Text, Modifier.fillMaxWidth(), align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ds.pivotAreas.forEachIndexed { i, a ->
                    PixText(a, 7, if (i == st.pivotArea) Pc.Positive else Pc.Text,
                        Modifier.fillMaxWidth().clickable { st.pivotArea = i; st.pivotType = null }.padding(vertical = 4.dp), wrap = true)
                }
            }
        }
        DsBox(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    PixText(t, 7, Pc.Text, Modifier.clickable { st.pivotType = t }.then(if (t == cur) Modifier.underline() else Modifier).padding(vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            if (data == null) PixText("---", 7, Pc.Text)
            val rows = cur?.let { data?.get(it) } ?: emptyList()
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { r ->
                    Row(Modifier.fillMaxWidth().clickable { onPokemon(r.pokemon.id) }.padding(vertical = 4.dp)) {
                        PixText(ds.nameOf(r.pokemon), 7, Pc.Text, Modifier.weight(1f))
                        PixText(if (r.minLevel == r.maxLevel) "Lv. ${r.minLevel}" else "Lv. ${r.minLevel} - ${r.maxLevel}", 7, Pc.Text, Modifier.width(80.dp))
                        PixText("${r.percent}%", 7, Pc.Text, Modifier.width(34.dp), align = TextAlign.End)
                    }
                }
            }
        }
    }
}

/** GymTMScreen: each gym's TM, its move, the badge, and the leader, who opens their team. */
@Composable
private fun DsGymTms(ds: DsLog, moves: Map<String, NdsMoveInfo>, onLeader: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<NdsMoveInfo?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
        items(ds.gymTms) { r ->
            if (r.tm == -1) Spacer(Modifier.height(18.dp))
            else Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PixText("TM%02d".format(r.tm), 7, Pc.Text, Modifier.width(44.dp))
                val m = moves[DsLog.norm(r.move)]
                PixText(m?.name ?: logTitle(r.move), 7, Pc.Text, Modifier.weight(1f).clickable(enabled = m != null) { info = m })
                PcAssets.badge(ctx, r.badgeSet, r.badge, true)?.let { Image(it, null, Modifier.size(16.dp), filterQuality = FilterQuality.None) }
                Spacer(Modifier.width(6.dp))
                val gi = r.group?.let { ds.groups.indexOf(it) } ?: -1
                val leader = r.leader
                PixText(leader?.name ?: "", 7, Pc.Text,
                    Modifier.width(80.dp).clickable(enabled = leader != null && gi >= 0) { if (leader != null) onLeader(gi, leader.position) })
            }
        }
    }
    info?.let { DsMoveDialog(it) { info = null } }
}

/** InfoScreen: the game, the randomizer's version, the seed and the settings string, and "Copy info". */
@Composable
private fun DsInfo(ds: DsLog) {
    var copy by remember { mutableStateOf(false) }
    val name = ds.game.name.replace("Pokemon", "Pokémon")
    DsBox(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        listOf("Game Name:" to name, "Randomizer Version:" to ds.log.version, "Random Seed:" to ds.log.seed).forEach { (l, r) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                PixText(l, 7, Pc.Text, Modifier.width(150.dp))
                PixText(r, 7, Pc.Text, wrap = true)
            }
        }
        PixText("Settings String:", 7, Pc.Text, Modifier.padding(top = 6.dp, bottom = 2.dp))
        ds.log.settingsString.chunked(40).forEach { PixText(it, 6, Pc.Text) }
        Spacer(Modifier.height(10.dp))
        PixText("Copy info", 8, Pc.Text, Modifier.border(1.dp, Pc.Border).clickable { copy = true }.padding(horizontal = 8.dp, vertical = 5.dp))
    }
    if (copy) {
        val ctx = LocalContext.current
        val text = listOf(
            "Game Name: $name", "Randomizer Version: ${ds.log.version}",
            "Random Seed: ${ds.log.seed}", "Settings String: ${ds.log.settingsString}",
        ).joinToString("\n")
        var copied by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { copy = false }) {
            DsBox(Modifier.fillMaxWidth()) {
                PixText("Seed Info", 9, Pc.Gold)
                Spacer(Modifier.height(6.dp))
                PixText(text, 7, Pc.Text, Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp), wrap = true)
                Spacer(Modifier.height(8.dp))
                Row {
                    PixText(if (copied) "COPIED" else "COPY", 8, if (copied) Pc.Positive else Pc.Gold,
                        Modifier.border(1.dp, Pc.Border).clickable {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Seed info", text))
                            copied = true
                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                    Spacer(Modifier.width(10.dp))
                    PixText("Close", 8, Pc.Text, Modifier.border(1.dp, Pc.Border).clickable { copy = false }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// ---- Search ----------------------------------------------------------------

/**
 * SearchScreen: look for Pokemon or Trainers, with a Move or an Ability.
 * Typing lists the game's moves (or abilities) that start with it; picking
 * one finds every Pokemon that learns it, with the levels it keeps it (or
 * whether it is the only ability), or every trainer with a Pokemon that knows
 * it at its level, opening on that Pokemon.
 */
@Composable
private fun DsSearch(
    ds: DsLog, tracker: NdsTracker, st: DsTabState, spriteOf: (Int) -> ImageBitmap?,
    onPokemon: (Int) -> Unit, onTrainer: (Int, Int, Int) -> Unit,
) {
    val moveNames = remember(tracker, ds) {
        tracker.moveTable().map { it.name }.filter { it.isNotBlank() && it != "-" }.distinct().sorted()
            .ifEmpty { ds.log.pokemon.flatMap { p -> p.moves.map { it.second } }.distinct().sorted() }
    }
    val abilityNames = remember(tracker, ds) {
        tracker.abilityTable().filter { it.isNotBlank() && !it.all { c -> c == '-' } }.distinct().sorted()
            .ifEmpty { ds.log.pokemon.flatMap { it.abilitySlots }.filter { it != "---" }.distinct().sorted() }
    }
    Column(Modifier.fillMaxSize()) {
        DsBox(Modifier.fillMaxWidth()) {
            DsRadioRow("Look for:", listOf("Pokémon", "Trainers"), if (st.searchTrainers) 1 else 0) { st.searchTrainers = it == 1 }
            DsRadioRow("With:", listOf("Move", "Ability"), if (st.searchAbility) 1 else 0) { st.searchAbility = it == 1; st.searchMatch = null }
            Spacer(Modifier.height(4.dp))
            DsField(st.searchQuery) { st.searchQuery = it }
            val q = st.searchQuery.trim()
            val source = if (st.searchAbility) abilityNames else moveNames
            val matches = remember(source, q) { if (q.isEmpty()) emptyList() else source.filter { it.startsWith(q, ignoreCase = true) } }
            if (matches.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(matches) { name ->
                        PixText(name, 7, if (name == st.searchMatch) Pc.Positive else Pc.Text,
                            Modifier.border(1.dp, if (name == st.searchMatch) Pc.Positive else Pc.Border).clickable { st.searchMatch = name }
                                .padding(horizontal = 6.dp, vertical = 5.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val match = st.searchMatch
        val pokemonRows = remember(ds, match, st.searchAbility, st.searchTrainers) {
            if (match == null || st.searchTrainers) emptyList()
            else if (st.searchAbility) ds.pokemonWithAbility(match) else ds.pokemonWithMove(match)
        }
        val trainerRows = remember(ds, match, st.searchAbility, st.searchTrainers) {
            if (match == null || !st.searchTrainers) emptyList() else ds.trainersWith(match, move = !st.searchAbility)
        }
        val total = if (st.searchTrainers) trainerRows.size else pokemonRows.size
        PixText(if (total == 0) "None found" else "Total: $total", 8, Pc.Text, Modifier.fillMaxWidth(), align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (st.searchTrainers) items(trainerRows) { t ->
                Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).clickable {
                    onTrainer(ds.groups.indexOf(t.group), t.battle.position, t.found.first())
                }.padding(6.dp)) {
                    PixText(t.battleName, 8, Pc.Text)
                    PixText("${t.found.size} Pokémon", 7, Pc.Text)
                }
            } else items(pokemonRows) { r ->
                Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).clickable { onPokemon(r.pokemon.id) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DsMonIcon(spriteOf(r.pokemon.id), ds.nameOf(r.pokemon))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        PixText(ds.nameOf(r.pokemon), 8, Pc.Text)
                        PixText(r.label, 7, Pc.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun DsRadioRow(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        PixText(label, 7, Pc.Text, Modifier.width(70.dp))
        options.forEachIndexed { i, o ->
            val on = i == selected
            PixText(o, 7, if (on) Pc.Positive else Pc.Text,
                Modifier.padding(end = 6.dp).border(1.dp, if (on) Pc.Positive else Pc.Border).clickable { onPick(i) }.padding(horizontal = 8.dp, vertical = 5.dp))
        }
    }
}
