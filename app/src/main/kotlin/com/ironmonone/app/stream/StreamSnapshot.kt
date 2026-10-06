package com.ironmonone.app.stream

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTracker
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * What the stream page shows, as one JSON object per tick.
 *
 * The page is a clone of the PC tracker's box, so the snapshot carries the
 * same facts the in-app panel draws and nothing the panel would not: the
 * enemy's moves are the ones SEEN, its ability is the guess unless a battle
 * revealed it, and stats are the player's marks, never the real values.
 *
 * Per-run notes (marks, notes, moves seen, encounters) arrive through
 * [Notes], so the builder has no dependency on StatMarks' file layout and a
 * test can hand it a map.
 */
object StreamSnapshot {

    class Notes(
        val marksOf: (Int) -> IntArray = { IntArray(6) },
        val noteOf: (Int) -> String = { "" },
        val movesSeenOf: (Int) -> List<String> = { emptyList() },
        val abilityOf: (Int) -> String? = { null },
        val encountersOf: (Int) -> Int = { 0 },
        val lastSeenLevelOf: (Int) -> Int? = { null },
        /** How many of a map's species have been seen there this run. */
        val routeSeenOf: (Int) -> Int = { 0 },
    )

    /**
     * What the Play screen is playing. [isRun] is true for a randomized run: a Kaizo IronMON
     * run, or a Nuzlocke on a randomized game. Only the Kaizo IronMON run counts attempts: a
     * randomized Nuzlocke counts none ([nuzlocke], PrepStore.installRun), and in Play any
     * game, ROM Hacks and a standard Nuzlocke the attempt number would be the last run's,
     * so the stream must not show it (2026-09-29). The tracker page reads the snapshot's
     * "run", a run that is no Nuzlocke, and prints the attempt only when it is true
     * (2026-09-30, UX audit P0-17; rc32 audit P2 #109). [isRun] defaults to "has a seed",
     * which is what the Play screen passes for a run, and the Play screen passes it outright.
     */
    class Run(
        val title: String,
        val platform: String,
        val attempt: Int,
        val tracked: Boolean,
        val seed: String? = null,
        val isRun: Boolean = seed != null,
        /** The game's generation, for the move rules (1 and 2 are the Game Boy trackers, 3 the GBA one). */
        val generation: Int = 3,
        /** How the Kaizo IronMON run ended once it is latched (StreamHub.ended), else null: what the run-over view shows. */
        val ended: RunOutcome? = StreamHub.ended,
        /** A randomized Nuzlocke counts no attempt (PrepStore.installRun), so the page prints none for it. */
        val nuzlocke: Boolean = com.ironmonone.app.NuzlockeTracking.inPlay(),
    )

    /** True for a snapshot built for a run. The hub reads it from the JSON it was handed. */
    fun isRun(json: String): Boolean = json.contains("\"run\":true")

    /**
     * How a Kaizo IronMON run ended, as the game-over popup knows it (streamer list item 1, 2026-10-05): the latch's
     * [outcome] (GameOverLatch, the popup's own; never re-read from the game), the death card RunHistoryHook filed for
     * it ([card]: what ended the run, where, badges, time played, the best other run), the popup's line ([quote],
     * DeathQuotes, the same one), the team the latch kept ([team]), and the run's GachaMon card ([gachamon], the prize
     * card when one was made, else the fallen lead's capture; [gachamonFrom] "prize" or "lead").
     * [counted]: the attempt is printed (a Kaizo IronMON run counts one; the popup is for those only).
     */
    class Ending(
        val outcome: RunOutcome,
        val title: String,
        val attempt: Int,
        val seed: String?,
        val counted: Boolean = true,
        val card: com.ironmonone.app.DeathCard? = null,
        val quote: String? = null,
        val team: List<com.ironmonone.app.GameOverMon> = emptyList(),
        /** The badges as the game shows them, for when there is no card (a bit a badge). */
        val badgeBits: Int = 0,
        val gachamon: com.ironmonone.app.GachaMonEntry? = null,
        val gachamonFrom: String? = null,
    )

