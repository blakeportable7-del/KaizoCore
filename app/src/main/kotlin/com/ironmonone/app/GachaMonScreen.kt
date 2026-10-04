package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonCodec
import com.ironmonone.tracker.gachamon.GachaMonRatingSystem

/** GachaMonOverlay's tabs, in its order. The Battle tab is not here: the reference hides it, "coming soon". */
enum class GachaMonTab(val label: String) {
    CAPTURES("Captures"), COLLECTION("Collection"), VIEW("View"), GACHADEX("GachaDex"), OPTIONS("Options"), ABOUT("About"),
}

/** The words of the GachaMon screen. Plain, American spelling; the PC tracker's where it has them. */
object GachaMonText {
    const val TITLE = "GachaMon"
    const val HELP_1 = "Here are GachaMons you've captured this game."
    const val HELP_2 = "Tap Add to Collection to keep them forever."
    const val NO_CAPTURES = "No GachaMon yet this run"
    const val NO_CAPTURES_DETAIL = "Each Pokémon that leads your party outside a battle in a Kaizo IronMON run on a Game Boy Advance game becomes a card here."
    const val NO_COLLECTION = "Your collection is empty"
    const val NO_COLLECTION_DETAIL = "Keep a card from Captures, or add one from a share code in Options."
    const val NO_VIEW = "No card to view"
    const val NO_VIEW_DETAIL = "Tap a card in Captures or Collection to see it here."
    const val SORT = "Sort"
    const val FILTER = "Filter"
    const val FAVORITE = "Favorite"
    const val IN_COLLECTION = "In Collection"
    const val ADD_TO_COLLECTION = "Add to Collection"
    const val SHARE = "Share code"
    const val NOW = "As it is now"
    const val AS_MADE = "As it was made"
    const val ON_CAPTURE = "When a new GachaMon is captured, add to collection if"
    const val IF_NEW = "It's a new Pokémon species"
    const val IF_TRAINERS = "It defeats at least 2 trainers"
    const val RULESET = "Ruleset used for ratings"
    const val AUTO = "Auto"
    const val SHOW_STARS = "Display stars next to heals"
    const val SHOW_PACK = "Show card pack opening before Pokémon stats"
    const val ANIMATE = "Animate card pack opening"
    const val PRIZES = "Occasionally receive prize cards from Trainers"
    const val COLLECTION_SIZE = "GachaMons in collection"
    const val CLEANUP = "Clean up collection"
    const val ADD_CODE = "Add a card from a code"
    const val IMPORT_PC = "Import a PC tracker collection"
    const val HEADER = "GachaMon Collectable Card Game"
    const val SLOGAN = "Play IronMON, collect GachaMon cards!"
    const val HOW = "How it works"
    val STEPS = listOf("Catch Pokémon", "Acquire GachaMon cards", "Keep cards in your Collection", "Battle! (coming soon)")
    const val WHATS_ON = "What's on a card"
    const val STARS_LINE = "Stars: the Pokémon's rating (1 to 5, and 5+)"
    const val POWER_LINE = "Battle Power: its strength for card battles"
    const val SAMPLE = "A sample card. Tap it for another."
    const val HELP_LINK = "Help (the tracker's GachaMon page)"
    const val HELP_URL = "https://github.com/besteon/Ironmon-Tracker/wiki/GachaMon-Collectable-Card-Game"
    const val SEEN = "Seen"
    const val COLLECTED = "Collected"
    const val SHOW_ALL = "Show every Pokémon"
    const val CREDIT = "GachaMon is from Besteon's Ironmon-Tracker, ported with its ratings unchanged, so a card rates here as it does there."
}

/**
 * GachaMonOverlay (screens/GachaMonOverlay.lua), in KaizoCore's own look: Captures (this run's cards), Collection,
 * View (one card up close), GachaDex, Options and About. A phone scrolls where the PC tracker pages six at a time.
 */
@Composable
fun GachaMonScreen(start: GachaMonStart, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) { GachaMonScreenContent(start, onDismiss) }
}

