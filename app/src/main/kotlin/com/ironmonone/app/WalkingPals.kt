package com.ironmonone.app

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * four ways ([Dex]) and [Index.find] takes any of them. Sprites: PMD Sprite
 * Collab, CC BY-NC 4.0, credited on the About screen.
 *
 * Blake, 2026-10-03: "If they are shiny you should be able to play as shiny",
 * "And forms sprites". Each set carries its shinies (shiny-colors.tsv, a color
 * map for each shiny that is an exact recolor of its plain sheet; shiny.tsv
 * and shiny/, the rest as sheets of their own), and the second set Unown's
 * letters. A [Look] says which to draw; PalForms works it out from the game.
 *
 * Blake, 2026-10-04: "i want iron boulder, find the missing sprites". A third set, walkingpals-darkus/ ([Pack.DARKUS]),
 * holds DarkusShadow's overworld sprites for Pokemon Sprite Collab has not drawn (Iron Boulder, Iron Crown, Chien-Pao,
 * Mega Malamar and the rest natdex-map.tsv names there); Sprite Collab's own sheet always wins.
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
        /**
         * DarkusShadow's overworld sprites, keyed as [NATIONAL] is ("1022" is Iron Boulder), only where Sprite Collab has
         * no sheet of its own (Blake, 2026-10-04: "use the ones i gave you if they fill in sprite collabs gap"). Idle and
         * walk only, no shinies; natdex-map.tsv names this set for the ids it covers (convert_walking_pals_nat.py,
         * DARKUSSHADOW). Free use with credit, credited on the About screen.
         */
        DARKUS("walkingpals-darkus", "walkingpals-darkus.tsv"),
    }

    /** Where one Pokemon's sheets are: a set, its key in that set's table, and whether it is the shiny ([Index.look]). */
    data class Pal(val pack: Pack, val key: String, val shiny: Boolean = false) {
        fun path(anim: Anim): String = "${pack.dir}/${anim.key}/$key.png"
        /** The shiny's sheet of its own, where it is not a recolor of the plain one (the set's shiny.tsv). */
        fun shinyPath(anim: Anim): String = "${pack.dir}/shiny/${anim.key}/$key.png"
    }

    /**
     * One Pokemon as its game draws it, past its species (Blake, 2026-10-03: "If they are shiny you should be able to play
     * as shiny", "And forms sprites"): the second set's key for its form ("201-b" is Unown B, "386-attack"), null for the
     * species' own sheets, and whether it is shiny. PalForms works it out from what the game holds.
     */
    data class Look(val form: String? = null, val shiny: Boolean = false)

    /**
     * A shiny that is an exact recolor of its plain sheet (the set's shiny-colors.tsv): each opaque color of the plain sheet
     * and the shiny's color for it, as RRGGBB, [from] in order. The converter proved, sheet by sheet, that recoloring the
     * plain sheet this way makes the shiny pixel for pixel (convert_walking_pals_nat.py, THE SHINIES).
     */
    class ShinyColors(val from: IntArray, val to: IntArray) {
        /** [argb] recolored in place: each opaque pixel takes the map's color; a clear pixel, or a color it lacks, stays. */
        fun apply(argb: IntArray) {
            for (i in argb.indices) {
                val p = argb[i]
                if ((p ushr 24) != 0xFF) continue
                val at = java.util.Arrays.binarySearch(from, p and 0xFFFFFF)
                if (at >= 0) argb[i] = (p and 0xFF000000.toInt()) or to[at]
            }
        }
    }

    /** One set's shinies: the color maps by key and animation, and the sheets of their own with their rows. */
    class Shinies(val colors: Map<String, Map<Anim, ShinyColors>> = emptyMap(), val sheets: Map<String, Map<Anim, Sheet>> = emptyMap()) {
        fun has(key: String): Boolean = key in colors || key in sheets
    }

    /** The file one animation of a [Pal] is drawn from ([Index.art]), and the colors to recolor it with, for a shiny that is a recolor. */
    data class Art(val path: String, val colors: ShinyColors? = null)

    /** How a caller numbers its species. */
    enum class Dex {
        /** Gen 3's own ids (a vanilla GBA game): 1-251 national, Hoenn at 277-411. */
        GEN3,
        /** The Nat. Dex Extension's (a Nat. Dex build): 1-411 as Gen 3's, then 412-1283 through natdex-map.tsv. */
        NAT_DEX,
        /** National dex numbers (a DS game, Red to Crystal): 1-251 as Gen 3's, 252-386 Hoenn, 387 on the second set. */
        NATIONAL,
        /**
         * MaxDex 1.0's (a MaxDex build): the Nat. Dex Extension's ids to 1235, then its 45 Legends Z-A Megas, 1236-1280,
         * in an order of its own, each found as the Nat. Dex id of the same name ([maxDexToNatDex]).
         */
        MAX_DEX,
        /** Heart & Soul's own ids (1 to 1572, the expansion's order), each drawn as the Nat. Dex id of the same Pokemon (HnsSpecies). */
        HNS,
    }

    /**
     * Both sets, the Nat. Dex map and MaxDex's ids, and the one lookup every caller uses. Plain maps, no Android, so the
     * tests load it from the files as shipped. [maxDex] is [maxDexToNatDex]'s map; [internalOf] turns a Hoenn national
     * number (252-386) into Gen 3's id for it. [shinies] is each set's shinies.
     */
    class Index(
        private val gen3: Map<String, Map<Anim, Sheet>>,
        private val national: Map<String, Map<Anim, Sheet>>,
        private val natDex: Map<Int, Pal?>,
        private val maxDex: Map<Int, Int> = emptyMap(),
        private val internalOf: (Int) -> Int? = Favorites::fromNational,
        private val shinies: Map<Pack, Shinies> = emptyMap(),
        private val darkus: Map<String, Map<Anim, Sheet>> = emptyMap(),
    ) {
        /** [pal]'s sheets; a shiny's own sheets in place of the plain ones for the animations that have them. */
        fun sheets(pal: Pal): Map<Anim, Sheet>? {
            val plain = when (pal.pack) { Pack.GEN3 -> gen3; Pack.NATIONAL -> national; Pack.DARKUS -> darkus }[pal.key] ?: return null
            val own = if (pal.shiny) shinies[pal.pack]?.sheets?.get(pal.key) else null
            return if (own == null) plain else plain + own.filterKeys { it in plain }
        }

        /**
         * [pal] as [look] says: its form's sheets where the second set has them (Unown's letter, the game's Deoxys), then
         * its shiny where one ships. Each falls back silently: no form sheet is the species' own, no shiny the plain one.
         */
        fun look(pal: Pal, look: Look): Pal {
            val formed = look.form?.let { k -> Pal(Pack.NATIONAL, k).takeIf { k in national } } ?: pal
            return formed.copy(shiny = look.shiny && shinies[formed.pack]?.has(formed.key) == true)
        }

        /** [find], drawn as [look] says. */
        fun find(id: Int, dex: Dex, look: Look): Pal? = find(id, dex)?.let { look(it, look) }

        /**
         * What [anim] of [pal] is drawn from: its sheet; a shiny's sheet of its own, or its plain sheet with the colors to
         * recolor it with, or (no shiny ships for this animation) the plain sheet as it is.
         */
        fun art(pal: Pal, anim: Anim): Art {
            val s = shinies[pal.pack]?.takeIf { pal.shiny } ?: return Art(pal.path(anim))
            if (s.sheets[pal.key]?.containsKey(anim) == true) return Art(pal.shinyPath(anim))
            return Art(pal.path(anim), s.colors[pal.key]?.get(anim))
        }

        /**
         * The sheets of species [id] as [dex] numbers it, or null when it has none: an id past the numbering, one of
         * Gen 3's unused slots past 252, or a Pokemon nobody has drawn yet (natdex-map.tsv says which and why). A MaxDex
         * id is the Nat. Dex Pokemon of its name, stand-ins and all, or nothing when the two tables do not share it.
         */
        fun find(id: Int, dex: Dex): Pal? = when (dex) {
            Dex.GEN3 -> if (id in 1..GEN3_LAST) Pal(Pack.GEN3, id.toString()).takeIf { it.key in gen3 } else null
            Dex.NAT_DEX -> if (id <= GEN3_LAST) find(id, Dex.GEN3) else natDex[id]?.takeIf { sheets(it) != null }
            Dex.MAX_DEX -> maxDex[id]?.let { find(it, Dex.NAT_DEX) }
            Dex.HNS -> com.ironmonone.tracker.HnsSpecies.natDexId(id)?.let { find(it, Dex.NAT_DEX) }
            Dex.NATIONAL -> when {
                id in 1..251 -> find(id, Dex.GEN3)
                id in 252..386 -> internalOf(id)?.let { find(it, Dex.GEN3) }
                id > 386 -> Pal(Pack.NATIONAL, id.toString()).takeIf { it.key in national }
                else -> null
            }
        }
    }

    /**
     * How the GBA and Game Boy tracker panel numbers its species: Red to Crystal by national number, MaxDex 1.0
     * ([maxDex], MaxDexInfo's maxDexInPlay) by its own ids, a Nat. Dex build (which knows more than Gen 3's 411) by
     * its own ids, any other Gen 3 game by Gen 3's.
     */
    fun trackerDex(generation: Int, speciesTotal: Int, maxDex: Boolean = false, hns: Boolean = false): Dex = when {
        generation < 3 -> Dex.NATIONAL
        hns -> Dex.HNS
        maxDex -> Dex.MAX_DEX
        speciesTotal > GEN3_LAST -> Dex.NAT_DEX
        else -> Dex.GEN3
    }

    class Sheet(val w: Int, val h: Int, val x: Int, val y: Int, val durations: IntArray) {
        val total: Int = durations.sum()

        /**
         * Where frame [index] of row [row] starts in a picture [width] x [height], x then y: a frame or a row past the
         * picture is the first one, as the tracker's icon draws it. Null when not even one frame fits. Play as your
         * Pokemon crops by it (AndroidSpriteArt), and SpriteIsMeEverySheetTest walks every shipped sheet through it.
         */
        fun cell(row: Int, index: Int, width: Int, height: Int): IntArray? {
            if (w <= 0 || h <= 0 || w > width || h > height) return null
            val sx = (w * index).takeIf { it + w <= width } ?: 0
            val sy = (h * row).takeIf { it + h <= height } ?: 0
            return intArrayOf(sx, sy)
        }

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

    /**
     * shiny-colors.tsv: key, the animations a map covers ("idle,walk,sleep"), and the map as plain=shiny pairs of RRGGBB
     * ("f8d030=f8b800 ..."). A line whose map is not read whole, or names a color twice, is skipped: its shiny is then
     * the plain one, never a half recolored one.
     */
    fun parseShinyColors(lines: Sequence<String>): Map<String, Map<Anim, ShinyColors>> {
        val out = HashMap<String, HashMap<Anim, ShinyColors>>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val c = line.split('\t')
            if (c.size < 3) continue
            val key = c[0].trim().takeIf { KEY.matches(it) } ?: continue
            val words = c[2].trim().split(' ')
            val pairs = words.mapNotNull { w ->
                if (w.length != 13 || w[6] != '=') null
                else w.substring(0, 6).toIntOrNull(16)?.let { a -> w.substring(7).toIntOrNull(16)?.let { b -> a to b } }
            }.sortedBy { it.first }
            if (pairs.isEmpty() || pairs.size != words.size || pairs.zipWithNext().any { (a, b) -> a.first == b.first }) continue
            val colors = ShinyColors(IntArray(pairs.size) { pairs[it].first }, IntArray(pairs.size) { pairs[it].second })
            for (a in c[1].split(',')) Anim.entries.firstOrNull { it.key == a.trim() }?.let { out.getOrPut(key) { HashMap() }[it] = colors }
        }
        return out
    }

    /** A species table as the tracker writes it (maxdex/species.tsv, natdex/species.tsv): id, a tab, the name. */
    fun parseSpecies(lines: Sequence<String>): Map<Int, String> {
        val out = HashMap<Int, String>()
        for (line in lines) {
            val tab = line.indexOf('\t'); if (tab < 0) continue
            val id = line.substring(0, tab).trim().toIntOrNull() ?: continue
            out[id] = line.substring(tab + 1).trim()
        }
        return out
    }

    /**
     * MaxDex 1.0's species ids to the Nat. Dex Extension's, so MaxDex walks too (Blake, 2026-10-03, wanted a MaxDex run
     * played as a Gen 9 Pokemon). The two tables name the same Pokemon at the same id up to 1235; MaxDex's 45 Legends
     * Z-A Megas, 1236-1280, are in an order of its own (Dragonite-M is 1236 there, 1241 in Nat. Dex, whose 1236 is Battle
     * Bond Greninja), so each is the Nat. Dex id of the same name, and natdex-map.tsv gives it its sheet or its stand-in.
     * A name the Nat. Dex table does not hold once is left out: that Pokemon finds no sheet, never another Pokemon's.
     */
    fun maxDexToNatDex(maxDex: Map<Int, String>, natDex: Map<Int, String>): Map<Int, Int> {
        val byName = natDex.entries.groupBy({ it.value.lowercase() }, { it.key })
        val out = HashMap<Int, Int>()
        for ((id, name) in maxDex) {
            if (natDex[id].equals(name, ignoreCase = true)) out[id] = id
            else byName[name.lowercase()]?.singleOrNull()?.let { out[id] = it }
        }
        return out
    }

    /** One of the tracker's own tables, by its resource path, or [empty] when it cannot be read. */
    private fun <T : Any> tracker(path: String, empty: T, read: (Sequence<String>) -> T): T = runCatching {
        com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.useLines(read)
    }.getOrNull() ?: empty

    /** [maxDexToNatDex] over the tracker's two species tables, as the app ships them. Blocks while it reads: off the main thread. */
    fun maxDexIds(): Map<Int, Int> = maxDexToNatDex(
        tracker("/maxdex/species.tsv", emptyMap()) { parseSpecies(it) },
        tracker("/natdex/species.tsv", emptyMap()) { parseSpecies(it) },
    )

    @Volatile private var index: Index? = null

    private fun <T> asset(ctx: android.content.Context, path: String, empty: T, read: (Sequence<String>) -> T): T =
        runCatching { ctx.assets.open(path).bufferedReader(Charsets.UTF_8).useLines(read) }.getOrDefault(empty)

    /**
     * The tables, read and parsed the first time: it blocks its caller while it reads, so it is for a background
     * thread (AndroidSpriteArt.prepare, [ready]'s own). The main thread asks [ready].
     */
    fun index(ctx: android.content.Context): Index =
        index ?: synchronized(this) {
            index ?: Index(
                asset(ctx, "${Pack.GEN3.dir}/${Pack.GEN3.table}", emptyMap()) { parse(it) },
                asset(ctx, "${Pack.NATIONAL.dir}/${Pack.NATIONAL.table}", emptyMap()) { parse(it) },
                asset(ctx, "${Pack.NATIONAL.dir}/natdex-map.tsv", emptyMap()) { parseNatDexMap(it) },
                maxDexIds(),
                shinies = Pack.entries.associateWith { p ->
                    Shinies(asset(ctx, "${p.dir}/shiny-colors.tsv", emptyMap()) { parseShinyColors(it) }, asset(ctx, "${p.dir}/shiny.tsv", emptyMap()) { parse(it) })
                },
                darkus = asset(ctx, "${Pack.DARKUS.dir}/${Pack.DARKUS.table}", emptyMap()) { parse(it) },
            ).also { index = it }
        }

    private val loaded = ReadOnce<Index>()

    /**
     * The index for the main thread: null until the tables are read, and the first ask starts reading them on a thread
     * of their own. They were read on first use, and the first use was on the main thread: the tracker's icon and the
     * picker in composition, Play as your Pokemon in a tick (RC35-NOTICED N #10). A composable that was told null
     * recomposes when they land, and a tick asks again on the next frame.
     */
    fun ready(ctx: android.content.Context): Index? = loaded.get { index(ctx.applicationContext ?: ctx) }

    /** What the decoded sheets may hold at most: a party's and a foe's, many times over, and never a session's worth. */
    const val CACHE_BYTES = 32L * 1024 * 1024

    /**
     * The sheets decoded so far, by path: every species a long session met used to stay decoded for the life of the
     * process, hundreds of MB on top of a DS core (rc32 audit P2 #106). Now the least recently used go past
     * [CACHE_BYTES]. A sheet on screen is held by its icon, so one dropped here costs a decode when it comes back.
     */
    private val cache = SheetCache<ImageBitmap>(CACHE_BYTES) { it.width.toLong() * it.height * 4 }

    /**
     * One sheet, decoded. A shiny's is its sheet of its own, or its plain sheet recolored ([ShinyColors]), or the plain sheet
     * where no shiny ships for that animation. Reads the app's assets: never on the main thread (WalkingPalsIcon and
     * AndroidSpriteArt call it on IO and Default).
     */
    fun bitmap(ctx: android.content.Context, anim: Anim, pal: Pal): ImageBitmap? =
        cache.get(if (pal.shiny) "shiny/" + pal.path(anim) else pal.path(anim)) { _ ->
            runCatching {
                val art = if (pal.shiny) index(ctx).art(pal, anim) else Art(pal.path(anim))
                val colors = art.colors
                val opts = android.graphics.BitmapFactory.Options().apply { inMutable = colors != null }
                val b = ctx.assets.open(art.path).use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                if (b != null && colors != null) {
                    val px = IntArray(b.width * b.height)
                    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                    colors.apply(px)
                    b.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
                }
                b?.asImageBitmap()
            }.getOrNull()
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

/**
 * A value read once, on a thread of its own, and handed out without waiting: null until it is read (RC35-NOTICED
 * N #10). The value is snapshot state, so a composable that was told null recomposes when it lands. A read that fails
 * leaves it null, as a missing table leaves the sets empty. Plain Kotlin and the Compose runtime, so the tests drive it
 * with a thread of their own.
 */
internal class ReadOnce<T : Any>(
    private val start: (Runnable) -> Unit = { r -> Thread(r, "walking-pals").apply { isDaemon = true }.start() },
) {
    private val value = androidx.compose.runtime.mutableStateOf<T?>(null)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The value when it has been read; else null, and the first ask hands [read] to the thread. */
    fun get(read: () -> T): T? {
        value.value?.let { return it }
        if (started.compareAndSet(false, true)) start(Runnable { runCatching(read).getOrNull()?.let { value.value = it } })
        return null
    }
}

/**
 * A least-recently-used cache bounded by the bytes its values hold, which also remembers a key that could not be
 * loaded, so a missing sheet is not looked for again (rc32 audit P2 #106). Plain Kotlin, so the tests run it.
 */
internal class SheetCache<V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<String, V>(16, 0.75f, true)
    private val missing = HashSet<String>()

    /** What the values held now take. */
    var bytes = 0L
        private set

    val size: Int get() = synchronized(this) { map.size }

    /**
     * The value for [key], loaded with [load] the first time and again once it was dropped. The load runs outside the
     * lock, so a slow decode on one thread does not hold up a look-up on another. The value just loaded always stays,
     * however big; the least recently used go first until the rest fit.
     */
    fun get(key: String, load: (String) -> V?): V? {
        synchronized(this) {
            map[key]?.let { return it }
            if (key in missing) return null
        }
        val v = load(key)
        synchronized(this) {
            if (v == null) { missing += key; return null }
            map[key]?.let { return it }
            map[key] = v
            bytes += sizeOf(v)
            val it = map.entries.iterator()
            while (bytes > maxBytes && map.size > 1 && it.hasNext()) {
                val e = it.next()
                if (e.key == key) continue
                bytes -= sizeOf(e.value)
                it.remove()
            }
            return v
        }
    }
}

