package com.ironmonone.app

import android.content.Context
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import java.io.File

/**
 * What PREPARE makes from one exact dump, shared by Library's Patched versions page and the PATCH button on a game in
 * My games. Blake, 2026-09-30: PATCH on his FireRed 1.1 said "No patch in the library fits this file" while the app
 * carries the Nat. Dex patch for that very file, and Patched versions would only take the game from the phone's files,
 * not from My games; "this needs applied to all games". So every game's built-in patched versions (Nat. Dex, the
 * growth patch, Faster, Smart AI, Super Kaizo) are made from the copy already in the library, wherever it is asked.
 *
 * [run] takes over the file it is given: it is moved into PrepStore or deleted. A library file is never passed to it;
 * [copyFromLibrary] makes the cache copy, exactly as a file picked from the phone is copied first.
 */
internal object PrepRun {
    /** The patched versions KaizoCore can make of [kind] by itself: every PREPARE option but the game as it is. */
    fun builtIns(kind: RomKind): List<PrepOptions.Option> =
        PrepOptions.forKind(kind).filter { it.mode != PrepOptions.Mode.STANDARD }

    /** The built-ins for a library game, or none when the library does not hold an exact copy of a known game. */
    fun builtIns(entry: LibraryStore.Entry): List<PrepOptions.Option> =
        entry.kind?.takeIf { entry.verified }?.let { builtIns(it) }.orEmpty()

    /** A cache copy of a library game, for [run], identified from its header with the checksum the library already holds. */
    fun copyFromLibrary(context: Context, entry: LibraryStore.Entry, progress: FileProgress): Pair<File, RomIdentity.Result> {
        val tmp = File(context.cacheDir, "prep-" + System.nanoTime())
        progress.start("Copying ${entry.name}", entry.sizeBytes)
        entry.file.inputStream().use { i -> tmp.outputStream().buffered(1 shl 20).use { o -> copyWithProgress(i, o) { progress.at(it) } } }
        return tmp to RomIdentity.identifyWithCrc(tmp, entry.crc)
    }

    /**
     * Makes [optionId] (null: the game's default option) of [kind] from [file], which this takes over, and returns what
     * to tell the player. Throws [PrepFailure], already worded, or [NeedPatch] when the Nat. Dex patch is not in this build.
     */
    fun run(context: Context, store: PrepStore, file: File, kind: RomKind, optionId: String?, progress: FileProgress): String {
        val options = PrepOptions.forKind(kind)
        val opt = options.firstOrNull { it.id == optionId } ?: PrepOptions.default(kind)
        return when (opt.mode) {
            PrepOptions.Mode.STANDARD -> {
                progress.start("Saving", 0L)
                store.savePrepared(kind, file)
                if (kind.isNatDex || kind.patchTag != null) "Already patched. Stored as is." else "Stored as a standard (vanilla) base."
            }

            PrepOptions.Mode.PATCH -> {
                // Every failure below is worded for the player; none of them passes on an exception's own text (audit, 2026-09-27).
                val outKind = opt.out ?: throw PrepFailure(NOT_IN_THIS_BUILD)
                val patchFile = store.bundledPatch(context, opt.asset ?: throw PrepFailure(NOT_IN_THIS_BUILD))
                    ?: throw PrepFailure(NOT_IN_THIS_BUILD)
                val tmp = File(context.cacheDir, "prep-patched-${outKind.id}.${outKind.fileExtension}")
                progress.start("Patching", 0L)
                val crc = Patcher.applyFiles(patchFile, file, tmp, kind.displayName) { done, total -> progress.done = done; progress.total = total }
                if (outKind.expectedCrc != RomKind.CRC_UNKNOWN && crc != outKind.expectedCrc) {
                    tmp.delete()
                    throw PrepFailure("The patch applied, but the result is not a version this app knows. " +
                        "Your dump is probably a different version of the game. Nothing was changed.")
                }
                file.delete()
                store.savePrepared(outKind, tmp)
                "Patched to ${outKind.displayName}."
            }

            PrepOptions.Mode.NATDEX -> {
                // Bundled patch is used unless the user imported one.
                val patchFile = store.patchFileOrBundled(context, kind) ?: throw NeedPatch()
                progress.start("Patching", 0L)
                val out = Patcher.apply(patchFile.readBytes(), file.readBytes(), kind.displayName)
                val outKind = RomKind.allNatDex.firstOrNull { it.expectedCrc == com.ironmonone.patch.Crc32.of(out) }
                    ?: throw PrepFailure("The patch applied, but the result is not a Nat. Dex version this app knows. " +
                        "A newer Nat. Dex release needs an update of this app first. Nothing was changed.")
                store.savePrepared(outKind, out)
                // The copy is spent only once the build is stored; the cache copy used to stay behind here.
                file.delete()
                "Patched to ${outKind.displayName}."
            }
        }
    }
}

internal class NeedPatch : Exception()

/** A failure already worded for the player. */
internal class PrepFailure(message: String) : Exception(message)

internal const val NOT_IN_THIS_BUILD = "This option is not part of this version of the app. Pick another one."

/** The one-time step when this build has no Nat. Dex patch for the game, with where to get it (audit, 2026-09-27). */
internal const val NEED_NATDEX_PATCH = "KaizoCore needs the National Dex patch for this game once. " +
    "It comes from the Nat. Dex Extension release page. Download the .bps for your game there, then import it here."

internal fun isNoSpace(t: Throwable): Boolean =
    generateSequence(t) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

/** What a failed Prepare says to the player. */
internal fun prepFailure(t: Throwable): String = when {
    t is PrepFailure -> t.message ?: "Setting up the game did not work. Nothing was changed."
    isNoSpace(t) -> "Not enough free space on this phone to save the game. Free some space and try again."
    else -> patchFailure(t)
}
