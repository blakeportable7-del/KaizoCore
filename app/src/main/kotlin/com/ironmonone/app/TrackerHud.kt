package com.ironmonone.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Tracker HUD (Blake, 2026-10-04: "like an overlay layer on the full game screen, info is close to the outside of all
 * sides of the game screen", "like a tracker HUD", then "take advantage of the black bars", "it can't interfere with the
 * buttons" and "the purpose of this is to get all tracker information while not making the play screen smaller").
 *
 * It is a third landscape choice beside Docked and Float, and it rides on the floating window's path
 * ([TrackerOptions.trackerHud] with the landscape setting at FLOATING), so Play lays the game out exactly as it does with
 * no tracker beside it: the game is never moved or made smaller. The HUD is only an overlay, two panels in a cockpit
 * frame: your Pokemon in one, the battle, the opponent and the route in the other. Each goes in the black bar beside the
 * game, in the tallest run of it that no on-screen control (the pad as drawn, a moved button included) comes near; a bar
 * too narrow for a readable panel gives way to a see-through panel over the game's edge, never over a control and never
 * over a DS touch screen. The panels draw the tracker's own cards ([WideCards] hands your card over through
 * [LocalHudPortal]), so every Tracker Setup switch shows or hides the same things here.
 */
object TrackerHud {
    /**
     * Off for rc35.1 (Blake, 2026-10-04: keep the HUD hidden until its second pass). With it off no menu or Tracker Setup
     * line offers the HUD, and a saved HUD choice loads as the floating window. The code stays; this one switch brings it back.
     */
    const val ENABLED = false

    /** The game picture in window pixels, as Play last laid it out (PlayScreen's gameFrame). */
    var game by mutableStateOf<Rect?>(null)
    /** The pad as drawn over the game, and its skin (ScreenTapMenu, which Play hands both every frame); null with no pad. */
    var pad by mutableStateOf<PadLayout?>(null)
    var padSkin by mutableStateOf(PadSkin.OUTLINE)
    /** Two DS screens on the phone: the game's right part is a touch screen, and nothing may lie over it. */
    var ds by mutableStateOf(false)

    /** The default accent, a cockpit cyan; a theme with its own border colour gives its own. */
    val CYAN = Color(0xFF3FE0FF)
    private val DEFAULT_BORDER = Color(0xFFAAAAAA)
    fun accent(border: Color): Color = if (border == DEFAULT_BORDER) CYAN else border

    /** Said in the menus. */
    const val MENU_HUD = "Tracker HUD (info around the game)"
    const val MENU_FLOAT = "Float the tracker (move and resize it)"
}

/**
 * Where the HUD's two panels go, in dp, in an area [areaW] by [areaH]: plain arithmetic the tests run for every phone
 * shape and pad layout. Nothing it returns meets a control grown by [MARGIN], leaves the area, or meets the other panel.
 */
object HudLayout {
    data class R(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        fun meets(o: R) = l < o.r && o.l < r && t < o.b && o.t < b
        fun grown(by: Float) = R(l - by, t - by, r + by, b + by)
    }

    /** Room kept round every control. */
    const val MARGIN = 8f
    /** The narrowest a panel may be and still be read. */
    const val MIN_W = 150f
    /** The shortest a panel may be. */
    const val MIN_H = 96f
    /** Air between a panel and the screen's or the game's edge. */
    const val EDGE = 4f

    /** The width a panel is given when the bar allows it; a narrower bar's panel reaches over the game's edge to it. */
    const val WANT_W = 230f
    /** The widest a panel grows in a wide bar. */
    const val MAX_W = 320f
    /** The menu's round button. */
    const val MENU = 44f

    /** [mine] and [rest] are the two panels; [menu] is the square the tracker's menu button sits in. */
    data class Plan(val mine: R?, val rest: R?, val menu: R? = null)

