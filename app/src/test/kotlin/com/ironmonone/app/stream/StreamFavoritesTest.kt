package com.ironmonone.app.stream

import com.ironmonone.app.Favorites
import com.ironmonone.core.RomKind
import org.junit.Assume
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The favorites as OBS sources (Blake, 2026-10-03, linking UTDZac's Favorites As Sources, which writes Favorite1.png to
 * Favorite9.png for OBS on the PC). Here /favorite/1.png to /favorite/9.png serve each favorite's picture as the
 * tracker card draws it, /favorite/1 to /favorite/9 are pages for OBS browser sources that follow them, the setup page
 * lists them and the scene has them.
 *
 * An empty box serves StreamFavorites.EMPTY, a 1 by 1 PNG with nothing in it, with a 200: OBS and a browser show
 * nothing, where a 404 is an error box in OBS and a broken picture in a page.
 */
class StreamFavoritesTest {
    private val pack = File("src/main/assets/gbasprites")

    /** A species' picture as the card draws it on a Nat. Dex or Game Boy game: the bundled pack, by the tracker's id. */
    private fun packed(species: Int): ByteArray? = File(pack, "$species.png").takeIf { it.isFile }?.readBytes()

    private class Got(val code: Int, val headers: Map<String, String>, val body: ByteArray)

