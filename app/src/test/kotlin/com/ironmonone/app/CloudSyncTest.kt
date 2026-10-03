package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CloudSyncTest {

    @Test
    fun `the link round-trips and is not part of the backup`() {
        val l = CloudSync.Link("content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D42", "Google Drive", 1700000000000L, "abc")
        assertEquals(l, CloudSync.parse(CloudSync.format(l)))
        assertNull(CloudSync.parse(""))
        assertEquals(CloudSync.Link("content://x", "", 0L, ""), CloudSync.parse("content://x\n"))
        // A restore onto another phone must not carry this phone's link.
        assertFalse(Backup.admits(CloudSync.CONFIG))
        val dir = Files.createTempDirectory("cs").toFile()
        CloudSync.save(dir, l); assertEquals(l, CloudSync.load(dir))
        CloudSync.save(dir, null); assertNull(CloudSync.load(dir))
    }

    /**
     * rc33 audit P0-9: "Use my file from another phone" saved the link before the restore was confirmed, and the
     * confirm lived only in the screen's memory. If Android ended the app first, the next sync wrote this nearly
     * empty phone over the old phone's backup. The link now carries the restore as pending, on disk, in one write;
     * sync writes nothing while it is set, and only a restore that went through clears it.
     */
    @Test
    fun `a file linked to restore from is never written until the restore went through`() {
        val dir = Files.createTempDirectory("cs").toFile()
        val pending = CloudSync.Link("content://x/doc", "Google Drive", 0L, "", restorePending = true)
        assertEquals(pending, CloudSync.parse(CloudSync.format(pending)))
        CloudSync.save(dir, pending)
        kotlin.test.assertTrue(CloudSync.load(dir)!!.restorePending, "the mark survives the app being ended")
        // A link file written before rc33 (four lines) reads as not pending.
        assertFalse(CloudSync.parse("content://x\nDrive\n0\nfp\n")!!.restorePending)
        CloudSync.restoreDone(dir)
        assertFalse(CloudSync.load(dir)!!.restorePending)
        assertEquals(pending.copy(restorePending = false), CloudSync.load(dir))

        val sync = File("src/main/kotlin/com/ironmonone/app/CloudSync.kt").readText().replace("\r\n", "\n")
        val body = sync.substring(sync.indexOf("fun sync(context: Context"))
        val load = body.indexOf("val link = load(context.filesDir) ?: return Result.NotLinked")
        val refuse = body.indexOf("if (link.restorePending) return Result.RestorePending")
        val write = body.indexOf("openOutputStream")
        kotlin.test.assertTrue(load in 0 until refuse && refuse < write, "sync refuses before it opens the file")
        kotlin.test.assertTrue("fun linkForRestore(context: Context, uri: Uri): Link = link(context, uri, restorePending = true)" in sync)

        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        kotlin.test.assertTrue("cloudLink = CloudSync.linkForRestore(context, uri)" in about)
        kotlin.test.assertTrue("CloudSync.restoreDone(context.filesDir); needsRestart = true" in about)
        // Cancel and a failed restore read the mark on disk, not only this visit's memory.
        kotlin.test.assertTrue("fun restoreOnlyLink() = linkedToRestore || CloudSync.load(context.filesDir)?.restorePending == true" in about)
        assertFalse('\u2014' in CloudSyncCopy.RESTORE_FIRST)
    }

    /**
     * rc33 audit P1: sync built the zip straight into the only cloud copy, so a failure part way left it cut short,
     * Sync now and a background sync could interleave on it, and a failed background sync was never shown.
     */
    @Test
    fun `the cloud copy is replaced only by a whole zip, one sync at a time, and a failure is said`() {
        val c = File("src/main/kotlin/com/ironmonone/app/CloudSync.kt").readText().replace("\r\n", "\n")
        val sync = c.substring(c.indexOf("fun sync(context: Context"))
        val local = sync.indexOf("tmp.outputStream().buffered(1 shl 20).use { Backup.write(context.filesDir, it) }")
        val remote = sync.indexOf("openOutputStream(uri, \"wt\")")
        kotlin.test.assertTrue(local in 0 until remote, "the zip is whole before the synced file is truncated")
        kotlin.test.assertTrue("writeLock.withLock {" in sync)
        kotlin.test.assertTrue("} finally { tmp.delete() }" in sync)
        kotlin.test.assertTrue("if (r is Result.Failed) SaveTrouble.report(SaveTrouble.CLOUD, r.reason)" in c)
    }

    /**
     * rc32 audit P2 #9: a restore's restart cut off a cloud sync writing the only cloud copy. A restore waits for one under
     * way, and no background sync starts while a restore holds them off.
     */
    @Test
    fun `a restore waits for a sync under way, and no background sync starts while one is held`() {
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val holding = java.util.concurrent.CountDownLatch(1)
        val writer = Thread {
            CloudSync.writeLock.lock()
            holding.countDown()
            try { Thread.sleep(300) } finally { released.set(true); CloudSync.writeLock.unlock() }
        }.apply { start() }
        holding.await()
        kotlin.test.assertTrue(CloudSync.awaitIdle(5_000))
        kotlin.test.assertTrue(released.get(), "it returned only once the sync under way let go")
        writer.join()
        // One that does not finish in time is said: the restore then waits for the next try.
        val stuck = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(1)
        Thread { CloudSync.writeLock.lock(); stuck.countDown(); try { done.await() } finally { CloudSync.writeLock.unlock() } }.start()
        stuck.await()
        assertFalse(CloudSync.awaitIdle(100))
        done.countDown()
        val c = File("src/main/kotlin/com/ironmonone/app/CloudSync.kt").readText().replace("\r\n", "\n")
        val bg = c.substringAfter("fun syncInBackground(context: Context")
        kotlin.test.assertTrue(bg.indexOf("if (held) return") in 0 until bg.indexOf("worker.execute"), "held: no background sync is started")
        kotlin.test.assertTrue("if (held) return@execute" in bg, "nor one asked for just before")
        // Sync now: refused under the lock the restore waits on, so the two can never overlap.
        val sync = c.substring(c.indexOf("fun sync(context: Context"))
        kotlin.test.assertTrue(sync.indexOf("if (held) return Result.Failed(RESTORING)") in sync.indexOf("writeLock.withLock {") until sync.indexOf("openOutputStream(uri, \"wt\")"))
    }

    @Test
    fun `the fingerprint moves only when a backed-up file changes`() {
        val dir = Files.createTempDirectory("fp").toFile()
        val save = File(dir, "saves/emerald.sav").apply { parentFile.mkdirs(); writeBytes(ByteArray(64)) }
        val rom = File(dir, "prep/library/emerald.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(16)) }
        val a = CloudSync.fingerprint(dir)
        assertEquals(a, CloudSync.fingerprint(dir))
        rom.writeBytes(ByteArray(32)); rom.setLastModified(rom.lastModified() + 5000)
        assertEquals(a, CloudSync.fingerprint(dir), "a ROM is not in the backup, so it must not trigger a sync")
        save.writeBytes(ByteArray(65))
        assertNotEquals(a, CloudSync.fingerprint(dir))
    }

    @Test
    fun `providers are named for the player`() {
        assertEquals("Google Drive", CloudSync.providerName("com.google.android.apps.docs.storage"))
        assertEquals("Dropbox", CloudSync.providerName("com.dropbox.product.android.dbapp.document_provider.documents"))
        assertEquals("Downloads", CloudSync.providerName("com.android.providers.downloads.documents"))
        assertEquals("a cloud folder", CloudSync.providerName(null))
        assertEquals("never", CloudSync.whenLabel(0))
    }
}
