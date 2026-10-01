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
    fun bytes(file: File, bytes: ByteArray): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        // Android replaces the target on rename; a desktop JVM on Windows may refuse, so try once more after removing it.
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "rename" }
        }
        true
    }.getOrDefault(false)

    fun text(file: File, text: String): Boolean = bytes(file, text.toByteArray(Charsets.UTF_8))
}
