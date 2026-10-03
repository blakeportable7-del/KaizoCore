package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rest of rc32 audit P2 #27: Play's Open Book parsed the run's log and built every route on the main thread the first
 * time route info opened, and built a name map of every species on each look. OpenBookRoutes does it once per run, and
 * the route info screen asks for it on Dispatchers.Default.
 */
class OpenBookRoutesTest {
    private val emerald = File("C:/Users/bepor/IronMonOne/.vendor/roms/emerald-u.gba")

    /** Emerald's tracker: on the real ROM, so species have their names, else on empty memory (names are "#id"). */
    private fun tracker(): Pair<GbaTracker, Boolean> {
        if (!emerald.exists()) return GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U) to false
        val bytes = emerald.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length)
            else ByteArray(length)
        }
        return GbaTracker(mem, GameMap.resolve(mem)) to true
    }

    @Test
    fun `the log's encounters for a route, built once, most likely first`() {
        val dir = Files.createTempDirectory("openbook").toFile()
        val store = PrepStore(dir)
        val kind = RomKind.EMERALD_U
        val (t, named) = tracker()
        val book = OpenBookRoutes()
        // No log yet: nothing, and nothing kept, so the log is read once it is there.
        assertNull(book.icons(t, store, kind, "RSE", 17, "Walking"))
        assertEquals(0, book.builds)
        val rom = store.currentRunFor(kind).apply { parentFile.mkdirs(); writeBytes(ByteArray(8)) }
        File(rom.parentFile, rom.name + ".log").writeText(javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8))
        val route101 = book.icons(t, store, kind, "RSE", 17, "Walking")!!
        assertTrue(route101.isNotEmpty(), "Route 101's grass")
        assertTrue(route101.zipWithNext().all { (a, b) -> (a.rate ?: 0.0) >= (b.rate ?: 0.0) }, "most likely first: $route101")
        assertTrue(route101.all { it.minLv != null && it.maxLv != null }, "levelled: $route101")
        if (named) assertTrue(route101.all { it.species != null }, "named by the game's own table: $route101")
        else println("SKIP: Emerald missing, so the species names are not checked")
        // A second look, and another area, use the routes already built.
        assertEquals(route101, book.icons(t, store, kind, "RSE", 17, "Walking"))
        book.icons(t, store, kind, "RSE", 17, "Surfing")
        assertEquals(1, book.builds)
        // Not a Gen 3 run's map, or an area the log has no table for: nothing.
        assertNull(book.icons(t, store, kind, "DPPT", 17, "Walking"))
        assertNull(book.icons(t, store, kind, "RSE", 17, "Headbutt"))
        assertNull(book.icons(t, store, null, "RSE", 17, "Walking"))
    }

    @Test
    fun `Play keeps no log work of its own, and route info reads it off the main thread`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertFalse("RandomizerLog.parse(" in play || "LogRoutes.build(" in play || "associateBy { t.speciesName(it).uppercase() }" in play)
        assertTrue("val openBook = remember(session.id, gameKeyForRom) { OpenBookRoutes() }" in play)
        assertTrue("logged = { area -> openBook.icons(t, store, session.kind, set, mapId, area) }," in play)
        val route = File("src/main/kotlin/com/ironmonone/app/RouteInfoScreen.kt").readText()
        assertTrue("withContext(Dispatchers.Default) { runCatching { read(want.second) }.getOrNull() }" in route)
        assertFalse("src.logged?.invoke(area)" in route, "never in composition")
        assertTrue("readingLog -> emptyList()" in route, "and draws no table of its own while it reads")
    }
}
