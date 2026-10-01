package com.ironmonone.tracker.nuzlocke

/**
 * One boss of the level cap table. [cap] is the highest level on the boss's team; [fromRom] says it was read
 * out of the loaded game's trainer data rather than taken from the standard table.
 */
data class BossCap(
    val seq: Int,
    /** "gym3", "e4-2", "champion", "steven". */
    val key: String,
    /** gym, e4, champion or post; the DS games add rival and boss, extra cap points that only count when the rules ask for them. */
    val kind: String,
    val label: String,
    val cap: Int,
    val ace: String,
    /** DS: the game's trainer ids. Game Boy: class and party number packed by [LevelCapTable.pack]. */
    val trainerIds: List<Int>,
    val fromRom: Boolean = false,
    /** The bit of the badge word this fight earns, or null for a fight that earns none. It is not always seq - 1: Platinum's Fantina is the third gym and the fifth badge. */
    val badge: Int? = null,
    /**
     * Fights that may come in any order share a phrase here ("Kanto gyms", "Elite Four, any order"). It is also the
     * words the screen uses for the place of a `post` fight ("N's Castle"). Empty for a fixed order.
     */
    val group: String = "",
) {
    /**
     * "Gym 3", "Elite Four 2", "Champion", "Kanto gyms": where the fight falls. A gym is numbered by its key, not by [seq],
     * which on the DS games counts the rival and team fights between the gyms too.
     */
    val place: String get() = when (kind) {
        "gym" -> "Gym ${key.removePrefix("gym").toIntOrNull() ?: seq}"
        "e4" -> if (group.isNotBlank()) "Elite Four" else "Elite Four " + key.substringAfter("e4-")
        "champion" -> "Champion"
        "rival" -> "Rival"
        "boss" -> "Team boss"
        else -> group.ifBlank { "After the Hall of Fame" }
    }

    /** On the ladder the player climbs (gym, League, Champion, post-game), as opposed to an extra cap point. */
    val onLadder: Boolean get() = kind != "rival" && kind != "boss"
}

/**
 * The bosses of one game in the order they are met, with their caps (2026-09-29).
 *
 * The table itself is data (resources nuzlocke/levelcaps-<system>.tsv, built from the research tables); the
 * tracker overrides each cap with the boss's real party level when it can read the ROM ([withRomLevels]),
 * which is what a randomized or level-scaled game needs.
 *
 * A data class on purpose: the tracker reads the table again on every poll while the game's trainer data will
 * not read, and the panel is only redrawn for a state that differs from the last. Two tables with the same
 * numbers must be equal, or every one of those polls redraws the tracker for nothing.
 */
data class LevelCapTable(val game: String, val bosses: List<BossCap>) {

    /** True when every cap came out of the loaded game. */
    val fromRom: Boolean get() = bosses.isNotEmpty() && bosses.all { it.fromRom }

    /** True when at least one cap came out of the game and at least one is the table's. */
    val mixed: Boolean get() = bosses.any { it.fromRom } && !fromRom

    fun byKey(key: String?): BossCap? = bosses.firstOrNull { it.key == key }

    /** The boss a trainer id belongs to, or null for anyone else. */
    fun keyOfTrainer(trainerId: Int): String? = bosses.firstOrNull { trainerId in it.trainerIds }?.key

    /**
     * The cap the Elite Four is entered with: the last member's highest level. Bulbapedia's clause is that
     * on entering the League the cap is that level, and once inside it may be passed.
     */
    val leagueCap: Int? get() = bosses.filter { it.kind == "e4" }.maxOfOrNull { it.cap }

    /** The next boss on the ladder that has not been beaten, or null once all are. */
    fun next(beaten: Set<String>): BossCap? = bosses.firstOrNull { it.onLadder && it.key !in beaten }

    /** The fights that earn a badge whose bit is set in [badges]: the gym leaders the game says are beaten. */
    fun beatenByBadges(badges: Int): Set<String> =
        bosses.mapNotNull { b -> b.badge?.let { bit -> if (((badges shr bit) and 1) == 1) b.key else null } }.toSet()

