package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.RunStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Your stats (CareerStats, 2026-09-30) on synthetic histories: every number worked out by hand from what is written
 * here, on an empty history, on files with damaged lines, and on the DS runs that two places both hold.
 */
class CareerStatsTest {
    private val t0 = 1_800_000_000_000L
    private val standard = NuzlockeRules.forPreset(NuzlockePreset.STANDARD)
    private val dirs = ArrayList<File>()
    private val tab = chr(9).toString()

    @BeforeTest fun fresh() { NuzlockeTracking.reset() }
    @AfterTest fun clean() { dirs.forEach { it.deleteRecursively() }; NuzlockeTracking.reset() }

    private fun chr(code: Int): Char = code.toChar()

    private fun filesDir(): File = Files.createTempDirectory("career").toFile().also { dirs += it; File(it, "prep").mkdirs() }

    private val LOST = RunRecord.Outcome.LOST
    private val WON = RunRecord.Outcome.WON

    private fun rec(
        attempt: Int, outcome: RunRecord.Outcome, badges: Int = 0, ruleset: String = "RSE Kaizo.rnqs",
        killer: String? = null, trainer: String = "", ended: Long = t0 + attempt * 60_000L, seed: String = "%016x".format(attempt.toLong()),
    ) = RunRecord(
        attempt = attempt, seed = seed, ruleset = ruleset, started = ended - 300_000L, ended = ended, playSeconds = 0, outcome = outcome,
        badges = badges, lead = RunRecord.Mon(1, "Lead", 5), killer = killer?.let { RunRecord.Mon(2, it, 6) }, trainer = trainer, location = "Route 1",
    )

    private fun ds(date: Long, seconds: Int, progress: Int, enemy: String = "ONIX"): PastRun {
        fun mon(name: String) = PastRun.RunMon(1, name, 10, 300, "Rock", "Ground", "Sturdy", listOf("Tackle"))
        return PastRun(date, seconds, mon("LEAD"), mon(enemy), "Route 1", 0, progress)
    }

    private fun inputs(
        records: Map<String, List<RunRecord>> = emptyMap(), attempts: Map<String, Int> = emptyMap(), clock: Map<String, Long> = emptyMap(),
        past: List<PastRun> = emptyList(), nuzlocke: List<RunStatus> = emptyList(),
    ) = CareerStats.Inputs(records, attempts, clock, past, nuzlocke)

    private fun name(id: String) = RomKind.byId(id)!!.displayName

    // ------------------------------------------------------------------ empty

    @Test
    fun `an empty history is all zeros and says so`() {
        val s = CareerStats.compute(inputs())
        assertEquals(CareerStats.EMPTY, s)
        assertTrue(s.isEmpty)
        assertEquals(0, s.runsStarted); assertEquals(0, s.wins); assertEquals(0L, s.playSeconds)
        assertNull(s.topCause); assertEquals(0, s.longestWinStreak); assertTrue(s.bests.isEmpty())
        assertEquals(listOf(0, 0, 0), listOf(s.nuzlockeStarted, s.nuzlockeFinished, s.nuzlockeLost))
    }

    @Test
    fun `a phone with no files at all, or no prep folder, reads as empty`() {
        val bare = Files.createTempDirectory("career-bare").toFile().also { dirs += it }
        assertEquals(CareerStats.EMPTY, CareerStats.read(bare))
        assertEquals(CareerStats.EMPTY, CareerStats.read(filesDir()))
        assertEquals(CareerStats.EMPTY, CareerStats.readOrEmpty(File(bare, "does/not/exist")))
    }

    // ------------------------------------------------------------- every number

