package com.ironmonone.app.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The setup guide as a streamer reads it (UX audit P0-16 to P0-19 and the Streaming items under P1, 2026-09-30).
 *
 * The audit's streamer opened OBS first, downloaded a file whose name nothing told them, imported it a second time when
 * the phone's address changed (which made a second scene collection and dropped their layout), let the mic hear the
 * phone's speaker, and lost the picture to a home swipe with no sign anywhere. Each test here pins one of the sentences
 * that answers that, in the words the audit's fix list gave. The OBS words (menu names, buttons, checkboxes) are OBS's
 * own, read from its locale files: Basic.MainMenu.SceneCollection.Import "Import Scene Collection", Importer.SelectFile
 * "Browse..." and Import, obs-browser's "Control audio via OBS", "Use custom frame rate" and "Refresh cache of current
 * page", and Basic.AdvAudio's "Advanced Audio Properties", "Audio Monitoring" and "Monitor and Output" (OBS 28 to
 * 32.1; 32.2 renamed the last to "Monitoring Enabled", which the guide says too).
 */
class StreamGuideTest {

    private val base = "http://192.168.1.50:8642"
    private val guide = StreamPages.setup(base, "abcd")

    /** A page as it is read: scripts and tags gone, entities decoded, every run of white space one space. */
    private fun read(html: String): String = html
        .replace(Regex("(?s)<script.*?</script>"), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")

    private val text = read(guide)

    /** Where [sentence] is in the guide as read, from [from] on. Fails, saying what is missing, when it is not there. */
    private fun at(sentence: String, from: Int = 0): Int {
        val i = text.indexOf(sentence, from)
        assertTrue(i >= 0, "the guide should say (after position $from): $sentence")
        return i
    }

    // ------------------------------------------------------------------ the three steps

    @Test
    fun `step 1 names the file that downloads and offers a test of the picture`() {
        val step1 = at("1 Download the OBS scene")
        at("Saves as KaizoCore-stream.json in your Downloads folder.", step1)
        at("KaizoCore-stream-top-screen.json", step1)
        val test = at("Open the picture in this tab", step1)
        at("You should see your game. Click the page once to turn the sound on. If you do, the phone side works and any problem left is in OBS.", test)
        assertTrue(test < at("2 Add it to OBS"), "the test is part of step 1")
        // The link opens the game page itself, with the key and the small box of numbers, at the address the guide was opened at.
        assertContains(guide, "href=\"$base/game?k=abcd&amp;debug=1\"")
    }

    @Test
    fun `the file names in the guide are the names the downloads have`() {
        val named = Regex("download=\"([^\"]+)\"").findAll(guide).map { it.groupValues[1] }.toSet()
        assertEquals(setOf(ObsScene.FILE, ObsScene.FILE_TOP), named, "what the page says it saves is what the server sends")
        assertEquals("KaizoCore-stream.json", ObsScene.FILE)
        assertEquals("KaizoCore-stream-top-screen.json", ObsScene.FILE_TOP)
        assertContains(guide, "href=\"/obs-scene.json?k=abcd\"")
        assertContains(guide, "href=\"/obs-scene.json?k=abcd&top=1\"")
    }

    @Test
    fun `step 2 has two ways in, the second in OBS's own words`() {
        val step2 = at("2 Add it to OBS")
        val already = at(
            "Already use OBS scenes? Do not import. In your gameplay scene choose Sources, +, Browser, and add the three addresses " +
                "from the table below, one source each. On the game source, check Control audio via OBS and Use custom frame rate, 60.", step2)
        val new = at("New to OBS? Download the scene in step 1, then:", already)
        val menu = at("Scene Collection menu and choose Import Scene Collection.", new)
        val browse = at("Click Browse..., pick the file from your Downloads folder, then click Import.", menu)
        val pick = at("Open the Scene Collection menu again and choose KaizoCore stream.", browse)
        val webcam = at("Add your own webcam and mic to it.", pick)
        assertTrue(webcam < at("3 What you should see"), "both ways are step 2")
        assertTrue(at("The links, one at a time") > already, "the table the first way sends you to is below it")
        assertFalse(text.contains("Import it in OBS"), "the one way in the guide used to have is gone")
    }

    @Test
    fun `step 3 says what you should see and Done is gone`() {
        assertContains(guide, "What you should see</h2>")
        assertFalse(guide.contains("Done</h2>"))
        at("The game on the left, the tracker box on the right, and KaizoCore game moving in the Audio Mixer. " +
            "Viewers see only these: never the phone's buttons, menus or camera.")
    }

    @Test
    fun `the sections come in the order a streamer needs them`() {
        val order = listOf(
            "1 Download the OBS scene", "2 Add it to OBS", "3 What you should see", "While you stream", "Sound",
            "If nothing shows", "The links, one at a time",
        ).map { at(it) }
        assertEquals(order.sorted(), order, "steps, then advice, then what to do when nothing shows, then the addresses to look up")
    }

    // ------------------------------------------------------------------ while streaming

    @Test
    fun `the guide says to keep KaizoCore in front, to plug the phone in and to turn on Do Not Disturb`() {
        val head = at("While you stream")
        at("Keep KaizoCore in front. A home swipe, the power button, a call or a permission prompt pauses the game, and the picture in OBS freezes.", head)
        at("Plug the phone in: streaming uses power and warms the phone.", head)
        at("Turn on Do Not Disturb.", head)
    }

    @Test
    fun `the sound advice is about the mic hearing the phone and not about the phone's mute`() {
        val head = at("Sound")
        at("Your mic can hear the phone's speaker, and then viewers hear the game twice, the second time late. " +
            "Wear headphones on the phone, or mute the phone. The phone's mute does not touch the stream's sound.", head)
        val monitor = at("To hear the game in your PC headphones: Advanced Audio Properties, Audio Monitoring, Monitor and Output for KaizoCore game. " +
            "It will run a little behind the phone.", head)
        at("OBS 32.2 and newer call that choice Monitoring Enabled.", monitor)
        at("Fast forward: the phone goes quiet, but the stream keeps sending sound, sped up. Mute KaizoCore game in OBS, or stay at 1x on air.", head)
        assertFalse(text.contains("mute the phone if you do not want to hear the game twice"), "the old reason to mute is gone")
    }

    @Test
    fun `the guide says how to place the boxes before a game runs, and the pages can do it`() {
        at("Placing the boxes before a game runs. Add &demo=battle to the tracker address and &demo=1 to the attempt address. Take them off before you go live.")
        // Said only because it is true: both pages read the switch.
        assertContains(File("src/main/assets/stream/tracker.html").readText(), "q.get('demo')")
        assertContains(StreamPages.attempts(), "q.get('demo')")
    }

    // ------------------------------------------------------------------ when nothing shows

    @Test
    fun `if nothing shows starts with the error box, keeps the router advice and names debug`() {
        val head = at("If nothing shows")
        val error = at(
            "An error box in OBS means the phone was not streaming when OBS started. Turn Stream on, then switch to another scene and back. " +
                "Still an error? Right-click the source, choose Properties, press Refresh cache of current page.", head)
        assertTrue(error < at("Same Wi-Fi.", head), "the error box is the first thing listed")
        val changed = at(
            "Address changed? Open this page again at the new address. In OBS, right-click each KaizoCore source, choose Properties and paste " +
                "its new address from the table below: game, tracker, attempts, favorites. You do not need to import again.", head)
        at("give the phone a fixed address in your router's settings (often called an address reservation)", changed)
        at("Add &debug=1 to the game address.", head)
        assertTrue(at("The links, one at a time") > changed, "the table it sends you to is below it")
        // The cure that made a second scene collection and dropped every move the streamer had made.
        assertFalse(text.contains("download the scene again", ignoreCase = true))
        assertFalse(text.contains("import it again", ignoreCase = true))
    }

    @Test
    fun `the guide says when the attempt counter shows and when it stays blank`() {
        // A Nuzlocke counts no attempt, a randomized one included (788fb97f): the page blanks every one (rc32 audit P2 #109).
        at("The attempt counter shows in a Kaizo IronMON run. " +
            "In a Nuzlocke, a ROM hack or any other game it stays blank: there is no attempt number.")
        assertFalse(text.contains("appears in Kaizo IronMON and Nuzlocke runs"), "the old sentence promised it in every Nuzlocke")
        assertFalse(text.contains("Nuzlocke on a randomized game"), "nor in a randomized one")
    }

    @Test
    fun `the wiki says the attempt counter shows in a Kaizo IronMON run only`() {
        val wiki = File("../docs/wiki/Streaming-to-OBS.md").readText()
        val line = wiki.lines().single { it.startsWith("- **Attempts**") }
        assertFalse("randomized Nuzlocke" in line, line)
        assertTrue("during a Kaizo IronMON run, and blank otherwise. A Nuzlocke counts no attempt." in line, line)
    }

    @Test
    fun `the table gives the address the scene uses, with its size and the boxes to tick`() {
        val urls = ObsScene.urls(base, "abcd")
        assertContains(guide, "value=\"" + urls.getValue(ObsScene.GAME).replace("&", "&amp;") + "\"")
        assertContains(guide, "game?k=abcd&amp;int=1")
        assertContains(text, "1500 x 1080. Check Control audio via OBS and Use custom frame rate, 60.")
        assertContains(text, "The game address ends in &int=1, which keeps every pixel the same size.")
        assertContains(text, "&smooth=1 softens the picture instead of keeping hard pixel edges")
        assertContains(text, "&top=1 shows only the top DS screen")
    }

    // ------------------------------------------------------------------ opened on the phone itself

    @Test
    fun `opened on the phone itself the guide says so and leaves out the downloads`() {
        for (host in listOf("localhost:8642", "LocalHost:8642", "127.0.0.1:8642", "127.0.0.1", "127.1.2.3:8642", "[::1]:8642", "0.0.0.0:8642")) {
            val page = StreamPages.setup("http://$host", "abcd")
            assertContains(read(page), "Open this page on your PC, not on the phone.", message = host)
            assertFalse(page.contains("download="), "no download is offered at $host")
            assertFalse(page.contains("/obs-scene.json"), "no scene file is linked at $host")
            assertFalse(page.contains("class=\"btn\""), "no download button at $host")
        }
    }

    @Test
    fun `opened from the PC the guide offers the downloads and says nothing about the phone`() {
        for (host in listOf("192.168.1.50:8642", "pixel-7.local:8642", "localhost.example.com:8642", "127.example.com:8642", "10.0.0.7", "[fe80::1]:8642", "<phone-ip>:8642")) {
            val page = StreamPages.setup("http://$host", "abcd")
            assertFalse(read(page).contains("Open this page on your PC"), host)
            assertContains(page, "download=\"${ObsScene.FILE}\"", message = host)
            assertContains(page, "download=\"${ObsScene.FILE_TOP}\"", message = host)
        }
        assertFalse(StreamPages.onPhone("http://localhost.example.com"), "a name that starts with localhost is somebody's site")
        assertFalse(StreamPages.onPhone("http://127.0.0.1.example.com:8642"))
    }

    // ------------------------------------------------------------------ the house rules

    @Test
    fun `no page has an em dash, an en dash, an emoji or a word about AI`() {
        val pages = mapOf(
            "guide" to guide,
            "guide opened on the phone" to StreamPages.setup("http://127.0.0.1:8642", "abcd"),
            "game" to StreamPages.game(),
            "attempts" to StreamPages.attempts(),
            "tracker" to File("src/main/assets/stream/tracker.html").readText(),
            "scene" to ObsScene.json(base, "abcd"),
            "favorite" to StreamFavorites.page(),
        )
        val emoji = Regex("[\\u2600-\\u27BF\\uFE0F]|[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]")
        for ((name, body) in pages) {
            assertFalse(body.contains(0x2014.toChar()) || body.contains(0x2013.toChar()), "$name has a dash")
            assertFalse(emoji.containsMatchIn(body), "$name has an emoji")
            assertFalse(Regex("\\bAI\\b|artificial intelligence|machine learning", RegexOption.IGNORE_CASE).containsMatchIn(body), "$name mentions AI")
        }
    }

    @Test
    fun `no file of the stream kit, its comments and its tests included, has an em dash or an en dash`() {
        val dirs = listOf(
            File("src/main/kotlin/com/ironmonone/app/stream"), File("src/main/assets/stream"), File("src/test/kotlin/com/ironmonone/app/stream"),
        )
        val files = dirs.flatMap { d -> existing(d).listFiles().orEmpty().filter { it.isFile } }
        assertTrue(files.size >= 20, "the stream kit's files were found: ${files.size}")
        for (f in files) {
            val t = f.readText()
            assertFalse(t.contains(0x2014.toChar()), "${f.name} has an em dash")
            assertFalse(t.contains(0x2013.toChar()), "${f.name} has an en dash")
        }
    }

    private fun existing(d: File): File {
        assertTrue(d.isDirectory, "${d.path} should exist (the working directory is app/)")
        return d
    }
}
