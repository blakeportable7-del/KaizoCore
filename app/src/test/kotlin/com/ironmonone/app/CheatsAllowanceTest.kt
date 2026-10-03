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
        assertTrue("r.setCheats(CheatStore.forCore(cheats, platform, cheatsAllowed))" in play.substring(play.indexOf("fun applyCheats()")))
        // Not allowed: the core is handed none, which leaves it with none.
        val codes = listOf(CheatStore.Cheat("Infinite money", "82025BC4 FFFF", true))
        assertTrue(CheatStore.forCore(codes, com.ironmonone.core.Platform.GBA, allowed = false).isEmpty())
        assertTrue(CheatStore.forCore(codes, com.ironmonone.core.Platform.GBA, allowed = true).isNotEmpty())
    }

    /**
     * rc32 audit P3 #49: a reset and a set per code each waited on the emulation thread from the main one, so with that
     * thread stalled three codes held the main thread for about eight seconds, past Android's five.
     */
    @Test
    fun `the whole list goes to the core in one emulation-thread job, in order`() {
        val list = listOf(
            CheatStore.Cheat("a", "82025bc4 ffff", true),
            CheatStore.Cheat("not a code", "hello", true),
            CheatStore.Cheat("b", "3300 1234\n\n  8200 0001 ", false),
        )
        kotlin.test.assertEquals(listOf(true to "82025BC4 FFFF", false to "3300 1234+8200 0001"),
            CheatStore.forCore(list, com.ironmonone.core.Platform.GBA, allowed = true), "disabled codes kept as off, indices in order, the bad one left out")
        val apply = play.substring(play.indexOf("fun applyCheats()")).substringBefore("\n")
        assertFalse("r.setCheat(" in apply || "r.resetCheat(" in apply, "no call per code")
        val view = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText().replace("\r\n", "\n")
        val set = view.substringAfter("fun setCheats(").substringBefore("\n    }\n")
        assertTrue("runOnEmulationThread(useEmulationThread, Unit) {\n            LibretroDroid.resetCheat()\n            cheats.forEachIndexed { i, (enabled, code) -> LibretroDroid.setCheat(i, enabled, code) }" in set)
    }

    @Test
    fun `cheats are applied again whenever the allowance changes`() {
        assertTrue("LaunchedEffect(cheatsAllowed) { if (retro != null && ui.coreUp === retro) applyCheats() }" in play)
    }
}
