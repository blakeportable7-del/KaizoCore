package com.ironmonone.tracker.nds

import java.io.File

/** The tracker's only window into the game (same contract as the GBA side). */
fun interface NdsMemoryReader {
    fun read(address: Long, length: Int): ByteArray
}

/** Debug-only write channel, used by the inject verification path. */
fun interface NdsMemoryWriter {
    fun write(address: Long, data: ByteArray): Int
}

data class NdsSpeciesInfo(
    val name: String,
    val type1: String,
    val type2: String,
    val bst: Int,
    val ability1: String,
    val ability2: String,
)

data class NdsMoveInfo(
    val name: String,
    val power: Int,
    val accuracy: Int,
    val type: String,
    /** Base PP from the ROM's move table; PP Ups are applied per Pokemon. */
    val pp: Int = 0,
    /** Gen 4 stores a real split, so this is read rather than derived. */
    val category: String = "",
    /** The move id, for the run-wide move memory. 0 when the row was built without one. */
    val id: Int = 0,
    /**
     * The power as the reference prints it when it is not a number (WT, <HP, VAR...):
     * MainScreen.lua:507 sets the power label to MoveData's own string. Empty for a
     * plain number or "---"; [power] is 0 for these, so it would print "---".
     */
    val powerText: String = "",
)

data class NdsTrackedMon(
    val mon: Gen4.Mon,
    val speciesName: String,
    val info: NdsSpeciesInfo?,
    val abilityName: String,
    val itemName: String,
    val moves: List<NdsMoveInfo>,
    /** How many level-up moves this species has by its current level. */
    val movesLearned: Int = 0,
    /** How many it learns in total. */
    val movesTotal: Int = 0,
    /** Level of the next one, or null when there is nothing left. */
    val nextMoveLevel: Int? = null,
    /**
     * Battle stat stages, 6 = neutral, keyed HP/ATK/DEF/SPE/SPA/SPD/ACC/EVA.
     * Empty outside battle. The reference's readBattleStatStages.
     */
    val statStages: Map<String, Int> = emptyMap(),
    /** SLP/PSN/BRN/FRZ/PAR, or empty when healthy. */
    val statusCondition: String = "",
    /** The species' level-up levels, ascending, repeats kept (PokemonData movelvls for the version group). */
    val moveLevels: List<Int> = emptyList(),
)

data class NdsTrackerState(
    val partyCount: Int,
    val party: List<NdsTrackedMon>,
    /** True once the party has actually been located. */
    val located: Boolean,
    override val inBattle: Boolean = false,
    /** Wild battles allow fleeing; trainer battles never do. */
    override val isWildBattle: Boolean = false,
    val enemy: NdsTrackedMon? = null,
    /**
     * What the address chain resolved to, shown while nothing is located. Turns
     * "no party" into something actionable: an address here means the chain is
     * reading live memory and is simply waiting for a Pokémon to exist, while a
     * dash means the chain itself did not resolve.
     */
    val resolvedBase: Long = 0,
    /** What slot 0 of the party (and the enemy, in battle) decoded to, when neither produced a Pokemon. */
    val probe: String? = null,
    /** Gym badges as 8 bits, badge 1 in bit 0. */
    val badges: Int = 0,
    /** Steps left on the active repel, 0 when none is running, and the length it was (RepelDrawer). */
    val repelSteps: Int = 0,
    val repelDuration: Int = 100,
    /** Which badge art to draw: the map's BADGE_PREFIX (DPPT, HGSS). */
    val badgeSet: String = "DPPT",
    /** Carried healing as a share of the lead's max HP, and the item count. */
    val healPercent: Int = 0,
    val healCount: Int = 0,
    /** The bag's healing and status items, id to quantity (Program.scanForHealingItems). */
    val healingItems: Map<Int, Int> = emptyMap(),
    val statusItems: Map<Int, Int> = emptyMap(),
    /** The Pokemon the heals are measured against (Program.getHealingTotals' playerPokemon); its card carries the box. */
    val healsPid: Long = 0L,
    /** null while the run is alive; otherwise how it ended. */
    val runOver: NdsRunOver? = null,
    /** PlaythroughConstants.PROGRESS: 0 nowhere, 1 past the lab, 2 won. */
    val progress: Int = 0,
    /** The opponent's trainer id, 0 for a wild battle, as last read. */
    val enemyTrainerId: Int = 0,
    /** Program.updateLocation: the child map header id and the area name it (or the parent) maps to. */
    val mapId: Int = 0,
    val areaName: String = "",
    /**
     * Ability revealed by a battle trigger this tick: species to ability
     * name. The panel persists these per run; the reference's TrackAbility.
     */
    val abilityRevealed: Pair<Int, String>? = null,
    /** Every reveal since the last read, oldest first, including Gen 4
     *  messages [NdsTracker.pollAbilityTrigger] caught between reads. */
    val abilitiesRevealed: List<Pair<Int, String>> = emptyList(),
    /** HGSS: the League has been beaten (Program.HGSS_checkLeagueDefeated), which moves a lone badge row to Kanto. */
    val leagueBeaten: Boolean = false,
    /** GameInfo NAME of the game being read ("Pokemon Pearl"): the key of its past-runs log. Empty when unknown. */
    val gameName: String = "",
    /**
     * Your Pokemon on the field, in a fetched battle: the reference's playerPokemon
     * there (BattleHandlerBase.getActivePokemonInBattle), which Program.onRunEnded
     * logs as the one that fainted. Null outside battle.
     */
    val playerActive: NdsTrackedMon? = null,
    /**
     * Your Pokemon on the field and the opponent as the last fetched battle left
     * them. A win ends the run after its battle (BattleHandlerBase._onEndOfBattle),
     * when the reference's playerPokemon and enemyPokemon still hold these.
     */
    val lastBattlePlayer: NdsTrackedMon? = null,
    val lastBattleEnemy: NdsTrackedMon? = null,
) : com.ironmonone.tracker.RunView {
    override val enemySpeciesId: Int get() = enemy?.mon?.species ?: -1

    /**
     * Program's playerPokemon (Program.lua:521-535): your Pokemon on the field in a
     * fetched battle, else the first party member standing that is not an egg; the
     * one [healsPid] names, as its party entry, which carries your side's battle
     * stages. The lead when nothing else is known; null before a party is read.
     */
    val playerPokemon: NdsTrackedMon? get() = party.firstOrNull { it.mon.pid == healsPid } ?: party.firstOrNull()
    override val outcome: com.ironmonone.tracker.RunOutcome? get() = runOver?.let {
        if (it == NdsRunOver.WON) com.ironmonone.tracker.RunOutcome.WON
        else com.ironmonone.tracker.RunOutcome.LOST
    }
}

/**
 * How a Gen 4 run ended, and why.
 *
 * The DS tracker does something the GBA one does not: it works out the CAUSE of
 * the loss and picks its closing line from that. Losing to a Shedinja gets a
 * different message from losing to something 100 BST below you. Recreated from
 * PlaythroughConstants.CAUSES and SeedLogger.figureOutCause.
 */
enum class NdsRunOver { WON, SHEDINJA, IMPOSTER, ENEMY_LOWER_BST, STANDARD }

/**
 * Gen 4 tracker for Platinum.
 *
 * The addresses are NOT re-derived here: the pointer chain and the Platinum
 * offsets come from Brian0255/NDS-Ironmon-Tracker (GPL-3.0, same licence as this
 * app), which is the reference implementation the brief points at. That project
 * is Lua for BizHawk and cannot run inside an APK, so this is a port of its
 * knowledge, not a competitor to it. Resolution order is copied exactly:
 *
 *   globalPtr   = u32[0xBA8]        & 0xFFFFFF
 *   versionPtr  = u32[globalPtr+0x20] & 0xFFFFFF
 *   playerBase  = versionPtr + 0xB4
 *
 * What this file adds is verification: every Gen 4 Pokémon carries a checksum
 * over its own encrypted data, so a resolved address is CONFIRMED by decoding a
 * real Pokémon out of it. If the chain ever fails (a revision moves it, a future
 * game), [findParty] sweeps main RAM using the same checksum test, so the
 * tracker degrades to slower-but-working instead of silently blank.
 */
