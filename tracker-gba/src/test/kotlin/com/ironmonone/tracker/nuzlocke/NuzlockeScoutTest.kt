package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Boss scouting's fence (2026-10-06): a randomized run, a Randomizer preset, a game whose first gym leaders do not field
 * their own teams, or one the tracker judges randomized, shows nothing and never even reads the trainer data. Only an
 * unrandomized game's own teams are shown.
 */
class NuzlockeScoutTest {

    /** FireRed's first two gym leaders as the game has them, plus Lt. Surge; [swap] replaces Brock's Onix with another species. */
    private class Fake(val swap: Int? = null, val randomized: Boolean? = null) : ScoutSource {
        var reads = 0
        private val names = mapOf(74 to "GEODUDE", 95 to "ONIX", 120 to "STARYU", 121 to "STARMIE", 26 to "RAICHU", 25 to "PIKACHU", 6 to "CHARIZARD")
        override fun team(trainerId: Int): Pair<String, List<ScoutMon>>? {
            reads++
            fun m(sp: Int, lv: Int) = ScoutMon(sp, names[sp] ?: "#$sp", lv, null, emptyList(), 18)
            return when (trainerId) {
                414 -> "LEADER Brock" to listOf(m(74, 12), m(swap ?: 95, 14))
                415 -> "LEADER Misty" to listOf(m(120, 18), m(121, 21))
                416 -> "LEADER Lt. Surge" to listOf(m(25, 18), m(26, 24))
                else -> null
            }
        }
        override fun teamsRandomized(): Boolean? = randomized
        override fun speciesName(species: Int): String = names[species] ?: "#$species"
    }

    private fun meta(bind: String = "lib-firered", preset: NuzlockePreset = NuzlockePreset.STANDARD) =
        RunMeta("nz-1", bind, "FireRed", NuzlockeRules(preset = preset), 1_000L)

    private val caps = LevelCapTable.standard("frlg")

    @Test
    fun `a randomized run shows nothing and reads no trainer data`() {
        val source = Fake()
        val view = NuzlockeScout.view(meta(bind = "firered/0123456789abcdef"), caps, emptySet(), source)
        assertEquals(NuzlockeScout.View.Hidden(NuzlockeScout.Closed.RANDOMIZED_RUN), view)
        assertEquals(0, source.reads)
    }

    @Test
    fun `the Randomizer preset shows nothing even on a library game`() {
        val source = Fake()
        val view = NuzlockeScout.view(meta(preset = NuzlockePreset.RANDOMIZER), caps, emptySet(), source)
        assertEquals(NuzlockeScout.View.Hidden(NuzlockeScout.Closed.RANDOMIZED_RUN), view)
        assertEquals(0, source.reads)
    }

    @Test
    fun `a library game whose gym leader has another ace shows nothing`() {
        // A ROM randomized outside the app and played from the library: Brock's Onix is a Charizard.
        val view = NuzlockeScout.view(meta(), caps, emptySet(), Fake(swap = 6))
        assertEquals(NuzlockeScout.View.Hidden(NuzlockeScout.Closed.NOT_OWN_TEAMS), view)
    }

    @Test
    fun `the tracker's own randomized judgement closes it`() {
        val view = NuzlockeScout.view(meta(), caps, emptySet(), Fake(randomized = true))
        assertEquals(NuzlockeScout.View.Hidden(NuzlockeScout.Closed.NOT_OWN_TEAMS), view)
    }

    @Test
    fun `no source or no table shows nothing`() {
        assertIs<NuzlockeScout.View.Hidden>(NuzlockeScout.view(meta(), caps, emptySet(), null))
        assertIs<NuzlockeScout.View.Hidden>(NuzlockeScout.view(meta(), null, emptySet(), Fake()))
    }

    @Test
    fun `an unrandomized game shows the next boss first and the rest after`() {
        val view = NuzlockeScout.view(meta(), caps, setOf("gym1"), Fake())
        assertIs<NuzlockeScout.View.Shown>(view)
        assertEquals(listOf("LEADER Misty"), view.next.map { it.name })
        assertEquals(listOf(21), view.next.single().party.map { it.level }.takeLast(1))
        // Brock is beaten and gone; Lt. Surge is later. Bosses whose team cannot be read are left out.
        assertEquals(listOf("LEADER Lt. Surge"), view.later.map { it.name })
    }

    @Test
    fun `a trainer Pokemon with no moves of its own knows its last four, none twice`() {
        val learnset = listOf(1 to 33, 1 to 39, 7 to 22, 13 to 33, 15 to 73, 20 to 75, 25 to 77, 32 to 230)
        assertEquals(listOf(33, 39, 22), NuzlockeScout.defaultMoves(learnset, 14))
        assertEquals(listOf(22, 73, 75, 77), NuzlockeScout.defaultMoves(learnset, 30))
        assertTrue(NuzlockeScout.defaultMoves(emptyList(), 50).isEmpty())
    }
}
