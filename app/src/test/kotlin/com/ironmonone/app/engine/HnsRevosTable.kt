package com.ironmonone.app.engine

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Heart & Soul's evolution odds (the tracker's Random Evos screen, VILLAIN 2026-10-10): KaizoCore's own randomizer run
 * over many seeds of the Kaizo preset on each pool, counting what each species' evolution became. Written to
 * tracker-gba/src/main/resources/hns/revos-<pool>.tsv in revos.tsv's form (base, the original target or 0 when there is
 * one evolution, then target:percent), with a "# seeds N" line the screen names. Runs only when asked:
 *
 *     IRONMON_HNS_REVOS=300 gradlew :app:testDebugUnitTest --tests '*HnsRevosTable*'
 *
 * Otherwise it checks the tables shipped are well formed.
 */
class HnsRevosTable {
    private val out = File("../tracker-gba/src/main/resources/hns")

    private fun reader(rom: ByteArray) = MemoryReader { address, length ->
        val off = (address - 0x08000000L).toInt()
        if (address >= 0x08000000L && off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
    }

    @Test
    fun `the evolution odds, made or checked`() {
        val seeds = System.getenv("IRONMON_HNS_REVOS")?.toIntOrNull()
        if (seeds == null) {
            for (pool in listOf("natdex", "vanilla")) {
                val f = File(out, "revos-$pool.tsv")
                if (!f.isFile) continue
                val lines = f.readLines().filter { !it.startsWith("#") && it.isNotBlank() }
                assertTrue(lines.size > 100, "$f has ${lines.size} species")
                for (l in lines) {
                    val p = l.split('\t')
                    val sum = p[2].split(',').sumOf { it.substringAfter(':').toDouble() }
                    assertTrue(sum in 99.0..101.0, "$f: $l adds to $sum")
                }
            }
            return
        }
        val rom = HnsEngineTest.rom ?: error("no app build ROM")
        val base = GbaTracker(reader(rom), GameMap.resolve(reader(rom)))
        val ids = (1 until 1600).filter { base.hnsEvoTargets(it).isNotEmpty() }
        val original = ids.associateWith { base.hnsEvoTargets(it) }
        for (pool in HnsEngine.Pool.entries) {
            // base -> original target (0 when it has one) -> new target -> count
            val counts = HashMap<Int, HashMap<Int, HashMap<Int, Int>>>()
            val settings = File("src/main/assets/presets/${HnsEngineTest.KAIZO}.rnqs")
            val assets = HnsEngine.folderAssets(File("src/main/assets"))
            // Eight seeds at a time: one run takes several seconds and the counts are all that is kept of it.
            val pool8 = java.util.concurrent.Executors.newFixedThreadPool(8)
            try {
                val runs = (1..seeds).map { seed ->
                    pool8.submit<Map<Int, List<Int>>> {
                        val r = HnsEngine.randomize(rom, HnsEngine.readSettings(settings), seed.toLong() * 7919L, pool, assets)
                        val t = GbaTracker(reader(r.rom), GameMap.resolve(reader(r.rom)))
                        ids.associateWith { t.hnsEvoTargets(it) }
                    }
                }
                for (run in runs) {
                    val got = run.get()
                    for (id in ids) {
                        val was = original.getValue(id)
                        val now = got.getValue(id)
                        for ((i, target) in was.withIndex()) {
                            val to = now.getOrNull(i) ?: continue
                            val key = if (was.size == 1) 0 else target
                            counts.getOrPut(id) { HashMap() }.getOrPut(key) { HashMap() }.merge(to, 1, Int::plus)
                        }
                    }
                }
            } finally { pool8.shutdown() }
            val f = File(out, "revos-${pool.name.lowercase()}.tsv")
            f.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("# Heart & Soul's random evolutions on the ${pool.name} pool: KaizoCore's randomizer, ${HnsEngineTest.KAIZO}, made by HnsRevosTable.\n")
                w.write("# seeds $seeds\n")
                for (id in counts.keys.sorted()) for ((key, m) in counts.getValue(id).entries.sortedBy { it.key }) {
                    val n = m.values.sum().toDouble()
                    w.write("$id\t$key\t" + m.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}:" + String.format(java.util.Locale.US, "%.2f", it.value * 100.0 / n) } + "\n")
                }
            }
            println("wrote $f: ${counts.size} species over $seeds seeds")
        }
    }
}