    /**
     * The game over card's data (/gameover.json, the `gameover` event), or null with no [e]: the run goes on. "key" names
     * the ending, so the page brings a card in once per ending and only refreshes it after that.
     */
    fun gameOver(e: Ending?): Map<String, Any?>? {
        e ?: return null
        val record = e.card?.record
        val won = e.outcome == RunOutcome.WON
        val headline = e.card?.headline()
        fun mon(m: com.ironmonone.app.RunRecord.Mon?) = m?.let { linkedMapOf("species" to it.species, "name" to it.name, "level" to it.level) }
        val fallen = record?.lead?.let { mon(it) }
            ?: e.team.firstOrNull { it.fainted }?.let { linkedMapOf("species" to it.species, "name" to it.name, "level" to it.level) }
        val g = e.gachamon
        return linkedMapOf(
            "key" to listOf(e.title, e.seed.orEmpty(), e.attempt, e.outcome.name).joinToString("|"),
            "outcome" to e.outcome.name,
            "won" to won,
            "title" to e.title,
            "attempt" to e.attempt.takeIf { e.counted },
            // "LOST TO", "ENDED ON" or "FIRST LOSS", and "Lv.21 Sandile (Hiker Marcos)": the popup's own first line.
            "label" to headline?.first,
            "cause" to headline?.second,
            "killer" to mon(record?.killer).takeIf { !won },
            "trainer" to record?.trainer.orEmpty(),
            "location" to record?.location.orEmpty(),
            "fallen" to fallen,
            "badges" to (record?.badges ?: Integer.bitCount(e.badgeBits)),
            "seconds" to record?.playSeconds?.takeIf { it > 0 },
            "newBest" to (e.card?.newBest == true),
            "best" to e.card?.bestText(),
            "integrity" to record?.let { com.ironmonone.app.integrityText(it.restores, it.resumes, it.resets, it.keptSave) },
            "quote" to e.quote?.takeIf { it.isNotBlank() },
            "gachamon" to g?.let {
                linkedMapOf(
                    "from" to (e.gachamonFrom ?: "lead"),
                    "species" to it.card.pokemonId,
                    "name" to it.speciesName,
                    "level" to it.card.level,
                    "stars" to it.stars,
                    "power" to it.card.battlePower,
                    "ability" to it.abilityName.takeIf { a -> a != "---" },
                    "moves" to (0 until 4).map { i -> it.moveName(i) }.filter { m -> m != "---" },
                    "shiny" to (it.card.isShiny == 1),
                    "trainer" to (it.notes.trainer.ifBlank { it.trainerName.orEmpty() }).takeIf { t -> t.isNotBlank() },
                )
            },
        )
    }

