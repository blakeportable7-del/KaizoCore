package com.ironmonone.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The pictures behind "Play as your Pokemon" on the phone: the Walking Pals sheets that ship in the app (the ones the
 * tracker's animated icons use, both sets, found by WalkingPals.find) and the player's own, decoded once and cropped a
 * frame at a time. All the rules about which frame and where it sits are SpriteIsMeLogic's and SheetSet's; this only
 * reads pixels.
 */
class AndroidSpriteArt(private val ctx: Context) : SpriteIsMeArt {
    private val filesDir: File = ctx.filesDir

    private val palBitmaps = HashMap<String, Bitmap?>()
    private val ownBitmaps = HashMap<WalkingPals.Anim, Bitmap?>()
    private var cachedVersion = -1
    private var ownSheetsCache: OwnSheets? = null
    private var ownSheetsBuilt = false
    private var pictureCache: SpriteArt.Fitted? = null
    private var pictureBuilt = false

    /** Drop what was decoded from the player's own files when the settings say they changed. */
    private fun sync() {
        val v = SpriteIsMeSettings.artVersion
        if (v == cachedVersion) return
        cachedVersion = v
        ownBitmaps.clear(); ownSheetsCache = null; ownSheetsBuilt = false; pictureCache = null; pictureBuilt = false
    }

    private fun palBitmap(anim: WalkingPals.Anim, pal: WalkingPals.Pal): Bitmap? {
        val key = pal.path(anim)
        if (palBitmaps.containsKey(key)) return palBitmaps[key]
        val b = WalkingPals.bitmap(ctx, anim, pal)?.asAndroidBitmap()
        palBitmaps[key] = b
        return b
    }

    override fun pal(id: Int, dex: WalkingPals.Dex): WalkingPals.Pal? =
        WalkingPals.find(ctx, id, dex)?.takeIf { palBitmap(WalkingPals.Anim.IDLE, it) != null }

    override fun palSheets(pal: WalkingPals.Pal): Map<WalkingPals.Anim, WalkingPals.Sheet>? =
        WalkingPals.sheets(ctx, pal)?.filterKeys { palBitmap(it, pal) != null }?.takeIf { it.isNotEmpty() }

    override fun palFrame(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? =
        palBitmap(anim, pal)?.let { crop(it, sheet, row, index) }

    override fun ownReady(): Boolean = SpriteIsMeStore.ready(filesDir, SpriteIsMeSettings.own)

    override fun ownSheets(): OwnSheets? {
        sync()
        if (ownSheetsBuilt) return ownSheetsCache
        ownSheetsBuilt = true
        val sizes = SpriteIsMeStore.sheetSizes(filesDir)
        val sheets = SheetSet.sheets(sizes, SpriteIsMeSettings.spec) ?: return null
        val rows = sheets.mapValues { (a, s) -> maxOf(1, (sizes.getValue(a).second) / s.h) }
        ownSheetsCache = OwnSheets(sheets, rows)
        return ownSheetsCache
    }

    override fun ownFrame(anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? {
        sync()
        val b = if (ownBitmaps.containsKey(anim)) ownBitmaps[anim]
            else BitmapFactory.decodeFile(SpriteIsMeStore.sheetFile(filesDir, anim).path).also { ownBitmaps[anim] = it }
        return b?.let { crop(it, sheet, row, index) }
    }

    override fun ownPicture(): SpriteArt.Fitted? {
        sync()
        if (pictureBuilt) return pictureCache
        pictureBuilt = true
        val b = BitmapFactory.decodeFile(SpriteIsMeStore.pictureFile(filesDir).path) ?: return null
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        pictureCache = SpriteArt.fit(ArtPixels(b.width, b.height, px))
        return pictureCache
    }

    /** The frame at [index] of [row] as pixels; the first frame when the sheet is smaller than its table says (as the tracker's icon does). */
    private fun crop(b: Bitmap, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? {
        if (sheet.w <= 0 || sheet.h <= 0 || sheet.w > b.width || sheet.h > b.height) return null
        val sx = (sheet.w * index).takeIf { it + sheet.w <= b.width } ?: 0
        val sy = (sheet.h * row).takeIf { it + sheet.h <= b.height } ?: 0
        val px = IntArray(sheet.w * sheet.h)
        b.getPixels(px, 0, sheet.w, sx, sy, sheet.w, sheet.h)
        return ArtPixels(sheet.w, sheet.h, px)
    }
}

/** Reading what the player picked: a picture as PNG bytes for the store, and the files of a sheet set. */
object SpriteIsMeImport {
    /** The longest side a stored picture keeps: plenty to fit into a 32 pixel box and small enough to back up. */
    const val MAX_SIDE = 1024

    private fun readCapped(ctx: Context, uri: Uri): ByteArray? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val r = s.read(chunk); if (r < 0) break
                total += r
                if (total > SpriteIsMeStore.MAX_FILE) return@use null
                out.write(chunk, 0, r)
            }
            out.toByteArray()
        }
    }.getOrNull()

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    /**
     * The picked picture as PNG bytes, its long side at most [MAX_SIDE] (a photo is read at reduced size, never in
     * full), turned upright by its EXIF orientation. Null when it is not a picture this can read.
     */
    fun picture(ctx: Context, uri: Uri): ByteArray? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        if (bytes.size > 64 * 1024 * 1024) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2 * MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        val turn = when (runCatching { android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            3 -> 180f; 6 -> 90f; 8 -> 270f; else -> 0f
        }
        if (turn != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(turn) }, true)
        val side = maxOf(bmp.width, bmp.height)
        if (side > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / side
            bmp = Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * k).toInt()), maxOf(1, (bmp.height * k).toInt()), true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray().takeIf { SheetSet.isPng(it) }
    }.getOrNull()

    /** The sheets in what was picked (PNG files, or zips of them): one per animation, chosen by name. */
    fun sheets(ctx: Context, uris: List<Uri>): Map<WalkingPals.Anim, ByteArray> {
        val files = ArrayList<Pair<String, ByteArray>>()
        for (u in uris.take(SpriteIsMeStore.MAX_ENTRIES)) {
            val bytes = readCapped(ctx, u) ?: continue
            files += SpriteIsMeStore.unpack(displayName(ctx, u), bytes)
        }
        return SheetSet.choose(files)
    }
}
