package com.ironmonone.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.InputStream

/*
 * The PC tracker's platform, Android's half (KaizoCore PC, PLAN row 3). The tracker's files that KaizoCore PC compiles
 * too (desktop/pcui-files.txt: PcTracker.kt and the files it needs) call the platform only through the names below,
 * never android.* themselves. KaizoCore PC's module (pcui, PcPlatformDesktop.kt) declares the same names for a desktop
 * JVM. Here every one is the call those files made before, so the phone draws exactly what it drew.
 */

/** What the tracker's pictures and settings are read through: on the phone, the Context. */
typealias PcCtx = android.content.Context

/** The composition's [PcCtx]: on the phone, LocalContext. */
val LocalPcCtx: androidx.compose.runtime.ProvidableCompositionLocal<PcCtx> get() = androidx.compose.ui.platform.LocalContext

/** One of the app's assets (app/src/main/assets), by its path there. */
fun PcCtx.pcOpenAsset(path: String): InputStream = assets.open(path)

/** The app's own files folder. */
val PcCtx.pcFilesDir: File get() = filesDir

/** The application's context, the one that outlives a screen. */
val PcCtx.pcAppCtx: PcCtx get() = applicationContext ?: this

/** The phone's animator duration scale (Settings.Global.ANIMATOR_DURATION_SCALE), 1 by default. */
fun PcCtx.pcAnimatorScale(): Float =
    android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** A PNG (or any picture BitmapFactory reads) as an ImageBitmap, or null. */
fun pcDecodePicture(input: InputStream): ImageBitmap? = android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()

/** A picture file as an ImageBitmap, or null. */
fun pcDecodePictureFile(path: String): ImageBitmap? = android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()

/** A picture whose ARGB pixels [recolor] changes before it is shown (WalkingPals' shinies); none: as it is. */
fun pcDecodePicture(input: InputStream, recolor: ((IntArray) -> Unit)?): ImageBitmap? {
    val opts = android.graphics.BitmapFactory.Options().apply { inMutable = recolor != null }
    val b = android.graphics.BitmapFactory.decodeStream(input, null, opts)
    if (b != null && recolor != null) {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        recolor(px)
        b.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
    }
    return b?.asImageBitmap()
}

/** [w] x [h] ARGB pixels as an ImageBitmap. */
fun pcPictureOf(argb: IntArray, w: Int, h: Int): ImageBitmap =
    android.graphics.Bitmap.createBitmap(argb, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()

/** The [w] x [h] corner of [img] at [x], [y]. */
fun pcCrop(img: ImageBitmap, x: Int, y: Int, w: Int, h: Int): ImageBitmap =
    android.graphics.Bitmap.createBitmap(img.asAndroidBitmap(), x, y, w, h).asImageBitmap()

/** The tracker's face: Android's sans-serif-condensed (Roboto Condensed). */
fun pcTrackerFontFamily(): FontFamily = FontFamily(
    androidx.compose.ui.text.font.Typeface(
        android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL),
    ),
)

/** The app's pixel face, Press Start 2P (res/font). */
fun pcPixelFontFamily(): FontFamily = FontFamily(Font(R.font.press_start_2p))

/** The tracker's text style: no font padding, so a line occupies exactly what it says it does. */
fun pcTrackerPlatformStyle(): androidx.compose.ui.text.PlatformTextStyle =
    androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
