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
 *
 * It also answers the panel's lookups ([GbLookups]): move summaries, weight,
 * evolution and weaknesses from the Gen 1 reference's tables and chart, and
 * the learn levels out of the ROM.
 */
class Gen1Tracker(
    private val memory: MemoryReader,
    private val rom: ByteArray,
    private val map: Gen1Map? = Gen1Map.forRom(rom),
) : GbLookups, ActionMenuGate {
    companion object {
        const val RAM = GbcTracker.RAM

        /**
         * The menu bytes HandleMenuInput reads, wTopMenuItemY 0xCC24 to wLastMenuItem 0xCC2A, and the tile map wTileMap
         * 0xC3A0, as offsets: the same in pokered and pokeyellow (tools/wram_layout.py).
         */
        const val MENU = 0x0C24L
        const val TILE_MAP = 0x03A0L
        const val CURSOR_TILE = 0xED   // "▶", pokered's charmap

        /** wPlayerMonNumber 0xCC2F, the party index of the player's Pokemon in battle: the same in both games. */
        const val PLAYER_MON_NUMBER = 0x0C2FL

        /**
         * DisplayBattleMenu (pokered and pokeyellow engine/battle/core.asm) waits on HandleMenuInput with the cursor at
         * row 0x0E, in column 0x09 (FIGHT, ITEM) watching RIGHT and A, or column 0x0F (PKMN, RUN) watching LEFT and A,
         * two items a column. B is never watched there; every other battle menu (the move menu, the item list, the
         * party) watches B, so nothing else can look like it.
         */
        internal fun battleMenuUp(menu: ByteArray, tileMap: (col: Int, row: Int) -> Int): Boolean {
            if (menu.size < 7) return false
            val top = menu[0].toInt() and 0xFF
            val col = menu[1].toInt() and 0xFF
            val max = menu[4].toInt() and 0xFF
            val keys = menu[5].toInt() and 0xFF
            if (top != 0x0E || max != 1) return false
            if (!((col == 0x09 && keys == 0x11) || (col == 0x0F && keys == 0x21))) return false
            // And on screen, its cursor in that column: the menu bytes outlive the menu by a text box or two.
            return tileMap(col, 0x0E) == CURSOR_TILE || tileMap(col, 0x10) == CURSOR_TILE
        }
        const val PARTY_STRIDE = 44
        const val ENEMY_SIZE = 29
        const val BASE_STRIDE = 28
        const val MOVE_STRIDE = 6
        const val ITEM_SLOTS = 20

        /** The maps of POKEMON_TOWER_1F to 7F and the Silph Scope's item id: IsGhostBattle (pokered engine/battle/core.asm) says a wild battle there is a ghost without the scope. */
        val POKEMON_TOWER = 142..148
        const val SILPH_SCOPE = 0x48

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

        /**
         * The evolution stones, pokered's (and pokeyellow's) item_constants.asm:
         * MOON_STONE $0A, FIRE_STONE $20, THUNDER_STONE $21, WATER_STONE $22,
         * LEAF_STONE $2F, the ids the Gen 1 reference keys MiscData.EvolutionStones by.
         */
        val STONES: Map<Int, EvoText.GbStone> = mapOf(
            0x0A to EvoText.GbStone.MOON, 0x20 to EvoText.GbStone.FIRE, 0x21 to EvoText.GbStone.THUNDER,
            0x22 to EvoText.GbStone.WATER, 0x2F to EvoText.GbStone.LEAF,
        )
    }

    private val speciesNames = HashMap<Int, String>()
    private val moveNames = HashMap<Int, String>()
    private val enemyMoves = GbEnemyMoves()
    private val lastMove = GbLastMove()
    private var lastCount = 0
    /** The stones in the bag at the last read, for the evolution text (Program.GameData.evolutionStones). */
    @Volatile private var bagStones: Set<EvoText.GbStone> = emptySet()

    /** Item names by id (gen1/items.tsv, from pokered), for the Nuzlocke item check's warning. */
    private val itemNames = HashMap<Int, String>()

    init {
        load("/gen2/species.tsv", speciesNames)   // dex numbers 1..151 are the same names
        load("/gen2/moves.tsv", moveNames)        // move ids 1..165 are Gen 1's
        load("/gen1/items.tsv", itemNames)
    }

    private fun load(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l -> val t = l.indexOf('\t'); if (t > 0) l.substring(0, t).toIntOrNull()?.let { into[it] = l.substring(t + 1).trim() } }
        }
    }

    private val m: Gen1Map get() = map ?: Gen1Map.RED_BLUE

    override val generation: Int get() = 1
    private val tables by lazy { GbTables(1) }
    private val learnCache = java.util.concurrent.ConcurrentHashMap<Int, List<Int>>()

    // ------------------------------------------------------------------ ROM tables
    private fun romU8(off: Int): Int = if (off in rom.indices) rom[off].toInt() and 0xFF else 0

    /** The dex number of an internal species id, from the ROM's own order table; 0 for a slot that is no Pokemon (MissingNo). */
    fun dexOf(internal: Int): Int = if (internal in 1..m.internalCount) romU8(m.dexOrder + internal - 1) else 0

    override fun baseStats(dex: Int): BaseStats? {
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

    /** B-to-Run's gate (ActionMenuGate): a wild battle (wIsInBattle 1) with its action menu up, read live. */
    override fun isChoosingActionInWild(): Boolean {
        val m = map ?: return false
        val battle = ram(m.inBattle, 1)
        if (battle.isEmpty() || battle[0].toInt() != 1) return false
        return battleMenuUp(ram(MENU, 7)) { col, row ->
            ram(TILE_MAP + row * 20L + col, 1).firstOrNull()?.toInt()?.and(0xFF) ?: -1
        }
    }
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
    override fun speciesName(dex: Int): String = speciesNames[dex] ?: "#$dex"
    fun moveRowOf(id: Int, pp: Int, ppMax: Int?): MoveRow = moveRow(id, pp, ppMax)
    /** The row for a move seen in an earlier battle, at its base PP. */
    override fun moveRowFor(id: Int): MoveRow? = if (id <= 0) null else moveData(id)?.let { moveRow(id, it[3], null) }

    // ------------------------------------------------------------------ the panel's lookups (GbLookups)
    /** MoveData.lua `summary`, ids 1..165. */
    override fun moveDescription(id: Int): String? = if (id in 1..165) tables.moveDescription(id) else null
    /** PokemonData.lua `weight` (kg), dex 1..151. */
    override fun weight(species: Int): String? = if (species in 1..151) tables.weight(species) else null
    /** PokemonData.lua `evolution`, dex 1..151. */
    override fun evolution(species: Int): String? = if (species in 1..151) tables.evolution(species) else null
    /** PokemonData.getEffectiveness: the ROM's types against the Gen 1 tracker's chart (MoveData.lua TypeToEffectiveness). */
    override fun effectivenessAgainst(species: Int): Map<Double, List<String>> =
        baseStats(species)?.let { weaknessesOf(it.type1, it.type2, gen1 = true) } ?: emptyMap()

    /** The INTERNAL id of each dex number: [dexOf] the other way round. */
    private val internalOf: IntArray by lazy {
        IntArray(152).also { a -> for (i in 1..m.internalCount) { val d = dexOf(i); if (d in 1..151 && a[d] == 0) a[d] = i } }
    }

    /**
     * The levels a species learns new moves at, out of the ROM's evolution
     * and learnset table (gen1_offsets.ini PokemonMovesetsTableOffset): a
     * bank-local pointer per INTERNAL id to its evolutions (type 1 level and
     * 3 trade are 3 bytes, 2 item is 4) and then its (level, move) pairs, the
     * walk the randomizer's own Gen1RomHandler.getMovesLearnt does. Read live
     * because the randomizer rewrites it.
     *
     * The Gen 1 reference types these into PokemonData.lua instead (movelvls,
     * one list for Red/Blue and one for Yellow) and never reads the ROM for
     * them. The ROM is the game being played: where the two disagree the
     * table is wrong (Red/Blue Pidgey learns Sand-Attack at 5, which its list
     * leaves out), so the ROM is read here, as the Gen 2 reference does.
     */
    override fun learnLevels(species: Int): List<Int> = learnCache.getOrPut(species) {
        // No map is a Game Boy game this tracker does not know: its tables are somewhere else.
        val internal = if (map != null && species in 1..151 && m.movesets != 0) internalOf[species] else 0
        if (internal == 0) return@getOrPut emptyList()
        val at = m.movesets + (internal - 1) * 2
        if (at + 1 !in rom.indices) return@getOrPut emptyList()
        val pointer = romU8(at) or (romU8(at + 1) shl 8)
        gbLearnLevels(rom, gbOffset(m.movesets / 0x4000, pointer)) { type -> when (type) { 1, 3 -> 3; 2 -> 4; else -> null } }
    }

    private fun tracked(mon: PokemonDecoder.Mon): TrackedMon {
        val base = baseStats(mon.species)
        val rows = mon.moves.indices.filter { mon.moves[it] != 0 }.map { i ->
            val basePp = moveData(mon.moves[i])?.get(3)
            moveRow(mon.moves[i], mon.pp[i], basePp?.let { it + (it / 5) * mon.ppUps[i] })
        }
        // Utils.getMovesLearnedHeader: "Moves 3/7 (16)" from the learn levels.
        val header = LearnedMoves.of(learnLevels(mon.species), mon.level)
        return TrackedMon(
            mon = mon, speciesName = speciesNames[mon.species] ?: "#${mon.species}",
            moveNames = rows.map { it.name }, base = base, abilityName = "-", itemName = "-",
            moveRows = rows, statusCondition = statusName(mon.status.toInt()),
            movesLearned = header.learned, movesTotal = header.total, nextMoveLevel = header.next,
            // "Lv.12 (16)": the Gen 1 reference's evolution, coloured as it draws it (EvoText.forOwnGb).
            // Gen 1 has no friendship, and the reference reads it as 0 (Program.lua readNewPokemon).
            evo = EvoText.forOwnGb(evolution(mon.species), mon.level, bagStones, friendship = 0, TrackerPrefs.determineFriendship),
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

    /**
     * [p], your Pokemon on the field, with the types in wBattleMon (+5, +6), which Conversion rewrites (pokered
     * ConversionEffect copies the target's types there): the opponent's card has always read wEnemyMon's. Only while
     * wBattleMon holds [p]'s species (Transform puts the target's there).
     */
    private fun withBattleTypes(p: TrackedMon): TrackedMon {
        val base = p.base ?: return p
        if (m.battleMon == 0L) return p
        val b = ram(m.battleMon, 7)
        if (b.size < 7 || dexOf(b.u8(0)) != p.mon.species) return p
        val t1 = GbcTracker.gen3Type(b.u8(5)); val t2 = GbcTracker.gen3Type(b.u8(6))
        return p.copy(base = base.copy(type1 = t1, type2 = t2), battleTypes = listOf(t1, t2))
    }

    private fun readEnemy(): EnemyInfo? {
        val b = ram(m.enemyMon, ENEMY_SIZE)
        if (b.size < ENEMY_SIZE) return null
        val dex = dexOf(b.u8(0)); if (dex == 0) return null
        val level = b.u8(14); val curHp = be16(b, 1); val maxHp = be16(b, 15)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        // The reference's rule (GbEnemyMoves.seeGen1): the enemy's move byte is recorded only once the
        // opponent has moved, and only when it is one of the moves it had before that.
        val known = (0 until 4).map { b.u8(8 + it) }
        val turns = ram(m.aiTurns, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        enemyMoves.seeGen1(dex, ram(m.enemyMove, 1).let { if (it.isEmpty()) 0 else it.u8(0) }, known, turns)
        val movesSeen = enemyMoves.seen
        return EnemyInfo(
            species = dex, speciesName = speciesNames[dex] ?: "#$dex", level = level, curHp = curHp, maxHp = maxHp,
            type1 = GbcTracker.gen3Type(b.u8(5)), type2 = GbcTracker.gen3Type(b.u8(6)), base = baseStats(dex),
            movesSeen = movesSeen.map { moveNames[it] ?: "#$it" },
            // "Count enemy PP usage" (DataHelper.lua:277-288): a shown move the opponent has now takes
            // its live PP from the battle struct (moves at 8, PP at 25, the whole byte as
            // Program.readNewEnemyPokemon reads it); otherwise, and with the option off, base PP.
            moveRows = movesSeen.map { id ->
                val live = (0 until 4).firstOrNull { TrackerPrefs.countEnemyPp && b.u8(8 + it) == id }?.let { b.u8(25 + it) }
                moveRow(id, live ?: moveData(id)?.get(3) ?: 0, null)
            },
            abilityGuess = "-", statusCondition = statusName(b.u8(4)),
            // The opponent's evolution, all in the default colour (TrackerScreen.lua:765).
            evo = EvoText.forEnemy(evolution(dex)),
        )
    }

    /** The bag (wNumBagItems, then wBagItems' id and quantity pairs up to the 0xFF that ends them). */
    private fun readBag(): List<Pair<Int, Int>> {
        val n = ram(m.numItems, 1).let { if (it.isEmpty()) 0 else it.u8(0) }.coerceIn(0, ITEM_SLOTS)
        val b = ram(m.items, n * 2 + 1)
        val out = ArrayList<Pair<Int, Int>>(n)
        for (i in 0 until n) {
            if (b.size < i * 2 + 2) break
            val id = b.u8(i * 2); if (id == 0xFF) break
            out += id to b.u8(i * 2 + 1)
        }
        return out
    }

    /** Heals in Bag for a Pokemon with [maxHp]: the PC tracker's rounding, not integer division (HealTotals, rc32 audit P2 #99). */
    private fun readHeals(bag: List<Pair<Int, Int>>, maxHp: Int): HealTotals {
        val items = LinkedHashMap<Int, Int>()
        for ((id, qty) in bag) if (id in HEALS && qty in 1..99) items[id] = (items[id] ?: 0) + qty
        return HealTotals.of(items, maxHp) { id -> HEALS[id]?.let { it.first.toDouble() to it.second } }
    }

    /** "Game is considered over when", set by the app from its options; the lead by default. */
    @Volatile var lossCondition: LossCondition = LossCondition.LEAD

    /** The Champion beaten, the end of a Red, Blue or Yellow run (GbWin). */
    private val win = GbWin.gen1()

    // ------------------------------------------------------------------ the Nuzlocke reads (2026-09-30)

    private val nuzTracker by lazy { GbNuzTracker(1, m.gameKey, listOf(m.gameKey), rom, m.trainerTable) }

    private fun byteAt(off: Long): Int = if (off == 0L) -1 else ram(off, 1).let { if (it.isEmpty()) -1 else it.u8(0) }

    /**
     * What the rules engine reads beyond the panel's state, Gen12Nuzlocke turns it into a snapshot: the place, how the last
     * battle ended, the player's trainer id and the enemy's DVs (a Pokemon's id, Gen12Nuzlocke.id), the balls and the bag,
     * the opponent's class and number, the level caps read out of the ROM, and the party's nicknames.
     */
    private fun nuzReads(party: List<TrackedMon>, mode: Int, battling: Boolean, enemy: EnemyInfo?, bag: List<Pair<Int, Int>>, mapId: Int?): NuzlockeReads? {
        if (map == null || m.playerId == 0L) return null
        val n = nuzTracker
        val wild = mode == 1
        n.look(battling, wild, enemy?.curHp, escapedNow = byteAt(m.escaped) > 0, capturedNow = byteAt(m.captured) > 0,
            enemyLeft = { Gen12Nuzlocke.enemyLeft(byteAt(m.enemyMove), byteAt(m.playerMove)) })
        val place = n.place(mapId)
        val id = ram(m.playerId, 2).let { if (it.size == 2) be16(it, 0) else -1 }
        val dvs = if (battling && wild) ram(m.enemyDvs, 2).let { if (it.size == 2) be16(it, 0) else -1 } else -1
        val options = byteAt(m.options)
        // Item ids 1 to 4 are the four balls; the Silph Scope (0x48) is what lets the Pokemon Tower's ghosts be seen.
        val balls = bag.filter { it.first in 1..4 }.sumOf { it.second }
        val ghost = battling && wild && mapId in POKEMON_TOWER && bag.none { it.first == SILPH_SCOPE && it.second > 0 }
        val opponent = if (battling && !wild) n.opponent(byteAt(m.trainerClass), byteAt(m.trainerNo)) else null
        val nicks = ram(m.nicks, 66)
        val gb = GbNuzReads(
            generation = 1, game = m.gameKey, gameKeys = listOf(m.gameKey),
            place = place?.place, detail = place?.detail,
            playerId = id, enemyDvs = dvs, enemyHpLast = n.lastEnemyHp, lastWild = n.lastWild,
            battleResult = byteAt(m.battleResult), escaped = n.escaped, enemyFled = n.enemyFled, captured = n.captured,
            battleType = byteAt(m.battleType).coerceAtLeast(0), ghost = ghost,
            surfing = byteAt(m.surfState) == 2,
            ballCount = balls,
            bag = bag.filter { it.first !in 1..4 && it.second > 0 }.associate { (item, qty) -> item to BagItem(itemNames[item] ?: "Item $item", qty) },
            turn = if (battling) byteAt(m.aiTurns).coerceAtLeast(0) else -1,
            // wOptions bit 6 set is the Set battle style (pokered BIT_BATTLE_SHIFT, main_menu.asm).
            battleStyleSet = if (options < 0) null else (options and 0x40) != 0,
            opponent = opponent, caps = n.caps(),
            nicknames = party.indices.map { i -> if (nicks.size >= (i + 1) * 11) GbText.decode(nicks, i * 11) else "" },
            // wPartyCount, when it is a count: Gen 1 has no eggs (rc32 audit P3 #111).
            partyCount = lastCount.takeIf { it in 1..6 } ?: -1,
        )
        return NuzlockeReads(gb = gb)
    }

    fun read(): TrackerState {
        if (map == null) return TrackerState(
            partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false,
            badgeSet = "RBY", diagnostics = "Game Boy game not known to the tracker", unreadable = true,
        )
        // Program.getBagItems: a stone counts while its quantity is above 0.
        val bag = readBag()
        bagStones = bag.filter { it.second > 0 }.mapNotNull { STONES[it.first] }.toSet()
        var party = readParty()
        // wIsInBattle: 0 none, 1 wild, 2 trainer; 0xFF marks a lost battle in the disassembly's comment.
        val mode = ram(m.inBattle, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val inBattle = mode == 1 || mode == 2
        var enemy = if (inBattle) readEnemy() else run { enemyMoves.clear(); null }
        val battling = inBattle && enemy != null
        // The player's Pokemon on the field (wPlayerMonNumber): slot 1 is not it after a switch (rc33 audit P1).
        // The party list stops at the first bad slot, so an index is a slot.
        val onField = if (battling) ram(PLAYER_MON_NUMBER, 1).let { if (it.isEmpty()) 0 else it.u8(0) }.takeIf { it in party.indices } ?: 0 else 0
        // Battle.updateStatStages (Battle.lua:735-761): the active battlers' stages, drawn only in battle,
        // yours on the Pokemon on the field as the reference views it (Battle.getViewedPokemon).
        if (battling && party.isNotEmpty()) {
            party = party.mapIndexed { i, p -> if (i == onField) withBattleTypes(p.copy(statStages = gbStatStages(ram(m.statMods, 6), GEN1_STAGES))) else p }
            enemy = enemy?.copy(statStages = gbStatStages(ram(m.statMods + 0x14, 6), GEN1_STAGES))
        }
        lastMove.read(battling, turn = ram(m.aiTurns, 1).let { if (it.isEmpty()) 0 else it.u8(0) },
            move = ram(m.enemyMove, 1).let { if (it.isEmpty()) 0 else it.u8(0) })
        val badges = ram(m.badges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val lead = party.getOrNull(onField)
        val heals = readHeals(bag, lead?.mon?.maxHp ?: 0)
        val mapId = ram(m.curMap, 1).takeIf { m.curMap != 0L && it.isNotEmpty() }?.u8(0)
        // Read first, as the GBA tracker reads its win before its loss (rc32 audit P2 #135). wEnemyMon's HP is at + 1.
        val won = win.read(mode, byteAt(m.trainerClass), byteAt(m.battleResult),
            enemyHp = ram(m.enemyMon + 1, 2).let { if (it.size == 2) be16(it, 0) else -1 }, mapId = mapId)
        return TrackerState(
            partyCount = party.size, party = party, ownOnField = onField,
            inBattle = battling, isWildBattle = inBattle && mode == 1, enemy = enemy,
            // "Team:" in a trainer battle: the one ball the reference knows (gbEnemyTeam).
            enemyTeam = gbEnemyTeam(trainerBattle = battling && mode == 2, enemy = enemy),
            badges = badges, badgeSet = "RBY",
            healPercent = heals.percent, healCount = heals.count, healHp = heals.hp,
            // "Last move: X" between the enemy's moves (GbLastMove); MoveData.isValid is 1..165.
            lastAttackMove = lastMove.shown.takeIf { it in 1..165 }?.let { moveNames[it] ?: "#$it" },
            // Program.updateMapLocation (Program.lua:1114-1129): the map is wCurMap, and any map read is
            // a valid location (isValidMapLocation is mapId ~= nil), so the Time Machine makes its points.
            // No name: the reference names Gen 1 maps from its RSE table (RouteData.lua:67-74, games 1
            // and 2), so Viridian City, map 1, would read "Petalburg City"; the point says Unknown Area.
            mapId = mapId,
            // The player's condition, checked once the battle byte reads 0, never mid-battle (GbGameOver).
            gameOver = if (won) GameOver.WON else if (GbGameOver.lost(mode, party, lossCondition)) GameOver.LOST else null,
            diagnostics = "${m.name}  party=%d mode=%d".format(party.size, mode),
            unreadable = party.isEmpty() && lastCount != 0,
            nuz = runCatching { nuzReads(party, mode, battling, enemy, bag, mapId) }.getOrNull(),
        )
    }
}

/**
 * One Gen 1 game's addresses: WRAM offsets from 0x02000000, and the ROM
 * tables. The core's SYSTEM_RAM is the Game Boy's work RAM (0xC000 up), so a
 * pokered address 0xDxxx is offset 0x1xxx, exactly as the reference writes them
 * (GameSettings.setWramAddresses: pstats 0x0200116B) and as GbcTracker does.
 * These were the raw addresses (0xD16B) until 2026-09-28: every read fell
 * outside the 8 KB work RAM and came back empty, so on a real device the Gen 1
 * panel never saw a party (parity audit; the unit test's 64 KB fake memory
 * could not notice). RED_BLUE is pokered's layout, YELLOW pokeyellow's; both computed
 * by tools/wram_layout.py and agreeing with the Gen 1 reference tracker's
 * table (its Red numbers, and its "Yellow is one less" rule for the D block).
 * ROM offsets are gen1_offsets.ini's [Red (U)] and [Yellow (U)] entries
 * ([Blue (U)] copies Red's), [movesets] its PokemonMovesetsTableOffset.
 */
data class Gen1Map(
    val name: String,
    val partyCount: Long, val partySpecies: Long, val partyMons: Long,
    val enemyMon: Long, val inBattle: Long, val enemyMove: Long,
    val badges: Long, val numItems: Long, val items: Long,
    val baseStats: Int, val mewStats: Int, val moves: Int, val dexOrder: Int, val internalCount: Int,
    val movesets: Int = 0,
    /**
     * wAILayer2Encouragement 0xCCD5 in both games, the reference's gTurn
     * (0x02000cd5, which its Yellow table deliberately does not shift).
     */
    val aiTurns: Long = 0L,
    /**
     * wPlayerMonStatMods 0xCD1A in both games, the enemy's 0x14 on
     * (wEnemyMonStatMods 0xCD2E): the reference's StatChange (0x02000D1A, not
     * shifted for Yellow either; tools/wram_layout.py gives 0xCD1A for both).
     */
    val statMods: Long = 0L,
    /**
     * wCurMap, 0xD35E in Red and Blue, 0xD35D in Yellow: the reference's
     * gMapHeader (0x0200135E, shifted for Yellow), Program.GameData.mapId.
     */
    val curMap: Long = 0L,
    // ---- The Nuzlocke reads (2026-09-30). pokered and pokeyellow WRAM, from tools/wram_layout.py, as offsets like the rest. ----
    /** wPlayerID (big endian), wBattleResult, wEscapedFromBattle, wCapturedMonSpecies, wOptions, wTrainerClass, wTrainerNo, wBattleType, wWalkBikeSurfState. */
    val playerId: Long = 0L, val battleResult: Long = 0L, val escaped: Long = 0L, val captured: Long = 0L, val options: Long = 0L,
    val trainerClass: Long = 0L, val trainerNo: Long = 0L, val battleType: Long = 0L, val surfState: Long = 0L,
    /** The enemy's DVs (wEnemyMon + 12) and the party's nicknames (wPartyMonNicks, six of 11 bytes). */
    val enemyDvs: Long = 0L, val nicks: Long = 0L,
    /** wPlayerMoveNum, six bytes after wEnemyMoveNum ([enemyMove]): whose Teleport, Roar or Whirlwind set wEscapedFromBattle. */
    val playerMove: Long = 0L,
    /** The randomizer's TrainerDataTableOffset ([Red (U)] and [Yellow (U)] in gen1_offsets.ini), and the game's data key. */
    val trainerTable: Int = 0, val gameKey: String = "rb",
    /** wBattleMon, the player's Pokemon in battle (tools/wram_layout.py): pokered 0xD014, pokeyellow 0xD013. */
    val battleMon: Long = 0L,
) {
    companion object {
        val RED_BLUE = Gen1Map(
            name = "Red/Blue",
            // pokered 0xD163, 0xD164, 0xD16B, 0xCFE5, 0xD057, 0xCFCC, 0xD356, 0xD31D, 0xD31E.
            partyCount = 0x1163L, partySpecies = 0x1164L, partyMons = 0x116BL,
            enemyMon = 0x0FE5L, inBattle = 0x1057L, enemyMove = 0x0FCCL,
            badges = 0x1356L, numItems = 0x131DL, items = 0x131EL,
            baseStats = 0x383DE, mewStats = 0x425B, moves = 0x38000, dexOrder = 0x41024, internalCount = 190,
            movesets = 0x3B05C, aiTurns = 0x0CD5L, statMods = 0x0D1AL, curMap = 0x135EL,
            // wPlayerID D359, wBattleResult CF0B, wEscapedFromBattle D078, wCapturedMonSpecies D11C, wOptions D355,
            // wTrainerClass D031, wTrainerNo D05D, wBattleType D05A, wWalkBikeSurfState D700, wEnemyMonDVs CFF1, wPartyMonNicks D2B5.
            playerId = 0x1359L, battleResult = 0x0F0BL, escaped = 0x1078L, captured = 0x111CL, options = 0x1355L,
            trainerClass = 0x1031L, trainerNo = 0x105DL, battleType = 0x105AL, surfState = 0x1700L,
            enemyDvs = 0x0FF1L, nicks = 0x12B5L, trainerTable = 0x39D3B, gameKey = "rb",
            playerMove = 0x0FD2L,   // wPlayerMoveNum CFD2
            battleMon = 0x1014L,
        )
        val YELLOW = Gen1Map(
            name = "Yellow",
            // pokeyellow: each one less than Red's.
            partyCount = 0x1162L, partySpecies = 0x1163L, partyMons = 0x116AL,
            enemyMon = 0x0FE4L, inBattle = 0x1056L, enemyMove = 0x0FCBL,
            badges = 0x1355L, numItems = 0x131CL, items = 0x131DL,
            baseStats = 0x383DE, mewStats = 0, moves = 0x38000, dexOrder = 0x410B1, internalCount = 190,
            movesets = 0x3B1E5, aiTurns = 0x0CD5L, statMods = 0x0D1AL, curMap = 0x135DL,
            // pokeyellow: wPlayerID D358, wBattleResult CF0B, wEscapedFromBattle D077, wCapturedMonSpecies D11B, wOptions D354,
            // wTrainerClass D030, wTrainerNo D05C, wBattleType D059, wWalkBikeSurfState D6FF, wEnemyMonDVs CFF0, wPartyMonNicks D2B4.
            playerId = 0x1358L, battleResult = 0x0F0BL, escaped = 0x1077L, captured = 0x111BL, options = 0x1354L,
            trainerClass = 0x1030L, trainerNo = 0x105CL, battleType = 0x1059L, surfState = 0x16FFL,
            enemyDvs = 0x0FF0L, nicks = 0x12B4L, trainerTable = 0x39DD1, gameKey = "y",
            playerMove = 0x0FD1L,   // wPlayerMoveNum CFD1
            battleMon = 0x1013L,
        )

        /** From the cartridge header title: "POKEMON RED", "POKEMON BLUE" (pokered's rgbfix titles) or "POKEMON YELLOW". */
        fun forRom(rom: ByteArray): Gen1Map? {
            if (rom.size < 0x150) return null
            val title = String(rom, 0x134, 16, Charsets.US_ASCII).substringBefore('\u0000').trim()
            return when (title) {
                "POKEMON RED", "POKEMON BLUE" -> RED_BLUE
                "POKEMON YELLOW" -> YELLOW
                else -> null
            }
        }
    }
}
