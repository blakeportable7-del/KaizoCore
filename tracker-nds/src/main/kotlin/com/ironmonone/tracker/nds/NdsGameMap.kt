package com.ironmonone.tracker.nds

/**
 * Where a DS game keeps what the tracker reads: every offset NdsTracker uses,
 * per game, as data.
 *
 * NdsTracker was written for Platinum with its offsets as private vals. Each
 * new DS game would have been a copy of the file. The reference tracker
 * (Brian0255/NDS-Ironmon-Tracker, GPL-3.0) keys the same numbers by game code
 * in constants/MemoryAddresses.lua and constants/GameInfo.lua, and every
 * number below is copied from there - file and key named per field, so a
 * disagreement can be settled by looking, not by re-deriving.
 *
 * Gen 4 offsets are relative to the resolved VERSION pointer (`u32[u32[0xBA8]
 * & 0xFFFFFF] + 0x20`), except [battleStatus], which is absolute in main RAM
 * (the reference's GLOBAL block). Gen 5 games have no pointer chain at all:
 * every address is in the GLOBAL block, relative to main RAM, and [absolute]
 * says so.
 */
data class NdsGameMap(
    val name: String,
    /** The DS header game codes (u32 little-endian at header + 0x0C) this map serves. */
    val gameCodes: Set<Long>,
    val generation: Int,
    val badgePrefix: String,
    /** True when every offset is relative to main RAM with no pointer chain (Gen 5). */
    val absolute: Boolean,
    /** Resource folder for names and move data: gen4 or gen5. */
    val dataDir: String,
    /** Move-level table; Gen 5 has one per version group. */
    val moveLevelsResource: String,
    val playerBase: Long,
    val enemyBase: Long,
    val enemyTrainerId: Long,
    val playerBattleMonPid: Long,
    val enemyBattleMonPid: Long,
    val statStagesPlayer: Long,
    val statStagesEnemy: Long,
    val battleSubscriptMsgs: Long,
    val itemStartNoBattle: Long,
    /** TrainerData.TRAINERS[game].LAB_IDS and FINAL_FIGHT_ID: beating a lab rival is Past Lab, beating the champion is Won. */
    val labTrainerIds: Set<Int> = emptySet(),
    val finalTrainerId: Int = 0,
    val itemStartBattle: Long,
    val berryBagStart: Long,
    val berryBagStartBattle: Long,
    /**
     * Badge bytes, least-significant region first. Platinum has one byte;
     * HGSS has Johto then Kanto, and the two are combined into one bitfield
     * (Johto in bits 0-7, Kanto in 8-15) so the panel's badge row keeps one
     * shape.
     */
    val badgeOffsets: List<Long>,
    /** Absolute address of the battle-status word (GLOBAL.battleStatus). */
    val battleStatus: Long,
    /**
     * Main-RAM offset of the global pointer that starts the Gen 4 chain
     * (MemoryAddresses[game].GLOBAL_POINTER). Platinum and HGSS use 0xBA8;
     * Diamond and Pearl use 0xB70. Unused on an absolute (Gen 5) map.
     */
    val globalPointer: Long = GLOBAL_POINTER,
    // ---- Gen 5 only (BattleHandlerGen5): 0 on Gen 4 maps -------------------
    /** Table of 0x1C-byte battler records; each starts with a pointer to its battle data. */
    val mainBattleDataPtr: Long = 0,
    val doubleTripleFlag: Long = 0,
    val abilityTriggerStart: Long = 0,
    val totalMonsParty: Long = 0,
    /** First battle-side PID; must equal the party lead's for a fetch to be trusted. */
    val playerBattleBase: Long = 0,
) {
    /** Encrypted party entry: GameInfo.ENCRYPTED_POKEMON_SIZE (236 Gen 4, 220 Gen 5). */
    val entrySize: Int get() = if (generation == 5) 220 else 236

    /**
     * The same map with every absolute address moved by [delta]. The
     * reference's MemoryAddresses.lua writes WHITE as every BLACK address
     * `+ 0x20` and WHITE2 as every BLACK2 address `+ 0x80`, badge bytes
     * and battle status included; zero (unset) fields stay zero. Only
     * meaningful on an absolute (Gen 5) map.
     */
    fun shifted(delta: Long, name: String, codes: Set<Long>): NdsGameMap {
        fun mv(v: Long) = if (v == 0L) 0L else v + delta
        return copy(
            name = name, gameCodes = codes,
            playerBase = mv(playerBase), enemyBase = mv(enemyBase), enemyTrainerId = mv(enemyTrainerId),
            playerBattleMonPid = mv(playerBattleMonPid), enemyBattleMonPid = mv(enemyBattleMonPid),
            statStagesPlayer = mv(statStagesPlayer), statStagesEnemy = mv(statStagesEnemy),
            battleSubscriptMsgs = mv(battleSubscriptMsgs),
            itemStartNoBattle = mv(itemStartNoBattle), itemStartBattle = mv(itemStartBattle),
            berryBagStart = mv(berryBagStart), berryBagStartBattle = mv(berryBagStartBattle),
            badgeOffsets = badgeOffsets.map { mv(it) }, battleStatus = mv(battleStatus),
            mainBattleDataPtr = mv(mainBattleDataPtr), doubleTripleFlag = mv(doubleTripleFlag),
            abilityTriggerStart = mv(abilityTriggerStart), totalMonsParty = mv(totalMonsParty),
            playerBattleBase = mv(playerBattleBase),
        )
    }
    val maxSpecies: Int get() = if (generation == 5) 649 else 493

    companion object {
        const val GLOBAL_POINTER = 0xBA8L
        const val VERSION_POINTER_OFFSET = 0x20L
        /** Cartridge header in main RAM (NDS_CONSTANTS.CARTRIDGE_HEADER); code at +0x0C. */
        const val CARTRIDGE_HEADER = 0x023FFE00L

        // GameInfo.VERSION_NUMBER
        const val CODE_DIAMOND = 0x45414441L       // "ADAE"
        const val CODE_PEARL = 0x45415041L         // "APAE"
        const val CODE_PLATINUM = 0x45555043L      // "CPUE"
        const val CODE_HEART_GOLD = 0x454B5049L    // "IPKE"
        const val CODE_SOUL_SILVER = 0x45475049L   // "IPGE"
        const val CODE_BLACK = 0x4F425249L         // "IRBO"
        const val CODE_WHITE = 0x4F415249L         // "IRAO"
        const val CODE_BLACK2 = 0x4F455249L        // "IREO"
        const val CODE_WHITE2 = 0x4F445249L        // "IRDO"

        /**
         * MemoryAddresses[DIAMOND] and [PEARL] - identical blocks down to the
         * global pointer (0xB70, where Platinum's is 0xBA8), so one map serves
         * both codes. GameInfo gives both BADGE_PREFIX DPPT, VERSION_GROUP 1,
         * the same GYM_TMS as Platinum, and Platinum's LOCATION_DATA.
         * Added 2026-09-07 from Blake's Rev 5 dumps.
         */
        val DP = NdsGameMap(
            name = "Pokemon Diamond / Pearl",
            labTrainerIds = setOf(247, 248, 249), finalTrainerId = 267,
            gameCodes = setOf(CODE_DIAMOND, CODE_PEARL),
            generation = 4, badgePrefix = "DPPT", absolute = false,
            dataDir = "gen4", moveLevelsResource = "/gen4/movelevels.tsv",
            playerBase = 0x2AC, enemyBase = 0x4CD88, enemyTrainerId = 0x42A8E,
            playerBattleMonPid = 0x485E8, enemyBattleMonPid = 0x486A8,
            statStagesPlayer = 0x48598, statStagesEnemy = 0x48658,
            battleSubscriptMsgs = 0x458F0,
            itemStartNoBattle = 0xD54, itemStartBattle = 0x4546C,
            berryBagStart = 0xDF4, berryBagStartBattle = 0x4550C,
            badgeOffsets = listOf(0x292),
            battleStatus = 0x23BB38,
            globalPointer = 0xB70,
        )

        /** MemoryAddresses[PLATINUM]; GameInfo[PLATINUM].BADGE_PREFIX. */
        val PLATINUM = NdsGameMap(
            name = "Pokemon Platinum",
            labTrainerIds = setOf(850, 851, 852), finalTrainerId = 267,
            gameCodes = setOf(CODE_PLATINUM),
            generation = 4, badgePrefix = "DPPT", absolute = false,
            dataDir = "gen4", moveLevelsResource = "/gen4/movelevels.tsv",
            playerBase = 0xB4, enemyBase = 0x4BE5C, enemyTrainerId = 0x4189E,
            playerBattleMonPid = 0x47620, enemyBattleMonPid = 0x476E0,
            statStagesPlayer = 0x475D0, statStagesEnemy = 0x47690,
            battleSubscriptMsgs = 0x44928,
            itemStartNoBattle = 0xB60, itemStartBattle = 0x442BC,
            berryBagStart = 0xC00, berryBagStartBattle = 0x4435C,
            badgeOffsets = listOf(0x96),
            battleStatus = 0x24A55A,
        )

        /**
         * MemoryAddresses[HEART_GOLD] and [SOUL_SILVER] - identical blocks, so
         * one map serves both codes. GameInfo[HEART_GOLD].BADGE_PREFIX = HGSS.
         */
        val HGSS = NdsGameMap(
            name = "Pokemon HeartGold / SoulSilver",
            labTrainerIds = setOf(495, 496, 497), finalTrainerId = 260,
            gameCodes = setOf(CODE_HEART_GOLD, CODE_SOUL_SILVER),
            generation = 4, badgePrefix = "HGSS", absolute = false,
            dataDir = "gen4", moveLevelsResource = "/gen4/movelevels.tsv",
            playerBase = 0xA8, enemyBase = 0x4F068, enemyTrainerId = 0x440AA,
            playerBattleMonPid = 0x49E7C, enemyBattleMonPid = 0x49F3C,
            statStagesPlayer = 0x49E2C, statStagesEnemy = 0x49EEC,
            battleSubscriptMsgs = 0x47184,
            itemStartNoBattle = 0xB74, itemStartBattle = 0x46AD8,
            berryBagStart = 0xC14, berryBagStartBattle = 0x46B78,
            badgeOffsets = listOf(0x8E, 0x93),     // johtoBadges, kantoBadges
            battleStatus = 0x246F48,
        )

        /**
         * MemoryAddresses[BLACK].GLOBAL. White shares the VERSION_GROUP (move
         * levels, names) but NOT the addresses: the reference's WHITE block is
         * every one of these `+ 0x20`, so White is [WHITE], a shifted copy.
         * No battleSubscriptMsgs on Gen 5: ability reveals come from
         * abilityTriggerStart (player slot +0, opponent slot +4 in singles).
         * No badge art bundled for BW yet, so the row falls back to numbers.
         */
        val BW = NdsGameMap(
            name = "Pokemon Black",
            labTrainerIds = setOf(64), finalTrainerId = 232,
            gameCodes = setOf(CODE_BLACK),
            generation = 5, badgePrefix = "BW", absolute = true,
            dataDir = "gen5", moveLevelsResource = "/gen5/movelevels-bw.tsv",
            playerBase = 0x2349B4, enemyBase = 0x26B254, enemyTrainerId = 0x2697BE,
            playerBattleMonPid = 0x2A7E14, enemyBattleMonPid = 0x2A7E70,
            statStagesPlayer = 0x26D7A0, statStagesEnemy = 0x26D9C4,
            battleSubscriptMsgs = 0,
            itemStartNoBattle = 0x234784, itemStartBattle = 0x234784,
            berryBagStart = 0x234844, berryBagStartBattle = 0x234844,
            badgeOffsets = listOf(0x23CDB0),
            battleStatus = 0x1D0798,
            mainBattleDataPtr = 0x269838, doubleTripleFlag = 0x2A62F8,
            abilityTriggerStart = 0x2A6354, totalMonsParty = 0x2349B0,
            playerBattleBase = 0x26A794,
        )

        /** MemoryAddresses[WHITE].GLOBAL: Black `+ 0x20` throughout. */
        val WHITE = BW.shifted(0x20, "Pokemon White", setOf(CODE_WHITE))

        /** MemoryAddresses[BLACK2].GLOBAL; VERSION_GROUP 5 with White 2, whose block is every address `+ 0x80` ([WHITE2]). No statStagesEnemy there. */
        val B2W2 = NdsGameMap(
            name = "Pokemon Black 2",
            labTrainerIds = setOf(161, 162, 163), finalTrainerId = 341,
            gameCodes = setOf(CODE_BLACK2),
            generation = 5, badgePrefix = "BW2", absolute = true,
            dataDir = "gen5", moveLevelsResource = "/gen5/movelevels-b2w2.tsv",
            playerBase = 0x21E42C, enemyBase = 0x258874, enemyTrainerId = 0x257332,
            playerBattleMonPid = 0x2968D4, enemyBattleMonPid = 0x296930,
            statStagesPlayer = 0x25B320, statStagesEnemy = 0,
            battleSubscriptMsgs = 0,
            itemStartNoBattle = 0x21E1FC, itemStartBattle = 0x21E1FC,
            berryBagStart = 0x21E2BC, berryBagStartBattle = 0x21E2BC,
            badgeOffsets = listOf(0x226728),
            battleStatus = 0x1B5138,
            mainBattleDataPtr = 0x2573AC, doubleTripleFlag = 0x294DA4,
            abilityTriggerStart = 0x294E08, totalMonsParty = 0x21E428,
            playerBattleBase = 0x258314,
        )

        /** MemoryAddresses[WHITE2].GLOBAL: Black 2 `+ 0x80` throughout. */
        val WHITE2 = B2W2.shifted(0x80, "Pokemon White 2", setOf(CODE_WHITE2))

        val ALL = listOf(DP, PLATINUM, HGSS, BW, WHITE, B2W2, WHITE2)

        fun forCode(code: Long): NdsGameMap? = ALL.firstOrNull { code in it.gameCodes }

        /**
         * The game the core is running, from the cartridge header in RAM.
         * Null for a game this tracker has no map for - which is a refusal,
         * not a Platinum default: a wrong map reads confident garbage.
         */
        fun detect(memory: NdsMemoryReader): NdsGameMap? {
            val b = memory.read(CARTRIDGE_HEADER + 0x0C, 4)
            if (b.size < 4) return null
            val code = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            return forCode(code)
        }
    }
}
