package com.ironmonone.app

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** One member of the team the run ended with, whichever tracker produced it. */
data class GameOverMon(val species: Int, val name: String, val level: Int, val fainted: Boolean, val shiny: Boolean = false)

/**
 * The reference's GameOverScreen.lua as a dialog over the game.
 *
 * Top box: the team icon (tap it to cycle the team and roll a new announcer
 * quote, the reference's PokemonIcon button), "GAME OVER" in Intermediate
 * text, "Attempt: N", and the quote centred, or CONGRATULATIONS!! on a win.
 * Bottom box: the final team, then the two actions this port keeps of the
 * reference's row: "Continue playing" (its ContinuePlaying button, back to
 * the tracker with the run as it is) and NEW RUN (the app's existing path,
 * with its own confirmation). The reference's BizHawk-only retry, save and
 * notes-grading buttons are not ported. Shown once per outcome per run.
 */
@Composable
fun GameOverDialog(
    won: Boolean,
    attempt: Int,
    team: List<GameOverMon>,
    spriteOf: @Composable (GameOverMon) -> ImageBitmap?,
    onContinue: () -> Unit,
    onNewRun: () -> Unit,
) {
    var teamIndex by remember { mutableIntStateOf(0) }
    var quoteIndex by remember { mutableIntStateOf(((attempt % PcGameOverQuotes.size) + PcGameOverQuotes.size) % PcGameOverQuotes.size) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onContinue) {
        Column(Modifier.width(300.dp).background(Pc.Ground).border(1.dp, Pc.Border)) {
            // Top box
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        PixText("GAME OVER", 11, Pc.Gold)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            PixText("Attempt:", 8, Pc.Text, Modifier.width(66.dp))
                            PixText("$attempt", 8, Pc.Text)
                        }
                    }
                    val shown = team.getOrNull(teamIndex)
                    Box(
                        Modifier.size(40.dp).clickable(enabled = team.size > 0) {
                            // nextTeamPokemon + randomizeAnnouncerQuote
                            if (team.isNotEmpty()) teamIndex = (teamIndex + 1) % team.size
                            quoteIndex = (quoteIndex + 7) % PcGameOverQuotes.size
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = shown?.let { spriteOf(it) }
                        if (bmp != null) Image(bmp, contentDescription = shown.name, modifier = Modifier.size(40.dp), contentScale = ContentScale.Fit)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PixText(
                        if (won) "CONGRATULATIONS!!" else PcGameOverQuotes[quoteIndex],
                        8, if (won) Pc.Positive else Pc.Text,
                    )
                }
            }
            // Bottom box
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                PixText("Final team", 8, Pc.Text)
                Spacer(Modifier.height(4.dp))
                team.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        PixText(m.name, 8, if (m.fainted) Pc.Negative else Pc.Text, Modifier.weight(1f))
                        PixText("Lv.${m.level}", 8, Pc.Dim)
                    }
                }
                if (team.isEmpty()) PixText("(no Pokemon)", 8, Pc.Dim)
                Spacer(Modifier.height(12.dp))
                // Stacked, full width: side by side the second label was clipped to "N".
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("CONTINUE PLAYING") { onContinue() }
                    com.ironmonone.app.gen3.Gen3Button("NEW RUN", accent = true) { onNewRun() }
                }
            }
        }
    }
}
