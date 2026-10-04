package com.ironmonone.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * GachaMon's cards, drawn in KaizoCore's look from what GachaMonOverlay.drawGachaCard puts on one (GachaMonOverlay.lua
 * :1930-2082): a two-tone frame in the Pokemon's type colours, stars top left, Battle Power top right, the Pokemon, its
 * ability and name, six stat bars along the foot, a heart for a favorite, a ribbon for a game winner, and a shine for a
 * shiny card. Measured in the reference's 68-unit card, so it scales to any size. The Pokemon is the shipped icon set's
 * picture (PcAssets.gbaSprite), never art bundled from a game.
 */

/** The reference's star colours (Constants.PixelImages.STAR): fill, outline. */
internal object GachaMonStarColors {
    val gold = Color(0xFFFCED86) to Color(0xFFEACA1D)
    val gained = Color(0xFFF99F5E) to Color(0xFFE87320)
    val lost = Color(0xFF877E48) to Color(0xFF847111)
    val trainer = Color(0xFF8BD6F5) to Color(0xFF67B8E5)
    /** The 5+ card's stars: platinum. */
    val platinum = Color(0xFFEEEEEE) to Color(0xFFCCCCCC)
}

/** The words a card's stars are read out with: "5 stars", "5+ stars". */
fun gachaStarsText(stars: Int): String = if (stars > 5) "5+ stars" else if (stars == 1) "1 star" else "$stars stars"

