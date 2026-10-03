package com.ironmonone.app

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
// Lazy: created at first draw, so the JVM unit tests can reach this file's plain
// helpers (pcTypeColorByName) without Typeface.create, which they cannot run.
val PcFont by lazy {
    androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Typeface(
            android.graphics.Typeface.create(
                "sans-serif-condensed", android.graphics.Typeface.NORMAL,
            )
        )
    )
}

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
    /** A card's head: the info box and the stats box side by side, 96 + 44. */
    const val HEAD_W = INFO_W + STATS_W            // 140
    /**
     * The moves table's number columns. The reference's are 20, 24 and 19 (PP at 82, Pow at 102, Acc at 126); KaizoCore's
     * look gives them what two and three digits need and hands the 10 units to the type symbol (TypeSymbols), so a
     * move's name keeps the room it had.
     */
    const val PP_W = 16
    const val POW_W = 20
    const val ACC_W = 17
    /**
     * KaizoCore's wide card (2026-10-02): the head, a one-pixel rule and the moves box side by side inside the
     * margins, where the reference stacks the moves under the head.
     */
    const val WIDE_WIDTH = MARGIN + HEAD_W + 1 + MOVES_W + MARGIN   // 291
}

/**
 * Establishes the reference coordinate system across [content].
 *
 * One reference pixel becomes (pane width / 150), so the panel fills the pane
 * horizontally and every child keeps the reference's proportions exactly.
 */
/**
 * The widest the tracker canvas is allowed to get: the landscape pane on the phone. A second
 * display (SecondScreenFrame) provides its own, sized to that display.
 */
val LocalCanvasMax = androidx.compose.runtime.compositionLocalOf { 224.dp }

/**
 * True when [PcCanvas] has room for the wide card (PcMonCard): the moves BESIDE the head, not under it. Portrait on
 * a phone has it; the landscape split and the floating window, at their usual widths, keep the reference's stack.
 */
val LocalTrackerWide = androidx.compose.runtime.compositionLocalOf { false }

/** Inside a wide card's side column: the moves table drops the rule over its header, the column's own edge is it. */
internal val LocalMovesBeside = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The smallest unit the wide card may be drawn at: 9-unit text at 10.8dp. A narrower pane keeps the stacked card,
 * whose unit is larger.
 */
internal val WIDE_MIN_RPX = 1.2.dp

/** The unit the canvas draws at, and whether it lays its cards out wide, for a pane [width] wide under [cap]. */
internal fun canvasUnit(width: androidx.compose.ui.unit.Dp, cap: androidx.compose.ui.unit.Dp): Pair<androidx.compose.ui.unit.Dp, Boolean> {
    val wide = minOf(width / PcRef.WIDE_WIDTH, cap / PcRef.WIDTH)
    return if (wide >= WIDE_MIN_RPX) wide to true else minOf(width, cap) / PcRef.WIDTH to false
}

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
        // case: cap the unit at the size it has in the split.
        //
        // Capped, a portrait card was the split's card stretched across the
        // phone: a strip of blank in the middle of every card, and the moves
        // scrolled out of the little room the game and the pad leave (Blake,
        // 2026-10-02: "Lots of wasted space in our current tracker"). Where the
        // pane holds a head and a moves box side by side at a readable size,
        // the card is drawn that way instead (PcMonCard).
        val (rpx, wide) = canvasUnit(maxWidth, LocalCanvasMax.current)
        androidx.compose.runtime.CompositionLocalProvider(LocalRpx provides rpx, LocalTrackerWide provides wide) {
            content()
        }
    }
}

/**
 * The tracker's palette, the DS tracker's default colour scheme
 * (MiscConstants.colorScheme) key for key. Mutable so the Edit Color Theme
 * screen (ColorSchemeScreen.lua) can change it live; ThemeStore holds the
 * saved theme.
 */
object Pc {
    var Page by mutableStateOf(Color(0xFF000000))        // Main background color
    var Ground by mutableStateOf(Color(0xFF222222))      // Top box background color
    var Border by mutableStateOf(Color(0xFFAAAAAA))      // Top box border color
    var Text by mutableStateOf(Color(0xFFFFFFFF))        // Top box text color
    var Positive by mutableStateOf(Color(0xFF00FF00))    // Positive text color
    var Negative by mutableStateOf(Color(0xFFFF0000))    // Negative text color
    var Gold by mutableStateOf(Color(0xFFFFFF00))        // Intermediate text color: item + ability
    var Dim by mutableStateOf(Color(0xFFAAAAAA))         // Bottom box text color, used for the quiet lines

    // A Gen 3 theme has three more colours than this palette: the lower box's own
    // text, border and background, and the header text (Theme.lua's "Lower box
    // text/border/background", "Header text"). Auto Pokemon Themes sets them; null
    // keeps today's one-box look, so nothing changes unless a theme asks for it.
    var HeaderX by mutableStateOf<Color?>(null)
    var HeaderGroundX by mutableStateOf<Color?>(null)
    var LowerTextX by mutableStateOf<Color?>(null)
    var LowerBorderX by mutableStateOf<Color?>(null)
    var LowerGroundX by mutableStateOf<Color?>(null)
    val Header: Color get() = HeaderX ?: Text
    val LowerText: Color get() = LowerTextX ?: Text
    val LowerBorder: Color get() = LowerBorderX ?: Border
    /**
     * Theme flag 1 at "0" (Theme.MOVE_TYPES_ENABLED off): move names in the lower
     * box text colour with a small bar in the move's type colour
     * (TrackerScreen.lua:1530), instead of names in the type colour.
     * Kept so a theme's code reads and saves whole; since 2026-10-02 the move rows
     * colour names by type whatever it says (TrackerLook.moveName).
     */
    var moveTypeBar by mutableStateOf(false)
    /**
     * The DS tracker's "Alternate positive/negative text color" (ThemeFactory.lua:190-210):
     * a DS theme whose two text colours are white and black gives the lower box its own
     * pair for STAB power and the effectiveness marks. Null uses Positive and Negative,
     * as DrawingUtils.convertColorKeyToColor falls back (DrawingUtils.lua:336-341).
     */
    var AltPositiveX by mutableStateOf<Color?>(null)
    var AltNegativeX by mutableStateOf<Color?>(null)
    val AltPositive: Color get() = AltPositiveX ?: Positive
    val AltNegative: Color get() = AltNegativeX ?: Negative
    /** A DS auto theme turns the physical and special icons on (formatPokemonTheme); null leaves them to the option. */
    var categoryIconsX by mutableStateOf<Boolean?>(null)
}

/**
 * Constants.MoveTypeColors (Constants.lua:79-99), verbatim, by Gen 3 type id. Fairy is 18, as the Nat. Dex expansion
 * numbers it (NatDexExtension.lua:16914, TypeIndexMap[0x12] = FAIRY; Gen3Types). It sat at 23, which nothing produces, so
 * every Fairy move and type chip on a Nat. Dex game took the unknown type's grey-green (rc32 audit P2 #44).
 */
fun pcTypeColor(id: Int): Color = when (id) {
    0 -> Color(0xFFA8A878); 1 -> Color(0xFFC03028); 2 -> Color(0xFFA890F0)
    3 -> Color(0xFFA040A0); 4 -> Color(0xFFE0C068); 5 -> Color(0xFFB8A038)
    6 -> Color(0xFFA8B820); 7 -> Color(0xFF705898); 8 -> Color(0xFFB8B8D0)
    10 -> Color(0xFFF08030); 11 -> Color(0xFF6890F0); 12 -> Color(0xFF78C850)
    13 -> Color(0xFFF8D030); 14 -> Color(0xFFF85888); 15 -> Color(0xFF98D8D8)
    16 -> Color(0xFF7038F8); 17 -> Color(0xFF705848); 18 -> Color(0xFFEE99AC)
    else -> Color(0xFF68A090)
}

