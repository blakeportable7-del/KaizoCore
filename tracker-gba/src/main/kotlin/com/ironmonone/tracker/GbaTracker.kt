package com.ironmonone.tracker

/**
 * One gTrainers entry and its party, as Program.readTrainerGameData reads
 * them: the Program.Addresses sizes and offsets (Program.lua:66-99). The Nat.
 * Dex ROM publishes its own (NatDexExtension.lua:17563-17601, ROM
 * 0x0800042E-0x08000434 and 0x08000494-0x080004B6): a 44-byte entry with
 * 16-byte names, items at 0x14 and everything after them four bytes on.
 */
data class TrainerLayout(
    val size: Int = 0x28,
    val nameSize: Int = 12,
    val classNameSize: Int = 13,
    val classOffset: Int = 0x01,
    val nameOffset: Int = 0x04,
    val itemsOffset: Int = 0x10,
    val itemSize: Int = 0x02,
    val doubleOffset: Int = 0x18,
    val aiOffset: Int = 0x1C,
    val partySizeOffset: Int = 0x20,
    val partyPtrOffset: Int = 0x24,
    val monLevel: Int = 0x02,
    val monSpecies: Int = 0x04,
    val monItem: Int = 0x06,
    val monNoItemMove1: Int = 0x06,
    val monItemMove1: Int = 0x08,
    val monDefaultSize: Int = 8,
    val monCustomSize: Int = 16,
    val moveSize: Int = 0x02,
    /** trainerPic, the trainer's slot in gTrainerFrontPicTable (GameMap.trainerPics). */
    val picOffset: Int = 0x03,
) {
    companion object { val VANILLA = TrainerLayout() }
}

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
    /**
     * gBattleTextBuff1, from the reference's GameAddresses JSONs (Nat. Dex: its slot at 0x08000208, NatDexExtension.lua:17661).
     * Trace's message names the traced Pokemon's battler at +2 (Battle.lua:648). 0 = unknown.
     */
    val battleTextBuff1: Long = 0,
    /** Which abilityscripts-*.tsv this game uses; "" = feature off. */
    val abilityScriptTable: String = "",
    val baseStats: Long,
    val speciesNames: Long,     // 11 bytes per name; 0 = no table known
    val moveNames: Long,        // 13 bytes per name; 0 = no table known
    /** Vanilla SpeciesInfo is 28 bytes with u8 abilities at 22/23. Nat. Dex uses the
     *  expansion struct: 0x24 bytes, u16 abilities at 0x16/0x18 — derived empirically
     *  from the real 1.2.1 ROM (Bulbasaur/Ivysaur/Charmander/Pikachu fingerprints). */
    val baseStatsStride: Int = 28,
    /** gExperienceTables: 6 growth rates x 101 levels x u32 (0x194 per rate). 0 = no EXP bar. */
    val expTables: Long = 0,
    /**
     * Where a species' growth rate, gender ratio and base friendship sit in
     * its base-stats entry (Program.Addresses.offsetGrowthRateIndex,
     * PokemonData.Addresses.offsetGenderRatio / offsetBaseFriendship). The
     * same in both layouts; Nat. Dex publishes them (0x080003F0, 0x08000470,
     * 0x08000472), and they were not read there at all, so every Nat. Dex
     * EXP bar used growth rate 0 and every species base friendship 70.
     */
    val growthRateOffset: Int = 0x13,
    val genderRatioOffset: Int = 0x10,
    val baseFriendshipOffset: Int = 0x12,
    /**
     * gBattleResults' byte for the turn count (Program.Addresses
     * offsetBattleResultsCurrentTurn): 0x13 vanilla, 0x41 on Nat. Dex
     * (0x0800040A). The last-attack line, Timer Ball and Battle Details read it.
     */
    val battleResultsTurnOffset: Int = 0x13,
    /**
     * BattleDetailsScreen.Addresses offsetBattleMonsStatus2 and
     * offsetBattleStructWrappedBy: 0x50 and 0x14 vanilla; Nat. Dex publishes
     * 0x54 and 0x1A9 (FireRed) / 0x237 (Emerald) at 0x08000448 / 0x0800044A.
     */
    val status2Offset: Int = 0x50,
    val wrappedByOffset: Int = 0x14,
    /**
     * SaveBlock2's owned-Pokemon bits for the Repeat Ball: offsetPokedex
     * (0x18) + offsetPokedexOwned (0x10). Nat. Dex publishes both
     * (0x08000158, 0x08000424); Emerald Nat. Dex's owned bits are at +0xC.
     */
    val pokedexOwnedOffset: Long = 0x18 + 0x10,
    val abilitiesAreU16: Boolean = false,
    /**
     * gBattlerPartyIndexes (u16 per battler): which party slot each battler is.
     * The opposing battlers' slots tell two of one species apart for the
     * encounter count (Battle.lua:276); on Nat. Dex it also finds a battler's
     * ability, whose live byte cannot be trusted there.
     */
    val battlerPartyIndexes: Long = 0,
    /** gActionSelectionCursor (pret symbols; exact for vanilla). 0 = unknown — the
     *  write-based flee fallback stays disabled and only the input macro is used. */
    val actionCursor: Long = 0,
    /** gBattleResults; lastUsedMoveOpponent lives at +0x24 (pret BattleResults). */
    val battleResults: Long = 0,
    /**
     * The move tracking's reads (EnemyMoveWatch, Battle.lua:379-407): gCurrentTurnActionNumber, gActionsByTurnOrder
     * and gHitMarker, from the reference's GameAddresses; Nat. Dex publishes them (0x08000224, 0x08000220, 0x08000244).
     * 0 where unknown, which leaves the opponent's moves untracked rather than guessed.
     */
    val currentTurnActionNumber: Long = 0,
    val actionsByTurnOrder: Long = 0,
    val hitMarker: Long = 0,
    /** The battle scripts the move tracking watches (MoveScripts). */
    val moveScripts: MoveScripts? = null,
    /** gSaveBlock1Ptr — SaveBlock1 begins with the player's x,y (u16 each). */
    val saveBlock1Ptr: Long = 0,
    /** gSaveBlock2Ptr. The bag's security key lives here, not in SaveBlock1. */
    val saveBlock2Ptr: Long = 0,
    /**
     * Ruby and Sapphire keep SaveBlock1 and SaveBlock2 at fixed EWRAM addresses
     * (gSaveBlock1 / gSaveBlock2); every later Gen 3 game moves them and
     * publishes IWRAM pointers instead. Non-zero here wins over the pointer.
     */
    val saveBlock1Fixed: Long = 0,
    val saveBlock2Fixed: Long = 0,
    /**
     * Ruby and Sapphire carry LAYOUT_LILYCOVE_CITY_EMPTY_MAP at map id 108, so
     * every id above 107 is Emerald's plus one (the reference's RouteData
     * offset). True reads an id back onto the shared rse route table.
     */
    val rsMapShift: Boolean = false,
    /**
     * Which version's RouteData this game uses: "firered", "leafgreen", "ruby",
     * "sapphire" or "emerald" (gen3/routeenc-<version>.tsv). The versions pick
     * different species ({FR, LG}, {R, S, E} tables), so one table per group was
     * wrong for half the games (2026-09-28).
     */
    val routeVersion: String = "",
    /** gSpecialVar_ItemId, which rod a fishing encounter used (Battle.incrementEnemyEncounter). 0 = unknown. */
    val specialVarItemId: Long = 0,
    /** gSpecialVar_Result, read after a Rock Smash (RouteData.EncounterArea.ROCKSMASH). 0 = unknown. */
    val specialVarResultAny: Long = 0,
    /**
     * The randomized starter table (ZX's own StarterPokemon offsets): species words here and [starter2Off] and
     * [starter3Off] bytes on. Which ball holds which is read from the ROM by GbaTracker.starters, not taken from this
     * order: FireRed's table runs Bulbasaur, Charmander, Squirtle while its balls stand Bulbasaur, Squirtle, Charmander
     * from the left. 0 = unknown.
     */
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
    /**
     * BattleDetailsScreen.lua's addresses, from the reference's GameAddresses
     * JSON per revision. battleTerrain at 0 turns the screen off. Ruby and
     * Sapphire have no gBattleStructPtr; the struct sits at gSharedMem
     * (0x02000000), which is what 0 means here.
     */
    val battleTerrain: Long = 0,
    val battleStructPtr: Long = 0,
    val statuses3: Long = 0,
    val sideStatuses: Long = 0,
    val sideTimers: Long = 0,
    val disableStructs: Long = 0,
    val lockedMoves: Long = 0,
    val wishFutureKnock: Long = 0,
    val paydayMoney: Long = 0,
    /** Front-pic and palette tables ({romPtr,size,tag} entries, LZ77 targets),
     *  located empirically by tools/find_sprites.py and eyeball-verified per
     *  game. 0 = no sprites. spriteCount bounds the species index. */
    val frontPics: Long = 0,
    val palettes: Long = 0,
    /**
     * pret's gTrainerFrontPicTable and gTrainerFrontPicPaletteTable ({LZ77 data, size, tag} and {LZ77 data, tag} entries,
     * each tagged with its slot) and how many pictures they hold, for the log viewer's trainer portraits (TrainerPictures).
     * Found on each vendored dump and checked by eye (rc34); 0 on a build whose tables are not proven: it draws none.
     */
    val trainerPics: Long = 0,
    val trainerPicPalettes: Long = 0,
    val trainerPicCount: Int = 0,
    /** The player's own picture in that table, the boy's, the girl's the next: FireRed's Red, Emerald's Brendan. -1: none. */
    val playerPic: Int = -1,
    /** Badge flags inside SaveBlock1. FireRed stores a byte at 0xFE4; Emerald a
     *  word at 0x137C whose 8 badge bits start at bit 7. Offsets from the
     *  reference tracker's GameAddresses JSONs. */
    val badgeOffset: Long = 0,
    val badgeIsWord: Boolean = false,
    /**
     * Steps left on the active repel, inside SaveBlock1: the reference's
     * gameVarsOffset plus 0x40 (FireRed, LeafGreen) or 0x42 (Ruby, Sapphire,
     * Emerald), from its GameAddresses JSONs. 0 when the game is not read here.
     */
    val repelStepsOffset: Long = 0,
    /**
     * GameSettings.FriendshipRequiredToEvo: the ROM byte the evolution code
     * compares friendship against. The requirement is that byte plus one
     * (Program.lua:343). 0 = unknown, and the tracker uses 220. Every dump reads
     * 219 there, so 220 on all six games.
     */
    val friendshipRequiredAddr: Long = 0,
    /** gTakenDmg: the damage your Pokemon has taken, for the last-attack line (DamageWatch). 0 = not tracked. */
    val takenDmg: Long = 0,
    /** sMonSummaryScreen: non-zero while a Pokemon summary is open in the game. 0 = not watched. */
    val monSummaryScreen: Long = 0,
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
    /** The Poke Balls pocket (bagPocket_Balls in GameAddresses): Emerald 0x650 x16, FRLG 0x430 x13, RS 0x600 x16. */
    val bagBallsOffset: Long = 0,
    val bagBallsSlots: Int = 0,
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
     * SYS_SAFARI_MODE as a flag index into the save block's flags: the
     * reference's offsetSysFlagStart + offsetSysFlagSafariMode, 0x800 + 0 on
     * FireRed and LeafGreen, 0x860 + 0x2C on Ruby, Sapphire and Emerald
     * (Program.lua:31-49, Program.isInSafariZone at 1511). 0 = not read.
     */
    val safariModeFlag: Int = 0,
    /**
     * RouteData.Locations.IsInSafariZone, by the map id the game reports
     * (Ruby and Sapphire are one higher than Emerald there): the maps whose
     * wild Pokemon Tracker.TrackSafariEncounter keeps (Battle.lua:610).
     */
    val safariMapIds: Set<Int> = emptySet(),
    /**
     * RouteData.Locations.IsInHallOfFame, by the map id the game reports:
     * FireRed/LeafGreen 218, Emerald 431 (Meteor Falls, where Steven ends an
     * Emerald run), Ruby/Sapphire 298 + 1. The repel bar hides there
     * (Program.ActiveRepel.shouldDisplay, Program.lua:253).
     */
    val hallOfFameMapIds: Set<Int> = emptySet(),
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
     * sSpecialFlags: its first byte reads 3 for as long as the catching tutorial runs (Wally's Ralts in Ruby,
     * Sapphire and Emerald, the Old Man's Weedle in FireRed and LeafGreen). 0 where it is not known.
     */
    val specialFlags: Long = 0,
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
    /**
     * gBattleCommunication, one byte per battler: where HandleTurnActionSelectionState is with it. [actionMenuState] is
     * the byte while the battle waits on the action menu (STATE_WAIT_ACTION_CHOSEN): 2 on Emerald, whose enum starts
     * with STATE_TURN_START_RECORD, 1 on FireRed, LeafGreen, Ruby and Sapphire (pret's decomps). In the Bag, the party
     * screen and the move menu it is one more, while gBattleMainFunc stays the same. 0 = unknown.
     */
    val battleCommunication: Long = 0,
    val actionMenuState: Int = 1,
    /** gTrainerBattleOpponent_A: which trainer the last battle was against. */
    val trainerOpponent: Long = 0,
    /**
     * gTrainers, gTrainerClassNames and the save block's flags offset, from the
     * reference's GameAddresses JSON per revision (2026-09-08). Zero means the
     * trainer screens are off for this build.
     */
    val gTrainers: Long = 0,
    val gTrainerClassNames: Long = 0,
    /** How a gTrainers entry and its party are laid out (TrainerLayout). */
    val trainerLayout: TrainerLayout = TrainerLayout.VANILLA,
    /**
     * Program.Addresses.offsetRivalName: FireRed and LeafGreen show a rival's
     * name from here in SaveBlock1, not the ROM's placeholder (Program.lua:1063).
     */
    val rivalNameOffset: Long = 0x3A4C,
    val gameFlagsOffset: Long = 0,
    /** Program.Addresses.offsetTrainerFlagStart: trainer N's defeated flag is this + N. */
    val trainerFlagStart: Int = 0x500,
    /**
     * Program.checkForStarterSelection's addresses, from the GameAddresses
     * JSONs. FRLG: gSpecialVar_Result, and the offered species at
     * gameVarsOffset + 4 in SaveBlock1. RSE: gTasks, whose first task runs
     * Task_HandleConfirmStarterInput while the choice is on screen. Zero where
     * this build does not read it (Nat. Dex).
     */
    val specialVarResult: Long = 0,
    val gameVarsOffset: Long = 0,
    val gTasks: Long = 0,
    val confirmStarterTask: Long = 0,
    /**
     * Beating one of these IS the win condition - the run is over and won.
     * FRLG has three because the champion's team depends on your starter.
     */
    val finalTrainers: Set<Int> = emptySet(),
    val encryptionKeyOffset: Long = 0,
    /** Vanilla species ceiling. Entries past a table's real end fail the LZ77
     *  sanity checks and decode to null rather than garbage. */
    val spriteCount: Int = 412,
    /**
     * The bundled name lists and data a build reads instead of its ROM's: "" for none (the Nat. Dex lists come through
     * [namesFromLists]), "maxdex" for MaxDex 1.0 (maxdex/species.tsv, moves.tsv and abilities.tsv, its own evolutions,
     * random evolutions and icons). Also how the app tells a MaxDex game (GbaTracker.nameSet).
     */
    val nameSet: String = "",
    /** Level-up learnsets in Jambo's format, 3-byte {u16 move, u8 level} entries ending 00 00 FF (MaxDex). */
    val learnsetJambo: Boolean = false,
    /**
     * gBattleMoves carries each move's own physical, special or status byte (MaxDex: u16 effect, power +2, type +3,
     * category +4, accuracy +5, PP +6, priority +9, flags +10). Without it Gen 3's rule holds: the type decides.
     */
    val moveCategoryByte: Boolean = false,
    /**
     * Where gBattleMons keeps types, PP and HP (level 2 bytes on, max HP 4). Vanilla 0x21, 0x24 and 0x28; MaxDex's u16
     * ability moves them to 0x22, 0x25 and 0x2A, which its code's own reads show (hp 0x2A, max HP 0x2E, item 0x30).
     */
    val battleMonTypes: Int = 0x21,
    val battleMonPp: Int = 0x24,
    val battleMonHp: Int = 0x28,
) {
    companion object {
        val EMERALD_U = GameMap(
            name = "Emerald (U)",
            routeVersion = "emerald", specialVarItemId = 0x0203CE7C, specialVarResultAny = 0x020375F0,
            partyCount = 0x020244E9,
            party = 0x020244EC,
            enemyParty = 0x02024744,
            battlerPartyIndexes = 0x0202406E,
            battleTypeFlags = 0x02022FEC,
            battleMons = 0x02024084,
            battlersCount = 0x0202406C,
            scriptCurrInstr = 0x02024214,
            scriptingBattler = 0x0202448B,
            battlerAttacker = 0x0202420B,
            currentTurnActionNumber = 0x02024082, actionsByTurnOrder = 0x0202407A, hitMarker = 0x02024280,
            moveScripts = MoveScripts.EMERALD,
            battlerTarget = 0x0202420C,
            battleTextBuff1 = 0x02022F58,
            abilityScriptTable = "emerald",
            baseStats = 0x083203CC,
            expTables = 0x0831F72C,
            speciesNames = 0x083185C8,
            moveNames = 0x0831977C,
            actionCursor = 0x020244AC,
            battleResults = 0x03005D10,
            saveBlock1Ptr = 0x03005D8C,
            saveBlock2Ptr = 0x03005D90,
            repelStepsOffset = 0x13DE,
            friendshipRequiredAddr = 0x0806D1D6,
            takenDmg = 0x020241F8,
            monSummaryScreen = 0x0203CF1C,
            abilityNames = 0x0831B6DB,
            itemNames = 0x085839A0,
            battleMoves = 0x0831C898,
            weather = 0x020243CC,
            battleTerrain = 0x02022FF0, battleStructPtr = 0x0202449C, statuses3 = 0x020242AC,
            sideStatuses = 0x0202428E, sideTimers = 0x02024294, disableStructs = 0x020242BC,
            lockedMoves = 0x02024268, wishFutureKnock = 0x020243D0,
            // The reference JSON lists gPaydayMoney at gWishFutureKnock, which makes Future Sight read Pay Day money. In battle_main.c
            // gPaydayMoney, gRandomTurnNumber and gBattleCommunication[8] sit right before gBattleOutcome (0x0202433A), so 0x0202432E; FireRed fits the same pattern.
            paydayMoney = 0x0202432E,
            frontPics = 0x08301418,
            palettes = 0x08303678,
            trainerPics = 0x08305654, trainerPicPalettes = 0x0830593C, trainerPicCount = 93, playerPic = 71,
            badgeOffset = 0x137C,
            badgeIsWord = true,
            badgeSet = "RSE",
            levelUpLearnsets = 0x0832937C,
            bagItemsOffset = 0x560,
            bagItemsSlots = 30,
            bagBerriesOffset = 0x790,
            bagBallsOffset = 0x650, bagBallsSlots = 16,
            bagBerriesSlots = 46,
            mapHeader = 0x02037318,
            labMapIds = setOf(17),
            routeTable = "rse",
            safariModeFlag = 0x860 + 0x2C,
            // Emerald adds the two extension areas (RouteData.lua:3256).
            safariMapIds = setOf(238, 239, 240, 241, 394, 395),
            hallOfFameMapIds = setOf(431),
            gameStatsOffset = 0x159C,
            battleOutcome = 0x0202433A,
            specialFlags = 0x020375FC,
            battleMainFunc = 0x03005D04,
            introDrawPartySummary = 0x0803AF81,
            introOpponentSendsOut = 0x0803B315,
            handleTurnAction = 0x0803BE75,
            battleCommunication = 0x02024332,
            actionMenuState = 2,
            returnToOverworld = 0x0803DF71,
            trainerOpponent = 0x02038BCA,
            gTrainers = 0x08310030, gTrainerClassNames = 0x0830FCD4, gameFlagsOffset = 0x1270,
            gTasks = 0x03005E00, confirmStarterTask = 0x08134400,
            // sStarterMon, the starter screen's table (Treecko, Torchic, Mudkip in the dump, 2026-10-01).
            startersBase = 0x085B1DF8, starter2Off = 2, starter3Off = 4,
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
            routeVersion = "firered", specialVarItemId = 0x0203AD30, specialVarResultAny = 0x020370D0,
            partyCount = 0x02024029,
            party = 0x02024284,
            enemyParty = 0x0202402C,
            battlerPartyIndexes = 0x02023BCE,
            battleTypeFlags = 0x02022B4C,
            battleMons = 0x02023BE4,
            battlersCount = 0x02023BCC,
            scriptCurrInstr = 0x02023D74,
            scriptingBattler = 0x02023FDB,
            battlerAttacker = 0x02023D6B,
            currentTurnActionNumber = 0x02023BE2, actionsByTurnOrder = 0x02023BDA, hitMarker = 0x02023DD0,
            moveScripts = MoveScripts.FIRERED_V10,
            battlerTarget = 0x02023D6C,
            battleTextBuff1 = 0x02022AB8,
            abilityScriptTable = "firered",
            baseStats = 0x08254784,
            expTables = 0x08253AE4,
            speciesNames = 0x08245EE0,
            moveNames = 0x08247094,
            battleResults = 0x03004F90,
            saveBlock1Ptr = 0x03005008,
            saveBlock2Ptr = 0x0300500C,
            repelStepsOffset = 0x1040,
            friendshipRequiredAddr = 0x08043002,
            // RAM, so FireRed v1.1 and LeafGreen share it through their copy().
            takenDmg = 0x02023D58,
            monSummaryScreen = 0x0203B140,
            startersBase = 0x08169BB5,
            starter2Off = 515,
            starter3Off = 461,
            abilityNames = 0x0824FC40,
            itemNames = 0x083DB028,
            battleMoves = 0x08250C04,
            weather = 0x02023F1C,
            battleTerrain = 0x02022B50, battleStructPtr = 0x02023FE8, statuses3 = 0x02023DFC,
            sideStatuses = 0x02023DDE, sideTimers = 0x02023DE4, disableStructs = 0x02023E0C,
            lockedMoves = 0x02023DB8, wishFutureKnock = 0x02023F20, paydayMoney = 0x02023E7E,
            frontPics = 0x082350AC,   // == pret gMonFrontPicTable
            palettes = 0x0823730C,    // == pret gMonPaletteTable
            trainerPics = 0x0823957C, trainerPicPalettes = 0x08239A1C, trainerPicCount = 148, playerPic = 135,
            badgeOffset = 0xFE4,
            badgeIsWord = false,
            badgeSet = "FRLG",
            levelUpLearnsets = 0x0825D7B4,
            bagItemsOffset = 0x310,
            bagItemsSlots = 42,
            bagBerriesOffset = 0x54C,
            bagBallsOffset = 0x430, bagBallsSlots = 13,
            bagBerriesSlots = 43,
            mapHeader = 0x02036DFC,
            labMapIds = setOf(5),
            routeTable = "frlg",
            safariModeFlag = 0x800 + 0x0,
            safariMapIds = setOf(147, 148, 149, 150),
            hallOfFameMapIds = setOf(218),
            gameStatsOffset = 0x1200,
            battleOutcome = 0x02023E8A,
            specialFlags = 0x020370E0,
            battleMainFunc = 0x03004F84,
            introDrawPartySummary = 0x0801333D,
            introOpponentSendsOut = 0x0801359D,
            handleTurnAction = 0x08014041,
            battleCommunication = 0x02023E82,
            returnToOverworld = 0x08015B59,
            trainerOpponent = 0x020386AE,
            gTrainers = 0x0823EAC8, gTrainerClassNames = 0x0823E558, gameFlagsOffset = 0xEE0,
            specialVarResult = 0x020370D0, gameVarsOffset = 0x1000,
            finalTrainers = setOf(438, 439, 440),
            encryptionKeyOffset = 0xF20,
        )

        /**
         * Ruby (U) v1.0. RAM and code addresses from the reference's "Pokemon
         * Ruby v1.0.json"; ROM tables (names, abilities, items, moves, pics,
         * palettes) located in Blake's own dump on 2026-09-07 with
         * tools/find_tables.py and tools/find_sprites.py, the same method that
         * found FireRed's and Emerald's (re-run on those two, it reproduces
         * every shipped address). The party lives in IWRAM here, the save
         * blocks sit at fixed addresses, and bag quantities are not encrypted.
         * Ruby and Sapphire share every RAM address; only the ROM tables move.
         */
        val RUBY_U = GameMap(
            name = "Ruby (U) v1.0",
            routeVersion = "ruby", specialVarItemId = 0x0203855E, specialVarResultAny = 0x0202E8DC,
            partyCount = 0x03004350,
            party = 0x03004360,
            enemyParty = 0x030045C0,
            battlerPartyIndexes = 0x02024A6A,
            battleTypeFlags = 0x020239F8,
            battleMons = 0x02024A80,
            battlersCount = 0x02024A68,
            scriptCurrInstr = 0x02024C10,
            scriptingBattler = 0x02016003,
            battlerAttacker = 0x02024C07,
            currentTurnActionNumber = 0x02024A7E, actionsByTurnOrder = 0x02024A76, hitMarker = 0x02024C6C,
            moveScripts = MoveScripts.RUBY_V10,
            battlerTarget = 0x02024C08,
            battleTextBuff1 = 0x030041C0,
            abilityScriptTable = "ruby",
            baseStats = 0x081FEC18,
            expTables = 0x081FDF78,
            speciesNames = 0x081F716C,
            moveNames = 0x081F8320,
            battleResults = 0x030042E0,
            saveBlock1Fixed = 0x02025734,
            saveBlock2Fixed = 0x02024EA4,
            repelStepsOffset = 0x1382,
            friendshipRequiredAddr = 0x0803F5CA,
            takenDmg = 0x02024BF4,
            monSummaryScreen = 0x02018076,
            abilityNames = 0x081FA248,
            itemNames = 0x083C5564,
            battleMoves = 0x081FB12C,
            weather = 0x02024DB8,
            battleTerrain = 0x0300428C, statuses3 = 0x02024C98,
            sideStatuses = 0x02024C7A, sideTimers = 0x02024C80, disableStructs = 0x02024CA8,
            lockedMoves = 0x02024C54, wishFutureKnock = 0x02024DBC, paydayMoney = 0x02024D1A,
            frontPics = 0x081E8354,
            palettes = 0x081EA5B4,
            trainerPics = 0x081EC53C, trainerPicPalettes = 0x081EC7D4, trainerPicCount = 83, playerPic = 0,
            badgeOffset = 0x1320,
            badgeIsWord = true,
            badgeSet = "RSE",
            levelUpLearnsets = 0x08207BC8,
            bagItemsOffset = 0x560,
            bagItemsSlots = 20,
            bagBerriesOffset = 0x740,
            bagBallsOffset = 0x600, bagBallsSlots = 16,
            bagBerriesSlots = 46,
            mapHeader = 0x0202E828,
            labMapIds = setOf(17),
            routeTable = "rse",
            safariModeFlag = 0x860 + 0x2C,
            // 238 + offset: the reference keys Ruby and Sapphire's safari maps one higher.
            safariMapIds = setOf(239, 240, 241, 242),
            hallOfFameMapIds = setOf(298 + 1),
            rsMapShift = true,
            gameStatsOffset = 0x1540,
            battleOutcome = 0x02024D26,
            specialFlags = 0x0202E8E2,
            battleMainFunc = 0x030042D4,
            introDrawPartySummary = 0x08011601,
            introOpponentSendsOut = 0x080118C5,
            handleTurnAction = 0x08012325,
            battleCommunication = 0x02024D1E,
            returnToOverworld = 0x08013EB1,
            trainerOpponent = 0x0202FF5E,
            gTrainers = 0x081F04FC, gTrainerClassNames = 0x081F0208, gameFlagsOffset = 0x1220,
            gTasks = 0x03004B20, confirmStarterTask = 0x0810A330,
            startersBase = 0x083F76C4, starter2Off = 2, starter3Off = 4,
            finalTrainers = setOf(335),      // Steven, TrainerData.setupTrainersAsRubySapphire
            encryptionKeyOffset = 0,         // Ruby and Sapphire store quantities in the clear
        )

        /** Sapphire (U) v1.0: Ruby's RAM, its own ROM tables (found the same way, the same day). */
        val SAPPHIRE_U = RUBY_U.copy(
            name = "Sapphire (U) v1.0",
            routeVersion = "sapphire",
            abilityScriptTable = "sapphire",
            moveScripts = MoveScripts.SAPPHIRE_V10,
            baseStats = 0x081FEBA8,
            expTables = 0x081FDF08,
            speciesNames = 0x081F70FC,
            moveNames = 0x081F82B0,
            abilityNames = 0x081FA1D8,
            itemNames = 0x083C55BC,
            battleMoves = 0x081FB0BC,
            frontPics = 0x081E82E4,
            palettes = 0x081EA544,
            trainerPics = 0x081EC4CC, trainerPicPalettes = 0x081EC764,
            levelUpLearnsets = 0x08207B58,
            gTrainers = 0x081F048C, gTrainerClassNames = 0x081F0198,
            startersBase = 0x083F771C,
        )

        /**
         * LeafGreen (U) v1.0: FireRed v1.0's RAM (identical in the reference's
         * two files), its ROM tables 0x24 lower, found in the dump. Its starter
         * table is its own, below.
         */
        val LEAFGREEN_U = FIRERED_U_V10.copy(
            name = "LeafGreen (U) v1.0",
            routeVersion = "leafgreen",
            abilityScriptTable = "leafgreen",
            moveScripts = MoveScripts.LEAFGREEN_V10,
            baseStats = 0x08254760,
            expTables = 0x08253AC0,
            speciesNames = 0x08245EBC,
            moveNames = 0x08247070,
            abilityNames = 0x0824FC1C,
            itemNames = 0x083DAE64,
            battleMoves = 0x08250BE0,
            frontPics = 0x08235088,
            palettes = 0x082372E8,
            trainerPics = 0x08239558, trainerPicPalettes = 0x082399F8,
            levelUpLearnsets = 0x0825D794,
            gTrainers = 0x0823EAA4, gTrainerClassNames = 0x0823E534,
            // Switched off (0) until 2026-09-19. FireRed's starter code sits 0x24
            // earlier on LeafGreen and keeps FireRed's spacing: Blake's dump reads
            // Bulbasaur, Charmander, Squirtle (1, 4, 7) at +0, +515 and +461.
            startersBase = 0x08169B91, starter2Off = 515, starter3Off = 461,
        )

        /** Nat. Dex bakes 1258 into ROM at this address; vanilla has other bytes here. */
        const val NATDEX_MAGIC_ADDR = 0x08000170L
        const val NATDEX_MAGIC = 1258L
        /** The Nat. Dex ROM's version, major/minor/patch bytes (NatDexExtension.lua:14-16). */
        const val NATDEX_VERSION_ADDR = 0x0800048CL

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
            expTables = 0x08253B54,
            // The trainer tables move with the rest of the data by +0x70; inheriting v1.0 through the copy() read the wrong trainers (caught by AddressAuditTest).
            gTrainers = 0x0823EB38, gTrainerClassNames = 0x0823E5C8,
            battleMoves = 0x08250C74,
            levelUpLearnsets = 0x0825D824,
            // The NAME and GRAPHICS tables move by the same +0x70 and every one
            // of them was INHERITED from v1.0 through the copy(), so a v1.1 ROM
            // read them 112 bytes early while its numbers stayed right - which
            // is the worst shape a bug can take here, because nothing looks
            // broken. Species landed on the previous record's 0x00 padding,
            // which Gen 3 text decodes as SPACES, and then on the name ten
            // records back: FLAAFFY displayed as a clean "CHINCHOU". Moves
            // landed nine records back and five bytes in, so SUPERPOWER read as
            // "E POWER" (the tail of NATURE POWER) beside its own correct PP,
            // power and accuracy. Blake caught it on a trainer battle screenshot.
            //
            // Found in his own v1.1 dump with tools/find_tables.py, the v1.0 run
            // reproducing all four shipped v1.0 addresses as the control.
            speciesNames = 0x08245F50,
            moveNames = 0x08247104,
            abilityNames = 0x0824FCB0,
            itemNames = 0x083DB098,
            frontPics = 0x0823511C,
            palettes = 0x0823737C,
            trainerPics = 0x082395EC, trainerPicPalettes = 0x08239A8C,
            // Code, not data: verified at +0x78, NOT the +0x70 the tables take.
            startersBase = 0x08169C2D,
            // Code, like startersBase: +0x14, the battle functions' shift.
            friendshipRequiredAddr = 0x08043016,
            abilityScriptTable = "firered11",
            moveScripts = MoveScripts.FIRERED_V11,
        )

        /** What the MaxDex map is called, and how Overworld and the app know it. */
        const val MAXDEX_NAME = "MaxDex 1.0"
        /** MaxDex keeps its species count where Nat. Dex keeps 1258 ([NATDEX_MAGIC_ADDR]). */
        const val MAXDEX_SPECIES = 1255L

        /**
         * MaxDex 1.0 (Trip, Tripc423/Maxdex): FireRed 1.1 patched to CRC 28C12926, Nat. Dex 1.1.3 grown to 1255 species.
         * It publishes no slot table: its addresses are the ones its tracker extension hardcodes for this build
         * (MaxDexExtension.lua 1.0, updateGameSettings' FireRed block, "These are this build's values (resync if the ROM
         * is rebuilt)", and updateProgramAddresses), the ROM tables also as the Game Freak header at 0x100 names them.
         * What the extension leaves alone (battle type flags, battler count, battle mons, party indexes, turn order) is
         * FireRed 1.1's, as on the PC tracker. Unconfirmed until a run shows them: the scripting battler (0x02024023, its
         * Emerald block's gBattleScripting + 0x17) and the Balls and Berries pockets (its pocket formula on FireRed's
         * sizes). The rival's name and the Pokedex-owned bits keep FireRed's offsets, which the extension does too.
         */
        val MAXDEX_FR_10 = FIRERED_U_V11.copy(
            name = MAXDEX_NAME,
            nameSet = "maxdex",
            partyCount = 0x02024075,
            party = 0x020242D0,
            enemyParty = 0x02024078,
            scriptCurrInstr = 0x02023D84,
            scriptingBattler = 0x02024023,
            battlerAttacker = 0x02023D7C,
            battlerTarget = 0x02023D7D,
            hitMarker = 0x02023DE0,
            moveScripts = MoveScripts.MAXDEX_FR_10,
            abilityScriptTable = "maxdex",
            // gSpeciesInfo, 0x20 per species: u16 abilities at 0x16 and 0x18, growth 0x13, gender 0x10, friendship 0x12.
            baseStats = 0x08270988,
            baseStatsStride = 0x20,
            abilitiesAreU16 = true,
            expTables = 0x0826FCE8,
            // Names come from the lists (nameSet); the ROM's own are cut to ten letters and its abilities past 77 shout.
            speciesNames = 0,
            moveNames = 0,
            abilityNames = 0x082A4134,
            abilityStride = 17,
            itemNames = 0x084275D8,
            itemStride = 44,
            battleMoves = 0x08268000,
            moveCategoryByte = true,
            levelUpLearnsets = 0x0829E474,
            learnsetJambo = true,
            gTrainers = 0x0823EC00,
            gTrainerClassNames = 0x0823E690,
            frontPics = 0x0824E5D4,
            palettes = 0x08255D78,
            trainerPics = 0x0825C548, trainerPicPalettes = 0x0825C9E8,
            spriteCount = 1281,
            expandedSpeciesIds = true,
            // The lab's balls: Bulbasaur's script word here, Charmander's 503 on, Squirtle's 449 (the 1.1.3 randomizer's own).
            startersBase = 0x08A0A6A1,
            starter2Off = 503,
            starter3Off = 449,
            friendshipRequiredAddr = 0x081CD74A,   // GetEvolutionTargetSpecies + 0x13E
            battleResults = 0x03004BC0,
            saveBlock1Ptr = 0x03004C38,
            saveBlock2Ptr = 0x03004C3C,
            gameFlagsOffset = 0x1078,
            gameVarsOffset = 0x1198,
            gameStatsOffset = 0x1398,
            badgeOffset = 0x1078 + 0x104,
            encryptionKeyOffset = 0x40C,
            repelStepsOffset = 0x1198 + 0x40,
            // The Items pocket holds 120; the pockets after it follow on (Key Items 30, Balls 13, TMs 58, Berries 43).
            bagItemsOffset = 0x310,
            bagItemsSlots = 120,
            bagBallsOffset = 0x568,
            bagBallsSlots = 13,
            bagBerriesOffset = 0x684,
            bagBerriesSlots = 43,
            takenDmg = 0x02023D68,
            weather = 0x02023F64,
            battleTerrain = 0x02022B51,
            battleStructPtr = 0x02024034,
            statuses3 = 0x02023E24,
            sideStatuses = 0x02023DEE,
            sideTimers = 0x02023E04,
            disableStructs = 0x02023E34,
            lockedMoves = 0x02023DC8,
            wishFutureKnock = 0x02023F68,
            paydayMoney = 0x02023EC6,
            battleCommunication = 0x02023ECA,
            battleOutcome = 0x02023ED2,
            mapHeader = 0x0203641C,
            specialVarResult = 0x020366F0,
            specialVarResultAny = 0x020366F0,
            specialFlags = 0x02036700,
            trainerOpponent = 0x02037CCE,
            specialVarItemId = 0x0203BB60,
            monSummaryScreen = 0x0203BF6C,
            battleMainFunc = 0x03004BB4,
            introDrawPartySummary = 0x08014011,
            introOpponentSendsOut = 0x0801427D,
            handleTurnAction = 0x08014DE1,
            returnToOverworld = 0x08017039,
            // BattlePokemon is 0x5C (the doubles partner 0xB8 on), its u16 ability moving the rest along.
            battleMonSize = 0x5C,
            battleMonTypes = 0x22,
            battleMonPp = 0x25,
            battleMonHp = 0x2A,
            status2Offset = 0x54,
        )

        /**
         * The MaxDex map when the ROM is MaxDex 1.0, by the Game Freak header it carries (all literal in the patch,
         * none moved by randomizing): FireRed 1.1's code and version, 1255 species, and the species, base stats and
         * moves tables where this build has them. Anything else that claims 1255 species is refused, never read
         * with these addresses.
         */
        private fun maxDex(memory: MemoryReader): GameMap {
            fun u32(addr: Long): Long { val b = memory.read(addr, 4); return if (b.size == 4) b.u32(0) else -1L }
            val code = memory.read(0x080000AC, 4)
            val version = memory.read(0x080000BC, 1)
            val header = code.size == 4 && String(code, Charsets.US_ASCII) == "BPRE" && version.size == 1 && version[0].toInt() == 1
            val tables = u32(0x08000144) == 0x08246018L && u32(0x080001BC) == 0x08270988L && u32(0x080001CC) == 0x08268000L
            require(header && tables) { "A ROM with 1255 species that is not MaxDex 1.0: this build's addresses would read the wrong memory." }
            return MAXDEX_FR_10
        }

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
            /** A u16 from the slot table, kept only inside [ok]; else the vanilla [fallback]. */
            fun slot16(addr: Long, ok: IntRange, fallback: Int): Int {
                val b = memory.read(addr, 2)
                val v = if (b.size < 2) -1 else b.u16(0)
                return if (v in ok) v else fallback
            }
            val magic = ptr(NATDEX_MAGIC_ADDR)
            if (magic == MAXDEX_SPECIES) return maxDex(memory)
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
                    // 2026-09-07: Ruby, Sapphire and LeafGreen from Blake's v1.0 dumps.
                    // A v1.1 or v1.2 cartridge moves every ROM table (the reference keeps
                    // one address file per revision), so those are refused by name rather
                    // than read with the wrong map.
                    "AXV" -> if (ver == 0) RUBY_U else error("Ruby v1.$ver is not supported yet; v1.0 is")
                    "AXP" -> if (ver == 0) SAPPHIRE_U else error("Sapphire v1.$ver is not supported yet; v1.0 is")
                    "BPG" -> if (ver == 0) LEAFGREEN_U else error("LeafGreen v1.$ver is not supported yet; v1.0 is")
                    else -> error("Unrecognized ROM header \"" + id + "\"")
                }
            }

            val em = isEmeraldHeader(memory)
            /*
             * CustomCode.lua:543-554: for Nat. Dex 1.1.3 and older the reference
             * swaps in its *_NatDex_113 addresses (GameAddresses JSONs), because
             * those ROMs did not publish these tables; anything newer is read
             * through the ROM's own slots. The version is the ROM's, at
             * 0x0800048C (major, minor, patch, as the extension's game-over line
             * reads it; the reference asks the extension's own version). Bytes
             * there that are not a version at all predate the field: 1.1.3 or older.
             */
            val legacy113 = run {
                val v = memory.read(NATDEX_VERSION_ADDR, 3)
                if (v.size < 3) true else {
                    val major = v.u8(0); val minor = v.u8(1); val patch = v.u8(2)
                    major !in 1..9 || major * 10000 + minor * 100 + patch <= 10103
                }
            }
            val lay = TrainerLayout.VANILLA
            val trainerLayout = if (legacy113) lay else TrainerLayout(
                size = slot16(0x0800042E, 0x20..0x80, lay.size),
                nameSize = slot16(0x08000430, 8..32, lay.nameSize),
                classNameSize = slot16(0x08000432, 8..32, lay.classNameSize),
                classOffset = slot16(0x08000494, 0..0x7F, lay.classOffset),
                nameOffset = slot16(0x0800049A, 0..0x7F, lay.nameOffset),
                itemsOffset = slot16(0x0800049C, 0..0x7F, lay.itemsOffset),
                itemSize = slot16(0x080004B4, 1..4, lay.itemSize),
                doubleOffset = slot16(0x0800049E, 0..0x7F, lay.doubleOffset),
                aiOffset = slot16(0x080004A0, 0..0x7F, lay.aiOffset),
                partySizeOffset = slot16(0x080004A2, 0..0x7F, lay.partySizeOffset),
                partyPtrOffset = slot16(0x080004A4, 0..0x7F, lay.partyPtrOffset),
                monLevel = slot16(0x080004A6, 0..0x3F, lay.monLevel),
                monSpecies = slot16(0x080004A8, 0..0x3F, lay.monSpecies),
                monItem = slot16(0x080004AA, 0..0x3F, lay.monItem),
                monNoItemMove1 = slot16(0x080004AC, 0..0x3F, lay.monNoItemMove1),
                monItemMove1 = slot16(0x080004AE, 0..0x3F, lay.monItemMove1),
                monDefaultSize = slot16(0x080004B0, 4..0x40, lay.monDefaultSize),
                monCustomSize = slot16(0x080004B2, 4..0x40, lay.monCustomSize),
                moveSize = slot16(0x080004B6, 1..4, lay.moveSize),
                // Between the class (0x494) and the name (0x49A): the music and gender byte's offset, then the picture's.
                picOffset = slot16(0x08000498, 0..0x7F, lay.picOffset),
            )
            // The trainer pictures' tables of Nat. Dex 1.2.1, found in both vendored builds (rc34): another version draws none.
            val v121 = memory.read(NATDEX_VERSION_ADDR, 3).let { it.size == 3 && it.u8(0) == 1 && it.u8(1) == 2 && it.u8(2) == 1 }

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
                // NatDexExtension.lua:17667-17676 and 17749-17761: the move tracking's reads and scripts.
                currentTurnActionNumber = ewramPtr(ptr(0x08000224)),
                actionsByTurnOrder = ewramPtr(ptr(0x08000220)),
                hitMarker = ewramPtr(ptr(0x08000244)),
                moveScripts = MoveScripts.natDex { ptr(it) },
                battlerTarget = ewramPtr(ptr(0x08000234)),
                battleTextBuff1 = ewramPtr(ptr(0x08000208)),
                battlerPartyIndexes = ewramPtr(ptr(0x0800021C)),   // GS.gBattlerPartyIndexes
                abilityScriptTable = "natdex",
                baseStats = ptr(0x080001BC),
                speciesNames = 0L,      // no pointer slot; names via imported lists later
                moveNames = 0L,
                baseStatsStride = 0x24,
                abilitiesAreU16 = true,
                battleResults = ptr(0x080002D8),
                // GS.gExperienceTables = Memory.read32(0x08000308), and the growth rate the
                // table is indexed by (Program.getNextLevelExp) at the published offset.
                expTables = romPtr(ptr(0x08000308)),
                growthRateOffset = slot16(0x080003F0, 0..0x23, 0x13),
                genderRatioOffset = slot16(0x08000470, 0..0x23, 0x10),
                baseFriendshipOffset = slot16(0x08000472, 0..0x23, 0x12),
                // NatDexExtension.lua:17671-17708: the battle addresses Battle Details, the
                // last-attack line and the weather read. Each was left at 0, so those were off.
                takenDmg = ewramPtr(ptr(0x0800022C)),
                weather = ewramPtr(ptr(0x08000264)),
                battleTerrain = ewramPtr(ptr(0x08000210)),
                battleStructPtr = ewramPtr(ptr(0x08000270)),
                // CustomCode.lua:547-553: Emerald 1.1.3 and older take the *_NatDex_113 values;
                // FireRed has none of these in its JSONs, so it keeps the slots.
                statuses3 = if (legacy113 && em) 0x020242A8L else ewramPtr(ptr(0x08000250)),
                sideStatuses = if (legacy113 && em) 0x0202428AL else ewramPtr(ptr(0x08000248)),
                sideTimers = if (legacy113 && em) 0x02024290L else ewramPtr(ptr(0x0800024C)),
                disableStructs = if (legacy113 && em) 0x020242B8L else ewramPtr(ptr(0x08000254)),
                lockedMoves = if (legacy113 && em) 0x02024264L else ewramPtr(ptr(0x0800023C)),
                wishFutureKnock = if (legacy113 && em) 0x020243CCL else ewramPtr(ptr(0x08000268)),
                paydayMoney = if (legacy113 && em) 0x0202432AL else ewramPtr(ptr(0x08000258)),
                status2Offset = slot16(0x08000448, 0..0x7F, 0x50),
                wrappedByOffset = slot16(0x0800044A, 0..0x3FF, 0x14),
                battleResultsTurnOffset = slot16(0x0800040A, 0..0x7F, 0x13),
                monSummaryScreen = ewramPtr(ptr(0x080002AC)),
                // GetEvolutionTargetSpecies (0x080002FC) + 0x1A9 on FireRed, + 0x1AD on
                // Emerald: the byte the friendship evolution compares against
                // (NatDexExtension.lua:17702-17706). Both ROMs read 219 there.
                friendshipRequiredAddr = romPtr(ptr(0x080002FC)).let { if (it == 0L) 0L else it + if (em) 0x1AD else 0x1A9 },
                // gSpecialVar_Result (0x08000288): the FRLG starter preview and Rock Smash;
                // gSpecialVar_ItemId (0x080002A8): which rod a fishing encounter used.
                specialVarResult = if (em) 0L else ewramPtr(ptr(0x08000288)),
                specialVarResultAny = ewramPtr(ptr(0x08000288)),
                specialVarItemId = ewramPtr(ptr(0x080002A8)),
                // The RSE starter preview: gTasks and Task_HandleConfirmStarterInput (the slot
                // holds the thumb address, hence - 1), or the _NatDex_113 address for the old
                // ROMs (Program.lua:741-746). FireRed publishes 0 and uses the special var.
                gTasks = iwramPtr(ptr(0x080002E8)),
                confirmStarterTask = if (!em) 0L else if (legacy113) 0x08134DC0L
                    else romPtr(ptr(0x08000300)).let { if (it == 0L) 0L else it - 1 },
                // The starter tables of Nat. Dex 1.2.1, found in Blake's builds (2026-10-01): Emerald's starter screen
                // table and FireRed's lab scripts. No slot publishes them, so on another build starters() finds other
                // bytes around them and reads no balls rather than three wrong ones.
                startersBase = if (em) 0x08612CECL else 0x089746A7L,
                starter2Off = if (em) 2 else 511,
                starter3Off = if (em) 4 else 457,
                // Program.Addresses.offsetPokedex (u32 at 0x08000158) + offsetPokedexOwned (0x08000424).
                pokedexOwnedOffset = slotOffset(0x08000158, 0x18) + slot16(0x08000424, 0..0xFF, 0x10),
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
                trainerPics = if (!v121) 0L else if (em) 0x08367694L else 0x082313F8L,
                trainerPicPalettes = if (!v121) 0L else if (em) 0x0836797CL else 0x08231898L,
                trainerPicCount = if (em) 93 else 148,
                playerPic = if (em) 71 else 135,
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
                // The save layout moves too (flags, vars, badges, bag, stats),
                // and the ROM publishes every offset the reference reads.
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
                // Program.updateRepelSteps: gameVarsOffset + offsetRepelStepCount, both
                // published (0x08000154, 0x080003EE): FireRed 0x11B0 + 0x40, Emerald
                // 0x1584 + 0x52. The vanilla 0x1040 / 0x13DE read unrelated save data.
                repelStepsOffset = slotOffset(0x08000154, if (em) 0x139C else 0x1000) +
                    slot16(0x080003EE, 0..0xFF, if (em) 0x42 else 0x40),
                badgeSet = if (isEmeraldHeader(memory)) "RSE" else "FRLG",
                bagItemsOffset = slotOffset(0x080002BC,
                    if (isEmeraldHeader(memory)) 0x560 else 0x310),
                bagItemsSlots = slotByte(0x080001E4,
                    if (isEmeraldHeader(memory)) 30 else 42),
                bagBerriesOffset = slotOffset(0x080002CC,
                    if (isEmeraldHeader(memory)) 0x790 else 0x54C),
                bagBerriesSlots = slotByte(0x080001E8,
                    if (isEmeraldHeader(memory)) 46 else 43),
                // FireRed 0x580 x13, Emerald 0x7D0 x16; unread until now, so Catch Rates was off.
                bagBallsOffset = slotOffset(0x080002C4, if (em) 0x650 else 0x430),
                bagBallsSlots = slotByte(0x080001E6, if (em) 16 else 13),
                // gLevelUpLearnsets_NatDex_113 for the old ROMs (CustomCode.lua:545).
                levelUpLearnsets = if (legacy113) (if (em) 0x08349750L else 0x0829050CL) else romPtr(ptr(0x0800030C)),
                learnsetWide = true,
                mapHeader = ewramPtr(ptr(0x08000284)),
                labMapIds = if (isEmeraldHeader(memory)) setOf(17) else setOf(5),
                routeTable = if (isEmeraldHeader(memory)) "rse" else "frlg",
                // offsetSysFlagStart (0x08000406) + offsetSysFlagSafariMode (0x08000408), as
                // the extension reads them; both builds publish the vanilla values.
                safariModeFlag = run {
                    fun u16(addr: Long): Int {
                        val b = memory.read(addr, 2)
                        return if (b.size < 2) -1 else b.u16(0)
                    }
                    val start = u16(0x08000406); val safari = u16(0x08000408)
                    if (start in 0x100..0x2000 && safari in 0..0xFF) start + safari
                    else if (isEmeraldHeader(memory)) 0x860 + 0x2C else 0x800
                },
                safariMapIds = if (isEmeraldHeader(memory)) setOf(238, 239, 240, 241, 394, 395) else setOf(147, 148, 149, 150),
                hallOfFameMapIds = if (isEmeraldHeader(memory)) setOf(431) else setOf(218),
                // The expansion keeps Gen 3's internal species ids, so the base game's
                // routes apply.
                routeVersion = if (isEmeraldHeader(memory)) "emerald" else "firered",
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
                    // sizeofPokemonNickname (NatDexExtension.lua:17600): 12 on both 1.2.1 builds, bounded to 10..12.
                    val nick = memory.read(0x08000176, 1).let { if (it.isEmpty()) 0 else it.u8(0) }.takeIf { it in 10..12 } ?: 10
                    // Refuse nonsense rather than decode against garbage: an
                    // unreadable table falls back to the vanilla layout.
                    if (size in 100..160 && enc in 0x10..0x40 && lvl in 0x40..0x80)
                        PokemonDecoder.Layout(
                            size = size, enc = enc, status = status,
                            level = lvl, curHp = lvl + 2, maxHp = maxHp, nickLen = nick)
                    else PokemonDecoder.Layout.VANILLA
                },
                gameStatsOffset = slotOffset(0x080002B4,
                    if (isEmeraldHeader(memory)) 0x159C else 0x1200),
                battleOutcome = ewramPtr(ptr(0x08000260)),
                specialFlags = ewramPtr(ptr(0x0800028C)),
                battleMainFunc = ptr(0x080002D4),
                introDrawPartySummary = ptr(0x080002EC),
                introOpponentSendsOut = ptr(0x080002F0),
                handleTurnAction = ptr(0x080002F4),
                // GS.gBattleCommunication (NatDexExtension.lua:17682); Emerald's numbering on an Emerald build.
                battleCommunication = ewramPtr(ptr(0x0800025C)),
                actionMenuState = if (em) 2 else 1,
                returnToOverworld = ptr(0x080002F8),
                trainerOpponent = ewramPtr(ptr(0x08000294)),
                // GS.gTrainers / GS.gTrainerClassNames = Memory.read32(0x08000314 / 0x08000310)
                // (NatDexExtension.lua:17744-17745). These were hardcoded to the 1.1.3
                // addresses for every build, so on 1.2.1 every trainer screen read garbage:
                // trainer 414 is Brock through the slots, nonsense through the old address.
                gTrainers = if (legacy113) (if (em) 0x08311190L else 0x0823D818L) else romPtr(ptr(0x08000314)),
                gTrainerClassNames = if (legacy113) (if (em) 0x0830C120L else 0x0823D2A8L) else romPtr(ptr(0x08000310)),
                trainerLayout = trainerLayout,
                // GS.gameFlagsOffset / gameVarsOffset = Memory.read32(0x08000150 / 0x08000154)
                // (NatDexExtension.lua:17725-17726): FireRed 0x1090 / 0x11B0, Emerald
                // 0x1458 / 0x1584. The vanilla 0xEE0 / 0x1270 left every defeated-trainer
                // count and the Safari flag reading the wrong bytes.
                gameFlagsOffset = slotOffset(0x08000150, if (em) 0x1270 else 0xEE0),
                gameVarsOffset = slotOffset(0x08000154, if (em) 0x139C else 0x1000),
                trainerFlagStart = slot16(0x08000404, 0x100..0x2000, 0x500),
                // PA.offsetRivalName = Memory.read16(0x08000420): 0x3829 on FireRed (vanilla
                // 0x3A4C). Emerald publishes 0; the reference reads it on FRLG only.
                rivalNameOffset = slot16(0x08000420, 0x1..0x3FFF, 0x3A4C).toLong(),
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
    /** Growth rate index, SpeciesInfo +0x13 on the vanilla struct; 0 where the layout is not pinned. */
    val growthRate: Int = 0,
    /** SpeciesInfo +8, PokemonData's catchRate. */
    val catchRate: Int = 0,
    /** SpeciesInfo +0x12 on the vanilla struct (PokemonData.Addresses.offsetBaseFriendship); 70 where the layout is not pinned. */
    val baseFriendship: Int = EvoText.DEFAULT_BASE,
    /** SpeciesInfo +0x10 (PokemonData.Addresses.offsetGenderRatio); 255 = genderless or unknown. */
    val genderRatio: Int = 255,
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
    /** Program.getNextLevelExp: experience earned into this level, and the level's total. 0 total = unknown. */
    val expNow: Int = 0,
    val expTotal: Int = 0,
    /** The evolution in brackets after the level (EvoText). Null when it does not evolve. */
    val evo: EvoText.Label? = null,
    /** The game's picture of it where that is not its species' plain one: shiny, Unown's letter, Deoxys's form (GbaTracker.picture). */
    val picture: Gen3Pictures.Picture? = null,
)

