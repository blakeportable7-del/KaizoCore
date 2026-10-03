package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two of Play's own follow-ups (rc35 N #11 and N #17). Play is a composable at ART's verifier limit, so what it holds is
 * held to the source, one line each.
 */
class PlayEntryTest {
    private val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")

    @Test
    fun `a DSi system file picked while Android ended the app is kept, N 11`() {
        // Plain remember lost the name of the file being picked, so the pick that came back was dropped.
        assertTrue("var importSystemFileName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }" in play)
        assertFalse("var importSystemFileName by remember {" in play)
    }

    @Test
    fun `the run's notes are read off the main thread, N 17`() {
        // StatMarks reads nine note files in its constructor; it was built in Play's composition.
        assertFalse("StatMarks(store.marksFile(session))" in play)
        assertEquals(1, Regex(Regex.escape("val statMarks = rememberStatMarks(store, session) ?: return")).findAll(play).count())
        val marks = File("src/main/kotlin/com/ironmonone/app/PlayMarks.kt").readText()
        assertTrue("withContext(Dispatchers.IO) { StatMarks(file) }" in marks)
        // Keyed on the notes' file, as it was on the session: a run keeps one through New Run.
        assertTrue("produceState<Pair<File, StatMarks>?>(null, file)" in marks && "loaded?.takeIf { it.first == file }?.second" in marks)
    }
}
