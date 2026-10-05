package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

/**
 * The floating window's one-row top (Blake, 2026-10-04: the window's top "less bulky"). The window bar used to sit over
 * the panel's own first row, the battle banner (TRAINER BATTLE, SEE MINE, the gear) or the bar with the area and the
 * gear. In the window that row is not drawn: it hands what it holds to the window bar ([publishToWindowBar]), which
 * draws it in one row with the lock: one text slot, the swap as an icon, the gear and the menu. Docked, in portrait, on
 * the second display and in the HUD nothing provides [LocalWindowBar], and the panels draw their rows as before.
 */
class WindowBarSlot {
    var parts by mutableStateOf<WindowBarParts?>(null)
        private set
    private var owner: Any? = null
    fun set(by: Any, p: WindowBarParts) { owner = by; parts = p }
    fun clear(by: Any) { if (owner === by) { owner = null; parts = null } }
}

val LocalWindowBar = compositionLocalOf<WindowBarSlot?> { null }

/** One piece of the text slot; a higher [drop] is dropped first where there is no room and no motion. */
data class BarSegment(val text: String, val color: Color, val drop: Int)

/** The swap between your Pokemon and the opponent's: its short words (shown on a long press), its spoken words, its tap. */
class SwapAction(val label: String, val spoken: String, val onClick: () -> Unit)

class WindowBarParts(
    val segments: List<BarSegment>,
    /** A tap on the text: the trainer's info in a trainer battle, as the banner's own tap. */
    val onTextTap: (() -> Unit)?,
    val tapLabel: String?,
    val swap: SwapAction?,
    val onGear: (() -> Unit)?,
    /** Anything else the row carried (the repel bar), drawn before the gear. */
    val extra: (@Composable () -> Unit)? = null,
)

/**
 * Whether the bar's words get a row of their own. One row holds the lock, the grip, the buttons and the menu, each in a
 * [PcMin.TOUCH_DP] box; where that leaves the words less than [MIN_TEXT] (a narrow window), the words and the gear go on
 * a second row under them, so no button is ever cut off and the trainer tap keeps a place.
 */
object WindowBarFit {
    const val MIN_TEXT = 64f
    const val GRIP = 12f
    const val PAD = 8f
    /** [buttons]: the swap, the gear and the menu that are there, the lock not counted. */
    fun twoRows(windowW: Float, buttons: Int, grip: Boolean): Boolean =
        windowW - PcMin.TOUCH_DP * (1 + buttons) - (if (grip) GRIP else 0f) - PAD < MIN_TEXT
}

/** The text slot's words: what, in what order and colour, and what goes first when there is no room. */
object WindowBarText {
    const val SEP = " · "
    // What goes last: the weather, then the side, then the attempt; the title or the area stays.
    private const val KEEP = 0
    private const val ATTEMPT = 1
    private const val SIDE = 2
    private const val WEATHER = 3

    fun attempt(n: Int): BarSegment = BarSegment("ATTEMPT $n", Pc.Text, ATTEMPT)

    /** In a battle: TRAINER BATTLE (the theme's negative colour) or WILD BATTLE (its positive), the side, the weather. */
    fun battle(isWild: Boolean, side: String?, weather: String?): List<BarSegment> = listOfNotNull(
        BarSegment(if (isWild) "WILD BATTLE" else "TRAINER BATTLE", if (isWild) Pc.Positive else Pc.Negative, KEEP),
        side?.takeIf { it.isNotBlank() }?.let { BarSegment(it, Pc.Text, SIDE) },
        weather?.let { TrackerWeather.name(it) }?.let { BarSegment(it, Pc.Dim, WEATHER) },
    )

    /** Out of a battle: the area's name. */
    fun overworld(area: String?): List<BarSegment> =
        listOfNotNull(area?.takeIf { it.isNotBlank() }?.let { BarSegment(it, Pc.Dim, KEEP) })

    /** The window's attempt first, then the row's words. */
    fun withAttempt(attempt: Int?, segs: List<BarSegment>): List<BarSegment> =
        listOfNotNull(attempt?.let(::attempt)) + segs

    fun plain(segs: List<BarSegment>): String = segs.joinToString(SEP) { it.text }

    /** Read in full by a screen reader, whatever is drawn. */
    fun spoken(segs: List<BarSegment>): String = segs.joinToString(", ") { it.text.lowercase().replaceFirstChar { c -> c.uppercase() } }

