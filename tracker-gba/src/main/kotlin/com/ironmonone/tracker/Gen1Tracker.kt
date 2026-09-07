package com.ironmonone.tracker


/**
 * The Gen 1 tracker: Red, Blue and Yellow through Gambatte's WRAM, producing
 * the same [TrackerState] the Gen 3 panel draws.
 *
 * Sources, and nothing else:
 * - The Gen 1 reference tracker (Ironmon-gen-tracker, MIT): the party is six
 *   44-byte slots from `pstats`, the opponent is one battle struct at
 *   `estats`, the enemy's move is read from `eMove` every tick and recorded
 *   when it is one the opponent knows, `gBattleTypeFlags` is the in-battle
 *   byte, badges one byte, the bag a count then (id, qty) pairs. Its table
 *   is Red's; Yellow is "every value minus one" in its own words, except
 *   the battle-side values in the C block.
 * - pokered and pokeyellow, through tools/wram_layout.py, which gives the
 *   same numbers with their names: wPartyCount D163/D162, wPartyMons
 *   D16B/D16A, wEnemyMon CFE5/CFE4, wIsInBattle D057/D056, wEnemyMoveNum
 *   CFCC/CFCB, wObtainedBadges D356/D355, wNumBagItems D31D/D31C, wBagItems
 *   D31E/D31D. The struct layouts are macros/ram.asm: party_struct 44 bytes
 *   (species 0, HP 1, status 4, types 5-6, moves 8, DVs 27, PP 29, level 33,
 *   max HP 34, stats 36..), battle_struct 29 bytes (species 0, HP 1, status
 *   4, types 5-6, moves 8, DVs 12, level 14, max HP 15, stats 17.., PP 25).
 * - The randomizer's gen1_offsets.ini for the ROM tables it writes: base
 *   stats 28 bytes per DEX number (hp 1, atk 2, def 3, spd 4, spc 5, types
 *   6-7), Mew apart in Red at MewStatsOffset, moves 6 bytes (power 2, type
 *   3, accuracy 4 of 255, pp 5), and the PokedexOrder table that turns the
 *   game's INTERNAL species id, which is what every RAM byte holds, into a
 *   dex number, which is what names, sprites and the base-stat table key on.
 *
 * Gen 1 has no held items and no abilities; those lines read "-".
 */
