package com.ironmonone.tracker.nuzlocke

/*
 * A small simulator for the Nuzlocke tests (2026-09-29): a world of mutable state (the party, the map, the
 * battle) that turns into one Snapshot per poll, so a scenario reads as the sequence of game states the
 * tracker would have reported. Polls are 700 ms apart, like the Play screen's outside battle.
 */

/** Gen 3 species ids used across the tests. */
internal object Sp {
    const val SQUIRTLE = 7; const val PIDGEY = 16; const val PIDGEOTTO = 17; const val PIDGEOT = 18
    const val RATTATA = 19; const val RATICATE = 20; const val SPEAROW = 21; const val MAGIKARP = 129
    const val EEVEE = 133; const val TREECKO = 277
}

internal const val WATER = 11
internal const val NORMAL = 0
internal const val FLYING = 2

internal fun mon(
    id: Long,
    species: Int = Sp.PIDGEY,
    name: String = "PIDGEY",
    level: Int = 5,
    hp: Int = 20,
    maxHp: Int = 20,
    nickname: String = "Nick$id",
    gender: Gender? = Gender.MALE,
    types: List<Int> = listOf(NORMAL, FLYING),
    shiny: Boolean = false,
    egg: Boolean = false,
) = NzMon(id, species, name, nickname, level, hp, maxHp, egg, gender, types, shiny)

internal fun foe(
    id: Long,
    species: Int = Sp.RATTATA,
    name: String = "RATTATA",
    level: Int = 3,
    hp: Int = 10,
    maxHp: Int = 10,
    gender: Gender? = Gender.MALE,
    types: List<Int> = listOf(NORMAL),
    shiny: Boolean = false,
) = NzEnemy(id, species, name, level, hp, maxHp, gender, types, shiny)

internal fun rules(preset: NuzlockePreset = NuzlockePreset.STANDARD, tweak: (NuzlockeRules) -> NuzlockeRules = { it }): NuzlockeRules =
    tweak(NuzlockeRules.forPreset(preset))

internal class Sim(val rules: NuzlockeRules = rules(), val system: NuzlockeSystem = NuzlockeSystem.GEN3) {
    val ledger = NuzlockeLedger(RunMeta("nz-test", "lib-test", "Test game", rules, 1_000L).also { it.system = system })
    var engine = NuzlockeEngine(ledger)
    var now = 10_000L

    var area = NzArea("Route 3", 91)
    var party: List<NzMon> = emptyList()
    /** The game's own count when the read shows fewer Pokemon than it has. */
    var partyCount: Int? = null
    var balls: Int? = 5
    var badges = 0
    var inBattle = false
    var wild = true
    var ghost = false
    var method = Method.WALK
    var enemy: NzEnemy? = null
    var opponent: NzOpponent? = null
    var end = BattleEnd.UNKNOWN
    var turn: Int? = 0
    var bag: Map<Int, NzItem>? = null
    var style: Boolean? = null
    var caps: LevelCapTable? = null
    var beaten: Set<String> = emptySet()
    var readable = true
    /** The party is one a Battle Tent or Frontier lends (Snapshot.facility). */
    var facility = false

    fun snapshot() = Snapshot(
        readable, area, inBattle, wild, ghost, method, enemy, opponent, end, turn,
        party, partyCount ?: party.size, badges, balls, bag, style, caps, beaten, facility,
    )

    fun poll(): Boolean = engine.update(snapshot(), now).also { now += 700 }

    /** [n] polls outside a battle. */
    fun idle(n: Int = 1) {
        inBattle = false; enemy = null; opponent = null; end = BattleEnd.UNKNOWN
        repeat(n) { poll() }
    }

    /** The first poll of a game with a starter: the ledger takes it in and the rules begin. */
    fun starter(m: NzMon = mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "Shell", types = listOf(WATER))): Sim {
        party = listOf(m)
        idle()
        return this
    }

    fun wildBattle(e: NzEnemy, how: Method = Method.WALK, polls: Int = 1) {
        inBattle = true; wild = true; ghost = false; enemy = e; method = how; opponent = null; end = BattleEnd.UNKNOWN; turn = 0
        repeat(polls) { poll() }
    }

    fun trainerBattle(opp: NzOpponent, e: NzEnemy? = foe(900, Sp.RATTATA, "RATTATA", 12), turnNow: Int? = 0, polls: Int = 1) {
        inBattle = true; wild = false; ghost = false; enemy = e; opponent = opp; end = BattleEnd.UNKNOWN; turn = turnNow
        repeat(polls) { poll() }
    }

    /** The battle is over: the first poll outside it, with how the game says it ended. */
    fun finish(how: BattleEnd = BattleEnd.UNKNOWN) {
        inBattle = false; enemy = null; opponent = null; end = how
        poll()
        end = BattleEnd.UNKNOWN
    }

    /** A whole wild battle that ends [how] with nothing caught. */
    fun wildEncounter(e: NzEnemy, how: BattleEnd, method: Method = Method.WALK) {
        wildBattle(e, method); finish(how)
    }

    /** A whole wild battle where [caught] joins the party and the game says caught. */
    fun catchIt(e: NzEnemy, caught: NzMon, method: Method = Method.WALK) {
        wildBattle(e, method)
        party = party + caught
        poll()
        finish(BattleEnd.CAUGHT)
    }

    fun moveTo(name: String, id: Int = 92) { area = NzArea(name, id) }

    fun enc(key: String): Encounter? = ledger.areas[key]?.encounter
    fun warnings(kind: WarnKind) = ledger.warnings.filter { it.kind == kind }
}

internal fun brock(level: Int = 14) = NzOpponent(414, "LEADER BROCK", "Gym", "gym1", level)
