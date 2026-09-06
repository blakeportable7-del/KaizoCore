package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File

/**
 * What the Play screen is playing. Until 2026-09-05 that was always "the
 * current run": the file the Run tab last randomized, its kind read from
 * lastrun.txt, save states and SRAM in paths that assumed one game. A
 * session is the same idea made explicit, so the Library can hand Play any
 * ROM the player owns and Play never has to know where it came from.
 *
 * Two producers, one consumer:
 * - the Run tab (and NEW RUN) select the run session, which resolves to
 *   exactly the file and save paths the app used before sessions existed;
 * - the Library's PLAY selects a library session, whose saves live under
 *   its own id so they can never collide with a run's.
 *
 * [kind] is non-null only when the tracker may read this game. For a run it
 * is the randomized kind. For a library file it is the kind identified BY
 * CRC: a hack of FireRed still carries FireRed's header, and a tracker fed a
 * hack's memory reads garbage that looks like a tracker bug, so a header-only
 * match plays without one.
 */
data class GameSession(
    val file: File,
    val platform: Platform,
    val kind: RomKind?,
    val title: String,
    /** Stable key for this game's saves. "run" for the IronMON run. */
    val id: String,
    /** The IronMON run: NEW RUN, attempts and seed stamping exist only here. */
    val isRun: Boolean,
) {
    val tracked: Boolean get() = kind != null

    companion object {
        const val RUN_ID = "run"

        /**
         * The kind a library file may be tracked as. Only a CRC match: a kind
         * whose CRC is not pinned yet, or a header match on a modified file,
         * gives null, and the game plays untracked rather than mis-tracked.
         */
        fun trackerKind(kind: RomKind?, crc: Long): RomKind? =
            kind?.takeIf { it.expectedCrc != RomKind.CRC_UNKNOWN && it.expectedCrc == crc }

        fun forRun(file: File, kind: RomKind?): GameSession = GameSession(
            file = file,
            platform = kind?.platform
                ?: Platform.fromExtension(file.extension)
                ?: Platform.GBA,
            kind = kind,
            title = kind?.displayName ?: file.name,
            id = RUN_ID,
            isRun = true,
        )

        fun forLibrary(entry: LibraryStore.Entry): GameSession? {
            val platform = entry.kind?.platform ?: entry.platform ?: return null
            return GameSession(
                file = entry.file,
                platform = platform,
                kind = trackerKind(entry.kind, entry.crc),
                title = entry.name,
                id = "lib-%08x".format(entry.crc),
                isRun = false,
            )
        }
    }
}

/**
 * Where a session's saves live. Pure paths, so they can be pinned by a test:
 * the run's paths are byte-for-byte what the app used before sessions, so no
 * existing save state, SRAM file or stat mark moves or is migrated.
 */
object SessionPaths {
    /** mGBA/Gambatte battery save. melonDS keeps its own .sav beside the ROM name. */
    fun sram(filesDir: File, session: GameSession, runKindId: String?): File =
        if (session.isRun) File(filesDir, "saves/" + (runKindId ?: "unknown") + ".srm")
        else File(filesDir, "saves/lib/${session.id}.srm")

    /** Slot 0 is the auto-save; its file is named apart so it can never be mistaken for a numbered slot. */
    private fun slotName(n: Int) = if (n == 0) "autosave" else "state$n"

    fun slot(filesDir: File, session: GameSession, n: Int): File =
        if (session.isRun) File(filesDir, "saves/${slotName(n)}.bin")
        else File(filesDir, "saves/lib/${session.id}/${slotName(n)}.bin")

    fun slotStamp(filesDir: File, session: GameSession, n: Int): File =
        if (session.isRun) File(filesDir, "saves/${slotName(n)}.id")
        else File(filesDir, "saves/lib/${session.id}/${slotName(n)}.id")

    /** Stat marks; the notes, route and move files sit beside it (StatMarks). */
    fun marks(prepRoot: File, session: GameSession): File =
        if (session.isRun) File(prepRoot, "marks.txt")
        else File(prepRoot, "library/notes/${session.id}/marks.txt")
}
