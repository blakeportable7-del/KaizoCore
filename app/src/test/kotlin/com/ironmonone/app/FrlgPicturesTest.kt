package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The FireRed and LeafGreen dungeon maps (2026-09-29; seven since 2026-09-30): the table points at files that exist,
 * every file is used, SOURCE.txt accounts for every original and says why each one that does not ship is left out,
 * and the table is keyed by the tracker's own map ids.
 */
class FrlgPicturesTest {
    // The maps are off until the player turns them on (TrackerOptions.frlgGuidePictures); these tests look at them on.
    init { TrackerOptions.frlgGuidePictures = true }

    @kotlin.test.AfterTest fun switchBack() { TrackerOptions.frlgGuidePictures = false }

    @Test
    fun `the guide pictures are off until the player turns them on, and the switch is saved`() {
        TrackerOptions.frlgGuidePictures = false
        assertNull(FrlgPictures.placeFor("FRLG", 115), "no mark on Mt. Moon with the switch off")
        assertNull(FrlgPictures.lookupFor("FRLG"))
        val src = File("src/main/kotlin/com/ironmonone/app/TrackerOptions.kt").readText()
        assertTrue("var frlgGuidePictures by mutableStateOf(false)" in src, "off by default")
        assertTrue("\"frlgGuidePictures\" -> frlgGuidePictures = v == \"true\"" in src, "read back")
        assertTrue("frlgGuidePictures=\$frlgGuidePictures" in src, "written")
        TrackerOptions.frlgGuidePictures = true
        assertNotNull(FrlgPictures.placeFor("FRLG", 115))
        // The player turns them on in Tracker Setup: the row reads and writes the one switch, and says what they show.
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue(gear.lines().any {
            "GearToggle(\"FireRed and LeafGreen dungeon maps (routes and item spots)\", TrackerOptions.frlgGuidePictures) " +
                "{ TrackerOptions.frlgGuidePictures = it; TrackerOptions.save() }" in it
        })
    }

    /** src/main/assets, where FrlgPictures' paths start. */
    private val assets = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
    private val frlg = File(assets, "frlg")

    /** SOURCE.txt's data rows: shipped file (or "-"), original, bytes, pixels, SHA-256, and a note for a file not shipped. */
    private fun sourceRows(): List<List<String>> =
        File(frlg, "SOURCE.txt").readLines(Charsets.UTF_8).filter { '\t' in it && !it.startsWith("#") }.map { it.split('\t') }

