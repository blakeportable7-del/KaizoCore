package com.ironmonone.tracker

/**
 * Per-game memory map. Vanilla Emerald values imported from the MIT tracker's own
 * machine-readable file (ironmon_tracker/GameAddresses/Pokemon Emerald.json, read
 * 2026-08-30) — not transcribed from a wiki.
 *
 * The name-table addresses are NOT in that file (the Lua tracker bundles name lists
 * instead). We read names from the ROM itself — which is what makes randomized and
 * Nat. Dex species work for free — and the constants are verified empirically by a
 * test against the real clean dump before anything trusts them.
 */
data class GameMap(
    val name: String,
    val partyCount: Long,
    val party: Long,
    val enemyParty: Long,
    val battleTypeFlags: Long,
    val battleMons: Long,
    val battlersCount: Long,
    /** gBattlescriptCurrInstr - the current battle script instruction,
     *  matched against the ability script table to detect activations. */
    val scriptCurrInstr: Long = 0,
    /** gBattleScripting.battler (a byte). */
    val scriptingBattler: Long = 0,
    val battlerAttacker: Long = 0,
    val battlerTarget: Long = 0,
    /** Which abilityscripts-*.tsv this game uses; "" = feature off. */
    val abilityScriptTable: String = "",
    val baseStats: Long,
    val speciesNames: Long,     // 11 bytes per name; 0 = no table known
    val moveNames: Long,        // 13 bytes per name; 0 = no table known
    /** Vanilla SpeciesInfo is 28 bytes with u8 abilities at 22/23. Nat. Dex uses the
     *  expansion struct: 0x24 bytes, u16 abilities at 0x16/0x18 — derived empirically
     *  from the real 1.2.1 ROM (Bulbasaur/Ivysaur/Charmander/Pikachu fingerprints). */
    val baseStatsStride: Int = 28,
    val abilitiesAreU16: Boolean = false,
    /** gActionSelectionCursor (pret symbols; exact for vanilla). 0 = unknown — the
     *  write-based flee fallback stays disabled and only the input macro is used. */
    val actionCursor: Long = 0,
    /** gBattleResults; lastUsedMoveOpponent lives at +0x24 (pret BattleResults). */
    val battleResults: Long = 0,
    /** gSaveBlock1Ptr — SaveBlock1 begins with the player's x,y (u16 each). */
    val saveBlock1Ptr: Long = 0,
    /** gSaveBlock2Ptr. The bag's security key lives here, not in SaveBlock1. */
    val saveBlock2Ptr: Long = 0,
    /** Randomized starter table (ZX's own StarterPokemon offsets). ball order is
     *  slot1=left, slot2=middle, slot3=right — verified empirically: the left ball
     *  contained species u16@base on the live seed. 0 = unknown. */
    val startersBase: Long = 0,
    val starter2Off: Int = 0,
    val starter3Off: Int = 0,
    /** True when species/move names come from the bundled lists (Nat. Dex: no ROM
     *  name-table pointer is published, and the lists are used with permission). */
    val namesFromLists: Boolean = false,
    /**
     * True when this ROM uses the expansion's EXTENDED species numbering.
     *
     * These ids are NOT national dex numbers, which is worth saying plainly
     * because they look like they might be. They continue Gen 3's internal
     * ordering: 1-251 match the national dex by coincidence, the Hoenn mons
     * sit at 277-411, and the expansion carries on from 412. So Turtwig is
     * 412, not its national 387, and Golisopod is 793, not its national 768 -
     * and id 768 is Ribombee.
     *
     * The bundled sprite pack is indexed by these same ids, because it is the
     * pack the expansion itself ships alongside these name lists. Index it
     * with a national dex number and it draws a different animal, with no
     * error anywhere to say so.
     */
    val expandedSpeciesIds: Boolean = false,
    /** Ability/item name tables, located empirically (tools/find_tables.py anchors
     *  on STENCH/DRIZZLE and MASTER BALL/ULTRA BALL, then verifies INTIMIDATE,
     *  PICKUP, BLAZE, LEVITATE and POTION before trusting). 0 = "#id" fallback. */
    val abilityNames: Long = 0,
    val abilityStride: Int = 13,
    val itemNames: Long = 0,
    val itemStride: Int = 44,
    /** gBattleMoves, stride 12: effect,power,type,accuracy,pp. Found by the same
     *  script via POUND/FLAMETHROWER/PSYCHIC value anchors; matches pret symbols
     *  exactly on both vanilla games. 0 = hide Pow/Acc (Nat. Dex: the vanilla
     *  table bytes survive the patch but the expansion rebalances move data in
     *  its own struct, so reading the leftover table would show wrong numbers). */
    val battleMoves: Long = 0,
    /** gBattleWeather (pret symbols; display is additionally gated on the bits
     *  matching a known weather mask so a wrong address shows nothing). */
    val weather: Long = 0,
    /** Front-pic and palette tables ({romPtr,size,tag} entries, LZ77 targets),
     *  located empirically by tools/find_sprites.py and eyeball-verified per
     *  game. 0 = no sprites. spriteCount bounds the species index. */
    val frontPics: Long = 0,
    val palettes: Long = 0,
    /** Badge flags inside SaveBlock1. FireRed stores a byte at 0xFE4; Emerald a
     *  word at 0x137C whose 8 badge bits start at bit 7. Offsets from the
     *  reference tracker's GameAddresses JSONs. */
    val badgeOffset: Long = 0,
    val badgeIsWord: Boolean = false,
    /** Which badge art to draw: FRLG, RSE or DPPT. */
    val badgeSet: String = "FRLG",
    /** gLevelUpLearnsets: one pointer per species to a 0xFFFF-terminated list of
     *  u16 entries, move in bits 0-8 and level in bits 9-15. Randomizers rewrite
     *  this, so it must be read live rather than bundled. */
    val levelUpLearnsets: Long = 0,
    /**
     * Nat. Dex learnsets are 4-byte {u16 move, u16 level} entries, not the
     * vanilla u16 with move in bits 0-8 and level in 9-15. Reading the wide
     * format with the packed decoder yields garbage that mostly fails the
     * move-id sanity check, which is why the bug looked like "0 moves"
     * rather than wrong moves.
     */
    val learnsetWide: Boolean = false,
    /** SaveBlock1 offsets for the Items pocket, and the security key that the
     *  quantities are XORed with (Gen 3 encrypts item counts). */
    val bagItemsOffset: Long = 0,
    val bagItemsSlots: Int = 0,
    /**
     * The Berries pouch, which is a SEPARATE pocket from Items in both games.
     * Berries are most of the healing you carry in a ruleset that forbids
     * buying any, so reading only the Items pocket undercounts badly.
     * Offsets and sizes are the decompilations' SaveBlock1 layout:
     * FireRed 0x54C x43, Emerald 0x790 x46.
     */
    val bagBerriesOffset: Long = 0,
    val bagBerriesSlots: Int = 0,
    /**
     * gMapHeader. The current map id is a word at +0x12 (mapLayoutId), which is
     * how the reference reads it in Program.updateMapLocation.
     */
    val mapHeader: Long = 0,
    /**
     * Maps where the starter is chosen, from RouteData.Locations.IsInLab:
     * map 5 in FRLG (Oak's lab), map 17 in RSE (Route 101, Birch's bag).
     */
    val labMapIds: Set<Int> = emptySet(),
    /** Which route table this game uses: "frlg" or "rse". */
    val routeTable: String = "",
    /**
     * How one Pokemon is laid out in memory. Vanilla is the fixed 100-byte
     * Gen 3 struct; the Nat. Dex expansion inserts four bytes before the
     * encrypted substructures, shifting everything after by +4 and making the
     * struct 104. It publishes its real offsets in the ROM, so they are read
     * rather than assumed.
     */
    val monLayout: PokemonDecoder.Layout = PokemonDecoder.Layout.VANILLA,
    /**
     * sizeof(struct BattlePokemon) - the stride between battlers in
     * gBattleMons. 88 (0x58) in vanilla Gen 3; Nat. Dex grows it to 92.
     *
     * This was hardcoded to 0x58, so on Nat. Dex the enemy battler was read
     * FOUR BYTES EARLY, landing in battler 0's tail. The species decoded as
     * garbage, failed validation, and readEnemy returned null - so the
     * opponent's card never appeared in any battle, wild or trainer, while
     * the TRAINER BATTLE banner still showed because battler 0 sits at
     * offset 0 and is correct at any stride.
     *
     * Only the stride is known. Every field this tracker reads out of a
     * battler is at or below 0x2E (the ROM publishes stat stages at 0x18 and
     * types at 0x21, both vanilla), and status2 is published at 0x54 against
     * a vanilla 0x50 - so the four inserted bytes sit somewhere in
     * 0x23..0x53, which is exactly why the status word is no longer read
     * from this struct at all (see enemyPartyStatus).
     */
    val battleMonSize: Int = 0x58,
    /** Game stats block in SaveBlock1; each stat is a u32 XORed with the key. */
    val gameStatsOffset: Long = 0,
    /** gBattleOutcome: 1 won, 2 lost, 3 tied. */
    val battleOutcome: Long = 0,
    /**
     * gBattleMainFunc and the four battle-phase functions it points at.
     *
     * The reference does NOT decide "am I in a battle" from gBattlersCount.
     * That byte is not cleared when a battle ends, so on its own it leaves the
     * enemy card on screen for the rest of the session - which is exactly the
     * bug this pins. Battle.lua reads three signals: gBattlersCount, whether
     * gBattleMons[0] holds a REAL species, and gBattleOutcome, whose own
     * comment says "isn't cleared when a battle ends" and so is only 0 while a
     * battle is live. gBattleMainFunc then times when data may be read and
     * when teardown is safe. 0 = symbol unknown; see inBattle() for the
     * degraded path.
     */
    val battleMainFunc: Long = 0,
    val introDrawPartySummary: Long = 0,
    val introOpponentSendsOut: Long = 0,
    val handleTurnAction: Long = 0,
    val returnToOverworld: Long = 0,
    /** gTrainerBattleOpponent_A: which trainer the last battle was against. */
    val trainerOpponent: Long = 0,
    /**
     * Beating one of these IS the win condition - the run is over and won.
     * FRLG has three because the champion's team depends on your starter.
     */
    val finalTrainers: Set<Int> = emptySet(),
    val encryptionKeyOffset: Long = 0,
    /** Vanilla species ceiling. Entries past a table's real end fail the LZ77
     *  sanity checks and decode to null rather than garbage. */
    val spriteCount: Int = 412,
) {
    companion object {
        val EMERALD_U = GameMap(
            name = "Emerald (U)",
            partyCount = 0x020244E9,
            party = 0x020244EC,
            enemyParty = 0x02024744,
            battleTypeFlags = 0x02022FEC,
            battleMons = 0x02024084,
            battlersCount = 0x0202406C,
            scriptCurrInstr = 0x02024214,
            scriptingBattler = 0x0202448B,
            battlerAttacker = 0x0202420B,
            battlerTarget = 0x0202420C,
            abilityScriptTable = "emerald",
            baseStats = 0x083203CC,
            speciesNames = 0x083185C8,
            moveNames = 0x0831977C,
            actionCursor = 0x020244AC,
            battleResults = 0x03005D10,
            saveBlock1Ptr = 0x03005D8C,
            saveBlock2Ptr = 0x03005D90,
            abilityNames = 0x0831B6DB,
            itemNames = 0x085839A0,
            battleMoves = 0x0831C898,
            weather = 0x020243CC,
            frontPics = 0x08301418,
            palettes = 0x08303678,
            badgeOffset = 0x137C,
            badgeIsWord = true,
            badgeSet = "RSE",
            levelUpLearnsets = 0x0832937C,
            bagItemsOffset = 0x560,
            bagItemsSlots = 30,
            bagBerriesOffset = 0x790,
            bagBerriesSlots = 46,
            mapHeader = 0x02037318,
            labMapIds = setOf(17),
            routeTable = "rse",
            gameStatsOffset = 0x159C,
            battleOutcome = 0x0202433A,
            battleMainFunc = 0x03005D04,
            introDrawPartySummary = 0x0803AF81,
            introOpponentSendsOut = 0x0803B315,
            handleTurnAction = 0x0803BE75,
            returnToOverworld = 0x0803DF71,
            trainerOpponent = 0x02038BCA,
            finalTrainers = setOf(804),
            encryptionKeyOffset = 0xAC,
        )

        /**
         * FireRed (U) v1.0. Addresses from the MIT tracker's "Pokemon FireRed v1.0.json";
         * name tables located EMPIRICALLY in the ROM (BULBASAUR/POUND signatures,
         * cross-checked by TREECKO at species 277), 2026-08-30.
         */
        val FIRERED_U_V10 = GameMap(
            name = "FireRed (U) v1.0",
            partyCount = 0x02024029,
            party = 0x02024284,
            enemyParty = 0x0202402C,
            battleTypeFlags = 0x02022B4C,
            battleMons = 0x02023BE4,
            battlersCount = 0x02023BCC,
            scriptCurrInstr = 0x02023D74,
            scriptingBattler = 0x02023FDB,
            battlerAttacker = 0x02023D6B,
            battlerTarget = 0x02023D6C,
            abilityScriptTable = "firered",
            baseStats = 0x08254784,
            speciesNames = 0x08245EE0,
            moveNames = 0x08247094,
            battleResults = 0x03004F90,
            saveBlock1Ptr = 0x03005008,
            saveBlock2Ptr = 0x0300500C,
            startersBase = 0x08169BB5,
            starter2Off = 515,
            starter3Off = 461,
            abilityNames = 0x0824FC40,
            itemNames = 0x083DB028,
            battleMoves = 0x08250C04,
            weather = 0x02023F1C,
            frontPics = 0x082350AC,   // == pret gMonFrontPicTable
            palettes = 0x0823730C,    // == pret gMonPaletteTable
            badgeOffset = 0xFE4,
            badgeIsWord = false,
            badgeSet = "FRLG",
            levelUpLearnsets = 0x0825D7B4,
            bagItemsOffset = 0x310,
            bagItemsSlots = 42,
            bagBerriesOffset = 0x54C,
            bagBerriesSlots = 43,
            mapHeader = 0x02036DFC,
            labMapIds = setOf(5),
            routeTable = "frlg",
            gameStatsOffset = 0x1200,
            battleOutcome = 0x02023E8A,
            battleMainFunc = 0x03004F84,
            introDrawPartySummary = 0x0801333D,
            introOpponentSendsOut = 0x0801359D,
            handleTurnAction = 0x08014041,
            returnToOverworld = 0x08015B59,
            trainerOpponent = 0x020386AE,
            finalTrainers = setOf(438, 439, 440),
            encryptionKeyOffset = 0xF20,
        )

        /** Nat. Dex bakes 1258 into ROM at this address; vanilla has other bytes here. */
        const val NATDEX_MAGIC_ADDR = 0x08000170L
        const val NATDEX_MAGIC = 1258L

        /**
         * Resolve the right map for whatever ROM is loaded. Nat. Dex publishes its
         * addresses through a pointer table baked into the ROM (slots read from the
         * extension's own Lua on 2026-08-30) — that is how each release absorbs its
         * layout reorganisations, and why nothing here may ever hardcode a Nat. Dex
         * EWRAM address. Name tables have no pointer slots; Nat. Dex names fall back
         * to "#id" until the granted name lists are imported.
         */
        /** A pointer only counts if it lands in the ROM region; else 0. */
        private fun romPtr(v: Long): Long =
            if (v in 0x08000000L..0x09FFFFFFL) v else 0L

        /** As [romPtr] but for EWRAM: implausible values become 0 = feature off. */
        private fun ewramPtr(v: Long): Long =
            if (v in 0x02000000L..0x0203FFFFL) v else 0L

        /** As [romPtr] but for IWRAM. */
        private fun iwramPtr(v: Long): Long =
            if (v in 0x03000000L..0x03007FFFL) v else 0L

        private fun isEmeraldHeader(memory: MemoryReader): Boolean {
            val code = memory.read(0x080000AC, 4)
            return code.size == 4 && code[0] == 'B'.code.toByte() &&
                code[1] == 'P'.code.toByte() && code[2] == 'E'.code.toByte()
        }

        /**
         * [resolve], but null instead of throwing when the ROM cannot be
         * identified yet. Callers poll with this: a core mid-load answers
         * with zeros for a moment, and the right response is to try again,
         * never to guess a map.
         */
        /**
         * FireRed v1.1, which differs from v1.0 only in ROM addresses.
         *
         * Every RAM address is identical between the two revisions - party,
         * battle structs, save blocks, gBattleMainFunc - so a v1.1 ROM read
         * DECODES FINE and looks correct. What shifts is three ROM tables and
         * the ability battle scripts, all by +0x70, so the tracker would show
         * the wrong types, BST, move power and learnsets with total
         * confidence, and never trip the sanity check that catches a wrong
         * map. v1.1 matters because Nat. Dex requires it as its base.
         */
        val FIRERED_U_V11 = FIRERED_U_V10.copy(
            name = "FireRed (U) v1.1",
            // The battle-phase functions are ROM code, so they move too - but
            // by +0x14, NOT the +0x70 the data tables shift by. gBattleMainFunc
            // itself is RAM and is identical between the revisions.
            introDrawPartySummary = 0x08013351,
            introOpponentSendsOut = 0x080135B1,
            handleTurnAction = 0x08014055,
            returnToOverworld = 0x08015B6D,
            baseStats = 0x082547F4,
            battleMoves = 0x08250C74,
            levelUpLearnsets = 0x0825D824,
            abilityScriptTable = "firered11",
        )

        fun resolveOrNull(memory: MemoryReader): GameMap? =
            runCatching { resolve(memory) }.getOrNull()

        fun resolve(memory: MemoryReader): GameMap {
            fun ptr(addr: Long): Long {
                val b = memory.read(addr, 4)
                return if (b.size == 4) b.u32(0) else 0L
            }
            /**
             * A save-block OFFSET published in the ROM's slot table.
             *
             * Nat. Dex moves the save layout, and every one of these was
             * hardcoded to the VANILLA value while the pointers around them
             * were already being read from the table. Six of the seven were
             * wrong on both builds, which is why the step counter read
             * 8,487,400 on a fresh save: the wrong address XORed with a key
             * fetched from the wrong offset.
             *
             * Bounded and falling back to the vanilla constant, so an
             * unreadable table degrades to today's behaviour instead of
             * reading unrelated memory.
             */
            fun slotOffset(addr: Long, fallback: Long): Long {
                val v = ptr(addr)
                return if (v in 0x1L..0x3FFFL) v else fallback
            }
            fun slotByte(addr: Long, fallback: Int): Int {
                val b = memory.read(addr, 1)
                val v = if (b.isEmpty()) 0 else (b[0].toInt() and 0xFF)
                return if (v in 1..200) v else fallback
            }
            val magic = ptr(NATDEX_MAGIC_ADDR)
            if (magic != NATDEX_MAGIC) {
                // Vanilla: pick the map by the header game code. An
                // UNRECOGNISED header is a refusal, not an Emerald default.
                //
                // This used to be `BPR -> FireRed else Emerald`, so a read
                // that returned zeros - which is what the ROM region gives
                // while the core is still loading it, right after a NEW RUN
                // reboot - resolved to EMERALD on a FireRed game. The tracker
                // is built once and cached, so that wrong map stuck for the
                // whole session and every party decode came back garbage:
                // "TRACKER CANNOT READ THIS ROM", permanently, on a ROM that
                // was perfectly fine.
                val code = memory.read(0x080000AC, 4)
                if (code.size < 4) error("ROM header unreadable")
                val id = String(code.copyOfRange(0, 3), Charsets.US_ASCII)
                // The header code is "BPRE" for BOTH FireRed revisions; only
                // the version byte at 0xBC separates them.
                val verByte = memory.read(0x080000BC, 1)
                val ver = if (verByte.isEmpty()) 0 else verByte[0].toInt() and 0xFF
                return when (id) {
                    "BPR" -> if (ver >= 1) FIRERED_U_V11 else FIRERED_U_V10
                    "BPE" -> EMERALD_U
                    else -> error("Unrecognised ROM header \"" + id + "\"")
                }
            }

            val map = GameMap(
                name = "Nat. Dex",
                // The player party is identified STRUCTURALLY, not by slot
                // order. Both Nat. Dex builds lay the block out the same way -
                // the count byte, the player party 3 bytes later, the enemy
                // party 0x270 after that - but the FireRed build TRANSPOSES
                // the two party slots relative to Emerald:
                //
                //   Emerald  count 020244D1  slot27C 020244D4  slot280 02024744
                //   FireRed  count 020242F5  slot27C 02024568  slot280 020242F8
                //
                // Reading slot 0x27C as "the party" is right on Emerald and
                // gives the ENEMY party on FireRed, so every FireRed Nat. Dex
                // run decoded the wrong block and reported the ROM unreadable.
                // Picking whichever slot sits at count+3 is correct for both
                // and survives another build swapping them again.
                partyCount = ptr(0x08000278),
                // The extension documents these slots directly
                // (NatDexExtension.lua:17689-17691). An earlier version tried
                // to pick the player party structurally as "the slot at
                // count+3", which is how EMERALD happens to lay it out and is
                // not a rule - vanilla FireRed puts a 0x25B gap between them.
                // The real cause of the unreadable party was the struct
                // layout below, not these addresses.
                party = ptr(0x0800027C),
                enemyParty = ptr(0x08000280),
                battleTypeFlags = ptr(0x0800020C),
                battleMons = ptr(0x08000228),
                battlersCount = ptr(0x08000218),
                // The extension reads these from the same slot block
                // (NatDexExtension.lua:17680-17686).
                scriptCurrInstr = ewramPtr(ptr(0x08000238)),
                scriptingBattler = ewramPtr(ptr(0x0800026C)).let {
                    if (it != 0L) it + 0x17 else 0L  // gBattleScripting.battler
                },
                battlerAttacker = ewramPtr(ptr(0x08000230)),
                battlerTarget = ewramPtr(ptr(0x08000234)),
                abilityScriptTable = "natdex",
                baseStats = ptr(0x080001BC),
                speciesNames = 0L,      // no pointer slot; names via imported lists later
                moveNames = 0L,
                baseStatsStride = 0x24,
                abilitiesAreU16 = true,
                battleResults = ptr(0x080002D8),
                namesFromLists = true,
                expandedSpeciesIds = true,
                // Nat. Dex publishes these three itself in the same pointer block
                // (discovered 2026-08-30; the values match the empirical scans
                // exactly: abilities 0x0838D56C, items 0x085FC720, battle moves
                // at the vanilla-layout 0x0831C898). Strides verified by scan.
                abilityNames = romPtr(ptr(0x080001C0)),
                abilityStride = 17,
                itemNames = romPtr(ptr(0x080001C8)),
                itemStride = 52,
                battleMoves = romPtr(ptr(0x080001CC)),
                // ALL 1283 species, not just the vanilla ones. Both tables are
                // indexed directly by species id and simply continue past 411;
                // an earlier scan split them into "separate runs" at a pointer
                // that failed a sanity check, which is what made the expansion
                // half look unreachable. Verified by rendering Turtwig (412),
                // Piplup, Lucario, Togekiss and species 1283 out of the real ROM
                // (tools/natdex_palette_lock.py, plus fifteen color anchors).
                frontPics = if (isEmeraldHeader(memory)) 0x08369C2C else 0L,
                palettes = if (isEmeraldHeader(memory)) 0x08360FA8 else 0L,
                spriteCount = 1284,
                // Published by the ROM at 0x08000436, the same slot table the
                // reference extension reads it from
                // (NatDexExtension.lua:17593 sizeofBattlePokemon). Bounded
                // rather than trusted: a bad read must fall back to vanilla,
                // not scatter reads across IWRAM.
                battleMonSize = run {
                    val v = memory.read(0x08000436, 2)
                    val n = if (v.size < 2) 0 else v.u16(0)
                    if (n in 0x58..0x80) n else 0x58
                },
                // Save layout is the base game's, untouched by the expansion:
                // Nat. Dex only moves the DATA tables it grows. The learnset
                // table DOES move, and has its own published address.
                // These five moved in Nat. Dex 1.21 and are published in the
                // ROM's own slot table - the extension reads every one of them
                // there (NatDexExtension.lua:17683-17741). The old hardcodes
                // were the VANILLA addresses, so badges, heals, the route
                // panel and win detection all read unrelated memory on 1.21.
                saveBlock1Ptr = iwramPtr(ptr(0x080002E0)),
                saveBlock2Ptr = iwramPtr(ptr(0x080002E4)),
                encryptionKeyOffset = slotOffset(0x080002D0,
                    if (isEmeraldHeader(memory)) 0xAC else 0xF20),
                badgeOffset = slotOffset(0x080002B8,
                    if (isEmeraldHeader(memory)) 0x137C else 0xFE4),
                badgeIsWord = isEmeraldHeader(memory),
                badgeSet = if (isEmeraldHeader(memory)) "RSE" else "FRLG",
                bagItemsOffset = slotOffset(0x080002BC,
                    if (isEmeraldHeader(memory)) 0x560 else 0x310),
                bagItemsSlots = slotByte(0x080001E4,
                    if (isEmeraldHeader(memory)) 30 else 42),
                bagBerriesOffset = slotOffset(0x080002CC,
                    if (isEmeraldHeader(memory)) 0x790 else 0x54C),
                bagBerriesSlots = slotByte(0x080001E8,
                    if (isEmeraldHeader(memory)) 46 else 43),
                levelUpLearnsets = romPtr(ptr(0x0800030C)),
                learnsetWide = true,
                mapHeader = ewramPtr(ptr(0x08000284)),
                labMapIds = if (isEmeraldHeader(memory)) setOf(17) else setOf(5),
                routeTable = if (isEmeraldHeader(memory)) "rse" else "frlg",
                monLayout = run {
                    fun u16(addr: Long): Int {
                        val b = memory.read(addr, 2)
                        return if (b.size < 2) 0 else b.u16(0)
                    }
                    val size = u16(0x08000442)
                    val enc = u16(0x08000414)
                    val status = u16(0x08000416)
                    val lvl = u16(0x08000418)
                    val maxHp = u16(0x0800041A)
                    // Refuse nonsense rather than decode against garbage: an
                    // unreadable table falls back to the vanilla layout.
                    if (size in 100..160 && enc in 0x10..0x40 && lvl in 0x40..0x80)
                        PokemonDecoder.Layout(
                            size = size, enc = enc, status = status,
                            level = lvl, curHp = lvl + 2, maxHp = maxHp)
                    else PokemonDecoder.Layout.VANILLA
                },
                gameStatsOffset = slotOffset(0x080002B4,
                    if (isEmeraldHeader(memory)) 0x159C else 0x1200),
                battleOutcome = ewramPtr(ptr(0x08000260)),
                battleMainFunc = ptr(0x080002D4),
                introDrawPartySummary = ptr(0x080002EC),
                introOpponentSendsOut = ptr(0x080002F0),
                handleTurnAction = ptr(0x080002F4),
                returnToOverworld = ptr(0x080002F8),
                trainerOpponent = ewramPtr(ptr(0x08000294)),
                finalTrainers = if (isEmeraldHeader(memory)) setOf(804) else setOf(438, 439, 440),
            )
            // A loud failure beats a blank tracker: if the pointer table did not
            // resolve into plausible regions, refuse rather than read garbage.
            val sane = map.party in 0x02000000L..0x0203FFFFL &&
                map.battleMons in 0x02000000L..0x0203FFFFL &&
                map.baseStats in 0x08000000L..0x09FFFFFFL
            require(sane) {
                "Nat. Dex pointer table did not resolve (party=%08x mons=%08x stats=%08x)"
                    .format(map.party, map.battleMons, map.baseStats)
            }
            return map
        }
    }
}