    private val emeraldRuns = listOf(
        rec(1, LOST, 2, killer = "Mawile"),
        rec(2, LOST, 3, killer = "Mawile", trainer = "Leader Roxanne"),
        rec(3, WON, 8), rec(4, WON, 8), rec(5, WON, 8),
        rec(6, LOST, 4, ruleset = "RSE Super Kaizo.rnqs", killer = "Poochyena"),
        rec(8, WON, 8),                                   // attempt 7 was started and never ended
    )
    private val fireRedRuns = listOf(
        rec(1, LOST, 5, ruleset = "FRLG NatDex v1.2 Kaizo.rnqs", killer = "MAWILE", trainer = "Rival Blue"),
        rec(2, LOST, 1, ruleset = "FRLG Kaizo.rnqs", killer = "Geodude"),
    )
    private val faster = listOf(rec(1, LOST, 6, ruleset = "B2W2 Kaizo.rnqs", killer = "ZANGOOSE"), rec(2, LOST, 2, ruleset = "B2W2 Kaizo.rnqs", killer = "Onix"))
    private val plain = listOf(rec(1, LOST, 3, ruleset = "B2W2 Kaizo.rnqs", killer = "Zangoose"))

    private fun mixed() = inputs(
        records = mapOf("emerald-u" to emeraldRuns, "firered-u-v11" to fireRedRuns, "black2-u-faster" to faster, "black2-u" to plain),
        attempts = mapOf("emerald-u" to 12, "firered-u-v11" to 2, "black2-u-faster" to 4, "black2-u" to 3),
        clock = mapOf("emerald-u#1" to 600L, "emerald-u#2" to 1_200L, "firered-u-v11#1" to 300L, "emerald-u#7" to 100L),
        nuzlocke = listOf(RunStatus.ACTIVE, RunStatus.COMPLETE, RunStatus.COMPLETE, RunStatus.OVER, RunStatus.ABANDONED),
    )

    @Test
    fun `runs started come from the attempt counters, not from the runs that ended`() {
        // 12 + 2 + 4 + 3: Emerald's attempt 7 and the other runs that never ended count, as the counters count every new run.
        assertEquals(21, CareerStats.compute(mixed()).runsStarted)
        // A game with results and no counter (a backup restored in part) still has its results.
        assertEquals(7, CareerStats.compute(inputs(records = mapOf("emerald-u" to emeraldRuns))).runsStarted)
        // A counter with no results is runs started that never ended.
        assertEquals(5, CareerStats.compute(inputs(attempts = mapOf("emerald-u" to 5))).runsStarted)
        // Each game on its own: one with a counter ahead of its results, and one with results and no counter.
        val two = inputs(records = mapOf("emerald-u" to emeraldRuns.take(2), "firered-u-v11" to fireRedRuns), attempts = mapOf("emerald-u" to 5))
        assertEquals(5 + 2, CareerStats.compute(two).runsStarted)
    }

    @Test
    fun `wins, time and the way runs end are counted over every game`() {
        val s = CareerStats.compute(mixed())
        assertEquals(4, s.wins)
        // Everything the clock holds, the run in progress and the abandoned run included.
        assertEquals(2_200L, s.playSeconds)
        // Mawile ended three runs (MAWILE in capitals is the same Pokemon), Zangoose two, and the others one each.
        assertEquals("Mawile" to 3, s.topCause)
    }

    @Test
    fun `the winning streak is attempts that follow each other, in one game`() {
        assertEquals(3, CareerStats.compute(mixed()).longestWinStreak, "Emerald attempts 3, 4 and 5")
        // Attempt 3 of one game and attempt 4 of another are not two in a row.
        val two = CareerStats.compute(inputs(records = mapOf("emerald-u" to listOf(rec(3, WON)), "firered-u-v11" to listOf(rec(4, WON)))))
        assertEquals(1, two.longestWinStreak)
    }

    @Test
    fun `a streak needs runs that follow each other, and a run that was not won ends it`() {
        assertEquals(0, CareerStats.winStreak(emptyList()))
        assertEquals(0, CareerStats.winStreak(listOf(rec(1, LOST), rec(2, LOST))))
        assertEquals(1, CareerStats.winStreak(listOf(rec(1, WON))))
        assertEquals(3, CareerStats.winStreak(listOf(rec(3, WON), rec(1, WON), rec(2, WON))), "in attempt order, whatever order they were read in")
        assertEquals(1, CareerStats.winStreak(listOf(rec(1, WON), rec(3, WON))), "attempt 2 was started and not won: it ends the streak, though no result was kept for it")
        assertEquals(2, CareerStats.winStreak(listOf(rec(1, WON), rec(2, WON), rec(3, LOST), rec(4, WON))))
        assertEquals(1, CareerStats.winStreak(listOf(rec(1, WON), rec(1, WON, seed = "again"))), "the same attempt twice is not two in a row")
        assertEquals(1, CareerStats.winStreak(listOf(rec(1, WON), rec(2, RunRecord.Outcome.ENDED), rec(3, WON))))
        assertEquals(2, CareerStats.winStreak(listOf(rec(9, WON), rec(1, WON), rec(2, WON), rec(3, LOST), rec(4, WON), rec(6, WON), rec(7, WON))), "1 and 2, then 6 and 7 after a gap")
    }