    /** The tracker's own FireRed names for its map ids (RouteData.Info). */
    private fun trackerNames(): Map<Int, String> =
        File("../tracker-gba/src/main/resources/gen3/routeinfo-firered.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { l -> l.split('\t').let { it[0].toInt() to it[2] } }

    private fun place(id: Int) = assertNotNull(FrlgPictures.places.firstOrNull { it.mapId == id }, "no place for map $id")

    /** The width and height a lossless WebP's header states, or a failure for anything that is not one. */
    private fun losslessSize(f: File): Pair<Int, Int> {
        val b = f.inputStream().use { it.readNBytes(32) }
        assertEquals("RIFF", String(b, 0, 4), "${f.name} is not a RIFF file")
        assertEquals("WEBP", String(b, 8, 4), "${f.name} is not a WebP")
        assertEquals("VP8L", String(b, 12, 4), "${f.name} is not lossless")
        assertEquals(0x2f, b[20].toInt() and 0xff, "${f.name}: VP8L signature")
        val bits = (b[21].toInt() and 0xff) or ((b[22].toInt() and 0xff) shl 8) or ((b[23].toInt() and 0xff) shl 16) or ((b[24].toInt() and 0xff) shl 24)
        return ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
    }

    /** The seven that ship since 2026-09-30, the IronMON rules check. */
    private val kept = setOf("mt_moon", "ss_anne", "victory_road", "power_plant", "pokemon_mansion", "rock_tunnel", "seafoam_islands")

    @Test
    fun `seven dungeon maps ship, each used, and no hidden item screenshot or step by step plan`() {
        val onDisk = File(frlg, "maps").listFiles { f -> f.isFile }.orEmpty().map { it.name }.toSet()
        assertEquals(kept.map { "$it.webp" }.toSet(), onDisk)
        assertFalse(File(frlg, "hidden").exists(), "the hidden item screenshots came from another randomized game")
        for (plan in listOf("silph_co", "rocket_hideout", "safari_zone", "saffron_gym")) assertFalse(File(frlg, "maps/$plan.webp").exists(), plan)
        assertEquals(kept, FrlgPictures.places.flatMap { it.maps }.toSet(), "every map is shown from some place, and only those")
        for (p in FrlgPictures.places) {
            assertEquals(1, p.maps.size, "${p.name}: one map for the building")
            for (f in p.maps) assertTrue(File(assets, FrlgPictures.mapAsset(f)).let { it.isFile && it.length() > 0 }, "${p.name}: missing ${FrlgPictures.mapAsset(f)}")
        }
    }

    @Test
    fun `SOURCE txt still lists all 118 originals, and says why each one that does not ship is left out`() {
        val text = File(frlg, "SOURCE.txt").readText(Charsets.UTF_8)
        assertTrue("https://github.com/billgreenwald/ironmon_emu" in text)
        assertTrue("abf9453ba031a2ff58ab9a11059cb8678aec15e1" in text, "the pinned commit")
        assertTrue("Bill Greenwald (doctrDNA)" in text && "used with permission" in text)
        val rows = sourceRows()
        assertEquals(118, rows.size, "11 maps and 107 hidden item pictures at that commit")
        assertEquals(11, rows.count { it[1].startsWith("route_maps/") })
        assertEquals(107, rows.count { it[1].startsWith("hidden_items/") })
        assertEquals(rows.size, rows.map { it[1] }.toSet().size, "an original listed twice")
        for (r in rows) {
            assertTrue(Regex("[0-9a-f]{64}").matches(r[4]), "${r[1]}: SHA-256 is ${r[4]}")
            assertTrue(r[2].toLong() > 0 && Regex("\\d+x\\d+").matches(r[3]), "${r[1]}: bytes and pixels")
        }
        // Shipped rows are exactly the files on disk.
        val shipped = rows.filter { it[0] != "-" }
        assertEquals(7, shipped.size)
        val onDisk = File(frlg, "maps").listFiles().orEmpty().map { "maps/${it.name}" }.toSet()
        assertEquals(onDisk, shipped.map { it[0] }.toSet(), "SOURCE.txt and the folder disagree")
        // Every original that is not shipped says why.
        val left = rows.filter { it[0] == "-" }
        assertEquals(111, left.size)
        for (r in left) assertTrue(r.size > 5 && r[5].isNotBlank(), "${r[1]} is left out without a reason")
        assertEquals(106, left.count { "another randomized game" in it[5] }, "the hidden item screenshots")
        assertEquals(4, left.count { "IronMON rules check" in it[5] && it[1].startsWith("route_maps/") }, "the plans and the solution")
        // The one original left out from the start is a byte for byte copy of another, and still says so.
        val twin = rows.single { it[1] == "hidden_items/route_4_3.png" }
        assertTrue("hidden_items/route_4_2.png" in twin[5])
    }

    @Test
    fun `each shipped file is a lossless WebP of the size SOURCE txt records`() {
        for (r in sourceRows().filter { it[0] != "-" }) {
            val (w, h) = losslessSize(File(frlg, r[0]))
            assertEquals(r[3], "${w}x$h", "${r[0]} is not the size of ${r[1]}")
        }
    }

    @Test
    fun `the places are the tracker's own map ids, and where it names one the names agree`() {
        val names = trackerNames()
        assertEquals(FrlgPictures.places.size, FrlgPictures.places.map { it.mapId }.toSet().size, "an id twice")
        var named = 0
        for (p in FrlgPictures.places) names[p.mapId]?.let { n -> assertEquals(n, p.name, "map ${p.mapId}"); named++ }
        assertTrue(named >= 25, "only $named places could be checked against the tracker's names")
        // Ids the tracker's table does not name still have to be the game's: the S.S. Anne's kitchen and captain's office.
        for (id in listOf(170, 171)) assertNull(names[id], "$id is in the tracker's table now; take its name")
    }

    @Test
    fun `every floor of the seven dungeons shows its building's map`() {
        val dungeons = mapOf("Mt. Moon" to "mt_moon", "S.S. Anne" to "ss_anne", "Victory Road" to "victory_road",
            "Poké Mansion" to "pokemon_mansion", "Rock Tunnel" to "rock_tunnel", "Seafoam Islands" to "seafoam_islands",
            "Power Plant" to "power_plant")
        val floors = trackerNames().filter { (_, n) -> dungeons.keys.any { n.startsWith(it) } }
        // Mt. Moon 3, S.S. Anne 8, Victory Road 3, Poke Mansion 4, Rock Tunnel 2, Seafoam Islands 5, Power Plant 1.
        assertEquals(26, floors.size, "the tracker's floors of the seven: ${floors.values}")
        for ((id, n) in floors) {
            val want = dungeons.entries.first { n.startsWith(it.key) }.value
            assertEquals(listOf(want), assertNotNull(FrlgPictures.placeFor("FRLG", id), "$n ($id) has no map").maps, n)
        }
        // The S.S. Anne's kitchen and captain's office are the same ship.
        for (id in listOf(170, 171)) assertEquals(listOf("ss_anne"), place(id).maps)
        // The four that do not ship have no mark any more.
        for (id in listOf(34, 128, 132, 147)) assertNull(FrlgPictures.placeFor("FRLG", id), "map $id")
    }

    @Test
    fun `only FireRed and LeafGreen get pictures`() {
        assertEquals("Mt. Moon B1F", FrlgPictures.placeFor("FRLG", 115)?.name)
        for (other in listOf("RSE", "RBY", "GSC", "HGSS", "DPPT", "BW", "", "frlg")) assertNull(FrlgPictures.placeFor(other, 115), other)
        assertNull(FrlgPictures.placeFor(null, 115))
        assertNull(FrlgPictures.placeFor("FRLG", null))
        assertNull(FrlgPictures.placeFor("FRLG", 1), "Mom's House has none")
        assertNull(FrlgPictures.placeFor("FRLG", 9999))
        assertNull(FrlgPictures.lookupFor("RSE"))
        assertNull(FrlgPictures.lookupFor(null))
        assertEquals("Seafoam Islands B3F", FrlgPictures.lookupFor("FRLG")!!.invoke(159)?.name)
        assertNull(FrlgPictures.lookupFor("FRLG")!!.invoke(2))
    }

    /** The badge set is what the tracker reports for each ROM; the Nat. Dex builds take it from their base game. */
    @Test
    fun `the tracker reports FRLG for FireRed and LeafGreen only`() {
        for (m in listOf(GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U)) assertTrue(FrlgPictures.isFrlg(m.badgeSet), m.name)
        for (m in listOf(GameMap.EMERALD_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U)) assertFalse(FrlgPictures.isFrlg(m.badgeSet), m.name)
    }

    @Test
    fun `the mark says what it opens`() {
        assertEquals("Show the map for Mt. Moon B1F", place(115).spoken())
        assertEquals("Show the map for Power Plant", place(168).spoken())
    }
}
