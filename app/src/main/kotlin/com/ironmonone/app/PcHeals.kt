package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * "Track PC Heals" (off by default in the reference): how many Pokemon Center
 * heals this attempt has left, or has used.
 *
 * Counting down it starts at 10, up at 0; either way 0 to 99. The counter's
 * sheet (PcHealCounter) adds or removes a heal by hand. With automatic counting
 * on (the red heart; off each session, as the reference's toggle starts), a new
 * Pokemon Center heal or rest at home, game statistics 15 and 16, moves it by
 * one (Program.lua:1203). Kept per attempt, so a new run starts fresh.
 */
object PcHeals {
    private val counts = mutableStateMapOf<Int, Int>()
    private val baseline = HashMap<Int, Int>()
    var autoTracking by mutableStateOf(false)
    private var file: File? = null

    fun count(attempt: Int): Int = counts[attempt] ?: if (TrackerOptions.pcHealsCountDownward) 10 else 0

    /**
     * The Survival rulesets' Pokemon Center limits (the official rules gist; the Survival
     * Revival README): Survival 10 with a bonus heal at the 8th badge, Survival Revival 5 after
     * the heal badge 1 grants, with a bonus at the 8th badge.
     */
    enum class Limit(val start: Int) { SURVIVAL(10), REVIVAL(5) }

    /** Survival's heals for Kanto after the Johto Elite Four, in a Johto game (the rules' "10 Heal Limit"). */
    const val KANTO_HEALS = 7

    /** The limit a settings file's name asks for, the way the reference reads a profile's keywords. */
    fun limitFor(settingsName: String?): Limit? = when {
        settingsName == null -> null
        settingsName.contains("Survival Revival", true) || settingsName.contains("SurvivalRevival", true) -> Limit.REVIVAL
        settingsName.contains("Survival", true) -> Limit.SURVIVAL
        else -> null
    }

    /**
     * The limit of the run in play, from prep/lastrun.txt beside this counter's file: by the run's mode (RunModeName),
     * which reads the settings file's sidecar too, not by its name alone (2026-10-01, rules check).
     */
    fun limitForLastRun(): Limit? {
        val prep = file?.parentFile ?: return null
        val lines = runCatching { File(prep, "lastrun.txt").readLines() }.getOrNull() ?: return null
        val name = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return limitFor(RunModeName.ofPrep(prep, lines.getOrNull(0), name))
    }

    private val armed = HashSet<Int>()
    private val bonusGiven = HashSet<Int>()

    /**
     * A Survival run's first sight of its attempt switches the counter on, counting down from
     * its limit (Blake, 2026-09-29: an official mode adds what its rules need, and the player
     * keeps control; the gear switches it off). The heart stays the player's, as in the
     * reference: heals before the first trainer that is not the rival are free under the rules.
     */
    fun arm(attempt: Int, limit: Limit?, badges: Int = 0) {
        if (limit == null || attempt in armed) return
        armed += attempt
        // A run already past the 8th badge when first armed (the app updated mid-run) had its
        // bonus by hand or not at all: it is not added again.
        if (badges >= 8) bonusGiven += attempt
        TrackerOptions.trackPcHeals = true
        TrackerOptions.pcHealsCountDownward = true
        TrackerOptions.save()
        if (counts[attempt] == null) counts[attempt] = limit.start
        save()
    }

    /**
     * A new run took attempt [n]. Attempts count per game, so an earlier run of another game can
     * have left a count, an armed mark or a bonus under the same number: they go, and the new run
     * starts fresh. Called by PrepStore.installRun (freshAttempt), which every new-run path goes through.
     */
    fun forgetAttempt(n: Int) {
        val had = counts.remove(n) != null
        baseline.remove(n)
        if (had or armed.remove(n) or bonusGiven.remove(n)) save()
    }

    /** [limitForLastRun], read again only when prep/lastrun.txt changes: the DS side asks on every read. */
    private var limitCache: Pair<Long, Limit?>? = null
    fun limitForLastRunCached(): Limit? {
        val f = file?.let { File(it.parentFile, "lastrun.txt") } ?: return null
        val stamp = f.lastModified()
        limitCache?.takeIf { it.first == stamp }?.let { return it.second }
        return limitForLastRun().also { limitCache = stamp to it }
    }

    /**
     * A Survival run on DS (the modes audit): the DS tracker's own counter ("Show Pokecenter
     * heals", counted by hand in the reference) shows from the run's first read at the rules'
     * limit, and the 8th badge adds the bonus heal. Both once per run, kept with the run's DS
     * values (StatMarks.armDsSurvival); the player keeps control: the gear switches it off and
     * the arrows step it. Returns whether the count changed.
     */
    fun observeDsSurvival(marks: StatMarks, badges: Int, limit: Limit?, leagueBeaten: Boolean = false): Boolean {
        if (limit == null) return false
        var changed = false
        if (marks.armDsSurvival(limit, badges, leagueBeaten)) {
            TrackerOptions.dsPokecenterHeals = true
            TrackerOptions.save()
            changed = true
        }
        if (marks.dsSurvivalBadges(badges)) changed = true
        // HeartGold and SoulSilver: Survival's 7 heals for Kanto, once the Johto League is beaten (Survival only).
        if (limit == Limit.SURVIVAL && marks.dsSurvivalKanto(leagueBeaten)) changed = true
        return changed
    }

    /**
     * The bonus heal at the 8th badge, once per armed Survival attempt, whether the heart counts
     * heals or the player does: the badge is a rule event the tracker sees for certain, and a
     * Game Boy game has no heal statistic for the heart to count. The DS counter does the same
     * (observeDsSurvival). A counter the player switched off is left alone.
     */
    fun observeBadges(attempt: Int, badges: Int, limit: Limit?) {
        if (limit == null || badges < 8 || attempt !in armed || attempt in bonusGiven || !TrackerOptions.trackPcHeals) return
        bonusGiven += attempt
        add(attempt, if (TrackerOptions.pcHealsCountDownward) +1 else -1)
    }

    fun add(attempt: Int, delta: Int) {
        counts[attempt] = (count(attempt) + delta).coerceIn(0, 99)
        save()
    }

    /**
     * The game's heal statistics, polled. The first reading this session is the
     * baseline, so reopening the app never counts old heals; a zero after a real
     * value is an unreadable save, not a reset, and is ignored.
     */
    fun observe(attempt: Int, stat: Int) {
        val prev = baseline[attempt]
        if (prev != null && prev > 0 && stat == 0) return
        baseline[attempt] = stat
        if (prev == null || stat <= prev) return
        if (TrackerOptions.trackPcHeals && autoTracking) {
            add(attempt, if (TrackerOptions.pcHealsCountDownward) -1 else +1)
        }
    }

    /**
     * Whether the run in play can be counted automatically: a Game Boy game's save has no heal statistic for the
     * heart to read (observeBadges), so its sheet leaves the switch out. The run is prep/lastrun.txt's game, as in
     * [limitForLastRun]; the counter shows for runs only.
     */
    fun lastRunCountsItself(): Boolean {
        val prep = file?.parentFile ?: return true
        val id = runCatching { File(prep, "lastrun.txt").readLines().firstOrNull() }.getOrNull() ?: return true
        return com.ironmonone.core.RomKind.byId(id.trim())?.platform != com.ironmonone.core.Platform.GBC
    }

    /** Utils.getCenterHealColor. */
    fun color(n: Int): Color =
        if (TrackerOptions.pcHealsCountDownward) when { n < 1 -> Pc.Negative; n < 6 -> Pc.Gold; else -> Pc.Text }
        else when { n < 5 -> Pc.Text; n < 10 -> Pc.Gold; else -> Pc.Negative }

    fun load(f: File) {
        file = f
        counts.clear(); armed.clear(); bonusGiven.clear()
        runCatching {
            if (f.exists()) f.forEachLine { line ->
                if (line.startsWith("armed:")) { line.substringAfter(':').trim().toIntOrNull()?.let { armed += it }; return@forEachLine }
                if (line.startsWith("bonus:")) { line.substringAfter(':').trim().toIntOrNull()?.let { bonusGiven += it }; return@forEachLine }
                val parts = line.split('=')
                if (parts.size == 2) {
                    val a = parts[0].trim().toIntOrNull(); val n = parts[1].trim().toIntOrNull()
                    if (a != null && n != null) counts[a] = n.coerceIn(0, 99)
                }
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            SafeWrite.text(f, counts.entries.joinToString("") { "${it.key}=${it.value}\n" } +
                armed.joinToString("") { "armed:$it\n" } + bonusGiven.joinToString("") { "bonus:$it\n" })
        }
    }
}

/** Constants.PixelImages.HEART: 1 outline, 2 fill, 3 shine. */
private val HEART = listOf(
    "00110001100", "01221012210", "12332122221", "12322222221", "12222222221", "01222222210",
    "00122222100", "00012221000", "00001210000", "00000100000", "00000000000",
)

/** What the PC heal counter and its sheet say. */
internal object PcHealsCopy {
    const val TITLE = "PC HEALS"
    const val ADD = "Add a heal"
    const val REMOVE = "Remove a heal"
    const val AUTO = "Count heals automatically"
    const val CHANGE = "Change"

    fun count(n: Int, down: Boolean = TrackerOptions.pcHealsCountDownward) = if (down) "Heals left: $n" else "Heals used: $n"

    /** What a screen reader says for the counter on the card. */
    fun spoken(n: Int, down: Boolean = TrackerOptions.pcHealsCountDownward) = "PC " + count(n, down).replaceFirstChar { it.lowercase() }
}

/**
 * The PC heal counter in the heals box (TrackerScreen.lua:1274): the heart on top, an outline while heals are counted
 * by hand and red while they are counted automatically, and the count right-aligned, with a small green + and red -
 * beside it. All of it is one 44dp target that opens [PcHealsSheet] (rc32 audit P2 #42, #43): the + and - were 5 by
 * 9dp glyphs stacked with no gap, so a tap a few dp off moved the count the wrong way, and one stray tap on the heart
 * switched automatic counting with nothing said. The heart is a mark of the state now, not a switch.
 */
@Composable
internal fun PcHealCounter(attempt: Int) {
    val n = PcHeals.count(attempt)
    var sheet by remember { mutableStateOf(false) }
    Column(
        Modifier.sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)
            .clickable(role = Role.Button, onClickLabel = PcHealsCopy.CHANGE) { sheet = true }
            .semantics { contentDescription = PcHealsCopy.spoken(n) },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.End) {
            val on = PcHeals.autoTracking
            PcPixelImageColors(
                HEART,
                if (on) mapOf('1' to Color(0xFFF04037), '2' to Color(0xFFFF0000), '3' to Color(0xFFFFFFFF))
                else mapOf('1' to Pc.Text),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    PixText("+", PcRef.FONT - 3, Pc.Positive)
                    PixText("-", PcRef.FONT - 3, Pc.Negative)
                }
                Spacer(Modifier.width(2.rp))
                PixText("$n", PcRef.FONT, PcHeals.color(n))
            }
        }
    }
    if (sheet) PcHealsSheet(attempt) { sheet = false }
}

/**
 * The PC heal counter's sheet: the count, 48dp Add and Remove, and the automatic count as a labelled switch, left out
 * on a Game Boy game, whose save has no heal statistic to count (PcHeals.lastRunCountsItself).
 */
@Composable
private fun PcHealsSheet(attempt: Int, onClose: () -> Unit) {
    val countsItself = remember { PcHeals.lastRunCountsItself() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText(PcHealsCopy.TITLE, 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            val n = PcHeals.count(attempt)
            DialogText(PcHealsCopy.count(n), 15, PcHeals.color(n), Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
            GearButton(PcHealsCopy.ADD) { PcHeals.add(attempt, +1) }
            GearButton(PcHealsCopy.REMOVE) { PcHeals.add(attempt, -1) }
            if (countsItself) GearToggle(PcHealsCopy.AUTO, PcHeals.autoTracking) { PcHeals.autoTracking = it }
        }
    }
}
