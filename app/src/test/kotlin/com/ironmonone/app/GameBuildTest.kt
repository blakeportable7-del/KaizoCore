package com.ironmonone.app

import com.ironmonone.app.engine.GameFacts
import com.ironmonone.core.RomKind
import com.ironmonone.editor.Option
import com.ironmonone.editor.SettingsReflector
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.dabomstew.pkrandom.Settings as NdSettings
import com.dabomstew.pkrandomzx.Settings as ZxSettings

/**
 * "Build your own" (2026-09-29): the choices a player makes come out as exactly those fields of the
 * engine's own Settings, read back the way the engine reads a settings file, and nothing else moves.
 *
 * The oracle here is the engine's typed getters (ZxSettings.getWildPokemonMod and the rest), not the
 * reflective options GameBuild sets fields through, so a wrong mapping cannot agree with itself.
 * The ROM-backed half is GameBuildRomTest.
 */
class GameBuildTest {
    private val presets = File("src/main/assets/presets")
    private fun preset(name: String) = File(presets, name)

    private val fireRed = RomKind.FIRERED_U_V11
    private val emerald = RomKind.EMERALD_U
    private val natFire = RomKind.FIRERED_NATDEX_121
    private val natEmerald = RomKind.EMERALD_NATDEX_121

    /** A game's facts as its ROM reports them, without a ROM: numbered species, the starters, what the game has. */
    private fun facts(count: Int, own: List<Int>, abilities: Boolean = true, tutors: Boolean = true, name: (Int) -> String = { "SPECIES$it" }) =
        GameFacts.Facts((1..count).map { GameFacts.Species(it, name(it)) }, own, abilities, tutors)

    private val frlg = facts(386, listOf(1, 4, 7))
    private val rse = facts(386, listOf(252, 255, 258))
    private val natFacts = facts(1258, listOf(1, 4, 7))

    private val dirs = ArrayList<File>()
    private fun newStore(): PrepStore = PrepStore(Files.createTempDirectory("gamebuild").toFile().also { dirs += it })

    @AfterTest
    fun cleanUp() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun zx(f: File): ZxSettings = FileInputStream(f).use { ZxSettings.read(it) }
    private fun nd(f: File): NdSettings = FileInputStream(f).use { NdSettings.read(it) }

    private fun zxBytes(bytes: ByteArray): ZxSettings {
        val f = File.createTempFile("zxbytes", ".rnqs")
        try { f.writeBytes(bytes); return zx(f) } finally { f.delete() }
    }

    private fun ndBytes(bytes: ByteArray): NdSettings {
        val f = File.createTempFile("ndbytes", ".rnqs")
        try { f.writeBytes(bytes); return nd(f) } finally { f.delete() }
    }

    private fun valueOf(o: Option, on: Any): String = when (o) {
        is Option.Bool -> o.get(on).toString()
        is Option.IntValue -> o.get(on).toString()
        is Option.Choice -> o.get(on)
    }

    // ------------------------------------------------------------------ the fields are real

    @Test
    fun `every field the builder sets is a real setting of both engines and takes the value it is given`() {
        var checked = 0
        for (kind in listOf(fireRed, natFire)) {
            val cls = GameBuild.settingsClass(kind)
            val choices = GameBuild.choices(kind, null)
            assertEquals(14, choices.size, "${kind.id}: with no facts every choice is offered")
            for (c in choices) for (p in c.picks) for (a in p.sets) {
                val s = GameBuild.blank(kind, null)
                GameBuild.set(cls, s, a.field, a.value)
                assertEquals(a.value, GameBuild.get(cls, s, a.field), "${kind.id} ${c.id}/${p.id}: ${a.field}")
                checked++
            }
        }
        assertTrue(checked > 100, "only $checked assignments were checked")
    }

    @Test
    fun `a field that is not a setting is refused by name and never made up`() {
        val cls = GameBuild.settingsClass(fireRed)
        val e = assertFailsWith<IllegalStateException> { GameBuild.get(cls, GameBuild.blank(fireRed, null), "wildPokemonBoost") }
        assertTrue("wildPokemonBoost" in e.message.orEmpty(), e.message)
    }

    // ------------------------------------------------------------------ what was chosen is what the file holds

    @Test
    fun `FireRed on ZX holds exactly the starters and answers that were chosen`() {
        val plan = GameBuild.Plan(
            starters = GameBuild.Starters.Pick(listOf(25, null, 152)),
            picks = mapOf(
                "wild" to "similar", "wildLevels" to "30", "trainers" to "random", "trainerLevels" to "50", "items" to "shuffle",
                "types" to "completely_random", "abilities" to "randomize", "evolutions" to "random",
                "movesets" to "random_prefer_same_type", "moveStats" to "all", "tmMoves" to "random", "tmCompat" to "full",
                "tutorMoves" to "random", "tutorCompat" to "random_prefer_type",
            ),
        )
        val saved = GameBuild.save(newStore(), fireRed, plan, frlg, "Pikachu run").getOrThrow()
        val s = zx(saved)
        assertEquals("CUSTOM", s.startersMod.name)
        assertContentEquals(intArrayOf(26, 1, 153), s.customStarters, "Pikachu, random, Chikorita, each stored as its number plus one")
        assertEquals("RANDOM", s.wildPokemonMod.name)
        assertEquals("SIMILAR_STRENGTH", s.wildPokemonRestrictionMod.name)
        assertTrue(s.isWildLevelsModified); assertEquals(30, s.wildLevelModifier)
        assertEquals("RANDOM", s.trainersMod.name)
        assertFalse(s.isTrainersUsePokemonOfSimilarStrength)
        assertTrue(s.isTrainersLevelModified); assertEquals(50, s.trainersLevelModifier)
        assertEquals("SHUFFLE", s.fieldItemsMod.name)
        assertEquals("COMPLETELY_RANDOM", s.typesMod.name)
        assertEquals("RANDOMIZE", s.abilitiesMod.name)
        assertEquals("RANDOM", s.evolutionsMod.name)
        assertEquals("RANDOM_PREFER_SAME_TYPE", s.movesetsMod.name)
        assertTrue(s.isRandomizeMovePowers && s.isRandomizeMoveAccuracies && s.isRandomizeMovePPs && s.isRandomizeMoveTypes)
        assertEquals("RANDOM", s.tmsMod.name)
        assertEquals("FULL", s.tmsHmsCompatibilityMod.name)
        assertEquals("RANDOM", s.moveTutorMovesMod.name)
        assertEquals("RANDOM_PREFER_TYPE", s.moveTutorsCompatibilityMod.name)
    }

