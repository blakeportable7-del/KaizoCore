package com.ironmonone.pcui

import androidx.compose.runtime.Composable
import com.ironmonone.app.GachaMonRulesets
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.gachamon.GachaMonBst
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonMaker
import com.ironmonone.tracker.gachamon.GachaMonMoves
import com.ironmonone.tracker.gachamon.GachaMonRatingSystem
import com.ironmonone.tracker.gachamon.MoveCategory
import com.ironmonone.tracker.gachamon.MoveFacts
import com.ironmonone.tracker.gachamon.SixStats

/**
 * GachaMon's stars beside the heals (TrackerScreen.Buttons.GachaMonStars, besteon's Ironmon-Tracker, MIT): the card of
 * the Pokemon on view as it would rate now, against the stars of the card it got when it joined the run
 * (GachaMonData.updateMainScreenViewedGachaMon). The phone's GachaMon.currentStars and monOf, read here off the
 * tracker's own Pokemon; the PC keeps no collection, so the card it joined with is its stars the first time KaizoCore
 * PC saw it this run (kept in the run's folder).
 */
object PcGachaStars {
    /** GachaMonData.autoDetermineIronmonRuleset for the run's settings name ("RSE NatDex v1.2 Kaizo" is Kaizo). */
    fun ruleset(settingsName: String, natDex: Boolean): String = GachaMonRulesets.detect(settingsName, natDex)

    /** The card's key in the run (the phone's GachaMonCard.pidIndex: personality plus species). */
    fun key(p: TrackedMon): Long = p.mon.pid + p.mon.species

    /** The stars [p]'s card would have now, or null when its species cannot be read. */
    fun stars(t: GbaTracker, p: TrackedMon, ruleset: String): Int? {
        val natDex = t.expandedSpeciesIds
        val m = p.mon
        val base = p.base ?: t.baseStats(m.species) ?: return null
        val abilityId = if (m.abilitySlot == 1) base.ability2 else base.ability1
        val moves = m.moves.filter { it in 1..t.lastMoveId }.mapNotNull { id ->
            t.moveRowFor(id)?.let { r ->
                val type = r.type ?: 9
                var cat = when (r.category) { "PHY" -> MoveCategory.PHYSICAL; "SPE" -> MoveCategory.SPECIAL; else -> MoveCategory.STATUS }
                if (type == 9 && cat != MoveCategory.STATUS) cat = MoveCategory.NONE
                MoveFacts(r.id, type, GachaMonMoves.powerText(r.id, r.power ?: 0), r.acc ?: 0, cat)
            }
        }
        val listed = if (t.heartSoul) com.ironmonone.tracker.HnsSpecies.natDexId(m.species)?.let { GachaMonBst.listed(it, true) }
            else GachaMonBst.listed(m.species, natDex)
        val mon = GachaMonMaker.Mon(
            personality = m.pid, species = m.species, level = m.level, abilityId = abilityId,
            stats = SixStats(m.maxHp, m.atk, m.def, m.spAtk, m.spDef, m.spe),
            moves = moves, nature = m.nature,
            gender = when (com.ironmonone.tracker.Gender3.of(base.genderRatio, m.pid)) {
                com.ironmonone.tracker.Gender3.MALE -> 1; com.ironmonone.tracker.Gender3.FEMALE -> 2; else -> 0
            },
            shiny = m.shiny, types = if (base.type2 != base.type1) listOf(base.type1, base.type2) else listOf(base.type1),
            baseStats = SixStats(base.hp, base.atk, base.def, base.spAtk, base.spDef, base.spe),
            listedBst = listed ?: base.bst,
            evolves = t.evolution(m.species) != null,
        )
        val rs = GachaMonRatingSystem.default
        return rs.stars(rs.ratingScore(GachaMonMaker.ratingInput(mon, natDex), ruleset), com.ironmonone.tracker.gachamon.GachaMonCodec.CURRENT_VERSION)
    }