/** What one icon draws: the animation, the frame of its sheet and the facing row. */
internal data class PalFrame(val anim: WalkingPals.Anim, val index: Int, val row: Int)

/**
 * SpriteData's choice of animation: asleep after 55 seconds idle; fainting at 0 HP; asleep while the status is SLP;
 * walking while a direction is held outside battle; idle otherwise.
 */
internal fun palAnim(afk: Boolean, status: String, walk: Boolean): WalkingPals.Anim = when {
    afk -> WalkingPals.Anim.SLEEP
    status == "FNT" -> WalkingPals.Anim.FAINT
    status == "SLP" -> WalkingPals.Anim.SLEEP
    walk -> WalkingPals.Anim.WALK
    else -> WalkingPals.Anim.IDLE
}

/**
 * One animated Walking Pals icon, in place of the 32x32 still (Drawing.drawSpriteIcon).
 * Which animation, as SpriteData decides it: asleep after 55 seconds idle;
 * fainting at 0 HP (once, then still); asleep while the status is SLP; walking,
 * facing the held direction, while a direction is held outside battle; idle
 * otherwise. Frames advance at 60 a second of real time, as the reference
 * scales them to hold that pace under fast-forward. The frame is drawn at the
 * sheet's offset from the icon's corner and may spill past the 32x32 box, as
 * on PC. Nothing recomposes, and the drawing re-runs only when the frame shown
 * changes: the loop works out the animation, its frame and the facing row each
 * display frame and writes them only when one of them moves. It wrote a tick
 * every display frame, up to 120 a second, so every icon redrew that often
 * though a sheet frame lasts several (rc32 audit P3 #45). [pal] is what
 * WalkingPals.Index.find answered for the species.
 */
