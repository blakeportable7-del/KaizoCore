package com.ironmonone.app

import com.ironmonone.core.PatchFormat
import com.ironmonone.core.RomKind
import com.ironmonone.patch.GbaHeader
import com.ironmonone.patch.RomIdentity
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Pokemon Heart & Soul button's flow (HnsSetup, 2026-10-05): every wrong input and the happy path, on small files
 * made here with checksums the flow is handed (Crcs), then once on the real files when they are on this PC: the
 * player's Emerald (.vendor/roms/emerald-u.gba), the team's pokemonHnS_v2.0.6.ups (.vendor/hns) and KaizoCore's own
 * bundled comfort patch, which must make 01713508 and then 949DBE42.
 */
class HnsSetupTest {
    // ------------------------------------------------------------------ small files and patches

    private fun crc(b: ByteArray): Long = CRC32().also { it.update(b) }.value

    private val emerald = ByteArray(4096) { (it * 7 + 3).toByte() }
    private val official = ByteArray(8192) { (it * 13 + 1).toByte() }
    private val kaizo = ByteArray(8192) { (it * 13 + 1 + (if (it % 97 == 0) 5 else 0)).toByte() }
    private val crcs = HnsSetup.Crcs(crc(emerald), crc(official), crc(kaizo))

    private fun varint(b: ByteArrayOutputStream, value: Long) {
        var n = value
        while (true) {
            val x = n and 0x7f
            n = n shr 7
            if (n == 0L) { b.write((0x80L or x).toInt()); return }
            b.write(x.toInt()); n--
        }
    }

    private fun u32(b: ByteArrayOutputStream, v: Long) { for (i in 0..3) b.write(((v ushr (8 * i)) and 0xFF).toInt()) }

    /** A BPS that writes [target] out whole, carrying [source]'s and [target]'s checksums. */
    private fun bps(source: ByteArray, target: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        "BPS1".forEach { b.write(it.code) }
        varint(b, source.size.toLong()); varint(b, target.size.toLong()); varint(b, 0)
        varint(b, ((target.size - 1).toLong() shl 2) or 1)
        b.write(target)
        u32(b, crc(source)); u32(b, crc(target)); u32(b, crc(b.toByteArray()))
        return b.toByteArray()
    }

    /** A UPS from [source] to [target] (one XOR run over the whole target), as the team's GitHub patch is. */
    private fun ups(source: ByteArray, target: ByteArray): ByteArray {
        // Records: the gap since the last one, then XOR bytes, ended by a zero (the byte where the two agree).
        val out = ByteArrayOutputStream()
        "UPS1".forEach { out.write(it.code) }
        varint(out, source.size.toLong()); varint(out, target.size.toLong())
        var pos = 0
        var last = 0
        while (pos < target.size) {
            val s = if (pos < source.size) source[pos].toInt() and 0xFF else 0
            val x = (target[pos].toInt() and 0xFF) xor s
            if (x == 0) { pos++; continue }
            varint(out, (pos - last).toLong())
            while (pos < target.size) {
                val s2 = if (pos < source.size) source[pos].toInt() and 0xFF else 0
                val x2 = (target[pos].toInt() and 0xFF) xor s2
                if (x2 == 0) break
                out.write(x2); pos++
            }
            out.write(0); pos++
            last = pos
        }
        u32(out, crc(source)); u32(out, crc(target)); u32(out, crc(out.toByteArray()))
        return out.toByteArray()
    }

    /** An IPS that writes [bytes] at 0: it says nothing of what it is for or what it makes. */
    private fun ips(bytes: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        "PATCH".forEach { b.write(it.code) }
        b.write(0); b.write(0); b.write(0)
        b.write(bytes.size shr 8); b.write(bytes.size and 0xFF)
        b.write(bytes)
        "EOF".forEach { b.write(it.code) }
        return b.toByteArray()
    }

