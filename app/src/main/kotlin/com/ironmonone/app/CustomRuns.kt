package com.ironmonone.app

import android.content.Context
import java.io.File

/**
 * A settings file that is not byte for byte one of the files KaizoCore comes with makes a custom game (2026-09-30,
 * IronMON rules check R2): a Build your own game, an edited or imported copy, a file named "My Kaizo run". The mode
 * came from any mode word in the file's name, so all of those were shown and counted as that mode: the Kaizo chip lit,
 * the Home card said "Kaizo, attempt 12", and Your stats filed them under Kaizo. Now the Kaizo IronMON screen says so
 * under the mode, the new-run question and the Home card add "(custom)", and the run's record keeps it, so Your stats
 * and the shared line count it apart. The byte check is the one the passes already use (ExtraPasses.officialName).
 */
internal object CustomRuns {
    const val SUFFIX = " (custom)"

    /** The files KaizoCore comes with, for code that has no Context (PrepStore.installRun). MainActivity sets it. */
    @Volatile var bundled: (() -> Map<String, ByteArray>)? = null

    fun isCustom(name: String, bytes: ByteArray?, bundled: Map<String, ByteArray>): Boolean =
        ExtraPasses.officialName(name, bytes, bundled) == null

    fun isCustom(context: Context, settings: File): Boolean = !ExtraPasses.isOfficial(context, settings)

    /** [mode] with "(custom)" after it when [custom]. */
    fun label(mode: String, custom: Boolean): String = if (custom) mode + SUFFIX else mode

    /** Under the mode on the Kaizo IronMON screen, when the file picked is custom. */
    fun line(mode: String): String =
        "These settings are not the $mode file KaizoCore comes with, so a run from them is a custom game, not an official $mode run."
}
