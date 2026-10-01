package com.ironmonone.tracker.nuzlocke

/**
 * Corrections by hand (2026-09-29). Everything the engine decided can be changed here: a gift the player wants
 * counted, an encounter that should not count, a death that was bad luck, a catch the tracker missed while the
 * app was closed. Each change is written in the event log as manual, and the records it makes are marked so the
 * next poll does not undo them.
 */
class NuzlockeEdits(private val ledger: NuzlockeLedger) {

    private fun say(at: Long, text: String, areaKey: String? = null) = ledger.event(at, "edit", text, manual = true, areaKey = areaKey)

    /**
     * The encounter of [area] as the player says it was. A "caught" one that has no Pokemon behind it gets a
     * roster entry, out of the party, so the team list, the dupes clause and the graveyard can see it.
     */
    fun setEncounter(area: AreaKey, species: Int, speciesName: String, level: Int, outcome: Outcome, at: Long) {
        val name = Names.pretty(speciesName).ifBlank { "Unknown" }
        val rec = ledger.area(area.key, area.name)
        val old = rec.encounter
        var monId: Long? = old?.monId?.takeIf { ledger.roster.containsKey(it) }
        if (outcome == Outcome.CAUGHT && monId == null) {
            val mon = RosterMon(nextManualId(), species, name, "", level, null, Origin.CAUGHT, area.key, area.name, at)
            ledger.roster[mon.id] = mon
            monId = mon.id
        }
        rec.encounter = Encounter(species, name, level, old?.pid ?: 0L, "by hand", null, false, at, outcome, monId, manual = true)
        say(at, "${area.name}: encounter set to $name Lv $level, ${outcome.label}.", area.key)
        ledger.touch()
    }

    /** "This should not count": the area is open again. The Pokemon, if one was caught, stays on the roster. */
    fun clearEncounter(areaKey: String, at: Long): Boolean {
        val rec = ledger.areas[areaKey] ?: return false
        val enc = rec.encounter ?: return false
        rec.encounter = null
        say(at, "${rec.name}: the ${enc.speciesName} encounter no longer counts. The area is open.", areaKey)
        ledger.touch()
        return true
    }

    /** A gift, static or other Pokemon the player wants counted as the encounter of [area]. */
    fun countAsEncounter(monId: Long, area: AreaKey, at: Long): Boolean {
        val mon = ledger.roster[monId] ?: return false
        val rec = ledger.area(area.key, area.name)
        rec.encounter = Encounter(mon.species, mon.speciesName, mon.level, mon.id, "by hand", mon.gender, mon.shiny, at, Outcome.CAUGHT, mon.id, manual = true)
        mon.violation = false
        say(at, "${mon.shownName} now counts as the encounter of ${area.name}.", area.key)
        ledger.touch()
        return true
    }

    fun setOrigin(monId: Long, origin: Origin, at: Long): Boolean {
        val mon = ledger.roster[monId] ?: return false
        if (mon.origin == origin) return false
        say(at, "${mon.shownName} is now marked ${origin.label}.")
        mon.origin = origin
        ledger.touch()
        return true
    }

    fun markDead(monId: Long, cause: String, at: Long): Boolean {
        val mon = ledger.roster[monId] ?: return false
        if (!mon.alive) return false
        mon.alive = false
        mon.death = Death(at, mon.level, mon.areaName, cause.ifBlank { "by hand" }, 0, manual = true)
        say(at, "${mon.shownName} is marked dead: ${mon.death!!.cause}.")
        val partner = mon.partner?.let { ledger.roster[it] }
        if (partner != null) { partner.partner = null; mon.partner = null }
        ledger.touch()
        return true
    }

    /** What killed a Pokemon, as the player says it was, when the tracker named the wrong thing or nothing. */
    fun setCause(monId: Long, cause: String, at: Long): Boolean {
        val mon = ledger.roster[monId] ?: return false
        val d = mon.death ?: return false
        val text = oneLine(cause)
        if (text.isEmpty() || text == d.cause) return false
        // Only the words change: whether the death was seen or marked by hand stays what it was.
        mon.death = Death(d.at, d.level, d.areaName, text, d.badges, d.manual)
        say(at, "${mon.shownName}: the death is now put down to $text.")
        ledger.touch()
        return true
    }

