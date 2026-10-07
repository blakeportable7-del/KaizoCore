package com.ironmonone.app.engine

import com.ironmonone.app.BstRule
import com.ironmonone.app.Dumps
import com.ironmonone.app.GameBuild
import com.ironmonone.app.NextRun
import com.ironmonone.app.NuzlockeFair
import com.ironmonone.app.PcHeals
import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.RnqsInfo
import com.ironmonone.app.RunCode
import com.ironmonone.app.RunCodes
import com.ironmonone.app.engine.hns.HnsGame
import com.ironmonone.app.engine.hns.HnsLayout
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.app.engine.hns.HnsRom
import com.ironmonone.app.engine.hns.HnsSpeciesFile
import com.ironmonone.core.RomKind
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The release check for Heart & Soul (Blake, 2026-10-06: "every Kaizo IronMON mode must work on Heart & Soul, with both
 * pools"). Every Emerald Nat. Dex v1.2 mode file, on both pools, is randomized with a fixed seed through the path the app
 * takes (Randomizers.randomize, HnsEngine.appAssets), and its output is read back through a fresh HnsGame and held to what
 * the mode's settings ask for: each option the file sets is either seen in the game or said in the log's KaizoCore Notes.
 * An option the engine drops without a word is a failure. Every check runs and the test fails with the whole list, so a
 * row names every gap at once. A line "HNSMODE|mode|pool|PASS or FAIL|..." goes to the output for the table.
 *
 * Needs the comfort build (IRONMON_HNS, else .vendor/hns/hns-kaizo.gba as HnsEngineTest finds it); without it the test
 * returns early, and under IRONMON_REQUIRE_DUMPS a missing ROM fails.
 */
@RunWith(Parameterized::class)
class HnsEveryModeTest(private val mode: String, private val pool: HnsEngine.Pool) {
    companion object {
        val MODES = listOf("Standard", "Ultimate", "Kaizo", "Survival", "Survival Revival", "Super Kaizo", "Kaizo Doubles",
            "Chaos Kaizo", "Evo Kaizo", "Ironmon Journey")
        const val SEED = 20261006L

        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = MODES.flatMap { m -> HnsEngine.Pool.entries.map { arrayOf<Any>(m, it) } }

        val assetsDir = File("src/main/assets")
        val assets = HnsEngine.folderAssets(assetsDir)
        val presets = File(assetsDir, "presets")

        /**
         * The comfort build this checkout's layout describes: IRONMON_HNS_ROM (a file), else IRONMON_HNS's hns-kaizo.gba,
         * else the file HnsEngineTest finds; whichever it is must carry the layout's CRC, or the run is refused.
         */
        val romFile: File? by lazy {
            System.getenv("IRONMON_HNS_ROM")?.let { File(it) }?.takeIf { it.isFile }
                ?: System.getenv("IRONMON_HNS")?.let { File(it, "hns-kaizo.gba") }?.takeIf { it.isFile } ?: HnsEngineTest.romFile
        }
        val layout: HnsLayout by lazy { HnsLayout.parse(assets("hns/layout-kaizo.json")) }
        val speciesFile: HnsSpeciesFile by lazy { HnsSpeciesFile.parse(assets("hns/species-kaizo.json")) }
        fun read(bytes: ByteArray) = HnsGame(HnsRom(bytes, layout), speciesFile)
        val vanillaBytes: ByteArray by lazy { romFile!!.readBytes() }
        val vanilla: HnsGame by lazy { read(vanillaBytes) }

        /** Base forms of Dex 1-386, never a regional form: the VANILLA pool (HnsRandomizer.inVanillaEmerald). */
        val vanillaScope: Set<Int> by lazy {
            vanilla.mons.filterNotNull().filter { it.eligible && it.natDex in 1..386 && !it.regional && it.baseOf == it.id }.map { it.id }.toSet()
        }

        /** One run through the app's own path: the ROM file in, the run and its log file out. */
        class Made(val bytes: ByteArray, val logText: String, val logFileWritten: Boolean, val sizeOk: Boolean)

        fun make(settings: File, pool: HnsEngine.Pool, seed: Long): Made {
            val dir = Files.createTempDirectory("hnsmode").toFile()
            val oldAssets = HnsEngine.assetText
            HnsEngine.assetText = assets
            try {
                val dest = File(dir, "run.gba")
                val outcome = Randomizers.randomize(RomKind.HEARTSOUL_KAIZO_206, romFile!!, settings, dest, seed, pool = pool)
                val log = Randomizers.logFor(dest)
                val made = Made(dest.readBytes(), outcome.logText, log.isFile && log.readText() == outcome.logText, dest.length() == romFile!!.length())
                return made
            } finally {
                HnsEngine.assetText = oldAssets
                dir.deleteRecursively()
            }
        }

        /** The "--KaizoCore Notes--" lines of a log. */
        fun notesOf(log: String): List<String> {
            val lines = log.lines()
            val i = lines.indexOfFirst { it.trim() == "--KaizoCore Notes--" }
            if (i < 0) return emptyList()
            return lines.drop(i + 1).takeWhile { it.isNotBlank() }
        }

        fun presetRegion(bytes: ByteArray): ByteArray {
            val base = layout.sym("gHnsChallengePreset") - layout.romBase
            return bytes.copyOfRange(base, base + layout.const("HNS_PRESET_SIZE"))
        }

        private val RIVAL1 = Regex("TRAINER_RIVAL_[A-Z]+_1_HNS")
        private val RIVAL = Regex("TRAINER_RIVAL_[A-Z]+_\\d+_HNS")
        fun isRival1(t: HnsGame.TrainerData) = RIVAL1.matches(t.constName)
        fun isRival(t: HnsGame.TrainerData) = RIVAL.matches(t.constName)
        fun isBoss(g: HnsGame, t: HnsGame.TrainerData): Boolean {
            val boss = listOf("LEADER_HNS", "LEADER_KANTO_HNS", "ELITE_FOUR_HNS", "CHAMPION_HNS").mapNotNull { g.L.enumValue("TRAINER_CLASS", "TRAINER_CLASS_$it") }
            return t.trainerClass in boss || t.constName.startsWith("TRAINER_RED_")
        }
        fun isEliteOrChampion(g: HnsGame, t: HnsGame.TrainerData): Boolean {
            val c = listOf("ELITE_FOUR_HNS", "CHAMPION_HNS").mapNotNull { g.L.enumValue("TRAINER_CLASS", "TRAINER_CLASS_$it") }
            return t.trainerClass in c
        }
        fun modLevel(level: Int, mod: Int) = minOf(100, Math.round(level * (1 + mod / 100.0)).toInt())

        /** Every species a run places where the player meets it: starters, trainers, wild, trades, scripts, roamers, gifts. */
        fun placed(g: HnsGame): Map<String, List<Int>> {
            val L = g.L
            val out = LinkedHashMap<String, List<Int>>()
            out["starters"] = (0 until 3).map { g.rom.u16(L.sym("sStarterMon") + 2 * it) }
            out["trainers"] = g.trainers.flatMap { t -> t.mons.map { it.species } }
            out["wild"] = g.wildSets.flatMap { s -> s.slots.map { it.species } }.filter { it != 0 }
            out["trades"] = g.trades.flatMap { listOf(it.species, it.requested) }.filter { it != 0 }
            val scripts = ArrayList<Int>()
            for (m in L.scriptMons) if (m.kind in setOf("givemon", "giveegg", "setwildbattle", "seteventmon", "setwildbossbattle") &&
                !m.label.startsWith("Debug_") && !m.label.contains("_Test") && m.text.contains("SPECIES_")) {
                val v = m.operands["species"] ?: continue
                scripts += g.rom.u16(v.addr)
            }
            out["scripts"] = scripts
            val tables = ArrayList<Int>()
            for (sym in listOf("gHnsRoamerSpecies", "gHnsNamedGiftSpecies", "sOddEggSpecies")) if (L.hasSym(sym)) {
                val n = L.tables[sym]?.count ?: 4
                for (i in 0 until n) g.rom.u16(L.sym(sym) + 2 * i).takeIf { it in 1 until g.numSpecies }?.let { tables += it }
            }
            out["gift tables"] = tables
            return out
        }
    }

    private val settingsFile = File(presets, "RSE NatDex v1.2 $mode.rnqs")
    private val problems = ArrayList<String>()

    /** One named check: a thrown assertion is recorded, never stops the others. */
    private fun check(name: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { problems += "$name: ${e.message}" }
    }

    private fun expect(cond: Boolean, msg: () -> String) { if (!cond) throw AssertionError(msg()) }

    @Test
    fun `the mode makes a valid run on this pool, every option applied or noted`() {
        if (romFile == null) {
            if (Dumps.required) fail("IRONMON_REQUIRE_DUMPS: hns-kaizo.gba is missing")
            println("HnsEveryModeTest skipped: no hns-kaizo.gba"); return
        }
        expect(settingsFile.isFile) { "missing $settingsFile" }
        expect(HnsEngine.refusal(vanillaBytes) == null) { "${romFile} is not a build this checkout's layout knows: ${HnsEngine.refusal(vanillaBytes)}" }
        val o: HnsOptions = HnsEngine.readSettings(settingsFile)
        val made = make(settingsFile, pool, SEED)
        val out = read(made.bytes)
        val v = vanilla
        val L = out.L
        val log = made.logText
        val notes = notesOf(log)
        val natdex = pool == HnsEngine.Pool.NATDEX
        val modeKey = HnsEngine.modeKey(o.modeName)
        val line = HnsEngine.bstLine(o, pool)
        /** The rule sheet's line for a wild Pokemon (BstRule.Lines.wild): what the tracker marks with an X. */
        val wildLine = BstRule.lines(RnqsInfo.parse(settingsFile.name).ruleset, natDex = natdex)?.wild
        val poolMons = out.mons.filterNotNull().filter { it.eligible && (natdex || it.id in vanillaScope) }
        val poolIds = poolMons.map { it.id }.toSet()
        val traitMons = out.mons.filterNotNull().filter { it.enabled && it.id != out.speciesEgg && it.cosmeticOf == 0 }
        val itemConst: Map<Int, String> = L.enum("ITEM").entries.associate { it.value to it.key }
        val luckyEgg = L.enumValue("ITEM", "ITEM_LUCKY_EGG")
        fun ability(n: String) = L.enumValue("ABILITY", "ABILITY_$n")
        val wonderGuard = ability("WONDER_GUARD")
        fun evosFrom(m: HnsGame.Mon) = m.evos.mapNotNull { out.mons.getOrNull(it.target) }.filter { it.enabled }

        // ------------------------------------------------------------- the run itself
        check("ROM written whole") {
            expect(made.sizeOk && made.bytes.size == vanillaBytes.size) { "size ${made.bytes.size}" }
            expect(!made.bytes.contentEquals(vanillaBytes)) { "the output is the input" }
        }
        check("log written beside the ROM") { expect(made.logFileWritten) { "no <rom>.log, or not the engine's log" } }
        check("log parses") {
            val p = RandomizerLog.parse(log)
            expect(p.seed == SEED.toString()) { "seed ${p.seed}" }
            expect(p.game == HnsEngine.GAME_NAME) { "game ${p.game}" }
            expect(o.settingsString.isNotEmpty() && log.contains("Settings String: ${o.settingsString}")) { "settings string not logged" }
            val st = (0 until 3).map { out.speciesName(out.rom.u16(L.sym("sStarterMon") + 2 * it)) }
            expect(p.starters == st) { "log starters ${p.starters} vs ROM $st" }
            expect(p.trainers.size == out.trainers.size) { "log trainers ${p.trainers.size} vs ${out.trainers.size}" }
            expect(p.pokemon.size > 1000) { "log pokemon ${p.pokemon.size}" }
        }
        check("same seed, same ROM") {
            // Determinism is what NEW RUN and run codes stand on; checked once per pool on Kaizo, the rest share the engine.
            if (mode == "Kaizo") expect(make(settingsFile, pool, SEED).bytes.contentEquals(made.bytes)) { "a second run differs" }
        }

        // ------------------------------------------------------------- challenge preset
        check("challenge preset is Kaizo IronMON's") {
            val base = L.sym("gHnsChallengePreset") - L.romBase
            expect(made.bytes[base + L.const("HNS_PRESET_MODE")].toInt() == L.const("HNS_PRESET_MODE_KAIZO")) { "mode byte ${made.bytes[base]}" }
            expect(made.bytes[base + L.const("HNS_PRESET_SKIP_MENU")].toInt() == 1) { "menu not skipped" }
            val vals = base + L.const("HNS_PRESET_VALUES"); val locks = base + L.const("HNS_PRESET_LOCKS")
            expect(made.bytes[vals + 60].toInt() == 1 && (0 until 6).all { made.bytes[locks + 60 + it].toInt() == 1 }) { "Nuzlocke rows not OFF and locked" }
            // The Nuzlocke path puts the build's own preset back on any mode's run.
            val back = made.bytes.copyOf()
            HnsEngine.writePreset(back, layout, HnsEngine.Preset.NUZLOCKE)
            expect(presetRegion(back).contentEquals(presetRegion(vanillaBytes))) { "Nuzlocke preset is not the build's" }
        }

        // ------------------------------------------------------------- recipe and run code
        check("recipe and run code keep mode and pool") {
            val dir = Files.createTempDirectory("hnsrecipe").toFile()
            val old = Randomizers.hnsPoolChosen
            try {
                Randomizers.hnsPoolChosen = { pool }
                val prepared = File(dir, "hns.gba").apply { writeBytes(ByteArray(16)) }
                val recipe = NextRun.recipe(RomKind.HEARTSOUL_KAIZO_206, prepared, settingsFile, null, null, "test")
                expect(Randomizers.hnsPoolOf(recipe.engine) == pool) { "engine id ${recipe.engine}" }
                val back = NextRun.Recipe.of(recipe.lines().associate { it.substringBefore('=') to it.substringAfter('=') })
                expect(back == recipe) { "recipe did not read back" }
                expect(back!!.settings == settingsFile.name && HnsEngine.modeKey(back.settings.removeSuffix(".rnqs")) == modeKey) { "mode lost" }
                // A stage made for this pool is taken for this pool and never for the other.
                val next = NextRun(File(dir, "next"))
                next.make(recipe, SEED, { dest, _ -> dest.writeBytes(ByteArray(8) { 1 }) })
                expect(next.ready(recipe) != null) { "stage not taken for its own recipe" }
                val other = if (natdex) HnsEngine.Pool.VANILLA else HnsEngine.Pool.NATDEX
                Randomizers.hnsPoolChosen = { other }
                val otherRecipe = NextRun.recipe(RomKind.HEARTSOUL_KAIZO_206, prepared, settingsFile, null, null, "test")
                expect(otherRecipe != recipe && next.ready(otherRecipe) == null) { "a stage of one pool was taken for the other" }
                // The run code carries the pool.
                val code = RunCodes.shareCodeFor(recipe, SEED, 0x12345678L)
                val parsed = RunCode.parse(code.text())
                // The code names its settings by content (the hash) and carries the pool; its name is only for people.
                expect(parsed != null && RunCodes.hnsPoolOf(parsed) == pool && parsed.settingsHash == code.settingsHash && parsed.seed == SEED) { "run code lost the pool or the settings" }
            } finally {
                Randomizers.hnsPoolChosen = old
                dir.deleteRecursively()
            }
        }

        // ------------------------------------------------------------- the mode's own rules outside the engine
        check("BST line is the rule sheet's") {
            val rs = RnqsInfo.parse(settingsFile.name).ruleset
            expect(line == BstRule.lines(rs, natDex = natdex)?.own) { "engine line $line, rule sheet ${BstRule.lines(rs, natdex)} for $rs" }
        }
        check("Survival heal limit set up") {
            val limit = PcHeals.limitFor(settingsFile.name)
            val want = when (mode) { "Survival" -> PcHeals.Limit.SURVIVAL; "Survival Revival" -> PcHeals.Limit.REVIVAL; else -> null }
            expect(limit == want) { "PcHeals.limitFor = $limit, want $want" }
        }

        // ------------------------------------------------------------- pool scope and banned Pokemon
        val placedNow = placed(out)
        check("every placed species is placeable") {
            for ((where, ids) in placedNow) for (id in ids) {
                val m = out.mons.getOrNull(id)
                expect(m != null && m.eligible) { "$where: ${out.speciesName(id)} is not placeable (battle form, cosmetic copy or invalid)" }
            }
        }
        if (!natdex) check("VANILLA never leaves Gen 1-3 base forms") {
            for ((where, ids) in placedNow) for (id in ids) expect(id in vanillaScope) { "$where: ${out.speciesName(id)}" }
            for (id in vanillaScope) for (e in out.mons[id]!!.evos) expect(e.target in vanillaScope) { "${out.speciesName(id)} evolves into ${out.speciesName(e.target)}" }
        }
        if (natdex) check("NATDEX reaches past Gen 3") {
            val outside = placedNow.values.flatten().count { it !in vanillaScope }
            expect(outside > 0) { "no species outside Gen 1-3 base forms was placed" }
        }
        check("Eternamax stays out (the file's restrictions leave it out)") {
            val e = L.enumValue("SPECIES", "SPECIES_ETERNATUS_ETERNAMAX")
            if (e != null && o.limitPokemon) {
                val hits = placedNow.filter { (_, ids) -> e in ids }.keys
                expect(hits.isEmpty()) { "Eternamax placed in $hits" }
                expect(out.mons.filterNotNull().none { m -> m.id in poolIds && m.evos.any { it.target == e } }) { "a pool species evolves into Eternamax" }
            }
        }

        // ------------------------------------------------------------- starters
        val starters = placedNow["starters"]!!
        check("starters (${o.startersMod})") {
            expect(starters.distinct().size == 3 && starters.all { it in poolIds }) { "starters ${starters.map { out.speciesName(it) }}" }
            expect(starters != (0 until 3).map { v.rom.u16(L.sym("sStarterMon") + 2 * it) }) { "starters unchanged" }
            if (o.startersMod == "RANDOM_WITH_TWO_EVOLUTIONS") for (s in starters) {
                val m = out.mons[s]!!
                expect(evosFrom(m).any { evosFrom(it).isNotEmpty() }) { "${m.const} does not evolve twice" }
            }
            if (line != null) for (s in starters) expect(out.mons[s]!!.bst < line) { "starter ${out.speciesName(s)} BST ${out.mons[s]!!.bst} >= $line" }
        }
        if (o.randomizeStarterHeldItems) check("starter held item") {
            expect(log.contains("--Starter Held Item--") || notes.any { it.contains("Starter held items") }) { "not set and not noted" }
            if (o.banLuckyEgg) expect(out.starterItem != luckyEgg) { "Lucky Egg" }
        }

        // ------------------------------------------------------------- Pokemon traits
        if (o.baseStatsMod == "RANDOM") check("base stats random within BST") {
            val changed = traitMons.count { !it.stats.contentEquals(v.mons[it.id]!!.stats) }
            expect(changed > traitMons.size * 9 / 10) { "$changed of ${traitMons.size} changed" }
            for (m in traitMons) if (v.mons[m.id]!!.stats[0] != 1) expect(abs(m.bst - v.mons[m.id]!!.bst) <= 6) { "${m.const} BST ${v.mons[m.id]!!.bst} -> ${m.bst}" }
        }
        if (o.abilitiesMod == "RANDOMIZE") check("abilities random, bans kept") {
            val changed = traitMons.count { !it.abilities.contentEquals(v.mons[it.id]!!.abilities) }
            expect(changed > traitMons.size * 8 / 10) { "$changed of ${traitMons.size} changed" }
            val banned = HashSet<Int>()
            if (!o.allowWonderGuard) wonderGuard?.let { banned += it }
            if (o.banTrappingAbilities) banned += listOf("SHADOW_TAG", "MAGNET_PULL", "ARENA_TRAP").mapNotNull { ability(it) }
            if (o.banNegativeAbilities) banned += listOf("DEFEATIST", "SLOW_START", "TRUANT", "KLUTZ", "STALL").mapNotNull { ability(it) }
            for (m in traitMons) {
                if (wonderGuard != null && wonderGuard in v.mons[m.id]!!.abilities) continue
                expect(m.abilities.none { it in banned }) { "${m.const} has a banned ability ${m.abilities.toList()}" }
                // UPR's pickRandomAbility checks for a repeat before it swaps in a variation (Insomnia for Vital Spirit), so
                // its second ability can be the first one's twin; HnsRandomizer copies that. "Two abilities" is a second slot.
                if (o.ensureTwoAbilities) expect(m.abilities[1] != 0) { "${m.const} has no second ability" }
            }
        }
        if (o.typesMod != "UNCHANGED") check("types ${o.typesMod}") {
            val changed = traitMons.count { !it.types.contentEquals(v.mons[it.id]!!.types) }
            expect(changed > 500) { "types changed $changed" }
        }
        if (o.standardizeExpCurves) check("EXP curves ${o.expCurve} (${o.expCurveMod})") {
            val chosen = L.enumValue("GROWTH", "GROWTH_" + o.expCurve)
            val slow = L.enumValue("GROWTH", "GROWTH_SLOW")
            for (m in out.mons.filterNotNull()) {
                if (!m.enabled || m.id == out.speciesEgg || m.baseOf != m.id) continue
                val want = when (o.expCurveMod) {
                    "LEGENDARIES" -> if (m.isLegendary) slow else chosen
                    "STRONG_LEGENDARIES" -> if (m.isStrongLegendary) slow else chosen
                    else -> chosen
                }
                expect(m.growth == want) { "${m.const} growth ${m.growth}, want $want" }
            }
        }
        if (o.useMinimumCatchRate) check("minimum catch rate level ${o.minimumCatchRateLevel}") {
            val (normal, legend) = when (o.minimumCatchRateLevel) { 2 -> 128 to 64; 3 -> 200 to 100; 4, 5 -> 255 to 255; else -> 75 to 37 }
            for (m in out.mons.filterNotNull()) if (m.enabled) expect(m.catchRate >= (if (m.isLegendary) legend else normal)) { "${m.const} catch rate ${m.catchRate}" }
            if (o.minimumCatchRateLevel == 5) expect(notes.any { it.contains("Guaranteed catching") }) { "level 5 (guaranteed) neither applied nor noted" }
        }
        if (o.randomizeWildHeldItems) check("wild held items") {
            val changed = traitMons.count { it.itemCommon != v.mons[it.id]!!.itemCommon || it.itemRare != v.mons[it.id]!!.itemRare }
            expect(changed > traitMons.size / 3) { "changed $changed" }
            if (o.banLuckyEgg) expect(traitMons.none { it.itemCommon == luckyEgg || it.itemRare == luckyEgg }) { "a wild Pokemon holds a Lucky Egg" }
        }

        // ------------------------------------------------------------- evolutions
        val timeConds = listOfNotNull(L.enumValue("IF", "IF_TIME"), L.enumValue("IF", "IF_NOT_TIME")).toSet()
        val evoLevel = L.enumValue("EVO", "EVO_LEVEL")
        when (o.evolutionsMod) {
            "RANDOM" -> check("evolutions random") {
                val withEvos = poolMons.filter { v.mons[it.id]!!.evos.isNotEmpty() && it.evos.isNotEmpty() }
                val changed = withEvos.count { m -> m.evos.map { it.target }.toSet() != v.mons[m.id]!!.evos.map { it.target }.toSet() }
                // Without forced change a random pick can land on the old target: Ironmon Journey on the Vanilla pool
                // changed 86.6% to 95.3% over seeds 1-16 and SEED (2026-10-06, build 949DBE42), so 90% failed on
                // four of them; four in five still tells a randomized table from an untouched one.
                expect(changed > withEvos.size * 4 / 5) { "$changed of ${withEvos.size} changed" }
                if (o.evosForceChange) for (m in poolMons) {
                    val old = v.mons[m.id]!!.evos.map { it.target }.toSet()
                    expect(m.evos.none { it.target in old }) { "${m.const} kept an evolution into ${m.evos.filter { it.target in old }.map { out.speciesName(it.target) }}" }
                }
            }
            "RANDOM_EVERY_LEVEL" -> check("evolutions every level, no loops") {
                val byGrowth = poolMons.groupBy { it.growth }
                for ((growth, group) in byGrowth) {
                    if (group.size < 2) continue
                    val finals = group.filter { it.evos.isEmpty() }
                    expect(finals.size == 1) { "growth $growth: ${finals.size} species with no evolution" }
                    for (m in group) if (m.evos.isNotEmpty()) expect(m.evos.size == 1 && m.evos[0].method == evoLevel && m.evos[0].param == 1 &&
                        m.evos[0].target in poolIds && out.mons[m.evos[0].target]!!.growth == growth) {
                        "${m.const} evos ${m.evos.map { "${it.method}/${it.param}/${out.speciesName(it.target)}" }}"
                    }
                }
                val c = HnsEvoKaizoSweepTest.onCycles(out)
                expect(c.isEmpty()) { "${c.size} species evolve back into themselves, e.g. ${c.take(3).map { out.speciesName(it) }}" }
            }
        }
        if (o.evosMaxThreeStages && o.evolutionsMod == "RANDOM") check("at most three stages") {
            fun depth(m: HnsGame.Mon, seen: Set<Int>): Int = 1 + (evosFrom(m).filter { it.id !in seen }.maxOfOrNull { depth(it, seen + m.id) } ?: 0)
            for (m in poolMons) expect(depth(m, emptySet()) <= 3) { "${m.const} starts a line of ${depth(m, emptySet())}" }
        }
        if (o.removeTimeBasedEvolutions && o.evolutionsMod != "RANDOM_EVERY_LEVEL") check("no time-based evolutions") {
            for (m in out.mons.filterNotNull()) if (m.enabled) for (e in m.evos) if (e.paramsOk)
                expect(e.conds.none { it[0] in timeConds }) { "${m.const} -> ${out.speciesName(e.target)} still asks a time of day" }
        }
        if (o.makeEvolutionsEasier) check("evolutions easier") {
            val friendship = L.enumValue("IF", "IF_MIN_FRIENDSHIP")
            for (m in out.mons.filterNotNull()) if (m.enabled) for (e in m.evos) {
                if (e.method == evoLevel) expect(e.param <= 40) { "${m.const} evolves at ${e.param}" }
                if (e.paramsOk) for (c in e.conds) if (c[0] == friendship) expect(c[1] <= 160) { "${m.const} needs friendship ${c[1]}" }
            }
        }

        // ------------------------------------------------------------- moves
        if (o.randomizeMovePowers || o.randomizeMoveAccuracies || o.randomizeMovePPs || o.randomizeMoveTypes) check("move data") {
            val ids = (1 until out.movesCount)
            if (o.randomizeMovePowers) expect(ids.count { out.moves[it]!!.power != v.moves[it]!!.power } > 100) { "powers" }
            if (o.randomizeMoveAccuracies) expect(ids.count { out.moves[it]!!.accuracy != v.moves[it]!!.accuracy } > 50) { "accuracies" }
            if (o.randomizeMovePPs) expect(ids.count { out.moves[it]!!.pp != v.moves[it]!!.pp } > 100) { "PPs" }
            if (o.randomizeMoveTypes) expect(ids.count { out.moves[it]!!.type != v.moves[it]!!.type } > 100) { "types" }
        }
        if (o.movesetsMod != "UNCHANGED") check("movesets ${o.movesetsMod}") {
            val changed = poolMons.count { m -> m.learnset.map { it.move } != v.mons[m.id]!!.learnset.map { it.move } }
            expect(changed > poolMons.size * 9 / 10) { "$changed of ${poolMons.size} changed" }
            for (m in poolMons) {
                if (o.evolutionMovesForAll) expect(m.learnset.firstOrNull()?.level == 0) { "${m.const} has no evolution move" }
                if (o.startWithGuaranteedMoves) expect(m.learnset.count { it.level == 1 } >= o.guaranteedMoveCount) { "${m.const} level 1 moves" }
            }
        }
        if (o.tmsMod == "RANDOM") check("TMs random") {
            expect(out.machineMoves.take(out.tmCount) != v.machineMoves.take(v.tmCount)) { "TMs unchanged" }
        }
        check("TM compatibility ${o.tmCompatMod}, full HM ${o.fullHMCompat}") {
            val all = out.machineMoves.toSet()
            val hms = out.L.machines.filter { it.kind == "HM" }.map { it.move }.toSet()
            for (m in traitMons) {
                if (o.tmCompatMod == "FULL") expect(m.teachable.containsAll(all)) { "${m.const} lacks ${all - m.teachable.toSet()}" }
                if (o.fullHMCompat) expect(m.teachable.containsAll(hms)) { "${m.const} lacks an HM" }
            }
            if (o.tmCompatMod == "COMPLETELY_RANDOM") {
                val machines = all
                val changed = traitMons.count { m -> m.teachable.filter { it in machines }.toSet() != v.mons[m.id]!!.teachable.filter { it in machines }.toSet() }
                expect(changed > traitMons.size * 8 / 10) { "$changed of ${traitMons.size} changed" }
            }
        }
        if (o.tutorMovesMod == "RANDOM") check("tutor moves random (noted)") {
            expect(notes.any { it.contains("Move tutor moves were not randomized") }) { "neither applied nor noted" }
        }
        if (o.tutorCompatMod != "UNCHANGED") check("tutor compatibility ${o.tutorCompatMod}") {
            val machineSet = (v.machineMoves.toList() + out.machineMoves.toList()).toSet()
            val tutorsOf = traitMons.associate { m -> m.id to m.teachable.filter { it !in machineSet }.toSet() }
            val union = tutorsOf.values.flatten().toSet()
            expect(union.isNotEmpty()) { "no tutor moves" }
            if (o.tutorCompatMod == "FULL") for ((id, t) in tutorsOf) expect(t == union) { "${out.mons[id]!!.const} lacks ${union - t}" }
            else {
                val changed = traitMons.count { m -> tutorsOf[m.id] != v.mons[m.id]!!.teachable.filter { it !in machineSet }.toSet() }
                expect(changed > traitMons.size / 2) { "$changed changed" }
            }
        }

        // ------------------------------------------------------------- trainers
        val before = v.trainers.associateBy { it.id }
        val doubles = L.enumValue("TRAINER_BATTLE_TYPE", "TRAINER_BATTLE_TYPE_DOUBLES")
        val battleType = L.struct("Trainer").fields["battleType"]
        if (o.trainersLevelModified) check("trainer levels +${o.trainersLevelModifier}%") {
            for (t in out.trainers) {
                val was = before.getValue(t.id)
                val allowed = was.mons.map { modLevel(it.level, o.trainersLevelModifier) }.toSet()
                for (tm in t.mons) expect(tm.level in allowed) { "${t.constName} level ${tm.level} not in $allowed" }
                expect(t.mons.maxOf { it.level } == modLevel(was.mons.maxOf { it.level }, o.trainersLevelModifier)) { "${t.constName} top level" }
            }
        }
        check("trainer party sizes (boss +${o.additionalBossPokemon}, important +${o.additionalImportantPokemon}, regular +${o.additionalRegularPokemon}, doubles ${o.doubleBattleMode})") {
            for (t in out.trainers) {
                val was = before.getValue(t.id)
                if (was.poolSize != 0) { expect(t.mons.size == was.mons.size) { "${t.constName} pool party changed" }; continue }
                val add = when {
                    isBoss(out, t) -> o.additionalBossPokemon
                    isRival(t) -> if (isRival1(t)) 0 else o.additionalImportantPokemon
                    else -> o.additionalRegularPokemon
                }
                var want = minOf(6, maxOf(was.mons.size, minOf(6, was.mons.size + add)))
                if (o.doubleBattleMode && !isRival1(t) && want == 1) want = 2
                expect(t.mons.size == want) { "${t.constName} has ${t.mons.size}, want $want (was ${was.mons.size})" }
            }
        }
        if (o.doubleBattleMode) check("double battles, the first rival single") {
            expect(battleType != null && doubles != null) { "no battleType field" }
            for (t in out.trainers) {
                val bt = out.rom.get(battleType!!, t.addr)
                if (isRival1(t)) expect(bt == v.rom.get(battleType, t.addr) && bt != doubles) { "${t.constName} is doubles" }
                else if (before.getValue(t.id).poolSize == 0) expect(bt == doubles) { "${t.constName} battle type $bt" }
            }
            expect(out.trainers.count { isRival1(it) } == 3) { "first rival battles not found" }
        }
        if (o.smartAi) check("smarter trainer AI") {
            expect(!log.contains("Smarter trainer AI was not set")) { "the engine says it was not set" }
            val ai = L.struct("Trainer").fields["aiFlags"]
            expect(ai != null) { "Trainer.aiFlags not in layout" }
            for (t in out.trainers) expect(out.rom.read(t.addr + ai!!.offset, ai.size) == (v.rom.read(t.addr + ai.offset, ai.size) or 7L)) { "${t.constName} AI flags" }
        }
        if (o.trainersMod == "RANDOM") check("trainer Pokemon random") {
            var same = 0; var total = 0
            for (t in out.trainers) {
                val was = before.getValue(t.id)
                if (was.mons.size != t.mons.size) continue
                for (k in t.mons.indices) { total++; if (t.mons[k].species == was.mons[k].species) same++ }
            }
            expect(total > 300 && same < total / 5) { "$same of $total unchanged" }
        }
        if (o.trainersSimilarStrength) check("trainer Pokemon of similar strength") {
            var near = 0; var total = 0
            for (t in out.trainers) {
                val was = before.getValue(t.id)
                if (isRival(t) || was.mons.size != t.mons.size) continue
                for (k in t.mons.indices) {
                    val a = v.mons[was.mons[k].species]!!.origBst; val b = v.mons[t.mons[k].species]!!.origBst
                    total++; if (abs(a - b) <= a / 5) near++
                }
            }
            expect(total > 300 && near >= total * 8 / 10) { "$near of $total within 20%" }
        }
        if (o.trainersForceFullyEvolved) check("fully evolved from level ${o.trainersForceFullyEvolvedLevel}") {
            for (t in out.trainers) for (tm in t.mons) if (tm.level >= o.trainersForceFullyEvolvedLevel)
                expect(evosFrom(out.mons[tm.species]!!).isEmpty()) { "${t.constName}: ${out.speciesName(tm.species)} Lv${tm.level} can evolve" }
        }
        if (o.rivalCarriesStarter) check("rival carries the starter") {
            for ((i, n) in listOf("CHIKORITA", "CYNDAQUIL", "TOTODILE").withIndex()) {
                val family = HashSet<Int>()
                fun add(id: Int) { if (family.add(id)) out.mons[id]?.evos?.forEach { add(it.target) } }
                add(starters[i])
                for (k in 1..7) {
                    val t = out.trainers.firstOrNull { it.constName == "TRAINER_RIVAL_${n}_${k}_HNS" } ?: continue
                    expect(t.mons.any { it.species in family }) { "rival $n $k carries no ${out.speciesName(starters[i])}" }
                }
            }
        }
        if (o.heldItemsBoss || o.heldItemsImportant || o.heldItemsRegular) check("trainer held items") {
            val consumable = { id: Int ->
                out.items.getOrNull(id)?.pocket == L.enumValue("POCKET", "POCKET_BERRIES") || itemConst[id] in setOf("ITEM_WHITE_HERB", "ITEM_MENTAL_HERB", "ITEM_BERRY_JUICE")
            }
            for (t in out.trainers) {
                val rolled = if (isBoss(out, t)) o.heldItemsBoss else if (isRival(t)) o.heldItemsImportant else o.heldItemsRegular
                if (!rolled) continue
                val targets = if (o.highestLevelGetsItems) listOf(t.mons.maxBy { it.level }) else t.mons
                for (tm in targets) {
                    expect(tm.heldItem != 0) { "${t.constName}: ${out.speciesName(tm.species)} holds nothing" }
                    if (o.consumableItemsOnly) expect(consumable(tm.heldItem)) { "${t.constName} holds ${out.itemName(tm.heldItem)}" }
                    if (o.banLuckyEgg) expect(tm.heldItem != luckyEgg) { "${t.constName} holds a Lucky Egg" }
                }
            }
        }
        if (o.eliteFourUniquePokemon > 0) check("Elite Four unique Pokemon") {
            val aces = out.trainers.filter { isEliteOrChampion(out, it) }.map { t -> t.mons.sortedByDescending { it.level }.first().species }
            expect(aces.size >= 5 && aces.distinct().size == aces.size) { "aces ${aces.map { out.speciesName(it) }}" }
        }
        if (o.trainersBlockEarlyWonderGuard && wonderGuard != null) check("no early Wonder Guard") {
            for (t in out.trainers) for (tm in t.mons) if (tm.level < 20)
                expect(wonderGuard !in out.mons[tm.species]!!.abilities) { "${t.constName}: ${out.speciesName(tm.species)} Lv${tm.level}" }
        }
        if (o.randomizeTrainerNames) check("trainer names") {
            expect(o.trainerNames.isNotEmpty()) { "UPR's custom name list did not load" }
            val changed = out.trainers.count { it.name != before.getValue(it.id).name }
            expect(changed > 100) { "$changed names changed" }
        }
        if (o.randomizeTrainerClassNames) check("trainer class names") {
            expect(o.trainerClassNames.isNotEmpty()) { "UPR's custom class list did not load" }
            val changed = out.trainerClassNames.count { (c, n) -> n != v.trainerClassNames[c] }
            expect(changed > 10) { "$changed class names changed" }
        }

        // ------------------------------------------------------------- wild
        val wildBefore = v.wildSets
        check("wild tables line up") { expect(wildBefore.size == out.wildSets.size) { "${wildBefore.size} vs ${out.wildSets.size}" } }
        if (o.wildLevelsModified) check("wild levels +${o.wildLevelModifier}%") {
            for ((i, s) in out.wildSets.withIndex()) for ((k, slot) in s.slots.withIndex()) {
                val w = wildBefore[i].slots[k]
                if (w.species == 0) continue
                val min = modLevel(w.minLevel, o.wildLevelModifier).coerceAtLeast(1)
                val max = modLevel(w.maxLevel, o.wildLevelModifier).coerceAtLeast(min)
                expect(slot.minLevel == min && slot.maxLevel == max) { "${s.name} slot $k ${w.minLevel}-${w.maxLevel} -> ${slot.minLevel}-${slot.maxLevel}, want $min-$max" }
            }
        }
        if (o.wildMod != "UNCHANGED") check("wild Pokemon ${o.wildMod}") {
            var same = 0; var total = 0
            for ((i, s) in out.wildSets.withIndex()) for ((k, slot) in s.slots.withIndex()) {
                val w = wildBefore[i].slots[k]; if (w.species == 0) continue
                total++; if (slot.species == w.species) same++
            }
            expect(same < total / 10) { "$same of $total unchanged" }
            if (o.wildMod == "AREA_MAPPING") {
                val groups = out.wildSets.indices.groupBy { out.wildSets[it].areaKey }
                for ((key, idx) in groups) {
                    val map = HashMap<Int, Int>()
                    for (i in idx) for ((k, slot) in out.wildSets[i].slots.withIndex()) {
                        val w = wildBefore[i].slots[k].species; if (w == 0) continue
                        val prev = map.put(w, slot.species)
                        expect(prev == null || prev == slot.species) { "area $key maps ${out.speciesName(w)} two ways" }
                    }
                }
            }
        }
        if (o.wildRestrictionMod == "SIMILAR_STRENGTH") check("wild Pokemon of similar strength") {
            var near = 0; var total = 0
            for ((i, s) in out.wildSets.withIndex()) for ((k, slot) in s.slots.withIndex()) {
                val w = wildBefore[i].slots[k].species; if (w == 0) continue
                val a = v.mons[w]!!.origBst; val b = v.mons[slot.species]!!.origBst
                total++; if (abs(a - b) <= a / 5) near++
            }
            expect(near >= total * 8 / 10) { "$near of $total within 20%" }
        }
        check("wild BST and legendaries (limit ${o.wildPokemonBSTLimit}, legendary mode ${o.wildBSTLimitMode}, wild line $wildLine)") {
            for (s in out.wildSets) for (slot in s.slots) {
                if (slot.species == 0) continue
                val m = out.mons[slot.species]!!
                val orig = v.mons[slot.species]!!
                if (o.wildPokemonBSTLimit > 0) {
                    expect(orig.bst <= o.wildPokemonBSTLimit && m.bst <= o.wildPokemonBSTLimit) { "${s.name}: ${m.const} BST ${orig.bst}/${m.bst}" }
                }
                // Every wild Pokemon stays under the rule sheet's wild line, whatever the file's own limit.
                if (wildLine != null) expect(m.bst < wildLine) { "${s.name}: ${m.const} BST ${m.bst} >= wild line $wildLine" }
                if (o.blockWildLegendaries) {
                    val bad = when (o.wildBSTLimitMode) { 2 -> m.isStrongLegendary; 1 -> m.isLegendary; else -> m.isLegendary || m.ultraBeast || m.paradox }
                    expect(!bad) { "${s.name}: legendary ${m.const}" }
                }
            }
        }

        // ------------------------------------------------------------- statics, trades, items
        if (o.staticMod != "UNCHANGED") check("static Pokemon") {
            val vScripts = placed(v)["scripts"]!! + placed(v)["gift tables"]!!
            val oScripts = placedNow["scripts"]!! + placedNow["gift tables"]!!
            val changed = vScripts.indices.count { vScripts[it] != oScripts.getOrNull(it) }
            expect(changed > vScripts.size / 2) { "$changed of ${vScripts.size} statics changed" }
            if (o.staticLevelModified) expect(notes.any { it.contains("Static levels") }) { "static levels neither applied nor noted" }
        }
        if (o.tradesMod != "UNCHANGED") check("trades ${o.tradesMod}") {
            val vt = v.trades
            expect(out.trades.indices.count { out.trades[it].species != vt[it].species } > vt.size / 2) { "given species" }
            if (o.randomizeTradeNicknames) expect(out.trades.indices.count { out.trades[it].nickname != vt[it].nickname } > vt.size / 2) { "nicknames" }
            if (o.randomizeTradeOTs) expect(out.trades.indices.count { out.trades[it].otName != vt[it].otName } > vt.size / 2) { "OT names" }
        }
        if (o.fieldItemsMod != "UNCHANGED") check("field items") {
            val changed = out.fieldItems.indices.count { out.fieldItems[it].item != v.fieldItems[it].item }
            expect(changed > out.fieldItems.size / 2) { "$changed of ${out.fieldItems.size}" }
            if (o.banLuckyEgg) expect(out.fieldItems.none { it.item == luckyEgg && it.origItem != luckyEgg }) { "a Lucky Egg was rolled" }
            expect(log.contains("--PC Item--") || out.labTrashAddr == null) { "PC item not rolled" }
            if (o.banLuckyEgg) expect(out.labTrashItem != luckyEgg) { "PC Lucky Egg" }
        }
        if (o.pickupItemsMod != "UNCHANGED") check("Pickup items") {
            expect(out.pickup.isNotEmpty()) { "no Pickup table" }
            expect(out.pickup.indices.count { out.pickup[it].item != v.pickup[it].item } > out.pickup.size / 2) { "pickup unchanged" }
            if (o.banLuckyEgg) expect(out.pickup.none { it.item == luckyEgg }) { "Pickup Lucky Egg" }
        }
        if (o.shopItemsMod != "UNCHANGED") check("shop items (noted)") { expect(notes.any { it.contains("Shop items") }) { "neither applied nor noted" } }

        // ------------------------------------------------------------- misc tweaks: applied or said
        check("misc tweaks") {
            val handled = com.dabomstew.pkrandom.MiscTweak.BAN_LUCKY_EGG.value or
                (if (o.fieldItemsMod != "UNCHANGED") com.dabomstew.pkrandom.MiscTweak.RANDOMIZE_PC_POTION.value else 0)
            val left = o.miscTweaks and handled.inv()
            val silent = (0 until 31).filter { left and (1 shl it) != 0 }.filter { bit ->
                notes.none { it.contains("misc tweak", ignoreCase = true) && it.contains(tweakName(1 shl bit), ignoreCase = true) }
            }
            expect(silent.isEmpty()) { "set and silently dropped: ${silent.map { tweakName(1 shl it) }}" }
        }

        val ok = problems.isEmpty()
        println("HNSMODE|$mode|$pool|${if (ok) "PASS" else "FAIL"}|${problems.joinToString(" ;; ")}|NOTES: ${notes.joinToString(" ;; ")}")
        if (!ok) fail("$mode $pool:\n  " + problems.joinToString("\n  "))
    }

    /** The tweak's name as UPR's own window shows it ("Disable Low HP Music"). */
    private fun tweakName(value: Int): String =
        com.dabomstew.pkrandom.MiscTweak.allTweaks.firstOrNull { it.value == value }?.tweakName ?: "bit $value"
}

/**
 * The Nuzlocke path's own settings for Heart & Soul: "Nuzlocke fair" (NuzlockeFair, made by GameBuild exactly as the
 * Nuzlocke screen makes it), on both pools. Wild Pokemon and trainers' teams random and close in strength to what they
 * replace, levels and everything else as the game has them, and the ROM carries the Nuzlocke challenge preset.
 */
@RunWith(Parameterized::class)
class HnsNuzlockeFairTest(private val pool: HnsEngine.Pool) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = HnsEngine.Pool.entries.map { arrayOf<Any>(it) }
    }

    @Test
    fun `Nuzlocke fair randomizes wild and trainers by strength and nothing else`() {
        if (HnsEveryModeTest.romFile == null) { println("HnsNuzlockeFairTest skipped: no hns-kaizo.gba"); return }
        val kind = RomKind.HEARTSOUL_KAIZO_206
        val dir = Files.createTempDirectory("hnsfair").toFile()
        val problems = ArrayList<String>()
        fun check(name: String, block: () -> Unit) { try { block() } catch (e: Throwable) { problems += "$name: ${e.message}" } }
        fun expect(cond: Boolean, msg: () -> String) { if (!cond) throw AssertionError(msg()) }
        try {
            val built = GameBuild.build(kind, GameBuild.Plan(picks = NuzlockeFair.PICKS), null, dir)
            val file = File(dir, NuzlockeFair.fileName(kind)).apply { writeBytes(built.bytes) }
            val o = HnsEngine.readSettings(file)
            check("settings") {
                expect(o.wildMod == "RANDOM" && o.wildRestrictionMod == "SIMILAR_STRENGTH") { "wild ${o.wildMod} ${o.wildRestrictionMod}" }
                expect(o.trainersMod == "RANDOM" && o.trainersSimilarStrength) { "trainers ${o.trainersMod} ${o.trainersSimilarStrength}" }
                expect(!o.trainersLevelModified && !o.wildLevelsModified && o.startersMod == "UNCHANGED" && o.evolutionsMod == "UNCHANGED") { "more than wild and trainers" }
            }
            val made = HnsEveryModeTest.make(file, pool, HnsEveryModeTest.SEED)
            val rom = File(dir, "run.gba").apply { writeBytes(made.bytes) }
            HnsEngine.writePreset(rom, HnsEngine.Preset.NUZLOCKE, HnsEveryModeTest.assets)
            val bytes = rom.readBytes()
            val out = HnsEveryModeTest.read(bytes)
            val v = HnsEveryModeTest.vanilla
            check("ROM and log") { expect(made.sizeOk && made.logFileWritten) { "size ${made.sizeOk} log ${made.logFileWritten}" } }
            check("Nuzlocke challenge preset") {
                expect(HnsEveryModeTest.presetRegion(bytes).contentEquals(HnsEveryModeTest.presetRegion(HnsEveryModeTest.vanillaBytes))) { "not the build's Nuzlocke preset" }
            }
            check("levels as the game has them") {
                for (t in out.trainers) {
                    val was = v.trainers.first { it.id == t.id }
                    expect(t.mons.map { it.level } == was.mons.map { it.level }) { "${t.constName} levels" }
                }
                for ((i, s) in out.wildSets.withIndex()) for ((k, slot) in s.slots.withIndex()) {
                    val w = v.wildSets[i].slots[k]
                    expect(slot.minLevel == w.minLevel && slot.maxLevel == w.maxLevel) { "${s.name} levels" }
                }
            }
            check("starters, stats, evolutions and items as the game has them") {
                val L = out.L
                expect((0 until 3).all { out.rom.u16(L.sym("sStarterMon") + 2 * it) == v.rom.u16(L.sym("sStarterMon") + 2 * it) }) { "starters changed" }
                for (m in out.mons.filterNotNull()) if (m.enabled) {
                    val w = v.mons[m.id]!!
                    expect(m.stats.contentEquals(w.stats) && m.abilities.contentEquals(w.abilities) && m.types.contentEquals(w.types)) { "${m.const} traits" }
                    val now = m.evos.map { it.target }.toSet(); val was = w.evos.map { it.target }.toSet()
                    // A Vanilla run never evolves out of vanilla Emerald, whatever the settings: only those are dropped.
                    expect(if (pool == HnsEngine.Pool.NATDEX) now == was else was.containsAll(now)) { "${m.const} evolutions" }
                }
                expect(out.fieldItems.indices.all { out.fieldItems[it].item == v.fieldItems[it].item }) { "field items changed" }
            }
            check("trainers random, similar strength") {
                var near = 0; var total = 0; var same = 0
                for (t in out.trainers) {
                    val was = v.trainers.first { it.id == t.id }
                    for (k in t.mons.indices) {
                        val a = v.mons[was.mons[k].species]!!.origBst; val b = v.mons[t.mons[k].species]!!.origBst
                        total++; if (abs(a - b) <= a / 5) near++; if (t.mons[k].species == was.mons[k].species) same++
                    }
                }
                expect(same < total / 5 && near >= total * 8 / 10) { "$same unchanged, $near of $total within 20%" }
            }
            check("wild random, similar strength") {
                var near = 0; var total = 0; var same = 0
                for ((i, s) in out.wildSets.withIndex()) for ((k, slot) in s.slots.withIndex()) {
                    val w = v.wildSets[i].slots[k].species; if (w == 0) continue
                    val a = v.mons[w]!!.origBst; val b = v.mons[slot.species]!!.origBst
                    total++; if (abs(a - b) <= a / 5) near++; if (slot.species == w) same++
                }
                expect(same < total / 10 && near >= total * 8 / 10) { "$same unchanged, $near of $total within 20%" }
            }
            check("pool scope of what it randomizes") {
                // Nuzlocke fair changes wild Pokemon and trainers only; trades and gifts stay Heart & Soul's own (printed below).
                for ((where, ids) in HnsEveryModeTest.placed(out).filterKeys { it == "wild" || it == "trainers" }) for (id in ids) {
                    expect(out.mons[id]?.eligible == true) { "$where ${out.speciesName(id)} not placeable" }
                    if (pool == HnsEngine.Pool.VANILLA) expect(id in HnsEveryModeTest.vanillaScope) { "$where ${out.speciesName(id)}" }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
        if (HnsEveryModeTest.romFile != null) {
            val g = HnsEveryModeTest.vanilla
            val kept = HnsEveryModeTest.placed(g).filterKeys { it != "wild" && it != "trainers" && it != "starters" }
                .mapValues { (_, ids) -> ids.filter { it !in HnsEveryModeTest.vanillaScope }.map { g.speciesName(it) } }.filterValues { it.isNotEmpty() }
            println("HNSFAIRKEPT|$pool|left as Heart & Soul has them, outside Gen 1-3 base forms: $kept")
        }
        println("HNSMODE|Nuzlocke fair|$pool|${if (problems.isEmpty()) "PASS" else "FAIL"}|${problems.joinToString(" ;; ")}")
        if (problems.isNotEmpty()) fail("Nuzlocke fair $pool:\n  " + problems.joinToString("\n  "))
    }
}

/**
 * Evo Kaizo swept over seeds, both pools. Two rulings of 2026-10-06:
 * - The wild line (rules audit): the engine took Evo Kaizo's own-Pokemon line (601) for the wild too, while the rule
 *   sheet bans a wild Pokemon from 599 on Vanilla and 600 on Nat. Dex (BstRule.Lines.wild), so a species ending at BST
 *   599 could sit in Vanilla's grass with the tracker's X on it.
 * - No evolution loops (Blake, matching the Emerald Nat. Dex Evo Kaizo rules, "There are no evo loops"): no species may
 *   evolve back into one it came from, directly or through a chain.
 */
@RunWith(Parameterized::class)
class HnsEvoKaizoSweepTest(private val pool: HnsEngine.Pool) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = HnsEngine.Pool.entries.map { arrayOf<Any>(it) }
        val SEEDS = 1L..12L

        /** The species of [g] that can evolve back into themselves, following every evolution. */
        fun onCycles(g: HnsGame): List<Int> {
            val out = ArrayList<Int>()
            val state = IntArray(g.numSpecies) // 0 new, 1 on the stack, 2 done
            fun targets(id: Int) = g.mons[id]?.evos?.map { it.target }?.filter { g.mons.getOrNull(it)?.enabled == true }.orEmpty()
            for (start in 0 until g.numSpecies) {
                if (g.mons[start]?.enabled != true || state[start] != 0) continue
                // Iterative DFS: a target found on the stack closes a loop.
                val stack = ArrayDeque<Pair<Int, Iterator<Int>>>()
                state[start] = 1; stack.addLast(start to targets(start).iterator())
                while (stack.isNotEmpty()) {
                    val (id, it) = stack.last()
                    if (it.hasNext()) {
                        val t = it.next()
                        when (state[t]) {
                            0 -> { state[t] = 1; stack.addLast(t to targets(t).iterator()) }
                            1 -> out += t
                        }
                    } else { state[id] = 2; stack.removeLast() }
                }
            }
            return out
        }
    }

    @Test
    fun `Evo Kaizo has no evolution loop and no wild Pokemon at the wild line, over a seed sweep`() {
        if (HnsEveryModeTest.romFile == null) { println("HnsEvoKaizoSweepTest skipped: no hns-kaizo.gba"); return }
        val file = File(HnsEveryModeTest.presets, "RSE NatDex v1.2 Evo Kaizo.rnqs")
        val line = BstRule.lines("evokaizo", natDex = pool == HnsEngine.Pool.NATDEX)!!.wild
        val wildHits = ArrayList<String>()
        val loops = ArrayList<String>()
        var near = 0
        for (seed in SEEDS) {
            val out = HnsEveryModeTest.read(HnsEveryModeTest.make(file, pool, seed).bytes)
            val seen = HashSet<Int>()
            for (s in out.wildSets) for (slot in s.slots) if (slot.species != 0 && seen.add(slot.species)) {
                val bst = out.mons[slot.species]!!.bst
                if (bst >= line - 3) near++
                if (bst >= line) wildHits += "seed $seed: ${out.speciesName(slot.species)} BST $bst"
            }
            val c = onCycles(out)
            if (c.isNotEmpty()) loops += "seed $seed: ${c.size} loops, e.g. ${c.take(3).map { out.speciesName(it) }}"
        }
        val v = HnsEveryModeTest.vanilla
        val reach = v.mons.filterNotNull().filter { it.eligible && (pool == HnsEngine.Pool.NATDEX || it.id in HnsEveryModeTest.vanillaScope) &&
            it.origBst in (line - 6) until 600 }.map { "${it.displayName} ${it.origBst}" }
        println("HNSEVOREACH|$pool|pool species of BST ${line - 6} to 599, the only ones stats can carry to the line: $reach")
        println("HNSEVOSWEEP|$pool|wild line $line: ${wildHits.size} at or over, $near within 3|loops: ${loops.size} of ${SEEDS.count()} seeds|${(wildHits + loops).joinToString(" ;; ")}")
        assertTrue(wildHits.isEmpty() && loops.isEmpty(), "$pool: wild at or over BST $line: $wildHits; evolution loops: $loops")
    }
}
