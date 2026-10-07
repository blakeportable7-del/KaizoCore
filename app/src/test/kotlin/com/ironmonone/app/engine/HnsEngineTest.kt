package com.ironmonone.app.engine

import com.ironmonone.app.Dumps
import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.engine.hns.HnsGame
import com.ironmonone.app.engine.hns.HnsLayout
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.app.engine.hns.HnsRom
import com.ironmonone.app.engine.hns.HnsSpeciesFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HnsEngine on the real Heart & Soul comfort build (.vendor/hns/hns-kaizo.gba, CRC 949DBE42, never in the repo).
 * Without the ROM every test returns early, as the other ROM tests do; under IRONMON_REQUIRE_DUMPS a missing ROM fails.
 * Each output is read back through a fresh HnsGame over the output bytes, plus raw reads where the game reads raw.
 */
class HnsEngineTest {
    companion object {
        private val assets = HnsEngine.folderAssets(File("src/main/assets"))
        private val presets = File("src/main/assets/presets")

        /** IRONMON_HNS_ROM, else .vendor/hns/hns-kaizo.gba in this checkout or in the main checkout a worktree belongs to. */
        val romFile: File? by lazy {
            val candidates = ArrayList<File>()
            System.getenv("IRONMON_HNS_ROM")?.let { candidates += File(it) }
            System.getenv("IRONMON_ROMS")?.let { candidates += File(File(it).parentFile, "hns/hns-kaizo.gba") }
            var d: File? = File("").absoluteFile
            while (d != null) {
                candidates += File(d, ".vendor/hns/hns-kaizo.gba")
                val git = File(d, ".git")
                if (git.isFile) {
                    // A worktree's .git file names <main>/.git/worktrees/<name>.
                    git.readText().substringAfter("gitdir:").trim().substringBefore("/.git/").let { candidates += File(it, ".vendor/hns/hns-kaizo.gba") }
                }
                d = d.parentFile
            }
            val f = candidates.firstOrNull { it.isFile }
            if (f == null && Dumps.required) throw AssertionError("IRONMON_REQUIRE_DUMPS: hns-kaizo.gba is not in .vendor/hns, and the release gate needs it")
            f
        }

        val rom: ByteArray? by lazy { romFile?.readBytes() }

        private val cache = HashMap<String, HnsEngine.Result>()

        /** One run; the two Kaizo runs most tests read are kept (a 32 MB ROM each), the rest are made and dropped. */
        fun run(preset: String, seed: Long, pool: HnsEngine.Pool): HnsEngine.Result = synchronized(cache) {
            val key = "$preset|$seed|$pool"
            cache[key] ?: HnsEngine.randomize(rom!!, HnsEngine.readSettings(File(presets, "$preset.rnqs")), seed, pool, assets)
                .also { if (preset == KAIZO && seed == 1234L) cache[key] = it }
        }

        const val KAIZO = "RSE NatDex v1.2 Kaizo"
        val layout: HnsLayout by lazy { HnsLayout.parse(assets("hns/layout-kaizo.json")) }
        val speciesFile: HnsSpeciesFile by lazy { HnsSpeciesFile.parse(assets("hns/species-kaizo.json")) }
        /** The game in [bytes], read without changing them (HnsGame only writes when asked). */
        fun read(bytes: ByteArray) = HnsGame(HnsRom(bytes, layout), speciesFile)
        val vanilla: HnsGame by lazy { read(rom!!) }
    }

    private fun skip(): Boolean = (rom == null).also { if (it) println("HnsEngineTest skipped: no .vendor/hns/hns-kaizo.gba") }

    @Test
    fun `the comfort build is what the layout describes`() {
        if (skip()) return
        assertEquals(0x949DBE42L, HnsEngine.crc(rom!!))
        assertEquals(0x949DBE42L, layout.buildCrc)
        assertEquals(null, HnsEngine.refusal(rom!!))
    }

    @Test
    fun `a file that is not a Heart and Soul build is refused`() {
        val junk = ByteArray(1024) { it.toByte() }
        assertNotNull(HnsEngine.refusal(junk))
    }

