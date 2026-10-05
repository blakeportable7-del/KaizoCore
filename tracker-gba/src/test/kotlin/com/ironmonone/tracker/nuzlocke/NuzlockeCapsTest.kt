package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The level cap table against the research (docs/research/nuzlocke-gen1-3.md sections 6.1, 7.1 and 8.1), and how the
 * engine and the panel use it (2026-09-29). The numbers below are typed from those tables, not read back from the
 * data file, so a slip in either place shows.
 */
class NuzlockeCapsTest {

    private fun caps(game: String) = LevelCapTable.standard(game)
    private fun capsOf(game: String, kind: String) = caps(game).bosses.filter { it.kind == kind }.map { it.cap }

    @Test
    fun `FireRed and LeafGreen match the research`() {
        assertEquals(listOf(14, 21, 24, 29, 43, 43, 47, 50), capsOf("frlg", "gym"))
        assertEquals(listOf(54, 56, 58, 60), capsOf("frlg", "e4"))
        assertEquals(listOf(63), capsOf("frlg", "champion"))
        assertEquals(listOf("Brock", "Misty", "Lt. Surge", "Erika", "Koga", "Sabrina", "Blaine", "Giovanni"),
            caps("frlg").bosses.filter { it.kind == "gym" }.map { it.label })
        assertEquals(listOf("Lorelei", "Bruno", "Agatha", "Lance"), caps("frlg").bosses.filter { it.kind == "e4" }.map { it.label })
    }

    @Test
    fun `Emerald matches the research`() {
        assertEquals(listOf(15, 19, 24, 29, 31, 33, 42, 46), capsOf("e", "gym"))
        assertEquals(listOf(49, 51, 53, 55), capsOf("e", "e4"))
        assertEquals(listOf(58), capsOf("e", "champion"))
        assertEquals(listOf(78), capsOf("e", "post"))
        assertEquals("Juan", caps("e").byKey("gym8")!!.label)
        assertEquals("Wallace", caps("e").byKey("champion")!!.label)
        assertEquals("Steven", caps("e").byKey("steven")!!.label)
    }

    @Test
    fun `Ruby and Sapphire match the research and differ from Emerald where the games differ`() {
        assertEquals(listOf(15, 18, 23, 28, 31, 33, 42, 43), capsOf("rs", "gym"))
        assertEquals(listOf(49, 51, 53, 55), capsOf("rs", "e4"))
        assertEquals(listOf(58), capsOf("rs", "champion"))
        assertEquals("Wallace", caps("rs").byKey("gym8")!!.label)
        assertEquals("Steven", caps("rs").byKey("champion")!!.label)
        // Brawly, Wattson and Flannery are a level higher in Emerald.
        for (i in listOf(1, 2, 3)) assertEquals(caps("rs").byKey("gym${i + 1}")!!.cap + 1, caps("e").byKey("gym${i + 1}")!!.cap)
        assertNull(caps("rs").byKey("steven"), "Ruby and Sapphire have no Steven fight after the Hall of Fame")
    }

    @Test
    fun `the bosses come in the order they are met, every id is one trainer, and the data names its source`() {
        for (game in listOf("rs", "e", "frlg", "hns")) {
            val t = caps(game)
            assertEquals(t.bosses.indices.map { it + 1 }, t.bosses.map { it.seq }, "$game order")
            assertEquals(t.bosses.map { it.key }.toSet().size, t.bosses.size, "$game keys")
            val ids = t.bosses.flatMap { it.trainerIds }
            assertEquals(ids.toSet().size, ids.size, "$game trainer ids repeat")
            assertTrue(t.bosses.all { it.trainerIds.isNotEmpty() && it.ace.isNotBlank() && it.label.isNotBlank() })
            assertTrue(t.bosses.filter { it.kind == "gym" }.map { it.seq } == (1..8).toList(), "$game gyms are badges 1 to 8")
        }
        val text = LevelCapTable.readResource()
        assertTrue("docs/research/nuzlocke-gen1-3.md" in text && "pret" in text && "Bulbapedia" in text, "the data file must name where the numbers came from")
        assertEquals(4, LevelCapTable.parse(text).map { it.first }.toSet().size)
    }

    @Test
    fun `Heart and Soul has its sixteen gyms on their own badges, the League, and Red after it`() {
        val t = caps("hns")
        assertEquals(listOf("Falkner", "Bugsy", "Whitney", "Morty", "Chuck", "Jasmine", "Pryce", "Clair"),
            t.bosses.filter { it.kind == "gym" }.map { it.label })
        assertEquals((0..7).toList(), t.bosses.filter { it.kind == "gym" }.map { it.badge })
        val kanto = t.bosses.filter { it.group == "Kanto gyms" }
        assertEquals((8..15).toList(), kanto.map { it.badge }, "FLAG_BADGE09..16: Pewter, Cerulean, Vermilion, Celadon, Saffron, Fuchsia, Seafoam, Viridian")
        assertEquals(listOf("Brock", "Misty", "Lt. Surge", "Erika", "Sabrina", "Janine", "Blaine", "Blue"), kanto.map { it.label })
        assertEquals("Lance", t.byKey("champion")!!.label)
        assertEquals(listOf(441), t.byKey("champion")!!.trainerIds, "TRAINER_LANCE_1_HNS")
        // The three first-run teams of each of the order-dependent leaders.
        assertEquals(listOf(418, 420, 421), t.byKey("gym5")!!.trainerIds)
        assertEquals(93, t.byKey("red")!!.cap)
    }

