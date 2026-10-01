package com.ironmonone.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The player's own image behind the tracker (2026-09-29), drawn where the Main background colour is
 * drawn (TrackerBackdrop.kt), under a black layer at [dim] percent so the text over it stays readable.
 * The boxes keep their own fill colours; give one an alpha in the editor and the image shows through it.
 *
 * The photo picker hands over an image, which is decoded off the main thread, turned upright by its EXIF
 * orientation, scaled so its long side is at most [MAX_LONG_SIDE] and kept as JPEG quality [JPEG_QUALITY]
 * in prep/tracker-bg.jpg, so it keeps working when the original moves. It is decoded once into [image]
 * and drawn from there; nothing decodes while the tracker draws. A missing or unreadable file leaves
 * [image] null and the colour is drawn as before. The settings are in prep/tracker-bg.txt.
 *
 * The backup takes both files, and so the image: up to a few hundred KB more (Backup.kt). The OBS stream
 * page has its own colours and never shows it.
 *
 * Everything from here to [TrackerBackgroundImages] is plain Kotlin the JVM tests run; that object is the
 * Android half (BitmapFactory, ExifInterface, Bitmap) and is not run by them.
 */
object TrackerBackground {
    const val IMAGE_FILE = "prep/tracker-bg.jpg"
    const val SETTINGS_FILE = "prep/tracker-bg.txt"
    const val MAX_LONG_SIDE = 1280
    const val JPEG_QUALITY = 85
    const val DEFAULT_DIM = 40
    const val MAX_DIM = 90
    /**
     * How much of the image shows through the tracker's own boxes. On the emulator (2026-09-29) the image showed
     * only in the margins, because every box is filled solid: the only way through was an 8-digit hex colour in the
     * editor. This takes that percent off the boxes' fill while an image is set, and only there (see [boxFill]).
     */
    const val DEFAULT_SEE_THROUGH = 35
    const val MAX_SEE_THROUGH = 80

    /** Fill covers the whole box and crops what does not fit; Fit shows the whole image and leaves the colour around it. */
    enum class Fit(val label: String) { FILL("Fill"), FIT("Fit") }

    /** Percent of black over the image, 0 to [MAX_DIM]. */
    var dim by mutableIntStateOf(DEFAULT_DIM)
        private set
    var fit by mutableStateOf(Fit.FILL)
        private set
    /** Percent taken off the tracker boxes' fill while an image is set, 0 to [MAX_SEE_THROUGH]. */
    var seeThrough by mutableIntStateOf(DEFAULT_SEE_THROUGH)
        private set
    /** Decoded once, here. Null when there is no image or its file would not decode. */
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    private var dir: File? = null

    /** Tests set these; the app decodes on a thread of its own with the Android decoder. */
    internal var decoder: (File) -> ImageBitmap? = { TrackerBackgroundImages.decodeFile(it) }
    internal var runAsync: (() -> Unit) -> Unit = { work -> Thread(work, "tracker-bg").apply { isDaemon = true }.start() }

    fun clampDim(v: Int): Int = v.coerceIn(0, MAX_DIM)
    fun changeDim(v: Int) { dim = clampDim(v) }
    fun clampSeeThrough(v: Int): Int = v.coerceIn(0, MAX_SEE_THROUGH)
    fun changeSeeThrough(v: Int) { seeThrough = clampSeeThrough(v) }

    /**
     * A tracker box's fill: [c] as it is, or with [seeThrough] percent taken off its alpha while an image is set.
     * Only the tracker's own boxes use this; its dialogs keep their fill solid so they stay readable over the game.
     */
    fun boxFill(c: Color): Color = boxFill(c, image != null, seeThrough)

    fun boxFill(c: Color, hasImage: Boolean, seeThrough: Int): Color =
        if (!hasImage || seeThrough <= 0) c else c.copy(alpha = c.alpha * (1f - clampSeeThrough(seeThrough) / 100f))
    fun changeFit(f: Fit) { fit = f; save() }

    fun settingsText(): String = "dim=$dim\nfit=${fit.name.lowercase()}\nsee=$seeThrough\n"

