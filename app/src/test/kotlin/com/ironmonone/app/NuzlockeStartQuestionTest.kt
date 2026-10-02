package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc33 audit P0-6: a randomized Nuzlocke asked first only when the run in play was the same game, so a Kaizo IronMON
 * run of another game was filed as ended without a word. There is one run slot; any run in play is asked about now,
 * by name when it is another game, as the Kaizo screen asks.
 */
class NuzlockeStartQuestionTest {
    @Test
    fun `any run in play is asked about, by name when it is another game`() {
        assertNull(NuzlockeStarts.replaceQuestion(RomKind.FIRERED_U_V11, RomKind.EMERALD_U, runInPlay = false))
        assertEquals("Randomize a new ${RomKind.FIRERED_U_V11.displayName}? The run in play ends, and so does its ledger.",
            NuzlockeStarts.replaceQuestion(RomKind.FIRERED_U_V11, RomKind.FIRERED_U_V11, runInPlay = true))
        assertEquals("Randomize a new ${RomKind.FIRERED_U_V11.displayName}? Your ${RomKind.EMERALD_U.displayName} run in play ends.",
            NuzlockeStarts.replaceQuestion(RomKind.FIRERED_U_V11, RomKind.EMERALD_U, runInPlay = true))
        // A run whose game is not known any more is still a run in play.
        assertTrue(NuzlockeStarts.replaceQuestion(RomKind.EMERALD_U, null, runInPlay = true) != null)
        for (k in listOf(RomKind.EMERALD_U, RomKind.FIRERED_U_V11))
            assertFalse('\u2014' in NuzlockeStarts.replaceQuestion(k, RomKind.PLATINUM_U, true)!!)
    }

    @Test
    fun `the screen asks whenever the run slot holds a run, not only the same game's`() {
        val screen = File("src/main/kotlin/com/ironmonone/app/NuzlockeScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("NuzlockeStarts.replaceQuestion(kind, store.loadLastRun()?.first?.let { RomKind.byId(it) }, store.currentRun.exists())" in screen)
        assertFalse("store.loadLastRun()?.first == kind.id && store.currentRunFor(kind).isFile" in screen)
    }
}
