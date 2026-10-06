package com.ironmonone.app.stream

import com.ironmonone.app.Favorites

import com.ironmonone.app.PrepStore
import com.ironmonone.app.RomFileReader
import com.ironmonone.app.StreamFavoritePictures
import com.ironmonone.app.StreamFavoritePictures.From
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.SpriteDecoder
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The stream's favorite pictures follow the saved favorites at once (Blake, 2026-10-03, before rc34 shipped: rc34's
 * known issue was that a favorite edited on the Kaizo IronMON screen reached OBS only once Play opened again, because
 * Play made the pictures as it opened). The server now asks StreamFavoritesSource on every request, and it reads the
 * game Play opens and that game's saved favorites, so an edit reaches the next request with no Play screen at all.
 */
class StreamFavoritesSourceTest {
    private val pack = File("src/main/assets/gbasprites")
    private fun packed(species: Int): ByteArray? = File(pack, "$species.png").takeIf { it.isFile }?.readBytes()

    private class Got(val code: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun get(port: Int, path: String): Got {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".toByteArray()); s.getOutputStream().flush()
            val all = s.getInputStream().readBytes()
            val end = (0..all.size - 4).first { all[it] == '\r'.code.toByte() && all[it + 1] == '\n'.code.toByte() && all[it + 2] == '\r'.code.toByte() && all[it + 3] == '\n'.code.toByte() }
            val head = String(all, 0, end, Charsets.ISO_8859_1).split("\r\n")
            val h = head.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            return Got(head[0].split(' ')[1].toInt(), h, all.copyOfRange(end + 4, all.size))
        }
    }

    /** Runs [body] with the hub up on a free port, reading [source]; everything put back after. */
    private fun hub(source: StreamFavoritesSource?, body: (port: Int, key: String) -> Unit) {
        val dir = Files.createTempDirectory("stream-favorites-live").toFile()
        val keep = StreamHub.service
        StreamHub.feed = FakeGameFeed()
        StreamHub.service = { }
        StreamHub.favorites = source
        val port = ServerSocket(0).use { it.localPort }
        try {
            assertNotNull(StreamHub.start(dir, port))
            body(port, StreamHub.token)
        } finally {
            StreamHub.stop()
            StreamHub.service = keep
            StreamHub.feed = null
            StreamHub.favorites = null
            dir.deleteRecursively()
        }
    }

    /** A store whose game in Play is a [kind] run, with [favorites] saved for it as the Kaizo IronMON screen saves them. */
    private fun store(kind: RomKind, favorites: List<String>): PrepStore {
        val store = PrepStore(Files.createTempDirectory("favstore").toFile())
        store.saveLastRun(kind.id, "FRLG NatDex v1.2 Kaizo.rnqs")
        Favorites.save(store, kind.id, favorites)
        return store
    }

    @Test
    fun `a favorite edited on the Kaizo IronMON screen reaches OBS at once, with no Play screen`() {
        val kind = RomKind.FIRERED_NATDEX_121
        val store = store(kind, listOf("Gengar", "", "Mew"))
        assertEquals(kind, store.session().kind, "the game Play opens")
        hub(StreamFavoritesSource.of(store) { _, sp -> packed(sp) }) { port, k ->
            val first = get(port, "/favorite/1.png?k=$k")
            assertContentEquals(packed(94), first.body, "Gengar")
            assertContentEquals(packed(151), get(port, "/favorite/3.png?k=$k").body, "Mew")
            // The Kaizo IronMON screen saves new favorites. Nothing else happens: no Play screen opens, nothing publishes.
            Favorites.save(store, kind.id, listOf("Mew", "Gengar"))
            val next = get(port, "/favorite/1.png?k=$k")
            assertContentEquals(packed(151), next.body, "the next request has the edit")
            assertNotEquals(first.headers["etag"], next.headers["etag"], "a new picture, a new tag for the page")
            assertContentEquals(packed(94), get(port, "/favorite/2.png?k=$k").body)
            assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/3.png?k=$k").body, "box 3 is empty now")
            // Emptying every box empties the stream too.
            Favorites.save(store, kind.id, listOf("", "", ""))
            assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/1.png?k=$k").body)
        }
    }

