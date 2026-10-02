package com.ironmonone.app

import java.io.File
import java.io.FileOutputStream

/**
 * Writes a small file the player cares about whole or not at all: a temp file beside it, synced
 * to disk, then renamed over it. Writing in place truncates first, so a kill or a power cut in
 * between left an empty file: a Survival heal count back at 10, attempts starting over, a run
 * history gone (review, 2026-09-29). Returns false when the write failed; the old file is then
 * untouched.
 */
object SafeWrite {
    fun bytes(file: File, bytes: ByteArray): Boolean {
        val tmp = File(file.parentFile, file.name + ".tmp")
        return runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            // One atomic replace, on Android and on a desktop JVM alike. It used to delete the file and rename again
            // when the first rename failed, and a full phone left the .tmp behind (rc33 audit P0-8).
            StateSlots.replace(tmp, file)
            true
        }.getOrElse { if (tmp.isFile) tmp.delete(); false }
    }

    fun text(file: File, text: String): Boolean = bytes(file, text.toByteArray(Charsets.UTF_8))
}
