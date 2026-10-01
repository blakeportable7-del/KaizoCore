package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/** MoveUtils.netEffectiveness and BattleHandlerBase._setUpDelay, as the DS main screen uses them. */
class NdsMoveRulesTest {
    private fun move(name: String, type: String, power: Int, cat: String = "PHYSICAL", text: String = "", acc: Int = 100) =
        NdsMoveInfo(name, power, acc, type, 10, cat, 1, text)

    private fun e(m: NdsMoveInfo, vararg types: String, item: Int = 0, hp: String = "BUG") =
        NdsMoveRules.effectiveness(m, types.toList(), item, hp)

    @Test
    fun `the chart over the target's types, a repeated type counted once`() {
        assertEquals(0.0, e(move("Tackle", "NORMAL", 35), "GHOST"))
        assertEquals(4.0, e(move("Razor Leaf", "GRASS", 55), "WATER", "GROUND"))
        assertEquals(0.25, e(move("Ember", "FIRE", 40), "WATER", "ROCK"))
        assertEquals(0.5, e(move("Ember", "FIRE", 40), "WATER", "WATER"))
        assertEquals(1.0, e(move("Ember", "FIRE", 40)))
        // A text power (WT) is not "---": Low Kick is charted.
        assertEquals(2.0, e(move("Low Kick", "FIGHTING", 0, text = "WT"), "NORMAL"))
    }

    @Test
    fun `a move with no power is only ever immune, and a status move only when it is Poison`() {
        assertEquals(0.0, e(move("Seismic Toss", "FIGHTING", 0), "GHOST"))
        assertEquals(1.0, e(move("Seismic Toss", "FIGHTING", 0), "NORMAL"), "no chart for a move with no power")
        assertEquals(0.0, e(move("Toxic", "POISON", 0, "STATUS"), "STEEL"))
        assertEquals(1.0, e(move("Thunder Wave", "ELECTRIC", 0, "STATUS"), "GROUND"), "a status move that is not Poison")
    }

    @Test
    fun `Future Sight and a sure Doom Desire are neutral`() {
        assertEquals(1.0, e(move("Future Sight", "PSYCHIC", 80, "SPECIAL", acc = 90), "POISON"))
        assertEquals(1.0, e(move("Doom Desire", "STEEL", 140, "SPECIAL", acc = 100), "ICE"))
        assertEquals(2.0, e(move("Doom Desire", "STEEL", 120, "SPECIAL", acc = 85), "ICE"), "Gen 4's 85-accuracy Doom Desire is charted")
    }

    @Test
    fun `Hidden Power takes the tracker's type and Judgment the target's plate, as the reference passes them`() {
        val hp = move("Hidden Power", "UNKNOWN", 0, "SPECIAL", text = "VAR")
        assertEquals(2.0, e(hp, "GRASS"), "BUG, the tracker's starting type")
        assertEquals(0.5, e(hp, "GRASS", hp = "WATER"))
        val judgment = move("Judgment", "NORMAL", 100, "SPECIAL")
        assertEquals(0.0, e(judgment, "GHOST"))
        assertEquals(2.0, e(judgment, "GHOST", item = 310), "a Spooky Plate on the Pokemon being hit")
    }

    @Test
    fun `STAB is a move with a power of one of its user's types, never Hidden Power, Judgment by the user's plate`() {
        assertEquals(true, NdsMoveRules.isStab(move("Ember", "FIRE", 40), listOf("FIRE", "FLYING"), 0))
        assertEquals(false, NdsMoveRules.isStab(move("Ember", "FIRE", 40), listOf("WATER"), 0))
        assertEquals(false, NdsMoveRules.isStab(move("Will-O-Wisp", "FIRE", 0, "STATUS"), listOf("FIRE"), 0), "no power, no STAB")
        assertEquals(true, NdsMoveRules.isStab(move("Low Kick", "FIGHTING", 0, text = "WT"), listOf("FIGHTING"), 0), "WT is a power")
        assertEquals(false, NdsMoveRules.isStab(move("Hidden Power", "UNKNOWN", 0, "SPECIAL", text = "VAR"), listOf("BUG"), 0))
        val judgment = move("Judgment", "NORMAL", 100, "SPECIAL")
        assertEquals(true, NdsMoveRules.isStab(judgment, listOf("NORMAL"), 0))
        assertEquals(true, NdsMoveRules.isStab(judgment, listOf("GHOST"), 310), "Spooky Plate")
        assertEquals(false, NdsMoveRules.isStab(judgment, listOf("NORMAL"), 310))
    }

    @Test
    fun `a star for a move learned over since it was seen, by its age rank`() {
        val levels = listOf(5, 10, 15, 20, 25)
        // Seen at 12 (rank 3: two empty slots below it) with 15, 20, 25 learned since: a star.
        // Seen at 22 (rank 4) with only 25 since: none. Empty slots never.
        assertEquals(listOf(true, false, false, false), NdsMoveRules.stars(listOf(33 to 12, 52 to 22), 26, levels))
        assertEquals(listOf(true, true, true, true),
            NdsMoveRules.stars(listOf(1 to 10, 2 to 10, 3 to 10, 4 to 10), 30, listOf(12, 14, 16, 18, 20)), "all rank 1")
        assertEquals(listOf(false, false, false, false), NdsMoveRules.stars(listOf(33 to 1), 50, levels), "seen at level 1: never")
        assertEquals(listOf(false, false, false, false), NdsMoveRules.stars(listOf(33 to 12), 14, levels), "nothing learned since")
        // Repeated levels count twice, as pairs() over movelvls does (Bulbasaur learns two at 13):
        // the moves seen at 12 have rank 2 (one seen at 11 below them) and two learned since.
        assertEquals(listOf(true, true, true, true),
            NdsMoveRules.stars(listOf(33 to 12, 52 to 11, 10 to 12, 45 to 12), 13, listOf(13, 13)))
    }