/**
 * The opponent, read from gBattleMons battler 1 (unencrypted BattlePokemon, pret
 * layout: species@0, moves@0xC, ability@0x20, types@0x21/0x22, hp@0x28 u16,
 * level@0x2A, maxHP@0x2C u16). Only [movesSeen] is revealed — moves the enemy has
 * actually used this battle, as the reference tracks them (EnemyMoveWatch) —
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
    /** The same evolution text, in the default colour: the reference gives an opponent no readiness. */
    val evo: EvoText.Label? = null,
    /** Its personality value (BattlePokemon +0x48, +0x4C on Nat. Dex), for its gender, and Unown's letter on the Walking Pals icon. */
    val pid: Long = 0,
    /**
     * Its actual four moves and their PP (BattlePokemon +0x0C, +0x24), in slot
     * order. Shown only where the reference allows it: an unrandomized
     * learnset, or Open Book.
     */
    val moves: List<Int> = emptyList(),
    val movePps: List<Int> = emptyList(),
    /**
     * The rolled ability id from the battle struct. INTERNAL: the panel
     * must not display it directly - the reference reveals an enemy
     * ability only when a battle script shows it activating, and the
     * revealed name arrives through [TrackerState.abilityRevealed].
     */
    val abilityId: Int = 0,
    val statStages: StatStages = emptyMap(),
    val statusCondition: String = "",
    /**
     * Tracker.getGhostPokemon: this is the reference's stand-in for a Pokemon
     * Tower ghost fought without the Silph Scope, not the Pokemon itself. Its
     * species is the GhostId, its name "Ghost", its types unknown, and nothing
     * about the real Pokemon is carried (Tracker.lua:121, 542).
     */
    val isGhost: Boolean = false,
    /**
     * Gold, Silver and Crystal: its DVs as the battle struct holds them (wEnemyMon +6, Attack and Defense then Speed and
     * Special), for whether it is shiny and Unown's letter on the Walking Pals icon (PalForms); -1 elsewhere.
     */
    val dvs: Int = -1,
    /** The game's picture of it where that is not its species' plain one: shiny, Unown's letter, Deoxys's form (GbaTracker.picture). */
    val picture: Gen3Pictures.Picture? = null,
)

