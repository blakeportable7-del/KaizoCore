package com.ironmonone.tracker

/**
 * Pokémon Crystal, read into the same [TrackerState] the Gen 3 panel draws.
 *
 * Reference: seadogstingray/Ironmon-gen-2-tracker (MIT), a fork of the Gen 1
 * tracker, itself a fork of besteon's - so the panel is already its panel.
 * Its GameSettings.setGen2Addresses writes WRAM as "0x0200xxxx": that is not
 * a Game Boy address, it is an offset into the core's SYSTEM_RAM, which is
 * exactly what LibretroDroid's fallback read does (`0x02000000 + offset`,
 * see libretrodroid.cpp readMemory). Crystal keeps its game data in WRAM
 * bank 1, so a pokecrystal address 0xDxxx is offset 0x1xxx; bank 0 (0xCxxx)
 * is offset 0x0xxx. Every WRAM offset below is that arithmetic applied to
 * pokecrystal's wram.asm, and it agrees with the reference except where
 * noted.
 *
 * Nothing about a species is a table: types, base stats, move power and type
 * come out of the RANDOMIZED ROM (BaseData at 0x51424, Moves at 0x41AFB -
 * the reference's gBaseStats and gBattleMoves with the 0x08 domain prefix
 * dropped). Only names are bundled.
 */