    /** With no motion: drop the parts that go first until the rest [fits], never the last one; the order is kept. */
    fun keep(segs: List<BarSegment>, fits: (List<BarSegment>) -> Boolean): List<BarSegment> {
        var shown = segs
        while (shown.size > 1 && !fits(shown)) {
            val worst = shown.maxOf { it.drop }
            shown = shown.toMutableList().also { l -> l.removeAt(l.indexOfLast { it.drop == worst }) }
        }
        return shown
    }

    fun styled(segs: List<BarSegment>): AnnotatedString = buildAnnotatedString {
        segs.forEachIndexed { i, s ->
            if (i > 0) withStyle(SpanStyle(color = Pc.Dim)) { append(SEP) }
            withStyle(SpanStyle(color = s.color)) { append(s.text) }
        }
    }

    /** The marquee's speed: brisk, about twice the default. */
    const val MARQUEE_DP_PER_S = 60
}

/**
 * In the floating window, hands [parts] to its bar and returns true: the caller draws nothing of its own. Anywhere else it
 * returns false and the caller draws its row as before.
 */
@Composable
internal fun publishToWindowBar(parts: WindowBarParts): Boolean {
    val slot = LocalWindowBar.current ?: return false
    val token = remember { Any() }
    SideEffect { slot.set(token, parts) }
    DisposableEffect(slot) { onDispose { slot.clear(token) } }
    return true
}

/**
 * The text slot: what fits stays still; what does not scrolls as a quick marquee, like the area's name. With the phone's
 * animations off it stays still, drops the parts that go first and ends with an ellipsis. A screen reader reads it all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WindowBarTextSlot(segs: List<BarSegment>, onTap: (() -> Unit)?, tapLabel: String?, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = remember(context) { !TrackerMotion.animationsOn(context) }
    val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, shadow = LocalTrackerTextShadow.current)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .then(if (onTap != null) Modifier.clickable(onClickLabel = tapLabel, role = Role.Button) { onTap() } else Modifier)
            .sizeIn(minHeight = PcMin.TOUCH_DP.dp)
            .semantics { contentDescription = WindowBarText.spoken(segs) },
        contentAlignment = Alignment.CenterStart,
    ) {
        val room = constraints.maxWidth
        val shown = if (still) WindowBarText.keep(segs) { measurer.measure(WindowBarText.styled(it), style, maxLines = 1).size.width <= room } else segs
        Text(
            WindowBarText.styled(shown), style = style, maxLines = 1, softWrap = false,
            overflow = if (still) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.clearAndSetSemantics { }.then(
                if (still) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = WindowBarText.MARQUEE_DP_PER_S.dp),
            ),
        )
    }
}

/**
 * SEE MINE / SEE FOE as an icon: two arrows passing, in a [PcMin.TOUCH_DP] touch box. A tap swaps as the button did; a
 * long press shows its words for a moment; a screen reader hears the same sentence as before.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwapIconButton(s: SwapAction) {
    var showWords by remember { mutableStateOf(false) }
    val above = with(androidx.compose.ui.platform.LocalDensity.current) { (PcMin.TOUCH_DP + 4).dp.roundToPx() }
    LaunchedEffect(showWords) { if (showWords) { kotlinx.coroutines.delay(1500); showWords = false } }
    val color = Pc.Text
    Box(
        Modifier.size(PcMin.TOUCH_DP.dp)
            .combinedClickable(role = Role.Button, onLongClickLabel = s.label, onLongClick = { showWords = true }, onClick = s.onClick)
            .semantics { contentDescription = s.spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clip(CircleShape).background(TrackerLook.inset).padding(6.dp)) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height
                val st = 2.dp.toPx()
                val head = w * 0.22f
                // Right along the top, left along the bottom.
                drawLine(color, Offset(w * 0.12f, h * 0.32f), Offset(w * 0.88f, h * 0.32f), st, StrokeCap.Round)
                drawLine(color, Offset(w * 0.88f, h * 0.32f), Offset(w * 0.88f - head, h * 0.32f - head), st, StrokeCap.Round)
                drawLine(color, Offset(w * 0.88f, h * 0.32f), Offset(w * 0.88f - head, h * 0.32f + head), st, StrokeCap.Round)
                drawLine(color, Offset(w * 0.88f, h * 0.68f), Offset(w * 0.12f, h * 0.68f), st, StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.68f), Offset(w * 0.12f + head, h * 0.68f - head), st, StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.68f), Offset(w * 0.12f + head, h * 0.68f + head), st, StrokeCap.Round)
            }
        }
        if (showWords) Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -above)) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(s.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
