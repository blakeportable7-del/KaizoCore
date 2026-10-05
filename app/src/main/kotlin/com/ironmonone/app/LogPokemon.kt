package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import com.ironmonone.tracker.Gen3Types

/**
 * LogTabPokemon: every Pokemon as its icon, a tap opening its page. Each cell carries its name, its types and its BST
 * in a size a phone can read (2026-10-02, Blake asked for a better look than the reference's 7-pixel labels).
 */
@Composable
internal fun LogPokemonTab(
    rows: List<RandomizerLog.Pokemon>,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    names: LogNames = LogNames.PLAIN,
    /** Its Walking Pals, which stand idle in place of the still (LogTabPokemon's icons, SpriteData.Types.Idle). */
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? = null,
) {
    if (rows.isEmpty()) {
        DialogText("No Pok\u00e9mon match that search.", 14, Pc.Dim)
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(108.dp), Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.id }) { p ->
            Column(
                Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onPokemon(p) }.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val shown = names.species(p.name)
                LogMonIcon(spriteOf?.invoke(p), palOf?.invoke(p), 56.dp, shown)
                DialogText(shown, 13, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    p.types.forEach { PcTypeChip(it.uppercase(), pcTypeColorByName(it)) }
                }
                Spacer(Modifier.height(3.dp))
                DialogText("BST ${p.bst}", 12, Pc.Gold, align = TextAlign.Center)
            }
        }
    }
}

private enum class StatView { BST, IVS, EVS }

/**
 * The labels under the log's evolution icons (LogTabPokemonDetails.lua:163-189, 204-222, 286-326):
 * each species' own evolution method in the reference's short words (EvoText.short), the i-th
 * method for the i-th evolution, the first where there are fewer methods than evolutions; for a
 * pre-evolution, its method into the Pokemon on the page, "" when the log does not list that one.
 */
internal object LogEvoLabels {
    fun forward(methods: List<String>, index: Int): String = methods.getOrNull(index) ?: methods.firstOrNull() ?: ""

    fun into(preMethods: List<String>, preEvolutions: List<String>, current: String): String {
        val j = preEvolutions.indexOfFirst { it.equals(current, ignoreCase = true) }
        return if (j < 0) "" else preMethods.getOrNull(j) ?: preMethods.firstOrNull() ?: ""
    }
}

/**
 * LogTabPokemonDetails: the name and abilities, the evolutions (with the
 * pre-evolutions when that box is ticked), the base stat graph (green from
 * 180, red at 40 and under) with its total, and the moves on two tabs:
 * Levelup Moves, and TM Moves with the gym TMs first. For a Pokemon on the
 * player's team the graph can show "Your IVs" and "Your EVs" instead, as the
 * reference allows for the log of the run being played.
 */