    /** The Pokemon on view as a card. A tap on the heals-box stars opens it, the same tap the phone uses. */
    fun viewCard(t: GbaTracker, p: TrackedMon, attempt: Int): PcGachaMon? {
        val rules = ruleset("", t.expandedSpeciesIds)
        val m = p.mon
        val base = p.base ?: t.baseStats(m.species) ?: return null
        val abilityId = if (m.abilitySlot == 1) base.ability2 else base.ability1
        val rows = m.moves.filter { it in 1..t.lastMoveId }.mapNotNull { t.moveRowFor(it) }
        val facts = rows.map { r ->
            val type = r.type ?: 9
            var cat = when (r.category) { "PHY" -> MoveCategory.PHYSICAL; "SPE" -> MoveCategory.SPECIAL; else -> MoveCategory.STATUS }
            if (type == 9 && cat != MoveCategory.STATUS) cat = MoveCategory.NONE
            MoveFacts(r.id, type, GachaMonMoves.powerText(r.id, r.power ?: 0), r.acc ?: 0, cat)
        }
        val listed = if (t.heartSoul) com.ironmonone.tracker.HnsSpecies.natDexId(m.species)?.let { GachaMonBst.listed(it, true) }
            else GachaMonBst.listed(m.species, t.expandedSpeciesIds)
        val mon = GachaMonMaker.Mon(
            personality = m.pid, species = m.species, level = m.level, abilityId = abilityId,
            stats = SixStats(m.maxHp, m.atk, m.def, m.spAtk, m.spDef, m.spe), moves = facts, nature = m.nature,
            gender = when (com.ironmonone.tracker.Gender3.of(base.genderRatio, m.pid)) {
                com.ironmonone.tracker.Gender3.MALE -> 1; com.ironmonone.tracker.Gender3.FEMALE -> 2; else -> 0
            },
            shiny = m.shiny, types = if (base.type2 != base.type1) listOf(base.type1, base.type2) else listOf(base.type1),
            baseStats = SixStats(base.hp, base.atk, base.def, base.spAtk, base.spDef, base.spe),
            listedBst = listed ?: base.bst, evolves = t.evolution(m.species) != null,
        )
        val date = java.time.LocalDate.now()
        val version = if (t.heartSoul) GachaMonCard.HEART_SOUL else GachaMonCard.gameVersionOf("")
        val card = GachaMonMaker.make(mon, t.expandedSpeciesIds, version, attempt, date.year, date.monthValue, date.dayOfMonth, rules, random = kotlin.random.Random(m.pid))
        return PcGachaMon(
            card, PcLogOverlay.nice(t.speciesName(m.species)),
            if (abilityId > 0) PcLogOverlay.nice(t.abilityName(abilityId)) else PcBesteon.BLANK,
            rows.map { r -> PcLogOverlay.nice(r.name) to (r.power?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK) },
            null,
        )
    }

    /** Constants.PixelImages.STAR's colours: yellow, gained (orange), lost (faded). */
    private val YELLOW = listOf(0xFFFCED86L, 0xFFEACA1DL, 0xFFFFFFFFL, 0x40000000L)
    private val PLATINUM = listOf(0xFFEEEEEEL, 0xFFCCCCCCL, 0xFFFFFFFFL, 0x40000000L)
    private val GAINED = listOf(0xFFF99F5EL, 0xFFE87320L, 0xFFF7E0CFL, 0x40000000L)
    private val LOST = listOf(0xFF877E48L, 0xFF847111L, 0xFF848484L, 0x20000000L)

    /**
     * GachaMonStars.draw at its box (x 61, y 58, 38 by 21 from the panel's left): [now] stars, gains orange and losses
     * faded against [made] (GachaMonOverlay.drawStarsOfGachaMon), moved down 3 under five and right 10 under three.
     */
    @Composable
    fun Draw(now: Int, made: Int, onOpen: () -> Unit = {}) {
        val most = maxOf(now, made)
        if (now < 1) return
        var x = 61; var y = 58
        if (most < 5) y += 3
        if (most < 3) x += 10
        y += 1
        val initial = made.takeIf { it > 0 }
        var draw = maxOf(initial ?: 0, now)
        val two = draw >= 5
        var plain = YELLOW
        if (draw > 5) { plain = PLATINUM; draw = 5 }
        val size = if (draw == 5) 10 else 9
        if (two) x += 3
        for (i in 1..draw) {
            var ix = x + 1 + size * (i - 1); var iy = y + 1
            val colors = when {
                initial != null && i > initial && i <= now -> GAINED
                initial != null && i > now && i <= initial -> LOST
                else -> plain
            }
            if (i >= 4 && two) { ix = ix + 5 - 3 * size; iy = iy + size - 4 }
            BPix(BImg.STAR, ix, iy, bColors(colors))
        }
        if (draw > 0) BHit(x, y, size * draw.coerceAtMost(3) + 8, if (two) 20 else 12, onOpen)
    }
}
