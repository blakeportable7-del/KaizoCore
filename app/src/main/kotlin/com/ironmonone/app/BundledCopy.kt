package com.ironmonone.app

import android.content.Context
import java.io.File

/**
 * A file copied out of the APK the first time it is needed, kept only when whole (rc33 audit P1). The copy used to be
 * written straight under its own name, so one cut short (a full phone, the app killed) stayed there, was used for
 * ever, and the patch it held then failed as if the player's dump were wrong. Now it goes through a .tmp and an atomic
 * replace, and a mark beside it records the size it was written at. A copy without the mark, or with another size,
 * is copied again.
 */
internal object BundledCopy {
    fun mark(f: File): File = File(f.parentFile, f.name + ".ok")

    fun whole(f: File): Boolean =
        f.isFile && runCatching { mark(f).readText().trim().toLong() == f.length() }.getOrDefault(false)

    fun markWhole(f: File) { runCatching { mark(f).writeText(f.length().toString()) } }

    /** A BPS patch whose own checksum holds (an imported one, or a copy from before the mark): marked whole once checked. */
    fun bpsIntact(f: File): Boolean =
        runCatching { com.ironmonone.patch.Bps.info(f.readBytes()).patchIntact }.getOrDefault(false).also { if (it) markWhole(f) }

    /** Copies [asset] out of the APK to [dest], whole or not at all; null when it could not. */
    fun extract(context: Context, asset: String, dest: File): File? = copyWhole(dest) { out ->
        context.assets.open(asset).use { it.copyTo(out) }
    }

    /**
     * Writes [dest] through a .tmp with [write], replaces it in one step and marks it whole; null on any failure. The
     * .tmp is this call's own: with one name for all, two first-time copies of one patch (two patch jobs at once) wrote
     * the same file, and one could read the patch half written (rc32 audit P2 #74).
     */
    fun copyWhole(dest: File, write: (java.io.OutputStream) -> Unit): File? {
        var tmp: File? = null
        return runCatching {
            dest.parentFile?.mkdirs()
            val t = File.createTempFile(dest.name.padEnd(3, '_'), ".tmp", dest.absoluteFile.parentFile).also { tmp = it }
            t.outputStream().use { write(it) }
            StateSlots.replace(t, dest)
            markWhole(dest)
            dest
        }.getOrElse { tmp?.takeIf { it.isFile }?.delete(); null }
    }
}
