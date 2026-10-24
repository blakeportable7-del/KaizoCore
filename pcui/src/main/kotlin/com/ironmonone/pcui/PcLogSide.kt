package com.ironmonone.pcui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.LocalPcCtx
import com.ironmonone.app.LogSearch
import com.ironmonone.app.Pc
import com.ironmonone.app.PcAssets
import com.ironmonone.app.PixText
import com.ironmonone.app.referenceWidth
import com.ironmonone.app.rp

/**
 * The panel beside the log viewer (PLAN row 7), ported from besteon's Ironmon-Tracker (MIT): LogSearchScreen while a
 * list is up (the Pokemon, trainers or routes), the Pokemon's InfoScreen card while its page is. Both read and set the
 * viewer's own view ([PcLogOverlay.Shared]), so a key typed here narrows the grid over the game at once.
 */
internal object PcLogSide {
    /** LogSearchScreen.KeyboardCharacters. */
    val KEYS = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

    // LogSearchScreen.createButtons' boxes, in panel pixels: the top box (5, 15, 140, 140), the keyboard 60 tall at its
    // foot, the clear and backspace buttons over it, the text field left of them, the two drop-downs 90 by 13 at x 45.
    const val KEY_W = 11
    const val KEY_H = 11
    const val KEY_X = 11
    const val KEY_Y = 100
    const val CLEAR_X = 130
    const val BACK_X = 115
    const val FIELD_Y = 80
    const val SORT_Y = 18
    const val FILTER_Y = 64
    const val DROP_X = 45

    /** Key [c]'s box (x, y, w, h): rows 13 apart, the third and fourth set in by half and a whole key; the space after M. */
    fun keyBox(c: Char): IntArray? {
        if (c == '_') return keyBox('M')?.let { m -> intArrayOf(m[0] + KEY_W + 2, m[1], KEY_W * 2 + 2, KEY_H) }
        KEYS.forEachIndexed { row, keys ->
            val i = keys.indexOf(c)
            if (i >= 0) {
                val inset = if (row >= 2) kotlin.math.floor((row + 1 - 2) * (0.5 * KEY_W) + 0.5).toInt() else 0
                return intArrayOf(KEY_X + inset + i * (KEY_W + 2), KEY_Y + row * (KEY_H + 2), KEY_W, KEY_H)
            }
        }
        return null
    }

