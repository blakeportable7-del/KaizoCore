package com.ironmonone.app

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P0-11: a big DS game loads on the GL thread, and leaving Play during the load made GLSurfaceView's
 * teardown wait on the main thread: an ANR. The tabs now hold Play while its game is loading, for 20 s at most.
 */
class PlayLoadingTest {
    @AfterTest fun reset() = PlayLoading.clear()

    @Test
    fun `Play is held while its game loads, and never for more than the cap`() {
        assertFalse(PlayLoading.holds(1_000))
        PlayLoading.note(true, now = 1_000)
        PlayLoading.note(true, now = 4_000)                 // recomposed mid-load: the start stays the start
        assertTrue(PlayLoading.holds(5_000))
        assertTrue(PlayLoading.holds(1_000 + PlayLoading.MAX_HOLD_MS - 1))
        assertFalse(PlayLoading.holds(1_000 + PlayLoading.MAX_HOLD_MS), "a flag that never cleared cannot trap the player")
        PlayLoading.note(false, now = 6_000)
        assertFalse(PlayLoading.holds(6_000), "the game is up")
        PlayLoading.note(true, now = 7_000); PlayLoading.clear()
        assertFalse(PlayLoading.holds(7_500), "Play left")
        assertFalse('\u2014' in PlayLoading.WAIT)
    }

    @Test
    fun `the load is noted where Play already holds its size, cleared when Play goes, and asked by the tabs`() {
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("this.also { PlayLoading.note(loading) }" in src("SideScreens.kt"))
        val play = src("PlayScreen.kt")
        assertTrue(".holdSizeWhileLoading(ui, loading = retro == null || ui.coreUp !== retro)" in play)
        assertTrue("onDispose {\n            PlayLoading.clear()" in play)
        assertTrue("if (tab == Tab.PLAY && t != Tab.PLAY && PlayLoading.holds()) {" in src("MainActivity.kt"))
    }
}
