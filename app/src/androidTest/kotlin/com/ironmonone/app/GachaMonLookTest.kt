package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonPrize
import com.ironmonone.tracker.gachamon.SixStats
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * GachaMon's screens, drawn to PNGs in the app's filesDir so they can be looked at (as TrackerLookTest does for the
 * tracker): the cards, each tab of the GachaMon screen, a pack sealed and opened, and the tracker with the heals box's
 * stars and the carousel's "GachaMon captured!". The cards live in a scratch folder, never the app's own collection.
 */
class GachaMonLookTest {
    @get:Rule val compose = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scratch: File
    private var carouselBefore = ""
    private var pcHealsBefore = false

    private class LookGame : GachaMonGame {
        override val expandedSpeciesIds = false
        override val nameSet = ""
        override val lastMoveId = 354
        override fun baseStats(species: Int) = when (species) {
            6 -> BaseStats(hp = 20, atk = 140, def = 45, spe = 35, spAtk = 125, spDef = 155, type1 = 10, type2 = 2, ability1 = 66, ability2 = 14, genderRatio = 31)
            else -> BaseStats(hp = 60, atk = 60, def = 60, spe = 60, spAtk = 60, spDef = 60, type1 = 0, type2 = 0, ability1 = 1, ability2 = 0)
        }
        private val moves = mapOf(
            152 to MoveRow(152, "Crabhammer", 10, 10, 90, 85, 11, "SPE"), 155 to MoveRow(155, "Bonemerang", 10, 10, 50, 90, 4, "PHY"),
            284 to MoveRow(284, "Eruption", 5, 5, 150, 100, 10, "SPE"), 337 to MoveRow(337, "Dragon Claw", 15, 15, 80, 100, 16, "SPE"),
        )
        override fun moveRowFor(id: Int) = moves[id]
        override fun evolution(species: Int): String? = null
        override fun speciesName(species: Int) = if (species == 6) "Charizard" else "#$species"
        override fun abilityName(id: Int) = if (id == 14) "Compoundeyes" else "Blaze"
        override fun moveName(id: Int) = moves[id]?.name ?: "#$id"
        override fun rawMapId(mapId: Int) = mapId
        override fun trainerParty(trainerId: Int) = emptyList<GachaMonPrize.TrainerMon>()
        override fun trainerTitle(trainerId: Int): String? = null
        override fun trainerDefeated(trainerId: Int) = false
        override fun learnset(species: Int) = emptyList<Pair<Int, Int>>()
        override fun speciesExists(species: Int) = true
        override fun routeNameOfTrainer(trainerId: Int): String? = null
    }

    private val charizard = TrackedMon(
        mon = PokemonDecoder.Mon(pid = 0x12345678, level = 5, nickname = "", species = 6, heldItem = 0, friendship = 70,
            moves = listOf(152, 155, 284, 337), pp = listOf(10, 10, 5, 15), ivs = List(6) { 20 }, evs = List(6) { 0 },
            ppUps = List(4) { 0 }, abilitySlot = 1, nature = 8, shiny = false, status = 0, curHp = 17, maxHp = 17,
            atk = 20, def = 11, spe = 9, spAtk = 16, spDef = 21),
        speciesName = "Charizard", moveNames = listOf("Crabhammer", "Bonemerang", "Eruption", "Dragon Claw"),
        base = BaseStats(hp = 20, atk = 140, def = 45, spe = 35, spAtk = 125, spDef = 155, type1 = 10, type2 = 2, ability1 = 66, ability2 = 14, genderRatio = 31),
        abilityName = "Compoundeyes", itemName = "-",
        moveRows = listOf(
            MoveRow(152, "Crabhammer", 10, 10, 90, 85, 11, "SPE"), MoveRow(155, "Bonemerang", 10, 10, 50, 90, 4, "PHY"),
            MoveRow(284, "Eruption", 5, 5, 150, 100, 10, "SPE"), MoveRow(337, "Dragon Claw", 15, 15, 80, 100, 16, "SPE"),
        ),
        movesLearned = 4, movesTotal = 12, nextMoveLevel = 7,
    )

    private fun state(inBattle: Boolean = false) = TrackerState(
        partyCount = 1, party = listOf(charizard), inBattle = inBattle, isWildBattle = false,
        healPercent = 120, healCount = 3, badges = 0b11, badgeSet = "RSE", routeVersion = "emerald", mapId = 3,
    )

    private fun card(species: Int, rating: Int, power: Int, t1: Int, t2: Int, game: Int = 2, personality: Long = 0x0BADCAFEL,
                     shiny: Int = 0, fave: Int = 0, winner: Int = 0, badges: Int = 0, level: Int = 40) = GachaMonEntry(GachaMonCard(
        version = 2, personality = personality, pokemonId = species, level = level, abilityId = 26, ratingScore = rating, battlePower = power,
        favorite = fave, gameWinner = winner, seedNumber = 1220, badges = badges, type1 = t1, type2 = t2,
        stats = SixStats(level * 3, level * 3, level * 2, level * 4, level * 2, level * 3), moveIds = listOf(85, 94, 0, 0),
        gameVersion = game, keep = 1, isShiny = shiny, gender = 0, nature = 3, year = 2026, month = 10, day = 3,
    ))

