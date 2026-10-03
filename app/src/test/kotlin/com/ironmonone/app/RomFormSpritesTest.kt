package com.ironmonone.app

import com.dabomstew.pkrandomzx.newnds.NARCArchive
import com.dabomstew.pkrandomzx.newnds.NDSFile
import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS tracker's form pictures (RomFormSprites), proven on the test dumps in .vendor/roms, read in place: Platinum,
 * HeartGold, Black 2 and White 2. Every form was first looked at by eye in a montage (2026-10-03); these hold what the
 * game's own files say about the places, so a slip of one picture or one palette fails. A dump that is not here is
 * skipped.
 */
class RomFormSpritesTest {
    private val roms = File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms")
    private fun rom(name: String): NDSRom? = File(roms, name).takeIf { it.isFile }?.let { NDSRom(it.absolutePath) }
    private fun magic(b: ByteArray) = if (b.size >= 4) String(b, 0, 4, Charsets.ISO_8859_1) else ""
    private fun lit(d: RomSprites.Decoded) = d.argb.count { it != 0 }
    private fun shape(d: RomSprites.Decoded) = d.argb.map { it != 0 }
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `Platinum's forms come out of pl_otherpoke, each a picture of its own, the first form too`() {
        val nds = rom("platinum-u.nds") ?: run { println("platinum-u.nds not here; skipped"); return }
        val forms = RomFormSprites.forms(nds, RomKind.PLATINUM_U)
        val other = NARCArchive(nds.getFile(RomFormSprites.PL_OTHERPOKE))
        val pokegra = NARCArchive(nds.getFile(RomSprites.narcPath(RomKind.PLATINUM_U)!!))
        // Every form of the twelve species the game draws from pl_otherpoke, the first included.
        assertEquals(mapOf(386 to 4, 201 to 28, 351 to 4, 412 to 3, 413 to 3, 422 to 2, 423 to 2, 421 to 2, 493 to 18, 492 to 2, 479 to 6, 487 to 2),
            forms.groupingBy { it.species }.eachCount())
        for (f in forms) {
            // Each lands on a picture and on two palettes, never one for the other.
            assertEquals("RGCN" to 6448, magic(other.files[f.picture]) to other.files[f.picture].size, "$f")
            assertEquals(listOf("RLCN", "RLCN"), listOf(magic(other.files[f.palette]), magic(other.files[f.shinyPalette])), "$f")
        }
        // Each species' pictures are a block, its backs then its fronts: the twelve blocks and the eggs' two pictures (132,
        // 133) are all 154 pictures in pl_otherpoke, each once.
        val blocks = forms.groupBy { it.species }.mapValues { (_, fs) -> (fs.maxOf { it.picture } + 1 - 2 * fs.size)..fs.maxOf { it.picture } }
        assertEquals((0 until 154).toList(), (blocks.values.flatMap { it } + listOf(132, 133)).sorted())
        // The species' own slot in pl_pokegra, which the game never draws for these twelve, is a front: in its block, the
        // picture nearest it is one the arithmetic takes for a front, never a back, so the places line up with the game's
        // own files and not only with each other. For five it is the first form's, the very same picture. The others hold
        // an older drawing, and Cherrim's its Sunshine form (form 1), which every Cherrim showed before.
        for ((species, block) in blocks) {
            val pal = pokegra.files[species * 6 + 4]
            var own = pokegra.files[species * 6 + 3]
            if (own.isEmpty()) own = pokegra.files[species * 6 + 2]
            val ownPx = RomSprites.decodeGen4(own, pal, backwards = false).argb
            val apart = block.associateWith { i -> RomSprites.decodeGen4(other.files[i], pal, backwards = false).argb.let { px -> px.indices.count { px[it] != ownPx[it] } } }
            val nearest = apart.keys.minBy { apart.getValue(it) }
            val form = assertNotNull(forms.firstOrNull { it.species == species && it.picture == nearest }, "species $species: its own picture is nearest $nearest, a back: $apart")
            if (species in listOf(386, 201, 351, 479, 487)) assertEquals(0 to 0, form.form to apart.getValue(nearest), "species $species")
            if (species == 421) assertEquals(1, form.form, "Cherrim's own slot is its Sunshine form")
        }
        // Each alternate form is a picture of its own, unlike form 0. Its shiny palette paints the same picture, in other
        // colours for nearly all (not every one: a shiny Castform in the weather looks as it always does).
        var shinyDiffers = 0
        for (f in forms) {
            val d = RomFormSprites.decode(other, f, shiny = false, gen5 = false)
            val shiny = RomFormSprites.decode(other, f, shiny = true, gen5 = false)
            assertTrue(lit(d) >= 100, "$f is blank")
            if (f.form > 0) {
                val first = RomFormSprites.decode(other, forms.single { it.species == f.species && it.form == 0 }, shiny = false, gen5 = false)
                assertFalse(d.argb.contentEquals(first.argb), "$f is its species' first form")
            }
            assertEquals(shape(d), shape(shiny), "$f")
            if (!d.argb.contentEquals(shiny.argb)) shinyDiffers++
        }
        assertTrue(shinyDiffers * 10 >= forms.size * 9, "only $shinyDiffers of ${forms.size} shinies differ: the shiny palette is not the shiny one")
        // Unown's letters are 28 shapes; Arceus's types one shape in 18 colourings; Deoxys's forms 4 shapes in one.
        fun pictures(species: Int) = forms.filter { it.species == species }.map { RomFormSprites.decode(other, it, shiny = false, gen5 = false) }
        assertEquals(28, pictures(201).map { shape(it) }.toSet().size)
        assertEquals(1, pictures(493).map { shape(it) }.toSet().size)
        assertEquals(18, pictures(493).map { it.argb.toList() }.toSet().size)
        assertEquals(4, pictures(386).map { shape(it) }.toSet().size)
    }

