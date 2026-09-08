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
 * the badges and how far the run got. Kept as a TSV per game family under
 * the prep folder, read back for the Past Runs and Statistics screens.
 */
class PastRun(
    val date: Long,
    val seconds: Int,
    val fainted: RunMon,
    val enemy: RunMon,
    val location: String,
    val badges: Int,
    val progress: Int,
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

        /** Program.onRunEnded: the run that just ended, from the DS tracker's last state. */
        fun fromDs(state: NdsTrackerState, won: Boolean, seconds: Int): PastRun? {
            val fainted = state.party.firstOrNull { it.mon.curHp <= 0 } ?: state.party.firstOrNull() ?: return null
            val enemy = state.enemy ?: fainted
            val progress = if (won) WON else maxOf(state.progress, if (state.located) PAST_LAB else NOWHERE)
            return PastRun(System.currentTimeMillis(), seconds, runMon(fainted), runMon(enemy), "", Integer.bitCount(state.badges), progress)
        }
    }
}

class PastRunStore(private val file: File) {
    private val runs = ArrayList<PastRun>()
    var version by mutableStateOf(0)
        private set

    init { load() }

    private fun load() {
        runs.clear()
        if (!file.isFile) return
        runCatching {
            file.forEachLine { line ->
                val p = line.split('\t'); if (p.size < 7) return@forEachLine
                val f = PastRun.RunMon.decode(p[2]) ?: return@forEachLine
                val e = PastRun.RunMon.decode(p[3]) ?: return@forEachLine
                runs += PastRun(p[0].toLongOrNull() ?: return@forEachLine, p[1].toIntOrNull() ?: 0, f, e, p[4], p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0)
            }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(runs.joinToString("") { r ->
                listOf(r.date, r.seconds, r.fainted.encode(), r.enemy.encode(), r.location, r.badges, r.progress).joinToString("\t") + "\n"
            })
        }
        version++
    }

    fun log(run: PastRun) { runs += run; save() }
    fun all(): List<PastRun> = runs.toList()
    fun totalRuns() = runs.size
    fun totalRunsPastLab() = runs.count { it.progress > PastRun.NOWHERE }
    fun totalSeconds() = runs.sumOf { it.seconds.toLong() }

