package com.ironmonone.pcui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.app.Pc
import com.ironmonone.app.PcRef
import com.ironmonone.app.PixText
import com.ironmonone.app.rp
import com.ironmonone.tracker.TrackedMon

/** What "Save this attempt" came to: the reference's clickedStatus (GameOverScreen.SaveGameFiles). */
enum class PcSaveStatus { NOT_CLICKED, SUCCESS, FAILED }

/** The ending the panel shows, latched by the session as the reference latches it (GameOverScreen.isDisplayed). */
data class PcOver(
    val won: Boolean,
    /** The team as it was when the run ended, fallen members and all. */
    val team: List<TrackedMon>,
    /** Which of the team the icon shows (PokemonIcon.teamIndex); a click moves to the next one. */
    val teamIndex: Int = 0,
    /** The announcer's line (Resources.GameOverScreenQuotes), drawn by the session so it stays until the icon is clicked. */
    val quote: String = "",
    /** Retry the battle is offered: BizHawk holds the state the battle began from, and the run was not won. */
    val canRetry: Boolean = false,
    /** Retry was clicked once: the button says "Are you sure?" until the second click. */
    val retryConfirm: Boolean = false,
    val save: PcSaveStatus = PcSaveStatus.NOT_CLICKED,
    /** Inspect the log is offered: the run's log is on disk. */
    val logAvailable: Boolean = true,
    /** Program.GameTimer: the run's play time as Utils.formatTime writes it; null when none was counted. */
    val playTime: String? = null,
    /** GameOverScreen.numDefeatedTrainers >= 2: "View log" and "Prize card" side by side in place of Inspect the log. */
    val prizeOffered: Boolean = false,
)

/** What a click on the card asks the session for. */
enum class PcOverAction { CYCLE_TEAM, CONTINUE, RETRY, SAVE, INSPECT_LOG, GRADE, GRADE_CLOSE, PRIZE }

/**
 * The game over card and the win card (PLAN row 6), drawn as besteon's GameOverScreen.lua: the top box (y 5, 58 tall)
 * with "G A M E O V E R", the attempt, the announcer's line centred on two lines of 30 letters and the team's icon in
 * the top right (a click shows the next member and draws another line); the bottom box with the ICON_BORDER buttons at
 * x 19, 112 by 16, each on its own row: Continue playing (65), Retry the battle (83, then "Are you sure?"), Save this
 * attempt (101, then "Saved in Tracker folder" or "Unable to save"; the attempt is kept in the run's folder), Grade my
 * notes (119), and Inspect the log (137), or "View log" and "Prize card" side by side once two common trainers are beaten.
 * Grade my notes opens StatMarkingScoreSheet on the panel (PcScoreSheet); the Prize card GachaMonOverlay over the game (PcPrize).
 */