/** One Pokemon of the opposing party: its party slot, species, level and whether it still has HP. */
/** An opposing Pokemon's moves seen this battle, in the order it used them (TrackerState.enemyMovesThisBattle). */
data class EnemyMovesSeen(val species: Int, val level: Int, val moves: List<Pair<Int, String>>)

data class EnemyPartyMon(
    val slot: Int, val species: Int, val level: Int, val alive: Boolean,
    /** Its personality value, and whether it is shiny for the player's ids (the Nuzlocke shiny clause). */
    val pid: Long = 0L, val shiny: Boolean = false,
)

/**
 * id -> (amount, isPercentage), from the reference tracker's
 * MiscData.HealingItems so the numbers match what the PC tracker shows.
 */
/** MiscData.PPItems: Ether, Max Ether, Elixir, Max Elixir, Leppa Berry. */
internal val PP_ITEMS: Set<Int> = setOf(34, 35, 36, 37, 138)

/**
 * Ruby/Sapphire RouteData keys the reference writes without "+ offset": Emerald-numbered, so one
 * map early for the game (tools/trainer-data/convert_route_info.py checks the set against the Lua).
 */
internal val RS_KEYED_AS_EMERALD: Set<Int> = setOf(108, 109, 110, 111, 112, 113, 114, 115, 274)

/**
 * MiscData.BattleItems (MiscData.lua:530-549): the Blue, Yellow and Red Flute (39 to 41), then Guard Spec., Dire Hit and
 * the X items (73 to 79). Only the flutes were here, under the X items' names, so Heals in Bag's Battle tab listed only
 * flutes and filed the X items under Other (rc32 audit P2 #129).
 */
internal val BATTLE_ITEMS: Set<Int> = setOf(39, 40, 41) + (73..79)

/** X Sp. Def, which the Nat. Dex expansion adds to MiscData.BattleItems (NatDexExtension.lua:4765-4771, addNewBattleItems). */
internal const val NATDEX_X_SP_DEF = 82

/** MiscData.StatusItems: item id to the status it cures, "All" for the cure-alls. */
internal val STATUS_ITEMS: Map<Int, String> = mapOf(
    14 to "Poison", 15 to "Burn", 16 to "Freeze", 17 to "Sleep", 18 to "Paralyze",
    19 to "All", 23 to "All", 32 to "All", 38 to "All",
    133 to "Paralyze", 134 to "Sleep", 135 to "Poison", 136 to "Burn", 137 to "Freeze", 140 to "Confusion", 141 to "All",
)

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
/** RouteData.Rods: the rod item ids. */
internal val RODS = mapOf(262 to "Old Rod", 263 to "Good Rod", 264 to "Super Rod")

/**
 * RouteData.getEncounterAreaByTerrain. [rsFirstBattle] is the reference's
 * "versiongroup == 1" (Ruby, Sapphire, Emerald), where the first battle's flag
 * marks a static encounter.
 */
internal fun encounterAreaByTerrain(terrainId: Int, battleFlags: Long, rsFirstBattle: Boolean): String? {
    if (terrainId < 0 || terrainId > 19) return null
    val flags = battleFlags
    val safari = (flags shr 7) and 1L == 1L
    if (flags > 4 && !safari) {
        val first = (flags shr 4) and 1L == 1L
        val staticFlags = flags shr 10
        return when {
            (flags shr 3) and 1L == 1L -> "Trainer"
            first && rsFirstBattle -> "Static"
            staticFlags > 0 -> "Static"
            else -> "Walking"
        }
    }
    return when (terrainId) {
        3 -> "Underwater"
        4, 5 -> "Surfing"
        else -> "Walking"
    }
}

/** RouteData.OrderedEncounters. */
val ORDERED_ENCOUNTERS = listOf("Walking", "Surfing", "Underwater", "Static", "RockSmash", "Super Rod", "Good Rod", "Old Rod")

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
    /** Id for a type name as the chips show it, or null. */
    fun idOf(name: String): Int? = names.entries.firstOrNull { it.value.equals(name.trim(), ignoreCase = true) }?.key

    /** The 17 real types, in tracker display order. Excludes the unused slot 9. */
    val ALL = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17)

    /**
     * The types a game has, attacking and defending: [ALL], then Fairy on the Nat. Dex expansion, last, as its
     * tracker adds it (NatDexExtension.lua addNewTypes: CoverageCalcScreen.OrderedTypeKeys gains FAIRY). Every walk
     * of the chart used [ALL] alone, so there Fairy was never counted as an attacker (rc33 audit P1 #70).
     */
    fun typesFor(natDex: Boolean): List<Int> = if (natDex) ALL + 18 else ALL

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

    /** Multiplier against a possibly dual-typed defender; [natDex], the Nat. Dex expansion's chart. */
    fun effect(attack: Int, t1: Int, t2: Int, natDex: Boolean = false): Double =
        effect(attack, t1, false, natDex) * (if (t2 == t1) 1.0 else effect(attack, t2, false, natDex))

    /**
     * The Gen 1 tracker's chart (Ironmon-gen-tracker data/MoveData.lua,
     * TypeToEffectiveness, read 2026-09-08) is this chart with three Gen 1
     * rules kept: Poison hits Bug for 2x, Bug hits Poison for 2x, Ghost does
     * nothing to Psychic. Everything else in that file matches Gen 3.
     *
     * One cell follows the game instead of that file: Ice on Fire is 1x. Red, Blue and Yellow have no ICE, FIRE row
     * (pokered and pokeyellow data/types/type_matchups.asm, Ice rows at :13, :33-36 and :83), so Charizard and
     * Moltres take 2x from Ice; the Gen 1 reference copies Gen 2's 0.5 (MoveData.lua:69 `ice = { fire = 0.5 }`;
     * pokecrystal type_matchups.asm:42 is where it begins). Blake's "correct values" ruling on reference bugs,
     * 2026-09-29 (rc32 audit P2 #128). With it the whole chart matches the two disassemblies cell for cell.
     */
    private val gen1Overrides: Map<Int, Map<Int, Double>> = mapOf(
        3 to mapOf(6 to 2.0),    // POISON -> BUG
        6 to mapOf(3 to 2.0),    // BUG -> POISON
        7 to mapOf(14 to 0.0),   // GHOST -> PSYCHIC
        15 to mapOf(10 to 1.0),  // ICE -> FIRE
    )

    /**
     * The Nat. Dex expansion's chart (NatDexExtension.lua updateMoveData, read 2026-10-02) is Gen 6's: Steel no
     * longer resists Ghost or Dark. Its Fairy cells are already in [chart], which no other Gen 3 game reaches.
     */
    private val natDexOverrides: Map<Int, Map<Int, Double>> = mapOf(
        7 to mapOf(8 to 1.0),    // GHOST -> STEEL
        17 to mapOf(8 to 1.0),   // DARK -> STEEL
    )

    fun effect(attack: Int, defend: Int, gen1: Boolean, natDex: Boolean = false): Double = when {
        gen1 -> gen1Overrides[attack]?.get(defend) ?: effect(attack, defend)
        natDex -> natDexOverrides[attack]?.get(defend) ?: effect(attack, defend)
        else -> effect(attack, defend)
    }

    /**
     * TypeDefensesScreen's buckets (PokemonData.getEffectiveness): every
     * attacking type that is not neutral against a [t1]/[t2] defender, keyed
     * by multiplier, in the screen's order 0x, 1/4x, 1/2x, 2x, 4x. Type names
     * as the chips show them.
     */
    fun defenses(t1: Int, t2: Int, gen1: Boolean = false, natDex: Boolean = false): Map<Double, List<String>> {
        val out = linkedMapOf(0.0 to ArrayList<String>(), 0.25 to ArrayList(), 0.5 to ArrayList(), 2.0 to ArrayList(), 4.0 to ArrayList())
        for (atk in typesFor(natDex)) {
            val e = effect(atk, t1, gen1, natDex) * (if (t2 == t1) 1.0 else effect(atk, t2, gen1, natDex))
            out[e]?.add(name(atk))
        }
        return out.filterValues { it.isNotEmpty() }
    }
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
    /**
     * Every opposing Pokemon's moves seen this battle, the doubles partner's too, for the run's tracked moves: the
     * reference's Tracker.TrackMove runs for whichever opposing battler used the move (Battle.lua:432-438).
     */
    val enemyMovesThisBattle: List<EnemyMovesSeen> = emptyList(),
    /**
     * Program.GameData.EnemyTeam: every Pokemon of gEnemyParty while a battle is
     * on, wild or trainer, with its party slot. Tracker.recordLastLevelsSeen
     * records each one's level when the battle ends (Battle.lua:816).
     */
    val enemyParty: List<EnemyPartyMon> = emptyList(),
    /**
     * The party slots of the opposing battlers on the field, battler 1 then (in
     * doubles) battler 3, from gBattlerPartyIndexes; empty where that address is
     * unknown. An opposing Pokemon is counted once per battle by its slot.
     */
    val enemyOnField: List<Int> = emptyList(),
    /** A wild battle: the Poke Ball chance the move header shows, 0-100. Null otherwise. */
    val catchPercent: Int? = null,
    /** Pokemon Center heals plus rests at home, from the game's statistics; PcHeals watches it. */
    val centerHealsStat: Int = 0,
    /** A Pokemon summary is open in the game right now (sMonSummaryScreen). */
    val summaryOpen: Boolean = false,
    /** PokemonData.isGameDataRandomized; true where it cannot be told. */
    val gameDataRandomized: Boolean = true,
    /** Which parts the randomizer changed; null while unknown. The information rules read it. */
    val randomized: RandomizedFlags? = null,
    /** The carousel's last attack (DamageWatch): the enemy's move, and the damage it did; null when it is not time to show it. */
    val lastAttackMove: String? = null,
    val lastAttackDamage: Int = 0,
    /** The same move's id (Battle.lastEnemyMoveId), 0 when none; Calc Atk fills its formula from it. */
    val lastAttackMoveId: Int = 0,
    /** A double battle, where the reference reads "Total received" in place of the move. */
    val lastAttackTeams: Boolean = false,
    /** Player overworld tile coordinates, or null when unreadable. */
    val playerX: Int? = null,
    val playerY: Int? = null,
    /**
     * BattleDetailsScreen.Data.DetailsSummary: for battlers 0 to 3, the first battle detail that
     * concerns it (its own, then its side's, then the field's), trimmed, or "" for none
     * (BattleDetailsScreen.summarizeDetails). Empty outside a battle or where the addresses are
     * not pinned. The carousel's battle line shows the viewed battler's.
     */
    val battleSummaries: List<String> = emptyList(),
    /** Active battle weather ("RAIN", "SUN", "SANDSTORM", "HAIL"), null outside
     *  battle, when clear, or when the bits do not match a known mask. */
    val weather: String? = null,
    /** gBattleWeather as the game holds it, null outside battle: Calc Atk compares exact values. */
    val weatherWord: Int? = null,
    /** Gym badges as 8 bits, badge 1 in bit 0. */
    val badges: Int = 0,
    val badgeSet: String = "FRLG",
    /** Gold, Silver and Crystal: the Johto League is beaten (wStatusFlags' Hall of Fame bit), for Survival's Kanto heals. */
    val leagueBeaten: Boolean = false,
    /**
     * The game, as GameMap.routeVersion names it ("firered", "leafgreen", "emerald", "ruby", "sapphire"; a Nat. Dex build's
     * base game; empty where unknown), for the form a retail game draws Deoxys in on the Walking Pals icon (PalForms).
     */
    val routeVersion: String = "",
    /** Steps left on the active repel, 0 when none is running, and the length it was (Program.ActiveRepel). */
    val repelSteps: Int = 0,
    val repelDuration: Int = 100,
    /** RouteData.Locations.IsInHallOfFame for the current map. */
    val inHallOfFame: Boolean = false,
    /** Healing carried, as a percentage of the lead's max HP, and item count -
     *  the PC tracker's "Heals: 44% HP (7)" line. */
    val healPercent: Int = 0,
    val healCount: Int = 0,
    /** The HP that healing adds up to, rounded per item as the reference does: "Show heals as whole number" (HealTotals). */
    val healHp: Int = 0,
    /** Current map id, or null when it cannot be read. */
    val mapId: Int? = null,
    /**
     * Something the tracker is about to show waits on the next read: a map id seen once and not yet adopted (it is
     * adopted on its second read), or a battle that has begun whose data is not ready yet (the reference's DataStart
     * moment, gBattleMainFunc at the intro's party summary or the action menu). The app reads again soon
     * (TrackerPoll.gbaWait) so the route line and the battle's weather show as soon as the game has them.
     */
    val settling: Boolean = false,
    /** The map's name, from the reference's RouteData. */
    val routeName: String? = null,
    /** Species that can be encountered here, from the same table. */
    val routeSpecies: List<Int> = emptyList(),
    /** Trainer ids stationed on this map, from the reference's route data. */
    val routeTrainers: List<Int> = emptyList(),
    /**
     * The carousel's Trainers defeated line, as TrackerScreen.lua:879-896 counts it: the trainers beaten and the total
     * across the map's whole combined area (Mt. Moon's three floors, the S.S. Anne...) or the map alone, leaving out the
     * rivals this run will not face (Program.getDefeatedTrainersByCombinedArea and getDefeatedTrainersByLocation,
     * Program.lua:1546-1581; TrainerData.shouldUseTrainer, TrainerData.lua:304-316). See [GbaTracker.trainersInArea].
     */
    val routeTrainersDefeated: Int = 0,
    /** The total for [routeTrainersDefeated]: 0 where the tracker counts none (the carousel then falls back to [routeTrainers]). */
    val routeTrainersTotal: Int = 0,
    /** How many of those are gym leaders, Elite 4 or bosses. */
    val routeBosses: Int = 0,
    /** gTrainerBattleOpponent_A during a trainer battle, for the Trainer Info screen. */
    val opponentTrainerId: Int? = null,
    /** Total steps walked this run, game stat 5. */
    val steps: Int = 0,
    /** True only in the map where the starter is chosen. */
    val inLab: Boolean = false,
    /** The starter whose ball is being confirmed in the lab, for "Show starter ball info". */
    val starterOffered: Int? = null,
    val starterBase: BaseStats? = null,
    /** Ability revealed by a battle-script activation this tick:
     *  species to ability name. The reference's Tracker.TrackAbility. */
    val abilityRevealed: Pair<Int, String>? = null,
    /**
     * Battle.CurrentRoute.encounterArea for this wild battle: "Walking",
     * "Surfing", "Underwater", "Static", "RockSmash", "Old Rod", "Good Rod" or
     * "Super Rod", fixed when the battle starts. Null outside a wild battle.
     */
    val encounterArea: String? = null,
    /** Every reveal caught since the last read, oldest first, including the
     *  ones [GbaTracker.pollAbilityTrigger] saw between reads. */
    val abilitiesRevealed: List<Pair<Int, String>> = emptyList(),
    /**
     * Battle.isGhost (Battle.lua:371): a FireRed or LeafGreen battle against a
     * Pokemon Tower ghost without the Silph Scope. The enemy is then the
     * reference's ghost stand-in, no move, ability or encounter is recorded
     * for it (Battle.lua:389, 505), and no move shows its effectiveness, yours
     * included (DataHelper.lua:337).
     */
    val isGhostBattle: Boolean = false,
    /** The player's own battlers in this battle, species to ability. The
     *  reference tracks these on every battle update with no trigger needed
     *  (Battle.lua ~484-504), so an ability you have fielded is known when that
     *  species turns up against you. */
    val ownAbilities: List<Pair<Int, String>> = emptyList(),
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
    /** What the Nuzlocke rules engine reads beyond the rest of this state (NuzlockeReads); null on a Game Boy game or when the reads failed. */
    val nuz: NuzlockeReads? = null,
    /**
     * In a battle, the index in [party] of the player's Pokemon on the field (gBattlerPartyIndexes[0] on Gen 3,
     * wPlayerMonNumber on Gen 1, wCurBattleMon on Gen 2); 0 otherwise. Slot 1 is not it after a switch, or when the
     * lead was fainted at the start (rc33 audit P1). The reference views this one (Battle.getViewedPokemon).
     */
    val ownOnField: Int = 0,
    /**
     * A double battle (gBattlersCount 4, Battle.numBattlers): a right-hand battler of each side is on the field too,
     * battler 2 yours and battler 3 the opponent's (Combatants.RightOwn and RightOther, Battle.lua:288-297).
     */
    val doubles: Boolean = false,
    /**
     * In a double battle, the index in [party] of your right-hand battler (gBattlerPartyIndexes[2]), its stat stages read
     * in; -1 otherwise, or when its party slot is not one of [party] (a partner's Pokemon past the party's size).
     */
    val ownRightOnField: Int = -1,
    /** The heals as a share of [ownRight]'s max HP: what the strip shows while it is the one viewed (Program.recalcLeadPokemonHealingInfo). */
    val ownRightHeals: HealTotals = HealTotals.NONE,
    /** The opponent's right-hand battler in a double battle (battler 3); null otherwise. */
    val enemyRight: EnemyInfo? = null,
    /**
     * Battle.EnemyTrainersToHideAlly (Battle.lua:68-75): a double battle beside a partner whose Pokemon the tracker never
     * shows, Emerald's Space Center fight with Steven against Tabitha (514) and Maxie (734).
     */
    val allyHidden: Boolean = false,
) : RunView {
    /** The player's Pokemon on the field in a battle, the lead otherwise: the own card, Calc Atk and the matchups use it. */
    val onField: TrackedMon? get() = fieldMonOf(party, ownOnField)

    /** Your right-hand battler in a double battle, never an Egg; null in a single battle or where it is not in [party]. */
    val ownRight: TrackedMon? get() = party.getOrNull(ownRightOnField)?.takeIf { !it.mon.isEgg }

    /**
     * The lead: slot 1, or the first Pokemon after it that is not an Egg, in a battle too. Tracker.getPokemon(1, true)
     * (Tracker.lua:105-140), which the carousel's early route encounters and Trainer Info's level colour read
     * (TrackerScreen.lua:664, TrainerInfoScreen.lua:251). Those took slot 1 as it was, an Egg's level included
     * (RC35-NOTICED N #34).
     */
    val lead: TrackedMon? get() = fieldMonOf(party, 0)

    /**
     * Program.ActiveRepel.shouldDisplay (Program.lua:249): a repel is running,
     * the player is on a map, not in a battle and not in the Hall of Fame.
     * The option itself ("Display repel usage") is the app's to add.
     */
    val repelVisible: Boolean get() = repelSteps > 0 && mapId != null && !inBattle && !inHallOfFame

    override val enemySpeciesId: Int get() = enemy?.species ?: -1
    override val outcome: RunOutcome? get() = when (gameOver) {
        GameOver.WON -> RunOutcome.WON; GameOver.LOST -> RunOutcome.LOST; null -> null
    }
}

/**
 * The Pokemon at [ownOnField] in [party], or the lead: never an Egg. Tracker.getPokemon(slot, true) skips eggs to the first
 * Pokemon that is not one (Tracker.lua:105-140, excludeEggs defaults to true), so with an Egg in slot 1 the PC tracker's
 * lead is slot 2. The card drew the Egg's species, moves and ability, which the game keeps hidden, and the heals were
 * worked out on its HP (rc32 audit P2 #136). An Egg is never sent out, so one at [ownOnField] is not on the field either.
 */
