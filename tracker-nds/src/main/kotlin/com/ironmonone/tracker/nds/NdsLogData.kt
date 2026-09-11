package com.ironmonone.tracker.nds

/**
 * NDS-Ironmon-Tracker's log-viewer tables, extracted by running its Lua
 * (tools/trainer-data/convert_nds_log_tables.py) into resources/nds:
 * GameInfo.GAME_INFO (the game's name, badge art, gym TMs and pivot types),
 * TrainerData IMPORTANT_GROUPS (the Trainers tab's rivals, gyms and Elite 4),
 * LocationData encounterAreaOrder (the Pivots tab's areas) and PokemonData's
 * evolution methods (the Pokemon page's "Evos:" label).
 */
object NdsLogData {
    /** TrainerData.TRAINER_TYPES. */
    const val STANDARD = 0
    const val RIVAL = 1
    const val GYM_LEADERS = 2

    class Game(
        val code: Long,
        val version: String,
        /** GameInfo NAME, which the log's "Randomization of Pokemon X" must match. */
        val name: String,
        val gen: Int,
        /** GameInfo VERSION_GROUP: 1 Diamond and Pearl, 2 Platinum, 3 HeartGold and SoulSilver, 4 Black and White, 5 Black 2 and White 2. */
        val versionGroup: Int,
        val badgePrefix: String,
        /** GYM_TMS in badge order; -1 is HeartGold and SoulSilver's spacer between Johto and Kanto. */
        val gymTms: List<Int>,
        val pivotTypes: List<String>,
        val trainerTable: Long,
        val locationTable: Long,
    )

    /** One battle of trainer-groups.tsv. [alt] is 1-3 when the battle is picked by the starter, else 0. */
    class GroupRow(
        val group: Int,
        val groupName: String,
        val type: Int,
        val battle: Int,
        val alt: Int,
        /** The trainer's name, or a rival battle's location. */
        val label: String,
        val ids: List<Int>,
        val badge: Int?,
        val iv: Int,
    )

    private fun table(name: String): List<List<String>> =
        NdsLogData::class.java.getResourceAsStream("/nds/$name")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()
        } ?: emptyList()

    private fun code(s: String): Long = s.trim().toLongOrNull(16) ?: 0L

    val games: List<Game> by lazy {
        table("game-info.tsv").filter { it.size >= 10 }.map { c ->
            Game(
                code(c[0]), c[1], c[2], c[3].toIntOrNull() ?: 0, c[4].toIntOrNull() ?: 0, c[5],
                c[6].split(',').mapNotNull { it.trim().toIntOrNull() },
                c[7].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                code(c[8]), code(c[9]),
            )
        }
    }

    fun game(code: Long): Game? = games.firstOrNull { it.code == code }

    fun gameNamed(name: String): Game? = games.firstOrNull { it.name == name }

    /** The game a DS map reads, preferring the one the log names (Diamond or Pearl, HeartGold or SoulSilver). */
    fun gameFor(map: NdsGameMap, logGameName: String): Game? {
        val gs = map.gameCodes.mapNotNull { game(it) }
        return gs.firstOrNull { it.name == logGameName } ?: gs.firstOrNull()
    }

    private val groupTable by lazy { table("trainer-groups.tsv").filter { it.size >= 9 } }

    fun groupRows(trainerTable: Long): List<GroupRow> = groupTable.filter { code(it[0]) == trainerTable }.map { c ->
        GroupRow(
            c[1].toInt(), c[2], c[3].toIntOrNull() ?: 0, c[4].toInt(), c[5].toIntOrNull() ?: 0, c[6],
            c[7].split(',').mapNotNull { it.trim().toIntOrNull() }, c[8].trim().toIntOrNull(),
            c.getOrNull(9)?.trim()?.toIntOrNull() ?: 0,
        )
    }

    private val areaTable by lazy { table("pivot-areas.tsv").filter { it.size >= 3 } }

    fun pivotAreas(locationTable: Long): List<String> =
        areaTable.filter { code(it[0]) == locationTable }.sortedBy { it[1].toIntOrNull() ?: 0 }.map { it[2] }

    private val evoTable: Map<Int, String> by lazy {
        table("evo-methods.tsv").filter { it.size >= 2 }.associate { (it[0].toIntOrNull() ?: 0) to (it.getOrNull(2) ?: "").trim() }
    }

    /** PokemonData's evolution for a national number: a level ("16"), a type code ("THUNDER"), or "" for none. */
    fun evoMethod(nationalId: Int): String = evoTable[nationalId] ?: ""

    /** PokemonData.EVO_LONGER_NAMES: a type code's long names, one per evolution. */
    val evoNames: Map<String, List<String>> by lazy {
        table("evo-names.tsv").filter { it.size >= 2 }.associate { it[0] to it[1].split(" | ") }
    }
}
