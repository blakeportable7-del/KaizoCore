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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTracker
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The DS tracker's PastRun (PastRun.lua) and SeedLogger: one line per run
 * that ended, with the Pokemon that fainted, the Pokemon that did it, where,
 * the badges and how far the run got. Kept as a TSV per game under the prep
 * folder (pastRunStoreFor), read back for the Past Runs and Statistics screens.
 */
class PastRun(
    val date: Long,
    val seconds: Int,
    val fainted: RunMon,
    val enemy: RunMon,
    val location: String,
    val badges: Int,
    val progress: Int,
    /** Which run it was, so a run is logged once (rc32 audit P2 #41); 0 and "" on a line logged before that. */
    val attempt: Int = 0,
    val seed: String = "",
) {
    class RunMon(val species: Int, val name: String, val level: Int, val bst: Int, val type1: String, val type2: String, val ability: String, val moves: List<String>) {
        fun encode() = listOf(species, name, level, bst, type1, type2, ability, moves.joinToString(",")).joinToString("|")
        companion object {
            fun decode(s: String): RunMon? {
                val p = s.split('|'); if (p.size < 8) return null
                return RunMon(p[0].toIntOrNull() ?: return null, p[1], p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4], p[5], p[6], p[7].split(',').filter { it.isNotEmpty() })
            }
        }
    }
    val dateText: String get() = SimpleDateFormat("MM/dd/yy hh:mm a", Locale.US).format(Date(date))

    companion object {
        const val NOWHERE = 0; const val PAST_LAB = 1; const val WON = 2

        private fun runMon(m: NdsTrackedMon): RunMon = RunMon(
            m.mon.species, m.speciesName, m.mon.level, m.info?.bst ?: 0, m.info?.type1 ?: "", m.info?.type2 ?: "",
            m.abilityName, m.moves.map { it.name },
        )

        /**
         * Program.onRunEnded: the run that just ended, from the DS tracker's last state. [progressSoFar] is the run's own
         * (StatMarks.dsProgress). "Has a party" used to stand in for past the lab, and a run with no party is not logged
         * at all, so every lost run was filed as past the lab (rc33 audit P1 #25). A badge is past the lab for certain,
         * which covers a run that was under way before its progress was kept.
         */
        fun fromDs(state: NdsTrackerState, won: Boolean, seconds: Int, progressSoFar: Int = 0, attempt: Int = 0, seed: String = ""): PastRun? {
            // Program.onRunEnded logs playerPokemon and enemyPokemon: in battle your Pokemon on
            // the field and the opponent; a win ends the run as its battle ends, when they are
            // still the last battle's.
            val fainted = state.playerActive ?: state.lastBattlePlayer.takeIf { won }
                ?: state.party.firstOrNull { it.mon.curHp <= 0 } ?: state.party.firstOrNull() ?: return null
            val enemy = state.enemy ?: state.lastBattleEnemy.takeIf { won } ?: fainted
            val progress = if (won) WON else maxOf(state.progress, progressSoFar, if (Integer.bitCount(state.badges) > 0) PAST_LAB else NOWHERE)
            return PastRun(System.currentTimeMillis(), seconds, runMon(fainted), runMon(enemy), state.areaName, Integer.bitCount(state.badges), progress, attempt, seed)
        }
    }
}

/**
 * SeedLogger's log for the game [state] reads. The reference keeps one per game
 * (SeedLogger(self, gameInfo.NAME) writes savedData/<name>.pastlog, SeedLogger.lua:233),
 * so Diamond, Pearl and Platinum each have their own. Builds before 2026-09-28 kept
 * one per badge set (pastruns-DPPT.tsv shared by all three, HGSS by HeartGold and
 * SoulSilver): which game each of those runs came from was never written down, so
 * that file is read alongside the game's own rather than guessed apart.
 */
fun pastRunStoreFor(state: NdsTrackerState, fileFor: (String) -> File): PastRunStore =
    if (state.gameName.isEmpty()) PastRunStore(fileFor(state.badgeSet))
    else PastRunStore(fileFor(state.gameName), legacy = fileFor(state.badgeSet), gameName = state.gameName)