/** Same table by name, for the DS side whose sidecar stores type NAMES. */
fun pcTypeColorByName(name: String): Color = when (name.uppercase()) {
    "NORMAL" -> pcTypeColor(0); "FIGHTING" -> pcTypeColor(1); "FLYING" -> pcTypeColor(2)
    "POISON" -> pcTypeColor(3); "GROUND" -> pcTypeColor(4); "ROCK" -> pcTypeColor(5)
    "BUG" -> pcTypeColor(6); "GHOST" -> pcTypeColor(7); "STEEL" -> pcTypeColor(8)
    "FIRE" -> pcTypeColor(10); "WATER" -> pcTypeColor(11); "GRASS" -> pcTypeColor(12)
    "ELECTRIC" -> pcTypeColor(13); "PSYCHIC" -> pcTypeColor(14); "ICE" -> pcTypeColor(15)
    "DRAGON" -> pcTypeColor(16); "DARK" -> pcTypeColor(17); "FAIRY" -> pcTypeColor(18)
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

    /**
     * Held while it is read or filled: the stream server's threads draw favorites through here too
     * (StreamFavoritePictures), beside the screens on the main thread.
     */
    private fun load(context: android.content.Context, path: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(path) {
            runCatching {
                context.assets.open(path).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    /** Type icon by name, e.g. "FIRE". Null when the game has no such type. */
    fun typeIcon(context: android.content.Context, typeName: String): ImageBitmap? {
        if (typeName.isBlank()) return null
        return load(context, "types/${typeName.lowercase()}.png")
    }

    /** Gym badge art. [set] is the tracker's badge set (FRLG, RSE, DPPT, RBY, GSC_K ...); [earned] picks the lit icon. */
    fun badge(context: android.content.Context, set: String, index: Int, earned: Boolean):
        ImageBitmap? = load(context, badgePath(set, index, earned))

    /**
     * The asset a badge is drawn from. Red, Blue and Yellow use FireRed's
     * Kanto art, as the Gen 1 reference does for all three (GameSettings.lua
     * setGameInfo: BADGE_PREFIX = "FRLG"); with no art of their own they drew
     * as numbers here. GSC_J is the Johto half of Crystal's two rows.
     */
    fun badgePath(set: String, index: Int, earned: Boolean): String {
        val art = when (set) { "RBY" -> "FRLG"; "GSC_J" -> "GSC"; else -> set }
        return "badges/${art}_badge$index${if (earned) "" else "_OFF"}.png"
    }

    /**
     * Status condition art, the reference's images/status (BRN, FNT, FRZ, PAR,
     * PSN, SLP), drawn 16x8 over the Pokemon icon (Drawing.drawStatusIcon).
     * Bad poison is PSN, as the reference's StatusCodeMap has it.
     */
    fun status(context: android.content.Context, code: String): ImageBitmap? =
        code.takeIf { it.isNotBlank() }?.let { load(context, "status/${if (it == "TOX") "PSN" else it}.png") }

    /** A tracker icon from assets/icons (the repel items). */
    fun icon(context: android.content.Context, name: String): ImageBitmap? = load(context, "icons/$name.png")

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

    /**
     * The same, for the game's own numbering: on MaxDex 1.0 ([nameSet] "maxdex", GbaTracker.nameSet) ids 412 to 1280
     * draw MaxDex's own icons (assets/gbasprites-maxdex, tools/trainer-data/convert_maxdex.py). Its ids are Nat. Dex
     * 1.2.1's to 1235 and part ways after, where the Nat. Dex pack drew another Pokemon. Everything else is [gbaSprite].
     */
    fun gbaSprite(context: android.content.Context, species: Int, nameSet: String?): ImageBitmap? =
        if (nameSet == "maxdex" && species in 412..1280) load(context, "gbasprites-maxdex/$species.png") else gbaSprite(context, species)

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
    /** The power column when the PC tracker's rules replace the ROM number (MoveRules); "0" draws a dash. */
    val powerText: String? = null,
    /** "?" when "Reveal info if randomized" hides a randomized PP. */
    val ppText: String? = null,
    val accText: String? = null,
    /** Same-type attack bonus in battle: the power draws green (TrackerScreen.lua:1536). */
    val stab: Boolean = false,
    /** Effectiveness against the target when not neutral; null draws nothing. */
    val effect: Double? = null,
    /** Your own Hidden Power on the DS card: the "<" and ">" that change its type (MainScreen's hiddenPowerArrowsFrame). */
    val hiddenPowerArrows: Boolean = false,
    /** This run's rules ban the move in the battle you are in (MoveRule): the name in red and the X after it. */
    val banned: Boolean = false,
    /** Why and when the run bans it, for the move's card; null when it does not. */
    val banLine: String? = null,
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
    /** KaizoCore's look (TrackerLook): names and buttons a weight heavier. */
    weight: FontWeight = FontWeight.Normal,
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
        textAlign = align, modifier = modifier, fontWeight = weight,
        maxLines = if (wrap) Int.MAX_VALUE else 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        softWrap = wrap)
}

/** The size a dialog label is drawn at, in sp: what was asked for, and never under [PcMin.LABEL_SP]. */
internal fun dialogSp(size: Int): Int = size.coerceAtLeast(PcMin.LABEL_SP)

/**
 * A label for a tracker dialog (Tracker Setup, Rules): the tracker's face and palette, in sp so it follows the
 * phone's font size, never under [PcMin.LABEL_SP], and always wrapping, so a big font grows the row instead of
 * clipping the words (2026-09-30, UX audit P0-15). [PixText] stays for the tracker panel itself, where the
 * reference's fixed pixel grid is the point; in a dialog it drew 6 to 8dp text that ignored the font setting.
 */
@Composable
fun DialogText(
    text: String,
    size: Int = PcMin.LABEL_SP,
    color: Color = Pc.Text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    /** A section or dialog head: a screen reader can jump to it. */
    heading: Boolean = false,
    /** A mark that is not colour: the chosen tab is underlined. */
    underline: Boolean = false,
) {
    val sp = dialogSp(size)
    Text(
        text, fontFamily = PcFont, fontSize = sp.sp, lineHeight = (sp + 5).sp, color = color,
        textAlign = align, fontWeight = FontWeight.Normal,
        textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        modifier = if (heading) modifier.semantics { heading() } else modifier,
    )
}

@Composable
fun PcTypeChip(label: String, color: Color) {
    if (label.isBlank()) return
    // KaizoCore's look (2026-10-02): a pill in the type's colour, in the 30 by 12 the reference's badge took, stacked
    // 12 apart (TrackerScreen.lua:1141), with a pixel of air between two.
    Box(Modifier.width(30.rp).height(12.rp).semantics { contentDescription = label }, contentAlignment = Alignment.TopStart) {
        TrackerTypePill(label, color)
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

/**
 * A picture the GBA tracker read out of the game for one Pokemon (GbaTracker.picture: shiny, Unown's letter, Deoxys's
 * form), ready for [PcSprite]; null for none, and the card draws the species' own picture as before.
 */
@Composable
fun romPicture(p: com.ironmonone.tracker.Gen3Pictures.Picture?): ImageBitmap? = remember(p) {
    p?.let { android.graphics.Bitmap.createBitmap(it.argb, it.width, it.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap() }
}


@Composable
fun PcStatRow(
    label: String, value: String, stage: Int? = null, nature: Int? = null,
    /** Drawing.drawNumber: off, the reference starts the number at its column. */
    rightJustify: Boolean = true,
    /** "Color stat numbers by nature": the number takes the label's colour. */
    colorNumber: Boolean = false,
    /** A colour for the number alone: the opponent's revealed base stats are Intermediate text. */
    valueColor: Color? = null,
    /** Past the run's BST line (BstRule): an X after the label and the number in red. */
    broken: Boolean = false,
    /** While [broken]: the rule, and for your own Pokemon "it evolved into this" (BstRuleSheet). */
    onBrokenTap: (() -> Unit)? = null,
) {
    // Reference geometry: label at statOffsetX, value drawn at statOffsetX+25,
    // row pitch 10, inside a stats box 44 wide. Same pitch as the enemy's
    // mark rows so the two columns line up with each other.
    Row(
        Modifier.fillMaxWidth().height(10.rp)
            .then(if (broken && onBrokenTap != null) Modifier.clickable(role = Role.Button) { onBrokenTap() } else Modifier)
            .padding(start = 1.rp, end = 2.rp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mark = natureMark(nature, label)
        val labelColor = when (mark) { '+' -> Pc.Positive; '-' -> Pc.Negative; else -> Pc.Text }
        PixText(label, PcRef.FONT, labelColor)
        if (mark != null) PixText(mark.toString(), PcRef.FONT - 3, labelColor, Modifier.padding(start = 1.dp))
        if (broken) BstCross()
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
        PixText(value, PcRef.FONT, if (broken) Pc.Negative else valueColor ?: if (colorNumber) labelColor else Pc.Text, Modifier.width(19.rp),
            if (rightJustify) TextAlign.End else TextAlign.Start)
    }
}

/**
 * The X after BST (Blake, 2026-10-02: "Or BSTX red color base stat total number"): drawn rather than typed, so it is
 * the same cross in every font, and beside the label so the number stays in its column.
 */
@Composable
private fun BstCross() = RuleCross("Over this run's BST limit")

/** The rules' X, after BST and after a move this run bans (MoveRule), with what a screen reader says for it. */
@Composable
internal fun RuleCross(description: String) {
    val color = Pc.Negative
    androidx.compose.foundation.Canvas(
        Modifier.padding(start = 2.rp).size(5.rp)
            .semantics { contentDescription = description },
    ) {
        val w = size.minDimension * 0.26f
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = w)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = w)
    }
}

/**
 * The enemy stat column as notes. Tapping cycles blank → + → − → =, the
 * reference tracker's four states, stored per species by the caller.
 */
@Composable
fun PcMarkColumn(marks: IntArray, onCycle: (Int) -> Unit, singleSpecial: Boolean = false) {
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
        // Gen 1 has one Special: the SPD row does not exist there (the Gen 1 reference lists hp/atk/def/spa/spe).
        if (singleSpecial && label == "SPD") return@forEachIndexed
        val state = marks.getOrElse(i) { 0 }
        Row(
            Modifier.fillMaxWidth().height(10.rp).clickable { onCycle(i) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixText(label, PcRef.FONT, Pc.Text, Modifier.width(27.rp))
            // KaizoCore's look: a rounded chip on the faint raised surface, washed in the mark's colour once marked,
            // where the reference draws an 8x8 square in the border colour. The glyph keeps its colour.
            val markColor = when (state) {
                1 -> Pc.Positive
                2 -> Pc.Negative
                else -> Pc.Text
            }
            val chip = androidx.compose.foundation.shape.RoundedCornerShape(2.rp)
            Box(
                Modifier.size(9.rp).clip(chip)
                    .background(if (state == 0) TrackerLook.inset else markColor.copy(alpha = 0.22f), chip)
                    .border(1.rp, if (state == 0) TrackerLook.outline else markColor.copy(alpha = 0.7f), chip),
                contentAlignment = Alignment.Center,
            ) {
                PixText(StatMarks.symbol(state).trim(), PcRef.FONT - 2, markColor, weight = FontWeight.Medium)
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
    /** The upper of the two lines: the held item on your own card, the first ability on the enemy's. */
    onItemTap: (() -> Unit)? = null,
    onNameTap: (() -> Unit)? = null,
    /** TrackerScreen.lua:76: tapping the type icons opens TypeDefensesScreen for this Pokemon. */
    onTypesTap: (() -> Unit)? = null,
    sprite: ImageBitmap?,
    /** BRN, FNT, FRZ, PAR, PSN or SLP, drawn as the reference's status image over the icon; empty for none. */
    status: String = "",
    /** The species id the Walking Pals icon is found by, numbered as [iconDex] says; 0 draws the still sprite. */
    iconSpecies: Int = 0,
    /** How [iconSpecies] is numbered: WalkingPals.trackerDex for the GBA and Game Boy panel, national on the DS panel. */
    iconDex: WalkingPals.Dex = WalkingPals.Dex.GEN3,
    /** How the game draws it past its species: shiny, and the form (Unown's letter, the game's Deoxys), by PalForms. */
    iconLook: WalkingPals.Look = WalkingPals.Look(),
    /** The evolution in brackets after the level, "Lv.5 (30)" (EvoText). */
    evo: com.ironmonone.tracker.EvoText.Label? = null,
    /** "Lv." on the GBA trackers; the DS tracker writes "Lv. " (MainScreen.setUpEvo). */
    levelPrefix: String = "Lv.",
    /** "Display gender": Gender3.MALE or FEMALE; null draws nothing. */
    gender: Int? = null,
    /** "Show experience points bar": your Pokemon's progress through its level, 0 to 1. */
    expFraction: Float? = null,
    /** In place of "cur/max" when the HP must not show ("Hide stats until summary shown"). */
    hpText: String? = null,
    /** False drops the HP row and the level moves up: the DS tracker hides an opponent's (MainScreen.lua:872). */
    showHp: Boolean = true,
    /**
     * The DS tracker's "Experience bar": while the level is held (its hover,
     * MainScreen.onPokemonLevelHover) the level text gives way to the bar
     * (setUpEvo blanks it, setUpEXPBar draws it). The fraction; null for none.
     */
    holdExpFraction: Double? = null,
    belowHead: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    statColumn: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // IntrinsicSize.Min so the rule matches the taller of the two columns.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(2.rp)) {
                // TrackerScreen.Buttons.PokemonIcon: the icon is the tap target, as well as the name.
                Box((if (onNameTap != null) Modifier.clickable { onNameTap() } else Modifier)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.rp)).background(TrackerLook.inset)) {
                    // Either set: Gen 1-3 by Gen 3's ids, Gen 4-9 and a Nat. Dex build's forms by national number (WalkingPals),
                    // drawn as the game draws it: a shiny as its shiny, Unown as its letter (iconLook).
                    val iconCtx = androidx.compose.ui.platform.LocalContext.current
                    val pal = WalkingPals.ready(iconCtx).let { ix -> remember(ix, iconSpecies, iconDex, iconLook) { if (iconSpecies > 0) ix?.find(iconSpecies, iconDex, iconLook) else null } }
                    val animated = pal != null && TrackerOptions.animatedSprites &&
                        WalkingPalsIcon(pal, status, 1.rp, PcRef.ICON.rp)
                    if (!animated) PcSprite(sprite)
                    // A name too wide for the symbol beside it: TrackerScreen.lua
                    // draws it over the icon instead, at the card's x + 23, y + 20.
                    if (gender != null && !genderFitsAfter(name)) {
                        PcGenderSymbol(gender, Modifier.padding(start = (21 + if (gender == com.ironmonone.tracker.Gender3.FEMALE) 3 else 0).rp, top = 18.rp))
                    }
                    // TrackerScreen.lua, STATUS ICON: 16x8 at the card's x + 30 - 16 + 1, y + 1,
                    // over the icon's top right (the icon sits 2 in from the card here). KaizoCore's look draws it as
                    // a pill in the status's colour (TrackerStatusPill) where the reference has its pixel image.
                    if (status.isNotEmpty()) TrackerStatusPill(status, Modifier.padding(start = 13.rp, top = 1.rp))
                }
                Column(Modifier.padding(start = 2.rp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixText(
                            name, PcRef.FONT, Pc.Text,
                            if (onNameTap != null) Modifier.clickable { onNameTap() }
                            else Modifier,
                            weight = FontWeight.Medium,
                        )
                        if (gender != null && genderFitsAfter(name)) {
                            Spacer(Modifier.width(4.rp))
                            PcGenderSymbol(gender)
                        }
                    }
                    Spacer(Modifier.height(1.rp))
                    if (encounterLine != null) {
                        PcLevelLine(level, evo, levelPrefix)
                        Spacer(Modifier.height(1.rp))
                        PixText(encounterLine, PcRef.FONT, Pc.Gold)
                    } else {
                    // TrackerScreen.lua:1206 draws the literal label "HP:"
                    // and puts the value 16 units to its right, coloured by
                    // the fraction remaining: Negative at 20% or less,
                    // Intermediate at 50% or less, Default above that. This
                    // panel printed a bare "29/29" in plain white.
                    if (showHp) {
                        Row {
                            PixText("HP:", PcRef.FONT, Pc.Text, Modifier.width(16.rp))
                            PixText(
                                hpText ?: "$curHp/$maxHp", PcRef.FONT,
                                when {
                                    maxHp <= 0 -> Pc.Text
                                    curHp * 5 <= maxHp -> Pc.Negative
                                    curHp * 2 <= maxHp -> Pc.Gold
                                    else -> Pc.Text
                                },
                            )
                        }
                        // KaizoCore's look: the same fraction as a bar under it.
                        if (hpText == null && maxHp > 0) { Spacer(Modifier.height(1.rp)); TrackerHpBar(curHp, maxHp) }
                        Spacer(Modifier.height(1.rp))
                    }
                    if (holdExpFraction != null) {
                        var held by remember { mutableStateOf(false) }
                        Box(
                            Modifier.height(PcRef.FONT.rp).pointerInput(Unit) {
                                detectTapGestures(onPress = { held = true; tryAwaitRelease(); held = false })
                            },
                            contentAlignment = Alignment.CenterStart,
                        ) { if (held) PcDsExpBar(holdExpFraction) else PcLevelLine(level, evo, levelPrefix) }
                    } else PcLevelLine(level, evo, levelPrefix)
                    // TrackerScreen.lua:1242, 60 by 3, just under the level.
                    expFraction?.let { Spacer(Modifier.height(2.rp)); PcExpBar(it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(if (onTypesTap != null) Modifier.clickable { onTypesTap() } else Modifier) {
                    typeChips.forEach { (label, color) -> PcTypeChip(label, color) }
                }
                Column(Modifier.padding(start = 2.rp, top = 1.rp)) {
                    // DataHelper.lua:217 fills TWO lines here: for your own
                    // Pokemon, held item then ability; for an enemy, its two
                    // POSSIBLE abilities, the first suffixed " /". Joining them
                    // into one "A / B" string is why "Pressure / Inner Focus"
                    // ran off the edge of a 96-unit box.
                    if (itemLine.isNotBlank())
                        PixText(itemLine, PcRef.FONT, Pc.Gold,
                            if (onItemTap != null) Modifier.clickable { onItemTap() } else Modifier)
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
                Box(Modifier.fillMaxWidth().height(1.dp).background(TrackerLook.divider))
                belowHead()
            }
        }
        Box(Modifier.width(1.rp).fillMaxHeight().background(TrackerLook.divider))
        // 44 reference pixels: the reference's own stats box width.
        Column(Modifier.width(PcRef.STATS_W.rp).padding(vertical = 1.rp)) {
            statColumn()
        }
    }
}

/**
 * DrawingUtils.drawExperienceBar (DrawingUtils.lua:522-536), 62 by 4: a 59 by 3
 * outline in top box border with a cap each end, and two rows filled from the
 * left, Positive text below and that colour darkened (calcShadowColor, x0.88)
 * above, floor(57 x fraction) wide (NdsExperience.barWidth).
 */
@Composable
internal fun PcDsExpBar(fraction: Double, modifier: Modifier = Modifier) {
    val w = com.ironmonone.tracker.nds.NdsExperience.barWidth(fraction)
    val fill = Pc.Positive
    val dark = Color(fill.red * 0.88f, fill.green * 0.88f, fill.blue * 0.88f, fill.alpha)
    androidx.compose.foundation.Canvas(modifier.width(62.rp).height(4.rp)) {
        val u = size.width / 62f
        fun px(x: Int, y: Int, wide: Int, tall: Int, c: Color) = drawRect(c,
            androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Size(wide * u, tall * u))
        px(1, 0, 60, 1, Pc.Border); px(1, 3, 60, 1, Pc.Border)       // the outline, (x + 1, y, 59, 3)
        px(1, 0, 1, 4, Pc.Border); px(60, 0, 1, 4, Pc.Border)
        px(0, 1, 1, 2, Pc.Border); px(61, 1, 1, 2, Pc.Border)       // the caps
        if (w > 0) { px(2, 2, w + 1, 1, fill); px(2, 1, w + 1, 1, dark) }
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
    /** TrackerScreen.lua:352: the Moves header opens MoveHistoryScreen for the viewed Pokemon. */
    onHeaderTap: (() -> Unit)? = null,
    /** The next move's level, drawn after the header in brackets. */
    nextLevel: Int? = null,
    /** TrackerScreen.lua:1492: your Pokemon one level from it, so the number takes the highlight colour. */
    nextHot: Boolean = false,
    /** A wild battle's "~ 42%  to catch", in place of the PP, Pow and Acc labels (TrackerScreen.lua:1474). */
    catchText: String? = null,
    onCatchTap: (() -> Unit)? = null,
    /**
     * The reference's own columns (TrackerScreen.lua drawMovesArea): headers
     * at PP 82, Pow 102, Acc 126, left-aligned; the effectiveness mark at 97,
     * between the PP number and the power. The DS panel keeps its layout.
     */
    referenceColumns: Boolean = false,
    /** "Right justified numbers", for [referenceColumns]. */
    rightJustify: Boolean = true,
    /** The DS main screen's Hidden Power arrows on a row that carries them: true is ">" (onChangeHiddenPower "forward"). */
    onHiddenPower: ((Boolean) -> Unit)? = null,
) {
    val numAlign = if (!referenceColumns || rightJustify) TextAlign.End else TextAlign.Start
    val headAlign = if (referenceColumns) TextAlign.Start else TextAlign.End
    // Beside the head (PcMonCard, wide) the card's edge and the rule between the columns frame it already.
    if (!LocalMovesBeside.current) Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.LowerBorder.copy(alpha = 0.28f)))
    Row(
        Modifier.fillMaxWidth()
            .then(Pc.HeaderGroundX?.let { Modifier.background(TrackerBackground.boxFill(it)) } ?: Modifier.background(TrackerLook.inset))
            .padding(vertical = 1.rp, horizontal = 2.rp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Column widths are the gaps between the reference's own offsets:
        // name at 5, PP at 82, Pow at 102, Acc at 126, box ends at 145.
        Row(if (onHeaderTap != null) Modifier.weight(1f).clickable { onHeaderTap() } else Modifier.weight(1f)) {
            PixText(header, PcRef.FONT, Pc.Header)
            if (nextLevel != null) {
                PixText(" (", PcRef.FONT, Pc.Header)
                PixText("$nextLevel", PcRef.FONT, if (nextHot) Pc.Gold else Pc.Header)
                PixText(")", PcRef.FONT, Pc.Header)
            }
        }
        if (catchText != null) {
            PixText(catchText, PcRef.FONT, Pc.Header, if (onCatchTap != null) Modifier.clickable { onCatchTap() } else Modifier)
        } else {
            PixText("PP", PcRef.FONT, Pc.Header, Modifier.width(PcRef.PP_W.rp), headAlign)
            PixText("Pow", PcRef.FONT, Pc.Header, Modifier.width(PcRef.POW_W.rp), headAlign)
            PixText("Acc", PcRef.FONT, Pc.Header, Modifier.width(PcRef.ACC_W.rp), headAlign)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.rp).background(Pc.LowerBorder.copy(alpha = 0.28f)))
    Column(
        (Pc.LowerGroundX?.let { Modifier.fillMaxWidth().background(TrackerBackground.boxFill(it)) } ?: Modifier)
            .padding(vertical = 1.rp)
    ) {
        // FOUR rows, always. DataHelper.lua:256 starts from four placeholders
        // and fills what it knows, so the box is the same height whether a
        // Pokemon has one move or four - and an enemy with two moves seen
        // shows two moves and two blanks, not a shorter table.
        val padded = (rows + List(4) { BLANK_MOVE }).take(4)
        padded.forEach { r ->
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        // A blank row is no move: the PC tracker ignores a tap there (InfoScreen.lua:651-656, Input.lua:466-490),
                        // where this opened a "---" card with PP 0 (rc32 audit P3 #43).
                        if (onMoveTap != null && !r.blank) Modifier.clickable { onMoveTap(r) }
                        else Modifier
                    )
                    .padding(horizontal = 2.rp, vertical = 1.rp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PcCategoryIcon(r.category)
                // KaizoCore's look: the name in its type's colour on every theme, made to read on the lower box
                // (TrackerLook.moveName). A theme's move-types flag (Pc.moveTypeBar) drew a plain name with a bar in
                // the type's colour instead; Blake likes the names coloured (2026-10-02), so the rows ignore it.
                // A blank row takes the live quiet colour (BLANK_MOVE captured it once at start-up).
                // A move this run bans takes the BST rule's look (MoveRule): the name in red and the X after it; its
                // type symbol stays, so the type still shows.
                val nameColor = when { r.blank -> Pc.Dim; r.banned -> Pc.Negative; else -> TrackerLook.moveName(r.color) }
                // The DS tracker's type symbol before the name, on every game (Blake, 2026-10-02: "I like the
                // symbols"). None where the type is hidden or unknown, and the slot is held so the names line up.
                TypeSymbol(r.typeName.takeIf { !r.blank })
                Spacer(Modifier.width(1.rp))
                if (r.banned) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    PixText(r.name, PcRef.FONT, nameColor, Modifier.weight(1f, fill = false))
                    RuleCross("Banned in this run")
                } else PixText(r.name, PcRef.FONT, nameColor, Modifier.weight(1f))
                if (r.hiddenPowerArrows && onHiddenPower != null) {
                    // MainScreenUIInitializer.initHiddenPowerArrows: "<" and ">" in the lower box's text colour, now each a
                    // named button (rc32 audit P2 #19, rc35 follow-up N #25). See HiddenPowerArrow for their size.
                    HiddenPowerArrow(forward = false) { onHiddenPower(false) }
                    HiddenPowerArrow(forward = true) { onHiddenPower(true) }
                }
                // ONE number, not cur/max. TrackerScreen.lua:1557 draws
                // drawNumber(movePPOffset, y, move.pp, 2) - two digits in a
                // 20-unit column - and DataHelper.lua:324 makes move.pp the
                // CURRENT pp for your own Pokemon. "15/15" was this app's
                // invention and it does not fit: it truncated to "15/1" and
                // took the Pow and Acc columns off the right edge with it.
                if (referenceColumns) {
                    Row(Modifier.width(PcRef.PP_W.rp), verticalAlignment = Alignment.CenterVertically) {
                        PixText(if (r.blank) "---" else r.ppText ?: "${r.pp}", PcRef.FONT, Pc.LowerText, Modifier.width(10.rp), numAlign)
                        Spacer(Modifier.width(1.rp))
                        Box(Modifier.width(5.rp), contentAlignment = Alignment.Center) {
                            r.effect?.let { PcEffectGlyph(it, Modifier.wrapContentWidth(unbounded = true)) }
                        }
                    }
                } else PixText(
                    if (r.blank) "---" else "${r.pp}",
                    PcRef.FONT, Pc.LowerText, Modifier.width(PcRef.PP_W.rp), TextAlign.End)
                val shownPower = r.powerText?.let { if (it == "0") "---" else it }
                    ?: if (r.power == null || r.power == 0) "---" else "${r.power}"
                // The effectiveness mark sits just left of the power digits, as
                // the reference draws it at movePowerOffset - 5.
                if (referenceColumns) {
                    PixText(shownPower, PcRef.FONT, if (r.stab) Pc.AltPositive else Pc.LowerText, Modifier.width(PcRef.POW_W.rp), numAlign)
                } else Row(Modifier.width(PcRef.POW_W.rp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    r.effect?.let { PcEffectGlyph(it); Spacer(Modifier.width(1.rp)) }
                    PixText(shownPower, PcRef.FONT, if (r.stab) Pc.AltPositive else Pc.LowerText)
                }
                val shownAcc = r.accText?.let { if (it == "0") "---" else it }
                    ?: if (r.acc == null || r.acc == 0) "---" else "${r.acc}"
                PixText(shownAcc, PcRef.FONT, Pc.LowerText, Modifier.width(PcRef.ACC_W.rp), numAlign)
            }
        }
    }
}

/** What a screen reader calls Hidden Power's arrows, on the DS card and in the move card's picker (HiddenPowerPicker). */
internal object HiddenPowerCopy {
    const val PREVIOUS = "Previous Hidden Power type"
    const val NEXT = "Next Hidden Power type"
}

/**
 * One of the DS card's Hidden Power arrows: a button with a spoken name, the full height of its move row and twice the
 * glyph's width. The row is one line of the reference's text, so the arrow cannot be 48dp tall without breaking the
 * moves table; Compose gives a target smaller than 48dp the touches around it that land on nothing else, the row's own
 * tap included, since the arrow is the deeper target. They were 3-unit pads around a bare glyph with no name.
 */
@Composable
private fun HiddenPowerArrow(forward: Boolean, onClick: () -> Unit) {
    val spoken = if (forward) HiddenPowerCopy.NEXT else HiddenPowerCopy.PREVIOUS
    Box(
        Modifier.sizeIn(minWidth = 12.rp).clickable(onClickLabel = spoken, role = Role.Button) { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { PixText(if (forward) ">" else "<", PcRef.FONT, Pc.LowerText) }
}

/**
 * The Pokemon Stadium announcer lines the PC tracker shows on a lost run,
 * copied from its Languages/English.lua GameOverScreenQuotes rather than
 * written fresh - half the point of the screen is that IronMON players know
 * these lines.
 */
internal val PcGameOverQuotes = listOf(
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
 * A win prints CONGRATULATIONS!!; a loss shows the line DeathQuotes drew for
 * this run, the same one the game over box shows: one per run, so it stays put
 * while you look at it instead of flickering on every tracker poll.
 */
@Composable
fun PcGameOver(won: Boolean, attempt: Int, party: List<TrackedMon>, onGrade: (() -> Unit)? = null) {
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
                else DeathQuotes.shown(DeathQuotes.PC_SOURCE, attempt, PcGameOverQuotes),
                9, if (won) Pc.Positive else Pc.Negative,
            )
            // GameOverScreen.NotesGrade: the score sheet, from here only.
            onGrade?.let { Spacer(Modifier.height(5.dp)); PcSmallButton("GRADE MY NOTES") { it() } }
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
        // In sp: it is drawn in the Pokemon info window, outside the panel (rc32 audit P2 #19).
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
            DialogText("Coverage", 13, Pc.Text, Modifier.weight(1f))
            DialogText("$total mons", 12, Pc.Dim)
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
                    DialogText(
                        "$n", 13,
                        when {
                            mult == 0.0 && n > 0 -> Pc.Negative
                            mult >= 2.0 -> Pc.Positive
                            else -> Pc.Text
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    DialogText(label, 12, Pc.Dim)
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
            // The reference's die (RerollBallPicker): clear the choice and roll again.
            // It was a REROLL text chip here, which the reference never shows.
            if (onReroll != null) PcDiceButton(onReroll)
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("LEFT", "MIDDLE", "RIGHT").forEachIndexed { i, label ->
                val chosen = i == pick
                // One item per ball for a screen reader, "Middle ball, selected" for the pick: it read the three words
                // with nothing to say which was picked, only the drawing did (rc32 audit P3 #44).
                Column(
                    Modifier.clearAndSetSemantics { contentDescription = BallCallCopy.spoken(label); selected = chosen },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
    // "Show physical special icons" off: the slot is held, the glyph is not drawn.
    val glyph = if (!(Pc.categoryIconsX ?: TrackerOptions.showCategoryIcons)) null else when (category) {
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
                        color = Pc.LowerText,
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
    /** Constants.PixelImages.DICE, 13x14: the reroll button under the balls (TrackerScreen.Buttons.RerollBallPicker). */
    val DICE = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0),
        intArrayOf(0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0),
        intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1),
        intArrayOf(1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1),
        intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1),
        intArrayOf(0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0),
        intArrayOf(0, 0, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0),
    )
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
 * badges and the pedometer, 420 for trainers, 180 for the battle items. Items that cannot show are skipped rather
 * than rendered blank, exactly as rotateToNextItem does.
 */
@Composable
fun PcCarousel(
    inBattle: Boolean,
    /** Battle.isViewingOwn: true outside battle, and in battle when your side is on screen. */
    viewingOwn: Boolean = !inBattle,
    isWildBattle: Boolean = false,
    /** The lead's level: under 13, the route's wild encounters replace the badges. */
    leadLevel: Int = 0,
    routeTrainersDefeated: Int = 0,
    badges: Int,
    badgeSet: String,
    note: String,
    onEditNote: () -> Unit,
    /**
     * The opponent's card carries the note itself (the DS panel's, after the DS tracker's MainScreen): the strip
     * never shows it then. Black 2 drew "NOTE tap to add a note" twice, on the card and under it (Blake, 2026-10-02).
     */
    notesInCard: Boolean = false,
    /** "Wing Attack: 23 damage" (DamageWatch), or null when it is not time to show it. */
    lastAttack: String? = null,
    /** The hit would knock your Pokemon out: the sword turns red. */
    lastAttackLethal: Boolean = false,
    /** BattleSummary.line: the viewed battler's first battle detail, or null when it has none. */
    battleDetailsSummary: String? = null,
    /** No longer shown: the battle line follows the reference. The DS panel still passes it. */
    encounters: Int = 0,
    routeName: String? = null,
    routeSeen: Int = 0,
    routeTotal: Int = 0,
    /** Battle.CurrentRoute.encounterArea when RouteData has it (hasInfo), or Walking outside battle. */
    routeArea: String? = null,
    routeTrainers: Int = 0,
    routeBosses: Int = 0,
    steps: Int = 0,
    /** The pedometer needs a real step count on a real map, out of a game over. */
    pedometerAllowed: Boolean = false,
    onRouteTap: (() -> Unit)? = null,
    /** TrackerScreen.Buttons.TrainerSummary: the Trainers line opens Trainers on Route. */
    onTrainersTap: (() -> Unit)? = null,
    /** TrackerScreen.Buttons.BattleDetailsSummary: the Battle line opens Battle Details. */
    onBattleDetailsTap: (() -> Unit)? = null,
    /** Calc Atk's hook on TrackerScreen.Buttons.LastAttackSummary: the last attack opens the calculator. */
    onLastAttackTap: (() -> Unit)? = null,
    /** HGSS: the League is beaten, so a single badge row is Kanto's (hgssBadgeRows). */
    leagueBeaten: Boolean = false,
) {
    // TrackerScreen.getCurrentCarouselItem, item for item. The order is
    // CarouselTypes': BADGES, TRAINERS, LAST_ATTACK, ROUTE_INFO, NOTES,
    // BATTLE_DETAILS, PEDOMETER. Each shows only while its Setup toggle is on
    // and its own condition holds; the current one stays until its frames run
    // out (with rotation allowed) or it can no longer show.
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    // Battle.lua:836: a trainer battle ending shows TRAINERS for 300 frames.
    var trainersUntil by remember { mutableStateOf(0L) }
    var wasTrainerBattle by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }
    var shownSince by remember { mutableStateOf(0L) }
    LaunchedEffect(inBattle) {
        if (inBattle) wasTrainerBattle = !isWildBattle
        else if (wasTrainerBattle) {
            wasTrainerBattle = false
            trainersUntil = System.currentTimeMillis() + 5000
            if (TrackerOptions.carouselShows("Trainers")) { index = 1; shownSince = System.currentTimeMillis() }
        }
    }
    val earlyRoute = (!inBattle || isWildBattle) && leadLevel in 1..12 && routeTotal > 0
    val pedometerShows = TrackerOptions.carouselShows("Pedometer") && viewingOwn && !inBattle &&
        TrackerOptions.displayPedometer && pedometerAllowed
    // (key, frames at 60fps, speed locked, can show)
    val items = listOf(
        Triple("badges", 210, TrackerOptions.carouselShows("Badges") && viewingOwn && !earlyRoute &&
            (TrackerOptions.allowCarouselRotation || !pedometerShows)),
        Triple("trainers", 420, TrackerOptions.carouselShows("Trainers") && !inBattle &&
            now.value < trainersUntil && routeTrainers > 0),
        Triple("lastAttack", 180, TrackerOptions.carouselShows("LastAttack") && inBattle && !lastAttack.isNullOrBlank()),
        Triple("route", 180, TrackerOptions.carouselShows("RouteInfo") &&
            (earlyRoute || (inBattle && isWildBattle && routeArea != null && routeTotal > 0))),
        Triple("notes", 180, TrackerOptions.carouselShows("Notes") && !viewingOwn && !notesInCard),
        // TrackerScreen.lua:772-777: in battle, while the viewed battler has a detail to summarize.
        Triple("battleDetails", 180, TrackerOptions.carouselShows("BattleDetails") && inBattle && battleDetailsSummary != null),
        Triple("pedometer", 210, pedometerShows),
    )
    fun rotate() {
        for (step in 1..items.size) {
            val i = (index + step) % items.size
            if (items[i].third) { index = i; break }
        }
        shownSince = System.currentTimeMillis()
    }
    val speed = mapOf("1/2" to 2.0, "1" to 1.0, "2" to 0.5, "3" to 1.0 / 3, "4" to 0.25)[TrackerOptions.carouselSpeed] ?: 1.0
    val cur = items[index]
    val ms = (cur.second * 1000L / 60 * (if (cur.first == "trainers") 1.0 else speed)).toLong()
    if (!cur.third) { if (items.any { it.third }) rotate() }
    else if (TrackerOptions.allowCarouselRotation && now.value - shownSince > ms && shownSince != 0L) rotate()
    if (shownSince == 0L) shownSince = now.value
    // The clock wakes only when something is due: the rotation, or the end of TRAINERS (CarouselClock). It ticked ten
    // times a second all session, recomposing the strip each time (rc32 audit P3 #45).
    LaunchedEffect(index, shownSince, ms, trainersUntil, TrackerOptions.allowCarouselRotation) {
        while (true) {
            now.value = System.currentTimeMillis()
            val wait = CarouselClock.wait(now.value, shownSince, ms, trainersUntil, TrackerOptions.allowCarouselRotation) ?: break
            kotlinx.coroutines.delay(wait)
        }
    }
    val shown = items[index]
    if (!shown.third) return

    when (shown.first) {
        "badges" -> PcBadgeRow(badges, badgeSet, leagueBeaten)
        "pedometer" -> PcPedometerLine(steps)
        "trainers" -> PcCarouselLine("Trainers defeated:", "$routeTrainersDefeated/$routeTrainers", Pc.LowerText, onTap = onTrainersTap)
        "notes" -> PcNoteRow(note, onEditNote)
        "lastAttack" -> PcLastAttackLine(lastAttack ?: "", lastAttackLethal, onLastAttackTap)
        // "%s: %s %s": the area, seen/total (just seen when a seed puts more there), "Seen Pokemon".
        "route" -> PcCarouselLine(
            (routeArea ?: "Walking") + ":",
            (if (routeSeen > routeTotal) "$routeSeen" else "$routeSeen/$routeTotal") + " Seen Pok\u00e9mon",
            Pc.LowerText,
            onTap = onRouteTap,
        )
        "battleDetails" -> PcBattleSummaryLine(battleDetailsSummary ?: "", onBattleDetailsTap)
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
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp))
            .background(TrackerBackground.boxFill(Pc.LowerGroundX ?: Pc.Ground)).border(1.dp, Pc.LowerBorder.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp))
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixText(label, 8, Pc.LowerText)
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
    // The shared compact card (InfoSheet, 2026-09-15): capped height and
    // scrollable, as before, so a long description never pushes the X off.
    InfoSheet(title, onDismiss) {
        if (!subtitle.isNullOrBlank()) {
            // In sp, as InfoParagraph below (rc32 audit P2 #19).
            DialogText(subtitle, 13, Pc.Text)
            Spacer(Modifier.height(8.dp))
        }
        InfoParagraph(
            null,
            body?.takeIf { it.isNotBlank() } ?: "No description for this one.",
            if (body.isNullOrBlank()) Pc.Dim else Pc.Text,
        )
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
    /** Utils.getDetailedEvolutionsInfo's lines, "Fire Stone" or "Level 30" over "Water Stone" (EvoText.detailed). */
    evolution: List<String>,
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
    /** InfoScreen's ViewRandomEvos button, only when the species has revo data. */
    onRandomEvos: (() -> Unit)? = null,
    /** InfoScreen's PreviousPokemon / NextPokemon arrows (showNextPokemon). */
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    /** InfoScreen's LookupPokemon: every species by name, to jump to one. */
    lookup: (() -> List<Pair<Int, String>>)? = null,
    onLookup: ((Int) -> Unit)? = null,
    /** InfoScreen's History (Move History) and Resistances (Type Defenses) buttons. */
    onHistory: (() -> Unit)? = null,
    onResistances: (() -> Unit)? = null,
    /** InfoScreen's NotepadTracking: the species' note, editable. */
    onEditNote: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var lookupOpen by remember { mutableStateOf(false) }
    if (lookupOpen && lookup != null && onLookup != null) {
        // Built when the window opens: the names come from the game, which may not have
        // been readable yet when this screen was first composed.
        val names = remember { lookup() }
        PcNameLookup("Look up a Pok\u00e9mon:", names, onPick = { lookupOpen = false; onLookup(it) }) { lookupOpen = false }
    }
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 48dp targets with a spoken name (rc32 audit P2 #42, #102): 8 to 20dp glyphs read as "<", ">", "SEARCH".
                onPrevious?.let { PcTap("<", 11, Pc.Text, "Previous Pok\u00e9mon") { it() } }
                // The words here follow the phone's font size (rc32 audit P2 #19): they were 8 to 11dp.
                DialogText(name, 16, Pc.Gold, Modifier.weight(1f), heading = true)
                onNext?.let { PcTap(">", 11, Pc.Text, "Next Pok\u00e9mon") { it() } }
                if (lookup != null && onLookup != null) PcTap("SEARCH", 8, Pc.Gold, "Look up a Pok\u00e9mon") { lookupOpen = true }
            }
            Spacer(Modifier.height(6.dp))
            Row { types.forEach { (t, c) -> PcTypeChip(t, c); Spacer(Modifier.width(4.dp)) } }
            Spacer(Modifier.height(8.dp))

            PcInfoRow("BST", bst)
            weight?.let { PcInfoRow("Weight", "$it kg") }
            // InfoScreen.lua:739-745: the method in the reference's words, its second way on a line of its own.
            evolution.ifEmpty { listOf("---") }.forEachIndexed { i, line -> PcInfoRow(if (i == 0) "Evolves" else "", line) }
            if (onRandomEvos != null || onHistory != null || onResistances != null) {
                Spacer(Modifier.height(4.dp))
                PcButtonRow {
                    onRandomEvos?.let { GearButton("VIEW EVOS", Modifier) { it() } }
                    onHistory?.let { GearButton("HISTORY", Modifier) { it() } }
                    onResistances?.let { GearButton("RESISTANCES", Modifier) { it() } }
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
            Spacer(Modifier.height(8.dp))

            // Only the halves that matter: what beats it, and what it laughs off.
            if (InfoScreenLines.hasNoWeaknesses(effectiveness)) PcInfoRow("Weak to", InfoScreenLines.NO_WEAKNESSES)
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

            // InfoScreen.lua:765-774: the learn levels always have their place, and say when there are none.
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
            Spacer(Modifier.height(8.dp))
            DialogText("Learns at", 13, Pc.Text)
            Spacer(Modifier.height(3.dp))
            // Levels already passed are dimmed; the next one is the one
            // worth knowing about.
            DialogText(InfoScreenLines.learnLevels(moveLevels, level), 13, Pc.Dim)

            if (onEditNote != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onEditNote() },
                    contentAlignment = Alignment.CenterStart) {
                    PcInfoRow("Note", note.ifBlank { "tap to add a note" }, if (note.isBlank()) Pc.Dim else Pc.Gold)
                }
            } else if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                PcInfoRow("Note", note, Pc.Gold)
            }

            Spacer(Modifier.height(12.dp))
            GearButton("CLOSE") { onDismiss() }
        }
    }
}

/** A row of buttons in a tracker window that wraps onto a second line rather than squeezing one. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PcButtonRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * The reference's lookup windows (InfoScreen.openPokemonInfoWindow and friends): a list of
 * every name to pick from. A dropdown there; a filterable list here, since a phone has no
 * dropdown that holds 400 names well.
 */
@Composable
fun PcNameLookup(title: String, names: List<Pair<Int, String>>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp).background(Pc.Ground).border(1.dp, Pc.Border).padding(12.dp)
        ) {
            DialogText(title, 14, Pc.Gold, heading = true)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            val shown = names.filter { query.isBlank() || it.second.contains(query.trim(), ignoreCase = true) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                shown.take(400).forEach { (id, n) ->
                    Box(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onPick(id) },
                        contentAlignment = Alignment.CenterStart) { DialogText(n, 13, Pc.Text) }
                }
            }
            Spacer(Modifier.height(6.dp))
            GearButton("CLOSE") { onDismiss() }
        }
    }
}

@Composable
private fun PcInfoRow(label: String, value: String, color: Color = Pc.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        DialogText(label, 13, Pc.Dim, Modifier.width(96.dp))
        DialogText(value, 13, color, Modifier.weight(1f))
    }
}

