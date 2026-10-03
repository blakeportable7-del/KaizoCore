package com.ironmonone.app

import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * rc35 follow-up N #28: Random Evos and Evo Data printed their shares, and Statistics its playtime, with String.format in
 * the phone's own language, so a German phone showed 12,50% among English words.
 */
class EvoShareTest {
    @Test
    fun `a share prints with a point whatever the phone's language`() {
        val was = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12,50", String.format("%.2f", 12.5), "the phone's language would give a comma")
            assertEquals("12.50%", evoShare(12.5))
            assertEquals("0.13%", evoShare(0.125))
        } finally {
            Locale.setDefault(was)
        }
    }

    @Test
    fun `no number on those screens is formatted in the phone's language`() {
        for (f in listOf("RandomEvos.kt", "PastRuns.kt")) {
            val text = File("src/main/kotlin/com/ironmonone/app/$f").readText()
            assertFalse(Regex("String\\.format\\(\"").containsMatchIn(text), "$f formats in the phone's language")
        }
    }
}
