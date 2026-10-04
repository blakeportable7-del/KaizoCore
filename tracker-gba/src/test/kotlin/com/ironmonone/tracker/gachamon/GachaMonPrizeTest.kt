package com.ironmonone.tracker.gachamon

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The prize card's Pokemon (GachaMonData.createPokemonDataFromDefeatedTrainers): from two different beaten common
 * trainers, the strongest Pokemon by listed base stat total, its moves or the last four it learned, its stats estimated
 * from its IVs, a neutral nature and its first ability; the trainer's id as its personality.
 */
class GachaMonPrizeTest {
    private val brock = 414
    private val misty = 415

    private val source = object : GachaMonPrize.Source {
        override fun party(trainerId: Int) = when (trainerId) {
            // Geodude 74 (300) Lv. 12 and Onix 95 (385) Lv. 14, default movesets
            brock -> listOf(GachaMonPrize.TrainerMon(74, 12, 15, emptyList()), GachaMonPrize.TrainerMon(95, 14, 15, emptyList()))
            // Staryu 120 (340) and Starmie 121 (520) Lv. 21, a custom moveset
            misty -> listOf(GachaMonPrize.TrainerMon(120, 18, 31, emptyList()), GachaMonPrize.TrainerMon(121, 21, 31, listOf(55, 61, 0, 0)))
            else -> emptyList()
        }
        override fun listedBst(species: Int) = mapOf(74 to 300, 95 to 385, 120 to 340, 121 to 520)[species] ?: 0
        override fun valid(species: Int) = species in setOf(74, 95, 120, 121)
        override fun learnset(species: Int) = listOf(1 to 33, 1 to 103, 8 to 20, 12 to 88, 16 to 99, 23 to 317, 30 to 175)
        override fun baseStats(species: Int) = SixStats(35, 45, 160, 30, 45, 70)
        override fun firstAbility(species: Int) = 69
    }

    @Test
    fun `no prize with fewer than two common trainers beaten`() {
        assertNull(GachaMonPrize.pick(listOf(brock), source, Random(3)))
        assertNull(GachaMonPrize.pick(emptyList(), source, Random(3)))
    }

    @Test
    fun `the stronger trainer's strongest Pokemon, with its own moves`() {
        // Whichever is drawn first, the other is drawn too, and Misty's Starmie (520) beats Brock's Onix (385).
        repeat(20) { seed ->
            val p = assertNotNull(GachaMonPrize.pick(listOf(brock, misty), source, Random(seed)))
            assertEquals(misty, p.trainerId)
            assertEquals(121, p.species)
            assertEquals(21, p.level)
            assertEquals(listOf(55, 61, 0, 0), p.moves)
            assertEquals(69, p.abilityId)
            assertTrue(p.nature in setOf(0, 6, 12, 18, 24), "a neutral nature, not ${p.nature}")
        }
    }

    @Test
    fun `a default moveset is the last four it learned by its level, and stats are estimated from the IVs`() {
        // 999 has no party, so Brock's Onix (Lv. 14) is the prize whichever is drawn first.
        val p = assertNotNull(GachaMonPrize.pick(listOf(brock, 999), source, Random(5)))
        assertEquals(brock, p.trainerId)
        assertEquals(95, p.species)
        // Learned by 14: 33 (1), 103 (1), 20 (8), 88 (12); 99 comes at 16.
        assertEquals(listOf(33, 103, 20, 88), p.moves)
        // floor(((ivs + 2 x base) x level / 100) + 5 + 0.5), HP adding 10 + level: (15 + 70) x 14 / 100 = 11.9 + 24.5
        assertEquals(36, p.stats.hp)
        assertEquals(floor(((15 + 2 * 160) * 14 / 100.0) + 5.5), p.stats.def)
    }

    private fun floor(d: Double) = kotlin.math.floor(d).toInt()
}