    @Before
    fun setUp() {
        scratch = File(ctx.cacheDir, "gachamon-look").apply { deleteRecursively(); mkdirs() }
        GachaMon.reset()
        GachaMon.ensureLoaded(scratch)
        GachaMonOptions.load("ruleset=AutoDetect\naddIfNew=false\naddAfterTrainers=true\nprizeCards=true\nshowStars=true\nshowPack=false\nanimatePack=true\n")
        carouselBefore = TrackerOptions.carouselItems
        pcHealsBefore = TrackerOptions.trackPcHeals
        TrackerOptions.trackPcHeals = false
        // Blake's Charizard, made as Play makes a card, and a few others for the collection.
        GachaMon.observeGame(scratch, "emerald-u 1220 look", 1220, state(), LookGame())
        GachaMon.collection += listOf(
            card(149, 80, 12000, 16, 2, shiny = 1, fave = 1),               // a 5+ card: platinum stars
            card(121, 58, 7000, 11, 14, game = 3, personality = 415),        // Misty's prize card
            card(94, 61, 9000, 7, 3, winner = 1, badges = 0b111111),         // won the game
            card(129, 10, 1000, 11, 11, level = 12),                         // one star
        )
    }

    @After
    fun tearDown() {
        TrackerOptions.carouselItems = carouselBefore
        TrackerOptions.trackPcHeals = pcHealsBefore
        GachaMon.reset()
        scratch.deleteRecursively()
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.background(Shell.night)) { content() } }
        compose.mainClock.advanceTimeBy(1_500)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File(ctx.filesDir, name)
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        check(out.length() > 0) { "no image written to " + out.absolutePath }
        println("SHOT " + out.absolutePath + " " + bmp.width + "x" + bmp.height)
    }

    @Test
    fun cards() = shoot("gacha-cards.png") {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GachaMonCardFace(GachaMon.recent.single(), 150.dp)
                GachaMonCardFace(GachaMon.collection[0], 150.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in GachaMon.collection.drop(1)) GachaMonCardFace(e, 100.dp, collected = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GachaMiniCard(6, 10, 2, seen = true, collected = true, reveal = false, width = 56.dp, dex = "")
                GachaMiniCard(25, 13, 13, seen = true, collected = false, reveal = false, width = 56.dp, dex = "")
                GachaMiniCard(150, 14, 14, seen = false, collected = false, reveal = false, width = 56.dp, dex = "")
                GachaMonStarRow(5, initial = 4, height = 30.dp)
                GachaMonStarRow(3, initial = 4, height = 16.dp)
            }
        }
    }

    private fun tab(name: String, start: GachaMonStart) = shoot(name) {
        Box(Modifier.size(400.dp, 820.dp)) { GachaMonScreenContent(start) {} }
    }

    @Test fun captures() = tab("gacha-captures.png", GachaMonStart(GachaMonTab.CAPTURES))
    @Test fun collection() = tab("gacha-collection.png", GachaMonStart(GachaMonTab.COLLECTION))
    @Test fun view() = tab("gacha-view.png", GachaMonStart(GachaMonTab.VIEW, GachaMon.recent.single()))
    @Test fun viewPrize() = tab("gacha-view-prize.png", GachaMonStart(GachaMonTab.VIEW, GachaMon.collection[1]))
    @Test fun gachadex() = tab("gacha-dex.png", GachaMonStart(GachaMonTab.GACHADEX))
    @Test fun options() = tab("gacha-options.png", GachaMonStart(GachaMonTab.OPTIONS))
    @Test fun about() = tab("gacha-about.png", GachaMonStart(GachaMonTab.ABOUT))

    @Test
    fun packSealed() = shoot("gacha-pack-sealed.png") {
        Box(Modifier.size(400.dp, 700.dp)) { GachaMonPackContent(GachaMon.recent.single()) {} }
    }

    @Test
    fun packOpened() = shoot("gacha-pack-open.png") {
        Box(Modifier.size(400.dp, 700.dp)) { GachaMonPackContent(GachaMon.collection[1], startOpened = true) {} }
    }

    @Test
    fun trackerStars() {
        // The new card looked at: the heals box shows its stars.
        GachaMon.newestSeen()
        shoot("gacha-tracker-stars.png") { trackerPanel(state()) }
    }

    @Test
    fun trackerCarousel() {
        // A new card waiting: the carousel's line (only its item on, so it is the one showing).
        TrackerOptions.carouselItems = "GachaMon"
        shoot("gacha-tracker-carousel.png") { trackerPanel(state()) }
    }

    @Test
    fun trackerPack() {
        GachaMonOptions.showPack = true
        shoot("gacha-tracker-pack.png") { trackerPanel(state()) }
    }

    @Composable
    private fun trackerPanel(s: TrackerState) {
        Box(Modifier.width(221.dp).fillMaxHeight().background(Color.Black)) {
            TrackerPanel(state = s, attempt = 1220, routeName = "Route 101",
                spriteFor = { sp -> PcAssets.gbaSprite(ctx, sp) }, bstLines = null, joinedForms = null, moveRules = null, ruleRun = null)
        }
    }
}
