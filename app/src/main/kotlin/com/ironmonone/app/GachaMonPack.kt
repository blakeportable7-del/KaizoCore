package com.ironmonone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.tracker.TrackedMon
import kotlinx.coroutines.launch

/*
 * Opening a GachaMon card pack (AnimationManager.createGachaMonPackOpening and createGachaMonCardDisplay,
 * AnimationManager.lua:226-493): a sealed pack, "tap to open", the pack tearing open with the card rising out of it
 * (at once with "Animate card pack opening" off), then the card until it is tapped away, with NEW on a species not yet
 * collected and, for a prize card, the trainer it came from. The pack is drawn in code in one of ten foils, the
 * reference's ten pack pictures' place.
 */

/** The words a pack and the tracker's GachaMon line use. */
object GachaMonCopy {
    const val CAPTURED = "GachaMon captured!"
    const val NEW = "NEW"
    const val TAP_TO_OPEN = "Tap the pack to open it"
    const val TAP_TO_CLOSE = "Tap to put it away"
    const val PRIZE_FROM = "Prize Card from Trainer:"
    const val PACK = "GachaMon card pack"
    /** The tracker's carousel line, "NEW! GachaMon captured!" for a species not yet collected. */
    fun capturedLine(isNew: Boolean): String = if (isNew) "$NEW! $CAPTURED" else CAPTURED
}

/** The ten foils: two colours each, for the pack's gradient. */
private val PACK_FOILS = listOf(
    Color(0xFFCF3A3F) to Color(0xFF6E1A2B), Color(0xFF3A6FCF) to Color(0xFF1A2B6E), Color(0xFF3FAF6A) to Color(0xFF1A4E33),
    Color(0xFFE0A030) to Color(0xFF7A4A12), Color(0xFF8E4FD1) to Color(0xFF3B1F66), Color(0xFF2FB5C4) to Color(0xFF14525A),
    Color(0xFFE05A9C) to Color(0xFF6B1F47), Color(0xFF7A8794) to Color(0xFF2E353D), Color(0xFFD1683A) to Color(0xFF5E2614),
    Color(0xFF9CC23F) to Color(0xFF45591A),
)

/** A sealed card pack, crimped top and bottom, [foil] one of the ten. */
@Composable
private fun GachaPackArt(foil: Int, modifier: Modifier) {
    val (light, dark) = PACK_FOILS[foil.mod(PACK_FOILS.size)]
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val crimp = h * 0.05f
            val body = Path().apply {
                moveTo(0f, crimp)
                // The top crimp's teeth
                val teeth = 12
                for (i in 0..teeth) lineTo(w * i / teeth, if (i % 2 == 0) crimp else 0f)
                lineTo(w, h - crimp)
                for (i in teeth downTo 0) lineTo(w * i / teeth, if (i % 2 == 0) h - crimp else h)
                close()
            }
            drawPath(body, Brush.linearGradient(listOf(light, dark), start = Offset(0f, 0f), end = Offset(w, h)))
            drawPath(body, Color.White.copy(alpha = 0.35f), style = Stroke(width = w * 0.012f))
            // A band of foil sheen, and the emblem: a card back's star in a ring.
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent),
                start = Offset(w * 0.1f, 0f), end = Offset(w * 0.9f, h)), topLeft = Offset(0f, crimp * 2), size = Size(w, h - crimp * 4))
            drawCircle(Color.White.copy(alpha = 0.9f), w * 0.24f, Offset(w / 2, h * 0.46f), style = Stroke(width = w * 0.03f))
            val star = Path()
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) w * 0.17f else w * 0.075f
                val a = Math.toRadians(-90.0 + k * 36.0)
                val x = w / 2 + (r * Math.cos(a)).toFloat(); val y = h * 0.46f + (r * Math.sin(a)).toFloat()
                if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, Color(0xFFFCED86))
        }
        // The wordmark under the emblem, sized to the pack: the tracker's own pack is a third the size of the window's.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Text("GachaMon", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                fontSize = with(density) { (maxWidth * 0.14f).toSp() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.68f))
        }
    }
}

