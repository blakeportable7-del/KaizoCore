package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #36: on Red, Blue, Yellow, Gold, Silver and Crystal the Notebook counted "Pokemon Seen N / 386" and
 * its "Include unseen" list held #152 to #411, drawn with later games' sprites. The list is the game's own now.
 */
class NotebookSpeciesTest {
    @Test
    fun `a Game Boy game's notebook holds its own Pokedex`() {
        assertEquals((1..151).toList(), NotebookSpecies.ids(null, "RBY"))
        assertEquals((1..251).toList(), NotebookSpecies.ids(null, "GSC"))
    }

    @Test
    fun `a GBA game keeps Gen 3's 386, the empty slots left out`() {
        val gba = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, GameMap.EMERALD_U)
        val ids = NotebookSpecies.ids(gba, "RSE")
        assertEquals(386, ids.size)
        assertTrue((252..276).none { it in ids })
        assertEquals(411, ids.last())
        // No tracker and no badge set read yet: what it always was.
        assertEquals(ids, NotebookSpecies.ids(null, null))
    }

    @Test
    fun `the dialog counts and lists what it is given`() {
        val src = File("src/main/kotlin/com/ironmonone/app/Notebook.kt").readText()
        val dialog = src.substringAfter("fun NotebookDialog(")
        assertFalse("?: 386" in dialog, "no Gen 3 total in the dialog")
        assertFalse("(1 until 412)" in dialog, "no Gen 3 list in the dialog")
        assertTrue("val total = speciesIds.size" in dialog)
        assertTrue("if (includeUnseen) speciesIds else tracked.toList()" in dialog)
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertTrue("speciesIds = NotebookSpecies.ids(trackerRef, trackerState?.badgeSet)," in side)
    }
}
