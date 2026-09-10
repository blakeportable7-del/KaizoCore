package com.ironmonone.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** LogOverlay.NavFilters.TMs. */
enum class LogTmFilter(val label: String) { TM_NUMBER("TM #"), GYM("Gym TMs") }

/** One gym's TM (LogTabTMs.buildPagedButtons with TrainerData.GymTMs): the machine, its move, and the leader who gives it. */
class LogGymTm(val gym: Int, val number: Int, val move: String, val leader: RandomizerLog.Trainer?)

object LogTms {
    /** TrainerData.GymTMs' numbers in gym order: Ruby, Sapphire and Emerald share theirs; FireRed and LeafGreen have their own. */
    fun gymTmNumbers(frlg: Boolean): List<Int> =
        if (frlg) listOf(39, 3, 34, 19, 6, 4, 38, 26) else listOf(39, 8, 34, 50, 42, 40, 4, 3)

    /** The Gym TMs filter's rows, by gym number. The leader is the gym's leader in the log (its original, not a rematch). */
    fun gymRows(log: RandomizerLog, rules: LogTrainerRules, frlg: Boolean): List<LogGymTm> {
        val byNumber = log.tms.associateBy { it.number }
        return gymTmNumbers(frlg).mapIndexed { i, n ->
            val gym = i + 1
            val leader = log.trainers.filter { rules.use(it.number) && rules.gymNumber(it.number) == gym }.minByOrNull { it.number }
            LogGymTm(gym, n, byNumber[n]?.move ?: "", leader)
        }
    }
}

/**
 * LogTabTMs: "Filter by:" TM # or Gym TMs, opening on Gym TMs. The gym view is
 * one row per gym: the badge, the TM and its move, the leader who hands it
 * out (custom name when that box is ticked) and the gym's number; the leader
 * opens their page. TM # lists every machine in number order.
 */
@Composable
internal fun LogTmsTab(
    log: RandomizerLog,
    rules: LogTrainerRules,
    frlg: Boolean,
    custom: Boolean,
    badgeSet: String?,
    filter: LogTmFilter,
    onFilter: (LogTmFilter) -> Unit,
    onTrainer: (RandomizerLog.Trainer) -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixText("Filter by:", 7, Pc.Dim)
            LogTmFilter.entries.forEach { f ->
                PixText(f.label, 7, if (f == filter) Pc.Gold else Pc.Text, Modifier.clickable { onFilter(f) }.padding(vertical = 4.dp))
            }
        }
        if (filter == LogTmFilter.GYM) {
            val rows = remember(log, rules, frlg) { LogTms.gymRows(log, rules, frlg) }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(rows, key = { it.gym }) { r ->
                    Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = remember(badgeSet, r.gym) { badgeSet?.let { PcAssets.badge(ctx, it, r.gym, true) } }
                        if (art != null) Image(art, "badge ${r.gym}", Modifier.size(20.dp), filterQuality = FilterQuality.None)
                        else Spacer(Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        PixText("TM%02d  %s".format(r.number, logTitle(r.move)), 8, Pc.Text, Modifier.weight(1f))
                        val leader = r.leader
                        if (leader != null) {
                            val name = logTitle(if (custom && leader.name.isNotBlank()) leader.customShortName else leader.shortName)
                            PixText(name, 7, Pc.Gold, Modifier.clickable { onTrainer(leader) }.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        PixText("Gym ${r.gym}", 7, Pc.Dim, Modifier.width(42.dp))
                    }
                }
            }
        } else {
            val tms = remember(log) { log.tms.sortedBy { it.number } }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tms, key = { it.number }) { t ->
                    PixText("TM%02d  %s".format(t.number, logTitle(t.move)), 7, Pc.Text,
                        Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp))
                }
            }
        }
    }
}
