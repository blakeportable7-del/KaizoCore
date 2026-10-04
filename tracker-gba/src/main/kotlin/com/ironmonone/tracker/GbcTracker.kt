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
 * dropped). Only names are bundled, and for the panel's lookups ([GbLookups])
 * the reference's move summaries, weights and evolutions; the learn levels
 * are the ROM's own, as the reference reads them.
 */
class GbcTracker(
    private val memory: MemoryReader,
    /** The randomized ROM as loaded, for the tables the game itself reads. */
    private val rom: ByteArray,
    /** Which Gen 2 game's addresses to use; null when the header names none of them. */
    private val map: Gen2Map? = Gen2Map.forRom(rom),
) : GbLookups, ActionMenuGate {
    companion object {
        const val RAM = 0x02000000L

        // ---- WRAM offsets (pokecrystal wram.asm, bank 1 -> +0x1000) ----------
        /** wPartyCount 0xDCD7. The reference has 0x0CD7 here, which is bank 0
         *  and cannot be right for a Crystal party; the count is validated
         *  against the species list and the structs rather than trusted. */
        const val PARTY_COUNT = 0x1CD7L
        const val PARTY_SPECIES = 0x1CD8L          // wPartySpecies, 6 + terminator
        const val PARTY_MONS = 0x1CDFL             // wPartyMon1 (reference pstats)
        /** An egg in the party species list (wPartySpecies). */
        const val EGG = 0xFD
        const val PARTY_STRIDE = 48                // party_struct; the reference steps 44, Gen 1's size
        const val ENEMY_MON = 0x1206L              // wEnemyMon (reference estats)
        /**
         * BattleMenuHeader (pokecrystal and pokegold engine/battle/menu.asm): a 2D menu boxed at (8, 12), two rows
         * and two columns six apart, STATICMENU_CURSOR | STATICMENU_DISABLE_B. Init2DMenuCursorPosition makes that
         * InitY 0x0E, InitX 0x09, CursorOffsets 0x26 and a joypad filter of A alone: no other battle menu leaves B
         * out. And on screen: Place2DMenuCursor keeps the address of the tile it drew the cursor on, which ExitMenu
         * puts back, so a menu already gone has no cursor there.
         */
        internal fun battleMenuUp(menu: ByteArray, tileAt: (Long) -> Int): Boolean {
            if (menu.size < 13) return false
            fun u(i: Int) = menu[i].toInt() and 0xFF
            if (u(0) != 0x0E || u(1) != 0x09 || u(2) != 2 || u(3) != 2 || u(6) != 0x26 || u(7) != 0x01) return false
            val tile = u(11) or (u(12) shl 8)
            if (tile !in 0xC000..0xCFFF) return false
            return tileAt(tile.toLong()) == 0xED   // "▶"
        }

        const val BATTLE_MODE = 0x122DL            // wBattleMode: 0 none, 1 wild, 2 trainer (reference gBattleTypeFlags)
        const val ENEMY_LAST_MOVE = 0x0608L        // wEnemyMoveStruct 0xC608 (reference eMove): its first byte is the move id. wCurEnemyMove is 0xC6E4
        const val ENEMY_TURNS = 0x06DCL            // wEnemyTurnsTaken 0xC6DC (reference oppTurn): counts the turns the opponent's move ran
        const val PLAYER_TURNS = 0x06DDL           // wPlayerTurnsTaken 0xC6DD (reference gTurn)
        const val CUR_LANDMARK = 0x02D9L           // wCurLandmark 0xC2D9 (reference gMapHeader)
        const val STAT_LEVELS = 0x06CCL            // wPlayerStatLevels 0xC6CC, the enemy's 8 on (reference StatChange)
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
        /** EvosAttacksPointers: the reference's gEvo_move / offset (0x080425B1), a bank-local pointer per species. */
        const val EVOS_ATTACKS = 0x425B1

        /**
         * Gen 2 type ids to the Gen 3 ids the panel's chips and chart use.
         * Gen 2: 0 Normal 1 Fighting 2 Flying 3 Poison 4 Ground 5 Rock 6 Bird
         * 7 Bug 8 Ghost 9 Steel, then 20 Fire 21 Water 22 Grass 23 Electric
         * 24 Psychic 25 Ice 26 Dragon 27 Dark. Bird is the unused slot.
         * 19 is CURSE_TYPE, Curse's own (pokecrystal constants/type_constants.asm:22-23, data/moves/moves.asm:190):
         * Gen 3's ??? (9), as the Gen 2 reference shows it (MoveData.lua:2038, Types.UNKNOWN). It read Normal
         * (rc32 audit P3 #110).
         */
        fun gen3Type(gen2: Int): Int = when (gen2) {
            in 0..5 -> gen2
            7 -> 6; 8 -> 7; 9 -> 8
            19 -> 9
            20 -> 10; 21 -> 11; 22 -> 12; 23 -> 13; 24 -> 14; 25 -> 15; 26 -> 16; 27 -> 17
            else -> 0
        }

        /**
         * wJohtoBadges in the order the badge art is numbered. The game keeps
         * Jasmine's Mineral Badge in bit 4 and Chuck's Storm Badge in bit 5
         * (pokecrystal constants/engine_flags.asm, MINERALBADGE before
         * STORMBADGE) while GSC_badge5 is the Storm Badge and GSC_badge6 the
         * Mineral: the Gen 2 reference draws badge 5 from bit 5 and badge 6
         * from bit 4 (Program.lua:1103-1108). Swapped here, so badge N is
         * bit N-1 as in every other set, and Jasmine no longer lights Chuck's.
         */
        fun johtoInArtOrder(bits: Int): Int =
            (bits and 0x30.inv()) or ((bits shr 1) and 0x10) or ((bits shl 1) and 0x20)

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

        /**
         * The evolution stones, pokecrystal's and pokegold's item_constants.asm:
         * MOON_STONE $08, FIRE_STONE $16, THUNDERSTONE $17, WATER_STONE $18,
         * LEAF_STONE $22. The Gen 2 reference keys its MiscData.EvolutionStones by
         * Gen 1's ids (10, 32, 33, 34, 47: MiscData.lua:486-517), which in Gold,
         * Silver and Crystal are Burn Heal, Rare Candy, X Accuracy, Leaf Stone and
         * Soda Pop; the game's own ids are read here.
         */
        val STONES: Map<Int, EvoText.GbStone> = mapOf(
            0x08 to EvoText.GbStone.MOON, 0x16 to EvoText.GbStone.FIRE, 0x17 to EvoText.GbStone.THUNDER,
            0x18 to EvoText.GbStone.WATER, 0x22 to EvoText.GbStone.LEAF,
        )
    }

    private val speciesNames = HashMap<Int, String>()
    private val moveNames = HashMap<Int, String>()
    private val seenForSpecies = -1
    private val enemyMoves = GbEnemyMoves()
    private val lastMove = GbLastMove()
    /** The stones in the bag at the last read, for the evolution text (Program.GameData.evolutionStones). */
    @Volatile private var bagStones: Set<EvoText.GbStone> = emptySet()

    /** The Gen 2 reference's MiscData.Items by item id (tools/extract_gen2_data.py). */
    private val itemNames = HashMap<Int, String>()
    /** The Gen 2 reference's RouteData.setupRouteInfoAsGSC by Crystal landmark id (tools/extract_gen2_data.py). */
    private val landmarkNames = HashMap<Int, String>()

    init {
        load("/gen2/species.tsv", speciesNames)
        load("/gen2/moves.tsv", moveNames)
        load("/gen2/items.tsv", itemNames)
        load("/gen2/landmarks.tsv", landmarkNames)
    }

    private fun load(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l ->
                val t = l.indexOf('\t')
                if (t > 0) l.substring(0, t).toIntOrNull()?.let { into[it] = l.substring(t + 1).trim() }
            }
        }
    }

    override fun speciesName(id: Int): String = speciesNames[id] ?: "#$id"
    fun moveName(id: Int): String = moveNames[id] ?: "#$id"

    override val generation: Int get() = 2
    private val tables by lazy { GbTables(2) }
    private val learnCache = java.util.concurrent.ConcurrentHashMap<Int, List<Int>>()

    // ------------------------------------------------------------------ ROM tables

    private fun romU8(off: Int): Int = if (off in rom.indices) rom[off].toInt() and 0xFF else 0

    override fun baseStats(species: Int): BaseStats? {
        if (species !in 1..251) return null
        val b = m.baseStats + (species - 1) * BASE_STRIDE
        if (b + BASE_STRIDE > rom.size) return null
        return BaseStats(
            hp = romU8(b + 1), atk = romU8(b + 2), def = romU8(b + 3),
            spe = romU8(b + 4), spAtk = romU8(b + 5), spDef = romU8(b + 6),
            type1 = gen3Type(romU8(b + 7)), type2 = gen3Type(romU8(b + 8)),
            ability1 = 0, ability2 = 0,
            // BaseData +13, BASE_GENDER: the ratio byte the Nuzlocke rules read a Pokemon's gender against (Gen12Nuzlocke.gender).
            genderRatio = romU8(b + 13),
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

    /** B-to-Run's gate (ActionMenuGate): a wild battle (wBattleMode 1) with its action menu up, read live. */
    override fun isChoosingActionInWild(): Boolean {
        val m = map ?: return false
        if (m.menu2D == 0L) return false
        val mode = ram(m.battleMode, 1)
        if (mode.isEmpty() || mode[0].toInt() != 1) return false
        return battleMenuUp(ram(m.menu2D, 13)) { addr ->
            ram(addr - 0xC000L, 1).firstOrNull()?.toInt()?.and(0xFF) ?: -1
        }
    }
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
            // The game's CheckShininess (pokecrystal engine/gfx/color.asm:3-36): bit 1 of the Attack DV, not Attack 2 alone.
            // The Lake of Rage Gyarados is Attack 14 (ATKDEFDV_SHINY $EA) and read not shiny (rc32 audit P2 #134).
            abilitySlot = 0, nature = 0, shiny = Gen12Nuzlocke.shiny(2, dv),
            status = b.u8(32).toLong(), curHp = curHp, maxHp = maxHp,
            atk = be16(b, 38), def = be16(b, 40), spe = be16(b, 42), spAtk = be16(b, 44), spDef = be16(b, 46),
        )
    }

    /** The panel's card for any mon, through this ROM's tables. Public for the screenshot demo. */
    fun trackedOf(m: PokemonDecoder.Mon): TrackedMon = tracked(m)
    fun moveRowOf(id: Int, pp: Int, ppMax: Int?): MoveRow = moveRow(id, pp, ppMax)
    /** The row for a move seen in an earlier battle, at its base PP. */
    override fun moveRowFor(id: Int): MoveRow? = if (id <= 0) null else moveData(id)?.let { moveRow(id, it[3], null) }

    // ------------------------------------------------------------------ the panel's lookups (GbLookups)
    /** MoveData.lua `summary`, ids 1..251. */
    override fun moveDescription(id: Int): String? = if (id in 1..251) tables.moveDescription(id) else null
    /** PokemonData.lua `weight` (kg). */
    override fun weight(species: Int): String? = if (species in 1..251) tables.weight(species) else null
    /** PokemonData.lua `evolution`. */
    override fun evolution(species: Int): String? = if (species in 1..251) tables.evolution(species) else null
    /**
     * Tracker Extras' "Estimate Pokemon IV Potential" for your [lead]
     * (Battle.getViewedPokemon(true), slot 1): "Cyndaquil is: Decent.", with the
     * BST the reference reads from the ROM (PokemonData.UpdateBST, gBaseStats)
     * and the reference's own name for the species (IvEstimate).
     */
    fun ivPotential(lead: TrackedMon?): String {
        val mon = lead?.mon ?: return IvEstimate.UNAVAILABLE
        if (mon.species !in 1..251) return IvEstimate.UNAVAILABLE
        val bst = baseStats(mon.species)?.bst ?: return IvEstimate.UNAVAILABLE
        val f = IvEstimate.fraction(mon.maxHp, mon.atk, mon.def, mon.spAtk, mon.spDef, mon.spe, mon.level, bst)
        return "${tables.name(mon.species) ?: speciesName(mon.species)} is: ${IvEstimate.verdict(f)}"
    }

    /** PokemonData.getEffectiveness: the ROM's types against the Gen 2 reference's chart, which is Gen 3's. */
    override fun effectivenessAgainst(species: Int): Map<Double, List<String>> =
        baseStats(species)?.let { weaknessesOf(it.type1, it.type2, gen1 = false) } ?: emptyMap()

    /**
     * PokemonData.updatemoves (Gen 2 reference PokemonData.lua:269-298): the
     * species' pointer in EvosAttacksPointers, its evolutions skipped
     * (EVOLVE_STAT is 4 bytes, the rest 3), then its (level, move) pairs, the
     * Lv.1 ones left out. The reference turns the pointer into a ROM offset
     * against the first entry's (pointer - first + 0x427A7); the bank
     * arithmetic here gives the same offsets on Crystal and does not depend
     * on Bulbasaur's entry staying where it was.
     */
    override fun learnLevels(species: Int): List<Int> = learnCache.getOrPut(species) {
        val table = m.evosAttacks
        if (map == null || species !in 1..251 || table == 0) return@getOrPut emptyList()
        val at = table + (species - 1) * 2
        if (at + 1 !in rom.indices) return@getOrPut emptyList()
        val pointer = romU8(at) or (romU8(at + 1) shl 8)
        gbLearnLevels(rom, gbOffset(table / 0x4000, pointer)) { type -> when (type) { 1, 2, 3, 4 -> 3; 5 -> 4; else -> null } }
    }

    private fun tracked(m: PokemonDecoder.Mon): TrackedMon {
        val base = baseStats(m.species)
        val rows = m.moves.indices.filter { m.moves[it] != 0 }.map { i ->
            val d = moveData(m.moves[i])
            val basePp = d?.get(3)
            moveRow(m.moves[i], m.pp[i], basePp?.let { it + (it / 5) * m.ppUps[i] })
        }
        // Utils.getMovesLearnedHeader: "Moves 3/7 (16)" from the learn levels.
        val header = LearnedMoves.of(learnLevels(m.species), m.level)
        return TrackedMon(
            mon = m, speciesName = speciesName(m.species),
            moveNames = rows.map { it.name }, base = base,
            // The held item by name, MiscData.Items[id + 1] (DataHelper.lua:173-174); it read "#18".
            abilityName = "-", itemName = if (m.heldItem == 0) "-" else itemNames[m.heldItem] ?: "#${m.heldItem}",
            moveRows = rows, statusCondition = statusName(m.status.toInt()),
            movesLearned = header.learned, movesTotal = header.total, nextMoveLevel = header.next,
            // "Lv.12 (14)": the Gen 2 reference's evolution, coloured as it draws it (EvoText.forOwnGb),
            // against the Pokemon's own friendship (party_struct +27).
            evo = EvoText.forOwnGb(evolution(m.species), m.level, bagStones, m.friendship, TrackerPrefs.determineFriendship),
        )
    }

    /** The raw count byte of the last read: 0 before a game has a party, 1..6 with one, anything else garbage. */
    private var lastCount = 0

    /** The party slot each Pokemon of the last [readParty] came from: an egg holds a slot and is not read, so the two can differ. */
    private var partySlots: List<Int> = emptyList()

    /** The game's count of Pokemon at the last [readParty], eggs left out, or -1 (GbNuzReads.partyCount). */
    private var gameCount = -1

    /** The eggs of the last [readParty], for the Nuzlocke reads (GbNuzReads.eggs), and the species list's EGG slots. */
    private var eggs: List<GbEgg> = emptyList()
    private var eggSlots = 0

    private fun readParty(): List<TrackedMon> {
        val raw = ram(m.partyCount, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        lastCount = raw
        val count = raw.coerceIn(0, 6)
        val species = ram(m.partySpecies, 7)
        eggSlots = if (raw in 1..6) (0 until raw).count { species.size > it && species.u8(it) == EGG } else 0
        gameCount = if (raw in 1..6) raw - eggSlots else -1
        val out = ArrayList<TrackedMon>(6)
        val slots = ArrayList<Int>(6)
        val eggsSeen = ArrayList<GbEgg>(2)
        for (i in 0 until 6) {
            val sp = if (species.size > i) species.u8(i) else 0
            // 0xFF terminates the list; 0 is empty. Read structs past the count
            // byte only while the list agrees, so a wrong count cannot invent.
            if (sp == 0xFF || sp == 0) break
            if (i >= count && count > 0) break
            // An egg is 0xFD in the species list. It stopped the read, so every Pokemon after
            // it was missing, and an "entire party" run ended while they stood (review,
            // 2026-09-29). An egg does not battle and is not tracked: the read goes on past it.
            // The Nuzlocke engine still wants to know it is there, and from where (rc32 audit P2 #140).
            if (sp == EGG) { eggOf(i)?.let { eggsSeen += it }; continue }
            if (sp !in 1..251) break
            val mon = partyMon(ram(m.partyMons + i * PARTY_STRIDE.toLong(), PARTY_STRIDE), sp) ?: break
            out += tracked(mon)
            slots += i
        }
        partySlots = slots
        eggs = eggsSeen
        return out
    }

    /**
     * The egg in party slot [slot], from its party struct (pokecrystal macros/ram.asm party_struct): the species it will
     * hatch into at 0, its trainer id at 6 and 7, its DVs at 21 and 22, its level at 31. Null when the struct does not read
     * as one: no species in range, or no level.
     */
    private fun eggOf(slot: Int): GbEgg? {
        val b = ram(m.partyMons + slot * PARTY_STRIDE.toLong(), PARTY_STRIDE)
        if (b.size < PARTY_STRIDE) return null
        val species = b.u8(0); val level = b.u8(31)
        if (species !in 1..251 || level !in 1..100) return null
        return GbEgg(slot, (b.u8(6) shl 8) or b.u8(7), (b.u8(21) shl 8) or b.u8(22), species, level)
    }

    /** battle_struct (wEnemyMon): species 0, item 1, moves 2, dvs 6, pp 8, happiness 12, level 13, status 14, hp 16, maxhp 18, stats 20.., types 30-31. */
    private fun readEnemy(): EnemyInfo? {
        val b = ram(m.enemyMon, 32)
        if (b.size < 32) return null
        val species = b.u8(0)
        if (species !in 1..251) return null
        val level = b.u8(13); val curHp = be16(b, 16); val maxHp = be16(b, 18)
        if (level !in 1..100 || maxHp !in 1..999 || curHp > maxHp) return null
        // Battle.updateTrackedInfoGen2 (Gen 2 reference Battle.lua:429-455, GbEnemyMoves): the move
        // byte counts only when it is one of the opponent's four, once per enemy turn. Any byte in range
        // used to count, so the move the AI last weighed, or last battle's, went on the card unused.
        val turns = ram(m.enemyTurns, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val known = (0 until 4).map { b.u8(2 + it) }
        enemyMoves.see(species, ram(m.enemyMove, 1).let { if (it.isEmpty()) 0 else it.u8(0) }, known, 1..251, turns)
        val movesSeen = enemyMoves.seen
        val base = baseStats(species)
        return EnemyInfo(
            species = species, speciesName = speciesName(species), level = level,
            curHp = curHp, maxHp = maxHp,
            type1 = gen3Type(b.u8(30)), type2 = gen3Type(b.u8(31)),
            base = base,
            movesSeen = movesSeen.map { moveName(it) },
            // "Count enemy PP usage" (DataHelper.lua:275-286): a shown move the opponent has now takes
            // its live PP from the battle struct (moves at 2, PP at 8, the whole byte as
            // Program.readNewEnemyPokemonGen2 reads it); otherwise, and with the option off, base PP.
            moveRows = movesSeen.map { id ->
                val live = (0 until 4).firstOrNull { TrackerPrefs.countEnemyPp && b.u8(2 + it) == id }?.let { b.u8(8 + it) }
                moveRow(id, live ?: moveData(id)?.get(3) ?: 0, null)
            },
            abilityGuess = "-",
            statusCondition = statusName(b.u8(14)),
            // The opponent's evolution, all in the default colour (TrackerScreen.lua:734).
            evo = EvoText.forEnemy(evolution(species)),
            // Its DVs (battle_struct +6), for whether it is shiny and Unown's letter on the Walking Pals icon.
            dvs = (b.u8(6) shl 8) or b.u8(7),
        )
    }

    /** The items pocket (wNumItems, then wItems' id and quantity pairs up to the 0xFF that ends them). */
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

    /** Red beaten on Mt. Silver, the GSC rulebook's win (GbWin). */
    private val win = GbWin.gen2()

    // ------------------------------------------------------------------ the Nuzlocke reads (2026-09-30)

    /** Crystal is `c`; Gold and Silver are told apart by the header title, their data rows are `g` or `s` first and `gs` for both. */
    private val nuzTracker by lazy {
        val title = if (rom.size >= 0x140) String(rom, 0x134, 11, Charsets.US_ASCII) else ""
        val keys = when {
            m === Gen2Map.CRYSTAL -> listOf("c")
            title == "POKEMON_SLV" -> listOf("s", "gs")
            else -> listOf("g", "gs")
        }
        GbNuzTracker(2, keys.first(), keys, rom, m.trainerTable)
    }

    private fun byteAt(off: Long): Int = if (off == 0L) -1 else ram(off, 1).let { if (it.isEmpty()) -1 else it.u8(0) }

    /** The ball pocket's quantities added up; -1 when it cannot be read. */
    private fun ballCount(): Int {
        if (m.numBalls == 0L) return -1
        val n = byteAt(m.numBalls).coerceIn(0, 12)
        val b = ram(m.balls, n * 2 + 1)
        if (b.size < n * 2) return -1
        return (0 until n).sumOf { b.u8(it * 2 + 1) }
    }

    /**
     * What the rules engine reads beyond the panel's state; Gen12Nuzlocke turns it into a snapshot: the place, how the last
     * battle ended and what kind it was, the player's trainer id and the enemy's DVs (a Pokemon's id, Gen12Nuzlocke.id), the
     * balls and the items, the opponent's class and number, the level caps read out of the ROM, and the party's nicknames.
     */
    private fun nuzReads(party: List<TrackedMon>, mode: Int, battling: Boolean, enemy: EnemyInfo?, bag: List<Pair<Int, Int>>, mapId: Int?): NuzlockeReads? {
        if (map == null || m.playerId == 0L) return null
        val n = nuzTracker
        val wild = mode == 1
        // Gen 2 has no flag for "someone ran": every escape ends as a draw. A catch does: wWildMon holds the species from the
        // throw that catches until the battle is over, so it is latched while the battle is on.
        n.look(battling, wild, enemy?.curHp, escapedNow = false, capturedNow = byteAt(m.wildMon) > 0)
        val place = n.place(mapId)
        val id = ram(m.playerId, 2).let { if (it.size == 2) be16(it, 0) else -1 }
        val dvs = if (battling && wild) ram(m.enemyDvs, 2).let { if (it.size == 2) be16(it, 0) else -1 } else -1
        val options = byteAt(m.options)
        val opponent = if (battling && !wild) n.opponent(byteAt(m.trainerClass), byteAt(m.trainerNo)) else null
        val nicks = ram(m.nicks, 66)
        val state = byteAt(m.playerState)
        val gb = GbNuzReads(
            generation = 2, game = n.game, gameKeys = n.keys,
            place = place?.place, detail = place?.detail,
            playerId = id, enemyDvs = dvs, enemyHpLast = n.lastEnemyHp, lastWild = n.lastWild,
            battleResult = byteAt(m.battleResult), captured = n.captured,
            battleType = byteAt(m.battleType).coerceAtLeast(0),
            // PLAYER_SURF is 4 and PLAYER_SURF_PIKA 8 (pokecrystal constants/ram_constants.asm).
            surfing = state == 4 || state == 8,
            ballCount = ballCount(),
            bag = bag.filter { it.second > 0 }.associate { (item, qty) -> item to BagItem(itemNames[item] ?: "Item $item", qty) },
            turn = if (battling) ram(m.playerTurns, 1).let { if (it.isEmpty()) 0 else it.u8(0) } else -1,
            // wOptions bit 6 set is the Set battle style (pokecrystal BATTLE_SHIFT, options_menu.asm).
            battleStyleSet = if (options < 0) null else (options and 0x40) != 0,
            opponent = opponent, caps = n.caps(),
            nicknames = partySlots.map { slot -> if (nicks.size >= (slot + 1) * 11) GbText.decode(nicks, slot * 11) else "" },
            partyCount = gameCount,
            eggs = eggs, eggSlots = eggSlots,
        )
        return NuzlockeReads(gb = gb)
    }

    fun read(): TrackerState {
        if (map == null) return TrackerState(
            partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false,
            badgeSet = "GSC", diagnostics = "Game Boy game not known to the tracker", unreadable = true,
        )
        // Program.getBagItems: a stone counts while its quantity is above 0.
        val bag = readBag()
        bagStones = bag.filter { it.second > 0 }.mapNotNull { STONES[it.first] }.toSet()
        var party = readParty()
        val mode = ram(m.battleMode, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val inBattle = mode == 1 || mode == 2
        var enemy = if (inBattle) readEnemy() else run { enemyMoves.clear(); null }
        val battling = inBattle && enemy != null
        // The player's Pokemon on the field (wCurBattleMon, a slot; eggs are not in [party], so through partySlots):
        // slot 1 is not it after a switch (rc33 audit P1).
        val onField = if (battling && m.curBattleMon != 0L)
            ram(m.curBattleMon, 1).let { if (it.isEmpty()) -1 else it.u8(0) }.let { s -> partySlots.indexOf(s).takeIf { it >= 0 } } ?: 0
        else 0
        // Battle.updateStatStagesGen2 (Gen 2 reference Battle.lua:699-722): the active battlers' stages,
        // drawn only in battle, yours on the Pokemon on the field as the reference views it (Battle.getViewedPokemon).
        if (battling && party.isNotEmpty()) {
            party = party.mapIndexed { i, p -> if (i == onField) p.copy(statStages = gbStatStages(ram(m.statLevels, 7), GEN2_STAGES)) else p }
            enemy = enemy?.copy(statStages = gbStatStages(ram(m.statLevels + 8, 7), GEN2_STAGES))
        }
        lastMove.read(battling, turn = ram(m.playerTurns, 1).let { if (it.isEmpty()) 0 else it.u8(0) },
            move = ram(m.enemyMove, 1).let { if (it.isEmpty()) 0 else it.u8(0) })
        val johto = ram(m.johtoBadges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        val kanto = ram(m.kantoBadges, 1).let { if (it.isEmpty()) 0 else it.u8(0) }
        // Crystal's landmark; on Gold and Silver, which keep none, the map group and number.
        val mapId: Int? = when {
            m.curLandmark != 0L -> ram(m.curLandmark, 1).takeIf { it.isNotEmpty() }?.u8(0)
            m.mapGroup != 0L -> ram(m.mapGroup, 2).takeIf { it.size == 2 }?.let { (it.u8(0) shl 8) or it.u8(1) }
            else -> null
        }
        val lead = party.getOrNull(onField)
        val heals = readHeals(bag, lead?.mon?.maxHp ?: 0)
        // Read first, as the GBA tracker reads its win before its loss (rc32 audit P2 #135).
        val won = win.read(mode, byteAt(m.trainerClass), byteAt(m.battleResult))
        return TrackerState(
            partyCount = party.size,
            party = party,
            ownOnField = onField,
            inBattle = battling,
            isWildBattle = inBattle && mode == 1,
            enemy = enemy,
            // "Team:" in a trainer battle: the one ball the reference knows (gbEnemyTeam).
            enemyTeam = gbEnemyTeam(trainerBattle = battling && mode == 2, enemy = enemy),
            // Johto's eight in the art's order, Kanto's eight above them (the badge row draws all sixteen).
            badges = johtoInArtOrder(johto) or (kanto shl 8),
            badgeSet = "GSC",
            // The Hall of Fame flag; a Kanto badge says the same, as Kanto's gyms open only after the League.
            leagueBeaten = kanto != 0 || (m.statusFlags != 0L && ram(m.statusFlags, 1).let { it.isNotEmpty() && (it.u8(0) and 0x40) != 0 }),
            healPercent = heals.percent,
            healCount = heals.count,
            healHp = heals.hp,
            // "Last move: X" between the enemy's moves (GbLastMove); MoveData.isValid is 1..251.
            lastAttackMove = lastMove.shown.takeIf { it in 1..251 }?.let { moveName(it) },
            // Program.updateMapLocation (Program.lua:1121-1134): the map is wCurLandmark, named from
            // RouteData.Info (setupRouteInfoAsGSC), and any map read is a valid location
            // (isValidMapLocation is mapId ~= nil), so the Time Machine makes its points.
            mapId = mapId, routeName = if (m.curLandmark != 0L) mapId?.let { landmarkNames[it] } else null,
            // The player's condition, checked once the battle byte reads 0, never mid-battle (GbGameOver).
            gameOver = if (won) GameOver.WON else if (GbGameOver.lost(mode, party, lossCondition)) GameOver.LOST else null,
            diagnostics = "${m.name}  party=%d mode=%d".format(party.size, mode),
            nuz = runCatching { nuzReads(party, mode, battling, enemy, bag, mapId) }.getOrNull(),
            // A count of 0 is a game with no party yet (the title screen, the
            // intro): the panel says so. A count of 1..6 with no decodable mon,
            // or a count no party can have, is a map that does not fit this ROM.
            unreadable = party.isEmpty() && lastCount != 0,
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
 * gen2_offsets.ini entries for Gold (U), which Silver (U) copies
 * ([evosAttacks] is its PokemonMovesetsTableOffset).
 */
data class Gen2Map(
    val name: String,
    val partyCount: Long, val partySpecies: Long, val partyMons: Long,
    val enemyMon: Long, val battleMode: Long, val enemyMove: Long,
    val johtoBadges: Long, val kantoBadges: Long, val numItems: Long, val items: Long,
    val baseStats: Int, val moves: Int,
    val evosAttacks: Int = 0,
    /** wEnemyTurnsTaken, for the once-per-enemy-turn rule on [enemyMove]. */
    val enemyTurns: Long = 0L,
    /** wPlayerTurnsTaken, the reference's gTurn, for the last-move line (GbLastMove). */
    val playerTurns: Long = 0L,
    /** wPlayerStatLevels, seven stage bytes, wEnemyStatLevels 8 on: the reference's StatChange. */
    val statLevels: Long = 0L,
    /**
     * wCurLandmark, the reference's gMapHeader (0x020002D9): the landmark of the
     * map, set on entering it (engine/events/map_name_sign.asm), 0xFF in a gate.
     * Crystal only; Gold and Silver have no such variable.
     */
    val curLandmark: Long = 0L,
    /** wMapGroup, then wMapNumber: the map on Gold and Silver, which no reference reads. */
    val mapGroup: Long = 0L,
    // ---- The Nuzlocke reads (2026-09-30). pokecrystal and pokegold WRAM, from tools/wram_layout.py, as offsets like the rest. ----
    /** wPlayerID (big endian), wBattleResult, wBattleType, wOtherTrainerClass, wOtherTrainerID, wOptions, wPlayerState. */
    val playerId: Long = 0L, val battleResult: Long = 0L, val battleType: Long = 0L, val trainerClass: Long = 0L,
    val trainerNo: Long = 0L, val options: Long = 0L, val playerState: Long = 0L,
    /** wNumBalls and wBalls (the ball pocket), the party's nicknames (six of 11 bytes) and the enemy's DVs (wEnemyMon + 6). */
    val numBalls: Long = 0L, val balls: Long = 0L, val nicks: Long = 0L, val enemyDvs: Long = 0L,
    /** wWildMon: zeroed at every throw and set to the species only when a throw catches, until the battle is over. */
    val wildMon: Long = 0L,
    /** The randomizer's TrainerDataTableOffset ([Crystal (U)] and [Gold (U)] in gen2_offsets.ini). */
    val trainerTable: Int = 0,
    /**
     * w2DMenuCursorInitY, the first of the 2D menu's bytes (InitY, InitX, NumRows, NumCols, Flags1, Flags2,
     * CursorOffsets, JoypadFilter, CursorY, CursorX, CursorOffCharacter, CursorCurrentTile x2): pokecrystal 0xCFA1,
     * pokegold 0xCED8 (tools/wram_layout.py). B-to-Run reads the battle menu from them.
     */
    val menu2D: Long = 0L,
    /** wCurBattleMon, the party slot of the player's Pokemon in battle: pokecrystal 0xD0D4, pokegold 0xCFC6. */
    val curBattleMon: Long = 0L,
    /**
     * wStatusFlags, whose bit 6 is STATUSFLAGS_HALL_OF_FAME_F, set when the Johto League is beaten
     * (engine/events/halloffame.asm:15): eleven bytes below wJohtoBadges in both games (wStatusFlags, wStatusFlags2,
     * wMoney 3, wMomsMoney 3, wMomSavingMoney, wCoins 2: pokecrystal ram/wram.asm:3071-3105, pokegold :2487-2504),
     * pokecrystal 0xD84C, pokegold 0xD571. Survival's Kanto heals wait on it.
     */
    val statusFlags: Long = 0L,
) {
    companion object {
        val CRYSTAL = Gen2Map(
            name = "Crystal",
            partyCount = GbcTracker.PARTY_COUNT, partySpecies = GbcTracker.PARTY_SPECIES, partyMons = GbcTracker.PARTY_MONS,
            enemyMon = GbcTracker.ENEMY_MON, battleMode = GbcTracker.BATTLE_MODE, enemyMove = GbcTracker.ENEMY_LAST_MOVE,
            enemyTurns = GbcTracker.ENEMY_TURNS, playerTurns = GbcTracker.PLAYER_TURNS, statLevels = GbcTracker.STAT_LEVELS,
            curLandmark = GbcTracker.CUR_LANDMARK,
            johtoBadges = GbcTracker.JOHTO_BADGES, kantoBadges = GbcTracker.KANTO_BADGES,
            numItems = GbcTracker.NUM_ITEMS, items = GbcTracker.ITEMS,
            baseStats = GbcTracker.BASE_STATS, moves = GbcTracker.MOVES, evosAttacks = GbcTracker.EVOS_ATTACKS,
            // wPlayerID D47B, wBattleResult D0EE, wBattleType D230, wOtherTrainerClass D22F, wOtherTrainerID D231, wOptions CFCC,
            // wPlayerState D95D, wNumBalls D8D7, wBalls D8D8, wPartyMonNicknames DE41, wEnemyMonDVs D20C.
            playerId = 0x147BL, battleResult = 0x10EEL, battleType = 0x1230L, trainerClass = 0x122FL, trainerNo = 0x1231L,
            options = 0x0FCCL, playerState = 0x195DL, numBalls = 0x18D7L, balls = 0x18D8L, nicks = 0x1E41L, enemyDvs = 0x120CL,
            wildMon = 0x064EL,      // wWildMon C64E
            trainerTable = 0x39999,
            menu2D = 0x0FA1L,
            curBattleMon = 0x10D4L,
            statusFlags = GbcTracker.JOHTO_BADGES - 11,
        )

        /** pokegold: wPartyCount DA22, wPartySpecies DA23, wPartyMons DA2A, wEnemyMon D0EF, wBattleMode D116,
         *  wEnemyMoveStruct CAE8, wJohtoBadges D57C, wKantoBadges D57D, wNumItems D5B7, wItems D5B8,
         *  wEnemyTurnsTaken CBBA, wPlayerTurnsTaken CBBB, wPlayerStatLevels CBAA, wMapGroup DA00. */
        val GS = Gen2Map(
            name = "Gold/Silver",
            partyCount = 0x1A22L, partySpecies = 0x1A23L, partyMons = 0x1A2AL,
            enemyMon = 0x10EFL, battleMode = 0x1116L, enemyMove = 0x0AE8L, enemyTurns = 0x0BBAL,
            playerTurns = 0x0BBBL, statLevels = 0x0BAAL, mapGroup = 0x1A00L,
            johtoBadges = 0x157CL, kantoBadges = 0x157DL, numItems = 0x15B7L, items = 0x15B8L,
            baseStats = 0x51B0B, moves = 0x41AFE, evosAttacks = 0x427BD,
            // pokegold: wPlayerID D1A1, wBattleResult CFE9, wBattleType D119, wOtherTrainerClass D118, wOtherTrainerID D11B,
            // wOptions D199, wPlayerState D682, wNumBalls D5FC, wBalls D5FD, wPartyMonNicknames DB8C, wEnemyMonDVs D0F5.
            playerId = 0x11A1L, battleResult = 0x0FE9L, battleType = 0x1119L, trainerClass = 0x1118L, trainerNo = 0x111BL,
            options = 0x1199L, playerState = 0x1682L, numBalls = 0x15FCL, balls = 0x15FDL, nicks = 0x1B8CL, enemyDvs = 0x10F5L,
            wildMon = 0x114FL,      // wWildMon D14F
            trainerTable = 0x3993E,
            menu2D = 0x0ED8L,
            curBattleMon = 0x0FC6L,
            statusFlags = 0x157CL - 11,
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
