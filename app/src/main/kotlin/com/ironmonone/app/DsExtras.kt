package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.nds.NdsTracker
import java.io.File

/**
 * TimerScreen.lua: a run clock in HH:MM:SS that counts while the game runs,
 * pauses on a tap (the reference's left click) and stops for good when the
 * run ends. Session only, like the reference's timer seconds in its tracked
 * data. Shown under SETUP on the DS panel when the option is on.
 */
class RunTimer(private val startedAt: Long = System.currentTimeMillis()) {
    var paused by mutableStateOf(false)
    private var deducted = 0L
    private var pausedAt = 0L
    var stopped by mutableStateOf(false)

    fun seconds(now: Long = System.currentTimeMillis()): Long {
        val end = if (paused || stopped) pausedAt else now
        return ((end - startedAt - deducted) / 1000).coerceAtLeast(0)
    }

    fun toggle(now: Long = System.currentTimeMillis()) {
        if (stopped) return
        if (paused) { deducted += now - pausedAt; paused = false } else { pausedAt = now; paused = true }
    }

    fun stop(now: Long = System.currentTimeMillis()) { if (!stopped) { if (!paused) pausedAt = now; stopped = true } }

    companion object {
        fun hms(seconds: Long): String = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}

@Composable
fun RunTimerLine(timer: RunTimer) {
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(timer) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    val text = RunTimer.hms(timer.seconds().also { tick })
    Row(Modifier.clickable { timer.toggle() }.border(1.dp, if (timer.paused) Pc.Border else Pc.Gold).padding(horizontal = 6.dp, vertical = 2.dp)) {
        PixText(text + (if (timer.paused) "  (paused)" else ""), 8, if (timer.paused) Pc.Dim else Pc.Text)
    }
}

/**
 * TrackedPokemonScreen.lua: every species with tracked data this run, in
 * name order, one at a time with i/N and arrows, and a search box that
 * jumps to a name. The card carries what the DS tracker keeps: types, BST,
 * abilities, stat marks, moves seen and the note.
 */
@Composable
fun TrackedPokemonDialog(marks: StatMarks, seen: Set<Int>, tracker: NdsTracker?, spriteOf: @Composable (Int) -> ImageBitmap?, onClose: () -> Unit) {
    val ids = remember(marks, seen) {
        (marks.markedSpecies() + marks.notedSpecies() + marks.movesSeenSpecies() + marks.abilitySeenSpecies() + seen).filter { it > 0 }.distinct()
            .sortedBy { tracker?.speciesName(it) ?: "#$it" }
    }
    var index by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val matches = remember(query, ids) { if (query.isBlank()) emptyList() else ids.filter { (tracker?.speciesName(it) ?: "").startsWith(query.trim(), ignoreCase = true) }.take(6) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("TRACKED POKEMON", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("<", 12, Pc.Text, Modifier.clickable { if (ids.isNotEmpty()) index = (index - 1 + ids.size) % ids.size }.padding(horizontal = 10.dp))
                PixText(if (ids.isEmpty()) "0/0" else "${index + 1}/${ids.size}", 9, Pc.Gold, Modifier.weight(1f), TextAlign.Center)
                PixText(">", 12, Pc.Text, Modifier.clickable { if (ids.isNotEmpty()) index = (index + 1) % ids.size }.padding(horizontal = 10.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PixText("Search:", 8, Pc.Dim, Modifier.width(56.dp))
                BasicTextField(query, { query = it }, Modifier.weight(1f), textStyle = TextStyle(color = Pc.Text, fontSize = 12.sp), singleLine = true)
            }
            matches.forEach { id ->
                PixText(tracker?.speciesName(id) ?: "#$id", 8, Pc.Gold, Modifier.clickable { index = ids.indexOf(id); query = "" }.padding(4.dp))
            }
            Spacer(Modifier.height(6.dp))
            val id = ids.getOrNull(index)
            if (id == null) PixText("Nothing tracked yet this run.", 8, Pc.Dim)
            else {
                val info = tracker?.speciesInfoFor(id)
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PcSprite(spriteOf(id))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        PixText(tracker?.speciesName(id) ?: "#$id", 9, Pc.Text)
                        if (info != null) {
                            PixText(listOf(info.type1, info.type2).filter { it.isNotEmpty() }.distinct().joinToString("/") + "  BST ${info.bst}", 7, Pc.Dim)
                            PixText(marks.abilityFor(id) ?: listOf(info.ability1, info.ability2).filter { it.isNotEmpty() }.distinct().joinToString(" / "), 7, Pc.Gold)
                        }
                    }
                    val m = marks.of(id)
                    Column {
                        StatMarks.STAT_NAMES.forEachIndexed { i, n -> val st = m.getOrElse(i) { 0 }; if (st != 0) PixText(n + " " + StatMarks.symbol(st), 7, Pc.Gold) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                PixText("Moves seen", 8, Pc.Gold)
                val moves = marks.movesSeenFor(id)
                if (moves.isEmpty()) PixText("---", 8, Pc.Dim)
                moves.forEach { mv -> Row { PixText(mv.name, 8, Pc.Text, Modifier.width(120.dp)); PixText(if (mv.minLv == mv.maxLv) "Lv.${mv.minLv}" else "Lv.${mv.minLv}-${mv.maxLv}", 8, Pc.Dim) } }
                Spacer(Modifier.height(4.dp))
                PixText("Note", 8, Pc.Gold)
                PixText(marks.noteFor(id).ifEmpty { "---" }, 8, Pc.Text, wrap = true)
            }
        }
    }
}

/**
 * TourneyTracker.lua, the HeartGold / SoulSilver tournament scorer: the
 * reference's milestone list with its points, checked against the trainers
 * this session's battles have ended against, plus the three Evo bonuses
 * added by hand. Scores are kept per seed in prep/tourney.tsv and summed as
 * the cumulative score. Three dungeon milestones also wait for the player
 * to leave the dungeon's map set, as the reference does.
 */
class TourneyTracker(private val file: File) {
    class Milestone(val name: String, val points: Int, val trainerIds: List<Int> = emptyList(), val isRival: Boolean = false, val milestones: List<Int> = emptyList(), val mapAreas: Set<Int> = emptySet()) {
        /** TrainerMilestone.completed: the trainers beaten (any one for a rival) and, for a dungeon, the player out of its map set. */
        fun completed(defeated: Set<Int>, done: Set<Int>, mapId: Int): Boolean = when {
            milestones.isNotEmpty() -> milestones.all { it in done }
            isRival -> trainerIds.any { it in defeated }
            else -> trainerIds.all { it in defeated } && (mapAreas.isEmpty() || mapId !in mapAreas)
        }
    }
    class Score(val seed: String, val milestones: MutableList<Int>, val bonuses: MutableList<Int>)

    companion object {
        /** TourneyTracker.DUNGEON_MAP_SETS. */
        val SPROUT_TOWER = setOf(110, 155, 156)
        val LIGHTHOUSE = setOf(115, 220, 221, 222, 223, 224, 225, 446)
        val ROCKET_HQ = setOf(247, 248, 249)
        /** Defined by the reference and, like the reference, not attached to a milestone: the Radio Tower full clear goes on its trainers alone. */
        val RADIO_TOWER = setOf(118, 199, 112, 186, 187, 188, 189, 190, 447)
        val MILESTONES: List<Milestone> = listOf(
            Milestone("Beat Rival 1", 1, listOf(495, 496, 497), isRival = true),
            Milestone("Beat Sprout Tower", 1, listOf(290), mapAreas = SPROUT_TOWER),
            Milestone("Beat Falkner", 1, listOf(20)),
            Milestone("Beat Rival 2", 1, listOf(1, 266, 269), isRival = true),
            Milestone("Beat Bugsy", 2, listOf(21)),
            Milestone("Beat Whitney", 1, listOf(30)),
            Milestone("Beat Lighthouse", 1, listOf(212), mapAreas = LIGHTHOUSE),
            Milestone("Full Cleared Lighthouse", 1, listOf(401, 211, 73, 213, 217, 37, 215, 212, 214)),
            Milestone("Beat Rival 3", 1, listOf(263, 267, 270), isRival = true),
            Milestone("Beat Morty", 2, listOf(31)),
            Milestone("Beat Rocket Hideout", 1, listOf(479), mapAreas = ROCKET_HQ),
            Milestone("Beat Chuck", 1, listOf(34)),
            Milestone("Beat Jasmine", 1, listOf(33)),
            Milestone("Beat Pryce", 1, listOf(32)),
            Milestone("Beat Gyms 5/6/7", 1, milestones = listOf(12, 13, 14)),
            Milestone("Beat Rival 4", 1, listOf(271, 288, 289), isRival = true),
            Milestone("Beat Archer", 1, listOf(485)),
            Milestone("Full Cleared Radio Tower", 2, listOf(195, 193, 14, 283, 228, 199, 196, 197, 227, 185, 198, 187, 188, 186, 189, 471, 190, 191, 192, 472, 200, 706, 487, 478, 485)),
            Milestone("Beat Clair", 3, listOf(35)),
            Milestone("Beat Kimono Girls", 1, listOf(160, 161, 162, 163, 164)),
            Milestone("Beat Rival 5", 1, listOf(264, 268, 272), isRival = true),
            Milestone("Beat Will", 1, listOf(245)),
            Milestone("Beat Koga", 1, listOf(247)),
            Milestone("Beat Bruno", 2, listOf(418)),
            Milestone("Beat Karen", 2, listOf(246)),
            Milestone("Beat Lance", 3, listOf(244)),
            Milestone("Beat Rival 6", 1, listOf(285, 286, 287), isRival = true),
            Milestone("Beat Janine", 1, listOf(257)),
            Milestone("Beat Surge", 1, listOf(255)),
            Milestone("Beat Misty", 1, listOf(254)),
            Milestone("Beat Brock", 1, listOf(253)),
            Milestone("Beat Sabrina", 1, listOf(258)),
            Milestone("Beat Erika", 1, listOf(256)),
            Milestone("Beat Blaine", 2, listOf(259)),
            Milestone("Beat Blue", 2, listOf(261)),
            Milestone("Beat Red", 5, listOf(260)),
        )
        val BONUSES: List<Pair<String, Int>> = listOf("Evo Bonus (Lv. 1 - 19)" to 2, "Evo Bonus (Lv. 20 - 29)" to 3, "Evo Bonus (Lv. 30+)" to 5)
        /** The reference's fullClearIDs, by milestone number (1-based). */
        val FULL_CLEAR_IDS = setOf(8, 19)
    }

    val scores = ArrayList<Score>()
    var version by mutableStateOf(0)
        private set

    init { load() }

    private fun load() {
        scores.clear()
        if (file.isFile) runCatching {
            file.forEachLine { line ->
                val p = line.split('\t'); if (p.size < 3) return@forEachLine
                scores += Score(p[0], p[1].split(',').mapNotNull { it.toIntOrNull() }.toMutableList(), p[2].split(',').mapNotNull { it.toIntOrNull() }.toMutableList())
            }
        }
    }

    fun save() {
        runCatching { file.parentFile?.mkdirs(); file.writeText(scores.joinToString("") { "${it.seed}\t${it.milestones.joinToString(",")}\t${it.bonuses.joinToString(",")}\n" }) }
        version++
    }

    /** The score for a seed (the reference keys on the ROM hash), created on first sight. */
    fun scoreFor(seed: String): Score = scores.firstOrNull { it.seed == seed } ?: Score(seed, ArrayList(), ArrayList()).also { scores += it; save() }

    fun points(s: Score): Int = s.milestones.sumOf { MILESTONES.getOrNull(it - 1)?.points ?: 0 } + s.bonuses.sumOf { BONUSES.getOrNull(it - 1)?.second ?: 0 }
    fun cumulative(): Int = scores.sumOf { points(it) }

    /** TourneyTracker.updateMilestones: returns the milestones newly completed, in order. */
    fun update(seed: String, defeated: Set<Int>, mapId: Int = 0): List<Milestone> {
        val s = scoreFor(seed); val done = s.milestones.toMutableSet(); val out = ArrayList<Milestone>()
        MILESTONES.forEachIndexed { i, m ->
            val id = i + 1
            if (id !in done && m.completed(defeated, done, mapId)) { s.milestones += id; done += id; out += m }
        }
        if (out.isNotEmpty()) save()
        return out
    }

    fun addBonus(s: Score, bonus: Int) { s.bonuses += bonus; save() }
    fun removeBonus(s: Score, at: Int) { if (at in s.bonuses.indices) { s.bonuses.removeAt(at); save() } }

    /** TourneyTracker.convertScoresToLines, the export text. */
    fun lines(): List<String> = scores.mapIndexed { i, s ->
        val last = s.milestones.lastOrNull()
        var heading = "Seed #${i + 1} - Last milestone: ${last?.let { MILESTONES.getOrNull(it - 1)?.name } ?: "None"}."
        s.milestones.filter { it in FULL_CLEAR_IDS && it != last }.forEach { heading += " ${MILESTONES[it - 1].name}." }
        listOfNotNull(heading, s.bonuses.takeIf { it.isNotEmpty() }?.let { b -> "Bonuses: " + b.joinToString(", ") { BONUSES[it - 1].first } })
    }.flatten()

    fun clear() { scores.clear(); save() }
}

@Composable
fun TourneyDialog(t: TourneyTracker, currentSeed: String, onClose: () -> Unit) {
    val v = t.version
    var index by remember(v) { mutableStateOf(t.scores.indexOfFirst { it.seed == currentSeed }.coerceAtLeast(0)) }
    var bonusMode by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("TOURNEY TRACKER", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            GearToggle("Tourney tracker on", TrackerOptions.tourneyTracker) { TrackerOptions.tourneyTracker = it; TrackerOptions.save() }
            Spacer(Modifier.height(4.dp))
            val s = t.scores.getOrNull(index)
            PixText("Cumulative score: ${t.cumulative()}", 9, Pc.Gold)
            if (s == null) PixText("No seeds scored yet.", 8, Pc.Dim)
            else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PixText("<", 12, Pc.Text, Modifier.clickable { index = (index - 1 + t.scores.size) % t.scores.size }.padding(horizontal = 10.dp))
                    PixText("Seed ${index + 1}/${t.scores.size}: ${t.points(s)} points", 9, Pc.Text, Modifier.weight(1f), TextAlign.Center)
                    PixText(">", 12, Pc.Text, Modifier.clickable { index = (index + 1) % t.scores.size }.padding(horizontal = 10.dp))
                }
                Spacer(Modifier.height(4.dp))
                PixText("Milestones", 8, Pc.Gold)
                if (s.milestones.isEmpty()) PixText("None yet.", 8, Pc.Dim)
                s.milestones.forEach { id -> val m = TourneyTracker.MILESTONES[id - 1]; Row { PixText(m.name, 8, Pc.Text, Modifier.weight(1f)); PixText("+${m.points}", 8, Pc.Positive) } }
                Spacer(Modifier.height(4.dp))
                PixText(when (bonusMode) { "add" -> "Select One to Add:"; "remove" -> "Select One to Remove:"; else -> "Bonuses" }, 8, Pc.Gold)
                when (bonusMode) {
                    "add" -> TourneyTracker.BONUSES.forEachIndexed { i, (name, pts) -> PixText("$name  +$pts", 8, Pc.Text, Modifier.clickable { t.addBonus(s, i + 1); bonusMode = "" }.padding(3.dp)) }
                    "remove" -> s.bonuses.forEachIndexed { i, b -> PixText(TourneyTracker.BONUSES[b - 1].first, 8, Pc.Negative, Modifier.clickable { t.removeBonus(s, i); bonusMode = "" }.padding(3.dp)) }
                    else -> {
                        if (s.bonuses.isEmpty()) PixText("None.", 8, Pc.Dim)
                        s.bonuses.forEach { b -> val (name, pts) = TourneyTracker.BONUSES[b - 1]; Row { PixText(name, 8, Pc.Text, Modifier.weight(1f)); PixText("+$pts", 8, Pc.Positive) } }
                        Row { com.ironmonone.app.gen3.Gen3Button("ADD BONUS") { bonusMode = "add" }; Spacer(Modifier.width(6.dp)); if (s.bonuses.isNotEmpty()) com.ironmonone.app.gen3.Gen3Button("REMOVE BONUS") { bonusMode = "remove" } }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row { com.ironmonone.app.gen3.Gen3Button("EXPORT SCORES") { export = !export }; Spacer(Modifier.width(6.dp)); if (!confirmClear) com.ironmonone.app.gen3.Gen3Button("CLEAR DATA") { confirmClear = true } else com.ironmonone.app.gen3.Gen3Button("CONFIRM CLEAR", accent = true) { t.clear(); confirmClear = false } }
            if (export) { Spacer(Modifier.height(4.dp)); t.lines().forEach { PixText(it, 7, Pc.Dim, wrap = true) }; if (t.lines().isEmpty()) PixText("Nothing to export.", 7, Pc.Dim) }
        }
    }
}
