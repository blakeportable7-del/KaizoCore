package com.ironmonone.tracker.nds

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.Outcome
import com.ironmonone.tracker.nuzlocke.RunMeta
import com.ironmonone.tracker.nuzlocke.RunStatus
import com.ironmonone.tracker.nuzlocke.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS side of the Nuzlocke rules (2026-09-30): the adapter that turns the DS tracker's state into the engine's
 * snapshot, its fallbacks where a DS game does not say something, and the data it reads (level caps, statics, species)
 * against the tracker's own tables for the same games.
 */
class NdsNuzlockeTest {

    private val allGames = listOf(
        "Pokemon Diamond", "Pokemon Pearl", "Pokemon Platinum", "Pokemon HeartGold", "Pokemon SoulSilver",
        "Pokemon Black", "Pokemon White", "Pokemon Black 2", "Pokemon White 2",
    )

    // ---------------------------------------------------------------- which game

    @Test
    fun `each DS game is known by the name the tracker gives it, with its family, table and keys`() {
        val want = mapOf(
            "Pokemon Diamond" to Triple(NuzlockeSystem.GEN4, "dp", listOf("d", "dp")),
            "Pokemon Pearl" to Triple(NuzlockeSystem.GEN4, "dp", listOf("p", "dp")),
            "Pokemon Platinum" to Triple(NuzlockeSystem.GEN4, "pt", listOf("pt")),
            "Pokemon HeartGold" to Triple(NuzlockeSystem.GEN4, "hgss", listOf("hg", "hgss")),
            "Pokemon SoulSilver" to Triple(NuzlockeSystem.GEN4, "hgss", listOf("ss", "hgss")),
            "Pokemon Black" to Triple(NuzlockeSystem.GEN5, "bw", listOf("b", "bw")),
            "Pokemon White" to Triple(NuzlockeSystem.GEN5, "bw", listOf("w", "bw")),
            "Pokemon Black 2" to Triple(NuzlockeSystem.GEN5, "b2w2", listOf("b2", "b2w2")),
            "Pokemon White 2" to Triple(NuzlockeSystem.GEN5, "b2w2", listOf("w2", "b2w2")),
        )
        assertEquals(allGames.toSet(), want.keys)
        for ((name, w) in want) {
            val g = assertNotNull(NdsNuzlocke.game(name), name)
            assertEquals(w.first, g.system, name); assertEquals(w.second, g.capsGame, name); assertEquals(w.third, g.keys, name)
            assertEquals(NdsLogData.gameNamed(name)!!.trainerTable, g.trainerTable, "$name: the tracker's own trainer group table")
            assertTrue(g.trainerTable != 0L)
            assertTrue(LevelCapTable.standard(g.capsGame, g.system).bosses.isNotEmpty(), "$name: level caps")
        }
        assertEquals(NdsNuzlocke.game("Pokemon Platinum")!!.capsGame, NdsNuzlocke.game("  pokemon platinum ")!!.capsGame, "case and spaces do not matter")
        assertNull(NdsNuzlocke.game("Pokemon Emerald"))
        assertNull(NdsNuzlocke.game(""))
    }

    @Test
    fun `every game the DS tracker can name is one this reads`() {
        for (g in NdsLogData.games) assertNotNull(NdsNuzlocke.game(g.name), g.name)
    }

    // ---------------------------------------------------------------- the data against the tracker's own tables

