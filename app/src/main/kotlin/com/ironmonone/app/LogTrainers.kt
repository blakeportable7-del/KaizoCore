package com.ironmonone.app

import androidx.compose.foundation.Canvas
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
    /** Heart & Soul (2026-10-05): its own trainers, sixteen gyms and gym TMs, none of the five games' tables. */
    private val hns: Boolean = tracker.heartSoul

    /** TrainerData.getExcludedTrainers: dummy trainers and VS Seeker rematches, per game; Heart & Soul's post-game rematches. */
    private val excluded: Set<Int> = ranges(if (hns) HNS_EXCLUDED else if (frlg) FRLG_EXCLUDED else RSE_EXCLUDED)

    /** The gym TMs in badge order (TrainerData.GymTMs): eight on the five games, sixteen on Heart & Soul. */
    val gymTms: List<Int> get() = if (hns) LogTms.HNS_GYM_TMS else LogTms.gymTmNumbers(frlg)

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
    fun gymNumber(id: Int): Int? = if (group(id) != "Gym") null else if (hns) HNS_GYMS[id] else
        classKey(id)?.let { Regex("^GymLeader(\\d)$").find(it)?.groupValues?.get(1)?.toInt() }

    /** TrainerData.shouldUseClassName: FireRed/LeafGreen's rivals are shown by class, their name being the player's choice. */
    fun useClassName(id: Int): Boolean = frlg && !hns && (id in 326..334 || id in 426..440 || id in 739..741)

    /** TrainerData.isGiovanni: his team is drawn as Master Balls. */
    fun isGiovanni(id: Int): Boolean = frlg && !hns && id in 348..350

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
        /** The game's names for the log's, which a search by Pokemon name also finds them by (LogNames). */
        names: LogNames = LogNames.PLAIN,
    ): List<RandomizerLog.Trainer> {
        val all = log.trainers.filter { use(it.number) }
        val q = query.trim()
        if (q.isNotEmpty()) return all.filter { matches(log, it, searchBy, q, custom, names) }.sortedWith(if (sortBy != null) searchOrder(sortBy, custom) else allOrder())
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
    fun matches(log: RandomizerLog, t: RandomizerLog.Trainer, by: LogFilter, q: String, custom: Boolean, names: LogNames = LogNames.PLAIN): Boolean = when (by) {
        LogFilter.NAME -> t.party.any { names.finds(it.name, q) }
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

        /** Heart & Soul's: TRAINER_NONE and the post-game rematches (TRAINER_*_POSTOBC_HNS, layout-kaizo.json). */
        internal const val HNS_EXCLUDED = "0,631-652"

        /**
         * Heart & Soul's gym leaders to their badge, 1 to 16, from the tracker's own level cap rows (nuzlocke/levelcaps-gen3.tsv,
         * "hns": each leader's story battles and the badge flag it sets), every variant of a leader's battle included.
         */
        internal val HNS_GYMS: Map<Int, Int> by lazy {
            val out = HashMap<Int, Int>()
            GbaTracker::class.java.getResourceAsStream("/nuzlocke/levelcaps-gen3.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                for (l in lines) {
                    val c = l.split('\t')
                    if (c.size < 9 || c[0] != "hns") continue
                    val bit = c[8].trim().toIntOrNull() ?: continue
                    c[7].split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { out[it] = bit + 1 }
                }
            }
            out
        }

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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    names: LogNames = LogNames.PLAIN,
    /** Each trainer's portrait, read from the ROM (GbaTracker.trainerPicture); null where the build has none. */
    portraitOf: (RandomizerLog.Trainer) -> ImageBitmap? = { null },
) {
    Column(Modifier.fillMaxSize()) {
        // Five choices of 48dp flow onto a second line on a narrow phone (rc32 audit P2 #26).
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DialogText("Filter by:", 12, Pc.Dim, Modifier.align(Alignment.CenterVertically))
            LogTrainerFilter.entries.forEach { f ->
                val on = if (query.isNotBlank()) f == LogTrainerFilter.ALL else f == filter
                LogChoice(f.label, on) { onFilter(f) }
            }
        }
        val rows = remember(log, filter, query, custom, searchBy, sortBy, names) { rules.rows(log, filter, query, custom, searchBy, sortBy, names) }
        if (rows.isEmpty()) {
            DialogText("(No results)", 13, Pc.Dim)
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(92.dp), Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(rows, key = { it.number }) { t -> LogTrainerTile(t, rules, custom, portraitOf(t)) { onTrainer(t) } }
        }
    }
}

