package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #56: REWIND's hold handler is a pointerInput(Unit) block, which keeps the lambdas of the composition
 * that made it. Hardcore turned on with the FILE menu open left REWIND calling the startRewind made before, whose
 * hardcore check was false, so the game rewound in hardcore. PadButton already reads its callback fresh.
 */
class HoldChipTest {
    @Test
    fun `REWIND's chip calls the callbacks of the composition it is in`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val chip = play.substringAfter("private fun HoldChip(").substringBefore("\n}\n")
        assertTrue("val down by androidx.compose.runtime.rememberUpdatedState(onDown)" in chip)
        assertTrue("val up by androidx.compose.runtime.rememberUpdatedState(onUp)" in chip)
        assertTrue(".holdUnlessScrolled({ held = true; down() }, { held = false; up() })" in chip)
        assertFalse("onDown() }" in chip, "never the parameter captured at the first composition")
    }
}
