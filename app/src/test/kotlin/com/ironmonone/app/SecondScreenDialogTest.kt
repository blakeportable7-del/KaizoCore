package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * rc33 audit P0-7: with the tracker on the second screen and "Show starter ball info" on, the lab's ball confirm
 * opened PcPokemonInfo, a Compose Dialog, from the presentation window's composition, which has no window token on
 * Android 12 and later: a crash. The second screen now marks itself (LocalOnSecondScreen) and the starter info, the one
 * dialog the tracker opens by itself from game state, is not opened there.
 */
class SecondScreenDialogTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `the second screen marks itself and the starter info stays off it`() {
        assertTrue("LocalOnSecondScreen provides true" in src("SecondScreen.kt"))
        assertTrue("val LocalOnSecondScreen = androidx.compose.runtime.staticCompositionLocalOf { false }" in src("TrackerBackdrop.kt"))
        val panel = src("TrackerPanel.kt")
        val gate = "if (starter != null && starter != starterClosed && !LocalOnSecondScreen.current) {"
        assertTrue(gate in panel)
        // The gate is the one in front of the starter's PcPokemonInfo.
        val at = panel.indexOf(gate)
        assertTrue(panel.indexOf("PcPokemonInfo(", at) - at in 1..200)
    }
}
