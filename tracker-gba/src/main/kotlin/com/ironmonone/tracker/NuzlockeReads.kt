package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.LevelCapTable

/** One item in the bag, for the Nuzlocke item check. */
data class BagItem(val name: String, val qty: Int)

/** The trainer being fought, as the level cap check needs to know them. */
data class OpponentInfo(
    val trainerId: Int,
    /** The game's class and name: "LEADER Roxanne". */
    val label: String,
    /** The tracker's group: Gym, Elite4, Boss, Rival or Other. */
    val group: String,
    /** The level cap table's key when this trainer is one of its bosses. */
    val bossKey: String?,
    /** The highest level on their team, read from the game's trainer data. */
    val maxLevel: Int?,
)

/**
 * What the Nuzlocke rules engine needs from a Gen 3 game beyond the rest of [TrackerState] (2026-09-29).
 *
 * Read once per poll and kept cheap: single bytes, or cached (the level cap table and each opponent are worked out
 * once). The bag is read only while a battle is on and on the first poll after it, which is all the item check
 * compares. None of it is shown by the tracker itself; NuzlockeAdapter turns it into the engine's snapshot.
 */
data class NuzlockeReads(
    /** gBattleOutcome. It is not cleared when a battle ends, so it is read once the battle is over: 7 is a catch. 0 while one is live. */
    val battleOutcome: Int = 0,
    /** Poke Balls in the bag; -1 when the bag could not be read. */
    val ballCount: Int = -1,
    /** The bag's Items and Berries pockets while a battle is on and once after it; null otherwise. */
    val bag: Map<Int, BagItem>? = null,
    /** gBattleResults' turn counter during a battle, 0 at its start; -1 outside one. */
    val turn: Int = -1,
    /**
     * The game's battle style: true is Set. Read on all five Gen 3 games and the Nat. Dex builds (SaveBlock2 + 0x14,
     * bit 9); null only when SaveBlock2 could not be read (rc32 audit P3 #113).
     */
    val battleStyleSet: Boolean? = null,
    /** The wild Pokemon on the field is shiny, judged from its personality value and the player's trainer id. */
    val enemyShiny: Boolean = false,
    val opponent: OpponentInfo? = null,
    val caps: LevelCapTable? = null,
    /** Keys of the caps table's bosses that are beaten: gym leaders from the badges, the League from trainer flags. */
    val beaten: Set<String> = emptySet(),
    /** The battle on screen is a catching lesson (Wally's Ralts, the Old Man's Weedle, the Teachy TV): never an encounter. */
    val lesson: Boolean = false,
    /**
     * A Battle Tent, Battle Frontier or Battle Tower battle is on, or has just ended with a Pokemon still down: the party
     * is the one the game lends for it, not the run's (GbaTracker.isFacilityBattle, rc32 audit P2 #141).
     */
    val facility: Boolean = false,
    /** The game's key in nuzlocke/statics-gen3.tsv: rs for Ruby and Sapphire, e for Emerald, frlg for FireRed and LeafGreen. */
    val staticsGame: String = "",
    /**
     * gMapHeader's regionMapSectionId and mapType for the map the tracker's map id names, read with that id: the map
     * section is the game's own "met at" place, and type 8 is a building. -1 when not read, or while the map is changing
     * (rc32 audit P2 #140).
     */
    val mapSection: Int = -1,
    val mapType: Int = -1,
    /** Set by the Game Boy trackers instead of the Gen 3 fields above; Gen12Nuzlocke reads it. Null on a Gen 3 game. */
    val gb: GbNuzReads? = null,
    /** The game's trainer teams for boss scouting (NuzlockeScout), one object per tracker; null where it has none. */
    val scout: com.ironmonone.tracker.nuzlocke.ScoutSource? = null,
)

/**
 * What the Nuzlocke rules engine needs from a Game Boy game (2026-09-30), as the games hold it: raw values, which
 * Gen12Nuzlocke turns into the engine's snapshot (so the mapping is plain code a test can drive without a ROM).
 * Nothing is here that the panel shows.
 */