    @Test
    fun `each gym leader of the caps is the tracker's gym leader for that game, on the same badge`() {
        for (name in allGames) {
            val g = NdsNuzlocke.game(name)!!
            val caps = LevelCapTable.standard(g.capsGame, g.system)
            for (row in NdsLogData.groupRows(g.trainerTable).filter { it.type == NdsLogData.GYM_LEADERS }) {
                val boss = caps.bosses.firstOrNull { b -> row.ids.any { it in b.trainerIds } }
                assertNotNull(boss, "$name: ${row.label} (${row.ids}) is in the caps")
                // The Kanto leaders of HeartGold and SoulSilver are numbered 1 to 8 in a group of their own; the tracker's badge word has them in bits 8 to 15.
                val bit = row.badge!! - 1 + if (row.groupName.startsWith("Kanto")) 8 else 0
                assertEquals(bit, boss.badge, "$name: ${row.label}'s badge bit")
                assertTrue(row.label.startsWith(boss.label.substringBefore(' ').substringBefore(',')) || boss.label.contains(row.label.substringBefore(' ')), "$name: ${row.label} is ${boss.label}")
            }
        }
    }

    @Test
    fun `the Elite Four and the last fight of the caps are the tracker's for that game`() {
        for (name in allGames) {
            val g = NdsNuzlocke.game(name)!!
            val caps = LevelCapTable.standard(g.capsGame, g.system)
            // HeartGold and SoulSilver keep Red and the Rocket executives in the same group; the first five battles are the League.
            val league = NdsLogData.groupRows(g.trainerTable).filter { it.groupName.startsWith("Elite 4") }
            assertTrue(league.size >= 5, name)
            for (row in league) {
                val boss = caps.bosses.firstOrNull { b -> row.ids.any { it in b.trainerIds } }
                assertNotNull(boss, "$name: ${row.label} (${row.ids})")
                // Black and White count N as the fifth League battle; the caps keep him as the fight before Ghetsis.
                if (row.battle <= 5) assertTrue(boss.kind == "e4" || boss.kind == "champion" || (boss.kind == "post" && row.label == "N"), "$name: ${row.label} is a ${boss.kind}")
            }
        }
    }

    @Test
    fun `the tracker's own final fight is the caps' last fight, except in HeartGold and SoulSilver where Lance comes first`() {
        for (map in NdsGameMap.ALL) {
            val name = NdsLogData.gameFor(map, "")!!.name
            val caps = LevelCapTable.standard(NdsNuzlocke.game(name)!!.capsGame, NdsNuzlocke.game(name)!!.system)
            val last = caps.bosses.first { it.kind == "champion" }
            if (map === NdsGameMap.HGSS) {
                assertEquals("Lance", last.label)
                assertTrue(map.finalTrainerId in caps.byKey("red")!!.trainerIds, "the tracker's final fight is Red's")
            } else assertTrue(map.finalTrainerId in last.trainerIds, "$name: ${last.label} ${last.trainerIds} against ${map.finalTrainerId}")
            for (lab in map.labTrainerIds) assertNull(caps.keyOfTrainer(lab).takeIf { it != null && caps.byKey(it)!!.kind != "rival" }, "$name: the lab fight $lab is a rival's")
        }
    }

    @Test
    fun `every static place is a name the tracker gives an area, spelled as the tracker spells it`() {
        // The adapter hands the tracker's own area name to the statics table, so a row has to use that spelling, typos and all.
        fun names(vararg files: String): Set<String> = files.flatMap { f -> rows(f).filter { it[0].toIntOrNull() != null }.map { it[1].trim() } }.toSet()
        val gen5 = names("/gen5/locations-bw.tsv", "/gen5/locations-b2w2.tsv")
        val gen5Rows = rows("/nuzlocke/statics-gen5.tsv")
        assertTrue(gen5Rows.size >= 30)
        for (r in gen5Rows) assertTrue(r[1] in gen5, "Generation 5 static place ${r[1]} is not an area of the tracker")
        val gen4 = names("/gen4/locations-pt.tsv", "/gen4/locations-hgss.tsv")
        val gen4Rows = rows("/nuzlocke/statics-gen4.tsv")
        assertTrue(gen4Rows.size >= 30)
        for (r in gen4Rows) assertTrue(r[1] in gen4, "Generation 4 static place ${r[1]} is not an area of the tracker")
    }