    /** IronMON rules check R10 (2026-09-30): a win reached after going back in time says so, and ends no streak. */
    @Test
    fun `a win after state loads, retries or restarts is counted apart and does not make a streak`() {
        val rewound = rec(2, WON).copy(restores = 2)
        val restarted = rec(4, WON).copy(resets = 1)
        assertEquals(1, CareerStats.winStreak(listOf(rec(1, WON), rewound, rec(3, WON))), "the rewound win ends the streak")
        assertEquals(1, CareerStats.winStreak(listOf(rec(3, WON), restarted)), "a restart too")
        assertEquals(2, CareerStats.winStreak(listOf(rec(1, WON).copy(resumes = 3), rec(2, WON))), "a resume after the app closed was not chosen")
        val s = CareerStats.compute(inputs(records = mapOf("emerald-u" to listOf(rec(1, WON), rewound, rec(3, LOST)))))
        assertEquals(2, s.wins); assertEquals(1, s.winsAfterRewinds)
        // The count, and under it a line of its own for the wins that went back in time (rc32 audit P2 #15).
        assertEquals("2", StatsCopy.wins(s.wins))
        assertEquals("1 after state loads, retries or restarts", StatsCopy.winsNote(s.winsAfterRewinds))
        assertEquals("3", StatsCopy.wins(3))
        assertEquals(null, StatsCopy.winsNote(0))
        // The best run is the first win, which was clean; had only the rewound one won, the line says so.
        assertEquals(0, s.bests.single().rewinds)
        val onlyRewound = CareerStats.compute(inputs(records = mapOf("emerald-u" to listOf(rec(1, LOST, badges = 3), rewound)))).bests.single()
        assertEquals(2, onlyRewound.rewinds)
        assertEquals("Won. Attempt 2. After 2 state loads, retries or restarts.", StatsCopy.bestResult(onlyRewound))
        // The death card's shared line says a rewound best is one.
        val card = DeathCard("Emerald", rewound, rec(1, LOST, badges = 3), false)
        assertTrue(card.newBest)
        assertTrue(card.shareText().endsWith(" New best, after state loads, retries or restarts."), card.shareText())
        assertTrue(DeathCard("Emerald", rec(5, WON), rec(1, LOST, badges = 3), false).shareText().endsWith(" New best."))
    }

    @Test
    fun `the best run is kept for each game and mode, patches counting as the game they were made from`() {
        val s = CareerStats.compute(mixed())
        val byKey = s.bests.associateBy { it.game to it.mode }
        val emerald = name("emerald-u"); val fire = name("firered-u-v11"); val b2 = name("black2-u")
        assertEquals(
            listOf(b2 to "Kaizo", emerald to "Kaizo", emerald to "Super Kaizo", fire to "Kaizo", fire to "Kaizo (Nat. Dex)"),
            s.bests.map { it.game to it.mode }, "one line for each, by game and then mode",
        )
        // The first win, though later runs won too.
        assertEquals(BestRun(emerald, "Kaizo", won = true, badges = 8, attempt = 3, endedBy = null), byKey.getValue(emerald to "Kaizo"))
        // A loss says who ended it: the trainer when there was one, else the Pokemon.
        assertEquals(BestRun(emerald, "Super Kaizo", won = false, badges = 4, attempt = 6, endedBy = "Poochyena"), byKey.getValue(emerald to "Super Kaizo"))
        assertEquals(BestRun(fire, "Kaizo (Nat. Dex)", won = false, badges = 5, attempt = 1, endedBy = "Rival Blue"), byKey.getValue(fire to "Kaizo (Nat. Dex)"))
        assertEquals(BestRun(fire, "Kaizo", won = false, badges = 1, attempt = 2, endedBy = "Geodude"), byKey.getValue(fire to "Kaizo"))
        // Black 2 and Black 2 with the patch are one game: 6 badges beat 3.
        assertEquals(BestRun(b2, "Kaizo", won = false, badges = 6, attempt = 1, endedBy = "ZANGOOSE"), byKey.getValue(b2 to "Kaizo"))
    }