@Composable
fun WalkingPalsIcon(pal: WalkingPals.Pal, status: String, unitDp: androidx.compose.ui.unit.Dp, boxDp: androidx.compose.ui.unit.Dp): Boolean {
    val ctx = LocalContext.current
    // The tables are read off the main thread (RC35-NOTICED N #10); a pal was found in them, so they are in now.
    val sheets = WalkingPals.ready(ctx).let { ix -> remember(ix, pal) { ix?.sheets(pal) } }
    if (sheets == null) return false
    // Decoded off the main thread, idle first: each new species decoded four sheets in composition, a stutter at every new
    // opponent (rc32 audit P2 #106). Until the idle sheet is in, this says no and the card draws the still sprite.
    val images by produceState<Map<WalkingPals.Anim, ImageBitmap>>(emptyMap(), pal) {
        val idle = withContext(Dispatchers.IO) { WalkingPals.bitmap(ctx, WalkingPals.Anim.IDLE, pal) } ?: return@produceState
        value = mapOf(WalkingPals.Anim.IDLE to idle)
        value = value + withContext(Dispatchers.IO) {
            WalkingPals.Anim.entries.filter { it != WalkingPals.Anim.IDLE }.mapNotNull { a -> WalkingPals.bitmap(ctx, a, pal)?.let { a to it } }
        }
    }
    if (images[WalkingPals.Anim.IDLE] == null) return false
    var shown by remember(pal) { mutableStateOf<PalFrame?>(null) }
    val statusNow by rememberUpdatedState(status)
    LaunchedEffect(pal) {
        val t0 = withFrameNanos { it }
        var anim: WalkingPals.Anim? = null
        var start = 0L
        while (true) withFrameNanos { nanos ->
            val frames = (nanos - t0) / 16_666_667L
            val afk = System.nanoTime() - SpriteMotion.lastInputNanos >= WalkingPals.IDLE_SECONDS_UNTIL_SLEEP * 1_000_000_000L
            val walk = TrackerOptions.spritesWalk && !SpriteMotion.inBattle && SpriteMotion.walking()
            var a = palAnim(afk, statusNow, walk)
            if (sheets[a] == null || images[a] == null) a = WalkingPals.Anim.IDLE
            val sheet = sheets[a] ?: return@withFrameNanos
            if (anim != a) { anim = a; start = frames }
            val index = sheet.frameAt(frames - start, loop = a != WalkingPals.Anim.FAINT)
            val row = if (a == WalkingPals.Anim.WALK) SpriteMotion.facingRow() else 0
            val f = PalFrame(a, index, row)
            if (f != shown) shown = f
        }
    }
    Canvas(Modifier.size(boxDp)) {
        val (anim, index, row) = shown ?: return@Canvas
        val sheet = sheets[anim] ?: return@Canvas
        val img = images[anim] ?: return@Canvas
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
