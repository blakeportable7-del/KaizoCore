package com.ironmonone.tracker

import com.ironmonone.tracker.MoveRules.Shown
import com.ironmonone.tracker.MoveRules.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The PC tracker's move-table rules (Ironmon-Tracker v9.3.1). */
class MoveRulesTest {
    // Gen 3 type ids
    private val NORMAL = 0; private val FIGHTING = 1; private val FLYING = 2; private val POISON = 3
    private val GROUND = 4; private val ROCK = 5; private val GHOST = 7; private val STEEL = 8
    private val FIRE = 10; private val WATER = 11; private val GRASS = 12; private val ELECTRIC = 13; private val ICE = 15

    @Test
    fun `variable-power moves show the reference's label, not the ROM placeholder`() {
        assertEquals(">FR", MoveRules.basePower(216, 1))    // Return
        assertEquals("<FR", MoveRules.basePower(218, 1))    // Frustration
        assertEquals("WT", MoveRules.basePower(67, 1))      // Low Kick
        assertEquals("<HP", MoveRules.basePower(175, 1))    // Flail
        assertEquals(">HP", MoveRules.basePower(284, 150))  // Eruption
        assertEquals("RNG", MoveRules.basePower(222, 1))    // Magnitude
        assertEquals("100x", MoveRules.basePower(255, 100)) // Spit Up
        assertEquals("0", MoveRules.basePower(69, 1))       // Seismic Toss: a dash
        assertEquals("80", MoveRules.basePower(94, 80))     // Psychic: the ROM's number
    }

    @Test
    fun `STAB is a damaging move of the attacker's own type`() {
        assertTrue(MoveRules.isStab(85, ELECTRIC, "SPE", "95", listOf(ELECTRIC, STEEL)))  // Thunderbolt on Magneton
        assertFalse(MoveRules.isStab(85, ELECTRIC, "SPE", "95", listOf(WATER)))
        assertFalse(MoveRules.isStab(86, ELECTRIC, "STA", "0", listOf(ELECTRIC)))         // Thunder Wave: status
        assertFalse(MoveRules.isStab(248, 14, "SPE", "80", listOf(14)))                   // Future Sight: typeless
        assertFalse(MoveRules.isStab(69, FIGHTING, "PHY", "0", listOf(FIGHTING)))          // Seismic Toss: no power
        assertTrue(MoveRules.isStab(67, FIGHTING, "PHY", "WT", listOf(FIGHTING)))          // Low Kick's label still counts
    }

    @Test
    fun `effectiveness multiplies both types once each`() {
        assertEquals(4.0, MoveRules.effectiveness(58, ICE, "SPE", listOf(GRASS, FLYING)))   // Ice Beam on Grass/Flying
        assertEquals(0.25, MoveRules.effectiveness(52, FIRE, "SPE", listOf(WATER, ROCK)))   // Ember on Water/Rock
        assertEquals(2.0, MoveRules.effectiveness(58, ICE, "SPE", listOf(GRASS, GRASS)))    // one type listed twice counts once
        assertEquals(0.0, MoveRules.effectiveness(33, NORMAL, "PHY", listOf(GHOST)))        // Tackle on a Ghost
    }

    @Test
    fun `status moves only register an immunity`() {
        assertEquals(0.0, MoveRules.effectiveness(86, ELECTRIC, "STA", listOf(GROUND)))     // Thunder Wave on Ground
        assertEquals(0.0, MoveRules.effectiveness(92, POISON, "STA", listOf(NORMAL, STEEL)))// Toxic on Steel
        assertEquals(1.0, MoveRules.effectiveness(86, ELECTRIC, "STA", listOf(WATER)))      // not "super effective"
        assertEquals(1.0, MoveRules.effectiveness(45, NORMAL, "STA", listOf(GHOST)))        // Growl ignores the chart
    }

    @Test
    fun `Hidden Power's type is unknown until set, so it is neither STAB nor effective`() {
        assertNull(MoveRules.shownType(237, NORMAL))
        assertEquals(1.0, MoveRules.effectiveness(237, MoveRules.shownType(237, NORMAL), "PHY", listOf(GHOST)))
    }

    private fun adjust(id: Int, power: String, acc: String = "100", type: Int? = NORMAL,
                       src: Side = Side(20), tgt: Side? = Side(20), battle: Boolean = true,
                       own: Boolean = true, weather: String? = null, friend: Boolean = true) =
        MoveRules.adjust(id, type, power, acc, src, tgt, battle, own, weather, friend)

    @Test
    fun `Weather Ball takes the weather's type and doubles`() {
        assertEquals(Shown(WATER, "100", "100"), adjust(311, "50", weather = "RAIN"))
        assertEquals(Shown(ROCK, "100", "100"), adjust(311, "50", weather = "SANDSTORM"))
        assertEquals(Shown(NORMAL, "50", "100"), adjust(311, "50", weather = null))
        assertEquals(Shown(NORMAL, "50", "100"), adjust(311, "50", weather = "SUN", battle = false))
    }

    @Test
    fun `Low Kick reads the target's weight in battle`() {
        assertEquals("20", adjust(67, "WT", tgt = Side(20, weightKg = 6.0)).power)     // Pikachu
        assertEquals("100", adjust(67, "WT", tgt = Side(20, weightKg = 120.0)).power)
        assertEquals("120", adjust(67, "WT", tgt = Side(20, weightKg = 460.0)).power)   // Snorlax
        assertEquals("WT", adjust(67, "WT", tgt = Side(20, weightKg = 6.0), battle = false).power)
    }

    @Test
    fun `Flail and Eruption read HP, but only your own`() {
        assertEquals("200", adjust(175, "<HP", src = Side(20, curHp = 1, maxHp = 100)).power)
        assertEquals("20", adjust(175, "<HP", src = Side(20, curHp = 100, maxHp = 100)).power)
        assertEquals("<HP", adjust(175, "<HP", src = Side(20, curHp = 1, maxHp = 100), own = false).power)
        assertEquals("150", adjust(284, ">HP", src = Side(20, curHp = 80, maxHp = 80)).power)
        assertEquals("75", adjust(284, ">HP", src = Side(20, curHp = 40, maxHp = 80)).power)
        assertEquals("1", adjust(284, ">HP", src = Side(20, curHp = 0, maxHp = 80)).power)
    }

    @Test
    fun `Return shows a number only from 100 power, and only with friendship readiness on`() {
        assertEquals("102", adjust(216, ">FR", src = Side(20, friendship = 255)).power)
        assertEquals(">FR", adjust(216, ">FR", src = Side(20, friendship = 200)).power)   // 80: still hidden
        assertEquals(">FR", adjust(216, ">FR", src = Side(20, friendship = 255), friend = false).power)
        assertEquals("102", adjust(218, "<FR", src = Side(20, friendship = 0)).power)     // Frustration inverts
        assertEquals(">FR", adjust(216, ">FR", src = Side(20, friendship = 255), own = false).power)
    }

    @Test
    fun `one-hit KO accuracy rises with the level gap and fails upward`() {
        assertEquals("40", adjust(12, "0", acc = "30", src = Side(30), tgt = Side(20)).acc)
        assertEquals("X", adjust(12, "0", acc = "30", src = Side(20), tgt = Side(30)).acc)
        assertEquals("30", adjust(12, "0", acc = "30", src = Side(20), tgt = Side(20)).acc)
        assertEquals("100", adjust(329, "0", acc = "30", src = Side(100), tgt = Side(2)).acc)
    }
}
