package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the app opens, when the welcome shows and what the Continue card says (2026-09-29), each driven
 * through the real PrepStore over a temp folder. The app opens on Home. The one exception is being
 * reopened after it closed in the middle of a game (CrashResume's marker), which must still open on Play
 * so the game comes back where it was.
 */
class HomeStartTest {
    private val filesDir = Files.createTempDirectory("homestart").toFile()
    private val store = PrepStore(filesDir)
    private val marker = File(filesDir, "prep/playing.txt")
    private val kind = RomKind.EMERALD_U

    /** A randomized run on disk with its settings and [attempts] counted: what the app used to open on Play for. */
    private fun runWaiting(settings: String = "RSE Kaizo.rnqs", attempts: Int = 1) {
        val run = store.currentRunFor(kind)
        run.parentFile.mkdirs(); run.writeBytes(ByteArray(64) { 1 })
        File(filesDir, "prep/settings/$settings").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        store.saveLastRun(kind.id, settings)
        repeat(attempts) { store.bumpAttempt(kind.id) }
    }

    private fun dsRom(title: String, code: String): ByteArray {
        val r = ByteArray(0x1000)
        title.forEachIndexed { i, c -> r[i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        return r
    }

    // ---------------------------------------------------------------- where the app opens

    @Test
    fun `a normal launch opens on Home, with nothing set up and with a run waiting`() {
        assertEquals("HOME", store.startingPoint())
        runWaiting()
        // Until 2026-09-29 a run waiting opened the app on Play. Now Home holds a Continue card for it.
        assertEquals("HOME", store.startingPoint())
    }

    @Test
    fun `reopening after the app closed in the middle of a game opens on Play`() {
        CrashResume.playing(marker, GameSession.RUN_ID)
        assertEquals("PLAY", store.startingPoint(), "a run that was up")
        CrashResume.playing(marker, "lib-1f1c08fb")
        assertEquals("PLAY", store.startingPoint(), "a Library game that was up, with no run on the phone at all")
        CrashResume.resuming(marker, GameSession.RUN_ID)
        assertEquals("PLAY", store.startingPoint(), "a resume that itself died is opened on Play too, where it says so")
    }

    @Test
    fun `a marker with no game in it is not a resume`() {
        for (junk in listOf("", "hello", "playing", "playing ", "resuming", "paused run")) {
            marker.parentFile.mkdirs(); marker.writeText(junk)
            assertEquals("HOME", store.startingPoint(), "\"$junk\"")
        }
    }

    @Test
    fun `once Play has closed the normal way the next launch opens on Home`() {
        CrashResume.playing(marker, GameSession.RUN_ID)
        assertEquals("PLAY", store.startingPoint())
        CrashResume.closed(marker)
        assertEquals("HOME", store.startingPoint())
        CrashResume.playing(marker, GameSession.RUN_ID)
        CrashResume.left(marker, finishing = true, destroyed = true)
        assertEquals("HOME", store.startingPoint(), "the app finishing")
    }

    @Test
    fun `an activity Android rebuilds while a game is up comes back on Play`() {
        // Destroyed without finishing (a font size change): the marker is kept for the rebuilt activity's Play screen.
        CrashResume.playing(marker, GameSession.RUN_ID)
        CrashResume.left(marker, finishing = false, destroyed = true)
        assertEquals("PLAY", store.startingPoint())
        // And on Home, where Play is not up and its marker is already gone, it comes back on Home.
        CrashResume.closed(marker)
        assertEquals("HOME", store.startingPoint())
    }

    @Test
    fun `asking where to open does not use up the marker Play resumes from`() {
        CrashResume.playing(marker, GameSession.RUN_ID)
        assertEquals("PLAY", store.startingPoint())
        assertEquals("PLAY", store.startingPoint())
        // Play's core-up makes CrashResume's one read of it per process. If the question above had made it, this would be null.
        assertEquals(CrashResume.Left(GameSession.RUN_ID, resuming = false), CrashResume.leftover(marker))
        assertNull(CrashResume.leftover(marker), "and that is still the once it is meant to be")
        assertEquals("PLAY", store.startingPoint(), "the file itself is left as it was")
    }

    @Test
    fun `a staged demo opens where every launch used to, on Play when a run is waiting`() {
        assertEquals("HOME", store.startingPoint("gba-battle"), "no run to stage it on")
        runWaiting()
        assertEquals("PLAY", store.startingPoint("gba-battle"))
        assertEquals("PLAY", store.startingPoint("update"))
        assertEquals("HOME", store.startingPoint(), "without a demo it is Home")
    }

    // ---------------------------------------------------------------- the welcome

    @Test
    fun `the welcome shows once`() {
        assertTrue(Welcome.due(filesDir), "a new install")
        assertTrue(Welcome.showAtLaunch(filesDir, "HOME", null))
        assertTrue(Welcome.markSeen(filesDir))
        assertFalse(Welcome.due(filesDir))
        assertFalse(Welcome.showAtLaunch(filesDir, "HOME", null), "never again")
        assertTrue(Welcome.markSeen(filesDir), "answering twice is harmless")
        assertFalse(Welcome.showAtLaunch(filesDir, "HOME", null))
    }

    @Test
    fun `the welcome flag is a file directly in the files folder, not under prep, and not in the backup`() {
        Welcome.markSeen(filesDir)
        val flag = Welcome.flag(filesDir)
        assertTrue(flag.isFile)
        assertEquals(filesDir.canonicalFile, flag.parentFile.canonicalFile)
        assertFalse(flag.relativeTo(filesDir).path.replace('\\', '/').startsWith("prep/"))
        assertFalse(Backup.admits(Welcome.FILE), "a phone restoring a backup still has none of the player's dumps (the library and prepared ROMs are left out), so the welcome is still true there")
    }

    @Test
    fun `the welcome never covers a resume or a staged demo, and asking does not spend it`() {
        assertFalse(Welcome.showAtLaunch(filesDir, "PLAY", null), "a game the app is bringing back")
        assertFalse(Welcome.showAtLaunch(filesDir, "HOME", "gba-battle"), "a demo lands on what it stages")
        assertTrue(Welcome.due(filesDir), "neither wrote the flag")
        assertTrue(Welcome.showAtLaunch(filesDir, "HOME", null), "so the first ordinary launch still gets it")
    }

    @Test
    fun `the welcome is due on a phone that already has games, once, the way it is on any first launch of this build`() {
        runWaiting()
        assertEquals("HOME", store.startingPoint())
        assertTrue(Welcome.showAtLaunch(filesDir, store.startingPoint(), null))
    }

    // ---------------------------------------------------------------- the Continue card

    @Test
    fun `nothing in progress, no Continue card`() {
        assertNull(ContinueCard.read(store))
        // A game picked and randomized once, whose run file is gone: Play would have nothing to open either.
        store.saveLastRun(kind.id, "RSE Kaizo.rnqs")
        assertNull(ContinueCard.read(store))
    }

    @Test
    fun `a run says its game, its mode and its attempt`() {
        runWaiting(attempts = 12)
        assertEquals(ContinueInfo(kind.displayName, "Kaizo IronMON, attempt 12"), ContinueCard.read(store))
        runWaiting(settings = "RSE Super Kaizo.rnqs", attempts = 0)
        // Attempts count per settings file since 2026-09-30 (PrepStore): Super Kaizo has none counted yet.
        assertEquals(ContinueInfo(kind.displayName, "Super Kaizo IronMON"), ContinueCard.read(store), "the mode of the settings in play, and its own count")
        runWaiting(settings = "RSE Super Kaizo.rnqs", attempts = 2)
        assertEquals(ContinueInfo(kind.displayName, "Super Kaizo IronMON, attempt 2"), ContinueCard.read(store))
    }

    @Test
    fun `a run with no attempt counted names only its mode, and one with no known mode says it is an IronMON run`() {
        runWaiting(attempts = 0)
        assertEquals(ContinueInfo(kind.displayName, "Kaizo IronMON"), ContinueCard.read(store))
        runWaiting(settings = "My run.rnqs", attempts = 3)
        assertEquals(ContinueInfo(kind.displayName, "${HomeCopy.RUN_DETAIL}, attempt 3"), ContinueCard.read(store))
    }

    @Test
    fun `the Library game Play would open is the one named, and the run comes back when Play is pointed at it again`() {
        runWaiting(attempts = 4)
        val game = store.library.import("Mario Kart DS.nds", dsRom("MARIO KART", "AMCE"))
        store.library.selectLibrary(game)
        assertEquals(ContinueInfo("Mario Kart DS", HomeCopy.LIBRARY_DETAIL), ContinueCard.read(store), "Play opens this, not the run")
        store.library.selectRun()
        assertEquals(ContinueInfo(kind.displayName, "Kaizo IronMON, attempt 4"), ContinueCard.read(store))
    }

    @Test
    fun `a game with a Nuzlocke run names the mode on the card, ended or not`() {
        val game = store.library.import("Mario Kart DS.nds", dsRom("MARIO KART", "AMCE"))
        store.library.selectLibrary(game)
        val nz = NuzlockeStore(filesDir)
        val bind = assertNotNull(NuzlockeStore.bindOf(store.session(), store.runIdentity()))
        val run = nz.start(bind, "Mario Kart DS", com.ironmonone.tracker.nuzlocke.NuzlockeRules.forPreset(com.ironmonone.tracker.nuzlocke.NuzlockePreset.STANDARD), 1_000L)
        assertEquals(ContinueInfo("Mario Kart DS", "Standard Nuzlocke"), ContinueCard.read(store, nz))
        assertEquals(ContinueInfo("Mario Kart DS", HomeCopy.LIBRARY_DETAIL), ContinueCard.read(store), "no store asked, no Nuzlocke line")
        // Blake, 2026-09-30: "It should say what game mode were last playing on", after a whiteout too.
        run.meta.status = com.ironmonone.tracker.nuzlocke.RunStatus.OVER
        assertTrue(nz.save(run))
        assertEquals(ContinueInfo("Mario Kart DS", "Standard Nuzlocke, run over"), ContinueCard.read(store, nz))
        // And how it ended, when the run says (Blake: "If it ended you can add how it ended").
        run.meta.endReason = "Whiteout"
        assertTrue(nz.save(run))
        assertEquals(ContinueInfo("Mario Kart DS", "Standard Nuzlocke, run over: Whiteout"), ContinueCard.read(store, nz))
        run.meta.status = com.ironmonone.tracker.nuzlocke.RunStatus.COMPLETE
        assertTrue(nz.save(run))
        assertEquals(ContinueInfo("Mario Kart DS", "Standard Nuzlocke, champion beaten"), ContinueCard.read(store, nz))
        // A run replaced by a newer one is history, not the mode in play.
        run.meta.status = com.ironmonone.tracker.nuzlocke.RunStatus.ABANDONED
        assertTrue(nz.save(run))
        assertEquals(ContinueInfo("Mario Kart DS", HomeCopy.LIBRARY_DETAIL), ContinueCard.read(store, nz))
    }

    @Test
    fun `a Library game with no run at all still gets a card`() {
        val game = store.library.import("Mario Kart DS.nds", dsRom("MARIO KART", "AMCE"))
        store.library.selectLibrary(game)
        assertEquals(ContinueInfo("Mario Kart DS", HomeCopy.LIBRARY_DETAIL), ContinueCard.read(store))
    }

    @Test
    fun `a Library game that was deleted falls back to the run, as Play does`() {
        runWaiting(attempts = 2)
        val game = store.library.import("Mario Kart DS.nds", dsRom("MARIO KART", "AMCE"))
        store.library.selectLibrary(game)
        assertTrue(game.file.delete())
        assertEquals(ContinueInfo(kind.displayName, "Kaizo IronMON, attempt 2"), ContinueCard.read(store))
        assertNull(store.library.selectedLibraryName(), "and the dead pointer is cleared, as session() always did")
    }
}