    /**
     * [gbaView] and [dsView] are the phone's battle views (the panels' own, as Play hands them): in a double or triple
     * battle the page shows the Pokemon the phone's swap shows, yours and the opponent's, and where each stands
     * ("own", "enemy" and "sides"). A single battle keeps the page as it was: the party's first and the one opponent.
     */
    internal fun build(
        run: Run,
        gba: TrackerState?,
        nds: NdsTrackerState?,
        notes: Notes,
        tracker: GbaTracker? = null,
        gbaView: com.ironmonone.app.GbaViewState? = null,
        dsView: com.ironmonone.app.DsViewState? = null,
    ): Map<String, Any?> {
        val view = gba ?: nds
        // "Hide stats until summary shown", as the phone's cards apply it (SummaryChecks.hides): yours and the opponent's.
        val hidden = gba != null && com.ironmonone.app.SummaryChecks.hides(run.attempt, gba.gameDataRandomized, run.generation)
        // The latched end of a Kaizo IronMON run, never the tracker's live read, which fires in any game (2026-09-30).
        val outcome: RunOutcome? = run.ended
        // A double or triple battle, as the phone's swap shows it: the Pokemon on its cards and where they stand.
        val gbaSpots = gba?.let { s -> gbaView?.shownSpots(s) }
        val dsSpots = nds?.let { s -> dsView?.shownSpots(s) }
        val gbaOwn = if (gbaSpots != null) gbaView?.own(gba) else null
        val dsOwn = if (dsSpots != null) dsView?.shownPlayer(nds!!) else null
        // The heals are a share of the Pokemon on the card (Program.recalcLeadPokemonHealingInfo): in a double battle the one shown.
        val (shownPercent, shownCount) = when {
            gbaOwn != null -> gbaView!!.heals(gba!!).let { it.percent to it.count }
            dsOwn != null -> com.ironmonone.tracker.nds.NdsHeals.totals(nds!!.healingItems, dsOwn.mon.maxHp, showHp = false)
            else -> (gba?.healPercent ?: nds?.healPercent ?: 0) to (gba?.healCount ?: nds?.healCount ?: 0)
        }
        // Hidden, the heals are 0 and 0, as the phone's strip and the reference's (HiddenCard, rc32 audit P2 #98).
        val (healPercent, healCount, _) = com.ironmonone.app.HiddenCard.heals(hidden, shownPercent, shownCount, 0)
        return linkedMapOf(
            "app" to "KaizoCore",
            "title" to run.title,
            "platform" to run.platform,
            "attempt" to run.attempt,
            "tracked" to run.tracked,
            "run" to (run.isRun && !run.nuzlocke),
            "seed" to run.seed,
            "inBattle" to (view?.inBattle ?: false),
            "wild" to (view?.isWildBattle ?: false),
            "outcome" to outcome?.name,
            "badges" to (gba?.badges ?: nds?.badges ?: 0),
            "badgeSet" to (gba?.badgeSet ?: nds?.badgeSet),
            "heals" to mapOf("percent" to healPercent, "count" to healCount),
            "route" to gba?.let { s ->
                s.routeName?.let { name ->
                    mapOf("name" to name, "total" to s.routeSpecies.size,
                        "trainers" to s.routeTrainers.size, "bosses" to s.routeBosses,
                        "seen" to (s.mapId?.let { notes.routeSeenOf(it) } ?: 0))
                }
            },
            "steps" to (gba?.steps ?: 0),
            "weather" to gba?.weather,
            "party" to (gba?.party?.map { own(it, notes, tracker, gba, run.generation, hidden = hidden) }
                ?: nds?.party?.map { ownNds(it, notes) } ?: emptyList<Any>()),
            // In a double or triple battle, your Pokemon the phone shows, its moves against the opponent shown; the page
            // draws the party's first otherwise.
            "own" to when {
                gbaOwn != null -> own(gbaOwn, notes, tracker, gba, run.generation, hidden = hidden, target = gbaView!!.ownTarget(gba!!))
                dsOwn != null -> ownNds(dsOwn, notes)
                else -> null
            },
            "enemy" to when {
                gbaSpots != null -> gbaView!!.foe(gba)?.let { enemy(it, gba!!, notes, tracker, run.generation, hidden, target = gbaView.foeTarget(gba)) }
                dsSpots != null -> dsView!!.shownEnemy(nds!!)?.let { enemyNds(it, notes) }
                gba?.enemy != null && gba.inBattle -> enemy(gba.enemy!!, gba, notes, tracker, run.generation, hidden)
                nds?.enemy != null && nds.inBattle -> enemyNds(nds.enemy!!, notes)
                else -> null
            },
            // Where each card's Pokemon stands on the game's screen, as the phone's banner says it (BattleSideWords).
            "sides" to (gbaSpots ?: dsSpots)?.let { (mine, theirs) ->
                mapOf("own" to com.ironmonone.app.BattleSideWords.one(mine).full, "foe" to com.ironmonone.app.BattleSideWords.one(theirs).full)
            },
            "enemyTeam" to (gba?.enemyTeam ?: emptyList<Boolean>()),
        )
    }

    private fun type(id: Int?): String? = id?.let { Gen3Types.name(it) }

    /**
     * A GBA move as the phone's tracker shows it (MoveDecor): power, accuracy and type after the variable-damage rules
     * and the "Reveal info if randomized" hiding, so the page reads Return's 102 and not the ROM's 1. A power or an
     * accuracy of "0" stays the ROM's 0, which the page draws as "-".
     */
    private fun moveRow(m: com.ironmonone.tracker.MoveRow, ctx: com.ironmonone.app.MoveContext?): Map<String, Any?> {
        val shown = with(com.ironmonone.app.MoveDecorAccess) { runCatching { m.shown(ctx) }.getOrNull() }
        return linkedMapOf(
            "name" to m.name, "pp" to (shown?.ppText ?: m.pp), "ppMax" to m.ppMax,
            "power" to (shown?.powerText?.takeIf { it != "0" } ?: m.power),
            "acc" to (shown?.accText?.takeIf { it != "0" } ?: m.acc),
            // A type the phone hides stays hidden here: null, not the ROM's.
            "type" to (if (shown != null) shown.typeName else type(m.type)),
            "category" to (shown?.category ?: m.category),
        )
    }

    private fun weightOf(tracker: GbaTracker?): ((Int) -> String?)? = tracker?.let { t -> { species: Int -> t.weight(species) } }

