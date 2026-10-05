package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Renders the tracker with a fixed party so the layout can be SEEN.
 *
 * The panel only appears once a real run has a real party, which is a minutes-
 * long loop to iterate a layout against - and the reported problem ("does not
 * match the PC version") is purely visual, so a passing assertion would not
 * have answered it anyway. This writes a PNG to the device for inspection.
 */
class TrackerLookTest {

    @get:Rule val compose = createComposeRule()

    private fun mon(species: Int, level: Int, cur: Int, max: Int) = PokemonDecoder.Mon(
        pid = 0x12345678, level = level, nickname = "", species = species,
        heldItem = 0, friendship = 70,
        moves = listOf(84, 45, 33, 39), pp = listOf(30, 40, 35, 20),
        ivs = List(6) { 20 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 3, shiny = false, status = 0,
        curHp = cur, maxHp = max,
        atk = 29, def = 29, spe = 13, spAtk = 26, spDef = 9,
    )

    private fun tracked(name: String, species: Int, level: Int) = TrackedMon(
        mon = mon(species, level, 29, 29),
        speciesName = name,
        moveNames = listOf("Thunder Shock", "Growl", "Tackle", "Tail Whip"),
        base = BaseStats(75, 125, 140, 40, 60, 90, 6, 8, 1, 2),
        abilityName = "Shield Dust",
        itemName = "Choice Band",
        moveRows = listOf(
            MoveRow(84, "Thunder Shock", 30, 30, 40, 100, 13, "SPE"),
            MoveRow(45, "Growl", 40, 40, 0, 100, 0, "STA"),
            MoveRow(33, "Tackle", 35, 35, 40, 100, 0, "PHY"),
            MoveRow(39, "Tail Whip", 30, 30, 0, 100, 0, "STA"),
        ),
        movesLearned = 4, movesTotal = 18, nextMoveLevel = 9,
        statusCondition = "",
    )

    private fun shoot(
        name: String, widthDp: Int, state: TrackerState,
        stackBoth: Boolean = false,
        bstLines: BstRule.Lines? = null,
        joined: JoinedForms? = null,
        // Handed in, never read from the device's own run, so no other shot here picks up its rules.
        moveRules: MoveRule.Rules? = null,
        ruleRun: RuleMarks.Run? = null,
        onGear: (() -> Unit)? = null,
        trailing: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        // The carousel's clock ticks every 100 ms (PcCarousel), so on a running clock Compose is never idle
        // (2026-10-02: every shot here timed out). The test drives the clock instead.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    TrackerPanel(
                        state = state,
                        attempt = 2,
                        routeName = "Viridian Forest",
                        // The real bundled pack, so this also proves the art
                        // actually loads and lands in the sprite box.
                        spriteFor = { sp -> PcAssets.gbaSprite(ctx, sp) },
                        stackBoth = stackBoth,
                        bstLines = bstLines, joinedForms = joined, moveRules = moveRules, ruleRun = ruleRun,
                        onGear = onGear, headerTrailing = trailing,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        // filesDir, not external storage: getExternalFilesDir can return null
        // on an emulator with no mounted media, and File(null, name) then
        // silently writes somewhere useless while the test still passes.
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val out = File(dir, name)
        out.outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        check(out.length() > 0) { "no image written to " + out.absolutePath }
        println("SHOT " + out.absolutePath + " " + bmp.width + "x" + bmp.height)
    }

    /** The pane width the landscape split actually gives the tracker. */
    private val paneDp = 221

    @Test
    fun party_card() {
        shoot("look-party.png", paneDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = false, isWildBattle = false,
            healPercent = 0, healCount = 0,
            routeName = "Viridian Forest",
        ))
    }

    @Test
    fun enemy_card() {
        shoot("look-enemy.png", paneDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = true, isWildBattle = false,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 245, speciesName = "Suicune", level = 42,
                curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
                base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2),
                movesSeen = listOf("Mind Reader", "Surf"),
                // Two seen of four slots, so the blank rows are visible too.
                moveRows = listOf(
                    MoveRow(95, "Mind Reader", 5, null, null, 100, 14, "STA"),
                    MoveRow(57, "Surf", 15, null, 95, 100, 11, "SPE"),
                ),
                abilityGuess = "Pressure / Inner Focus",
                statusCondition = "SLP",
            ),
            healPercent = 44, healCount = 7,
            // A trainer with four Pokemon, the second of which has fainted.
            enemyTeam = listOf(true, false, true, true),
            routeName = "Route 24",
        ))
    }

    /**
     * Portrait puts the tracker under the game at the FULL screen width, so
     * one reference unit is nearly twice what it is in the landscape split.
     * The canvas is meant to make that a pure scale-up; this is the check.
     */
    private val portraitDp = 393

    /**
     * Landscape: YOUR lead and the enemy on screen together, yours on top.
     *
     * The swap-only layout meant the enemy card replaced your own, so marking
     * an opponent's stats hid the Pokemon you were comparing it against. This
     * shot is the proof both cards render, and in that order.
     */
    @Test
    fun both_cards_stacked_landscape() {
        shoot("look-stacked.png", paneDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = true, isWildBattle = false,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 245, speciesName = "Suicune", level = 42,
                curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
                base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2),
                movesSeen = listOf("Surf"),
                moveRows = listOf(
                    MoveRow(57, "Surf", 15, null, 95, 100, 11, "SPE"),
                ),
                abilityGuess = "Pressure / Inner Focus",
            ),
            healPercent = 44, healCount = 7,
            enemyTeam = listOf(true, false, true, true),
            routeName = "Route 24",
        ), stackBoth = true)
    }

    /**
     * The enemy card must LEAVE when the battle does.
     *
     * "Trainer pokemon stays on screen even after battle" was a real defect
     * here once. The tracker nulls its enemy out of battle, but this asserts
     * the PANEL refuses to draw one regardless - so a stale EnemyInfo cannot
     * put a dead opponent back on screen, and the stackBoth clause added for
     * landscape cannot quietly bypass the in-battle gate.
     *
     * The in-battle assertion is a POSITIVE CONTROL, not decoration. Without
     * it, this test passes just as happily if the card never renders at all.
     */
    @Test
    fun enemy_card_leaves_when_the_battle_ends() {
        fun state(inBattle: Boolean) = TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = inBattle, isWildBattle = false,
            // Deliberately still present after the battle: the gate under test
            // is the panel's, not the tracker's.
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 245, speciesName = "Suicune", level = 42,
                curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
                base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2),
                movesSeen = listOf("Surf"),
                moveRows = listOf(
                    MoveRow(57, "Surf", 15, null, 95, 100, 11, "SPE"),
                ),
            ),
            enemyTeam = listOf(true, true),
            healPercent = 0, healCount = 0,
        )

        val live = mutableStateOf(state(inBattle = true))
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(paneDp.dp).fillMaxHeight()) {
                    TrackerPanel(
                        state = live.value, attempt = 2,
                        stackBoth = true,
                    )
                }
            }
        }

        compose.onNodeWithText("Suicune").assertIsDisplayed()
        compose.onNodeWithText("Golisopod").assertIsDisplayed()

        live.value = state(inBattle = false)
        compose.waitForIdle()

        compose.onNodeWithText("Suicune").assertDoesNotExist()
        // Yours stays: the battle ending must not blank the whole panel.
        compose.onNodeWithText("Golisopod").assertIsDisplayed()
    }

    /**
     * A WILD encounter, stacked in landscape.
     *
     * Every other battle fixture here is a trainer, so the wild path had no
     * rendered proof at all - and it differs: no pokeball row, and the banner
     * offers RUN, which a trainer battle never does.
     */
    @Test
    fun wild_encounter_stacked_landscape() {
        shoot("look-wild.png", paneDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = true, isWildBattle = true,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 396, speciesName = "Starly", level = 4,
                curHp = 18, maxHp = 18, type1 = 0, type2 = 2,
                base = BaseStats(40, 55, 30, 30, 30, 60, 0, 2, 1, 2),
                movesSeen = listOf("Tackle"),
                moveRows = listOf(
                    MoveRow(33, "Tackle", 35, null, 40, 100, 0, "PHY"),
                ),
                abilityGuess = "Keen Eye",
            ),
            healPercent = 44, healCount = 7,
            // Wild: no team, so no pokeball row should appear.
            enemyTeam = emptyList(),
            routeName = "Route 24",
        ), stackBoth = true)
    }

    /** The move popup, rendered as content (a Dialog is its own window). */
    @Test
    fun move_info_popup() {
        val d = MoveDetail(
            name = "Surf", typeId = 11, typeName = "Water", category = "SPE",
            contact = false, pp = 15, ppMax = 15, power = 95, acc = 100, priority = 0,
            summary = "A big wave crashes down on the foe. Can also be used for crossing water.",
            typeChart = MoveMatchup.general(11),
        )
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(300.dp)) { PcMoveInfoContent(d) {} }
            }
        }
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.filesDir, "look-move-info.png").outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("SURF").assertIsDisplayed()
        compose.onNodeWithText("Strong against: Fire, Ground, Rock").assertIsDisplayed()
        // Nothing about an opponent may appear here. Vetoed 2026-09-05.
        compose.onNodeWithText("vs ", substring = true).assertDoesNotExist()
    }

    @Test
    fun party_card_portrait() {
        shoot("look-portrait.png", portraitDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = false, isWildBattle = false,
            healPercent = 0, healCount = 0,
            routeName = "Viridian Forest",
        ))
    }

    @Test
    fun enemy_card_portrait() {
        shoot("look-portrait-enemy.png", portraitDp, TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = true, isWildBattle = false,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 245, speciesName = "Suicune", level = 42,
                curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
                base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2),
                movesSeen = listOf("Mind Reader", "Surf"),
                moveRows = listOf(
                    MoveRow(95, "Mind Reader", 5, null, null, 100, 14, "STA"),
                    MoveRow(57, "Surf", 15, null, 95, 100, 11, "SPE"),
                ),
                abilityGuess = "Pressure / Inner Focus",
                statusCondition = "SLP",
            ),
            healPercent = 44, healCount = 7,
            enemyTeam = listOf(true, false, true, true),
            routeName = "Route 24",
        ))
    }

    // ---- DS panel --------------------------------------------------------
    //
    // The DS tracker had never been rendered ONCE since the shared composables
    // changed - and they changed twice (the reference-pixel canvas, then the
    // component kit). It shares PixText's metrics, the chips, the stat rows,
    // the moves table and the card chrome with the GBA panel, so it is the
    // most likely place for those changes to have broken something, and the
    // only place nobody had looked.

    private fun ndsMon(species: Int, level: Int) = com.ironmonone.tracker.nds.Gen4.Mon(
        pid = 0x1234_5678, species = species, heldItem = 0, abilityId = 1,
        level = level, curHp = 44, maxHp = 61,
        atk = 38, def = 31, spe = 29, spAtk = 42, spDef = 33,
        moves = listOf(33, 45, 84, 39), pp = listOf(35, 40, 30, 30),
        ppUps = List(4) { 0 }, ivs = List(6) { 20 },
        shiny = false, nature = 3, isEgg = false, status = 0,
    )

    private fun ndsTracked(name: String, species: Int, level: Int) =
        com.ironmonone.tracker.nds.NdsTrackedMon(
            mon = ndsMon(species, level),
            speciesName = name,
            info = com.ironmonone.tracker.nds.NdsSpeciesInfo(
                name = name, type1 = "GRASS", type2 = "POISON", bst = 405,
                ability1 = "Overgrow", ability2 = "Chlorophyll",
            ),
            abilityName = "Overgrow",
            itemName = "Oran Berry",
            moves = listOf(
                com.ironmonone.tracker.nds.NdsMoveInfo("Tackle", 40, 100, "NORMAL", 35, "PHY"),
                com.ironmonone.tracker.nds.NdsMoveInfo("Growl", 0, 100, "NORMAL", 40, "STA"),
                com.ironmonone.tracker.nds.NdsMoveInfo("Thunder Shock", 40, 100, "ELECTRIC", 30, "SPE"),
                com.ironmonone.tracker.nds.NdsMoveInfo("Tail Whip", 0, 100, "NORMAL", 30, "STA"),
            ),
            movesLearned = 4, movesTotal = 16, nextMoveLevel = 13,
        )

    private fun shootNds(
        name: String, widthDp: Int, state: com.ironmonone.tracker.nds.NdsTrackerState,
        bstLines: BstRule.Lines? = null, joined: JoinedForms? = null, stackBoth: Boolean = false,
        moveRules: MoveRule.Rules? = null,
        ruleRun: RuleMarks.Run? = null,
        onGear: (() -> Unit)? = null,
        trailing: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                    NdsTrackerPanel(state = state, attempt = 2, stackBoth = stackBoth, bstLines = bstLines, joinedForms = joined, moveRules = moveRules,
                        ruleRun = ruleRun, onGear = onGear, headerTrailing = trailing)
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val out = File(dir, name)
        out.outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        check(out.length() > 0) { "no image written to " + out.absolutePath }
    }

    /** A fresh record of what each Pokemon joined as, in the app's cache. */
    private fun joinedFresh(): JoinedForms {
        val f = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "look-joined.txt")
        f.delete()
        return JoinedForms(f, attempt = 2)
    }

    /**
     * Kaizo's BST line (Blake, 2026-10-02: a Zekrom of 679 from the Black 2 lab, "no indication that this is against
     * the rules"): your Pokemon past it, as it joined, and a wild one past it, both with the X and a red number.
     */
    @Test
    fun bst_over_the_line() {
        // 406 is Rayquaza's index inside the Gen 3 games, which the sprite pack follows; 384 there is Aggron.
        val ray = tracked("Rayquaza", 406, 5).copy(
            base = BaseStats(105, 150, 90, 95, 150, 90, 16, 2, 1, 2),
            abilityName = "Air Lock", itemName = "-",
            moveNames = listOf("Twister", "Leer"),
            moveRows = listOf(
                MoveRow(239, "Twister", 20, 20, 40, 100, 16, "SPE"),
                MoveRow(43, "Leer", 30, 30, 0, 100, 0, "STA"),
            ),
        )
        shoot("look-bst.png", paneDp, TrackerState(
            partyCount = 1, party = listOf(ray),
            inBattle = true, isWildBattle = true,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 150, speciesName = "Mewtwo", level = 4,
                curHp = 20, maxHp = 20, type1 = 14, type2 = 14,
                base = BaseStats(106, 110, 90, 130, 154, 90, 14, 14, 1, 2),
                movesSeen = emptyList(), abilityGuess = "Pressure",
            ),
            healPercent = 0, healCount = 0, enemyTeam = emptyList(), routeName = "Route 1",
        ), stackBoth = true, bstLines = BstRule.Lines(599, 599), joined = joinedFresh())
        assert(compose.onAllNodesWithContentDescription("Over this run's BST limit", useUnmergedTree = true)
            .fetchSemanticsNodes().size == 2) { "the X on yours and on the wild one" }
    }

    /**
     * Banned moves (Blake, 2026-10-02: "just like the x on bst of 600+"): FireRed Kaizo in a wild battle, where Giga
     * Drain, Recover and Surf (an HM) are banned and Tackle is not. Each banned name goes red with the X after it.
     */
    /** The rules' X follows Tracker Setup's switch, off until turned on (RuleMarks): these shots turn it on. */
    private fun marksOn(block: () -> Any?) {
        val was = TrackerOptions.ruleMarks
        TrackerOptions.ruleMarks = true
        try { block() } finally { TrackerOptions.ruleMarks = was }
    }

    @Test
    fun banned_moves() = marksOn {
        val mon = tracked("Bulbasaur", 1, 12).copy(
            moveNames = listOf("Giga Drain", "Recover", "Surf", "Tackle"),
            moveRows = listOf(
                MoveRow(202, "Giga Drain", 5, 5, 60, 100, 12, "SPE"),
                MoveRow(105, "Recover", 20, 20, 0, 0, 0, "STA"),
                MoveRow(57, "Surf", 15, 15, 95, 100, 11, "SPE"),
                MoveRow(33, "Tackle", 35, 35, 35, 95, 0, "PHY"),
            ),
        )
        shoot("look-banned.png", paneDp, TrackerState(
            partyCount = 1, party = listOf(mon), inBattle = true, isWildBattle = true,
            healPercent = 0, healCount = 0, enemyTeam = emptyList(), routeName = "Route 1",
        ), moveRules = MoveRule.rules("kaizo", "FRLG", natDex = false, kindId = "firered-u-v10"))
        assert(compose.onAllNodesWithContentDescription("Banned in this run", useUnmergedTree = true)
            .fetchSemanticsNodes().size == 3) { "Giga Drain, Recover and Surf, not Tackle" }
    }

    /** Platinum Kaizo: Roost, U-turn and Defog (Platinum's HM) banned, Brave Bird not. */
    @Test
    fun ds_banned_moves() = marksOn {
        val bird = ndsTracked("Staraptor", 398, 40).copy(moves = listOf(
            com.ironmonone.tracker.nds.NdsMoveInfo("Roost", 0, 0, "FLYING", 10, "STA"),
            com.ironmonone.tracker.nds.NdsMoveInfo("U-turn", 70, 100, "BUG", 20, "PHY"),
            com.ironmonone.tracker.nds.NdsMoveInfo("Brave Bird", 120, 100, "FLYING", 15, "PHY"),
            com.ironmonone.tracker.nds.NdsMoveInfo("Defog", 0, 0, "FLYING", 15, "STA"),
        ))
        shootNds("look-ds-banned.png", paneDp, com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 1, party = listOf(bird), located = true, healPercent = 0, healCount = 0,
        ), moveRules = MoveRule.rules("kaizo", "DPPt", natDex = false, kindId = "platinum-u"))
        assert(compose.onAllNodesWithContentDescription("Banned in this run", useUnmergedTree = true)
            .fetchSemanticsNodes().size == 3) { "Roost, U-turn and Defog, not Brave Bird" }
    }

    /**
     * Banned held items and abilities (Blake, 2026-10-04): FireRed Kaizo, a 450 BST Pokemon with Huge Power holding
     * Leftovers. The item gets the X and so does its physical move (Huge Power bans physical moves; the ability itself is not marked), its
     * special move does not. A tap on the item's X says which rule.
     */
    @Test
    fun rule_marks() = marksOn {
        val mon = tracked("Marill", 183, 20).copy(
            base = BaseStats(100, 90, 90, 60, 60, 50, 11, 11, 1, 2),
            abilityName = "HUGE POWER", itemName = "LEFTOVERS",
            moveNames = listOf("Tackle", "Water Gun"),
            moveRows = listOf(
                MoveRow(33, "Tackle", 35, 35, 35, 95, 0, "PHY"),
                MoveRow(55, "Water Gun", 25, 25, 40, 100, 11, "SPE"),
            ),
        )
        shoot("look-rule-marks.png", paneDp, TrackerState(
            partyCount = 1, party = listOf(mon), inBattle = false, isWildBattle = false, healPercent = 0, healCount = 0, enemyTeam = emptyList(), routeName = "Route 1",
        ), moveRules = MoveRule.rules("kaizo", "FRLG", natDex = false, kindId = "firered-u-v10"),
            ruleRun = RuleMarks.Run("kaizo", natDex = false, family = "FRLG"))
        assert(compose.onAllNodesWithContentDescription("Held item banned in this run", useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        assert(compose.onAllNodesWithContentDescription("Ability banned in this run", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) { "Huge Power itself is not marked" }
        assert(compose.onAllNodesWithContentDescription("Banned in this run", useUnmergedTree = true).fetchSemanticsNodes().size == 1) { "Tackle, not Water Gun" }
        compose.onAllNodesWithContentDescription("Held item banned in this run", useUnmergedTree = true)[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Banned in this run: holding LEFTOVERS. You may hold it in the lab fight.").assertExists()
    }

    /** Off, the switch's default: the same card carries no X at all. */
    @Test
    fun rule_marks_off() {
        TrackerOptions.ruleMarks = false
        val mon = tracked("Marill", 183, 20).copy(
            base = BaseStats(100, 90, 90, 60, 60, 50, 11, 11, 1, 2),
            abilityName = "HUGE POWER", itemName = "LEFTOVERS",
            moveNames = listOf("Recover"),
            moveRows = listOf(MoveRow(105, "Recover", 20, 20, 0, 0, 0, "STA")),
        )
        shoot("look-rule-marks-off.png", paneDp, TrackerState(
            partyCount = 1, party = listOf(mon), inBattle = false, isWildBattle = false, healPercent = 0, healCount = 0, enemyTeam = emptyList(), routeName = "Route 1",
        ), moveRules = MoveRule.rules("kaizo", "FRLG", natDex = false, kindId = "firered-u-v10"),
            ruleRun = RuleMarks.Run("kaizo", natDex = false, family = "FRLG"))
        for (d in listOf("Held item banned in this run", "Ability banned in this run", "Banned in this run"))
            assert(compose.onAllNodesWithContentDescription(d, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) { d }
    }

    /** Platinum Super Kaizo: Battle Armor at 500 BST and a Life Orb, both marked. */
    @Test
    fun ds_rule_marks() = marksOn {
        val crab = ndsTracked("Drapion", 452, 40).let { t ->
            t.copy(mon = t.mon.copy(heldItem = 270), info = t.info?.copy(type1 = "POISON", type2 = "DARK", bst = 500),
                abilityName = "Battle Armor", itemName = "Life Orb") }
        shootNds("look-ds-rule-marks.png", paneDp, com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 1, party = listOf(crab), located = true, healPercent = 0, healCount = 0,
        ), moveRules = MoveRule.rules("superkaizo", "DPPt", natDex = false, kindId = "platinum-u"),
            ruleRun = RuleMarks.Run("superkaizo", natDex = false, family = "DPPt"))
        assert(compose.onAllNodesWithContentDescription("Held item banned in this run", useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        assert(compose.onAllNodesWithContentDescription("Ability banned in this run", useUnmergedTree = true).fetchSemanticsNodes().size == 1)
    }

    @Test
    fun ds_bst_over_the_line() {
        val zek = ndsTracked("Zekrom", 644, 5).let { t ->
            t.copy(info = t.info?.copy(type1 = "DRAGON", type2 = "ELECTRIC", bst = 680, ability1 = "Teravolt", ability2 = "Teravolt"),
                abilityName = "Teravolt", itemName = "---") }
        shootNds("look-ds-bst.png", paneDp, com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 1, party = listOf(zek), located = true,
            healPercent = 0, healCount = 0,
        ), bstLines = BstRule.Lines(599, 599), joined = joinedFresh())
        compose.onNodeWithContentDescription("Over this run's BST limit", useUnmergedTree = true).assertExists()
    }

    @Test
    fun ds_party_card() {
        shootNds("look-ds.png", paneDp, com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 1,
            party = listOf(ndsTracked("Turtwig", 387, 12)),
            located = true,
            healPercent = 30, healCount = 4,
        ))
    }

    // ---- Double battles (rc34) ----------------------------------------------
    //
    // Blake, 2026-10-03: "on doubles how do you cycle through your party pokemon in the tracker?" The swap walks every
    // Pokemon on the field, and the banner says which one the card is, by where it stands on the game's screen.

    private fun doublesState() = TrackerState(
        partyCount = 3,
        party = listOf(tracked("Golisopod", 793, 6), tracked("Bulbasaur", 1, 5), tracked("Charmander", 4, 7)),
        inBattle = true, isWildBattle = false,
        enemy = com.ironmonone.tracker.EnemyInfo(
            species = 245, speciesName = "Suicune", level = 42, curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
            base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2), movesSeen = emptyList(), abilityGuess = "Pressure / Inner Focus",
        ),
        enemyRight = com.ironmonone.tracker.EnemyInfo(
            species = 16, speciesName = "Pidgey", level = 40, curHp = 90, maxHp = 90, type1 = 0, type2 = 2,
            base = BaseStats(40, 45, 40, 56, 35, 35, 0, 2, 1, 2), movesSeen = emptyList(), abilityGuess = "Keen Eye",
        ),
        enemyOnField = listOf(0, 1), doubles = true, ownOnField = 0, ownRightOnField = 2,
        healPercent = 44, healCount = 7, ownRightHeals = com.ironmonone.tracker.HealTotals(61, 20, 7),
        enemyTeam = listOf(true, true, true, true), routeName = "Route 24",
    )

    /** The shared view as a battle leaves it after [swaps] taps of the swap ([stacked]: both cards on screen). */
    private fun primeGba(s: TrackerState, swaps: Int, stacked: Boolean) {
        gbaView.clear()
        gbaView.onRead(s.copy(inBattle = false), autoSwap = false)
        gbaView.onRead(s, autoSwap = false)
        repeat(swaps) { gbaView.swap(s, stacked) }
    }

    /** Portrait, one card: two taps from your left are your right-hand Charmander, its heals its own. */
    @Test
    fun doubles_banner_your_right_hand_pokemon() {
        val s = doublesState()
        primeGba(s, swaps = 2, stacked = false)
        shoot("look-doubles-mine-right.png", portraitDp, s)
        compose.onNodeWithText("MINE ON THE RIGHT").assertIsDisplayed()
        compose.onNodeWithText("Charmander").assertIsDisplayed()
        compose.onNodeWithText("SEE FOE").assertIsDisplayed()
        gbaView.clear()
    }

    /**
     * Landscape, both cards: the first tap puts your right-hand Charmander up, the second your Golisopod again beside the
     * opponent's right-hand Pidgey, which stands on the left of the game's screen.
     */
    @Test
    fun doubles_banner_stacked_landscape() {
        val s = doublesState()
        primeGba(s, swaps = 2, stacked = true)
        shoot("look-doubles-stacked.png", paneDp, s, stackBoth = true)
        compose.onNodeWithText("MINE LEFT, FOE LEFT").assertIsDisplayed()
        compose.onNodeWithText("Pidgey").assertIsDisplayed()
        compose.onNodeWithText("Golisopod").assertIsDisplayed()
        gbaView.clear()
    }

    /** DS, one card: Platinum's double battle, one tap from your left is your right-hand Pokemon. */
    @Test
    fun ds_doubles_banner() {
        val left = ndsTracked("Turtwig", 387, 12)
        val right = ndsTracked("Chimchar", 390, 11).let { it.copy(mon = it.mon.copy(pid = 0x2222_2222)) }
        val foes = listOf(ndsTracked("Starly", 396, 10), ndsTracked("Bidoof", 399, 9))
        val s = com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 2, party = listOf(left, right), located = true, inBattle = true, enemy = foes[0],
            healsPid = left.mon.pid, playerBattlers = listOf(left, right), enemyBattlers = foes,
        )
        dsView.clear(); dsView.forAttempt(2); dsView.onRead(s); dsView.swap(s, allowed = true)
        shootNds("look-ds-doubles.png", portraitDp, s)
        compose.onNodeWithText("MINE ON THE RIGHT").assertIsDisplayed()
        compose.onNodeWithText("SEE FOE").assertIsDisplayed()
        dsView.clear()
    }

    // ---- A crowded banner (rc34.1) ----------------------------------------------
    //
    // Blake, 2026-10-03, a Nat. Dex Emerald double battle with the tracker docked in a narrow column: "Something bad is
    // happening with double battles". SEE MINE, SETUP and the menu left the words a sliver, the side spelled itself a
    // letter to a line and TRAINER BATTLE did not show. The words now go under the buttons, whole (BannerFit).

    /** The menu button's 44 dp box, as landscape puts it after SETUP. */
    private val menuBox: @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.width(44.dp).fillMaxHeight())
    }

    /** A docked column of 181 dp, as in Blake's screenshot: SEE FOE, SETUP and the menu beside the words. */
    @Test
    fun doubles_banner_narrow_docked() {
        val s = doublesState()
        primeGba(s, swaps = 2, stacked = true)
        shoot("look-doubles-narrow-181.png", 181, s, stackBoth = true, onGear = {}, trailing = menuBox)
        compose.onNodeWithText("TRAINER BATTLE").assertIsDisplayed()
        compose.onNodeWithText("MINE LEFT, FOE LEFT").assertIsDisplayed()
        gbaView.clear()
    }

    /** The floating window at its narrowest, 160 dp: SETUP but no menu, which is in the window's title bar. */
    @Test
    fun doubles_banner_floating_narrowest() {
        val s = doublesState()
        primeGba(s, swaps = 2, stacked = false)
        shoot("look-doubles-narrow-160.png", 160, s, onGear = {})
        // Without the menu there is room beside the buttons for the short words.
        compose.onNodeWithText("TRAINER").assertIsDisplayed()
        compose.onNodeWithText("MINE RIGHT").assertIsDisplayed()
        gbaView.clear()
    }

    @Test
    fun ds_doubles_banner_narrow_docked() {
        val left = ndsTracked("Turtwig", 387, 12)
        val right = ndsTracked("Chimchar", 390, 11).let { it.copy(mon = it.mon.copy(pid = 0x2222_2222)) }
        val foes = listOf(ndsTracked("Starly", 396, 10), ndsTracked("Bidoof", 399, 9))
        val s = com.ironmonone.tracker.nds.NdsTrackerState(
            partyCount = 2, party = listOf(left, right), located = true, inBattle = true, enemy = foes[0],
            healsPid = left.mon.pid, playerBattlers = listOf(left, right), enemyBattlers = foes,
        )
        dsView.clear(); dsView.forAttempt(2); dsView.onRead(s); dsView.swap(s, allowed = true)
        shootNds("look-ds-doubles-narrow.png", 181, s, onGear = {}, trailing = menuBox)
        compose.onNodeWithText("TRAINER BATTLE").assertIsDisplayed()
        compose.onNodeWithText("MINE ON THE RIGHT").assertIsDisplayed()
        dsView.clear()
    }

    /**
     * The floating window's wide view (TrackerWideView): your card wide on the left, the opponent's stacked beside it.
     * It draws the same cards, so a Tracker Setup switch turned off is gone here too: the catch rate is the proof.
     */
    @Test
    fun wide_view_honours_the_switches() {
        val battle = TrackerState(
            partyCount = 1,
            party = listOf(tracked("Golisopod", 793, 6)),
            inBattle = true, isWildBattle = true, catchPercent = 33,
            enemy = com.ironmonone.tracker.EnemyInfo(
                species = 245, speciesName = "Suicune", level = 42,
                curHp = 118, maxHp = 155, type1 = 11, type2 = 11,
                base = BaseStats(100, 75, 115, 85, 90, 115, 11, 11, 1, 2),
                movesSeen = listOf("Surf"),
                moveRows = listOf(MoveRow(57, "Surf", 15, null, 95, 100, 11, "SPE")),
                abilityGuess = "Pressure / Inner Focus",
            ),
            healPercent = 44, healCount = 7, routeName = "Route 24",
        )
        val catchOn = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TrackerOptions.showCatchRate = catchOn.value
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.fillMaxHeight()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    // A phone held upright is 411 dp wide; at this density the 760 dp window fits on its screen.
                    androidx.compose.runtime.CompositionLocalProvider(LocalTrackerWideView provides true,
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1.3f, 1f)) {
                        Box(Modifier.width(760.dp)) {
                            TrackerPanel(state = battle, attempt = 2, routeName = "Route 24",
                                spriteFor = { sp -> PcAssets.gbaSprite(ctx, sp) }, stackBoth = true)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Golisopod", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Suicune", substring = true).assertIsDisplayed()
        compose.onNodeWithText("to catch", substring = true).assertIsDisplayed()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "look-wide.png").outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        catchOn.value = false
        compose.mainClock.advanceTimeBy(500)
        check(compose.onAllNodes(androidx.compose.ui.test.hasText("to catch", substring = true)).fetchSemanticsNodes().isEmpty()) {
            "the catch rate is switched off but the wide view still shows it"
        }
        TrackerOptions.showCatchRate = true
    }
}
