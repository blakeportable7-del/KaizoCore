package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.tracker.TrackedMon

/**
 * The PC IronMON tracker's interface, as shared building blocks.
 *
 * Both the GBA and the DS trackers render through these, so Platinum looks
 * identical to FireRed instead of each console growing its own panel. Colours
 * are the tracker's own defaults, taken from its Theme.lua rather than guessed:
 *
 *   FFFFFF FFFFFF 00FF00 FF0000 FFFF00 FFFFFF AAAAAA 222222 AAAAAA 222222 000000
 *   [text] [l.box text] [positive] [negative] [intermediate] [header]
 *   [u.border] [u.fill] [l.border] [l.fill] [main background]
 */
/**
 * ONE REFERENCE PIXEL, in dp.
 *
 * The PC tracker is not a responsive layout. It draws into a fixed canvas of
 * Constants.SCREEN.RIGHT_GAP x HEIGHT = 150 x 160 pixels beside the 240x160
 * game, with a 5px margin, 9px text and 11px line spacing, and every box at an
 * absolute position inside it. Nothing in it reflows, so nothing in it can
 * wrap.
 *
 * Sizing this panel in dp instead is why it did not match: at a narrow pane a
 * name like "Golisopod-M" broke across three lines and the stat column drifted
 * away from the head block, which cannot happen on a fixed canvas. Everything
 * here is therefore measured in REFERENCE PIXELS and scaled as one unit, so
 * the panel is a scaled photograph of the reference at any pane width.
 *
 * Defaults to 1.dp so anything drawn outside [PcCanvas] keeps its old size.
 */
/**
 * The tracker's typeface.
 *
 * Constants.Font in the reference is "Franklin Gothic Medium" at size 9 - a
 * NARROW PROPORTIONAL sans, not a pixel font. That is not decoration: the
 * whole layout is sized around how much of it fits in a 96-pixel box. Drawn in
 * this app's wide monospace pixel face instead, every field overflowed its box
 * and the panel could not match the reference at any size, because the boxes
 * are right and the glyphs are simply too wide for them.
 *
 * Franklin Gothic is not on Android; sans-serif-condensed (Roboto Condensed)
 * is the same kind of face and the closest thing shipped on every device. The
 * app's own pixel font stays everywhere else - this is the tracker only.
 */
val PcFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Typeface(
        android.graphics.Typeface.create(
            "sans-serif-condensed", android.graphics.Typeface.NORMAL,
        )
    )
)

val LocalRpx = androidx.compose.runtime.compositionLocalOf { 1.dp }

/** [this] reference pixels as a real length. */
val Int.rp: androidx.compose.ui.unit.Dp
    @Composable get() = LocalRpx.current * this

/** [this] reference pixels as a text size. */
val Int.rsp: androidx.compose.ui.unit.TextUnit
    @Composable get() = with(androidx.compose.ui.platform.LocalDensity.current) {
        (LocalRpx.current * this@rsp).toSp()
    }

/** The reference's own canvas metrics, from Constants.SCREEN. */
object PcRef {
    const val WIDTH = 150
    const val HEIGHT = 160
    const val MARGIN = 5
    const val FONT = 9
    const val LINESPACING = 11
    /** Upper info box: gui.drawRectangle(MARGIN, MARGIN, 96, 52). */
    const val INFO_W = 96
    const val INFO_H = 52
    /** Stats box: x = 101, w = RIGHT_GAP - 101 - 5. */
    const val STATS_X = 101
    const val STATS_W = WIDTH - STATS_X - MARGIN   // 44
    const val STATS_H = 75
    /** Sprite icon box, 32x32 at the left edge of the info box. */
    const val ICON = 32
    /** Moves box: y = 92, w = RIGHT_GAP - 2*MARGIN, h = 44. */
    const val MOVES_W = WIDTH - 2 * MARGIN         // 140
}

/**
 * Establishes the reference coordinate system across [content].
 *
 * One reference pixel becomes (pane width / 150), so the panel fills the pane
 * horizontally and every child keeps the reference's proportions exactly.
 */
/** The widest the tracker canvas is allowed to get: the landscape pane. */
private val MAX_CANVAS = 224.dp

@Composable
fun PcCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // Tying a unit to the pane width is right in the landscape split,
        // where the pane IS the tracker's share of the screen. In portrait the
        // pane is the whole 1080px, so a unit became 2.6dp instead of 1.5dp,
        // the card rendered 1234px tall, and it shoved the d-pad about a
        // thousand pixels below the fold. The reference has no portrait layout
        // and no on-screen controls, so there is nothing to copy for that
        // case: cap the unit at the size it has in the split, and the tracker
        // is simply the same tracker with room left for the game and the pad.
        val rpx = minOf(maxWidth, MAX_CANVAS) / PcRef.WIDTH
        androidx.compose.runtime.CompositionLocalProvider(LocalRpx provides rpx) {
            content()
        }
    }
}

object Pc {
    val Page = Color(0xFF000000)
    val Ground = Color(0xFF222222)
    val Border = Color(0xFFAAAAAA)
    val Text = Color(0xFFFFFFFF)
    val Positive = Color(0xFF00FF00)
    val Negative = Color(0xFFFF0000)
    val Gold = Color(0xFFFFFF00)      // "Intermediate text": item + ability
    val Dim = Color(0xFFAAAAAA)
}

/** Constants.MoveTypeColors, verbatim. */
fun pcTypeColor(id: Int): Color = when (id) {
    0 -> Color(0xFFA8A878); 1 -> Color(0xFFC03028); 2 -> Color(0xFFA890F0)
    3 -> Color(0xFFA040A0); 4 -> Color(0xFFE0C068); 5 -> Color(0xFFB8A038)
    6 -> Color(0xFFA8B820); 7 -> Color(0xFF705898); 8 -> Color(0xFFB8B8D0)
    10 -> Color(0xFFF08030); 11 -> Color(0xFF6890F0); 12 -> Color(0xFF78C850)
    13 -> Color(0xFFF8D030); 14 -> Color(0xFFF85888); 15 -> Color(0xFF98D8D8)
    16 -> Color(0xFF7038F8); 17 -> Color(0xFF705848); 23 -> Color(0xFFEE99AC)
    else -> Color(0xFF68A090)
}