    /**
     * Reads settings text. What it does not hold, or holds unreadably, is the default: dim 40, Fill.
     * A dim outside 0 to 90 is clamped, not refused.
     */
    fun applySettings(text: String) {
        var d = DEFAULT_DIM
        var f = Fit.FILL
        var s = DEFAULT_SEE_THROUGH
        for (line in text.lines()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "dim" -> value.toIntOrNull()?.let { d = clampDim(it) }
                "fit" -> Fit.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }?.let { f = it }
                "see" -> value.toIntOrNull()?.let { s = clampSeeThrough(it) }
            }
        }
        dim = d
        fit = f
        seeThrough = s
    }

    fun save() { dir?.let { SafeWrite.text(File(it, SETTINGS_FILE), settingsText()) } }

    /** Reads the settings now and decodes the image, if there is one, on another thread. */
    fun load(filesDir: File) {
        dir = filesDir
        val settings = File(filesDir, SETTINGS_FILE)
        applySettings(if (settings.isFile) runCatching { settings.readText() }.getOrDefault("") else "")
        image = null
        val file = File(filesDir, IMAGE_FILE)
        if (file.isFile) runAsync { image = runCatching { decoder(file) }.getOrNull() }
    }

    /**
     * Keeps the image [uri] names as prep/tracker-bg.jpg and shows it. Blocking: call it off the main thread.
     * False when the image could not be read; the image that was there stays.
     */
    fun importFrom(context: Context, uri: Uri): Boolean = runCatching {
        val base = dir ?: context.filesDir.also { dir = it }
        val out = File(base, IMAGE_FILE)
        if (!TrackerBackgroundImages.process(context, uri, out)) return@runCatching false
        val decoded = decoder(out)
        if (decoded == null) { out.delete(); return@runCatching false }
        image = decoded
        true
    }.getOrDefault(false)

    /** Takes the image away; the colour shows again. */
    fun clear() {
        dir?.let { File(it, IMAGE_FILE).delete() }
        image = null
    }

    /** Tests only: forget the folder and the image, and go back to the default settings. */
    internal fun detach() { dir = null; image = null; applySettings("") }

    // ---- What the JVM tests run -------------------------------------------------------------------------

    /** The size to save an image of [w] by [h] at: long side at most [maxLong], aspect kept, never enlarged. */
    fun scaledSize(w: Int, h: Int, maxLong: Int = MAX_LONG_SIDE): Pair<Int, Int> {
        val long = max(w, h)
        if (long <= maxLong) return w to h
        val f = maxLong.toDouble() / long
        return max(1, (w * f).roundToInt()) to max(1, (h * f).roundToInt())
    }

    /**
     * The BitmapFactory inSampleSize for an image of [w] by [h]: the largest power of two that still decodes
     * to [maxLong] or more on the long side, so a huge photo is not decoded whole, and the exact scale down
     * that follows starts from enough pixels.
     */
    fun sampleSize(w: Int, h: Int, maxLong: Int = MAX_LONG_SIDE): Int {
        val long = max(w, h)
        var s = 1
        while (long / (s * 2) >= maxLong) s *= 2
        return s
    }

    /**
     * An EXIF orientation as the picture is turned upright: mirror it left to right first when [flip],
     * then rotate it [degrees] clockwise (androidx ExifInterface's getRotationDegrees and isFlipped agree
     * for every tag). Tags 5 to 8 turn the picture on its side, which is [swapsAxes].
     */
    class Orientation(val degrees: Int, val flip: Boolean) {
        val swapsAxes: Boolean get() = degrees % 180 != 0
    }

    fun orientationOf(tag: Int): Orientation = when (tag) {
        2 -> Orientation(0, true)      // mirrored left to right
        3 -> Orientation(180, false)   // upside down
        4 -> Orientation(180, true)    // mirrored top to bottom
        5 -> Orientation(270, true)    // transposed
        6 -> Orientation(90, false)    // the usual phone portrait
        7 -> Orientation(90, true)     // transversed
        8 -> Orientation(270, false)
        else -> Orientation(0, false)  // 1, and anything the tag should not hold
    }

    /** Where an image goes in a box: the part of the image to take and the part of the box to put it in, in pixels. */
    data class Placement(
        val srcX: Int, val srcY: Int, val srcW: Int, val srcH: Int,
        val dstX: Int, val dstY: Int, val dstW: Int, val dstH: Int,
    )

    /**
     * Fill: the image scaled until it covers the box, the middle of it kept. Fit: scaled until all of it is
     * inside the box, centred. Null when the box or the image has no size.
     */
    fun place(imgW: Int, imgH: Int, boxW: Int, boxH: Int, fit: Fit): Placement? {
        if (imgW <= 0 || imgH <= 0 || boxW <= 0 || boxH <= 0) return null
        return when (fit) {
            Fit.FILL -> {
                val scale = max(boxW.toDouble() / imgW, boxH.toDouble() / imgH)
                val w = (boxW / scale).roundToInt().coerceIn(1, imgW)
                val h = (boxH / scale).roundToInt().coerceIn(1, imgH)
                Placement((imgW - w) / 2, (imgH - h) / 2, w, h, 0, 0, boxW, boxH)
            }
            Fit.FIT -> {
                val scale = min(boxW.toDouble() / imgW, boxH.toDouble() / imgH)
                val w = (imgW * scale).roundToInt().coerceIn(1, boxW)
                val h = (imgH * scale).roundToInt().coerceIn(1, boxH)
                Placement(0, 0, imgW, imgH, (boxW - w) / 2, (boxH - h) / 2, w, h)
            }
        }
    }

    /** What one draw of the backdrop does, in order (TrackerBackdrop.kt runs them). */
    sealed interface Op {
        data class Colour(val color: Color) : Op
        data class Picture(val placement: Placement) : Op
        /** [alpha] of black over the rectangle the picture covers, and over nothing else. */
        data class Dim(val alpha: Float, val x: Int, val y: Int, val w: Int, val h: Int) : Op
    }

    /**
     * The backdrop for a box of [boxW] by [boxH]: the Main background colour first, always, then, when there
     * is an image ([imgW] above zero), the picture, then a black layer at [dim] percent over the picture
     * and not over the colour around a Fit. No dim layer at 0.
     */
    fun plan(page: Color, imgW: Int, imgH: Int, boxW: Int, boxH: Int, fit: Fit, dim: Int): List<Op> {
        val ops = ArrayList<Op>(3)
        ops += Op.Colour(page)
        val p = place(imgW, imgH, boxW, boxH, fit) ?: return ops
        ops += Op.Picture(p)
        val d = clampDim(dim)
        if (d > 0) ops += Op.Dim(d / 100f, p.dstX, p.dstY, p.dstW, p.dstH)
        return ops
    }
}

