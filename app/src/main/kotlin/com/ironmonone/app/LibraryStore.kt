package com.ironmonone.app

import com.ironmonone.core.PatchFormat
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Bps
import com.ironmonone.patch.Crc32
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import java.io.File

/**
 * The player's own ROMs and patches, copied once into app storage and
 * sorted into what they are.
 *
 * A copy rather than a document URI, because both cores open the game by
 * path (melonDS keeps the file handle open for the whole session), and a
 * 128MB DS ROM re-copied out of SAF on every launch is churn for nothing.
 *
 * Every file has a sidecar written at import with what identification
 * found, so listing the library never re-hashes 16 to 128MB per entry on
 * the composition thread. The sidecar is derived data plus provenance:
 * delete it and the next list() re-identifies the file (and forgets which
 * patch made it, which only costs a label).
 *
 * Patches are matched to games by CRC, never by name (2026-09-05, Blake:
 * "they won't be able to accidentally put a patch on a game that doesn't
 * match"). BPS and UPS carry their source CRC; IPS carries nothing, so an
 * IPS is stored against the game the player picked for it at import and
 * offered for that CRC only.
 *
 * Nothing here downloads, fetches or bundles anything. Files arrive only
 * through the import calls, from a picker the player drove.
 */
class LibraryStore(private val root: File) {

    private val patchDir = File(root, "patches")

    init { root.mkdirs(); patchDir.mkdirs() }

    /** How a ROM is shelved. Derived from identity and provenance, never typed by hand. */
    enum class Category(val title: String, val blurb: String) {
        CLEAN("Clean ROMs", "Verified dumps. The tracker and the randomizer start here."),
        PATCHED("Patched", "A known build made from a clean ROM, like Nat. Dex."),
        HACK("ROM hacks", "A Pokémon game that is not a clean dump. Plays without a tracker."),
        OTHER("Other games", "Not a game this app knows. Plays like any emulator."),
    }

    data class Entry(
        val file: File,
        val name: String,
        val crc: Long,
        /** As identified; may be a header-only match. See GameSession.trackerKind. */
        val kind: RomKind?,
        val platform: Platform?,
        val summary: String,
        /** Which library file and patch made this one, when it came from APPLY. */
        val baseName: String? = null,
        val patchName: String? = null,
    ) {
        val sizeBytes: Long get() = file.length()
        val verified: Boolean get() = kind != null && kind.expectedCrc != RomKind.CRC_UNKNOWN && kind.expectedCrc == crc
        /** Header says a supported game but the CRC is not pinned yet: shelved as clean, labelled unverified. */
        val unverified: Boolean get() = kind != null && kind.expectedCrc == RomKind.CRC_UNKNOWN
        val category: Category get() = when {
            verified && kind!!.isNatDex -> Category.PATCHED
            // Made by a patch and not a known build: a hack, even if the header
            // still names a game whose CRC is not pinned.
            patchName != null -> if (platform != null) Category.HACK else Category.OTHER
            verified || unverified -> Category.CLEAN
            kind != null -> Category.HACK
            platform != null && (summary.contains("but modified") || summary.contains("not a revision")) -> Category.HACK
            platform != null -> Category.OTHER
            else -> Category.OTHER
        }
        /** What the card says under the name. */
        val subtitle: String get() = when {
            verified && kind!!.isNatDex -> kind.displayName + " · verified"
            patchName != null -> "$patchName on ${baseName ?: "?"}"
            verified -> kind!!.displayName + " · verified"
            unverified -> kind!!.displayName + " · unverified (CRC not pinned yet)"
            else -> summary
        }
    }

    data class PatchEntry(
        val file: File,
        val name: String,
        val format: PatchFormat,
        /** CRC of the file this patch applies to. From the patch itself (BPS/UPS) or declared at import (IPS). */
        val forCrc: Long?,
        /** CRC the patch produces, when the format says (BPS/UPS). */
        val makesCrc: Long?,
        /** A name for the game it is for, resolved at import. */
        val forName: String?,
    ) {
        val sizeBytes: Long get() = file.length()
        fun matches(rom: Entry): Boolean = forCrc != null && forCrc == rom.crc
    }

