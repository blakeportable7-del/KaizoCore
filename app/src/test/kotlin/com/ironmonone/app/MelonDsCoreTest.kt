package com.ironmonone.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc34 (2026-10-03): the DS core is built here from a pinned source commit and patches (libretrodroid/cores/melonds,
 * tools/cores/build_core.py), no longer taken from a nightly. 0001 makes retro_unload_game stop the 3D render thread and
 * retro_deinit free the screen buffer, which every DS load left behind (one thread and 388 KB a load), and makes the
 * platform's thread handle safe to free after a join, without which stopping the thread aborted the app. 0002 is
 * melonDS's own later fix for a 3D depth value read before it was set, which left what a W-buffered scene draws up to
 * the compiler: the nightly and a build of the same source here drew some frames of Platinum and HeartGold differently.
 * 0003 (2026-10-04) gives the firmware the core makes when none is imported the Wi-Fi calibration melonDS 0.9.4 writes:
 * it was all 0xFF, a game's wireless library would not start, and Platinum's main menu, which looks for wireless
 * partners whenever a save exists, stopped on "A communication error has occurred" and a white screen. 0004 (2026-10-04)
 * keeps a state loaded in the middle of a quad from writing a fifth vertex past the 3D vertex buffer, over the opaque
 * polygon count: VBlank then sorted a list with holes, and Black 2 resumed from its snapshot crashed in YSort.
 *
 * This pins what ships to that record: each library's SHA-256 and build id, the symbol table kept for crash reports
 * (the same build id, so it reads this build's addresses), the patches' hashes, 16 KB alignment, and NOTICE and the
 * Licenses page saying where the source is. A core swapped in by hand, or a patch edited without a rebuild, fails here.
 */
class MelonDsCoreTest {
    private val dir = File("../libretrodroid/cores/melonds")
    private val abis = listOf("arm64-v8a", "x86_64")

    /** PINNED.txt's key: value lines. */
    private val record: Map<String, String> = File(dir, "PINNED.txt").readLines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && ':' in it }
        .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }

    /** "name k=v k=v" -> the k=v pairs. */
    private fun pairs(value: String): Map<String, String> = value.split(' ')
        .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun fields(key: String): Map<String, String> = pairs(record.getValue(key))

    /** Every patch: line in the order build_core.py applies them, as file name to the SHA-256 the record pins. */
    private val patches: List<Pair<String, String?>> = File(dir, "PINNED.txt").readLines()
        .filter { it.trimStart().startsWith("patch:") }
        .map { it.substringAfter(':').trim() }
        .map { it.substringBefore(' ') to pairs(it)["sha256"] }

    /** A patch as git stores it (LF), whatever the checkout did to its line endings. */
    private fun patchText(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private class Elf(bytes: ByteArray) {
        private val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val loadAlignments = ArrayList<Long>()
        var buildId: String? = null
        val sections = ArrayList<String>()

        init {
            require(bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[4] == 2.toByte() && bytes[5] == 1.toByte()) { "not ELF64 LE" }
            val phoff = b.getLong(0x20).toInt()
            val phentsize = b.getShort(0x36).toInt()
            val phnum = b.getShort(0x38).toInt()
            for (i in 0 until phnum) {
                val at = phoff + i * phentsize
                when (b.getInt(at)) {
                    1 -> loadAlignments += b.getLong(at + 0x30)                       // PT_LOAD
                    4 -> {                                                             // PT_NOTE
                        var pos = b.getLong(at + 0x08).toInt()
                        val end = pos + b.getLong(at + 0x20).toInt()
                        while (pos + 12 <= end) {
                            val namesz = b.getInt(pos); val descsz = b.getInt(pos + 4); val type = b.getInt(pos + 8)
                            val name = String(bytes, pos + 12, maxOf(0, namesz - 1), Charsets.US_ASCII)
                            val desc = pos + 12 + ((namesz + 3) and 3.inv())
                            if (type == 3 && name == "GNU") buildId = (0 until descsz).joinToString("") { "%02x".format(bytes[desc + it]) }
                            pos = desc + ((descsz + 3) and 3.inv())
                        }
                    }
                }
            }
            val shoff = b.getLong(0x28).toInt()
            val shentsize = b.getShort(0x3A).toInt()
            val shnum = b.getShort(0x3C).toInt()
            val shstrndx = b.getShort(0x3E).toInt()
            if (shoff != 0 && shstrndx < shnum) {
                val strtab = b.getLong(shoff + shstrndx * shentsize + 0x18).toInt()
                for (i in 0 until shnum) {
                    var at = strtab + b.getInt(shoff + i * shentsize)
                    val sb = StringBuilder()
                    while (bytes[at] != 0.toByte()) sb.append(bytes[at++].toInt().toChar())
                    sections += sb.toString()
                }
            }
        }
    }

    @Test
    fun `the DS cores that ship are the build the record names`() {
        for (abi in abis) {
            val shipped = File("src/main/jniLibs/$abi/libmelonds_libretro_android.so").readBytes()
            val want = fields(abi)
            assertEquals(want["sha256"], sha256(shipped), "$abi: jniLibs holds the library tools/cores/build_core.py made")
            val elf = Elf(shipped)
            assertEquals(want["build-id"], elf.buildId, "$abi build id")
            assertTrue(elf.loadAlignments.isNotEmpty() && elf.loadAlignments.all { it >= 16384 }, "$abi: 16 KB pages")
            assertFalse(".symtab" in elf.sections || ".debug_info" in elf.sections, "$abi ships stripped")

            val symbols = File(dir, "symbols/$abi/libmelonds_libretro_android.so").readBytes()
            assertEquals(want["symbols-sha256"], sha256(symbols), "$abi: the symbol table kept for crash reports")
            val sym = Elf(symbols)
            assertEquals(elf.buildId, sym.buildId, "$abi: the symbols are this build's")
            assertTrue(".symtab" in sym.sections, "$abi: function names for a crash in the core")
        }
    }

    @Test
    fun `the source is a pinned commit and four patches, with the hashes the record pins`() {
        assertEquals("https://github.com/libretro/melonDS.git", record["repo"])
        assertTrue(Regex("[0-9a-f]{40}").matches(record.getValue("commit")), "a full commit, not a branch")
        assertEquals(listOf("0001-", "0002-", "0003-", "0004-"), patches.map { it.first.take(5) }, "applied in this order")
        for ((name, sha) in patches) {
            assertEquals(sha, sha256(patchText(name).toByteArray(Charsets.UTF_8)), "$name is the patch the record pins")
        }
        assertTrue("-Wl,-z,max-page-size=16384" in record.getValue("build"), "16 KB pages")
        assertTrue(File("../tools/cores/build_core.py").isFile)
    }

    @Test
    fun `patch 0001 stops the render thread and frees the screen buffer on unload`() {
        val patch = patchText(patches[0].first)
        val unload = patch.substringAfter("void retro_unload_game(void)").substringBefore("}")
        assertTrue(unload.indexOf("GPU::DeInitRenderer();") in 0 until unload.indexOf("NDS::DeInit();"), "the render thread stops first")
        val deinit = patch.substringAfter("void retro_deinit(void)").substringBefore("}")
        assertTrue("free(screen_layout_data.buffer_ptr);" in deinit && "screen_layout_data.buffer_ptr = nullptr;" in deinit)
        // Stopping the thread waits for it and then frees it; the platform's Free detached the thread its Wait had
        // already joined and freed, which bionic aborts on. The first build without this closed the app on leaving Play.
        val platform = patch.substringAfter("+++ b/src/libretro/platform.cpp")
        assertTrue("if (!handle->joined) sthread_detach(handle->thread);" in platform, "Free never detaches a joined thread")
        assertTrue("handle->joined = true;" in platform.substringAfter("void Thread_Wait(Thread *thread)"))
    }

    @Test
    fun `patch 0002 is melonDS's own fix for the depth value read before it was set`() {
        val patch = patchText(patches[1].first)
        assertTrue("f143e89c931d12a234851e443b7d85f8017db9a3" in patch, "it names the upstream commit it brings back")
        val header = patch.substringAfter("+++ b/src/GPU3D_Soft.h")
        // The factor InterpolateZ reads for a W-buffered polygon is now computed for it, linear or not.
        assertTrue("+            if ((xdiff != 0) && ((!linear) || wbuffer))" in header, "SetX computes yfactor when W-buffering")
        assertTrue("+        s32 InterpolateZ(s32 z0, s32 z1)" in header, "InterpolateZ reads the interpolator's own wbuffer")
        assertTrue("+            this->wbuffer = wbuffer;" in header)
        val soft = patch.substringAfter("+++ b/src/GPU3D_Soft.cpp").substringBefore("diff --git")
        val told = soft.lines().count { it.startsWith("+") && "Interpolator<0> interpX(xstart, xend+1, wl, wr, polygon->WBuffer);" in it }
        assertEquals(2, told, "both scanline interpolators are told the polygon is W-buffered")
        assertFalse(soft.lines().any { it.startsWith("+") && "InterpolateZ(" in it && "WBuffer" in it }, "no call passes it per call")
    }

    @Test
    fun `patch 0003 gives the core's own firmware the Wi-Fi block melonDS 0_9_4 writes`() {
        val added = patchText(patches[2].first).lines().filter { it.startsWith("+") && !it.startsWith("+++") }
            .joinToString(System.lineSeparator()) { it.drop(1) }
        for (line in listOf(
            "*(u16*)&Firmware[0x2C] = 0x138;",
            "const u8 defaultmac[6] = {0x00, 0x09, 0xBF, 0x11, 0x22, 0x33};",
            "memcpy(&Firmware[0x36], defaultmac, 6);",
            "*(u16*)&Firmware[0x2A] = CRC16(&Firmware[0x2C], *(u16*)&Firmware[0x2C], 0x0000);",
            "Firmware[0x1D] = 0x20; // DS Lite",
        )) assertTrue(line in added, "the patch writes $line")
        // The user settings stay as the source made them: Reset() fills them from the core's options and signs them.
        assertFalse("Firmware[userdata+0x03]" in added || "Firmware[userdata+0x64]" in added, "name, birthday and language are left to Reset()")
        // What a game's wireless library reads: the baseband and RF init values and the channel table, whole, in both
        // libraries. rc34's libraries, built before the patch, carry none of the three.
        val tables = mapOf(
            "baseband init" to bytes(
                0x03, 0x17, 0x40, 0x00, 0x1B, 0x6C, 0x48, 0x80, 0x38, 0x00, 0x35, 0x07, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC7, 0xBB, 0x01, 0x24, 0x7F,
                0x5A, 0x01, 0x3F, 0x01, 0x3F, 0x36, 0x1D, 0x00, 0x78, 0x35, 0x55, 0x12, 0x34, 0x1C, 0x00, 0x01,
                0x0E, 0x38, 0x03, 0x70, 0xC5, 0x2A, 0x0A, 0x08, 0x04, 0x01, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFE,
                0xFE, 0xFE, 0xFE, 0xFC, 0xFC, 0xFA, 0xFA, 0xFA, 0xFA, 0xFA, 0xF8, 0xF8, 0xF6, 0x00, 0x12, 0x14,
                0x12, 0x41, 0x23, 0x03, 0x04, 0x70, 0x35, 0x0E, 0x2C, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x0E, 0x00, 0x00, 0x12, 0x28, 0x1C),
            "RF init" to bytes(
                0x31, 0x4C, 0x4F, 0x21, 0x00, 0x10, 0xB0, 0x08, 0xFA, 0x15, 0x26, 0xE6, 0xC1, 0x01, 0x0E, 0x50,
                0x05, 0x00, 0x6D, 0x12, 0x00, 0x00, 0x01, 0xFF, 0x0E, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x06,
                0x06, 0x00, 0x00, 0x00, 0x18, 0x00, 0x02, 0x00, 0x00),
            "channel table" to bytes(
                0x1E, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0E, 0x0E, 0x0E, 0x0E, 0x0E, 0x0E, 0x0E, 0x16,
                0x26, 0x1C, 0x1C, 0x1C, 0x1D, 0x1D, 0x1D, 0x1E, 0x1E, 0x1E, 0x1E, 0x1F, 0x1E, 0x1F, 0x18,
                0x01, 0x4B, 0x4B, 0x4B, 0x4B, 0x4C, 0x4C, 0x4C, 0x4C, 0x4C, 0x4C, 0x4C, 0x4D, 0x4D, 0x4D,
                0x02, 0x6C, 0x71, 0x76, 0x5B, 0x40, 0x45, 0x4A, 0x2F, 0x34, 0x39, 0x3E, 0x03, 0x08, 0x14),
        )
        for (abi in abis) {
            val lib = File("src/main/jniLibs/$abi/libmelonds_libretro_android.so").readBytes()
            for ((name, table) in tables) assertTrue(indexOf(lib, table) >= 0, "$abi: the core carries the $name (${table.size} bytes)")
        }
    }

    @Test
    fun `patch 0004 keeps a loaded state from writing past the 3D vertex buffer and VBlank from sorting holes`() {
        val patch = patchText(patches[3].first)
        val added = patch.lines().filter { it.startsWith("+") && !it.startsWith("+++") }.map { it.drop(1).trim() }
        // SubmitVertex: the guard comes before the buffer is indexed. The buffer holds four vertices.
        val vertex = patch.substringAfter("void SubmitVertex()").substringBefore("vertextrans->Position[0]")
        val guard = vertex.indexOf("+    if (VertexNumInPoly > 3)")
        assertTrue(guard >= 0, "SubmitVertex guards VertexNumInPoly")
        assertTrue(vertex.indexOf("+        VertexNumInPoly = 0;") > guard, "and starts a new polygon")
        assertTrue(vertex.indexOf("Vertex* vertextrans = &TempVertexBuffer[VertexNumInPoly];") > guard, "before the buffer is indexed")
        // VBlank: the opaque count comes from the polygons' own flags before the list is filled and sorted.
        val vblank = patch.substringAfter("void VBlank()")
        val recount = vblank.indexOf("+                    NumOpaquePolygons = numopaque;")
        assertTrue(recount >= 0 && recount < vblank.indexOf("u32 io = 0, it = NumOpaquePolygons;"), "recounted before the list is filled")
        for (line in listOf("if (NumPolygons > 2048) NumPolygons = 2048;", "u32 numopaque = 0;",
            "if (!CurPolygonRAM[i].Translucent) numopaque++;")) assertTrue(line in added, "VBlank: $line")
        assertEquals(0, patch.lines().count { it.startsWith("-") && !it.startsWith("---") }, "it only adds lines")
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `NOTICE, the Licenses page and the release say where the source and the symbols are`() {
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        assertTrue(record.getValue("commit") in notice, "NOTICE pins the commit")
        for ((name, _) in patches) assertTrue(name in notice, "NOTICE names $name")
        val part = Licences.parts.single { it.name == "melonDS libretro core" }
        assertTrue("fix" in part.what && "KaizoCore's source" in part.what, part.what)
        assertEquals("github.com/libretro/melonDS", part.source)
        val release = File("../tools/release.sh").readText()
        assertTrue("libretrodroid/cores/*/symbols/*/*.so" in release, "release.sh keeps the core's symbols")
    }
}
