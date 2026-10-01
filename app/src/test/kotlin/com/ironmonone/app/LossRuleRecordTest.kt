package com.ironmonone.app

import com.ironmonone.tracker.LossCondition
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IronMON rules check R12 (2026-09-30): a run played under a game-over rule other than its settings file's own says so
 * on its record, its card and the shared line, and a change during the run goes on its log.
 */
class LossRuleRecordTest {
    @Test
    fun `the record keeps a rule other than the file's own, or that it was changed on the way`() {
        assertEquals("Entire party faints", lossRuleAtEnd(LossCondition.ENTIRE_PARTY, "FRLG Kaizo.rnqs", "Entire party faints", changedOnTheWay = true))
        assertEquals("", lossRuleAtEnd(LossCondition.LEAD, "FRLG Kaizo.rnqs", "Lead Pokemon faints", changedOnTheWay = false), "Kaizo's own rule")
        assertEquals(LOSS_RULE_CHANGED, lossRuleAtEnd(LossCondition.LEAD, "FRLG Kaizo.rnqs", "Lead Pokemon faints", changedOnTheWay = true))
        assertEquals("", lossRuleAtEnd(LossCondition.HIGHEST_LEVEL, "RBY Survival.rnqs", "Highest level faints", false), "RBY Survival's own")
        assertEquals("", lossRuleAtEnd(LossCondition.ENTIRE_PARTY, "HGSS Standard.rnqs", "Entire party faints", false), "Standard's own")
        assertEquals("", lossRuleAtEnd(LossCondition.ENTIRE_PARTY, "", "Entire party faints", true), "no file, nothing to compare with")

        assertNull(lossRuleText(""))
        assertEquals("game over when entire party faints", lossRuleText("Entire party faints"))
        assertEquals("game over rule changed during the run", lossRuleText(LOSS_RULE_CHANGED))

        val r = RunRecord(attempt = 3, seed = "aa", ruleset = "FRLG Kaizo.rnqs", started = 0, ended = 1, playSeconds = 0,
            outcome = RunRecord.Outcome.WON, badges = 8, lead = null, killer = null, trainer = "", location = "", lossRule = "Entire party faints")
        assertEquals("Entire party faints", RunRecord.decode(r.encode())!!.lossRule)
        assertEquals("", RunRecord.decode(r.encode().split('\t').take(19).joinToString("\t"))!!.lossRule, "a line from before")
        assertTrue(DeathCard("FireRed", r, null, false).statsText().endsWith(", game over when entire party faints"))
        assertTrue("(FRLG Kaizo, game over when entire party faints)" in runSummaryLine(r, "FireRed"), runSummaryLine(r, "FireRed"))
    }

    @Test
    fun `a change during a run to another rule goes on its log, and a change back to the file's own does not`() {
        val filesDir = Files.createTempDirectory("rulelog").toFile()
        val store = PrepStore(filesDir)
        val log = File(filesDir, "prep/integrity.txt")
        store.library.selectRun()
        RunRuleLog.changed(filesDir, LossCondition.LEAD, "FRLG Kaizo.rnqs")
        assertTrue(RunEvents(log).entries().isEmpty(), "back to the file's own rule is nothing to note")
        RunRuleLog.changed(filesDir, LossCondition.ENTIRE_PARTY, "FRLG Kaizo.rnqs", at = 9L)
        val e = RunEvents(log).entries().single()
        assertEquals(RunEvents.Kind.RULE, e.kind); assertEquals("Entire party faints", e.detail); assertEquals(9L, e.at)
        // Tracker Setup writes it for both kinds of game.
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue("RunRuleLog.changed(filesDir, c, runSettingsName, TrackerOptions.dsLossLabel(c))" in gear)
        assertTrue("RunRuleLog.changed(filesDir, c, runSettingsName) }" in gear)
    }
}
