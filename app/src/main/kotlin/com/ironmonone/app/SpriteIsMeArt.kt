package com.ironmonone.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The pictures behind "Play as your Pokemon" on the phone: the Walking Pals sheets that ship in the app (the ones the
 * tracker's animated icons use, both sets, found by WalkingPals.Index.find) and the player's own. All the rules about which
 * frame and where it sits are SpriteIsMeLogic's and SheetSet's; this only reads pixels.
 *
 * Nothing is read from a file or decoded in a tick, which runs on the main thread once a display frame: [prepare]
 * decodes off it, and the tick crops what is ready. The player's sheets were decoded whole in a tick, up to 256 MB a
 * sheet, and with a sheet set imported every frame opened its four files (rc32 audit P2 #88, P3 #66); every Pokemon
 * met was kept for the session (P2 #106). Now the player's art is decoded once each time it changes, a sheet past
 * SheetSet's bound is never read, and of the Walking Pals only the one Pokemon being played as is kept, so
 * WalkingPals' bounded cache can never take its sheets away in the middle of a walk.
 */
class AndroidSpriteArt(private val ctx: Context) : SpriteIsMeArt {
    private val filesDir: File = ctx.filesDir

    /** The player's own art for one art version and choice, as [prepare] left it. */
    private class Own(
        val version: Int, val choice: SpriteIsMeSettings.Own, val ready: Boolean, val sheets: OwnSheets?,
        val bitmaps: Map<WalkingPals.Anim, Bitmap>, val picture: SpriteArt.Fitted?,
    )

    /** The one Pokemon being played as, decoded. */
    private class PalArt(val pal: WalkingPals.Pal, val sheets: Map<WalkingPals.Anim, WalkingPals.Sheet>?, val bitmaps: Map<WalkingPals.Anim, Bitmap>)

    @Volatile private var own: Own? = null
    @Volatile private var palArt: PalArt? = null
    /** Each decode [prepare] finished, for [readiness]. */
    private val decoded = java.util.concurrent.atomic.AtomicInteger()

    /** The Pokemon a tick asked for and did not have yet, for [prepare] to decode. */
    private val wanted = MutableStateFlow<WalkingPals.Pal?>(null)

    /** The player's art as it stands now, or null while it is being read again. */
    private fun ownNow(): Own? = own?.takeIf { it.version == SpriteIsMeSettings.artVersion && it.choice == SpriteIsMeSettings.own }

    /**
     * Decodes what the ticks draw, until cancelled; off the main thread (the runner's Default dispatcher): the player's
     * own art each time it changes (SpriteIsMeSettings.artVersion, which every import, removal and sheet setting bumps),
     * and each Pokemon a tick asks for.
     */
    suspend fun prepare() {
        coroutineScope {
            launch {
                snapshotFlow { SpriteIsMeSettings.artVersion to SpriteIsMeSettings.own }.collect { (v, choice) -> prepareOwn(v, choice) }
            }
            wanted.collect { p -> if (p != null && palArt?.pal != p) preparePal(p) }
        }
    }

    private fun prepareOwn(version: Int, choice: SpriteIsMeSettings.Own) {
        val note: String?
        own = when (choice) {
            SpriteIsMeSettings.Own.NONE -> { note = null; Own(version, choice, false, null, emptyMap(), null) }
            SpriteIsMeSettings.Own.PICTURE -> {
                val file = SpriteIsMeStore.pictureFile(filesDir)
                val picture = if (!file.isFile) null else runCatching { decodePicture(file) }.getOrNull()
                note = if (file.isFile && picture == null) SpriteIsMeCopy.OWN_UNREADABLE else null
                Own(version, choice, picture != null, null, emptyMap(), picture)
            }
            SpriteIsMeSettings.Own.SHEET -> {
                // Only sheets inside the bound: a bigger one is not read at all (SpriteIsMeStore.sheetSizes).
                val sizes = SpriteIsMeStore.sheetSizes(filesDir)
                val bitmaps = HashMap<WalkingPals.Anim, Bitmap>()
                for (a in sizes.keys) {
                    runCatching { BitmapFactory.decodeFile(SpriteIsMeStore.sheetFile(filesDir, a).path) }.getOrNull()?.let { bitmaps[a] = it }
                }
                val decoded = sizes.filterKeys { it in bitmaps }
                val sheets = SheetSet.sheets(decoded, SpriteIsMeSettings.spec)
                val rows = sheets?.mapValues { (a, s) -> maxOf(1, decoded.getValue(a).second / s.h) }
                note = when {
                    SpriteIsMeStore.oversized(filesDir) -> SpriteIsMeCopy.SHEET_TOO_BIG
                    sizes.isNotEmpty() && sheets == null -> SpriteIsMeCopy.OWN_UNREADABLE
                    else -> null
                }
                Own(version, choice, sheets != null, sheets?.let { OwnSheets(it, rows!!) }, bitmaps, null)
            }
        }
        SpriteIsMeSupport.ownNote = note
        decoded.incrementAndGet()
    }

    private fun decodePicture(file: File): SpriteArt.Fitted? {
        val b = BitmapFactory.decodeFile(file.path) ?: return null
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return SpriteArt.fit(ArtPixels(b.width, b.height, px))
    }

    private fun preparePal(p: WalkingPals.Pal) {
        val table = WalkingPals.index(ctx).sheets(p)
        val bitmaps = HashMap<WalkingPals.Anim, Bitmap>()
        table?.keys?.forEach { a -> WalkingPals.bitmap(ctx, a, p)?.asAndroidBitmap()?.let { bitmaps[a] = it } }
        palArt = PalArt(p, table?.filterKeys { it in bitmaps }?.takeIf { it.isNotEmpty() }, bitmaps)
        decoded.incrementAndGet()
    }

    /** The Pokemon decoded for [p], or null with [p] asked for: the frames show the trainer until it is in. */
    private fun palFor(p: WalkingPals.Pal): PalArt? = palArt?.takeIf { it.pal == p } ?: run { wanted.value = p; null }

    /** From the tables once they are read (WalkingPals.ready): a tick never reads them. Until then the trainer shows. */
    override fun pal(id: Int, dex: WalkingPals.Dex): WalkingPals.Pal? = WalkingPals.ready(ctx)?.find(id, dex)

    /** A shiny as its shiny and Unown as its letter, from the tables once they are read; [pal] itself until then. */
    override fun look(pal: WalkingPals.Pal, look: WalkingPals.Look): WalkingPals.Pal = WalkingPals.ready(ctx)?.look(pal, look) ?: pal

    override fun palSheets(pal: WalkingPals.Pal): Map<WalkingPals.Anim, WalkingPals.Sheet>? = palFor(pal)?.sheets

    override fun palFrame(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? =
        palFor(pal)?.bitmaps?.get(anim)?.let { crop(it, sheet, row, index) }

    /** What [prepare] found, from memory: no file is looked at in a tick. */
    override fun ownReady(): Boolean = ownNow()?.ready == true

    override fun ownSheets(): OwnSheets? = ownNow()?.sheets

    override fun ownFrame(anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? =
        ownNow()?.bitmaps?.get(anim)?.let { crop(it, sheet, row, index) }

    override fun ownPicture(): SpriteArt.Fitted? = ownNow()?.picture

    /** The tables once read, and one more each time [prepare] finishes a decode: a tick reads it, nothing more. */
    override fun readiness(): Int = decoded.get() * 2 + if (WalkingPals.ready(ctx) != null) 1 else 0

    /** The frame at [index] of [row] as pixels; the first frame when the sheet is smaller than its table says (WalkingPals.Sheet.cell). */
    private fun crop(b: Bitmap, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? {
        val (sx, sy) = sheet.cell(row, index, b.width, b.height) ?: return null
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
