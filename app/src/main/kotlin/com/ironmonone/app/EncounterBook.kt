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
 * screen mid-battle and coming back does not count the same Pokemon twice. It is also written
 * into the run's encounters.txt with the counts (StatMarks.commitBattle), so an app start resumed
 * into the same battle does not count it again either: until rc35.3 a new process began with an
 * empty book, and every restart in Lorelei's battle added one to Leafeon's Seen (Trainer) (rc35.2
 * QA). The PC tracker never meets this case, since it does not start over in the middle of a
 * battle the way a phone app does when it is closed.
 */
class EncounterBook {
    /** One opposing battler on the field: a key that stays the same for that Pokemon all battle. */
    data class Battler(val key: String, val species: Int, val level: Int)

    /**
     * What the book needs from one GBA or Game Boy update. [sig]: the opposing party as slot, species and personality
     * value, which tells one battle from another across an app restart (the battlers' keys are party slots); "" on a
     * Game Boy game, whose party is not read.
     */
    data class Gba(
        val inBattle: Boolean, val wild: Boolean, val onField: List<Battler>, val party: List<Pair<Int, Int>>, val sig: String = "",
    )

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

    // The battle in progress as the run's encounters.txt holds it (StatMarks.battleLatch), for a restart. Read on the
    // book's first saved update; a battle found there waits ([gbaPending], [dsPending]) for the first update that shows
    // an opponent, which either is that battle (nothing counted again) or another (the old one ended while the app was
    // closed: its levels are recorded, as at any battle's end).
    private var gbaLoaded = false
    private var gbaPending: GbaLatch? = null
    private var gbaSig = ""
    private var gbaWild = false
    private var gbaCommitted: String? = null
    private var dsLoaded = false
    private var dsPending: DsLatch? = null
    private var dsWild = false
    /** Every PID that has been an opposing Pokemon on the field this DS battle. */
    private val dsPids = LinkedHashSet<Long>()
    private var dsCommitted: String? = null

    /** A new run: nothing of the last one's battle carries over. */
    fun reset() {
        counted.clear(); seen.clear(); lastParty = emptyList(); gbaInBattle = false
        dsInBattle = false; dsActive = null; dsActiveMon = null; dsOthers.clear(); dsOthersMon.clear()
        gbaLoaded = false; gbaPending = null; gbaSig = ""; gbaWild = false; gbaCommitted = null
        dsLoaded = false; dsPending = null; dsWild = false; dsPids.clear(); dsCommitted = null
    }

    /** A GBA or Game Boy battle in progress as written to the run (StatMarks.battleLatch "gba"). */
    internal data class GbaLatch(
        val wild: Boolean, val sig: String, val keys: Set<String>, val seen: Map<String, Pair<Int, Int>>, val party: List<Pair<Int, Int>>,
    ) {
        /** The battle [u] shows is this one: the same party (or, on a Game Boy game, a Pokemon this battle already counted). */
        fun sameBattle(u: Gba): Boolean =
            wild == u.wild && sig == u.sig && (sig.isNotEmpty() || u.onField.any { it.key in keys })

        /** The levels this battle's end records (as [onGba] does): the party, else on a Game Boy game what was seen. */
        fun levels(): List<Pair<Int, Int>> = party.ifEmpty { seen.values.toList() }

        fun encode(): String = listOf(
            "w=" + (if (wild) 1 else 0),
            "sig=$sig",
            "keys=" + keys.joinToString(","),
            "seen=" + seen.entries.joinToString(",") { "${it.key}/${it.value.first}/${it.value.second}" },
            "party=" + party.joinToString(",") { "${it.first}/${it.second}" },
        ).joinToString(";")

        companion object {
            fun parse(text: String): GbaLatch? = runCatching {
                val f = fields(text)
                GbaLatch(
                    wild = f["w"] == "1", sig = f["sig"].orEmpty(),
                    keys = list(f["keys"]).toSet(),
                    seen = list(f["seen"]).associate { e -> e.split('/').let { it[0] to (it[1].toInt() to it[2].toInt()) } },
                    party = pairs(f["party"]),
                )
            }.getOrNull()
        }
    }

