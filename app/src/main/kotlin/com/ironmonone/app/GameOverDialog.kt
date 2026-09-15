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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** The loss colour: the header band, the frame and the title. */
private val LossRed = Color(0xFFE0483C)

/**
 * The end-of-run popup, redrawn 2026-09-15 (Blake: "so ugly and the text is
 * all too small", "lots of wasted space"). The old card was a fixed 300dp box
 * of five full-width rows, taller than a portrait game picture, so FitInside
 * shrank all of it to about 60% and the 8-unit text landed near 5. Now the card
 * takes the picture's width and lays the actions two to a row, so it fits the
 * picture at full size: a header band (red for a loss, gold for a win) with the
 * lead's sprite, the title in the app's pixel face, the attempt and the rest of
 * the team; the quote at 13; tiles at 12 with Continue as the filled primary.
 * What it says and does is unchanged.
 */
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
    /** Where the game picture is on screen. The popup covers it; null centres the card on the window. */
    gameFrame: androidx.compose.ui.geometry.Rect? = null,
) {
    var teamIndex by remember { mutableIntStateOf(0) }
    val quotes = when (family) {
        GameOverFamily.DS -> ndsRunOverLines(dsCause ?: com.ironmonone.tracker.nds.NdsRunOver.STANDARD)
        else -> PcGameOverQuotes
    }
    var quoteIndex by remember { mutableIntStateOf(((attempt % quotes.size) + quotes.size) % quotes.size) }
    var retryConfirm by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf(SaveAttemptStatus.NOT_CLICKED) }
    val accent = if (won) Pc.Gold else LossRed
    // nextTeamPokemon + randomizeAnnouncerQuote: a tap on the team shows the next
    // Pokemon (or that one) and re-rolls the quote.
    fun pick(i: Int) {
        if (team.isEmpty()) return
        teamIndex = i % team.size
        quoteIndex = (quoteIndex + 7) % quotes.size
    }
    val actions = buildList {
        add(TileSpec(Glyph.ARROW, "Continue playing", Tone.PRIMARY, onContinue))
        if (canRetry && !won) add(
            TileSpec(Glyph.SWORD, if (retryConfirm) "Are you sure?" else "Retry the battle",
                if (retryConfirm) Tone.DANGER else Tone.PLAIN) { if (retryConfirm) onRetry() else retryConfirm = true },
        )
        add(
            TileSpec(
                Glyph.INSTALL,
                when (saveStatus) {
                    SaveAttemptStatus.NOT_CLICKED -> "Save this attempt"
                    SaveAttemptStatus.SUCCESS -> "Saved to the attempts folder"
                    SaveAttemptStatus.FAILED -> "Unable to save"
                },
                when (saveStatus) {
                    SaveAttemptStatus.NOT_CLICKED -> Tone.PLAIN
                    SaveAttemptStatus.SUCCESS -> Tone.GOOD
                    SaveAttemptStatus.FAILED -> Tone.DANGER
                },
            ) { if (saveStatus == SaveAttemptStatus.NOT_CLICKED) saveStatus = if (onSaveAttempt()) SaveAttemptStatus.SUCCESS else SaveAttemptStatus.FAILED },
        )
        if (onGrade != null) add(TileSpec(Glyph.STAR, "Grade my notes", Tone.PLAIN, onGrade))
        if (onInspectLog != null) add(TileSpec(Glyph.MAGNIFIER, if (family == GameOverFamily.DS) "Open the log" else "Inspect the log", Tone.PLAIN, onInspectLog))
        add(TileSpec(Glyph.PLUS, "New game (new seed)", Tone.GOLD, onNewGame))
    }
    // Blake, 2026-09-10: "game over is a popup over the game screen". It covers the
    // game picture (dimmed behind the card) and leaves the tracker below it alone.
    // It is its own window, so it draws above the emulator's GL surface. Only the X,
    // Continue or New game close it (Back counts as Continue); a tap outside does nothing.
    androidx.compose.ui.window.Popup(
        popupPositionProvider = remember(gameFrame) { OverGameFrame(gameFrame) },
        onDismissRequest = onContinue,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        FitInside(gameFrame) {
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(2.dp, accent)) {
                // Header: the lead, the title, the attempt and the rest of the team, the X.
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.06f))))
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lead = team.getOrNull(teamIndex)
                    Box(
                        Modifier.size(52.dp).background(Pc.Page.copy(alpha = 0.55f)).border(1.dp, accent.copy(alpha = 0.6f))
                            .clickable(enabled = team.isNotEmpty()) { pick(teamIndex + 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = lead?.let { spriteOf(it) }
                        if (bmp != null) MonImage(bmp, lead.name, lead.fainted, Modifier.size(48.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                family == GameOverFamily.DS && won -> "RUN WON"
                                family == GameOverFamily.DS -> "RUN OVER"
                                won -> "YOU WON"
                                else -> "GAME OVER"
                            },
                            fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 15.sp, color = accent, maxLines = 1,
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixText("ATTEMPT", 10, Pc.Dim)
                            Spacer(Modifier.width(5.dp))
                            PixText("$attempt", 13, Pc.Text)
                            if (team.size > 1) {
                                Spacer(Modifier.width(12.dp))
                                team.forEachIndexed { i, m ->
                                    val b = spriteOf(m)
                                    Box(
                                        Modifier.size(26.dp).clickable { pick(i) }
                                            .then(if (i == teamIndex) Modifier.drawBehind {
                                                drawLine(accent, Offset(2f, size.height - 1f), Offset(size.width - 2f, size.height - 1f), 2.dp.toPx())
                                            } else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) { if (b != null) MonImage(b, m.name, m.fainted, Modifier.size(24.dp)) }
                                }
                            }
                        }
                    }
                    // Blake, 2026-09-07: an X in the top right closes the popup; the run
                    // stays as it is, the same as Continue playing.
                    Box(
                        Modifier.size(32.dp).border(1.dp, Pc.Border).clickable { onContinue() },
                        contentAlignment = Alignment.Center,
                    ) { Text("X", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 12.sp, color = Pc.Text) }
                }
                // The announcer's line, or the DS tracker's run-over message.
                PixText(
                    if (won && family != GameOverFamily.DS) "CONGRATULATIONS!!" else quotes[quoteIndex],
                    13, if (won) Pc.Positive else Pc.Text,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    align = TextAlign.Center, wrap = true,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border.copy(alpha = 0.45f)))
                // The actions, two to a row; an odd last one takes the whole row.
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { GameOverTile(it, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** A team member's picture, drawn crisp; a fainted one in grey and faded, as a fallen member reads. */
@Composable
private fun MonImage(bmp: ImageBitmap, name: String, fainted: Boolean, modifier: Modifier) {
    Image(
        bmp, contentDescription = name, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
        colorFilter = if (fainted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        modifier = modifier.then(if (fainted) Modifier.alpha(0.65f) else Modifier),
    )
}

private enum class Glyph { ARROW, SWORD, INSTALL, MAGNIFIER, PLUS, STAR }

private enum class Tone { PRIMARY, PLAIN, GOLD, GOOD, DANGER }

private class TileSpec(val glyph: Glyph, val label: String, val tone: Tone, val onClick: () -> Unit)

/** One action: the reference's icon and label, as a 40dp tile. */
@Composable
private fun GameOverTile(a: TileSpec, modifier: Modifier) {
    val bg: Color; val fg: Color; val edge: Color
    when (a.tone) {
        Tone.PRIMARY -> { bg = Pc.Text; fg = Pc.Page; edge = Pc.Text }
        Tone.GOLD -> { bg = Pc.Page; fg = Pc.Gold; edge = Pc.Gold }
        Tone.GOOD -> { bg = Pc.Page; fg = Pc.Positive; edge = Pc.Positive }
        Tone.DANGER -> { bg = Pc.Page; fg = Pc.Negative; edge = Pc.Negative }
        Tone.PLAIN -> { bg = Pc.Ground; fg = Pc.Text; edge = Pc.Border }
    }
    Row(
        modifier.height(40.dp).background(bg).border(1.dp, edge).clickable { a.onClick() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(a.glyph, fg, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        PixText(a.label, 12, fg, Modifier.weight(1f), wrap = true)
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val s = 2.2f
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
            Glyph.STAR -> {
                val pts = (0 until 10).map { k ->
                    val r = if (k % 2 == 0) w / 2 else w / 4.6f
                    val a = Math.toRadians(-90.0 + k * 36.0)
                    Offset(w / 2 + (r * kotlin.math.cos(a)).toFloat(), h / 2 + (r * kotlin.math.sin(a)).toFloat())
                }
                for (k in pts.indices) drawLine(color, pts[k], pts[(k + 1) % pts.size], s * 0.8f)
            }
        }
    }
}

/** Places the popup on the game picture (it is the picture's size), kept inside the window. Null frame: centred. */
private class OverGameFrame(private val frame: androidx.compose.ui.geometry.Rect?) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val left = frame?.left ?: 0f
        val top = frame?.top ?: 0f
        val w = frame?.width ?: windowSize.width.toFloat()
        val h = frame?.height ?: windowSize.height.toFloat()
        val x = (left + (w - popupContentSize.width) / 2f).toInt().coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (top + (h - popupContentSize.height) / 2f).toInt().coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/**
 * With a game picture: fills it with a dim layer and lays the card out at the
 * picture's width (up to 440dp) centred on it, scaled down only if it is still
 * taller than the picture. Without one: the card at 400dp, centred. Taps follow
 * the scale.
 */
@Composable
private fun FitInside(frame: androidx.compose.ui.geometry.Rect?, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = if (frame != null) Modifier.background(Color(0xA6000000)) else Modifier,
    ) { measurables, _ ->
        val margin = 6.dp.toPx()
        val cardW = ((frame?.width?.minus(2 * margin)) ?: 400.dp.toPx()).coerceAtMost(440.dp.toPx()).toInt().coerceAtLeast(1)
        val p = measurables.first().measure(androidx.compose.ui.unit.Constraints(maxWidth = cardW))
        val maxH = frame?.let { it.height - 2 * margin } ?: p.height.toFloat()
        val s = minOf(1f, maxH / p.height).coerceAtLeast(0.4f)
        val w = frame?.width?.toInt() ?: (p.width * s).toInt()
        val h = frame?.height?.toInt() ?: (p.height * s).toInt()
        layout(w, h) {
            val x = ((w - p.width * s) / 2f).toInt()
            val y = ((h - p.height * s) / 2f).toInt()
            p.placeWithLayer(x, y) {
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}
