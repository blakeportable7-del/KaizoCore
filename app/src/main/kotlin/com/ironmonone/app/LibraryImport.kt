package com.ironmonone.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bringing files into the library: ROMs, patches and zips of either. Shared by
 * the ROMS tab and the HACK tab so there is one way in, not two that drift.
 * Moved out of RomLibraryScreen unchanged, apart from [ipsFor].
 *
 * ROMs move into the library as files (a DS dump is 128 to 512 MB and does not
 * fit in the heap; Black 2 proved it); zips are unpacked to files the same way;
 * only patches, which are small, are read into memory. Call from a background
 * thread.
 */
internal class LibraryImport(
    private val context: Context,
    private val store: PrepStore,
    private val progress: FileProgress,
) {
    /**
     * An .ips names no game, so the player says which ROM it is for. It waits
     * here until they do. Null when nothing is waiting.
     */
    var pendingIps by mutableStateOf<Pair<String, ByteArray>?>(null)

    /** The same, for an xdelta kept on disk because it may be large. */
    var pendingFile by mutableStateOf<Pair<String, java.io.File>?>(null)

    /** The patch the player is being asked about, whichever kind it is. */
    val pendingName: String? get() = pendingIps?.first ?: pendingFile?.first

    /** Save the waiting patch for [game]. Returns the status line. */
    fun declarePending(game: LibraryStore.Entry): String {
        val msg = pendingIps?.let { (n, b) -> store.library.importPatch(n, b, declaredFor = game).name }
            ?: pendingFile?.let { (n, f) -> store.library.importPatchFile(n, f, game).name }
        cancelPending()
        return if (msg == null) "" else "Added patch $msg for ${game.name}."
    }

    fun cancelPending() {
        pendingFile?.second?.delete()
        pendingIps = null; pendingFile = null
    }

    private fun isXdelta(f: java.io.File): Boolean =
        runCatching { f.inputStream().use { com.ironmonone.patch.Patcher.detect(it.readNBytes(64)) } == com.ironmonone.core.PatchFormat.XDELTA }
            .getOrDefault(false)

    /**
     * Import one file already in memory. [ipsFor], when set, is the ROM an
     * .ips is declared for without asking: the HACK tab passes the game the
     * player has already picked.
     */
    fun importOne(name: String, bytes: ByteArray, ipsFor: LibraryStore.Entry? = null): String = when {
        // A zip is opened and every ROM or patch inside is imported on its own.
        ZipImport.isZip(name, bytes) -> {
            val inside = ZipImport.extract(bytes)
            if (inside.isEmpty()) "$name holds no ROM or patch this app reads."
            else inside.joinToString(Char(10).toString()) { (n, b) -> importOne(n, b, ipsFor) }.ifBlank { "" }
        }
        LibraryStore.looksLikePatch(name) || store.library.peekPatch(bytes) != null -> {
            val peek = store.library.peekPatch(bytes)
            when {
                peek == null -> "$name is not a patch this app reads."
                peek.forCrc == null && ipsFor != null -> {
                    val p = store.library.importPatch(name, bytes, declaredFor = ipsFor)
                    "Added patch ${p.name} for ${ipsFor.name}."
                }
                peek.forCrc == null -> { pendingIps = name to bytes; "" }
                else -> { val p = store.library.importPatch(name, bytes); "Added patch ${p.name} for ${p.forName ?: "an unknown game (%08x)".format(p.forCrc)}." }
            }
        }
        else -> admit(name, store.library.import(name, bytes))
    }

    /**
     * Keep a newly imported ROM only if it is a game and not one already in
     * the library. Anything went in before: a photo picked by mistake became a
     * library entry, and the same dump added twice showed up twice (audit,
     * 2026-09-27). The check runs after import because identifying the file
     * is what import does.
     */
    private fun admit(name: String, e: LibraryStore.Entry): String =
        store.library.refuse(name, e) ?: ("Added ${e.name}: ${e.subtitle}" + (if (e.verified) ". Ready on the Run tab." else ""))

    /** Import one picked file that has been streamed to [f]. */
    fun importFile(name: String, f: java.io.File, ipsFor: LibraryStore.Entry? = null): String {
        val head = f.inputStream().use { i -> val b = ByteArray(8); val n = i.read(b); if (n > 0) b.copyOf(n) else ByteArray(0) }
        return when {
            ZipImport.isZip(name, head) -> {
                progress.start("Unpacking $name", f.length())
                val dir = java.io.File(f.parentFile, f.name + ".d")
                try {
                    val inside = f.inputStream().use { ZipImport.extractToFiles(it, dir) { progress.at(it) } }
                    f.delete()
                    if (inside.isEmpty()) "$name holds no ROM or patch this app reads."
                    else inside.joinToString(Char(10).toString()) { (n, file) -> importFile(n, file, ipsFor) }.ifBlank { "" }
                } finally {
                    // Whatever was not moved into the library, a waiting xdelta aside.
                    val keep = pendingFile?.second
                    dir.walkBottomUp().forEach { if (it != keep) it.delete() }  // a folder still holding it stays
                }
            }
            // A big xdelta (a DS hack) is moved, not read into memory.
            isXdelta(f) -> {
                if (ipsFor == null) { pendingFile = name to f; "" }
                else { val p = store.library.importPatchFile(name, f, ipsFor); "Added patch ${p.name} for ${ipsFor.name}." }
            }
            LibraryStore.looksLikePatch(name) || f.length() < 32L * 1024 * 1024 && store.library.peekPatch(f.readBytes()) != null ->
                importOne(name, f.readBytes(), ipsFor).also { f.delete() }
            else -> {
                progress.start("Checking $name", f.length())
                admit(name, store.library.importFile(name, f) { d, t -> progress.at(d); if (t > 0) progress.total = t })
            }
        }
    }

    /** Copy each picked document to the cache and import it. Returns the status lines, or null if there is nothing to say. */
    fun importUris(uris: List<Uri>, ipsFor: LibraryStore.Entry? = null): String? {
        val lines = uris.mapNotNull { uri ->
            // A copy that failed half way used to stay in the cache for good.
            // It is removed here unless the library took it or it is the
            // patch waiting for the player to name its game.
            val tmp = java.io.File(context.cacheDir, "import-" + System.nanoTime())
            try {
                val name = context.displayNameOf(uri)
                val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                // The copy and the library file exist together for a moment,
                // and a zip unpacks beside itself: twice the size, plus room.
                val free = context.cacheDir.usableSpace
                if (size > 0 && free < size * 2 + FREE_MARGIN)
                    return@mapNotNull "Not enough free space for $name. It needs about ${mb(size * 2 + FREE_MARGIN)} MB and your phone has ${mb(free)} MB free."
                progress.start("Copying $name", if (size > 0) size else 0L)
                context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
                importFile(name, tmp, ipsFor)
            } catch (t: Throwable) {
                (t as? com.ironmonone.patch.PatchException)?.message
                    ?: if (isNoSpace(t)) "Your phone ran out of space while copying. Nothing was added." else "Could not read one file."
            } finally {
                if (tmp.exists() && pendingFile?.second != tmp) tmp.delete()
            }
        }
        return lines.filter { it.isNotEmpty() }.joinToString("\n").ifBlank { null }
    }

    private fun isNoSpace(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    private fun mb(bytes: Long) = (bytes + (1 shl 20) - 1) / (1 shl 20)

    private companion object { const val FREE_MARGIN = 64L * 1024 * 1024 }
}
