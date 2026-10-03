package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The opponent's moves seen, tracked the way the reference tracks them (EnemyMoveWatch, Battle.lua:376-445; rc33 audit
 * P1 #72). The tracker used to copy gBattleResults' last opponent move on every poll, which is the move the AI chose,
 * written before the game checks whether the Pokemon can act and kept after it has gone: it revealed moves never used,
 * handed a fainted foe's last move to the next one, and put a doubles partner's moves on the left foe's card.
 *
 * A battle staged in Emerald's RAM, no ROM needed: the names come from a blank ROM image with two tables poked in.
 */
class EnemyMovesSeenTest {
    private val map = GameMap.EMERALD_U
    private val pikachu = 25; private val rattata = 19; private val vulpix = 37; private val machop = 66
    private val thundershock = 84; private val tackle = 33; private val growl = 45; private val ember = 52
    private val mimic = 102; private val focusPunch = 264; private val tailWhip = 39

    private class Mem(val rom: ByteArray) {
        val ram = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { ram[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun putBytes(a: Long, b: ByteArray) { b.forEachIndexed { i, x -> ram[a + i] = x } }
        fun reader() = MemoryReader { address, length ->
            if (address >= 0x08000000L) {
                val off = (address - 0x08000000L).toInt()
                if (off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
            } else ByteArray(length) { ram[address + it] ?: 0 }
        }
    }

    private fun name(s: String, len: Int): ByteArray = ByteArray(len).also { out ->
        s.forEachIndexed { i, c -> out[i] = (if (c in 'A'..'Z') 0xBB + (c - 'A') else 0).toByte() }
        out[s.length] = 0xFF.toByte()
    }

    /** A party Pokemon: personality a multiple of 24 keeps the plain GAEM substructure order, OT id 0 the key. */
    private fun partyMon(pid: Long, species: Int, level: Int, moves: List<Int>): ByteArray {
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        moves.forEachIndexed { i, m -> plain[12 + i * 2] = m.toByte(); plain[13 + i * 2] = (m shr 8).toByte() }
        val mon = ByteArray(100)
        mon.putU32(0, pid)
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4) xor pid)
        mon[0x54] = level.toByte(); mon[0x56] = 30; mon[0x58] = 30
        return mon
    }

    /** Your Pikachu against [foes] (species and moves, in party order); battler 1 is slot 0, and battler 3 slot 1 in doubles. */
    private fun battle(foes: List<Pair<Int, List<Int>>>, doubles: Boolean = false): Pair<Mem, GbaTracker> {
        val rom = ByteArray(0x400000)
        fun poke(addr: Long, bytes: ByteArray) = bytes.copyInto(rom, (addr - 0x08000000L).toInt())
        for ((id, n) in listOf(pikachu to "PIKACHU", rattata to "RATTATA", vulpix to "VULPIX", machop to "MACHOP"))
            poke(map.speciesNames + id * 11L, name(n, 11))
        for ((id, n) in listOf(thundershock to "THUNDERSHOCK", tackle to "TACKLE", growl to "GROWL", ember to "EMBER",
                mimic to "MIMIC", focusPunch to "FOCUS PUNCH", tailWhip to "TAIL WHIP"))
            poke(map.moveNames + id * 13L, name(n, 13))
        val mem = Mem(rom)
        mem.put8(map.battlersCount, if (doubles) 4 else 2)
        mem.put32(map.battleMainFunc, map.handleTurnAction)
        mem.put32(map.battleTypeFlags, 0x8L or (if (doubles) 1L else 0L))
        mem.put16(map.battleMons, pikachu)
        foes.forEachIndexed { i, (sp, moves) -> mem.putBytes(map.enemyParty + i * 100L, partyMon(24L * (i + 1), sp, 10 + i, moves)) }
        sendOut(mem, 1, 0, foes[0].first)
        if (doubles) { mem.put16(map.battleMons + 2L * map.battleMonSize, pikachu); sendOut(mem, 3, 1, foes[1].first) }
        val t = GbaTracker(mem.reader(), map)
        t.read(); t.read()   // the battle screen, then its data
        return mem to t
    }