data class BaseStats(
    val hp: Int, val atk: Int, val def: Int, val spe: Int, val spAtk: Int, val spDef: Int,
    val type1: Int, val type2: Int, val ability1: Int, val ability2: Int,
    /** Gen 1: one Special stat, carried in both spAtk and spDef so damage code reads it either way.
     *  The panel shows it once and the BST counts it once, as the Gen 1 reference tracker does. */
    val singleSpecial: Boolean = false,
) { val bst: Int get() = hp + atk + def + spe + spAtk + (if (singleSpecial) 0 else spDef) }

/** One move row for the panel: PP is live (decrypted from the party struct);
 *  power/accuracy/max PP come from the ROM's move table, null when unknown. */
data class MoveRow(
    /** Move id, so the info screen can look up its description. */
    val id: Int,
    val name: String,
    val pp: Int,
    val ppMax: Int?,     // base PP raised by any PP Ups on this mon
    val power: Int?,     // 0 = status move, shown as "-"
    val acc: Int?,       // 0 = never misses
    val type: Int?,
    /** Gen 3 has no per-move split: the TYPE decides. Types 0-8 are physical,
     *  10-17 special, and 9 is the unused Mystery slot. Randomizers change a
     *  move's type, and the category follows it, so this is derived and never
     *  cached against a move id. */
    val category: String?,
    /** Move priority, -7..7; 0 for the ordinary case. Null when unreadable. */
    val priority: Int? = null,
    /** Whether the move makes contact. Null when unreadable. */
    val contact: Boolean? = null,
)

