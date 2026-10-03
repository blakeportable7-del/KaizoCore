package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The log viewer's pictures (rc34, Blake: "Does the pc tracker log have more images?"): the PC tracker's Pokemon icons
 * that stand idle, the trainers' portraits and the tabs' small pictures. Nothing here is game art shipped with the app:
 * the idle icons are the Walking Pals the tracker already ships, the portraits and the player's head are read out of the
 * player's own ROM (TrainerPictures), and the map, the TM and the PC are drawn here.
 */
internal object LogPictures {
    /** LogTabPokemon.TabIcons: Nidoran (both), Pidgey, Spearow, Jigglypuff, Clefairy, Omanyte and Kabuto, by Gen 3 id. */
    val TAB_POKEMON = listOf(32, 29, 16, 21, 39, 35, 138, 140)

    /** LogTabPokemon.defaultIconCount: the tab shows two of them, picked at random each time the log opens. */
    const val TAB_POKEMON_SHOWN = 2

    /**
     * LogTabTrainers.getTabIcons: the girl's head on an even number, the boy's on an odd one. The reference counts its
     * runs; the app takes the last digit of the run's seed, as random a pick.
     */
    fun girlFor(seed: String): Boolean = (seed.lastOrNull()?.digitToIntOrNull() ?: 1) % 2 == 0

    /**
     * Where a frame [w] x [h] at ([x], [y]) from the corner of the reference's 32x32 icon box lands in a square [box]
     * wide: the box and the frame together, fitted in. A frame runs past the box on PC (a tall one by 24 pixels), which
     * here would draw over the name under it. Left, top, width, height.
     */
    fun fit(w: Int, h: Int, x: Int, y: Int, box: Float): FloatArray {
        val left = minOf(0, x); val top = minOf(0, y)
        val span = maxOf(maxOf(32, x + w) - left, maxOf(32, y + h) - top).toFloat()
        val u = box / span
        val padX = (box - (maxOf(32, x + w) - left) * u) / 2f
        val padY = box - (maxOf(32, y + h) - top) * u
        return floatArrayOf(padX + (x - left) * u, padY + (y - top) * u, w * u, h * u)
    }
}

/** 64x64 or any ARGB pixels as a picture (the tracker's ROM pictures). */
internal fun argbImage(px: IntArray, w: Int, h: Int): ImageBitmap =
    android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()

/**
 * A Pokemon's icon in the log, the reference's POKEMON_ICON button with SpriteData.Types.Idle: the Walking Pals idle
 * animation where [pal] finds one (null when there is none, or Animated sprites is off), else the still picture.
 */
@Composable
internal fun LogMonIcon(still: ImageBitmap?, pal: WalkingPals.Pal?, size: Dp, description: String?) {
    Box(Modifier.size(size)) {
        if (pal == null || !LogIdleIcon(pal, size)) {
            if (still != null) Image(still, description, Modifier.size(size), filterQuality = FilterQuality.None)
        }
    }
}

/**
 * One Walking Pals sheet's idle loop, facing down, [box] square ([LogPictures.fit]); its first frame alone when not
 * [animate]. Only the idle sheet is decoded: the log never walks, sleeps or faints them. False until the sheet is in.
 */
@Composable
internal fun LogIdleIcon(pal: WalkingPals.Pal, box: Dp, animate: Boolean = true): Boolean {
    val ctx = LocalContext.current
    val sheet = WalkingPals.ready(ctx).let { ix -> remember(ix, pal) { ix?.sheets(pal)?.get(WalkingPals.Anim.IDLE) } } ?: return false
    val image by produceState<ImageBitmap?>(null, pal) { value = withContext(Dispatchers.IO) { WalkingPals.bitmap(ctx, WalkingPals.Anim.IDLE, pal) } }
    val img = image ?: return false
    var index by remember(pal) { mutableIntStateOf(0) }
    if (animate) LaunchedEffect(pal) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { nanos ->
            val i = sheet.frameAt((nanos - t0) / 16_666_667L, loop = true)
            if (i != index) index = i
        }
    }
    Canvas(Modifier.size(box)) {
        val cell = sheet.cell(0, index, img.width, img.height) ?: return@Canvas
        val (dx, dy, dw, dh) = LogPictures.fit(sheet.w, sheet.h, sheet.x, sheet.y, size.width).toList()
        drawImage(
            img, srcOffset = IntOffset(cell[0], cell[1]), srcSize = IntSize(sheet.w, sheet.h),
            dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
    return true
}

/**
 * The small picture on a log tab (each LogTab*.lua's TabIcons): two little Pokemon on POKEMON ([pals]), the player's
 * head on TRAINERS ([head], from the ROM), and drawn ones for the rest: a map for ROUTES, a TM disc for TMS, a PC for
 * MISC. [color] is the tab's text color.
 */
@Composable
internal fun LogTabIcon(tab: LogTab, pals: List<WalkingPals.Pal>, head: ImageBitmap?, color: Color) {
    val s = 18.dp
    when (tab) {
        LogTab.POKEMON -> Row {
            pals.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.width(2.dp))
                Box(Modifier.size(s)) { LogIdleIcon(p, s, animate = false) }
            }
        }
        LogTab.TRAINERS -> if (head != null) Image(head, null, Modifier.size(s), filterQuality = FilterQuality.None) else Spacer(Modifier.size(s))
        LogTab.ROUTES -> Canvas(Modifier.size(s)) {
            // A folded map: three panels, the middle one shaded, a route across them.
            val w = size.width; val h = size.height; val sw = w * 0.08f
            val top = h * 0.18f; val bottom = h * 0.82f
            drawRect(color.copy(alpha = 0.25f), Offset(w * 0.37f, top), Size(w * 0.26f, bottom - top))
            drawRect(color, Offset(w * 0.08f, top), Size(w * 0.84f, bottom - top), style = Stroke(sw))
            drawLine(color, Offset(w * 0.37f, top), Offset(w * 0.37f, bottom), sw)
            drawLine(color, Offset(w * 0.63f, top), Offset(w * 0.63f, bottom), sw)
            drawLine(Color(0xFFE03C3C), Offset(w * 0.18f, h * 0.68f), Offset(w * 0.5f, h * 0.4f), sw * 1.2f)
            drawLine(Color(0xFFE03C3C), Offset(w * 0.5f, h * 0.4f), Offset(w * 0.82f, h * 0.58f), sw * 1.2f)
        }
        LogTab.TMS -> Canvas(Modifier.size(s)) {
            // A disc, its label ring and hole.
            val r = size.minDimension * 0.44f
            drawCircle(color, r)
            drawCircle(Color(0xFF5878C8), r * 0.62f)
            drawCircle(Color.Black, r * 0.18f)
        }
        LogTab.MISC -> Canvas(Modifier.size(s)) {
            // A PC: a screen on a stand.
            val w = size.width; val h = size.height; val sw = w * 0.08f
            drawRect(Color(0xFF5878C8), Offset(w * 0.16f, h * 0.16f), Size(w * 0.68f, h * 0.48f))
            drawRect(color, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.56f), style = Stroke(sw))
            drawLine(color, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.5f, h * 0.82f), sw)
            drawLine(color, Offset(w * 0.28f, h * 0.86f), Offset(w * 0.72f, h * 0.86f), sw * 1.2f)
        }
    }
}