/** Same table by name, for the DS side whose sidecar stores type NAMES. */
fun pcTypeColorByName(name: String): Color = when (name.uppercase()) {
    "NORMAL" -> pcTypeColor(0); "FIGHTING" -> pcTypeColor(1); "FLYING" -> pcTypeColor(2)
    "POISON" -> pcTypeColor(3); "GROUND" -> pcTypeColor(4); "ROCK" -> pcTypeColor(5)
    "BUG" -> pcTypeColor(6); "GHOST" -> pcTypeColor(7); "STEEL" -> pcTypeColor(8)
    "FIRE" -> pcTypeColor(10); "WATER" -> pcTypeColor(11); "GRASS" -> pcTypeColor(12)
    "ELECTRIC" -> pcTypeColor(13); "PSYCHIC" -> pcTypeColor(14); "ICE" -> pcTypeColor(15)
    "DRAGON" -> pcTypeColor(16); "DARK" -> pcTypeColor(17); "FAIRY" -> pcTypeColor(23)
    else -> pcTypeColor(-1)
}


/**
 * Images taken from the PC trackers themselves rather than approximated.
 *
 * Type chips are the tracker's own 30x12 icons (Drawing.drawTypeIcon), so they
 * match pixel for pixel instead of being coloured rectangles with text. DS
 * sprites come from the NDS tracker's National-Dex-numbered set, because Gen 4
 * art lives in compressed archives inside the ROM and cannot be decoded live the
 * way GBA sprites are.
 */
object PcAssets {
    private val cache = HashMap<String, ImageBitmap?>()

    private fun load(context: android.content.Context, path: String): ImageBitmap? =
        cache.getOrPut(path) {
            runCatching {
                context.assets.open(path).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }

    /** Type icon by name, e.g. "FIRE". Null when the game has no such type. */
    fun typeIcon(context: android.content.Context, typeName: String): ImageBitmap? {
        if (typeName.isBlank()) return null
        return load(context, "types/${typeName.lowercase()}.png")
    }

    /** Gym badge art. [set] is FRLG, RSE or DPPT; [earned] picks the lit icon. */
    fun badge(context: android.content.Context, set: String, index: Int, earned: Boolean):
        ImageBitmap? = load(
            context,
            "badges/${set}_badge$index${if (earned) "" else "_OFF"}.png",
        )

    /** A full-screen scene from assets/backgrounds. */
    fun background(context: android.content.Context, name: String): ImageBitmap? =
        load(context, "backgrounds/$name.png")

    /**
     * GBA sprite by SPECIES ID, from the bundled pack.
     *
     * Species id, not national dex number: the pack is the one the Nat. Dex
     * expansion ships, indexed the way the ROM numbers species, so Golisopod
     * is 793 and 768 is Ribombee.
     *
     * The reference tracker does NOT decode sprites out of the ROM - it ships
     * PNGs indexed by pokemonID and draws those (Drawing.lua:691), and the Nat.
     * Dex extension overrides the same path with its own pack for the species
     * it adds. Decoding from the ROM instead is why the Nat. Dex builds showed
     * no art at all: no front-pic table address is published for them, so the
     * decoder had nothing to read and every card came up blank.
     *
     * Single 32x32 images, not the Gen 4 animation sheets, so no cell crop.
     */
    fun gbaSprite(context: android.content.Context, species: Int): ImageBitmap? {
        if (species <= 0 || species > 1285) return null
        return load(context, "gbasprites/$species.png")
    }

    /** DS sprite by national dex id; [shiny] picks the alternate palette. */
    /**
     * One Gen 4 sprite.
     *
     * These files are 4x4 ANIMATION SHEETS, not single sprites: sixteen 32x32
     * cells in a 128x128 image. Drawing the file whole put a grid of sixteen
     * tiny Pokemon in the card where one belonged. The reference cycles the
     * frames; a static card wants the idle pose, which is the top-left cell.
     */
    fun dsSprite(context: android.content.Context, species: Int, shiny: Boolean):
        ImageBitmap? {
        if (species <= 0) return null
        // First choice: the sprite decoded from the player's own ROM (RomSprites),
        // a single image, no cell crop. The bundled sheet below is the fallback
        // until every DS kind has been seen decoding on a real dump.
        RomSprites.activeKind?.let { kind ->
            val f = RomSprites.cacheFile(context.filesDir, kind, species, shiny)
            if (f.isFile) runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()?.let { return it }
        }
        val sheet = load(context, "gen4sprites/$species${if (shiny) "s" else ""}.png")
            ?: return null
        val cell = sheet.width / 4
        if (cell <= 0 || sheet.height < cell) return sheet
        return runCatching {
            android.graphics.Bitmap.createBitmap(
                sheet.asAndroidBitmap(), 0, 0, cell, cell,
            ).asImageBitmap()
        }.getOrDefault(sheet)
    }
}

/** One move row, console-neutral. */
data class PcMove(
    val id: Int = 0,
    val name: String,
    val pp: Int,
    val ppMax: Int?,
    val power: Int?,
    val acc: Int?,
    val color: Color,
    val category: String?,   // "PHY" | "SPE" | "STA" | null
    /** For the move popup: the Gen 3 type id (GBA) and its name (both consoles). */
    val type: Int? = null,
    val typeName: String? = null,
    val priority: Int? = null,
    val contact: Boolean? = null,
    /**
     * True only for the padding rows that fill a Pokemon's unused move slots.
     *
     * This used to be inferred from `id == 0`, which was wrong: the DS panel
     * builds its rows from NdsMoveInfo, which carries no move id, so every
     * real Gen 4 move looked like a blank slot and its PP printed "---". An
     * absent id means "not known here", not "no move".
     */
    val blank: Boolean = false,
)

@Composable
fun PixText(
    text: String,
    size: Int = 8,
    color: Color = Pc.Text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    /**
     * The reference never wraps: it draws into fixed boxes and truncates.
     * Wrapping is what turned "Golisopod-M" into three lines, so it is off by
     * default and opted into for prose (descriptions, notes).
     */
    wrap: Boolean = false,
) {
    // `size` is in REFERENCE PIXELS. Outside a PcCanvas one reference pixel is
    // 1.dp, which is what this used to be in sp, so nothing else moves.
    // The reference draws bitmap text exactly `size` pixels tall and steps
    // rows by 10. Compose adds font padding and ~1.2x leading on top of the
    // requested size, so a 9-unit glyph needed ~12 units of box and every stat
    // row was sliced across the middle. Turning both off makes the text
    // occupy what it says it occupies, which is what the pitch assumes.
    Text(text, fontFamily = PcFont, fontSize = size.rsp, color = color,
        lineHeight = size.rsp,
        style = androidx.compose.ui.text.TextStyle(
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                includeFontPadding = false,
            ),
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
            ),
        ),
        textAlign = align, modifier = modifier, fontWeight = FontWeight.Normal,
        maxLines = if (wrap) Int.MAX_VALUE else 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        softWrap = wrap)
}

