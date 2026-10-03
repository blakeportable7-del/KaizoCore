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
    fun `a fixed-damage move checks immunities and nothing else`() {
        // rc33 audit P1 #77, Utils.lua:715-721: the ROM's power 1 made Seismic Toss and the rest read as ordinary attacks.
        val PSYCHIC = 14; val DRAGON = 16
        assertEquals(1.0, MoveRules.effectiveness(69, FIGHTING, "PHY", listOf(ROCK), power = "0"), "Seismic Toss on Rock")
        assertEquals(0.0, MoveRules.effectiveness(69, FIGHTING, "PHY", listOf(GHOST), power = "0"), "but not on a Ghost")
        assertEquals(0.0, MoveRules.effectiveness(101, GHOST, "SPE", listOf(NORMAL), power = "0"), "Night Shade on Normal")
        assertEquals(1.0, MoveRules.effectiveness(82, DRAGON, "SPE", listOf(DRAGON), power = "0"), "Dragon Rage on Dragon")
        assertEquals(0.0, MoveRules.effectiveness(90, GROUND, "PHY", listOf(FLYING), power = "0"), "Fissure on Flying")
        assertEquals(1.0, MoveRules.effectiveness(149, PSYCHIC, "SPE", listOf(STEEL), power = "0"), "Psywave on Steel")
        assertEquals(0.5, MoveRules.effectiveness(33, NORMAL, "PHY", listOf(ROCK), power = "35"), "Tackle on Rock is unchanged")
        assertEquals(0.5, MoveRules.effectiveness(33, NORMAL, "PHY", listOf(ROCK)), "and so is a caller that gives no power")
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

    /**
     * Low Kick was weight-based from Gen 3 on. The Gen 2 tracker's MoveData
     * gives it 50 power and 90 accuracy (Ironmon-gen-2-tracker MoveData.lua:
     * 938-943). The Gen 1 tracker's says "WT" but shows the ROM's 50: its
     * randomization check reads Gen 1's 90% Blizzard as randomized, so it
     * takes every power from the ROM (MoveData.lua:100-180).
     */
    @Test
    fun `Low Kick shows its ROM power on the Game Boy games and WT on GBA`() {
        assertEquals("50", MoveRules.basePower(67, 50, generation = 2))
        assertEquals("50", MoveRules.basePower(67, 50, generation = 1))
        assertEquals("WT", MoveRules.basePower(67, 1))
        for (gen in 1..2) assertEquals(MoveRules.VARIABLE_POWER - MoveRules.LOW_KICK, MoveRules.variablePower(gen), "every other label stays, Gen $gen")
    }

    /**
     * The Gen 1 tracker marks its move rows with its own chart (Utils.lua
     * netEffectiveness over MoveData.lua TypeToEffectiveness), not only Type
     * Defenses: Poison and Bug hit each other for 2x and Ghost does nothing
     * to Psychic.
     */
    @Test
    fun `Red, Blue and Yellow mark moves on the Gen 1 tracker's chart`() {
        val BUG = 6; val PSYCHIC = 14
        assertEquals(2.0, MoveRules.effectiveness(124, POISON, "PHY", listOf(BUG), gen1 = true))       // Sludge on a Bug
        assertEquals(1.0, MoveRules.effectiveness(124, POISON, "PHY", listOf(BUG)))
        assertEquals(0.0, MoveRules.effectiveness(122, GHOST, "PHY", listOf(PSYCHIC), gen1 = true))    // Lick on a Psychic
        assertEquals(2.0, MoveRules.effectiveness(122, GHOST, "PHY", listOf(PSYCHIC)))
        assertEquals(4.0, MoveRules.effectiveness(41, BUG, "PHY", listOf(POISON, GRASS), gen1 = true))  // Twineedle on Oddish
        assertEquals(1.0, MoveRules.effectiveness(41, BUG, "PHY", listOf(POISON, GRASS)))
    }

    @Test
    fun `move rows and Calc Atk follow the Nat Dex chart`() {
        // rc33 audit P1 #70: Shadow Ball on a Steel/Flying, and a Fairy move on a Dragon.
        assertEquals(0.5, MoveRules.effectiveness(247, 7, "PHY", listOf(8, 2), power = "80"))
        assertEquals(1.0, MoveRules.effectiveness(247, 7, "PHY", listOf(8, 2), power = "80", natDex = true))
        assertEquals(2.0, MoveRules.effectiveness(585, 18, "SPE", listOf(16, 16), power = "95", natDex = true))
        val fill = CalcAtk.autoFill(
            moveId = 247, power = "80", type = 7, category = "PHY", damage = 30,
            ownTypes = listOf(8, 2), ownDef = 100, ownSpd = 60, ownWeightKg = null,
            enemyTypes = listOf(7, 7), enemyLevel = 30, enemyCurHp = 50, enemyMaxHp = 90, enemyBurned = false,
            enemyBaseFriendship = null, wild = true, weatherWord = null, natDex = true,
        )
        assertEquals(1.0, fill.inputs.effectiveness)
    }
}
