package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.Gen3Types

/** LogOverlay.NavFilters.Trainers. "Other" is commented out in the reference, so it is not offered here either. */
enum class LogTrainerFilter(val label: String) { ALL("All"), RIVAL("Rival"), GYM("Gym"), ELITE4("Elite 4"), BOSS("Boss") }

/**
 * Utils.firstToUpperEachWord: a lowercase letter after a space, full stop or
 * hyphen (or at the start) goes up, and nothing else changes. Names from the
 * tracker's own tables go through it as they are ("Victory Road 1F").
 */
internal fun refUpperEachWord(s: String): String {
    val t = s.trim()
    val sb = StringBuilder(t.length)
    for ((i, ch) in t.withIndex()) {
        val prev = if (i == 0) ' ' else t[i - 1]
        sb.append(if ((prev == ' ' || prev == '.' || prev == '-') && ch.isLowerCase()) ch.uppercaseChar() else ch)
    }
    return sb.toString()
}

/**
 * Log text the reference's way: RandomizerLog.formatInput lowercases it, then
 * firstToUpperEachWord. "MUD-SLAP" is "Mud-Slap", "LT. SURGE" is "Lt. Surge",
 * and "TATE&LIZA" is "Tate&liza", as the PC tracker shows it.
 */
internal fun logTitle(s: String): String = refUpperEachWord(s.trim().lowercase())

/**
 * The Gen 3 tracker's trainer rules for the log viewer (TrainerData.lua and
 * LogTabTrainers.lua), for a log read after a loss. Groups and class keys come
 * from the tracker's own tables (gen3/trainers-*.tsv); the exclusions, the
 * rivals shown by class, Giovanni's Master Balls and the grunt ids are
 * TrainerData's, per game family.
 */
class LogTrainerRules(private val tracker: GbaTracker, private val frlg: Boolean) {
    /** TrainerData.getExcludedTrainers: dummy trainers and VS Seeker rematches, per game. */
    private val excluded: Set<Int> = ranges(if (frlg) FRLG_EXCLUDED else RSE_EXCLUDED)

    fun group(id: Int): String = tracker.trainerGroup(id)
    /** The reference's TrainerGroups value: the tables say "Elite4", the reference sorts by "Elite 4". */
    fun groupLabel(id: Int): String = when (val g = group(id)) { "Elite4" -> "Elite 4"; "" -> "Other"; else -> g }
    fun classKey(id: Int): String? = tracker.trainerClassName(id)

    /** getExcludedTrainers, then TrainerData.shouldUseTrainer: once the run's rival is known, the other two go. */
    fun use(id: Int): Boolean {
        if (id in excluded) return false
        val choice = tracker.rivalChoice ?: return true
        val rival = tracker.whichRival(id) ?: return true
        return rival == choice
    }

    /** DataHelper.buildTrainerLogDisplay: a gym leader's badge number, from its class ("gymleader-N"). */
    fun gymNumber(id: Int): Int? = if (group(id) != "Gym") null else
        classKey(id)?.let { Regex("^GymLeader(\\d)$").find(it)?.groupValues?.get(1)?.toInt() }

    /** TrainerData.shouldUseClassName: FireRed/LeafGreen's rivals are shown by class, their name being the player's choice. */
    fun useClassName(id: Int): Boolean = frlg && (id in 326..334 || id in 426..440 || id in 739..741)

    /** TrainerData.isGiovanni: his team is drawn as Master Balls. */
    fun isGiovanni(id: Int): Boolean = frlg && id in 348..350

    /** Grunts have no unique names, so the reference appends the trainer id. */
    fun isGrunt(id: Int): Boolean = classKey(id) in GRUNT_CLASSES

    private fun useCustom(t: RandomizerLog.Trainer, custom: Boolean) = custom && t.name.isNotBlank()

