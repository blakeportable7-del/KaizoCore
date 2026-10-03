package com.ironmonone.app

import com.swordfish.libretrodroid.LibretroDroid
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P1: a game the core could not load was a black screen with no word (nothing read the view's errors), and
 * the aborted view never destroyed the native core (onDestroy ran inside catchExceptions, which skips everything once
 * aborted).
 */
class CoreLoadErrorsTest {
    @Test
    fun `each load error has a plain sentence`() {
        val codes = listOf(LibretroDroid.ERROR_LOAD_LIBRARY, LibretroDroid.ERROR_LOAD_GAME, LibretroDroid.ERROR_GL_NOT_COMPATIBLE, LibretroDroid.ERROR_GENERIC)
        val lines = codes.map { CoreLoadErrorCopy.forCode(it) }
        assertEquals(codes.size, lines.toSet().size)
        for (l in lines) assertFalse('\u2014' in l, l)
        assertTrue("CoreLoadErrors(retro, session.isRun, Modifier.align(Alignment.Center))" in File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText())
    }

    /**
     * rc32 audit P2 #53: the line was a toast gone after three seconds, leaving a black screen with nothing saying why,
     * and File > Restart on that view reached a core that was never loaded (a null core when its library failed).
     */
    @Test
    fun `the failure stays on screen with the way out, and Restart cannot reach a core with no game`() {
        val src = File("src/main/kotlin/com/ironmonone/app/CoreLoadErrors.kt").readText().replace("\r\n", "\n")
        val body = src.substringAfter("internal fun CoreLoadErrors(").substringBefore("\n}\n")
        assertTrue("EmptyState(" in body && "CoreLoadErrorCopy.HEADLINE, line" in body, "a panel of its own, not a status line")
        assertTrue("nav?.openMyGames" in body && "nav?.openKaizo" in body)
        assertFalse("onError" in body)
        val native = File("../libretrodroid/src/main/cpp/libretrodroid.cpp").readText().replace("\r\n", "\n")
        val reset = native.substring(native.indexOf("void LibretroDroid::reset() {"), native.indexOf("std::pair<int8_t*, size_t> LibretroDroid::serializeState()"))
        assertTrue(reset.indexOf("if (core == nullptr || !gameLoaded) return;") in 0 until reset.indexOf("core->retro_reset();"))
    }

    @Test
    fun `an aborted view is torn down, and only a loaded game is unloaded`() {
        val view = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText().replace("\r\n", "\n")
        val destroy = view.substring(view.indexOf("    fun onDestroy()"))
        assertFalse(destroy.startsWith("    fun onDestroy() = catchExceptions"), "catchExceptions skips everything once aborted")
        assertTrue("LibretroDroid.destroy()" in destroy.substring(0, 600))
        val native = File("../libretrodroid/src/main/cpp/libretrodroid.cpp").readText().replace("\r\n", "\n")
        assertTrue("if (hadGame) core->retro_unload_game();" in native)
        assertTrue(native.split("coreHasGame = true;").size - 1 >= 2, "set on every load path that succeeds")
    }
}
