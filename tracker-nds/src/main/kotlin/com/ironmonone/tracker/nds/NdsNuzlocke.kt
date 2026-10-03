package com.ironmonone.tracker.nds

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeSpecies
import com.ironmonone.tracker.nuzlocke.NuzlockeStatics
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.NzArea
import com.ironmonone.tracker.nuzlocke.NzEnemy
import com.ironmonone.tracker.nuzlocke.NzItem
import com.ironmonone.tracker.nuzlocke.NzMon
import com.ironmonone.tracker.nuzlocke.NzOpponent
import com.ironmonone.tracker.nuzlocke.Snapshot

/**
 * The DS side of the Nuzlocke rules engine (2026-09-30): an [NdsTrackerState] in, the engine's [Snapshot] out. Pure: no
 * memory is read here; the DS tracker already reads everything this needs.
 *
 * What the DS tracker cannot read, and what stands in for it (each is on the panel's rules page for these games):
 * - How a battle ended. The game leaves no word, so a battle that ended with the last Pokemon of the other side at 0 HP
 *   (the DS tracker's own test for its final fight) is a win. Anything else is the engine's own fallback: a wild battle is
 *   worked out from the enemy's HP and the party growing (a new Pokemon in the party is a catch, and a catch into a full
 *   party's box shows as "unknown"), and a trainer battle that ended with a Pokemon of the player's standing is a win. The
 *   Champion is only ever won by the first test, never by the fallback, so a state loaded in the middle of the fight does
 *   not finish the run.
 * - The Poke Ball count, so the rules begin as soon as the party has a Pokemon.
 * - How a wild battle began (surfing, fishing): everything counts as walking, apart from the statics table.
 * - The battle style option, and the whole bag: only the healing and status items are compared for "no items in battle".
 */
object NdsNuzlocke {

    /** A DS game as the data files know it. */
    class Game(val system: NuzlockeSystem, val capsGame: String, val keys: List<String>, val trainerTable: Long)

    /** The data keys of the game the tracker names ("Pokemon Platinum"); null for a name this does not know. */
    fun game(gameName: String): Game? {
        val n = gameName.trim().lowercase()
        val g = NdsLogData.gameNamed(gameName) ?: NdsLogData.games.firstOrNull { it.name.equals(gameName.trim(), ignoreCase = true) }
        val table = g?.trainerTable ?: 0L
        return when (n) {
            "pokemon diamond" -> Game(NuzlockeSystem.GEN4, "dp", listOf("d", "dp"), table)
            "pokemon pearl" -> Game(NuzlockeSystem.GEN4, "dp", listOf("p", "dp"), table)
            "pokemon platinum" -> Game(NuzlockeSystem.GEN4, "pt", listOf("pt"), table)
            "pokemon heartgold" -> Game(NuzlockeSystem.GEN4, "hgss", listOf("hg", "hgss"), table)
            "pokemon soulsilver" -> Game(NuzlockeSystem.GEN4, "hgss", listOf("ss", "hgss"), table)
            "pokemon black" -> Game(NuzlockeSystem.GEN5, "bw", listOf("b", "bw"), table)
            "pokemon white" -> Game(NuzlockeSystem.GEN5, "bw", listOf("w", "bw"), table)
            "pokemon black 2" -> Game(NuzlockeSystem.GEN5, "b2w2", listOf("b2", "b2w2"), table)
            "pokemon white 2" -> Game(NuzlockeSystem.GEN5, "b2w2", listOf("w2", "b2w2"), table)
            else -> null
        }
    }

    private val capsCache = HashMap<Pair<NuzlockeSystem, String>, LevelCapTable>()

    private fun caps(g: Game): LevelCapTable = synchronized(capsCache) {
        capsCache.getOrPut(g.system to g.capsGame) { LevelCapTable.standard(g.capsGame, g.system) }
    }