internal fun fieldMonOf(party: List<TrackedMon>, ownOnField: Int): TrackedMon? =
    party.getOrNull(ownOnField)?.takeIf { !it.mon.isEgg } ?: party.firstOrNull { !it.mon.isEgg } ?: party.firstOrNull()

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
) : ActionMenuGate {
    companion object {
        /** PokemonData.Values.GhostId. */
        const val GHOST_ID = 413
        /** The same, as the Nat. Dex extension sets it (NatDexExtension.lua:16912). */
        const val NATDEX_GHOST_ID = 1285
        /** Resources.TrackerScreen.UnidentifiedGhost, English. */
        const val GHOST_NAME = "Ghost"
        /** Gen 3's "???" type, PokemonData.Types.UNKNOWN in the reference's TypeIndexMap. */
        const val UNKNOWN_TYPE = 9
        /** status2's bit for a battler that used Transform (CFRU include/constants/battle.h:121, as pokeemerald's). */
        internal const val STATUS2_TRANSFORMED = 0x00200000L
        /** Trace's ability id (AbilityData.Values.TraceId). */
        internal const val TRACE = 36
        /** Psycho Boost, the last of the five games' 354 moves. */
        const val VANILLA_LAST_MOVE = 354
        /** A starter ball's place, on FireRed's lab table or Hoenn's starter screen, from the left. */
        val BALL_NAMES = listOf("LEFT", "MIDDLE", "RIGHT")
        /**
         * Battle.EnemyTrainersToHideAlly (Battle.lua:68-75), by the reference's game number: the opponents whose double
         * battle hides your partner's Pokemon. Only Emerald's Space Center fight beside Steven, Tabitha (514) and
         * Maxie (734).
         */
        internal val ENEMY_TRAINERS_TO_HIDE_ALLY: Map<Int, Set<Int>> = mapOf(1 to emptySet(), 2 to setOf(514, 734), 3 to emptySet())
        /** The Hoenn starter screen's three ball places, x and y, left to right (sPokeballCoordinates). */
        internal val HOENN_BALL_PLACES = byteArrayOf(60, 64, 120, 88, 180.toByte(), 64)
    }

    /** GameOverScreen.LossConditions: which faint ends the run. The app sets it from its options. */
    @Volatile var lossCondition: LossCondition = LossCondition.LEAD

    /**
     * Tracker.getAbilities: the abilities tracked for a species so far, by name as [abilityName]
     * gives them. The app keeps them (StatMarks) and sets this; Battle Details reads it.
     */
    @Volatile var trackedAbilities: (species: Int) -> List<String> = { emptyList() }

    /**
     * Whether species ids follow the expansion's extended numbering, and so
     * whether the bundled sprite pack may be indexed with them. Exposed
     * narrowly because the map itself is module-internal.
     */
    val expandedSpeciesIds: Boolean get() = map.expandedSpeciesIds

    /** GameMap.nameSet: "maxdex" on MaxDex 1.0, whose icons and data the app picks by it; "" otherwise. */
    val nameSet: String get() = map.nameSet

    /**
     * Species ids by the names a randomizer log prints, upper case, where the log's names are not the lists': MaxDex's
     * log names Pokemon as its ROM does, cut to ten letters ("DragoniteM", "Flechinder"), from the table its Game Freak
     * header names at 0x144. Empty on every other build, whose log names are the ones the tracker shows.
     */
    fun logSpeciesIds(): Map<String, Int> {
        if (map.nameSet != "maxdex") return emptyMap()
        val ptr = memory.read(0x08000144L, 4)
        val table = if (ptr.size == 4) ptr.u32(0) else return emptyMap()
        if (table !in 0x08000000L..0x09FFFFFFL) return emptyMap()
        val out = HashMap<String, Int>()
        for (id in 1 until map.spriteCount) {
            val b = memory.read(table + id * 11L, 11)
            val name = if (b.isEmpty()) "" else Gen3Text.decode(b).trim().uppercase()
            if (name.isNotEmpty() && name != "?") out.putIfAbsent(name, id)
        }
        return out
    }

    /** This game's types by name, Fairy last on the Nat. Dex expansion (Gen3Types.typesFor): the Coverage Calculator's. */
    val typeNames: List<String> get() = Gen3Types.typesFor(map.expandedSpeciesIds).map(Gen3Types::name)

    // Filled from more than one thread: the poll and the stream's dex walk on background threads, the panel's lookups on
    // the main one. Plain HashMaps filled with getOrPut there could lose or corrupt entries (rc32 audit P3 #51); the
    // concurrent map's getOrPut is safe across threads. Their values are never null (spriteCache holds nulls and stays).
    private val speciesNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val moveNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val baseStatsCache = java.util.concurrent.ConcurrentHashMap<Int, BaseStats>()
    private val abilityNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val itemNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val moveDataCache = java.util.concurrent.ConcurrentHashMap<Int, IntArray>()
    private val learnsetCache = java.util.concurrent.ConcurrentHashMap<Int, List<Pair<Int, Int>>>()

    // Enemy moves revealed this encounter; keyed off the enemy species so a new
    // encounter (or a switch) starts a fresh page.
    // The opponent's moves seen this battle (pollMoves, rc33 audit P1 #72), per opposing party slot, in the order used.
    private val moveWatch = EnemyMoveWatch()
    private val battleMoves = HashMap<Int, LinkedHashSet<Int>>()

    init {
        if (map.namesFromLists) {
            loadList("/natdex/species.tsv", speciesNameCache)
            loadList("/natdex/moves.tsv", moveNameCache)
        }
        // MaxDex 1.0: its own species, moves and abilities (tools/trainer-data/convert_maxdex.py).
        if (map.nameSet == "maxdex") {
            loadList("/maxdex/species.tsv", speciesNameCache)
            loadList("/maxdex/moves.tsv", moveNameCache)
            loadList("/maxdex/abilities.tsv", abilityNameCache)
        }
    }

    /**
     * The highest move id the game has: Psycho Boost's 354 on the five games, the last line of natdex/moves.tsv (847)
     * on the Nat. Dex builds and of maxdex/moves.tsv (841) on MaxDex. The log viewer stopped at 354 on every build, so a
     * Nat. Dex run's newer moves (Roost, Dragon Pulse) were never drawn as same-type (rc32 audit P2 #28); MaxDex, whose
     * list comes through its name set, still stopped there, and its log's U-turn read "U-Turn" (rc34).
     */
    val lastMoveId: Int =
        if (map.namesFromLists || map.nameSet == "maxdex") moveNameCache.keys.maxOrNull() ?: VANILLA_LAST_MOVE else VANILLA_LAST_MOVE

    private fun loadList(resource: String, into: MutableMap<Int, String>) {
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
        // The GhostId is past the species table on every build: vanilla 413
        // reads the move name table's "POUND", Nat. Dex 1285 has no list entry.
        if (species == ghostSpeciesId) return@getOrPut GHOST_NAME
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
        if (map.moveCategoryByte) return splitMoveData(move)
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

    /**
     * MaxDex's 12-byte move entry ([GameMap.moveCategoryByte]): u16 effect, power +2, type +3, category +4, accuracy +5,
     * PP +6, priority +9, flags +10. The same [power, type, accuracy, PP, priority, flags] as [moveData], and the
     * category byte seventh (0 physical, 1 special, 2 status), which [categoryOf] reads.
     */
    private fun splitMoveData(move: Int): IntArray? = moveDataCache.getOrPut(move) {
        val b = memory.read(map.battleMoves + move.toLong() * 12, 11)
        if (b.size < 11) intArrayOf(-1, -1, -1, -1, 0, 0, 0)
        else intArrayOf(b.u8(2), b.u8(3), b.u8(5), b.u8(6), b.u8(9).let { if (it >= 128) it - 256 else it }, b.u8(10), b.u8(4))
    }.takeIf { it[0] >= 0 }

    /** A move row's category: the ROM's own byte where the build has one, else Gen 3's rule (the type decides). */
    private fun categoryOf(d: IntArray): String = if (d.size > 6) when (d[6]) {
        0 -> "PHY"
        1 -> "SPE"
        else -> "STA"
    } else gen3Category(d[1], d[0])

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
            category = categoryOf(d), priority = d[4].takeIf { it in -7..7 }, contact = (d[5] and 1) != 0,
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
                    category = d?.let { categoryOf(it) },
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

    /**
     * Program.getNextLevelExp: (experience into this level, experience the
     * level spans), from gExperienceTables[growthRate][level..level+1]. Null
     * at level 100, or where the table is not pinned for this build.
     */
    fun expProgress(mon: PokemonDecoder.Mon, base: BaseStats?): Pair<Int, Int>? {
        if (map.expTables == 0L || base == null || mon.level !in 1..99) return null
        val at = map.expTables + base.growthRate.toLong() * 0x194 + mon.level.toLong() * 4
        val b = memory.read(at, 8)
        if (b.size < 8) return null
        val lv = b.u32(0); val next = b.u32(4)
        if (next <= lv) return null
        return ((mon.exp - lv).coerceIn(0, next - lv).toInt()) to (next - lv).toInt()
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
            growthRate = if (map.growthRateOffset < stride) b.u8(map.growthRateOffset) else 0,
            baseFriendship = if (map.baseFriendshipOffset < stride) b.u8(map.baseFriendshipOffset) else EvoText.DEFAULT_BASE,
            genderRatio = if (map.genderRatioOffset < stride) b.u8(map.genderRatioOffset) else 255,
            catchRate = b.u8(8),
        )
    }.takeIf { it.bst > 0 }

    private val spriteCache = HashMap<Int, IntArray?>()

    /** 64x64 ARGB front sprite from the ROM, or null. Cached per species. */
    fun sprite(species: Int): IntArray? = spriteCache.getOrPut(species) {
        runCatching { SpriteDecoder.frontSprite(memory, map, species) }.getOrNull()
    }

    /**
     * Where this game keeps its pictures (Gen3Pictures), read once. None on the builds whose card draws the shipped
     * menu icons (expandedSpeciesIds: Nat. Dex and MaxDex), since a Gen 3 game keeps no shiny icon.
     */
    private val pictureTables: Gen3Pictures.Tables? by lazy {
        if (map.expandedSpeciesIds) null else runCatching { Gen3Pictures.tables(memory, map) }.getOrNull()
    }
    private val pictureCache = HashMap<Long, Gen3Pictures.Picture?>()

    /**
     * The card's picture of a Pokemon where the game draws it otherwise than its species' plain picture (Gen3Pictures):
     * shiny, Unown's letter, Deoxys's form where [gameForm]. Your own card asks for the form, as the game draws your
     * Deoxys; the opponent's does not, as the battle draws an opponent's Deoxys in its Normal form and the PC tracker has
     * one Deoxys picture for both (Ironmon-Tracker PokemonData.lua:3723).
     * Null for every other Pokemon, and wherever the ROM gives none: the card then draws what it always has. Cached per
     * species, letter, shininess and form.
     */
    fun picture(species: Int, shiny: Boolean, personality: Long, gameForm: Boolean): Gen3Pictures.Picture? {
        val t = pictureTables ?: return null
        val letter = if (species == Gen3Pictures.UNOWN) Gen3Pictures.unownLetter(personality) else 0
        val key = (species.toLong() shl 8) or (letter.toLong() shl 2) or (if (gameForm) 2L else 0L) or (if (shiny) 1L else 0L)
        // A null is kept too (getOrPut would read the ROM again on every poll for a picture it cannot give).
        if (key in pictureCache) return pictureCache[key]
        return runCatching { Gen3Pictures.picture(memory, t, species, shiny, personality, gameForm) }.getOrNull()
            .also { pictureCache[key] = it }
    }

    data class BallOption(val ball: String, val species: Int, val name: String)

    /**
     * The three starter balls of the randomized ROM, left to right, or none when they cannot be told for certain.
     *
     * Which ball holds which entry is read, not assumed. FireRed and LeafGreen keep each ball's species in its lab
     * script, right after `setvar VAR_TEMP_1, <ball>` and `setvar VAR_TEMP_2`, and the ball number (0, 1, 2) is its
     * place on the table from the left: Bulbasaur, Squirtle, Charmander, where the table runs Bulbasaur, Charmander,
     * Squirtle. Until 2026-10-01 this named them LEFT, MIDDLE, RIGHT in table order, which put Charmander in the middle.
     * Ruby, Sapphire and Emerald keep the species in the starter screen's table, 12 bytes after the balls' places on
     * that screen, so there the order is left to right. Read in all eight ROMs, both Nat. Dex builds included. Bytes
     * that are neither (a build these addresses were not found in) give no balls.
     */
    fun starters(): List<BallOption> {
        if (map.startersBase == 0L) return emptyList()
        val at = listOf(map.startersBase, map.startersBase + map.starter2Off, map.startersBase + map.starter3Off)
        val places = if (memory.read(map.startersBase - 12, 6).contentEquals(HOENN_BALL_PLACES)) listOf(0, 1, 2)
        else at.map { addr ->
            val b = memory.read(addr - 8, 8)
            val script = b.size == 8 && b.u8(0) == 0x16 && b.u8(1) == 0x01 && b.u8(2) == 0x40 && b.u8(4) == 0 &&
                b.u8(5) == 0x16 && b.u8(6) == 0x02 && b.u8(7) == 0x40
            if (script) b.u8(3) else -1
        }
        if (places.sorted() != listOf(0, 1, 2)) return emptyList()
        val balls = at.indices.map { i ->
            val b = memory.read(at[i], 2)
            val species = if (b.size == 2) b.u16(0) else 0
            BallOption(BALL_NAMES[places[i]], species, if (speciesIsValid(species)) speciesName(species) else "")
        }
        if (balls.any { !speciesIsValid(it.species) }) return emptyList()
        return balls.sortedBy { BALL_NAMES.indexOf(it.ball) }
    }

    /**
     * Program.updateCatchingTutorial (Program.lua:1367): true while the catching tutorial runs.
     *
     * The game lends the tutor a Pokemon by putting it in the player's own party (Wally's Zigzagoon goes into slot
     * one, the real party is saved and put back after), and the battle is a wild one that ends in a catch. The
     * reference reads neither the teams nor the battle while sSpecialFlags is 3 (Program.lua:529, Battle.lua:122),
     * and once the tutorial has begun and ended it never looks again, so the same value later in the game means
     * nothing. Without this the tracker showed the Zigzagoon as the lead, and a Nuzlocke's ledger took Ralts as the
     * Route 102 encounter, with a Ralts "in a box" that never existed (2026-09-30, UX audit P0-9).
     */
    private fun updateCatchingTutorial(): Boolean {
        if (hasCompletedTutorial || map.specialFlags == 0L) return false
        val b = memory.read(map.specialFlags, 1)
        if (b.isEmpty()) return inCatchingTutorial
        val flag = b.u8(0)
        if (inCatchingTutorial && flag == 0) hasCompletedTutorial = true
        inCatchingTutorial = flag == 3
        return inCatchingTutorial
    }

    private var inCatchingTutorial = false
    private var hasCompletedTutorial = false
    /** The last state read outside the tutorial: what the tracker goes on showing while it runs. */
    private var stateBeforeTutorial: TrackerState? = null

    fun read(): TrackerState {
        // While the tutorial runs nothing is read, as in the reference: the state from before it stands.
        if (updateCatchingTutorial()) stateBeforeTutorial?.let { return it }
        return readNow().also { stateBeforeTutorial = it }
    }

    private fun readNow(): TrackerState {
        val countBytes = memory.read(map.partyCount, 1)
        val count = if (countBytes.isEmpty()) 0 else countBytes.u8(0).coerceIn(0, 6)

        var unreadable = false
        val party = ArrayList<TrackedMon>(count)
        val slots = ArrayList<Int>(count)   // the party slot of each entry of [party]
        var bagIds: Set<Int>? = null
        val evoBag = { bagIds ?: readBag().keys.also { bagIds = it } }
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
                    val header = LearnedMoves.of(learn.map { it.first }, mon.level)
                    party += TrackedMon(
                        mon = mon,
                        speciesName = speciesName(mon.species),
                        moveNames = mon.moves.map { moveName(it) },
                        base = base,
                        abilityName = abilityOf(mon, base),
                        itemName = itemName(mon.heldItem),
                        moveRows = moveRows(mon),
                        movesLearned = header.learned,
                        movesTotal = header.total,
                        nextMoveLevel = header.next,
                        statusCondition = statusName(mon.status),
                        expNow = expProgress(mon, base)?.first ?: 0,
                        expTotal = expProgress(mon, base)?.second ?: 0,
                        evo = EvoText.forOwn(
                            evolution(mon.species), mon.level, evoBag,
                            mon.friendship, base?.baseFriendship ?: EvoText.DEFAULT_BASE, friendshipRequired(),
                            TrackerPrefs.determineFriendship,
                        ),
                        picture = if (mon.isEgg) null else picture(mon.species, mon.shiny, mon.pid, gameForm = true),
                    )
                    slots += i
                }
            }
        }

        // The layout id (+0x12), and with it regionMapSectionId (+0x14) and mapType (+0x17) of struct MapHeader
        // (pokeemerald and pokefirered global.fieldmap.h): the Nuzlocke ledger counts a gift in a building for the town
        // or route it stands in (rc32 audit P2 #140).
        val header = if (map.mapHeader == 0L) null else memory.read(map.mapHeader + 0x12, 6)
        val rawMapId = if (header == null || header.size < 2) null else header.u16(0)
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
        // Only from a read whose layout id is the adopted one, so one map's name is never paired with the next map's section.
        stableMapSection = if (header != null && header.size >= 6 && rawMapId != null && rawMapId == stableMapId) header.u8(2) to header.u8(5) else null
        // Ruby and Sapphire: ids above the empty Lilycove layout (108) come down one onto the rse table.
        val mapId = stableMapId?.let { if (map.rsMapShift && it > 108) it - 1 else it }

        val inBattle = updateBattleStatus()
        // The player's Pokemon on the field (Battle.lua:276, Combatants.LeftOwn): its heals, its stat stages, and the
        // card, Calc Atk and the matchups through TrackerState.onField. Slot 1 is not it after a switch (rc33 audit P1).
        val ownOnField = if (inBattle) readOwnOnField()?.let { s -> slots.indexOf(s).takeIf { it >= 0 } } ?: 0 else 0
        // A double battle: your right-hand battler too (Battle.lua:288-292, Combatants.RightOwn), which the swap shows and
        // whose card, stages and heals are its own. Never the left one's entry, and not a slot past [party].
        val doubles = inBattle && map.battlersCount != 0L && rb(map.battlersCount) == 4
        val ownRightOnField = if (doubles) readOwnOnField(2)?.let { s -> slots.indexOf(s).takeIf { it >= 0 && it != ownOnField } } ?: -1 else -1
        // The heals are a share of the Pokemon the card shows, never an Egg's (fieldMonOf, rc32 audit P2 #136).
        val heals = readHeals(fieldMonOf(party, ownOnField)?.mon?.maxHp ?: 0)
        val rightHeals = party.getOrNull(ownRightOnField)?.takeIf { !it.mon.isEgg }?.let { readHeals(it.mon.maxHp) } ?: HealTotals.NONE
        // Tracker.resetData (Tracker.lua:550-558) takes the fishing and Rock Smash counts from the save when the tracker
        // starts. Taken at the first wild battle instead, they already held that battle's own bite, which the game counts
        // before the battle begins (CFRU wild_encounter.c:752-753), so the first rod or Rock Smash battle of every Play
        // visit was filed as Surfing or Walking (rc32 audit P3 #109). Only once the save is in: the party has a Pokemon,
        // and readGameStat reads 0 without SaveBlock1, which would make the save's own count look like a new bite.
        if (fishingStat < 0 && !inBattleScreen && party.isNotEmpty()) { fishingStat = readGameStat(12); rockSmashStat = readGameStat(19) }
        // A facility battle's hold (isFacilityBattle): the lent party as the battle shows it, then, outside the battle, held
        // until the game hands back another party (the real one) or nobody is at 0 HP, so it never outlasts the lent party.
        if (facilityHeld) {
            val ids = party.map { it.mon.pid }.toSet()
            if (inBattleScreen) facilityParty = ids
            else if (party.none { it.mon.curHp == 0 && !it.mon.isEgg } || (facilityParty != null && ids != facilityParty)) {
                facilityHeld = false; facilityParty = null
            }
        }
        if (inBattle && !lastInBattle) damageWatch.reset()
        lastInBattle = inBattle
        if (inBattle && map.takenDmg != 0L && map.battleResults != 0L && map.battlerAttacker != 0L) {
            damageWatch.tick(rb(map.battleResults + map.battleResultsTurnOffset), rb(map.battlerAttacker), rw(map.takenDmg), rw(map.battleResults + 0x24))
        }
        // In battle, battler 0's stage block belongs to the player's active
        // mon; the panel shows chevrons on both sides like the reference.
        if (inBattle && party.isNotEmpty()) {
            party[ownOnField] = party[ownOnField].copy(statStages = readStatStages(0))
            // Battle.updateStatStages(ownRightPokemon, true, false) (Battle.lua:494-502): battler 2's block is the right-hand one's.
            if (ownRightOnField >= 0) party[ownRightOnField] = party[ownRightOnField].copy(statStages = readStatStages(2))
        }
        val trainer = !isWildEncounter
        // Battle.updateTrackedInfo reads it fresh on every update while data is ready (Battle.lua:371).
        val ghost = inBattle && isGhostBattle()

        var px: Int? = null; var py: Int? = null
        saveBlock1()?.let { sb1 ->
            val pos = memory.read(sb1, 4)
            if (pos.size == 4) { px = pos.u16(0); py = pos.u16(2) }
        }

        if (inBattle && !ghost) runCatching { pollMoves() }
        val enemy = if (inBattle) { if (ghost) ghostEnemy() else readEnemy() } else null
        // The opponent's right-hand battler in a double battle (Combatants.RightOther). A ghost battle is a single one.
        val enemyRight = if (doubles && !ghost) readEnemy(3) else null
        // Battle.incrementEnemyEncounter is never reached for a ghost (Battle.lua:505), so
        // the battle has no encounter area and no route details.
        val encounterArea = if (inBattle && !trainer && !ghost) battleEncounterArea() else { currentArea = null; null }
        if (enemy != null && encounterArea != null && mapId != null) trackSafariEncounter(mapId, enemy, encounterArea)
        val enemyParty = if (inBattle) readEnemyParty() else emptyList()
        // The opponent: shiny by its own party entry, found by its personality, and Unown's letter. Deoxys in its Normal
        // form, as the battle screen draws an opponent's whatever the game (PalForms.ofEnemy too).
        val enemyCard = enemy?.let { e ->
            if (e.isGhost) e
            else e.copy(picture = picture(e.species, enemyParty.firstOrNull { it.pid != 0L && it.pid == e.pid }?.shiny == true, e.pid, gameForm = false))
        }
        // The Trainers defeated line's trainers: the map's combined area, less the rivals this run will not face.
        val areaTrainers = mapId?.let { trainersInArea(it) } ?: emptyList()

        return TrackerState(
            partyCount = count,
            party = party,
            ownOnField = ownOnField,
            doubles = doubles,
            ownRightOnField = ownRightOnField,
            ownRightHeals = rightHeals,
            enemyRight = enemyRight,
            // Battle.lua:228: the opponent is gTrainerBattleOpponent_A, read at the battle's start in the reference.
            allyHidden = doubles && trainer && readOpponentTrainerId()?.let { it in ENEMY_TRAINERS_TO_HIDE_ALLY[refGame].orEmpty() } == true,
            inBattle = inBattle,
            isWildBattle = inBattle && !trainer,
            // data.x.catchrate: PokemonData.calcCatchRate with its default ball, the Poke Ball.
            // For the ghost stand-in it is 0: calcCatchRate refuses an id that is not a species.
            catchPercent = if (inBattle && !trainer) {
                if (ghost) 0 else runCatching { catchRates()?.rows?.firstOrNull { it.ballId == 4 }?.rate }.getOrNull()
            } else null,
            lastAttackMove = if (inBattle && damageWatch.ready) moveName(damageWatch.lastEnemyMoveId) else null,
            lastAttackMoveId = if (inBattle && damageWatch.ready) damageWatch.lastEnemyMoveId else 0,
            lastAttackDamage = damageWatch.damageReceived,
            lastAttackTeams = inBattle && map.battlersCount != 0L && rb(map.battlersCount) > 2,
            enemyTeam = if (inBattle && trainer) enemyParty.map { it.alive } else emptyList(),
            enemyParty = enemyParty,
            enemyOnField = if (inBattle) readEnemyOnField() else emptyList(),
            enemy = enemyCard,
            enemyMovesThisBattle = if (inBattle && !ghost) enemyMovesThisBattle(enemyParty) else emptyList(),
            abilityRevealed = if (inBattle) readAbilityTrigger() else null,
            encounterArea = encounterArea,
            abilitiesRevealed = drainReveals(live = inBattle && !ghost),
            // Your own battlers' abilities are tracked in a ghost battle too: that
            // block sits outside the reference's isGhost check (Battle.lua:482-503).
            ownAbilities = if (inBattle) readOwnAbilities() else emptyList(),
            isGhostBattle = ghost,
            playerX = px,
            playerY = py,
            weather = if (inBattle) readWeather() else null,
            weatherWord = if (inBattle && map.weather != 0L) memory.read(map.weather, 2).takeIf { it.size == 2 }?.u16(0) else null,
            battleSummaries = if (inBattle) runCatching { battleDetails()?.let { d -> (0..3).map { d.summary(it).trim() } } }.getOrNull() ?: emptyList() else emptyList(),
            badges = readBadges(),
            badgeSet = map.badgeSet,
            routeVersion = map.routeVersion,
            repelSteps = readRepelSteps().also { repelDuration = RepelRules.duration(it, repelDuration) },
            repelDuration = repelDuration,
            inHallOfFame = mapId != null && rawMapId(mapId) in map.hallOfFameMapIds,
            healPercent = heals.percent,
            healCount = heals.count,
            healHp = heals.hp,
            mapId = mapId,
            settling = (inBattleScreen && !battleDataReady) || (rawMapId != null && rawMapId != stableMapId),
            inLab = mapId != null && mapId in map.labMapIds,
            starterOffered = if (count == 0 && mapId != null && mapId in map.labMapIds) starterOffered() else null,
            starterBase = if (count == 0 && mapId != null && mapId in map.labMapIds) starterOffered()?.let { baseStats(it) } else null,
            routeName = mapId?.let { routeInfo(it)?.first },
            routeSpecies = mapId?.let { routeInfo(it)?.second } ?: emptyList(),
            routeTrainers = mapId?.let { trainersOnRoute(it) } ?: emptyList(),
            routeTrainersDefeated = areaTrainers.count { trainerDefeated(it) },
            routeTrainersTotal = areaTrainers.size,
            opponentTrainerId = if (inBattle && trainer) readOpponentTrainerId()?.also { id -> learnRival(id) } else null,
            routeBosses = mapId?.let { m ->
                trainersOnRoute(m).count { trainerGroup(it) in BOSS_GROUPS }
            } ?: 0,
            steps = readGameStat(5),
            // Constants.GAME_STATS USED_POKECENTER (15) + RESTED_AT_HOME (16), for "Track PC Heals".
            centerHealsStat = readGameStat(15) + readGameStat(16),
            summaryOpen = map.monSummaryScreen != 0L && rb(map.monSummaryScreen) != 0,
            gameDataRandomized = randomized()?.gameData ?: true,
            randomized = randomized(),
            gameOver = readGameOver(party, inBattle),
            diagnostics = "%s  party=%08X count=%08X base=%08X"
                .format(map.name, map.party, map.partyCount, map.baseStats),
            unreadable = unreadable && party.isEmpty(),
            nuz = readNuzlocke(inBattle, trainer, enemy, enemyParty),
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

    /**
     * The map's name and every species that can appear there. The species come from
     * the per-version RouteData (internal ids, the running version's picks); the old
     * shared table held national dex numbers, so every Hoenn species in it was wrong.
     * The name comes from this version's RouteData.Info as the tracker numbers maps
     * ([routeInfoTable]). The shared table it used to come from is Emerald's, so Ruby's
     * hideout read "Aqua Hideout" where the reference says "Magma Hideout"
     * (RouteData.lua:5936-5946), and the encounter table is keyed by the reference's
     * raw R/S ids, where its gym and Elite Four keys are off by one (parity audit,
     * 2026-09-28).
     */
    fun routeInfo(mapId: Int): Pair<String, List<Int>>? {
        val info = routeInfoTable[mapId]
        val old = routes[mapId]
        val enc = routeKey(mapId)?.let { routeEnc[it] }
        if (info == null && old == null && enc == null) return null
        val name = info?.first?.takeIf { it.isNotBlank() } ?: old?.first?.takeIf { it.isNotBlank() } ?: enc?.first ?: ""
        val mons = enc?.second?.values?.flatten()?.map { it.id }?.distinct() ?: old?.second ?: emptyList()
        return name to mons
    }

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
        routeEncounters(mapId).mapValues { (_, mons) -> mons.map { it.id } }.ifEmpty { routeAreas[mapId] ?: emptyMap() }

    /** One vanilla encounter slot group: RouteData.getEncounterAreaPokemon's entry. */
    data class RouteMon(val id: Int, val rate: Double, val minLv: Int, val maxLv: Int)

    /**
     * RouteData.Info for this exact version (gen3/routeenc-<version>.tsv, made by
     * tools/trainer-data/convert_route_encounters.py by running the reference's
     * own setup), keyed by the map id as the game reports it.
     */
    private val routeEnc: Map<Int, Pair<String, LinkedHashMap<String, List<RouteMon>>>> by lazy {
        val out = HashMap<Int, Pair<String, LinkedHashMap<String, List<RouteMon>>>>()
        if (map.routeVersion.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/routeenc-${map.routeVersion}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#")) return@forEach
                    val p = line.split('\t')
                    val id = p.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val areas = LinkedHashMap<String, List<RouteMon>>()
                    p.getOrNull(2)?.takeIf { it.isNotBlank() }?.split('|')?.forEach { chunk ->
                        val mons = chunk.substringAfter('=').split(',').mapNotNull { e ->
                            val f = e.split(':')
                            val sp = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                            RouteMon(sp, f.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                                f.getOrNull(2)?.toIntOrNull() ?: 0, f.getOrNull(3)?.toIntOrNull() ?: 0)
                        }
                        if (mons.isNotEmpty()) areas[chunk.substringBefore('=')] = mons
                    }
                    out[id] = (p.getOrNull(1) ?: "") to areas
                }
            }
        out
    }

    /**
     * The state's map id is Emerald-numbered on Ruby/Sapphire (rsMapShift); the
     * reference looks RouteData up with the id the game reports, so undo it here.
     */
    fun rawMapId(mapId: Int): Int = if (map.rsMapShift && mapId >= 108) mapId + 1 else mapId

    /** The inverse of [rawMapId]: the id the rest of the tracker keys on. */
    fun mapIdFromRaw(raw: Int): Int = if (map.rsMapShift && raw > 108) raw - 1 else raw

    /**
     * The RouteData key for the tracker's [mapId], or null when the reference has none for it.
     * The reference keys Ruby/Sapphire maps by the raw id the game reports ("[id + offset]"),
     * except the gyms, Elite Four rooms and Route 124 Water, which it writes without the offset
     * and so files one map early (RouteData.lua:4467-4529, 4103; RS_KEYED_AS_EMERALD). Blake,
     * 2026-09-29: keep the game-correct numbering. Those are looked up under the map they name,
     * so Mossdeep Gym no longer reads "Sootopolis Gym 1F", and the one map whose raw id Route 124
     * Water's key takes (273) has no entry instead of Route 124's underwater encounters.
     */
    fun routeKey(mapId: Int): Int? {
        if (!map.rsMapShift || mapId in RS_KEYED_AS_EMERALD) return mapId
        return rawMapId(mapId).takeIf { it !in RS_KEYED_AS_EMERALD }
    }

    /** The inverse of [routeKey]: the tracker's map id for a RouteData key (the route look-up lists keys). */
    fun mapIdFromRouteKey(key: Int): Int = if (map.rsMapShift && key in RS_KEYED_AS_EMERALD) key else mapIdFromRaw(key)

    /** RouteData.Info[mapId][area] for every area the map has, in RouteData.OrderedEncounters order. */
    fun routeEncounters(mapId: Int): Map<String, List<RouteMon>> =
        routeKey(mapId)?.let { routeEnc[it] }?.second ?: emptyMap()

    /** The same, by RouteData key (the route look-up lists those). */
    fun routeEncountersRaw(raw: Int): Map<String, List<RouteMon>> = routeEnc[raw]?.second ?: emptyMap()

    fun routeNameRaw(raw: Int): String? = routeEnc[raw]?.first

    /** RouteData.AvailableRoutes: maps with any encounter area, by map id, as (raw id, name). */
    fun routeLookupList(): List<Pair<Int, String>> =
        routeEnc.entries.filter { it.value.second.isNotEmpty() && it.value.first.isNotBlank() }
            .sortedBy { it.key }.map { it.key to it.value.first }

    /**
     * GameSettings.game, the reference's game number: 1 Ruby/Sapphire, 2 Emerald, 3 FireRed and
     * LeafGreen; 0 when unknown. A Nat. Dex build counts as its base game (its routeVersion).
     */
    private val refGame: Int get() = when (map.routeVersion) {
        "ruby", "sapphire" -> 1
        "emerald" -> 2
        "firered", "leafgreen" -> 3
        else -> 0
    }

    /**
     * Which TrainerData setup this game runs (TrainerData.buildData, TrainerData.lua:131-144), as a
     * table name: "rs" (setupTrainersAsRubySapphire), "rse" (setupTrainersAsEmerald, the name
     * predates the split) or "frlg". Ruby and Sapphire used to load Emerald's tables, where 229 of
     * their 692 ids belong to someone else (Archie is 1/34/35 in R/S, Maxie 566/601/602) and
     * Courtney (599, 600) counted as a rival (parity audit, 2026-09-28).
     */
    private val trainerTable: String get() = if (refGame == 1) "rs" else map.routeTable

    /**
     * Trainer classes and groups, from TrainerData.lua by tools/trainer-data/convert_trainers.py:
     * 654 trainers for FRLG, 855 for Emerald, 692 for Ruby and Sapphire.
     */
    private val trainerClass: Map<Int, Pair<String, String>> by lazy {
        val out = HashMap<Int, Pair<String, String>>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/trainers-$trainerTable.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('	')
                    if (p.size >= 3) p[0].toIntOrNull()?.let { out[it] = p[1] to p[2] }
                }
            }
        out
    }

    /**
     * RouteData.Info[id].name and .trainers for this exact version (gen3/routeinfo-<version>.tsv,
     * made by tools/trainer-data/convert_route_info.py by running the reference's own setup),
     * keyed by the tracker's map id: the id the game reports, Emerald-numbered on Ruby and
     * Sapphire ([rawMapId]). RouteData.Info[id].trainers is where the reference takes a map's
     * trainers from for Trainers On Route, the notebook, the carousel and the log's route tab
     * (TrainersOnRouteScreen.lua:155-163, Program.lua:1545-1561, RandomizerLog.lua:606-651).
     * The app used to take FireRed/LeafGreen's from FRLGTrainerRouteData.lua, the tile-click
     * map, which misses Oak's Lab, Route 22, the Elite Four and more, and gave Ruby and Sapphire
     * Emerald's (parity audit, 2026-09-28). The reference's R/S gym, Elite Four and Route 124
     * Water keys lack the R/S offset; the converter files them under the map they name.
     */
    private val routeInfoTable: Map<Int, Pair<String, List<Int>>> by lazy {
        val out = HashMap<Int, Pair<String, List<Int>>>()
        if (map.routeVersion.isEmpty()) return@lazy out
        val areas = HashMap<Int, String>()
        javaClass.getResourceAsStream("/gen3/routeinfo-${map.routeVersion}.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#")) return@forEach
                    val p = line.split('\t')
                    val id = p.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    out[id] = (p.getOrNull(2) ?: "") to
                        (p.getOrNull(3) ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }
                    p.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }?.let { areas[id] = it }
                }
            }
        combinedAreaOf = areas
        out
    }

    /** RouteData.Info[mapId].area.name, from the same table's fifth column: the combined area a map belongs to. */
    @Volatile private var combinedAreaOf: Map<Int, String> = emptyMap()

    /** RouteData.combineRouteAreas (RouteData.lua:380-399): every map of each combined area, by its name, sorted. */
    private val combinedAreaMaps: Map<String, List<Int>> by lazy {
        routeInfoTable.size   // fills combinedAreaOf
        combinedAreaOf.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.sorted() }
    }

    /** The combined area [mapId] belongs to (Mt. Moon, S.S. Anne, Victory Road...), or null for a map of its own. */
    fun combinedArea(mapId: Int): String? { routeInfoTable.size; return combinedAreaOf[mapId] }

    /** The maps the Trainers defeated line counts for [mapId]: its combined area's, or itself alone. */
    fun areaMaps(mapId: Int): List<Int> = combinedArea(mapId)?.let { combinedAreaMaps[it] } ?: listOf(mapId)

    /**
     * The trainers the carousel's Trainers defeated line counts for [mapId], as the PC tracker does: every map of its
     * combined area (Program.getDefeatedTrainersByCombinedArea, Program.lua:1567-1581) or the map alone
     * (getDefeatedTrainersByLocation, :1546-1561), each trainer only when TrainerData.shouldUseTrainer holds
     * (TrainerData.lua:304-316): a trainer the game's tables have, and not a rival this run will not face (every rival
     * counts until the first rival battle says which). Full Clearzo counts areas the same way.
     */
    fun trainersInArea(mapId: Int): List<Int> = areaMaps(mapId).flatMap { trainersOnRoute(it) }.filter { id ->
        (trainerClass.isEmpty() || id in trainerClass) && rivalOf[id].let { r -> r == null || rivalChoice == null || r == rivalChoice }
    }

    /** The maps that have trainers, each list in the reference's order. */
    private val routeTrainerIds: Map<Int, List<Int>> by lazy {
        routeInfoTable.filterValues { it.second.isNotEmpty() }.mapValues { it.value.second }
    }

    fun trainersOnRoute(mapId: Int): List<Int> = routeTrainerIds[mapId] ?: emptyList()

    /** Every map in this version's RouteData.Info, with wild data, trainers or neither (the reference's keys). */
    fun routeMapIds(): Set<Int> = routeInfoTable.keys.ifEmpty { routes.keys }

    /**
     * RandomizerLog.RouteSetNumToIdMap: the map each "Set #N" of a randomizer log's
     * wild encounters belongs to, per game (gen3/routesets-*.tsv, converted from the
     * reference's three setup functions by tools/trainer-data/convert_routesets.py).
     *
     * As the tracker's map id, like [trainersOnRoute]'s. Ruby and Sapphire's table is in
     * the game's raw numbering (setupRubySappRouteMappings writes "id + offset" above 107,
     * RandomizerLog.lua:911-1096), so it comes down one above 108 here. Left raw, the log's
     * route tab filed Victory Road 1F's wild Pokemon under Shoal Cave Lo-1 while its
     * trainers stayed on Victory Road 1F (parity audit, 2026-09-28).
     */
    fun logRouteSets(): Map<Int, Int> {
        val key = when { map.badgeSet == "FRLG" -> "frlg"; map.rsMapShift -> "rs"; else -> "e" }
        val out = HashMap<Int, Int>()
        javaClass.getResourceAsStream("/gen3/routesets-$key.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { l ->
                if (l.startsWith("#")) return@forEach
                val p = l.split('\t')
                val set = p.getOrNull(0)?.trim()?.toIntOrNull()
                val mapId = p.getOrNull(1)?.trim()?.toIntOrNull()
                if (set != null && mapId != null) out[set] = mapIdFromRaw(mapId)
            }
        }
        return out
    }

    /** "Gym", "Elite4", "Rival", "Boss" or "Other". */
    fun trainerGroup(trainerId: Int): String = trainerClass[trainerId]?.second ?: "Other"

    fun trainerClassName(trainerId: Int): String? = trainerClass[trainerId]?.first

    // ---- Trainer Info and Trainers On Route (TrainerInfoScreen.lua, TrainersOnRouteScreen.lua) ----

    /** TrainerData.Trainers[id].whichRival, from rivals-<[trainerTable]>.tsv (tools/trainer-data/convert_rivals.py). */
    private val rivalOf: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        if (map.routeTable.isEmpty()) return@lazy out
        javaClass.getResourceAsStream("/gen3/rivals-$trainerTable.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#")) return@forEach
                    val p = line.split('\t')
                    if (p.size >= 2) p[0].toIntOrNull()?.let { out[it] = p[1].trim() }
                }
            }
        out
    }

    fun whichRival(trainerId: Int): String? = rivalOf[trainerId]

    /** Ruby, Sapphire or Emerald, as opposed to FireRed and LeafGreen (Sevii only exists on the latter). */
    val isRse: Boolean get() = map.routeTable == "rse"

    /** Whether the Poke Balls pocket is pinned for this build, so Catch Rates can open. */
    val hasCatchRates: Boolean get() = map.bagBallsOffset != 0L

    /** Whether BattleDetailsScreen's addresses are pinned for this build. */
    val hasBattleDetails: Boolean get() = map.battleTerrain != 0L

    /** Whether this build's ROM map carries gTrainers, so the trainer screens can open. */
    val hasTrainerData: Boolean get() = map.gTrainers != 0L

    /**
     * Tracker.Data.whichRival: which of the three rivals this run has, learned
     * from the first rival battle. Until then every rival is listed, as the
     * reference does (TrainerData.shouldUseTrainer).
     *
     * The reference keeps it with the run's tracked data (Tracker.saveData and loadData, Tracker.lua:612, :632), and
     * this tracker is made again on every visit to Play, so the app keeps it too (StatMarks, TrackerRival): a rival
     * learned is handed over through [takeLearnedRival], and given back through [restoreRival] (rc32 audit P2 #130).
     */
    @Volatile var rivalChoice: String? = null
        private set

    /** A rival learned in a battle and not yet handed to the app; a restored one never lands here. */
    private val learnedRival = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** Tracker.tryTrackWhichRival (Tracker.lua:362-371): a rival trainer's battle says which rival this run has. */
    private fun learnRival(trainerId: Int) {
        val r = whichRival(trainerId) ?: return
        if (r != rivalChoice) { rivalChoice = r; learnedRival.set(r) }
    }

    /** The run's rival from its tracked data, into a tracker that has not learned one: only a value this game's table has. */
    fun restoreRival(choice: String?) {
        if (rivalChoice == null && choice != null && choice in rivalOf.values) rivalChoice = choice
    }

    /** The rival a battle taught this tracker since the last call, once: what the app saves with the run. */
    fun takeLearnedRival(): String? = learnedRival.getAndSet(null)

    /** TrainersOnRouteScreen's list for a map: the route's trainers, minus the rivals that are not this run's. */
    fun trainersForRoute(mapId: Int): List<Int> =
        trainersOnRoute(mapId).filter { id -> val r = rivalOf[id]; r == null || rivalChoice == null || r == rivalChoice }

    private fun readOpponentTrainerId(): Int? {
        if (map.trainerOpponent == 0L) return null
        val b = memory.read(map.trainerOpponent, 2)
        return if (b.size == 2) b.u16(0).takeIf { it != 0 } else null
    }

    data class TrainerMon(val species: Int, val level: Int, val ivs: Int, val heldItem: Int, val moves: List<Int>)

    data class TrainerInfo(
        val id: Int,
        val className: String,
        val name: String,
        val party: List<TrainerMon>,
        val aiFlags: Int,
        val doubleBattle: Boolean,
        val defeated: Boolean,
        val items: List<Int>,
    ) {
        /** TrainerInfoScreen's AI label from the script flags. */
        val aiLabel: String get() = when {
            aiFlags and 4 != 0 -> "Smart"
            aiFlags and 2 != 0 -> "Semi-Smart"
            aiFlags and 1 != 0 -> "Normal"
            aiFlags == 0 -> "Dumb"
            else -> "Complex"
        }
        val minLevel: Int get() = party.minOfOrNull { it.level } ?: 0
        val maxLevel: Int get() = party.maxOfOrNull { it.level } ?: 0
        /** TrainerInfoScreen: the party's average IV, floor of the mean. */
        val avgIvs: Int get() = if (party.isEmpty()) 0 else (party.sumOf { it.ivs } / party.size).coerceAtLeast(0)
    }

    /**
     * Program.readTrainerGameData: the 0x28-byte gTrainers entry, its class
     * name, its name (the rival's from the save block on FRLG, as the reference
     * does, since the ROM's name for the rival is a placeholder), and the party
     * in whichever of the four layouts the flags say. IVs are the 0..255 byte
     * scaled to 31, the reference's `iv * 31 / 255`.
     */
    fun trainer(trainerId: Int): TrainerInfo? {
        if (map.gTrainers == 0L || trainerId <= 0) return null
        // Every size and offset from the layout: the Nat. Dex entry is 44 bytes
        // with 16-byte names, and reading it as the vanilla 40 put Brock's name,
        // party and flags at the wrong place (Program.readTrainerGameData).
        val lay = map.trainerLayout
        val base = map.gTrainers + trainerId.toLong() * lay.size
        val t = memory.read(base, lay.size)
        if (t.size < lay.size) return null
        val partyFlags = t.u8(0)
        val classId = t.u8(lay.classOffset)
        val partySize = t.u8(lay.partySizeOffset)
        val aiFlags = t.u32(lay.aiOffset).toInt()
        val doubleBattle = t.u8(lay.doubleOffset) != 0
        val items = (0 until 4).map { t.u16(lay.itemsOffset + it * lay.itemSize) }
        val className = if (map.gTrainerClassNames == 0L) "" else
            Gen3Text.decode(memory.read(map.gTrainerClassNames + classId.toLong() * lay.classNameSize, lay.classNameSize))
        val ownName = t.copyOfRange(lay.nameOffset, minOf(lay.nameOffset + lay.nameSize, t.size))
        val nameBytes = if (map.badgeSet == "FRLG" && rivalOf[trainerId] != null)
            saveBlock1()?.let { memory.read(it + map.rivalNameOffset, lay.nameSize) } ?: ownName
        else ownName
        val name = Gen3Text.decode(nameBytes)
        val partyPtr = t.u32(lay.partyPtrOffset)
        // The reference reads a party only for flags 0 to 3 (bit 0 custom moves, bit 1 held item).
        val custom = partyFlags and 1 != 0
        val held = partyFlags and 2 != 0
        val entry = if (custom) lay.monCustomSize else lay.monDefaultSize
        val party = ArrayList<TrainerMon>()
        if (partyFlags in 0..3 && partyPtr in 0x08000000L..0x09FFFFFFL && partySize in 1..6) {
            val pb = memory.read(partyPtr, entry * partySize)
            if (pb.size == entry * partySize) for (i in 0 until partySize) {
                val o = i * entry
                val iv = pb.u16(o); val level = pb.u8(o + lay.monLevel); val species = pb.u16(o + lay.monSpecies)
                val item = if (held) pb.u16(o + lay.monItem) else 0
                val moves = if (custom) {
                    val m0 = o + if (held) lay.monItemMove1 else lay.monNoItemMove1
                    (0 until 4).map { pb.u16(m0 + it * lay.moveSize) }
                } else emptyList()
                party += TrainerMon(species, level, iv * 31 / 255, item, moves)
            }
        }
        return TrainerInfo(trainerId, className, name, party, aiFlags, doubleBattle, trainerDefeated(trainerId), items)
    }

    /**
     * The picture the game draws [trainerId] with, 64x64 ARGB (TrainerPictures): its gTrainers entry's picture slot, read
     * out of the ROM. Null on a build whose picture tables are not proven (GameMap.trainerPics), or where a read fails.
     */
    fun trainerPicture(trainerId: Int): IntArray? {
        if (map.gTrainers == 0L || trainerId <= 0) return null
        val lay = map.trainerLayout
        val b = memory.read(map.gTrainers + trainerId.toLong() * lay.size + lay.picOffset, 1)
        return if (b.isEmpty()) null else trainerPictureIn(b.u8(0))
    }

    /** The player's own picture, the boy's or [girl]'s (GameMap.playerPic): the log viewer's Trainers tab cuts its head. */
    fun playerPicture(girl: Boolean): IntArray? = if (map.playerPic < 0) null else trainerPictureIn(map.playerPic + if (girl) 1 else 0)

    /** Each picture decoded once: a log has hundreds of trainers and a game 83 to 148 pictures. Nulls are kept too. */
    private val trainerPictures = HashMap<Int, IntArray?>()

    private fun trainerPictureIn(slot: Int): IntArray? = synchronized(trainerPictures) {
        if (slot in trainerPictures) return trainerPictures[slot]
        runCatching { TrainerPictures.picture(memory, map, slot) }.getOrNull().also { trainerPictures[slot] = it }
    }

    private var trainerTeamsCache: Boolean? = null

    /**
     * TrainerData.IsRand.teamPokemon (TrainerData.lua:147-259): whether the trainers' Pokemon were
     * randomized, judged as the reference judges it, by the first two gym leaders' Pokemon (Roxanne
     * and Brawly, 265 and 266, on Ruby, Sapphire and Emerald; Brock and Misty, 414 and 415, on
     * FireRed and LeafGreen), a slot the leader does not have not counting. Trainer Info reveals a
     * team before it is beaten only when this is false (TrainerData.canShowUnknownTrainerTeams).
     * Null where it cannot be told (no trainer table, an unknown game, neither party readable),
     * which callers read as randomized.
     */
    fun trainerTeamsRandomized(): Boolean? {
        trainerTeamsCache?.let { return it }
        val leaders: List<Pair<Int, List<Int>>> = when (refGame) {
            1 -> listOf(265 to listOf(74, 320), 266 to listOf(66, 335))
            2 -> listOf(265 to listOf(74, 74, 320), 266 to listOf(66, 356, 335))
            3 -> listOf(414 to listOf(74, 95), 415 to listOf(120, 121))
            else -> return null
        }
        val parties = leaders.map { (id, _) -> trainer(id)?.party ?: return null }
        if (parties.all { it.isEmpty() }) return null
        val changed = leaders.zip(parties).any { (leader, party) ->
            leader.second.indices.any { i -> party.getOrNull(i)?.let { it.species != leader.second[i] } == true }
        }
        return changed.also { trainerTeamsCache = it }
    }

    // ---- Random Evos (RandomEvosScreen.lua, PokemonRevoData.lua) ----

    /** revos.tsv: base id -> (target id, or 0 for a single evolution) -> (evo id, percent) in the reference's order. */
    private val revos: Map<Int, Map<Int, List<Pair<Int, Double>>>> by lazy {
        val out = HashMap<Int, LinkedHashMap<Int, List<Pair<Int, Double>>>>()
        javaClass.getResourceAsStream(if (map.nameSet == "maxdex") "/gen3/revos-maxdex.tsv" else "/gen3/revos.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#")) return@forEach
                val p = line.split('\t'); if (p.size < 3) return@forEach
                val base = p[0].toIntOrNull() ?: return@forEach
                val target = p[1].toIntOrNull() ?: return@forEach
                val list = p[2].split(',').mapNotNull { e ->
                    val c = e.indexOf(':'); if (c <= 0) null else {
                        val id = e.substring(0, c).toIntOrNull(); val perc = e.substring(c + 1).toDoubleOrNull()
                        if (id == null || perc == null) null else id to perc
                    }
                }
                out.getOrPut(base) { LinkedHashMap() }[target] = list
            }
        }
        out
    }

    /** PokemonRevoData.getEvoOptions: the regular evolutions to pick between, or empty for a single one. */
    fun randomEvoOptions(species: Int): List<Int> = revos[species]?.keys?.filter { it != 0 } ?: emptyList()

    fun hasRandomEvos(species: Int): Boolean = revos.containsKey(species)

    /** PokemonRevoData.getEvoTable: the possible randomized evolutions with their chances. */
    fun randomEvos(species: Int, target: Int? = null): List<Pair<Int, Double>>? {
        val entry = revos[species] ?: return null
        entry[0]?.let { return it }
        val t = target ?: entry.keys.firstOrNull() ?: return null
        return entry[t]
    }

    // ---- Heals In Bag (HealsInBagScreen.lua) ----

    data class BagRow(val id: Int, val name: String, val quantity: Int, val category: String, val helpful: Boolean, val sortValue: Int)

    /** Program.updateBagItems: Items, Berries and Poke Balls pockets, decrypted, id to quantity. */
    fun readBag(): Map<Int, Int> {
        val sb1 = saveBlock1() ?: return emptyMap()
        val key = readSecurityKey()
        val out = LinkedHashMap<Int, Int>()
        for ((offset, slots) in listOf(map.bagItemsOffset to map.bagItemsSlots, map.bagBerriesOffset to map.bagBerriesSlots, map.bagBallsOffset to map.bagBallsSlots)) {
            if (offset == 0L) continue
            for (slot in 0 until slots) {
                val b = memory.read(sb1 + offset + slot * 4L, 4)
                if (b.size < 4) break
                val id = b.u16(0); if (id == 0) continue
                val qty = b.u16(2) xor key
                if (qty in 1..999) out[id] = (out[id] ?: 0) + qty
            }
        }
        return out
    }

    /**
     * HealsInBagScreen.buildPagedButtons for every tab at once: each item in
     * the bag with its category, whether it helps the lead right now (the
     * reference's rules: an HP heal whose two thirds fit the missing HP, a PP
     * item when a move is down to a point, a status heal matching the lead's
     * status or a cure-all while it has one) and the reference's sort value.
     */
    fun healsInBag(lead: TrackedMon?): List<BagRow> {
        val bag = readBag()
        val maxHp = lead?.mon?.maxHp ?: 0
        val missing = if (lead != null) maxOf(maxHp - lead.mon.curHp, 0) else 0
        val status = lead?.statusCondition ?: ""
        val statusType = when (status) { "SLP" -> "Sleep"; "PSN" -> "Poison"; "BRN" -> "Burn"; "FRZ" -> "Freeze"; "PAR" -> "Paralyze"; else -> "" }
        val ppEmpty = lead != null && lead.mon.moves.indices.any { i -> lead.mon.moves[i] != 0 && lead.mon.moves[i] != 166 && (lead.mon.pp.getOrNull(i) ?: 99) <= 1 }
        val rows = ArrayList<BagRow>()
        for ((id, qty) in bag) {
            val heal = HEAL_ITEMS[id]
            val statusKind = STATUS_ITEMS[id]
            val category: String; var helpful = false; var sort: Int
            when {
                heal != null -> {
                    category = "HP"
                    val amt = if (heal.second) Math.floor(maxHp * heal.first / 100).toInt() else heal.first.toInt()
                    helpful = lead != null && amt * 2.0 / 3 <= missing
                    sort = 50000 + heal.first.toInt() + (if (heal.second) 1000 else 0)
                }
                statusKind != null -> {
                    category = "Status"
                    helpful = statusType.isNotEmpty() && (statusKind == statusType || statusKind == "All")
                    sort = 40000 + (if (statusKind == "All") 2 else 1)
                }
                id in PP_ITEMS -> { category = "PP"; helpful = ppEmpty; sort = 30000 }
                id in BATTLE_ITEMS || (map.expandedSpeciesIds && id == NATDEX_X_SP_DEF) -> { category = "Battle"; sort = 20000 }
                // calcSortValue gives balls, stones and everything else 0 (HealsInBagScreen.lua:182-204).
                id in 1..12 -> { category = "Balls"; sort = 0 }
                id in 93..98 -> { category = "Evo"; sort = 0 }
                else -> { category = "Other"; sort = 0 }
            }
            rows += BagRow(id, itemName(id), qty, category, helpful, sort)
        }
        // Pager.defaultSort (HealsInBagScreen.lua:75): sort value, highest first, then item id.
        return rows.sortedWith(compareByDescending<BagRow> { it.sortValue }.thenBy { it.id })
    }

    // ---- Notebook (NotebookIndexScreen.lua, NotebookTrainersByArea.lua) ----

    /**
     * TrainerData.getExcludedTrainers: dummy trainers and VS Seeker rematches,
     * per game family, so the counts match the PC tracker's.
     */
    private val excludedTrainers: Set<Int> by lazy {
        val ranges: List<IntRange> = when (map.routeTable) {
            "rse" -> listOf(40..43, 47..50, 54..56, 60..63, 67..70, 84..87, 101..104, 110..113, 117..117,
                120..123, 132..135, 139..142, 147..150, 173..173, 175..178, 184..187, 197..200,
                207..210, 219..222, 228..231, 239..242, 250..253, 257..260, 276..279, 282..285,
                288..291, 295..298, 303..306, 308..311, 314..317, 328..331, 341..341, 346..349,
                354..357, 360..363, 365..368, 370..373, 379..382, 388..391, 393..396, 409..412,
                421..424, 430..433, 437..440, 456..456, 462..462, 466..468, 477..480, 482..482, 485..489,
                497..500, 515..518, 541..544, 548..551, 555..558, 562..565, 607..610, 622..625,
                633..634, 636..639, 643..646, 657..660, 682..685, 688..691, 770..801, 805..847,
                851..855)
            "frlg" -> listOf(1..88, 101..101, 147..147, 200..200, 263..263, 454..461, 492..515, 530..530, 621..741)
            else -> emptyList()
        }
        ranges.flatMapTo(HashSet()) { it }
    }

    /** TrainerData.shouldUseTrainer minus getExcludedTrainers: a trainer the notebook counts. */
    fun trainerCounts(trainerId: Int): Boolean {
        if (trainerId in excludedTrainers) return false
        val r = rivalOf[trainerId] ?: return true
        return rivalChoice == null || r == rivalChoice
    }

    data class AreaRow(val routeId: Int, val name: String, val defeated: Int, val total: Int)

    /**
     * NotebookTrainersByArea.buildScreen, one row per map with trainers: the
     * usable trainers there and how many are beaten. Sevii Islands (route ids
     * 230 and up on FRLG) are out unless asked for, as are finished areas.
     * The reference also merges a few maps into one named area; this lists
     * each map on its own.
     */
    fun notebookAreas(includeSevii: Boolean, includeCompleted: Boolean): List<AreaRow> {
        val out = ArrayList<AreaRow>()
        for ((routeId, ids) in routeTrainerIds.entries.sortedBy { it.key }) {
            if (map.routeTable == "frlg" && routeId >= 230 && !includeSevii) continue
            val usable = ids.filter { trainerCounts(it) }
            if (usable.isEmpty()) continue
            val defeated = usable.count { trainerDefeated(it) }
            if (!includeCompleted && defeated == usable.size) continue
            out += AreaRow(routeId, routeInfo(routeId)?.first ?: "Map $routeId", defeated, usable.size)
        }
        return out
    }

    /**
     * NotebookIndexScreen: trainers defeated and total, over every area, each trainer once, as
     * the reference counts them (one pass over TrainerData.OrderedIds, NotebookIndexScreen.lua:
     * 171-187). Ruby and Sapphire's RouteData lists twelve gym trainers on two floors (Lavaridge
     * and Sootopolis, RouteData.lua:4489-4493), which a sum of the per-map rows counts twice.
     */
    fun notebookTrainerTotals(includeSevii: Boolean): Pair<Int, Int> {
        val ids = routeTrainerIds.filterKeys { includeSevii || map.routeTable != "frlg" || it < 230 }
            .values.flatten().distinct().filter { trainerCounts(it) }
        return ids.count { trainerDefeated(it) } to ids.size
    }

    /** The species' two possible abilities from the base stats table, for the note view. */
    fun possibleAbilities(species: Int): List<String> {
        val b = baseStats(species) ?: return emptyList()
        return listOf(b.ability1, b.ability2).filter { it != 0 }.distinct().map { abilityName(it) }
    }

    // ---- Catch Rates (CatchRatesScreen.lua, PokemonData.calcCatchRate) ----

    data class CatchRow(val ballId: Int, val name: String, val quantity: Int, val rate: Int)

    data class CatchRates(
        val speciesName: String,
        /** The reference's estimate: HP rounded up to the nearest tenth, as a percent. */
        val hpPercent: Int,
        val status: String,
        val rows: List<CatchRow>,
    )

    /** The Poke Balls pocket as ball id to quantity, decrypted like the Items pocket. */
    fun bagBalls(): Map<Int, Int> {
        if (map.bagBallsOffset == 0L) return emptyMap()
        val sb1 = saveBlock1() ?: return emptyMap()
        val key = readSecurityKey()
        val out = HashMap<Int, Int>()
        for (slot in 0 until map.bagBallsSlots) {
            val b = memory.read(sb1 + map.bagBallsOffset + slot * 4L, 4)
            if (b.size < 4) break
            val id = b.u16(0); if (id !in 1..12) continue
            val qty = b.u16(2) xor key
            if (qty in 1..999) out[id] = qty
        }
        return out
    }

    /** Program.Addresses.offsetPokedex + offsetPokedexOwned: whether the species has been caught before, for the Repeat Ball. */
    fun dexOwned(species: Int): Boolean {
        val sb2 = saveBlock2() ?: return false
        val b = memory.read(sb2 + map.pokedexOwnedOffset + ((species - 1) / 8), 1)
        return b.size == 1 && ((b[0].toInt() shr ((species - 1) % 8)) and 1) == 1
    }

    /**
     * PokemonData.calcCatchRate, the reference's estimate of the Gen 3
     * formula: HP rounded up to the tenth, the ball bonus with its four
     * conditional balls, the status bonus (Toxic counts for nothing on Ruby,
     * Sapphire and Emerald), then the game's own shake arithmetic to a percent.
     */
    fun calcCatchRate(baseCatchRate: Int, hpMax: Int, hpCurrent: Int, level: Int, status: Long, ball: Int,
                      isWaterOrBug: Boolean, terrain: Int, owned: Boolean, battleTurn: Int): Int {
        if (hpMax <= 0 || hpCurrent <= 0) return 0
        val estimatedCurrHp = Math.floor(Math.ceil(hpCurrent.toDouble() / hpMax * 10) / 10 * hpMax)
        val hpMultiplier = (hpMax * 3 - estimatedCurrHp * 2) / (hpMax * 3.0)
        val bonusMap = mapOf(1 to 255, 2 to 20, 3 to 15, 4 to 10, 5 to 15, 6 to 30, 7 to 35, 8 to 40, 9 to 30, 10 to 10, 11 to 10, 12 to 10)
        val ballBonus = when {
            ball <= 5 || ball >= 11 -> bonusMap[ball] ?: 10
            ball == 6 && isWaterOrBug -> 30
            ball == 7 && terrain == 3 -> 35
            ball == 8 -> maxOf(10, 40 - level)
            ball == 9 && owned -> 30
            ball == 10 -> minOf(10 + battleTurn, 40)
            else -> 10
        } / 10.0
        val statusBonus = when {
            status and 0x07L != 0L -> 2.0          // sleep
            status and 0x20L != 0L -> 2.0          // freeze
            // Toxic: 1x when GameSettings.game is 1 or 2, Ruby/Sapphire and Emerald (Nat. Dex
            // Emerald too), 1.5x only on FireRed/LeafGreen (PokemonData.lua:672-676). Keyed on
            // rsMapShift, Emerald got 1.5x (parity audit, 2026-09-28).
            status and 0x80L != 0L -> if (refGame == 1 || refGame == 2) 1.0 else 1.5
            status and 0x08L != 0L || status and 0x10L != 0L || status and 0x40L != 0L -> 1.5
            else -> 1.0
        }
        val raw = Math.floor(Math.floor(baseCatchRate * ballBonus * hpMultiplier) * statusBonus).toInt()
        val percentage = when {
            raw <= 0 -> 0
            raw > 254 -> 100
            else -> {
                var processed = Math.floor(1048560.0 / Math.floor(Math.sqrt(Math.floor(Math.sqrt(Math.floor(16711680.0 / raw))))))
                processed = Math.floor(processed / 65535 * 100) / 100
                Math.floor(processed * processed * processed * processed * 100).toInt()
            }
        }
        return percentage.coerceIn(0, 100)
    }

    /**
     * CatchRatesScreen.buildScreen for the enemy in gBattleMons slot 1:
     * the twelve balls, the bag's count of each, and the rate at the
     * estimated HP plus [hpAdjust] (the screen's +/- 10% buttons), owned
     * balls first and best rate first, as the reference sorts them.
     */
    fun catchRates(hpAdjust: Int = 0): CatchRates? {
        // CatchRatesScreen.buildScreen: the ghost stand-in is not a valid Pokemon, so no screen.
        if (!inBattleNow() || map.battleMons == 0L || isGhostBattle()) return null
        val b = memory.read(map.battleMons + map.battleMonSize, map.battleMonSize)
        if (b.size < 0x50) return null
        val species = b.u16(0)
        if (!speciesIsValid(species)) return null
        val hpMax = b.u16(map.battleMonHp + 4); val hpCur = b.u16(map.battleMonHp); val level = b.u8(map.battleMonHp + 2)
        // The reference's status is the party struct's (Tracker.getPokemon(1, false)), as
        // on the enemy card: gBattleMons + 0x4C is not the status in Nat. Dex's grown struct.
        val status = enemyPartyStatus(species, level, hpCur) ?: 0L
        if (hpMax <= 0) return null
        val base = baseStats(species)
        val estimatedCurrHp = Math.floor(Math.ceil(hpCur.toDouble() / hpMax * 10) / 10 * hpMax)
        val hpPercent = Math.floor(estimatedCurrHp / hpMax * 100).toInt()
        val estimatedHp = Math.floor(hpMax * (hpPercent + hpAdjust) / 100.0 + 0.5).toInt()
        val terrain = if (map.battleTerrain == 0L) 0 else rw(map.battleTerrain)
        val turn = if (map.battleResults == 0L) 0 else rb(map.battleResults + map.battleResultsTurnOffset)
        val waterOrBug = base != null && (base.type1 == 11 || base.type2 == 11 || base.type1 == 6 || base.type2 == 6)
        val owned = dexOwned(species)
        val bag = bagBalls()
        val rows = (1..12).map { ball ->
            CatchRow(ball, itemName(ball), bag[ball] ?: 0,
                calcCatchRate(base?.catchRate ?: 0, hpMax, estimatedHp, level, status, ball, waterOrBug, terrain, owned, turn))
        }.sortedWith(compareByDescending<CatchRow> { it.quantity > 0 }.thenByDescending { it.rate }.thenBy { it.ballId })
        return CatchRates(speciesName(species), hpPercent, statusName(status), rows)
    }

    // ---- Battle Details (BattleDetailsScreen.lua) ----

    /** One line of the screen; moveId or species say what it is about, for a future tap-through. */
    data class BattleDetail(val text: String, val moveId: Int = 0, val species: Int = 0)

    /**
     * Everything BattleDetailsScreen.updateData reads: terrain, weather, the
     * turn, field effects, each side's effects and each battler's. Battler
     * indexes are the game's: 0 and 2 allied, 1 and 3 enemy.
     */
    data class BattleDetails(
        val terrain: String,
        val weather: String,
        val turn: Int,
        val battlers: Int,
        val field: List<BattleDetail>,
        val sides: List<List<BattleDetail>>,
        val mons: List<List<BattleDetail>>,
    ) {
        /** BattleDetailsScreen.summarizeDetails: the first relevant line for a battler. */
        fun summary(index: Int): String =
            (mons.getOrNull(index)?.firstOrNull() ?: sides.getOrNull(index % 2)?.firstOrNull() ?: field.firstOrNull())?.text ?: ""
    }

    @Volatile private var lastInBattle = false
    /** What the last read() decided about being in a battle; the battle screens key off it. */
    fun inBattleNow(): Boolean = lastInBattle

    private fun rb(a: Long): Int { val b = memory.read(a, 1); return if (b.size == 1) b[0].toInt() and 0xFF else 0 }
    private fun rw(a: Long): Int { val b = memory.read(a, 2); return if (b.size == 2) b.u16(0) else 0 }
    private fun rd(a: Long): Long { val b = memory.read(a, 4); return if (b.size == 4) b.u32(0) else 0 }

    /** The species in gBattleMons slot [index], for the "(SOURCE)" suffixes; null when empty. */
    private fun battlerSpeciesName(index: Int): String? {
        if (index !in 0..3 || map.battleMons == 0L) return null
        val sp = rw(map.battleMons + index.toLong() * map.battleMonSize)
        return if (speciesIsValid(sp)) speciesName(sp) else null
    }

    private fun turns(n: Int) = "$n Turn" + (if (n == 1) "" else "s")

    /**
     * BattleDetailsScreen.updateData, all of GameFuncs in one read. Null when
     * not in a battle or the addresses are not pinned for this build.
     */
    fun battleDetails(): BattleDetails? {
        if (map.battleTerrain == 0L || !inBattleNow()) return null
        val battlers = if (map.battlersCount != 0L) rb(map.battlersCount).coerceIn(2, 4) else 2
        val field = ArrayList<BattleDetail>()
        val sides = List(2) { ArrayList<BattleDetail>() }
        val mons = List(4) { ArrayList<BattleDetail>() }

        // readTerrain / readWeather
        val terrain = when (rb(map.battleTerrain)) {
            0 -> "Grass"; 1 -> "Long Grass"; 2 -> "Sand"; 3 -> "Underwater"; 4 -> "Water"
            5 -> "Pond"; 6 -> "Mountain"; 7 -> "Cave"; else -> "Building"
        }
        var weather = "None"
        val wb = if (map.weather == 0L) 0 else rb(map.weather)
        if (wb != 0) {
            var bit = 0; var v = wb
            while (v > 1) { v = v shr 1; bit++ }
            weather = when (bit) { 0, 1, 2 -> "Rain"; 3, 4 -> "Sandstorm"; 5, 6 -> "Sunlight"; 7 -> "Hail"; else -> "None" }
            if (bit == 0 || bit == 3 || bit == 5 || bit == 7) {
                val wt = rb(map.wishFutureKnock + 0x28)
                field += BattleDetail("Weather turns Left: $wt")
            } else field += BattleDetail("Weather: $weather")
        }
        // Pay Day
        val payday = rw(map.paydayMoney)
        if (payday != 0) field += BattleDetail("${moveName(6)} ($payday)", moveId = 6)

        val battleStruct = if (map.battleStructPtr != 0L) rd(map.battleStructPtr) else 0x02000000L
        var lockOn = BooleanArray(4); var perish = BooleanArray(4)
        for (i in 0 until battlers) {
            val m = mons[i]
            // readStatus2: gBattleMons + offsetBattleMonsStatus2 (0x50; 0x54 on Nat. Dex)
            val s2 = rd(map.battleMons + i.toLong() * map.battleMonSize + map.status2Offset)
            fun b2(n: Int) = (s2 shr n) and 1L == 1L
            if (b2(0) || b2(1) || b2(2)) m += BattleDetail("Confused (1- 4 Turns)")
            if (b2(4) || b2(5) || b2(6)) m += BattleDetail(moveName(253), 253)
            if (b2(8) || b2(9)) {
                val t = (if (b2(8)) 1 else 0) + (if (b2(9)) 2 else 0)
                m += BattleDetail("${moveName(117)}: ${turns(t)}", 117)
            }
            if (b2(12) && !(b2(8) || b2(9))) m += BattleDetail("Must Attack")
            if (b2(13) || b2(14) || b2(15)) {
                val src = rb(battleStruct + map.wrappedByOffset + i)
                battlerSpeciesName(src)?.let { m += BattleDetail("Trapped ($it)") }
            }
            if (b2(16) || b2(17) || b2(18) || b2(19)) {
                val target = when { b2(16) -> 0; b2(17) -> 1; b2(18) -> 2; else -> 3 }
                battlerSpeciesName(target)?.let { m += BattleDetail("${moveName(213)} ($it)", 213) }
            }
            if (b2(20)) m += BattleDetail(moveName(116), 116)
            if (b2(21)) m += BattleDetail(moveName(144), 144)
            if (b2(22)) m += BattleDetail("Recharging")
            if (b2(23)) m += BattleDetail(moveName(99), 99)
            if (b2(24)) m += BattleDetail(moveName(164), 164)
            if (b2(25)) m += BattleDetail(moveName(194), 194)
            if (b2(26)) m += BattleDetail("Can't Escape")
            if (b2(27)) m += BattleDetail(moveName(171), 171)
            if (b2(28)) m += BattleDetail(moveName(174), 174)
            if (b2(29)) m += BattleDetail(moveName(193), 193)
            if (b2(30)) m += BattleDetail(moveName(111), 111)
            if (b2(31)) m += BattleDetail(moveName(259), 259)

            // readStatus3
            val s3 = rd(map.statuses3 + i.toLong() * 4)
            fun b3(n: Int) = (s3 shr n) and 1L == 1L
            if (b3(2)) {
                val src = (if (b3(0)) 1 else 0) + (if (b3(1)) 2 else 0)
                battlerSpeciesName(src)?.let { m += BattleDetail("${moveName(73)} ($it)", 73) }
            }
            if (b3(3) || b3(4)) lockOn[i] = true
            if (b3(5)) perish[i] = true
            when { b3(6) -> "Airborne"; b3(7) -> "Underground"; b3(18) -> "Underwater"; else -> null }?.let { m += BattleDetail(it) }
            if (b3(8)) m += BattleDetail(moveName(107), 107)
            if (b3(9)) m += BattleDetail(moveName(268), 268)
            if (b3(10)) m += BattleDetail(moveName(275), 275)
            if (b3(11) || b3(12)) m += BattleDetail("Drowsy", 281)
            if (b3(13)) m += BattleDetail(moveName(286), 286)
            if (b3(14)) m += BattleDetail(moveName(288), 288)
            if (b3(16)) battlerSpeciesName(i)?.let { field += BattleDetail("${moveName(300)} ($it)", 300) }
            if (b3(17)) battlerSpeciesName(i)?.let { field += BattleDetail("${moveName(346)} ($it)", 346) }

            // readSideStatuses, once per side
            if (i < 2) {
                val side = sides[i]
                val ss = rw(map.sideStatuses + i.toLong() * 2)
                val tb = map.sideTimers + i.toLong() * 0xC
                if (ss and 1 != 0) m.let { side += BattleDetail("${moveName(115)}: ${turns(rb(tb + 0))} Left", 115) }
                if (ss and 2 != 0) side += BattleDetail("${moveName(113)}: ${turns(rb(tb + 2))} Left", 113)
                if (ss and 0x10 != 0) side += BattleDetail("${moveName(191)}: ${rb(tb + 0xA)}", 191)
                if (ss and 0x20 != 0) side += BattleDetail("${moveName(219)}: ${turns(rb(tb + 6))} Left", 219)
                if (ss and 0x100 != 0) side += BattleDetail("${moveName(54)}: ${turns(rb(tb + 4))} Left", 54)
            }

            // readDisableStruct
            val ds = map.disableStructs + i.toLong() * 0x1C
            val disabled = rw(ds + 4); if (disabled != 0) m += BattleDetail("${moveName(50)} (${moveName(disabled)})", 50)
            val encored = rw(ds + 6); if (encored != 0) m += BattleDetail("${moveName(227)} (${moveName(encored)})", 227)
            val protect = rb(ds + 8); if (protect != 0) m += BattleDetail("Protection Uses: $protect", 182)
            val stockpile = rb(ds + 9); if (stockpile != 0) m += BattleDetail("${moveName(254)}: $stockpile", 254)
            if (perish[i]) m += BattleDetail("Perish Count: ${1 + (rb(ds + 0xF) and 0xF)}", 195)
            val fury = rb(ds + 0x10); if (fury != 0) m += BattleDetail("${moveName(210)}: $fury", 210)
            val rollout = rb(ds + 0x11) and 0xF
            if (rollout != 0) m += BattleDetail("${moveName(rw(map.lockedMoves + i.toLong() * 2))}: ${turns(rollout)} Left", 205)
            val taunt = rb(ds + 0x13) and 0xF; if (taunt != 0) m += BattleDetail("${moveName(269)}: ${turns(taunt)} Left", 269)
            if (lockOn[i]) battlerSpeciesName(rb(ds + 0x15))?.let { m += BattleDetail("${moveName(199)} ($it)", 199) }
            if (rb(ds + 0x18) and 1 == 1) {
                // BattleDetailsScreen.lua:1574-1588: Loafing only once Truant (54) is tracked for the
                // battler's species, so the line never reveals the ability. It used to show for any
                // species that could have Truant.
                val sp = rw(map.battleMons + i.toLong() * map.battleMonSize)
                if (speciesIsValid(sp) && abilityName(54) in trackedAbilities(sp)) m += BattleDetail("Loafing")
            }

            // readWishStruct
            val wk = map.wishFutureKnock
            val fs = rb(wk + i)
            if (fs != 0) battlerSpeciesName(rb(wk + 4 + i))?.let { m += BattleDetail("Future: ${turns(fs)} Left ($it)") }
            val wish = rb(wk + 0x20 + i)
            if (wish != 0) battlerSpeciesName(rb(wk + 0x24 + i))?.let { m += BattleDetail("${moveName(273)}: ${turns(fs)} Left ($it)", 273) }
            if (rb(wk + 0x29 + i + (if (i < 2) 0 else 1)) != 0) m += BattleDetail(moveName(282), 282)
        }
        val turn = if (map.battleResults == 0L) 0 else rb(map.battleResults + map.battleResultsTurnOffset) + 1
        return BattleDetails(terrain, weather, turn, battlers, field, sides, mons)
    }

    /**
     * Program.checkForStarterSelection: the species in the ball the player is
     * being asked to confirm, or null. FRLG reads it from a game var while the
     * yes/no is open (result 1 or 255); RSE finds the confirm task running and
     * takes the matching rival's first Pokemon (choice 0, 1, 2 -> trainers 520,
     * 523, 526).
     */
    fun starterOffered(): Int? {
        val species = when {
            map.confirmStarterTask != 0L && map.gTasks != 0L -> {
                val func = rd(map.gTasks)
                if (func >= map.confirmStarterTask && func < map.confirmStarterTask + 10) {
                    val rival = mapOf(0 to 520, 1 to 523, 2 to 526)[rw(map.gTasks + 0x8)] ?: 0
                    trainer(rival)?.party?.firstOrNull()?.species
                } else null
            }
            map.specialVarResult != 0L -> {
                val r = rw(map.specialVarResult)
                if (r == 1 || r == 255) saveBlock1()?.let { rw(it + map.gameVarsOffset + 0x4) } else null
            }
            else -> null
        }
        // PokemonData.isValid (Program.lua:760): any species this build has, not vanilla's 411.
        return species?.takeIf { speciesIsValid(it) && baseStats(it) != null }
    }

    /** Program.hasDefeatedTrainer: flag offsetTrainerFlagStart (0x500) + id in the save block's flags. */
    fun trainerDefeated(trainerId: Int): Boolean {
        if (map.gameFlagsOffset == 0L) return false
        val sb1 = saveBlock1() ?: return false
        val flag = map.trainerFlagStart + trainerId
        val b = memory.read(sb1 + map.gameFlagsOffset + (flag / 8), 1)
        return b.size == 1 && ((b[0].toInt() shr (flag % 8)) and 1) == 1
    }

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
     * tracker indexes everything else. On Nat. Dex, the extension's table
     * (tools/trainer-data/convert_natdex_species.py): its own evolutions for
     * the base game's species, Kadabra's Linking Cord and Eevee's eight stones
     * among them, and the 872 species it adds, to 1283. The base game's table
     * was used there, wrong for dozens of species and blank past 411
     * (rc33 audit P1 #67).
     *
     * Evolution is the reference's own string: a bare number means "at that
     * level", anything else names the method (STONE, FRIENDSHIP, TRADE...).
     */
    private val speciesExtra: Map<Int, Triple<String, String, String>> by lazy {
        val out = HashMap<Int, Triple<String, String, String>>()
        javaClass.getResourceAsStream(if (map.nameSet == "maxdex") "/gen3/species-extra-maxdex.tsv" else if (map.expandedSpeciesIds) "/gen3/species-extra-natdex.tsv" else "/gen3/species-extra.tsv")
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
    fun evolution(species: Int): String? = EvoText.clean(speciesExtra[species]?.second)

    private val damageWatch = DamageWatch()
    private var randomizedCache: RandomizedFlags? = null

    /**
     * PokemonData / MoveData checkIfDataIsRandomized, from the ROM. Computed once
     * the ROM answers (Bulbasaur's stats all zero means it has not loaded yet);
     * null on a layout it does not know (Nat. Dex), which callers read as randomized.
     */
    fun randomized(): RandomizedFlags? {
        randomizedCache?.let { return it }
        if (map.baseStatsStride != 28 || map.baseStats == 0L) return null
        fun mon(sp: Int): RandomizedFlags.Mon? {
            val b = memory.read(map.baseStats + sp * 28L, 28)
            if (b.size < 28) return null
            return RandomizedFlags.Mon(List(6) { b.u8(it) }, b.u8(6) to b.u8(7), b.u8(22) to b.u8(23),
                b.u8(0x12), b.u8(9), learnset(sp).map { (level, move) -> move to level })
        }
        fun move(id: Int): RandomizedFlags.Move? = moveData(id)?.let { RandomizedFlags.Move(it[0], it[1], it[2], it[3]) }
        val b = mon(1) ?: return null
        if (b.stats.all { it == 0 }) return null
        val l = mon(131) ?: return null
        val s = mon(213) ?: return null
        val a = move(314) ?: return null
        val c = move(128) ?: return null
        return RandomizedFlags.detect(b, l, s, a, c).also { randomizedCache = it }
    }
    private var friendshipRequiredCache = 0

    /**
     * Program.lua:343: the byte plus one, kept only once the ROM gives a real
     * byte. A ROM still loading reads 0, and latching that would stick at the
     * fallback for the whole session.
     */
    fun friendshipRequired(): Int {
        if (friendshipRequiredCache != 0) return friendshipRequiredCache
        if (map.friendshipRequiredAddr == 0L) return EvoText.DEFAULT_REQUIRED
        val b = memory.read(map.friendshipRequiredAddr, 1)
        val v = if (b.isEmpty()) 0 else (b[0].toInt() and 0xFF)
        // Program.lua:344 (and the extension's updateFriendshipValues): only 2..220 is kept.
        if (v == 0 || v + 1 > EvoText.DEFAULT_REQUIRED) return EvoText.DEFAULT_REQUIRED
        friendshipRequiredCache = v + 1
        return friendshipRequiredCache
    }

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
        val natDex = map.expandedSpeciesIds
        for (atk in Gen3Types.typesFor(natDex)) {
            val e = Gen3Types.effect(atk, b.type1, b.type2, natDex)
            if (e != 1.0) out.getOrPut(e) { mutableListOf() }.add(Gen3Types.name(atk))
        }
        return out
    }

    private val moveDescs by lazy { loadDesc("/gen3/movedesc.tsv") }
    private val abilityDescs by lazy { loadDesc("/gen3/abilitydesc.tsv") }

    /**
     * The Nat. Dex builds' moves past 354, one description each, in its source's words: the Nat. Dex Extension's own
     * where it has one, else the DS tracker's for a move Black and White already had, else Pokemon Showdown's short
     * one, or KaizoCore's own line where that says nothing (natdex/movedesc.tsv, made by
     * tools/trainer-data/convert_natdex_move_desc.py). Before rc34.1 nothing past 354 had one, so Fairy Wind showed none.
     */
    private val natDexMoveDescs by lazy { loadDesc("/natdex/movedesc.tsv") }

    /** The same text by move name, for MaxDex: it numbers its moves its own way (Fairy Wind is 358 there, 584 here). */
    private val natDexMoveDescsByName by lazy { natDexMoveDescs.values.associate { moveKey(it.first) to it.second } }

    /**
     * The move's description for the info screen. Ids 1-354 are Gen 3's moves on every build and keep the reference's
     * Gen 3 text (gen3/movedesc.tsv). Past them, a Nat. Dex build reads natdex/movedesc.tsv by id and MaxDex by the
     * move's name (its list, maxdex/moves.tsv); the five games have no move past 354.
     */
    fun moveDescription(moveId: Int): String? {
        val text = when {
            moveId <= 0 -> null
            moveId <= VANILLA_LAST_MOVE -> moveDescs[moveId]?.second
            map.nameSet == "maxdex" -> natDexMoveDescsByName[moveKey(moveName(moveId))]
            map.namesFromLists -> natDexMoveDescs[moveId]?.second
            else -> null
        }
        return text?.takeIf { it.isNotBlank() }
    }

    /** A move name as a match key: case, spaces and punctuation ignored ("U-turn" and "U-Turn" are one move). */
    private fun moveKey(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /**
     * The ability's description, then its Emerald-only effect under the reference's label
     * (InfoScreen.lua:1029-1041): Resources.InfoScreen.LabelEmeraldAbility plus ":", "In
     * Emerald:" (Languages/English.lua:441), on a line of its own after a gap. The info
     * screen shows it in every game, FireRed included: DataHelper.buildAbilityInfoDisplay
     * always fills it (DataHelper.lua:549) and the screen never checks the game. Only the
     * stream chat command limits it to Emerald (EventData.lua:222), and the app has none.
     * It used to be appended unlabelled, in brackets, and only on Ruby, Sapphire and Emerald
     * (parity audit, 2026-09-28).
     */
    fun abilityDescription(abilityId: Int): String? {
        val d = abilityDescs[abilityId] ?: return null
        val base = d.second.takeIf { it.isNotBlank() }
        val emerald = d.third.takeIf { it.isNotBlank() }?.let { "In Emerald:\n$it" }
        return listOfNotNull(base, emerald).joinToString("\n\n").ifEmpty { null }
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
    fun coverage(moveTypes: List<Int>, fullyEvolvedOnly: Boolean = false): Map<Double, List<Int>> {
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
            // CoverageCalcScreen's OptionOnlyFullyEvolved: skip anything that still evolves.
            if (fullyEvolvedOnly && evolution(id) != null) continue
            var best = 0.0
            for (t in moveTypes) {
                val e = Gen3Types.effect(t, b.type1, b.type2, map.expandedSpeciesIds)
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
    private fun readGameOver(party: List<TrackedMon>, inBattle: Boolean): GameOver? {
        val lead = party.firstOrNull() ?: return null
        if (map.battleOutcome != 0L && map.trainerOpponent != 0L) {
            val outcome = memory.read(map.battleOutcome, 1)
            val opp = memory.read(map.trainerOpponent, 2)
            if (outcome.isNotEmpty() && outcome.u8(0) == 1 && opp.size == 2 &&
                opp.u16(0) in map.finalTrainers
            ) return GameOver.WON
        }
        // Battle.lua:190 asks GameOverScreen.checkForGameOver only once a battle's data is
        // ready, so a Pokemon fainting to poison on the overworld does not end the run.
        // Eggs never count. Level 0 is a slot not decoded yet; LossCondition guards it.
        if (inBattle && lossCondition.lostMons(party.map { LossMon(it.mon.level, it.mon.curHp, it.mon.isEgg) })) return GameOver.LOST
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
    /** SaveBlock1's base: the map's fixed address, else through its IWRAM pointer; null when unreadable. */
    private fun saveBlock1(): Long? {
        if (map.saveBlock1Fixed != 0L) return map.saveBlock1Fixed
        if (map.saveBlock1Ptr == 0L) return null
        val p = memory.read(map.saveBlock1Ptr, 4)
        if (p.size != 4) return null
        val sb1 = p.u32(0)
        return if (sb1 in 0x02000000L..0x0203FFFFL) sb1 else null
    }

    /** SaveBlock2's base, the same way. */
    private fun saveBlock2(): Long? {
        if (map.saveBlock2Fixed != 0L) return map.saveBlock2Fixed
        if (map.saveBlock2Ptr == 0L) return null
        val p = memory.read(map.saveBlock2Ptr, 4)
        if (p.size != 4) return null
        val sb2 = p.u32(0)
        return if (sb2 in 0x02000000L..0x0203FFFFL) sb2 else null
    }

    fun readGameStat(index: Int): Int {
        if (map.gameStatsOffset == 0L) return 0
        val sb1 = saveBlock1() ?: return 0
        val raw = memory.read(sb1 + map.gameStatsOffset + index * 4L, 4)
        if (raw.size < 4) return 0
        val key = readSecurityKey32()
        val v = (raw.u32(0) xor key) and 0xFFFFFFFFL
        // A stat past a few million is a failed read, not a long playthrough.
        return if (v > 90_000_000L) 0 else v.toInt()
    }

    /** The full 32-bit security key. Item quantities use only its low half. */
    private fun readSecurityKey32(): Long {
        if (map.encryptionKeyOffset == 0L) return 0
        val sb2 = saveBlock2() ?: return 0
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
        if (map.encryptionKeyOffset == 0L) return 0
        val sb2 = saveBlock2() ?: return 0
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
        // An id past the table (the GhostId, 413 or 1285) would read the pointer after its end.
        if (!speciesIsValid(species)) return@getOrPut emptyList()
        val ptrBytes = memory.read(map.levelUpLearnsets + species.toLong() * 4, 4)
        if (ptrBytes.size < 4) return@getOrPut emptyList()
        val addr = ptrBytes.u32(0)
        if (addr !in 0x08000000L..0x09FFFFFFL) return@getOrPut emptyList()
        if (map.learnsetJambo) return@getOrPut jamboLearnset(addr)
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
     * Jambo's learnset format ([GameMap.learnsetJambo], MaxDex): 3-byte {u16 move, u8 level} entries, the list ending at
     * 00 00 FF, read as the randomizer reads it (Gen3RomHandler.getMovesLearnt). An entry with move 0 is a slot the
     * build left empty, filled when the randomizer runs; it is skipped, not taken for the end.
     */
    private fun jamboLearnset(addr: Long): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(24)
        for (i in 0 until 64) {
            val e = memory.read(addr + i * 3L, 3)
            if (e.size < 3) break
            val move = e.u16(0)
            val level = e.u8(2)
            if (move == 0 && level == 0xFF) break
            if (move == 0 || level > 100) continue
            out += level to move
        }
        return out
    }

    /**
     * Healing carried in the Items and Berries pockets, for a Pokemon with [maxHp] (HealTotals).
     *
     * Gen 3 XORs item quantities with a security key held in SaveBlock2, which is
     * why a naive read shows nonsense counts. Percentage-healing items (Full
     * Restore, berries) contribute their share of max HP; constant ones their
     * flat value, capped at a full heal each.
     */
    private fun readHeals(maxHp: Int): HealTotals {
        if (map.bagItemsOffset == 0L || maxHp <= 0) return HealTotals.NONE
        val sb1 = saveBlock1() ?: return HealTotals.NONE

        // The security key is in SaveBlock2 in both games (FireRed +0xF20,
        // Emerald +0xAC), NOT in SaveBlock1 where the bag itself lives. Reading
        // it from the wrong block yields 0, which silently leaves every quantity
        // encrypted - and an empty bag looks identical either way, so this is
        // asserted below rather than eyeballed.
        val key = readSecurityKey()

        // Item id to quantity: the reference works per item (Program.lua:1669), and two stacks of one item are one.
        val items = LinkedHashMap<Int, Int>()
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
                if (id in HEAL_ITEMS) items[id] = (items[id] ?: 0) + qty
            }
        }
        return HealTotals.of(items, maxHp) { HEAL_ITEMS[it] }
    }

    /**
     * The eight gym badges as bits, badge 1 in bit 0.
     *
     * FireRed keeps them in a byte; Emerald packs them into a word starting at
     * bit 7, which is why this is not simply "read a byte" for both.
     */
    /** Program.ActiveRepel.duration: grows to the repel that was used, back to 100 when it ends. */
    private var repelDuration = RepelRules.DEFAULT_DURATION

    /**
     * Program.updateRepelSteps: the save variable holding the steps the active
     * repel has left, 0 when none is running. A byte past a Max Repel's 250 is
     * not a repel (a wrong address, a hack's different layout), and reads as none.
     */
    internal fun readRepelSteps(): Int {
        if (map.repelStepsOffset == 0L) return 0
        val sb1 = saveBlock1() ?: return 0
        val b = memory.read(sb1 + map.repelStepsOffset, 1)
        val steps = if (b.isEmpty()) 0 else b.u8(0)
        return RepelRules.stepsOf(steps)
    }

    internal fun readBadges(): Int {
        if (map.badgeOffset == 0L) return 0
        val sb1 = saveBlock1() ?: return 0
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
    /** The map section and map type of the map [stableMapId] names, from the same header read; null mid-transition. */
    private var stableMapSection: Pair<Int, Int>? = null
    private val routeLog = ArrayDeque<String>()
    private fun logRoute(id: Int?) {
        // [id] is the raw id the game reports; routeInfo takes the tracker's (Ruby and Sapphire
        // come down one above 108), or every R/S map above 108 is logged under the next map's name.
        val info = id?.let { routeInfo(mapIdFromRaw(it)) }
        val name = info?.first ?: "-"
        val n = info?.second?.size ?: 0
        routeLog.addLast("%d map=%s route=%s wild=%d".format(
            System.currentTimeMillis(), id?.toString() ?: "null", name, n))
        while (routeLog.size > 300) routeLog.removeFirst()
    }
    /** Adopted map-id transitions, oldest first. For the ROUTE LOG button. */
    fun routeLogSnapshot(): List<String> = routeLog.toList()

    private var inBattleScreen = false
    private var battleDataReady = false
    private var isWildEncounter = false
    /** The battle on screen is a catching lesson (Wally, the Old Man, the Teachy TV): nobody's encounter. */
    private var lessonBattle = false
    /**
     * The battle on screen is a Battle Tent or Battle Frontier battle (Emerald) or a Battle Tower one (Ruby and Sapphire,
     * Emerald): the game lends a reduced or rental party for it and gives the real one back afterwards (pokeemerald
     * ReducePlayerPartyToSelectedMons and LoadPlayerParty), so nothing that happens to that party is a Nuzlocke event
     * (rc32 audit P2 #141). Held after such a battle until, outside a battle, the party reads with nobody at 0 HP or is
     * another party than the battle's, or a battle that is not one begins: a lost facility battle leaves the entrants
     * down until the game hands the real party back.
     */
    private var facilityHeld = false
    /** The personality values of the party a facility battle showed: another party out of the battle is the real one back. */
    private var facilityParty: Set<Long>? = null
    /** gPlayerPartyCount when the battle began (Battle.partySize): a battler at a party slot past it is a partner's. */
    private var battlePartySize = 6

    /**
     * gBattleTypeFlags' facility bits, as each game numbers them. Emerald: BATTLE_TYPE_BATTLE_TOWER (bit 8), DOME,
     * PALACE, ARENA, FACTORY, PIKE and PYRAMID (bits 16 to 21; the Battle Tents fight as Palace, Arena and Factory).
     * Ruby and Sapphire: the Battle Tower, bit 8, their only facility. FireRed and LeafGreen use bits 16 to 19 for
     * other battles (Pokedude, scripted wild, legendary, Trainer Tower: RouteData.lua:244, :253-256), so none there,
     * and none on Nat. Dex, whose flags are not the vanilla game's.
     */
    internal fun isFacilityBattle(flags: Long): Boolean = when {
        map.expandedSpeciesIds -> false
        map.routeVersion == "emerald" -> (flags and (0x100L or 0x3F0000L)) != 0L
        map.routeVersion == "ruby" || map.routeVersion == "sapphire" -> (flags and 0x100L) != 0L
        else -> false
    }
    /** This battle began as a Safari Zone encounter (see updateBattleStatus). */
    private var safariBattle = false

    /**
     * Program.isInSafariZone (Program.lua:1511): the SYS_SAFARI_MODE flag in
     * the save block's flags.
     */
    internal fun isInSafariZone(): Boolean {
        if (map.safariModeFlag == 0 || map.gameFlagsOffset == 0L) return false
        val sb1 = saveBlock1() ?: return false
        val flag = map.safariModeFlag
        val b = memory.read(sb1 + map.gameFlagsOffset + flag / 8, 1)
        return b.size == 1 && ((b[0].toInt() shr (flag % 8)) and 1) != 0
    }

    /**
     * Battle.lua:147: Tracker.getPokemon(1, false) ~= nil. The opponent's lead
     * in gEnemyParty: a non-zero personality or trainer id that decodes into
     * a real Pokemon (Program.updatePokemonTeams).
     */
    private fun enemyLeadPresent(): Boolean {
        if (map.enemyParty == 0L) return false
        val size = map.monLayout.size
        val slot = memory.read(map.enemyParty, size)
        if (slot.size < size || (slot.u32(0) == 0L && slot.u32(4) == 0L)) return false
        return sane(PokemonDecoder.decode(slot, map.monLayout))
    }

    // ---- Tracker.TrackSafariEncounter (Battle.lua:607-612) ----
    /** Raw map id to species to the highest level it was seen at, in the order met. */
    private val safariSeen = HashMap<Int, LinkedHashMap<Int, Int>>()
    private var safariSeenSpecies = -1

    /**
     * Tracker.getSafariEncounters: the wild Pokemon met on a Safari Zone map
     * this session, with the highest level each was seen at. The reference's
     * only reader is Stream Connect's !pivots command.
     */
    /** RouteData.Locations.IsInSafariZone for the tracker's [mapId]. */
    fun isSafariMap(mapId: Int): Boolean = rawMapId(mapId) in map.safariMapIds

    fun safariEncounters(rawMapId: Int): List<Pair<Int, Int>> =
        safariSeen[rawMapId]?.entries?.map { it.key to it.value } ?: emptyList()

    /**
     * Battle.incrementEnemyEncounter's tail: a wild battle on a map whose
     * RouteData has this encounter area records the species there, and on a
     * Safari Zone map also its level, raising a level already kept.
     */
    private fun trackSafariEncounter(mapId: Int, enemy: EnemyInfo, area: String) {
        if (enemy.species == safariSeenSpecies) return
        safariSeenSpecies = enemy.species
        val raw = rawMapId(mapId)
        if (raw !in map.safariMapIds || !routeEncounters(mapId).containsKey(area)) return
        val seen = safariSeen.getOrPut(raw) { LinkedHashMap() }
        if (enemy.level > (seen[enemy.species] ?: -1)) seen[enemy.species] = enemy.level
    }

    // ---- Battle.CurrentRoute.encounterArea (Battle.incrementEnemyEncounter) ----
    private var currentArea: String? = null
    // Tracker.Data.gameStatsFishing / gameStatsRockSmash: taken on the first read
    // outside a battle with the save in (readNow; Tracker.lua resets them from the
    // save), then compared at each battle start, so the first battle after loading
    // is not taken for fishing.
    private var fishingStat = -1
    private var rockSmashStat = -1

    private fun battleEncounterArea(): String? {
        currentArea?.let { return it }
        // Only when the tracker started in the middle of a battle: there was no read outside one to take them from.
        if (fishingStat < 0) { fishingStat = readGameStat(12); rockSmashStat = readGameStat(19) }
        val terrain = if (map.battleTerrain != 0L) rw(map.battleTerrain) else 0
        val flags = rd(map.battleTypeFlags)
        var area = encounterAreaByTerrain(terrain, flags, rsFirstBattle = map.badgeSet != "FRLG")
        val fishing = readGameStat(12)                        // FISHING_CAPTURES
        if (fishing != fishingStat) {
            fishingStat = fishing
            if (map.specialVarItemId != 0L) RODS[rw(map.specialVarItemId)]?.let { area = it }
        }
        val rockSmash = readGameStat(19)                      // USED_ROCK_SMASH
        if (rockSmash > rockSmashStat) {
            rockSmashStat = rockSmash
            if (map.specialVarResultAny != 0L && rw(map.specialVarResultAny) == 1) area = "RockSmash"
        }
        currentArea = area
        return area
    }

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
    override fun isChoosingActionInWild(): Boolean {
        if (!inBattleScreen || !isWildEncounter) return false
        if (map.battleMainFunc == 0L || map.handleTurnAction == 0L) return false
        val f = memory.read(map.battleMainFunc, 4)
        if (f.size != 4 || f.u32(0) != map.handleTurnAction) return false
        if (map.battleOutcome != 0L) {
            val o = memory.read(map.battleOutcome, 1)
            if (o.isNotEmpty() && o.u8(0) != 0) return false
        }
        // The action menu itself (2026-10-01): gBattleMainFunc is the same inside the Bag, the party screen and the
        // move menu opened from it. B-to-Run fired in the Bag, and its A picked an item again after every B, so the Bag
        // could not be closed; after a B out of the move menu it ran. The player is battler 0 in a wild battle.
        if (map.battleCommunication == 0L) return false
        val c = memory.read(map.battleCommunication, 1)
        return c.isNotEmpty() && c.u8(0) == map.actionMenuState
    }

    /** A species this game has (PokemonData.getNatDexCompatible is not BlankPokemon): GachaMon's prize card asks. */
    fun speciesExists(id: Int): Boolean = speciesIsValid(id) && id !in 252..276

    /**
     * The name of the place [trainerId] stands (TrainerData.getTrainerInfo(id).routeId in RouteData.Info), from this
     * version's route table: the first map whose trainers hold it. Null where none does. GachaMon's prize card shows it.
     */
    fun routeNameOfTrainer(trainerId: Int): String? =
        routeInfoTable.entries.firstOrNull { trainerId in it.value.second }?.value?.first?.takeIf { it.isNotBlank() }

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
        // Battle.lua:176-181. A Safari Zone battle LOOKS fake: the player sends
        // nothing out, so gBattleMons[0] holds no species. The reference asks
        // Program.isInSafariZone only then, and only when the opponent's lead
        // is in gEnemyParty (its battleStatusActive), and lets the battle start.
        // Without this no Safari battle ever began: no enemy card, no moves,
        // no encounter recorded.
        val safariEncounter = !inBattleScreen && outcome == 0 && fakeBattle &&
            enemyLeadPresent() && isInSafariZone()
        // A Safari battle stays fake to its end; the outcome alone ends it, as
        // in the reference, whose end check never looks at gBattleMons.
        val statusActive = outcome == 0 && (!fakeBattle || safariEncounter || (inBattleScreen && safariBattle))

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
            // BATTLE_TYPE_WALLY_TUTORIAL / OLD_MAN_TUTORIAL is bit 9 in every Gen 3 game; FireRed and LeafGreen's
            // Teachy TV battles are BATTLE_TYPE_POKEDUDE, bit 16 (pokeemerald and pokefirered constants/battle.h).
            lessonBattle = (flags and 0x200L) != 0L || (isFrlg && (flags and 0x10000L) != 0L)
            facilityHeld = isFacilityBattle(flags)
            facilityParty = null
            // Battle.beginNewBattle (Battle.lua:764-769) reads gPlayerPartyCount once, here: a slot past it is a partner's.
            battlePartySize = rb(map.partyCount).takeIf { it in 1..6 } ?: 6
            inBattleScreen = true
            battleDataReady = false
            safariBattle = fakeBattle
            safariSeenSpecies = -1
            clearBattleMoves()
        } else if (inBattleScreen && !battleDataReady && atDataStart) {
            battleDataReady = true
        } else if (inBattleScreen && !statusActive && atDataEnd) {
            inBattleScreen = false
            battleDataReady = false
            safariBattle = false
            isWildEncounter = false
            lessonBattle = false
            battlePartySize = 6
            clearBattleMoves()
        }
        return inBattleScreen && battleDataReady
    }

    /**
     * Which of the opposing party's slots are filled, and which still stand.
     *
     * Read from gEnemyParty with the same stride and sanity rules as the
     * player's, so a wrong map cannot turn into a row of phantom pokeballs.
     */
    private fun readEnemyParty(): List<EnemyPartyMon> {
        if (map.enemyParty == 0L) return emptyList()
        val monSize = map.monLayout.size
        val bytes = memory.read(map.enemyParty, 6 * monSize)
        if (bytes.size < monSize) return emptyList()
        val out = ArrayList<EnemyPartyMon>(6)
        for (i in 0 until minOf(6, bytes.size / monSize)) {
            val slot = bytes.copyOfRange(i * monSize, (i + 1) * monSize)
            if (PokemonDecoder.isEmpty(slot)) continue
            val mon = PokemonDecoder.decode(slot, map.monLayout)
            if (!sane(mon)) continue
            out += EnemyPartyMon(i, mon.species, mon.level, mon.curHp > 0, mon.pid, mon.shiny)
        }
        return out
    }

    /**
     * Battle.lua:276 and :289: a player's battler, 0 or (in a double battle) 2, its party slot; null where
     * gBattlerPartyIndexes is unknown or holds no party slot.
     */
    private fun readOwnOnField(battler: Int = 0): Int? {
        if (map.battlerPartyIndexes == 0L) return null
        val idx = memory.read(map.battlerPartyIndexes + battler * 2L, 2)
        return if (idx.size == 2) idx.u16(0).takeIf { it in 0..5 } else null
    }

    /** Battle.lua:277 and :290: the opposing battlers' party slots, battler 1 and, in doubles, 3. */
    private fun readEnemyOnField(): List<Int> {
        if (map.battlerPartyIndexes == 0L) return emptyList()
        val four = map.battlersCount != 0L && rb(map.battlersCount) == 4
        return (if (four) listOf(1, 3) else listOf(1)).mapNotNull { b ->
            val idx = memory.read(map.battlerPartyIndexes + b * 2L, 2)
            if (idx.size == 2) idx.u16(0).takeIf { it in 0..5 } else null
        }
    }

    /** FireRed, LeafGreen and FireRed Nat. Dex: GameSettings.game == 3 in the reference. */
    private val isFrlg: Boolean get() = map.badgeSet == "FRLG"

    /**
     * PokemonData.Values.GhostId: 413 in the reference, 1285 once the Nat. Dex
     * extension has run (NatDexExtension.lua:16912). Neither is a species; it
     * is the id the ghost stand-in carries, and the bundled sprite pack's 1285
     * is the reference's ghost icon.
     */
    val ghostSpeciesId: Int get() = if (map.expandedSpeciesIds) NATDEX_GHOST_ID else GHOST_ID

    /**
     * Battle.lua:371, Battle.isGhost: BATTLE_TYPE_GHOST (bit 15) set and
     * BATTLE_TYPE_GHOST_UNVEILED (bit 13) clear, on FireRed and LeafGreen only.
     * That is a Pokemon Tower ghost met before the Silph Scope, including the
     * Marowak. Read live, like the reference, from gBattleTypeFlags.
     */
    internal fun isGhostBattle(mem: MemoryReader = memory): Boolean {
        if (!isFrlg || map.battleTypeFlags == 0L) return false
        val b = mem.read(map.battleTypeFlags, 4)
        val flags = if (b.size == 4) b.u32(0) else 0L
        return (flags shr 15) and 1L == 1L && (flags shr 13) and 1L == 0L
    }

    /**
     * Tracker.getPokemon(slot, false) during a ghost battle: the reference hands
     * back Tracker.getGhostPokemon() with only the real level copied in
     * (Tracker.lua:121). "Ghost", both types unknown (Gen 3's type 9, the
     * reference's PokemonData.Types.UNKNOWN), no base stats, evolution,
     * ability, moves, status or stat stages. HP stays the battler's own: the
     * card never prints an enemy's HP, and a zero would draw it fainted.
     */
    private fun ghostEnemy(): EnemyInfo? {
        val stride = map.battleMonSize
        val b = memory.read(map.battleMons + stride, stride)
        if (b.size < stride || !speciesIsValid(b.u16(0x00))) return null
        return EnemyInfo(
            species = ghostSpeciesId,
            speciesName = GHOST_NAME,
            level = b.u8(map.battleMonHp + 2),
            curHp = b.u16(map.battleMonHp),
            maxHp = b.u16(map.battleMonHp + 4),
            type1 = UNKNOWN_TYPE,
            type2 = UNKNOWN_TYPE,
            base = null,
            movesSeen = emptyList(),
            abilityGuess = "---",
            isGhost = true,
        )
    }

    /**
     * The species of the party Pokemon a battler that used Transform is, or null when it has not used it (or its party
     * slot cannot be read, when the battle struct stands). Transform copies every byte of the battle struct below pp:
     * species, stats, moves, the IV word with the ability bit, stages, ability and types (pokeemerald
     * Cmd_transformdataexecution; CFRU general_bs_commands.c:3446-3471), and sets STATUS2_TRANSFORMED. The reference reads
     * a battler as the party Pokemon it is (Battle.lua:276), so a wild Ditto that copied your Pokemon stayed a Ditto
     * there, while here it took your Pokemon's name and card and was logged as seen on the route (rc32 audit P2 #131).
     */
    private fun transformedSpecies(battler: Int, struct: ByteArray): Int? = transformedMon(battler, struct)?.species

    /** The Pokemon in [battler]'s party slot when its battle struct has used Transform; null otherwise or when unreadable. */
    private fun transformedMon(battler: Int, struct: ByteArray): PokemonDecoder.Mon? {
        if (struct.size < map.status2Offset + 4 || (struct.u32(map.status2Offset) and STATUS2_TRANSFORMED) == 0L) return null
        return partyMonOf(battler)
    }

    /** gBattlerPartyIndexes[battler] into the player's party (even battlers) or the enemy's (odd), decoded; null when unreadable. */
    private fun partyMonOf(battler: Int): PokemonDecoder.Mon? {
        if (map.battlerPartyIndexes == 0L) return null
        val idxB = memory.read(map.battlerPartyIndexes + battler * 2L, 2)
        val idx = if (idxB.size == 2) idxB.u16(0) else return null
        val party = if (battler % 2 == 0) map.party else map.enemyParty
        val size = map.monLayout.size
        if (idx !in 0..5 || party == 0L) return null
        val bytes = memory.read(party + idx.toLong() * size, size)
        if (bytes.size < size || PokemonDecoder.isEmpty(bytes)) return null
        return PokemonDecoder.decode(bytes, map.monLayout).takeIf { sane(it) }
    }

    /**
     * An opposing [battler]'s card: 1, or 3 for the right-hand one of a double battle (Combatants.LeftOther and
     * RightOther), everything read from that battler's own struct, party slot and stage block.
     */
    private fun readEnemy(battler: Int = 1): EnemyInfo? {
        val stride = map.battleMonSize
        val b = memory.read(map.battleMons + stride.toLong() * battler, stride)
        if (b.size < stride) return null
        // Not just "not zero": a stale battle struct holds values far outside
        // any species range, and rendering one puts an impossible number and
        // an impossible HP on the card.
        if (!speciesIsValid(b.u16(0x00))) return null
        // A transformed opponent is the Pokemon in its party slot; its level, HP, stages and the types it took stay the
        // battle struct's, as the reference keeps them (Program.getPokemonTypes reads gBattleMons, Program.lua:1355-1363).
        val own = transformedMon(battler, b)
        val species = own?.species ?: b.u16(0x00)
        // Its moves and their PP too: the reference's card is Tracker.getPokemon(slot, false), the Pokemon in gEnemyParty
        // (Program.lua:855-871), its moves and PP read from the party struct (Program.lua:999-1003, DataHelper.lua:268-331),
        // which Transform does not touch. The battle struct holds the copied moves at 5 PP, and those were shown as its
        // own (RC35-NOTICED N #35).
        val moveIds = own?.moves ?: List(4) { b.u16(0x0C + it * 2) }
        val pps = own?.pp ?: List(4) { b.u8(map.battleMonPp + it) }

        // What this Pokemon has been seen to use this battle (pollMoves), by its party slot: a foe that switches out and
        // back keeps its moves, and nothing another foe used lands on it.
        val movesSeen = enemySlotOf(battler)?.let { s -> synchronized(battleMoves) { battleMoves[s]?.toList() } } ?: emptyList()

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
            evo = EvoText.forEnemy(evolution(species)),
            // Personality sits just below status1 and status2: +0x48 vanilla, +0x4C on Nat. Dex, whose 12-letter nickname
            // moves everything after it up 4 (rc33 audit P1 #73: +0x48 there is the experience).
            pid = b.u32(map.status2Offset - 8),
            moves = moveIds,
            movePps = pps,
            level = b.u8(map.battleMonHp + 2),
            curHp = b.u16(map.battleMonHp),
            maxHp = b.u16(map.battleMonHp + 4),
            type1 = b.u8(map.battleMonTypes),
            type2 = b.u8(map.battleMonTypes + 1),
            base = base,
            movesSeen = movesSeen.map { moveName(it) },
            moveRows = movesSeen.map { id ->
                val d = moveData(id)
                MoveRow(
                    id = id,
                    name = moveName(id),
                    // "Count enemy PP usage", on by default: the opponent's real
                    // remaining PP from its battle struct (moves at 0x0C, PP at
                    // 0x24), or its party slot's after Transform, for a move in its
                    // moveset now; base PP otherwise.
                    pp = (0 until 4).firstOrNull { TrackerPrefs.countEnemyPp && moveIds.getOrNull(it) == id }?.let { pps.getOrNull(it) } ?: d?.get(3) ?: 0,
                    ppMax = null,          // an enemy's used PP is not readable
                    power = d?.get(0),
                    acc = d?.get(2),
                    type = d?.get(1),
                    category = d?.let { categoryOf(it) },
                    priority = d?.get(4)?.takeIf { it in -7..7 },
                    contact = d?.let { (it[5] and 1) != 0 },
                )
            },
            abilityGuess = possible,
            abilityId = abilityId,
            statStages = readStatStages(battler),
            // From the enemy PARTY slot, not from gBattleMons. See enemyPartyStatus.
            statusCondition = enemyPartyStatus(species, b.u8(map.battleMonHp + 2), b.u16(map.battleMonHp))
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
    internal val abilityScripts: Map<Long, List<Triple<String, Set<Int>, String>>> by lazy {
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
    /**
     * The player's side in battle: battler 0, and battler 2 in a double. The
     * ability is the mon's own (its party Pokemon's species and ability slot,
     * against its base stats), as the reference's PokemonData.getAbilityId(id,
     * abilityNum) reads it, not the live +0x20 byte that Trace or Skill Swap
     * change.
     */
    internal fun readOwnAbilities(): List<Pair<Int, String>> {
        if (map.battleMons == 0L) return emptyList()
        val nB = memory.read(map.battlersCount, 1)
        val n = if (nB.isEmpty()) 2 else (nB[0].toInt() and 0xFF)
        val out = ArrayList<Pair<Int, String>>(2)
        for (i in if (n >= 4) listOf(0, 2) else listOf(0)) {
            // The party Pokemon the battler is (Battle.lua:485-500: Tracker.getPokemon and PokemonData.getAbilityId),
            // not its battle struct: after your Transform that holds the foe's species and ability bit, and the foe's
            // real ability was recorded with no message in the game. A battler past the party's size when the battle
            // began is a partner's, Steven's in Emerald's multi battle, and is not yours to record (Battle.lua:486, :496;
            // rc32 audit P2 #131). The battle struct stands only where the party slot cannot be read.
            val idxB = if (map.battlerPartyIndexes == 0L) ByteArray(0) else memory.read(map.battlerPartyIndexes + i * 2L, 2)
            if (idxB.size == 2 && idxB.u16(0) >= battlePartySize) continue
            val b = memory.read(map.battleMons + i.toLong() * map.battleMonSize, map.battleMonSize)
            if (b.size < 0x18) continue
            val own = partyMonOf(i)
            if (own == null && b.size >= map.status2Offset + 4 && (b.u32(map.status2Offset) and STATUS2_TRANSFORMED) != 0L) continue
            val sp = own?.species ?: b.u16(0)
            val base = baseStats(sp) ?: continue
            val slot = own?.abilitySlot ?: ((b.u32(0x14) ushr 31) and 1L).toInt()
            val id = if (slot == 1 && base.ability2 != 0) base.ability2 else base.ability1
            if (sp != 0 && id != 0) out += sp to abilityName(id)
        }
        return out
    }

    /**
     * The reference looks for an ability message every 10 EMULATED frames,
     * because it runs inside the emulator's frame loop. read() runs every 250 ms
     * of wall time: at fast-forward that is 60 frames and more, and Faster
     * FireRed's short battle text left the message on screen for less than
     * that, so reveals were missed (2026-09-27, Blake: abilities not tracked on
     * FireRed). This reads only the battle-script pointer and a few bytes, so
     * the play screen calls it every few frames between full reads.
     *
     * That was still a wall clock: every 31 ms is about 15 emulated frames at
     * 8x and 30 at 16x, more around each full read, and a short message was
     * still missed there (Blake, 2026-10-04: missed at 8x, caught at 4x). Where
     * the emulator has the trigger tap ([triggerTap]), every frame is looked at
     * and these polls only collect what it caught (drainTap).
     */
    private val pendingReveals = ArrayList<Pair<Int, String>>()

    fun pollAbilityTrigger() {
        // The move tracking wants the same fast polls: a move action can be over in a quarter second at 4x.
        if (inBattleScreen && battleDataReady) runCatching { pollMoves() }
        synchronized(pendingReveals) { runCatching { drainTap() } }
        val r = runCatching { readAbilityTriggers() }.getOrNull() ?: return
        synchronized(pendingReveals) {
            // The same message stays up across several polls, and one message can reveal two: each once.
            for (p in r) if (p !in pendingReveals) pendingReveals += p
        }
    }

    /**
     * The emulator's per-frame watch on the battle-script pointer (TriggerTap; libretrodroid's cpp/triggertap.h).
     * Setting it arms it with this game's ability scripts and the memory their check reads ([tapPlan]); null is the
     * old way, the polls alone.
     */
    var triggerTap: TriggerTap? = null
        set(value) {
            if (field !== value) field?.let { old -> runCatching { old.disarm() } }
            field = value
            tapArmed = value != null && runCatching {
                tapPlan?.let { p -> value.arm(p.watch, p.targets, p.addresses, p.lengths) } ?: false
            }.getOrDefault(false)
        }
    private var tapArmed = false

    /**
     * What the tap watches and copies. The pointer is gBattlescriptCurrInstr and the values are this game's ability
     * script addresses ([abilityScripts], the reference's GameSettings.ABILITIES). The copy is every byte that changes
     * during a battle and that [readAbilityTriggers] reads (Battle.readBattleValues, Battle.lua:542-547, the attacker
     * from :322, and the battle parties' abilities, :616-726): the battler count, the scripting battler, the attacker and
     * the target, the Trace message's battler (gBattleTextBuff1 + 2, :648), the battlers' party slots, each battler's
     * species, IV word and live ability byte, the battle type flags (the ghost check, :505) and gBattleOutcome. The
     * parties and the species data stay as they were from that frame to the drain and are read live. The tap reads
     * only the work RAM (TapPlan.inRam), where every one of these is kept, except Ruby and Sapphire's gBattleTextBuff1,
     * which sits in IWRAM there and is read live too (it only matters to Trace in a double battle).
     */
    internal val tapPlan: TapPlan? by lazy {
        if (!TapPlan.inRam(map.scriptCurrInstr, 4) || map.battleMons == 0L || abilityScripts.isEmpty()) return@lazy null
        val addresses = ArrayList<Long>(); val lengths = ArrayList<Int>()
        fun copy(address: Long, length: Int) { if (TapPlan.inRam(address, length)) { addresses += address; lengths += length } }
        copy(map.battlersCount, 1)
        copy(map.scriptingBattler, 1)
        copy(map.battlerAttacker, 1)
        copy(map.battlerTarget, 1)
        copy(map.battleOutcome, 1)
        copy(map.battleTypeFlags, 4)
        copy(map.battleTextBuff1.let { if (it == 0L) 0L else it + 2 }, 1)
        copy(map.battlerPartyIndexes, 8)
        // Species at 0, the IV word with the ability bit at 0x14, the live ability byte at 0x20.
        for (i in 0 until 4) copy(map.battleMons + i.toLong() * map.battleMonSize, 0x21)
        TapPlan(map.scriptCurrInstr, abilityScripts.keys.sorted().toLongArray(), addresses.toLongArray(), lengths.toIntArray())
    }

    /**
     * Run the reference's check on every frame the tap caught since the last drain, as that frame left the game. Battle.update
     * (Battle.lua:116-139) looks only during a battle, so a caught frame counts only if gBattleOutcome was still 0 on it
     * (Battle.lua:150-151); a frame the pointer only reaches inside a battle's scripts needs nothing more. A reveal found
     * this way stands even if this read has not seen the battle start yet: at 8x the whole intro can pass between two
     * reads. Call with [pendingReveals] held; what it finds waits in [tapReveals] for the next read.
     */
    private fun drainTap() {
        val tap = triggerTap ?: return
        if (!tapArmed) return
        val plan = tapPlan ?: return
        val bytes = runCatching { tap.drain() }.getOrNull() ?: return
        for (hit in TapHits.parse(bytes, plan.contextLength)) {
            val view = plan.view(memory, hit)
            if (map.battleOutcome != 0L) {
                val o = view.read(map.battleOutcome, 1)
                if (o.size == 1 && o[0].toInt() != 0) continue
            }
            for (p in readAbilityTriggers(view)) if (p !in tapReveals) tapReveals += p
        }
    }

    /** The tap's reveals waiting for the next read; [pendingReveals]' lock guards both. */
    private val tapReveals = ArrayList<Pair<Int, String>>()

    /**
     * One poll of the move tracking (EnemyMoveWatch, Battle.lua:376-445), from the fast battle poll and from read(): a
     * move it records is filed under the opposing party slot that used it. Off in a ghost battle (Battle.lua:390) and
     * on a map without the reads.
     */
    private fun pollMoves() {
        if (map.currentTurnActionNumber == 0L || map.actionsByTurnOrder == 0L || map.hitMarker == 0L || map.battleResults == 0L ||
            map.battlerAttacker == 0L || map.battlersCount == 0L || map.battleCommunication == 0L) return
        if (isGhostBattle()) return
        val n = rb(map.battlersCount).coerceIn(2, 4)
        val actionNumber = rb(map.currentTurnActionNumber)
        val last = memory.read(map.battleResults + 0x22, 4)
        if (last.size < 4) return
        val frame = EnemyMoveWatch.Frame(
            battlers = n,
            attacker = rb(map.battlerAttacker),
            scriptingBattler = if (map.scriptingBattler != 0L) rb(map.scriptingBattler) % n else 0,
            actionNumber = actionNumber,
            action = rb(map.actionsByTurnOrder + actionNumber),
            confirmedCount = rb(map.battleCommunication + 4),
            hitMarker = rd(map.hitMarker),
            script = if (map.scriptCurrInstr != 0L) rd(map.scriptCurrInstr) else 0L,
            lastMovePlayer = last.u16(0),
            lastMoveOpponent = last.u16(2),
        )
        val (battler, move) = moveWatch.tick(frame, map.moveScripts, if (map.expandedSpeciesIds) 2000 else 354, ::partyMovesOf) ?: return
        val slot = enemySlotOf(battler) ?: return
        synchronized(battleMoves) { battleMoves.getOrPut(slot) { LinkedHashSet() }.add(move) }
    }

    private fun clearBattleMoves() {
        synchronized(battleMoves) { battleMoves.clear() }
        moveWatch.reset()
    }

    /** gBattlerPartyIndexes: the enemy party slot an opposing [battler] is; null for your side or when unknown. */
    private fun enemySlotOf(battler: Int): Int? {
        if (battler % 2 == 0 || map.battlerPartyIndexes == 0L || map.enemyParty == 0L) return null
        val idx = memory.read(map.battlerPartyIndexes + battler * 2L, 2)
        return if (idx.size == 2) idx.u16(0).takeIf { it in 0..5 } else null
    }

    /** An opposing [battler]'s four moves as its Pokemon has them in its party (Battle.lua:432); empty when unreadable. */
    private fun partyMovesOf(battler: Int): List<Int> {
        val slot = enemySlotOf(battler) ?: return emptyList()
        val size = map.monLayout.size
        val bytes = memory.read(map.enemyParty + slot.toLong() * size, size)
        if (bytes.size < size || PokemonDecoder.isEmpty(bytes)) return emptyList()
        return PokemonDecoder.decode(bytes, map.monLayout).moves
    }

    private fun enemyMovesThisBattle(team: List<EnemyPartyMon>): List<EnemyMovesSeen> = synchronized(battleMoves) {
        battleMoves.entries.sortedBy { it.key }.mapNotNull { (slot, ids) ->
            team.firstOrNull { it.slot == slot }?.let { m -> EnemyMovesSeen(m.species, m.level, ids.map { it to moveName(it) }) }
        }
    }

    /**
     * The reveals since the last read. What the tap caught was checked against its own frame (drainTap) and stands; the
     * live check, and the fast polls' reveals, only in an active battle that is not a ghost one ([live]), as before.
     */
    private fun drainReveals(live: Boolean): List<Pair<Int, String>> = synchronized(pendingReveals) {
        if (!live) pendingReveals.clear()
        runCatching { drainTap() }
        if (live) for (p in readAbilityTriggers()) if (p !in pendingReveals) pendingReveals += p
        for (p in tapReveals) if (p !in pendingReveals) pendingReveals += p
        tapReveals.clear()
        val out = pendingReveals.toList(); pendingReveals.clear(); out
    }

    /**
     * PokemonData.getAbilityId(pokemonID, abilityNum) for battler [i]: the party
     * Pokemon it is (gBattlerPartyIndexes into the player's or the enemy's party,
     * by side), decoded for its ability slot, then its species' ability. Falls back
     * to the battle struct's own IV word (+0x14, bit 31) when the index is unknown.
     */
    private fun partyAbility(i: Int, mem: MemoryReader = memory): Int {
        val battle = mem.read(map.battleMons + i.toLong() * map.battleMonSize, 0x18)
        if (battle.size < 0x18) return 0
        val battleSpecies = battle.u16(0)
        var species = battleSpecies
        var slot = ((battle.u32(0x14) ushr 31) and 1L).toInt()
        if (map.battlerPartyIndexes != 0L) {
            val idxB = mem.read(map.battlerPartyIndexes + i * 2L, 2)
            val idx = if (idxB.size == 2) idxB.u16(0) else -1
            val party = if (i % 2 == 0) map.party else map.enemyParty
            val size = map.monLayout.size
            if (idx in 0..5 && party != 0L) {
                val bytes = mem.read(party + idx.toLong() * size, size)
                if (bytes.size == size && !PokemonDecoder.isEmpty(bytes)) {
                    val mon = PokemonDecoder.decode(bytes, map.monLayout)
                    if (mon.species == battleSpecies) { species = mon.species; slot = mon.abilitySlot }
                }
            }
        }
        val base = baseStats(species) ?: return 0
        return if (slot == 1 && base.ability2 != 0) base.ability2 else base.ability1
    }

    /** The first of [readAbilityTriggers], or null. */
    internal fun readAbilityTrigger(): Pair<Int, String>? = readAbilityTriggers().firstOrNull()

    /**
     * Every ability the battle script on screen reveals. The reference runs each kind of row as a check of its own and
     * tracks every battler that matches (Battle.checkAbilitiesToTrack, Battle.lua:616-726), so one message can reveal
     * two: Clear Body, Hyper Cutter or White Smoke stopping Intimidate has a BATTLER row (the Intimidate) and a
     * REVERSE_BATTLER row (what stopped it) at the same address, the one address in any table with two. Returning at
     * the first match kept your Intimidate and dropped the foe's ability (rc32 audit P2 #132).
     *
     * [mem] is the memory the check reads: the live game, or one frame the trigger tap caught (TapPlan.view), which is
     * how a message too short for any poll at 8x or 16x is still checked exactly as the reference would have.
     */
    internal fun readAbilityTriggers(mem: MemoryReader = memory): List<Pair<Int, String>> {
        if (map.scriptCurrInstr == 0L || abilityScripts.isEmpty()) return emptyList()
        // Battle.lua:505: checkAbilitiesToTrack is skipped in a ghost battle.
        if (isGhostBattle(mem)) return emptyList()
        val msgB = mem.read(map.scriptCurrInstr, 4)
        if (msgB.size < 4) return emptyList()
        val rows = abilityScripts[msgB.u32(0)] ?: return emptyList()

        val nB = mem.read(map.battlersCount, 1)
        val n = (if (nB.isEmpty()) 0 else nB[0].toInt() and 0xFF)
            .coerceIn(1, 4)
        fun battlerByte(addr: Long): Int {
            val b = mem.read(addr, 1)
            return if (b.isEmpty()) 0 else (b[0].toInt() and 0xFF) % n
        }
        val battler = battlerByte(map.scriptingBattler)
        val attacker = battlerByte(map.battlerAttacker)
        val target = battlerByte(map.battlerTarget)

        fun abilityOf(i: Int): Int {
            // Nat. Dex: abilities are u16 there and the struct keeps its types at
            // 0x21 (the ROM's own offsetBattlePokemonTypes), so byte 0x20 cannot be
            // the ability; the tracker read garbage there and no enemy ability was
            // ever revealed (Blake, 2026-09-28, FireRed Nat. Dex). The reference never
            // reads that byte at all: a battler's ability is its species' ability for
            // the party Pokemon's ability slot (Battle.populateBattlePartyObject,
            // PokemonData.getAbilityId).
            if (map.abilitiesAreU16) return partyAbility(i, mem)
            // Vanilla: the live byte, which also follows Skill Swap and Role Play the
            // way the reference's own swap tracking does.
            val b = mem.read(map.battleMons + i * map.battleMonSize + 0x20L, 1)
            return if (b.isEmpty()) 0 else b[0].toInt() and 0xFF
        }
        fun speciesOf(i: Int): Int {
            val b = mem.read(map.battleMons + i * map.battleMonSize.toLong(), 2)
            return if (b.size < 2) 0 else b.u16(0)
        }
        val out = LinkedHashSet<Pair<Int, String>>()
        fun reveal(i: Int) {
            val sp = speciesOf(i); val ab = abilityOf(i)
            if (sp != 0 && ab != 0) out += sp to abilityName(ab)
        }
        // Trace: the game writes the traced ability into the holder's live byte before it runs this script (CFRU
        // ability_battle_effects.c:760-766, as pokeemerald's ABILITYEFFECT_TRACE), so that byte never read Trace here and
        // the reveal never fired. The reference tests its own record of the holder, still Trace (Battle.lua:637), and
        // reveals the Pokemon named in gBattleTextBuff1 + 2 (:646-649); without that address, the other side in a single.
        fun traced(holder: Int): Int? {
            if (map.battleTextBuff1 != 0L) mem.read(map.battleTextBuff1 + 2, 1).takeIf { it.size == 1 }?.let { b ->
                val t = b[0].toInt() and 0xFF
                if (t < n && t != holder) return t
            }
            return if (n == 2) 1 - holder else null
        }

        for ((trigger, ids, scope) in rows) {
            when (trigger) {
                "BATTLER" -> when {
                    TRACE in ids && partyAbility(battler, mem) == TRACE -> traced(battler)?.let { reveal(it) }
                    abilityOf(battler) in ids -> reveal(battler)
                }
                // Battle.lua:656-663: the target's ability stopped the battler's, and both are tracked.
                "REVERSE_BATTLER" -> if (abilityOf(target) in ids) { reveal(target); reveal(battler) }
                "ATTACKER" -> if (abilityOf(target) in ids) reveal(target)
                "REVERSE_ATTACKER" -> if (abilityOf(attacker) in ids) reveal(attacker)
                "STATUS_INFLICT" -> if (abilityOf(battler) in ids &&
                    battler == target) reveal(battler)
                "BATTLE_TARGET" -> when (scope) {
                    "other" -> if (abilityOf(attacker) in ids) reveal(attacker)
                    // Battle.lua:713-724: the Damp check walks every battler, 0 to numBattlers - 1. It stopped at the first
                    // two, so a doubles partner's Damp, battler 2 or 3, was never revealed (RC35-NOTICED N #36).
                    "both" -> for (i in 0 until n) {
                        if (abilityOf(i) in ids) reveal(i)
                    }
                    else -> if (abilityOf(target) in ids) reveal(target)
                }
            }
        }
        return out.toList()
    }

    // ---- The Nuzlocke reads (2026-09-29): the rules engine's extra facts, all cheap or cached ----

    private var nuzPrevInBattle = false
    private var nuzCaps: com.ironmonone.tracker.nuzlocke.LevelCapTable? = null
    private var nuzCapTries = 0
    /** The rival [nuzCaps] was worked out for. */
    private var nuzCapsRival: String? = null
    private val nuzOpponents = HashMap<Int, OpponentInfo>()

    /**
     * The level cap table for this game. The standard table (nuzlocke/levelcaps-gen3.tsv) first, then each boss's
     * cap replaced by the highest level on their real team read out of the loaded ROM, which is what a randomized
     * or level-scaled game needs. When the trainer data cannot be read the table stands, and its bosses say so
     * (BossCap.fromRom). A read that fell short is tried again on later polls, a few times, and then left.
     */
    internal fun levelCaps(): com.ironmonone.tracker.nuzlocke.LevelCapTable? {
        // A rival learned (or restored) after the table was read changes FireRed and LeafGreen's Champion: the table is
        // read again for it. It was cached for good on the first poll, so the Champion's cap stayed the highest of all
        // three teams for the whole run (rc32 audit P2 #130).
        val rival = rivalChoice
        if (rival != nuzCapsRival) { nuzCaps = null; nuzCapTries = 0; nuzCapsRival = rival }
        nuzCaps?.let { if (it.fromRom || nuzCapTries >= 20 || !hasTrainerData) return it }
        val key = com.ironmonone.tracker.nuzlocke.LevelCapTable.gameKey(map.routeVersion) ?: return null
        val standard = com.ironmonone.tracker.nuzlocke.LevelCapTable.standard(key)
        if (standard.bosses.isEmpty()) return null
        if (!hasTrainerData) { nuzCaps = standard; return standard }
        nuzCapTries++
        val levels = HashMap<String, Int>()
        for (b in standard.bosses) {
            // FireRed and LeafGreen's Champion has one entry per rival starter: the player's own once the rival is known.
            val ids = if (b.trainerIds.size > 1 && rival != null)
                b.trainerIds.filter { whichRival(it) == rival }.ifEmpty { b.trainerIds } else b.trainerIds
            val level = ids.mapNotNull { id -> trainer(id)?.party?.maxOfOrNull { it.level }?.takeIf { it in 1..100 } }.maxOrNull()
            if (level != null) levels[b.key] = level
        }
        return (if (levels.isEmpty()) standard else standard.withRomLevels(levels)).also { nuzCaps = it }
    }

    /** Who is being fought, worked out once per trainer id. */
    private fun nuzOpponent(id: Int): OpponentInfo = nuzOpponents.getOrPut(id) {
        val t = runCatching { trainer(id) }.getOrNull()
        val label = listOfNotNull(t?.className, t?.name).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .ifBlank { trainerClassName(id) ?: "Trainer $id" }
        OpponentInfo(id, label, trainerGroup(id), levelCaps()?.keyOfTrainer(id), t?.maxLevel?.takeIf { it in 1..100 })
    }

    /**
     * Which of the table's bosses are beaten. Gym leaders are the badges (badge N is bit N-1, the same order as the
     * gyms); the League and after are trainer flags, read only once all eight badges are held and only as far as
     * the first one still standing.
     */
    private fun nuzBeaten(badges: Int): Set<String> {
        val caps = levelCaps() ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (b in caps.bosses) {
            if (b.kind == "gym") { if (((badges shr (b.seq - 1)) and 1) == 1) out += b.key; continue }
            if ((badges and 0xFF) != 0xFF) break
            if (b.trainerIds.any { trainerDefeated(it) }) out += b.key else break
        }
        return out
    }

    /** Items and Berries pockets, each in one read; Poke Balls are not in it. Null when the bag cannot be read. */
    private fun nuzBag(): Map<Int, BagItem>? {
        val sb1 = saveBlock1() ?: return null
        val key = readSecurityKey()
        val out = LinkedHashMap<Int, BagItem>()
        for ((offset, slots) in listOf(map.bagItemsOffset to map.bagItemsSlots, map.bagBerriesOffset to map.bagBerriesSlots)) {
            if (offset == 0L || slots <= 0) continue
            val b = memory.read(sb1 + offset, slots * 4)
            if (b.size < slots * 4) return null
            for (slot in 0 until slots) {
                val id = b.u16(slot * 4); if (id == 0) continue
                val qty = b.u16(slot * 4 + 2) xor key
                if (qty in 1..999) out[id] = BagItem(itemName(id), (out[id]?.qty ?: 0) + qty)
            }
        }
        return out
    }

    /**
     * The game's battle style, the option in its OPTIONS menu, true for Set: bit 9 of the options word at
     * SaveBlock2 + 0x14 (OPTIONS_BATTLE_STYLE_SET is 1). The same in all five Gen 3 games: pokeruby's SaveBlock2
     * starts exactly as pokefirered's and pokeemerald's (name, gender, trainer id, play time, button mode, then the
     * text speed, frame, sound, battle style bits), and the Nat. Dex builds keep that header. Only where SaveBlock2
     * lives differs, and saveBlock2() knows it: fixed on Ruby and Sapphire, the pointer elsewhere, the ROM's own
     * published pointer on Nat. Dex (Blake, 2026-09-30: "That's an option in the game's actual options menu").
     */
    private fun nuzBattleStyle(): Boolean? {
        val sb2 = saveBlock2() ?: return null
        val b = memory.read(sb2 + 0x14, 2)
        if (b.size < 2) return null
        return ((b.u16(0) shr 9) and 1) == 1
    }

    private fun readNuzlocke(inBattle: Boolean, trainer: Boolean, enemy: EnemyInfo?, enemyParty: List<EnemyPartyMon>): NuzlockeReads? {
        val wasInBattle = nuzPrevInBattle
        nuzPrevInBattle = inBattle
        return runCatching {
            val badges = readBadges()
            val opponentId = if (inBattle && trainer) readOpponentTrainerId() else null
            // The wild Pokemon on the field, found in the enemy party by its personality value, or failing that the only one of its kind.
            val shiny = enemy != null && !trainer && (
                enemyParty.firstOrNull { it.pid != 0L && it.pid == enemy.pid }
                    ?: enemyParty.singleOrNull { it.species == enemy.species && it.level == enemy.level })?.shiny == true
            NuzlockeReads(
                battleOutcome = if (map.battleOutcome != 0L) rb(map.battleOutcome) else 0,
                ballCount = if (map.bagBallsOffset == 0L || saveBlock1() == null) -1 else bagBalls().values.sum(),
                bag = if (inBattle || wasInBattle) nuzBag() else null,
                turn = if (inBattle && map.battleResults != 0L) rb(map.battleResults + map.battleResultsTurnOffset) else -1,
                battleStyleSet = nuzBattleStyle(),
                enemyShiny = shiny,
                opponent = opponentId?.let { nuzOpponent(it) },
                caps = levelCaps(),
                beaten = nuzBeaten(badges),
                lesson = inBattle && lessonBattle,
                facility = facilityHeld,
                staticsGame = when (map.routeVersion) { "ruby", "sapphire" -> "rs"; "emerald" -> "e"; else -> "frlg" },
                mapSection = stableMapSection?.first ?: -1,
                mapType = stableMapSection?.second ?: -1,
            )
        }.getOrNull()
    }
}