class PastRunStore(
    private val file: File,
    legacy: File? = null,
    /** GameInfo NAME of the game the log belongs to, for the one label that differs by game. */
    private val gameName: String = "",
) {
    private val runs = ArrayList<PastRun>()
    /** The per-family file older builds wrote; its runs show here too, and new runs never go into it. */
    private val legacy: File? = legacy?.takeIf { it.absoluteFile != file.absoluteFile }
    private val fromLegacy = HashSet<PastRun>()
    var version by mutableStateOf(0)
        private set

    init { load() }

    private fun load() {
        runs.clear(); fromLegacy.clear()
        legacy?.let { l -> read(l).let { runs += it; fromLegacy += it } }
        runs += read(file)
    }

    /** Read through the writer (DiskWriter.read): a run logged a moment ago and not yet on disk is there. */
    private fun read(f: File): List<PastRun> {
        val out = ArrayList<PastRun>()
        runCatching {
            DiskWriter.read(f)?.lineSequence()?.forEach { line ->
                val p = line.split('\t'); if (p.size < 7) return@forEach
                val fm = PastRun.RunMon.decode(p[2]) ?: return@forEach
                val e = PastRun.RunMon.decode(p[3]) ?: return@forEach
                out += PastRun(p[0].toLongOrNull() ?: return@forEach, p[1].toIntOrNull() ?: 0, fm, e, p[4], p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0,
                    p.getOrNull(7)?.toIntOrNull() ?: 0, p.getOrNull(8).orEmpty())
            }
        }
        return out
    }

    /**
     * Written whole or not at all (SafeWrite), on the writer's thread (DiskWriter), which says so when it fails. It was
     * rewritten in place inside a runCatching: a full phone or a kill as a DS run ended left the file empty, and every
     * earlier DS past run was gone at the next launch (rc32 audit P2 #40, P3 #42).
     */
    private fun write(f: File, list: List<PastRun>) {
        DiskWriter.write(f, list.joinToString("") { r ->
            listOf(r.date, r.seconds, r.fainted.encode(), r.enemy.encode(), r.location, r.badges, r.progress, r.attempt, r.seed).joinToString("\t") + "\n"
        })
    }

    private fun save(legacyChanged: Boolean = false) {
        write(file, runs.filter { it !in fromLegacy })
        if (legacyChanged) legacy?.let { write(it, runs.filter { r -> r in fromLegacy }) }
        version++
    }

    fun log(run: PastRun) { runs += run; save() }

    /**
     * Program.onRunEnded, once per run (rc32 audit P2 #41). The first end of an attempt and seed stays its line unless
     * Retry the battle came after it (a RETRY in the run's [events]), as RunHistory files a run's end (fileRunEnd,
     * retriedAfter); a retried run's next end replaces its line. Retry, or Continue playing and coming back to Play,
     * logged the same run again, a win included for a run already lost, and Statistics counted every copy. A line
     * logged before runs were named matches nothing. Returns whether a line was written.
     */
    fun logEnd(run: PastRun, events: List<RunEvents.Entry>?): Boolean {
        val same = runs.lastOrNull { run.attempt > 0 && run.seed.isNotEmpty() && it.attempt == run.attempt && it.seed == run.seed }
        if (same != null) {
            if (events?.any { it.kind == RunEvents.Kind.RETRY && it.at > same.date } != true) return false
            runs.remove(same)
        }
        runs += run
        save()
        return true
    }
    fun all(): List<PastRun> = runs.toList()
    fun totalRuns() = runs.size
    fun totalRunsPastLab() = runs.count { it.progress > PastRun.NOWHERE }
    fun totalSeconds() = runs.sumOf { it.seconds.toLong() }

    /** SeedLogger.removeNoBadgeRuns; the older shared file loses its no-badge runs too, as it did before. */
    fun removeNoBadgeRuns() {
        val gone = runs.filter { it.badges == 0 }.toSet()
        runs.removeAll(gone)
        val legacyChanged = fromLegacy.removeAll(gone)
        save(legacyChanged)
    }

    /** SeedLogger.getPastRunHashesSorted: NEWEST, OLDEST or A_TO_Z (by the fainted Pokemon's name), then the badge filter. */
    fun sorted(sort: String, minBadges: Int): List<PastRun> {
        val list = when (sort) {
            "OLDEST" -> runs.sortedBy { it.date }
            "A_TO_Z" -> runs.sortedBy { it.fainted.name }
            else -> runs.sortedByDescending { it.date }
        }
        return list.filter { it.badges >= minBadges }
    }

    /**
     * StatisticsOrganizer's past run statistics, counted over every run
     * (the reference keeps a running count; the sums are the same). Each
     * set is a name and (label, count) rows; the counted lists are capped
     * at their ten largest as capAt10 does.
     */
    fun statistics(): List<Pair<String, List<Pair<String, Int>>>> {
        fun counted(name: String, forEnemy: Boolean, keys: (PastRun.RunMon) -> List<String>): Pair<String, List<Pair<String, Int>>> {
            val counts = LinkedHashMap<String, Int>()
            for (r in runs) for (k in keys(if (forEnemy) r.enemy else r.fainted).filter { it.isNotEmpty() }) counts[k] = (counts[k] ?: 0) + 1
            return name to counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(10).map { it.key to it.value }
        }
        fun bst(name: String, forEnemy: Boolean) = name to listOf("< 300" to (0..299), "300 - 399" to (300..399), "400 - 499" to (400..499), "500+" to (500..800)).map { (label, range) ->
            label to runs.count { (if (forEnemy) it.enemy else it.fainted).bst in range }
        }
        // StatisticsScreen.lua:52-53: Black and White (VERSION_GROUP 4) call the first milestone "Past N".
        val pastLab = if (com.ironmonone.tracker.nds.NdsLogData.gameNamed(gameName)?.versionGroup == 4) "Past N" else "Past Lab"
        val progress = "Overall Progress" to (listOf(pastLab to runs.count { it.progress > PastRun.NOWHERE }) +
            (1..8).map { n -> (if (n == 1) "1 Badge" else "$n Badges") to runs.count { it.badges >= n } } +
            listOf("Won" to runs.count { it.progress == PastRun.WON }))
        return listOf(
            progress,
            bst("BST Ranges You Ran", false), bst("BST Ranges You Lost to", true),
            counted("Types You Ran", false) { listOf(it.type1, it.type2).distinct() },
            counted("Types You Lost to", true) { listOf(it.type1, it.type2).distinct() },
            counted("Pokemon You Ran", false) { listOf(it.name) },
            counted("Pokemon You Lost to", true) { listOf(it.name) },
            counted("Moves You Had", false) { it.moves },
            counted("Moves Your Enemies Had", true) { it.moves },
            counted("Abilities You Had", false) { listOf(it.ability) },
            counted("Abilities Your Enemies Had", true) { listOf(it.ability) },
        )
    }
}