    @Test
    fun `the pictures are drawn again only when the game or its favorites change`() {
        val kind = RomKind.FIRERED_NATDEX_121
        val store = store(kind, listOf("Gengar", "Mew"))
        var drawn = 0
        val source = StreamFavoritesSource.of(store) { _, sp -> drawn++; packed(sp) }
        val a = source.pictures()
        assertEquals(2, drawn)
        repeat(5) { assertTrue(source.pictures() === a, "nothing changed: the same pictures, nothing drawn") }
        assertEquals(2, drawn)
        Favorites.save(store, kind.id, listOf("Gengar", "Pikachu"))
        assertContentEquals(packed(25), source.pictures()[1])
        assertEquals(4, drawn, "both boxes drawn again for the new list")
    }

    @Test
    fun `the favorites shown are those of the game Play opens, and follow it to another game`() {
        val store = store(RomKind.FIRERED_NATDEX_121, listOf("Gengar"))
        Favorites.save(store, RomKind.EMERALD_NATDEX_121.id, listOf("Treecko"))
        val source = StreamFavoritesSource.of(store) { _, sp -> packed(sp) }
        assertContentEquals(packed(94), source.pictures()[0], "FireRed's")
        // Favorites saved for another game than the one in Play leave the stream as it is.
        Favorites.save(store, RomKind.EMERALD_NATDEX_121.id, listOf("Torchic"))
        assertContentEquals(packed(94), source.pictures()[0])
        // A run of that game starts: its own favorites.
        store.saveLastRun(RomKind.EMERALD_NATDEX_121.id, "RSE NatDex v1.2 Kaizo.rnqs")
        assertContentEquals(packed(Favorites.idOf("Torchic")!!), source.pictures()[0], "Emerald's")
    }

    @Test
    fun `with no game there is nothing to show, and no source shows the empty picture`() {
        assertEquals(emptyList<ByteArray?>(), StreamFavoritesSource({ null }, { listOf("Gengar") }) { _, sp -> packed(sp) }.pictures())
        assertEquals(emptyList<ByteArray?>(), StreamFavoritesSource({ error("no session") }, { listOf("Gengar") }) { _, sp -> packed(sp) }.pictures())
        hub(null) { port, k -> assertContentEquals(StreamFavorites.EMPTY, get(port, "/favorite/1.png?k=$k").body) }
    }

    // ------------------------------------------------------------------ the same pictures the card draws