    /** The name drawn above a trainer on the grid (LogTabTrainers.drawTrainerPortraitInfo). */
    fun displayName(t: RandomizerLog.Trainer, custom: Boolean): String {
        val c = useCustom(t, custom)
        var n = logTitle(
            if (useClassName(t.number)) (if (c) t.customCls else t.cls)
            else (if (c) t.customShortName else t.shortName)
        )
        if (isGrunt(t.number)) n = "$n #${t.number}"
        return n
    }

    /** The detail header's two lines: class, then name (grunts with their id). */
    fun detailClass(t: RandomizerLog.Trainer, custom: Boolean): String = logTitle(if (useCustom(t, custom)) t.customCls else t.cls)
    fun detailName(t: RandomizerLog.Trainer, custom: Boolean): String {
        val n = logTitle(if (useCustom(t, custom)) t.customShortName else t.shortName)
        return if (isGrunt(t.number)) "$n #${t.number}" else n
    }

    /** What the Trainer Name search reads: the full name, custom or not (the button's getText). */
    fun searchText(t: RandomizerLog.Trainer, custom: Boolean): String = logTitle(if (useCustom(t, custom)) t.name else t.originalName)

    private fun ordered(g: String) = g in setOf("Rival", "Boss", "Gym", "Elite 4")

    /** NavFilters.Trainers.All.sortFunc: by group, then level for the named groups, else by id. */
    private fun allOrder(): Comparator<RandomizerLog.Trainer> = compareBy<RandomizerLog.Trainer>(
        { groupLabel(it.number) },
        { if (ordered(groupLabel(it.number))) it.maxLevel else 0 },
        { if (ordered(groupLabel(it.number))) 0 else it.number },
    )

    /** The Elite 4 in order, the Champion last (the reference sorts by the portrait file's number). */
    private fun eliteOrder(id: Int): Int = classKey(id)?.let { k ->
        Regex("^EliteFour(\\d)$").find(k)?.groupValues?.get(1)?.toInt() ?: if (k == "EliteChampion") 5 else 9
    } ?: 9

    /**
     * LogTabTrainers.realignGrid: the trainers in [filter], sorted by its sortFunc.
     * A search looks past the filter at every trainer by name, as the
     * reference's does (the All tab lights up while it is active).
     */
    fun rows(
        log: RandomizerLog, filter: LogTrainerFilter, query: String, custom: Boolean,
        /** LogSearchScreen's filter (Trainer Name by default) and, once one is picked, its sort. */
        searchBy: LogFilter = LogFilter.TRAINER, sortBy: LogSort? = null,
    ): List<RandomizerLog.Trainer> {
        val all = log.trainers.filter { use(it.number) }
        val q = query.trim()
        if (q.isNotEmpty()) return all.filter { matches(log, it, searchBy, q, custom) }.sortedWith(if (sortBy != null) searchOrder(sortBy, custom) else allOrder())
        val base = when (filter) {
            LogTrainerFilter.ALL -> all.sortedWith(allOrder())
            LogTrainerFilter.RIVAL -> all.filter { groupLabel(it.number) == "Rival" }.sortedBy { it.maxLevel }
            LogTrainerFilter.BOSS -> all.filter { groupLabel(it.number) == "Boss" }.sortedBy { it.maxLevel }
            LogTrainerFilter.GYM -> all.filter { group(it.number) == "Gym" }.sortedBy { gymNumber(it.number) ?: 99 }
            LogTrainerFilter.ELITE4 -> all.filter { group(it.number) == "Elite4" }.sortedBy { eliteOrder(it.number) }
        }
        return if (sortBy != null) base.sortedWith(searchOrder(sortBy, custom)) else base
    }

