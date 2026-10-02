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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

/**
 * What the log's search suggests while it is typed (Blake, 2026-10-02: "an actual search bar that suggests pokemon as
 * you type the name"). Pure, so it is tested. Names are compared without case, accents, spaces or punctuation, so
 * "mr mime" finds "Mr. Mime" and "nidoran f" finds "Nidoran♀". Ranked: the whole name, then a name that starts
 * with what was typed, then one of its words that does, then a name that contains it, then a name one slip away
 * from it (a letter missing, extra, wrong or swapped), and Pokedex order within each.
 */
object LogSuggest {
    fun normalize(s: String): String {
        val gendered = s.replace("♀", "f").replace("♂", "m")
        return Normalizer.normalize(gendered, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().filter { it.isLetterOrDigit() }
    }

    /** The rank of [name] for what was typed, lower first; null when it does not match. */
    fun rank(name: String, query: String): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val n = normalize(name)
        val words = name.split(' ', '-', '.', '\'').map { normalize(it) }.filter { it.isNotEmpty() }
        return when {
            n == q -> 0
            n.startsWith(q) -> 1
            words.any { it.startsWith(q) } -> 2
            n.contains(q) -> 3
            q.length >= 3 && withinOneEdit(n.take(q.length), q) || q.length >= 4 && withinOneEdit(n, q) -> 4
            else -> null
        }
    }

    fun pokemon(log: RandomizerLog, query: String, limit: Int = 6): List<RandomizerLog.Pokemon> =
        log.pokemon.mapNotNull { p -> rank(p.name, query)?.let { it to p } }
            .sortedWith(compareBy({ it.first }, { it.second.id }))
            .take(limit).map { it.second }

    /** The same ranking over plain words (abilities, moves, routes, trainers): each once, as first written. */
    fun words(candidates: Iterable<String>, query: String, limit: Int = 6): List<String> {
        val seen = HashSet<String>()
        return candidates.filter { it.isNotBlank() && seen.add(normalize(it)) }
            .mapNotNull { w -> rank(w, query)?.let { it to w } }
            .sortedWith(compareBy({ it.first }, { it.second.lowercase() }))
            .take(limit).map { it.second }
    }

    /** One insertion, deletion, substitution or swap of two neighbours (or none) turns [a] into [b]. */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diff = a.indices.filter { a[it] != b[it] }
            return diff.size == 1 || diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        }
        val (long, short) = if (a.length > b.length) a to b else b to a
        var i = 0
        while (i < short.length && long[i] == short[i]) i++
        return long.substring(i + 1) == short.substring(i)
    }
}

/** One line of the dropdown: a Pokemon (sprite, name, types, BST) or a plain word. */
internal class LogSuggestion(val label: String, val pokemon: RandomizerLog.Pokemon? = null)

/**
 * The log's search box: a magnifier, the hint, what is typed in the tracker's face at a size a phone can read, and a
 * clear button; under it, while the box has focus and something is typed, the suggestions. A tap on one hands it to
 * [onSuggestion]; the keyboard's search key just closes them, leaving the list filtered by what was typed.
 */
@Composable
internal fun LogSearchField(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    suggestions: List<LogSuggestion>,
    spriteOf: ((RandomizerLog.Pokemon) -> ImageBitmap?)?,
    onSuggestion: (LogSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).background(Pc.Page)
                .border(if (focused) 2.dp else 1.dp, if (focused) Pc.Gold else Pc.Border).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Magnifier(Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) DialogText(hint, 15, Pc.Dim)
                BasicTextField(
                    value = query,
                    onValueChange = { onQuery(it); dismissed = false },
                    singleLine = true,
                    textStyle = TextStyle(color = Pc.Text, fontSize = 17.sp, fontFamily = PcFont),
                    cursorBrush = SolidColor(Pc.Gold),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { dismissed = true; focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = hint },
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(PcMin.DIALOG_TOUCH_DP.dp).clickable { onQuery(""); dismissed = false }
                        .semantics { contentDescription = "Clear search" },
                    contentAlignment = Alignment.Center,
                ) { DialogText("X", 14, Pc.Dim) }
            }
        }
        if (focused && !dismissed && query.isNotBlank() && suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Gold)) {
                suggestions.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
                            .clickable { dismissed = true; focus.clearFocus(); onSuggestion(s) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val p = s.pokemon
                        if (p != null) {
                            val art = spriteOf?.invoke(p)
                            if (art != null) Image(art, null, Modifier.size(40.dp), filterQuality = FilterQuality.None)
                            else Spacer(Modifier.size(40.dp))
                        }
                        DialogText(if (p != null) logTitle(p.name) else s.label, 15, Pc.Text, Modifier.weight(1f))
                        if (p != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                p.types.forEach { PcTypeChip(it.uppercase(), pcTypeColorByName(it)) }
                            }
                            DialogText("${p.bst}", 13, Pc.Gold, Modifier.widthIn(min = 34.dp), align = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

/** A log page's way back: a full touch target, in the tracker's face. */
@Composable
internal fun LogBack(onBack: () -> Unit) {
    Box(
        Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable { onBack() }.padding(horizontal = 12.dp)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) { DialogText("< Back", 13, Pc.Dim) }
}

/** The search box's magnifier, drawn so it needs no glyph the tracker's font may not have. */
@Composable
private fun Magnifier(modifier: Modifier) {
    val c = Pc.Dim
    Canvas(modifier) {
        val r = size.minDimension * 0.34f
        val centre = Offset(r + 1.5f, r + 1.5f)
        drawCircle(c, r, centre, style = Stroke(width = size.minDimension * 0.12f))
        val d = r * 0.72f
        drawLine(c, Offset(centre.x + d, centre.y + d), Offset(size.width - 1f, size.height - 1f), strokeWidth = size.minDimension * 0.14f)
    }
}