/** A five-pointed star path in a [size] square at [at]. */
private fun starPath(at: Offset, size: Float): Path = Path().apply {
    val cx = at.x + size / 2; val cy = at.y + size / 2
    val outer = size / 2; val inner = outer * 0.45f
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = Math.toRadians(-90.0 + k * 36.0)
        val x = cx + (r * Math.cos(a)).toFloat(); val y = cy + (r * Math.sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * GachaMonOverlay.drawStarsOfGachaMon: [stars] stars (6 is the 5+ card, five platinum stars), a five split three over
 * two. With [initial], the stars it was made with: stars gained since are orange and stars lost are hollow, as the
 * tracker's heals box shows a Pokemon's card against how it is now.
 */
internal fun DrawScope.drawGachaStars(stars: Int, origin: Offset, unit: Float, initial: Int? = null, trainer: Boolean = false) {
    if (stars < 1) return
    val start = initial?.takeIf { it >= 1 }
    var count = maxOf(start ?: 0, stars)
    val twoLines = count >= 5
    var base = if (trainer) GachaMonStarColors.trainer else GachaMonStarColors.gold
    if (count > 5) { base = GachaMonStarColors.platinum; count = 5 }
    val icon = (if (count == 5) 10f else 9f) * unit
    val x0 = origin.x + (if (twoLines) 3f * unit else 0f)
    for (i in 1..count) {
        var x = x0 + 1f * unit + icon * (i - 1)
        var y = origin.y + 1f * unit
        val colors = when {
            start != null && i > start && i <= stars -> GachaMonStarColors.gained
            start != null && i > stars && i <= start -> GachaMonStarColors.lost
            else -> base
        }
        if (i >= 4 && twoLines) { x = x + 5f * unit - 3 * icon; y = y + icon - 4f * unit }
        val p = starPath(Offset(x, y), icon - unit)
        drawPath(p, colors.first)
        drawPath(p, colors.second, style = Stroke(width = unit * 0.9f))
    }
}

/** A stand-alone row of stars, for the tracker's heals box and the View tab. */
@Composable
fun GachaMonStarRow(stars: Int, initial: Int? = null, height: Dp, modifier: Modifier = Modifier) {
    val shown = maxOf(stars, initial ?: 0).coerceAtMost(5)
    val twoLines = maxOf(stars, initial ?: 0) >= 5
    // In the stars' own 9-unit grid: four across in one line, or a five as three over two.
    val unitsWide = if (twoLines) 3 + 3 * 10 + 2 else 1 + 9 * maxOf(shown, 1)
    val unitsTall = if (twoLines) 1 + 10 + 10 - 4 + 1 else 11
    val unit = height / unitsTall
    Canvas(modifier.size(unit * unitsWide, height).semantics { contentDescription = gachaStarsText(stars) }) {
        drawGachaStars(stars, Offset.Zero, unit.toPx(), initial)
    }
}

/** IGachaMon.getCardDisplayData's StatBars: each stat over the level, capped at 5 (HP less 10, tiny stats none). */
fun gachaStatBars(e: GachaMonEntry): List<Int> {
    val c = e.card
    val s = c.stats
    return listOf(s.hp - 10, s.atk, s.def, s.spa, s.spd, s.spe).map { v ->
        val value = if (v <= 6) 0 else v
        if (c.level <= 0) 0 else minOf(value / c.level, 5)
    }
}

/** Gym badge art set for a card's game: Ruby, Sapphire and Emerald share theirs, as do FireRed and LeafGreen. */
fun gachaBadgeSet(gameVersion: Int): String = if (gameVersion == 3 || gameVersion == 5) "FRLG" else "RSE"

/** The shipped icon set's picture for a card's Pokemon (the pack the PC trackers draw); null where there is none. */
@Composable
internal fun gachaSprite(e: GachaMonEntry): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    return remember(e.card.pokemonId, e.notes.dex) { PcAssets.gbaSprite(ctx, e.card.pokemonId, if (e.notes.dex == "maxdex") "maxdex" else null) }
}

/**
 * One card, [width] square. [heart]: true draws the favorite heart whether or not it is a favorite (the Captures tab's
 * empty heart), false only for a favorite; [collected] draws the in-collection mark (a check, or a cross for a capture
 * not kept), as the Captures tab does.
 */
@Composable
fun GachaMonCardFace(e: GachaMonEntry, width: Dp, modifier: Modifier = Modifier, heart: Boolean = false, collected: Boolean = false) {
    val c = e.card
    val t1 = pcTypeColor(c.type1)
    val t2 = if (c.type2 != c.type1) pcTypeColor(c.type2) else t1
    val stars = e.stars
    val trainer = e.trainerName != null
    val bars = remember(e.card) { gachaStatBars(e) }
    val density = LocalDensity.current
    val unitDp = width / 68f
    fun units(n: Float) = with(density) { (unitDp * n).toSp() }
    val sprite = gachaSprite(e)
    val spoken = "${e.trainerName?.let { "$it's " } ?: ""}${e.speciesName}, ${gachaStarsText(stars)}, ${c.battlePower} Battle Power" +
        (if (c.isShiny == 1) ", shiny" else "") + (if (c.favorite == 1) ", favorite" else "")
    Box(
        modifier.size(width).clip(RoundedCornerShape(unitDp * 4f)).background(Color.Black)
            .semantics { contentDescription = spoken }
            .drawBehind { drawCardFrame(size.width / 68f, t1, t2, bars, stars, trainer) },
    ) {
        // Battle Power, top right on black, right-aligned in the frame's tab.
        Text(
            c.battlePower.toString(), color = Color.White, fontSize = units(9f), fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = unitDp * 3f, top = unitDp * 0.5f), maxLines = 1,
        )
        // The Pokemon, 32 units square at the card's centre line, 8 units down: pixel art, scaled without smoothing.
        if (sprite != null) Image(
            sprite, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = unitDp * 8f).size(unitDp * 32f),
        )
        if (c.gameWinner == 1) GachaWinnerRibbon(Modifier.offset(x = unitDp * 4f, y = unitDp * 19f).size(unitDp * 11f, unitDp * 16f))
        if (c.favorite == 1 || heart) GachaHeart(c.favorite == 1, t2, Modifier.align(Alignment.TopEnd).offset(x = -unitDp * 6f, y = unitDp * 14f).size(unitDp * 9f))
        if (collected) GachaCollectedMark(c.keep == 1, Modifier.align(Alignment.TopEnd).offset(x = -unitDp * 6f, y = unitDp * 26f).size(unitDp * 9f))
        // Ability, 27 units from the foot, in the card's yellow; the name in the band at the foot, in white.
        Text(
            e.abilityName, color = Color(0xFFFCED86), fontSize = units(7.5f), textAlign = TextAlign.Center, maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = unitDp * 16.5f).padding(horizontal = unitDp * 2f),
        )
        Text(
            e.speciesName, color = Color.White, fontSize = units(8.5f), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = unitDp * 3.5f).padding(horizontal = unitDp * 2f),
        )
        if (c.isShiny == 1) GachaShine(Modifier.size(width))
    }
}

