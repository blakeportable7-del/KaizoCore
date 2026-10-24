package com.ironmonone.pcui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ironmonone.app.LocalPcCtx
import com.ironmonone.app.LogEncType
import com.ironmonone.app.LogFilter
import com.ironmonone.app.LogSearch
import com.ironmonone.app.LogSort
import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.rp
import com.ironmonone.app.PcTrainerTeam
import com.ironmonone.app.LogRoute
import com.ironmonone.app.LogTmFilter
import com.ironmonone.app.LogTrainerFilter
import com.ironmonone.app.Pc
import com.ironmonone.app.PcCrispText
import com.ironmonone.app.PcRef
import com.ironmonone.app.PixText
import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.pcDesktopCtx
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.roundToInt

/**
 * The log viewer drawn over the game area inside BizHawk (PLAN row 7), ported from besteon's LogOverlay (Ironmon-Tracker,
 * MIT) with the Heart & Soul log as the data: the game's 240 by 160 pixels, the header of tab icons (Pokemon, Trainers,
 * Routes, TMs, Misc.) with "Page N/M" and its arrows and the X (the back arrow inside a page); the tab's box under it.
 * The panel beside it shows LogSearchScreen (sort, search, the on-screen keyboard) or the Pokemon's InfoScreen card
 * (PcLogSide), sharing the view. Opened by the game over card's Inspect the log, closed by the X, which lands back on
 * the card; mouse and wheel driven. Drawn the panel's way: a picture at the window's scale on BizHawk's client surface.
 *
 * It is never drawn mid-run: the session opens it only from the game over card (Blake, 2026-09-10: the log is for
 * after a loss).
 */
object PcLogOverlay {
    const val WIDTH = 240
    const val HEIGHT = 160
    /** LogOverlay.margin and tabHeight, and the TabBox they give. */
    private const val MARGIN = 2
    private const val TAB_H = 12
    private const val BOX_X = MARGIN
    private const val BOX_Y = TAB_H
    private const val BOX_W = WIDTH - 2 * MARGIN
    private const val BOX_H = HEIGHT - TAB_H - MARGIN - 1
    private const val LINE = PcRef.LINESPACING
    private const val FONT = PcRef.FONT

    /** LogOverlay.addHeaderTabButtons' tabs, in order, with the app's LogTab for the search's sorts and filters. */
    enum class Tab(val log: com.ironmonone.app.LogTab, val icons: List<String>) {
        POKEMON(com.ironmonone.app.LogTab.POKEMON, listOf("tiny-jigglypuff", "tiny-nidoranm")),
        TRAINERS(com.ironmonone.app.LogTab.TRAINERS, listOf("boy-e")),
        ROUTES(com.ironmonone.app.LogTab.ROUTES, listOf("tiny-map-hoenn")),
        TMS(com.ironmonone.app.LogTab.TMS, listOf("tiny-tm")),
        MISC(com.ironmonone.app.LogTab.MISC, listOf("tiny-pc")),
    }
    enum class MovesTab { LEVEL, TM }

    /** The panel screens a click opens beside the viewer. */
    sealed class Side {
        data class Move(val name: String) : Side()
        data class Trainer(val trainer: RandomizerLog.Trainer) : Side()
        data class Route(val route: LogRoute) : Side()
        data class History(val pokemon: RandomizerLog.Pokemon) : Side()
        data class Defenses(val pokemon: RandomizerLog.Pokemon) : Side()
    }
    /** LogTabPokemonDetails.currentStatView. */
    enum class StatView { BST, IVS, EVS }

    /** Everything the overlay shows; a click makes the next one. Immutable, so a test reads it whole. */
    data class View(
        val tab: Tab = Tab.POKEMON,
        val page: Int = 1,
        /** LogSearchScreen.searchText. */
        val query: String = "",
        /** LogSearchScreen.currentSortOrder and currentFilter; null is the tab's default. */
        val sort: LogSort? = null,
        val filter: LogFilter? = null,
        val pokemon: RandomizerLog.Pokemon? = null,
        val trainer: RandomizerLog.Trainer? = null,
        val route: LogRoute? = null,
        /** LogTabTrainers.realignGrid's default: the gym leaders. */
        val trainerFilter: LogTrainerFilter = LogTrainerFilter.GYM,
        val tmFilter: LogTmFilter = LogTmFilter.GYM,
        val movesTab: MovesTab = MovesTab.LEVEL,
        val movesPage: Int = 1,
        val statView: StatView = StatView.BST,
        /** LogTabPokemonDetails.currentEvoSet. */
        val evoSet: Int = 1,
        /** The route page's area; null is its first. */
        val area: LogEncType? = null,
        /** LogOverlay.TabHistory: where the back arrow goes. */
        val history: List<View> = emptyList(),
    ) {
        val inPage: Boolean get() = pokemon != null || trainer != null || route != null
        val sortOrDefault: LogSort get() = sort ?: LogSearch.defaultSort(tab.log) ?: LogSort.ALPHA
        val filterOrDefault: LogFilter get() = filter ?: LogSearch.defaultFilter(tab.log) ?: LogFilter.NAME

        /** This view on the stack, then [next] on top of it (the search goes with it, as besteon's is global). */
        fun into(next: View): View = next.copy(query = query, sort = sort, filter = filter, history = history + this.copy(history = emptyList()))

        fun back(): View? = history.lastOrNull()?.copy(history = history.dropLast(1), query = query, sort = sort, filter = filter)
    }

    /**
     * What the overlay and the panel beside it share while the log is open (the panel shows LogSearchScreen or the
     * log's Pokemon card): the log, the view, the search's drop-downs, and your team's IVs and EVs for "Show IVs".
     */
    class Shared {
        var data by mutableStateOf<PcLogData?>(null)
        var view by mutableStateOf(View())
        var sortOpen by mutableStateOf(false)
        var filterOpen by mutableStateOf(false)
        /** Species id to its IVs and EVs in HP, ATK, DEF, SPA, SPD, SPE order, for the team's Pokemon. */
        var team by mutableStateOf<Map<Int, Pair<List<Int>, List<Int>>>>(emptyMap())
        /** The level of a team member of each species, for the learn-level boxes' colours. */
        var teamLevels by mutableStateOf<Map<Int, Int>>(emptyMap())
        /** The X at the top level. */
        var onClose: () -> Unit = {}
        /** Something changed that the other picture shows (the session draws both). */
        var onChanged: () -> Unit = {}

        /** The screen a click put on the panel beside the viewer (InfoScreen, TrainerInfoScreen, MoveHistoryScreen, TypeDefensesScreen); null is the view's own. */
        var side by mutableStateOf<Side?>(null)
        private var sideStack: List<Side> = emptyList()
        var historyPage by mutableStateOf(1)
        /** GameOverScreen's Prize card: GachaMonOverlay's View tab over the game area in place of the log, until its X. */
        var prize by mutableStateOf<PcGachaMon?>(null)
        var prizeSprite: ImageBitmap? = null
        /** The run's tracked moves of a species (Tracker.getMoves), for History. */
        var movesSeen: (Int) -> List<com.ironmonone.app.StatMarks.SeenMove> = { emptyList() }

