package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.gachamon.GachaMonCodec
import com.ironmonone.tracker.gachamon.GachaMonPrize
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GachaMon in Play, read by read (GachaMon.observeGame): the lead becoming a card, the files it is kept in and read back
 * from, a new run moving the kept cards into the collection, the trainer battles and badges that mark cards, the heals
 * box's stars, the carousel's rules, and the game over's prize card. The game is a stand-in with Blake's Charizard
 * (FakeGame); the rating itself is checked against the PC tracker's code in tracker-gba (GachaMonReferenceTest).
 */
class GachaMonPlayTest {
    private lateinit var dir: File
    /** Never shiny by chance: math.random answering just under 1. */
    private val noLuck = object : Random() { override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount) }

    @BeforeTest
    fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("gachamon").toFile()
        GachaMon.reset()
        GachaMonOptions.load("ruleset=AutoDetect\naddIfNew=false\naddAfterTrainers=true\nprizeCards=true\nshowStars=true\nshowPack=false\nanimatePack=true\n")
        GachaMon.ensureLoaded(dir)
        GachaMon.random = noLuck
    }

    @AfterTest
    fun tearDown() {
        DiskWriter.drain()
        GachaMon.reset()
        dir.deleteRecursively()
    }

    private class FakeGame(private val routeTrainers: Map<Int, List<GachaMonPrize.TrainerMon>> = emptyMap(), private val beaten: Set<Int> = emptySet()) : GachaMonGame {
        override val expandedSpeciesIds = false
        override val nameSet = ""
        override val lastMoveId = 354
        // Charizard as Blake's seed had it: randomized base stats, Fire/Flying, Blaze or Compoundeyes; a Charmander.
        private val bases = mapOf(
            6 to BaseStats(hp = 20, atk = 140, def = 45, spe = 35, spAtk = 125, spDef = 155, type1 = 10, type2 = 2, ability1 = 66, ability2 = 14, genderRatio = 31),
            4 to BaseStats(hp = 39, atk = 52, def = 43, spe = 65, spAtk = 60, spDef = 50, type1 = 10, type2 = 10, ability1 = 66, ability2 = 0, genderRatio = 31),
            95 to BaseStats(hp = 35, atk = 45, def = 160, spe = 70, spAtk = 30, spDef = 45, type1 = 5, type2 = 4, ability1 = 69, ability2 = 5, genderRatio = 127),
            121 to BaseStats(hp = 60, atk = 75, def = 85, spe = 115, spAtk = 100, spDef = 85, type1 = 11, type2 = 14, ability1 = 35, ability2 = 30, genderRatio = 255),
        )
        private val moves = mapOf(
            152 to MoveRow(152, "Crabhammer", 10, 10, 90, 85, 11, "SPE"),
            155 to MoveRow(155, "Bonemerang", 10, 10, 50, 90, 4, "PHY"),
            284 to MoveRow(284, "Eruption", 5, 5, 150, 100, 10, "SPE"),
            337 to MoveRow(337, "Dragon Claw", 15, 15, 80, 100, 16, "SPE"),
            10 to MoveRow(10, "Scratch", 35, 35, 40, 100, 0, "PHY"),
            52 to MoveRow(52, "Ember", 25, 25, 40, 100, 10, "SPE"),
            33 to MoveRow(33, "Tackle", 35, 35, 35, 95, 0, "PHY"),
            55 to MoveRow(55, "Water Gun", 25, 25, 40, 100, 11, "SPE"),
            61 to MoveRow(61, "BubbleBeam", 20, 20, 65, 100, 11, "SPE"),
        )
        private val names = mapOf(4 to "Charmander", 6 to "Charizard", 95 to "Onix", 121 to "Starmie")
        override fun baseStats(species: Int) = bases[species]
        override fun moveRowFor(id: Int) = moves[id]
        override fun evolution(species: Int) = if (species == 4) "16" else null
        override fun speciesName(species: Int) = names[species] ?: "#$species"
        override fun abilityName(id: Int) = mapOf(14 to "Compoundeyes", 66 to "Blaze", 69 to "Rock Head", 35 to "Illuminate")[id] ?: "#$id"
        override fun moveName(id: Int) = moves[id]?.name ?: "#$id"
        override fun rawMapId(mapId: Int) = mapId
        override fun trainerParty(trainerId: Int) = routeTrainers[trainerId].orEmpty()
        override fun trainerTitle(trainerId: Int) = mapOf(414 to "Leader Brock", 415 to "Leader Misty")[trainerId]
        override fun trainerDefeated(trainerId: Int) = trainerId in beaten
        override fun learnset(species: Int) = listOf(1 to 33, 7 to 52, 13 to 10)
        override fun speciesExists(species: Int) = species in bases
        override fun routeNameOfTrainer(trainerId: Int) = mapOf(414 to "Pewter Gym", 415 to "Cerulean Gym")[trainerId]
    }

    private fun mon(species: Int = 6, pid: Long = 0x12345678L, level: Int = 5, moves: List<Int> = listOf(152, 155, 284, 337),
                    hp: Int = 17, curHp: Int = hp, slot: Int = 1) = TrackedMon(
        mon = PokemonDecoder.Mon(pid = pid, level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = moves, pp = moves.map { 10 }, ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = slot, nature = 8, shiny = false, status = 0, curHp = curHp, maxHp = hp,
            atk = 20, def = 11, spe = 9, spAtk = 16, spDef = 21),
        speciesName = mapOf(4 to "Charmander", 6 to "Charizard")[species] ?: "#$species", moveNames = emptyList(), base = null,
    )

    private fun state(lead: TrackedMon?, inBattle: Boolean = false, wild: Boolean = false, badges: Int = 0, mapId: Int? = 3,
                      route: String = "emerald", badgeSet: String = "RSE") = TrackerState(
        partyCount = if (lead == null) 0 else 1, party = listOfNotNull(lead), inBattle = inBattle, isWildBattle = wild,
        badges = badges, mapId = mapId, routeVersion = route, badgeSet = badgeSet,
    )

    private val run = "emerald-u 1220 0123"

    @Test
    fun `Blake's Charizard leads, and becomes his card`() {
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)
        val e = assertNotNull(GachaMon.recent.singleOrNull())
        assertEquals(69, e.card.ratingScore)
        assertEquals(5, e.stars)
        assertEquals(8000, e.card.battlePower)
        assertEquals(2, e.card.version)
        assertEquals(2, e.card.gameVersion)        // Emerald
        assertEquals(1220, e.card.seedNumber)      // the attempt
        assertEquals(14, e.card.abilityId)         // its slot's ability: Compoundeyes
        assertEquals(1, e.card.gender)             // male
        assertEquals(1, e.card.isShiny)            // five stars is always shiny...
        assertEquals(1, e.card.keep)               // ...and kept
        assertEquals("69 points (5 stars)", gachaRatingLine(e.card))
        assertEquals("Compoundeyes", e.notes.ability)
        assertEquals(listOf("Crabhammer", "Bonemerang", "Eruption", "Dragon Claw"), e.notes.moves)
        // MoveData's text for Eruption, a variable-power move, is ">HP" on PC whatever the ROM holds.
        assertEquals(listOf("90", "50", ">HP", "80"), e.notes.powers)
        assertEquals(e.uid, GachaMon.newest?.uid)
        assertTrue(6 in GachaMon.seen(""))
        // The same Pokemon leading again is the same card; evolved, it is a new one (pidIndex is personality + species).
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)
        assertEquals(1, GachaMon.recent.size)
        GachaMon.observeGame(dir, run, 1220, state(mon(species = 4, moves = listOf(10, 52))), game)
        assertEquals(2, GachaMon.recent.size)
    }

    @Test
    fun `cards are kept in files and read back whole`() {
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)
        val made = GachaMon.recent.single()
        DiskWriter.drain()
        val line = File(dir, "prep/gachamon/recent.txt").readLines()
        assertEquals("run $run", line[0])
        assertTrue(line[1].startsWith(GachaMonCodec.shareCode(made.card) + "\t"), line[1])
        GachaMon.reset()
        GachaMon.ensureLoaded(dir)
        val back = GachaMon.recent.single()
        assertEquals(made.card, back.card)
        assertEquals(made.notes, back.notes)
        assertTrue(6 in GachaMon.seen(""))
        // A line is the PC tracker's code, then the notes: GachaMonEntry.parse reads its own line back.
        assertEquals(made.card, GachaMonEntry.parse(made.line())?.card)
        assertEquals(made.notes, GachaMonEntry.parse(made.line())?.notes)
    }

    @Test
    fun `a new run moves the kept captures into the collection and starts empty`() {
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)                                       // five stars: kept
        GachaMon.observeGame(dir, run, 1220, state(mon(species = 4, pid = 99, moves = listOf(10, 52))), game) // not kept
        assertEquals(listOf(1, 0), GachaMon.recent.map { it.card.keep })
        GachaMon.observeGame(dir, "emerald-u 1221 0456", 1221, state(null), game)
        assertTrue(GachaMon.recent.isEmpty())
        assertEquals(listOf(6), GachaMon.collection.map { it.card.pokemonId })
        DiskWriter.drain()
        GachaMon.reset()
        GachaMon.ensureLoaded(dir)
        assertEquals(listOf(6), GachaMon.collection.map { it.card.pokemonId })
        assertTrue(GachaMon.recent.isEmpty())
    }

    @Test
    fun `no card is made in a battle, a battle starting lets the new card go, two trainers keep it`() {
        val game = FakeGame()
        val weak = mon(species = 4, pid = 77, moves = listOf(10, 52), hp = 20)
        GachaMon.observeGame(dir, run, 1, state(weak, inBattle = true), game)
        assertTrue(GachaMon.recent.isEmpty(), "a lead in a battle is not a catch")
        GachaMon.observeGame(dir, run, 1, state(weak), game)
        val card = GachaMon.recent.single()
        assertEquals(0, card.card.keep)
        assertNotNull(GachaMon.newest)
        // Battle.beginNewBattle: the new card is let go.
        GachaMon.observeGame(dir, run, 1, state(weak, inBattle = true), game)
        assertNull(GachaMon.newest)
        GachaMon.observeGame(dir, run, 1, state(weak), game)
        assertEquals(1, GachaMon.recent.single().notes.trainers)
        assertEquals(0, GachaMon.recent.single().card.keep)
        // A wild battle does not count.
        GachaMon.observeGame(dir, run, 1, state(weak, inBattle = true, wild = true), game)
        GachaMon.observeGame(dir, run, 1, state(weak), game)
        assertEquals(1, GachaMon.recent.single().notes.trainers)
        // A trainer lost to (the lead down) does not count either.
        GachaMon.observeGame(dir, run, 1, state(weak, inBattle = true), game)
        GachaMon.observeGame(dir, run, 1, state(mon(species = 4, pid = 77, moves = listOf(10, 52), hp = 20, curHp = 0)), game)
        assertEquals(1, GachaMon.recent.single().notes.trainers)
        // The second trainer it beats keeps it.
        GachaMon.observeGame(dir, run, 1, state(weak, inBattle = true), game)
        GachaMon.observeGame(dir, run, 1, state(weak), game)
        assertEquals(1, GachaMon.recent.single().card.keep)
        // With the option off, wins do not keep a card.
        GachaMonOptions.addAfterTrainers = false
        val other = mon(species = 4, pid = 88, moves = listOf(10), hp = 20)
        GachaMon.observeGame(dir, run, 1, state(other), game)
        repeat(2) {
            GachaMon.observeGame(dir, run, 1, state(other, inBattle = true), game)
            GachaMon.observeGame(dir, run, 1, state(other), game)
        }
        assertEquals(0, GachaMon.recent.first { it.card.personality == 88L }.card.keep)
    }

    @Test
    fun `a badge won on a gym's map marks the party's cards, the game won marks them as winners`() {
        val game = FakeGame()
        val lead = mon(species = 4, pid = 5, moves = listOf(10, 52), hp = 20)
        // FireRed, Pewter City's gym (map 12): the first read is the starting point.
        GachaMon.observeGame(dir, run, 1, state(lead, badges = 0, mapId = 12, route = "firered", badgeSet = "FRLG"), game)
        GachaMon.observeGame(dir, run, 1, state(lead, badges = 1, mapId = 12, route = "firered", badgeSet = "FRLG"), game)
        assertEquals(1, GachaMon.recent.single().card.badges)
        // Not on a gym's map: no mark.
        GachaMon.observeGame(dir, run, 1, state(lead, badges = 3, mapId = 3, route = "firered", badgeSet = "FRLG"), game)
        assertEquals(1, GachaMon.recent.single().card.badges)
        GachaMon.observeGame(dir, run, 1, state(lead, badges = 3, mapId = 3, route = "firered", badgeSet = "FRLG").copy(gameOver = com.ironmonone.tracker.GameOver.WON), game)
        assertEquals(1, GachaMon.recent.single().card.gameWinner)
    }

    @Test
    fun `the heals box's stars are the card now against the card as made`() {
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)
        assertEquals(5 to 5, GachaMon.viewedStars(mon(), dir))
        // Eruption forgotten for Scratch: no same-type move, a weaker set; it rates lower now, the card keeps its five.
        val now = assertNotNull(GachaMon.viewedStars(mon(moves = listOf(152, 155, 10, 337)), dir))
        assertEquals(5, now.second)
        assertTrue(now.first < 5, "now ${now.first}")
        // A Pokemon with no card has no stars.
        assertNull(GachaMon.viewedStars(mon(pid = 1), dir))
        // TrackerScreen.Buttons.GachaMonStars.isVisible
        assertTrue(GachaMonShown.healsStars(starsOn = true, trackPcHeals = false, hasCard = true, newestWaiting = false, inBattle = false))
        assertFalse(GachaMonShown.healsStars(true, trackPcHeals = true, hasCard = true, newestWaiting = false, inBattle = false), "PC heals use the place")
        assertFalse(GachaMonShown.healsStars(false, false, true, false, false), "option off")
        assertFalse(GachaMonShown.healsStars(true, false, hasCard = false, newestWaiting = false, inBattle = false))
        assertFalse(GachaMonShown.healsStars(true, false, true, newestWaiting = true, inBattle = false), "a new card waits")
        assertTrue(GachaMonShown.healsStars(true, false, true, newestWaiting = true, inBattle = true), "in battle the stars stay")
    }

    @Test
    fun `the carousel says GachaMon captured, NEW for a new species, outside a battle`() {
        assertTrue(GachaMonShown.carousel(itemOn = true, packOnTracker = false, newestWaiting = true, inBattle = false))
        assertFalse(GachaMonShown.carousel(itemOn = false, packOnTracker = false, newestWaiting = true, inBattle = false))
        assertFalse(GachaMonShown.carousel(true, packOnTracker = true, newestWaiting = true, inBattle = false), "the pack shows on the tracker instead")
        assertFalse(GachaMonShown.carousel(true, false, newestWaiting = false, inBattle = false))
        assertFalse(GachaMonShown.carousel(true, false, true, inBattle = true))
        assertTrue(GachaMonShown.packOnTracker(packOption = true, newestWaiting = true, inBattle = false))
        assertFalse(GachaMonShown.packOnTracker(packOption = true, newestWaiting = true, inBattle = true))
        assertEquals("NEW! GachaMon captured!", GachaMonCopy.capturedLine(true))
        assertEquals("GachaMon captured!", GachaMonCopy.capturedLine(false))
        // A Charmander card, not kept, nothing collected: a new species (checkIfNewCollectionSpecies)...
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1, state(mon(species = 4, pid = 3, moves = listOf(10), hp = 20)), game)
        val first = GachaMon.recent.single()
        assertTrue(GachaMon.isNewSpecies(first))
        // ...and it stays new once found so, kept or not (Temp.IsNewCollectionSpecies).
        GachaMon.update(first, keep = true)
        assertTrue(GachaMon.isNewSpecies(GachaMon.current(first)))
        // A second Charmander is not new: one is kept.
        GachaMon.observeGame(dir, run, 1, state(mon(species = 4, pid = 4, moves = listOf(10), hp = 20)), game)
        assertFalse(GachaMon.isNewSpecies(GachaMon.recent.last()))
        // "It's a new Pokemon species" keeps a new species at once.
        GachaMonOptions.addIfNew = true
        GachaMon.observeGame(dir, run, 1, state(mon(species = 6, pid = 9, moves = listOf(10), hp = 20)), game)
        assertEquals(1, GachaMon.recent.last().card.keep)
    }

    @Test
    fun `the collection, its favorites, removal, cleanup and a card from a code`() {
        val game = FakeGame()
        GachaMon.observeGame(dir, run, 1220, state(mon()), game)
        GachaMon.observeGame(dir, "emerald-u 1221 x", 1221, state(null), game)
        val e = GachaMon.collection.single()
        GachaMon.update(e, favorite = true)
        assertEquals(1, GachaMon.collection.single().card.favorite)
        // Cleanup never removes a favorite.
        assertEquals(0, GachaMon.removeAll { true })
        GachaMon.update(e, favorite = false)
        assertEquals(1, GachaMon.removeAll { it.stars == 5 })
        assertTrue(GachaMon.collection.isEmpty())
        // The PC tracker's code for Blake's card (tools/gachamon/reference_fixtures.py) goes in, not as a favorite.
        val card = assertNotNull(GachaMonCodec.fromShareCode("ArF5N54GQAUORcQEAAoCEVCwABBUkACYNnGMqkP2NA=="))
        val imported = GachaMon.import(card.copy(favorite = 1))
        assertEquals(0, imported.card.favorite)
        assertEquals("Charizard", imported.speciesName)    // the reference's own names for a card from elsewhere
        assertEquals("Compoundeyes", imported.abilityName)
        assertEquals("Crabhammer", imported.moveName(0))
        GachaMon.removeFromCollection(imported)
        assertTrue(GachaMon.collection.isEmpty())
        DiskWriter.drain()
        GachaMon.reset(); GachaMon.ensureLoaded(dir)
        assertTrue(GachaMon.collection.isEmpty())
    }

    @Test
    fun `the GachaMon files are in the backup`() {
        for (f in listOf("collection.txt", "recent.txt", "dex.txt", "options.txt")) assertTrue(Backup.admits("${GachaMon.DIR}/$f"), f)
    }

    @Test
    fun `options are kept, and the stars and PC heals take turns`() {
        GachaMonOptions.ruleset = "SuperKaizo"; GachaMonOptions.showPack = true; GachaMonOptions.prizeCards = false
        GachaMon.saveOptions()
        DiskWriter.drain()
        GachaMonOptions.load("ruleset=Standard\nshowPack=false\nprizeCards=true\n")
        GachaMonOptions.load(File(dir, "prep/gachamon/options.txt").readText())
        assertEquals("SuperKaizo", GachaMonOptions.ruleset)
        assertTrue(GachaMonOptions.showPack)
        assertFalse(GachaMonOptions.prizeCards)
        assertEquals("SuperKaizo", GachaMon.rulesetKey(dir, natDex = false))
        val before = TrackerOptions.trackPcHeals
        try {
            TrackerOptions.trackPcHeals = true
            GachaMonOptions.chooseShowStars(true)
            assertFalse(TrackerOptions.trackPcHeals)
        } finally { TrackerOptions.trackPcHeals = before }
        // autoDetermineIronmonRuleset: Super Kaizo before Kaizo, Survival Revival before Survival, Standard when none.
        assertEquals("SuperKaizo", GachaMonRulesets.detect("RSE Super Kaizo", false))
        assertEquals("Kaizo", GachaMonRulesets.detect("FRLG Kaizo Doubles", false))
        assertEquals("SurvivalRevival", GachaMonRulesets.detect("RSE Survival Revival", false))
        assertEquals("Survival", GachaMonRulesets.detect("RSE Survival", false))
        assertEquals("Standard", GachaMonRulesets.detect("RSE Journey", false))
        assertEquals("Standard", GachaMonRulesets.detect("FRLG Ascension 2", natDex = false), "the Ascension rulesets are Nat. Dex only")
        assertEquals("Ascension2", GachaMonRulesets.detect("FRLG Ascension 2", natDex = true))
    }

    @Test
    fun `the prize card after two common trainers, the stronger one's best Pokemon, its id in the card, kept`() {
        val parties = mapOf(
            414 to listOf(GachaMonPrize.TrainerMon(95, 14, 15, emptyList())),
            415 to listOf(GachaMonPrize.TrainerMon(121, 21, 31, listOf(55, 61, 0, 0))),
        )
        val game = FakeGame(parties, beaten = setOf(414))
        val lead = mon(species = 4, pid = 5, moves = listOf(10, 52), hp = 20)
        GachaMon.observeGame(dir, run, 7, state(lead, route = "firered", badgeSet = "FRLG"), game)
        assertEquals(1, GachaMon.defeatedCommonTrainers())
        assertFalse(GachaMon.prizeOffered(), "the first rival fight alone does not count")
        val both = FakeGame(parties, beaten = setOf(414, 415))
        GachaMon.observeGame(dir, run, 7, state(lead, route = "firered", badgeSet = "FRLG"), both)
        assertTrue(GachaMon.prizeOffered())
        val prize = assertNotNull(GachaMon.makePrize(dir, Random(11)))
        // Misty's Starmie (listed 520) beats Brock's Onix (385).
        assertEquals(415L, prize.card.personality)
        assertEquals(121, prize.card.pokemonId)
        assertEquals(21, prize.card.level)
        assertEquals("Misty", prize.trainerName)
        assertEquals("Leader Misty", prize.notes.trainer)
        assertEquals("Cerulean Gym", prize.notes.place)
        assertEquals(1, prize.card.keep, "Occasionally receive prize cards from Trainers keeps it")
        assertEquals(7, prize.card.seedNumber)
        assertTrue(prize.uid in GachaMon.recent.map { it.uid })
        // Asked again, the same card: one prize a run.
        assertEquals(prize.uid, GachaMon.makePrize(dir, Random(12))?.uid)
        // The option off: no prize offered.
        GachaMonOptions.prizeCards = false
        assertFalse(GachaMon.prizeOffered())
    }
}