    @Test
    fun `Black 2 and White 2 keep their forms after Genesect's, where each species' personal data points`() {
        var seen = 0
        for ((name, kind) in listOf("black2-u.nds" to RomKind.BLACK2_U, "white2-u.nds" to RomKind.WHITE2_U)) {
            val nds = rom(name) ?: run { println("$name not here; skipped"); null } ?: continue
            seen++
            val forms = RomFormSprites.forms(nds, kind)
            val gra = NARCArchive(nds.getFile(RomFormSprites.B2W2_PICTURES))
            // The forms fill entries 685 to 750 exactly once each: no gap and no two forms on one picture. Unown's 27 come
            // first, from its offset 0; Arceus's offset is 0 as well and it has none there, so it has no forms here.
            val entries = forms.map { it.picture / 20 }
            assertEquals((685..750).toList(), entries.sorted(), name)
            assertEquals(685, forms.single { it.species == 201 && it.form == 1 }.picture / 20)
            assertEquals(27, forms.count { it.species == 201 })
            assertTrue(forms.none { it.species == 493 }, "Arceus")
            assertEquals(mapOf(201 to 27, 351 to 3, 386 to 3, 412 to 2, 413 to 2, 421 to 1, 422 to 1, 423 to 1, 479 to 5, 487 to 1, 492 to 1,
                550 to 1, 555 to 1, 585 to 3, 586 to 3, 641 to 1, 642 to 1, 645 to 1, 646 to 2, 647 to 1, 648 to 1, 649 to 4),
                forms.groupingBy { it.species }.eachCount(), name)
            var shinyDiffers = 0
            for (f in forms) {
                val d = RomFormSprites.decode(gra, f, shiny = false, gen5 = true)
                val shiny = RomFormSprites.decode(gra, f, shiny = true, gen5 = true)
                val base = RomSprites.decodeGen5(gra.files[f.species * 20], gra.files[f.species * 20 + 18])
                assertTrue(lit(d) >= 100, "$name $f is blank")
                assertFalse(d.argb.contentEquals(base.argb), "$name $f is its species' own picture")
                assertEquals(listOf("RLCN", "RLCN"), listOf(magic(gra.files[f.palette]), magic(gra.files[f.shinyPalette])), "$name $f")
                assertEquals(shape(d), shape(shiny), "$name $f")
                if (!d.argb.contentEquals(shiny.argb)) shinyDiffers++
            }
            assertTrue(shinyDiffers * 10 >= forms.size * 9, "$name: only $shinyDiffers of ${forms.size} shinies differ")
            // Deerling's and Sawsbuck's seasons, and Kyurem's White and Black, each differ from the others.
            for (sp in listOf(585, 586, 646)) {
                val all = listOf(RomSprites.decodeGen5(gra.files[sp * 20], gra.files[sp * 20 + 18])) +
                    forms.filter { it.species == sp }.map { RomFormSprites.decode(gra, it, shiny = false, gen5 = true) }
                assertEquals(all.size, all.map { it.argb.toList() }.toSet().size, "$name $sp")
            }
        }
        if (seen == 0) println("no Black 2 or White 2 dump here; skipped")
    }

