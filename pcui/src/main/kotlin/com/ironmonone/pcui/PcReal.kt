package com.ironmonone.pcui

import com.ironmonone.app.PcMove
import com.ironmonone.app.TrackerOptions
import com.ironmonone.tracker.HitChance
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState

/**
 * What the card needs from a battle for the real hit chance and move power: the names on each side's own list (the
 * game's battle details: "Lock-On", "Foresight", "Minimize", "Charge", "Confused", "Mud Sport", "Water Sport"), and the
 * ROM's move and item data. The session fills it from each read; the lambdas are the session's own, so an unchanged
 * battle draws no new card.
 */
data class PcBattleFacts(
    val own: Set<String> = emptySet(),
    val foe: Set<String> = emptySet(),
    val field: Set<String> = emptySet(),
    val move: (Int) -> HitChance.Move? = { null },
    val hold: (Int) -> Pair<HitChance.Hold, Int> = { HitChance.Hold.UNKNOWN to 0 },
)

/**
 * Your moves' Acc and Pow as they are now, in battle (Blake, 2026-10-10: "can we do it for accuracy and for move power?
 * With an option toggle for both?"; "Like 100 shows at 67"). Only what the player knows counts, as the tracker never
 * reveals what the game has not shown: the stages, the weather, the battle's own list, your ability and item, and the
 * opponent's ability once the game has shown it (one seen; with two seen it is not known which). The opponent's item is
 * never counted.
 *
 * Acc ("Show real hit chance"): HitChance, the game's own accuracy check; a sure hit prints the card's dash.
 * Pow ("Show real move power"): Gen 3's power changes on top of the card's own (MoveRules.adjust already worked out
 * Weather Ball, Low Kick, Flail and the rest): Fire and Water in sun and rain, Solar Beam in rain, sand and hail, Charge,
 * Mud Sport and Water Sport, Blaze, Torrent, Overgrow and Swarm at a third of HP or less, a type item (x1.1, the build's
 * I_TYPE_BOOST_POWER is Gen 3), Facade with a status, SmellingSalt on a paralyzed opponent, and the opponent's Thick Fat.
 * Not here: STAB (the card already marks it green), effectiveness (the chevrons), and anything that moves an Attack stat.
 * With real power on, the weather arrow by Pow is not drawn: the number has the weather in it.
 */
internal object PcReal {
    private const val SOLAR_BEAM = 76
    private const val FACADE = 263
    private const val SMELLING_SALT = 265
    private const val FIRE = 10; private const val WATER = 11; private const val ELECTRIC = 13; private const val ICE = 15

    /** Blaze, Torrent, Overgrow, Swarm: the type each powers up at a third of HP. */
    private val PINCH = mapOf("blaze" to FIRE, "torrent" to WATER, "overgrow" to 12, "swarm" to 6)

    /** Gen 3's type items: the type each powers up by a tenth (Sea Incense by a twentieth). */
    private val TYPE_ITEM = mapOf(
        "silkscarf" to 0, "blackbelt" to 1, "sharpbeak" to 2, "poisonbarb" to 3, "softsand" to 4, "hardstone" to 5,
        "silverpowder" to 6, "spelltag" to 7, "metalcoat" to 8, "charcoal" to FIRE, "mysticwater" to WATER,
        "miracleseed" to 12, "magnet" to ELECTRIC, "twistedspoon" to 14, "nevermeltice" to ICE, "dragonfang" to 16,
        "blackglasses" to 17,
    )

