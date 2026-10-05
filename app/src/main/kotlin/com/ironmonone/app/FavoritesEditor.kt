package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.core.RomKind

/**
 * The startup favorites editor, one for both places it opens: the Kaizo IronMON screen's "Startup favorites" (RunScreen)
 * and, since rc35.1, Tracker Setup's EDIT FAVORITES during a run, as the PC tracker's Streamer settings let a player
 * change them without leaving the game (StreamerScreen.lua; Blake approved it for rc35.1).
 *
 * The favorites of [kind], the game picked or in play: as many boxes as its tracker keeps (Favorites.slotCount), only
 * that game's names (MaxDex's own on MaxDex 1.0), the suggestions as a name is typed, and the rules of [mode]'s book for
 * favorites. Every change saves at once through Favorites.save, which the tracker's favorites row, its ball line
 * (FavoritesShown) and the stream's pictures (StreamFavoritesSource) all follow.
 */
@Composable
internal fun FavoritesEditor(store: PrepStore, kind: RomKind?, mode: String?) {
    val context = LocalContext.current
    // The PC tracker's startup favorites: three Pokémon it shows on the
    // new-game screen. Typed by name here; the tracker's no-party card
    // repeats them before a party exists, as the PC trackers' startup and title screens do.
    Text("Startup favorites", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    // As many boxes as the game's PC tracker keeps, and only that game's dex in the list.
    val favCount = Favorites.slotCount(kind)
    val favMax = Favorites.maxDex(kind)
    // The game's own names: MaxDex's on MaxDex 1.0, whose Z-A Megas have ids of their own (Favorites.idOf with a game).
    val favRomId = kind?.id
    var favSlots by remember(favCount, favRomId) { mutableStateOf(Favorites.slots(store, favRomId, favCount)) }
    // Which box is being typed in: its suggestions show under the row.
    var favActive by remember { mutableStateOf(-1) }
    // One full-width box per slot, stacked. Four or five boxes in one
    // row left about 27dp of text each on a DS game (2026-09-27, audit).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        favSlots.forEachIndexed { i, v ->
            // Known FOR THIS GAME: a name past its dex (a Gen 5 species on a standard Emerald) is as wrong as a typo.
            val known = v.isBlank() || Favorites.inGame(v, favMax, kind)
            androidx.compose.material3.OutlinedTextField(
                value = v,
                onValueChange = { t ->
                    favSlots = favSlots.toMutableList().also { it[i] = t }
                    favActive = i
                    Favorites.save(store, favRomId, favSlots)
                },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) favActive = i },
                singleLine = true,
                isError = !known,
                placeholder = { Text("Favorite ${i + 1}") },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    // The names that start with what is typed in the active box, narrowing
    // with every letter; a tap fills the box. Dex order, eight at most.
    val favHints = if (favActive in favSlots.indices) Favorites.suggest(favSlots[favActive], maxId = favMax, kind = kind) else emptyList()
    if (favHints.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            favHints.forEach { name ->
                // As the name is written: upper-casing it and passing it
                // through Shell.label mangled "Mr. Mime" and "Ho-Oh".
                com.ironmonone.app.gen3.Gen3Button(name, raw = true) {
                    favSlots = favSlots.toMutableList().also { it[favActive] = name }
                    Favorites.save(store, favRomId, favSlots)
                    favActive = -1
                }
            }
        }
    }
    val favDs = kind?.platform == com.ironmonone.core.Platform.NDS
    // Whether the tracker names a favorite's ball in the mode picked (FavoriteBall).
    val favBall = kind?.platform == com.ironmonone.core.Platform.GBA && mode != null && mode != FavoriteBall.JOURNEY
    Text(
        // The NDS tracker's title screen shows four: a Gen 5 game's five take turns, a Gen 4 game's four stand still.
        if (favSlots.all { it.isBlank() || Favorites.inGame(it, favMax, kind) }) (when {
            favDs && favCount > 4 -> "The DS tracker keeps $favCount and shows four at a time on its title screen, in turn."
            favDs -> "The DS tracker keeps four and shows them on its title screen."
            favCount > 3 -> "A Nat. Dex game allows $favCount. The tracker shows them all before your first Pokémon."
            else -> "Shown on the tracker before your first Pokémon, as the PC tracker's startup screen shows them."
        } + if (favBall) " " + RunCopy.FAVORITE_BALL else "")
        else "A name in red is not a Pokémon this game has.",
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
    )
    // The run's own rules for favorites, from the book for this game and mode (Blake, 2026-10-01: "base it on
    // whatever game is doing a run because different kaizo's have different rules"). MaxDex's is its own book,
    // with the Nat. Dex 1.1.3 lines; it was reading the Nat. Dex 1.2.1 book.
    val favBook = remember(kind?.id, mode) {
        if (kind == null || mode == null) null else Rules.text(context, Rules.dirFor(kind.family, kind.isNatDex, kind), mode)
    }
    FavoriteRulesBlock(favBook, mode, favSlots)
}

/** Tracker Setup's EDIT FAVORITES: where it shows, and its words. */
internal object FavoritesInPlay {
    const val ROW = "EDIT FAVORITES"
    const val DONE = "DONE"

    /**
     * Only in a Kaizo IronMON run, as the Favorites Clause and the Run screen's favorites are: a Nuzlocke and a library
     * game have none.
     */
    fun offered(scope: GearScope): Boolean = scope.ironmon
}

/**
 * The row in Tracker Setup and the editor it opens over the game, for the game in play (PrepStore.session) and the run's
 * mode (FavoriteBall.modeOf). Self-contained, so Tracker Setup carries one line for it.
 */
@Composable
internal fun EditFavoritesRow() {
    var open by remember { mutableStateOf(false) }
    GearButton(FavoritesInPlay.ROW) { open = true }
    if (!open) return
    val filesDir = LocalContext.current.applicationContext.filesDir
    val store = remember { PrepStore(filesDir) }
    val kind = remember { runCatching { store.session().kind }.getOrNull() }
    val mode = remember { runCatching { FavoriteBall.modeOf(store) }.getOrNull() }
    androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }) {
        Column(
            Modifier.width(320.dp).heightIn(max = 560.dp).background(Shell.paper).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            // The Run screen's words on the Run screen's ground (Shell.paper), whatever color Play's tracker set around it.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Shell.inkOnPaper) {
                FavoritesEditor(store, kind, mode)
            }
            Spacer(Modifier.height(10.dp))
            GearButton(FavoritesInPlay.DONE) { open = false }
        }
    }
}
