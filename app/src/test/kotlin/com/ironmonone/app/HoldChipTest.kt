package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #56: REWIND's hold handler is a pointerInput(Unit) block, which keeps the lambdas of the composition
 * that made it. Hardcore turned on with the FILE menu open left REWIND calling the startRewind made before, whose
 * hardcore check was false, so the game rewound in hardcore. REWIND is the FILE bar's FILE sheet's now (HoldButton), and
 * it still reads its callback fresh, and still waits out a scroll before it acts.
 */
class HoldChipTest {
    @Test
    fun `REWIND's button calls the callbacks of the composition it is in`() {
        val bar = File("src/main/kotlin/com/ironmonone/app/FileBar.kt").readText().replace("\r\n", "\n")
        val button = bar.substringAfter("internal fun HoldButton(").substringBefore("\n}\n")
        assertTrue("val hold by rememberUpdatedState(onHold)" in button)
        assertTrue(".holdUnlessScrolled({ held = true; hold(true) }, { held = false; hold(false) })" in button)
        assertFalse("onHold(true)" in button, "never the parameter captured at the first composition")
        val scroll = bar.substringAfter("private fun Modifier.holdUnlessScrolled(").substringBefore("\n    }\n")
        assertTrue("withTimeoutOrNull(150L)" in scroll && "viewConfiguration.touchSlop" in scroll, "a swipe that starts on it scrolls the sheet")
        assertTrue("BarItem.REWIND -> HoldButton(item.label, a.onRewind, modifier)" in bar)
    }
}