    private fun side(hp: Int = 50, max: Int = 100, kg: Double = 50.0, stages: Map<String, Int>? = null) = NdsMoveRules.Side(hp, max, kg, stages)

    @Test
    fun `the variable powers, each with its own requirement`() {
        fun v(name: String, pp: Int = 5, user: NdsMoveRules.Side = side(), target: NdsMoveRules.Side? = side(), enemy: Boolean = false, battle: Boolean = true) =
            NdsMoveRules.variablePower(name, pp, user, target, enemy, battle)
        // Flail and Reversal: your own, by HP (percent < 4.17, 10.42, 20.83, 35.42, 68.75).
        assertEquals("200", v("Flail", user = side(4, 100)))
        assertEquals("150", v("Reversal", user = side(10, 100)))
        assertEquals("80", v("Flail", user = side(35, 100)))
        assertEquals("40", v("Flail", user = side(68, 100)))
        assertEquals("20", v("Flail", user = side(69, 100)))
        assertEquals(null, v("Flail", user = side(4, 100), enemy = true), "not the opponent's")
        assertEquals("200", v("Flail", user = side(4, 100), target = null, battle = false), "no opponent needed")
        // Eruption and Water Spout: 150 x HP fraction, at least 1, rounded.
        assertEquals("150", v("Eruption", user = side(100, 100)))
        assertEquals("75", v("Water Spout", user = side(50, 100)))
        assertEquals("1", v("Eruption", user = side(0, 100)))
        assertEquals(null, v("Eruption", enemy = true))
        // Trump Card: either side, by the PP the row shows.
        assertEquals("200", v("Trump Card", pp = 1, enemy = true))
        assertEquals("40", v("Trump Card", pp = 8))
        assertEquals("0", v("Trump Card", pp = 0))
        // Low Kick and Grass Knot: in battle with an opponent, by its weight.
        assertEquals("20", v("Low Kick", target = side(kg = 6.9)))
        assertEquals("100", v("Grass Knot", target = side(kg = 199.9)))
        assertEquals("120", v("Low Kick", target = side(kg = 200.0)))
        assertEquals(null, v("Low Kick", target = null), "no opponent")
        assertEquals(null, v("Low Kick", battle = false), "not in battle")
        // Heat Crash and Heavy Slam: its weight over yours.
        assertEquals("120", v("Heavy Slam", user = side(kg = 100.0), target = side(kg = 20.0)))
        assertEquals("80", v("Heat Crash", user = side(kg = 100.0), target = side(kg = 30.0)))
        assertEquals("40", v("Heavy Slam", user = side(kg = 100.0), target = side(kg = 60.0)))
        // Punishment: 60 + 20 per raised stage, to 200.
        assertEquals("60", v("Punishment", target = side(stages = null)))
        assertEquals("100", v("Punishment", target = side(stages = mapOf("ATK" to 8, "DEF" to 5, "EVA" to 6))))
        assertEquals("200", v("Punishment", target = side(stages = mapOf("ATK" to 12, "SPE" to 12))))
        // Not in the reference's list: the table's text stays.
        for (n in listOf("Frustration", "Gyro Ball", "Wring Out", "Crush Grip", "Electro Ball", "Return")) assertEquals(null, v(n), n)
    }

    @Test
    fun `Return shows its power only at 100 or more, only on your own`() {
        assertEquals("102", NdsMoveRules.returnPower("Return", 255, userIsEnemy = false))
        assertEquals("100", NdsMoveRules.returnPower("Return", 250, userIsEnemy = false))
        assertEquals(null, NdsMoveRules.returnPower("Return", 249, userIsEnemy = false), ">FR stays below 100")
        assertEquals(null, NdsMoveRules.returnPower("Return", 255, userIsEnemy = true))
        assertEquals(null, NdsMoveRules.returnPower("Frustration", 0, userIsEnemy = false))
    }

    @Test
    fun `the Gen 4 move table is the reference's, with its text powers`() {
        val t = NdsTracker({ _, _ -> ByteArray(0) }, null, NdsGameMap.PLATINUM)
        assertEquals("WT", t.moveInfoFor(67)?.powerText, "Low Kick")
        assertEquals("<HP", t.moveInfoFor(175)?.powerText, "Flail")
        assertEquals(">HP", t.moveInfoFor(284)?.powerText, "Eruption")
        assertEquals("VAR", t.moveInfoFor(237)?.powerText, "Hidden Power")
        assertEquals("UNKNOWN", t.moveInfoFor(237)?.type)
        assertEquals(0, t.moveInfoFor(12)?.power, "Guillotine prints ---, not the ROM's 1")
        assertEquals(6.9, NdsLogData.weight(1)); assertEquals(0.1, NdsLogData.weight(92), "Gastly")
    }

    @Test
    fun `the pause after a new opponent`() {
        assertEquals(150, NdsMoveRules.effectivenessDelayFrames(4, firstOfBattle = true))
        assertEquals(150, NdsMoveRules.effectivenessDelayFrames(4, firstOfBattle = false))
        assertEquals(240, NdsMoveRules.effectivenessDelayFrames(5, firstOfBattle = true))
        assertEquals(90, NdsMoveRules.effectivenessDelayFrames(5, firstOfBattle = false))
    }
}