    /** A DS battle in progress as written to the run (StatMarks.battleLatch "ds"). */
    internal data class DsLatch(val wild: Boolean, val pids: Set<Long>, val levels: List<Pair<Int, Int>>) {
        fun encode(): String = listOf(
            "w=" + (if (wild) 1 else 0),
            "pids=" + pids.joinToString(","),
            "lv=" + levels.joinToString(",") { "${it.first}/${it.second}" },
        ).joinToString(";")

        companion object {
            fun parse(text: String): DsLatch? = runCatching {
                val f = fields(text)
                DsLatch(f["w"] == "1", list(f["pids"]).map { it.toLong() }.toSet(), pairs(f["lv"]))
            }.getOrNull()
        }
    }

    private fun gbaLatch(): GbaLatch? = if (!gbaInBattle) null else GbaLatch(gbaWild, gbaSig, counted.toSet(), seen.toMap(), lastParty)

    private fun dsLatch(): DsLatch? = if (!dsInBattle) null
        else DsLatch(dsWild, dsPids.toSet(), listOfNotNull(dsActiveMon) + dsOthersMon.toSortedMap().values)

    /** One GBA or Game Boy update; null (no read yet) changes nothing. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onGba(marks: StatMarks, u: Gba?, save: Boolean): Boolean {
        if (u == null) return false
        var changed = false
        if (save && !gbaLoaded) {
            gbaLoaded = true
            gbaCommitted = marks.battleLatch(GBA)
            if (!gbaInBattle) gbaPending = gbaCommitted?.let { GbaLatch.parse(it) }
        }
        if (u.inBattle) {
            gbaPending?.takeIf { u.onField.isNotEmpty() }?.let { p ->
                gbaPending = null
                if (p.sameBattle(u)) { counted += p.keys; seen.putAll(p.seen); lastParty = p.party }
                else if (marks.recordLastLevels(p.levels(), save = false)) changed = true
            }
            gbaInBattle = true
            gbaWild = u.wild
            if (u.sig.isNotEmpty()) gbaSig = u.sig
            if (u.party.isNotEmpty()) lastParty = u.party
            for (b in u.onField) {
                if (b.species <= 0) continue
                seen[b.key] = b.species to b.level
                if (counted.add(b.key)) { marks.trackEncounter(b.species, u.wild, save = false); changed = true }
            }
        } else if (gbaInBattle) {
            gbaInBattle = false
            // The enemy party where the game's is read (every GBA game); on a Game Boy game,
            // whose party is not read, the Pokemon that were seen.
            if (marks.recordLastLevels(lastParty.ifEmpty { seen.values.toList() }, save = false)) changed = true
            counted.clear(); seen.clear(); lastParty = emptyList(); gbaSig = ""
        }
        if (save) {
            // The counts and the battle they belong to in one write, so a kill between two writes cannot leave a
            // count without its battle (counted again on the next start).
            val latch = gbaPending?.encode() ?: gbaLatch()?.encode()
            if (changed || latch != gbaCommitted) { marks.commitBattle(GBA, latch); gbaCommitted = latch }
        }
        return changed
    }

    /** One DS update; null (no read yet) changes nothing. [save] false keeps it off disk (Demo). True when anything was recorded. */
    fun onDs(marks: StatMarks, u: Ds?, save: Boolean): Boolean {
        if (u == null) return false
        var changed = false
        if (save && !dsLoaded) {
            dsLoaded = true
            dsCommitted = marks.battleLatch(DS)
            if (!dsInBattle) dsPending = dsCommitted?.let { DsLatch.parse(it) }
        }
        if (u.inBattle) {
            dsPending?.takeIf { u.pid != null && u.species > 0 }?.let { p ->
                dsPending = null
                if (p.wild == u.wild && u.pid != null && u.pid in p.pids) {
                    // The same battle: the Pokemon out now, and any other slot's that this battle had, are not new.
                    dsPids += p.pids
                    dsActive = u.pid
                    u.others.forEachIndexed { k, f -> if (f != null && f.pid in p.pids) dsOthers[k] = f.pid }
                } else if (marks.recordLastLevels(p.levels, save = false)) changed = true
            }
            dsInBattle = true
            dsWild = u.wild
            if (u.pid != null && u.species > 0) {
                if (u.pid != dsActive) {
                    dsPids += u.pid
                    marks.trackEncounter(u.species, u.wild, save = false)
                    // Tracker.updateEncounterData: a new wild Pokemon on the area's encounter frame. Here, with the count,
                    // so a battle not fetched (a catching demonstration) is on neither (rc33 audit P1 #80).
                    if (u.wild && save) marks.seeDsEncounter(u.area, u.species, u.level)
                    dsActiveMon?.let { marks.recordLastLevels(listOf(it), save = false) }
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
                    dsPids += f.pid
                    marks.trackEncounter(f.species, u.wild, save = false)
                    if (u.wild && save) marks.seeDsEncounter(u.area, f.species, f.level)
                    dsOthersMon[k]?.let { marks.recordLastLevels(listOf(it), save = false) }
                    dsOthers[k] = f.pid
                    changed = true
                }
                dsOthersMon[k] = f.species to f.level
            }
        } else if (dsInBattle) {
            dsInBattle = false
            dsActiveMon?.let { if (marks.recordLastLevels(listOf(it), save = false)) changed = true }
            if (dsOthersMon.isNotEmpty() && marks.recordLastLevels(dsOthersMon.values.toList(), save = false)) changed = true
            dsActive = null; dsActiveMon = null; dsOthers.clear(); dsOthersMon.clear(); dsPids.clear()
        }
        if (save) {
            val latch = dsPending?.encode() ?: dsLatch()?.encode()
            if (changed || latch != dsCommitted) { marks.commitBattle(DS, latch); dsCommitted = latch }
        }
        return changed
    }

