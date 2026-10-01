package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.Origin
import com.ironmonone.tracker.nuzlocke.Outcome
import com.ironmonone.tracker.nuzlocke.RunMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Gen 3 adapter: a TrackerState in, the engine's snapshot out, and the engine driven by real states (2026-09-29). */
class Gen3NuzlockeTest {

    private fun decoded(
        pid: Long, species: Int = 16, level: Int = 5, hp: Int = 20, maxHp: Int = 20, nick: String = "", egg: Boolean = false, shiny: Boolean = false,
    ) = PokemonDecoder.Mon(
        pid = pid, level = level, nickname = nick, species = species, heldItem = 0, friendship = 70,
        moves = List(4) { 0 }, pp = List(4) { 0 }, ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 0, shiny = shiny, status = 0, curHp = hp, maxHp = maxHp,
        atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10, exp = 0, isEgg = egg,
    )

    private fun base(ratio: Int = 127, t1: Int = 0, t2: Int = 2) =
        BaseStats(40, 45, 40, 56, 35, 35, t1, t2, 51, 77, genderRatio = ratio)

    private fun tracked(m: PokemonDecoder.Mon, name: String = "PIDGEY", b: BaseStats? = base()) =
        TrackedMon(m, name, emptyList(), b)

    private fun enemy(pid: Long = 100, species: Int = 19, name: String = "RATTATA", level: Int = 3, hp: Int = 10, ghost: Boolean = false) = EnemyInfo(
        species = species, speciesName = name, level = level, curHp = hp, maxHp = 10, type1 = 0, type2 = 0,
        base = base(ratio = 127, t1 = 0, t2 = 0), movesSeen = emptyList(), pid = pid, isGhost = ghost,
    )

    private val standardNuz = NuzlockeReads(ballCount = 5)

    private fun state(
        party: List<TrackedMon> = emptyList(), inBattle: Boolean = false, wild: Boolean = false, foe: EnemyInfo? = null,
        nuz: NuzlockeReads? = standardNuz, area: String = "Route 3", encounterArea: String? = null, ghost: Boolean = false,
        partyCount: Int = party.size, badges: Int = 0,
    ) = TrackerState(
        partyCount = partyCount, party = party, inBattle = inBattle, isWildBattle = wild, enemy = foe, mapId = 91, routeName = area,
        encounterArea = encounterArea, isGhostBattle = ghost, badges = badges, nuz = nuz,
    )

    @Test
    fun `the game's outcome numbers become the engine's endings`() {
        val want = mapOf(
            0 to BattleEnd.UNKNOWN, 1 to BattleEnd.WON, 2 to BattleEnd.LOST, 3 to BattleEnd.DREW, 4 to BattleEnd.RAN, 5 to BattleEnd.RAN,
            6 to BattleEnd.MON_FLED, 7 to BattleEnd.CAUGHT, 8 to BattleEnd.RAN, 9 to BattleEnd.RAN, 10 to BattleEnd.MON_FLED, 11 to BattleEnd.UNKNOWN, 255 to BattleEnd.UNKNOWN,
        )
        for ((n, e) in want) assertEquals(e, Gen3Nuzlocke.battleEnd(n), "outcome $n")
    }

    @Test
    fun `an encounter area name becomes how the battle began`() {
        assertEquals(Method.WALK, Gen3Nuzlocke.method("Walking")); assertEquals(Method.WALK, Gen3Nuzlocke.method(null))
        assertEquals(Method.WALK, Gen3Nuzlocke.method("Trainer"))
        assertEquals(Method.SURF, Gen3Nuzlocke.method("Surfing")); assertEquals(Method.UNDERWATER, Gen3Nuzlocke.method("Underwater"))
        assertEquals(Method.ROCK_SMASH, Gen3Nuzlocke.method("RockSmash")); assertEquals(Method.STATIC, Gen3Nuzlocke.method("Static"))
        for (rod in listOf("Old Rod", "Good Rod", "Super Rod")) assertEquals(Method.ROD, Gen3Nuzlocke.method(rod))
    }

    @Test
    fun `a party member keeps its personality value as its id, and gender comes from the species' ratio`() {
        val s = Gen3Nuzlocke.snapshot(state(party = listOf(
            tracked(decoded(0x180, nick = "Male")),                                            // low byte 0x80: at or above 127, male
            tracked(decoded(0x110, nick = "Female")),                                          // 0x10: below, female
            tracked(decoded(0x99, nick = "None"), b = base(ratio = 255)),                      // genderless
            tracked(decoded(0x05, nick = "Always male"), b = base(ratio = 0)),
            tracked(decoded(0xF0, nick = "Always female"), b = base(ratio = 254)),
            tracked(decoded(0x07, nick = "No stats"), b = null),
        )))!!
        assertEquals(listOf(0x180L, 0x110L, 0x99L, 0x05L, 0xF0L, 0x07L), s.party.map { it.id })
        assertEquals(listOf(Gender.MALE, Gender.FEMALE, null, Gender.MALE, Gender.FEMALE, null), s.party.map { it.gender })
        assertEquals(listOf("Male", "Female", "None", "Always male", "Always female", "No stats"), s.party.map { it.nickname })
    }

