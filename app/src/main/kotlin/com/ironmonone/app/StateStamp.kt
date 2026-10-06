package com.ironmonone.app

import java.io.File

/**
 * What a run's save state is stamped with, and which stamps load into the run in play (2026-10-06).
 *
 * A save state is the whole of the emulator's memory, and it is only good on the very game it was taken on: put into
 * another build of the same game, the code and the data tables it points at have moved. Since rc36.1 Heart & Soul's
 * comfort build moves its addresses with every release, so a run stamped only "game/seed" could be made again on the
 * new build with the same seed and the same stamp, and its old states would load into it. A run's stamp now names the
 * making of its game too ("game/seed/tag", the tag a checksum of the recipe the run was made from, NextRun.Recipe:
 * the build of the game, the settings, the engine, the app), so a state taken on any other making of the game, another
 * build or other settings with the same seed, is never resumed into this one. The tag is the run's for its whole life:
 * the recipe is written once, when the run is put in place.
 *
 * Stamps written before this have no tag ("game/seed"). They still load into the run they were taken on: every
 * change of the run's game goes through PrepStore.installRun, which first [pin]s every such stamp to the tag of the
 * game going out, so an unpinned old stamp can only belong to the game that is in place now.
 */
internal object StateStamp {
    /** "game/seed" and, when a recipe names the run, its tag ([tagOf]): "game/seed/5d0c19a2". */
    fun of(identity: String, tag: String?): String = if (tag == null) identity else "$identity/$tag"

    /** The tag of a run made from [recipe] with [seed]: CRC-32 of its recipe's lines, 8 hex digits. */
    fun tagOf(recipe: NextRun.Recipe, seed: Long): String {
        val c = java.util.zip.CRC32()
        c.update((recipe.lines() + "seed=%016x".format(seed)).joinToString(",").toByteArray(Charsets.UTF_8))
        return "%08x".format(c.value)
    }

    /** What an old stamp is pinned to when the run going out had no recipe: no run has it. */
    const val NO_TAG = "00000000"

    private fun parts(s: String) = s.split('/')

    /** A stamp from before builds were stamped: game and seed only. */
    fun legacy(s: String): Boolean = parts(s).size == 2

    /** A state stamped [saved] loads into the run stamped [want]. */
    fun matches(saved: String?, want: String): Boolean {
        if (saved == null || !PrepStore.stampKnown(want)) return false
        if (saved == want) return true
        // An old stamp, game and seed only, of this very run: pin() makes sure it was taken on the game in place.
        return legacy(saved) && parts(want).size == 3 && want.startsWith("$saved/")
    }

    /** [saved] is this run (same game and seed) taken on another making of its game: the case that has its own words. */
    fun otherBuild(saved: String?, want: String): Boolean {
        if (saved == null || matches(saved, want)) return false
        val s = parts(saved); val w = parts(want)
        return s.size == 3 && w.size >= 2 && s[0] == w[0] && s[1] == w[1]
    }

    /**
     * Before the run's game is replaced: every state stamp in [stamps] still in the old form, game and seed only, gets
     * [tag] (the outgoing run's, or [NO_TAG] when it had no recipe), so it can never pass for a state of whatever game
     * moves in next, with the same seed or not. Stamps with a tag already are left alone.
     */
    fun pin(stamps: List<File>, tag: String?) {
        for (f in stamps) runCatching {
            if (!f.isFile) return@runCatching
            val s = f.readText().trim()
            if (legacy(s)) f.writeText(of(s, tag ?: NO_TAG))
        }
    }

    /** What a load says about a state of this run taken before its game was made again ([otherBuild]). */
    fun otherBuildLine(named: String): String =
        "$named was saved before this run's game was made again, so it was not loaded. Your in-game save keeps the run: pick Continue on the title screen."

    /** What Play says when the auto-save it would open was taken before this run's game was made again. */
    const val AUTO_OTHER_BUILD =
        "The auto-save was taken before this run's game was made again, so the game starts from its in-game save. Pick Continue on the title screen."
}
