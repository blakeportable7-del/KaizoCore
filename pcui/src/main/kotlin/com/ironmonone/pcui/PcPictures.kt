package com.ironmonone.pcui

import androidx.compose.ui.graphics.ImageBitmap
import com.ironmonone.app.ArtPixels
import com.ironmonone.app.PcAssets
import com.ironmonone.app.SpriteArt
import com.ironmonone.app.WalkingPals
import com.ironmonone.app.pcDesktopCtx
import com.ironmonone.app.pcPictureOf
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * The Pokemon pictures on the panel, chosen in the gear and kept for every run (pictures.txt and the pictures/ folder in
 * the KaizoCore PC folder): the tracker's GBA pictures (besteon's "Original" set), the phone's "Animated Pokemon (Walking
 * Pals)" set (the idle frames, facing down, stepped as the panel redraws), and on top of either, the player's own
 * pictures: a PNG in pictures/ named after the Pokemon ("Gengar.png") or its National Dex number ("94.png") stands in
 * for that Pokemon wherever the panel draws it. Gear's "Add my own pictures" copies chosen files in there.
 */
object PcPictures {
    enum class Set(val key: String) { GBA("gba"), WALKING("walkingpals") }

    @Volatile var set: Set = Set.WALKING
    @Volatile var own: Boolean = false
    private var file: File? = null
    @Volatile var folder: File? = null
        private set

