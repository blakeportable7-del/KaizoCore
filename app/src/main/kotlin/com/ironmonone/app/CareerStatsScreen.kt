package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Your stats (2026-09-30): the lifetime numbers of CareerStats as plain rows, no charts. It draws under a ModeScreen's top
 * bar (Home's link) and says nothing that StatsCopy does not hold, so the copy rules cover everything the player reads.
 *
 * The numbers are read off the main thread, as Home's Continue card is: a history can be a dozen files. Nothing is drawn
 * until they are in, so the rows never move under a finger.
 */
@Composable
internal fun CareerStatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<CareerStats?>(null) }
    LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { CareerStats.readOrEmpty(context.filesDir) } }
    Box(modifier.fillMaxSize().background(Shell.night)) {
        val s = stats
        if (s != null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A column no wider than a phone's, so a tablet does not stretch the rows across the room.
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    if (s.isEmpty) {
                        Gen3Box(Modifier.fillMaxWidth()) {
                            Text(StatsCopy.EMPTY, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                        }
                    } else {
                        Gen3Box(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatRow(StatsCopy.RUNS_STARTED, StatsCopy.number(s.runsStarted))
                                StatRow(StatsCopy.RUNS_WON, StatsCopy.wins(s.wins, s.winsAfterRewinds))
                                StatRow(StatsCopy.TIME_PLAYED, StatsCopy.timePlayed(s.playSeconds))
                                StatRow(StatsCopy.ENDED_MOST, StatsCopy.cause(s.topCause))
                                StatRow(StatsCopy.STREAK, StatsCopy.streak(s.longestWinStreak))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Gen3Header(StatsCopy.BEST_TITLE)
                        Spacer(Modifier.height(6.dp))
                        Gen3Box(Modifier.fillMaxWidth()) {
                            if (s.bests.isEmpty()) {
                                Text(StatsCopy.NO_BEST, style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    s.bests.forEach { b ->
                                        // One line to a screen reader: the game and mode, then how far it got.
                                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                                            Text(StatsCopy.bestTitle(b), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                                            Text(StatsCopy.bestResult(b), style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Gen3Header(StatsCopy.NUZLOCKE_TITLE)
                        Spacer(Modifier.height(6.dp))
                        Gen3Box(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatRow(StatsCopy.NUZLOCKE_STARTED, StatsCopy.number(s.nuzlockeStarted))
                                StatRow(StatsCopy.NUZLOCKE_FINISHED, StatsCopy.number(s.nuzlockeFinished))
                                StatRow(StatsCopy.NUZLOCKE_LOST, StatsCopy.number(s.nuzlockeLost))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(StatsCopy.NOTE, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
                    }
                }
            }
        }
    }
}

/**
 * A label and its number on one line: the label takes the room and wraps at a large font, the number keeps its own.
 * A screen reader reads the two as one thing.
 */
@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper, textAlign = TextAlign.End)
    }
}