    /** LogSearchScreen.drawScreen. */
    @Composable
    fun Search(sh: PcLogOverlay.Shared) {
        val v = sh.view
        val upper = Pc.Ground; val border = Pc.Border; val text = Pc.Text
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 15, 140, 140, border, upper)
            // The title is above the box, on the page.
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { BText("SEARCH THE LOG", 5, 3, Pc.Header) }
            BText("Sort by:", 8, SORT_Y + 1, text)
            BText("Search:", 8, FILTER_Y + 1, text)
            // The keyboard's box, in the top box's colours so the keys stand out.
            BRect(5, 95, 140, 60, border, upper)
            // The text field: ten slots, a line under each empty one, the letters centred over theirs.
            val fieldW = BACK_X - 2 * 3 - 5
            BRect(8, FIELD_Y, fieldW, 12, border, upper)
            for (i in 0 until 10) {
                val x = 8 + i * 10 + 2 + fieldW / 2 - 50
                val ch = v.query.getOrNull(i)
                if (ch != null) BText(ch.toString(), x, FIELD_Y + 1, text, w = 8, align = TextAlign.Center)
                else Box(Modifier.offset(x.rp, (FIELD_Y + 10).rp).size(8.rp, 1.rp).background(text))
            }
            // Backspace and clear.
            BRect(BACK_X, FIELD_Y, 12, 12, border, upper) { if (v.query.isNotEmpty() && !sh.filterOpen && !sh.sortOpen) sh.setQuery(v.query.dropLast(1)) }
            BImage(PcBesteon.LEFT_ARROW, BACK_X + 2, FIELD_Y + 1, text)
            BRect(CLEAR_X, FIELD_Y, 12, 12, border, upper) { if (v.query.isNotEmpty() && !sh.filterOpen && !sh.sortOpen) sh.setQuery("") }
            BImage(PcLogOverlay.CLOSE, CLEAR_X + 2, FIELD_Y + 2, text)
            // The keys.
            (KEYS.joinToString("") + "_").forEach { c ->
                val b = keyBox(c) ?: return@forEach
                BRect(b[0], b[1], b[2], b[3], Pc.LowerBorder, upper) {
                    if (!sh.filterOpen && !sh.sortOpen && v.query.length < 10) sh.setQuery(v.query + (if (c == '_') ' ' else c))
                }
                BText(c.toString(), b[0], b[1], Pc.LowerText, w = b[2] + 1, align = TextAlign.Center)
            }
            // The drop-downs last, so an open list lies over the rest.
            val sorts = LogSearch.sortsFor(v.tab.log)
            val filters = LogSearch.filtersFor(v.tab.log)
            Dropdown(SORT_Y, v.sortOrDefault.label, open = sh.sortOpen, options = sorts.map { it.label }, current = sorts.indexOf(v.sortOrDefault),
                onToggle = { if (!sh.filterOpen) sh.sortOpen = !sh.sortOpen; sh.onChanged() },
                onPick = { i -> sh.sortOpen = false; sh.set(v.copy(sort = sorts[i], page = 1)) })
            Dropdown(FILTER_Y, v.filterOrDefault.label, open = sh.filterOpen, options = filters.map { it.label }, current = filters.indexOf(v.filterOrDefault),
                onToggle = { if (!sh.sortOpen) sh.filterOpen = !sh.filterOpen; sh.onChanged() },
                onPick = { i -> sh.filterOpen = false; sh.set(v.copy(filter = filters[i], page = 1)) })
        }
    }

    /** A drop-down 90 by 13 at (45, [y]): the choice, a rule and the down triangle; open, the choices stacked under it. */
    @Composable
    private fun Dropdown(y: Int, label: String, open: Boolean, options: List<String>, current: Int, onToggle: () -> Unit, onPick: (Int) -> Unit) {
        val fill = Pc.LowerGroundX ?: Pc.Ground
        BRect(DROP_X, y, 90, 13, Pc.LowerBorder, fill, onToggle)
        // The drop-down is the lower box's fill on the top box: its words are read on that fill.
        androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides fill) { DropdownWords(y, label, open, options, current, fill, onPick) }
    }

    @Composable
    private fun DropdownWords(y: Int, label: String, open: Boolean, options: List<String>, current: Int, fill: Color, onPick: (Int) -> Unit) {
        BText(label, DROP_X + 1, y + 1, Pc.LowerText)
        val size = TRIANGLE_DOWN.size + 2
        Box(Modifier.offset((DROP_X + 90 - size - 1).rp, (y + 1).rp).size(1.rp, 12.rp).background(Pc.LowerBorder))
        BImage(TRIANGLE_DOWN, DROP_X + 90 - size + 1, y + 1, Pc.LowerText)
        if (open) options.forEachIndexed { i, o ->
            val oy = y + 13 + i * 13
            BRect(DROP_X, oy, 90, 13, Pc.LowerBorder, fill) { onPick(i) }
            BText(o, DROP_X + 1, oy + 1, if (i == current) Pc.Gold else Pc.LowerText)
        }
    }

    /** Constants.PixelImages.TRIANGLE_DOWN, 11 by 11. */
    private val TRIANGLE_DOWN = listOf(
        "00000000000", "00000000000", "00000000000", "01111111110", "00111111100", "00011111000",
        "00001110000", "00000100000", "00000000000", "00000000000", "00000000000",
    )

    /** What the panel shows beside the viewer, besteon's way: the screen a click asked for, else the view's own. */
    @Composable
    fun Panel(sh: PcLogOverlay.Shared, input: PcPanel.Input, onNote: () -> Unit) {
        val d = sh.data ?: return
        val v = sh.view
        when (val s = sh.side) {
            is PcLogOverlay.Side.Move -> MoveInfo(sh, d, s.name)
            is PcLogOverlay.Side.Trainer -> TrainerInfo(sh, d, s.trainer)
            is PcLogOverlay.Side.Route -> RouteInfo(sh, d, s.route)
            is PcLogOverlay.Side.History -> History(sh, d, s.pokemon)
            is PcLogOverlay.Side.Defenses -> Defenses(sh, d, s.pokemon)
            null -> when {
                v.pokemon != null -> PokemonInfo(sh, v.pokemon, input, onNote)
                !v.inPage && v.tab in setOf(PcLogOverlay.Tab.POKEMON, PcLogOverlay.Tab.TRAINERS, PcLogOverlay.Tab.ROUTES) -> Search(sh)
                // Any other tab: Bulbasaur's card, as LogSearchScreen.tryDisplayOrHide leaves it.
                else -> d.pokemonRows("").firstOrNull()?.let { PokemonInfo(sh, it, input, onNote) }
            }
        }
    }

    /** The screens' common frame: the box (5, 5, 140, 150) and the back arrow at (129, 142). */
    @Composable
    private fun Frame(sh: PcLogOverlay.Shared, content: @Composable () -> Unit) {
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 5, 140, 150, Pc.Border, Pc.Ground)
            content()
            BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.Text)
            BHit(124, 140, 21, 15) { sh.back() }
        }
    }

    /** InfoScreen.drawMoveInfoScreen: the name, the type box, Category, Contact, PP, Power, Accuracy, Priority, then the Move Summary box. */
    @Composable
    private fun MoveInfo(sh: PcLogOverlay.Shared, d: PcLogData, name: String) {
        val id = d.moveId(name) ?: 0
        val r = d.moveRow(id)
        val text = Pc.Text
        val priority = r?.priority?.takeIf { it != 0 }
        val bot = 85 + (if (priority != null) 10 else 0)
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 5, 140, bot - 10 - 8, Pc.Border, Pc.Ground)
            PixText((r?.name ?: PcLogOverlay.nice(name)).uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
            BRect(112, 22, 31, 13, Pc.Border, Pc.Border)
            r?.type?.let { t -> PcAssets.typeIcon(LocalPcCtx.current, com.ironmonone.tracker.Gen3Types.name(t))?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(113.rp, 23.rp).size(30.rp, 12.rp)) } }
            var y = 20
            val cat = when (r?.category) { "PHY" -> "Physical"; "SPE" -> "Special"; "STA" -> "Status"; else -> PcBesteon.BLANK }
            when (r?.category) { "PHY" -> BImage(PcBesteon.PHYSICAL, 87, y + 2, text); "SPE" -> BImage(PcBesteon.SPECIAL, 84, y + 2, text) }
            val rows = listOfNotNull(
                "Category" to cat,
                "Contact" to when (r?.contact) { true -> "Yes"; false -> "No"; null -> PcBesteon.BLANK },
                "PP" to (r?.pp?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK),
                "Power" to (r?.power?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK),
                "Accuracy" to (r?.acc?.takeIf { it > 0 }?.let { "$it%" } ?: PcBesteon.BLANK),
                priority?.let { "Priority" to "$it" },
            )
            rows.forEachIndexed { i, (label, value) -> BText("$label:", 7, y + i * 10, text); BText(value, 51, y + i * 10, text) }
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { BText("Move Summary:", 5, bot - 11, Pc.Header) }
            BRect(5, bot, 140, 150 - bot + 5, Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground)
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides (Pc.LowerGroundX ?: Pc.Ground)) {
                wrapWords(d.moveDescription(id) ?: "", 31).forEachIndexed { i, l -> BText(l, 6, bot + 1 + i * 11, Pc.LowerText) }
            }
            BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.LowerText)
            BHit(124, 140, 21, 15) { sh.back() }
        }
    }

    /** TrainerInfoScreen: the class and name, the route, the party size and levels, Avg. IVs, the AI, the items, the portrait, the party grid. */
    @Composable
    private fun TrainerInfo(sh: PcLogOverlay.Shared, d: PcLogData, t: com.ironmonone.app.RandomizerLog.Trainer) {
        val info = d.trainerInfo(t.number)
        val text = Pc.Text; val hi = Pc.Gold
        Frame(sh) {
            BText("${d.trainerClass(t)} ${d.trainerName(t)}".trim(), 7, 7, hi, w = 105)
            BImage(PcBesteon.MAP_PINDROP, 10, 20, text)
            BText(d.routeOfTrainer(t.number) ?: PcBesteon.BLANK, 21, 20, text, w = 90)
            val party = info?.party.orEmpty()
            BText("Pokémon:", 7, 35, text)
            BText(if (info == null) PcBesteon.BLANK else "${party.size}  (Lv.${info.minLevel}${if (info.maxLevel != info.minLevel) "-${info.maxLevel}" else ""})", 52, 35, text)
            BText("Avg. IVs:", 7, 45, text); BText(info?.avgIvs?.toString() ?: PcBesteon.BLANK, 52, 45, text)
            BText("A I Script:", 7, 55, text); BText(info?.aiLabel ?: PcBesteon.BLANK, 52, 55, text)
            val items = info?.items.orEmpty().filter { it > 0 }.groupingBy { it }.eachCount().map { (id, n) -> if (n == 1) d.itemName(id) else "$n ${d.itemName(id)}" }.sorted()
            if (items.isNotEmpty()) { BText("Usable Items:", 7, 65, text); BText(items.joinToString(", "), 14, 75, Pc.Positive, w = 130) }
            // The portrait at the top right, its number under it.
            Box(Modifier.offset(112.rp, 5.rp).size(33.rp, 34.rp).background(Pc.Border))
            Box(Modifier.offset(113.rp, 6.rp).size(31.rp, 32.rp).background(Pc.Ground))
            d.portrait(t)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(113.rp, 7.rp).size(32.rp)) }
            BText("# ${t.number}", 113, 39, text, w = 32, align = TextAlign.Center)
            if (info?.doubleBattle == true) { BText("Double", 113, 51, hi); BText("Battle!", 113, 61, hi) }
            // The party, three to a row from (27, 84).
            party.take(6).forEachIndexed { i, m ->
                val x = 27 + (i % 3) * 32; val y = 84 + (i / 3) * 32 + 4
                BRect(x, y, 32, 32, Pc.Border, Pc.Ground)
                if (info?.defeated == true) d.sprite(m.species)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.rp, (y - 4).rp).size(32.rp)) }
                else BPix(BImg.POKEBALL, x + 16 - 5, y + 7, listOf(Color.Black, Color(0xFFF04037.toInt()), Color.White))
                BText("Lv.${m.level}", x, y + 22, text, w = 33, align = TextAlign.Center)
                if (m.heldItem != 0) BPix(BImg.HELD_ITEM, x + 26, y + 1, bColors(BImg.HELD_ITEM_COLORS))
            }
        }
    }

    /** InfoScreen.drawRouteInfoScreen: the pin and the route's name, then each area's Pokemon with its rate. */
    @Composable
    private fun RouteInfo(sh: PcLogOverlay.Shared, d: PcLogData, r: com.ironmonone.app.LogRoute) {
        val text = Pc.Text; val lower = Pc.LowerText
        val area = d.areas(r).firstOrNull { it != com.ironmonone.app.LogEncType.TRAINERS }
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 5, 140, 30, Pc.Border, Pc.Ground)
            BImage(PcBesteon.MAP_PINDROP, 8, 8, text)
            BText(PcLogOverlay.nice(r.name), 18, 7, text, w = 125)
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { BText(if (area != null) "${area.label} Pokémon encounters" else "No wild Pokémon", 15, 37, Pc.Header) }
            val fill = Pc.LowerGroundX ?: Pc.Ground
            BRect(5, 48, 140, 107, Pc.LowerBorder, fill)
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides fill) {
            BText("In order of appearance:", 7, 48, lower)
            r.areas[area].orEmpty().take(12).forEachIndexed { i, w ->
                val x = 9 + (i % 4) * 34; val y = 62 + (i / 4) * 30
                w.pokemon?.let { p -> d.sprite(p)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.rp, y.rp).size(32.rp)) } }
                val rate = "${(w.rate * 100).toInt()}%"
                BText(rate, x + (32 - referenceWidth(rate)) / 2 - 2, y - 1, lower)
                w.pokemon?.let { p -> BHit(x, y, 32, 30) { sh.set(sh.view.into(PcLogOverlay.View(tab = sh.view.tab, pokemon = p))) } }
            }
            }
            BImage(PcBesteon.BACK_ARROW, 129, 142, lower)
            BHit(124, 140, 21, 15) { sh.back() }
        }
    }

    /** MoveHistoryScreen: the name, the learn-level boxes, "Move seen at level" with Min and Max, seven a page, newest first. */
    @Composable
    private fun History(sh: PcLogOverlay.Shared, d: PcLogData, p: com.ironmonone.app.RandomizerLog.Pokemon) {
        val id = d.speciesId(p) ?: 0
        val text = Pc.Text
        val seen = sh.movesSeen(id).sortedByDescending { it.minLv }
        Frame(sh) {
            PixText(d.shownName(p).uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
            LevelBoxes(p.moves.map { it.first }.filter { it > 1 }, sh.teamLevels[id], 6, 21, Pc.Border, Pc.Ground, text)
            BText("Move seen at level", 10, 50, Pc.Header)
            BText("Min", 92, 50, Pc.Header); BText("Max", 117, 50, Pc.Header)
            if (seen.isEmpty()) BText("(No tracked move data yet)", 18, 66, text)
            val pages = maxOf(1, (seen.size + 6) / 7)
            val page = sh.historyPage.coerceIn(1, pages)
            seen.drop((page - 1) * 7).take(7).forEachIndexed { i, m ->
                val y = 60 + i * 11
                BText(m.name, 17, y, text)
                BNumber("${m.minLv}", 94, y, 2, text)
                BNumber("${m.maxLv}", 119, y, 2, text)
                BHit(17, y, 80, 10) { sh.push(PcLogOverlay.Side.Move(m.name)) }
            }
            if (pages > 1) {
                BImage(PcBesteon.LEFT_ARROW, 37, 141, text); BText("$page/$pages", 51, 140, text, w = 40, align = TextAlign.Center); BImage(PcBesteon.RIGHT_ARROW, 96, 141, text)
                BHit(35, 140, 13, 11) { sh.historyPage = if (page <= 1) pages else page - 1; sh.onChanged() }
                BHit(94, 140, 13, 11) { sh.historyPage = if (page >= pages) 1 else page + 1; sh.onChanged() }
            }
        }
    }

    /** TypeDefensesScreen: the name, then 0x Immunities, 1/4x and 1/2x Resistances, 2x and 4x Weaknesses, four type boxes a line. */
    @Composable
    private fun Defenses(sh: PcLogOverlay.Shared, d: PcLogData, p: com.ironmonone.app.RandomizerLog.Pokemon) {
        val id = d.speciesId(p) ?: 0
        val eff = d.effectiveness(id)
        val text = Pc.Text
        val ctx = LocalPcCtx.current
        Frame(sh) {
            PixText(d.shownName(p).uppercase(), 12, text, Modifier.offset(5.rp, 3.rp))
            var y = 19
            listOf(0.0 to "0x Immunities", 0.25 to "1/4x Resistances", 0.5 to "1/2x Resistances", 2.0 to "2x Weaknesses", 4.0 to "4x Weaknesses").forEach { (m, label) ->
                val types = eff[m].orEmpty()
                if (types.isEmpty()) return@forEach
                BText(label, 10, y, text)
                types.forEachIndexed { i, t ->
                    val x = 14 + (i % 4) * 31; val ty = y + 13 + (i / 4) * 13
                    BRect(x - 1, ty - 1, 31, 13, Pc.Border, Pc.Border)
                    PcAssets.typeIcon(ctx, t)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(x.rp, ty.rp).size(30.rp, 12.rp)) }
                }
                y += 11 + 2 + ((types.size + 3) / 4) * 13 + 3
            }
        }
    }

    /** InfoScreen's learn-level boxes: eight a row 16 wide (nine 15 wide past sixteen), red once learned and green to come for a team member. */
    @Composable
    internal fun LevelBoxes(levels: List<Int>, level: Int?, x0: Int, y0: Int, border: Color, fill: Color, text: Color) {
        if (levels.isEmpty()) { BText("Does not learn any moves", x0 + 6, y0, text); return }
        val perRow = if (levels.size <= 16) 8 else 9
        val boxW = if (levels.size <= 16) 16 else 15
        val start = if (levels.size <= 16) 5 else 1
        levels.take(perRow * 2).forEachIndexed { i, lv ->
            val bx = x0 + (i % perRow) * boxW + start
            val by = y0 + (i / perRow) * 13 + 1
            BRect(bx, by, boxW, 13, border, fill)
            val color = when { level == null -> text; lv <= level -> Pc.Negative; else -> Pc.Positive }
            BText("$lv", bx + 2 + (2 - "$lv".length) * 3, by + 1, color)
        }
    }

    /**
     * InfoScreen.drawPokemonInfoScreen for the log's Pokemon: the name, the icon and the type boxes, BST and EXP, the
     * weight with the previous and next arrows, the evolution; under them "Learns a move at level:" with a box a level and
     * History, "Weak to:" and "Show resistances", the note strip and the back arrow.
     */
    @Composable
    fun PokemonInfo(sh: PcLogOverlay.Shared, p: com.ironmonone.app.RandomizerLog.Pokemon, input: PcPanel.Input, onNote: () -> Unit) {
        val d = sh.data ?: return
        val v = sh.view
        val id = d.speciesId(p) ?: 0
        val note = input.noteFor(id)
        val text = Pc.Text; val lower = Pc.LowerText
        val ctx = LocalPcCtx.current
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            // The top box: InfoScreen's (5, 5, 140, 58).
            BRect(5, 5, 140, 58, Pc.Border, Pc.Ground)
            PixText(d.shownName(p).uppercase(), 12, text, Modifier.offset(5.rp, 7.rp))
            d.sprite(p)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(113.rp, 2.rp).size(32.rp)) }
            p.types.take(2).forEachIndexed { i, t ->
                val ty = 34 + 14 * i
                BRect(112, ty, 31, 13, Pc.Border, Pc.Border)
                PcAssets.typeIcon(ctx, t)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(113.rp, (ty + 1).rp).size(30.rp, 12.rp)) }
            }
            var y = 20
            val level = sh.teamLevels[id]
            BText("BST: ${p.bst}", 7, y, text)
            BText("EXP: ${d.expYield(id) ?: PcBesteon.BLANK}", 49, y, if (level != null) Pc.Positive else text)
            y += 10
            BText("Weight:", 7, y, text)
            BText(d.weight(id)?.let { "$it kg" } ?: PcBesteon.BLANK, 49, y, text)
            // PreviousPokemon and NextPokemon at x 87 and 100, y 31.
            BImage(PcBesteon.LEFT_ARROW, 87, 31, text)
            BImage(PcBesteon.RIGHT_ARROW, 100, 31, text)
            fun go(step: Int) { d.neighbour(p, step)?.let { n -> sh.set(v.copy(pokemon = n, movesPage = 1, evoSet = 1, statView = PcLogOverlay.StatView.BST)) } }
            BHit(87, 31, 10, 10) { go(-1) }
            BHit(100, 31, 10, 10) { go(1) }
            y += 10
            val evo = d.evolutionLines(id)
            BText("Evolution:", 7, y, text)
            BText(evo.firstOrNull() ?: PcBesteon.BLANK, 49, y, text)
            evo.getOrNull(1)?.let { BText(it, 49, y + 10, text) }

            // The bottom box from y 63.
            val lowerFill = Pc.LowerGroundX ?: Pc.Ground
            BRect(5, 63, 140, 92, Pc.LowerBorder, lowerFill)
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides lowerFill) {
            BText("Learns a move at level:", 6, 64, lower)
            // MoveHistory: "History", underlined, at (113, 64).
            BText("History", 114, 64, lower)
            Box(Modifier.offset(115.rp, 73.rp).size(referenceWidth("History").rp, 1.rp).background(lower))
            BHit(113, 64, 30, 10) { sh.push(PcLogOverlay.Side.History(p)) }
            val levels = p.moves.map { it.first }.filter { it > 1 }
            LevelBoxes(levels, level, 6, 75, Pc.LowerBorder, lowerFill, lower)
            var by = 75 + 28
            if (levels.size <= (if (levels.size <= 16) 8 else 9)) by -= 10
            BText("Weak to:", 6, by, lower)
            BText("Show resistances", 0, by, lower, w = 143, align = TextAlign.End)
            Box(Modifier.offset((143 - referenceWidth("Show resistances") - 2).rp, (by + 9).rp).size((referenceWidth("Show resistances") + 3).rp, 1.rp).background(lower))
            BHit(40, by, 105, 12) { sh.push(PcLogOverlay.Side.Defenses(p)) }
            by += 13
            val eff = d.effectiveness(id)
            val list = eff.filterKeys { it > 1.0 }.entries.sortedByDescending { it.key }.flatMap { (m, ts) -> ts.map { it to m } }
            if (list.isEmpty()) BText("Has no weaknesses", 12, by, lower)
            var tx = 12
            list.forEach { (t, m) ->
                if (by > 128) return@forEach
                BRect(tx - 1, by - 1, 31, 13, Pc.LowerBorder, Pc.LowerBorder)
                PcAssets.typeIcon(ctx, t)?.let { Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.offset(tx.rp, by.rp).size(30.rp, 12.rp)) }
                if (m > 2.0) {
                    // Four times: the white bars over and under the box.
                    Box(Modifier.offset(tx.rp, by.rp).size(30.rp, 2.rp).background(Color.White))
                    Box(Modifier.offset(tx.rp, (by + 10).rp).size(30.rp, 2.rp).background(Color.White))
                }
                tx += 31
                if (tx > 150 - 30) { tx = 12; by += 13 }
            }
            // The note strip and the back arrow.
            if (note.isBlank()) {
                BImage(PcBesteon.NOTEPAD, 8, 141, lower)
                BText("(Leave a note)", 21, 141, lower)
            } else BText(note, 8, 141, lower, w = 115)
            }
            BHit(6, 140, 115, 13, onNote)
            BImage(PcBesteon.BACK_ARROW, 127, 142, lower)
            BHit(125, 142, 18, 12) { sh.set(v.back() ?: PcLogOverlay.View(tab = v.tab)) }
        }
    }
}
