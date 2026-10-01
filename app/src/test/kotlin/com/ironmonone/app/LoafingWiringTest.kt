package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Battle Details shows Loafing only once Truant is tracked for the species
 * (BattleDetailsScreen.lua:1574-1588). The tracked abilities live in StatMarks, so the app must
 * hand them to the Gen 3 tracker; the tracker side is BattleDetailsTest.
 */
class LoafingWiringTest {
    private val dir = Files.createTempDirectory("loaf").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun `the side screens hand StatMarks' tracked abilities to the tracker`() {
        val src = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText().replace("\r\n", "\n")
        assertTrue("SideEffect { trackerRef?.trackedAbilities = statMarks::abilitiesFor }" in src)
        // What the tracker then compares against: the names as they were revealed.
        val marks = StatMarks(File(dir, "marks.txt"))
        marks.revealAbility(313, "TRUANT")
        assertEquals(listOf("TRUANT"), marks.abilitiesFor(313))
    }
}
