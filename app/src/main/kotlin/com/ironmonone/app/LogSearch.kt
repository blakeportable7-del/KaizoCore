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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** LogSearchScreen.SortBy, in the reference's index order. */
enum class LogSort(val label: String) {
    POKEDEX("Pokédex Number"), ALPHA("Alphabetical"), BST("BST"),
    HP("HP"), ATK("Attack"), DEF("Defense"), SPA("Sp. Atk"), SPD("Sp. Def"), SPE("Speed"),
    WILD("Wild Pokémon Lv."), TRAINER("Trainer Level"),
}

/** LogSearchScreen.FilterBy, in the reference's index order. */
enum class LogFilter(val label: String) {
    ROUTE("Route Name"), TRAINER("Trainer Name"), NAME("Pokémon Name"), ABILITY("Ability"), MOVE("Levelup Move"),
}

/** One row of a Pokemon's TM list: a label ("Gym TMs", "Other TMs") or a machine. */
class LogTmRow(val label: String? = null, val number: Int = 0, val move: String = "", val gym: Int = 9, val unlearnable: Boolean = false)

/**
 * LogSearchScreen for the app: which sorts and filters each tab offers (their
 * "contexts") and its defaults (the tab's defaultSortKey and defaultFilterKey),
 * and the searching and sorting each tab does with them. Pure, so it is tested.
 */
object LogSearch {
    fun sortsFor(tab: LogTab): List<LogSort> = when (tab) {
        LogTab.POKEMON -> listOf(LogSort.POKEDEX, LogSort.ALPHA, LogSort.BST, LogSort.HP, LogSort.ATK, LogSort.DEF, LogSort.SPA, LogSort.SPD, LogSort.SPE)
        LogTab.TRAINERS -> listOf(LogSort.ALPHA, LogSort.TRAINER)
        LogTab.ROUTES -> listOf(LogSort.ALPHA, LogSort.WILD, LogSort.TRAINER)
        else -> emptyList()
    }
    fun filtersFor(tab: LogTab): List<LogFilter> = when (tab) {
        LogTab.POKEMON -> listOf(LogFilter.NAME, LogFilter.ABILITY, LogFilter.MOVE)
        LogTab.TRAINERS -> listOf(LogFilter.TRAINER, LogFilter.NAME, LogFilter.ABILITY, LogFilter.MOVE)
        LogTab.ROUTES -> listOf(LogFilter.ROUTE, LogFilter.TRAINER, LogFilter.NAME, LogFilter.ABILITY, LogFilter.MOVE)
        else -> emptyList()
    }
    fun defaultSort(tab: LogTab): LogSort? = when (tab) {
        LogTab.POKEMON -> LogSort.POKEDEX; LogTab.TRAINERS -> LogSort.ALPHA; LogTab.ROUTES -> LogSort.WILD; else -> null
    }
    fun defaultFilter(tab: LogTab): LogFilter? = when (tab) {
        LogTab.POKEMON -> LogFilter.NAME; LogTab.TRAINERS -> LogFilter.TRAINER; LogTab.ROUTES -> LogFilter.ROUTE; else -> null
    }

    /** A base stat by the reference's key, whatever this log's table header calls it. */
    fun statOf(p: RandomizerLog.Pokemon, key: String): Int {
        val names = when (key) {
            "HP" -> listOf("HP")
            "ATK" -> listOf("ATK", "ATTACK")
            "DEF" -> listOf("DEF", "DEFENSE")
            "SPA" -> listOf("SPA", "SATK", "SP.ATK", "SPATK")
            "SPD" -> listOf("SPD", "SDEF", "SP.DEF", "SPDEF")
            "SPE" -> listOf("SPE", "SPEED")
            else -> listOf(key)
        }
        val i = p.statNames.indexOfFirst { n -> names.any { it.equals(n.trim(), ignoreCase = true) } }
        return if (i >= 0) p.stats.getOrElse(i) { 0 } else 0
    }

    /** Constants.OrderedLists.STATSTAGES from the game's own order (HP, Atk, Def, Speed, SpA, SpD). */
    fun refOrder(v: List<Int>): List<Int> = if (v.size >= 6) listOf(v[0], v[1], v[2], v[4], v[5], v[3]) else v

    /** RandomizerLog.Data.Pokemon[id].PreEvolutions: the log's evolutions read backwards, in log order. */
    fun preEvolutions(log: RandomizerLog, p: RandomizerLog.Pokemon): List<RandomizerLog.Pokemon> =
        log.pokemon.filter { q -> q.evolutions.any { it.equals(p.name, ignoreCase = true) } }

    /** LogTabPokemon's search and sort. */
    fun pokemonRows(log: RandomizerLog, query: String, filter: LogFilter, sort: LogSort): List<RandomizerLog.Pokemon> {
        val q = query.trim()
        val base = if (q.isEmpty()) log.pokemon else log.pokemon.filter { p ->
            when (filter) {
                LogFilter.ABILITY -> p.abilities.any { it.contains(q, ignoreCase = true) }
                LogFilter.MOVE -> (p.moves.map { it.second } + p.evoMoves).any { it.contains(q, ignoreCase = true) }
                else -> p.name.contains(q, ignoreCase = true)
            }
        }
        val stat = when (sort) {
            LogSort.HP -> "HP"; LogSort.ATK -> "ATK"; LogSort.DEF -> "DEF"; LogSort.SPA -> "SPA"; LogSort.SPD -> "SPD"; LogSort.SPE -> "SPE"; else -> null
        }
        return base.sortedWith(when {
            sort == LogSort.ALPHA -> compareBy<RandomizerLog.Pokemon>({ logTitle(it.name) }, { it.id })
            sort == LogSort.BST -> compareBy<RandomizerLog.Pokemon>({ -it.bst }, { it.id })
            stat != null -> compareBy<RandomizerLog.Pokemon>({ -statOf(it, stat) }, { it.id })
            else -> compareBy<RandomizerLog.Pokemon> { it.id }
        })
    }

