package com.ironmonone.pcui

import androidx.compose.ui.graphics.ImageBitmap
import com.ironmonone.app.LogEncType
import com.ironmonone.app.LogFilter
import com.ironmonone.app.LogGymTm
import com.ironmonone.app.LogNames
import com.ironmonone.app.LogRoute
import com.ironmonone.app.LogRoutes
import com.ironmonone.app.LogSearch
import com.ironmonone.app.LogSort
import com.ironmonone.app.LogTmRow
import com.ironmonone.app.LogTms
import com.ironmonone.app.LogTrainerFilter
import com.ironmonone.app.LogTrainerRules
import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.pcPictureOf
import com.ironmonone.tracker.GbaTracker
import java.io.File

/**
 * One run's randomizer log as the log viewer reads it (PLAN row 7): the app's own data layer (RandomizerLog, LogNames,
 * LogSearch, LogTrainerRules, LogRoutes, LogTms, copied from the app's files when pcui is built) over the Heart & Soul
 * log beside the run's game, with the game's own pictures from the tracker that plays it. Loaded once per game over,
 * off the panel's drawing.
 */
class PcLogData(
    val log: RandomizerLog,
    val names: LogNames,
    val rules: LogTrainerRules,
    val routes: List<LogRoute>,
    /** Every move by upper-case name to its type, for the same-type colouring (LogViewer's logMoveTypes). */
    val moveTypes: Map<String, Int>,
    val gymTms: List<LogGymTm>,
    private val tracker: GbaTracker,
    /** Every move by upper-case name to its category ("PHY", "SPE", "STA"), for the moves list's icons. */
    val moveCategories: Map<String, String> = emptyMap(),
) {
    private val sprites = HashMap<Int, ImageBitmap?>()
    private val portraits = HashMap<Int, ImageBitmap?>()
    val hns: Boolean = tracker.heartSoul

    fun speciesId(p: RandomizerLog.Pokemon): Int? = names.speciesId(p.name)

    /** The game's own front picture (Heart & Soul), else the bundled pack's, by the log's name. */
    fun sprite(p: RandomizerLog.Pokemon): ImageBitmap? = speciesId(p)?.let { sprite(it) }

    fun sprite(id: Int): ImageBitmap? = sprites.getOrPut(id) {
        runCatching { tracker.frontPicture(id)?.let { pcPictureOf(it.argb, it.width, it.height) } }.getOrNull() ?: PcPanel.sprite(id, hns)
    }

    /** The trainer's picture from the game (gTrainerSprites on Heart & Soul), 64 by 64; none where the build has none. */
    fun portrait(t: RandomizerLog.Trainer): ImageBitmap? = portraits.getOrPut(t.number) {
        runCatching { tracker.trainerPicture(t.number)?.let { pcPictureOf(it, 64, 64) } }.getOrNull()
    }

    /** The game's spelling of the log's name (LogNames.species). */
    fun shownName(p: RandomizerLog.Pokemon): String = names.species(p.name)
    fun shownName(name: String): String = names.species(name)

    fun pokemon(name: String): RandomizerLog.Pokemon? = log.pokemonNamed(name)

    fun trainerClass(t: RandomizerLog.Trainer): String = rules.detailClass(t, false)
    fun trainerName(t: RandomizerLog.Trainer): String = rules.detailName(t, false)

    fun pokemonRows(query: String, filter: LogFilter = LogFilter.NAME, sort: LogSort = LogSort.POKEDEX): List<RandomizerLog.Pokemon> =
        LogSearch.pokemonRows(log, query, filter, sort, names)

    fun trainerRows(filter: LogTrainerFilter, query: String, searchBy: LogFilter = LogFilter.TRAINER, sort: LogSort? = null): List<RandomizerLog.Trainer> =
        rules.rows(log, filter, query, com.ironmonone.app.TrackerOptions.logCustomTrainerNames, searchBy, sort, names = names)

    fun routeRows(query: String, filter: LogFilter = LogFilter.ROUTE, sort: LogSort = LogSort.WILD): List<LogRoute> =
        LogSearch.routeRows(routes, log, query, filter, sort, names)

    /** The TMs [p] can learn, the gym ones first with their gym, under the "Gym TMs" and "Other TMs" labels (LogSearch.tmRows). */
    fun tmRows(p: RandomizerLog.Pokemon): List<LogTmRow> =
        LogSearch.tmRows(p, log, false, com.ironmonone.app.TrackerOptions.logShowUnlearnableGymTms, gymNumbers = rules.gymTms)

    /** The InfoScreen facts the log's Pokemon card shows beside the viewer, from the game the log made. */
    fun expYield(id: Int): Int? = tracker.expYield(id)
    fun weight(id: Int): String? = tracker.weight(id)
    fun evolutionLines(id: Int): List<String> = com.ironmonone.tracker.EvoText.detailed(tracker.evolution(id), tracker.friendshipRequired())
    fun effectiveness(id: Int): Map<Double, List<String>> = tracker.effectivenessAgainst(id)

    /** A move by the log's name (MoveData by name), its row from the game's table and its summary. */
    fun moveId(name: String): Int? = moveIds[name.uppercase()]
    fun moveRow(id: Int): com.ironmonone.tracker.MoveRow? = if (id > 0) tracker.moveRowFor(id) else null
    fun moveDescription(id: Int): String? = if (id > 0) tracker.moveDescription(id) else null
    /** The trainer as the game holds it (TrainerInfoScreen), its route, an item's name. */
    fun trainerInfo(number: Int): GbaTracker.TrainerInfo? = runCatching { tracker.trainer(number) }.getOrNull()
    fun routeOfTrainer(number: Int): String? = runCatching { tracker.routeNameOfTrainer(number) }.getOrNull()
    fun itemName(id: Int): String = tracker.itemName(id)
    /** A badge's art for gym [n] (the game's own set, Heart & Soul's sixteen). */
    val badgeSet: String get() = if (hns) com.ironmonone.app.PcAssets.HNS_BADGES else "RSE"
    private val moveIds: Map<String, Int> by lazy { (1..tracker.lastMoveId).mapNotNull { id -> tracker.moveRowFor(id)?.let { it.name.uppercase() to id } }.toMap() }

    /** The Pokemon with the next or the previous species id (InfoScreen's NextPokemon and PreviousPokemon). */
    fun neighbour(p: RandomizerLog.Pokemon, step: Int): RandomizerLog.Pokemon? {
        val all = pokemonRows("", com.ironmonone.app.LogFilter.NAME, com.ironmonone.app.LogSort.POKEDEX)
        val i = all.indexOf(p).takeIf { it >= 0 } ?: return null
        return all[(i + step + all.size) % all.size]
    }

    /** The areas of [r] that have something in them, in the reference's tab order, trainers first. */
    fun areas(r: LogRoute): List<LogEncType> =
        LogEncType.entries.filter { if (it == LogEncType.TRAINERS) r.trainers.isNotEmpty() else !r.areas[it].isNullOrEmpty() }

    companion object {
        /** Reads [file] for the game [tracker] plays; null when it is not a randomizer log. */
        fun load(file: File, tracker: GbaTracker): PcLogData? {
            val log = RandomizerLog.parse(file) ?: return null
            val rules = LogTrainerRules(tracker, false)
            val moveTypes = (1..tracker.lastMoveId).mapNotNull { id -> tracker.moveRowFor(id)?.let { r -> r.type?.let { ty -> r.name.uppercase() to ty } } }.toMap()
            val moveCategories = (1..tracker.lastMoveId).mapNotNull { id -> tracker.moveRowFor(id)?.let { r -> r.category?.let { c -> r.name.uppercase() to c } } }.toMap()
            return PcLogData(log, LogNames.of(tracker), rules, LogRoutes.build(log, rules, tracker), moveTypes, LogTms.gymRows(log, rules, false), tracker, moveCategories)
        }
    }
}
