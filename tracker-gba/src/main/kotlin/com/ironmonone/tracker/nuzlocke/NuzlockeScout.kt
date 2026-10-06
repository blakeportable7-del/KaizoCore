package com.ironmonone.tracker.nuzlocke

/*
 * Boss scouting (2026-10-06, Nuzlify's "trainer scouting"): the teams of the gym leaders, the Elite Four and the
 * Champion still to come, with their levels, moves and held items, read out of the loaded game.
 *
 * In a game whose trainers are the game's own this is public knowledge: every guide prints these teams, and a
 * Nuzlocke player plans the run around them. In a randomized game it is exactly what only the randomizer log knows,
 * so the screen must show nothing there. That fence is [NuzlockeScout.view]'s job, and it is in code, not in the
 * screen: a run the app randomized, a Randomizer preset, a game whose first gym leaders do not have their own teams,
 * or a game where that cannot be told, all get a closed view and the game's trainer data is never even read.
 */

/** One move of a scouted Pokemon. [power] 0 is a status move; [type] is a Gen 3 type id, null when unknown. */
data class ScoutMove(val id: Int, val name: String, val power: Int, val type: Int?, val accuracy: Int?)

/** One Pokemon of a boss's team as the game holds it. [ivs] is the 0 to 31 value every stat of it gets. */
data class ScoutMon(
    val species: Int,
    val speciesName: String,
    val level: Int,
    /** The held item's name, or null for none. */
    val item: String?,
    val moves: List<ScoutMove>,
    val ivs: Int,
    /** Its types, Gen 3 type ids, and base stats (hp, atk, def, spe, spa, spd); empty when the game would not give them. */
    val types: List<Int> = emptyList(),
    val base: List<Int> = emptyList(),
    /** Its moves are the last four it learned by its level, as the game gives a trainer's Pokemon with no moves of its own. */
    val defaultMoves: Boolean = false,
)

/** A boss's team: the boss row of the level cap table, the name the game gives them, and their Pokemon. */
data class ScoutTeam(val boss: BossCap, val name: String, val party: List<ScoutMon>)

/**
 * What a tracker gives the scouting: the teams in the loaded game. Each tracker that can read its game's trainer data
 * implements it; it is only asked once [NuzlockeScout.view]'s fence is open.
 */
interface ScoutSource {
    /** The team of the trainer with [trainerId] (the cap table's ids), or null when it cannot be read. */
    fun team(trainerId: Int): Pair<String, List<ScoutMon>>?

    /**
     * The tracker's own judgement whether the trainers' teams were randomized (Gen 3: the reference's check on the
     * first two gym leaders), or null when it has none. True closes the view whatever else says.
     */
    fun teamsRandomized(): Boolean?

    /** The species' name as this game spells it, for the check against the cap table's aces. */
    fun speciesName(species: Int): String

    /**
     * The ids of [boss] to read, the likeliest first: FireRed and LeafGreen's Champion has one team per starter the
     * rival may have, and the player's own comes first once the tracker knows it.
     */
    fun idsFor(boss: BossCap): List<Int> = boss.trainerIds
}

object NuzlockeScout {

    /** Why the view is closed, in words the screen shows. */
    enum class Closed(val text: String) {
        RANDOMIZED_RUN("This is a randomized game, so the bosses' teams are part of what you find out by playing. They are not shown."),
        NOT_OWN_TEAMS("The first gym leaders do not have their own teams in this game, so it looks changed or randomized. The teams are not shown."),
        CANNOT_TELL("The tracker cannot tell whether this game's trainers are its own, so the teams are not shown."),
        NO_DATA("The tracker cannot read the trainers' teams in this game."),
        NO_BOSSES("There is no boss table for this game."),
    }

    sealed class View {
        data class Hidden(val why: Closed) : View()
        /** [next] is the fight (or any-order group) that comes next; [later] the rest still standing, in order. */
        data class Shown(val next: List<ScoutTeam>, val later: List<ScoutTeam>) : View()
    }

    /** A randomized run's bind (NuzlockeStore.bindOfRun): the game, a slash and the seed. A library game's never has a slash. */
    fun isRunBind(bind: String): Boolean = '/' in bind