    /** LogTabRoutes' includeInGrid per filter, then the chosen sort. */
    fun routeRows(routes: List<LogRoute>, log: RandomizerLog, query: String, filter: LogFilter, sort: LogSort): List<LogRoute> {
        val q = query.trim()
        fun partyHas(r: LogRoute, test: (RandomizerLog.PartyMon) -> Boolean) = r.trainers.any { t -> t.party.any(test) }
        val base = if (q.isEmpty()) routes else routes.filter { r ->
            when (filter) {
                LogFilter.TRAINER -> r.trainers.any { logTitle(it.originalName).contains(q, ignoreCase = true) }
                LogFilter.NAME -> partyHas(r) { it.name.contains(q, ignoreCase = true) } ||
                    r.areas.values.any { ws -> ws.any { it.name.contains(q, ignoreCase = true) } }
                LogFilter.ABILITY -> partyHas(r) { m -> log.pokemonNamed(m.name)?.abilities?.any { it.contains(q, ignoreCase = true) } == true }
                LogFilter.MOVE -> partyHas(r) { m -> log.pokemonNamed(m.name)?.let { p -> log.movesAt(p, m.level).any { it.contains(q, ignoreCase = true) } } == true }
                else -> r.name.contains(q, ignoreCase = true)
            }
        }
        return base.sortedWith(when (sort) {
            LogSort.ALPHA -> compareBy<LogRoute>({ it.name }, { it.mapId })
            LogSort.TRAINER -> compareBy<LogRoute>({ it.avgTrainerLv ?: 999.0 }, { it.mapId })
            else -> compareBy<LogRoute>({ it.maxWildLv ?: 999 }, { it.mapId })
        })
    }

    /**
     * LogTabPokemonDetails' TM list: "Gym TMs", the gym TMs in gym order (the
     * gym TMs it cannot learn added when that box is ticked), "Other TMs", then
     * the rest by number.
     */
    fun tmRows(p: RandomizerLog.Pokemon, log: RandomizerLog, frlg: Boolean, showUnlearnable: Boolean): List<LogTmRow> {
        val gymNumbers = LogTms.gymTmNumbers(frlg)
        val byNumber = log.tms.associateBy { it.number }
        fun gymOf(n: Int) = gymNumbers.indexOf(n).let { if (it >= 0) it + 1 else 9 }
        val rows = p.tmsLearnable.map { n -> LogTmRow(null, n, byNumber[n]?.move ?: "", gymOf(n)) }.toMutableList()
        if (showUnlearnable) gymNumbers.forEachIndexed { i, n ->
            if (rows.none { it.number == n }) rows += LogTmRow(null, n, byNumber[n]?.move ?: "", i + 1, unlearnable = true)
        }
        rows.sortBy { it.gym * 1000 + it.number }
        val numGym = rows.takeWhile { it.gym <= 8 }.size
        rows.add(0, LogTmRow(label = "Gym TMs"))
        rows.add(numGym + 1, LogTmRow(label = "Other TMs"))
        return rows
    }
}

/**
 * LogSearchScreen, sized for a phone: "Search:" with its filter, "Sort by:"
 * with its order, and the text. The reference's on-screen keyboard is the
 * phone's own here.
 */
@Composable
internal fun LogSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    sorts: List<LogSort>,
    sort: LogSort,
    onSort: (LogSort) -> Unit,
    filters: List<LogFilter>,
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixText("Search:", 7, Pc.Dim, Modifier.width(50.dp))
            LogPicker(filter.label, filters.map { it.label }) { onFilter(filters[it]) }
            Spacer(Modifier.width(14.dp))
            PixText("Sort by:", 7, Pc.Dim, Modifier.width(50.dp))
            LogPicker(sort.label, sorts.map { it.label }) { onSort(sorts[it]) }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixText("FIND", 7, Pc.Dim, Modifier.width(34.dp))
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = Pc.Text, fontSize = 12.sp),
                cursorBrush = SolidColor(Pc.Gold),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) PixText("X", 7, Pc.Dim, Modifier.clickable { onQuery("") }.padding(4.dp))
        }
    }
}

/** A choice drawn in the tracker's own style: the current value, and a list to pick from when tapped. */
@Composable
private fun LogPicker(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PixText("$current ▼", 7, Pc.Gold, Modifier.clickable { open = true }.padding(vertical = 4.dp))
        if (open) {
            Popup(onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(Modifier.background(Pc.Page).border(1.dp, Pc.Gold)) {
                    options.forEachIndexed { i, o ->
                        PixText(o, 8, if (o == current) Pc.Gold else Pc.Text,
                            Modifier.clickable { onPick(i); open = false }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