/** The eight gym badges, lit as they are earned - the PC tracker's badge row. */
@Composable
fun PcBadgeRow(badges: Int, set: String, leagueBeaten: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Gold, Silver and Crystal: both rows, always (GbBadgeRows.kt).
    if (set == "GSC") {
        Column { gscBadgeRows(badges).forEach { (art, bits) -> PcBadgeRow(bits, art) } }
        return
    }
    // HGSS has sixteen: Johto in bits 0-7 (set HGSS), Kanto in 8-15 (set
    // HGSS_K, art already bundled). Which rows, in which order, is the DS
    // tracker's badgesAppearance (hgssBadgeRows).
    if (set == "HGSS") {
        Column {
            hgssBadgeRows(badges, TrackerOptions.showBothBadgeSets, TrackerOptions.kantoBadgesFirst, leagueBeaten)
                .forEach { (bits, art) -> PcBadgeRow(bits, art) }
        }
        return
    }
    val artSet = if (set == "HGSS_J") "HGSS" else set
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp))
            .background(TrackerBackground.boxFill(Pc.Ground)).border(1.dp, TrackerLook.outline, androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp))
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
fun PcHealsBlock(
    percent: Int, count: Int, wholeHp: Int? = null, pcHealsAttempt: Int? = null,
    /** TrackerScreen.Buttons.HealsInBag: the heals text opens Heals in Bag on its All tab. */
    onTap: (() -> Unit)? = null,
) {
    // TrackerScreen.lua:1276 draws both lines in Default text. The colour
    // ramp here was invented, and it painted "0% HP (0)" bright red as though
    // something were wrong rather than simply reporting an empty bag.
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.rp, vertical = 1.rp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)) {
            PixText("Heals:", PcRef.FONT, Pc.Text)
            // "Show heals as whole number": the HP the bag would restore, instead of the share of max HP.
            PixText(if (TrackerOptions.healsWhole && wholeHp != null) "$wholeHp HP ($count)" else "$percent% HP ($count)", PcRef.FONT, Pc.Text)
        }
        // "Track PC Heals": the counter at the box's right edge.
        if (pcHealsAttempt != null) PcHealCounter(pcHealsAttempt)
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
    // KaizoCore's look (TrackerLook): a rounded card, its outline the theme's border softened.
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 3.rp).then(Modifier.clip(shape))
            .background(TrackerBackground.boxFill(Pc.Ground), shape).border(1.rp, TrackerLook.outline, shape),
    ) { content() }
}

