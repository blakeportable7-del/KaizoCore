package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc35 follow-up N #20: "Save this attempt" said "Unable to save" for a full phone as for anything else, and only a
 * toast said why. The store keeps why it refused, and the tile says it.
 */
class AttemptTileTest {
    @Test
    fun `a full phone is told so on the tile, other failures keep their words`() {
        val dir = Files.createTempDirectory("attempttile").toFile()
        val store = PrepStore(dir)
        val kind = RomKind.EMERALD_U
        store.saveLastRun(kind.id, "RSE Kaizo.rnqs"); store.saveLastSeed(0x5L)
        val seed = store.lastSeedText()
        // No run on disk: a failure, not a want of space.
        assertFalse(store.saveAttempt(kind, 1, seed, null))
        assertFalse(store.attemptShortOfRoom)
        assertEquals(SaveAttemptStatus.FAILED, SaveAttemptStatus.of(false, store.attemptShortOfRoom))
        // A phone without room for the game.
        store.currentRunFor(kind).apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 1 }) }
        store.freeBytes = { 100L }
        assertFalse(store.saveAttempt(kind, 1, seed, null))
        assertTrue(store.attemptShortOfRoom)
        assertEquals(SaveAttemptStatus.NO_ROOM, SaveAttemptStatus.of(false, store.attemptShortOfRoom))
        // Room again: saved, and the reason is gone.
        store.freeBytes = { Long.MAX_VALUE }
        assertTrue(store.saveAttempt(kind, 1, seed, null))
        assertFalse(store.attemptShortOfRoom)
        assertEquals(SaveAttemptStatus.SUCCESS, SaveAttemptStatus.of(true, store.attemptShortOfRoom))
        val label = SaveAttemptStatus.NO_ROOM_LABEL
        assertFalse(label.contains(0x2014.toChar()) || label.contains(0x2013.toChar()) || " - " in label, label)
    }

    @Test
    fun `the game-over popup draws the reason, as the host passes it`() {
        val dialog = File("src/main/kotlin/com/ironmonone/app/GameOverDialog.kt").readText()
        assertTrue("SaveAttemptStatus.NO_ROOM -> SaveAttemptStatus.NO_ROOM_LABEL" in dialog)
        assertTrue("saveScope.launch { saveStatus = onSaveAttempt() }" in dialog)
        val host = File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertTrue("SaveAttemptStatus.of(store.saveAttempt(kind, attempt, seed, state), store.attemptShortOfRoom)" in host)
    }
}
