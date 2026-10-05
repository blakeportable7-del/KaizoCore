package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The PC tracker's side panels in the Gen 3 log viewer (LogInfoPanels): TrainerInfoScreen's lines on a trainer's page,
 * InfoScreen POKEMON_INFO's Weak to, Show resistances, History and note on a Pokemon's page (Blake, 2026-10-04, with PC
 * screenshots of Nat. Dex FireRed).
 */
class LogInfoPanelsTest {
    private fun trainer(
        levels: List<Int>, ivs: List<Int> = levels.map { 0 }, ai: Int = 0, double: Boolean = false, items: List<Int> = emptyList(),
    ) = GbaTracker.TrainerInfo(
        id = 410, className = "Elite Four", name = "Lorelei",
        party = levels.mapIndexed { i, lv -> GbaTracker.TrainerMon(species = 100 + i, level = lv, ivs = ivs[i], heldItem = 0, moves = emptyList()) },
        aiFlags = ai, doubleBattle = double, defeated = false, items = items,
    )

    private val items = mapOf(19 to "Full Restore", 21 to "Hyper Potion")
    private fun itemName(id: Int) = items[id]

    @Test fun `the trainer lines are TrainerInfoScreen's`() {
        val p = LogInfoPanels.trainer(trainer(listOf(52, 51, 54, 54), ivs = listOf(31, 31, 30, 30), ai = 7, double = true,
            items = listOf(21, 19, 21, 0)), leadLevel = 50, itemName = ::itemName)
        // TrainerInfoScreen.lua:240-250: "N  (Lv.min -- max)".
        assertEquals("4  (Lv.51 -- 54)", p.team)
        assertTrue(p.leadBelow, "a lead below the lowest level is drawn red")
        // :258-263: floor of the mean.
        assertEquals(30, p.avgIvs)
        // :273: AI_SCRIPT_TRY_TO_FAINT wins over the lower flags.
        assertEquals("Smart", p.script)
        // Blake, 2026-10-05: the PC tracker's word for the line.
        assertEquals("AI Script", LogInfoPanels.SCRIPT_LABEL)
        // :290-311: each item once with its count, sorted as text, an id with no name left out.
        assertEquals("2 Hyper Potion, Full Restore".split(", ").sorted().joinToString(", "), p.items)
        assertTrue(p.doubleBattle)
        assertEquals(410, p.id)
    }

    @Test fun `one level, a lead at it, no items`() {
        val p = LogInfoPanels.trainer(trainer(listOf(5)), leadLevel = 5, itemName = ::itemName)
        assertEquals("1  (Lv.5)", p.team)
        assertFalse(p.leadBelow)
        assertNull(p.items, "no Usable Items line")
        assertFalse(LogInfoPanels.trainer(trainer(listOf(5)), leadLevel = null, itemName = ::itemName).leadBelow)
    }

    @Test fun `the script label follows the flags`() {
        fun label(flags: Int) = LogInfoPanels.trainer(trainer(listOf(10), ai = flags), null, ::itemName).script
        assertEquals("Dumb", label(0))
        assertEquals("Normal", label(1))
        assertEquals("Semi-Smart", label(3))
        assertEquals("Smart", label(4))
        assertEquals("Complex", label(8))
    }

    @Test fun `weak to is the 2x and 4x types from the log's typing`() {
        val parasect = LogInfoPanels.weakTo(listOf("Bug", "Grass"), natDex = false)
        assertEquals(setOf("Fire", "Flying"), parasect.filter { it.quad }.map { it.type }.toSet())
        assertEquals(setOf("Ice", "Poison", "Rock", "Bug"), parasect.filter { !it.quad }.map { it.type }.toSet())
        // Sableye: "Has no weaknesses" (InfoScreen.lua:818).
        assertEquals(emptyList(), LogInfoPanels.weakTo(listOf("Dark", "Ghost"), natDex = false))
        // One type is that type twice.
        assertEquals(setOf("Fighting"), LogInfoPanels.weakTo(listOf("Normal"), natDex = false).map { it.type }.toSet())
        // Nat. Dex's chart has Fairy.
        assertTrue(LogInfoPanels.weakTo(listOf("Dragon"), natDex = true).any { it.type == "Fairy" })
        assertFalse(LogInfoPanels.weakTo(listOf("Dragon"), natDex = false).any { it.type == "Fairy" })
    }

    @Test fun `show resistances is the type defenses table`() {
        val d = LogInfoPanels.defenses(listOf("Normal", "Ghost"), natDex = false)
        assertEquals(setOf("Normal", "Fighting", "Ghost"), d[0.0].orEmpty().toSet())
        assertEquals(listOf("Dark"), d[2.0])
        assertEquals(emptyMap(), LogInfoPanels.defenses(emptyList(), natDex = false))
    }

    @Test fun `the note line is the note or Leave a note`() {
        assertEquals("(Leave a note)", LogInfoPanels.noteLine(""))
        assertEquals("Has Ice Beam", LogInfoPanels.noteLine("Has Ice Beam"))
    }

    @Test fun `a note left from the log is the run's note, and History reads the run's tracked moves`() {
        val dir: File = Files.createTempDirectory("lognote").toFile()
        val marks = StatMarks(File(dir, "marks.txt"))
        RunMarks.current = marks
        try {
            // What the log's note dialog does on Save, through the holder Play fills.
            RunMarks.current?.setNote(470, "Leafeon\nhas Swords Dance")
            assertEquals("Leafeon has Swords Dance", marks.noteFor(470), "Play's own StatMarks has it")
            assertTrue(DiskWriter.drain())
            assertEquals("Leafeon has Swords Dance", StatMarks(File(dir, "marks.txt")).noteFor(470), "and it is on disk")
            marks.addMovesSeen(470, listOf(14 to "Swords Dance"), 54)
            assertEquals(listOf("Swords Dance"), RunMarks.current?.movesSeenFor(470)?.map { it.name })
        } finally {
            RunMarks.current = null
        }
    }

    @Test fun `Play fills the holder for a run, and the log viewer reads it`() {
        val marks = File("src/main/kotlin/com/ironmonone/app/PlayMarks.kt").readText()
        assertTrue("if (session.isRun) RunMarks.current = it" in marks)
        val viewer = File("src/main/kotlin/com/ironmonone/app/LogViewer.kt").readText()
        assertTrue("val runMarks = RunMarks.current" in viewer)
        assertTrue("runMarks?.setNote(sp, text)" in viewer)
        assertTrue("runMarks?.movesSeenFor(sp)" in viewer)
    }
}