    /** [slot] of the enemy party takes opposing [battler]'s place on the field. */
    private fun sendOut(mem: Mem, battler: Int, slot: Int, species: Int) {
        mem.put16(map.battlerPartyIndexes + battler * 2L, slot)
        val b = map.battleMons + battler.toLong() * map.battleMonSize
        mem.put16(b, species); mem.put8(b + 0x2A, 10 + slot); mem.put16(b + 0x28, 30); mem.put16(b + 0x2C, 30)
    }

    /**
     * [battler]'s move action, the [n]th of the turn: the game has written [move] as its side's last move, and the
     * tracker polls twice while the action runs. [unable]: the game marked the Pokemon unable to use it.
     */
    private fun act(mem: Mem, t: GbaTracker, battler: Int, n: Int, move: Int, unable: Boolean = false, script: Long = 0L): TrackerState {
        mem.put8(map.currentTurnActionNumber, n)
        mem.put8(map.actionsByTurnOrder + n, 0)
        mem.put8(map.battlerAttacker, battler)
        mem.put16(map.battleResults + if (battler % 2 == 0) 0x22 else 0x24, move)
        mem.put32(map.hitMarker, if (unable) EnemyMoveWatch.UNABLE_TO_USE_MOVE else 0L)
        mem.put32(map.scriptCurrInstr, script)
        t.read()
        return t.read()
    }

    /** The turn's actions are over: gCurrentTurnActionNumber reaches gBattlersCount. */
    private fun endTurn(mem: Mem, t: GbaTracker): TrackerState {
        mem.put8(map.currentTurnActionNumber, mem.ram[map.battlersCount]!!.toInt())
        t.read()
        return t.read()
    }

    private fun seen(s: TrackerState) = s.enemy!!.movesSeen

