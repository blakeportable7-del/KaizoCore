package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P1: cheats were read only when allowed at that moment, so a stale Nuzlocke flag hid a library game's
 * cheats and adding one saved the short list over them; and hardcore switched on mid-game re-applied cheats with the
 * allowance of the moment before, so they ran on under hardcore while the button said CHEATS OFF.
 */
class CheatsAllowanceTest {
    private val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")

    @Test
    fun `the stored cheats are always read, and only applying them is gated`() {
        assertTrue("var cheats by remember(session.id) { mutableStateOf(store.cheats.load(session.id)) }" in play)
        assertFalse("if (cheatsAllowed) store.cheats.load(session.id) else emptyList()" in play)
        assertTrue("if (!cheatsAllowed) return" in play.substring(play.indexOf("fun applyCheats()")))
    }

    @Test
    fun `cheats are applied again whenever the allowance changes`() {
        assertTrue("LaunchedEffect(cheatsAllowed) { if (retro != null && ui.coreUp === retro) applyCheats() }" in play)
    }
}