    /** LogTabTrainers' includeInGrid per LogSearchScreen filter. */
    fun matches(log: RandomizerLog, t: RandomizerLog.Trainer, by: LogFilter, q: String, custom: Boolean): Boolean = when (by) {
        LogFilter.NAME -> t.party.any { it.name.contains(q, ignoreCase = true) }
        LogFilter.ABILITY -> t.party.any { m -> log.pokemonNamed(m.name)?.abilities?.any { it.contains(q, ignoreCase = true) } == true }
        LogFilter.MOVE -> t.party.any { m -> log.pokemonNamed(m.name)?.let { p -> log.movesAt(p, m.level).any { it.contains(q, ignoreCase = true) } } == true }
        else -> searchText(t, custom).contains(q, ignoreCase = true)
    }

    /** SortBy.Alphabetical on the button's text, or SortBy.TrainerLevel (999 + maxlevel on a trainer: the top level, lowest first). */
    fun searchOrder(sort: LogSort, custom: Boolean): Comparator<RandomizerLog.Trainer> = when (sort) {
        LogSort.TRAINER -> compareBy<RandomizerLog.Trainer>({ it.maxLevel }, { it.number })
        else -> compareBy<RandomizerLog.Trainer>({ searchText(it, custom) }, { it.number })
    }

    companion object {
        private val GRUNT_CLASSES = setOf("TeamRocketGrunt", "TeamAquaGrunt", "TeamMagmaGrunt")

        /** TrainerData.getExcludedTrainers, GameSettings.game 1 and 2 (Ruby, Sapphire, Emerald). */
        internal const val RSE_EXCLUDED =
            "40-43,47-50,54-56,60-63,67-70,84-87,101-104,110-113,117,120-123,132-135,139-142,147-150,173,175-178," +
            "184-187,197-200,207-210,219-222,228-231,239-242,250-253,257-260,276-279,282-285,288-291,295-298,303-306," +
            "308-311,314-317,328-331,341,346-349,354-357,360-363,365-368,370-373,379-382,388-391,393-396,409-412," +
            "421-424,430-433,437-440,456,462,466-468,477-480,482,485-489,497-500,515-518,541-544,548-551,555-558," +
            "562-565,607-610,622-625,633-634,636-639,643-646,657-660,682-685,688-691,770-801,805-847,851-855"

        /** The same, GameSettings.game 3 (FireRed, LeafGreen). */
        internal const val FRLG_EXCLUDED = "1-88,101,147,200,263,454-461,492-515,530,621-741"

        internal fun ranges(spec: String): Set<Int> = spec.split(',').flatMap { part ->
            val p = part.trim().split('-').map { it.toInt() }
            if (p.size == 2) (p[0]..p[1]).toList() else listOf(p[0])
        }.toSet()
    }
}

/**
 * LogTabTrainers: "Filter by:" and the five groups, then the trainers as a
 * grid, each with its name above and a Poke Ball per Pokemon below (Giovanni's
 * are Master Balls). The reference draws each class's portrait; the app ships
 * no trainer art, so the tile carries the name and the balls.
 */
@Composable
internal fun LogTrainersTab(
    log: RandomizerLog,
    rules: LogTrainerRules,
    query: String,
    filter: LogTrainerFilter,
    custom: Boolean,
    onFilter: (LogTrainerFilter) -> Unit,
    searchBy: LogFilter = LogFilter.TRAINER,
    sortBy: LogSort? = null,
    onTrainer: (RandomizerLog.Trainer) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixText("Filter by:", 7, Pc.Dim)
            LogTrainerFilter.entries.forEach { f ->
                val on = if (query.isNotBlank()) f == LogTrainerFilter.ALL else f == filter
                PixText(f.label, 7, if (on) Pc.Gold else Pc.Text, Modifier.clickable { onFilter(f) }.padding(vertical = 4.dp))
            }
        }
        val rows = remember(log, filter, query, custom, searchBy, sortBy) { rules.rows(log, filter, query, custom, searchBy, sortBy) }
        if (rows.isEmpty()) {
            PixText("(No results)", 8, Pc.Dim)
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(92.dp), Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(rows, key = { it.number }) { t -> LogTrainerTile(t, rules, custom) { onTrainer(t) } }
        }
    }
}

