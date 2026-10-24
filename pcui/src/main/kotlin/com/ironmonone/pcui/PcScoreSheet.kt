package com.ironmonone.pcui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.Pc
import com.ironmonone.app.ScoreSheet
import com.ironmonone.app.referenceWidth
import com.ironmonone.app.rp

/**
 * GameOverScreen's "Grade my notes": besteon's StatMarkingScoreSheet (Ironmon-Tracker, MIT) on the panel, graded by the
 * app's ScoreSheet (the same ranges, margin and ability exceptions). The summary (Great marks, Poor marks, Total,
 * Percentage) with the letter in its circle, the column heads, then a row a marked species, worst first, two a page:
 * the icon, each stat's mark with its tick or cross over the base stat.
 */
object PcScoreSheet {
    private const val COL2 = 53
    private const val ROW_X = 6
    private const val ROW_Y = 73
    private const val ROW_W = 138
    private const val ROW_H = 32
    private const val CELL_W = 21
    private val COLS_X = listOf(1, 35, 56, 77, 98, 119)
    const val PER_PAGE = 2

    fun pages(r: ScoreSheet.Result) = maxOf(1, (r.rows.size + PER_PAGE - 1) / PER_PAGE)

    @Composable
    private fun right(v: String, y: Int, col: Color) = BText(v, 6 + COL2 + (4 - v.length) * 5, y, col)

    @Composable
    fun View(r: ScoreSheet.Result, page: Int, spriteFor: (Int) -> ImageBitmap?, onPage: (Int) -> Unit, onBack: () -> Unit) {
        val text = Pc.Text; val border = Pc.Border; val fill = Pc.Ground
        val has = r.total > 0
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 15, 140, 140, border, fill)
            BText("STAT MARKING SCORE SHEET", 5, 3, Pc.Header)
            BText("Great marks:", 6, 16, text); right(if (has) "${r.great}" else PcBesteon.BLANK, 16, Pc.Positive)
            BPix(BImg.SMALL_CHECK, 6 + COL2 + 25, 20, listOf(Pc.Positive))
            BText("Poor marks:", 6, 26, text); right(if (has) "${r.poor}" else PcBesteon.BLANK, 26, Pc.Negative)
            BPix(BImg.SMALL_X, 6 + COL2 + 25, 29, listOf(Pc.Negative))
            BText("Total:", 6, 36, text); right(if (has) "${r.total}" else PcBesteon.BLANK, 36, text)
            BText("Percentage:", 6, 46, text)
            BText(r.percentage, 6 + COL2 + 19 - referenceWidth(r.percentage), 46, text)
            BText("%", 6 + COL2 + 22, 46, text)
            if (has) {
                val full = r.percentage == "100"
                val gx = 113 - (if (full) 3 else 0); val gy = 31
                val img = when (r.letter) { 'A' -> BImg.GRADE_A; 'B' -> BImg.GRADE_B; 'C' -> BImg.GRADE_C; else -> BImg.GRADE_D }
                val hi = Pc.Gold
                Canvas(Modifier.offset((gx - 9 + (if (full) 3 else 0)).rp, (gy - 9).rp).size(32.rp, 32.rp)) {
                    val u = size.width / 32f
                    drawOval(hi, Offset(0.5f * u, 0.5f * u), Size(30 * u, 30 * u), style = Stroke(2 * u))
                }
                BPix(img, gx, gy, listOf(hi))
                if (full) BPix(BImg.PLUS_SIGN, gx + 14 + 2, gy + 5, listOf(hi))
            }
            if (!has) {
                val lines = wrapWords("Take notes while playing by marking stats on opposing Pokémon. (The Speed stat is excluded from grading.)", 29)
                lines.forEachIndexed { i, l -> BText(l, 5, ROW_Y + 11 + i * 10, text, w = 141, align = TextAlign.Center) }
            } else {
                // The column heads, with the dotted dividers down to the rows.
                listOf("HP", "ATK", "DEF", "SPA", "SPD").forEachIndexed { i, s ->
                    val x = ROW_X + COLS_X[i + 1]; val y = ROW_Y - 11 - 3
                    for (dy in 1 until ROW_H step 2) Box(Modifier.offset((x - 1).rp, (y + 2 + dy).rp).size(1.rp, 1.rp).background(border))
                    BText(s, x, y + 2, text, w = CELL_W, align = TextAlign.Center)
                }
                val p = page.coerceIn(1, pages(r))
                r.rows.drop((p - 1) * PER_PAGE).take(PER_PAGE).forEachIndexed { k, row ->
                    val x = ROW_X; val y = ROW_Y + k * ROW_H
                    BRect(x - 1, y - 1, ROW_W + 2, ROW_H - 1, border, Color.Transparent)
                    COLS_X.drop(1).forEach { cx -> for (dy in 1 until ROW_H - 1 step 2) Box(Modifier.offset((x + cx - 1).rp, (y + dy).rp).size(1.rp, 1.rp).background(border)) }
                    for (dx in COLS_X[1] + 1 until ROW_W step 2) Box(Modifier.offset((x + dx).rp, (y + 15).rp).size(1.rp, 1.rp).background(border))
                    spriteFor(row.species)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset((x + COLS_X[0]).rp, (y - 4).rp).size(ROW_H.rp)) }
                    row.cells.forEachIndexed { i, cell ->
                        val cx = x + COLS_X[i + 1]; val cy = y + ROW_H / 2 + 2
                        cell.mark?.let { m ->
                            BText(m, cx, cy - 16, PcMarks.colorOf(m, text), w = CELL_W, align = TextAlign.Center)
                            when (cell.accurate) {
                                true -> BPix(BImg.SMALL_CHECK, cx + CELL_W - 7, cy - 17 + 4, listOf(Pc.Positive))
                                false -> BPix(BImg.SMALL_X, cx + CELL_W - 7, cy - 17 + 3, listOf(Pc.Negative))
                                null -> {}
                            }
                        }
                        BText("${cell.base}", cx, cy, text, w = CELL_W, align = TextAlign.Center)
                        if (cell.exception) Box(Modifier.offset((cx + 1).rp, (cy - 3).rp)) { com.ironmonone.app.PcStageChevrons(1) }
                    }
                }
                if (pages(r) > 1) {
                    BImage(PcBesteon.LEFT_ARROW, 44, 141, text)
                    BText("$p/${pages(r)}", 56, 140, text, w = 40, align = TextAlign.Center)
                    BImage(PcBesteon.RIGHT_ARROW, 98, 141, text)
                    BHit(42, 140, 13, 11) { onPage(if (p <= 1) pages(r) else p - 1) }
                    BHit(96, 140, 13, 11) { onPage(if (p >= pages(r)) 1 else p + 1) }
                }
            }
            BImage(PcBesteon.BACK_ARROW, 129, 142, text)
            BHit(127, 142, 16, 12, onBack)
        }
    }
}