    fun load(f: File, dir: File) {
        file = f; folder = dir
        set = Set.WALKING; own = false
        runCatching {
            if (f.isFile) f.readLines().forEach { l ->
                val (k, v) = l.split('=', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                when (k) { "set" -> set = Set.entries.firstOrNull { it.key == v } ?: Set.GBA; "own" -> own = v == "true" }
            }
        }
        cache.clear()
        synchronized(bounds) { bounds.clear() }
    }

    fun save() {
        val f = file ?: return
        runCatching { f.parentFile?.mkdirs(); f.writeText("set=${set.key}\nown=$own\n") }
        cache.clear()
    }

    private val cache = HashMap<String, ImageBitmap?>()

    /**
     * Whether the composed card draws the walking sprite itself. The card is drawn only when the game changes, so a
     * sprite drawn into it stands still between changes; with the effects layer (pc.fx) the layer walks it instead, on
     * its own clock, and this is false (the animation exploration, 2026-10-10).
     */
    @Volatile var cardDrawsWalking: Boolean = true

    /**
     * Heart & Soul species whose walking sheet is replaced by the game's own follower (HnsPics.follower). Aggron: Sprite
     * Collab's base Aggron (walkingpals/idle/384.png) reads as Mega Aggron, and Blake picked the game's follower
     * (2026-10-09). Never point base Aggron at the mega sheet (walkingpals-ow 306-mega).
     */
    val GAME_FOLLOWER: kotlin.collections.Set<Int> = setOf(306)

    /**
     * A picture for a named Pokémon: Heart & Soul's own sprite when [hnsSpecies] is in this game, otherwise the
     * Nat. Dex sheet for [natDexId], including a base species standing in for a form that has no sheet of its own.
     */
    fun picture(hnsSpecies: Int?, natDexId: Int?): ImageBitmap? {
        natDexId?.let { sprite(it, false) }?.let { return it }
        hnsSpecies?.let { sprite(it, true) }?.let { return it }
        return null
    }

    /** The picture for [species] as the game numbers it ([hns]: Heart & Soul's own ids), at 32 by 32. */
    fun sprite(species: Int, hns: Boolean): ImageBitmap? = synchronized(cache) {
        val pack = if (hns) com.ironmonone.tracker.HnsSpecies.natDexId(species) ?: species else species
        val frame = if (set == Set.WALKING && !own) (System.currentTimeMillis() / 180 % 8).toInt() else 0
        val key = "${set.key}/$own/$hns/$pack/${ownStamp()}/$frame"
        cache.getOrPut(key) {
            (if (own) ownPicture(pack) else null)
                ?: (if (set == Set.WALKING) walking(species, hns, com.ironmonone.app.WalkingPals.Look()) else null)
                ?: PcAssets.gbaSprite(pcDesktopCtx, species, if (hns) "hns" else null)
        }
    }

    /**
     * The picture the player chose for [species], or null for the game's own (then the game's picture of this very
     * Pokemon, shiny or in its form, stands, and else the GBA set's): the player's own picture, or Walking Pals.
     */
    fun chosen(species: Int, hns: Boolean, look: com.ironmonone.app.WalkingPals.Look = com.ironmonone.app.WalkingPals.Look()): ImageBitmap? {
        if (!own && set == Set.GBA) return null
        val pack = if (hns) com.ironmonone.tracker.HnsSpecies.natDexId(species) ?: species else species
        return synchronized(cache) {
            cache.getOrPut("chosen/${set.key}/$own/$pack/${look.form}/${look.shiny}/${ownStamp()}") {
                (if (own) ownPicture(pack) else null) ?: (if (set == Set.WALKING) walking(species, hns, look) else null)
            }
        }
    }

    /** The sheet [look] names, or null when that form has no sheet of its own (the game's picture stands). */
    private fun palOf(species: Int, hns: Boolean, look: com.ironmonone.app.WalkingPals.Look): com.ironmonone.app.WalkingPals.Pal? {
        val index = com.ironmonone.app.WalkingPals.index(pcDesktopCtx)
        val dex = if (hns) com.ironmonone.app.WalkingPals.Dex.HNS else com.ironmonone.app.WalkingPals.Dex.NAT_DEX
        val found = index.find(species, dex) ?: return null
        val pal = index.look(found, look)
        return if (look.form != null && pal.key != look.form) null else pal
    }

    private var stamp = 0L
    private var stampAt = 0L
    /**
     * What is in the folder (every file's name, time and size), looked at once a second, so a picture added, replaced
     * or deleted shows without a restart. The newest file's time alone missed a deleted picture that was not the newest.
     */
    fun ownStamp(): Long {
        if (!own) return 0
        val now = System.currentTimeMillis()
        if (now - stampAt > 1000) {
            stampAt = now
            stamp = folder?.listFiles()?.sortedBy { it.name }?.fold(17L) { a, f -> (a * 31 + f.name.hashCode()) * 31 + f.lastModified() * 7 + f.length() } ?: 0
        }
        return stamp
    }

    /** The player's own picture for the Pokemon of pack id [pack] (Favorites' numbering), fitted into 32 by 32. */
    private fun ownPicture(pack: Int): ImageBitmap? {
        val dir = folder ?: return null
        val f = dir.listFiles { x -> x.isFile && x.name.endsWith(".png", true) }?.firstOrNull { idOf(it.nameWithoutExtension) == pack } ?: return null
        val img = runCatching { ImageIO.read(f) }.getOrNull() ?: return null
        val px = IntArray(img.width * img.height)
        val argb = java.awt.image.BufferedImage(img.width, img.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            .also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
        argb.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return box(SpriteArt.fit(ArtPixels(img.width, img.height, px)))
    }

    /** A file's name as a Pokemon: its name in the Nat. Dex table, or a National Dex number. */
    fun idOf(name: String): Int? = name.trim().toIntOrNull()?.let { n -> if (n in 252..386) com.ironmonone.app.Favorites.fromNational(n) ?: n else n }
        ?: com.ironmonone.app.Favorites.idOf(name.trim())

    /**
     * The animated icon the phone draws (WalkingPalsIcon): one cell of the sheet, at the sheet's own
     * offset, which may sit outside the 32 by 32 box. Null when this Pokemon has no walking sheet.
     */
    class Posed(val image: ImageBitmap, val x: Int, val y: Int, val w: Int, val h: Int)

    fun posed(species: Int, hns: Boolean, faint: Boolean, asleep: Boolean, look: com.ironmonone.app.WalkingPals.Look = com.ironmonone.app.WalkingPals.Look(),
              /** The furthest right its visible pixels may reach, from where it is drawn (the card's column of words). */
              maxRight: Float = Float.MAX_VALUE,
              /** The moment the frame is for (the effects layer draws the walk on its own clock). */
              nanos: Long = System.nanoTime()): Posed? {
        if (own || set != Set.WALKING || !com.ironmonone.app.TrackerOptions.animatedSprites) return null
        val index = WalkingPals.index(pcDesktopCtx)
        val pal = palOf(species, hns, look) ?: return null
        val sheets = index.sheets(pal) ?: return null
        val anim = when {
            faint && sheets[WalkingPals.Anim.FAINT] != null -> WalkingPals.Anim.FAINT
            asleep && sheets[WalkingPals.Anim.SLEEP] != null -> WalkingPals.Anim.SLEEP
            else -> WalkingPals.Anim.IDLE
        }
        val sheet = sheets[anim] ?: return null
        val frame = sheet.frameAt(nanos / 16_666_667L, loop = anim != WalkingPals.Anim.FAINT)
        val bmp = synchronized(cache) {
            cache.getOrPut("posed/${pal.pack.dir}/${pal.key}/${pal.shiny}/${anim.key}/$frame") { cell(pal, anim, sheet, frame) }
        } ?: return null
        val b = synchronized(bounds) { bounds.getOrPut("${pal.pack.dir}/${pal.key}/${anim.key}") { contentBox(pal, anim, sheet) } }
            ?: return Posed(bmp, sheet.x, sheet.y, sheet.w, sheet.h)
        val tall = maxOf(b[2] - b[0], b[3] - b[1])
        val footX = (b[0] + b[2]) / 2f
        val footY = b[3].toFloat()
        var k = if (tall <= SPRITE_MAX) 1f else SPRITE_MAX.toFloat() / tall
        // It may overflow its 32 up, left and down, but not under the words (Blake, 2026-10-09: shrink to the words).
        // Shrunk at its feet, its right edge is sheet.x + footX + k * (right - footX).
        if (b[2] > footX) {
            val room = (maxRight - sheet.x - footX) / (b[2] - footX)
            if (room < k) k = room.coerceAtLeast(0.25f)
        }
        if (k >= 1f) return Posed(bmp, sheet.x, sheet.y, sheet.w, sheet.h)
        // Shrunk around its feet, so a big sheet stands where a small one does.
        val w = (sheet.w * k).roundToInt().coerceAtLeast(1)
        val h = (sheet.h * k).roundToInt().coerceAtLeast(1)
        val x = (sheet.x + footX - footX * k).roundToInt()
        val y = (sheet.y + footY - footY * k).roundToInt()
        return Posed(bmp, x, y, w, h)
    }

    /** The most a walking sprite's visible pixels may span on the card, in card pixels (the sprite box is 32). */
    const val SPRITE_MAX = 44

    private val bounds = HashMap<String, IntArray?>()

    /** The visible pixels of every frame of the sheet's first row together: left, top, right, bottom. Null if blank. */
    private fun contentBox(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet): IntArray? {
        val img = com.ironmonone.app.PcDesktopCtx::class.java.getResourceAsStream("/assets/" + pal.path(anim))?.use { ImageIO.read(it) } ?: return null
        if (sheet.w <= 0 || sheet.h <= 0) return null
        val frames = (img.width / sheet.w).coerceAtLeast(1)
        val rowH = minOf(sheet.h, img.height)
        var l = sheet.w; var t = rowH; var r = 0; var btm = 0
        for (f in 0 until frames) for (y in 0 until rowH) for (x in 0 until sheet.w) {
            if ((img.getRGB(f * sheet.w + x, y) ushr 24) < 128) continue
            if (x < l) l = x
            if (y < t) t = y
            if (x + 1 > r) r = x + 1
            if (y + 1 > btm) btm = y + 1
        }
        return if (r > l && btm > t) intArrayOf(l, t, r, btm) else null
    }

    /** One cell of the sheet, at its own pixel size, not squeezed into 32 by 32. */
    private fun cell(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, frame: Int): ImageBitmap? {
        val img = com.ironmonone.app.PcDesktopCtx::class.java.getResourceAsStream("/assets/" + pal.path(anim))?.use { ImageIO.read(it) } ?: return null
        if (sheet.w <= 0 || sheet.h <= 0 || frame * sheet.w + sheet.w > img.width) return null
        val argb = java.awt.image.BufferedImage(img.width, img.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            .also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
        val px = IntArray(sheet.w * sheet.h)
        val rowH = minOf(sheet.h, img.height)
        argb.getRGB(frame * sheet.w, 0, sheet.w, rowH, px, 0, sheet.w)
        return pcPictureOf(px, sheet.w, rowH)
    }
    private fun walking(species: Int, hns: Boolean, look: com.ironmonone.app.WalkingPals.Look): ImageBitmap? {
        val index = WalkingPals.index(pcDesktopCtx)
        val pal = palOf(species, hns, look) ?: return null
        val sheet = index.sheets(pal)?.get(WalkingPals.Anim.IDLE) ?: return null
        val img = com.ironmonone.app.PcDesktopCtx::class.java.getResourceAsStream("/assets/" + pal.path(WalkingPals.Anim.IDLE))?.use { ImageIO.read(it) } ?: return null
        val frames = (img.width / sheet.w).coerceAtLeast(1)
        val frame = (System.currentTimeMillis() / 180 % frames).toInt()
        val argb = java.awt.image.BufferedImage(img.width, img.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            .also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
        val px = IntArray(sheet.w * sheet.h)
        argb.getRGB(frame * sheet.w, 0, sheet.w, minOf(sheet.h, img.height), px, 0, sheet.w)
        return box(SpriteArt.fit(ArtPixels(sheet.w, minOf(sheet.h, img.height), px)))
    }

    private fun box(f: SpriteArt.Fitted): ImageBitmap {
        val out = IntArray(SpriteArt.BOX * SpriteArt.BOX)
        for (y in 0 until f.pixels.h) for (x in 0 until f.pixels.w) {
            val tx = f.ox + x; val ty = f.oy + y
            if (tx in 0 until SpriteArt.BOX && ty in 0 until SpriteArt.BOX) out[ty * SpriteArt.BOX + tx] = f.pixels.argb[y * f.pixels.w + x]
        }
        return pcPictureOf(out, SpriteArt.BOX, SpriteArt.BOX)
    }

    /**
     * Copies [files] into the pictures folder, each named after the Pokemon it is for; the answer says how many were
     * taken and which names were not a Pokemon.
     */
    fun add(files: List<File>): Pair<Int, List<String>> {
        val dir = folder ?: return 0 to files.map { it.name }
        dir.mkdirs()
        var n = 0
        val skipped = ArrayList<String>()
        for (f in files) {
            val id = idOf(f.nameWithoutExtension)
            if (id == null || runCatching { ImageIO.read(f) }.getOrNull() == null) { skipped += f.name; continue }
            f.copyTo(File(dir, f.nameWithoutExtension.trim() + ".png"), overwrite = true)
            n++
        }
        synchronized(cache) { cache.clear() }
        return n to skipped
    }
}