/**
 * PastRunsScreen.lua: one run at a time, "i/N" with arrows, Swap between the
 * Pokemon that fainted and the one that beat it, sort by Newest, Oldest or
 * A-Z, the minimum-badges filter, and Remove no-badge runs behind a
 * confirmation. The run's date and place sit where the notes go; a won run
 * says "You won!". This, Statistics and Evo Data draw their words in
 * DialogText, which follows the phone's font size (rc32 audit P2 #19).
 */
@Composable
fun PastRunsDialog(store: PastRunStore, spriteOf: @Composable (Int) -> ImageBitmap?, onClose: () -> Unit) {
    var sort by remember { mutableStateOf("NEWEST") }
    var minBadges by remember { mutableStateOf(0) }
    var index by remember { mutableStateOf(0) }
    var showEnemy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val runs = remember(store.version, sort, minBadges) { store.sorted(sort, minBadges) }
    if (index >= runs.size) index = 0
    var jump by remember { mutableStateOf(false) }
    if (jump) Dialog(onDismissRequest = { jump = false }) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("JUMP TO A RUN", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { jump = false }
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(runs.size) { i ->
                    val r = runs[i]
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, if (i == index) Pc.Gold else Pc.Border)
                        .clickable { index = i; showEnemy = false; jump = false }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DialogText("${i + 1}", 13, Pc.Gold, Modifier.widthIn(min = 30.dp))
                        Column(Modifier.weight(1f)) {
                            DialogText(r.fainted.name, 13, Pc.Text)
                            DialogText(r.dateText, 12, Pc.Dim)
                        }
                    }
                }
            }
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("PAST RUNS", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PcTap("<", 12, Pc.Text, "Previous") { if (runs.isNotEmpty()) { index = (index - 1 + runs.size) % runs.size; showEnemy = false } }
                // Tapping the count opens every run as a list to jump to; the arrows alone
                // meant stepping one run at a time through a long history (2026-09-27, audit).
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = runs.size > 1, onClickLabel = "Jump to a run") { jump = true },
                    contentAlignment = Alignment.Center) {
                    DialogText(if (runs.isEmpty()) "0/0" else "${index + 1}/${runs.size}", 14, Pc.Gold, align = TextAlign.Center)
                }
                PcTap(">", 12, Pc.Text, "Next") { if (runs.isNotEmpty()) { index = (index + 1) % runs.size; showEnemy = false } }
            }
            Spacer(Modifier.height(6.dp))
            val run = runs.getOrNull(index)
            if (run == null) {
                DialogText("No data was found.", 13, Pc.Dim)
            } else {
                val m = if (showEnemy) run.enemy else run.fainted
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PcSprite(spriteOf(m.species))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        DialogText((if (showEnemy) "Lost to " else "") + m.name, 14, Pc.Text)
                        DialogText("Lv.${m.level}  BST ${m.bst}  " + listOf(m.type1, m.type2).filter { it.isNotEmpty() }.distinct().joinToString("/"), 12, Pc.Dim)
                        DialogText(m.ability, 12, Pc.Gold)
                        DialogText(m.moves.joinToString(", "), 12, Pc.Dim)
                    }
                }
                Spacer(Modifier.height(4.dp))
                DialogText(run.dateText, 13, Pc.Text)
                DialogText(if (run.progress == PastRun.WON) "You won!" else run.location.ifEmpty { "Badges: ${run.badges}" }, 13, if (run.progress == PastRun.WON) Pc.Positive else Pc.Text)
                Spacer(Modifier.height(4.dp))
                Row { com.ironmonone.app.gen3.Gen3Button("SWAP") { showEnemy = !showEnemy } }
            }
            Spacer(Modifier.height(8.dp))
            DialogText("Sort", 13, Pc.Text)
            listOf("NEWEST" to "Newest", "OLDEST" to "Oldest", "A_TO_Z" to "A-Z").forEach { (k, label) ->
                GearToggle(label, sort == k, radio = true) { sort = k; index = 0 }
            }
            Spacer(Modifier.height(6.dp))
            DialogText("Minimum badges", 13, Pc.Text)
            Row(Modifier.fillMaxWidth()) {
                (0..8).forEach { n ->
                    // A choice of one, 48dp tall, and the chosen one underlined and framed heavier (rc32 audit P2 #19).
                    val on = minBadges == n
                    Box(Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                        .selectable(selected = on, role = Role.RadioButton) { minBadges = n; index = 0 }, contentAlignment = Alignment.Center) {
                        DialogText("$n", 13, if (on) Pc.Gold else Pc.Dim, align = TextAlign.Center, underline = on)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!confirmRemove) com.ironmonone.app.gen3.Gen3Button("REMOVE NO-BADGE RUNS") { confirmRemove = true }
            else Row {
                com.ironmonone.app.gen3.Gen3Button("CONFIRM REMOVE", accent = true) { store.removeNoBadgeRuns(); confirmRemove = false; index = 0 }
                Spacer(Modifier.width(8.dp)); com.ironmonone.app.gen3.Gen3Button("CANCEL") { confirmRemove = false }
            }
        }
    }
}

