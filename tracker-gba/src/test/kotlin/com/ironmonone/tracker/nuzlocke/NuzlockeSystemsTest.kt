package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What changes with the family of games a run is on (2026-09-30): Generation 1 has no genders, the DS games do not say
 * how a battle ended, the Elite Four of some games can be fought in any order, and each family names its species and
 * places its own way.
 */
class NuzlockeSystemsTest {

    private fun dex(name: String, system: NuzlockeSystem = NuzlockeSystem.GEN1): Int =
        assertNotNull(NuzlockeFamilies.speciesId(name, system), "$name is not a species of ${system.key}")

    private fun boss(seq: Int, key: String, kind: String, label: String, cap: Int, ids: List<Int>, badge: Int? = null, group: String = "") =
        BossCap(seq, key, kind, label, cap, "ace", ids, badge = badge, group = group)

    /** A League that can be fought in any order, then a final fight, after two gyms. */
    private fun anyOrderLeague() = LevelCapTable("test", listOf(
        boss(1, "gym1", "gym", "Roark", 14, listOf(246), badge = 0),
        boss(2, "gym2", "gym", "Gardenia", 22, listOf(315), badge = 1),
        boss(3, "e4-1", "e4", "Shauntal", 48, listOf(1), group = "Elite Four, any order"),
        boss(4, "e4-2", "e4", "Marshal", 50, listOf(2), group = "Elite Four, any order"),
        boss(5, "e4-3", "e4", "Grimsley", 49, listOf(3), group = "Elite Four, any order"),
        boss(6, "e4-4", "e4", "Caitlin", 50, listOf(4), group = "Elite Four, any order"),
        boss(7, "champion", "champion", "Iris", 59, listOf(5)),
    ))

    private fun opp(id: Int, key: String?, group: String = "Elite4") = NzOpponent(id, "Trainer $id", group, key, null)

    // ---------------------------------------------------------------- Generation 1 has no genders

