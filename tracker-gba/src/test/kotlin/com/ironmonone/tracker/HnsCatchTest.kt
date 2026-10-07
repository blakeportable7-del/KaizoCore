package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HnsCatch against the expansion's own arithmetic, worked by hand from src/battle_script_commands.c (kaizocore-rc37,
 * the C218FD9E build): ComputeCaptureOdds' integer steps in its order, then ComputeBallShakeOdds' two BIOS square roots
 * and four 16-bit shake checks. Each case names the C it stands for.
 */
class HnsCatchTest {
    private val bugSteel = listOf(HnsLayout.TYPE_BUG, HnsLayout.TYPE_STEEL, HnsLayout.TYPE_MYSTERY)

    /** A wild Scizor (catch rate 255 under the Kaizo preset's minimum catch rate level 4, 25 unchanged). */
    private fun scizor(level: Int, hp: Int = 60, catchRate: Int = 255, status: Long = 0) = HnsCatch.Target(
        species = 212, catchRate = catchRate, hp = hp, maxHp = 60, level = level, status1 = status, types = bugSteel,
        baseSpeed = 65, weight = 1180)

    private fun pct(ball: Int, t: HnsCatch.Target, f: HnsCatch.Field = HnsCatch.Field()) =
        HnsCatch.percent(HnsCatch.chance(HnsCatch.odds(ball, t, f)))

    @Test
    fun `the shake odds are the game's two integer square roots`() {
        // 16711680 / 85 = 196608, Sqrt 443, Sqrt 21, 1048560 / 21 = 49931.
        assertEquals(49931, HnsCatch.shakeOdds(85))
        // 16711680 / 170 = 98304, Sqrt 313, Sqrt 17: 61680.
        assertEquals(61680, HnsCatch.shakeOdds(170))
        // 16711680 / 221 = 75618, Sqrt 274, Sqrt 16: 65535, the most a throw under 255 gets.
        assertEquals(65535, HnsCatch.shakeOdds(221))
        assertEquals(0, HnsCatch.shakeOdds(0))
        // Four checks of RandomUniform(0, 65535) < 49931: (49931 / 65536)^4 = 0.3369.
        assertEquals(0.33695, HnsCatch.chance(85), 0.00001)
        assertEquals(1.0, HnsCatch.chance(255)); assertEquals(1.0, HnsCatch.chance(HnsCatch.GUARANTEED))
    }

