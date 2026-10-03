package com.ironmonone.app

import android.content.ContextWrapper
import com.ironmonone.core.PatchFormat
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Bps
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

/**
 * Making MaxDex 1.0 and what the app around Play says and offers: Prepare, the library, the hack list and what stays
 * off in a first version (the rules are MaxDexRulesTest's). Trip's MaxDex.bps (25.7 MB) is built into the app since
 * rc34, as assets/patches/maxdex-firered-u-v11.bps (Blake, 2026-10-03: "yes"), and a copy a player added to the library
 * before then is the same file. KaizoCore knows it by the two checksums its footer carries (FireRed USA 1.1 in,
 * MaxDex 1.0 out), not by its name. Most tests here make small stand-ins with those checksums (four bytes at the end of
 * each file are solved for to set its CRC-32), so the whole path runs without the real patch or a dump; the real patch
 * is read where it lies in the assets, and applied to the real FireRed when IRONMON_ROMS holds one.
 */
class MaxDexPrepareTest {
    private fun tmp(): File = Files.createTempDirectory("maxdex").toFile()

    /**
     * A context that is only a cache folder: PrepRun.run asks it for nothing else on this path but the APK's assets, and
     * this one has no APK behind it, so the app's copy of the patch cannot be unpacked from it, as on a full phone.
     */
    private fun context(cache: File) = object : ContextWrapper(null) { override fun getCacheDir(): File = cache }

    /** The bundled patch the MaxDex option names. */
    private val asset: String get() = PrepOptions.forKind(RomKind.FIRERED_U_V11).single { it.mode == PrepOptions.Mode.MAXDEX }.asset!!

    /** The app's copy of the patch as Prepare leaves it once unpacked from the APK (PrepStore.bundledPatch): whole and marked. */
    private fun unpacked(files: File, bytes: ByteArray): File = BundledCopy.copyWhole(File(files, "prep/patches/$asset")) { it.write(bytes) }!!

    private fun sha256(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val table = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }

    private fun crc(b: ByteArray): Long = CRC32().apply { update(b) }.value

    /** [data] and four bytes after it that make the whole CRC-32 [want]. CRC-32 is linear, so the four can be solved for. */
    private fun withCrc(data: ByteArray, want: Long): ByteArray {
        var reg = 0xFFFFFFFFL
        for (b in data) reg = (reg ushr 8) xor table[((reg xor b.toLong()) and 0xFF).toInt()]
        // Back from the register [want] needs: each of the four steps used the one table entry whose top byte matches.
        val idx = IntArray(4)
        var r = want xor 0xFFFFFFFFL
        for (k in 3 downTo 0) {
            idx[k] = (0 until 256).first { (table[it] ushr 24) == (r ushr 24) }
            r = ((r xor table[idx[k]]) shl 8) and 0xFFFFFFFFL
        }
        val tail = ByteArray(4)
        for (k in 0 until 4) {
            tail[k] = ((reg xor idx[k].toLong()) and 0xFF).toByte()
            reg = (reg ushr 8) xor table[idx[k]]
        }
        return (data + tail).also { assertEquals(want, crc(it), "the four bytes set the checksum") }
    }

    /** A BPS patch that writes [target] out whole, its footer carrying the checksums of [source] and [target]. */
    private fun bps(source: ByteArray, target: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        fun varint(value: Long) {
            var n = value
            while (true) {
                val x = n and 0x7f
                n = n shr 7
                if (n == 0L) { b.write((0x80L or x).toInt()); return }
                b.write(x.toInt()); n--
            }
        }
        fun u32(v: Long) { for (i in 0..3) b.write(((v ushr (8 * i)) and 0xFF).toInt()) }
        "BPS1".forEach { b.write(it.code) }
        varint(source.size.toLong()); varint(target.size.toLong()); varint(0)
        varint(((target.size - 1).toLong() shl 2) or 1)   // TargetRead: the whole target
        b.write(target)
        u32(crc(source)); u32(crc(target))
        u32(crc(b.toByteArray()))
        return b.toByteArray()
    }

    private val fireRed = withCrc(ByteArray(4096) { (it * 7).toByte() }, RomKind.FIRERED_U_V11.expectedCrc)
    private val maxDex = withCrc(ByteArray(6000) { (it * 13 + 1).toByte() }, RomKind.FIRERED_MAXDEX_10.expectedCrc)
    private fun notMaxDex() = withCrc(ByteArray(64) { it.toByte() }, 0x12345678L)

    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    // ------------------------------------------------------------------ the patch and Prepare

