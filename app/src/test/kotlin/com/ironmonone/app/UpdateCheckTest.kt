package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The in-app update check (2026-09-29): what it trusts from the site's latest.json, when it
 * asks, when it interrupts, what it sends, and how it fails. The network tests talk to a real
 * server on loopback (a plain ServerSocket: com.sun.net.httpserver is not visible to the Kotlin
 * test compile without a compiler flag), so the timeouts, the size limit and the request bytes
 * are the real ones. The composables cannot run in a JVM test; the last tests prove they are
 * wired to these rules, the way the other screens' tests do.
 */
class UpdateCheckTest {

    // ------------------------------------------------------------ fixtures
    private val BS = 92.toChar()
    private val BOM = 0xFEFF.toChar()
    private val EM_DASH = 0x2014.toChar()
    private val EN_DASH = 0x2013.toChar()

    private fun q(s: String) = "\"" + s + "\""

    /** The manifest as the site serves it, one raw JSON value per key, so a test changes or drops exactly one. */
    private val base = linkedMapOf(
        "app" to q("KaizoCore"),
        "versionCode" to "39",
        "version" to q("1.0.0-rc30"),
        "published" to q("2026-09-29"),
        "apk" to q("https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk?v=3800ad62"),
        "bytes" to "120385434",
        "sha256" to q("3800ad62cad6c21a87da5dedd663f1e8e9b716bf20076aa8ce9e1b7a8fe9cc7e"),
        "notes" to q("https://github.com/blakeportable7-del/KaizoCore/releases/tag/v1.0.0-rc30"),
        "page" to q("https://willowcreek.group/kaizocore"),
    )

    /** [base] with the given keys replaced (or dropped, for a null), as text. */
    private fun json(vararg change: Pair<String, String?>): String {
        val m = LinkedHashMap(base)
        for ((k, v) in change) if (v == null) m.remove(k) else m[k] = v
        return m.entries.joinToString(",\n  ", "{\n  ", "\n}") { "\"${it.key}\": ${it.value}" }
    }

    private fun compact(): String = base.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

    private fun parsed(vararg change: Pair<String, String?>) = UpdateCheck.parse(json(*change))

    /** Asserts the untouched manifest parses (so a null below is the change's doing) and the changed one does not. */
    private fun refused(why: String, vararg change: Pair<String, String?>) {
        assertNotNull(UpdateCheck.parse(json()), "control: the unchanged manifest parses")
        assertNull(parsed(*change), why)
    }

    private fun manifest(code: Long = 39L): UpdateCheck.Manifest = parsed("versionCode" to code.toString())!!

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("update").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    private val HOUR = 60L * 60 * 1000
    private val NOW = 1_800_000_000_000L

    // --------------------------------------------------------------- parse
    @Test
    fun `the manifest the site serves parses to the right fields`() {
        val m = UpdateCheck.parse(json())
        assertEquals(
            UpdateCheck.Manifest(
                versionCode = 39L,
                version = "1.0.0-rc30",
                apk = "https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk?v=3800ad62",
                bytes = 120385434L,
                sha256 = "3800ad62cad6c21a87da5dedd663f1e8e9b716bf20076aa8ce9e1b7a8fe9cc7e",
                notes = "https://github.com/blakeportable7-del/KaizoCore/releases/tag/v1.0.0-rc30",
                page = "https://willowcreek.group/kaizocore",
            ),
            m,
        )
        assertEquals("https://willowcreek.group/kaizocore/latest.json", UpdateCheck.URL)
    }

    @Test
    fun `whitespace, key order, unknown keys and escaped strings still parse`() {
        val want = UpdateCheck.parse(json())!!
        // No whitespace at all.
        assertEquals(want, UpdateCheck.parse(compact()))
        // Tabs and CRLF everywhere, and after the closing brace.
        val spaced = base.entries.joinToString(",\t\r\n\t", "\t{ \r\n\t", " \t}\r\n \n") { "\"${it.key}\" \t:\r\n ${it.value}" }
        assertEquals(want, UpdateCheck.parse(spaced))
        // The keys in the opposite order.
        assertEquals(want, UpdateCheck.parse(base.entries.reversed().joinToString(", ", "{", "}") { "\"${it.key}\": ${it.value}" }))
        // Keys the app has never heard of, of every JSON kind, nested ones included.
        val extra = json(
            "extra" to "{\"a\": [1, 2, {\"b\": null}], \"c\": \"d\"}",
            "list" to "[]", "empty" to "{}", "n" to "-1.5e3", "t" to "true", "f" to "false", "z" to "null",
            "s" to q("x" + BS + "ny"),
        )
        assertEquals(want, UpdateCheck.parse(extra))
        // Escaped slashes, as some serialisers write URLs, and a unicode escape for a hyphen.
        val slashed = "https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk?v=3800ad62".replace("/", "$BS/")
        assertEquals(want, parsed("apk" to q(slashed), "version" to q("1.0.0" + BS + "u002drc30")))
    }

    @Test
    fun `a leading byte order mark is tolerated`() {
        // Windows PowerShell and some editors write one; without this the check would silently never work.
        assertEquals(UpdateCheck.parse(json()), UpdateCheck.parse(BOM + json()))
        assertNull(UpdateCheck.parse(BOM.toString() + BOM + json()), "one is tolerated, a stack of them is not")
        assertNull(UpdateCheck.parse(json() + BOM), "and only at the start")
    }

    @Test
    fun `the reader decodes every escape and every number shape`() {
        val text = "{\"s\":\"a${BS}\"b${BS}${BS}c${BS}/d${BS}b${BS}f${BS}n${BS}r${BS}t${BS}u0041${BS}u00e9${BS}u00E9\"}"
        val want = buildString {
            append("a\"b"); append(BS); append("c/d"); append('\b'); append(12.toChar()); append('\n'); append('\r'); append('\t')
            append('A'); append(0xE9.toChar()); append(0xE9.toChar())
        }
        assertEquals(want, UpdateCheck.readFlat(text)!!["s"])

        val n = UpdateCheck.readFlat("{\"a\":1,\"b\":-0,\"c\":1.5,\"d\":1e2,\"e\":-2.5E-3,\"f\":12345678901234567890,\"g\":0,\"h\":-7}")!!
        assertEquals(1L, n["a"]); assertEquals(0L, n["b"]); assertEquals(0L, n["g"]); assertEquals(-7L, n["h"])
        assertEquals(1.5, n["c"]); assertEquals(100.0, n["d"]); assertEquals(-0.0025, n["e"])
        // Too big for a Long: not a Long, so no field will take it.
        assertTrue(n["f"] is Double)

        val lit = UpdateCheck.readFlat("{\"t\":true,\"f\":false,\"z\":null}")!!
        assertEquals(true, lit["t"]); assertEquals(false, lit["f"]); assertTrue(lit.containsKey("z") && lit["z"] == null)

        for (bad in listOf("01", "+1", ".5", "1.", "1e", "-", "0x1F", "NaN", "Infinity", "tru", "nul", "True", "'x'", "\"x", "\"a${BS}q\"", "\"${BS}u12G4\"", "\"${BS}u12\"", "\"a\nb\""))
            assertNull(UpdateCheck.readFlat("{\"a\":$bad}"), bad)
    }

