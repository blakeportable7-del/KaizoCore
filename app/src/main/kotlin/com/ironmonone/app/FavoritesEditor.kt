package com.ironmonone.app

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ironmonone.core.RomKind

/**
 * The startup favorites editor, one for every place favorites are entered: the Kaizo IronMON screen's "Startup
 * favorites" before a run (RunScreen, [nextRun] true) and Tracker Setup's EDIT FAVORITES during one ([EditFavoritesRow],
 * [nextRun] false), as the PC tracker's Streamer settings let a player change them without leaving the game
 * (StreamerScreen.lua; Blake approved it for rc35.1). Self-contained: whatever screen holds it passes the store, the game
 * and the mode, nothing more.
 *
 * The favorites of [kind] as the run holds them (Favorites.Scope, HnsPool.favoritesScope): as many boxes as its tracker
 * keeps, only that game's names (MaxDex's own on MaxDex 1.0) and, on Heart & Soul, the pool's: the run in play's in a
 * run, the one chosen now before it. Each box offers names as they are typed, ranked as the log's search ranks them
 * ([FavoriteNameField]), and the rules of [mode]'s book for favorites. Every change saves at once through Favorites.save,
 * which the tracker's favorites row, its ball line (FavoritesShown) and the stream's pictures (StreamFavoritesSource)
 * all follow. Boxes a run does not show (a Vanilla Heart & Soul run's 4 to 9) stay in the file as they were.
 */
@Composable
internal fun FavoritesEditor(store: PrepStore, kind: RomKind?, mode: String?, nextRun: Boolean) {
    val context = LocalContext.current
    val filesDir = context.applicationContext.filesDir
    // The PC tracker's startup favorites: three Pokémon it shows on the
    // new-game screen. Typed by name here; the tracker's no-party card
    // repeats them before a party exists, as the PC trackers' startup and title screens do.
    Text("Startup favorites", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    // The pool chosen on this screen changes Heart & Soul's boxes and names at once (HnsPool.edits).
    val poolEdits = HnsPool.edits.intValue
    val scope = remember(kind?.id, nextRun, poolEdits) { HnsPool.favoritesScope(kind, filesDir, nextRun) }
    val favRomId = kind?.id
    var favSlots by remember(scope, favRomId) { mutableStateOf(Favorites.slots(store, favRomId, scope.stored)) }
    // One full-width box per slot, stacked. Four or five boxes in one
    // row left about 27dp of text each on a DS game (2026-09-27, audit).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until scope.shown) {
            FavoriteNameField(favSlots[i], i, scope) { t ->
                favSlots = favSlots.toMutableList().also { it[i] = t }
                Favorites.save(store, favRomId, favSlots)
            }
        }
    }
    val shownSlots = scope.used(favSlots)
    val favDs = kind?.platform == com.ironmonone.core.Platform.NDS
    // Whether the tracker names a favorite's ball in the mode picked (FavoriteBall).
    val favBall = kind?.platform == com.ironmonone.core.Platform.GBA && mode != null && mode != FavoriteBall.JOURNEY
    Text(
        // The NDS tracker's title screen shows four: a Gen 5 game's five take turns, a Gen 4 game's four stand still.
        if (shownSlots.all { it.isBlank() || Favorites.inGame(it, scope.maxDex, kind) }) (when {
            favDs && scope.shown > 4 -> "The DS tracker keeps ${scope.shown} and shows four at a time on its title screen, in turn."
            favDs -> "The DS tracker keeps four and shows them on its title screen."
            scope.shown > 3 -> "A Nat. Dex game allows ${scope.shown}. The tracker shows them all before your first Pokémon."
            else -> "Shown on the tracker before your first Pokémon, as the PC tracker's startup screen shows them."
        } + if (favBall) " " + RunCopy.FAVORITE_BALL else "")
        else FavoritesCopy.notInGame(scope),
        style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
    )
    FavoritesCopy.keptAside(scope, favSlots)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 4.dp))
    }
    // The run's own rules for favorites, from the book for this game and mode (Blake, 2026-10-01: "base it on
    // whatever game is doing a run because different kaizo's have different rules"). MaxDex's is its own book,
    // with the Nat. Dex 1.1.3 lines; it was reading the Nat. Dex 1.2.1 book. A Vanilla Heart & Soul run reads its book
    // without the Nat. Dex ruleset changes, which only a Nat. Dex pool run is held to (FavoriteRules.forPool).
    val natDexRules = !scope.hnsVanilla
    val favBook = remember(kind?.id, mode, natDexRules) {
        if (kind == null || mode == null) null
        else Rules.text(context, Rules.dirFor(kind.family, kind.isNatDex, kind), mode)?.let { FavoriteRules.forPool(it, natDexRules) }
    }
    FavoriteRulesBlock(favBook, mode, shownSlots)
}