    @Test
    fun `among runs that got equally far the first one is the best, and a win beats any number of badges`() {
        val runs = listOf(rec(5, LOST, 4, killer = "B"), rec(2, LOST, 4, killer = "A"), rec(9, LOST, 3, killer = "C"))
        val best = CareerStats.compute(inputs(records = mapOf("emerald-u" to runs))).bests.single()
        assertEquals(2, best.attempt)
        assertEquals("A", best.endedBy)
        val won = CareerStats.compute(inputs(records = mapOf("emerald-u" to runs + rec(20, WON, 8)))).bests.single()
        assertTrue(won.won); assertEquals(20, won.attempt)
        assertNull(won.endedBy)
        // A run lost to the Champion with all 8 badges is not better than one won with 8: a win first, then badges.
        val champion = CareerStats.compute(inputs(records = mapOf("emerald-u" to listOf(rec(1, LOST, 8, killer = "Steven"), rec(2, WON, 8))))).bests.single()
        assertTrue(champion.won); assertEquals(2, champion.attempt)
        // Two wins: the first.
        assertEquals(20, CareerStats.compute(inputs(records = mapOf("emerald-u" to runs + rec(30, WON, 8) + rec(20, WON, 8)))).bests.single().attempt)
    }

    @Test
    fun `Nuzlocke runs are counted by how each one stands`() {
        val s = CareerStats.compute(mixed())
        assertEquals(5, s.nuzlockeStarted)
        assertEquals(2, s.nuzlockeFinished, "Champion beaten")
        assertEquals(1, s.nuzlockeLost)
        // A run in progress and a replaced one are started, and neither finished nor lost.
        val open = CareerStats.compute(inputs(nuzlocke = listOf(RunStatus.ACTIVE, RunStatus.ABANDONED)))
        assertEquals(listOf(2, 0, 0), listOf(open.nuzlockeStarted, open.nuzlockeFinished, open.nuzlockeLost))
        assertFalse(open.isEmpty, "a Nuzlocke alone is something to show")
    }

    @Test
    fun `the way runs end breaks a tie by name and ignores what is not a name`() {
        val runs = listOf(
            rec(1, LOST, killer = "Zubat"), rec(2, LOST, killer = "  Abra  "), rec(3, LOST, killer = "ABRA"), rec(4, LOST, killer = "zubat"),
            rec(5, LOST, killer = "   "), rec(6, LOST, killer = null), rec(7, WON, killer = "Mew"), rec(8, RunRecord.Outcome.ENDED, killer = "Mew"),
        )
        // Abra and Zubat two each: the earlier name in the alphabet. Only a lost run names what ended it.
        assertEquals("Abra" to 2, CareerStats.compute(inputs(records = mapOf("emerald-u" to runs))).topCause)
        assertNull(CareerStats.compute(inputs(records = mapOf("emerald-u" to listOf(rec(1, LOST), rec(2, WON, killer = "Mew"))))).topCause)
    }

    // ------------------------------------------------- DS runs from two places

