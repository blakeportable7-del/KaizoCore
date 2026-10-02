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
 *
 * A DS game's in-game save is the one thing that follows the file NAME, not the
 * game: melonDS writes it as <ROM name>.sav in [savesDir] (SaveGuard.dsSaveFile).
 * Renaming the file, or adding the same game again under another name, used to
 * leave the save behind (UX audit, 2026-09-30); [rename] and [adoptDsSave] keep
 * it with the game. Game Boy and GBA saves are keyed by CRC (SessionPaths) and
 * were never at risk. [savesDir] is null where there are no cores' saves to
 * keep, which is every test that builds a store on a bare folder.
 */
class LibraryStore(private val root: File, private val savesDir: File? = savesDirFor(root)) {

    private val patchDir = File(root, "patches")

    init { root.mkdirs(); patchDir.mkdirs() }

    /** How a ROM is shelved. Derived from identity and provenance, never typed by hand. */
    enum class Category(val title: String, val blurb: String) {
        CLEAN("Clean ROMs", "Verified dumps. The tracker and the randomizer start here."),
        PATCHED("Patched", "A known build made from a clean ROM, like Nat. Dex."),
        // A real game the tracker cannot read is not a ROM hack (2026-09-30, UX audit P0-11): another language or revision goes here.
        OTHER_VERSIONS("Other versions", "Real Pokémon games the tracker does not read, such as another language or revision. They play without the tracker."),
        HACK("ROM hacks", "Pokémon games that have been changed. They play without the tracker."),
        OTHER("Other games", "Games the tracker does not read. They play like in any emulator."),
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
        /** Which case identification found (RomIdentity.Verdict); null for an entry built without one. */
        val verdict: RomIdentity.Verdict? = null,
    ) {
        val sizeBytes: Long get() = file.length()
        val verified: Boolean get() = kind != null && kind.expectedCrc != RomKind.CRC_UNKNOWN && kind.expectedCrc == crc
        /** Header says a supported game but the CRC is not pinned yet: shelved as clean, labelled unverified. */
        val unverified: Boolean get() = kind != null && kind.expectedCrc == RomKind.CRC_UNKNOWN
        /** The tracker reads this file: the same test GameSession.trackerKind makes. */
        val tracked: Boolean get() = verified
        val category: Category get() = when {
            verified && (kind!!.isNatDex || kind.patchTag != null) -> Category.PATCHED
            // Made by a patch and not a known build: a hack, even if the header
            // still names a game whose CRC is not pinned.
            patchName != null -> if (platform != null) Category.HACK else Category.OTHER
            verified || unverified -> Category.CLEAN
            // The verdict, not the words of the summary: what shows a file was changed is its size, and nothing else does.
            else -> when (verdict) {
                RomIdentity.Verdict.OTHER_LANGUAGE, RomIdentity.Verdict.OTHER_VERSION -> Category.OTHER_VERSIONS
                RomIdentity.Verdict.CHANGED -> Category.HACK
                null -> if (kind != null) Category.OTHER_VERSIONS else Category.OTHER
                else -> Category.OTHER
            }
        }
        /**
         * What the card says under the name. Says whether the tracker works ("Tracker works" and "No tracker", the
         * Words table of the UX audit), so no caller adds its own; for a file the tracker cannot read it is the
         * summary itself, which says what it is and that it still plays.
         */
        val subtitle: String get() = when {
            verified && (kind!!.isNatDex || kind.patchTag != null) -> kind.displayName + " · Tracker works"
            // Names without their file extensions, and no checksum talk (audit, 2026-09-27).
            patchName != null -> "${stripKnownExt(patchName)} on ${baseName?.let(::stripKnownExt) ?: "?"} · " + if (verified) "Tracker works" else "No tracker"
            verified -> kind!!.displayName + " · Tracker works"
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
        // Only what a filename cannot hold is replaced. The old allow-list
        // turned "Pokémon" into "Pok_mon" and ate apostrophes (audit, 2026-09-27).
        val base = wanted.replace(ILLEGAL_IN_NAME, "_").trim().trimStart('.').ifBlank { "rom" }
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
    fun importFile(displayName: String, source: File, baseName: String? = null, patchName: String? = null, onProgress: ((Long, Long) -> Unit)? = null): Entry {
        val target = unique(root, displayName)
        if (!source.renameTo(target)) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            source.inputStream().use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> i.copyTo(o, 1 shl 20) } }
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            source.delete()
        }
        return describe(target, RomIdentity.identify(target, onProgress), baseName, patchName).also { write(it) }
    }

    /** Copy [bytes] in as [displayName], never overwriting an existing entry. */
    fun import(displayName: String, bytes: ByteArray, baseName: String? = null, patchName: String? = null): Entry {
        val target = unique(root, displayName)
        writeAtomic(target, bytes)
        return describe(target, RomIdentity.identify(bytes), baseName, patchName).also { write(it) }
    }

    /**
     * Drop a just-imported [e] that is not a game or is already here, and say
     * why; null keeps it. For files the player adds, not for hacks this app
     * makes (a hack of a game is a new CRC anyway).
     */
    fun refuse(name: String, e: Entry): String? {
        if (e.platform == null) {
            delete(e)
            // A damaged DS header has its own next step; anything else is not a game, or did not copy whole (2026-09-30, UX audit P0-11).
            return if (e.verdict == RomIdentity.Verdict.DAMAGED) e.summary
            else "$name is not a Game Boy, Game Boy Advance or DS game file, or the copy is damaged. Nothing was added."
        }
        val same = list().firstOrNull { it.file != e.file && it.crc == e.crc } ?: return null
        delete(e)
        return "$name is already in your library as ${same.name}."
    }

    private fun describe(f: File, r: RomIdentity.Result, baseName: String?, patchName: String?): Entry {
        val platform = r.kind?.platform ?: when {
            r.header != null -> Platform.GBA
            r.dsHeader != null -> if (r.dsHeader?.checksumValid == true) Platform.NDS else null
            r.gbHeader != null -> Platform.GBC
            else -> Platform.fromExtension(f.extension)
        }
        // A file with no header at all that plays only because of its extension must not read "not a game" beside a Play button.
        val summary = if (r.verdict == RomIdentity.Verdict.NOT_A_GAME && platform != null)
            "A ${consoleName(platform)} file with no readable header. It may still play, without a tracker."
        else r.summary
        return Entry(f, f.name, r.crc, r.kind, platform, summary, baseName, patchName, r.verdict)
    }

    private fun consoleName(p: Platform) = when (p) { Platform.GBA -> "GBA"; Platform.NDS -> "DS"; Platform.GBC -> "Game Boy" }

    /**
     * Sidecar format version. Bumped when identification changes its mind
     * about a file (v2: DS header checksum, v3: the verdict and its words, 2026-09-30), so entries written under an
     * older rule are re-identified on the next list() instead of keeping a
     * verdict that is now wrong. A v2 sidecar is re-read from the file's header and the checksum it already
     * holds, not hashed again: a 512 MB DS game is seconds, and every screen that lists the library would do it at once.
     */
    private val SIDECAR_VERSION = "v3"

    private fun write(e: Entry) = runCatching {
        // A full disk must not take the library down with it.
        sidecar(e.file).writeText(
            listOf(SIDECAR_VERSION, "%08x".format(e.crc), e.kind?.id ?: "-", e.platform?.name ?: "-",
                e.baseName ?: "-", e.patchName ?: "-", e.verdict?.name ?: "-", e.summary).joinToString("\n"))
    }.let { }

    private fun read(f: File): Entry? {
        val all = runCatching { sidecar(f).readLines() }.getOrNull() ?: return null
        if (all.firstOrNull() != SIDECAR_VERSION) return null
        val lines = all.drop(1)
        if (lines.size < 7) return null
        val crc = lines[0].toLongOrNull(16) ?: return null
        val kind = RomKind.byId(lines[1].takeIf { it != "-" })
        val platform = lines[2].takeIf { it != "-" }?.let { p -> Platform.entries.firstOrNull { it.name == p } }
        val verdict = RomIdentity.Verdict.entries.firstOrNull { it.name == lines[5] }
        return Entry(f, f.name, crc, kind, platform, lines.drop(6).joinToString("\n"),
            lines[3].takeIf { it != "-" }, lines[4].takeIf { it != "-" }, verdict)
    }

    /** A v2 sidecar's file identified again from its header, keeping the checksum, base and patch it recorded; null when there is none. */
    private fun migrate(f: File): Entry? {
        val all = runCatching { sidecar(f).readLines() }.getOrNull() ?: return null
        if (all.firstOrNull() != "v2") return null
        val lines = all.drop(1)
        if (lines.size < 6) return null
        val crc = lines[0].toLongOrNull(16) ?: return null
        return runCatching {
            describe(f, RomIdentity.identifyWithCrc(f, crc), lines[3].takeIf { it != "-" }, lines[4].takeIf { it != "-" })
        }.getOrNull()?.also { write(it) }
    }

    /** Every ROM on hand, newest first. A missing sidecar is rebuilt, not an error. */
    fun list(): List<Entry> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.endsWith(".meta") && !it.name.endsWith(".tmp") &&
                it.name != selection.name }
            .sortedByDescending { it.lastModified() }
            .map { f -> read(f) ?: migrate(f) ?: describe(f, RomIdentity.identify(f), null, null).also { write(it) } }
            .also { noteDsSaves(it) }

    fun find(name: String): Entry? =
        File(root, name).takeIf { it.isFile }?.let { read(it) ?: list().firstOrNull { e -> e.name == name } }

    /**
     * Rename the file (the extension is kept). Saves are keyed by CRC, so nothing is lost, except a DS game's in-game
     * save, which melonDS keeps under the file's name: it is moved to the new name, with its .before-load copy (2026-09-30, UX audit).
     */
    fun rename(e: Entry, newStem: String): Entry {
        // Only a real file extension is cut: "FireRed 1.2.1" is a name, not "FireRed 1.2" plus ".1" (audit, 2026-09-27).
        val ext = knownExtOf(e.name) ?: ""
        val stem = stripKnownExt(newStem).trim().ifBlank { return e }
        val target = unique(root, if (ext.isEmpty()) stem else "$stem.$ext")
        if (!e.file.renameTo(target)) return e
        sidecar(e.file).renameTo(sidecar(target))
        synchronized(SAVES_LOCK) {
            if (isDs(e)) { moveDsSave(e.file, target); noteDsSave(e.crc, target) }
        }
        val renamed = e.copy(file = target, name = target.name)
        if (selectedLibraryName() == e.name) selectLibrary(renamed)
        return renamed
    }

    // -------------------------------------------------------- a DS game's in-game save

    private fun isDs(e: Entry) = (e.kind?.platform ?: e.platform) == Platform.NDS

    /**
     * Where the stem of the file a DS game's save lives under is kept: beside the game's save states (saves/lib/<id>/,
     * the id GameSession.forLibrary makes), so it goes into a backup with them and a restored phone still knows it.
     */
    private fun dsMarker(crc: Long): File? = savesDir?.let { File(it, "lib/lib-%08x/ds-save-name.txt".format(crc)) }

    private fun noteDsSave(crc: Long, rom: File) {
        val marker = dsMarker(crc) ?: return
        SafeWrite.text(marker, rom.nameWithoutExtension)
    }

    /** Every DS game in [entries] that has no marker yet gets one, so a game added before this existed is covered too. */
    private fun noteDsSaves(entries: List<Entry>) {
        if (savesDir == null) return
        synchronized(SAVES_LOCK) {
            for (e in entries) if (isDs(e) && dsMarker(e.crc)?.exists() == false) noteDsSave(e.crc, e.file)
        }
    }

    /** Whether some file in the library, other than [except], has [stem] for its name. */
    private fun stemInUse(stem: String, except: File): Boolean = (root.listFiles() ?: emptyArray()).any {
        it.isFile && it != except && !it.name.endsWith(".meta") && !it.name.endsWith(".tmp") && it.nameWithoutExtension == stem
    }

    /**
     * Move the in-game save of the DS game [from] to the name of [to], and the .before-load copy SaveGuard keeps
     * beside it. Nothing is ever overwritten: a save already under the new name is set aside as .replaced, so the
     * game being moved keeps its own and the other is still on the phone.
     */
    private fun moveDsSave(from: File, to: File) {
        val dir = savesDir ?: return
        val fromSave = SaveGuard.dsSaveFile(dir, from)
        val toSave = SaveGuard.dsSaveFile(dir, to)
        if (fromSave == toSave) return
        for ((src, dst) in listOf(fromSave to toSave, SaveGuard.DsWatch(fromSave).backup to SaveGuard.DsWatch(toSave).backup)) {
            if (!src.isFile) continue
            if (dst.exists()) setAside(dst)
            if (!src.renameTo(dst)) { src.copyTo(dst, overwrite = true); src.delete() }
        }
    }

    private fun setAside(f: File) {
        var n = 1
        var to = File(f.parentFile, f.name + ".replaced")
        while (to.exists()) to = File(f.parentFile, f.name + ".replaced" + ++n)
        f.renameTo(to)
    }

    /**
     * Keep a DS game's in-game save with the game when it comes back under another file name (deleted and added again,
     * or added on a phone a backup was restored to). Call it for a file the player added and [refuse] let stay. The
     * game is known by its CRC: the stem its save was last under is read from the marker, and when that stem is free
     * (no file in the library uses it now, so the save cannot be another game's) and the new name has no save yet, the
     * save moves. Then the marker names the new stem. Does nothing for a game that is not a DS game.
     */
    fun adoptDsSave(e: Entry) {
        if (savesDir == null || !isDs(e)) return
        val marker = dsMarker(e.crc) ?: return
        synchronized(SAVES_LOCK) {
            val was = runCatching { marker.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            val now = e.file.nameWithoutExtension
            if (was != null && was != now && !stemInUse(was, except = e.file) &&
                !SaveGuard.dsSaveFile(savesDir, e.file).exists()
            ) moveDsSave(File(root, "$was.nds"), e.file)
            noteDsSave(e.crc, e.file)
        }
    }

    /**
     * Names worth offering in the rename dialog, best first. Built from what
     * the sidecar already knows: this used to re-hash the whole file on the
     * main thread for a header line ("POKEMON EMER BPEE") nobody would pick,
     * and froze the app on a DS game (audit, 2026-09-27).
     */
    fun suggestions(e: Entry): List<String> {
        val out = LinkedHashSet<String>()
        val k = e.kind
        when {
            k != null && e.verified && k.isNatDex -> { out += k.displayName; out += k.displayName.replace(" + ", " ") }
            k != null && e.verified -> { out += k.displayName + " clean"; out += k.displayName }
            k != null && e.unverified -> { out += k.displayName; out += k.displayName + " (unverified)" }
            e.patchName != null -> {
                val stem = stripKnownExt(e.patchName)
                out += stem
                e.baseName?.let { b -> out += "$stem on ${stripKnownExt(b)}" }
                k?.let { out += "$stem (${it.displayName.substringBefore(" (")} hack)" }
            }
            // A game the header names whose checksum is not the pinned one: another copy of it, not a hack (2026-09-30).
            k != null -> out += k.displayName.substringBefore(" (")
        }
        return out.filter { it.isNotBlank() && it != stripKnownExt(e.name) }.take(4)
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
    /**
     * Store a patch already on disk, moving it rather than reading it: an
     * xdelta for a DS hack can be tens of megabytes. Only xdelta is taken this
     * way, since its header is all [peekPatch] needs; BPS and UPS carry their
     * checksums at the end, and are small.
     */
    fun importPatchFile(displayName: String, source: File, declaredFor: Entry? = null): PatchEntry {
        val head = com.ironmonone.patch.Patcher.head(source, 64)
        if (Patcher.detect(head) != PatchFormat.XDELTA) return importPatch(displayName, source.readBytes(), declaredFor).also { source.delete() }
        val forCrc = declaredFor?.crc
        val target = unique(patchDir, displayName)
        if (!source.renameTo(target)) { source.copyTo(target, overwrite = true); source.delete() }
        return PatchEntry(target, target.name, PatchFormat.XDELTA, forCrc, null, forCrc?.let { declaredFor.name })
            .also { writePatchMeta(it) }
    }

    fun importPatch(displayName: String, bytes: ByteArray, declaredFor: Entry? = null): PatchEntry {
        val peek = peekPatch(bytes) ?: throw com.ironmonone.patch.CorruptPatch("it is not an IPS, BPS, UPS or xdelta patch")
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
        val ext = knownExtOf(p.name) ?: ""
        val stem = stripKnownExt(newStem).trim().ifBlank { return p }
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
    fun apply(base: Entry, patch: PatchEntry, onProgress: ((Long, Long) -> Unit)? = null): Entry {
        if (!patch.matches(base)) {
            throw com.ironmonone.patch.WrongSourceRom(patch.forCrc ?: 0L, base.crc, base.name)
        }
        // File to file. This read the whole ROM and the patch into memory and
        // went through Patcher.apply, which refuses xdelta outright; so every
        // DS hack (they ship as xdelta, on 128 to 512 MB dumps) failed or ran
        // out of memory (audit, 2026-09-27). applyFiles streams xdelta and
        // takes the small formats through memory as before.
        val stem = stripKnownExt(patch.name).ifBlank { "patched" }
        val ext = base.name.substringAfterLast('.', "")
        val tmp = File(root, ".patching-" + System.nanoTime() + ".tmp")
        try {
            Patcher.applyFiles(patch.file, base.file, tmp, base.name, onProgress)
            return importFile(if (ext.isEmpty()) stem else "$stem.$ext", tmp, baseName = base.name, patchName = patch.name)
        } finally {
            tmp.delete()
        }
    }

    /** Apply patch bytes to a library entry; the result is a new entry named after the patch. */
    fun patch(base: Entry, patchName: String, patchBytes: ByteArray): Entry {
        val out = Patcher.apply(patchBytes, base.file.readBytes(), base.name)
        val stem = stripKnownExt(patchName).ifBlank { "patched" }
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
        /**
         * One lock for the saves folder's markers and moves. Every screen makes its own PrepStore, so its own
         * LibraryStore, and two of them list the library at once.
         */
        private val SAVES_LOCK = Any()

        /** The games in the order a player thinks of them: Game Boy, then GBA, then DS. */
        private val TRACKED_ORDER = listOf(
            "Red", "Blue", "Yellow", "Gold", "Silver", "Crystal", "Ruby", "Sapphire", "Emerald", "FireRed", "LeafGreen",
            "Diamond", "Pearl", "Platinum", "HeartGold", "SoulSilver", "Black", "White", "Black 2", "White 2",
        )

        /**
         * The games the tracker reads: those with a build whose checksum is pinned (a header alone is not read, see
         * GameSession.trackerKind), so Black, whose copy has not been checked, is not among them. A test holds this
         * list to every pinned build, so a game added to RomKind is not left out of what the empty page promises.
         */
        fun trackedGames(): List<String> {
            val pinned = RomKind.allV1.filter { it.expectedCrc != RomKind.CRC_UNKNOWN }
                .map { it.displayName.substringAfter(' ').substringBefore(" (") }.toSet()
            return TRACKED_ORDER.filter { it in pinned }
        }

        /** Guess whether a picked file is a patch by name, so ADD can route it. */
        fun looksLikePatch(name: String): Boolean =
            name.lowercase().let {
                it.endsWith(".bps") || it.endsWith(".ips") || it.endsWith(".ups") ||
                    it.endsWith(".xdelta") || it.endsWith(".vcdiff")
            }

        @Suppress("unused")
        private val crcOf = Crc32

        /** What no filename may hold: path separators, the Windows reserved set, control characters. */
        private val ILLEGAL_IN_NAME = Regex("[/\\\\:*?\"<>|\\x00-\\x1F\\x7F]")
    }
}

/**
 * The cores' saves folder for a library kept the way PrepStore keeps it: filesDir/prep/library beside filesDir/saves
 * (PlayScreen's savesDirectory). Null for a library anywhere else, which has none.
 */
internal fun savesDirFor(libraryRoot: File): File? =
    libraryRoot.takeIf { it.name == "library" && it.parentFile?.name == "prep" }?.parentFile?.parentFile?.let { File(it, "saves") }

/** The file extensions this app reads. Anything else after a dot is part of the name. */
private val KNOWN_EXTS = setOf("gba", "gbc", "gb", "nds", "zip", "ips", "bps", "ups", "xdelta", "vcdiff")

/** [name]'s extension, lowercase, when it is one this app reads; null otherwise. */
internal fun knownExtOf(name: String): String? =
    name.substringAfterLast('.', "").lowercase().takeIf { it in KNOWN_EXTS && name.lastIndexOf('.') > 0 }

/**
 * A file or game name without its file extension. Only a known extension is
 * cut, because game names carry dots of their own: "FireRed + Nat. Dex 1.2.1"
 * and "(U) v1.1" lost their versions to substringBeforeLast (audit, 2026-09-27).
 */
internal fun stripKnownExt(name: String): String =
    if (knownExtOf(name) != null) name.substringBeforeLast('.') else name

/** A patch format as the player reads it. */
internal fun patchFormatLabel(f: PatchFormat): String = when (f) {
    PatchFormat.IPS -> "IPS patch"
    PatchFormat.BPS -> "BPS patch"
    PatchFormat.UPS -> "UPS patch"
    PatchFormat.XDELTA -> "xdelta patch"
}
