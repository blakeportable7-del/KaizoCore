package com.ironmonone.app.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Things the JVM cannot run but can read: that the game's picture and sound are
 * offered for every game, and that the native taps, the JNI names and the Kotlin
 * that calls them agree. A mismatch between the last three is an UnsatisfiedLinkError
 * on a phone, never on a JVM, so they are pinned here.
 */
class StreamWiringTest {

    private val stream = File("src/main/kotlin/com/ironmonone/app/stream")
    private val cpp = File("../libretrodroid/src/main/cpp")
    private val java = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid")

    private fun read(f: File): String {
        assertTrue(f.isFile, "${f.path} should exist (the working directory is app/)")
        return f.readText().replace("\r\n", "\n")
    }

    /** Source without its comments, so a rule about code is not tripped by a sentence about it. */
    private fun code(f: File): String = read(f)
        .replace(Regex("(?s)/\\*.*?\\*/"), "")
        .replace(Regex("(?m)(^|\\s)//.*$"), "$1")

    // ------------------------------------------------------------------ every game

    @Test
    fun `nothing in the game's path asks whether it is a run, a verified kind or tracked`() {
        val path = listOf("GameStream.kt", "GameSocket.kt", "NativeGameFeed.kt", "WebSocket.kt", "PngEncoder.kt", "ObsScene.kt", "StreamPages.kt", "StreamServer.kt")
        val gate = Regex("\\b(isRun|RomKind|GameSession|tracked|kind)\\b")
        for (name in path) {
            val hits = gate.findAll(code(File(stream, name))).map { it.value }.toSet()
            assertTrue(hits.isEmpty(), "$name must not gate the game's picture or sound on $hits: it works for every game the Play screen runs")
        }
    }