/** A trainer on the Trainers tab or a route page (LogTabTrainers.drawTrainerPortraitInfo): the name above, a ball per Pokemon below. */
@Composable
internal fun LogTrainerTile(t: RandomizerLog.Trainer, rules: LogTrainerRules, custom: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onClick() }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixText(rules.displayName(t, custom), 7, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        PokeBalls(t.party.size, rules.isGiovanni(t.number))
    }
}

/** TrackerScreen's small Poke Ball, one per party Pokemon; Master Ball colours for Giovanni. */
@Composable
private fun PokeBalls(count: Int, master: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) {
            Canvas(Modifier.size(8.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(r, r)
                drawCircle(Color(0xFFE8E8E8), r, c)
                drawArc(if (master) Color(0xFF8A4FC8) else Color(0xFFE03C3C), 180f, 180f, true, Offset.Zero, size)
                drawLine(Color.Black, Offset(0f, r), Offset(size.width, r), 1f)
                drawCircle(Color.Black, r * 0.36f, c)
                drawCircle(Color.White, r * 0.2f, c)
            }
        }
    }
}

/**
 * LogTabTrainerDetails: the class and name, the gym leader's badge, then the
 * party two to a row, each with its icon and level, the four moves it knows
 * at that level (same-type moves in green, as the reference's isstab) and its
 * held item, and the numbered list of the party beneath. Any Pokemon opens its
 * log page.
 */
@Composable
internal fun LogTrainerDetail(
    t: RandomizerLog.Trainer,
    log: RandomizerLog,
    rules: LogTrainerRules,
    custom: Boolean,
    badgeSet: String?,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    moveTypes: Map<String, Int>,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PixText("< BACK", 7, Pc.Dim, Modifier.clickable { onBack() }.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                PixText(rules.detailClass(t, custom).uppercase(), 8, Pc.Gold)
                PixText(rules.detailName(t, custom).uppercase(), 8, Pc.Gold)
            }
            rules.gymNumber(t.number)?.let { g ->
                val art = remember(badgeSet, g) { badgeSet?.let { PcAssets.badge(ctx, it, g, true) } }
                if (art != null) Image(art, "badge $g", Modifier.size(24.dp), filterQuality = FilterQuality.None)
            }
        }
        Spacer(Modifier.height(4.dp))
        t.party.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { m -> LogPartyCell(m, log, spriteOf, moveTypes, onPokemon, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            t.party.forEachIndexed { i, m ->
                PixText("${i + 1}. ${logTitle(m.name)}", 7, Pc.Text,
                    Modifier.clickable { log.pokemonNamed(m.name)?.let(onPokemon) }.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun LogPartyCell(
    m: RandomizerLog.PartyMon,
    log: RandomizerLog,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    moveTypes: Map<String, Int>,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    modifier: Modifier,
) {
    val p = log.pokemonNamed(m.name)
    val monTypes = p?.types?.mapNotNull { Gen3Types.idOf(it) } ?: emptyList()
    Row(modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable(enabled = p != null) { p?.let(onPokemon) }.padding(6.dp)) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val art = p?.let { spriteOf?.invoke(it) }
            if (art != null) Image(art, m.name, Modifier.size(36.dp), filterQuality = FilterQuality.None)
            else Spacer(Modifier.size(36.dp))
            PixText("Lv.${m.level}", 7, Pc.Text)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            val moves = p?.let { log.movesAt(it, m.level) } ?: emptyList()
            moves.forEach { mv ->
                val stab = moveTypes[mv.uppercase()]?.let { it in monTypes } == true
                PixText(logTitle(mv), 7, if (stab) Pc.Positive else Pc.Text)
            }
            m.item?.let { PixText(logTitle(it), 7, Pc.Gold) }
        }
    }
}