    /**
     * The fence: whether this run may see the bosses' teams. It is open only for a run on a library game with a
     * preset that is not the Randomizer, on a game whose first two gym leaders field their own aces at their own
     * levels and which the tracker does not itself judge randomized.
     */
    fun fence(meta: RunMeta, caps: LevelCapTable?, source: ScoutSource?): Closed? {
        if (isRunBind(meta.bind) || meta.rules.preset == NuzlockePreset.RANDOMIZER) return Closed.RANDOMIZED_RUN
        if (source == null) return Closed.NO_DATA
        if (caps == null || caps.bosses.isEmpty()) return Closed.NO_BOSSES
        when (source.teamsRandomized()) {
            true -> return Closed.NOT_OWN_TEAMS
            else -> {}
        }
        return when (ownTeams(caps, source)) {
            true -> null
            false -> Closed.NOT_OWN_TEAMS
            null -> Closed.CANNOT_TELL
        }
    }

    /**
     * The game's own teams check, for every game with a cap table: the first two gym leaders each field the table's
     * ace at the table's level. The table is the standard one (LevelCapTable.standard), so a changed game shows up as a
     * different species or level. Null when a team cannot be read.
     */
    fun ownTeams(caps: LevelCapTable, source: ScoutSource): Boolean? {
        val standard = LevelCapTable.standard(caps.game, systemOf(caps)).bosses.ifEmpty { caps.bosses }
        val gyms = standard.filter { it.kind == "gym" }.take(2)
        if (gyms.isEmpty()) return null
        for (g in gyms) {
            val aces = g.ace.split(" or ").map { norm(it) }.filter { it.isNotEmpty() }
            if (aces.isEmpty()) return null
            val parties = g.trainerIds.mapNotNull { source.team(it)?.second }.filter { it.isNotEmpty() }
            if (parties.isEmpty()) return null
            val fits = parties.any { party ->
                val top = party.maxOf { it.level }
                top == g.cap && party.any { it.level == top && norm(source.speciesName(it.species)) in aces }
            }
            if (!fits) return false
        }
        return true
    }

    // The cap tables of every system share the game keys' spelling, and only Gen 3's has these.
    private fun systemOf(caps: LevelCapTable): NuzlockeSystem = when (caps.game) {
        "rb", "y" -> NuzlockeSystem.GEN1
        "gs", "c" -> NuzlockeSystem.GEN2
        "dp", "pt", "hgss" -> NuzlockeSystem.GEN4
        "bw", "b2w2" -> NuzlockeSystem.GEN5
        else -> NuzlockeSystem.GEN3
    }

    private fun norm(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * What the bosses screen shows. The game's trainer data is read only once [fence] is open. [beaten] are the cap
     * table's keys already won.
     */
    fun view(meta: RunMeta, caps: LevelCapTable?, beaten: Set<String>, source: ScoutSource?): View {
        fence(meta, caps, source)?.let { return View.Hidden(it) }
        caps!!; source!!
        // Every fight of the table, the rival and team fights between the gyms too: knowing them is the point.
        val standing = caps.bosses.filter { it.key !in beaten }
        val nextKeys = caps.nextSet(beaten).map { it.key }.toSet()
        fun teamOf(b: BossCap): ScoutTeam? {
            val ids = source.idsFor(b)
            val read = ids.firstNotNullOfOrNull { id -> source.team(id)?.takeIf { it.second.isNotEmpty() } } ?: return null
            return ScoutTeam(b, read.first, read.second)
        }
        val teams = standing.mapNotNull { teamOf(it) }
        if (teams.isEmpty()) return View.Hidden(Closed.NO_DATA)
        val next = teams.filter { it.boss.key in nextKeys }.ifEmpty { listOf(teams.first()) }
        return View.Shown(next, teams - next.toSet())
    }

    /**
     * A trainer Pokemon with no moves of its own knows the last four moves it learned by its level, in the order it
     * learned them, a move it already knows not learned twice (GiveBoxMonInitialMoveset).
     */
    fun defaultMoves(learnset: List<Pair<Int, Int>>, level: Int): List<Int> {
        val out = ArrayList<Int>()
        for ((lv, move) in learnset) {
            if (lv > level) break
            if (move == 0 || move in out) continue
            if (out.size == 4) out.removeAt(0)
            out += move
        }
        return out
    }
}