    private fun rows(path: String): List<List<String>> =
        NdsNuzlockeTest::class.java.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('	') }.toList()
        } ?: emptyList()

    // ---------------------------------------------------------------- the snapshot

    private fun mon(
        species: Int, level: Int = 5, hp: Int = 20, max: Int = 20, pid: Long = 0x1234_5678L, otId: Int = 1234, otSid: Int = 5678,
        isEgg: Boolean = false, shiny: Boolean = false, named: Boolean = false, nickname: String = "",
    ) = Gen4.Mon(
        pid = pid, species = species, heldItem = 0, abilityId = 0, level = level, curHp = hp, maxHp = max,
        atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10, moves = List(4) { 0 }, pp = List(4) { 0 }, ppUps = List(4) { 0 },
        ivs = List(6) { 0 }, shiny = shiny, nature = 0, isEgg = isEgg, otId = otId, otSid = otSid, nicknamed = named, nickname = nickname,
    )

    private fun tracked(m: Gen4.Mon, name: String, info: NdsSpeciesInfo? = null) =
        NdsTrackedMon(mon = m, speciesName = name, info = info, abilityName = "-", itemName = "-", moves = emptyList())

    private fun state(
        game: String = "Pokemon Platinum", party: List<NdsTrackedMon> = listOf(tracked(mon(387), "TURTWIG")), inBattle: Boolean = false, wild: Boolean = false,
        enemy: NdsTrackedMon? = null, area: String = "Route 201", badges: Int = 0, trainer: Int = 0, runOver: NdsRunOver? = null,
        lastEnemy: NdsTrackedMon? = null, located: Boolean = true, fetched: Boolean = inBattle,
    ) = NdsTrackerState(
        partyCount = party.size, party = party, located = located, inBattle = inBattle, isWildBattle = wild, enemy = enemy, badges = badges,
        areaName = area, mapId = 339, gameName = game, enemyTrainerId = trainer, runOver = runOver, lastBattleEnemy = lastEnemy,
        battleFetched = fetched,
    )

    @Test
    fun `nothing is given for a party that is not located, and nothing for a game this does not know`() {
        assertNull(NdsNuzlocke.snapshot(state(located = false)))
        assertNull(NdsNuzlocke.snapshot(state(game = "Pokemon Emerald")))
        assertNull(NdsNuzlocke.snapshot(state(game = "")))
        assertNotNull(NdsNuzlocke.snapshot(state()))
    }

    @Test
    fun `a party member carries its personality value, level, HP, gender, types and its nickname or a stand-in`() {
        val s = state(party = listOf(
            tracked(mon(387, pid = 0x0000_0010L, named = true), "TURTWIG"),                              // low byte 16 under Turtwig's 31: female
            tracked(mon(25, pid = 0x0000_00FFL, level = 12, hp = 7, max = 30), "PIKACHU", NdsSpeciesInfo("Pikachu", "FIRE", "FIRE", 320, "-", "-")),   // a randomized Pikachu
            tracked(mon(81, pid = 0x55L), "MAGNEMITE"),
            tracked(mon(1, pid = 0x1FL, isEgg = true), "BULBASAUR"),
        ))
        val snap = assertNotNull(NdsNuzlocke.snapshot(s))
        assertEquals(listOf(0x10L, 0xFFL, 0x55L, 0x1FL), snap.party.map { it.id })
        assertEquals(listOf(Gender.FEMALE, Gender.MALE, null, Gender.MALE), snap.party.map { it.gender }, "ratio 31 against the low byte, 127, none, 31")
        assertEquals(listOf(12), snap.party[0].types, "Turtwig is Grass, from the species table")
        assertEquals(listOf(10), snap.party[1].types, "the run's own types first: a randomized Pikachu is Fire, the species table says Electric")
        assertEquals(listOf(13, 8), snap.party[2].types, "Magnemite: Electric and Steel")
        assertEquals(listOf("TURTWIG (named)", "", "", ""), snap.party.map { it.nickname }, "a Generation 4 nickname is not decoded: a stand-in when there is one")
        assertEquals(listOf(5, 12, 5, 5), snap.party.map { it.level })
        assertEquals(7, snap.party[1].hp); assertEquals(30, snap.party[1].maxHp)
        assertTrue(snap.party[3].isEgg)
        assertEquals(4, snap.partyCount)
    }

    @Test
    fun `a Generation 4 nickname is read too, and the stand-in only covers a name that could not be`() {
        val s = state(party = listOf(
            tracked(mon(387, named = true, nickname = "Sprout"), "TURTWIG"),
            tracked(mon(388, named = false, nickname = "Grotle"), "GROTLE"),        // the default name the game keeps for an unnamed one
            tracked(mon(389, named = true, nickname = ""), "TORTERRA"),
        ))
        val party = assertNotNull(NdsNuzlocke.snapshot(s)).party
        assertEquals(listOf("Sprout", "Grotle", "TORTERRA (named)"), party.map { it.nickname })
    }

    @Test
    fun `a Generation 5 nickname is read as it is`() {
        val s = state(game = "Pokemon Black 2", party = listOf(tracked(mon(495, named = true, nickname = "Sprout"), "SNIVY")))
        assertEquals("Sprout", assertNotNull(NdsNuzlocke.snapshot(s)).party.single().nickname)
    }

    @Test
    fun `a wild enemy is judged for shininess against the trainer ids of the party, and a wild Pokemon's own fields are not the player's`() {
        // The party's ids are 1234 and 5678. A personality value of (1234 xor 5678) in the low half and 0 above it makes the sum 0: shiny.
        val shinyPid = (1234 xor 5678).toLong()
        val plain = 0x1111_2222L
        val party = listOf(tracked(mon(387), "TURTWIG"), tracked(mon(388, pid = 0x77L), "GROTLE"))
        fun snap(pid: Long) = assertNotNull(NdsNuzlocke.snapshot(state(party = party, inBattle = true, wild = true,
            enemy = tracked(mon(396, pid = pid, otId = 999, otSid = 999), "STARLY")))).enemy!!
        assertTrue(snap(shinyPid).shiny)
        assertFalse(snap(plain).shiny)
        // The enemy's own trainer ids would make this shiny if they were used: they are not.
        val own = mon(396, pid = 0L, otId = 0, otSid = 0)
        assertFalse(assertNotNull(NdsNuzlocke.snapshot(state(party = party, inBattle = true, wild = true, enemy = tracked(own, "STARLY")))).enemy!!.shiny)
    }

    @Test
    fun `a wild battle is wild only with no trainer, and a trainer's Pokemon carries no gender-free wild flags`() {
        val foe = tracked(mon(396, pid = 0x20L), "STARLY")
        val wild = assertNotNull(NdsNuzlocke.snapshot(state(inBattle = true, wild = true, enemy = foe)))
        assertTrue(wild.wild); assertTrue(wild.inBattle); assertNull(wild.opponent)
        assertEquals(Method.WALK, wild.method, "how a wild battle began is not read")
        val trainer = assertNotNull(NdsNuzlocke.snapshot(state(inBattle = true, wild = false, enemy = foe, trainer = 246)))
        assertFalse(trainer.wild)
        assertNotNull(trainer.opponent)
        // A wild flag with a trainer id is not a wild battle.
        assertFalse(assertNotNull(NdsNuzlocke.snapshot(state(inBattle = true, wild = true, enemy = foe, trainer = 246))).wild)
    }

    @Test
    fun `a boss is named for the caps and the game, and an unknown trainer is a trainer`() {
        fun opp(game: String, id: Int) = assertNotNull(NdsNuzlocke.snapshot(state(game = game, inBattle = true, wild = false, enemy = tracked(mon(396), "STARLY"), trainer = id))).opponent!!
        val roark = opp("Pokemon Platinum", 246)
        assertEquals("Leader Roark", roark.label); assertEquals("Gym", roark.group); assertEquals("gym1", roark.bossKey)
        val cynthia = opp("Pokemon Platinum", 267)
        assertEquals("Champion Cynthia", cynthia.label); assertEquals("Elite4", cynthia.group); assertEquals("champion", cynthia.bossKey)
        assertEquals("Elite Four Aaron", opp("Pokemon Platinum", 261).label)
        val barry = opp("Pokemon Platinum", 850)
        assertEquals("Rival", barry.group); assertTrue(barry.label.startsWith("Rival Barry")); assertEquals("barry1", barry.bossKey)
        val mars = opp("Pokemon Platinum", 295)
        assertEquals("Boss", mars.group); assertTrue(mars.label.startsWith("Boss Mars"))
        val lance = opp("Pokemon HeartGold", 244)
        assertEquals("champion", lance.bossKey); assertEquals("Champion Lance", lance.label)
        assertEquals("Elite Four Shauntal", opp("Pokemon Black 2", 38).label)
        assertEquals("champion", opp("Pokemon Black 2", 341).bossKey)
        val nobody = opp("Pokemon Platinum", 3)
        assertEquals("Other", nobody.group); assertNull(nobody.bossKey)
        assertNull(nobody.maxLevel, "no boss level is read from a DS game: the standard table stands")
    }

    @Test
    fun `the badges say which gyms are beaten, in the game's own order`() {
        val platinum = assertNotNull(NdsNuzlocke.snapshot(state(badges = 0b10001)))
        assertEquals(setOf("gym1", "gym3"), platinum.beaten, "bits 0 and 4: Roark, and Fantina, who is Platinum's third gym")
        val diamond = assertNotNull(NdsNuzlocke.snapshot(state(game = "Pokemon Diamond", badges = 0b10001)))
        assertEquals(setOf("gym1", "gym5"), diamond.beaten, "in Diamond the same bit is the fifth fight")
        assertEquals(255, assertNotNull(NdsNuzlocke.snapshot(state(badges = 255))).badges)
        assertEquals(8, assertNotNull(NdsNuzlocke.snapshot(state(badges = 255))).beaten.size)
    }

    @Test
    fun `the bag is the healing and status items, and the reads that are not there are null`() {
        val potion = NdsHeals.HEALS.first { it.name.equals("Potion", ignoreCase = true) }
        val antidote = NdsHeals.CURES.first { it.name.equals("Antidote", ignoreCase = true) }
        val s = state().copy(healingItems = mapOf(potion.id to 3), statusItems = mapOf(antidote.id to 2))
        val snap = assertNotNull(NdsNuzlocke.snapshot(s))
        assertEquals(mapOf(potion.id to "Potion", antidote.id to "Antidote"), snap.bag!!.mapValues { it.value.name })
        assertEquals(3, snap.bag!!.getValue(potion.id).qty)
        assertNull(snap.ballCount, "the ball count is not read: the rules begin with the first Pokemon")
        assertNull(snap.battleStyleSet); assertNull(snap.turn)
        assertEquals(setOf<Int>(), assertNotNull(NdsNuzlocke.snapshot(state())).bag!!.keys)
        assertNotNull(snap.caps); assertEquals("pt", snap.caps!!.game)
    }

    // ---------------------------------------------------------------- how a battle ended

    @Test
    fun `a battle is won when the last Pokemon of the other side was down, and never known to be by the fallback alone`() {
        val down = tracked(mon(396, hp = 0), "STARLY")
        val up = tracked(mon(396, hp = 9), "STARLY")
        assertEquals(BattleEnd.WON, NdsNuzlocke.battleEnd(state(lastEnemy = down)), "the last enemy at 0 HP")
        assertEquals(BattleEnd.UNKNOWN, NdsNuzlocke.battleEnd(state(lastEnemy = up)), "still standing: a catch, a run or a loaded state")
        assertEquals(BattleEnd.UNKNOWN, NdsNuzlocke.battleEnd(state()), "no battle seen")
        assertEquals(BattleEnd.WON, NdsNuzlocke.battleEnd(state(runOver = NdsRunOver.WON)), "the tracker's own word for its final fight")
        assertEquals(BattleEnd.UNKNOWN, NdsNuzlocke.battleEnd(state(runOver = NdsRunOver.STANDARD)), "a loss is not a win")
        assertEquals(BattleEnd.UNKNOWN, NdsNuzlocke.battleEnd(state(inBattle = true, wild = true, enemy = down, lastEnemy = down)), "while it is on, nothing has ended")
        assertEquals(BattleEnd.UNKNOWN, NdsNuzlocke.battleEnd(state(lastEnemy = tracked(mon(396, hp = 0, max = 0), "STARLY"))), "an enemy with no HP at all was not read")
        assertEquals(BattleEnd.WON, assertNotNull(NdsNuzlocke.snapshot(state(lastEnemy = down))).end)
    }

    // ---------------------------------------------------------------- statics

    @Test
    fun `a wild battle at a place and level in the statics table is a static, and a species check keeps ordinary encounters out`() {
        fun method(game: String, area: String, level: Int, species: Int) = assertNotNull(NdsNuzlocke.snapshot(state(
            game = game, area = area, inBattle = true, wild = true, enemy = tracked(mon(species, level = level, pid = 0x30L), "X"),
        ))).method
        // Black 2: Reshiram or Zekrom at Dragonspiral Tower, and the Cobalion of Route 13, have no species check.
        assertEquals(Method.STATIC, method("Pokemon Black 2", "Dragonspiral Tower", 70, 643))
        assertEquals(Method.STATIC, method("Pokemon Black 2", "Route 13", 45, 638))
        assertEquals(Method.WALK, method("Pokemon Black 2", "Route 13", 44, 638), "another level")
        assertEquals(Method.WALK, method("Pokemon Black 2", "Route 12", 45, 638), "another place")
        // Route 22's level 45 Terrakion is one of the rows whose level an ordinary land encounter has too: it names the species.
        assertEquals(Method.STATIC, method("Pokemon Black 2", "Route 22", 45, 639))
        assertEquals(Method.WALK, method("Pokemon Black 2", "Route 22", 45, 100), "an ordinary Voltorb of that level")
        // Black and White share the bw key, a game's own key is tried first and the family's second.
        assertEquals(Method.STATIC, method("Pokemon White", "Desert Resort", 35, 555))
        assertEquals(Method.WALK, method("Pokemon Platinum", "Desert Resort", 35, 555), "another game's table")
        // Generation 4: Route 209's Spiritomb shares its level with surf and fishing slots, so its row names the species.
        assertEquals(Method.STATIC, method("Pokemon Platinum", "Route 209", 25, 442))
        assertEquals(Method.WALK, method("Pokemon Platinum", "Route 209", 25, 54), "a Psyduck of that level on the water")
        assertEquals(Method.STATIC, method("Pokemon Diamond", "Route 209", 25, 442), "Diamond's row is Platinum's at the same level")
        assertEquals(Method.STATIC, method("Pokemon Platinum", "Old Chateau", 20, 479), "Platinum's Rotom is level 20")
        assertEquals(Method.WALK, method("Pokemon Platinum", "Old Chateau", 15, 479), "and Diamond's is 15")
        assertEquals(Method.STATIC, method("Pokemon Diamond", "Old Chateau", 15, 479))
        assertEquals(Method.STATIC, method("Pokemon HeartGold", "Union Cave", 20, 131), "Lapras on a Friday")
        assertEquals(Method.WALK, method("Pokemon HeartGold", "Union Cave", 20, 19), "a Rattata of B2F")
        assertEquals(Method.STATIC, method("Pokemon SoulSilver", "Burned Tower", 40, 245), "Suicune, and its row names no species")
        // Ho-Oh is level 45 in HeartGold and 70 in SoulSilver, each with a row of its own version.
        assertEquals(Method.STATIC, method("Pokemon HeartGold", "Bell Tower", 45, 250))
        assertEquals(Method.WALK, method("Pokemon HeartGold", "Bell Tower", 70, 250))
        assertEquals(Method.STATIC, method("Pokemon SoulSilver", "Bell Tower", 70, 250))
        assertEquals(Method.WALK, method("Pokemon SoulSilver", "Bell Tower", 45, 250))
    }

    // ---------------------------------------------------------------- through the engine

    private class Run(system: NuzlockeSystem, rules: NuzlockeRules = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)) {
        val ledger = NuzlockeLedger(RunMeta("nz-ds", "ds", "Test", rules, 1_000L).also { it.system = system })
        val engine = NuzlockeEngine(ledger)
        var at = 10_000L
        fun poll(s: Snapshot?) { engine.update(assertNotNull(s), at); at += 700 }
    }

    @Test
    fun `beating a gym leader on Platinum is remembered, and the Champion completes the run only when the last Pokemon was down`() {
        val run = Run(NuzlockeSystem.GEN4)
        val hero = tracked(mon(387, level = 40, hp = 100, max = 100, pid = 0x11L), "TURTWIG")
        val down = tracked(mon(445, level = 62, hp = 0, max = 200, pid = 0x22L), "GARCHOMP")
        val up = tracked(mon(445, level = 62, hp = 80, max = 200, pid = 0x22L), "GARCHOMP")
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero))))
        assertTrue(run.ledger.meta.started)
        // Roark: a fight the party leaves standing, with nothing to say how it ended: the fallback calls it a win.
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), inBattle = true, enemy = tracked(mon(408, level = 14, pid = 0x50L), "CRANIDOS"), trainer = 246)))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), badges = 1)))
        assertEquals(setOf("gym1"), run.ledger.meta.beatenBosses)
        // Cynthia, in a state loaded in the middle of her fight: the last enemy is standing, so the fight is not won.
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), inBattle = true, enemy = up, trainer = 267)))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), lastEnemy = up)))
        assertEquals(RunStatus.ACTIVE, run.ledger.meta.status, "a Champion is never worked out from the party standing")
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), inBattle = true, enemy = up, trainer = 267)))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), lastEnemy = down)))
        assertEquals(RunStatus.COMPLETE, run.ledger.meta.status)
        assertTrue("champion" in run.ledger.meta.beatenBosses)
    }

    @Test
    fun `Lance completes a HeartGold run, though the tracker's own final fight is Red's`() {
        val run = Run(NuzlockeSystem.GEN4)
        val hero = tracked(mon(157, level = 50, hp = 150, max = 150, pid = 0x11L), "TYPHLOSION")
        val dragonite = { hp: Int -> tracked(mon(149, level = 50, hp = hp, max = 160, pid = 0x22L), "DRAGONITE") }
        val game = "Pokemon HeartGold"
        run.poll(NdsNuzlocke.snapshot(state(game = game, party = listOf(hero))))
        run.poll(NdsNuzlocke.snapshot(state(game = game, party = listOf(hero), inBattle = true, enemy = dragonite(160), trainer = 244)))
        run.poll(NdsNuzlocke.snapshot(state(game = game, party = listOf(hero), lastEnemy = dragonite(0))))
        assertEquals(RunStatus.COMPLETE, run.ledger.meta.status)
    }

    @Test
    fun `a DS wild catch and a faint are read from the party and the enemy's HP, with no word from the game`() {
        val run = Run(NuzlockeSystem.GEN4)
        val hero = tracked(mon(387, pid = 0x11L), "TURTWIG")
        val starly = { hp: Int -> tracked(mon(396, level = 3, hp = hp, max = 12, pid = 0x7B2E_9F41L), "STARLY") }
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero))))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero), inBattle = true, wild = true, enemy = starly(12))))
        // The ball catches: the party grows while the battle is still on.
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero, starly(5)), inBattle = true, wild = true, enemy = starly(5))))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero, starly(5)), lastEnemy = starly(5))))
        val enc = run.ledger.areas.getValue("Route 201").encounter!!
        assertEquals(Outcome.CAUGHT, enc.outcome)
        assertEquals(0x7B2E_9F41L, enc.monId)
        // Route 202: a wild Bidoof faints.
        val bidoof = { hp: Int -> tracked(mon(399, level = 3, hp = hp, max = 12, pid = 0x14D6_A8C3L), "BIDOOF") }
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero, starly(5)), area = "Route 202", inBattle = true, wild = true, enemy = bidoof(12))))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero, starly(5)), area = "Route 202", inBattle = true, wild = true, enemy = bidoof(0), lastEnemy = bidoof(0))))
        run.poll(NdsNuzlocke.snapshot(state(party = listOf(hero, starly(5)), area = "Route 202", lastEnemy = bidoof(0))))
        assertEquals(Outcome.FAINTED, run.ledger.areas.getValue("Route 202").encounter!!.outcome)
    }

    @Test
    fun `a battle the tracker has not fetched is no battle to the rules`() {
        // rc33 audit P1 #80: Platinum's catching demonstration on Route 202 is never fetched (the battle's party is not
        // yours), and it took the route's first encounter. #82: a Gen 5 read before the fetch reported a trainer battle.
        val bidoof = tracked(mon(399, pid = 0x202L, level = 2), "BIDOOF")
        val demo = assertNotNull(NdsNuzlocke.snapshot(state(inBattle = true, wild = true, enemy = bidoof, area = "Route 202", fetched = false)))
        assertFalse(demo.inBattle); assertFalse(demo.wild); assertNull(demo.enemy); assertNull(demo.opponent)
        val gen5 = assertNotNull(NdsNuzlocke.snapshot(state(game = "Pokemon Black 2", inBattle = true, wild = false, trainer = 161, fetched = false)))
        assertFalse(gen5.inBattle); assertNull(gen5.opponent)
        // Through the engine: the demonstration, then the player's own first battle on the route.
        val ledger = com.ironmonone.tracker.nuzlocke.NuzlockeLedger(com.ironmonone.tracker.nuzlocke.RunMeta("nz", "lib", "Platinum",
            com.ironmonone.tracker.nuzlocke.NuzlockeRules.forPreset(com.ironmonone.tracker.nuzlocke.NuzlockePreset.STANDARD), 1_000L)
            .also { it.system = com.ironmonone.tracker.nuzlocke.NuzlockeSystem.GEN4 })
        val engine = com.ironmonone.tracker.nuzlocke.NuzlockeEngine(ledger)
        var at = 10_000L
        fun feed(s: NdsTrackerState) { engine.update(assertNotNull(NdsNuzlocke.snapshot(s)), at); at += 700 }
        feed(state(area = "Route 202"))
        feed(state(inBattle = true, wild = true, enemy = bidoof, area = "Route 202", fetched = false))
        feed(state(area = "Route 202"))
        val starly = tracked(mon(396, pid = 0x396L, level = 3), "STARLY")
        feed(state(inBattle = true, wild = true, enemy = starly, area = "Route 202"))
        feed(state(area = "Route 202", lastEnemy = tracked(mon(396, pid = 0x396L, level = 3, hp = 0), "STARLY")))
        assertEquals("STARLY", ledger.areas.values.single().encounter?.speciesName?.uppercase(), "the player's own battle is the first encounter")
    }
}