    /**
     * One switch of the rules changed in the middle of a run: the table talked it over and dupes are off now.
     * The next poll follows the new rules, and nothing already recorded is redone.
     */
    fun setClause(key: String, on: Boolean, at: Long): Boolean {
        val clause = NuzlockeRules.CLAUSES.firstOrNull { it.key == key } ?: return false
        val now = ledger.meta.rules
        if (clause.get(now) == on) return false
        ledger.meta.rules = clause.set(now, on)
        say(at, "Rule changed by hand: ${clause.title} is now ${if (on) "on" else "off"}.")
        ledger.touch()
        return true
    }

    /** How the Safari Zone is split into areas, changed by hand. */
    fun setSafari(rule: SafariRule, at: Long): Boolean {
        val now = ledger.meta.rules
        if (now.safari == rule) return false
        ledger.meta.rules = now.copy(safari = rule)
        say(at, "Rule changed by hand: ${rule.label}.")
        ledger.touch()
        return true
    }

    /** A death that does not count. HP 0 is ignored for this Pokemon until it has been seen healthy again. */
    fun markAlive(monId: Long, at: Long): Boolean {
        val mon = ledger.roster[monId] ?: return false
        if (mon.alive) return false
        mon.alive = true
        mon.death = null
        mon.forgiven = true
        say(at, "${mon.shownName} is marked alive again.")
        ledger.touch()
        return true
    }

    /** A Pokemon the tracker never saw in the party, added to the roster. */
    fun addMon(speciesName: String, species: Int, level: Int, origin: Origin, area: AreaKey, at: Long): Long {
        val name = Names.pretty(speciesName).ifBlank { "Unknown" }
        val mon = RosterMon(nextManualId(), species, name, "", level, null, origin, area.key, area.name, at)
        ledger.roster[mon.id] = mon
        say(at, "$name Lv $level added to the roster by hand.", area.key)
        ledger.touch()
        return mon.id
    }

    fun dismiss(warningId: String): Boolean {
        val w = ledger.warnings.firstOrNull { it.id == warningId } ?: return false
        if (w.dismissed) return false
        w.dismissed = true
        ledger.touch()
        return true
    }

    /** Carry on after a whiteout or a run ended by mistake. The whiteout stays latched until the party is healthy. */
    fun reopen(at: Long): Boolean {
        val meta = ledger.meta
        if (meta.status == RunStatus.ACTIVE || meta.status == RunStatus.ABANDONED) return false
        meta.status = RunStatus.ACTIVE
        meta.endReason = ""
        meta.endedAt = 0
        meta.whiteoutLatched = true
        say(at, "The run is open again.")
        ledger.touch()
        return true
    }

    fun endRun(reason: String, at: Long): Boolean {
        val meta = ledger.meta
        if (meta.status != RunStatus.ACTIVE) return false
        meta.status = RunStatus.OVER
        meta.endedAt = at
        meta.endReason = reason.ifBlank { "Ended by hand" }
        say(at, "The run is ended: ${meta.endReason}.")
        ledger.touch()
        return true
    }

    fun addNote(text: String, at: Long): Boolean {
        val flat = text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim()
        if (flat.isEmpty()) return false
        ledger.notes += flat
        say(at, "Note: $flat")
        ledger.touch()
        return true
    }

    /** One line of text: every run of spaces, tabs and line breaks is a single space, and the ends are trimmed. */
    private fun oneLine(text: String): String = buildString {
        var gap = false
        for (c in text.trim()) {
            if (c.isWhitespace()) { gap = true; continue }
            if (gap) append(' ')
            gap = false
            append(c)
        }
    }

    private fun nextManualId(): Long = (ledger.roster.keys.minOrNull()?.coerceAtMost(0L) ?: 0L) - 1
}