    fun plan(areaW: Float, areaH: Float, game: R?, controls: List<R>, ds: Boolean): Plan {
        val g = game ?: R(areaW * 0.2f, 0f, areaW * 0.8f, areaH)
        val keep = controls.map { it.grown(MARGIN) }
        val leftBar = g.l.coerceAtLeast(0f)
        val rightBar = (areaW - g.r).coerceAtLeast(0f)
        // Each side: the best column at the side's edge or slid in past a control, as wide as the bar or [WANT_W],
        // scored by the room it frees less the game it covers. A DS's right side is its touch screen: no panel there.
        val left = bestColumn(true, leftBar, areaW, areaH, g, keep)
        val right = if (ds) null else bestColumn(false, rightBar, areaW, areaH, g, keep)
        var plan = when {
            left != null && right != null -> Plan(left, right)
            left != null || right != null -> single((left ?: right)!!, keep)
            else -> Plan(null, null)
        }
        // The menu: a corner of the game first (its top left, then its top right), then a corner of the screen, else the
        // top of a panel with room to give it up.
        fun sq(x: Float, y: Float) = R(x, y, x + MENU, y + MENU)
        val corners = listOf(
            sq(g.l + EDGE, g.t + EDGE), sq(g.r - EDGE - MENU, g.t + EDGE), sq(g.l + EDGE, g.b - EDGE - MENU), sq(g.r - EDGE - MENU, g.b - EDGE - MENU),
            sq(EDGE, EDGE), sq(areaW - EDGE - MENU, EDGE), sq(EDGE, areaH - EDGE - MENU), sq(areaW - EDGE - MENU, areaH - EDGE - MENU),
        )
        val taken = listOfNotNull(plan.mine, plan.rest)
        var menu = corners.firstOrNull { c -> keep.none { it.meets(c) } && taken.none { it.meets(c) } && c.l >= 0f && c.r <= areaW && c.t >= 0f && c.b <= areaH }
        if (menu == null) for (which in listOf(plan.rest, plan.mine)) {
            val r = which ?: continue
            if (r.h - MENU - EDGE < MIN_H) continue
            menu = R(r.r - MENU, r.t, r.r, r.t + MENU)
            val cut = R(r.l, r.t + MENU + EDGE, r.r, r.b)
            plan = if (which === plan.rest) plan.copy(rest = cut) else plan.copy(mine = cut)
            break
        }
        return plan.copy(menu = menu)
    }

    private fun bestColumn(isLeft: Boolean, bar: Float, areaW: Float, areaH: Float, g: R, keep: List<R>): R? {
        val w = (bar - 2 * EDGE).coerceIn(WANT_W, MAX_W).coerceAtMost(areaW / 2 - 2 * EDGE)
        val limit = maxOf(areaW * 0.34f, bar)
        var best: R? = null
        var bestScore = 0f
        var x = EDGE
        while (x == EDGE || x + w <= limit + 0.01f) {
            val col = if (isLeft) R(x, 0f, x + w, areaH) else R(areaW - x - w, 0f, areaW - x, areaH)
            val run = freeRuns(col, keep, areaH).firstOrNull()
            if (run != null) {
                val over = (minOf(run.r, g.r) - maxOf(run.l, g.l)).coerceAtLeast(0f)
                val score = run.h * run.w - 0.3f * over * run.h
                if (score > bestScore) { best = run; bestScore = score }
            }
            x += 8f
        }
        return best
    }

    /** Both parts on one side: its two tallest free runs, or its tallest split in two, or everything in one. */
    private fun single(col: R, keep: List<R>): Plan {
        if (col.h >= 2 * MIN_H + EDGE) {
            val mid = col.t + col.h / 2
            return Plan(R(col.l, col.t, col.r, mid - EDGE / 2), R(col.l, mid + EDGE / 2, col.r, col.b))
        }
        return Plan(null, col)
    }