/**
 * A trainer on the Trainers tab or a route page (LogTabTrainers.drawTrainerPortraitInfo): the name above, the trainer's
 * portrait (TrainerData.getPortraitIcon on PC, the game's own picture of them here) and a ball per Pokemon below.
 */
@Composable
internal fun LogTrainerTile(t: RandomizerLog.Trainer, rules: LogTrainerRules, custom: Boolean, portrait: ImageBitmap? = null, onClick: () -> Unit) {
    Column(
        Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onClick() }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DialogText(rules.displayName(t, custom), 12, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
        if (portrait != null) Image(portrait, null, Modifier.size(56.dp), filterQuality = FilterQuality.None)
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
    names: LogNames = LogNames.PLAIN,
    /** The trainer's portrait from the ROM (LogTabTrainerDetails draws its class's), or null. */
    portrait: ImageBitmap? = null,
    /** Walking Pals for the team, which stand idle on PC (SpriteData.Types.Idle). */
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? = null,
    /** The PC tracker's Trainer Info panel for this trainer (LogTrainerInfoPanel), under the header. */
    infoPanel: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LogBack(onBack)
            if (portrait != null) Image(portrait, null, Modifier.size(64.dp).padding(end = 6.dp), filterQuality = FilterQuality.None)
            Column(Modifier.weight(1f)) {
                DialogText(rules.detailClass(t, custom).uppercase(), 13, Pc.Gold)
                DialogText(rules.detailName(t, custom).uppercase(), 13, Pc.Gold)
            }
            rules.gymNumber(t.number)?.let { g ->
                val art = remember(badgeSet, g) { badgeSet?.let { PcAssets.badge(ctx, it, g, true) } }
                if (art != null) Image(art, "badge $g", Modifier.size(24.dp), filterQuality = FilterQuality.None)
            }
        }
        if (infoPanel != null) { Spacer(Modifier.height(4.dp)); infoPanel() }
        Spacer(Modifier.height(4.dp))
        t.party.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { m -> LogPartyCell(m, log, spriteOf, moveTypes, onPokemon, names, palOf, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            t.party.forEachIndexed { i, m ->
                Box(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable { log.pokemonNamed(m.name)?.let(onPokemon) }, contentAlignment = Alignment.CenterStart) {
                    DialogText("${i + 1}. ${names.species(m.name)}", 12, Pc.Text)
                }
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
    names: LogNames,
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)?,
    modifier: Modifier,
) {
    val p = log.pokemonNamed(m.name)
    val monTypes = p?.types?.mapNotNull { Gen3Types.idOf(it) } ?: emptyList()
    Row(modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable(enabled = p != null) { p?.let(onPokemon) }.padding(6.dp)) {
        // LogTabTrainerDetails: the icon standing idle, its level under it.
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LogMonIcon(p?.let { spriteOf?.invoke(it) }, p?.let { palOf?.invoke(it) }, 36.dp, names.species(m.name))
            DialogText("Lv.${m.level}", 12, Pc.Text)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            val moves = p?.let { log.movesAt(it, m.level) } ?: emptyList()
            moves.forEach { mv ->
                val stab = moveTypes[mv.uppercase()]?.let { it in monTypes } == true
                DialogText(names.move(mv), 12, if (stab) Pc.Positive else Pc.Text)
            }
            m.item?.let { DialogText(logTitle(it), 12, Pc.Gold) }
        }
    }
}
