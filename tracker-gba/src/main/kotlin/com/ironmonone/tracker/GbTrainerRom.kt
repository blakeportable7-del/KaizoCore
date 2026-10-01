package com.ironmonone.tracker

/**
 * The trainers' teams in a Game Boy ROM, read the way the games do (2026-09-30), for the level caps: a randomized or
 * level-scaled game has its own boss levels, which the standard table cannot know.
 *
 * Both games keep one pointer per trainer class in a table in bank 0x0E, and the teams of a class one after the
 * other where the pointer says. Gen 1 (pokered data/trainers/parties.asm, read by ReadTrainer): a team is either a
 * level and then species, or 0xFF and then level, species pairs, and ends with a 0 byte. Gen 2 (pokecrystal
 * data/trainers/parties.asm, TrainerGroups): a team is its name, a type byte and then the Pokemon, each a level and a
 * species with an item and four moves when the type says so, and ends with 0xFF.
 *
 * The table's place is the randomizer's `TrainerDataTableOffset` (gen1_offsets.ini, gen2_offsets.ini), which the
 * randomizer edits in place, so it stays right on a randomized ROM. The functions never throw: anything that does not
 * fit the format gives null.
 */
object GbTrainerRom {

    private fun u8(rom: ByteArray, at: Int): Int = if (at in rom.indices) rom[at].toInt() and 0xFF else -1

    /** The file offset a bank-local pointer means, in the bank the table itself is in. */
    private fun bankOffset(table: Int, pointer: Int): Int = (table / 0x4000) * 0x4000 + (pointer - 0x4000)

    /** Where a class's teams start, or -1. */
    private fun classStart(rom: ByteArray, table: Int, trainerClass: Int): Int {
        if (trainerClass < 1) return -1
        val lo = u8(rom, table + (trainerClass - 1) * 2)
        val hi = u8(rom, table + (trainerClass - 1) * 2 + 1)
        if (lo < 0 || hi < 0) return -1
        val p = lo or (hi shl 8)
        if (p !in 0x4000..0x7FFF) return -1
        val at = bankOffset(table, p)
        return if (at in rom.indices) at else -1
    }

    /** Generation 1: the highest level on team [no] (1 is the first) of [trainerClass], or null. */
    fun gen1MaxLevel(rom: ByteArray, table: Int, trainerClass: Int, no: Int): Int? {
        if (no < 1) return null
        var at = classStart(rom, table, trainerClass)
        if (at < 0) return null
        // Skip the teams before this one: each ends with a 0 byte, and none has a 0 byte inside it.
        repeat(no - 1) {
            while (true) {
                val b = u8(rom, at++)
                if (b < 0) return null
                if (b == 0) break
            }
        }
        val first = u8(rom, at++)
        if (first < 0) return null
        var max = 0
        if (first == 0xFF) {
            while (true) {
                val level = u8(rom, at)
                if (level < 0) return null
                if (level == 0) break
                max = maxOf(max, level)
                at += 2
            }
        } else {
            // One level for the whole team, and the species after it: an empty team is not a team.
            if (u8(rom, at) <= 0) return null
            max = first
        }
        return max.takeIf { it in 1..100 }
    }

    /** Generation 2: the highest level on team [no] (1 is the first) of [trainerClass], or null. */
    fun gen2MaxLevel(rom: ByteArray, table: Int, trainerClass: Int, no: Int): Int? {
        if (no < 1) return null
        var at = classStart(rom, table, trainerClass)
        if (at < 0) return null
        var party = 1
        while (party <= no) {
            // The team's name, ended by 0x50, then its type.
            while (true) {
                val b = u8(rom, at++)
                if (b < 0) return null
                if (b == 0x50) break
            }
            val type = u8(rom, at++)
            // 0 plain, 1 moves, 2 item, 3 item and moves: a level and a species, then what the type adds.
            val size = when (type) { 0 -> 2; 1 -> 6; 2 -> 3; 3 -> 7; else -> return null }
            var max = 0
            while (true) {
                val level = u8(rom, at)
                if (level < 0) return null
                if (level == 0xFF) { at++; break }
                max = maxOf(max, level)
                at += size
            }
            if (party == no) return max.takeIf { it in 1..100 }
            party++
        }
        return null
    }
}
