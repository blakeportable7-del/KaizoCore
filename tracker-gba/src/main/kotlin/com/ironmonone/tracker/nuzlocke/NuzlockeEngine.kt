package com.ironmonone.tracker.nuzlocke

/** Species names as the ledger writes them: the ROM's capitals ("MR. MIME") become "Mr. Mime". */
object Names {
    private val WORD = Regex("[A-Za-z][A-Za-z']*")

    fun pretty(name: String): String =
        WORD.replace(name.trim()) { m -> m.value.lowercase().replaceFirstChar { it.uppercase() } }
}

/**
 * The rules engine (2026-09-29): one [Snapshot] in, the [NuzlockeLedger] updated, nothing in the game touched.
 *
 * It is passive. It records what happened and warns about what the rules say should not have; it never blocks.
 * Everything is level-triggered where it can be: a Pokemon at 0 HP is dead on the poll that sees it, whether or
 * not a transition was watched, and a whiteout that heals the party a moment later cannot un-kill anything.
 *
 * The engine keeps almost no memory of its own. What survives a restart is in the ledger: the areas, the
 * roster, the flags in [RunMeta]. Only the battle in progress is transient, and a ledger reloaded in the middle
 * of a battle picks it up from the area record that battle already made.
 */
class NuzlockeEngine(val ledger: NuzlockeLedger) {

    private val meta: RunMeta get() = ledger.meta
    private val rules: NuzlockeRules get() = ledger.meta.rules
    private val system: NuzlockeSystem get() = ledger.meta.system

    private enum class PlanKind { NONE, BEFORE, COUNTS, FREE, SKIP, USED }

    /** What the wild battle in progress means for its area, decided once when its enemy is first seen. */
    private class Plan(val kind: PlanKind, val extra: ExtraKind? = null)

    private class Battle(
        val startedAt: Long,
        val wild: Boolean,
        val ghost: Boolean,
        val area: AreaKey,
        val bag: Map<Int, NzItem>?,
    ) {
        var registered = false
        var plan = Plan(PlanKind.NONE)
        var enemy: NzEnemy? = null
        var enemyHp = -1
        var end = BattleEnd.UNKNOWN
        var trainerHandled = false
        var bossKey: String? = null
        var opponent: NzOpponent? = null
        var caught: RosterMon? = null
        var cause = "outside a battle"
    }

    private var battle: Battle? = null

    /** Whether this engine has settled what an engine before it left "in battle" (settleStale): once, at its first look. */
    private var staleChecked = false

    /** Feeds one poll. True when the ledger changed and wants saving. */
    fun update(s: Snapshot, at: Long): Boolean {
        if (!s.readable) return false
        val before = ledger.revision
        if (meta.status != RunStatus.ACTIVE) { battle = null; return false }
        // A party the game lends for a facility battle: its faints are no deaths, losing is no whiteout, and its rentals
        // are nobody's catch or gift. When the real party comes back nothing has changed (rc32 audit P2 #141).
        if (s.facility) { battle = null; return false }

        val real = distinctIds(s.party.filter { it.real })
        learnSection(s)
        beginRules(real, s, at)
        if (s.inBattle && battle == null) startBattle(s, at)
        battle?.takeIf { s.inBattle }?.let { trackBattle(it, s, at) }
        noteEggs(s, at, firstSight = !meta.partySeen)
        takeParty(real, s, at)
        recordDeaths(real, s, at)
        if (!s.inBattle && battle != null) endBattle(s, at)
        if (!staleChecked && !s.inBattle && battle == null && real.isNotEmpty()) settleStale(at)
        checkWhiteout(real, s, at)
        checkStyle(s, at)
        return ledger.revision != before
    }

    // ------------------------------------------------------------------ where a gift was received

    /** A Gen 3 map section seen outdoors, with the map's name, for the gifts in its buildings ([giftArea]). The first name stays. */
    private fun learnSection(s: Snapshot) {
        val sec = s.area.section ?: return
        val name = s.area.name?.takeIf { it.isNotBlank() } ?: return
        if (s.area.indoor || sec in meta.sections) return
        meta.sections[sec] = name
        ledger.touch()
    }

    /**
     * Where a gift was received. A building takes the town or route it stands in, by its map section (the game's own
     * "met at" place) as it was seen outdoors: every Pokemon Center shares one layout and so one name, so FireRed's
     * Route 4 Magikarp used up one "Pokemon Center" area for the whole game while Route 4 stayed open (rc32 audit P2
     * #140). A section not seen outdoors yet, and the games that give no section, keep the map's own name.
     */
    private fun giftArea(s: Snapshot): AreaKey {
        val a = s.area
        val outdoors = if (a.indoor) a.section?.let { meta.sections[it] } else null
        return NuzlockeAreas.of(if (outdoors != null) NzArea(outdoors, null) else a, null, rules, system)
    }