    @Test
    fun `the same seed makes the same ROM and log, another seed another`() {
        if (skip()) return
        val a = HnsEngine.randomize(rom!!, HnsEngine.readSettings(File(presets, "$KAIZO.rnqs")), 1234L, HnsEngine.Pool.NATDEX, assets)
        val b = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX)
        assertContentEquals(a.rom, b.rom)
        assertEquals(a.logText, b.logText)
        val c = run(KAIZO, 99L, HnsEngine.Pool.NATDEX)
        assertFalse(a.rom.contentEquals(c.rom))
        assertTrue(a.rom.size == rom!!.size)
    }

    /** Every id the engine wrote is one the game has: species, moves, abilities, items, and every list ends. */
    private fun assertSane(bytes: ByteArray, label: String) {
        val g = read(bytes)
        val L = g.L
        val r = g.rom
        val nSpecies = L.const("NUM_SPECIES")
        val nMoves = L.const("MOVES_COUNT")
        val nAbilities = L.const("ABILITIES_COUNT")
        val nItems = L.const("ITEMS_COUNT")
        fun placeable(id: Int) = id in 1 until nSpecies && g.mons[id]?.enabled == true
        val sp = L.struct("SpeciesInfo")
        for (m in g.mons) {
            if (m == null || !m.enabled || m.id == g.speciesEgg) continue
            m.abilities.forEach { assertTrue(it in 0 until nAbilities, "$label: ${m.const} ability $it") }
            assertTrue(m.stats.all { it in 1..255 }, "$label: ${m.const} stats ${m.stats.toList()}")
            assertTrue(m.itemCommon in 0 until nItems && m.itemRare in 0 until nItems, "$label: ${m.const} items")
            // The learnset as the game walks it: ends within MAX_LEVEL_UP_MOVES, levels 0-100, real moves.
            val p = r.get(sp.f("levelUpLearnset"), L.rec("gSpeciesInfo", m.id))
            var k = 0
            while (r.u16(p + 4 * k) != L.const("LEVEL_UP_MOVE_END")) {
                assertTrue(k < 40, "$label: ${m.const} learnset longer than 40")
                assertTrue(r.u16(p + 4 * k) in 1 until nMoves && r.u16(p + 4 * k + 2) in 0..100, "$label: ${m.const} learnset entry $k")
                k++
            }
            m.teachable.forEach { assertTrue(it in 1 until nMoves, "$label: ${m.const} teachable $it") }
            m.eggMoves.forEach { assertTrue(it in 1 until nMoves, "$label: ${m.const} egg move $it") }
            for (e in m.evos) assertTrue(placeable(e.target), "$label: ${m.const} evolves into ${e.target}")
        }
        for (t in g.trainers) {
            assertTrue(t.mons.size in 1..6, "$label: trainer ${t.id} has ${t.mons.size}")
            for (tm in t.mons) {
                assertTrue(placeable(tm.species), "$label: trainer ${t.id} species ${tm.species}")
                assertTrue(tm.level in 1..100, "$label: trainer ${t.id} level ${tm.level}")
                assertTrue(tm.heldItem in 0 until nItems, "$label: trainer ${t.id} item ${tm.heldItem}")
            }
        }
        for (s in g.wildSets) for (slot in s.slots) if (slot.species != 0) {
            assertTrue(placeable(slot.species), "$label: ${s.name} species ${slot.species}")
            assertTrue(slot.minLevel in 1..100 && slot.maxLevel in slot.minLevel..100, "$label: ${s.name} levels")
        }
        g.fieldItems.forEach { assertTrue(it.item in 1 until nItems, "$label: field item ${it.item}") }
        g.trades.forEach { assertTrue(placeable(it.species), "$label: trade ${it.species}") }
        for (i in 0 until 3) assertTrue(placeable(r.u16(L.sym("sStarterMon") + 2 * i)), "$label: starter $i")
        assertTrue(g.machineMoves.all { it in 1 until nMoves }, "$label: TM moves")
    }

    @Test
    fun `Kaizo writes only ids the game has, and lists that end`() {
        if (skip()) return
        assertSane(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom, "Kaizo NATDEX")
        assertSane(run(KAIZO, 1234L, HnsEngine.Pool.VANILLA).rom, "Kaizo VANILLA")
    }

    @Test
    fun `Kaizo stats keep each species' BST, starters and wild Pokemon stay under the line`() {
        if (skip()) return
        for (pool in HnsEngine.Pool.values()) {
            val out = read(run(KAIZO, 1234L, pool).rom)
            for (m in out.mons) {
                if (m == null || !m.enabled || m.id == out.speciesEgg) continue
                val orig = vanilla.mons[m.id]!!.bst
                if (vanilla.mons[m.id]!!.stats[0] == 1) continue
                assertTrue(kotlin.math.abs(m.bst - orig) <= 6, "${m.const}: BST $orig became ${m.bst}")
            }
            val line = if (pool == HnsEngine.Pool.NATDEX) 600 else 599
            val L = out.L
            for (i in 0 until 3) {
                val s = out.mons[out.rom.u16(L.sym("sStarterMon") + 2 * i)]!!
                assertTrue(s.bst < line, "$pool starter ${s.const} BST ${s.bst}")
            }
            for (set in out.wildSets) for (slot in set.slots) if (slot.species != 0) {
                val m = out.mons[slot.species]!!
                assertTrue(m.bst <= 599 && vanilla.mons[m.id]!!.bst <= 599 && m.bst < line, "$pool wild ${m.const} BST ${m.bst}")
                assertFalse(m.isStrongLegendary, "$pool wild legendary ${m.const}")
            }
        }
    }

    @Test
    fun `Kaizo learnsets open with an evolution move and four level 1 moves`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        var checked = 0
        for (m in out.mons) {
            if (m == null || !m.eligible) continue
            assertEquals(0, m.learnset[0].level, "${m.const} first entry")
            assertTrue(m.learnset.count { it.level == 1 } >= 4, "${m.const} level 1 moves ${m.learnset.map { it.level }}")
            assertEquals(m.learnset.map { it.move }.distinct().size, m.learnset.size, "${m.const} repeats a move")
            checked++
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun `no randomized learnset holds an HM move, so no Pokemon learns or starts with one`() {
        if (skip()) return
        // Blake, 2026-10-05: "starters can't know hm moves", "can't learn hm moves either". Trainers get their level-up
        // moves from these learnsets (their move slots are cleared), so this covers them too.
        for (pool in listOf(HnsEngine.Pool.NATDEX, HnsEngine.Pool.VANILLA)) for (seed in 1L..6L) {
            val out = read(run(KAIZO, seed, pool).rom)
            val hms = out.L.machines.filter { it.kind == "HM" }.map { it.move }.toSet()
            assertEquals(8, hms.size)
            for (m in out.mons) {
                if (m == null || !m.eligible) continue
                val bad = m.learnset.filter { it.move in hms }
                assertTrue(bad.isEmpty(), "$pool seed $seed: ${m.const} learns ${bad.map { "${out.moveName(it.move)} at ${it.level}" }}")
            }
            for (sp in (0 until 3).map { out.rom.u16(out.L.sym("sStarterMon") + 2 * it) })
                assertTrue(out.mons[sp]!!.learnset.none { it.move in hms }, "$pool seed $seed: starter ${out.speciesName(sp)}")
        }
    }

    @Test
    fun `Kaizo gives every species every HM`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val hms = out.L.machines.filter { it.kind == "HM" }.map { it.move }
        assertEquals(8, hms.size)
        for (m in out.mons) {
            if (m == null || !m.enabled || m.id == out.speciesEgg || m.cosmeticOf != 0) continue
            assertTrue(m.teachable.containsAll(hms), "${m.const} lacks an HM: ${hms - m.teachable.toSet()}")
        }
    }

    @Test
    fun `Kaizo TMs are new moves, written to the item table and the compiled lookups`() {
        if (skip()) return
        val bytes = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom
        val out = read(bytes)
        val L = out.L
        val tms = (1..out.tmCount).map { out.rom.get("TmHmIndexKey", L.rec("gTMHMItemMoveIds", it), "moveId") }
        assertFalse(tms == vanilla.origMachineMoves.take(out.tmCount))
        assertEquals(tms.size, tms.distinct().size)
        // The old run of TM moves is gone from the ROM, and the new one is where the old one was (three tables).
        val old = vanilla.origMachineMoves.take(out.tmCount).flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()
        val new = tms.flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()
        fun count(hay: ByteArray, needle: ByteArray): Int {
            var n = 0; var i = 0
            outer@ while (i + needle.size <= hay.size) { for (k in needle.indices) if (hay[i + k] != needle[k]) { i++; continue@outer }; n++; i += needle.size }
            return n
        }
        assertEquals(3, count(rom!!, old))
        assertEquals(0, count(bytes, old))
        assertEquals(3, count(bytes, new))
    }

    @Test
    fun `Kaizo trainers are 60 percent higher, capped at 100, and bosses carry three more`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val before = vanilla.trainers.associateBy { it.id }
        val bossClasses = listOf("LEADER_HNS", "LEADER_KANTO_HNS", "ELITE_FOUR_HNS", "CHAMPION_HNS").mapNotNull { out.L.enumValue("TRAINER_CLASS", "TRAINER_CLASS_$it") }
        var bosses = 0
        for (t in out.trainers) {
            val v = before.getValue(t.id)
            val expected = v.mons.map { minOf(100, Math.round(it.level * 1.6).toInt()) }.toSet()
            for (tm in t.mons) assertTrue(tm.level in expected, "trainer ${t.id}: level ${tm.level} not one of $expected")
            assertEquals(minOf(100, Math.round(v.mons.maxOf { it.level } * 1.6).toInt()), t.mons.maxOf { it.level })
            if (t.trainerClass in bossClasses || t.constName.startsWith("TRAINER_RED_")) {
                assertEquals(minOf(6, v.mons.size + 3), t.mons.size, "boss ${t.constName}")
                bosses++
            } else assertEquals(v.mons.size, t.mons.size, "trainer ${t.constName}")
        }
        assertTrue(bosses >= 40, "only $bosses bosses")
    }

    @Test
    fun `the rival carries the starter it is named for`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val starters = (0 until 3).map { out.rom.u16(out.L.sym("sStarterMon") + 2 * it) }
        val names = listOf("CHIKORITA", "CYNDAQUIL", "TOTODILE")
        for ((i, n) in names.withIndex()) {
            val first = out.trainers.first { it.constName == "TRAINER_RIVAL_${n}_1_HNS" }
            assertTrue(first.mons.any { it.species == starters[i] }, "first $n rival does not carry ${starters[i]}")
            // Every later battle carries the starter or one of its evolutions.
            val line = HashSet<Int>()
            fun add(id: Int) { if (line.add(id)) out.mons[id]?.evos?.forEach { add(it.target) } }
            add(starters[i])
            for (k in 2..7) {
                val t = out.trainers.first { it.constName == "TRAINER_RIVAL_${n}_${k}_HNS" }
                assertTrue(t.mons.any { it.species in line }, "rival $n $k carries no ${starters[i]} line: ${t.mons.map { it.species }}")
            }
        }
    }

    @Test
    fun `the VANILLA pool places only vanilla Emerald's Pokemon, Dex 1 to 386 in base form`() {
        if (skip()) return
        // Base forms of 1-386: the first species of each Dex number, never a regional form (Geodude-A is not one).
        val first = HashMap<Int, Int>()
        for (m in vanilla.mons) if (m != null && m.enabled && m.natDex in 1..386 && !m.regional) first.putIfAbsent(m.natDex, m.id)
        val scope = first.values.toSet()
        assertEquals(386, scope.size)
        val geodudeA = vanilla.L.enumValue("SPECIES", "SPECIES_GEODUDE_ALOLA")!!
        val magnezone = vanilla.L.enumValue("SPECIES", "SPECIES_MAGNEZONE")!!
        assertFalse(geodudeA in scope || magnezone in scope)
        for ((preset, seed) in listOf(KAIZO to 1234L, KAIZO to 77L, "RSE Kaizo" to 5L, "RSE Standard" to 9L)) {
            val out = read(run(preset, seed, HnsEngine.Pool.VANILLA).rom)
            val tag = "$preset $seed"
            for (i in 0 until 3) assertTrue(out.rom.u16(out.L.sym("sStarterMon") + 2 * i) in scope, "$tag starter $i")
            for (t in out.trainers) for (tm in t.mons) assertTrue(tm.species in scope, "$tag trainer ${t.constName} ${tm.species}")
            for (st in out.wildSets) for (slot in st.slots) if (slot.species != 0) assertTrue(slot.species in scope, "$tag wild ${st.name} ${slot.species}")
            for (t in out.trades) assertTrue(t.species in scope, "$tag trade ${t.species}")
            for (id in scope) for (e in out.mons[id]!!.evos) assertTrue(e.target in scope, "$tag ${out.mons[id]!!.const} evolves into ${out.mons[e.target]?.const}")
            for (sym in listOf("gHnsRoamerSpecies", "gHnsNamedGiftSpecies")) for (i in 0 until 4) assertTrue(out.rom.u16(out.L.sym(sym) + 2 * i) in scope, "$tag $sym $i")
            for (m in out.L.scriptMons) if (m.kind in setOf("givemon", "giveegg", "setwildbattle", "seteventmon", "setwildbossbattle") &&
                !m.label.startsWith("Debug_") && !m.label.contains("_Test") && m.text.contains("SPECIES_")) {
                val v = m.operands["species"] ?: continue
                val now = out.rom.u16(v.addr)
                if (now != v.value) assertTrue(now in scope, "$tag static ${m.label} $now")
            }
        }
    }

    @Test
    fun `a run carries the Kaizo IronMON challenge preset, and the Nuzlocke path the Nuzlocke one`() {
        if (skip()) return
        val L = layout
        val base = L.sym("gHnsChallengePreset") - L.romBase
        val v = base + L.const("HNS_PRESET_VALUES"); val lk = base + L.const("HNS_PRESET_LOCKS")
        fun row(tab: Int, item: Int) = tab * 20 + item
        // The build itself: the Nuzlocke mode's, menu shown, Nuzlocke rows free, BST equalizer OFF and locked.
        val r = rom!!
        assertEquals(L.const("HNS_PRESET_MODE_NUZLOCKE"), r[base].toInt())
        assertEquals(0, r[base + 1].toInt())
        assertEquals(0, r[lk + row(3, 0)].toInt())
        assertEquals(1, r[v + row(5, 4)].toInt()); assertEquals(1, r[lk + row(5, 4)].toInt())
        assertEquals(20, r[v + row(5, 3)].toInt(), "one type OFF is selection 19")
        assertEquals(1, r[v + row(0, 10)].toInt(), "legendary abilities OFF")
        assertEquals(2, r[v + row(0, 4)].toInt(), "reusable TMs ON")
        // A run: Kaizo IronMON's, the menu skipped, NUZLOCKE OFF and every Nuzlocke row locked; the rest as the build has it.
        val out = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom
        assertEquals(L.const("HNS_PRESET_MODE_KAIZO"), out[base].toInt())
        assertEquals(1, out[base + 1].toInt())
        assertEquals(1, out[v + row(3, 0)].toInt())
        assertTrue((0 until 6).all { out[lk + row(3, it)].toInt() == 1 })
        // Kaizo's own rules rows (Blake, 2026-10-05), all locked: CUSTOM, reusable TMs OFF, mints OFF, shiny 1/8192, drops OFF.
        val kaizoRows = mapOf(row(0, 0) to 2, row(0, 4) to 1, row(0, 5) to 1, row(1, 1) to 1, row(1, 3) to 1)
        for ((i, value) in kaizoRows) { assertEquals(value, out[v + i].toInt(), "Kaizo value $i"); assertEquals(1, out[lk + i].toInt(), "Kaizo lock $i") }
        assertEquals(0, r[lk + row(1, 1)].toInt(), "the build leaves SHINY CHANCE free")
        for (i in 0 until 120) if (i / 20 != 3 && i !in kaizoRows) {
            assertEquals(r[v + i], out[v + i], "value $i"); assertEquals(r[lk + i], out[lk + i], "lock $i")
        }
        // The Nuzlocke path puts the build's own preset back.
        val back = out.copyOf()
        HnsEngine.writePreset(back, L, HnsEngine.Preset.NUZLOCKE)
        assertTrue((0 until L.const("HNS_PRESET_SIZE")).all { back[base + it] == r[base + it] })
    }

    @Test
    fun `trainer doubles are singles in a Kaizo run, doubles in the Kaizo Doubles mode and the Nuzlocke one`() {
        if (skip()) return
        // Blake, 2026-10-06: KaizoCore_TrainerDoublesAreSingles reads the mode byte and this one; the build carries 0.
        val L = layout
        val at = L.sym("gHnsChallengePreset") - L.romBase + L.const("HNS_PRESET_KAIZO_DOUBLES")
        assertEquals(0, rom!![at].toInt())
        assertEquals(0, run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom[at].toInt())
        val doubles = run("RSE NatDex v1.2 Kaizo Doubles", 7L, HnsEngine.Pool.NATDEX).rom
        assertEquals(1, doubles[at].toInt())
        val back = doubles.copyOf()
        HnsEngine.writePreset(back, L, HnsEngine.Preset.NUZLOCKE)
        assertEquals(0, back[at].toInt())
    }

    @Test
    fun `the starters sit in the starter table and in the lab's three scripts`() {
        if (skip()) return
        val result = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX)
        val out = read(result.rom)
        val table = (0 until 3).map { out.rom.u16(out.L.sym("sStarterMon") + 2 * it) }
        val lab = out.L.scriptMons.filter { it.kind == "setvar-species" && it.file.contains("NewBarkTown_Lab") }
            .map { out.rom.u16(it.operands.getValue("value").addr) }
        assertEquals(3, lab.size)
        assertEquals(table, lab)
        val log = RandomizerLog.parse(result.logText)
        assertEquals(table.map { out.speciesName(it) }, log.starters)
    }

    @Test
    fun `the log parses with the log viewer's parser`() {
        if (skip()) return
        val result = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX)
        val log = RandomizerLog.parse(result.logText)
        val out = read(result.rom)
        assertEquals("1234", log.seed)
        assertEquals(HnsEngine.GAME_NAME, log.game)
        assertTrue(log.pokemon.size > 1000, "pokemon ${log.pokemon.size}")
        val bulba = log.pokemonNamed("Bulbasaur") ?: log.pokemonNamed("BULBASAUR")
        assertNotNull(bulba)
        val m = out.mons[out.L.enumValue("SPECIES", "SPECIES_BULBASAUR")!!]!!
        assertEquals(listOf(m.stats[0], m.stats[1], m.stats[2], m.stats[4], m.stats[5], m.stats[3]), bulba.stats)
        assertTrue(bulba.moves.size >= 5 && bulba.tmsLearnable.isNotEmpty())
        assertEquals(out.tmCount, log.tms.size)
        assertEquals(out.trainers.size, log.trainers.size)
        assertTrue(log.routes.size > 100)
        assertTrue(log.statics.isNotEmpty())
        assertTrue(log.trainers.all { it.party.isNotEmpty() })
    }

    @Test
    fun `random moves come from the Nat Dex fork's own pools and abilities stop at Teravolt`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val rules = com.ironmonone.app.engine.hns.HnsUprRules(zx = false)
        val moveNames = out.L.enum("MOVE").entries.groupBy({ it.value }, { it.key.removePrefix("MOVE_") })
        val abilityNames = out.L.enum("ABILITY").entries.groupBy({ it.value }, { it.key.removePrefix("ABILITY_") })
        assertTrue("U_TURN" in rules.bannedMoves && "CLOSE_COMBAT" in rules.bannedMoves)
        for (m in out.mons) {
            if (m == null || !m.eligible) continue
            for (lm in m.learnset) assertFalse(moveNames[lm.move].orEmpty().any { it in rules.bannedMoves }, "${m.const} learns ${moveNames[lm.move]}")
            val vm = vanilla.mons[m.id]!!
            // Shedinja keeps Wonder Guard as UPR keeps it; every other slot was rolled from Black 2 / White 2's 1 to 164
            // (the Nat. Dex pool's abilities since rc38; HnsNatDexParityTest has the rest).
            if (vm.abilities.contains(out.L.enumValue("ABILITY", "ABILITY_WONDER_GUARD") ?: -1)) continue
            for (a in m.abilities) assertTrue(a in 0..164, "${m.const} has ${abilityNames[a]}")
        }
        // HnsGame's machineMoves is the layout's vanilla list; the ROM's TMs are in gTMHMItemMoveIds.
        val tms = (1..out.tmCount).map { out.rom.get("TmHmIndexKey", out.L.rec("gTMHMItemMoveIds", it), "moveId") }
        assertTrue(tms.none { mv -> moveNames[mv].orEmpty().any { it in rules.bannedMoves } }, "TMs ${tms.map { moveNames[it] }}")
    }

    @Test
    fun `the plain build randomizes too when its ROM is there`() {
        val plain = romFile?.let { File(it.parentFile, "hns-plain.gba") }?.takeIf { it.isFile } ?: return
        val bytes = plain.readBytes()
        assertEquals(0x45D07ED4L, HnsEngine.crc(bytes))
        val r = HnsEngine.randomize(bytes, HnsEngine.readSettings(File(presets, "$KAIZO.rnqs")), 5L, HnsEngine.Pool.NATDEX, assets)
        assertTrue(RandomizerLog.parse(r.logText).trainers.isNotEmpty())
    }

    @Test
    fun `easy and hard trainers stay empty, so the game falls back to the randomized normal ones`() {
        if (skip()) return
        val bytes = run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom
        val L = layout
        val count = L.const("TRAINERS_COUNT")
        val ts = L.struct("Trainer")
        val r = HnsRom(bytes, L)
        for (d in 0 until L.const("DIFFICULTY_COUNT")) {
            if (d == L.const("DIFFICULTY_NORMAL")) continue
            for (tid in 0 until count) {
                val a = L.rec("gTrainers", d * count + tid)
                assertEquals(0, r.get(ts.f("party"), a), "difficulty $d trainer $tid has a party")
            }
        }
    }

    @Test
    fun `the Game Corner pays out the new prizes, each setvar and its case moving together`() {
        if (skip()) return
        val out = read(run(KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val setvars = out.L.scriptMons.filter { it.kind == "setvar-species" && it.label.contains("GameCorner") }
        assertEquals(5, setvars.size)
        val prizes = setvars.map { out.rom.u16(it.operands.getValue("value").addr) }
        val cases = out.L.speciesRefs.filter { it.kind == "case" && it.label.contains("GameCorner") }.flatMap { it.refs }.map { out.rom.u16(it.second) }
        assertTrue(cases.containsAll(prizes), "cases $cases do not pay out $prizes")
        assertEquals(5, prizes.distinct().size)
        assertFalse(prizes == setvars.map { it.operands.getValue("value").value })
    }

    // ---------------------------------------------------------------- every mode

    private val natDexModes = listOf("Standard", "Ultimate", "Kaizo", "Survival", "Survival Revival", "Super Kaizo", "Chaos Kaizo",
        "Evo Kaizo", "Kaizo Doubles", "Ironmon Journey")

    @Test
    fun `every RSE preset makes a game whose ids are all real`() {
        if (skip()) return
        for (mode in natDexModes) assertSane(run("RSE NatDex v1.2 $mode", 7L, HnsEngine.Pool.NATDEX).rom, mode)
        for (mode in listOf("Standard", "Kaizo", "Super Kaizo", "Ironmon Journey")) assertSane(run("RSE $mode", 7L, HnsEngine.Pool.VANILLA).rom, "RSE $mode")
    }

    @Test
    fun `each mode's own options show in its game`() {
        if (skip()) return
        fun game(mode: String) = read(run("RSE NatDex v1.2 $mode", 7L, HnsEngine.Pool.NATDEX).rom)
        val fluctuating = vanilla.L.enumValue("GROWTH", "GROWTH_FLUCTUATING")
        val mediumSlow = vanilla.L.enumValue("GROWTH", "GROWTH_MEDIUM_SLOW")
        // Standard: levels +50%, no added Pokemon, curves left alone.
        game("Standard").let { g ->
            val t = g.trainers.first { it.constName == "TRAINER_FALKNER_HNS" || it.constName.contains("FALKNER") }
            val v = vanilla.trainers.first { it.id == t.id }
            assertEquals(v.mons.size, t.mons.size)
            assertEquals(minOf(100, Math.round(v.mons.maxOf { it.level } * 1.5).toInt()), t.mons.maxOf { it.level })
        }
        // Ultimate: every curve Fluctuating, legendaries too.
        game("Ultimate").let { g -> assertTrue(g.mons.filterNotNull().filter { it.eligible }.all { it.growth == fluctuating }) }
        // Super Kaizo: bosses carry five more, regular trainers hold items, every trainer has UPR's smart AI bits.
        game("Super Kaizo").let { g ->
            val ai = g.L.struct("Trainer").f("aiFlags")
            for (t in g.trainers) {
                val now = g.rom.read(t.addr + ai.offset, ai.size)
                val was = vanilla.rom.read(t.addr + ai.offset, ai.size)
                assertEquals(was or 7L, now, "trainer ${t.constName} AI flags")
            }
            val t = g.trainers.first { it.constName.contains("FALKNER") }
            assertEquals(minOf(6, vanilla.trainers.first { it.id == t.id }.mons.size + 5), t.mons.size)
            assertTrue(g.trainers.count { tr -> tr.mons.all { it.heldItem != 0 } } > g.trainers.size / 2)
        }
        // Kaizo Doubles: trainers battle in doubles, the first rival does not.
        game("Kaizo Doubles").let { g ->
            val doubles = g.L.enumValue("TRAINER_BATTLE_TYPE", "TRAINER_BATTLE_TYPE_DOUBLES")
            val bt = g.L.struct("Trainer").f("battleType")
            assertTrue(g.trainers.filter { !it.constName.matches(Regex("TRAINER_RIVAL_[A-Z]+_1_HNS")) }.all { g.rom.get(bt, it.addr) == doubles })
            assertTrue(g.trainers.all { it.mons.size >= 2 || it.constName.matches(Regex("TRAINER_RIVAL_[A-Z]+_1_HNS")) })
        }
        // Evo Kaizo: every species of the pool evolves at its next level, and no time-based or boss changes are lost.
        game("Evo Kaizo").let { g ->
            val level = g.L.enumValue("EVO", "EVO_LEVEL")
            val pool = g.mons.filterNotNull().filter { it.eligible }
            assertTrue(pool.count { m -> m.evos.size == 1 && m.evos[0].method == level && m.evos[0].param == 1 } > pool.size * 9 / 10)
        }
        // Chaos Kaizo: move data and types changed.
        game("Chaos Kaizo").let { g ->
            val changedMoves = (1 until g.movesCount).count { g.moves[it]!!.power != vanilla.moves[it]!!.power }
            assertTrue(changedMoves > 100, "moves changed $changedMoves")
            val changedTypes = g.mons.filterNotNull().count { it.eligible && !it.types.contentEquals(vanilla.mons[it.id]!!.types) }
            assertTrue(changedTypes > 500, "types changed $changedTypes")
        }
        // Ironmon Journey: Medium Slow curves and starters with two evolutions.
        game("Ironmon Journey").let { g ->
            assertTrue(g.mons.filterNotNull().filter { it.eligible && !it.isStrongLegendary }.all { it.growth == mediumSlow })
            for (i in 0 until 3) {
                val s = g.mons[g.rom.u16(g.L.sym("sStarterMon") + 2 * i)]!!
                assertTrue(s.evos.isNotEmpty() && s.evos.any { e -> g.mons[e.target]!!.evos.isNotEmpty() }, "Journey starter ${s.const}")
            }
        }
    }
}