class Gen1Tracker(
    private val memory: MemoryReader,
    private val rom: ByteArray,
    private val map: Gen1Map? = Gen1Map.forRom(rom),
) {
    companion object {
        const val RAM = GbcTracker.RAM
        const val PARTY_STRIDE = 44
        const val ENEMY_SIZE = 29
        const val BASE_STRIDE = 28
        const val MOVE_STRIDE = 6
        const val ITEM_SLOTS = 20

        /** Gen 1 healing items: id to (amount, isPercent). Ids from pokered's item_constants.asm; amounts as the reference's table. */
        val HEALS: Map<Int, Pair<Int, Boolean>> = mapOf(
            0x14 to (20 to false),   // Potion
            0x13 to (50 to false),   // Super Potion
            0x12 to (200 to false),  // Hyper Potion
            0x11 to (100 to true),   // Max Potion
            0x10 to (100 to true),   // Full Restore
            0x3C to (50 to false),   // Fresh Water
            0x3D to (60 to false),   // Soda Pop
            0x3E to (80 to false),   // Lemonade
        )
    }

    private val speciesNames = HashMap<Int, String>()
    private val moveNames = HashMap<Int, String>()
    private var enemySpeciesSeen = -1
    private val movesSeen = LinkedHashSet<Int>()
    private var lastCount = 0

    init {
        load("/gen2/species.tsv", speciesNames)   // dex numbers 1..151 are the same names
        load("/gen2/moves.tsv", moveNames)        // move ids 1..165 are Gen 1's
    }

    private fun load(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l -> val t = l.indexOf('\t'); if (t > 0) l.substring(0, t).toIntOrNull()?.let { into[it] = l.substring(t + 1).trim() } }
        }
    }

    private val m: Gen1Map get() = map ?: Gen1Map.RED_BLUE

    // ------------------------------------------------------------------ ROM tables
    private fun romU8(off: Int): Int = if (off in rom.indices) rom[off].toInt() and 0xFF else 0

    /** The dex number of an internal species id, from the ROM's own order table; 0 for a slot that is no Pokemon (MissingNo). */
    fun dexOf(internal: Int): Int = if (internal in 1..m.internalCount) romU8(m.dexOrder + internal - 1) else 0

    fun baseStats(dex: Int): BaseStats? {
        if (dex !in 1..151) return null
        val b = if (dex == 151 && m.mewStats != 0) m.mewStats else m.baseStats + (dex - 1) * BASE_STRIDE
        if (b + BASE_STRIDE > rom.size) return null
        val spc = romU8(b + 5)
        return BaseStats(
            hp = romU8(b + 1), atk = romU8(b + 2), def = romU8(b + 3), spe = romU8(b + 4),
            spAtk = spc, spDef = spc,                                   // one Special stat in Gen 1
            type1 = GbcTracker.gen3Type(romU8(b + 6)), type2 = GbcTracker.gen3Type(romU8(b + 7)),
            ability1 = 0, ability2 = 0, singleSpecial = true,
        )
    }

    /** power, gen3 type, accuracy percent, pp - or null for move 0 / out of table. */
    fun moveData(id: Int): IntArray? {
        if (id !in 1..165) return null
        val at = m.moves + (id - 1) * MOVE_STRIDE
        if (at + MOVE_STRIDE > rom.size) return null
        val acc = (romU8(at + 4) * 100 + 127) / 255
        return intArrayOf(romU8(at + 2), GbcTracker.gen3Type(romU8(at + 3)), acc, romU8(at + 5))
    }

    private fun moveRow(id: Int, pp: Int, ppMax: Int?): MoveRow {
        val d = moveData(id)
        return MoveRow(
            id = id, name = moveNames[id] ?: "#$id", pp = pp, ppMax = ppMax,
            power = d?.get(0), acc = d?.get(2), type = d?.get(1),
            category = d?.let { gen3Category(it[1], it[0]) },
        )
    }

    // ------------------------------------------------------------------ RAM
    private fun ram(off: Long, len: Int): ByteArray = memory.read(RAM + off, len)
    private fun be16(b: ByteArray, o: Int): Int = (b.u8(o) shl 8) or b.u8(o + 1)

    /** Gen 1 status byte: bits 0-2 sleep turns, 3 poison, 4 burn, 5 freeze, 6 paralysis. */
    private fun statusName(s: Int): String = when {
        s and 0x07 != 0 -> "SLP"; s and 0x08 != 0 -> "PSN"; s and 0x10 != 0 -> "BRN"
        s and 0x20 != 0 -> "FRZ"; s and 0x40 != 0 -> "PAR"; else -> ""
    }

    /** One party_struct (44 bytes) as the Gen 3 decoder's Mon, so the panel can draw it. Species is converted to its dex number. */
    private fun partyMon(b: ByteArray): PokemonDecoder.Mon? {
        if (b.size < PARTY_STRIDE) return null
        val dex = dexOf(b.u8(0)); if (dex == 0) return null
        val level = b.u8(33); val curHp = be16(b, 1); val maxHp = be16(b, 34)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        val dv = be16(b, 27)
        val atkDv = (dv shr 12) and 0xF; val defDv = (dv shr 8) and 0xF
        val spdDv = (dv shr 4) and 0xF; val spcDv = dv and 0xF
        val hpDv = ((atkDv and 1) shl 3) or ((defDv and 1) shl 2) or ((spdDv and 1) shl 1) or (spcDv and 1)
        val spc = be16(b, 42)
        return PokemonDecoder.Mon(
            pid = (dex.toLong() shl 16) or ((b.u8(12).toLong() shl 8) or b.u8(13).toLong()),
            level = level, nickname = "", species = dex, heldItem = 0, friendship = 0,
            moves = List(4) { b.u8(8 + it) }, pp = List(4) { b.u8(29 + it) and 0x3F },
            ivs = listOf(hpDv, atkDv, defDv, spdDv, spcDv, spcDv), evs = List(6) { 0 },
            ppUps = List(4) { (b.u8(29 + it) shr 6) and 0x3 },
            abilitySlot = 0, nature = 0, shiny = false,
            status = b.u8(4).toLong(), curHp = curHp, maxHp = maxHp,
            atk = be16(b, 36), def = be16(b, 38), spe = be16(b, 40), spAtk = spc, spDef = spc,
        )
    }

    /** The panel's card for any mon, through this ROM's tables. Public for the screenshot demo. */
    fun trackedOf(mon: PokemonDecoder.Mon): TrackedMon = tracked(mon)
    fun speciesName(dex: Int): String = speciesNames[dex] ?: "#$dex"
    fun moveRowOf(id: Int, pp: Int, ppMax: Int?): MoveRow = moveRow(id, pp, ppMax)

    private fun tracked(mon: PokemonDecoder.Mon): TrackedMon {
        val base = baseStats(mon.species)
        val rows = mon.moves.indices.filter { mon.moves[it] != 0 }.map { i ->
            val basePp = moveData(mon.moves[i])?.get(3)
            moveRow(mon.moves[i], mon.pp[i], basePp?.let { it + (it / 5) * mon.ppUps[i] })
        }
        return TrackedMon(
            mon = mon, speciesName = speciesNames[mon.species] ?: "#${mon.species}",
            moveNames = rows.map { it.name }, base = base, abilityName = "-", itemName = "-",
            moveRows = rows, statusCondition = statusName(mon.status.toInt()),
        )
    }

    private fun readParty(): List<TrackedMon> {
        val raw = ram(m.partyCount, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        lastCount = raw
        val count = raw.coerceIn(0, 6)
        val species = ram(m.partySpecies, 7)
        val out = ArrayList<TrackedMon>(6)
        for (i in 0 until 6) {
            val sp = if (species.size > i) species.u8(i) else 0
            if (sp == 0xFF || sp == 0) break
            if (i >= count && count > 0) break
            val mon = partyMon(ram(m.partyMons + i * PARTY_STRIDE.toLong(), PARTY_STRIDE)) ?: break
            out += tracked(mon)
        }
        return out
    }

    private fun readEnemy(): EnemyInfo? {
        val b = ram(m.enemyMon, ENEMY_SIZE)
        if (b.size < ENEMY_SIZE) return null
        val dex = dexOf(b.u8(0)); if (dex == 0) return null
        val level = b.u8(14); val curHp = be16(b, 1); val maxHp = be16(b, 15)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        if (dex != enemySpeciesSeen) { enemySpeciesSeen = dex; movesSeen.clear() }
        // The reference's rule: the enemy's move byte is recorded only when it is a move the opponent knows.
        val known = (0 until 4).map { b.u8(8 + it) }
        ram(m.enemyMove, 1).let { if (it.isNotEmpty() && it.u8(0) in 1..165 && it.u8(0) in known) movesSeen.add(it.u8(0)) }
        return EnemyInfo(
            species = dex, speciesName = speciesNames[dex] ?: "#$dex", level = level, curHp = curHp, maxHp = maxHp,
            type1 = GbcTracker.gen3Type(b.u8(5)), type2 = GbcTracker.gen3Type(b.u8(6)), base = baseStats(dex),
            movesSeen = movesSeen.map { moveNames[it] ?: "#$it" },
            moveRows = movesSeen.map { moveRow(it, moveData(it)?.get(3) ?: 0, null) },
            abilityGuess = "-", statusCondition = statusName(b.u8(4)),
        )
    }

    private fun readHeals(maxHp: Int): Pair<Int, Int> {
        if (maxHp <= 0) return 0 to 0
        val n = ram(m.numItems, 1).let { if (it.isEmpty()) 0 else it.u8(0) }.coerceIn(0, ITEM_SLOTS)
        val b = ram(m.items, n * 2 + 1)
        var total = 0; var count = 0
        for (i in 0 until n) {
            if (b.size < i * 2 + 2) break
            val id = b.u8(i * 2); val qty = b.u8(i * 2 + 1)
            if (id == 0xFF) break
            val heal = HEALS[id] ?: continue
            if (qty !in 1..99) continue
            val each = if (heal.second) maxHp * heal.first / 100 else minOf(heal.first, maxHp)
            total += each * qty; count += qty
        }
        return (total * 100 / maxHp) to count
    }

    fun read(): TrackerState {
        if (map == null) return TrackerState(
            partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false,
            badgeSet = "RBY", diagnostics = "Game Boy game not known to the tracker", unreadable = true,
        )
        val party = readParty()
        // wIsInBattle: 0 none, 1 wild, 2 trainer; 0xFF marks a lost battle in the disassembly's comment.
        val mode = ram(m.inBattle, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val inBattle = mode == 1 || mode == 2
        val enemy = if (inBattle) readEnemy() else run { enemySpeciesSeen = -1; movesSeen.clear(); null }
        val badges = ram(m.badges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val lead = party.firstOrNull()
        val heals = readHeals(lead?.mon?.maxHp ?: 0)
        return TrackerState(
            partyCount = party.size, party = party,
            inBattle = inBattle && enemy != null, isWildBattle = inBattle && mode == 1, enemy = enemy,
            badges = badges, badgeSet = "RBY",
            healPercent = heals.first, healCount = heals.second,
            gameOver = lead?.let { if (it.mon.curHp == 0 && it.mon.level > 0) GameOver.LOST else null },
            diagnostics = "${m.name}  party=%d mode=%d".format(party.size, mode),
            unreadable = party.isEmpty() && lastCount != 0,
        )
    }
}

/**
 * One Gen 1 game's addresses: WRAM offsets from 0x02000000 (the whole
 * Game Boy address space is mapped there, so 0xD163 is 0xD163), and the ROM
 * tables. RED_BLUE is pokered's layout, YELLOW pokeyellow's; both computed
 * by tools/wram_layout.py and agreeing with the Gen 1 reference tracker's
 * table (its Red numbers, and its "Yellow is one less" rule for the D block).
 * ROM offsets are gen1_offsets.ini's [Red (U)] and [Yellow (U)] entries.
 */
data class Gen1Map(
    val name: String,
    val partyCount: Long, val partySpecies: Long, val partyMons: Long,
    val enemyMon: Long, val inBattle: Long, val enemyMove: Long,
    val badges: Long, val numItems: Long, val items: Long,
    val baseStats: Int, val mewStats: Int, val moves: Int, val dexOrder: Int, val internalCount: Int,
) {
    companion object {
        val RED_BLUE = Gen1Map(
            name = "Red/Blue",
            partyCount = 0xD163L, partySpecies = 0xD164L, partyMons = 0xD16BL,
            enemyMon = 0xCFE5L, inBattle = 0xD057L, enemyMove = 0xCFCCL,
            badges = 0xD356L, numItems = 0xD31DL, items = 0xD31EL,
            baseStats = 0x383DE, mewStats = 0x425B, moves = 0x38000, dexOrder = 0x41024, internalCount = 190,
        )
        val YELLOW = Gen1Map(
            name = "Yellow",
            partyCount = 0xD162L, partySpecies = 0xD163L, partyMons = 0xD16AL,
            enemyMon = 0xCFE4L, inBattle = 0xD056L, enemyMove = 0xCFCBL,
            badges = 0xD355L, numItems = 0xD31CL, items = 0xD31DL,
            baseStats = 0x383DE, mewStats = 0, moves = 0x38000, dexOrder = 0x410B1, internalCount = 190,
        )

        /** From the cartridge header title: "POKEMON RED", "POKEMON BLUE" (pokered's rgbfix titles) or "POKEMON YELLOW". */
        fun forRom(rom: ByteArray): Gen1Map? {
            if (rom.size < 0x150) return null
            val title = String(rom, 0x134, 16, Charsets.US_ASCII).substringBefore(' ').trim()
            return when (title) {
                "POKEMON RED", "POKEMON BLUE" -> RED_BLUE
                "POKEMON YELLOW" -> YELLOW
                else -> null
            }
        }
    }
}
