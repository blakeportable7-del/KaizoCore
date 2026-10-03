package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #56 and P3 #55: Play asked PrepStore for the attempt number in composition, five times on each tracker
 * change in landscape and once more in each poll, and every ask read lastrun.txt twice and a count file on the main
 * thread and compiled the file-name pattern again.
 */
class PlayAttemptReadsTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `Play reads the attempt and seed once per run`() {
        val play = src("PlayScreen.kt")
        assertEquals(0, Regex(Regex.escape("store.attempt()")).findAll(play).count(), "every reader takes the remembered value")
        assertTrue("val runNow = rememberRunIds(store, gameKeyForRom)" in play)
        assertFalse("store.lastSeed()" in play)
        assertTrue("currentSeed = runNow.seed.ifEmpty { session.id }" in play)
        assertTrue("internal fun rememberRunIds(store: PrepStore, runKey: Int): RunIds = remember(runKey) { RunIds(store.attempt(), store.lastSeedText()) }" in src("PlayRules.kt"))
        // The polls hand KeptSave the number; it no longer reads it from disk at every poll.
        assertFalse("store.attempt()" in src("KeptSave.kt"))
        assertEquals(3, Regex(Regex.escape("RunClock.tick(session.kind?.id?.takeIf { session.isRun }, { runNow.attempt },")).findAll(play).count())
    }

    /** rc32 audit P2 #55: the second display printed "ATTEMPT N", the last Kaizo run's number, in any game. */
    @Test
    fun `the second display shows the panel's own attempt line, a Kaizo run's only`() {
        val play = src("PlayScreen.kt")
        assertFalse("Text(\"ATTEMPT" in play, "no ungated ATTEMPT line of Play's own")
        // The panels print theirs behind ironmonRunInPlay, and the second display is not the window's title.
        for (panel in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt"))
            assertTrue("val attemptShown = attempt.takeIf { !LocalAttemptInTitle.current && ironmonRunInPlay(it) }" in src(panel), panel)
    }

    @Test
    fun `the file-name pattern is compiled once`() {
        val prep = src("PrepStore.kt")
        assertEquals(1, Regex(Regex.escape("Regex(\"[^A-Za-z0-9._-]\")")).findAll(prep).count())
        assertTrue("private fun safeName(s: String) = s.replace(UNSAFE_NAME, \"_\")" in prep)
    }

    @Test
    fun `the attempt still counts per settings file`() {
        // The pattern moved; what it does did not: a name with a space and a slash keeps one count file of its own.
        val dir = Files.createTempDirectory("attempt").toFile()
        val store = PrepStore(dir)
        File(dir, "prep").mkdirs()
        File(dir, "prep/lastrun.txt").writeText("${RomKind.EMERALD_U.id}\nKaizo run/one.rnqs\n")
        assertEquals(0, store.attempt())
        assertEquals(1, store.bumpAttempt())
        assertEquals(1, store.attempt())
        assertTrue(File(dir, "prep/attempts/${RomKind.EMERALD_U.id}/Kaizo_run_one.rnqs.txt").isFile)
    }
}