@Composable
fun PcTypeChip(label: String, color: Color) {
    if (label.isBlank()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(label) { PcAssets.typeIcon(context, label) }
    if (icon != null) {
        // The files are 30x12 and the reference draws them at 30x12, stacked
        // 12 apart (TrackerScreen.lua:1141). Sizing them in dp instead put
        // them at roughly 1.4x on a fractional scale, so two stacked chips
        // did not line up with each other or with the box they sit in.
        androidx.compose.foundation.Image(
            bitmap = icon, contentDescription = label,
            modifier = Modifier.width(30.rp).height(12.rp),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        )
    } else {
        // Only for a type with no shipped icon (a hack adding its own).
        Box(
            Modifier.width(30.rp).height(12.rp).background(color),
            contentAlignment = Alignment.Center,
        ) { PixText(label.uppercase(), PcRef.FONT - 2, Color.White) }
    }
}

@Composable
fun PcSprite(bmp: ImageBitmap?) {
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp, contentDescription = null,
            modifier = Modifier.size(PcRef.ICON.rp),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        )
    } else Spacer(Modifier.size(PcRef.ICON.rp))
}

@Composable
fun PcStatRow(label: String, value: String, stage: Int? = null) {
    // Reference geometry: label at statOffsetX, value drawn at statOffsetX+25,
    // row pitch 10, inside a stats box 44 wide. Same pitch as the enemy's
    // mark rows so the two columns line up with each other.
    Row(
        Modifier.fillMaxWidth().height(10.rp).padding(start = 1.rp, end = 2.rp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixText(label, PcRef.FONT, Pc.Text)
        // Stat-stage chevron, the reference's in-battle marker: green up /
        // red down beside the stat it moved. 6 is neutral and shows nothing.
        if (stage != null && stage != 6) {
            Spacer(Modifier.width(3.dp))
            val delta = stage - 6
            PixText(
                (if (delta > 0) "\u25b2" else "\u25bc") + kotlin.math.abs(delta),
                PcRef.FONT - 2, if (delta > 0) Pc.Positive else Pc.Negative)
        }
        Spacer(Modifier.weight(1f))
        PixText(value, PcRef.FONT, Pc.Text, Modifier.width(19.rp), TextAlign.End)
    }
}

/**
 * The enemy stat column as notes. Tapping cycles blank → + → − → =, the
 * reference tracker's four states, stored per species by the caller.
 */
@Composable
fun PcMarkColumn(marks: IntArray, onCycle: (Int) -> Unit) {
    // The reference's enemy stat column (TrackerScreen.lua:1427, the
    // STAT_STAGE buttons at :588). Copied rather than styled:
    //
    //  - the row pitch is 10, the same as the player's stat rows
    //  - the mark is an 8x8 box whose LEFT EDGE sits at x=129 inside a stats
    //    box spanning 101..145, i.e. 28 in from its left edge
    //  - the box border is "Upper box border" in every state; only the GLYPH
    //    takes a colour. This panel used to tint the whole row green or red,
    //    which the reference never does
    //  - the glyphs are Constants.STAT_STATES: blank, "+", "--", "="
    StatMarks.STAT_NAMES.forEachIndexed { i, label ->
        val state = marks.getOrElse(i) { 0 }
        Row(
            Modifier.fillMaxWidth().height(10.rp).clickable { onCycle(i) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixText(label, PcRef.FONT, Pc.Text, Modifier.width(27.rp))
            Box(
                Modifier.size(8.rp).background(Pc.Ground).border(1.rp, Pc.Border),
                contentAlignment = Alignment.Center,
            ) {
                PixText(
                    StatMarks.symbol(state).trim(), PcRef.FONT - 2,
                    when (state) {
                        1 -> Pc.Positive
                        2 -> Pc.Negative
                        else -> Pc.Text
                    },
                )
            }
        }
    }
}

/**
 * The head block: info left, a vertical rule, then the stat column.
 *
 * [belowHead] is the boxed strip under the head on the LEFT side - where the PC
 * tracker puts the Heals line. It belongs inside this Row rather than after it,
 * because the stat column has to run the full height of head plus strip as one
 * unbroken box. Rendering it as a separate full-width row below instead cuts
 * the stat column short and loses the grid.
 */
@Composable
fun PcHeadBlock(
    name: String,
    level: Int,
    curHp: Int,
    maxHp: Int,
    /**
     * The enemy's second and third lines, which the reference SWAPS relative
     * to your own Pokemon: level first, then "Last seen Lv.N" or
     * "New encounter" (TrackerScreen.lua:1234). It never prints an enemy's
     * current HP - you can see that on the game screen. This panel was showing
     * "118/155" there, which is not on the reference's card at all.
     */
    encounterLine: String? = null,
    typeChips: List<Pair<String, Color>>,
    itemLine: String,
    abilityLine: String,
    onAbilityTap: (() -> Unit)? = null,
    onNameTap: (() -> Unit)? = null,
    sprite: ImageBitmap?,
    belowHead: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    statColumn: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // IntrinsicSize.Min so the rule matches the taller of the two columns.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(2.rp)) {
                PcSprite(sprite)
                Column(Modifier.padding(start = 2.rp)) {
                    PixText(
                        name, PcRef.FONT, Pc.Text,
                        if (onNameTap != null) Modifier.clickable { onNameTap() }
                        else Modifier,
                    )
                    Spacer(Modifier.height(1.rp))
                    if (encounterLine != null) {
                        PixText("Lv.$level", PcRef.FONT, Pc.Text)
                        Spacer(Modifier.height(1.rp))
                        PixText(encounterLine, PcRef.FONT, Pc.Gold)
                    } else {
                    // TrackerScreen.lua:1206 draws the literal label "HP:"
                    // and puts the value 16 units to its right, coloured by
                    // the fraction remaining: Negative at 20% or less,
                    // Intermediate at 50% or less, Default above that. This
                    // panel printed a bare "29/29" in plain white.
                    Row {
                        PixText("HP:", PcRef.FONT, Pc.Text, Modifier.width(16.rp))
                        PixText(
                            "$curHp/$maxHp", PcRef.FONT,
                            when {
                                maxHp <= 0 -> Pc.Text
                                curHp * 5 <= maxHp -> Pc.Negative
                                curHp * 2 <= maxHp -> Pc.Gold
                                else -> Pc.Text
                            },
                        )
                    }
                    Spacer(Modifier.height(1.rp))
                    PixText("Lv.$level", PcRef.FONT, Pc.Text)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column {
                    typeChips.forEach { (label, color) -> PcTypeChip(label, color) }
                }
                Column(Modifier.padding(start = 2.rp, top = 1.rp)) {
                    // DataHelper.lua:217 fills TWO lines here: for your own
                    // Pokemon, held item then ability; for an enemy, its two
                    // POSSIBLE abilities, the first suffixed " /". Joining them
                    // into one "A / B" string is why "Pressure / Inner Focus"
                    // ran off the edge of a 96-unit box.
                    if (itemLine.isNotBlank())
                        PixText(itemLine, PcRef.FONT, Pc.Gold)
                    if (abilityLine.isNotBlank()) {
                        Spacer(Modifier.height(1.rp))
                        PixText(
                            abilityLine, PcRef.FONT, Pc.Gold,
                            if (onAbilityTap != null)
                                Modifier.clickable { onAbilityTap() }
                            else Modifier,
                        )
                    }
                }
            }
            if (belowHead != null) {
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
                belowHead()
            }
        }
        Box(Modifier.width(1.rp).fillMaxHeight().background(Pc.Border))
        // 44 reference pixels: the reference's own stats box width.
        Column(Modifier.width(PcRef.STATS_W.rp).padding(vertical = 1.rp)) {
            statColumn()
        }
    }
}

/** MoveData.BlankMove: an unknown slot, drawn as Constants.BLANKLINE. */
private val BLANK_MOVE = PcMove(
    id = 0, name = "---", pp = 0, ppMax = null, power = null, acc = null,
    color = Pc.Dim, category = null, blank = true,
)

@Composable
fun PcMovesSection(
    rows: List<PcMove>,
    header: String = "Moves",
    onMoveTap: ((PcMove) -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.rp, horizontal = 2.rp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Column widths are the gaps between the reference's own offsets:
        // name at 5, PP at 82, Pow at 102, Acc at 126, box ends at 145.
        PixText(header, PcRef.FONT, Pc.Text, Modifier.weight(1f))
        PixText("PP", PcRef.FONT, Pc.Text, Modifier.width(20.rp), TextAlign.End)
        PixText("Pow", PcRef.FONT, Pc.Text, Modifier.width(24.rp), TextAlign.End)
        PixText("Acc", PcRef.FONT, Pc.Text, Modifier.width(19.rp), TextAlign.End)
    }
    Box(Modifier.fillMaxWidth().height(1.rp).background(Pc.Border))
    Column(Modifier.padding(vertical = 1.rp)) {
        // FOUR rows, always. DataHelper.lua:256 starts from four placeholders
        // and fills what it knows, so the box is the same height whether a
        // Pokemon has one move or four - and an enemy with two moves seen
        // shows two moves and two blanks, not a shorter table.
        val padded = (rows + List(4) { BLANK_MOVE }).take(4)
        padded.forEach { r ->
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        if (onMoveTap != null) Modifier.clickable { onMoveTap(r) }
                        else Modifier
                    )
                    .padding(horizontal = 2.rp, vertical = 1.rp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PcCategoryIcon(r.category)
                PixText(r.name, PcRef.FONT, r.color, Modifier.weight(1f))
                // ONE number, not cur/max. TrackerScreen.lua:1557 draws
                // drawNumber(movePPOffset, y, move.pp, 2) - two digits in a
                // 20-unit column - and DataHelper.lua:324 makes move.pp the
                // CURRENT pp for your own Pokemon. "15/15" was this app's
                // invention and it does not fit: it truncated to "15/1" and
                // took the Pow and Acc columns off the right edge with it.
                PixText(
                    if (r.blank) "---" else "${r.pp}",
                    PcRef.FONT, Pc.Text, Modifier.width(20.rp), TextAlign.End)
                PixText(if (r.power == null || r.power == 0) "---" else "${r.power}",
                    PcRef.FONT, Pc.Text, Modifier.width(24.rp), TextAlign.End)
                PixText(if (r.acc == null || r.acc == 0) "---" else "${r.acc}",
                    PcRef.FONT, Pc.Text, Modifier.width(19.rp), TextAlign.End)
            }
        }
    }
}

