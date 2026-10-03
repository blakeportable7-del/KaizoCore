package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.MoveRow
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Hide stats until summary shown" hides the VIEWED Pokemon, the opponent included
 * (DataHelper.lua:140-151): the reference swaps in a blank stand-in with only its id and level.
 * On the opponent's card that leaves no status, no stat stages and no moves of its own, while
 * its tracked moves stay at base PP. Until 2026-09-29 only your own card was hidden.
 */
class HiddenStatsTest {
    private val dir = Files.createTempDirectory("hide").toFile()
    @AfterTest fun cleanup() {
        TrackerOptions.hideStatsUntilSummary = false
        dir.deleteRecursively()
    }

    private fun row(id: Int, pp: Int) = MoveRow(id, "M$id", pp, null, 40, 100, 0, "PHY")
    private val base = mapOf(33 to row(33, 35), 45 to row(45, 40), 52 to row(52, 25))
    private val enemy = EnemyInfo(
        species = 25, speciesName = "PIKACHU", level = 12, curHp = 20, maxHp = 30, type1 = 13, type2 = 13,
        base = null, movesSeen = emptyList(),
        moveRows = listOf(row(33, 30)),                   // Tackle this battle, 5 PP used
        moves = listOf(33, 45, 0, 0), movePps = listOf(30, 38, 0, 0),
        statStages = mapOf("ATK" to 8, "DEF" to 6, "ACC" to 5), statusCondition = "PAR",
    )
    private val runWide = listOf(StatMarks.SeenMove(52, "M52", 10, 10), StatMarks.SeenMove(33, "M33", 12, 12))

    @Test
    fun `the option hides on a randomized game until this attempt checks a summary`() {
        SummaryChecks.load(File(dir, "summary-checked.txt"))
        assertFalse(SummaryChecks.hides(4, gameDataRandomized = true), "off by default")
        TrackerOptions.hideStatsUntilSummary = true
        assertTrue(SummaryChecks.hides(4, gameDataRandomized = true))
        assertFalse(SummaryChecks.hides(4, gameDataRandomized = false))
        SummaryChecks.mark(4)
        assertFalse(SummaryChecks.hides(4, gameDataRandomized = true))
    }

    @Test
    fun `hidden, the opponent has no status, no stages and no moves of its own`() {
        assertEquals("PAR", EnemyView.status(enemy, hidden = false))
        assertEquals("", EnemyView.status(enemy, hidden = true))
        assertEquals(mapOf("ATK" to 8), EnemyView.stageRows(enemy, hidden = false))
        assertEquals(emptyMap(), EnemyView.stageRows(enemy, hidden = true))
        // Its actual moves at live PP where they may show, none while hidden.
        assertEquals(listOf(33 to 30, 45 to 38), EnemyView.moveRows(enemy, runWide, base::get, actual = true, hidden = false).first.map { it.id to it.pp })
        assertEquals(emptyList(), EnemyView.moveRows(enemy, runWide, base::get, actual = true, hidden = true).first)
        // Tracked moves stay; this battle's live PP gives way to the base PP while hidden.
        assertEquals(listOf(52 to 25, 33 to 30), EnemyView.moveRows(enemy, runWide, base::get, actual = false, hidden = false).first.map { it.id to it.pp })
        assertEquals(listOf(52 to 25, 33 to 35), EnemyView.moveRows(enemy, runWide, base::get, actual = false, hidden = true).first.map { it.id to it.pp })
        assertEquals(2, EnemyView.moveRows(enemy, runWide, base::get, actual = false, hidden = true).second)
    }

    @Test
    fun `both cards take the same rule`() {
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertEquals(2, Regex(Regex.escape("hidden = hideStats,")).findAll(src).count(), "your card and the opponent's")
        assertTrue("val hideStats = if (runScoped) SummaryChecks.hides(attempt, state.gameDataRandomized, generation)" in src)
        assertTrue("status = EnemyView.status(e, hidden)" in src)
        assertTrue("EnemyView.stageRows(e, hidden).forEach" in src)
        assertTrue("EnemyView.moveRows(e, movesSeenRunWide, moveRowFor, actual, hidden)" in src)
    }

    @Test
    fun `turning the option on hides the card again, as GameOptionsScreen does`() {
        SummaryChecks.load(File(dir, "summary-checked.txt"))
        TrackerOptions.hideStatsUntilSummary = true
        SummaryChecks.mark(4)
        assertFalse(SummaryChecks.hides(4, gameDataRandomized = true))
        SummaryChecks.forgetAll()
        assertTrue(SummaryChecks.hides(4, gameDataRandomized = true))
        assertTrue(SummaryChecks.load(File(dir, "summary-checked.txt")).let { SummaryChecks.hides(4, gameDataRandomized = true) }, "and it stays forgotten")
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue("TrackerOptions.hideStatsUntilSummary = it; if (it) SummaryChecks.forgetAll()" in gear)
    }

    @Test
    fun `hidden, your card still shows its species' types, BST, evolution and moves header`() {
        // DataHelper.lua:145-148 keeps the species on the stand-in; :168, :169, :205, :253 draw from it.
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertFalse("typeChips = if (hidden) emptyList()" in src)
        assertTrue("evo = p.evo," in src)
        assertTrue("PcStatRow(\"BST\", p.base?.bst?.toString() ?: \"?\"" in src)
        assertTrue("header = if (p.movesTotal > 0) \"Moves ${'$'}{p.movesLearned}/${'$'}{p.movesTotal}\" else \"Moves\"" in src)
        assertTrue("if (hidden) emptyList() else p.moveRows" in src, "the moves themselves stay hidden")
        // DataHelper.lua:402-406: the blank stand-in's catch rate is 0.
        assertTrue("if (hideStats) 0 else pct" in src)
    }
}