    private fun dir(): File = Files.createTempDirectory("hns-setup").toFile().apply { deleteOnExit() }
    private fun file(d: File, name: String, bytes: ByteArray) = File(d, name).apply { writeBytes(bytes) }

    private fun game(d: File, bytes: ByteArray, name: String = "my emerald.gba") =
        HnsSetup.Game(file(d, "pick-$name", bytes), name, crc(bytes), HnsSetup.startOf(crc(bytes), null, crcs))

    private fun patch(d: File, bytes: ByteArray, name: String = "pokemonHnS_v2.0.6.ups"): HnsSetup.Patch {
        val f = file(d, "patch-$name", bytes)
        return HnsSetup.Patch(f, name, HnsSetup.patchFormatOf(f, crcs))
    }

    private fun problem(block: () -> Unit): String = assertFailsWith<HnsSetup.Problem> { block() }.message!!

    // ------------------------------------------------------------------ step 1: the game

    @Test
    fun `the game is Emerald, an already patched 2_0_6 or the KaizoCore build, and nothing else`() {
        assertEquals(HnsSetup.Start.EMERALD, HnsSetup.startOf(crcs.emerald, null, crcs))
        assertEquals(HnsSetup.Start.OFFICIAL, HnsSetup.startOf(crcs.official, null, crcs))
        assertEquals(HnsSetup.Start.KAIZO, HnsSetup.startOf(crcs.kaizo, null, crcs))
        assertEquals(HnsSetup.WRONG_GAME, problem { HnsSetup.startOf(0x1234L, null, crcs) })
        val emeraldHeader = GbaHeader("POKEMON EMER", "BPEE", "01", 0, true)
        assertEquals(HnsSetup.OTHER_EMERALD, problem { HnsSetup.startOf(0x1234L, emeraldHeader, crcs) }, "a changed or other-release Emerald")
        val hnsHeader = GbaHeader("POKEMON HNS", "BPEE", "01", 0, true)
        assertEquals(HnsSetup.OTHER_HNS, problem { HnsSetup.startOf(0x1234L, hnsHeader, crcs) }, "Heart & Soul, but not 2.0.6")
        val fireRed = GbaHeader("POKEMON FIRE", "BPRE", "01", 1, true)
        assertEquals(HnsSetup.WRONG_GAME, problem { HnsSetup.startOf(0x1234L, fireRed, crcs) })
        // The real checksums by default: Emerald (USA), the official 2.0.6 and the KaizoCore build.
        assertEquals(HnsSetup.Start.EMERALD, HnsSetup.startOf(0x1F1C08FBL, null))
        assertEquals(HnsSetup.Start.OFFICIAL, HnsSetup.startOf(0x01713508L, null))
        assertEquals(HnsSetup.Start.KAIZO, HnsSetup.startOf(0x949DBE42L, null))
    }

    @Test
    fun `only Emerald needs the patch, and Patch it waits for what is missing`() {
        val d = dir()
        assertTrue(HnsSetup.needsPatch(HnsSetup.Start.EMERALD))
        assertFalse(HnsSetup.needsPatch(HnsSetup.Start.OFFICIAL))
        assertFalse(HnsSetup.needsPatch(HnsSetup.Start.KAIZO))
        assertEquals(HnsSetup.PICK_GAME_FIRST, HnsSetup.waitingFor(null, null))
        assertFalse(HnsSetup.canPatch(null, null))
        val g = game(d, emerald)
        assertEquals(HnsSetup.PICK_PATCH_FIRST, HnsSetup.waitingFor(g, null))
        assertFalse(HnsSetup.canPatch(g, null))
        val p = patch(d, ups(emerald, official))
        assertNull(HnsSetup.waitingFor(g, p))
        assertTrue(HnsSetup.canPatch(g, p))
        assertTrue(HnsSetup.canPatch(game(d, official, "hns.gba"), null), "a 2.0.6 copy needs no patch")
    }

