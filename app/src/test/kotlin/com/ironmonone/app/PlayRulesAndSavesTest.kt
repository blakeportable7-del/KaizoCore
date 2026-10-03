package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.RunStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The UX audit's run fixes (2026-09-30): the IronMON popup only in a Kaizo IronMON run (P0-1), a Nuzlocke's own rules
 * behind Rules (P0-6), and a randomized Nuzlocke that keeps its rules through NEW RUN (P0-8). And what a new seed does
 * with the in-game save: it stays, in every game (Blake, the same day, over the audit's P0-2).
 */
class PlayRulesAndSavesTest {
    private val filesDir = Files.createTempDirectory("playrules").toFile()

    init { NuzlockeTracking.reset() }

    @AfterTest fun cleanUp() { NuzlockeTracking.reset(); filesDir.deleteRecursively() }

    // ------------------------------------------------------------------ a Gen 3 save, built by hand

    /**
     * 128 KB of flash with both copies of the save written. Section 1 sits at [slot1At] in each copy (the game
     * rotates the order), and its party count is [partyA] in copy A and [partyB] in copy B.
     */
    private fun gen3Save(frlg: Boolean, partyA: Int, counterA: Long, partyB: Int? = null, counterB: Long = 0, slot1At: Int = 3): ByteArray {
        val b = ByteArray(128 * 1024) { 0xFF.toByte() }
        fun put32(at: Int, v: Long) { for (i in 0 until 4) b[at + i] = (v ushr (8 * i)).toByte() }
        fun copy(base: Int, party: Int, counter: Long) {
            for (pos in 0 until 14) {
                val id = (pos - slot1At + 1 + 14) % 14
                val at = base + pos * 4096
                b[at + 0xFF4] = id.toByte(); b[at + 0xFF5] = 0
                put32(at + 0xFF8, 0x08012025L)
                put32(at + 0xFFC, counter)
                if (id == 1) put32(at + if (frlg) 0x34 else 0x234, party.toLong())
            }
        }
        copy(0, partyA, counterA)
        if (partyB != null) copy(14 * 4096, partyB, counterB)
        return b
    }

    @Test
    fun `the party count is read from section 1 of the newest copy, at each family's own offset`() {
        assertEquals(0, SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 0, counterA = 5), frlg = false))
        assertEquals(3, SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 3, counterA = 5), frlg = false))
        assertEquals(2, SaveCheck.gen3PartyCount(gen3Save(frlg = true, partyA = 2, counterA = 5), frlg = true))
        // FireRed's count read at Emerald's offset lands in the map view, not the party.
        assertNotEquals(2, SaveCheck.gen3PartyCount(gen3Save(frlg = true, partyA = 2, counterA = 5), frlg = false))
        assertEquals(4, SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 1, counterA = 5, partyB = 4, counterB = 6), frlg = false), "copy B is newer")
        assertEquals(1, SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 1, counterA = 9, partyB = 4, counterB = 6), frlg = false), "copy A is newer")
        assertEquals(6, SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 6, counterA = 1, slot1At = 0), frlg = false), "found by id, wherever it sits")
        assertNull(SaveCheck.gen3PartyCount(gen3Save(frlg = false, partyA = 9, counterA = 1), frlg = false), "more than six is not a party")
        assertNull(SaveCheck.gen3PartyCount(ByteArray(128 * 1024) { 0xFF.toByte() }, frlg = false), "erased flash")
    }

    // ------------------------------------------------------------------ what a new seed does with the save

    private val emerald = RomKind.EMERALD_U
    private fun saveFor(kind: RomKind, bytes: ByteArray): File =
        RunSaves.file(filesDir, kind, File(filesDir, "prep/runs/current.${kind.fileExtension}")).apply { parentFile.mkdirs(); writeBytes(bytes) }

    @Test
    fun `a new seed keeps the in-game save in every game, a team and all`() {
        // Blake, 2026-09-30: "Should be able to open a save on a new seed for all games"; New Game is the clean start.
        val team = gen3Save(frlg = false, partyA = 2, counterA = 7)
        val save = saveFor(emerald, team)
        assertEquals(RunSaves.Plan.HOLDS_TEAM, RunSaves.onNewSeed(save, emerald))
        assertContentEquals(team, save.readBytes(), "Continue opens it")
        assertFalse(RunSaves.setAside(save).exists(), "nothing is set aside")
        val introSkip = gen3Save(frlg = false, partyA = 0, counterA = 7)
        saveFor(emerald, introSkip)
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.onNewSeed(save, emerald))
        assertContentEquals(introSkip, save.readBytes())
        // Blake's own case: Nat. Dex FireRed, saved in front of the three balls.
        val natDex = RomKind.FIRERED_NATDEX_121
        val balls = gen3Save(frlg = true, partyA = 0, counterA = 3)
        val natDexSave = saveFor(natDex, balls)
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.onNewSeed(natDexSave, natDex))
        assertContentEquals(balls, natDexSave.readBytes())
        val ds = RomKind.all.first { it.platform == Platform.NDS }
        val dsBytes = ByteArray(512 * 1024).also { it[40] = 9 }
        val dsSave = saveFor(ds, dsBytes)
        assertEquals(RunSaves.Plan.UNREAD, RunSaves.onNewSeed(dsSave, ds))
        assertContentEquals(dsBytes, dsSave.readBytes())
    }

    @Test
    fun `what a save holds, Nat Dex builds included`() {
        val missing = RunSaves.file(filesDir, emerald, File("current.gba"))
        assertEquals(RunSaves.Plan.NONE, RunSaves.plan(missing, emerald))
        assertEquals(RunSaves.Plan.NONE, RunSaves.plan(saveFor(emerald, ByteArray(128 * 1024) { 0xFF.toByte() }), emerald))
        // The Nat. Dex builds keep vanilla's sections and party count (NatDexSaveLayoutTest, on the ROMs themselves).
        for (natDex in RomKind.allNatDex) {
            val frlg = natDex.family == "FRLG"
            assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.plan(saveFor(natDex, gen3Save(frlg, partyA = 0, counterA = 1)), natDex), natDex.id)
            assertEquals(RunSaves.Plan.HOLDS_TEAM, RunSaves.plan(saveFor(natDex, gen3Save(frlg, partyA = 1, counterA = 1)), natDex), natDex.id)
        }
        // A DS run's save is melonDS's, beside the run's ROM name, and its party is not read.
        val ds = RomKind.all.first { it.platform == Platform.NDS }
        val dsSave = saveFor(ds, ByteArray(512 * 1024).also { it[40] = 9 })
        assertEquals("current.sav", dsSave.name)
        assertEquals(File(filesDir, "saves"), dsSave.parentFile)
        assertEquals(RunSaves.Plan.UNREAD, RunSaves.plan(dsSave, ds))
    }

    @Test
    fun `a save rc32's first builds set aside comes back when there is none, never over one`() {
        val natDex = RomKind.FIRERED_NATDEX_121
        val save = RunSaves.file(filesDir, natDex, File("current.gba"))
        val balls = gen3Save(frlg = true, partyA = 0, counterA = 4)
        RunSaves.setAside(save).apply { parentFile.mkdirs(); writeBytes(balls) }
        // The dialog says what the new seed opens with before anything moves.
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.planOnNewSeed(save, natDex))
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.onNewSeed(save, natDex))
        assertContentEquals(balls, save.readBytes())
        assertFalse(RunSaves.setAside(save).exists())
        // A save the game never wrote (blank flash) is no save: the copy takes its place.
        save.writeBytes(ByteArray(128 * 1024) { 0xFF.toByte() })
        RunSaves.setAside(save).writeBytes(balls)
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.onNewSeed(save, natDex))
        assertContentEquals(balls, save.readBytes())
        // A written save stays, and the copy is left alone.
        val team = gen3Save(frlg = true, partyA = 1, counterA = 9)
        save.writeBytes(team)
        RunSaves.setAside(save).writeBytes(balls)
        assertEquals(RunSaves.Plan.HOLDS_TEAM, RunSaves.planOnNewSeed(save, natDex))
        assertEquals(RunSaves.Plan.HOLDS_TEAM, RunSaves.onNewSeed(save, natDex))
        assertContentEquals(team, save.readBytes())
        assertContentEquals(balls, RunSaves.setAside(save).readBytes())
    }

    @Test
    fun `NEW RUN hands the old seed's save to the new ROM as it is`() {
        val store = PrepStore(filesDir)
        val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
        val settings = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val stub: (File, Long) -> Unit = { dest, seed -> dest.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(dest).writeText("log $seed") }
        val save = File(filesDir, "saves/${emerald.id}.srm")
        val team = gen3Save(frlg = false, partyA = 3, counterA = 2)
        save.parentFile.mkdirs(); save.writeBytes(team)
        RunStart.start(store, emerald, prepared, settings, seed = 0x11L, app = "t", prePass = null, secondPass = null,
            take = { fail("a seed was chosen") }, stop = {}, randomize = stub)
        assertContentEquals(team, save.readBytes(), "Continue opens it; New Game on the title screen is the clean start")
        assertFalse(RunSaves.setAside(save).exists())
        assertTrue(RunEvents(File(filesDir, "prep/integrity.txt")).entries().none { it.kind == RunEvents.Kind.KEPT_SAVE })
    }

    // ------------------------------------------------------------------ which rules the game in Play follows

    @Test
    fun `the IronMON popup belongs to a Kaizo IronMON run, Rules to a Nuzlocke's ledger`() {
        assertEquals(PlayRules.Kind.IRONMON, PlayRules.kind(isRun = true, nuzlocke = false))
        assertEquals(PlayRules.Kind.NUZLOCKE, PlayRules.kind(isRun = true, nuzlocke = true), "a randomized Nuzlocke")
        assertEquals(PlayRules.Kind.NUZLOCKE, PlayRules.kind(isRun = false, nuzlocke = true))
        assertEquals(PlayRules.Kind.PLAIN, PlayRules.kind(isRun = false, nuzlocke = false))
        assertTrue(PlayRules.ironmonGameOver(PlayRules.Kind.IRONMON))
        assertFalse(PlayRules.ironmonGameOver(PlayRules.Kind.NUZLOCKE))
        assertFalse(PlayRules.ironmonGameOver(PlayRules.Kind.PLAIN))
        assertTrue(PlayRules.nuzlockeRules(PlayRules.Kind.NUZLOCKE))
        assertFalse(PlayRules.nuzlockeRules(PlayRules.Kind.IRONMON))
    }

    @Test
    fun `a latch that does not apply never fires, and fires again once it does`() {
        val latch = GameOverLatch(GameOverFamily.GEN3)
        latch.applies = false
        assertFalse(latch.onRead(RunOutcome.LOST, null))
        assertFalse(latch.open, "no GAME OVER in a Nuzlocke or a library game")
        latch.applies = true
        assertTrue(latch.onRead(RunOutcome.LOST, null))
        assertTrue(latch.open)
    }

    @Test
    fun `Play asks the rules before it opens the popup, the rulebook or a new run`() {
        val host = File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertTrue("latch.applies = PlayRules.ironmonGameOver(PlayRules.kind(session, filesDir))" in host)
        assertTrue(host.indexOf("latch.applies =") < host.indexOf("if (!latch.open || hidden) return"), "set before the early return, every time")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex("""if \(NuzlockeTracking\.inPlay\(\)\) NuzlockeLedgerRequest\.openRules\(\) else rulesDialog = true""").findAll(play).count(),
            "RULES in the File menu and Rules in Tracker Setup")
        // The running game's battery save is written before the dialog reads it (Blake, 2026-09-30: it said "no save").
        assertTrue("NewRunConfirmDialog(beforeRead = { persistSram() }, onConfirm = { confirmNewRun = false; newRun() }" in play)
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        val dialog = side.substringAfter("fun NewRunConfirmDialog(").substringBefore("internal object NewRunCopy")
        assertTrue(dialog.indexOf("beforeRead()") in 1 until dialog.indexOf("RunSaves.planOnNewSeed("), "written, then read")
        assertTrue("NuzlockeLedgerRequested()" in side, "the ledger opens from SideScreenDialogs, tracker shown or not")
        val prep = File("src/main/kotlin/com/ironmonone/app/PrepStore.kt").readText().replace("\r\n", "\n")
        val install = prep.substringAfter("fun installRun(").substringBefore("\n    }\n")
        assertTrue(install.indexOf("RunSaves.onNewSeed(") in 1 until install.indexOf("rotateRuns(kind)"), "the old run's save, before its ROM rotates")
    }

    /**
     * rc32 audit P3 #64: the call sites that make UX P0-1, P0-4 and P0-8 real could each be deleted with the suite green,
     * because the tests drove the helpers alone. Each is held here, to the one line that matters.
     */
    @Test
    fun `A+B+Start offers a new run for a run only, and the call sites behind the fixes stay in place`() {
        // P0-1: PlayScreen arms the combo in every game; the dialog asks first, through PlayRules.
        val library = GameSession(File(filesDir, "FireRed.gba"), Platform.GBA, RomKind.FIRERED_U_V10, "FireRed", "lib-1", isRun = false)
        val run = GameSession.forRun(File(filesDir, "run.gba"), RomKind.FIRERED_U_V10)
        assertFalse(PlayRules.newRunOffered(library), "a library game has no run to start")
        assertTrue(PlayRules.newRunOffered(run))
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        val dialog = src("SideScreens.kt").substringAfter("fun NewRunConfirmDialog(").substringBefore("internal object NewRunCopy")
        val gate = dialog.indexOf("if (session == null || !PlayRules.newRunOffered(session)) {")
        assertTrue(gate in 1 until dialog.indexOf("beforeRead()"), "refused before anything is read or written")
        assertTrue("return" in dialog.substring(gate, dialog.indexOf("beforeRead()")), "and the refusal returns")
        val play = src("PlayScreen.kt")
        assertTrue("NewRunCombo.onFire = { confirmNewRun = true }" in play)
        // P0-1: both panels take their own game-over card through ironmonGameOverCard, not the bare condition.
        assertTrue("val ironmonOver = ironmonGameOverCard(state?.gameOver != null)" in src("TrackerPanel.kt"))
        assertTrue("state.gameOver != null && ironmonOver -> {" in src("TrackerPanel.kt"))
        assertTrue("val ironmonOver = ironmonGameOverCard(state?.runOver != null)" in src("NdsTrackerPanel.kt"))
        assertTrue("&& ironmonOver -> {" in src("NdsTrackerPanel.kt"))
        // P0-8: NEW NUZLOCKE gives the next game its ledger, inside newRun.
        val newRun = play.substringAfter("    fun newRun() {").substringBefore("\n    }\n")
        assertTrue("NuzlockeStore(context.applicationContext.filesDir).startNextRandomized(it, k, started.seed, System.currentTimeMillis())" in newRun)
        // P0-4: back where the game was left. The core coming up reads the marker, and leaving Play clears it.
        assertTrue("CrashResume.atCoreUp(store.playMarker, session, StateSlots.auto(context.filesDir, session), store.stateStamp(session)," in play)
        val left = play.indexOf("CrashResume.left(store.playMarker,")
        assertTrue(left > 0 && left - play.lastIndexOf("onDispose {", left) < 400, "inside the dispose that leaves Play")
    }

    @Test
    fun `the new-run words say what happens to the save, with no dashes`() {
        assertTrue("stays" in NewRunCopy.save(RunSaves.Plan.HOLDS_TEAM) && "holds this run's team" in NewRunCopy.save(RunSaves.Plan.HOLDS_TEAM))
        assertTrue("New Game starts fresh" in NewRunCopy.save(RunSaves.Plan.HOLDS_TEAM))
        assertTrue("skips the intro" in NewRunCopy.save(RunSaves.Plan.NO_TEAM))
        assertTrue("Continue on the title screen opens it" in NewRunCopy.save(RunSaves.Plan.UNREAD))
        assertTrue("no save" in NewRunCopy.save(null))
        val all = listOf(NewRunCopy.IRONMON, NewRunCopy.NUZLOCKE, NewRunCopy.NOT_A_RUN) +
            RunSaves.Plan.entries.map { NewRunCopy.save(it) } + NewRunCopy.save(null)
        for (s in all) assertFalse('—' in s || '–' in s, s)
        // rc32's first builds set the save aside; nothing says so now.
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertFalse("clean save" in side)
        assertFalse("Keep my in-game save" in side)
    }

    // ------------------------------------------------------------------ a randomized Nuzlocke through NEW RUN

    @Test
    fun `NEW RUN in a randomized Nuzlocke gives the next game a ledger with the same rules`() {
        val nz = NuzlockeStore(filesDir)
        val hardcore = NuzlockeRules.forPreset(NuzlockePreset.HARDCORE)
        val t0 = 1_800_000_000_000L
        val first = nz.start(NuzlockeStore.bindOfRun(emerald.id, 0x1L), "Pokémon Emerald (U), randomized", hardcore, t0)
        val next = nz.startNextRandomized(first, emerald, 0x2L, t0 + 1)
        assertEquals(NuzlockeStore.bindOfRun(emerald.id, 0x2L), next.meta.bind)
        assertEquals(hardcore, next.meta.rules)
        assertEquals(RunStatus.ACTIVE, next.meta.status)
        assertEquals(RunStatus.ABANDONED, nz.load(first.meta.id)?.meta?.status, "a run still going is replaced, and stays in the list")
        assertEquals(next.meta.id, nz.current(NuzlockeStore.bindOfRun(emerald.id, 0x2L))?.header?.id)

        // A run that ended keeps its ending.
        val lost = nz.load(next.meta.id)!!.also { it.meta.status = RunStatus.OVER; nz.save(it) }
        nz.startNextRandomized(lost, emerald, 0x3L, t0 + 2)
        assertEquals(RunStatus.OVER, nz.load(lost.meta.id)?.meta?.status)
    }
}
