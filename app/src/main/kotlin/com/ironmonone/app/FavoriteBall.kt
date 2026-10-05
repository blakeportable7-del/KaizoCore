package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker
import java.io.File

/**
 * The no-party card's favorites: the list, as the PC tracker shows it, and in the lab each ball holding one to take.
 * The card draws [icons], each favorite's picture (FavoriteIcons).
 *
 * Made by [FavoriteBall.shown], it works itself out again after any save of the favorites (Favorites.edits), the first
 * time it is read after one: so an edit in Tracker Setup during a run (FavoritesEditor) shows on the card and in the ball
 * line at once. Reading it in a composable is what makes that composable redraw; Play, at the verifier's limit, keeps
 * its one remember line.
 */
class FavoritesShown private constructor(first: Parts, private val again: (() -> Parts)?) {
    constructor(list: String?, balls: List<String> = emptyList(), icons: List<FavoriteIcon> = emptyList()) :
        this(Parts(list, balls, icons), null)

    internal constructor(again: () -> Parts) : this(again(), again)

    internal data class Parts(val list: String?, val balls: List<String>, val icons: List<FavoriteIcon>)

    private var parts = first
    // Read without observing: made inside Play's remember, a tracked read here would redraw all of Play on every save.
    private var madeAt = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { Favorites.edits.intValue }

    private fun now(): Parts {
        val at = Favorites.edits.intValue
        val make = again
        if (make != null && at != madeAt) {
            parts = make()
            madeAt = at
        }
        return parts
    }

    val list: String? get() = now().list
    val balls: List<String> get() = now().balls
    val icons: List<FavoriteIcon> get() = now().icons
}

/**
 * The favorite in a starter ball (Blake, 2026-10-01: "The note is on the tracker, I believe. As long as it fits in the
 * rules, I would say do it for the modes that allow favorites"). The Favorites Clause lets a player skip the blind
 * random pick when one of their favorites is among the three; this line says which ball.
 *
 * No PC tracker matches favorites against the balls (read 2026-09-07 and 2026-09-30), which is why a first version was
 * cut on 2026-09-07. It is back on Blake's word, on these terms:
 *  - Only in a Kaizo IronMON run on a Gen 3 game, whose tracker reads the balls. Not in IronMON Journey, whose Rule #1
 *    lets you take any starter anyway, and not in a Nuzlocke or a library game, which have no Favorites Clause.
 *  - Only a match is named. What the other balls hold is never shown, so with no favorite there the pick stays blind.
 *  - Only a favorite the run's own mode lets you take, by the official rules for that mode ([takeable]; Blake, asked
 *    whether to follow them mode by mode: "Yes"). A favorite past a limit gets no line, and the ball call stands. The
 *    BST is the game's own, read from the ROM.
 */
internal object FavoriteBall {
    /** IronMON Journey, whose Rule #1 reads "You can choose any of the 3 starters". */
    const val JOURNEY = "ironmonjourney"

    /** A favorite in a ball as the rules see it: its BST in this game and what kind of Pokemon it is. */
    data class Candidate(val bst: Int, val legendary: Boolean, val strongOrMythical: Boolean, val national: Int?)

    /** What the no-party card shows for the game in Play: its favorites, and in a run's lab the balls holding one. */
    fun shown(store: PrepStore, session: GameSession, tracker: GbaTracker?, filesDir: File): FavoritesShown =
        FavoritesShown { parts(store, session, tracker, filesDir) }

    private fun parts(store: PrepStore, session: GameSession, tracker: GbaTracker?, filesDir: File): FavoritesShown.Parts {
        val favorites = Favorites.slots(store, session.kind?.id, Favorites.slotCount(session.kind)).filter { it.isNotBlank() }
        val balls = runCatching {
            if (tracker == null || favorites.isEmpty() || PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON) emptyList()
            else lines(favorites, modeOf(store), session.kind?.isNatDex == true, tracker.starters(), maxDex = session.kind?.isMaxDex == true) { tracker.baseStats(it)?.bst }
        }.getOrDefault(emptyList())
        return FavoritesShown.Parts(Favorites.line(favorites), balls, FavoriteIcons.of(favorites, session.kind))
    }

