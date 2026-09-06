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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
    ) {
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    TrackerPanel(
                        state = state,
                        onFlee = {},
                        attempt = 2,
                        routeName = "Viridian Forest",
                        // The real bundled pack, so this also proves the art
                        // actually loads and lands in the sprite box.
                        spriteFor = { sp -> PcAssets.gbaSprite(ctx, sp) },
                        stackBoth = stackBoth,
                    )
                }
            }
        }
        compose.waitForIdle()
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
                        state = live.value, onFlee = {}, attempt = 2,
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

    private fun shootNds(name: String, widthDp: Int, state: com.ironmonone.tracker.nds.NdsTrackerState) {
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                    NdsTrackerPanel(state = state, attempt = 2)
                }
            }
        }
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val out = File(dir, name)
        out.outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        check(out.length() > 0) { "no image written to " + out.absolutePath }
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
}