/** StatisticsScreen.lua: Total runs and Playtime, then one statistic at a time as a bar graph, arrows to step through them. */
@Composable
fun StatisticsDialog(store: PastRunStore, onClose: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val sets = remember(store.version) { store.statistics() }
    val totalRuns = store.totalRuns(); val pastLab = store.totalRunsPastLab()
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("STATISTICS", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            DialogText("Total runs: $totalRuns", 13, Pc.Text)
            DialogText("Playtime: " + String.format(Locale.US, "%.1f hours", store.totalSeconds() / 3600.0), 13, Pc.Text)
            Spacer(Modifier.height(6.dp))
            val (name, rows) = sets[index]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PcTap("<", 12, Pc.Text, "Previous") { index = (index - 1 + sets.size) % sets.size }
                DialogText(name, 14, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PcTap(">", 12, Pc.Text, "Next") { index = (index + 1) % sets.size }
            }
            Spacer(Modifier.height(6.dp))
            val max = maxOf(1, if (name == "Overall Progress") totalRuns else pastLab)
            if (rows.isEmpty()) DialogText("No runs recorded yet.", 13, Pc.Dim)
            rows.forEach { (label, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    DialogText(label, 12, Pc.Text, Modifier.width(96.dp).padding(end = 4.dp))
                    Box(Modifier.weight(1f).height(10.dp).border(1.dp, Pc.Border)) {
                        Box(Modifier.fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f)).height(10.dp).background(Pc.Gold))
                    }
                    DialogText("$count", 12, Pc.Text, Modifier.widthIn(min = 30.dp), TextAlign.End)
                }
            }
        }
    }
}

