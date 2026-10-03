package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 2026-09-30, the feature check before the write-up: five Tracker Setup switches were listed on DS games and did
 * nothing there, Coverage calc opened nothing on a Game Boy game, and the backup screen said "ROMs are never
 * included" while the current run's randomized game is in the backup (so a restored run comes back whole).
 */
class SetupShowsWhatWorksTest {
    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { if (File(it, "src/main/kotlin").isDirectory) it else File(it, "app") }
        .first { File(it, "src/main/kotlin").isDirectory }
    private fun read(name: String) = File(app, "src/main/kotlin/com/ironmonone/app/$name").readText()

    @Test
    fun switchesTheDsPanelIgnoresAreNotOfferedOnDs() {
        val gear = read("TrackerGearDialog.kt")
        for (label in listOf("Show team view", "Type matchups in move info")) {
            val line = gear.lines().single { "GearToggle(\"$label\"" in it }.trim()
            assertTrue(line.startsWith("if (!ds) GearToggle("), "$label is Gen 1 to 3 only")
        }
        // Reveal and Open Book act on randomized games (2026-10-01): a Game Boy game's randomization is not known.
        for (label in listOf("Reveal info if randomized", "Open Book Play Mode")) {
            val line = gear.lines().single { "GearToggle(\"$label\"" in it }.trim()
            assertTrue(line.startsWith("if (!ds && (gameBoy || scope.randomized)) GearToggle("), "$label is Gen 1 to 3, randomized")
        }
        val hide = gear.lines().single { "GearToggle(\"Hide stats until summary shown\"" in it }.trim()
        assertTrue(hide.startsWith("if (!gameBoy && !ds && scope.randomized) GearToggle("), "no summary screen is read on Game Boy or DS")
    }

    @Test
    fun coverageCalcIsNotOfferedOnGameBoyGames() {
        val line = read("TrackerGearDialog.kt").lines().single { "GearButton(\"COVERAGE CALC\")" in it }.trim()
        assertTrue(line.startsWith("if (!gameBoy) "))
    }

    @Test
    fun theBackupWordsAreTrue() {
        val hits = File(app, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().let { "ROMs are never included" in it || "ROMs never go up" in it } }
            .map { it.name }.toList()
        assertEquals(emptyList(), hits, "a backup holds the current run's randomized game")
        val about = read("AboutScreen.kt")
        // The card's words are BackupCopy.CARD now (rc32 audit P2 #2), wrapped over several source lines: read the value.
        assertTrue("Text(BackupCopy.CARD" in about)
        assertTrue("Your library games are never in it" in BackupCopy.CARD)
        assertTrue("so a restored run comes back whole" in BackupCopy.CARD)
        assertFalse("ROMs" in about.substringAfter("Text(\"Backup\"").substringBefore("Gen3Button(\"Back up\""))
    }
}