    @Test
    fun `Emerald on ZX holds a different set of answers just as exactly`() {
        val plan = GameBuild.Plan(
            starters = GameBuild.Starters.Pick(listOf(null, 149, 150)),
            picks = mapOf(
                "wild" to "random", "wildLevels" to "0", "trainers" to "similar", "trainerLevels" to "10", "items" to "random_even",
                "types" to "random_follow_evolutions", "abilities" to "unchanged", "evolutions" to "random_every_level",
                "movesets" to "metronome_only", "moveStats" to "stats", "tmMoves" to "unchanged", "tmCompat" to "random_prefer_type",
                "tutorMoves" to "unchanged", "tutorCompat" to "completely_random",
            ),
        )
        val saved = GameBuild.save(newStore(), emerald, plan, rse, "Dragons").getOrThrow()
        val s = zx(saved)
        assertEquals("CUSTOM", s.startersMod.name)
        assertContentEquals(intArrayOf(1, 150, 151), s.customStarters, "random, Dragonite, Mewtwo")
        assertEquals("RANDOM", s.wildPokemonMod.name)
        assertEquals("NONE", s.wildPokemonRestrictionMod.name)
        assertFalse(s.isWildLevelsModified); assertEquals(0, s.wildLevelModifier)
        assertEquals("RANDOM", s.trainersMod.name)
        assertTrue(s.isTrainersUsePokemonOfSimilarStrength)
        assertTrue(s.isTrainersLevelModified); assertEquals(10, s.trainersLevelModifier)
        assertEquals("RANDOM_EVEN", s.fieldItemsMod.name)
        assertEquals("RANDOM_FOLLOW_EVOLUTIONS", s.typesMod.name)
        assertEquals("UNCHANGED", s.abilitiesMod.name)
        assertEquals("RANDOM_EVERY_LEVEL", s.evolutionsMod.name)
        assertEquals("METRONOME_ONLY", s.movesetsMod.name)
        assertTrue(s.isRandomizeMovePowers && s.isRandomizeMoveAccuracies && s.isRandomizeMovePPs)
        assertFalse(s.isRandomizeMoveTypes, "\"random power, accuracy and PP\" leaves the types alone")
        assertEquals("UNCHANGED", s.tmsMod.name)
        assertEquals("RANDOM_PREFER_TYPE", s.tmsHmsCompatibilityMod.name)
        assertEquals("UNCHANGED", s.moveTutorMovesMod.name)
        assertEquals("COMPLETELY_RANDOM", s.moveTutorsCompatibilityMod.name)
    }

    @Test
    fun `random starters and the game's own are their own settings, not custom picks`() {
        val random = zxBytes(GameBuild.build(fireRed, GameBuild.Plan(starters = GameBuild.Starters.Random), frlg).bytes)
        assertEquals("COMPLETELY_RANDOM", random.startersMod.name)
        // Kaizo's starters are random; asking for the game's own makes them the game's own.
        val own = zxBytes(GameBuild.build(fireRed, GameBuild.Plan(base = preset("FRLG Kaizo.rnqs"), starters = GameBuild.Starters.Own), frlg).bytes)
        assertEquals("UNCHANGED", own.startersMod.name)
    }

    @Test
    fun `an answer above zero turns the level change on and zero turns it off, even over a mode that had it on`() {
        val kaizo = preset("FRLG Kaizo.rnqs")
        assertTrue(zx(kaizo).isTrainersLevelModified && zx(kaizo).trainersLevelModifier == 50, "the premise: Kaizo raises trainer levels 50%")
        val off = zxBytes(GameBuild.build(fireRed, GameBuild.Plan(base = kaizo, picks = mapOf("trainerLevels" to "0", "wildLevels" to "20")), frlg).bytes)
        assertFalse(off.isTrainersLevelModified); assertEquals(0, off.trainersLevelModifier)
        assertTrue(off.isWildLevelsModified); assertEquals(20, off.wildLevelModifier)
    }