/**
 * The Pokemon Stadium announcer lines the PC tracker shows on a lost run,
 * copied from its Languages/English.lua GameOverScreenQuotes rather than
 * written fresh - half the point of the screen is that IronMON players know
 * these lines.
 */
private val GAME_OVER_QUOTES = listOf(
    "What's the matter trainer?",
    "What will the trainer do now?",
    "Oh! Another failure!",
    "Boom!",
    "Devastating!",
    "Gone! It didn't stand a chance!",
    "Can strategy overcome the level disadvantage?",
    "It's in no condition to fight!",
    "This is a battle between obviously mismatched Pokemon.",
    "The Pokemon returns to its Poke Ball.",
    "Down! That didn't take much!",
    "That one hurt!",
    "And there goes the battle!",
    "What a wild turn of events!",
    "Taken down on the word go!",
    "Woah! That was overpowering!",
    "It's finally taken down!",
    "Harsh blow!",
    "That was brutal!",
    "Nailed the weak spot!",
    "Hey! What's it doing? Down it goes!",
)

/**
 * The end-of-run screen: how it ended, which attempt it was, and the team you
 * ended with.
 *
 * A win prints CONGRATULATIONS!!; a loss draws one of the announcer quotes,
 * picked by attempt number so it stays put while you look at it instead of
 * flickering on every tracker poll.
 */