/**
 * A Pokemon's card: its [head] (PcHeadBlock: the info and the stats), then [rest] (the moves, and on the DS the
 * encounter and note rows). On a wide canvas (LocalTrackerWide) the rest goes BESIDE the head, the head at the
 * reference's 140 units and the rest in what is left, so a phone in portrait shows the whole card at once.
 */
@Composable
fun PcMonCard(head: @Composable () -> Unit, rest: @Composable () -> Unit) = PcCard {
    if (LocalTrackerWide.current) {
        // IntrinsicSize.Min: the rule between the two runs the height of the taller.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.width(PcRef.HEAD_W.rp)) { head() }
            Box(Modifier.width(1.rp).fillMaxHeight().background(TrackerLook.divider))
            Column(Modifier.weight(1f)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalMovesBeside provides true) { rest() }
            }
        }
    } else {
        head()
        rest()
    }
}

/**
 * The reference's 13x14 die, drawn in "Default text" like its other pixel buttons, in a tracker button's 44dp touch
 * box with its name for a screen reader (rc32 audit P3 #44): it was a 24dp box with no name.
 */
@Composable
fun PcDiceButton(onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)
            .clickable(role = Role.Button, onClickLabel = BallCallCopy.REROLL) { onClick() }
            .semantics { contentDescription = BallCallCopy.REROLL },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.width(13.rp).height(14.rp)) {
            val px = size.width / 13f
            for (y in 0 until 14) for (x in 0 until 13) {
                if (PcCategoryGlyphs.DICE[y][x] == 1) drawRect(
                    color = Pc.Text,
                    topLeft = androidx.compose.ui.geometry.Offset(x * px, y * px),
                    size = androidx.compose.ui.geometry.Size(px, px),
                )
            }
        }
    }
}

