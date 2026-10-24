package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HnsCatch against the expansion's own arithmetic, worked by hand from src/battle_script_commands.c as the KaizoCore
 * build compiles it (Nat. Dex Emerald's Gen 3 rules, build AA092306): ComputeCaptureOdds' integer steps in its order,
 * then ComputeBallShakeOdds' two BIOS square roots and four 16-bit shake checks. Each case names the C it stands for.
 *
 * Until 2026-10-11 these held the rc37 build's Gen 9 numbers, and with them the bug Blake hit: a level 5 Magikarp in a
 * Poke Ball read 99% (the Gen 9 low-level bonus) and took four balls, because the game gives it a third.
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
    fun `Blake's level 5 Magikarp at full health is a third in a Poke Ball, not 99`() {
        // Magikarp's catch rate, 255: (3 x 20 - 2 x 20) * 255 / 60 = 85, and Gen 3 adds nothing for a low level.
        val magikarp = HnsCatch.Target(species = 129, catchRate = 255, hp = 20, maxHp = 20, level = 5,
            types = listOf(HnsLayout.TYPE_WATER, HnsLayout.TYPE_WATER, HnsLayout.TYPE_MYSTERY))
        assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, magikarp, HnsCatch.Field()))
        assertEquals(33, pct(HnsLayout.BALL_POKE, magikarp))
    }

    @Test
    fun `a Scizor is a third at full health at any level, and the HP it has lost is what raises it`() {
        // (60 * 3 - 60 * 2) * 255 / 180 = 85 at every level: B_LOW_LEVEL_CATCH_BONUS is Gen 3, nothing.
        for (lv in listOf(3, 5, 8, 12, 13, 20)) {
            assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(lv), HnsCatch.Field()), "level $lv")
            assertEquals(33, pct(HnsLayout.BALL_POKE, scizor(lv)), "level $lv")
        }
        // Worn down at level 20: half HP is (180 - 60) * 255 / 180 = 170 (78%); a quarter is 212 (99%).
        assertEquals(78, pct(HnsLayout.BALL_POKE, scizor(20, hp = 30)))
        assertEquals(212, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, hp = 15), HnsCatch.Field()))
        assertEquals(99, pct(HnsLayout.BALL_POKE, scizor(20, hp = 15)))
        // Its own catch rate, 25, without the preset: 60 * 25 / 180 = 8, 3%.
        assertEquals(3, pct(HnsLayout.BALL_POKE, scizor(20, catchRate = 25)))
    }

    @Test
    fun `badges change nothing`() {
        // B_MISSING_BADGE_CATCH_MALUS is Gen 3: no malus however few badges and however high the level.
        for (b in listOf(0, 3, 7, 12)) for (lv in listOf(20, 40, 70))
            assertEquals(85, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(lv), HnsCatch.Field(badges = b)), "badges $b level $lv")
    }

    @Test
    fun `status bonuses are Gen 3's two and one and a half`() {
        assertEquals(170, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 3), HnsCatch.Field()))      // asleep: 85 * 2
        assertEquals(170, HnsCatch.odds(HnsLayout.BALL_POKE, scizor(20, status = 0x20), HnsCatch.Field()))   // frozen
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
        // Net x3 (Gen 3) on a Bug: 85 * 300 / 100.
        assertEquals(255, odds(HnsLayout.BALL_NET))
        // Nest, Gen 3's rule: 400 - 10 x level below 30.
        assertEquals(170, odds(HnsLayout.BALL_NEST)); assertEquals(85, odds(HnsLayout.BALL_NEST, HnsCatch.Field(), scizor(30)))
        // Timer, Gen 3's: 100 + 10 a turn, x4 at most.
        assertEquals(127, odds(HnsLayout.BALL_TIMER, HnsCatch.Field(turn = 5))); assertEquals(340, odds(HnsLayout.BALL_TIMER, HnsCatch.Field(turn = 99)))
        // Quick x4 on the first turn only (Gen 3).
        assertEquals(340, odds(HnsLayout.BALL_QUICK)); assertEquals(85, odds(HnsLayout.BALL_QUICK, HnsCatch.Field(turn = 1)))
        // Repeat x3 once caught (Gen 3).
        assertEquals(85, odds(HnsLayout.BALL_REPEAT)); assertEquals(255, odds(HnsLayout.BALL_REPEAT, HnsCatch.Field(caughtBefore = true)))
        // Dusk x3.5 in a cave or underground only: Emerald has no night for it.
        assertEquals(85, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(timeOfDay = HnsLayout.TIME_NIGHT)))
        assertEquals(297, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(cave = true)))
        assertEquals(297, odds(HnsLayout.BALL_DUSK, HnsCatch.Field(mapType = HnsLayout.MAP_TYPE_UNDERGROUND)))
        // Dive x3.5 underwater only (Gen 3): not surfing, not fishing.
        assertEquals(85, odds(HnsLayout.BALL_DIVE)); assertEquals(85, odds(HnsLayout.BALL_DIVE, HnsCatch.Field(surfing = true)))
        assertEquals(85, odds(HnsLayout.BALL_DIVE, HnsCatch.Field(fishing = true)))
        assertEquals(297, odds(HnsLayout.BALL_DIVE, HnsCatch.Field(mapType = HnsLayout.MAP_TYPE_UNDERWATER)))
        // Beast on anything not an Ultra Beast: 85 * 410 / 4096 = 8.
        assertEquals(8, odds(HnsLayout.BALL_BEAST))
        // Kurt's Apricorn Balls and the GS Ball are a Poke Ball in this build, whatever the target or the field.
        for (b in listOf(HnsLayout.BALL_LEVEL, HnsLayout.BALL_LURE, HnsLayout.BALL_MOON, HnsLayout.BALL_FRIEND, HnsLayout.BALL_LOVE,
                HnsLayout.BALL_FAST, HnsLayout.BALL_HEAVY, HnsLayout.BALL_GS)) {
            assertEquals(85, odds(b, HnsCatch.Field(playerLevel = 80, fishing = true)), "ball $b")
        }
        assertEquals(85, odds(HnsLayout.BALL_GS, target = t.copy(species = HnsLayout.SPECIES_CELEBI)))
        // The 1x balls, the Dream Ball among them (x4 asleep is Gen 8's).
        for (b in listOf(HnsLayout.BALL_POKE, HnsLayout.BALL_PREMIER, HnsLayout.BALL_HEAL, HnsLayout.BALL_LUXURY,
                HnsLayout.BALL_CHERISH, HnsLayout.BALL_PARK, HnsLayout.BALL_STRANGE, HnsLayout.BALL_DREAM)) assertEquals(85, odds(b), "ball $b")
        assertEquals(170, odds(HnsLayout.BALL_DREAM, target = scizor(20, status = 3)))
        assertEquals(127, odds(HnsLayout.BALL_SAFARI)); assertEquals(127, odds(HnsLayout.BALL_SPORT))
    }

    @Test
    fun `the Safari Zone and Ultra Beasts`() {
        val bug = HnsCatch.Target(species = 1, catchRate = 45, hp = 30, maxHp = 30, level = 20,
            types = listOf(HnsLayout.TYPE_BUG, HnsLayout.TYPE_BUG, HnsLayout.TYPE_MYSTERY))
        // 30 * 45 / 90 = 15.
        assertEquals(15, HnsCatch.odds(HnsLayout.BALL_POKE, bug, HnsCatch.Field()))
        // The Safari Zone's factor: 8 * 1275 / 100 = 102, 30 * 102 / 90 = 34, x1.5 = 51.
        assertEquals(51, HnsCatch.odds(HnsLayout.BALL_SAFARI, bug, HnsCatch.Field(safari = true, safariCatchFactor = 8)))
        // Every ball thrown there is a Safari Ball in this build: the same 51.
        assertEquals(51, HnsCatch.odds(HnsLayout.BALL_POKE, bug, HnsCatch.Field(safari = true, safariCatchFactor = 8)))
        assertEquals(51, HnsCatch.odds(HnsLayout.BALL_ULTRA, bug, HnsCatch.Field(safari = true, safariCatchFactor = 8)))
        // An Ultra Beast: x5 in a Beast Ball, 410 / 4096 in anything else.
        val ub = bug.copy(isUltraBeast = true)
        assertEquals(75, HnsCatch.odds(HnsLayout.BALL_BEAST, ub, HnsCatch.Field()))
        assertEquals(1, HnsCatch.odds(HnsLayout.BALL_ULTRA, ub, HnsCatch.Field()))
    }
}