@Composable
internal fun LogPokemonDetail(
    p: RandomizerLog.Pokemon,
    log: RandomizerLog,
    frlg: Boolean,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    moveTypes: Map<String, Int>,
    /** The team member of this species' IVs and EVs in the reference's stat order, when it is on the team. */
    mine: Pair<List<Int>, List<Int>>?,
    /** The game's names for the log's (LogNames). */
    names: LogNames,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    onBack: () -> Unit,
    /** A species' evolution methods, short (EvoText.short of its PokemonData evolution); null for none. */
    evoMethodsOf: ((RandomizerLog.Pokemon) -> List<String>)? = null,
    /** Walking Pals for the icons that stand idle on PC: this one and its evolutions (LogTabPokemonDetails). */
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? = null,
    /** The game's gym TMs in badge order (LogTrainerRules.gymTms); null is the five games' by [frlg]. */
    gymTms: List<Int>? = null,
    /** The PC tracker's Pokemon info panel for this species (LogPokemonInfoPanel), under the header. */
    infoPanel: (@Composable () -> Unit)? = null,
) {
    val types = p.types.mapNotNull { Gen3Types.idOf(it) }
    fun stab(move: String) = moveTypes[move.uppercase()]?.let { it in types } == true
    var levelTab by remember(p.id) { mutableStateOf(true) }
    var view by remember(p.id) { mutableStateOf(StatView.BST) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // The header (2026-10-02, readable): Back, the sprite, the name, its types as the tracker draws them.
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LogBack(onBack)
            LogMonIcon(spriteOf?.invoke(p), palOf?.invoke(p), 48.dp, null)
            Spacer(Modifier.width(8.dp))
            DialogText(names.species(p.name), 17, Pc.Gold, Modifier.weight(1f), heading = true)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { p.types.forEach { PcTypeChip(it.uppercase(), pcTypeColorByName(it)) } }
        }
        if (infoPanel != null) { Spacer(Modifier.height(6.dp)); infoPanel() }
        Spacer(Modifier.height(6.dp))
        // ABILITIES, the second dropped when it repeats the first.
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(10.dp)) {
            DialogText("Abilities", 12, Pc.Dim)
            p.abilities.distinct().forEachIndexed { i, a -> DialogText("${i + 1}: ${logTitle(a)}", 14, Pc.Text) }
        }
        // EVOLUTIONS
        val prevos = if (TrackerOptions.logShowPreEvolutions) LogSearch.preEvolutions(log, p) else emptyList()
        val evos = p.evolutions.mapNotNull { log.pokemonNamed(it) }
        if (prevos.isNotEmpty() || evos.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prevos.forEach { pre -> EvoIcon(pre, spriteOf, onPokemon, names, palOf, evoMethodsOf?.let { LogEvoLabels.into(it(pre), pre.evolutions, p.name) }) }
                if (prevos.isNotEmpty()) DialogText(">", 16, Pc.Dim)
                EvoIcon(p, spriteOf, null, names, palOf)
                if (evos.isNotEmpty()) DialogText(">", 16, Pc.Dim)
                val methods = evoMethodsOf?.invoke(p) ?: emptyList()
                evos.forEachIndexed { i, evo -> EvoIcon(evo, spriteOf, onPokemon, names, palOf, evoMethodsOf?.let { LogEvoLabels.forward(methods, i) }) }
            }
        }
        // STAT GRAPH
        Spacer(Modifier.height(6.dp))
        val keys = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        val values = when {
            view == StatView.IVS && mine != null -> mine.first
            view == StatView.EVS && mine != null -> mine.second
            else -> keys.map { LogSearch.statOf(p, it) }
        }
        fun barOf(v: Int) = if (view == StatView.IVS) minOf(v * 8, 255) else v
        fun colorOf(v: Int) = barOf(v).let { b -> if (b >= 180) Pc.Positive else if (b <= 40) Pc.Negative else Pc.Text }
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogText(when (view) { StatView.IVS -> "Your IVs"; StatView.EVS -> "Your EVs"; else -> "Base stats" }, 14, Pc.Text, Modifier.weight(1f))
                if (mine != null) {
                    Box(Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(1.dp, Pc.Border).clickable {
                        view = when (view) { StatView.BST -> StatView.IVS; StatView.IVS -> StatView.EVS; else -> StatView.BST }
                    }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        DialogText(when (view) { StatView.BST -> "Show IVs"; StatView.IVS -> "Show EVs"; else -> "Show BST" }, 13, Pc.Gold)
                    }
                } else DialogText("Total ${p.bst}", 14, Pc.Gold)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(80.dp).border(1.dp, Pc.Border).padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                values.forEach { v -> Box(Modifier.width(12.dp).fillMaxHeight((barOf(v) / 255f).coerceIn(0f, 1f)).background(colorOf(v))) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                keys.forEachIndexed { i, k ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DialogText(k.lowercase().replaceFirstChar { it.uppercase() }, 12, Pc.Dim)
                        DialogText("${values.getOrElse(i) { 0 }}", 14, colorOf(values.getOrElse(i) { 0 }))
                    }
                }
            }
        }
        // MOVES: two tabs, a full touch target each, the open one gold and underlined; the moves in rows a phone can read.
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(true to "Level-up moves", false to "TM moves").forEach { (level, name) ->
                    val on = levelTab == level
                    Box(Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable { levelTab = level }, contentAlignment = Alignment.Center) {
                        DialogText(name, 14, if (on) Pc.Gold else Pc.Text, underline = on)
                    }
                }
            }
            @Composable
            fun moveRow(lead: String, move: String, color: androidx.compose.ui.graphics.Color, mod: Modifier = Modifier) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    DialogText(lead, 13, Pc.Dim, Modifier.width(52.dp))
                    DialogText(move, 14, color, mod)
                }
            }
            if (levelTab) {
                p.evoMoves.forEach { mv -> moveRow("Evo", names.move(mv), if (stab(mv)) Pc.Positive else Pc.Text) }
                p.moves.forEach { (lv, mv) -> moveRow("Lv %d".format(lv), names.move(mv), if (stab(mv)) Pc.Positive else Pc.Text) }
                if (p.moves.isEmpty() && p.evoMoves.isEmpty()) DialogText("The log lists no level-up moves for it.", 13, Pc.Dim)
            } else {
                LogSearch.tmRows(p, log, frlg, TrackerOptions.logShowUnlearnableGymTms, gymTms ?: LogTms.gymTmNumbers(frlg)).forEach { r ->
                    val label = r.label
                    if (label != null) {
                        DialogText(label, 13, Pc.Gold, Modifier.padding(top = 8.dp, bottom = 2.dp))
                    } else {
                        val num = "TM%02d".format(r.number)
                        if (r.unlearnable) {
                            // The reference fades a gym TM it cannot learn and strikes it through in red.
                            moveRow(num, names.move(r.move), Pc.Text.copy(alpha = 0.62f), Modifier.drawWithContent {
                                drawContent()
                                val y = size.height / 2f
                                drawLine(Pc.Negative.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            })
                        } else moveRow(num, names.move(r.move), if (stab(r.move)) Pc.Positive else Pc.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvoIcon(
    p: RandomizerLog.Pokemon, spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?, onPokemon: ((RandomizerLog.Pokemon) -> Unit)?,
    names: LogNames, palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)?, method: String? = null,
) {
    Column(
        Modifier.then(if (onPokemon != null) Modifier.clickable { onPokemon(p) } else Modifier).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shown = names.species(p.name)
        LogMonIcon(spriteOf?.invoke(p), palOf?.invoke(p), 44.dp, shown)
        DialogText(shown, 12, if (onPokemon == null) Pc.Gold else Pc.Text)
        if (!method.isNullOrEmpty()) DialogText(method, 12, Pc.Dim)
    }
}