    @Test
    fun `an apk that is not on our own site is refused`() {
        val good = "https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk"
        assertNotNull(parsed("apk" to q(good)), "control")
        for (apk in listOf(
            "https://evil.example/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "http://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "https://willowcreek.group.evil.example/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "https://willowcreek.group@evil.example/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "https://evil.example/https://willowcreek.group/kaizocore/x.apk",
            "https://www.willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "https://willowcreek.group/other/KaizoCore-1.0.0-rc30.apk",
            "https://willowcreek.group/kaizocore",
            "https://willowcreek.group/",
            "//willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "HTTPS://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "ftp://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "javascript:alert(1)",
            "intent://willowcreek.group/kaizocore/x#Intent;scheme=https;end",
            "market://details?id=com.ironmonone.app",
            "/kaizocore/KaizoCore-1.0.0-rc30.apk",
            "KaizoCore-1.0.0-rc30.apk",
            "",
        )) refused("apk $apk", "apk" to q(apk))
        // One clean token: whitespace, a control character or a backslash could be read differently by a browser.
        for (bad in listOf(
            "$good x", "$good ", "$good${BS}n", "$good${BS}t", "$good${BS}r", "$good${BS}u0000", "$good${BS}u007f", "$good${BS}u0020",
            "https://willowcreek.group/kaizocore/${BS}${BS}evil.example",
        )) assertNull(parsed("apk" to q(bad)), "apk with a stray character: $bad")
        refused("apk that is not a string", "apk" to "5")
        refused("apk that is null", "apk" to "null")
        refused("apk missing", "apk" to null)
        refused("apk that is an object", "apk" to "{}")
    }

    @Test
    fun `a bad sha256 is refused`() {
        val good = "3800ad62cad6c21a87da5dedd663f1e8e9b716bf20076aa8ce9e1b7a8fe9cc7e"
        assertNotNull(parsed("sha256" to q(good)), "control")
        for (sha in listOf(
            good.dropLast(1), good + "0", "", good.uppercase(), good.replaceRange(0, 1, "g"), good.replaceRange(10, 11, " "),
            " $good", "$good ", "0x$good".take(64), good.take(32),
        )) refused("sha256 '$sha'", "sha256" to q(sha))
        refused("sha256 as a number", "sha256" to "12345")
        refused("sha256 missing", "sha256" to null)
        // 64 of anything lower-case hex is what counts, not this particular build's hash.
        assertNotNull(parsed("sha256" to q("0".repeat(64))))
        assertNotNull(parsed("sha256" to q("abcdef0123456789".repeat(4))))
    }

    @Test
    fun `a missing or bad version code is refused`() {
        assertNotNull(parsed("versionCode" to "1"), "control: the smallest real code")
        refused("missing", "versionCode" to null)
        refused("zero", "versionCode" to "0")
        refused("negative", "versionCode" to "-1")
        refused("a string", "versionCode" to q("39"))
        refused("a fraction", "versionCode" to "39.5")
        refused("an exponent", "versionCode" to "3.9e1")
        refused("a boolean", "versionCode" to "true")
        refused("null", "versionCode" to "null")
        refused("an object", "versionCode" to "{}")
        refused("too big to be a number here", "versionCode" to "99999999999999999999")
        refused("leading zero", "versionCode" to "039")
    }

    @Test
    fun `the wrong app is refused`() {
        refused("missing", "app" to null)
        refused("another app", "app" to q("SomethingElse"))
        refused("wrong case", "app" to q("kaizocore"))
        refused("with a space", "app" to q("KaizoCore "))
        refused("empty", "app" to q(""))
        refused("a number", "app" to "1")
    }

    @Test
    fun `a bad version name is refused`() {
        assertNotNull(parsed("version" to q("1.0.0-rc30+c8e0a70")), "control: a build id is allowed")
        assertNotNull(parsed("version" to q("a".repeat(40))), "40 characters is the limit and fits")
        refused("41 characters", "version" to q("a".repeat(41)))
        refused("blank", "version" to q(""))
        refused("spaces only", "version" to q("   "))
        refused("a space inside", "version" to q("1.0.0 rc30"))
        refused("a slash", "version" to q("1.0.0/rc30"))
        refused("a bracket", "version" to q("<b>1.0</b>"))
        refused("a quote", "version" to q("1.0.0" + BS + "\"rc30"))
        refused("a newline", "version" to q("1.0.0" + BS + "nrc30"))
        refused("a letter with an accent", "version" to q("1.0.0-r" + BS + "u00e9"))
        refused("missing", "version" to null)
        refused("a number", "version" to "1.0")
    }

    @Test
    fun `a missing or bad size is refused`() {
        assertNotNull(parsed("bytes" to "1"), "control: one byte is a size")
        refused("missing", "bytes" to null)
        refused("zero", "bytes" to "0")
        refused("negative", "bytes" to "-5")
        refused("a string", "bytes" to q("120385434"))
        refused("a fraction", "bytes" to "1.5")
        refused("null", "bytes" to "null")
    }