    /** The ROM's own file table, as NDSRom reads it from the FNT and FAT: every path, and where its bytes lie. */
    @Suppress("UNCHECKED_CAST")
    private fun fileTable(nds: NDSRom): Map<String, NDSFile> =
        NDSRom::class.java.getDeclaredField("files").apply { isAccessible = true }.get(nds) as Map<String, NDSFile>

    /** How many files the NARC at [f] holds (its FATB count), read from its header alone; -1 for a file that is no NARC. */
    private fun narcCount(rom: RandomAccessFile, f: NDSFile): Int {
        if (f.size < 0x1C) return -1
        val h = ByteArray(0x1C); rom.seek(f.offset.toLong()); rom.readFully(h)
        fun u16(o: Int) = (h[o].toInt() and 0xFF) or ((h[o + 1].toInt() and 0xFF) shl 8)
        return if (magic(h) == "NARC" && u16(0x0C) == 0x10 && String(h, 0x10, 4, Charsets.ISO_8859_1) == "BTAF") u16(0x18) else -1
    }

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    /**
     * pokeheartgold's own build checks (heartgold.us/filesystem.sha1 and soulsilver.us/filesystem.sha1, read 2026-10-03)
     * give the two games' a/0/0/4 and a/1/1/4 these same hashes, which is what proves SoulSilver without a dump of it.
     */
    private val hgssSha1 = mapOf("a/0/0/4" to "7318a24ec62c83be4345bbf05d3b60ae6f558f22", "a/1/1/4" to "8419d0d617ef11b66d527fff43006d8515128f1b")

