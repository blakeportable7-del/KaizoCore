package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** How the tracker's map names become the areas a Nuzlocke counts (2026-09-29). */
class NuzlockeAreasTest {

    private val standard = NuzlockeRules()

    private fun key(name: String, rules: NuzlockeRules = standard, method: Method? = null) =
        NuzlockeAreas.of(NzArea(name, 1), method, rules).key

    /** Every map name of one game's route data, as the tracker reports it. */
    private fun names(version: String): List<String> =
        NuzlockeAreasTest::class.java.getResourceAsStream("/gen3/routeinfo-$version.tsv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { !it.startsWith("#") }.mapNotNull { it.split('\t').getOrNull(2)?.takeIf { n -> n.isNotBlank() } }.toList()
        }

    @Test
    fun `every floor and side map of a cave or tower is one place`() {
        for (name in listOf("Mt. Moon 1F", "Mt. Moon B1F", "Mt. Moon B2F")) assertEquals("Mt. Moon", key(name))
        for (name in listOf("Rock Tunnel 1F", "Rock Tunnel B1F")) assertEquals("Rock Tunnel", key(name))
        for (name in listOf("Seafoam Islands 1F", "Seafoam Islands B4F")) assertEquals("Seafoam Islands", key(name))
        for (name in listOf("Victory Road 1F", "Victory Road 3F", "Victory Road B1F", "Victory Road B2F")) assertEquals("Victory Road", key(name))
        for (name in listOf("Meteor Falls 1F 1R", "Meteor Falls 1F 2R", "Meteor Falls B1F 2R", "Meteor Falls Steven")) assertEquals("Meteor Falls", key(name))
        for (name in listOf("Seafloor Cavern U.", "Seafloor Cavern", "Seafloor Cavern 1", "Seafloor Cavern 9")) assertEquals("Seafloor Cavern", key(name))
        for (name in listOf("Shoal Cave Lo-1", "Shoal Cave Lo-4", "Shoal Cave Hi-2")) assertEquals("Shoal Cave", key(name))
        for (name in listOf("Abandoned Ship Deck", "Abandoned Ship 1F", "Abandoned Ship Uw1", "Abandoned Ship Cpt")) assertEquals("Abandoned Ship", key(name))
        for (name in listOf("Mt. Pyre 1F", "Mt. Pyre 6F", "Mt. Pyre Ext.", "Mt. Pyre Summit")) assertEquals("Mt. Pyre", key(name))
        for (name in listOf("Mt. Ember Base", "Mt. Ember Summit", "Summit Path 2F", "Ruby Path B1F- a")) assertEquals("Mt. Ember", key(name))
        for (name in listOf("Lost Cave Room 1", "Lost Cave Room 14")) assertEquals("Lost Cave", key(name))
        for (name in listOf("Icefall Cave Entrance", "Icefall Cave 1F", "Icefall Cave Back")) assertEquals("Icefall Cave", key(name))
    }

    @Test
    fun `a route is one place whatever side or water map the game is on`() {
        assertEquals("Route 21", key("Route 21 North")); assertEquals("Route 21", key("Route 21 South"))
        assertEquals("Route 126", key("Route 126 Water")); assertEquals("Route 124", key("Route 124 Water"))
        assertEquals("Route 3", key("Route 3"))
        // A route with a different number is a different place.
        assertNotEquals(key("Route 12"), key("Route 120"))
    }

    @Test
    fun `towns, cities and the places with a name of their own are left alone`() {
        for (name in listOf("Pallet Town", "Viridian Forest", "Safari Zone Center", "Cerulean City", "Berry Forest", "Kindle Road", "Petalburg Woods", "Fiery Path", "Jagged Pass"))
            assertEquals(name, key(name))
    }

    @Test
    fun `with floors kept apart each floor is its own area but a route is still a route`() {
        val apart = standard.copy(floorsMerged = false)
        assertEquals("Mt. Moon 1F", key("Mt. Moon 1F", apart))
        assertEquals("Mt. Moon B2F", key("Mt. Moon B2F", apart))
        assertNotEquals(key("Mt. Moon 1F", apart), key("Mt. Moon B1F", apart))
        assertEquals("Route 21", key("Route 21 North", apart))
    }