/**
 * Gen 3 category rule, matching the reference tracker: a move with no power is
 * Status; otherwise the TYPE decides, since Gen 3 has no per-move split
 * (MoveData.TypeToCategory). Randomizers change move types, so this must be
 * derived from the live type rather than a move-id lookup table.
 */
internal fun gen3Category(type: Int, power: Int): String = when {
    power == 0 -> "STA"
    type <= 8 -> "PHY"
    else -> "SPE"
}

/** One battler's stage block, 6 = neutral. Empty outside battle. */
typealias StatStages = Map<String, Int>

data class TrackedMon(
    val mon: PokemonDecoder.Mon,
    val speciesName: String,
    val moveNames: List<String>,
    val base: BaseStats?,
    /** The mon's actual ability, resolved from its ability slot bit against the
     *  (possibly randomized) base-stats table. */
    val abilityName: String = "?",
    val itemName: String = "-",
    val moveRows: List<MoveRow> = emptyList(),
    /** How many level-up moves this species has learned by its current level. */
    val movesLearned: Int = 0,
    /** How many it learns in total. */
    val movesTotal: Int = 0,
    /** Level of the next level-up move, or null when there is nothing left. */
    val nextMoveLevel: Int? = null,
    /** Battle stat stages while this mon is the active battler. */
    val statStages: StatStages = emptyMap(),
    /** SLP/PSN/BRN/FRZ/PAR, or empty when healthy. */
    val statusCondition: String = "",
)

/**
 * The opponent, read from gBattleMons battler 1 (unencrypted BattlePokemon, pret
 * layout: species@0, moves@0xC, ability@0x20, types@0x21/0x22, hp@0x28 u16,
 * level@0x2A, maxHP@0x2C u16). Only [movesSeen] is revealed — moves the enemy has
 * actually used this encounter, via gBattleResults.lastUsedMoveOpponent (+0x24) —
 * never its full moveset. That is the IronMON tracker's information rule.
 */
data class EnemyInfo(
    val species: Int,
    val speciesName: String,
    val level: Int,
    val curHp: Int,
    val maxHp: Int,
    val type1: Int,
    val type2: Int,
    val base: BaseStats?,
    val movesSeen: List<String>,
    /**
     * The same four-row move table the player's card gets.
     *
     * The reference does not give an enemy a different treatment: it fills the
     * ordinary moves area from the TRACKED moves and takes power, accuracy and
     * PP from the ROM's move table (DataHelper.lua:266). PP is the move's BASE
     * PP, because an enemy's remaining PP is not knowable - which is why there
     * is no ppMax here and the column shows a single number.
     */
    val moveRows: List<MoveRow> = emptyList(),
    /** The enemy's POSSIBLE abilities (both base-stat slots) - a legal
     *  hint, never the rolled one. What the panel may always show. */
    val abilityGuess: String = "?",
    /**
     * The rolled ability id from the battle struct. INTERNAL: the panel
     * must not display it directly - the reference reveals an enemy
     * ability only when a battle script shows it activating, and the
     * revealed name arrives through [TrackerState.abilityRevealed].
     */
    val abilityId: Int = 0,
    val statStages: StatStages = emptyMap(),
    val statusCondition: String = "",
)

/**
 * id -> (amount, isPercentage), from the reference tracker's
 * MiscData.HealingItems so the numbers match what the PC tracker shows.
 */
internal val HEAL_ITEMS: Map<Int, Pair<Double, Boolean>> = mapOf(
    13 to (20.0 to false),      // Potion
    19 to (100.0 to true),      // Full Restore
    20 to (100.0 to true),      // Max Potion
    21 to (200.0 to false),     // Hyper Potion
    22 to (50.0 to false),      // Super Potion
    26 to (50.0 to false),      // Fresh Water
    27 to (60.0 to false),      // Soda Pop
    28 to (80.0 to false),      // Lemonade
    29 to (100.0 to false),     // Moomoo Milk
    30 to (50.0 to false),      // Energy Powder
    31 to (200.0 to false),     // Energy Root
    44 to (20.0 to false),      // Berry Juice
    139 to (10.0 to false),     // Oran Berry
    142 to (30.0 to false),     // Sitrus Berry
    143 to (12.5 to true), 144 to (12.5 to true), 145 to (12.5 to true),
    146 to (12.5 to true), 147 to (12.5 to true), 175 to (12.5 to true),
)

/** Trainer groups that matter enough to call out separately. */
internal val BOSS_GROUPS = setOf("Gym", "Elite4", "Boss", "Rival")

/** RouteData.EncounterArea's display names. */
internal val AREA_LABELS = mapOf(
    "LAND" to "Walking",
    "SURFING" to "Surfing",
    "UNDERWATER" to "Underwater",
    "STATIC" to "Static",
    "ROCKSMASH" to "RockSmash",
    "SUPERROD" to "Super Rod",
    "GOODROD" to "Good Rod",
    "OLDROD" to "Old Rod",
)