    @Test
    fun `Wedlocke on Generation 1 pairs the catches in the order they came, and never skips one for its gender`() {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE), NuzlockeSystem.GEN1)
        s.area = NzArea("Route 1", 12)
        s.starter(mon(1, dex("Charmander"), "CHARMANDER", 5, gender = null, nickname = "Ash", types = listOf(10)))
        // Enemies with no gender at all: on Generation 3 every one of them would be skipped as genderless.
        s.catchIt(foe(100, dex("Pidgey"), "PIDGEY", 3, gender = null), mon(100, dex("Pidgey"), "PIDGEY", 3, gender = null, nickname = "Fly"))
        assertEquals(Outcome.CAUGHT, s.enc("Route 1")!!.outcome)
        val roster = s.ledger.roster
        assertEquals(roster.getValue(1L).partner, 100L)
        assertEquals(roster.getValue(100L).partner, 1L)
        s.moveTo("Route 2", 13)
        s.catchIt(foe(101, dex("Rattata"), "RATTATA", 3, gender = null), mon(101, dex("Rattata"), "RATTATA", 3, gender = null, nickname = "Rat"))
        s.moveTo("Route 3", 14)
        s.catchIt(foe(102, dex("Spearow"), "SPEAROW", 3, gender = null), mon(102, dex("Spearow"), "SPEAROW", 3, gender = null, nickname = "Peck"))
        assertEquals(roster.getValue(101L).partner, 102L, "the third and fourth catches are a pair")
        assertTrue(s.warnings(WarnKind.GENDER).isEmpty())
        // The first of a pair dies: the other waits, and the next catch takes it.
        s.party = s.party.map { if (it.id == 100L) it.copy(hp = 0) else it }
        s.poll()
        assertNull(roster.getValue(1L).partner, "widowed")
        s.moveTo("Route 4", 15)
        s.catchIt(foe(103, dex("Ekans"), "EKANS", 4, gender = null), mon(103, dex("Ekans"), "EKANS", 4, gender = null, nickname = "Snek"))
        assertEquals(1L, roster.getValue(103L).partner, "the widow takes the next catch")
    }

    @Test
    fun `Wedlocke on a game with genders still skips an encounter of the gender the party has more of`() {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE), NuzlockeSystem.GEN2)
        s.starter(mon(1, dex("Cyndaquil", NuzlockeSystem.GEN2), "CYNDAQUIL", 5, gender = Gender.MALE, nickname = "Cyn", types = listOf(10)))
        s.wildEncounter(foe(100, dex("Pidgey", NuzlockeSystem.GEN2), "PIDGEY", 3, gender = Gender.MALE), BattleEnd.WON)
        assertEquals(ExtraKind.GENDER, s.ledger.areas.getValue("Route 3").extras.single().kind)
        s.wildEncounter(foe(101, dex("Rattata", NuzlockeSystem.GEN2), "RATTATA", 3, gender = null), BattleEnd.WON)
        assertEquals(2, s.ledger.areas.getValue("Route 3").extras.size, "genderless is skipped too")
    }

    // ---------------------------------------------------------------- each family's own species numbers

    @Test
    fun `the same number is a different species on Generation 3 and on Generation 5, and each table knows its own lines`() {
        // 277 is Treecko in the Gen 3 numbering and Swellow in the national one.
        assertNotEquals(NuzlockeFamilies.lineOf(277, NuzlockeSystem.GEN3), NuzlockeFamilies.lineOf(277, NuzlockeSystem.GEN5))
        assertTrue(NuzlockeFamilies.sameLine(276, 277, NuzlockeSystem.GEN5), "Taillow and Swellow")
        assertTrue(NuzlockeFamilies.sameLine(277, 279, NuzlockeSystem.GEN3), "Treecko and Sceptile")
        assertTrue(NuzlockeFamilies.sameLine(1, 3, NuzlockeSystem.GEN5))
        assertFalse(NuzlockeFamilies.sameLine(1, 4, NuzlockeSystem.GEN5))
    }

    @Test
    fun `the dupes clause reads the run's own table`() {
        val s = Sim(rules(NuzlockePreset.STANDARD), NuzlockeSystem.GEN1)
        s.starter(mon(1, dex("Squirtle"), "SQUIRTLE", 5, nickname = "Shell", types = listOf(WATER)))
        s.moveTo("Route 1", 12)
        s.catchIt(foe(100, dex("Pidgey"), "PIDGEY", 3), mon(100, dex("Pidgey"), "PIDGEY", 3, nickname = "Fly"))
        s.moveTo("Route 2", 13)
        s.wildEncounter(foe(101, dex("Pidgeotto"), "PIDGEOTTO", 12), BattleEnd.WON)
        assertEquals(listOf(ExtraKind.DUPE), s.ledger.areas.getValue("Route 2").extras.map { it.kind })
        assertNull(s.enc("Route 2"), "a dupe leaves the area open")
    }

    @Test
    fun `the dupes clause of a Generation 5 run counts the species by national numbers`() {
        // 276 and 277 are Taillow and Swellow in the national numbering, and in the Generation 3 numbering 277 is Treecko.
        val s = Sim(rules(NuzlockePreset.STANDARD), NuzlockeSystem.GEN5)
        s.starter(mon(1, 495, "SNIVY", 5, nickname = "Vine", types = listOf(12)))
        s.moveTo("Route 1", 12)
        s.catchIt(foe(100, 276, "TAILLOW", 3), mon(100, 276, "TAILLOW", 3, nickname = "Tail"))
        s.moveTo("Route 2", 13)
        s.wildEncounter(foe(101, 277, "SWELLOW", 12), BattleEnd.WON)
        assertEquals(listOf(ExtraKind.DUPE), s.ledger.areas.getValue("Route 2").extras.map { it.kind }, "Swellow is Taillow's line")
        assertNull(s.enc("Route 2"), "so the route stays open")
        val g3 = Sim(rules(NuzlockePreset.STANDARD))
        g3.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "Shell", types = listOf(WATER)))
        g3.moveTo("Route 1", 12)
        g3.catchIt(foe(100, 276, "X", 3), mon(100, 276, "X", 3, nickname = "Ex"))
        g3.moveTo("Route 2", 13)
        g3.wildEncounter(foe(101, Sp.TREECKO, "TREECKO", 12), BattleEnd.WON)
        assertEquals(Outcome.FAINTED, g3.enc("Route 2")!!.outcome, "the same number is not a dupe on Generation 3")
    }

    // ---------------------------------------------------------------- how a map name becomes an area

    @Test
    fun `a Game Boy place is used as it is, and its floors are apart only when the switch says so`() {
        val merged = NuzlockeRules()
        val apart = NuzlockeRules(floorsMerged = false)
        val moon = NzArea("Mt. Moon", 59, "Mt. Moon B1F")
        assertEquals("Mt. Moon", NuzlockeAreas.of(moon, null, merged, NuzlockeSystem.GEN1).key)
        assertEquals("Mt. Moon B1F", NuzlockeAreas.of(moon, null, apart, NuzlockeSystem.GEN1).key)
        assertEquals("Route 3", NuzlockeAreas.of(NzArea("Route 3", 14, "Route 3"), null, apart, NuzlockeSystem.GEN1).key)
        assertEquals("Union Cave", NuzlockeAreas.of(NzArea("Union Cave", 10), null, apart, NuzlockeSystem.GEN2).key, "a place with no floor detail stays whole")
        // The Safari Zone: four zones, or one.
        val zone = NzArea("Safari Zone East", 217)
        assertEquals("Safari Zone East", NuzlockeAreas.of(zone, null, merged, NuzlockeSystem.GEN1).key)
        assertEquals("Safari Zone", NuzlockeAreas.of(zone, null, merged.copy(safari = SafariRule.ONE_AREA), NuzlockeSystem.GEN1).key)
        // Water: only where the rules say.
        assertEquals("Route 4|water", NuzlockeAreas.of(NzArea("Route 4", 15), Method.SURF, merged.copy(waterSeparate = true), NuzlockeSystem.GEN1).key)
        assertEquals("Route 4", NuzlockeAreas.of(NzArea("Route 4", 15), Method.SURF, merged, NuzlockeSystem.GEN1).key)
    }

    @Test
    fun `a DS place folds the tracker's gym, typos and contest names into places`() {
        val r = NuzlockeRules()
        fun key(n: String, id: Int = 1, rules: NuzlockeRules = r) = NuzlockeAreas.of(NzArea(n, id), null, rules, NuzlockeSystem.GEN4).key
        assertEquals("Hearthome City", key("Hearthome City's gym"))
        assertEquals("Hearthome City", key("Hearthome City"))
        assertEquals("Route 209", key("Route 209"))
        assertEquals("Mt. Mortar", key("Mr. Mortar"), "the reference tracker's typo")
        assertEquals("Rock Peak Ruins", key("Rpck Peak Ruins"))
        assertEquals("Cinnabar Island", key("Cinnarbar Island"))
        assertEquals("Sprout Tower", key("Sprout Tower 2F"))
        for (day in listOf("Tues Bug Catching", "Thurs Bug Catching", "Sat Bug Catching")) assertEquals("Bug-Catching Contest", key(day))
        // The Great Marsh and the Safari Zone: each map is a zone, or the switch makes them one.
        assertEquals("Great Marsh, map 152", key("Great Marsh", 152))
        assertNotEquals(key("Great Marsh", 152), key("Great Marsh", 153))
        assertEquals("Great Marsh", key("Great Marsh", 152, r.copy(safari = SafariRule.ONE_AREA)))
        assertEquals("Safari Zone", key("Safari Zone", 300, r.copy(safari = SafariRule.ONE_AREA)))
        // Floors: one place, or told apart by map when the switch says so.
        assertEquals("Mt. Coronet", key("Mt. Coronet", 200))
        assertEquals("Mt. Coronet, map 200", key("Mt. Coronet", 200, r.copy(floorsMerged = false)))
        assertNotEquals(key("Mt. Coronet", 200, r.copy(floorsMerged = false)), key("Mt. Coronet", 201, r.copy(floorsMerged = false)))
        assertEquals("Route 209", key("Route 209", 200, r.copy(floorsMerged = false)), "a route is not a floor")
    }

    @Test
    fun `a Generation 5 place reads the same way`() {
        fun key(n: String, id: Int = 1, rules: NuzlockeRules = NuzlockeRules()) = NuzlockeAreas.of(NzArea(n, id), null, rules, NuzlockeSystem.GEN5).key
        assertEquals("Nimbasa City", key("Nimbasa City's gym"))
        assertEquals("Pinwheel Exterior", key("Pinwheel Exterior"))
        assertEquals("Twist Mountain", key("Twist Mountain", 7))
        assertEquals("Twist Mountain, map 7", key("Twist Mountain", 7, NuzlockeRules(floorsMerged = false)))
    }

    // ---------------------------------------------------------------- the League in any order

    @Test
    fun `where the Elite Four can be fought in any order, the League's cap applies to whichever comes first`() {
        val s = Sim(rules(NuzlockePreset.HARDCORE), NuzlockeSystem.GEN5)
        s.caps = anyOrderLeague()
        s.badges = 0xFF
        s.starter(mon(1, dex("Squirtle", NuzlockeSystem.GEN5), "SQUIRTLE", 52, nickname = "Shell", types = listOf(WATER)))
        // The third member first: the party has a level 52, over the League's 50.
        s.trainerBattle(opp(3, "e4-3"), foe(900))
        assertEquals(1, s.warnings(WarnKind.CAP).size, "Grimsley first: the cap of the League applies")
        s.finish(BattleEnd.WON)
        assertEquals(setOf("e4-3"), s.ledger.meta.beatenBosses)
        // Then the others, inside the League: no cap.
        s.trainerBattle(opp(1, "e4-1"), foe(900))
        s.finish(BattleEnd.WON)
        s.trainerBattle(opp(2, "e4-2"), foe(900))
        assertEquals(1, s.warnings(WarnKind.CAP).size, "nothing new warned inside the League")
        assertTrue(s.ledger.events.any { it.text.contains("no level cap inside the League") })
    }

    @Test
    fun `in a fixed order only the first member of the League has the cap, as before`() {
        val fixed = LevelCapTable("test", listOf(
            boss(1, "e4-1", "e4", "Sidney", 49, listOf(1)), boss(2, "e4-2", "e4", "Phoebe", 51, listOf(2)),
            boss(3, "champion", "champion", "Steven", 58, listOf(3)),
        ))
        val s = Sim(rules(NuzlockePreset.HARDCORE)); s.caps = fixed
        s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 55, nickname = "Shell", types = listOf(WATER)))
        s.trainerBattle(opp(2, "e4-2"), foe(900))
        assertTrue(s.warnings(WarnKind.CAP).isEmpty(), "the second member is fought inside the League")
        s.finish(BattleEnd.WON)
        s.trainerBattle(opp(1, "e4-1"), foe(900))
        assertEquals(1, s.warnings(WarnKind.CAP).size, "the first member has the League's cap, 51")
    }

    // ---------------------------------------------------------------- which fights were won

    @Test
    fun `a boss won is remembered, and a DS trainer battle that ends with a Pokemon standing is a win`() {
        val s = Sim(rules(NuzlockePreset.STANDARD), NuzlockeSystem.GEN4)
        s.caps = anyOrderLeague()
        s.starter()
        s.trainerBattle(opp(246, "gym1", "Gym"), foe(900))
        s.finish(BattleEnd.UNKNOWN)
        assertEquals(setOf("gym1"), s.ledger.meta.beatenBosses, "the game did not say, and the party was standing")
        // The same on a game that says how it ended: only a win counts.
        val g3 = Sim(rules(NuzlockePreset.STANDARD)); g3.caps = anyOrderLeague(); g3.starter()
        g3.trainerBattle(opp(246, "gym1", "Gym"), foe(900)); g3.finish(BattleEnd.UNKNOWN)
        assertTrue(g3.ledger.meta.beatenBosses.isEmpty(), "Generation 3 does not guess")
        g3.trainerBattle(opp(315, "gym2", "Gym"), foe(900)); g3.finish(BattleEnd.WON)
        assertEquals(setOf("gym2"), g3.ledger.meta.beatenBosses)
    }

    @Test
    fun `a DS battle that ended with the whole party down is not a win`() {
        val s = Sim(rules(NuzlockePreset.STANDARD.let { NuzlockePreset.STANDARD }) { it.copy(whiteoutEndsRun = false) }, NuzlockeSystem.GEN5)
        s.caps = anyOrderLeague()
        s.starter()
        s.trainerBattle(opp(246, "gym1", "Gym"), foe(900))
        s.party = s.party.map { it.copy(hp = 0) }
        s.finish(BattleEnd.UNKNOWN)
        assertTrue(s.ledger.meta.beatenBosses.isEmpty())
    }

    @Test
    fun `the Champion is never worked out on a DS game, only the tracker's own word completes the run`() {
        val s = Sim(rules(NuzlockePreset.STANDARD), NuzlockeSystem.GEN4)
        s.caps = anyOrderLeague()
        s.starter()
        s.trainerBattle(opp(5, "champion"), foe(900))
        s.finish(BattleEnd.UNKNOWN)
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status, "a state loaded in the middle of the fight is not a win")
        s.trainerBattle(opp(5, "champion"), foe(900))
        s.finish(BattleEnd.WON)
        assertEquals(RunStatus.COMPLETE, s.ledger.meta.status)
    }

    // ---------------------------------------------------------------- extra cap points

    @Test
    fun `a rival or a team boss in the table has a cap only when the rules ask for it`() {
        val table = LevelCapTable("test", listOf(
            boss(1, "gym1", "gym", "Roark", 14, listOf(246), badge = 0),
            boss(2, "barry1", "rival", "Barry", 9, listOf(247, 248, 249)),
            boss(3, "mars1", "boss", "Mars", 16, listOf(295)),
        ))
        fun run(capExtra: Boolean): Sim {
            val s = Sim(rules(NuzlockePreset.HARDCORE) { it.copy(capExtraBosses = capExtra) }, NuzlockeSystem.GEN4)
            s.caps = table
            s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 12, nickname = "Shell", types = listOf(WATER)))
            s.trainerBattle(opp(247, "barry1", "Rival"), foe(900))
            return s
        }
        assertEquals(1, run(true).warnings(WarnKind.CAP).size, "12 is over Barry's 9")
        assertTrue(run(false).warnings(WarnKind.CAP).isEmpty())
        assertTrue(run(false).ledger.events.none { it.text.contains("no level cap inside the League") }, "an extra is not the League")
        // The next boss on the ladder is never an extra.
        assertEquals("gym1", table.next(emptySet())!!.key)
        assertNull(table.next(setOf("gym1")), "only the extras are left, and they are not on the ladder")
    }

    // ---------------------------------------------------------------- the panel's cap line

    private fun capLineOf(s: Sim) = assertNotNull(NuzlockeView.capLine(s.ledger, s.snapshot())).text

    @Test
    fun `the cap line names an any-order League without naming a member`() {
        val s = Sim(rules(NuzlockePreset.HARDCORE), NuzlockeSystem.GEN5).starter()
        s.caps = anyOrderLeague(); s.beaten = setOf("gym1", "gym2")
        s.poll()
        assertEquals("Cap Lv 50: Elite Four, any order (standard table)", capLineOf(s))
        s.ledger.meta.beatenBosses += "e4-2"
        assertEquals("No level cap inside the League", capLineOf(s))
    }

    @Test
    fun `the cap line gives a range while gyms may come in any order, and the one leader when one is left`() {
        val kanto = LevelCapTable("test", listOf(
            boss(1, "champion", "champion", "Lance", 50, listOf(1)),
            boss(2, "brock", "post", "Brock", 54, listOf(2), badge = 8, group = "Kanto gyms"),
            boss(3, "misty", "post", "Misty", 47, listOf(3), badge = 9, group = "Kanto gyms"),
            boss(4, "blue", "post", "Blue", 60, listOf(4), badge = 15, group = "Blue"),
        ))
        val s = Sim(rules(NuzlockePreset.HARDCORE), NuzlockeSystem.GEN2).starter()
        s.caps = kanto; s.beaten = setOf("champion")
        s.poll()
        assertEquals("Cap Lv 47 to 54: Kanto gyms, the one you fight (standard table)", capLineOf(s))
        s.beaten = setOf("champion", "misty")
        assertEquals("Cap Lv 54: Kanto gyms, Brock (standard table)", capLineOf(s))
        s.beaten = setOf("champion", "misty", "brock")
        assertEquals("Cap Lv 60: Blue (standard table)", capLineOf(s))
        assertEquals("Kanto gyms", kanto.byKey("brock")!!.place)
    }

    // ---------------------------------------------------------------- the file

    @Test
    fun `a run's family, game and the bosses it saw beaten come back out of its file`() {
        val s = Sim(rules(NuzlockePreset.STANDARD), NuzlockeSystem.GEN2).starter()
        s.ledger.meta.gameKey = "crystal"
        s.ledger.meta.beatenBosses += listOf("gym1", "e4-1")
        val text = NuzlockeText.format(s.ledger)
        val back = assertNotNull(NuzlockeText.parse(text))
        assertEquals(NuzlockeSystem.GEN2, back.meta.system)
        assertEquals("crystal", back.meta.gameKey)
        assertEquals(setOf("gym1", "e4-1"), back.meta.beatenBosses)
        assertEquals(text, NuzlockeText.format(back))
    }

    @Test
    fun `a run that has not begun says what it waits for, balls where the game can be asked and the first Pokemon where it cannot`() {
        for ((system, wanted) in listOf(
            NuzlockeSystem.GEN1 to "Poke Balls", NuzlockeSystem.GEN2 to "Poke Balls", NuzlockeSystem.GEN3 to "Poke Balls",
            NuzlockeSystem.GEN4 to "first Pokemon", NuzlockeSystem.GEN5 to "first Pokemon",
        )) {
            val lines = NuzlockeView.panel(Sim(rules(NuzlockePreset.STANDARD), system).ledger, null).lines.map { it.text }
            assertTrue(lines.any { wanted in it && "have not begun" in it }, "$system: $lines")
        }
        assertTrue(NuzlockeSystem.GEN1.readsBalls && NuzlockeSystem.GEN2.readsBalls && NuzlockeSystem.GEN3.readsBalls)
        assertFalse(NuzlockeSystem.GEN4.readsBalls || NuzlockeSystem.GEN5.readsBalls)
    }

    @Test
    fun `a ledger from before the other games existed is a Generation 3 run`() {
        val s = Sim().starter()
        val text = NuzlockeText.format(s.ledger).lines().filterNot { it.startsWith("system\t") || it.startsWith("gamekey\t") || it.startsWith("beaten\t") }.joinToString("\n")
        val back = assertNotNull(NuzlockeText.parse(text))
        assertEquals(NuzlockeSystem.GEN3, back.meta.system)
        assertEquals("", back.meta.gameKey)
        assertEquals(NuzlockeSystem.GEN3, NuzlockeSystem.byKey("nonsense"))
        assertEquals(NuzlockeSystem.GEN5, NuzlockeSystem.byKey("gen5"))
    }

    @Test
    fun `the header of a Game Boy run's file still reads from its first lines`() {
        val s = Sim(rules(NuzlockePreset.WEDLOCKE), NuzlockeSystem.GEN1).starter()
        s.ledger.meta.gameKey = "yellow"
        val h = assertNotNull(NuzlockeText.header(NuzlockeText.format(s.ledger).lineSequence().take(40)))
        assertEquals(NuzlockePreset.WEDLOCKE, h.preset)
        assertEquals("nz-test", h.id)
    }

    // ---------------------------------------------------------------- the words on the rules page

    @Test
    fun `every Game Boy and DS game says what is read and what is by hand, and Generation 3 says nothing new`() {
        for ((system, game) in listOf(
            NuzlockeSystem.GEN1 to "red", NuzlockeSystem.GEN1 to "yellow", NuzlockeSystem.GEN2 to "gold", NuzlockeSystem.GEN2 to "crystal",
            NuzlockeSystem.GEN4 to "diamond", NuzlockeSystem.GEN4 to "platinum", NuzlockeSystem.GEN4 to "heartgold",
            NuzlockeSystem.GEN5 to "black", NuzlockeSystem.GEN5 to "white2",
        )) {
            val n = NuzlockeNotes.forGame(system, game)
            assertTrue(n.automatic.size >= 5 && n.byHand.size >= 5, "$system $game")
            for (t in n.automatic + n.byHand) {
                assertFalse('\u2014' in t || '\u2013' in t, "no dashes in what a player reads: $t")
                assertTrue(t.length in 30..400, t)
            }
        }
        assertTrue(NuzlockeNotes.forGame(NuzlockeSystem.GEN3, "").isEmpty)
        // The fallbacks the task names are each said, in the words of that game.
        val gen1 = NuzlockeNotes.forGame(NuzlockeSystem.GEN1, "red").byHand.joinToString(" ")
        assertTrue("no genders" in gen1 && "order of the catches" in gen1)
        val ds = NuzlockeNotes.forGame(NuzlockeSystem.GEN4, "platinum").byHand.joinToString(" ")
        assertTrue("Poke Ball count is not read" in ds && "unknown" in ds && "Set reminder" in ds)
        assertTrue("open the run again" in NuzlockeNotes.forGame(NuzlockeSystem.GEN2, "crystal").byHand.joinToString(" "))
        assertTrue("Challenge Mode" in NuzlockeNotes.forGame(NuzlockeSystem.GEN5, "black2").byHand.joinToString(" "))
    }
}