    /** Item names for the bag check: the healing and status items the DS tracker keeps, by id. */
    private val itemNames: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        NdsHeals.HEALS.forEach { out[it.id] = it.name }
        NdsHeals.CURES.forEach { out.putIfAbsent(it.id, it.name) }
        out
    }

    /** The original trainer ids most of the party shares: the player's own, which a caught Pokemon is stamped with. */
    private fun playerIds(party: List<NdsTrackedMon>): Pair<Int, Int>? =
        party.groupBy { it.mon.otId to it.mon.otSid }.maxByOrNull { it.value.size }?.key?.takeIf { it != (0 to 0) }

    private fun shinyFor(pid: Long, ids: Pair<Int, Int>?): Boolean {
        if (ids == null) return false
        val v = ids.first xor ids.second xor (pid and 0xFFFFL).toInt() xor (pid ushr 16).toInt()
        return v < 8
    }

    /** Gen 4 and 5's `types` of a Pokemon: the run's own (the randomizer's sidecar) when there is one, else the species table's. */
    private fun types(system: NuzlockeSystem, t: NdsTrackedMon): List<Int> {
        val info = t.info
        val own = info?.let { listOfNotNull(Gen4Types.idOf(it.type1), Gen4Types.idOf(it.type2)).distinct() } ?: emptyList()
        return own.ifEmpty { NuzlockeSpecies.types(system, t.mon.species) }
    }

    /** The label of a trainer the game names in its important-trainer groups (rivals, gym leaders, the Elite Four), or null. */
    private fun label(g: Game, id: Int, boss: com.ironmonone.tracker.nuzlocke.BossCap?): Pair<String, String> {
        if (boss != null) {
            val label = when (boss.kind) {
                "gym" -> "Leader ${boss.label}"
                "e4" -> "Elite Four ${boss.label}"
                "champion" -> "Champion ${boss.label}"
                "rival" -> "Rival ${boss.label}"
                "boss" -> "Boss ${boss.label}"
                else -> boss.label
            }
            val group = when (boss.kind) { "gym" -> "Gym"; "e4", "champion" -> "Elite4"; "rival" -> "Rival"; "boss" -> "Boss"; else -> "Boss" }
            return label to group
        }
        val row = NdsLogData.groupRows(g.trainerTable).firstOrNull { id in it.ids }
        return when (row?.type) {
            NdsLogData.RIVAL -> "Rival ${row.groupName}" to "Rival"
            NdsLogData.GYM_LEADERS -> "Leader ${row.label}" to "Gym"
            else -> if (row != null) "${row.groupName} ${row.label}" to "Elite4" else "a trainer" to "Other"
        }
    }

    /**
     * How the battle that has just ended went, from what the tracker last saw of it. The tracker itself calls its final fight
     * won when that fight is over and its opponent was down at the last look (a state loaded in the middle of the fight ends it
     * too, and that must not count), and [NdsTrackerState.runOver] is where it says so. The same test on the other side of any
     * battle is how a fight is known to be won without the game's word: the last Pokemon there was at 0 HP. Heart Gold and Soul
     * Silver need it for Lance, whose fight is not the one the tracker calls final (that is Red's).
     */
    internal fun battleEnd(s: NdsTrackerState): BattleEnd {
        if (s.inBattle) return BattleEnd.UNKNOWN
        if (s.runOver == NdsRunOver.WON) return BattleEnd.WON
        val last = s.lastBattleEnemy?.mon ?: return BattleEnd.UNKNOWN
        return if (last.maxHp > 0 && last.curHp == 0) BattleEnd.WON else BattleEnd.UNKNOWN
    }

    /** The engine's view of this state, or null when it has none to give: the party is not located yet, or the game is unknown. */
    fun snapshot(s: NdsTrackerState): Snapshot? {
        if (!s.located) return null
        val g = game(s.gameName) ?: return null
        val system = g.system
        val ids = playerIds(s.party)
        val party = s.party.map { p ->
            val m = p.mon
            NzMon(
                id = m.pid, species = m.species, speciesName = p.speciesName,
                // Gen 5 reads the nickname itself; Gen 4's text is not decoded, so a named Pokemon there carries a stand-in.
                nickname = m.nickname.ifEmpty { if (m.nicknamed) "${p.speciesName} (named)" else "" },
                level = m.level, hp = m.curHp, maxHp = m.maxHp, isEgg = m.isEgg,
                gender = NuzlockeSpecies.gender(system, m.species, m.pid),
                types = types(system, p), shiny = m.shiny,
            )
        }
        // A battle the tracker has not fetched is no battle to the rules: the reference reads the opponent only once
        // _tryToFetchBattleData passes. A catching demonstration (Route 202, Route 29) never does, and took the route's
        // first encounter; a Gen 5 read taken before the fetch reported the last trainer's battle (rc33 audit P1 #80, #82).
        val inBattle = s.inBattle && s.battleFetched
        val wild = inBattle && s.isWildBattle && s.enemyTrainerId == 0
        val enemy = s.enemy?.takeIf { inBattle }?.let { e ->
            val m = e.mon
            NzEnemy(
                id = m.pid, species = m.species, speciesName = e.speciesName, level = m.level, hp = m.curHp, maxHp = m.maxHp,
                gender = NuzlockeSpecies.gender(system, m.species, m.pid),
                types = types(system, e),
                // A wild Pokemon's own trainer fields are not the player's: judge it against the party's.
                shiny = if (wild) shinyFor(m.pid, ids) || m.shiny else m.shiny,
            )
        }
        val caps = caps(g)
        val bossKey = if (inBattle && !wild) caps.keyOfTrainer(s.enemyTrainerId) else null
        val opponent = if (inBattle && !wild && s.enemyTrainerId != 0) {
            val (label, group) = label(g, s.enemyTrainerId, caps.byKey(bossKey))
            NzOpponent(s.enemyTrainerId, label, group, bossKey, null)
        } else null
        val method = if (wild && enemy != null && NuzlockeStatics.isStatic(system, g.keys, s.areaName, enemy.level, enemy.species)) Method.STATIC else Method.WALK
        val bag = LinkedHashMap<Int, NzItem>()
        (s.healingItems + s.statusItems).forEach { (id, qty) -> bag[id] = NzItem(itemNames[id] ?: "Item $id", qty) }
        return Snapshot(
            readable = true,
            area = NzArea(s.areaName.takeIf { it.isNotBlank() }, s.mapId.takeIf { it != 0 }),
            inBattle = inBattle,
            wild = wild,
            ghost = false,
            method = method,
            enemy = enemy,
            opponent = opponent,
            end = battleEnd(s),
            turn = null,
            party = party,
            partyCount = s.partyCount,
            badges = s.badges,
            ballCount = null,
            bag = bag,
            battleStyleSet = null,
            caps = caps,
            beaten = caps.beatenByBadges(s.badges),
        )
    }
}
