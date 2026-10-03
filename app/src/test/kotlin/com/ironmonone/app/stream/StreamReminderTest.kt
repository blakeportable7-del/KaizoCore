package com.ironmonone.app.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stream reminder (UX audit P0-19, 2026-09-30): while the stream is on, KaizoCore in the background pauses the
 * game and freezes OBS, and one notification brings the player back. Android classes, so the wiring is read.
 */
class StreamReminderTest {
    private fun src(path: String) = File(path).readText().replace("\r\n", "\n")

    @Test
    fun `the notification goes up as KaizoCore leaves, only while streaming, and down as it comes back`() {
        val main = src("src/main/kotlin/com/ironmonone/app/MainActivity.kt")
        assertTrue("override fun onStop() {\n        super.onStop()\n        com.ironmonone.app.stream.StreamReminder.left(this)" in main)
        assertTrue("override fun onStart() {\n        super.onStart()\n        com.ironmonone.app.stream.StreamReminder.back(this)" in main)
        val reminder = src("src/main/kotlin/com/ironmonone/app/stream/StreamReminder.kt")
        assertTrue("if (!StreamHub.running || !allowed(context)) return" in reminder, "only while the stream is on, and never without the player's yes")
        assertTrue("getLaunchIntentForPackage(context.packageName)" in reminder, "a tap brings the task back as it was")
        assertTrue("PendingIntent.FLAG_IMMUTABLE" in reminder)
    }

    @Test
    fun `the phone asks once, as the stream is turned on, and the manifest says why`() {
        val hub = src("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt")
        assertTrue(hub.indexOf("server = s") in 0 until hub.indexOf("onStarted?.invoke()"), "asked once the stream is up")
        val main = src("src/main/kotlin/com/ironmonone/app/MainActivity.kt")
        assertTrue("if (com.ironmonone.app.stream.StreamReminder.shouldAsk(this)) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 71)" in main)
        assertTrue("com.ironmonone.app.stream.StreamHub.onStarted = null" in main, "the activity is let go")
        assertTrue("android.permission.POST_NOTIFICATIONS" in src("src/main/AndroidManifest.xml"))
        assertTrue(File("src/main/res/drawable/ic_stat_stream.xml").isFile)
    }

    @Test
    fun `the words are plain and the guide says it`() {
        for (s in listOf(StreamReminder.TITLE, StreamReminder.TEXT, StreamReminder.TEXT_ON, StreamReminder.CHANNEL_NAME))
            assertFalse('\u2014' in s || '\u2013' in s || Regex("\\bAI\\b").containsMatchIn(s), s)
        assertTrue("While the stream is on, a notification takes you straight back." in src("src/main/kotlin/com/ironmonone/app/stream/StreamPages.kt"))
    }
}
