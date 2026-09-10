package com.ironmonone.app

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.Gen3Types

/** LogTabPokemon: every Pokemon as its icon with its name above; a tap opens its page. */
@Composable
internal fun LogPokemonTab(
    rows: List<RandomizerLog.Pokemon>,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
) {
    if (rows.isEmpty()) {
        PixText("(No results)", 8, Pc.Dim)
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(76.dp), Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rows, key = { it.id }) { p ->
            Column(
                Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onPokemon(p) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixText(logTitle(p.name), 7, Pc.Text, Modifier.fillMaxWidth(), TextAlign.Center)
                val art = spriteOf?.invoke(p)
                if (art != null) Image(art, p.name, Modifier.size(40.dp), filterQuality = FilterQuality.None)
                else Spacer(Modifier.size(40.dp))
            }
        }
    }
}

private enum class StatView { BST, IVS, EVS }

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
    /** Log name, upper case, to the team member's IVs and EVs in the reference's stat order. */
    team: Map<String, Pair<List<Int>, List<Int>>>,
    onPokemon: (RandomizerLog.Pokemon) -> Unit,
    onBack: () -> Unit,
) {
    val types = p.types.mapNotNull { Gen3Types.idOf(it) }
    fun stab(move: String) = moveTypes[move.uppercase()]?.let { it in types } == true
    var levelTab by remember(p.id) { mutableStateOf(true) }
    var view by remember(p.id) { mutableStateOf(StatView.BST) }
    val mine = team[p.name.uppercase()]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PixText("< BACK", 7, Pc.Dim, Modifier.clickable { onBack() }.padding(end = 10.dp))
            PixText(p.name.uppercase(), 9, Pc.Gold, Modifier.weight(1f))
            PixText(p.types.joinToString("/") { logTitle(it) }, 7, Pc.Text)
        }
        Spacer(Modifier.height(4.dp))
        // ABILITIES, the second dropped when it repeats the first.
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            p.abilities.distinct().forEachIndexed { i, a -> PixText("${i + 1}: ${logTitle(a)}", 7, Pc.Text) }
        }
        // EVOLUTIONS
        val prevos = if (TrackerOptions.logShowPreEvolutions) LogSearch.preEvolutions(log, p) else emptyList()
        val evos = p.evolutions.mapNotNull { log.pokemonNamed(it) }
        if (prevos.isNotEmpty() || evos.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                prevos.forEach { EvoIcon(it, spriteOf, onPokemon) }
                if (prevos.isNotEmpty()) PixText(">", 9, Pc.Dim)
                EvoIcon(p, spriteOf, null)
                if (evos.isNotEmpty()) PixText(">", 9, Pc.Dim)
                evos.forEach { EvoIcon(it, spriteOf, onPokemon) }
            }
        }
        // STAT GRAPH
        Spacer(Modifier.height(4.dp))
        val keys = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        val values = when {
            view == StatView.IVS && mine != null -> mine.first
            view == StatView.EVS && mine != null -> mine.second
            else -> keys.map { LogSearch.statOf(p, it) }
        }
        fun barOf(v: Int) = if (view == StatView.IVS) minOf(v * 8, 255) else v
        fun colorOf(v: Int) = barOf(v).let { b -> if (b >= 180) Pc.Positive else if (b <= 40) Pc.Negative else Pc.Text }
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixText(when (view) { StatView.IVS -> "Your IVs"; StatView.EVS -> "Your EVs"; else -> "Base Stats" }, 7, Pc.Text, Modifier.weight(1f))
                if (mine != null) {
                    PixText(when (view) { StatView.BST -> "Show IVs"; StatView.IVS -> "Show EVs"; else -> "Show BST" }, 7, Pc.Gold,
                        Modifier.border(1.dp, Pc.Border).clickable {
                            view = when (view) { StatView.BST -> StatView.IVS; StatView.IVS -> StatView.EVS; else -> StatView.BST }
                        }.padding(horizontal = 6.dp, vertical = 3.dp))
                } else PixText("Total: ${p.bst}", 7, Pc.Text)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(80.dp).border(1.dp, Pc.Border).padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                values.forEach { v -> Box(Modifier.width(12.dp).fillMaxHeight((barOf(v) / 255f).coerceIn(0f, 1f)).background(colorOf(v))) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                keys.forEachIndexed { i, k ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PixText(k.lowercase().replaceFirstChar { it.uppercase() }, 7, Pc.Text)
                        PixText("${values.getOrElse(i) { 0 }}", 7, colorOf(values.getOrElse(i) { 0 }))
                    }
                }
            }
        }
        // MOVES
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PixText("Levelup Moves", 7, if (levelTab) Pc.Gold else Pc.Text, Modifier.clickable { levelTab = true }.padding(vertical = 3.dp))
                PixText("TM Moves", 7, if (!levelTab) Pc.Gold else Pc.Text, Modifier.clickable { levelTab = false }.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(3.dp))
            if (levelTab) {
                p.evoMoves.forEach { mv -> PixText("Evo  ${logTitle(mv)}", 7, if (stab(mv)) Pc.Positive else Pc.Text) }
                p.moves.forEach { (lv, mv) -> PixText("%02d  %s".format(lv, logTitle(mv)), 7, if (stab(mv)) Pc.Positive else Pc.Text) }
            } else {
                LogSearch.tmRows(p, log, frlg, TrackerOptions.logShowUnlearnableGymTms).forEach { r ->
                    val label = r.label
                    if (label != null) {
                        PixText(label, 7, Pc.Gold, Modifier.padding(top = 3.dp))
                    } else {
                        val text = "TM%02d  %s".format(r.number, logTitle(r.move))
                        if (r.unlearnable) {
                            // The reference fades a gym TM it cannot learn and strikes it through in red.
                            PixText(text, 7, Pc.Text.copy(alpha = 0.62f), Modifier.drawWithContent {
                                drawContent()
                                val y = size.height / 2f
                                drawLine(Pc.Negative.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            })
                        } else PixText(text, 7, if (stab(r.move)) Pc.Positive else Pc.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvoIcon(p: RandomizerLog.Pokemon, spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?, onPokemon: ((RandomizerLog.Pokemon) -> Unit)?) {
    Column(
        Modifier.then(if (onPokemon != null) Modifier.clickable { onPokemon(p) } else Modifier).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val art = spriteOf?.invoke(p)
        if (art != null) Image(art, p.name, Modifier.size(32.dp), filterQuality = FilterQuality.None)
        PixText(logTitle(p.name), 7, if (onPokemon == null) Pc.Gold else Pc.Text)
    }
}