/** The card's ground, frame, stat bars and stars, in the reference's 68-unit layout ([u] pixels a unit). */
private fun DrawScope.drawCardFrame(u: Float, t1: Color, t2: Color, bars: List<Int>, stars: Int, trainer: Boolean) {
    val w = 68f * u; val h = 68f * u
    val topW = 40f * u; val topH = 10f * u; val botH = 15f * u
    val half = w / 2
    // The two halves of the ground, faintly in each type's colour, and the name band a little stronger.
    drawRect(t1.copy(alpha = 0.19f), Offset(u, u), Size(half - u, h - 2 * u - botH))
    drawRect(t2.copy(alpha = 0.19f), Offset(half + u, topH + u), Size(half - 2 * u, h - topH - 2 * u - botH))
    drawRect(t1.copy(alpha = 0.27f), Offset(u, h - botH + u), Size(half - u, botH - 2 * u))
    drawRect(t2.copy(alpha = 0.27f), Offset(half + u, h - botH + u), Size(half - 2 * u, botH - 2 * u))
    // The frame: up the left, across the top to the tab's slant, down to the right side's lower top edge, round the foot.
    val frame = Path().apply {
        moveTo(u, h - u); lineTo(u, u); lineTo(topW, u); lineTo(topW + 4f * u, u + topH); lineTo(w - u, u + topH)
        lineTo(w - u, h - u); close()
    }
    val stroke = Stroke(width = 1.2f * u)
    clipRect(0f, 0f, half, h) { drawPath(frame, t1, style = stroke) }
    clipRect(half, 0f, w, h) { drawPath(frame, t2, style = stroke) }
    // The name band's top edge.
    val bot = h - botH
    drawLine(t1, Offset(u, bot), Offset(half, bot), strokeWidth = u)
    drawLine(t2, Offset(half, bot), Offset(w - u, bot), strokeWidth = u)
    // Six stat bars, HP first, each up to 5 units in from each side.
    bars.forEachIndexed { i, len ->
        if (len <= 0) return@forEachIndexed
        val y = bot + 2f * u * (i + 1)
        drawLine(t1, Offset(u, y), Offset(u + len * u, y), strokeWidth = u * 0.8f)
        drawLine(t2, Offset(w - u - len * u, y), Offset(w - u, y), strokeWidth = u * 0.8f)
    }
    drawGachaStars(stars, Offset(0f, 0f), u, trainer = trainer)
}

/** The favorite heart: filled red, or an outline in the card's colour. */
@Composable
private fun GachaHeart(filled: Boolean, outline: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val hh = size.height
        val p = Path().apply {
            moveTo(w / 2, hh * 0.92f)
            cubicTo(w * -0.1f, hh * 0.5f, w * 0.15f, hh * -0.15f, w / 2, hh * 0.28f)
            cubicTo(w * 0.85f, hh * -0.15f, w * 1.1f, hh * 0.5f, w / 2, hh * 0.92f)
            close()
        }
        if (filled) { drawPath(p, Color(0xFFF04037)); drawPath(p, Color(0xFFFF0000), style = Stroke(width = w * 0.08f)) }
        else drawPath(p, outline, style = Stroke(width = w * 0.12f))
    }
}

/** In the collection: a green check; a capture not kept: a white cross. */
@Composable
private fun GachaCollectedMark(kept: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val hh = size.height
        if (kept) {
            val p = Path().apply { moveTo(w * 0.1f, hh * 0.55f); lineTo(w * 0.4f, hh * 0.85f); lineTo(w * 0.92f, hh * 0.15f) }
            drawPath(p, Color(0xFF4CC38A), style = Stroke(width = w * 0.2f))
        } else {
            drawLine(Color.White, Offset(w * 0.15f, hh * 0.15f), Offset(w * 0.85f, hh * 0.85f), strokeWidth = w * 0.16f)
            drawLine(Color.White, Offset(w * 0.85f, hh * 0.15f), Offset(w * 0.15f, hh * 0.85f), strokeWidth = w * 0.16f)
        }
    }
}

