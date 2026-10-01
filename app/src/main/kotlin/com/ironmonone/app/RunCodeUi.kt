package com.ironmonone.app

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Run tab's run codes (RunCodes): share the run in play as a code, or build the run a code
 * names from this phone's own dump and settings. Building one ends the run in play, so it asks
 * first, as Start new run does, and it stays on this tab afterwards so the checksum verdict is
 * read (RunJob.randomize with expect).
 */
@Composable
internal fun RunCodeSection(
    store: PrepStore,
    preparedList: List<Pair<RomKind, File>>,
    settingsList: List<File>,
    refreshKey: Int,
    busy: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember(refreshKey) { NextRun.currentRecipe(store) }
    var sharing by remember { mutableStateOf(false) }
    var codeText by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<RunCodes.Plan.Ready?>(null) }

    fun build(plan: RunCodes.Plan.Ready) {
        // The code's passes for this one build; the player's switches for the file stay as they are (R4, 2026-09-30).
        if (!RunJob.randomize(context, plan.kind to plan.prepared, plan.settings, seed = plan.code.seed, expect = plan.code,
                prePassOn = plan.code.prePass.takeIf { ExtraPasses.prePassName(plan.kind) != null },
                part2On = plan.code.part2.takeIf { ExtraPasses.takesPart2(plan.kind) }))
            RunJob.say("A run is being made already. Paste the code again when it is done.", true)
    }

    Column(Modifier.fillMaxWidth()) {
        if (current != null) {
            Text(
                "Your run as a code: a friend with the same game and settings builds the same game from it.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
            Spacer(Modifier.height(6.dp))
            Gen3Button(if (sharing) "Making the code..." else "Share this run", enabled = !sharing && !busy) {
                sharing = true
                scope.launch {
                    val (recipe, seed) = current
                    val kind = RomKind.byId(recipe.kind)
                    // The run's own checksum, so a friend's build can be compared with it: a few
                    // seconds for a DS game, off the main thread.
                    val crc = withContext(Dispatchers.IO) {
                        runCatching { kind?.let { RunCode.crc32(store.currentRunFor(it)) } }.getOrNull() ?: 0L
                    }
                    val code = RunCodes.shareCodeFor(recipe, seed, crc)
                    val text = RunCodes.shareText(code, kind?.displayName ?: recipe.kind, store.attempt(recipe.kind))
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share this run"))
                    }.onFailure { RunJob.say("Could not open sharing on this phone.", true) }
                    sharing = false
                }
            }
            Spacer(Modifier.height(14.dp))
        } else if (store.currentRun.exists()) {
            Text(
                "This run was made before run codes, so it has no code. Your next run can be shared.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "Play a friend's run: paste their code. Your own dump and settings build it, and the checksum says whether it is the same game.",
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            codeText, { codeText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Run code") }, placeholder = { Text("KC1-...") },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Shell.hintOnPaper),
        )
        Spacer(Modifier.height(6.dp))
        Gen3Button("Build this run", enabled = codeText.isNotBlank() && !busy) {
            scope.launch {
                val plan = withContext(Dispatchers.IO) {
                    RunCodes.plan(codeText, preparedList, settingsList) { f -> RunCode.sha256(f) }
                }
                when (plan) {
                    is RunCodes.Plan.Refused -> RunJob.say(plan.why, true)
                    // Asked first when a run would end, or when this game's history already holds the seed (R7).
                    is RunCodes.Plan.Ready -> if (store.currentRun.exists() || RunCodeHistory.playedBefore(store, plan) != null) confirm = plan else build(plan)
                }
            }
        }
        confirm?.let { plan ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                text = {
                    val before = remember(plan) { RunCodeHistory.playedBefore(store, plan) }
                    Text(
                        listOfNotNull(
                            before?.let { RunCodeHistory.playedLine(it) },
                            if (store.currentRun.exists()) "End the current run and build the shared run on ${plan.kind.displayName} " +
                                "(${plan.settings.name.removeSuffix(".rnqs")})?"
                            else "Build the shared run on ${plan.kind.displayName} (${plan.settings.name.removeSuffix(".rnqs")})?",
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = { Gen3Button("YES, BUILD IT", accent = true) { confirm = null; build(plan) } },
                dismissButton = { Gen3Button("CANCEL") { confirm = null } },
            )
        }
    }
}