    @Test
    fun `HeartGold's forms come out of the one archive of otherpoke's kind, Platinum's twelve and Pichu's two`() {
        val file = File(roms, "heartgold-u.nds").takeIf { it.isFile } ?: run { println("heartgold-u.nds not here; skipped"); return }
        val nds = NDSRom(file.absolutePath)
        // Found by the ROM's own file table: of every archive under a/, one holds otherpoke's 261 files.
        val table = fileTable(nds)
        val counts = RandomAccessFile(file, "r").use { r -> table.filterKeys { it.startsWith("a/") }.mapValues { (_, f) -> narcCount(r, f) } }
        assertTrue(counts.size > 250, "the file table: ${counts.size} archives under a/")
        assertEquals(listOf(RomFormSprites.HGSS_OTHERPOKE), counts.filterValues { it == 261 }.keys.toList())
        // The dump's two archives are the ones pokeheartgold's build matches HeartGold and SoulSilver against.
        for ((path, hash) in hgssSha1) assertEquals(hash, sha1(nds.getFile(path)), path)

        val forms = RomFormSprites.forms(nds, RomKind.HEARTGOLD_U)
        val other = NARCArchive(nds.getFile(RomFormSprites.HGSS_OTHERPOKE))
        val pokegra = NARCArchive(nds.getFile(RomSprites.narcPath(RomKind.HEARTGOLD_U)!!))
        assertEquals(261, other.files.size)
        assertEquals(mapOf(386 to 4, 201 to 28, 351 to 4, 412 to 3, 413 to 3, 422 to 2, 423 to 2, 421 to 2, 493 to 18, 492 to 2, 479 to 6, 487 to 2, 172 to 2),
            forms.groupingBy { it.species }.eachCount())
        for (f in forms) {
            assertEquals("RGCN" to 6448, magic(other.files[f.picture]) to other.files[f.picture].size, "$f")
            assertEquals(listOf("RLCN", "RLCN"), listOf(magic(other.files[f.palette]), magic(other.files[f.shinyPalette])), "$f")
        }
        // The thirteen blocks of backs and fronts and the eggs' two pictures (132, 133) are all 158 pictures, each once.
        val blocks = forms.groupBy { it.species }.mapValues { (_, fs) -> (fs.maxOf { it.picture } + 1 - 2 * fs.size)..fs.maxOf { it.picture } }
        assertEquals((0 until 158).toList(), (blocks.values.flatMap { it } + listOf(132, 133)).sorted())
        assertEquals("RLCN", magic(other.files[158]), "the palettes start right after Pichu's pictures")
        // As on Platinum: each species' own slot in a/0/0/4 is nearest a front of its block; Pichu's is its first form.
        for ((species, block) in blocks) {
            val pal = pokegra.files[species * 6 + 4]
            var own = pokegra.files[species * 6 + 3]
            if (own.isEmpty()) own = pokegra.files[species * 6 + 2]
            val ownPx = RomSprites.decodeGen4(own, pal, backwards = false).argb
            val apart = block.associateWith { i -> RomSprites.decodeGen4(other.files[i], pal, backwards = false).argb.let { px -> px.indices.count { px[it] != ownPx[it] } } }
            val nearest = apart.keys.minBy { apart.getValue(it) }
            val form = assertNotNull(forms.firstOrNull { it.species == species && it.picture == nearest }, "species $species: its own picture is nearest $nearest, a back: $apart")
            if (species == 172) assertEquals(0, form.form, "Pichu's own slot is its first form")
        }
        var shinyDiffers = 0
        for (f in forms) {
            val d = RomFormSprites.decode(other, f, shiny = false, gen5 = false)
            val shiny = RomFormSprites.decode(other, f, shiny = true, gen5 = false)
            assertTrue(lit(d) >= 100, "$f is blank")
            if (f.form > 0) {
                val first = RomFormSprites.decode(other, forms.single { it.species == f.species && it.form == 0 }, shiny = false, gen5 = false)
                assertFalse(d.argb.contentEquals(first.argb), "$f is its species' first form")
            }
            assertEquals(shape(d), shape(shiny), "$f")
            if (!d.argb.contentEquals(shiny.argb)) shinyDiffers++
        }
        assertTrue(shinyDiffers * 10 >= forms.size * 9, "only $shinyDiffers of ${forms.size} shinies differ: the shiny palette is not the shiny one")
        fun pictures(species: Int) = forms.filter { it.species == species }.map { RomFormSprites.decode(other, it, shiny = false, gen5 = false) }
        assertEquals(28, pictures(201).map { shape(it) }.toSet().size)
        assertEquals(1, pictures(493).map { shape(it) }.toSet().size)
        assertEquals(18, pictures(493).map { it.argb.toList() }.toSet().size)
        assertEquals(4, pictures(386).map { shape(it) }.toSet().size)
        assertEquals(2, pictures(172).map { shape(it) }.toSet().size, "Pichu and its Spiky ear")
        // Platinum's twelve sit at Platinum's pictures in HeartGold too, each palette four on.
        rom("platinum-u.nds")?.let { pl ->
            val platinum = RomFormSprites.forms(pl, RomKind.PLATINUM_U).associateBy { it.species to it.form }
            for (f in forms.filter { it.species != 172 }) {
                val p = assertNotNull(platinum[f.species to f.form], "$f")
                assertEquals(listOf(p.picture, p.palette + 4, p.shinyPalette + 4), listOf(f.picture, f.palette, f.shinyPalette), "$f")
            }
        }
        // The decode pass: every form once, plain and shiny, then the mark.
        val dir = Files.createTempDirectory("rom-forms-hg").toFile()
        try {
            val write = { _: RomSprites.Decoded, out: File -> out.writeBytes(byteArrayOf(1)); true }
            assertEquals(78 * 2, RomFormSprites.decodeAll(nds, dir, RomKind.HEARTGOLD_U, write), "the thirteen species' 78 forms, plain and shiny")
            assertTrue(RomFormSprites.done(dir, RomKind.HEARTGOLD_U) && File(dir, "172-1s.png").isFile && File(dir, "201-27.png").isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * The patched builds the forms pass reads by their base game's places: each, built here from the dump with its bundled
     * patch as the Prep screen builds it, keeps the base game's two picture archives byte for byte. A dump that is not
     * here skips its builds.
     */
    @Test
    fun `the patched builds read as their base game keep its picture archives byte for byte`() {
        var built = 0
        for (id in RomFormSprites.PATCHED_PROVEN) {
            val kind = RomKind.allPatched.single { it.id == id }
            val base = RomKind.allV1.single { it.id == kind.baseId }
            val baseFile = File(roms, "${base.id}.nds").takeIf { it.isFile } ?: run { println("${base.id}.nds not here; ${kind.id} skipped"); null } ?: continue
            val opt = PrepOptions.forKind(base).first { it.out?.id == kind.id }
            val out = File.createTempFile(kind.id, ".nds")
            try {
                val crc = Patcher.applyFiles(File("src/main/assets/patches/" + opt.asset), baseFile, out)
                assertEquals("%08x".format(kind.expectedCrc), "%08x".format(crc), "the pinned build of ${kind.id}")
                val baseRom = NDSRom(baseFile.absolutePath)
                val patched = NDSRom(out.absolutePath)
                try {
                    val forms = if (base.id == RomKind.PLATINUM_U.id) RomFormSprites.PL_OTHERPOKE else RomFormSprites.HGSS_OTHERPOKE
                    for (a in listOf(RomSprites.narcPath(base)!!, forms)) assertTrue(baseRom.getFile(a).contentEquals(patched.getFile(a)), "${kind.id} $a")
                    assertTrue(RomFormSprites.supports(kind))
                    assertEquals(RomFormSprites.forms(baseRom, base), RomFormSprites.forms(patched, kind), kind.id)
                } finally {
                    baseRom.closeROM(); patched.closeROM()
                }
                built++
            } finally {
                out.delete()
            }
        }
        if (built == 0) println("no base dump for a patched build here; skipped")
        // Not proven, so not read: the Faster Black 2 and White 2 builds.
        for (k in RomKind.allPatched.filter { it.baseId == RomKind.BLACK2_U.id || it.baseId == RomKind.WHITE2_U.id }) assertFalse(RomFormSprites.supports(k), k.id)
    }

    /** RomSprites.decodeAll's forms pass, minus the PNG writer: every form once, then a mark so the next visit opens nothing. */
    @Test
    fun `the forms pass writes each form once and marks the cache done`() {
        val nds = rom("platinum-u.nds") ?: run { println("platinum-u.nds not here; skipped"); return }
        val dir = Files.createTempDirectory("rom-forms").toFile()
        try {
            val kind = RomKind.PLATINUM_U
            assertFalse(RomFormSprites.done(dir, kind), "a cache from before the forms is read again for them")
            val write = { _: RomSprites.Decoded, out: File -> out.writeBytes(byteArrayOf(1)); true }
            assertEquals(76 * 2, RomFormSprites.decodeAll(nds, dir, kind, write), "the twelve species' 76 forms, plain and shiny")
            assertTrue(RomFormSprites.done(dir, kind))
            assertTrue(File(dir, "421-0.png").isFile && File(dir, "479-1.png").isFile && File(dir, "479-1s.png").isFile && File(dir, "201-27.png").isFile)
            assertEquals(0, RomFormSprites.decodeAll(nds, dir, kind, write), "what is there is not written again")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a form not read, and a game with none, draw the species' own picture`() {
        val dir = Files.createTempDirectory("rom-forms").toFile()
        try {
            val k = RomKind.PLATINUM_U
            val f = RomFormSprites.cacheFile(dir, k, 479, 2, shiny = false)
            assertEquals(File(dir, "sprites/platinum-u/479-2.png"), f)
            assertEquals("479-2s.png", RomFormSprites.cacheFile(dir, k, 479, 2, shiny = true).name)
            assertNull(RomFormSprites.cached(dir, k, 479, 2, shiny = false), "not read yet")
            f.parentFile.mkdirs(); f.writeBytes(byteArrayOf(1))
            assertEquals(f, RomFormSprites.cached(dir, k, 479, 2, shiny = false))
            assertNull(RomFormSprites.cached(dir, k, 479, 0, shiny = false), "a first form not read: the species' own picture")
            RomFormSprites.cacheFile(dir, k, 479, 0, shiny = false).writeBytes(byteArrayOf(1))
            assertEquals(File(dir, "sprites/platinum-u/479-0.png"), RomFormSprites.cached(dir, k, 479, 0, shiny = false), "Platinum's first forms are read too")
            assertNull(RomFormSprites.cached(dir, k, 479, -1, shiny = false))
            assertNull(RomFormSprites.cached(dir, k, 479, 3, shiny = false), "a form with no picture here")
            assertNull(RomFormSprites.cached(dir, null, 479, 2, shiny = false), "no DS game running")
            // Games it was not proven on read no forms, and the species' pass is not held up waiting for them.
            for (other in listOf(RomKind.BLACK_U, RomKind.WHITE_U, RomKind.DIAMOND_U, RomKind.PEARL_U)) {
                assertFalse(RomFormSprites.supports(other), other.id)
                assertTrue(RomFormSprites.done(dir, other), other.id)
            }
            assertTrue(listOf(RomKind.PLATINUM_U, RomKind.HEARTGOLD_U, RomKind.SOULSILVER_U, RomKind.BLACK2_U, RomKind.WHITE2_U).all { RomFormSprites.supports(it) })
            assertFalse(RomFormSprites.done(dir, RomKind.HEARTGOLD_U), "a HeartGold cache from before is read again for its forms")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the archives are where the randomizer's ini files put the species`() {
        val gen4 = File("../engine-zx/src/com/dabomstew/pkrandomzx/config/gen4_offsets.ini")
        val gen5 = File(gen4.path.replace("gen4_offsets", "gen5_offsets"))
        fun file(ini: File, section: String, what: String): String {
            val lines = ini.readLines(); val start = lines.indexOf("[$section]")
            return lines.drop(start + 1).takeWhile { !it.startsWith("[") }.first { it.startsWith("File<$what>=") }
                .substringAfter("=<").substringBefore(",").trim()
        }
        assertEquals(file(gen5, "Black 2 (U)", "PokemonGraphics"), RomFormSprites.B2W2_PICTURES)
        assertEquals(file(gen5, "Black 2 (U)", "PokemonStats"), RomFormSprites.B2W2_PERSONAL)
        // pl_otherpoke.narc sits beside the species' archive, as the game's code names them (NARC_INDEX_POKETOOL__POKEGRA__).
        assertEquals(file(gen4, "Platinum (U)", "PokemonGraphics").replace("pl_pokegra", "pl_otherpoke"), RomFormSprites.PL_OTHERPOKE)
    }

    /** The playui package is changing these files too: one line each, the logic in RomFormSprites. */
    @Test
    fun `both DS cards ask for the tracked Pokemon's form in one line, and the decode pass reads the forms`() {
        val panel = src("NdsTrackerPanel.kt")
        assertTrue("    val sprite = remember(m.species, m.shiny, m.form) { RomFormSprites.sprite(context, m.species, m.form, m.shiny) ?: PcAssets.dsSprite(context, m.species, m.shiny) }\n" in panel)
        assertTrue("    val sprite = remember(e.mon.species, e.mon.form) { RomFormSprites.sprite(context, e.mon.species, e.mon.form, false) ?: PcAssets.dsSprite(context, e.mon.species, false) }\n" in panel)
        val decode = src("RomSprites.kt").substringAfter("fun decodeAll(")
        assertTrue("File(dir, \"1.png\").exists() && RomFormSprites.done(dir, kind)) return 0" in decode, "a cache from before the forms gets them")
        assertTrue("written += RomFormSprites.decodeAll(nds, dir, kind, ::writePng)" in decode)
    }
}
