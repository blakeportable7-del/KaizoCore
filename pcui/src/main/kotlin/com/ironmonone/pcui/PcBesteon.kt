package com.ironmonone.pcui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.EnemyView
import com.ironmonone.app.GhostCard
import com.ironmonone.app.InfoRules
import com.ironmonone.app.LocalPcCtx
import com.ironmonone.app.Pc
import com.ironmonone.app.PcAssets
import com.ironmonone.app.PcEffectGlyph
import com.ironmonone.app.PcHeals
import com.ironmonone.app.PcMove
import com.ironmonone.app.PcPixelImage
import com.ironmonone.app.PcPixelImageColors
import com.ironmonone.app.PcRef
import com.ironmonone.app.PcStageChevrons
import com.ironmonone.app.PcTrainerTeam
import com.ironmonone.app.PixText
import com.ironmonone.app.SWORD
import com.ironmonone.app.StatMarks
import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.enemyMoveContext
import com.ironmonone.app.natureMark
import com.ironmonone.app.ownMoveContext
import com.ironmonone.app.romPicture
import com.ironmonone.app.rp
import com.ironmonone.app.toPcMove
import com.ironmonone.app.typesOf
import com.ironmonone.tracker.EvoText
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.HnsRules
import com.ironmonone.tracker.LearnedMoves
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.MoveStars
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.effectiveWeather

/**
 * besteon's TrackerScreen (Ironmon-Tracker, MIT, screens/TrackerScreen.lua) drawn with KaizoCore's data: the PC
 * panel's own composable, not the phone's card (Blake, 2026-10-07: "we are building a pc tracker, this isn't android
 * anymore"). Every box, line and column is at the x and y TrackerScreen.lua draws it at, in panel pixels (its x less
 * Constants.SCREEN.WIDTH, 240): drawPokemonInfoArea, drawStatsArea, drawMovesArea and drawCarouselArea. The colours are
 * the tracker's theme (Pc), chosen in the settings screen.
 */
internal object PcBesteon {
    /** Where a 9-pixel PixText line sits against gui.drawText's y: the glyphs' tops on the reference's rows. */
    const val TY = 0

    /** Constants.PixelImages, verbatim. */
    val GEAR = listOf("00011000", "01111110", "01111110", "11100111", "11100111", "01111110", "01111110", "00011000")
    val PHYSICAL = listOf("1001001", "0101010", "0011100", "1111111", "0011100", "0101010", "1001001")
    val SPECIAL = listOf("0011100", "0100010", "1001001", "1010101", "1001001", "0100010", "0011100")
    val NOTEPAD = listOf(
        "00000000011", "00000000101", "11111111110", "10000001100", "10111010100", "10000000100",
        "10111110100", "10000000100", "10111110100", "10000000100", "11111111100",
    )
    val MAP_PINDROP = listOf(
        "00111100", "01111110", "11100111", "11000011", "11000011", "11100111",
        "01111110", "01111110", "00111100", "00111100", "00011000", "00011000",
    )
    /** 1 black, 2 white, 3 red, 4 yellow. */
    val BATTLE_BALLS = listOf(
        "0001110010000000", "0113331141000000", "0133333141001110", "1333133314114410",
        "1111211114444100", "1222122214441000", "0122222144444110", "0112221144111441",
        "1441114411333110", "0114444413333310", "0001444133313331", "0014444111121111",
        "0144114122212221", "0111001412222210", "0000001411222110", "0000000100111000",
    )
    val BATTLE_BALLS_COLORS = mapOf('1' to Color(0xFF000000), '2' to Color(0xFFFFFFFF), '3' to Color(0xFFFF0000), '4' to Color(0xFFFFFF00))
    val BACK_ARROW = listOf(
        "0000000000000000", "0000000000000000", "0000001000010000", "0000011000010000", "0000110000010000",
        "0001111111100000", "0000110000000000", "0000011000000000", "0000001000000000",
    )
    val LEFT_ARROW = listOf("0000000000", "0001100000", "0011000000", "0110000000", "1111111110", "1111111110", "0110000000", "0011000000", "0001100000")
    val SPARKLES = listOf("000000000000", "001000010000", "010100010000", "001000101000", "000011000110", "100000101000",
        "000000010000", "000100010000", "000100000000", "011011000100", "000100000000", "000100000000")
    val CLOCK = listOf("0011111100", "0100000010", "1000100001", "1000100001", "1000100001", "1000111001", "1000000001",
        "1000000001", "0100000010", "0011111100")
    val RIGHT_ARROW = listOf("0000000000", "0000011000", "0000001100", "0000000110", "0111111111", "0111111111", "0000000110", "0000001100", "0000011000")

    /** Constants.BLANKLINE. */
    const val BLANK = "---"
}

/** The fill behind the text [BText] is drawing (the app's LocalInkGround). A box with its own fill provides that fill. */
internal val LocalPanelInkGround = com.ironmonone.app.LocalInkGround

