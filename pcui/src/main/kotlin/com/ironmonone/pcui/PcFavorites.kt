package com.ironmonone.pcui

import com.ironmonone.app.FavoriteBall
import com.ironmonone.app.Favorites
import com.ironmonone.app.HnsNumbers
import com.ironmonone.app.RnqsRulesets
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File

/**
 * One favorite as the start screen draws it. [species] is Heart & Soul's id when this Pokémon is in that game.
 * [pack] is the Nat. Dex table's id, kept even when Heart & Soul has no row for that form, so the picture can
 * still be the form's sheet or the base species that stands in for it.
 */
data class PcFavorite(val name: String, val species: Int?, val pack: Int? = null)

/**
 * The favorites on the PC (PLAN row 8): the app's Favorites (the nine boxes of a Heart & Soul run, the drop-down's
 * matching in Favorites.suggest), held to the run's pool as HnsPool.favoritesScope holds them: a Nat. Dex pool run
 * shows nine and offers every Pokemon; a Vanilla pool run shows the first three, held to Gens 1 to 3, and keeps the
 * other six in the file for a Nat. Dex run.
 */
object PcFavorites {
    val kind: RomKind = RomKind.HEARTSOUL_KAIZO_206

    fun scope(natDex: Boolean): Favorites.Scope = Favorites.scope(kind, hnsNatDex = natDex)

    /** The file's text as nine boxes. */
    fun slots(text: String): List<String> = Favorites.slots(text, Favorites.NAT_DEX_SLOTS)

    fun text(slots: List<String>): String = Favorites.text(slots)

    /** The boxes the run counts, as icons (FavoriteIcons.of for a Heart & Soul run). */
    fun icons(slots: List<String>, natDex: Boolean): List<PcFavorite> {
        val sc = scope(natDex)
        return sc.used(slots).map { it.trim() }.filter { it.isNotEmpty() }.map { typed ->
            val id = Favorites.idOf(typed, kind)?.takeIf { Favorites.inGame(typed, sc.maxDex, kind) }
            PcFavorite(id?.let { Favorites.nameOf(it, kind) } ?: typed, id?.let(HnsNumbers::fromPack), id)
        }
    }

    /** What the drop-down offers for [typed] (Favorites.suggest): the names of the run's pool ranked as the log's search ranks them. */
    fun suggest(typed: String, natDex: Boolean, limit: Int = 6): List<String> =
        Favorites.suggest(typed, limit = limit, maxId = scope(natDex).maxDex, kind = kind)

    /**
     * The name [typed] as the drop-down would settle it: the one Pokemon it spells (as the table spells it), else the
     * drop-down's first offer; null when nothing is offered or the name is not one the run can have.
     */
    fun resolve(typed: String, natDex: Boolean): String? {
        val sc = scope(natDex)
        val exact = Favorites.idOf(typed, kind)?.takeIf { Favorites.inGame(typed, sc.maxDex, kind) }?.let { Favorites.nameOf(it, kind) }
        return exact ?: suggest(typed, natDex, limit = 1).firstOrNull()
    }

    /** What chat's `!fav` settles a typed name to: the Pokemon, or nothing and the names it may have meant. */
    data class Match(val name: String?, val offers: List<String>)

    /**
     * Chat's `!fav`, stricter than the drop-down: the one Pokemon [typed] spells, or the only one it nearly spells
     * (three letters or more). Anything else names nobody and offers up to three, so "a" or "char" never picks for chat.
     */
    fun strict(typed: String, natDex: Boolean): Match {
        val sc = scope(natDex)
        Favorites.idOf(typed, kind)?.takeIf { Favorites.inGame(typed, sc.maxDex, kind) }?.let { return Match(Favorites.nameOf(it, kind), emptyList()) }
        val offers = suggest(typed, natDex, limit = 4)
        val letters = com.ironmonone.app.LogSuggest.normalize(typed).length
        // Gengar and Gengar-M are one Pokemon to chat: forms of the first offer do not make it a guess.
        val one = offers.isNotEmpty() && offers.drop(1).all { it.startsWith(offers[0] + "-", ignoreCase = true) }
        return if (one && letters >= 3) Match(offers[0], emptyList()) else Match(null, offers.take(3))
    }

    /** IronMON Journey, whose Rule #1 lets you take any of the three starters: no ball picker (FavoriteBall.JOURNEY). */
    const val JOURNEY: String = FavoriteBall.JOURNEY

    /**
     * The run's mode ("kaizo", "survival") from its settings file, as the phone reads it (RnqsInfo.of: the name before
     * any bracket, the rulesets longest first, else the file's .meta sidecar); null when neither names one.
     */
    fun mode(settings: File): String? {
        val n = settings.name.substringBefore('(').removeSuffix(".rnqs").removeSuffix(".RNQS").lowercase().replace(Regex("[^a-z0-9]"), "")
        return RnqsRulesets.RULESETS.firstOrNull { n.contains(it) }
            ?: runCatching {
                File(settings.parentFile, settings.name + ".meta").takeIf { it.isFile }?.readLines()
                    ?.firstOrNull { it.substringBefore('=').trim() == "ruleset" }?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
    }

    /**
     * The starter balls holding a favorite the run's [mode] lets you take, as (ball 0 left to 2 right, the favorite's name):
     * the phone's FavoriteBall.hnsLines asked ball by ball, so only an allowed match is ever named and the other balls
     * stay blind. [bst] is the game's own BST of a species, read from the ROM.
     */
    fun favoriteBalls(slots: List<String>, natDex: Boolean, mode: String?, balls: List<GbaTracker.BallOption>, bst: (Int) -> Int?): List<Pair<Int, String>> {
        val favorites = scope(natDex).used(slots).filter { it.isNotBlank() }
        if (favorites.isEmpty()) return emptyList()
        return balls.mapNotNull { b ->
            val at = BALLS.indexOf(b.ball).takeIf { it >= 0 } ?: return@mapNotNull null
            if (FavoriteBall.hnsLines(favorites, mode, natDex, listOf(b), bst).isEmpty()) return@mapNotNull null
            val id = HnsNumbers.toPack(b.species) ?: return@mapNotNull null
            at to (Favorites.nameOf(id, kind) ?: favorites.first { Favorites.idOf(it, false) == id }.trim())
        }
    }

    private val BALLS = listOf("LEFT", "MIDDLE", "RIGHT")
}

/**
 * besteon's ball picker on the start screen (TrackerScreen.drawBallPicker): [chosen] the random ball (0 left, 1
 * middle, 2 right), [favorites] the balls holding a favorite the run may take (PcFavorites.favoriteBalls), which are
 * marked instead.
 */
data class PcBallPick(val chosen: Int, val favorites: List<Pair<Int, String>> = emptyList())