    companion object {
        /** StatMarks.battleLatch's kinds. */
        internal const val GBA = "gba"
        internal const val DS = "ds"

        /** "k=v;k=v" to its fields. */
        private fun fields(text: String): Map<String, String> =
            text.split(';').mapNotNull { f -> f.indexOf('=').takeIf { it > 0 }?.let { f.substring(0, it) to f.substring(it + 1) } }.toMap()

        private fun list(v: String?): List<String> = v.orEmpty().split(',').filter { it.isNotEmpty() }

        private fun pairs(v: String?): List<Pair<Int, Int>> = list(v).map { e -> e.split('/').let { it[0].toInt() to it[1].toInt() } }

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
            val sig = s.enemyParty.joinToString(",") { "${it.slot}.${it.species}.${it.pid}" }
            val e = s.enemy
            // Battle.lua:505: nothing is counted for a Pokemon Tower ghost; its party still
            // gets its levels at the end (the real Pokemon, as the reference records).
            if (e == null || e.isGhost || e.species <= 0) return Gba(true, s.isWildBattle, emptyList(), party, sig)
            if (s.enemyOnField.isEmpty()) {
                // No party slots on this game (Game Boy): the Pokemon on screen, by its PID.
                val key = if (e.pid != 0L) "p${e.pid}:${e.species}" else "sp${e.species}"
                return Gba(true, s.isWildBattle, listOf(Battler(key, e.species, e.level)), party, sig)
            }
            val onField = s.enemyOnField.mapNotNull { slot -> s.enemyParty.firstOrNull { it.slot == slot } }
            // Right after a battle starts the slot can still be the last battle's: count only
            // once it agrees with the Pokemon on screen.
            if (onField.isEmpty() || onField[0].species != e.species) return Gba(true, s.isWildBattle, emptyList(), party, sig)
            return Gba(true, s.isWildBattle, onField.map { Battler("s${it.slot}", it.species, it.level) }, party, sig)
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