    @Test
    fun `the Safari Zone is one area or one per zone`() {
        val zones = standard
        val one = standard.copy(safari = SafariRule.ONE_AREA)
        for (name in listOf("Safari Zone NW.", "Safari Zone NE.", "Safari Zone SE.", "Safari Zone N-Ext.", "Safari Zone Center", "Safari Zone West")) {
            assertEquals("Safari Zone", key(name, one), name)
            assertEquals(name, key(name, zones), name)
        }
        assertNotEquals(key("Safari Zone NW.", zones), key("Safari Zone NE.", zones))
    }

    @Test
    fun `water is its own area only when the rules say so`() {
        val split = standard.copy(waterSeparate = true)
        assertEquals("Route 3", key("Route 3", standard, Method.SURF))
        assertEquals("Route 3|water", key("Route 3", split, Method.SURF))
        assertEquals("Route 3|water", key("Route 3", split, Method.ROD))
        assertEquals("Route 3|water", key("Route 3", split, Method.UNDERWATER))
        assertEquals("Route 3", key("Route 3", split, Method.WALK))
        assertEquals("Route 3", key("Route 3", split, Method.ROCK_SMASH))
        assertEquals("Route 3", key("Route 3", split, null))
        assertEquals("Route 3 (water)", NuzlockeAreas.of(NzArea("Route 3", 1), Method.SURF, split).name)
    }

    @Test
    fun `a map the route data does not name still gets a stable key`() {
        assertEquals(AreaKey("map#342", "Map 342"), NuzlockeAreas.of(NzArea(null, 342), null, standard))
        assertEquals(AreaKey("unknown", "Unknown place"), NuzlockeAreas.of(NzArea(null, null), null, standard))
        assertEquals(AreaKey("unknown", "Unknown place"), NuzlockeAreas.of(NzArea(null, null), Method.SURF, standard.copy(waterSeparate = true)))
        assertEquals("map#5", NuzlockeAreas.of(NzArea("  ", 5), null, standard).key)
    }

    @Test
    fun `the real map names of all five games fold into places with no floor left over`() {
        for (v in listOf("firered", "leafgreen", "ruby", "sapphire", "emerald")) {
            val all = names(v)
            assertTrue(all.size > 150, "$v names")
            val keys = all.map { key(it) }
            // No place name ends in a floor marker or a bare number once merged, except a route's own number.
            for (k in keys.toSet()) {
                assertTrue(!Regex("\\s+B?\\d+F$").containsMatchIn(k), "$v: $k still carries a floor")
                assertTrue(!k.startsWith("Route") || Regex("^Route \\d+$").matches(k), "$v: $k")
            }
            // Folding really merges: fewer places than maps.
            assertTrue(keys.toSet().size < all.toSet().size, v)
        }
    }

    @Test
    fun `the places that have wild Pokemon on real cave floors come out as the caves they are`() {
        val fr = names("firered").map { key(it) }.toSet()
        for (cave in listOf("Mt. Moon", "Rock Tunnel", "Seafoam Islands", "Victory Road", "Cerulean Cave", "Diglett's Cave", "Power Plant", "Pokémon Tower", "Viridian Forest"))
            assertTrue(cave in fr, "FireRed: $cave missing from $fr")
        assertTrue("Mt. Moon 1F" !in fr && "Seafoam Islands B1F" !in fr)
        val em = names("emerald").map { key(it) }.toSet()
        for (cave in listOf("Granite Cave", "Meteor Falls", "Shoal Cave", "Mt. Pyre", "Seafloor Cavern", "Victory Road", "Petalburg Woods", "Rusturf Tunnel", "Route 126"))
            assertTrue(cave in em, "Emerald: $cave missing")
        assertTrue("Route 126 Water" !in em)
    }
}
