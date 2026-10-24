package com.ironmonone.pcui

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File

/**
 * Sample panels, the fixture the app's TrackerLookTest draws on the phone, rendered by [PcPanel] to PNG at game-pixel
 * size (and 3x, as BizHawk shows them at a 3x window): ./gradlew :pcui:samples. For a look by eye.
 */
object Samples {
    private fun mon(species: Int, level: Int, cur: Int, max: Int) = PokemonDecoder.Mon(
        pid = 0x12345678, level = level, nickname = "", species = species,
        heldItem = 0, friendship = 70,
        moves = listOf(84, 45, 33, 39), pp = listOf(30, 40, 35, 20),
        ivs = List(6) { 20 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 3, shiny = false, status = 0,
        curHp = cur, maxHp = max,
        atk = 29, def = 29, spe = 13, spAtk = 26, spDef = 9,
    )

    fun tracked(name: String, species: Int, level: Int) = TrackedMon(
        mon = mon(species, level, 22, 29),
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

    val party = TrackerState(
        partyCount = 1, party = listOf(tracked("Golisopod", 793, 6)),
        inBattle = false, isWildBattle = false, healPercent = 110, healCount = 3, routeName = "Viridian Forest",
    )

    /** In the lab with no Pokemon yet (TrackerScreen.canShowBallPicker): the start screen with the ball picker. */
    val lab = TrackerState(partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false, inLab = true, badgeSet = "HNS", routeName = "Elm's Lab")

    val enemy = TrackerState(
        partyCount = 1, party = listOf(tracked("Golisopod", 793, 6)),
        inBattle = true, isWildBattle = false,
        enemy = EnemyInfo(
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
    )

    /** A wild battle, as the streamer's reference frame: a new encounter on a route, the catch rate in the header. */
    val wild = enemy.copy(
        isWildBattle = true, enemyTeam = emptyList(), catchPercent = 33, routeName = "Petalburg Woods",
        enemy = enemy.enemy!!.copy(species = 156, speciesName = "Quilava", level = 8, type1 = 10, type2 = 10, statusCondition = "",
            moveRows = listOf(MoveRow(583, "Play Rough", 9, null, 90, 90, 18, "PHY"))),
    )

    /** A wild Hoothoot whose ability the battle revealed: "Drought /" over "?" (the streamer's frame). */
    val revealed = wild.copy(
        routeName = "Route 1", catchPercent = 41,
        // Hoothoot's own base stats (BST 262) and its evolution at 20, as the game's tables give them.
        enemy = wild.enemy!!.copy(species = 163, speciesName = "Hoothoot", level = 5, type1 = 0, type2 = 2, moveRows = emptyList(),
            base = BaseStats(60, 30, 30, 50, 36, 56, 0, 2, 1, 2), evo = com.ironmonone.tracker.EvoText.Label("20", com.ironmonone.tracker.EvoText.Tone.PLAIN)),
        abilitiesRevealed = listOf(163 to "Drought"),
    )

    /** Stat marks the staged endings grade (Grade my notes): species to marks in HP, ATK, DEF, SPA, SPD, SPE (1 +, 2 --, 3 =). */
    val demoMarks: Map<Int, IntArray> = mapOf(245 to intArrayOf(1, 3, 1, 1, 1, 0), 156 to intArrayOf(2, 1, 2, 1, 3, 0), 163 to intArrayOf(1, 2, 2, 2, 1, 0))

    /** Its learnset's levels: 13 level-up moves, the next at 10 (the reference's "Moves 0/13 (10)"). */
    val levels: (Int) -> List<Int> = { listOf(10, 13, 17, 20, 24, 28, 31, 35, 38, 42, 46, 49, 53) }

    /** The run lost in that battle: the lead at 0 HP, the tracker's read saying so. */
    val over = enemy.copy(party = listOf(tracked("Golisopod", 793, 6).let { it.copy(mon = it.mon.copy(curHp = 0)) }), gameOver = com.ironmonone.tracker.GameOver.LOST)

    /** The run won: the champion beaten. */
    val won = party.copy(gameOver = com.ironmonone.tracker.GameOver.WON)
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/png").apply { mkdirs() }
    val files = File(out, "files").apply { mkdirs() }
    val inputs = listOf(
        "start" to PcPanel.Input(null, 740, title = "Heart & Soul (KaizoCore)"),
        "party" to PcPanel.Input(Samples.party, 740),
        "enemy" to PcPanel.Input(Samples.enemy, 740, routeName = "Route 24", moveLevels = Samples.levels),
        "wild" to PcPanel.Input(Samples.wild, 740, routeName = "Petalburg Woods", moveLevels = Samples.levels),
        "battle-own" to PcPanel.Input(Samples.enemy, 740, routeName = "Route 24", showOwn = true),
        "settings" to PcPanel.Input(Samples.party, 740),
        "settings-display" to PcPanel.Input(Samples.party, 740),
        "settings-theme" to PcPanel.Input(Samples.party, 740),
    )
    // At 1x (the game-pixel picture) and at the window scale asked for (-Dpcui.scale, 3 by default: a 3x window).
    val scales = listOf(1f, System.getProperty("pcui.scale")?.toFloatOrNull() ?: 3f).distinct()
    for (scale in scales) PcPanel.Renderer(files, PcPanel.Input(null, 0), scale).use { r ->
        for ((name, input) in inputs) {
            r.settings.open = name.startsWith("settings")
            r.settings.tab = when (name) { "settings-display" -> PcSettingsScreen.Tab.DISPLAY; "settings-theme" -> PcSettingsScreen.Tab.THEME; else -> PcSettingsScreen.Tab.BATTLE }
            r.render(input)
            val times = (1..20).map { val t0 = System.nanoTime(); r.render(input); (System.nanoTime() - t0) / 1e6 }.sorted()
            val png = r.render(input)
            val suffix = if (scale == 1f) "" else "-${scale.toInt()}x"
            File(out, "$name$suffix.png").writeBytes(png)
            println("%s%s: %dx%d, render and PNG: median %.1f ms, max %.1f ms of 20".format(name, suffix, r.width, r.height, times[10], times.last()))
        }
    }
}