    @Test
    fun `a game is found from the tracker's route version`() {
        assertEquals("rs", LevelCapTable.gameKey("ruby")); assertEquals("rs", LevelCapTable.gameKey("sapphire"))
        assertEquals("e", LevelCapTable.gameKey("emerald"))
        assertEquals("frlg", LevelCapTable.gameKey("firered")); assertEquals("frlg", LevelCapTable.gameKey("leafgreen"))
        assertNull(LevelCapTable.gameKey(""))
        assertTrue(LevelCapTable.standard("nope").bosses.isEmpty())
    }

    @Test
    fun `the next boss is the first one not beaten, and only the first Elite Four fight has the League cap`() {
        val t = caps("frlg")
        assertEquals("gym1", t.next(emptySet())!!.key)
        assertEquals("gym3", t.next(setOf("gym1", "gym2"))!!.key)
        // Badges out of order: the lowest gym still standing.
        assertEquals("gym2", t.next(setOf("gym1", "gym3", "gym4"))!!.key)
        assertEquals("champion", t.next((1..8).map { "gym$it" }.toSet() + setOf("e4-1", "e4-2", "e4-3", "e4-4"))!!.key)
        assertNull(t.next(t.bosses.map { it.key }.toSet()))
        assertEquals(14, t.capAtStart("gym1"))
        assertEquals(60, t.capAtStart("e4-1"), "entering the League: the last member's highest level")
        assertNull(t.capAtStart("e4-2")); assertNull(t.capAtStart("e4-4")); assertNull(t.capAtStart("champion"))
        assertNull(t.capAtStart("nobody")); assertNull(t.capAtStart(null))
        assertEquals(60, t.leagueCap)
        assertEquals(55, caps("e").leagueCap)
    }

    @Test
    fun `trainer ids find their boss`() {
        assertEquals("gym1", caps("frlg").keyOfTrainer(414))
        assertEquals("champion", caps("frlg").keyOfTrainer(439))
        assertEquals("e4-4", caps("e").keyOfTrainer(264))
        assertNull(caps("e").keyOfTrainer(9999))
    }

    @Test
    fun `levels read from the game replace the table's and are marked as read`() {
        val t = caps("frlg").withRomLevels(mapOf("gym1" to 22, "gym2" to 30))
        assertEquals(22, t.byKey("gym1")!!.cap); assertTrue(t.byKey("gym1")!!.fromRom)
        assertEquals(21 + 9, t.byKey("gym2")!!.cap)
        assertEquals(24, t.byKey("gym3")!!.cap); assertTrue(!t.byKey("gym3")!!.fromRom)
        assertTrue(t.mixed && !t.fromRom)
        val all = caps("frlg").let { c -> c.withRomLevels(c.bosses.associate { it.key to it.cap }) }
        assertTrue(all.fromRom && !all.mixed)
        assertTrue(!caps("frlg").fromRom && !caps("frlg").mixed)
    }

    @Test
    fun `a line of the data file that does not parse is skipped`() {
        val rows = LevelCapTable.parse("# c\n\nrs\t1\tgym1\tgym\tRoxanne\t15\tNosepass\t265\nrs\tx\tgym2\tgym\tBrawly\t18\tMakuhita\t266\nrs\t3\tgym3\tgym\tWattson\t0\tMagneton\t267\nrs\t4\n")
        assertEquals(listOf("gym1"), rows.map { it.second.key })
    }

    @Test
    fun `the panel names the next boss, its cap and where the number came from`() {
        val s = Sim(rules(NuzlockePreset.HARDCORE)).starter()
        s.caps = caps("frlg"); s.beaten = setOf("gym1")
        s.poll()
        val line = assertNotNull(NuzlockeView.capLine(s.ledger, s.snapshot()))
        assertEquals("Cap Lv 21: Gym 2, Misty (standard table)", line.text)
        s.caps = caps("frlg").let { c -> c.withRomLevels(c.bosses.associate { it.key to it.cap + 5 }) }
        assertEquals("Cap Lv 26: Gym 2, Misty (from the game)", NuzlockeView.capLine(s.ledger, s.snapshot())!!.text)
        s.beaten = (1..8).map { "gym$it" }.toSet()
        assertEquals("Cap Lv 65: Elite Four 1, Lorelei (from the game)", NuzlockeView.capLine(s.ledger, s.snapshot())!!.text)
        s.beaten = s.beaten + setOf("e4-1")
        assertEquals("No level cap inside the League", NuzlockeView.capLine(s.ledger, s.snapshot())!!.text)
        s.beaten = s.caps!!.bosses.map { it.key }.toSet()
        assertEquals("Level cap: no bosses left", NuzlockeView.capLine(s.ledger, s.snapshot())!!.text)
        s.caps = null
        assertEquals("Level cap: no table for this game", NuzlockeView.capLine(s.ledger, s.snapshot())!!.text)
        assertNull(NuzlockeView.capLine(Sim().starter().ledger, s.snapshot()), "no line when the rules have no caps")
    }
}