@Composable
fun PcGameOver(won: Boolean, attempt: Int, party: List<TrackedMon>) {
    PcCard {
        Column(Modifier.fillMaxWidth().padding(6.dp)) {
            PixText("G a m e  O v e r", 11, Pc.Gold)
            Spacer(Modifier.height(5.dp))
            Row {
                PixText("Attempt:", 8, Pc.Text, Modifier.width(66.dp))
                PixText("$attempt", 8, Pc.Text)
            }
            Spacer(Modifier.height(5.dp))
            PixText(
                if (won) "CONGRATULATIONS!!"
                else GAME_OVER_QUOTES[
                    ((attempt % GAME_OVER_QUOTES.size) + GAME_OVER_QUOTES.size) %
                        GAME_OVER_QUOTES.size],
                9, if (won) Pc.Positive else Pc.Negative,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        // The rundown: what the team actually was when the run ended.
        Column(Modifier.padding(4.dp)) {
            PixText("Final team", 8, Pc.Text)
            Spacer(Modifier.height(3.dp))
            party.forEach { p ->
                val m = p.mon
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    PixText(
                        p.speciesName, 8,
                        if (m.curHp == 0) Pc.Negative else Pc.Text,
                        Modifier.weight(1f),
                    )
                    PixText("Lv.${m.level}", 7, Pc.Dim, Modifier.width(46.dp))
                    PixText(
                        "${m.maxHp}/${m.atk}/${m.def}/${m.spAtk}/${m.spDef}/${m.spe}",
                        7, Pc.Dim,
                    )
                }
            }
        }
    }
}

/**
 * The Coverage Calculator's result: how much of the dex your moveset can hurt.
 *
 * The bucket that matters is the left one. Everything your lead literally
 * cannot damage is the list that ends runs.
 */
@Composable
fun PcCoverage(buckets: Map<Double, List<Int>>, total: Int) {
    if (total == 0) return
    PcCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
            PixText("Coverage", 9, Pc.Text, Modifier.weight(1f))
            PixText("$total mons", 7, Pc.Dim)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                0.0 to "0x", 0.25 to "1/4", 0.5 to "1/2",
                1.0 to "1x", 2.0 to "2x", 4.0 to "4x",
            ).forEach { (mult, label) ->
                val n = buckets[mult]?.size ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixText(
                        "$n", 9,
                        when {
                            mult == 0.0 && n > 0 -> Pc.Negative
                            mult >= 2.0 -> Pc.Positive
                            else -> Pc.Text
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    PixText(label, 7, Pc.Dim)
                }
            }
        }
    }
}

/**
 * The starter ball position, drawn as three balls rather than written out.
 *
 * The run's seed decides which of the three balls on Oak's table you are
 * allowed to take, and a line of text reading "BALL CALL: MIDDLE" makes you
 * translate a word into a position every single run. The PC tracker shows the
 * table, so this does too: the ball you take is drawn in full, the two you may
 * not are greyed.
 *
 * [pick] is 0 for left, 1 for middle, 2 for right.
 */
@Composable
fun PcBallPicker(pick: Int, onReroll: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixText("Pick this ball:", 8, Pc.Text, Modifier.weight(1f))
            // The reference's dice button: clear the choice and pick again.
            if (onReroll != null) PcSmallButton("REROLL") { onReroll() }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("LEFT", "MIDDLE", "RIGHT").forEachIndexed { i, label ->
                val chosen = i == pick
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(Modifier.size(38.dp)) {
                        val r = size.minDimension / 2f
                        val c = androidx.compose.ui.geometry.Offset(r, r)
                        val red = if (chosen) Color(0xFFE03028) else Color(0xFF4A4A4A)
                        val white = if (chosen) Color(0xFFF8F8F8) else Color(0xFF6E6E6E)
                        val line = if (chosen) Color(0xFF101010) else Color(0xFF2A2A2A)
                        // Bottom half first, then the red cap over it.
                        drawCircle(white, radius = r, center = c)
                        drawArc(
                            red, startAngle = 180f, sweepAngle = 180f, useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = size,
                        )
                        drawRect(
                            line,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, r - r * 0.11f),
                            size = androidx.compose.ui.geometry.Size(size.width, r * 0.22f),
                        )
                        drawCircle(line, radius = r * 0.30f, center = c)
                        drawCircle(white, radius = r * 0.18f, center = c)
                        drawCircle(
                            line, radius = r, center = c,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.12f),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    PixText(label, 7, if (chosen) Pc.Positive else Pc.Dim)
                }
            }
        }
    }
}

/**
 * The physical / special marker beside a move name.
 *
 * These were both drawn as the same circle, which told you a move did damage
 * but not which stat it used - the thing the split actually matters for. They
 * are two different shapes now: physical is solid, special is a ring. Drawn
 * rather than typed, because the pixel font has no glyph for either and was
 * silently falling back to a system face at a different weight.
 *
 * Status moves get an empty slot of the same width so the names stay aligned.
 */
@Composable
fun PcCategoryIcon(category: String?) {
    // Constants.PixelImages.PHYSICAL / SPECIAL, transcribed 1:1 from the
    // reference. These were a filled dot and a ring before, which were simply
    // invented - the reference draws two specific 7x7 pixel glyphs.
    //
    // Two details that are easy to get wrong and are copied, not chosen:
    // the icon is drawn in Theme.COLORS["Lower box text"], i.e. plain WHITE,
    // NOT the move's type colour; and a STATUS move gets no icon at all -
    // TrackerScreen.lua:1521 only ever draws these two.
    val glyph = when (category) {
        "PHY" -> PcCategoryGlyphs.PHYSICAL
        "SPE" -> PcCategoryGlyphs.SPECIAL
        else -> null
    }
    // The slot is held even when empty so the move names stay in one column.
    Box(Modifier.width(9.rp).height(PcRef.FONT.rp)) {
        if (glyph != null) {
            androidx.compose.foundation.Canvas(Modifier.size(7.rp)) {
                val px = size.width / 7f
                for (y in 0 until 7) for (x in 0 until 7) {
                    if (glyph[y][x] == 1) drawRect(
                        color = Pc.Text,
                        topLeft = androidx.compose.ui.geometry.Offset(x * px, y * px),
                        size = androidx.compose.ui.geometry.Size(px, px),
                    )
                }
            }
        }
    }
}

