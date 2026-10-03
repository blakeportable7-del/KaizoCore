package com.ironmonone.tracker

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gen3Pictures read out of the real games, read where they lie (IRONMON_ROMS, else the main checkout's .vendor/roms) and
 * never copied; a dump that is not there is skipped. What each game keeps, as found on 2026-10-03 and checked against
 * pret's sources (rom_header_gf.c, decompress.c, the pic and palette tables): FireRed 1.0 and 1.1, LeafGreen and
 * Emerald carry the Game Freak header, Ruby and Sapphire do not and keep the shiny table 440 entries after the normal
 * one; each species' shiny picture is its plain picture in other colors; Unown's 27 other letters are 27 other
 * shapes; Deoxys has FireRed's Attack, LeafGreen's Defense and Emerald's Speed form as its second frame. A montage of
 * what was read is written to a temporary folder, so it can be looked at and not only counted.
 */
class Gen3PicturesRomTest {
    private val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")
    private fun dump(name: String): File? = Dumps.file(roms, name)

    /** The cartridge alone: 0x08000000 on is the file, everything else unmapped, as the emulator's bridge answers. */
    private fun reader(f: File): MemoryReader {
        val rom = f.readBytes()
        return MemoryReader { address, length ->
            val o = address - 0x08000000L
            if (o < 0 || o + length > rom.size) ByteArray(0) else rom.copyOfRange(o.toInt(), (o + length).toInt())
        }
    }

    private class Game(val file: String, val map: GameMap, val header: String?, val shiny: Long, val ownFront: Long)

    private val games = listOf(
        Game("firered-u-v10.gba", GameMap.FIRERED_U_V10, "pokemon red version", 0x082380CC, 0x082350AC),
        Game("firered-u-v11.gba", GameMap.FIRERED_U_V11, "pokemon red version", 0x0823813C, 0x0823511C),
        Game("leafgreen-u.gba", GameMap.LEAFGREEN_U, "pokemon green version", 0x082380A8, 0x08235088),
        // The map's table is gMonStillFrontPicTable (Pokemon Jump's); the game's own is the header's.
        Game("emerald-u.gba", GameMap.EMERALD_U, "pokemon emerald version", 0x08304438, 0x0830A18C),
        Game("ruby-u.gba", GameMap.RUBY_U, null, 0x081EB374, 0x081E8354),
        Game("sapphire-u.gba", GameMap.SAPPHIRE_U, null, 0x081EB304, 0x081E82E4),
    )

    private fun shape(px: IntArray) = px.map { it != 0 }
    private fun lit(px: IntArray) = px.count { it != 0 }

    /** The 16 colors of [table]'s entry for [slot], read here from the table itself, apart from Gen3Pictures. */
    private fun colorsOf(m: MemoryReader, table: Long, slot: Int): Set<Int> {
        val e = m.read(table + slot * 8L, 8)
        val ptr = e.u32(0)
        val raw = assertNotNull(SpriteDecoder.lz77(m.read(ptr, 0x100)), "palette $slot")
        return SpriteDecoder.palette(raw).toSet()
    }

    @Test
    fun `each game's tables are where its header or its palette table says, and the map's are the header's`() {
        var seen = 0
        for (g in games) {
            val f = dump(g.file) ?: run { println("${g.file} not here; skipped"); null } ?: continue
            seen++
            val m = reader(f)
            assertEquals(g.map.name, GameMap.resolve(m).name, g.file)
            val h = Gen3Pictures.header(m)
            assertEquals(g.header, h?.gameName, g.file)
            if (h != null) {
                // The tracker's tables, found by scanning in its day (tools/find_sprites.py), are the header's own:
                // FireRed's and LeafGreen's front table, and every game's palettes.
                assertEquals(g.map.palettes, h.palettes, g.file)
                assertEquals(g.ownFront, h.frontPics, g.file)
                if (g.map != GameMap.EMERALD_U) assertEquals(g.map.frontPics, h.frontPics, g.file)
            }
            val t = assertNotNull(Gen3Pictures.tables(m, g.map), g.file)
            assertEquals(g.shiny, t.shinyPalettes, g.file)
            assertEquals(g.ownFront, t.ownFront, g.file)
            // Every slot of all three tables carries pret's tag: a picture and a normal palette their slot, a shiny
            // palette its slot plus SPECIES_SHINY_TAG.
            for (slot in 0 until Gen3Pictures.TABLE_ENTRIES) {
                assertEquals(slot, assertNotNull(Gen3Pictures.entry(m, t.front, slot, picture = true)).tag, "${g.file} picture $slot")
                assertEquals(slot, assertNotNull(Gen3Pictures.entry(m, t.palettes, slot, picture = false)).tag, "${g.file} palette $slot")
                assertEquals(slot + 500, assertNotNull(Gen3Pictures.entry(m, t.shinyPalettes, slot, picture = false)).tag, "${g.file} shiny $slot")
            }
        }
        if (seen == 0) println("no GBA dump here; skipped")
    }

    @Test
    fun `every species' shiny picture is its plain picture in its own shiny colors`() {
        for (g in games) {
            val f = dump(g.file) ?: continue
            val m = reader(f)
            val t = assertNotNull(Gen3Pictures.tables(m, g.map))
            // 252 to 276 are the empty slots between Celebi and Treecko: one "?" picture, one palette for both.
            for (species in (1..251) + (277..411)) {
                val plain = assertNotNull(SpriteDecoder.frontSprite(m, g.map, species), "${g.file} $species")
                val shiny = assertNotNull(Gen3Pictures.picture(m, t, species, shiny = true, personality = 0, gameForm = false), "${g.file} $species").argb
                assertEquals(shape(plain), shape(shiny), "${g.file} $species: the same picture")
                assertFalse(plain.contentEquals(shiny), "${g.file} $species: in other colors")
                // Read apart from Gen3Pictures, straight from the two tables: each picture's colors are its own palette's.
                val shinyColors = colorsOf(m, t.shinyPalettes, species); val plainColors = colorsOf(m, t.palettes, species)
                assertTrue(shiny.all { it in shinyColors }, "${g.file} $species: every color is one of its shiny palette's")
                assertTrue(plain.all { it in plainColors }, "${g.file} $species: and the plain one's of its normal palette")
                assertNull(Gen3Pictures.picture(m, t, species, shiny = false, personality = 0, gameForm = false), "${g.file} $species: plain is the card's own")
            }
        }
    }

    @Test
    fun `Unown draws its letter and Deoxys its game's form, as each game keeps them`() {
        val forms = HashMap<String, IntArray>()
        val out = kotlin.io.path.createTempDirectory("gba-pictures").toFile()
        val rows = ArrayList<List<IntArray?>>()
        for (g in games) {
            val f = dump(g.file) ?: continue
            val m = reader(f)
            val t = assertNotNull(Gen3Pictures.tables(m, g.map))
            // Unown: A is its own picture (the card's plain one); B to ? are 27 more shapes, each its own.
            val a = assertNotNull(SpriteDecoder.frontSprite(m, g.map, Gen3Pictures.UNOWN))
            val letters = (1..27).map { k ->
                val pid = (0L..0xFFFFFFL).first { Gen3Pictures.unownLetter(it) == k }
                assertNotNull(Gen3Pictures.picture(m, t, Gen3Pictures.UNOWN, false, pid, gameForm = false), "${g.file} letter $k").argb
            }
            assertEquals(28, (listOf(a) + letters).map { shape(it) }.toSet().size, "${g.file}: 28 letters, 28 shapes")
            assertTrue(letters.all { lit(it) >= 60 }, "${g.file}: no blank letter")
            assertNull(Gen3Pictures.picture(m, t, Gen3Pictures.UNOWN, false, 0, gameForm = false), "${g.file}: A")

            // Deoxys: the plain picture (the card's, and an opponent's in battle) is its Normal form.
            val normal = assertNotNull(SpriteDecoder.frontSprite(m, g.map, Gen3Pictures.DEOXYS))
            assertNull(Gen3Pictures.picture(m, t, Gen3Pictures.DEOXYS, false, 0, gameForm = false), g.file)
            val own = Gen3Pictures.picture(m, t, Gen3Pictures.DEOXYS, false, 0, gameForm = true)
            if (g.header == null) assertNull(own, "${g.file} keeps the Normal form only")
            else {
                val form = assertNotNull(own, g.file).argb
                assertNotEquals(shape(normal), shape(form), "${g.file}: another form")
                assertTrue(lit(form) >= 400, g.file)
                forms[g.header]?.let { assertTrue(it.contentEquals(form), "${g.file} is the other ${g.header} cartridge's form") }
                forms[g.header] = form
            }
            rows += listOf(SpriteDecoder.frontSprite(m, g.map, 6), Gen3Pictures.picture(m, t, 6, true, 0, true)?.argb,
                letters[0], letters[26], own?.argb, Gen3Pictures.picture(m, t, Gen3Pictures.DEOXYS, true, 0, true)?.argb)
        }
        // FireRed's Attack form, LeafGreen's Defense and Emerald's Speed: three shapes.
        if (forms.size == 3) assertEquals(3, forms.values.map { shape(it) }.toSet().size)
        if (rows.isNotEmpty()) {
            val cols = 6; val cell = 64
            val px = IntArray(cols * cell * rows.size * cell) { 0xFF1E1E24.toInt() }
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, p -> p?.let { for (y in 0 until 64) for (x in 0 until 64) if (it[y * 64 + x] != 0) px[(r * cell + y) * cols * cell + c * cell + x] = it[y * 64 + x] } } }
            File(out, "montage.png").writeBytes(png(cols * cell, rows.size * cell, px))
            println("montage (Charizard, shiny; Unown B, ?; Deoxys's form, shiny), one row a game: ${File(out, "montage.png").absolutePath}")
        }
    }

    /**
     * Not read for the card there (it draws the shipped menu icons, and a Gen 3 game keeps no shiny icon), but asked:
     * whether the Nat. Dex and MaxDex builds keep the header's tables current. They do: each points at tables that run
     * past the build's last species with every tag in place, the shiny ones too, and two of them are the tables the
     * tracker found by scanning.
     */
    @Test
    fun `the Nat Dex and MaxDex builds keep the header's tables current, and their card is left to its icons`() {
        for ((file, species, scanned) in listOf(Triple("firered-natdex-121.gba", 1283, null), Triple("emerald-natdex-121.gba", 1283, 0x08369C2CL),
                Triple("firered-maxdex.gba", 1280, 0x0824E5D4L))) {
            val f = dump(file) ?: run { println("$file not here; skipped"); null } ?: continue
            val m = reader(f)
            val h = assertNotNull(Gen3Pictures.header(m), file)
            assertTrue(h.gameName == "pokemon red version" || h.gameName == "pokemon emerald version", file)
            val map = GameMap.resolve(m)
            assertTrue(map.expandedSpeciesIds, file)
            scanned?.let { assertEquals(it, h.frontPics, file); assertEquals(it, map.frontPics, file) }
            // Each species' picture and normal palette tagged with its id; each shiny palette tagged apart from it.
            for (s in 1..species) {
                val pic = assertNotNull(Gen3Pictures.entry(m, h.frontPics, s, picture = true), "$file $s")
                assertEquals(s, pic.tag, "$file picture $s")
                assertTrue(assertNotNull(Gen3Pictures.lz77At(m, pic.data), "$file $s").size >= Gen3Pictures.FRAME)
                assertEquals(s, assertNotNull(Gen3Pictures.entry(m, h.palettes, s, picture = false)).tag, "$file palette $s")
                val shiny = assertNotNull(Gen3Pictures.entry(m, h.shinyPalettes, s, picture = false), "$file shiny $s")
                assertNotEquals(s, shiny.tag, "$file shiny $s")
                // Decompresses: MaxDex keeps four short ones (950, 956, 1003, 1155: 11 to 15 colors), the game loads 16 anyway.
                assertNotNull(Gen3Pictures.lz77At(m, shiny.data), "$file shiny $s")
            }
            // The tracker leaves their card to the shipped icons: no picture of its own.
            val tracker = GbaTracker(m, map)
            assertNull(tracker.picture(6, shiny = true, personality = 0, gameForm = true), file)
            assertNull(tracker.picture(Gen3Pictures.UNOWN, shiny = false, personality = 1, gameForm = true), file)
        }
    }

    /** Minimal PNG: 8-bit RGBA, no filtering, one IDAT (the JVM tests have no image library to lean on). */
    private fun png(w: Int, h: Int, argb: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) { raw.write(0); for (x in 0 until w) { val c = argb[y * w + x]; raw.write((c shr 16) and 0xFF); raw.write((c shr 8) and 0xFF); raw.write(c and 0xFF); raw.write((c ushr 24) and 0xFF) } }
        val def = Deflater(); def.setInput(raw.toByteArray()); def.finish()
        val comp = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); comp.write(buf, 0, n) }
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        o.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            o.writeInt(data.size); val t = type.toByteArray(Charsets.US_ASCII); o.write(t); o.write(data)
            val c = CRC32(); c.update(t); c.update(data); o.writeInt(c.value.toInt())
        }
        val ihdr = ByteArrayOutputStream(); DataOutputStream(ihdr).apply { writeInt(w); writeInt(h); write(8); write(6); write(0); write(0); write(0) }
        chunk("IHDR", ihdr.toByteArray()); chunk("IDAT", comp.toByteArray()); chunk("IEND", ByteArray(0))
        return bo.toByteArray()
    }
}