/** The GachaMon screen's page, in its own window or (for the look test) in any. */
@Composable
fun GachaMonScreenContent(start: GachaMonStart, onDismiss: () -> Unit) {
    val filesDir = LocalContext.current.applicationContext.filesDir
    remember { GachaMon.ensureLoaded(filesDir); true }
    // GachaMonOverlay.open: with no cards at all it opens on About.
    var tab by remember {
        mutableStateOf(if (start.card == null && start.tab == GachaMonTab.CAPTURES && GachaMon.recent.isEmpty() && GachaMon.collection.isEmpty()) GachaMonTab.ABOUT else start.tab)
    }
    var viewed by remember { mutableStateOf(start.card ?: GachaMon.newest ?: GachaMon.recent.lastOrNull() ?: GachaMon.collection.firstOrNull()) }
    /** A card read from a code, not in the collection yet (Temp.PreventSaving). */
    var preview by remember { mutableStateOf<GachaMonEntry?>(null) }
    val captures = remember { GachaMonListState(recent = true) }
    val collected = remember { GachaMonListState(recent = false) }
    fun view(e: GachaMonEntry) { viewed = e; preview = null; tab = GachaMonTab.VIEW }
    run {
        Column(Modifier.fillMaxSize().background(Shell.night).padding(horizontal = 12.dp, vertical = 8.dp)) {
            DialogTitle(GachaMonText.TITLE, onDismiss)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GachaMonTab.entries.forEach { t -> GachaChip(t.label, tab == t) { tab = t } }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    GachaMonTab.CAPTURES -> GachaMonListTab(captures, GachaMon.recent, ::view)
                    GachaMonTab.COLLECTION -> GachaMonListTab(collected, GachaMon.collection, ::view)
                    GachaMonTab.VIEW -> GachaMonViewTab(preview ?: viewed?.let { GachaMon.current(it) }, preview != null,
                        onAdded = { e -> preview = null; viewed = e }, onRemoved = { tab = GachaMonTab.COLLECTION })
                    GachaMonTab.GACHADEX -> GachaMonDexTab { species -> collected.species = species; tab = GachaMonTab.COLLECTION }
                    GachaMonTab.OPTIONS -> GachaMonOptionsTab(onPreview = { e -> preview = e; tab = GachaMonTab.VIEW })
                    GachaMonTab.ABOUT -> GachaMonAboutTab()
                }
            }
        }
    }
}

/** A tab or sort chip: filled when chosen, a radio to a screen reader, 48dp tall. */
@Composable
private fun GachaChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Shell.inkOnPaper else Shell.raised)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = Shell.touchTarget).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) Shell.night else Shell.inkOnPaper) }
}

// ------------------------------------------------------------------ Captures and Collection

/** One list tab's sort and filter (Data.Recent / Data.Collection's SortFunc and FilterFunc). */
class GachaMonListState(val recent: Boolean) {
    var sort by mutableStateOf(GachaSort.DEFAULT)
    var filter by mutableStateOf<GachaFilter?>(null)
    /** The GachaDex's tap on a collected species: the collection shows that species only. */
    var species by mutableStateOf<Int?>(null)
}

enum class GachaSort(val label: String) { DEFAULT(""), STARS("Stars"), POWER("BP"), DATE("Date") }

/** openFilterSettingsWindow's choices: stars, games, favorites, shiny, and a Pokemon's name. */
data class GachaFilter(
    val stars: Set<Int> = (1..6).toSet(),
    val games: Set<Int> = (1..5).toSet(),
    val favorites: Set<Int> = setOf(0, 1),
    val shiny: Set<Int> = setOf(0, 1),
    val name: String = "",
) {
    fun matches(e: GachaMonEntry): Boolean = e.stars in stars && e.card.gameVersion in games && e.card.favorite in favorites &&
        e.card.isShiny in shiny && (name.isBlank() || e.speciesName.contains(name.trim(), ignoreCase = true))
}

/** The date a card was collected, as one number (C_DateObtained's bits), and when it was made as the tie-break. */
private fun dateKey(e: GachaMonEntry): Long = GachaMonCodec.dateBits(e.card.year, e.card.month, e.card.day).toLong() * 10_000_000_000_000L + e.notes.at