    /**
     * Each egg in the party, with where it was first seen: an egg is not a party member the rules count until it
     * hatches, and the Pokemon that hatches is a gift of the place the egg was received, not of the route it hatched on
     * (rc32 audit P2 #140: Emerald's Lavaridge egg used up Route 117). An egg the ledger found in the party at its first
     * look, or before the rules began, hatches free.
     */
    private fun noteEggs(s: Snapshot, at: Long, firstSight: Boolean) {
        for (m in s.party) {
            if (!m.isEgg || m.id == 0L || m.id in meta.eggs || ledger.roster.containsKey(m.id)) continue
            val area = giftArea(s)
            meta.eggs[m.id] = EggSeen(area.key, area.name, meta.started && !firstSight)
            if (!firstSight) ledger.event(at, "egg", "An egg joined the party at ${area.name}.", areaKey = area.key)
            ledger.touch()
        }
    }

    /**
     * An encounter an engine before this one left "in battle": the app closed during a wild battle and the game went on
     * from before it, so no poll will ever see how that battle ended, and the area said "in battle" for the rest of the
     * run (rc32 audit P3 #116). At this engine's first look out of battle with a party read, each one is settled as it
     * stands: caught when its Pokemon is on the roster, otherwise unknown. A battle that does come back is the same
     * Pokemon, which planFor takes up again before this runs, and it ends as any other.
     */
    private fun settleStale(at: Long) {
        staleChecked = true
        for (a in ledger.areas.values) {
            a.encounter?.takeIf { it.outcome == Outcome.IN_PROGRESS && !it.manual }?.let { enc ->
                val mon = ledger.roster[enc.pid]
                enc.outcome = if (mon != null) Outcome.CAUGHT else Outcome.UNKNOWN
                enc.monId = mon?.id
                ledger.event(at, "outcome",
                    if (mon != null) "${a.name}: ${enc.speciesName} Lv ${enc.level} was caught before the app closed."
                    else "${a.name}: the app closed during the battle with ${enc.speciesName} Lv ${enc.level}, so how it ended is not known.",
                    areaKey = a.key)
                ledger.touch()
            }
            for (x in a.extras) if (x.outcome == Outcome.IN_PROGRESS) {
                val mon = ledger.roster[x.pid]
                x.outcome = if (mon != null) Outcome.CAUGHT else Outcome.UNKNOWN
                x.monId = mon?.id
                ledger.touch()
            }
        }
    }

    // ------------------------------------------------------------------ the rules begin

    private fun beginRules(real: List<NzMon>, s: Snapshot, at: Long) {
        if (meta.started || real.isEmpty()) return
        val balls = s.ballCount
        if (rules.slowStart && balls != null && balls <= 0) return
        meta.started = true
        ledger.event(at, "start", if (rules.slowStart && balls != null) "You hold Poke Balls. The rules begin." else "The rules begin.")
        ledger.touch()
    }

    // ------------------------------------------------------------------ the party

    /**
     * Two party members never share a record. Shedinja is a copy of the Nincada that made it (pokeemerald CreateShedinja:
     * CopyMon, then its own species, nickname and item), so it carries Ninjask's personality value, the id the Gen 3 and
     * DS adapters give a Pokemon: one record stood for both, a death was missed, a living Ninjask was called revived, and
     * the ledger was rewritten on every poll as the two took turns at it (rc33 audit P1 #78). The member that keeps the
     * id is the one whose species the record holds, else the first; any other takes [twinId], the same on every poll
     * whatever the party's order, and is adopted as a Pokemon of its own.
     */
    private fun distinctIds(party: List<NzMon>): List<NzMon> {
        val byId = party.groupBy { it.id }
        if (byId.values.all { it.size == 1 }) return party
        return party.map { m ->
            val same = byId.getValue(m.id)
            if (same.size == 1) return@map m
            val keeper = same.firstOrNull { it.species == ledger.roster[m.id]?.species } ?: same.first()
            if (m === keeper) m else m.copy(id = twinId(m.id, m.species))
        }
    }

