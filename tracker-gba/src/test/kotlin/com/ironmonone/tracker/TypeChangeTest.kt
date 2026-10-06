package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Type changes in battle on the Game Boy Advance builds that need no ROM (Blake, 2026-10-06: "what about moves that
 * change the type of the pokemon?", "the conversion moves, the ability that changes the type based on the type of move
 * that hits them", "the effectiveness arrows on the moves should adapt, right?"). Synthetic memory at each game's own
 * addresses, the battle structs written the way each mechanic's handler writes them.
 */
class TypeChangeTest {

    private class Mem {
        val bytes = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { bytes[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun put(a: Long, b: ByteArray) { b.forEachIndexed { i, v -> bytes[a + i] = v } }
        fun read(a: Long, n: Int) = ByteArray(n) { bytes[a + it] ?: 0 }
        fun reader() = MemoryReader { address, length -> read(address, length) }
    }

    /** A vanilla-layout party record (personality a multiple of 24), as Gen3BattleReadsTest builds them. */
    private fun encodeMon(pid: Long, species: Int, level: Int, moves: List<Int>): ByteArray {
        val otId = 0x1234L
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        moves.forEachIndexed { i, id -> plain[12 + i * 2] = id.toByte(); plain[13 + i * 2] = (id shr 8).toByte(); plain[20 + i] = 10 }
        val mon = ByteArray(100)
        mon.putU32(0, pid); mon.putU32(4, otId)
        val key = pid xor otId
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4) xor key)
        mon[0x54] = level.toByte(); mon[0x56] = 40; mon[0x58] = 40
        return mon
    }

    private fun setUp(m: GameMap, mem: Mem, own: Int, ownTypes: Pair<Int, Int>, foe: Int, foeTypes: Pair<Int, Int>, moves: List<Int> = listOf(33)) {
        mem.put8(m.partyCount, 1)
        mem.put(m.party, encodeMon(24, own, 20, moves))
        mem.put(m.enemyParty, encodeMon(48, foe, 20, listOf(33)))
        fun base(species: Int, t: Pair<Int, Int>) {
            val at = m.baseStats + species.toLong() * m.baseStatsStride
            for (i in 0 until 6) mem.put8(at + i, 50)
            mem.put8(at + 6, t.first); mem.put8(at + 7, t.second)
        }
        base(own, ownTypes); base(foe, foeTypes)
        mem.put8(m.battlersCount, 2)
        mem.put8(m.battleOutcome, 0)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
        mem.put32(m.battleTypeFlags, 0)
        for ((battler, sp, t) in listOf(Triple(0, own, ownTypes), Triple(1, foe, foeTypes))) {
            val s = m.battleMons + battler.toLong() * m.battleMonSize
            mem.put16(s, sp)
            mem.put8(s + m.battleMonTypes, t.first); mem.put8(s + m.battleMonTypes + 1, t.second)
            mem.put16(s + m.battleMonHp, 40); mem.put8(s + m.battleMonHp + 2, 20); mem.put16(s + m.battleMonHp + 4, 40)
        }
    }

    private fun types(mem: Mem, m: GameMap, battler: Int, t1: Int, t2: Int) {
        val s = m.battleMons + battler.toLong() * m.battleMonSize + m.battleMonTypes
        mem.put8(s, t1); mem.put8(s + 1, t2)
    }

    /**
     * Every mechanic of the five games that changes a type writes the battle struct's two type bytes (pokeemerald
     * Cmd_tryconversiontypechange, Cmd_settypetorandomresistance, Cmd_settypebasedonterrain, and AbilityBattleEffects'
     * Color Change, Forecast's CastformDataTypeChange): each shows on the very next read, on its own side's card, and
     * the other card's matchups follow.
     */
    @Test
    fun `the five games' type changes show on the next read, on both sides`() {
        val m = GameMap.EMERALD_U
        // (mechanic, battler, new types, a move type, its effectiveness on the battler after)
        val cases = listOf(
            listOf("Conversion makes yours Grass", 0, 12, 12, 13, 0.5),
            listOf("Conversion 2 makes yours Steel", 0, 8, 8, 15, 0.5),
            listOf("Camouflage makes yours Ground", 0, 4, 4, 13, 0.0),
            listOf("Color Change makes the foe Electric", 1, 13, 13, 4, 2.0),
            listOf("Castform in the sun is Fire", 1, 10, 10, 11, 2.0),
        )
        for (c in cases) {
            val what = c[0] as String; val battler = c[1] as Int; val t1 = c[2] as Int; val t2 = c[3] as Int
            val moveType = c[4] as Int; val after = c[5] as Double
            val mem = Mem()
            setUp(m, mem, own = 1, ownTypes = 11 to 11, foe = 25, foeTypes = 0 to 0)
            val t = GbaTracker(mem.reader(), m)
            t.read(); val before = t.read()
            assertEquals(listOf(11, 11), before.party[0].battleTypes, what)
            assertEquals(listOf(0, 0), before.enemy!!.battleTypes, what)
            types(mem, m, battler, t1, t2)
            val s = t.read()
            val shown = if (battler == 0) s.party[0].battleTypes else s.enemy!!.battleTypes
            assertEquals(listOf(t1, t2), shown, what)
            val card = if (battler == 0) s.party[0].base!!.let { it.type1 to it.type2 } else s.enemy!!.let { it.type1 to it.type2 }
            assertEquals(t1 to t2, card, "$what: the card's type icons")
            assertEquals(after, MoveRules.effectiveness(85, moveType, "SPE", shown!!, power = "90"), "$what: the arrows aimed at it")
        }
    }

    // ------------------------------------------------------------------ MaxDex 1.0

    private val maxDex = GameMap.MAXDEX_FR_10

    /** A MaxDex move row in the ROM's own table: u16 effect, power +2, type +3, category +4, accuracy +5, PP +6. */
    private fun move(mem: Mem, id: Int, power: Int, type: Int, category: Int) {
        val a = maxDex.battleMoves + id.toLong() * 12
        mem.put8(a + 2, power); mem.put8(a + 3, type); mem.put8(a + 4, category); mem.put8(a + 5, 100); mem.put8(a + 6, 10)
    }

    private fun maxDexGame(ownAbility: Int, moves: List<Int>, item: Int = 0): Pair<Mem, GbaTracker> {
        val mem = Mem()
        setUp(maxDex, mem, own = 25, ownTypes = 13 to 13, foe = 39, foeTypes = 0 to 18, moves = moves)
        mem.put16(maxDex.battleMons + 0x20, ownAbility)
        mem.put16(maxDex.battleMons + 0x30, item)
        move(mem, 33, 40, 0, 0)          // Tackle
        move(mem, 455, 100, 0, 1)        // Judgment
        move(mem, 304, 90, 0, 1)         // Hyper Voice
        move(mem, 731, 50, 0, 1)         // Terrain Pulse
        mem.put8(maxDex.battleType3, 9); mem.put8(maxDex.battleType3 + 1, 9)
        return mem to GbaTracker(mem.reader(), maxDex)
    }

    private fun GbaTracker.settle(): TrackerState { read(); return read() }

    @Test
    fun `MaxDex keeps a third type apart and its Roost and Protean write the battle struct`() {
        val (mem, t) = maxDexGame(ownAbility = 0, moves = listOf(33))
        assertEquals(listOf(0, 18), t.settle().enemy!!.battleTypes)
        // Forest's Curse on the foe: Grass at 0x02023DFA + its battler.
        mem.put8(maxDex.battleType3 + 1, 12)
        val cursed = t.read().enemy!!
        assertEquals(listOf(0, 18, 12), cursed.battleTypes)
        assertEquals(1.0, MoveRules.effectiveness(53, 10, "SPE", listOf(0, 18), power = "90", natDex = true))
        assertEquals(2.0, MoveRules.effectiveness(53, 10, "SPE", cursed.battleTypes!!, power = "90", natDex = true), "Fire: 2x on the Grass it gained")
        // Roost on yours: the Flying type byte becomes 9 for the turn (0x081AF2F2).
        types(mem, maxDex, 0, 2, 13)
        assertEquals(listOf(2, 13), t.read().party[0].battleTypes)
        types(mem, maxDex, 0, 9, 13)
        assertEquals(listOf(9, 13), t.read().party[0].battleTypes)
        // Protean before a Tackle: the struct's types become Normal (0x081AF54E).
        types(mem, maxDex, 0, 0, 0)
        assertEquals(listOf(0, 0), t.read().party[0].battleTypes)
    }

    @Test
    fun `MaxDex sets your moves' types by your -ate ability, held item, Liquid Voice and the terrain`() {
        // Pixilate (192): every Normal move with power turns Fairy, Judgment and Terrain Pulse too (MaxDex's -ate check,
        // last in its order, looks at the move's own type and power only).
        var s = maxDexGame(ownAbility = 192, moves = listOf(33, 304, 455, 731)).second.settle()
        assertEquals(listOf(18, 18, 18, 18), s.party[0].moveRows.map { it.type })
        // Liquid Voice (227): the sound move turns Water, the others stay.
        s = maxDexGame(ownAbility = 227, moves = listOf(33, 304)).second.settle()
        assertEquals(listOf(0, 11), s.party[0].moveRows.map { it.type })
        // Judgment with a Black Belt (hold effect 50): Fighting, read from the item table.
        val (mem, t) = maxDexGame(ownAbility = 0, moves = listOf(455, 731), item = 207)
        mem.put8(maxDex.itemNames + 207L * maxDex.itemStride + 18, 50)
        mem.put8(maxDex.terrain, 3)
        assertEquals(listOf(1, 13), t.settle().party[0].moveRows.map { it.type }, "Judgment Fighting, Terrain Pulse Electric")
    }

    /** The hidden information fence (hard rule): an opponent's Pixilate changes nothing until the game has shown it. */
    @Test
    fun `an unrevealed opponent Pixilate does not change anything shown`() {
        val (mem, t) = maxDexGame(ownAbility = 0, moves = listOf(33))
        t.settle()
        val foe = maxDex.battleMons + maxDex.battleMonSize
        mem.put16(foe + 0x20, 192)
        val struct = mem.read(foe, maxDex.battleMonSize)
        val tackle = assertNotNull(t.moveRowFor(33))
        assertEquals(0, t.battleMoveRows(listOf(tackle), 1, struct, own = false, species = 39).single().type, "not shown: Normal")
        // Its pop-up: the tracker records what the game showed, and then its Tackle shows Fairy.
        t.noteShownAbility(39, "Pixilate")
        assertEquals(18, t.battleMoveRows(listOf(tackle), 1, struct, own = false, species = 39).single().type)
        // Another species' reveal does not count for this one.
        assertEquals(0, t.battleMoveRows(listOf(tackle), 1, struct, own = false, species = 40).single().type)
    }
}