/** What the favorites editor says about a run's pool. */
internal object FavoritesCopy {
    /** Under the boxes when a name is not one the run can have. */
    fun notInGame(scope: Favorites.Scope): String =
        if (scope.hnsVanilla) "A name in red is not a Pokémon of Gens 1 to 3, which a Vanilla run has."
        else "A name in red is not a Pokémon this game has."

    /** For a run that shows fewer boxes than are kept (Vanilla Heart & Soul) and has some of the others filled. */
    fun keptAside(scope: Favorites.Scope, slots: List<String>): String? =
        if (scope.hnsVanilla && slots.drop(scope.shown).any { it.isNotBlank() })
            "Favorites ${scope.shown + 1} to ${scope.stored} are kept for a Nat. Dex run. A Vanilla run uses the first ${scope.shown}."
        else null
}

/**
 * One favorite's box (Blake, 2026-10-06: "have a way to predict what the player will type as they start typing just like
 * we have on the log", "the favorite entry as you set up the game should also have the drop down"). While it has focus
 * and something is typed, a list drops under it: the names of the run's [scope] ranked as the log's search ranks them
 * (Favorites.suggest, LogSuggest.rank), one line each with the Pokémon's picture from the bundled pack and its name. No
 * types or stats: before a run nothing about the randomized game is known, and nothing of it is shown. A tap fills the
 * box and closes the list; the keyboard's Done takes the top name.
 */
@Composable
internal fun FavoriteNameField(value: String, index: Int, scope: Favorites.Scope, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val hits = remember(value, scope) { Favorites.suggestIds(value, limit = FavoriteNames.LIMIT, maxId = scope.maxDex, kind = scope.kind) }
    fun pick(name: String) { dismissed = true; onValue(name); focus.clearFocus() }
    val known = value.isBlank() || Favorites.inGame(value, scope.maxDex, scope.kind)
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { onValue(it); dismissed = false },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
            isError = !known,
            placeholder = { Text("Favorite ${index + 1}") },
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrect = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { hits.firstOrNull()?.let { pick(it.second) } ?: run { dismissed = true; focus.clearFocus() } }),
        )
        if (focused && !dismissed && value.isNotBlank() && hits.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Shell.raised).border(1.dp, Shell.frame)) {
                hits.forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Shell.touchTarget).clickable(role = Role.Button) { pick(name) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val art = remember(id, scope.kind?.id) { FavoriteNames.picture(context, id, scope.kind) }
                        if (art != null) Image(art, null, Modifier.size(40.dp), filterQuality = FilterQuality.None)
                        else Spacer(Modifier.size(40.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, color = Shell.inkOnPaper, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The favorite box's list. */
internal object FavoriteNames {
    /** Lines in the list, as the log's search shows. */
    const val LIMIT = 6

    /** The Pokémon [id] of the game's name table as the bundled pack draws it: MaxDex's own past 411 on MaxDex 1.0. */
    fun picture(context: android.content.Context, id: Int, kind: RomKind?): androidx.compose.ui.graphics.ImageBitmap? =
        runCatching { PcAssets.gbaSprite(context, id, if (kind?.isMaxDex == true) "maxdex" else null) }.getOrNull()
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
    if (open) FavoritesEditorDialog { open = false }
}

/** The run's favorites editor over the game: Tracker Setup's row opens it, and so does the FILE bar's TRACKER sheet. */
@Composable
internal fun FavoritesEditorDialog(onClose: () -> Unit) {
    val filesDir = LocalContext.current.applicationContext.filesDir
    val store = remember { PrepStore(filesDir) }
    val kind = remember { runCatching { store.session().kind }.getOrNull() }
    val mode = remember { runCatching { FavoriteBall.modeOf(store) }.getOrNull() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(320.dp).heightIn(max = 560.dp).background(Shell.paper).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            // The Run screen's words on the Run screen's ground (Shell.paper), whatever color Play's tracker set around it.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Shell.inkOnPaper) {
                FavoritesEditor(store, kind, mode, nextRun = false)
            }
            Spacer(Modifier.height(10.dp))
            GearButton(FavoritesInPlay.DONE) { onClose() }
        }
    }
}