    /**
     * Your Pokemon as the phone's card draws it. [hidden] ("Hide stats until summary shown", before this attempt has
     * opened a summary): the reference's stand-in keeps the species, its types, BST and evolution, and nothing else,
     * so HP, ability, item, stats, stages and moves stay on the phone too (TrackerPanel's own card).
     */
    private fun own(
        p: TrackedMon, notes: Notes, tracker: GbaTracker?, s: TrackerState? = null, generation: Int = 3, hidden: Boolean = false,
        /** The opponent its moves are matched against: the one on the field, or in a double battle the one shown. */
        target: EnemyInfo? = s?.enemy?.takeIf { s.inBattle },
    ): Map<String, Any?> {
        val ctx = com.ironmonone.app.ownMoveContext(p, target, s?.weather, weightOf(tracker))
            .copy(generation = generation)
        val m = p.mon
        return linkedMapOf(
            "species" to m.species, "name" to p.speciesName, "level" to m.level,
            "hp" to m.curHp.takeIf { !hidden }, "maxHp" to m.maxHp.takeIf { !hidden },
            "types" to listOfNotNull(type(p.base?.type1), type(p.base?.type2).takeIf { p.base?.type2 != p.base?.type1 }),
            "ability" to p.abilityName.takeIf { !hidden }, "item" to p.itemName.takeIf { !hidden },
            "stats" to if (hidden) null else mapOf("hp" to m.maxHp, "atk" to m.atk, "def" to m.def, "spa" to m.spAtk,
                "spd" to m.spDef, "spe" to m.spe),
            "bst" to p.base?.bst,
            "status" to p.statusCondition,
            "stages" to if (hidden) emptyMap() else p.statStages,
            "moves" to if (hidden) emptyList() else p.moveRows.map { moveRow(it, ctx) },
            "movesLearned" to p.movesLearned, "movesTotal" to p.movesTotal,
            "nextMoveLevel" to p.nextMoveLevel,
            // The card's words, not the table's key: "L.CORD", not LINKING_CORD (rc33 audit P1 #67 made Nat. Dex keys common).
            "evolution" to com.ironmonone.tracker.EvoText.abbreviation(tracker?.evolution(m.species)),
            "note" to notes.noteOf(m.species),
        )
    }

    private fun enemy(
        e: EnemyInfo, s: TrackerState, notes: Notes, tracker: GbaTracker? = null, generation: Int = 3, hidden: Boolean = false,
        /** Your Pokemon its moves are matched against: the one on the field (TrackerState.onField), in a double battle the one shown. */
        target: TrackedMon? = s.onField,
    ): Map<String, Any?> {
      val ctx = com.ironmonone.app.enemyMoveContext(e, target, s.weather, weightOf(tracker))
          .copy(hide = com.ironmonone.app.InfoRules.hiddenMoveInfo(s.randomized), generation = generation)
      return linkedMapOf(
        "species" to e.species, "name" to e.speciesName, "level" to e.level,
        // A percentage only: the PC trackers never print the opponent's HP, and its max HP and level give back the
        // randomized base HP (2026-09-30, IronMON rules check).
        "hpPercent" to if (e.maxHp > 0) (e.curHp * 100 / e.maxHp) else 0,
        // "?" where the phone hides randomized types ("Reveal info if randomized" off).
        "types" to if (com.ironmonone.app.InfoRules.hidesRandomizedTypes(s.randomized)) listOf("?")
            else listOfNotNull(type(e.type1), type(e.type2).takeIf { e.type2 != e.type1 }),
        "bst" to e.base?.bst,
        // Marks are the player's guesses (0 none, 1 +, 2 -, 3 =), never real stats.
        "marks" to notes.marksOf(e.species).toList(),
        "movesSeen" to (notes.movesSeenOf(e.species) + e.movesSeen).distinct(),
        "moves" to e.moveRows.map { moveRow(it, ctx) },
        "ability" to (notes.abilityOf(e.species) ?: s.abilityRevealed?.takeIf { it.first == e.species }?.second),
        // The two abilities from the ROM only where the phone's card shows them (InfoRules.canShowAbilities): an
        // unrandomized game, or Open Book. They were sent in every battle, the randomized game's own data.
        "abilityGuess" to e.abilityGuess.takeIf { com.ironmonone.app.InfoRules.canShowAbilities(s.randomized) },
        "encounters" to notes.encountersOf(e.species),
        "lastSeenLevel" to notes.lastSeenLevelOf(e.species),
        "note" to notes.noteOf(e.species),
        // Hidden, the phone draws the stand-in's neutral stages (EnemyView.stageRows).
        "stages" to if (hidden) emptyMap() else e.statStages,
      )
    }