    @Test
    fun `a move is the opponent's once it uses it, and your own are never filed`() {
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl)))
        assertEquals(emptyList(), seen(t.read()))
        assertEquals(emptyList(), seen(act(mem, t, 0, 0, thundershock)), "your move is not the opponent's")
        val s = act(mem, t, 1, 1, tackle)
        assertEquals(listOf("TACKLE"), seen(s))
        assertEquals(listOf(EnemyMovesSeen(rattata, 10, listOf(tackle to "TACKLE"))), s.enemyMovesThisBattle)
    }

    @Test
    fun `the AI's choice alone is not a use`() {
        // What the tracker used to read: the last move written, with no action behind it.
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl)))
        mem.put8(map.battlerAttacker, 1)
        mem.put16(map.battleResults + 0x24, tackle)
        mem.put8(map.currentTurnActionNumber, 2)
        repeat(4) { t.read() }
        assertEquals(emptyList(), seen(t.read()))
    }

    @Test
    fun `a turn the opponent cannot move reveals nothing`() {
        // Asleep, fully paralysed, flinching or loafing: the game writes the chosen move, then marks it unable.
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl)))
        act(mem, t, 0, 0, thundershock)
        assertEquals(emptyList(), seen(act(mem, t, 1, 1, tackle, unable = true)))
        endTurn(mem, t)
        act(mem, t, 0, 0, thundershock)
        assertEquals(listOf("TACKLE"), seen(act(mem, t, 1, 1, tackle)), "used the next turn, it counts")
    }

    @Test
    fun `a fainted opponent's last move does not land on the next one`() {
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl), vulpix to listOf(ember, tackle)))
        act(mem, t, 1, 0, tackle)
        act(mem, t, 0, 1, thundershock)
        endTurn(mem, t)
        // Rattata faints and the trainer sends Vulpix; the game's last opponent move still reads Tackle, which Vulpix
        // knows too.
        sendOut(mem, 1, 1, vulpix)
        assertEquals(emptyList(), seen(endTurn(mem, t)), "nothing Vulpix has done")
        act(mem, t, 0, 0, thundershock)
        val s = act(mem, t, 1, 1, ember)
        assertEquals(listOf("EMBER"), seen(s))
        assertEquals(listOf(EnemyMovesSeen(rattata, 10, listOf(tackle to "TACKLE")), EnemyMovesSeen(vulpix, 11, listOf(ember to "EMBER"))),
            s.enemyMovesThisBattle, "each kept for the run under its own species")
    }

    @Test
    fun `in a double battle the partner's moves are its own`() {
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl), vulpix to listOf(ember, tailWhip)), doubles = true)
        act(mem, t, 0, 0, thundershock)
        act(mem, t, 1, 1, tackle)
        act(mem, t, 2, 2, thundershock)
        val s = act(mem, t, 3, 3, ember)
        assertEquals(listOf("TACKLE"), seen(s), "the left foe's card has only its own")
        assertEquals(listOf(EnemyMovesSeen(rattata, 10, listOf(tackle to "TACKLE")), EnemyMovesSeen(vulpix, 11, listOf(ember to "EMBER"))),
            s.enemyMovesThisBattle)
    }

    @Test
    fun `a confused pause holds the move until it is used`() {
        val scripts = map.moveScripts!!
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl)))
        act(mem, t, 0, 0, thundershock)
        assertEquals(emptyList(), seen(act(mem, t, 1, 1, tackle, script = scripts.isConfused)), "it may yet hurt itself")
        // It did not: the move's own script runs.
        mem.put32(map.scriptCurrInstr, 0L)
        t.read()
        assertEquals(listOf("TACKLE"), seen(t.read()))
        // The next turn it hurts itself: the game marks it unable while the pause is showing.
        endTurn(mem, t)
        act(mem, t, 0, 0, thundershock)
        act(mem, t, 1, 1, growl, unable = true, script = scripts.isConfused)
        mem.put32(map.scriptCurrInstr, 0L)
        t.read()
        assertEquals(listOf("TACKLE"), seen(t.read()), "Growl was never used")
    }

    @Test
    fun `a move Mimic gave it is not one of its own`() {
        val (mem, t) = battle(listOf(rattata to listOf(tackle, mimic)))
        act(mem, t, 0, 0, thundershock)
        act(mem, t, 1, 1, mimic)
        endTurn(mem, t)
        act(mem, t, 0, 0, thundershock)
        assertEquals(listOf("MIMIC"), seen(act(mem, t, 1, 1, thundershock)))
    }

    @Test
    fun `Focus Punch counts from its set-up`() {
        // Battle.lua:452-470: it is not the last move used until its second part, so the set-up message names it.
        val (mem, t) = battle(listOf(machop to listOf(focusPunch, tackle)))
        mem.put8(map.battlerAttacker, 1)
        mem.put32(map.scriptCurrInstr, map.moveScripts!!.focusPunchSetUp)
        t.read()
        assertEquals(listOf("FOCUS PUNCH"), seen(t.read()))
    }

    @Test
    fun `a new battle starts with nothing seen`() {
        val (mem, t) = battle(listOf(rattata to listOf(tackle, growl)))
        act(mem, t, 0, 0, thundershock)
        assertEquals(listOf("TACKLE"), seen(act(mem, t, 1, 1, tackle)))
        mem.put8(map.battleOutcome, 1)
        mem.put32(map.battleMainFunc, map.returnToOverworld)
        val after = t.read()
        assertNull(after.enemy)
        assertEquals(emptyList(), after.enemyMovesThisBattle)
        // The next battle, against the same species: the game clears gBattleResults and starts at action 0.
        mem.put8(map.battleOutcome, 0)
        mem.put32(map.battleMainFunc, map.handleTurnAction)
        mem.put16(map.battleResults + 0x22, 0); mem.put16(map.battleResults + 0x24, 0)
        mem.put8(map.currentTurnActionNumber, 0)
        t.read()
        val s = t.read()
        assertTrue(s.inBattle)
        assertEquals(emptyList(), seen(s))
        assertEquals(emptyList(), s.enemyMovesThisBattle)
        assertEquals(emptyList(), seen(t.read()), "and nothing until it moves")
    }

    @Test
    fun `every game knows where to read and which scripts to wait out`() {
        for (m in listOf(GameMap.EMERALD_U, GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U)) {
            assertTrue(m.currentTurnActionNumber != 0L && m.actionsByTurnOrder != 0L && m.hitMarker != 0L, m.name)
            assertEquals(11, m.moveScripts!!.delayed.size, m.name)
        }
        // The revisions differ in their ROM scripts only.
        assertTrue(GameMap.FIRERED_U_V11.moveScripts != GameMap.FIRERED_U_V10.moveScripts)
        assertTrue(GameMap.LEAFGREEN_U.moveScripts != GameMap.FIRERED_U_V10.moveScripts)
        assertTrue(GameMap.SAPPHIRE_U.moveScripts != GameMap.RUBY_U.moveScripts)
    }
}
