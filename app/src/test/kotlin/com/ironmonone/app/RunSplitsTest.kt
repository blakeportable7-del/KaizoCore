package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A run's splits, the time played when each badge was earned (the stream's timer, 2026-10-05): kept per run with how
 * far it got (RunProgress), filed with the run (RunRecord.splits), and read back by a later attempt.
 */
class RunSplitsTest {
    private val dir: File = Files.createTempDirectory("splits").toFile()

    @AfterTest fun cleanup() { DiskWriter.drain(); RunProgress.forget(); dir.deleteRecursively() }

    private val kind = RomKind.EMERALD_U

    /** A store with a run of [kind] in it, made the way NEW RUN makes one, with [seed]. */
    private fun roll(store: PrepStore, seed: Long) {
        val prepared = File(dir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); if (!isFile) writeBytes(ByteArray(64) { 7 }) }
        val settings = File(dir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); if (!isFile) writeBytes(byteArrayOf(1)) }
        RunStart.start(store, kind, prepared, settings, seed = seed, app = "t", prePass = null, secondPass = null,
            take = { fail("a seed was chosen") }, stop = {}, randomize = { d, s -> d.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(d).writeText("log $s") })
    }

    private val lead = RunRecord.Mon(258, "MUDKIP", 31)

    @Test
    fun `a badge seen being earned gets the time played then, and nothing takes it away`() {
        val store = PrepStore(dir)
        roll(store, 0x10L)
        val session = GameSession.forRun(store.currentRunFor(kind), kind)
        var played = 0
        fun look(bits: Int) = RunProgress.note(store, session, Integer.bitCount(bits), lead, "Route 104", bits = bits, seconds = { played })
        played = 30; look(0)                     // the first look: no badges yet
        played = 754; look(0b1)                  // Stone Badge
        played = 900; look(0b1)                  // nothing new
        played = 1890; look(0b11)                // Knuckle Badge
        played = 2000; look(0b1)                 // a state from before Dewford: the split stays
        played = 2655; look(0b111)               // Dynamo Badge
        val attempt = store.attempt(kind.id); val seed = store.lastSeedText()
        assertEquals(mapOf(0 to 754, 1 to 1890, 2 to 2655), RunProgress.splitsOf(dir, attempt, seed))
        // Read back from the file, as after the app restarts.
        DiskWriter.drain(); RunProgress.forget()
        assertEquals(mapOf(0 to 754, 1 to 1890, 2 to 2655), RunProgress.splitsOf(dir, attempt, seed))
        assertEquals(emptyMap(), RunProgress.splitsOf(dir, attempt + 1, seed), "another attempt's are not these")
    }

    @Test
    fun `badges already there at the first look, or in a file from before splits, get no split`() {
        val store = PrepStore(dir)
        roll(store, 0x20L)
        val session = GameSession.forRun(store.currentRunFor(kind), kind)
        val attempt = store.attempt(kind.id); val seed = store.lastSeedText()
        // A file written before splits: badges, but no bits line.
        File(dir, "prep/run-progress.txt").writeText("attempt=$attempt\nseed=$seed\nbadges=2\nlead=258|MUDKIP|31\nlocation=Route 110\n")
        RunProgress.note(store, session, 2, lead, "Route 110", bits = 0b11, seconds = { 5000 })
        assertEquals(emptyMap(), RunProgress.splitsOf(dir, attempt, seed), "not seen being earned")
        RunProgress.note(store, session, 3, lead, "Route 110", bits = 0b111, seconds = { 6000 })
        assertEquals(mapOf(2 to 6000), RunProgress.splitsOf(dir, attempt, seed), "the next one is")
    }

    @Test
    fun `the format keeps the bits and splits and reads a file without them`() {
        val s = RunProgress.Seen(5, "ab", 3, lead, "Route 3", bits = 0b111, splits = mapOf(2 to 2655, 0 to 754, 1 to 1890))
        val text = RunProgress.format(s)
        assertEquals("splits=0:754,1:1890,2:2655", text.lines().single { it.startsWith("splits=") })
        assertEquals(s, RunProgress.parse(text))
        val old = RunProgress.parse("attempt=5\nseed=ab\nbadges=3\nlocation=x\n")!!
        assertEquals(-1, old.bits); assertEquals(emptyMap(), old.splits)
    }

    @Test
    fun `a run's record carries its splits, and a line from before them reads as none`() {
        val r = RunRecord(7, "ab", "Kaizo.rnqs", 1L, 2L, 4000, RunRecord.Outcome.LOST, 3, lead, null, "", "Route 3", splits = mapOf(0 to 754, 2 to 2655))
        assertEquals(r, RunRecord.decode(r.encode()))
        val before = r.copy(splits = emptyMap()).encode().substringBeforeLast('\t')   // the twenty columns a line had until now
        assertEquals(emptyMap(), RunRecord.decode(before)!!.splits)
        assertEquals(r.copy(splits = emptyMap()), RunRecord.decode(before))
        assertEquals(mapOf(1 to 5), RunRecord.parseSplits("1:5,x,3:,40:9,-1:2,2:-3"), "only bit:seconds pairs a badge can have")
    }

    @Test
    fun `a run replaced before it ended is filed with its splits, for the next attempt to compare with`() {
        val store = PrepStore(dir)
        roll(store, 0x30L)
        val session = GameSession.forRun(store.currentRunFor(kind), kind)
        RunProgress.note(store, session, 0, lead, "Route 101", bits = 0, seconds = { 10 })
        RunProgress.note(store, session, 1, lead, "Rustboro City", bits = 0b1, seconds = { 812 })
        roll(store, 0x31L)
        val filed = RunHistory(store.runHistoryFile(kind)).all().single()
        assertEquals(RunRecord.Outcome.ENDED, filed.outcome)
        assertEquals(mapOf(0 to 812), filed.splits)
    }

    @Test
    fun `the death card's filing takes the run's splits`() {
        // RunHistoryHook needs Play's session and trackers; the line that hands it the splits is pinned instead.
        val src = File("src/main/kotlin/com/ironmonone/app/RunHistory.kt").readText()
        assertEquals(1, Regex(Regex.escape("splits = RunProgress.splitsOf(store.files, attempt, seed),")).findAll(src).count())
        // And both polls hand RunProgress the badge bits and the clock.
        val progress = File("src/main/kotlin/com/ironmonone/app/RunProgress.kt").readText()
        assertEquals(2, Regex(Regex.escape("bits = s.badges, seconds = ::playedNow")).findAll(progress).count())
    }
}
