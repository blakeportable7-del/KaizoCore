package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #88: both restore questions said "ROMs are untouched" while a restore puts back the run's randomized game
 * and every saved attempt's (Backup admits prep/runs/ and attempts/), and the wiki and the questions are to say what the
 * code does now: ask, put the files back, restart at once (AboutScreen calls restartApp after a restore that worked).
 */
class RestoreWordsTest {
    private val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")

    @Test
    fun `the questions say the run's games come back, library games stay, and the app restarts`() {
        assertFalse("ROMs are untouched" in about)
        for (q in listOf(BackupCopy.RESTORE, CloudSyncCopy.RESTORE)) {
            assertTrue("Your library games are untouched." in q, q)
            assertTrue("runs and their games" in q && "saved attempts" in q, q)
            assertTrue("KaizoCore restarts" in q, q)
            assertFalse(q.contains(0x2014.toChar()) || q.contains(0x2013.toChar()) || " - " in q || "ROM" in q, q)
        }
        assertTrue("Text(BackupCopy.RESTORE," in about, "the file restore asks with it")
        assertTrue("Text(CloudSyncCopy.RESTORE," in about, "and the cloud restore")
        // What the questions promise is what the buttons do: each successful restore restarts the app.
        assertTrue("if (n != null && n >= 0) { needsRestart = true; restartApp(context) }" in about)
        assertTrue("CloudSync.restoreDone(context.filesDir); needsRestart = true; linkedToRestore = false; restartApp(context)" in about)
        // The backup holds what the question says comes back.
        val backup = File("src/main/kotlin/com/ironmonone/app/Backup.kt").readText()
        assertTrue("\"prep/runs/\"" in backup && "\"attempts/\"" in backup)
    }

    @Test
    fun `the wiki says the same`() {
        val wiki = File("../docs/wiki/Saves-backups-and-updates.md").readText().replace("\r\n", "\n").replace("\n", " ")
        assertTrue("**Restore** asks first, then puts the backup's files in place and restarts the app by itself." in wiki)
        assertTrue("your library games are left as they are" in wiki)
        assertTrue("**Restore from cloud** asks first and restarts the app, as **Restore** does." in wiki)
    }
}