    @Test
    fun `Trip's patch is known by the checksums it carries, under any name, and only then`() {
        assertEquals(RomKind.FIRERED_U_V11.expectedCrc, MaxDexInfo.PATCH_FROM)
        assertEquals(RomKind.FIRERED_MAXDEX_10.expectedCrc, MaxDexInfo.PATCH_TO)
        val store = PrepStore(tmp())
        val trips = store.library.importPatch("my download.bps", bps(fireRed, maxDex))
        assertEquals(PatchFormat.BPS, trips.format)
        assertEquals(MaxDexInfo.PATCH_FROM to MaxDexInfo.PATCH_TO, trips.forCrc to trips.makesCrc)
        assertTrue(MaxDexInfo.isPatch(trips))
        // Called MaxDex.bps, for the same FireRed, making something else: not Trip's.
        assertFalse(MaxDexInfo.isPatch(store.library.importPatch("MaxDex.bps", bps(fireRed, notMaxDex()))))
        // Read back from the library as it lists them.
        assertEquals(1, store.library.listPatches().count { MaxDexInfo.isPatch(it) })
    }

    @Test
    fun `MaxDex is offered last on FireRed 1_1 only, and the build itself is stored as it is`() {
        val o = PrepOptions.forKind(RomKind.FIRERED_U_V11).last()
        assertEquals(PrepOptions.Mode.MAXDEX, o.mode)
        assertEquals(RomKind.FIRERED_MAXDEX_10, o.out)
        assertEquals(RomKind.FIRERED_MAXDEX_10.id, o.id)
        assertEquals("maxdex-${RomKind.FIRERED_U_V11.id}.bps", o.asset, "Trip's patch, bundled, named as every bundled patch is")
        assertEquals(MaxDexInfo.LABEL, o.label)
        assertEquals(MaxDexInfo.SHORT, PrepOptions.describe(o))
        assertEquals(PrepOptions.Mode.STANDARD, PrepOptions.default(RomKind.FIRERED_U_V11).mode, "Prepare still opens on Standard")
        assertEquals(listOf(RomKind.FIRERED_U_V11.id), RomKind.all.filter { k -> PrepOptions.forKind(k).any { it.mode == PrepOptions.Mode.MAXDEX } }.map { it.id })
        assertTrue(PrepRun.builtIns(RomKind.FIRERED_U_V11).any { it.mode == PrepOptions.Mode.MAXDEX }, "the library's PATCH offers it too")
        assertEquals(listOf(PrepOptions.Mode.STANDARD), PrepOptions.forKind(RomKind.FIRERED_MAXDEX_10).map { it.mode })
        assertEquals(listOf("Already a MaxDex build. Stored as is, ready to randomize."), PrepPlan.lines(RomKind.FIRERED_MAXDEX_10))
        assertTrue(PrepPlan.lines(RomKind.FIRERED_U_V11).any { it.startsWith("MaxDex: ") })
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).none { "MaxDex" in it })
        // Prepare says what MaxDex is before anything is made.
        val prepare = src("PrepareScreen.kt")
        assertTrue("if (picked.mode == PrepOptions.Mode.MAXDEX) {" in prepare && "MaxDexInfo.lines.forEach" in prepare)
        val words = listOf(MaxDexInfo.TITLE, MaxDexInfo.WHAT, MaxDexInfo.LABEL, MaxDexInfo.CREDIT, MaxDexInfo.SHORT,
            MaxDexInfo.NEED_PATCH, MaxDexInfo.PATCH_ADDED) + MaxDexInfo.lines + PrepPlan.lines(RomKind.FIRERED_U_V11)
        for (w in words) assertTrue('—' !in w && '–' !in w && !Regex("\\bAI\\b").containsMatchIn(w), w)
    }

    /**
     * The patch is in the app (Blake, 2026-10-03), so nothing the player reads sends them to get Trip's file or to add
     * it: not the Prepare lines, the PATCH window's rows, the hack list, the credits, the release notes or the help.
     */
    @Test
    fun `no text sends the player to get or add Trip's patch, and each says it is built in`() {
        val told = listOf(MaxDexInfo.CREDIT, MaxDexInfo.SHORT, MaxDexInfo.NEED_PATCH, MaxDexInfo.PATCH_ADDED) + MaxDexInfo.lines +
            PrepPlan.lines(RomKind.FIRERED_U_V11) + HackLinks.all.single { it.name == MaxDexInfo.TITLE }.blurb
        val about = src("AboutScreen.kt")
        val notes = listOf("../docs/RELEASE-NOTES.md", "../docs/wiki/Release-notes.md").map { File(it).readText() }
        val help = File("../docs/wiki").listFiles { f -> f.extension == "md" }!!.map { it.readText() } + File("../README.md").readText()
        val sendsForIt = Regex("(?i)download MaxDex|add (Trip's )?MaxDex\\.bps|add it to your library|which you add|your own copy of Trip|not in the app|is not bundled")
        for (t in told + about + notes + help) assertNull(sendsForIt.find(t)?.value, t.take(80))
        assertTrue("built in" in MaxDexInfo.SHORT && "built into KaizoCore" in MaxDexInfo.lines[3], "Prepare and the PATCH window say so")
        assertTrue("Its patch is Trip's too, and is in the app unchanged." in about)
        for (n in notes) assertTrue("It is built in: tap Patch on your FireRed 1.1 in the Library and pick MaxDex." in n)
        assertTrue("KaizoCore already includes it." in HackLinks.all.single { it.name == MaxDexInfo.TITLE }.blurb)
        assertTrue(help.any { "| MaxDex 1.0 | FireRed v1.1 |" in it }, "the wiki lists it with the patched versions that come with the app")
    }

    // ------------------------------------------------------------------ the patch in the app

    /** PatcherTest's way with the Nat. Dex patch: the footer read, no ROM needed. */
    @Test
    fun `the patch built into the app is Trip's MaxDex 1_0, by the checksums its footer carries`() {
        assertEquals("maxdex-firered-u-v11.bps", asset)
        val f = File("src/main/assets/patches/$asset")
        assertTrue(f.isFile, "${f.path} is bundled")
        val bytes = f.readBytes()
        val info = Bps.info(bytes)
        assertTrue(info.patchIntact, "whole: its own checksum holds")
        assertEquals(RomKind.FIRERED_U_V11.expectedCrc, info.sourceCrc, "FireRed (USA) 1.1 in")
        assertEquals(RomKind.FIRERED_MAXDEX_10.expectedCrc, info.targetCrc, "MaxDex 1.0 out")
        assertEquals(16L shl 20, info.sourceSize)
        assertEquals(32L shl 20, info.targetSize)
        // Trip's file byte for byte (commit 73bb022, 2026-06-25), the one NOTICE and engine-maxdex/PINNED.txt pin.
        assertEquals(25_761_384, bytes.size)
        assertEquals("65d70b582f14b493863d2c31d1f668301dc370bf46ece9f40ecaf28e94b15057", sha256(bytes))
        // Known as Trip's the way the library knows any copy of it.
        val peek = PrepStore(tmp()).library.peekPatch(bytes)!!
        assertTrue(MaxDexInfo.isPatch(LibraryStore.PatchEntry(f, f.name, peek.format, peek.forCrc, peek.makesCrc, null)))
    }

    /**
     * The bundled patch on the real FireRed 1.1 gives MaxDex 1.0, the build every MaxDex test reads. In memory: the dump
     * is read where it lies and nothing is written. Needs IRONMON_ROMS holding firered-u-v11.gba; skipped without it.
     */
    @Test
    fun `the bundled patch makes MaxDex 1_0 from the real FireRed 1_1`() {
        val rom = Dumps.rom("firered-u-v11.gba") ?: return println("MaxDexPrepareTest skipped: set IRONMON_ROMS (firered-u-v11.gba)")
        val out = com.ironmonone.patch.Patcher.apply(File("src/main/assets/patches/$asset").readBytes(), rom.readBytes(), "FireRed")
        assertEquals("28c12926", "%08x".format(crc(out)))
        assertEquals(RomKind.FIRERED_MAXDEX_10, com.ironmonone.patch.RomIdentity.identify(out).kind)
    }

    // ------------------------------------------------------------------ Prepare and PATCH

    /**
     * Library > FireRed (U) v1.1 > PATCH and Prepare both run PrepRun.run; what it makes is what the Kaizo IronMON
     * screen lists (PrepStore.listPrepared). Neither needs MaxDex.bps in the library now.
     */
    @Test
    fun `MaxDex 1_0 is made with the patch built into the app, with no MaxDex_bps in the library`() {
        val dir = tmp()
        val cache = File(dir, "cache").apply { mkdirs() }
        val files = File(dir, "files")
        val store = PrepStore(files)
        val copy = File(cache, "prep-copy").apply { writeBytes(fireRed) }
        val id = PrepOptions.forKind(RomKind.FIRERED_U_V11).single { it.mode == PrepOptions.Mode.MAXDEX }.id
        fun run() = PrepRun.run(context(cache), store, copy, RomKind.FIRERED_U_V11, id, FileProgress())

        // The app's copy could not be unpacked (here there is no APK) and the library holds none: said plainly, with
        // nothing made or spent.
        assertEquals(MaxDexInfo.NEED_PATCH, assertFailsWith<PrepFailure> { run() }.message)
        assertEquals(MaxDexInfo.NEED_PATCH, prepFailure(PrepFailure(MaxDexInfo.NEED_PATCH)), "said to the player as it is")
        // Another patch for the same FireRed does not count.
        store.library.importPatch("MaxDex.bps", bps(fireRed, notMaxDex()))
        assertEquals(MaxDexInfo.NEED_PATCH, assertFailsWith<PrepFailure> { run() }.message)
        assertTrue(copy.isFile, "the copy is not spent")
        assertTrue(store.listPrepared().none { it.first.isMaxDex })

        // The app's own copy, as Prepare unpacks it from the APK: MaxDex 1.0 is made and stored, and the copy is spent.
        val inApp = unpacked(files, bps(fireRed, maxDex))
        assertEquals("Made ${RomKind.FIRERED_MAXDEX_10.displayName} with Trip's patch.", run())
        assertFalse(copy.exists())
        val made = store.listPrepared().single { it.first.isMaxDex }
        assertEquals(RomKind.FIRERED_MAXDEX_10, made.first, "listed on Kaizo IronMON")
        assertContentEquals(maxDex, made.second.readBytes())
        assertTrue(store.library.listPatches().none { MaxDexInfo.isPatch(it) }, "with no MaxDex.bps in the library")
        assertTrue(BundledCopy.whole(inApp), "the app's copy stays, unpacked once")
        assertTrue(cache.listFiles()!!.isEmpty(), "nothing left in the cache")
    }

    /**
     * A player who added MaxDex.bps before it was built in keeps working: theirs is the same file, so it is used, the
     * app's copy is never unpacked beside it, and either one makes the one MaxDex 1.0, in the one place.
     */
    @Test
    fun `a MaxDex_bps added to the library before keeps working, and nothing is made twice`() {
        val dir = tmp()
        val cache = File(dir, "cache").apply { mkdirs() }
        val files = File(dir, "files")
        val store = PrepStore(files)
        val id = PrepOptions.forKind(RomKind.FIRERED_U_V11).single { it.mode == PrepOptions.Mode.MAXDEX }.id
        fun run() = PrepRun.run(context(cache), store, File(cache, "prep-copy").apply { writeBytes(fireRed) }, RomKind.FIRERED_U_V11, id, FileProgress())

        val theirs = store.library.importPatch("my download.bps", bps(fireRed, maxDex))
        // The player's copy is read first, so the app's is not unpacked beside it: an app copy put here that would make
        // something else is never read.
        unpacked(files, bps(fireRed, notMaxDex()))
        assertEquals("Made ${RomKind.FIRERED_MAXDEX_10.displayName} with Trip's patch.", run())
        val first = store.listPrepared().single { it.first.isMaxDex }
        assertContentEquals(maxDex, first.second.readBytes())
        assertEquals(listOf(theirs.file), store.library.listPatches().map { it.file }, "and the player's copy stays")

        // Without it, the app's copy makes the same build in the same place: still one MaxDex 1.0.
        store.library.deletePatch(theirs)
        unpacked(files, bps(fireRed, maxDex))
        run()
        val again = store.listPrepared().filter { it.first.isMaxDex }
        assertEquals(listOf(first.second), again.map { it.second })
        assertContentEquals(maxDex, again.single().second.readBytes())

        // A copy in the library that is damaged (its footer intact, its own checksum not) is passed over for the app's.
        val damaged = bps(fireRed, maxDex).also { it[20] = (it[20] + 1).toByte() }
        assertTrue(MaxDexInfo.isPatch(store.library.importPatch("MaxDex.bps", damaged)))
        assertEquals("Made ${RomKind.FIRERED_MAXDEX_10.displayName} with Trip's patch.", run())
        assertContentEquals(maxDex, store.listPrepared().single { it.first.isMaxDex }.second.readBytes())
    }

    // ------------------------------------------------------------------ the library

    @Test
    fun `the library lists and counts Trip's patch once on FireRed 1_1, as the MaxDex option`() {
        val store = PrepStore(tmp())
        val trips = store.library.importPatch("MaxDex.bps", bps(fireRed, maxDex))
        val other = LibraryStore.PatchEntry(File("x.ips"), "x.ips", PatchFormat.IPS, RomKind.FIRERED_U_V11.expectedCrc, null, "FireRed")
        val game = LibraryStore.Entry(File("fr.gba"), "fr.gba", RomKind.FIRERED_U_V11.expectedCrc, RomKind.FIRERED_U_V11, RomKind.FIRERED_U_V11.platform, "a game")
        assertTrue(trips.matches(game) && other.matches(game))
        assertEquals(listOf(other), MaxDexInfo.fitting(listOf(trips, other), game))
        // The card's count and the PATCH list both go through it.
        val lib = src("RomLibraryScreen.kt")
        assertTrue("patchCount = MaxDexInfo.fitting(patches, e).size + PrepRun.builtIns(e).size," in lib)
        assertTrue("val fits = MaxDexInfo.fitting(patches, e)" in lib)
    }

    @Test
    fun `adding Trip's patch says what it makes, and no other patch says so`() {
        val imp = LibraryImport(ContextWrapper(null), PrepStore(tmp()), FileProgress(), false)
        imp.begin(2)
        val line = imp.importOne("MaxDex.bps", bps(fireRed, maxDex))
        assertTrue(line.startsWith("Added patch MaxDex for "), line)
        assertTrue(line.endsWith(" " + MaxDexInfo.PATCH_ADDED), line)
        val plain = imp.importOne("Other.bps", bps(fireRed, notMaxDex()))
        assertTrue(plain.startsWith("Added patch Other for "), plain)
        assertFalse(MaxDexInfo.PATCH_ADDED in plain, plain)
    }

    // ------------------------------------------------------------------ what stays off, and the links

    @Test
    fun `no Nuzlocke on MaxDex in its first version, and the editor leaves Trip's file as he made it`() {
        val k = RomKind.FIRERED_MAXDEX_10
        val entry = LibraryStore.Entry(File("md.gba"), "md.gba", k.expectedCrc, k, k.platform, "a game")
        assertTrue(entry.verified)
        assertEquals(emptyList(), NuzlockeStarts.plainGames(listOf(entry)))
        assertEquals(emptyList(), NuzlockeStarts.randomGames(listOf(k to File("md.gba"))))
        assertEquals(listOf(RomKind.FIRERED_NATDEX_121), NuzlockeStarts.randomGames(listOf(RomKind.FIRERED_NATDEX_121 to File("nd.gba"), k to File("md.gba"))).map { it.first })
        assertTrue(RnqsInfo.of(File("src/main/assets/presets/FRLG MaxDex Kaizo.rnqs")).maxDex)
        assertTrue("is a MaxDex settings file. The editor does not change MaxDex files yet; " in src("EditorScreen.kt"))
    }

    @Test
    fun `the ROM hacks list links Trip's page for FireRed 1_1, and warns on any other copy`() {
        val link = HackLinks.all.single { it.name == "MaxDex Kaizo IronMON" }
        assertEquals(MaxDexInfo.REPO, link.url)
        assertEquals("https://github.com/Tripc423/Maxdex", link.url)
        assertEquals(setOf(RomKind.FIRERED_U_V11.id), link.ids)
        assertNull(HackLinks.mismatch(link, RomKind.FIRERED_U_V11))
        assertNotNull(HackLinks.mismatch(link, RomKind.FIRERED_U_V10))
        // A MaxDex copy is told what a Nat. Dex copy is told: the patch does not fit it.
        assertEquals(
            HackLinks.mismatch(link, RomKind.FIRERED_NATDEX_121)?.replace(RomKind.FIRERED_NATDEX_121.displayName, "X"),
            HackLinks.mismatch(link, RomKind.FIRERED_MAXDEX_10)?.replace(RomKind.FIRERED_MAXDEX_10.displayName, "X"),
        )
        assertNotNull(HackLinks.mismatch(link, RomKind.FIRERED_MAXDEX_10))
        assertFalse(link.fullRomPage)
    }
}
