package com.ironmonone.pcui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.LocalPcCtx
import com.ironmonone.app.Pc
import com.ironmonone.app.PcAssets
import com.ironmonone.app.PixText
import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.rp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.Gen3Types

/**
 * besteon's screens a click opens over the live card (TrackerScreen.Buttons: the icon's Pokemon info, the moves
 * header's moves, a move's info, the heals, the catch rates, the route, the trainer, the carousel's battle details,
 * route and trainers; the starter card's History): a stack, each back arrow going down one, the card itself under the
 * last. Kept by the session across a remade renderer, as the chart is. What a screen shows is read when it is drawn,
 * from [source], on the session's thread (a click and a render both run there), so an open screen follows the game.
 */
class PcScreens {
    sealed class Screen {
        /** InfoScreen POKEMON_INFO for any species. */
        data class PokemonInfo(val species: Int) : Screen()
        /** The moves header (the DS tracker's Moves Learned): every level-up move with its level, [level] the Pokemon's own. */
        data class MovesLearned(val species: Int, val level: Int?, val own: Boolean = false) : Screen()
        /** MoveHistoryScreen: [levels] the species' learn levels, [level] a team member's for the boxes' colours. */
        data class MoveHistory(val species: Int, val name: String, val levels: List<Int>, val level: Int? = null) : Screen()
        /** InfoScreen MOVE_INFO; [hide] keeps a randomized move's numbers back, as the card's row does. */
        data class MoveInfo(val move: Int, val hide: Boolean = false) : Screen()
        object HealsInBag : Screen()
        object CatchRates : Screen()
        object BattleDetails : Screen()
        data class RouteInfo(val mapId: Int, val area: String? = null) : Screen()
        data class TrainerInfo(val trainer: Int) : Screen()
        data class TrainersOnRoute(val mapId: Int) : Screen()
        /** An item's name and the game's description of it: a held item, a bag row, a ball. */
        data class ItemInfo(val item: Int) : Screen()
        /** Words with nothing more behind them: why the run bans something, a badge. */
        data class Note(val title: String, val body: String) : Screen()
        /** What a move type hits: twice, half and not at all. */
        data class TypeInfo(val type: Int, val natDex: Boolean) : Screen()
    }

    /** What the screens read: the session's tracker, run and pictures. */
    interface Source {
        fun info(species: Int): PcPanel.StarterFacts?
        /** (level, move id) for each level-up move. */
        fun learnset(species: Int): List<Pair<Int, Int>>
        fun moveName(id: Int): String
        fun moveRow(id: Int): com.ironmonone.tracker.MoveRow?
        fun moveText(id: Int): String?
        fun heals(): List<GbaTracker.BagRow>
        fun catchRates(hpAdjust: Int): GbaTracker.CatchRates?
        fun battleDetails(): GbaTracker.BattleDetails?
        fun routeName(mapId: Int): String
        fun routeAreas(mapId: Int): Map<String, List<GbaTracker.RouteMon>>
        /** The species met on the map this run (Tracker.getRouteEncounters). */
        fun routeSeen(mapId: Int): Set<Int>
        /** Heart & Soul's tables are the run's own: a Pokemon not met there stays a "?" outside Open Book. */
        val runTables: Boolean
        fun trainer(id: Int): GbaTracker.TrainerInfo?
        fun trainersOnRoute(mapId: Int): List<Int>
        fun itemName(id: Int): String
        /** The item's description from the game; null where the game has none. */
        fun itemText(id: Int): String? = null
        fun sprite(species: Int): ImageBitmap?
    }

    var source: Source? = null
    var stack by mutableStateOf<List<Screen>>(emptyList())
        private set
    var page by mutableStateOf(1)
    var tab by mutableStateOf("All")
    var hpAdjust by mutableStateOf(0)
    val top: Screen? get() = stack.lastOrNull()
    /** The page count the card on top drew its arrows for ([pagesFor] that card), for the mouse wheel. */
    @Volatile var pages = 1
    @Volatile var pagesFor: Screen? = null
    /** The mouse wheel on a card with pages: one page on, or back, round the ends as the arrows go. False: no pages. */
    fun wheel(down: Boolean): Boolean {
        val n = if (pagesFor == top && top != null) pages else 1
        if (n <= 1) return false
        val p = page.coerceIn(1, n)
        page = if (down) (if (p >= n) 1 else p + 1) else (if (p <= 1) n else p - 1)
        return true
    }
    /** "(Leave a note)" on a Pokemon's info card: the note window for that species (the session sets it). */
    var onNoteFor: (Int) -> Unit = {}