class GbcTracker(
    private val memory: MemoryReader,
    /** The randomized ROM as loaded, for the tables the game itself reads. */
    private val rom: ByteArray,
    /** Which Gen 2 game's addresses to use; null when the header names none of them. */
    private val map: Gen2Map? = Gen2Map.forRom(rom),
) {
    companion object {
        const val RAM = 0x02000000L

        // ---- WRAM offsets (pokecrystal wram.asm, bank 1 -> +0x1000) ----------
        /** wPartyCount 0xDCD7. The reference has 0x0CD7 here, which is bank 0
         *  and cannot be right for a Crystal party; the count is validated
         *  against the species list and the structs rather than trusted. */
        const val PARTY_COUNT = 0x1CD7L
        const val PARTY_SPECIES = 0x1CD8L          // wPartySpecies, 6 + terminator
        const val PARTY_MONS = 0x1CDFL             // wPartyMon1 (reference pstats)
        const val PARTY_STRIDE = 48                // party_struct; the reference steps 44, Gen 1's size
        const val ENEMY_MON = 0x1206L              // wEnemyMon (reference estats)
        const val BATTLE_MODE = 0x122DL            // wBattleMode: 0 none, 1 wild, 2 trainer (reference gBattleTypeFlags)
        const val ENEMY_LAST_MOVE = 0x0608L        // wEnemyMoveStruct 0xC608 (reference eMove): its first byte is the move id. wCurEnemyMove is 0xC6E4
        const val JOHTO_BADGES = 0x1857L           // wJohtoBadges (reference badgeOffset)
        const val KANTO_BADGES = 0x1858L           // wKantoBadges
        const val NUM_ITEMS = 0x1892L              // wNumItems
        const val ITEMS = 0x1893L                  // wItems: (id, qty) pairs, 0xFF ends (reference bagPocket_Items_offset)
        const val ITEM_SLOTS = 20

        // ---- ROM ---------------------------------------------------------------
        const val BASE_STATS = 0x51424             // BaseData, 32 bytes per species, index species-1
        const val BASE_STRIDE = 32
        const val MOVES = 0x41AFB                  // Moves, 7 bytes per move, index move-1
        const val MOVE_STRIDE = 7

        /**
         * Gen 2 type ids to the Gen 3 ids the panel's chips and chart use.
         * Gen 2: 0 Normal 1 Fighting 2 Flying 3 Poison 4 Ground 5 Rock 6 Bird
         * 7 Bug 8 Ghost 9 Steel, then 20 Fire 21 Water 22 Grass 23 Electric
         * 24 Psychic 25 Ice 26 Dragon 27 Dark. Bird is the unused slot.
         */
        fun gen3Type(gen2: Int): Int = when (gen2) {
            in 0..5 -> gen2
            7 -> 6; 8 -> 7; 9 -> 8
            20 -> 10; 21 -> 11; 22 -> 12; 23 -> 13; 24 -> 14; 25 -> 15; 26 -> 16; 27 -> 17
            else -> 0
        }

        /** The reference's Gen 2 HealingItems: id to (amount, isPercent). */
        val HEALS: Map<Int, Pair<Int, Boolean>> = mapOf(
            18 to (20 to false),    // Potion
            17 to (50 to false),    // Super Potion
            16 to (200 to false),   // Hyper Potion
            15 to (100 to true),    // Max Potion
            14 to (100 to true),    // Full Restore
            46 to (50 to false),    // Fresh Water
            47 to (60 to false),    // Soda Pop
            48 to (80 to false),    // Lemonade
            72 to (100 to false),   // Moomoo Milk
            114 to (20 to false),   // Rage Candy Bar
            121 to (50 to false),   // Energy Powder
            122 to (200 to false),  // Energy Root
            139 to (20 to false),   // Berry Juice
            173 to (10 to false),   // Berry
            174 to (30 to false),   // Gold Berry
        )
    }

    private val speciesNames = HashMap<Int, String>()
    private val moveNames = HashMap<Int, String>()
    private val seenForSpecies = -1
    private var enemySpeciesSeen = -1
    private val movesSeen = LinkedHashSet<Int>()

    init {
        load("/gen2/species.tsv", speciesNames)
        load("/gen2/moves.tsv", moveNames)
    }

    private fun load(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l ->
                val t = l.indexOf('\t')
                if (t > 0) l.substring(0, t).toIntOrNull()?.let { into[it] = l.substring(t + 1).trim() }
            }
        }
    }

    fun speciesName(id: Int): String = speciesNames[id] ?: "#$id"
    fun moveName(id: Int): String = moveNames[id] ?: "#$id"

    // ------------------------------------------------------------------ ROM tables

    private fun romU8(off: Int): Int = if (off in rom.indices) rom[off].toInt() and 0xFF else 0

    fun baseStats(species: Int): BaseStats? {
        if (species !in 1..251) return null
        val b = m.baseStats + (species - 1) * BASE_STRIDE
        if (b + BASE_STRIDE > rom.size) return null
        return BaseStats(
            hp = romU8(b + 1), atk = romU8(b + 2), def = romU8(b + 3),
            spe = romU8(b + 4), spAtk = romU8(b + 5), spDef = romU8(b + 6),
            type1 = gen3Type(romU8(b + 7)), type2 = gen3Type(romU8(b + 8)),
            ability1 = 0, ability2 = 0,
        )
    }

    /** power, gen3 type, accuracy percent, pp - or null for move 0 / out of table. */
    fun moveData(id: Int): IntArray? {
        if (id !in 1..251) return null
        val at = m.moves + (id - 1) * MOVE_STRIDE
        if (at + MOVE_STRIDE > rom.size) return null
        // Accuracy is a byte out of 255 in Gen 2; 0xFF is 100%.
        val acc = (romU8(at + 4) * 100 + 127) / 255
        return intArrayOf(romU8(at + 2), gen3Type(romU8(at + 3)), acc, romU8(at + 5))
    }

    private fun moveRow(id: Int, pp: Int, ppMax: Int?): MoveRow {
        val d = moveData(id)
        return MoveRow(
            id = id, name = moveName(id), pp = pp, ppMax = ppMax,
            power = d?.get(0), acc = d?.get(2), type = d?.get(1),
            category = d?.let { gen3Category(it[1], it[0]) },
        )
    }

    // ------------------------------------------------------------------ WRAM

    private fun ram(off: Long, len: Int): ByteArray = memory.read(RAM + off, len)
    private val m: Gen2Map get() = map ?: Gen2Map.CRYSTAL   // read() refuses first when map is null
    private fun be16(b: ByteArray, o: Int): Int = (b.u8(o) shl 8) or b.u8(o + 1)

    private fun statusName(s: Int): String = when {
        s and 0x07 != 0 -> "SLP"; s and 0x08 != 0 -> "PSN"; s and 0x10 != 0 -> "BRN"
        s and 0x20 != 0 -> "FRZ"; s and 0x40 != 0 -> "PAR"; else -> ""
    }

    /** One party_struct (48 bytes) as the Gen 3 decoder's Mon, so the panel can draw it. */
    private fun partyMon(b: ByteArray, species: Int): PokemonDecoder.Mon? {
        if (b.size < PARTY_STRIDE) return null
        val level = b.u8(31)
        val curHp = be16(b, 34); val maxHp = be16(b, 36)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        val dv = (b.u8(21) shl 8) or b.u8(22)
        val atkDv = (dv shr 12) and 0xF; val defDv = (dv shr 8) and 0xF
        val spdDv = (dv shr 4) and 0xF; val spcDv = dv and 0xF
        val hpDv = ((atkDv and 1) shl 3) or ((defDv and 1) shl 2) or ((spdDv and 1) shl 1) or (spcDv and 1)
        return PokemonDecoder.Mon(
            pid = (species.toLong() shl 16) or ((b.u8(6).toLong() shl 8) or b.u8(7).toLong()),
            level = level, nickname = "", species = species, heldItem = b.u8(1), friendship = b.u8(27),
            moves = List(4) { b.u8(2 + it) }, pp = List(4) { b.u8(23 + it) and 0x3F },
            ivs = listOf(hpDv, atkDv, defDv, spdDv, spcDv, spcDv), evs = List(6) { 0 },
            ppUps = List(4) { (b.u8(23 + it) shr 6) and 0x3 },
            abilitySlot = 0, nature = 0, shiny = atkDv == 2 && defDv == 10 && spdDv == 10 && spcDv == 10,
            status = b.u8(32).toLong(), curHp = curHp, maxHp = maxHp,
            atk = be16(b, 38), def = be16(b, 40), spe = be16(b, 42), spAtk = be16(b, 44), spDef = be16(b, 46),
        )
    }

    private fun tracked(m: PokemonDecoder.Mon): TrackedMon {
        val base = baseStats(m.species)
        val rows = m.moves.indices.filter { m.moves[it] != 0 }.map { i ->
            val d = moveData(m.moves[i])
            val basePp = d?.get(3)
            moveRow(m.moves[i], m.pp[i], basePp?.let { it + (it / 5) * m.ppUps[i] })
        }
        return TrackedMon(
            mon = m, speciesName = speciesName(m.species),
            moveNames = rows.map { it.name }, base = base,
            abilityName = "-", itemName = if (m.heldItem == 0) "-" else "#${m.heldItem}",
            moveRows = rows, statusCondition = statusName(m.status.toInt()),
        )
    }

    private fun readParty(): List<TrackedMon> {
        val count = ram(m.partyCount, 1).let { if (it.isEmpty()) 0 else it.u8(0) }.coerceIn(0, 6)
        val species = ram(m.partySpecies, 7)
        val out = ArrayList<TrackedMon>(6)
        for (i in 0 until 6) {
            val sp = if (species.size > i) species.u8(i) else 0
            // 0xFF terminates the list; 0 is empty. Read structs past the count
            // byte only while the list agrees, so a wrong count cannot invent.
            if (sp == 0xFF || sp == 0) break
            if (i >= count && count > 0) break
            if (sp !in 1..251) break
            val mon = partyMon(ram(m.partyMons + i * PARTY_STRIDE.toLong(), PARTY_STRIDE), sp) ?: break
            out += tracked(mon)
        }
        return out
    }

    /** battle_struct (wEnemyMon): species 0, item 1, moves 2, dvs 6, pp 8, happiness 12, level 13, status 14, hp 16, maxhp 18, stats 20.., types 30-31. */
    private fun readEnemy(): EnemyInfo? {
        val b = ram(m.enemyMon, 32)
        if (b.size < 32) return null
        val species = b.u8(0)
        if (species !in 1..251) return null
        val level = b.u8(13); val curHp = be16(b, 16); val maxHp = be16(b, 18)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        if (species != enemySpeciesSeen) { enemySpeciesSeen = species; movesSeen.clear() }
        // The move the opponent last used: seen once it is used, never before.
        ram(m.enemyMove, 1).let { if (it.isNotEmpty() && it.u8(0) in 1..251) movesSeen.add(it.u8(0)) }
        val base = baseStats(species)
        return EnemyInfo(
            species = species, speciesName = speciesName(species), level = level,
            curHp = curHp, maxHp = maxHp,
            type1 = gen3Type(b.u8(30)), type2 = gen3Type(b.u8(31)),
            base = base,
            movesSeen = movesSeen.map { moveName(it) },
            moveRows = movesSeen.map { moveRow(it, moveData(it)?.get(3) ?: 0, null) },
            abilityGuess = "-",
            statusCondition = statusName(b.u8(14)),
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
            badgeSet = "GSC", diagnostics = "Game Boy game not known to the tracker", unreadable = true,
        )
        val party = readParty()
        val mode = ram(m.battleMode, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val inBattle = mode == 1 || mode == 2
        val enemy = if (inBattle) readEnemy() else run { enemySpeciesSeen = -1; movesSeen.clear(); null }
        val johto = ram(m.johtoBadges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val kanto = ram(m.kantoBadges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val lead = party.firstOrNull()
        val heals = readHeals(lead?.mon?.maxHp ?: 0)
        return TrackerState(
            partyCount = party.size,
            party = party,
            inBattle = inBattle && enemy != null,
            isWildBattle = inBattle && mode == 1,
            enemy = enemy,
            badges = johto or (kanto shl 8),
            badgeSet = "GSC",
            healPercent = heals.first,
            healCount = heals.second,
            gameOver = lead?.let { if (it.mon.curHp == 0 && it.mon.level > 0) GameOver.LOST else null },
            diagnostics = "${m.name}  party=%d mode=%d".format(party.size, mode),
            unreadable = party.isEmpty(),
        )
    }
}

/**
 * One Gen 2 game's addresses: WRAM offsets from 0x02000000 (bank 1 is +0x1000)
 * and the two ROM tables the tracker reads out of the randomized file.
 *
 * CRYSTAL is the Gen 2 reference tracker's set, kept in [GbcTracker]'s
 * companion where the tests already pin it. GS was not in any tracker: it is
 * computed from the pokegold disassembly by tools/wram_layout.py, which walks
 * ram/wram.asm and layout.link the way rgblink does, and was first proven on
 * pokecrystal against every CRYSTAL address here (10 of 10 exact) before its
 * pokegold output was believed. Gold and Silver share one WRAM layout (the
 * `-D_GOLD` / `-D_SILVER` builds differ only in constants), and the tool
 * gives the same numbers for both. The ROM tables are the randomizer's own
 * gen2_offsets.ini entries for Gold (U), which Silver (U) copies.
 */
data class Gen2Map(
    val name: String,
    val partyCount: Long, val partySpecies: Long, val partyMons: Long,
    val enemyMon: Long, val battleMode: Long, val enemyMove: Long,
    val johtoBadges: Long, val kantoBadges: Long, val numItems: Long, val items: Long,
    val baseStats: Int, val moves: Int,
) {
    companion object {
        val CRYSTAL = Gen2Map(
            name = "Crystal",
            partyCount = GbcTracker.PARTY_COUNT, partySpecies = GbcTracker.PARTY_SPECIES, partyMons = GbcTracker.PARTY_MONS,
            enemyMon = GbcTracker.ENEMY_MON, battleMode = GbcTracker.BATTLE_MODE, enemyMove = GbcTracker.ENEMY_LAST_MOVE,
            johtoBadges = GbcTracker.JOHTO_BADGES, kantoBadges = GbcTracker.KANTO_BADGES,
            numItems = GbcTracker.NUM_ITEMS, items = GbcTracker.ITEMS,
            baseStats = GbcTracker.BASE_STATS, moves = GbcTracker.MOVES,
        )

        /** pokegold: wPartyCount DA22, wPartySpecies DA23, wPartyMons DA2A, wEnemyMon D0EF, wBattleMode D116,
         *  wEnemyMoveStruct CAE8, wJohtoBadges D57C, wKantoBadges D57D, wNumItems D5B7, wItems D5B8. */
        val GS = Gen2Map(
            name = "Gold/Silver",
            partyCount = 0x1A22L, partySpecies = 0x1A23L, partyMons = 0x1A2AL,
            enemyMon = 0x10EFL, battleMode = 0x1116L, enemyMove = 0x0AE8L,
            johtoBadges = 0x157CL, kantoBadges = 0x157DL, numItems = 0x15B7L, items = 0x15B8L,
            baseStats = 0x51B0B, moves = 0x41AFE,
        )

        /**
         * The game from the cartridge header title at 0x134: rgbfix stamps
         * "PM_CRYSTAL" (pokecrystal Makefile), "POKEMON_GLD" and "POKEMON_SLV"
         * (pokegold Makefile). Null for anything else: no default map, since a
         * wrong one reads confident garbage.
         */
        fun forRom(rom: ByteArray): Gen2Map? {
            if (rom.size < 0x150) return null
            val title = String(rom, 0x134, 11, Charsets.US_ASCII)
            return when {
                title.startsWith("PM_CRYSTAL") -> CRYSTAL
                title == "POKEMON_GLD" || title == "POKEMON_SLV" -> GS
                else -> null
            }
        }
    }
}
