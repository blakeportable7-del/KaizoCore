package com.ironmonone.app

import java.io.File

// HnsSetup's checks, words and patching are in engine-hns (HnsSetupCore.kt), shared with KaizoCore PC. Its Library half
// is here, as extensions called the same way (HnsSetup.addToLibrary, HnsSetup.refresh).

/** The two Library entries, and whether each was already there. */
internal class HnsAdded(val official: LibraryStore.Entry?, val kaizo: LibraryStore.Entry, val officialWasThere: Boolean, val kaizoWasThere: Boolean)

/**
 * Puts what [make] made into [library] under [OFFICIAL_FILE] and [KAIZO_FILE]. A game already there (the same
 * checksum, under any name) is kept as it is and the new copy dropped, so doing this twice adds nothing twice.
 */
internal fun HnsSetup.addToLibrary(library: LibraryStore, made: HnsSetup.Made, gameName: String?, patchName: String?): HnsAdded {
    val have = library.list()
    fun keep(file: File, name: String, baseName: String?, patch: String?): Pair<LibraryStore.Entry, Boolean> {
        val crc = crcOf(file)
        have.firstOrNull { it.crc == crc }?.let { file.delete(); return it to true }
        return library.importFile(name, file, baseName = baseName, patchName = patch) to false
    }
    val official = made.official?.let { keep(it, OFFICIAL_FILE, gameName, patchName) }
    val kaizo = keep(made.kaizo, KAIZO_FILE, null, null)
    return HnsAdded(official?.first, kaizo.first, official?.second == true, kaizo.second)
}

/** What the screen says once both are in the Library. */
internal fun HnsSetup.doneLine(added: HnsAdded): String {
    val names = listOfNotNull(added.official?.let { stripKnownExt(it.name) }, stripKnownExt(added.kaizo.name))
    val there = (added.official == null || added.officialWasThere) && added.kaizoWasThere
    return if (there) "Already in your library: ${names.joinToString(" and ")}. Choose how to play below."
    else "Done. ${names.joinToString(" and ")} ${if (names.size > 1) "are" else "is"} in your library. Choose how to play below."
}

/**
 * After an update that changed the comfort patch (rc36.1 known issue: "patch it again once after updating"), done by
 * the app instead of by the player: when [library] holds the KaizoCore build only as an older release made it, this
 * release's is made from the library's own official 2.0.6 with [comfort] and added, and the older copy is renamed
 * ([OLDER_FILE_STEM]) and left, with any save it has, for the player to delete. The library's files are not changed
 * otherwise: the 2.0.6 is copied into [work] first, as [make] uses up the game it is given. A failure throws a
 * [Problem] and leaves the library as it was. Blocking: a 32 MB patch.
 */
internal fun HnsSetup.refresh(library: LibraryStore, comfort: () -> File?, work: File, crcs: HnsSetup.Crcs = HnsSetup.Crcs.REAL): HnsSetup.Refresh {
    val list = library.list()
    if (list.any { it.crc == crcs.kaizo }) return HnsSetup.Refresh.NOTHING
    val older = list.filter { crcs.older(it.crc) }
    if (older.isEmpty()) return HnsSetup.Refresh.NOTHING
    val official = list.firstOrNull { it.crc == crcs.official } ?: return HnsSetup.Refresh.NEEDS_OFFICIAL
    work.mkdirs()
    val copy = File(work, "hns-refresh-${System.nanoTime()}.gba")
    try {
        official.file.copyTo(copy)
        val made = make(HnsSetup.Game(copy, official.name, crcs.official, HnsSetup.Start.OFFICIAL), null, comfort(), work, crcs)
        // The older copy steps aside first, so this release's takes the library's usual name.
        older.filter { it.name == KAIZO_FILE }.forEach { runCatching { library.rename(it, OLDER_FILE_STEM) } }
        addToLibrary(library, HnsSetup.Made(null, made.kaizo), null, null)
    } finally {
        copy.delete()
    }
    return HnsSetup.Refresh.MADE
}

/** The Library games step 1 offers: any copy of the three starting points, Emerald first. */
internal fun HnsSetup.libraryGames(entries: List<LibraryStore.Entry>, crcs: HnsSetup.Crcs = HnsSetup.Crcs.REAL): List<Pair<LibraryStore.Entry, HnsSetup.Start>> =
    entries.mapNotNull { e ->
        when (e.crc) {
            crcs.emerald -> e to HnsSetup.Start.EMERALD
            crcs.official -> e to HnsSetup.Start.OFFICIAL
            crcs.kaizo -> e to HnsSetup.Start.KAIZO
            else -> null
        }
    }.sortedBy { it.second.ordinal }

/**
 * [HnsSetup.refresh] once a launch, off the main thread (MainActivity), and what it did for the screens that say so
 * (HeartSoulScreen). One at a time: the Heart & Soul screen's own Patch it waits for it.
 */
internal object HnsRefresh {
    val result = androidx.compose.runtime.mutableStateOf<HnsSetup.Refresh?>(null)
    private val LOCK = Any()

    fun run(context: android.content.Context): HnsSetup.Refresh = synchronized(LOCK) {
        val store = PrepStore(context)
        val r = runCatching {
            HnsSetup.refresh(store.library, { store.bundledPatch(context, HnsSetup.COMFORT_PATCH) }, File(context.cacheDir, "hns-setup"))
        }.getOrElse { android.util.Log.w("KaizoCore", "Heart & Soul refresh failed", it); HnsSetup.Refresh.NOTHING }
        result.value = r
        r
    }

    /** Runs [block] (the screen's Patch it) with no refresh under way. */
    fun <T> exclusive(block: () -> T): T = synchronized(LOCK) { block() }
}