    fun push(s: Screen) { stack = stack + s; page = 1; if (s is Screen.HealsInBag) tab = "All"; if (s is Screen.CatchRates) hpAdjust = 0 }
    fun back() { stack = stack.dropLast(1); page = 1 }
    fun close() { stack = emptyList(); page = 1 }
    private fun replace(s: Screen) { stack = stack.dropLast(1) + s; page = 1 }

    companion object {
        /** HealsInBagScreen.Tabs: All first, as the screen opens on it. */
        val HEAL_TABS = listOf("All", "HP", "PP", "Status", "Battle")

        @Composable
        fun View(screens: PcScreens, input: PcPanel.Input, chart: PcPanel.ChartUi) {
            val src = screens.source
            when (val s = screens.top) {
                null -> {}
                is Screen.MoveHistory -> MoveHistory(screens, input, s)
                is Screen.Note -> Words(screens, s.title, s.body)
                is Screen.TypeInfo -> Words(screens, "${Gen3Types.name(s.type)} moves", typeLines(s.type, s.natDex))
                else -> if (src == null) Frame(screens) { BText("Not ready yet.", 10, 10, Pc.Text) } else when (s) {
                    is Screen.PokemonInfo -> PokemonInfo(screens, src, input, chart, s)
                    is Screen.MovesLearned -> MovesLearned(screens, src, input, s)
                    is Screen.MoveInfo -> MoveInfo(screens, src, s)
                    Screen.HealsInBag -> Heals(screens, src)
                    Screen.CatchRates -> Catch(screens, src, input)
                    Screen.BattleDetails -> Details(screens, src)
                    is Screen.RouteInfo -> Route(screens, src, s)
                    is Screen.TrainerInfo -> Trainer(screens, src, input, s)
                    is Screen.TrainersOnRoute -> Trainers(screens, src, s)
                    is Screen.ItemInfo -> Words(screens, src.itemName(s.item), src.itemText(s.item) ?: "No description for this item.")
                    is Screen.MoveHistory, is Screen.Note, is Screen.TypeInfo -> {}
                }
            }
        }

        /** Whether a move's numbers stay hidden: a randomized move table with "Reveal info if randomized" off, for a move the lead does not know. */
        fun hides(st: com.ironmonone.tracker.TrackerState?, move: Int): Boolean {
            if (TrackerOptions.revealInfoIfRandomized || st?.randomized?.moveType != true) return false
            val lead = st.lead ?: st.party.firstOrNull()
            return lead?.moveRows?.none { it.id == move } != false
        }

        @Composable
        private fun Frame(screens: PcScreens, content: @Composable () -> Unit) {
            Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
                BRect(5, 5, 140, 150, Pc.Border, Pc.Ground)
                content()
                BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.Text)
                BHit(124, 140, 21, 15) { screens.back() }
            }
        }

        /** A title and its words, a page at a time: item descriptions, ban reasons, badges, a type's matchups. */
        @Composable
        private fun Words(screens: PcScreens, title: String, body: String) {
            Frame(screens) {
                BText(title.uppercase(), 7, 6, Pc.Header, w = 136)
                val lines = body.split('\n').flatMap { if (it.isBlank()) listOf("") else wrapWords(it, 30) }
                val (shown, pages) = paged(lines, 11, screens.page)
                shown.forEachIndexed { i, l -> BText(l, 7, 19 + i * 11, Pc.Text, w = 136) }
                Pager(screens, pages)
            }
        }

        /** What [type] hits for twice, half and no damage, each its own paragraph. */
        internal fun typeLines(type: Int, natDex: Boolean): String {
            val by = Gen3Types.typesFor(natDex).groupBy { Gen3Types.effect(type, it, false, natDex) }
            fun line(label: String, m: Double) = by[m]?.takeIf { it.isNotEmpty() }?.let { "$label: " + it.joinToString(", ") { t -> Gen3Types.name(t) } }
            return listOfNotNull(line("Super effective (2x)", 2.0), line("Not very effective (1/2x)", 0.5), line("No effect (0x)", 0.0))
                .joinToString("\n\n").ifEmpty { "Hits every type for normal damage." }
        }

        /** The page arrows at the foot of a list, LogSearchScreen's; [pages] of them. */
        @Composable
        private fun Pager(screens: PcScreens, pages: Int, color: Color = Pc.Text) {
            screens.pages = pages; screens.pagesFor = screens.top
            if (pages <= 1) return
            val page = screens.page.coerceIn(1, pages)
            BImage(PcBesteon.LEFT_ARROW, 37, 141, color); BText("$page/$pages", 51, 140, color, w = 40, align = TextAlign.Center); BImage(PcBesteon.RIGHT_ARROW, 96, 141, color)
            BHit(35, 140, 13, 11) { screens.page = if (page <= 1) pages else page - 1 }
            BHit(94, 140, 13, 11) { screens.page = if (page >= pages) 1 else page + 1 }
        }

        private fun <T> paged(items: List<T>, per: Int, page: Int): Pair<List<T>, Int> {
            val pages = maxOf(1, (items.size + per - 1) / per)
            return items.drop((page.coerceIn(1, pages) - 1) * per).take(per) to pages
        }

        @Composable
        private fun Picture(bmp: ImageBitmap?, x: Int, y: Int, size: Int = 32) {
            if (bmp != null) Image(bmp, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.rp, y.rp).size(size.rp))
        }

        /** InfoScreen POKEMON_INFO: the starter card's lines for any species, its History and Show resistances, and a back arrow. */
        @Composable
        private fun PokemonInfo(screens: PcScreens, src: Source, input: PcPanel.Input, chart: PcPanel.ChartUi, s: Screen.PokemonInfo) {
            val facts = src.info(s.species) ?: return Frame(screens) { BText("No data for this Pokemon.", 10, 10, Pc.Text) }
            Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp)) {
                PcPanel.InfoCard(facts, input, chart, screens) { screens.onNoteFor(s.species) }
                BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.LowerText)
                BHit(124, 140, 21, 15) { screens.back() }
            }
        }

        /** The moves header: each level-up move and its level, learned ones in the learn boxes' red and the ones to come green; History under them. */
        @Composable
        private fun MovesLearned(screens: PcScreens, src: Source, input: PcPanel.Input, s: Screen.MovesLearned) {
            val text = Pc.Text
            val moves = src.learnset(s.species)
            val name = src.info(s.species)?.name ?: ""
            Frame(screens) {
                PixText(name.uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
                BText("Level-up moves", 7, 18, Pc.Header)
                if (s.level != null) BText("Lv.${s.level}", 0, 18, Pc.Header, w = 143, align = TextAlign.End)
                if (moves.isEmpty()) BText("Does not learn any moves", 12, 32, text)
                val (rows, pages) = paged(moves, 10, screens.page)
                // Never the log mid-run (Blake, 2026-10-11: "all the moves in green, you should not be able to see them";
                // "you can only see enemy wild or trainer pokemon past/current moves of that particular encounter, and same
                // for your pokemon, you can only see the levels"). A name only for your own Pokemon's moves at or below its
                // level, or a move the foe in this battle has been seen to use; open-book play and an unrandomized game show
                // them all, as the card's moves do (InfoRules). Otherwise the level alone.
                val open = com.ironmonone.app.InfoRules.canShowMoves(input.state?.randomized)
                val seen = input.state?.enemy?.takeIf { !s.own && it.species == s.species }?.moveRows.orEmpty().map { it.id }.toSet()
                rows.forEachIndexed { i, (lv, id) ->
                    val y = 30 + i * 11
                    val color = when { s.level == null -> text; lv <= s.level -> Pc.Negative; else -> Pc.Positive }
                    BNumber("$lv", 9, y, 2, color)
                    val known = open || id in seen || (s.own && s.level != null && lv <= s.level)
                    if (known) {
                        BText(src.moveName(id), 25, y, text, w = 115)
                        BHit(7, y, 136, 10) { screens.push(Screen.MoveInfo(id, hides(input.state, id))) }
                    } else BText("?", 25, y, Pc.Dim)
                }
                BText("History", 7, 140, Pc.Header)
                Box(Modifier.offset(8.rp, 149.rp).size(com.ironmonone.app.referenceWidth("History").rp, 1.rp).background(Pc.Header))
                BHit(6, 139, 30, 12) { screens.push(Screen.MoveHistory(s.species, name, moves.map { it.first }, s.level)) }
                Pager(screens, pages)
            }
        }

        /** MoveHistoryScreen as the log's History draws it: the learn-level boxes, then each move seen with its Min and Max level, seven a page, newest first. */
        @Composable
        private fun MoveHistory(screens: PcScreens, input: PcPanel.Input, s: Screen.MoveHistory) {
            val text = Pc.Text
            val seen = input.marks?.movesSeenFor(s.species).orEmpty().sortedByDescending { it.minLv }
            Frame(screens) {
                PixText(s.name.uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
                PcLogSide.LevelBoxes(s.levels.filter { it > 1 }, s.level, 6, 21, Pc.Border, Pc.Ground, text)
                BText("Move seen at level", 10, 50, Pc.Header)
                BText("Min", 92, 50, Pc.Header); BText("Max", 117, 50, Pc.Header)
                if (seen.isEmpty()) BText("(No tracked move data yet)", 18, 66, text)
                val (rows, pages) = paged(seen, 7, screens.page)
                rows.forEachIndexed { i, m ->
                    val y = 60 + i * 11
                    BText(m.name, 17, y, text)
                    BNumber("${m.minLv}", 94, y, 2, text)
                    BNumber("${m.maxLv}", 119, y, 2, text)
                    if (m.id > 0) BHit(17, y, 80, 10) { screens.push(Screen.MoveInfo(m.id, hides(input.state, m.id))) }
                }
                Pager(screens, pages)
            }
        }

        /** InfoScreen.drawMoveInfoScreen, as the log's: the name, the type box, Category, Contact, PP, Power, Accuracy, Priority, then the Move Summary box. */
        @Composable
        private fun MoveInfo(screens: PcScreens, src: Source, s: Screen.MoveInfo) {
            val r = src.moveRow(s.move)
            val text = Pc.Text
            val hide = s.hide
            val priority = r?.priority?.takeIf { it != 0 && !hide }
            val bot = 85 + (if (priority != null) 10 else 0)
            Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
                BRect(5, 5, 140, bot - 10 - 8, Pc.Border, Pc.Ground)
                PixText((r?.name ?: src.moveName(s.move)).uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
                BRect(112, 22, 31, 13, Pc.Border, Pc.Border)
                if (!hide) r?.type?.let { t ->
                    PcAssets.typeIcon(LocalPcCtx.current, Gen3Types.name(t))?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(113.rp, 23.rp).size(30.rp, 12.rp)) }
                    BHit(112, 22, 32, 14) { screens.push(Screen.TypeInfo(t, natDex = src.runTables)) }
                }
                val y = 20
                val q = if (hide) "?" else null
                val cat = q ?: when (r?.category) { "PHY" -> "Physical"; "SPE" -> "Special"; "STA" -> "Status"; else -> PcBesteon.BLANK }
                if (!hide) when (r?.category) { "PHY" -> BImage(PcBesteon.PHYSICAL, 87, y + 2, text); "SPE" -> BImage(PcBesteon.SPECIAL, 84, y + 2, text) }
                val rows = listOfNotNull(
                    "Category" to cat,
                    "Contact" to (q ?: when (r?.contact) { true -> "Yes"; false -> "No"; null -> PcBesteon.BLANK }),
                    "PP" to (q ?: r?.pp?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK),
                    "Power" to (q ?: r?.power?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK),
                    "Accuracy" to (q ?: r?.acc?.takeIf { it > 0 }?.let { "$it%" } ?: PcBesteon.BLANK),
                    priority?.let { "Priority" to "$it" },
                )
                rows.forEachIndexed { i, (label, value) -> BText("$label:", 7, y + i * 10, text); BText(value, 51, y + i * 10, text) }
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { BText("Move Summary:", 5, bot - 11, Pc.Header) }
                BRect(5, bot, 140, 150 - bot + 5, Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground)
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides (Pc.LowerGroundX ?: Pc.Ground)) {
                    wrapWords(src.moveText(s.move) ?: "", 31).take((150 - bot) / 11).forEachIndexed { i, l -> BText(l, 6, bot + 1 + i * 11, Pc.LowerText) }
                }
                BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.LowerText)
                BHit(124, 140, 21, 15) { screens.back() }
            }
        }

        /** HealsInBagScreen: the tabs (All, HP, PP, Status, Battle), each item with its count, green where it would help your Pokemon now. */
        @Composable
        private fun Heals(screens: PcScreens, src: Source) {
            val text = Pc.Text
            val all = src.heals()
            Frame(screens) {
                BText("HEALS IN BAG", 7, 6, Pc.Header)
                var x = 7
                HEAL_TABS.forEach { t ->
                    val w = com.ironmonone.app.referenceWidth(t) + 4
                    BText(t, x, 18, if (t == screens.tab) Pc.Gold else text)
                    if (t == screens.tab) Box(Modifier.offset((x + 1).rp, 27.rp).size((w - 4).rp, 1.rp).background(Pc.Gold))
                    BHit(x - 1, 17, w, 11) { screens.tab = t; screens.page = 1 }
                    x += w + 2
                }
                val rows = if (screens.tab == "All") all else all.filter { it.category == screens.tab }
                if (rows.isEmpty()) BText("None in the bag", 12, 34, Pc.Dim)
                val (shown, pages) = paged(rows, 10, screens.page)
                shown.forEachIndexed { i, r ->
                    val y = 31 + i * 11
                    val green = (r.helpful && screens.tab != "All") || r.quantity == 69
                    BText(r.name, 9, y, if (green) Pc.Positive else text, w = 100)
                    BText("x${r.quantity}", 0, y, if (green) Pc.Positive else text, w = 142, align = TextAlign.End)
                    BHit(7, y, 136, 10) { screens.push(Screen.ItemInfo(r.id)) }
                }
                Pager(screens, pages)
            }
        }

        /** CatchRatesScreen: the wild Pokemon, its HP and status, the HP buttons, then each ball in the bag with its chance. */
        @Composable
        private fun Catch(screens: PcScreens, src: Source, input: PcPanel.Input) {
            val text = Pc.Text
            val d = src.catchRates(screens.hpAdjust)
            Frame(screens) {
                BText("CATCH RATES", 7, 6, Pc.Header)
                if (d == null) { BText("Only in a wild battle", 12, 22, Pc.Dim); return@Frame }
                BText(d.speciesName, 7, 18, text, w = 130)
                input.state?.enemy?.species?.takeIf { it > 0 }?.let { sp -> BHit(6, 18, 100, 10) { screens.push(Screen.PokemonInfo(sp)) } }
                BText("HP: ${d.hpPercent}%" + (if (d.exact) "" else " (est.)"), 7, 28, text)
                BText("Status: ${d.status.ifBlank { "None" }}", 74, 28, text)
                // The HP buttons: what the chances would be after more damage, or less.
                BRect(112, 39, 13, 11, Pc.Border, Pc.Ground) { screens.hpAdjust = (screens.hpAdjust - 10).coerceAtLeast(-100) }
                BText(PcBesteon.BLANK, 112, 39, text, w = 14, align = TextAlign.Center)
                BRect(129, 39, 13, 11, Pc.Border, Pc.Ground) { screens.hpAdjust = (screens.hpAdjust + 10).coerceAtMost(100) }
                BText("+", 129, 39, text, w = 14, align = TextAlign.Center)
                BText("Ball", 9, 41, Pc.Header); BText("Have", 66, 41, Pc.Header); BText("Rate", 90, 41, Pc.Header)
                if (d.rows.isEmpty()) BText("No balls in the bag", 12, 55, Pc.Dim)
                val (rows, pages) = paged(d.rows, 7, screens.page)
                rows.forEachIndexed { i, r ->
                    val y = 53 + i * 11
                    val c = if (r.pokeBall) Pc.Gold else text
                    BText(r.name, 9, y, c, w = 56)
                    BNumber("${r.quantity}", 68, y, 3, c)
                    BText("${r.rate}%", 90, y, if (r.rate >= 100) Pc.Positive else c)
                }
                Pager(screens, pages)
            }
        }

        /** BattleDetailsScreen: the turn, the weather and terrain, the field's effects, each side's and each battler's. */
        @Composable
        private fun Details(screens: PcScreens, src: Source) {
            val text = Pc.Text
            val d = src.battleDetails()
            Frame(screens) {
                BText("BATTLE DETAILS", 7, 6, Pc.Header)
                if (d == null) { BText("Only in a battle", 12, 22, Pc.Dim); return@Frame }
                val lines = ArrayList<Pair<String, Color>>()
                lines += "Turn: ${d.turn}" to text
                if (d.weather.isNotBlank()) lines += "Weather: ${d.weather}" to text
                if (d.terrain.isNotBlank()) lines += "Terrain: ${d.terrain}" to text
                fun section(head: String, items: List<GbaTracker.BattleDetail>) {
                    if (items.isEmpty()) return
                    lines += head to Pc.Gold
                    items.forEach { lines += "  " + it.text to text }
                }
                section("Field", d.field)
                d.sides.forEachIndexed { i, side -> section(if (i == 0) "Your side" else "Foe's side", side) }
                d.mons.forEachIndexed { i, m -> section((if (i % 2 == 0) "You" else "Foe") + (if (d.battlers > 2) " ${i / 2 + 1}" else ""), m) }
                val (shown, pages) = paged(lines, 11, screens.page)
                shown.forEachIndexed { i, (l, c) -> BText(l, 7, 18 + i * 11, c, w = 136) }
                Pager(screens, pages)
            }
        }

        /** InfoScreen ROUTE_INFO: the pin and the map's name, the area between its arrows, then the Pokemon met there or "?" for one not met yet. */
        @Composable
        private fun Route(screens: PcScreens, src: Source, s: Screen.RouteInfo) {
            val text = Pc.Text; val lower = Pc.LowerText
            val areas = src.routeAreas(s.mapId).filterValues { it.isNotEmpty() }
            val names = areas.keys.toList()
            val area = s.area?.takeIf { it in areas } ?: names.firstOrNull()
            val seen = src.routeSeen(s.mapId)
            Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
                BRect(5, 5, 140, 30, Pc.Border, Pc.Ground)
                BImage(PcBesteon.MAP_PINDROP, 8, 8, text)
                BText(PcLogOverlay.nice(src.routeName(s.mapId)), 18, 7, text, w = 125)
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) {
                    BText(if (area != null) "$area Pokémon" else "No wild Pokémon here", 0, 37, Pc.Header, w = 150, align = TextAlign.Center)
                }
                if (names.size > 1 && area != null) {
                    val i = names.indexOf(area)
                    BImage(PcBesteon.LEFT_ARROW, 8, 38, Pc.Header); BImage(PcBesteon.RIGHT_ARROW, 132, 38, Pc.Header)
                    BHit(5, 36, 16, 12) { screens.replace(Screen.RouteInfo(s.mapId, names[(i - 1 + names.size) % names.size])) }
                    BHit(129, 36, 16, 12) { screens.replace(Screen.RouteInfo(s.mapId, names[(i + 1) % names.size])) }
                }
                val fill = Pc.LowerGroundX ?: Pc.Ground
                BRect(5, 48, 140, 107, Pc.LowerBorder, fill)
                val mons = areas[area].orEmpty()
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides fill) {
                // Twelve a page, with page arrows: an area with more showed only its first twelve.
                val (onPage, pages) = paged(mons, 12, screens.page)
                onPage.forEachIndexed { i, m ->
                    val x = 9 + (i % 4) * 34; val y = 62 + (i / 4) * 26
                    val known = !src.runTables || m.id in seen
                    if (known) Picture(src.sprite(m.id), x, y - 4)
                    else BText("?", x, y + 6, lower, w = 32, align = TextAlign.Center)
                    val rate = "${(m.rate * 100).toInt()}%"
                    BText(rate, x, y - 12, lower, w = 32, align = TextAlign.Center)
                    if (known) BHit(x, y - 4, 32, 26) { screens.push(Screen.PokemonInfo(m.id)) }
                }
                Pager(screens, pages, lower)
                }
                BImage(PcBesteon.BACK_ARROW, 129, 142, lower)
                BHit(124, 140, 21, 15) { screens.back() }
            }
        }

        /** TrainerInfoScreen as the log's: the class and name, the route, the party size and levels, Avg. IVs, the AI, the items, the party grid (Poke Balls until beaten). */
        @Composable
        private fun Trainer(screens: PcScreens, src: Source, input: PcPanel.Input, s: Screen.TrainerInfo) {
            val info = src.trainer(s.trainer)
            val text = Pc.Text; val hi = Pc.Gold
            Frame(screens) {
                if (info == null) { BText("No data for this trainer.", 10, 10, text); return@Frame }
                BText("${info.className} ${info.name}".trim(), 7, 7, hi, w = 105)
                BImage(PcBesteon.MAP_PINDROP, 10, 20, text)
                BText(PcLogOverlay.nice(input.routeName ?: PcBesteon.BLANK), 21, 20, text, w = 90)
                input.state?.mapId?.let { id -> BHit(8, 19, 104, 11) { screens.push(Screen.RouteInfo(id, input.state?.encounterArea)) } }
                BText("Pokémon:", 7, 35, text)
                BText("${info.party.size}  (Lv.${info.minLevel}${if (info.maxLevel != info.minLevel) "-${info.maxLevel}" else ""})", 52, 35, text)
                BText("Avg. IVs:", 7, 45, text); BText("${info.avgIvs}", 52, 45, text)
                BText("A I Script:", 7, 55, text); BText(info.aiLabel, 52, 55, text)
                val items = info.items.filter { it > 0 }.groupingBy { it }.eachCount().map { (id, n) -> if (n == 1) src.itemName(id) else "$n ${src.itemName(id)}" }.sorted()
                if (items.isNotEmpty()) {
                    BText("Usable Items:", 7, 65, text); BText(items.joinToString(", "), 14, 75, Pc.Positive, w = 130)
                    val about = info.items.filter { it > 0 }.distinct().joinToString("\n\n") { id -> src.itemName(id) + ": " + (src.itemText(id) ?: "No description.") }
                    BHit(6, 65, 138, 20) { screens.push(Screen.Note("Usable items", about)) }
                }
                BText("# ${info.id}", 113, 7, text, w = 32, align = TextAlign.Center)
                if (info.defeated) BText("Beaten", 113, 17, Pc.Positive, w = 32, align = TextAlign.Center)
                if (info.doubleBattle) { BText("Double", 113, 31, hi); BText("Battle!", 113, 41, hi) }
                info.party.take(6).forEachIndexed { i, m ->
                    val x = 27 + (i % 3) * 32; val y = 84 + (i / 3) * 27
                    BRect(x, y, 32, 26, Pc.Border, Pc.Ground)
                    if (info.defeated) Picture(src.sprite(m.species), x, y - 5)
                    else BPix(BImg.POKEBALL, x + 16 - 5, y + 4, listOf(Color.Black, Color(0xFFF04037.toInt()), Color.White))
                    BText("Lv.${m.level}", x, y + 16, text, w = 33, align = TextAlign.Center)
                    if (m.heldItem != 0) BPix(BImg.HELD_ITEM, x + 26, y + 1, bColors(BImg.HELD_ITEM_COLORS))
                    // A Pokemon of a trainer not beaten yet stays a Poke Ball, as besteon keeps it hidden.
                    if (info.defeated) BHit(x, y, 32, 26) { screens.push(Screen.PokemonInfo(m.species)) }
                }
            }
        }

        /** TrainersOnRouteScreen: each trainer on the map, beaten ones in green; a click opens that trainer. */
        @Composable
        private fun Trainers(screens: PcScreens, src: Source, s: Screen.TrainersOnRoute) {
            val text = Pc.Text
            val ids = src.trainersOnRoute(s.mapId)
            Frame(screens) {
                BImage(PcBesteon.MAP_PINDROP, 8, 8, text)
                BText(PcLogOverlay.nice(src.routeName(s.mapId)), 18, 7, text, w = 125)
                BHit(6, 6, 138, 11) { screens.push(Screen.RouteInfo(s.mapId)) }
                if (ids.isEmpty()) BText("No trainers here", 12, 22, Pc.Dim)
                val (shown, pages) = paged(ids, 10, screens.page)
                shown.forEachIndexed { i, id ->
                    val t = src.trainer(id)
                    val y = 21 + i * 11
                    val c = if (t?.defeated == true) Pc.Positive else text
                    BText(t?.let { "${it.className} ${it.name}".trim() } ?: "# $id", 9, y, c, w = 112)
                    if (t != null) BText("Lv.${t.maxLevel}", 0, y, c, w = 142, align = TextAlign.End)
                    BHit(7, y, 136, 10) { screens.push(Screen.TrainerInfo(id)) }
                }
                Pager(screens, pages)
            }
        }
    }
}