    /** One raw GET, read to the end: the server answers once and closes. */
    private fun get(port: Int, path: String, headers: Map<String, String> = emptyMap()): Got {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5000
            val req = StringBuilder("GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n")
            headers.forEach { (k, v) -> req.append("$k: $v\r\n") }
            req.append("\r\n")
            s.getOutputStream().write(req.toString().toByteArray()); s.getOutputStream().flush()
            val all = s.getInputStream().readBytes()
            val end = (0..all.size - 4).first { all[it] == '\r'.code.toByte() && all[it + 1] == '\n'.code.toByte() && all[it + 2] == '\r'.code.toByte() && all[it + 3] == '\n'.code.toByte() }
            val head = String(all, 0, end, Charsets.ISO_8859_1).split("\r\n")
            val h = head.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            return Got(head[0].split(' ')[1].toInt(), h, all.copyOfRange(end + 4, all.size))
        }
    }

    private fun server(pictures: List<ByteArray?> = emptyList()) =
        StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "0" }, { null }, favorite = { n -> pictures.getOrNull(n - 1) })

    // ------------------------------------------------------------------ the pictures

    @Test
    fun `each address serves its own favorite's picture, as the card draws it, by its box`() {
        // A Nat. Dex FireRed, whose card draws the bundled pack by the tracker's id. Box 2 is empty; box 5 is no Pokemon.
        val kind = RomKind.FIRERED_NATDEX_121
        val icons = StreamFavorites.icons(listOf("Gengar", "", "mew", "Cosmoem", "Notamon", "", "", "", ""), kind)
        assertEquals(listOf("Gengar", null, "Mew", "Cosmoem", "Notamon", null, null, null, null), icons.map { it?.name })
        val ids = icons.map { it?.species }
        assertEquals(listOf(94, null, 151, Favorites.fromNational(790), null, null, null, null, null), ids)
        val pictures = StreamFavorites.pictures(icons, ::packed)
        val s = server(pictures)
        val port = s.start(0)
        try {
            val one = get(port, "/favorite/1.png?k=abcd")
            assertEquals(200, one.code)
            assertEquals("image/png", one.headers["content-type"])
            assertContentEquals(packed(94), one.body, "Gengar's own picture")
            assertContentEquals(packed(151), get(port, "/favorite/3.png?k=abcd").body, "Mew, in box 3 though box 2 is empty")
            assertContentEquals(packed(ids[3]!!), get(port, "/favorite/4.png?k=abcd").body, "Cosmoem")
            for (n in listOf(2, 5, 6, 7, 8, 9)) assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/$n.png?k=abcd").body, "box $n has no picture")
            // Three different pictures, each under its own number only.
            val served = (1..9).map { get(port, "/favorite/$it.png?k=abcd").body.toList() }
            assertEquals(3, served.filter { it != StreamFavorites.EMPTY.toList() }.toSet().size)
        } finally { s.stop() }
    }

    @Test
    fun `a box past the game's own count, or a picture that fails, serves the empty picture`() {
        // A GBA game keeps three favorites: boxes 4 to 9 have nothing.
        assertEquals(3, Favorites.slotCount(RomKind.FIRERED_U_V11))
        val icons = StreamFavorites.icons(listOf("Pikachu", "Squirtle", "Gengar"), RomKind.FIRERED_U_V11)
        assertEquals(3, icons.size)
        // A renderer that throws is a box with no picture, not a crash.
        assertEquals(listOf(null, null, null), StreamFavorites.pictures(icons) { error("no picture") })
        val s = server(StreamFavorites.pictures(icons, ::packed))
        val port = s.start(0)
        try {
            assertContentEquals(packed(25), get(port, "/favorite/1.png?k=abcd").body)
            for (n in 4..9) assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/$n.png?k=abcd").body, "box $n")
        } finally { s.stop() }
    }

    @Test
    fun `an empty box is a 1 by 1 PNG with nothing in it, served with a 200`() {
        val e = StreamFavorites.EMPTY
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), e.copyOf(8))
        // Read by the JDK's own decoder (javax.imageio, by reflection: the tests compile against android.jar), alpha and all.
        val read = Class.forName("javax.imageio.ImageIO").getMethod("read", java.io.InputStream::class.java)
        val img = assertNotNull(read.invoke(null, e.inputStream()), "a PNG the JDK can read")
        val int = Int::class.javaPrimitiveType
        assertEquals(1, img.javaClass.getMethod("getWidth").invoke(img))
        assertEquals(1, img.javaClass.getMethod("getHeight").invoke(img))
        val argb = img.javaClass.getMethod("getRGB", int, int).invoke(img, 0, 0) as Int
        assertEquals(0, argb ushr 24, "fully see-through")
        assertTrue(img.javaClass.getMethod("getColorModel").invoke(img).let { it.javaClass.getMethod("hasAlpha").invoke(it) as Boolean }, "with an alpha channel")
        val s = server()
        val port = s.start(0)
        try {
            val got = get(port, "/favorite/1.png?k=abcd")
            assertEquals(200, got.code, "never an error box in OBS")
            assertEquals("image/png", got.headers["content-type"])
            assertContentEquals(e, got.body)
        } finally { s.stop() }
    }

    @Test
    fun `the pictures come from the hub and change when the favorites do, with a 304 while they do not`() {
        val dir = Files.createTempDirectory("stream-favorites").toFile()
        val keep = StreamHub.service
        StreamHub.feed = FakeGameFeed()
        StreamHub.service = { }
        val port = ServerSocket(0).use { it.localPort }
        // The hub's source (StreamFavoritesSource), over a game and boxes the test changes as the favorites file would.
        val game = com.ironmonone.app.GameSession(File(dir, "run.gba"), com.ironmonone.core.Platform.GBA, RomKind.FIRERED_NATDEX_121, "Run", "run", isRun = true)
        var boxes = listOf("Gengar", "", "Mew")
        StreamHub.favorites = StreamFavoritesSource({ game }, { boxes }) { _, sp -> packed(sp) }
        try {
            assertNotNull(StreamHub.start(dir, port))
            val k = StreamHub.token
            val first = get(port, "/favorite/1.png?k=$k")
            assertContentEquals(packed(94), first.body)
            assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/2.png?k=$k").body)
            assertContentEquals(packed(151), get(port, "/favorite/3.png?k=$k").body)
            // The page asks "still this one?" with the tag it has: no body while it is.
            val tag = assertNotNull(first.headers["etag"])
            assertEquals(StreamFavorites.etag(packed(94)!!), tag)
            val same = get(port, "/favorite/1.png?k=$k", mapOf("If-None-Match" to tag))
            assertEquals(304, same.code)
            assertEquals(0, same.body.size)
            // The favorites change: the same address, the new picture, a new tag.
            boxes = listOf("Mew")
            val changed = get(port, "/favorite/1.png?k=$k", mapOf("If-None-Match" to tag))
            assertEquals(200, changed.code)
            assertContentEquals(packed(151), changed.body)
            assertNotEquals(tag, changed.headers["etag"])
            assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/3.png?k=$k").body, "box 3 is empty now")
        } finally {
            StreamHub.stop()
            StreamHub.service = keep
            StreamHub.feed = null
            StreamHub.favorites = null
            dir.deleteRecursively()
        }
        assertContains(File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText(), "favorite = { n -> favorites?.pictures()?.getOrNull(n - 1) }")
    }

    @Test
    fun `there are nine favorites, behind the key, and no other address under favorite`() {
        assertEquals(9, StreamFavorites.SLOTS)
        assertEquals(Favorites.NAT_DEX_SLOTS, StreamFavorites.SLOTS, "the most favorites the app keeps for any game")
        val s = server()
        val port = s.start(0)
        try {
            for (path in listOf("/favorite/0.png", "/favorite/10.png", "/favorite/01.png", "/favorite/1.jpg", "/favorite/", "/favorite/x.png", "/favorite/1/2", "/favorites"))
                assertEquals(404, get(port, "$path?k=abcd").code, path)
            for (path in listOf("/favorite/1.png", "/favorite/1", "/favorite/9.png", "/favorite/9")) {
                assertEquals(403, get(port, path).code, "$path with no key")
                assertEquals(403, get(port, "$path?k=nope").code, "$path with a wrong key")
                assertEquals(200, get(port, "$path?k=abcd").code, path)
            }
        } finally { s.stop() }
        assertEquals(1 to true, StreamFavorites.route("/favorite/1.png"))
        assertEquals(9 to false, StreamFavorites.route("/favorite/9"))
    }

    // ------------------------------------------------------------------ the page for a browser source

    @Test
    fun `the page is see-through, scales with hard pixel edges and writes no words`() {
        val s = server()
        val port = s.start(0)
        try {
            val got = get(port, "/favorite/4?k=abcd")
            assertEquals(200, got.code)
            assertContains(got.headers.getValue("content-type"), "text/html")
            val html = String(got.body, Charsets.UTF_8)
            assertEquals(StreamFavorites.page(), html)
            assertContains(html, "background:transparent")
            assertContains(html, "object-fit:contain")
            assertContains(html, "image-rendering:pixelated")
            val words = html.replace(Regex("(?s)<script.*?</script>"), " ").replace(Regex("(?s)<style.*?</style>"), " ")
                .replace(Regex("(?s)<title>.*?</title>"), " ").replace(Regex("<[^>]+>"), "").trim()
            assertEquals("", words, "a page on a live stream shows no words")
        } finally { s.stop() }
    }

    private fun needNode() = Assume.assumeTrue("node is not on the PATH, so the page's own script is not run", PageRunner.available)

    private fun reply(etag: String, body: String) = mapOf("status" to 200L, "etag" to etag, "body" to body)

    @Test
    fun `the page asks for its own favorite's picture and shows it`() {
        needNode()
        val run = PageRunner.run("favorite", StreamFavorites.page(), "?k=a%20b", extra = mapOf("path" to "/favorite/4", "reply" to reply("\"1\"", "gengar")))
        val seen = run.initial
        assertEquals("/favorite/4.png?k=a%20b", seen.str("url"), "its number from its own address, the key passed on")
        assertEquals("no-cache", seen.str("cache"), "it asks the phone every time, and gets a 304 while nothing changed")
        assertTrue(seen.str("src").endsWith(":gengar"), seen.str("src"))
        assertEquals("visible", seen.str("visibility"))
    }

    @Test
    fun `the page asks again every two seconds and changes picture only when the phone's does`() {
        needNode()
        val run = PageRunner.run("favorite", StreamFavorites.page(), "?k=abcd", extra = mapOf("path" to "/favorite/2", "reply" to reply("\"1\"", "gengar")), steps = listOf(
            mapOf("advance" to 2000L),                                             // the same picture: kept as it is
            mapOf("reply" to reply("\"2\"", "mew"), "advance" to 2000L),           // the favorites changed
            mapOf("reply" to null, "advance" to 2000L),                            // the phone cannot be reached
            mapOf("reply" to reply("\"2\"", "mew"), "advance" to 2000L),           // back, and nothing changed meanwhile
        ))
        val (same, changed, offline, back) = run.steps
        assertEquals(1L, run.initial.long("made"))
        assertEquals(2L, same.long("fetches"))
        assertEquals(1L, same.long("made"), "an unchanged tag makes no new picture")
        assertEquals(run.initial.str("src"), same.str("src"))
        assertTrue(changed.str("src").endsWith(":mew"), changed.str("src"))
        assertEquals(1L, changed.long("revoked"), "the old picture is let go once the new one shows")
        assertEquals(changed.str("src"), offline.str("src"), "with the phone gone the last picture stays")
        assertEquals("visible", offline.str("visibility"))
        assertEquals(changed.str("src"), back.str("src"))
        assertEquals(2L, back.long("made"))
    }

    @Test
    fun `smooth=1 softens the picture, as on the game page`() {
        needNode()
        val run = PageRunner.run("favorite", StreamFavorites.page(), "?k=abcd&smooth=1", extra = mapOf("reply" to reply("\"1\"", "x")))
        assertEquals("auto", run.initial.str("rendering"))
        assertEquals("", PageRunner.run("favorite", StreamFavorites.page(), "?k=abcd", extra = mapOf("reply" to reply("\"1\"", "x"))).initial.str("rendering"))
    }

    // ------------------------------------------------------------------ where the streamer finds them

    @Test
    fun `the setup page lists every favorite's address, with what to do with it`() {
        val base = "http://192.168.1.50:8642"
        val page = StreamPages.setup(base, "abcd")
        for (n in 1..StreamFavorites.SLOTS) {
            assertContains(page, "<td>Favorite $n</td>")
            assertContains(page, "value=\"$base/favorite/$n?k=abcd\"")
        }
        assertFalse(page.contains("/favorite/10"))
        val text = page.replace(Regex("(?s)<script.*?</script>"), " ").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ")
        assertContains(text, "Favorites are the favorite Pokémon you set for the game you are playing, one picture each, as the tracker shows them, numbered as their boxes are.")
        assertContains(text, "Add each one you want as a Browser source.")
        assertContains(text, "The imported scene has all nine, hidden: click the eye beside one in Sources to show it, then drag it where you want it.")
        assertContains(text, "They change by themselves as soon as you change your favorites, and a box with no favorite shows nothing.")
        assertContains(text, "The picture alone, a see-through PNG for your own overlays, is at the same address with .png after the number, like /favorite/1.png.")
        // The table says each source's size, the size the scene gives it.
        assertContains(page, "<td>${ObsScene.FAVORITE_SIZE} x ${ObsScene.FAVORITE_SIZE}</td>")
        // The favorites sit in the links table, after the attempt counter, so the guide's Copy buttons work on them too.
        assertTrue(page.indexOf("<td>Attempt counter</td>") < page.indexOf("<td>Favorite 1</td>"))
        assertTrue(page.indexOf("<td>Favorite 9</td>") < page.indexOf("</table>", page.indexOf("<td>Favorite 1</td>")))
        // Served for real, with the address the PC used.
        val s = server()
        val port = s.start(0)
        try {
            assertContains(String(get(port, "/?k=abcd", mapOf("Host" to "192.168.1.50:8642")).body, Charsets.UTF_8), "value=\"http://192.168.1.50:8642/favorite/9?k=abcd\"")
        } finally { s.stop() }
    }

    @Test
    fun `the scene has every favorite, hidden, shut down while hidden, at this phone with its key`() {
        @Suppress("UNCHECKED_CAST")
        val scene = MiniJson(ObsScene.json("http://192.168.1.50:8642", "abcd")).parse() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val sources = scene["sources"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val items = ((sources.single { it["name"] == ObsScene.SCENE }["settings"] as Map<String, Any?>)["items"] as List<Map<String, Any?>>)
        for (n in 1..StreamFavorites.SLOTS) {
            val name = "KaizoCore favorite $n"
            @Suppress("UNCHECKED_CAST")
            val settings = sources.single { it["name"] == name }["settings"] as Map<String, Any?>
            assertEquals("http://192.168.1.50:8642/favorite/$n?k=abcd", settings["url"], name)
            assertEquals(128L, settings["width"]); assertEquals(128L, settings["height"])
            assertEquals(true, settings["shutdown"], "$name costs OBS nothing while hidden")
            assertEquals(true, settings["restart_when_active"], name)
            assertEquals(false, settings["reroute_audio"], name)
            assertEquals(false, items.single { it["name"] == name }["visible"], "$name is hidden until the streamer shows it")
        }
    }

    // ------------------------------------------------------------------ the pictures are PNGs

    @Test
    fun `the stream keeps the card's see-through parts`() {
        // How the app writes each picture it draws (StreamFavoritePictures); StreamFavoritesSourceTest holds the rest.
        val icons = File("src/main/kotlin/com/ironmonone/app/FavoriteIcons.kt").readText()
        assertTrue("compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)" in icons, "a PNG keeps the see-through parts")
    }
}