    private fun takeParty(real: List<NzMon>, s: Snapshot, at: Long) {
        val firstSight = !meta.partySeen
        val seen = HashSet<Long>()
        for (m in real) {
            seen += m.id
            val mon = ledger.roster[m.id] ?: adopt(m, s, at, firstSight, real.size)
            refresh(mon, m)
        }
        if (firstSight && real.isNotEmpty()) { meta.partySeen = true; ledger.touch() }
        // Only a whole read of the party may say a Pokemon left it: a slot that failed to decode is not a box.
        if (s.party.size >= s.partyCount) {
            for (mon in ledger.roster.values) if (mon.id !in seen && mon.inParty) { mon.inParty = false; ledger.touch() }
        }
    }

    private fun refresh(mon: RosterMon, m: NzMon) {
        var changed = !mon.inParty
        mon.inParty = true
        val name = Names.pretty(m.speciesName)
        if (mon.species != m.species || mon.speciesName != name) { mon.species = m.species; mon.speciesName = name; changed = true }
        if (mon.nickname != m.nickname.trim()) { mon.nickname = m.nickname.trim(); changed = true }
        if (mon.level != m.level) { mon.level = m.level; changed = true }
        if (m.level > mon.highestLevel) { mon.highestLevel = m.level; changed = true }
        if (mon.gender != m.gender && m.gender != null) { mon.gender = m.gender; changed = true }
        if (mon.shiny != m.shiny) { mon.shiny = m.shiny; changed = true }
        if (m.types.isNotEmpty() && mon.types != m.types) { mon.types = m.types; changed = true }
        if (changed) ledger.touch()
    }

    /** A Pokemon the ledger has not seen: the starter, a catch, a gift, or one the run began with. */
    private fun adopt(m: NzMon, s: Snapshot, at: Long, firstSight: Boolean, partySize: Int): RosterMon {
        val ctx = battle
        val inWild = ctx != null && ctx.wild && !ctx.ghost
        // A Pokemon out of an egg is a gift of the place the egg was received (noteEggs).
        val egg = if (firstSight || inWild) null else meta.eggs.remove(m.id)
        // A catch belongs to the area its battle was counted in, which can be the water half of a place.
        val area = when {
            inWild -> ctx!!.area
            egg != null -> AreaKey(egg.areaKey, egg.areaName)
            else -> giftArea(s)
        }
        val twin = isTwin(m.id)
        val origin = when {
            firstSight -> if (partySize == 1) Origin.STARTER else Origin.EXISTING
            // Shedinja is no encounter, no catch and no gift: it came out of an evolution, so it is a free extra.
            twin -> Origin.EXTRA
            inWild -> originFor(ctx!!.plan)
            else -> Origin.GIFT
        }
        val mon = RosterMon(m.id, m.species, Names.pretty(m.speciesName), m.nickname.trim(), m.level, m.gender, origin, area.key, area.name, at)
        mon.shiny = m.shiny
        mon.types = m.types
        ledger.roster[m.id] = mon
        val label = mon.label(m.level)
        when (origin) {
            Origin.STARTER -> ledger.event(at, "starter", "Starter: $label.", areaKey = area.key)
            Origin.EXISTING -> ledger.event(at, "existing", "In the party already: $label.", areaKey = area.key)
            Origin.EXTRA.takeIf { twin } -> ledger.event(at, "extra",
                "$label shares its game id with another Pokemon in the party, as Shedinja does with Ninjask: kept as a Pokemon of its own, and free.",
                areaKey = area.key)
            Origin.GIFT -> {
                if (egg != null) ledger.event(at, "gift", "Hatched from the egg from ${area.name}: $label.", areaKey = area.key)
                else ledger.event(at, "gift", "Gift or trade: $label, at ${area.name}.", areaKey = area.key)
                if (egg == null || egg.counts) countGift(mon, area, at)
            }
            else -> {
                ctx!!.caught = mon
                ledger.event(at, "catch", "Caught $label at ${area.name}.", areaKey = area.key)
                flagCatch(ctx, mon, area, at)
            }
        }
        if (rules.wedlocke && origin != Origin.EXISTING) pair(mon, at)
        ledger.touch()
        return mon
    }

    internal companion object {
        /** A party member's id when another holds its game id: that id with its species above it, past any real id's 32 bits. */
        fun twinId(id: Long, species: Int): Long = (species.toLong() shl 32) or (id and 0xFFFFFFFFL)
        /** Above every game's 32-bit ids; a hand-added Pokemon's are negative. */
        fun isTwin(id: Long): Boolean = id > 0xFFFFFFFFL
    }

    private fun originFor(plan: Plan): Origin = when {
        plan.kind == PlanKind.FREE && plan.extra == ExtraKind.STATIC -> Origin.STATIC
        plan.kind == PlanKind.FREE && plan.extra == ExtraKind.SHINY -> Origin.EXTRA
        else -> Origin.CAUGHT
    }

