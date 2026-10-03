package com.ironmonone.app

import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * The reference trackers' encounter bookkeeping, fed one tracker update at a time and
 * written into the run's [StatMarks], so the counts last as long as the run (the
 * reference keeps them in its tracked data), not as long as the Play screen.
 *
 * GBA, Battle.lua: an opposing battler is counted once per battle, the first time it is
 * on the field (BattleParties[1][slot].seenAlready, :522 and :532), as a wild or a
 * trainer encounter (Tracker.TrackEncounter); a Pokemon Tower ghost is never counted
 * (:505). When the battle ends every Pokemon of the enemy party has its level recorded
 * (Tracker.recordLastLevelsSeen, :816, levels 1-100), so "Last seen" shows the level
 * from before this battle all through it.
 *
 * DS, BattleHandlerGen4.lua:190 and BattleHandlerGen5.lua:277: every time a new Pokemon
 * becomes the active enemy it is counted (logNewEnemyPokemonInBattle, one count, the
 * card's "Total seen"), the one it replaced has its level recorded, and when the battle
 * ends so does the one still out (BattleHandlerBase.lua:364). The reference does this for
 * each enemy slot (its battleData slots), so a double or triple battle's other opponents are
 * counted and recorded the same way, each in its own slot (rc34).
 *
 * The battle state is kept per session for the life of the process, so leaving the Play
 * screen mid-battle and coming back does not count the same Pokemon twice.
 */
class EncounterBook {
    /** One opposing battler on the field: a key that stays the same for that Pokemon all battle. */
    data class Battler(val key: String, val species: Int, val level: Int)

    /** What the book needs from one GBA or Game Boy update. */
    data class Gba(val inBattle: Boolean, val wild: Boolean, val onField: List<Battler>, val party: List<Pair<Int, Int>>)

    /** One more opposing slot's Pokemon in a DS double or triple battle: its PID, species and level. */
    data class DsFoe(val pid: Long, val species: Int, val level: Int)

    /** What the book needs from one DS update: the active enemy's PID, species and level. */
    /** [area]: the DS area name a wild Pokemon is met in, for its encounter frame (Tracker.updateEncounterData). */
    /** [others]: a double or triple battle's other opposing slots, from the second, null for one that read nothing this tick. */
    data class Ds(
        val inBattle: Boolean, val wild: Boolean, val pid: Long?, val species: Int, val level: Int, val area: String = "",
        val others: List<DsFoe?> = emptyList(),
    )

    private val counted = HashSet<String>()
    private val seen = LinkedHashMap<String, Pair<Int, Int>>()
    private var lastParty: List<Pair<Int, Int>> = emptyList()
    private var gbaInBattle = false
    private var dsInBattle = false
    private var dsActive: Long? = null
    private var dsActiveMon: Pair<Int, Int>? = null
    /** The same for a double or triple battle's other opposing slots, by their place in [Ds.others]. */
    private val dsOthers = HashMap<Int, Long>()
    private val dsOthersMon = HashMap<Int, Pair<Int, Int>>()

    /** A new run: nothing of the last one's battle carries over. */
    fun reset() {
        counted.clear(); seen.clear(); lastParty = emptyList(); gbaInBattle = false
        dsInBattle = false; dsActive = null; dsActiveMon = null; dsOthers.clear(); dsOthersMon.clear()
    }

    /** One GBA or Game Boy update; null (no read yet) changes nothing. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onGba(marks: StatMarks, u: Gba?, save: Boolean): Boolean {
        if (u == null) return false
        var changed = false
        if (u.inBattle) {
            gbaInBattle = true
            if (u.party.isNotEmpty()) lastParty = u.party
            for (b in u.onField) {
                if (b.species <= 0) continue
                seen[b.key] = b.species to b.level
                if (counted.add(b.key)) { marks.trackEncounter(b.species, u.wild, save); changed = true }
            }
        } else if (gbaInBattle) {
            gbaInBattle = false
            // The enemy party where the game's is read (every GBA game); on a Game Boy game,
            // whose party is not read, the Pokemon that were seen.
            if (marks.recordLastLevels(lastParty.ifEmpty { seen.values.toList() }, save)) changed = true
            counted.clear(); seen.clear(); lastParty = emptyList()
        }
        return changed
    }

    /** One DS update; null (no read yet) changes nothing. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onDs(marks: StatMarks, u: Ds?, save: Boolean): Boolean {
        if (u == null) return false
        var changed = false
        if (u.inBattle) {
            dsInBattle = true
            if (u.pid != null && u.species > 0) {
                if (u.pid != dsActive) {
                    marks.trackEncounter(u.species, u.wild, save)
                    // Tracker.updateEncounterData: a new wild Pokemon on the area's encounter frame. Here, with the count,
                    // so a battle not fetched (a catching demonstration) is on neither (rc33 audit P1 #80).
                    if (u.wild && save) marks.seeDsEncounter(u.area, u.species, u.level)
                    dsActiveMon?.let { marks.recordLastLevels(listOf(it), save) }
                    dsActive = u.pid
                    changed = true
                }
                dsActiveMon = u.species to u.level
            }
            // The other opposing slots, each as the first is: a new Pokemon in a slot is counted, put on the area's frame
            // when wild, and the one it replaced in that slot has its level recorded.
            u.others.forEachIndexed { k, f ->
                if (f == null || f.species <= 0) return@forEachIndexed
                if (f.pid != dsOthers[k]) {
                    marks.trackEncounter(f.species, u.wild, save)
                    if (u.wild && save) marks.seeDsEncounter(u.area, f.species, f.level)
                    dsOthersMon[k]?.let { marks.recordLastLevels(listOf(it), save) }
                    dsOthers[k] = f.pid
                    changed = true
                }
                dsOthersMon[k] = f.species to f.level
            }
        } else if (dsInBattle) {
            dsInBattle = false
            dsActiveMon?.let { if (marks.recordLastLevels(listOf(it), save)) changed = true }
            if (dsOthersMon.isNotEmpty() && marks.recordLastLevels(dsOthersMon.values.toList(), save)) changed = true
            dsActive = null; dsActiveMon = null; dsOthers.clear(); dsOthersMon.clear()
        }
        return changed
    }

    companion object {
        private val books = HashMap<String, EncounterBook>()

        /** The book for [id] (a run's seed in Play, so a new run never inherits the last one's battle), kept for the life of the process. */
        fun of(id: String): EncounterBook = synchronized(books) { books.getOrPut(id) { EncounterBook() } }

        /**
         * The book's view of one GBA or Game Boy tracker update. Null for no state, or an unreadable one: Play starts every
         * visit with no state, and reading that as "not in battle" ended the battle the player came back to, counted it
         * again and filed its levels (rc33 audit P1 #15, #30).
         */
        fun gbaUpdate(s: TrackerState?): Gba? {
            if (s == null || s.unreadable) return null
            if (!s.inBattle) return Gba(false, false, emptyList(), emptyList())
            val party = s.enemyParty.map { it.species to it.level }
            val e = s.enemy
            // Battle.lua:505: nothing is counted for a Pokemon Tower ghost; its party still
            // gets its levels at the end (the real Pokemon, as the reference records).
            if (e == null || e.isGhost || e.species <= 0) return Gba(true, s.isWildBattle, emptyList(), party)
            if (s.enemyOnField.isEmpty()) {
                // No party slots on this game (Game Boy): the Pokemon on screen, by its PID.
                val key = if (e.pid != 0L) "p${e.pid}:${e.species}" else "sp${e.species}"
                return Gba(true, s.isWildBattle, listOf(Battler(key, e.species, e.level)), party)
            }
            val onField = s.enemyOnField.mapNotNull { slot -> s.enemyParty.firstOrNull { it.slot == slot } }
            // Right after a battle starts the slot can still be the last battle's: count only
            // once it agrees with the Pokemon on screen.
            if (onField.isEmpty() || onField[0].species != e.species) return Gba(true, s.isWildBattle, emptyList(), party)
            return Gba(true, s.isWildBattle, onField.map { Battler("s${it.slot}", it.species, it.level) }, party)
        }

        /** The book's view of one DS tracker update; null for no state yet (see [gbaUpdate]). */
        fun dsUpdate(s: NdsTrackerState?): Ds? {
            if (s == null) return null
            if (!s.inBattle) return Ds(false, false, null, 0, 0)
            // Still in battle with no enemy read this tick (a switch), or a battle not fetched (a catching demonstration,
            // rc33 audit P1 #80): nothing to count, no end.
            val e = s.enemy?.takeIf { s.battleFetched } ?: return Ds(true, s.isWildBattle, null, 0, 0)
            // A double or triple battle's other opponents, your partner's left out (NdsTrackerState.opponentSlots).
            val others = s.opponentSlots.drop(1).map { o -> o?.let { DsFoe(it.mon.pid, it.mon.species, it.mon.level) } }
            return Ds(true, s.isWildBattle && s.enemyTrainerId == 0, e.mon.pid, e.mon.species, e.mon.level, s.areaName, others)
        }
    }
}

/**
 * The moves the DS notebook records: every opposing Pokemon on the field's (NdsTrackerState.opponents), as the DS tracker
 * checks each enemy slot's PP (BattleHandlerBase.updateAllPokemonInBattle and checkEnemyPP). Its moves are already the
 * used ones (NdsTracker.usedOnly), so every one is a sighting. Only the first opponent's were recorded until rc34.
 */
internal object DsFoeMoves {
    /** What Play keys the recording on: each opponent's species and moves, so a change in any of them records it. */
    fun key(s: NdsTrackerState?): List<Pair<Int, List<com.ironmonone.tracker.nds.NdsMoveInfo>>> =
        s?.opponents?.map { it.mon.species to it.moves }.orEmpty()

    /** Records every opponent's moves into [marks]; true when any was new. */
    fun record(marks: StatMarks, s: NdsTrackerState?): Boolean {
        var changed = false
        s?.opponents?.forEach { e -> if (marks.addMovesSeen(e.mon.species, e.moves.map { it.id to it.name }, e.mon.level)) changed = true }
        return changed
    }
}