    // ------------------------------------------------------------------ step 2: the patch

    @Test
    fun `a file that is not a patch, a patch for another game and one that is not 2_0_6 are each told so`() {
        val d = dir()
        assertEquals(HnsSetup.NOT_A_PATCH, problem { HnsSetup.patchFormatOf(file(d, "notes.txt", "hello, world".toByteArray()), crcs) })
        assertEquals(HnsSetup.NOT_A_PATCH, problem { HnsSetup.patchFormatOf(file(d, "emerald.gba", emerald), crcs) }, "a game is not a patch")
        val otherGame = ByteArray(4096) { (it * 5).toByte() }
        assertEquals(HnsSetup.PATCH_OTHER_GAME, problem { HnsSetup.patchFormatOf(file(d, "radical.ups", ups(otherGame, official)), crcs) })
        val notOfficial = ByteArray(8192) { (it * 3).toByte() }
        assertEquals(HnsSetup.NOT_206, problem { HnsSetup.patchFormatOf(file(d, "hns-1.2.1.ups", ups(emerald, notOfficial)), crcs) })
        assertEquals(HnsSetup.NOT_206, problem { HnsSetup.patchFormatOf(file(d, "hns.bps", bps(emerald, notOfficial)), crcs) })
        // Our comfort patch picked by mistake: it starts from 2.0.6, not from Emerald.
        assertEquals(HnsSetup.NOT_206, problem { HnsSetup.patchFormatOf(file(d, "hns-kaizo.bps", bps(official, kaizo)), crcs) })
        assertEquals(PatchFormat.UPS, HnsSetup.patchFormatOf(file(d, "pokemonHnS_v2.0.6.ups", ups(emerald, official)), crcs))
        assertEquals(PatchFormat.BPS, HnsSetup.patchFormatOf(file(d, "a.bps", bps(emerald, official)), crcs))
        // An IPS (or an xdelta) says neither: it is checked by what it makes.
        assertEquals(PatchFormat.IPS, HnsSetup.patchFormatOf(file(d, "a.ips", ips(byteArrayOf(1, 2, 3))), crcs))
        assertEquals(HnsSetup.PATCH_UNCHECKED, HnsSetup.patchLine(PatchFormat.IPS))
        assertEquals(HnsSetup.PATCH_OK, HnsSetup.patchLine(PatchFormat.UPS))
    }

    // ------------------------------------------------------------------ step 3: patch

    @Test
    fun `Emerald and the 2_0_6 patch make both games, and both land in the Library once`() {
        val d = dir()
        val work = File(d, "work")
        val comfort = file(d, "hns-kaizo.bps", bps(official, kaizo))
        val g = game(d, emerald)
        val made = HnsSetup.make(g, patch(d, ups(emerald, official)), comfort, work, crcs)
        assertEquals(crcs.official, crc(made.official!!.readBytes()))
        assertEquals(crcs.kaizo, crc(made.kaizo.readBytes()))
        assertFalse(g.file.exists(), "the flow's own copy of the Emerald is not kept once both are made")
        val library = LibraryStore(File(d, "library"))
        val added = HnsSetup.addToLibrary(library, made, g.name, "pokemonHnS_v2.0.6.ups")
        assertEquals(HnsSetup.OFFICIAL_FILE, added.official!!.name)
        assertEquals(HnsSetup.KAIZO_FILE, added.kaizo.name)
        assertEquals(setOf(crcs.official, crcs.kaizo), library.list().map { it.crc }.toSet())
        assertFalse(added.officialWasThere || added.kaizoWasThere)
        assertTrue(HnsSetup.doneLine(added).startsWith("Done."))
        // Again: nothing is added twice, and it says so.
        val again = HnsSetup.addToLibrary(library, HnsSetup.make(game(d, emerald), patch(d, ups(emerald, official)), comfort, work, crcs), g.name, null)
        assertTrue(again.officialWasThere && again.kaizoWasThere)
        assertEquals(2, library.list().size)
        assertTrue(HnsSetup.doneLine(again).startsWith("Already in your library"))
    }