/** What a screen reader hears of the starter ball call (PcBallPicker, PcDiceButton). */
internal object BallCallCopy {
    const val REROLL = "Reroll the ball"
    fun spoken(label: String) = label.lowercase().replaceFirstChar { it.uppercase() } + " ball"
}

/**
 * The least a control or a label may be (2026-09-30, UX audit P0-14 and P0-15). Plain numbers, so a test can hold
 * the sources to them.
 */
internal object PcMin {
    /** A tracker button's touch box, in dp, both ways. The drawn button is 11 reference pixels tall, about 16dp. */
    const val TOUCH_DP = 44
    /** A dialog's row, tab or close mark: Android's own minimum, as Shell.touchTarget. */
    const val DIALOG_TOUCH_DP = 48
    /** A label in a tracker dialog, in sp: it follows the phone's font size and never drops under this. */
    const val LABEL_SP = 12
    /** The clear space between the RUN and SEE MINE touch boxes, in dp. */
    const val BUTTON_GAP_DP = 10
}

/**
 * A button that asks twice (2026-09-30, UX audit P0-14): the first tap arms it, a second inside [WINDOW_MS] does
 * the thing, and otherwise it disarms. It is the "Sure?" the library's deletes use (RomLibraryScreen's
 * DISARM_MS), kept as a plain holder so the timing is proved on the JVM. RUN used to flee the wild battle on the
 * first touch, and in a Nuzlocke a mis-tap next to SEE MINE cost the encounter.
 */