/** A game winner's ribbon: a gold rosette with two tails, drawn here rather than taken from the tracker's image. */
@Composable
private fun GachaWinnerRibbon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val hh = size.height
        val r = w * 0.45f
        val tail = Path().apply {
            moveTo(w * 0.25f, hh * 0.45f); lineTo(w * 0.05f, hh); lineTo(w * 0.35f, hh * 0.88f); lineTo(w * 0.5f, hh)
            lineTo(w * 0.65f, hh * 0.88f); lineTo(w * 0.95f, hh); lineTo(w * 0.75f, hh * 0.45f); close()
        }
        drawPath(tail, Color(0xFFCF3A3F))
        drawCircle(Color(0xFFEACA1D), r, Offset(w / 2, r))
        drawCircle(Color(0xFFFCED86), r * 0.6f, Offset(w / 2, r))
    }
}

/**
 * A shiny card's shine: a band of light that sweeps across it and four twinkles (AnimationManager's sparkles, drawn in
 * code). Light only: the card underneath reads the same with motion off.
 */
@Composable
private fun GachaShine(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shine")
    val sweep by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle")
    Canvas(modifier) {
        val w = size.width; val hh = size.height
        val x = sweep * w
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                start = Offset(x - w * 0.25f, 0f), end = Offset(x + w * 0.25f, hh),
            ),
        )
        val spots = listOf(0.18f to 0.22f, 0.8f to 0.36f, 0.3f to 0.66f, 0.72f to 0.12f)
        spots.forEachIndexed { i, (sx, sy) ->
            val a = if (i % 2 == 0) twinkle else 1f - twinkle
            val c = Offset(sx * w, sy * hh); val r = w * 0.035f
            drawLine(Color.White.copy(alpha = a), Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = w * 0.012f)
            drawLine(Color.White.copy(alpha = a), Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = w * 0.012f)
        }
    }
}

/**
 * GachaMonOverlay.drawMiniGachaCard: one GachaDex square. Collected, its frame and a Poke Ball; seen, the Pokemon
 * dimmed with a white ball; neither, its number on black (unless [reveal] shows its picture).
 */
@Composable
fun GachaMiniCard(species: Int, type1: Int?, type2: Int?, seen: Boolean, collected: Boolean, reveal: Boolean, width: Dp, dex: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val sprite = if (seen || collected || reveal) remember(species, dex) { PcAssets.gbaSprite(ctx, species, if (dex == "maxdex") "maxdex" else null) } else null
    val c1 = type1?.let { pcTypeColor(it) } ?: pcTypeColor(-1)
    val c2 = type2?.let { pcTypeColor(it) } ?: c1
    Box(
        modifier.size(width).clip(RoundedCornerShape(width * 0.08f)).background(Color.Black)
            .drawBehind {
                if (!seen || collected) {
                    val u = size.width / 34f
                    val half = size.width / 2
                    val frame = Path().apply {
                        moveTo(u, size.height - u); lineTo(u, u); lineTo(23f * u, u); lineTo(25f * u, 6f * u)
                        lineTo(size.width - u, 6f * u); lineTo(size.width - u, size.height - u); close()
                    }
                    clipRect(0f, 0f, half, size.height) { drawPath(frame, c1, style = Stroke(width = u)) }
                    clipRect(half, 0f, size.width, size.height) { drawPath(frame, c2, style = Stroke(width = u)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) Image(
            sprite, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            modifier = Modifier.size(width * 0.94f).then(if (seen && !collected) Modifier.background(Color.Transparent) else Modifier),
            alpha = if (seen && !collected) 0.45f else 1f,
        )
        else Text("$species", color = Color.White, fontSize = with(LocalDensity.current) { (width * 0.28f).toSp() })
        if (collected || seen) Canvas(Modifier.align(Alignment.TopEnd).padding(width * 0.04f).size(width * 0.24f)) {
            val r = size.width / 2
            if (collected) {
                drawCircle(Color.White, r)
                drawArc(Color(0xFFCF3A3F), 180f, 180f, true)
                drawLine(Color.Black, Offset(0f, r), Offset(size.width, r), strokeWidth = r * 0.25f)
                drawCircle(Color.Black, r * 0.38f, Offset(r, r))
                drawCircle(Color.White, r * 0.22f, Offset(r, r))
            } else drawCircle(Color.White, r * 0.8f, style = Stroke(width = r * 0.3f))
        }
    }
}
