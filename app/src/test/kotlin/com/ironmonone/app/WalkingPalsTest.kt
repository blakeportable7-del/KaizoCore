package com.ironmonone.app

import androidx.compose.runtime.snapshots.Snapshot
import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Walking Pals tables as shipped, the one lookup over both sets, and the frame and facing rules SpriteData and Input use. */
class WalkingPalsTest {
    private val table = ShippedPals.gen3
    private val ix = ShippedPals.index

    @Test
    fun `the shipped table covers Gen 1 to 3 with every sheet on disk`() {
        assertTrue(table.size >= 380, "only ${table.size} species")
        assertTrue((1..251).all { it.toString() in table }, "a Gen 1 or 2 species is missing")
        // Gengar (94): SpriteData.WalkingPals[94].
        val g = assertNotNull(table["94"])
        val idle = assertNotNull(g[WalkingPals.Anim.IDLE])
        assertEquals(listOf(40, 4, 3, 3, 3, 3, 3, 4), idle.durations.toList())
        assertEquals(32 to 40, idle.w to idle.h)
        for ((id, anims) in table) for (a in anims.keys) {
            assertTrue(File("src/main/assets/walkingpals/${a.key}/$id.png").isFile, "no sheet ${a.key}/$id")
        }
    }

    /** Phase 1 wrote the second set keyed by text: a national number, or "<national>-<form>". */
    @Test
    fun `keys are read as text, so the second set's forms load with its numbers`() {
        val t = WalkingPals.parse(sequenceOf(
            "# key\tanimation\tw\th\tx\ty\tdurations",
            "6-mega-x\tidle\t48\t56\t-7\t-1\t15,15,15,15",
            "741-pom-pom\twalk\t32\t40\t0\t4\t8,10",
            "387\tidle\t32\t40\t0\t4\t40,2",
            "../idle/25\tidle\t32\t32\t0\t0\t1",
            "6-Mega-X\tidle\t32\t32\t0\t0\t1",
        ))
        assertEquals(setOf("6-mega-x", "741-pom-pom", "387"), t.keys, "a key the tables never write is not read")
        val x = assertNotNull(t["6-mega-x"]?.get(WalkingPals.Anim.IDLE))
        assertEquals(listOf(48, 56, -7, -1), listOf(x.w, x.h, x.x, x.y))
        assertEquals(listOf(15, 15, 15, 15), x.durations.toList())
        // As shipped: every key of the second set, forms and all, with its sheets on disk.
        val nat = ShippedPals.national
        assertTrue(nat.size >= 700, "only ${nat.size} keys")
        assertTrue(nat.keys.count { '-' in it } >= 150, "the forms did not load")
        assertEquals(48 to 56, nat.getValue("6-mega-x").getValue(WalkingPals.Anim.IDLE).let { it.w to it.h })
        for ((key, anims) in nat) for (a in anims.keys) {
            assertTrue(ShippedPals.file(Pal(Pack.NATIONAL, key), a).isFile, "no sheet ${a.key}/$key")
        }
    }