    @Test
    fun `a DS run both the history and the DS past runs hold is counted once, and one only the log holds is counted from it`() {
        val end = t0 + 500_000L
        val history = listOf(rec(7, LOST, 3, ruleset = "B2W2 Kaizo.rnqs", killer = "Onix", ended = end))
        val past = listOf(
            ds(end + 5_000L, 60, PastRun.NOWHERE, "ONIX"),                       // the same run: written a moment after the history's record
            ds(end - 3_600_000L, 100, PastRun.PAST_LAB, "ZANGOOSE"),             // an hour before it: from before the history
            ds(end - 7_200_000L, 200, PastRun.WON),                              // a win from before the history
            ds(end + 120_000L, 30, PastRun.NOWHERE, "ONIX"),                     // two minutes after: the same run's line again
            ds(end + 120_001L, 40, PastRun.NOWHERE, "ONIX"),                     // later, with no record: not a run (rc32 audit P2 #14)
        )
        val s = CareerStats.compute(inputs(records = mapOf("black2-u" to history), attempts = mapOf("black2-u" to 9), clock = mapOf("black2-u#7" to 500L), past = past))
        assertEquals(1, s.wins, "the older win; the run the history also holds is not counted twice")
        assertEquals(500L + 100 + 200, s.playSeconds, "the clock, plus the seconds of the two runs from before the history")
        // The record's Onix and the older Zangoose, one each: the tie goes to the name first in the alphabet.
        assertEquals("Onix" to 1, s.topCause)
        // The counter holds every run that was started; the log's older runs are among them, not on top of them.
        assertEquals(9, s.runsStarted)
        // A DS past run has no mode and no attempt number: no best run and no streak come from it.
        assertEquals(1, s.bests.size)
        assertEquals(0, s.longestWinStreak)
    }

    /**
     * rc32 audit P2 #14: a DS loss logs its past run, and Retry left that line in the log; the run's end logged it again
     * with its record. The first line, matching no record, counted as a run from before the history: a second cause, its
     * seconds on top of the run clock. And rc31 logged a past run for a DS library game, which has no record at all.
     */
    @Test
    fun `a retried DS loss and a library game's line are not runs of their own`() {
        val start = t0
        val end = t0 + 30 * 60_000L
        val record = rec(7, LOST, 2, ruleset = "B2W2 Kaizo.rnqs", killer = "Onix", ended = end).copy(started = start)
        val past = listOf(
            ds(start + 10 * 60_000L, 600, PastRun.NOWHERE, "GEODUDE"),    // the loss that was retried
            ds(end + 1_000L, 1_800, PastRun.NOWHERE, "ONIX"),               // the run's own end, beside its record
            ds(start + 40 * 60_000L, 900, PastRun.WON, "LILLIPUP"),         // a library game, logged by rc31 as a run
        )
        val s = CareerStats.compute(inputs(records = mapOf("black2-u" to listOf(record)), attempts = mapOf("black2-u" to 7), clock = mapOf("black2-u#7" to 1_800L), past = past))
        assertEquals(0, s.wins)
        assertEquals(1_800L, s.playSeconds, "the run clock, and nothing on top of it")
        assertEquals("Onix" to 1, s.topCause)
        assertEquals(7, s.runsStarted)
        // A past run from before the first record still counts, whatever comes after it.
        val before = ds(start - 86_400_000L, 120, PastRun.WON)
        val withOlder = CareerStats.compute(inputs(records = mapOf("black2-u" to listOf(record)), clock = mapOf("black2-u#7" to 1_800L), past = past + before))
        assertEquals(1, withOlder.wins)
        assertEquals(1_800L + 120, withOlder.playSeconds)
        assertEquals(2, withOlder.runsStarted, "the record and the run from before it")
    }

    @Test
    fun `with no counter the results still make the runs started, the DS log included`() {
        val past = listOf(ds(t0, 10, PastRun.NOWHERE), ds(t0 + 1_000_000L, 20, PastRun.WON), ds(t0 + 2_000_000L, -5, PastRun.NOWHERE))
        val s = CareerStats.compute(inputs(past = past))
        assertEquals(3, s.runsStarted)
        assertEquals(1, s.wins)
        assertEquals(30L, s.playSeconds, "a damaged negative time counts as nothing")
        assertTrue(s.bests.isEmpty())
    }

    // ------------------------------------------------------- damaged files

    private fun prep(root: File, rel: String, text: String) { val f = File(root, "prep/$rel"); f.parentFile.mkdirs(); f.writeText(text) }