/** GachaMonOverlay.SortFuncs, stable, newest first among equals. */
internal fun gachaSorted(list: List<GachaMonEntry>, sort: GachaSort, recent: Boolean): List<GachaMonEntry> {
    val newestFirst = list.withIndex().sortedByDescending { it.index }.map { it.value }
    return when (sort) {
        // DefaultRecentSort: the newest capture first. DefaultCollectionSort: favorites, then the newest date.
        GachaSort.DEFAULT -> if (recent) newestFirst.sortedByDescending { it.notes.at }
            else newestFirst.sortedWith(compareByDescending<GachaMonEntry> { it.card.favorite }.thenByDescending { dateKey(it) })
        GachaSort.STARS -> newestFirst.sortedByDescending { it.stars }
        GachaSort.POWER -> newestFirst.sortedByDescending { it.card.battlePower }
        GachaSort.DATE -> newestFirst.sortedByDescending { dateKey(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GachaMonListTab(state: GachaMonListState, source: List<GachaMonEntry>, onView: (GachaMonEntry) -> Unit) {
    var filterOpen by remember { mutableStateOf(false) }
    val shown = gachaSorted(source.filter { e -> (state.filter?.matches(e) ?: true) && (state.species == null || e.card.pokemonId == state.species) }, state.sort, state.recent)
    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("${GachaMonText.SORT}:", color = Shell.hintOnNight, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
            for (s in listOf(GachaSort.STARS, GachaSort.POWER, GachaSort.DATE)) {
                // A second tap goes back to the tab's own order, as the reference's sort buttons do.
                GachaChip(s.label, state.sort == s) { state.sort = if (state.sort == s) GachaSort.DEFAULT else s }
            }
            GachaChip(if (state.filter != null || state.species != null) "${GachaMonText.FILTER} (on)" else GachaMonText.FILTER, state.filter != null || state.species != null) { filterOpen = true }
            Text("${shown.size} of ${source.size}", color = Shell.hintOnNight, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(6.dp))
        if (state.recent && source.size <= 3) {
            // UsageHelpText: shown while three or fewer GachaMons have been captured
            Text("- ${GachaMonText.HELP_1}", color = Shell.hintOnNight, fontSize = 13.sp)
            Text("- ${GachaMonText.HELP_2}", color = Shell.hintOnNight, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (source.isEmpty()) {
            if (state.recent) EmptyState(GachaMonText.NO_CAPTURES, GachaMonText.NO_CAPTURES_DETAIL)
            else EmptyState(GachaMonText.NO_COLLECTION, GachaMonText.NO_COLLECTION_DETAIL)
        } else LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp), contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.uid }) { e ->
                // The Captures tab marks the cards kept in the collection (drawGachaCard's checkmark); a favorite has its heart.
                GachaMonCardFace(e, 108.dp, Modifier.clickable(role = Role.Button, onClickLabel = "View this card") { onView(e) },
                    collected = state.recent && e.card.keep == 1)
            }
        }
    }
    if (filterOpen) GachaFilterDialog(state) { filterOpen = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GachaFilterDialog(state: GachaMonListState, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(state.filter ?: GachaFilter()) }
    fun <T> toggle(set: Set<T>, v: T): Set<T> = if (v in set) set - v else set + v
    ShellDialog("Filter GachaMon: " + if (state.recent) "Captures" else "Collection", onDismiss) {
        Text("Show GachaMon cards with these qualities:", color = Shell.inkOnPaper, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Stars", color = Shell.hintOnPaper, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in 1..6) GachaCheckChip(if (s == 6) "5+" else "$s", s in f.stars) { f = f.copy(stars = toggle(f.stars, s)) }
        }
        Text("Game", color = Shell.hintOnPaper, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in 1..5) GachaCheckChip(GachaMonCard.GAME_NAMES[g - 1], g in f.games) { f = f.copy(games = toggle(f.games, g)) }
        }
        Text("Favorite and shiny", color = Shell.hintOnPaper, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GachaCheckChip("Favorites", 1 in f.favorites) { f = f.copy(favorites = toggle(f.favorites, 1)) }
            GachaCheckChip("Not favorites", 0 in f.favorites) { f = f.copy(favorites = toggle(f.favorites, 0)) }
            GachaCheckChip("Shiny", 1 in f.shiny) { f = f.copy(shiny = toggle(f.shiny, 1)) }
            GachaCheckChip("Not shiny", 0 in f.shiny) { f = f.copy(shiny = toggle(f.shiny, 0)) }
        }
        GachaTextField(f.name, "By Pokémon (name)") { f = f.copy(name = it) }
        if (state.species != null) Text("Showing one species from the GachaDex.", color = Shell.hintOnPaper, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("Apply filters", accent = true, raw = true) { state.filter = f; onDismiss() }
            Gen3Button("Reset", raw = true) { state.filter = null; state.species = null; onDismiss() }
        }
    }
}

@Composable
private fun GachaCheckChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Shell.raised)
            .clickable(role = Role.Checkbox, onClick = onClick).heightIn(min = Shell.touchTarget).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellCheck(on)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = Shell.inkOnPaper)
    }
}