    @Test
    fun `notes and page that are not ours are dropped and the manifest stands`() {
        val want = UpdateCheck.parse(json())!!
        // The unchanged ones are kept.
        assertEquals("https://github.com/blakeportable7-del/KaizoCore/releases/tag/v1.0.0-rc30", want.notes)
        assertEquals("https://willowcreek.group/kaizocore", want.page)
        // Our own site is fine for notes too.
        assertEquals("https://willowcreek.group/kaizocore/notes", parsed("notes" to q("https://willowcreek.group/kaizocore/notes"))!!.notes)
        assertEquals("https://willowcreek.group/", parsed("page" to q("https://willowcreek.group/"))!!.page)

        for (url in listOf(
            "https://evil.example/blakeportable7-del/KaizoCore/releases",
            "https://github.com/someone-else/KaizoCore/releases",
            "https://github.com/blakeportable7-del/KaizoCore-fake/releases",
            "https://github.com/blakeportable7-del/KaizoCore",
            "https://github.com.evil.example/blakeportable7-del/KaizoCore/releases",
            "https://github.com@evil.example/blakeportable7-del/KaizoCore/releases",
            "http://github.com/blakeportable7-del/KaizoCore/releases",
            "http://willowcreek.group/kaizocore",
            "javascript:alert(1)", "",
            "https://github.com/blakeportable7-del/KaizoCore/releases x",
        )) {
            val m = parsed("notes" to q(url))
            assertNotNull(m, "the manifest stands with notes $url")
            assertNull(m.notes, "notes $url")
            assertEquals(want.copy(notes = null), m)
        }
        for (url in listOf(
            "https://evil.example/kaizocore", "http://willowcreek.group/kaizocore", "https://willowcreek.group.evil.example/",
            "https://willowcreek.group", "https://github.com/blakeportable7-del/KaizoCore/releases", "javascript:alert(1)", "",
            "https://willowcreek.group/kaizocore x",
        )) {
            val m = parsed("page" to q(url))
            assertNotNull(m, "the manifest stands with page $url")
            assertNull(m.page, "page $url")
            assertEquals(want.copy(page = null), m)
        }
        // Not a string at all: dropped the same way.
        assertNull(parsed("notes" to "5")!!.notes)
        assertNull(parsed("page" to "{}")!!.page)
        assertNull(parsed("notes" to "null")!!.notes)
    }

    @Test
    fun `notes and page are optional`() {
        val m = parsed("notes" to null, "page" to null)
        assertNotNull(m)
        assertNull(m.notes)
        assertNull(m.page)
        assertEquals(39L, m.versionCode)
    }

    @Test
    fun `garbage, empty and truncated text return null without throwing`() {
        val text = json()
        assertNotNull(UpdateCheck.parse(text), "control")
        // Every cut of the file short of the whole is refused: a download that stopped half way is not a manifest.
        for (n in 0 until text.length) assertNull(UpdateCheck.parse(text.substring(0, n)), "cut after $n characters")
        val tight = compact()
        assertNotNull(UpdateCheck.parse(tight), "control")
        for (n in 0 until tight.length) assertNull(UpdateCheck.parse(tight.substring(0, n)), "compact, cut after $n")

        val nul = 0.toChar()
        val junk = listOf(
            "", " ", "\n\t\r", "null", "true", "42", "\"KaizoCore\"", "[]", "[${text}]", "{}", "{\"app\":\"KaizoCore\"}",
            "{,}", "{\"a\":}", "{\"a\" 1}", "{a:1}", "{'app':'KaizoCore'}", "{\"a\":1,}", "{\"a\":1}}", "{{}}", "{\"a\":[}",
            text + " x", text + text, text + "{}", "x" + text,
            "<html><head><title>Sign in to the network</title></head><body>Log in</body></html>",
            "HTTP/1.1 200 OK", "PK" + nul + nul, nul.toString(), nul.toString().repeat(500),
            "{\"app\":\"KaizoCore\",\"versionCode\":39,\"version\":\"1.0.0\",\"apk\":\"https://willowcreek.group/kaizocore/x.apk\",\"bytes\":1,\"sha256\":\"",
            "{".repeat(50_000), "[".repeat(50_000), "{\"a\":".repeat(50_000), "\"".repeat(50_000), "x".repeat(1_000_000),
            json("deep" to "[".repeat(50_000)), json("deep" to "[".repeat(50_000) + "]".repeat(50_000)),
            json("deep" to "{\"a\":".repeat(20_000) + "1" + "}".repeat(20_000)),
        )
        for (j in junk) assertNull(UpdateCheck.parse(j), "junk of ${j.length} characters starting ${j.take(30)}")
        // Nesting is fine at a sane depth in a key the app does not know.
        assertNotNull(parsed("deep" to "[[[1, {\"a\": [2]}]]]"))
    }

    @Test
    fun `a key given twice is refused`() {
        val twice = json().trimEnd().removeSuffix("}") + ",\n  \"version\": \"1.0.1\"\n}"
        assertNull(UpdateCheck.parse(twice))
        val unknownTwice = json("x" to "1").trimEnd().removeSuffix("}") + ",\n  \"x\": 2\n}"
        assertNull(UpdateCheck.parse(unknownTwice))
    }

    // --------------------------------------------------------------- rules
    @Test
    fun `only a higher version code is an update`() {
        val m = manifest(39)
        assertTrue(UpdateCheck.isNewer(m, 38))
        assertTrue(UpdateCheck.isNewer(m, 0))
        assertFalse(UpdateCheck.isNewer(m, 39))
        assertFalse(UpdateCheck.isNewer(m, 40))
        assertFalse(UpdateCheck.isNewer(m, 1_000_000))
    }

    @Test
    fun `the automatic check asks once every 20 hours`() {
        val now = NOW
        assertTrue(UpdateCheck.due(true, 0L, now), "never asked")
        assertTrue(UpdateCheck.due(true, 0L, 5L), "never asked, even on a clock that reads almost zero")
        assertFalse(UpdateCheck.due(true, now - 19 * HOUR, now), "19 hours")
        assertFalse(UpdateCheck.due(true, now - 1, now), "just now")
        assertFalse(UpdateCheck.due(true, now, now), "this instant")
        assertFalse(UpdateCheck.due(true, now - (20 * HOUR - 1), now), "a millisecond short of 20 hours")
        assertTrue(UpdateCheck.due(true, now - 20 * HOUR, now), "20 hours exactly")
        assertTrue(UpdateCheck.due(true, now - 21 * HOUR, now), "21 hours")
        assertTrue(UpdateCheck.due(true, now - 400 * 24 * HOUR, now), "over a year")
        assertEquals(20 * HOUR, UpdateCheck.CHECK_EVERY_MS)
        // Switched off is off, however long ago.
        assertFalse(UpdateCheck.due(false, 0L, now))
        assertFalse(UpdateCheck.due(false, now - 400 * 24 * HOUR, now))
        // The clock moved back: the last check is in the future. That is due, not "wait for the clock".
        assertTrue(UpdateCheck.due(true, now + HOUR, now), "an hour ahead")
        assertTrue(UpdateCheck.due(true, now + 3000 * 24 * HOUR, now), "years ahead")
        assertFalse(UpdateCheck.due(false, now + HOUR, now), "and off is still off")
    }