object Gen3Types {
    /** Gen 3 internal type ids — verified against the ROM (Bulbasaur reads 12,3 =
     *  Grass/Poison). 9 is the unused "Mystery" slot; Nat. Dex appends Fairy. */
    private val names = mapOf(
        0 to "Normal", 1 to "Fighting", 2 to "Flying", 3 to "Poison", 4 to "Ground",
        5 to "Rock", 6 to "Bug", 7 to "Ghost", 8 to "Steel", 9 to "Mystery",
        10 to "Fire", 11 to "Water", 12 to "Grass", 13 to "Electric", 14 to "Psychic",
        15 to "Ice", 16 to "Dragon", 17 to "Dark", 18 to "Fairy",
    )
    fun name(id: Int): String = names[id] ?: "T$id"

    /** The 17 real types, in tracker display order. Excludes the unused slot 9. */
    val ALL = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17)

    // Every non-neutral matchup in the Gen 3 chart, as attacker to
    // (defender -> multiplier). Anything absent is 1x.
    //
    // This is the hardcoded chart rather than the ROM's gTypeEffectiveness
    // table. Standard IronMON rulesets do not randomize type effectiveness, so
    // this is correct for them - but a preset that DOES randomize the chart
    // would make the coverage lines below wrong, and they would look right.
    private val chart: Map<Int, Map<Int, Double>> = mapOf(
        0 to mapOf(5 to 0.5, 8 to 0.5, 7 to 0.0),
        1 to mapOf(0 to 2.0, 5 to 2.0, 8 to 2.0, 15 to 2.0, 17 to 2.0,
                   2 to 0.5, 3 to 0.5, 6 to 0.5, 14 to 0.5, 18 to 0.5, 7 to 0.0),
        2 to mapOf(1 to 2.0, 6 to 2.0, 12 to 2.0, 5 to 0.5, 8 to 0.5, 13 to 0.5),
        3 to mapOf(12 to 2.0, 18 to 2.0, 3 to 0.5, 4 to 0.5, 5 to 0.5, 7 to 0.5, 8 to 0.0),
        4 to mapOf(3 to 2.0, 5 to 2.0, 8 to 2.0, 10 to 2.0, 13 to 2.0,
                   6 to 0.5, 12 to 0.5, 2 to 0.0),
        5 to mapOf(2 to 2.0, 6 to 2.0, 10 to 2.0, 15 to 2.0,
                   1 to 0.5, 4 to 0.5, 8 to 0.5),
        6 to mapOf(12 to 2.0, 14 to 2.0, 17 to 2.0,
                   1 to 0.5, 2 to 0.5, 3 to 0.5, 7 to 0.5, 8 to 0.5, 10 to 0.5, 18 to 0.5),
        7 to mapOf(14 to 2.0, 7 to 2.0, 17 to 0.5, 8 to 0.5, 0 to 0.0),
        8 to mapOf(5 to 2.0, 15 to 2.0, 18 to 2.0, 8 to 0.5, 10 to 0.5, 11 to 0.5, 13 to 0.5),
        10 to mapOf(6 to 2.0, 8 to 2.0, 12 to 2.0, 15 to 2.0,
                    5 to 0.5, 10 to 0.5, 11 to 0.5, 16 to 0.5),
        11 to mapOf(4 to 2.0, 5 to 2.0, 10 to 2.0, 11 to 0.5, 12 to 0.5, 16 to 0.5),
        12 to mapOf(4 to 2.0, 5 to 2.0, 11 to 2.0,
                    2 to 0.5, 3 to 0.5, 6 to 0.5, 8 to 0.5, 10 to 0.5, 12 to 0.5, 16 to 0.5),
        13 to mapOf(2 to 2.0, 11 to 2.0, 12 to 0.5, 13 to 0.5, 16 to 0.5, 4 to 0.0),
        14 to mapOf(1 to 2.0, 3 to 2.0, 8 to 0.5, 14 to 0.5, 17 to 0.0),
        15 to mapOf(2 to 2.0, 4 to 2.0, 12 to 2.0, 16 to 2.0,
                    8 to 0.5, 10 to 0.5, 11 to 0.5, 15 to 0.5),
        16 to mapOf(16 to 2.0, 8 to 0.5, 18 to 0.0),
        17 to mapOf(7 to 2.0, 14 to 2.0, 1 to 0.5, 8 to 0.5, 17 to 0.5, 18 to 0.5),
        // Nat. Dex Fairy (18), from the reference's MoveData chart. Without
        // this row a fairy move computed neutral against everything.
        18 to mapOf(1 to 2.0, 17 to 2.0, 16 to 2.0, 3 to 0.5, 8 to 0.5, 10 to 0.5),
    )

    /** Damage multiplier of [attack] against a single [defend] type. */
    fun effect(attack: Int, defend: Int): Double =
        chart[attack]?.get(defend) ?: 1.0

    /** Multiplier against a possibly dual-typed defender. */
    fun effect(attack: Int, t1: Int, t2: Int): Double =
        effect(attack, t1) * (if (t2 == t1) 1.0 else effect(attack, t2))
}

data class TrackerState(
    val partyCount: Int,
    val party: List<TrackedMon>,
    override val inBattle: Boolean,
    override val isWildBattle: Boolean,
    /**
     * The opposing team, one entry per Pokemon the trainer actually has, true
     * while it still has HP.
     *
     * The reference draws this as a row of pokeballs under the enemy's card
     * (Drawing.drawTrainerTeamPokeballs), greying out the ones that have
     * fainted, so you can see how many are left. Empty outside a battle and
     * for wild encounters, which have no team.
     */
    val enemyTeam: List<Boolean> = emptyList(),
    val enemy: EnemyInfo? = null,
    /** Player overworld tile coordinates, or null when unreadable. */
    val playerX: Int? = null,
    val playerY: Int? = null,
    /** Active battle weather ("RAIN", "SUN", "SANDSTORM", "HAIL"), null outside
     *  battle, when clear, or when the bits do not match a known mask. */
    val weather: String? = null,
    /** Gym badges as 8 bits, badge 1 in bit 0. */
    val badges: Int = 0,
    val badgeSet: String = "FRLG",
    /** Healing carried, as a percentage of the lead's max HP, and item count -
     *  the PC tracker's "Heals: 44% HP (7)" line. */
    val healPercent: Int = 0,
    val healCount: Int = 0,
    /** Current map id, or null when it cannot be read. */
    val mapId: Int? = null,
    /** The map's name, from the reference's RouteData. */
    val routeName: String? = null,
    /** Species that can be encountered here, from the same table. */
    val routeSpecies: List<Int> = emptyList(),
    /** Trainer ids stationed on this map, from the reference's route data. */
    val routeTrainers: List<Int> = emptyList(),
    /** How many of those are gym leaders, Elite 4 or bosses. */
    val routeBosses: Int = 0,
    /** Total steps walked this run, game stat 5. */
    val steps: Int = 0,
    /** True only in the map where the starter is chosen. */
    val inLab: Boolean = false,
    /** Ability revealed by a battle-script activation this tick:
     *  species to ability name. The reference's Tracker.TrackAbility. */
    val abilityRevealed: Pair<Int, String>? = null,
    /** null while the run is alive; otherwise how it ended. */
    val gameOver: GameOver? = null,
    /**
     * True when the party slots decoded into something impossible, which means
     * the addresses for this ROM are wrong. Better to say so than to print a
     * fictional Pokemon that looks authoritative.
     */
    val unreadable: Boolean = false,
    /**
     * What the tracker resolved for this ROM, shown when [unreadable] is set.
     * The old message said only "the addresses are wrong", which tells the
     * player nothing and tells a bug report even less.
     */
    val diagnostics: String = "",
) : RunView {
    override val enemySpeciesId: Int get() = enemy?.species ?: -1
    override val outcome: RunOutcome? get() = when (gameOver) {
        GameOver.WON -> RunOutcome.WON; GameOver.LOST -> RunOutcome.LOST; null -> null
    }
}

/** How a run ended, matching the PC tracker's own two outcomes. */
enum class GameOver { LOST, WON }

/**
 * Reads the game through [MemoryReader] and assembles [TrackerState]. Names and base
 * stats come from the ROM region and are cached per species/move: they cannot change
 * within a loaded ROM, and a New Run reloads the core, which drops this reader.
 */