    @Test
    fun `a 2_0_6 copy needs only the comfort patch, and the KaizoCore build needs nothing`() {
        val d = dir()
        val comfort = file(d, "hns-kaizo.bps", bps(official, kaizo))
        val made = HnsSetup.make(game(d, official, "hns.gba"), null, comfort, File(d, "work"), crcs)
        assertEquals(crcs.official, crc(made.official!!.readBytes()))
        assertEquals(crcs.kaizo, crc(made.kaizo.readBytes()))
        val already = HnsSetup.make(game(d, kaizo, "kaizo.gba"), null, null, File(d, "work"), crcs)
        assertNull(already.official)
        assertEquals(crcs.kaizo, crc(already.kaizo.readBytes()))
    }

    @Test
    fun `a patch that does not give 2_0_6 is refused with its checksum, and nothing is kept`() {
        val d = dir()
        val work = File(d, "work")
        val comfort = file(d, "hns-kaizo.bps", bps(official, kaizo))
        val g = game(d, emerald)
        val wrong = file(d, "other.ips", ips(byteArrayOf(9, 9, 9)))
        val msg = problem { HnsSetup.make(g, HnsSetup.Patch(wrong, "other.ips", PatchFormat.IPS), comfort, work, crcs) }
        assertTrue(msg.startsWith(HnsSetup.NOT_206), msg)
        assertTrue("%08X".format(crc(ips(byteArrayOf(9, 9, 9)).let { Ips_apply(it, emerald) })) in msg, msg)
        assertTrue(g.file.exists(), "the picked game stays for another try")
        assertTrue(work.listFiles().orEmpty().isEmpty(), "nothing half made is left: ${work.listFiles()?.map { it.name }}")
        // A UPS for another game, applied anyway: the patch says it is not for this file.
        val otherGame = ByteArray(4096) { (it * 5).toByte() }
        val other = file(d, "radical.ups", ups(otherGame, official))
        assertEquals(HnsSetup.PATCH_OTHER_GAME, problem { HnsSetup.make(g, HnsSetup.Patch(other, "radical.ups", PatchFormat.UPS), comfort, work, crcs) })
        // A damaged UPS (its own checksum broken).
        val broken = ups(emerald, official).also { it[10] = (it[10] + 1).toByte() }
        assertEquals(HnsSetup.DAMAGED_PATCH, problem { HnsSetup.make(g, HnsSetup.Patch(file(d, "broken.ups", broken), "broken.ups", PatchFormat.UPS), comfort, work, crcs) })
    }

    @Test
    fun `a comfort patch that is missing or makes the wrong build is KaizoCore's fault, and the Emerald is kept`() {
        val d = dir()
        val work = File(d, "work")
        val g = game(d, emerald)
        val p = patch(d, ups(emerald, official))
        assertEquals(HnsSetup.COMFORT_FAILED, problem { HnsSetup.make(g, p, null, work, crcs) })
        val wrongComfort = file(d, "hns-kaizo.bps", bps(official, emerald + official))
        assertEquals(HnsSetup.COMFORT_FAILED, problem { HnsSetup.make(g, p, wrongComfort, work, crcs) })
        assertTrue(g.file.exists(), "the picked game stays for another try")
        assertTrue(work.listFiles().orEmpty().isEmpty(), "the 2.0.6 made on the way is not left behind")
    }

    // ------------------------------------------------------------------ after an update (2026-10-06)

    /** The KaizoCore build as an older release made it: another comfort patch on the same 2.0.6. */
    private val olderKaizo = ByteArray(8192) { (it * 13 + 1 + (if (it % 89 == 0) 3 else 0)).toByte() }
    private val crcsAfterUpdate = crcs.copy(older = { it == crc(olderKaizo) })