    private fun ownNds(p: NdsTrackedMon, notes: Notes): Map<String, Any?> {
        val m = p.mon
        return linkedMapOf(
            "species" to m.species, "name" to p.speciesName, "level" to m.level,
            "hp" to m.curHp, "maxHp" to m.maxHp,
            "types" to listOfNotNull(p.info?.type1, p.info?.type2?.takeIf { it != p.info?.type1 && it.isNotBlank() }),
            "ability" to p.abilityName, "item" to p.itemName,
            "stats" to mapOf("hp" to m.maxHp, "atk" to m.atk, "def" to m.def, "spa" to m.spAtk,
                "spd" to m.spDef, "spe" to m.spe),
            "bst" to p.info?.bst,
            "status" to p.statusCondition,
            "stages" to p.statStages,
            "moves" to p.moves.mapIndexed { i, mv -> linkedMapOf(
                "name" to mv.name, "pp" to m.pp.getOrNull(i), "ppMax" to null,
                "power" to mv.power, "acc" to mv.accuracy, "type" to mv.type, "category" to mv.category) },
            "movesLearned" to p.movesLearned, "movesTotal" to p.movesTotal,
            "nextMoveLevel" to p.nextMoveLevel,
            "note" to notes.noteOf(m.species),
        )
    }

    private fun enemyNds(e: NdsTrackedMon, notes: Notes): Map<String, Any?> {
        val m = e.mon
        return linkedMapOf(
            "species" to m.species, "name" to e.speciesName, "level" to m.level,
            "hpPercent" to if (m.maxHp > 0) (m.curHp * 100 / m.maxHp) else 0,
            "types" to listOfNotNull(e.info?.type1, e.info?.type2?.takeIf { it != e.info?.type1 && it.isNotBlank() }),
            "bst" to e.info?.bst,
            "marks" to notes.marksOf(m.species).toList(),
            "movesSeen" to notes.movesSeenOf(m.species),
            "moves" to emptyList<Any>(),
            "ability" to notes.abilityOf(m.species),
            // No abilities from the randomizer's sidecar: the DS tracker shows none for an opponent (2026-09-30).
            "encounters" to notes.encountersOf(m.species),
            "lastSeenLevel" to notes.lastSeenLevelOf(m.species),
            "note" to notes.noteOf(m.species),
            "stages" to e.statStages,
        )
    }

    /**
     * Every species as the randomizer left it: what the PC tracker's log
     * viewer shows after a run. Per-run notes are merged by the page.
     */
    fun dex(tracker: GbaTracker?, nds: NdsTracker?, maxSpecies: Int): List<Map<String, Any?>> {
        if (tracker != null) {
            return (1 until maxSpecies).mapNotNull { sp ->
                val b = tracker.baseStats(sp) ?: return@mapNotNull null
                if (b.bst == 0) return@mapNotNull null
                val name = tracker.speciesName(sp)
                if (name.isBlank() || name.startsWith("#") || name.startsWith("?")) return@mapNotNull null
                linkedMapOf(
                    "species" to sp, "name" to name,
                    "types" to listOfNotNull(type(b.type1), type(b.type2).takeIf { b.type2 != b.type1 }),
                    "stats" to mapOf("hp" to b.hp, "atk" to b.atk, "def" to b.def, "spa" to b.spAtk, "spd" to b.spDef, "spe" to b.spe),
                    "bst" to b.bst,
                    "abilities" to listOfNotNull(tracker.abilityName(b.ability1),
                        tracker.abilityName(b.ability2).takeIf { b.ability2 != 0 && b.ability2 != b.ability1 }),
                    "learnset" to tracker.learnset(sp).map { (lv, mv) -> mapOf("level" to lv, "move" to tracker.moveName(mv)) },
                    "evolution" to com.ironmonone.tracker.EvoText.detailed(tracker.evolution(sp), tracker.friendshipRequired()).joinToString(" / "),
                    "weight" to tracker.weight(sp),
                )
            }
        }
        if (nds != null) {
            return (1 until maxSpecies).mapNotNull { sp ->
                val name = nds.speciesName(sp)
                if (name.isBlank() || name.startsWith("#")) return@mapNotNull null
                linkedMapOf("species" to sp, "name" to name,
                    "moveLevels" to nds.moveLevelsOf(sp))
            }
        }
        return emptyList()
    }
}