@Composable
private fun GachaTextField(value: String, label: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

// ------------------------------------------------------------------ View

private val NATURE_NAMES = listOf(
    "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax", "Timid", "Hasty",
    "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky",
)

/** "69 points (5 stars)": the View tab's rating line, "5+" for the top card. */
fun gachaRatingLine(c: GachaMonCard): String {
    val stars = c.stars()
    return "${c.ratingScore} points (${if (stars > 5) "5+" else "$stars"} stars)"
}

/** os.date("%x"): the date collected, 07/22/26. */
fun gachaDateText(c: GachaMonCard): String = "%02d/%02d/%02d".format(c.month, c.day, c.year % 100)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GachaMonViewTab(e: GachaMonEntry?, isPreview: Boolean, onAdded: (GachaMonEntry) -> Unit, onRemoved: () -> Unit) {
    if (e == null) { EmptyState(GachaMonText.NO_VIEW, GachaMonText.NO_VIEW_DETAIL); return }
    val filesDir = LocalContext.current.applicationContext.filesDir
    var shareOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    /** The arrows' card made again from the lead as it is now; null shows the card as made. */
    var now by remember(e.uid) { mutableStateOf<GachaMonEntry?>(null) }
    val recalculated = remember(e.uid, GachaMon.liveState) { if (isPreview) null else GachaMon.recalculated(e, filesDir) }
    val shown = now ?: e
    val c = shown.card
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                GachaMonCardFace(shown, 176.dp)
                Text("v${c.version}", color = Shell.hintOnNight, fontSize = 12.sp)
            }
            Column(Modifier.width(220.dp)) {
                val name = shown.trainerName?.let { "$it's ${shown.speciesName}" } ?: shown.speciesName
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.uppercase(), color = Shell.accentOnNight, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
                    when (c.gender) {
                        1 -> Text("  ♂", color = Color(0xFF6890F0), fontSize = 18.sp)
                        2 -> Text("  ♀", color = Color(0xFFF85888), fontSize = 18.sp)
                    }
                }
                GachaInfoLine(NATURE_NAMES.getOrElse(c.nature) { "?" }, "Lv. ${c.level}")
                GachaInfoLine("Rating:", gachaRatingLine(c))
                GachaInfoLine("Battle Power:", "${c.battlePower} BP")
                GachaInfoLine("Collected on:", gachaDateText(c))
                GachaInfoLine("", c.gameName)
                GachaInfoLine("", "Seed %,d".format(c.seedNumber))
                if (c.isShiny == 1) GachaInfoLine("", "Shiny")
                if (c.gameWinner == 1) GachaInfoLine("", "Won the game")
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(150.dp)) {
                Text("Stats", color = Shell.accentOnNight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val s = c.stats
                listOf("HP" to ("hp" to s.hp), "ATK" to ("atk" to s.atk), "DEF" to ("def" to s.def), "SPA" to ("spa" to s.spa),
                    "SPD" to ("spd" to s.spd), "SPE" to ("spe" to s.spe)).forEach { (label, kv) ->
                    val m = GachaMonRatingSystem.natureMultiplier(kv.first, c.nature)
                    val color = if (m > 1) Shell.goodOnNight else if (m < 1) Shell.dangerOnNight else Shell.textOnNight
                    val mark = if (m > 1) "+" else if (m < 1) "-" else ""
                    Row(Modifier.fillMaxWidth()) {
                        Text("$label$mark", color = color, fontSize = 14.sp, modifier = Modifier.width(56.dp))
                        Text(if (kv.second == 0) "---" else "${kv.second}", color = if (TrackerOptions.colorStatNumbers) color else Shell.textOnNight, fontSize = 14.sp)
                    }
                }
            }
            Column(Modifier.width(210.dp)) {
                Text("Moves", color = Shell.accentOnNight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val live = GachaMon.game
                for (i in 0 until 4) {
                    val id = shown.moveIds.getOrNull(i) ?: 0
                    if (id <= 0) { Text("---", color = Shell.hintOnNight, fontSize = 14.sp); continue }
                    val power = shown.notes.powers.getOrNull(i) ?: live?.moveRowFor(id)?.power?.toString() ?: ""
                    Row(Modifier.fillMaxWidth()) {
                        Text(shown.moveName(i), color = Shell.textOnNight, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (power == "0" || power.isBlank()) "---" else power, color = Shell.textOnNight, fontSize = 14.sp)
                    }
                }
            }
        }
        if (c.badges > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Badges it helped win", color = Shell.hintOnNight, fontSize = 13.sp)
            val ctx = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..8) {
                    val earned = (c.badges shr (i - 1)) and 1 == 1
                    val bmp = remember(c.gameVersion, i, earned) { PcAssets.badge(ctx, gachaBadgeSet(c.gameVersion), i, earned) }
                    if (bmp != null) Image(bmp, contentDescription = if (earned) "Badge $i" else null, filterQuality = FilterQuality.None,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isPreview) {
                Gen3Button(GachaMonText.ADD_TO_COLLECTION, accent = true, raw = true) { onAdded(GachaMon.import(e.card)) }
            } else if (now == null) {
                Gen3Button(if (c.favorite == 1) "♥ ${GachaMonText.FAVORITE}" else "♡ ${GachaMonText.FAVORITE}", raw = true) {
                    // Favoriting a card not in the collection keeps it there too (GachaMonOverlay Favorite.onClick).
                    val fave = c.favorite != 1
                    GachaMon.update(e, favorite = fave, keep = if (fave) true else null)
                }
                if (c.keep == 1) Gen3Button("✓ ${GachaMonText.IN_COLLECTION}", raw = true) {
                    if (GachaMon.isRecent(e)) GachaMon.removeFromCollection(e) else confirmRemove = true
                } else Gen3Button(GachaMonText.ADD_TO_COLLECTION, accent = true, raw = true) { GachaMon.update(e, keep = true) }
            }
            if (!isPreview) Gen3Button(GachaMonText.SHARE, raw = true) { shareOpen = true }
            if (recalculated != null || now != null) Gen3Button(if (now == null) GachaMonText.NOW else GachaMonText.AS_MADE, raw = true) {
                now = if (now == null) recalculated else null
            }
        }
        if (now != null) Text("The card made again from your lead as it is now. It is not saved.", color = Shell.hintOnNight, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp))
    }
    if (shareOpen) GachaShareDialog(e) { shareOpen = false }
    if (confirmRemove) ShellDialog("Remove from your collection?", { confirmRemove = false }) {
        // openGachaMonRemovalConfirmation: the card's name, stars, power, date, seed and game, and the warning.
        Text("This will remove this GachaMon from your collection for good:", color = Shell.inkOnPaper, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text("${e.speciesName}, ${gachaStarsText(minOf(e.stars, 5))}, ${c.battlePower} Battle Power", color = Shell.inkOnPaper, fontSize = 14.sp)
        Text("${gachaDateText(c)}, ${c.gameName}, seed %,d".format(c.seedNumber), color = Shell.hintOnPaper, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gen3Button("Remove it", accent = true, raw = true) { GachaMon.removeFromCollection(e); confirmRemove = false; onRemoved() }
            Gen3Button("Keep it", raw = true) { confirmRemove = false }
        }
    }
}