    @Test
    fun `each game's pictures come from where its card draws them from`() {
        assertEquals(From.DS_SPRITE, StreamFavoritePictures.from(Platform.NDS, RomKind.PLATINUM_U))
        assertEquals(From.PACK, StreamFavoritePictures.from(Platform.GBC, RomKind.CRYSTAL_U))
        assertEquals(From.PACK, StreamFavoritePictures.from(Platform.GBC, RomKind.RED_U))
        assertEquals(From.PACK, StreamFavoritePictures.from(Platform.GBC, null), "the Game Boy card draws the pack, tracker or not")
        assertEquals(From.PACK, StreamFavoritePictures.from(Platform.GBA, RomKind.FIRERED_NATDEX_121))
        assertEquals(From.PACK, StreamFavoritePictures.from(Platform.GBA, RomKind.EMERALD_NATDEX_121))
        assertEquals(From.MAXDEX_PACK, StreamFavoritePictures.from(Platform.GBA, RomKind.FIRERED_MAXDEX_10))
        assertEquals(From.ROM_SPRITE, StreamFavoritePictures.from(Platform.GBA, RomKind.FIRERED_U_V11))
        assertEquals(From.ROM_SPRITE, StreamFavoritePictures.from(Platform.GBA, RomKind.EMERALD_U))
        assertEquals(From.NONE, StreamFavoritePictures.from(Platform.GBA, null), "no tracker, no picture on the card")
        // The card's own rules, which those follow: Play's spriteFor and the DS card.
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("val packed = if (platform == com.ironmonone.core.Platform.GBC ||\n                trackerRef?.expandedSpeciesIds == true)\n" +
            "                PcAssets.gbaSprite(context, sp, trackerRef?.nameSet) else null\n            packed ?: trackerRef?.sprite(sp)?.let { px ->" in play)
        val icons = File("src/main/kotlin/com/ironmonone/app/FavoriteIcons.kt").readText().replace("\r\n", "\n")
        assertTrue("FavoriteIconRow(icons, { n -> val c = LocalContext.current; remember(n) { PcAssets.dsSprite(c, n, false) } })" in icons)
        val draw = icons.substringAfter("private fun draw(").substringBefore("\n    }\n")
        for (call in listOf("From.DS_SPRITE -> PcAssets.dsSprite(context, species, false)", "From.PACK -> PcAssets.gbaSprite(context, species, null)",
            "From.MAXDEX_PACK -> PcAssets.gbaSprite(context, species, \"maxdex\")", "From.ROM_SPRITE -> romSprite(s.file, species)"))
            assertTrue(call in draw, call)
        // The server's threads draw through PcAssets too: its cache is held while it is read or filled.
        assertTrue("private fun load(context: android.content.Context, path: String): ImageBitmap? = synchronized(cache) {" in
            File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText())
    }

    private val fireRed = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-u-v10.gba")

    @Test
    fun `a GBA game's picture read from its ROM file is the one the tracker reads from the running game`() {
        if (!fireRed.isFile) { println("SKIP: FireRed v1.0 missing"); return }
        val bytes = fireRed.readBytes()
        // The emulator's view of the cartridge, as the tracker's tests stand it in.
        val running = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(0)
        }
        val tracker = GbaTracker(running, GameMap.resolve(running))
        for (species in listOf(1, 25, 94, 151, 251, 277, 411)) {
            val card = assertNotNull(tracker.sprite(species), "the card's picture of $species")
            assertContentEquals(card, StreamFavoritePictures.romSprite(fireRed, species), "species $species")
        }
        // The file reader reads what the running game's cartridge holds, and nothing outside it.
        RandomAccessFile(fireRed, "r").use { raf ->
            val r = RomFileReader(raf)
            assertContentEquals(bytes.copyOfRange(0xA0, 0xAC), r.read(0x080000A0L, 12), "the header's title")
            assertEquals(0, r.read(0x02000000L, 4).size, "no RAM")
            assertEquals(4, r.read(0x08000000L + bytes.size - 4, 16).size, "up to the end of the file")
            assertEquals(0, r.read(0x08000000L + bytes.size, 4).size)
            assertContentEquals(SpriteDecoder.frontSprite(running, GameMap.resolve(running), 94), SpriteDecoder.frontSprite(r, GameMap.resolve(r), 94))
        }
    }

    // ------------------------------------------------------------------ where it is wired

    @Test
    fun `the app sets the source once, the server asks it every time, and Play has no part in it`() {
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText()
        assertTrue("com.ironmonone.app.stream.StreamHub.favorites = StreamFavoritePictures.source(applicationContext)" in main)
        val hub = File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText()
        assertTrue("favorite = { n -> favorites?.pictures()?.getOrNull(n - 1) }" in hub)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertFalse("StreamFavorites" in play, "Play no longer makes the pictures")
        val src = File("src/main/kotlin/com/ironmonone/app/stream/StreamFavoritesSource.kt").readText()
        // The boxes the run counts: a Heart & Soul Vanilla run's three of its nine (HnsPool.favoritesScope).
        assertTrue("game = { store.session() }" in src &&
            "HnsPool.favoritesScope(s.kind, store.files, nextRun = false).let { sc -> sc.used(Favorites.slots(store, s.kind?.id, sc.stored)) }" in src)
    }
}