internal class ArmedTap(private val windowMs: Long = WINDOW_MS) {
    /** When it was armed, or [NOT_ARMED]. State, so the label follows it and a timer keyed on it restarts on every arming. */
    var armedAt by mutableStateOf(NOT_ARMED)
        private set

    val armed: Boolean get() = armedAt != NOT_ARMED

    /** A tap at [now] (ms, on any steady clock). True: do the thing. False: this tap only armed it. */
    fun tap(now: Long): Boolean {
        if (armed && now - armedAt <= windowMs) { armedAt = NOT_ARMED; return true }
        armedAt = now
        return false
    }

    /** The window ran out, or the button left the screen. */
    fun disarm() { armedAt = NOT_ARMED }

    companion object {
        const val WINDOW_MS = 3000L
        const val NOT_ARMED = Long.MIN_VALUE
    }
}

/**
 * The tracker's small button: drawn 11 reference pixels tall, in a touch box of at least [PcMin.TOUCH_DP] both ways
 * (2026-09-30, UX audit P0-14). The look is the tracker's and stays; only the box around it grew. SETUP is one.
 */
@Composable
fun PcSmallButton(label: String, onClick: () -> Unit) = PcButton(label, onClick = onClick)

/**
 * [PcSmallButton] with a colour for its label, and [spoken] for a screen reader in place of the label. [alert]
 * announces the change when the label changes under a finger (RUN turning into RUN?).
 */