    @Test
    fun `damaged lines and files are skipped and the good ones count, in every place it reads`() {
        val root = filesDir()
        val good1 = rec(1, LOST, 2, killer = "Mawile").encode()
        val good2 = rec(2, WON, 8).encode()
        val junk = String(charArrayOf(chr(0), chr(1), chr(2))) + "junk"
        prep(root, "runhistory-emerald-u.tsv", listOf(good1, "not a record at all", "1" + tab + "2" + tab + "3", "", "x" + tab + good2.substringAfter(tab), good2.replace("WON", "MAYBE"), good2, junk).joinToString("\n") + "\n")
        File(root, "prep/runhistory-crystal-u.tsv").writeBytes(ByteArray(2000) { (it * 37 % 256).toByte() })   // a whole file of junk
        File(root, "prep/runhistory-folder.tsv").mkdirs()                                                     // a folder named like a history
        File(root, "prep/runhistory-emerald-u.tsv.tmp").writeText("half a write")                             // what SafeWrite leaves if killed
        prep(root, "attempts/emerald-u.txt", "seven")                     // not a number: no counter
        prep(root, "attempts/firered-u-v11.txt", "-5\n")                  // nonsense: no counter
        prep(root, "attempts/black2-u.txt", "7\n")
        prep(root, "attempts/notes.md", "9")                              // not a counter file
        prep(root, "run-clock.txt", listOf("emerald-u#1=600", "garbage", "=5", "emerald-u#2=abc", "emerald-u#3=-4", "emerald-u#4=90", "").joinToString("\n"))
        // The DS past runs: one good line written the way the log writes it, junk around it.
        val log = File(root, "prep/pastruns-Pokemon Black 2.tsv")
        PastRunStore(log).log(ds(t0 - 9_000_000L, 70, PastRun.WON))
        DiskWriter.drain()   // the log is written on the writer's thread (rc32 audit P2 #40): on disk before the junk goes after it
        log.appendText("junk\n1" + tab + "2\n")
        // Nuzlocke ledgers: three real ones (one finished, one lost, one going), and files that are not ledgers.
        val store = NuzlockeStore(root)
        val a = store.start("lib-a", "Game A", standard, t0)
        val b = store.start("lib-b", "Game B", standard, t0 + 1)
        store.start("lib-c", "Game C", standard, t0 + 2)
        store.load(a.meta.id)!!.also { it.meta.status = RunStatus.COMPLETE; store.save(it) }
        store.load(b.meta.id)!!.also { it.meta.status = RunStatus.OVER; store.save(it) }
        File(store.dir, "junk.txt").writeText("hello\nworld\n")
        File(store.dir, "empty.txt").writeText("")
        File(store.dir, "bin.txt").writeBytes(ByteArray(500) { (it * 31).toByte() })

        val s = CareerStats.read(root)
        // Emerald's two good records, black2's counter of 7 and the two bad counters as none: 2 + 7 (the older DS win is among the counter's).
        assertEquals(2 + 7, s.runsStarted)
        assertEquals(2, s.wins, "the good win, and the DS win from before the history")
        assertEquals(600L + 90 + 70, s.playSeconds, "the two clock lines that are seconds, and the DS run's own")
        assertEquals("Mawile" to 1, s.topCause)
        assertEquals(1, s.longestWinStreak)
        assertEquals(listOf(BestRun(name("emerald-u"), "Kaizo", won = true, badges = 8, attempt = 2, endedBy = null)), s.bests)
        assertEquals(listOf(3, 1, 1), listOf(s.nuzlockeStarted, s.nuzlockeFinished, s.nuzlockeLost))
        // The screen's own read is the same read.
        assertEquals(s, CareerStats.readOrEmpty(root))
        assertEquals(s, CareerStats.compute(CareerStats.Inputs.from(root)))
    }

    @Test
    fun `the clock file is read tolerantly, and a missing one reads as empty`() {
        val root = filesDir()
        prep(root, "run-clock.txt", "a#1=10\nbad\n=3\nb#2=x\nc#3=-1\nd#4=5\n")
        assertEquals(mapOf("a#1" to 10L, "d#4" to 5L), RunClock.readSeconds(File(root, "prep/run-clock.txt")))
        assertEquals(emptyMap(), RunClock.readSeconds(File(root, "prep/nothing.txt")))
    }

    // -------------------------------------------------------- names and words

