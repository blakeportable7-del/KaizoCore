package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonCodec
import com.ironmonone.tracker.gachamon.GachaMonPrize
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GachaMon on Heart & Soul (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): its cards say their game and keep its numbering,
 * its Johto badges mark them, and its rivals, leaders, Elite Four, Lance, Red and the Rocket executives give prize
 * cards. Its map has no route version, so all of this was "?" or nothing before. The game is a stand-in (HnsGame) with
 * Heart & Soul's ids: Treecko is 252 there.
 */
class HnsGachaMonTest {
    private lateinit var dir: File
    private val noLuck = object : Random() { override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount) }

    @BeforeTest
    fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("hns-gachamon").toFile()
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

    private class HnsGame(private val parties: Map<Int, List<GachaMonPrize.TrainerMon>> = emptyMap(), private val beaten: Set<Int> = emptySet()) : GachaMonGame {
        override val expandedSpeciesIds = true
        override val nameSet = "hns"
        override val heartSoul = true
        override val lastMoveId = 900
        private val bases = mapOf(
            252 to BaseStats(hp = 40, atk = 45, def = 35, spe = 70, spAtk = 65, spDef = 55, type1 = 12, type2 = 12, ability1 = 65, ability2 = 0, genderRatio = 31),
            130 to BaseStats(hp = 95, atk = 125, def = 79, spe = 81, spAtk = 60, spDef = 100, type1 = 11, type2 = 2, ability1 = 22, ability2 = 0, genderRatio = 127),
        )
        private val moves = mapOf(1 to MoveRow(1, "Pound", 35, 35, 40, 100, 0, "PHY"), 33 to MoveRow(33, "Tackle", 35, 35, 40, 100, 0, "PHY"))
        override fun baseStats(species: Int) = bases[species]
        override fun moveRowFor(id: Int) = moves[id]
        override fun evolution(species: Int) = if (species == 252) "16" else null
        override fun speciesName(species: Int) = mapOf(252 to "Treecko", 130 to "Gyarados")[species] ?: "#$species"
        override fun abilityName(id: Int) = "Overgrow"
        override fun moveName(id: Int) = moves[id]?.name ?: "#$id"
        override fun rawMapId(mapId: Int) = mapId
        override fun trainerParty(trainerId: Int) = parties[trainerId].orEmpty()
        override fun trainerTitle(trainerId: Int) = mapOf(464 to "PKMN Trainer Red", 402 to "Leader Falkner")[trainerId]
        override fun trainerDefeated(trainerId: Int) = trainerId in beaten
        override fun learnset(species: Int) = listOf(1 to 1, 5 to 33)
        override fun speciesExists(species: Int) = species in bases
        override fun routeNameOfTrainer(trainerId: Int) = mapOf(464 to "Mt. Silver", 402 to "Violet City")[trainerId]
    }

    private fun treecko(pid: Long = 0x0BADCAFEL) = TrackedMon(
        mon = PokemonDecoder.Mon(pid = pid, level = 5, nickname = "", species = 252, heldItem = 0, friendship = 70,
            moves = listOf(1, 33), pp = listOf(35, 35), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 3, shiny = false, status = 0, curHp = 20, maxHp = 20,
            atk = 10, def = 9, spe = 13, spAtk = 12, spDef = 11),
        speciesName = "Treecko", moveNames = emptyList(), base = null,
    )

    /** What the Heart & Soul map reads: no route version, the badge set "GSC" (two rows of eight). */
    private fun state(lead: TrackedMon?, badges: Int = 0, mapId: Int? = 0x0105) = TrackerState(
        partyCount = if (lead == null) 0 else 1, party = listOfNotNull(lead), inBattle = false, isWildBattle = false,
        badges = badges, mapId = mapId, routeVersion = "", badgeSet = "GSC",
    )

    private val run = "heartsoul-kaizo-206 3 0042"

    @Test
    fun `a Heart and Soul lead becomes a card of its own game and numbering`() {
        GachaMon.observeGame(dir, run, 3, state(treecko()), HnsGame())
        val e = assertNotNull(GachaMon.recent.singleOrNull())
        assertEquals(GachaMonCard.HEART_SOUL, e.card.gameVersion)
        assertEquals("Heart & Soul", e.card.gameName)
        assertEquals(252, e.card.pokemonId)
        assertEquals(GachaMon.HNS_DEX, e.notes.dex)
        assertTrue(252 in GachaMon.seen(GachaMon.HNS_DEX), "Treecko is seen in Heart & Soul's numbering")
        assertTrue(252 !in GachaMon.seen(""), "and not as Gen 3's empty id 252")
        // A code read back (another phone, the PC tracker's file) keeps its game, and with it the numbering and the name.
        val code = GachaMonCodec.shareCode(e.card)
        val back = assertNotNull(GachaMonCodec.fromShareCode(code))
        assertEquals(GachaMonCard.HEART_SOUL, back.gameVersion)
        val imported = GachaMon.import(back)
        assertEquals(GachaMon.HNS_DEX, imported.notes.dex)
        assertEquals("Treecko", imported.speciesName)
        // Its badges in HeartGold and SoulSilver's art, its picture from the pack by its own id, every GachaDex id.
        assertEquals("HGSS", gachaBadgeSet(GachaMonCard.HEART_SOUL))
        assertEquals("hns", gachaSpriteSet(GachaMon.HNS_DEX))
        val ids = GachaMon.dexIds(com.ironmonone.tracker.HnsSpecies.TOTAL, GachaMon.HNS_DEX)
        assertEquals(1572, ids.size)
        assertTrue(252 in ids && 276 in ids)
        assertEquals(1283 - 25, GachaMon.dexIds(1283, "").size, "the other numberings still skip Gen 3's empty ids")
        // The filter lists Heart & Soul, and its cards are not filtered out by default.
        assertTrue(GachaFilter().matches(e))
    }

    @Test
    fun `a Johto badge marks the party's cards on any map, a Kanto one is past what a card holds`() {
        val game = HnsGame()
        val lead = treecko()
        GachaMon.observeGame(dir, run, 3, state(lead, badges = 0), game)
        GachaMon.observeGame(dir, run, 3, state(lead, badges = 1, mapId = 0x0A01), game)
        assertEquals(1, GachaMon.recent.single().card.badges, "Falkner's Zephyr Badge, won in Violet City's gym")
        GachaMon.observeGame(dir, run, 3, state(lead, badges = 0x81), game)
        assertEquals(0x81, GachaMon.recent.single().card.badges, "Clair's Rising Badge, the eighth")
        GachaMon.observeGame(dir, run, 3, state(lead, badges = 0x181), game)
        assertEquals(0x81, GachaMon.recent.single().card.badges, "the Boulder Badge is the ninth: a card holds eight")
    }

    @Test
    fun `Heart and Soul's rivals, leaders and Red give prize cards`() {
        val common = GachaMonPrize.commonTrainers(GachaMonCard.HEART_SOUL)
        assertEquals(listOf(464), common["Red"])
        assertEquals(listOf(441, 442, 560), common["Lance"])
        assertEquals(listOf(443, 450, 457), common["Rival 1"])
        assertEquals(34, common.size)
        assertTrue(common.values.flatten().none { it in 631..652 }, "no post-game rematch")
        val parties = mapOf(
            402 to listOf(GachaMonPrize.TrainerMon(252, 12, 10, emptyList())),
            464 to listOf(GachaMonPrize.TrainerMon(130, 88, 31, listOf(33, 0, 0, 0))),
        )
        val lead = treecko()
        GachaMon.observeGame(dir, run, 3, state(lead), HnsGame(parties, beaten = setOf(402)))
        assertEquals(1, GachaMon.defeatedCommonTrainers())
        GachaMon.observeGame(dir, run, 3, state(lead), HnsGame(parties, beaten = setOf(402, 464)))
        assertTrue(GachaMon.prizeOffered())
        val prize = assertNotNull(GachaMon.makePrize(dir, Random(5)))
        assertEquals(464L, prize.card.personality, "Red's Gyarados beats Falkner's Treecko")
        assertEquals(GachaMonCard.HEART_SOUL, prize.card.gameVersion)
        assertEquals("Red", prize.trainerName)
        assertEquals("Mt. Silver", prize.notes.place)
    }
}
