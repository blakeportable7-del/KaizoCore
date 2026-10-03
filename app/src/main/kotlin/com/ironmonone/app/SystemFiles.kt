package com.ironmonone.app

import java.io.File
import java.io.InputStream

/**
 * The player's own BIOS, firmware and NAND dumps, which the cores read from the files folder under the names in
 * CoreOptions.systemFiles. Nothing is downloaded or shipped: each comes from a file the player picks.
 *
 * An import is streamed to disk (rc32 audit P2 #45). The whole file used to be read into memory and copied out again,
 * about twice its size: for the 240 MB DSi NAND that is about 480 MB of heap while a DS game runs, which ends in
 * "Could not read that file." on most phones, so DSi mode could never be turned on. It is written beside the target,
 * flushed, checked against the sizes a good dump of that file has, and only then moved over the old one; a file that
 * fails any step leaves the old one as it was. A cut-short or wrong file was taken as present, and enabled DSi mode.
 */
internal object SystemFiles {
    private const val KB = 1024L
    private const val MB = 1024L * KB

    /** The sizes a good dump of [name] has, or null for a name with no known size. */
    fun sizes(name: String): List<LongRange>? = when (name) {
        "gba_bios.bin", "bios7.bin" -> listOf(16 * KB..16 * KB)
        "bios9.bin" -> listOf(4 * KB..4 * KB)
        // DS firmware: 256 KB from a DS or DS Lite, 128 KB from a DSi or 3DS, 512 KB from an iQue DS.
        "firmware.bin" -> listOf(128 * KB..128 * KB, 256 * KB..256 * KB, 512 * KB..512 * KB)
        "dsi_bios7.bin", "dsi_bios9.bin" -> listOf(64 * KB..64 * KB)
        "dsi_firmware.bin" -> listOf(128 * KB..128 * KB)
        // A DSi NAND image is 240 MB; dumps differ by a footer, and a DSi XL's runs a little larger.
        "dsi_nand.bin" -> listOf(240 * MB..256 * MB + KB)
        else -> null
    }

    fun sizeOk(name: String, size: Long): Boolean = size > 0 && (sizes(name)?.any { size in it } ?: true)

    /** The file is there and is the size a good dump of it is: what the settings call present. */
    fun present(dir: File, name: String): Boolean = File(dir, name).let { it.isFile && sizeOk(name, it.length()) }

    /**
     * Imports [name] into [dir] from what [open] returns, off the main thread. Returns the line for the status toast.
     */
    suspend fun import(dir: File, name: String, open: () -> InputStream?): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { importNow(dir, name, open) }

    internal fun importNow(dir: File, name: String, open: () -> InputStream?): String {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        return try {
            val size = (open() ?: return "Could not read that file.").use { input ->
                java.io.FileOutputStream(tmp).use { out -> input.copyTo(out, 1 shl 20).also { out.fd.sync() } }
            }
            if (!sizeOk(name, size)) {
                tmp.delete()
                return "That file is not a $name dump (it is %,d bytes). Nothing was changed.".format(size)
            }
            StateSlots.replace(tmp, target)
            "$name imported (%,d bytes). Takes effect on the next boot.".format(size)
        } catch (e: Exception) {
            tmp.delete()
            val m = e.message.orEmpty()
            if (e is java.io.IOException && ("ENOSPC" in m || "No space" in m))
                "Could not import $name: this phone is out of space. Nothing was changed."
            else "Could not read that file."
        }
    }
}