    @Test
    fun `a mode is named as the settings file names it`() {
        assertEquals("Kaizo", CareerStats.modeLabel("RSE Kaizo.rnqs"))
        assertEquals("Super Kaizo", CareerStats.modeLabel("RSE Super Kaizo.rnqs"))
        assertEquals("Kaizo Doubles", CareerStats.modeLabel("B2W2 Kaizo Doubles.rnqs"))
        assertEquals("Chaos Kaizo (Nat. Dex)", CareerStats.modeLabel("FRLG NatDex v1.2 Chaos Kaizo.rnqs"))
        assertEquals("Kaizo", CareerStats.modeLabel("FRLG Kaizo (edited).RNQS"))
        assertEquals("My own run", CareerStats.modeLabel("My own run.rnqs"), "a file made by hand keeps its own name")
        assertEquals(StatsCopy.MODE_UNKNOWN, CareerStats.modeLabel(""))
        assertEquals(StatsCopy.MODE_UNKNOWN, CareerStats.modeLabel(".rnqs"))
    }

    @Test
    fun `a game is named as the Library names it, and a patched build is the game it was made from`() {
        assertEquals(name("black2-u"), CareerStats.gameName("black2-u-faster"))
        assertEquals(name("black2-u"), CareerStats.gameName("black2-u"))
        assertEquals(name("emerald-natdex-121"), CareerStats.gameName("emerald-natdex-121"), "Nat. Dex is its own build")
        assertEquals("some-future-game", CareerStats.gameName("some-future-game"), "an id this build does not know is shown as it is")
    }

    @Test
    fun `the numbers are said in plain words`() {
        assertEquals("0 min", StatsCopy.timePlayed(0))
        assertEquals("0 min", StatsCopy.timePlayed(59))
        assertEquals("1 min", StatsCopy.timePlayed(60))
        assertEquals("59 min", StatsCopy.timePlayed(3_599))
        assertEquals("1 h 0 min", StatsCopy.timePlayed(3_600))
        assertEquals("5 h 7 min", StatsCopy.timePlayed(5 * 3_600L + 7 * 60 + 59))
        assertEquals("0 min", StatsCopy.timePlayed(-5))
        assertEquals("Nothing yet", StatsCopy.cause(null))
        assertEquals("Zangoose, 1 run", StatsCopy.cause("Zangoose" to 1))
        assertEquals("Zangoose, 9 runs", StatsCopy.cause("Zangoose" to 9))
        assertEquals("Nothing yet", StatsCopy.streak(0)); assertEquals("1 run", StatsCopy.streak(1)); assertEquals("3 runs in a row", StatsCopy.streak(3))
        assertEquals("Won. Attempt 4.", StatsCopy.bestResult(BestRun("G", "Kaizo", won = true, badges = 8, attempt = 4, endedBy = null)))
        assertEquals("1 badge, lost to Leader Brock. Attempt 4.", StatsCopy.bestResult(BestRun("G", "Kaizo", won = false, badges = 1, attempt = 4, endedBy = "Leader Brock")))
        assertEquals("0 badges. Attempt 12.", StatsCopy.bestResult(BestRun("G", "Kaizo", won = false, badges = 0, attempt = 12, endedBy = null)))
        assertEquals("Game, Kaizo", StatsCopy.bestTitle(BestRun("Game", "Kaizo", won = true, badges = 8, attempt = 4, endedBy = null)))
    }

    @Test
    fun `every word the page says follows the copy rules`() {
        val em = 0x2014.toChar()
        assertTrue(StatsCopy.all.size >= 25, "the list of what the page says is not empty: ${StatsCopy.all.size}")
        for (s in StatsCopy.all) {
            assertTrue(s.isNotBlank() && s == s.trim(), "\"$s\" is plain text with no stray space")
            assertFalse(em in s || 0x2013.toChar() in s, "\"$s\" has a dash that is not a hyphen")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s) || Regex("(?i)artificial intelligence|machine learning|\\bGPT\\b|\\bLLM\\b").containsMatchIn(s), "\"$s\" says how the work is made")
            assertFalse('!' in s, "\"$s\" shouts: the voice is dry")
        }
    }
}
