package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One member of the team the run ended with, whichever tracker produced it. */
data class GameOverMon(val species: Int, val name: String, val level: Int, val fainted: Boolean, val shiny: Boolean = false)

/**
 * Which PC tracker's end-of-run screen this popup clones. Each was read on
 * 2026-09-07:
 *
 * - [GEN3]: Ironmon-Tracker's GameOverScreen.lua. "G a m e O v e r", the
 *   attempt, a team icon that cycles and re-rolls a Pokemon Stadium announcer
 *   quote, then Continue playing, Retry the battle (with an "Are you sure?"
 *   step), Save this attempt, Grade my notes and Inspect the log.
 * - [GEN12]: the Gen 1 and Gen 2 trackers share one older copy of that screen
 *   (screens/GameOverScreen.lua is byte-identical between them): the same box
 *   without Grade my notes.
 * - [DS]: NDS-Ironmon-Tracker has no game-over screen. Its RunOverScreen shows
 *   a message chosen by HOW the run ended (Shedinja, Imposter, a hundred BST
 *   below you, or the standard pool) with Dismiss and Open log.
 *
 * Every family gets the same row of actions here, because the app can do the
 * same things for each of them; the title, the quote source and the log
 * button's label follow the family's own tracker. "New game" is the app's
 * addition Blake asked for: it rolls a fresh seed and re-randomizes.
 */
enum class GameOverFamily { GEN12, GEN3, DS }

/** What happened when the player asked to keep this attempt: the reference's clickedStatus. */
enum class SaveAttemptStatus { NOT_CLICKED, SUCCESS, FAILED }