    @Test
    fun `the STREAM button is in the menu for every game`() {
        val play = read(File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt")).lines()
        val at = play.indexOfFirst { it.contains("\"STREAM ON\"") }
        assertTrue(at > 0, "the STREAM button should be in PlayScreen")
        assertFalse(play[at].contains("session"), "the button's own line is not gated")
        // The last two lines of code before it must not open a condition on the session.
        val before = play.subList(0, at).filter { it.isNotBlank() && !it.trim().startsWith("//") }.takeLast(2)
        for (l in before) {
            assertFalse(Regex("\\bif\\b.*\\b(isRun|tracked|kind|session)\\b").containsMatchIn(l), "gated by: $l")
        }
    }

    @Test
    fun `the snapshot is published for every session, not only a run`() {
        val feed = read(File(stream, "StreamFeed.kt"))
        val at = feed.indexOf("StreamHub.publish(json")
        assertTrue(at > 0)
        // The effect that publishes it is the one above, keyed on the stream, the state, the session and the phone's battle views.
        val effect = feed.lastIndexOf("LaunchedEffect(", at)
        assertContains(feed.substring(effect, effect + 90), "on, gba, nds, marksVersion, session.id")
        val header = feed.substring(effect, at)
        assertFalse(Regex("\\bif\\s*\\(\\s*session\\.(isRun|tracked)").containsMatchIn(header.substringBefore("val shown")), "the publishing effect is not gated on a run")
        assertContains(header, ", session.isRun, session.kind", message = "the run flag is passed outright")
        val play = read(File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt"))
        assertContains(play, "com.ironmonone.app.stream.StreamFeed(streamOn, session, platform, runNow, statMarks, marksVersion, trackerState, ndsState, trackerRef, ndsTrackerRef, gameOverLatch)")
    }

    /**
     * rc32 audit P3 #50: the snapshot was built on every tracker change, with file reads on the main thread, for a
     * server that was not running. Turning the stream on is a change of `on`, so the state goes out at once.
     */
    @Test
    fun `nothing is built while the stream is off`() {
        val feed = read(File(stream, "StreamFeed.kt"))
        // The phone's battle views joined the keys in rc34, so a swap goes out at once (StreamDoublesTest).
        val effect = feed.substring(feed.indexOf("LaunchedEffect(on, gba, nds, marksVersion, session.id, com.ironmonone.app.gbaView.view, com.ironmonone.app.dsView.key) {"), feed.indexOf("StreamHub.publish(json"))
        assertTrue(effect.indexOf("if (!on) return@LaunchedEffect") in 0 until effect.indexOf("StreamSnapshot.build("))
        val play = read(File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt"))
        assertFalse("StreamHub.publish(" in play, "Play publishes nothing of its own")
    }

    /**
     * rc32 audit P3 #51: every species was read out of the game at each tracker start, stream on or off, for a page
     * that serves them only after the run ends. Now only then, and /dex.json refuses until they are built.
     */
    @Test
    fun `the randomized dex is built only once it can be served`() {
        val feed = read(File(stream, "StreamFeed.kt"))
        val effect = feed.substring(feed.indexOf("LaunchedEffect(on, ended, ref, nref) {"))
        val built = effect.indexOf("StreamSnapshot.dex(")
        assertTrue(effect.indexOf("if (!on || ended == null) return@LaunchedEffect") in 0 until built)
        assertTrue(effect.indexOf("StreamHub.dex = null") in 0 until built, "a stale dex is dropped, and null refuses")
        assertContains(feed, "val ended = if (latch.applies) latch.outcome else null")
        assertContains(read(File(stream, "StreamHub.kt")), "@Volatile var dex: String? = null")
    }

    // ------------------------------------------------------------------ the native side

    @Test
    fun `the taps sit before the renderer and before the phone's mute`() {
        val src = read(File(cpp, "libretrodroid.cpp"))
        fun body(signature: String): String {
            val start = src.indexOf(signature)
            assertTrue(start >= 0, "$signature should exist")
            return src.substring(start, src.indexOf("\n}\n", start))
        }
        val video = body("void LibretroDroid::handleVideoRefresh(")
        assertTrue(video.indexOf("StreamTap::enabled()") in 0 until video.indexOf("video->onNewFrame("), "the picture is copied before the renderer can rewrite it")
        assertContains(video, "isUseHwAcceleration()", message = "a hardware-rendered core hands over no pixels")
        assertTrue(video.indexOf("StreamTap::enabled()") < video.indexOf("StreamTap::getInstance()"), "one relaxed load first, the instance only when on")

        val audio = body("size_t LibretroDroid::handleAudioCallback(")
        assertTrue(audio.indexOf("StreamTap::enabled()") in 0 until audio.indexOf("audio->write("), "the stream keeps its sound when the phone is muted")
        assertTrue(audio.indexOf("StreamTap::enabled()") < audio.indexOf("StreamTap::getInstance()"))
    }

    @Test
    fun `the JNI functions, the Java declarations and the Kotlin calls use the same three names`() {
        val jni = read(File(cpp, "libretrodroidjni.cpp"))
        val decl = read(File(java, "LibretroDroid.java"))
        val kotlinCalls = read(File(stream, "NativeGameFeed.kt"))
        for (name in listOf("setStreamCapture", "streamFrame", "streamAudio")) {
            assertContains(jni, "Java_com_swordfish_libretrodroid_LibretroDroid_$name(", message = "JNI function $name")
            assertTrue(Regex("public static native \\w+ $name\\(").containsMatchIn(decl), "Java declaration $name")
            assertContains(kotlinCalls, "LibretroDroid.$name(", message = "the Kotlin feed calls $name")
        }
    }

    @Test
    fun `the JNI parameter lists match the Java ones`() {
        val jni = read(File(cpp, "libretrodroidjni.cpp"))
        assertTrue(Regex("jlong JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_streamFrame\\(\\s*JNIEnv\\* env,\\s*jclass obj,\\s*jobject dst,\\s*jlong afterCounter,\\s*jintArray size\\s*\\)").containsMatchIn(jni))
        assertTrue(Regex("jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_streamAudio\\(\\s*JNIEnv\\* env,\\s*jclass obj,\\s*jobject dst,\\s*jintArray info\\s*\\)").containsMatchIn(jni))
        assertTrue(Regex("void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setStreamCapture\\(\\s*JNIEnv\\* env,\\s*jclass obj,\\s*jboolean on\\s*\\)").containsMatchIn(jni))
        val decl = read(File(java, "LibretroDroid.java"))
        assertContains(decl, "long streamFrame(java.nio.ByteBuffer dst, long afterCounter, int[] size)")
        assertContains(decl, "int streamAudio(java.nio.ByteBuffer dst, int[] info)")
        assertContains(decl, "void setStreamCapture(boolean on)")
    }

    @Test
    fun `the tap is compiled into the library`() {
        assertContains(read(File(cpp, "CMakeLists.txt")), "streamtap.cpp")
        assertTrue(File(cpp, "streamtap.h").isFile)
    }

    @Test
    fun `the tap's cost when nobody watches is one relaxed load`() {
        val h = read(File(cpp, "streamtap.h"))
        assertContains(h, "static bool enabled() { return captureOn.load(std::memory_order_relaxed); }")
        // The rate and the flush never build the tap (and its sound ring) for a player who does not stream.
        assertContains(h, "static void setAudioRate(")
        assertContains(h, "static void flushAudio()")
        val glue = read(File(cpp, "libretrodroid.cpp"))
        val onLoad = glue.substring(glue.indexOf("void LibretroDroid::afterGameLoad()"), glue.indexOf("float LibretroDroid::findDefaultAspectRatio"))
        assertFalse(onLoad.contains("StreamTap::getInstance()"), "loading a game must not build the tap")
    }

    @Test
    fun `the wire format the page reads is the one the server writes`() {
        val page = StreamPages.game()
        // The page reads tag 1 as a picture and 2 as sound, sound rate at byte 4, samples from byte 8.
        assertContains(page, "tag === 1")
        assertContains(page, "tag === 2")
        assertContains(page, "getUint32(4, true)")
        assertContains(page, "new Int16Array(buf, 8,")
        assertEquals(1, GameWire.VIDEO); assertEquals(2, GameWire.AUDIO); assertEquals(8, GameWire.HEADER)
        // And the socket path is the one the server serves.
        assertContains(page, "/game.ws?k=")
        assertNotNull(page.lines().firstOrNull { it.contains("WebSocket(") })
    }

    @Test
    fun `a Game Boy run's randomizer data is none, never the last GBA or DS game's`() {
        // rc33 audit P1 #34: the effect returned early when neither tracker was up, so /dex.json kept the last game's.
        val feed = File(stream, "StreamFeed.kt").readText()
        kotlin.test.assertTrue("if (ref == null && nref == null) { StreamHub.dex = \"[]\"; return@LaunchedEffect }" in feed)
    }

    @Test
    fun `the stream shows an evolution's words, not the table's key`() {
        // "L.CORD" on the card and "Linking Cord" in the dex, not LINKING_CORD (rc33 audit P1 #67 made Nat. Dex keys common).
        val src = File("src/main/kotlin/com/ironmonone/app/stream/StreamSnapshot.kt").readText()
        kotlin.test.assertTrue("\"evolution\" to com.ironmonone.tracker.EvoText.abbreviation(tracker?.evolution(m.species))," in src)
        kotlin.test.assertTrue("EvoText.detailed(tracker.evolution(sp), tracker.friendshipRequired()).joinToString(\" / \")" in src)
    }
}