    /** The column's stretches of height that no kept-out control crosses, tallest first, each at least [MIN_H]. */
    fun freeRuns(col: R, keep: List<R>, areaH: Float): List<R> {
        val blocks = keep.filter { it.l < col.r && col.l < it.r }.map { it.t to it.b }.sortedBy { it.first }
        val out = ArrayList<R>()
        var y = EDGE
        for ((t, b) in blocks) {
            if (t - y >= MIN_H) out += R(col.l, y, col.r, t)
            y = maxOf(y, b)
        }
        if (areaH - EDGE - y >= MIN_H) out += R(col.l, y, col.r, areaH - EDGE)
        return out.sortedByDescending { it.h }
    }
}

/** Where [WideCards] hands your card while the HUD shows it in a panel of its own. */
class HudPortal { var left by mutableStateOf<(@Composable () -> Unit)?>(null) }

/** Set inside the HUD while it has a panel for your card; null everywhere else, where the card is drawn in place. */
val LocalHudPortal = compositionLocalOf<HudPortal?> { null }

/** A menu line the floating window or the HUD adds to the tracker's menu: the way to the other of the two. */
class HudMenuItem(val label: String, val action: () -> Unit)
val LocalHudMenu = compositionLocalOf<HudMenuItem?> { null }

/** How solid a panel's glass is at most where it lies over the game. */
private const val OVER_GAME_FADE = 0.6f

/** The least share of its width a panel's cards are drawn at, to fit its height. */
private const val MIN_FIT = 0.45f

/** The HUD itself, in the box the floating window would have used. [menu] is the tracker's menu, handed the way to the dock. */
@Composable
internal fun TrackerHudLayer(menu: @Composable () -> Unit, attempt: Int, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val areaW = maxWidth.value
        val areaH = maxHeight.value
        var origin by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.size(0.dp).onGloballyPositioned { origin = it.positionInWindow() })
        val g = TrackerHud.game?.let {
            HudLayout.R((it.left - origin.x) / density, (it.top - origin.y) / density, (it.right - origin.x) / density, (it.bottom - origin.y) / density)
        }
        val controls = TrackerHud.pad?.let { p ->
            PadGeometry.rects(p, areaW, areaH, landscape = true, skin = TrackerHud.padSkin).values.flatten().map { HudLayout.R(it.l, it.t, it.r, it.b) }
        } ?: emptyList()
        val plan = HudLayout.plan(areaW, areaH, g, controls, TrackerHud.ds)
        val solid = TrackerOptions.floatingSolid
        val fade = FloatingSeeThrough.fade(solid)
        val outline = trackerTextOutline(2.5f * density)
        val accent = TrackerHud.accent(Pc.Border)
        // Fades in once; with the system's animations off, it is simply there.
        val ctx = LocalContext.current
        val reduce = remember { HudMotion.reduced(ctx) }
        var shown by remember { mutableStateOf(reduce) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduce) 0 else 220), label = "hud")
        val portal = remember { HudPortal() }
        val mineSlot = plan.mine.takeIf { TrackerOptions.hudShowMine && plan.rest != null }
        DisposableEffect(Unit) { onDispose { portal.left = null } }
        CompositionLocalProvider(
            LocalWindowFade provides fade, LocalTrackerTextShadow provides outline, LocalBackdropHosted provides true,
            LocalTrackerRoom provides null, LocalAttemptInTitle provides false, LocalTrackerMargin provides HUD_MARGIN,
            LocalHudPortal provides portal.takeIf { mineSlot != null },
        ) {
            plan.rest?.let { r ->
                HudPanel(r, accent, alpha, overGame = g?.meets(r) == true) {
                    if (TrackerOptions.hudShowRest || mineSlot == null) FitWidth { content() }
                }
            }
            mineSlot?.let { r ->
                HudPanel(r, accent, alpha, overGame = g?.meets(r) == true) {
                    FitWidth { PcCanvas(Modifier.fillMaxWidth()) { CompositionLocalProvider(LocalTrackerWide provides false) { portal.left?.invoke() } } }
                }
            }
            // The tracker's menu, the way back to the window or the dock: a dark disc in a corner no control is near.
            plan.menu?.let { m ->
                Box(
                    Modifier.offset { IntOffset((m.l * density).roundToInt(), (m.t * density).roundToInt()) }.size(m.w.dp, m.h.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .drawBehind { drawCircle(Color.Black.copy(alpha = 0.55f)); drawCircle(accent.copy(alpha = 0.8f), style = Stroke(1.dp.toPx())) },
                    contentAlignment = Alignment.Center,
                ) { PcCanvas(Modifier.size(m.w.dp)) { menu() } }
            }
        }
    }
}