    @Test
    fun `party types, HP, eggs and shininess pass through, and a single type is one`() {
        val s = Gen3Nuzlocke.snapshot(state(party = listOf(
            tracked(decoded(1, hp = 7, maxHp = 20, shiny = true), b = base(t1 = 0, t2 = 2)),
            tracked(decoded(2), b = base(t1 = 11, t2 = 11)),
            tracked(decoded(3, egg = true), name = "PIDGEY"),
            tracked(decoded(4), b = null),
        )))!!
        val (a, b, egg, none) = s.party
        assertEquals(listOf(0, 2), a.types); assertEquals(7, a.hp); assertEquals(20, a.maxHp); assertTrue(a.shiny)
        assertEquals(listOf(11), b.types)
        assertTrue(egg.isEgg && !egg.real)
        assertEquals(emptyList(), none.types)
    }

    @Test
    fun `the map, the party count and the badges are carried as they are`() {
        val s = Gen3Nuzlocke.snapshot(state(party = listOf(tracked(decoded(1))), partyCount = 2, badges = 5))!!
        assertEquals("Route 3", s.area.name); assertEquals(91, s.area.mapId)
        assertEquals(2, s.partyCount, "the game's own count, not the decoded one")
        assertEquals(5, s.badges)
    }

    @Test
    fun `a wild enemy is described with its shininess from the reads, and a ghost is no enemy at all`() {
        val s = Gen3Nuzlocke.snapshot(state(inBattle = true, wild = true, foe = enemy(pid = 77), encounterArea = "Surfing", nuz = NuzlockeReads(enemyShiny = true)))!!
        assertTrue(s.inBattle && s.wild && !s.ghost)
        assertEquals(Method.SURF, s.method)
        val e = assertNotNull(s.enemy)
        assertEquals(77L, e.id); assertEquals("RATTATA", e.speciesName); assertTrue(e.shiny)
        val g = Gen3Nuzlocke.snapshot(state(inBattle = true, wild = true, foe = enemy(ghost = true), ghost = true))!!
        assertTrue(g.ghost); assertNull(g.enemy)
    }

    @Test
    fun `the ending is only given once the battle is over`() {
        val n = NuzlockeReads(battleOutcome = 7, turn = 4)
        assertEquals(BattleEnd.UNKNOWN, Gen3Nuzlocke.snapshot(state(inBattle = true, wild = true, foe = enemy(), nuz = n))!!.end)
        assertEquals(BattleEnd.CAUGHT, Gen3Nuzlocke.snapshot(state(inBattle = false, nuz = n))!!.end)
        assertEquals(4, Gen3Nuzlocke.snapshot(state(inBattle = true, wild = true, foe = enemy(), nuz = n))!!.turn)
    }

    @Test
    fun `values the tracker could not read stay unknown, not zero`() {
        val s = Gen3Nuzlocke.snapshot(state(nuz = NuzlockeReads(ballCount = -1, turn = -1)))!!
        assertNull(s.ballCount); assertNull(s.turn); assertNull(s.bag); assertNull(s.battleStyleSet); assertNull(s.caps)
        assertEquals(0, Gen3Nuzlocke.snapshot(state(nuz = NuzlockeReads(ballCount = 0)))!!.ballCount, "no balls is 0, not unknown")
    }

    @Test
    fun `the bag, the opponent, the caps and the beaten bosses are handed on`() {
        val caps = LevelCapTable.standard("frlg")
        val n = NuzlockeReads(
            bag = mapOf(13 to BagItem("POTION", 3)),
            opponent = OpponentInfo(414, "LEADER BROCK", "Gym", "gym1", 14),
            caps = caps, beaten = setOf("gym1"), battleStyleSet = true,
        )
        val s = Gen3Nuzlocke.snapshot(state(nuz = n))!!
        assertEquals(3, s.bag!!.getValue(13).qty); assertEquals("POTION", s.bag!!.getValue(13).name)
        assertEquals("gym1", s.opponent!!.bossKey); assertEquals(14, s.opponent!!.maxLevel); assertEquals("Gym", s.opponent!!.group)
        assertEquals(caps, s.caps); assertEquals(setOf("gym1"), s.beaten); assertEquals(true, s.battleStyleSet)
    }