data class GbNuzReads(
    /** 1 for Red, Blue and Yellow, 2 for Gold, Silver and Crystal. */
    val generation: Int,
    /** The data files' game key for this game: `rb`, `y`, `g`, `s` (Gold and Silver, one at a time) or `c`. */
    val game: String,
    /** The keys to look a row up under, the game's own first: `g` then `gs`. */
    val gameKeys: List<String>,
    /** The place the player stands in (the area's name, every floor of a cave alike) and the specific map, or null when the map is not one the table names. */
    val place: String? = null,
    val detail: String? = null,
    /** wPlayerID: the trainer id every Pokemon the player catches is stamped with. -1 when it could not be read. */
    val playerId: Int = -1,
    /** The wild enemy's DVs as the game holds them (attack and defense in the high byte, speed and special in the low); -1 outside a battle. */
    val enemyDvs: Int = -1,
    /** The enemy's HP the last time it was seen in a battle, and whether that battle was a wild one: what a battle that has just ended was against. */
    val enemyHpLast: Int = -1,
    val lastWild: Boolean = true,
    /** wBattleResult as the game left it. Gen 1: 0 won or the wild Pokemon left, 1 lost, 2 the player ran or caught. Gen 2: the low two bits are 0 won, 1 lost, 2 a draw (someone ran), bit 7 the box filled up. -1 when unread. */
    val battleResult: Int = -1,
    /** Gen 1: wEscapedFromBattle was set at some look during the battle (Teleport, Roar, Whirlwind or a Poke Doll). */
    val escaped: Boolean = false,
    /** Generation 1: the escape was the wild Pokemon's own Teleport, Roar or Whirlwind (Gen12Nuzlocke.enemyLeft). */
    val enemyFled: Boolean = false,
    /** Gen 1: wCapturedMonSpecies, Gen 2: wWildMon, was set at some look during the battle: a ball caught the Pokemon. */
    val captured: Boolean = false,
    /** wBattleType. Gen 1: 1 the Old Man's lesson, 2 Safari. Gen 2: see Gen12Nuzlocke.method. */
    val battleType: Int = 0,
    /** Gen 1: a Pokemon Tower ghost (wild, on a Tower floor, no Silph Scope). */
    val ghost: Boolean = false,
    /** The player is on the water (wWalkBikeSurfState, wPlayerState). */
    val surfing: Boolean = false,
    /** Poke Balls in the bag (Gen 1) or in the ball pocket (Gen 2); -1 when unread. */
    val ballCount: Int = -1,
    /** The items pocket while the game is read; the balls are not in it. */
    val bag: Map<Int, BagItem>? = null,
    /** The enemy's turn counter, 0 at the start of a battle; -1 outside one. */
    val turn: Int = -1,
    /** wOptions' battle style: true is Set. */
    val battleStyleSet: Boolean? = null,
    val opponent: OpponentInfo? = null,
    val caps: LevelCapTable? = null,
    /** The party's nicknames, in the order of [TrackerState.party]. */
    val nicknames: List<String> = emptyList(),
    /**
     * The game's own count of Pokemon in the party (wPartyCount, eggs left out as [TrackerState.party] leaves them), -1 when
     * the byte is not a count (0, or a save not loaded yet). The party list stops at a slot that does not decode, and the
     * engine takes a party as long as this as a whole read (rc32 audit P3 #111).
     */
    val partyCount: Int = -1,
    /**
     * Generation 2: the eggs in the party, which [TrackerState.party] leaves out, read from their party structs, and how
     * many slots of the species list say EGG. The engine follows an egg from the place it joined the party (rc32 audit
     * P2 #140): Crystal's eggs used to be first seen as they hatched, and with 'Gifts count' on they used up the route.
     */
    val eggs: List<GbEgg> = emptyList(),
    val eggSlots: Int = 0,
)

/** An egg in a Generation 2 party: its party slot, its party struct's trainer id, DVs, species and level. */
data class GbEgg(val slot: Int, val otId: Int, val dvs: Int, val species: Int, val level: Int)
