package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.TrainerPictures
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Gen 3 log viewer's pictures (rc34, Blake: "Does the pc tracker log have more images?"): the trainers' portraits and
 * the player's head read from the ROM, the Walking Pals that stand idle, and the tabs' small pictures. The portraits'
 * tables are proven on the real dumps in tracker-gba (TrainerPicturesRomTest); here, on a ROM made for the test.
 */
class LogPicturesTest {
    /** GBA BIOS LZ77 that keeps every byte as it is. */
    private fun literal(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x10); out.write(data.size and 0xFF); out.write((data.size shr 8) and 0xFF); out.write((data.size shr 16) and 0xFF)
        data.toList().chunked(8).forEach { group -> out.write(0); group.forEach { out.write(it.toInt()) } }
        return out.toByteArray()
    }

    /** Emerald with two trainer pictures of its own (slot 0 all color 1, slot 1 all color 2): trainer n is drawn with slot n % 2. */
    private fun tracker(withTables: Boolean = true): GbaTracker {
        val bytes = ByteArray(0x10000)
        fun put(at: Long, b: ByteArray) = b.copyInto(bytes, (at - 0x08000000L).toInt())
        fun word(at: Long, v: Long) = put(at, ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() })
        fun half(at: Long, v: Int) = put(at, byteArrayOf((v and 0xFF).toByte(), (v shr 8).toByte()))
        var data = 0x08004000L
        for (slot in 0..1) {
            val p = literal(ByteArray(0x800) { (if (slot == 0) 0x11 else 0x22).toByte() }); put(data, p)
            word(0x08001000L + slot * 8, data); half(0x08001000L + slot * 8 + 4, 0x800); half(0x08001000L + slot * 8 + 6, slot)
            data += p.size + 4
            val c = literal(ByteArray(32) { (if (it in 2..5) 0x1F else 0).toByte() }); put(data, c)
            word(0x08001010L + slot * 8, data); half(0x08001010L + slot * 8 + 4, slot)
            data += c.size + 4
        }
        for (n in 1..40) bytes[(0x08002000L + n * 40 + 3 - 0x08000000L).toInt()] = (n % 2).toByte()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(length)
        }
        val map = GameMap.EMERALD_U.copy(gTrainers = 0x08002000L, trainerPics = if (withTables) 0x08001000L else 0L,
            trainerPicPalettes = if (withTables) 0x08001010L else 0L, trainerPicCount = 2, playerPic = 0)
        return GbaTracker(mem, map)
    }

    @Test
    fun `the viewer loads each trainer's portrait and the player's head with the log`() {
        val f = File.createTempFile("view", ".log")
        try {
            f.writeText(File("src/test/resources/logs/emerald.log").readText(Charsets.UTF_8))
            val t = tracker()
            val rules = LogTrainerRules(t, frlg = false)
            val data = loadLogView(f, t, rules)
            val log = assertNotNull(data.log)
            val inPlay = log.trainers.filter { rules.use(it.number) }.map { it.number }
            assertTrue(inPlay.isNotEmpty())
            assertEquals(inPlay.toSet(), data.portraits.keys)
            for (n in inPlay) assertContentEquals(t.trainerPicture(n), data.portraits[n], "trainer $n")
            assertTrue(data.portraits.getValue(1) !== data.portraits.getValue(2) && data.portraits.getValue(1) === data.portraits.getValue(3), "each picture decoded once")
            // The seed ends in 8: the girl's head, slot 1.
            assertTrue(LogPictures.girlFor(log.seed))
            assertContentEquals(TrainerPictures.head(assertNotNull(t.playerPicture(girl = true))), data.playerHead)
            // A build with no proven tables: no portraits, no head.
            val none = loadLogView(f, tracker(withTables = false), rules)
            assertTrue(none.portraits.isEmpty())
            assertNull(none.playerHead)
        } finally { f.delete() }
    }

    @Test
    fun `the head is the girl's on an even seed and the boy's on an odd one, as the PC tracker's is by its run`() {
        assertTrue(LogPictures.girlFor("186610104527268"))
        assertFalse(LogPictures.girlFor("-4619714808076434503"))
        assertFalse(LogPictures.girlFor(""), "no seed: the boy")
    }

    @Test
    fun `an idle Walking Pal fits its square, never over the name under it`() {
        val sheets = listOf("walkingpals/walkingpals.tsv", "walkingpals-nat/walkingpals-nat.tsv").flatMap { path ->
            WalkingPals.parse(File("src/main/assets/$path").readLines().asSequence()).values.mapNotNull { it[WalkingPals.Anim.IDLE] }
        }
        assertTrue(sheets.size > 1000, "${sheets.size} idle sheets")
        for (box in listOf(18f, 36f, 56f)) for (s in sheets) {
            val (x, y, w, h) = LogPictures.fit(s.w, s.h, s.x, s.y, box).toList()
            assertTrue(x >= -0.01f && y >= -0.01f && x + w <= box + 0.01f && y + h <= box + 0.01f, "${s.w}x${s.h} at ${s.x},${s.y} in $box: $x,$y $w x $h")
            assertEquals(s.w.toFloat() / s.h, w / h, 0.001f)
        }
        // One inside the 32 box keeps the box's scale and stands on its floor; a tall one shrinks to fit.
        assertContentEquals(floatArrayOf(4f, 0f, 24f, 32f), LogPictures.fit(24, 32, 4, 0, 32f))
        assertContentEquals(floatArrayOf(4f, 0f, 24f, 32f).map { it * 2 }.toFloatArray(), LogPictures.fit(24, 32, 4, 0, 64f))
        val tall = LogPictures.fit(32, 56, 0, 0, 56f)
        assertEquals(56f, tall[3], 0.001f)
    }

    @Test
    fun `the Pokemon tab's little Pokemon are the PC tracker's eight, from the shipped Walking Pals`() {
        val gen3 = WalkingPals.parse(File("src/main/assets/walkingpals/walkingpals.tsv").readLines().asSequence())
        assertEquals(listOf(32, 29, 16, 21, 39, 35, 138, 140), LogPictures.TAB_POKEMON, "LogTabPokemon.TabIcons")
        for (id in LogPictures.TAB_POKEMON) assertNotNull(gen3[id.toString()]?.get(WalkingPals.Anim.IDLE), "#$id")
        assertEquals(2, LogPictures.TAB_POKEMON_SHOWN)
    }

    @Test
    fun `the viewer draws the pictures where the PC tracker does`() {
        val src = File("src/main/kotlin/com/ironmonone/app")
        fun read(f: String) = File(src, f).readText().replace("\r\n", "\n")
        val viewer = read("LogViewer.kt")
        assertTrue("LogTabIcon(t, tabPals, playerHead, if (on) Pc.Gold else Pc.Text)" in viewer, "the tabs' pictures")
        assertTrue("portraitOf = portraitOf)" in viewer && "portrait = portraitOf(td), palOf = logPal)" in viewer, "the trainers' portraits")
        assertEquals(5, Regex("""palOf = logPal""").findAll(viewer).count(), "the Pokemon tab, a Pokemon's page, a trainer's team, a route page's wild Pokemon and a Game Boy Pokemon page")
        assertTrue("!TrackerOptions.animatedSprites) null" in viewer, "still pictures with Animated sprites off")
        val pokemon = read("LogPokemon.kt")
        assertEquals(3, Regex("""LogMonIcon\(""").findAll(pokemon).count(), "the grid, the page and its evolutions")
        assertTrue("LogMonIcon(p?.let { spriteOf?.invoke(it) }, p?.let { palOf?.invoke(it) }, 36.dp, names.species(m.name))" in read("LogTrainers.kt"))
        assertTrue("LogTrainerTile(t, rules, custom, portraitOf(t))" in read("LogRoutes.kt"), "a route page's trainers")
        // The log's icons stand idle: only the idle sheet is read, never the walking, sleeping or fainting ones.
        val pictures = read("LogPictures.kt")
        assertTrue("WalkingPals.bitmap(ctx, WalkingPals.Anim.IDLE, pal)" in pictures)
        assertFalse("Anim.WALK" in pictures || "Anim.SLEEP" in pictures || "palAnim(" in pictures)
    }
}