@Composable
internal fun PcButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Pc.Text,
    spoken: String? = null,
    alert: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)
            .clickable(role = Role.Button) { onClick() }
            .semantics {
                if (spoken != null) contentDescription = spoken
                if (alert) liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        // KaizoCore's look: a pill on the faint raised surface, the label a weight heavier.
        Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(TrackerLook.inset).padding(horizontal = 6.rp, vertical = 2.rp)) {
            PixText(label, PcRef.FONT, color, weight = FontWeight.Medium)
        }
    }
}

/**
 * What the banner's buttons say, drawn and spoken. The drawn words are the PC tracker's own short ones; a screen
 * reader gets a sentence.
 */
internal object PcBannerCopy {
    const val RUN = "RUN"
    const val RUN_ARMED = "RUN?"
    const val RUN_SPOKEN = "Run from this battle"
    const val RUN_ARMED_SPOKEN = "Run away? Tap again to run"
    const val SEE_MINE = "SEE MINE"
    const val SEE_FOE = "SEE FOE"
    const val SEE_MINE_SPOKEN = "Show my Pok\u00e9mon"
    const val SEE_FOE_SPOKEN = "Show the opponent's Pok\u00e9mon"
    const val TRAINER_SPOKEN = "Open the trainer's info"

    fun run(armed: Boolean) = if (armed) RUN_ARMED else RUN
    fun runSpoken(armed: Boolean) = if (armed) RUN_ARMED_SPOKEN else RUN_SPOKEN
    /** The button offers the side that is not on screen. */
    fun see(viewingOwn: Boolean) = if (viewingOwn) SEE_FOE else SEE_MINE
    fun seeSpoken(viewingOwn: Boolean) = if (viewingOwn) SEE_FOE_SPOKEN else SEE_MINE_SPOKEN

    /** The same, naming where the Pokemon it goes to stands in a battle with more than one a side ([next]; null in a single battle). */
    fun seeSpoken(viewingOwn: Boolean, next: SideSpot?): String = seeSpoken(viewingOwn) + when {
        next == null || next.at.isEmpty() -> ""
        next.place == BattleSideWords.MIDDLE -> " in the middle"
        next.place != null -> " on the " + next.place.lowercase()
        else -> ", " + next.at.lowercase()
    }
}

/**
 * The strip a battle banner is drawn as, with room for touch (2026-09-30, UX audit P0-14). The strip keeps its
 * look and its height; the buttons in it sit in a band [PcMin.TOUCH_DP] tall, so their touch boxes are real
 * space that never reaches the row above or the card below. The blank above and below the strip is the price.
 * [fill] is the strip's own colour, so a banner decides for itself whether the tracker's image shows through.
 */
@Composable
internal fun PcBannerBand(
    fill: Color,
    buttons: Boolean,
    label: @Composable () -> Unit,
    controls: @Composable RowScope.() -> Unit,
) {
    // KaizoCore's look (2026-10-02, "lots of wasted space"): the band IS the touch row, a rounded bar as tall as its
    // buttons' touch boxes, where it used to be a thin strip with blank space above and below it for them.
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(TrackerLook.RADIUS.rp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (buttons) PcMin.TOUCH_DP.dp else (PcRef.FONT + 8).rp)
            .clip(shape).background(fill, shape).border(1.rp, TrackerLook.outline, shape).padding(horizontal = 4.rp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label gives way, so the buttons are never squeezed off a narrow pane.
        Box(Modifier.weight(1f)) { label() }
        controls()
    }
}