/**
 * The opposing trainer's team, as the reference draws it.
 *
 * Drawing.drawTrainerTeamPokeballs: one 9x9 ball per Pokemon the trainer has,
 * spaced 9 apart, in REVERSE party order to match the in-game team display,
 * and drawn in the fainted palette once its HP hits zero. This is how the PC
 * tracker tells you how many the opponent has left; this app showed nothing.
 */
@Composable
fun PcTrainerTeam(alive: List<Boolean>) {
    if (alive.isEmpty()) return
    Row {
        // TrackerScreen.PokeBalls.ColorList / ColorListFainted. Index 0 is
        // "not drawn", 1 outline, 2 the red half, 3 the white half.
        val live = listOf(Color.Transparent, Color(0xFF000000), Color(0xFFF04037), Color(0xFFFFFFFF))
        val dead = listOf(Color.Transparent, Color(0xFF000000), Color(0x22F04037), Color(0x44FFFFFF))
        alive.asReversed().forEach { standing ->
            val pal = if (standing) live else dead
            androidx.compose.foundation.Canvas(Modifier.size(9.rp).padding(end = 0.rp)) {
                val px = size.width / 9f
                for (y in 0 until 9) for (x in 0 until 9) {
                    val v = POKEBALL_SMALL[y][x]
                    if (v != 0) drawRect(
                        color = pal[v],
                        topLeft = androidx.compose.ui.geometry.Offset(x * px, y * px),
                        size = androidx.compose.ui.geometry.Size(px, px),
                    )
                }
            }
        }
    }
}

/** Constants.PixelImages.POKEBALL_SMALL, 9x9. */
private val POKEBALL_SMALL = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 1, 1, 2, 2, 2, 1, 1, 0),
    intArrayOf(0, 1, 2, 2, 2, 2, 2, 1, 0),
    intArrayOf(1, 2, 2, 2, 1, 2, 2, 2, 1),
    intArrayOf(1, 1, 1, 1, 3, 1, 1, 1, 1),
    intArrayOf(1, 3, 3, 3, 1, 3, 3, 3, 1),
    intArrayOf(0, 1, 3, 3, 3, 3, 3, 1, 0),
    intArrayOf(0, 1, 1, 3, 3, 3, 1, 1, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 0, 0, 0),
)

/** The reference's Constants.PixelImages entries, transcribed 1:1. */
object PcCategoryGlyphs {
    /** Constants.PixelImages.PHYSICAL, 7x7. */
    val PHYSICAL = arrayOf(
        intArrayOf(1, 0, 0, 1, 0, 0, 1),
        intArrayOf(0, 1, 0, 1, 0, 1, 0),
        intArrayOf(0, 0, 1, 1, 1, 0, 0),
        intArrayOf(1, 1, 1, 1, 1, 1, 1),
        intArrayOf(0, 0, 1, 1, 1, 0, 0),
        intArrayOf(0, 1, 0, 1, 0, 1, 0),
        intArrayOf(1, 0, 0, 1, 0, 0, 1),
    )

    /** Constants.PixelImages.SPECIAL, 7x7. */
    val SPECIAL = arrayOf(
        intArrayOf(0, 0, 1, 1, 1, 0, 0),
        intArrayOf(0, 1, 0, 0, 0, 1, 0),
        intArrayOf(1, 0, 0, 1, 0, 0, 1),
        intArrayOf(1, 0, 1, 0, 1, 0, 1),
        intArrayOf(1, 0, 0, 1, 0, 0, 1),
        intArrayOf(0, 1, 0, 0, 0, 1, 0),
        intArrayOf(0, 0, 1, 1, 1, 0, 0),
    )
}

/**
 * The carousel: the PC tracker's fourth area.
 *
 * Its screen is four regions - Pokemon info, stats, moves, and a single strip
 * at the bottom that ROTATES through whichever items make sense right now.
 * Badges outside battle; notes, the last attack and battle details during one.
 * This app had badges and notes as two permanent rows instead, which is both
 * taller than the real thing and never shows the in-battle items at all.
 *
 * Durations are the reference's own framesToShow at 60fps: 210 frames for
 * badges, 180 for the battle items. Items that cannot show are skipped rather
 * than rendered blank, exactly as rotateToNextItem does.
 */
@Composable
fun PcCarousel(
    inBattle: Boolean,
    badges: Int,
    badgeSet: String,
    note: String,
    onEditNote: () -> Unit,
    lastMove: String? = null,
    weather: String? = null,
    encounters: Int = 0,
    routeName: String? = null,
    routeSeen: Int = 0,
    routeTotal: Int = 0,
    routeTrainers: Int = 0,
    routeBosses: Int = 0,
    steps: Int = 0,
    onRouteTap: (() -> Unit)? = null,
) {
    // Build the list of what can show, in the reference's own order.
    data class Item(val ms: Long, val key: String)
    val items = buildList {
        if (!inBattle) {
            add(Item(3500, "badges"))
            // TRAINERS: who is still standing between you and the next town.
            if (routeTrainers > 0) add(Item(3500, "trainers"))
            // PEDOMETER runs longest in the reference: 420 frames, 7 seconds.
            if (steps > 0) add(Item(7000, "pedometer"))
        }
        if (inBattle) {
            add(Item(3000, "notes"))
            // ROUTE_INFO: the reference shows it during a WILD encounter only.
            if (!routeName.isNullOrBlank()) add(Item(3000, "route"))
            if (!lastMove.isNullOrBlank()) add(Item(3000, "lastAttack"))
            if (weather != null || encounters > 1) add(Item(3000, "battleDetails"))
        }
    }
    if (items.isEmpty()) return

    var index by remember(inBattle, items.size) { mutableStateOf(0) }
    val safe = index.coerceIn(0, items.lastIndex)
    LaunchedEffect(inBattle, items.size, safe) {
        kotlinx.coroutines.delay(items[safe].ms)
        index = (safe + 1) % items.size
    }

    when (items[safe].key) {
        "badges" -> PcBadgeRow(badges, badgeSet)
        "pedometer" -> PcCarouselLine("Steps:", "%,d".format(steps), Pc.Text)
        "trainers" -> PcCarouselLine(
            routeName ?: "Here:",
            "$routeTrainers trainers" +
                (if (routeBosses > 0) ", $routeBosses major" else ""),
            if (routeBosses > 0) Pc.Gold else Pc.Text,
        )
        "notes" -> PcNoteRow(note, onEditNote)
        "lastAttack" -> PcCarouselLine("Last attack:", lastMove ?: "", Pc.Text)
        "route" -> PcCarouselLine(
            routeName ?: "",
            if (routeTotal > 0) "seen $routeSeen of $routeTotal here"
            else "$routeSeen seen here",
            Pc.Text,
            onTap = onRouteTap,
        )
        "battleDetails" -> PcCarouselLine(
            "Battle:",
            listOfNotNull(
                weather,
                if (encounters > 1) "seen $encounters times" else null,
            ).joinToString("  ·  "),
            Pc.Dim,
        )
    }
}

