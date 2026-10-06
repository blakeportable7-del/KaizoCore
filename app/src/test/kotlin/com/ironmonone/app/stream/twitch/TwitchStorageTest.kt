package com.ironmonone.app.stream.twitch

import com.ironmonone.app.Backup
import com.ironmonone.app.CrashReport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where the sign-in lives and everywhere it must not turn up: backups, Cloud sync, crash reports, strings, the copy. */
class TwitchStorageTest {

    @Test
    fun `the sign-in file is left out of the backup and Cloud sync, the command switches are kept`() {
        assertFalse(Backup.admits(KeystoreTokenStore.FILE), "the sealed tokens are never in a backup")
        assertTrue(Backup.admits(TwitchChat.SETTINGS_FILE), "which commands are on is the player's, and comes back with a restore")
        val dir = java.nio.file.Files.createTempDirectory("twitch").toFile()
        try {
            File(dir, KeystoreTokenStore.FILE).writeBytes(ByteArray(40) { 7 })
            File(dir, TwitchChat.SETTINGS_FILE).apply { parentFile.mkdirs(); writeText("answering=1\noff=\n") }
            val taken = Backup.collect(dir)
            assertTrue(TwitchChat.SETTINGS_FILE in taken)
            assertTrue(taken.none { "twitch-session" in it }, "Backup.collect, which Cloud sync's fingerprint reads too: $taken")
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `tokens round-trip through their saved form, and their string names only the account`() {
        val t = TwitchTokens("access-secret-0123456789", "refresh-secret-0123456789", 1234L, "141981764", "streamer", "Streamer",
            listOf("user:read:chat", "user:write:chat"))
        val back = TwitchTokens.decode(t.encode())!!
        assertEquals(listOf(t.access, t.refresh, t.userId, t.login, t.name), listOf(back.access, back.refresh, back.userId, back.login, back.name))
        assertEquals(1234L, back.expiresAt); assertEquals(t.scopes, back.scopes)
        assertEquals("TwitchTokens(login=streamer)", "$t")
        assertFalse("secret" in "${TwitchAuth.DeviceCode("device-secret", "ABCD", "https://x", 5, 0)}")
        assertFalse("secret" in "${HttpReply(200, "{\"access_token\":\"secret\"}")}")
        assertEquals(null, TwitchTokens.decode("not json"))
    }

    @Test
    fun `a crash report drops anything token-shaped`() {
        val text = "java.io.IOException: HTTP 401 Authorization: Bearer abcdefghij0123456789xyzw\n" +
            "body {\"access_token\":\"abcdefghij0123456789xyzw\",\"refresh_token\": \"zyxwvutsrq9876543210abcdefghijklmnop\"}\n" +
            "OAuth 0123456789abcdefghijklmn\n\tat com.ironmonone.app.stream.twitch.TwitchLink.send(TwitchLink.kt:300)"
        val out = CrashReport.scrub(text)
        for (s in listOf("abcdefghij0123456789xyzw", "zyxwvutsrq9876543210abcdefghijklmnop", "0123456789abcdefghijklmn"))
            assertFalse(s in out, "'$s' survived: $out")
        assertTrue("at com.ironmonone.app.stream.twitch.TwitchLink.send(TwitchLink.kt:300)" in out, "stack frames come through")
    }

    @Test
    fun `no Twitch source writes a log line`() {
        val dir = File("src/main/kotlin/com/ironmonone/app/stream/twitch")
        val files = dir.listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(files.size >= 6, "the scan found ${files.size} files")
        for (f in files) {
            val src = f.readText()
            for (bad in listOf("android.util.Log", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "println(", "printStackTrace"))
                assertFalse(bad in src, "${f.name} has $bad")
        }
    }

    @Test
    fun `the Stream page's words follow the copy rules`() {
        val all = com.ironmonone.app.TwitchCopy.ALL + ChatCommand.entries.map { it.help }
        for (line in all) {
            assertFalse('—' in line || '–' in line, "no em or en dashes: $line")
            for (word in listOf(" AI", "A.I.", "money", "donat", "subscribe", "\$", "pay", "support"))
                assertFalse(word.lowercase() in line.lowercase(), "'$word' in: $line")
        }
    }
}