/**
 * Battle banner. RUN only appears in wild battles: trainers never allow it. RUN asks twice (ArmedTap): the first
 * tap draws it as RUN? in the warning colour, a second inside three seconds flees, and otherwise it disarms.
 */
@Composable
fun PcBattleBanner(
    isWild: Boolean,
    onFlee: () -> Unit,
    viewingOwn: Boolean = false,
    onSwapView: (() -> Unit)? = null,
    /** TrainersOnRouteScreen: the TRAINER BATTLE banner opens Trainer Info for the opponent. */
    onTrainerTap: (() -> Unit)? = null,
    /** Tracker Setup, at the banner's end: in a battle SETUP has no row of its own (2026-10-02, "wasted space"). */
    onGear: (() -> Unit)? = null,
    /** The battle's weather (TrackerState.weather), as a pill under the label; null for clear skies or a game without it. */
    weather: String? = null,
    /** After SETUP: landscape's corner arrow (TrackerCornerMenu), so it takes no row of its own either. */
    trailing: (@Composable () -> Unit)? = null,
    /** A Kaizo IronMON run's attempt, under the label (ironmonRunInPlay); null for none. */
    attempt: Int? = null,
    /**
     * A battle with more than one Pokemon a side: which of them the cards show, in words, under the label
     * (BattleSideWords); null in a single battle.
     */
    side: BannerSide? = null,
    /** The swap button's spoken label, naming where its next Pokemon stands; null for the plain one. */
    swapSpoken: String? = null,
) {
    val run = remember { ArmedTap() }
    // A timer per arming: armedAt changes on every tap that arms, so an older timer never disarms a newer one.
    LaunchedEffect(run.armedAt) {
        if (run.armed) { kotlinx.coroutines.delay(ArmedTap.WINDOW_MS); run.disarm() }
    }
    PcBannerBand(
        fill = TrackerBackground.boxFill(Pc.Ground),
        buttons = onSwapView != null || isWild || onGear != null || trailing != null,
        label = {
            val trainerTap = if (isWild) null else onTrainerTap
            Box(
                if (trainerTap != null) {
                    Modifier.heightIn(min = PcMin.TOUCH_DP.dp)
                        .clickable(onClickLabel = PcBannerCopy.TRAINER_SPOKEN, role = Role.Button) { trainerTap() }
                } else Modifier,
                contentAlignment = Alignment.CenterStart,
            ) {
                // The band is a touch row's height anyway: the weather takes the line under the label, not a row.
                Column {
                    // "TRAINER" or "WILD" where the whole words do not fit: in a narrow window the buttons left the
                    // label a single "T" (2026-10-02).
                    val full = if (isWild) "WILD BATTLE" else "TRAINER BATTLE"
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                        val style = androidx.compose.ui.text.TextStyle(fontFamily = PcFont, fontSize = PcRef.FONT.rsp, fontWeight = FontWeight.Medium)
                        val fits = measurer.measure(full, style).size.width <= constraints.maxWidth
                        PixText(
                            if (fits) full else if (isWild) "WILD" else "TRAINER", PcRef.FONT,
                            if (isWild) Pc.Positive else Pc.Negative, weight = FontWeight.Medium,
                        )
                    }
                    // A double (or triple) battle: which Pokemon the cards show, by where it stands on the game's screen. The
                    // reference names none, its swap being a hotkey; here it is the one way to tell two of a side apart.
                    side?.let { s ->
                        Spacer(Modifier.height(2.rp))
                        androidx.compose.foundation.layout.BoxWithConstraints {
                            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                            val style = androidx.compose.ui.text.TextStyle(fontFamily = PcFont, fontSize = (PcRef.FONT - 2).rsp, fontWeight = FontWeight.Medium)
                            val fits = measurer.measure(s.full, style).size.width <= constraints.maxWidth
                            // Wraps rather than clips where even the short words do not fit a narrow window.
                            PixText(if (fits) s.full else s.short, PcRef.FONT - 2, Pc.Text, weight = FontWeight.Medium, wrap = true)
                        }
                    }
                    val weatherShown = weather != null && TrackerWeather.name(weather) != null
                    if (attempt != null || weatherShown) {
                        Spacer(Modifier.height(2.rp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            attempt?.let { PixText("ATTEMPT $it", PcRef.FONT - 2, Pc.Dim, weight = FontWeight.Medium) }
                            if (attempt != null && weatherShown) Spacer(Modifier.width(4.rp))
                            if (weatherShown) TrackerWeatherPill(weather!!)
                        }
                    }
                }
            }
        },
    ) {
        // The reference swaps between your Pokemon and the enemy's with a
        // CONTROLLER BUTTON (Input.lua:178, Battle.togglePokemonViewed),
        // so it needs nothing on screen. There is no controller here, so
        // the same action needs somewhere to be tapped. This is the one
        // control added rather than copied, and it exists only during a
        // battle, which is the only time the reference's hotkey does
        // anything either.
        onSwapView?.let {
            PcButton(PcBannerCopy.see(viewingOwn), spoken = swapSpoken ?: PcBannerCopy.seeSpoken(viewingOwn), onClick = it)
        }
        if (isWild) {
            if (onSwapView != null) Spacer(Modifier.width(PcMin.BUTTON_GAP_DP.dp))
            PcButton(
                PcBannerCopy.run(run.armed),
                color = if (run.armed) Pc.Negative else Pc.Text,
                spoken = PcBannerCopy.runSpoken(run.armed),
                alert = run.armed,
            ) { if (run.tap(android.os.SystemClock.elapsedRealtime())) onFlee() }
        }
        onGear?.let {
            Spacer(Modifier.width(PcMin.BUTTON_GAP_DP.dp))
            TrackerGearButton(onClick = it)
        }
        trailing?.invoke()
    }
}


/**
 * The reference's TypeDefensesScreen (Ironmon-Tracker screens/TypeDefensesScreen.lua,
 * read 2026-09-08; the Gen 1 and 2 trackers carry the same file): a box headed by
 * the Pokemon's name, then one row per bucket that has any types, in the order
 * "0x Immunities", "1/4x Resistances", "1/2x Resistances", "2x Weaknesses",
 * "4x Weaknesses", each with its type boxes four to a line. Reached by tapping
 * the type icons on a Pokemon's card, which is where the reference puts it.
 */
@Composable
fun TypeDefensesDialog(name: String, buckets: Map<Double, List<String>>, onClose: () -> Unit) {
    val rows = listOf(0.0 to ("0x" to "Immunities"), 0.25 to ("1/4x" to "Resistances"), 0.5 to ("1/2x" to "Resistances"), 2.0 to ("2x" to "Weaknesses"), 4.0 to ("4x" to "Weaknesses"))
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText(name.uppercase(), 10, Pc.Text, Modifier.weight(1f))
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            var any = false
            rows.forEach { (mult, label) ->
                val types = buckets[mult] ?: return@forEach
                if (types.isEmpty()) return@forEach
                any = true
                PixText("${label.first} ${label.second}", 8, Pc.Text)
                Spacer(Modifier.height(2.dp))
                types.chunked(4).forEach { line ->
                    Row(Modifier.padding(start = 8.dp, bottom = 2.dp)) {
                        line.forEach { t ->
                            Box(Modifier.padding(end = 2.dp).border(1.dp, Pc.Border).background(Pc.Ground).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                PixText(t.uppercase(), 7, pcTypeColorByName(t))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (!any) PixText("No type has an edge either way.", 8, Pc.Dim)
        }
    }
}

/**
 * "Lv.5 (30)": TrackerScreen.lua draws the level and brackets in the default
 * colour, then redraws the method in its own: green when ready, the
 * intermediate colour otherwise. A friendship evolution instead fills green
 * one letter at a time over the default-coloured word.
 */
@Composable
private fun PcLevelLine(level: Int, evo: com.ironmonone.tracker.EvoText.Label?, prefix: String = "Lv.") {
    Row {
        PixText("$prefix$level", PcRef.FONT, Pc.Text)
        if (evo != null) {
            val t = evo.text
            val n = evo.highlighted.coerceIn(0, t.length)
            val rest = when (evo.tone) {
                com.ironmonone.tracker.EvoText.Tone.READY -> Pc.Positive
                com.ironmonone.tracker.EvoText.Tone.WAITING -> Pc.Gold
                com.ironmonone.tracker.EvoText.Tone.PLAIN -> Pc.Text
            }
            PixText(" (", PcRef.FONT, Pc.Text)
            val fill = evo.fill
            if (fill != null) {
                // The word in the default colour with a green copy over it, clipped to the fill.
                androidx.compose.foundation.layout.Box {
                    PixText(t, PcRef.FONT, Pc.Text)
                    PixText(t, PcRef.FONT, Pc.Positive, Modifier.drawWithContent {
                        clipRect(right = size.width * fill.coerceIn(0f, 1f)) { this@drawWithContent.drawContent() }
                    })
                }
            } else {
                if (n > 0) PixText(t.substring(0, n), PcRef.FONT, Pc.Positive)
                if (n < t.length) PixText(t.substring(n), PcRef.FONT, rest)
            }
            PixText(")", PcRef.FONT, Pc.Text)
        }
    }
}
