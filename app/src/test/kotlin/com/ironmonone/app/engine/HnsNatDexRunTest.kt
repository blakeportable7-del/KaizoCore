package com.ironmonone.app.engine

import com.ironmonone.app.Dumps
import com.ironmonone.app.LogNames
import com.ironmonone.app.LogPictureIds
import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.ShippedPals
import com.ironmonone.app.WalkingPals
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Nat. Dex Heart & Soul Kaizo IronMON, end to end (Blake, 2026-10-06: a streamer wants to play it, "so the Natl Dex needs
 * tested by our tracker"). Three seeds of the Emerald Nat. Dex v1.2 Kaizo mode on the Nat. Dex pool go through the app's
 * own randomizing path (HnsEveryModeTest.make), then through the tracker over the run's ROM and through the log viewer's
 * lookups over the run's log:
 *
 * - every species the run places (starters, trainers, wild, trades, scripts, gift tables): the tracker names it, has its
 *   stats, its picture and its pack picture, and says it evolves wherever the run's ROM says it does;
 * - the log: every Pokemon page, trainer and route the log lists is found by the log viewer's names (LogNames) as the
 *   species the log means, with the run's own stats and types, its picture and its Walking Pal;
 * - the encounter and trainer views: every wild area's list (the Kaizo cycle's), every trainer of every map.
 *
 * Every check runs and the test fails with the whole list; lines "HNSRUN ..." go to the output and
 * build/hns-natdex-run/ for the report. Without the comfort build the test returns (IRONMON_REQUIRE_DUMPS fails it).
 */
class HnsNatDexRunTest {
    private val seeds = listOf(20261006L, 4242L, 777001L)
    private val settings = File(HnsEveryModeTest.presets, "RSE NatDex v1.2 Kaizo.rnqs")

    /** The run's ROM, with empty RAM: what the log viewer and the info screens read. */
    private class RomMem(val rom: ByteArray) : MemoryReader {
        private val ewram = ByteArray(0x40000)
        override fun read(address: Long, length: Int): ByteArray = when (address) {
            in 0x08000000L..0x09FFFFFFL -> {
                val o = (address - 0x08000000L).toInt()
                if (o >= rom.size) ByteArray(0) else rom.copyOfRange(o, minOf(rom.size, o + length))
            }
            in 0x02000000L..0x0203FFFFL -> {
                val o = (address - 0x02000000L).toInt()
                ewram.copyOfRange(o, minOf(ewram.size, o + length))
            }
            else -> ByteArray(0)
        }
    }

    private fun report(name: String, lines: List<String>) {
        val dir = File("build/hns-natdex-run").also { it.mkdirs() }
        File(dir, "$name.txt").writeText(lines.joinToString("\n") + "\n")
        println("HNSRUN $name: ${lines.size} line(s)")
        lines.take(300).forEach { println("HNSRUN $name | $it") }
    }

    @Test
    fun `three Nat Dex Kaizo seeds through the tracker and the log viewer`() {
        if (HnsEveryModeTest.romFile == null) {
            if (Dumps.required) fail("IRONMON_REQUIRE_DUMPS: hns-kaizo.gba is missing")
            println("HnsNatDexRunTest skipped: no hns-kaizo.gba"); return
        }
        val oldAssets = HnsEngine.assetText
        HnsEngine.assetText = HnsEngine.folderAssets(File("src/main/assets"))
        try { runSeeds() } finally { HnsEngine.assetText = oldAssets }
    }

