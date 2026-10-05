package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.nuzlocke.NuzlockeNotes
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): a Nuzlocke on it is a Gen 3 one with
 * a rules page of its own (its sixteen badges and Red, what the ledger cannot see, the hack's own Nuzlocke option).
 */
class HnsNuzlockeTest {
    @Test
    fun `a Heart and Soul Nuzlocke carries its own notes, and the other Gen 3 games still none`() {
        val hns = RomKind.HEARTSOUL_KAIZO_206
        assertEquals(NuzlockeSystem.GEN3, NuzlockeStarts.systemOf(hns))
        assertEquals(NuzlockeNotes.HEART_SOUL, NuzlockeStarts.gameKeyOf(hns))
        val notes = NuzlockeNotes.forGame(NuzlockeStarts.systemOf(hns), NuzlockeStarts.gameKeyOf(hns))
        assertFalse(notes.isEmpty)
        val all = (notes.automatic + notes.byHand).joinToString("\n")
        for (want in listOf("Kanto", "Red", "Nuzlocke option of its own", "Cherrygrove")) assertTrue(want in all, want)
        assertTrue('\u2014' !in all)
        assertEquals("", NuzlockeStarts.gameKeyOf(RomKind.EMERALD_U))
        assertTrue(NuzlockeNotes.forGame(NuzlockeSystem.GEN3, "").isEmpty)
    }
}
