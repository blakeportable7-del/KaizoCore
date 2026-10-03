package com.ironmonone.app

import com.ironmonone.tracker.nuzlocke.NuzlockeEdits
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the Nuzlocke parts are joined to the rest of the app, read from the sources (2026-09-29). The screens
 * cannot be run here, so what would silently break them is pinned instead: the one line in the tracker, what
 * feeds the ledger, how each kind of run starts, and that nothing but the store writes a ledger.
 */
class NuzlockeWiringTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun src(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    private val tracker = src("TrackerPanel.kt")
    private val dsTracker = src("NdsTrackerPanel.kt")
    private val play = src("PlayScreen.kt")
    private val panel = src("NuzlockePanel.kt")
    private val screen = src("NuzlockeScreen.kt")
    private val store = src("NuzlockeStore.kt")

    /** The body of the screen's start function, where a run is made. */
    private val startNow = screen.substringAfter("fun startNow() {").substringBefore("fun tryStart()")

    // ---------------------------------------------------------------- the tracker

    @Test
    fun `the tracker draws the Nuzlocke panel once, under the team and above the state cards`() {
        assertEquals(1, Regex("""NuzlockePanel\(state\)""").findAll(tracker).count())
        val at = tracker.indexOf("NuzlockePanel(state)")
        assertTrue(tracker.indexOf("PcTeamView(") in 0 until at, "under the team view")
        assertTrue(at < tracker.indexOf("unsupportedNote != null -> PcCard"), "above the cards the state picks between")
    }

    @Test
    fun `nothing the ledger or an adapter throws can end the Play screen's polling loop`() {
        for (fn in listOf("fun observe(", "fun observeNds(")) {
            val body = store.substringAfter(fn).substringBefore("\n    }\n")
            assertTrue("runCatching {" in body && ".getOrDefault(false)" in body, "$fn keeps its own failures")
        }
    }

    @Test
    fun `the DS tracker draws the Nuzlocke panel once, under its team and above the state cards`() {
        assertEquals(1, Regex("""NuzlockeNdsPanel\(state\)""").findAll(dsTracker).count())
        val at = dsTracker.indexOf("NuzlockeNdsPanel(state)")
        assertTrue(dsTracker.indexOf("timer?.let { RunTimerLine(it)") in 0 until at, "after the run timer line")
        assertTrue(at < dsTracker.indexOf("state == null -> PcCard"), "above the cards the state picks between")
    }

    @Test
    fun `the DS panel feeds the ledger through the DS adapter, the same way the others do`() {
        val ds = panel.substringAfter("fun NuzlockeNdsPanel(state: NdsTrackerState?)").substringBefore("@Composable")
        assertTrue("if (state == null) return" in ds)
        assertTrue("NuzlockeTracking.current(context.applicationContext.filesDir) ?: return" in ds)
        assertTrue("val snapshot = remember(state) { NdsNuzlocke.snapshot(state) }" in ds)
        assertTrue("NuzlockePanelBody(live, snapshot)" in ds, "the same body, so the ledger looks the same on every game")
    }

    @Test
    fun `the Play screen feeds the ledger from each console's own poll, and from nowhere else`() {
        val feeds = Regex("""NuzlockeTracking\.observe(Nds)?\(context\.applicationContext\.filesDir, (\w+)\)""").findAll(play).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(listOf("Nds" to "ndsState", "" to "trackerState", "" to "trackerState"), feeds, "DS, then Game Boy, then Game Boy Advance")
        assertEquals(3, Regex("""NuzlockeTracking\.observe""").findAll(play).count(), "no other use of the tracking object in the composable")
        // Each sits in a polling loop after the state was read (and after the demo replaced it), never in the composable body.
        val nds = play.indexOf("NuzlockeTracking.observeNds(")
        assertTrue(play.lastIndexOf("Demo.nds(t, m)", nds) in 0 until nds)
        val gb = play.indexOf("NuzlockeTracking.observe(context.applicationContext.filesDir, trackerState)")
        assertTrue(play.lastIndexOf("Demo.gb2(gbc, m)", gb) in 0 until gb, "after the Game Boy demo")
        for (call in Regex("""NuzlockeTracking\.observe(Nds)?\(""").findAll(play).map { it.range.first }) {
            assertTrue(insideLaunchedEffect(play, call), "the call at $call is in a LaunchedEffect's lambda, not in the composable body")
        }
    }

    /** True when [at] is inside the braces of the nearest LaunchedEffect before it: the lambda that polls, which the ART verifier counts apart from the composable. */
    private fun insideLaunchedEffect(text: String, at: Int): Boolean {
        val start = text.lastIndexOf("LaunchedEffect(", at)
        if (start < 0) return false
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until at) if (text[i] == '{') depth++ else if (text[i] == '}') depth--
        return depth > 0
    }

    @Test
    fun `the panel draws nothing unless the tracker read the game and a run is in play`() {
        assertTrue("if (state?.nuz == null) return" in panel)
        assertTrue("val live = NuzlockeTracking.current(context.applicationContext.filesDir) ?: return" in panel)
    }

    @Test
    fun `the panel feeds the ledger with every state the tracker reports, and a tap opens the ledger`() {
        assertTrue("val snapshot = remember(state) { NuzlockeAdapters.snapshot(state) }" in panel, "one adapter picks Gen 3 or a Game Boy game")
        assertTrue("LaunchedEffect(snapshot) { if (snapshot != null && live.feed(snapshot)) tick++ }" in panel)
        assertTrue(".clickable { open = true }" in panel)
        assertTrue("if (open) NuzlockeLedgerDialog(live, snapshot, onClose = { open = false }" in panel)
    }

    @Test
    fun `every correction the edits offer can be made from the ledger`() {
        val offered = NuzlockeEdits::class.java.methods
            .filter { it.declaringClass == NuzlockeEdits::class.java && Modifier.isPublic(it.modifiers) }
            .map { it.name }.toSet()
        assertTrue(offered.size >= 12, "the reflection found only $offered")
        for (fn in offered) assertTrue("live.edits.$fn(" in panel, "$fn is offered by NuzlockeEdits and nothing in the ledger calls it")
    }

    @Test
    fun `a death marked by hand takes the place and badges the game shows, and a Pokemon that never was can go`() {
        // rc32 audit P3 #115: MARK DEAD recorded the catch area and no badges.
        assertTrue("MonEditor(live, mon, here, snapshot?.badges, rev," in panel)
        assertTrue("live.edits.markDead(mon.id, cause.trim(), now, here?.name, badges)" in panel)
        // rc32 audit P2 #139: a Pokemon made for an area, or added by hand, can be taken off; one the tracker saw cannot.
        val remove = panel.substringAfter("if (mon.id < 0 && !mon.inParty) {").substringBefore("\n        }\n")
        assertTrue("live.edits.removeMon(mon.id, now)" in remove && "KEEP IT" in remove, "a second tap to be sure")
    }

    @Test
    fun `the mid-run rules page leaves out the Genlocke switch, which only a start reads`() {
        // rc32 audit P2 #39: flipping it in the middle of a run changed nothing.
        val rulesTab = panel.substringAfter("private fun RulesTab(").substringBefore("\n}\n")
        assertTrue("it.key != \"genlocke\"" in rulesTab)
        assertTrue("meta.genlockeId.isNotEmpty() || rules.preset == NuzlockePreset.GENLOCKE" in rulesTab, "survivors are said only for a Genlocke")
        assertTrue("val canContinue = NuzlockeStarts.canContinue(h, status)" in screen)
    }

    @Test
    fun `the ledger has a tab for what the player can do to each part of it`() {
        for (tab in listOf("AREAS", "TEAM", "GRAVE", "LOG", "RULES")) assertTrue("""LedgerTab.$tab ->""" in panel, tab)
        assertTrue("AreasTab(live, here, rev, onEdit = { areaEdit = it })" in panel)
        assertTrue("TeamTab(live, rev, onEdit = { monEdit = it }, onAdd = { addOpen = true })" in panel)
        assertTrue("GraveTab(live, rev, onEdit = { monEdit = it })" in panel)
    }

    // ---------------------------------------------------------------- the screen

    @Test
    fun `the screen has the signature the shell calls`() {
        val signature = screen.substringAfter("fun NuzlockeScreen(").substringBefore(") {")
        assertTrue("modifier: Modifier = Modifier," in signature && "onPlay: () -> Unit = {}," in signature && "onAddGame: () -> Unit = {}," in signature, signature)
    }

    @Test
    fun `Randomizer makes its game through RunJob with a seed of its own, and the ledger is made first for that seed`() {
        assertTrue("val seed = SecureRandom().nextLong()" in startNow)
        val ledger = startNow.indexOf("NuzlockeStore.bindOfRun(prepared.first.id, seed)")
        // A Nuzlocke counts no IronMON attempt (2026-09-30, IronMON rules check R8).
        val job = startNow.indexOf("RunJob.randomize(context, prepared, settingsFile, seed, nuzlocke = true)")
        assertTrue(ledger in 0 until job, "the ledger is tied to the seed before the game exists")
        assertEquals(1, Regex("""RunJob\.randomize\(""").findAll(screen).count(), "one way to randomize, and it never asks for a seed of the job's choosing")
        assertTrue("if (RunJob.busy)" in startNow)
    }

    @Test
    fun `a randomize that cannot begin leaves no ledger behind`() {
        val refused = startNow.substringAfter("if (!RunJob.randomize(").substringBefore("waiting = true")
        assertTrue("nz.delete(ledger.meta.id)" in refused)
    }

    @Test
    fun `every other preset plays the Library game as it is and never randomizes`() {
        val plain = startNow.substringAfter("} else {")
        assertTrue("store.library.selectLibrary(entry)" in plain)
        assertTrue("nz.start(" in plain && "session.id" in plain)
        assertFalse("RunJob" in plain, "a plain start touches no run")
        assertFalse("randomize" in plain)
        assertTrue("onPlay()" in plain)
    }

    @Test
    fun `games come from the lists the Run and Library tabs use`() {
        assertTrue("NuzlockeStarts.plainGames(runCatching { store.library.list() }" in screen)
        assertTrue("NuzlockeStarts.randomGames(runCatching { store.listPrepared() }" in screen)
        assertTrue("RulesetCatalog.forRom(it.first, settingsList)" in screen)
        assertTrue("store.seedBundledPresets(context)" in screen, "the modes are the settings files, and this tab can be opened first")
    }

    @Test
    fun `a new run goes to Play only while the screen is showing, and never clears another screen's hook`() {
        assertTrue("RunJob.onRunReady = mine" in screen)
        assertTrue("if (RunJob.onRunReady === mine) RunJob.onRunReady = null" in screen)
    }

    @Test
    fun `a randomize's outcome is put in order once the randomizer is idle`() {
        assertTrue("nz.settleRandomized(store.loadLastRun()?.first, store.lastSeed()" in screen)
        assertTrue("if (!busy)" in screen.substringAfter("LaunchedEffect(busy, jobGeneration)").substringBefore("}"), "not while a randomize is running")
    }

    @Test
    fun `replacing a run in progress is asked about first`() {
        assertTrue("StartConfirm(" in screen.substringAfter("fun tryStart()"))
        assertTrue("running != null" in screen)
        assertTrue("enabled = problem == null && confirm == null" in screen, "Start waits for the answer")
    }

    /** RC35-NOTICED N #40: the plain start's question listed every ledger on the main thread (nz.current). */
    @Test
    fun `the plain start's question reads the ledgers off the main thread`() {
        val tryStart = screen.substringAfter("fun tryStart() {").substringBefore("\n    }\n")
        assertTrue("withContext(Dispatchers.IO) { runCatching { bind?.let { nz.current(it) } }" in tryStart)
        assertEquals(1, Regex("""nz\.current\(""").findAll(tryStart).count(), "and nowhere else in it")
        assertTrue("starting = true" in tryStart && "finally { starting = false }" in tryStart, "Start is held meanwhile")
    }

    /**
     * RC35-NOTICED N #16, the rest of rc32 audit P2 #63: the lists were read in composition, and listPrepared reads a
     * randomized build whose checksum is not in its memo whole, seconds on the main thread for a DS game.
     */
    @Test
    fun `the games, the settings files and the ledgers are read off the main thread`() {
        assertTrue("produceState<NuzlockeLists?>(null, refresh) {" in screen)
        assertTrue("value = withContext(Dispatchers.IO) { NuzlockeLists.read(store, nz, refresh) }" in screen)
        for (old in listOf("remember(refresh) { NuzlockeStarts.plainGames", "remember(refresh) { NuzlockeStarts.randomGames",
            "remember(refresh) { runCatching { store.listSettings() }", "remember(refresh) { nz.list() }"))
            assertFalse(old in screen, old)
        // What the read gives: the settings files on disk, and nothing the store cannot read.
        val files = java.nio.file.Files.createTempDirectory("nzlists").toFile()
        try {
            val prep = PrepStore(files)
            File(files, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            val lists = NuzlockeLists.read(prep, NuzlockeStore(files), 7)
            assertEquals(7, lists.refresh)
            assertEquals(listOf("RSE Kaizo.rnqs"), lists.settings.map { it.name })
            assertTrue(lists.prepared.isEmpty() && lists.runs.isEmpty())
        } finally { files.deleteRecursively() }
    }

    /** RC35-NOTICED N #13, the rest of rc32 audit P2 #33: the screen's choices were plain remember, lost to a process death. */
    @Test
    fun `the start's choices are kept with the activity`() {
        for (v in listOf("preset", "rules", "typePick", "gameKey", "modeKey"))
            assertTrue(Regex("""var $v by rememberSaveable[ (]""").containsMatchIn(screen), v)
        assertTrue("rememberSaveable(stateSaver = NuzlockeChoices.RulesSaver)" in screen)
        val rules = com.ironmonone.tracker.nuzlocke.NuzlockeRules.forPreset(com.ironmonone.tracker.nuzlocke.NuzlockePreset.MONOTYPE, 11)
            .copy(giftsCount = true, dupes = false)
        assertEquals(rules, NuzlockeChoices.rulesOf(NuzlockeChoices.rulesText(rules)))
        for (p in com.ironmonone.tracker.nuzlocke.NuzlockePreset.entries)
            assertEquals(p, com.ironmonone.tracker.nuzlocke.NuzlockePreset.byKey(p.key), "each preset has a key that reads back")
    }

    // ---------------------------------------------------------------- the store

    @Test
    fun `the store writes only through SafeWrite and only in its own folder`() {
        assertTrue("SafeWrite.text(file, text)" in store)
        assertFalse("writeText(" in store)
        assertFalse("FileWriter" in store || "FileOutputStream" in store || "appendText(" in store)
        // Backup.kt names the folder to take it along; nothing else may, or a second writer could appear.
        val owners = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Backup.kt" && "\"prep/nuzlocke" in it.readText() }
            .map { it.name }.toList()
        assertEquals(listOf("NuzlockeStore.kt"), owners, "nothing else names the ledger folder")
    }

    @Test
    fun `the ledgers are in the backup`() {
        assertTrue(File(dir, "Backup.kt").readText().contains("\"prep/nuzlocke/\""))
        assertTrue(Backup.admits("prep/nuzlocke/nz-x.txt"))
    }
}