        /** A new view: the panel follows it (its screen set again by the click when it names one). */
        fun set(v: View, panel: Side? = null) { view = v; side = panel; sideStack = emptyList(); historyPage = 1; onChanged() }

        /** A screen opened from the panel's own screen, its back arrow returning there. */
        fun push(s: Side) { side?.let { sideStack = sideStack + it }; side = s; historyPage = 1; onChanged() }

        /** The panel screen's back arrow: the screen before it, else the view's own. */
        fun back() { side = sideStack.lastOrNull(); sideStack = sideStack.dropLast(1); onChanged() }

        /** A key of the on-screen keyboard, the backspace, the clear: the tab's list from its first page. */
        fun setQuery(q: String) { set(view.copy(query = q.take(10), page = 1)) }
    }

    // ---- page sizes (what the grids hold) ----------------------------------------------------------------------------

    /** LogTabPokemon.realignGrid: 32 by 32 from (21, 14), 23 and 4 apart, inside the TabBox: four by four. */
    private const val MON_COLS = 4
    private const val MON_ROWS = 4
    /** LogTabTrainers.realignGrid: 32 by 32 from (17, 42), 56 across and 60 down: four by two. */
    private const val TRAINER_COLS = 4
    private const val TRAINER_ROWS = 2
    /** LogTabRoutes: rows 21 tall from y 29, five a page. */
    private const val ROUTE_ROWS = 5
    /** LogTabTMs: by number nine down and two across; by gym seven a page. */
    private const val TM_ROWS = 9
    private const val GYM_ROWS = 7
    /** LogTabRouteDetails: three across and two down, left of the encounters box. */
    private const val WILD_COLS = 3
    private const val WILD_ROWS = 2

    private fun pages(count: Int, per: Int): Int = maxOf(1, (count + per - 1) / per)

    fun pokemonRows(d: PcLogData, v: View) = d.pokemonRows(v.query, v.filterOrDefault, v.sortOrDefault)
    fun trainerRows(d: PcLogData, v: View) = d.trainerRows(v.trainerFilter, v.query, v.filterOrDefault, v.sort)
    fun routeRows(d: PcLogData, v: View) = d.routeRows(v.query, v.filterOrDefault, v.sortOrDefault)

    /** LogTabPokemonDetails: with evolutions the moves start under them, eight a page, else twelve. */
    private fun hasEvo(p: RandomizerLog.Pokemon) = p.evolutions.isNotEmpty()
    private fun movesPerPage(p: RandomizerLog.Pokemon) = if (hasEvo(p)) 8 else 12

    private fun movesRows(d: PcLogData, v: View): Int {
        val p = v.pokemon ?: return 0
        return if (v.movesTab == MovesTab.LEVEL) p.moves.size + p.evoMoves.size else d.tmRows(p).size
    }

    /** The moves column's pages on a Pokemon's page (LogTabPokemonDetails.Pager). */
    fun movesPages(d: PcLogData, v: View): Int = v.pokemon?.let { pages(movesRows(d, v), movesPerPage(it)) } ?: 1

    /** How many pages the view's list has, for the header (LogOverlay.Windower) and the wheel. */
    fun totalPages(d: PcLogData, v: View): Int = when {
        v.pokemon != null -> movesPages(d, v)
        v.trainer != null -> 1
        v.route != null -> {
            val area = areaOf(d, v)
            val n = if (area == LogEncType.TRAINERS) v.route.trainers.size else v.route.areas[area]?.size ?: 0
            pages(n, WILD_COLS * WILD_ROWS)
        }
        v.tab == Tab.POKEMON -> pages(pokemonRows(d, v).size, MON_COLS * MON_ROWS)
        v.tab == Tab.TRAINERS -> pages(trainerRows(d, v).size, TRAINER_COLS * TRAINER_ROWS)
        v.tab == Tab.ROUTES -> pages(routeRows(d, v).size, ROUTE_ROWS)
        v.tab == Tab.TMS -> if (v.tmFilter == LogTmFilter.GYM) pages(d.gymTms.size, GYM_ROWS) else pages(d.log.tms.size, TM_ROWS * 2)
        else -> 1
    }

    private fun areaOf(d: PcLogData, v: View): LogEncType? = v.area ?: v.route?.let { d.areas(it).firstOrNull() }