private fun lin(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun luminance(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

private fun contrast(a: Color, b: Color): Double {
    val l1 = luminance(a); val l2 = luminance(b)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

/**
 * The same hue, lightened or darkened until it is at least 4.5:1 on [background].
 * A type colour that matches the card (Ground yellow on a sand-coloured box) was unreadable; this is the rule that stops that.
 */
internal fun Color.readableOn(background: Color): Color = com.ironmonone.app.readableInk(this, background)

/** gui.drawText at (x, y) in panel pixels. */
@Composable
internal fun BText(text: String, x: Int, y: Int, color: Color, size: Int = PcRef.FONT, w: Int? = null, align: TextAlign = TextAlign.Start) {
    if (text.isEmpty()) return
    val ink = LocalPanelInkGround.current?.let { color.readableOn(it) } ?: color
    if (text == PcBesteon.BLANK) {
        // Constants.BLANKLINE: in the reference's font its three hyphens join into one bar, 5 wide, at the middle of the line.
        val bx = when (align) { TextAlign.End -> x + (w ?: 5) - 6; TextAlign.Center -> x + ((w ?: 5) - 5) / 2; else -> x + 1 }
        Box(Modifier.offset(bx.rp, (y + 5).rp).size(5.rp, 1.rp).background(ink))
        return
    }
    val m = Modifier.offset(x.rp, (y + PcBesteon.TY).rp)
    // A line with a width ends in an ellipsis when it does not fit, rather than being cut off mid-letter.
    PixText(text, size, ink, if (w != null) m.width(w.rp) else m, align, ellipsis = w != null)
}

/** Drawing.drawNumber: the number at x, or right justified in [spacing] digits of 5 pixels. */
@Composable
internal fun BNumber(text: String, x: Int, y: Int, spacing: Int, color: Color) {
    if (TrackerOptions.rightJustifiedNumbers) BText(text, x, y, color, w = spacing * 5 + 1, align = TextAlign.End)
    else BText(text, x, y, color)
}

/** gui.drawRectangle(x, y, w, h, border, fill): the outline covers x to x + w and y to y + h. */
@Composable
internal fun BRect(x: Int, y: Int, w: Int, h: Int, border: Color, fill: Color, onClick: (() -> Unit)? = null) {
    Box(Modifier.offset(x.rp, y.rp).size((w + 1).rp, (h + 1).rp).background(fill).border(1.rp, border)
        .then(if (onClick != null) Modifier.pcClick(onClick) else Modifier))
}

/**
 * Whether the mouse is over something clickable on the card, for the tracker window's hand cursor (Blake, 2026-10-11).
 * Every click area of the card counts the pointer in and out ([pcClick]); [PcPanel.Renderer.hoverAt] moves the pointer
 * away (every area counts it out) and then to the mouse, so the count is fresh on each look and nothing gets stuck.
 */
object PcHover {
    @Volatile var count = 0
}

/** A click area that also counts the mouse over it for [PcHover]. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.pcClick(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
    .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { PcHover.count++ }
    .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { PcHover.count = (PcHover.count - 1).coerceAtLeast(0) }

/** A clickable area with nothing drawn (the reference's NO_BORDER buttons and clickableArea). */
@Composable
internal fun BHit(x: Int, y: Int, w: Int, h: Int, onClick: () -> Unit) {
    Box(Modifier.offset(x.rp, y.rp).size(w.rp, h.rp).pcClick(onClick))
}

@Composable
internal fun BImage(rows: List<String>, x: Int, y: Int, color: Color) = PcPixelImage(rows, color, Modifier.offset(x.rp, y.rp))

/** Drawing.drawImageAsPixels for the BImg images: base-36 digit d is [colors] d - 1, 0 is clear. */
@Composable
internal fun BPix(rows: List<String>, x: Int, y: Int, colors: List<Color>) {
    val w = rows.maxOf { it.length }; val h = rows.size
    androidx.compose.foundation.Canvas(Modifier.offset(x.rp, y.rp).size(w.rp, h.rp)) {
        val u = size.width / w
        rows.forEachIndexed { ry, r -> r.forEachIndexed { rx, ch ->
            val d = Character.digit(ch, 36)
            if (d > 0) colors.getOrNull(d - 1)?.let { drawRect(it, androidx.compose.ui.geometry.Offset(rx * u, ry * u), androidx.compose.ui.geometry.Size(u, u)) }
        } }
    }
}

/**
 * The nature's mark beside a stat: + in blue, the minus in red, whatever the theme (Blake, 2026-10-09). Both are one 5 by
 * 5 glyph with the minus on the plus's middle row, so they are the same size, and each has a dark edge so a theme's
 * ground cannot swallow it. [x], [y] is the edge's top left; the glyph is 7 by 7 with it.
 */
@Composable
internal fun NatureMark(mark: Char, x: Int, y: Int) {
    val plus = mark == '+'
    BPix(if (plus) NatureMarks.PLUS else NatureMarks.MINUS, x, y, listOf(if (plus) NatureMarks.UP else NatureMarks.DOWN, NatureMarks.EDGE))
}

internal object NatureMarks {
    val UP = Color(0xFF4A90FF.toInt())
    val DOWN = Color(0xFFFF3A3A.toInt())
    val EDGE = Color(0xFF141414.toInt())
    val PLUS = edged(listOf("00100", "00100", "11111", "00100", "00100"))
    val MINUS = edged(listOf("00000", "00000", "11111", "00000", "00000"))

    /** [rows] padded by one on every side, each clear pixel next to a set one (diagonals too) turned into edge (2). */
    private fun edged(rows: List<String>): List<String> {
        val h = rows.size + 2; val w = rows[0].length + 2
        fun set(x: Int, y: Int) = rows.getOrNull(y - 1)?.getOrNull(x - 1) == '1'
        return (0 until h).map { y -> String(CharArray(w) { x ->
            when {
                set(x, y) -> '1'
                (-1..1).any { dy -> (-1..1).any { dx -> set(x + dx, y + dy) } } -> '2'
                else -> '0'
            }
        }) }
    }
}

/** A BImg image's own colours (its _COLORS list). */
internal fun bColors(argb: List<Long>): List<Color> = argb.map { Color(it.toInt()) }

@Composable
internal fun BPicture(bmp: ImageBitmap?, x: Int, y: Int, w: Int, h: Int) {
    if (bmp == null) return
    Image(bmp, null, Modifier.offset(x.rp, y.rp).size(w.rp, h.rp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}

/** What the tracker screen needs besides the tracker's read. */
internal class BesteonCtx(
    val input: PcPanel.Input,
    val spriteFor: (Int) -> ImageBitmap?,
    val onCycleMark: (Int, Int) -> Unit,
    val onGear: () -> Unit,
    val onNote: () -> Unit,
    val onPcHeal: (Int) -> Unit,
    /** The pedometer's Reset (or Total). */
    val onPedometerReset: () -> Unit = {},
    /** A shown move: its description. A blank row is not clickable. */
    val onMove: (PcMove) -> Unit = {},
    /** A shown ability name: its description. "?" and "---" are not clickable. */
    val onAbility: (String) -> Unit = {},
    /** The name or the sprite: the resistance chart for these live types. */
    val onTypes: (List<Int>) -> Unit = {},
    /** The heals-box stars: open this Pokemon's GachaMon card, as the phone does. */
    val onGacha: () -> Unit = {},
    /** besteon's screens a click opens over the card (PcScreens). */
    val screens: PcScreens = PcScreens(),
    /** The pedometer's Goal: the step goal typed on the panel's keyboard. */
    val onGoal: () -> Unit = {},
)

/** TrackerScreen.drawScreen: your Pokemon or the opponent, as Battle.isViewingOwn says. */
@Composable
internal fun BesteonTracker(c: BesteonCtx, st: TrackerState, viewingOwn: Boolean) {
    val page = Pc.Page
    Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(page)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPanelInkGround provides upperGround) {
        if (viewingOwn) OwnView(c, st) else EnemyView(c, st)
        // SETTINGS GEAR (drawn in the info box; the box is drawn first).
        BImage(PcBesteon.GEAR, 92, 7, Pc.Text)
        BHit(90, 5, 11, 11, c.onGear)
        Carousel(c, st, viewingOwn)
        }
    }
}

private val upperGround get() = Pc.Ground
private val upperBorder get() = Pc.Border
private val lowerGround get() = Pc.LowerGroundX ?: Pc.Ground
private val lowerBorder get() = Pc.LowerBorder
private val lowerText get() = Pc.LowerText

/** "Lv.14 (36)": the level, then the evolution in its colour ([own]: ready green, else Intermediate; the opponent's plain). */
@Composable
private fun LevelLine(level: Int, evo: EvoText.Label?, y: Int, own: Boolean) {
    Row(Modifier.offset(36.rp, (y + PcBesteon.TY).rp)) {
        PixText("Lv.$level" + if (evo != null) " (" else "", PcRef.FONT, Pc.Text)
        if (evo != null) {
            val color = if (!own) Pc.Text else when (evo.tone) {
                EvoText.Tone.READY -> Pc.Positive
                EvoText.Tone.WAITING -> Pc.Gold
                EvoText.Tone.PLAIN -> Pc.Text
            }
            val n = if (own) evo.highlighted.coerceIn(0, evo.text.length) else 0
            if (n > 0) PixText(evo.text.substring(0, n), PcRef.FONT, Pc.Positive)
            if (n < evo.text.length) PixText(evo.text.substring(n), PcRef.FONT, color)
            PixText(")", PcRef.FONT, Pc.Text)
        }
    }
}

/** The info box (5, 5, 96, 52) with the types, the name and the lines; the icon is drawn last, as the reference does. */
@Composable
private fun InfoBox(types: List<String>) {
    BRect(5, 5, 96, 52, upperBorder, upperGround)
    val ctx = LocalPcCtx.current
    types.take(2).forEachIndexed { i, t -> BPicture(PcAssets.typeIcon(ctx, t), 6, 33 + 12 * i, 30, 12) }
}

/** The types drawn on the card: the live battle types, without Roost's Mystery stand-in when a real type remains. */
private fun cardTypes(types: List<Int>): List<Int> {
    val real = types.distinct().filter { it != 9 }
    return if (real.isNotEmpty()) real else types.distinct()
}

@Composable
private fun Icon(species: Int, hns: Boolean, bmp: ImageBitmap?, status: String, look: com.ironmonone.app.WalkingPals.Look = com.ironmonone.app.WalkingPals.Look()) {
    // Drawn from x 5; the words start at x 36, so its pixels stop at 35 (half a pixel kept for rounding).
    val posed = PcPictures.posed(species, hns, faint = status == "FNT", asleep = status == "SLP", look, maxRight = 36f - 5f - 0.5f)
    // With the effects layer walking it (PcPictures.cardDrawsWalking false) the slot is left for the layer.
    if (posed != null) { if (PcPictures.cardDrawsWalking) BPicture(posed.image, 5 + posed.x, -1 + posed.y, posed.w, posed.h) }
    else BPicture(bmp, 5, -1, 32, 32)
    if (status.isNotEmpty()) BPicture(PcAssets.status(LocalPcCtx.current, status), 20, 6, 16, 8)
}

@Composable
private fun OwnView(c: BesteonCtx, st: TrackerState) {
    val p = st.lead ?: st.party.firstOrNull() ?: return
    val m = p.mon
    val hns = PcPanel.hnsOf(st)
    val ownTypes = cardTypes(typesOf(p))
    InfoBox(ownTypes.map { Gen3Types.name(it) })
    // The sprite before the words beside it: a wide one (Golisopod's claws) reaches under the name and HP, never over them.
    val ownLook = com.ironmonone.app.PalForms.lookFor(m.species, hns, ownTypes, m.shiny, m.pid)
    Icon(m.species, hns, PcPictures.chosen(m.species, hns, ownLook) ?: romPicture(p.picture) ?: c.spriteFor(m.species), if (m.curHp <= 0) "FNT" else p.statusCondition, ownLook)
    val exp = TrackerOptions.showExpBar && p.expTotal > 0
    val ls = if (exp) 9 else 10
    var y = 5
    val name = if (TrackerOptions.showNicknames && m.nickname.isNotBlank() && !m.nickname.equals(p.speciesName, ignoreCase = true)) m.nickname else p.speciesName
    BText(name, 36, y, Pc.Text)
    BHit(36, y, 54, 9) { c.onTypes(ownTypes) }
    y += ls
    BText("HP:", 36, y, Pc.Text)
    val hpColor = when {
        m.maxHp <= 0 || m.curHp <= 0 -> Pc.Text
        m.curHp.toFloat() / m.maxHp <= 0.2f -> Pc.Negative
        m.curHp.toFloat() / m.maxHp <= 0.5f -> Pc.Gold
        else -> Pc.Text
    }
    BText(if (m.curHp <= 0) "?/?" else "${m.curHp}/${m.maxHp}", 52, y, hpColor)
    // The HP and level lines: this Pokemon's info, its evolution among it.
    BHit(36, y, 54, ls * 2) { c.screens.push(PcScreens.Screen.PokemonInfo(m.species)) }
    y += ls
    LevelLine(m.level, p.evo, y, own = true); y += ls
    if (exp) {
        com.ironmonone.app.PcExpBar((p.expNow.toFloat() / p.expTotal), Modifier.offset(38.rp, (y + 2).rp))
        y += 5
    }
    // HELD ITEM AND ABILITIES, in Intermediate text.
    val rules = if (com.ironmonone.app.TrackerOptions.ruleMarks) com.ironmonone.app.MoveRule.rules(c.input.rulesMode, "HnS", natDex = hns) else null
    val run = com.ironmonone.app.RuleMarks.Run(c.input.rulesMode, natDex = hns, family = "HnS")
    val itemBan = com.ironmonone.app.RuleMarks.itemLine(run, p.itemName, p.moveNames)
    val abilityBan = com.ironmonone.app.RuleMarks.abilityLine(run, p.abilityName, p.base?.bst, p.evo != null)
    BText(p.itemName.takeIf { it != "-" } ?: "", 36, y, Pc.Gold, w = 53)
    if (m.heldItem > 0) BHit(36, y, 54, 9) { c.screens.push(PcScreens.Screen.ItemInfo(m.heldItem)) }
    if (itemBan != null) { BText("x", 90, y, Pc.Negative); BHit(89, y, 9, 9) { c.screens.push(PcScreens.Screen.Note("Banned item", itemBan)) } }
    y += ls
    BText(p.abilityName, 36, y, Pc.Gold, w = 53)
    if (abilityClickable(p.abilityName)) BHit(36, y, 60, 8) { c.onAbility(p.abilityName) }
    if (abilityBan != null) { BText("x", 90, y, Pc.Negative); BHit(89, y, 9, 9) { c.screens.push(PcScreens.Screen.Note("Banned ability", abilityBan)) } }

    // HEALS INFO (5, 57, 96, 23). HealsInBag: the words open the bag; the PC heal buttons and the stars keep their own clicks.
    BRect(5, 57, 96, 23, upperBorder, upperGround)
    BHit(5, 57, 76, 23) { c.screens.push(PcScreens.Screen.HealsInBag) }
    val pct = st.healPercent.coerceAtLeast(0)
    BText("Heals in Bag:", 6, 57, Pc.Text)
    BText(if (TrackerOptions.healsWhole) "${st.healHp} HP (${st.healCount})" else "$pct% HP (${st.healCount})", 6, 68, Pc.Text)
    if (TrackerOptions.trackPcHeals) {
        val n = PcHeals.count(c.input.attempt)
        BText("$n", (2 - "$n".length) * 5 + 87, 68, PcHeals.color(n))
        BText("+", 83, 69 - 3, Pc.Positive, size = 5)
        BText(PcBesteon.BLANK, 83, 73 - 3, Pc.Negative, size = 5)
        // The top half of the corner adds, the bottom half takes off: the 6 by 5 buttons were too small to hit.
        BHit(81, 64, 19, 9) { c.onPcHeal(+1) }
        BHit(81, 73, 19, 7) { c.onPcHeal(-1) }
    } else {
        // TrackerScreen.lua:1291: the same place shows GachaMon's stars when "Track PC Heals" is off.
        c.input.gachaStars?.let { (now, made) -> PcGachaStars.Draw(now, made, c.onGacha) }
    }

    // PokemonIcon (5, 5, 32, 27): Pokemon info; TypeDefenses (the type boxes under it): the resistances.
    BHit(5, 0, 32, 32) { c.screens.push(PcScreens.Screen.PokemonInfo(m.species)) }
    BHit(5, 32, 31, 25) { c.onTypes(ownTypes) }

    // STATS (101, 5, 44, 75); InvisibleStatsArea opens the Pokemon's info too.
    BRect(101, 5, 44, 75, upperBorder, upperGround)
    BHit(101, 5, 44, 75) { c.screens.push(PcScreens.Screen.PokemonInfo(m.species)) }
    val vals = listOf(m.maxHp, m.atk, m.def, m.spAtk, m.spDef, m.spe)
    StatMarks.STAT_NAMES.forEachIndexed { i, label ->
        val sy = 7 + 10 * i
        val mark = natureMark(m.nature, label)
        val color = when (mark) { '+' -> Pc.Positive; '-' -> Pc.Negative; else -> Pc.Text }
        BText(label, 102, sy, color)
        // Centred on the label's letters (rows sy+2 to sy+8): the 5 by 5 glyph's middle row on sy+5, its edge just right of the label (Blake, 2026-10-10).
        if (mark != null) NatureMark(mark, 116, sy + 2)
        if (st.inBattle) p.statStages[label]?.let { s -> if (s != 6) Box(Modifier.offset(122.rp, (sy + 1).rp)) { PcStageChevrons(s - 6) } }
        BNumber(if (vals[i] == 0) PcBesteon.BLANK else "${vals[i]}", 127, sy, 3, if (TrackerOptions.colorStatNumbers) color else Pc.Text)
    }
    BstRow(st.inBattle, p.statStages["ACC"] ?: 6, p.statStages["EVA"] ?: 6, p.base?.bst?.toString() ?: PcBesteon.BLANK)

    // MOVES. The marks use the types on the field (battleTypes): Conversion, Color Change and the rest, the same as the Android card.
    val ctx = ownMoveContext(p, st.enemy, st.effectiveWeather, null)
        .copy(ownCategories = hns && !HnsRules.categoryByType, hideEffectiveness = InfoRules.hideEffectiveness(st.randomized, st.isGhostBattle, own = true), generation = 3, natDex = hns)
    val header = if (p.movesTotal > 0) "Moves ${p.movesLearned}/${p.movesTotal}" else "Moves"
    val mark = com.ironmonone.app.MoveRule.gbaMark(rules, p, st, run)
    MovesArea(
        header = header, next = p.nextMoveLevel.takeIf { p.movesTotal > 0 }, nextHot = p.nextMoveLevel?.let { m.level + 1 >= it } == true,
        // "Show real hit chance" and "Show real move power": the numbers as they are now in this battle (PcReal).
        catchText = null, rows = PcReal.own(p.moveRows.map { it.toPcMove(ctx) }.map(mark), st, p, c.input.battle,
            st.enemy?.let { e -> c.input.marks?.abilitiesFor(e.species) }.orEmpty()), own = true, allowHidden = true,
        onMove = { mv -> c.screens.push(PcScreens.Screen.MoveInfo(mv.id)) },
        onHeader = { c.screens.push(PcScreens.Screen.MovesLearned(m.species, m.level, own = true)) },
    )
}

@Composable
private fun EnemyView(c: BesteonCtx, st: TrackerState) {
    val e = st.enemy!!
    val marks = c.input.marks
    val rand = st.randomized
    val liveTypes = if (e.isGhost) emptyList() else cardTypes(typesOf(e))
    val shownTypes = if (e.isGhost) InfoRules.typeIcons(GhostCard.types(e), InfoRules.hidesRandomizedTypes(rand)).map { it.first }
        else liveTypes.map { Gen3Types.name(it) }
    InfoBox(shownTypes)
    // The sprite before the words beside it, as on your own card.
    val hnsE = PcPanel.hnsOf(st)
    val enemyShiny = st.enemyParty.any { it.pid != 0L && it.pid == e.pid && it.shiny }
    val enemyLook = com.ironmonone.app.PalForms.lookFor(e.species, hnsE, liveTypes, enemyShiny, e.pid)
    Icon(e.species, hnsE, if (e.isGhost) PcAssets.gbaSprite(LocalPcCtx.current, GhostCard.SPRITE) else PcPictures.chosen(e.species, hnsE, enemyLook) ?: romPicture(e.picture) ?: c.spriteFor(e.species),
        if (e.curHp <= 0) "FNT" else e.statusCondition, enemyLook)
    BText(e.speciesName, 36, 5, Pc.Text)
    BHit(36, 5, 54, 9) { if (liveTypes.isNotEmpty()) c.onTypes(liveTypes) }
    LevelLine(e.level, e.evo, 15, own = false)
    val last = marks?.lastLevelSeen(e.species)
    BText(if (last != null) "Last seen Lv.$last" else "New encounter", 36, 25, if (InfoRules.canShowAbilities(rand)) Pc.Text else Pc.Gold)
    if (!e.isGhost) BHit(36, 15, 54, 20) { c.screens.push(PcScreens.Screen.PokemonInfo(e.species)) }
    // DataHelper.lua:234-242: the two possible abilities, or the ones seen.
    val a1 = marks?.abilityFor(e.species); val a2 = marks?.secondAbilityFor(e.species)
    val line1 = when {
        InfoRules.canShowAbilities(rand) -> e.abilityGuess.substringBefore(" / ").let { if (" / " in e.abilityGuess) "$it /" else it }
        a1 != null -> "$a1 /"
        else -> PcBesteon.BLANK
    }
    val line2 = when {
        InfoRules.canShowAbilities(rand) -> e.abilityGuess.substringAfter(" / ", PcBesteon.BLANK)
        a2 != null -> a2
        a1 != null -> "?"
        else -> PcBesteon.BLANK
    }
    BText(line1, 36, 35, Pc.Gold, w = 64)
    BText(line2, 36, 45, Pc.Gold, w = 64)
    if (abilityClickable(line1)) BHit(36, 35, 60, 8) { c.onAbility(line1) }
    if (abilityClickable(line2)) BHit(36, 45, 60, 8) { c.onAbility(line2) }

    // ENCOUNTER INFO (5, 57, 96, 23): RouteDetails in a wild battle, TrainerDetails in a trainer's.
    BRect(5, 57, 96, 23, upperBorder, upperGround)
    val wild = st.isWildBattle
    if (wild) st.mapId?.let { id -> BHit(6, 57, 95, 23) { c.screens.push(PcScreens.Screen.RouteInfo(id, st.encounterArea)) } }
    else st.opponentTrainerId?.let { id -> BHit(6, 57, 95, 23) { c.screens.push(PcScreens.Screen.TrainerInfo(id)) } }
    val encounters = (marks?.encounters(e.species, wild) ?: 0).coerceAtMost(999)
    if (wild) {
        BImage(PcBesteon.MAP_PINDROP, 8, 63, Pc.Text)
        BText("Seen (Wild): $encounters", 16, 58, Pc.Text)
        BText(PcLogOverlay.nice(c.input.routeName ?: ""), 16, 68, Pc.Text)
    } else {
        BText("Seen (Trainer): $encounters", 6, 58, Pc.Text)
        Box(Modifier.offset(7.rp, 70.rp)) { PcTrainerTeam(st.enemyTeam) }
        PcPixelImageColors(PcBesteon.BATTLE_BALLS, PcBesteon.BATTLE_BALLS_COLORS, Modifier.offset(83.rp, 61.rp))
    }

    if (!e.isGhost) {
        BHit(5, 0, 32, 32) { c.screens.push(PcScreens.Screen.PokemonInfo(e.species)) }
        BHit(5, 32, 31, 25) { if (liveTypes.isNotEmpty()) c.onTypes(liveTypes) }
    }

    // STATS: the marks, or an unrandomized opponent's base stats in Intermediate text.
    BRect(101, 5, 44, 75, upperBorder, upperGround)
    // The names and numbers open the Pokemon's info; the mark boxes, drawn after, keep their own click.
    if (!e.isGhost) BHit(101, 5, 44, 75) { c.screens.push(PcScreens.Screen.PokemonInfo(e.species)) }
    val b = e.base
    val showStats = b != null && InfoRules.canShowStats(rand)
    val m = marks?.of(e.species) ?: IntArray(StatMarks.COUNT)
    val baseVals = b?.let { listOf(it.hp, it.atk, it.def, it.spAtk, it.spDef, it.spe) }
    StatMarks.STAT_NAMES.forEachIndexed { i, label ->
        val sy = 7 + 10 * i
        BText(label, 102, sy, Pc.Text)
        e.statStages[label]?.let { s -> if (s != 6) Box(Modifier.offset(122.rp, (sy + 1).rp)) { PcStageChevrons(s - 6) } }
        if (showStats && baseVals != null) BNumber("${baseVals[i]}", 127, sy, 3, Pc.Gold)
        else {
            val state = m.getOrElse(i) { 0 }
            val bx = 129; val by = 9 + 10 * i
            BRect(bx, by, 8, 8, upperBorder, upperGround) { c.onCycleMark(e.species, i) }
            // The row's right end cycles the mark too, not only the 8 by 8 box: a near miss opened the whole info card.
            BHit(126, sy, 19, 10) { c.onCycleMark(e.species, i) }
            val sym = StatMarks.symbol(state).trim()
            if (sym.isNotEmpty()) BText(sym, bx, by - 1 - (if (state == 3) 1 else 0), PcMarks.color(state), w = 9, align = TextAlign.Center)
        }
    }
    val bstLines = com.ironmonone.app.BstRule.lines(c.input.rulesMode, natDex = hnsE, hns = hnsE)
    val bstBroken = wild && !e.isGhost && com.ironmonone.app.BstRule.wildBreaks(e.base?.bst, bstLines)
    BstRow(true, e.statStages["ACC"] ?: 6, e.statStages["EVA"] ?: 6, GhostCard.bst(e), bstBroken)
    if (bstBroken) BHit(117, 67, 9, 9) {
        c.screens.push(PcScreens.Screen.Note("BST over the line", "This wild ${e.speciesName} has a BST of ${e.base?.bst}. This run marks a wild Pokemon at BST ${bstLines?.wild} or above."))
    }

    // MOVES: the tracked ones (Tracker.getMoves), never a move not seen; the header counts the learnset at its level.
    val ctx = enemyMoveContext(e, st.lead, st.effectiveWeather, null)
        .copy(ownCategories = hnsE && !HnsRules.categoryByType, hide = InfoRules.hiddenMoveInfo(rand),
            hideEffectiveness = InfoRules.hideEffectiveness(rand, st.isGhostBattle, own = false), generation = 3, natDex = hnsE)
    val levels = c.input.moveLevels(e.species)
    val learned = LearnedMoves.of(levels, e.level)
    val seenRunWide = marks?.movesSeenFor(e.species) ?: emptyList()
    val actual = InfoRules.canShowMoves(rand)
    val starred = if (actual || EnemyView.fourKnown(e)) emptySet() else MoveStars.of(seenRunWide.map { it.id to it.lastLv }, e.level, levels)
    val (rows, seenCount) = EnemyView.moveRows(e, seenRunWide, c.input.moveRowFor, actual, false)
    val header = "Moves" + (if (!actual && seenCount > 4) "*" else "") + (if (learned.total > 0) " ${learned.learned}/${learned.total}" else "")
    val catchText = st.catchPercent?.takeIf { wild && TrackerOptions.showCatchRate }?.let { "~ $it%  to catch" }
    MovesArea(
        header = header, next = learned.next.takeIf { learned.total > 0 }, nextHot = false, catchText = catchText,
        rows = rows.map { r -> r.toPcMove(ctx).let { if (r.id in starred) it.copy(name = it.name + "*") else it } },
        own = false, allowHidden = TrackerOptions.revealInfoIfRandomized || rand?.moveType != true,
        onMove = { mv -> c.screens.push(PcScreens.Screen.MoveInfo(mv.id, PcScreens.hides(st, mv.id))) },
        onHeader = if (e.isGhost) null else ({ c.screens.push(PcScreens.Screen.MovesLearned(e.species, e.level)) }),
        onCatch = if (catchText != null) ({ c.screens.push(PcScreens.Screen.CatchRates) }) else null,
    )
}

/** BST, or Acc and Eva with their chevrons once either moved in battle (TrackerScreen.lua:1435-1448). */
@Composable
private fun BstRow(inBattle: Boolean, acc: Int, eva: Int, bst: String, broken: Boolean = false) {
    val sy = 67
    if (inBattle && (acc != 6 || eva != 6)) {
        BText("Acc", 101, sy + 1, Pc.Text)
        BText("Eva", 129, sy + 1, Pc.Text)
        Box(Modifier.offset(117.rp, (sy + 2).rp)) { PcStageChevrons(acc - 6) }
        Box(Modifier.offset(124.rp, (sy + 2).rp)) { PcStageChevrons(eva - 6) }
    } else {
        BText("BST", 102, sy, if (broken) Pc.Negative else Pc.Text)
        if (broken) BText("x", 118, sy, Pc.Negative)
        BNumber(bst, 127, sy, 3, Pc.Text)
    }
}

/** TrackerScreen.drawMovesArea: the header on the page at y 81, the box (5, 92, 140, 44), four rows 10 apart from 94. */
@Composable
private fun abilityClickable(line: String): Boolean {
    val name = line.removeSuffix(" /").trim()
    return name.isNotEmpty() && name != "?" && name != PcBesteon.BLANK
}

/** The move or ability description, over the card, until a click anywhere on it. */
@Composable
internal fun InspectCard(title: String, body: String, onClose: () -> Unit) {
    Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPanelInkGround provides Pc.Page) {
            BText(title.take(22), 5, 3, Pc.Header)
            wrapWords(body, 28).take(13).forEachIndexed { i, line -> BText(line, 5, 16 + i * 9, Pc.Text) }
        }
        BImage(PcBesteon.BACK_ARROW, 129, 142, Pc.Text)
        // Nothing else on the card takes a click, so all of it closes the card.
        BHit(0, 0, PcPanel.WIDTH, PcPanel.HEIGHT, onClose)
    }
}

@Composable
private fun MovesArea(
    header: String, next: Int?, nextHot: Boolean, catchText: String?, rows: List<PcMove>, own: Boolean, allowHidden: Boolean,
    onMove: (PcMove) -> Unit, onHeader: (() -> Unit)? = null, onCatch: (() -> Unit)? = null,
) {
    val headerY = 81
    // MovesHistory (6, 81, 75, 10) and CatchRates (82, 81, 50, 10); without the catch line PP, Pow and Acc open the moves too.
    if (onHeader != null) BHit(6, headerY, if (catchText == null) 139 else 75, 10, onHeader)
    if (onCatch != null) BHit(82, headerY, 63, 10, onCatch)
    // PP, Pow and Acc sit on the right of the box. The name takes everything to their left: stopping it at x 74
    // (50 pixels) cut MUDDY WATER and MAGICAL LEAF.
    val ppX = 90
    val powX = 110
    val accX = 128
    // The header line sits between the two boxes, on the page.
    androidx.compose.runtime.CompositionLocalProvider(LocalPanelInkGround provides Pc.Page) {
    Row(Modifier.offset(5.rp, (headerY + PcBesteon.TY).rp)) {
        if (next != null) {
            PixText("$header (", PcRef.FONT, Pc.Header)
            PixText("$next", PcRef.FONT, if (nextHot && own) Pc.Gold else Pc.Header)
            PixText(")", PcRef.FONT, Pc.Header)
        } else PixText(header, PcRef.FONT, Pc.Header)
    }
    if (catchText != null) BText(catchText, 0, headerY, Pc.Header, w = 150 - 5 - 2, align = TextAlign.End)
    else {
        BText("PP", ppX, headerY, Pc.Header)
        BText("Pow", powX, headerY, Pc.Header)
        BText("Acc", accX, headerY, Pc.Header)
    }
    }
    BRect(5, 92, 140, 44, lowerBorder, lowerGround)
    val icons = (Pc.categoryIconsX ?: TrackerOptions.showCategoryIcons)
    // The phone's type symbol (TypeSymbol) sits before the name. The slot is always there so the names line up.
    val nameX = 6 + (if (icons) 8 else 0) + 10
    val nameLimit = ppX - 2
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rpx = com.ironmonone.app.LocalRpx.current
    val shown = rows.take(4) + List((4 - rows.size).coerceAtLeast(0)) { null }
    androidx.compose.runtime.CompositionLocalProvider(LocalPanelInkGround provides lowerGround) {
    shown.forEachIndexed { i, mv ->
        val y = 94 + 10 * i
        if (mv == null || mv.blank) {
            BText(PcBesteon.BLANK, nameX, y, lowerText)
            BText(PcBesteon.BLANK, ppX, y, lowerText)
            BText(PcBesteon.BLANK, powX, y, lowerText)
            BText(PcBesteon.BLANK, accX, y, lowerText)
            return@forEachIndexed
        }
        if (icons && allowHidden) when (mv.category) {
            "PHY" -> BImage(PcBesteon.PHYSICAL, 7, y + 2, lowerText)
            "SPE" -> BImage(PcBesteon.SPECIAL, 7, y + 2, lowerText)
        }
        Box(Modifier.offset((nameX - 10).rp, (y + PcBesteon.TY).rp)) {
            com.ironmonone.app.TypeSymbol(mv.typeName.takeIf { allowHidden })
        }
        val nameColor = if (mv.banned) Pc.Negative else if (allowHidden) mv.color else lowerText
        mv.effect?.let { ef -> Box(Modifier.offset((powX - 7).rp, (y + PcBesteon.TY).rp)) { PcEffectGlyph(ef) } }
        val ban = if (mv.banned) 6 else 0
        val nameRoom = (nameLimit - ban - nameX).coerceAtLeast(20)
        val roomPx = with(density) { (rpx * nameRoom).toPx() }
        var nameSize = PcRef.FONT
        while (nameSize > 6) {
            val drawn = measurer.measure(
                mv.name,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.ironmonone.app.PcFont,
                    fontSize = with(density) { (rpx * nameSize).toSp() },
                    platformStyle = com.ironmonone.app.pcTrackerPlatformStyle(),
                ),
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
            ).size.width
            if (drawn <= roomPx) break
            nameSize--
        }
        BText(mv.name, nameX, y, nameColor, size = nameSize, w = nameRoom)
        if (mv.banned) BText("x", nameLimit - 6, y, Pc.Negative)
        val pp = mv.ppText ?: "${mv.pp}"
        val pow = mv.powerText?.let { if (it == "0") PcBesteon.BLANK else it } ?: mv.power?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK
        val acc = mv.accText?.let { if (it == "0") PcBesteon.BLANK else it }
            ?: mv.acc?.takeIf { it > 0 }?.toString()
            ?: PcBesteon.BLANK
        BNumber(pp, ppX, y, 2, lowerText)
        BNumber(pow, powX, y, 3, if (mv.stab && allowHidden) Pc.Positive else lowerText)
        if (mv.weatherMark != 0 && allowHidden) Box(Modifier.offset((accX - 5).rp, (y + PcBesteon.TY).rp)) { com.ironmonone.app.PcWeatherGlyph(mv.weatherMark, mv.weather) }
        BNumber(acc, accX, y, 3, lowerText)
        if (mv.id > 0) BHit(5, y, 140, 9) { onMove(mv) }
    }
    }
}