    /** With gifts counting, a gift uses up the area where it was received, if that area is still open. */
    private fun countGift(mon: RosterMon, area: AreaKey, at: Long) {
        if (!rules.giftsCount || !rules.firstEncounter || !meta.started) return
        val rec = ledger.area(area.key, area.name)
        if (rec.encounter != null) return
        rec.encounter = Encounter(mon.species, mon.speciesName, mon.level, mon.id, "gift", mon.gender, mon.shiny, at, Outcome.CAUGHT, mon.id, false)
        ledger.event(at, "area", "${area.name}: gift ${mon.speciesName} uses the area.", areaKey = area.key)
    }

    /** A catch the rules did not allow is kept and flagged, never refused. */
    private fun flagCatch(ctx: Battle, mon: RosterMon, area: AreaKey, at: Long) {
        val name = mon.shownName
        when (ctx.plan.kind) {
            PlanKind.USED -> {
                val first = ledger.areas[area.key]?.encounter
                mon.violation = true
                ledger.warn("second:${ctx.startedAt}", at, WarnKind.SECOND_CATCH,
                    "Caught $name at ${area.name}, but ${first?.speciesName ?: "another Pokemon"} was the first encounter there.")
            }
            PlanKind.SKIP -> {
                mon.violation = true
                when (ctx.plan.extra) {
                    ExtraKind.DUPE -> ledger.warn("dupe:${ctx.startedAt}", at, WarnKind.DUPE_CATCH,
                        "Caught $name, but its evolution line was already yours, so it should not have counted.")
                    ExtraKind.TYPE -> ledger.warn("type:${ctx.startedAt}", at, WarnKind.TYPE,
                        "Caught $name, which is not ${rules.monotypeLabel ?: "the chosen type"}.")
                    ExtraKind.GENDER -> ledger.warn("gender:${ctx.startedAt}", at, WarnKind.GENDER,
                        "Caught $name, but the party has more of that gender already.")
                    else -> {}
                }
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ deaths

    private fun recordDeaths(real: List<NzMon>, s: Snapshot, at: Long) {
        if (!meta.started) return
        for (m in real) {
            val mon = ledger.roster[m.id] ?: continue
            if (m.hp > 0) {
                if (mon.forgiven) { mon.forgiven = false; ledger.touch() }
                val death = mon.death
                if (!mon.alive && death != null && !death.manual && rules.faintIsDeath) {
                    ledger.warn("revived:${mon.id}", at, WarnKind.REVIVED,
                        "${mon.shownName} died at ${death.areaName} and has HP again. Revives are not allowed; box it.")
                }
                continue
            }
            if (!rules.faintIsDeath || !mon.alive || mon.forgiven) continue
            kill(mon, m, s, at)
        }
    }

    private fun kill(mon: RosterMon, m: NzMon, s: Snapshot, at: Long) {
        val area = NuzlockeAreas.of(s.area, null, rules, system)
        val cause = battle?.cause ?: "outside a battle"
        mon.alive = false
        mon.level = m.level
        mon.death = Death(at, m.level, area.name, cause, s.badges, manual = false)
        ledger.event(at, "death", "${mon.label(m.level)} died at ${area.name}: $cause.", areaKey = area.key)
        widow(mon, at)
        ledger.touch()
    }

    // ------------------------------------------------------------------ battles

    private fun startBattle(s: Snapshot, at: Long) {
        val wild = s.wild && !s.ghost
        val area = NuzlockeAreas.of(s.area, if (wild) s.method else null, rules, system)
        battle = Battle(at, wild, s.ghost, area, s.bag)
    }

    private fun trackBattle(ctx: Battle, s: Snapshot, at: Long) {
        if (s.end != BattleEnd.UNKNOWN) ctx.end = s.end
        if (ctx.ghost) return
        val e = s.enemy
        if (ctx.wild) {
            if (e != null) ctx.cause = "wild ${Names.pretty(e.speciesName)} Lv ${e.level}"
            if (!ctx.registered && e != null) registerWild(ctx, s, e, at)
            if (e != null && e.id == ctx.enemy?.id) ctx.enemyHp = e.hp
        } else {
            val opp = s.opponent
            if (opp != null) {
                ctx.opponent = opp
                ctx.cause = if (e != null) "${opp.label} (${Names.pretty(e.speciesName)} Lv ${e.level})" else opp.label
                if (!ctx.trainerHandled) { ctx.trainerHandled = true; ctx.bossKey = opp.bossKey; trainerStart(ctx, s, opp, at) }
            } else if (e != null) ctx.cause = "a trainer's ${Names.pretty(e.speciesName)} Lv ${e.level}"
        }
    }

    /** The first sight of a wild enemy: decide what this battle means for its area, and record it. */
    private fun registerWild(ctx: Battle, s: Snapshot, e: NzEnemy, at: Long) {
        ctx.registered = true
        ctx.enemy = e
        ctx.enemyHp = e.hp
        val plan = planFor(ctx, s, e)
        ctx.plan = plan
        val area = ctx.area
        val name = Names.pretty(e.speciesName)
        when (plan.kind) {
            PlanKind.COUNTS -> {
                val rec = ledger.area(area.key, area.name)
                if (rec.encounter == null) {
                    rec.encounter = Encounter(e.species, name, e.level, e.id, s.method.label, e.gender, e.shiny, at, Outcome.IN_PROGRESS, null, false)
                    ledger.event(at, "area", "${area.name}: first encounter is $name Lv ${e.level}.", areaKey = area.key)
                    ledger.touch()
                }
            }
            PlanKind.FREE, PlanKind.SKIP -> {
                val rec = ledger.area(area.key, area.name)
                if (rec.extras.none { it.pid == e.id }) {
                    val kind = plan.extra!!
                    rec.extras += Extra(kind, e.species, name, e.level, e.id, at, Outcome.IN_PROGRESS, null)
                    ledger.event(at, "extra", if (plan.kind == PlanKind.SKIP)
                        "${area.name}: $name Lv ${e.level} does not count (${kind.label}); the area stays open."
                    else "${area.name}: $name Lv ${e.level} is a free ${kind.label} encounter.", areaKey = area.key)
                    ledger.touch()
                }
            }
            PlanKind.USED, PlanKind.BEFORE, PlanKind.NONE -> {}
        }
    }

    private fun planFor(ctx: Battle, s: Snapshot, e: NzEnemy): Plan {
        if (!meta.started) return Plan(PlanKind.BEFORE)
        if (!rules.firstEncounter) return Plan(PlanKind.NONE)
        val rec = ledger.areas[ctx.area.key]
        // The same Pokemon again: the app was closed mid-battle, or a state was loaded. Take the battle back up.
        rec?.encounter?.let { if (it.pid == e.id) return Plan(PlanKind.COUNTS) }
        rec?.extras?.firstOrNull { it.pid == e.id }?.let { x ->
            return when (x.kind) {
                ExtraKind.STATIC, ExtraKind.SHINY -> Plan(PlanKind.FREE, x.kind)
                ExtraKind.SECOND -> Plan(PlanKind.USED)
                else -> Plan(PlanKind.SKIP, x.kind)
            }
        }
        if (s.method == Method.STATIC && !rules.staticsCount) return Plan(PlanKind.FREE, ExtraKind.STATIC)
        // A shiny "does not use up the area" (NuzlockeRules), so it is free in a used area too; it was checked after the
        // used area's return and so never applied there (rc33 audit P1 #79).
        if (e.shiny && rules.shinyClause) return Plan(PlanKind.FREE, ExtraKind.SHINY)
        if (rec?.encounter != null) return Plan(PlanKind.USED)
        val type = rules.monotypeType
        if (type != null && type !in e.types) return Plan(PlanKind.SKIP, ExtraKind.TYPE)
        // Generation 1 has no genders, so no encounter can be judged by one: Wedlocke pairs go by the order of the catches there.
        if (rules.wedlocke && system.hasGender && !genderAllowed(e.gender)) return Plan(PlanKind.SKIP, ExtraKind.GENDER)
        if (rules.dupes && isDupe(e.species)) return Plan(PlanKind.SKIP, ExtraKind.DUPE)
        return Plan(PlanKind.COUNTS)
    }

    private fun isDupe(species: Int): Boolean {
        val line = NuzlockeFamilies.lineOf(species, system)
        val owned = ledger.roster.values.any { (it.alive || !rules.dupesOwnedOnly) && NuzlockeFamilies.lineOf(it.species, system) == line }
        if (owned) return true
        // An encounter set by hand as caught counts too, though no Pokemon is on the roster for it.
        return ledger.areas.values.any { a ->
            val enc = a.encounter
            enc != null && enc.outcome == Outcome.CAUGHT && enc.monId == null && NuzlockeFamilies.lineOf(enc.species, system) == line
        }
    }

    /** Wedlocke: a gender is skipped while the party already holds more of it; genderless cannot be caught. */
    private fun genderAllowed(g: Gender?): Boolean {
        if (g == null) return false
        val alive = ledger.alive
        val males = alive.count { it.gender == Gender.MALE }
        val females = alive.count { it.gender == Gender.FEMALE }
        return if (g == Gender.MALE) males <= females else females <= males
    }

    private fun trainerStart(ctx: Battle, s: Snapshot, opp: NzOpponent, at: Long) {
        if (!rules.levelCaps || !meta.started) return
        val boss = s.caps?.byKey(opp.bossKey)
        val cap: Int? = when {
            boss != null -> when (boss.kind) {
                "gym", "post" -> opp.maxLevel ?: boss.cap
                "e4" -> if (leagueCapApplies(boss, s)) s.caps?.leagueCap else null
                // The DS tables carry the extra cap points as rows; the Game Boy and Gen 3 read them from the game (below).
                "rival", "boss" -> if (rules.capExtraBosses) opp.maxLevel ?: boss.cap else null
                else -> null
            }
            rules.capExtraBosses && (opp.group == "Rival" || opp.group == "Boss") -> opp.maxLevel
            else -> null
        }
        if (cap == null) {
            if (boss != null && boss.onLadder) ledger.event(at, "boss", "${boss.place}, ${boss.label}: no level cap inside the League.")
            return
        }
        // Joined a battle that was already going: levels may have moved since it began, so nothing to check.
        if ((s.turn ?: 0) > 0) {
            ledger.event(at, "boss", "${opp.label}: seen after the battle began, so the level cap was not checked.")
            return
        }
        val over = s.party.filter { it.real && it.hp > 0 && it.level > cap }
        val where = boss?.let { "${it.place}, ${it.label}" } ?: opp.label
        ledger.event(at, "boss", if (over.isEmpty()) "$where: level cap $cap, the party is under it." else "$where: level cap $cap, ${over.size} over it.")
        for (m in over) {
            ledger.warn("cap:${ctx.startedAt}:${m.id}", at, WarnKind.CAP,
                "${m.nickname.ifBlank { Names.pretty(m.speciesName) }} is level ${m.level}, over the cap of $cap for $where.")
        }
    }

    /**
     * The League's cap applies to its first fight. In a fixed order that is the first member (as before); where the Elite
     * Four can be fought in any order it is whichever comes first, known by the game's own flags or by the wins this
     * ledger saw.
     */
    private fun leagueCapApplies(boss: BossCap, s: Snapshot): Boolean {
        if (boss.group.isBlank()) return boss.key == "e4-1"
        val e4 = s.caps?.bosses?.filter { it.kind == "e4" }?.map { it.key }?.toSet() ?: return false
        return (s.beaten + meta.beatenBosses).none { it in e4 }
    }

    private fun endBattle(s: Snapshot, at: Long) {
        val ctx = battle ?: return
        battle = null
        if (ctx.ghost) return
        if (rules.noItemsInBattle && meta.started) itemUse(ctx, s, at)
        if (ctx.wild) resolveWild(ctx, s, at) else finishTrainer(ctx, s, at)
    }

    /**
     * How a trainer battle ended. Where the game says (Generations 1 to 3) that is the answer. A DS game does not, and a
     * trainer cannot be run from, so a battle that ended with a Pokemon of the player's still standing was won. The
     * Champion is never worked out this way: the run is only complete when the tracker itself says the last fight was won.
     */
    private fun trainerEnd(ctx: Battle, s: Snapshot): BattleEnd {
        val given = if (s.end != BattleEnd.UNKNOWN) s.end else ctx.end
        if (given != BattleEnd.UNKNOWN || system.readsOutcome || ctx.bossKey == "champion") return given
        return if (s.party.any { it.real && it.hp > 0 }) BattleEnd.WON else BattleEnd.LOST
    }

    private fun finishTrainer(ctx: Battle, s: Snapshot, at: Long) {
        val end = trainerEnd(ctx, s)
        val key = ctx.bossKey
        if (key != null && end == BattleEnd.WON && meta.started && meta.beatenBosses.add(key)) ledger.touch()
        // The Champion is beaten when that fight ends with a win; a ledger begun after it does not complete.
        if (key == "champion" && end == BattleEnd.WON && meta.started) complete(at)
    }

    private fun outcomeOf(ctx: Battle, s: Snapshot): Outcome {
        val end = if (s.end != BattleEnd.UNKNOWN) s.end else ctx.end
        return when {
            ctx.caught != null || end == BattleEnd.CAUGHT -> Outcome.CAUGHT
            end == BattleEnd.WON -> Outcome.FAINTED
            end == BattleEnd.RAN -> Outcome.RAN
            end == BattleEnd.MON_FLED -> Outcome.FLED
            end == BattleEnd.LOST || end == BattleEnd.DREW -> Outcome.LOST
            ctx.enemyHp == 0 -> Outcome.FAINTED
            else -> Outcome.UNKNOWN
        }
    }

    private fun resolveWild(ctx: Battle, s: Snapshot, at: Long) {
        val e = ctx.enemy ?: return
        val outcome = outcomeOf(ctx, s)
        val name = Names.pretty(e.speciesName)
        // Caught, and the party was full: it went to a box, where the tracker cannot see it.
        if (outcome == Outcome.CAUGHT && ctx.caught == null && ledger.roster[e.id] == null) {
            boxedCatch(ctx, e, at)
        }
        val mon = ctx.caught ?: ledger.roster[e.id]?.takeIf { outcome == Outcome.CAUGHT }
        val area = ctx.area
        when (ctx.plan.kind) {
            PlanKind.COUNTS -> {
                val enc = ledger.areas[area.key]?.encounter
                if (enc != null && enc.pid == e.id && !enc.manual) {
                    enc.outcome = outcome
                    enc.monId = mon?.id
                    if (outcome == Outcome.FLED && rules.escapeClause) {
                        ledger.areas[area.key]?.encounter = null
                        ledger.event(at, "area", "${area.name}: $name fled. The escape clause opens the area again.", areaKey = area.key)
                    }
                    ledger.touch()
                }
            }
            PlanKind.FREE, PlanKind.SKIP -> {
                ledger.areas[area.key]?.extras?.firstOrNull { it.pid == e.id }?.let { it.outcome = outcome; it.monId = mon?.id; ledger.touch() }
            }
            PlanKind.USED -> if (outcome == Outcome.CAUGHT) {
                // A wild battle in a used area is ordinary and not written down; a catch there is.
                val rec = ledger.area(area.key, area.name)
                if (rec.extras.none { it.pid == e.id }) {
                    rec.extras += Extra(ExtraKind.SECOND, e.species, name, e.level, e.id, at, outcome, mon?.id)
                    ledger.touch()
                }
            }
            PlanKind.BEFORE, PlanKind.NONE -> {}
        }
        if (outcome == Outcome.CAUGHT || ctx.plan.kind == PlanKind.COUNTS || ctx.plan.kind == PlanKind.FREE || ctx.plan.kind == PlanKind.SKIP)
            ledger.event(at, "outcome", "${area.name}: $name Lv ${e.level} ${outcome.label}.", areaKey = area.key)
        if (mon != null && outcome == Outcome.CAUGHT) checkNickname(mon, at)
    }

    /** A catch that went to a box: the roster gets it from what the battle showed of the enemy. */
    private fun boxedCatch(ctx: Battle, e: NzEnemy, at: Long) {
        val origin = originFor(ctx.plan)
        val mon = RosterMon(e.id, e.species, Names.pretty(e.speciesName), "", e.level, e.gender, origin, ctx.area.key, ctx.area.name, at)
        mon.shiny = e.shiny
        mon.types = e.types
        mon.inParty = false
        ledger.roster[e.id] = mon
        ctx.caught = mon
        ledger.event(at, "catch", "Caught ${mon.speciesName} Lv ${e.level} at ${ctx.area.name}; the party was full, so it is in a box.", areaKey = ctx.area.key)
        flagCatch(ctx, mon, ctx.area, at)
        if (rules.wedlocke) pair(mon, at)
        ledger.touch()
    }

    private fun checkNickname(mon: RosterMon, at: Long) {
        if (!rules.nicknames || !mon.inParty) return
        if (!mon.hasNickname)
            ledger.warn("nickname:${mon.id}", at, WarnKind.NICKNAME, "${mon.speciesName} has no nickname.")
    }

    private fun itemUse(ctx: Battle, s: Snapshot, at: Long) {
        val start = ctx.bag ?: return
        val now = s.bag ?: return
        val used = start.mapNotNull { (id, item) ->
            val left = now[id]?.qty ?: 0
            if (left < item.qty) Names.pretty(item.name) to (item.qty - left) else null
        }
        // Many different items changing at once is a misread of the bag, not a battle.
        if (used.isEmpty() || used.size > 6) return
        val list = used.joinToString(", ") { (n, q) -> if (q > 1) "$n x$q" else n }
        val against = ctx.opponent?.label ?: ctx.enemy?.let { Names.pretty(it.speciesName) }
        ledger.warn("item:${ctx.startedAt}", at, WarnKind.ITEM, "Used $list in battle" + (against?.let { " against $it" } ?: "") + ".")
    }

    // ------------------------------------------------------------------ whiteout, the end of the run

    /**
     * A whole party at 0 HP is a whiteout, on the first poll that shows it. A read is only taken for the whole party
     * when it decoded every slot the game has (a slot that failed is not a fainted Pokemon), and no other state has
     * every Pokemon down. It is not held for a second poll: the panel only hears about a poll that changed something,
     * and the party sits unchanged through the whole blackout until the heal.
     */
    private fun checkWhiteout(real: List<NzMon>, s: Snapshot, at: Long) {
        if (!meta.started) return
        val whole = s.party.size >= s.partyCount
        val allDown = whole && real.isNotEmpty() && real.all { it.hp <= 0 }
        if (!allDown && meta.whiteoutLatched && real.any { it.hp > 0 }) { meta.whiteoutLatched = false; ledger.touch() }
        if (allDown && !meta.whiteoutLatched) {
            meta.whiteoutLatched = true
            ledger.event(at, "whiteout", "Whiteout: every Pokemon in the party fainted.")
            ledger.touch()
            if (rules.whiteoutEndsRun) { finish(RunStatus.OVER, "Whiteout", at); return }
        }
        if (ledger.roster.isNotEmpty() && ledger.alive.isEmpty() && rules.faintIsDeath) finish(RunStatus.OVER, "No living Pokemon left", at)
    }

    private fun finish(status: RunStatus, reason: String, at: Long) {
        closeBattle()
        meta.status = status
        meta.endedAt = at
        meta.endReason = reason
        ledger.event(at, if (status == RunStatus.COMPLETE) "complete" else "over", reason + ".")
        ledger.touch()
    }

    /**
     * The run ended in the middle of a wild battle, so no later poll will see how it ended. The encounter it made
     * is settled as it stands: caught if a catch was seen, otherwise lost.
     */
    private fun closeBattle() {
        val ctx = battle ?: return
        battle = null
        val e = ctx.enemy ?: return
        if (!ctx.wild || ctx.ghost) return
        val outcome = if (ctx.caught != null) Outcome.CAUGHT else Outcome.LOST
        val rec = ledger.areas[ctx.area.key] ?: return
        rec.encounter?.let { enc ->
            if (enc.pid == e.id && enc.outcome == Outcome.IN_PROGRESS && !enc.manual) { enc.outcome = outcome; enc.monId = ctx.caught?.id }
        }
        rec.extras.firstOrNull { it.pid == e.id && it.outcome == Outcome.IN_PROGRESS }?.let { it.outcome = outcome; it.monId = ctx.caught?.id }
        ledger.touch()
    }

    private fun complete(at: Long) {
        meta.heirsOut.clear()
        ledger.alive.forEach { meta.heirsOut += Heir(it.species, it.speciesName, it.nickname, it.level, it.gender, it.shiny) }
        finish(RunStatus.COMPLETE, "Champion beaten", at)
    }

    // ------------------------------------------------------------------ set battle style

    private fun checkStyle(s: Snapshot, at: Long) {
        val set = s.battleStyleSet
        if (!rules.setStyle || set == null) return
        if (!set && !meta.styleShift) {
            meta.styleShift = true
            ledger.warn("style:$at", at, WarnKind.STYLE, "The battle style is Shift. The rules say Set.")
            ledger.touch()
        } else if (set && meta.styleShift) {
            meta.styleShift = false
            ledger.touch()
        }
    }

    // ------------------------------------------------------------------ Wedlocke pairs

    private fun pair(mon: RosterMon, at: Long) {
        if (mon.partner != null) return
        // Generation 1 has no genders: the pairs go by the order of the catches, the oldest one still waiting first.
        if (!system.hasGender) {
            val mate = ledger.alive.firstOrNull { it.id != mon.id && it.partner == null } ?: return
            mon.partner = mate.id
            mate.partner = mon.id
            ledger.event(at, "pair", "${mon.shownName} and ${mate.shownName} are a pair.")
            return
        }
        val g = mon.gender ?: return
        val mate = ledger.alive.firstOrNull { it.id != mon.id && it.partner == null && it.gender != null && it.gender != g } ?: return
        mon.partner = mate.id
        mate.partner = mon.id
        ledger.event(at, "pair", "${mon.shownName} and ${mate.shownName} are a pair.")
    }

    private fun widow(dead: RosterMon, at: Long) {
        val partner = dead.partner?.let { ledger.roster[it] } ?: return
        dead.partner = null
        partner.partner = null
        if (partner.alive) ledger.event(at, "widow", "${partner.shownName} lost its partner ${dead.shownName}.")
    }
}