@Composable
private fun GachaInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = Shell.hintOnNight, fontSize = 14.sp, modifier = Modifier.width(100.dp))
        Text(value, color = Shell.textOnNight, fontSize = 14.sp)
    }
}

/** openShareCodeWindow, for a phone: the card's code to copy, which the PC tracker reads too. */
@Composable
private fun GachaShareDialog(e: GachaMonEntry, onDismiss: () -> Unit) {
    val code = remember(e.card) { GachaMonCodec.shareCode(e.card) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    ShellDialog(GachaMonText.SHARE, onDismiss) {
        Text("Send this code to a friend. Their KaizoCore, or their PC tracker, reads it as this card.", color = Shell.inkOnPaper,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(code, color = Shell.inkOnPaper, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(Shell.night, RoundedCornerShape(8.dp)).padding(10.dp))
        }
        if (e.notes.moveIds.isNotEmpty() || e.notes.abilityId > 0)
            Text("A move or ability past what the code holds goes as none.", color = Shell.hintOnPaper, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Gen3Button(if (copied) "Copied" else "Copy the code", accent = true, raw = true) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)); copied = true
        }
    }
}

// ------------------------------------------------------------------ GachaDex

@Composable
private fun GachaMonDexTab(onCollected: (Int) -> Unit) {
    val t = GachaMon.game
    val dex = if (t?.nameSet == "maxdex") "maxdex" else ""
    val total = when {
        t == null -> 411
        t.nameSet == "maxdex" -> 1280
        t.expandedSpeciesIds -> 1283
        else -> 411
    }
    @Suppress("UNUSED_VARIABLE") val version = GachaMon.dexVersion
    val ids = remember(total) { (1..total).filter { it !in 252..276 } }
    val collectedIds = (GachaMon.collection.map { it.card.pokemonId } + GachaMon.recent.filter { it.card.keep == 1 }.map { it.card.pokemonId }).toSet()
    val seenIds = GachaMon.seen(dex) + collectedIds
    var showAll by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<Int?>(null) }
    val numCollected = ids.count { it in collectedIds }
    val dexTotal = ids.size
    // buildGachaDexData: the share of species collected, rounded down, at most 100
    val percent = if (dexTotal > 0) minOf(numCollected * 100 / dexTotal, 100) else 0
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${GachaMonText.SEEN}: ${ids.count { it in seenIds }}", color = Shell.textOnNight, fontSize = 14.sp)
            Text("${GachaMonText.COLLECTED}: $numCollected / $dexTotal", color = if (numCollected >= dexTotal) Shell.goodOnNight else Shell.textOnNight, fontSize = 14.sp)
            Text("$percent%", color = if (percent >= 100) Shell.goodOnNight else Shell.accentOnNight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        ShellSwitchRow(GachaMonText.SHOW_ALL, showAll) { showAll = it; revealed = null }
        LazyVerticalGrid(columns = GridCells.Adaptive(58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(ids) { id ->
                val base = remember(id, t) { t?.baseStats(id) }
                GachaMiniCard(id, base?.type1, base?.type2, seen = id in seenIds, collected = id in collectedIds,
                    reveal = showAll || revealed == id, width = 58.dp, dex = dex,
                    modifier = Modifier.clickable(role = Role.Button) {
                        if (id in collectedIds) onCollected(id) else revealed = if (revealed == id) null else id
                    })
            }
        }
    }
}