class NdsTracker(
    private val memory: NdsMemoryReader,
    sidecar: File? = null,
    /** Which DS game's offsets to read. See NdsGameMap; detect() picks it by header. */
    val map: NdsGameMap = NdsGameMap.PLATINUM,
) {
    /**
     * RandomBallScreen.lua: `math.random(1, 3)` once when the tracker starts,
     * labelled Left / Middle / Right, shown until the first Pokemon exists.
     * 1, 2 or 3.
     */
    val randomBall: Int = (1..3).random()

    /**
     * settings.trackedInfo.FAINT_DETECTION, "Run is considered over when:"
     * (TrackedInfoScreen.lua:238-244), ON_FIRST_SLOT_FAINT by default
     * (MiscConstants.lua:91). The app sets it from its DS option; how each
     * value is read is [runHasEnded].
     */
    @Volatile var lossCondition: com.ironmonone.tracker.LossCondition = com.ironmonone.tracker.LossCondition.LEAD
    /**
     * BattleHandlerBase._faintMonIndex: the battle-party slot the run hangs on,
     * chosen at the first check of a battle and kept to its end, -1 before that
     * and between battles (BattleHandlerBase.lua:237-243, 370).
     */
    private var faintMonIndex = -1
    /** BattleHandlerBase.inBattleAndFetched, for the battle last read: the loss checks run only then. */
    private var battleFetched = false
    /**
     * tracker.hasRunEnded(): the run has already ended, by a loss or a win. The
     * app sets it from its game-over latch, which is the reference's
     * Program.onRunEnded / tracker.setRunOver pair. While it is set a battle's
     * end changes nothing (BattleHandlerBase._onEndOfBattle, lua:350).
     */
    @Volatile var runEnded: Boolean = false
    /** Your Pokemon on the field and the opponent, as the last fetched battle read them. */
    private var lastBattlePlayer: NdsTrackedMon? = null
    private var lastBattleEnemy: NdsTrackedMon? = null
    private val ramStart = 0x02000000L
    private val ramEnd = 0x02400000L
    /**
     * Absolute maps without a pointer (Black, White, and Black 2 and White 2 while their pointer cannot
     * be used): a scan whenever the fixed address holds nothing, on a cooldown so the 4 MB walk runs about
     * once every few seconds, never once and never again (a latch here hid the party on Blake's phone for a
     * whole evening: the first scan ran before the starter existed and nothing ever rescanned). [scanShift]
     * is where the party sat relative to the PC address.
     */
    private var readsSinceScan = SCAN_EVERY
    var scanShift: Long = 0
        private set

    /**
     * How the heap addresses in [live] were found. POINTER: read from the game's own pointer, which is how
     * every read of Black 2 and White 2 goes while the pointer can be used (NDS-Ironmon-Tracker 6.3.11).
     * SCAN: the party was searched for in RAM, because the pointer could not be used or led to no party
     * while a battle was on. STATIC: the map's own fixed addresses, before either has happened.
     */
    enum class BaseSource { STATIC, POINTER, SCAN }
    var baseSource: BaseSource = BaseSource.STATIC
        private set
    /** How many 4 MB party scans have run. A Black 2 or White 2 whose pointer works makes none. */
    var scans: Int = 0
        private set
    /** The base the pointer named on the last read, or null when it could not be used (none, or not a map with a pointer). */
    var pointerBase: Long? = null
        private set
    /** The pointer's base that [live] is at, and a base the scan found the party away from (ignored until the pointer changes). */
    private var followedBase: Long? = null
    private var distrustedBase: Long? = null
    /**
     * Tracker.getFirstPokemonID: the first Pokemon this tracker saw in the
     * party. The log viewer matches it against the log's starters to pick the
     * rival's teams and Black and White's first gym. 0 until one exists.
     */
    @Volatile var firstPokemonId: Int = 0
        private set
    private val chunk = 0x20000

    /**
     * GameInfo NAME of the game this tracker reads, from the cartridge header's
     * code: "Pokemon Pearl", where the map serves Diamond and Pearl alike. The
     * reference keys its past-runs log by it (SeedLogger(self, gameInfo.NAME),
     * savedData/<name>.pastlog, SeedLogger.lua:233). A header that cannot be
     * read, or names a game this map does not serve, gives the map's first game.
     */
    val gameName: String = run {
        val code = runCatching { NdsGameMap.gameCode(memory) }.getOrNull()
        val game = code?.takeIf { it in map.gameCodes }?.let { NdsLogData.game(it) } ?: NdsLogData.gameFor(map, "")
        game?.name ?: ""
    }

    // Offsets come from the game's NdsGameMap (copied from NDS-Ironmon-Tracker's
    // MemoryAddresses.lua); Platinum unless detect() said otherwise.
    private val globalPointer = map.globalPointer
    private val versionPointerOffset = NdsGameMap.VERSION_POINTER_OFFSET
    private val playerBaseOffset = map.playerBase
    private val enemyBaseOffset = map.enemyBase
    /**
     * The map the reads use. A randomized Gen 5 ROM lays its heap out a
     * constant number of bytes away from the clean ROM's (measured 2026-09-08:
     * the party, the enemy party, the trainer id, the battle pointers and the
     * battle PIDs all sat 0x40 below the PC tracker's addresses on one
     * randomized Black 2, 0x54 on another). The battle flag lives in static
     * memory and never moves.
     *
     * Black 2 and White 2 are told where their heap is by a pointer in main RAM, which
     * [followPointer] reads on every read, so [live] is that base's map (NDS-Ironmon-Tracker
     * 6.3.11). Anywhere else, or when that pointer cannot be used, the fixed party address that
     * holds no Pokemon makes findParty() scan, and the whole map follows the shift it finds.
     */
    var live: NdsGameMap = map
        private set

    private val enemyTrainerIdOffset get() = live.enemyTrainerId
    /** PID of the enemy Pokemon actually on the field (VERSION_POINTER_OFFSETS
     *  enemyBattleMonPID). Slot 0 of the enemy party is only the LEAD; after a
     *  trainer switches, the mon fighting you is whichever party slot carries
     *  this PID. Without the match the panel kept showing the first Pokemon
     *  for the whole battle. */
    private val enemyBattleMonPidOffset get() = live.enemyBattleMonPid
    /** VERSION_POINTER_OFFSETS.playerBattleMonPID / statStages / subscript. */
    private val playerBattleMonPidOffset get() = live.playerBattleMonPid
    private val statStagesPlayerOffset get() = live.statStagesPlayer
    private val statStagesEnemyOffset get() = live.statStagesEnemy
    private val battleSubscriptMsgsOffset get() = live.battleSubscriptMsgs
    /**
     * GLOBAL (not pointer-relative) in that project's table, on every game, Black 2 and White 2 included:
     * it is the one address no pointer and no heap shift moves, which is what makes it the way to tell a
     * battle is on when the heap is not where the tracker thinks (see [scanAllowed]). Always the map's own.
     */
    private val battleStatusGlobal = map.battleStatus
    // Bag pockets. Items and berries are separate lists, and on Gen 4 both
    // move to a different address while a battle is running. Like every heap
    // address they follow [live]: they used to be copied from it once, before
    // any scan, so a Black 2 whose heap sat 0x40 below the PC tracker's
    // addresses walked an empty bag (parity audit, 2026-09-28).
    private val itemStartNoBattle get() = live.itemStartNoBattle
    private val itemStartBattle get() = live.itemStartBattle
    private val berryBagStart get() = live.berryBagStart
    private val berryBagStartBattle get() = live.berryBagStartBattle

    /**
     * Battle-status words, from BattleHandlerBase.BATTLE_STATUS_TYPES: 0x2100 and
     * 0x2101 mean a battle is running, 0x2800 means it is not. Anything else is
     * treated as "not in battle" rather than guessed at.
     */
    private val inBattleWords = setOf(0x2100, 0x2101)

    private val speciesNames = HashMap<Int, String>()
    private val abilityNames = HashMap<Int, String>()
    private val itemNames = HashMap<Int, String>()
    private val moveInfo = HashMap<Int, NdsMoveInfo>()
    private val speciesInfo = HashMap<Int, NdsSpeciesInfo>()

    /**
     * Level-up LEVELS per species, ascending. Not the moves - the levels.
     *
     * This is how the DS tracker does it, and the reason it works is worth
     * stating: a randomizer changes WHICH move is learned at each slot but
     * leaves the levels alone, so a static table stays correct across every
     * seed. Gen 4 learnsets live in the ROM's narc archives rather than in
     * RAM, so there is nothing to read live; the reference ships the same
     * table (PokemonData.POKEMON_MASTER_LIST movelvls, one list per version
     * group, Program.lua:327) and so does this: the map names the table
     * (tools/trainer-data/convert_nds_movelevels.py writes the Gen 4 ones).
     */
    private val moveLevels = HashMap<Int, List<Int>>()

    private var partyBase = 0L
    /**
     * Gen 5: the last ability-trigger word seen per battler slot (player,
     * opponent), BattleHandlerGen5's lastAbilityValue. A reveal fires once,
     * when the word changes to something non-zero; -1 until a battle is up.
     */
    private val lastAbilityTrigger = longArrayOf(-1L, -1L)

    /**
     * Gen 4: the reference checks the battle message every 8 frames, and the
     * message is gone a moment later. read() runs every 250 ms at best, so at
     * fast-forward reveals were missed (2026-09-28, Blake: "do the DS tracker
     * too"). Between reads the play screen calls this; it reads one u16 and
     * queues a message that can reveal an ability, resolved against the mons
     * on the field at the next read.
     */
    @Volatile private var pollVersionRel = 0L
    private val pendingMsgs = ArrayList<Int>()

    fun pollAbilityTrigger() {
        val rel = pollVersionRel
        if (rel == 0L || map.absolute) return
        val b = runCatching { memory.read(ramStart + rel + battleSubscriptMsgsOffset, 2) }.getOrNull() ?: return
        if (b.size < 2) return
        val msg = b.u16(0)
        if (battleMsgAbilities[msg] == null) return
        synchronized(pendingMsgs) { if (pendingMsgs.lastOrNull() != msg) pendingMsgs += msg }
    }

    init {
        loadPairs("/${map.dataDir}/species.tsv", speciesNames)
        loadPairs("/${map.dataDir}/abilities.tsv", abilityNames)
        // MainScreen.lua:860 names the held item from ItemData.GEN_5_ITEMS on EVERY DS
        // game, Diamond to White 2. Gen 4 read Platinum's own table, which stops at 467,
        // so an HGSS Apricorn, Apricorn Ball, Sport or Park Ball (468-536) showed "#id"
        // (parity audit, 2026-09-28); D/P/Pt names now follow the reference's spelling too.
        loadPairs("/gen5/items.tsv", itemNames)
        loadMoves()
        loadMoveLevels()
        sidecar?.takeIf { it.exists() }?.let(::loadSidecar)
    }

    private fun loadMoveLevels() {
        javaClass.getResourceAsStream(map.moveLevelsResource)
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('	')
                    if (tab > 0) line.substring(0, tab).toIntOrNull()?.let { id ->
                        moveLevels[id] = line.substring(tab + 1)
                            .split(',').mapNotNull { it.trim().toIntOrNull() }
                    }
                }
            }
    }

    /** Levels at which [species] learns a move, ascending. */
    fun moveLevelsOf(species: Int): List<Int> = moveLevels[species] ?: emptyList()

    private fun loadPairs(resource: String, into: HashMap<Int, String>) {
        javaClass.getResourceAsStream(resource)
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) line.substring(0, tab).toIntOrNull()?.let {
                        into[it] = line.substring(tab + 1)
                    }
                }
            }
    }

    private fun loadMoves() {
        javaClass.getResourceAsStream("/${map.dataDir}/moves.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size >= 5) p[0].toIntOrNull()?.let {
                        moveInfo[it] = NdsMoveInfo(
                            id = it,
                            name = p[1],
                            power = p[2].toIntOrNull() ?: 0,
                            accuracy = p[3].toIntOrNull() ?: 0,
                            type = p[4],
                            pp = p.getOrNull(5)?.toIntOrNull() ?: 0,
                            category = p.getOrNull(6) ?: "",
                            powerText = p.getOrNull(7)?.trim() ?: "",
                        )
                    }
                }
            }
    }

    /** id, name, type1, type2, bst, ability1, ability2 — post-randomization. */
    private fun loadSidecar(file: File) {
        runCatching {
            file.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 7) p[0].toIntOrNull()?.let {
                    speciesInfo[it] = NdsSpeciesInfo(
                        name = p[1], type1 = p[2], type2 = p[3],
                        bst = p[4].toIntOrNull() ?: 0,
                        ability1 = p[5], ability2 = p[6],
                    )
                }
            }
        }
    }

    /** The ROM data's row for a move id, for the enemy card's moves seen in an earlier battle. */
    fun moveInfoFor(id: Int): NdsMoveInfo? = moveInfo[id]

    fun speciesName(id: Int): String =
        speciesInfo[id]?.name ?: speciesNames[id] ?: "#$id"

    /** The ROM data's row for a species, for screens that need its BST, types and abilities. */
    fun speciesInfoFor(id: Int): NdsSpeciesInfo? = speciesInfo[id]

    /** Every move in the game's table, for the log viewer's move details and search. */
    fun moveTable(): List<NdsMoveInfo> = moveInfo.values.sortedBy { it.id }

    /** Every ability name in the game's table, for the log viewer's search. */
    fun abilityTable(): List<String> = abilityNames.toSortedMap().values.toList()

    /** Tracker.getProgress: 0 nowhere, 1 past the lab, 2 won. Set by the battle that ends against a lab or final trainer. */
    var progress: Int = 0
        private set
    private var lastTrainerId = 0
    private var wasInBattle = false
    /** BattleHandlerBase._defeatedTrainerList: every trainer a battle has ended against this session. */
    val defeatedTrainers: MutableSet<Int> = HashSet()

    private val locations: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        if (map.locationsResource.isNotEmpty()) javaClass.getResourceAsStream(map.locationsResource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line -> if (!line.startsWith("#")) { val p = line.split('\t'); if (p.size >= 2) p[0].toIntOrNull()?.let { out[it] = p[1] } } }
        }
        out
    }
    private var lastMapId = 0
    private var lastAreaName = ""
    /** Program.lua:86: the Bug Catching weekday, Tuesday (2) until one has been read. */
    private var dayOfWeek = 2

    /**
     * Program.updateLocation: the child map header, then the parent, looked up
     * in LocationData; the Mystery Zone (id 0's name) never replaces a known
     * area.
     *
     * HGSS (Program.lua:601-611): the IronMON patch writes the real weekday
     * only while the player stands in either contest gatehouse (child maps 102
     * and 104), so it is read there and kept, and the National Park's "Bug
     * Catching" area becomes "Tues", "Thurs" or "Sat Bug Catching" by it, any
     * other day reading as Tuesday.
     */
    private fun updateLocation() {
        if (live.childMapHeader == 0L) return
        val versionRel = if (map.absolute) 0L else versionPointer()
        if (!map.absolute && versionRel == 0L) return
        // The map headers are heap too and follow [live] (a shifted Black 2 read Black City's id 0 here).
        val cb = memory.read(ramStart + versionRel + live.childMapHeader, 2); if (cb.size < 2) return
        val pb = memory.read(ramStart + versionRel + live.parentMapHeader, 2)
        val child = cb.u16(0); val parent = if (pb.size == 2) pb.u16(0) else 0
        if ((child == 102 || child == 104) && map.dayOfWeek != 0L) {
            val d = memory.read(ramStart + versionRel + map.dayOfWeek, 2)
            if (d.size == 2) dayOfWeek = d.u16(0)
        }
        var name = locations[child] ?: locations[parent] ?: return
        if (name == "Bug Catching") name = BUG_CATCHING_DAYS[dayOfWeek] ?: BUG_CATCHING_DAYS.getValue(2)
        lastMapId = child
        if (name != locations[0]) lastAreaName = name
    }

    /** EvoDataScreen's EvoData.EVOLUTIONS[base]: target id -> (evo id, percent) in the reference's order, from gen4/evos.tsv or gen5/evos.tsv. */
    private val evoData: Map<Int, Map<Int, List<Pair<Int, Double>>>> by lazy {
        val out = HashMap<Int, LinkedHashMap<Int, List<Pair<Int, Double>>>>()
        javaClass.getResourceAsStream("/${map.dataDir}/evos.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#")) return@forEach
                val p = line.split('\t'); if (p.size < 3) return@forEach
                val base = p[0].toIntOrNull() ?: return@forEach
                val target = p[1].toIntOrNull() ?: return@forEach
                out.getOrPut(base) { LinkedHashMap() }[target] = p[2].split(',').mapNotNull { e ->
                    val c = e.indexOf(':'); if (c <= 0) null else {
                        val id = e.substring(0, c).toIntOrNull(); val perc = e.substring(c + 1).toDoubleOrNull()
                        if (id == null || perc == null) null else id to perc
                    }
                }
            }
        }
        out
    }

    fun evoData(species: Int): Map<Int, List<Pair<Int, Double>>> = evoData[species] ?: emptyMap()

    private fun u32(addr: Long): Long {
        val b = memory.read(addr, 4)
        return if (b.size == 4) b.u32(0) else 0L
    }

    /**
     * Follow the published pointer chain, then PROVE it by decoding a Pokémon at
     * the result. Returns 0 when the chain does not lead to real party data.
     */
    fun resolvePartyViaPointers(): Long {
        val base = rawPointerChain()
        if (base == 0L) return 0
        val first = memory.read(base, Gen4.PARTY_ENTRY_SIZE)
        if (first.size < Gen4.PARTY_ENTRY_SIZE) return 0
        return if (Gen4.decodeParty(first) != null) base else 0
    }

    /**
     * DEBUG ONLY. Writes a known, correctly encrypted Pokémon into party slot 0
     * so the whole chain — address resolution, memory write, memory read, decode,
     * panel render — can be proven without playing to the first starter.
     *
     * This is a synthetic Pokémon placed in memory, NOT one the game handed out.
     * It proves the tracker reads what is there; it does not prove the game put
     * it there. Returns a human-readable result either way.
     */
    /** Debug: a distinctive test Pokemon written at [address] in this game's entry format, to prove the scan finds it. */
    fun injectAt(writer: NdsMemoryWriter, address: Long): String {
        val bytes = Gen4.encodeParty(pid = 0x0BADF00DL, species = 464, level = 42, curHp = 77, maxHp = 130,
            moves = listOf(224, 89, 157, 0), abilityId = 31, heldItem = 13, gen5 = map.generation == 5)
        val n = writer.write(address, bytes)
        writer.write(address + bytes.size, ByteArray(8))
        partyBase = 0L; readsSinceScan = SCAN_EVERY
        return "injected %d bytes at 0x%08X".format(n, address)
    }

    fun injectTestMon(writer: NdsMemoryWriter): String {
        val base = rawPointerChain()
        if (base == 0L) return "address chain did not resolve — nothing written"
        // Rhyperior, deliberately distinctive so it cannot be confused with a
        // real early-game Pokemon if this is ever seen by accident.
        val bytes = Gen4.encodeParty(
            pid = 0x0BADF00DL,
            species = 464,
            level = 42,
            curHp = 77,
            maxHp = 130,
            moves = listOf(224, 89, 157, 0),
            abilityId = 31,
            heldItem = 13,
        )
        val n = writer.write(base, bytes)
        if (n < bytes.size) return "write failed at 0x%08X (%d of %d bytes)"
            .format(base, n, bytes.size)
        // Slot 1 must NOT look like a Pokemon, or the panel would show leftovers.
        writer.write(base + Gen4.PARTY_ENTRY_SIZE, ByteArray(8))
        partyBase = 0L        // force a re-resolve so the read is honest
        return "injected test mon at 0x%08X".format(base)
    }

    /**
     * What ended a lost run, recreated from SeedLogger.figureOutCause: the cause
     * picks the closing line. [fainted] is the reference's playerPokemon when the
     * run ends, your Pokemon on the field. Order matters there: a Shedinja that
     * also happens to be 100 BST below you is still reported as a Shedinja loss.
     */
    internal fun runOverCause(
        fainted: NdsTrackedMon?,
        enemy: NdsTrackedMon?,
    ): NdsRunOver {
        val enemyName = enemy?.speciesName?.uppercase() ?: ""
        val enemyAbility = enemy?.abilityName?.uppercase() ?: ""
        val faintedBst = fainted?.info?.bst ?: 0
        val enemyBst = enemy?.info?.bst ?: 0
        return when {
            enemyName == "SHEDINJA" -> NdsRunOver.SHEDINJA
            enemyAbility == "IMPOSTER" -> NdsRunOver.IMPOSTER
            faintedBst > 0 && enemyBst > 0 && faintedBst - enemyBst >= 100 ->
                NdsRunOver.ENEMY_LOWER_BST
            else -> NdsRunOver.STANDARD
        }
    }

    /**
     * BattleHandlerBase.checkIfRunHasEnded (BattleHandlerBase.lua:226-247), which
     * Program.readMemory calls every tick but which does nothing unless a battle
     * is running and its data has been fetched: a faint outside battle, or one
     * noticed only after the battle, never ends a DS run. Everything here reads
     * the battle's own copy of your party (playerBattleBase), not the party:
     *
     * - Entire party faints: every Pokemon in that copy at 0 HP (_hasPartyWiped).
     * - Lead Pokemon faints: slot 1 of that copy at 0 HP.
     * - Highest level faints: the slot with the highest level, picked at the
     *   first check of the battle and watched until it ends, even if levels
     *   change (_calculateHighestPlayerMonIndex). It walks the slots with
     *   pairs(), which in Lua 5.1 to 5.4 visits slots 2 to 6 and then the lead
     *   (slot 0 lives in the hash part; checked with lupa), and keeps the first
     *   strictly higher level: on a tie the later slot is watched, and the lead
     *   only when it is the highest alone.
     *
     * Gen 5 reads the HP from each party member's battle data instead
     * (BattleHandlerGen5._hasPartyWiped and _playerSlotHasFainted, lua:226-255):
     * the pointers at mainBattleDataPtr + 4i, in address order, HP at + 0x10.
     * With no pointer at all the Gen 5 override would call the party wiped (its
     * loop never runs); that is taken as nothing read, as Gen 4's own check does.
     * The Gen 5 reference then waits for the battle screen's HP byte to reach 0
     * (_onPlayerSlotFainted); not ported, see the note on [read].
     */
    internal fun runHasEnded(versionRel: Long): Boolean {
        val party = battleParty(versionRel)
        val gen5 = map.absolute
        if (lossCondition == com.ironmonone.tracker.LossCondition.ENTIRE_PARTY) {
            val wiped = if (gen5) gen5PartyPointers().let { p -> p.isNotEmpty() && p.all { battleHp(it) == 0 } }
                else party.isNotEmpty() && party.values.all { it.curHp == 0 }
            if (wiped) return true
        }
        // Kaizo Doubles: either of the battle copy's first two slots. No DS reference has it; the
        // app offers it on every game (Blake, 2026-09-29: full control).
        if (lossCondition == com.ironmonone.tracker.LossCondition.EITHER_OF_FIRST_TWO)
            return if (gen5) gen5PartyPointers().take(2).any { battleHp(it) == 0 }
                else (0..1).any { party[it]?.curHp == 0 }
        if (faintMonIndex == -1) faintMonIndex = when (lossCondition) {
            com.ironmonone.tracker.LossCondition.HIGHEST_LEVEL -> highestLevelSlot(party)
            com.ironmonone.tracker.LossCondition.LEAD -> 0
            com.ironmonone.tracker.LossCondition.ENTIRE_PARTY, com.ironmonone.tracker.LossCondition.EITHER_OF_FIRST_TWO -> -1
        }
        if (faintMonIndex < 0) return false
        return if (gen5) gen5PartyPointers().getOrNull(faintMonIndex)?.let { battleHp(it) == 0 } ?: false
            else party[faintMonIndex]?.curHp == 0
    }

    /** _calculateHighestPlayerMonIndex over [party]: pairs() order, slots 2 to 6 then the lead, first strictly higher level; -1 for none. */
    private fun highestLevelSlot(party: Map<Int, Gen4.Mon>): Int {
        var maxLevel = 0
        var slot = -1
        for (i in listOf(1, 2, 3, 4, 5, 0)) {
            val m = party[i] ?: continue
            if (m.level > maxLevel) { maxLevel = m.level; slot = i }
        }
        return slot
    }

    /** BattleHandlerBase._getPlayerParty: the battle's copy of your party, slot to Pokemon, empty slots left out. */
    private fun battleParty(versionRel: Long): Map<Int, Gen4.Mon> {
        if (live.playerBattleBase == 0L) return emptyMap()
        val out = LinkedHashMap<Int, Gen4.Mon>()
        for (slot in 0 until 6) {
            val b = memory.read(ramStart + versionRel + live.playerBattleBase + slot.toLong() * map.entrySize, map.entrySize)
            if (b.size < map.entrySize) break
            Gen4.decodeParty(b, gen5 = map.generation == 5)?.let { out[slot] = it }
        }
        return out
    }

    /** BattleHandlerGen5._readPlayerPartyPointers: your party's battle data, the non-zero words at mainBattleDataPtr + 4i, ascending. */
    private fun gen5PartyPointers(): List<Long> =
        (0 until 6).mapNotNull { i -> ptr(ramStart + live.mainBattleDataPtr + 4L * i) }.sorted()

    /** BATTLE_STAT_OFFSETS curHP: the HP a Gen 5 battle data block holds. */
    private fun battleHp(battleData: Long): Int = memory.read(battleData + 0x10, 2).let { if (it.size == 2) it.u16(0) else -1 }

    /**
     * Your Pokemon on the field (BattleHandlerBase.getActivePokemonInBattle for
     * the player). Gen 4: the battle-party entry carrying the PID at
     * playerBattleMonPID (BattleHandlerGen4._getPokemonData). Gen 5: the
     * Pokemon the first battler record points at, with its live HP and level
     * from the battle data (_getPokemonData, _readBattleStats).
     */
    private fun readPlayerActive(versionRel: Long): NdsTrackedMon? {
        if (map.absolute) {
            val bd = ptr(ramStart + live.mainBattleDataPtr) ?: return null
            val pd = ptr(bd) ?: return null
            val d = Gen4.decodeParty(memory.read(pd, map.entrySize), gen5 = true) ?: return null
            fun u16At(off: Long, fallback: Int) = memory.read(bd + off, 2).let { if (it.size == 2) it.u16(0) else fallback }
            return decorate(d.copy(curHp = u16At(0x10, d.curHp), maxHp = u16At(0x0E, d.maxHp), level = u16At(0x18, d.level) % 256))
        }
        val pid = u32(ramStart + versionRel + playerBattleMonPidOffset)
        if (pid == 0L) return null
        return battleParty(versionRel).values.firstOrNull { it.pid == pid }?.let(::decorate)
    }

    /** Healing carried, as a share of [maxHp] rounded as the reference rounds, and the count (NdsHeals.totals). */
    internal fun readHeals(maxHp: Int, inBattle: Boolean): Pair<Int, Int> =
        NdsHeals.totals(readBag(inBattle).first, maxHp, showHp = false)

    /**
     * Program.scanForHealingItems (Program.lua:334-377): the healing items and the
     * status items in the bag, each id to its quantity (NdsHeals' tables).
     *
     * Gen 4 stores bag slots as a u32: item id in the low half, quantity in the
     * high half, and NO encryption - unlike Gen 3, where the quantity is XORed.
     * The list is null-terminated rather than fixed length, so the scan stops at
     * the first zero id instead of walking a fixed slot count.
     *
     * Items and berries are separate pockets, and both move while a battle is
     * running. The battle bag is populated a few frames AFTER the battle flag
     * flips, so a read in that window returns garbage; the reference guards it
     * by sanity-checking the first battle slot and falling back, and so does
     * this. A quantity outside 1 to 999 is dropped as a bad read, which the
     * reference does not do.
     */
    internal fun readBag(inBattle: Boolean): Pair<Map<Int, Int>, Map<Int, Int>> {
        val healing = LinkedHashMap<Int, Int>()
        val status = LinkedHashMap<Int, Int>()
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return healing to status
        var itemStart = itemStartNoBattle
        var berryStart = berryBagStart
        if (inBattle) {
            itemStart = itemStartBattle
            berryStart = berryBagStartBattle
            val probe = u32(ramStart + versionRel + itemStartBattle)
            val id = (probe and 0xFFFFL).toInt()
            val qty = ((probe shr 16) and 0xFFFFL).toInt()
            if (qty > 1000 || id > 600) {
                itemStart = itemStartNoBattle
                berryStart = berryBagStart
            }
        }

        for (start in listOf(itemStart, berryStart)) {
            var addr = ramStart + versionRel + start
            // 400 slots is far beyond any real bag; the null terminator is what
            // normally stops this, and the bound only guards a bad pointer.
            for (i in 0 until 400) {
                val word = u32(addr)
                val id = (word and 0xFFFFL).toInt()
                if (id == 0) break
                val qty = ((word shr 16) and 0xFFFFL).toInt()
                if (qty in 1..999) {
                    // healingItems[id] = quantity, statusItems[id] = quantity: Full Restore is both.
                    if (NdsHeals.isHeal(id)) healing[id] = qty
                    if (NdsHeals.isCure(id)) status[id] = qty
                }
                addr += 4
            }
        }
        return healing to status
    }

    /**
     * Coverage over the Gen 4 dex, the same question the GBA screen answers:
     * the best multiplier this moveset gets against each species.
     *
     * Types come from the per-run sidecar, so this follows the randomization
     * rather than vanilla typings. With no sidecar there are no types and the
     * honest answer is an empty table, not a confident wrong one.
     */
    /** BST from the run's sidecar, for the Coverage Calc list's order (the DS reference sorts by BST). */
    fun speciesBst(id: Int): Int = speciesInfo[id]?.bst ?: 0

    companion object {
        /** Reads between scans while no party decodes; the tracker reads a few times a second. */
        const val SCAN_EVERY = 20
        /** Program.lua:606-609, dayToNewName: the contest days the IronMON HGSS patch uses. */
        private val BUG_CATCHING_DAYS = mapOf(2 to "Tues Bug Catching", 4 to "Thurs Bug Catching", 6 to "Sat Bug Catching")
        /** The last raw party and enemy bytes read while nothing decoded, for the bug report. */
        @Volatile var lastDump: String? = null
    }
    /** True once the randomizer's sidecar has been read; types and BST come from nowhere else on DS. */
    fun hasSpeciesData(): Boolean = speciesInfo.isNotEmpty()

    fun coverage(moveTypes: List<String>): Map<Double, List<Int>> {
        val out = linkedMapOf(
            0.0 to ArrayList<Int>(), 0.25 to ArrayList(), 0.5 to ArrayList(),
            1.0 to ArrayList(), 2.0 to ArrayList(), 4.0 to ArrayList(),
        )
        if (moveTypes.isEmpty() || speciesInfo.isEmpty()) return out
        val atk = moveTypes.mapNotNull { Gen4Types.idOf(it) }
        if (atk.isEmpty()) return out
        for ((id, info) in speciesInfo) {
            val t1 = Gen4Types.idOf(info.type1) ?: continue
            val t2 = Gen4Types.idOf(info.type2) ?: t1
            var best = 0.0
            for (a in atk) {
                val e = Gen4Types.effect(a, t1, t2)
                if (e > best) best = e
            }
            out[best]?.add(id)
        }
        return out
    }

    /** Gym badges, from the same pointer chain (offset 0x96). */
    /** RepelDrawer: the repel that was used, kept while it counts down. */
    private var repelDuration = com.ironmonone.tracker.RepelRules.DEFAULT_DURATION

    /**
     * RepelDrawer.Update: the steps the active repel has left. Gen 4 keeps it
     * among the version-pointer offsets, Gen 5 at a fixed address; a byte past
     * 250 is not a repel and reads as none.
     */
    internal fun readRepelSteps(): Int {
        if (live.repelSteps == 0L) return 0
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return 0
        val b = memory.read(ramStart + versionRel + live.repelSteps, 1)
        val steps = if (b.isEmpty()) 0 else b.u8(0)
        return com.ironmonone.tracker.RepelRules.stepsOf(steps)
    }

    internal fun readBadges(): Int {
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return 0
        // Platinum: one byte. HGSS: Johto then Kanto, combined so the badge
        // row keeps one shape (Johto bits 0-7, Kanto 8-15).
        var bits = 0
        live.badgeOffsets.forEachIndexed { i, off ->
            val b = memory.read(ramStart + versionRel + off, 1)
            if (b.isNotEmpty()) bits = bits or (b.u8(0) shl (8 * i))
        }
        return bits
    }

    /**
     * Program.HGSS_checkLeagueDefeated (Program.lua:583-586): the League event byte
     * at versionRel + 0x1000 reaching 3 means the Champion is beaten; the main
     * screen's single badge row then shows Kanto (MainScreen.lua:1285-1295). Only
     * HeartGold and SoulSilver have the byte.
     */
    internal fun readLeagueBeaten(): Boolean {
        if (map.leagueBeaten == 0L) return false
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return false
        val b = memory.read(ramStart + versionRel + map.leagueBeaten, 1)
        return b.isNotEmpty() && b.u8(0) >= 3
    }

    /** Second link of the chain, shared by the party and battle lookups. */
    private fun versionPointer(): Long {
        // Gen 5: no chain, every offset is already relative to main RAM.
        if (map.absolute) return 0L
        val globalRel = u32(ramStart + globalPointer) and 0xFFFFFFL
        if (globalRel == 0L) return 0
        return u32(ramStart + globalRel + versionPointerOffset) and 0xFFFFFFL
    }

    /** The chain's result WITHOUT the Pokémon check, for diagnostics. */
    fun rawPointerChain(): Long {
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return 0
        val base = ramStart + versionRel + playerBaseOffset
        return if (base in ramStart until ramEnd) base else 0
    }

    /**
     * Black 2 and White 2 (NDS-Ironmon-Tracker 6.3.11): where the heap is comes from one pointer in main RAM,
     * read again on every read, so nothing is latched. A usable pointer that has changed takes [live] to
     * its new base at once. One the scan has shown to lead nowhere while a battle was on (see [scanAllowed])
     * is left alone until its value changes. A pointer that cannot be used leaves [live] as it was, and
     * the scan looks for the party as it always did. A game with no pointer table does nothing here.
     */
    private fun followPointer() {
        val table = map.pointerOffsets ?: return
        val base = table.baseOf(u32(ramStart + table.pointer))
        pointerBase = base
        if (base == null || base == distrustedBase) return
        distrustedBase = null
        if (baseSource == BaseSource.POINTER && base == followedBase) return
        live = map.atPointerBase(base)
        followedBase = base
        baseSource = BaseSource.POINTER
        partyBase = 0L   // read() takes the party from the new map
    }

    /**
     * Whether a party scan may run now. Without a pointer to lean on, always (on its cooldown). With a
     * pointer that can be used, only while the fixed battle flag says a battle is on and no party decodes where
     * the pointer says: a battle cannot happen without a party, so the pointer is wrong for this ROM and the
     * scan is right. Before the first Pokemon nothing decodes either, and a scan then would only walk 4 MB to
     * find nothing, or old bytes left over from the run before.
     */
    private fun scanAllowed(): Boolean = map.pointerOffsets == null || pointerBase == null || battleFlagSet()

    /** The fixed battle-status word says a battle is running ([inBattleWords]). */
    private fun battleFlagSet(): Boolean =
        memory.read(ramStart + battleStatusGlobal, 2).let { it.size == 2 && it.u16(0) in inBattleWords }

    /**
     * Fallback: sweep main RAM for a valid party entry. Two-stage — a cheap
     * species-only decrypt rejects almost everything, then the checksum decides.
     */
    /**
     * Scans main RAM for the first decodable party entry and walks back to
     * slot 0. Gen 4 and Gen 5 (220-byte entries, 649 species). Used when a
     * pointer chain fails and, since 2026-09-08, when an absolute map's fixed
     * address holds no Pokemon: Black 2 on this core keeps static data at the
     * PC tracker's party address (identical bytes on two devices), so the
     * party is found instead of assumed.
     */
    fun findParty(): Long {
        val gen5 = map.generation == 5
        val size = map.entrySize
        val maxSpecies = if (gen5) Gen4.MAX_SPECIES_GEN5 else Gen4.MAX_SPECIES
        var addr = ramStart
        while (addr < ramEnd) {
            val len = minOf(chunk, (ramEnd - addr).toInt())
            val buf = memory.read(addr, len)
            if (buf.isEmpty()) { addr += chunk; continue }

            var i = 0
            while (i + size <= buf.size) {
                if (Gen4.quickSpecies(buf, i) in 1..maxSpecies) {
                    val entry = buf.copyOfRange(i, i + size)
                    val mon = Gen4.decodeParty(entry, gen5)
                    if (mon != null && !mon.isEgg) {
                        // Walk back to slot 0 so party order is right.
                        var base = addr + i
                        while (base - size >= ramStart) {
                            val prev = memory.read(base - size, size)
                            if (prev.size < size) break
                            if (Gen4.decodeParty(prev, gen5) == null) break
                            base -= size
                        }
                        return base
                    }
                }
                i += 4
            }
            addr += (chunk - size)   // overlap, so nothing straddles
        }
        return 0
    }

    fun read(): NdsTrackerState {
        followPointer()
        if (partyBase == 0L) {
            partyBase = if (map.absolute) ramStart + live.playerBase
                else resolvePartyViaPointers().takeIf { it != 0L } ?: findParty()
            if (partyBase == 0L) {
                return NdsTrackerState(
                    0, emptyList(), located = false, badgeSet = map.badgePrefix, resolvedBase = rawPointerChain(),
                    gameName = gameName)
            }
        }

        val party = ArrayList<NdsTrackedMon>(6)
        var probe: String? = null
        for (slot in 0 until 6) {
            val bytes = memory.read(
                partyBase + slot.toLong() * map.entrySize, map.entrySize)
            if (slot == 0 && bytes.size < map.entrySize) probe = "party @%08X: ".format(partyBase) + Gen4.probe(bytes, map.generation == 5)
            if (bytes.size < map.entrySize) break
            val mon = Gen4.decodeParty(bytes, gen5 = map.generation == 5)
            if (mon == null) {
                if (slot == 0) {
                    probe = "party @%08X: ".format(partyBase) + Gen4.probe(bytes, map.generation == 5)
                    if (map.absolute && readsSinceScan >= SCAN_EVERY && scanAllowed()) {
                        readsSinceScan = 0
                        scans++
                        val found = findParty()
                        probe += if (found != 0L) " | scan found a party at %08X (shift %s%X from the PC address)".format(found, if (found >= ramStart + live.playerBase) "+" else "-", kotlin.math.abs(found - (ramStart + live.playerBase)))
                            else " | scan found no party in RAM"
                        if (found != 0L) {
                            // A usable pointer that led to no party in a battle: the party is elsewhere, and this value of the pointer is not to be trusted again.
                            if (baseSource == BaseSource.POINTER) distrustedBase = pointerBase
                            partyBase = found
                            scanShift = found - (ramStart + map.playerBase)
                            live = if (scanShift == 0L) map else map.shifted(scanShift, map.name, map.gameCodes)
                            baseSource = BaseSource.SCAN
                            return read()
                        }
                    }
                }
                break
            }
            // Gen 4 stores the rolled ability's own id in the mon, so decorate()
            // resolves it exactly rather than guessing a slot.
            party += decorate(mon)
        }

        val partyBaseRead = partyBase
        // Party gone (New Run, reset, or the pointer moved): re-resolve next tick.
        if (party.isEmpty()) { partyBase = 0L; readsSinceScan++ }

        val battle = readBattle()
        if (party.isEmpty() && map.absolute) {
            // The raw bytes for the bug report, so a phone that decodes nothing can
            // still hand over what it read (Blake's Black 2 battle, 2026-09-08).
            fun hex(addr: Long, n: Int) = memory.read(addr, n).joinToString("") { "%02X".format(it) }
            lastDump = buildString {
                appendLine("party @%08X: %s".format(partyBaseRead, hex(partyBaseRead, map.entrySize)))
                appendLine("probe: " + (probe ?: "-"))
                appendLine("base: %s, pointer %s, scans %d".format(baseSource, pointerBase?.let { "names %06X".format(it) } ?: "not usable", scans))
                appendLine("header @023FFE00: %s  header @027FFE00: %s".format(hex(0x023FFE00L, 16), hex(0x027FFE00L, 16)))
                appendLine("ram @02000000: %s  ram @02200000: %s".format(hex(0x02000000L, 16), hex(0x02200000L, 16)))
                appendLine("enemy @%08X: %s".format(ramStart + live.enemyBase, hex(ramStart + live.enemyBase, map.entrySize)))
                appendLine("trainerId @%08X: %s  battleStatus @%08X: %s".format(ramStart + live.enemyTrainerId, hex(ramStart + live.enemyTrainerId, 4), ramStart + battleStatusGlobal, hex(ramStart + battleStatusGlobal, 4)))
                appendLine("totalMonsParty @%08X: %s".format(ramStart + live.totalMonsParty, hex(ramStart + live.totalMonsParty, 4)))
            }
        }
        if (battle != null && battle.first == null && map.absolute) {
            val eb = memory.read(ramStart + live.enemyBase, map.entrySize)
            probe = (probe ?: "") + " | enemy @%08X: ".format(ramStart + live.enemyBase) + Gen4.probe(eb, map.generation == 5)
        }
        val lead = party.firstOrNull()
        val battleRel = if (map.absolute) 0L else battle?.third ?: 0L
        val fetchedNow = battle != null && battleFetched
        val playerActive = if (fetchedNow) runCatching { readPlayerActive(battleRel) }.getOrNull() else null
        // Your side in battle is the Pokemon on the field (the reference's playerPokemon,
        // BattleHandlerBase.getActivePokemonInBattle): its party entry carries your live
        // stages (BattleHandlerGen4._updateStatStages, BattleHandlerGen5._readBattleStats)
        // and is the one ability triggers are matched against (getAllPokemonInBattle). The
        // lead stands in when that Pokemon is not found. Both used to be the lead, whichever
        // Pokemon was out.
        var onField = lead
        if (battle != null && lead != null && battle.third != 0L) {
            val i = party.indexOfFirst { it.mon.pid == playerActive?.mon?.pid }.coerceAtLeast(0)
            party[i] = party[i].copy(statStages =
                if (map.absolute) ptr(ramStart + live.mainBattleDataPtr)
                    ?.let { readStatStagesGen5(it + 0xFC) } ?: emptyMap()
                else readStatStages(battle.third, isEnemy = false))
            onField = party[i]
        }
        val revealed = when {
            battle == null || battle.third == 0L -> { lastAbilityTrigger.fill(-1L); null }
            map.absolute -> readAbilityTriggerGen5(onField, battle.first)
            else -> readAbilityTrigger(battle.third, onField, battle.first)
        }
        pollVersionRel = if (battle != null && !map.absolute) battle.third else 0L
        val allRevealed = if (battle == null || battle.third == 0L || map.absolute) {
            synchronized(pendingMsgs) { pendingMsgs.clear() }
            listOfNotNull(revealed)
        } else {
            val queued = synchronized(pendingMsgs) { val q = pendingMsgs.toList(); pendingMsgs.clear(); q }
            (queued.mapNotNull { resolveAbilityMsg(it, onField, battle.first) } + listOfNotNull(revealed)).distinct()
        }
        val bag = readBag(battle != null)
        runCatching { updateLocation() }
        // Program.readMemory: battleHandler:checkIfRunHasEnded() after the battle is read,
        // and only a fetched battle is checked. Gen 5 addresses are main-RAM ones.
        // BattleHandlerGen5._onPlayerSlotFainted then waits up to 480 frames for the
        // battle screen's HP byte (someBattleUIPtr + UI_HP_OFFSET) to reach 0; the
        // Black 2 battle dump reads that byte as 0 while the lead shows 8/19, so the
        // address is unconfirmed and the wait is not copied: the run ends on the 0.
        val lost = fetchedNow && runHasEnded(battleRel)
        if (fetchedNow) { lastBattlePlayer = playerActive; lastBattleEnemy = battle?.first }
        // Program.getHealingTotals measures the bag against playerPokemon's max HP: in battle
        // the Pokemon on the field, otherwise the party's first that is standing and not an
        // egg (PokemonDataReader.decryptPokemonInfo with checkingParty, lua:292-322).
        val healsFor = (if (fetchedNow) playerActive else party.firstOrNull { it.mon.curHp > 0 && !it.mon.isEgg }) ?: lead
        val heals = NdsHeals.totals(bag.first, healsFor?.mon?.maxHp ?: 0, showHp = false)
        // BattleHandlerBase._onEndOfBattle (lua:348-373), unless the run has already
        // ended: the trainer goes on the defeated list, a lab rival makes the run Past
        // Lab, and FINAL_FIGHT_ID (TrainerData.lua) sets progress WON and ends the run
        // (Program.onRunEnded: the win popup, the run logged as won). The champion
        // fight ending used to set the progress and nothing else, so no DS run was won.
        // The final opponent must also be down in the last read of the battle: the
        // reference takes any end of that battle as the win, and a state loaded or a
        // Time Machine point restored in the middle of it ends it too, which filed a
        // WON and a new best in the run history (review, 2026-09-29).
        var won = false
        if (wasInBattle && battle == null) {
            if (!runEnded) {
                if (lastTrainerId != 0) defeatedTrainers.add(lastTrainerId)
                if (lastTrainerId in map.labTrainerIds && progress < 1) progress = 1
                else if (map.finalTrainerId != 0 && lastTrainerId == map.finalTrainerId && lastBattleEnemy?.mon?.curHp == 0) { progress = 2; won = true }
            }
            faintMonIndex = -1
        }
        wasInBattle = battle != null
        if (firstPokemonId == 0) party.firstOrNull()?.let { firstPokemonId = it.mon.species }
        return NdsTrackerState(
            badgeSet = map.badgePrefix,
            partyCount = party.size,
            party = party,
            located = party.isNotEmpty(),
            inBattle = battle != null,
            isWildBattle = battle?.second ?: false,
            enemy = battle?.first,
            abilityRevealed = revealed,
            abilitiesRevealed = allRevealed,
            resolvedBase = partyBase,
            probe = probe,
            badges = readBadges(),
            repelSteps = readRepelSteps().also { repelDuration = com.ironmonone.tracker.RepelRules.duration(it, repelDuration) },
            repelDuration = repelDuration,
            healPercent = heals.first,
            healCount = heals.second,
            healingItems = bag.first,
            statusItems = bag.second,
            healsPid = healsFor?.mon?.pid ?: 0L,
            // A win is reported on the read the final battle ended, the loss causes while
            // the battle lasts; the app's latch holds either for the rest of the run.
            runOver = if (won) NdsRunOver.WON else if (lost) runOverCause(playerActive ?: lead, battle?.first) else null,
            playerActive = playerActive,
            lastBattlePlayer = lastBattlePlayer,
            lastBattleEnemy = lastBattleEnemy,
            progress = progress,
            enemyTrainerId = if (battle != null) lastTrainerId else 0,
            mapId = lastMapId,
            areaName = lastAreaName,
            leagueBeaten = readLeagueBeaten(),
            gameName = gameName,
        )
    }

    /**
     * The opponent, plus whether this is a wild battle. Returns null outside
     * battle. Wild vs trainer is the enemy trainer id being zero, which is the
     * rule the reference tracker uses (BattleHandlerBase: `_enemyTrainerID == 0`)
     * — and it matters here because fleeing is a wild-only action.
     */
    private fun readBattle(): Triple<NdsTrackedMon?, Boolean, Long>? {
        battleFetched = false
        val statusBytes = memory.read(ramStart + battleStatusGlobal, 2)
        if (statusBytes.size < 2) return null
        if (statusBytes.u16(0) !in inBattleWords) return null
        if (map.absolute) return readBattleGen5()

        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return Triple(null, false, 0L)
        battleFetched = gen4BattleFetched(versionRel)

        val trainerBytes = memory.read(ramStart + versionRel + enemyTrainerIdOffset, 2)
        val isWild = trainerBytes.size == 2 && trainerBytes.u16(0) == 0
        if (trainerBytes.size == 2) lastTrainerId = trainerBytes.u16(0)

        // The reference matches the active battle PID into the enemy party
        // (BattleHandlerGen4) rather than trusting slot 0, which is only the
        // lead. Fall back to slot 0 when the PID is unreadable or unmatched.
        val pidBytes = memory.read(ramStart + versionRel + enemyBattleMonPidOffset, 4)
        val activePid = if (pidBytes.size == 4) pidBytes.u32(0) else 0L
        var mon: NdsTrackedMon? = null
        if (activePid != 0L) {
            for (slot in 0 until 6) {
                val b = memory.read(
                    ramStart + versionRel + enemyBaseOffset +
                        slot.toLong() * Gen4.PARTY_ENTRY_SIZE,
                    Gen4.PARTY_ENTRY_SIZE)
                if (b.size < Gen4.PARTY_ENTRY_SIZE) break
                val d = Gen4.decodeParty(b) ?: continue
                if (d.pid == activePid) { mon = decorate(enemyUsedOnly(d)); break }
            }
        }
        if (mon == null) {
            val enemyBytes = memory.read(
                ramStart + versionRel + enemyBaseOffset, Gen4.PARTY_ENTRY_SIZE)
            mon = if (enemyBytes.size < Gen4.PARTY_ENTRY_SIZE) null
            else Gen4.decodeParty(enemyBytes)?.let { decorate(enemyUsedOnly(it)) }
        }
        // The enemy on the field carries live stage data and status.
        mon = mon?.let {
            it.copy(statStages = readStatStages(versionRel, isEnemy = true))
        }
        return Triple(mon, isWild, versionRel)
    }

    /**
     * BattleHandlerGen4._tryToFetchBattleData (BattleHandlerGen4.lua:93-112): the
     * battle's copy of your party starts with your lead's PID, the enemy party has
     * a Pokemon, and each side has a battler on the field (a PID at its battle PID
     * address, _readBattlePIDInfo). The reference tries 60 frames after the battle
     * flag and, on a failure, not again that battle; here every read tries.
     */
    private fun gen4BattleFetched(versionRel: Long): Boolean {
        if (live.playerBattleBase == 0L) return false
        val first = u32(ramStart + versionRel + live.playerBattleBase)
        if (first == 0L || u32(ramStart + versionRel + enemyBaseOffset) == 0L) return false
        if (first != u32(ramStart + versionRel + playerBaseOffset)) return false
        return u32(ramStart + versionRel + playerBattleMonPidOffset) != 0L &&
            u32(ramStart + versionRel + enemyBattleMonPidOffset) != 0L
    }

    /**
     * The 8 battle stat-stage bytes: HP, ATK, DEF, SPE, then SPA, SPD, ACC,
     * EVA, 6 = neutral. The reference's sanity rule is ported verbatim: any
     * value outside 0..12, or a sum under 3, means the block is not really
     * stage data yet, and everything reads as neutral.
     */

    /** A pointer read out of RAM, or null when it does not point into main RAM. */
    private fun ptr(addr: Long): Long? {
        val v = u32(addr)
        return if (v in ramStart until ramEnd) v else null
    }

    /**
     * Gen 5 battle read: BattleHandlerGen5._tryToFetchBattleData,
     * _getPokemonData and _readBattleStats, for singles.
     *
     * No pointer chain and no enemy-party scan. Battler records sit 0x1C apart
     * from mainBattleDataPtr - player at +0, opponent at +0x1C - and each
     * begins with a pointer to that battler's battle data, whose first word
     * points at the Pokemon struct itself (220 bytes, decoded as any party
     * entry). The live values - HP, status, moves with PP, stat stages - sit
     * at fixed offsets beside it (BATTLE_STAT_OFFSETS). A non-zero pointer at
     * +4 is an Illusion: the mon being shown instead of the real one, which is
     * what the tracker should show too.
     *
     * The fetch is trusted only when the battle-side PID equals the party
     * lead's and the enemy side has a PID at all, exactly the reference's guard.
     */
    private fun readBattleGen5(): Triple<NdsTrackedMon?, Boolean, Long>? {
        val leadPid = u32(ramStart + live.playerBase)
        val battlePid = u32(ramStart + live.playerBattleBase)
        val enemyPid = u32(ramStart + live.enemyBase)
        if (battlePid == 0L || enemyPid == 0L || battlePid != leadPid) return Triple(null, false, 0L)
        // _readAmountOfBattlers (lua:60-69): 2 is singles, doubles or triples, 3 to 6 a
        // multi battle; any other count refuses the fetch (lua:127-142).
        var battlers = 0
        while (battlers < 7 && u32(ramStart + live.mainBattleDataPtr + 0x18 + 0x1CL * battlers) != 0L) battlers++
        battleFetched = battlers in 2..6
        val trainer = memory.read(ramStart + live.enemyTrainerId, 2)
        val isWild = trainer.size == 2 && trainer.u16(0) == 0
        if (trainer.size == 2) lastTrainerId = trainer.u16(0)

        val battleDataBase = ptr(ramStart + live.mainBattleDataPtr + 0x1C)
            ?: return Triple(null, isWild, 0L)
        var pokemonDataBase = ptr(battleDataBase) ?: return Triple(null, isWild, 0L)
        ptr(battleDataBase + 4)?.let { pokemonDataBase = it }
        val bytes = memory.read(pokemonDataBase, map.entrySize)
        if (bytes.size < map.entrySize) return Triple(null, isWild, 0L)
        val d = Gen4.decodeParty(bytes, gen5 = true) ?: return Triple(null, isWild, 0L)

        fun u16At(off: Long, fallback: Int) =
            memory.read(battleDataBase + off, 2).let { if (it.size == 2) it.u16(0) else fallback }
        val curHp = u16At(0x10, d.curHp)
        val maxHp = u16At(0x0E, d.maxHp)
        val status = memory.read(battleDataBase + 0x20, 20).let { if (it.size == 20) gen5StatusBits(it) else d.status }
        val moves = List(4) { i -> u16At(0x104 + i * 14L, 0) }
        val pp = List(4) { i ->
            memory.read(battleDataBase + 0x104 + i * 14L + 2, 1).let { if (it.size == 1) it.u8(0) else 0 }
        }
        val live = d.copy(curHp = curHp, maxHp = maxHp, status = status, moves = moves, pp = pp)
        val mon = decorate(enemyUsedOnly(live)).copy(statStages = readStatStagesGen5(battleDataBase + 0xFC))
        return Triple(mon, isWild, battleDataBase)
    }

    /**
     * Gen 5 stage bytes (PokemonDataReader.readBattleStatStages, GEN == 5,
     * PokemonDataReader.lua:345-383): ATK, DEF, SPA, SPD, then SPE, ACC, EVA,
     * and no HP. The reference's sanity rule is the Gen 4 one: a value outside
     * 0..12, or a sum under 3, means the block is not stage data yet, and
     * EVERY stage reads 6. This used to skip ACC and EVA and hand back nothing
     * at all for a bad block (parity audit, 2026-09-28).
     */
    private fun readStatStagesGen5(base: Long): Map<String, Int> {
        val b = memory.read(base, 8)
        if (b.size < 8) return emptyMap()
        val names = listOf("ATK", "DEF", "SPA", "SPD", "SPE", "ACC", "EVA")
        val stages = names.mapIndexed { i, n -> n to b.u8(i) }.toMap()
        val ok = stages.values.all { it in 0..12 } && stages.values.sum() >= 3
        return if (ok) stages else names.associateWith { 6 }
    }

    private fun readStatStages(versionRel: Long, isEnemy: Boolean): Map<String, Int> {
        val base = versionRel + if (isEnemy) statStagesEnemyOffset else statStagesPlayerOffset
        val b = memory.read(ramStart + base, 8)
        if (b.size < 8) return emptyMap()
        val names = listOf("HP", "ATK", "DEF", "SPE", "SPA", "SPD", "ACC", "EVA")
        val stages = names.mapIndexed { i, n -> n to (b[i].toInt() and 0xFF) }.toMap()
        val ok = stages.values.all { it in 0..12 } && stages.values.sum() >= 3
        return if (ok) stages else names.associateWith { 6 }
    }

    /**
     * The Gen 4 battle-subscript message table, from the reference's
     * BATTLE_MSGS_MASTER_LIST.GEN4 - including its deliberate omissions
     * (Poison Point, Effect Spore, Cute Charm and the message-221 group are
     * commented out THERE as buggy, so they are absent HERE).
     */
    private val battleMsgAbilities: Map<Int, Set<Int>> = mapOf(
        12 to setOf(3, 88),          // Speed Boost, Download
        25 to setOf(49),             // Flame Body
        31 to setOf(9),              // Static
        177 to setOf(104),           // Mold Breaker
        178 to setOf(10, 11, 87),    // Volt Absorb, Water Absorb, Dry Skin
        179 to setOf(18),            // Flash Fire
        180 to setOf(31, 114),       // Lightningrod, Storm Drain
        181 to setOf(43),            // Soundproof
        182 to setOf(78),            // Motor Drive
        183 to setOf(2),             // Drizzle
        184 to setOf(45),            // Sand Stream
        185 to setOf(70),            // Drought
        186 to setOf(22),            // Intimidate
        187 to setOf(36),            // Trace
        188 to setOf(16),            // Color Change
        189 to setOf(24),            // Rough Skin
        190 to setOf(61),            // Shed Skin
        191 to setOf(54),            // Truant
        192 to setOf(44, 87),        // Rain Dish, Dry Skin
        193 to setOf(106),           // Aftermath
        194 to setOf(107),           // Anticipation
        195 to setOf(108),           // Forewarn
        196 to setOf(112),           // Slow Start
        252 to setOf(117),           // Snow Warning
        253 to setOf(119),           // Frisk
        285 to setOf(46),            // Pressure
    )

    /**
     * Whether the current battle message reveals an ability, and whose.
     *
     * The reference checks this every 8 frames; here it rides the normal
     * poll. If BOTH mons on the field could have produced the message, it
     * reveals nothing - there is no reliable way to know the source, and
     * the reference makes the same refusal.
     */
    private fun readAbilityTrigger(
        versionRel: Long,
        player: NdsTrackedMon?,
        enemy: NdsTrackedMon?,
    ): Pair<Int, String>? {
        val msgBytes = memory.read(ramStart + versionRel + battleSubscriptMsgsOffset, 2)
        if (msgBytes.size < 2) return null
        return resolveAbilityMsg(msgBytes.u16(0), player, enemy)
    }

    /** The rest of [readAbilityTrigger], for a message already read. */
    internal fun resolveAbilityMsg(
        msg: Int,
        player: NdsTrackedMon?,
        enemy: NdsTrackedMon?,
    ): Pair<Int, String>? {
        val candidates = battleMsgAbilities[msg] ?: return null
        val onField = listOfNotNull(player, enemy)
        val sources = onField.filter { it.mon.abilityId in candidates }
        if (sources.size != 1) return null
        val src = sources[0]
        // Speed Boost only counts once SPE actually rose past neutral.
        if (src.mon.abilityId == 3 && (src.statStages["SPE"] ?: 6) <= 6) return null
        var out = src.mon.species to src.abilityName
        // Trace on the player's side in 1v1 also reveals the enemy's ability.
        if (src.mon.abilityId == 36 && src === player && enemy != null) {
            out = enemy.mon.species to enemy.abilityName
        }
        return out
    }

    /**
     * Gen 5 ability reveals: BattleHandlerGen5._checkBattlerAbilityTriggered.
     *
     * Each battler slot has a u16 at abilityTriggerStart + 8 * index that the
     * game writes with the ability that just activated; in singles the
     * player's slot is +0 and the opponent's +4 (_tryToFetchBattleData's
     * two _readBattleDataPtr calls). The reference polls every 30 frames,
     * remembers the last word per slot, and acts only on a change to a
     * non-zero word: if it equals that battler's own ability, that ability
     * is tracked for that species; if the player's mon has Trace (36) and
     * the word is something else, it is the traced enemy's ability, tracked
     * for the enemy when exactly one enemy carries it. No Speed Boost rule
     * here; that is the Gen 4 subscript path's.
     *
     * The opponent's slot is read first and, when it reveals, the player's
     * slot is left for the next tick rather than dropped: one reveal per
     * state, none lost.
     */
    private fun readAbilityTriggerGen5(player: NdsTrackedMon?, enemy: NdsTrackedMon?): Pair<Int, String>? {
        if (live.abilityTriggerStart == 0L) return null
        fun slot(index: Int, mon: NdsTrackedMon?, isEnemy: Boolean): Pair<Int, String>? {
            val b = memory.read(ramStart + live.abilityTriggerStart + 4L * index, 2)
            if (b.size < 2) return null
            val word = b.u16(0).toLong()
            if (word == lastAbilityTrigger[index] || word == 0L) return null
            lastAbilityTrigger[index] = word
            if (mon == null) return null
            if (word == mon.mon.abilityId.toLong()) return mon.mon.species to mon.abilityName
            if (!isEnemy && mon.mon.abilityId == 36 && enemy != null && enemy.mon.abilityId.toLong() == word) {
                return enemy.mon.species to enemy.abilityName
            }
            return null
        }
        return slot(1, enemy, isEnemy = true) ?: slot(0, player, isEnemy = false)
    }

    /** Wrap a decoded mon with the names and randomized data the panel shows. */
    /**
     * Gen 4 status word (party +0x00): 1-7 = sleep turns, bit 3 poison,
     * bit 4 burn, bit 5 freeze, bit 6 paralysis, bit 7 bad poison.
     */
    private fun statusName(status: Long): String = when {
        status and 0x07L != 0L -> "SLP"
        status and 0x08L != 0L -> "PSN"
        status and 0x10L != 0L -> "BRN"
        status and 0x20L != 0L -> "FRZ"
        status and 0x40L != 0L -> "PAR"
        status and 0x80L != 0L -> "PSN"
        else -> ""
    }

    /**
     * The NDS reference shows an ENEMY only the moves it has used: a move is
     * tracked when its current PP is below the move's base PP
     * (BattleHandlerBase.lua:265, `currentPP < maxPP` then trackMove). Slots
     * whose move is unknown to the table are kept, since they cannot be judged.
     * Slots, PP and PP Ups stay aligned so the card's columns line up.
     *
     * Every move in gen4/moves.tsv and gen5/moves.tsv has a base PP (Gen5MovesTest
     * holds both tables to it), so the keep-branch only ever sees an id outside
     * the table. It used to see 21 real Gen 5 moves, which the old extraction
     * wrote with PP 0, and showed them before the opponent had used them.
     */
    internal fun usedOnly(mon: Gen4.Mon, basePp: (Int) -> Int): Gen4.Mon {
        val keep = mon.moves.indices.filter { i ->
            val id = mon.moves[i]; val base = basePp(id)
            id != 0 && (base <= 0 || mon.pp.getOrElse(i) { 0 } < base)
        }
        fun <T> pick(l: List<T>, pad: T) = List(4) { k -> keep.getOrNull(k)?.let { l.getOrElse(it) { pad } } ?: pad }
        return mon.copy(moves = pick(mon.moves, 0), pp = pick(mon.pp, 0), ppUps = pick(mon.ppUps, 0))
    }

    private fun enemyUsedOnly(mon: Gen4.Mon): Gen4.Mon = usedOnly(mon) { moveInfo[it]?.pp ?: 0 }

    private fun decorate(mon: Gen4.Mon): NdsTrackedMon {
        val info = speciesInfo[mon.species]
        val levels = moveLevelsOf(mon.species)
        return NdsTrackedMon(
            mon = mon,
            statusCondition = statusName(mon.status),
            speciesName = speciesName(mon.species),
            info = info,
            abilityName = abilityNames[mon.abilityId] ?: "-",
            // An id GEN_5_ITEMS lacks has no name there (heldItemInfo nil -> ""), never "#id".
            itemName = if (mon.heldItem == 0) "-"
            else itemNames[mon.heldItem] ?: "",
            moves = mon.moves.filter { it != 0 }.map {
                moveInfo[it] ?: NdsMoveInfo("#$it", 0, 0, "")
            },
            moveLevels = levels,
            movesLearned = levels.count { it <= mon.level },
            movesTotal = levels.size,
            nextMoveLevel = levels.firstOrNull { it > mon.level },
        )
    }
}
