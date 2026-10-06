package com.ironmonone.app

import androidx.compose.runtime.snapshots.Snapshot
import com.ironmonone.app.stream.StreamFavoritesSource
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tracker Setup's EDIT FAVORITES (rc35.1; Blake approved it): the Run screen's favorites editor, opened during a Kaizo
 * IronMON run, as the PC tracker's Streamer settings let a player change them in game (StreamerScreen.lua). One editor
 * for both places, saving through Favorites.save; only in a Kaizo IronMON run; and a save reaches the tracker's
 * favorites row, its ball line and the stream's pictures at once.
 */
class FavoritesInPlayTest {
    private val src = "src/main/kotlin/com/ironmonone/app/"
    private fun read(name: String) = File(src + name).readText().replace("\r\n", "\n")

    private fun runOn(settings: String, favorites: String): Triple<File, PrepStore, GameSession> {
        val filesDir = Files.createTempDirectory("favinplay").toFile()
        val store = PrepStore(filesDir)
        store.saveFavorites(RomKind.FIRERED_U_V10.id, favorites)
        store.saveLastRun(RomKind.FIRERED_U_V10.id, settings)
        return Triple(filesDir, store, GameSession.forRun(File(filesDir, "run.gba"), RomKind.FIRERED_U_V10))
    }

    @Test
    fun `the editor saves through Favorites save, and both screens open the same editor`() {
        val (_, store, _) = runOn("FRLG Kaizo.rnqs", "Squirtle,Gengar,Dragonite")
        val id = RomKind.FIRERED_U_V10.id
        val before = Favorites.edits.intValue
        Favorites.save(store, id, listOf("Bulbasaur", "", "Mr. Mime"))
        assertEquals(listOf("Bulbasaur", "", "Mr. Mime"), Favorites.slots(store, id, 3), "read back as saved")
        assertEquals("Bulbasaur,,Mr. Mime", store.favoritesText(id))
        assertTrue(Favorites.edits.intValue > before, "a save is counted, so what shows the favorites reads again")

        val editor = read("FavoritesEditor.kt")
        // One save for every box: typing and a name picked from its list both come through FavoriteNameField's onValue.
        assertEquals(1, Regex("""Favorites\.save\(store, favRomId, favSlots\)""").findAll(editor).count(), "typing and a suggestion")
        val run = read("RunScreen.kt")
        assertTrue("FavoritesEditor(store, selectedRom?.first, RulesetCatalog.modeOf(modes, selectedSettings)?.key, nextRun = true)" in run)
        assertFalse("Favorites.save(" in run || "Favorites.suggest(" in run || "FavoriteRulesBlock(" in run, "nothing duplicated on the Run screen")
        // In play: the game in Play and the run's mode, so the same rules, limits and ball note apply.
        assertTrue("store.session().kind" in editor && "FavoriteBall.modeOf(store)" in editor && "FavoritesEditor(store, kind, mode, nextRun = false)" in editor)
    }

    @Test
    fun `the row shows only in a Kaizo IronMON run`() {
        fun scope(rules: PlayRules.Kind, isRun: Boolean, gameBoy: Boolean = false, ds: Boolean = false) =
            GearScope(gameBoy, ds, "FRLG", false, rules, isRun)
        assertTrue(FavoritesInPlay.offered(scope(PlayRules.Kind.IRONMON, isRun = true)))
        assertTrue(FavoritesInPlay.offered(scope(PlayRules.Kind.IRONMON, isRun = true, ds = true)), "a DS run")
        assertTrue(FavoritesInPlay.offered(scope(PlayRules.Kind.IRONMON, isRun = true, gameBoy = true)), "a Game Boy run")
        assertFalse(FavoritesInPlay.offered(scope(PlayRules.Kind.NUZLOCKE, isRun = true)), "a Nuzlocke")
        assertFalse(FavoritesInPlay.offered(scope(PlayRules.Kind.PLAIN, isRun = false)), "a library game")
        val gear = read("TrackerGearDialog.kt")
        assertEquals(1, gear.lines().count { "EditFavoritesRow()" in it })
        assertTrue(gear.lines().single { "EditFavoritesRow()" in it }.trim().startsWith("if (FavoritesInPlay.offered(scope)) EditFavoritesRow()"))
    }

    @Test
    fun `a save reaches the tracker's favorites row and the stream at once`() {
        val (dir, store, session) = runOn("FRLG Kaizo.rnqs", "Squirtle,Gengar,Dragonite")
        // Made once, as Play's remember makes it, and never made again.
        val shown = FavoriteBall.shown(store, session, null, dir)
        assertEquals("FAVORITES: SQUIRTLE / GENGAR / DRAGONITE", shown.list)
        val stream = StreamFavoritesSource.of(store) { _, sp -> byteArrayOf(sp.toByte(), (sp shr 8).toByte()) }
        val streamBefore = stream.pictures().map { it?.toList() }

        // Reading the row inside a snapshot observes Favorites.edits: a composable that reads it redraws on a save.
        val reads = HashSet<Any>()
        val snap = Snapshot.takeSnapshot { reads += it }
        try { snap.enter { shown.icons } } finally { snap.dispose() }
        assertTrue(Favorites.edits in reads, "the tracker's row observes the saves")

        Favorites.save(store, RomKind.FIRERED_U_V10.id, listOf("Pikachu", "Gengar", ""))
        assertEquals("FAVORITES: PIKACHU / GENGAR", shown.list, "the same object, read again")
        assertEquals(listOf("Pikachu", "Gengar"), shown.icons.map { it.name })
        assertNotEquals(streamBefore, stream.pictures().map { it?.toList() }, "the stream's pictures follow")
    }

    // The ball line on the real FireRed, where the vendored ROM is present.
    private val fireRed = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-u-v10.gba")

    @Test
    fun `a save mid-run changes the ball line in the lab`() {
        if (!fireRed.exists()) { println("SKIP: FireRed v1.0 missing"); return }
        val bytes = fireRed.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(0)
        }
        val t = GbaTracker(mem, GameMap.resolve(mem))
        NuzlockeTracking.reset()
        val (dir, store, session) = runOn("FRLG Kaizo.rnqs", "Gengar")
        val shown = FavoriteBall.shown(store, session, t, dir)
        assertTrue(shown.balls.isEmpty())
        Favorites.save(store, RomKind.FIRERED_U_V10.id, listOf("Squirtle", "Gengar", ""))
        assertEquals(listOf("FAVORITE! SQUIRTLE IN THE MIDDLE BALL"), shown.balls)
    }
}