/** One carousel row: a label and its value, in the tracker's box. */
@Composable
private fun PcCarouselLine(
    label: String,
    value: String,
    valueColor: Color,
    onTap: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().background(Pc.Ground).border(1.dp, Pc.Border)
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixText(label, 8, Pc.Text)
        Spacer(Modifier.width(6.dp))
        PixText(value, 8, valueColor)
    }
}

/**
 * The info screen: what a move or an ability actually does.
 *
 * The PC tracker's InfoScreen has four modes (Pokemon, move, ability, route);
 * these are the two you reach from the tracker itself, by tapping the thing you
 * want explained. Descriptions are the reference's own text, not paraphrased.
 */
@Composable
fun PcInfoDialog(
    title: String,
    subtitle: String?,
    body: String?,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            // Capped and scrollable: on a landscape phone these dialogs grew
            // past the window and pushed CLOSE off the bottom, leaving no
            // visible way out.
            Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            PixText(title, 11, Pc.Gold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                PixText(subtitle, 8, Pc.Text)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
            Spacer(Modifier.height(8.dp))
            PixText(
                body?.takeIf { it.isNotBlank() } ?: "No description for this one.",
                8, if (body.isNullOrBlank()) Pc.Dim else Pc.Text,
                wrap = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PcSmallButton("CLOSE") { onDismiss() }
            }
        }
    }
}

/**
 * The Pokemon info screen: InfoScreen's POKEMON_INFO mode.
 *
 * Everything the reference puts there - types, BST, weight, how it evolves,
 * what hits it hard, and the levels it learns moves at - plus your note on it.
 * Weaknesses are computed from the live types against the shared chart, which
 * is what PokemonData.getEffectiveness does, so a randomized typing is
 * followed rather than assumed.
 */
@Composable
fun PcPokemonInfo(
    name: String,
    types: List<Pair<String, Color>>,
    bst: String,
    weight: String?,
    evolution: String?,
    effectiveness: Map<Double, List<String>>,
    moveLevels: List<Int>,
    level: Int,
    note: String,
    /**
     * Type coverage for the current moves. The reference keeps this on its own
     * CoverageCalcScreen, NOT on the tracker panel, so it lives behind this
     * screen rather than taking permanent space beside the card.
     */
    coverage: Map<Double, List<Int>> = emptyMap(),
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            // Capped and scrollable: on a landscape phone these dialogs grew
            // past the window and pushed CLOSE off the bottom, leaving no
            // visible way out.
            Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            PixText(name, 11, Pc.Gold)
            Spacer(Modifier.height(6.dp))
            Row { types.forEach { (t, c) -> PcTypeChip(t, c); Spacer(Modifier.width(4.dp)) } }
            Spacer(Modifier.height(8.dp))

            PcInfoRow("BST", bst)
            weight?.let { PcInfoRow("Weight", "$it kg") }
            PcInfoRow(
                "Evolves",
                when {
                    evolution == null -> "does not evolve"
                    evolution.all { it.isDigit() } -> "at level $evolution"
                    else -> evolution.lowercase()
                },
            )

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
            Spacer(Modifier.height(8.dp))

            // Only the halves that matter: what beats it, and what it laughs off.
            effectiveness[4.0]?.let { PcInfoRow("4x from", it.joinToString(", "), Pc.Negative) }
            effectiveness[2.0]?.let { PcInfoRow("2x from", it.joinToString(", "), Pc.Negative) }
            effectiveness[0.5]?.let { PcInfoRow("Resists", it.joinToString(", "), Pc.Positive) }
            effectiveness[0.25]?.let { PcInfoRow("1/4 from", it.joinToString(", "), Pc.Positive) }
            effectiveness[0.0]?.let { PcInfoRow("Immune to", it.joinToString(", "), Pc.Positive) }

            if (coverage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
                Spacer(Modifier.height(8.dp))
                PcCoverage(coverage, coverage.values.sumOf { it.size })
            }

            if (moveLevels.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
                Spacer(Modifier.height(8.dp))
                PixText("Learns at", 8, Pc.Text)
                Spacer(Modifier.height(3.dp))
                // Levels already passed are dimmed; the next one is the one
                // worth knowing about.
                PixText(
                    moveLevels.joinToString(", ") { if (it <= level) "$it" else "[$it]" },
                    8, Pc.Dim, wrap = true,
                )
            }

            if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                PcInfoRow("Note", note, Pc.Gold)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PcSmallButton("CLOSE") { onDismiss() }
            }
        }
    }
}

@Composable
private fun PcInfoRow(label: String, value: String, color: Color = Pc.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PixText(label, 8, Pc.Dim, Modifier.width(74.dp))
        PixText(value, 8, color, Modifier.weight(1f), wrap = true)
    }
}

/**
 * The route info screen: InfoScreen's ROUTE_INFO mode.
 *
 * Everything that can appear here, split by how you meet it - walking,
 * surfing, each rod - with the ones you have actually seen marked. Trainer
 * count comes from the ported route data.
 *
 * The species lists are the VANILLA tables. A randomizer rewrites which
 * Pokemon appear on a route, so this is the shape of the route rather than a
 * promise about this seed, and the header says so rather than letting the list
 * read as fact.
 */
