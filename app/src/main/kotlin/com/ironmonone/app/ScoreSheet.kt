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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.BaseStats

/**
 * StatMarkingScoreSheet.lua: every species you marked this run, graded
 * against its real base stats. A mark is right when the stat sits in its
 * range with a 25-point margin (+ is 115 to 255, -- is 11 to 70, = is 70
 * to 115); Speed is never graded. Huge Power and Pure Power double the
 * Attack it is judged on, Hustle boosts it by half, Thick Fat lets a doubled
 * Sp. Def count too. A right mark is a point, a wrong one costs ten, and the
 * list runs worst first. The percentage of right marks gives the letter.
 */
object ScoreSheet {
    class Cell(val stat: String, val mark: String?, val base: Int, val accurate: Boolean?, val exception: Boolean)
    class Row(val species: Int, val name: String, val cells: List<Cell>, val score: Int)
    class Result(val rows: List<Row>, val great: Int, val poor: Int, val total: Int, val percentage: String, val letter: Char)

    private const val MARGIN = 25
    private val RANGES = mapOf("+" to (115 to 255), "--" to (11 to 70), "=" to (70 to 115))
    private val GRADED = listOf(0, 1, 2, 3, 4)   // HP, ATK, DEF, SPA, SPD; Speed excluded

    /** checkAbilityException: which of the three special cases applies, if any. */
    fun exception(statIndex: Int, abilityId: Int): String? = when {
        statIndex == 1 && (abilityId == 37 || abilityId == 74) -> "HugePower"
        statIndex == 1 && abilityId == 55 -> "Hustle"
        statIndex == 4 && abilityId == 47 -> "ThickFat"
        else -> null
    }

    /** isMarkingAccurate, with the margin of error and the ability exceptions. */
    fun accurate(mark: String, baseStat: Int, statIndex: Int, abilityId: Int): Boolean {
        val range = RANGES[mark] ?: return false
        val min = range.first - MARGIN; val max = range.second + MARGIN
        var base = baseStat.toDouble(); var alt = false
        when (exception(statIndex, abilityId)) {
            "HugePower" -> base = minOf(base * 2, 255.0)
            "Hustle" -> base = minOf(base * 1.5, 255.0)
            "ThickFat" -> { val a = minOf(base * 2, 255.0); alt = a >= min && a <= max }
        }
        return (base >= min && base <= max) || alt
    }

    fun letter(percentile: Double): Char = when {
        percentile >= 90 -> 'A'; percentile >= 80 -> 'B'; percentile >= 70 -> 'C'; else -> 'D'
    }

    fun build(marks: StatMarks, baseOf: (Int) -> BaseStats?, speciesName: (Int) -> String): Result {
        var great = 0; var poor = 0; var total = 0
        val rows = ArrayList<Row>()
        for (species in marks.markedSpecies().sorted()) {
            val m = marks.of(species)
            if (GRADED.none { m.getOrElse(it) { 0 } > 0 }) continue
            val base = baseOf(species)
            val stats = listOf(base?.hp ?: 0, base?.atk ?: 0, base?.def ?: 0, base?.spAtk ?: 0, base?.spDef ?: 0)
            val ability = base?.ability1 ?: 0
            var score = 0
            val cells = GRADED.map { i ->
                val mark = m.getOrElse(i) { 0 }.takeIf { it > 0 }?.let { StatMarks.symbol(it) }
                val acc = mark?.let { accurate(it, stats[i], i, ability) }
                if (acc != null) { total++; if (acc) { score++; great++ } else { score -= 10; poor++ } }
                Cell(StatMarks.STAT_NAMES[i], mark, stats[i], acc, exception(i, ability) != null)
            }
            rows += Row(species, speciesName(species), cells, score)
        }
        rows.sortWith(compareBy<Row> { it.score }.thenBy { it.species })
        val percentage = if (total == 0) "---" else if (great == total) "100" else String.format("%.1f", great * 100.0 / total)
        return Result(rows, great, poor, total, percentage, letter(percentage.toDoubleOrNull() ?: 0.0))
    }
}

@Composable
fun ScoreSheetDialog(r: ScoreSheet.Result, spriteFor: (Int) -> ImageBitmap?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(320.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("STAT MARKING SCORE SHEET", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            if (r.total == 0) {
                PixText("Take notes while playing by marking stats on opposing Pokemon. (The Speed stat is excluded from grading.)", 8, Pc.Text, wrap = true)
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Row { PixText("Great marks:", 8, Pc.Text, Modifier.width(90.dp)); PixText("${r.great}", 8, Pc.Positive) }
                    Row { PixText("Poor marks:", 8, Pc.Text, Modifier.width(90.dp)); PixText("${r.poor}", 8, Pc.Negative) }
                    Row { PixText("Total:", 8, Pc.Text, Modifier.width(90.dp)); PixText("${r.total}", 8, Pc.Text) }
                    Row { PixText("Percentage:", 8, Pc.Text, Modifier.width(90.dp)); PixText(r.percentage + "%", 8, Pc.Gold) }
                }
                PixText(r.letter.toString(), 28, if (r.letter == 'A') Pc.Positive else if (r.letter == 'D') Pc.Negative else Pc.Gold, Modifier.width(48.dp), TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(40.dp))
                listOf("HP", "ATK", "DEF", "SPA", "SPD").forEach { PixText(it, 7, Pc.Dim, Modifier.weight(1f), TextAlign.Center) }
            }
            r.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { PcSprite(spriteFor(row.species)) }
                    row.cells.forEach { c ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val mark = c.mark ?: " "
                            val tick = when (c.accurate) { true -> " \u2713"; false -> " \u2717"; null -> "" }
                            PixText(mark + tick, 8, when (c.accurate) { true -> Pc.Positive; false -> Pc.Negative; null -> Pc.Text })
                            PixText((if (c.exception) "^" else "") + "${c.base}", 7, Pc.Dim)
                        }
                    }
                }
            }
        }
    }
}