/** The Android half of [TrackerBackground]: reading a picked image and keeping it. Not run by the JVM tests. */
internal object TrackerBackgroundImages {
    fun decodeFile(f: File): ImageBitmap? = BitmapFactory.decodeFile(f.path)?.asImageBitmap()

    /**
     * Writes the image [uri] names to [out] as the backdrop keeps it: upright, at most 1280 px on its long
     * side, JPEG quality 85, on black so a transparent PNG is not left to the encoder. False when it cannot
     * be read. Blocking, and never on the main thread. It goes in whole or not at all (SafeWrite).
     */
    fun process(context: Context, uri: Uri, out: File): Boolean {
        val resolver = context.contentResolver
        val tag = runCatching {
            resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
            }
        }.getOrNull() ?: 1
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Reading the bounds returns no bitmap by design, so the result of decodeStream is not the test.
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val sampled = BitmapFactory.Options().apply { inSampleSize = TrackerBackground.sampleSize(bounds.outWidth, bounds.outHeight) }
        val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sampled) } ?: return false
        try {
            val o = TrackerBackground.orientationOf(tag)
            val upW = if (o.swapsAxes) src.height else src.width
            val upH = if (o.swapsAxes) src.width else src.height
            val (w, h) = TrackerBackground.scaledSize(upW, upH)
            val target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = android.graphics.Canvas(target)
                canvas.drawColor(android.graphics.Color.BLACK)
                // Centre on the origin, mirror, turn, scale to the target, centre on the target.
                val m = Matrix()
                m.postTranslate(-src.width / 2f, -src.height / 2f)
                if (o.flip) m.postScale(-1f, 1f)
                m.postRotate(o.degrees.toFloat())
                val scale = w.toFloat() / upW
                m.postScale(scale, scale)
                m.postTranslate(w / 2f, h / 2f)
                canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
                val jpeg = ByteArrayOutputStream().also { target.compress(Bitmap.CompressFormat.JPEG, TrackerBackground.JPEG_QUALITY, it) }
                return SafeWrite.bytes(out, jpeg.toByteArray())
            } finally { target.recycle() }
        } finally { src.recycle() }
    }
}