    private fun key(s: String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

    /** [rows] (your lead [p]'s moves as the card prints them) with the real Acc and Pow, for [st]'s battle. */
    fun own(rows: List<PcMove>, st: TrackerState, p: TrackedMon, facts: PcBattleFacts?, foeAbilities: List<String>): List<PcMove> {
        val e = st.enemy ?: return rows
        if (!st.inBattle || facts == null || (!TrackerOptions.realHitChance && !TrackerOptions.realMovePower)) return rows
        val foeAbility = foeAbilities.singleOrNull()
        val weather = st.weather.takeIf { !st.weatherSuppressed }
        val atk = side(p.statStages, p.abilityName.takeIf { it.isNotBlank() && it != "?" },
            if (p.mon.heldItem == 0) HitChance.Hold.NONE to 0 else facts.hold(p.mon.heldItem),
            setOfNotNull(p.base?.type1, p.base?.type2), p.mon.level, p.speciesName, p.statusCondition, facts.own, sureHit = "Lock-On" in facts.foe)
        val def = side(e.statStages, foeAbility, HitChance.Hold.UNKNOWN to 0, setOf(e.type1, e.type2), e.level, e.speciesName,
            e.statusCondition, facts.foe, sureHit = "Lock-On" in facts.own)
        val hw = when (weather) {
            "RAIN" -> HitChance.Weather.RAIN; "SUN" -> HitChance.Weather.SUN; "SANDSTORM" -> HitChance.Weather.SAND
            "HAIL", "SNOW" -> HitChance.Weather.HAIL_SNOW; else -> HitChance.Weather.NONE
        }
        val sports = facts.own + facts.foe + facts.field
        return rows.map { mv ->
            var out = mv
            if (TrackerOptions.realHitChance) {
                val shown = mv.accText ?: mv.acc?.toString()
                // A number the card prints (not the dash of a move that never misses, an OHKO's X, or a hidden "?").
                if (shown != null && (shown.toIntOrNull() ?: 0) > 0) facts.move(mv.id)?.let { m ->
                    val r = HitChance.of(m, atk, def, hw, weatherOn = !st.weatherSuppressed)
                    out = out.copy(accText = if (r.sure) "0" else r.chance.coerceIn(0, 100).toString())
                }
            }
            if (TrackerOptions.realMovePower) {
                val base = (mv.powerText ?: mv.power?.toString())?.toIntOrNull()
                if (base != null && base > 0) {
                    var k = 1.0
                    when (mv.type) {
                        FIRE -> k *= when (weather) { "SUN" -> 1.5; "RAIN" -> 0.5; else -> 1.0 }
                        WATER -> k *= when (weather) { "RAIN" -> 1.5; "SUN" -> 0.5; else -> 1.0 }
                    }
                    if (mv.id == SOLAR_BEAM && weather in setOf("RAIN", "SANDSTORM", "HAIL", "SNOW")) k *= 0.5
                    if (mv.type == ELECTRIC && "Charge" in facts.own) k *= 2.0
                    if (mv.type == ELECTRIC && "Mud Sport" in sports) k *= 0.5
                    if (mv.type == FIRE && "Water Sport" in sports) k *= 0.5
                    PINCH[key(p.abilityName)]?.let { t -> if (mv.type == t && p.mon.maxHp > 0 && p.mon.curHp * 3 <= p.mon.maxHp) k *= 1.5 }
                    val item = key(p.itemName)
                    TYPE_ITEM[item]?.let { t -> if (mv.type == t) k *= 1.1 }
                    if (item == "seaincense" && mv.type == WATER) k *= 1.05
                    if (mv.id == FACADE && p.statusCondition in setOf("PSN", "TOX", "BRN", "PAR")) k *= 2.0
                    if (mv.id == SMELLING_SALT && e.statusCondition == "PAR") k *= 2.0
                    if (key(foeAbility) == "thickfat" && (mv.type == FIRE || mv.type == ICE)) k *= 0.5
                    out = out.copy(powerText = (base * k).toInt().coerceAtLeast(1).toString(), weatherMark = 0)
                }
            }
            out
        }
    }

    private fun side(stages: Map<String, Int>, ability: String?, hold: Pair<HitChance.Hold, Int>, types: Set<Int>, level: Int,
                     species: String?, status: String, list: Set<String>, sureHit: Boolean): HitChance.Side {
        fun stage(k: String) = (stages[k] ?: 6) - 6
        return HitChance.Side(stage("ACC"), stage("EVA"), ability, hold.first, hold.second, types, level, species,
            confused = "Confused" in list, minimized = "Minimize" in list, foresight = "Foresight" in list, sureHit = sureHit,
            poisoned = status in setOf("PSN", "TOX"))
    }
}