/** The NEW tab under a card of a species not yet collected (AnimationManager's _drawNewLabel). */
@Composable
private fun GachaNewLabel(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE849A2)).border(1.dp, Color(0xFFFD7DFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(GachaMonCopy.NEW, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

/**
 * The pack, opened over the whole screen: [e] the card inside. A prize card heads with its trainer. [onDone] runs when
 * the card is tapped away; the newest card is then marked seen (clearNewestMonToShow).
 */
@Composable
fun GachaMonPackDialog(e: GachaMonEntry, onDone: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) { GachaMonPackContent(e, onDone = onDone) }
}

/** The pack's page: sealed until tapped ([startOpened] for the look test), then the card until it is tapped away. */
@Composable
fun GachaMonPackContent(e: GachaMonEntry, startOpened: Boolean = false, onDone: () -> Unit) {
    val foil = remember(e.uid) { (e.uid % PACK_FOILS.size).toInt() }
    val isNew = remember(e.uid) { GachaMon.isNewSpecies(e) }
    val animate = GachaMonOptions.animatePack
    var opened by remember { mutableStateOf(startOpened) }
    val open = remember { Animatable(if (startOpened) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var hintShown by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        GachaMon.packShowing = true
        onDispose { GachaMon.packShowing = false }
    }
    // The help line comes after a moment, as the reference's does (210 frames there).
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); hintShown = true }
    fun finish() { GachaMon.newestSeen(); onDone() }
    fun tap() {
        if (!opened) {
            opened = true
            scope.launch { if (animate) open.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) else open.snapTo(1f) }
        } else if (open.value >= 1f) finish()
    }
    // Back puts the card away once it is out; before that it opens the pack (the reference's pack waits to be opened).
    androidx.activity.compose.BackHandler { tap() }
    run {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
                    onClickLabel = if (opened) GachaMonCopy.TAP_TO_CLOSE else GachaMonCopy.TAP_TO_OPEN) { tap() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.notes.trainer.isNotEmpty() || e.trainerName != null) {
                    Text(GachaMonCopy.PRIZE_FROM, color = Color(0xFFFCED86), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(e.notes.trainer.ifBlank { e.trainerName.orEmpty() }, color = Color.White, fontSize = 18.sp)
                    if (e.notes.place.isNotBlank()) Text(e.notes.place, color = Shell.hintOnNight, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                }
                Box(Modifier.size(240.dp, 300.dp), contentAlignment = Alignment.Center) {
                    val t = open.value
                    // The card rises out of the pack as it opens, and settles at full size.
                    if (opened) GachaMonCardFace(e, 220.dp,
                        Modifier.offset(y = (90 * (1f - t)).dp).scale(0.6f + 0.4f * t).alpha((t * 1.6f).coerceAtMost(1f)))
                    // The pack slides down and fades; its torn top flies off.
                    if (t < 1f) {
                        GachaPackArt(foil, Modifier.size(170.dp, 250.dp).offset(y = (160 * t).dp).alpha(1f - t)
                            .semantics { contentDescription = GachaMonCopy.PACK })
                        if (opened) Box(Modifier.size(170.dp, 18.dp).offset(y = (-125 - 120 * t).dp, x = (40 * t).dp)
                            .rotate(25f * t).alpha(1f - t).background(PACK_FOILS[foil].first))
                    }
                }
                if (opened && open.value >= 1f && isNew) {
                    Spacer(Modifier.height(10.dp))
                    GachaNewLabel()
                }
                Spacer(Modifier.height(18.dp))
                if (hintShown || opened) Text(
                    if (!opened) GachaMonCopy.TAP_TO_OPEN else if (open.value >= 1f) GachaMonCopy.TAP_TO_CLOSE else "",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** When GachaMon shows on the tracker, as TrackerScreen.lua and DataHelper.lua decide it. */
object GachaMonShown {
    /**
     * TrackerScreen.CarouselItems[GACHAMON].canShow: its Tracker Setup item on, the pack not shown on the tracker
     * instead, and a new card waiting outside a battle (GachaMonData.hasNewestMonToShow).
     */
    fun carousel(itemOn: Boolean, packOnTracker: Boolean, newestWaiting: Boolean, inBattle: Boolean): Boolean =
        itemOn && !packOnTracker && newestWaiting && !inBattle

    /**
     * TrackerScreen.Buttons.GachaMonStars.isVisible: "Display stars next to heals" on, "Track PC Heals" off (it uses the
     * same place), a card for the Pokemon on view, and no new card waiting outside a battle.
     */
    fun healsStars(starsOn: Boolean, trackPcHeals: Boolean, hasCard: Boolean, newestWaiting: Boolean, inBattle: Boolean): Boolean =
        starsOn && !trackPcHeals && hasCard && !(newestWaiting && !inBattle)

    /** "Show card pack opening before Pokemon stats": the pack in your Pokemon's place while a new card waits outside a battle. */
    fun packOnTracker(packOption: Boolean, newestWaiting: Boolean, inBattle: Boolean): Boolean = packOption && newestWaiting && !inBattle
}

/** Where the tracker opens GachaMon: the screen on a tab (and a card), or a pack. */
@Stable
class GachaMonTrackerUi {
    /** The GachaMon screen, open on this card's View tab, or on its first tab when the card is null. */
    var screen by mutableStateOf<GachaMonStart?>(null)
    var pack by mutableStateOf<GachaMonEntry?>(null)
}

/** How the GachaMon screen opens: [tab], and [card] for the View tab. */
data class GachaMonStart(val tab: GachaMonTab = GachaMonTab.CAPTURES, val card: GachaMonEntry? = null)

@Composable
fun rememberGachaMonUi(): GachaMonTrackerUi = remember { GachaMonTrackerUi() }

/**
 * The tracker's GachaMon windows. Not on a second screen, whose window cannot host one (LocalOnSecondScreen). A prize
 * card's pack, when put away, opens its View tab, as the reference does.
 */
@Composable
fun GachaMonTrackerDialogs(ui: GachaMonTrackerUi) {
    if (LocalOnSecondScreen.current) return
    ui.pack?.let { e ->
        GachaMonPackDialog(e) {
            ui.pack = null
            if (e.uid == GachaMon.prizeCard?.uid) ui.screen = GachaMonStart(GachaMonTab.VIEW, GachaMon.current(e))
        }
    }
    ui.screen?.let { s -> GachaMonScreen(s) { ui.screen = null } }
}

/**
 * The stars in the heals box (TrackerScreen.Buttons.GachaMonStars): the Pokemon on view's card as it would rate now,
 * gains orange and losses hollow against the stars it was made with. Only with "Display stars next to heals" on, "Track
 * PC Heals" off, a card for this Pokemon and no new card waiting outside a battle. A tap opens its card.
 */
@Composable
fun GachaMonHealsStars(p: TrackedMon, inBattle: Boolean, onOpen: (GachaMonEntry) -> Unit) {
    val card = GachaMon.cardFor(p)
    if (card == null || !GachaMonShown.healsStars(GachaMonOptions.showStars, TrackerOptions.trackPcHeals, true, GachaMon.newest != null, inBattle)) return
    val filesDir = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    val (now, made) = GachaMon.viewedStars(p, filesDir) ?: return
    if (maxOf(now, made) < 1) return
    Box(
        Modifier.clickable(role = Role.Button, onClickLabel = "Open its GachaMon card") { onOpen(card) }
            .semantics { contentDescription = "GachaMon card, ${gachaStarsText(now)}" + if (now != made) ", made with ${gachaStarsText(made)}" else "" }
            .padding(horizontal = 2.rp, vertical = 1.rp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        GachaMonStarRow(now, initial = made.takeIf { it > 0 }, height = if (maxOf(now, made) >= 5) 18.rp else 10.rp)
    }
}

/** The carousel's GachaMon line (TrackerScreen.Buttons.GachaMonSummary): a card glyph and "GachaMon captured!". */
@Composable
fun GachaMonCarouselLine(onTap: () -> Unit) {
    val e = GachaMon.newest ?: return
    val isNew = remember(e.uid) { GachaMon.isNewSpecies(e) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TrackerLook.RADIUS.rp))
            .background(trackerBoxFill(Pc.LowerGroundX ?: Pc.Ground))
            .border(1.dp, Pc.LowerBorder.copy(alpha = 0.55f), RoundedCornerShape(TrackerLook.RADIUS.rp))
            .clickable(role = Role.Button, onClickLabel = "Open the card pack") { onTap() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Constants.PixelImages.GACHAMON_CARD, in Intermediate text: a little card with a star.
        Canvas(Modifier.size(9.dp, 12.dp)) {
            drawRoundRect(Pc.Gold, style = Stroke(width = 1.2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()))
            drawCircle(Pc.Gold, size.width * 0.18f, Offset(size.width / 2, size.height * 0.4f))
        }
        Spacer(Modifier.width(6.dp))
        PixText(GachaMonCopy.capturedLine(isNew), 8, Pc.LowerText)
    }
}

/**
 * "Show card pack opening before Pokemon stats": while a new card waits, the pack in place of your Pokemon's card,
 * which comes back once the pack is opened.
 */
@Composable
fun GachaMonPendingPack(onOpen: () -> Unit) {
    val e = GachaMon.newest ?: return
    PcCard {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = GachaMonCopy.TAP_TO_OPEN) { onOpen() }.padding(6.rp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GachaPackArt((e.uid % PACK_FOILS.size).toInt(), Modifier.size(56.rp, 82.rp).semantics { contentDescription = GachaMonCopy.PACK })
            Spacer(Modifier.height(3.rp))
            PixText(GachaMonCopy.TAP_TO_OPEN, PcRef.FONT, Pc.Text)
        }
    }
}