    @Test
    fun `an older KaizoCore's build is named as that in step 1, never as another Heart & Soul`() {
        assertEquals(HnsSetup.OLDER_KAIZO, problem { HnsSetup.startOf(crc(olderKaizo), GbaHeader("POKEMON HNS", "BPEE", "01", 0, true), crcsAfterUpdate) })
        // The real list: rc36's build is one rc36.1 knows.
        assertEquals(HnsSetup.OLDER_KAIZO, problem { HnsSetup.startOf(0xC993EB6EL, null) })
        assertEquals(HnsSetup.Start.KAIZO, HnsSetup.startOf(RomKind.HEARTSOUL_KAIZO_206.expectedCrc, null))
    }

    @Test
    fun `after an update the KaizoCore build is made again from the library's 2_0_6, with no step for the player`() {
        val d = dir()
        val library = LibraryStore(File(d, "library"))
        library.import(HnsSetup.OFFICIAL_FILE, official)
        library.import(HnsSetup.KAIZO_FILE, olderKaizo)
        val comfort = file(d, "hns-kaizo.bps", bps(official, kaizo))
        assertEquals(HnsSetup.Refresh.MADE, HnsSetup.refresh(library, { comfort }, File(d, "work"), crcsAfterUpdate))
        val byCrc = library.list().associateBy { it.crc }
        assertEquals(HnsSetup.KAIZO_FILE, byCrc.getValue(crcs.kaizo).name, "this release's build takes the usual name")
        assertEquals(HnsSetup.OLDER_FILE_STEM + ".gba", byCrc.getValue(crc(olderKaizo)).name, "the older one is kept, named as older")
        assertContentEquals(official, byCrc.getValue(crcs.official).file.readBytes(), "the library's 2.0.6 is not touched")
        assertTrue(File(d, "work").listFiles().orEmpty().none { it.name.startsWith("hns-refresh") }, "the working copy goes")
        // Once is enough.
        assertEquals(HnsSetup.Refresh.NOTHING, HnsSetup.refresh(library, { comfort }, File(d, "work"), crcsAfterUpdate))
        assertEquals(3, library.list().size)
    }

    @Test
    fun `with no 2_0_6 in the library the player is asked, and a library with no KaizoCore build is left alone`() {
        val d = dir()
        val comfort = file(d, "hns-kaizo.bps", bps(official, kaizo))
        val only = LibraryStore(File(d, "only-older")).apply { import(HnsSetup.KAIZO_FILE, olderKaizo) }
        assertEquals(HnsSetup.Refresh.NEEDS_OFFICIAL, HnsSetup.refresh(only, { comfort }, File(d, "work"), crcsAfterUpdate))
        assertEquals(1, only.list().size)
        val none = LibraryStore(File(d, "none")).apply { import(HnsSetup.OFFICIAL_FILE, official) }
        assertEquals(HnsSetup.Refresh.NOTHING, HnsSetup.refresh(none, { fail("nothing to make") }, File(d, "work"), crcsAfterUpdate))
        // A comfort patch that will not apply leaves the library as it was.
        val broken = LibraryStore(File(d, "broken")).apply { import(HnsSetup.OFFICIAL_FILE, official); import(HnsSetup.KAIZO_FILE, olderKaizo) }
        assertFailsWith<HnsSetup.Problem> { HnsSetup.refresh(broken, { null }, File(d, "work"), crcsAfterUpdate) }
        assertEquals(setOf(HnsSetup.OFFICIAL_FILE, HnsSetup.KAIZO_FILE), broken.list().map { it.name }.toSet())
    }

    @Test
    fun `step 1 offers the library's copies of the three starting points, Emerald first`() {
        val d = dir()
        val library = LibraryStore(File(d, "library"))
        library.import("hns.gba", official)
        library.import("emerald.gba", emerald)
        library.import("other.gba", ByteArray(100) { 1 })
        val offered = HnsSetup.libraryGames(library.list(), crcs)
        assertEquals(listOf(HnsSetup.Start.EMERALD, HnsSetup.Start.OFFICIAL), offered.map { it.second })
        assertEquals(listOf("emerald.gba", "hns.gba"), offered.map { it.first.name })
    }

