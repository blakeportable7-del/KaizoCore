package com.ironmonone.app

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Ironmon-Tracker's "Walking Pals" icon set (Options.IconSetMap entry 6, the
 * only animated one): PMD Sprite Collab sprites, four animations a species -
 * idle, walk, sleep, faint - each a sheet of frames left to right and the
 * eight facing directions top to bottom. SpriteData.WalkingPals gives each
 * sheet's frame size, the offset it is drawn at from the icon's corner and
 * each frame's length in game frames; tools/trainer-data/convert_walking_pals.py
 * ran the reference's Lua to write that table (assets/walkingpals/walkingpals.tsv)
 * and copied the sheets beside it.
 *
 * Blake asked for these on 2026-09-15 for Gen 1 to 3. The sheets are keyed by
 * Gen 3 species id (1-411); Gen 1 and 2 report national numbers, which are the
 * same ids up to 251. Sprites: PMD Sprite Collab, CC BY-NC 4.0, credited on
 * the About screen.
 */
object WalkingPals {
    enum class Anim(val key: String) { IDLE("idle"), WALK("walk"), SLEEP("sleep"), FAINT("faint") }

    class Sheet(val w: Int, val h: Int, val x: Int, val y: Int, val durations: IntArray) {
        val total: Int = durations.sum()

        /**
         * SpriteData.createActiveIcon's frameToIndex: which frame (0-based) is
         * showing [frames] game frames after the animation began. Faint does
         * not loop (canLoop): it stops on its last frame.
         */
        fun frameAt(frames: Long, loop: Boolean): Int {
            if (total <= 0 || durations.isEmpty()) return 0
            if (!loop && frames >= total) return durations.lastIndex
            var f = (Math.floorMod(frames, total.toLong())).toInt()
            for (i in durations.indices) {
                if (f < durations[i]) return i
                f -= durations[i]
            }
            return durations.lastIndex
        }
    }

    /** walkingpals.tsv: id, animation, w, h, x, y, durations. */
    fun parse(lines: Sequence<String>): Map<Int, Map<Anim, Sheet>> {
        val out = HashMap<Int, HashMap<Anim, Sheet>>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val c = line.split('\t')
            if (c.size < 7) continue
            val id = c[0].toIntOrNull() ?: continue
            val anim = Anim.entries.firstOrNull { it.key == c[1] } ?: continue
            val durs = c[6].split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            out.getOrPut(id) { HashMap() }[anim] = Sheet(
                c[2].toIntOrNull() ?: 32, c[3].toIntOrNull() ?: 32, c[4].toIntOrNull() ?: 0, c[5].toIntOrNull() ?: 0, durs,
            )
        }
        return out
    }

    @Volatile private var table: Map<Int, Map<Anim, Sheet>>? = null

    private fun table(ctx: android.content.Context): Map<Int, Map<Anim, Sheet>> =
        table ?: synchronized(this) {
            table ?: runCatching {
                ctx.assets.open("walkingpals/walkingpals.tsv").bufferedReader(Charsets.UTF_8).useLines { parse(it) }
            }.getOrDefault(emptyMap()).also { table = it }
        }

    fun sheets(ctx: android.content.Context, species: Int): Map<Anim, Sheet>? = table(ctx)[species]

    private val bitmaps = HashMap<String, ImageBitmap?>()

    fun bitmap(ctx: android.content.Context, anim: Anim, species: Int): ImageBitmap? = synchronized(bitmaps) {
        val key = "${anim.key}/$species"
        if (bitmaps.containsKey(key)) bitmaps[key]
        else runCatching {
            ctx.assets.open("walkingpals/$key.png").use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull().also { bitmaps[key] = it }
    }

    /**
     * Input.getSpriteFacingDirection: the sheet's row (0-based) for the held
     * direction - down, down-right, right, up-right, up, up-left, left,
     * down-left - facing down when nothing is held.
     */
    fun facingRow(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int = when {
        right && down -> 1
        right && up -> 3
        left && up -> 5
        left && down -> 7
        down -> 0
        right -> 2
        up -> 4
        left -> 6
        else -> 0
    }

    /** SpriteData.idleTimeUntilSleep: seconds without input before every sprite falls asleep. */
    const val IDLE_SECONDS_UNTIL_SLEEP = 55
}

/**
 * What the animated icons need to know about the player's hands: the
 * directions held (a sprite walks, facing that way, while one is) and when
 * anything was last pressed (they all fall asleep after 55 seconds), plus
 * whether a battle is up (Battle.inActiveBattle: no walking in battle). Fed
 * from every path that hands a key to the core: the pad, a controller's
 * buttons and its hat.
 */
object SpriteMotion {
    @Volatile var lastInputNanos: Long = System.nanoTime()
    @Volatile var inBattle: Boolean = false
    private val held = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun key(action: Int, keyCode: Int) {
        lastInputNanos = System.nanoTime()
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (action == KeyEvent.ACTION_DOWN) held.add(keyCode) else if (action == KeyEvent.ACTION_UP) held.remove(keyCode)
        }
    }

    fun walking(): Boolean = held.isNotEmpty()

    fun facingRow(): Int = WalkingPals.facingRow(
        KeyEvent.KEYCODE_DPAD_UP in held, KeyEvent.KEYCODE_DPAD_DOWN in held,
        KeyEvent.KEYCODE_DPAD_LEFT in held, KeyEvent.KEYCODE_DPAD_RIGHT in held,
    )
}

