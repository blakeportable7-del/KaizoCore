package com.ironmonone.tracker.nuzlocke

/*
 * The ledger of one Nuzlocke run (2026-09-29): every area with its encounter and outcome, the roster with the
 * graveyard, an append-only event log and the warnings. It is plain data with no Android and no tracker in it,
 * so the same ledger serves a Gen 3 tracker today and a Game Boy or DS one later.
 *
 * Everything the engine decides is a record the player can change (NuzlockeEdits), and a change made by hand is
 * marked `manual` so the next poll does not undo it.
 */

enum class Gender(val glyph: String, val label: String) { MALE("M", "male"), FEMALE("F", "female") }

/** How a first encounter ended. */
enum class Outcome(val key: String, val label: String) {
    IN_PROGRESS("progress", "in battle"),
    CAUGHT("caught", "caught"),
    FAINTED("fainted", "fainted"),
    FLED("fled", "fled"),
    RAN("ran", "you ran"),
    LOST("lost", "you lost"),
    UNKNOWN("unknown", "unknown");

    companion object {
        fun byKey(key: String?): Outcome = entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** Where a roster member came from. */
enum class Origin(val key: String, val label: String) {
    STARTER("starter", "starter"),
    CAUGHT("caught", "caught"),
    GIFT("gift", "gift"),
    STATIC("static", "static"),
    /** A free extra: a shiny under the shiny clause. */
    EXTRA("extra", "free extra"),
    /** Already in the party when the ledger first looked (a run started on an old save). */
    EXISTING("existing", "had already");

    companion object {
        fun byKey(key: String?): Origin = entries.firstOrNull { it.key == key } ?: EXISTING
    }
}

/** Why a wild battle in an area did not use it up, or was one more than the area allows. */
enum class ExtraKind(val key: String, val label: String) {
    STATIC("static", "static"),
    SHINY("shiny", "shiny"),
    DUPE("dupe", "dupe"),
    TYPE("type", "wrong type"),
    GENDER("gender", "wrong gender"),
    SECOND("second", "second encounter");

    companion object {
        fun byKey(key: String?): ExtraKind = entries.firstOrNull { it.key == key } ?: SECOND
    }
}

enum class RunStatus(val key: String, val label: String) {
    ACTIVE("active", "In progress"),
    OVER("over", "Over"),
    COMPLETE("complete", "Champion beaten"),
    /** Replaced by a newer run on the same game. Kept as history. */
    ABANDONED("abandoned", "Replaced");

    companion object {
        fun byKey(key: String?): RunStatus = entries.firstOrNull { it.key == key } ?: ACTIVE
    }
}

enum class WarnKind(val key: String, val label: String) {
    CAP("cap", "Level cap"),
    ITEM("item", "Item in battle"),
    REVIVED("revived", "Revived"),
    SECOND_CATCH("second", "Second catch"),
    DUPE_CATCH("dupe", "Dupe caught"),
    TYPE("type", "Wrong type"),
    GENDER("gender", "Wrong gender"),
    NICKNAME("nickname", "No nickname"),
    STYLE("style", "Battle style"),
    OTHER("other", "Note");

    companion object {
        fun byKey(key: String?): WarnKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** The first encounter of an area, or the free ones beside it. */
class Encounter(
    var species: Int,
    var speciesName: String,
    var level: Int,
    var pid: Long,
    /** "Walking", "Surfing", "Old Rod" and so on, as the tracker names the area of a wild battle. */
    var method: String,
    var gender: Gender?,
    var shiny: Boolean,
    var at: Long,
    var outcome: Outcome,
    /** The roster member it became, when it was caught. */
    var monId: Long?,
    var manual: Boolean,
)

class Extra(
    val kind: ExtraKind,
    val species: Int,
    val speciesName: String,
    val level: Int,
    val pid: Long,
    val at: Long,
    var outcome: Outcome,
    var monId: Long?,
)

class AreaRecord(val key: String, var name: String) {
    /** The encounter that uses the area up; null while the area is open. */
    var encounter: Encounter? = null
    val extras = ArrayList<Extra>()
}

class Death(
    val at: Long,
    val level: Int,
    val areaName: String,
    /** What was on the field: "wild Pidgey Lv 3", "LEADER Roxanne (Geodude Lv 12)", or "outside a battle". */
    val cause: String,
    val badges: Int,
    val manual: Boolean,
)

class RosterMon(
    /** The personality value, which never changes for one Pokemon; a mon added by hand gets a negative id. */
    val id: Long,
    var species: Int,
    var speciesName: String,
    var nickname: String,
    var level: Int,
    var gender: Gender?,
    /** Changeable by hand: a gift the player counts as a catch, a static that is really a gift. */
    var origin: Origin,
    var areaKey: String?,
    var areaName: String,
    val at: Long,
) {
    var inParty: Boolean = false
    var alive: Boolean = true
    var death: Death? = null
    var shiny: Boolean = false
    /** Something the rules did not allow when it was caught; the reason is in the warnings. */
    var violation: Boolean = false
    /** Wedlocke: the id of its partner. */
    var partner: Long? = null
    /** Gen 3 type ids, kept for the Monotype check and the ledger. */
    var types: List<Int> = emptyList()
    var highestLevel: Int = level
    /** The player said this faint does not count: ignore HP 0 until it has been seen healthy again. */
    var forgiven: Boolean = false

    /** It has a nickname of its own: not blank, and not the species name the game gives it by default ("ZIGZAGOON"). */
    val hasNickname: Boolean get() = nickname.isNotBlank() && !nickname.equals(speciesName, ignoreCase = true)

    /** The name to show: its nickname when it has a real one. */
    val shownName: String get() = if (hasNickname) nickname else speciesName

    /** "Razor (Scyther Lv 25)", or "Scyther Lv 25" when it has no nickname of its own. */
    fun label(level: Int): String = if (hasNickname) "$nickname ($speciesName Lv $level)" else "$speciesName Lv $level"
}

class Event(
    val at: Long,
    val kind: String,
    val text: String,
    val manual: Boolean,
    val areaKey: String?,
)

class Warning(
    val id: String,
    val at: Long,
    val kind: WarnKind,
    val text: String,
    var dismissed: Boolean,
)

/** A survivor at the Champion, kept so the next game of a Genlocke can carry it on. */
class Heir(
    val species: Int,
    val speciesName: String,
    val nickname: String,
    val level: Int,
    val gender: Gender?,
    val shiny: Boolean,
)

class RunMeta(
    val id: String,
    /** What the run is tied to: a library game's id, or a randomized run's "game/seed" identity. */
    val bind: String,
    var game: String,
    /** The switches of this run. The player can change one by hand (NuzlockeEdits.setClause), and the engine reads it each poll. */
    var rules: NuzlockeRules,
    val startedAt: Long,
) {
    /** The family of games the run is on. It picks the evolution lines, level caps and area rules; Gen 3 for a ledger made before there was any other. */
    var system: NuzlockeSystem = NuzlockeSystem.GEN3
    /** The game inside that family ("red", "crystal", "platinum"), for the rules text. Empty when the run did not say. */
    var gameKey: String = ""
    /** Bosses of the level cap table the engine saw beaten: a trainer battle of theirs that ended in a win. The League's cap depends on it. */
    val beatenBosses = LinkedHashSet<String>()
    var status: RunStatus = RunStatus.ACTIVE
    var endedAt: Long = 0
    var endReason: String = ""
    /** The rules have begun (slow start passed). Latched: it never goes back. */
    var started: Boolean = false
    /** The first party the ledger saw was taken in. */
    var partySeen: Boolean = false
    /** The whole party is down right now; latched so one whiteout is logged once. */
    var whiteoutLatched: Boolean = false
    /** The game's battle style is on Shift right now (the Set reminder). */
    var styleShift: Boolean = false
    /** Genlocke: which chain, which game of it, and the run it came from. */
    var genlockeId: String = ""
    var leg: Int = 0
    var carriedFrom: String = ""
    /** Survivors carried in from the last game, and the ones saved at this game's Champion. */
    val heirsIn = ArrayList<Heir>()
    val heirsOut = ArrayList<Heir>()
}

/**
 * One run's whole record. Mutable on purpose: the engine and the edits change it in place, and
 * [revision] moves on every change so a caller can tell whether anything needs saving.
 */
class NuzlockeLedger(val meta: RunMeta) {
    val areas = LinkedHashMap<String, AreaRecord>()
    val roster = LinkedHashMap<Long, RosterMon>()
    val events = ArrayList<Event>()
    val warnings = ArrayList<Warning>()
    val notes = ArrayList<String>()

    var revision: Int = 0
        private set

    fun touch() { revision++ }

    fun area(key: String, name: String): AreaRecord {
        val have = areas[key]
        if (have != null) return have
        touch()
        return AreaRecord(key, name).also { areas[key] = it }
    }

    fun event(at: Long, kind: String, text: String, manual: Boolean = false, areaKey: String? = null) {
        events += Event(at, kind, text, manual, areaKey)
        // A long run cannot grow the file without end: the oldest lines go first.
        if (events.size > MAX_EVENTS) events.subList(0, events.size - MAX_EVENTS).clear()
        touch()
    }

    /** Adds a warning once: false when one with this id is already there (dismissed or not). */
    fun warn(id: String, at: Long, kind: WarnKind, text: String): Boolean {
        if (warnings.any { it.id == id }) return false
        warnings += Warning(id, at, kind, text, dismissed = false)
        if (warnings.size > MAX_WARNINGS) warnings.removeAt(0)
        touch()
        return true
    }

    val alive: List<RosterMon> get() = roster.values.filter { it.alive }
    val party: List<RosterMon> get() = roster.values.filter { it.alive && it.inParty }
    val boxed: List<RosterMon> get() = roster.values.filter { it.alive && !it.inParty }
    val graveyard: List<RosterMon> get() = roster.values.filter { !it.alive }.sortedBy { it.death?.at ?: 0L }
    val openWarnings: List<Warning> get() = warnings.filter { !it.dismissed }

    /** Every evolution line the run has caught, alive or dead, as its species ids. */
    fun species(includeDead: Boolean): List<Int> =
        roster.values.filter { includeDead || it.alive }.map { it.species }

    companion object {
        const val MAX_EVENTS = 4000
        const val MAX_WARNINGS = 400

        /** A fresh id for a run: sortable by start time, safe as a file name. */
        fun newId(at: Long, salt: Int = (Math.random() * 46656).toInt()): String =
            "nz-" + java.lang.Long.toString(at, 36) + "-" + java.lang.Integer.toString(salt, 36).padStart(3, '0')
    }
}
