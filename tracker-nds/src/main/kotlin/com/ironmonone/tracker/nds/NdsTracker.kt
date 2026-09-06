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
    /** Gym badges as 8 bits, badge 1 in bit 0. */
    val badges: Int = 0,
    /** Which badge art to draw: the map's BADGE_PREFIX (DPPT, HGSS). */
    val badgeSet: String = "DPPT",
    /** Carried healing as a share of the lead's max HP, and the item count. */
    val healPercent: Int = 0,
    val healCount: Int = 0,
    /** null while the run is alive; otherwise how it ended. */
    val runOver: NdsRunOver? = null,
    /**
     * Ability revealed by a battle trigger this tick: species to ability
     * name. The panel persists these per run; the reference's TrackAbility.
     */
    val abilityRevealed: Pair<Int, String>? = null,
) : com.ironmonone.tracker.RunView {
    override val enemySpeciesId: Int get() = enemy?.mon?.species ?: -1
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
    private val ramStart = 0x02000000L
    private val ramEnd = 0x02400000L
    private val chunk = 0x20000

    // Offsets come from the game's NdsGameMap (copied from NDS-Ironmon-Tracker's
    // MemoryAddresses.lua); Platinum unless detect() said otherwise.
    private val globalPointer = NdsGameMap.GLOBAL_POINTER
    private val versionPointerOffset = NdsGameMap.VERSION_POINTER_OFFSET
    private val playerBaseOffset = map.playerBase
    private val enemyBaseOffset = map.enemyBase
    private val enemyTrainerIdOffset = map.enemyTrainerId
    /** PID of the enemy Pokemon actually on the field (VERSION_POINTER_OFFSETS
     *  enemyBattleMonPID). Slot 0 of the enemy party is only the LEAD; after a
     *  trainer switches, the mon fighting you is whichever party slot carries
     *  this PID. Without the match the panel kept showing the first Pokemon
     *  for the whole battle. */
    private val enemyBattleMonPidOffset = map.enemyBattleMonPid
    /** VERSION_POINTER_OFFSETS.playerBattleMonPID / statStages / subscript. */
    private val playerBattleMonPidOffset = map.playerBattleMonPid
    private val statStagesPlayerOffset = map.statStagesPlayer
    private val statStagesEnemyOffset = map.statStagesEnemy
    private val battleSubscriptMsgsOffset = map.battleSubscriptMsgs
    /** GLOBAL (not pointer-relative) in that project's table. */
    private val battleStatusGlobal = map.battleStatus
    // Bag pockets, Platinum. Items and berries are separate lists, and both
    // move to a different address while a battle is running.
    private val itemStartNoBattle = map.itemStartNoBattle
    private val itemStartBattle = map.itemStartBattle
    private val berryBagStart = map.berryBagStart
    private val berryBagStartBattle = map.berryBagStartBattle

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
     * table (PokemonData.POKEMON_MASTER_LIST movelvls, version group 2 for
     * Platinum) and so does this.
     */
    private val moveLevels = HashMap<Int, List<Int>>()

    private var partyBase = 0L
    /**
     * Gen 5: the last ability-trigger word seen per battler slot (player,
     * opponent), BattleHandlerGen5's lastAbilityValue. A reveal fires once,
     * when the word changes to something non-zero; -1 until a battle is up.
     */
    private val lastAbilityTrigger = longArrayOf(-1L, -1L)

    init {
        loadPairs("/${map.dataDir}/species.tsv", speciesNames)
        loadPairs("/${map.dataDir}/abilities.tsv", abilityNames)
        loadPairs("/${map.dataDir}/items.tsv", itemNames)
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
                            name = p[1],
                            power = p[2].toIntOrNull() ?: 0,
                            accuracy = p[3].toIntOrNull() ?: 0,
                            type = p[4],
                            pp = p.getOrNull(5)?.toIntOrNull() ?: 0,
                            category = p.getOrNull(6) ?: "",
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

    fun speciesName(id: Int): String =
        speciesInfo[id]?.name ?: speciesNames[id] ?: "#$id"

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
     * Whether the run is over, and what killed it.
     *
     * The loss condition is the reference's default, ON_FIRST_SLOT_FAINT: the
     * LEAD fainting ends the run, not a full party wipe.
     *
     * The cause decides which closing line is shown, recreated from
     * SeedLogger.figureOutCause. Order matters there: a Shedinja that also
     * happens to be 100 BST below you is still reported as a Shedinja loss.
     */
    internal fun readRunOver(
        lead: NdsTrackedMon?,
        enemy: NdsTrackedMon?,
    ): NdsRunOver? {
        if (lead == null) return null
        // Level 0 means the slot has not decoded yet, not a dead Pokemon.
        if (lead.mon.curHp != 0 || lead.mon.level <= 0) return null
        val enemyName = enemy?.speciesName?.uppercase() ?: ""
        val enemyAbility = enemy?.abilityName?.uppercase() ?: ""
        val leadBst = lead.info?.bst ?: 0
        val enemyBst = enemy?.info?.bst ?: 0
        return when {
            enemyName == "SHEDINJA" -> NdsRunOver.SHEDINJA
            enemyAbility == "IMPOSTER" -> NdsRunOver.IMPOSTER
            leadBst > 0 && enemyBst > 0 && leadBst - enemyBst >= 100 ->
                NdsRunOver.ENEMY_LOWER_BST
            else -> NdsRunOver.STANDARD
        }
    }

    /**
     * Healing carried, as a share of [maxHp].
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
     * this.
     */
    internal fun readHeals(maxHp: Int, inBattle: Boolean): Pair<Int, Int> {
        if (maxHp <= 0) return 0 to 0
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return 0 to 0
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

        var total = 0.0
        var count = 0
        for (start in listOf(itemStart, berryStart)) {
            var addr = ramStart + versionRel + start
            // 400 slots is far beyond any real bag; the null terminator is what
            // normally stops this, and the bound only guards a bad pointer.
            for (i in 0 until 400) {
                val word = u32(addr)
                val id = (word and 0xFFFFL).toInt()
                if (id == 0) break
                val qty = ((word shr 16) and 0xFFFFL).toInt()
                val heal = GEN4_HEAL_ITEMS[id]
                if (heal != null && qty in 1..999) {
                    val perItem =
                        if (heal.second) maxHp * heal.first / 100.0 else heal.first
                    total += minOf(perItem, maxHp.toDouble()) * qty
                    count += qty
                }
                addr += 4
            }
        }
        val pct = ((total / maxHp) * 100).toInt().coerceIn(0, 9999)
        return pct to count
    }

    /**
     * Coverage over the Gen 4 dex, the same question the GBA screen answers:
     * the best multiplier this moveset gets against each species.
     *
     * Types come from the per-run sidecar, so this follows the randomization
     * rather than vanilla typings. With no sidecar there are no types and the
     * honest answer is an empty table, not a confident wrong one.
     */
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
    internal fun readBadges(): Int {
        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return 0
        // Platinum: one byte. HGSS: Johto then Kanto, combined so the badge
        // row keeps one shape (Johto bits 0-7, Kanto 8-15).
        var bits = 0
        map.badgeOffsets.forEachIndexed { i, off ->
            val b = memory.read(ramStart + versionRel + off, 1)
            if (b.isNotEmpty()) bits = bits or (b.u8(0) shl (8 * i))
        }
        return bits
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
     * Fallback: sweep main RAM for a valid party entry. Two-stage — a cheap
     * species-only decrypt rejects almost everything, then the checksum decides.
     */
    fun findParty(): Long {
        var addr = ramStart
        while (addr < ramEnd) {
            val len = minOf(chunk, (ramEnd - addr).toInt())
            val buf = memory.read(addr, len)
            if (buf.isEmpty()) { addr += chunk; continue }

            var i = 0
            while (i + Gen4.PARTY_ENTRY_SIZE <= buf.size) {
                if (Gen4.quickSpecies(buf, i) in 1..Gen4.MAX_SPECIES) {
                    val entry = buf.copyOfRange(i, i + Gen4.PARTY_ENTRY_SIZE)
                    val mon = Gen4.decodeParty(entry)
                    if (mon != null && !mon.isEgg) {
                        // Walk back to slot 0 so party order is right.
                        var base = addr + i
                        while (base - Gen4.PARTY_ENTRY_SIZE >= ramStart) {
                            val prev = memory.read(
                                base - Gen4.PARTY_ENTRY_SIZE, Gen4.PARTY_ENTRY_SIZE)
                            if (prev.size < Gen4.PARTY_ENTRY_SIZE) break
                            if (Gen4.decodeParty(prev) == null) break
                            base -= Gen4.PARTY_ENTRY_SIZE
                        }
                        return base
                    }
                }
                i += 4
            }
            addr += (chunk - Gen4.PARTY_ENTRY_SIZE)   // overlap, so nothing straddles
        }
        return 0
    }

    fun read(): NdsTrackerState {
        if (partyBase == 0L) {
            partyBase = if (map.absolute) ramStart + map.playerBase
                else resolvePartyViaPointers().takeIf { it != 0L } ?: findParty()
            if (partyBase == 0L) {
                return NdsTrackerState(
                    0, emptyList(), located = false, badgeSet = map.badgePrefix, resolvedBase = rawPointerChain())
            }
        }

        val party = ArrayList<NdsTrackedMon>(6)
        for (slot in 0 until 6) {
            val bytes = memory.read(
                partyBase + slot.toLong() * map.entrySize, map.entrySize)
            if (bytes.size < map.entrySize) break
            val mon = Gen4.decodeParty(bytes, gen5 = map.generation == 5) ?: break
            // Gen 4 stores the rolled ability's own id in the mon, so decorate()
            // resolves it exactly rather than guessing a slot.
            party += decorate(mon)
        }

        // Party gone (New Run, reset, or the pointer moved): re-resolve next tick.
        if (party.isEmpty()) partyBase = 0L

        val battle = readBattle()
        var lead = party.firstOrNull()
        // In battle the LEAD also carries stage data; the reference draws
        // chevrons on both sides of the screen.
        if (battle != null && lead != null && battle.third != 0L) {
            lead = lead.copy(statStages =
                if (map.absolute) ptr(ramStart + map.mainBattleDataPtr)
                    ?.let { readStatStagesGen5(it + 0xFC) } ?: emptyMap()
                else readStatStages(battle.third, isEnemy = false))
            party[0] = lead
        }
        val revealed = when {
            battle == null || battle.third == 0L -> { lastAbilityTrigger.fill(-1L); null }
            map.absolute -> readAbilityTriggerGen5(lead, battle.first)
            else -> readAbilityTrigger(battle.third, lead, battle.first)
        }
        val heals = readHeals(lead?.mon?.maxHp ?: 0, battle != null)
        return NdsTrackerState(
            badgeSet = map.badgePrefix,
            partyCount = party.size,
            party = party,
            located = party.isNotEmpty(),
            inBattle = battle != null,
            isWildBattle = battle?.second ?: false,
            enemy = battle?.first,
            abilityRevealed = revealed,
            resolvedBase = partyBase,
            badges = readBadges(),
            healPercent = heals.first,
            healCount = heals.second,
            runOver = readRunOver(lead, battle?.first),
        )
    }

    /**
     * The opponent, plus whether this is a wild battle. Returns null outside
     * battle. Wild vs trainer is the enemy trainer id being zero, which is the
     * rule the reference tracker uses (BattleHandlerBase: `_enemyTrainerID == 0`)
     * — and it matters here because fleeing is a wild-only action.
     */
    private fun readBattle(): Triple<NdsTrackedMon?, Boolean, Long>? {
        val statusBytes = memory.read(ramStart + battleStatusGlobal, 2)
        if (statusBytes.size < 2) return null
        if (statusBytes.u16(0) !in inBattleWords) return null
        if (map.absolute) return readBattleGen5()

        val versionRel = versionPointer()
        if (!map.absolute && versionRel == 0L) return Triple(null, false, 0L)

        val trainerBytes = memory.read(ramStart + versionRel + enemyTrainerIdOffset, 2)
        val isWild = trainerBytes.size == 2 && trainerBytes.u16(0) == 0

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
                if (d.pid == activePid) { mon = decorate(d); break }
            }
        }
        if (mon == null) {
            val enemyBytes = memory.read(
                ramStart + versionRel + enemyBaseOffset, Gen4.PARTY_ENTRY_SIZE)
            mon = if (enemyBytes.size < Gen4.PARTY_ENTRY_SIZE) null
            else Gen4.decodeParty(enemyBytes)?.let { decorate(it) }
        }
        // The enemy on the field carries live stage data and status.
        mon = mon?.let {
            it.copy(statStages = readStatStages(versionRel, isEnemy = true))
        }
        return Triple(mon, isWild, versionRel)
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
        val leadPid = u32(ramStart + map.playerBase)
        val battlePid = u32(ramStart + map.playerBattleBase)
        val enemyPid = u32(ramStart + map.enemyBase)
        if (battlePid == 0L || enemyPid == 0L || battlePid != leadPid) return Triple(null, false, 0L)
        val trainer = memory.read(ramStart + map.enemyTrainerId, 2)
        val isWild = trainer.size == 2 && trainer.u16(0) == 0

        val battleDataBase = ptr(ramStart + map.mainBattleDataPtr + 0x1C)
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
        val status = memory.read(battleDataBase + 0x20, 4).let { if (it.size == 4) it.u32(0) else d.status }
        val moves = List(4) { i -> u16At(0x104 + i * 14L, 0) }
        val pp = List(4) { i ->
            memory.read(battleDataBase + 0x104 + i * 14L + 2, 1).let { if (it.size == 1) it.u8(0) else 0 }
        }
        val live = d.copy(curHp = curHp, maxHp = maxHp, status = status, moves = moves, pp = pp)
        val mon = decorate(live).copy(statStages = readStatStagesGen5(battleDataBase + 0xFC))
        return Triple(mon, isWild, battleDataBase)
    }

    /**
     * Gen 5 stage bytes (PokemonDataReader.readBattleStatStages, GEN == 5):
     * ATK, DEF, SPA, SPD, then SPE, ACC, EVA - no HP. Same sanity rule as
     * Gen 4: a value outside 0..12 means this is not stage data yet.
     */
    private fun readStatStagesGen5(base: Long): Map<String, Int> {
        val b = memory.read(base, 8)
        if (b.size < 8) return emptyMap()
        val v = listOf(b.u8(0), b.u8(1), b.u8(2), b.u8(3), b.u8(4))
        if (v.any { it !in 0..12 }) return emptyMap()
        return mapOf("ATK" to v[0], "DEF" to v[1], "SPA" to v[2], "SPD" to v[3], "SPE" to v[4])
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
        val candidates = battleMsgAbilities[msgBytes.u16(0)] ?: return null
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
        if (map.abilityTriggerStart == 0L) return null
        fun slot(index: Int, mon: NdsTrackedMon?, isEnemy: Boolean): Pair<Int, String>? {
            val b = memory.read(ramStart + map.abilityTriggerStart + 4L * index, 2)
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

    private fun decorate(mon: Gen4.Mon): NdsTrackedMon {
        val info = speciesInfo[mon.species]
        val levels = moveLevelsOf(mon.species)
        return NdsTrackedMon(
            mon = mon,
            statusCondition = statusName(mon.status),
            speciesName = speciesName(mon.species),
            info = info,
            abilityName = abilityNames[mon.abilityId] ?: "-",
            itemName = if (mon.heldItem == 0) "-"
            else itemNames[mon.heldItem] ?: "#${mon.heldItem}",
            moves = mon.moves.filter { it != 0 }.map {
                moveInfo[it] ?: NdsMoveInfo("#$it", 0, 0, "")
            },
            movesLearned = levels.count { it <= mon.level },
            movesTotal = levels.size,
            nextMoveLevel = levels.firstOrNull { it > mon.level },
        )
    }
}
