package com.ironmonone.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.imageio.ImageIO

/*
 * The PC tracker's platform, the desktop's half (KaizoCore PC, PLAN row 3): the same names as the app's
 * PcPlatformAndroid.kt, so the tracker's own files (pcui/app-files.txt) compile here unchanged. Pictures come from the
 * jar's /assets (copied from app/src/main/assets when it is built), files from KaizoCore PC's run folder, and the text is
 * drawn without anti-aliasing: the panel is drawn at game-pixel size and BizHawk scales it up, so a blended edge would
 * come out soft.
 */

/** What the tracker reads its pictures and files through on the desktop. */
class PcDesktopCtx(val filesDir: File)

typealias PcCtx = PcDesktopCtx

/** The context the panel renders with; KaizoCore PC sets the run's folder. */
@Volatile var pcDesktopCtx: PcDesktopCtx = PcDesktopCtx(File(System.getProperty("java.io.tmpdir"), "kaizocore-pc-files"))

val LocalPcCtx: androidx.compose.runtime.ProvidableCompositionLocal<PcCtx> =
    androidx.compose.runtime.staticCompositionLocalOf { pcDesktopCtx }

fun PcCtx.pcOpenAsset(path: String): InputStream =
    PcDesktopCtx::class.java.getResourceAsStream("/assets/$path") ?: throw FileNotFoundException(path)

val PcCtx.pcFilesDir: File get() = filesDir

val PcCtx.pcAppCtx: PcCtx get() = this

/** No animator scale on a desktop: the panel is a still picture, so "animations off". */
fun PcCtx.pcAnimatorScale(): Float = 0f

private fun argbImage(i: BufferedImage): BufferedImage =
    if (i.type == BufferedImage.TYPE_INT_ARGB) i
    else BufferedImage(i.width, i.height, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(i, 0, 0, null); dispose() } }

fun pcDecodePicture(input: InputStream): ImageBitmap? = ImageIO.read(input)?.let { argbImage(it).toComposeImageBitmap() }

fun pcDecodePictureFile(path: String): ImageBitmap? =
    File(path).takeIf { it.isFile }?.let { ImageIO.read(it) }?.let { argbImage(it).toComposeImageBitmap() }

fun pcDecodePicture(input: InputStream, recolor: ((IntArray) -> Unit)?): ImageBitmap? {
    val img = ImageIO.read(input)?.let(::argbImage) ?: return null
    if (recolor != null) {
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        recolor(px)
        img.setRGB(0, 0, img.width, img.height, px, 0, img.width)
    }
    return img.toComposeImageBitmap()
}

fun pcPictureOf(argb: IntArray, w: Int, h: Int): ImageBitmap =
    BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, w, h, argb, 0, w) }.toComposeImageBitmap()

fun pcCrop(img: ImageBitmap, x: Int, y: Int, w: Int, h: Int): ImageBitmap {
    val px = IntArray(w * h)
    img.readPixels(px, x, y, w, h)
    return pcPictureOf(px, w, h)
}

/** Roboto Condensed, the face Android's sans-serif-condensed is, bundled (OFL, fonts/OFL_roboto_condensed.txt). */
fun pcTrackerFontFamily(): FontFamily = FontFamily(Font("fonts/RobotoCondensed-Regular.ttf"))

/** Press Start 2P, the app's pixel face (OFL), from the app's res/font. */
fun pcPixelFontFamily(): FontFamily = FontFamily(Font("font/press_start_2p.ttf"))

/**
 * Aliased glyphs: crisp at 1x, so BizHawk's scaling keeps them sharp. The hinting and subpixel positioning can be tried
 * with -Dpcui.text=<None|Slight|Normal|Full>,<subpixel true|false> (pcui's samples task).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val PcCrispText: PlatformTextStyle = run {
    // The tracker window shows the card 1:1 at the screen's own pixels: real, smoothed type, as the phone draws it.
    if (System.getProperty("pcui.smooth") == "1") {
        return@run PlatformTextStyle(null, PlatformParagraphStyle(FontRasterizationSettings(FontSmoothing.AntiAlias, FontHinting.Slight, true, false)))
    }
    val spec = System.getProperty("pcui.text", "Slight,true").split(',')
    val hinting = FontHinting.values().firstOrNull { it.toString().equals(spec[0], ignoreCase = true) } ?: FontHinting.Slight
    PlatformTextStyle(null, PlatformParagraphStyle(FontRasterizationSettings(FontSmoothing.None, hinting, spec.getOrNull(1) != "false", false)))
}

fun pcTrackerPlatformStyle(): PlatformTextStyle = PcCrispText
