package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The rules that ship for a game family, read from assets/rulesets/<family>/. */
object Rules {
    const val DIR = "rulesets"
    fun modesFor(context: android.content.Context, family: String): List<String> =
        runCatching { context.assets.list("$DIR/$family")?.map { it.removeSuffix(".md") } ?: emptyList() }.getOrDefault(emptyList())
            .sortedBy { RulesetCatalogOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
    fun text(context: android.content.Context, family: String, mode: String): String? =
        runCatching { context.assets.open("$DIR/$family/$mode.md").bufferedReader().readText() }.getOrNull()
    private val RulesetCatalogOrder = listOf("standard", "ultimate", "kaizo", "superkaizo", "survival", "kaizodoubles")
}

/**
 * 2.4, the RULES button: the rules for the game and mode being played,
 * readable mid-run and closed without leaving the game. The text is the
 * official rulesets, generated from their sources by tools/rules/build_rules.py
 * with the sources and dates at the bottom of every file. [mode] is the run's
 * own mode when known; the tabs switch modes either way.
 */
@Composable
fun RulesDialog(family: String, mode: String?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modes = remember(family) { Rules.modesFor(context, family) }
    var current by remember(family, mode) { mutableStateOf(mode?.takeIf { it in modes } ?: modes.firstOrNull()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(340.dp).heightIn(max = 600.dp).background(Pc.Ground).border(1.dp, Pc.Border).padding(10.dp)) {
            PixText("RULES", 10, Pc.Gold)
            Spacer(Modifier.height(6.dp))
            if (modes.isEmpty()) {
                PixText("No rules text ships for this game yet.", 7, Pc.Dim)
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEach { m ->
                        val on = m == current
                        PixText(
                            RnqsInfo.rulesetLabel(m).uppercase(), 6, if (on) Pc.Gold else Pc.Dim,
                            Modifier.border(1.dp, if (on) Pc.Gold else Pc.Border).padding(horizontal = 4.dp, vertical = 3.dp)
                                .clickable { current = m },
                        )
                    }
                }
                val text = remember(family, current) { current?.let { Rules.text(context, family, it) } ?: "" }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    text.lines().forEach { line ->
                        when {
                            line.startsWith("# ") -> { PixText(line.removePrefix("# ").uppercase(), 9, Pc.Gold); Spacer(Modifier.height(4.dp)) }
                            line.startsWith("## ") -> { Spacer(Modifier.height(6.dp)); PixText(line.removePrefix("## ").uppercase(), 8, Pc.Text); Spacer(Modifier.height(3.dp)) }
                            line.startsWith("### ") -> { Spacer(Modifier.height(4.dp)); PixText(line.removePrefix("### "), 7, Pc.Gold); Spacer(Modifier.height(2.dp)) }
                            line.isBlank() -> Spacer(Modifier.height(3.dp))
                            else -> Text(
                                line.trimEnd(), fontSize = 12.sp, lineHeight = 16.sp,
                                color = if (line.trimStart().startsWith("- Note:")) Color(0xFFB8B8B0) else Pc.Text,
                                modifier = Modifier.padding(start = if (line.startsWith("    ")) 14.dp else 0.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            com.ironmonone.app.gen3.Gen3Button("CLOSE") { onDismiss() }
        }
    }
}