@Composable
fun PcRouteInfo(
    routeName: String,
    trainers: Int,
    bosses: Int,
    areas: Map<String, List<Int>>,
    seen: Set<Int>,
    nameOf: (Int) -> String,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            // Capped and scrollable: on a landscape phone these dialogs grew
            // past the window and pushed CLOSE off the bottom, leaving no
            // visible way out.
            Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            PixText(routeName, 11, Pc.Gold)
            Spacer(Modifier.height(5.dp))
            PixText(
                if (trainers > 0)
                    "$trainers trainers" + (if (bosses > 0) ", $bosses major" else "")
                else "No trainers here",
                8, if (bosses > 0) Pc.Gold else Pc.Dim,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))

            if (areas.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                PixText("Nothing wild appears here.", 8, Pc.Dim)
            } else {
                Column(
                    Modifier.heightIn(max = 300.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    areas.forEach { (area, mons) ->
                        Spacer(Modifier.height(8.dp))
                        val here = mons.count { it in seen }
                        Row(Modifier.fillMaxWidth()) {
                            PixText(area, 8, Pc.Text, Modifier.weight(1f))
                            PixText("$here/${mons.size}", 8, Pc.Dim)
                        }
                        Spacer(Modifier.height(3.dp))
                        mons.forEach { id ->
                            val found = id in seen
                            PixText(
                                (if (found) "+ " else "- ") + nameOf(id),
                                8, if (found) Pc.Positive else Pc.Dim,
                                Modifier.padding(start = 6.dp, top = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PixText("Vanilla encounter table; a seed may differ.", 7, Pc.Dim)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PcSmallButton("CLOSE") { onDismiss() }
            }
        }
    }
}

/** The eight gym badges, lit as they are earned - the PC tracker's badge row. */
@Composable
fun PcBadgeRow(badges: Int, set: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // HGSS has sixteen: Johto in bits 0-7 (set HGSS), Kanto in 8-15 (set
    // HGSS_K, art already bundled). The Kanto row appears once the first
    // Kanto badge is earned, so Johto-only runs keep the one-row shape.
    if (set == "HGSS" && (badges shr 8) != 0) {
        Column {
            PcBadgeRow(badges and 0xFF, "HGSS_J")
            PcBadgeRow(badges shr 8, "HGSS_K")
        }
        return
    }
    val artSet = if (set == "HGSS_J") "HGSS" else set
    Row(
        Modifier.fillMaxWidth().background(Pc.Ground).border(1.dp, Pc.Border)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..8).forEach { i ->
            val earned = (badges shr (i - 1)) and 1 == 1
            val art = remember(artSet, i, earned) { PcAssets.badge(context, artSet, i, earned) }
            if (art != null) {
                androidx.compose.foundation.Image(
                    bitmap = art, contentDescription = "badge $i",
                    modifier = Modifier.size(24.dp),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                )
            } else {
                PixText(if (earned) "$i" else "-", 8,
                    if (earned) Pc.Gold else Pc.Dim, Modifier.size(24.dp),
                    TextAlign.Center)
            }
        }
    }
}

/** The PC tracker's Heals line: carried healing as a share of the lead's HP. */
@Composable
fun PcHealsRow(percent: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No fixed label width: at this pixel size "Heals:" does not fit 50dp
        // and wrapped its own colon onto a third line.
        PixText("Heals:", 8, Pc.Text)
        Spacer(Modifier.width(6.dp))
        PixText("$percent% HP ($count)", 8,
            if (percent >= 100) Pc.Positive else if (percent < 30) Pc.Negative else Pc.Text)
    }
}

/**
 * The Heals line stacked on two rows, which is how it reads in the narrow left
 * column of the head block. Same numbers as [PcHealsRow].
 */
@Composable
fun PcHealsBlock(percent: Int, count: Int) {
    // TrackerScreen.lua:1276 draws both lines in Default text. The colour
    // ramp here was invented, and it painted "0% HP (0)" bright red as though
    // something were wrong rather than simply reporting an empty bag.
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.rp, vertical = 1.rp)) {
        PixText("Heals:", PcRef.FONT, Pc.Text)
        PixText("$percent% HP ($count)", PcRef.FONT, Pc.Text)
    }
}

/** Free-text note for the species on screen; tap to edit. */
@Composable
fun PcNoteRow(note: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onEdit() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixText("NOTE", 7, Pc.Dim, Modifier.width(34.dp))
        PixText(
            note.ifBlank { "tap to add a note" }, 8,
            if (note.isBlank()) Pc.Dim else Pc.Gold, Modifier.weight(1f),
        )
    }
}

@Composable
fun PcCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 2.rp)
            .background(Pc.Ground).border(1.rp, Pc.Border),
    ) { content() }
}

@Composable
fun PcSmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Color(0xFF303030)).clickable { onClick() }
            .padding(horizontal = 3.rp, vertical = 1.rp)
    ) { PixText(label, PcRef.FONT, Pc.Text) }
}

/** Battle banner. RUN only appears in wild battles — trainers never allow it. */
@Composable
fun PcBattleBanner(
    isWild: Boolean,
    onFlee: () -> Unit,
    viewingOwn: Boolean = false,
    onSwapView: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().background(Pc.Ground).border(1.rp, Pc.Border)
            .padding(horizontal = 2.rp, vertical = 1.rp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PixText(
            if (isWild) "WILD BATTLE" else "TRAINER BATTLE", PcRef.FONT,
            if (isWild) Pc.Positive else Pc.Negative,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The reference swaps between your Pokemon and the enemy's with a
            // CONTROLLER BUTTON (Input.lua:178, Battle.togglePokemonViewed),
            // so it needs nothing on screen. There is no controller here, so
            // the same action needs somewhere to be tapped. This is the one
            // control added rather than copied, and it exists only during a
            // battle, which is the only time the reference's hotkey does
            // anything either.
            onSwapView?.let {
                PcSmallButton(if (viewingOwn) "SEE FOE" else "SEE MINE", it)
                Spacer(Modifier.width(2.rp))
            }
            if (isWild) PcSmallButton("RUN", onFlee)
        }
    }
}