    /** SeedLogger.removeNoBadgeRuns. */
    fun removeNoBadgeRuns() { runs.removeAll { it.badges == 0 }; save() }

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
        val progress = "Overall Progress" to (listOf("Past Lab" to runs.count { it.progress > PastRun.NOWHERE }) +
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
 * says "You won!".
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
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("PAST RUNS", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("<", 12, Pc.Text, Modifier.clickable { if (runs.isNotEmpty()) { index = (index - 1 + runs.size) % runs.size; showEnemy = false } }.padding(horizontal = 10.dp))
                PixText(if (runs.isEmpty()) "0/0" else "${index + 1}/${runs.size}", 9, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PixText(">", 12, Pc.Text, Modifier.clickable { if (runs.isNotEmpty()) { index = (index + 1) % runs.size; showEnemy = false } }.padding(horizontal = 10.dp))
            }
            Spacer(Modifier.height(6.dp))
            val run = runs.getOrNull(index)
            if (run == null) {
                PixText("No data was found.", 8, Pc.Dim)
            } else {
                val m = if (showEnemy) run.enemy else run.fainted
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PcSprite(spriteOf(m.species))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        PixText((if (showEnemy) "Lost to " else "") + m.name, 9, Pc.Text)
                        PixText("Lv.${m.level}  BST ${m.bst}  " + listOf(m.type1, m.type2).filter { it.isNotEmpty() }.distinct().joinToString("/"), 7, Pc.Dim)
                        PixText(m.ability, 7, Pc.Gold)
                        PixText(m.moves.joinToString(", "), 7, Pc.Dim, wrap = true)
                    }
                }
                Spacer(Modifier.height(4.dp))
                PixText(run.dateText, 8, Pc.Text)
                PixText(if (run.progress == PastRun.WON) "You won!" else run.location.ifEmpty { "Badges: ${run.badges}" }, 8, if (run.progress == PastRun.WON) Pc.Positive else Pc.Text)
                Spacer(Modifier.height(4.dp))
                Row { com.ironmonone.app.gen3.Gen3Button("SWAP") { showEnemy = !showEnemy } }
            }
            Spacer(Modifier.height(8.dp))
            PixText("Sort", 8, Pc.Text)
            listOf("NEWEST" to "Newest", "OLDEST" to "Oldest", "A_TO_Z" to "A-Z").forEach { (k, label) ->
                GearToggle(label, sort == k, radio = true) { sort = k; index = 0 }
            }
            Spacer(Modifier.height(6.dp))
            PixText("Minimum badges", 8, Pc.Text)
            Row(Modifier.fillMaxWidth()) {
                (0..8).forEach { n ->
                    PixText("$n", 8, if (minBadges == n) Pc.Gold else Pc.Dim, Modifier.weight(1f).border(1.dp, if (minBadges == n) Pc.Gold else Pc.Border).clickable { minBadges = n; index = 0 }.padding(vertical = 4.dp), TextAlign.Center)
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
                PixText("STATISTICS", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            PixText("Total runs: $totalRuns", 8, Pc.Text)
            PixText("Playtime: " + String.format("%.1f hours", store.totalSeconds() / 3600.0), 8, Pc.Text)
            Spacer(Modifier.height(6.dp))
            val (name, rows) = sets[index]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("<", 12, Pc.Text, Modifier.clickable { index = (index - 1 + sets.size) % sets.size }.padding(horizontal = 10.dp))
                PixText(name, 9, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PixText(">", 12, Pc.Text, Modifier.clickable { index = (index + 1) % sets.size }.padding(horizontal = 10.dp))
            }
            Spacer(Modifier.height(6.dp))
            val max = maxOf(1, if (name == "Overall Progress") totalRuns else pastLab)
            if (rows.isEmpty()) PixText("No runs recorded yet.", 8, Pc.Dim)
            rows.forEach { (label, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixText(label, 7, Pc.Text, Modifier.width(96.dp))
                    Box(Modifier.weight(1f).height(10.dp).border(1.dp, Pc.Border)) {
                        Box(Modifier.fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f)).height(10.dp).background(Pc.Gold))
                    }
                    PixText("$count", 7, Pc.Text, Modifier.width(30.dp), TextAlign.End)
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
                PixText("EVO DATA (${tracker?.speciesName(species) ?: "#$species"})", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            if (targets.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("<", 12, Pc.Text, Modifier.clickable { target = (target - 1 + targets.size) % targets.size }.padding(horizontal = 10.dp))
                PixText("Evo ${target + 1}: " + (tracker?.speciesName(targets[target]) ?: ""), 9, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PixText(">", 12, Pc.Text, Modifier.clickable { target = (target + 1) % targets.size }.padding(horizontal = 10.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("NAME" to "Name", "BST" to "BST", "PERCENT" to "Percent").forEach { (k, label) ->
                    PixText(label, 8, if (sort == k) Pc.Gold else Pc.Dim, Modifier.weight(1f).border(1.dp, if (sort == k) Pc.Gold else Pc.Border).clickable { sort = k }.padding(vertical = 4.dp), TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (rows.isEmpty()) PixText("No evolution data for this Pokemon.", 8, Pc.Dim)
            rows.forEach { (id, perc) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    PcSprite(spriteOf(id))
                    PixText(tracker?.speciesName(id) ?: "#$id", 8, Pc.Text, Modifier.weight(1f).padding(start = 6.dp))
                    PixText("${tracker?.speciesInfoFor(id)?.bst ?: 0}", 8, Pc.Dim, Modifier.width(44.dp), TextAlign.End)
                    PixText(String.format("%.2f%%", perc), 8, Pc.Text, Modifier.width(60.dp), TextAlign.End)
                }
            }
        }
    }
}