    /**
     * The fights that may be next: the [next] boss alone, or every boss of its any-order group that is still standing
     * (the Kanto gyms after the Johto League, an Elite Four that can be fought in any order).
     */
    fun nextSet(beaten: Set<String>): List<BossCap> {
        val first = next(beaten) ?: return emptyList()
        if (first.group.isBlank()) return listOf(first)
        return bosses.filter { it.onLadder && it.key !in beaten && it.kind == first.kind && it.group == first.group }
    }

    /**
     * The cap that applies when the fight with [key] starts, or null when none does. A gym leader is checked
     * against their own team; the first Elite Four fight against the League cap; the rest of the League and
     * the Champion are fought inside it, where the cap may be passed.
     */
    fun capAtStart(key: String?): Int? {
        val boss = byKey(key) ?: return null
        return when (boss.kind) {
            "gym", "post" -> boss.cap
            "e4" -> if (boss.key == "e4-1" || boss.group.isNotBlank()) leagueCap else null
            else -> null
        }
    }

    /** [cap] for [key] read from the game, [key]'s other fields kept. Keys not listed keep the table's number. */
    fun withRomLevels(levels: Map<String, Int>): LevelCapTable =
        LevelCapTable(game, bosses.map { b -> levels[b.key]?.let { b.copy(cap = it, fromRom = true) } ?: b })

    companion object {
        /** The table's game key for a tracker map's route version, or null when the game has none. */
        fun gameKey(routeVersion: String): String? = when (routeVersion) {
            "ruby", "sapphire" -> "rs"
            "emerald" -> "e"
            "firered", "leafgreen" -> "frlg"
            else -> null
        }

        /**
         * A Game Boy trainer: its class number and its number within the class, as one id. The data files write them
         * as "34:1" (Brock is class 34, party 1); the game holds them in two bytes and the tracker reads both.
         */
        fun pack(trainerClass: Int, no: Int): Int = (trainerClass shl 8) or (no and 0xFF)

        /** Every row of the standard table, as [game]'s bosses in order. Empty for an unknown game. */
        fun standard(game: String, system: NuzlockeSystem = NuzlockeSystem.GEN3): LevelCapTable =
            LevelCapTable(game, rowsOf(system).filter { it.first == game }.map { it.second })

        // Read once per system: the tracker asks for a table on every poll until the game's own levels have been read.
        private val cache = HashMap<NuzlockeSystem, List<Pair<String, BossCap>>>()

        private fun rowsOf(system: NuzlockeSystem): List<Pair<String, BossCap>> =
            synchronized(cache) { cache.getOrPut(system) { parse(readResource(system)) } }

        internal fun readResource(system: NuzlockeSystem = NuzlockeSystem.GEN3): String =
            LevelCapTable::class.java.getResourceAsStream(system.capsResource)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        /** One id of the ids column: a plain number (Gen 3 and the DS games) or "class:no" (Game Boy). */
        private fun id(token: String): Int? {
            val t = token.trim()
            val colon = t.indexOf(':')
            if (colon < 0) return t.toIntOrNull()
            val c = t.substring(0, colon).trim().toIntOrNull() ?: return null
            val n = t.substring(colon + 1).trim().toIntOrNull() ?: return null
            return pack(c, n)
        }

        /** Rows as (game, boss); a line that does not parse is skipped. */
        internal fun parse(text: String): List<Pair<String, BossCap>> = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 8) return@mapNotNull null
                val seq = p[1].trim().toIntOrNull() ?: return@mapNotNull null
                val cap = p[5].trim().toIntOrNull()?.takeIf { it in 1..100 } ?: return@mapNotNull null
                val ids = p[7].split(',').mapNotNull { id(it) }
                val badge = p.getOrNull(8)?.trim()?.toIntOrNull()?.takeIf { it in 0..31 }
                val group = p.getOrNull(9)?.trim().orEmpty()
                p[0].trim() to BossCap(seq, p[2].trim(), p[3].trim(), p[4].trim(), cap, p[6].trim(), ids, badge = badge, group = group)
            }.toList()
    }
}