private val HUD_MARGIN = androidx.compose.foundation.layout.PaddingValues(2.dp)

/** Whether the system's animations are off (Settings, Accessibility: Remove animations). */
internal object HudMotion {
    fun reduced(context: android.content.Context): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

/**
 * The cockpit frame: smoky glass with a faint gradient, faded by the see-through setting, and a thin glowing edge with
 * cut corners and small brackets. Nothing in it takes a touch, so a tap on its empty glass reaches the game.
 */
@Composable
private fun HudPanel(r: HudLayout.R, accent: Color, alpha: Float, overGame: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    // Over the game's edge the glass is always see-through, at least to the HUD's least.
    val fade = if (overGame) minOf(LocalWindowFade.current, OVER_GAME_FADE) else LocalWindowFade.current
    // No frame round nothing: your panel before you have a Pokemon draws no empty glass over the bar.
    var has by remember { mutableStateOf(false) }
    Box(
        Modifier
            .offset { IntOffset((r.l * density).roundToInt(), (r.t * density).roundToInt()) }
            .size(r.w.dp, r.h.dp)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                if (!has) return@drawBehind
                val cut = 10.dp.toPx()
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(cut, 0f); lineTo(w - cut, 0f); lineTo(w, cut); lineTo(w, h - cut); lineTo(w - cut, h)
                    lineTo(cut, h); lineTo(0f, h - cut); lineTo(0f, cut); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFF08141C).copy(alpha = 0.78f * fade), Color(0xFF08141C).copy(alpha = 0.55f * fade))))
                drawPath(p, accent.copy(alpha = 0.22f), style = Stroke(4.dp.toPx()))
                drawPath(p, accent.copy(alpha = 0.9f), style = Stroke(1.dp.toPx()))
                // Brackets on two corners and a tick at the top's middle.
                val b = 12.dp.toPx()
                val s = 2.dp.toPx()
                drawLine(accent, Offset(cut + 3 * s, 2 * s), Offset(cut + 3 * s + b, 2 * s), s, StrokeCap.Square)
                drawLine(accent, Offset(w - cut - 3 * s, h - 2 * s), Offset(w - cut - 3 * s - b, h - 2 * s), s, StrokeCap.Square)
                drawLine(accent.copy(alpha = 0.6f), Offset(w / 2, 0f), Offset(w / 2, 4.dp.toPx()), s)
            }
            .padding(6.dp)
            .clipToBounds(),
    ) { Box(Modifier.onSizeChanged { has = it.height > 2 }) { content() } }
}

/**
 * The cards at the panel's width, or narrower, so their height fits the panel: the tracker's canvas draws a unit from
 * its width, so a narrower canvas is a smaller card, words and numbers alike, and all four moves stay in view. It tries
 * again whenever the cards change height, so it grows back as well.
 */
@Composable
private fun FitWidth(content: @Composable () -> Unit) {
    var fit by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier.fillMaxWidth().layout { m, c ->
            val full = c.maxWidth
            val w = (full * fit).roundToInt().coerceIn(1, maxOf(1, full))
            val p = m.measure(Constraints(minWidth = w, maxWidth = w))
            if (c.hasBoundedHeight && p.height > 0) {
                val natural = p.height / fit
                val want = (c.maxHeight / natural).coerceIn(MIN_FIT, 1f)
                if (abs(want - fit) > 0.02f) fit = want
            }
            val h = if (c.hasBoundedHeight) minOf(p.height, c.maxHeight) else p.height
            layout(full, h) { p.place((full - w) / 2, 0) }
        },
    ) { content() }
}
