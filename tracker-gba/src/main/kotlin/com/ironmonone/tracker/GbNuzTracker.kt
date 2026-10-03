package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem

/**
 * The part of the Game Boy Nuzlocke reads that has to remember something between polls (2026-09-30), shared by the
 * Gen 1 and Gen 2 trackers: the last place the player stood in, how the battle that just ended was going, the level
 * cap table and who each opponent is. The reads themselves (which byte is where) are each tracker's own.
 */
internal class GbNuzTracker(
    private val generation: Int,
    /** The data files' game key: rb, y, g, s or c. */
    val game: String,
    /** The keys a data row is looked up under, the game's own first: g then gs. The last one is the level cap table's game. */
    val keys: List<String>,
    private val rom: ByteArray,
    /** The trainer table's place in the ROM (the randomizer's TrainerDataTableOffset); 0 when this game has none pinned. */
    private val trainerTable: Int,
) {
    private val system = if (generation == 1) NuzlockeSystem.GEN1 else NuzlockeSystem.GEN2

    // ---- where the player is

    private var lastPlace: GbData.Place? = null

    /** The place [mapId] is in; a map the table does not name (a gate, a glitch) keeps the last place that was named. */
    fun place(mapId: Int?): GbData.Place? {
        val found = mapId?.let { GbData.place(generation, keys, it) }
        if (found != null) lastPlace = found
        return found ?: lastPlace
    }

    // ---- how the battle is going, so the first look after it can say how it ended

    private var battling = false
    var lastEnemyHp = -1
        private set
    var lastWild = true
        private set
    var escaped = false
        private set
    var captured = false
        private set
    /** The escape was the wild Pokemon's own doing: decided by [look]'s enemyLeft when the escape flag first reads set. */
    var enemyFled = false
        private set

    /**
     * One look at the game. [escapedNow] and [capturedNow] are the game's flags at this moment; they are held for the
     * rest of the battle. [enemyLeft] is asked once, when the escape flag first reads set, whether the wild Pokemon left
     * by its own move (Gen12Nuzlocke.enemyLeft).
     */
    fun look(inBattle: Boolean, wild: Boolean, enemyHp: Int?, escapedNow: Boolean, capturedNow: Boolean, enemyLeft: () -> Boolean = { false }) {
        if (inBattle) {
            if (!battling) { escaped = false; captured = false; enemyFled = false }
            lastWild = wild
            if (enemyHp != null) lastEnemyHp = enemyHp
            if (escapedNow && !escaped) enemyFled = enemyLeft()
            escaped = escaped || escapedNow
            captured = captured || capturedNow
        }
        battling = inBattle
    }

    // ---- the level cap table

    private var caps: LevelCapTable? = null
    private var capTries = 0
    private val opponents = HashMap<Int, OpponentInfo>()

    private fun maxLevel(trainerClass: Int, no: Int): Int? =
        if (trainerTable == 0) null
        else if (generation == 1) GbTrainerRom.gen1MaxLevel(rom, trainerTable, trainerClass, no)
        else GbTrainerRom.gen2MaxLevel(rom, trainerTable, trainerClass, no)

    /**
     * The level cap table: the standard one first, then each boss's cap replaced by the highest level on their real team
     * read out of the loaded ROM, which is what a randomized or level-scaled game needs. When the ROM's trainer data will
     * not read the table stands, and its bosses say so. A read that fell short is tried again on later polls, a few
     * times, and then left.
     */
    fun caps(): LevelCapTable? {
        caps?.let { if (it.fromRom || capTries >= 20 || trainerTable == 0) return it }
        val standard = LevelCapTable.standard(keys.last(), system)
        if (standard.bosses.isEmpty()) return null
        if (trainerTable == 0) { caps = standard; return standard }
        capTries++
        val levels = HashMap<String, Int>()
        for (b in standard.bosses) {
            val level = b.trainerIds.mapNotNull { id -> maxLevel(id shr 8, id and 0xFF) }.maxOrNull()
            if (level != null) levels[b.key] = level
        }
        return (if (levels.isEmpty()) standard else standard.withRomLevels(levels)).also { caps = it }
    }

    /** Who is being fought, worked out once per class and party number. */
    fun opponent(trainerClass: Int, no: Int): OpponentInfo {
        val id = LevelCapTable.pack(trainerClass, no)
        return opponents.getOrPut(id) {
            val who = GbData.trainer(generation, keys, trainerClass, no)
            OpponentInfo(id, who?.first ?: "Trainer", who?.second ?: "Other", caps()?.keyOfTrainer(id), maxLevel(trainerClass, no))
        }
    }
}
