package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P0-10: the stream page's snapshot was built on a background thread and read StatMarks' live move lists
 * while a battle added to them on the main thread: a rare ConcurrentModificationException. The snapshot is now built
 * on the main thread, where StatMarks is written, and only the JSON is written off it; movesSeenFor hands out a copy.
 */
class StreamSnapshotThreadTest {
    @Test
    fun `movesSeenFor is a copy, not the list a battle keeps adding to`() {
        val m = StatMarks(File(Files.createTempDirectory("sm").toFile(), "marks.txt"))
        m.addMovesSeen(25, listOf(84 to "Thunder Shock"), 5)
        val seen = m.movesSeenFor(25)
        m.addMovesSeen(25, listOf(98 to "Quick Attack"), 6)
        assertEquals(listOf("Thunder Shock"), seen.map { it.name }, "what was handed out does not change under its reader")
        assertEquals(listOf("Quick Attack", "Thunder Shock"), m.movesSeenFor(25).map { it.name })
    }

    @Test
    fun `the snapshot is built on the main thread and only the JSON off it`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val build = play.indexOf("val snap = com.ironmonone.app.stream.StreamSnapshot.build(run, gba, nds, notes, ref)")
        val off = play.indexOf("withContext(kotlinx.coroutines.Dispatchers.Default) { com.ironmonone.app.stream.Json.write(snap) }")
        assertTrue(build in 0 until off)
        assertFalse("StreamSnapshot.build(run, gba, nds, notes, ref))\n        }" in play, "no build left inside the background block")
    }
}
