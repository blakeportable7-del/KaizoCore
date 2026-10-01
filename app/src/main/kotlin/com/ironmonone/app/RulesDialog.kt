package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The rules that ship for a game family, read from assets/rulesets/<family>/,
 * and for a Nat. Dex build from rulesets/<family>-NatDex/: the same modes with
 * the Nat. Dex ruleset changes (tools/rules/build_rules.py).
 */
object Rules {
    const val DIR = "rulesets"
    /** The folder a game's rules are in. */
    fun dirFor(family: String, natDex: Boolean): String = if (natDex) "$family-NatDex" else family
    fun modesFor(context: android.content.Context, dir: String): List<String> =
        order(runCatching { context.assets.list("$DIR/$dir")?.map { it.removeSuffix(".md") } ?: emptyList() }.getOrDefault(emptyList()))
    fun text(context: android.content.Context, dir: String, mode: String): String? =
        runCatching { context.assets.open("$DIR/$dir/$mode.md").bufferedReader().readText() }.getOrNull()
    /** The Mode row's order (RulesetCatalog.rank): the box listed the modes after Survival in another (2026-10-01). */
    fun order(modes: List<String>): List<String> = modes.sortedWith(compareBy({ RulesetCatalog.rank(it) }, { it }))

    /**
     * The tabs for [kind]: the folder's modes the game can run (RulesetCatalog.defined). Ruby, Sapphire, Diamond and
     * Pearl share a folder with Emerald and Platinum, whose Super Kaizo they have no patch for (2026-10-01, rules check).
     */
    fun shown(modes: List<String>, kind: com.ironmonone.core.RomKind?): List<String> =
        order(modes.filter { kind == null || RulesetCatalog.defined(kind, it) })

    /**
     * Which tab opens, and what to say when the run's own mode has none: it
     * used to open the first tab (Standard) with no word, so a custom mode's
     * player read rules that were not theirs. With no run mode (a library
     * game, a file with no mode in its name) the first tab opens as before.
     */
    fun opening(modes: List<String>, mode: String?): Pair<String?, String?> = when {
        mode == null || mode in modes -> (mode ?: modes.firstOrNull()) to null
        else -> null to "There is no rules text for ${RnqsInfo.rulesetLabel(mode)} on this game. The tabs above are the modes that have some."
    }
}

/**
 * 2.4, the RULES button: the rules for the game and mode being played,
 * readable mid-run and closed without leaving the game. The text is the
 * official rulesets, generated from their sources by tools/rules/build_rules.py
 * with the sources and dates at the bottom of every file. [mode] is the run's
 * own mode when known; the tabs switch modes either way. [natDex] shows the
 * Nat. Dex text, which carries the Nat. Dex ruleset changes.
 */
@Composable
fun RulesDialog(family: String, mode: String?, natDex: Boolean = false, kind: com.ironmonone.core.RomKind? = null, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dir = Rules.dirFor(family, natDex)
    val modes = remember(dir, kind?.id) { Rules.shown(Rules.modesFor(context, dir), kind) }
    val opening = remember(dir, mode) { Rules.opening(modes, mode) }
    var current by remember(dir, mode) { mutableStateOf(opening.first) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(340.dp).heightIn(max = 600.dp).background(Pc.Ground).border(1.dp, Pc.Border).padding(10.dp)) {
            // Every label is in sp, at 12 or more, so it follows the phone's font size (2026-09-30, UX audit
            // P0-15): the tabs were 6dp and the headings 7 to 9dp, in reference pixels.
            DialogText("RULES", 18, Pc.Gold, heading = true)
            Spacer(Modifier.height(6.dp))
            if (modes.isEmpty()) {
                DialogText("No rules text ships for this game yet.", 13, Pc.Dim)
            } else {
                // Six tabs in a Row were clipped on a 320dp phone and about
                // 12dp tall. They wrap now, each a 48dp target, same look
                // (2026-09-27, audit).
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    modes.forEach { m ->
                        val on = m == current
                        androidx.compose.foundation.layout.Box(
                            Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).widthIn(min = PcMin.DIALOG_TOUCH_DP.dp)
                                .selectable(selected = on, role = Role.Tab) { current = m },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            // The chosen tab is gold, underlined and in a heavier frame: more than a colour tells
                            // it from the rest, and a screen reader hears it as selected.
                            DialogText(
                                RnqsInfo.rulesetLabel(m).uppercase(), 13, if (on) Pc.Gold else Pc.Dim,
                                Modifier.border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border).padding(horizontal = 10.dp, vertical = 7.dp),
                                underline = on,
                            )
                        }
                    }
                }
                // The run's mode has no text here: said plainly until a tab is picked.
                if (current == null) opening.second?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = Pc.Text) }
                val text = remember(dir, current) { current?.let { Rules.text(context, dir, it) } ?: "" }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    text.lines().forEach { line ->
                        when {
                            // Headings wrap: a long one was cut mid-word at the dialog edge.
                            line.startsWith("# ") -> { DialogText(line.removePrefix("# ").uppercase(), 16, Pc.Gold, heading = true); Spacer(Modifier.height(4.dp)) }
                            line.startsWith("## ") -> { Spacer(Modifier.height(6.dp)); DialogText(line.removePrefix("## ").uppercase(), 14, Pc.Text, heading = true); Spacer(Modifier.height(3.dp)) }
                            line.startsWith("### ") -> { Spacer(Modifier.height(4.dp)); DialogText(line.removePrefix("### "), 13, Pc.Gold, heading = true); Spacer(Modifier.height(2.dp)) }
                            line.isBlank() -> Spacer(Modifier.height(3.dp))
                            else -> Text(
                                line.trim(), fontSize = 12.sp, lineHeight = 16.sp,
                                color = if (line.trimStart().startsWith("- Note:")) Color(0xFFB8B8B0) else Pc.Text,
                                // Four spaces a level: build_rules.py keeps a source's nesting, two levels deep.
                                modifier = Modifier.padding(start = 14.dp * ((line.length - line.trimStart().length) / 4)),
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
