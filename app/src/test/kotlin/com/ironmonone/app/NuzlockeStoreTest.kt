package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.GbNuzReads
import com.ironmonone.tracker.NuzlockeReads
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.Heir
import com.ironmonone.tracker.nuzlocke.NuzlockeEdits
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.NuzlockeText
import com.ironmonone.tracker.nuzlocke.NzArea
import com.ironmonone.tracker.nuzlocke.NzMon
import com.ironmonone.tracker.nuzlocke.Origin
import com.ironmonone.tracker.nuzlocke.RosterMon
import com.ironmonone.tracker.nuzlocke.RunStatus
import com.ironmonone.tracker.nuzlocke.Snapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Nuzlocke runs on disk (2026-09-29): one file per run under prep/nuzlocke, written whole, tied to the game
 * they were started on, and never shown against another. The rules and the file's own format are tested in
 * tracker-gba; this is the part that lives in the app.
 */
class NuzlockeStoreTest {
    private val filesDir = Files.createTempDirectory("nuzlocke").toFile()
    private val store = NuzlockeStore(filesDir)
    private val t0 = 1_800_000_000_000L
    private val standard = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)
    private val genlocke = NuzlockeRules.forPreset(NuzlockePreset.GENLOCKE)

    // Every test starts with no run in memory: the tracking object is shared by the whole JVM.
    init { NuzlockeTracking.reset() }

    private fun treecko() = NzMon(
        id = 1L, species = 277, speciesName = "TREECKO", nickname = "", level = 5, hp = 20, maxHp = 20,
        isEgg = false, gender = null, types = listOf(12), shiny = false,
    )

    private fun mon(id: Long, name: String, species: Int) =
        RosterMon(id, species, name, "", 10, Gender.MALE, Origin.CAUGHT, "Route 101", "Route 101", t0)

    // ---------------------------------------------------------------- files

    @Test
    fun `a run is one file under prep-nuzlocke, written whole`() {
        val a = store.start("lib-0000abcd", "Pokémon Emerald (U)", standard, t0)
        assertEquals(File(filesDir, "prep/nuzlocke"), store.dir)
        assertEquals(listOf(a.meta.id + ".txt"), store.dir.listFiles()!!.map { it.name }, "one file, and no temp file left beside it")
        assertEquals(NuzlockeText.format(a), store.fileFor(a.meta.id).readText())
        val back = assertNotNull(store.load(a.meta.id))
        assertEquals("lib-0000abcd", back.meta.bind)
        assertEquals("Pokémon Emerald (U)", back.meta.game)
        assertEquals(RunStatus.ACTIVE, back.meta.status)
        assertEquals(NuzlockePreset.STANDARD, back.meta.rules.preset)
    }

    @Test
    fun `a game's second run is a second file`() {
        val a = store.start("lib-1", "Game", standard, t0)
        val b = store.start("lib-2", "Other game", standard, t0 + 1)
        assertEquals(setOf(a.meta.id + ".txt", b.meta.id + ".txt"), store.dir.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun `listing skips what is not a ledger and leaves it alone`() {
        val a = store.start("lib-1", "Game", standard, t0)
        File(store.dir, "junk.txt").writeText("hello\nworld\n")
        File(store.dir, "empty.txt").writeText("")
        File(store.dir, "nomagic.txt").writeText("run\tnz-1\tbind\tgame\t1\tactive\t0\t\n")
        File(store.dir, "cut.txt").writeText(NuzlockeText.MAGIC + "\t1\n")
        File(store.dir, "readme.md").writeText("not a run")
        File(store.dir, "folder.txt").mkdirs()
        File(store.dir, a.meta.id + ".txt.tmp").writeText("half a write")
        assertEquals(listOf(a.meta.id), store.list().map { it.header.id })
        for (n in listOf("junk.txt", "empty.txt", "nomagic.txt", "cut.txt", "readme.md", a.meta.id + ".txt.tmp")) {
            assertTrue(File(store.dir, n).exists(), "$n was left where it was")
        }
    }

    @Test
    fun `a ledger with a damaged body still lists and opens, with what is readable`() {
        val lines = listOf(
            NuzlockeText.MAGIC + "\t1",
            "run\tnz-old\tlib-9\tOld game\t5\tactive\t0\t",
            "rule\tpreset\thardcore",
            "enc\tbroken",
            "area\tRoute 1\tRoute 1",
            "mon\tnot-a-number",
            "evt\tnope",
            "a line from a newer build\twith\tfields",
        )
        File(store.dir, "nz-old.txt").apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n") + "\n")
        val entry = assertNotNull(store.list().singleOrNull())
        assertEquals("nz-old", entry.header.id)
        assertEquals(NuzlockePreset.HARDCORE, entry.header.preset)
        val ledger = assertNotNull(store.load("nz-old"))
        assertEquals("Old game", ledger.meta.game)
        assertTrue(ledger.roster.isEmpty())
        assertNull(store.load("no-such-run"))
        assertNull(store.load("junk"), "an id that is no file is no ledger")
    }

    @Test
    fun `an old file with no rules is read with the standard ones`() {
        File(store.dir, "nz-plain.txt").apply { parentFile.mkdirs() }
            .writeText(NuzlockeText.MAGIC + "\t1\nrun\tnz-plain\tlib-9\tGame\t5\tactive\t0\t\n")
        val rules = assertNotNull(store.load("nz-plain")).meta.rules
        assertEquals(standard, rules)
    }

    @Test
    fun `a run's own ledger survives being saved and loaded again`() {
        val a = store.start("lib-1", "Game", standard, t0)
        a.roster[7L] = mon(7L, "Pidgey", 16)
        NuzlockeEdits(a).addNote("Route 3 was quiet", t0 + 5)
        assertTrue(store.save(a))
        val back = assertNotNull(store.load(a.meta.id))
        assertEquals(NuzlockeText.format(a), NuzlockeText.format(back))
        assertEquals(listOf("Pidgey"), back.roster.values.map { it.speciesName })
        assertEquals(listOf("Route 3 was quiet"), back.notes)
    }

    // ---------------------------------------------------------------- which run is current

    @Test
    fun `a new run on a game replaces the one in progress, which stays as history`() {
        val a = store.start("lib-1", "Game", standard, t0)
        a.roster[7L] = mon(7L, "Pidgey", 16)
        store.save(a)
        val other = store.start("lib-2", "Another game", standard, t0 + 1)
        val b = store.start("lib-1", "Game", NuzlockeRules.forPreset(NuzlockePreset.HARDCORE), t0 + 5_000)
        assertEquals(b.meta.id, store.current("lib-1")?.header?.id)
        val old = assertNotNull(store.load(a.meta.id))
        assertEquals(RunStatus.ABANDONED, old.meta.status)
        assertEquals(t0 + 5_000, old.meta.endedAt)
        assertTrue(old.events.any { it.kind == "over" })
        assertEquals(listOf("Pidgey"), old.roster.values.map { it.speciesName }, "history keeps everything in it")
        assertEquals(RunStatus.ACTIVE, assertNotNull(store.load(other.meta.id)).meta.status, "another game's run is left alone")
        assertEquals(other.meta.id, store.current("lib-2")?.header?.id)
        assertEquals(listOf(b.meta.id, other.meta.id, a.meta.id), store.list().map { it.header.id }, "newest first")
    }

    @Test
    fun `a run that ended stays as it is when a new one starts`() {
        val a = store.start("lib-1", "Game", standard, t0)
        assertTrue(NuzlockeEdits(a).endRun("Whiteout", t0 + 1))
        store.save(a)
        store.start("lib-1", "Game", standard, t0 + 2)
        val back = assertNotNull(store.load(a.meta.id))
        assertEquals(RunStatus.OVER, back.meta.status)
        assertEquals("Whiteout", back.meta.endReason)
    }

    @Test
    fun `a replaced run is never the current one`() {
        val a = store.start("lib-1", "Game", standard, t0)
        assertEquals(a.meta.id, store.current("lib-1")?.header?.id)
        assertTrue(store.abandon(a.meta.id, t0 + 1))
        assertNull(store.current("lib-1"), "nothing shows for a game whose only run was replaced")
        assertFalse(store.abandon(a.meta.id, t0 + 2), "replacing it twice does nothing")
        assertFalse(store.abandon("no-such-run", t0))
    }

    @Test
    fun `the copy in memory is the freshest one when a run is replaced`() {
        val a = store.start("lib-1", "Game", standard, t0)
        val live = assertNotNull(NuzlockeTracking.liveFor(store, "lib-1"))
        // A change the panel has made and the writer has not finished: it must not be lost to the abandon.
        live.ledger.roster[9L] = mon(9L, "Zigzagoon", 288)
        store.start("lib-1", "Game", standard, t0 + 10)
        assertEquals(listOf("Zigzagoon"), assertNotNull(store.load(a.meta.id)).roster.values.map { it.speciesName })
        assertNull(NuzlockeTracking.loaded(a.meta.id), "a replaced run is let go of")
    }

    // ---------------------------------------------------------------- what a run is tied to

    @Test
    fun `a library game is tied to its own id and a randomized run to its game and seed`() {
        val lib = GameSession(File("x.gba"), com.ironmonone.core.Platform.GBA, RomKind.EMERALD_U, "Emerald", "lib-1f1c08fb", false)
        assertEquals("lib-1f1c08fb", NuzlockeStore.bindOf(lib, "emerald-u/00000000000000ab"), "the run's seed does not matter to a library game")
        val run = GameSession.forRun(File("current.gba"), RomKind.EMERALD_U)
        assertEquals("emerald-u/00000000000000ab", NuzlockeStore.bindOf(run, "emerald-u/00000000000000ab"))
        assertNull(NuzlockeStore.bindOf(run, "emerald-u/?"), "half way through a new run the seed is not known")
        assertNull(NuzlockeStore.bindOf(run, "?/00000000000000ab"))
        assertNull(NuzlockeStore.bindOf(run, ""))
    }

    @Test
    fun `the bind a randomized run is made under is the identity PrepStore writes once the game is in place`() {
        val prep = PrepStore(filesDir)
        for (seed in listOf(0xabL, 0L, -5L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            prep.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs")
            prep.saveLastSeed(seed)
            assertEquals(NuzlockeStore.bindOfRun(RomKind.EMERALD_U.id, seed), prep.runIdentity(), "seed $seed")
            assertEquals(prep.runIdentity(), NuzlockeStore.bindOf(GameSession.forRun(prep.currentRun, RomKind.EMERALD_U), prep.runIdentity()))
        }
    }

    @Test
    fun `the run for the game in play is found by its identity and no other is`() {
        val prep = PrepStore(filesDir)
        val kind = RomKind.EMERALD_U
        prep.saveLastRun(kind.id, "RSE Kaizo.rnqs")
        prep.saveLastSeed(0xabL)
        assertNull(NuzlockeTracking.current(filesDir, now = 1_000L), "no run made yet")
        val run = store.start(NuzlockeStore.bindOfRun(kind.id, 0xabL), "Pokémon Emerald (U), randomized", standard, t0)
        NuzlockeTracking.changed()
        val live = assertNotNull(NuzlockeTracking.current(filesDir, now = 2_000L))
        assertEquals(run.meta.id, live.ledger.meta.id)
        assertSame(live, NuzlockeTracking.current(filesDir, now = 2_500L), "asked again at once, it is the same run")
        // A new seed is another game, and the old run is not shown against it.
        prep.saveLastSeed(0xcdL)
        assertNull(NuzlockeTracking.current(filesDir, now = 2_000L + NuzlockeTracking.RECHECK_MS))
        // Back on the first seed it is found again, with what the panel had put in it.
        prep.saveLastSeed(0xabL)
        assertSame(live, NuzlockeTracking.current(filesDir, now = 2_000L + 2 * NuzlockeTracking.RECHECK_MS))
    }

    @Test
    fun `starting a run is seen at once and not after the next look at the disk`() {
        val prep = PrepStore(filesDir)
        val kind = RomKind.EMERALD_U
        prep.saveLastRun(kind.id, "RSE Kaizo.rnqs")
        prep.saveLastSeed(0x77L)
        assertNull(NuzlockeTracking.current(filesDir, now = 5_000L))
        store.start(NuzlockeStore.bindOfRun(kind.id, 0x77L), "Emerald", standard, t0)
        assertNotNull(NuzlockeTracking.current(filesDir, now = 5_001L), "a start rings the bell, so the panel does not wait two seconds")
    }

    @Test
    fun `one run is one object however it is asked for`() {
        val a = store.start("lib-1", "Game", standard, t0)
        val x = assertNotNull(NuzlockeTracking.liveFor(store, "lib-1"))
        val y = assertNotNull(NuzlockeTracking.liveById(store, a.meta.id))
        assertSame(x, y)
        assertSame(x.ledger, NuzlockeTracking.loaded(a.meta.id))
        assertNull(NuzlockeTracking.liveFor(store, "lib-nobody"))
        assertNull(NuzlockeTracking.liveById(store, "nz-nobody"))
        NuzlockeTracking.forget(a.meta.id)
        assertNull(NuzlockeTracking.loaded(a.meta.id))
    }

    // ---------------------------------------------------------------- feeding and saving

    @Test
    fun `a poll that changes the ledger reaches the disk, and the same poll again changes nothing`() {
        val made = store.start("lib-1", "Emerald", standard, t0)
        val live = assertNotNull(NuzlockeTracking.liveFor(store, "lib-1"))
        val poll = Snapshot(area = NzArea("Route 101", 16), party = listOf(treecko()), ballCount = 5)
        assertTrue(live.feed(poll, t0 + 1))
        assertTrue(live.saveNow())
        val disk = assertNotNull(store.load(made.meta.id))
        assertEquals(listOf("Treecko"), disk.roster.values.map { it.speciesName })
        assertEquals(Origin.STARTER, disk.roster.values.single().origin)
        assertTrue(disk.meta.started, "the rules began when the bag held Poke Balls")
        val revision = live.ledger.revision
        assertFalse(live.feed(poll, t0 + 2), "the same state again says nothing new")
        assertEquals(revision, live.ledger.revision)
        assertFalse(live.feed(Snapshot(readable = false), t0 + 3), "a poll the tracker could not read is ignored")
    }

    @Test
    fun `a correction made by hand is saved too`() {
        val a = store.start("lib-1", "Game", standard, t0)
        val live = assertNotNull(NuzlockeTracking.liveFor(store, "lib-1"))
        assertTrue(live.edits.addNote("by hand", t0 + 1))
        live.edited()
        assertTrue(live.saveNow())
        assertEquals(listOf("by hand"), assertNotNull(store.load(a.meta.id)).notes)
    }

    @Test
    fun `saves reach the disk in the order they were made`() {
        val f = File(filesDir, "order.txt")
        repeat(300) { i -> NuzlockeTracking.write(f, "v$i", wait = false) }
        assertTrue(NuzlockeTracking.write(f, "last", wait = true))
        assertEquals("last", f.readText())
    }

    @Test
    fun `deleting a run removes its file, and nothing queued before it brings it back`() {
        val a = store.start("lib-1", "Game", standard, t0)
        val f = store.fileFor(a.meta.id)
        NuzlockeTracking.liveFor(store, "lib-1")
        repeat(50) { NuzlockeTracking.write(f, NuzlockeText.format(a), wait = false) }
        assertTrue(store.delete(a.meta.id))
        assertFalse(f.exists())
        assertNull(store.load(a.meta.id))
        assertNull(NuzlockeTracking.loaded(a.meta.id))
        assertTrue(store.list().isEmpty())
        assertFalse(store.delete(a.meta.id), "it is gone")
    }

    // ---------------------------------------------------------------- fed with no panel on screen

    private fun trackerState(nuz: NuzlockeReads? = NuzlockeReads(ballCount = 5)): TrackerState {
        val decoded = PokemonDecoder.Mon(
            pid = 24L, level = 5, nickname = "", species = 277, heldItem = 0, friendship = 70,
            moves = List(4) { 0 }, pp = List(4) { 0 }, ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 20, maxHp = 20,
            atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10, exp = 0, isEgg = false,
        )
        val base = BaseStats(40, 45, 40, 56, 35, 35, 12, 12, 51, 77, genderRatio = 31)
        return TrackerState(
            partyCount = 1, party = listOf(TrackedMon(decoded, "TREECKO", emptyList(), base)),
            inBattle = false, isWildBattle = false, mapId = 16, routeName = "Route 101", nuz = nuz,
        )
    }

    @Test
    fun `a tracker state fed with no panel on screen reaches the run in play`() {
        val prep = PrepStore(filesDir)
        val kind = RomKind.EMERALD_U
        prep.saveLastRun(kind.id, "RSE Kaizo.rnqs")
        prep.saveLastSeed(0xabL)
        store.start(NuzlockeStore.bindOfRun(kind.id, 0xabL), "Emerald", standard, t0)
        val state = trackerState()
        assertTrue(NuzlockeTracking.observe(filesDir, state, at = 2_000L))
        val live = assertNotNull(NuzlockeTracking.current(filesDir, now = 2_001L))
        assertEquals(listOf("Treecko"), live.ledger.roster.values.map { it.speciesName })
        assertTrue(live.saveNow())
        assertEquals(listOf("Treecko"), assertNotNull(store.load(live.ledger.meta.id)).roster.values.map { it.speciesName })
        assertFalse(NuzlockeTracking.observe(filesDir, state, at = 2_100L), "the same state again changes nothing")
    }

    @Test
    fun `a state with nothing to feed, or no run to feed it to, is left alone`() {
        val prep = PrepStore(filesDir)
        prep.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs")
        prep.saveLastSeed(0xabL)
        assertFalse(NuzlockeTracking.observe(filesDir, trackerState(), at = 1_000L), "no run has been started")
        store.start(NuzlockeStore.bindOfRun(RomKind.EMERALD_U.id, 0xabL), "Emerald", standard, t0)
        assertFalse(NuzlockeTracking.observe(filesDir, null, at = 2_000L))
        assertFalse(NuzlockeTracking.observe(filesDir, trackerState(nuz = null), at = 2_100L), "a game the tracker did not read for the rules")
        assertTrue(assertNotNull(NuzlockeTracking.current(filesDir, now = 2_200L)).ledger.roster.isEmpty())
    }

    // ---------------------------------------------------------------- the other families of games (2026-09-30)

    @Test
    fun `a run keeps the family of games and the game it was started on`() {
        val gb = store.start("lib-1", "Pokemon Crystal", standard, t0, system = NuzlockeSystem.GEN2, gameKey = "crystal")
        val ds = store.start("lib-2", "Pokemon Platinum", standard, t0 + 1, system = NuzlockeSystem.GEN4, gameKey = "platinum")
        val gba = store.start("lib-3", "Pokemon Emerald", standard, t0 + 2)
        val back = assertNotNull(store.load(gb.meta.id))
        assertEquals(NuzlockeSystem.GEN2, back.meta.system); assertEquals("crystal", back.meta.gameKey)
        assertEquals(NuzlockeSystem.GEN4, assertNotNull(store.load(ds.meta.id)).meta.system)
        assertEquals("platinum", assertNotNull(store.load(ds.meta.id)).meta.gameKey)
        assertEquals(NuzlockeSystem.GEN3, assertNotNull(store.load(gba.meta.id)).meta.system, "a start that names no family is Generation 3")
        assertTrue(store.fileFor(gb.meta.id).readLines().any { it == "system\tgen2" })
        assertTrue(store.fileFor(gb.meta.id).readLines().any { it == "gamekey\tcrystal" })
        assertEquals(setOf(gb.meta.id, ds.meta.id, gba.meta.id), store.list().map { it.header.id }.toSet(), "all three list, whatever the family")
    }

    private fun gbState(species: Int = 155, name: String = "CYNDAQUIL", ratio: Int = 31, nuz: NuzlockeReads? = null): TrackerState {
        val decoded = PokemonDecoder.Mon(
            pid = (species.toLong() shl 16) or 0x2B0EL, level = 5, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = List(4) { 0 }, pp = List(4) { 0 }, ivs = listOf(0, 15, 7, 15, 7, 7), evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 20, maxHp = 20,
            atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10,
        )
        val base = BaseStats(39, 52, 43, 65, 50, 50, 10, 10, 0, 0, genderRatio = ratio)
        return TrackerState(
            partyCount = 1, party = listOf(TrackedMon(decoded, name, emptyList(), base)), inBattle = false, isWildBattle = false, mapId = 2,
            nuz = nuz ?: NuzlockeReads(gb = GbNuzReads(2, "c", listOf("c"), place = "Route 29", detail = "Route 29", playerId = 0x2B0E, ballCount = 5)),
        )
    }

    @Test
    fun `a Game Boy state fed with no panel on screen reaches the run in play, as a Game Boy run`() {
        val prep = PrepStore(filesDir)
        val kind = RomKind.CRYSTAL_U
        prep.saveLastRun(kind.id, "GSC Kaizo.rnqs")
        prep.saveLastSeed(0xabL)
        val made = store.start(NuzlockeStore.bindOfRun(kind.id, 0xabL), "Crystal", standard, t0, system = NuzlockeSystem.GEN2, gameKey = "crystal")
        assertTrue(NuzlockeTracking.observe(filesDir, gbState(), at = 2_000L))
        val live = assertNotNull(NuzlockeTracking.current(filesDir, now = 2_001L))
        assertEquals(made.meta.id, live.ledger.meta.id)
        val mon = live.ledger.roster.values.single()
        assertEquals(Gender.MALE, mon.gender, "the DVs against the ratio in the ROM, the way the game works it out")
        assertEquals((0x2B0EL shl 16) or 0xF7F7L, mon.id, "the trainer id above the DVs")
        assertEquals(Origin.STARTER, mon.origin)
        assertTrue(live.ledger.meta.started, "the bag holds balls")
        assertTrue(live.saveNow())
        assertEquals(NuzlockeSystem.GEN2, assertNotNull(store.load(made.meta.id)).meta.system)
        assertFalse(NuzlockeTracking.observe(filesDir, gbState(), at = 2_100L), "the same state again changes nothing")
        assertEquals("Route 29", live.lastSnapshot?.area?.name)
    }

    private fun dsMon(species: Int = 387, name: String = "TURTWIG", level: Int = 5, hp: Int = 20): NdsTrackedMon = NdsTrackedMon(
        mon = Gen4.Mon(
            pid = 0x1234_5678L, species = species, heldItem = 0, abilityId = 0, level = level, curHp = hp, maxHp = 20,
            atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10, moves = List(4) { 0 }, pp = List(4) { 0 }, ppUps = List(4) { 0 },
            ivs = List(6) { 0 }, shiny = false, nature = 0, isEgg = false, otId = 1234, otSid = 5678,
        ),
        speciesName = name, info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    @Test
    fun `a DS state reaches the run in play, and one that is not located, or of an unknown game, does not`() {
        val prep = PrepStore(filesDir)
        val kind = RomKind.PLATINUM_U
        prep.saveLastRun(kind.id, "DPPt Kaizo.rnqs")
        prep.saveLastSeed(0xabL)
        val made = store.start(NuzlockeStore.bindOfRun(kind.id, 0xabL), "Platinum", standard, t0, system = NuzlockeSystem.GEN4, gameKey = "platinum")
        val state = NdsTrackerState(partyCount = 1, party = listOf(dsMon()), located = true, gameName = "Pokemon Platinum", areaName = "Twinleaf Town", mapId = 3)
        assertFalse(NuzlockeTracking.observeNds(filesDir, null, at = 1_500L))
        assertFalse(NuzlockeTracking.observeNds(filesDir, state.copy(located = false), at = 1_600L), "the party is not found yet")
        assertFalse(NuzlockeTracking.observeNds(filesDir, state.copy(gameName = "Some other game"), at = 1_700L), "a game the adapter does not know")
        assertTrue(NuzlockeTracking.observeNds(filesDir, state, at = 2_000L))
        val live = assertNotNull(NuzlockeTracking.current(filesDir, now = 2_001L))
        assertEquals(made.meta.id, live.ledger.meta.id)
        val mon = live.ledger.roster.values.single()
        assertEquals("Turtwig", mon.speciesName)
        assertEquals(Origin.STARTER, mon.origin)
        assertTrue(live.ledger.meta.started, "no ball count is read on a DS game, so the rules begin with the first Pokemon")
        assertFalse(NuzlockeTracking.observeNds(filesDir, state, at = 2_100L), "the same state again changes nothing")
    }

    // ---------------------------------------------------------------- Genlocke

    @Test
    fun `a Genlocke's next game carries the survivors the last one saved`() {
        val first = store.start("lib-1", "Pokémon FireRed", genlocke, t0)
        assertTrue(first.meta.genlockeId.startsWith("gl-"))
        assertEquals(1, first.meta.leg)
        first.roster[1L] = mon(1L, "Charmander", 4)
        first.roster[2L] = mon(2L, "Pidgey", 16).also { it.alive = false }
        store.save(first)
        assertEquals(listOf("Charmander"), store.heirsOf(first.meta.id).map { it.speciesName }, "not finished: who is alive")
        first.meta.status = RunStatus.COMPLETE
        first.meta.heirsOut += Heir(4, "Charmander", "Blaze", 55, Gender.MALE, false)
        store.save(first)
        val heirs = store.heirsOf(first.meta.id)
        assertEquals(listOf("Blaze"), heirs.map { it.nickname }, "finished: who the ledger saved at the Champion")

        val next = store.start("lib-2", "Pokémon Emerald", genlocke, t0 + 10, genlockeId = first.meta.genlockeId, leg = 2, carriedFrom = first.meta.id, carry = heirs)
        val back = assertNotNull(store.load(next.meta.id))
        assertEquals(first.meta.genlockeId, back.meta.genlockeId)
        assertEquals(2, back.meta.leg)
        assertEquals(first.meta.id, back.meta.carriedFrom)
        assertEquals(listOf("Charmander"), back.meta.heirsIn.map { it.speciesName })
        assertEquals(55, back.meta.heirsIn.single().level)
        assertTrue(back.events.any { it.text.contains("Charmander") && it.text.contains("Carried") })
        assertEquals(RunStatus.COMPLETE, assertNotNull(store.load(first.meta.id)).meta.status, "the finished game is left as it was")
    }

    @Test
    fun `survivors are carried only by a Genlocke`() {
        val heirs = listOf(Heir(4, "Charmander", "", 55, null, false))
        val plain = store.start("lib-1", "Game", standard, t0, genlockeId = "gl-x", leg = 3, carriedFrom = "nz-old", carry = heirs)
        val back = assertNotNull(store.load(plain.meta.id))
        assertTrue(back.meta.heirsIn.isEmpty())
        assertEquals("", back.meta.genlockeId)
        assertEquals(0, back.meta.leg)
    }

    // ---------------------------------------------------------------- a randomized run's ledger, after the randomizer

    @Test
    fun `after a randomize the ledgers of the game's other seeds are put in order`() {
        val kind = RomKind.EMERALD_U.id
        val now = "%016x".format(0xabL)
        val played = store.start(NuzlockeStore.bindOfRun(kind, 0x11L), "played", standard, t0)
        played.meta.started = true
        played.roster[1L] = mon(1L, "Treecko", 277)
        store.save(played)
        val failed = store.start(NuzlockeStore.bindOfRun(kind, 0x22L), "failed", standard, t0 + 1)
        val current = store.start(NuzlockeStore.bindOfRun(kind, 0xabL), "current", standard, t0 + 2)
        val otherGame = store.start(NuzlockeStore.bindOfRun(RomKind.FIRERED_U_V11.id, 0x33L), "other game", standard, t0 + 3)
        val library = store.start("lib-00000001", "library", standard, t0 + 4)

        assertEquals(2, store.settleRandomized(kind, now, t0 + 10))
        assertEquals(RunStatus.ABANDONED, assertNotNull(store.load(played.meta.id)).meta.status, "a run that was played is kept, marked replaced")
        assertNull(store.load(failed.meta.id), "a run never played is dropped")
        for ((name, keep) in listOf("current" to current, "other game" to otherGame, "library" to library)) {
            assertEquals(RunStatus.ACTIVE, assertNotNull(store.load(keep.meta.id), name).meta.status, name)
        }
        assertEquals(0, store.settleRandomized(kind, now, t0 + 11), "nothing more to do")
    }

    @Test
    fun `nothing is settled while the game or its seed is not known`() {
        val kind = RomKind.EMERALD_U.id
        val a = store.start(NuzlockeStore.bindOfRun(kind, 0x11L), "a", standard, t0)
        assertEquals(0, store.settleRandomized(kind, null, t0))
        assertEquals(0, store.settleRandomized(null, "00000000000000ab", t0))
        assertEquals(0, store.settleRandomized(kind, "?", t0))
        assertEquals(0, store.settleRandomized(kind, "", t0))
        assertEquals(RunStatus.ACTIVE, assertNotNull(store.load(a.meta.id)).meta.status)
    }

    @Test
    fun `a ledger someone has added to is not dropped as unplayed`() {
        val kind = RomKind.EMERALD_U.id
        val noted = store.start(NuzlockeStore.bindOfRun(kind, 0x11L), "noted", standard, t0)
        NuzlockeEdits(noted).addNote("I kept this by hand", t0 + 1)
        store.save(noted)
        assertEquals(1, store.settleRandomized(kind, "%016x".format(0xabL), t0 + 2))
        assertEquals(RunStatus.ABANDONED, assertNotNull(store.load(noted.meta.id)).meta.status)
    }

    // ---------------------------------------------------------------- backup

    @Test
    fun `the ledgers are in the backup and come back from it`() {
        val a = store.start("lib-1", "Game", standard, t0)
        a.roster[7L] = mon(7L, "Pidgey", 16)
        store.save(a)
        File(store.dir, a.meta.id + ".txt.tmp").writeText("half a write")
        assertTrue(Backup.admits("prep/nuzlocke/" + a.meta.id + ".txt"))
        val got = Backup.collect(filesDir)
        assertTrue("prep/nuzlocke/${a.meta.id}.txt" in got, "the run is in the backup")
        assertFalse(got.any { it.endsWith(".tmp") }, "a write cut short is not")
        val bytes = ByteArrayOutputStream().also { Backup.write(filesDir, it) }.toByteArray()
        val elsewhere = Files.createTempDirectory("restored").toFile()
        assertTrue(Backup.read(elsewhere, bytes.inputStream()) > 0)
        val restored = NuzlockeStore(elsewhere)
        assertEquals(listOf(a.meta.id), restored.list().map { it.header.id })
        assertEquals(listOf("Pidgey"), assertNotNull(restored.load(a.meta.id)).roster.values.map { it.speciesName })
    }
}
