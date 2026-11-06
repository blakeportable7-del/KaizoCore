package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The DS bottom screen on a second display: the 4:3 picture, where a touch on it lands, and how it follows the display. */
class DsSecondDisplayTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().crlf()
    private fun String.crlf() = replace("\r\n", "\n")

    @Test
    fun `the picture is 4 by 3, as large as fits, in the middle`() {
        // The AYN Thor's bottom screen, 1240 by 1080: wider than 4:3, so full width, a band above and below.
        assertContentEquals(floatArrayOf(0f, 75f, 1240f, 930f), DsSecondDisplay.band(1240f, 1080f))
        // The same panel reported upright: full width, the bands taller.
        assertContentEquals(floatArrayOf(0f, 215f, 1080f, 810f), DsSecondDisplay.band(1080f, 1240f))
        // A 16:9 screen: full height, bands at the sides.
        assertContentEquals(floatArrayOf(240f, 0f, 1440f, 1080f), DsSecondDisplay.band(1920f, 1080f))
        assertContentEquals(floatArrayOf(0f, 0f, 1024f, 768f), DsSecondDisplay.band(1024f, 768f))
    }

    @Test
    fun `a touch on the picture is the stylus on the bottom screen`() {
        assertEquals(0f to 0.5f, DsSecondDisplay.touchToFrame(0f, 0f, 1024f, 768f), "its top left is the bottom screen's")
        assertEquals(1f to 1f, DsSecondDisplay.touchToFrame(1024f, 768f, 1024f, 768f))
        assertEquals(0.5f to 0.75f, DsSecondDisplay.touchToFrame(512f, 384f, 1024f, 768f))
        // On the Thor's 1240 by 1080: the picture starts 75 down, so 75 is the touch screen's top row and 1005 its last.
        assertEquals(0f to 0.5f, DsSecondDisplay.touchToFrame(0f, 75f, 1240f, 1080f))
        assertEquals(0.5f to 0.75f, DsSecondDisplay.touchToFrame(620f, 540f, 1240f, 1080f))
        assertEquals(1f to 1f, DsSecondDisplay.touchToFrame(1240f, 1005f, 1240f, 1080f))
    }

    @Test
    fun `a touch on the black band around the picture is no stylus`() {
        assertNull(DsSecondDisplay.touchToFrame(620f, 40f, 1240f, 1080f), "above the picture")
        assertNull(DsSecondDisplay.touchToFrame(620f, 1040f, 1240f, 1080f), "below it")
        assertNull(DsSecondDisplay.touchToFrame(100f, 540f, 1920f, 1080f), "beside it")
        assertNull(DsSecondDisplay.touchToFrame(-1f, 10f, 1024f, 768f), "off the view")
        assertNull(DsSecondDisplay.touchToFrame(10f, 10f, 0f, 0f), "a view with no size yet")
    }

    @Test
    fun `the touch info names the DS touch screen pixel`() {
        assertEquals(0 to 0, DsSecondDisplay.dsPixel(0f to 0.5f))
        assertEquals(128 to 96, DsSecondDisplay.dsPixel(0.5f to 0.75f))
        assertEquals(255 to 191, DsSecondDisplay.dsPixel(1f to 1f), "the last pixel, not one past it")
        // A tap at the middle of the Thor's bottom screen is the middle of the DS touch screen.
        assertEquals(128 to 96, DsSecondDisplay.dsPixel(DsSecondDisplay.touchToFrame(620f, 540f, 1240f, 1080f)!!))
    }

    @Test
    fun `a presentation the system dismissed is shown again a few times, not forever`() {
        assertTrue(DsSecondDisplay.mayReshow(emptyList(), 100_000))
        assertTrue(DsSecondDisplay.mayReshow(listOf(95_000, 96_000), 100_000))
        assertFalse(DsSecondDisplay.mayReshow(listOf(95_000, 96_000, 97_000), 100_000), "three in ten seconds is the limit")
        assertTrue(DsSecondDisplay.mayReshow(listOf(80_000, 81_000, 97_000), 100_000), "older ones do not count")
    }

    @Test
    fun `the second display never takes the game's buttons, and its picture is let go before its surface goes`() {
        val ds = src("DsSecondDisplay.kt")
        assertTrue("WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE" in ds, "a touch there must not move the key focus off the game")
        assertTrue("override fun surfaceDestroyed(holder: SurfaceHolder) = retro.setSecondDisplay(null, 0f, 0f, waitMs = 500)" in ds)
        assertTrue("StylusTouch.act(action, trackedUp)" in ds, "the same pointer rule as the phone's screen")
    }

    @Test
    fun `the play screen falls back to both screens on the phone when the display goes`() {
        val play = src("PlayScreen.kt")
        assertTrue("val dsOnSecond = DsBottomOnSecondDisplay(dsWantSecond, retro)" in play, "the phone shows the top only while the bottom is up there")
        assertTrue("if (dsScreens && (dsTopOnly || dsOnSecond))" in play)
        assertTrue("melonds_screen_gap\", if (dsOnSecond) NdsScreens.classicGap(0)" in play)
    }

    @Test
    fun `the native side keeps the window across a new surface or context`() {
        val cpp = File("../libretrodroid/src/main/cpp/seconddisplay.cpp").readText().crlf()
        assertTrue("if (surface != EGL_NO_SURFACE && (context != surfaceContext || currentDisplay != display)) releaseSurface();" in cpp)
        assertTrue("if (!makeSurface(currentDisplay, context)) return;" in cpp, "made at the next frame drawn, with the context current then")
        assertTrue("eglSwapInterval(display, 0);" in cpp, "the second display never holds the game back")
        val owner = File("../libretrodroid/src/main/cpp/libretrodroid.cpp").readText().crlf()
        assertTrue("video->setSecondDisplay(&secondDisplay);" in owner, "a new Video draws the window already set")
    }
}