// ------------------------------------------------------------------ Options

@Composable
private fun GachaMonOptionsTab(onPreview: (GachaMonEntry) -> Unit) {
    val filesDir = LocalContext.current.applicationContext.filesDir
    var rulesetOpen by remember { mutableStateOf(false) }
    var cleanupOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    fun save() = GachaMon.saveOptions()
    val natDex = GachaMon.game?.expandedSpeciesIds == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column {
                Text("${GachaMonText.ON_CAPTURE}...", color = Shell.inkOnPaper, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                ShellSwitchRow(GachaMonText.IF_NEW, GachaMonOptions.addIfNew) { GachaMonOptions.addIfNew = it; save() }
                ShellSwitchRow(GachaMonText.IF_TRAINERS, GachaMonOptions.addAfterTrainers) { GachaMonOptions.addAfterTrainers = it; save() }
                Spacer(Modifier.height(6.dp))
                val key = GachaMonOptions.ruleset
                val label = if (key == "AutoDetect") "${GachaMonRulesets.name(GachaMon.rulesetKey(filesDir, natDex))} (${GachaMonText.AUTO})" else GachaMonRulesets.name(key)
                ShellListRow("${GachaMonText.RULESET}:", label, onClick = { rulesetOpen = true })
            }
        }
        Spacer(Modifier.height(8.dp))
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column {
                ShellSwitchRow(GachaMonText.SHOW_STARS, GachaMonOptions.showStars,
                    detail = "Uses the place of Track PC Heals, which goes off.".takeIf { TrackerOptions.trackPcHeals }) { GachaMonOptions.chooseShowStars(it) }
                ShellSwitchRow(GachaMonText.SHOW_PACK, GachaMonOptions.showPack) { GachaMonOptions.showPack = it; save() }
                ShellSwitchRow(GachaMonText.ANIMATE, GachaMonOptions.animatePack) { GachaMonOptions.animatePack = it; save() }
                ShellSwitchRow(GachaMonText.PRIZES, GachaMonOptions.prizeCards,
                    detail = "A prize card from a beaten trainer after a game over, kept in your collection.") { GachaMonOptions.prizeCards = it; save() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            Column {
                ShellListRow("${GachaMonText.COLLECTION_SIZE}:", "${GachaMon.collection.size}")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (GachaMon.collection.isNotEmpty()) Gen3Button(GachaMonText.CLEANUP, raw = true) { cleanupOpen = true }
                    Gen3Button(GachaMonText.ADD_CODE, raw = true) { importOpen = true }
                }
                Spacer(Modifier.height(6.dp))
                GachaPcCollectionImport()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (rulesetOpen) GachaRulesetDialog(natDex) { rulesetOpen = false }
    if (cleanupOpen) GachaCleanupDialog { cleanupOpen = false }
    if (importOpen) GachaImportDialog(onPreview = { importOpen = false; onPreview(it) }) { importOpen = false }
}

/** openEditRulesetWindow: find it from the run's settings, or one of the rulesets (Ascension 1 to 3 on Nat. Dex). */
@Composable
private fun GachaRulesetDialog(natDex: Boolean, onDismiss: () -> Unit) {
    val filesDir = LocalContext.current.applicationContext.filesDir
    var auto by remember { mutableStateOf(GachaMonOptions.ruleset == "AutoDetect") }
    var pick by remember { mutableStateOf(GachaMonOptions.ruleset.takeIf { it != "AutoDetect" } ?: "Standard") }
    ShellDialog(GachaMonText.RULESET, onDismiss) {
        Text("Which ruleset the ratings and stars are worked out under:", color = Shell.inkOnPaper, style = MaterialTheme.typography.bodyMedium)
        ShellSwitchRow("Find it from the run's settings", auto,
            detail = "This run's: " + GachaMonRulesets.name(GachaMon.detectedRuleset(filesDir, natDex))) { auto = it }
        if (!auto) GachaMonRulesets.NAMES.filter { natDex || !it.first.startsWith("Ascension") }.forEach { (key, name) ->
            Row(Modifier.fillMaxWidth().selectable(selected = pick == key, role = Role.RadioButton) { pick = key }.heightIn(min = Shell.touchTarget),
                verticalAlignment = Alignment.CenterVertically) {
                ShellRadio(pick == key); Spacer(Modifier.width(10.dp)); Text(name, color = Shell.inkOnPaper, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Gen3Button("Save", accent = true, raw = true) {
            GachaMonOptions.ruleset = if (auto) "AutoDetect" else pick
            GachaMon.saveOptions(); onDismiss()
        }
    }
}

/** openCleanupCollectionWindow: what to remove for good; favorites are never removed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GachaCleanupDialog(onDismiss: () -> Unit) {
    var stars by remember { mutableStateOf(emptySet<Int>()) }
    var nonFavorites by remember { mutableStateOf(false) }
    var shiny by remember { mutableStateOf(emptySet<Int>()) }
    var less by remember { mutableStateOf("") }
    var greater by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("") }
    fun <T> toggle(set: Set<T>, v: T): Set<T> = if (v in set) set - v else set + v
    fun changed() { found = null; message = "" }
    // _buildRemovalFilterFunc: a group with nothing chosen does not narrow; nothing chosen at all removes nothing.
    fun matcher(): (GachaMonEntry) -> Boolean {
        val lessV = less.trim().toIntOrNull(); val greaterV = greater.trim().toIntOrNull()
        if (stars.isEmpty() && !nonFavorites && shiny.isEmpty() && lessV == null && greaterV == null) return { false }
        return { e ->
            (stars.isEmpty() || e.stars in stars) && (!nonFavorites || e.card.favorite == 0) && (shiny.isEmpty() || e.card.isShiny in shiny) &&
                (lessV == null || e.card.battlePower < lessV) && (greaterV == null || e.card.battlePower > greaterV)
        }
    }
    ShellDialog(GachaMonText.CLEANUP, onDismiss) {
        Text("Choose which GachaMon cards to remove from your collection for good. Favorites are safe: cleanup never removes them.",
            color = Shell.inkOnPaper, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text("Stars", color = Shell.hintOnPaper, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (s in 1..5) GachaCheckChip("$s", s in stars) { stars = toggle(stars, s); changed() } }
        Text("Favorite and shiny", color = Shell.hintOnPaper, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GachaCheckChip("Not favorites", nonFavorites) { nonFavorites = !nonFavorites; changed() }
            GachaCheckChip("Shiny", 1 in shiny) { shiny = toggle(shiny, 1); changed() }
            GachaCheckChip("Not shiny", 0 in shiny) { shiny = toggle(shiny, 0); changed() }
        }
        GachaTextField(less, "Battle Power less than") { v -> less = v.filter { it.isDigit() }.take(5); changed() }
        GachaTextField(greater, "Battle Power greater than") { v -> greater = v.filter { it.isDigit() }.take(5); changed() }
        if (message.isNotEmpty()) Text(message, color = Shell.dangerOnPaper, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(Modifier.height(6.dp))
        val n = found
        Gen3Button(if (n != null && n > 0) "Remove them" else "Find the cards", accent = true, raw = true) {
            val m = matcher()
            if (n == null || n == 0) {
                val count = GachaMon.collection.count { it.card.favorite != 1 && m(it) }
                found = count
                message = if (count == 0) "No GachaMons in your collection match that." else "$count of ${GachaMon.collection.size} GachaMons in your collection will be removed. Continue?"
            } else {
                val removed = GachaMon.removeAll(m)
                found = null
                message = "$removed GachaMons were removed from your collection."
            }
        }
    }
}

/**
 * The PC tracker's whole collection: its FullCollection.gccg (in the tracker's GachaMon folder), picked with the
 * system's file picker and merged (GachaMon.importCollection). Says what happened in one line.
 */
@Composable
private fun GachaPcCollectionImport() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        said = "Reading the file..."
        scope.launch {
            // A collection file is 31 bytes a card; anything over 4 MB is not one.
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { s -> s.readBytesUpTo(4 shl 20) } }.getOrNull()
            }
            said = if (bytes == null) "That file could not be opened." else GachaMon.importCollection(bytes)
        }
    }
    Gen3Button(GachaMonText.IMPORT_PC, raw = true) { picker.launch(arrayOf("*/*")) }
    Text("Pick FullCollection.gccg from the PC tracker's GachaMon folder. Cards you already have are skipped.",
        color = Shell.hintOnPaper, fontSize = 13.sp)
    said?.let { Text(it, color = Shell.inkOnPaper, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** At most [max] bytes of this stream, or null when it holds more. */
private fun java.io.InputStream.readBytesUpTo(max: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = read(buf)
        if (n < 0) return out.toByteArray()
        out.write(buf, 0, n)
        if (out.size() > max) return null
    }
}

/** A friend's card, from its code (the PC tracker's codes too): shown on the View tab first, then kept if wanted. */
@Composable
private fun GachaImportDialog(onPreview: (GachaMonEntry) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    ShellDialog(GachaMonText.ADD_CODE, onDismiss) {
        Text("Paste a GachaMon share code, from KaizoCore or the PC tracker.", color = Shell.inkOnPaper, style = MaterialTheme.typography.bodyMedium)
        GachaTextField(code, "Share code") { code = it; wrong = false }
        if (wrong) Text("That code is not a GachaMon card.", color = Shell.dangerOnPaper, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Gen3Button("Read the code", accent = true, raw = true) {
            val card = GachaMonCodec.fromShareCode(code)
            if (card == null) wrong = true else onPreview(GachaMonEntry(card.copy(favorite = 0)))
        }
    }
}

// ------------------------------------------------------------------ About

@Composable
private fun GachaMonAboutTab() {
    val ctx = LocalContext.current
    var sample by remember { mutableIntStateOf(0) }
    val card = remember(sample) { gachaSampleCard(kotlin.random.Random(System.nanoTime())) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(GachaMonText.HEADER, color = Shell.accentOnNight, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
        Text(GachaMonText.SLOGAN, color = Shell.textOnNight, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(GachaMonText.HOW.uppercase(), color = Shell.accentOnNight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                GachaMonText.STEPS.forEachIndexed { i, s -> Text("${i + 1}.  $s", color = Shell.textOnNight, fontSize = 14.sp) }
                Spacer(Modifier.height(10.dp))
                Text(GachaMonText.WHATS_ON.uppercase(), color = Shell.accentOnNight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(GachaMonText.STARS_LINE, color = Shell.textOnNight, fontSize = 14.sp)
                Text(GachaMonText.POWER_LINE, color = Shell.textOnNight, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GachaMonCardFace(card, 120.dp, Modifier.clickable(role = Role.Button, onClickLabel = "Show another sample card") { sample++ })
                Text(GachaMonText.SAMPLE, color = Shell.hintOnNight, fontSize = 12.sp, modifier = Modifier.width(120.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(GachaMonText.HELP_LINK, color = Shell.textOnNight, fontSize = 14.sp, textDecoration = Shell.linkDecoration,
            modifier = Modifier.heightIn(min = Shell.touchTarget).clickable(role = Role.Button) {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(GachaMonText.HELP_URL))) }
            }.padding(vertical = 12.dp))
        Text(GachaMonText.CREDIT, color = Shell.hintOnNight, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * GachaMonData.createRandomGachaMon: a made-up card to show what one looks like. Never saved. Its Pokemon, ability and
 * moves are random from the five games', its rating 1 to 71 and its power its stars and up to three thousand more.
 */
internal fun gachaSampleCard(r: kotlin.random.Random): GachaMonEntry {
    val level = r.nextInt(1, 101)
    var species = r.nextInt(1, 387)
    if (species in 252..276) species += 25
    val rating = r.nextInt(1, 72)
    val stars = GachaMonRatingSystem.default.stars(rating, GachaMonCodec.CURRENT_VERSION)
    val type1 = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17).random(r)
    val today = java.time.LocalDate.now()
    val card = GachaMonCard(
        version = GachaMonCodec.CURRENT_VERSION, personality = 0xFFFFFFFFL, pokemonId = species, level = level,
        abilityId = r.nextInt(1, 78), ratingScore = rating, battlePower = (stars + r.nextInt(0, 4)) * 1000,
        seedNumber = r.nextInt(1, 30000), type1 = type1, type2 = type1,
        stats = com.ironmonone.tracker.gachamon.SixStats(level * r.nextInt(1, 5), level * r.nextInt(1, 5), level * r.nextInt(1, 5),
            level * r.nextInt(1, 5), level * r.nextInt(1, 5), level * r.nextInt(1, 5)),
        moveIds = List(4) { r.nextInt(1, 355) }, gameVersion = r.nextInt(1, 6), gender = r.nextInt(1, 3), nature = r.nextInt(0, 25),
        year = today.year, month = today.monthValue, day = today.dayOfMonth,
    )
    return GachaMonEntry(card)
}