    @Test
    fun `one lookup takes each caller's numbering, at each seam`() {
        // A retail Gen 3 game: its own ids, 1 to 411, and nothing past them (412 is its egg).
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(25, Dex.GEN3))
        assertEquals(Pal(Pack.GEN3, "411"), ix.find(411, Dex.GEN3), "Chimecho")
        assertNull(ix.find(412, Dex.GEN3))
        // A Nat. Dex build: 1 to 411 as Gen 3's, then the expansion's ids through natdex-map.tsv.
        assertEquals(Pal(Pack.GEN3, "277"), ix.find(277, Dex.NAT_DEX), "Treecko")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(412, Dex.NAT_DEX), "Turtwig")
        assertEquals(Pal(Pack.NATIONAL, "1025"), ix.find(1050, Dex.NAT_DEX), "Pecharunt")
        // National numbers: 1 to 251 straight, Hoenn through Gen 3's own order, 387 on the second set.
        assertEquals(Pal(Pack.GEN3, "251"), ix.find(251, Dex.NATIONAL), "Celebi")
        assertEquals(Pal(Pack.GEN3, "277"), ix.find(252, Dex.NATIONAL), "Treecko")
        assertEquals(Pal(Pack.GEN3, "392"), ix.find(280, Dex.NATIONAL), "Ralts")
        assertEquals(Pal(Pack.GEN3, "410"), ix.find(386, Dex.NATIONAL), "Deoxys")
        assertEquals(Pal(Pack.GEN3, "411"), ix.find(358, Dex.NATIONAL), "Chimecho")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(387, Dex.NATIONAL), "Turtwig")
    }

    @Test
    fun `a Nat Dex form finds its own sheet, or the base one where the map says it looks the same`() {
        assertEquals(Pal(Pack.NATIONAL, "6-mega-x"), ix.find(1052, Dex.NAT_DEX), "Charizard-X")
        assertEquals(Pal(Pack.NATIONAL, "19-alola"), ix.find(1101, Dex.NAT_DEX), "Rattata-A")
        assertEquals(Pal(Pack.NATIONAL, "479-wash"), ix.find(1174, Dex.NAT_DEX), "Rotom-Wash")
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(1157, Dex.NAT_DEX), "Partner Pikachu is drawn as Pikachu")
    }

    @Test
    fun `no sheet is null, never another Pokemon's`() {
        assertNull(ix.find(539, Dex.NAT_DEX), "Simisear: nobody has drawn it yet")
        assertNull(ix.find(514, Dex.NATIONAL), "Simisear by its national number")
        assertNull(ix.find(1263, Dex.NAT_DEX), "Mega Falinks: Falinks has no sheet either, so nothing stands in")
        assertNull(ix.find(260, Dex.GEN3), "one of Gen 3's unused slots")
        assertNull(ix.find(1284, Dex.NAT_DEX), "a Nat. Dex build's egg")
        assertNull(ix.find(1285, Dex.NAT_DEX), "the Nat. Dex ghost stand-in")
        assertNull(ix.find(413, Dex.GEN3), "the retail ghost stand-in")
        assertNull(ix.find(1026, Dex.NATIONAL), "past the National Dex")
        for (d in Dex.entries) { assertNull(ix.find(0, d)); assertNull(ix.find(-5, d)) }
    }

    /**
     * Blake, 2026-10-02: a Mega or form with no walking sprite of its own uses its base species' sheet, "only if we don't
     * have the correct sprites". The converter writes it into natdex-map.tsv (convert_walking_pals_nat.py's stand_in,
     * tested by convert_walking_pals_nat_test.py), so the first run after a form's own sheet is drawn uses that instead.
     */
    @Test
    fun `a Mega or form with no sheet of its own walks as its base species, never over a sheet of its own`() {
        class Row(val id: Int, val national: Int, val form: String, val pal: Pal?, val note: String)
        val rows = File("src/main/assets/walkingpals-nat/natdex-map.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
            .map { c -> Row(c[0].toInt(), c[2].toInt(), c[3], ShippedPals.natDex[c[0].toInt()], c.getOrElse(6) { "" }) }
        val standIns = rows.filter { "stands in until its own is drawn" in it.note }
        assertTrue(standIns.size >= 60, "only ${standIns.size}")
        for (r in standIns) {
            assertTrue(r.id > 1050, "${r.id}: only a Mega or form stands in, never a species")
            assertFalse("${r.national}-${r.form}" in ShippedPals.national, "${r.id} stands in over a sheet of its own")
            // Its base species' sheet, as the base's own row has it (Gen 1-3 by Gen 3's id).
            val base = assertNotNull(Favorites.fromNational(r.national))
            assertEquals(ix.find(base, Dex.NAT_DEX), assertNotNull(r.pal), "${r.id} walks as ${r.national}")
        }
        // Every form that has a sheet of its own keeps it.
        for (r in rows) if (r.id > 1050 && r.form.isNotEmpty() && "${r.national}-${r.form}" in ShippedPals.national) {
            assertEquals(Pal(Pack.NATIONAL, "${r.national}-${r.form}"), r.pal, "${r.id}")
        }
        assertEquals(Pal(Pack.GEN3, "6"), ix.find(1053, Dex.NAT_DEX), "Charizard-Y walks as Charizard")
        assertEquals(Pal(Pack.NATIONAL, "6-mega-x"), ix.find(1052, Dex.NAT_DEX), "Charizard-X keeps its own")
        assertEquals(Pal(Pack.GEN3, "282"), ix.find(1073, Dex.NAT_DEX), "Mega Blaziken walks as Blaziken, by Gen 3's id")
        assertEquals(Pal(Pack.NATIONAL, "668-female"), ix.find(1255, Dex.NAT_DEX), "Mega Pyroar as Pyroar's own row has it")
        assertNull(ix.find(1263, Dex.NAT_DEX), "Mega Falinks: its base has no sheet")
        assertNull(ix.find(895, Dex.NAT_DEX), "Falinks itself: no species is given another's")
    }

    @Test
    fun `every sheet the Nat Dex map names is in its set's table and on disk`() {
        val map = ShippedPals.natDex
        assertEquals((412..1283).toSet(), map.keys, "the map covers the expansion's ids exactly")
        val named = map.values.filterNotNull()
        assertTrue(named.size >= 700, "only ${named.size} with a sheet")
        for (pal in named) {
            val sheets = assertNotNull(ix.sheets(pal), "$pal is not in its table")
            assertTrue(WalkingPals.Anim.IDLE in sheets, "$pal has no idle sheet")
            for (a in sheets.keys) assertTrue(ShippedPals.file(pal, a).isFile, "no file for $pal ${a.key}")
        }
    }

    /**
     * Play as your Pokemon hands the emulator side one frame at most 128 x 128 (sprite_core.h's kMaxSpriteDim). A few
     * second-set sheets have bigger frames: each frame of those, cut to what shows (SpriteArt.placeFrame), fits whole,
     * not a shown pixel lost, so every Pokemon the picker lists can be drawn.
     */
    @Test
    fun `every frame of every sheet fits the overworld once cut to what shows`() {
        var big = 0
        for ((pack, set) in listOf(Pack.GEN3 to ShippedPals.gen3, Pack.NATIONAL to ShippedPals.national, Pack.DARKUS to ShippedPals.darkus)) {
            for ((key, anims) in set) for ((anim, sheet) in anims) {
                if (sheet.w <= SpriteArt.MAX_FRAME && sheet.h <= SpriteArt.MAX_FRAME) continue
                big++
                val img = ShippedPals.pixels(ShippedPals.file(Pal(pack, key), anim))
                for (row in 0 until img.h / sheet.h) for (col in 0 until img.w / sheet.w) {
                    val frame = SpriteArt.crop(img, col * sheet.w, row * sheet.h, sheet.w, sheet.h)
                    val placed = SpriteArt.placeFrame(frame, sheet.x, sheet.y)
                    val what = "$key ${anim.key} row $row frame $col"
                    assertTrue(placed.pixels.w <= SpriteArt.MAX_FRAME && placed.pixels.h <= SpriteArt.MAX_FRAME, what)
                    assertEquals(frame.argb.count { (it ushr 24) != 0 }, placed.pixels.argb.count { (it ushr 24) != 0 }, "$what lost a pixel")
                }
            }
        }
        assertTrue(big > 0, "no sheet has a big frame any more: this test checks nothing")
    }

    @Test
    fun `the tracker panel numbers its species by the game`() {
        assertEquals(Dex.NATIONAL, WalkingPals.trackerDex(generation = 1, speciesTotal = 151))
        assertEquals(Dex.NATIONAL, WalkingPals.trackerDex(generation = 2, speciesTotal = 251))
        assertEquals(Dex.GEN3, WalkingPals.trackerDex(generation = 3, speciesTotal = 411))
        assertEquals(Dex.NAT_DEX, WalkingPals.trackerDex(generation = 3, speciesTotal = 1283))
        // MaxDex 1.0 in Play: its own numbering, though the panel counts it as an expanded build like Nat. Dex.
        assertEquals(Dex.MAX_DEX, WalkingPals.trackerDex(generation = 3, speciesTotal = 1283, maxDex = true))
    }

    private fun species(set: String) =
        WalkingPals.parseSpecies(File("../tracker-gba/src/main/resources/$set/species.tsv").readLines(Charsets.UTF_8).asSequence())

    /**
     * MaxDex 1.0 walks too (2026-10-03). Its ids are the Nat. Dex Extension's to 1235; its 45 Legends Z-A Megas, 1236 to
     * 1280, are in an order of its own, so each is the Nat. Dex Mega of the same name, with that one's sheet or stand-in.
     * Read from the tracker's own two species tables, as the phone reads them.
     */
    @Test
    fun `MaxDex counts its own way, Nat Dex's ids to 1235 and its Z-A Megas by name`() {
        val ids = ShippedPals.maxDex
        val max = species("maxdex")
        val nat = species("natdex")
        assertEquals((1..1280).toSet(), ids.keys, "every MaxDex id, and nothing past its last")
        assertTrue((1..1235).all { ids[it] == it }, "the ids the two tables share")
        val megas = (1236..1280).associateWith { ids.getValue(it) }
        assertEquals(45, megas.values.toSet().size, "45 Megas, 45 Nat. Dex ids")
        for ((m, n) in megas) {
            assertEquals(max.getValue(m), nat.getValue(n), "MaxDex's $m is the Nat. Dex Pokemon of its own name")
            assertTrue(n in 1238..1283, "$m: a Legends Z-A Mega in Nat. Dex too")
        }
        assertEquals(1241, ids[1236], "Dragonite-M")
        assertEquals(1282, ids[1280], "Baxcalibur-M")
        // Each walks as the Nat. Dex Pokemon of its name, never as the one Nat. Dex keeps at the same number.
        assertEquals(Pal(Pack.GEN3, "149"), ix.find(1236, Dex.MAX_DEX), "Dragonite-M walks as Dragonite")
        assertEquals(Pal(Pack.NATIONAL, "658"), ix.find(1236, Dex.NAT_DEX), "Nat. Dex's 1236 is Battle Bond Greninja")
        assertEquals(Pal(Pack.NATIONAL, "998"), ix.find(1280, Dex.MAX_DEX), "Baxcalibur-M walks as Baxcalibur")
        assertEquals(Pal(Pack.NATIONAL, "227-mega"), ix.find(1244, Dex.MAX_DEX), "Skarmory-M has a sheet of its own")
        assertNull(ix.find(1276, Dex.MAX_DEX), "Falinks-M: its base has no sheet either")
        assertEquals(44, (1236..1280).count { ix.find(it, Dex.MAX_DEX) != null })
        // Gen 1 to 9 as the Nat. Dex Extension numbers them.
        assertEquals(Pal(Pack.GEN3, "1"), ix.find(1, Dex.MAX_DEX))
        assertEquals(Pal(Pack.GEN3, "277"), ix.find(277, Dex.MAX_DEX), "Treecko")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(412, Dex.MAX_DEX), "Turtwig")
        assertEquals(Pal(Pack.NATIONAL, "1007"), ix.find(1032, Dex.MAX_DEX), "Koraidon")
        assertNull(ix.find(1018, Dex.MAX_DEX), "Iron Jugulis: nobody has drawn it yet")
        assertEquals(Pal(Pack.DARKUS, "1008"), ix.find(1033, Dex.MAX_DEX), "Miraidon: DarkusShadow's sheet fills the gap")
        // Past MaxDex's last Pokemon (where its egg and ghost stand-ins sit), and Gen 3's unused slots: nothing.
        for (id in listOf(1281, 1282, 1283, 1285, 260, 0, -1)) assertNull(ix.find(id, Dex.MAX_DEX), "$id")
    }

    @Test
    fun `a MaxDex id is matched by its name or not at all`() {
        val max = mapOf(1 to "Bulbasaur", 252 to "none", 1236 to "Dragonite-M", 1240 to "Nobody", 1241 to "none")
        val nat = mapOf(1 to "Bulbasaur", 252 to "none", 253 to "none", 1236 to "Greninja-B", 1241 to "Dragonite-M")
        // The same name at the same id keeps the id; a name that moved follows its name; a name Nat. Dex does not have,
        // or has twice and not at that id, is left out.
        assertEquals(mapOf(1 to 1, 252 to 252, 1236 to 1241), WalkingPals.maxDexToNatDex(max, nat))
        // Without the tables (a read that failed), a MaxDex Pokemon finds nothing, never a Nat. Dex one by its number.
        val bare = WalkingPals.Index(ShippedPals.gen3, ShippedPals.national, ShippedPals.natDex)
        assertNull(bare.find(1, Dex.MAX_DEX))
        assertNull(bare.find(1236, Dex.MAX_DEX))
        assertEquals(mapOf(1 to "Bulbasaur", 412 to "Turtwig"), WalkingPals.parseSpecies(sequenceOf("1\tBulbasaur", "", "x\ty", "412\tTurtwig ")))
    }

    @Test
    fun `frames follow their durations, and faint stops on its last`() {
        val s = WalkingPals.Sheet(32, 40, 0, 0, intArrayOf(40, 6, 6))
        assertEquals(0, s.frameAt(0, loop = true))
        assertEquals(0, s.frameAt(39, loop = true))
        assertEquals(1, s.frameAt(40, loop = true))
        assertEquals(2, s.frameAt(51, loop = true))
        assertEquals(0, s.frameAt(52, loop = true))
        assertEquals(2, s.frameAt(500, loop = false))
    }

    @Test
    fun `facing follows Input getSpriteFacingDirection`() {
        assertEquals(0, WalkingPals.facingRow(up = false, down = false, left = false, right = false))
        assertEquals(0, WalkingPals.facingRow(up = false, down = true, left = false, right = false))
        assertEquals(1, WalkingPals.facingRow(up = false, down = true, left = false, right = true))
        assertEquals(2, WalkingPals.facingRow(up = false, down = false, left = false, right = true))
        assertEquals(4, WalkingPals.facingRow(up = true, down = false, left = false, right = false))
        assertEquals(6, WalkingPals.facingRow(up = false, down = false, left = true, right = false))
        assertEquals(5, WalkingPals.facingRow(up = true, down = false, left = true, right = false))
    }

    /**
     * rc32 audit P2 #106: every sheet decoded was kept for the life of the process, about 634 KB a species in the Gen 1-3
     * set and 905 KB in the other, hundreds of MB over a long session on top of a DS core. The cache is bounded by bytes.
     */
    @Test
    fun `decoded sheets are bounded by what they hold, the least recently used going first`() {
        val mb = 1024 * 1024
        var loads = 0
        val c = SheetCache<ByteArray>(32L * mb) { it.size.toLong() }
        repeat(60) { i -> assertNotNull(c.get("s$i") { loads++; ByteArray(mb) }) }
        assertEquals(60, loads)
        assertEquals(32, c.size)
        assertTrue(c.bytes <= 32L * mb, "${c.bytes} bytes")
        // The first ones went: asked for again, one is decoded again. One still held is not, and is kept the longer for it.
        assertNotNull(c.get("s0") { loads++; ByteArray(mb) })
        assertEquals(61, loads)
        assertNotNull(c.get("s59") { loads++; ByteArray(mb) })
        assertEquals(61, loads)
        // A sheet that is not there is looked for once.
        assertNull(c.get("missing") { loads++; null })
        assertNull(c.get("missing") { loads++; null })
        assertEquals(62, loads)
        // One bigger than the bound on its own is still given to the icon that asked, and the rest make room for it.
        assertNotNull(c.get("huge") { ByteArray(40 * mb) })
        assertEquals(1, c.size)
    }

    @Test
    fun `the animated icon decodes off the main thread, never in composition`() {
        val src = File("src/main/kotlin/com/ironmonone/app/WalkingPals.kt").readText().replace("\r\n", "\n")
        val icon = src.substringAfter("fun WalkingPalsIcon(")
        assertFalse(Regex("remember\\(pal\\) \\{[^}]*WalkingPals\\.bitmap").containsMatchIn(icon), "a remember block runs in composition, on the main thread")
        val produce = icon.substringAfter("produceState<").substringBefore("\n    }\n")
        assertTrue("withContext(Dispatchers.IO) { WalkingPals.bitmap(ctx, WalkingPals.Anim.IDLE, pal) }" in produce, produce)
        assertEquals(2, Regex("WalkingPals\\.bitmap\\(").findAll(icon.substringBefore("Canvas(")).count(), "the idle sheet, then the rest, both on IO")
        assertTrue("SheetCache<ImageBitmap>(CACHE_BYTES)" in src)
    }

    /**
     * RC35-NOTICED N #10: the three tables were read and parsed on first use, and the first use was on the main thread (the
     * tracker's icon and the picker in composition, Play as your Pokemon in a tick). They are read once, on a thread of
     * their own; the first to ask is told null at once, and a composable told so is told again when they land.
     */
    @Test
    fun `the tables are read once on a thread of their own, and the main thread never waits for them`() {
        var handed: Runnable? = null
        val once = ReadOnce<String> { r -> handed = r }
        var reads = 0
        var readOn: Thread? = null
        val read = { reads++; readOn = Thread.currentThread(); "tables" }
        // Asked as composition asks: answered at once, and the answer's state is a read the composition records.
        val seen = HashSet<Any>()
        assertNull(Snapshot.observe(readObserver = { seen.add(it) }) { once.get(read) })
        assertNull(once.get(read))
        assertEquals(0, reads, "nothing is read on the thread that asks")
        val job = assertNotNull(handed, "the read was handed to a thread of its own")
        val changed = HashSet<Any>()
        val observer = Snapshot.registerApplyObserver { set, _ -> changed.addAll(set) }
        try {
            Thread(job).apply { start(); join() }
            Snapshot.sendApplyNotifications()
        } finally {
            observer.dispose()
        }
        assertEquals(1, reads)
        assertNotSame(Thread.currentThread(), readOn)
        assertTrue(seen.isNotEmpty() && seen.any { it in changed }, "what composition read changed when the tables landed, so it recomposes")
        assertEquals("tables", once.get(read))
        assertEquals(1, reads, "read once, not once per ask")
        // A read that fails leaves it empty, as a missing table leaves the sets empty, and never throws at the one asking.
        val failing = ReadOnce<String> { it.run() }
        assertNull(failing.get { error("no assets") })
        assertNull(failing.get { "not asked again" })
    }

    @Test
    fun `nothing on the main thread reads the tables, every lookup there asks ready`() {
        val src = File("src/main/kotlin/com/ironmonone/app").walk().filter { it.extension == "kt" }
            .associate { it.name to it.readText().replace("\r\n", "\n") }
        val pals = src.getValue("WalkingPals.kt")
        // The lookups that read the tables on first use are gone; ready hands the read to its own thread.
        assertFalse(Regex("fun (find|sheets)\\(ctx").containsMatchIn(pals), "a lookup that reads on first use")
        assertTrue("fun ready(ctx: android.content.Context): Index? = loaded.get { index(ctx.applicationContext ?: ctx) }" in pals)
        assertTrue("Thread(r, \"walking-pals\").apply { isDaemon = true }.start()" in pals)
        // The read that waits is only where it runs off the main thread: AndroidSpriteArt.prepare.
        assertEquals(listOf("SpriteIsMeArt.kt"), src.filter { "WalkingPals.index(" in it.value }.keys.toList())
        // MaxDex's two species tables are read with the rest, inside that read, and nowhere else.
        assertEquals(listOf("WalkingPals.kt"), src.filter { "maxDexIds()" in it.value }.keys.toList())
        assertTrue("maxDexIds()," in pals.substringAfter("fun index(ctx: android.content.Context): Index =").substringBefore("private val loaded"))
        val art = src.getValue("SpriteIsMeArt.kt")
        assertTrue("val table = WalkingPals.index(ctx).sheets(p)" in art.substringAfter("private fun preparePal(").substringBefore("\n    }\n"))
        // The four that run on the main thread ask ready: the animated icon, the tracker card's head, the picker, the tick.
        assertTrue("WalkingPals.ready(ctx).let { ix -> remember(ix, pal) { ix?.sheets(pal) } }" in pals.substringAfter("fun WalkingPalsIcon("))
        assertTrue("WalkingPals.ready(iconCtx).let { ix -> remember(ix, iconSpecies, iconDex, iconLook)" in src.getValue("PcTracker.kt").substringAfter("fun PcHeadBlock("))
        val picker = src.getValue("SpriteIsMeUi.kt").substringAfter("private fun SpeciesPickerDialog(")
        assertTrue("val ix = WalkingPals.ready(ctx)" in picker && "remember(all, query)" in picker, "the list fills in when the tables land")
        assertTrue("override fun pal(id: Int, dex: WalkingPals.Dex): WalkingPals.Pal? = WalkingPals.ready(ctx)?.find(id, dex)" in art)
    }
}
