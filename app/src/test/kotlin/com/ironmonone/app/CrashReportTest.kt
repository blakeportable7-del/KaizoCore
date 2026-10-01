package com.ironmonone.app

import android.app.ApplicationExitInfo
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Crash reports that go to Blake (CrashReport, CrashLog): what is scrubbed, what is sent and how it
 * is cut to fit, when a report may go, the Java stack the handler saves and where it lands in the
 * report, and the network end of it against a real server on this machine.
 *
 * Non-ASCII and control characters are built from code points below, never written as escapes in
 * this file: an escape written here has been turned into the character itself on the way to disk.
 */
class CrashReportTest {

    private val BS = 92.toChar().toString()
    private val E_ACUTE = 0xE9.toChar().toString()
    private val KANJI = String(intArrayOf(0x65E5, 0x672C), 0, 2)
    private val EMOJI = String(Character.toChars(0x1F600))
    private val EM_DASH = 0x2014.toChar()
    private val REPLACEMENT = 0xFFFD.toChar().toString()

    private fun tmp(): File = Files.createTempDirectory("crashreport").toFile()

    // ------------------------------------------------------------------ a JSON reader of the test's own

    /** Shares nothing with the encoder, and is strict: a raw control character inside a string is an error. */
    private class MiniJson(private val s: String) {
        private var i = 0

        fun parse(): Any? {
            val v = value()
            ws()
            check(i == s.length) { "text after the value, at $i" }
            return v
        }

        private fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }

