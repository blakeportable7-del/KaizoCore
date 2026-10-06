package com.ironmonone.app

import java.io.File
import java.io.IOException

/**
 * A small secret kept in one file: the Twitch sign-in (KeystoreTokenStore) and OBS's WebSocket password (ObsLink).
 * The app's is [KeystoreSealedFile]; a test keeps it in memory ([MemorySealedFile]).
 */
interface SealedFile {
    /** The secret, or null for none. Never throws. */
    fun read(): ByteArray?

    /** Replaces the secret. Throws when it could not be kept, so the caller can keep the old copy it had. */
    fun write(bytes: ByteArray)

    /** Removes it. */
    fun clear()
}

class MemorySealedFile(var bytes: ByteArray? = null) : SealedFile {
    override fun read() = bytes
    override fun write(bytes: ByteArray) { this.bytes = bytes }
    override fun clear() { bytes = null }
}

/**
 * A secret encrypted with an AES-256-GCM key that lives in the Android Keystore under [alias] and never leaves it, in
 * one file: a version byte, the 12-byte IV, then the sealed bytes. androidx.security would be a new dependency for what
 * the platform's own Keystore does here in a page. Moved out of the Twitch sign-in's store (2026-10-05) so OBS's
 * password uses the same lock; the file format and the Twitch key's alias are unchanged.
 *
 * Keep the file out of every copy of the app's data: at the top of filesDir, where device-local state lives, outside
 * the backup's allowlist (Backup.admits). Android's own backup is off; a device-to-device transfer can copy the file
 * but not the key, which is bound to this phone, so the copy cannot be read: [read] finds that, deletes it, and returns
 * null.
 */
class KeystoreSealedFile(private val file: File, private val alias: String) : SealedFile {

    @Synchronized
    override fun read(): ByteArray? {
        if (!file.isFile) return null
        val b = runCatching {
            val all = file.readBytes()
            require(all.size > 1 + IV_BYTES && all[0] == VERSION)
            val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, all, 1, IV_BYTES))
            c.doFinal(all, 1 + IV_BYTES, all.size - 1 - IV_BYTES)
        }.getOrNull()
        // Unreadable (copied from another phone, or the key was reset): useless, so it goes.
        if (b == null) file.delete()
        return b
    }

    @Synchronized
    override fun write(bytes: ByteArray) {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        check(iv.size == IV_BYTES)
        val sealed = c.doFinal(bytes)
        val out = ByteArray(1 + IV_BYTES + sealed.size)
        out[0] = VERSION
        System.arraycopy(iv, 0, out, 1, IV_BYTES)
        System.arraycopy(sealed, 0, out, 1 + IV_BYTES, sealed.size)
        file.parentFile?.mkdirs()
        if (!SafeWrite.bytes(file, out)) throw IOException("could not save ${file.name}")
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }

    private fun key(): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val g = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return g.generateKey()
    }

    private companion object {
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
    }
}
