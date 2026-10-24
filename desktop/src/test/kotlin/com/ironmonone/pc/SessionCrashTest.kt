package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Junk the session's files the way a stuck Lua script or a bad click can. The loop must keep heartbeating and still
 * take a real RAM snapshot afterwards.
 */
class SessionCrashTest {
    private var seq = 700

    private fun parse(t: String) = t.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()

    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(15)
        }
    }

    private fun drop(dir: File, name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        assertTrue(tmp.renameTo(File(dir, name)) || File(dir, name).writeText(text).let { true })
    }

    private fun dropBin(dir: File, name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(dir, name))) File(dir, name).writeBytes(bytes)
    }

    private fun request(dir: File, cmd: String, vararg fields: Pair<String, String>): Map<String, String> {
        seq++
        val resp = File(dir, "jar.resp")
        resp.delete()
        File(dir, "lua.req.tmp").writeText((listOf("seq=$seq", "cmd=$cmd") + fields.map { "${it.first}=${it.second}" }).joinToString("\n", postfix = "\n"))
        assertTrue(File(dir, "lua.req.tmp").renameTo(File(dir, "lua.req")))
        var r: Map<String, String> = emptyMap()
        waitFor(40_000, "the answer to $cmd") { resp.isFile && runCatching { parse(resp.readText()) }.getOrNull()?.also { r = it }?.get("seq") == "$seq" }
        return r
    }

    @Test
    fun `garbage files and out-of-range clicks leave the session alive`() {
        val comfort = TestFiles.comfortBuild ?: return println("SessionCrashTest skipped: no hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        val root = TestFiles.tempDir("crash")
        val dir = File(root, "session")
        val s = Session(build, PcHns.settingsFile(PcHns.DEFAULT_PRESET), PcHns.options(PcHns.settingsFile(PcHns.DEFAULT_PRESET)),
            HnsEngine.Pool.NATDEX, File(root, "runs"), File(root, "runs"), dir, "A, B, Start", ahead = 1, keepLogs = 10)
        val t = Thread({ s.run() }, "kc-crash").apply { start() }
        try {
            waitFor(60_000, "heartbeat") { File(dir, "jar.txt").isFile }
            waitFor(8_000, "script seen") {
                File(dir, "lua.txt").writeText("battlestate=0\ngame=none\nalive=${System.currentTimeMillis() / 1000}\n")
                s.scriptRunning
            }
            val nr = request(dir, "newrun")
            assertEquals("1", nr["ok"], nr.toString())
            val gen = nr["gen"]!!
            val hello = request(dir, "hello", "hash" to nr.getValue("sha1"), "path" to nr.getValue("path"), "quiet" to "1")
            assertEquals("1", hello["ok"])

            drop(dir, "lua.req", "this is not a request\n")
            drop(dir, "input.req", "cmd=oclick\nx=not-a-number\ny=5\n")
            drop(dir, "input.req2", "cmd=oclick\nx=-99999\ny=99999\n")
            waitFor(2_000, "bad clicks eaten") { !File(dir, "input.req").exists() && !File(dir, "input.req2").exists() }

            drop(dir, "input.req", "cmd=click\nx=0\ny=0\n")
            drop(dir, "ability.req", "species=nope\nability=\n")
            dropBin(dir, "ram.bin", "no header".toByteArray())
            waitFor(2_000, "junk ram eaten") { !File(dir, "ram.bin").exists() }

            dropBin(dir, "ram.bin", "KCRAM $gen\n".toByteArray() + byteArrayOf(1, 2, 3))
            waitFor(2_000, "short ram eaten") { !File(dir, "ram.bin").exists() }

            dropBin(dir, "ram.bin", "KCRAM 999999\n".toByteArray() + ByteArray(100))
            waitFor(2_000, "wrong-gen ram eaten") { !File(dir, "ram.bin").exists() }

            drop(dir, "input.req", "cmd=logclose\n")
            drop(dir, "input.req2", "cmd=typecancel\n")
            drop(dir, "input.req", "cmd=wheel\ndelta=0\n")
            drop(dir, "input.req", "cmd=search\ntext=" + "A".repeat(4000) + "\n")
            drop(dir, "input.req", "cmd=note\nspecies=0\ntext=x\n")
            drop(dir, "input.req", "cmd=unknown-command\n")
            waitFor(3_000, "odd commands eaten") { !File(dir, "input.req").exists() }

            val good = "KCRAM $gen\n".toByteArray() + ByteArray(0x40000 + 0x8000)
            dropBin(dir, "ram.bin", good)
            waitFor(5_000, "good snapshot after junk") { File(dir, "tracker.txt").isFile }
            assertTrue("party:" in File(dir, "tracker.txt").readText())
            waitFor(2_000, "good ram taken") { !File(dir, "ram.bin").exists() }

            val alive = parse(File(dir, "jar.txt").readText())["alive"]?.toLongOrNull()
            assertTrue(alive != null && alive > 0, "heartbeat still written after junk")
            assertTrue(t.isAlive, "session thread still running")
        } finally {
            File(dir, "quit").writeText("1")
            t.join(8_000)
        }
    }
}
