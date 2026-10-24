package com.ironmonone.pcui

import com.ironmonone.app.MoveDecorAccess
import com.ironmonone.app.StatMarks
import com.ironmonone.app.ownMoveContext
import com.ironmonone.app.stream.Json
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.HnsRules
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState

/**
 * What the chat commands read on the PC (PLAN row 9): the stream snapshot's JSON as the phone's StreamSnapshot builds
 * it, with the keys ChatAnswers reads (app, title, attempt, run, badges, heals, party: species, name, level, hp, maxHp,
 * types, ability, item, stats, bst, status, moves, nextMoveLevel, note). Your party only, as the phone's: nothing
 * about the opponent, the seed or the log is in it.
 */
object PcSnapshot {
    fun json(st: TrackerState?, attempt: Int, title: String, notes: StatMarks?): String = Json.write(linkedMapOf(
        "app" to "KaizoCore",
        "title" to title,
        "platform" to "GBA",
        "attempt" to attempt,
        "tracked" to (st != null),
        "run" to true,
        "inBattle" to (st?.inBattle ?: false),
        "wild" to (st?.isWildBattle ?: false),
        "badges" to (st?.badges ?: 0),
        "badgeSet" to st?.badgeSet,
        "heals" to mapOf("percent" to (st?.healPercent ?: 0), "count" to (st?.healCount ?: 0)),
        "party" to st?.party?.map { own(it, st, notes) }.orEmpty(),
    ))

    private fun type(id: Int?): String? = id?.let { Gen3Types.name(it) }

    private fun own(p: TrackedMon, st: TrackerState, notes: StatMarks?): Map<String, Any?> {
        val ctx = ownMoveContext(p, st.enemy?.takeIf { st.inBattle }, st.weather, null).copy(ownCategories = PcPanel.hnsOf(st) && !HnsRules.categoryByType)
        val m = p.mon
        return linkedMapOf(
            "species" to m.species, "name" to p.speciesName, "level" to m.level,
            "hp" to m.curHp, "maxHp" to m.maxHp,
            "types" to listOfNotNull(type(p.base?.type1), type(p.base?.type2).takeIf { p.base?.type2 != p.base?.type1 }),
            "ability" to p.abilityName, "item" to p.itemName,
            "stats" to mapOf("hp" to m.maxHp, "atk" to m.atk, "def" to m.def, "spa" to m.spAtk, "spd" to m.spDef, "spe" to m.spe),
            "bst" to p.base?.bst,
            "status" to p.statusCondition,
            "moves" to p.moveRows.map { moveRow(it, ctx) },
            "movesLearned" to p.movesLearned, "movesTotal" to p.movesTotal,
            "nextMoveLevel" to p.nextMoveLevel,
            "note" to notes?.noteFor(m.species).orEmpty(),
        )
    }

    /** A move as the card draws it (MoveDecor): the shown power, accuracy and type. */
    private fun moveRow(m: MoveRow, ctx: com.ironmonone.app.MoveContext?): Map<String, Any?> {
        val shown = with(MoveDecorAccess) { runCatching { m.shown(ctx) }.getOrNull() }
        return linkedMapOf(
            "name" to m.name, "pp" to (shown?.ppText ?: m.pp), "ppMax" to m.ppMax,
            "power" to (shown?.powerText?.takeIf { it != "0" } ?: m.power),
            "acc" to (shown?.accText?.takeIf { it != "0" } ?: m.acc),
            "type" to (if (shown != null) shown.typeName else type(m.type)),
            "category" to (shown?.category ?: m.category),
        )
    }
}