    @Test
    fun `a snooze holds one build until it runs out, and a newer build breaks through`() {
        val day = 24 * HOUR
        val m39 = manifest(39)
        // Not newer: never.
        assertFalse(UpdateCheck.shouldPrompt(m39, 39, 0L, 0L, NOW))
        assertFalse(UpdateCheck.shouldPrompt(m39, 40, 0L, 0L, NOW))
        // Newer, nothing snoozed.
        assertTrue(UpdateCheck.shouldPrompt(m39, 38, 0L, 0L, NOW))
        // This build snoozed and still inside it.
        assertFalse(UpdateCheck.shouldPrompt(m39, 38, 39L, NOW + day, NOW))
        assertFalse(UpdateCheck.shouldPrompt(m39, 38, 39L, NOW + 1, NOW), "a millisecond left")
        // Ran out, exactly and long ago.
        assertTrue(UpdateCheck.shouldPrompt(m39, 38, 39L, NOW, NOW), "the snooze ends at its time")
        assertTrue(UpdateCheck.shouldPrompt(m39, 38, 39L, NOW - day, NOW))
        // A newer build than the snoozed one prompts at once, snooze or not.
        assertTrue(UpdateCheck.shouldPrompt(manifest(40), 38, 39L, NOW + 3 * day, NOW))
        // A snooze of some other build (older or newer) does not hold this one.
        assertTrue(UpdateCheck.shouldPrompt(m39, 38, 38L, NOW + day, NOW))
        assertTrue(UpdateCheck.shouldPrompt(m39, 38, 41L, NOW + day, NOW))
        // Snoozed and already installed: still not an update.
        assertFalse(UpdateCheck.shouldPrompt(m39, 39, 39L, NOW - day, NOW))
        assertEquals(24 * HOUR, UpdateCheck.DOWNLOAD_SNOOZE_MS)
        assertEquals(3 * day, UpdateCheck.LATER_SNOOZE_MS)
    }

    // --------------------------------------------------------------- state
    @Test
    fun `the state round-trips and defaults to on`() {
        assertEquals(UpdateCheck.State(enabled = true, lastCheckMs = 0L, snoozedCode = 0L, snoozedUntilMs = 0L), UpdateCheck.parseState(""))
        assertTrue(UpdateCheck.State().enabled, "on until the player says otherwise")
        for (s in listOf(
            UpdateCheck.State(),
            UpdateCheck.State(enabled = false, lastCheckMs = NOW, snoozedCode = 39L, snoozedUntilMs = NOW + 3 * 24 * HOUR),
            UpdateCheck.State(enabled = true, lastCheckMs = 1L, snoozedCode = Long.MAX_VALUE, snoozedUntilMs = Long.MAX_VALUE),
            UpdateCheck.State(enabled = false),
        )) assertEquals(s, UpdateCheck.parseState(UpdateCheck.formatState(s)), s.toString())
        // The file is key=value lines, as written.
        assertEquals(
            "enabled=false\nlastCheck=5\nsnoozedCode=39\nsnoozedUntil=9\n",
            UpdateCheck.formatState(UpdateCheck.State(false, 5L, 39L, 9L)),
        )
        // Only an explicit false switches it off.
        for (off in listOf("enabled=false", "enabled=FALSE", "enabled = false ", "x=1\nenabled=false\n", "enabled=false\r\nlastCheck=1\r\n"))
            assertFalse(UpdateCheck.parseState(off).enabled, off)
        for (on in listOf("enabled=true", "enabled=", "enabled=0", "enabled=no", "enabled=fals", "enabled", "Enabled=false", "=false", "garbage", "\n\n"))
            assertTrue(UpdateCheck.parseState(on).enabled, on)
        // Damaged numbers and unknown lines are ignored, the rest still read.
        val s = UpdateCheck.parseState("lastCheck=abc\nsnoozedCode=39\nsnoozedUntil=1.5\nmystery=1\nno equals here\nlastCheck=\n")
        assertEquals(UpdateCheck.State(enabled = true, lastCheckMs = 0L, snoozedCode = 39L, snoozedUntilMs = 0L), s)
    }

    @Test
    fun `the state file sits beside the app's files, outside the backup, and a missing one is the default`() = withDir { dir ->
        assertEquals(dir, UpdateCheck.stateFile(dir).parentFile, "directly in filesDir, not in a folder")
        assertEquals("update-check.txt", UpdateCheck.stateFile(dir).name)
        assertFalse(Backup.admits(UpdateCheck.STATE_FILE), "device-local: another phone's snooze means nothing here")
        assertEquals(UpdateCheck.State(), UpdateCheck.loadState(dir), "no file yet")
        val s = UpdateCheck.State(enabled = false, lastCheckMs = NOW, snoozedCode = 39L, snoozedUntilMs = NOW + HOUR)
        assertTrue(UpdateCheck.saveState(dir, s))
        assertEquals(s, UpdateCheck.loadState(dir))
        assertEquals(listOf("update-check.txt"), dir.list()!!.toList(), "no temp file left beside it")
        // A damaged file is the default, not a crash.
        UpdateCheck.stateFile(dir).writeBytes(byteArrayOf(0, -1, 7, 61, 61))
        assertEquals(UpdateCheck.State(), UpdateCheck.loadState(dir))
        // The folder can be gone entirely (the app's data cleared): the write makes it again.
        val nested = File(dir, "gone/away")
        assertTrue(UpdateCheck.saveState(nested, s))
        assertEquals(s, UpdateCheck.loadState(nested))
    }

    @Test
    fun `the switch and a snooze are written without touching the rest`() = withDir { dir ->
        UpdateCheck.saveState(dir, UpdateCheck.State(enabled = true, lastCheckMs = NOW, snoozedCode = 0L, snoozedUntilMs = 0L))
        UpdateCheck.setEnabled(dir, false)
        assertEquals(UpdateCheck.State(enabled = false, lastCheckMs = NOW), UpdateCheck.loadState(dir))
        UpdateCheck.snooze(dir, 39L, UpdateCheck.LATER_SNOOZE_MS, NOW)
        assertEquals(UpdateCheck.State(enabled = false, lastCheckMs = NOW, snoozedCode = 39L, snoozedUntilMs = NOW + 3 * 24 * HOUR), UpdateCheck.loadState(dir))
        UpdateCheck.setEnabled(dir, true)
        assertEquals(UpdateCheck.State(enabled = true, lastCheckMs = NOW, snoozedCode = 39L, snoozedUntilMs = NOW + 3 * 24 * HOUR), UpdateCheck.loadState(dir))
    }