        private fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c' at $i")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            check(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        private fun num(): Long {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toLong()
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                check(s[i] == '"') { "a key was expected at $i" }
                val k = str()
                ws()
                check(s[i++] == ':') { "a colon was expected at ${i - 1}" }
                m[k] = value()
                ws()
                when (val c = s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> error("bad object separator '$c' at ${i - 1}")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                when (val c = s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> error("bad array separator '$c' at ${i - 1}")
                }
            }
        }

        private fun str(): String {
            check(s[i] == '"')
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> when (val e = s[i++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append(8.toChar())
                        'f' -> sb.append(12.toChar())
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> error("bad escape '$e' at ${i - 1}")
                    }
                    c.code < 0x20 -> error("a raw control character (${c.code}) inside a string at ${i - 1}")
                    else -> sb.append(c)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String): Map<String, Any?> = MiniJson(json).parse() as Map<String, Any?>

    private fun body(report: String, family: String? = "FRLG"): String =
        CrashReport.payload("1.0.0-rc30+c8e0a70 (39)", "Google Pixel 7", "Android 14 (API 34)", family, report)

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    // ------------------------------------------------------------------ scrub

    @Test
    fun `a file name in an exception message goes with the whole tail of its path`() {
        val name = "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba"
        val text = "java.io.FileNotFoundException: /storage/emulated/0/ROMs/$name (No such file or directory)\n" +
            "\tat java.io.FileInputStream.open0(Native Method)"
        val out = CrashReport.scrub(text)
        assertEquals("java.io.FileNotFoundException: <path>\n\tat java.io.FileInputStream.open0(Native Method)", out)
        for (part in listOf("Pokemon", "FireRed", "USA", "Europe", "Rev 1", ".gba", "ROMs", "emulated")) assertFalse(part in out, part)
    }

    @Test
    fun `a content uri is gone, encoded name and all`() {
        val text = "java.io.FileNotFoundException: content://com.android.externalstorage.documents/document/primary%3AROMs%2FEmerald.gba: open failed: ENOENT"
        val out = CrashReport.scrub(text)
        assertEquals("java.io.FileNotFoundException: <path>", out)
        for (part in listOf("Emerald", "ROMs", "primary", "externalstorage")) assertFalse(part in out, part)
    }

    @Test
    fun `every place a player's files live is cut to the end of its line`() {
        val starts = listOf(
            "/storage/emulated/0/Download/x", "/sdcard/Download/x", "/sdcard0/x", "/mnt/sdcard/x", "/mnt/user/0/primary/x",
            "/data/user/0/com.ironmonone.app/files/prep/runs/current.gba", "/data/data/com.ironmonone.app/files/x",
            "/data/media/0/x", "content://media/external/file/12", "file:///storage/emulated/0/x", "CONTENT://media/x",
        )
        for (p in starts) {
            val out = CrashReport.scrub("before $p tail with spaces (and more)\nnext line stays")
            assertEquals("before <path>\nnext line stays", out, p)
        }
    }

    @Test
    fun `a quoted name is replaced whole, with or without spaces`() {
        assertEquals("could not read <file> from the card", CrashReport.scrub("could not read \"Emerald.sav\" from the card"))
        assertEquals("bad header in <file>.", CrashReport.scrub("bad header in \"Pokemon - Emerald Version (USA, Europe).GBA\"."))
        assertEquals("cannot open <file>: denied", CrashReport.scrub("cannot open 'my hack.ips': denied"))
    }

    @Test
    fun `a bare name ending in a game or save extension is replaced whatever its case`() {
        val exts = "gba gbc gb nds 3ds cia cci zip 7z sav srm dsv state st0 st5 st9 ips ups bps xdelta".split(' ')
        for (e in exts) for (x in listOf(e, e.uppercase(), e.replaceFirstChar { it.uppercase() })) {
            assertEquals("failed to open <file> (denied)", CrashReport.scrub("failed to open Emerald_v1.$x (denied)"), x)
            assertEquals("<file>", CrashReport.scrub("Emerald.$x"), x)
            assertEquals("<file>.", CrashReport.scrub("Emerald.$x."), x)
            assertEquals("<file>: gone", CrashReport.scrub("Emerald.$x: gone"), x)
            assertEquals("<file>, <file>", CrashReport.scrub("Emerald.$x, Ruby.$x"), x)
        }
        assertFalse("Emerald" in CrashReport.scrub("open(Emerald.sav)"))
    }

    /** Everything a fix reads, in the shape the reports have: none of it may change. */
    private val CLEAN = listOf(
        "KaizoCore - crash report",
        "app 1.0.0-rc30+c8e0a70 (39)",
        "device Google Pixel 7, Android 14 (API 34)",
        "",
        "---- 2026-09-29 10:00:00 ----",
        "reason      CRASH_NATIVE (signal in native code)",
        "status      0",
        "importance  100",
        "process     com.ironmonone.app",
        "rss         412384 kB",
        "tombstone   crash-trace-1790000000000.bin (21480 bytes)",
        "            signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0000000000000018",
        "            Cause: null pointer dereference",
        "            #00 pc 000000000006e2f4  /data/app/~~x==/com.ironmonone.app-y==/lib/arm64/libmgba_libretro_android.so (retro_run+412)",
        "            #01 pc 0000000000012a90  /data/app/~~x==/com.ironmonone.app-y==/base.apk!libretrodroid.so (offset 0x123000)",
        "java        thread main",
        "            java.lang.IllegalStateException: boom",
        "            \tat com.ironmonone.app.PlayScreenKt.PlayScreen(PlayScreen.kt:2440)",
        "            \tat com.ironmonone.tracker.nds.NdsTracker.read(NdsTracker.kt:77)",
        "            \tat com.ironmonone.tracker.gba.State.load(State.kt:12)",
        "            \tat java.util.zip.ZipFile.<init>(ZipFile.java:230)",
        "            \tat libcore.io.Linux.open(Native Method)",
        "            Caused by: java.io.IOException: read failed",
        "            \t... 12 more",
    ).joinToString("\n")

    @Test
    fun `library frames, signals, causes and stack frames come through untouched`() {
        assertEquals(CLEAN, CrashReport.scrub(CLEAN))
        // The package names that end in an extension, in the middle of a frame, are frames and not files.
        assertTrue("com.ironmonone.tracker.nds.NdsTracker.read(NdsTracker.kt:77)" in CrashReport.scrub(CLEAN))
    }

    @Test
    fun `only the lines with something to hide change`() {
        val text = CLEAN + "\nCaused by: java.io.FileNotFoundException: /storage/emulated/0/x/Ruby.gba (denied)\nlast line ok"
        val out = CrashReport.scrub(text)
        assertEquals(CLEAN + "\nCaused by: java.io.FileNotFoundException: <path>\nlast line ok", out)
    }

    @Test
    fun `scrubbing twice is scrubbing once`() {
        val text = "a /storage/emulated/0/x.gba\nb \"Ruby.sav\" c Emerald.nds d\ne content://x/y.gba"
        val once = CrashReport.scrub(text)
        assertEquals(once, CrashReport.scrub(once))
    }

    @Test
    fun `a huge token with no spaces is not scanned again from every character`() {
        // Sized so that a scan from every character (60,000 squared, about half a minute) is caught by the wait
        // below and still ends by itself, while one scan is a millisecond or two.
        val out = StringBuilder()
        val t = Thread { out.append(CrashReport.scrub("x".repeat(60_000) + " and " + "y".repeat(60_000) + ".gba")) }
        t.isDaemon = true
        t.start()
        t.join(5_000)
        assertFalse(t.isAlive, "scrub took longer than five seconds on one long token")
        assertEquals("x".repeat(60_000) + " and <file>", out.toString())
    }

    @Test
    fun `the email and share paths scrub the crash and the log too`() {
        val dev = Feedback.Device("Pixel", "Android 14 (API 34)", "1.0.0-rc30")
        val out = Feedback.compose(
            dev, "FRLG", "it froze", "I/KaizoCore: opened /storage/emulated/0/ROMs/Emerald.gba ok\nI/KaizoCore: fine",
            crash = "java.io.FileNotFoundException: content://x/primary%3AROMs%2FRuby.gba\n\tat a.B.c(B.kt:1)",
        )
        assertFalse("Emerald" in out || "Ruby" in out || "ROMs" in out, out)
        assertTrue("I/KaizoCore: opened <path>" in out)
        assertTrue("java.io.FileNotFoundException: <path>" in out)
        assertTrue("I/KaizoCore: fine" in out && "\tat a.B.c(B.kt:1)" in out)
    }

    // ---------------------------------------------------------------- payload

    @Test
    fun `the payload has the shape the server reads`() {
        val json = body("KaizoCore - crash report\napp x\n")
        assertTrue(
            json.startsWith("{\"v\":1,\"app\":\"1.0.0-rc30+c8e0a70 (39)\",\"device\":\"Google Pixel 7\"," +
                "\"android\":\"Android 14 (API 34)\",\"family\":\"FRLG\",\"report\":\""), json,
        )
        assertTrue(json.endsWith("\"}"))
        val o = parse(json)
        assertEquals(listOf("v", "app", "device", "android", "family", "report"), o.keys.toList())
        assertEquals(1L, o["v"])
        assertEquals("FRLG", o["family"])
        assertEquals("KaizoCore - crash report\napp x\n", o["report"])
    }

    @Test
    fun `a missing family is json null`() {
        val json = body("r", family = null)
        assertTrue("\"family\":null," in json, json)
        val o = parse(json)
        assertTrue(o.containsKey("family"))
        assertNull(o["family"])
    }

    @Test
    fun `quotes, backslashes, newlines, tabs, control characters and non-ASCII survive a round trip`() {
        val tricky = "say \"hi\" \\ back" + BS + "n\nline two\r\n\ttabbed" +
            1.toChar() + 31.toChar() + 8.toChar() + 12.toChar() + 127.toChar() +
            " caf$E_ACUTE $KANJI $EMOJI </script> {\"a\":[1,2]}"
        val json = body(tricky)
        assertEquals(tricky, parse(json)["report"])
        // The wire form: no raw control character anywhere, and the escapes the spec names.
        assertTrue(json.none { it.code < 0x20 }, "a raw control character reached the body")
        assertTrue("\\n" in json && "\\r" in json && "\\t" in json)
        assertTrue((BS + "u0001") in json && (BS + "u001f") in json && (BS + "u0008") in json && (BS + "u000c") in json)
        assertTrue("\\\"hi\\\"" in json && "\\\\" in json)
        // Non-ASCII goes out as itself, in UTF-8.
        assertTrue(E_ACUTE in json && KANJI in json && EMOJI in json)
    }

    @Test
    fun `the short fields are escaped as well`() {
        val o = parse(CrashReport.payload("a\"b", "d" + BS + "e", "f\ng", "h\ti", "r"))
        assertEquals("a\"b", o["app"])
        assertEquals("d" + BS + "e", o["device"])
        assertEquals("f\ng", o["android"])
        assertEquals("h\ti", o["family"])
    }

    @Test
    fun `half of a surrogate pair becomes the replacement character`() {
        val lone = "a" + 0xD83D.toChar() + "b" + 0xDE00.toChar() + "c"
        val json = body(lone)
        assertEquals("a" + REPLACEMENT + "b" + REPLACEMENT + "c", parse(json)["report"])
        // And a whole pair is left alone.
        assertEquals("x${EMOJI}y", parse(body("x${EMOJI}y"))["report"])
    }

    @Test
    fun `the report is scrubbed on its way into the body`() {
        val report = "KaizoCore - crash report\nCaused by: java.io.FileNotFoundException: /storage/emulated/0/ROMs/Ruby.gba (denied)\n"
        val kept = parse(body(report))["report"] as String
        assertEquals("KaizoCore - crash report\nCaused by: java.io.FileNotFoundException: <path>\n", kept)
    }

    private fun stampOf(i: Int) = "2026-09-29 %02d:%02d:00".format(Locale.US, 23 - i / 60, 59 - i % 60)

    private fun section(i: Int, frames: Int) = buildString {
        appendLine("---- ${stampOf(i)} ----")
        appendLine("reason      CRASH (unhandled Java exception)")
        appendLine("status      2")
        for (n in 1..frames) appendLine("java        frame $n com.ironmonone.app.SomeClass.someMethod(SomeClass.kt:$n)")
        appendLine()
    }

    private fun bigReport(sections: Int, frames: Int) = buildString {
        appendLine("KaizoCore - crash report")
        appendLine("app 1.0.0-rc30+c8e0a70 (39)")
        appendLine("device Google Pixel 7, Android 14 (API 34)")
        appendLine()
        for (i in 0 until sections) append(section(i, frames))
    }

    @Test
    fun `a report that fits is sent as it is`() {
        val report = bigReport(sections = 3, frames = 5)
        assertEquals(report, parse(body(report))["report"])
    }

    @Test
    fun `a long report is trimmed from the end in whole lines, keeps the header and the newest crash, and says so`() {
        val report = bigReport(sections = 200, frames = 40)
        assertTrue(bytes(report) > 500_000)
        val json = body(report)
        val size = bytes(json)
        assertTrue(size <= 60_000, "$size bytes")
        assertTrue(size > 59_800, "kept nearly all that fits, not less: $size bytes")

        val kept = parse(json)["report"] as String
        val lines = kept.split("\n")
        assertEquals("KaizoCore - crash report", lines[0])
        assertEquals("[trimmed]", lines.last())
        assertFalse(kept.endsWith("\n"))
        // The newest crash is there whole, and what is kept is the top of the original, line for line.
        assertTrue(section(0, 40).trimEnd() in kept)
        val keptLines = lines.dropLast(1)
        assertEquals(report.lines().take(keptLines.size), keptLines)
        assertTrue(keptLines.size < report.lines().size)
        // The oldest is gone.
        assertFalse(stampOf(199) in kept)
    }

    @Test
    fun `the limit is on the bytes of the whole body, to the byte`() {
        val overhead = bytes(body(""))
        val exact = "x".repeat(60_000 - overhead)
        assertEquals(60_000, bytes(body(exact)))
        assertEquals(exact, parse(body(exact))["report"])
        val over = body(exact + "x")
        assertTrue(bytes(over) <= 60_000)
        assertTrue((parse(over)["report"] as String).endsWith("[trimmed]"))
    }

    @Test
    fun `bytes are counted, not characters`() {
        val report = "header line\n" + (E_ACUTE.repeat(120) + "\n").repeat(300)
        assertTrue(report.length < 60_000 && bytes(report) > 60_000, "under 60,000 characters and over 60,000 bytes is the point")
        val json = body(report)
        assertTrue(bytes(json) <= 60_000)
        val kept = parse(json)["report"] as String
        assertTrue(kept.startsWith("header line\n") && kept.endsWith("[trimmed]"))
    }

    @Test
    fun `escapes count against the limit`() {
        val json = body("a\n".repeat(40_000))
        val size = bytes(json)
        assertTrue(size <= 60_000, "$size bytes")
        assertTrue(size > 59_900, "kept nearly all that fits: $size bytes")
    }

    @Test
    fun `one runaway line cannot use the whole budget`() {
        val report = "KaizoCore - crash report\napp x\n" + "y".repeat(500_000) + "\nreason CRASH\n"
        val json = body(report)
        assertTrue(bytes(json) <= 60_000)
        val kept = parse(json)["report"] as String
        assertTrue(kept.startsWith("KaizoCore - crash report\napp x\n"))
        assertTrue(kept.endsWith("[trimmed]"))
    }

    @Test
    fun `a line is never cut through the middle of a character`() {
        // The emoji is two chars, and the line is shortened between them if it is cut by count.
        val report = "KaizoCore - crash report\n" + "a".repeat(1_999) + EMOJI + "b".repeat(50_000) + "\n" + "tail line\n".repeat(6_000)
        val kept = parse(body(report))["report"] as String
        assertTrue(kept.endsWith("[trimmed]"))
        assertFalse(REPLACEMENT in kept, "half of a pair was cut off and replaced")
        assertFalse(EMOJI in kept, "the emoji cannot be whole and inside the shortened line")
        assertTrue(kept.lines().any { it == "a".repeat(1_999) + "..." })
    }

    @Test
    fun `a long line inside a report that has to be cut is shortened, not sent whole`() {
        val report = "KaizoCore - crash report\n" + "z".repeat(30_000) + "\n" + "tail line\n".repeat(6_000)
        val json = body(report)
        assertTrue(bytes(json) <= 60_000)
        val kept = parse(json)["report"] as String
        val long = kept.lines().first { it.startsWith("z") }
        assertTrue(long.length < 30_000 && long.endsWith("..."), "${long.length}")
    }

    // ------------------------------------------------------------------ state and decisions

    @Test
    fun `auto send is off by default, however the file is missing or damaged`() {
        val dir = tmp()
        assertFalse(CrashReport.load(dir).auto)
        assertFalse(CrashReport.parseState(null).auto)
        assertFalse(CrashReport.parseState("").auto)
        assertEquals(CrashReport.State(), CrashReport.parseState("auto=maybe\nnonsense\n=\ncount=x\nlast="))
        File(dir, CrashReport.STATE_FILE).writeBytes(byteArrayOf(0, 1, 2, -1, -2, 61, 61))
        assertFalse(CrashReport.load(dir).auto)
        CrashReport.setAuto(dir, true)
        assertTrue(CrashReport.load(dir).auto)
        CrashReport.setAuto(dir, false)
        assertFalse(CrashReport.load(dir).auto)
    }

    @Test
    fun `a count in the file is a number from zero to a thousand`() {
        assertEquals(0, CrashReport.parseState("day=2026-09-29\ncount=-5\n").sentToday)
        assertEquals(1_000, CrashReport.parseState("day=2026-09-29\ncount=99999999\n").sentToday)
        assertEquals(0, CrashReport.parseState("day=2026-09-29\ncount=99999999999999999999\n").sentToday)
        assertEquals(2, CrashReport.parseState("day=2026-09-29\ncount= 2 \n").sentToday)
        // A day with no count is nothing sent on it, and a count with no day is not today's.
        assertEquals(0, CrashReport.sentOn(CrashReport.parseState("day=2026-09-29\n"), "2026-09-29"))
        assertEquals(0, CrashReport.sentOn(CrashReport.parseState("count=2\n"), "2026-09-29"))
    }

    @Test
    fun `the state round-trips through its file`() {
        val states = listOf(
            CrashReport.State(),
            CrashReport.State(auto = true),
            CrashReport.State(false, "2026-09-29 10:00:00"),
            CrashReport.State(true, "2026-09-29 10:00:00", "2026-09-29", 2),
        )
        val dir = tmp()
        for (s in states) {
            assertEquals(s, CrashReport.parseState(CrashReport.formatState(s)))
            assertTrue(CrashReport.save(dir, s))
            assertEquals(s, CrashReport.load(dir))
        }
        assertEquals(
            "auto=1\nlast=2026-09-29 10:00:00\nday=2026-09-29\ncount=2\n",
            CrashReport.formatState(CrashReport.State(true, "2026-09-29 10:00:00", "2026-09-29", 2)),
        )
        assertEquals("crash-sent.txt", CrashReport.STATE_FILE)
    }

    @Test
    fun `the state is this phone's and stays out of the backup`() {
        assertFalse(Backup.admits(CrashReport.STATE_FILE))
        assertFalse(Backup.admits("prep/" + CrashReport.STATE_FILE))
    }

    @Test
    fun `a report may go once, three a day, and the next day is a new day`() {
        val day = "2026-09-29"
        var s = CrashReport.State()
        for ((n, stamp) in listOf("A", "B", "C").withIndex()) {
            assertEquals(CrashReport.Verdict.GO, CrashReport.mayGo(s, stamp, day), "send ${n + 1}")
            s = CrashReport.afterSent(s, stamp, day)
            assertEquals(n + 1, CrashReport.sentOn(s, day))
        }
        // The newest one sent is never sent again, and a fourth is not sent today.
        assertEquals(CrashReport.Verdict.SENT_ALREADY, CrashReport.mayGo(s, "C", day))
        assertEquals(CrashReport.Verdict.DAY_FULL, CrashReport.mayGo(s, "D", day))
        assertEquals(CrashReport.Verdict.DAY_FULL, CrashReport.mayGo(s, null, day))
        // Tomorrow the count starts again; the crash that was sent is still not sent twice.
        assertEquals(0, CrashReport.sentOn(s, "2026-09-30"))
        assertEquals(CrashReport.Verdict.GO, CrashReport.mayGo(s, "D", "2026-09-30"))
        assertEquals(CrashReport.Verdict.SENT_ALREADY, CrashReport.mayGo(s, "C", "2026-09-30"))
        val next = CrashReport.afterSent(s, "D", "2026-09-30")
        assertEquals(1, CrashReport.sentOn(next, "2026-09-30"))
        assertEquals("D", next.lastSent)
        assertTrue(next.day == "2026-09-30")
    }

    @Test
    fun `whether a launch sends by itself takes the switch, the day and the report`() {
        val dir = tmp()
        val rep = report("2026-09-29 10:00:00")
        assertFalse(CrashReport.mayAutoSend(dir, rep, "2026-09-29"), "off until the player says so")
        CrashReport.setAuto(dir, true)
        assertTrue(CrashReport.mayAutoSend(dir, rep, "2026-09-29"))
        CrashReport.update(dir) { CrashReport.afterSent(it, "2026-09-29 10:00:00", "2026-09-29") }
        assertFalse(CrashReport.mayAutoSend(dir, rep, "2026-09-29"), "already sent")
        CrashReport.update(dir) { it.copy(lastSent = "an older one", sentToday = CrashReport.MAX_PER_DAY) }
        assertFalse(CrashReport.mayAutoSend(dir, rep, "2026-09-29"), "the day is full")
        assertTrue(CrashReport.mayAutoSend(dir, rep, "2026-09-30"))
    }

    // ------------------------------------------------------------------ the network end

    /**
     * A server on this machine that answers as the site would, and remembers what it was sent. It speaks
     * plain HTTP/1.1 over a ServerSocket: this module compiles against android.jar, where the JDK's own
     * com.sun.net.httpserver is not visible.
     */
    private class FakeSite(
        private val status: Int = 200,
        private val answer: String = "{\"ok\":true}",
        private val delayMs: Long = 0,
        private val location: String? = null,
    ) : java.io.Closeable {
        val bodies = CopyOnWriteArrayList<ByteArray>()
        val types = CopyOnWriteArrayList<String?>()
        val agents = CopyOnWriteArrayList<String?>()
        val methods = CopyOnWriteArrayList<String>()
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val url = "http://127.0.0.1:${socket.localPort}/api/kaizocore-crash"

        init {
            Thread { runCatching { while (true) { val c = socket.accept(); pool.execute { serve(c) } } } }
                .apply { isDaemon = true; start() }
        }

        private fun readLine(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (out.size() == 0) null else out.toString("ISO-8859-1")
                if (b == '\n'.code) return out.toString("ISO-8859-1").trimEnd('\r')
                out.write(b)
            }
        }

        private fun serve(c: Socket) {
            runCatching {
                c.use {
                    val input = BufferedInputStream(c.getInputStream())
                    val requestLine = readLine(input) ?: return@use
                    val headers = HashMap<String, String>()
                    while (true) {
                        val line = readLine(input) ?: return@use
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                    }
                    val body = ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
                    var got = 0
                    while (got < body.size) {
                        val n = input.read(body, got, body.size - got)
                        if (n < 0) break
                        got += n
                    }
                    bodies += body
                    types += headers["content-type"]
                    agents += headers["user-agent"]
                    methods += requestLine.substringBefore(' ')
                    if (delayMs > 0) Thread.sleep(delayMs)
                    val out = answer.toByteArray(Charsets.UTF_8)
                    val head = StringBuilder("HTTP/1.1 $status Answer\r\n")
                    head.append("Content-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n")
                    location?.let { head.append("Location: $it\r\n") }
                    head.append("\r\n")
                    val w = c.getOutputStream()
                    w.write(head.toString().toByteArray(Charsets.ISO_8859_1))
                    w.write(out)
                    w.flush()
                }
            }
        }

        override fun close() { runCatching { socket.close() } }
    }

    private val META = CrashReport.Meta("1.0.0-rc30+c8e0a70 (39)", "Google Pixel 7", "Android 14 (API 34)", "FRLG")

    private fun report(stamp: String) =
        "KaizoCore - crash report\napp 1.0.0-rc30+c8e0a70 (39)\ndevice Google Pixel 7, Android 14 (API 34)\n\n" +
            "---- $stamp ----\nreason      CRASH (unhandled Java exception)\nstatus      2\n\n"

    @Test
    fun `an ok answer is sent, with the body, type and agent the site expects`() {
        FakeSite().use { site ->
            val json = body("hello\n")
            assertEquals(CrashReport.SendResult.Sent, CrashReport.send(json, site.url, 3_000))
            assertEquals(listOf(json), site.bodies.map { String(it, Charsets.UTF_8) })
            assertEquals(listOf("POST"), site.methods.toList())
            assertEquals(listOf<String?>("application/json; charset=utf-8"), site.types.toList())
            assertEquals(listOf<String?>("KaizoCore"), site.agents.toList())
        }
    }

    @Test
    fun `a body with non-ASCII arrives byte for byte`() {
        FakeSite().use { site ->
            val json = body("caf$E_ACUTE $KANJI $EMOJI\n")
            assertEquals(CrashReport.SendResult.Sent, CrashReport.send(json, site.url, 3_000))
            assertEquals(json.toByteArray(Charsets.UTF_8).toList(), site.bodies.single().toList())
        }
    }

    @Test
    fun `the answer is read only as far as 4 KB, and a long one is not waited for`() {
        val padding = " ".repeat(10_000)
        // A yes inside the first 4 KB counts, however much follows it.
        FakeSite(200, "{\"ok\":true}" + " ".repeat(2_000_000)).use { site ->
            assertEquals(CrashReport.SendResult.Sent, CrashReport.send("{}", site.url, 3_000))
        }
        // A yes past the first 4 KB is not read, so it is not a yes.
        FakeSite(200, padding + "{\"ok\":true}").use { site ->
            assertEquals(CrashReport.SendResult.Failed("unexpected answer"), CrashReport.send("{}", site.url, 3_000))
        }
    }

    @Test
    fun `ok false is not sent, and carries the reason the site gave`() {
        FakeSite(200, "{\"ok\":false,\"reason\":\"mail is down\"}").use { site ->
            assertEquals(CrashReport.SendResult.NotSent("mail is down"), CrashReport.send("{}", site.url, 3_000))
        }
        FakeSite(200, "{\"ok\":false}").use { site ->
            assertIs<CrashReport.SendResult.NotSent>(CrashReport.send("{}", site.url, 3_000))
        }
    }

    @Test
    fun `the rate limit and a server error are failures`() {
        for (code in listOf(429, 500, 502, 503)) {
            FakeSite(code, "{\"ok\":false,\"reason\":\"x\"}").use { site ->
                val r = CrashReport.send("{}", site.url, 3_000)
                assertIs<CrashReport.SendResult.Failed>(r, "$code")
            }
        }
    }

    @Test
    fun `a refusal is not sent`() {
        for (code in listOf(400, 405, 413)) {
            FakeSite(code, "{\"ok\":false,\"reason\":\"refused\"}").use { site ->
                assertIs<CrashReport.SendResult.NotSent>(CrashReport.send("{}", site.url, 3_000), "$code")
            }
        }
    }

    @Test
    fun `only a 200 that says ok true counts as sent`() {
        val answers = listOf(
            200 to "{}", 200 to "{\"ok\":\"true\"}", 200 to "", 200 to "<html>a captive portal</html>", 200 to "{\"okay\":true}",
            201 to "{\"ok\":true}", 202 to "{\"ok\":true}", 204 to "",
        )
        for ((code, text) in answers) {
            FakeSite(code, text).use { site ->
                val r = CrashReport.send("{}", site.url, 3_000)
                assertNotEquals(CrashReport.SendResult.Sent, r, "$code $text")
            }
        }
        FakeSite(200, "{\"ok\": true}").use { site ->
            assertEquals(CrashReport.SendResult.Sent, CrashReport.send("{}", site.url, 3_000))
        }
    }

    @Test
    fun `a redirect is not followed, so the report goes to the one address it was made for`() {
        FakeSite().use { elsewhere ->
            FakeSite(302, "", location = elsewhere.url).use { site ->
                val r = CrashReport.send("{}", site.url, 3_000)
                assertIs<CrashReport.SendResult.Failed>(r)
                assertTrue(elsewhere.bodies.isEmpty(), "the redirect was followed")
            }
        }
    }

    @Test
    fun `a server that never answers fails within the timeout, not never`() {
        val ss = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val held = CopyOnWriteArrayList<Socket>()
        val acceptor = Thread { runCatching { while (true) held += ss.accept() } }.apply { isDaemon = true; start() }
        try {
            val start = System.nanoTime()
            val r = CrashReport.send("{}", "http://127.0.0.1:${ss.localPort}/api/kaizocore-crash", timeoutMs = 300)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertIs<CrashReport.SendResult.Failed>(r)
            assertTrue(ms < 5_000, "took $ms ms")
        } finally {
            runCatching { ss.close() }
            held.forEach { runCatching { it.close() } }
            acceptor.interrupt()
        }
    }

    @Test
    fun `nothing listening is a failure, not an exception`() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertIs<CrashReport.SendResult.Failed>(CrashReport.send("{}", "http://127.0.0.1:$port/x", 2_000))
        assertIs<CrashReport.SendResult.Failed>(CrashReport.send("{}", "not a url", 2_000))
        assertIs<CrashReport.SendResult.Failed>(CrashReport.send("{}", "ftp://127.0.0.1/x", 2_000))
    }

    @Test
    fun `answers are read by status and by what they say`() {
        assertEquals(CrashReport.SendResult.Sent, CrashReport.answer(200, "{\"ok\":true}"))
        assertEquals(CrashReport.SendResult.NotSent("nope"), CrashReport.answer(200, "{\"ok\":false,\"reason\":\"nope\"}"))
        assertEquals(CrashReport.SendResult.NotSent("not sent"), CrashReport.answer(200, "{\"ok\":false,\"reason\":\"\"}"))
        assertEquals(CrashReport.SendResult.Failed("unexpected answer"), CrashReport.answer(200, "??"))
        assertEquals(CrashReport.SendResult.Failed("too many reports just now"), CrashReport.answer(429, "{\"ok\":true}"))
        assertIs<CrashReport.SendResult.NotSent>(CrashReport.answer(413, ""))
        assertEquals(CrashReport.SendResult.Failed("HTTP 500"), CrashReport.answer(500, "{\"ok\":true}"))
    }

    // -------------------------------------------------------- the whole delivery, on a fake site

    @Test
    fun `a delivered report reaches the site as the exact body and is recorded`() {
        FakeSite().use { site ->
            val dir = tmp()
            val rep = report("2026-09-29 10:00:00")
            val r = CrashReport.deliver(dir, META, rep, "2026-09-29", site.url, 3_000)
            assertEquals(CrashReport.SendResult.Sent, r)
            assertEquals(
                CrashReport.payload(META.app, META.device, META.android, META.family, rep),
                String(site.bodies.single(), Charsets.UTF_8),
            )
            assertEquals(CrashReport.State(false, "2026-09-29 10:00:00", "2026-09-29", 1), CrashReport.load(dir))
        }
    }

    @Test
    fun `the same report is never sent twice`() {
        FakeSite().use { site ->
            val dir = tmp()
            val rep = report("2026-09-29 10:00:00")
            repeat(2) { assertEquals(CrashReport.SendResult.Sent, CrashReport.deliver(dir, META, rep, "2026-09-29", site.url, 3_000)) }
            // Not on another day either.
            assertEquals(CrashReport.SendResult.Sent, CrashReport.deliver(dir, META, rep, "2026-10-05", site.url, 3_000))
            assertEquals(1, site.bodies.size)
            assertEquals(1, CrashReport.sentOn(CrashReport.load(dir), "2026-09-29"))
        }
    }

    @Test
    fun `two sends of one report at the same moment deliver it once`() {
        FakeSite(delayMs = 300).use { site ->
            val dir = tmp()
            val rep = report("2026-09-29 10:00:00")
            val results = CopyOnWriteArrayList<CrashReport.SendResult>()
            val threads = List(2) { Thread { results += CrashReport.deliver(dir, META, rep, "2026-09-29", site.url, 5_000) } }
            threads.forEach { it.start() }
            threads.forEach { it.join(15_000) }
            assertEquals(1, site.bodies.size, "one report reached the site twice")
            assertEquals(listOf<CrashReport.SendResult>(CrashReport.SendResult.Sent, CrashReport.SendResult.Sent), results.toList())
        }
    }

    @Test
    fun `three a day, automatic or by hand, and the fourth waits for tomorrow`() {
        FakeSite().use { site ->
            val dir = tmp()
            val stamps = listOf("2026-09-29 09:00:00", "2026-09-29 10:00:00", "2026-09-29 11:00:00", "2026-09-29 12:00:00")
            for (s in stamps.take(3)) {
                assertEquals(CrashReport.SendResult.Sent, CrashReport.deliver(dir, META, report(s), "2026-09-29", site.url, 3_000), s)
            }
            val fourth = CrashReport.deliver(dir, META, report(stamps[3]), "2026-09-29", site.url, 3_000)
            assertIs<CrashReport.SendResult.NotSent>(fourth)
            assertEquals(3, site.bodies.size, "the fourth reached the site")
            CrashReport.setAuto(dir, true)
            assertFalse(CrashReport.mayAutoSend(dir, report(stamps[3]), "2026-09-29"), "an automatic send obeys it too")
            assertEquals(CrashReport.SendResult.Sent, CrashReport.deliver(dir, META, report(stamps[3]), "2026-09-30", site.url, 3_000))
            assertEquals(4, site.bodies.size)
            assertEquals(1, CrashReport.sentOn(CrashReport.load(dir), "2026-09-30"))
            assertTrue(CrashReport.load(dir).auto, "recording a send left the switch as it was")
        }
    }

    @Test
    fun `a failed send records nothing and does not use up the day`() {
        val dir = tmp()
        val rep = report("2026-09-29 10:00:00")
        for ((code, text) in listOf(500 to "oops", 429 to "{\"ok\":false,\"reason\":\"rate\"}", 200 to "{\"ok\":false,\"reason\":\"mail\"}", 413 to "")) {
            FakeSite(code, text).use { site ->
                val r = CrashReport.deliver(dir, META, rep, "2026-09-29", site.url, 3_000)
                assertNotEquals(CrashReport.SendResult.Sent, r, "$code")
                assertEquals(CrashReport.State(), CrashReport.load(dir), "nothing recorded after $code")
            }
        }
        FakeSite().use { site ->
            assertEquals(CrashReport.SendResult.Sent, CrashReport.deliver(dir, META, rep, "2026-09-29", site.url, 3_000))
            assertEquals(1, site.bodies.size)
        }
    }

    // -------------------------------------------------------- the report's own shape

    private val T0 = 1_790_000_000_000L

    private fun exit(time: Long, reason: Int = ApplicationExitInfo.REASON_CRASH) =
        CrashLog.Exit(time, reason, 2, 100, "com.ironmonone.app", "crash", 1_000L)

    private fun stackOf(lines: Int) = buildString {
        appendLine("java.lang.IllegalStateException: boom")
        for (i in 1 until lines) appendLine("\tat com.ironmonone.app.Frame$i.run(Frame.kt:$i)")
    }

    private fun stampFor(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(ms))

    @Test
    fun `the report keeps the first line and the shape the site checks`() {
        val exits = listOf(
            exit(T0 + 5_000), exit(T0 - 5_000, ApplicationExitInfo.REASON_CRASH_NATIVE), exit(T0 - 9_000, ApplicationExitInfo.REASON_ANR),
        )
        val text = CrashLog.render("1.0.0-rc30+c8e0a70 (39)", "Google Pixel 7, Android 14 (API 34)", exits)
        val lines = text.lines()
        assertEquals("KaizoCore - crash report", lines[0])
        assertEquals(CrashLog.HEADER, lines[0])
        assertEquals("app 1.0.0-rc30+c8e0a70 (39)", lines[1])
        assertEquals("device Google Pixel 7, Android 14 (API 34)", lines[2])
        assertEquals("", lines[3])
        val stampLine = Regex("^---- \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} ----$")
        assertEquals(3, lines.count { stampLine.matches(it) })
        assertEquals("---- ${stampFor(T0 + 5_000)} ----", lines[4], "newest first")
        assertTrue("reason      CRASH (unhandled Java exception)" in lines)
        assertTrue("reason      CRASH_NATIVE (signal in native code)" in lines)
        assertTrue("reason      ANR (not responding)" in lines)
        assertTrue("status      2" in lines && "importance  100" in lines && "process     com.ironmonone.app" in lines)
        assertTrue("description crash" in lines && "rss         1000 kB" in lines)
        // What reads the report back: the crash filter, the newest stamp and the INFO card's date.
        assertTrue(CrashLog.isCrashReport(text))
        assertEquals(stampFor(T0 + 5_000), CrashLog.newestStamp(text))
        assertNotNull(CrashLog.whenLabel(text))
    }

    @Test
    fun `a tombstone's lines print under its exit as they always did`() {
        val e = CrashLog.Exit(
            T0, ApplicationExitInfo.REASON_CRASH_NATIVE, 0, 100, "p", null, 7L,
            CrashLog.Tombstone("crash-trace-1.bin", 99L, listOf("signal 11 (SIGSEGV)", "Cause: null")),
        )
        val lines = CrashLog.render("a", "d", listOf(e)).lines()
        val at = lines.indexOf("tombstone   crash-trace-1.bin (99 bytes)")
        assertTrue(at > 0)
        assertEquals("            signal 11 (SIGSEGV)", lines[at + 1])
        assertEquals("            Cause: null", lines[at + 2])
        assertFalse(lines.any { it.startsWith("description") }, "no description, no line")
    }

    @Test
    fun `a Java stack goes under the crash that followed it, the first forty lines of it`() {
        val saved = CrashLog.JavaCrash(T0, "main", stackOf(100))
        val text = CrashLog.render("a", "d", listOf(exit(T0 + 5_000)), saved)
        val lines = text.lines()
        val at = lines.indexOf("java        thread main")
        assertTrue(at > 0, text)
        assertEquals("            java.lang.IllegalStateException: boom", lines[at + 1])
        assertEquals("            \tat com.ironmonone.app.Frame1.run(Frame.kt:1)", lines[at + 2])
        val block = lines.drop(at + 1).takeWhile { it.startsWith("            ") }
        assertEquals(40, block.size)
        assertTrue("Frame39" in text && "Frame40" !in text)
        // It sits inside that exit's block, before the blank line that ends it.
        assertEquals("", lines[at + 41])
        assertTrue(at > lines.indexOf("rss         1000 kB"))
    }

    @Test
    fun `a Java stack joins only a crash the system logged within a minute after it`() {
        val saved = CrashLog.JavaCrash(T0, "main", stackOf(5))
        fun has(vararg exits: CrashLog.Exit) = "java        thread main" in CrashLog.render("a", "d", exits.toList(), saved)
        assertTrue(has(exit(T0)), "at the same moment")
        assertTrue(has(exit(T0 + 1_000)))
        assertTrue(has(exit(T0 + 60_000)), "a minute exactly")
        assertFalse(has(exit(T0 + 60_001)), "a minute and a millisecond")
        assertFalse(has(exit(T0 - 1)), "an exit from before the stack was written is an older crash")
        assertFalse(has(exit(T0 - 5_000)))
        assertFalse(has(exit(T0 + 5_000, ApplicationExitInfo.REASON_CRASH_NATIVE)), "a native crash has no Java stack")
        assertFalse(has(exit(T0 + 5_000, ApplicationExitInfo.REASON_ANR)), "nor does a freeze")
        assertFalse(has())
        assertFalse("java        thread" in CrashLog.render("a", "d", listOf(exit(T0 + 5_000)), null))
    }

    @Test
    fun `with two crashes after it the stack belongs to the first, and is printed once`() {
        val saved = CrashLog.JavaCrash(T0, "RenderThread", stackOf(3))
        val early = exit(T0 + 5_000)
        val late = exit(T0 + 20_000)
        assertSame(early, CrashLog.javaOwner(listOf(late, early), saved))
        val text = CrashLog.render("a", "d", listOf(early, late), saved)
        assertEquals(1, text.lines().count { it.startsWith("java        thread ") })
        val sections = text.split("---- ").drop(1)
        assertEquals(2, sections.size)
        assertTrue(sections[0].startsWith(stampFor(T0 + 20_000)) && "java        thread" !in sections[0], "the newest is the later crash, with no stack")
        assertTrue(sections[1].startsWith(stampFor(T0 + 5_000)) && "java        thread RenderThread" in sections[1])
    }

    // ------------------------------------------------------- the saved stack and its handler

    @Test
    fun `a saved stack round-trips, and what is not one is null`() {
        val j = CrashLog.JavaCrash(T0, "main", stackOf(4))
        assertEquals(j, CrashLog.parseJava(CrashLog.formatJava(j)))
        assertEquals("Thread-7", CrashLog.parseJava(CrashLog.formatJava(j.copy(thread = "Thread-7")))!!.thread)
        assertEquals("a b", CrashLog.parseJava(CrashLog.formatJava(j.copy(thread = "a\nb")))!!.thread)
        assertNull(CrashLog.parseJava(null))
        assertNull(CrashLog.parseJava(""))
        assertNull(CrashLog.parseJava("not a stack"))
        assertNull(CrashLog.parseJava("abc\nmain\nstack"))
        assertNull(CrashLog.parseJava("0\nmain\nstack"))
        assertNull(CrashLog.parseJava("$T0\nmain\n"))
        assertNull(CrashLog.parseJava("$T0\nmain"))
    }

    @Test
    fun `the handler saves the stack, then hands the exception on`() {
        val dir = tmp()
        val seen = mutableListOf<String>()
        val previous = Thread.UncaughtExceptionHandler { t, e ->
            // The file is already on disk when the system's handler runs: it may kill the process at once.
            seen += "previous:${t.name}:${e.message}:${File(dir, CrashLog.JAVA_FILE).isFile}"
        }
        val h = CrashLog.JavaCrashHandler(dir, previous, now = { T0 }, kill = { fail("killed with a handler to hand on to") })
        h.uncaughtException(Thread("RenderThread"), IllegalStateException("boom"))
        assertEquals(listOf("previous:RenderThread:boom:true"), seen)
        val saved = CrashLog.parseJava(File(dir, CrashLog.JAVA_FILE).readText())
        assertNotNull(saved)
        assertEquals(T0, saved.time)
        assertEquals("RenderThread", saved.thread)
        assertTrue(saved.stack.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue("at com.ironmonone.app.CrashReportTest" in saved.stack)
    }

    @Test
    fun `the exception still goes to the system's handler when saving fails`() {
        // A file where the directory should be: nothing can be written under it.
        val notADir = File.createTempFile("crashreport", ".file")
        var handedOn = 0
        val previous = Thread.UncaughtExceptionHandler { _, _ -> handedOn++ }
        CrashLog.JavaCrashHandler(notADir, previous, kill = { fail("killed") }).uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertEquals(1, handedOn)
        assertFalse(File(notADir, CrashLog.JAVA_FILE).exists())

        // An exception that cannot even print its own stack.
        val dir = tmp()
        val broken = object : RuntimeException("x") {
            override fun toString(): String = throw IllegalStateException("no string for me")
        }
        CrashLog.JavaCrashHandler(dir, previous, kill = { fail("killed") }).uncaughtException(Thread.currentThread(), broken)
        assertEquals(2, handedOn)
    }

    @Test
    fun `with nothing before it the handler ends the process the standard way`() {
        val dir = tmp()
        var killed = 0
        CrashLog.JavaCrashHandler(dir, null, now = { T0 }, kill = { killed++ }).uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertEquals(1, killed)
        assertNotNull(CrashLog.parseJava(File(dir, CrashLog.JAVA_FILE).readText()))
    }

    @Test
    fun `a stack is cut at 32 KB, and only there`() {
        assertEquals("short", CrashLog.capStack("short"))
        val exact = "a".repeat(CrashLog.JAVA_MAX_CHARS)
        assertEquals(exact, CrashLog.capStack(exact))
        assertEquals(CrashLog.JAVA_MAX_CHARS, CrashLog.capStack(exact + "b").length)
        // Never through the middle of a surrogate pair.
        val pair = "a".repeat(CrashLog.JAVA_MAX_CHARS - 1) + EMOJI + "tail"
        val cut = CrashLog.capStack(pair)
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertEquals(CrashLog.JAVA_MAX_CHARS - 1, cut.length)

        val dir = tmp()
        val deep = object : RuntimeException("deep") {
            override fun toString(): String = "deep: " + "x".repeat(100_000)
        }
        CrashLog.JavaCrashHandler(dir, null, kill = {}).uncaughtException(Thread.currentThread(), deep)
        val saved = CrashLog.parseJava(File(dir, CrashLog.JAVA_FILE).readText())!!
        assertTrue(saved.stack.length <= CrashLog.JAVA_MAX_CHARS, "${saved.stack.length}")
        assertTrue(saved.stack.startsWith("deep: xxx"))
    }

    @Test
    fun `installing twice wraps once, and the first handler is still handed the exception`() {
        val dir = tmp()
        var original = 0
        var slot: Thread.UncaughtExceptionHandler? = Thread.UncaughtExceptionHandler { _, _ -> original++ }
        val once = AtomicBoolean(false)
        assertTrue(CrashLog.install(dir, once, { slot }, { slot = it }))
        val first = slot
        assertTrue(first is CrashLog.JavaCrashHandler)
        assertFalse(CrashLog.install(dir, once, { slot }, { slot = it }))
        assertSame(first, slot, "the second call wrapped it again")
        // Even with a new flag, a handler that is already ours is not wrapped over.
        assertFalse(CrashLog.install(dir, AtomicBoolean(false), { slot }, { slot = it }))
        assertSame(first, slot)
        slot!!.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertEquals(1, original)
        assertTrue(File(dir, CrashLog.JAVA_FILE).isFile)
    }

    @Test
    fun `another library's handler put over ours is not wrapped over by a later install`() {
        val dir = tmp()
        var slot: Thread.UncaughtExceptionHandler? = null
        val once = AtomicBoolean(false)
        assertTrue(CrashLog.install(dir, once, { slot }, { slot = it }))
        val ours = slot!!
        // Something else installs its own handler over ours, as a crash library would.
        val theirs = Thread.UncaughtExceptionHandler { t, e -> ours.uncaughtException(t, e) }
        slot = theirs
        // The activity is made again: it is the flag that stops a second copy of ours going in under theirs.
        assertFalse(CrashLog.install(dir, once, { slot }, { slot = it }))
        assertSame(theirs, slot, "wrapped again over another handler")
    }

    @Test
    fun `a saved stack a week old is deleted, a fresh one kept, and clear takes it too`() {
        val dir = tmp()
        val day = 24L * 60 * 60 * 1000
        val now = T0 + 30 * day
        val file = File(dir, CrashLog.JAVA_FILE)
        fun put(time: Long) = file.writeText(CrashLog.formatJava(CrashLog.JavaCrash(time, "main", stackOf(3))))
        put(now - 8 * day)
        CrashLog.pruneJava(dir, now)
        assertFalse(file.exists(), "eight days old")
        put(now - 6 * day)
        CrashLog.pruneJava(dir, now)
        assertTrue(file.exists(), "six days old")
        CrashLog.clearIn(dir)
        assertFalse(file.exists(), "clear takes it with the report")
        file.writeText("not a stack at all")
        CrashLog.pruneJava(dir, now)
        assertFalse(file.exists(), "what is not a stack goes")
        CrashLog.pruneJava(dir, now)
    }

    @Test
    fun `clear removes the report and the tombstones as well`() {
        val dir = tmp()
        File(dir, CrashLog.REPORT).writeText("r")
        File(dir, "crash-trace-1.bin").writeText("t")
        File(dir, "crash-seen.txt").writeText("1")
        File(dir, CrashReport.STATE_FILE).writeText("auto=1\n")
        CrashLog.clearIn(dir)
        assertFalse(File(dir, CrashLog.REPORT).exists() || File(dir, "crash-trace-1.bin").exists())
        // The marker keeps the same crash from being announced again, and the state is the player's choice.
        assertTrue(File(dir, "crash-seen.txt").exists())
        assertTrue(CrashReport.load(dir).auto)
    }

    @Test
    fun `the newest stamp is the first stamp line, in the shape the writer uses`() {
        assertEquals("2026-09-29 10:00:00", CrashLog.newestStamp("x\n---- 2026-09-29 10:00:00 ----\nreason\n---- 2026-09-28 09:00:00 ----\n"))
        assertEquals("2026-09-29 10:00:00", CrashLog.newestStamp("---- 2026-09-29 10:00:00 ----\r\nreason"))
        assertNull(CrashLog.newestStamp("no stamp here"))
        assertNull(CrashLog.newestStamp("---- yesterday ----"))
        assertNull(CrashLog.newestStamp("---- 2026-09-29 10:00 ----"))
    }

    // ------------------------------------------------------------------------ the words

    @Test
    fun `the words are plain, dry and carry the promise`() {
        val all = listOf(
            CrashReportText.TITLE, CrashReportText.ASK, CrashReportText.SENDING, CrashReportText.SENT, CrashReportText.FAILED,
            CrashReportText.SENT_BEFORE, CrashReportText.INFO_HINT, CrashReportText.CARD_TITLE, CrashReportText.SWITCH, CrashReportText.CARD_NOTE,
        )
        for (t in all) {
            assertFalse(EM_DASH in t, "an em dash: $t")
            assertFalse(Regex("\\bAI\\b", RegexOption.IGNORE_CASE).containsMatchIn(t), "AI: $t")
        }
        assertEquals("KaizoCore closed unexpectedly", CrashReportText.TITLE)
        assertEquals(
            "It happened last time you played. Your saves are kept. Send a report so it gets fixed? " +
                "It says where the app failed, with your phone model, the Android and app versions and the game family. " +
                "Never a ROM, a save or a file name.",
            CrashReportText.ASK,
        )
        assertEquals("Sending...", CrashReportText.SENDING)
        assertEquals("Sent. Thank you.", CrashReportText.SENT)
        assertEquals("Could not send it. Share it instead?", CrashReportText.FAILED)
        assertEquals("Sent to Blake.", CrashReportText.SENT_BEFORE)
        assertEquals("Crash reports", CrashReportText.CARD_TITLE)
        assertEquals("Send crash reports automatically", CrashReportText.SWITCH)
        assertEquals(
            "If KaizoCore closes unexpectedly, a report of where it failed goes to Blake at willowcreek.group: " +
                "your phone model, the Android and app versions, the game family and the lines that name the failure. " +
                "Never a ROM, a save or a file name. With this off, the app asks you before it sends a report.",
            CrashReportText.CARD_NOTE,
        )
    }

    // ------------------------------------------------------------------------ the wiring

    private fun source(name: String): String =
        File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `the crash screens are wired in where they belong`() {
        val main = source("MainActivity.kt")
        val launch = "launchCrash?.let { text -> CrashReportLaunch(text, onClose = { launchCrash = null }) }"
        assertEquals(1, Regex(Regex.escape(launch)).findAll(main).count(), "one launch dialog")
        // The update prompt is told about the crash dialog, so the two never stack.
        assertTrue("    $launch\n    UpdatePrompt(show = tab != Tab.PLAY && launchCrash == null)" in main, "the crash dialog, then the update prompt that waits for it")
        assertEquals(1, Regex(Regex.escape("CrashLog.installHandler(this)")).findAll(main).count(), "the handler is installed once, from onCreate")
        assertTrue(main.indexOf("CrashLog.installHandler(this)") > main.indexOf("override fun onCreate("), "inside onCreate")
        val about = source("AboutScreen.kt")
        assertEquals(1, Regex(Regex.escape("CrashReportsCard()")).findAll(about).count(), "one switch card")
        assertTrue(about.indexOf("CrashReportsCard()") < about.indexOf("// BETA FEEDBACK."), "before the bug report card")
        assertTrue("CrashCardActions(text, onDismiss = { CrashLog.clear(context); crash = null })" in about, "the crash card sends through the site")
    }

    @Test
    fun `the switch is the shell's row and is written off the main thread`() {
        val ui = source("CrashReportUi.kt")
        assertTrue("ShellSwitchRow(CrashReportText.SWITCH, auto, CrashReportText.CARD_NOTE)" in ui, "the shell's switch row, drawn for paper cards")
        assertFalse("GearToggle(" in ui, "not the tracker's toggle: it is drawn in the PC palette for a dark dialog")
        assertTrue("scope.launch(Dispatchers.IO) { CrashReport.setAuto(context.filesDir, on) }" in ui, "SafeWrite syncs to disk: not on the main thread")
    }

    @Test
    fun `release builds keep class names and line numbers, so a Java trace reads without a mapping file`() {
        val rules = File("proguard-rules.pro").readText().replace("\r\n", "\n")
        assertTrue(Regex("(?m)^-dontobfuscate\\s*$").containsMatchIn(rules), "names kept: an obfuscated trace needs that exact build's mapping.txt")
        assertTrue(Regex("(?m)^-keepattributes\\s+SourceFile,LineNumberTable\\s*$").containsMatchIn(rules), "line numbers kept")
    }
}