/**
 * EvoDataScreen.lua: what the lead can randomly evolve into and how likely,
 * one target at a time for species with several regular evolutions, sorted
 * by Name, BST or Percent. The reference also links to the community site.
 */
@Composable
fun EvoDataDialog(species: Int, tracker: NdsTracker?, spriteOf: @Composable (Int) -> ImageBitmap?, onClose: () -> Unit) {
    val data = remember(species) { tracker?.evoData(species) ?: emptyMap() }
    val targets = remember(species) { data.keys.toList() }
    var target by remember(species) { mutableStateOf(0) }
    var sort by remember { mutableStateOf("PERCENT") }
    val rows = remember(species, target, sort) {
        val list = data[targets.getOrNull(target)] ?: emptyList()
        when (sort) {
            "NAME" -> list.sortedBy { tracker?.speciesName(it.first) ?: "" }
            "BST" -> list.sortedByDescending { tracker?.speciesInfoFor(it.first)?.bst ?: 0 }
            else -> list.sortedByDescending { it.second }
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("EVO DATA (${tracker?.speciesName(species) ?: "#$species"})", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            if (targets.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PcTap("<", 12, Pc.Text, "Previous") { target = (target - 1 + targets.size) % targets.size }
                DialogText("Evo ${target + 1}: " + (tracker?.speciesName(targets[target]) ?: ""), 14, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PcTap(">", 12, Pc.Text, "Next") { target = (target + 1) % targets.size }
            }
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
            if (rows.isEmpty()) DialogText("No evolution data for this Pokemon.", 13, Pc.Dim)
            rows.forEach { (id, perc) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    PcSprite(spriteOf(id))
                    DialogText(tracker?.speciesName(id) ?: "#$id", 13, Pc.Text, Modifier.weight(1f).padding(start = 6.dp))
                    DialogText("${tracker?.speciesInfoFor(id)?.bst ?: 0}", 13, Pc.Dim, Modifier.widthIn(min = 44.dp), TextAlign.End)
                    DialogText(evoShare(perc), 13, Pc.Text, Modifier.widthIn(min = 60.dp), TextAlign.End)
                }
            }
        }
    }
}