    @Test
    fun `the level boost stops where each randomizer's own slider does`() {
        assertEquals(50, GameBuild.maxLevelBoost(fireRed))
        assertEquals(60, GameBuild.maxLevelBoost(natFire))
        fun steps(kind: RomKind) = GameBuild.choices(kind, null).single { it.id == "trainerLevels" }.picks.map { it.id.toInt() }
        assertEquals(listOf(0, 10, 20, 30, 40, 50), steps(emerald))
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60), steps(natEmerald))
        // The slider limits are in each engine's own form file: the app must not offer more than the engine's own screen does.
        for ((form, max) in listOf("../engine-zx/src/com/dabomstew/pkrandomzx/newgui/NewRandomizerGUI.form" to 50, "../engine-natdex/src/com/dabomstew/pkrandom/newgui/NewRandomizerGUI.form" to 60)) {
            val text = File(form).readText()
            val at = text.indexOf("binding=\"tpPercentageLevelModifierSlider\"")
            assertTrue(at > 0, form)
            val block = text.substring(at, text.indexOf("</component>", at))
            assertTrue("<maximum value=\"$max\"/>" in block, "$form: the trainer level slider does not stop at $max")
        }
    }

    @Test
    fun `a game with two starters takes two picks, and a game with none read gives no picks at all`() {
        val yellow = facts(151, listOf(25, 133), abilities = false, tutors = false)
        val s = zxBytes(GameBuild.build(RomKind.YELLOW_U, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(1, null))), yellow).bytes)
        assertEquals("CUSTOM", s.startersMod.name)
        assertContentEquals(intArrayOf(2, 1, 1), s.customStarters, "Bulbasaur, random, and the unused third slot left random")
        assertFailsWith<IllegalArgumentException> {
            GameBuild.build(RomKind.YELLOW_U, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(1, 2, 3))), yellow)
        }
        assertFailsWith<IllegalArgumentException> {
            GameBuild.build(fireRed, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(1, 2))), frlg)
        }
        // With no facts (the ROM could not be read) picks are refused in words, and everything else still builds.
        val e = assertFailsWith<IllegalArgumentException> {
            GameBuild.build(fireRed, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(1, 2, 3))), null)
        }
        assertTrue(e.message.orEmpty().endsWith("."), e.message)
        assertNotNull(GameBuild.build(fireRed, GameBuild.Plan(picks = mapOf("wild" to "random")), null))
    }

    // ------------------------------------------------------------------ what was not chosen is the base's

    @Test
    fun `whatever is not chosen keeps the value the official mode has`() {
        val kaizo = preset("FRLG Kaizo.rnqs")
        val kaizoBefore = kaizo.readBytes()
        val plan = GameBuild.Plan(base = kaizo, starters = GameBuild.Starters.Pick(listOf(25, null, 152)), picks = mapOf("wild" to "unchanged"))
        val built = zxBytes(GameBuild.build(fireRed, plan, frlg).bytes)
        assertContentEquals(kaizoBefore, kaizo.readBytes(), "building over a mode must never write to the mode's own file")

        // The same three changes made through the engine's own typed setters, and nothing else.
        val expected = zx(kaizo)
        expected.setStartersMod(false, true, false, false)
        expected.setCustomStarters(intArrayOf(26, 1, 153))
        expected.setWildPokemonMod(true, false, false, false)
        expected.setWildPokemonRestrictionMod(true, false, false, false)
        assertEquals(expected.toString(), built.toString(), "a byte of the settings differs from Kaizo's beyond the three changes")

        // Said the other way round, through the editor's own option list: exactly these fields differ.
        val differing = SettingsReflector.options(ZxSettings::class.java).filter { valueOf(it, built) != valueOf(it, zx(kaizo)) }.map { it.id }.toSet()
        assertEquals(setOf("startersMod", "wildPokemonMod"), differing)
        assertEquals(zx(kaizo).currentMiscTweaks, built.currentMiscTweaks, "the misc tweaks, which the builder does not show, are Kaizo's")
        assertEquals(zx(kaizo).romName, built.romName)
    }

    @Test
    fun `with nothing chosen every bundled mode comes out as the same settings it went in as`() {
        val files = presets.listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }.filter { !RnqsInfo.of(it).appliedByApp }
        assertTrue(files.size >= 70, "${files.size} presets")
        for (f in files) {
            val kind = RomKind.all.firstOrNull { k -> RulesetCatalog.forRom(k, files).any { it.preset == f || f in it.alternatives } }
            assertNotNull(kind, "${f.name} is offered for no game")
            val built = GameBuild.build(kind, GameBuild.Plan(base = f), null)
            val original = FileInputStream(f).use { input ->
                built.cls.getMethod("read", FileInputStream::class.java).invoke(null, input)
            }
            assertEquals(original.toString(), built.settings.toString(), f.name)
        }
    }

    @Test
    fun `a mode of the other engine cannot be the starting point, and a failed save writes nothing`() {
        val store = newStore()
        val natKaizo = preset("FRLG NatDex v1.2 Kaizo.rnqs")
        val failed = GameBuild.save(store, fireRed, GameBuild.Plan(base = natKaizo), frlg, "wrong engine")
        assertTrue(failed.isFailure)
        assertTrue(store.listSettings().isEmpty(), "a failed save left ${store.listSettings()}")
        assertTrue(store.cacheDirFor().listFiles().orEmpty().isEmpty(), "a failed save left scratch files behind")
        val vanillaKaizo = preset("FRLG Kaizo.rnqs")
        assertTrue(GameBuild.save(store, natFire, GameBuild.Plan(base = vanillaKaizo), natFacts, "wrong engine").isFailure)
        assertTrue(store.listSettings().isEmpty())
        // The same store still saves a good one afterwards, and its scratch space is clean too.
        assertTrue(GameBuild.save(store, fireRed, GameBuild.Plan(), frlg, "fine").isSuccess)
        assertTrue(store.cacheDirFor().listFiles().orEmpty().isEmpty(), "a save left scratch files behind")
    }

    @Test
    fun `the game as it is has nothing randomized, on both engines`() {
        val z = zxBytes(GameBuild.build(fireRed, GameBuild.Plan(), frlg).bytes)
        assertEquals(
            listOf("UNCHANGED", "UNCHANGED", "NONE", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED", "UNCHANGED"),
            listOf(
                z.startersMod.name, z.wildPokemonMod.name, z.wildPokemonRestrictionMod.name, z.trainersMod.name, z.typesMod.name, z.abilitiesMod.name,
                z.evolutionsMod.name, z.movesetsMod.name, z.tmsMod.name, z.tmsHmsCompatibilityMod.name, z.moveTutorMovesMod.name,
                z.moveTutorsCompatibilityMod.name, z.fieldItemsMod.name,
            ),
        )
        assertFalse(z.isWildLevelsModified || z.isTrainersLevelModified || z.isTrainersUsePokemonOfSimilarStrength)
        assertFalse(z.isRandomizeMovePowers || z.isRandomizeMoveAccuracies || z.isRandomizeMovePPs || z.isRandomizeMoveTypes)
        assertEquals(0, z.currentMiscTweaks)
        // The custom starters wait on the game's own, as the desktop's dropdowns do, and match the official presets' own.
        assertContentEquals(intArrayOf(2, 5, 8), z.customStarters)
        assertContentEquals(zx(preset("FRLG Kaizo.rnqs")).customStarters, z.customStarters)

        val n = ndBytes(GameBuild.build(natEmerald, GameBuild.Plan(), facts(1258, listOf(252, 255, 258))).bytes)
        assertEquals("UNCHANGED", n.startersMod.name)
        assertEquals("UNCHANGED", n.wildPokemonMod.name)
        assertEquals("UNCHANGED", n.trainersMod.name)
        assertFalse(n.isTrainersLevelModified || n.isWildLevelsModified)
        assertContentEquals(nd(preset("RSE NatDex v1.2 Kaizo.rnqs")).customStarters, n.customStarters)
    }

    // ------------------------------------------------------------------ where it lands

    @Test
    fun `the file lands in the folder the Run tab lists settings from, paired with its game`() {
        val store = newStore()
        val plan = GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(25, 6, 9)), picks = mapOf("wild" to "random"))
        val saved = GameBuild.save(store, fireRed, plan, frlg, "Pikachu run").getOrThrow()
        assertEquals("FRLG (Pikachu run).rnqs", saved.name)
        assertTrue(saved in store.listSettings(), "RUN reads store.listSettings()")
        assertEquals(store.settingsFile("x").parentFile.canonicalFile, saved.parentFile.canonicalFile)
        assertEquals(saved.canonicalFile, store.settingsFile(saved.name).canonicalFile)
        assertTrue(saved.length() > 0)
        // RUN's settings list shows a file for a game when the game and the engine both match.
        assertTrue(RulesetCatalog.isCompatible(fireRed, saved))
        assertTrue(RulesetCatalog.isCompatible(RomKind.LEAFGREEN_U, saved), "FireRed and LeafGreen share FRLG")
        assertFalse(RulesetCatalog.isCompatible(emerald, saved), "not another family")
        assertFalse(RulesetCatalog.isCompatible(natFire, saved), "not the Nat. Dex build of the same game")
        // The pairing survives the sidecar being lost, as it does when the file is sent to another phone.
        val elsewhere = File(saved.parentFile.parentFile, "elsewhere").apply { mkdirs() }
        val copy = saved.copyTo(File(elsewhere, saved.name))
        assertTrue(RulesetCatalog.isCompatible(fireRed, copy))
        // And the same name a second time is a second file, never the first one written over.
        val again = GameBuild.save(store, fireRed, plan, frlg, "Pikachu run").getOrThrow()
        assertEquals("FRLG (Pikachu run) (2).rnqs", again.name)
        assertTrue(saved.isFile && again.isFile)
    }

    @Test
    fun `a build named like a mode never takes that mode's place in the Mode row`() {
        val store = newStore()
        val official = store.settingsFile("FRLG Kaizo.rnqs").apply { parentFile.mkdirs() }
        preset("FRLG Kaizo.rnqs").copyTo(official)
        val kaizoMode = RulesetCatalog.forRom(fireRed, store.listSettings()).single { it.key == "kaizo" }
        assertEquals(official, kaizoMode.preset)
        // A build called "Kaizo", and one that starts from Kaizo: both are behind the official file.
        val plain = GameBuild.save(store, fireRed, GameBuild.Plan(), frlg, "Kaizo").getOrThrow()
        val fromKaizo = GameBuild.save(store, fireRed, GameBuild.Plan(base = official), frlg, "Kaizo").getOrThrow()
        assertEquals("FRLG (Kaizo).rnqs", plain.name)
        assertEquals("FRLG Kaizo (Kaizo).rnqs", fromKaizo.name)
        val mode = RulesetCatalog.forRom(fireRed, store.listSettings()).single { it.key == "kaizo" }
        assertEquals(official, mode.preset, "the official file is still what the Kaizo mode runs")
        assertTrue(plain in mode.alternatives && fromKaizo in mode.alternatives)
        // The one that started from Kaizo says so in its sidecar; the plain one has no mode.
        assertEquals("kaizo", RnqsInfo.of(fromKaizo).ruleset)
        assertEquals("kaizo", RnqsInfo.of(plain).ruleset, "its name has the word, which is all the Run tab reads")
    }

    @Test
    fun `the sidecar carries the game, the engine and the starting mode, so a file renamed without them still pairs`() {
        val store = newStore()
        val kaizo = store.settingsFile("FRLG Kaizo.rnqs").also { preset("FRLG Kaizo.rnqs").copyTo(it) }
        val fromKaizo = GameBuild.save(store, fireRed, GameBuild.Plan(base = kaizo), frlg, "mine").getOrThrow()
        val plain = GameBuild.save(store, fireRed, GameBuild.Plan(), frlg, "plain").getOrThrow()
        val nat = GameBuild.save(store, natFire, GameBuild.Plan(), natFacts, "big").getOrThrow()
        fun sidecar(f: File) = RnqsInfo.metaFile(f).readLines().toSet()
        assertEquals(setOf("family=FRLG", "natdex=false", "ruleset=kaizo"), sidecar(fromKaizo))
        assertEquals(setOf("family=FRLG", "natdex=false"), sidecar(plain), "no mode was started from, so none is claimed")
        assertEquals(setOf("family=FRLG", "natdex=true"), sidecar(nat))
        // Renamed by hand to a name with none of the tokens, with its sidecar beside it: still this game's, and only this game's.
        val dir = File(store.settingsFile("x").parentFile.parentFile, "renamed").apply { mkdirs() }
        val renamed = plain.copyTo(File(dir, "Dragon night.rnqs"))
        assertFalse(RulesetCatalog.isCompatible(fireRed, renamed), "without its sidecar the name says nothing")
        RnqsInfo.metaFile(plain).copyTo(RnqsInfo.metaFile(renamed))
        assertTrue(RulesetCatalog.isCompatible(fireRed, renamed))
        assertFalse(RulesetCatalog.isCompatible(emerald, renamed))
        val natRenamed = nat.copyTo(File(dir, "Big dex night.rnqs"))
        RnqsInfo.metaFile(nat).copyTo(RnqsInfo.metaFile(natRenamed))
        assertTrue(RulesetCatalog.isCompatible(natFire, natRenamed))
        assertFalse(RulesetCatalog.isCompatible(fireRed, natRenamed))
    }

    @Test
    fun `a saved build shows under its own name in the Run tab's settings list, never as the game and a question mark`() {
        val store = newStore()
        val kaizo = store.settingsFile("FRLG Kaizo.rnqs").also { preset("FRLG Kaizo.rnqs").copyTo(it) }
        val plain = GameBuild.save(store, fireRed, GameBuild.Plan(), frlg, "Pikachu run").getOrThrow()
        val fromKaizo = GameBuild.save(store, fireRed, GameBuild.Plan(base = kaizo), frlg, "Pikachu run").getOrThrow()
        val nat = GameBuild.save(store, natFire, GameBuild.Plan(), natFacts, "Big dex").getOrThrow()
        val files = store.listSettings()
        val shown = RnqsInfo.displayLabelsFor(files)
        fun row(f: File) = shown[files.indexOf(f)]
        assertEquals("FRLG (Pikachu run)" to "FRLG", row(plain))
        assertEquals("FRLG NatDex (Big dex)" to "FRLG Nat. Dex", row(nat))
        // Beside the official Kaizo, whose label it shares, both are told apart by their own names.
        assertEquals("FRLG Kaizo" to "FRLG Kaizo", row(kaizo))
        assertEquals("FRLG Kaizo (Pikachu run)" to "FRLG Kaizo", row(fromKaizo))
        assertTrue(shown.none { "?" in it.first || "?" in it.second }, shown.toString())
        assertEquals(shown.size, shown.map { it.first }.toSet().size, "two rows with one title: $shown")
    }

    @Test
    fun `a saved build is custom to the extra passes, which stay off until the player switches them on`() {
        val store = newStore()
        val bundled = presets.listFiles { f -> f.extension == "rnqs" }!!.associate { it.name to it.readBytes() }
        val official = preset("RSE Kaizo.rnqs")
        assertTrue(ExtraPasses.prePassByDefault(emerald, official.name, official.readBytes(), bundled), "the premise: official Kaizo takes the 60% levels")
        val plan = GameBuild.Plan(base = official, starters = GameBuild.Starters.Pick(listOf(252, null, 258)))
        val saved = GameBuild.save(store, emerald, plan, rse, "Kaizo, my starters").getOrThrow()
        assertNull(ExtraPasses.officialName(saved.name, saved.readBytes(), bundled), "a build is not an official preset")
        assertFalse(ExtraPasses.prePassByDefault(emerald, saved.name, saved.readBytes(), bundled))
    }

    // ------------------------------------------------------------------ the name

    @Test
    fun `the name is made safe for a file, on a phone and on a PC`() {
        val cases = listOf(
            "Blake's Emerald run" to "Blake's Emerald run",
            "  spaced    out  " to "spaced out",
            "../../etc/passwd" to "etc passwd",
            "..\\..\\windows\\system32" to "windows system32",
            "a<b>c:d*e?f\"g|h" to "a b c d e f g h",
            "Run (v2)" to "Run [v2]",
            "my game.rnqs" to "my game",
            "MY GAME.RNQS" to "MY GAME",
            "tab\tand\nnewline\u0000nul" to "tab and newline nul",
            "...dots..." to "dots",
            "Pokémon night" to "Pokémon night",
        )
        for ((typed, clean) in cases) assertEquals(clean, GameBuild.cleanName(typed), "\"$typed\"")
        for (empty in listOf("", "   ", "///", "...", ".rnqs", "\\/:*?\"<>|", "\u0000\u0001")) {
            assertNull(GameBuild.cleanName(empty), "\"$empty\" leaves nothing")
            assertNull(GameBuild.fileName(fireRed, null, empty))
        }
    }

    @Test
    fun `a long name is cut without splitting a character and a hostile one stays inside the settings folder`() {
        val long = GameBuild.cleanName("a".repeat(100))!!
        assertEquals(GameBuild.NAME_LIMIT, long.length)
        // A character outside the basic plane is two chars: cutting between them would leave half of one.
        val cut = GameBuild.cleanName("a".repeat(GameBuild.NAME_LIMIT - 1) + "😀" + "b")!!
        assertFalse(cut.any { Character.isSurrogate(it) }, "a lone half of a character was left at the cut")
        val fits = GameBuild.cleanName("a".repeat(GameBuild.NAME_LIMIT - 2) + "😀" + "b")!!
        assertTrue(fits.contains("😀"), "a character that fits is kept whole")

        val store = newStore()
        val saved = GameBuild.save(store, emerald, GameBuild.Plan(), rse, "../../../evil/../name").getOrThrow()
        assertEquals(store.settingsFile("x").parentFile.canonicalFile, saved.parentFile.canonicalFile)
        assertEquals("RSE (evil .. name).rnqs", saved.name)
        val nothing = GameBuild.save(store, emerald, GameBuild.Plan(), rse, "///")
        assertTrue(nothing.isFailure)
        assertEquals("Give your game a name.", PresetStrings.plain(nothing.exceptionOrNull()!!, "Could not save your game"))
        assertEquals(1, store.listSettings().size, "a refused name wrote nothing")
    }

    @Test
    fun `the file name carries the game, Nat Dex and the starting mode in front, as the editor's copies do`() {
        assertEquals("FRLG (my build).rnqs", GameBuild.fileName(fireRed, null, "my build"))
        assertEquals("RSE Kaizo (my build).rnqs", GameBuild.fileName(emerald, "kaizo", "my build"))
        assertEquals("FRLG NatDex (my build).rnqs", GameBuild.fileName(natFire, null, "my build"))
        assertEquals("RSE NatDex Super Kaizo (x).rnqs", GameBuild.fileName(natEmerald, "superkaizo", "x"))
        for (k in listOf(fireRed, emerald, natFire, natEmerald, RomKind.PLATINUM_U, RomKind.BLACK2_U, RomKind.GOLD_U, RomKind.RED_U)) {
            val info = RnqsInfo.parse(GameBuild.fileName(k, null, GameBuild.DEFAULT_NAME)!!)
            assertEquals(k.family, info.gameTag, k.id)
            assertEquals(k.isNatDex, info.natDex, k.id)
        }
    }

    // ------------------------------------------------------------------ Nat. Dex

    @Test
    fun `a Nat Dex build takes custom starters through the fork's own settings, and ZX cannot read the file`() {
        val store = newStore()
        val plan = GameBuild.Plan(
            base = preset("FRLG NatDex v1.2 Kaizo.rnqs"),
            starters = GameBuild.Starters.Pick(listOf(700, null, 152)),   // 700 is Sylveon, which only the Nat. Dex list has
            picks = mapOf("trainers" to "similar", "trainerLevels" to "60"),
        )
        val saved = GameBuild.save(store, natFire, plan, natFacts, "Sylveon start").getOrThrow()
        assertEquals("FRLG NatDex Kaizo (Sylveon start).rnqs", saved.name)
        val s = nd(saved)
        assertEquals("CUSTOM", s.startersMod.name)
        assertContentEquals(intArrayOf(701, 1, 153), s.customStarters)
        assertEquals("RANDOM", s.trainersMod.name); assertTrue(s.isTrainersUsePokemonOfSimilarStrength)
        assertTrue(s.isTrainersLevelModified); assertEquals(60, s.trainersLevelModifier)
        assertTrue(RnqsInfo.of(saved).natDex)
        assertTrue(RulesetCatalog.isCompatible(natFire, saved))
        assertFalse(RulesetCatalog.isCompatible(fireRed, saved))
        // The two randomizers do not read each other's files, so a Nat. Dex build cannot reach the wrong one.
        assertFailsWith<UnsupportedOperationException> { zx(saved) }
        // What the fork's Kaizo has that the builder does not show is still there.
        assertEquals(nd(preset("FRLG NatDex v1.2 Kaizo.rnqs")).currentMiscTweaks, s.currentMiscTweaks)
        assertEquals(nd(preset("FRLG NatDex v1.2 Kaizo.rnqs")).wildPokemonBSTLimit, s.wildPokemonBSTLimit)
    }

    // ------------------------------------------------------------------ the last Pokemon in the list

    @Test
    fun `the last Pokemon in a game's list is refused as a starter, and every other one is allowed`() {
        assertNotNull(GameBuild.starterProblem(frlg, 386))
        assertNull(GameBuild.starterProblem(frlg, 385))
        assertNull(GameBuild.starterProblem(frlg, 1))
        assertNotNull(GameBuild.starterProblem(frlg, 0))
        assertNotNull(GameBuild.starterProblem(frlg, 387))
        assertNotNull(GameBuild.starterProblem(natFacts, 1258))
        assertNull(GameBuild.starterProblem(natFacts, 1257))
        val e = assertFailsWith<IllegalArgumentException> {
            GameBuild.build(fireRed, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(386, null, null))), frlg)
        }
        assertTrue("last" in e.message.orEmpty(), e.message)
        assertNotNull(GameBuild.build(fireRed, GameBuild.Plan(starters = GameBuild.Starters.Pick(listOf(385, null, null))), frlg))
    }

    // ------------------------------------------------------------------ the choices follow the game

    @Test
    fun `a game without abilities or move tutors is not asked about them`() {
        val gsc = facts(251, listOf(155, 158, 152), abilities = false, tutors = false)
        val ids = GameBuild.choices(RomKind.GOLD_U, gsc).map { it.id }
        assertFalse("abilities" in ids || "tutorMoves" in ids || "tutorCompat" in ids, ids.toString())
        assertTrue("tmCompat" in ids && "wild" in ids && "trainers" in ids && "items" in ids)
        val all = GameBuild.choices(fireRed, frlg).map { it.id }
        assertEquals(
            listOf("wild", "wildLevels", "trainers", "trainerLevels", "items", "types", "abilities", "evolutions", "movesets", "moveStats", "tmMoves", "tmCompat", "tutorMoves", "tutorCompat"),
            all,
        )
        // Every choice starts with the game as it is, and every pick has words.
        for (c in GameBuild.choices(fireRed, frlg)) {
            assertTrue(c.picks.first().let { it.id == "unchanged" || it.id == "0" }, c.id)
            assertTrue(c.title.isNotBlank() && c.blurb.isNotBlank() && c.picks.all { it.label.isNotBlank() && it.detail.isNotBlank() }, c.id)
            assertEquals(c.picks.size, c.picks.map { it.id }.toSet().size, "${c.id} has two answers with one id")
        }
        assertEquals(setOf(GameBuild.Page.WORLD, GameBuild.Page.POKEMON), GameBuild.choices(fireRed, frlg).map { it.page }.toSet())
        assertEquals(listOf("wild", "wildLevels", "trainers", "trainerLevels", "items"), GameBuild.choices(fireRed, frlg).filter { it.page == GameBuild.Page.WORLD }.map { it.id })
    }

    @Test
    fun `what a mode already has is said in words, whether or not it is one of the answers`() {
        val cls = GameBuild.settingsClass(fireRed)
        val kaizo = GameBuild.read(cls, preset("FRLG Kaizo.rnqs"))
        val by = GameBuild.choices(fireRed, frlg).associateBy { it.id }
        // Kaizo's wild Pokemon are the one thing none of the three answers is: area 1-to-1 mapping.
        assertNull(GameBuild.matching(cls, kaizo, by.getValue("wild")))
        assertEquals("Area 1-to-1 mapping", GameBuild.currentText(cls, kaizo, by.getValue("wild")))
        assertEquals("Random", GameBuild.currentText(cls, kaizo, by.getValue("trainers")))
        assertEquals("+50%", GameBuild.currentText(cls, kaizo, by.getValue("wildLevels")))
        assertEquals("Random", GameBuild.currentText(cls, kaizo, by.getValue("abilities")))
        assertEquals("Unchanged", GameBuild.currentText(cls, kaizo, by.getValue("tutorMoves")))
        assertEquals("Everyone learns everything", GameBuild.currentText(cls, GameBuild.read(cls, preset("FRLG Survival.rnqs")), by.getValue("tmCompat")))
    }

    @Test
    fun `the summary lists what changed and says what stays`() {
        val plan = GameBuild.Plan(
            starters = GameBuild.Starters.Pick(listOf(25, null, 152)),
            picks = mapOf("wild" to "random", "trainerLevels" to "20"),
        )
        val lines = GameBuild.summary(fireRed, plan, facts(386, listOf(1, 4, 7)) { n -> when (n) { 25 -> "PIKACHU"; 152 -> "CHIKORITA"; else -> "SPECIES$n" } }, null)
        assertEquals(
            listOf(
                "Starts from the game as it is. Whatever you did not change stays as the game has it.",
                "Starters: Pikachu, random, Chikorita",
                "Wild Pokémon: Random",
                "Trainer levels: +20%",
            ),
            lines,
        )
        val fromKaizo = GameBuild.summary(fireRed, GameBuild.Plan(base = preset("FRLG Kaizo.rnqs")), frlg, "Kaizo")
        assertEquals(listOf("Starts from Kaizo. Whatever you did not change stays as Kaizo has it."), fromKaizo)
        assertFalse(GameBuild.Plan().touched)
        assertTrue(GameBuild.Plan(picks = mapOf("wild" to "random")).touched)
        assertTrue(GameBuild.Plan(base = preset("FRLG Kaizo.rnqs")).touched)
    }

    // ------------------------------------------------------------------ the species list

    @Test
    fun `species names read the way a player writes them, and pictures are found for them`() {
        assertEquals("Mr. Mime", SpeciesText.display(122, "MR.MIME"))
        assertEquals("Mr. Mime", SpeciesText.display(122, "MR. MIME"))
        assertEquals("Ho-Oh", SpeciesText.display(250, "HO-OH"))
        assertEquals("Porygon2", SpeciesText.display(233, "PORYGON2"))
        assertEquals("Nidoran F", SpeciesText.display(29, "NIDORAN♀"))
        assertEquals("Farfetch'd", SpeciesText.display(83, "FARFETCH’D"))
        assertEquals("Mime Jr.", SpeciesText.display(439, "MIME JR."))
        assertEquals("Porygon-Z", SpeciesText.display(474, "PORYGON-Z"))
        assertEquals("Charmeleon", SpeciesText.display(5, "Charmeleon"), "a name already spelled is left as the ROM has it")
        assertEquals("Terapagos-S", SpeciesText.display(1258, "Terapagos-S"))
        // Up to 251 a picture is the species' own number; past it the pack is the Nat. Dex expansion's, found by name.
        assertEquals(25, SpeciesText.iconId(25, "Pikachu"))
        assertEquals(277, SpeciesText.iconId(252, "Treecko"), "Treecko is #252 nationally and 277 in the pack")
        assertEquals(Favorites.idOf("Turtwig"), SpeciesText.iconId(387, "Turtwig"))
        assertNotNull(SpeciesText.iconId(700, "Sylveon"))
        assertNull(SpeciesText.iconId(9999, "Nothing"))
    }

    private val names = mapOf(
        1 to "BULBASAUR", 4 to "CHARMANDER", 5 to "CHARMELEON", 6 to "CHARIZARD", 25 to "PIKACHU", 122 to "MR.MIME",
        250 to "HO-OH", 252 to "TREECKO", 386 to "DEOXYS",
    )
    private val listed = facts(386, listOf(1, 4, 7)) { names[it] ?: "SPECIES$it" }

    @Test
    fun `the species list has every Pokemon in dex order, with the last one marked as not pickable`() {
        val rows = GameBuild.entries(listed)
        assertEquals(386, rows.size)
        assertEquals((1..386).toList(), rows.map { it.number })
        assertEquals("Mr. Mime", rows[121].label)
        assertEquals("Pikachu", rows[24].label)
        assertEquals(25, rows[24].iconId)
        assertEquals(277, rows[251].iconId)
        assertEquals(listOf(386), rows.filter { it.blocked != null }.map { it.number })
        assertTrue("cannot be a custom starter" in rows.last().blocked.orEmpty())
        // A name two entries share is told apart by its number.
        val twin = GameBuild.entries(facts(4, listOf(1, 2, 3)) { if (it >= 3) "Twin" else "One$it" })
        assertEquals(listOf("One1", "One2", "Twin (#3)", "Twin (#4)"), twin.map { it.label })
    }

    @Test
    fun `searching finds a Pokemon by the start of its name, part of it, or its number`() {
        val rows = GameBuild.entries(listed)
        fun found(q: String) = GameBuild.search(rows, q).map { it.number }
        assertEquals(rows.size, found("").size)
        assertEquals(rows.size, found("   ").size)
        assertEquals(listOf(4, 5, 6), found("char"), "Charmander, Charmeleon, Charizard, in dex order")
        assertEquals(listOf(4, 5, 6), found("CHAR"), "case does not matter")
        assertEquals(listOf(25), found("pika"))
        assertEquals(25, found("25").first(), "a number finds that Pokemon first")
        assertEquals(25, found("#25").first())
        assertEquals(4, found("#4").first())
        assertEquals(listOf(122), found("mr mime"), "spaces and dots do not matter")
        assertEquals(listOf(122), found("mrmime"))
        assertEquals(250, found("hooh").first())
        assertTrue(found("zzzz").isEmpty())
    }

    @Test
    fun `a name that only contains the text comes after names that start with it`() {
        val fruit = arrayOf("", "Apple", "Pineapple", "Apricot", "Grape", "Pear", "Peach")
        val rows = GameBuild.entries(facts(6, listOf(1, 2, 3)) { fruit[it] })
        assertEquals(listOf(1, 3, 2, 4), GameBuild.search(rows, "ap").map { it.number }, "Apple and Apricot start with it, Pineapple and Grape only hold it")
        assertEquals(listOf(5, 6), GameBuild.search(rows, "pea").map { it.number })
        assertEquals(listOf(5), GameBuild.search(rows, "5").map { it.number })
    }

    // ------------------------------------------------------------------ where it hooks in

    @Test
    fun `after a save the game built for and the file saved are the ones picked, found in the lists as they are read again`() {
        // RUN reads its lists again after a save and gets new objects, with other games in front.
        val prepared = listOf(natEmerald.copy() to File("a.gba"), emerald.copy() to File("emerald-u.gba"), fireRed.copy() to File("firered.gba"))
        val settings = listOf(File("FRLG Kaizo.rnqs"), File("FRLG (mine).rnqs"), File("RSE (mine).rnqs"))
        val (rom, file) = GameBuild.selectionAfterSave(prepared, settings, fireRed, "FRLG (mine).rnqs")
        assertEquals(fireRed.id to "firered.gba", rom?.first?.id to rom?.second?.name)
        assertEquals("FRLG (mine).rnqs", file?.name)
        // A file that is not there, or a game that is not, picks nothing and leaves RUN's own choice alone.
        assertNull(GameBuild.selectionAfterSave(prepared, settings, fireRed, "gone.rnqs").second)
        assertNull(GameBuild.selectionAfterSave(prepared, settings, RomKind.PLATINUM_U, "FRLG (mine).rnqs").first)
        assertNull(GameBuild.selectionAfterSave(prepared, settings, null, "FRLG (mine).rnqs").first)
        // And the Run screen hands the builder's save to it.
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("GameBuild.selectionAfterSave(" in run && "builtName = f.name" in run)
    }

    @Test
    fun `the Run screen has the entry and the builder, and the activity was left alone`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("BuildYourGameEntry" in run, "no entry on the Run screen")
        assertTrue("BuildYourGame(" in run, "the builder is not shown by the Run screen")
        assertTrue(run.indexOf("BuildYourGameEntry") > run.indexOf("ShellSegmented("), "the entry is not under the mode picker")
        val entry = File("src/main/kotlin/com/ironmonone/app/BuildYourGame.kt").readText()
        assertTrue("\"Build your own\"" in entry)
        assertFalse("BuildYourGame" in File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText(), "MainActivity belongs to another change")
    }
}