    private fun runSeeds() {
        val bad = ArrayList<String>()
        val evoMissing = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        fun count(k: String, n: Int = 1) { counts[k] = (counts[k] ?: 0) + n }
        val pals = ShippedPals.index
        for (seed in seeds) {
            val made = HnsEveryModeTest.make(settings, HnsEngine.Pool.NATDEX, seed)
            val game = HnsEveryModeTest.read(made.bytes)
            val log = RandomizerLog.parse(made.logText)
            val mem = RomMem(made.bytes)
            val t = GbaTracker(mem, GameMap.resolve(mem))
            val tag = "seed $seed"
            if (!t.heartSoul) { bad += "$tag: the tracker did not take the run's ROM as Heart & Soul"; continue }

            // ---- every species the run places
            val placed = HnsEveryModeTest.placed(game)
            val all = placed.values.flatten().filter { it > 0 }.toSortedSet()
            val gen49 = all.count { (game.mons[it]?.natDex ?: 0) > 386 }
            count("placed species (distinct, over the seeds)", all.size); count("placed species of Gen 4-9 and forms", gen49)
            for (s in all) {
                val m = game.mons.getOrNull(s)
                if (m == null || !m.enabled) { bad += "$tag: places species $s, which the build does not have"; continue }
                val n = t.speciesName(s)
                if (n.isBlank() || n.startsWith("#") || "?" in n || n.replace('’', '\'') != m.name.replace('’', '\'')) bad += "$tag $s ${m.const}: tracker name '$n', ROM '${m.name}'"
                val b = t.baseStats(s)
                if (b == null) { bad += "$tag $s ${m.const}: no base stats"; continue }
                if (listOf(b.hp, b.atk, b.def, b.spe, b.spAtk, b.spDef) != m.stats.toList()) bad += "$tag $s ${m.const}: stats ${listOf(b.hp, b.atk, b.def, b.spe, b.spAtk, b.spDef)}, the run's ${m.stats.toList()}"
                val types = m.types.map { if (it in 1..19) it - 1 else 9 }
                if (listOf(b.type1, b.type2) != types) bad += "$tag $s ${m.const}: types ${b.type1}/${b.type2}, the run's $types"
                val ab = m.abilities.filter { it != 0 }.distinct().map { game.abilityNames[it] }
                if (t.possibleAbilities(s).map { it.uppercase() } != ab.map { it.uppercase() }) bad += "$tag $s ${m.const}: abilities ${t.possibleAbilities(s)}, the run's $ab"
                if (t.frontPicture(s) == null) bad += "$tag $s ${m.const}: no picture"
                val pack = t.packSpriteId(s)
                if (pack == null) bad += "$tag $s ${m.const}: no pack picture" else if (pals.find(s, WalkingPals.Dex.HNS) == null) count("placed species with no Walking Pal")
                if (t.learnset(s).size != m.learnset.size) bad += "$tag $s ${m.const}: learnset ${t.learnset(s).size}, the run's ${m.learnset.size}"
                val evos = m.evos.filter { e -> game.mons.getOrNull(e.target)?.enabled == true }
                val text = t.evolution(s)
                if (evos.isNotEmpty() && text == null) evoMissing += "$tag $s ${m.const}: evolves (${evos.joinToString { "method ${it.method} param ${it.param} -> ${game.speciesName(it.target)}" }}), the card shows nothing"
                if (evos.isEmpty() && text != null) bad += "$tag $s ${m.const}: does not evolve in the run, the card says '$text'"
                count("species checked")
            }

            // ---- the log viewer
            val names = LogNames.of(t)
            val byDisplay = game.mons.filterNotNull().filter { it.enabled }.associateBy { it.displayName.uppercase() }
            for (p in log.pokemon) {
                count("log Pokemon pages")
                val want = byDisplay[p.name.uppercase()]
                val id = names.speciesId(p.name)
                if (want == null) { bad += "$tag log: '${p.name}' is no species of the run"; continue }
                if (id != want.id) { bad += "$tag log: '${p.name}' is found as ${id?.let { "$it ${game.mons[it]?.const}" }}, the log means ${want.id} ${want.const}"; continue }
                if (p.stats.sum() != t.baseStats(id)?.bst) bad += "$tag log: '${p.name}' BST ${p.stats.sum()}, the tracker ${t.baseStats(id)?.bst}"
                if (t.frontPicture(id) == null) bad += "$tag log: '${p.name}' has no picture"
                if (LogPictureIds.species(p, names, gameBoy = false) == null) bad += "$tag log: '${p.name}' has no pack picture"
            }
            val logNames = log.trainers.flatMap { tr -> tr.party.map { it.name } } + log.routes.flatMap { r -> r.encounters.map { it.name } } + log.starters
            for (n in logNames.distinct()) {
                count("log trainer, route and starter names")
                val want = byDisplay[n.uppercase()]
                val id = names.speciesId(n)
                if (want == null || id != want.id) bad += "$tag log: '$n' (a trainer's, a route's or a starter) is found as $id, the log means ${want?.id}"
            }
            for (tm in log.tms) {
                count("log TMs")
                if (game.moves.none { it?.name.equals(tm.move, true) }) bad += "$tag log: TM${tm.number} '${tm.move}' is no move of the run"
            }

            // ---- the tracker's encounter views: every wild area (the Kaizo cycle's list), every map's trainers
            val starters = t.starters()
            if (starters.map { it.name.uppercase() } != log.starters.map { n -> byDisplay[n.uppercase()]?.name?.uppercase() }) bad += "$tag: Elm's balls ${starters.map { it.name }}, the log ${log.starters}"
            val wildSpecies = HashSet<Int>()
            for ((mapId, mapName) in t.routeLookupList()) {
                val areas = t.routeEncounters(mapId)
                if (mapName.isBlank() || "?" in mapName) bad += "$tag: map $mapId is named '$mapName'"
                for ((area, mons) in areas) {
                    count("wild areas listed")
                    if (mons.isEmpty()) bad += "$tag: $mapName $area lists nothing"
                    for (rm in mons) {
                        count("wild entries")
                        wildSpecies += rm.id
                        val n = t.speciesName(rm.id)
                        if (!t.speciesExists(rm.id) || n.isBlank() || n.startsWith("#") || "?" in n) bad += "$tag: $mapName $area lists species ${rm.id} '$n'"
                        if (rm.minLv !in 1..100 || rm.maxLv !in rm.minLv..100) bad += "$tag: $mapName $area ${n}: levels ${rm.minLv}-${rm.maxLv}"
                    }
                }
            }
            // The game reads a map's first wild header only (GetCurrentMapWildMonHeaderId; Altering Cave's sets are not in
            // this build): Mt. Silver's snowfield has two more, randomized and never met, so they are left out here.
            val hs = game.L.struct("WildPokemonHeader")
            val firstHeaders = HashMap<Int, Int>()
            for (i in 0 until game.L.tables.getValue("gWildMonHeaders").count) {
                val h = game.L.rec("gWildMonHeaders", i)
                val g = game.rom.get(hs.f("mapGroup"), h)
                if (g == 0xFF) break
                firstHeaders.putIfAbsent((g shl 8) or game.rom.get(hs.f("mapNum"), h), i)
            }
            val reachable = firstHeaders.values.toSet()
            val gameWild = game.wildSets.filter { it.areaKey.substringBefore('|').toInt() in reachable }
                .flatMap { s -> s.slots.map { it.species } }.filter { it != 0 }.toSet()
            if (wildSpecies != gameWild) bad += "$tag: the routes list ${wildSpecies.size} species, the run's wild tables ${gameWild.size} (" +
                "missing ${(gameWild - wildSpecies).take(10)}, extra ${(wildSpecies - gameWild).take(10)})"
            val trainerIds = game.trainers.map { it.id }.toSet()
            for (tr in game.trainers) {
                val info = t.trainer(tr.id)
                if (info == null) { if (tr.mons.isNotEmpty()) bad += "$tag: trainer ${tr.id} ${tr.constName} has no Trainer Info"; continue }
                count("trainers")
                if (tr.poolSize == 0 && info.party.map { it.species } != tr.mons.map { it.species }) bad += "$tag: trainer ${tr.id} ${tr.constName}: party ${info.party.map { it.species }}, the run's ${tr.mons.map { it.species }}"
                for (pm in info.party) {
                    count("trainer Pokemon")
                    val n = t.speciesName(pm.species)
                    if (!t.speciesExists(pm.species) || n.startsWith("#") || n.isBlank() || "?" in n) bad += "$tag: trainer ${tr.id} has species ${pm.species} '$n'"
                    if (pm.level !in 1..100) bad += "$tag: trainer ${tr.id} ${n} at Lv.${pm.level}"
                    if (t.frontPicture(pm.species) == null) bad += "$tag: trainer ${tr.id} ${n} has no picture"
                    for (mv in pm.moves.filter { it != 0 }) if (t.moveName(mv).startsWith("#")) bad += "$tag: trainer ${tr.id} ${n} knows move $mv '${t.moveName(mv)}'"
                    if (pm.heldItem != 0 && t.itemName(pm.heldItem).startsWith("#")) bad += "$tag: trainer ${tr.id} ${n} holds item ${pm.heldItem}"
                }
            }
            for ((mapId, _) in t.routeLookupList()) for (id in t.trainersOnRoute(mapId)) {
                if (id !in trainerIds) bad += "$tag: map $mapId lists trainer $id, which the run does not have"
            }
        }
        for ((k, v) in counts) println("HNSRUN count | $k: $v")
        report("bad", bad); report("evo-missing", evoMissing)
        bad += evoMissing
        report("counts", counts.map { "${it.key}: ${it.value}" })
        assertTrue((counts["species checked"] ?: 0) > 1000, "species checked: ${counts["species checked"]}")
        assertEquals(emptyList(), bad)
    }
}