@Composable
fun PcGameOverCard(over: PcOver, attempt: Int, spriteFor: (Int) -> ImageBitmap?, onAction: (PcOverAction) -> Unit) {
    val upper = Pc.Ground
    val lowerFill = Pc.LowerGroundX ?: Pc.Ground
    Box(Modifier.fillMaxSize().background(Pc.Page)) {
        // The top box: GameOverScreen.drawScreen's topBox, (5, 5, 140, 58).
        BRect(5, 5, 140, 58, Pc.Border, upper)
        // The team's icon first, so the words may cross it (PokemonIcon, box at x 111, y 2); a click shows the next member.
        val lead = over.team.getOrNull(over.teamIndex.coerceIn(0, maxOf(0, over.team.size - 1)))
        val bmp = lead?.let { spriteFor(it.mon.species) }
        if (bmp != null) {
            val fainted = lead.mon.curHp == 0
            Image(
                bmp, contentDescription = lead.speciesName, filterQuality = FilterQuality.None,
                colorFilter = if (fainted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                modifier = Modifier.offset(111.rp, 2.rp).size(32.rp).then(if (fainted) Modifier.alpha(0.65f) else Modifier),
            )
        }
        if (over.team.isNotEmpty()) BHit(111, 2, 32, 32) { onAction(PcOverAction.CYCLE_TEAM) }
        var y = 7
        BText("G A M E O V E R", 7, y, Pc.Gold)
        y += PcRef.LINESPACING
        BText("Attempt:", 7, y, Pc.Text)
        BText("%,d".format(attempt), 62, y, Pc.Text)
        y += PcRef.LINESPACING
        over.playTime?.let { BText("Play Time:", 7, y, Pc.Text); BText(it, 62, y, Pc.Text) }
        y += PcRef.LINESPACING
        val message = if (over.won) "CONGRATULATIONS!!" else over.quote
        val lines = wrapWords(message, 30).take(2)
        y += 5 * (2 - lines.size)
        for (line in lines) {
            BText(line, 5, y, Pc.Text, w = 141, align = TextAlign.Center)
            y += PcRef.LINESPACING - 1
        }
        // The bottom box: botBox, from the top box's foot to 10 above the panel's edge.
        BRect(5, 63, 140, PcRef.HEIGHT - 58 - 10, Pc.LowerBorder, lowerFill)
        // The buttons at their own rows (Continue 65, Retry 83, Save 101, the log 137); a hidden one leaves its row empty.
        OverButton(PcBesteon.RIGHT_ARROW, "Continue playing", Pc.LowerText, 65) { onAction(PcOverAction.CONTINUE) }
        if (over.canRetry && !over.won) {
            OverButton(com.ironmonone.app.SWORD, if (over.retryConfirm) "Are you sure?" else "Retry the battle",
                if (over.retryConfirm) Pc.Negative else Pc.LowerText, 83) { onAction(PcOverAction.RETRY) }
        }
        val (saveText, saveColor) = when (over.save) {
            PcSaveStatus.NOT_CLICKED -> "Save this attempt" to Pc.LowerText
            PcSaveStatus.SUCCESS -> "Saved in Tracker folder" to Pc.Positive
            PcSaveStatus.FAILED -> "Unable to save" to Pc.Negative
        }
        OverButton(INSTALL_BOX, saveText, saveColor, 101) { if (over.save == PcSaveStatus.NOT_CLICKED) onAction(PcOverAction.SAVE) }
        OverButton(PcBesteon.NOTEPAD, "Grade my notes", Pc.LowerText, 119) { onAction(PcOverAction.GRADE) }
        if (over.prizeOffered) {
            if (over.logAvailable) OverButton(MAGNIFYING_GLASS, "View log", Pc.LowerText, 137, x = 11, w = 60) { onAction(PcOverAction.INSPECT_LOG) }
            OverButton(BImg.GACHAMON_CARD, "Prize card", Pc.Positive, 137, x = 77, w = 62, textColor = Pc.LowerText) { onAction(PcOverAction.PRIZE) }
        } else if (over.logAvailable) OverButton(MAGNIFYING_GLASS, "Inspect the log", Pc.LowerText, 137) { onAction(PcOverAction.INSPECT_LOG) }
    }
}

/** Constants.PixelImages INSTALL_BOX and MAGNIFYING_GLASS (its outline; the fill takes no colour in the reference's button). */
private val INSTALL_BOX = listOf("000010000", "000010000", "000010000", "001010100", "000111000", "000010000", "100000001", "100000001", "111111111")
private val MAGNIFYING_GLASS = listOf(
    "0011100000", "0100010000", "1000001000", "1000001000", "1000001000", "0100010000", "0011101000", "0000000110", "0000000111", "0000000011",
)

/** Drawing.drawButton's ICON_BORDER, (19, y, 112, 16): the icon centred in its first 16 pixels, the words from x + 17. */
@Composable
private fun OverButton(image: List<String>, text: String, color: Color, y: Int, x: Int = 19, w: Int = 112, textColor: Color = color, onClick: () -> Unit) {
    BRect(x, y, w, 16, Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground, onClick)
    val iw = image.maxOf { it.length }; val ih = image.size
    BPix(image, x + maxOf((16 - iw) / 2, 0) + 1, y + maxOf((16 - ih) / 2, 0) + 1, listOf(color))
    BText(text, x + 17, y + 2, textColor)
}

/** Utils.getWordWrapLines: whole words onto lines of at most [width] letters. */
internal fun wrapWords(text: String, width: Int): List<String> {
    val out = ArrayList<String>()
    var line = StringBuilder()
    for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        if (line.isNotEmpty() && line.length + 1 + word.length > width) { out += line.toString(); line = StringBuilder() }
        if (line.isNotEmpty()) line.append(' ')
        line.append(word)
    }
    if (line.isNotEmpty()) out += line.toString()
    return out
}
