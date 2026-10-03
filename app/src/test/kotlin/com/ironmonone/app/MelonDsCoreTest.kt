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
 * rc34 (2026-10-03): the DS core is built here from a pinned source commit and two patches (libretrodroid/cores/melonds,
 * tools/cores/build_core.py), no longer taken from a nightly. 0001 makes retro_unload_game stop the 3D render thread and
 * retro_deinit free the screen buffer, which every DS load left behind (one thread and 388 KB a load), and makes the
 * platform's thread handle safe to free after a join, without which stopping the thread aborted the app. 0002 is
 * melonDS's own later fix for a 3D depth value read before it was set, which left what a W-buffered scene draws up to
 * the compiler: the nightly and a build of the same source here drew some frames of Platinum and HeartGold differently.
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
    fun `the source is a pinned commit and two patches, with the hashes the record pins`() {
        assertEquals("https://github.com/libretro/melonDS.git", record["repo"])
        assertTrue(Regex("[0-9a-f]{40}").matches(record.getValue("commit")), "a full commit, not a branch")
        assertEquals(listOf("0001-", "0002-"), patches.map { it.first.take(5) }, "applied in this order")
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