    // -------------------------------------------------------- the launch check
    @Test
    fun `the launch check asks only when it is due and on`() = withDir { dir ->
        val asked = AtomicInteger()
        val ask = { asked.incrementAndGet(); manifest(39) }
        // Never asked: asks, and hands the newer build back.
        assertEquals(manifest(39), UpdateCheck.checkOnce(dir, 38L, NOW, ask))
        assertEquals(1, asked.get())
        assertEquals(NOW, UpdateCheck.loadState(dir).lastCheckMs)
        // Launched again inside 20 hours: does not ask.
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW + 19 * HOUR, ask))
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW + HOUR, ask))
        assertEquals(1, asked.get(), "not asked again inside 20 hours")
        assertEquals(NOW, UpdateCheck.loadState(dir).lastCheckMs, "and the time is not moved by launches that did not ask")
        // 21 hours on: asks again.
        assertEquals(manifest(39), UpdateCheck.checkOnce(dir, 38L, NOW + 21 * HOUR, ask))
        assertEquals(2, asked.get())
        assertEquals(NOW + 21 * HOUR, UpdateCheck.loadState(dir).lastCheckMs)
        // Switched off: never asks, however long it has been, and leaves the file alone.
        UpdateCheck.setEnabled(dir, false)
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW + 500 * 24 * HOUR, ask))
        assertEquals(2, asked.get())
        assertEquals(NOW + 21 * HOUR, UpdateCheck.loadState(dir).lastCheckMs)
        assertFalse(UpdateCheck.loadState(dir).enabled)
    }

    @Test
    fun `the launch check records the ask even when nothing came back`() = withDir { dir ->
        val asked = AtomicInteger()
        // No signal, a bad file, a wrong host: all of them null from the fetch.
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW) { asked.incrementAndGet(); null })
        assertEquals(1, asked.get())
        assertEquals(NOW, UpdateCheck.loadState(dir).lastCheckMs, "a failed ask counts: no signal must not mean an ask at every launch")
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW + HOUR) { asked.incrementAndGet(); manifest(39) })
        assertEquals(1, asked.get(), "so the next launch does not ask")
        // An answer that is not newer is recorded too.
        withDir { d2 ->
            assertNull(UpdateCheck.checkOnce(d2, 39L, NOW) { manifest(39) })
            assertNull(UpdateCheck.checkOnce(d2, 45L, NOW + 30 * HOUR) { manifest(39) })
            assertEquals(NOW + 30 * HOUR, UpdateCheck.loadState(d2).lastCheckMs)
        }
    }

    @Test
    fun `the launch check hands back a build only when it is newer and not snoozed`() = withDir { dir ->
        val day = 24 * HOUR
        // Snoozed for a day at NOW (the player tapped Download): 21 hours later it asks, and stays quiet.
        UpdateCheck.snooze(dir, 39L, UpdateCheck.DOWNLOAD_SNOOZE_MS, NOW)
        assertNull(UpdateCheck.checkOnce(dir, 38L, NOW + 21 * HOUR) { manifest(39) }, "still inside the day")
        assertEquals(NOW + 21 * HOUR, UpdateCheck.loadState(dir).lastCheckMs, "it asked; it just did not interrupt")
        // 42 hours in: the snooze is over.
        assertEquals(manifest(39), UpdateCheck.checkOnce(dir, 38L, NOW + 42 * HOUR) { manifest(39) })
        // A newer build than the snoozed one comes through at once.
        UpdateCheck.snooze(dir, 39L, 3 * day, NOW + 42 * HOUR)
        assertEquals(manifest(40), UpdateCheck.checkOnce(dir, 38L, NOW + 64 * HOUR) { manifest(40) })
        // Installed already: nothing, snooze or not.
        assertNull(UpdateCheck.checkOnce(dir, 40L, NOW + 90 * HOUR) { manifest(40) })
    }

    @Test
    fun `a switch or a snooze that changes while the request is out is not undone`() {
        withDir { dir ->
            // The player turns the check off while it is on the wire; the write after the answer must keep that.
            UpdateCheck.checkOnce(dir, 38L, NOW) { UpdateCheck.setEnabled(dir, false); manifest(39) }
            val s = UpdateCheck.loadState(dir)
            assertFalse(s.enabled, "the switch survived the check's own write")
            assertEquals(NOW, s.lastCheckMs)
        }
        withDir { dir ->
            // A snooze recorded while the request was out holds the very answer that comes back.
            val got = UpdateCheck.checkOnce(dir, 38L, NOW) { UpdateCheck.snooze(dir, 39L, 24 * HOUR, NOW); manifest(39) }
            assertNull(got, "the snooze is read after the answer, not before the request")
            assertEquals(39L, UpdateCheck.loadState(dir).snoozedCode)
        }
    }

    @Test
    fun `Check now ignores the cadence, a snooze and the switch, and records the ask`() = withDir { dir ->
        UpdateCheck.saveState(dir, UpdateCheck.State(enabled = false, lastCheckMs = NOW - 1000, snoozedCode = 39L, snoozedUntilMs = NOW + 24 * HOUR))
        val asked = AtomicInteger()
        val out = UpdateCheck.checkNow(dir, 38L, NOW) { asked.incrementAndGet(); manifest(39) }
        assertEquals(UpdateCheck.Outcome.Newer(manifest(39)), out)
        assertEquals(1, asked.get(), "asked although off, snoozed and asked a second ago")
        val s = UpdateCheck.loadState(dir)
        assertEquals(NOW, s.lastCheckMs)
        assertFalse(s.enabled, "asking by hand does not turn the daily check back on")
        assertEquals(39L, s.snoozedCode, "and does not clear a snooze")
        // Current: same build, or the site is behind the phone.
        assertEquals(UpdateCheck.Outcome.Current, UpdateCheck.checkNow(dir, 39L, NOW + 1) { manifest(39) })
        assertEquals(UpdateCheck.Outcome.Current, UpdateCheck.checkNow(dir, 50L, NOW + 2) { manifest(39) })
        // No answer.
        assertEquals(UpdateCheck.Outcome.Unreachable, UpdateCheck.checkNow(dir, 38L, NOW + 3) { null })
        assertEquals(NOW + 3, UpdateCheck.loadState(dir).lastCheckMs, "a failed ask is recorded too")
    }

    // -------------------------------------------------------------- network
    /** A request as it came over the wire: the first line and the headers in the order they were sent. */
    private class Request(val line: String, val headers: List<Pair<String, String>>) {
        val target: String get() = line.split(' ').getOrElse(1) { "" }
        fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    /** One connection: the request, and the stream a route writes its raw answer to. */
    private class Wire(val request: Request, val out: OutputStream)

    private fun route(path: String, handler: (Wire) -> Unit): Pair<String, (Wire) -> Unit> = path to handler

    private fun Wire.reply(code: Int, body: ByteArray, type: String = "application/json", extra: List<Pair<String, String>> = emptyList()) {
        val head = StringBuilder("HTTP/1.1 $code Status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
        for ((k, v) in extra) head.append(k).append(": ").append(v).append("\r\n")
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun readRequest(input: InputStream): Request? {
        val raw = ByteArrayOutputStream()
        var tail = 0
        while (raw.size() < 16 * 1024) {
            val b = input.read()
            if (b < 0) return null
            raw.write(b)
            tail = (tail shl 8) or b
            if (tail == 0x0D0A0D0A) break
        }
        val lines = String(raw.toByteArray(), Charsets.ISO_8859_1).split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        return Request(lines[0], lines.drop(1).map { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() })
    }

    /** A real HTTP server on 127.0.0.1, port chosen by the OS, for the length of [block]. Unrouted paths get a 404. */
    private fun serve(vararg routes: Pair<String, (Wire) -> Unit>, block: (host: String) -> Unit) {
        val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val open = Collections.synchronizedList(ArrayList<Socket>())
        val acceptor = Thread accept@{
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (e: Exception) { break }
                open.add(socket)
                Thread conn@{
                    try {
                        val request = readRequest(socket.getInputStream()) ?: return@conn
                        val handler = routes.firstOrNull { it.first == request.target.substringBefore('?') }?.second
                        val wire = Wire(request, socket.getOutputStream())
                        if (handler != null) handler(wire) else wire.reply(404, "not here".toByteArray(), "text/plain")
                    } catch (e: Exception) {
                        // The client hung up, which is what several of these tests are for.
                    } finally {
                        runCatching { socket.close() }
                    }
                }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }
        acceptor.start()
        try {
            block("http://127.0.0.1:${listener.localPort}")
        } finally {
            runCatching { listener.close() }
            synchronized(open) { open.forEach { runCatching { it.close() } } }
        }
    }

    private val PATH = "/kaizocore/latest.json"

    @Test
    fun `fetch reads the manifest from a 200 answer`() {
        serve(route(PATH) { w -> w.reply(200, json().toByteArray()) }) { host ->
            assertEquals(UpdateCheck.parse(json()), UpdateCheck.fetch(host + PATH))
            assertNotNull(UpdateCheck.fetch(host + PATH))
        }
        // The same file with a byte order mark and CRLF line ends, as a Windows tool would write it.
        serve(route(PATH) { w -> w.reply(200, (BOM + json().replace("\n", "\r\n")).toByteArray(Charsets.UTF_8)) }) { host ->
            assertEquals(UpdateCheck.parse(json()), UpdateCheck.fetch(host + PATH))
        }
    }

    @Test
    fun `fetch sends only what the card says it sends`() {
        val seen = AtomicReference<Request>()
        serve(route(PATH) { w -> seen.set(w.request); w.reply(200, json().toByteArray()) }) { host ->
            assertNotNull(UpdateCheck.fetch(host + PATH))
        }
        val req = seen.get()!!
        assertEquals("GET $PATH HTTP/1.1", req.line, "one GET for the path and nothing after it: no query, no id")
        assertEquals("KaizoCore", req.header("User-Agent"), "the default agent would name the phone's model")
        assertEquals("application/json", req.header("Accept"))
        assertEquals("no-cache", req.header("Cache-Control"))
        // Nothing that could identify the phone or the player, and nothing that is not in this list.
        val names = req.headers.map { it.first.lowercase() }
        val allowed = setOf("host", "user-agent", "accept", "cache-control", "pragma", "connection", "accept-encoding")
        assertTrue(names.all { it in allowed }, "unexpected request headers: ${names - allowed}")
        for (name in listOf("cookie", "authorization", "referer", "from", "x-requested-with", "accept-language", "if-none-match", "if-modified-since"))
            assertFalse(name in names, name)
        assertEquals(names.size, names.toSet().size, "no header twice")
    }

    @Test
    fun `fetch refuses every answer but a whole manifest in a 200`() {
        val good = json().toByteArray()
        for (code in listOf(201, 202, 301, 302, 400, 403, 404, 410, 429, 500, 503)) {
            val extra = if (code == 301 || code == 302) listOf("Location" to "/elsewhere") else emptyList()
            serve(route(PATH) { w -> w.reply(code, good, extra = extra) }) { host -> assertNull(UpdateCheck.fetch(host + PATH), "status $code") }
        }
        // No body at all, and a path the server has no route for.
        serve(route(PATH) { w -> w.reply(204, ByteArray(0)) }) { host -> assertNull(UpdateCheck.fetch(host + PATH), "204") }
        serve(route(PATH) { w -> w.reply(200, ByteArray(0)) }) { host -> assertNull(UpdateCheck.fetch(host + PATH), "an empty 200") }
        serve(route("/other") { w -> w.reply(200, good) }) { host -> assertNull(UpdateCheck.fetch(host + PATH), "the file is not there (404)") }
        // A captive portal answers 200 with a sign-in page.
        serve(route(PATH) { w -> w.reply(200, "<html><body>Sign in to the Wi-Fi</body></html>".toByteArray(), "text/html") }) { host ->
            assertNull(UpdateCheck.fetch(host + PATH), "a portal page")
        }
        // A 200 whose manifest names a build somewhere else.
        serve(route(PATH) { w -> w.reply(200, json("apk" to q("https://evil.example/kaizocore/x.apk")).toByteArray()) }) { host ->
            assertNull(UpdateCheck.fetch(host + PATH), "a foreign apk")
        }
    }

    @Test
    fun `fetch does not follow a redirect`() {
        val hits = AtomicInteger()
        serve(
            route(PATH) { w -> w.reply(302, ByteArray(0), extra = listOf("Location" to "/kaizocore/moved.json")) },
            route("/kaizocore/moved.json") { w -> hits.incrementAndGet(); w.reply(200, json().toByteArray()) },
        ) { host ->
            assertNull(UpdateCheck.fetch(host + PATH))
            assertEquals(0, hits.get(), "the card says the question goes to one place; a redirect must not send it on")
            // And the target itself is fine when asked directly, so the null above is the redirect's doing.
            assertNotNull(UpdateCheck.fetch(host + "/kaizocore/moved.json"))
        }
    }

    @Test
    fun `fetch refuses a body over 16 KB and takes one of exactly 16 KB`() {
        val limit = UpdateCheck.MAX_BODY
        assertEquals(16 * 1024, limit)
        fun padded(size: Int): ByteArray {
            val body = compact().toByteArray()
            assertTrue(body.size < size)
            return body + ByteArray(size - body.size) { ' '.code.toByte() }
        }
        serve(route(PATH) { w -> w.reply(200, padded(limit)) }) { host ->
            assertNotNull(UpdateCheck.fetch(host + PATH), "exactly 16 KB of manifest and spaces")
        }
        serve(route(PATH) { w -> w.reply(200, padded(limit + 1)) }) { host ->
            assertNull(UpdateCheck.fetch(host + PATH), "one byte over")
        }
        // 20 KB of a perfectly good manifest followed by spaces: refused for its size, not for what is in it.
        serve(route(PATH) { w -> w.reply(200, padded(20 * 1024)) }) { host ->
            assertNull(UpdateCheck.fetch(host + PATH), "20 KB")
        }
        // 8 MB is refused without being held: the read stops at the limit.
        serve(route(PATH) { w -> w.reply(200, padded(8_000_000)) }) { host ->
            val t0 = System.nanoTime()
            assertNull(UpdateCheck.fetch(host + PATH), "8 MB")
            // Only that it does not hang: loose, for a busy machine (rc32 audit P3 #83).
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 15_000)
        }
    }

    @Test
    fun `fetch gives up on a server that never answers`() {
        val release = CountDownLatch(1)
        serve(route(PATH) { w -> release.await(15, TimeUnit.SECONDS); w.reply(200, json().toByteArray()) }) { host ->
            val t0 = System.nanoTime()
            val m = UpdateCheck.fetch(host + PATH, timeoutMs = 300)
            val ms = (System.nanoTime() - t0) / 1_000_000
            release.countDown()
            assertNull(m)
            // Well above the 300 ms timeout, for a busy machine, and below the server's 15 s hold (rc32 audit P3 #83).
            assertTrue(ms < 10_000, "gave up after $ms ms, with a 300 ms timeout")
        }
    }

    @Test
    fun `fetch gives up on a server that trickles the answer`() {
        // A good manifest at one byte every 15 ms takes seconds. Each read is quick, so the read timeout
        // alone never fires; the answer has to be refused for taking too long overall.
        serve(route(PATH) { w ->
            val body = json().toByteArray()
            w.out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            w.out.flush()
            for (b in body) { w.out.write(b.toInt()); w.out.flush(); Thread.sleep(15) }
        }) { host ->
            val t0 = System.nanoTime()
            val m = UpdateCheck.fetch(host + PATH, timeoutMs = 500)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertNull(m, "the trickled manifest is not accepted")
            // Above the 500 ms budget with room, below the whole trickle's 7 s or so (rc32 audit P3 #83).
            assertTrue(ms < 5000, "gave up after $ms ms, with a 500 ms budget")
        }
    }

    @Test
    fun `fetch returns null for a closed port, a bad address and an address that is not http`() {
        // A port that was open a moment ago.
        var dead = ""
        serve(route(PATH) { w -> w.reply(200, json().toByteArray()) }) { host -> dead = host + PATH }
        assertNull(UpdateCheck.fetch(dead, timeoutMs = 500))
        for (bad in listOf("", "not a url", "://nothing", "http://127.0.0.1:0/x", "http://[::1"))
            assertNull(UpdateCheck.fetch(bad, timeoutMs = 500), "'$bad'")
        // A file: address holding a perfect manifest is still not fetched: only http and https are asked.
        withDir { dir ->
            val f = File(dir, "latest.json").apply { writeText(json()) }
            assertNull(UpdateCheck.fetch(f.toURI().toString(), timeoutMs = 500))
            assertNull(UpdateCheck.fetch("ftp://127.0.0.1/latest.json", timeoutMs = 500))
            assertNull(UpdateCheck.fetch("jar:" + f.toURI() + "!/x", timeoutMs = 500))
        }
    }

    // ---------------------------------------------------------------- misc
    @Test
    fun `sizes are shown in whole decimal megabytes, never below one`() {
        assertEquals(120, UpdateCheck.megabytes(120_385_434L))
        assertEquals(121, UpdateCheck.megabytes(120_500_000L))
        assertEquals(120, UpdateCheck.megabytes(120_499_999L))
        assertEquals(1, UpdateCheck.megabytes(1L))
        assertEquals(1, UpdateCheck.megabytes(499_999L))
        assertEquals(1, UpdateCheck.megabytes(1_400_000L))
        assertEquals(2, UpdateCheck.megabytes(1_500_000L))
        assertEquals(1500, UpdateCheck.megabytes(1_500_000_000L))
        assertEquals(99_999, UpdateCheck.megabytes(Long.MAX_VALUE))
    }

    @Test
    fun `the installed version is shown without its build id`() {
        assertEquals("1.0.0-rc30", UpdateCheck.displayVersion("1.0.0-rc30+c8e0a70"))
        assertEquals("1.0.0-rc30", UpdateCheck.displayVersion("1.0.0-rc30+local"))
        assertEquals("1.0.0-rc30", UpdateCheck.displayVersion("1.0.0-rc30"))
        assertEquals("1.0.0-rc30", UpdateCheck.displayVersion("1.0.0-rc30+a+b"))
        assertEquals("?", UpdateCheck.displayVersion(""))
        assertEquals("?", UpdateCheck.displayVersion("+abc"))
    }

    @Test
    fun `the demo build passes the same checks as a real one`() {
        val d = UpdateCheck.demo(38L)
        assertEquals(39L, d.versionCode, "one past the installed build")
        assertEquals("1.0.0-rc99", d.version)
        assertTrue(UpdateCheck.isNewer(d, 38L))
        assertFalse(UpdateCheck.isNewer(d, 39L))
        // Written out as the site would and read back by the real parser: nothing the demo shows is a shape the parser refuses.
        val written = json(
            "versionCode" to d.versionCode.toString(), "version" to q(d.version), "apk" to q(d.apk),
            "bytes" to d.bytes.toString(), "sha256" to q(d.sha256), "notes" to q(d.notes!!), "page" to q(d.page!!),
        )
        assertEquals(d, UpdateCheck.parse(written))
    }

    @Test
    fun `everything the player reads is plain and follows the house rules`() {
        val m = manifest(39)
        val lines = listOf(
            UpdateCheck.CARD_TITLE, UpdateCheck.TOGGLE_LABEL, UpdateCheck.CARD_HINT, UpdateCheck.CHECKING, UpdateCheck.CURRENT,
            UpdateCheck.UNREACHABLE, UpdateCheck.NO_BROWSER, UpdateCheck.promptTitle(m), UpdateCheck.promptBody("1.0.0-rc29", m),
            UpdateCheck.cardVersion("1.0.0-rc29"), UpdateCheck.cardFound(m),
        )
        for (l in lines) {
            assertTrue(l.isNotBlank())
            assertFalse(EM_DASH in l || EN_DASH in l, "no em dash: $l")
            val words = l.split(Regex("[^A-Za-z]+"))
            assertFalse("AI" in words || "ai" in words || "Claude" in words || "artificial" in words, "nothing about how the work is made: $l")
        }
        // The wording the check was specified with.
        assertEquals("KaizoCore 1.0.0-rc30 is out", UpdateCheck.promptTitle(m))
        assertEquals(
            "You have 1.0.0-rc29. The new build is 120 MB from willowcreek.group. Update downloads it, checks it and " +
                "asks Android to install it over this one. Your saves, runs and settings stay.",
            UpdateCheck.promptBody("1.0.0-rc29", m),
        )
        assertEquals("This is KaizoCore 1.0.0-rc29.", UpdateCheck.cardVersion("1.0.0-rc29"))
        assertEquals("1.0.0-rc30 is out (120 MB).", UpdateCheck.cardFound(m))
        assertEquals("Updates", UpdateCheck.CARD_TITLE)
        assertEquals("Check for a new build once a day", UpdateCheck.TOGGLE_LABEL)
        assertEquals("Checking...", UpdateCheck.CHECKING)
        assertEquals("You have the newest build.", UpdateCheck.CURRENT)
        assertEquals("Could not reach willowcreek.group. Try again later.", UpdateCheck.UNREACHABLE)
        assertEquals("No browser on this phone.", UpdateCheck.NO_BROWSER)
        assertEquals(
            "It asks willowcreek.group for one small file that names the newest build, and sends nothing else: " +
                "no account, no phone details, nothing about your games.",
            UpdateCheck.CARD_HINT,
        )
    }

    // -------------------------------------------------------------- wiring
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    @Test
    fun `the prompt and the card are wired in where they belong`() {
        val main = read("MainActivity.kt")
        val call = "UpdatePrompt(show = tab != Tab.PLAY && crashChecked && launchCrash == null)"
        assertEquals(1, Regex(Regex.escape(call)).findAll(main).count(), "one prompt, never over a game and never over the crash dialog")
        assertTrue("    $call\n    // The preset being edited, with the generation of the ROM it targets." in main, "in App(), before the editor state")
        val about = read("AboutScreen.kt")
        assertEquals(1, Regex(Regex.escape("UpdatesCard()")).findAll(about).count(), "one card")
        assertTrue("        UpdatesCard()\n        Spacer(Modifier.height(10.dp))\n        // BACKUP. One zip of saves" in about, "on INFO, before Backup")
    }

    @Test
    fun `the screens use the app's own parts and keep the network off the main thread`() {
        val ui = read("UpdateUi.kt")
        val core = read("UpdateCheck.kt")
        assertTrue("ShellSwitchRow(UpdateCheck.TOGGLE_LABEL, on, UpdateCheck.CARD_HINT)" in ui, "the shell's switch row, drawn for paper cards")
        assertFalse("GearToggle(" in ui, "not the tracker's toggle: it is drawn in the PC palette for a dark dialog")
        assertTrue("ShellDialog(UpdateCheck.promptTitle(m)" in ui && "Gen3Box(Modifier.fillMaxWidth())" in ui)
        // A bare Row( only: FlowRow( and ShellSwitchRow( are other names.
        assertFalse(Regex("(?<![A-Za-z])Row[(]").containsMatchIn(ui), "button rows are FlowRows: two buttons in a Row ran off the dialog at a large font")
        assertTrue(Regex(Regex.escape("FlowRow(")).findAll(ui).count() >= 3, "the prompt, the card, and the card's found row")
        // The launch check and the manual check both leave the main thread before they touch the network.
        assertTrue("withContext(Dispatchers.IO)" in core.substringAfter("suspend fun checkIfDue(").substringBefore("\n"), "launch check on IO")
        assertTrue("withContext(Dispatchers.IO) { UpdateCheck.checkNow(context) }" in ui, "Check now on IO")
        assertTrue("UpdateCheck.checkIfDue(context)" in ui)
        // A staged demo never writes a snooze, and its writes are off the main thread when they are made.
        assertTrue("if (!demo) {" in ui && "scope.launch(Dispatchers.IO) { UpdateCheck.snooze(" in ui)
        assertTrue("Demo.mode == \"update\"" in ui && "Demo.mode == \"update\"" in core)
        assertTrue("if (Demo.mode != null) return@runCatching null" in core, "no network check while a staged demo mode is set")
        // A link that nothing can open is said, not thrown.
        assertTrue("runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess" in ui)
        assertTrue("UpdateCheck.NO_BROWSER" in ui)
    }

    @Test
    fun `the sources carry no em dash and never say how the work is made`() {
        for (name in listOf("UpdateCheck.kt", "UpdateUi.kt")) {
            val text = read(name)
            assertFalse(EM_DASH in text || EN_DASH in text, "$name: dash")
            val words = text.split(Regex("[^A-Za-z]+"))
            assertFalse("AI" in words, "$name: AI")
            assertFalse(Regex("prep/").containsMatchIn(text), "$name: this state is not under the backup's folder")
        }
    }
}