/** Heart & Soul's sixteen badges in the game's flag order, with the leader who gives each (HeartGold and SoulSilver's). */
/** Heart & Soul's sixteen badges and their leaders (public for the effects layer's badge celebration). */
val HNS_BADGES = listOf(
    "Zephyr Badge" to "Falkner, Violet City Gym", "Hive Badge" to "Bugsy, Azalea Town Gym", "Plain Badge" to "Whitney, Goldenrod City Gym",
    "Fog Badge" to "Morty, Ecruteak City Gym", "Storm Badge" to "Chuck, Cianwood City Gym", "Mineral Badge" to "Jasmine, Olivine City Gym",
    "Glacier Badge" to "Pryce, Mahogany Town Gym", "Rising Badge" to "Clair, Blackthorn City Gym",
    "Boulder Badge" to "Brock, Pewter City Gym", "Cascade Badge" to "Misty, Cerulean City Gym", "Thunder Badge" to "Lt. Surge, Vermilion City Gym",
    "Rainbow Badge" to "Erika, Celadon City Gym", "Soul Badge" to "Janine, Fuchsia City Gym", "Marsh Badge" to "Sabrina, Saffron City Gym",
    "Volcano Badge" to "Blaine, Seafoam Islands Gym", "Earth Badge" to "Blue, Viridian City Gym",
)

