package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * An app update that changes the build of a game KaizoCore makes (Heart & Soul's comfort build, C993EB6E in rc36,
 * E35A0E40 in rc36.1) must never strand a run (2026-10-06, Blake's rc36 Heart & Soul run in rc36.1: "Back where you
 * left off", then "Tracker: waiting for the game..." for good). Proved here on small files:
 *
 * - the run knows the build it was made from (its recipe) and is OLDER when this app makes another (RunBuild.state);
 * - the tracker card says so in plain words, with the button that fixes it (RunBuild.trackerNote);
 * - the run can be moved: same seed, settings, pool and mode on this app's build, no attempt counted, its notes kept,
 *   its in-game save carried over, and the save states of the old build never loaded into the new one (StateStamp);
 * - every other new run pins the old states the same way, so a state can never pass for one of another game;
 * - Play's NEW RUN keeps a Heart & Soul run's pool and a Nuzlocke's preset (the rc36.1 known issue).
 */
class RunBuildTest {
    private val filesDir = Files.createTempDirectory("runbuild").toFile()
    private val store = PrepStore(filesDir)
    private val prep = File(filesDir, "prep")
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val rc36 = 0xC993EB6EL
    private val app = "test build"
    private val settings = File(filesDir, "prep/settings/HnS Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(4, 5, 6)) }
    private val seed = 0x1234_5678_9abcL

    /** This app's Heart & Soul (KaizoCore) in the prepared folder, its CRC recorded as the real build's. */
    private fun newBuildOnHand(): File = store.savePrepared(hns, ByteArray(512) { 9 }, crc = hns.expectedCrc)

    private fun stub(tag: Int): (File, Long) -> Unit = { dest, s ->
        dest.writeBytes(ByteArray(64) { tag.toByte() })
        Randomizers.logFor(dest).writeText("log $tag seed $s")
    }

    /**
     * A Heart & Soul run made by rc36: its ROM, its recipe naming build C993EB6E and the Vanilla pool, its seed, an
     * attempt, its notes and events, its in-game save and an auto-save stamped the way rc36 stamped one.
     */
    private fun rc36Run(nuzlocke: Boolean = false) {
        val cur = store.currentRunFor(hns)
        cur.parentFile.mkdirs(); cur.writeBytes(ByteArray(64) { 1 }); Randomizers.logFor(cur).writeText("rc36 log")
        store.saveLastRun(hns.id, settings.name, nuzlocke = nuzlocke); store.saveLastSeed(seed)
        if (!nuzlocke) store.bumpAttempt(hns.id)
        val recipe = NextRun.recipe(hns, File(filesDir, "old-copy.gba").apply { writeBytes(ByteArray(8)) }, settings, null, null, "rc36",
            HnsEngine.Pool.VANILLA).copy(romCrc = "%08x".format(rc36))
        NextRun.recipeFileFor(cur).writeText((listOf("format=${NextRun.FORMAT}") + recipe.lines() + "seed=%016x".format(seed)).joinToString("\n"))
        for (n in listOf("marks.txt", "notes.txt")) File(prep, n).writeText("25:1,0,0,0,0,0")
        RunEvents(File(prep, "integrity.txt")).add(RunEvents.Kind.LOAD, "slot 1")
        RunSaves.file(filesDir, hns, cur).apply { parentFile.mkdirs(); writeBytes(ByteArray(128) { 3 }) }
        val auto = StateSlots.auto(filesDir, store.session())
        auto.file.parentFile.mkdirs(); auto.file.writeBytes(ByteArray(32) { 2 })
        auto.stamp.writeText("${hns.id}/%016x".format(seed))   // rc36's stamp: game and seed, no build
        auto.leftMark.writeText("")
    }

    // ------------------------------------------------------------------ the stamp

    @Test
    fun `a state loads only into the build it was taken on, and an old stamp only into the game in place`() {
        val now = StateStamp.of("heartsoul-kaizo-206/00000000000000aa", "e35a0e40")
        assertEquals("heartsoul-kaizo-206/00000000000000aa/e35a0e40", now)
        assertTrue(StateStamp.matches(now, now))
        assertTrue(StateStamp.matches("heartsoul-kaizo-206/00000000000000aa", now), "a stamp from before builds were stamped")
        assertFalse(StateStamp.matches("heartsoul-kaizo-206/00000000000000aa/c993eb6e", now), "another build of the same run")
        assertFalse(StateStamp.matches("heartsoul-kaizo-206/00000000000000bb", now), "another seed")
        assertFalse(StateStamp.matches(null, now))
        assertFalse(StateStamp.matches("x/?", "x/?"), "a run with no seed on disk matches nothing")
        assertTrue(StateStamp.otherBuild("heartsoul-kaizo-206/00000000000000aa/c993eb6e", now))
        assertFalse(StateStamp.otherBuild("heartsoul-kaizo-206/00000000000000bb/c993eb6e", now), "another seed is another run")
        assertFalse(StateStamp.otherBuild(now, now))
        // No recipe, no build: the stamp is the old form and matches only itself.
        assertEquals("emerald-u/0d", StateStamp.of("emerald-u/0d", null))
        assertTrue(StateStamp.matches("emerald-u/0d", "emerald-u/0d"))
        // The load refusal names the case.
        assertEquals(StateStamp.otherBuildLine("Slot 2"), StateSlots.loadRefusal(2, "heartsoul-kaizo-206/00000000000000aa/c993eb6e", now))
        assertNull(StateSlots.loadRefusal(2, "heartsoul-kaizo-206/00000000000000aa", now))
        assertEquals("Slot 2 is from a different run, so it was not loaded.", StateSlots.loadRefusal(2, "emerald-u/0d", now))
    }

    @Test
    fun `the run's stamp names the making of its game, from its recipe`() {
        rc36Run()
        assertEquals(rc36, store.runBuild())
        val tag = assertNotNull(store.runTag())
        assertEquals("${hns.id}/%016x/$tag".format(seed), store.stateStamp(store.session()))
        // rc36's own auto-save, stamped without a build, is still this run's: nothing replaced the game it was taken on.
        assertTrue(CrashResume.usable(StateSlots.auto(filesDir, store.session()), store.stateStamp(store.session())))
    }

    @Test
    fun `the kind knows every older comfort build, and the tracker's layout is the build the app makes`() {
        assertEquals(hns.expectedCrc, com.ironmonone.tracker.UnreadableBuild.HNS_BUILD_CRC, "a rebuild moves the kind with the layout")
        assertFalse(hns.expectedCrc in hns.supersededCrcs)
        assertTrue(hns.isOlderBuild(rc36), "rc36's, the build players had")
        assertEquals(hns, RomKind.olderBuildOf(rc36))
        // Every build players were given stays known for good: rc36 (C993EB6E) and rc36.1 (E35A0E40), or an update strands their runs.
        for (shipped in listOf(0xC993EB6EL, 0xE35A0E40L)) assertTrue(hns.isOlderBuild(shipped), "%08X shipped to players".format(shipped))
        assertNull(RomKind.olderBuildOf(hns.expectedCrc))
        assertNull(RomKind.olderBuildOf(RomKind.EMERALD_U.expectedCrc))
    }

    // ------------------------------------------------------------------ the run's build

    @Test
    fun `an rc36 run is OLDER in rc36_1, and a run of this build or with no recipe is not`() {
        assertEquals(RunBuild.State.OLDER, RunBuild.state(hns, rc36))
        assertEquals(RunBuild.State.CURRENT, RunBuild.state(hns, hns.expectedCrc))
        assertEquals(RunBuild.State.CURRENT, RunBuild.state(hns, null))
        assertEquals(RunBuild.State.CURRENT, RunBuild.state(null, rc36))
        rc36Run()
        assertEquals(RunBuild.State.OLDER, RunBuild.state(store))
    }

    @Test
    fun `the tracker card says what happened and what to do, only once the tracker refused the game`() {
        assertNull(RunBuild.trackerNote(hns, isRun = true, state = RunBuild.State.OLDER, haveNew = true, refused = false))
        val move = RunBuild.trackerNote(hns, isRun = true, state = RunBuild.State.OLDER, haveNew = true, refused = true)!!
        assertTrue(move.startsWith("This run was made with an older KaizoCore's Pokémon Heart & Soul (KaizoCore)"), move)
        assertTrue(move.endsWith(RunBuild.MOVE_LINE))
        val remake = RunBuild.trackerNote(hns, isRun = true, state = RunBuild.State.OLDER, haveNew = false, refused = true)!!
        assertTrue("Make it again in Home > Pokémon Heart & Soul" in remake, remake)
        val other = RunBuild.trackerNote(RomKind.FIRERED_MAXDEX_10, isRun = false, state = RunBuild.State.CURRENT, haveNew = true, refused = true)!!
        assertTrue("Library > Patched versions" in other && other.endsWith("The game plays without the tracker."), other)
        val words = listOf(move, remake, other, RunBuild.MOVE_LINE, RunBuild.MOVE_BUTTON, RunBuild.REMAKE_BUTTON, RunBuild.MOVING, RunBuild.MOVED,
            StateStamp.AUTO_OTHER_BUILD, StateStamp.otherBuildLine("Slot 1"), LibraryStore.OLDER_BUILD_LINE,
            RunStart.goneLine(hns.id, emptyList()))
        for (w in words) {
            assertFalse('—' in w || '–' in w, "no dashes: $w")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(w), w)
        }
    }

    // ------------------------------------------------------------------ moving the run

    @Test
    fun `a run cannot be moved until this app's build of its game is on hand, and says where to make it`() {
        rc36Run()
        val e = assertFailsWith<RunSetupProblem> { RunBuild.inputs(store) }
        assertEquals("Make Pokémon Heart & Soul (KaizoCore) again in Home > Pokémon Heart & Soul first.", e.message)
        newBuildOnHand()
        val i = RunBuild.inputs(store)
        assertEquals(seed, i.seed); assertEquals(settings.name, i.settings.name); assertEquals(HnsEngine.Pool.VANILLA, i.pool)
        assertFalse(i.nuzlocke); assertFalse(i.prePass); assertFalse(i.secondPass)
    }

    @Test
    fun `moving keeps the run, its save and notes, and never resumes the old build's states`() {
        rc36Run()
        newBuildOnHand()
        val attempt = store.attempt(hns.id)
        val save = RunSaves.file(filesDir, hns, store.currentRunFor(hns)).readBytes()
        val rc36Stamp = store.stateStamp(store.session())
        var randomizedWith = -1L
        val started = RunBuild.move(store, RunBuild.inputs(store), app, null, null,
            randomize = { d, s -> randomizedWith = s; stub(4)(d, s) }, hnsNuzlockePreset = { fail("a Kaizo run takes no Nuzlocke preset") })
        // The same seed, on this build, with the run's pool, and nothing counted or ended.
        assertEquals(seed, started.seed); assertEquals(seed, randomizedWith)
        assertContentEquals(ByteArray(64) { 4 }, store.currentRunFor(hns).readBytes())
        assertEquals(hns.expectedCrc, store.runBuild())
        assertEquals(HnsEngine.Pool.VANILLA, HnsPool.ofRun(store))
        assertEquals(RunBuild.State.CURRENT, RunBuild.state(store))
        assertEquals(attempt, store.attempt(hns.id), "a move is not an attempt")
        assertTrue(File(prep, "marks.txt").isFile && File(prep, "notes.txt").isFile, "the run's notes stay")
        val events = RunEvents(File(prep, "integrity.txt")).entries()
        assertEquals(listOf(RunEvents.Kind.LOAD, RunEvents.Kind.MOVE), events.map { it.kind })
        assertTrue("from C993EB6E" in events.last().detail, events.last().detail)
        // The in-game save carries the run onto the new build.
        assertContentEquals(save, RunSaves.file(filesDir, hns, store.currentRunFor(hns)).readBytes())
        // rc36's auto-save is pinned to rc36's build: not loaded, and said in a sentence.
        val auto = StateSlots.auto(filesDir, store.session())
        assertEquals(rc36Stamp, auto.stamp.readText())
        val want = store.stateStamp(store.session())
        assertTrue(want.startsWith("${hns.id}/%016x/".format(seed)) && want != rc36Stamp, want)
        assertFalse(CrashResume.usable(auto, want))
        assertTrue(CrashResume.otherBuild(auto, want))
        // Cannot be moved twice.
        assertFailsWith<RunSetupProblem> { RunBuild.inputs(store) }
    }

    @Test
    fun `Play opens a moved run on its in-game save and says why the auto-save was not loaded`() = kotlinx.coroutines.runBlocking {
        rc36Run()
        newBuildOnHand()
        RunBuild.move(store, RunBuild.inputs(store), app, null, null, randomize = stub(4))
        val session = store.session()
        var loaded = false
        // As Play's core-up does after a pause: the left mark is there, but the state is the old build's.
        val line = CrashResume.atCoreUp(File(prep, "playing.txt"), session, StateSlots.auto(filesDir, session), store.stateStamp(session),
            loadsAllowed = true, events = null, why = { "" }, load = { loaded = true; true }, settle = {})
        assertFalse(loaded, "a state of the old build is never put into the new one")
        assertEquals(StateStamp.AUTO_OTHER_BUILD, line)
    }

    @Test
    fun `a Heart & Soul Nuzlocke moves as a Nuzlocke, with its preset`() {
        rc36Run(nuzlocke = true)
        newBuildOnHand()
        val i = RunBuild.inputs(store)
        assertTrue(i.nuzlocke)
        var preset: File? = null
        RunBuild.move(store, i, app, null, null, randomize = stub(4), hnsNuzlockePreset = { preset = it })
        assertEquals(store.currentRunFor(hns), preset)
        assertTrue(store.lastRunNuzlocke(), "still a Nuzlocke")
    }

    // ------------------------------------------------------------------ every new run pins the old states

    @Test
    fun `a new run with the same seed but another making never takes the last run's states, stamped before or after this change`() {
        val kind = RomKind.EMERALD_U
        val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
        val rse = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        RunStart.start(store, kind, prepared, rse, seed = 0x0dL, app = app, prePass = null, secondPass = null,
            take = { fail("a chosen seed takes no stage") }, stop = {}, randomize = stub(1))
        val session = store.session()
        // Two states of this run: one stamped the old way (before this change), one the new way.
        val slot1 = StateSlots.slot(filesDir, session, 1).apply { file.parentFile.mkdirs(); file.writeBytes(ByteArray(8)); stamp.writeText(store.runIdentity()) }
        val slot2 = StateSlots.slot(filesDir, session, 2).apply { file.writeBytes(ByteArray(8)); stamp.writeText(store.stateStamp(session)) }
        val stampNow = store.stateStamp(session)
        assertNull(StateSlots.loadRefusal(1, slot1.stamp.readText(), stampNow))
        assertNull(StateSlots.loadRefusal(2, slot2.stamp.readText(), stampNow))
        // The same seed again (a run code, a chosen seed) with the settings since edited: another game, the same seed.
        rse.writeBytes(byteArrayOf(9, 9))
        RunStart.start(store, kind, prepared, rse, seed = 0x0dL, app = app, prePass = null, secondPass = null,
            take = { fail("a chosen seed takes no stage") }, stop = {}, randomize = stub(2))
        assertEquals(stampNow, slot2.stamp.readText(), "a stamp with its tag already is left alone")
        assertEquals(stampNow, slot1.stamp.readText(), "the old-form stamp is pinned to the game it was taken on")
        val after = store.stateStamp(store.session())
        assertTrue(after != stampNow && after.startsWith(store.runIdentity() + "/"), after)
        assertEquals(StateStamp.otherBuildLine("Slot 1"), StateSlots.loadRefusal(1, slot1.stamp.readText(), after))
        assertEquals(StateStamp.otherBuildLine("Slot 2"), StateSlots.loadRefusal(2, slot2.stamp.readText(), after))
    }

    @Test
    fun `a run with no recipe pins its old states to no build at all`() {
        val stamp = File(filesDir, "saves/state3.id").apply { parentFile.mkdirs(); writeText("emerald-u/0d") }
        StateStamp.pin(listOf(stamp, File(filesDir, "saves/none.id")), null)
        assertEquals("emerald-u/0d/" + StateStamp.NO_TAG, stamp.readText())
        assertFalse(StateStamp.matches(stamp.readText(), "emerald-u/0d"))
        assertFalse(File(filesDir, "saves/none.id").exists())
    }

    // ------------------------------------------------------------------ NEW RUN keeps the run's mode

    @Test
    fun `a Heart & Soul Nuzlocke's next run gets the Nuzlocke preset and keeps its pool, whichever button starts it`() {
        rc36Run(nuzlocke = true)
        val prepared = newBuildOnHand()
        var preset: File? = null
        // Play's NEW RUN: the run's own pool, a Nuzlocke counts no attempt (RunStart, PlayScreen.newRun).
        RunStart.start(store, hns, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { null }, stop = {}, pool = HnsEngine.Pool.VANILLA, randomize = stub(5), countAttempt = false,
            hnsNuzlockePreset = { preset = it })
        assertEquals(store.currentRunFor(hns), preset, "the Nuzlocke preset goes on the new game")
        assertEquals(HnsEngine.Pool.VANILLA, HnsPool.ofRun(store), "the run's pool, not the one chosen now (Nat. Dex by default)")
        assertTrue(store.lastRunNuzlocke())
        // A Kaizo run's next game keeps the randomizer's Kaizo preset.
        preset = null
        RunStart.start(store, hns, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { null }, stop = {}, randomize = stub(6), countAttempt = true, hnsNuzlockePreset = { preset = it })
        assertNull(preset)
    }

    @Test
    fun `Play's NEW RUN passes the run's pool and its Nuzlocke, and its move goes the same way`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("countAttempt = nuzlocke == null && !store.lastRunNuzlocke()," in play)
        assertTrue("pool = if (k.isHns) HnsPool.ofRun(store) else null)" in play)
        assertTrue("val started = RunBuild.move(store, i, NextRunJob.appStamp(context)," in play)
        assertTrue("}.onFailure { if (it is com.ironmonone.tracker.UnreadableBuild) buildCard.refused = true }.getOrNull()" in play,
            "the tracker's refusal reaches the card instead of being swallowed")
        assertEquals(2, Regex("unsupportedNote = buildCard.note, unsupportedAction = buildCard.action,").findAll(play).count(),
            "both Gen 3 tracker panels, portrait and landscape")
        // The Nuzlocke preset is RunStart's alone now, for every button.
        assertFalse("HnsEngine.writePreset(store.currentRunFor" in File("src/main/kotlin/com/ironmonone/app/RunJob.kt").readText())
    }

    // ------------------------------------------------------------------ the library

    @Test
    fun `an older KaizoCore's build in the library is named as that, not as a hack, and NEW RUN says so`() {
        val library = LibraryStore(File(filesDir, "library"))
        val e = LibraryStore.Entry(File(filesDir, "x.gba"), "Pokémon Heart & Soul (KaizoCore).gba", rc36, null, com.ironmonone.core.Platform.GBA, "A changed game")
        assertEquals(hns, e.olderBuildOf)
        assertEquals(LibraryStore.Category.PATCHED, e.category)
        assertEquals("Pokémon Heart & Soul (KaizoCore) · " + LibraryStore.OLDER_BUILD_LINE, e.subtitle)
        assertFalse(e.tracked)
        assertEquals("Your Pokémon Heart & Soul (KaizoCore) was made by an older KaizoCore. Make it again in Home > Pokémon Heart & Soul, then start the run.",
            RunStart.goneLine(hns.id, listOf(e)))
        assertTrue(RunStart.goneLine(hns.id, emptyList()).startsWith("The game this run was made from is gone."))
        assertNotNull(library)
    }
}