    @Test
    fun `Blake's Scizor is a third at level 20 and all but certain at his early levels`() {
        // (60 * 3 - 60 * 2) * 255 / 180 = 85; no malus below level 26 with no badges; no low-level bonus above 13.
        assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20), HnsCatch.Field()))
        assertEquals(33, pct(HnsLayout.BALL_POKE, scizor(20)))
        // B_LOW_LEVEL_CATCH_BONUS Gen 9: level 13 and under, odds * (36 - 2 x level) / 10.
        assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(13), HnsCatch.Field()))
        assertEquals(102, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(12), HnsCatch.Field()))
        assertEquals(170, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(8), HnsCatch.Field()))
        assertEquals(78, pct(HnsLayout.BALL_POKE, scizor(8)))
        assertEquals(221, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(5), HnsCatch.Field()))
        assertEquals(99, pct(HnsLayout.BALL_POKE, scizor(5)))
        // Level 3: 85 * 30 / 10 = 255, over 254: caught outright.
        assertEquals(100, pct(HnsLayout.BALL_POKE, scizor(3)))
        // Worn down at level 20: half HP is (180 - 60) * 255 / 180 = 170 (78%); a quarter is 212 (99%).
        assertEquals(78, pct(HnsLayout.BALL_POKE, scizor(20, hp = 30)))
        assertEquals(212, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, hp = 15), HnsCatch.Field()))
        assertEquals(99, pct(HnsLayout.BALL_POKE, scizor(20, hp = 15)))
        // Its own catch rate, 25, without the preset: 60 * 25 / 180 = 8, 3%.
        assertEquals(3, pct(HnsLayout.BALL_POKE, scizor(20, catchRate = 25)))
    }

    @Test
    fun `the missing-badge malus takes a fifth for each badge level the wild Pokemon is above`() {
        // Level 40, no badges: above 25, 30 and 35 (not 40): 85 -> 68 -> 54 -> 43.
        assertEquals(43, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(40), HnsCatch.Field(badges = 0)))
        // Three badges start the walk at 40: nothing taken. Eight (or more, of the sixteen) take nothing at all.
        assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(40), HnsCatch.Field(badges = 3)))
        assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(70), HnsCatch.Field(badges = 12)))
        // Seven badges at level 70: 60 and then 100's levels: one step, 68.
        assertEquals(68, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(70), HnsCatch.Field(badges = 7)))
    }

    @Test
    fun `status bonuses are Gen 5's two and a half and one and a half`() {
        assertEquals(212, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 3), HnsCatch.Field()))      // asleep: 85 * 25 / 10
        assertEquals(212, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 0x20), HnsCatch.Field()))   // frozen
        assertEquals(127, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 0x40), HnsCatch.Field()))   // paralysed: 85 * 15 / 10
        assertEquals(127, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 0x80), HnsCatch.Field()))   // badly poisoned
        assertEquals(127, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 0x1000), HnsCatch.Field())) // frostbite
    }

    @Test
    fun `each ball's multiplier as this build configures it`() {
        val t = scizor(20)
        fun odds(ball: Int, f: HnsCatch.Field = HnsCatch.Field(), target: HnsCatch.Target = t) = HnsCatch.odds(ball, target, f)
        assertEquals(127, odds(HnsLayout.BALL_GREAT)); assertEquals(170, odds(HnsLayout.BALL_ULTRA))
        assertEquals(HnsCatch.GUARANTEED, odds(HnsLayout.BALL_MASTER))
        // Net x3.5 (Gen 7) on a Bug: 85 * 350 / 100.
        assertEquals(297, odds(HnsLayout.BALL_NET))
        // Nest, Gen 3's rule: 400 - 10 x level below 30.
        assertEquals(170, odds(HnsLayout.BALL_NEST)); assertEquals(85, odds(HnsLayout.BALL_NEST, HnsCatch.Field(badges = 8), scizor(30)))
        // Timer, Gen 3's: 100 + 10 a turn, x4 at most.
        assertEquals(127, odds(HnsLayout.BALL_TIMER, HnsCatch.Field(turn = 5))); assertEquals(340, odds(HnsLayout.BALL_TIMER, HnsCatch.Field(turn = 99)))
        // Quick x4 on the first turn only (Gen 3).
        assertEquals(340, odds(HnsLayout.BALL_QUICK)); assertEquals(85, odds(HnsLayout.BALL_QUICK, HnsCatch.Field(turn = 1)))
        // Repeat x3.5 once caught (Gen 7).
        assertEquals(85, odds(HnsLayout.BALL_REPEAT)); assertEquals(297, odds(HnsLayout.BALL_REPEAT, HnsCatch.Field(caughtBefore = true)))
        // Dusk x3.5 at evening or night, in a cave or underground (Gen 3).
        assertEquals(85, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(timeOfDay = HnsLayout.TIME_EVENING - 1)))
        assertEquals(297, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(timeOfDay = HnsLayout.TIME_NIGHT)))
        assertEquals(297, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(cave = true)))
        assertEquals(297, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(mapType = HnsLayout.MAP_TYPE_UNDERGROUND)))
        // Dive x3.5 underwater, surfing or fishing (Gen 4+).
        assertEquals(85, odds(HnsLayout.BALL_DIVE)); assertEquals(297, odds(HnsLayout.BALL_DIVE, HnsCatch.Field(surfing = true)))
        // Heavy: a Steel type takes Heart & Soul's x4, and 118 kg is the 0 bracket.
        assertEquals(340, odds(HnsLayout.BALL_HEAVY))
        // Fast: base speed 65 and Bug/Steel: nothing. Level: no level edge and no Normal, Flying or Ice: nothing.
        assertEquals(85, odds(HnsLayout.BALL_FAST)); assertEquals(85, odds(HnsLayout.BALL_LEVEL, HnsCatch.Field(playerLevel = 20)))
        // Level: player twice as high and more is x4; four times is x8.
        assertEquals(340, odds(HnsLayout.BALL_LEVEL, HnsCatch.Field(playerLevel = 41)))
        assertEquals(680, odds(HnsLayout.BALL_LEVEL, HnsCatch.Field(playerLevel = 80)))
        // Friend: Heart & Soul's x4 for Bug or Grass.
        assertEquals(340, odds(HnsLayout.BALL_FRIEND))
        // Beast on anything not an Ultra Beast: 85 * 410 / 4096 = 8.
        assertEquals(8, odds(HnsLayout.BALL_BEAST))
        // GS: x25.5, Celebi caught outright.
        assertEquals(2167, odds(HnsLayout.BALL_GS))
        assertEquals(HnsCatch.GUARANTEED, odds(HnsLayout.BALL_GS, target = t.copy(species = HnsLayout.SPECIES_CELEBI)))
        // The 1x balls.
        for (b in listOf(HnsLayout.BALL_POKE, HnsLayout.BALL_PREMIER, HnsLayout.BALL_HEAL, HnsLayout.BALL_LUXURY,
                HnsLayout.BALL_CHERISH, HnsLayout.BALL_PARK, HnsLayout.BALL_STRANGE, HnsLayout.BALL_MOON, HnsLayout.BALL_LURE,
                HnsLayout.BALL_LOVE, HnsLayout.BALL_DREAM)) assertEquals(85, odds(b), "ball $b")
        assertEquals(127, odds(HnsLayout.BALL_SAFARI)); assertEquals(127, odds(HnsLayout.BALL_SPORT))
    }

    @Test
    fun `the conditional balls' own rules`() {
        val plain = HnsCatch.Target(species = 1, catchRate = 45, hp = 30, maxHp = 30, level = 20, types = listOf(HnsLayout.TYPE_NORMAL, HnsLayout.TYPE_NORMAL, HnsLayout.TYPE_MYSTERY))
        // 30 * 45 / 90 = 15.
        assertEquals(15, HnsCatch.odds(HnsLayout.BALL_POKE, plain, HnsCatch.Field()))
        // Heavy, Gen 3's brackets: under 102.4 kg takes 20 off the catch rate: 30 * 25 / 90 = 8.
        assertEquals(8, HnsCatch.odds(HnsLayout.BALL_HEAVY, plain.copy(weight = 500), HnsCatch.Field()))
        // A Normal type takes Heart & Soul's Level Ball x4.
        assertEquals(60, HnsCatch.odds(HnsLayout.BALL_LEVEL, plain, HnsCatch.Field()))
        // Lure x3 when fishing (Gen 3) on a type it does not boost.
        val bug = plain.copy(types = listOf(HnsLayout.TYPE_BUG, HnsLayout.TYPE_BUG, HnsLayout.TYPE_MYSTERY))
        assertEquals(45, HnsCatch.odds(HnsLayout.BALL_LURE, bug, HnsCatch.Field(fishing = true)))
        // Moon x4 for a Moon Stone evolver; Dream x4 asleep (then x2.5 for the sleep).
        assertEquals(60, HnsCatch.odds(HnsLayout.BALL_MOON, bug.copy(evolvesByMoonStone = true), HnsCatch.Field()))
        assertEquals(150, HnsCatch.odds(HnsLayout.BALL_DREAM, bug.copy(status1 = 2), HnsCatch.Field()))
        // Love x8 for the same species, the other gender.
        val female = bug.copy(gender = HnsLayout.MON_FEMALE)
        assertEquals(120, HnsCatch.odds(HnsLayout.BALL_LOVE, female, HnsCatch.Field(playerSpecies = 1, playerGender = HnsLayout.MON_MALE)))
        assertEquals(15, HnsCatch.odds(HnsLayout.BALL_LOVE, female, HnsCatch.Field(playerSpecies = 1, playerGender = HnsLayout.MON_FEMALE)))
        // Fast x4 at base speed 100.
        assertEquals(60, HnsCatch.odds(HnsLayout.BALL_FAST, bug.copy(baseSpeed = 100), HnsCatch.Field()))
        // An Ultra Beast: x5 in a Beast Ball, 410 / 4096 in anything else.
        val ub = bug.copy(isUltraBeast = true)
        assertEquals(75, HnsCatch.odds(HnsLayout.BALL_BEAST, ub, HnsCatch.Field()))
        assertEquals(1, HnsCatch.odds(HnsLayout.BALL_ULTRA, ub, HnsCatch.Field()))
        // The Safari Zone's factor is the Safari Ball's catch rate: 8 * 1275 / 100 = 102, 30 * 102 / 90 = 34, x1.5 = 51.
        assertEquals(51, HnsCatch.odds(HnsLayout.BALL_SAFARI, bug, HnsCatch.Field(safari = true, safariCatchFactor = 8)))
        // Another ball in the Safari Zone keeps the species' own rate (Heart & Soul lets any ball be thrown there).
        assertEquals(15, HnsCatch.odds(HnsLayout.BALL_POKE, bug, HnsCatch.Field(safari = true, safariCatchFactor = 8)))
    }
}