/**
 * TrackerScreen.drawCarouselArea, the box (5, 136, 140, 19), showing the item the session's carousel picked
 * ([PcPanel.Input.strip], PcCarousel): the badges, the opponent's note ("(Leave a note)" by the notepad, or the note
 * on one or two lines), the last attack (the sword red when it would knock your Pokemon out), the route's encounters
 * (the pin), the trainers defeated (the sword in Positive), a battle detail (the sparkles), the pedometer (the clock,
 * Goal and Reset). Each icon at its button's box and the words one pixel after it, as Drawing.drawButton does.
 */
@Composable
private fun Carousel(c: BesteonCtx, st: TrackerState, viewingOwn: Boolean) {
    BRect(5, 136, 140, 19, lowerBorder, lowerGround)
    // The strip's words are on the lower box, not the upper one the rest of the tracker is.
    androidx.compose.runtime.CompositionLocalProvider(LocalPanelInkGround provides lowerGround) { CarouselBody(c, st, viewingOwn) }
}

@Composable
private fun CarouselBody(c: BesteonCtx, st: TrackerState, viewingOwn: Boolean) {
    val note = c.input.note
    val strip = c.input.strip ?: PcStrip(if (viewingOwn) PcStrip.Key.BADGES else PcStrip.Key.NOTES)
    when (strip.key) {
        PcStrip.Key.NOTES -> if (note.isBlank()) {
            BImage(PcBesteon.NOTEPAD, 9, 140, lowerText)
            BText("(Leave a note)", 21, 140, lowerText)
            BHit(6, 140, 138, 12, c.onNote)
        } else {
            val lines = wrapWords(note, 34)
            if (lines.size <= 1) BText(lines.firstOrNull() ?: "", 6, 140, lowerText)
            else { BText(lines[0], 6, 136, lowerText); BText(lines[1], 6, 145, lowerText) }
            BHit(6, 140, 138, 12, c.onNote)
        }
        PcStrip.Key.LAST_ATTACK -> {
            BImage(SWORD, 8, 140, if (strip.lethal) Pc.Negative else lowerText)
            BText(strip.text, 22, 140, lowerText)
            BHit(6, 140, 138, 12) { c.screens.push(PcScreens.Screen.BattleDetails) }
        }
        PcStrip.Key.TRAINERS -> {
            BImage(SWORD, 9, 140, Pc.Positive)
            BText(strip.text, 23, 140, lowerText)
            st.mapId?.let { id -> BHit(6, 140, 138, 12) { c.screens.push(PcScreens.Screen.TrainersOnRoute(id)) } }
        }
        PcStrip.Key.ROUTE -> {
            BImage(PcBesteon.MAP_PINDROP, 9, 140, lowerText)
            BText(strip.text, 18, 140, lowerText, w = 126)
            st.mapId?.let { id -> BHit(6, 140, 138, 12) { c.screens.push(PcScreens.Screen.RouteInfo(id, st.encounterArea)) } }
        }
        PcStrip.Key.BATTLE_DETAILS -> {
            BImage(PcBesteon.SPARKLES, 8, 140, lowerText)
            BText(strip.text, 21, 140, lowerText, w = 123)
            BHit(6, 140, 138, 12) { c.screens.push(PcScreens.Screen.BattleDetails) }
        }
        PcStrip.Key.PEDOMETER -> {
            BImage(PcBesteon.CLOCK, 8, 141, lowerText)
            val reached = strip.goal != 0 && strip.steps >= strip.goal
            BText("Steps: " + "%,d".format(strip.steps), 19, 141, if (reached) Pc.Positive else lowerText)
            BRect(86, 140, 23, 11, lowerBorder, lowerGround)
            BText("Goal", 86, 140, if (strip.goal == 0) lowerText else Pc.Gold, w = 24, align = TextAlign.Center)
            BHit(86, 140, 23, 11, c.onGoal)
            BRect(113, 140, 28, 11, lowerBorder, lowerGround)
            BText(strip.resetLabel, 113, 140, lowerText, w = 29, align = TextAlign.Center)
            BHit(113, 140, 28, 11, c.onPedometerReset)
        }
        PcStrip.Key.NONE -> {}
        PcStrip.Key.BADGES -> {
            // Heart & Soul's sixteen: Johto's eight, Kanto's once Johto's are all won.
            val ctx = LocalPcCtx.current
            // A real run's tracker names Heart & Soul's badges "GSC" (PcPanel.hnsOf): its sixteen are the HNS art.
            val sixteen = PcPanel.hnsOf(st)
            val set = if (sixteen) PcAssets.HNS_BADGES else st.badgeSet
            val kanto = sixteen && (st.badges and 0xFF) == 0xFF
            for (i in 1..8) {
                val idx = if (kanto) i + 8 else i
                val earned = (st.badges shr (idx - 1)) and 1 == 1
                BPicture(PcAssets.badge(ctx, set, idx, earned), 7 + (i - 1) * 17, 138, 16, 16)
                BHit(7 + (i - 1) * 17, 138, 16, 16) {
                    val (badge, leader) = if (sixteen) HNS_BADGES[idx - 1] else "Badge $idx" to null
                    c.screens.push(PcScreens.Screen.Note(badge, listOfNotNull(leader, if (earned) "Earned." else "Not earned yet.").joinToString("\n\n")))
                }
            }
        }
    }
}