    /** The next page (the wheel down, the > arrow), wrapping as Windower.nextPage does. */
    fun turn(d: PcLogData, v: View, forward: Boolean): View {
        val total = totalPages(d, v)
        if (total <= 1) return v
        fun step(p: Int) = if (forward) (p % total) + 1 else ((p - 2 + total) % total) + 1
        return if (v.pokemon != null) v.copy(movesPage = step(v.movesPage)) else v.copy(page = step(v.page))
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    @Composable
    fun Screen(sh: Shared) {
        CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides PcNoIndication,
            LocalTextStyle provides TextStyle(platformStyle = PcCrispText),
            LocalPcCtx provides pcDesktopCtx,
        ) {
            val d = sh.data
            val v = sh.view
            val set: (View) -> Unit = { sh.set(it) }
            sh.prize?.let { g -> PcPrize.View(g, sh.prizeSprite) { sh.prize = null; if (sh.data == null) sh.onClose() else sh.onChanged() }; return@CompositionLocalProvider }
            Box(Modifier.fillMaxSize().background(Pc.Page)) {
                // Every word is kept readable on what is behind it (PixText, readableInk), as on the card: the tabs and the
                // page count on the page, everything else on the box, and boxed words on their own fill.
                CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { Header(sh, d, v, set) }
                val lower = v.pokemon != null
                val boxFill = if (lower) lowerFill() else Pc.Ground
                BRect(BOX_X, BOX_Y, BOX_W, BOX_H, if (lower) Pc.LowerBorder else Pc.Border, boxFill)
                CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides boxFill) {
                if (d == null) {
                    Text(BOX_X + 4, BOX_Y + 4, "The log could not be read.", Pc.Negative)
                    return@CompositionLocalProvider
                }
                when {
                    v.pokemon != null -> PokemonPage(sh, d, v, set)
                    v.trainer != null -> TrainerPage(sh, d, v)
                    v.route != null -> RoutePage(sh, d, v)
                    v.tab == Tab.POKEMON -> PokemonGrid(d, v, set)
                    v.tab == Tab.TRAINERS -> TrainersTab(sh, d, v)
                    v.tab == Tab.ROUTES -> RoutesTab(sh, d, v)
                    v.tab == Tab.TMS -> TmsTab(sh, d, v)
                    else -> MiscTab(d, set, v)
                }
                }
            }
        }
    }

    private fun lowerFill() = Pc.LowerGroundX ?: Pc.Ground

    @Composable
    private fun Text(x: Int, y: Int, text: String, color: Color = Pc.Text, width: Int? = null, align: TextAlign = TextAlign.Start, bold: Boolean = false) {
        PixText(text, FONT, color, Modifier.offset(x.dp, y.dp).then(if (width != null) Modifier.width(width.dp) else Modifier), align = align,
            weight = if (bold) FontWeight.Medium else FontWeight.Normal)
    }

    /** Drawing.drawTransparentTextbox: the words on a box of the fill at three quarters, 1 + their width wide. */
    @Composable
    private fun BoxedText(x: Int, y: Int, text: String, color: Color, fill: Color) {
        if (text.isEmpty()) return
        // The box is three quarters solid, so what shows through (a sprite under a name: Charizard's under "Charizard",
        // 2.89 to 1) moves the ground. The words are made readable on the fill, then taken further the same way (darker
        // for dark words, lighter for light ones) until they also read on the worst that can show through: the fill over
        // black, or over white. Where no colour can (black on a mid green over a dark sprite stops at 3.9), the box is
        // drawn solid instead.
        val base = com.ironmonone.app.readableInk(color, fill)
        val toward = if (com.ironmonone.app.inkContrast(base, Color.Black) < com.ironmonone.app.inkContrast(base, Color.White)) Color.Black else Color.White
        val worst = androidx.compose.ui.graphics.lerp(toward, fill, 0.75f)
        var ink = base
        var t = 0f
        while (com.ironmonone.app.inkContrast(ink, worst) < 4.65 && t < 1f) { t = minOf(1f, t + 0.05f); ink = androidx.compose.ui.graphics.lerp(base, toward, t) }
        val solid = com.ironmonone.app.inkContrast(ink, worst) < com.ironmonone.app.INK_MIN
        if (solid) ink = base
        Box(Modifier.offset((x + 1).dp, (y + 1).dp).size((3 + com.ironmonone.app.referenceWidth(text)).dp, (FONT - 1).dp).background(fill.copy(alpha = if (solid) 1f else 0.75f)))
        CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides fill) { Text(x, y, text, ink) }
    }

    /** A line of words that answers a click, [w] by [h] from ([x], [y]). */
    @Composable
    private fun Button(x: Int, y: Int, w: Int, h: Int, text: String, color: Color, underline: Boolean = false, onClick: () -> Unit) {
        Box(Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).clickable(role = Role.Button) { onClick() }) {
            PixText(text, FONT, color, Modifier.offset(1.dp, 0.dp).width((w - 1).dp))
            if (underline) Box(Modifier.offset(1.dp, (h - 1).dp).size((w - 2).dp, 1.dp).background(color))
        }
    }

    @Composable
    private fun Icon(bmp: ImageBitmap?, x: Int, y: Int, size: Int, name: String) {
        if (bmp != null) Image(bmp, contentDescription = name, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            modifier = Modifier.offset(x.dp, y.dp).size(size.dp))
    }

    /** besteon's own pictures (the tabs' icons, MissingNo), from pcui's resources (MIT, LICENSE.txt beside them). */
    private val pictures = HashMap<String, ImageBitmap?>()
    internal fun picture(name: String): ImageBitmap? = synchronized(pictures) {
        pictures.getOrPut(name) { PcLogOverlay::class.java.getResourceAsStream("/besteon/$name.png")?.use { com.ironmonone.app.pcDecodePicture(it) } }
    }

    private const val SPACER = 3
    /** Each tab icon's width and its x and y nudge (the tabs' TabIcons). */
    private fun iconW(name: String) = when (name) { "tiny-jigglypuff" -> 11; "tiny-nidoranm" -> 12; "boy-e" -> 15; "tiny-map-hoenn" -> 15; "tiny-tm" -> 14; else -> 12 }
    private fun iconH(name: String) = when (name) { "tiny-nidoranm" -> 14; "boy-e" -> 14; "tiny-map-hoenn" -> 11; "tiny-tm" -> 14; else -> 12 }
    private fun iconDx(name: String) = when (name) { "tiny-jigglypuff", "tiny-nidoranm", "tiny-tm" -> 1; else -> 0 }

    /** LogOverlay.addHeaderTabButtons: each tab's box from x 3, as wide as its icons and the spacers between them. */
    fun tabBoxes(): List<Pair<Int, Int>> {
        var x = MARGIN + 1
        return Tab.entries.map { t ->
            val w = SPACER + t.icons.sumOf { iconW(it) + SPACER }
            (x to w).also { x += w + SPACER }
        }
    }

    /**
     * LogOverlay.drawScreen's header: the tabs' icons on the page with a divider after each, the page count with its
     * arrows (Windower, from x 144), and the X, or the back arrow in the highlight colour inside a page, at x 230.
     */
    @Composable
    private fun Header(sh: Shared, d: PcLogData?, v: View, set: (View) -> Unit) {
        val border = if (v.pokemon != null) Pc.LowerBorder else Pc.Border
        Box(Modifier.offset(MARGIN.dp, 1.dp).size(1.dp, (TAB_H - 1).dp).background(border))
        tabBoxes().forEachIndexed { i, (x, w) ->
            val t = Tab.entries[i]
            var ix = x
            for (name in t.icons) {
                val bmp = picture(name)
                val y = (if (name == "tiny-tm") 1 else 0) + TAB_H - iconH(name)
                if (bmp != null) Image(bmp, contentDescription = name, filterQuality = FilterQuality.None,
                    modifier = Modifier.offset((ix + iconDx(name) + SPACER).dp, y.dp).size(bmp.width.dp, bmp.height.dp))
                ix += iconW(name) + SPACER
            }
            val right = x + w + 2
            Box(Modifier.offset(right.dp, 1.dp).size(1.dp, TAB_H.dp).background(border))
            Box(Modifier.offset(x.dp, 0.dp).size(w.dp, 11.dp).clickable(role = Role.Button) {
                if (t != v.tab || v.inPage) set(View(tab = t, trainerFilter = v.trainerFilter, tmFilter = v.tmFilter))
            })
        }
        val total = if (v.inPage) 1 else d?.let { totalPages(it, v) } ?: 1
        if (total > 1) {
            val px = MARGIN + 155
            BImage(PcBesteon.LEFT_ARROW, px - 13, 1, Pc.Header)
            BText("Page ${v.page}/$total", px + 1, 0, Pc.Header)
            BImage(PcBesteon.RIGHT_ARROW, px + 50, 1, Pc.Header)
            BHit(px - 13, 1, 10, 10) { d?.let { set(turn(it, v, forward = false)) } }
            BHit(px + 50, 1, 10, 10) { d?.let { set(turn(it, v, forward = true)) } }
        }
        val back = v.inPage
        if (back) BImage(PcBesteon.LEFT_ARROW, MARGIN + 228, 1, Pc.Gold) else BImage(CLOSE, MARGIN + 228, 2, Pc.Header)
        BHit(MARGIN + 218, 0, 22, 13) { if (back) set(v.back() ?: View(tab = v.tab)) else sh.onClose() }
    }

    /** Constants.PixelImages.CLOSE, UP_ARROW, DOWN_ARROW. */
    val CLOSE = listOf("110000011", "111000111", "011101110", "001111100", "000111000", "001111100", "011101110", "111000111", "110000011")
    val UP_ARROW = listOf("0000110000", "0001111000", "0011111100", "0110110110", "0100110010", "0000110000", "0000110000", "0000110000", "0000110000")
    val DOWN_ARROW = listOf("0000000000", "0000110000", "0000110000", "0000110000", "0000110000", "0100110010", "0110110110", "0011111100", "0001111000", "0000110000")

    /** LogSearchScreen.drawNoSearchResults: MissingNo in the middle of the box and "(No results)" under it. */
    @Composable
    private fun NoResults() {
        val x = BOX_X + (BOX_W - 24) / 2; val y = BOX_Y + (BOX_H - 56) / 2 - 4
        picture("missingno")?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.dp, y.dp).size(24.dp, 56.dp)) }
        Text(BOX_X, y + 58, "(No results)", Pc.Text, width = BOX_W, align = TextAlign.Center)
    }

    // ---- Pokemon --------------------------------------------------------------------------------------------------------

    /**
     * LogTabPokemon: the icons 32 by 32 from (21, 14), 55 across and 36 down, four by four a page, each Pokemon's name
     * over the top of its icon on a see-through box; a click opens its page.
     */
    @Composable
    private fun PokemonGrid(d: PcLogData, v: View, set: (View) -> Unit) {
        val rows = pokemonRows(d, v)
        val per = MON_COLS * MON_ROWS
        val page = v.page.coerceIn(1, pages(rows.size, per))
        val shown = rows.drop((page - 1) * per).take(per)
        if (shown.isEmpty()) { NoResults(); return }
        shown.forEachIndexed { i, p ->
            val x = BOX_X + 19 + (i % MON_COLS) * 55
            val y = BOX_Y + 2 + (i / MON_COLS) * 36
            val name = d.shownName(p)
            Icon(d.sprite(p), x, y, 32, name)
            val nameW = com.ironmonone.app.referenceWidth(name)
            BoxedText(x + (32 - nameW) / 2 - 2, y - 2, name, Pc.Text, Pc.Ground)
            Box(Modifier.offset(x.dp, y.dp).size(32.dp, 32.dp).clickable(role = Role.Button) { set(v.into(View(tab = Tab.POKEMON, pokemon = p))) })
        }
    }

    /**
     * LogTabPokemonDetails: the name in the highlight colour, "1: ability" and "2: ability", the icon and its evolutions
     * with the arrow, "Base Stats" with "Total: N" (or Show IVs / EVs for your own team's), the stat graph (a box at (8,
     * 65) 103 by 68 with ticks, bars green from 180 and red to 40, labels and values under), and the moves column at x
     * 120: the Levelup Moves and TM Moves tabs, "01  Move" rows 11 apart with the category icon at +90, STAB in green,
     * eight a page under evolutions, twelve without, and the up and down arrows at x 227.
     */
    @Composable
    private fun PokemonPage(sh: Shared, d: PcLogData, v: View, set: (View) -> Unit) {
        val p = v.pokemon ?: return
        val text = Pc.LowerText
        val fill = lowerFill()
        val id = d.speciesId(p)
        // ABILITIES, under the name.
        val abilities = p.abilities.filter { it.isNotBlank() }.distinct()
        abilities.take(2).forEachIndexed { i, a -> Text(BOX_X + 2, BOX_Y + 13 + i * LINE, "${i + 1}: ${if (a == "-" || a == "---") PcBesteon.BLANK else nice(a)}", text) }
        // The icon and the evolutions.
        val iconX = BOX_X + 75; val iconY = BOX_Y - 2
        Icon(d.sprite(p), iconX, iconY, 32, d.shownName(p))
        val evos = p.evolutions.mapNotNull { n -> d.pokemon(n) }
        val hasEvo = evos.isNotEmpty()
        if (hasEvo) {
            val perSet = 3
            val sets = (evos.size + perSet - 1) / perSet
            val set1 = v.evoSet.coerceIn(1, sets)
            BImage(PcBesteon.RIGHT_ARROW, iconX + 2 + 32, iconY + (32 + 7) / 2 - 3, text)
            if (sets > 1) BHit(iconX + 2 + 32, iconY + 16, 10, 10) { set(v.copy(evoSet = set1 % sets + 1)) }
            val methods = id?.let { d.evolutionLines(it) }.orEmpty()
            evos.drop((set1 - 1) * perSet).take(perSet).forEachIndexed { k, e ->
                val ex = 10 + iconX + 32 + 4 + k * (32 + 2)
                Icon(d.sprite(e), ex, iconY, 32, d.shownName(e))
                val method = shortEvo(methods.getOrNull((set1 - 1) * perSet + k) ?: methods.firstOrNull())
                val mw = com.ironmonone.app.referenceWidth(method)
                BoxedText(ex + maxOf((32 - mw) / 2, 0), iconY + 32 + 2, method, text, fill)
                Box(Modifier.offset(ex.dp, iconY.dp).size(32.dp, 39.dp).clickable(role = Role.Button) {
                    set(v.copy(pokemon = e, movesTab = MovesTab.LEVEL, movesPage = 1, evoSet = 1, statView = StatView.BST))
                })
            }
        }
        // THE STAT GRAPH: its header, Total or the IVs button, the box with its ticks, the bars, labels and values.
        val team = id?.let { sh.team[it] }
        val btnX = BOX_X + 66; val btnY = BOX_Y + 42
        val header = when (v.statView) { StatView.IVS -> "Your IVs"; StatView.EVS -> "Your EVs"; else -> "Base Stats" }
        Text(BOX_X + 5, btnY, header, text)
        if (team == null) Text(btnX + 1, btnY, "Total: ${p.bst}", text)
        else {
            BRect(btnX, btnY, 43, 11, Pc.LowerBorder, fill) { set(v.copy(statView = StatView.entries[(v.statView.ordinal + 1) % 3])) }
            Text(btnX + 1, btnY, when (v.statView) { StatView.BST -> "Show IVs"; StatView.IVS -> "Show EVs"; else -> "Show BST" }, Pc.Gold)
        }
        val sx = BOX_X + 6; val sy = BOX_Y + 53; val sw = 103; val shh = 68; val labelW = 17; val barW = 8
        BRect(sx, sy, sw, shh, Pc.LowerBorder, fill)
        val q = shh / 4f
        for (k in 0..4) {
            val ly = (sy + q * k).toInt(); val long = k % 2 == 0
            val len = if (long) 2 else 1
            Box(Modifier.offset((sx - len).dp, ly.dp).size(len.dp, 1.dp).background(Pc.LowerBorder))
            Box(Modifier.offset((sx + sw + 1).dp, ly.dp).size(len.dp, 1.dp).background(Pc.LowerBorder))
        }
        val keys = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        keys.forEachIndexed { i, key ->
            val shownVal = when (v.statView) {
                StatView.IVS -> team?.first?.getOrNull(i) ?: 0
                StatView.EVS -> team?.second?.getOrNull(i) ?: 0
                else -> LogSearch.statOf(p, key)
            }
            val barVal = if (v.statView == StatView.IVS) minOf(shownVal * 8, 255) else shownVal
            val barH = kotlin.math.floor(barVal / 255.0 * (shh - 2) + 0.5).toInt()
            val color = when { barVal >= 180 -> Pc.Positive; barVal <= 40 -> Pc.Negative; else -> text }
            val statX = sx + 1 + i * labelW
            if (barH > 0) Box(Modifier.offset((statX + (labelW - barW) / 2).dp, (sy + shh - barH - 1).dp).size((barW + 1).dp, (barH + 1).dp).background(color))
            val label = key.lowercase().replaceFirstChar { it.uppercase() }
            Text(statX + (3 - key.length) * 2, sy + shh + 1, label, text)
            Text(statX + (3 - "$shownVal".length) * 2, sy + shh + 11, "$shownVal", color)
        }
        // THE MOVES COLUMN.
        val mx = BOX_X + 118
        val my = BOX_Y + if (hasEvo) 42 else 0
        val lvlOn = v.movesTab == MovesTab.LEVEL
        Button(mx, my, 60, 11, "Levelup Moves", if (lvlOn) Pc.Gold else text, underline = lvlOn) { set(v.copy(movesTab = MovesTab.LEVEL, movesPage = 1)) }
        Button(mx + 70, my, 41, 11, "TM Moves", if (!lvlOn) Pc.Gold else text, underline = !lvlOn) { set(v.copy(movesTab = MovesTab.TM, movesPage = 1)) }
        val per = movesPerPage(p)
        val types = p.types.map { it.uppercase() }
        fun stab(move: String) = d.moveTypes[move.uppercase()]?.let { t -> com.ironmonone.tracker.Gen3Types.name(t).uppercase() in types } == true &&
            d.moveCategories[move.uppercase()] != "STA"
        val rows: List<MoveLine> = if (lvlOn) {
            p.evoMoves.map { MoveLine("Evo  ${nice(it)}", it, if (stab(it)) Pc.Positive else text) } +
                p.moves.map { (lv, mv) -> MoveLine(if (lv == 0) "Evo  ${nice(mv)}" else "%02d  %s".format(lv, nice(mv)), mv, if (stab(mv)) Pc.Positive else text) }
        } else d.tmRows(p).map { r ->
            if (r.label != null) MoveLine(r.label, null, Pc.Gold)
            else MoveLine("TM%02d  %s".format(r.number, nice(r.move)), r.move, if (stab(r.move)) Pc.Positive else text, r.unlearnable)
        }
        val total = pages(rows.size, per)
        val page = v.movesPage.coerceIn(1, total)
        rows.drop((page - 1) * per).take(per).forEachIndexed { i, r ->
            val y = my + 13 + i * LINE + (if (hasEvo) 0 else -2)
            BoxedText(mx + 1, y, r.label, r.color, fill)
            r.move?.let { mv -> BHit(mx, y, 80, 11) { sh.push(Side.Move(mv)) } }
            if (r.struck) Box(Modifier.offset((mx + 1).dp, (y + 6).dp).size((com.ironmonone.app.referenceWidth(r.label) + 4).dp, 1.dp).background(r.color.copy(alpha = 0.6f)))
            val cat = r.move?.let { d.moveCategories[it.uppercase()] }
            if (TrackerOptions.showCategoryIcons && r.move != null) when (cat) {
                "PHY" -> BImage(PcBesteon.PHYSICAL, mx + 90, y + 2, r.color)
                "SPE" -> BImage(PcBesteon.SPECIAL, mx + 90, y + 2, r.color)
            }
        }
        if (total > 1) {
            val ux = mx + 107
            val upY = my + 24 + (if (hasEvo) 0 else 10)
            val downY = my + 81 + (if (hasEvo) 0 else 30)
            BImage(UP_ARROW, ux, upY, text)
            BImage(DOWN_ARROW, ux, downY, text)
            BHit(ux, upY, 10, 10) { set(turn(d, v, forward = false)) }
            BHit(ux, downY, 10, 10) { set(turn(d, v, forward = true)) }
        }
        // The name last, over anything it crosses (drawTab draws it after the buttons).
        BoxedText(BOX_X + 3, BOX_Y + 2, d.shownName(p).uppercase(), Pc.Gold, fill)
    }

    /** The log writes moves and abilities in capitals; the tracker prints them as the game's data does ("Slow Start"). */
    internal fun nice(name: String): String =
        if (name.any { it.isLowerCase() }) name
        else name.lowercase().split(' ').joinToString(" ") { w -> w.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } } }

    private class MoveLine(val label: String, val move: String?, val color: Color, val struck: Boolean = false)

    /** Utils.getShortenedEvolutionsInfo: the method in a few letters ("Lv.16", "Fire", "Friend"). */
    private fun shortEvo(line: String?): String {
        val s = line?.trim().orEmpty()
        if (s.isEmpty() || s == PcBesteon.BLANK) return ""
        Regex("(?i)(?:lv|level)\\.?\\s*(\\d+)").find(s)?.let { return "Lv.${it.groupValues[1]}" }
        return s.split(' ').firstOrNull { it.isNotBlank() }?.take(7).orEmpty()
    }

    /** LogTabMisc: the three checkboxes at (7, 17) 12 apart, and the log's game, randomizer version, seed and settings. */
    @Composable
    private fun MiscTab(d: PcLogData, set: (View) -> Unit, v: View) {
        val opts = listOf(
            Triple("Show unlearnable Gym TMs", { TrackerOptions.logShowUnlearnableGymTms }, { b: Boolean -> TrackerOptions.logShowUnlearnableGymTms = b }),
            Triple("Show pre-evolutions", { TrackerOptions.logShowPreEvolutions }, { b: Boolean -> TrackerOptions.logShowPreEvolutions = b }),
            Triple("Use custom trainer names", { TrackerOptions.logCustomTrainerNames }, { b: Boolean -> TrackerOptions.logCustomTrainerNames = b }),
        )
        opts.forEachIndexed { i, (label, get, put) ->
            val x = BOX_X + 5; val y = BOX_Y + 5 + i * 12
            BRect(x, y, 8, 8, Pc.Border, Pc.Ground)
            if (get()) BText("X", x + 1, y - 1, Pc.Positive)
            Text(x + 9, y - 2, label, Pc.Text)
            BHit(x, y, 150, 8) { put(!get()); TrackerOptions.save(); set(v.copy()) }
        }
        val info = listOf("Pokémon Game:" to d.log.game, "Randomizer Version:" to d.log.version, "Random Seed:" to d.log.seed, "Settings String:" to d.log.settingsString)
        info.forEachIndexed { i, (k, value) ->
            val y = BOX_Y + 55 + i * 12
            Text(BOX_X + 4, y, k, Pc.Text)
            Text(BOX_X + 100, y, value, Pc.Gold, width = BOX_W - 102)
        }
    }

    // ---- Trainers -------------------------------------------------------------------------------------------------------

    /** LogTabTrainers.buildNavigation (and LogTabTMs'): "Filter by:" at (4, 13), each label 4 wider than its words, 6 apart. */
    fun filterBoxes(labels: List<String>): List<Pair<Int, Int>> {
        var x = BOX_X + (2 + com.ironmonone.app.referenceWidth("Filter by") + 6)
        return labels.map { l -> val w = com.ironmonone.app.referenceWidth(l) + 4; (x to w).also { x += w + 6 } }
    }

    @Composable
    private fun <T> FilterRow(entries: List<T>, label: (T) -> String, selected: T?, onPick: (T) -> Unit) {
        Text(BOX_X + 2, BOX_Y + 1, "Filter by:", Pc.Text)
        filterBoxes(entries.map(label)).forEachIndexed { i, (x, w) ->
            val on = entries[i] == selected
            Button(x, BOX_Y + 1, w, 11, label(entries[i]), if (on) Pc.Gold else Pc.Text, underline = on) { onPick(entries[i]) }
        }
    }

    /** LogTabTrainers.drawTrainerPortraitInfo: the name centred over the portrait, a ball a party member under it. */
    @Composable
    private fun TrainerTile(d: PcLogData, t: RandomizerLog.Trainer, x: Int, y: Int, onClick: () -> Unit) {
        Icon(d.portrait(t), x, y, 32, d.trainerName(t))
        val name = d.trainerName(t)
        Text(x + (32 - com.ironmonone.app.referenceWidth(name)) / 2 - 3, y - LINE, name, Pc.Text)
        val n = t.party.size
        val bx = x + 1 + (32 - n * 8) / 2
        repeat(n) { k -> Box(Modifier.offset((bx + k * 8).dp, (y + 34).dp)) { PcTrainerTeam(listOf(true)) } }
        Box(Modifier.offset(x.dp, y.dp).size(32.dp, 32.dp).clickable(role = Role.Button) { onClick() })
    }

    /** LogTabTrainers: the filters, then the portraits 32 by 32 from (17, 42), 56 across and 60 down, four by two a page. */
    @Composable
    private fun TrainersTab(sh: Shared, d: PcLogData, v: View) {
        FilterRow(LogTrainerFilter.entries, { it.label }, if (v.query.isNotEmpty()) LogTrainerFilter.ALL else v.trainerFilter) { f ->
            sh.set(v.copy(trainerFilter = f, page = 1, query = ""))
        }
        val rows = trainerRows(d, v)
        val per = TRAINER_COLS * TRAINER_ROWS
        val page = v.page.coerceIn(1, pages(rows.size, per))
        val shown = rows.drop((page - 1) * per).take(per)
        if (shown.isEmpty()) { NoResults(); return }
        shown.forEachIndexed { i, t ->
            TrainerTile(d, t, BOX_X + 15 + (i % TRAINER_COLS) * 56, BOX_Y + 30 + (i / TRAINER_COLS) * 60) {
                sh.set(v.into(View(tab = Tab.TRAINERS, trainer = t, trainerFilter = v.trainerFilter)), Side.Trainer(t))
            }
        }
    }

    /**
     * LogTabTrainerDetails: the class and name in the highlight colour, the gym's badge, the portrait, "1. Name" a party
     * member from (3, 94), and the party two to a row from (62, 14), 86 across and 49 down: the icon with its level, its
     * four moves (STAB in green) and its item.
     */
    @Composable
    private fun TrainerPage(sh: Shared, d: PcLogData, v: View) {
        val t = v.trainer ?: return
        val fill = Pc.Ground
        d.rules.gymNumber(t.number)?.let { g -> Icon(com.ironmonone.app.PcAssets.badge(pcDesktopCtx, d.badgeSet, g, true), BOX_X + 44, BOX_Y + 2, 16, "Gym $g") }
        Icon(d.portrait(t), BOX_X, BOX_Y + 20, 64, d.trainerName(t))
        val anyItem = t.party.any { !it.item.isNullOrBlank() }
        val moveLine = if (anyItem) LINE - 2 else LINE - 1
        t.party.take(6).forEachIndexed { i, m ->
            val mon = d.pokemon(m.name)
            val open: () -> Unit = { mon?.let { sh.set(v.into(View(tab = Tab.TRAINERS, pokemon = it, trainerFilter = v.trainerFilter))) } }
            BoxedText(BOX_X + 2, BOX_Y + 82 + i * (LINE - 1), "${i + 1}. ${d.shownName(m.name)}", Pc.Text, fill)
            BHit(BOX_X + 1, BOX_Y + 82 + i * (LINE - 1), 60, 10, open)
            val x = BOX_X + 60 + (i % 2) * 86; val y = BOX_Y + 2 + (i / 2) * 49
            Icon(mon?.let { d.sprite(it) }, x, y - 4, 32, m.name)
            BoxedText(x + 5, y + 30, "Lv.${m.level}", Pc.Text, fill)
            BHit(x, y, 32, 29, open)
            val types = mon?.types?.map { it.uppercase() }.orEmpty()
            var my = y + (if (anyItem) -2 else 0)
            mon?.let { d.log.movesAt(it, m.level) }.orEmpty().takeLast(4).forEach { mv ->
                val stab = d.moveTypes[mv.uppercase()]?.let { ty -> com.ironmonone.tracker.Gen3Types.name(ty).uppercase() in types } == true && d.moveCategories[mv.uppercase()] != "STA"
                BoxedText(x + 31, my, nice(mv), if (stab) Pc.Positive else Pc.Text, fill)
                BHit(x + 30, my, 56, moveLine) { sh.push(Side.Move(mv)) }
                my += moveLine
            }
            m.item?.takeIf { it.isNotBlank() }?.let { BoxedText(x + 31, my, nice(it), Pc.Gold, fill) }
        }
        BoxedText(BOX_X + 2, BOX_Y + 1, d.trainerClass(t).uppercase(), Pc.Gold, fill)
        BoxedText(BOX_X + 2, BOX_Y + 10, d.trainerName(t).uppercase(), Pc.Gold, fill)
    }

    // ---- Routes ---------------------------------------------------------------------------------------------------------

    /** LogTabRoutes' columns, 230 wide: the icon, LOCATION, the wild Pokemon and their levels, the trainers and theirs. */
    private val ROUTE_COLS = listOf(0 to 21, 21 to 105, 126 to 18, 144 to 34, 178 to 18, 196 to 34)

    /** LogTabRoutes: the header row of icons and labels, then a row 21 tall a route from (5, 29), five a page. */
    @Composable
    private fun RoutesTab(sh: Shared, d: PcLogData, v: View) {
        val x0 = BOX_X + 3
        Text(x0 + ROUTE_COLS[1].first + 3 + 3, BOX_Y + 5, "LOCATION", Pc.Gold)
        listOf(2 to "tiny-nidoranm", 4 to "boy-e").forEach { (c, n) ->
            val (cx, cw) = ROUTE_COLS[c]; val bmp = picture(n)
            if (bmp != null) Image(bmp, null, filterQuality = FilterQuality.None, modifier = Modifier.offset((x0 + cx + 3 + (16 - bmp.width) / 2 + 1).dp, (BOX_Y + 1 + 16 - bmp.height - 1).dp).size(bmp.width.dp, bmp.height.dp))
        }
        listOf(3, 5).forEach { c -> val (cx, cw) = ROUTE_COLS[c]; Text(x0 + cx, BOX_Y + 5, "LV.", Pc.Gold, width = cw, align = TextAlign.Center) }
        val rows = routeRows(d, v)
        val page = v.page.coerceIn(1, pages(rows.size, ROUTE_ROWS))
        val shown = rows.drop((page - 1) * ROUTE_ROWS).take(ROUTE_ROWS)
        if (shown.isEmpty()) { NoResults(); return }
        val lowerFill = lowerFill()
        shown.forEachIndexed { i, r ->
            val y = BOX_Y + 17 + i * 21
            ROUTE_COLS.forEachIndexed { k, (cx, cw) -> BRect(x0 + cx, y, cw, 21, Pc.Border, if (k == 2 || k == 3) lowerFill else Pc.Ground) }
            Text(x0 + ROUTE_COLS[1].first + 3, y + 6, nice(r.name), Pc.Text, width = ROUTE_COLS[1].second - 4)
            if (r.numWilds > 0) CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides lowerFill) {
                Text(x0 + ROUTE_COLS[2].first, y + 6, "${r.numWilds}", Pc.LowerText, width = ROUTE_COLS[2].second, align = TextAlign.Center)
                Text(x0 + ROUTE_COLS[3].first, y + 6, "${r.minWildLv} -- ${r.maxWildLv}", Pc.LowerText, width = ROUTE_COLS[3].second, align = TextAlign.Center)
            }
            if (r.numTrainers > 0) {
                Text(x0 + ROUTE_COLS[4].first, y + 6, "${r.numTrainers}", Pc.Text, width = ROUTE_COLS[4].second, align = TextAlign.Center)
                Text(x0 + ROUTE_COLS[5].first, y + 6, "${r.minTrainerLv} -- ${r.maxTrainerLv}", Pc.Text, width = ROUTE_COLS[5].second, align = TextAlign.Center)
            }
            BHit(x0, y, 230, 21) { sh.set(v.into(View(tab = Tab.ROUTES, route = r)), Side.Route(r)) }
        }
    }

    /** LogTabRouteDetails' encounter tabs: their pictures (PixelImages GRASS, SURFING, FISHING_ROD, ROCK; the player's head). */
    @Composable
    private fun EncIcon(a: LogEncType, x: Int, y: Int) {
        when (a) {
            LogEncType.TRAINERS -> picture("boy-e")?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.dp, (y + 1).dp).size(it.width.dp, it.height.dp)) }
            LogEncType.GRASS -> BPix(BImg.GRASS, x, y, bColors(BImg.GRASS_COLORS))
            LogEncType.SURFING -> BPix(BImg.SURFING, x, y, bColors(BImg.SURFING_COLORS))
            LogEncType.ROCKSMASH -> BPix(BImg.ROCK, x, y, bColors(BImg.ROCK_COLORS))
            else -> BPix(BImg.FISHING_ROD, x, y, bColors(BImg.FISHING_ROD_COLORS))
        }
    }

    /**
     * LogTabRouteDetails: the route's name, the encounters box at the right (69 wide, the bottom 96 of the tab) with the
     * chosen area's name and picture and a picture and count per area, two to a row; the trainers (portraits from (9, 40))
     * or the Pokemon (from (9, 33), the name over, the levels and the rate under), 56 across and 64 down.
     */
    @Composable
    private fun RoutePage(sh: Shared, d: PcLogData, v: View) {
        val r = v.route ?: return
        val fill = Pc.Ground
        val areas = d.areas(r)
        val area = areaOf(d, v)
        val bx = BOX_X + BOX_W - 68; val by = BOX_Y + BOX_H - 96
        BRect(bx, by, 68, 96, Pc.Border, Color.Transparent)
        val navX = BOX_X + BOX_W - 69 + 3
        Text(navX, 62, "Encounters:", Pc.Text)
        area?.let { a ->
            Text(navX - 3, BOX_Y + 7, a.label, Pc.Gold, width = 69, align = TextAlign.Center)
            EncIcon(a, navX + 23, BOX_Y + 7 + 14)
        }
        var ix = navX + 2; var iy = 62 + 11 + 5
        areas.forEachIndexed { k, a ->
            val total = if (a == LogEncType.TRAINERS) r.trainers.size else r.areas[a]?.size ?: 0
            EncIcon(a, ix, iy)
            Text(ix + 17, iy + 1, "$total", Pc.Text)
            if (a == area) {
                val w = 16 + com.ironmonone.app.referenceWidth("$total") + 4
                BRect(ix - 1, iy - 1, w, 17, Pc.Gold, Color.Transparent)
            }
            BHit(ix, iy, 34, 16) { sh.set(v.copy(area = a, page = 1)) }
            if (k % 2 == 0) ix += 16 + 17 else { ix -= 16 + 17; iy += 16 + 4 }
        }
        val per = 6
        if (area == LogEncType.TRAINERS) {
            val page = v.page.coerceIn(1, pages(r.trainers.size, per))
            r.trainers.drop((page - 1) * per).take(per).forEachIndexed { i, t ->
                TrainerTile(d, t, BOX_X + 7 + (i % 3) * 56, BOX_Y + 28 + (i / 3) * 64) {
                    sh.set(v.into(View(tab = Tab.ROUTES, trainer = t)), Side.Trainer(t))
                }
            }
        } else {
            val wilds = area?.let { r.areas[it] }.orEmpty()
            val page = v.page.coerceIn(1, pages(wilds.size, per))
            wilds.drop((page - 1) * per).take(per).forEachIndexed { i, w ->
                val x = BOX_X + 7 + (i % 3) * 56; val y = BOX_Y + 21 + (i / 3) * 64
                val mon = w.pokemon
                Icon(mon?.let { d.sprite(it) }, x, y, 32, w.name)
                BoxedText(x - 3, y - 4, d.shownName(w.name), Pc.Text, fill)
                val lv = if (w.levelMin == w.levelMax) "Lv. ${w.levelMin}" else "Lv. ${w.levelMin} -- ${w.levelMax}"
                BoxedText(x - 3, y + 34, lv, Pc.Text, fill)
                val rate = "(${(w.rate * 100).toInt()}%)"
                BoxedText(x + (32 - com.ironmonone.app.referenceWidth(rate)) / 2 - 2, y + 43, rate, Pc.Text, fill)
                mon?.let { p -> BHit(x, y, 32, 32) { sh.set(v.into(View(tab = Tab.ROUTES, pokemon = p))) } }
            }
        }
        BoxedText(BOX_X + 3, BOX_Y + 2, nice(r.name), Pc.Gold, fill)
    }

    // ---- TMs ------------------------------------------------------------------------------------------------------------

    /**
     * LogTabTMs: the filters (TM #, Gym TMs); by number, "TM01  Move" 80 by 11 from (27, 26), nine down and two across;
     * by gym, seven a page from (39, 28) 16 apart, the gym's badge before it, the leader and "Gym N" after it. A move
     * opens its info on the panel, a leader his page and his TrainerInfoScreen.
     */
    @Composable
    private fun TmsTab(sh: Shared, d: PcLogData, v: View) {
        FilterRow(LogTmFilter.entries, { it.label }, v.tmFilter) { f -> sh.set(v.copy(tmFilter = f, page = 1)) }
        if (v.tmFilter == LogTmFilter.GYM) {
            val page = v.page.coerceIn(1, pages(d.gymTms.size, GYM_ROWS))
            d.gymTms.drop((page - 1) * GYM_ROWS).take(GYM_ROWS).forEachIndexed { i, g ->
                val x = BOX_X + 25 + 12; val y = BOX_Y + 14 + 2 + i * 16
                Icon(com.ironmonone.app.PcAssets.badge(pcDesktopCtx, d.badgeSet, g.gym, true), x - 18, y - 2, 16, "Gym ${g.gym}")
                Text(x + 1, y, "TM%02d  %s".format(g.number, nice(g.move)), Pc.Text)
                BHit(x, y, 80, 11) { sh.push(Side.Move(g.move)) }
                val leader = g.leader
                Text(x + 97 + 1, y, leader?.let { d.trainerName(it) } ?: "", Pc.Text)
                Text(x + 97 + 55, y, "Gym ${g.gym}", Pc.Text)
                if (leader != null) BHit(x + 97, y, 90, 11) { sh.set(v.into(View(tab = Tab.TMS, trainer = leader, tmFilter = v.tmFilter)), Side.Trainer(leader)) }
            }
            return
        }
        val per = TM_ROWS * 2
        val page = v.page.coerceIn(1, pages(d.log.tms.size, per))
        d.log.tms.drop((page - 1) * per).take(per).forEachIndexed { i, tm ->
            val x = BOX_X + 25 + (i / TM_ROWS) * 97; val y = BOX_Y + 14 + (i % TM_ROWS) * 13
            Text(x + 1, y, "TM%02d  %s".format(tm.number, nice(tm.move)), Pc.Text)
            BHit(x, y, 80, 11) { sh.push(Side.Move(tm.move)) }
        }
    }

    // ---- the renderer ---------------------------------------------------------------------------------------------------

    /**
     * One overlay kept alive while it is open, at the window's [scale] (1 game pixel = [scale] picture pixels): each
     * [render] draws once, and a click or the wheel goes to the same composition (PcPanel.Renderer's way). [shared] is
     * the panel's too: its search screen and the log's Pokemon card read and set the same view.
     */
    class Renderer(val scale: Float = 1f, val shared: Shared = Shared(), val upscale: Int = 1) : AutoCloseable {
        /** The view as it stands: a test reads it. */
        val view: View get() = shared.view
        val data: PcLogData? get() = shared.data
        /** The X clicked at the top level. */
        var onClose: () -> Unit
            get() = shared.onClose
            set(f) { shared.onClose = f }
        val width: Int = (WIDTH * scale).roundToInt()
        val height: Int = (HEIGHT * scale).roundToInt()
        private val scene = ImageComposeScene(width, height, Density(scale)) { Screen(shared) }
        private var clock = 0L

        /**
         * LogOverlay.parseAndDisplay: the Pokemon tab, then the lead's own page when the party has one, with the grid
         * under it for the back arrow.
         */
        fun open(d: PcLogData?, leadSpecies: Int?) {
            val grid = View()
            val lead = leadSpecies?.let { sp -> d?.log?.pokemon?.firstOrNull { d.speciesId(it) == sp } }
            shared.data = d
            shared.sortOpen = false; shared.filterOpen = false
            shared.view = if (lead != null) grid.into(View(pokemon = lead)) else grid
        }

        fun set(v: View) { shared.set(v) }

        fun render(): ByteArray {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            clock += 16_000_000L
            var img = scene.render(clock)
            if (scene.hasInvalidations()) { clock += 16_000_000L; img.close(); img = scene.render(clock) }
            return try { PcBmp.of(img, upscale) } finally { img.close() }
        }

        /** A left click at game pixel ([x], [y]). */
        fun click(x: Int, y: Int) {
            val at = Offset((x + 0.5f) * scale, (y + 0.5f) * scale)
            clock += 16_000_000L
            scene.sendPointerEvent(PointerEventType.Move, at, timeMillis = clock / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Press, at, timeMillis = clock / 1_000_000)
            clock += 50_000_000L
            scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = clock / 1_000_000)
        }

        /** The wheel, as besteon's Input.checkMouseWheel: up is the page before, down the page after. */
        fun wheel(delta: Int) {
            val d = data ?: return
            if (delta == 0) return
            set(turn(d, view, forward = delta < 0))
        }

        /** The search as typed (the panel's keyboard): the tab's list narrowed to it, from its first page. */
        fun search(text: String) {
            val v = view
            set(v.copy(query = text.trim().take(10), page = 1, pokemon = null, trainer = null, route = null, history = emptyList()))
        }

        override fun close() = scene.close()
    }
}
