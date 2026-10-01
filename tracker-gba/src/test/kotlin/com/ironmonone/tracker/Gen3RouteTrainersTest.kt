package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trainers On Route, the notebook, the carousel and the log's route tab all take a map's trainers
 * from RouteData.Info[mapId].trainers in the reference (TrainerData.lua:490, Program.lua:1552,
 * RandomizerLog.lua:606-651), per version. The app used to carry FRLG's from the tile-click map
 * (FRLGTrainerRouteData.lua) and Emerald's for Ruby and Sapphire too (parity audit, 2026-09-28).
 * Expected lists are RouteData.lua's, in its order.
 */
class Gen3RouteTrainersTest {
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, map)

    @Test
    fun `FireRed and LeafGreen list what RouteData lists`() {
        for (map in listOf(GameMap.FIRERED_U_V10, GameMap.LEAFGREEN_U)) {
            val t = tracker(map)
            // Missing from the tile-click table: the lab's three rivals, Route 22, the Elite Four.
            assertEquals(listOf(326, 327, 328), t.trainersOnRoute(5), "${map.name} Oak's Lab")
            assertEquals(listOf(329, 330, 331, 435, 436, 437), t.trainersOnRoute(110), "${map.name} Route 22")
            assertEquals(listOf(listOf(410), listOf(411), listOf(412), listOf(413), listOf(438, 439, 440)),
                (213..217).map { t.trainersOnRoute(it) }, "${map.name} Elite Four")
            // The two S.S. Anne room maps were swapped, and Tanoby Ruins (257) has none in RouteData.
            assertEquals(listOf(483, 127, 223, 482, 422, 421, 126, 96), t.trainersOnRoute(177))
            assertEquals(listOf(138, 139, 224, 140, 136, 137), t.trainersOnRoute(178))
            assertEquals(emptyList(), t.trainersOnRoute(257))
            // The reference's order, not sorted: Fuchsia Gym ends on Koga.
            assertEquals(listOf(294, 295, 288, 289, 292, 293, 418), t.trainersOnRoute(20))
            assertEquals("Oak's Lab", t.routeInfo(5)?.first)
        }
    }

    @Test
    fun `Ruby and Sapphire have their own gyms, no rivals in Rustboro and no Space Center`() {
        for (map in listOf(GameMap.RUBY_U, GameMap.SAPPHIRE_U)) {
            val t = tracker(map)
            // RouteData.lua:4488-4494, the R/S gym lists. The reference keys these maps with
            // Emerald's ids (no R/S offset); here they sit on the map they name.
            assertEquals(listOf(426, 179, 425, 266), t.trainersOnRoute(65), "${map.name} Dewford Gym")
            assertEquals(listOf(320, 321, 265), t.trainersOnRoute(94), "${map.name} Rustboro Gym")
            assertEquals(listOf(233, 246, 245, 235, 234, 244, 271), t.trainersOnRoute(108), "${map.name} Mossdeep Gym")
            assertEquals("Mossdeep Gym", t.routeInfo(108)?.first)
            assertEquals(listOf(128, 613, 115, 131, 614, 301, 130, 118, 129, 272), t.trainersOnRoute(109))
            assertEquals(listOf(listOf(261), listOf(262), listOf(263), listOf(264), listOf(335)),
                (111..115).map { t.trainersOnRoute(it) })
            assertEquals("Champion's Room", t.routeInfo(115)?.first)
            assertEquals(emptyList(), t.trainersOnRoute(4), "no rivals in Rustboro in R/S")
            assertEquals(emptyList(), t.trainersOnRoute(275)); assertEquals(emptyList(), t.trainersOnRoute(276))
            assertEquals(listOf(80, 96, 97, 81, 100, 83, 99, 82, 98, 79, 519), t.trainersOnRoute(163))
            assertEquals(listOf(318, 615, 333, 603), t.trainersOnRoute(18))
            // Emerald-only maps are not R/S maps.
            assertTrue(336 !in t.routeMapIds() && 431 !in t.routeMapIds())
            assertEquals("Route 124 Water", t.routeInfo(274)?.first)
        }
    }

    @Test
    fun `Ruby's team trainers and hideout are Magma's, Sapphire's Aqua's`() {
        // RouteData.swapRubySapphireTeamTrainers (RouteData.lua:5909-5947) runs on Ruby only.
        val ruby = tracker(GameMap.RUBY_U)
        assertEquals(listOf("Magma Hideout 1F", "Magma Hideout B1F", "Magma Hideout B2F"), (143..145).map { ruby.routeInfo(it)?.first })
        assertEquals(listOf(567), ruby.trainersOnRoute(143))
        assertEquals(listOf(570, 593, 596, 193), ruby.trainersOnRoute(145))
        assertEquals(listOf(571, 572, 573, 600, 601), ruby.trainersOnRoute(147))
        val sapphire = tracker(GameMap.SAPPHIRE_U)
        assertEquals(listOf("Aqua Hideout 1F", "Aqua Hideout B1F", "Aqua Hideout B2F"), (143..145).map { sapphire.routeInfo(it)?.first })
        assertEquals(listOf(2), sapphire.trainersOnRoute(143))
        assertEquals(listOf(5, 28, 30, 193), sapphire.trainersOnRoute(145))
    }

    @Test
    fun `Emerald lists its rivals, Space Center and Steven, and names every map`() {
        val t = tracker(GameMap.EMERALD_U)
        assertEquals(listOf(592, 593, 599, 600, 768, 769), t.trainersOnRoute(4))
        assertEquals((661..666).toList(), t.trainersOnRoute(6))
        assertEquals(listOf(520, 523, 526, 529, 532, 535, 36, 481, 293, 336, 703, 702, 736, 735), t.trainersOnRoute(19))
        assertEquals(listOf(335), t.trainersOnRoute(115))
        assertEquals(listOf(586, 22, 587, 116), t.trainersOnRoute(275))
        assertEquals(listOf(588, 589, 590, 734, 514), t.trainersOnRoute(276))
        assertEquals(listOf(804), t.trainersOnRoute(431))
        assertEquals(listOf(717, 716), t.trainersOnRoute(336))
        // Victory Road: the reference stores every trainer on 1F (RouteData.lua:5053).
        assertEquals(17, t.trainersOnRoute(163).size)
        assertEquals(emptyList(), t.trainersOnRoute(285)); assertEquals(emptyList(), t.trainersOnRoute(286))
        assertEquals(listOf("Cave of Origin B1F", "City Space Center 1F", "City Space Center 2F", "Meteor Falls Steven"),
            listOf(162, 275, 276, 431).map { t.routeInfo(it)?.first })
        // The log's route tab starts from every RouteData.Info map, trainers or not.
        assertTrue(162 in t.routeMapIds() && 431 in t.routeMapIds())
        assertNull(t.routeInfo(9999))
    }
}
