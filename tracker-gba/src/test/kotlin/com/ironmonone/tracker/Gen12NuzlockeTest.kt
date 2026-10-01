package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Game Boy side of the Nuzlocke adapter with no ROM and no memory (2026-09-30): the formulas the games use for what
 * their own data does not hold (a Pokemon's id, gender, shininess), the reading of how a battle ended, and the mapping
 * of a tracker state to the engine's snapshot.
 */
class Gen12NuzlockeTest {

    private fun reads(
        generation: Int = 2, game: String = "c", keys: List<String> = listOf("c"), place: String? = "Route 29", detail: String? = "Route 29",
        playerId: Int = 0x1234, enemyDvs: Int = -1, enemyHpLast: Int = -1, lastWild: Boolean = true, battleResult: Int = -1,
        escaped: Boolean = false, captured: Boolean = false, battleType: Int = 0, ghost: Boolean = false, surfing: Boolean = false,
    ) = GbNuzReads(
        generation = generation, game = game, gameKeys = keys, place = place, detail = detail, playerId = playerId, enemyDvs = enemyDvs,
        enemyHpLast = enemyHpLast, lastWild = lastWild, battleResult = battleResult, escaped = escaped, captured = captured,
        battleType = battleType, ghost = ghost, surfing = surfing,
    )

    private fun gen1(battleResult: Int, lastWild: Boolean = true, hp: Int = -1, escaped: Boolean = false, captured: Boolean = false) =
        reads(1, "rb", listOf("rb"), battleResult = battleResult, lastWild = lastWild, enemyHpLast = hp, escaped = escaped, captured = captured)

    private fun gen2(battleResult: Int, lastWild: Boolean = true, hp: Int = -1, captured: Boolean = false) =
        reads(2, battleResult = battleResult, lastWild = lastWild, enemyHpLast = hp, captured = captured)

    // ---------------------------------------------------------------- a Pokemon's id

    @Test
    fun `an id is the trainer id above the DVs, and nothing else about the Pokemon is in it`() {
        assertEquals(0x1234_ABCDL, Gen12Nuzlocke.id(0x1234, 0xABCD))
        assertEquals(0xFFFF_FFFFL, Gen12Nuzlocke.id(0xFFFF, 0xFFFF), "no sign extension")
        assertEquals(0L, Gen12Nuzlocke.id(0, 0))
        assertEquals(Gen12Nuzlocke.id(0x1234, 0xABCD), Gen12Nuzlocke.id(0x11234, 0x1ABCD), "16 bits each")
        assertNotEquals(Gen12Nuzlocke.id(0x1234, 0xABCD), Gen12Nuzlocke.id(0x1235, 0xABCD))
        assertNotEquals(Gen12Nuzlocke.id(0x1234, 0xABCD), Gen12Nuzlocke.id(0x1234, 0xABCE))
    }

    @Test
    fun `a party member's id is its own trainer id and DVs, whatever its species is`() {
        // The tracker keeps the original trainer id in the low half of its stand-in personality value and the species above it.
        fun mon(species: Int, ot: Int, atk: Int, def: Int, spd: Int, spc: Int) = PokemonDecoder.Mon(
            pid = (species.toLong() shl 16) or ot.toLong(), level = 5, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0), ivs = listOf(0, atk, def, spd, spc, spc), evs = List(6) { 0 },
            ppUps = listOf(0, 0, 0, 0), abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 10, maxHp = 10,
            atk = 1, def = 1, spe = 1, spAtk = 1, spDef = 1,
        )
        val charmander = mon(4, 0x2B0E, 10, 5, 11, 6)
        val charmeleon = mon(5, 0x2B0E, 10, 5, 11, 6)
        assertEquals(0xA5B6, Gen12Nuzlocke.dvsOf(charmander), "attack, defense, speed and special, a nibble each")
        assertEquals(Gen12Nuzlocke.partyId(charmander), Gen12Nuzlocke.partyId(charmeleon), "evolving does not change who it is")
        assertEquals(0x2B0E_A5B6L, Gen12Nuzlocke.partyId(charmander))
        assertNotEquals(Gen12Nuzlocke.partyId(charmander), Gen12Nuzlocke.partyId(mon(4, 0x2B0E, 10, 5, 11, 7)), "one DV apart")
        assertNotEquals(Gen12Nuzlocke.partyId(charmander), Gen12Nuzlocke.partyId(mon(4, 0x2B0F, 10, 5, 11, 6)), "another trainer's")
    }

    @Test
    fun `a wild enemy's id is the id its catch will have, and a read that failed gets an id nothing else has`() {
        val g = reads(playerId = 0x2B0E, enemyDvs = 0xA5B6)
        assertEquals(Gen12Nuzlocke.id(0x2B0E, 0xA5B6), Gen12Nuzlocke.enemyId(g))
        assertEquals(0L, Gen12Nuzlocke.enemyId(reads(playerId = 0x2B0E, enemyDvs = -1)), "outside a battle")
        val noOwner = Gen12Nuzlocke.enemyId(reads(playerId = -1, enemyDvs = 0xA5B6))
        assertTrue(noOwner > 0xFFFF_FFFFL, "no trainer id: above every id a party member can have")
        assertNotEquals(noOwner, Gen12Nuzlocke.enemyId(reads(playerId = -1, enemyDvs = 0xA5B7)))
    }

    // ---------------------------------------------------------------- what the games' own formulas say

    @Test
    fun `Generation 2 gender is the attack and speed DVs against the species' ratio, as the game works it out`() {
        fun dvs(atk: Int, spd: Int) = (atk shl 12) or (7 shl 8) or (spd shl 4) or 7
        // A ratio of 31 (Charmander, one female in eight): the number is (attack << 4) | speed, male when the ratio is below it.
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 31, dvs(15, 15)))
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 31, dvs(0, 0)))
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 31, dvs(1, 15)), "b = 31: not above the ratio, so female")
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 31, dvs(2, 0)), "b = 32")
        // Even odds (127): the boundary is exactly between 127 and 128.
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 127, dvs(7, 15)), "b = 127")
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 127, dvs(8, 0)), "b = 128")
        // The speed DV is the low nibble of b, not the high one.
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 127, dvs(8, 15)))
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 127, dvs(7, 0)))
        // The single-sex species and the genderless.
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 0, dvs(0, 0)), "always male")
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 254, dvs(15, 15)), "always female")
        assertNull(Gen12Nuzlocke.gender(2, 255, dvs(15, 15)), "genderless")
        // Generation 1 has no genders at all, and an unknown ratio or unread DVs give none.
        assertNull(Gen12Nuzlocke.gender(1, 31, dvs(15, 15)))
        assertNull(Gen12Nuzlocke.gender(2, null, dvs(15, 15)))
        assertNull(Gen12Nuzlocke.gender(2, 31, -1))
        assertNull(Gen12Nuzlocke.gender(2, 300, dvs(15, 15)))
    }

    @Test
    fun `the speed DV counts in a gender, which only a ratio between the game's own would show`() {
        // Every ratio a game uses is one below a multiple of 16, so the Attack DV alone decides. A made-up ratio needs both.
        fun dvs(atk: Int, spd: Int) = (atk shl 12) or (15 shl 8) or (spd shl 4) or 15
        assertEquals(Gender.MALE, Gen12Nuzlocke.gender(2, 100, dvs(6, 5)), "b = 101 against 100")
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 100, dvs(6, 4)), "b = 100 is not above it")
        assertEquals(Gender.FEMALE, Gen12Nuzlocke.gender(2, 100, dvs(5, 15)), "b = 95")
        for (ratio in listOf(31, 63, 127, 191)) for (atk in 0..15) for (spd in 0..15) {
            val male = Gen12Nuzlocke.gender(2, ratio, dvs(atk, spd)) == Gender.MALE
            assertEquals(male, atk >= (ratio + 1) / 16, "ratio $ratio, attack $atk, speed $spd")
        }
    }

    @Test
    fun `Generation 2 shininess is a defense, speed and special of 10 with bit 1 of the attack DV set`() {
        fun dvs(atk: Int, def: Int = 10, spd: Int = 10, spc: Int = 10) = (atk shl 12) or (def shl 8) or (spd shl 4) or spc
        for (atk in listOf(2, 3, 6, 7, 10, 11, 14, 15)) assertTrue(Gen12Nuzlocke.shiny(2, dvs(atk)), "attack $atk")
        for (atk in listOf(0, 1, 4, 5, 8, 9, 12, 13)) assertFalse(Gen12Nuzlocke.shiny(2, dvs(atk)), "attack $atk")
        assertFalse(Gen12Nuzlocke.shiny(2, dvs(2, def = 9)))
        assertFalse(Gen12Nuzlocke.shiny(2, dvs(2, spd = 11)))
        assertFalse(Gen12Nuzlocke.shiny(2, dvs(2, spc = 0)))
        assertFalse(Gen12Nuzlocke.shiny(1, dvs(2)), "Generation 1 has no shiny Pokemon")
        assertFalse(Gen12Nuzlocke.shiny(2, -1), "unread DVs are not shiny")
    }

    // ---------------------------------------------------------------- how a battle ended

    @Test
    fun `Generation 1 tells a win, a loss, a run and a catch apart, and the wild Pokemon that left`() {
        // wBattleResult: 0 won, 1 lost, 2 both for the player running and for a ball catching.
        assertEquals(BattleEnd.LOST, Gen12Nuzlocke.battleEnd(gen1(1)))
        assertEquals(BattleEnd.LOST, Gen12Nuzlocke.battleEnd(gen1(1, lastWild = false)))
        assertEquals(BattleEnd.WON, Gen12Nuzlocke.battleEnd(gen1(0, lastWild = false)), "a trainer battle won")
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen1(2, lastWild = false)), "a trainer cannot be run from or caught")
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen1(2, hp = 5, captured = true)), "the ball's flag says it was a catch")
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen1(0, hp = 5, captured = true)))
        assertEquals(BattleEnd.RAN, Gen12Nuzlocke.battleEnd(gen1(2, hp = 5)), "2 with no ball is the player running")
        assertEquals(BattleEnd.WON, Gen12Nuzlocke.battleEnd(gen1(0, hp = 0)), "the wild Pokemon fainted")
        assertEquals(BattleEnd.MON_FLED, Gen12Nuzlocke.battleEnd(gen1(0, hp = 7)), "it left with HP to spare")
        assertEquals(BattleEnd.RAN, Gen12Nuzlocke.battleEnd(gen1(0, hp = 7, escaped = true)), "Teleport, a Poke Doll, Roar or Whirlwind")
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen1(0, hp = -1)), "never saw the enemy's HP")
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen1(-1)), "unread")
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen1(5, hp = 3)), "a value the game does not use")
    }

    @Test
    fun `Generation 2 calls every escape a draw, and a win with the wild Pokemon still standing is a catch`() {
        assertEquals(BattleEnd.LOST, Gen12Nuzlocke.battleEnd(gen2(1)))
        assertEquals(BattleEnd.WON, Gen12Nuzlocke.battleEnd(gen2(0, lastWild = false)))
        assertEquals(BattleEnd.DREW, Gen12Nuzlocke.battleEnd(gen2(2, lastWild = false)), "a trainer battle that ended in a draw")
        assertEquals(BattleEnd.RAN, Gen12Nuzlocke.battleEnd(gen2(2, hp = 9)), "a wild draw: somebody ran, and the game does not say who")
        assertEquals(BattleEnd.WON, Gen12Nuzlocke.battleEnd(gen2(0, hp = 0)))
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen2(0, hp = 9)), "no flag seen: the wild Pokemon was standing")
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen2(0, hp = -1)))
        // wWildMon, latched: the ball's own word, which needs no look at the enemy's HP.
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen2(0, hp = -1, captured = true)))
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen2(0, hp = 9, captured = true)))
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen2(0x80, hp = 0, captured = true)), "the flag outranks what the HP says")
        assertEquals(BattleEnd.WON, Gen12Nuzlocke.battleEnd(gen2(0, lastWild = false, captured = true)), "a trainer's Pokemon cannot be caught")
        assertEquals(BattleEnd.RAN, Gen12Nuzlocke.battleEnd(gen2(2, hp = 9)), "a draw is still a run, flag or none")
        assertEquals(BattleEnd.CAUGHT, Gen12Nuzlocke.battleEnd(gen2(0x80, hp = 9)), "bit 7 is the box filling up: a catch still")
        assertEquals(BattleEnd.LOST, Gen12Nuzlocke.battleEnd(gen2(0x81)))
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen2(3, hp = 9)))
        assertEquals(BattleEnd.UNKNOWN, Gen12Nuzlocke.battleEnd(gen2(-1, hp = 9)), "unread")
    }

    // ---------------------------------------------------------------- what kind of battle it was

    @Test
    fun `a lesson or a ghost is not an encounter`() {
        assertTrue(Gen12Nuzlocke.notAnEncounter(reads(1, "rb", listOf("rb"), battleType = 1)), "the Old Man's lesson")
        assertTrue(Gen12Nuzlocke.notAnEncounter(reads(1, "rb", listOf("rb"), ghost = true)), "a Pokemon Tower ghost")
        assertFalse(Gen12Nuzlocke.notAnEncounter(reads(1, "rb", listOf("rb"), battleType = 2)), "the Safari Zone is a real encounter")
        assertTrue(Gen12Nuzlocke.notAnEncounter(reads(1, "y", listOf("y"), battleType = 4)), "Yellow's opening battle with Pikachu")
        assertFalse(Gen12Nuzlocke.notAnEncounter(reads(1, "rb", listOf("rb"), battleType = 4)), "4 means nothing in Red and Blue")
        assertFalse(Gen12Nuzlocke.notAnEncounter(reads(1, "rb", listOf("rb"))))
        assertTrue(Gen12Nuzlocke.notAnEncounter(reads(2, battleType = 3)), "Crystal's tutorial")
        assertFalse(Gen12Nuzlocke.notAnEncounter(reads(2, battleType = 1)), "1 is not the lesson in Generation 2")
        assertFalse(Gen12Nuzlocke.notAnEncounter(reads(2, battleType = 0)))
    }

    @Test
    fun `Generation 2 says how a wild battle began, and the statics table covers the rest`() {
        fun m(type: Int, level: Int = 5, place: String? = "Route 29", surfing: Boolean = false, keys: List<String> = listOf("c")) =
            Gen12Nuzlocke.method(reads(2, game = keys.first(), keys = keys, place = place, battleType = type, surfing = surfing), level)
        assertEquals(Method.WALK, m(0))
        assertEquals(Method.ROD, m(4), "fishing")
        assertEquals(Method.HEADBUTT, m(8), "a headbutt tree")
        for (type in listOf(5, 6, 7, 9, 10, 11, 12)) assertEquals(Method.STATIC, m(type), "battle type $type is a set battle")
        assertEquals(Method.SURF, m(0, surfing = true))
        assertEquals(Method.ROD, m(4, surfing = true), "a rod on the water is still a rod")
        assertEquals(Method.WALK, m(0, place = null))
    }

    @Test
    fun `Generation 1 knows only that the player is surfing, and a table place and level makes a static`() {
        fun m(place: String?, level: Int, surfing: Boolean = false, keys: List<String> = listOf("rb"), species: Int? = null) =
            Gen12Nuzlocke.method(reads(1, keys.first(), keys, place = place, surfing = surfing), level, species)
        assertEquals(Method.WALK, m("Route 1", 3))
        assertEquals(Method.SURF, m("Route 19", 20, surfing = true))
        assertEquals(Method.STATIC, m("Route 16", 30), "Snorlax")
        assertEquals(Method.WALK, m("Route 16", 29), "the same road at another level is an ordinary encounter")
        assertEquals(Method.STATIC, m("Cerulean Cave", 70), "Mewtwo")
        assertEquals(Method.STATIC, m("Power Plant", 40), "a Voltorb that was an item ball")
        // Red and Blue's Route 12 has a level 30 slot of its own (Gloom or Weepinbell), so its Snorlax row names the species.
        assertEquals(Method.STATIC, m("Route 12", 30, species = 143), "Snorlax")
        assertEquals(Method.WALK, m("Route 12", 30, species = 44), "a level 30 Gloom in the grass is an ordinary encounter")
        assertEquals(Method.WALK, m("Route 12", 30), "an unknown species does not match a row that names one")
        assertEquals(Method.STATIC, m("Pokemon Tower", 30, species = 105), "the ghost Marowak")
        assertEquals(Method.WALK, m("Pokemon Tower", 30, species = 93), "a Haunter of the Tower's own slots")
        assertEquals(Method.STATIC, m("Route 12", 30, keys = listOf("y"), species = 44), "Yellow's row has no species check")
    }

    // ---------------------------------------------------------------- the engine's view of a state

    private fun base(ratio: Int, t1: Int = 10, t2: Int = 10) = BaseStats(39, 52, 43, 65, 50, 50, t1, t2, 0, 0, genderRatio = ratio)

    private fun partyMon(species: Int, name: String, ratio: Int?, ot: Int = 0x2B0E, dvs: Int = 0xA5B6, level: Int = 12, hp: Int = 30, max: Int = 40): TrackedMon {
        val mon = PokemonDecoder.Mon(
            pid = (species.toLong() shl 16) or ot.toLong(), level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0),
            ivs = listOf(0, (dvs shr 12) and 15, (dvs shr 8) and 15, (dvs shr 4) and 15, dvs and 15, dvs and 15), evs = List(6) { 0 },
            ppUps = listOf(0, 0, 0, 0), abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = hp, maxHp = max,
            atk = 1, def = 1, spe = 1, spAtk = 1, spDef = 1,
        )
        return TrackedMon(mon, name, emptyList(), ratio?.let { base(it) })
    }

    private fun enemy(species: Int, name: String, level: Int, hp: Int, max: Int, ratio: Int?) =
        EnemyInfo(species, name, level, hp, max, 0, 0, ratio?.let { base(it, 0, 0) }, emptyList())

    @Test
    fun `a state with no Game Boy reads has no snapshot, and neither has one that could not be read`() {
        assertNull(Gen12Nuzlocke.snapshot(TrackerState(0, emptyList(), inBattle = false, isWildBattle = false)))
        val bad = TrackerState(0, emptyList(), inBattle = false, isWildBattle = false, unreadable = true, nuz = NuzlockeReads(gb = reads()))
        assertNull(Gen12Nuzlocke.snapshot(bad))
        assertNull(NuzlockeAdapters.snapshot(null))
    }

    @Test
    fun `the party of a Generation 2 state carries each Pokemon's id, gender, types, shininess and nickname`() {
        val shinyDvs = (2 shl 12) or (10 shl 8) or (10 shl 4) or 10
        val state = TrackerState(
            partyCount = 2, inBattle = false, isWildBattle = false, badges = 0b101,
            party = listOf(
                partyMon(155, "CYNDAQUIL", 31, dvs = 0xF7F7),          // attack 15, speed 15: male
                partyMon(161, "SENTRET", 127, ot = 0x99, dvs = shinyDvs),
            ),
            mapId = 2,
            nuz = NuzlockeReads(gb = reads(2, place = "Route 29", detail = "Route 29", playerId = 0x2B0E).copy(nicknames = listOf("Ash", "")))
        )
        val s = assertNotNull(NuzlockeAdapters.snapshot(state))
        assertEquals("Route 29", s.area.name); assertEquals(2, s.area.mapId); assertEquals("Route 29", s.area.detail)
        assertEquals(listOf(Gender.MALE, Gender.FEMALE), s.party.map { it.gender })
        assertEquals(listOf(10), s.party[0].types, "one type, once")
        assertEquals(listOf(false, true), s.party.map { it.shiny })
        assertEquals(listOf("Ash", ""), s.party.map { it.nickname })
        assertEquals(0x2B0E_F7F7L, s.party[0].id)
        assertEquals((0x99L shl 16) or shinyDvs.toLong(), s.party[1].id)
        assertEquals(0b101, s.badges); assertEquals(2, s.partyCount)
        assertFalse(s.inBattle); assertFalse(s.ghost)
    }

    @Test
    fun `the badges say which gyms are beaten, Johto's in bits 0 to 7 and Kanto's in 8 to 15`() {
        fun beaten(gb: GbNuzReads, badges: Int) = assertNotNull(Gen12Nuzlocke.snapshot(
            TrackerState(0, emptyList(), inBattle = false, isWildBattle = false, badges = badges, nuz = NuzlockeReads(gb = gb)),
        )).beaten
        val red = reads(1, "rb", listOf("rb")).copy(caps = LevelCapTable.standard("rb", NuzlockeSystem.GEN1))
        assertEquals(setOf("gym1", "gym3"), beaten(red, 0b101), "Boulder and Thunder")
        assertEquals(8, beaten(red, 0xFF).size)
        assertEquals(emptySet(), beaten(red, 0))
        val crystal = reads(2).copy(caps = LevelCapTable.standard("c", NuzlockeSystem.GEN2))
        assertEquals(setOf("gym1", "gym2", "brock"), beaten(crystal, 0b11 or (1 shl 8)))
        assertEquals(setOf("blue"), beaten(crystal, 1 shl 15), "the Earth Badge is the last Kanto bit")
        assertEquals(emptySet(), beaten(reads(2), 0xFF), "no table to say which")
    }

    @Test
    fun `the party of a Generation 1 state has no gender and no shiny`() {
        val state = TrackerState(
            partyCount = 1, inBattle = false, isWildBattle = false,
            party = listOf(partyMon(4, "CHARMANDER", 31, dvs = 0xFFFF)),
            nuz = NuzlockeReads(gb = reads(1, "rb", listOf("rb"), place = "Route 1", detail = "Route 1")),
        )
        val s = assertNotNull(NuzlockeAdapters.snapshot(state))
        assertNull(s.party.single().gender)
        assertFalse(s.party.single().shiny)
    }

    @Test
    fun `a wild enemy is given the id of its catch and its own gender and shininess, and a trainer's Pokemon none of them`() {
        val dvs = (10 shl 12) or (10 shl 8) or (10 shl 4) or 10
        val base = TrackerState(
            partyCount = 1, party = listOf(partyMon(155, "CYNDAQUIL", 31)), inBattle = true, isWildBattle = true,
            enemy = enemy(161, "SENTRET", 4, 12, 18, 127),
        )
        val wild = assertNotNull(Gen12Nuzlocke.snapshot(base.copy(nuz = NuzlockeReads(gb = reads(playerId = 0x2B0E, enemyDvs = dvs)))))
        val e = assertNotNull(wild.enemy)
        assertEquals(Gen12Nuzlocke.id(0x2B0E, dvs), e.id)
        assertEquals(Gender.MALE, e.gender, "b = 170 against 127")
        assertTrue(e.shiny, "attack 10 has bit 1 set")
        assertTrue(wild.wild); assertTrue(wild.inBattle)
        assertEquals(Method.WALK, wild.method)
        assertEquals(BattleEnd.UNKNOWN, wild.end, "the battle is still on")
        val trainer = assertNotNull(Gen12Nuzlocke.snapshot(base.copy(isWildBattle = false, nuz = NuzlockeReads(gb = reads(playerId = 0x2B0E, enemyDvs = dvs)))))
        val t = assertNotNull(trainer.enemy)
        assertEquals(0L, t.id); assertNull(t.gender); assertFalse(t.shiny)
        assertFalse(trainer.wild)
    }

    @Test
    fun `outside a battle the snapshot carries how the last one ended, and inside it none`() {
        val out = TrackerState(partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false,
            nuz = NuzlockeReads(gb = reads(2, battleResult = 0, enemyHpLast = 9)))
        assertEquals(BattleEnd.CAUGHT, assertNotNull(Gen12Nuzlocke.snapshot(out)).end)
        val inside = out.copy(inBattle = true, isWildBattle = true, enemy = enemy(161, "SENTRET", 4, 12, 18, 127))
        assertEquals(BattleEnd.UNKNOWN, assertNotNull(Gen12Nuzlocke.snapshot(inside)).end)
    }

    @Test
    fun `a ghost or a lesson is flagged only while its battle is on`() {
        val ghostReads = NuzlockeReads(gb = reads(1, "rb", listOf("rb"), place = "Pokemon Tower", ghost = true))
        val on = TrackerState(partyCount = 0, party = emptyList(), inBattle = true, isWildBattle = true, enemy = enemy(92, "GASTLY", 10, 20, 20, null), nuz = ghostReads)
        assertTrue(assertNotNull(Gen12Nuzlocke.snapshot(on)).ghost)
        assertFalse(assertNotNull(Gen12Nuzlocke.snapshot(on.copy(inBattle = false, enemy = null))).ghost)
    }

    @Test
    fun `the counters and options are handed on, and a value that is not there is null`() {
        val gb = reads().copy(ballCount = 7, turn = 3, battleStyleSet = true, bag = mapOf(18 to BagItem("POTION", 2)))
        val state = TrackerState(partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false, nuz = NuzlockeReads(gb = gb))
        val s = assertNotNull(Gen12Nuzlocke.snapshot(state))
        assertEquals(7, s.ballCount); assertEquals(3, s.turn); assertEquals(true, s.battleStyleSet)
        assertEquals("POTION", s.bag!!.getValue(18).name); assertEquals(2, s.bag!!.getValue(18).qty)
        val none = assertNotNull(Gen12Nuzlocke.snapshot(state.copy(nuz = NuzlockeReads(gb = reads()))))
        assertNull(none.ballCount, "-1 is not a count"); assertNull(none.turn); assertNull(none.bag); assertNull(none.battleStyleSet); assertNull(none.caps)
        assertTrue(none.beaten.isEmpty())
    }
}
