package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc35 follow-up N #9: Report a bug's "Game family" line took the last Kaizo run's family whatever the player had in
 * Play, the guess rc32 audit P3 #25 took out of crash reports. It names the game Play has now, as a crash report does.
 */
class BugReportGameTest {
    @Test
    fun `the report names the game in Play, its console for one the tracker does not read, none with no game`() {
        val dir = Files.createTempDirectory("bugreport").toFile()
        val store = PrepStore(dir)
        assertNull(Feedback.gameInPlay(store), "no run and no game picked: none")
        // A Kaizo run of Emerald, in Play.
        store.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs")
        store.currentRunFor(RomKind.EMERALD_U).apply { parentFile.mkdirs(); writeBytes(ByteArray(8)) }
        assertEquals(RomKind.EMERALD_U.family, Feedback.gameInPlay(store))
        // The player then picked a DS game from the library that no tracker reads: its console, not the run's family.
        val lib = store.library.import("kart.nds", LibraryRoms.ds("MARIO KART", "AMCE"))
        store.library.selectLibrary(lib)
        assertEquals("DS", Feedback.gameInPlay(store))
        assertTrue(Feedback.compose(Feedback.Device("Pixel", "Android 14", "rc34"), Feedback.gameInPlay(store), "x", "").contains("Game family: DS"))
    }

    @Test
    fun `About's report asks Play's game, not the last run`() {
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        val build = about.substringAfter("fun report(device: Feedback.Device): String = Feedback.compose(").substringBefore("\n                )")
        assertTrue("Feedback.gameInPlay(store)" in build)
        assertFalse("loadLastRun" in build)
    }
}