/** The animation a shown icon keeps between frames: which one, and the frame it began on. */
private class PalClock { var anim: WalkingPals.Anim? = null; var start: Long = 0 }

/**
 * One animated Walking Pals icon, in place of the 32x32 still (Drawing.drawSpriteIcon).
 * Which animation, as SpriteData decides it: asleep after 55 seconds idle;
 * fainting at 0 HP (once, then still); asleep while the status is SLP; walking,
 * facing the held direction, while a direction is held outside battle; idle
 * otherwise. Frames advance at 60 a second of real time, as the reference
 * scales them to hold that pace under fast-forward. The frame is drawn at the
 * sheet's offset from the icon's corner and may spill past the 32x32 box, as
 * on PC. Only the drawing re-runs each frame; nothing recomposes.
 */
@Composable
fun WalkingPalsIcon(species: Int, status: String, unitDp: androidx.compose.ui.unit.Dp, boxDp: androidx.compose.ui.unit.Dp): Boolean {
    val ctx = LocalContext.current
    val sheets = remember(species) { WalkingPals.sheets(ctx, species) }
    if (sheets == null) return false
    val images = remember(species) { WalkingPals.Anim.entries.associateWith { WalkingPals.bitmap(ctx, it, species) } }
    if (images[WalkingPals.Anim.IDLE] == null) return false
    var tick by remember { mutableLongStateOf(0L) }
    val clock = remember(species) { PalClock() }
    LaunchedEffect(species) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { tick = (it - t0) / 16_666_667L }
    }
    Canvas(Modifier.size(boxDp)) {
        val frames = tick
        val afk = System.nanoTime() - SpriteMotion.lastInputNanos >= WalkingPals.IDLE_SECONDS_UNTIL_SLEEP * 1_000_000_000L
        val walk = TrackerOptions.spritesWalk && !SpriteMotion.inBattle && SpriteMotion.walking()
        var anim = when {
            afk -> WalkingPals.Anim.SLEEP
            status == "FNT" -> WalkingPals.Anim.FAINT
            status == "SLP" -> WalkingPals.Anim.SLEEP
            walk -> WalkingPals.Anim.WALK
            else -> WalkingPals.Anim.IDLE
        }
        if (sheets[anim] == null || images[anim] == null) anim = WalkingPals.Anim.IDLE
        val sheet = sheets[anim] ?: return@Canvas
        val img = images[anim] ?: return@Canvas
        if (clock.anim != anim) { clock.anim = anim; clock.start = frames }
        val index = sheet.frameAt(frames - clock.start, loop = anim != WalkingPals.Anim.FAINT)
        val row = if (anim == WalkingPals.Anim.WALK) SpriteMotion.facingRow() else 0
        val sx = (sheet.w * index).takeIf { it + sheet.w <= img.width } ?: 0
        val sy = (sheet.h * row).takeIf { it + sheet.h <= img.height } ?: 0
        val u = unitDp.toPx()
        drawImage(
            img,
            srcOffset = IntOffset(sx, sy), srcSize = IntSize(sheet.w, sheet.h),
            dstOffset = IntOffset((sheet.x * u).roundToInt(), (sheet.y * u).roundToInt()),
            dstSize = IntSize((sheet.w * u).roundToInt(), (sheet.h * u).roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
    return true
}