    @Test
    fun `no reads or an unreadable game gives no snapshot`() {
        assertNull(Gen3Nuzlocke.snapshot(state(nuz = null)))
        val unreadable = state(nuz = standardNuz).copy(unreadable = true)
        assertNull(Gen3Nuzlocke.snapshot(unreadable))
    }

    // ---- The engine, fed the states the tracker would report -----------------------------------------------

    private fun run(rules: NuzlockeRules = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)): Pair<NuzlockeEngine, NuzlockeLedger> {
        val ledger = NuzlockeLedger(RunMeta("nz-adapter", "lib-test", "FireRed", rules, 1_000L))
        return NuzlockeEngine(ledger) to ledger
    }

    private var clock = 5_000L
    private fun feed(e: NuzlockeEngine, s: TrackerState) { e.update(Gen3Nuzlocke.snapshot(s)!!, clock); clock += 700 }

    @Test
    fun `a starter, a wild encounter and a catch, as tracker states`() {
        val (engine, ledger) = run()
        val shell = tracked(decoded(1, species = 7, level = 5, nick = "Shell"), "SQUIRTLE")
        val peck = tracked(decoded(100, species = 16, level = 3, nick = "Peck"), "PIDGEY")
        feed(engine, state(party = listOf(shell)))
        assertEquals(Origin.STARTER, ledger.roster.getValue(1L).origin)
        assertTrue(ledger.meta.started, "five Poke Balls in the bag")
        // A wild Pidgey on Route 3: the first encounter of the area.
        feed(engine, state(party = listOf(shell), inBattle = true, wild = true, foe = enemy(pid = 100, species = 16, name = "PIDGEY", level = 3), encounterArea = "Walking"))
        assertEquals(Outcome.IN_PROGRESS, ledger.areas.getValue("Route 3").encounter!!.outcome)
        // Caught: it is in the party while the battle is still on, then the battle is over and the game says 7.
        feed(engine, state(party = listOf(shell, peck), inBattle = true, wild = true, foe = enemy(pid = 100, species = 16, name = "PIDGEY", level = 3, hp = 0), encounterArea = "Walking"))
        feed(engine, state(party = listOf(shell, peck), nuz = NuzlockeReads(ballCount = 4, battleOutcome = 7)))
        val enc = ledger.areas.getValue("Route 3").encounter!!
        assertEquals(Outcome.CAUGHT, enc.outcome)
        assertEquals(100L, enc.monId)
        assertEquals(Origin.CAUGHT, ledger.roster.getValue(100L).origin)
        assertEquals("Pidgey", ledger.roster.getValue(100L).speciesName)
    }

    @Test
    fun `a faint and a whiteout, as tracker states`() {
        val (engine, ledger) = run()
        val shell = tracked(decoded(1, species = 7, level = 5, nick = "Shell"), "SQUIRTLE")
        val peck = tracked(decoded(100, species = 16, level = 3, nick = "Peck"), "PIDGEY")
        val box = tracked(decoded(101, species = 19, level = 3, nick = "Nibble"), "RATTATA")
        feed(engine, state(party = listOf(shell, peck)))
        feed(engine, state(party = listOf(shell, peck, box)))
        feed(engine, state(party = listOf(shell, peck)))
        assertFalse(ledger.roster.getValue(101L).inParty, "boxed")
        fun down(m: TrackedMon) = m.copy(mon = m.mon.copy(curHp = 0))
        feed(engine, state(party = listOf(down(shell), down(peck)), inBattle = true, wild = true, foe = enemy(), encounterArea = "Walking"))
        assertEquals(2, ledger.graveyard.size)
        feed(engine, state(party = listOf(down(shell), down(peck)), inBattle = true, wild = true, foe = enemy(), encounterArea = "Walking"))
        assertEquals("Whiteout", ledger.meta.endReason)
        assertEquals("wild Rattata Lv 3", ledger.graveyard.first().death!!.cause)
    }

    @Test
    fun `a boss battle checks the party against the level cap read from the game`() {
        val (engine, ledger) = run(NuzlockeRules.forPreset(NuzlockePreset.HARDCORE))
        val big = tracked(decoded(1, species = 7, level = 30, nick = "Shell"), "SQUIRTLE")
        val caps = LevelCapTable.standard("frlg").withRomLevels(mapOf("gym1" to 22))
        val brock = OpponentInfo(414, "LEADER BROCK", "Gym", "gym1", 22)
        feed(engine, state(party = listOf(big), nuz = NuzlockeReads(ballCount = 5, caps = caps)))
        feed(engine, state(party = listOf(big), inBattle = true, wild = false, foe = enemy(pid = 900),
            nuz = NuzlockeReads(ballCount = 5, turn = 0, opponent = brock, caps = caps)))
        val w = ledger.openWarnings.single()
        assertTrue("level 30" in w.text && "cap of 22" in w.text, w.text)
    }
}