class GbaTracker(
    private val memory: MemoryReader,
    internal val map: GameMap = GameMap.EMERALD_U,
) {
    /** GameOverScreen.LossConditions: which faint ends the run. The app sets it from its options. */
    @Volatile var lossCondition: LossCondition = LossCondition.LEAD

    /**
     * Whether species ids follow the expansion's extended numbering, and so
     * whether the bundled sprite pack may be indexed with them. Exposed
     * narrowly because the map itself is module-internal.
     */
    val expandedSpeciesIds: Boolean get() = map.expandedSpeciesIds

    private val speciesNameCache = HashMap<Int, String>()
    private val moveNameCache = HashMap<Int, String>()
    private val baseStatsCache = HashMap<Int, BaseStats>()
    private val abilityNameCache = HashMap<Int, String>()
    private val itemNameCache = HashMap<Int, String>()
    private val moveDataCache = HashMap<Int, IntArray>()
    private val learnsetCache = HashMap<Int, List<Pair<Int, Int>>>()

    // Enemy moves revealed this encounter; keyed off the enemy species so a new
    // encounter (or a switch) starts a fresh page.
    private var seenForSpecies = -1
    private val movesSeen = LinkedHashSet<Int>()

    init {
        if (map.namesFromLists) {
            loadList("/natdex/species.tsv", speciesNameCache)
            loadList("/natdex/moves.tsv", moveNameCache)
        }
    }

    private fun loadList(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) line.substring(0, tab).toIntOrNull()?.let {
                    into[it] = line.substring(tab + 1)
                }
            }
        }
    }

    fun speciesName(species: Int): String = speciesNameCache.getOrPut(species) {
        if (map.speciesNames == 0L) return@getOrPut "#$species"
        val b = memory.read(map.speciesNames + species.toLong() * 11, 11)
        if (b.isEmpty()) "#$species" else Gen3Text.decode(b).ifBlank { "#$species" }
    }

    fun moveName(move: Int): String = moveNameCache.getOrPut(move) {
        if (move == 0) return@getOrPut "-"
        if (map.moveNames == 0L) return@getOrPut "#$move"
        val b = memory.read(map.moveNames + move.toLong() * 13, 13)
        if (b.isEmpty()) "#$move" else Gen3Text.decode(b).ifBlank { "#$move" }
    }

    fun abilityName(id: Int): String = abilityNameCache.getOrPut(id) {
        if (id == 0) return@getOrPut "-"
        if (map.abilityNames == 0L) return@getOrPut "#$id"
        val b = memory.read(map.abilityNames + id.toLong() * map.abilityStride, map.abilityStride)
        if (b.isEmpty()) "#$id" else Gen3Text.decode(b).ifBlank { "#$id" }
    }

    fun itemName(id: Int): String = itemNameCache.getOrPut(id) {
        if (id == 0) return@getOrPut "-"
        if (map.itemNames == 0L) return@getOrPut "#$id"
        val b = memory.read(map.itemNames + id.toLong() * map.itemStride, 14)
        if (b.isEmpty()) "#$id" else Gen3Text.decode(b).ifBlank { "#$id" }
    }

    /** [power, type, accuracy, basePP] from the ROM move table, or null. */
    private fun moveData(move: Int): IntArray? {
        if (map.battleMoves == 0L || move == 0) return null
        return moveDataCache.getOrPut(move) {
            // gBattleMoves entry (pret BattleMove, 12-byte stride): effect,
            // power, type, accuracy, pp, secondaryEffectChance, target,
            // priority (s8), flags (bit 0 = makes contact). Verified against
            // the Nat. Dex ROM 2026-09-05: Quick Attack +1, Protect +3, Vital
            // Throw -1; Pound/Quick Attack flags 0x33, Thunder Wave 0x16.
            // Read live, like power and type, rather than from a static table.
            val b = memory.read(map.battleMoves + move.toLong() * 12, 9)
            if (b.size < 9) intArrayOf(-1, -1, -1, -1, 0, 0)
            else intArrayOf(b.u8(1), b.u8(2), b.u8(3), b.u8(4),
                b.u8(7).let { if (it >= 128) it - 256 else it }, b.u8(8))
        }.takeIf { it[0] >= 0 }
    }

    /** The panel's four move rows for any mon, from the ROM's move table. Public for the screenshot demo. */
    fun moveRowsOf(mon: PokemonDecoder.Mon): List<MoveRow> = moveRows(mon)
    /** The ability the mon's slot resolves to. Public for the screenshot demo. */
    fun abilityNameOf(mon: PokemonDecoder.Mon, base: BaseStats?): String = abilityOf(mon, base)

    /** The row for a move seen in an EARLIER battle: base PP, since the enemy's remaining PP is unknowable. */
    fun moveRowFor(id: Int): MoveRow? {
        if (id <= 0) return null
        val d = moveData(id) ?: return null
        return MoveRow(
            id = id, name = moveName(id), pp = d[3], ppMax = null, power = d[0], acc = d[2], type = d[1],
            category = gen3Category(d[1], d[0]), priority = d[4].takeIf { it in -7..7 }, contact = (d[5] and 1) != 0,
        )
    }

    private fun moveRows(mon: PokemonDecoder.Mon): List<MoveRow> =
        (0..3).mapNotNull { i ->
            val m = mon.moves[i]
            if (m == 0) null else {
                val d = moveData(m)
                val basePP = d?.get(3)
                // Gen 3 PP Up: each adds a fifth of the base, capped at 3 Ups.
                val ups = mon.ppUps.getOrElse(i) { 0 }.coerceIn(0, 3)
                val maxPP = basePP?.let { it + (it / 5) * ups }
                MoveRow(
                    id = m,
                    name = moveName(m),
                    pp = mon.pp[i],
                    ppMax = maxPP,
                    power = d?.get(0),
                    acc = d?.get(2),
                    type = d?.get(1),
                    category = d?.let { gen3Category(it[1], it[0]) },
                    priority = d?.get(4)?.takeIf { it in -7..7 },
                    contact = d?.let { (it[5] and 1) != 0 },
                )
            }
        }

    /** The rolled ability for a party mon: slot bit against the base-stats pair. */
    private fun abilityOf(mon: PokemonDecoder.Mon, base: BaseStats?): String {
        base ?: return "?"
        val id = if (mon.abilitySlot == 1 && base.ability2 != 0) base.ability2 else base.ability1
        return abilityName(id)
    }

    fun baseStats(species: Int): BaseStats? = baseStatsCache.getOrPut(species) {
        val stride = map.baseStatsStride
        val b = memory.read(map.baseStats + species.toLong() * stride, stride)
        if (b.size < stride) return@getOrPut BaseStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        BaseStats(
            hp = b.u8(0), atk = b.u8(1), def = b.u8(2),
            spe = b.u8(3), spAtk = b.u8(4), spDef = b.u8(5),
            type1 = b.u8(6), type2 = b.u8(7),
            ability1 = if (map.abilitiesAreU16) b.u16(0x16) else b.u8(22),
            ability2 = if (map.abilitiesAreU16) b.u16(0x18) else b.u8(23),
        )
    }.takeIf { it.bst > 0 }

    private val spriteCache = HashMap<Int, IntArray?>()

    /** 64x64 ARGB front sprite from the ROM, or null. Cached per species. */
    fun sprite(species: Int): IntArray? = spriteCache.getOrPut(species) {
        runCatching { SpriteDecoder.frontSprite(memory, map, species) }.getOrNull()
    }

    data class BallOption(val ball: String, val species: Int, val name: String)

    /** The three starter balls, read from the randomized ROM. Empty if unknown. */
    fun starters(): List<BallOption> {
        if (map.startersBase == 0L) return emptyList()
        fun sp(off: Int): Int {
            val b = memory.read(map.startersBase + off, 2)
            return if (b.size == 2) b.u16(0) else 0
        }
        val s1 = sp(0); val s2 = sp(map.starter2Off); val s3 = sp(map.starter3Off)
        if (s1 == 0) return emptyList()
        return listOf(
            BallOption("LEFT", s1, speciesName(s1)),
            BallOption("MIDDLE", s2, speciesName(s2)),
            BallOption("RIGHT", s3, speciesName(s3)),
        )
    }

    fun read(): TrackerState {
        val countBytes = memory.read(map.partyCount, 1)
        val count = if (countBytes.isEmpty()) 0 else countBytes.u8(0).coerceIn(0, 6)

        var unreadable = false
        val party = ArrayList<TrackedMon>(count)
        if (count > 0) {
            // Stride is the ROM's own struct size, not a constant: Nat. Dex
            // is 104 bytes, so a 100-byte stride walked into the middle of
            // every slot after the first.
            val monSize = map.monLayout.size
            val bytes = memory.read(map.party, count * monSize)
            if (bytes.size >= monSize) {
                for (i in 0 until minOf(count, bytes.size / monSize)) {
                    val slot = bytes.copyOfRange(i * monSize, (i + 1) * monSize)
                    if (PokemonDecoder.isEmpty(slot)) continue
                    val mon = PokemonDecoder.decode(slot, map.monLayout)
                    // A decode that lands on the wrong memory still returns a
                    // Mon - it just contains nonsense, and the panel renders it
                    // with total confidence: species #7444, "HP: 0/65285",
                    // "Lv.0", a type id of 54. Say the read is broken instead.
                    if (!sane(mon)) {
                        unreadable = true
                        continue
                    }
                    val base = baseStats(mon.species)
                    val learn = learnset(mon.species)
                    party += TrackedMon(
                        mon = mon,
                        speciesName = speciesName(mon.species),
                        moveNames = mon.moves.map { moveName(it) },
                        base = base,
                        abilityName = abilityOf(mon, base),
                        itemName = itemName(mon.heldItem),
                        moveRows = moveRows(mon),
                        movesLearned = learn.count { it.first <= mon.level },
                        movesTotal = learn.size,
                        nextMoveLevel = learn.firstOrNull { it.first > mon.level }?.first,
                        statusCondition = statusName(mon.status),
                    )
                }
            }
        }

        val rawMapId = if (map.mapHeader == 0L) null else {
            val m = memory.read(map.mapHeader + 0x12, 2)
            if (m.size < 2) null else m.u16(0)
        }
        // A map id must be seen on TWO consecutive polls before it is adopted.
        //
        // The header pointer is read mid-transition during map loads, and a
        // single poll of a different (or zero) id used to be believed at once
        // - the route panel is keyed on the id, so it reset, then reset again
        // when the real id came back. That is the encounter list appearing
        // "sometimes". A real change persists and is adopted one poll later,
        // which nobody can see; a one-poll blip never lands.
        if (rawMapId != stableMapId) {
            if (rawMapId == candidateMapId) {
                logRoute(rawMapId)
                stableMapId = rawMapId
            } else candidateMapId = rawMapId
        }
        val mapId = stableMapId

        val heals = readHeals(party.firstOrNull()?.mon?.maxHp ?: 0)

        val inBattle = updateBattleStatus()
        // In battle, battler 0's stage block belongs to the player's active
        // mon; the panel shows chevrons on both sides like the reference.
        if (inBattle && party.isNotEmpty()) {
            party[0] = party[0].copy(statStages = readStatStages(0))
        }
        val trainer = !isWildEncounter

        var px: Int? = null; var py: Int? = null
        if (map.saveBlock1Ptr != 0L) {
            val ptr = memory.read(map.saveBlock1Ptr, 4)
            if (ptr.size == 4) {
                val sb1 = ptr.u32(0)
                if (sb1 in 0x02000000L..0x0203FFFFL) {
                    val pos = memory.read(sb1, 4)
                    if (pos.size == 4) { px = pos.u16(0); py = pos.u16(2) }
                }
            }
        }

        return TrackerState(
            partyCount = count,
            party = party,
            inBattle = inBattle,
            isWildBattle = inBattle && !trainer,
            enemyTeam = if (inBattle && trainer) readEnemyTeam() else emptyList(),
            enemy = if (inBattle) readEnemy() else run { seenForSpecies = -1; movesSeen.clear(); null },
            abilityRevealed = if (inBattle) readAbilityTrigger() else null,
            playerX = px,
            playerY = py,
            weather = if (inBattle) readWeather() else null,
            badges = readBadges(),
            badgeSet = map.badgeSet,
            healPercent = heals.first,
            healCount = heals.second,
            mapId = mapId,
            inLab = mapId != null && mapId in map.labMapIds,
            routeName = mapId?.let { routeInfo(it)?.first },
            routeSpecies = mapId?.let { routeInfo(it)?.second } ?: emptyList(),
            routeTrainers = mapId?.let { trainersOnRoute(it) } ?: emptyList(),
            routeBosses = mapId?.let { m ->
                trainersOnRoute(m).count { trainerGroup(it) in BOSS_GROUPS }
            } ?: 0,
            steps = readGameStat(5),
            gameOver = readGameOver(party),
            diagnostics = "%s  party=%08X count=%08X base=%08X"
                .format(map.name, map.party, map.partyCount, map.baseStats),
            unreadable = unreadable && party.isEmpty(),
        )
    }

    /**
     * Does this decode describe a Pokemon that could actually exist?
     *
     * Every one of these is cheap and none of them can be true of a real party
     * slot. They exist because a wrong address does not fail - it returns bytes,
     * and those bytes decode into a confident, completely fictional Pokemon.
     */
    private fun sane(mon: PokemonDecoder.Mon): Boolean {
        if (mon.level !in 1..100) return false
        if (mon.maxHp !in 1..2000) return false
        if (mon.curHp < 0 || mon.curHp > mon.maxHp) return false
        val ceiling = if (map.spriteCount > 0) map.spriteCount else 412
        if (mon.species !in 1 until ceiling + 1) return false
        return true
    }

    /**
     * Map name and its wild encounter list, from the reference's RouteData.
     *
     * These tables are the reference's own, extracted from RouteData.lua rather
     * than rebuilt: 192 maps for FRLG and 191 for RSE, each with the species
     * that can appear there per encounter area. Randomizers rewrite WHICH
     * species appear, so the list is what the vanilla game offers - it is the
     * shape of the route, not a promise about this seed.
     */
    private val routes: Map<Int, Pair<String, List<Int>>> by lazy {
        val out = HashMap<Int, Pair<String, List<Int>>>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/routes-${map.routeTable}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 2) p[0].toIntOrNull()?.let { id ->
                        val mons = if (p.size >= 3 && p[2].isNotBlank()) {
                            p[2].split('|').flatMap { area ->
                                area.substringAfter(':', "").split(',')
                                    .mapNotNull { it.trim().toIntOrNull() }
                            }.distinct()
                        } else emptyList()
                        out[id] = p[1] to mons
                    }
                }
            }
        out
    }

    fun routeInfo(mapId: Int): Pair<String, List<Int>>? = routes[mapId]

    /**
     * The route's encounters split BY AREA, which is how the route info screen
     * lists them: walking, surfing, each rod separately. The flat list above is
     * the same data collapsed, for the one-line carousel version.
     *
     * Area keys are the reference's own EncounterArea names.
     */
    private val routeAreas: Map<Int, Map<String, List<Int>>> by lazy {
        val out = HashMap<Int, Map<String, List<Int>>>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/routes-${map.routeTable}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 3 && p[2].isNotBlank()) {
                        p[0].toIntOrNull()?.let { id ->
                            val areas = LinkedHashMap<String, List<Int>>()
                            p[2].split('|').forEach { chunk ->
                                val key = chunk.substringBefore(':')
                                val mons = chunk.substringAfter(':', "").split(',')
                                    .mapNotNull { it.trim().toIntOrNull() }
                                if (key.isNotBlank() && mons.isNotEmpty()) {
                                    areas[AREA_LABELS[key] ?: key] = mons
                                }
                            }
                            if (areas.isNotEmpty()) out[id] = areas
                        }
                    }
                }
            }
        out
    }

    fun routeEncounterAreas(mapId: Int): Map<String, List<Int>> =
        routeAreas[mapId] ?: emptyMap()

    /**
     * Trainer classes and the trainers stationed on each map, ported from
     * TrainerData.lua and the per-game TrainerRouteData files: 653 trainers
     * and 85 mapped routes for FRLG, 855 and 90 for Emerald.
     */
    private val trainerClass: Map<Int, Pair<String, String>> by lazy {
        val out = HashMap<Int, Pair<String, String>>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/trainers-${map.routeTable}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 3) p[0].toIntOrNull()?.let { out[it] = p[1] to p[2] }
                }
            }
        out
    }

    private val routeTrainerIds: Map<Int, List<Int>> by lazy {
        val out = HashMap<Int, List<Int>>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/trainerroutes-${map.routeTable}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 2) p[0].toIntOrNull()?.let { id ->
                        out[id] = p[1].split(',').mapNotNull { it.trim().toIntOrNull() }
                    }
                }
            }
        out
    }

    fun trainersOnRoute(mapId: Int): List<Int> = routeTrainerIds[mapId] ?: emptyList()

    /** "Gym", "Elite4", "Rival", "Boss" or "Other". */
    fun trainerGroup(trainerId: Int): String = trainerClass[trainerId]?.second ?: "Other"

    fun trainerClassName(trainerId: Int): String? = trainerClass[trainerId]?.first

    /**
     * Move and ability descriptions, from the reference's English resources.
     *
     * 354 moves and 77 abilities - Gen 3's real counts, which is the check that
     * the extraction got all of them rather than stopping early.
     *
     * Abilities carry an Emerald-specific override for the handful whose
     * behaviour differs there; it is used only on Emerald, the way
     * AbilityData does it.
     */
    private fun loadDesc(resource: String): Map<Int, Triple<String, String, String>> {
        val out = HashMap<Int, Triple<String, String, String>>()
        javaClass.getResourceAsStream(resource)
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 3) p[0].toIntOrNull()?.let {
                        out[it] = Triple(p[1], p[2], if (p.size >= 4) p[3] else "")
                    }
                }
            }
        return out
    }

    /**
     * Evolution method and weight per species, from PokemonData.lua - 411
     * entries, Gen 3's internal species count, indexed the same way this
     * tracker indexes everything else.
     *
     * Evolution is the reference's own string: a bare number means "at that
     * level", anything else names the method (STONE, FRIENDSHIP, TRADE...).
     */
    private val speciesExtra: Map<Int, Triple<String, String, String>> by lazy {
        val out = HashMap<Int, Triple<String, String, String>>()
        javaClass.getResourceAsStream("/gen3/species-extra.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 4) p[0].toIntOrNull()?.let {
                        out[it] = Triple(p[1], p[2], p[3])
                    }
                }
            }
        out
    }

    /** Evolution method, or null when this species does not evolve. */
    fun evolution(species: Int): String? =
        speciesExtra[species]?.second?.takeIf { it.isNotBlank() }

    /** Weight in kg, as the reference records it. */
    fun weight(species: Int): String? =
        speciesExtra[species]?.third?.takeIf { it.isNotBlank() }

    /**
     * What hits this species hard, computed from the live types and the shared
     * chart - the reference's PokemonData.getEffectiveness.
     *
     * Returns the multiplier for every attacking type that is not neutral, so
     * the info screen can show both what to fear and what it shrugs off.
     */
    fun effectivenessAgainst(species: Int): Map<Double, List<String>> {
        val b = baseStats(species) ?: return emptyMap()
        val out = sortedMapOf<Double, MutableList<String>>(compareByDescending { it })
        for (atk in Gen3Types.ALL) {
            val e = Gen3Types.effect(atk, b.type1, b.type2)
            if (e != 1.0) out.getOrPut(e) { mutableListOf() }.add(Gen3Types.name(atk))
        }
        return out
    }

    private val moveDescs by lazy { loadDesc("/gen3/movedesc.tsv") }
    private val abilityDescs by lazy { loadDesc("/gen3/abilitydesc.tsv") }

    fun moveDescription(moveId: Int): String? =
        moveDescs[moveId]?.second?.takeIf { it.isNotBlank() }

    fun abilityDescription(abilityId: Int): String? {
        val d = abilityDescs[abilityId] ?: return null
        val emeraldOverride = d.third.takeIf { it.isNotBlank() && map.routeTable == "rse" }
        val base = d.second.takeIf { it.isNotBlank() } ?: return emeraldOverride
        return if (emeraldOverride != null) "$base  ($emeraldOverride)" else base
    }

    /** Ability id for a name, so the panel can look up what it is showing. */
    fun abilityIdOf(name: String): Int? =
        abilityDescs.entries.firstOrNull { it.value.first.equals(name, true) }?.key

    /**
     * Coverage: how many species in this game each of your move types can hit,
     * bucketed by the BEST multiplier your moveset achieves against them.
     *
     * This is the PC tracker's Coverage Calculator. It walks every species,
     * takes the highest effectiveness any one of your damaging move types gets
     * against that species' type pair, and files it under 0x, quarter, half,
     * neutral, super or quad. It answers the question that actually matters
     * mid-run: "is there anything my team simply cannot hurt?"
     *
     * Types come from the live base-stat table, so a randomized type chart is
     * followed - but the effectiveness chart itself is the standard one.
     */
    fun coverage(moveTypes: List<Int>): Map<Double, List<Int>> {
        val out = linkedMapOf(
            0.0 to ArrayList<Int>(), 0.25 to ArrayList(), 0.5 to ArrayList(),
            1.0 to ArrayList(), 2.0 to ArrayList(), 4.0 to ArrayList(),
        )
        if (moveTypes.isEmpty()) return out
        val last = if (map.spriteCount > 0) map.spriteCount - 1 else 411
        for (id in 1..last) {
            // The Gen 3 species table has a block of unused slots between the
            // Johto dex and the Hoenn dex; they decode as garbage types.
            if (id in 252..276) continue
            val b = baseStats(id) ?: continue
            if (b.bst == 0) continue
            var best = 0.0
            for (t in moveTypes) {
                val e = Gen3Types.effect(t, b.type1, b.type2)
                if (e > best) best = e
            }
            // Shedinja: Wonder Guard blocks anything under 2x, so unless a
            // move is super effective it counts as unhittable - the
            // reference's CoverageCalcScreen rule, which raw type math misses.
            if (id == 303 && best < 2.0) best = 0.0
            out[best]?.add(id)
        }
        return out
    }

    /**
     * Whether the run is over, and how.
     *
     * The loss condition is the PC tracker's default: the LEAD Pokemon fainting
     * ends the run. Not the whole party - in IronMON the lead is the run, and
     * waiting for a wipe would call it over long after it actually was.
     *
     * A win is beating the final trainer, which is a different question from
     * surviving: gBattleOutcome reads 1 for a win, and the opponent has to be
     * the champion. FRLG has three champion ids because the team depends on
     * which starter you took.
     */
    private fun readGameOver(party: List<TrackedMon>): GameOver? {
        val lead = party.firstOrNull() ?: return null
        if (map.battleOutcome != 0L && map.trainerOpponent != 0L) {
            val outcome = memory.read(map.battleOutcome, 1)
            val opp = memory.read(map.trainerOpponent, 2)
            if (outcome.isNotEmpty() && outcome.u8(0) == 1 && opp.size == 2 &&
                opp.u16(0) in map.finalTrainers
            ) return GameOver.WON
        }
        // Level 0 means the slot has not been decoded yet, not a dead Pokemon; LossCondition guards it.
        if (lossCondition.lost(party.map { it.mon.level to it.mon.curHp })) return GameOver.LOST
        return null
    }

    /**
     * One of the game's own statistics, XOR-decrypted.
     *
     * Stats live in SaveBlock1 as u32s and are encrypted with the SAME security
     * key as the bag - but the full 32 bits of it, where item quantities use
     * only the low 16. Reading them with the 16-bit key gives a number that is
     * wrong in its high half and still looks like a plausible step count.
     *
     * Index 5 is STEPS (Constants.GAME_STATS.STEPS).
     */
    fun readGameStat(index: Int): Int {
        if (map.gameStatsOffset == 0L || map.saveBlock1Ptr == 0L) return 0
        val ptr = memory.read(map.saveBlock1Ptr, 4)
        if (ptr.size != 4) return 0
        val sb1 = ptr.u32(0)
        if (sb1 !in 0x02000000L..0x0203FFFFL) return 0
        val raw = memory.read(sb1 + map.gameStatsOffset + index * 4L, 4)
        if (raw.size < 4) return 0
        val key = readSecurityKey32()
        val v = (raw.u32(0) xor key) and 0xFFFFFFFFL
        // A stat past a few million is a failed read, not a long playthrough.
        return if (v > 90_000_000L) 0 else v.toInt()
    }

    /** The full 32-bit security key. Item quantities use only its low half. */
    private fun readSecurityKey32(): Long {
        if (map.encryptionKeyOffset == 0L || map.saveBlock2Ptr == 0L) return 0
        val p = memory.read(map.saveBlock2Ptr, 4)
        if (p.size != 4) return 0
        val sb2 = p.u32(0)
        if (sb2 !in 0x02000000L..0x0203FFFFL) return 0
        val k = memory.read(sb2 + map.encryptionKeyOffset, 4)
        return if (k.size < 4) 0 else k.u32(0)
    }

    /**
     * The bag's security key, from SaveBlock2.
     *
     * Gen 3 stores every item quantity XORed with this, so a zero key reads
     * every quantity as ciphertext. An empty slot stores 0, i.e. the key
     * itself, which is what makes a wrong key detectable at all.
     */
    fun readSecurityKey(): Int {
        if (map.encryptionKeyOffset == 0L || map.saveBlock2Ptr == 0L) return 0
        val p = memory.read(map.saveBlock2Ptr, 4)
        if (p.size != 4) return 0
        val sb2 = p.u32(0)
        if (sb2 !in 0x02000000L..0x0203FFFFL) return 0
        val k = memory.read(sb2 + map.encryptionKeyOffset, 2)
        return if (k.size < 2) 0 else k.u16(0)
    }

    /**
     * Level-up learnset for a species: pairs of (level, move), in order.
     *
     * gLevelUpLearnsets holds one ROM pointer per species to a list of u16
     * entries terminated by 0xFFFF, with the move in bits 0-8 and the level in
     * bits 9-15. Read live because randomizers rewrite it.
     */
    fun learnset(species: Int): List<Pair<Int, Int>> = learnsetCache.getOrPut(species) {
        if (map.levelUpLearnsets == 0L) return@getOrPut emptyList()
        val ptrBytes = memory.read(map.levelUpLearnsets + species.toLong() * 4, 4)
        if (ptrBytes.size < 4) return@getOrPut emptyList()
        val addr = ptrBytes.u32(0)
        if (addr !in 0x08000000L..0x09FFFFFFL) return@getOrPut emptyList()
        val out = ArrayList<Pair<Int, Int>>(24)
        if (map.learnsetWide) {
            // Expansion format: {u16 move, u16 level} per entry, 0xFFFF ends.
            for (i in 0 until 40) {
                val e = memory.read(addr + i * 4, 4)
                if (e.size < 4) break
                val move = e.u16(0)
                val level = e.u16(2)
                if (move == 0xFFFF || move == 0) break
                if (level > 100) break
                out += level to move
            }
        } else {
            // Vanilla packing: move in bits 0-8, level in 9-15, 0xFFFF ends.
            for (i in 0 until 32) {
                val e = memory.read(addr + i * 2, 2)
                if (e.size < 2) break
                val v = e.u16(0)
                if (v == 0xFFFF) break
                val move = v and 0x1FF
                val level = (v shr 9) and 0x7F
                if (move == 0) break
                out += level to move
            }
        }
        out
    }

    /**
     * Healing carried in the Items pocket, as a percentage of [maxHp].
     *
     * Gen 3 XORs item quantities with a security key held in SaveBlock2, which is
     * why a naive read shows nonsense counts. Percentage-healing items (Full
     * Restore, berries) contribute their share of max HP; constant ones their
     * flat value, capped at a full heal each.
     */
    private fun readHeals(maxHp: Int): Pair<Int, Int> {
        if (map.bagItemsOffset == 0L || map.saveBlock1Ptr == 0L || maxHp <= 0) {
            return 0 to 0
        }
        val sb1Ptr = memory.read(map.saveBlock1Ptr, 4)
        if (sb1Ptr.size != 4) return 0 to 0
        val sb1 = sb1Ptr.u32(0)
        if (sb1 !in 0x02000000L..0x0203FFFFL) return 0 to 0

        // The security key is in SaveBlock2 in both games (FireRed +0xF20,
        // Emerald +0xAC), NOT in SaveBlock1 where the bag itself lives. Reading
        // it from the wrong block yields 0, which silently leaves every quantity
        // encrypted - and an empty bag looks identical either way, so this is
        // asserted below rather than eyeballed.
        val key = readSecurityKey()

        var total = 0.0
        var count = 0
        // Items and Berries are two different pockets; both hold healing.
        val pockets = listOf(
            map.bagItemsOffset to map.bagItemsSlots,
            map.bagBerriesOffset to map.bagBerriesSlots,
        )
        for ((offset, slots) in pockets) {
            if (offset == 0L) continue
            for (slot in 0 until slots) {
                val b = memory.read(sb1 + offset + slot * 4L, 4)
                if (b.size < 4) break
                val id = b.u16(0)
                if (id == 0) continue
                val qty = b.u16(2) xor key
                if (qty <= 0 || qty > 999) continue
                val heal = HEAL_ITEMS[id] ?: continue
                val perItem = if (heal.second) maxHp * heal.first / 100.0 else heal.first
                total += minOf(perItem, maxHp.toDouble()) * qty
                count += qty
            }
        }
        val pct = ((total / maxHp) * 100).toInt().coerceIn(0, 9999)
        return pct to count
    }

    /**
     * The eight gym badges as bits, badge 1 in bit 0.
     *
     * FireRed keeps them in a byte; Emerald packs them into a word starting at
     * bit 7, which is why this is not simply "read a byte" for both.
     */
    private fun readBadges(): Int {
        if (map.badgeOffset == 0L || map.saveBlock1Ptr == 0L) return 0
        val ptr = memory.read(map.saveBlock1Ptr, 4)
        if (ptr.size != 4) return 0
        val sb1 = ptr.u32(0)
        if (sb1 !in 0x02000000L..0x0203FFFFL) return 0
        return if (map.badgeIsWord) {
            val b = memory.read(sb1 + map.badgeOffset, 2)
            if (b.size < 2) 0 else (b.u16(0) shr 7) and 0xFF
        } else {
            val b = memory.read(sb1 + map.badgeOffset, 1)
            if (b.isEmpty()) 0 else b.u8(0)
        }
    }

    /** Gen 3 weather bitmasks. Unknown bits mean "don't show" — that gates a wrong
     *  address into silence instead of garbage. */
    private fun readWeather(): String? {
        if (map.weather == 0L) return null
        val b = memory.read(map.weather, 2)
        if (b.size < 2) return null
        val w = b.u16(0)
        if (w == 0 || w > 0xFF) return null   // unknown bits: show nothing
        return when {
            (w and 0x07) != 0 -> "RAIN"
            (w and 0x18) != 0 -> "SANDSTORM"
            (w and 0x60) != 0 -> "SUN"
            (w and 0x80) != 0 -> "HAIL"
            else -> null
        }
    }

    /** Battler 1 = the opponent in singles. Pret BattlePokemon offsets. */
    // Battle screen state, latched exactly as the reference latches it.
    // Map id debounce (see read()) and a transition log for diagnosing the
    // route panel without adb: the last 300 adopted ids, with timestamps.
    private var stableMapId: Int? = null
    private var candidateMapId: Int? = null
    private val routeLog = ArrayDeque<String>()
    private fun logRoute(id: Int?) {
        val name = id?.let { routeInfo(it)?.first } ?: "-"
        val n = id?.let { routeInfo(it)?.second?.size } ?: 0
        routeLog.addLast("%d map=%s route=%s wild=%d".format(
            System.currentTimeMillis(), id?.toString() ?: "null", name, n))
        while (routeLog.size > 300) routeLog.removeFirst()
    }
    /** Adopted map-id transitions, oldest first. For the ROUTE LOG button. */
    fun routeLogSnapshot(): List<String> = routeLog.toList()

    private var inBattleScreen = false
    private var battleDataReady = false
    private var isWildEncounter = false

    /**
     * True only while the battle's action menu is open in a WILD battle, read
     * LIVE from memory at the moment of asking.
     *
     * The auto-flee used to gate on the last polled TrackerState, which lags
     * the game by one poll (250-700 ms). Mash B in that window after a battle
     * ends and the synthetic RIGHT/DOWN/A still fires into the overworld,
     * walking the player. gBattleMainFunc == HandleTurnActionSelectionState is
     * the reference's own definition of "choosing an action", and it is false
     * the instant the battle leaves that state, so nothing can leak.
     */
    fun isChoosingActionInWild(): Boolean {
        if (!inBattleScreen || !isWildEncounter) return false
        if (map.battleMainFunc == 0L || map.handleTurnAction == 0L) return false
        val f = memory.read(map.battleMainFunc, 4)
        if (f.size != 4 || f.u32(0) != map.handleTurnAction) return false
        if (map.battleOutcome != 0L) {
            val o = memory.read(map.battleOutcome, 1)
            if (o.isNotEmpty() && o.u8(0) != 0) return false
        }
        return true
    }

    /** A species id that this game could actually have. */
    private fun speciesIsValid(id: Int): Boolean {
        val ceiling = if (map.spriteCount > 0) map.spriteCount else 412
        return id in 1..ceiling
    }

    /**
     * Port of Battle.updateBattleStatus (reference Battle.lua:143).
     *
     * Three signals, because no single one of them is trustworthy:
     *
     *  - gBattlersCount survives the end of a battle, so on its own it pins
     *    the enemy card on screen permanently. That was the bug.
     *  - gBattleMons[0] can hold a stale or garbage species; the reference
     *    calls that a "fake battle" and refuses to start on it. This is what
     *    rejects an impossible species id rather than rendering it.
     *  - gBattleOutcome is 0 only while a battle is live. It is the actual
     *    exit signal, and its own reference comment says it is not cleared
     *    afterwards - so it is only meaningful in the 0 / not-0 sense.
     *
     * gBattleMainFunc then says when battle data may be read and when
     * teardown is safe. When those four symbols are unknown for a game the
     * timing gate is skipped rather than failing shut, because a missing
     * symbol must not mean "the tracker never shows a battle".
     */
    private fun updateBattleStatus(): Boolean {
        val battlers = memory.read(map.battlersCount, 1)
        val numBattlers = if (battlers.isNotEmpty()) battlers.u8(0) else 0
        val firstMon = memory.read(map.battleMons, 2)
        val firstMonId = if (firstMon.size == 2) firstMon.u16(0) else 0
        val fakeBattle = numBattlers == 0 || !speciesIsValid(firstMonId)

        val outcomeByte =
            if (map.battleOutcome == 0L) ByteArray(0)
            else memory.read(map.battleOutcome, 1)
        // Unknown outcome address: treat as live, so the older behaviour
        // stands rather than the battle panel never appearing.
        val outcome = if (outcomeByte.isEmpty()) 0 else outcomeByte.u8(0)
        val statusActive = outcome == 0 && !fakeBattle

        val funcBytes =
            if (map.battleMainFunc == 0L) ByteArray(0)
            else memory.read(map.battleMainFunc, 4)
        val mainFunc = if (funcBytes.size == 4) funcBytes.u32(0) else 0L
        // An UNREADABLE gBattleMainFunc is not the same as one reading zero.
        // Zero is a real "between battles" value; a failed read means no
        // timing information at all, and must skip the gate rather than hold
        // the battle panel shut forever - failing shut here would trade a
        // stuck-on card for a never-appears card.
        val haveTiming = map.returnToOverworld != 0L && map.handleTurnAction != 0L &&
            funcBytes.size == 4
        val atDataStart = !haveTiming || mainFunc == map.handleTurnAction ||
            mainFunc == (if (isWildEncounter) map.introDrawPartySummary
                         else map.introOpponentSendsOut)
        val atDataEnd = !haveTiming || mainFunc == 0L ||
            mainFunc == map.returnToOverworld

        if (!inBattleScreen && statusActive) {
            // Captured once, here: gBattleTypeFlags is stale after a battle
            // like everything else, so re-reading it every poll would let the
            // wild/trainer label drift. BATTLE_TYPE_TRAINER is bit 3.
            val fb = memory.read(map.battleTypeFlags, 4)
            val flags = if (fb.size == 4) fb.u32(0) else 0L
            isWildEncounter = (flags and 0x8L) == 0L
            inBattleScreen = true
            battleDataReady = false
            seenForSpecies = -1
            movesSeen.clear()
        } else if (inBattleScreen && !battleDataReady && atDataStart) {
            battleDataReady = true
        } else if (inBattleScreen && !statusActive && atDataEnd) {
            inBattleScreen = false
            battleDataReady = false
            isWildEncounter = false
            seenForSpecies = -1
            movesSeen.clear()
        }
        return inBattleScreen && battleDataReady
    }

    /**
     * Which of the opposing party's slots are filled, and which still stand.
     *
     * Read from gEnemyParty with the same stride and sanity rules as the
     * player's, so a wrong map cannot turn into a row of phantom pokeballs.
     */
    private fun readEnemyTeam(): List<Boolean> {
        if (map.enemyParty == 0L) return emptyList()
        val monSize = map.monLayout.size
        val bytes = memory.read(map.enemyParty, 6 * monSize)
        if (bytes.size < monSize) return emptyList()
        val out = ArrayList<Boolean>(6)
        for (i in 0 until minOf(6, bytes.size / monSize)) {
            val slot = bytes.copyOfRange(i * monSize, (i + 1) * monSize)
            if (PokemonDecoder.isEmpty(slot)) continue
            val mon = PokemonDecoder.decode(slot, map.monLayout)
            if (!sane(mon)) continue
            out += mon.curHp > 0
        }
        return out
    }

    private fun readEnemy(): EnemyInfo? {
        val stride = map.battleMonSize
        val b = memory.read(map.battleMons + stride, stride)
        if (b.size < stride) return null
        val species = b.u16(0x00)
        // Not just "not zero": a stale battle struct holds values far outside
        // any species range, and rendering one puts an impossible number and
        // an impossible HP on the card.
        if (!speciesIsValid(species)) return null

        if (species != seenForSpecies) { seenForSpecies = species; movesSeen.clear() }
        if (map.battleResults != 0L) {
            val lu = memory.read(map.battleResults + 0x24, 2)
            if (lu.size == 2) {
                val move = lu.u16(0)
                if (move in 1..2000) movesSeen.add(move)
            }
        }

        val base = baseStats(species)
        // 0x20 is the ability the enemy ACTUALLY has. It is kept internal:
        // the reference shows an enemy ability only after a battle script
        // reveals it activating, and the panel follows the same rule.
        val abilityId = b.u8(0x20)
        val possible = base?.let {
            if (it.ability2 != 0 && it.ability2 != it.ability1)
                abilityName(it.ability1) + " / " + abilityName(it.ability2)
            else abilityName(it.ability1)
        } ?: "?"
        return EnemyInfo(
            species = species,
            speciesName = speciesName(species),
            level = b.u8(0x2A),
            curHp = b.u16(0x28),
            maxHp = b.u16(0x2C),
            type1 = b.u8(0x21),
            type2 = b.u8(0x22),
            base = base,
            movesSeen = movesSeen.map { moveName(it) },
            moveRows = movesSeen.map { id ->
                val d = moveData(id)
                MoveRow(
                    id = id,
                    name = moveName(id),
                    pp = d?.get(3) ?: 0,
                    ppMax = null,          // an enemy's used PP is not readable
                    power = d?.get(0),
                    acc = d?.get(2),
                    type = d?.get(1),
                    category = d?.let { gen3Category(it[1], it[0]) },
                    priority = d?.get(4)?.takeIf { it in -7..7 },
                    contact = d?.let { (it[5] and 1) != 0 },
                )
            },
            abilityGuess = possible,
            abilityId = abilityId,
            statStages = readStatStages(1),
            // From the enemy PARTY slot, not from gBattleMons. See enemyPartyStatus.
            statusCondition = enemyPartyStatus(species, b.u8(0x2A), b.u16(0x28))
                ?.let(::statusName) ?: "",
        )
    }

    /**
     * The opponent's status condition, read the way the reference reads it.
     *
     * The PC tracker never reads status out of gBattleMons; it takes it from
     * the party struct (Tracker.getPokemon(1, false)), whose status offset is
     * part of the layout the ROM publishes and we already decode with. This
     * tracker used to read gBattleMons + 0x4C instead - a vanilla offset
     * inside a struct Nat. Dex GREW by four bytes somewhere after 0x22. Where
     * exactly is not published, so that read may land on a different field,
     * and sleep is the low three bits of the word: any nonzero garbage there
     * renders as SLP. That is the "[SLP] on first encounter" bug, and why it
     * was only ever SLP.
     *
     * The active battler is matched to its party slot by species and level,
     * then HP as a tiebreak. No match means no status shown - blank is never
     * wrong, a fabricated condition is.
     */
    private fun enemyPartyStatus(species: Int, level: Int, curHp: Int): Long? {
        if (map.enemyParty == 0L) return null
        val monSize = map.monLayout.size
        val bytes = memory.read(map.enemyParty, 6 * monSize)
        if (bytes.size < monSize) return null
        var best: PokemonDecoder.Mon? = null
        for (i in 0 until minOf(6, bytes.size / monSize)) {
            val slot = bytes.copyOfRange(i * monSize, (i + 1) * monSize)
            if (PokemonDecoder.isEmpty(slot)) continue
            val mon = PokemonDecoder.decode(slot, map.monLayout)
            if (!sane(mon) || mon.species != species || mon.level != level) continue
            if (best == null || mon.curHp == curHp) best = mon
            if (mon.curHp == curHp) break
        }
        return best?.status
    }

    // ------------------------------------------------------------ battle extras

    /**
     * Gen 3 status1 word: sleep turns in bits 0-2, then PSN/BRN/FRZ/PAR and
     * bit 7 toxic - identical layout to the party struct's word at +0x50.
     */
    internal fun statusName(status: Long): String = when {
        status and 0x07L != 0L -> "SLP"
        status and 0x08L != 0L -> "PSN"
        status and 0x10L != 0L -> "BRN"
        status and 0x20L != 0L -> "FRZ"
        status and 0x40L != 0L -> "PAR"
        status and 0x80L != 0L -> "PSN"
        else -> ""
    }

    /**
     * The 8 stage bytes at gBattleMons + battler*0x58 + 0x18: HP, ATK, DEF,
     * SPE, then SPA, SPD, ACC, EVA, 6 = neutral. The reference treats an HP
     * stage of 0 as "block not live yet" and reads everything as neutral
     * (Battle.updateStatStages) - ported as returning empty, which the panel
     * renders the same way.
     */
    internal fun readStatStages(battler: Int): Map<String, Int> {
        if (map.battleMons == 0L) return emptyMap()
        val b = memory.read(map.battleMons + battler * map.battleMonSize + 0x18L, 8)
        if (b.size < 8) return emptyMap()
        if (b[0].toInt() == 0) return emptyMap()
        val names = listOf("HP", "ATK", "DEF", "SPE", "SPA", "SPD", "ACC", "EVA")
        val stages = names.mapIndexed { i, n -> n to (b[i].toInt() and 0xFF) }.toMap()
        return if (stages.values.all { it in 0..12 }) stages else emptyMap()
    }

    /**
     * The ability battle-script table for this game: script address to the
     * (trigger, abilityIds, scope) rows anchored there. Vanilla tables are
     * the reference's AbilityAddresses data; the Nat. Dex ROM publishes the
     * same table through pointer slots, resolved here the way the extension
     * itself builds GS.ABILITIES.
     */
    private val abilityScripts: Map<Long, List<Triple<String, Set<Int>, String>>> by lazy {
        val out = HashMap<Long, MutableList<Triple<String, Set<Int>, String>>>()
        if (map.abilityScriptTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream(
            "/gen3/abilityscripts-" + map.abilityScriptTable + ".tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val f = line.split('\t')
                    if (map.abilityScriptTable == "natdex") {
                        if (f.size < 4) return@forEach
                        val slot = f[0].toLongOrNull(16) ?: return@forEach
                        val off = f[1].toLongOrNull(16) ?: return@forEach
                        val ptrBytes = memory.read(0x08000000L + slot, 4)
                        if (ptrBytes.size < 4) return@forEach
                        val addr = ptrBytes.u32(0) + off
                        val ids = f[3].split(',')
                            .mapNotNull { it.trim().toIntOrNull() }.toSet()
                        if (ids.isNotEmpty()) out.getOrPut(addr) { mutableListOf() }
                            .add(Triple(f[2], ids, f.getOrNull(4) ?: ""))
                    } else {
                        if (f.size < 3) return@forEach
                        val addr = f[0].toLongOrNull(16) ?: return@forEach
                        val ids = f[2].split(',')
                            .mapNotNull { it.trim().toIntOrNull() }.toSet()
                        if (ids.isNotEmpty()) out.getOrPut(addr) { mutableListOf() }
                            .add(Triple(f[1], ids, f.getOrNull(3) ?: ""))
                    }
                }
            }
        out
    }

    /**
     * Whether the current battle script is one that reveals an ability, and
     * whose. The singles paths of Battle.checkAbilitiesToTrack, using each
     * battler's LIVE ability from gBattleMons +0x20 (switch-proof, unlike
     * indexing the party). NOT ported: the Synchronize turn-chain and the
     * Levitate gBattleCommunication check - both need cross-poll bookkeeping
     * the reference itself flags as fragile.
     */
    internal fun readAbilityTrigger(): Pair<Int, String>? {
        if (map.scriptCurrInstr == 0L || abilityScripts.isEmpty()) return null
        val msgB = memory.read(map.scriptCurrInstr, 4)
        if (msgB.size < 4) return null
        val rows = abilityScripts[msgB.u32(0)] ?: return null

        val nB = memory.read(map.battlersCount, 1)
        val n = (if (nB.isEmpty()) 0 else nB[0].toInt() and 0xFF)
            .coerceIn(1, 4)
        fun battlerByte(addr: Long): Int {
            val b = memory.read(addr, 1)
            return if (b.isEmpty()) 0 else (b[0].toInt() and 0xFF) % n
        }
        val battler = battlerByte(map.scriptingBattler)
        val attacker = battlerByte(map.battlerAttacker)
        val target = battlerByte(map.battlerTarget)

        fun abilityOf(i: Int): Int {
            val b = memory.read(map.battleMons + i * map.battleMonSize + 0x20L, 1)
            return if (b.isEmpty()) 0 else b[0].toInt() and 0xFF
        }
        fun speciesOf(i: Int): Int {
            val b = memory.read(map.battleMons + i * map.battleMonSize.toLong(), 2)
            return if (b.size < 2) 0 else b.u16(0)
        }
        fun reveal(i: Int): Pair<Int, String>? {
            val sp = speciesOf(i); val ab = abilityOf(i)
            return if (sp != 0 && ab != 0) sp to abilityName(ab) else null
        }

        for ((trigger, ids, scope) in rows) {
            when (trigger) {
                "BATTLER" -> if (abilityOf(battler) in ids) {
                    // Trace: the traced ability belongs to the OTHER side.
                    return if (abilityOf(battler) == 36 && n == 2)
                        reveal(1 - battler) else reveal(battler)
                }
                "REVERSE_BATTLER" -> if (abilityOf(target) in ids) return reveal(target)
                "ATTACKER" -> if (abilityOf(target) in ids) return reveal(target)
                "REVERSE_ATTACKER" -> if (abilityOf(attacker) in ids) return reveal(attacker)
                "STATUS_INFLICT" -> if (abilityOf(battler) in ids &&
                    battler == target) return reveal(battler)
                "BATTLE_TARGET" -> when (scope) {
                    "other" -> if (abilityOf(attacker) in ids) return reveal(attacker)
                    "both" -> for (i in 0 until minOf(n, 2)) {
                        if (abilityOf(i) in ids) return reveal(i)
                    }
                    else -> if (abilityOf(target) in ids) return reveal(target)
                }
            }
        }
        return null
    }
}
