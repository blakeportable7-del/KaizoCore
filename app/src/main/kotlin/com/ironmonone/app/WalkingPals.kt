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
 * Blake asked for these on 2026-09-15 for Gen 1 to 3, and on 2026-09-30 for
 * the rest ("play as Gen 4+ Pokemon, and show Gen 4+ animations on the
 * trackers"). Two sets ship, keyed differently (see [Pack]): walkingpals/ by
 * Gen 3 species id (1-411), walkingpals-nat/ by national number or
 * "<national>-<form>" (convert_walking_pals_nat.py). Callers number species
 * three ways ([Dex]) and [Index.find] takes any of them. Sprites: PMD Sprite
 * Collab, CC BY-NC 4.0, credited on the About screen.
 */
object WalkingPals {
    enum class Anim(val key: String) { IDLE("idle"), WALK("walk"), SLEEP("sleep"), FAINT("faint") }

    /** The last Gen 3 species id: 1-251 national, 252-276 unused, 277-411 Hoenn in the game's own order. */
    const val GEN3_LAST = 411

    /** The two sets in the app's assets, each a folder with its table and a folder of sheets per animation. */
    enum class Pack(val dir: String, val table: String) {
        /** Ironmon-Tracker's Gen 1-3 set, keyed by Gen 3 species id ("277" is Treecko). */
        GEN3("walkingpals", "walkingpals.tsv"),
        /** Gen 4-9 and the forms, keyed by national number or "<national>-<form>" ("387" is Turtwig, "6-mega-x"). */
        NATIONAL("walkingpals-nat", "walkingpals-nat.tsv"),
    }

    /** Where one Pokemon's sheets are: a set, and its key in that set's table. */
    data class Pal(val pack: Pack, val key: String) {
        fun path(anim: Anim): String = "${pack.dir}/${anim.key}/$key.png"
    }

    /** How a caller numbers its species. */
    enum class Dex {
        /** Gen 3's own ids (a vanilla GBA game): 1-251 national, Hoenn at 277-411. */
        GEN3,
        /** The Nat. Dex Extension's (a Nat. Dex build): 1-411 as Gen 3's, then 412-1283 through natdex-map.tsv. */
        NAT_DEX,
        /** National dex numbers (a DS game, Red to Crystal): 1-251 as Gen 3's, 252-386 Hoenn, 387 on the second set. */
        NATIONAL,
    }

    /**
     * Both sets and the Nat. Dex map, and the one lookup every caller uses. Plain maps, no Android, so the tests load
     * it from the files as shipped. [internalOf] turns a Hoenn national number (252-386) into Gen 3's id for it.
     */
    class Index(
        private val gen3: Map<String, Map<Anim, Sheet>>,
        private val national: Map<String, Map<Anim, Sheet>>,
        private val natDex: Map<Int, Pal?>,
        private val internalOf: (Int) -> Int? = Favorites::fromNational,
    ) {
        fun sheets(pal: Pal): Map<Anim, Sheet>? = (if (pal.pack == Pack.GEN3) gen3 else national)[pal.key]

        /**
         * The sheets of species [id] as [dex] numbers it, or null when it has none: an id past the numbering, one of
         * Gen 3's unused slots past 252, or a Pokemon nobody has drawn yet (natdex-map.tsv says which and why).
         */
        fun find(id: Int, dex: Dex): Pal? = when (dex) {
            Dex.GEN3 -> if (id in 1..GEN3_LAST) Pal(Pack.GEN3, id.toString()).takeIf { it.key in gen3 } else null
            Dex.NAT_DEX -> if (id <= GEN3_LAST) find(id, Dex.GEN3) else natDex[id]?.takeIf { sheets(it) != null }
            Dex.NATIONAL -> when {
                id in 1..251 -> find(id, Dex.GEN3)
                id in 252..386 -> internalOf(id)?.let { find(it, Dex.GEN3) }
                id > 386 -> Pal(Pack.NATIONAL, id.toString()).takeIf { it.key in national }
                else -> null
            }
        }
    }

    /**
     * How the GBA and Game Boy tracker panel numbers its species: Red to Crystal by national number, a Nat. Dex build
     * (which knows more than Gen 3's 411) by its own ids, any other Gen 3 game by Gen 3's.
     */
    fun trackerDex(generation: Int, speciesTotal: Int): Dex = when {
        generation < 3 -> Dex.NATIONAL
        speciesTotal > GEN3_LAST -> Dex.NAT_DEX
        else -> Dex.GEN3
    }

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

    /** A key as the tables write them: a number, then any form after a dash ("94", "6-mega-x", "741-pom-pom"). */
    private val KEY = Regex("[0-9]+(-[a-z0-9]+)*")

    /**
     * walkingpals.tsv and walkingpals-nat.tsv: key, animation, w, h, x, y, durations. The key is text, so the second
     * set's forms ("19-alola") read as well as the numbers; a line whose key is neither is skipped.
     */
    fun parse(lines: Sequence<String>): Map<String, Map<Anim, Sheet>> {
        val out = HashMap<String, HashMap<Anim, Sheet>>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val c = line.split('\t')
            if (c.size < 7) continue
            val key = c[0].trim().takeIf { KEY.matches(it) } ?: continue
            val anim = Anim.entries.firstOrNull { it.key == c[1] } ?: continue
            val durs = c[6].split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            out.getOrPut(key) { HashMap() }[anim] = Sheet(
                c[2].toIntOrNull() ?: 32, c[3].toIntOrNull() ?: 32, c[4].toIntOrNull() ?: 0, c[5].toIntOrNull() ?: 0, durs,
            )
        }
        return out
    }

    /**
     * natdex-map.tsv: the Nat. Dex Extension's ids 412-1283 (natdex, name, national, form, set, key, note) to the set
     * and key of their sheets, or to null for the ones with none.
     */
    fun parseNatDexMap(lines: Sequence<String>): Map<Int, Pal?> {
        val out = HashMap<Int, Pal?>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val c = line.split('\t')
            val id = c.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
            val pack = Pack.entries.firstOrNull { it.dir == c.getOrNull(4)?.trim() }
            val key = c.getOrNull(5)?.trim().orEmpty()
            out[id] = if (pack != null && KEY.matches(key)) Pal(pack, key) else null
        }
        return out
    }

    @Volatile private var index: Index? = null

    private fun <T> asset(ctx: android.content.Context, path: String, empty: T, read: (Sequence<String>) -> T): T =
        runCatching { ctx.assets.open(path).bufferedReader(Charsets.UTF_8).useLines(read) }.getOrDefault(empty)

    fun index(ctx: android.content.Context): Index =
        index ?: synchronized(this) {
            index ?: Index(
                asset(ctx, "${Pack.GEN3.dir}/${Pack.GEN3.table}", emptyMap()) { parse(it) },
                asset(ctx, "${Pack.NATIONAL.dir}/${Pack.NATIONAL.table}", emptyMap()) { parse(it) },
                asset(ctx, "${Pack.NATIONAL.dir}/natdex-map.tsv", emptyMap()) { parseNatDexMap(it) },
            ).also { index = it }
        }

    /** [Index.find] on the sets in the app. */
    fun find(ctx: android.content.Context, id: Int, dex: Dex): Pal? = index(ctx).find(id, dex)

    fun sheets(ctx: android.content.Context, pal: Pal): Map<Anim, Sheet>? = index(ctx).sheets(pal)

    private val bitmaps = HashMap<String, ImageBitmap?>()

    fun bitmap(ctx: android.content.Context, anim: Anim, pal: Pal): ImageBitmap? = synchronized(bitmaps) {
        val path = pal.path(anim)
        if (bitmaps.containsKey(path)) bitmaps[path]
        else runCatching {
            ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull().also { bitmaps[path] = it }
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
 * on PC. Only the drawing re-runs each frame; nothing recomposes. [pal] is
 * what WalkingPals.find answered for the species.
 */
@Composable
fun WalkingPalsIcon(pal: WalkingPals.Pal, status: String, unitDp: androidx.compose.ui.unit.Dp, boxDp: androidx.compose.ui.unit.Dp): Boolean {
    val ctx = LocalContext.current
    val sheets = remember(pal) { WalkingPals.sheets(ctx, pal) }
    if (sheets == null) return false
    val images = remember(pal) { WalkingPals.Anim.entries.associateWith { WalkingPals.bitmap(ctx, it, pal) } }
    if (images[WalkingPals.Anim.IDLE] == null) return false
    var tick by remember { mutableLongStateOf(0L) }
    val clock = remember(pal) { PalClock() }
    LaunchedEffect(pal) {
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