    @Test
    fun `every word follows the copy rules and the credit names the team`() {
        val em = 0x2014.toChar()
        val all = HnsSetup.allCopy + listOf(HomeMode.HEARTSOUL.title, HomeMode.HEARTSOUL.line) + HnsPool.LABELS.map { it.second } + HnsPool.LINE
        for (line in all) {
            assertFalse(em in line, "an em dash in: $line")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(line), "AI in: $line")
            assertFalse(Regex("(?i)donat|\\bpay\\b|price|\\$").containsMatchIn(line), "money in: $line")
        }
        assertTrue("Lil Dill" in HnsSetup.CREDIT && "pokeemerald-expansion" in HnsSetup.CREDIT)
        assertTrue("does not include their patch or any game" in HnsSetup.CREDIT)
        assertEquals("https://github.com/PokemonHnS-Development/pokehns-expansion/releases/tag/Release-v2.0.6", HnsSetup.GITHUB_URL)
        assertEquals("https://www.hackdex.app/hack/pokemon-heart-and-soul", HnsSetup.HACKDEX_URL)
        assertEquals("Pokémon Heart & Soul 2.0.6.gba", HnsSetup.OFFICIAL_FILE)
        assertEquals("Pokémon Heart & Soul (KaizoCore).gba", HnsSetup.KAIZO_FILE)
    }

    @Test
    fun `KaizoCore ships its own comfort patch and never theirs`() {
        val patches = File("src/main/assets/patches")
        val comfort = File(patches, HnsSetup.COMFORT_PATCH)
        assertTrue(comfort.isFile)
        val info = com.ironmonone.patch.Bps.info(comfort.readBytes())
        assertTrue(info.patchIntact)
        assertEquals(RomKind.HEARTSOUL_206.expectedCrc, info.sourceCrc, "made from the official 2.0.6")
        assertEquals(RomKind.HEARTSOUL_KAIZO_206.expectedCrc, info.targetCrc, "makes the KaizoCore build")
        // No patch in the APK starts from Emerald and makes 2.0.6: that one is the team's, downloaded by the player.
        for (f in patches.listFiles().orEmpty()) {
            val head = com.ironmonone.patch.Patcher.head(f, 16)
            val fmt = com.ironmonone.patch.Patcher.detect(head)
            if (fmt == PatchFormat.UPS || fmt == PatchFormat.BPS) {
                val b = f.readBytes()
                val target = (b[b.size - 8].toLong() and 0xFF) or ((b[b.size - 7].toLong() and 0xFF) shl 8) or
                    ((b[b.size - 6].toLong() and 0xFF) shl 16) or ((b[b.size - 5].toLong() and 0xFF) shl 24)
                assertTrue(target != RomKind.HEARTSOUL_206.expectedCrc, "${f.name} makes the official game")
            }
        }
    }

    // ------------------------------------------------------------------ the real files, when they are on this PC

    /** [name] in IRONMON_ROMS (or IRONMON_HNS for the .ups), else in this checkout's or the main checkout's .vendor. */
    private fun vendor(sub: String, name: String): File? {
        val env = if (sub == "hns") System.getenv("IRONMON_HNS") else System.getenv("IRONMON_ROMS")
        val candidates = ArrayList<File>()
        env?.let { candidates += File(it, name) }
        var d: File? = File("").absoluteFile
        while (d != null) {
            candidates += File(d, ".vendor/$sub/$name")
            val git = File(d, ".git")
            if (git.isFile) git.readText().substringAfter("gitdir:").trim().substringBefore("/.git/").let { candidates += File(it, ".vendor/$sub/$name") }
            d = d.parentFile
        }
        val f = candidates.firstOrNull { it.isFile }
        if (f == null && Dumps.required) throw AssertionError("IRONMON_REQUIRE_DUMPS: $name is not in .vendor/$sub, and the release gate needs it")
        return f
    }

    @Test
    fun `the player's Emerald and the team's 2_0_6 patch make 01713508, and the comfort patch 949DBE42`() {
        val emeraldFile = vendor("roms", "emerald-u.gba") ?: return println("HnsSetupTest skipped: no emerald-u.gba")
        val upsFile = vendor("hns", "pokemonHnS_v2.0.6.ups") ?: return println("HnsSetupTest skipped: no pokemonHnS_v2.0.6.ups")
        val d = dir()
        val pick = File(d, "pick.gba").also { emeraldFile.copyTo(it) }
        val id = RomIdentity.identify(pick)
        assertEquals(RomKind.EMERALD_U, id.kind)
        val g = HnsSetup.Game(pick, "Pokemon Emerald (USA).gba", id.crc, HnsSetup.startOf(id.crc, id.header))
        assertEquals(HnsSetup.Start.EMERALD, g.start)
        val patchCopy = File(d, "pokemonHnS_v2.0.6.ups").also { upsFile.copyTo(it) }
        val p = HnsSetup.Patch(patchCopy, patchCopy.name, HnsSetup.patchFormatOf(patchCopy))
        assertEquals(PatchFormat.UPS, p.format)
        val made = HnsSetup.make(g, p, File("src/main/assets/patches/${HnsSetup.COMFORT_PATCH}"), File(d, "work"))
        val official = RomIdentity.identify(made.official!!)
        assertEquals(0x01713508L, official.crc)
        assertEquals(RomKind.HEARTSOUL_206, official.kind)
        val kaizo = RomIdentity.identify(made.kaizo)
        assertEquals(0x949DBE42L, kaizo.crc)
        assertEquals(RomKind.HEARTSOUL_KAIZO_206, kaizo.kind)
        // Into a Library: the official plays without a tracker, the KaizoCore build is tracked and Kaizo IronMON's.
        val library = LibraryStore(File(d, "library"), savesDir = null)
        val added = HnsSetup.addToLibrary(library, made, g.name, p.name)
        assertTrue(added.official!!.playOnly && !added.official!!.tracked)
        assertTrue(added.kaizo.tracked && !added.kaizo.playOnly)
        assertNull(GameSession.forLibrary(added.official!!)!!.kind, "no tracker on the official game")
        assertEquals(RomKind.HEARTSOUL_KAIZO_206, GameSession.forLibrary(added.kaizo)!!.kind)
        d.deleteRecursively()
    }

    @Test
    fun `the Emerald dump with a patch for another game is refused before anything is made`() {
        val emeraldFile = vendor("roms", "emerald-u.gba") ?: return println("HnsSetupTest skipped: no emerald-u.gba")
        val id = RomIdentity.identify(emeraldFile)
        assertEquals(HnsSetup.Start.EMERALD, HnsSetup.startOf(id.crc, id.header))
        // The comfort patch, which is for 2.0.6, picked as step 2's patch.
        val msg = assertFailsWith<HnsSetup.Problem> { HnsSetup.patchFormatOf(File("src/main/assets/patches/${HnsSetup.COMFORT_PATCH}")) }.message
        assertEquals(HnsSetup.NOT_206, msg)
        // A FireRed dump picked as the game.
        vendor("roms", "firered-u-v11.gba")?.let { fr ->
            val r = RomIdentity.identify(fr)
            assertEquals(HnsSetup.WRONG_GAME, assertFailsWith<HnsSetup.Problem> { HnsSetup.startOf(r.crc, r.header) }.message)
        }
    }

    private fun Ips_apply(patch: ByteArray, source: ByteArray): ByteArray = com.ironmonone.patch.Ips.apply(patch, source)
}
