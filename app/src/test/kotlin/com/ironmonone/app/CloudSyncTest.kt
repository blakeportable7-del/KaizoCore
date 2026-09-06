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