    private fun sidecar(f: File) = File(f.parentFile, f.name + ".meta")
    private val selection = File(root, "session.txt")

    // ------------------------------------------------------------------ ROMs

    private fun unique(dir: File, wanted: String): File {
        val base = wanted.replace(Regex("[^A-Za-z0-9 ._+()\\[\\]-]"), "_").trim().ifBlank { "rom" }
        var target = File(dir, base)
        var n = 2
        while (target.exists()) {
            val dot = base.lastIndexOf('.')
            val stem = if (dot > 0) base.substring(0, dot) else base
            val ext = if (dot > 0) base.substring(dot) else ""
            target = File(dir, "$stem ($n)$ext"); n++
        }
        return target
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    /**
     * Take [source] in as [displayName] without reading it into memory: a
     * rename when it sits on the same filesystem, a streamed copy otherwise.
     * The identity is read off the file (RomIdentity.identify(File)).
     */
    fun importFile(displayName: String, source: File, baseName: String? = null, patchName: String? = null): Entry {
        val target = unique(root, displayName)
        if (!source.renameTo(target)) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            source.inputStream().use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> i.copyTo(o, 1 shl 20) } }
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            source.delete()
        }
        return describe(target, RomIdentity.identify(target), baseName, patchName).also { write(it) }
    }

    /** Copy [bytes] in as [displayName], never overwriting an existing entry. */
    fun import(displayName: String, bytes: ByteArray, baseName: String? = null, patchName: String? = null): Entry {
        val target = unique(root, displayName)
        writeAtomic(target, bytes)
        return describe(target, RomIdentity.identify(bytes), baseName, patchName).also { write(it) }
    }

    private fun describe(f: File, r: RomIdentity.Result, baseName: String?, patchName: String?): Entry {
        val platform = r.kind?.platform ?: when {
            r.header != null -> Platform.GBA
            r.dsHeader != null -> if (r.dsHeader?.checksumValid == true) Platform.NDS else null
            r.gbHeader != null -> Platform.GBC
            else -> Platform.fromExtension(f.extension)
        }
        return Entry(f, f.name, r.crc, r.kind, platform, r.summary, baseName, patchName)
    }

    /**
     * Sidecar format version. Bumped when identification changes its mind
     * about a file (v2: DS header checksum), so entries written under an
     * older rule are re-identified on the next list() instead of keeping a
     * verdict that is now wrong.
     */
    private val SIDECAR_VERSION = "v2"

    private fun write(e: Entry) {
        sidecar(e.file).writeText(
            listOf(SIDECAR_VERSION, "%08x".format(e.crc), e.kind?.id ?: "-", e.platform?.name ?: "-",
                e.baseName ?: "-", e.patchName ?: "-", e.summary).joinToString("\n"))
    }

    private fun read(f: File): Entry? {
        val all = runCatching { sidecar(f).readLines() }.getOrNull() ?: return null
        if (all.firstOrNull() != SIDECAR_VERSION) return null
        val lines = all.drop(1)
        if (lines.size < 6) return null
        val crc = lines[0].toLongOrNull(16) ?: return null
        val kind = RomKind.byId(lines[1].takeIf { it != "-" })
        val platform = lines[2].takeIf { it != "-" }?.let { p -> Platform.entries.firstOrNull { it.name == p } }
        return Entry(f, f.name, crc, kind, platform, lines.drop(5).joinToString("\n"),
            lines[3].takeIf { it != "-" }, lines[4].takeIf { it != "-" })
    }

    /** Every ROM on hand, newest first. A missing sidecar is rebuilt, not an error. */
    fun list(): List<Entry> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.endsWith(".meta") && !it.name.endsWith(".tmp") &&
                it.name != selection.name }
            .sortedByDescending { it.lastModified() }
            .map { f -> read(f) ?: describe(f, RomIdentity.identify(f), null, null).also { write(it) } }

    fun find(name: String): Entry? =
        File(root, name).takeIf { it.isFile }?.let { read(it) ?: list().firstOrNull { e -> e.name == name } }

    /** Rename the file (the extension is kept; saves are keyed by CRC, so nothing is lost). */
    fun rename(e: Entry, newStem: String): Entry {
        val ext = e.name.substringAfterLast('.', "")
        val stem = newStem.substringBeforeLast('.').trim().ifBlank { return e }
        val target = unique(root, if (ext.isEmpty()) stem else "$stem.$ext")
        if (!e.file.renameTo(target)) return e
        sidecar(e.file).renameTo(sidecar(target))
        val renamed = e.copy(file = target, name = target.name)
        if (selectedLibraryName() == e.name) selectLibrary(renamed)
        return renamed
    }

    /** Names worth offering in the rename dialog, best first. */
    fun suggestions(e: Entry): List<String> {
        val out = LinkedHashSet<String>()
        val k = e.kind
        when {
            k != null && e.verified && k.isNatDex -> { out += k.displayName; out += k.displayName.replace(" + ", " ") }
            k != null && e.verified -> { out += k.displayName + " clean"; out += k.displayName }
            k != null && e.unverified -> { out += k.displayName; out += k.displayName + " (unverified)" }
            e.patchName != null -> {
                val stem = e.patchName.substringBeforeLast('.')
                out += stem
                e.baseName?.let { b -> out += "$stem on ${b.substringBeforeLast('.')}" }
                k?.let { out += "$stem (${it.displayName.substringBefore(" (")} hack)" }
            }
            k != null -> out += k.displayName.substringBefore(" (") + " hack"
        }
        runCatching { RomIdentity.identify(e.file) }.getOrNull()?.headerLine
            ?.let { out += it.trim() }
        return out.filter { it.isNotBlank() && it != e.name.substringBeforeLast('.') }.take(4)
    }

    fun delete(e: Entry) {
        e.file.delete(); sidecar(e.file).delete()
        if (selectedLibraryName() == e.name) selectRun()
    }

    // --------------------------------------------------------------- patches

    private fun writePatchMeta(p: PatchEntry) {
        sidecar(p.file).writeText(listOf(p.format.name,
            p.forCrc?.let { "%08x".format(it) } ?: "-", p.makesCrc?.let { "%08x".format(it) } ?: "-",
            p.forName ?: "-").joinToString("\n"))
    }

    private fun readPatch(f: File): PatchEntry? {
        val lines = runCatching { sidecar(f).readLines() }.getOrNull() ?: return null
        if (lines.size < 4) return null
        val fmt = PatchFormat.entries.firstOrNull { it.name == lines[0] } ?: return null
        return PatchEntry(f, f.name, fmt, lines[1].takeIf { it != "-" }?.toLongOrNull(16),
            lines[2].takeIf { it != "-" }?.toLongOrNull(16), lines[3].takeIf { it != "-" })
    }

    /** What a patch file says about itself before it is stored: null when it is not a patch. */
    data class PatchPeek(val format: PatchFormat, val forCrc: Long?, val makesCrc: Long?)

    fun peekPatch(bytes: ByteArray): PatchPeek? {
        val fmt = Patcher.detect(bytes) ?: return null
        return when (fmt) {
            PatchFormat.BPS -> runCatching { Bps.info(bytes) }.getOrNull()?.let { PatchPeek(fmt, it.sourceCrc, it.targetCrc) }
                ?: PatchPeek(fmt, null, null)
            PatchFormat.UPS -> PatchPeek(fmt, bytes.u32le(bytes.size - 12), bytes.u32le(bytes.size - 8))
            else -> PatchPeek(fmt, null, null)
        }
    }

    /**
     * Store a patch. [declaredFor] is the ROM the player picked for a patch
     * that cannot say (IPS); a BPS/UPS ignores it and trusts its own CRC.
     */
    fun importPatch(displayName: String, bytes: ByteArray, declaredFor: Entry? = null): PatchEntry {
        val peek = peekPatch(bytes) ?: throw com.ironmonone.patch.CorruptPatch("it is not an IPS, BPS or UPS patch")
        val forCrc = peek.forCrc ?: declaredFor?.crc
        val forName = forCrc?.let { crc -> nameForCrc(crc) ?: declaredFor?.name }
        val target = unique(patchDir, displayName)
        writeAtomic(target, bytes)
        return PatchEntry(target, target.name, peek.format, forCrc, peek.makesCrc, forName).also { writePatchMeta(it) }
    }

    /** The game a CRC is: a known kind's name, else a library file with that CRC. */
    fun nameForCrc(crc: Long): String? =
        RomKind.all.firstOrNull { it.expectedCrc == crc && it.expectedCrc != RomKind.CRC_UNKNOWN }?.displayName
            ?: list().firstOrNull { it.crc == crc }?.name

    fun listPatches(): List<PatchEntry> =
        (patchDir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.endsWith(".meta") && !it.name.endsWith(".tmp") }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> readPatch(f) ?: peekPatch(f.readBytes())?.let { pk ->
                PatchEntry(f, f.name, pk.format, pk.forCrc, pk.makesCrc, pk.forCrc?.let(::nameForCrc)).also { writePatchMeta(it) } } }

    /** Only the patches that can be applied to this exact file. */
    fun patchesFor(rom: Entry): List<PatchEntry> = listPatches().filter { it.matches(rom) }

    /** Only the ROMs this patch can be applied to. */
    fun romsFor(patch: PatchEntry): List<Entry> = list().filter { patch.matches(it) }

    fun renamePatch(p: PatchEntry, newStem: String): PatchEntry {
        val ext = p.name.substringAfterLast('.', "")
        val stem = newStem.substringBeforeLast('.').trim().ifBlank { return p }
        val target = unique(patchDir, if (ext.isEmpty()) stem else "$stem.$ext")
        if (!p.file.renameTo(target)) return p
        sidecar(p.file).renameTo(sidecar(target))
        return p.copy(file = target, name = target.name)
    }

    fun deletePatch(p: PatchEntry) { p.file.delete(); sidecar(p.file).delete() }

    /**
     * Apply a stored patch to a ROM it matches. Refuses a mismatch here too,
     * so the UI's filtering is not the only guard.
     */
    fun apply(base: Entry, patch: PatchEntry): Entry {
        if (!patch.matches(base)) {
            throw com.ironmonone.patch.WrongSourceRom(patch.forCrc ?: 0L, base.crc, base.name)
        }
        return patch(base, patch.name, patch.file.readBytes())
    }

    /** Apply patch bytes to a library entry; the result is a new entry named after the patch. */
    fun patch(base: Entry, patchName: String, patchBytes: ByteArray): Entry {
        val out = Patcher.apply(patchBytes, base.file.readBytes(), base.name)
        val stem = patchName.substringBeforeLast('.').ifBlank { "patched" }
        val ext = base.name.substringAfterLast('.', "")
        return import(if (ext.isEmpty()) stem else "$stem.$ext", out, baseName = base.name, patchName = patchName)
    }

    // ------------------------------------------------------------ selection

    /** The run session: the file the Run tab produces. The default when nothing was chosen. */
    fun selectRun() { selection.writeText("run") }

    fun selectLibrary(e: Entry) { selection.writeText("lib\t" + e.name) }

    fun selectedLibraryName(): String? {
        val t = runCatching { selection.readText().trim() }.getOrNull() ?: return null
        return if (t.startsWith("lib\t")) t.removePrefix("lib\t") else null
    }

    private fun ByteArray.u32le(o: Int): Long =
        (this[o].toLong() and 0xFF) or ((this[o + 1].toLong() and 0xFF) shl 8) or
            ((this[o + 2].toLong() and 0xFF) shl 16) or ((this[o + 3].toLong() and 0xFF) shl 24)

    companion object {
        /** Guess whether a picked file is a patch by name, so ADD can route it. */
        fun looksLikePatch(name: String): Boolean =
            name.lowercase().let { it.endsWith(".bps") || it.endsWith(".ips") || it.endsWith(".ups") }

        @Suppress("unused")
        private val crcOf = Crc32
    }
}
