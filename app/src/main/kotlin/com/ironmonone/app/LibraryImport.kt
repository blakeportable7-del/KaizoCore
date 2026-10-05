package com.ironmonone.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf

/**
 * Bringing files into the library: ROMs, patches and zips of either. Shared by
 * Library's My games page and the ROM Hacks screen so there is one way in, not two that drift.
 * Moved out of RomLibraryScreen unchanged, apart from [ipsFor].
 *
 * ROMs move into the library as files (a DS dump is 128 to 512 MB and does not
 * fit in the heap; Black 2 proved it); zips are unpacked to files the same way;
 * only patches, which are small, are read into memory. Call from a background
 * thread.
 *
 * What a pick came to is said in the player's words (2026-09-30, UX audit P0-11): whether the tracker reads
 * each file, what it is if it cannot, and how many of the files were added.
 */
internal class LibraryImport(
    private val context: Context,
    private val store: PrepStore,
    private val progress: FileProgress,
    /** True on the ROM Hacks screen, where an added game turns up in step 1 and has no Play button of its own. */
    private val forHacks: Boolean = false,
) {
    /** A patch that names no game, waiting for the player to say which one: small ones in memory, an xdelta on disk. */
    private class Pending(val name: String, val bytes: ByteArray?, val file: java.io.File?)

    /**
     * An .ips or .xdelta names no game, so the player says which ROM it is
     * for. Each waits here until they do, first in first asked. This held one
     * patch: a zip or a pick with two lost all but the last, and an .ips plus
     * an .xdelta saved one and deleted the other (audit, 2026-09-27).
     */
    private val pending = mutableStateListOf<Pending>()

    /** The patch the player is being asked about now, or null when nothing is waiting. */
    val pendingName: String? get() = pending.firstOrNull()?.name

    private fun isPendingFile(f: java.io.File) = pending.any { it.file == f }

    /** Save the patch being asked about for [game], then move on to the next. Returns the status line. */
    fun declarePending(game: LibraryStore.Entry): String {
        val head = pending.firstOrNull() ?: return ""
        val msg = runCatching {
            when {
                head.bytes != null -> store.library.importPatch(head.name, head.bytes, declaredFor = game).name
                head.file != null -> store.library.importPatchFile(head.name, head.file, game).name
                else -> null
            }
        }
        pending.removeAt(0)
        return msg.fold(
            { n -> if (n == null) "" else "Added patch ${stripKnownExt(n)} for ${stripKnownExt(game.name)}." },
            { patchFailure(it) },
        )
    }

    /** Drop the patch being asked about and move on to the next. */
    fun cancelPending() {
        val head = pending.firstOrNull() ?: return
        head.file?.delete()
        pending.removeAt(0)
    }

    private fun isXdelta(f: java.io.File): Boolean =
        runCatching { com.ironmonone.patch.Patcher.detect(com.ironmonone.patch.Patcher.head(f, 64)) == com.ironmonone.core.PatchFormat.XDELTA }
            .getOrDefault(false)

    // What this pick has come to so far, for the line that counts it. A file inside a zip counts on its own.
    private var added = 0
    private var skipped = 0
    private var several = false

    /** Start counting a pick of [picked] files. [importUris] does it; a caller that imports files one by one does it itself. */
    internal fun begin(picked: Int) { added = 0; skipped = 0; several = picked > 1 }

    /** The lines the pick came to, under the count when there is more than one file; null when there is nothing to say. */
    internal fun finish(lines: List<String>): String? =
        (listOfNotNull(countLine(added, skipped)) + lines.filter { it.isNotEmpty() }).joinToString("\n").ifBlank { null }

    /** A line that does not name its file gets the name in front when more than one file is being added. */
    private fun named(name: String, line: String) = if (several && !line.startsWith(name)) "${stripKnownExt(name)}: $line" else line

    /**
     * Import one file already in memory. [ipsFor], when set, is the ROM an
     * .ips is declared for without asking: the ROM Hacks screen passes the game the
     * player has already picked.
     */
    fun importOne(name: String, bytes: ByteArray, ipsFor: LibraryStore.Entry? = null): String = when {
        // A zip is opened and every ROM or patch inside is imported on its own.
        ZipImport.isZip(name, bytes) -> {
            val inside = ZipImport.extract(bytes)
            if (inside.isEmpty()) { skipped++; "$name holds no ROM or patch this app reads." }
            else {
                several = several || inside.size > 1
                inside.joinToString(Char(10).toString()) { (n, b) -> importOne(n, b, ipsFor) }.ifBlank { "" }
            }
        }
        archiveKind(name, bytes) != null -> { skipped++; named(name, archiveLine(archiveKind(name, bytes)!!)) }
        LibraryStore.looksLikePatch(name) || store.library.peekPatch(bytes) != null -> {
            val peek = store.library.peekPatch(bytes)
            when {
                peek == null -> { skipped++; "$name is not a patch this app reads." }
                peek.forCrc == null && ipsFor != null -> {
                    val p = store.library.importPatch(name, bytes, declaredFor = ipsFor)
                    added++
                    "Added patch ${stripKnownExt(p.name)} for ${stripKnownExt(ipsFor.name)}."
                }
                // Counted as added: it is kept, and the player is asked which game it is for.
                peek.forCrc == null -> { pending.add(Pending(name, bytes, null)); added++; "" }
                else -> {
                    val p = store.library.importPatch(name, bytes)
                    added++
                    "Added patch ${stripKnownExt(p.name)} for ${p.forName?.let(::stripKnownExt) ?: "a game you have not added yet"}." +
                        (if (MaxDexInfo.isPatch(p)) " " + MaxDexInfo.PATCH_ADDED else "")
                }
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
     *
     * A DS game that stays gets its in-game save back if it was added before under another name
     * (LibraryStore.adoptDsSave).
     */
    private fun admit(name: String, e: LibraryStore.Entry): String {
        store.library.refuse(name, e)?.let { skipped++; return named(name, it) }
        store.library.adoptDsSave(e)
        added++
        return addedLine(e, several, forHacks)
    }

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
                    if (inside.isEmpty()) { skipped++; "$name holds no ROM or patch this app reads." }
                    else {
                        several = several || inside.size > 1
                        inside.joinToString(Char(10).toString()) { (n, file) -> importFile(n, file, ipsFor) }.ifBlank { "" }
                    }
                } finally {
                    // Whatever was not moved into the library, waiting xdeltas aside.
                    dir.walkBottomUp().forEach { if (!isPendingFile(it)) it.delete() }  // a folder still holding one stays
                }
            }
            // A .7z or .rar is not opened here, and it used to be called a damaged game (2026-09-30, UX audit).
            archiveKind(name, head) != null -> { skipped++; named(name, archiveLine(archiveKind(name, head)!!)) }
            // A big xdelta (a DS hack) is moved, not read into memory.
            isXdelta(f) -> {
                if (ipsFor == null) { pending.add(Pending(name, null, f)); added++; "" }
                else { val p = store.library.importPatchFile(name, f, ipsFor); added++; "Added patch ${stripKnownExt(p.name)} for ${stripKnownExt(ipsFor.name)}." }
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
        begin(uris.size)
        val lines = uris.mapNotNull { uri ->
            var name: String? = null
            // A copy that failed half way used to stay in the cache for good.
            // It is removed here unless the library took it or it is the
            // patch waiting for the player to name its game.
            val tmp = java.io.File(context.cacheDir, "import-" + System.nanoTime())
            try {
                name = context.displayNameOf(uri)
                val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                // The copy and the library file exist together for a moment,
                // and a zip unpacks beside itself: twice the size, plus room.
                val free = context.cacheDir.usableSpace
                if (size > 0 && free < size * 2 + FREE_MARGIN) {
                    skipped++
                    return@mapNotNull "Not enough free space for $name. It needs about ${mb(size * 2 + FREE_MARGIN)} MB and your phone has ${mb(free)} MB free."
                }
                progress.start("Copying $name", if (size > 0) size else 0L)
                context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
                importFile(name, tmp, ipsFor)
            } catch (t: Throwable) {
                skipped++
                (t as? com.ironmonone.patch.PatchException)?.message
                    ?: if (isNoSpace(t)) "Your phone ran out of space while copying. Nothing was added."
                    // Say which one: "one file" out of a pick of five was no help (audit, 2026-09-27).
                    else "Could not read ${name ?: "one of the files"}. Nothing was added from it."
            } finally {
                if (tmp.exists() && !isPendingFile(tmp)) tmp.delete()
            }
        }
        return finish(lines)
    }

    private fun isNoSpace(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    private fun mb(bytes: Long) = (bytes + (1 shl 20) - 1) / (1 shl 20)

    internal companion object {
        const val FREE_MARGIN = 64L * 1024 * 1024

        /** ".7z" or ".rar" when the file's first bytes or its name say it is one of those archives; null otherwise. */
        fun archiveKind(name: String, head: ByteArray): String? {
            fun starts(vararg b: Int) = head.size >= b.size && b.indices.all { (head[it].toInt() and 0xFF) == b[it] }
            val lower = name.lowercase()
            return when {
                starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ".7z"
                starts(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> ".rar"
                lower.endsWith(".7z") -> ".7z"
                lower.endsWith(".rar") -> ".rar"
                else -> null
            }
        }

        /** What a .7z or .rar is told. [verb] is what the player does with the game inside: "add" here, "choose" on the Patched versions page. */
        fun archiveLine(kind: String, verb: String = "add") =
            "That is a $kind file. KaizoCore opens .zip only. Unpack it on your phone first, then $verb the game inside."

        /**
         * What was said when [e] went in. A game the tracker reads says so and where to start; one it cannot read says
         * that first, then what it is (RomIdentity's summary) and that it plays. [several] puts the file's name in front
         * of a line that would not name it. [forHacks] points at the ROM Hacks list instead of at Play.
         */
        fun addedLine(e: LibraryStore.Entry, several: Boolean = false, forHacks: Boolean = false): String {
            if (e.playOnly) {
                return "Added ${e.kind!!.displayName}. It plays without a tracker." +
                    if (several || forHacks || !e.kind.isHns) "" else " For Kaizo IronMON and Nuzlocke, use Pokémon Heart & Soul on Home."
            }
            if (e.verified) {
                return "Added ${e.kind!!.displayName}. The tracker reads it." +
                    if (several) "" else if (forHacks) " It is in the list under step 1." else " Start a run from Kaizo IronMON on Home, or tap Play to just play."
            }
            val label = stripKnownExt(e.name)
            return when (e.verdict) {
                com.ironmonone.patch.RomIdentity.Verdict.OTHER_LANGUAGE,
                com.ironmonone.patch.RomIdentity.Verdict.OTHER_VERSION,
                com.ironmonone.patch.RomIdentity.Verdict.UNCHECKED ->
                    (if (several) "$label: " else "") + "Added, but the tracker cannot read it. ${e.summary}"
                else -> "Added $label. ${e.summary}"
            }
        }

        /** The line that counts a pick of more than one file: null for one. */
        fun countLine(added: Int, skipped: Int): String? {
            val total = added + skipped
            return when {
                total < 2 -> null
                skipped == 0 -> "Added all $total."
                added == 0 -> "Nothing was added. All $total were skipped, see below."
                else -> "Added $added of $total. $skipped ${if (skipped == 1) "was" else "were"} skipped, see below."
            }
        }
    }
}