@Composable
fun GameOverDialog(
    family: GameOverFamily,
    won: Boolean,
    attempt: Int,
    team: List<GameOverMon>,
    spriteOf: @Composable (GameOverMon) -> ImageBitmap?,
    /** DS only: the cause the tracker read, which picks the message pool. */
    dsCause: com.ironmonone.tracker.nds.NdsRunOver? = null,
    /** False hides Retry: no battle-start state was captured, or the run was won. */
    canRetry: Boolean,
    /** Null hides the log button: no log was kept for this run. */
    onInspectLog: (() -> Unit)?,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    /** Returns whether the attempt was saved; the button reports it in place, as the reference does. */
    onSaveAttempt: () -> Boolean,
    onNewGame: () -> Unit,
    /** GameOverScreen.NotesGrade: the Stat Marking Score Sheet. Null hides it (no marks to grade). */
    onGrade: (() -> Unit)? = null,
) {
    var teamIndex by remember { mutableIntStateOf(0) }
    val quotes = when (family) {
        GameOverFamily.DS -> ndsRunOverLines(dsCause ?: com.ironmonone.tracker.nds.NdsRunOver.STANDARD)
        else -> PcGameOverQuotes
    }
    var quoteIndex by remember { mutableIntStateOf(((attempt % quotes.size) + quotes.size) % quotes.size) }
    var retryConfirm by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf(SaveAttemptStatus.NOT_CLICKED) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onContinue) {
        Column(Modifier.width(300.dp).background(Pc.Ground).border(1.dp, Pc.Border)) {
            // Top box: title, attempt, the team icon, the quote.
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        PixText(
                            when {
                                family == GameOverFamily.DS && won -> "R u n  W o n"
                                family == GameOverFamily.DS -> "R u n  O v e r"
                                else -> "G a m e O v e r"
                            }, 11, Pc.Gold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            PixText("Attempt:", 8, Pc.Text, Modifier.width(66.dp))
                            PixText("$attempt", 8, Pc.Text)
                        }
                    }
                    val shown = team.getOrNull(teamIndex)
                    Box(
                        Modifier.size(36.dp).clickable(enabled = team.isNotEmpty()) {
                            // nextTeamPokemon + randomizeAnnouncerQuote
                            teamIndex = (teamIndex + 1) % team.size
                            quoteIndex = (quoteIndex + 7) % quotes.size
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = shown?.let { spriteOf(it) }
                        if (bmp != null) Image(bmp, contentDescription = shown.name, modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
                    }
                    // Blake, 2026-09-07: an X in the top right closes the popup; the run
                    // stays as it is, the same as Continue playing.
                    PixText("X", 9, Pc.Dim, Modifier.padding(start = 6.dp).clickable { onContinue() }.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    PixText(
                        if (won && family != GameOverFamily.DS) "CONGRATULATIONS!!" else quotes[quoteIndex],
                        8, if (won) Pc.Positive else Pc.Text, align = TextAlign.Center, wrap = true,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            // Bottom box: the actions, one per row with the reference's icon at the left.
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameOverAction(Glyph.ARROW, "Continue playing", onClick = onContinue)
                if (canRetry && !won) {
                    GameOverAction(
                        Glyph.SWORD,
                        if (retryConfirm) "Are you sure?" else "Retry the battle",
                        color = if (retryConfirm) Pc.Negative else Pc.Text,
                        onClick = { if (retryConfirm) onRetry() else retryConfirm = true },
                    )
                }
                GameOverAction(
                    Glyph.INSTALL,
                    when (saveStatus) {
                        SaveAttemptStatus.NOT_CLICKED -> "Save this attempt"
                        SaveAttemptStatus.SUCCESS -> "Saved to the attempts folder"
                        SaveAttemptStatus.FAILED -> "Unable to save"
                    },
                    color = when (saveStatus) {
                        SaveAttemptStatus.NOT_CLICKED -> Pc.Text
                        SaveAttemptStatus.SUCCESS -> Pc.Positive
                        SaveAttemptStatus.FAILED -> Pc.Negative
                    },
                    onClick = { if (saveStatus == SaveAttemptStatus.NOT_CLICKED) saveStatus = if (onSaveAttempt()) SaveAttemptStatus.SUCCESS else SaveAttemptStatus.FAILED },
                )
                if (onGrade != null) GameOverAction(Glyph.PLUS, "Grade my notes", onClick = onGrade)
                if (onInspectLog != null) {
                    GameOverAction(Glyph.MAGNIFIER, if (family == GameOverFamily.DS) "Open the log" else "Inspect the log", onClick = onInspectLog)
                }
                GameOverAction(Glyph.PLUS, "New game (new seed)", color = Pc.Gold, onClick = onNewGame)
            }
        }
    }
}

private enum class Glyph { ARROW, SWORD, INSTALL, MAGNIFIER, PLUS }

/** The reference's ICON_BORDER button: a bordered row, the pixel icon at the left, the label after it. */
@Composable
private fun GameOverAction(glyph: Glyph, label: String, color: Color = Pc.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val w = size.width; val h = size.height; val s = 2f
            when (glyph) {
                Glyph.ARROW -> {
                    drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), s)
                    drawLine(color, Offset(w * 0.55f, h * 0.15f), Offset(w, h / 2), s)
                    drawLine(color, Offset(w * 0.55f, h * 0.85f), Offset(w, h / 2), s)
                }
                Glyph.SWORD -> {
                    drawLine(color, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.9f, h * 0.1f), s)
                    drawLine(color, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.45f, h * 0.85f), s)
                    drawLine(color, Offset(w * 0.05f, h * 0.95f), Offset(w * 0.25f, h * 0.75f), s)
                }
                Glyph.INSTALL -> {
                    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, h * 0.6f), s)
                    drawLine(color, Offset(w * 0.25f, h * 0.35f), Offset(w / 2, h * 0.6f), s)
                    drawLine(color, Offset(w * 0.75f, h * 0.35f), Offset(w / 2, h * 0.6f), s)
                    drawLine(color, Offset(0f, h * 0.7f), Offset(0f, h), s)
                    drawLine(color, Offset(0f, h), Offset(w, h), s)
                    drawLine(color, Offset(w, h * 0.7f), Offset(w, h), s)
                }
                Glyph.MAGNIFIER -> {
                    drawCircle(color, radius = w * 0.3f, center = Offset(w * 0.4f, h * 0.4f), style = Stroke(s))
                    drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w, h), s)
                }
                Glyph.PLUS -> {
                    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, h), s)
                    drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), s)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        PixText(label, 8, color)
    }
}