    /** The run's mode ("kaizo", "survival"), from its settings file and sidecar; null when neither names one. */
    fun modeOf(store: PrepStore): String? =
        store.loadLastRun()?.second?.let { RnqsInfo.of(store.settingsFile(it)).ruleset }

    /**
     * "FAVORITE! GENGAR IN THE LEFT BALL" for each ball, left to right, that holds a favorite [mode] lets you take.
     * [maxDex]: MaxDex 1.0, held to the Nat. Dex 1.1.3 limits ([takeable]).
     */
    fun lines(
        favorites: List<String>, mode: String?, natDex: Boolean, balls: List<GbaTracker.BallOption>, maxDex: Boolean = false, bst: (Int) -> Int?,
    ): List<String> {
        val legendaries = favorites.count { FavoriteRules.isLegendary(it) }
        return balls.mapNotNull { b ->
            // By the game's own id: a Nat. Dex favourite counts for its own form only, as those books say. MaxDex's ids are
            // its own past 1235 (its Z-A Megas), so its names are read from its own table.
            val favorite = favorites.firstOrNull { Favorites.idOf(it, maxDex) == b.species } ?: return@mapNotNull null
            val stats = bst(b.species) ?: return@mapNotNull null
            val c = Candidate(stats, FavoriteRules.isLegendary(favorite), FavoriteRules.isStrongOrMythical(favorite), FavoriteRules.nationalOfName(favorite))
            if (takeable(mode, natDex, c, legendaries, maxDex)) "FAVORITE! ${favorite.trim().uppercase()} IN THE ${b.ball} BALL" else null
        }
    }

    /**
     * Whether a favorite may be taken from its ball in [mode], by the official rules (ironmon.gg's rules, rules last
     * changed 2025-02-23; the Nat. Dex Ruleset Changes, last changed 2026-06-29; read again 2026-10-01).
     *
     * Every mode: at most one legendary among the favorites ([legendariesInList]), and none in Super Kaizo ("No
     * Legendary favorites") or Survival ("No Legendaries or 580+ BST Pokemon as favorites").
     *
     * The games' own rules: Standard and Ultimate set no BST limit. Kaizo and the modes on it ban "599+ BST Pokémon" and
     * want a legendary favorite "under 600 BST" (no Pokemon has 599, so under 600). Evo Kaizo makes "600 BST and lower
     * Mons ... LEGAL in LAB" (a legendary favorite stays under 600). Survival, under 580.
     *
     * Nat. Dex (1.2.0 and newer): "Your favourites can be up to 600 BST, and if you are playing Standard or Ultimate,
     * any Pokémon above 600 BST that is not categorized as Strong Legendary or Mythical ... is also allowed", in
     * every mode (its older line named Survival among the 600 ones); no Cosmoem starter outside Standard; Evo Kaizo no
     * Strong Legendary or Mythical starter.
     *
     * MaxDex ([maxDex]) is held to the same page's "v1.0.0 to v1.1.3 only" lines, the version it is built on (Blake,
     * 2026-10-03: "Max dex is allowed a bst 600 pokemon"): "Your favourites can be up to 600 BST in Kaizo, Survival and
     * Super Kaizo, or 640 BST in Standard and Ultimate". That section has no Cosmoem rule and no Evo Kaizo part, so Evo
     * Kaizo takes Kaizo's 600 there.
     *
     * A run whose mode nothing names (a build of your own) has no Favorites Clause to apply, and Journey needs none.
     */
    fun takeable(mode: String?, natDex: Boolean, c: Candidate, legendariesInList: Int, maxDex: Boolean = false): Boolean {
        if (mode == null || mode == JOURNEY || c.bst <= 0) return false
        if (c.legendary && (legendariesInList > 1 || mode in FavoriteRules.NO_LEGENDARY_MODES)) return false
        val plain = mode == "standard" || mode == "ultimate"
        if (maxDex) return c.bst <= if (plain) 640 else 600
        return if (natDex) when {
            c.national == FavoriteRules.COSMOEM && mode != "standard" -> false
            mode == "evokaizo" -> c.bst <= 600 && !c.strongOrMythical
            plain -> c.bst <= 600 || !c.strongOrMythical
            else -> c.bst <= 600
        } else when {
            plain -> true
            mode == "survival" -> c.bst < 580
            mode == "evokaizo" && !c.legendary -> c.bst <= 600
            else -> c.bst < 600
        }
    }
}
