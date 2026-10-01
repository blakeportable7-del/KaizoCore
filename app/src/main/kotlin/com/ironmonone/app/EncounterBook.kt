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
 * ends so does the one still out (BattleHandlerBase.lua:364).
 *
 * The battle state is kept per session for the life of the process, so leaving the Play
 * screen mid-battle and coming back does not count the same Pokemon twice.
 */
class EncounterBook {
    /** One opposing battler on the field: a key that stays the same for that Pokemon all battle. */
    data class Battler(val key: String, val species: Int, val level: Int)

    /** What the book needs from one GBA or Game Boy update. */
    data class Gba(val inBattle: Boolean, val wild: Boolean, val onField: List<Battler>, val party: List<Pair<Int, Int>>)

    /** What the book needs from one DS update: the active enemy's PID, species and level. */
    data class Ds(val inBattle: Boolean, val wild: Boolean, val pid: Long?, val species: Int, val level: Int)

    private val counted = HashSet<String>()
    private val seen = LinkedHashMap<String, Pair<Int, Int>>()
    private var lastParty: List<Pair<Int, Int>> = emptyList()
    private var gbaInBattle = false
    private var dsInBattle = false
    private var dsActive: Long? = null
    private var dsActiveMon: Pair<Int, Int>? = null

    /** A new run: nothing of the last one's battle carries over. */
    fun reset() {
        counted.clear(); seen.clear(); lastParty = emptyList(); gbaInBattle = false
        dsInBattle = false; dsActive = null; dsActiveMon = null
    }

    /** One GBA or Game Boy update. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onGba(marks: StatMarks, u: Gba, save: Boolean): Boolean {
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

    /** One DS update. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onDs(marks: StatMarks, u: Ds, save: Boolean): Boolean {
        var changed = false
        if (u.inBattle) {
            dsInBattle = true
            if (u.pid != null && u.species > 0) {
                if (u.pid != dsActive) {
                    marks.trackEncounter(u.species, u.wild, save)
                    dsActiveMon?.let { marks.recordLastLevels(listOf(it), save) }
                    dsActive = u.pid
                    changed = true
                }
                dsActiveMon = u.species to u.level
            }
        } else if (dsInBattle) {
            dsInBattle = false
            dsActiveMon?.let { if (marks.recordLastLevels(listOf(it), save)) changed = true }
            dsActive = null; dsActiveMon = null
        }
        return changed
    }

    companion object {
        private val books = HashMap<String, EncounterBook>()

        /** The book for session [id], kept for the life of the process. */
        fun of(id: String): EncounterBook = synchronized(books) { books.getOrPut(id) { EncounterBook() } }

        /** The book's view of one GBA or Game Boy tracker update. */
        fun gbaUpdate(s: TrackerState?): Gba {
            if (s == null || !s.inBattle) return Gba(false, false, emptyList(), emptyList())
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

        /** The book's view of one DS tracker update. */
        fun dsUpdate(s: NdsTrackerState?): Ds {
            if (s == null || !s.inBattle) return Ds(false, false, null, 0, 0)
            // Still in battle with no enemy read this tick (a switch): nothing to count, no end.
            val e = s.enemy ?: return Ds(true, s.isWildBattle, null, 0, 0)
            return Ds(true, s.isWildBattle, e.mon.pid, e.mon.species, e.mon.level)
        }
    }
}
